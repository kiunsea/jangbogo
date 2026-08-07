package com.jiniebox.jangbogo.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jiniebox.jangbogo.dao.SchemaTestSupport;
import com.jiniebox.jangbogo.dto.MallAccount;
import com.jiniebox.jangbogo.util.StringEncrypter;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 세션 만료 일시중단이 <b>수집 진입 지점에서 실제로 무엇을 하는가</b>를 잰다 (Phase 5-10 키 세분화).
 *
 * <h2>왜 상태 확인만으로는 부족한가</h2>
 *
 * <p>{@code SessionExpirySuspensionTest} 는 맵의 상태({@code isSuspendedForExpiredSession} · {@code
 * allCollectorsSuspendedForExpiredSession})를 잰다. 그것만으로는 <b>진입 지점이 그 값을 실제로 보고 물러나는지</b>를 알 수 없다. 같은
 * 파일의 소스 형태 감시가 "그 자리에 호출이 있다" 까지는 보지만, 호출이 있는 것과 그 결과로 수집이 실제로 돌거나 멈추는 것은 다른 사실이다.
 *
 * <p>이 프로젝트가 반복해서 겪은 결함이 정확히 그 틈이다 — 만료 감지는 단위 테스트 25건이 초록인 채 프로덕션 호출자가 0건이었고, 배포 산출물 가드는 판별식을
 * 무력화해도 5건이 전부 통과했다. 둘 다 '초록인데 아무 일도 안 하는' 상태였다. 그래서 여기서는 {@code runOneTimeCollection} 을 실제로 돌리고
 * <b>수집이 시작됐는가</b>를 센다.
 *
 * <h2>이 국면이 고치는 결함</h2>
 *
 * <p>통합회원(seq=1)에는 수집기가 둘 붙어 있고 둘은 서로 독립적인 데이터원이다. 세션 주입 경로가 대체하는 자리는 그중 하나뿐이라, 그쪽 세션이 죽어도 나머지 하나는
 * 저장된 자격증명으로 멀쩡히 돈다. 예전에는 몰 단위로 멈춰 <b>멀쩡한 쪽까지 사람이 다시 로그인할 때까지 함께 섰다.</b> 아래 첫 시험이 그 경계를 잰다.
 *
 * <h2>실계정 DB·브라우저·네트워크를 쓰지 않는다</h2>
 *
 * <p>DB 는 {@code @TempDir} 의 새 파일만 쓴다. 실제 수집으로 들어가는 유일한 통로({@code JangBoGoManager.collect})는 가짜로 갈아
 * 끼우므로 브라우저가 뜨지 않는다 — <b>그 가짜가 불렸는지가 곧 "수집이 시작됐는가" 다.</b> 수집 로그도 갈아 끼운 기록기로 받는다.
 *
 * @author KIUNSEA
 */
class SessionExpiryEntryPointTest {

  private static final String DB_URL_PROPERTY = "jangbogo.localdb.url";

  /** 수집기가 둘인 몰. 이름은 {@code MallRegistry} 선언에서 온다 ({@code SessionExpirySuspensionTest} 가 대조한다). */
  private static final String MALL_WITH_TWO_COLLECTORS = "1";

  private static final String SESSION_COLLECTOR = "SsgSession";
  private static final String CREDENTIAL_COLLECTOR = "Emart";

  private String previousUrl;
  private String dbUrl;

  private JangBoGoManager manager;
  private MallSchedulerService scheduler;

  /** 갈아 끼운 기록기가 받은 행. 상태 문자열만 본다 — 설정이 어긋나면 여기에 FAIL 이 남아 원인이 드러난다. */
  private final List<String> loggedStatuses = new ArrayList<>();

  @BeforeEach
  void isolateDatabaseAndWireScheduler(@TempDir Path tempDir) throws Exception {
    previousUrl = System.getProperty(DB_URL_PROPERTY);
    dbUrl = "jdbc:sqlite:" + tempDir.resolve("expiry-entry-test.db").toString().replace('\\', '/');
    System.setProperty(DB_URL_PROPERTY, dbUrl);

    // 마이그레이터는 JVM 당 1회만 실제 작업을 한다. 되돌리지 않으면 이 임시 DB 에는 테이블이 없다.
    SchemaTestSupport.remigrate();

    // 진짜 키로 진짜 암호문을 만든다. 자격 판정이 키/IV 와 저장된 계정을 함께 보므로, 가짜 값을
    // 넣으면 재려던 지점 대신 자격 없음에서 먼저 물러나 이 시험이 다른 것을 재게 된다.
    SecretKey key = StringEncrypter.generateKey(256);
    IvParameterSpec iv = StringEncrypter.generateIv();
    String encKeyBase64 = StringEncrypter.encodeSecretKeyToBase64(key);
    String encIvBase64 = StringEncrypter.encodeIvToBase64(iv);
    String cipherId = StringEncrypter.encrypt(StringEncrypter.ALGORITHM, "%tester", key, iv);
    String cipherPass = StringEncrypter.encrypt(StringEncrypter.ALGORITHM, "%secret", key, iv);

    insertMall(Integer.parseInt(MALL_WITH_TWO_COLLECTORS), encKeyBase64, encIvBase64);

    MallCollectOutcome collected = MallCollectOutcome.success(List.of());
    manager = mock(JangBoGoManager.class);
    when(manager.collect(any(), any(), any(), any())).thenReturn(collected);

    MallAccount stored = new MallAccount(MALL_WITH_TWO_COLLECTORS, "ssg", cipherId, cipherPass);
    MallAccountYmlService accounts = mock(MallAccountYmlService.class);
    when(accounts.getAccountBySeq(anyString())).thenReturn(Optional.of(stored));

    scheduler = new MallSchedulerService();
    ReflectionTestUtils.setField(scheduler, "jangBoGoManager", manager);
    ReflectionTestUtils.setField(scheduler, "mallAccountYmlService", accounts);
    ReflectionTestUtils.setField(scheduler, "exportService", mock(ExportService.class));
    scheduler.useLogWriter(
        (seqMall, mallName, status, code, reason, step, startedAt) -> loggedStatuses.add(status));
  }

  @AfterEach
  void restoreDatabaseUrl() {
    // 스케줄러는 인스턴스마다 쓰레드 풀을 하나씩 만든다. 시험마다 새로 만들면서 닫지 않으면
    // 빌드가 끝날 때까지 놀고 있는 풀이 쌓인다. 등록한 작업이 없으므로 즉시 끝난다.
    scheduler.shutdown();
    SchemaTestSupport.reset();
    if (previousUrl == null) {
      System.clearProperty(DB_URL_PROPERTY);
    } else {
      System.setProperty(DB_URL_PROPERTY, previousUrl);
    }
  }

  @Test
  @DisplayName("한 수집기의 세션이 죽어도 그 몰의 회차는 예정대로 시작된다")
  void aDeadSessionCollectorDoesNotStopTheRound() throws Exception {
    // 이것이 키 세분화의 본체다. 예전에는 몰 단위로 멈춰, 저장된 자격증명으로 멀쩡히 도는 나머지
    // 수집기까지 사람이 다시 로그인할 때까지 섰다. 그 회차에 다른 수집기도 0건이면 — 이미 따라잡은
    // 계정에서는 흔한 정상 상태다 — 승격 제약도 막아 주지 못했다.
    scheduler.suspendCollectors(
        MALL_WITH_TWO_COLLECTORS,
        List.of(SESSION_COLLECTOR),
        "테스트몰",
        "세션이 죽었다",
        System.currentTimeMillis());
    loggedStatuses.clear(); // 위 한 행은 만료 기록이다. 아래에서 재는 것은 그 다음 회차다.

    scheduler.runOneTimeCollection(MALL_WITH_TWO_COLLECTORS);

    // 로그를 먼저 본다. 준비가 어긋나 자격 판정에서 걸린 회차는 여기에 사유가 남아 있어,
    // 아래 '수집이 안 불렸다' 보다 원인을 훨씬 빨리 가리킨다.
    assertTrue(
        loggedStatuses.isEmpty(),
        "정상 회차인데 수집 로그가 남았다 — 진입 지점이 물러났거나 자격 판정에서 걸렸다: " + loggedStatuses);
    verify(manager, times(1)).collect(any(), any(), any(), any());
  }

  @Test
  @DisplayName("돌릴 수 있는 자리가 하나도 없으면 회차가 시작되지 않는다")
  void aRoundWithNoLivePositionNeverStarts() throws Exception {
    // 모든 자리가 죽었으면 브라우저를 띄우는 것 자체가 헛일이다. 그리고 여기서 로그를 남기지 않는
    // 것이 핵심이다 — 사유는 만료를 관측한 그 회차에 이미 한 행 적혔고, 여기서 또 적으면 주기마다
    // 같은 줄이 쌓여 수집 로그 화면이 무의미해진다.
    scheduler.suspendCollectors(
        MALL_WITH_TWO_COLLECTORS,
        List.of(SESSION_COLLECTOR, CREDENTIAL_COLLECTOR),
        "테스트몰",
        "세션이 죽었다",
        System.currentTimeMillis());
    loggedStatuses.clear();

    scheduler.runOneTimeCollection(MALL_WITH_TWO_COLLECTORS);

    verify(manager, never()).collect(any(), any(), any(), any());
    assertEquals(List.of(), loggedStatuses, "건너뛴 회차마다 같은 줄이 쌓이면 수집 로그 화면이 무의미해진다.");
  }

  @Test
  @DisplayName("사람이 세션을 다시 뜨고 재개하면 회차가 다시 시작된다")
  void resumingReopensTheEntryPoint() throws Exception {
    // 아무도 풀지 않으면 사용자가 다시 로그인해도 다음 기동까지 수집이 돌아오지 않는다. 재개가
    // 맵에서 지우기만 하고 진입 지점이 그것을 보지 않으면, 이 시험만 그 틈을 잡는다.
    scheduler.suspendCollectors(
        MALL_WITH_TWO_COLLECTORS,
        List.of(SESSION_COLLECTOR, CREDENTIAL_COLLECTOR),
        "테스트몰",
        "세션이 죽었다",
        System.currentTimeMillis());
    scheduler.runOneTimeCollection(MALL_WITH_TWO_COLLECTORS);
    verify(manager, never()).collect(any(), any(), any(), any());

    assertTrue(scheduler.resumeAfterSessionRecovery(MALL_WITH_TWO_COLLECTORS));
    scheduler.runOneTimeCollection(MALL_WITH_TWO_COLLECTORS);

    verify(manager, times(1)).collect(any(), any(), any(), any());
  }

  /** 자격 판정을 통과하는 최소한의 몰 행. {@code auto_collect} 는 이 경로가 읽지 않지만 예전 형태 그대로 둔다. */
  private void insertMall(int seq, String encKeyBase64, String encIvBase64) throws Exception {
    try (Connection c = DriverManager.getConnection(dbUrl);
        Statement s = c.createStatement()) {
      s.executeUpdate(
          "INSERT INTO jbg_mall (seq, id, name, encrypt_key, encrypt_iv, account_status,"
              + " auto_collect, collect_interval_minutes) VALUES ("
              + seq
              + ", 'ssg', '테스트몰', '"
              + encKeyBase64
              + "', '"
              + encIvBase64
              + "', 1, 1, 720)");
    }
  }
}

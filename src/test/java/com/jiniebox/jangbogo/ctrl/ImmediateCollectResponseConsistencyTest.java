package com.jiniebox.jangbogo.ctrl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.jiniebox.jangbogo.dao.SchemaTestSupport;
import com.jiniebox.jangbogo.dto.MallAccount;
import com.jiniebox.jangbogo.svc.ExportService;
import com.jiniebox.jangbogo.svc.JangBoGoManager;
import com.jiniebox.jangbogo.svc.MallAccountYmlService;
import com.jiniebox.jangbogo.svc.MallCollectOutcome;
import com.jiniebox.jangbogo.svc.MallSchedulerService;
import com.jiniebox.jangbogo.util.StringEncrypter;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 즉시수집 응답이 스스로 모순되지 않는지 잰다.
 *
 * <h2>무엇이 문제였나</h2>
 *
 * <p>{@code /malls/auto-collect} 는 몰마다 사유를 여섯 통({@code processed} · {@code skipped} · {@code
 * decryptionFailed} · {@code blocked} · {@code notQualified} · {@code sessionExpired})으로 나눠 내려보낸다.
 * 그런데 판정을 내린 분기가 <b>그 자리에서 목록에 담고 계속 흘러갔다.</b> 세션 만료로 끝난 seq 가 뒤이은 '주기적 스케줄링 시작' 으로 들어가고, 거기서 예외가
 * 나면 포괄 {@code catch} 가 같은 seq 를 {@code skipped} 에도 담았다.
 *
 * <p>결과는 응답 하나에 "계정을 안 끊었다"({@code sessionExpired})와 "끊긴 것처럼 그려라"({@code skipped})가 동시에 실리는 것이다.
 * 대시보드는 {@code skipped} 에 담긴 몰을 {@code account_status=0} 으로 되돌려 버튼을 '계정연결' 로 바꾼다 — 사용자는 끊기지도 않은 계정을
 * 다시 맺고, 정작 해야 할 '브라우저로 로그인' 은 하지 않아 그 몰은 계속 0건이다. 지금 화면에 {@code keepConnected} 필터가 있어 가려지지만, 필터는
 * 증상 차단이고 그 필터를 지우는 사람이 나오면 그대로 되살아난다.
 *
 * <h2>왜 소스 형태가 아니라 동작을 재나</h2>
 *
 * <p>같은 결함은 {@code add} 호출 한 줄의 위치로도, {@code catch} 조건 한 줄로도 다시 생긴다. 형태를 감시하면 그 두 가지를 각각 따로 적어야 하고
 * 새 경로는 놓친다. 반면 "한 seq 는 한 통에만" 은 응답만 보면 판정할 수 있다.
 *
 * <p>DB 는 {@code @TempDir} 의 새 파일만 쓴다 — 기준선 DB 에 닿지 않는다. 브라우저·네트워크를 쓰지 않는다.
 *
 * @author KIUNSEA
 */
class ImmediateCollectResponseConsistencyTest {

  private static final String DB_URL_PROPERTY = "jangbogo.localdb.url";

  /** 수집 판정을 담는 통 전부. 한 seq 는 이 중 하나에만 담겨야 한다. */
  private static final List<String> COLLECT_BUCKETS =
      List.of(
          "processed",
          "skipped",
          "decryptionFailed",
          "blocked",
          "notQualified",
          "sessionExpired",
          "invalidSeq");

  private String previousUrl;
  private String dbUrl;

  private String encKeyBase64;
  private String encIvBase64;
  private String cipherId;
  private String cipherPass;

  private JangBoGoManager manager;
  private MallSchedulerService scheduler;
  private MallAccountYmlService accounts;
  private AdminController controller;

  @BeforeEach
  void isolateDatabaseAndWireController(@TempDir Path tempDir) throws Exception {
    previousUrl = System.getProperty(DB_URL_PROPERTY);
    dbUrl =
        "jdbc:sqlite:" + tempDir.resolve("immediate-collect-test.db").toString().replace('\\', '/');
    System.setProperty(DB_URL_PROPERTY, dbUrl);

    // 마이그레이터는 JVM 당 1회만 실제 작업을 한다. 되돌리지 않으면 이 임시 DB 에는 테이블이 없다.
    SchemaTestSupport.remigrate();

    // 진짜 키로 진짜 암호문을 만든다. executeNow=false 경로는 실제로 복호화를 해 보므로,
    // 가짜 값을 넣으면 검증하려는 지점 대신 복호화에서 먼저 넘어져 테스트가 다른 것을 재게 된다.
    SecretKey key = StringEncrypter.generateKey(256);
    IvParameterSpec iv = StringEncrypter.generateIv();
    encKeyBase64 = StringEncrypter.encodeSecretKeyToBase64(key);
    encIvBase64 = StringEncrypter.encodeIvToBase64(iv);
    cipherId = StringEncrypter.encrypt(StringEncrypter.ALGORITHM, "%tester", key, iv);
    cipherPass = StringEncrypter.encrypt(StringEncrypter.ALGORITHM, "%secret", key, iv);

    manager = mock(JangBoGoManager.class);
    scheduler = mock(MallSchedulerService.class);
    accounts = mock(MallAccountYmlService.class);
    when(accounts.getAccountBySeq(anyString()))
        .thenReturn(Optional.of(new MallAccount("1", "testmall", cipherId, cipherPass)));

    controller = new AdminController();
    ReflectionTestUtils.setField(controller, "jangBoGoManager", manager);
    ReflectionTestUtils.setField(controller, "mallSchedulerService", scheduler);
    ReflectionTestUtils.setField(controller, "mallAccountYmlService", accounts);
    ReflectionTestUtils.setField(controller, "exportService", mock(ExportService.class));
  }

  @AfterEach
  void restoreDatabaseUrl() {
    SchemaTestSupport.reset();
    if (previousUrl == null) {
      System.clearProperty(DB_URL_PROPERTY);
    } else {
      System.setProperty(DB_URL_PROPERTY, previousUrl);
    }
  }

  @Test
  @DisplayName("만료로 끝난 몰은 스케줄 등록이 실패해도 skipped 에 겹쳐 담기지 않는다")
  void expiredMallIsNeverAlsoSkipped() throws Exception {
    insertMall(1, "testmall", 30);
    when(manager.collect(any(), any(), any(), any()))
        .thenReturn(MallCollectOutcome.sessionExpired("저장된 세션이 만료됐다"));
    schedulerRefusesToSchedule();

    JsonNode res = controller.autoCollectSelected(request(true, "1"));

    assertEquals(List.of("1"), listOf(res, "sessionExpired"), "만료 판정이 사라졌다.");
    assertTrue(
        listOf(res, "skipped").isEmpty(),
        "만료로 끝난 seq 가 skipped 에도 담겼다 — 응답 하나가 '계정을 안 끊었다' 와 '끊긴 것처럼 그려라' 를 동시에 말한다.");
    assertOneBucketPerSeq(res);
  }

  @Test
  @DisplayName("스케줄 등록 실패는 수집 판정을 덮지 않고 따로 보고된다")
  void schedulingFailureIsReportedSeparately() throws Exception {
    insertMall(1, "testmall", 30);
    when(manager.collect(any(), any(), any(), any()))
        .thenReturn(MallCollectOutcome.sessionExpired("저장된 세션이 만료됐다"));
    schedulerRefusesToSchedule();

    JsonNode res = controller.autoCollectSelected(request(true, "1"));

    // 수집은 만료로 끝났고 스케줄 등록만 실패했다. 둘은 다른 사건이므로 각각 실려야 한다 —
    // 하나로 합치면 사용자는 남은 한쪽만 조치하고 끝낸다.
    assertEquals(List.of("1"), listOf(res, "sessionExpired"));
    assertEquals(List.of("1"), listOf(res, "scheduleFailed"), "스케줄 등록 실패가 보고되지 않았다.");
    assertTrue(listOf(res, "scheduled").isEmpty(), "등록에 실패했는데 등록된 것으로 보고했다.");
    assertTrue(res.get("blockedReasons").has("1"), "만료 사유가 스케줄 오류에 덮였다.");
    assertTrue(res.get("scheduleFailedReasons").has("1"), "스케줄 실패 사유가 없다.");

    String message = res.get("message").asText();
    assertTrue(message.contains("만료"), "안내에서 만료가 사라졌다: " + message);
    assertTrue(message.contains("주기적 수집 등록에 실패"), "안내에서 스케줄 실패가 사라졌다: " + message);
  }

  @Test
  @DisplayName("수집에 성공한 몰은 스케줄 등록이 실패해도 '계정 연결 끊김' 으로 되돌려지지 않는다")
  void collectedMallSurvivesSchedulingFailure() throws Exception {
    insertMall(1, "testmall", 30);
    when(manager.collect(any(), any(), any(), any()))
        .thenReturn(MallCollectOutcome.success(List.of()));
    schedulerRefusesToSchedule();

    JsonNode res = controller.autoCollectSelected(request(true, "1"));

    assertEquals(List.of("1"), listOf(res, "processed"), "수집 성공 판정이 사라졌다.");
    // 화면은 decryptionFailed 와 skipped 에 담긴 seq 를 account_status=0 으로 되돌린다.
    // 수집까지 끝난 몰이 거기 담기면 사용자는 멀쩡한 계정을 다시 연결한다.
    assertTrue(listOf(res, "skipped").isEmpty(), "수집에 성공한 seq 가 skipped 에 담겼다.");
    assertTrue(listOf(res, "decryptionFailed").isEmpty(), "수집에 성공한 seq 가 복호화 실패로 보고됐다.");
    assertEquals(List.of("1"), listOf(res, "scheduleFailed"));
    assertOneBucketPerSeq(res);
  }

  @Test
  @DisplayName("즉시 실행하지 않는 요청에서도 스케줄 등록 실패가 계정 연결을 되돌리지 않는다")
  void scheduleOnlyRequestDoesNotBlameTheAccount() throws Exception {
    insertMall(1, "testmall", 30);
    schedulerRefusesToSchedule();

    // executeNow=false 는 자격증명 복호화까지만 하고 수집하지 않는다. 복호화는 통과했으므로
    // 계정에는 아무 문제가 없다 — 그 뒤 스케줄 등록이 실패했다고 계정을 끊으면 안 된다.
    JsonNode res = controller.autoCollectSelected(request(false, "1"));

    assertTrue(listOf(res, "skipped").isEmpty(), "자격증명이 멀쩡한 몰이 skipped 로 떨어져 화면에서 연결이 끊긴다.");
    assertTrue(listOf(res, "decryptionFailed").isEmpty());
    assertEquals(List.of("1"), listOf(res, "scheduleFailed"));
    assertOneBucketPerSeq(res);
  }

  @Test
  @DisplayName("수집에 들어가기 전에 난 오류는 예전대로 skipped 로 남는다")
  void failuresBeforeTheCollectPhaseStayInSkipped() throws Exception {
    insertMall(1, "testmall", 30);
    // 수집 단계에 닿기도 전에 넘어진 회차다. 원인을 모르므로 예전 판정을 그대로 둔다 —
    // 이 테스트가 없으면 '덮어쓰기 금지' 를 넓히다가 skipped 자체가 조용히 비게 된다.
    when(manager.isCollecting(anyString())).thenThrow(new IllegalStateException("상태 조회 실패"));

    JsonNode res = controller.autoCollectSelected(request(true, "1"));

    assertEquals(List.of("1"), listOf(res, "skipped"));
    assertTrue(listOf(res, "scheduleFailed").isEmpty(), "수집 전 오류가 스케줄 실패로 잘못 분류됐다.");
    assertOneBucketPerSeq(res);
  }

  @Test
  @DisplayName("여러 몰이 섞인 회차에서도 한 seq 는 한 통에만 담긴다")
  void everySeqLandsInExactlyOneBucket() throws Exception {
    insertMall(1, "expiredmall", 30);
    insertMall(2, "workingmall", 30);
    insertMall(3, "unlinkedmall", 30);

    // 3번은 저장된 계정이 없다 — 자격 판정에서 걸러진다.
    when(accounts.getAccountBySeq(eq("3"))).thenReturn(Optional.empty());
    when(manager.collect(any(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              JSONObject mall = invocation.getArgument(0);
              return "1".equals(String.valueOf(mall.get("seq")))
                  ? MallCollectOutcome.sessionExpired("저장된 세션이 만료됐다")
                  : MallCollectOutcome.success(List.of());
            });
    schedulerRefusesToSchedule();

    JsonNode res = controller.autoCollectSelected(request(true, "1", "2", "3"));

    assertEquals(List.of("1"), listOf(res, "sessionExpired"));
    assertEquals(List.of("2"), listOf(res, "processed"));
    assertEquals(List.of("3"), listOf(res, "notQualified"));
    assertTrue(listOf(res, "skipped").isEmpty());
    // 자격 없는 3번은 수집 단계까지 가지 않으므로 스케줄 등록도 시도하지 않는다.
    assertEquals(List.of("1", "2"), listOf(res, "scheduleFailed"));
    assertOneBucketPerSeq(res);
  }

  // ---------------------------------------------------------------
  // seq 형태 검증 — 잘못된 요청과 '몰 없음' 은 다른 사실이다
  // ---------------------------------------------------------------

  @Test
  @DisplayName("쇼핑몰 번호 형태가 아닌 seq 는 '계정 연결 끊김' 으로 그려지는 목록에 담기지 않는다")
  void aMalformedSeqIsNotReportedAsADisconnectedAccount() throws Exception {
    // 예전에는 DAO 가 '몰 없음' 으로 돌려주어 skipped 로 흘렀다. 대시보드는 skipped 에 담긴 seq 를
    // account_status=0 으로 되돌리므로, 사용자는 끊긴 계정도 없는데 원인을 자기 계정에서 찾는다.
    JsonNode res = controller.autoCollectSelected(request(true, "1 OR 1=1"));

    assertEquals(List.of("1 OR 1=1"), listOf(res, "invalidSeq"), "형태가 깨진 seq 를 따로 보고하지 않는다.");
    assertTrue(listOf(res, "skipped").isEmpty(), "형태가 깨진 seq 가 '계정 연결 끊김' 목록에 담겼다.");
    assertTrue(listOf(res, "notQualified").isEmpty());
    assertTrue(listOf(res, "blocked").isEmpty());
    assertTrue(res.get("blockedReasons").has("1 OR 1=1"), "사유가 응답에 실리지 않았다.");
    assertOneBucketPerSeq(res);

    // 대시보드는 사유를 data-seq 로 DOM 을 찾아 그린다. 없는 몰에는 그릴 자리가 없으므로,
    // 이 안내 문구가 사용자에게 닿는 유일한 통로다.
    String message = res.get("message").asText();
    assertTrue(message.contains("형식"), "안내가 '요청이 잘못됐다' 를 말하지 않는다: " + message);
  }

  @Test
  @DisplayName("형태가 깨진 seq 는 조회도 만료 해제도 시도하지 않는다")
  void aMalformedSeqNeverReachesTheRestOfTheLoop() throws Exception {
    controller.autoCollectSelected(request(true, "abc"));

    // 여기까지 갔다면 그 뒤의 getMall·스케줄 등록도 함께 돌았다는 뜻이다.
    verify(scheduler, never()).resumeAfterSessionRecovery("abc");
    verify(scheduler, never()).scheduleMall(anyString(), anyInt());
  }

  @Test
  @DisplayName("숫자지만 없는 몰은 예전대로 skipped 로 남는다 — 형태 검증이 판정을 넓히지 않는다")
  void anAbsentButWellFormedSeqKeepsItsOldBucket() throws Exception {
    // '형태가 잘못됐다' 와 '그런 몰이 없다' 는 다른 사실이고 사람이 할 일도 다르다. 형태 검증이
    // 뒤쪽까지 삼키면 DB 에서 몰이 사라진 진짜 사고가 '요청이 잘못됐다' 로 오인된다.
    JsonNode res = controller.autoCollectSelected(request(true, "999"));

    assertEquals(List.of("999"), listOf(res, "skipped"));
    assertTrue(listOf(res, "invalidSeq").isEmpty());
    assertOneBucketPerSeq(res);
  }

  @Test
  @DisplayName("형태가 멀쩡한 몰은 같은 요청에 섞여 있어도 그대로 수집된다")
  void wellFormedSeqsAreUnaffectedByAMalformedNeighbour() throws Exception {
    insertMall(1, "testmall", 30);
    when(manager.collect(any(), any(), any(), any()))
        .thenReturn(MallCollectOutcome.success(List.of()));

    JsonNode res = controller.autoCollectSelected(request(true, "1", "  ", "abc"));

    assertEquals(List.of("1"), listOf(res, "processed"), "멀쩡한 몰의 수집이 함께 막혔다.");
    assertEquals(List.of("1"), listOf(res, "scheduled"), "멀쩡한 몰의 주기 등록이 함께 막혔다.");
    assertEquals(List.of("  ", "abc"), listOf(res, "invalidSeq"));
    assertOneBucketPerSeq(res);
  }

  // ── 도우미 ──────────────────────────────────────────────────────────────

  /** 스케줄 등록에서 예외를 낸다. '수집은 끝났는데 뒷일이 실패한' 회차를 만드는 유일한 방법이다. */
  private void schedulerRefusesToSchedule() {
    doThrow(new IllegalStateException("스케줄러를 쓸 수 없다"))
        .when(scheduler)
        .scheduleMall(anyString(), anyInt());
  }

  private void insertMall(int seq, String id, int intervalMinutes) throws Exception {
    try (Connection c = DriverManager.getConnection(dbUrl);
        Statement s = c.createStatement()) {
      s.executeUpdate(
          "INSERT INTO jbg_mall (seq, id, name, encrypt_key, encrypt_iv, account_status,"
              + " auto_collect, collect_interval_minutes) VALUES ("
              + seq
              + ", '"
              + id
              + "', '테스트몰"
              + seq
              + "', '"
              + encKeyBase64
              + "', '"
              + encIvBase64
              + "', 1, 1, "
              + intervalMinutes
              + ")");
    }
  }

  private static AdminController.AutoCollectRequest request(boolean executeNow, String... seqs) {
    AdminController.AutoCollectRequest req = new AdminController.AutoCollectRequest();
    req.setSeqs(List.of(seqs));
    req.setExecuteNow(executeNow);
    return req;
  }

  private static List<String> listOf(JsonNode response, String field) {
    List<String> values = new ArrayList<>();
    JsonNode node = response.get(field);
    if (node != null) {
      node.forEach(item -> values.add(item.asText()));
    }
    return values;
  }

  /**
   * 한 seq 가 수집 판정 통 두 곳 이상에 담기지 않았는지 본다.
   *
   * <p>{@code scheduled} 와 {@code scheduleFailed} 는 일부러 제외한다 — 그 둘은 수집 판정이 아니라 뒷일이고, 만료로 끝난 몰의 스케줄
   * 등록이 실패하면 <b>두 곳에 함께 실리는 것이 맞다.</b>
   */
  private static void assertOneBucketPerSeq(JsonNode response) {
    Set<String> seen = new LinkedHashSet<>();
    for (String bucket : COLLECT_BUCKETS) {
      for (String seq : listOf(response, bucket)) {
        assertTrue(
            seen.add(seq), "seq=" + seq + " 가 " + bucket + " 을 포함해 두 통 이상에 담겼다 — 응답이 스스로 모순된다.");
      }
    }
  }
}

package com.jiniebox.jangbogo.dao;

import static com.jiniebox.jangbogo.svc.util.ExecutionContextDetector.ExecutionContext.INTERACTIVE;
import static com.jiniebox.jangbogo.svc.util.SessionProfileGate.Decision.PROCEED;
import static com.jiniebox.jangbogo.svc.util.SessionProfileGate.Decision.PROFILE_MISSING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.jiniebox.jangbogo.svc.mall.MallRegistry;
import com.jiniebox.jangbogo.svc.util.SessionProfileGate;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 캡처가 게이트의 판정 근거를 실제로 남기는지 검증 (Phase 5 · v0.16.0 회귀).
 *
 * <h2>왜 이 테스트가 따로 필요했나</h2>
 *
 * <p>{@code MallSessionProfileFieldsTest} 는 <b>테스트가 직접 INSERT 한 값</b>을 되읽는 스키마 왕복 검사라, 프로덕션이 그 컬럼에
 * 무언가를 쓰기는 하는지를 전혀 보증하지 못한다. 실제로 v0.16.0 의 캡처는 스냅샷 키/IV/마지막 로그인 시각만 커밋했고 {@code
 * session_profile_name}·{@code session_profile_owner}·{@code session_profile_status} 를 쓰는 코드는 저장소에
 * <b>한 줄도 없었다.</b> 그 상태로 몰 옵트인을 켜면 수집이 코드 한 줄 돌기 전에 전량 {@code PROFILE_MISSING} 으로 건너뛰어진다. 스키마 왕복
 * 검사는 그 사이 내내 초록불이었다.
 *
 * <p>그래서 여기서는 방향을 뒤집는다 — <b>프로덕션 쓰기 메서드를 호출한 뒤</b> 게이트가 실제로 읽는 조회로 되읽고, 그 값을 그대로 게이트에 먹여 판정이
 * 뒤집히는지까지 본다. 캡처와 수집을 잇는 연결이 살아 있는지를 한 줄로 잰다.
 *
 * <p>{@code @TempDir} 의 새 파일을 쓴다 — 기준선 DB 에 닿지 않는다.
 *
 * @author KIUNSEA
 */
class MallSessionProfileIdentityWriteTest {

  private static final String DB_URL_PROPERTY = "jangbogo.localdb.url";

  /**
   * 캡처가 만드는 프로필 디렉터리 이름.
   *
   * <p>리터럴이 아니라 레지스트리에서 가져온다 — 게이트가 통과시킨 이 이름이 그대로 {@code SessionProfilePolicy.profileDir()} 에
   * 들어가므로, 캡처가 실제로 여는 디렉터리와 같은 출처여야 한다.
   */
  private static final String PROFILE_NAME = MallRegistry.SSG_GROUP.mallId();

  /** 프로필을 만든 OS 계정 자리. 공개 저장소라 실제 계정명은 남기지 않는다. */
  private static final String OWNER = "tester";

  private String previousUrl;
  private String dbUrl;

  @BeforeEach
  void isolateDatabase(@TempDir Path tempDir) throws Exception {
    previousUrl = System.getProperty(DB_URL_PROPERTY);
    dbUrl = "jdbc:sqlite:" + tempDir.resolve("mall-identity-test.db").toString().replace('\\', '/');
    System.setProperty(DB_URL_PROPERTY, dbUrl);

    // 마이그레이터는 JVM 당 1회만 실제 작업을 한다. 되돌리지 않으면 이 임시 DB 에는 테이블이 생기지 않는다.
    SchemaMigrator.resetForTest();
    SchemaMigrator.ensureMigrated();

    try (Connection c = DriverManager.getConnection(dbUrl);
        Statement s = c.createStatement()) {
      // v0.16.0 이 실제로 만들어 내던 상태다 — 옵트인은 켰는데 신원 컬럼은 비어 있다.
      s.executeUpdate(
          "INSERT INTO jbg_mall (seq, id, name, account_status, auto_collect,"
              + " collect_interval_minutes, session_profile_enabled)"
              + " VALUES (1, 'ssg', '테스트몰', 1, 1, 720, 1)");
      s.executeUpdate(
          "INSERT INTO jbg_mall (seq, id, name, account_status, auto_collect,"
              + " collect_interval_minutes, session_profile_enabled)"
              + " VALUES (2, 'oasis', '옆몰', 1, 1, 720, 1)");
    }
  }

  @AfterEach
  void restoreDatabaseUrl() {
    SchemaMigrator.resetForTest();
    if (previousUrl == null) {
      System.clearProperty(DB_URL_PROPERTY);
    } else {
      System.setProperty(DB_URL_PROPERTY, previousUrl);
    }
  }

  @Test
  @DisplayName("아무도 기록하지 않으면 게이트가 PROFILE_MISSING 으로 막는다 — 고치기 전 상태")
  void theGateBlocksWhileNobodyRecordsTheIdentity() throws Exception {
    JSONObject mall = new JbgMallDataAccessObject().getMall("1");

    assertNotNull(mall, "몰을 찾지 못했다.");
    assertNull(mall.get("session_profile_name"));
    assertEquals(
        PROFILE_MISSING,
        SessionProfileGate.evaluate(
            true,
            () -> INTERACTIVE,
            (String) mall.get("session_profile_name"),
            (String) mall.get("session_profile_owner"),
            OWNER),
        "옵트인만 켜고 신원을 안 적으면 수집이 전량 건너뛰어진다.");
  }

  @Test
  @DisplayName("신원 기록이 게이트가 읽는 세 컬럼을 실제로 채운다")
  void recordingTheIdentityFillsEveryColumnTheGateReads() throws Exception {
    new JbgMallDataAccessObject().saveSessionProfileIdentity("1", PROFILE_NAME, OWNER);

    JSONObject mall = new JbgMallDataAccessObject().getMall("1");

    assertEquals(PROFILE_NAME, mall.get("session_profile_name"));
    assertEquals(OWNER, mall.get("session_profile_owner"));
    // 상태는 READY 하나만 쓴다. EXPIRED 는 시간 경과로 생기는데 갱신해 줄 주체가 없어 값이 굳는다 —
    // 만료는 session_profile_last_login 에서 파생 계산한다.
    assertEquals(
        JbgMallDataAccessObject.SESSION_PROFILE_STATUS_READY, mall.get("session_profile_status"));
  }

  @Test
  @DisplayName("기록한 값을 되읽어 게이트에 넣으면 통과한다 — 캡처와 수집이 여기서 이어진다")
  void theGateProceedsOnTheValuesThatWereActuallyWritten() throws Exception {
    new JbgMallDataAccessObject().saveSessionProfileIdentity("1", PROFILE_NAME, OWNER);

    JSONObject mall = new JbgMallDataAccessObject().getMall("1");

    // 소유자 대조는 게이트에 넘어가는 '현재 계정' 과 같은 값이어야 통과한다. 프로덕션에서 그 값은
    // 양쪽 모두 System.getProperty("user.name") 이다 — 출처가 갈리면 PROFILE_OWNER_MISMATCH 가 된다.
    assertEquals(
        PROCEED,
        SessionProfileGate.evaluate(
            true,
            () -> INTERACTIVE,
            (String) mall.get("session_profile_name"),
            (String) mall.get("session_profile_owner"),
            OWNER),
        "기록했는데도 게이트가 막는다 — 쓰는 값과 읽는 값이 어긋났다.");
  }

  @Test
  @DisplayName("신원 기록이 이미 커밋된 스냅샷 키와 마지막 로그인 시각을 지우지 않는다")
  void recordingTheIdentityKeepsTheSnapshotKeysIntact() throws Exception {
    JbgMallDataAccessObject dao = new JbgMallDataAccessObject();
    // 캡처는 키 커밋(SessionSnapshotStore) 과 신원 커밋(캡처 서비스) 두 번 DB 에 닿는다.
    // 뒤 문장이 앞 문장의 SET 절을 덮으면 방금 뜬 세션이 복호화 불능이 된다.
    dao.saveSessionSnapshotKeys("1", "key-base64", "iv-base64", 1700000000000L);

    dao.saveSessionProfileIdentity("1", PROFILE_NAME, OWNER);

    JSONObject keys = dao.getSessionSnapshotKeys("1");
    assertEquals("key-base64", keys.get("snapshot_key"));
    assertEquals("iv-base64", keys.get("snapshot_iv"));
    assertEquals(1700000000000L, dao.getMall("1").get("session_profile_last_login"));
  }

  @Test
  @DisplayName("한 몰의 기록이 옆 몰을 건드리지 않는다")
  void recordingOneMallLeavesTheOthersAlone() throws Exception {
    new JbgMallDataAccessObject().saveSessionProfileIdentity("1", PROFILE_NAME, OWNER);

    JSONObject other = new JbgMallDataAccessObject().getMall("2");

    assertNull(other.get("session_profile_name"), "WHERE 절이 빠지면 옆 몰이 남의 프로필을 연다.");
    assertNull(other.get("session_profile_owner"));
    assertNull(other.get("session_profile_status"));
  }
}

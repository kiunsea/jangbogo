package com.jiniebox.jangbogo.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jiniebox.jangbogo.svc.mall.MallRegistry;
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
 * 몰별 세션 옵트인 쓰기 경로 검증 (Phase 5-21).
 *
 * <h2>왜 이 테스트가 필요했나</h2>
 *
 * <p>{@code session_profile_enabled} 를 <b>1 로 바꾸는 코드가 저장소에 한 줄도 없었다.</b> 읽는 쪽은 넷이나 있었다 — {@code
 * JangBoGoManager.qualify}, {@code MallOrderUpdater}, {@code StartupTasks}, {@code
 * SessionProfileGate} 를 먹이는 자리. 그래서 스키마·게이트·캡처·주입 수집기를 다 만들어 놓고도 DB 파일을 직접 UPDATE 하지 않으면 세션 경로 전체가
 * 사용자 손에 닿지 않았다. 여기서 그 통로가 실제로 값을 남기는지를 잰다.
 *
 * <h2>무엇을 특히 보는가</h2>
 *
 * <p><b>옆 몰을 건드리지 않는 것</b>과 <b>캡처가 남긴 신원을 지우지 않는 것</b>이다. 두 가지 모두 깨져도 저장 자체는 성공으로 보이고, 사고는 다음 수집
 * 회차에 "그 몰만 통째로 건너뛰어진다" 는 형태로만 드러난다 — 화면에는 아무 변화가 없다.
 *
 * <p>{@code @TempDir} 의 새 파일을 쓴다 — 기준선 DB 에 닿지 않는다. 브라우저·네트워크를 쓰지 않는다.
 *
 * @author KIUNSEA
 */
class MallSessionOptInToggleTest {

  private static final String DB_URL_PROPERTY = "jangbogo.localdb.url";

  /** 캡처가 만드는 프로필 디렉터리 이름. 리터럴이 아니라 레지스트리에서 가져온다. */
  private static final String PROFILE_NAME = MallRegistry.SSG_GROUP.mallId();

  /** 프로필을 만든 OS 계정 자리. 공개 저장소라 실제 계정명은 남기지 않는다. */
  private static final String OWNER = "tester";

  private String previousUrl;
  private String dbUrl;

  @BeforeEach
  void isolateDatabase(@TempDir Path tempDir) throws Exception {
    previousUrl = System.getProperty(DB_URL_PROPERTY);
    dbUrl = "jdbc:sqlite:" + tempDir.resolve("mall-optin-test.db").toString().replace('\\', '/');
    System.setProperty(DB_URL_PROPERTY, dbUrl);

    // 마이그레이터는 JVM 당 1회만 실제 작업을 한다. 되돌리지 않으면 이 임시 DB 에는 테이블이 생기지 않는다.
    SchemaTestSupport.remigrate();

    try (Connection c = DriverManager.getConnection(dbUrl);
        Statement s = c.createStatement()) {
      // 둘 다 옵트인 꺼짐으로 시작한다 — 스키마 기본값과 같은 상태다.
      s.executeUpdate(
          "INSERT INTO jbg_mall (seq, id, name, account_status, auto_collect,"
              + " collect_interval_minutes, session_profile_enabled)"
              + " VALUES (1, 'ssg', '테스트몰', 1, 1, 720, 0)");
      s.executeUpdate(
          "INSERT INTO jbg_mall (seq, id, name, account_status, auto_collect,"
              + " collect_interval_minutes, session_profile_enabled)"
              + " VALUES (2, 'oasis', '옆몰', 1, 1, 720, 0)");
    }
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

  private static int optIn(String seq) throws Exception {
    JSONObject mall = new JbgMallDataAccessObject().getMall(seq);
    assertNotNull(mall, "몰을 찾지 못했다: seq=" + seq);
    return ((Number) mall.get("session_profile_enabled")).intValue();
  }

  @Test
  @DisplayName("옵트인을 켜면 1 로 저장되고 되읽힌다")
  void turningTheOptInOnPersists() throws Exception {
    assertEquals(0, optIn("1"), "시작 상태가 꺼짐이 아니다 — 이 테스트의 전제가 깨졌다.");

    assertTrue(new JbgMallDataAccessObject().setSessionProfileEnabled("1", true));

    assertEquals(1, optIn("1"));
  }

  @Test
  @DisplayName("켠 뒤 끄면 0 으로 돌아온다")
  void turningTheOptInOffPersists() throws Exception {
    JbgMallDataAccessObject dao = new JbgMallDataAccessObject();
    dao.setSessionProfileEnabled("1", true);

    assertTrue(dao.setSessionProfileEnabled("1", false));

    assertEquals(0, optIn("1"));
  }

  @Test
  @DisplayName("한 몰의 옵트인을 켜도 옆 몰은 그대로다")
  void togglingOneMallLeavesTheOthersAlone() throws Exception {
    JbgMallDataAccessObject dao = new JbgMallDataAccessObject();
    dao.setSessionProfileEnabled("2", true);

    // 1번을 켠다. WHERE 절이 빠지거나 "목록에 없으면 해제" 시맨틱이 섞이면 2번이 함께 뒤집힌다.
    dao.setSessionProfileEnabled("1", true);
    assertEquals(1, optIn("2"), "다른 몰의 옵트인이 함께 켜지거나 꺼졌다.");

    // 1번을 끈다. 이쪽이 실제 사고 형태였다 — 전체를 0 으로 밀고 시작하는 저장은 여기서 2번을 끈다.
    dao.setSessionProfileEnabled("1", false);
    assertEquals(1, optIn("2"), "한 몰을 끄는 요청이 다른 몰의 옵트인까지 껐다.");
    assertEquals(0, optIn("1"));
  }

  @Test
  @DisplayName("옵트인 갱신이 캡처가 남긴 신원과 스냅샷 키를 지우지 않는다")
  void togglingKeepsTheCapturedIdentityIntact() throws Exception {
    JbgMallDataAccessObject dao = new JbgMallDataAccessObject();
    dao.saveSessionSnapshotKeys("1", "key-base64", "iv-base64", 1700000000000L);
    dao.saveSessionProfileIdentity("1", PROFILE_NAME, OWNER);

    // SET 절에 신원 컬럼이 섞이면 여기서 지워지고, 다음 회차에 게이트가 PROFILE_MISSING 으로 막는다.
    // 즉 스위치를 켠 순간 그 몰의 수집을 통째로 잃는다.
    dao.setSessionProfileEnabled("1", true);

    JSONObject mall = dao.getMall("1");
    assertEquals(PROFILE_NAME, mall.get("session_profile_name"));
    assertEquals(OWNER, mall.get("session_profile_owner"));
    assertEquals(
        JbgMallDataAccessObject.SESSION_PROFILE_STATUS_READY, mall.get("session_profile_status"));
    assertEquals(1700000000000L, mall.get("session_profile_last_login"));

    JSONObject keys = dao.getSessionSnapshotKeys("1");
    assertEquals("key-base64", keys.get("snapshot_key"));
    assertEquals("iv-base64", keys.get("snapshot_iv"));
  }

  @Test
  @DisplayName("없는 몰은 false 를 돌려준다 — 아무 행도 안 고친 것을 성공이라 하지 않는다")
  void togglingAMallThatDoesNotExistReportsFailure() throws Exception {
    // WHERE 가 아무 행도 고르지 못한 것을 void 로 삼키면 화면은 "저장했습니다" 라 말하고 DB 는 그대로다.
    assertFalse(new JbgMallDataAccessObject().setSessionProfileEnabled("99", true));
  }

  @Test
  @DisplayName("숫자가 아닌 seq 는 조용히 넘기지 않고 던진다")
  void nonNumericSeqIsRejected() {
    // 갱신에서 대상이 틀린 것을 값으로 넘기면 '저장됨' 으로 보고된다. 조회와 달리 여기서 끊어야 한다.
    assertThrows(
        IllegalArgumentException.class,
        () -> new JbgMallDataAccessObject().setSessionProfileEnabled("1 OR 1=1", true));
  }
}

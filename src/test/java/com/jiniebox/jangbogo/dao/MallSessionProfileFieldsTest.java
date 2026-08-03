package com.jiniebox.jangbogo.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 몰 조회가 세션 프로필 컬럼을 실제로 돌려주는지 검증 (Phase 5-4 회귀).
 *
 * <h2>왜 이 테스트가 생겼나</h2>
 *
 * <p>실측에서 잡힌 결함이다. 컬럼을 {@code schema.sql} 에 선언하고, 정책·게이트 코드를 만들고, 수집 경로에 배선까지 끝냈는데 <b>게이트가 전혀 걸리지
 * 않았다.</b> 원인은 {@code JbgMallDataAccessObject} 의 SELECT 목록에 새 컬럼이 없다는 것이었다 — 값이 항상 null 이라 옵트인 판정이
 * 영원히 false 였다.
 *
 * <p>단위테스트는 이걸 못 잡았다. 게이트 판정 함수를 <b>직접</b> 호출해 검증했기 때문에, 그 함수에 값을 실어 나르는 경로가 끊어진 것은 보이지 않았다. <b>순수
 * 함수 테스트와 실제 배선 사이의 틈</b>이 바로 이 자리다.
 *
 * <p>그래서 여기서는 DAO 를 실제 SQLite 파일에 대고 호출해, 게이트가 읽는 키가 결과에 담기는지 확인한다.
 *
 * <p>{@code @TempDir} 의 새 파일을 쓴다 — 기준선 DB 에 닿지 않는다.
 *
 * @author KIUNSEA
 */
class MallSessionProfileFieldsTest {

  private static final String DB_URL_PROPERTY = "jangbogo.localdb.url";

  /** 게이트가 읽는 키. 하나라도 빠지면 그 판정이 조용히 죽는다. */
  private static final List<String> GATE_KEYS =
      List.of(
          "session_profile_enabled",
          "session_profile_name",
          "session_profile_status",
          "session_profile_last_login",
          "session_profile_owner");

  private String previousUrl;
  private String dbUrl;

  @BeforeEach
  void isolateDatabase(@TempDir Path tempDir) throws Exception {
    previousUrl = System.getProperty(DB_URL_PROPERTY);
    dbUrl = "jdbc:sqlite:" + tempDir.resolve("mall-test.db").toString().replace('\\', '/');
    System.setProperty(DB_URL_PROPERTY, dbUrl);

    // 마이그레이터는 JVM 당 1회만 실제 작업을 한다. 되돌리지 않으면 이 임시 DB 에는
    // 테이블이 생기지 않는다.
    SchemaMigrator.resetForTest();
    SchemaMigrator.ensureMigrated();

    try (Connection c = DriverManager.getConnection(dbUrl);
        Statement s = c.createStatement()) {
      s.executeUpdate(
          "INSERT INTO jbg_mall (seq, id, name, account_status, auto_collect,"
              + " collect_interval_minutes, session_profile_enabled, session_profile_name,"
              + " session_profile_status, session_profile_last_login, session_profile_owner)"
              + " VALUES (1, 'ssg', '테스트몰', 1, 1, 720, 1, 'ssg-profile', 'READY', 1700000000000, 'kim')");
      s.executeUpdate(
          "INSERT INTO jbg_mall (seq, id, name, account_status, auto_collect,"
              + " collect_interval_minutes) VALUES (2, 'oasis', '옵트인안함', 1, 1, 720)");
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
  @DisplayName("getMall 이 게이트가 읽는 키를 전부 담아 돌려준다")
  void getMallCarriesEveryGateKey() throws Exception {
    JSONObject mall = new JbgMallDataAccessObject().getMall("1");

    assertNotNull(mall, "몰을 찾지 못했다.");
    for (String key : GATE_KEYS) {
      assertEquals(
          true,
          mall.containsKey(key),
          "getMall 결과에 '" + key + "' 가 없다 — 게이트 입력이 끊긴다. SELECT 목록을 확인할 것.");
    }
  }

  @Test
  @DisplayName("getAllMalls 도 같은 키를 담는다 — 스케줄 복원이 이걸로 판단한다")
  void getAllMallsCarriesEveryGateKey() throws Exception {
    List<JSONObject> malls = new JbgMallDataAccessObject().getAllMalls(false);

    assertNotNull(malls);
    assertEquals(2, malls.size());
    for (JSONObject mall : malls) {
      for (String key : GATE_KEYS) {
        assertEquals(
            true,
            mall.containsKey(key),
            "getAllMalls 결과에 '" + key + "' 가 없다 — 5-5 의 스케줄 등록 판단이 죽는다.");
      }
    }
  }

  @Test
  @DisplayName("값이 그대로 실려 온다 — 담기기만 하고 비면 소용없다")
  void carriesTheActualValues() throws Exception {
    JSONObject mall = new JbgMallDataAccessObject().getMall("1");

    assertEquals(1, mall.get("session_profile_enabled"));
    assertEquals("ssg-profile", mall.get("session_profile_name"));
    assertEquals("READY", mall.get("session_profile_status"));
    assertEquals("kim", mall.get("session_profile_owner"));
    assertEquals(1700000000000L, mall.get("session_profile_last_login"));
  }

  @Test
  @DisplayName("옵트인하지 않은 몰은 0 과 null 로 온다")
  void unsetMallReadsAsOptedOut() throws Exception {
    JSONObject mall = new JbgMallDataAccessObject().getMall("2");

    // 이 값이 곧 SessionProfilePolicy.appliesTo 의 입력이다. null 이 아니라 0 이어야
    // '옵트인하지 않음' 이 명확해진다.
    assertEquals(0, mall.get("session_profile_enabled"));
    assertNull(mall.get("session_profile_name"));
    assertNull(mall.get("session_profile_owner"));
  }
}

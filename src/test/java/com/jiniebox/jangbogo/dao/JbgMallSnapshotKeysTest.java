package com.jiniebox.jangbogo.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
 * 세션 스냅샷 키/IV 의 저장·조회 왕복 검증 (Phase 5-16 · 경계).
 *
 * <h2>왜 경계 테스트인가</h2>
 *
 * <p>순수 함수 테스트는 이걸 못 잡는다. {@code schema.sql} 에 컬럼을 선언하고 저장 코드를 만들어도, {@code SchemaMigrator} 가 실제 DB
 * 에 컬럼을 붙이지 못했거나 SELECT 가 다른 컬럼을 읽으면 저장은 성공으로 보이는데 복호화 시점에 키가 null 로 온다. 5차 세션 교훈이 정확히 이 자리다 — 그래서
 * 실제 SQLite 파일에 대고 <b>쓴 것을 그대로 읽는지</b> 확인한다.
 *
 * <p>{@code @TempDir} 의 새 파일을 쓴다 — 기준선 DB 에 닿지 않는다.
 */
class JbgMallSnapshotKeysTest {

  private static final String DB_URL_PROPERTY = "jangbogo.localdb.url";

  private String previousUrl;
  private String dbUrl;

  @BeforeEach
  void isolateDatabase(@TempDir Path tempDir) throws Exception {
    previousUrl = System.getProperty(DB_URL_PROPERTY);
    dbUrl = "jdbc:sqlite:" + tempDir.resolve("snapshot-keys-test.db").toString().replace('\\', '/');
    System.setProperty(DB_URL_PROPERTY, dbUrl);

    // 마이그레이터는 JVM 당 1회만 실제 작업을 한다. 되돌리지 않으면 이 임시 DB 에 컬럼이 생기지 않는다.
    SchemaMigrator.resetForTest();
    SchemaMigrator.ensureMigrated();

    try (Connection c = DriverManager.getConnection(dbUrl);
        Statement s = c.createStatement()) {
      s.executeUpdate(
          "INSERT INTO jbg_mall (seq, id, name, account_status) VALUES (1, 'ssg', '테스트몰', 1)");
      s.executeUpdate(
          "INSERT INTO jbg_mall (seq, id, name, account_status) VALUES (2, 'oasis', '키없는몰', 1)");
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
  @DisplayName("저장한 키/IV 와 캡처 시각을 그대로 읽는다")
  void savesAndReadsBackKeys() throws Exception {
    JbgMallDataAccessObject dao = new JbgMallDataAccessObject();
    dao.saveSessionSnapshotKeys("1", "KEY-base64==", "IV-base64==", 1700000000000L);

    JSONObject keys = dao.getSessionSnapshotKeys("1");
    assertNotNull(keys, "몰을 찾지 못했다 — 컬럼이 안 붙었거나 SELECT 가 어긋났다.");
    assertEquals("KEY-base64==", keys.get("snapshot_key"));
    assertEquals("IV-base64==", keys.get("snapshot_iv"));

    // capturedAt 은 session_profile_last_login 에 함께 실린다 (5-17 입력).
    JSONObject mall = dao.getMall("1");
    assertEquals(1700000000000L, mall.get("session_profile_last_login"));
  }

  @Test
  @DisplayName("저장한 적 없는 몰은 키가 null 로 온다 — 부재를 값으로 표현")
  void unsavedMallReadsNullKeys() throws Exception {
    JSONObject keys = new JbgMallDataAccessObject().getSessionSnapshotKeys("2");
    assertNotNull(keys, "몰 자체는 있어야 한다.");
    assertNull(keys.get("snapshot_key"));
    assertNull(keys.get("snapshot_iv"));
  }

  @Test
  @DisplayName("없는 몰은 null 을 돌려준다")
  void missingMallReturnsNull() throws Exception {
    assertNull(new JbgMallDataAccessObject().getSessionSnapshotKeys("999"));
  }

  @Test
  @DisplayName("스냅샷 키는 넓은 조회(getMall)에 실리지 않는다 — 세션 열쇠가 화면·로그로 흐르면 안 된다")
  void snapshotKeysDoNotLeakIntoBroadSelect() throws Exception {
    JbgMallDataAccessObject dao = new JbgMallDataAccessObject();
    dao.saveSessionSnapshotKeys("1", "SECRET-KEY", "SECRET-IV", 1700000000000L);

    JSONObject mall = dao.getMall("1");
    assertFalse(mall.containsKey("session_snapshot_key"), "세션 스냅샷 키가 넓은 SELECT 로 새어 나온다.");
    assertFalse(mall.containsKey("session_snapshot_iv"), "세션 스냅샷 IV 가 넓은 SELECT 로 새어 나온다.");
  }
}

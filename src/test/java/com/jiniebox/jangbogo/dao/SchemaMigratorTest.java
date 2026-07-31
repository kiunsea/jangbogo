package com.jiniebox.jangbogo.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 스키마 마이그레이션 단일 경로 검증 (Phase 3-10).
 *
 * <p>브라우저·네트워크를 쓰지 않는다. DB 는 테스트마다 {@code @TempDir} 의 새 SQLite 파일이라 기준선 DB 에 닿지 않는다 — {@code
 * LocalDBConnection} 이 접속 대상을 {@code jangbogo.localdb.url} 에서 매 생성 시 읽으므로 프로퍼티만 바꿔 격리한다.
 *
 * @author KIUNSEA
 */
class SchemaMigratorTest {

  private static final String DB_URL_PROPERTY = "jangbogo.localdb.url";

  private String previousUrl;
  private String dbUrl;

  @BeforeEach
  void isolateDatabase(@TempDir Path tempDir) {
    previousUrl = System.getProperty(DB_URL_PROPERTY);
    dbUrl = "jdbc:sqlite:" + tempDir.resolve("migrator-test.db").toString().replace('\\', '/');
    System.setProperty(DB_URL_PROPERTY, dbUrl);
    SchemaMigrator.resetForTest();
  }

  @AfterEach
  void restoreDatabase() {
    SchemaMigrator.resetForTest();
    if (previousUrl == null) {
      System.clearProperty(DB_URL_PROPERTY);
    } else {
      System.setProperty(DB_URL_PROPERTY, previousUrl);
    }
  }

  private Set<String> columnsOf(String table) throws Exception {
    Set<String> cols = new LinkedHashSet<>();
    try (Connection c = DriverManager.getConnection(dbUrl);
        Statement s = c.createStatement();
        ResultSet r = s.executeQuery("PRAGMA table_info(" + table + ")")) {
      while (r.next()) {
        cols.add(r.getString("name").toLowerCase(Locale.ROOT));
      }
    }
    return cols;
  }

  private void exec(String... sql) throws Exception {
    try (Connection c = DriverManager.getConnection(dbUrl);
        Statement s = c.createStatement()) {
      for (String q : sql) {
        s.executeUpdate(q);
      }
    }
  }

  private String singleValue(String query) throws Exception {
    try (Connection c = DriverManager.getConnection(dbUrl);
        Statement s = c.createStatement();
        ResultSet r = s.executeQuery(query)) {
      return r.next() ? r.getString(1) : null;
    }
  }

  // ---------------------------------------------------------------- 파서

  @Test
  @DisplayName("schema.sql 의 테이블 5개를 모두 읽어낸다")
  void parsesEveryDeclaredTable() throws Exception {
    Map<String, SchemaMigrator.TableSpec> tables =
        SchemaMigrator.parseSchema(SchemaMigrator.readSchemaSql());

    assertTrue(
        tables
            .keySet()
            .containsAll(
                Set.of(
                    "jbg_item", "jbg_mall", "jbg_order", "jbg_export_config", "jbg_collect_log")),
        "읽어낸 테이블: " + tables.keySet());
  }

  @Test
  @DisplayName("행 주석(--)을 컬럼으로 오인하지 않는다")
  void ignoresLineComments() throws Exception {
    Map<String, SchemaMigrator.TableSpec> tables =
        SchemaMigrator.parseSchema(SchemaMigrator.readSchemaSql());

    SchemaMigrator.TableSpec mall = tables.get("jbg_mall");
    assertNotNull(mall);
    for (String col : mall.columns.keySet()) {
      assertFalse(col.startsWith("--"), "주석이 컬럼으로 잡혔다: " + col);
      assertFalse(col.contains(" "), "컬럼명에 공백이 들어갔다: " + col);
    }
  }

  @Test
  @DisplayName("괄호 안의 콤마에서 컬럼을 자르지 않는다")
  void doesNotSplitInsideParentheses() {
    Map<String, SchemaMigrator.TableSpec> tables =
        SchemaMigrator.parseSchema(
            "CREATE TABLE t (\n"
                + "  seq INTEGER PRIMARY KEY,\n"
                + "  amount DECIMAL(10,2) DEFAULT 0,\n"
                + "  note TEXT\n"
                + ");");

    SchemaMigrator.TableSpec t = tables.get("t");
    assertEquals(Set.of("seq", "amount", "note"), t.columns.keySet());
  }

  @Test
  @DisplayName("테이블 제약(PRIMARY KEY (...) 등)은 컬럼으로 세지 않는다")
  void skipsTableLevelConstraints() {
    Map<String, SchemaMigrator.TableSpec> tables =
        SchemaMigrator.parseSchema(
            "CREATE TABLE t (\n"
                + "  a INTEGER,\n"
                + "  b INTEGER,\n"
                + "  PRIMARY KEY (a, b),\n"
                + "  UNIQUE (b)\n"
                + ");");

    assertEquals(Set.of("a", "b"), tables.get("t").columns.keySet());
  }

  // ---------------------------------------------------------------- 신규 설치

  @Test
  @DisplayName("빈 DB 에 선언된 테이블을 전부 만든다")
  void createsAllTablesOnAFreshDatabase() throws Exception {
    SchemaMigrator.ensureMigrated();

    for (String table :
        new String[] {
          "jbg_item", "jbg_mall", "jbg_order", "jbg_export_config", "jbg_collect_log"
        }) {
      assertFalse(columnsOf(table).isEmpty(), table + " 테이블이 만들어지지 않았다.");
    }
  }

  @Test
  @DisplayName("auto_collect 는 신규 설치에서도 생긴다")
  void freshInstallHasAutoCollect() throws Exception {
    // 통합 전에는 auto_collect 가 schema.sql 에 없고 런타임 ALTER 에만 있었다.
    // 신규 설치와 기존 설치가 서로 다른 경로로 같은 스키마에 도달하던 비대칭을 여기서 막는다.
    SchemaMigrator.ensureMigrated();

    Set<String> cols = columnsOf("jbg_mall");
    assertTrue(cols.contains("auto_collect"), "jbg_mall 컬럼: " + cols);
    assertTrue(cols.contains("collect_interval_minutes"), "jbg_mall 컬럼: " + cols);
  }

  // ---------------------------------------------------------------- 기존 설치 보정

  @Test
  @DisplayName("구버전 테이블에 없는 컬럼만 채우고 데이터는 보존한다")
  void addsMissingColumnsWithoutLosingRows() throws Exception {
    // v0.7.0 이전 모양의 jbg_mall — auto_collect / collect_interval_minutes 가 없다.
    exec(
        "CREATE TABLE jbg_mall ("
            + "seq INTEGER PRIMARY KEY AUTOINCREMENT, id TEXT NOT NULL, name TEXT NOT NULL DEFAULT '0',"
            + "details TEXT, encrypt_key TEXT, encrypt_iv TEXT,"
            + "account_status INTEGER NOT NULL DEFAULT 0, last_signin_time INTEGER)",
        "INSERT INTO jbg_mall (seq, id, name) VALUES (1, 'ssg', 'SSG')");

    SchemaMigrator.ensureMigrated();

    Set<String> cols = columnsOf("jbg_mall");
    assertTrue(cols.contains("auto_collect"), "auto_collect 가 추가되지 않았다: " + cols);
    assertTrue(cols.contains("collect_interval_minutes"), "collect_interval_minutes 가 없다: " + cols);
    assertEquals("ssg", singleValue("SELECT id FROM jbg_mall WHERE seq = 1"), "기존 행이 유실됐다.");
  }

  @Test
  @DisplayName("v0.8.0 진단 컬럼 5개가 구버전 jbg_collect_log 에 채워진다")
  void addsDiagnosticColumnsToLegacyCollectLog() throws Exception {
    // v0.8.0 이전 모양 — 진단 컬럼 5개가 없다.
    exec(
        "CREATE TABLE jbg_collect_log ("
            + "seq INTEGER PRIMARY KEY AUTOINCREMENT, seq_mall INTEGER NOT NULL, mall_name TEXT,"
            + "status TEXT NOT NULL DEFAULT 'SUCCESS', order_count INTEGER DEFAULT 0,"
            + "item_count INTEGER DEFAULT 0, error_message TEXT, error_detail TEXT,"
            + "started_at INTEGER, finished_at INTEGER, insert_time INTEGER)",
        "INSERT INTO jbg_collect_log (seq_mall, mall_name, status) VALUES (1, 'SSG', 'FAIL')");

    SchemaMigrator.ensureMigrated();

    Set<String> cols = columnsOf("jbg_collect_log");
    for (String col :
        new String[] {
          "step_name", "current_url", "page_title", "target_selector", "screenshot_path"
        }) {
      assertTrue(cols.contains(col), col + " 이 추가되지 않았다. 현재: " + cols);
    }
    assertEquals("FAIL", singleValue("SELECT status FROM jbg_collect_log WHERE seq = 1"));
  }

  @Test
  @DisplayName("구버전 jbg_export_config 에 FTP 컬럼 6개가 채워진다")
  void addsFtpColumnsToLegacyExportConfig() throws Exception {
    // 통합 전에는 이 6개가 schema.sql 에 없고 JbgExportConfigDataAccessObject 의 런타임 ALTER 에만
    // 있었다. 게다가 그 DAO 의 CREATE TABLE 은 schema.sql 과 달리 updated_time / last_export_time
    // 을 빠뜨리고 있었다 — 복제된 DDL 이 조용히 갈라진 실례다.
    exec(
        "CREATE TABLE jbg_export_config ("
            + "id INTEGER PRIMARY KEY DEFAULT 1, save_path TEXT NOT NULL DEFAULT '',"
            + "save_format TEXT NOT NULL DEFAULT 'json', auto_save_enabled INTEGER NOT NULL DEFAULT 0)",
        "INSERT INTO jbg_export_config (id, save_path) VALUES (1, 'D:/exports')");

    SchemaMigrator.ensureMigrated();

    Set<String> cols = columnsOf("jbg_export_config");
    for (String col :
        new String[] {
          "save_to_jiniebox",
          "ftp_address",
          "ftp_id",
          "ftp_pass",
          "public_key",
          "ftp_encrypt_enabled",
          "updated_time",
          "last_export_time"
        }) {
      assertTrue(cols.contains(col), col + " 이 추가되지 않았다. 현재: " + cols);
    }
    assertEquals("D:/exports", singleValue("SELECT save_path FROM jbg_export_config WHERE id = 1"));
  }

  @Test
  @DisplayName("구버전 jbg_item 에 qty 컬럼이 채워진다")
  void addsQtyColumnToLegacyItem() throws Exception {
    exec(
        "CREATE TABLE jbg_item ("
            + "seq INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL DEFAULT '0',"
            + "seq_order INTEGER, insert_time INTEGER)",
        "INSERT INTO jbg_item (name, seq_order) VALUES ('국산 대파', 1)");

    SchemaMigrator.ensureMigrated();

    assertTrue(columnsOf("jbg_item").contains("qty"), "qty 가 추가되지 않았다.");
    assertEquals("국산 대파", singleValue("SELECT name FROM jbg_item WHERE seq = 1"));
  }

  @Test
  @DisplayName("신규 설치에도 FTP 컬럼과 qty 가 있다")
  void freshInstallHasEveryConsolidatedColumn() throws Exception {
    // 통합 전에는 신규 설치가 FTP 컬럼 없는 jbg_export_config 와 qty 없는 jbg_item 을 받았고,
    // DAO 의 런타임 ALTER 가 뒤늦게 메워 주는 구조였다.
    SchemaMigrator.ensureMigrated();

    assertTrue(columnsOf("jbg_export_config").contains("ftp_encrypt_enabled"));
    assertTrue(columnsOf("jbg_export_config").contains("last_export_time"));
    assertTrue(columnsOf("jbg_item").contains("qty"));
  }

  @Test
  @DisplayName("테이블이 없으면 만들고, 있으면 지우지 않는다")
  void createsMissingTableWhileLeavingOthersAlone() throws Exception {
    exec(
        "CREATE TABLE jbg_mall (seq INTEGER PRIMARY KEY AUTOINCREMENT, id TEXT NOT NULL,"
            + " name TEXT NOT NULL DEFAULT '0')",
        "INSERT INTO jbg_mall (seq, id, name) VALUES (7, 'keepme', 'KEEP')");

    SchemaMigrator.ensureMigrated();

    assertFalse(columnsOf("jbg_collect_log").isEmpty(), "없던 테이블이 생성되지 않았다.");
    assertEquals("keepme", singleValue("SELECT id FROM jbg_mall WHERE seq = 7"), "기존 테이블이 재생성됐다.");
  }

  // ---------------------------------------------------------------- 멱등성 · 안전성

  @Test
  @DisplayName("두 번 돌려도 스키마가 달라지지 않는다")
  void isIdempotent() throws Exception {
    SchemaMigrator.ensureMigrated();
    Set<String> first = columnsOf("jbg_mall");

    SchemaMigrator.resetForTest();
    SchemaMigrator.ensureMigrated();

    assertEquals(first, columnsOf("jbg_mall"));
  }

  @Test
  @DisplayName("JVM 당 한 번만 실제로 수행된다")
  void runsOnlyOncePerJvmUntilReset() throws Exception {
    SchemaMigrator.ensureMigrated();

    // 두 번째 호출은 아무것도 하지 않는다. 마이그레이터가 매번 돌면 DAO 생성마다 DB 를 열게 된다.
    exec("DROP TABLE jbg_collect_log");
    SchemaMigrator.ensureMigrated();

    assertTrue(columnsOf("jbg_collect_log").isEmpty(), "가드가 동작하지 않아 두 번째 호출이 테이블을 되살렸다.");
  }

  @Test
  @DisplayName("ALTER 로 붙일 수 없는 컬럼은 시도하지 않는다")
  void doesNotAttemptUnsafeColumns() {
    Map<String, SchemaMigrator.TableSpec> tables =
        SchemaMigrator.parseSchema(
            "CREATE TABLE t (\n"
                + "  seq INTEGER PRIMARY KEY AUTOINCREMENT,\n"
                + "  code TEXT UNIQUE,\n"
                + "  required TEXT NOT NULL,\n"
                + "  ok TEXT,\n"
                + "  ok2 INTEGER NOT NULL DEFAULT 0\n"
                + ");");
    Map<String, SchemaMigrator.ColumnSpec> cols = tables.get("t").columns;

    assertFalse(cols.get("seq").addableByAlter(), "PRIMARY KEY 컬럼을 ALTER 로 붙이려 한다.");
    assertFalse(cols.get("code").addableByAlter(), "UNIQUE 컬럼을 ALTER 로 붙이려 한다.");
    assertFalse(cols.get("required").addableByAlter(), "기본값 없는 NOT NULL 을 ALTER 로 붙이려 한다.");
    assertTrue(cols.get("ok").addableByAlter());
    assertTrue(cols.get("ok2").addableByAlter(), "기본값 있는 NOT NULL 은 붙일 수 있어야 한다.");
  }

  @Test
  @DisplayName("DAO 를 만들기만 해도 스키마가 보장된다")
  void daoConstructionTriggersMigration() throws Exception {
    // CommonDataAccessObject 생성자가 안전망이다 — 웹서버는 ApplicationReadyEvent 보다 먼저 뜬다.
    new JbgMallDataAccessObject();

    assertFalse(columnsOf("jbg_mall").isEmpty(), "DAO 생성이 스키마 보정을 일으키지 않았다.");
    assertTrue(columnsOf("jbg_mall").contains("auto_collect"));
  }
}

package com.jiniebox.jangbogo.dao;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * DB 스키마 진화의 단일 소유자 (Phase 3-10).
 *
 * <p><b>선언은 {@code schema.sql} 한 곳에만 둔다.</b> 이 클래스는 그 파일을 읽어 실제 DB 와 대조하고, 없는 테이블은 만들고 없는 컬럼은
 * {@code ALTER TABLE ADD COLUMN} 으로 채운다. 컬럼 목록을 자바 코드에 다시 적지 않으므로 두 목록이 어긋날 수가 없다.
 *
 * <h2>왜 필요했나</h2>
 *
 * 통합 전에는 마이그레이션이 세 곳에 흩어져 있었고 기법까지 서로 달랐다.
 *
 * <ul>
 *   <li>{@code schema.sql} + {@code spring.sql.init} — {@code continue-on-error: true} 라 <b>실패해도
 *       조용히 넘어간다.</b> 실제로 개발 트리 DB 에 {@code jbg_collect_log} 가 없는 채로 남아 수집 이력이 통째로 유실됐다.
 *   <li>{@code JbgMallDataAccessObject.ensureAutoCollectColumns} — {@code SELECT col} 을 던져 보고 예외를
 *       잡는 방식. DAO 의 <b>네 경로에서 매 호출마다</b> 돌아, 조회 한 번에 예외 기반 탐지가 두 번 일어났다.
 *   <li>{@code StartupTasks.migrateCollectLogSchema} — {@code PRAGMA table_info} 를 쓰는 정공법. 다만 테이블
 *       DDL 을 자바 문자열로 <b>복제</b>해 두어 {@code schema.sql} 과 어긋날 수 있었다.
 * </ul>
 *
 * <p>비대칭도 있었다. {@code collect_interval_minutes} 는 {@code schema.sql} 에도 런타임 ALTER 에도 있었는데 {@code
 * auto_collect} 는 {@code schema.sql} 에 <b>없고</b> 런타임 ALTER 에만 있었다. 신규 설치와 기존 설치가 서로 다른 경로로 같은 스키마에
 * 도달하고 있었다는 뜻이다.
 *
 * <h2>동작</h2>
 *
 * <p>{@link #ensureMigrated()} 는 JVM 당 한 번만 실제 작업을 한다. 두 지점에서 호출한다 — 기동 시 {@code StartupTasks}, 그리고
 * 안전망으로 {@link CommonDataAccessObject} 생성자. 후자가 있어야 기동 이벤트보다 이른 요청이 들어와도 스키마가 보장된다. 첫 호출 이후에는
 * {@code AtomicBoolean} 한 번 읽는 비용뿐이라, 매 호출마다 예외를 두 번 던지던 기존 방식보다 싸다.
 *
 * <p>{@code ALTER TABLE ADD COLUMN} 으로 안전하게 붙일 수 없는 컬럼(PRIMARY KEY / UNIQUE / 기본값 없는 NOT NULL)은
 * <b>시도하지 않고 경고만 남긴다.</b> 그런 컬럼은 테이블 재작성이 필요하므로 사람이 판단해야 한다.
 *
 * @author KIUNSEA
 */
public final class SchemaMigrator {

  private static final Logger log = LogManager.getLogger(SchemaMigrator.class);

  /** 선언의 단일 출처. */
  static final String SCHEMA_RESOURCE = "schema.sql";

  /** JVM 당 1회 보장. 재진입(마이그레이터가 여는 커넥션이 다시 이 메서드를 부르는 경우)도 이 플래그가 막는다. */
  private static final AtomicBoolean STARTED = new AtomicBoolean(false);

  private static final Pattern CREATE_TABLE =
      Pattern.compile(
          "CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s*\\((.*?)\\)\\s*;",
          Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

  private SchemaMigrator() {}

  /**
   * 스키마를 최신 선언에 맞춘다. JVM 당 한 번만 실제로 수행된다.
   *
   * <p>어떤 실패도 밖으로 던지지 않는다 — 스키마 보정에 실패했다고 애플리케이션 기동이나 DAO 호출을 막을 수는 없다. 대신 무엇이 실패했는지 로그로 남긴다.
   */
  public static void ensureMigrated() {
    if (!STARTED.compareAndSet(false, true)) {
      return;
    }
    try {
      migrate();
    } catch (Throwable t) {
      log.warn("스키마 마이그레이션 실패: {}", t.getMessage());
    }
  }

  /** 테스트 전용 — 다음 {@link #ensureMigrated()} 가 다시 수행되도록 되돌린다. */
  static void resetForTest() {
    STARTED.set(false);
  }

  private static void migrate() throws Exception {
    Map<String, TableSpec> declared = parseSchema(readSchemaSql());
    if (declared.isEmpty()) {
      log.warn("{} 에서 테이블 선언을 찾지 못했다. 스키마 보정을 건너뛴다.", SCHEMA_RESOURCE);
      return;
    }

    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();
      for (TableSpec table : declared.values()) {
        applyTable(conn, table);
      }
    } finally {
      if (conn != null) {
        try {
          conn.close();
        } catch (Exception ignore) {
        }
      }
    }
  }

  private static void applyTable(LocalDBConnection conn, TableSpec table) {
    Set<String> existing = existingColumns(conn, table.name);

    if (existing.isEmpty()) {
      // 테이블 자체가 없다. schema.sql 의 DDL 을 그대로 실행한다 (자바에 DDL 을 복제하지 않는다).
      try {
        conn.txPstmtExecuteUpdate(table.createStatement);
        log.info("테이블 생성: {}", table.name);
      } catch (Exception e) {
        log.warn("테이블 {} 생성 실패: {}", table.name, e.getMessage());
      }
      return;
    }

    for (ColumnSpec column : table.columns.values()) {
      if (existing.contains(column.name.toLowerCase(Locale.ROOT))) {
        continue;
      }
      if (!column.addableByAlter()) {
        log.warn(
            "컬럼 {}.{} 이 없지만 ALTER 로 추가할 수 없다(PK/UNIQUE/기본값 없는 NOT NULL). 정의: {}",
            table.name,
            column.name,
            column.definition);
        continue;
      }
      try {
        conn.txPstmtExecuteUpdate("ALTER TABLE " + table.name + " ADD COLUMN " + column.definition);
        log.info("컬럼 추가: {}.{}", table.name, column.name);
      } catch (Exception e) {
        log.warn("컬럼 {}.{} 추가 실패: {}", table.name, column.name, e.getMessage());
      }
    }
  }

  /** 테이블이 없으면 빈 집합을 반환한다. 컬럼명은 소문자로 정규화한다(SQLite 는 컬럼명 대소문자를 구분하지 않는다). */
  private static Set<String> existingColumns(LocalDBConnection conn, String tableName) {
    Set<String> columns = new LinkedHashSet<>();
    try {
      ResultSet rs = conn.executeQuery("PRAGMA table_info(" + tableName + ")");
      while (rs != null && rs.next()) {
        String name = rs.getString("name");
        if (name != null) {
          columns.add(name.toLowerCase(Locale.ROOT));
        }
      }
    } catch (Exception e) {
      log.debug("{} 컬럼 조회 실패(테이블 없음으로 간주): {}", tableName, e.getMessage());
    }
    return columns;
  }

  static String readSchemaSql() throws IOException {
    try (InputStream in =
        SchemaMigrator.class.getClassLoader().getResourceAsStream(SCHEMA_RESOURCE)) {
      if (in == null) {
        throw new IOException(SCHEMA_RESOURCE + " 를 클래스패스에서 찾을 수 없다.");
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  // ---------------------------------------------------------------------------------------------
  // 파서 — schema.sql 의 CREATE TABLE 선언을 테이블/컬럼 목록으로 바꾼다.
  // 이 프로젝트가 소유한 파일만 다루므로 SQL 전체 문법이 아니라 실제로 쓰는 형태만 처리한다.
  // ---------------------------------------------------------------------------------------------

  static Map<String, TableSpec> parseSchema(String sql) {
    Map<String, TableSpec> tables = new LinkedHashMap<>();
    String cleaned = stripComments(sql);

    Matcher m = CREATE_TABLE.matcher(cleaned);
    while (m.find()) {
      String tableName = m.group(1);
      String body = m.group(2);
      TableSpec spec = new TableSpec(tableName, m.group().trim(), parseColumns(body));
      tables.put(tableName.toLowerCase(Locale.ROOT), spec);
    }
    return tables;
  }

  /** {@code --} 행 주석을 제거한다. 문자열 리터럴 안의 {@code --} 는 이 파일에 없다. */
  private static String stripComments(String sql) {
    StringBuilder out = new StringBuilder(sql.length());
    for (String line : sql.split("\\R", -1)) {
      int idx = line.indexOf("--");
      out.append(idx >= 0 ? line.substring(0, idx) : line).append('\n');
    }
    return out.toString();
  }

  /**
   * CREATE TABLE 본문을 컬럼 정의로 쪼갠다.
   *
   * <p>괄호 깊이를 세며 최상위 콤마에서만 자른다 — {@code DECIMAL(10,2)} 같은 정의가 잘리지 않게 하기 위해서다. 테이블 제약 ({@code
   * PRIMARY KEY (...)}, {@code FOREIGN KEY ...} 등)은 컬럼이 아니므로 걸러낸다.
   */
  private static Map<String, ColumnSpec> parseColumns(String body) {
    Map<String, ColumnSpec> columns = new LinkedHashMap<>();
    for (String piece : splitTopLevel(body)) {
      String definition = piece.trim().replaceAll("\\s+", " ");
      if (definition.isEmpty()) {
        continue;
      }
      String head = definition.split("\\s+")[0];
      if (isTableConstraint(head)) {
        continue;
      }
      String name = head.replaceAll("[\"'`\\[\\]]", "");
      if (name.isEmpty()) {
        continue;
      }
      columns.put(name.toLowerCase(Locale.ROOT), new ColumnSpec(name, definition));
    }
    return columns;
  }

  private static boolean isTableConstraint(String head) {
    switch (head.toUpperCase(Locale.ROOT)) {
      case "PRIMARY":
      case "FOREIGN":
      case "UNIQUE":
      case "CHECK":
      case "CONSTRAINT":
        return true;
      default:
        return false;
    }
  }

  private static List<String> splitTopLevel(String body) {
    List<String> parts = new ArrayList<>();
    int depth = 0;
    StringBuilder current = new StringBuilder();
    for (int i = 0; i < body.length(); i++) {
      char c = body.charAt(i);
      if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth--;
      }
      if (c == ',' && depth == 0) {
        parts.add(current.toString());
        current.setLength(0);
      } else {
        current.append(c);
      }
    }
    if (current.length() > 0) {
      parts.add(current.toString());
    }
    return parts;
  }

  /** {@code schema.sql} 의 테이블 하나. */
  static final class TableSpec {
    final String name;
    final String createStatement;
    final Map<String, ColumnSpec> columns;

    TableSpec(String name, String createStatement, Map<String, ColumnSpec> columns) {
      this.name = name;
      this.createStatement = createStatement;
      this.columns = columns;
    }
  }

  /** 컬럼 하나. {@code definition} 은 {@code ALTER TABLE ADD COLUMN} 뒤에 그대로 붙일 수 있는 형태다. */
  static final class ColumnSpec {
    final String name;
    final String definition;

    ColumnSpec(String name, String definition) {
      this.name = name;
      this.definition = definition;
    }

    /**
     * SQLite 가 {@code ADD COLUMN} 으로 붙일 수 있는 컬럼인지 판정한다.
     *
     * <p>PRIMARY KEY / UNIQUE 는 SQLite 가 거부한다. 기본값 없는 NOT NULL 도 기존 행을 채울 값이 없어 거부된다. 이런 컬럼은 테이블
     * 재작성이 필요하므로 자동으로 손대지 않는다.
     */
    boolean addableByAlter() {
      String upper = definition.toUpperCase(Locale.ROOT);
      if (upper.contains("PRIMARY KEY") || upper.contains("UNIQUE")) {
        return false;
      }
      return !upper.contains("NOT NULL") || upper.contains("DEFAULT");
    }
  }
}

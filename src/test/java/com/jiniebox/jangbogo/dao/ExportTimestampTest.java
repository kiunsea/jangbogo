package com.jiniebox.jangbogo.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 내보내기·전송 시각 기록 검증 (판단 대기 10).
 *
 * <p>{@code last_export_time} 은 {@code schema.sql} 에 선언만 되어 있고 <b>읽지도 쓰지도 표시하지도 않는 죽은 컬럼</b>이었다.
 * 내보내기 파일이 11개 쌓여 있어도 값이 비어 있던 이유가 그것이다.
 *
 * <p>테스트마다 {@code @TempDir} 의 새 SQLite 파일을 쓴다 — 기준선 DB 에 닿지 않는다.
 *
 * @author KIUNSEA
 */
class ExportTimestampTest {

  private static final String DB_URL_PROPERTY = "jangbogo.localdb.url";

  private String previousUrl;
  private String dbUrl;
  private JbgExportConfigDataAccessObject dao;

  @BeforeEach
  void isolateDatabase(@TempDir Path tempDir) throws Exception {
    previousUrl = System.getProperty(DB_URL_PROPERTY);
    dbUrl = "jdbc:sqlite:" + tempDir.resolve("export-test.db").toString().replace('\\', '/');
    System.setProperty(DB_URL_PROPERTY, dbUrl);
    SchemaMigrator.resetForTest();

    dao = new JbgExportConfigDataAccessObject(); // 생성자가 스키마를 보장한다
    exec("INSERT INTO jbg_export_config (id, save_path) VALUES (1, 'D:/exports')");
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

  private void exec(String sql) throws Exception {
    try (Connection c = DriverManager.getConnection(dbUrl);
        Statement s = c.createStatement()) {
      s.executeUpdate(sql);
    }
  }

  private long longValue(String query) throws Exception {
    try (Connection c = DriverManager.getConnection(dbUrl);
        Statement s = c.createStatement();
        ResultSet r = s.executeQuery(query)) {
      return r.next() ? r.getLong(1) : -1;
    }
  }

  @Test
  @DisplayName("스키마에 두 시각 컬럼이 모두 있다")
  void schemaHasBothTimestamps() throws Exception {
    assertEquals(0, longValue("SELECT COALESCE(last_export_time, 0) FROM jbg_export_config"));
    assertEquals(0, longValue("SELECT COALESCE(last_ftp_upload_time, 0) FROM jbg_export_config"));
  }

  @Test
  @DisplayName("로컬 저장 시각을 기록한다")
  void recordsLocalExportTime() throws Exception {
    dao.touchLastExportTime(1_700_000_000_000L);

    assertEquals(1_700_000_000_000L, longValue("SELECT last_export_time FROM jbg_export_config"));
  }

  @Test
  @DisplayName("FTP 전송 시각을 로컬 저장과 별개로 기록한다")
  void recordsFtpUploadTimeSeparately() throws Exception {
    // 두 경로는 각각의 설정 플래그로 켜고 끌 수 있어 한쪽만 도는 구성이 정상이다.
    // 하나로 뭉치면 "파일은 저장되는데 전송만 죽은" 상태를 구분할 수 없다.
    dao.touchLastExportTime(1_700_000_000_000L);
    dao.touchLastFtpUploadTime(1_800_000_000_000L);

    assertEquals(1_700_000_000_000L, longValue("SELECT last_export_time FROM jbg_export_config"));
    assertEquals(
        1_800_000_000_000L, longValue("SELECT last_ftp_upload_time FROM jbg_export_config"));
  }

  @Test
  @DisplayName("전송 기록이 저장 시각을 덮지 않는다")
  void ftpUploadDoesNotOverwriteExportTime() throws Exception {
    dao.touchLastExportTime(1_700_000_000_000L);
    dao.touchLastFtpUploadTime(1_800_000_000_000L);
    dao.touchLastFtpUploadTime(1_900_000_000_000L);

    assertEquals(1_700_000_000_000L, longValue("SELECT last_export_time FROM jbg_export_config"));
  }

  @Test
  @DisplayName("설정 조회에 두 시각이 함께 실려 나온다")
  void configCarriesBothTimestamps() throws Exception {
    // 값을 쓰기만 하고 조회에 싣지 않으면 다시 죽은 컬럼이 된다.
    dao.touchLastExportTime(1_700_000_000_000L);
    dao.touchLastFtpUploadTime(1_800_000_000_000L);

    JSONObject config = dao.getConfig();

    assertEquals(1_700_000_000_000L, ((Number) config.get("last_export_time")).longValue());
    assertEquals(1_800_000_000_000L, ((Number) config.get("last_ftp_upload_time")).longValue());
  }

  @Test
  @DisplayName("기본 설정에도 두 키가 들어 있다")
  void defaultConfigHasBothKeys() throws Exception {
    exec("DELETE FROM jbg_export_config");

    JSONObject config = dao.getConfig();

    assertTrue(config.containsKey("last_export_time"), "키가 없으면 화면이 undefined 를 그린다.");
    assertTrue(config.containsKey("last_ftp_upload_time"));
  }

  @Test
  @DisplayName("설정 행이 없어도 예외를 밖으로 내지 않는다")
  void doesNotThrowWhenConfigRowMissing() throws Exception {
    // 기록 실패가 내보내기 자체를 되돌릴 이유는 없다.
    exec("DELETE FROM jbg_export_config");

    dao.touchLastExportTime(1_700_000_000_000L);
    dao.touchLastFtpUploadTime(1_700_000_000_000L);
  }
}

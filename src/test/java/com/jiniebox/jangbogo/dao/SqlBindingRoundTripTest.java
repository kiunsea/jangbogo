package com.jiniebox.jangbogo.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * 값을 SQL 에 이어 붙이지 않고 바인딩으로 넘기는지 <b>실제 SQLite 파일에 대고</b> 확인한다.
 *
 * <h2>소스 검사만으로는 왜 부족한가</h2>
 *
 * <p>{@code SecurityHardeningTest} 는 소스에 조립 흔적이 남았는지를 본다. 그것만으로는 "형태는 바꿔 놓았는데 조회가 더 이상 안 걸리는" 경우를 못
 * 잡는다. 특히 {@code jbg_order.date_time} 은 INTEGER 컬럼인데 호출부가 문자열을 넘긴다 — SQLite 의 숫자 친화도 변환이 기대대로 돌지
 * 않으면 <b>중복 판정이 조용히 항상 거짓</b>이 되어 같은 주문이 매 회차 다시 쌓인다. 숫자를 그대로 이어 붙이던 시절에는 없던 위험이라, 바꾼 쪽이 책임지고 확인해야
 * 한다.
 *
 * <p>그래서 왕복을 본다. 따옴표가 섞인 값을 넣고 <b>같은 값으로 다시 찾는다.</b> 이어 붙이는 구현으로 되돌리면 여기 절반이 깨진다.
 *
 * <p>{@code @TempDir} 의 새 파일을 쓴다 — 기준선 DB 에 닿지 않는다. 브라우저·네트워크를 쓰지 않는다.
 *
 * @author KIUNSEA
 */
class SqlBindingRoundTripTest {

  private static final String DB_URL_PROPERTY = "jangbogo.localdb.url";

  /** 따옴표가 섞인 합성 영수증 번호. 수집기가 페이지 텍스트에서 만들어 내는 값에는 숫자 필터가 없다. */
  private static final String SERIAL_WITH_QUOTE = "TEST-O'BRIEN-01";

  /** 조건을 통째로 바꿔치기하려는 고전적인 형태. 값으로만 다뤄져야 한다. */
  private static final String INJECTION = "' OR '1'='1";

  /** 합성 구매일자. 실제 수집분과 무관한 고정값이다. */
  private static final String DATE = "20200101";

  private static final String OTHER_DATE = "20200102";

  /** base64 는 '+' 와 '/' 를 쓴다. 여기에 따옴표까지 섞어 최악을 만든 합성 값이다. */
  private static final String KEY_WITH_QUOTE = "k+e/y==';--";

  private static final String IV_WITH_QUOTE = "i+v/==';--";

  private String previousUrl;
  private String dbUrl;

  @BeforeEach
  void isolateDatabase(@TempDir Path tempDir) throws Exception {
    previousUrl = System.getProperty(DB_URL_PROPERTY);
    dbUrl = "jdbc:sqlite:" + tempDir.resolve("sql-binding-test.db").toString().replace('\\', '/');
    System.setProperty(DB_URL_PROPERTY, dbUrl);

    // 마이그레이터는 JVM 당 1회만 실제 작업을 한다. 되돌리지 않으면 이 임시 DB 에 테이블이 생기지 않는다.
    SchemaMigrator.resetForTest();
    SchemaMigrator.ensureMigrated();

    try (Connection c = DriverManager.getConnection(dbUrl);
        Statement s = c.createStatement()) {
      s.executeUpdate(
          "INSERT INTO jbg_mall (seq, id, name, account_status) VALUES (1, 'ssg', '테스트몰', 0)");
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

  // ---------------------------------------------------------------
  // jbg_order — 중복 방지 조회 (수집기가 매 회차 부른다)
  // ---------------------------------------------------------------

  @Test
  @DisplayName("따옴표가 섞인 영수증 번호도 저장한 그대로 다시 찾는다")
  void findsOrderWhoseSerialContainsQuote() throws Exception {
    JbgOrderDataAccessObject dao = new JbgOrderDataAccessObject();
    int seq = dao.add(SERIAL_WITH_QUOTE, DATE, "테스트몰", "1");
    assertTrue(seq > 0, "주문 등록 자체가 실패했다 — 이후 판정이 의미가 없다.");

    JSONObject found = dao.getOrder(SERIAL_WITH_QUOTE, DATE, null);

    assertNotNull(found, "이어 붙이면 여기서 SQL 이 깨진다. 중복 판정이 무너지면 같은 주문이 매 회차 다시 쌓인다.");
    assertEquals(seq, ((Number) found.get("seq")).intValue());
  }

  @Test
  @DisplayName("주입 문자열은 조건이 아니라 값으로만 다뤄진다")
  void injectionStringIsTreatedAsAValue() throws Exception {
    JbgOrderDataAccessObject dao = new JbgOrderDataAccessObject();
    dao.add("TEST-0001", DATE, "테스트몰", "1");

    // 이어 붙이던 시절에는 serial_num='' OR '1'='1' 이 되어 남의 주문을 그대로 열어 줬다.
    assertNull(dao.getOrder(INJECTION, DATE, null), "주입 문자열이 조건으로 해석됐다.");
  }

  @Test
  @DisplayName("구매일자는 INTEGER 컬럼이지만 문자열 바인딩으로도 정확히 걸러진다")
  void dateFilterStillDiscriminates() throws Exception {
    // 조립을 걷어내면서 새로 생긴 위험이다. 친화도 변환이 안 되면 조회가 항상 비어 오고,
    // 그 증상은 "수집은 도는데 같은 주문이 계속 늘어난다" 로만 보인다.
    JbgOrderDataAccessObject dao = new JbgOrderDataAccessObject();
    dao.add("TEST-0002", DATE, "테스트몰", "1");

    assertNotNull(dao.getOrder("TEST-0002", DATE, null), "숫자 친화도 변환이 기대대로 돌지 않는다.");
    assertNull(dao.getOrder("TEST-0002", OTHER_DATE, null), "날짜 조건이 무시됐다.");
  }

  // ---------------------------------------------------------------
  // jbg_export_config — ftp_pass 는 암호문이라 특수문자가 섞인다
  // ---------------------------------------------------------------

  @Test
  @DisplayName("따옴표가 섞인 FTP 설정을 새로 만들고 그대로 읽는다")
  void insertsExportConfigWithQuotedValues() throws Exception {
    JbgExportConfigDataAccessObject dao = new JbgExportConfigDataAccessObject();
    dao.updateConfig("exports'dir", "json", 1, 1, "ftp.example.test", "us'er", "pa'ss", "", 1);

    JSONObject config = dao.getConfig();

    assertEquals("exports'dir", config.get("save_path"));
    assertEquals("us'er", config.get("ftp_id"));
    assertEquals("pa'ss", dao.getDecryptedFtpPassword(), "암호문이 깨지면 FTP 전송만 조용히 실패한다.");
  }

  @Test
  @DisplayName("두 번째 저장은 UPDATE 경로로 가고 값이 덮인다")
  void secondSaveTakesUpdatePath() throws Exception {
    // INSERT 와 UPDATE 두 벌이 따로 있다. 한쪽만 고치면 갈라지므로 둘 다 밟는다.
    JbgExportConfigDataAccessObject dao = new JbgExportConfigDataAccessObject();
    dao.updateConfig("exports'dir", "json", 1, 1, "ftp.example.test", "us'er", "pa'ss", "", 1);
    dao.updateConfig("other'dir", "csv", 0, 0, "ftp2.example.test", "id2", "new'pass", "", 0);

    JSONObject config = dao.getConfig();

    assertEquals("other'dir", config.get("save_path"));
    assertEquals("csv", config.get("save_format"));
    assertEquals("new'pass", dao.getDecryptedFtpPassword());
  }

  @Test
  @DisplayName("비밀번호를 넘기지 않으면 기존 암호문이 유지된다")
  void blankPasswordKeepsExistingSecret() throws Exception {
    JbgExportConfigDataAccessObject dao = new JbgExportConfigDataAccessObject();
    dao.updateConfig("exports'dir", "json", 1, 1, "ftp.example.test", "us'er", "pa'ss", "", 1);
    dao.updateConfig("exports'dir", "json", 1, 1, "ftp.example.test", "us'er", null, "", 1);

    assertEquals("pa'ss", dao.getDecryptedFtpPassword(), "빈 입력이 기존 비밀번호를 지웠다.");
  }

  // ---------------------------------------------------------------
  // jbg_mall — encrypt_key/iv 가 깨지면 계정 복호화만 조용히 실패한다
  // ---------------------------------------------------------------

  @Test
  @DisplayName("암호화 키에 따옴표가 섞여도 저장한 그대로 읽힌다")
  void mallEncryptionKeysSurviveRoundTrip() throws Exception {
    JbgMallDataAccessObject dao = new JbgMallDataAccessObject();
    dao.update("1", 1, KEY_WITH_QUOTE, IV_WITH_QUOTE);

    JSONObject info = dao.getAccessInfo("1");

    assertNotNull(info, "몰을 찾지 못했다.");
    assertEquals(KEY_WITH_QUOTE, info.get("enckey"), "키가 깨지면 계정 비밀번호를 다시 열 수 없다.");
    assertEquals(IV_WITH_QUOTE, info.get("enciv"));
    assertEquals(1, ((Number) info.get("status")).intValue());
  }

  @Test
  @DisplayName("키를 넘기지 않은 갱신은 기존 키를 건드리지 않는다")
  void mallUpdateKeepsKeysWhenNotGiven() throws Exception {
    // SET 절이 값의 유무에 따라 늘고 주는 성질이 이 메서드의 계약이다. 바인딩으로 바꾸면서
    // 이 성질을 잃으면 계정 상태만 바꾸려던 호출이 키를 날린다.
    JbgMallDataAccessObject dao = new JbgMallDataAccessObject();
    dao.update("1", 1, KEY_WITH_QUOTE, IV_WITH_QUOTE);
    dao.update("1", 0, null, null);

    JSONObject info = dao.getAccessInfo("1");

    assertEquals(KEY_WITH_QUOTE, info.get("enckey"), "상태만 바꾸려던 갱신이 키를 지웠다.");
    assertEquals(IV_WITH_QUOTE, info.get("enciv"));
    assertEquals(0, ((Number) info.get("status")).intValue());
  }

  @Test
  @DisplayName("add 는 키와 함께 접속 시각도 기록한다")
  void mallAddStampsSigninTime() throws Exception {
    JbgMallDataAccessObject dao = new JbgMallDataAccessObject();
    long before = System.currentTimeMillis();
    dao.add("1", 1, KEY_WITH_QUOTE, IV_WITH_QUOTE);

    JSONObject info = dao.getAccessInfo("1");

    assertEquals(KEY_WITH_QUOTE, info.get("enckey"));
    assertTrue(((Number) info.get("time")).longValue() >= before, "last_signin_time 이 기록되지 않았다.");
  }
}

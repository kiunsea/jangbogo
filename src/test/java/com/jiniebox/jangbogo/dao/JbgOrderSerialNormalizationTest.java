package com.jiniebox.jangbogo.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 주문번호 <b>표기</b>가 달라도 같은 주문으로 맞닿는지 <b>실제 SQLite 파일에 대고</b> 확인한다.
 *
 * <p>정규화 함수의 단위테스트({@code OrderSerialNormalizerTest})만으로는 부족하다 — 함수는 맞는데 DAO 가 그것을 거치지 않으면 똑같이 중복이
 * 난다. 그래서 DAO 의 세 입구(두 INSERT, 중복 조회)를 실제로 밟는다.
 *
 * <p>특히 <b>이 수정 전에 꼬리표째 저장된 행</b>을 흉내 낸다. 운영 SQLite 에는 오아시스 주문이 {@code 주문번호 : 00-…} 로 들어가 있다. 파서만
 * 고치면 다음 회차의 깨끗한 값이 그 행들과 문자열로 안 맞아 같은 주문이 한 번 더 쌓이고, 그대로 수신측으로 다시 나간다. 여기 절반은 그 시나리오다.
 *
 * <p>{@code @TempDir} 의 새 파일을 쓴다 — 기준선 DB 에 닿지 않는다. 브라우저·네트워크를 쓰지 않는다. 주문번호·구매일자는 전부 <b>합성</b>이다.
 *
 * @author KIUNSEA
 */
class JbgOrderSerialNormalizationTest {

  private static final String DB_URL_PROPERTY = "jangbogo.localdb.url";

  /** 합성 주문번호. 오아시스 형태만 빌렸다. */
  private static final String BARE = "00-1234567890-12345-1234";

  private static final String LABELLED = "주문번호 : " + BARE;

  private static final String DATE = "20200101";

  private static final String OTHER_DATE = "20200102";

  private String previousUrl;
  private String dbUrl;

  @BeforeEach
  void isolateDatabase(@TempDir Path tempDir) throws Exception {
    previousUrl = System.getProperty(DB_URL_PROPERTY);
    dbUrl =
        "jdbc:sqlite:"
            + tempDir.resolve("serial-normalization-test.db").toString().replace('\\', '/');
    System.setProperty(DB_URL_PROPERTY, dbUrl);
    SchemaTestSupport.remigrate();
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
  @DisplayName("꼬리표째 저장돼 있던 주문을 깨끗한 주문번호로 찾는다 — 파서를 고친 뒤 첫 회차")
  void findsLegacyRowStoredWithLabel() throws Exception {
    // 정규화 이전 코드가 넣은 행을 그대로 흉내 낸다 — DAO 를 거치지 않고 꼬리표째 넣는다.
    int legacySeq = insertRaw(LABELLED, DATE);

    JSONObject found = new JbgOrderDataAccessObject().getOrder(BARE, DATE, null);

    assertNotNull(found, "꼬리표째 저장된 행을 못 찾으면 같은 주문이 한 번 더 쌓이고, 그대로 수신측으로 다시 나간다.");
    assertEquals(legacySeq, ((Number) found.get("seq")).intValue());
  }

  @Test
  @DisplayName("깨끗하게 저장된 주문을 꼬리표째 들어온 값으로도 찾는다")
  void findsCleanRowWhenLabelledValueComesAgain() throws Exception {
    JbgOrderDataAccessObject dao = new JbgOrderDataAccessObject();
    int seq = dao.add(BARE, DATE, "테스트몰", "2");

    JSONObject found = dao.getOrder(LABELLED, DATE, null);

    assertNotNull(found, "들어온 쪽만 정규화하고 저장된 쪽을 안 하면 반대 방향에서 같은 중복이 난다.");
    assertEquals(seq, ((Number) found.get("seq")).intValue());
  }

  @Test
  @DisplayName("저장할 때 꼬리표·괄호를 벗긴다 — 테이블에는 한 가지 모양만 남는다")
  void addStoresNormalizedSerial() throws Exception {
    int seq = new JbgOrderDataAccessObject().add("주문번호 : (" + BARE + ")", DATE, "테스트몰", "2");

    assertEquals(BARE, rawSerialOf(seq));
  }

  @Test
  @DisplayName("트랜잭션 안의 INSERT 도 같은 규칙이다 — 두 벌이 갈라지면 한쪽만 깨끗해진다")
  void addWithConnectionStoresNormalizedSerial() throws Exception {
    // 수집기가 실제로 쓰는 쪽은 이쪽이다(MallOrderUpdaterRunner). add(...) 만 고치면 아무것도 안 고친 것이다.
    LocalDBConnection conn = null;
    int seq;
    try {
      conn = new LocalDBConnection();
      conn.txOpen();
      seq =
          new JbgOrderDataAccessObject()
              .addWithConnection(conn, LABELLED, DATE, "테스트몰", "2", "oasis");
      conn.txCommit();
    } finally {
      if (conn != null) {
        conn.close();
      }
    }

    assertEquals(BARE, rawSerialOf(seq));
  }

  @Test
  @DisplayName("다른 주문번호·다른 날짜는 여전히 다른 주문이다 — 정규화가 식별을 뭉개지 않는다")
  void differentOrdersStayDifferent() throws Exception {
    JbgOrderDataAccessObject dao = new JbgOrderDataAccessObject();
    dao.add(BARE, DATE, "테스트몰", "2");

    assertNull(dao.getOrder("00-1234567890-12345-1235", DATE, null), "끝자리만 다른 주문을 같은 주문으로 봤다.");
    assertNull(dao.getOrder(BARE, OTHER_DATE, null), "날짜 조건이 무시됐다.");
  }

  // ---------------------------------------------------------------- helpers

  /** DAO 를 거치지 않고 행을 넣는다 — 정규화 이전 코드가 남긴 데이터를 흉내 내기 위해서다. */
  private int insertRaw(String serial, String date) throws Exception {
    try (Connection c = DriverManager.getConnection(dbUrl);
        PreparedStatement ps =
            c.prepareStatement(
                "INSERT INTO jbg_order (serial_num, date_time, mall_name, seq_mall)"
                    + " VALUES (?, ?, ?, ?)")) {
      ps.setString(1, serial);
      ps.setString(2, date);
      ps.setString(3, "테스트몰");
      ps.setInt(4, 2);
      ps.executeUpdate();
    }
    try (Connection c = DriverManager.getConnection(dbUrl);
        PreparedStatement ps = c.prepareStatement("SELECT MAX(seq) FROM jbg_order");
        ResultSet rs = ps.executeQuery()) {
      assertTrue(rs.next());
      return rs.getInt(1);
    }
  }

  private String rawSerialOf(int seq) throws Exception {
    try (Connection c = DriverManager.getConnection(dbUrl);
        PreparedStatement ps =
            c.prepareStatement("SELECT serial_num FROM jbg_order WHERE seq = ?")) {
      ps.setInt(1, seq);
      try (ResultSet rs = ps.executeQuery()) {
        assertTrue(rs.next(), "seq " + seq + " 행이 없다.");
        return rs.getString(1);
      }
    }
  }
}

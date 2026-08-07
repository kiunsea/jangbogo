package com.jiniebox.jangbogo.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 따옴표 없는 <b>숫자 자리</b>(seq)가 SQL 을 깨뜨리지도, 대상을 바꿔치기하지도 못하는지 <b>실제 SQLite 파일에 대고</b> 확인한다.
 *
 * <h2>왜 따로 필요한가</h2>
 *
 * <p>{@code SqlBindingRoundTripTest} 가 보는 것은 <b>따옴표로 감싼 값</b>이다. 통합 검증에서 드러난 것은 그 바깥이었다 — {@code
 * WHERE seq=} 뒤에 값을 따옴표 없이 이어 붙이던 자리들이고, {@code AdminController} 의 {@code @RequestParam} 문자열이 숫자 필터
 * 하나 없이 거기까지 닿았다.
 *
 * <h2>필터가 방어가 아니었다는 것도 여기서 본다</h2>
 *
 * <p>일부 자리는 {@code replaceAll("[^0-9]", "")} 로 막아 둔 것처럼 보였지만, 그 필터는 거르는 것이 아니라 <b>깎는다.</b> 숫자가 섞인
 * 문자열은 남은 숫자만 이어 붙은 <b>다른 유효한 seq</b> 가 되어 통과한다. SQL 은 멀쩡하고 대상만 조용히 바뀌므로 로그로는 알아낼 방법이 없다. 그 형태를
 * {@link #MANGLES_TO_ANOTHER_MALL} 로 못 박아 둔다.
 *
 * <p>{@code @TempDir} 의 새 파일을 쓴다 — 기준선 DB 에 닿지 않는다. 브라우저·네트워크를 쓰지 않는다.
 *
 * @author KIUNSEA
 */
class SeqBindingSafetyTest {

  private static final String DB_URL_PROPERTY = "jangbogo.localdb.url";

  /** 실제로 쓰는 몰. */
  private static final long MALL = 1L;

  /**
   * 두 번째 몰. seq 를 11 로 잡은 데 이유가 있다 — {@link #MANGLES_TO_ANOTHER_MALL} 을 숫자만 남기고 깎으면 정확히 이 값이 된다.
   */
  private static final long OTHER_MALL = 11L;

  /** 두 번째 주문. 아이템 삭제에서 같은 함정을 재현하려고 몰과 같은 숫자를 쓴다. */
  private static final long OTHER_ORDER = 11L;

  /**
   * 숫자 필터를 통과해 <b>다른 몰</b>이 되는 문자열.
   *
   * <p>{@code replaceAll("[^0-9]", "")} 를 거치면 {@code 11} 이 된다. 예전 코드는 이 값을 "정화됐다" 고 보고 {@link
   * #OTHER_MALL} 의 행을 열어 줬다. 차단해야 할 입력이 조용히 남의 데이터로 이어지는, 필터 단독 방어의 실제 실패 형태다.
   */
  private static final String MANGLES_TO_ANOTHER_MALL = "1a1";

  /** 조건을 통째로 바꿔치기하려는 고전적인 형태. 숫자 자리에는 따옴표조차 필요 없다. */
  private static final String INJECTION = "1 OR 1=1";

  /** 문장을 끊고 뒤에 파괴적인 문장을 붙이려는 형태. */
  private static final String DROP_ATTEMPT = "1; DROP TABLE jbg_mall";

  private String previousUrl;
  private String dbUrl;

  @BeforeEach
  void isolateDatabase(@TempDir Path tempDir) throws Exception {
    previousUrl = System.getProperty(DB_URL_PROPERTY);
    dbUrl = "jdbc:sqlite:" + tempDir.resolve("seq-binding-test.db").toString().replace('\\', '/');
    System.setProperty(DB_URL_PROPERTY, dbUrl);

    // 마이그레이터는 JVM 당 1회만 실제 작업을 한다. 되돌리지 않으면 이 임시 DB 에 테이블이 생기지 않는다.
    SchemaMigrator.resetForTest();
    SchemaMigrator.ensureMigrated();

    try (Connection c = DriverManager.getConnection(dbUrl);
        Statement s = c.createStatement()) {
      s.executeUpdate(
          "INSERT INTO jbg_mall (seq, id, name, account_status) VALUES (1, 'ssg', '테스트몰A', 1)");
      s.executeUpdate(
          "INSERT INTO jbg_mall (seq, id, name, account_status) VALUES (11, 'oasis', '테스트몰B', 1)");
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
  // jbg_mall — 조회
  // ---------------------------------------------------------------

  @Test
  @DisplayName("몰 조회에 주입 문자열을 넣어도 SQL 이 깨지지 않고 빈 결과가 나온다")
  void mallLookupSurvivesInjectionStrings() throws Exception {
    JbgMallDataAccessObject dao = new JbgMallDataAccessObject();

    // 이어 붙이던 시절에는 여기서 SQL 문법 오류가 났다. 예외가 나든 결과가 나든, 어느 쪽도
    // "입력이 조건이 아니라 값으로 다뤄졌다" 는 뜻은 아니었다.
    assertNull(dao.getMall(INJECTION), "주입 문자열이 몰을 열었다.");
    assertNull(dao.getMall(DROP_ATTEMPT), "문장 끊기가 통했다.");
    assertNull(dao.getMall("'; --"), "따옴표 섞인 값이 조건으로 해석됐다.");

    // 테이블이 살아 있어야 위의 null 이 '못 찾았다' 라는 뜻이 된다. 지워졌어도 null 이기 때문이다.
    assertNotNull(dao.getMall("1"), "정상 조회가 깨졌다 — 테이블이 남아 있는지부터 확인할 것.");
  }

  @Test
  @DisplayName("숫자가 섞인 seq 를 깎아서 다른 몰을 열지 않는다")
  void digitFilteringDoesNotRedirectToAnotherMall() throws Exception {
    JbgMallDataAccessObject dao = new JbgMallDataAccessObject();
    dao.saveSessionSnapshotKeys(String.valueOf(OTHER_MALL), "key-b", "iv-b", 1L);

    // 제대로 된 seq 로는 읽힌다 — 아래 null 이 "그 행이 원래 비어 있었다" 가 아님을 못 박는다.
    assertNotNull(
        dao.getSessionSnapshotKeys(String.valueOf(OTHER_MALL)), "정상 seq 로도 스냅샷 키를 못 읽는다.");

    assertNull(
        dao.getSessionSnapshotKeys(MANGLES_TO_ANOTHER_MALL),
        "숫자만 남기고 깎은 seq 가 남의 몰 스냅샷 키를 열었다. 세션 토큰을 여는 열쇠다.");
    assertNull(dao.getMall(MANGLES_TO_ANOTHER_MALL), "숫자만 남기고 깎은 seq 가 남의 몰 정보를 열었다.");
    assertNull(dao.getName(MANGLES_TO_ANOTHER_MALL), "숫자만 남기고 깎은 seq 가 남의 몰 이름을 열었다.");
  }

  @Test
  @DisplayName("계정 상태 조회는 숫자가 아닌 seq 에 미등록(-1)을 돌린다")
  void accountStatusLookupReportsUnregisteredForNonNumericSeq() throws Exception {
    JbgMallDataAccessObject dao = new JbgMallDataAccessObject();

    // -1 은 '미등록' 이다. 호출부(JangBoGoManager)는 이 값을 받으면 add(...) 로 넘어가고 거기서
    // 다시 막히므로, 잘못된 seq 가 성공으로 흐르지는 않는다.
    assertEquals(-1, dao.checkAccountStatus(INJECTION), "주입 문자열이 계정 상태를 읽었다.");
    assertEquals(-1, dao.checkAccountStatus(""), "빈 seq 가 계정 상태를 읽었다.");
    assertEquals(1, dao.checkAccountStatus("1"), "정상 조회가 깨졌다.");
  }

  @Test
  @DisplayName("접속 정보 조회도 주입 문자열에 빈 결과를 돌린다")
  void accessInfoLookupReturnsNothingForInjection() throws Exception {
    JbgMallDataAccessObject dao = new JbgMallDataAccessObject();

    assertNull(dao.getAccessInfo(INJECTION), "주입 문자열이 접속 정보를 열었다 — 여기에는 암호화 키가 실린다.");
    assertNull(dao.getAccessInfo(MANGLES_TO_ANOTHER_MALL), "깎인 seq 가 남의 몰 암호화 키를 열었다.");
    assertNotNull(dao.getAccessInfo("1"), "정상 조회가 깨졌다.");
  }

  // ---------------------------------------------------------------
  // jbg_mall — 갱신
  // ---------------------------------------------------------------

  @Test
  @DisplayName("계정 상태 저장은 숫자가 아닌 seq 를 거부하고 다른 몰을 건드리지 않는다")
  void accountStatusWriteRejectsNonNumericSeq() throws Exception {
    JbgMallDataAccessObject dao = new JbgMallDataAccessObject();

    // 갱신은 조회와 다르게 값으로 넘기지 않는다. 대상이 없는 갱신을 성공으로 보고하면 화면은
    // 저장됐다고 말하고 DB 는 그대로인, 알아채기 가장 어려운 형태가 된다.
    assertThrows(IllegalArgumentException.class, () -> dao.setAccountStatus(INJECTION, 0));
    assertThrows(
        IllegalArgumentException.class, () -> dao.updateLastSigninTime(MANGLES_TO_ANOTHER_MALL));

    assertEquals(1, intColumn("SELECT account_status FROM jbg_mall WHERE seq=?", MALL));
    assertEquals(1, intColumn("SELECT account_status FROM jbg_mall WHERE seq=?", OTHER_MALL));
  }

  @Test
  @DisplayName("수집주기 저장은 숫자가 아닌 seq 를 거부한다")
  void collectIntervalWriteRejectsNonNumericSeq() throws Exception {
    JbgMallDataAccessObject dao = new JbgMallDataAccessObject();

    assertThrows(IllegalArgumentException.class, () -> dao.updateCollectInterval(INJECTION, 0));
    assertEquals(0, intColumn("SELECT collect_interval_minutes FROM jbg_mall WHERE seq=?", MALL));
  }

  @Test
  @DisplayName("자동수집 저장은 깎으면 다른 몰이 되는 seq 를 건너뛴다")
  void autoCollectSaveSkipsSeqThatWouldBeMangled() throws Exception {
    JbgMallDataAccessObject dao = new JbgMallDataAccessObject();

    dao.saveAutoCollectFlags(List.of("1", MANGLES_TO_ANOTHER_MALL), null);

    assertEquals(
        1, intColumn("SELECT auto_collect FROM jbg_mall WHERE seq=?", MALL), "정상 seq 가 안 켜졌다.");
    assertEquals(
        0,
        intColumn("SELECT auto_collect FROM jbg_mall WHERE seq=?", OTHER_MALL),
        "체크하지도 않은 몰의 자동수집이 켜졌다. 사용자가 켠 적 없는 계정으로 로그인이 돌기 시작한다.");
  }

  @Test
  @DisplayName("세션 옵트인 저장은 숫자가 아닌 seq 를 거부하고 다른 몰의 옵트인을 건드리지 않는다")
  void sessionProfileOptInWriteRejectsNonNumericSeq() throws Exception {
    JbgMallDataAccessObject dao = new JbgMallDataAccessObject();

    // 옆 몰만 켜 둔 상태에서 시작한다. 이 값이 뒤집히는 사고는 화면에 아무 흔적을 남기지 않는다 —
    // 꺼지면 그 몰은 다음 회차부터 조용히 예전 경로로 돌고, 세션을 떠 두지 않은 몰에서 켜지면
    // 게이트가 PROFILE_MISSING 으로 막아 수집이 통째로 사라진다. 어느 쪽이든 사용자는 모른다.
    dao.setSessionProfileEnabled(String.valueOf(OTHER_MALL), true);

    assertThrows(
        IllegalArgumentException.class, () -> dao.setSessionProfileEnabled(INJECTION, false));
    assertThrows(
        IllegalArgumentException.class, () -> dao.setSessionProfileEnabled(DROP_ATTEMPT, false));
    // 깎는 필터가 되살아나면 이 값이 11 이 되어 OTHER_MALL 의 옵트인을 끈다.
    assertThrows(
        IllegalArgumentException.class,
        () -> dao.setSessionProfileEnabled(MANGLES_TO_ANOTHER_MALL, false));

    assertEquals(
        1,
        intColumn("SELECT session_profile_enabled FROM jbg_mall WHERE seq=?", OTHER_MALL),
        "거부됐어야 할 seq 가 남의 몰 옵트인을 껐다. 그 몰은 다음 회차부터 예전 경로로 조용히 돌아간다.");
    assertEquals(
        0,
        intColumn("SELECT session_profile_enabled FROM jbg_mall WHERE seq=?", MALL),
        "켠 적 없는 몰의 옵트인이 켜졌다.");

    // 정상 seq 로는 실제로 저장된다 — 위의 '안 바뀜' 이 DAO 가 원래 아무 일도 못 하는 것이
    // 아니라 거부한 결과임을 못 박는다.
    assertTrue(dao.setSessionProfileEnabled(String.valueOf(MALL), true), "정상 저장이 깨졌다.");
    assertEquals(1, intColumn("SELECT session_profile_enabled FROM jbg_mall WHERE seq=?", MALL));
    assertEquals(
        1,
        intColumn("SELECT session_profile_enabled FROM jbg_mall WHERE seq=?", OTHER_MALL),
        "한 몰을 켜는 요청이 다른 몰의 옵트인까지 건드렸다.");
  }

  // ---------------------------------------------------------------
  // 롤백 — 여러 문장을 한 트랜잭션에 묶은 자리
  // ---------------------------------------------------------------

  @Test
  @DisplayName("자동수집 저장이 SQL 실패로 끝나면 앞선 갱신까지 되돌린다")
  void autoCollectSaveRollsBackEarlierWritesOnSqlFailure() throws Exception {
    // saveAutoCollectFlags 는 한 트랜잭션에서 (1) 전체 auto_collect 초기화 (2) 선택분 켜기
    // (3) 주기 갱신 을 차례로 한다. (3) 이 깨졌는데 (1) 이 남으면 화면에서 켜 두었던 몰이
    // 전부 조용히 꺼진다 — 사용자는 저장에 실패했다는 말만 듣고 설정은 이미 날아간 상태다.
    //
    // 마지막 UPDATE 만 실패시키려고 트리거를 쓴다. 대상 컬럼을 명시했으므로 (1)(2) 는 통과한다.
    try (Connection c = DriverManager.getConnection(dbUrl);
        Statement s = c.createStatement()) {
      s.executeUpdate("UPDATE jbg_mall SET auto_collect=1 WHERE seq=11");
      s.executeUpdate(
          "CREATE TRIGGER block_interval BEFORE UPDATE OF collect_interval_minutes ON jbg_mall"
              + " BEGIN SELECT RAISE(ABORT, 'blocked by test'); END");
    }

    JbgMallDataAccessObject dao = new JbgMallDataAccessObject();

    assertThrows(
        SQLException.class,
        () -> dao.saveAutoCollectFlags(List.of("1"), Map.of("1", 0)),
        "트리거가 막지 못했다 — 이 테스트는 실패를 만들어 내야 의미가 있다.");

    assertEquals(
        1,
        intColumn("SELECT auto_collect FROM jbg_mall WHERE seq=?", OTHER_MALL),
        "실패한 트랜잭션의 초기화가 남았다. 켜 두었던 몰이 조용히 꺼진 상태다.");
    assertEquals(
        0,
        intColumn("SELECT auto_collect FROM jbg_mall WHERE seq=?", MALL),
        "실패한 트랜잭션의 부분 갱신이 남았다.");
  }

  // ---------------------------------------------------------------
  // jbg_item — deleteByOrder 는 이 저장소에서 가장 위험한 WHERE 다
  // ---------------------------------------------------------------

  @Test
  @DisplayName("아이템 조회는 주입 문자열에 빈 결과를 돌린다")
  void itemLookupReturnsNothingForInjection() throws Exception {
    JbgItemDataAccessObject dao = new JbgItemDataAccessObject();
    int seq = dao.add("사과", "1", "2");
    assertTrue(seq > 0, "아이템 등록 자체가 실패했다 — 이후 판정이 의미가 없다.");

    assertNull(dao.getItem(INJECTION), "주입 문자열이 아이템을 열었다.");
    assertNotNull(dao.getItem(String.valueOf(seq)), "정상 조회가 깨졌다.");

    assertTrue(dao.getItemsByOrder(INJECTION).isEmpty(), "주입 문자열이 주문의 아이템을 열었다.");
    assertFalse(dao.getItemsByOrder("1").isEmpty(), "정상 조회가 깨졌다.");
  }

  @Test
  @DisplayName("주문 단위 삭제는 주입 문자열로 아무 것도 지우지 않는다")
  void deleteByOrderRefusesInjectionAndKeepsEveryRow() throws Exception {
    JbgItemDataAccessObject dao = new JbgItemDataAccessObject();
    dao.add("사과", "1", "2");
    dao.add("배", String.valueOf(OTHER_ORDER), "1");

    // WHERE 절이 깨지면 지우려던 한 주문이 아니라 테이블 전체가 대상이 된다. 수집분은 되돌릴 수 없다.
    assertFalse(dao.deleteByOrder(INJECTION), "주입 문자열이 삭제를 수행했다고 보고했다.");
    assertFalse(dao.deleteByOrder("1 OR 1=1; DELETE FROM jbg_item"), "문장 끊기가 통했다.");
    assertFalse(dao.deleteByOrder(MANGLES_TO_ANOTHER_MALL), "깎인 seq 가 삭제 대상을 바꿨다.");

    assertEquals(1, intColumn("SELECT COUNT(*) FROM jbg_item WHERE seq_order=?", 1L));
    assertEquals(
        1,
        intColumn("SELECT COUNT(*) FROM jbg_item WHERE seq_order=?", OTHER_ORDER),
        "깎인 seq 가 남의 주문에 딸린 아이템을 지웠다.");
  }

  @Test
  @DisplayName("아이템 삭제도 숫자가 아닌 seq 에 아무 것도 지우지 않는다")
  void deleteRefusesNonNumericSeq() throws Exception {
    JbgItemDataAccessObject dao = new JbgItemDataAccessObject();
    int seq = dao.add("사과", "1", "2");

    assertFalse(dao.delete(INJECTION), "주입 문자열이 삭제를 수행했다고 보고했다.");
    assertEquals(1, intColumn("SELECT COUNT(*) FROM jbg_item WHERE seq=?", seq));

    assertTrue(dao.delete(String.valueOf(seq)), "정상 삭제가 깨졌다.");
    assertEquals(0, intColumn("SELECT COUNT(*) FROM jbg_item WHERE seq=?", seq));
  }

  // ---------------------------------------------------------------

  /** DAO 를 거치지 않고 값을 직접 확인한다. DAO 가 거짓말을 해도 여기서는 드러난다. */
  private int intColumn(String sql, long key) throws Exception {
    try (Connection c = DriverManager.getConnection(dbUrl);
        PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setLong(1, key);
      try (ResultSet rset = ps.executeQuery()) {
        return rset.next() ? rset.getInt(1) : -1;
      }
    }
  }
}

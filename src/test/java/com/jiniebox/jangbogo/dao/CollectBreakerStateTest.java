package com.jiniebox.jangbogo.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jiniebox.jangbogo.svc.util.CollectBreakerPolicy;
import com.jiniebox.jangbogo.svc.util.CollectHealthPolicy;
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
 * 브레이커 상태 저장 검증 (Phase 3-3).
 *
 * <p>핵심은 <b>재시작을 넘어 살아남는가</b>다. 트립 상태가 메모리에만 있으면 재기동이 차단된 수집기를 되살려 다시 사이트를 두드린다.
 *
 * <p>하트비트 쪽은 반대 방향을 본다 — <b>없는 행이 경보를 만들지 못하는</b> 사각지대다. 옵트인만 해 두고 한 회차도 못 돈 몰은 브레이커 행 자체가 없어 건강도
 * 목록에서 통째로 빠지고, 그래서 "이상 없음"으로 보인다. 아래 세 건이 그 자리를 지킨다.
 *
 * <p>테스트마다 {@code @TempDir} 의 새 SQLite 파일을 쓴다 — 기준선 DB 에 닿지 않는다.
 *
 * @author KIUNSEA
 */
class CollectBreakerStateTest {

  private static final String DB_URL_PROPERTY = "jangbogo.localdb.url";
  private static final int SEQ_SSG_MALL = 1;

  private String previousUrl;
  private String dbUrl;
  private JbgCollectBreakerDataAccessObject dao;

  @BeforeEach
  void isolateDatabase(@TempDir Path tempDir) {
    previousUrl = System.getProperty(DB_URL_PROPERTY);
    dbUrl = "jdbc:sqlite:" + tempDir.resolve("breaker-test.db").toString().replace('\\', '/');
    System.setProperty(DB_URL_PROPERTY, dbUrl);
    SchemaMigrator.resetForTest();
    dao = new JbgCollectBreakerDataAccessObject(); // 생성자가 스키마를 보장한다
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

  @Test
  @DisplayName("행이 없으면 정상 상태를 돌려준다")
  void unknownCollectorIsHealthy() {
    CollectBreakerPolicy.State state = dao.getState(SEQ_SSG_MALL, "SSG");

    assertEquals(0, state.consecutiveFailures);
    assertFalse(state.isTripped());
  }

  @Test
  @DisplayName("저장한 상태를 그대로 읽는다")
  void roundTripsState() {
    CollectBreakerPolicy.State saved = new CollectBreakerPolicy.State(2, 1000L, 2000L, 0L);
    dao.saveState(SEQ_SSG_MALL, "SSG", saved, 0L, 0L, "테스트");

    CollectBreakerPolicy.State loaded = dao.getState(SEQ_SSG_MALL, "SSG");

    assertEquals(2, loaded.consecutiveFailures);
    assertEquals(1000L, loaded.streakStartedTime);
    assertEquals(2000L, loaded.lastFailureTime);
    assertFalse(loaded.isTripped());
  }

  @Test
  @DisplayName("같은 수집기를 두 번 저장하면 행이 늘지 않고 덮어쓴다")
  void upsertsInsteadOfDuplicating() throws Exception {
    dao.saveState(
        SEQ_SSG_MALL, "SSG", new CollectBreakerPolicy.State(1, 10L, 10L, 0L), 0L, 0L, "1회");
    dao.saveState(
        SEQ_SSG_MALL, "SSG", new CollectBreakerPolicy.State(2, 10L, 20L, 0L), 0L, 0L, "2회");

    assertEquals(1, countRows(), "복합 PK 가 동작하지 않아 행이 중복됐다.");
    assertEquals(2, dao.getState(SEQ_SSG_MALL, "SSG").consecutiveFailures);
  }

  @Test
  @DisplayName("같은 몰의 다른 수집기는 별개로 기록된다")
  void tracksCollectorsSeparately() {
    // seq=1 은 SSG·Emart 둘이다. 한쪽이 죽어도 다른 쪽은 영향을 받지 않아야 한다.
    dao.saveState(
        SEQ_SSG_MALL, "SSG", new CollectBreakerPolicy.State(3, 10L, 30L, 30L), 0L, 0L, "차단");
    dao.saveState(SEQ_SSG_MALL, "Emart", CollectBreakerPolicy.onSuccess(), 40L, 40L, "성공");

    assertTrue(dao.getState(SEQ_SSG_MALL, "SSG").isTripped());
    assertFalse(dao.getState(SEQ_SSG_MALL, "Emart").isTripped());
  }

  @Test
  @DisplayName("트립 상태가 재시작을 넘어 남는다")
  void trippedStateSurvivesRestart() {
    dao.saveState(
        SEQ_SSG_MALL, "SSG", new CollectBreakerPolicy.State(3, 10L, 30L, 30L), 0L, 0L, "차단");

    // 새 DAO = 새 커넥션. 메모리 상태가 아니라 DB 에서 읽어야 한다.
    CollectBreakerPolicy.State reloaded =
        new JbgCollectBreakerDataAccessObject().getState(SEQ_SSG_MALL, "SSG");

    assertTrue(reloaded.isTripped(), "재기동이 차단된 수집기를 되살리면 자동 차단의 의미가 사라진다.");
    assertEquals(30L, reloaded.trippedTime);
  }

  @Test
  @DisplayName("성공 시각 0 은 기존 값을 지우지 않는다")
  void zeroSuccessTimeKeepsPreviousValue() throws Exception {
    dao.saveState(SEQ_SSG_MALL, "SSG", CollectBreakerPolicy.onSuccess(), 5000L, 5000L, "성공");
    dao.saveState(
        SEQ_SSG_MALL, "SSG", new CollectBreakerPolicy.State(1, 6000L, 6000L, 0L), 0L, 0L, "실패");

    assertEquals(5000L, longValue("SELECT last_success_time FROM jbg_collect_breaker"));
  }

  @Test
  @DisplayName("트립된 수집기 목록을 경보용으로 뽑는다")
  void listsTrippedCollectors() {
    dao.saveState(
        SEQ_SSG_MALL, "SSG", new CollectBreakerPolicy.State(3, 10L, 30L, 30L), 0L, 0L, "로그인 실패");
    dao.saveState(SEQ_SSG_MALL, "Emart", CollectBreakerPolicy.onSuccess(), 40L, 40L, "성공");
    dao.saveState(2, "Oasis", new CollectBreakerPolicy.State(3, 10L, 50L, 50L), 0L, 0L, "타임아웃");

    List<JSONObject> tripped = dao.getTripped();

    assertEquals(2, tripped.size(), "트립된 것만 나와야 한다: " + tripped);
    assertEquals("Oasis", tripped.get(0).get("collector"), "최근 트립이 먼저 와야 한다.");
  }

  @Test
  @DisplayName("0건 수집은 last_nonempty_time 을 밀지 않는다")
  void emptyCollectionDoesNotAdvanceNonEmptyTime() throws Exception {
    // 조용한 실패를 알아챌 신호가 바로 이 시각이다. 0건이 이걸 밀어 버리면 영영 못 알아챈다.
    dao.saveState(SEQ_SSG_MALL, "SSG", CollectBreakerPolicy.onSuccess(), 1000L, 1000L, "성공");
    dao.saveState(SEQ_SSG_MALL, "SSG", CollectBreakerPolicy.onSuccess(), 9000L, 0L, "수집 0건");

    assertEquals(
        9000L, longValue("SELECT last_success_time FROM jbg_collect_breaker"), "성공 시각은 갱신돼야 한다.");
    assertEquals(
        1000L,
        longValue("SELECT last_nonempty_time FROM jbg_collect_breaker"),
        "0건 수집이 마지막 데이터 시각을 밀어 버렸다.");
  }

  @Test
  @DisplayName("하트비트에 몰 이름과 주기가 함께 붙어 나온다")
  void heartbeatCarriesMallContext() throws Exception {
    // 건강도 판정이 "마지막 성공 이후 주기의 N배"라 주기 없이는 판정할 수 없다.
    try (Connection c = DriverManager.getConnection(dbUrl);
        Statement s = c.createStatement()) {
      s.executeUpdate(
          "INSERT INTO jbg_mall (seq, id, name, collect_interval_minutes)"
              + " VALUES (1, 'ssg', 'SSG(신세계,이마트,트레이더스)', 720)");
    }
    dao.saveState(SEQ_SSG_MALL, "SSG", CollectBreakerPolicy.onSuccess(), 1000L, 1000L, "성공");

    List<JSONObject> beats = dao.getHeartbeats();

    assertEquals(1, beats.size());
    assertEquals("SSG(신세계,이마트,트레이더스)", beats.get(0).get("mall_name"));
    assertEquals(720, ((Number) beats.get(0).get("collect_interval_minutes")).intValue());
    assertEquals(1000L, ((Number) beats.get(0).get("last_nonempty_time")).longValue());
  }

  @Test
  @DisplayName("몰 행이 없어도 하트비트는 나온다")
  void heartbeatSurvivesMissingMallRow() {
    dao.saveState(9, "Unknown", CollectBreakerPolicy.onSuccess(), 1000L, 1000L, "성공");

    List<JSONObject> beats = dao.getHeartbeats();

    assertEquals(1, beats.size(), "LEFT JOIN 이 아니라 INNER JOIN 이면 행이 사라진다.");
    assertEquals(0, ((Number) beats.get(0).get("collect_interval_minutes")).intValue());
  }

  @Test
  @DisplayName("한 번도 안 돈 옵트인 몰이 하트비트에 NEVER_RAN 자리로 나온다")
  void optedInMallWithoutBreakerRowSurfacesAsNeverRan() throws Exception {
    // 브레이커 표는 실제로 돈 회차에만 갱신된다. 그래서 자동수집을 켜 놓고 한 회차도 못 돈 몰은
    // 행 자체가 없고, 행이 없으면 건강도 목록에 안 나오고, 안 나오면 "이상 없음"으로 보인다.
    // 가장 나쁜 고장이 가장 조용했던 자리가 여기다 — 두 달을 무증상으로 지나간 장애의 형태.
    insertMall(3, "oasis", "Oasis", 1, 720);

    List<JSONObject> beats = dao.getHeartbeats();

    assertEquals(1, beats.size(), "브레이커 행이 없는 옵트인 몰이 하트비트에서 통째로 빠졌다: " + beats);
    JSONObject beat = beats.get(0);
    assertEquals(3, ((Number) beat.get("seq_mall")).intValue());
    assertEquals(720, ((Number) beat.get("collect_interval_minutes")).intValue());
    assertEquals(0L, ((Number) beat.get("last_success_time")).longValue(), "성공한 적이 없으므로 0 이어야 한다.");

    // 판정 규칙은 손대지 않는다 — 시각이 0 이면 judge 가 이미 NEVER_RAN 을 돌려준다.
    assertEquals(
        CollectHealthPolicy.Health.NEVER_RAN,
        CollectHealthPolicy.judge(
                ((Number) beat.get("last_success_time")).longValue(),
                ((Number) beat.get("last_nonempty_time")).longValue(),
                ((Number) beat.get("tripped_time")).longValue() > 0,
                ((Number) beat.get("collect_interval_minutes")).intValue(),
                System.currentTimeMillis())
            .health);
  }

  @Test
  @DisplayName("돌 예정이 없는 몰은 하트비트에 끼어들지 않는다")
  void mallsThatWereNeverScheduledStayOutOfHeartbeats() throws Exception {
    // 대상 조건이 StartupTasks 의 스케줄 조건보다 넓으면, 애초에 돌 예정이 없던 몰까지 경보로 뜬다.
    // 늘 떠 있는 경고는 아무도 읽지 않게 되고 정작 진짜 사고 때 눈에 안 띈다.
    insertMall(4, "coupang", "Coupang", 0, 720); // 자동수집을 끈 몰
    insertMall(5, "hanaro", "Hanaro", 1, 0); // 켜 뒀지만 주기가 없어 주기 실행 대상이 아니다

    List<JSONObject> beats = dao.getHeartbeats();

    assertTrue(beats.isEmpty(), "돌 예정이 없는 몰까지 경보 후보로 올라왔다: " + beats);
  }

  @Test
  @DisplayName("한 번이라도 돈 몰은 하트비트에 두 번 나오지 않는다")
  void mallWithBreakerRowIsNotDuplicatedByTheNeverRanLeg() throws Exception {
    insertMall(SEQ_SSG_MALL, "ssg", "SSG", 1, 720);
    dao.saveState(SEQ_SSG_MALL, "SSG", CollectBreakerPolicy.onSuccess(), 1000L, 1000L, "성공");

    List<JSONObject> beats = dao.getHeartbeats();

    assertEquals(1, beats.size(), "UNION ALL 두 다리가 겹쳐 같은 몰이 두 줄로 나왔다: " + beats);
    assertEquals("SSG", beats.get(0).get("collector"));
  }

  /** 하트비트 두 번째 다리의 대상 조건을 검증하려면 몰 행이 필요하다. */
  private void insertMall(int seq, String id, String name, int autoCollect, int intervalMinutes)
      throws Exception {
    try (Connection c = DriverManager.getConnection(dbUrl);
        var p =
            c.prepareStatement(
                "INSERT INTO jbg_mall (seq, id, name, auto_collect, collect_interval_minutes)"
                    + " VALUES (?, ?, ?, ?, ?)")) {
      p.setInt(1, seq);
      p.setString(2, id);
      p.setString(3, name);
      p.setInt(4, autoCollect);
      p.setInt(5, intervalMinutes);
      p.executeUpdate();
    }
  }

  private int countRows() throws Exception {
    return (int) longValue("SELECT COUNT(*) FROM jbg_collect_breaker");
  }

  private long longValue(String query) throws Exception {
    try (Connection c = DriverManager.getConnection(dbUrl);
        Statement s = c.createStatement();
        var r = s.executeQuery(query)) {
      return r.next() ? r.getLong(1) : -1;
    }
  }
}

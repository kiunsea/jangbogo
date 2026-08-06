package com.jiniebox.jangbogo.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 수집 로그 요약 집계식 고정.
 *
 * <p>이 테스트가 지키는 것은 <b>{@code total = success + fail}</b> 하나다. 초기 계획서는 {@code total = success + fail
 * + skipped} 로 적혀 있었고, 구현이 SKIPPED 를 분모에서까지 뺀 것은 누락이 아니라 결정이다. 근거는 {@link
 * JbgCollectLogDataAccessObject#getSummary()} 의 javadoc 에 있다.
 *
 * <p>그런데 그 결정을 지키는 테스트가 <b>한 건도 없었다.</b> 계획서만 읽고 "구현이 계획과 다르다"며 되돌리면 조용히 통과하고, 그 순간 <b>지금까지 쌓인
 * SKIPPED 행이 한꺼번에 전체 실행 수에 얹혀 기존 대시보드 숫자가 소급해서 달라진다.</b> 되돌리는 사람이 그 사실을 알고 되돌리게 하려고 못을 박는다.
 *
 * <p>테스트마다 {@code @TempDir} 의 새 SQLite 파일을 쓴다 — 기준선 DB 에 닿지 않는다.
 *
 * @author KIUNSEA
 */
class CollectLogSummaryTest {

  private static final String DB_URL_PROPERTY = "jangbogo.localdb.url";
  private static final int SEQ_MALL = 1;

  private String previousUrl;
  private JbgCollectLogDataAccessObject dao;

  @BeforeEach
  void isolateDatabase(@TempDir Path tempDir) {
    previousUrl = System.getProperty(DB_URL_PROPERTY);
    String dbUrl =
        "jdbc:sqlite:" + tempDir.resolve("collect-log-test.db").toString().replace('\\', '/');
    System.setProperty(DB_URL_PROPERTY, dbUrl);
    SchemaMigrator.resetForTest();
    dao = new JbgCollectLogDataAccessObject(); // 생성자가 스키마를 보장한다
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
  @DisplayName("요약은 SUCCESS 와 FAIL 만 세고 건너뜀은 전체에서도 뺀다")
  void skippedRunsAreExcludedFromTotalAsWellAsFromSuccessAndFail() {
    // 한 회차 = 수집기마다 한 행 + 실행 단위 집계 행 한 행.
    addCollectorRun("SSG", "SUCCESS", 1000L);
    addCollectorRun("Emart", "SKIPPED", 1000L);
    addRollupRun("SUCCESS", 1000L);

    JSONObject summary = dao.getSummary();

    assertEquals(1, intOf(summary, "success"));
    assertEquals(0, intOf(summary, "fail"));
    assertEquals(1, intOf(summary, "total"), "건너뜀이 전체 실행 수에 얹혔다. 보호 장치가 잘 막을수록 지표가 나빠 보인다.");
  }

  @Test
  @DisplayName("건너뜀을 아무리 쌓아도 요약 숫자 셋이 모두 그대로다")
  void pilingUpSkippedRunsMovesNoNumber() {
    addCollectorRun("SSG", "SUCCESS", 1000L);
    addCollectorRun("SSG", "FAIL", 2000L);

    JSONObject before = dao.getSummary();

    // 브레이커가 열린 뒤에는 이런 행만 계속 쌓인다. 이것이 지표를 흔들면 안 된다.
    for (long startedAt = 3000L; startedAt < 3010L; startedAt++) {
      addCollectorRun("SSG", "SKIPPED", startedAt);
    }

    JSONObject after = dao.getSummary();

    assertEquals(intOf(before, "total"), intOf(after, "total"), "건너뜀이 전체 실행 수를 밀었다.");
    assertEquals(intOf(before, "success"), intOf(after, "success"));
    assertEquals(intOf(before, "fail"), intOf(after, "fail"));
  }

  @Test
  @DisplayName("전체는 언제나 성공과 실패의 합이다")
  void totalAlwaysEqualsSuccessPlusFail() {
    // 이 등식이 요약 카드 세 칸의 계약이다. 세 숫자가 서로 안 맞으면 사람은 셋 다 안 믿게 된다.
    addCollectorRun("SSG", "SUCCESS", 1000L);
    addCollectorRun("Emart", "FAIL", 1000L);
    addCollectorRun("Oasis", "SKIPPED", 1000L);
    addRollupRun("FAIL", 1000L);
    addRollupRun("SUCCESS", 5000L); // 수집기 행이 없는 옛 형식 회차

    JSONObject summary = dao.getSummary();

    // 등식만 보면 조회가 통째로 실패해 0/0/0 이 와도 통과한다. 숫자가 실제로 세어졌는지 먼저 본다 —
    // getSummary 는 예외를 삼키고 0 을 돌려주므로 이 확인이 없으면 이 테스트가 아무 것도 안 지킨다.
    assertEquals(3, intOf(summary, "total"), "집계가 아예 돌지 않았거나 세는 행이 달라졌다: " + summary);
    assertEquals(
        intOf(summary, "success") + intOf(summary, "fail"),
        intOf(summary, "total"),
        "total = success + fail 이 깨졌다. 계획서의 total = success + fail + skipped 로 되돌린 것이라면"
            + " getSummary 의 javadoc 을 먼저 읽어라 — 기존 대시보드 숫자가 소급해서 달라진다.");
  }

  @Test
  @DisplayName("수집기 행이 있는 회차의 집계 행은 두 번 세지 않는다")
  void rollupRowIsNotCountedWhenCollectorRowsExist() {
    // 전부 세면 정상 회차 하나가 성공 3 으로 잡혀 숫자가 부풀고, 반대로 집계 행만 세면
    // "한쪽 수집기는 죽었는데 회차는 SUCCESS" 인 상태가 성공으로만 보인다.
    addCollectorRun("SSG", "SUCCESS", 1000L);
    addCollectorRun("Emart", "SUCCESS", 1000L);
    addRollupRun("SUCCESS", 1000L);

    assertEquals(2, intOf(dao.getSummary(), "total"), "실행 단위 집계 행이 수집기 행과 함께 세어졌다.");
  }

  /** 수집기 한 대의 결과 행. */
  private void addCollectorRun(String collector, String status, long startedAt) {
    dao.addLog(
        SEQ_MALL,
        "테스트몰",
        collector,
        status,
        0,
        0,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        startedAt,
        startedAt + 1);
  }

  /** 실행 단위 집계 행 ({@code collector} 가 null). */
  private void addRollupRun(String status, long startedAt) {
    addCollectorRun(null, status, startedAt);
  }

  private static int intOf(JSONObject summary, String key) {
    return ((Number) summary.get(key)).intValue();
  }
}

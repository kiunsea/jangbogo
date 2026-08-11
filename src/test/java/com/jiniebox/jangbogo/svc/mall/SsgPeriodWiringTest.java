package com.jiniebox.jangbogo.svc.mall;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jiniebox.jangbogo.dao.JbgCollectBreakerDataAccessObject;
import com.jiniebox.jangbogo.dao.JbgOrderDataAccessObject;
import com.jiniebox.jangbogo.dao.LocalDBConnection;
import com.jiniebox.jangbogo.dao.SchemaTestSupport;
import com.jiniebox.jangbogo.svc.util.CollectPeriod;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * SSG 가 <b>계산한 구간</b>으로 조회하는지 고정한다.
 *
 * <h2>무엇이 깨져 있었나</h2>
 *
 * <p>예전 코드는 프리셋 라벨 하나를 눌렀다 — {@code //label[@for='sf_m3']}, 실측 결과 <b>1개월</b>이다. 시작점이 언제나 "오늘 기준 1개월
 * 전" 이라 <b>되돌아가지 않는다.</b> 앱이 한 달 넘게 돌지 않으면 그 사이 구매는 다음 회차에도 조회 범위 밖이고 <b>영영 들어오지 않는다.</b>
 *
 * <p>{@link CollectPeriod} 가 하나로에서 정확히 이 실패를 막으려고 만들어졌는데 SSG 에는 적용되지 않았다. 2026-08-11 실계정 확인에서 SSG 가
 * 0건이었고, 그 0건이 "정말 안 샀다" 인지 "1개월 밖이라 안 보인다" 인지 가릴 수 없었던 것이 발단이다.
 *
 * <h2>실측 근거 (2026-08-11, {@code SsgPeriodProbe})</h2>
 *
 * <pre>
 * id=_d_sch_start_dt  name=schDateFr  type=text  값형태=NNNN-NN-NN  maxlength=10  readonly=false
 * id=_d_sch_end_dt    name=schDateTo  type=text  값형태=NNNN-NN-NN  maxlength=10  readonly=false
 * </pre>
 *
 * <p>날짜를 직접 넣는 칸이 있고 표기는 {@code yyyy-MM-dd} 다. 그래서 프리셋을 버렸다.
 */
class SsgPeriodWiringTest {

  private static final String DB_URL_PROPERTY = "jangbogo.localdb.url";
  private static final int SEQ_SSG = MallRegistry.SSG_GROUP.seq();
  private static final Path SOURCE =
      Path.of("src/main/java/com/jiniebox/jangbogo/svc/mall/Ssg.java");

  private String previousUrl;

  @BeforeEach
  void isolateDatabase(@TempDir Path tempDir) {
    previousUrl = System.getProperty(DB_URL_PROPERTY);
    System.setProperty(
        DB_URL_PROPERTY,
        "jdbc:sqlite:" + tempDir.resolve("ssg-period-test.db").toString().replace('\\', '/'));
    SchemaTestSupport.remigrate();
  }

  @AfterEach
  void restoreDatabase() {
    SchemaTestSupport.reset();
    if (previousUrl == null) {
      System.clearProperty(DB_URL_PROPERTY);
    } else {
      System.setProperty(DB_URL_PROPERTY, previousUrl);
    }
  }

  // ── 구간을 정하는 쪽 ──────────────────────────────────────────────────

  @Test
  @DisplayName("저장된 것이 없으면 기본 범위 — 1개월이 아니다")
  void withNothingStoredItLooksBackFurtherThanOneMonth() {
    // 이 한 건이 결함의 핵심이다. 예전에는 저장분이 있든 없든 언제나 1개월이었다.
    CollectPeriod.Window window = ssg().resolveWindow();

    assertTrue(
        window.start().isBefore(LocalDate.now().minusMonths(2)),
        "조회 시작일이 아직 최근 몇 주에 묶여 있다: " + window.startYmd());
    assertEquals(CollectPeriod.resolve(null, LocalDate.now()).startYmd(), window.startYmd());
  }

  @Test
  @DisplayName("저장분이 있으면 그 마지막 구매일부터 조회한다")
  void itResumesFromTheLastStoredPurchase() throws Exception {
    commitOrder("20260701", Ssg.COLLECTOR);

    assertEquals("20260701", ssg().resolveWindow().startYmd());
  }

  @Test
  @DisplayName("다른 수집기가 저장한 주문은 SSG 의 시작일을 밀지 않는다")
  void anotherCollectorsOrdersDoNotMoveTheStart() throws Exception {
    // seq=1 은 수집기가 둘이다. 몰 단위로 최대값을 구하면 Emart 의 최신 영수증이 SSG 의 시작점을
    // 밀어 그 사이 온라인 구매를 영구히 놓친다.
    commitOrder("20260801", "Emart");

    assertEquals(
        CollectPeriod.resolve(null, LocalDate.now()).startYmd(),
        ssg().resolveWindow().startYmd(),
        "Emart 의 주문이 SSG 조회 시작일을 밀었다.");
  }

  @Test
  @DisplayName("부분 저장 실패가 남긴 바닥이 SSG 시작일도 되돌린다")
  void theRetryFloorPullsSsgBackToo() throws Exception {
    commitOrder("20260701", Ssg.COLLECTOR);
    new JbgCollectBreakerDataAccessObject().saveRetryFrom(SEQ_SSG, Ssg.COLLECTOR, "20260615");

    assertEquals("20260615", ssg().resolveWindow().startYmd());
  }

  // ── 화면에 넣는 쪽 ────────────────────────────────────────────────────

  @Test
  @DisplayName("날짜 표기는 실측한 yyyy-MM-dd 다")
  void theDateFormatMatchesWhatTheScreenAsksFor() {
    // 형식이 틀리면 이 사이트는 예외가 아니라 빈 목록을 준다 — 틀린 표기는 '정상인 0건' 으로 굳는다.
    assertEquals("2026-06-01", LocalDate.of(2026, 6, 1).format(Ssg.SEARCH_DATE_FORMAT));
    assertEquals(10, LocalDate.of(2026, 6, 1).format(Ssg.SEARCH_DATE_FORMAT).length());
  }

  @Test
  @DisplayName("실측한 셀렉터를 그대로 쓴다")
  void itUsesTheMeasuredSelectors() {
    assertEquals("By.id: _d_sch_start_dt", Ssg.SEARCH_START_DATE.toString());
    assertEquals("By.id: _d_sch_end_dt", Ssg.SEARCH_END_DATE.toString());
    assertEquals("By.id: _d_sch_button", Ssg.SEARCH_BUTTON.toString());
  }

  // ── 배선 가드 ────────────────────────────────────────────────────────

  @Test
  @DisplayName("1개월 프리셋을 더 이상 누르지 않는다")
  void theOneMonthPresetIsGone() throws IOException {
    // 날짜를 넣고도 프리셋을 함께 누르면 어느 쪽이 이기는지 사이트에 달렸고, 지는 날
    // 조용히 1개월로 되돌아간다 — 고친 것이 무효가 되는데 아무것도 실패하지 않는다.
    String source = sourceWithoutComments();

    assertFalse(source.contains("sf_m3"), "1개월 프리셋을 아직 누른다 — 계산한 구간이 무시될 수 있다.");
  }

  @Test
  @DisplayName("조회 전에 계산한 구간을 실제로 넣는다")
  void itActuallyFillsTheComputedWindow() throws IOException {
    // 구간을 계산해 놓고 화면에 넣지 않으면 테스트는 전부 초록인데 조회는 예전 그대로다.
    String source = sourceWithoutComments();

    assertTrue(source.contains("resolveWindow()"), "조회 구간을 계산하지 않는다.");
    assertTrue(source.contains("SEARCH_START_DATE"), "시작일 칸에 넣지 않는다.");
    assertTrue(source.contains("SEARCH_END_DATE"), "종료일 칸에 넣지 않는다.");
    assertTrue(source.contains("getLastCollectedDate("), "마지막 수집일을 조회하지 않는다.");
    assertTrue(source.contains("getRetryFrom("), "재조회 바닥을 읽지 않는다.");
  }

  @Test
  @DisplayName("변경 이벤트를 전역 Event 생성자로 만들지 않는다")
  void itDoesNotDependOnTheGlobalEventConstructor() throws IOException {
    // 2026-08-12 실계정 실행이 여기서 죽었다:
    //   JavascriptException: javascript error: Event is not a constructor
    //
    // 브라우저가 낡아서가 아니라 이 페이지가 전역 Event 를 자기 것으로 덮어썼기 때문이다.
    // 문법은 옳은데 이 사이트에서만 죽는 형태라, 단위테스트로는 원리상 드러나지 않는다.
    // 그래서 '무엇을 쓰지 않는가' 를 소스에 고정한다.
    String source = sourceWithoutComments();

    assertFalse(source.contains("new Event("), "전역 Event 생성자에 기댄다 — 이 페이지에서 덮여 있어 실행 중 죽는다.");
    assertTrue(source.contains("document.createEvent("), "전역에 기대지 않는 이벤트 생성 경로가 없다.");
  }

  @Test
  @DisplayName("조회 시작일 유도에 자기 수집기 이름을 넘긴다")
  void itDerivesTheWatermarkWithItsOwnName() throws IOException {
    // contains("COLLECTOR") 만 보면 상수 선언만으로 만족된다. 호출에 실제로 넘기는지를 본다.
    String source = sourceWithoutComments();
    int call = source.indexOf("getLastCollectedDate(");
    String callSite = source.substring(call, Math.min(source.length(), call + 200));

    assertTrue(
        callSite.contains("COLLECTOR"),
        "조회 호출이 자기 수집기 이름을 넘기지 않는다 — 몰 단위로 구하면 Emart 에 시작점이 밀린다: " + callSite);
  }

  @Test
  @DisplayName("레지스트리가 수집기 이름을 다시 적지 않는다")
  void theRegistryReusesTheConstant() {
    // 두 곳에 따로 적으면 한쪽만 고쳐져도 컴파일은 통과하고, 그때 기준일이 늘 비어
    // 매 회차 기본 범위를 통째로 훑는다 — 조용히.
    assertEquals(Ssg.COLLECTOR, MallRegistry.SSG_GROUP.collectors().get(0).name());
  }

  @Test
  @DisplayName("대조군 — 소스 스캔이 실제로 읽는다")
  void theSourceScanIsNotAlwaysGreen() throws IOException {
    String source = sourceWithoutComments();

    assertFalse(source.isBlank(), "소스를 빈 문자열로 읽었다 — 위 검사들이 전부 무의미하다.");
    assertFalse(source.contains("이_문자열은_소스에_없다"), "존재하지 않는 문자열이 발견됐다 — 판별식이 죽었다.");
  }

  /** 주석을 걷어낸 소스. javadoc 이 적어 둔 것과 실제로 하는 것은 다른 사실이다. */
  private static String sourceWithoutComments() throws IOException {
    assertTrue(Files.exists(SOURCE), "소스를 찾지 못했다(경로가 바뀌었나): " + SOURCE.toAbsolutePath());
    return Files.readString(SOURCE, StandardCharsets.UTF_8)
        .replaceAll("(?s)/\\*.*?\\*/", "")
        .replaceAll("(?m)^\\s*//.*$", "");
  }

  private static Ssg ssg() {
    return new Ssg("test-id", "test-pass");
  }

  /** 수집 회차가 주문 하나를 커밋했을 때와 같은 상태를 만든다. */
  private void commitOrder(String ymd, String collector) throws Exception {
    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();
      conn.txOpen();
      new JbgOrderDataAccessObject()
          .addWithConnection(conn, ymd + "_1", ymd, null, String.valueOf(SEQ_SSG), collector);
      conn.txCommit();
    } finally {
      if (conn != null) {
        conn.close();
      }
    }
  }
}

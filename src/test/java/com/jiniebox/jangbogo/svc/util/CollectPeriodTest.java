package com.jiniebox.jangbogo.svc.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 기간 조회 정책을 고정한다 (하나로 온라인/오프라인 분리).
 *
 * <p>여기 쓰인 날짜는 전부 <b>합성</b>이다. 실제 구매일을 테스트에 넣지 않는다.
 *
 * <p>순수 함수라 브라우저·네트워크·DB 를 쓰지 않는다.
 *
 * @author KIUNSEA
 */
class CollectPeriodTest {

  /** 합성 기준일. 윤년·월말 경계를 함께 밟도록 골랐다. */
  private static final LocalDate TODAY = LocalDate.of(2099, 3, 1);

  @Test
  @DisplayName("저장된 것이 없으면 기본 햇수만큼 거슬러 올라간다")
  void fallsBackToTheDefaultLookback() {
    CollectPeriod.Window window = CollectPeriod.resolve(null, TODAY);

    assertEquals("20970301", window.startYmd());
    assertEquals("20990301", window.endYmd());
  }

  @Test
  @DisplayName("빈 값도 '저장된 것이 없음' 으로 본다")
  void treatsBlankAsAbsent() {
    assertEquals("20970301", CollectPeriod.resolve("", TODAY).startYmd());
    assertEquals("20970301", CollectPeriod.resolve("   ", TODAY).startYmd());
  }

  @Test
  @DisplayName("마지막 저장일을 포함해서 시작한다 — 같은 날 나중 구매를 놓치지 않는다")
  void startsOnTheLastStoredDayInclusive() {
    CollectPeriod.Window window = CollectPeriod.resolve("20990215", TODAY);

    assertEquals("20990215", window.startYmd(), "하루 뒤부터 시작하면 그날 나중에 산 것을 놓친다.");
    assertEquals("20990301", window.endYmd());
  }

  @Test
  @DisplayName("읽을 수 없는 값은 기본 범위로 되돌아간다 — 건너뛰지 않는다")
  void widensRatherThanSkippingOnGarbage() {
    // 겹쳐 가져오는 것은 중복 판정이 걸러 내지만, 건너뛴 구간은 되돌아오지 않는다.
    for (String bad : List.of("2099-02-15", "abc", "209902", "0", "99999999")) {
      assertEquals("20970301", CollectPeriod.resolve(bad, TODAY).startYmd(), "입력: " + bad);
    }
  }

  @Test
  @DisplayName("미래 날짜가 저장돼 있으면 오늘로 당긴다 — 구간이 뒤집히면 안 된다")
  void clampsFutureDatesToToday() {
    CollectPeriod.Window window = CollectPeriod.resolve("20991231", TODAY);

    assertEquals("20990301", window.startYmd());
    assertEquals("20990301", window.endYmd());
    assertFalse(window.start().isAfter(window.end()), "시작일이 종료일보다 뒤다.");
  }

  @Test
  @DisplayName("같은 날이면 하루짜리 구간이다")
  void countsASingleDayAsOne() {
    assertEquals(1, CollectPeriod.resolve("20990301", TODAY).days());
  }

  @Test
  @DisplayName("기본 햇수는 2년이다 — 사용자 결정")
  void defaultLookbackIsTwoYears() {
    assertEquals(2, CollectPeriod.DEFAULT_LOOKBACK_YEARS);
  }

  // ── 구간 쪼개기 ─────────────────────────────────────────────────────────

  @Test
  @DisplayName("상한이 없으면 쪼개지 않는다 — 실측 전에는 추측한 상한을 넣지 않는다")
  void doesNotSplitWithoutAMeasuredLimit() {
    CollectPeriod.Window whole = CollectPeriod.resolve(null, TODAY);

    assertEquals(1, CollectPeriod.split(whole, 0).size());
    assertEquals(1, CollectPeriod.split(whole, -1).size());
  }

  @Test
  @DisplayName("상한보다 짧으면 그대로 한 구간이다")
  void keepsShortWindowsWhole() {
    CollectPeriod.Window window = CollectPeriod.resolve("20990215", TODAY);

    assertEquals(List.of(window), CollectPeriod.split(window, 90));
  }

  @Test
  @DisplayName("상한을 넘으면 앞에서부터 잘라 나가고 빈틈이 없다")
  void splitsWithoutGapsOrOverlaps() {
    CollectPeriod.Window whole =
        new CollectPeriod.Window(LocalDate.of(2099, 1, 1), LocalDate.of(2099, 1, 10));

    List<CollectPeriod.Window> parts = CollectPeriod.split(whole, 4);

    assertEquals(3, parts.size());
    assertEquals("20990101", parts.get(0).startYmd());
    assertEquals("20990104", parts.get(0).endYmd());
    assertEquals("20990105", parts.get(1).startYmd());
    assertEquals("20990108", parts.get(1).endYmd());
    assertEquals("20990109", parts.get(2).startYmd());
    assertEquals("20990110", parts.get(2).endYmd());

    // 빈틈 없음: 앞 구간의 다음 날이 뒤 구간의 시작이어야 한다.
    for (int i = 1; i < parts.size(); i++) {
      assertEquals(
          parts.get(i - 1).end().plusDays(1),
          parts.get(i).start(),
          "구간 사이에 빈틈이나 겹침이 생겼다 — 빈틈은 그대로 수집 누락이다.");
    }
  }

  @Test
  @DisplayName("쪼갠 구간을 다 합치면 원래 구간과 같다")
  void splitCoversTheWholeWindow() {
    CollectPeriod.Window whole = CollectPeriod.resolve(null, TODAY);

    List<CollectPeriod.Window> parts = CollectPeriod.split(whole, 90);

    assertEquals(whole.start(), parts.get(0).start());
    assertEquals(whole.end(), parts.get(parts.size() - 1).end());
    long total = parts.stream().mapToLong(CollectPeriod.Window::days).sum();
    assertEquals(whole.days(), total, "쪼갠 일수의 합이 원래 구간과 다르다.");
  }

  @Test
  @DisplayName("상한이 1일이어도 무한루프가 되지 않는다")
  void survivesADailyLimit() {
    CollectPeriod.Window whole =
        new CollectPeriod.Window(LocalDate.of(2099, 1, 1), LocalDate.of(2099, 1, 5));

    List<CollectPeriod.Window> parts = CollectPeriod.split(whole, 1);

    assertEquals(5, parts.size());
    assertTrue(parts.stream().allMatch(w -> w.days() == 1), "하루짜리가 아닌 구간이 섞였다.");
  }
}

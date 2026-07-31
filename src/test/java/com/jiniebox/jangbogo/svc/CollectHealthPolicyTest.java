package com.jiniebox.jangbogo.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jiniebox.jangbogo.svc.util.CollectHealthPolicy;
import com.jiniebox.jangbogo.svc.util.CollectHealthPolicy.Health;
import com.jiniebox.jangbogo.svc.util.CollectHealthPolicy.Verdict;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 수집 건강도 판정 검증 (Phase 3-4 · 3-5).
 *
 * <p>정책이 순수 함수라 시각을 인자로 받는다 — 실제로 기다리지 않고 일주일 뒤를 검증할 수 있다.
 *
 * @author KIUNSEA
 */
class CollectHealthPolicyTest {

  private static final long NOW = 1_800_000_000_000L;
  private static final int INTERVAL = 720; // 운영 주기 (분)

  private static long minutesAgo(int minutes) {
    return NOW - TimeUnit.MINUTES.toMillis(minutes);
  }

  private static Verdict judge(long lastSuccess, long lastNonEmpty, boolean tripped) {
    return CollectHealthPolicy.judge(lastSuccess, lastNonEmpty, tripped, INTERVAL, NOW);
  }

  @Test
  @DisplayName("최근에 성공했고 데이터도 받았으면 정상")
  void healthyWhenRecent() {
    Verdict v = judge(minutesAgo(60), minutesAgo(60), false);

    assertEquals(Health.OK, v.health);
    assertFalse(v.needsAttention());
  }

  @Test
  @DisplayName("한 번도 성공한 적이 없으면 NEVER_RAN")
  void neverRanWhenNoSuccess() {
    assertEquals(Health.NEVER_RAN, judge(0, 0, false).health);
  }

  @Test
  @DisplayName("트립이 다른 무엇보다 우선한다")
  void trippedTakesPriority() {
    // 트립된 수집기는 당연히 STALE 해진다. 둘을 같이 보고하면 같은 사실이 두 번 세어진다.
    Verdict v = judge(minutesAgo(INTERVAL * 100), 0, true);

    assertEquals(Health.TRIPPED, v.health);
    assertTrue(v.needsAttention());
  }

  @Test
  @DisplayName("한 회차를 거른 정도로는 경보하지 않는다")
  void oneMissedCycleIsNotStale() {
    // 주기의 2배까지는 봐준다 — 매 회차 경보가 뜨면 아무도 안 본다.
    assertEquals(
        Health.OK, judge(minutesAgo(INTERVAL + 10), minutesAgo(INTERVAL + 10), false).health);
  }

  @Test
  @DisplayName("마지막 성공이 주기의 2배를 넘으면 STALE")
  void staleWhenSuccessTooOld() {
    Verdict v =
        judge(
            minutesAgo(INTERVAL * CollectHealthPolicy.staleMultiplier() + 10),
            minutesAgo(60),
            false);

    assertEquals(Health.STALE, v.health);
    assertTrue(v.message.contains("시간 경과"), v.message);
  }

  @Test
  @DisplayName("수집은 되는데 오래 0건이면 NO_DATA")
  void noDataWhenDroughtPersists() {
    // 조용한 실패의 신호. 다만 정말 안 산 경우와 구분할 수 없으므로 알리기만 한다.
    Verdict v =
        judge(
            minutesAgo(30),
            minutesAgo(INTERVAL * CollectHealthPolicy.droughtMultiplier() + 10),
            false);

    assertEquals(Health.NO_DATA, v.health);
    assertTrue(v.message.contains("셀렉터"), v.message);
  }

  @Test
  @DisplayName("NO_DATA 는 경고일 뿐 자동 차단이 아니다")
  void noDataDoesNotImplyTripped() {
    // 장바구니를 두 달 안 쓰는 사용자를 장애로 처리하면 안 된다.
    Verdict v = judge(minutesAgo(30), minutesAgo(INTERVAL * 100), false);

    assertEquals(Health.NO_DATA, v.health);
    assertFalse(v.health == Health.TRIPPED);
  }

  @Test
  @DisplayName("STALE 이 NO_DATA 보다 우선한다")
  void staleBeatsNoData() {
    // 아예 안 도는 것이 더 큰 문제다.
    Verdict v = judge(minutesAgo(INTERVAL * 100), minutesAgo(INTERVAL * 100), false);

    assertEquals(Health.STALE, v.health);
  }

  @Test
  @DisplayName("데이터를 받은 적이 없으면 마지막 성공 시각을 기준으로 본다")
  void fallsBackToSuccessTimeWhenNeverGotData() {
    // 갓 연결한 계정은 last_nonempty_time 이 0 이다. 그것만으로 즉시 NO_DATA 를 띄우면 안 된다.
    assertEquals(Health.OK, judge(minutesAgo(30), 0, false).health);
  }

  @Test
  @DisplayName("주기를 모르면 정책 기본 하한으로 판정한다")
  void usesDefaultIntervalWhenUnknown() {
    Verdict v = CollectHealthPolicy.judge(minutesAgo(30), minutesAgo(30), false, 0, NOW);

    assertEquals(Health.OK, v.health);
  }
}

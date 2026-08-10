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
 * <p>아래쪽 "한 번도 데이터를 받은 적 없는 수집기" 묶음은 <b>실제로 관측된 값</b>으로 짰다. 그 자리가 이 프로젝트가 반복해서 싸워 온 '조용한 실패'의 마지막
 * 은신처였다 — 0건이 계속 나오는데 화면은 '정상'이라 사람이 알 방법이 없었다.
 *
 * @author KIUNSEA
 */
class CollectHealthPolicyTest {

  private static final long NOW = 1_800_000_000_000L;
  private static final int INTERVAL = 720; // 운영 주기 (분)

  /** 아주 오래 전에 처음 성공했다. 첫 성공 시각이 판정에 끼어들지 않아야 하는 케이스의 기본값. */
  private static final long LONG_AGO = NOW - TimeUnit.DAYS.toMillis(365);

  private static long minutesAgo(int minutes) {
    return NOW - TimeUnit.MINUTES.toMillis(minutes);
  }

  private static Verdict judge(
      long lastSuccess, long lastNonEmpty, long firstSuccess, boolean tripped) {
    return CollectHealthPolicy.judge(
        lastSuccess, lastNonEmpty, firstSuccess, tripped, INTERVAL, NOW);
  }

  @Test
  @DisplayName("최근에 성공했고 데이터도 받았으면 정상")
  void healthyWhenRecent() {
    Verdict v = judge(minutesAgo(60), minutesAgo(60), LONG_AGO, false);

    assertEquals(Health.OK, v.health);
    assertFalse(v.needsAttention());
  }

  @Test
  @DisplayName("한 번도 성공한 적이 없으면 NEVER_RAN")
  void neverRanWhenNoSuccess() {
    assertEquals(Health.NEVER_RAN, judge(0, 0, 0, false).health);
  }

  @Test
  @DisplayName("트립이 다른 무엇보다 우선한다")
  void trippedTakesPriority() {
    // 트립된 수집기는 당연히 STALE 해진다. 둘을 같이 보고하면 같은 사실이 두 번 세어진다.
    Verdict v = judge(minutesAgo(INTERVAL * 100), 0, LONG_AGO, true);

    assertEquals(Health.TRIPPED, v.health);
    assertTrue(v.needsAttention());
  }

  @Test
  @DisplayName("한 회차를 거른 정도로는 경보하지 않는다")
  void oneMissedCycleIsNotStale() {
    // 주기의 2배까지는 봐준다 — 매 회차 경보가 뜨면 아무도 안 본다.
    long recent = minutesAgo(INTERVAL + 10);

    assertEquals(Health.OK, judge(recent, recent, LONG_AGO, false).health);
  }

  @Test
  @DisplayName("마지막 성공이 주기의 2배를 넘으면 STALE")
  void staleWhenSuccessTooOld() {
    long tooOld = minutesAgo(INTERVAL * CollectHealthPolicy.staleMultiplier() + 10);

    Verdict v = judge(tooOld, minutesAgo(60), LONG_AGO, false);

    assertEquals(Health.STALE, v.health);
    assertTrue(v.message.contains("시간 경과"), v.message);
  }

  @Test
  @DisplayName("수집은 되는데 오래 0건이면 NO_DATA")
  void noDataWhenDroughtPersists() {
    // 조용한 실패의 신호. 다만 정말 안 산 경우와 구분할 수 없으므로 알리기만 한다.
    long lastData = minutesAgo(INTERVAL * CollectHealthPolicy.droughtMultiplier() + 10);

    Verdict v = judge(minutesAgo(30), lastData, LONG_AGO, false);

    assertEquals(Health.NO_DATA, v.health);
    assertTrue(v.message.contains("셀렉터"), v.message);
  }

  @Test
  @DisplayName("NO_DATA 는 경고일 뿐 자동 차단이 아니다")
  void noDataDoesNotImplyTripped() {
    // 장바구니를 두 달 안 쓰는 사용자를 장애로 처리하면 안 된다.
    Verdict v = judge(minutesAgo(30), minutesAgo(INTERVAL * 100), LONG_AGO, false);

    assertEquals(Health.NO_DATA, v.health);
    assertFalse(v.health == Health.TRIPPED);
  }

  @Test
  @DisplayName("STALE 이 NO_DATA 보다 우선한다")
  void staleBeatsNoData() {
    // 아예 안 도는 것이 더 큰 문제다.
    long veryOld = minutesAgo(INTERVAL * 100);

    Verdict v = judge(veryOld, veryOld, LONG_AGO, false);

    assertEquals(Health.STALE, v.health);
  }

  @Test
  @DisplayName("주기를 모르면 정책 기본 하한으로 판정한다")
  void usesDefaultIntervalWhenUnknown() {
    long recent = minutesAgo(30);

    Verdict v = CollectHealthPolicy.judge(recent, recent, LONG_AGO, false, 0, NOW);

    assertEquals(Health.OK, v.health);
  }

  @Test
  @DisplayName("데이터를 받은 적이 있으면 첫 성공 시각은 판정에 끼어들지 않는다")
  void firstSuccessDoesNotOverrideLastNonEmpty() {
    // 회귀 방지. 오래 전에 연결했지만 방금 데이터를 받은 수집기가 첫 성공 시각 때문에 NO_DATA 로
    // 뒤집히면, 멀쩡한 수집기가 매 조회마다 경보로 뜬다.
    Verdict v = judge(minutesAgo(30), minutesAgo(60), LONG_AGO, false);

    assertEquals(Health.OK, v.health, v.message);
  }

  // ===============================================================================================
  // 한 번도 데이터를 받은 적 없는 수집기 — 예전에는 여기가 통째로 사각지대였다.
  //
  // 실측 (2026-08-10). 두 수집기의 브레이커 행이 아래 모양이었다.
  //   last_success_time  = 회차마다 갱신됨 (0건 수집도 '성공'이므로)
  //   last_nonempty_time = 0             (단 한 건도 받은 적이 없음)
  // 그런데 대시보드 건강도는 둘 다 '정상'이었다. 폴백 기준이 last_success_time 이었고 그 값이 매
  // 회차 따라 올라와, "빈손인 기간"이 언제나 0 에 가깝게 계산됐기 때문이다.
  // 경보가 가장 필요한 수집기가 정확히 경보를 받지 못하는 구조였다.
  // ===============================================================================================

  /** 실측 — 데이터를 한 번도 받지 못한 수집기 A 의 마지막 성공(=0건 수집) 시각. */
  private static final long OBSERVED_LAST_SUCCESS_A = 1_786_342_828_971L;

  /** 실측 — 같은 상태였던 수집기 B 의 마지막 성공(=0건 수집) 시각. */
  private static final long OBSERVED_LAST_SUCCESS_B = 1_786_344_678_277L;

  /** 실측 — 두 행 모두 이 값이었다. 0 은 "한 번도 데이터를 받은 적 없음"을 뜻한다. */
  private static final long OBSERVED_LAST_NONEMPTY = 0L;

  /** 실측 직후 시각. 두 행 모두 STALE 창(주기의 2배) 안이라 '수집은 돌고 있다'가 맞다. */
  private static final long OBSERVED_NOW = 1_786_345_000_000L;

  private static Verdict judgeObserved(long lastSuccess, long firstSuccess) {
    return CollectHealthPolicy.judge(
        lastSuccess, OBSERVED_LAST_NONEMPTY, firstSuccess, false, INTERVAL, OBSERVED_NOW);
  }

  @Test
  @DisplayName("실측: 한 번도 데이터를 못 받은 채 드라우트 창을 넘긴 수집기는 NO_DATA")
  void observedRowsWithoutAnyDataRaiseNoData() {
    // 첫 성공이 드라우트 창(주기 720분 × 14 = 7일)보다 앞이다. 즉 일주일 넘게 빈손이었다.
    long firstSuccessA = OBSERVED_NOW - TimeUnit.DAYS.toMillis(8);
    long firstSuccessB = OBSERVED_NOW - TimeUnit.DAYS.toMillis(30);

    Verdict a = judgeObserved(OBSERVED_LAST_SUCCESS_A, firstSuccessA);
    Verdict b = judgeObserved(OBSERVED_LAST_SUCCESS_B, firstSuccessB);

    assertEquals(Health.NO_DATA, a.health, "고치기 전에는 여기가 OK 였다: " + a.message);
    assertEquals(Health.NO_DATA, b.health, "고치기 전에는 여기가 OK 였다: " + b.message);
    assertTrue(a.needsAttention());
    assertTrue(b.needsAttention());
  }

  @Test
  @DisplayName("한 번도 못 받았을 때는 '한 번도' 라고 말한다 — 무엇을 확인할지까지")
  void neverReceivedMessageIsDistinct() {
    // "N시간째 0건"과 "한 번도 받은 적 없음"은 사람이 볼 곳이 다르다. 후자는 셀렉터가 처음부터
    // 어긋났을 가능성을 가리킨다. 문구가 같으면 그 차이가 화면에서 사라진다.
    long firstSuccess = OBSERVED_NOW - TimeUnit.DAYS.toMillis(8);

    Verdict v = judgeObserved(OBSERVED_LAST_SUCCESS_A, firstSuccess);

    assertEquals(Health.NO_DATA, v.health);
    assertTrue(v.message.contains("한 번도"), v.message);
    assertTrue(v.message.contains("구매"), v.message);
    assertTrue(v.message.contains("셀렉터"), v.message);
  }

  @Test
  @DisplayName("갓 연결한 몰은 아직 빈손이어도 정상 — 드라우트 창은 그대로 적용한다")
  void freshlyConnectedCollectorStaysOkInsideDroughtWindow() {
    // 한 번도 못 받았다고 즉시 경보하면 방금 연결한 몰이 첫 회차부터 빨개진다.
    // "장바구니를 두 달 안 쓰는 사용자를 장애로 처리하지 않는다"는 설계 목표가 여기서도 유효하다.
    long firstSuccess = OBSERVED_NOW - TimeUnit.DAYS.toMillis(2);

    Verdict v = judgeObserved(OBSERVED_LAST_SUCCESS_A, firstSuccess);

    assertEquals(Health.OK, v.health, v.message);
    assertFalse(v.needsAttention());
  }

  @Test
  @DisplayName("첫 성공 시각이 없는 기존 행은 옛 동작으로 물러선다 — 다음 성공에 채워지며 해소된다")
  void legacyRowWithoutFirstSuccessFallsBackToLastSuccess() {
    // 컬럼이 없던 시절에 쌓인 행은 first_success_time 이 0 이다. 그때까지 경보를 지어내면
    // 업그레이드 직후 모든 수집기가 한꺼번에 빨개진다 — 늘 떠 있는 경고는 아무도 읽지 않는다.
    // 여기서 OK 가 나오는 것이 고치기 전의 사각지대 그 자체이기도 하다. 다만 오래가지 않는다:
    // 다음 성공 한 번이면 DAO 가 first_success_time 을 채우고 위 테스트들의 경로로 넘어간다.
    Verdict v = judgeObserved(OBSERVED_LAST_SUCCESS_A, 0L);

    assertEquals(Health.OK, v.health, v.message);
  }
}

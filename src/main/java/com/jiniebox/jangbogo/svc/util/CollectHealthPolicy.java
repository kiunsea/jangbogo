package com.jiniebox.jangbogo.svc.util;

import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 수집 건강도 판정 (Phase 3-4 · 3-5).
 *
 * <p>순수 판정 함수다 — 시각까지 인자로 받는다.
 *
 * <h2>왜 필요한가 — 0건은 두 가지를 뜻한다</h2>
 *
 * <p>수집기가 <b>예외 없이 0건</b>을 반환하는 경로가 실재한다. 예를 들어 SSG 는 구매 목록의 {@code tr} 을 {@code findElements} 로
 * 찾는데, 셀렉터가 어긋나면 예외가 아니라 <b>빈 목록</b>이 돌아오고 루프가 통째로 건너뛰어진다. 로그인은 성공했으므로 예외도 없다.
 *
 * <p>그 0건이 뜻하는 바는 둘 중 하나다.
 *
 * <ul>
 *   <li><b>정상</b> — 조회 기간에 산 것이 없다.
 *   <li><b>조용한 실패</b> — 셀렉터가 깨져 아무것도 못 읽었다.
 * </ul>
 *
 * <p><b>한 회차만 봐서는 구분할 수 없다.</b> 그래서 추측하지 않는다. 0건을 성공과 구분해 기록해 두고 ({@code EMPTY}), 그 상태가 <b>얼마나 오래
 * 이어지는지</b>로 판단한다. 이것이 3-4(조용한 실패 차단)와 3-5(하트비트)가 같은 문제의 두 얼굴인 이유다.
 *
 * <h2>두 가지 경보</h2>
 *
 * <ul>
 *   <li>{@code STALE} — 수집 자체가 안 돌고 있다. 마지막 성공이 주기의 {@value #DEFAULT_STALE_MULTIPLIER}배를 넘었다. 스케줄이
 *       죽었거나 앱이 내려가 있었다는 뜻이다.
 *   <li>{@code NO_DATA} — 수집은 도는데 계속 0건이다. 마지막으로 실제 데이터를 받은 지 주기의 {@value
 *       #DEFAULT_DROUGHT_MULTIPLIER}배가 넘었다. <b>조용한 실패의 신호</b>지만, 정말 안 산 경우와 구분할 수 없으므로 자동 차단하지 않고
 *       <b>사람에게 알리기만 한다.</b>
 * </ul>
 *
 * <p>{@code NO_DATA} 로 브레이커를 트립시키지 않는 것은 의도다. 장바구니를 두 달 안 쓰는 사용자를 장애로 처리하면 안 된다.
 *
 * @author KIUNSEA
 */
public final class CollectHealthPolicy {

  private static final Logger logger = LogManager.getLogger(CollectHealthPolicy.class);

  /** 마지막 성공이 주기의 이 배수를 넘으면 STALE. 한 회차를 걸렀다고 바로 경보하지 않기 위해 2를 쓴다. */
  public static final int DEFAULT_STALE_MULTIPLIER = 2;

  /**
   * 마지막 실제 데이터가 주기의 이 배수를 넘으면 NO_DATA.
   *
   * <p>720분 주기에서 14배 = 7일이다. 일주일 내내 한 건도 안 잡히는 것은 정상일 수 있지만 확인해 볼 만한 일이다.
   */
  public static final int DEFAULT_DROUGHT_MULTIPLIER = 14;

  public static final String STALE_MULTIPLIER_PROPERTY = "jangbogo.collect.health.stale-multiplier";
  public static final String DROUGHT_MULTIPLIER_PROPERTY =
      "jangbogo.collect.health.drought-multiplier";

  private CollectHealthPolicy() {}

  /** 건강 상태. */
  public enum Health {
    /** 정상. */
    OK,
    /** 브레이커가 열려 있다. */
    TRIPPED,
    /** 수집 자체가 예정대로 돌지 않고 있다. */
    STALE,
    /** 수집은 도는데 계속 0건이다. */
    NO_DATA,
    /** 아직 한 번도 성공한 적이 없다. */
    NEVER_RAN
  }

  /** 한 수집기의 판정 결과. */
  public static final class Verdict {
    public final Health health;
    public final String message;

    Verdict(Health health, String message) {
      this.health = health;
      this.message = message;
    }

    public boolean needsAttention() {
      return health != Health.OK;
    }
  }

  /**
   * 한 수집기의 건강 상태를 판정한다.
   *
   * <p>우선순위가 있다 — 트립이 가장 강한 신호이고, 그 다음이 "아예 안 돈다"(STALE), 마지막이 "돌긴 도는데 빈손"(NO_DATA)이다. 트립된 수집기는 당연히
   * STALE 해지므로 둘을 같이 보고하면 같은 사실이 두 번 세어진다.
   *
   * @param lastSuccessTime 마지막 성공 시각 (0건 수집 포함). {@code 0} 이면 한 번도 성공하지 않았다
   * @param lastNonEmptyTime 마지막으로 실제 데이터를 받은 시각. {@code 0} 이면 받은 적이 없다
   * @param tripped 브레이커가 열려 있는지
   * @param intervalMinutes 몰의 수집 주기 (분)
   * @param now 현재 시각 (epoch millis)
   * @return 판정 결과
   */
  public static Verdict judge(
      long lastSuccessTime, long lastNonEmptyTime, boolean tripped, int intervalMinutes, long now) {

    if (tripped) {
      return new Verdict(Health.TRIPPED, "연속 실패로 자동 차단됨");
    }
    if (lastSuccessTime <= 0) {
      return new Verdict(Health.NEVER_RAN, "아직 성공한 수집이 없음");
    }

    int interval =
        intervalMinutes > 0 ? intervalMinutes : CollectIntervalPolicy.DEFAULT_MIN_MINUTES;

    long staleAfter = TimeUnit.MINUTES.toMillis((long) interval * staleMultiplier());
    long sinceSuccess = now - lastSuccessTime;
    if (sinceSuccess > staleAfter) {
      return new Verdict(
          Health.STALE, "마지막 성공 이후 " + toHours(sinceSuccess) + "시간 경과 (주기 " + interval + "분)");
    }

    long droughtAfter = TimeUnit.MINUTES.toMillis((long) interval * droughtMultiplier());
    long sinceData = now - (lastNonEmptyTime > 0 ? lastNonEmptyTime : lastSuccessTime);
    if (sinceData > droughtAfter) {
      return new Verdict(
          Health.NO_DATA,
          "수집은 되지만 " + toHours(sinceData) + "시간째 0건 — 셀렉터 확인 필요 (실제로 구매가 없었을 수도 있음)");
    }

    return new Verdict(Health.OK, "정상");
  }

  private static long toHours(long millis) {
    return TimeUnit.MILLISECONDS.toHours(Math.max(0, millis));
  }

  public static int staleMultiplier() {
    return positiveProperty(STALE_MULTIPLIER_PROPERTY, DEFAULT_STALE_MULTIPLIER);
  }

  public static int droughtMultiplier() {
    return positiveProperty(DROUGHT_MULTIPLIER_PROPERTY, DEFAULT_DROUGHT_MULTIPLIER);
  }

  private static int positiveProperty(String key, int defaultValue) {
    String raw = System.getProperty(key);
    if (raw == null || raw.isBlank()) {
      return defaultValue;
    }
    try {
      int parsed = Integer.parseInt(raw.trim());
      return parsed > 0 ? parsed : defaultValue;
    } catch (NumberFormatException e) {
      logger.warn("{} 값을 해석할 수 없어 기본값 {} 을 사용합니다: {}", key, defaultValue, raw);
      return defaultValue;
    }
  }
}

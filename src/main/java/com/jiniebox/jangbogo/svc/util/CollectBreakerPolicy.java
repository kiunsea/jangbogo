package com.jiniebox.jangbogo.svc.util;

import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 수집기 서킷 브레이커 정책 (Phase 3-3 · 3-9).
 *
 * <p>순수 판정 함수다 — DB·브라우저·시각을 스스로 읽지 않고 전부 인자로 받는다. 상태 저장은 {@code
 * JbgCollectBreakerDataAccessObject}, 적용은 {@code MallOrderUpdater} 가 한다.
 *
 * <h2>왜 수집기 단위인가</h2>
 *
 * <p>seq=1 은 수집기가 둘이다(SSG·Emart). 몰 단위로 세면 한쪽이 계속 죽어도 다른 쪽이 성공하는 한 몰의 성공/실패는 SUCCESS 로 남아, <b>한쪽
 * 수집기 단독 장애를 영원히 잡지 못한다.</b> 그래서 카운트도 차단도 수집기 단위다.
 *
 * <h2>세 가지 판정</h2>
 *
 * <ul>
 *   <li>{@code RUN} — 돌린다.
 *   <li>{@code SKIP_BACKOFF} — 실패 직후의 유예. 다음 시도를 뒤로 미룬다 (Phase 3-9).
 *   <li>{@code SKIP_TRIPPED} — 브레이커가 열렸다. 냉각 시간이 지날 때까지 아예 시도하지 않는다 (Phase 3-3).
 * </ul>
 *
 * <p>둘 다 "그 회차를 건너뛴다"는 같은 수단으로 구현된다. 차이는 유예 길이와, 트립은 <b>상태로 남고 경보가 뜬다</b>는 것뿐이다.
 *
 * <h2>왜 스케줄을 취소하지 않는가</h2>
 *
 * <p>결정은 "해당 몰 스케줄 일시중단"이었다. 실제 구현은 <b>수집기 진입 지점에서 건너뛰는 것</b>으로 한다. 이유는 셋이다.
 *
 * <ul>
 *   <li>차단 단위가 수집기인데 스케줄은 몰 단위다. SSG 가 트립했다고 몰 스케줄을 내리면 <b>멀쩡한 Emart 까지 멈춘다.</b>
 *   <li>브라우저는 각 수집기의 {@code getItems()} 안에서 뜬다. 진입 전에 걸러내면 <b>로그인 시도도 브라우저 기동도 일어나지 않는다</b> — 사이트를
 *       그만 두드린다는 목적은 그대로 달성된다.
 *   <li>냉각 후 자동 복귀(half-open)가 그냥 된다. {@code ScheduledFuture} 를 취소해 버리면 되살릴 트리거를 따로 만들어야 하고, 그것을
 *       빠뜨리면 영구 정지가 된다.
 * </ul>
 *
 * <p>{@code auto_collect} 컬럼은 어느 경우에도 건드리지 않는다 — 코드가 사용자 설정을 덮으면 재개할 때 원래 값을 복원할 근거가 사라진다.
 *
 * @author KIUNSEA
 */
public final class CollectBreakerPolicy {

  private static final Logger logger = LogManager.getLogger(CollectBreakerPolicy.class);

  /**
   * 트립 임계 (연속 실패 횟수).
   *
   * <p>원안은 5회였다. 운영 주기 720분에서 5회는 <b>2.5일</b>이라 자동 강등이 너무 늦다. 3회로 낮춘다.
   */
  public static final int DEFAULT_TRIP_AFTER_FAILURES = 3;

  /**
   * 트립 임계 (연속 실패 지속 시간, 분).
   *
   * <p>횟수만으로는 주기가 길수록 트립이 늦어진다. 실패가 끊기지 않은 채 이 시간이 지나면 횟수와 무관하게 트립한다 — <b>주기와 무관하게 벽시계로 상한을 두는
   * 것</b>이 목적이다.
   */
  public static final int DEFAULT_TRIP_AFTER_MINUTES = 48 * 60;

  /**
   * 트립 후 냉각 시간 (분). 이 시간이 지나면 <b>한 번</b> 시도해 본다(half-open).
   *
   * <p>성공하면 상태가 초기화되고, 실패하면 다시 트립해 냉각이 새로 시작된다. 이 경로가 없으면 트립은 사람이 개입하기 전까지 영구 정지다.
   */
  public static final int DEFAULT_COOLDOWN_MINUTES = 24 * 60;

  /**
   * 백오프 상한 (분).
   *
   * <p>운영 주기 720분(12시간)보다 커야 의미가 있으므로 24시간으로 둔다.
   */
  public static final int DEFAULT_BACKOFF_CAP_MINUTES = 24 * 60;

  public static final String TRIP_FAILURES_PROPERTY = "jangbogo.collect.breaker.trip-failures";
  public static final String TRIP_MINUTES_PROPERTY = "jangbogo.collect.breaker.trip-minutes";
  public static final String COOLDOWN_PROPERTY = "jangbogo.collect.breaker.cooldown-minutes";
  public static final String BACKOFF_CAP_PROPERTY = "jangbogo.collect.breaker.backoff-cap-minutes";

  private CollectBreakerPolicy() {}

  /** 판정 결과 종류. */
  public enum Action {
    RUN,
    SKIP_BACKOFF,
    SKIP_TRIPPED
  }

  /** 한 수집기의 브레이커 상태. 모든 시각은 epoch millis 이며 {@code 0} 은 '없음'을 뜻한다. */
  public static final class State {
    public final int consecutiveFailures;
    public final long streakStartedTime;
    public final long lastFailureTime;
    public final long trippedTime;

    public State(
        int consecutiveFailures, long streakStartedTime, long lastFailureTime, long trippedTime) {
      this.consecutiveFailures = Math.max(0, consecutiveFailures);
      this.streakStartedTime = Math.max(0, streakStartedTime);
      this.lastFailureTime = Math.max(0, lastFailureTime);
      this.trippedTime = Math.max(0, trippedTime);
    }

    /** 실패 이력이 없는 초기 상태. */
    public static State healthy() {
      return new State(0, 0, 0, 0);
    }

    public boolean isTripped() {
      return trippedTime > 0;
    }
  }

  /** 판정 결과. */
  public static final class Decision {
    public final Action action;
    public final long notBefore;
    public final String reason;

    Decision(Action action, long notBefore, String reason) {
      this.action = action;
      this.notBefore = notBefore;
      this.reason = reason;
    }

    public boolean shouldRun() {
      return action == Action.RUN;
    }
  }

  /**
   * 이번 회차에 이 수집기를 돌릴지 판정한다.
   *
   * @param state 현재 브레이커 상태 (null 이면 초기 상태로 본다)
   * @param intervalMinutes 몰의 수집 주기 (분). 백오프 기준값
   * @param now 현재 시각 (epoch millis)
   * @return 판정 결과
   */
  public static Decision decide(State state, int intervalMinutes, long now) {
    State s = (state == null) ? State.healthy() : state;

    if (s.isTripped()) {
      long resumeAt = s.trippedTime + minutesToMillis(cooldownMinutes());
      if (now < resumeAt) {
        return new Decision(Action.SKIP_TRIPPED, resumeAt, "브레이커 열림 — 냉각 중");
      }
      // 냉각이 끝났다. 딱 한 번 시도해 본다.
      return new Decision(Action.RUN, now, "브레이커 냉각 종료 — 복귀 시도");
    }

    if (s.consecutiveFailures <= 0) {
      return new Decision(Action.RUN, now, "정상");
    }

    long resumeAt =
        s.lastFailureTime + minutesToMillis(backoffMinutes(s.consecutiveFailures, intervalMinutes));
    if (now < resumeAt) {
      return new Decision(
          Action.SKIP_BACKOFF, resumeAt, "연속 실패 " + s.consecutiveFailures + "회 — 백오프 중");
    }
    return new Decision(Action.RUN, now, "백오프 종료");
  }

  /**
   * 실패 후의 새 상태를 만든다. 임계를 넘으면 트립한다.
   *
   * @param state 실패 직전 상태 (null 허용)
   * @param now 실패 시각 (epoch millis)
   * @return 갱신된 상태
   */
  public static State onFailure(State state, long now) {
    State s = (state == null) ? State.healthy() : state;

    int failures = s.consecutiveFailures + 1;
    long streakStarted = (s.streakStartedTime > 0) ? s.streakStartedTime : now;

    boolean byCount = failures >= tripAfterFailures();
    boolean byDuration = (now - streakStarted) >= minutesToMillis(tripAfterMinutes());
    long tripped = (byCount || byDuration) ? now : 0;

    return new State(failures, streakStarted, now, tripped);
  }

  /**
   * 성공 후의 새 상태를 만든다. 연속 실패 카운트와 트립 상태를 모두 지운다.
   *
   * @return 항상 {@link State#healthy()}
   */
  public static State onSuccess() {
    return State.healthy();
  }

  /**
   * 연속 실패 {@code failures} 회일 때 적용할 유예 시간(분).
   *
   * <p>주기의 {@code 2^failures} 배로 늘리되 상한을 넘지 않는다. 첫 실패면 주기의 2배다.
   *
   * <p>운영 주기 720분에서는 상한(1440분)에 한 번에 닿으므로 사실상 "한 회차 건너뛰기"가 된다. 주기를 하한(360분)까지 낮춰 쓰는 경우에 360 → 720 →
   * 1440 으로 단계가 생긴다. <b>주기가 길수록 백오프의 여지가 작다</b>는 점은 설계상 그대로 받아들인다 — 이미 충분히 느리게 두드리고 있다는 뜻이기 때문이다.
   */
  public static int backoffMinutes(int failures, int intervalMinutes) {
    if (failures <= 0) {
      return 0;
    }
    int base = intervalMinutes > 0 ? intervalMinutes : CollectIntervalPolicy.DEFAULT_MIN_MINUTES;
    int cap = backoffCapMinutes();

    long delay = base;
    for (int i = 0; i < failures && delay < cap; i++) {
      delay *= 2;
    }
    return (int) Math.min(delay, cap);
  }

  private static long minutesToMillis(int minutes) {
    return TimeUnit.MINUTES.toMillis(Math.max(0, minutes));
  }

  public static int tripAfterFailures() {
    return positiveProperty(TRIP_FAILURES_PROPERTY, DEFAULT_TRIP_AFTER_FAILURES);
  }

  public static int tripAfterMinutes() {
    return positiveProperty(TRIP_MINUTES_PROPERTY, DEFAULT_TRIP_AFTER_MINUTES);
  }

  public static int cooldownMinutes() {
    return positiveProperty(COOLDOWN_PROPERTY, DEFAULT_COOLDOWN_MINUTES);
  }

  public static int backoffCapMinutes() {
    return positiveProperty(BACKOFF_CAP_PROPERTY, DEFAULT_BACKOFF_CAP_MINUTES);
  }

  /** 임계는 안전장치라 화면에 노출하지 않는다. 개발·시험용으로 시스템 프로퍼티만 연다. */
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

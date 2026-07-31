package com.jiniebox.jangbogo.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jiniebox.jangbogo.svc.util.CollectBreakerPolicy;
import com.jiniebox.jangbogo.svc.util.CollectBreakerPolicy.Action;
import com.jiniebox.jangbogo.svc.util.CollectBreakerPolicy.Decision;
import com.jiniebox.jangbogo.svc.util.CollectBreakerPolicy.State;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 수집기 서킷 브레이커 정책 검증 (Phase 3-3 · 3-9).
 *
 * <p>정책이 순수 함수라 시각까지 인자로 받는다 — 실제로 기다리지 않고 24시간 뒤를 검증할 수 있다. 브라우저·네트워크·DB 를 쓰지 않는다.
 *
 * @author KIUNSEA
 */
class CollectBreakerPolicyTest {

  private static final long T0 = 1_800_000_000_000L; // 고정 기준 시각
  private static final int INTERVAL = 720; // 운영 주기 (분)

  private static long plusMinutes(long base, int minutes) {
    return base + TimeUnit.MINUTES.toMillis(minutes);
  }

  @Nested
  @DisplayName("판정")
  class Decide {

    @Test
    @DisplayName("실패 이력이 없으면 그냥 돌린다")
    void runsWhenHealthy() {
      Decision d = CollectBreakerPolicy.decide(State.healthy(), INTERVAL, T0);

      assertEquals(Action.RUN, d.action);
      assertTrue(d.shouldRun());
    }

    @Test
    @DisplayName("상태가 null 이어도 돌린다")
    void runsWhenStateIsNull() {
      // 상태를 못 읽었다고 수집을 막지 않는다.
      assertTrue(CollectBreakerPolicy.decide(null, INTERVAL, T0).shouldRun());
    }

    @Test
    @DisplayName("실패 직후에는 백오프로 건너뛴다")
    void skipsDuringBackoff() {
      State afterOneFailure = CollectBreakerPolicy.onFailure(State.healthy(), T0);

      Decision d = CollectBreakerPolicy.decide(afterOneFailure, INTERVAL, plusMinutes(T0, 720));

      assertEquals(Action.SKIP_BACKOFF, d.action, "주기 720분에서 첫 실패의 유예는 1440분이다.");
      assertFalse(d.shouldRun());
    }

    @Test
    @DisplayName("백오프가 끝나면 다시 돌린다")
    void runsAfterBackoffExpires() {
      State afterOneFailure = CollectBreakerPolicy.onFailure(State.healthy(), T0);

      assertTrue(
          CollectBreakerPolicy.decide(afterOneFailure, INTERVAL, plusMinutes(T0, 1440))
              .shouldRun());
    }

    @Test
    @DisplayName("트립하면 냉각이 끝날 때까지 아예 시도하지 않는다")
    void skipsWhileTripped() {
      State tripped = trippedState();

      Decision d = CollectBreakerPolicy.decide(tripped, INTERVAL, plusMinutes(T0, 60));

      assertEquals(Action.SKIP_TRIPPED, d.action);
      assertFalse(d.shouldRun());
    }

    @Test
    @DisplayName("냉각이 끝나면 한 번 복귀를 시도한다 (half-open)")
    void retriesOnceAfterCooldown() {
      State tripped = trippedState();
      long resumeAt = plusMinutes(tripped.trippedTime, CollectBreakerPolicy.cooldownMinutes());

      assertFalse(CollectBreakerPolicy.decide(tripped, INTERVAL, resumeAt - 1).shouldRun());
      assertTrue(
          CollectBreakerPolicy.decide(tripped, INTERVAL, resumeAt).shouldRun(),
          "복귀 경로가 없으면 트립은 사람이 개입하기 전까지 영구 정지가 된다.");
    }
  }

  @Nested
  @DisplayName("상태 전이")
  class Transitions {

    @Test
    @DisplayName("연속 실패가 임계에 닿으면 트립한다")
    void tripsAtThreshold() {
      int threshold = CollectBreakerPolicy.tripAfterFailures();

      State state = State.healthy();
      for (int i = 1; i < threshold; i++) {
        state = CollectBreakerPolicy.onFailure(state, plusMinutes(T0, i * 30));
        assertFalse(state.isTripped(), i + "회째에 이미 트립했다.");
      }
      state = CollectBreakerPolicy.onFailure(state, plusMinutes(T0, threshold * 30));

      assertTrue(state.isTripped(), threshold + "회에 트립하지 않았다.");
      assertEquals(threshold, state.consecutiveFailures);
    }

    @Test
    @DisplayName("실패가 끊기지 않은 채 시간 상한을 넘으면 횟수와 무관하게 트립한다")
    void tripsByDurationRegardlessOfCount() {
      // 횟수만으로는 주기가 길수록 트립이 늦어진다. 벽시계 상한이 그것을 막는다.
      State first = CollectBreakerPolicy.onFailure(State.healthy(), T0);
      assertFalse(first.isTripped());

      State second =
          CollectBreakerPolicy.onFailure(
              first, plusMinutes(T0, CollectBreakerPolicy.tripAfterMinutes()));

      assertTrue(second.isTripped(), "연속 실패가 시간 상한을 넘었는데 트립하지 않았다.");
      assertTrue(second.consecutiveFailures < CollectBreakerPolicy.tripAfterFailures());
    }

    @Test
    @DisplayName("연속 실패의 시작 시각은 첫 실패로 고정된다")
    void streakStartStaysAtFirstFailure() {
      State first = CollectBreakerPolicy.onFailure(State.healthy(), T0);
      State second = CollectBreakerPolicy.onFailure(first, plusMinutes(T0, 100));

      assertEquals(T0, second.streakStartedTime);
      assertEquals(plusMinutes(T0, 100), second.lastFailureTime);
    }

    @Test
    @DisplayName("성공하면 카운트와 트립이 모두 지워진다")
    void successClearsEverything() {
      State state = CollectBreakerPolicy.onSuccess();

      assertEquals(0, state.consecutiveFailures);
      assertFalse(state.isTripped());
      assertEquals(0, state.streakStartedTime);
    }

    @Test
    @DisplayName("음수 입력은 0 으로 정규화된다")
    void normalizesNegativeInput() {
      State state = new State(-5, -1, -1, -1);

      assertEquals(0, state.consecutiveFailures);
      assertFalse(state.isTripped());
    }
  }

  @Nested
  @DisplayName("백오프 계산")
  class Backoff {

    @Test
    @DisplayName("실패가 없으면 유예도 없다")
    void noDelayWithoutFailure() {
      assertEquals(0, CollectBreakerPolicy.backoffMinutes(0, INTERVAL));
    }

    @Test
    @DisplayName("주기를 2배씩 늘리되 상한을 넘지 않는다")
    void doublesUpToTheCap() {
      int cap = CollectBreakerPolicy.backoffCapMinutes();

      assertEquals(720, CollectBreakerPolicy.backoffMinutes(1, 360));
      assertEquals(1440, CollectBreakerPolicy.backoffMinutes(2, 360));
      assertEquals(cap, CollectBreakerPolicy.backoffMinutes(3, 360));
      assertEquals(cap, CollectBreakerPolicy.backoffMinutes(99, 360));
    }

    @Test
    @DisplayName("운영 주기 720분에서는 한 번에 상한에 닿는다")
    void reachesCapImmediatelyAtProductionInterval() {
      // 사실상 "한 회차 건너뛰기"가 된다. 주기가 길수록 백오프의 여지가 작다는 점은 설계상 받아들인다 —
      // 이미 충분히 느리게 두드리고 있다는 뜻이기 때문이다.
      assertEquals(
          CollectBreakerPolicy.backoffCapMinutes(), CollectBreakerPolicy.backoffMinutes(1, 720));
    }

    @Test
    @DisplayName("주기를 모르면 정책 기본값으로 계산한다")
    void fallsBackWhenIntervalUnknown() {
      assertTrue(CollectBreakerPolicy.backoffMinutes(1, 0) > 0);
    }
  }

  private static State trippedState() {
    State state = State.healthy();
    for (int i = 0; i < CollectBreakerPolicy.tripAfterFailures(); i++) {
      state = CollectBreakerPolicy.onFailure(state, plusMinutes(T0, i * 10));
    }
    assertTrue(state.isTripped(), "테스트 준비: 트립 상태를 만들지 못했다.");
    return state;
  }
}

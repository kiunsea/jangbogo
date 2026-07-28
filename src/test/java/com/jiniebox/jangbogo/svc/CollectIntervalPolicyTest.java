package com.jiniebox.jangbogo.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jiniebox.jangbogo.svc.util.CollectIntervalPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link CollectIntervalPolicy} 회귀 테스트 (Phase 3-2).
 *
 * <p>하한 검증이 코드 어디에도 없어 UI 조작이나 API 직접 호출로 1분 주기가 저장될 수 있었다. 짧은 주기는 같은 계정으로 로그인을 반복하게 만들어 봇 차단을
 * 자초한다.
 *
 * <p>규칙은 "0 이거나 하한 이상" 이다. 즉 {@code 0 < 값 < 하한} 구간만 거부한다.
 */
class CollectIntervalPolicyTest {

  @AfterEach
  void clearOverride() {
    System.clearProperty(CollectIntervalPolicy.MIN_PROPERTY);
  }

  @Test
  @DisplayName("기본 하한은 360분이다")
  void defaultMinimumIsSixHours() {
    assertEquals(360, CollectIntervalPolicy.DEFAULT_MIN_MINUTES);
    assertEquals(360, CollectIntervalPolicy.minMinutes());
  }

  @Test
  @DisplayName("0 은 '자동수집 안 함' 이므로 허용한다")
  void zeroIsAllowed() {
    assertTrue(CollectIntervalPolicy.isAllowed(0));
  }

  @Test
  @DisplayName("하한 이상은 허용한다")
  void valuesAtOrAboveMinimumAreAllowed() {
    assertTrue(CollectIntervalPolicy.isAllowed(360), "하한과 같은 값");
    assertTrue(CollectIntervalPolicy.isAllowed(720), "운영 기본값 720분");
    assertTrue(CollectIntervalPolicy.isAllowed(1440));
  }

  @Test
  @DisplayName("0 초과 하한 미만은 거부한다")
  void valuesBetweenZeroAndMinimumAreRejected() {
    assertFalse(CollectIntervalPolicy.isAllowed(1), "API 직접 호출로 들어올 수 있던 값");
    assertFalse(CollectIntervalPolicy.isAllowed(10), "수정 전 seq=1 이 실제로 갖고 있던 값");
    assertFalse(CollectIntervalPolicy.isAllowed(359), "경계 바로 아래");
  }

  @Test
  @DisplayName("null 과 음수는 거부한다")
  void nullAndNegativeAreRejected() {
    assertFalse(CollectIntervalPolicy.isAllowed(null));
    assertFalse(CollectIntervalPolicy.isAllowed(-1));
  }

  @Test
  @DisplayName("이미 저장된 하한 미만 값은 스케줄 시 하한으로 올린다")
  void clampRaisesStoredValuesBelowMinimum() {
    assertEquals(360, CollectIntervalPolicy.clampForSchedule("1", 10));
    assertEquals(360, CollectIntervalPolicy.clampForSchedule("1", 359));
  }

  @Test
  @DisplayName("0 과 하한 이상은 스케줄 시 그대로 둔다")
  void clampLeavesValidValuesAlone() {
    assertEquals(0, CollectIntervalPolicy.clampForSchedule("1", 0), "0 은 끌어올리지 않는다");
    assertEquals(360, CollectIntervalPolicy.clampForSchedule("1", 360));
    assertEquals(720, CollectIntervalPolicy.clampForSchedule("2", 720));
  }

  @Test
  @DisplayName("시스템 프로퍼티로 하한을 낮출 수 있다 (개발용)")
  void systemPropertyOverridesMinimum() {
    System.setProperty(CollectIntervalPolicy.MIN_PROPERTY, "10");

    assertEquals(10, CollectIntervalPolicy.minMinutes());
    assertTrue(CollectIntervalPolicy.isAllowed(10));
    assertFalse(CollectIntervalPolicy.isAllowed(9));
    assertEquals(10, CollectIntervalPolicy.clampForSchedule("1", 5));
  }

  @Test
  @DisplayName("프로퍼티 값이 잘못되면 기본 하한으로 되돌아간다")
  void invalidPropertyFallsBackToDefault() {
    System.setProperty(CollectIntervalPolicy.MIN_PROPERTY, "무제한");
    assertEquals(360, CollectIntervalPolicy.minMinutes(), "해석 실패 시 기본값");

    System.setProperty(CollectIntervalPolicy.MIN_PROPERTY, "0");
    assertEquals(360, CollectIntervalPolicy.minMinutes(), "0 이하로는 낮출 수 없다");

    System.setProperty(CollectIntervalPolicy.MIN_PROPERTY, "-5");
    assertEquals(360, CollectIntervalPolicy.minMinutes());
  }

  @Test
  @DisplayName("거부 사유에 하한과 입력값이 모두 들어간다")
  void rejectionMessageNamesBoundAndInput() {
    String message = CollectIntervalPolicy.rejectionMessage(10);

    assertTrue(message.contains("360"), "하한을 알려야 한다");
    assertTrue(message.contains("10"), "입력값을 알려야 한다");
  }
}

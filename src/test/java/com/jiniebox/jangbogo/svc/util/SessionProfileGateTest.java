package com.jiniebox.jangbogo.svc.util;

import static com.jiniebox.jangbogo.svc.util.ExecutionContextDetector.ExecutionContext.INTERACTIVE;
import static com.jiniebox.jangbogo.svc.util.ExecutionContextDetector.ExecutionContext.SERVICE_SESSION_0;
import static com.jiniebox.jangbogo.svc.util.ExecutionContextDetector.ExecutionContext.UNKNOWN;
import static com.jiniebox.jangbogo.svc.util.SessionProfileGate.Decision.PROCEED;
import static com.jiniebox.jangbogo.svc.util.SessionProfileGate.Decision.PROFILE_LOCKED;
import static com.jiniebox.jangbogo.svc.util.SessionProfileGate.Decision.PROFILE_MISSING;
import static com.jiniebox.jangbogo.svc.util.SessionProfileGate.Decision.PROFILE_OWNER_MISMATCH;
import static com.jiniebox.jangbogo.svc.util.SessionProfileGate.Decision.REQUIRES_USER_SESSION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 세션 프로필 진입 게이트 검증 (Phase 5-4 · 5-5).
 *
 * <p>이 게이트를 수집 경로에 끼우면서 지켜야 할 첫 번째 계약은 <b>꺼져 있을 때 아무것도 바꾸지 않는 것</b>이다. 그게 깨지면 아직 검증되지 않은 Phase 5
 * 경로가 기존 수집을 망가뜨린다.
 *
 * <p>두 번째는 <b>막힌 이유를 구분해서 남기는 것</b>이다. 조건이 어긋난 채로 시도하면 수집이 실패가 아니라 로그인 화면에서 멈춘 채 타임아웃되고, 로그만으로는 원인을
 * 가릴 수 없다.
 *
 * <p>순수 함수라 브라우저·네트워크·DB 를 쓰지 않는다.
 *
 * @author KIUNSEA
 */
class SessionProfileGateTest {

  // ---------------------------------------------------------------
  // 꺼져 있으면 아무것도 바꾸지 않는다
  // ---------------------------------------------------------------

  @Test
  @DisplayName("적용 대상이 아니면 무조건 통과한다 — 기존 동작이 유지된다")
  void doesNothingWhenNotApplicable() {
    // 킬스위치가 꺼졌거나 그 몰이 옵트인하지 않은 경우다. 나머지 조건이 아무리 나빠도 막지 않는다.
    assertEquals(
        PROCEED, SessionProfileGate.evaluate(false, () -> SERVICE_SESSION_0, null, null, null));
    assertEquals(PROCEED, SessionProfileGate.evaluate(false, () -> UNKNOWN, null, "다른사람", "나"));
  }

  @Test
  @DisplayName("적용 대상이 아니면 실행 컨텍스트를 탐지조차 하지 않는다 (Phase 5-18)")
  void doesNotDetectTheContextWhenNotApplicable() {
    // 값으로 받던 때는 자바가 호출 전에 인자를 평가해, 첫 줄에서 통과할 몰에서도
    // 매 회차 tasklist 외부 프로세스가 떴다(최대 5초 대기). 옵트인 OFF 몰의 런타임
    // 동작이 그대로여야 한다는 수용 기준 10 의 기존 위반이었다.
    AtomicInteger calls = new AtomicInteger();

    assertEquals(
        PROCEED,
        SessionProfileGate.evaluate(
            false,
            () -> {
              calls.incrementAndGet();
              return SERVICE_SESSION_0;
            },
            null,
            null,
            null));

    assertEquals(0, calls.get(), "적용 대상이 아닌데 실행 컨텍스트를 탐지했다.");
  }

  @Test
  @DisplayName("적용 대상이면 실행 컨텍스트를 한 번만 탐지한다")
  void detectsTheContextOnceWhenApplicable() {
    // 반대로 대상일 때는 반드시 봐야 한다. 안 보면 세션 0 판정이 통째로 죽는다.
    AtomicInteger calls = new AtomicInteger();

    SessionProfileGate.evaluate(
        true,
        () -> {
          calls.incrementAndGet();
          return SERVICE_SESSION_0;
        },
        "ssg",
        "kim",
        "kim");

    assertEquals(1, calls.get(), "탐지 횟수가 1이 아니다.");
  }

  // ---------------------------------------------------------------
  // 네 가지 사유
  // ---------------------------------------------------------------

  @Test
  @DisplayName("세션 0 이면 나머지를 볼 것도 없이 막는다")
  void blocksInSessionZero() {
    // 프로필이 멀쩡해도 띄울 자리가 없다.
    assertEquals(
        REQUIRES_USER_SESSION,
        SessionProfileGate.evaluate(true, () -> SERVICE_SESSION_0, "ssg", "kim", "kim"));
  }

  @Test
  @DisplayName("실행 컨텍스트를 모르면 막는다 — 모를 때 띄우면 증상이 재현된다")
  void blocksWhenTheContextIsUnknown() {
    assertEquals(
        REQUIRES_USER_SESSION,
        SessionProfileGate.evaluate(true, () -> UNKNOWN, "ssg", "kim", "kim"));
    assertEquals(
        REQUIRES_USER_SESSION, SessionProfileGate.evaluate(true, () -> null, "ssg", "kim", "kim"));
  }

  @Test
  @DisplayName("프로필 이름이 없으면 막는다")
  void blocksWhenTheProfileIsMissing() {
    for (String missing : new String[] {null, "", "   "}) {
      assertEquals(
          PROFILE_MISSING,
          SessionProfileGate.evaluate(true, () -> INTERACTIVE, missing, "kim", "kim"));
    }
  }

  @Test
  @DisplayName("다른 OS 계정이 만든 프로필이면 막는다")
  void blocksOnOwnerMismatch() {
    // 열어도 세션이 살아나지 않는다. 시도하면 '로그인 화면에서 멈춤' 이 된다.
    assertEquals(
        PROFILE_OWNER_MISMATCH,
        SessionProfileGate.evaluate(true, () -> INTERACTIVE, "ssg", "다른사람", "나"));
  }

  @Test
  @DisplayName("소유자 표기의 대소문자·공백 차이는 불일치로 보지 않는다")
  void ownerComparisonIsForgiving() {
    // 윈도우 계정명은 대소문자를 가리지 않는다. 여기서 틀리면 멀쩡한 프로필이 막힌다.
    assertEquals(
        PROCEED, SessionProfileGate.evaluate(true, () -> INTERACTIVE, "ssg", " KIM ", "kim"));
  }

  @Test
  @DisplayName("소유자가 기록돼 있지 않으면 검사하지 않는다")
  void skipsTheOwnerCheckWhenNotRecorded() {
    // 소유자를 적기 전에 만든 프로필을 '미기록' 만으로 막으면 멀쩡한 것을 못 쓰게 된다.
    assertEquals(PROCEED, SessionProfileGate.evaluate(true, () -> INTERACTIVE, "ssg", null, "kim"));
    assertEquals(PROCEED, SessionProfileGate.evaluate(true, () -> INTERACTIVE, "ssg", "  ", "kim"));
  }

  @Test
  @DisplayName("조건이 다 맞으면 통과한다")
  void proceedsWhenEverythingIsInPlace() {
    assertEquals(
        PROCEED, SessionProfileGate.evaluate(true, () -> INTERACTIVE, "ssg", "kim", "kim"));
  }

  // ---------------------------------------------------------------
  // 락 결과 옮기기
  // ---------------------------------------------------------------

  @Test
  @DisplayName("다른 프로세스가 잡고 있으면 PROFILE_LOCKED 다")
  void mapsLockOutcomes() {
    assertEquals(
        PROFILE_LOCKED, SessionProfileGate.fromLockOutcome(MallProfileLock.Outcome.HELD_BY_OTHER));
    assertEquals(PROCEED, SessionProfileGate.fromLockOutcome(MallProfileLock.Outcome.ACQUIRED));
  }

  @Test
  @DisplayName("잠글 수 없는 환경은 막지 않는다 — 막을 근거가 없다")
  void doesNotBlockWhenLockingIsUnavailable() {
    assertEquals(PROCEED, SessionProfileGate.fromLockOutcome(MallProfileLock.Outcome.UNAVAILABLE));
  }

  // ---------------------------------------------------------------
  // 기록에 쓰이는 값
  // ---------------------------------------------------------------

  @Test
  @DisplayName("막은 사유는 전부 사람이 읽을 수 있는 문구를 갖는다")
  void everyBlockingDecisionCarriesAReason() {
    // 수집 로그에 그대로 들어간다. 비어 있으면 왜 막혔는지 알 수 없다.
    for (SessionProfileGate.Decision d : SessionProfileGate.Decision.values()) {
      if (d == PROCEED) {
        assertNull(d.reason(), "통과에는 사유가 없어야 한다.");
        assertTrue(d.canProceed());
      } else {
        assertNotNull(d.reason(), d + " 에 사유가 없다.");
        assertFalse(d.reason().isBlank(), d + " 의 사유가 비었다.");
        assertFalse(d.canProceed(), d + " 가 통과로 판정됐다.");
      }
    }
  }
}

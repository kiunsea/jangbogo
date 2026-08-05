package com.jiniebox.jangbogo.svc.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jiniebox.jangbogo.svc.MallCollectOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * 수집 진입 판정 검증 (Phase 5-19).
 *
 * <p>핵심 불변식은 <b>게이트에 막힌 몰은 브라우저 자리를 잡지 않는다</b> 는 것이다. 잡았다 곧바로 놓더라도 그 사이 대기 중인 다른 몰이 밀리고, 동시 실행 수
 * 기본값이 1 이라 자리는 하나뿐이다.
 *
 * <p>그 단언이 공허하지 않다는 것은 <b>양성 대조군</b>이 증명한다 — 같은 제한기에서 게이트를 통과시키면 자리는 실제로 줄어든다. 대조군만 두면 "제한기가 애초에
 * 동작하지 않아서" 자리가 안 줄어든 경우와 구분되지 않는다.
 *
 * <p>브라우저·네트워크·DB 를 쓰지 않는다. 세마포어만 만진다.
 *
 * @author KIUNSEA
 */
class CollectAdmissionTest {

  @Test
  @DisplayName("게이트에 막히면 브라우저 자리를 잡지 않는다")
  void gateBlockedDoesNotConsumeAPermit() {
    BrowserConcurrencyLimiter limiter = new BrowserConcurrencyLimiter(1);

    try (CollectAdmission admission =
        CollectAdmission.evaluate(SessionProfileGate.Decision.PROFILE_MISSING, limiter, 0)) {

      assertFalse(admission.admitted());
      assertEquals(CollectAdmission.Verdict.GATE_BLOCKED, admission.verdict());
      assertEquals(1, limiter.availablePermits(), "게이트에 막혔는데 브라우저 자리를 잡았다.");
    }
    assertEquals(1, limiter.availablePermits());
  }

  @Test
  @DisplayName("[양성 대조군] 게이트를 통과하면 자리가 실제로 줄어든다")
  void admittedConsumesAPermit() {
    BrowserConcurrencyLimiter limiter = new BrowserConcurrencyLimiter(1);

    try (CollectAdmission admission =
        CollectAdmission.evaluate(SessionProfileGate.Decision.PROCEED, limiter, 0)) {

      assertTrue(admission.admitted(), "자리가 비어 있는데 통과시키지 않았다.");
      assertEquals(0, limiter.availablePermits(), "통과했는데 자리가 줄지 않았다 — 제한기가 죽어 있다.");
      assertNull(admission.code());
      assertNull(admission.reason());
    }
    assertEquals(1, limiter.availablePermits(), "닫았는데 자리가 돌아오지 않았다.");
  }

  @Test
  @DisplayName("막힌 사유는 게이트 판정 이름을 그대로 쓴다")
  void gateBlockedCarriesTheDecisionName() {
    BrowserConcurrencyLimiter limiter = new BrowserConcurrencyLimiter(1);

    try (CollectAdmission admission =
        CollectAdmission.evaluate(SessionProfileGate.Decision.PROFILE_OWNER_MISMATCH, limiter, 0)) {

      assertEquals("PROFILE_OWNER_MISMATCH", admission.code());
      assertEquals(SessionProfileGate.Decision.PROFILE_OWNER_MISMATCH.reason(), admission.reason());
    }
  }

  @Test
  @DisplayName("자리가 없으면 BROWSER_BUSY 로 물러난다")
  @Timeout(10)
  void browserBusyWhenNoPermitIsFree() {
    BrowserConcurrencyLimiter limiter = new BrowserConcurrencyLimiter(1);

    try (BrowserConcurrencyLimiter.Lease held = limiter.acquire(0)) {
      assertTrue(held.acquired());

      try (CollectAdmission admission =
          CollectAdmission.evaluate(SessionProfileGate.Decision.PROCEED, limiter, 0)) {

        assertFalse(admission.admitted());
        assertEquals(CollectAdmission.Verdict.BROWSER_BUSY, admission.verdict());
        assertEquals(CollectAdmission.BROWSER_BUSY_CODE, admission.code());
        assertNotNull(admission.reason());
      }
      assertEquals(0, limiter.availablePermits(), "물러나면서 남의 자리를 놓아 버렸다.");
    }
    assertEquals(1, limiter.availablePermits());
  }

  @Test
  @DisplayName("막힌 판정은 수집 결과로 그대로 옮겨진다")
  @Timeout(10)
  void blockedAdmissionBecomesABlockedOutcome() {
    BrowserConcurrencyLimiter limiter = new BrowserConcurrencyLimiter(1);

    try (BrowserConcurrencyLimiter.Lease held = limiter.acquire(0)) {
      try (CollectAdmission busy =
          CollectAdmission.evaluate(SessionProfileGate.Decision.PROCEED, limiter, 0)) {

        MallCollectOutcome outcome = MallCollectOutcome.blockedBy(busy);

        assertFalse(outcome.collected());
        assertEquals(MallCollectOutcome.Status.BROWSER_BUSY, outcome.status());
        assertEquals(busy.code(), outcome.code());
        assertEquals(busy.reason(), outcome.reason());
        assertTrue(outcome.newOrderSeqs().isEmpty());

        // 브라우저 자리 부족을 세션 프로필 게이트로 적으면, 그 기능을 켠 적 없는 사용자가
        // 수집 로그 화면에서 원인을 거기서 찾게 된다.
        assertEquals(MallCollectOutcome.STEP_BROWSER_CONCURRENCY, outcome.stepName());
        assertNotEquals(MallCollectOutcome.STEP_SESSION_PROFILE_GATE, outcome.stepName());
      }
    }
  }

  @Test
  @DisplayName("게이트 판정이나 제한기가 없으면 통과로 간주하지 않고 거부한다")
  void refusesMissingCollaborators() {
    BrowserConcurrencyLimiter limiter = new BrowserConcurrencyLimiter(1);

    assertThrows(IllegalArgumentException.class, () -> CollectAdmission.evaluate(null, limiter, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> CollectAdmission.evaluate(SessionProfileGate.Decision.PROCEED, null, 0));
    assertEquals(1, limiter.availablePermits(), "거부하면서 자리를 잡아 버렸다.");
  }
}

package com.jiniebox.jangbogo.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jiniebox.jangbogo.svc.util.BrowserConcurrencyLimiter;
import com.jiniebox.jangbogo.svc.util.CollectAdmission;
import com.jiniebox.jangbogo.svc.util.SessionExpiryDetector;
import com.jiniebox.jangbogo.svc.util.SessionProfileGate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 수집 결과 구분 검증 (Phase 5-19).
 *
 * <p>예전에는 결과가 {@code List<Integer>} 하나였다. 그래서 <b>"막혀서 못 돌았다" 와 "돌았는데 신규 주문이 없었다" 가 똑같이 빈 목록</b>으로
 * 나왔다. 아래 첫 두 테스트가 그 둘을 갈라 놓는다.
 *
 * <p>브라우저·네트워크·DB 를 쓰지 않는다.
 *
 * @author KIUNSEA
 */
class MallCollectOutcomeTest {

  @Test
  @DisplayName("신규 주문이 0 건이어도 수집은 돈 것이다")
  void emptyResultIsStillACollection() {
    MallCollectOutcome outcome = MallCollectOutcome.success(List.of());

    assertTrue(outcome.collected(), "빈 목록을 '못 돌았다' 로 읽으면 예전 결함이 그대로다.");
    assertEquals(MallCollectOutcome.Status.COLLECTED, outcome.status());
    assertTrue(outcome.newOrderSeqs().isEmpty());
  }

  @Test
  @DisplayName("[대조군] 이미 실행 중이라 건너뛴 회차는 수집이 아니다")
  void alreadyRunningIsNotACollection() {
    MallCollectOutcome outcome = MallCollectOutcome.alreadyRunning();

    assertFalse(outcome.collected());
    assertEquals(MallCollectOutcome.Status.ALREADY_RUNNING, outcome.status());
    assertEquals(MallCollectOutcome.ALREADY_RUNNING_CODE, outcome.code());
    assertTrue(outcome.newOrderSeqs().isEmpty());
  }

  @Test
  @DisplayName("게이트 차단은 사유를 그대로 싣고 온다")
  void gateBlockedCarriesCodeAndReason() {
    try (CollectAdmission admission =
        CollectAdmission.evaluate(
            SessionProfileGate.Decision.PROFILE_MISSING, BrowserConcurrencyLimiter.shared(), 0)) {

      MallCollectOutcome outcome = MallCollectOutcome.blockedBy(admission);

      assertFalse(outcome.collected());
      assertEquals(MallCollectOutcome.Status.GATE_BLOCKED, outcome.status());
      assertEquals("PROFILE_MISSING", outcome.code());
      assertEquals(SessionProfileGate.Decision.PROFILE_MISSING.reason(), outcome.reason());
    }
  }

  @Test
  @DisplayName("막히지 않은 판정을 차단 결과로 바꾸려 하면 거부한다")
  void refusesToFakeABlock() {
    assertThrows(IllegalArgumentException.class, () -> MallCollectOutcome.blockedBy(null));

    try (CollectAdmission admitted =
        CollectAdmission.evaluate(
            SessionProfileGate.Decision.PROCEED, BrowserConcurrencyLimiter.shared(), 0)) {
      if (admitted.admitted()) {
        assertThrows(IllegalArgumentException.class, () -> MallCollectOutcome.blockedBy(admitted));
      }
    }
  }

  @Test
  @DisplayName("세션 만료도 수집이 아니다 — 그리고 다른 막힘과 구분된다 (Phase 5-10)")
  void sessionExpiryIsItsOwnOutcome() {
    // 게이트·브라우저 자리 부족은 '잠시 뒤 다시' 로 풀리지만 만료는 사람이 다시 로그인해야 풀린다.
    // 한 통에 담으면 둘 중 하나는 반드시 틀린 안내를 하게 된다.
    MallCollectOutcome outcome = MallCollectOutcome.sessionExpired(null);

    assertFalse(outcome.collected());
    assertTrue(outcome.sessionExpired());
    assertEquals(MallCollectOutcome.Status.SESSION_EXPIRED, outcome.status());
    assertEquals(MallCollectOutcome.SESSION_EXPIRED_CODE, outcome.code());
    assertTrue(outcome.newOrderSeqs().isEmpty());

    assertFalse(MallCollectOutcome.alreadyRunning().sessionExpired());
    assertFalse(MallCollectOutcome.success(List.of()).sessionExpired());
  }

  @Test
  @DisplayName("만료 사유는 비워 둘 수 없다 — 읽은 사람이 할 일을 가리켜야 한다")
  void sessionExpiryAlwaysCarriesAReason() {
    // 이 문자열은 jbg_collect_log 의 SKIPPED 사유로 저장되고 화면에 그대로 나간다.
    for (String blank : new String[] {null, "", "   "}) {
      String reason = MallCollectOutcome.sessionExpired(blank).reason();

      assertNotNull(reason);
      assertFalse(reason.isBlank());
      assertEquals(SessionExpiryDetector.Verdict.EXPIRED.reason(), reason);
    }

    assertEquals("직접 준 사유", MallCollectOutcome.sessionExpired(" 직접 준 사유 ").reason());
  }

  @Test
  @DisplayName("만료의 단계 이름은 세션 프로필 게이트와 다르다")
  void sessionExpiryHasItsOwnStepName() {
    // 게이트는 '시도하기 전에' 막힌 것이고 만료는 '실제로 주입해 보고' 밀린 것이다. 사람이 할 일도
    // 다르다 — 한 이름으로 뭉뚱그리면 수집 로그 화면의 단계 필터가 그 둘을 갈라 주지 못한다.
    String step = MallCollectOutcome.sessionExpired(null).stepName();

    assertEquals(MallCollectOutcome.STEP_SESSION_EXPIRY, step);
    assertNotEquals(MallCollectOutcome.STEP_SESSION_PROFILE_GATE, step);
    assertNotEquals(MallCollectOutcome.STEP_BROWSER_CONCURRENCY, step);
    assertNotEquals(MallCollectOutcome.STEP_ALREADY_RUNNING, step);
  }

  @Test
  @DisplayName("돌려준 목록은 호출부가 바꿀 수 없다")
  void newOrderSeqsAreCopiedAndImmutable() {
    List<Integer> source = new ArrayList<>(List.of(1, 2));
    MallCollectOutcome outcome = MallCollectOutcome.success(source);

    source.add(3);
    assertEquals(2, outcome.newOrderSeqs().size(), "원본을 바꿨더니 결과가 따라 바뀌었다.");
    assertThrows(UnsupportedOperationException.class, () -> outcome.newOrderSeqs().add(4));
  }

  @Test
  @DisplayName("목록이 없으면 빈 목록으로 다룬다")
  void nullBecomesEmpty() {
    assertTrue(MallCollectOutcome.success(null).newOrderSeqs().isEmpty());
  }

  @Test
  @DisplayName("막힌 사유마다 단계 이름이 다르다 — 게이트 이름으로 뭉뚱그리지 않는다")
  void stepNamesDistinguishTheReason() {
    assertNull(MallCollectOutcome.success(List.of()).stepName(), "수집이 돌았으면 단계 이름이 없다.");
    assertEquals(
        MallCollectOutcome.STEP_ALREADY_RUNNING, MallCollectOutcome.alreadyRunning().stepName());

    try (CollectAdmission gateBlocked =
        CollectAdmission.evaluate(
            SessionProfileGate.Decision.REQUIRES_USER_SESSION,
            BrowserConcurrencyLimiter.shared(),
            0)) {

      // 게이트 차단만은 5-4 부터 쓰던 이름을 유지한다 — 기존 로그·필터와 이어져야 한다.
      assertEquals(
          MallCollectOutcome.STEP_SESSION_PROFILE_GATE,
          MallCollectOutcome.blockedBy(gateBlocked).stepName());
    }

    assertNotEquals(
        MallCollectOutcome.STEP_SESSION_PROFILE_GATE, MallCollectOutcome.STEP_BROWSER_CONCURRENCY);
    assertNotEquals(
        MallCollectOutcome.STEP_SESSION_PROFILE_GATE, MallCollectOutcome.STEP_ALREADY_RUNNING);
  }
}

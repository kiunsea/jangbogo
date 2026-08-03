package com.jiniebox.jangbogo.svc.util;

import static com.jiniebox.jangbogo.svc.util.ExecutionContextDetector.ExecutionContext.INTERACTIVE;
import static com.jiniebox.jangbogo.svc.util.ExecutionContextDetector.ExecutionContext.SERVICE_SESSION_0;
import static com.jiniebox.jangbogo.svc.util.ExecutionContextDetector.ExecutionContext.UNKNOWN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 실행 컨텍스트 판별 검증 (Phase 5-1).
 *
 * <p>세션 0(윈도우 서비스)에는 데스크톱이 없어 headed 브라우저를 띄울 수 없다. 이걸 모르면 수집이 "실패"가 아니라 <b>로그인 화면에서 멈춘 채
 * 타임아웃</b>으로 나타나고, 로그만으로는 원인을 가릴 수 없다.
 *
 * <p>판단 로직은 전부 순수 함수라 브라우저·프로세스 없이 검증한다. {@code tasklist} 를 실제로 돌리는 부분만 실측이 남는다.
 *
 * @author KIUNSEA
 */
class ExecutionContextDetectorTest {

  // ---------------------------------------------------------------
  // 우선순위 — 재정의 > 세션 번호 > 휴리스틱
  // ---------------------------------------------------------------

  @Test
  @DisplayName("사람이 지정한 재정의가 가장 우선이다")
  void overrideWinsOverEverything() {
    // 세션 번호가 0(서비스)이라도 사람이 interactive 라고 하면 그쪽을 따른다.
    assertEquals(
        INTERACTIVE,
        ExecutionContextDetector.decide("interactive", 0, "service", "Services", "PC$"));
    assertEquals(
        SERVICE_SESSION_0, ExecutionContextDetector.decide("service", 3, null, "Console", "kim"));
  }

  @Test
  @DisplayName("재정의가 없으면 세션 번호가 권위값이다")
  void sessionNumberIsAuthoritative() {
    // 휴리스틱이 반대를 가리켜도 세션 번호를 따른다.
    assertEquals(
        SERVICE_SESSION_0, ExecutionContextDetector.decide(null, 0, null, "Console", "kim"));
    assertEquals(
        INTERACTIVE, ExecutionContextDetector.decide(null, 1, "service", "Services", "PC$"));
  }

  @Test
  @DisplayName("세션 번호가 없으면 런처가 심은 실행 모드를 본다")
  void fallsBackToTheLaunchMode() {
    assertEquals(
        SERVICE_SESSION_0, ExecutionContextDetector.decide(null, null, "service", null, null));
    assertEquals(
        SERVICE_SESSION_0, ExecutionContextDetector.decide(null, null, "SERVICE", null, null));
  }

  @Test
  @DisplayName("아무 신호도 없으면 UNKNOWN 이다 — 모르는 것을 안다고 하지 않는다")
  void reportsUnknownWhenNothingIsConclusive() {
    assertEquals(UNKNOWN, ExecutionContextDetector.decide(null, null, null, null, null));
  }

  // ---------------------------------------------------------------
  // 재정의 파싱
  // ---------------------------------------------------------------

  @Test
  @DisplayName("재정의는 두 표기를 받고, 모르는 값은 재정의로 보지 않는다")
  void parsesTheOverrideDefensively() {
    assertEquals(SERVICE_SESSION_0, ExecutionContextDetector.fromOverride("service"));
    assertEquals(SERVICE_SESSION_0, ExecutionContextDetector.fromOverride("session0"));
    assertEquals(INTERACTIVE, ExecutionContextDetector.fromOverride("interactive"));
    assertEquals(INTERACTIVE, ExecutionContextDetector.fromOverride("Console"));

    // 오타를 임의로 해석하면 틀린 확신을 만든다.
    assertEquals(UNKNOWN, ExecutionContextDetector.fromOverride("serivce"));
    assertEquals(UNKNOWN, ExecutionContextDetector.fromOverride(null));
    assertEquals(UNKNOWN, ExecutionContextDetector.fromOverride("   "));
  }

  // ---------------------------------------------------------------
  // 환경 휴리스틱
  // ---------------------------------------------------------------

  @Test
  @DisplayName("SESSIONNAME 이 Services 면 서비스, Console·RDP 면 사람이 로그인한 자리다")
  void readsTheSessionName() {
    assertEquals(SERVICE_SESSION_0, ExecutionContextDetector.fromEnvironment("Services", "kim"));
    assertEquals(INTERACTIVE, ExecutionContextDetector.fromEnvironment("Console", "kim"));
    assertEquals(INTERACTIVE, ExecutionContextDetector.fromEnvironment("RDP-Tcp#0", "kim"));
  }

  @Test
  @DisplayName("머신 계정(NAME$)은 LocalSystem 서비스의 표식이다")
  void treatsAMachineAccountAsService() {
    assertEquals(SERVICE_SESSION_0, ExecutionContextDetector.fromEnvironment(null, "NUC$"));
  }

  @Test
  @DisplayName("신호가 엇갈리면 UNKNOWN 이다 — 한쪽을 임의로 우선하지 않는다")
  void conflictingSignalsYieldUnknown() {
    // Console 인데 머신 계정이면 둘 중 하나가 틀린 것이다. 찍지 않는다.
    assertEquals(UNKNOWN, ExecutionContextDetector.fromEnvironment("Console", "NUC$"));
    assertEquals(UNKNOWN, ExecutionContextDetector.fromEnvironment(null, null));
    assertEquals(UNKNOWN, ExecutionContextDetector.fromEnvironment("알수없는값", "kim"));
  }

  // ---------------------------------------------------------------
  // tasklist 출력 파싱
  // ---------------------------------------------------------------

  @Test
  @DisplayName("tasklist CSV 에서 세션 번호를 뽑는다")
  void parsesTheSessionNumberFromCsv() {
    // 메모리 칸에 쉼표가 들어가므로 단순 split 으로는 칸이 어긋난다.
    assertEquals(
        0,
        ExecutionContextDetector.parseSessionNumber(
            "\"java.exe\",\"1234\",\"Services\",\"0\",\"123,456 K\""));
    assertEquals(
        2,
        ExecutionContextDetector.parseSessionNumber(
            "\"java.exe\",\"5678\",\"Console\",\"2\",\"1,048,576 K\""));
  }

  @Test
  @DisplayName("실측 출력 형식 — 이 머신에서 실제로 받은 줄")
  void parsesTheShapeObservedOnARealMachine() {
    // 2026-08-03 에 실행 중인 배포본 프로세스로 tasklist 를 돌려 그대로 받은 줄이다.
    // 메모리 칸에 쉼표가 들어가는 것이 여기서 확인된다 — split(",") 로는 칸이 밀린다.
    assertEquals(
        2,
        ExecutionContextDetector.parseSessionNumber(
            "\"java.exe\",\"29408\",\"RDP-Tcp#29\",\"2\",\"99,292 K\""));

    // 같은 줄의 세션 이름만으로도 같은 결론이 나와야 한다 — 두 신호가 어긋나면 판별이 흔들린다.
    assertEquals(INTERACTIVE, ExecutionContextDetector.fromEnvironment("RDP-Tcp#29", "kim"));
  }

  @Test
  @DisplayName("일치하는 프로세스가 없다는 안내문은 세션 번호가 아니다")
  void ignoresTheNoTasksMessage() {
    assertNull(
        ExecutionContextDetector.parseSessionNumber(
            "INFO: No tasks are running which match the specified criteria."));
    assertNull(ExecutionContextDetector.parseSessionNumber(""));
    assertNull(ExecutionContextDetector.parseSessionNumber(null));
  }

  @Test
  @DisplayName("칸이 모자라거나 숫자가 아니면 건너뛰고 다음 줄을 본다")
  void skipsMalformedLines() {
    assertNull(ExecutionContextDetector.parseSessionNumber("\"java.exe\",\"1234\""));
    assertNull(ExecutionContextDetector.parseSessionNumber("\"a\",\"b\",\"c\",\"세션없음\""));

    String mixed =
        "INFO: something\n\"a\",\"b\",\"c\",\"nope\"\n\"java.exe\",\"1234\",\"Services\",\"0\",\"1 K\"";
    assertEquals(0, ExecutionContextDetector.parseSessionNumber(mixed));
  }

  @Test
  @DisplayName("세션 번호 조회가 터져도 판별이 멈추지 않는다")
  void sessionLookupFailureIsNotFatal() {
    // 판별 하나 때문에 기동이 막히면 안 된다.
    assertNull(
        ExecutionContextDetector.currentSessionNumber(
            () -> {
              throw new IllegalStateException("tasklist 없음");
            }));
    assertNull(ExecutionContextDetector.currentSessionNumber(() -> null));
    assertEquals(
        0,
        ExecutionContextDetector.currentSessionNumber(
            () -> "\"java.exe\",\"1\",\"Services\",\"0\",\"1 K\""));
  }

  // ---------------------------------------------------------------
  // 호출측이 쓰는 판정
  // ---------------------------------------------------------------

  @Test
  @DisplayName("UNKNOWN 은 브라우저를 띄울 수 있다고 보지 않는다")
  void unknownIsTreatedConservatively() {
    // 모를 때 띄우면 세션 0 에서 '로그인 화면에서 멈춘 채 타임아웃' 이 재현된다.
    assertTrue(INTERACTIVE.canRunHeadedBrowser());
    assertFalse(SERVICE_SESSION_0.canRunHeadedBrowser());
    assertFalse(UNKNOWN.canRunHeadedBrowser(), "UNKNOWN 을 실행 가능으로 판정했다.");
  }
}

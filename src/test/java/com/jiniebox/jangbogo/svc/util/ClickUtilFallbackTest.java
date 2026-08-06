package com.jiniebox.jangbogo.svc.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;

/**
 * {@link ClickUtil} JS 폴백의 원예외 보존 검증 (전파 P1-5).
 *
 * <p>배경: 폴백까지 실패했을 때 밖으로 나가는 것이 JS 실행 예외 하나뿐이면, 클릭을 <b>무엇이</b> 가로챘는지 적힌 {@code
 * ElementClickInterceptedException} 이 사라진다. 남는 것은 폴백 경고 로그 한 줄인데 그건 예외 클래스명만 담아 대체가 되지 않는다.
 * 2026-05-30 사고에서 원인을 봇 차단이 아니라 프로모션 배너로 특정한 단서가 바로 그 메시지의 {@code Other element would receive the
 * click} 줄이었다. 그래서 폴백 실패 예외에 원예외를 suppressed 로 매단다.
 *
 * <p>대조군을 함께 둔다 — "폴백이 안 돌 때는 붙지 않는다"까지 확인해야, 무조건 붙이는 구현이나 폴백이 애초에 안 도는 상태를 통과로 읽지 않는다.
 *
 * <p>브라우저·네트워크·DB 를 쓰지 않는다. Selenium 인터페이스를 Mockito 로 세운다.
 *
 * @author KIUNSEA
 */
class ClickUtilFallbackTest {

  private WebDriver driver;
  private WebElement element;

  /** JS 로 실제 실행된 스크립트. scrollIntoView 와 click 을 구분해 폴백 발생 여부를 판정한다. */
  private final List<String> executedScripts = new ArrayList<>();

  /** null 이 아니면 JS <b>클릭</b>만 이 예외로 실패시킨다 (scrollIntoView 는 그대로 성공). */
  private RuntimeException jsClickFailure;

  @BeforeEach
  void setUp() {
    driver = mock(WebDriver.class, withSettings().extraInterfaces(JavascriptExecutor.class));
    element = mock(WebElement.class);

    // elementToBeClickable 이 보는 두 값. 둘 다 true 라야 대기가 즉시 통과해 click() 까지 간다.
    // (false 로 두면 대기가 5초를 다 쓰고 TimeoutException 으로 빠져 테스트가 느려진다.)
    when(element.isDisplayed()).thenReturn(true);
    when(element.isEnabled()).thenReturn(true);

    when(((JavascriptExecutor) driver).executeScript(anyString(), any(WebElement.class)))
        .thenAnswer(
            invocation -> {
              String script = invocation.getArgument(0);
              executedScripts.add(script);
              if (isClickScript(script) && jsClickFailure != null) {
                throw jsClickFailure;
              }
              return null;
            });
  }

  private static boolean isClickScript(String script) {
    return script.contains(".click(");
  }

  private boolean jsClickAttempted() {
    return executedScripts.stream().anyMatch(ClickUtilFallbackTest::isClickScript);
  }

  /** 2026-05-30 로그의 형태를 그대로 쓴다. 진단 단서가 이 문장이라 테스트도 그것으로 확인한다. */
  private static ElementClickInterceptedException interception() {
    return new ElementClickInterceptedException(
        "Element <button id=\"loginBtn\"> is not clickable at point (486, 390). "
            + "Other element would receive the click: <img src=\"(banner)\">");
  }

  @Test
  @DisplayName("일반 클릭이 성공하면 JS 폴백을 부르지 않는다")
  void normalClickSucceedsWithoutFallback() {
    ClickUtil.safeClick(driver, element, "#loginBtn");

    verify(element).click();
    assertFalse(jsClickAttempted(), "일반 클릭이 성공했는데 JS 폴백이 돌았다: " + executedScripts);
  }

  @Test
  @DisplayName("클릭이 가로채이면 JS 클릭으로 폴백해 예외 없이 끝난다")
  void interceptedClickFallsBackToJavascript() {
    doThrow(interception()).when(element).click();

    ClickUtil.safeClick(driver, element, "#loginBtn");

    assertTrue(jsClickAttempted(), "가로채였는데 JS 폴백이 돌지 않았다: " + executedScripts);
  }

  @Test
  @DisplayName("JS 폴백까지 실패하면 원래의 가로채기 예외가 suppressed 로 함께 나온다")
  void fallbackFailureCarriesOriginalInterception() {
    ElementClickInterceptedException original = interception();
    doThrow(original).when(element).click();
    jsClickFailure = new WebDriverException("javascript error: element is not attached");

    WebDriverException thrown =
        assertThrows(
            WebDriverException.class, () -> ClickUtil.safeClick(driver, element, "#loginBtn"));

    assertSame(jsClickFailure, thrown, "밖으로 나가는 것은 폴백 실패 예외여야 한다.");
    assertEquals(1, thrown.getSuppressed().length, "원예외가 suppressed 로 붙지 않았다.");
    assertSame(original, thrown.getSuppressed()[0], "원예외가 아닌 것이 붙었다.");
    assertTrue(
        thrown.getSuppressed()[0].getMessage().contains("Other element would receive the click"),
        "가로챈 요소를 지목하는 문장이 사라졌다 — 그 줄이 없으면 진단이 오버레이 탐색부터 다시 시작된다.");
  }

  @Test
  @DisplayName("폴백이 원예외와 같은 객체를 다시 던져도 IllegalArgumentException 으로 바뀌지 않는다")
  void sameExceptionInstanceIsNotSuppressedIntoItself() {
    ElementClickInterceptedException original = interception();
    doThrow(original).when(element).click();
    jsClickFailure = original;

    // addSuppressed 는 자기 자신을 받으면 IllegalArgumentException 을 던진다. 진단을 늘리려는
    // 코드가 원래 실패를 엉뚱한 예외로 바꿔치기하면 안 된다.
    WebDriverException thrown =
        assertThrows(
            WebDriverException.class, () -> ClickUtil.safeClick(driver, element, "#loginBtn"));

    assertSame(original, thrown, "원래 실패가 다른 예외로 바뀌었다: " + thrown.getClass().getName());
    assertEquals(0, thrown.getSuppressed().length);
  }

  @Test
  @DisplayName("가로채기 계열이 아닌 예외는 폴백 없이 그대로 나가고 suppressed 도 붙지 않는다")
  void nonInterceptionFailureIsNotRetriedByJavascript() {
    WebDriverException other = new WebDriverException("invalid session id");
    doThrow(other).when(element).click();

    WebDriverException thrown =
        assertThrows(
            WebDriverException.class, () -> ClickUtil.safeClick(driver, element, "#loginBtn"));

    assertSame(other, thrown);
    assertFalse(jsClickAttempted(), "가로채기가 아닌데 JS 폴백이 돌았다: " + executedScripts);
    assertEquals(0, thrown.getSuppressed().length, "폴백이 돌지도 않았는데 suppressed 가 붙었다.");
  }
}

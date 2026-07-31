package com.jiniebox.jangbogo.svc.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 예외 메시지 요약 검증.
 *
 * <p>실전 수집에서 Emart 타임아웃 한 건의 {@code getMessage()} 가 <b>1,293자</b>였고, 그 값이 그대로 {@code
 * jbg_collect_log.error_message} 와 {@code jbg_collect_breaker.last_reason} 에 저장됐다. 후자는 "사람이 읽을 사유"
 * 칸이다.
 *
 * @author KIUNSEA
 */
class ErrorSummaryTest {

  /** 실전에서 관측된 Selenium 예외 메시지의 형태. */
  private static final String SELENIUM_MESSAGE =
      "java.util.concurrent.TimeoutException\n"
          + "Build info: version: '4.31.0', revision: '1ef9f18787*'\n"
          + "System info: os.name: 'Windows 11', os.arch: 'amd64'\n"
          + "Driver info: org.openqa.selenium.chrome.ChromeDriver\n"
          + "Command: [SESSION_ID_PLACEHOLDER, get {url=https://eapp.emart.com/myemart/jornalV3.do}]\n"
          + "Capabilities {acceptInsecureCerts: false, browserName: chrome}\n"
          + "Session ID: SESSION_ID_PLACEHOLDER";

  @Test
  @DisplayName("Selenium 진단 블록을 잘라낸다")
  void stripsSeleniumBoilerplate() {
    String summary = ErrorSummary.summarize(SELENIUM_MESSAGE);

    assertEquals("java.util.concurrent.TimeoutException", summary);
    assertFalse(summary.contains("Build info"), summary);
    assertFalse(summary.contains("Capabilities"), summary);
    assertFalse(summary.contains("Session ID"), summary);
  }

  @Test
  @DisplayName("실전 사례가 1293자에서 크게 줄어든다")
  void shrinksTheRealCase() {
    // 전체 스택은 error_detail 이 따로 갖는다. 요약 칸까지 원문을 담을 이유가 없다.
    assertTrue(SELENIUM_MESSAGE.length() > 300, "테스트 입력이 충분히 길지 않다.");
    assertTrue(ErrorSummary.summarize(SELENIUM_MESSAGE).length() < 60);
  }

  @Test
  @DisplayName("긴 한 줄은 300자에서 자르고 말줄임표를 붙인다")
  void capsLongSingleLine() {
    String summary = ErrorSummary.summarize("x".repeat(1000));

    assertEquals(ErrorSummary.MAX_LENGTH + 1, summary.length(), "300자 + 말줄임표 1자여야 한다.");
    assertTrue(summary.endsWith("…"));
  }

  @Test
  @DisplayName("짧은 메시지는 그대로 둔다")
  void keepsShortMessage() {
    assertEquals("로그인 실패", ErrorSummary.summarize("로그인 실패"));
  }

  @Test
  @DisplayName("여러 줄은 한 줄로 접고 연속 공백을 줄인다")
  void flattensWhitespace() {
    assertEquals("첫 줄 둘째 줄", ErrorSummary.summarize("첫 줄\n   둘째    줄  "));
  }

  @Test
  @DisplayName("null 과 공백을 안전하게 다룬다")
  void handlesNullAndBlank() {
    assertNull(ErrorSummary.summarize((String) null));
    assertEquals("", ErrorSummary.summarize("   \n  "));
  }

  @Test
  @DisplayName("예외를 이름과 함께 요약한다")
  void summarizesThrowable() {
    assertEquals(
        "TimeoutException: java.util.concurrent.TimeoutException",
        ErrorSummary.summarize(new TimeoutException(SELENIUM_MESSAGE)));
  }

  @Test
  @DisplayName("메시지가 없는 예외는 이름만 남긴다")
  void summarizesThrowableWithoutMessage() {
    assertEquals("IllegalStateException", ErrorSummary.summarize(new IllegalStateException()));
  }

  @Test
  @DisplayName("예외가 null 이면 unknown")
  void summarizesNullThrowable() {
    assertEquals("unknown", ErrorSummary.summarize((Throwable) null));
  }

  @Test
  @DisplayName("CollectStep 이 만드는 메시지에도 진단 블록이 안 남는다")
  void collectStepMessageStaysShort() {
    // wrap 은 driver 없이도 동작한다 — 스크린샷·URL 은 null 이 되고 메시지는 만들어진다.
    var wrapped =
        CollectStep.wrap(
            null, "Emart", "navigateReceipt", null, new TimeoutException(SELENIUM_MESSAGE));

    assertFalse(wrapped.getMessage().contains("Build info"), wrapped.getMessage());
    assertTrue(wrapped.getMessage().length() < 200, "길이: " + wrapped.getMessage().length());
    assertTrue(wrapped.getMessage().contains("[step=navigateReceipt]"));
  }
}

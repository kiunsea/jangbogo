package com.jiniebox.jangbogo.svc.util;

import com.jiniebox.jangbogo.svc.CollectException;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;

/**
 * 쇼핑몰 크롤링 단계 실행을 감싸 예외 발생 시 컨텍스트(URL, 타이틀, 셀렉터, 스크린샷)를 자동 캡처해 {@link CollectException}으로 변환한다.
 *
 * <p>사용 예:
 *
 * <pre>{@code
 * CollectStep.run(driver, mallName, "signin", () -> { ... });
 *
 * WebElement btn = CollectStep.find(driver, mallName, "signin-btnLogin",
 *     By.id("loginBtn"));
 * }</pre>
 *
 * @author KIUNSEA
 */
public final class CollectStep {

  private CollectStep() {}

  /** 인자 없는 단계 실행 (반환값 없음). */
  public static void run(WebDriver driver, String mallName, String stepName, Runnable action) {
    runWithSelector(driver, mallName, stepName, null, action);
  }

  /** 셀렉터 컨텍스트가 있는 단계 실행 (반환값 없음). */
  public static void runWithSelector(
      WebDriver driver, String mallName, String stepName, String selector, Runnable action) {
    try {
      action.run();
    } catch (CollectException e) {
      // 이미 컨텍스트가 들어있는 예외는 그대로 전파
      throw e;
    } catch (Throwable t) {
      throw wrap(driver, mallName, stepName, selector, t);
    }
  }

  /** 반환값 있는 단계 실행. */
  public static <T> T call(
      WebDriver driver, String mallName, String stepName, java.util.function.Supplier<T> action) {
    return callWithSelector(driver, mallName, stepName, null, action);
  }

  /** 셀렉터 컨텍스트가 있는 반환값 있는 단계 실행. */
  public static <T> T callWithSelector(
      WebDriver driver,
      String mallName,
      String stepName,
      String selector,
      java.util.function.Supplier<T> action) {
    try {
      return action.get();
    } catch (CollectException e) {
      throw e;
    } catch (Throwable t) {
      throw wrap(driver, mallName, stepName, selector, t);
    }
  }

  /**
   * Selenium 예외를 받아 CollectException으로 래핑한다 (스크린샷 포함).
   *
   * <p><b>순서가 중요하다.</b> 대화상자(alert/confirm) 처리를 반드시 가장 먼저 한다. Chrome 의 W3C 기본값은 {@code
   * unhandledPromptBehavior = "dismiss and notify"} 이므로, 대화상자가 떠 있을 때 {@code getCurrentUrl()} 을 먼저
   * 부르면 드라이버가 <b>대화상자를 닫아 버린 뒤</b> 예외를 던진다. 그러면 아래 {@code safe()} 가 그 예외를 삼켜 URL 은 null 이 되고, 실패
   * 원인이 적혀 있던 <b>대화상자 문구는 어디에도 기록되지 않은 채 사라진다.</b> 먼저 문구를 확보하고 닫아 두면 URL·타이틀·스크린샷이 모두 정상적으로 수집된다.
   * (Phase 3-7)
   */
  public static CollectException wrap(
      WebDriver driver, String mallName, String stepName, String selector, Throwable cause) {

    String alertText = ScreenshotUtil.consumePendingAlert(driver);

    String url = safe(() -> driver != null ? driver.getCurrentUrl() : null);
    String title = safe(() -> driver != null ? driver.getTitle() : null);
    String inferredSelector = (selector != null) ? selector : inferSelector(cause);
    String screenshot = ScreenshotUtil.capture(driver, mallName);

    // 예외 메시지는 요약해서 담는다. Selenium 은 getMessage() 뒤에 Build/System/Driver info 와
    // Capabilities 블록을 통째로 붙이는데(실측 1,293자), 이 메시지는 jbg_collect_log.error_message 와
    // jbg_collect_breaker.last_reason 에 그대로 저장되고 화면에도 뿌려진다. 전체 스택은
    // error_detail 이 따로 갖고 있으므로 요약 칸까지 원문을 담을 이유가 없다.
    String causeMsg = ErrorSummary.summarize(cause);
    String message =
        "[step="
            + stepName
            + "] "
            + causeMsg
            + (inferredSelector != null ? " (target=" + inferredSelector + ")" : "")
            + (alertText != null ? " (alert=\"" + ErrorSummary.summarize(alertText) + "\")" : "");

    return new CollectException(stepName, url, title, inferredSelector, screenshot, message, cause);
  }

  /** Selenium NoSuchElementException / TimeoutException 메시지에서 selector 정보 추출 시도. */
  private static String inferSelector(Throwable t) {
    if (t == null) return null;
    String msg = t.getMessage();
    if (msg == null) return null;
    if (t instanceof NoSuchElementException || t instanceof TimeoutException) {
      // 메시지의 첫 줄 정도만 사용
      int nl = msg.indexOf('\n');
      String firstLine = nl > 0 ? msg.substring(0, nl).trim() : msg.trim();
      // 첫 줄만으로도 길 수 있다 (셀렉터가 긴 XPath 인 경우). 저장·표시 칸에 맞춘다.
      return ErrorSummary.summarize(firstLine);
    }
    return null;
  }

  private static String safe(java.util.function.Supplier<String> s) {
    try {
      return s.get();
    } catch (Throwable ignore) {
      return null;
    }
  }
}

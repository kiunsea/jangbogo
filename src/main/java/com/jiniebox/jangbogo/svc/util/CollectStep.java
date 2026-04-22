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

  /** Selenium 예외를 받아 CollectException으로 래핑한다 (스크린샷 포함). */
  public static CollectException wrap(
      WebDriver driver, String mallName, String stepName, String selector, Throwable cause) {

    String url = safe(() -> driver != null ? driver.getCurrentUrl() : null);
    String title = safe(() -> driver != null ? driver.getTitle() : null);
    String inferredSelector = (selector != null) ? selector : inferSelector(cause);
    String screenshot = ScreenshotUtil.capture(driver, mallName);

    String causeMsg =
        cause != null ? cause.getClass().getSimpleName() + ": " + cause.getMessage() : "unknown";
    String message =
        "[step="
            + stepName
            + "] "
            + causeMsg
            + (inferredSelector != null ? " (target=" + inferredSelector + ")" : "");

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
      return nl > 0 ? msg.substring(0, nl).trim() : msg.trim();
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

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

    String causeMsg =
        cause != null ? cause.getClass().getSimpleName() + ": " + cause.getMessage() : "unknown";
    String message =
        "[step="
            + stepName
            + "] "
            + causeMsg
            + (inferredSelector != null ? " (target=" + inferredSelector + ")" : "")
            + (alertText != null ? " (alert=\"" + oneLine(alertText) + "\")" : "");

    return new CollectException(stepName, url, title, inferredSelector, screenshot, message, cause);
  }

  /** 대화상자 문구는 여러 줄일 수 있다. 로그 한 줄·DB 한 칸에 들어가도록 접는다. */
  private static String oneLine(String text) {
    String flat = text.replaceAll("\\s*\\R\\s*", " ").trim();
    return flat.length() > 300 ? flat.substring(0, 300) + "…" : flat;
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

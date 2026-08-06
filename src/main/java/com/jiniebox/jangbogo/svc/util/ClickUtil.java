package com.jiniebox.jangbogo.svc.util;

import java.time.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementNotInteractableException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * 오버레이·레이지로딩에 강한 클릭 헬퍼.
 *
 * <p>배경: 2026-05-30 SSG/이마트 계정 연결이 2회 모두 실패했다. 원인은 봇 차단이 아니라 프로모션 배너 {@code <img>} 가 로그인 버튼을 덮어
 * {@code ElementClickInterceptedException} 이 발생한 것이었다.
 *
 * <pre>
 * Element &lt;button id="loginBtn" ...&gt; is not clickable at point (486, 390).
 * Other element would receive the click: &lt;img src="https://eapp-cdn.emart.com/banner/..."&gt;
 * </pre>
 *
 * <p>대응 순서는 (1) 요소를 화면 중앙으로 스크롤 → (2) 클릭 가능 상태 대기 후 일반 클릭 → (3) 그래도 가로채이면 {@code
 * JavascriptExecutor} 클릭으로 폴백. JS 클릭은 좌표를 거치지 않고 요소에 직접 이벤트를 전달하므로 위를 덮은 배너의 영향을 받지 않는다.
 *
 * <p><b>폴백까지 실패하면 원예외를 suppressed 로 매달아 던진다.</b> 매달지 않으면 밖으로 나가는 것이 JS 실행 예외 하나뿐이라 "무엇이 클릭을 가로챘는가"
 * 가 사라진다. 위 메시지의 {@code Other element would receive the click: <img src=...>} 한 줄이 2026-05-30 원인
 * 규명의 유일한 단서였고, 그 줄이 없었으면 봇 차단으로 오진했을 것이다. 폴백 경고 로그는 예외 <b>클래스명</b>만 남기므로 대체가 되지 않는다 — 원인이 사라진 채
 * "JS 클릭도 실패" 만 남으면 진단이 처음부터 다시 시작된다.
 *
 * @author KIUNSEA
 */
public final class ClickUtil {

  private static final Logger log = LogManager.getLogger(ClickUtil.class);

  /** 클릭 가능 상태를 기다리는 최대 시간. */
  private static final Duration CLICKABLE_TIMEOUT = Duration.ofSeconds(5);

  private ClickUtil() {}

  /**
   * 셀렉터로 요소를 찾아 오버레이에 강한 방식으로 클릭한다.
   *
   * @param driver WebDriver 인스턴스
   * @param locator 클릭 대상 셀렉터
   */
  public static void safeClick(WebDriver driver, By locator) {
    safeClick(driver, driver.findElement(locator), locator.toString());
  }

  /**
   * 이미 찾아둔 요소를 오버레이에 강한 방식으로 클릭한다.
   *
   * @param driver WebDriver 인스턴스
   * @param element 클릭 대상 요소
   */
  public static void safeClick(WebDriver driver, WebElement element) {
    safeClick(driver, element, null);
  }

  /**
   * 이미 찾아둔 요소를 오버레이에 강한 방식으로 클릭한다.
   *
   * @param driver WebDriver 인스턴스
   * @param element 클릭 대상 요소
   * @param description 로그에 남길 대상 설명 (셀렉터 등, null 허용)
   */
  public static void safeClick(WebDriver driver, WebElement element, String description) {
    String target = (description != null) ? description : describe(element);

    scrollIntoView(driver, element);

    RuntimeException interception;
    try {
      new WebDriverWait(driver, CLICKABLE_TIMEOUT)
          .until(ExpectedConditions.elementToBeClickable(element))
          .click();
      return;
    } catch (ElementNotInteractableException | TimeoutException e) {
      // ElementClickInterceptedException 은 ElementNotInteractableException 의 하위 타입이라 함께 잡힌다.
      // 오버레이(배너·팝업·플로팅 레이어)가 덮고 있거나 아직 상호작용 불가한 상태다. JS 클릭으로 폴백한다.
      log.warn("일반 클릭 실패 → JS 클릭 폴백 (target={}): {}", target, e.getClass().getSimpleName());
      interception = e;
    }

    try {
      ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
    } catch (RuntimeException fallbackFailure) {
      // 왜 매다는가: 여기서 그냥 던지면 밖으로 나가는 것은 JS 실행 예외 하나뿐이고, 원인이 적힌
      // ElementClickInterceptedException 은 그대로 사라진다. 남는 것은 위 warn 한 줄인데 그건
      // 클래스명만 담는다 — "무엇이 클릭을 받았는가"(가로챈 요소의 태그·src)가 없으면 진단이
      // 오버레이 탐색부터 다시 시작되고, 2026-05-30 에는 그 한 줄이 봇 차단 오진을 막은 단서였다.
      //
      // 같은 객체면 매달지 않는다: addSuppressed 는 자기 자신을 받으면 IllegalArgumentException 을
      // 던진다. 그러면 진단을 늘리려는 이 줄이 원래 실패를 엉뚱한 예외로 바꿔치기하게 된다.
      if (fallbackFailure != interception) {
        fallbackFailure.addSuppressed(interception);
      }
      throw fallbackFailure;
    }
  }

  /** 요소를 화면 중앙으로 스크롤한다. 실패해도 클릭 시도는 계속한다. */
  private static void scrollIntoView(WebDriver driver, WebElement element) {
    try {
      ((JavascriptExecutor) driver)
          .executeScript(
              "arguments[0].scrollIntoView({block:'center', inline:'center'});", element);
    } catch (Exception ignore) {
      log.debug("scrollIntoView 실패(무시): {}", ignore.getMessage());
    }
  }

  /** 로그용 요소 설명을 만든다. 조회 자체가 실패하면 태그명 대신 물음표를 남긴다. */
  private static String describe(WebElement element) {
    try {
      String id = element.getAttribute("id");
      return element.getTagName() + ((id != null && !id.isEmpty()) ? "#" + id : "");
    } catch (Exception ignore) {
      return "?";
    }
  }
}

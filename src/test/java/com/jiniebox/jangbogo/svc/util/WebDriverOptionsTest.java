package com.jiniebox.jangbogo.svc.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;

/**
 * ChromeDriver 기동 옵션 구성 검증 (Phase 3-11).
 *
 * <p>브라우저를 띄우지 않는다 — {@link WebDriverManager#buildChromeOptions(boolean)} 이 만든 {@link
 * ChromeOptions} 객체만 들여다본다.
 *
 * <p>headless 기본값이 특히 중요하다. 과거 이 조건이 반대로 되어 있어 로그인 화면을 눈으로 확인할 수 없었고, 그것이 수집 장애 진단을 오래 막았다(Phase
 * 1). 설정이 없으면 headless 를 켜지 않는다는 규칙을 여기서 고정한다.
 *
 * @author KIUNSEA
 */
class WebDriverOptionsTest {

  private final WebDriverManager manager = new WebDriverManager();

  @SuppressWarnings("unchecked")
  private static List<String> argsOf(ChromeOptions options) {
    Map<String, Object> chromeOptions =
        (Map<String, Object>) options.asMap().get(ChromeOptions.CAPABILITY);
    Object args = chromeOptions.get("args");
    return args == null ? List.of() : (List<String>) args;
  }

  @Test
  @DisplayName("headless=false 면 --headless 인자를 넣지 않는다")
  void doesNotGoHeadlessByDefault() {
    List<String> args = argsOf(manager.buildChromeOptions(false));

    assertFalse(
        args.stream().anyMatch(a -> a.contains("headless")),
        "설정이 없을 때 headless 로 뜨면 로그인 화면을 눈으로 확인할 수 없다. 실제 인자: " + args);
  }

  @Test
  @DisplayName("headless=true 면 --headless=new 를 넣는다")
  void goesHeadlessWhenRequested() {
    assertTrue(argsOf(manager.buildChromeOptions(true)).contains("--headless=new"));
  }

  @Test
  @DisplayName("headless 여부와 무관하게 공통 인자는 항상 들어간다")
  void alwaysCarriesTheCommonArguments() {
    for (boolean headless : new boolean[] {false, true}) {
      List<String> args = argsOf(manager.buildChromeOptions(headless));

      assertTrue(
          args.contains("--remote-allow-origins=*"), "headless=" + headless + " 인자: " + args);
      assertTrue(
          args.stream().anyMatch(a -> a.startsWith("user-agent=")),
          "user-agent 가 빠졌다. headless=" + headless + " 인자: " + args);
    }
  }

  @Test
  @DisplayName("브라우저 이름은 chrome 이다")
  void targetsChrome() {
    assertEquals("chrome", manager.buildChromeOptions(false).getBrowserName());
  }

  @Test
  @DisplayName("페이지 로드 타임아웃을 명시한다 — Selenium 기본값 300초를 쓰지 않는다")
  void pinsPageLoadTimeout() {
    // 실측: Emart 트레이더스 페이지가 응답하지 않아 수집 한 회차가 약 6분 붙잡혔다.
    // capabilities 에 timeouts={pageLoad:300000} 으로 찍혀 있었다 — 설정한 적이 없어서다.
    @SuppressWarnings("unchecked")
    Map<String, Object> timeouts =
        (Map<String, Object>) manager.buildChromeOptions(false).asMap().get("timeouts");

    assertNotNull(timeouts, "timeouts capability 가 없다 — 기본값 300초가 그대로 적용된다.");
    long pageLoadMs = ((Number) timeouts.get("pageLoad")).longValue();

    assertEquals(
        WebDriverManager.DEFAULT_PAGE_LOAD_TIMEOUT_SECONDS * 1000L,
        pageLoadMs,
        "기본 페이지 로드 타임아웃이 60초가 아니다.");
    assertTrue(pageLoadMs < 300_000L, "Selenium 기본값(300초)보다 짧아야 한다.");
  }

  @Test
  @DisplayName("타임아웃은 시스템 프로퍼티로 올릴 수 있다")
  void pageLoadTimeoutIsOverridable() {
    String previous = System.getProperty(WebDriverManager.PAGE_LOAD_TIMEOUT_PROPERTY);
    try {
      // 느린 회선을 만나면 늘릴 수 있어야 한다. 안전장치라 화면에는 노출하지 않는다.
      System.setProperty(WebDriverManager.PAGE_LOAD_TIMEOUT_PROPERTY, "120");
      assertEquals(120, WebDriverManager.pageLoadTimeoutSeconds());

      System.setProperty(WebDriverManager.PAGE_LOAD_TIMEOUT_PROPERTY, "0");
      assertEquals(
          WebDriverManager.DEFAULT_PAGE_LOAD_TIMEOUT_SECONDS,
          WebDriverManager.pageLoadTimeoutSeconds(),
          "0 이하는 무시하고 기본값을 쓴다.");

      System.setProperty(WebDriverManager.PAGE_LOAD_TIMEOUT_PROPERTY, "이상한값");
      assertEquals(
          WebDriverManager.DEFAULT_PAGE_LOAD_TIMEOUT_SECONDS,
          WebDriverManager.pageLoadTimeoutSeconds());
    } finally {
      if (previous == null) {
        System.clearProperty(WebDriverManager.PAGE_LOAD_TIMEOUT_PROPERTY);
      } else {
        System.setProperty(WebDriverManager.PAGE_LOAD_TIMEOUT_PROPERTY, previous);
      }
    }
  }

  @Test
  @DisplayName("드라이버 경로는 Selenium 표준 프로퍼티로 지정한다")
  void driverPathIsGivenByTheStandardSeleniumProperty() {
    // Phase 3-11 에서 지운 CHROME_DRIVER_PATH 필드가 없어도 잃는 것이 없다는 근거.
    // Selenium 이 이 프로퍼티를 직접 읽고, 값이 있으면 Selenium Manager 자동 다운로드를 건너뛴다.
    // 폐쇄망에서는 -Dwebdriver.chrome.driver=... 로 기동하면 된다.
    assertEquals("webdriver.chrome.driver", ChromeDriverService.CHROME_DRIVER_EXE_PROPERTY);
  }
}

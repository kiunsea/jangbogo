package com.jiniebox.jangbogo.svc.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
  @DisplayName("드라이버 경로는 Selenium 표준 프로퍼티로 지정한다")
  void driverPathIsGivenByTheStandardSeleniumProperty() {
    // Phase 3-11 에서 지운 CHROME_DRIVER_PATH 필드가 없어도 잃는 것이 없다는 근거.
    // Selenium 이 이 프로퍼티를 직접 읽고, 값이 있으면 Selenium Manager 자동 다운로드를 건너뛴다.
    // 폐쇄망에서는 -Dwebdriver.chrome.driver=... 로 기동하면 된다.
    assertEquals("webdriver.chrome.driver", ChromeDriverService.CHROME_DRIVER_EXE_PROPERTY);
  }
}

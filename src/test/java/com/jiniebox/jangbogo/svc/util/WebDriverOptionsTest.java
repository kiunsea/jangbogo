package com.jiniebox.jangbogo.svc.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
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
  @DisplayName("Edge 도 페이지 로드 타임아웃을 명시한다 — Chrome 만 낮추고 Edge 를 300초로 두지 않는다")
  void pinsPageLoadTimeoutForEdgeToo() {
    // Chrome 분기만 60초로 낮추면 Edge 분기는 Selenium 기본값 300초가 그대로 남는다.
    // 두 분기의 타임아웃은 같은 상수·같은 재정의 프로퍼티를 공유해야 한다.
    @SuppressWarnings("unchecked")
    Map<String, Object> timeouts =
        (Map<String, Object>) manager.buildEdgeOptions().asMap().get("timeouts");

    assertNotNull(timeouts, "Edge 에 timeouts capability 가 없다 — 기본값 300초가 그대로 적용된다.");
    long pageLoadMs = ((Number) timeouts.get("pageLoad")).longValue();

    assertEquals(
        WebDriverManager.DEFAULT_PAGE_LOAD_TIMEOUT_SECONDS * 1000L,
        pageLoadMs,
        "Edge 의 기본 페이지 로드 타임아웃이 Chrome 과 다르다.");
  }

  @Test
  @DisplayName("Edge 타임아웃도 같은 시스템 프로퍼티를 따른다")
  void edgePageLoadTimeoutFollowsTheSameProperty() {
    String previous = System.getProperty(WebDriverManager.PAGE_LOAD_TIMEOUT_PROPERTY);
    try {
      System.setProperty(WebDriverManager.PAGE_LOAD_TIMEOUT_PROPERTY, "90");

      @SuppressWarnings("unchecked")
      Map<String, Object> timeouts =
          (Map<String, Object>) manager.buildEdgeOptions().asMap().get("timeouts");

      assertEquals(
          90_000L,
          ((Number) timeouts.get("pageLoad")).longValue(),
          "Chrome 용 재정의 프로퍼티가 Edge 에는 적용되지 않는다 — 두 분기가 갈라졌다.");
    } finally {
      if (previous == null) {
        System.clearProperty(WebDriverManager.PAGE_LOAD_TIMEOUT_PROPERTY);
      } else {
        System.setProperty(WebDriverManager.PAGE_LOAD_TIMEOUT_PROPERTY, previous);
      }
    }
  }

  @Test
  @DisplayName("Edge 옵션의 브라우저 이름은 MicrosoftEdge 다")
  void edgeOptionsTargetEdge() {
    assertEquals("MicrosoftEdge", manager.buildEdgeOptions().getBrowserName());
  }

  // ---------------------------------------------------------------------
  // Phase 5-7 · 경로 A 배선
  //
  // 이 묶음은 순수 함수 검증이 아니다. 우리 코드가 만든 값이 Selenium 의 옵션 객체까지
  // 실제로 건너갔는지를 직렬화 결과(asMap)로 확인한다. 5차 세션에서 게이트 판정을 완벽히
  // 테스트하고도 DAO 의 SELECT 목록에 컬럼이 빠져 게이트가 통째로 죽어 있던 일이 있었다 —
  // 끊긴 배선은 순수 함수 테스트로는 잡히지 않는다.
  // ---------------------------------------------------------------------

  private static final Path PROFILE = Paths.get("build", "test-profiles", "ssg");

  @SuppressWarnings("unchecked")
  private static Map<String, Object> chromeOptionsOf(ChromeOptions options) {
    return (Map<String, Object>) options.asMap().get(ChromeOptions.CAPABILITY);
  }

  @Test
  @DisplayName("프로필을 주면 --user-data-dir 이 절대경로로, -- 접두사를 달고 실제로 전달된다")
  void carriesUserDataDirWithTheRequiredPrefix() {
    List<String> args = argsOf(manager.buildChromeOptions(false, PROFILE));

    String expected = "--user-data-dir=" + PROFILE.toAbsolutePath();
    assertTrue(
        args.contains(expected),
        "프로필 경로가 옵션까지 건너가지 않았다. 이 인자가 빠지면 매번 새 임시 프로필이 열려"
            + " '로그인 안 된 상태로 성공' 이 된다. 실제 인자: "
            + args);

    // 접두사가 빠진 형태로 새어 들어가면 chromedriver 149+ 가 조용히 무시한다.
    assertFalse(
        args.stream().anyMatch(a -> a.startsWith("user-data-dir=")),
        "-- 접두사 없는 user-data-dir 이 섞여 있다. 실제 인자: " + args);
  }

  @Test
  @DisplayName("상대 경로를 줘도 절대경로로 바꿔 넘긴다")
  void absolutizesRelativeProfilePaths() {
    // Chrome 은 상대 경로를 우리 작업 디렉터리가 아니라 자기 기준으로 푼다.
    // 그대로 넘기면 엉뚱한 위치에 빈 프로필이 생긴다.
    List<String> args = argsOf(manager.buildChromeOptions(false, Paths.get("relative", "profile")));

    assertTrue(
        args.stream()
            .anyMatch(
                a ->
                    a.startsWith("--user-data-dir=")
                        && Paths.get(a.substring("--user-data-dir=".length())).isAbsolute()),
        "상대 경로가 그대로 넘어갔다. 실제 인자: " + args);
  }

  @Test
  @DisplayName("프로필을 쓰면 하위 프로필을 Default 로 명시한다")
  void pinsTheProfileDirectory() {
    // 생략하면 Chrome 이 마지막에 쓰던 프로필을 고를 수 있다 —
    // 사람이 로그인해 둔 프로필과 다른 곳이 열릴 수 있다.
    assertTrue(
        argsOf(manager.buildChromeOptions(false, PROFILE))
            .contains("--profile-directory=" + WebDriverManager.PROFILE_DIRECTORY));
  }

  @Test
  @DisplayName("프로필을 쓰면 자동화 표식 스위치가 excludeSwitches 로 전달된다")
  void carriesExcludeSwitches() {
    Object excluded =
        chromeOptionsOf(manager.buildChromeOptions(false, PROFILE)).get("excludeSwitches");

    assertNotNull(excluded, "excludeSwitches 가 goog:chromeOptions 까지 건너가지 않았다.");
    assertTrue(
        ((Collection<?>) excluded).containsAll(WebDriverManager.EXCLUDED_SWITCHES),
        "실제 값: " + excluded);
  }

  @Test
  @DisplayName("프로필을 쓰면 headless 설정을 무시하고 화면을 띄운다")
  void profileForcesHeadedMode() {
    // 프로필 재사용은 headless 로 성립하지 않는다. 설정이 켜져 있어도 우리가 이긴다.
    List<String> args = argsOf(manager.buildChromeOptions(true, PROFILE));

    assertFalse(
        args.stream().anyMatch(a -> a.contains("headless")),
        "프로필을 쓰는데 headless 로 떴다. 실제 인자: " + args);
  }

  @Test
  @DisplayName("Chrome 실행 파일은 프로퍼티가 있을 때만 핀 고정한다")
  void pinsChromeBinaryOnlyWhenAsked() {
    String previous = System.getProperty(WebDriverManager.CHROME_BINARY_PROPERTY);
    try {
      System.clearProperty(WebDriverManager.CHROME_BINARY_PROPERTY);
      assertNull(
          chromeOptionsOf(manager.buildChromeOptions(false, PROFILE)).get("binary"),
          "지정하지 않았는데 바이너리가 박혔다 — 설치된 Chrome 을 못 찾게 될 수 있다.");

      System.setProperty(WebDriverManager.CHROME_BINARY_PROPERTY, "C:\\chrome\\chrome.exe");
      assertEquals(
          "C:\\chrome\\chrome.exe",
          chromeOptionsOf(manager.buildChromeOptions(false, PROFILE)).get("binary"));
    } finally {
      if (previous == null) {
        System.clearProperty(WebDriverManager.CHROME_BINARY_PROPERTY);
      } else {
        System.setProperty(WebDriverManager.CHROME_BINARY_PROPERTY, previous);
      }
    }
  }

  @Test
  @DisplayName("프로필이 없으면 조립 결과가 기존과 완전히 같다 — 옵트인 OFF 몰의 동작 불변")
  void withoutProfileTheOptionsAreUnchanged() {
    // Phase 5 수용 기준 10. 옵트인하지 않은 몰은 Phase 3 종료 시점과 실행 결과가 같아야 한다.
    // 프로필 전용 옵션이 하나라도 새어 나가면 여기서 걸린다.
    String previous = System.getProperty(WebDriverManager.CHROME_BINARY_PROPERTY);
    try {
      System.setProperty(WebDriverManager.CHROME_BINARY_PROPERTY, "C:\\chrome\\chrome.exe");

      for (boolean headless : new boolean[] {false, true}) {
        assertEquals(
            manager.buildChromeOptions(headless).asMap(),
            manager.buildChromeOptions(headless, null).asMap(),
            "프로필 없는 경로의 옵션이 달라졌다. headless=" + headless);

        Map<String, Object> chromeOptions =
            chromeOptionsOf(manager.buildChromeOptions(headless, null));
        assertNull(chromeOptions.get("excludeSwitches"), "프로필이 없는데 excludeSwitches 가 붙었다.");
        assertNull(chromeOptions.get("binary"), "프로필이 없는데 바이너리가 핀 고정됐다.");
        assertFalse(
            argsOf(manager.buildChromeOptions(headless, null)).stream()
                .anyMatch(a -> a.contains("user-data-dir") || a.contains("profile-directory")),
            "프로필이 없는데 프로필 인자가 붙었다.");
      }
    } finally {
      if (previous == null) {
        System.clearProperty(WebDriverManager.CHROME_BINARY_PROPERTY);
      } else {
        System.setProperty(WebDriverManager.CHROME_BINARY_PROPERTY, previous);
      }
    }
  }

  @Test
  @DisplayName("마스킹 스크립트는 navigator.webdriver 를 지우는 게 아니라 false 로 되돌린다")
  void stealthScriptRestoresWebdriverToFalse() {
    // excludeSwitches 로 배너와 스위치는 지워지지만 navigator.webdriver 는 남는다.
    //
    // 흔한 레시피대로 undefined 를 넣으면 순정 Chrome(=false)과 다른 상태가 되어 오히려 눈에 띈다.
    // T2 실측으로 확인한 값이다 — 순정 "webdriver":"false" / 이 스크립트 적용 후에도 "false".
    assertTrue(WebDriverManager.STEALTH_SCRIPT.contains("webdriver"));
    assertTrue(
        WebDriverManager.STEALTH_SCRIPT.contains("false"),
        "순정 Chrome 은 false 다. 실제: " + WebDriverManager.STEALTH_SCRIPT);
    assertFalse(
        WebDriverManager.STEALTH_SCRIPT.contains("undefined"),
        "undefined 로 지우면 순정과 달라진다 — 과잉 마스킹이다.");
    assertTrue(
        WebDriverManager.STEALTH_SCRIPT.contains("Navigator.prototype"),
        "순정 Chrome 에서 이 속성은 프로토타입에 있다. 인스턴스에 얹으면 구분된다.");
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

package com.jiniebox.jangbogo.svc.util;

import com.jiniebox.jangbogo.dto.JangbogoConfig;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.chromium.ChromiumDriver;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;

public class WebDriverManager {

  private Logger log = LogManager.getLogger(WebDriverManager.class);

  public static String BROWSER_NAME_CHROME = "chrome";
  public static String BROWSER_NAME_EDGE = "edge";

  /** 페이지 로드 타임아웃 기본값(초). Selenium 기본값 300초는 너무 길다 — B-2 참조. */
  public static final int DEFAULT_PAGE_LOAD_TIMEOUT_SECONDS = 60;

  /** 페이지 로드 타임아웃 재정의 시스템 프로퍼티. */
  public static final String PAGE_LOAD_TIMEOUT_PROPERTY = "jangbogo.browser.page-load-timeout-sec";

  /**
   * Chrome 실행 파일 핀 고정 (Phase 5-7).
   *
   * <p>프로필을 만든 Chrome 과 그 프로필을 여는 Chrome 이 다르면 세션이 살아나지 않을 수 있다. 값이 없으면 지정하지 않는다 — 지금까지의 동작 그대로다.
   */
  public static final String CHROME_BINARY_PROPERTY = "jangbogo.browser.chrome-binary";

  /** 프로필 디렉터리 안에서 쓸 하위 프로필 이름. Chrome 의 기본값과 같다. */
  static final String PROFILE_DIRECTORY = "Default";

  /**
   * 자동화 표식을 지우는 스위치 (Phase 5-7).
   *
   * <p>{@code enable-automation} 은 "Chrome이 자동화된 테스트 소프트웨어에 의해 제어되고 있습니다" 배너와 {@code
   * navigator.webdriver=true} 를 만든다. {@code test-type} 은 명령줄에 {@code test-type=webdriver} 로 남는다. 둘
   * 다 사람이 만든 프로필을 여는 경로에서만 지운다.
   */
  static final List<String> EXCLUDED_SWITCHES = List.of("enable-automation", "test-type");

  /**
   * 새 문서마다 주입하는 마스킹 스크립트 (Phase 5-7).
   *
   * <p>타입이 붙은 DevTools 바인딩({@code selenium-devtools-vNNN})은 Chrome 150 용 아티팩트가 없어 쓸 수 없다. {@code
   * ChromiumDriver.executeCdpCommand} 는 버전 매칭이 필요 없어 그대로 동작한다 — 실측으로 확인했다.
   *
   * <p><b>{@code undefined} 가 아니라 {@code false} 로 되돌린다.</b> 흔히 쓰이는 레시피는 {@code undefined} 를 넣지만,
   * 그것은 {@code navigator.webdriver} 가 자동화일 때만 존재하던 옛 Chrome 기준이다. 지금 Chrome 은 <b>평소에도 이 값이 있고
   * {@code false}</b> 다 — T2 로 실측했다. {@code undefined} 로 지우면 순정 브라우저와 <b>다른</b> 상태가 되어 오히려 눈에 띈다.
   *
   * <p>인스턴스가 아니라 프로토타입에 정의하는 이유도 같다. 순정 Chrome 에서 이 속성은 {@code Navigator.prototype} 에 있고 navigator
   * 자신의 속성이 아니다. 인스턴스에 얹으면 {@code getOwnPropertyDescriptor} 로 구분된다.
   */
  static final String STEALTH_SCRIPT =
      "Object.defineProperty(Navigator.prototype, 'webdriver',"
          + " {get: () => false, configurable: true});";

  // 여기 있던 CHROME_DRIVER_ID / CHROME_DRIVER_PATH / CHROME_BINARY_PATH /
  // EDGE_DRIVER_ID / EDGE_DRIVER_PATH 5개 필드는 제거했다 (Phase 3-11).
  // 선언만 되어 있고 대입도 참조도 한 번도 없었으며, 상속 클래스도 설정 키도 없었다.
  //
  // 드라이버 경로는 필드가 없어도 이미 지정할 수 있다 — Selenium 이 표준 시스템 프로퍼티
  // webdriver.chrome.driver(ChromeDriverService.CHROME_DRIVER_EXE_PROPERTY)를 직접 읽고,
  // 그 값이 있으면 Selenium Manager 의 자동 다운로드를 건너뛴다. 폐쇄망에서는
  //     java -Dwebdriver.chrome.driver=D:\drivers\chromedriver.exe -jar jangbogo-x.y.z.jar
  // 로 기동하면 된다. 죽은 필드는 "여기에 경로를 넣는 통로가 있다"는 인상만 주고 있었다.
  //
  // 브라우저 실행 파일(binary)은 사정이 다르다. Selenium 4 에 대응하는 표준 프로퍼티가 없고
  // ChromeOptions.setBinary() 로만 지정할 수 있다. 이 배선은 Phase 5(프로필 재사용)의
  // 바이너리 핀 고정과 같은 문제라 그쪽에서 함께 설계한다.

  /**
   * 설정 인스턴스를 반환한다.
   *
   * <p>이 클래스는 크롤러에서 {@code new WebDriverManager()} 로 생성되는 비-빈이므로 {@code @Autowired} 가 동작하지 않는다(주입되지
   * 않은 채 {@code new JangbogoConfig()} 가 남아 {@code @PostConstruct} 미실행 상태의 빈 설정을 보게 된다). Spring 이
   * 관리하는 인스턴스를 직접 조회한다.
   */
  private JangbogoConfig config() {
    return JangbogoConfig.getInstance();
  }

  /**
   * 웹서비스에 접속하기 위한 웹드라이브를 반환한다. properties에 설정한 값이 없다면 default는 chrome이다.
   *
   * @return
   */
  public synchronized WebDriver getWebDriver() {
    String defWebDrv = config().get("DEFAULT_WEB_DRIVER");
    return getWebDriver(defWebDrv != null ? defWebDrv : "chrome");
  }

  /**
   * 웹서비스에 접속하기 위한 웹드라이브를 반환한다.
   *
   * @param browserName ( "chrome" or "edge")
   * @return
   */
  public synchronized WebDriver getWebDriver(String browserName) {
    return getWebDriver(browserName, null);
  }

  /**
   * 세션 프로필로 웹드라이버를 기동한다 (Phase 5-7 · 경로 A).
   *
   * <p>{@code profileDir} 이 {@code null} 이면 <b>기존 경로 그대로</b>다 — 옵트인하지 않은 몰의 동작이 바뀌지 않는다는 보장이 여기서
   * 나온다. 프로필 전용 옵션(자동화 표식 제거·바이너리 핀·headless 강제 off)도 프로필이 있을 때만 붙인다.
   *
   * <p>Edge 는 프로필 경로를 받지 않는다. 경로 A 는 Chrome 으로만 판정했고, Edge 분기는 설정으로만 닿는 예비 경로다.
   *
   * @param browserName {@code "chrome"} 또는 {@code "edge"}
   * @param profileDir 사람이 로그인해 둔 프로필 디렉터리. 없으면 null
   * @return 웹드라이버
   */
  public synchronized WebDriver getWebDriver(String browserName, Path profileDir) {
    WebDriver driver = null;

    if (this.BROWSER_NAME_CHROME.equals(browserName)) {
      // BROWSER_HEADLESS 가 true 일 때만 headless 로 띄운다.
      // (설정이 없거나 파싱 불가면 headless 를 켜지 않는다 — 로그인 화면을 눈으로 확인할 수 있어야 한다)
      boolean headless = Boolean.parseBoolean(config().get("BROWSER_HEADLESS"));
      ChromeOptions options = buildChromeOptions(headless, profileDir);
      log.info(
          "ChromeDriver 기동 (headless={}, profile={})",
          headless && profileDir == null,
          profileDir == null ? "없음" : profileDir);
      driver = new ChromeDriver(options);
      if (profileDir != null) {
        applyStealth(driver);
      }
    } else if (this.BROWSER_NAME_EDGE.equals(browserName)) {
      log.info("EdgeDriver 기동");
      driver = new EdgeDriver(buildEdgeOptions());
    }

    return driver;
  }

  /**
   * 새 문서마다 마스킹 스크립트를 주입한다 (Phase 5-7).
   *
   * <p>{@code excludeSwitches} 로 배너와 스위치는 지워지지만 {@code navigator.webdriver} 는 그대로 남는다. 페이지가 로드될 때마다
   * 문서 스크립트보다 먼저 돌아야 하므로 {@code Page.addScriptToEvaluateOnNewDocument} 를 쓴다.
   *
   * <p>실패해도 수집을 멈추지 않는다 — 마스킹은 있으면 나은 것이지 없으면 못 도는 것이 아니다. 다만 조용히 넘기지는 않는다.
   *
   * @param driver 기동된 드라이버
   */
  void applyStealth(WebDriver driver) {
    if (!(driver instanceof ChromiumDriver chromium)) {
      log.warn("CDP 를 지원하지 않는 드라이버라 마스킹을 건너뛴다: {}", driver.getClass().getSimpleName());
      return;
    }
    try {
      chromium.executeCdpCommand(
          "Page.addScriptToEvaluateOnNewDocument", Map.of("source", STEALTH_SCRIPT));
    } catch (RuntimeException e) {
      log.warn("마스킹 스크립트 주입 실패({}). 경고만 남기고 진행한다.", e.getClass().getSimpleName());
    }
  }

  /**
   * ChromeDriver 에 넘길 옵션을 조립한다.
   *
   * <p>브라우저를 띄우지 않고 옵션 구성만 검증할 수 있도록 분리했다(순수 코드 이동). {@code headless} 를 설정에서 읽지 않고 인자로 받는 이유도 같다 —
   * 테스트가 설정 싱글턴에 의존하지 않는다.
   *
   * @param headless true 면 화면 출력 없이 실행한다
   * @return 조립된 ChromeOptions
   */
  ChromeOptions buildChromeOptions(boolean headless) {
    return buildChromeOptions(headless, null);
  }

  /**
   * 세션 프로필까지 반영해 ChromeOptions 를 조립한다 (Phase 5-7).
   *
   * <p>{@code profileDir} 이 {@code null} 이면 위 {@link #buildChromeOptions(boolean)} 과 <b>완전히 같은
   * 결과</b>다. 프로필 전용 옵션은 하나도 붙지 않는다 — 옵트인 OFF 몰의 런타임 동작이 그대로 유지되어야 하기 때문이다.
   *
   * @param headless true 면 화면 출력 없이 실행한다. 프로필을 쓸 때는 무시된다
   * @param profileDir 프로필 디렉터리. 없으면 null
   * @return 조립된 ChromeOptions
   */
  ChromeOptions buildChromeOptions(boolean headless, Path profileDir) {
    ChromeOptions options = new ChromeOptions();
    options.addArguments("--remote-allow-origins=*");
    options.addArguments(
        "user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.91 Safari/537.3");
    if (headless && profileDir == null) {
      options.addArguments("--headless=new"); // 크롬 브라우저를 화면에 출력하지 않고 실행한다
    } else if (headless) {
      // 프로필 재사용은 headless 로 성립하지 않는다 — 세션 만료를 눈으로 확인할 수 없고,
      // 로그인 화면에 멈춘 채 타임아웃되면 로그만으로는 원인을 가릴 수 없다. 설정보다 우선한다.
      log.info("세션 프로필을 쓰므로 BROWSER_HEADLESS=true 를 무시하고 화면을 띄운다.");
    }

    if (profileDir != null) {
      applyProfileOptions(options, profileDir);
    }

    // 페이지 로드 타임아웃을 명시한다 (B-2).
    //
    // 설정한 적이 없어 Selenium 기본값 300초가 그대로 적용되고 있었다. 실측 사례:
    // Emart 트레이더스 영수증 페이지가 응답하지 않자 수집 한 회차가 약 6분(준비 45초 + 300초)을
    // 통째로 붙잡혔다. capabilities 덤프에 timeouts={implicit:0, pageLoad:300000, script:30000} 로
    // 찍혀 있었다.
    //
    // 60초면 정상 페이지에 넉넉하고, 죽은 페이지는 6분이 아니라 1분 안에 드러난다. 느린 회선을
    // 만나면 시스템 프로퍼티로 올릴 수 있다.
    options.setPageLoadTimeout(Duration.ofSeconds(pageLoadTimeoutSeconds()));

    return options;
  }

  /**
   * 프로필 전용 옵션을 붙인다 (Phase 5-7 · 경로 A).
   *
   * <p><b>{@code --} 접두사가 필수다.</b> 구 chromedriver 는 접두사 없는 {@code user-data-dir=...} 을 보정해 줬지만 149
   * 계열부터는 그러지 않는다. 접두사가 빠지면 인자가 조용히 무시되고 <b>매번 새 임시 프로필이 열려</b> '로그인 안 된 상태로 성공' 이 된다 — 실패보다 나쁜
   * 결말이다.
   *
   * <p>{@code --profile-directory} 를 명시하는 이유도 같다. 생략하면 Chrome 이 마지막에 쓰던 프로필을 고를 수 있어, 사람이 로그인한 프로필과
   * 다른 곳이 열릴 수 있다.
   *
   * @param options 조립 중인 옵션
   * @param profileDir 프로필 디렉터리
   */
  private void applyProfileOptions(ChromeOptions options, Path profileDir) {
    options.addArguments("--user-data-dir=" + profileDir.toAbsolutePath());
    options.addArguments("--profile-directory=" + PROFILE_DIRECTORY);

    // 자동화 표식 제거. 프로필을 쓰는 경로에서만 붙인다.
    options.setExperimentalOption("excludeSwitches", EXCLUDED_SWITCHES);

    String binary = trimmedProperty(CHROME_BINARY_PROPERTY);
    if (binary != null) {
      // 프로필을 만든 Chrome 과 여는 Chrome 을 같은 것으로 고정한다.
      options.setBinary(binary);
    }
  }

  /** 시스템 프로퍼티를 읽되 빈 값은 없는 것으로 본다. */
  private static String trimmedProperty(String key) {
    String raw = System.getProperty(key);
    if (raw == null) {
      return null;
    }
    String trimmed = raw.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  /**
   * EdgeDriver 에 넘길 옵션을 조립한다.
   *
   * <p>Edge 분기는 페이지 로드 타임아웃 없이 기동되고 있었다 — Chrome 만 60초로 낮추고 Edge 는 Selenium 기본값 300초가 그대로 남아, 죽은
   * 페이지를 만나면 Edge 수집만 6분씩 붙잡히는 비대칭이 있었다(B-2 와 같은 결). 타임아웃 값과 재정의 프로퍼티는 Chrome 과 공유한다.
   *
   * <p>headless·user-agent 는 넣지 않는다 — Edge 분기는 설정 {@code DEFAULT_WEB_DRIVER=edge} 일 때만 타는 예비 경로로, 그
   * 두 옵션을 읽어 온 적이 없다. 필요해지면 Chrome 과 같은 방식으로 배선한다.
   *
   * @return 조립된 EdgeOptions
   */
  EdgeOptions buildEdgeOptions() {
    EdgeOptions options = new EdgeOptions();
    options.setPageLoadTimeout(Duration.ofSeconds(pageLoadTimeoutSeconds()));
    return options;
  }

  /** 페이지 로드 타임아웃(초). {@value #PAGE_LOAD_TIMEOUT_PROPERTY} 로 덮어쓸 수 있다. */
  static int pageLoadTimeoutSeconds() {
    String raw = System.getProperty(PAGE_LOAD_TIMEOUT_PROPERTY);
    if (raw == null || raw.isBlank()) {
      return DEFAULT_PAGE_LOAD_TIMEOUT_SECONDS;
    }
    try {
      int parsed = Integer.parseInt(raw.trim());
      return parsed > 0 ? parsed : DEFAULT_PAGE_LOAD_TIMEOUT_SECONDS;
    } catch (NumberFormatException e) {
      return DEFAULT_PAGE_LOAD_TIMEOUT_SECONDS;
    }
  }

  /**
   * WebDriver 인스턴스의 브라우저 이름을 반환한다.
   *
   * @param driver
   * @return
   */
  public static String getBrowserName(WebDriver driver) {
    // RemoteWebDriver로 캐스팅하여 Capabilities 가져오기
    Capabilities capabilities = ((RemoteWebDriver) driver).getCapabilities();

    // 브라우저 이름 및 버전 가져오기
    String browserName = capabilities.getBrowserName();

    return browserName;
  }

  public static boolean isChrome(WebDriver driver) {
    String browserName = WebDriverManager.getBrowserName(driver);
    return browserName.equals(WebDriverManager.BROWSER_NAME_CHROME);
  }

  public static boolean isEdge(WebDriver driver) {
    String browserName = WebDriverManager.getBrowserName(driver);
    return browserName.equals(WebDriverManager.BROWSER_NAME_EDGE);
  }
}

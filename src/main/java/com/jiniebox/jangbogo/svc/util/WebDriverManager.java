package com.jiniebox.jangbogo.svc.util;

import com.jiniebox.jangbogo.dto.JangbogoConfig;
import java.time.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
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
    WebDriver driver = null;

    if (this.BROWSER_NAME_CHROME.equals(browserName)) {
      // BROWSER_HEADLESS 가 true 일 때만 headless 로 띄운다.
      // (설정이 없거나 파싱 불가면 headless 를 켜지 않는다 — 로그인 화면을 눈으로 확인할 수 있어야 한다)
      boolean headless = Boolean.parseBoolean(config().get("BROWSER_HEADLESS"));
      ChromeOptions options = buildChromeOptions(headless);
      log.info("ChromeDriver 기동 (headless={})", headless);
      driver = new ChromeDriver(options);
    } else if (this.BROWSER_NAME_EDGE.equals(browserName)) {
      log.info("EdgeDriver 기동");
      driver = new EdgeDriver(buildEdgeOptions());
    }

    return driver;
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
    ChromeOptions options = new ChromeOptions();
    options.addArguments("--remote-allow-origins=*");
    options.addArguments(
        "user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.91 Safari/537.3");
    if (headless) {
      options.addArguments("--headless=new"); // 크롬 브라우저를 화면에 출력하지 않고 실행한다
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

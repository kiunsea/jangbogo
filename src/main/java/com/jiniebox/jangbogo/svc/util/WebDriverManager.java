package com.jiniebox.jangbogo.svc.util;

import com.jiniebox.jangbogo.dto.JangbogoConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.remote.RemoteWebDriver;

public class WebDriverManager {

  private Logger log = LogManager.getLogger(WebDriverManager.class);

  public static String BROWSER_NAME_CHROME = "chrome";
  public static String BROWSER_NAME_EDGE = "edge";

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
      driver = new EdgeDriver();
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
    return options;
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

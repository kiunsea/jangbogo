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
import org.springframework.beans.factory.annotation.Autowired;

public class WebDriverManager {

  private Logger log = LogManager.getLogger(WebDriverManager.class);

  public static String BROWSER_NAME_CHROME = "chrome";
  public static String BROWSER_NAME_EDGE = "edge";

  protected String CHROME_DRIVER_ID, CHROME_DRIVER_PATH, CHROME_BINARY_PATH;
  protected String EDGE_DRIVER_ID, EDGE_DRIVER_PATH;

  @Autowired private JangbogoConfig jangbogoConfig = new JangbogoConfig();

  /**
   * 웹서비스에 접속하기 위한 웹드라이브를 반환한다. properties에 설정한 값이 없다면 default는 chrome이다.
   *
   * @return
   */
  public synchronized WebDriver getWebDriver() {
    String defWebDrv = jangbogoConfig.get("DEFAULT_WEB_DRIVER");
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
      ChromeOptions options = new ChromeOptions();
      options.addArguments("--remote-allow-origins=*");
      options.addArguments(
          "user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.91 Safari/537.3");
      Object headless = jangbogoConfig.get("BROWSER_HEADLESS");
      if (headless == null || !Boolean.parseBoolean(headless.toString())) {
        options.addArguments("headless"); // 크롬 브라우저를 화면에 출력하지 않고 실행한다
      }
      driver = new ChromeDriver();
    } else if (this.BROWSER_NAME_EDGE.equals(browserName)) {
      driver = new EdgeDriver();
    }

    return driver;
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

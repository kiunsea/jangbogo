package com.jiniebox.jangbogo.svc.util;

import com.jiniebox.jangbogo.dto.JangbogoConfig;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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
   * 이미 떠 있는 Chrome 에 붙는다 (<b>휴면 보존 — 아래 "처분" 참조</b>).
   *
   * <h3>⚠ 처분 — 현역이 아니다 (2026-08-07)</h3>
   *
   * <p><b>{@code src/main} 안에 이 메서드를 부르는 코드는 없다.</b> ADR-0002 가 캡처 경로를 후보 (a)(Selenium + 마스킹)로
   * 확정하면서 (b)(순정 chrome.exe 에 디버깅 포트로 붙기)가 기각됐고, 이 메서드는 (b) 전용이다. 지금 세션 캡처는 {@code
   * SessionCaptureService} 가 {@link #getWebDriver(String, Path)} 로 직접 띄운 브라우저에서 한다.
   *
   * <p><b>그래도 지우지 않는다.</b> ADR-0002 의 측정표를 만드는 {@code SessionCaptureProbe} 가 이 메서드로 '붙은 뒤' 팔을 세운다 —
   * 지우면 프로브가 컴파일되지 않고, "포트를 여는 것만으로 {@code navigator.webdriver} 가 켜진다" 는 그 문서의 핵심 근거를 다시 잴 수 없게 된다.
   * 이 표기가 없던 동안 javadoc 이 {@code (Phase 5-15)} 를 달고 있어 <b>현역인데 호출자가 0건</b>인 모순으로 읽혔다.
   *
   * <p>아래 서술은 재측정을 위해 실측 그대로 보존한다.
   *
   * <p>여기서만 브라우저를 <b>새로 띄우지 않는다.</b> 사람이 순정 chrome.exe 로 로그인하는 그 브라우저에 밖에서 붙어 쿠키만 뜨는 것이 목적이다. 캡처는
   * 브라우저가 <b>살아 있는 동안</b> 해야 한다 — 인증 쿠키가 세션 스코프라 창을 닫으면 사라진다(ADR-0001).
   *
   * <p>{@code debuggerAddress} 를 주면 chromedriver 는 브라우저를 기동하지 않고 그 주소에 접속만 한다. 그래서 프로필·바이너리·자동화 표식
   * <b>옵션</b>은 하나도 붙이지 않는다 — 기동 시점에만 의미가 있는 값이고, 여기서 얹으면 조용히 무시되면서 "적용됐다"는 착각만 남는다.
   *
   * <p><b>표식이 없다는 뜻은 아니다.</b> 붙는 대상은 디버깅 포트를 연 브라우저이고, 그것만으로 {@code navigator.webdriver} 가 {@code
   * true} 다(실측). 사람이 그 창을 만지기 전에 반드시 {@link #applyStealth(WebDriver)} 를 불러 되돌려야 한다 — 새 문서부터 적용되므로
   * 로그인 주소로 이동하기 <b>전에</b> 불러야 한다.
   *
   * <p><b>{@code quit()} 은 이 브라우저를 닫지 않는다</b>(실측). 붙은 세션만 끊기고 브라우저와 열린 포트는 남는다. 닫으려면 {@link
   * #closeAttachedBrowser(WebDriver)} 를 쓴다.
   *
   * @param debuggerAddress {@code 127.0.0.1:포트}
   * @return 붙은 드라이버
   */
  public WebDriver attachToRunningChrome(String debuggerAddress) {
    log.info("실행 중인 Chrome 에 붙는다 (debuggerAddress={})", debuggerAddress);
    return new ChromeDriver(buildAttachOptions(debuggerAddress));
  }

  /**
   * 붙어 있는 브라우저를 닫는다 (<b>휴면 보존 — {@link #attachToRunningChrome} 의 "처분" 과 같다</b>).
   *
   * <p>{@code src/main} 안 호출자가 없다. {@link #attachToRunningChrome} 이 붙은 브라우저를 닫는 유일한 수단이라 그것과 한 쌍이고,
   * {@code SessionCaptureProbe} 의 '닫힌다' 확인이 이 메서드로 이뤄진다 — 그래서 함께 남긴다. 근거는 ADR-0002.
   *
   * <p>{@code quit()} 만으로는 닫히지 않는다 — 드라이버가 띄운 브라우저가 아니기 때문이다. 그대로 두면 <b>로그인된 브라우저와 인증 없는 디버깅 포트가
   * 그대로 남는다.</b> 같은 PC 의 아무 프로세스나 그 세션을 조작할 수 있으므로 캡처가 끝나면 반드시 닫는다.
   *
   * <p>CDP {@code Browser.close} 로 브라우저에게 정상 종료를 시킨 뒤 드라이버를 놓는다. 닫기가 실패해도 드라이버는 놓는다 — 그쪽까지 못 놓으면
   * 정리할 방법이 아예 없어진다.
   *
   * @param driver {@link #attachToRunningChrome} 이 돌려준 드라이버
   */
  public void closeAttachedBrowser(WebDriver driver) {
    if (driver == null) {
      return;
    }
    try {
      if (driver instanceof ChromiumDriver chromium) {
        chromium.executeCdpCommand("Browser.close", Map.of());
      } else {
        log.warn("CDP 를 지원하지 않는 드라이버라 브라우저를 닫지 못한다: {}", driver.getClass().getSimpleName());
      }
    } catch (RuntimeException e) {
      log.warn("브라우저 종료 실패({}). 드라이버만 놓는다.", e.getClass().getSimpleName());
    } finally {
      try {
        driver.quit();
      } catch (RuntimeException e) {
        // 이미 닫힌 브라우저에 대고 놓으면 예외가 날 수 있다. 정리 단계라 삼킨다.
        log.debug("드라이버 종료 중 예외({})", e.getClass().getSimpleName());
      }
    }
  }

  /**
   * 프로필 디렉터리를 물고 있는 고아 Chrome 을 강제 종료한다 (Phase 5-15 후속).
   *
   * <p>ChromeDriver 는 chrome.exe 를 띄운 <b>뒤에</b> 세션 핸드셰이크를 한다. 핸드셰이크가 실패하면({@code
   * SessionNotCreatedException}) 예외만 남고 돌려받은 드라이버가 없어 {@code quit()} 으로 닿을 수 없는 브라우저가 생긴다. 그 브라우저가
   * {@code --user-data-dir} 락을 쥔 채 남으면 같은 프로필의 다음 기동이 전부 "user data directory is already in use" 로
   * 실패한다 — 실측에서 이런 고아 8개가 쌓여 이후 시도를 전부 막았다.
   *
   * <p>대상은 명령줄에 {@code --user-data-dir=<profileDir>} 가 있는 chrome.exe 로 한정한다. 캡처 프로필 디렉터리는 캡처 브라우저만
   * 쓰므로, 활성 캡처가 없을 때 그 경로를 문 크롬은 전부 고아다. 개인 Chrome 은 이 인자를 갖지 않아 건드리지 않는다. Java 의 {@link
   * ProcessHandle} 은 Windows 에서 다른 프로세스의 명령줄을 주지 않으므로 PowerShell(CIM)로 조회한다.
   *
   * <p>정리는 최선 노력이다 — 어떤 실패도 밖으로 던지지 않는다. 캡처 흐름은 "실패는 값으로" 계약이라, 정리 실패가 흐름을 끊어서는 안 된다.
   *
   * @param profileDir 캡처 프로필 디렉터리
   * @return 종료한 프로세스 수. 비-Windows·조회 실패면 0
   */
  public int killOrphanProfileChrome(Path profileDir) {
    if (profileDir == null
        || !System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) {
      return 0;
    }
    // PowerShell 단일 인용 문자열은 ' 를 '' 로만 이스케이프하면 된다. 경로 재료는
    // SessionProfilePolicy.profileDir 의 검증(구분자·상위 참조 금지)을 통과한 값이다.
    String needle = ("--user-data-dir=" + profileDir.toAbsolutePath()).replace("'", "''");
    String script =
        "Get-CimInstance Win32_Process -Filter \"Name='chrome.exe'\" | Where-Object {"
            + " $_.CommandLine -and $_.CommandLine.Contains('"
            + needle
            + "') } | ForEach-Object {"
            + " Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue; $_.ProcessId }";
    try {
      Process ps =
          new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script)
              .redirectErrorStream(true)
              .start();
      // 출력은 종료한 PID 몇 줄이 전부라 파이프가 차기 전에 끝난다 — 종료를 먼저 기다려도 안전하다.
      if (!ps.waitFor(10, TimeUnit.SECONDS)) {
        ps.destroyForcibly();
        log.warn("고아 브라우저 조회가 10초 안에 끝나지 않아 중단했다.");
        return 0;
      }
      int killed = 0;
      try (BufferedReader out = new BufferedReader(new InputStreamReader(ps.getInputStream()))) {
        String line;
        while ((line = out.readLine()) != null) {
          String t = line.trim();
          if (!t.isEmpty() && t.chars().allMatch(Character::isDigit)) {
            killed++;
          }
        }
      }
      if (killed > 0) {
        log.info("프로필 {} 을 물고 있던 고아 Chrome {}개를 강제 종료했다.", profileDir.getFileName(), killed);
      }
      return killed;
    } catch (IOException e) {
      log.warn("고아 브라우저 정리 실패({}). 그대로 진행한다.", e.getClass().getSimpleName());
      return 0;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("고아 브라우저 정리가 인터럽트됐다. 그대로 진행한다.");
      return 0;
    }
  }

  /**
   * 붙기 전용 옵션을 조립한다 (Phase 5-15).
   *
   * @param debuggerAddress {@code 호스트:포트}
   * @return 조립된 ChromeOptions
   */
  ChromeOptions buildAttachOptions(String debuggerAddress) {
    String address = debuggerAddress == null ? "" : debuggerAddress.trim();
    if (address.isBlank()) {
      throw new IllegalArgumentException("debuggerAddress 가 비어 있다.");
    }
    // 형태만 멀쩡한 값(예: 127.0.0.1:-1)이 통과하면 원래 원인('포트가 안 열렸다')이
    // 드라이버 예외 속으로 사라진다. '비어 있지 않다' 는 이 값에 대한 검사로 너무 약하다.
    int colon = address.lastIndexOf(':');
    int port = -1;
    if (colon > 0 && colon < address.length() - 1) {
      try {
        port = Integer.parseInt(address.substring(colon + 1));
      } catch (NumberFormatException ignore) {
        port = -1;
      }
    }
    if (port <= 0 || port > 65535) {
      throw new IllegalArgumentException("debuggerAddress 의 포트가 유효하지 않다: " + address);
    }

    ChromeOptions options = new ChromeOptions();
    options.setExperimentalOption("debuggerAddress", address);
    // 붙은 뒤에도 페이지가 응답하지 않으면 기다림이 끝나야 한다. 나머지 기동 옵션과 같은 상수를 쓴다.
    options.setPageLoadTimeout(Duration.ofSeconds(pageLoadTimeoutSeconds()));
    return options;
  }

  /**
   * 새 문서마다 마스킹 스크립트를 주입한다 (Phase 5-7).
   *
   * <p>{@code excludeSwitches} 로 배너와 스위치는 지워지지만 {@code navigator.webdriver} 는 그대로 남는다. 페이지가 로드될 때마다
   * 문서 스크립트보다 먼저 돌아야 하므로 {@code Page.addScriptToEvaluateOnNewDocument} 를 쓴다.
   *
   * <p>실패해도 수집을 멈추지 않는다 — 마스킹은 있으면 나은 것이지 없으면 못 도는 것이 아니다. 다만 조용히 넘기지는 않는다.
   *
   * <p><b>붙기 경로에서는 이것을 밖에서 불러야 한다</b>(5-15). 디버깅 포트를 연 브라우저는 그것만으로 표식이 켜져 있고, 되돌리는 수단이 이 메서드뿐이라 패키지
   * 밖(컨트롤러·서비스)에서도 부를 수 있어야 한다. 새 문서부터 적용되므로 <b>로그인 주소로 이동하기 전에</b> 부른다.
   *
   * @param driver 기동했거나 붙은 드라이버
   */
  public void applyStealth(WebDriver driver) {
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
    // User-Agent 를 지정하지 않는다 — Chrome 이 내는 값이 언제나 옳다.
    //
    // 예전에는 여기에 UA 문자열을 박아 뒀는데, 그것이 실측에서 순정 Chrome 과 갈리는 유일한 원인이었다.
    // 12축 지문 비교에서 세 축이 어긋났고 셋 다 이 한 줄에서 나왔다:
    //   ua      : 순정 Chrome/150.0.0.0  vs  박아 둔 값 Chrome/124.0.6367.91
    //   uaTail  : 순정 Safari/537.36     vs  박아 둔 값 Safari/537.3  (형태 자체가 어긋나 있었다)
    //   uaFull  : 순정 150.0.7871.187    vs  빈 값
    //
    // 마지막 항목이 특히 나쁘다. UA 문자열을 덮어써도 클라이언트 힌트(User-Agent Client Hints)는
    // 실제 버전을 그대로 내므로, 사이트는 '자기를 124 라고 주장하면서 힌트는 150 인' 브라우저를 본다.
    // 지문을 정교하게 분석할 것도 없이 이미 받은 두 값이 서로 어긋난다.
    //
    // 박아 둘 이유도 없었다 — 이 값을 읽는 코드가 저장소에 없고, 도입 커밋에도 근거가 없다.
    // 지우면 Chrome 이 자기 버전을 정확히 광고하고, 업데이트를 따라가는 유지보수도 사라진다.
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

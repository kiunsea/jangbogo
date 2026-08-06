package com.jiniebox.jangbogo.svc.util;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 사람이 직접 로그인할 순정 Chrome 을 띄운다 (Phase 4A-3 · 5-6).
 *
 * <h2>⚠ 처분 — 이 클래스는 휴면이다. 현역이 아니다 (2026-08-07)</h2>
 *
 * <p><b>{@code src/main} 안에 이 클래스를 부르는 코드는 없다.</b> ADR-0002 가 캡처 경로를 후보 (a)(Selenium + 마스킹)로 확정하면서
 * (b)(순정 chrome.exe + 디버깅 포트 + 붙기)가 기각됐고, 이 클래스는 (b) 전용 부품이다. 지금 세션 캡처를 실제로 수행하는 것은 {@code
 * SessionCaptureService} 이고 브라우저는 {@link WebDriverManager#getWebDriver(String, java.nio.file.Path)}
 * 로 띄운다.
 *
 * <p><b>그래도 지우지 않는다 — 지우면 ADR-0002 를 다시 잴 수 없다.</b> 그 문서의 측정표는 전부 {@code SessionCaptureProbe} 가 만든
 * 것이고, 그 프로브는 이 클래스로 <b>대조군</b>을 세운다(포트 없는 순정 / 포트를 연 순정 / 붙은 뒤 / 마스킹한 뒤). 이 클래스를 지우면 프로브가 컴파일되지
 * 않고, "{@code --remote-debugging-port} 를 여는 것 자체가 자동화 표식을 켠다" 는 핵심 근거를 누구도 다시 확인할 수 없게 된다. 근거를 다시 잴
 * 수 없는 ADR 은 그때부터 전언이다.
 *
 * <p>이 문단을 여기 적어 두는 이유는, 이 처분이 문서에만 있던 동안 <b>코드가 스스로를 현역이라 말하고 있었기</b> 때문이다 — 아래 메서드 javadoc 들이
 * {@code (Phase 5-15)} 를 달고 있었고, 호출자는 0건이었다. 그 어긋남을 만난 사람은 "배선이 빠진 건가" 로 없는 버그를 조사하거나 "안 쓰니 정리하자" 로
 * 프로브를 깨뜨린다. 둘 다 실제로 비싸다.
 *
 * <p><b>되살아나는 조건</b> — (a) 가 실사이트 봇 방어에서 막혀 (b) 를 다시 재야 할 때다. 그때 필요한 것은 새로 쓰는 코드가 아니라 이 클래스와 프로브다.
 * 아래 서술은 그 재측정을 위해 <b>실측 그대로</b> 보존한다.
 *
 * <h2>왜 Selenium 이 아니라 순정 chrome.exe 인가</h2>
 *
 * <p>세션을 <b>만드는</b> 일과 <b>여는</b> 일은 다른 문제다. 만드는 쪽은 사람이 로그인하는 절차이므로 자동화 표식이 하나도 없는 편이 낫다. Selenium
 * 으로 띄우면 아무리 표식을 지워도 지운 흔적이 남을 수 있고, 로그인 단계에서 막히면 "프로필 재사용이 안 되는 것"인지 "로그인 자체가 막힌 것"인지 구분할 수 없게 된다.
 *
 * <p>그래서 만드는 쪽은 <b>순수 JDK {@link ProcessBuilder}</b> 로 chrome.exe 를 그냥 실행한다. Selenium·chromedriver
 * 가 관여하지 않으므로 사람이 평소 쓰는 브라우저와 구분되지 않는다.
 *
 * <p><b>단, 디버깅 포트를 여는 오버로드는 이 성질을 잃는다.</b> 실측 결과 {@code --remote-debugging-port} 를 붙이는 것만으로 {@code
 * navigator.webdriver} 가 {@code true} 가 된다(같은 실행에서 포트 없는 순정은 {@code false}). 그래서 그 경로는 붙은 직후 {@link
 * WebDriverManager#applyStealth} 로 표식을 되돌린 <b>다음에</b> 사람에게 로그인 화면을 넘겨야 한다. 재현: {@code
 * SessionCaptureProbe}.
 *
 * <h2>창이 닫혔다고 끝난 게 아니다</h2>
 *
 * <p>Chrome 은 실행된 프로세스가 브라우저 수명주기를 그대로 갖지 않는다. 이미 떠 있는 인스턴스에 넘기고 즉시 빠지거나, 권한이 다르면 자신을 재실행하고 원래
 * 프로세스는 곧바로 종료한다. 그래서 {@code Process.waitFor()} 는 <b>사람이 로그인하기 전에 돌아올 수 있다.</b>
 *
 * <p>창이 닫혔다는 판정은 <b>프로필 락 해제</b>로 한다. Chrome 이 프로필을 쓰는 동안에는 {@code lockfile} 을 붙잡고 있고, 완전히 닫혀야 놓는다.
 * <b>영속</b> 쿠키가 디스크로 내려가는 것도 그 시점이다 — 강제 종료로 끝내면 프로필에 남지 않을 수 있다.
 *
 * <p><b>그러나 락 해제는 세션 캡처의 신호가 아니다(5-15).</b> 인증 쿠키는 세션 스코프라 창이 닫히면 사라진다(ADR-0001). 캡처는 브라우저가 살아 있는
 * 동안 해야 하고, 락 해제는 그 뒤의 <b>정리</b> 신호다. 닫힌 뒤에 뜨면 매번 빈 스냅샷이 나오는데 아무 예외도 나지 않는다.
 *
 * <p>브라우저를 띄우는 것 말고는 네트워크·DB 를 쓰지 않는다(디버깅 포트 확인은 루프백 HTTP 한 번).
 *
 * @author KIUNSEA
 */
public final class NativeChromeLoginLauncher {

  private static final Logger logger = LogManager.getLogger(NativeChromeLoginLauncher.class);

  /** Chrome 실행 파일 경로 재정의. 없으면 표준 설치 위치를 훑는다. */
  public static final String CHROME_BINARY_PROPERTY = WebDriverManager.CHROME_BINARY_PROPERTY;

  /** Chrome 이 프로필을 쓰는 동안 붙잡고 있는 파일. */
  static final String LOCK_FILE = "lockfile";

  /** 원격 디버깅 포트를 열지 않는다는 뜻. */
  public static final int NO_DEBUG_PORT = -1;

  /** 포트 번호를 Chrome 이 직접 고르게 한다. 실제 값은 {@link #DEVTOOLS_PORT_FILE} 에 적힌다. */
  public static final int ANY_DEBUG_PORT = 0;

  /**
   * Chrome 이 실제로 연 디버깅 포트를 적어 두는 파일 (프로필 디렉터리 바로 아래).
   *
   * <p>첫 줄이 포트, 둘째 줄이 브라우저 WebSocket 경로다.
   */
  static final String DEVTOOLS_PORT_FILE = "DevToolsActivePort";

  /**
   * 붙을 때 쓰는 호스트.
   *
   * <p>루프백 <b>바인딩</b>을 이 상수가 강제하는 것은 아니다 — {@code --remote-debugging-address} 를 주지 않으면 Chrome 이
   * 기본으로 루프백에만 여는 것이고, 이 값은 그 포트에 붙을 주소를 만들 때만 쓰인다.
   */
  public static final String DEBUG_HOST = "127.0.0.1";

  /** 표준 설치 위치. 위에서부터 찾는다. */
  static final List<String> STANDARD_BINARY_PATHS =
      List.of(
          "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
          "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe");

  private NativeChromeLoginLauncher() {}

  /**
   * 실행할 chrome.exe 를 찾는다.
   *
   * <p>프로퍼티가 있으면 그것을 쓴다 — 프로필을 만든 Chrome 과 여는 Chrome 을 같은 것으로 고정해야 하므로, 두 경로가 같은 프로퍼티를 본다({@link
   * WebDriverManager#CHROME_BINARY_PROPERTY}).
   *
   * @return chrome.exe 경로
   * @throws IllegalStateException 찾지 못하면
   */
  public static Path resolveChromeBinary() {
    String override = System.getProperty(CHROME_BINARY_PROPERTY);
    if (override != null && !override.isBlank()) {
      return Paths.get(override.trim());
    }
    for (String candidate : STANDARD_BINARY_PATHS) {
      Path path = Paths.get(candidate);
      if (Files.isRegularFile(path)) {
        return path;
      }
    }
    throw new IllegalStateException(
        "chrome.exe 를 찾을 수 없다. -D" + CHROME_BINARY_PROPERTY + "=<경로> 로 지정할 것.");
  }

  /**
   * 실행 명령을 조립한다.
   *
   * <p>브라우저를 띄우지 않고 검증할 수 있도록 분리했다. {@code --user-data-dir} 의 <b>{@code --} 접두사와 절대경로</b>는 여기서도 같은
   * 이유로 필수다 — 빠지면 Chrome 이 조용히 기본 프로필을 열고, 사람은 로그인했다고 믿지만 그 세션은 우리 프로필에 없다.
   *
   * @param chromeBinary chrome.exe
   * @param profileDir 프로필 디렉터리
   * @param url 처음 열 주소. 없으면 생략
   * @return 명령 토큰
   */
  public static List<String> buildCommand(Path chromeBinary, Path profileDir, String url) {
    return buildCommand(chromeBinary, profileDir, url, NO_DEBUG_PORT);
  }

  /**
   * 원격 디버깅 포트까지 열어 실행 명령을 조립한다 (<b>휴면 — 기각된 후보 (b) 의 부품. 클래스 javadoc "처분" 참조</b>).
   *
   * <p>아래는 (b) 를 설계하던 시점의 서술이고, 재측정을 위해 그대로 둔다. 현재 캡처 경로는 이 포트를 열지 않는다.
   *
   * <p>세션 캡처는 <b>브라우저가 살아 있는 동안</b> 해야 한다. 인증 쿠키가 세션 스코프라(ADR-0001) 창을 닫으면 사라지므로, 닫힌 뒤에 뜰 수 있는 것이
   * 없다. 그래서 사람이 로그인하는 그 브라우저에 밖에서 붙을 창구가 필요하고, 그것이 이 포트다.
   *
   * <p><b>대가가 있다.</b> 실측 결과 이 포트를 여는 것만으로 {@code navigator.webdriver} 가 {@code true} 가 된다 — 사람이
   * 로그인하는 화면이 이미 자동화로 보인다. 붙은 직후 {@link WebDriverManager#applyStealth} 로 되돌린 <b>다음에</b> 로그인 주소로
   * 이동시켜야 한다.
   *
   * <p>포트는 루프백에만 열리지만 같은 PC 의 다른 프로세스는 붙을 수 있다. 로그인이 끝나면 <b>호출자가</b> 이 브라우저를 닫아야 한다 — 이 클래스는 닫지 않고,
   * 드라이버를 놓는 것({@code quit()})으로도 닫히지 않는다(실측). {@link WebDriverManager#closeAttachedBrowser} 를 쓴다.
   *
   * <p>{@code debugPort} 에 {@link #ANY_DEBUG_PORT} 를 주면 Chrome 이 빈 포트를 직접 고른다. 고정 번호를 쓰면 이미 쓰이는 포트와
   * 부딪혀 <b>디버깅 포트가 조용히 열리지 않고</b>, 그 번호로 붙는 쪽은 남의 브라우저에서 쿠키를 뜨게 된다. 그래서 {@link #launch} 는 고정 번호를 아예
   * 거부한다 — 이 메서드는 명령 조립만 하므로 무엇이든 받지만, 실제로 쓰이는 값은 두 상수뿐이다.
   *
   * @param chromeBinary chrome.exe
   * @param profileDir 프로필 디렉터리
   * @param url 처음 열 주소. 없으면 생략
   * @param debugPort {@link #ANY_DEBUG_PORT} 또는 {@link #NO_DEBUG_PORT}
   * @return 명령 토큰
   */
  public static List<String> buildCommand(
      Path chromeBinary, Path profileDir, String url, int debugPort) {
    List<String> command = new ArrayList<>();
    command.add(chromeBinary.toString());
    command.add("--user-data-dir=" + profileDir.toAbsolutePath());
    command.add("--profile-directory=" + WebDriverManager.PROFILE_DIRECTORY);
    // 새 창으로 띄운다. 안 그러면 이미 떠 있는 Chrome 이 URL 만 받아 열고
    // 우리 프로필은 열리지 않은 채 프로세스가 즉시 끝난다.
    command.add("--new-window");
    // 첫 실행 안내·기본 브라우저 확인 창이 로그인 화면을 가린다.
    command.add("--no-first-run");
    command.add("--no-default-browser-check");
    if (debugPort >= ANY_DEBUG_PORT) {
      command.add("--remote-debugging-port=" + debugPort);
    }
    if (url != null && !url.isBlank()) {
      command.add(url.trim());
    }
    return command;
  }

  /**
   * 사람이 로그인할 Chrome 을 띄운다.
   *
   * @param profileDir 프로필 디렉터리. 없으면 만든다
   * @param url 처음 열 주소
   * @return 실행된 프로세스. <b>수명주기를 믿지 말 것</b> — {@link #awaitProfileRelease} 를 쓴다
   * @throws IOException 실행 실패
   */
  public static Process launch(Path profileDir, String url) throws IOException {
    return launch(profileDir, url, NO_DEBUG_PORT);
  }

  /**
   * 원격 디버깅 포트를 열고 사람이 로그인할 Chrome 을 띄운다 (<b>휴면 — 프로브만 부른다. 클래스 javadoc "처분" 참조</b>).
   *
   * <p><b>고정 포트 번호는 받지 않는다.</b> 번호를 지정하면 그 포트를 이미 다른 브라우저가 쓰고 있을 때 우리 Chrome 은 포트를 못 열고, 그 번호로 붙은
   * 쪽은 <b>남의 브라우저에서 쿠키를 뜨게 된다.</b> 그것은 살아 있는 인증 토큰이므로 조용히 넘길 수 있는 사고가 아니다. 그래서 {@link
   * #ANY_DEBUG_PORT} 로 Chrome 이 고르게 하고 실제 값을 파일에서 읽는 길만 연다.
   *
   * <p>기동 전에 {@link #DEVTOOLS_PORT_FILE} 을 지운다 — 단, <b>그 프로필이 쓰이고 있지 않을 때만</b>이다. 이미 열려 있는 창의 기록을
   * 지우면 새 프로세스는 기존 인스턴스에 URL 만 넘기고 끝나므로, <b>포트는 멀쩡히 열려 있는데 파일이 사라져</b> 붙기만 실패한다.
   *
   * @param profileDir 프로필 디렉터리. 없으면 만든다
   * @param url 처음 열 주소
   * @param debugPort {@link #ANY_DEBUG_PORT} 또는 {@link #NO_DEBUG_PORT}
   * @return 실행된 프로세스. <b>수명주기를 믿지 말 것</b>
   * @throws IOException 실행 실패
   * @throws IllegalArgumentException 고정 포트 번호를 주면
   * @throws IllegalStateException 포트를 여는데 그 프로필이 이미 쓰이고 있으면
   */
  public static Process launch(Path profileDir, String url, int debugPort) throws IOException {
    if (debugPort != NO_DEBUG_PORT && debugPort != ANY_DEBUG_PORT) {
      throw new IllegalArgumentException(
          "고정 디버깅 포트는 지원하지 않는다(남의 브라우저에 붙을 수 있다). ANY_DEBUG_PORT 를 쓸 것: " + debugPort);
    }
    Files.createDirectories(profileDir);
    if (debugPort == ANY_DEBUG_PORT) {
      if (isProfileInUse(profileDir)) {
        throw new IllegalStateException(
            "이 프로필로 이미 브라우저가 열려 있다. 그 창을 닫고 다시 시도할 것: " + profileDir.toAbsolutePath());
      }
      clearStaleDevToolsPort(profileDir);
    }
    Path chrome = resolveChromeBinary();

    logger.info(
        "순정 Chrome 기동: {}", String.join(" ", buildCommand(chrome, profileDir, url, debugPort)));
    return newProcessBuilder(chrome, profileDir, url, debugPort).start();
  }

  /** 지난 실행이 남긴 포트 기록을 지운다. 남아 있으면 죽은 포트에 붙으려 한다. */
  static void clearStaleDevToolsPort(Path profileDir) throws IOException {
    Files.deleteIfExists(profileDir.resolve(DEVTOOLS_PORT_FILE));
  }

  /**
   * Chrome 이 실제로 연 디버깅 포트를 읽는다.
   *
   * <p><b>두 줄이 다 있어야 인정한다.</b> Chrome 은 포트와 WebSocket 경로를 함께 쓰므로, 첫 줄만 있는 상태는 아직 쓰는 중일 수 있다. 그때 읽으면
   * {@code 51234} 의 앞부분만 읽어 <b>엉뚱한 포트가 살아 있어 보인다.</b>
   *
   * <p>읽지 못한 이유는 로그로 남긴다 — '파일이 아직 없다' 와 '읽을 수 없다' 가 같은 반환값으로 뭉개지면, 포트가 열려 있는데도 사람은 방화벽·포트 충돌을 뒤지게
   * 된다.
   *
   * @param profileDir 프로필 디렉터리
   * @return 포트. 아직 없거나 읽을 수 없으면 {@link #NO_DEBUG_PORT}
   */
  public static int readDevToolsPort(Path profileDir) {
    Path file = profileDir.resolve(DEVTOOLS_PORT_FILE);
    if (!Files.isRegularFile(file)) {
      return NO_DEBUG_PORT;
    }
    try {
      List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
      if (lines.size() < 2) {
        // 아직 쓰는 중이다. 다음 폴링에서 다시 본다.
        return NO_DEBUG_PORT;
      }
      int port = Integer.parseInt(lines.get(0).trim());
      return port > 0 ? port : NO_DEBUG_PORT;
    } catch (IOException e) {
      logger.warn("디버깅 포트 기록을 읽지 못했다({}): {}", e.getClass().getSimpleName(), file);
      return NO_DEBUG_PORT;
    } catch (NumberFormatException e) {
      logger.warn("디버깅 포트 기록의 형식이 예상과 다르다: {}", file);
      return NO_DEBUG_PORT;
    }
  }

  /**
   * Chrome 이 포트를 열 때까지 기다린다.
   *
   * @param profileDir 프로필 디렉터리
   * @param timeout 대기 상한
   * @param pollInterval 확인 주기
   * @return 포트. 시간 안에 열리지 않으면 {@link #NO_DEBUG_PORT}
   */
  public static int awaitDevToolsPort(Path profileDir, Duration timeout, Duration pollInterval) {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline && !Thread.currentThread().isInterrupted()) {
      int port = readDevToolsPort(profileDir);
      if (port != NO_DEBUG_PORT) {
        return port;
      }
      sleep(pollInterval);
    }
    return NO_DEBUG_PORT;
  }

  /**
   * 그 포트의 DevTools 가 실제로 응답하는지 확인한다.
   *
   * <p>포트 파일이 생겼다는 것과 붙을 수 있다는 것은 다르다. 붙기 전에 이 확인을 한 번 통과시켜야 경합으로 실패하지 않는다.
   *
   * @param port 디버깅 포트
   * @param timeout 대기 상한
   * @param pollInterval 확인 주기
   * @return 브라우저 식별 문자열(예: {@code Chrome/150.x}). 응답이 없으면 null
   */
  public static String awaitDevToolsBrowser(int port, Duration timeout, Duration pollInterval) {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline && !Thread.currentThread().isInterrupted()) {
      String browser = devToolsBrowser(port);
      if (browser != null) {
        return browser;
      }
      sleep(pollInterval);
    }
    return null;
  }

  /**
   * DevTools 의 {@code /json/version} 을 한 번 두드린다.
   *
   * <p><b>200 이라고 DevTools 인 것은 아니다.</b> 디버깅 포트는 임시 포트라 재사용되므로, 그 번호를 그 사이 다른 로컬 서비스가 차지했을 수 있다.
   * 응답이 DevTools 의 것인지 확인하지 않으면 <b>남의 HTTP 서버를 브라우저로 착각</b>하고, 그 뒤 붙기가 실패해도 기록은 "DevTools 는 응답했다" 를
   * 가리켜 원인 추적이 반대로 간다.
   *
   * @param port 디버깅 포트
   * @return 브라우저 식별 문자열. DevTools 가 아니거나 응답이 없으면 null
   */
  static String devToolsBrowser(int port) {
    if (port <= 0) {
      return null;
    }
    try (HttpClient client =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create("http://" + DEBUG_HOST + ":" + port + "/json/version"))
              .timeout(Duration.ofSeconds(3))
              .GET()
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        return null;
      }
      // {"Browser":"Chrome/150.0.0.0", "webSocketDebuggerUrl":"ws://..."} — 값만 뽑는다.
      // 여기서 JSON 파서를 끌어올 이유가 없다.
      String body = response.body();
      int at = body.indexOf("\"Browser\"");
      if (at < 0 || !body.contains("\"webSocketDebuggerUrl\"")) {
        // DevTools 가 아니다. 붙을 수 있다고 보고하지 않는다.
        return null;
      }
      int colon = body.indexOf(':', at);
      if (colon < 0) {
        return null;
      }
      int start = body.indexOf('"', colon) + 1;
      int end = start > 0 ? body.indexOf('"', start) : -1;
      return start > 0 && end > start ? body.substring(start, end) : null;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    } catch (IOException | RuntimeException e) {
      return null;
    }
  }

  /**
   * 붙을 때 쓰는 주소.
   *
   * <p>열리지 않은 포트({@link #NO_DEBUG_PORT})로는 주소를 만들지 않는다. {@code 127.0.0.1:-1} 같은 문자열은 형태만 멀쩡해서 받는 쪽의
   * '비어 있는지' 검사를 통과하고, 그러면 원래 원인('포트가 안 열렸다')이 드라이버 예외 속으로 사라진다.
   *
   * @param port 디버깅 포트
   * @return {@code 127.0.0.1:포트}
   * @throws IllegalArgumentException 포트가 유효하지 않으면
   */
  public static String debuggerAddress(int port) {
    if (port <= 0 || port > 65535) {
      throw new IllegalArgumentException("붙을 수 있는 포트가 아니다: " + port);
    }
    return DEBUG_HOST + ":" + port;
  }

  /**
   * 기동에 쓸 {@link ProcessBuilder} 를 조립한다.
   *
   * <p><b>표준 입출력을 물려주지 않는다.</b> {@code inheritIO()} 로 띄우면 Chrome 이 부모의 스트림 핸들을 잡고 있어, <b>부모가 먼저 끝나려
   * 해도 Chrome 이 닫힐 때까지 끝나지 못한다.</b> 실측으로 3시간 13분을 붙잡힌 적이 있다 — 창을 띄우고 바로 반환해야 하는 자리에서 사람이 로그인을 마칠
   * 때까지 호출자가 통째로 멈춰 있었다.
   *
   * <p>사람이 쓰라고 띄우는 브라우저이므로 콘솔 출력을 받을 이유도 없다. 진단이 필요하면 {@code {프로필}/chrome_debug.log} 를 본다.
   *
   * @param chromeBinary chrome.exe
   * @param profileDir 프로필 디렉터리
   * @param url 처음 열 주소
   * @return 조립된 ProcessBuilder
   */
  static ProcessBuilder newProcessBuilder(Path chromeBinary, Path profileDir, String url) {
    return newProcessBuilder(chromeBinary, profileDir, url, NO_DEBUG_PORT);
  }

  /** 디버깅 포트까지 반영한 {@link ProcessBuilder}. 표준 입출력 미상속 규칙은 위와 같다. */
  static ProcessBuilder newProcessBuilder(
      Path chromeBinary, Path profileDir, String url, int debugPort) {
    return new ProcessBuilder(buildCommand(chromeBinary, profileDir, url, debugPort))
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD);
  }

  /**
   * 프로필이 지금 쓰이고 있는지.
   *
   * <p>{@code lockfile} 을 배타적으로 잠글 수 있으면 Chrome 이 놓은 것이다. 파일이 아예 없으면 아직 한 번도 열리지 않았거나 이미 닫힌 것이므로 역시
   * 쓰이지 않는 상태로 본다.
   *
   * @param profileDir 프로필 디렉터리
   * @return 다른 프로세스가 쓰고 있으면 true
   */
  public static boolean isProfileInUse(Path profileDir) {
    Path lockFile = profileDir.resolve(LOCK_FILE);
    if (!Files.exists(lockFile)) {
      return false;
    }
    try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.WRITE)) {
      FileLock lock = channel.tryLock();
      if (lock == null) {
        return true;
      }
      lock.release();
      return false;
    } catch (IOException | RuntimeException e) {
      // 열 수 없다는 것 자체가 누군가 쥐고 있다는 신호다.
      return true;
    }
  }

  /**
   * 프로필이 놓일 때까지 기다린다.
   *
   * <p>이 대기가 <b>"사람이 로그인을 끝내고 창을 닫았다"</b>의 판정이다. {@code Process.waitFor()} 로는 알 수 없다.
   *
   * @param profileDir 프로필 디렉터리
   * @param timeout 대기 상한
   * @param pollInterval 확인 주기
   * @return 시간 안에 놓였으면 true
   */
  public static boolean awaitProfileRelease(
      Path profileDir, Duration timeout, Duration pollInterval) {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      if (!isProfileInUse(profileDir)) {
        // 쿠키가 디스크로 내려가는 데 약간의 여유를 준다.
        sleep(pollInterval);
        if (!isProfileInUse(profileDir)) {
          return true;
        }
      }
      sleep(pollInterval);
    }
    return false;
  }

  private static void sleep(Duration duration) {
    try {
      Thread.sleep(Math.max(1L, duration.toMillis()));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}

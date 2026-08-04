package com.jiniebox.jangbogo.svc.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 순정 Chrome 런처의 명령 조립·락 판정 검증 (Phase 4A-3).
 *
 * <p>브라우저를 띄우지 않는다 — 조립된 명령 토큰과 락 파일만 들여다본다. 실제 기동은 프로브(`@Tag("probe")`)가 맡는다.
 *
 * @author KIUNSEA
 */
class NativeChromeLoginLauncherTest {

  private static final Path CHROME = Paths.get("C:", "chrome", "chrome.exe");

  @Test
  @DisplayName("프로필 경로를 -- 접두사와 절대경로로 넘긴다")
  void carriesProfilePathWithPrefixAndAbsolutePath() {
    Path profile = Paths.get("build", "probe-profile");
    List<String> command = NativeChromeLoginLauncher.buildCommand(CHROME, profile, null);

    assertTrue(
        command.contains("--user-data-dir=" + profile.toAbsolutePath()),
        "접두사가 빠지면 Chrome 이 조용히 기본 프로필을 연다 — 사람은 로그인했다고 믿지만"
            + " 그 세션은 우리 프로필에 없다. 실제 명령: "
            + command);
    assertFalse(
        command.stream().anyMatch(a -> a.startsWith("user-data-dir=")), "실제 명령: " + command);
  }

  @Test
  @DisplayName("여는 쪽과 같은 하위 프로필을 쓴다")
  void usesTheSameProfileDirectoryAsTheReader() {
    // 만드는 쪽과 여는 쪽이 다른 하위 프로필을 보면 로그인해 둔 세션을 못 찾는다.
    assertTrue(
        NativeChromeLoginLauncher.buildCommand(CHROME, Paths.get("p"), null)
            .contains("--profile-directory=" + WebDriverManager.PROFILE_DIRECTORY));
  }

  @Test
  @DisplayName("이미 떠 있는 Chrome 에 흡수되지 않도록 새 창을 강제한다")
  void forcesANewWindow() {
    // --new-window 가 없으면 이미 떠 있는 인스턴스가 URL 만 받아 열고
    // 우리 프로필은 열리지 않은 채 프로세스가 즉시 끝난다.
    assertTrue(
        NativeChromeLoginLauncher.buildCommand(CHROME, Paths.get("p"), null)
            .contains("--new-window"));
  }

  @Test
  @DisplayName("첫 실행 안내 창을 띄우지 않는다")
  void suppressesFirstRunPrompts() {
    List<String> command = NativeChromeLoginLauncher.buildCommand(CHROME, Paths.get("p"), null);

    assertTrue(command.contains("--no-first-run"), "실제 명령: " + command);
    assertTrue(command.contains("--no-default-browser-check"), "실제 명령: " + command);
  }

  @Test
  @DisplayName("URL 은 마지막 인자로 붙고, 없으면 생략한다")
  void appendsUrlLast() {
    List<String> withUrl =
        NativeChromeLoginLauncher.buildCommand(CHROME, Paths.get("p"), "https://www.ssg.com/");
    assertEquals("https://www.ssg.com/", withUrl.get(withUrl.size() - 1));

    List<String> withoutUrl = NativeChromeLoginLauncher.buildCommand(CHROME, Paths.get("p"), "   ");
    assertFalse(withoutUrl.stream().anyMatch(a -> a.startsWith("http")), "실제 명령: " + withoutUrl);
  }

  @Test
  @DisplayName("실행 파일은 여는 쪽과 같은 프로퍼티로 지정한다")
  void binaryPropertyIsSharedWithTheReader() {
    // 프로필을 만든 Chrome 과 여는 Chrome 이 다르면 세션이 살아나지 않을 수 있다.
    // 두 경로가 같은 프로퍼티를 봐야 그 사고를 막는다.
    assertEquals(
        WebDriverManager.CHROME_BINARY_PROPERTY, NativeChromeLoginLauncher.CHROME_BINARY_PROPERTY);

    String previous = System.getProperty(NativeChromeLoginLauncher.CHROME_BINARY_PROPERTY);
    try {
      System.setProperty(
          NativeChromeLoginLauncher.CHROME_BINARY_PROPERTY, "D:\\pinned\\chrome.exe");
      assertEquals(
          Paths.get("D:\\pinned\\chrome.exe"), NativeChromeLoginLauncher.resolveChromeBinary());
    } finally {
      if (previous == null) {
        System.clearProperty(NativeChromeLoginLauncher.CHROME_BINARY_PROPERTY);
      } else {
        System.setProperty(NativeChromeLoginLauncher.CHROME_BINARY_PROPERTY, previous);
      }
    }
  }

  @Test
  @DisplayName("실행 파일을 못 찾으면 조용히 넘기지 않는다")
  void failsLoudlyWhenChromeIsMissing() {
    String previous = System.getProperty(NativeChromeLoginLauncher.CHROME_BINARY_PROPERTY);
    try {
      // 빈 값은 없는 것으로 보고 표준 위치를 훑는다. 표준 위치에도 없으면 예외다.
      System.setProperty(NativeChromeLoginLauncher.CHROME_BINARY_PROPERTY, "   ");
      boolean installed =
          NativeChromeLoginLauncher.STANDARD_BINARY_PATHS.stream()
              .anyMatch(p -> Files.isRegularFile(Paths.get(p)));
      if (!installed) {
        assertThrows(IllegalStateException.class, NativeChromeLoginLauncher::resolveChromeBinary);
      }
    } finally {
      if (previous == null) {
        System.clearProperty(NativeChromeLoginLauncher.CHROME_BINARY_PROPERTY);
      } else {
        System.setProperty(NativeChromeLoginLauncher.CHROME_BINARY_PROPERTY, previous);
      }
    }
  }

  @Test
  @DisplayName("표준 입출력을 물려주지 않는다 — 물려주면 호출자가 창이 닫힐 때까지 못 끝난다")
  void doesNotInheritStandardStreams() {
    // 실측: inheritIO() 로 띄웠더니 Chrome 이 부모의 스트림 핸들을 잡아, 창을 띄우고 바로
    // 반환해야 하는 호출이 사람이 로그인을 마칠 때까지 3시간 13분을 붙잡혀 있었다.
    ProcessBuilder builder =
        NativeChromeLoginLauncher.newProcessBuilder(CHROME, Paths.get("p"), null);

    assertEquals(
        ProcessBuilder.Redirect.DISCARD,
        builder.redirectOutput(),
        "표준 출력을 물려주면 호출자가 브라우저 수명에 묶인다.");
    assertEquals(
        ProcessBuilder.Redirect.DISCARD, builder.redirectError(), "표준 오류를 물려주면 호출자가 브라우저 수명에 묶인다.");
  }

  @Test
  @DisplayName("락 파일이 없으면 쓰이지 않는 상태로 본다")
  void treatsMissingLockFileAsFree(@TempDir Path profile) {
    assertFalse(NativeChromeLoginLauncher.isProfileInUse(profile));
  }

  @Test
  @DisplayName("락 파일이 잡혀 있으면 사용 중으로 본다 — 창이 닫혔다는 판정의 근거")
  void detectsHeldLockFile(@TempDir Path profile) throws IOException {
    // Process.waitFor() 는 사람이 로그인하기 전에 돌아올 수 있다.
    // 창이 정말 닫혔는지는 이 락으로만 알 수 있다.
    Path lockFile = profile.resolve(NativeChromeLoginLauncher.LOCK_FILE);
    Files.createFile(lockFile);

    try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.WRITE)) {
      FileLock held = channel.tryLock();
      assertTrue(NativeChromeLoginLauncher.isProfileInUse(profile), "잡혀 있는데 비었다고 판정했다.");
      held.release();
    }

    assertFalse(NativeChromeLoginLauncher.isProfileInUse(profile), "놓았는데 잡혀 있다고 판정했다.");
  }

  // ---------------------------------------------------------------------
  // Phase 5-15 · 원격 디버깅 포트
  //
  // 세션 캡처는 브라우저가 살아 있는 동안 해야 한다 — 인증 쿠키가 세션 스코프라
  // 창을 닫으면 뜰 것이 남지 않는다(ADR-0001). 그래서 사람이 로그인하는 그 브라우저에
  // 밖에서 붙을 창구가 필요하고, 아래는 그 창구가 실제로 명령까지 건너가는지 본다.
  // ---------------------------------------------------------------------

  @Test
  @DisplayName("디버깅 포트를 주면 명령에 들어가고, URL 은 여전히 마지막이다")
  void carriesDebugPortBeforeTheUrl() {
    List<String> command =
        NativeChromeLoginLauncher.buildCommand(
            CHROME,
            Paths.get("p"),
            "https://www.ssg.com/",
            NativeChromeLoginLauncher.ANY_DEBUG_PORT);

    assertTrue(command.contains("--remote-debugging-port=0"), "실제 명령: " + command);
    assertEquals(
        "https://www.ssg.com/",
        command.get(command.size() - 1),
        "URL 뒤에 인자가 붙으면 Chrome 이 그것을 주소로 읽을 수 있다. 실제 명령: " + command);
  }

  @Test
  @DisplayName("포트를 주지 않으면 디버깅 포트를 열지 않는다 — 기존 경로의 동작 불변")
  void doesNotOpenDebugPortByDefault() {
    // 포트를 여는 것은 같은 PC 의 다른 프로세스에 브라우저를 여는 일이다.
    // 캡처가 필요한 경로에서만 명시적으로 열어야 한다.
    for (List<String> command :
        List.of(
            NativeChromeLoginLauncher.buildCommand(CHROME, Paths.get("p"), null),
            NativeChromeLoginLauncher.buildCommand(
                CHROME, Paths.get("p"), null, NativeChromeLoginLauncher.NO_DEBUG_PORT))) {
      assertFalse(
          command.stream().anyMatch(a -> a.startsWith("--remote-debugging-port")),
          "요청하지 않았는데 디버깅 포트가 열렸다. 실제 명령: " + command);
    }
  }

  @Test
  @DisplayName("Chrome 이 고른 포트를 프로필의 기록 파일에서 읽는다")
  void readsThePortChromeActuallyOpened(@TempDir Path profile) throws IOException {
    // 고정 번호를 쓰면 이미 쓰이는 포트와 부딪혀 디버깅 포트가 조용히 열리지 않는다.
    // 그래서 Chrome 에게 고르게 하고, 실제 값은 이 파일에서 읽는다.
    assertEquals(
        NativeChromeLoginLauncher.NO_DEBUG_PORT,
        NativeChromeLoginLauncher.readDevToolsPort(profile),
        "파일이 없는데 포트를 돌려줬다.");

    Path portFile = profile.resolve(NativeChromeLoginLauncher.DEVTOOLS_PORT_FILE);
    Files.writeString(portFile, "51234\n/devtools/browser/abcd-1234\n");
    assertEquals(51234, NativeChromeLoginLauncher.readDevToolsPort(profile));

    Files.writeString(portFile, "포트아님\n/devtools/browser/abcd-1234\n");
    assertEquals(
        NativeChromeLoginLauncher.NO_DEBUG_PORT,
        NativeChromeLoginLauncher.readDevToolsPort(profile),
        "읽을 수 없는 값을 포트로 넘기면 엉뚱한 곳에 붙는다.");

    Files.writeString(portFile, "");
    assertEquals(
        NativeChromeLoginLauncher.NO_DEBUG_PORT,
        NativeChromeLoginLauncher.readDevToolsPort(profile));
  }

  @Test
  @DisplayName("포트만 적히고 둘째 줄이 없으면 아직 쓰는 중으로 본다")
  void ignoresHalfWrittenPortFile(@TempDir Path profile) throws IOException {
    // Chrome 은 포트와 WebSocket 경로를 함께 쓴다. 첫 줄만 있는 순간에 읽으면
    // 51234 의 앞부분 512 를 읽어 '살아 있어 보이는 엉뚱한 포트' 가 나올 수 있다.
    Files.writeString(profile.resolve(NativeChromeLoginLauncher.DEVTOOLS_PORT_FILE), "512");

    assertEquals(
        NativeChromeLoginLauncher.NO_DEBUG_PORT,
        NativeChromeLoginLauncher.readDevToolsPort(profile),
        "쓰다 만 파일을 읽고 포트를 확정했다.");
  }

  @Test
  @DisplayName("기동 전에 지난 실행의 포트 기록을 지운다 — 죽은 포트에 붙지 않게")
  void clearsStalePortFileBeforeLaunch(@TempDir Path profile) throws IOException {
    // 남아 있으면 브라우저는 멀쩡히 떠 있는데 붙기만 실패한다. 원인을 가리기 가장 어려운 형태다.
    Path portFile = profile.resolve(NativeChromeLoginLauncher.DEVTOOLS_PORT_FILE);
    Files.writeString(portFile, "51234\n");

    NativeChromeLoginLauncher.clearStaleDevToolsPort(profile);

    assertFalse(Files.exists(portFile), "지난 실행의 포트 기록이 남았다.");
  }

  @Test
  @DisplayName("포트가 열리지 않으면 대기가 시간 안에 끝난다")
  void portWaitTimesOut(@TempDir Path profile) {
    assertEquals(
        NativeChromeLoginLauncher.NO_DEBUG_PORT,
        NativeChromeLoginLauncher.awaitDevToolsPort(
            profile, Duration.ofMillis(150), Duration.ofMillis(20)),
        "열리지 않았는데 포트를 돌려주면 다음 단계가 죽은 주소에 붙는다.");
  }

  @Test
  @DisplayName("응답하지 않는 포트는 붙을 수 있다고 보고하지 않는다")
  void doesNotReportUnreachableDevTools() {
    // 포트 파일이 생겼다는 것과 붙을 수 있다는 것은 다르다.
    assertNull(NativeChromeLoginLauncher.devToolsBrowser(NativeChromeLoginLauncher.NO_DEBUG_PORT));
    assertNull(
        NativeChromeLoginLauncher.awaitDevToolsBrowser(
            NativeChromeLoginLauncher.NO_DEBUG_PORT,
            Duration.ofMillis(100),
            Duration.ofMillis(20)));

    // 아무도 안 듣는 포트. 열려 있지 않다는 것을 실제 HTTP 경로로 확인한다.
    assertNull(NativeChromeLoginLauncher.devToolsBrowser(freePort()));
  }

  @Test
  @DisplayName("DevTools 가 아닌 서버가 200 을 줘도 붙을 수 있다고 보고하지 않는다")
  void doesNotMistakeAnotherServerForDevTools() throws IOException {
    // 디버깅 포트는 임시 포트라 재사용된다. 그 번호를 다른 로컬 서비스가 차지했을 때
    // '응답했다' 로 읽으면, 붙기가 실패해도 기록은 "DevTools 는 응답했다" 를 가리켜
    // 원인 추적이 반대로 간다.
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          byte[] body = "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    try {
      assertNull(
          NativeChromeLoginLauncher.devToolsBrowser(server.getAddress().getPort()),
          "DevTools 가 아닌 200 응답을 브라우저로 읽었다.");
    } finally {
      server.stop(0);
    }
  }

  @Test
  @DisplayName("DevTools 응답이면 브라우저 이름을 뽑는다")
  void readsBrowserNameFromDevTools() throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/json/version",
        exchange -> {
          byte[] body =
              ("{\"Browser\":\"Chrome/150.0.7871.187\","
                      + "\"webSocketDebuggerUrl\":\"ws://127.0.0.1/devtools/browser/x\"}")
                  .getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    try {
      assertEquals(
          "Chrome/150.0.7871.187",
          NativeChromeLoginLauncher.devToolsBrowser(server.getAddress().getPort()));
    } finally {
      server.stop(0);
    }
  }

  @Test
  @DisplayName("붙을 주소는 루프백으로만 만들고, 열리지 않은 포트로는 만들지 않는다")
  void debuggerAddressStaysOnLoopback() {
    assertEquals("127.0.0.1:51234", NativeChromeLoginLauncher.debuggerAddress(51234));

    // '127.0.0.1:-1' 은 형태만 멀쩡해서 받는 쪽의 '비어 있는지' 검사를 통과한다.
    // 그러면 원래 원인('포트가 안 열렸다')이 드라이버 예외 속으로 사라진다.
    assertThrows(
        IllegalArgumentException.class,
        () -> NativeChromeLoginLauncher.debuggerAddress(NativeChromeLoginLauncher.NO_DEBUG_PORT));
    assertThrows(
        IllegalArgumentException.class, () -> NativeChromeLoginLauncher.debuggerAddress(70000));
  }

  @Test
  @DisplayName("고정 디버깅 포트는 아예 받지 않는다 — 남의 브라우저에 붙는 사고를 원천 차단")
  void refusesFixedDebugPort(@TempDir Path profile) {
    // 그 번호를 이미 다른 브라우저가 쓰고 있으면, 우리 Chrome 은 포트를 못 열고
    // 그 번호로 붙은 쪽은 남의 브라우저에서 살아 있는 인증 토큰을 뜨게 된다.
    assertThrows(
        IllegalArgumentException.class,
        () -> NativeChromeLoginLauncher.launch(profile, "http://127.0.0.1/", 9222));
  }

  @Test
  @DisplayName("이미 열려 있는 프로필에는 포트를 열지 않고 즉시 알린다")
  void refusesToOpenPortOnAProfileInUse(@TempDir Path profile) throws IOException {
    // 살아 있는 창의 포트 기록을 지우면, 새 프로세스는 기존 인스턴스에 URL 만 넘기고 끝나
    // '포트는 열려 있는데 파일이 없어' 붙기만 실패한다 — 가장 가리기 어려운 형태다.
    Path lockFile = profile.resolve(NativeChromeLoginLauncher.LOCK_FILE);
    Files.createFile(lockFile);
    Path portFile = profile.resolve(NativeChromeLoginLauncher.DEVTOOLS_PORT_FILE);
    Files.writeString(portFile, "51234\n/devtools/browser/abcd-1234\n");

    try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.WRITE)) {
      FileLock held = channel.tryLock();
      assertThrows(
          IllegalStateException.class,
          () ->
              NativeChromeLoginLauncher.launch(
                  profile, "http://127.0.0.1/", NativeChromeLoginLauncher.ANY_DEBUG_PORT));
      assertTrue(Files.exists(portFile), "살아 있는 창의 포트 기록을 지웠다.");
      held.release();
    }
  }

  /** 아무도 듣지 않는 포트 하나를 고른다. */
  private static int freePort() {
    try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      return 1;
    }
  }

  @Test
  @DisplayName("놓이지 않으면 대기가 시간 안에 false 로 끝난다")
  void awaitTimesOutWhileStillHeld(@TempDir Path profile) throws IOException {
    Path lockFile = profile.resolve(NativeChromeLoginLauncher.LOCK_FILE);
    Files.createFile(lockFile);

    try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.WRITE)) {
      FileLock held = channel.tryLock();
      assertFalse(
          NativeChromeLoginLauncher.awaitProfileRelease(
              profile, Duration.ofMillis(150), Duration.ofMillis(20)),
          "잡혀 있는데 해제됐다고 판정했다 — 로그인 전에 다음 단계로 넘어간다.");
      held.release();
    }

    assertTrue(
        NativeChromeLoginLauncher.awaitProfileRelease(
            profile, Duration.ofSeconds(2), Duration.ofMillis(20)));
  }
}

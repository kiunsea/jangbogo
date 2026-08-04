package com.jiniebox.jangbogo.svc.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

/**
 * 자동화 표식 측정 — Phase 4A 의 T1 · T2 · T4.
 *
 * <p><b>실제 브라우저를 띄운다.</b> 그래서 {@code @Tag("probe")} 로 일반 테스트 묶음에서 빠져 있다. 실행:
 *
 * <pre>./gradlew test -PincludeProbe --tests '*ChromeFingerprintProbe*'</pre>
 *
 * <p>외부 사이트에 접속하지 않는다 — 측정 페이지는 이 프로세스가 띄우는 127.0.0.1 서버가 낸다. 실계정도 쓰지 않는다.
 *
 * <p>산출물은 {@code build/probe-artifacts/} 아래에 남는다. <b>커밋 대상이 아니다.</b>
 *
 * @author KIUNSEA
 */
@Tag("probe")
class ChromeFingerprintProbe {

  /** 측정할 값들. 순정 Chrome 과 자동화 Chrome 이 갈리는 지점이다. */
  private static final String FINGERPRINT_SCRIPT =
      "return JSON.stringify({"
          + "webdriver: String(navigator.webdriver),"
          + "chromeRuntime: typeof (window.chrome && window.chrome.runtime),"
          + "plugins: navigator.plugins.length,"
          + "languages: navigator.languages.join(',')"
          + "});";

  private static Path artifactDir() throws IOException {
    Path dir = Paths.get("build", "probe-artifacts");
    Files.createDirectories(dir);
    return dir;
  }

  private static void record(String name, String body) throws IOException {
    Path file = artifactDir().resolve(name);
    Files.writeString(file, body, StandardCharsets.UTF_8);
    System.out.println("[probe] 기록: " + file.toAbsolutePath());
    System.out.println(body);
  }

  // ------------------------------------------------------------------
  // T1 — excludeSwitches 가 실제 명령줄에서 표식을 지우는가
  // ------------------------------------------------------------------

  @Test
  @DisplayName("T1 — excludeSwitches 적용 전/후 chrome.exe 명령줄 diff")
  void t1CommandLineDiff() throws Exception {
    Path plainProfile = probeProfile("t1-plain");
    Path cleanedProfile = probeProfile("t1-cleaned");

    String plain = commandLineWith(new WebDriverManager().buildChromeOptions(false), plainProfile);
    String cleaned =
        commandLineWith(new WebDriverManager().buildChromeOptions(false, cleanedProfile), null);

    StringBuilder report = new StringBuilder();
    report.append("[T1] chrome.exe 명령줄 diff\n\n");
    report.append("--- 적용 전 (Selenium 기본) ---\n").append(plain).append("\n\n");
    report.append("--- 적용 후 (excludeSwitches) ---\n").append(cleaned).append("\n\n");
    report.append("enable-automation  적용 전 포함=").append(plain.contains("enable-automation"));
    report.append(" / 적용 후 포함=").append(cleaned.contains("enable-automation")).append('\n');
    report.append("test-type          적용 전 포함=").append(plain.contains("test-type"));
    report.append(" / 적용 후 포함=").append(cleaned.contains("test-type")).append('\n');
    record("T1-command-line-diff.txt", report.toString());

    assertTrue(plain.contains("enable-automation"), "적용 전에 표식이 없다 — 비교가 성립하지 않는다.");
    assertTrue(
        !cleaned.contains("enable-automation"), "excludeSwitches 가 enable-automation 을 못 지웠다.");
    assertTrue(!cleaned.contains("test-type"), "excludeSwitches 가 test-type 을 못 지웠다.");
  }

  /** 옵션대로 Chrome 을 띄우고, 우리 프로필을 쓰는 프로세스의 명령줄만 골라 온다. */
  private String commandLineWith(ChromeOptions options, Path extraProfile) throws Exception {
    Path marker = extraProfile;
    if (marker != null) {
      options.addArguments("--user-data-dir=" + marker.toAbsolutePath());
      options.addArguments("--profile-directory=" + WebDriverManager.PROFILE_DIRECTORY);
    } else {
      marker = profileArgOf(options);
    }
    assertNotNull(marker, "프로필 경로를 못 찾겠다 — 남의 브라우저 명령줄을 덤프할 위험이 있다.");

    WebDriver driver = new ChromeDriver(options);
    try {
      return dumpCommandLines(marker);
    } finally {
      driver.quit();
    }
  }

  private static Path profileArgOf(ChromeOptions options) {
    @SuppressWarnings("unchecked")
    java.util.Map<String, Object> chromeOptions =
        (java.util.Map<String, Object>) options.asMap().get(ChromeOptions.CAPABILITY);
    @SuppressWarnings("unchecked")
    List<String> args = (List<String>) chromeOptions.getOrDefault("args", List.of());
    return args.stream()
        .filter(a -> a.startsWith("--user-data-dir="))
        .map(a -> Paths.get(a.substring("--user-data-dir=".length())))
        .findFirst()
        .orElse(null);
  }

  /**
   * 우리가 띄운 chrome.exe 의 명령줄만 덤프한다.
   *
   * <p>필터가 중요하다. 전부 덤프하면 사용자가 개인적으로 열어 둔 탭의 주소까지 산출물에 남는다 — 이 저장소는 공개다.
   */
  private static String dumpCommandLines(Path profileMarker) throws Exception {
    String needle = profileMarker.toAbsolutePath().toString();
    // 필터 값을 명령 문자열에 끼워 넣지 않는다. ProcessBuilder 의 Windows 인자 인용과
    // PowerShell 의 파싱이 겹치면 중첩 따옴표가 조용히 망가진다 — 실제로 이 프로브의
    // 첫 실행이 그렇게 빈 결과를 냈다. 환경 변수로 넘기면 인용이 아예 개입하지 않는다.
    List<String> command =
        List.of(
            "powershell",
            "-NoProfile",
            "-Command",
            "Get-CimInstance Win32_Process | Where-Object {"
                + " $_.Name -eq 'chrome.exe' -and $_.CommandLine"
                + " -and $_.CommandLine.Contains($env:PROBE_PROFILE)"
                + " } | Select-Object -ExpandProperty CommandLine");
    ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
    builder.environment().put("PROBE_PROFILE", needle);

    Process process = builder.start();
    String output;
    try (InputStream in = process.getInputStream()) {
      output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    process.waitFor(30, TimeUnit.SECONDS);

    List<String> mine = new ArrayList<>();
    for (String line : output.split("\\R")) {
      String trimmed = line.trim();
      if (trimmed.contains(needle)) {
        mine.add(trimmed);
      }
    }
    return mine.isEmpty() ? "(우리 프로필을 쓰는 chrome.exe 를 찾지 못했다)" : String.join("\n", mine);
  }

  // ------------------------------------------------------------------
  // T2 — 순정 Chrome 과 자동화 Chrome 의 지문 비교
  // ------------------------------------------------------------------

  @Test
  @DisplayName("T2 — (a) 순정 / (b) Selenium 기본 / (c) Selenium+표식제거+CDP 지문 비교")
  void t2FingerprintComparison() throws Exception {
    String nativeFingerprint = measureNative();
    String seleniumDefault =
        measureSelenium(new WebDriverManager().buildChromeOptions(false), false);

    Path stealthProfile = probeProfile("t2-stealth");
    String seleniumStealth =
        measureSelenium(new WebDriverManager().buildChromeOptions(false, stealthProfile), true);

    StringBuilder report = new StringBuilder();
    report.append("[T2] 자동화 지문 비교 (외부 사이트 접속 0)\n\n");
    report.append("(a) 순정 chrome.exe          : ").append(nativeFingerprint).append('\n');
    report.append("(b) Selenium 기본            : ").append(seleniumDefault).append('\n');
    report.append("(c) Selenium+표식제거+CDP    : ").append(seleniumStealth).append('\n');
    record("T2-fingerprint.txt", report.toString());

    // (a) 가 비면 (c) 의 "순정과 같다" 가 근거를 잃는다. 첫 실행에서 실제로 비어 있었는데도
    // (b)·(c) 만 보던 단언이 통과했다 — 측정이 빠진 것을 측정으로 잡는다.
    assertTrue(
        nativeFingerprint.contains("\"webdriver\":"),
        "(a) 순정 측정값이 비었다. 비교 기준이 없으면 (c) 판정이 성립하지 않는다. 실제: [" + nativeFingerprint + "]");
    assertTrue(
        seleniumDefault.contains("\"webdriver\":\"true\""),
        "(b) 가 webdriver=true 가 아니다 — 비교가 성립하지 않는다.");
    // 지우는 게 아니라 순정과 같은 값(false)으로 되돌리는 것이 목표다.
    assertTrue(
        seleniumStealth.contains("\"webdriver\":\"false\""),
        "(c) 의 webdriver 가 순정과 같은 false 가 아니다. 실제: " + seleniumStealth);
    assertEquals(
        nativeFingerprint.trim(),
        seleniumStealth.trim(),
        "(c) 가 순정 Chrome 과 다르다 — stealth 동등성이 성립하지 않는다.");
  }

  /** 순정 Chrome 은 우리가 스크립트를 넣을 수 없다. 로컬 페이지가 값을 되돌려 주게 한다. */
  private String measureNative() throws Exception {
    BlockingQueue<String> results = new ArrayBlockingQueue<>(1);
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/probe",
        exchange -> {
          // 스크립트를 그대로 감싼다. return 을 떼어내면 IIFE 가 undefined 를 돌려주고
          // 빈 본문이 올라온다 — 세 측정이 같은 식을 쓴다는 것이 이 비교의 전제다.
          String page =
              "<html><body>측정 중… 이 창은 자동으로 닫힙니다.<script>"
                  + "var v = (function(){"
                  + FINGERPRINT_SCRIPT
                  + "})();"
                  + "fetch('/result', {method:'POST', body: v});"
                  + "</script></body></html>";
          byte[] body = page.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.createContext(
        "/result",
        exchange -> {
          String body =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          results.offer(body);
          exchange.sendResponseHeaders(204, -1);
          exchange.close();
        });
    server.start();

    Path profile = probeProfile("t2-native");
    Process chrome = null;
    try {
      String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/probe";
      chrome = NativeChromeLoginLauncher.launch(profile, url);
      String measured = results.poll(60, TimeUnit.SECONDS);
      assertNotNull(measured, "순정 Chrome 이 측정값을 돌려주지 않았다.");
      return measured;
    } finally {
      if (chrome != null) {
        chrome.destroy();
      }
      killChromeUsing(profile);
      server.stop(0);
    }
  }

  private String measureSelenium(ChromeOptions options, boolean stealth) {
    WebDriverManager manager = new WebDriverManager();
    WebDriver driver = new ChromeDriver(options);
    try {
      if (stealth) {
        manager.applyStealth(driver);
      }
      driver.get("about:blank");
      return (String) ((JavascriptExecutor) driver).executeScript(FINGERPRINT_SCRIPT);
    } finally {
      driver.quit();
    }
  }

  // ------------------------------------------------------------------
  // T4 — chromedriver 149+ 의 pipe 전환이 기동을 막는가
  // ------------------------------------------------------------------

  @Test
  @DisplayName("T4 — --remote-debugging-port=0 없이 기동되는가")
  void t4LaunchWithoutRemoteDebuggingPort() throws Exception {
    Path profile = probeProfile("t4");
    ChromeOptions options = new WebDriverManager().buildChromeOptions(false, profile);

    String outcome;
    WebDriver driver = null;
    try {
      driver = new ChromeDriver(options);
      outcome = "기동 성공 — --remote-debugging-port=0 없이도 문제없다.";
    } catch (RuntimeException e) {
      outcome =
          "기동 실패 ("
              + e.getClass().getSimpleName()
              + "): "
              + firstLine(e.getMessage())
              + "\n→ --remote-debugging-port=0 을 붙여야 한다. 진단 결정타는 "
              + profile.resolve("chrome_debug.log");
    } finally {
      if (driver != null) {
        driver.quit();
      }
    }
    record("T4-remote-debugging-port.txt", "[T4] " + outcome + "\n");
    System.out.println("[T4] " + outcome);
  }

  // ------------------------------------------------------------------

  private static Path probeProfile(String name) throws IOException {
    Path dir = Paths.get("build", "probe-profiles", name);
    Files.createDirectories(dir);
    return dir;
  }

  /** 프로브가 띄운 Chrome 만 정리한다. 사용자의 브라우저는 건드리지 않는다. */
  private static void killChromeUsing(Path profile) {
    try {
      ProcessBuilder builder =
          new ProcessBuilder(
                  "powershell",
                  "-NoProfile",
                  "-Command",
                  "Get-CimInstance Win32_Process | Where-Object {"
                      + " $_.Name -eq 'chrome.exe' -and $_.CommandLine"
                      + " -and $_.CommandLine.Contains($env:PROBE_PROFILE)"
                      + " } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }")
              .redirectErrorStream(true);
      builder.environment().put("PROBE_PROFILE", profile.toAbsolutePath().toString());
      builder.start().waitFor(30, TimeUnit.SECONDS);
      NativeChromeLoginLauncher.awaitProfileRelease(
          profile, Duration.ofSeconds(10), Duration.ofMillis(200));
    } catch (Exception ignore) {
      // 정리 실패는 측정 결과에 영향이 없다.
    }
  }

  private static String firstLine(String message) {
    if (message == null) {
      return "(메시지 없음)";
    }
    int newline = message.indexOf('\n');
    return newline < 0 ? message : message.substring(0, newline);
  }
}

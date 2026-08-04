package com.jiniebox.jangbogo.svc.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 세션 쿠키가 브라우저 재시작을 넘기는가 — T3 실패의 공통 원인 규명.
 *
 * <h2>왜 이 실험이 필요한가</h2>
 *
 * <p>ssg 와 oasis 가 <b>같은 방식으로</b> T3 에 실패했다. 둘 다 순정 Chrome 으로도 재시작 뒤 로그인 화면으로 밀렸고, 프로필에 남은 것은 분석·추적
 * 쿠키뿐이었다. 두 사이트가 우연히 같을 리 없으니 공통 원인을 의심해야 한다.
 *
 * <p>유력한 가설: <b>인증이 세션 쿠키로 이뤄지고, Chrome 은 기본 설정에서 창을 닫을 때 세션 쿠키를 버린다.</b> 그렇다면 실패의 원인은 사이트도 엔진도 아니고
 * <b>브라우저 설정</b>이며, Chrome 의 "중단한 위치에서 계속하기"({@code session.restore_on_startup=1})를 켜면 세션 쿠키가 보존된다.
 *
 * <p>이 실험은 <b>실계정이 필요 없다.</b> 로컬 페이지가 세션 쿠키와 영속 쿠키를 하나씩 심고, 창을 정상 종료한 뒤 무엇이 남았는지 본다. 설정을 켠 프로필과 끈
 * 프로필을 나란히 재서 A/B 로 가른다.
 *
 * <p>영속 쿠키는 <b>양성 대조군</b>이다. 그것마저 안 남으면 측정 자체가 고장 난 것이므로, 세션 쿠키가 없다는 결과를 신뢰할 수 없다.
 *
 * @author KIUNSEA
 */
@Tag("probe")
class SessionCookieSurvivalProbe {

  /** Chrome 시작 동작. 1 = 중단한 위치에서 계속하기. */
  private static final String RESTORE_PREFERENCES = "{\"session\":{\"restore_on_startup\":1}}";

  private static final String SESSION_COOKIE = "probe_session";
  private static final String PERSISTENT_COOKIE = "probe_persistent";

  @Test
  @DisplayName("세션 쿠키 생존 A/B — 기본 설정 vs '중단한 위치에서 계속하기'")
  void sessionCookieSurvivalAcrossRestart() throws Exception {
    Map<String, String> verdicts = new LinkedHashMap<>();

    Result plain = measure("cookie-survival-plain", false);
    Result restore = measure("cookie-survival-restore", true);

    verdicts.put("기본 설정", plain.describe());
    verdicts.put("계속하기 켬", restore.describe());

    StringBuilder report = new StringBuilder();
    report.append("[세션 쿠키 생존] 브라우저 정상 종료 후 무엇이 남는가 (실계정 불필요)\n\n");
    verdicts.forEach((k, v) -> report.append(String.format("  %-12s : %s%n", k, v)));
    report.append('\n');
    report.append("해석 : ");
    if (!plain.persistentSurvived() || !restore.persistentSurvived()) {
      report.append("영속 쿠키(양성 대조군)마저 안 남았다 → 측정이 고장 났다.\n");
      report.append("       세션 쿠키 결과를 신뢰할 수 없다. 종료가 정상 종료였는지부터 확인할 것.\n");
    } else if (!plain.sessionSurvived() && restore.sessionSurvived()) {
      report.append("가설 적중 — 세션 쿠키는 기본 설정에서 사라지고, '계속하기' 를 켜면 남는다.\n");
      report.append("       그렇다면 ssg·oasis 의 T3 실패 원인은 사이트도 엔진도 아니라 브라우저 설정이다.\n");
      report.append("       프로필을 만들 때 이 설정을 켜 주면 경로 A 가 성립할 수 있다.\n");
    } else if (!plain.sessionSurvived() && !restore.sessionSurvived()) {
      report.append("설정을 켜도 세션 쿠키가 남지 않는다 → 이 경로로는 해결되지 않는다.\n");
      report.append("       T3 실패의 원인은 다른 곳에 있다. 사이트가 영속 로그인을 안 주는 쪽이 남는다.\n");
    } else {
      report.append("기본 설정에서도 세션 쿠키가 남았다 → 가설이 틀렸다.\n");
      report.append("       세션 쿠키 소실은 T3 실패의 원인이 아니다.\n");
    }
    record("SESSION-COOKIE-SURVIVAL.txt", report.toString());
    System.out.println(report);

    assertTrue(
        plain.persistentSurvived() && restore.persistentSurvived(),
        "영속 쿠키가 안 남았다 — 측정이 고장 났으므로 어떤 결론도 내지 않는다. " + report);
  }

  /** 한 프로필에 대해 심고·닫고·확인한다. */
  private Result measure(String profileName, boolean enableRestore) throws Exception {
    Path profile = Paths.get("build", "probe-profiles", profileName);
    deleteRecursively(profile);
    Files.createDirectories(profile.resolve(WebDriverManager.PROFILE_DIRECTORY));

    if (enableRestore) {
      // Chrome 이 처음 뜰 때 이 파일을 읽어 설정으로 삼는다. 나머지 항목은 Chrome 이 채운다.
      Files.writeString(
          profile.resolve(WebDriverManager.PROFILE_DIRECTORY).resolve("Preferences"),
          RESTORE_PREFERENCES,
          StandardCharsets.UTF_8);
    }

    BlockingQueue<String> planted = new ArrayBlockingQueue<>(1);
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/plant",
        exchange -> {
          String page =
              "<html><body>쿠키를 심는 중…<script>"
                  // 만료를 주지 않으면 세션 쿠키다 — 창을 닫으면 사라지는 종류.
                  + "document.cookie='"
                  + SESSION_COOKIE
                  + "=1; path=/';"
                  // 양성 대조군. 이것이 안 남으면 측정 자체가 고장 난 것이다.
                  + "document.cookie='"
                  + PERSISTENT_COOKIE
                  + "=1; path=/; max-age=3600';"
                  + "fetch('/done', {method:'POST', body: document.cookie});"
                  + "</script></body></html>";
          byte[] body = page.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.createContext(
        "/done",
        exchange -> {
          planted.offer(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          exchange.sendResponseHeaders(204, -1);
          exchange.close();
        });
    server.start();

    String confirmation;
    try {
      String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/plant";
      NativeChromeLoginLauncher.launch(profile, url);
      confirmation = planted.poll(60, TimeUnit.SECONDS);
      // 쿠키가 디스크로 내려갈 여유를 주고 정상 종료한다.
      Thread.sleep(3000);
      closeGracefully(profile);
      NativeChromeLoginLauncher.awaitProfileRelease(
          profile, Duration.ofSeconds(60), Duration.ofMillis(500));
    } finally {
      server.stop(0);
    }

    Map<String, Boolean> survived = readCookies(profile);

    // 디스크만 보면 관측 지점이 틀릴 수 있다. Chrome 이 세션 쿠키를 복원한다면 그것은
    // Cookies DB 가 아니라 재기동한 브라우저의 메모리에 있다. 그래서 실제로 다시 띄워
    // 페이지가 document.cookie 를 읽게 한다 — 사이트가 보는 것과 같은 관점이다.
    String afterRestart = relaunchAndReadCookies(profile);

    return new Result(
        confirmation != null && confirmation.contains(SESSION_COOKIE),
        survived.getOrDefault(SESSION_COOKIE, false),
        survived.getOrDefault(PERSISTENT_COOKIE, false),
        afterRestart != null && afterRestart.contains(SESSION_COOKIE),
        afterRestart != null && afterRestart.contains(PERSISTENT_COOKIE));
  }

  /** 프로필을 다시 띄워 브라우저가 실제로 들고 있는 쿠키를 읽는다. */
  private String relaunchAndReadCookies(Path profile) throws Exception {
    BlockingQueue<String> seen = new ArrayBlockingQueue<>(1);
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/verify",
        exchange -> {
          String page =
              "<html><body>확인 중…<script>"
                  + "fetch('/seen', {method:'POST', body: document.cookie || '(비어 있음)'});"
                  + "</script></body></html>";
          byte[] body = page.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.createContext(
        "/seen",
        exchange -> {
          seen.offer(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          exchange.sendResponseHeaders(204, -1);
          exchange.close();
        });
    server.start();
    try {
      // 포트가 매번 달라도 상관없다 — 쿠키는 호스트(127.0.0.1) 단위라 포트를 가리지 않는다.
      NativeChromeLoginLauncher.launch(
          profile, "http://127.0.0.1:" + server.getAddress().getPort() + "/verify");
      String value = seen.poll(60, TimeUnit.SECONDS);
      closeGracefully(profile);
      NativeChromeLoginLauncher.awaitProfileRelease(
          profile, Duration.ofSeconds(60), Duration.ofMillis(500));
      return value;
    } finally {
      server.stop(0);
    }
  }

  /**
   * 창을 <b>정상 종료</b>한다.
   *
   * <p>강제 종료하면 쿠키가 디스크에 내려가지 않아 측정이 무의미해진다. {@code CloseMainWindow} 는 사람이 창을 닫는 것과 같은 경로다.
   */
  private static void closeGracefully(Path profile) throws Exception {
    ProcessBuilder builder =
        new ProcessBuilder(
                "powershell",
                "-NoProfile",
                "-Command",
                "$ids = Get-CimInstance Win32_Process | Where-Object {"
                    + " $_.Name -eq 'chrome.exe' -and $_.CommandLine"
                    + " -and $_.CommandLine.Contains($env:PROBE_PROFILE)"
                    + " } | Select-Object -ExpandProperty ProcessId;"
                    + " Get-Process -Id $ids -ErrorAction SilentlyContinue"
                    + " | Where-Object { $_.MainWindowTitle }"
                    + " | ForEach-Object { $_.CloseMainWindow() | Out-Null }")
            .redirectErrorStream(true);
    builder.environment().put("PROBE_PROFILE", profile.toAbsolutePath().toString());
    builder.start().waitFor(30, TimeUnit.SECONDS);
  }

  private static Map<String, Boolean> readCookies(Path profile) throws Exception {
    Map<String, Boolean> found = new LinkedHashMap<>();
    Path cookies =
        profile.resolve(WebDriverManager.PROFILE_DIRECTORY).resolve("Network").resolve("Cookies");
    if (!Files.isRegularFile(cookies)) {
      return found;
    }
    Path copy = Files.createTempFile("survival", ".db");
    try {
      Files.copy(cookies, copy, StandardCopyOption.REPLACE_EXISTING);
      try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + copy.toAbsolutePath());
          Statement st = conn.createStatement();
          ResultSet rs =
              st.executeQuery("SELECT name FROM cookies WHERE host_key LIKE '%127.0.0.1%'")) {
        while (rs.next()) {
          found.put(rs.getString("name"), true);
        }
      }
    } finally {
      Files.deleteIfExists(copy);
    }
    return found;
  }

  private static void deleteRecursively(Path dir) throws IOException {
    if (!Files.exists(dir)) {
      return;
    }
    try (var walk = Files.walk(dir)) {
      walk.sorted((a, b) -> b.getNameCount() - a.getNameCount())
          .forEach(
              p -> {
                try {
                  Files.deleteIfExists(p);
                } catch (IOException ignore) {
                  // 남은 파일은 다음 실행이 덮어쓴다
                }
              });
    }
  }

  private static void record(String name, String body) throws IOException {
    Path dir = Paths.get("build", "probe-artifacts");
    Files.createDirectories(dir);
    Path file = dir.resolve(name);
    Files.writeString(file, body, StandardCharsets.UTF_8);
    System.out.println("[probe] 기록: " + file.toAbsolutePath());
  }

  /** 한 프로필의 측정 결과. */
  private record Result(
      boolean plantedOk,
      boolean sessionInDb,
      boolean persistentInDb,
      boolean sessionAfterRestart,
      boolean persistentAfterRestart) {

    /** 브라우저가 재기동 뒤 실제로 들고 있는지 — 사이트가 보는 것과 같은 관점이다. */
    boolean sessionSurvived() {
      return sessionAfterRestart;
    }

    boolean persistentSurvived() {
      return persistentAfterRestart;
    }

    String describe() {
      return "심기="
          + (plantedOk ? "성공" : "실패")
          + " / 재기동 후 세션 쿠키="
          + (sessionAfterRestart ? "있음" : "없음")
          + " / 재기동 후 영속 쿠키="
          + (persistentAfterRestart ? "있음" : "없음")
          + " (디스크: 세션="
          + (sessionInDb ? "Y" : "N")
          + ", 영속="
          + (persistentInDb ? "Y" : "N")
          + ")";
    }
  }
}

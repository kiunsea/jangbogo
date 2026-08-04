package com.jiniebox.jangbogo.svc.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;

/**
 * 사람이 로그인하는 <b>순정 Chrome 에 밖에서 붙어</b> 세션을 뜰 수 있는가 (Phase 5-15 · 실계정 불필요).
 *
 * <h2>왜 이것부터 재는가</h2>
 *
 * <p>ADR-0001 은 캡처 방법 후보 (b) — 순정 chrome.exe 를 {@code --remote-debugging-port} 로 띄우고 CDP 로 붙어 쿠키만
 * 뜬다 — 를 <b>"유력하나 미측정"</b> 으로 남겼다. 5-15 의 나머지(계정 연결 화면·저장·암호화)는 전부 이 한 가지가 되는지에 얹혀 있다. 되지 않으면 설계가
 * 통째로 바뀐다.
 *
 * <h2>왜 실계정이 필요 없는가</h2>
 *
 * <p>재려는 것은 <b>몰이 로그인을 주는가</b>가 아니라 <b>붙어서 세션 쿠키를 뜰 수 있는가</b>다. 로컬 페이지가 세션 쿠키를 하나 심으면 그것으로 충분하다 —
 * 우리가 옮기려는 인증 쿠키와 같은 종류(만료 없는 세션 스코프)다.
 *
 * <h2>대조군과 양성 대조군</h2>
 *
 * <ul>
 *   <li><b>대조군</b> — 붙기 <i>전</i>과 <i>후</i>의 {@code navigator.webdriver}. 붙는 순간 자동화 표식이 되살아나면 '순정
 *       브라우저에서 로그인한다'는 전제가 깨진다.
 *   <li><b>양성 대조군</b> — Selenium 이 <i>띄운</i> Chrome 도 같은 페이지로 잰다. 여기서 {@code true} 가 나와야 "순정에서
 *       false" 라는 결과를 믿을 수 있다. 이것이 없으면 측정기가 항상 false 를 뱉는 것과 구분되지 않는다.
 * </ul>
 *
 * <p>쿠키 <b>값</b>은 출력하지 않는다. 이 프로브의 값은 무해하지만 같은 코드가 실계정에서도 돈다.
 *
 * @author KIUNSEA
 */
@Tag("probe")
class SessionCaptureProbe {

  private static final String SESSION_COOKIE = "probe_session";
  private static final String PERSISTENT_COOKIE = "probe_persistent";

  private static final Duration WAIT = Duration.ofSeconds(30);
  private static final Duration POLL = Duration.ofMillis(200);

  /** 페이지가 보낸 보고. 어느 팔에서 온 것인지 tag 로 가른다. */
  private final List<String> reports = new CopyOnWriteArrayList<>();

  /**
   * 페이지가 자기 시점의 지문을 보고한다.
   *
   * <p>한 축만 보면 "표식이 없다" 는 결론을 믿을 수 없다. 자동화 브라우저와 순정 브라우저가 <b>어느 축에서 갈리는지</b> 를 나열해야 '차이가 UA 하나뿐인가'
   * 같은 질문에 답할 수 있다.
   *
   * <p>{@code ua} 는 브라우저가 <b>주장하는</b> 버전이고 {@code uaFull}·{@code brands} 는 클라이언트 힌트가 내는 <b>실제</b>
   * 버전이다. 둘이 어긋나면 사이트는 정교한 분석 없이도 자기모순을 본다.
   *
   * <p>값에 구분자가 섞이지 않도록 {@code |} 는 치환해서 보낸다.
   */
  private static final String FINGERPRINT_JS =
      """
      (async function () {
        var f = [];
        function put(k, v) { f.push(k + '=' + String(v).replace(/[|]/g, '/')); }
        put('webdriver', navigator.webdriver);
        var m = navigator.userAgent.match(/Chrome\\/[0-9.]+/);
        put('ua', m ? m[0] : '-');
        put('uaTail', navigator.userAgent.split(' ').pop());
        put('platform', navigator.platform);
        put('langs', navigator.languages.join(','));
        put('plugins', navigator.plugins.length);
        put('cores', navigator.hardwareConcurrency);
        put('chromeKeys', (typeof window.chrome === 'object' && window.chrome)
            ? Object.keys(window.chrome).sort().join('+') : '-');
        try {
          var b = navigator.userAgentData.brands
              .map(function (x) { return x.brand + '/' + x.version; }).sort().join(' ');
          put('brands', b);
          var hv = await navigator.userAgentData.getHighEntropyValues(['uaFullVersion']);
          put('uaFull', hv.uaFullVersion);
        } catch (e) { put('brands', '-'); put('uaFull', '-'); }
        try {
          var gl = document.createElement('canvas').getContext('webgl');
          var ext = gl.getExtension('WEBGL_debug_renderer_info');
          put('webgl', gl.getParameter(ext.UNMASKED_RENDERER_WEBGL));
        } catch (e) { put('webgl', '-'); }
        try {
          var st = await navigator.permissions.query({ name: 'notifications' });
          put('perm', Notification.permission + '/' + st.state);
        } catch (e) { put('perm', '-'); }
        put('cookies', document.cookie || '-');
        fetch('/report', { method: 'POST', body: PROBE_TAG + '|' + f.join('|') });
      })();
      """;

  /** 지문 비교에서 뺄 축 — 팔마다 다른 것이 정상인 값. */
  private static final List<String> FINGERPRINT_IGNORED = List.of("cookies");

  @Test
  @DisplayName("순정 Chrome 에 붙어 살아 있는 세션을 뜬다 — 대조군·양성 대조군 동시 측정")
  void attachToNativeChromeAndCaptureLiveSession() throws Exception {
    Path profile = Paths.get("build", "probe-profiles", "session-capture-native");
    deleteRecursively(profile);
    Files.createDirectories(profile);

    HttpServer server = startPageServer();
    String base = "http://127.0.0.1:" + server.getAddress().getPort();

    Map<String, String> verdicts = new LinkedHashMap<>();
    int port = NativeChromeLoginLauncher.NO_DEBUG_PORT;
    String browser = null;
    String nativeWebdriver = null;
    String afterAttachWebdriver = null;
    String afterAttachPageWebdriver = null;
    SessionSnapshot snapshot = SessionSnapshot.empty();
    boolean browserSurvivedQuit = false;
    boolean closedByApi = false;
    boolean profileDetectedInUse = false;
    String seleniumWebdriver = null;
    String headlessUa = null;
    List<String> fingerprintDiffs = List.of();
    String maskedSeleniumWebdriver = null;
    String roundTripCookies = null;

    // ── 0. 대조군 — 같은 순정 chrome.exe 를 포트 없이 띄운다 ─────────────────────
    //
    // 이 팔이 없으면 표식이 보일 때 '포트 탓' 인지 '런처 탓' 인지 가를 수 없다.
    // ADR T2 는 순정 = false 라고 적었지만 그것은 다른 측정이므로 같은 실행에서 다시 잰다.
    String plainReport =
        measureNativeReport("session-capture-plain", NativeChromeLoginLauncher.NO_DEBUG_PORT);
    String plainNativeWebdriver = fieldOf(plainReport, "webdriver");
    verdicts.put("[대조군] 순정 Chrome (포트 없음)", describe(plainNativeWebdriver));
    verdicts.put(
        "[대조군] UA 문자열 / 실제 버전(힌트)",
        describe(fieldOf(plainReport, "ua")) + "  /  " + describe(fieldOf(plainReport, "brands")));

    String afterStealthWebdriver = null;
    Process chrome = null;
    try {
      // ── 1. 순정 chrome.exe 를 디버깅 포트와 함께 띄운다 ──────────────────────────
      chrome =
          NativeChromeLoginLauncher.launch(
              profile, base + "/page?tag=plant", NativeChromeLoginLauncher.ANY_DEBUG_PORT);

      port = NativeChromeLoginLauncher.awaitDevToolsPort(profile, WAIT, POLL);
      verdicts.put("디버깅 포트", port > 0 ? "열림 (" + port + ")" : "열리지 않음");

      // '이미 열려 있는 프로필' 가드는 이 판정이 참이라는 전제 위에 서 있다.
      // 전제가 깨지면 가드는 있으나 마나이므로, 브라우저가 도는 동안 여기서 함께 잰다.
      profileDetectedInUse = NativeChromeLoginLauncher.isProfileInUse(profile);
      verdicts.put("브라우저가 도는 동안 프로필 사용 중 판정", profileDetectedInUse ? "true" : "false (가드가 죽는다)");

      if (port > 0) {
        browser = NativeChromeLoginLauncher.awaitDevToolsBrowser(port, WAIT, POLL);
      }
      verdicts.put("DevTools 응답", browser == null ? "없음" : browser);

      // 페이지가 쿠키를 심고 자기 시점의 navigator.webdriver 를 보고한다 (붙기 전 = 대조군).
      String planted = awaitReport("plant", WAIT);
      nativeWebdriver = fieldOf(planted, "webdriver");
      verdicts.put("순정 기동 시 navigator.webdriver", describe(nativeWebdriver));

      // ── 2. 그 브라우저에 밖에서 붙어 세션을 뜬다 ────────────────────────────────
      if (port > 0 && browser != null) {
        WebDriver attached =
            new WebDriverManager()
                .attachToRunningChrome(NativeChromeLoginLauncher.debuggerAddress(port));
        try {
          snapshot = SessionTransfer.capture(attached);

          // 붙은 뒤 같은 문서에서 다시 본다 (대조군 2).
          afterAttachWebdriver =
              String.valueOf(
                  ((org.openqa.selenium.JavascriptExecutor) attached)
                      .executeScript("return navigator.webdriver"));

          // 새 문서에서도 표식이 살아나지 않는지 본다 (대조군 3).
          attached.navigate().to(base + "/page?tag=after-attach");
          String again = awaitReport("after-attach", WAIT);
          afterAttachPageWebdriver = fieldOf(again, "webdriver");

          // 표식이 보인다면 붙은 김에 지울 수 있는지 — 후보 (b) 를 살릴 수 있는지의 판정이다.
          // 사람이 로그인 페이지로 가는 것은 이 다음이므로, 새 문서부터 적용되면 충분하다.
          new WebDriverManager().applyStealth(attached);
          attached.navigate().to(base + "/page?tag=after-stealth");
          afterStealthWebdriver = fieldOf(awaitReport("after-stealth", WAIT), "webdriver");
        } finally {
          // 붙었던 드라이버를 놓는다. 이때 사람의 브라우저가 같이 죽으면 캡처 흐름이 성립하지 않는다.
          attached.quit();
        }
        // 한 번만 재면 '닫히는 중' 을 '살아 있음' 으로 읽을 수 있다. 여유를 두고 두 번 다 응답할 때만 인정한다.
        boolean firstLook = NativeChromeLoginLauncher.devToolsBrowser(port) != null;
        TimeUnit.SECONDS.sleep(2);
        browserSurvivedQuit = firstLook && NativeChromeLoginLauncher.devToolsBrowser(port) != null;

        // 놓아도 안 닫힌다면 닫는 수단이 따로 있어야 한다 — 그러지 않으면 로그인된 브라우저와
        // 인증 없는 디버깅 포트가 그대로 남는다. 그 수단이 실제로 닫는지 여기서 잰다.
        if (browserSurvivedQuit) {
          WebDriverManager manager = new WebDriverManager();
          WebDriver second =
              manager.attachToRunningChrome(NativeChromeLoginLauncher.debuggerAddress(port));
          manager.closeAttachedBrowser(second);
          TimeUnit.SECONDS.sleep(2);
          closedByApi = NativeChromeLoginLauncher.devToolsBrowser(port) == null;
        }
      }

      verdicts.put(
          "캡처한 쿠키",
          snapshot.size() + "개 / 세션 쿠키 " + (snapshot.hasCookie(SESSION_COOKIE) ? "있음" : "없음"));
      verdicts.put("붙은 뒤 navigator.webdriver (같은 문서)", describe(afterAttachWebdriver));
      verdicts.put("붙은 뒤 navigator.webdriver (새 문서)", describe(afterAttachPageWebdriver));
      verdicts.put("마스킹 주입 뒤 navigator.webdriver", describe(afterStealthWebdriver));
      verdicts.put("드라이버를 놓은 뒤 브라우저", browserSurvivedQuit ? "살아 있음" : "닫힘");
      verdicts.put(
          "closeAttachedBrowser 로 닫기",
          browserSurvivedQuit ? (closedByApi ? "닫힘" : "안 닫힘") : "해당 없음");
    } finally {
      closeNativeChrome(profile, chrome);
      server.stop(0);
    }

    // ── 3. 양성 대조군 — Selenium 이 띄운 Chrome 은 표식이 보여야 한다 ──────────────
    HttpServer control = startPageServer();
    String controlBase = "http://127.0.0.1:" + control.getAddress().getPort();
    try {
      WebDriver selenium = new WebDriverManager().getWebDriver("chrome");
      try {
        selenium.get(controlBase + "/page?tag=selenium");
        seleniumWebdriver = fieldOf(awaitReport("selenium", WAIT), "webdriver");
      } finally {
        selenium.quit();
      }
      verdicts.put("[양성 대조군] Selenium 이 띄운 Chrome", describe(seleniumWebdriver));

      // ── 4. 후보 (a) — Selenium 이 띄우되 우리 마스킹을 적용한 브라우저 ────────────
      //
      // 사람이 이 창에서 로그인하는 방식이다(ADR 후보 (a)). 여기가 깨끗하면 포트·붙기 없이
      // 5-15 가 성립한다 — 드라이버가 처음부터 우리 것이라 캡처에 붙을 필요도 없다.
      String maskedReport = null;
      Path maskedProfile = Paths.get("build", "probe-profiles", "session-capture-masked");
      deleteRecursively(maskedProfile);
      Files.createDirectories(maskedProfile);

      WebDriver masked = new WebDriverManager().getWebDriver("chrome", maskedProfile);
      try {
        masked.get(controlBase + "/page?tag=masked");
        maskedReport = awaitReport("masked", WAIT);
        maskedSeleniumWebdriver = fieldOf(maskedReport, "webdriver");
      } finally {
        masked.quit();
      }
      verdicts.put("[후보 a] Selenium + 마스킹", describe(maskedSeleniumWebdriver));

      // ── 4-1. 지문 비교 — '차이가 UA 하나뿐인가' 에 답하려면 축을 나열해야 한다 ──────
      Map<String, String> plainFp = fingerprintOf(plainReport);
      Map<String, String> maskedFp = fingerprintOf(maskedReport);
      fingerprintDiffs = fingerprintDiff(plainFp, maskedFp);
      int compared =
          (int) plainFp.keySet().stream().filter(k -> !FINGERPRINT_IGNORED.contains(k)).count();
      verdicts.put(
          "[지문] 순정 vs 후보 a",
          fingerprintDiffs.isEmpty()
              ? "비교한 " + compared + "축 전부 일치"
              : compared + "축 중 " + fingerprintDiffs.size() + "축 불일치");
      for (String diff : fingerprintDiffs) {
        verdicts.put(
            "   └ " + diff.substring(0, diff.indexOf(':')),
            diff.substring(diff.indexOf(':') + 1).trim());
      }

      // ── 4-2. headless 는 UA 를 스스로 드러내는가 ─────────────────────────────
      //
      // UA 하드코딩을 지우려면 headless 가 'HeadlessChrome' 을 광고하지 않아야 한다.
      // 수집기는 headless 로도 돌기 때문이다.
      WebDriver headless = new WebDriverManager().getWebDriver("chrome", null);
      try {
        headless.get(controlBase + "/page?tag=headless");
        headlessUa = fieldOf(awaitReport("headless", WAIT), "ua");
      } finally {
        headless.quit();
      }
      verdicts.put("[headless] UA 문자열", describe(headlessUa));

      // ── 5. 왕복 — 뜬 세션을 다른 브라우저에 넣으면 그 브라우저가 들고 있는가 ──────────
      if (!snapshot.isEmpty()) {
        Path scratch = Paths.get("build", "probe-profiles", "session-capture-inject");
        deleteRecursively(scratch);
        Files.createDirectories(scratch);

        WebDriver fresh = new WebDriverManager().getWebDriver("chrome", scratch);
        try {
          // 넣기 전에 도메인에 한 번 들른다. about:blank 상태에서 넣으면 조용히 무시될 수 있다.
          fresh.get(controlBase + "/page?tag=preinject");
          awaitReport("preinject", WAIT);

          SessionTransfer.inject(fresh, snapshot);

          fresh.navigate().to(controlBase + "/page?tag=verify");
          roundTripCookies = fieldOf(awaitReport("verify", WAIT), "cookies");
        } finally {
          fresh.quit();
        }
      }
      verdicts.put(
          "[왕복] 주입한 브라우저가 세션 쿠키를 들고 있는가",
          roundTripCookies == null
              ? "확인 못 함"
              : (roundTripCookies.contains(SESSION_COOKIE) ? "예" : "아니오"));
    } finally {
      control.stop(0);
    }

    // ── 보고 ────────────────────────────────────────────────────────────────
    // 여기서 가장 조심할 것은 '재지 못한 것' 을 '재서 false' 로 접어 넣는 일이다.
    // 그러면 재지도 않은 축에 대해 확신에 찬 문장이 산출물에 남는다.
    boolean captured = snapshot.hasCookie(SESSION_COOKIE);
    boolean controlValid = isTrue(seleniumWebdriver);
    boolean roundTrip = roundTripCookies != null && roundTripCookies.contains(SESSION_COOKIE);

    StringBuilder report = new StringBuilder();
    report.append("[5-15] 순정 Chrome 세션 캡처 성립 여부 (실계정 불필요)\n\n");
    verdicts.forEach((k, v) -> report.append(String.format("  %-40s : %s%n", k, v)));
    report.append('\n');
    report.append("해석 : ");
    if (!measured(seleniumWebdriver)) {
      report.append("양성 대조군을 재지 못했다(보고 없음). 측정기가 도는지부터 확인해야 한다.\n");
      report.append("       어떤 축도 판정하지 않는다.\n");
    } else if (!controlValid) {
      report.append("양성 대조군이 성립하지 않았다 — Selenium 이 띄운 Chrome 에서도 표식이 안 보인다.\n");
      report.append("       측정기가 고장 났을 수 있으므로 어떤 결론도 내지 않는다.\n");
    } else if (!captured) {
      report.append("붙어서 세션을 뜨지 못했다. 이 경로로는 5-15 가 성립하지 않는다.\n");
      report.append("       DevTools 포트·붙기·CDP 중 어디서 끊겼는지 위 표로 가른다.\n");
    } else if (!roundTrip) {
      report.append("떴지만 다른 브라우저에서 살아나지 않았다 — 주입 경로에서 끊겼다.\n");
      report.append("       캡처 형식(만료·도메인)이나 주입 시점(도메인 선방문)을 봐야 한다.\n");
    } else {
      report.append("캡처·주입 왕복이 성립한다");
      report.append(
          browserSurvivedQuit ? " (드라이버를 놓아도 사람의 창은 남는다).\n" : " — 드라이버를 놓을 때 창이 같이 닫혔다.\n");
      if (browserSurvivedQuit) {
        report.append("       ▸ 남은 창을 닫는 수단: ");
        report.append(
            closedByApi ? "closeAttachedBrowser 로 닫힌다.\n" : "닫지 못했다 — 로그인된 브라우저와 포트가 남는다.\n");
      }
      report.append('\n');
      report.append("       자동화 표식 귀속:\n");
      if (!measured(plainNativeWebdriver) || !measured(nativeWebdriver)) {
        report.append("       ▸ 두 팔 중 하나를 재지 못했다 — 귀속을 판정하지 않는다.\n");
      } else if (isFalse(plainNativeWebdriver) && isTrue(nativeWebdriver)) {
        report.append("       ▸ 포트 없는 순정은 false, 포트를 열면 true — ");
        report.append("**디버깅 포트 자체가 표식을 켠다.**\n");
        report.append("         ADR 후보 (b) 의 전제('브라우저 자체는 순정')가 이 지점에서 깨진다.\n");
      } else if (!isFalse(plainNativeWebdriver)) {
        report.append("       ▸ 포트 없는 순정에서도 표식이 보인다 — 포트 탓이 아니다.\n");
        report.append("         런처 인자나 이 PC 의 Chrome 설정을 봐야 한다(ADR T2 와 어긋난다).\n");
      } else {
        report.append("       ▸ 포트를 열어도 표식이 켜지지 않는다 — 후보 (b) 가 전제대로 성립한다.\n");
      }
      report
          .append("       ▸ 붙은 뒤 마스킹 주입: ")
          .append(
              mark(afterStealthWebdriver, "표식이 false 로 돌아온다 — 로그인 화면을 그 다음에 열면 가려진다.", "돌아오지 않는다."))
          .append('\n');
      report
          .append("       ▸ 후보 (a)(Selenium+마스킹): ")
          .append(mark(maskedSeleniumWebdriver, "깨끗하다. 포트·붙기 없이도 같은 결과를 얻는다.", "표식이 남는다."))
          .append('\n');
      report.append("       ▸ 지문 비교: ");
      if (fingerprintDiffs.isEmpty()) {
        report.append("순정과 갈리는 축이 없다 — 잰 범위 안에서 후보 (a) 는 순정과 구분되지 않는다.\n");
      } else {
        report.append("갈리는 축이 있다 → ").append(fingerprintDiffs).append('\n');
      }
      report
          .append("       ▸ headless UA: ")
          .append(
              measured(headlessUa) && !headlessUa.contains("Headless")
                  ? "스스로를 드러내지 않는다(" + headlessUa + ").\n"
                  : "확인 필요 — " + describe(headlessUa) + "\n");
    }
    record("SESSION-CAPTURE.txt", report.toString());
    System.out.println(report);

    // 재지 못한 축이 있으면 결론을 내지 않는다 — 그 자체가 실패다.
    assertTrue(measured(seleniumWebdriver), "[양성 대조군] 보고를 받지 못했다. " + report);
    assertTrue(measured(plainNativeWebdriver), "[대조군] 포트 없는 순정 Chrome 의 보고를 받지 못했다. " + report);
    assertTrue(measured(maskedSeleniumWebdriver), "[후보 a] 보고를 받지 못했다. " + report);

    assertTrue(
        profileDetectedInUse, "브라우저가 도는데 프로필이 비었다고 판정했다 — '이미 열려 있는 프로필' 가드가 죽는다. " + report);
    // 후보 (a) 가 순정과 갈리지 않는다는 것은 이제 계약이다. 누가 UA 를 다시 박으면 여기서 걸린다.
    assertTrue(fingerprintDiffs.isEmpty(), "순정과 갈리는 축이 생겼다: " + fingerprintDiffs + "\n" + report);
    assertTrue(
        measured(headlessUa) && !headlessUa.contains("Headless"),
        "headless 가 스스로를 드러낸다: " + describe(headlessUa) + "\n" + report);

    assertTrue(controlValid, "양성 대조군 실패 — Selenium 이 띄운 Chrome 에서도 표식이 안 보인다. " + report);
    assertTrue(captured, "순정 Chrome 에 붙어 세션을 뜨지 못했다. " + report);
    assertTrue(roundTrip, "뜬 세션이 다른 브라우저에서 살아나지 않았다. " + report);
  }

  /** 보고 한 줄을 축별 값으로 가른다. */
  private static Map<String, String> fingerprintOf(String report) {
    Map<String, String> out = new LinkedHashMap<>();
    if (report == null) {
      return out;
    }
    String[] parts = report.split("\\|");
    for (int i = 1; i < parts.length; i++) {
      int at = parts[i].indexOf('=');
      if (at > 0) {
        out.put(parts[i].substring(0, at), parts[i].substring(at + 1));
      }
    }
    return out;
  }

  /**
   * 두 지문이 갈리는 축을 나열한다.
   *
   * <p>"차이가 없다" 를 주장하려면 <b>무엇을 비교했는지</b>가 함께 있어야 한다. 그래서 갈린 축뿐 아니라 비교한 축 수도 함께 보고한다.
   */
  private static List<String> fingerprintDiff(Map<String, String> a, Map<String, String> b) {
    List<String> diffs = new ArrayList<>();
    List<String> keys = new ArrayList<>(a.keySet());
    for (String key : b.keySet()) {
      if (!keys.contains(key)) {
        keys.add(key);
      }
    }
    for (String key : keys) {
      if (FINGERPRINT_IGNORED.contains(key)) {
        continue;
      }
      String left = a.getOrDefault(key, "(없음)");
      String right = b.getOrDefault(key, "(없음)");
      if (!left.equals(right)) {
        diffs.add(key + ": 순정=" + left + " / 후보a=" + right);
      }
    }
    return diffs;
  }

  /** 재기는 했는가. null 은 '보고를 못 받았다' 이지 'false 로 측정됐다' 가 아니다. */
  private static boolean measured(String value) {
    return value != null && !value.isBlank();
  }

  private static boolean isTrue(String value) {
    return "true".equals(value);
  }

  private static boolean isFalse(String value) {
    return "false".equals(value);
  }

  /** 3상태를 문장으로. 재지 못한 축에 대해서는 단정하지 않는다. */
  private static String mark(String value, String whenFalse, String whenTrue) {
    if (!measured(value)) {
      return "재지 못했다 — 판정하지 않는다.";
    }
    return isFalse(value) ? whenFalse : whenTrue;
  }

  /**
   * 순정 chrome.exe 를 한 번 띄워 페이지가 보고한 한 줄을 그대로 돌려준다.
   *
   * <p>포트를 열고/열지 않고 두 번 재서 표식의 원인을 가르는 데 쓴다.
   *
   * @param profileName 프로필 디렉터리 이름
   * @param debugPort 디버깅 포트
   * @return 보고 한 줄. 못 받으면 null
   */
  private String measureNativeReport(String profileName, int debugPort) throws Exception {
    Path profile = Paths.get("build", "probe-profiles", profileName);
    deleteRecursively(profile);
    Files.createDirectories(profile);

    HttpServer server = startPageServer();
    Process chrome = null;
    try {
      String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/page?tag=" + profileName;
      chrome = NativeChromeLoginLauncher.launch(profile, url, debugPort);
      return awaitReport(profileName, WAIT);
    } finally {
      closeNativeChrome(profile, chrome);
      server.stop(0);
    }
  }

  // ── 로컬 페이지 ──────────────────────────────────────────────────────────

  /**
   * 쿠키를 심고 자기 시점의 상태를 보고하는 페이지.
   *
   * <p>{@code tag=plant} 일 때만 심는다. 나머지는 지금 보이는 것을 그대로 보고한다.
   */
  private HttpServer startPageServer() throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/page",
        exchange -> {
          String query = exchange.getRequestURI().getQuery();
          String tag = query == null ? "" : query.replace("tag=", "");
          String plant =
              "plant".equals(tag)
                  // 만료를 주지 않으면 세션 쿠키다 — 우리가 옮기려는 인증 쿠키와 같은 종류.
                  ? "document.cookie='"
                      + SESSION_COOKIE
                      + "=1; path=/';"
                      + "document.cookie='"
                      + PERSISTENT_COOKIE
                      + "=1; path=/; max-age=3600';"
                  : "";
          String page =
              "<html><body>probe "
                  + tag
                  + "<script>var PROBE_TAG='"
                  + tag
                  + "';"
                  + plant
                  + FINGERPRINT_JS
                  + "</script></body></html>";
          byte[] body = page.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.createContext(
        "/report",
        exchange -> {
          reports.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          exchange.sendResponseHeaders(204, -1);
          exchange.close();
        });
    server.start();
    return server;
  }

  /** 해당 tag 의 보고가 올 때까지 기다린다. */
  private String awaitReport(String tag, Duration timeout) throws InterruptedException {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      for (String line : reports) {
        if (line.startsWith(tag + "|")) {
          return line;
        }
      }
      TimeUnit.MILLISECONDS.sleep(POLL.toMillis());
    }
    return null;
  }

  private static String fieldOf(String report, String key) {
    if (report == null) {
      return null;
    }
    for (String part : report.split("\\|")) {
      if (part.startsWith(key + "=")) {
        return part.substring(key.length() + 1);
      }
    }
    return null;
  }

  private static String describe(String value) {
    return value == null ? "확인 못 함" : value;
  }

  // ── 정리 ────────────────────────────────────────────────────────────────

  /**
   * 띄운 브라우저를 닫는다.
   *
   * <p>정상 종료를 먼저 시도한다 — 캡처는 이미 끝났으므로 디스크 반영이 목적은 아니지만, 사람이 쓰는 것과 같은 경로로 닫는 편이 뒤에 남는 것이 적다.
   */
  private static void closeNativeChrome(Path profile, Process launched) {
    String path = profile.toAbsolutePath().toString();
    // 이 필터가 넓어지면 개발자의 개인 Chrome 창까지 강제 종료된다.
    // 프로브 전용 경로가 아니면 아예 돌리지 않는다.
    if (path.isBlank() || !path.replace('\\', '/').contains("/build/probe-profiles/")) {
      System.out.println("[probe] 프로브 프로필이 아니라 종료를 건너뛴다: " + path);
      return;
    }
    try {
      ProcessBuilder builder =
          new ProcessBuilder(
                  "powershell",
                  "-NoProfile",
                  "-Command",
                  "$ids = Get-CimInstance Win32_Process | Where-Object {"
                      + " $_.Name -eq 'chrome.exe' -and $_.CommandLine"
                      + " -and $_.CommandLine.Contains($env:PROBE_PROFILE)"
                      + " } | Select-Object -ExpandProperty ProcessId;"
                      + " if ($ids) { Stop-Process -Id $ids -Force -ErrorAction SilentlyContinue }")
              .redirectErrorStream(true);
      builder.environment().put("PROBE_PROFILE", profile.toAbsolutePath().toString());
      builder.start().waitFor(30, TimeUnit.SECONDS);
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
    }
    if (launched != null && launched.isAlive()) {
      launched.destroy();
    }
  }

  private static void deleteRecursively(Path dir) throws IOException {
    if (!Files.exists(dir)) {
      return;
    }
    try (var walk = Files.walk(dir)) {
      walk.sorted(Collections.reverseOrder())
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
}

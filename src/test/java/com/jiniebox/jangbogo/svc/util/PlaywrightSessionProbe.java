package com.jiniebox.jangbogo.svc.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Cookie;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Playwright 로 세션 재사용을 다시 잰다 — T3 의 마지막 배제 절차.
 *
 * <h2>왜 다시 재는가</h2>
 *
 * <p>Selenium 판정은 이미 났고, 대조군(순정 Chrome)이 같은 결과를 내서 <b>엔진 탓이 아니라</b>고 결론지었다. 그래도 Playwright 를 실제로 재
 * 두는 이유는 Phase 5B 를 <b>영구 미착수로 닫으려면</b> 추론이 아니라 측정이 필요하기 때문이다.
 *
 * <h2>두 가지를 한 번에 잰다</h2>
 *
 * <p>사람이 한 번 로그인하면 그 상태로 서로 다른 두 질문에 답한다.
 *
 * <ol>
 *   <li><b>프로필 재사용</b> — Selenium 과 같은 방식. 프로필 디렉터리를 다시 열면 세션이 살아 있는가. 예상은 실패다(순정 Chrome 도 실패했다).
 *   <li><b>{@code storageState}</b> — <b>이쪽이 진짜 다른 것.</b> 브라우저가 <b>살아 있는 동안</b> 쿠키를 통째로 떠서 나중에 새
 *       컨텍스트에 주입한다. "닫으면 세션 쿠키가 사라진다"는 벽을 우회하므로, 성립한다면 전략 자체가 바뀐다.
 * </ol>
 *
 * <p>로그인 감지는 <b>탭을 열지 않고</b> 한다 — {@code context.request()} 가 컨텍스트의 쿠키를 그대로 쓰는 HTTP 요청을 보내므로, 사람이
 * 로그인하는 화면을 건드리지 않고 회원 페이지 접근 가능 여부만 주기적으로 확인할 수 있다.
 *
 * <p>설치된 Chrome 을 쓴다({@code channel=chrome}) — 프로필을 만든 브라우저와 같아야 한다.
 *
 * @author KIUNSEA
 */
@Tag("probe")
class PlaywrightSessionProbe {

  static final String MALL_PROPERTY = "jangbogo.probe.mall";

  /** 사람이 로그인할 때까지 기다리는 상한. 감지되면 즉시 다음으로 넘어간다. */
  private static final Duration LOGIN_WAIT = Duration.ofMinutes(40);

  private static final Duration POLL = Duration.ofSeconds(10);

  private enum Target {
    SSG(
        "ssg",
        "https://www.ssg.com/",
        "https://www.ssg.com/myssg/productMng/purchaseList.ssg?menu=purchaseList"),
    OASIS("oasis", "https://www.oasis.co.kr/", "https://www.oasis.co.kr/myPage/orderList");

    final String id;
    final String homeUrl;
    final String memberUrl;

    Target(String id, String homeUrl, String memberUrl) {
      this.id = id;
      this.homeUrl = homeUrl;
      this.memberUrl = memberUrl;
    }

    static Target current() {
      String raw = System.getProperty(MALL_PROPERTY);
      if (raw == null || raw.isBlank()) {
        throw new IllegalStateException("대상 몰이 지정되지 않았다. -D" + MALL_PROPERTY + "=ssg|oasis");
      }
      for (Target t : values()) {
        if (t.id.equals(raw.trim().toLowerCase())) {
          return t;
        }
      }
      throw new IllegalArgumentException("알 수 없는 대상 몰: " + raw);
    }

    /** Selenium 판정에 쓴 프로필과 섞지 않는다 — 비교 대상이 오염되면 안 된다. */
    Path profileDir() {
      return SessionProfilePolicy.profileDir(id + "-pw");
    }

    Path storageStateFile() throws IOException {
      return artifactDir().resolve("storage-state-" + id + ".json");
    }
  }

  private static Path artifactDir() throws IOException {
    Path dir = Paths.get("build", "probe-artifacts");
    Files.createDirectories(dir);
    return dir;
  }

  @Test
  @DisplayName("PW-1 — 로그인 감지 후 storageState 를 뜬다 (사람 로그인 필요)")
  void pw1LoginAndCaptureStorageState() throws Exception {
    Target target = Target.current();
    Path profile = target.profileDir();
    Files.createDirectories(profile);

    System.out.println();
    System.out.println("==================================================================");
    System.out.println(" Playwright 로 " + target.id.toUpperCase() + " 창을 띄웁니다. 로그인해 주세요.");
    System.out.println();
    System.out.println("  프로필 : " + profile.toAbsolutePath());
    System.out.println();
    System.out.println("  * 로그인만 하시면 됩니다. 창은 닫지 마세요 —");
    System.out.println("    로그인이 감지되면 제가 세션을 뜨고 알아서 닫습니다.");
    System.out.println("  * 감지는 " + POLL.toSeconds() + "초마다, 탭을 열지 않고 조용히 확인합니다.");
    System.out.println("==================================================================");
    System.out.println();

    boolean detected = false;
    List<String> cookieSummary = new ArrayList<>();

    try (Playwright playwright = Playwright.create()) {
      BrowserContext context =
          playwright
              .chromium()
              .launchPersistentContext(
                  profile,
                  new BrowserType.LaunchPersistentContextOptions()
                      .setHeadless(false)
                      .setChannel("chrome"));
      try {
        context.newPage().navigate(target.homeUrl);

        long deadline = System.nanoTime() + LOGIN_WAIT.toNanos();
        while (System.nanoTime() < deadline) {
          if (memberPageReachable(context, target)) {
            detected = true;
            break;
          }
          Thread.sleep(POLL.toMillis());
        }

        if (detected) {
          // 브라우저가 살아 있는 지금이 유일한 기회다. 닫히면 세션 쿠키는 사라진다.
          context.storageState(
              new BrowserContext.StorageStateOptions().setPath(target.storageStateFile()));
          cookieSummary = summarize(context.cookies());
        }
      } finally {
        context.close();
      }
    }

    StringBuilder report = new StringBuilder();
    report.append("[PW-1] 로그인 감지 · storageState 캡처 — ").append(target.id).append("\n\n");
    report.append("프로필      : ").append(profile.toAbsolutePath()).append('\n');
    report.append("로그인 감지 : ").append(detected).append('\n');
    if (detected) {
      report.append("storageState: ").append(target.storageStateFile()).append('\n');
      report.append("\n캡처된 쿠키 (이름·영속 여부만, 값은 읽지 않는다):\n");
      cookieSummary.forEach(c -> report.append("  ").append(c).append('\n'));
      long sessionScoped = cookieSummary.stream().filter(c -> c.contains("|세션")).count();
      report.append("\n세션 쿠키 ").append(sessionScoped).append("개가 함께 캡처됐다.\n");
      report.append("이것들은 브라우저를 닫으면 사라지지만 storageState 에는 남는다 —\n");
      report.append("PW-3 이 그것을 주입해 실제로 통하는지 판정한다.\n");
    } else {
      report.append("\n시간 안에 로그인이 감지되지 않았다. 다시 실행할 것.\n");
    }
    record("PW-1-capture-" + target.id + ".txt", report.toString());
    System.out.println(report);

    assertTrue(detected, "로그인이 감지되지 않았다 — 회원 페이지가 계속 로그인으로 밀렸다.");
  }

  @Test
  @DisplayName("PW-2 — 프로필 재사용: 같은 프로필을 Playwright 로 다시 연다")
  void pw2ProfileReuse() throws Exception {
    Target target = Target.current();
    Path profile = target.profileDir();

    boolean reachable;
    try (Playwright playwright = Playwright.create()) {
      BrowserContext context =
          playwright
              .chromium()
              .launchPersistentContext(
                  profile,
                  new BrowserType.LaunchPersistentContextOptions()
                      .setHeadless(true)
                      .setChannel("chrome"));
      try {
        reachable = memberPageReachable(context, target);
      } finally {
        context.close();
      }
    }

    StringBuilder report = new StringBuilder();
    report.append("[PW-2] 프로필 재사용 — ").append(target.id).append("\n\n");
    report.append("회원 페이지 도달 : ").append(reachable).append("\n\n");
    report.append("해석 : ");
    report.append(
        reachable
            ? "Playwright 프로필 재사용이 성립한다. Selenium·순정과 다른 결과다 — 재확인 필요.\n"
            : "Selenium·순정 Chrome 과 같은 결과다. 프로필 재사용은 엔진과 무관하게 성립하지 않는다.\n");
    record("PW-2-profile-reuse-" + target.id + ".txt", report.toString());
    System.out.println(report);
  }

  @Test
  @DisplayName("PW-3 — storageState 주입: 갓 만든 컨텍스트에 세션을 넣는다")
  void pw3StorageStateInjection() throws Exception {
    Target target = Target.current();
    Path state = target.storageStateFile();
    assertTrue(Files.isRegularFile(state), "storageState 가 없다. PW-1 을 먼저 실행할 것: " + state);

    boolean reachable;
    try (Playwright playwright = Playwright.create()) {
      Browser browser =
          playwright
              .chromium()
              .launch(new BrowserType.LaunchOptions().setHeadless(true).setChannel("chrome"));
      try {
        // 프로필을 쓰지 않는다. 완전히 새 컨텍스트에 뜬 세션만 주입한다.
        BrowserContext context =
            browser.newContext(new Browser.NewContextOptions().setStorageStatePath(state));
        try {
          reachable = memberPageReachable(context, target);
        } finally {
          context.close();
        }
      } finally {
        browser.close();
      }
    }

    StringBuilder report = new StringBuilder();
    report.append("[PW-3] storageState 주입 — ").append(target.id).append("\n\n");
    report.append("회원 페이지 도달 : ").append(reachable).append("\n\n");
    report.append("해석 : ");
    if (reachable) {
      report.append("성립한다. 브라우저가 살아 있을 때 뜬 세션을 새 브라우저에 옮길 수 있다.\n");
      report.append("       '프로필을 재사용한다' 가 아니라 '세션을 떠서 옮긴다' 로 전략을 바꾸면\n");
      report.append("       영속 로그인을 주지 않는 몰에서도 자동 수집이 가능하다.\n");
      report.append("       다만 뜨는 시점이 사람의 로그인 직후여야 하고, 세션 수명만큼만 유효하다.\n");
    } else {
      report.append("성립하지 않는다. 세션을 옮겨도 사이트가 받아 주지 않는다.\n");
      report.append("       쿠키 외의 것(단말 바인딩·서버측 세션 고정 등)에 묶여 있을 수 있다.\n");
      report.append("       이 경로로도 안 되면 프로필 재사용 전략은 이 몰에서 닫힌다.\n");
    }
    record("PW-3-storage-state-" + target.id + ".txt", report.toString());
    System.out.println(report);
  }

  /**
   * 탭을 열지 않고 회원 페이지 접근 가능 여부를 본다.
   *
   * <p>{@code context.request()} 는 컨텍스트의 쿠키를 그대로 쓰는 HTTP 클라이언트다. 사람이 로그인하는 화면을 건드리지 않고 조용히 확인할 수
   * 있다. 리다이렉트를 따라간 최종 주소가 로그인 화면이면 아직 세션이 없는 것이다.
   */
  private static boolean memberPageReachable(BrowserContext context, Target target) {
    try {
      APIResponse response = context.request().get(target.memberUrl);
      String finalUrl = response.url();
      boolean pushedToLogin = finalUrl.toLowerCase().contains("login");
      response.dispose();
      return !pushedToLogin;
    } catch (RuntimeException e) {
      return false;
    }
  }

  /** 쿠키 이름·도메인·영속 여부만 남긴다. <b>값은 읽지 않는다.</b> */
  private static List<String> summarize(List<Cookie> cookies) {
    List<String> out = new ArrayList<>();
    for (Cookie c : cookies) {
      // Playwright 는 세션 쿠키의 만료를 -1 로 준다.
      boolean session = c.expires == null || c.expires < 0;
      out.add(c.domain + " " + c.name + " |" + (session ? "세션" : "영속"));
    }
    out.sort(String::compareTo);
    return out;
  }

  private static void record(String name, String body) throws IOException {
    Path file = artifactDir().resolve(name);
    Files.writeString(file, body, StandardCharsets.UTF_8);
    System.out.println("[probe] 기록: " + file.toAbsolutePath());
  }
}

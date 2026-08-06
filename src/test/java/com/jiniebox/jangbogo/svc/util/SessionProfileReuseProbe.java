package com.jiniebox.jangbogo.svc.util;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

/**
 * T3 — 사람이 만든 프로필의 로그인 상태가 Selenium 에서 유지되는가 (Phase 4A-3).
 *
 * <h2>무엇을 가르는 측정인가</h2>
 *
 * <p>계획서는 이 측정을 <b>엔진 질문</b>으로 규정한다 — "Selenium 단독으로 프로필 재사용이 성립하는가". 그런데 실패는 두 갈래로 온다.
 *
 * <ul>
 *   <li><b>세션이 프로필에 안 남는다</b> — 몰·로그인 쪽 문제. 엔진을 바꿔도 그대로다.
 *   <li><b>세션은 남았는데 Selenium 이라 거부된다</b> — 경로 A 의 실패. 이때만 5B 를 상정한다.
 * </ul>
 *
 * <p>둘을 가르려고 <b>순정 Chrome 대조군</b>(3단계)과 <b>쿠키 차분</b>(1단계)을 둔다. 이것 없이는 FAIL 하나로 잘못된 결론에 간다 — 실제로 이
 * 프로브의 첫 판이 그럴 뻔했다.
 *
 * <h2>실행</h2>
 *
 * <p>가운데에 <b>사람이 로그인하는 절차</b>가 있어 한 번에 돌 수 없다. 대상 몰은 {@code -Djangbogo.probe.mall} 로 고른다(기본 ssg).
 *
 * <pre>
 * 1) ./gradlew test -PincludeProbe --tests '*SessionProfileReuseProbe.step1*' -Djangbogo.probe.mall=ssg
 *    → 순정 Chrome 이 뜬다. 로그인하고 <b>마이페이지까지 직접 들어가 본 뒤</b> 창을 닫는다.
 * 2) ./gradlew test -PincludeProbe --tests '*SessionProfileReuseProbe.step2*'   (순정 대조군)
 * 3) ./gradlew test -PincludeProbe --tests '*SessionProfileReuseProbe.step3*'   (Selenium 판정)
 * </pre>
 *
 * <p><b>산출물에 개인정보가 담긴다.</b> 로그인된 화면의 스크린샷·DOM 에는 구매 내역이 들어 있다. 전부 {@code build/} 아래에만 쓰고, 이 저장소는
 * 공개이므로 <b>커밋하지 않는다</b>. 쿠키는 이름·영속 여부만 보고 <b>값은 읽지 않는다</b>.
 *
 * @author KIUNSEA
 */
@Tag("probe")
class SessionProfileReuseProbe {

  /** 대상 몰 선택. */
  static final String MALL_PROPERTY = "jangbogo.probe.mall";

  /** 사람이 로그인을 마칠 때까지 기다리는 상한. */
  private static final Duration LOGIN_TIMEOUT = Duration.ofMinutes(20);

  /**
   * 측정 대상. URL 과 로그인 신호는 <b>수집기가 실제로 쓰는 것</b>에서 가져왔다.
   *
   * <p>로그인 신호를 여러 개 두는 이유는 수집기의 판정이 취약해서다 — ssg 는 {@code #logoutBtn} 을 숨긴 채 DOM 에 남기고, oasis 는
   * nth-child 경로로 찾는다. 프로브는 그것을 그대로 베끼지 않고 <b>보이는 로그아웃 어포던스</b>라는 더 넓은 신호를 본다.
   */
  private enum Target {
    SSG(
        "ssg",
        "https://www.ssg.com/",
        "https://www.ssg.com/myssg/productMng/purchaseList.ssg?menu=purchaseList",
        "purchaseList",
        "ssg",
        List.of(By.id("logoutBtn"), By.cssSelector("a[href*='logout']"))),
    OASIS(
        "oasis",
        "https://www.oasis.co.kr/",
        "https://www.oasis.co.kr/myPage/orderList",
        "myPage",
        "oasis",
        List.of(By.cssSelector("a[href*='logout']"), By.xpath("//a[normalize-space()='로그아웃']")));

    private final String id;
    private final String homeUrl;
    private final String memberUrl;
    private final String memberUrlMarker;
    private final String cookieHostFragment;
    private final List<By> loggedInLocators;

    Target(
        String id,
        String homeUrl,
        String memberUrl,
        String memberUrlMarker,
        String cookieHostFragment,
        List<By> loggedInLocators) {
      this.id = id;
      this.homeUrl = homeUrl;
      this.memberUrl = memberUrl;
      this.memberUrlMarker = memberUrlMarker;
      this.cookieHostFragment = cookieHostFragment;
      this.loggedInLocators = loggedInLocators;
    }

    /**
     * 대상 몰. <b>기본값을 두지 않는다.</b>
     *
     * <p>기본값이 있으면 지정이 전달되지 않았을 때 조용히 엉뚱한 몰로 돈다. 실제로 그렇게 당했다 — gradle 명령줄의 {@code -D} 가 포크된 테스트 JVM
     * 까지 가지 않아, oasis 를 지정했는데 ssg 브라우저가 뜨고 ssg 기록을 덮어썼다. 지정이 안 왔으면 아무것도 하지 않고 멈추는 편이 낫다.
     */
    static Target current() {
      String raw = System.getProperty(MALL_PROPERTY);
      if (raw == null || raw.isBlank()) {
        throw new IllegalStateException(
            "대상 몰이 지정되지 않았다. -D"
                + MALL_PROPERTY
                + "=ssg|oasis 를 줄 것."
                + " (gradle 명령줄의 -D 는 build.gradle 의 systemProperty 통과가 있어야 테스트 JVM 에 닿는다)");
      }
      String requested = raw.trim().toLowerCase();
      for (Target t : values()) {
        if (t.id.equals(requested)) {
          System.out.println("[probe] 대상 몰 = " + t.id);
          return t;
        }
      }
      throw new IllegalArgumentException(
          "알 수 없는 대상 몰: " + requested + " (-D" + MALL_PROPERTY + "=ssg|oasis)");
    }

    Path profileDir() {
      return SessionProfilePolicy.profileDir(id);
    }
  }

  private static Path artifactDir() throws IOException {
    Path dir = Paths.get("build", "probe-artifacts");
    Files.createDirectories(dir);
    return dir;
  }

  // ==================================================================
  // 1단계 — 순정 Chrome 으로 사람이 로그인한다
  // ==================================================================

  /**
   * 창을 띄우고 <b>기다리지 않는다</b>.
   *
   * <p>기다리지 않는 것이 설계다. 앞선 판은 이 자리에서 20분을 기다렸는데, 사람이 그 안에 조작하지 못하면 테스트가 타임아웃으로 죽으면서 <b>Chrome 을 강제
   * 종료</b>했다. 그러면 로그인했더라도 쿠키가 디스크에 내려가지 않아 측정 자체가 무의미해진다. 사람을 시계와 경쟁시키면 안 된다.
   *
   * <p>대신 '이전' 쿠키 지문을 파일로 남겨 둔다. 사람이 자기 속도로 끝낸 뒤 {@link #step1bConfirmLogin()} 을 돌리면 그때 비교한다.
   */
  @Test
  @DisplayName("T3-1a — 순정 Chrome 을 띄우기만 한다 (기다리지 않는다)")
  void step1aLaunchForLogin() throws Exception {
    Target target = Target.current();
    Path profile = target.profileDir();
    Files.createDirectories(profile);

    if (NativeChromeLoginLauncher.isProfileInUse(profile)) {
      fail("이미 이 프로필로 Chrome 이 떠 있다. 그 창에서 작업하거나 닫고 다시 실행할 것.");
    }

    // 로그인이 '그 창에서' 일어났는지 나중에 확정하려면 이전 상태를 알아야 한다.
    Set<String> before = cookieFingerprints(profile, target.cookieHostFragment);
    Files.write(beforeSnapshotFile(target), before, StandardCharsets.UTF_8);

    NativeChromeLoginLauncher.launch(profile, target.homeUrl);

    System.out.println();
    System.out.println("==================================================================");
    System.out.println(" Chrome 을 띄웠습니다. " + target.id.toUpperCase() + " 에 로그인해 주세요.");
    System.out.println();
    System.out.println("  프로필 : " + profile.toAbsolutePath());
    System.out.println("  이전 쿠키 지문 " + before.size() + "개를 기록해 뒀습니다.");
    System.out.println();
    System.out.println("  1. 로그인합니다. '로그인 상태 유지' 류 옵션이 있으면 켜 주세요.");
    System.out.println("  2. 로그인 후 마이페이지(주문/구매내역)까지 직접 들어가 보세요.");
    System.out.println("  3. 그 다음 창을 닫아 주세요. 강제 종료는 하지 마세요.");
    System.out.println();
    System.out.println("  급할 것 없습니다 — 이 명령은 기다리지 않고 여기서 끝납니다.");
    System.out.println("==================================================================");
  }

  /** 사람이 끝낸 뒤 돌린다. 창이 닫혀 있어야 쿠키 저장소를 읽을 수 있다. */
  @Test
  @DisplayName("T3-1b — 로그인 전후 쿠키를 비교해 '그 창에서 로그인했는지' 를 확정한다")
  void step1bConfirmLogin() throws Exception {
    Target target = Target.current();
    Path profile = target.profileDir();

    Path snapshot = beforeSnapshotFile(target);
    if (!Files.isRegularFile(snapshot)) {
      fail("이전 지문이 없다. 1a 를 먼저 실행할 것: " + snapshot.toAbsolutePath());
    }
    if (NativeChromeLoginLauncher.isProfileInUse(profile)) {
      fail("Chrome 이 아직 프로필을 쥐고 있다. 창을 닫고 다시 실행할 것 — 쥔 채로는 쿠키를 읽을 수 없다.");
    }

    Set<String> before = new LinkedHashSet<>(Files.readAllLines(snapshot, StandardCharsets.UTF_8));
    Set<String> after = cookieFingerprints(profile, target.cookieHostFragment);
    Set<String> added = new LinkedHashSet<>(after);
    added.removeAll(before);
    long addedPersistent = added.stream().filter(c -> c.contains("|persistent")).count();
    long persistentValid = added.stream().filter(c -> c.contains("|유효")).count();

    StringBuilder report = new StringBuilder();
    report.append("[T3-1] 로그인 전후 쿠키 차분 — ").append(target.id).append("\n\n");
    report.append("프로필        : ").append(profile.toAbsolutePath()).append('\n');
    report.append("쿠키 (이전)   : ").append(before.size()).append("개\n");
    report.append("쿠키 (이후)   : ").append(after.size()).append("개\n");
    report
        .append("새로 생긴 쿠키 : ")
        .append(added.size())
        .append("개 (영속 ")
        .append(addedPersistent)
        .append("개, 그중 만료 유효 ")
        .append(persistentValid)
        .append("개)\n");
    report.append("\n새로 생긴 쿠키 (이름·영속 여부만, 값은 읽지 않는다):\n");
    for (String c : added) {
      report.append("  ").append(c).append('\n');
    }
    report.append('\n');
    if (added.isEmpty()) {
      report.append("해석 : 쿠키가 하나도 늘지 않았다 → 이 프로필 창에서 로그인이 일어나지 않았다.\n");
      report.append("       다른 Chrome 창에서 로그인했을 가능성을 먼저 확인할 것.\n");
    } else if (persistentValid == 0) {
      report.append("해석 : 새 쿠키가 있으나 만료 유효한 영속 쿠키가 없다 → 창을 닫으면 사라지는 세션이다.\n");
      report.append("       이 몰이 영속 로그인을 주지 않는다는 뜻이고, 그렇다면 프로필 재사용은\n");
      report.append("       엔진과 무관하게 성립하지 않는다. 2단계 대조군으로 확증할 것.\n");
    } else {
      report.append("해석 : 만료 유효한 영속 쿠키가 새로 생겼다 → 로그인이 프로필에 기록됐다.\n");
      report.append("       실제 유지 여부는 2단계(순정 대조군)가 판정한다.\n");
    }
    record("T3-1-cookie-diff-" + target.id + ".txt", report.toString());
    System.out.println(report);

    assertTrue(!added.isEmpty(), "쿠키가 전혀 늘지 않았다 — 이 프로필 창에서 로그인이 일어나지 않았다.");
  }

  /** 1a 가 남기고 1b 가 읽는 '이전' 지문. */
  private static Path beforeSnapshotFile(Target target) throws IOException {
    return artifactDir().resolve(".cookies-before-" + target.id + ".txt");
  }

  // ==================================================================
  // 2단계 — 순정 Chrome 대조군. 세션이 브라우저 재시작을 넘기는가
  // ==================================================================

  @Test
  @DisplayName("T3-2 — 같은 프로필을 순정 Chrome 으로 다시 열어 세션 생존을 본다")
  void step2NativeControl() throws Exception {
    Target target = Target.current();
    Path profile = target.profileDir();
    if (NativeChromeLoginLauncher.isProfileInUse(profile)) {
      fail("프로필을 다른 Chrome 이 쓰고 있다. 그 창을 닫고 다시 실행할 것 — 배제 (ii).");
    }

    Process chrome = NativeChromeLoginLauncher.launch(profile, target.memberUrl);
    String title;
    try {
      Thread.sleep(12_000);
      title = windowTitleOf(profile);
    } finally {
      if (chrome.isAlive()) {
        chrome.destroy();
      }
      killChromeUsing(profile);
    }

    // 읽지 못한 제목으로는 아무 결론도 내지 않는다. 깨진 문자열에서 '로그인' 을 찾으면
    // 없다고 나오고, 그 순간 판정이 조용히 뒤집힌다 — 실제로 한 번 그렇게 틀렸다.
    assertTrue(
        !title.contains("�") && !title.startsWith("("),
        "창 제목을 제대로 읽지 못했다. 이 상태로는 판정할 수 없다. 실제: [" + title + "]");

    boolean pushedToLogin = title.contains("로그인") || title.toLowerCase().contains("login");

    StringBuilder report = new StringBuilder();
    report.append("[T3-2] 순정 Chrome 대조군 — ").append(target.id).append("\n\n");
    report.append("요청 주소   : ").append(target.memberUrl).append('\n');
    report.append("창 제목     : ").append(title).append('\n');
    report.append("로그인 밀림 : ").append(pushedToLogin).append("\n\n");
    report.append("해석 : ");
    if (pushedToLogin) {
      report.append("순정 Chrome 도 밀렸다 → 세션이 브라우저 재시작을 넘기지 못한다.\n");
      report.append("       Selenium 탓이 아니다. 경로 A(엔진)의 실패가 아니라 몰의 로그인 정책 문제다.\n");
      report.append("       이 FAIL 은 5B(Playwright) 를 상정할 근거가 되지 못한다 — 같은 벽이다.\n");
    } else {
      report.append("순정은 통과한다 → 세션은 재시작을 넘긴다.\n");
      report.append("       이제 3단계에서 Selenium 이 같은 일을 할 수 있는지가 진짜 엔진 질문이다.\n");
    }
    record("T3-2-native-control-" + target.id + ".txt", report.toString());
    System.out.println(report);
  }

  // ==================================================================
  // 3단계 — Selenium 으로 같은 프로필을 연다 (엔진 판정)
  // ==================================================================

  @Test
  @DisplayName("T3-3 — 같은 프로필을 Selenium 으로 열어 회원 페이지 직행 여부를 판정한다")
  void step3SeleniumVerdict() throws Exception {
    Target target = Target.current();
    Path profile = target.profileDir();
    if (!Files.isDirectory(profile)) {
      fail("프로필이 없다. 1단계를 먼저 실행할 것: " + profile.toAbsolutePath());
    }
    if (NativeChromeLoginLauncher.isProfileInUse(profile)) {
      fail("프로필을 다른 Chrome 이 쓰고 있다. 그 창을 닫고 다시 실행할 것 — 배제 (ii).");
    }

    List<String> observations = new ArrayList<>();
    boolean homeLoggedIn;
    boolean memberReached;

    WebDriver driver = new WebDriverManager().getWebDriver("chrome", profile);
    try {
      driver.get(target.homeUrl);
      Thread.sleep(2500);

      // 존재만이 아니라 '보이는지' 까지 본다. ssg 홈은 로그아웃 어포던스를 DOM 에 두고
      // JS 로 감춘다 — findElements 만 쓰면 로그아웃 상태에서도 true 가 나온다.
      //
      // 여기서 나온 실측치(존재=1, 보임=0)가 근거가 되어 수집기의 Ssg.isSignedIn 도 '보임'
      // 기준으로 고쳐졌다(별건으로 남겨 뒀던 그 건이다). 이제 프로브와 수집기가 같은 기준을
      // 쓰므로, 아래 판정을 존재 기준으로 되돌리면 프로브가 수집기보다 후하게 판정해 실사이트
      // 관측과 수집 결과가 어긋난다.
      int present = 0;
      int visible = 0;
      for (By locator : target.loggedInLocators) {
        List<org.openqa.selenium.WebElement> found = driver.findElements(locator);
        present += found.size();
        visible += (int) found.stream().filter(org.openqa.selenium.WebElement::isDisplayed).count();
      }
      homeLoggedIn = visible > 0;
      observations.add("홈 로그아웃 어포던스 : 존재=" + present + ", 보임=" + visible);
      observations.add("홈 최종 URL          : " + driver.getCurrentUrl());

      driver.navigate().to(target.memberUrl);
      Thread.sleep(3500);
      String memberUrl = driver.getCurrentUrl();
      memberReached =
          memberUrl.contains(target.memberUrlMarker) && !memberUrl.toLowerCase().contains("login");
      observations.add("회원 페이지 최종 URL : " + memberUrl);
      observations.add("로그인 화면으로 밀림  : " + memberUrl.toLowerCase().contains("login"));

      saveScreenshot(driver, "T3-3-member-" + target.id + ".png");
      saveDomSnapshot(driver, "T3-3-member-" + target.id + ".html");
    } finally {
      driver.quit();
    }

    boolean pass = homeLoggedIn && memberReached;

    StringBuilder report = new StringBuilder();
    report.append("[T3-3] Selenium 세션 프로필 재사용 판정 — ").append(target.id).append("\n\n");
    report
        .append("측정 시각 : ")
        .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
        .append('\n');
    report.append("프로필    : ").append(profile.toAbsolutePath()).append("\n\n");
    for (String line : observations) {
      report.append("  ").append(line).append('\n');
    }
    report.append("\n판정 : ").append(pass ? "PASS" : "FAIL").append('\n');
    if (!pass) {
      report.append("\n⚠ 이 FAIL 만으로 엔진을 탓할 수 없다. 2단계(순정 대조군)와 반드시 함께 읽을 것.\n");
      report.append('\n').append(exclusionChecklist(profile, target));
    }
    record("T3-3-verdict-" + target.id + ".txt", report.toString());

    System.out.println("[T3-3] 판정 = " + (pass ? "PASS" : "FAIL"));
    observations.forEach(o -> System.out.println("        " + o));

    assertTrue(pass, "T3 FAIL — 산출물의 배제 절차를 따를 것. 2단계 대조군이 원인을 가른다.");
  }

  // ==================================================================

  /** 계획서가 요구하는 원인 4종 배제 절차. 자동으로 알 수 있는 것은 값을 채워 둔다. */
  private static String exclusionChecklist(Path profile, Target target) {
    StringBuilder sb = new StringBuilder();
    sb.append("[배제 절차] 아래 4종이 전부 배제되어야만 Phase 5B 를 상정한다.\n\n");
    sb.append("  (i)   영속 로그인 미성립 ('로그인 상태 유지' 미체크 또는 몰이 제공하지 않음)\n");
    sb.append("        → 1단계 쿠키 차분 산출물 참조\n\n");
    sb.append("  (ii)  프로필 락 / 좀비 chrome\n");
    sb.append("        → 측정 시점 사용 중 = ")
        .append(NativeChromeLoginLauncher.isProfileInUse(profile))
        .append("\n\n");
    sb.append("  (iii) chromedriver 149+ pipe 이슈\n");
    sb.append("        → T4 결과 참조. 기동 실패였다면 ")
        .append(profile.resolve("chrome_debug.log"))
        .append(" 가 결정타\n\n");
    sb.append("  (iv)  사이트 세션 · 동시 로그인 정책\n");
    sb.append("        → 2단계 순정 대조군이 밀렸다면 여기가 원인이다 (엔진 아님)\n");
    return sb.toString();
  }

  /**
   * 쿠키 지문. <b>값은 읽지 않는다</b> — 이름·영속 여부·만료 유효성만 본다.
   *
   * <p>이것을 로그인 전후로 비교하면 "그 창에서 로그인이 일어났는가"와 "그 로그인이 영속인가"가 갈린다. 갯수만 세면 추적 쿠키와 구분되지 않아 잘못 단정하게 된다.
   */
  private static Set<String> cookieFingerprints(Path profile, String hostFragment)
      throws IOException {
    Set<String> result = new LinkedHashSet<>();
    Path cookies =
        profile.resolve(WebDriverManager.PROFILE_DIRECTORY).resolve("Network").resolve("Cookies");
    if (!Files.isRegularFile(cookies)) {
      return result;
    }
    Path copy = null;
    try {
      copy = Files.createTempFile("probe-cookies", ".db");
      Files.copy(cookies, copy, StandardCopyOption.REPLACE_EXISTING);
      long nowChromeEpoch = (System.currentTimeMillis() + 11644473600000L) * 1000L;
      try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + copy.toAbsolutePath());
          Statement st = conn.createStatement();
          ResultSet rs =
              st.executeQuery(
                  "SELECT host_key, name, is_persistent, expires_utc FROM cookies"
                      + " WHERE host_key LIKE '%"
                      + hostFragment.replace("'", "")
                      + "%' ORDER BY host_key, name")) {
        while (rs.next()) {
          boolean persistent = rs.getInt("is_persistent") == 1;
          boolean future = rs.getLong("expires_utc") > nowChromeEpoch;
          result.add(
              rs.getString("host_key")
                  + " "
                  + rs.getString("name")
                  + " |"
                  + (persistent ? "persistent" : "session")
                  + (persistent ? (future ? " |유효" : " |만료") : ""));
        }
      }
    } catch (Exception e) {
      // 읽기 실패를 데이터로 섞지 않는다. 섞으면 그것이 '새 쿠키 1개' 로 세어져
      // "영속 쿠키가 없다 → 이 몰은 영속 로그인을 안 준다" 같은 확신에 찬 오답이 나온다.
      // 실제로 한 번 그렇게 틀렸다 — Chrome 이 파일을 쥔 채로 읽으려 했을 때다.
      throw new IOException(
          "쿠키 저장소를 읽지 못했다("
              + e.getClass().getSimpleName()
              + "). Chrome 이 프로필을 쥐고 있으면 읽을 수 없다 — 창을 닫고 다시 시도할 것.",
          e);
    } finally {
      try {
        if (copy != null) {
          Files.deleteIfExists(copy);
        }
      } catch (IOException ignore) {
        // 임시 파일 정리 실패는 결과에 영향이 없다
      }
    }
    return result;
  }

  /**
   * 우리 프로필을 쓰는 Chrome 창의 제목. 사용자의 다른 창은 보지 않는다.
   *
   * <p>인코딩을 양쪽에서 UTF-8 로 못박는다. PowerShell 은 기본적으로 콘솔 코드페이지(이 PC 는 CP949)로 내보내는데 그것을 UTF-8 로 읽으면 한글이
   * 깨지고, 깨진 제목에서 '로그인' 을 찾으면 없다고 나와 판정이 정반대로 뒤집힌다.
   */
  private static String windowTitleOf(Path profile) throws Exception {
    ProcessBuilder builder =
        new ProcessBuilder(
                "powershell",
                "-NoProfile",
                "-Command",
                "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8;"
                    + " $ids = Get-CimInstance Win32_Process | Where-Object {"
                    + " $_.Name -eq 'chrome.exe' -and $_.CommandLine"
                    + " -and $_.CommandLine.Contains($env:PROBE_PROFILE)"
                    + " } | Select-Object -ExpandProperty ProcessId;"
                    + " Get-Process -Id $ids -ErrorAction SilentlyContinue"
                    + " | Where-Object { $_.MainWindowTitle }"
                    + " | Select-Object -ExpandProperty MainWindowTitle")
            .redirectErrorStream(true);
    builder.environment().put("PROBE_PROFILE", profile.toAbsolutePath().toString());

    Process process = builder.start();
    String output;
    try (java.io.InputStream in = process.getInputStream()) {
      output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    process.waitFor(30, TimeUnit.SECONDS);
    return output.isBlank() ? "(창 제목을 읽지 못했다)" : output.trim();
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

  private static void saveScreenshot(WebDriver driver, String name) {
    try {
      byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
      Path file = artifactDir().resolve(name);
      Files.write(file, png);
      System.out.println("[probe] 스크린샷: " + file.toAbsolutePath() + " (" + png.length + " bytes)");
    } catch (Exception e) {
      System.out.println("[probe] 스크린샷 실패: " + e.getClass().getSimpleName());
    }
  }

  private static void saveDomSnapshot(WebDriver driver, String name) {
    try {
      Path file = artifactDir().resolve(name);
      Files.writeString(file, driver.getPageSource(), StandardCharsets.UTF_8);
      System.out.println("[probe] DOM 스냅샷: " + file.toAbsolutePath());
    } catch (Exception e) {
      System.out.println("[probe] DOM 스냅샷 실패: " + e.getClass().getSimpleName());
    }
  }

  private static void record(String name, String body) throws IOException {
    Path file = artifactDir().resolve(name);
    Files.writeString(file, body, StandardCharsets.UTF_8);
    System.out.println("[probe] 기록: " + file.toAbsolutePath());
  }

  /** 만료 시각 표기 헬퍼. 산출물 가독성용. */
  @SuppressWarnings("unused")
  private static String chromeEpochToIso(long chromeEpochMicros) {
    return Instant.ofEpochMilli(chromeEpochMicros / 1000L - 11644473600000L).toString();
  }

  /** SQL 예외를 삼키지 않고 드러내기 위한 좁은 통로. */
  @SuppressWarnings("unused")
  private static int countRows(Statement st, String sql) throws SQLException {
    try (ResultSet rs = st.executeQuery(sql)) {
      return rs.next() ? rs.getInt(1) : 0;
    }
  }
}

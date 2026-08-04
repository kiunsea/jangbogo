package com.jiniebox.jangbogo.svc.util;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chromium.ChromiumDriver;

/**
 * 세션 이관을 <b>Selenium</b> 으로 할 수 있는가 — Playwright 의존성이 실제로 필요한지 가른다.
 *
 * <h2>왜 이 질문이 결정적인가</h2>
 *
 * <p>Playwright 로 잰 결과는 이렇게 갈렸다.
 *
 * <ul>
 *   <li>프로필 재사용 — <b>실패</b>. Selenium·순정 Chrome 과 같다.
 *   <li>{@code storageState} 주입 — <b>성공</b>. 프로필 없는 새 브라우저에 세션이 옮겨졌다.
 * </ul>
 *
 * <p>즉 갈린 것은 <b>엔진이 아니라 방식</b>이다. 그렇다면 같은 방식을 Selenium 으로도 할 수 있어야 한다 — 쿠키를 넣는 일에 특별한 엔진이 필요할 이유가
 * 없기 때문이다. 된다면 Playwright 의존성은 <b>필요 없다</b>(계획서 수용 기준 5 · 결정 3 을 지킬 수 있다).
 *
 * <p>주입은 CDP {@code Network.setCookies} 로 한다. Selenium 의 {@code addCookie} 는 <b>현재 열린 도메인</b>에만 넣을
 * 수 있어 여러 호스트의 쿠키를 넣으려면 도메인마다 옮겨 다녀야 하지만, CDP 는 한 번에 넣는다.
 *
 * <p>Playwright 가 뜬 {@code storageState} 파일을 입력으로 쓴다 — <b>파일 형식만 빌리는 것</b>이고 실행에는 Playwright 가 관여하지
 * 않는다. 이관 자체를 Selenium 만으로 하게 되면 이 입력도 Selenium 이 만들면 된다(그건 별건).
 *
 * <p>쿠키 <b>값</b>은 출력하지 않는다. 실제 세션 토큰이다.
 *
 * @author KIUNSEA
 */
@Tag("probe")
class SeleniumSessionTransferProbe {

  static final String MALL_PROPERTY = "jangbogo.probe.mall";

  private static final Map<String, String[]> TARGETS =
      Map.of(
          "ssg",
              new String[] {
                "https://www.ssg.com/",
                "https://www.ssg.com/myssg/productMng/purchaseList.ssg?menu=purchaseList",
                "purchaseList"
              },
          "oasis",
              new String[] {
                "https://www.oasis.co.kr/", "https://www.oasis.co.kr/myPage/orderList", "myPage"
              });

  @Test
  @DisplayName("ST-1 — Playwright 없이 Selenium 만으로 세션을 옮긴다")
  void transferSessionWithSeleniumOnly() throws Exception {
    String mall = System.getProperty(MALL_PROPERTY);
    if (mall == null || mall.isBlank()) {
      fail("대상 몰이 지정되지 않았다. -D" + MALL_PROPERTY + "=ssg|oasis");
    }
    String[] target = TARGETS.get(mall.trim().toLowerCase());
    if (target == null) {
      fail("알 수 없는 대상 몰: " + mall);
    }
    String homeUrl = target[0];
    String memberUrl = target[1];
    String memberMarker = target[2];

    Path stateFile = artifactDir().resolve("storage-state-" + mall + ".json");
    assertTrue(Files.isRegularFile(stateFile), "세션 파일이 없다: " + stateFile);

    List<Map<String, Object>> cookies = readCookies(stateFile);
    assertTrue(!cookies.isEmpty(), "세션 파일에 쿠키가 없다 — 이관할 것이 없다.");

    // 프로필을 쓰지 않는다. 갓 만든 임시 프로필에 세션만 넣는다.
    Path scratch = Paths.get("build", "probe-profiles", "selenium-transfer-" + mall);
    Files.createDirectories(scratch);

    String finalUrl;
    boolean reached;
    WebDriver driver = new WebDriverManager().getWebDriver("chrome", scratch);
    try {
      if (!(driver instanceof ChromiumDriver chromium)) {
        fail("CDP 를 지원하지 않는 드라이버다: " + driver.getClass().getSimpleName());
        return;
      }
      // 쿠키를 넣기 전에 도메인에 한 번 들러 둔다. CDP 는 도메인 밖에서도 넣을 수 있지만,
      // 빈 about:blank 상태에서 넣으면 일부 브라우저 버전이 조용히 무시한다.
      driver.get(homeUrl);
      Thread.sleep(1500);

      chromium.executeCdpCommand("Network.setCookies", Map.of("cookies", cookies));

      driver.navigate().to(memberUrl);
      Thread.sleep(3000);
      finalUrl = driver.getCurrentUrl();
      reached = finalUrl.contains(memberMarker) && !finalUrl.toLowerCase().contains("login");

      saveScreenshot(driver, "ST-1-member-" + mall + ".png");
    } finally {
      driver.quit();
    }

    StringBuilder report = new StringBuilder();
    report.append("[ST-1] Selenium 단독 세션 이관 — ").append(mall).append("\n\n");
    report.append("주입한 쿠키 수   : ").append(cookies.size()).append("개 (값은 출력하지 않는다)\n");
    report.append("임시 프로필      : ").append(scratch.toAbsolutePath()).append('\n');
    report.append("회원 페이지 URL  : ").append(finalUrl).append('\n');
    report.append("회원 페이지 도달 : ").append(reached).append("\n\n");
    report.append("해석 : ");
    if (reached) {
      report.append("Selenium 만으로 세션 이관이 성립한다.\n");
      report.append("       Playwright 는 필요 없다 — 갈린 것은 엔진이 아니라 방식이었고,\n");
      report.append("       그 방식은 기존 스택으로 그대로 할 수 있다.\n");
      report.append("       계획서 수용 기준 5(main 에 Playwright 의존성 없음)를 지킬 수 있다.\n");
    } else {
      report.append("Selenium 으로는 이관이 안 된다. Playwright 와 결과가 갈린다면\n");
      report.append("       주입 경로(CDP Network.setCookies)나 쿠키 속성 처리의 차이를 봐야 한다.\n");
      report.append("       그 차이가 본질이면 Playwright 도입이 근거를 갖는다.\n");
    }
    record("ST-1-selenium-transfer-" + mall + ".txt", report.toString());
    System.out.println(report);

    assertTrue(reached, "Selenium 단독 세션 이관 실패 — " + finalUrl);
  }

  /**
   * ST-2 — 캡처까지 Selenium 으로 한다. 여기까지 되면 Playwright 는 어느 단계에도 필요 없다.
   *
   * <p>ST-1 은 주입만 Selenium 으로 했고, 입력이 된 세션 파일은 Playwright 가 떴다. 그 마지막 고리를 없앤다.
   *
   * <p>사람을 다시 로그인시키지 않고 닫는 방법이 있다 — ST-1 로 <b>로그인된 Selenium 세션</b>을 만들 수 있으므로, 거기서 CDP {@code
   * Network.getAllCookies} 로 다시 뜨고, 그것을 <b>세 번째</b> 브라우저에 넣어 통하는지 본다. 캡처·주입 어느 쪽에도 Playwright 가 없다.
   */
  @Test
  @DisplayName("ST-2 — 캡처도 Selenium 으로: 왕복 전체를 기존 스택으로 닫는다")
  @SuppressWarnings("unchecked")
  void captureAndReinjectWithSeleniumOnly() throws Exception {
    String mall = System.getProperty(MALL_PROPERTY);
    if (mall == null || mall.isBlank()) {
      fail("대상 몰이 지정되지 않았다. -D" + MALL_PROPERTY + "=ssg|oasis");
    }
    String[] target = TARGETS.get(mall.trim().toLowerCase());
    String homeUrl = target[0];
    String memberUrl = target[1];
    String memberMarker = target[2];

    Path seed = artifactDir().resolve("storage-state-" + mall + ".json");
    assertTrue(Files.isRegularFile(seed), "씨앗 세션이 없다. PW-1 을 먼저 실행할 것: " + seed);

    // 1) 씨앗을 넣어 '로그인된 Selenium 세션' 을 만든다.
    List<Map<String, Object>> captured;
    WebDriver first = new WebDriverManager().getWebDriver("chrome", scratchProfile(mall, "cap"));
    try {
      ChromiumDriver chromium = (ChromiumDriver) first;
      first.get(homeUrl);
      Thread.sleep(1500);
      chromium.executeCdpCommand("Network.setCookies", Map.of("cookies", readCookies(seed)));
      first.navigate().to(memberUrl);
      Thread.sleep(2500);

      // 2) 그 살아 있는 세션에서 Selenium 이 직접 뜬다.
      Map<String, Object> all = chromium.executeCdpCommand("Network.getAllCookies", Map.of());
      captured = (List<Map<String, Object>>) all.get("cookies");
    } finally {
      first.quit();
    }
    assertTrue(captured != null && !captured.isEmpty(), "Selenium 이 쿠키를 뜨지 못했다.");

    // 3) 세 번째 브라우저에 그것을 넣는다. 여기까지 Playwright 는 어디에도 없다.
    String finalUrl;
    boolean reached;
    WebDriver second =
        new WebDriverManager().getWebDriver("chrome", scratchProfile(mall, "reinject"));
    try {
      ChromiumDriver chromium = (ChromiumDriver) second;
      second.get(homeUrl);
      Thread.sleep(1500);
      chromium.executeCdpCommand("Network.setCookies", Map.of("cookies", captured));
      second.navigate().to(memberUrl);
      Thread.sleep(3000);
      finalUrl = second.getCurrentUrl();
      reached = finalUrl.contains(memberMarker) && !finalUrl.toLowerCase().contains("login");
      saveScreenshot(second, "ST-2-member-" + mall + ".png");
    } finally {
      second.quit();
    }

    StringBuilder report = new StringBuilder();
    report.append("[ST-2] Selenium 단독 왕복 (캡처 + 주입) — ").append(mall).append("\n\n");
    report.append("Selenium 이 뜬 쿠키 : ").append(captured.size()).append("개 (값은 출력하지 않는다)\n");
    report.append("회원 페이지 URL     : ").append(finalUrl).append('\n');
    report.append("회원 페이지 도달    : ").append(reached).append("\n\n");
    report.append("해석 : ");
    if (reached) {
      report.append("캡처·주입 어느 쪽에도 Playwright 가 필요 없다.\n");
      report.append("       CDP Network.getAllCookies / setCookies 로 왕복이 닫힌다.\n");
      report.append("       Phase 5B 는 계속 미착수로 둘 수 있고, 계획서 수용 기준 5 도 지켜진다.\n");
    } else {
      report.append("Selenium 캡처본으로는 통하지 않는다. Playwright storageState 와의 차이를\n");
      report.append("       확인해야 한다(쿠키 속성 보존 범위·localStorage 포함 여부 등).\n");
    }
    record("ST-2-selenium-roundtrip-" + mall + ".txt", report.toString());
    System.out.println(report);

    assertTrue(reached, "Selenium 단독 왕복 실패 — " + finalUrl);
  }

  private static Path scratchProfile(String mall, String suffix) throws IOException {
    Path dir = Paths.get("build", "probe-profiles", "selenium-" + suffix + "-" + mall);
    Files.createDirectories(dir);
    return dir;
  }

  /**
   * Playwright storageState 형식에서 CDP 가 받는 형태로 옮긴다.
   *
   * <p>값을 다루지만 <b>어디에도 출력하지 않는다</b>. 실제 세션 토큰이다.
   */
  private static List<Map<String, Object>> readCookies(Path stateFile) throws IOException {
    JsonNode root =
        new ObjectMapper().readTree(Files.readString(stateFile, StandardCharsets.UTF_8));
    List<Map<String, Object>> out = new ArrayList<>();
    for (JsonNode c : root.path("cookies")) {
      Map<String, Object> cookie = new LinkedHashMap<>();
      cookie.put("name", c.path("name").asText());
      cookie.put("value", c.path("value").asText());
      cookie.put("domain", c.path("domain").asText());
      cookie.put("path", c.path("path").asText("/"));
      cookie.put("secure", c.path("secure").asBoolean(false));
      cookie.put("httpOnly", c.path("httpOnly").asBoolean(false));
      // Playwright 는 세션 쿠키의 만료를 -1 로 준다. CDP 도 만료를 빼면 세션 쿠키로 본다.
      double expires = c.path("expires").asDouble(-1);
      if (expires > 0) {
        cookie.put("expires", expires);
      }
      String sameSite = c.path("sameSite").asText("");
      if (!sameSite.isBlank() && !"None".equalsIgnoreCase(sameSite)) {
        cookie.put("sameSite", sameSite);
      }
      out.add(cookie);
    }
    return out;
  }

  private static Path artifactDir() throws IOException {
    Path dir = Paths.get("build", "probe-artifacts");
    Files.createDirectories(dir);
    return dir;
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

  private static void record(String name, String body) throws IOException {
    Path file = artifactDir().resolve(name);
    Files.writeString(file, body, StandardCharsets.UTF_8);
    System.out.println("[probe] 기록: " + file.toAbsolutePath());
  }
}

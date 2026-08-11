package com.jiniebox.jangbogo.svc.mall;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jiniebox.jangbogo.svc.util.NativeChromeLoginLauncher;
import com.jiniebox.jangbogo.svc.util.WebDriverManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.NoSuchSessionException;
import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * 하나로마트 두 사이트의 화면 구조를 <b>사람이 로그인한 뒤</b> 실측한다 (온라인/오프라인 분리 착수 실측).
 *
 * <h2>왜 필요한가</h2>
 *
 * <p>하나로마트가 서비스를 개편해 <b>오프라인 거래내역이 {@code nhhanaro.co.kr} 로 분리</b>됐다. 기존 수집기는 {@code
 * nonghyupmall.com} 만 탐색하므로 오프라인을 영영 못 가져온다 — 셀렉터 버그가 아니라 대상이 옮겨 간 것이다. 새 수집기를 쓰려면 두 화면의 표 구조를 알아야
 * 하는데, <b>저장소에도 문서에도 그 기록이 없다.</b>
 *
 * <p>이 프로젝트가 반복해서 겪은 사고가 "추측해 채운 셀렉터가 0건을 성공으로 굳히는" 것이다. 그래서 <b>추측 대신 실측</b>한다.
 *
 * <h2>왜 로그인을 사람이 하는가</h2>
 *
 * <p>프로브는 자격증명을 다루지 않는다. 순정 Chrome 을 띄워 <b>사람이 직접 로그인</b>하고, 프로브는 그 살아 있는 브라우저에 CDP 로 <b>밖에서 붙어</b>
 * 화면만 읽는다({@code SessionCaptureProbe} 가 이 붙기 경로가 성립함을 이미 쟀다). 비밀번호는 프로브의 메모리에도, 산출물에도 들어오지 않는다.
 *
 * <h2>산출물에 무엇이 담기는가 — 이것이 이 프로브의 설계 제약이다</h2>
 *
 * <p>읽는 화면은 <b>그 사람의 구매 내역</b>이고 이 저장소는 PUBLIC 이다. 그래서 이 프로브는 값을 옮기지 않고 {@link DomShapeReport} 로
 * <b>형태만</b> 옮긴다 — {@code 2099-12-31} 은 {@code NNNN-NN-NN} 이 되고 상품명은 {@code 가×7} 이 된다. 파서를 쓰는 데 필요한
 * 것(열 개수, 몇 번째가 날짜인지, 금액에 쉼표·단위가 붙는지)은 그것으로 충분하다.
 *
 * <p><b>쿠키는 이름만 본다 — 값은 읽지 않는다.</b> 주소는 질의 문자열의 값을 지운다(기간별 조회의 날짜와 세션 토큰이 거기 실린다).
 *
 * <p>그럼에도 산출물은 {@code build/} 아래에만 쓰고 <b>커밋하지 않는다</b>.
 *
 * <h2>실행</h2>
 *
 * <pre>
 * ./gradlew test -PincludeProbe --tests '*HanaroSiteProbe.offline*'
 * ./gradlew test -PincludeProbe --tests '*HanaroSiteProbe.online*'
 * </pre>
 *
 * <p>순정 Chrome 이 뜬다. 로그인하고 <b>조회 버튼까지 눌러 목록이 화면에 보이는 상태로 둔다</b>(창을 닫지 않는다).
 *
 * <p><b>도착만으로는 읽지 않는다.</b> 기간별 조회가 같은 주소로 POST 하는 탓에 주소로는 조회 전후를 가를 수 없어, 첫 실측이 조회 전의 빈 화면을 읽고도
 * 초록으로 끝났다. 지금은 <b>값이 든 칸이 둘 이상인 행</b>이 나타날 때까지 더 기다리고, 끝내 없으면 실패한다 — 결과 0건과 '아직 조회를 안 눌렀다' 는 여기서
 * 갈리지 않으므로 그 판단은 사람에게 남긴다.
 *
 * <p>프로필은 <b>지우지 않는다.</b> 같은 계정으로 반복 로그인하는 것은 차단 패턴이고, 이 프로젝트가 줄이려는 것이 바로 그것이다. 두 번째 실행부터는 이미 로그인된
 * 상태로 뜰 수 있다.
 *
 * @author KIUNSEA
 */
@Tag("probe")
class HanaroSiteProbe {

  /** 사람이 로그인하고 목표 화면까지 들어가는 것을 기다리는 상한. */
  private static final Duration HUMAN_TIMEOUT = Duration.ofMinutes(20);

  /**
   * 목표 화면에 도착한 뒤, <b>조회 결과 행이 실제로 그려질 때까지</b> 더 기다리는 상한.
   *
   * <p>이 대기가 따로 필요한 이유가 첫 실측에서 드러났다. 기간별 조회는 <b>같은 주소로 POST</b> 하므로 조회 전후의 주소가 같다 — 주소만 보면 사람이 메뉴에
   * 도착한 순간 조건이 성립해 버려, 조회를 누르기도 전의 빈 화면을 읽는다. 실제로 첫 판이 그랬고 산출물에는 '조회된 자료가 없습니다' 한 칸만 남았다.
   */
  private static final Duration DATA_ROW_TIMEOUT = Duration.ofMinutes(10);

  /** 사람이 거래 한 건을 눌러 상세를 띄우는 것을 기다리는 상한. */
  private static final Duration DETAIL_TIMEOUT = Duration.ofMinutes(10);

  /** DevTools 가 열리기를 기다리는 상한. */
  private static final Duration ATTACH_TIMEOUT = Duration.ofSeconds(60);

  private static final Duration POLL = Duration.ofMillis(500);

  /**
   * 한 표에서 형태를 뜰 행 수.
   *
   * <p>2 였을 때 거래 행 하나와 빈 간격 행 하나가 잡혀 실제 데이터 행을 한 줄밖에 못 봤다. 열마다 값이 늘 채워지는지(선택 필드가 있는지)는 한 줄로는 알 수
   * 없다.
   */
  private static final int SAMPLE_ROWS = 4;

  /** 요약에 실을 표의 상한. */
  private static final int TABLE_LIMIT = 25;

  /** 요약에 실을 링크 경로의 상한. */
  private static final int LINK_LIMIT = 30;

  /**
   * 실측 대상.
   *
   * <p>{@code targetUrl} 은 사용자가 확정해 준 진입점이다. {@code urlMarker} 는 "사람이 목표 화면에 도달했다" 의 판정인데, <b>주소로
   * 판정하는 이유</b>는 그것만이 지금 확실히 아는 신호이기 때문이다. 로그인 어포던스 셀렉터는 아직 실측 전이라 그것으로 기다리면 안 뜨는 화면을 영원히 기다린다.
   */
  private enum Target {
    OFFLINE(
        "hanaro-offline",
        "오프라인 거래내역 (nhhanaro.co.kr)",
        "https://www.nhhanaro.co.kr",
        "https://www.nhhanaro.co.kr/nahh_70090.do",
        "nahh_70090",
        // 2026-08-10 실측한 머리글. '구매금액(원)' 은 괄호가 붙어 있어 뺐다 — 셋이면 충분히 특정된다.
        List.of("일시", "내역", "매장"),
        List.of(
            "1) 로그인한다.",
            "2) '거래내역 조회' 메뉴로 들어간다.",
            "3) '기간별 조회' 로 기간을 지정해 조회한다 — 목록이 화면에 보이는 상태로 둔다.",
            "4) 목록이 뜨고 5초쯤 지난 뒤, 거래 한 건의 <우측 버튼>을 눌러 상품 목록을 펼친다.",
            "5) 창을 닫지 말고 그대로 둔다. 프로브가 알아서 읽는다.")),
    ONLINE(
        "hanaro-online",
        "온라인 주문/배송 조회 (nonghyupmall.com)",
        "https://www.nonghyupmall.com",
        "https://www.nonghyupmall.com/BCE2080R/inqOrrDvyPsttList.nh",
        "inqOrrDvyPsttList",
        // 머리글 미실측. 첫 실측에서 잡힌 표 10개는 전부 셀이 비어 있는 레이아웃·탭 템플릿이었고,
        // 주문 목록이 table 이 아닐 수도 있다. 추측해 적지 않고 구조로 고르게 둔다.
        List.of(),
        List.of(
            "1) 로그인한다.",
            "2) 주문/배송 조회 화면으로 들어간다.",
            "3) 주문 목록이 화면에 보이는 상태로 둔다(조회 기간이 필요하면 지정한다).",
            "4) 목록이 뜨고 5초쯤 지난 뒤, 주문 한 건의 상세로 들어간다.",
            "5) 창을 닫지 말고 그대로 둔다. 프로브가 알아서 읽는다."));

    private final String id;
    private final String label;
    private final String siteRoot;
    private final String targetUrl;
    private final String urlMarker;

    /**
     * 거래 목록 표를 알아보는 머리글. <b>실측한 것만 적는다.</b>
     *
     * <p>비어 있으면 구조로 고른다({@link #findListTable}) — 아직 머리글을 모르는 대상이다.
     */
    private final List<String> listHeaders;

    private final List<String> humanSteps;

    Target(
        String id,
        String label,
        String siteRoot,
        String targetUrl,
        String urlMarker,
        List<String> listHeaders,
        List<String> humanSteps) {
      this.id = id;
      this.label = label;
      this.siteRoot = siteRoot;
      this.targetUrl = targetUrl;
      this.urlMarker = urlMarker;
      this.listHeaders = listHeaders;
      this.humanSteps = humanSteps;
    }
  }

  @Test
  @DisplayName("오프라인 — nhhanaro.co.kr 거래내역 조회 화면의 구조를 뜬다")
  void offlineTransactionInquiryShape() throws Exception {
    probe(Target.OFFLINE);
  }

  @Test
  @DisplayName("온라인 — nonghyupmall.com 주문/배송 조회 화면의 구조를 뜬다")
  void onlineOrderDeliveryShape() throws Exception {
    probe(Target.ONLINE);
  }

  /**
   * 오프라인 사이트의 <b>로그인 화면 구조</b>만 뜬다 — <b>자격증명이 필요 없다</b>.
   *
   * <h2>왜 따로 두는가</h2>
   *
   * <p>수집기가 로그인 제출에서 막혔다. 실계정 실행이 남긴 판정은 "제출 뒤에도 로그인 화면에 머물러 있다" 였고, 프로브 기록을 보면 이유가 보인다 — 사람이 치는 칸에
   * {@code name} 이 없다. name 없는 입력은 폼 전송에 <b>포함되지 않으므로</b>, 그 폼을 그대로 제출하면 빈 자격증명이 간다. name 을 가진 것은
   * 숨은 폼의 {@code userId}/{@code userPw} 뿐이다.
   *
   * <p>즉 사이트 스크립트가 보이는 값을 숨은 칸으로 옮겨 제출한다. <b>그 진입점(함수 이름 또는 실제 버튼)을 알아야</b> 수집기가 같은 길을 갈 수 있는데, 그것을
   * 추측하면 회차만 태운다.
   *
   * <p>로그인 화면은 공개 페이지라 <b>로그인하지 않고</b> 뜰 수 있다. 사람이 할 일이 없으므로 그냥 돌리면 된다.
   *
   * <pre>
   * ./gradlew test -PincludeProbe --tests '*HanaroSiteProbe.offlineLogin*'
   * </pre>
   */
  @Test
  @DisplayName("오프라인 로그인 화면 — 제출 진입점을 찾는다 (자격증명 불필요)")
  void offlineLoginFormShape() throws Exception {
    Path profile = Paths.get("build", "probe-profiles", "hanaro-login");
    Files.createDirectories(profile);

    StringBuilder report = new StringBuilder();
    report.append("[하나로 실측] 오프라인 로그인 화면 구조 (자격증명 없이)\n\n");
    report.append("  값은 담지 않는다 — 폼·버튼·스크립트의 이름과 형태만.\n\n");

    Process chrome = null;
    WebDriver attached = null;
    WebDriverManager manager = new WebDriverManager();

    try {
      chrome =
          NativeChromeLoginLauncher.launch(
              profile, "https://www.nhhanaro.co.kr", NativeChromeLoginLauncher.ANY_DEBUG_PORT);

      int port = NativeChromeLoginLauncher.awaitDevToolsPort(profile, ATTACH_TIMEOUT, POLL);
      if (port <= 0) {
        throw new IllegalStateException("DevTools 포트가 열리지 않아 붙을 수 없다.");
      }
      NativeChromeLoginLauncher.awaitDevToolsBrowser(port, ATTACH_TIMEOUT, POLL);
      attached = manager.attachToRunningChrome(NativeChromeLoginLauncher.debuggerAddress(port));
      manager.applyStealth(attached);

      TimeUnit.SECONDS.sleep(3);

      // 첫 화면에서 <b>메뉴 링크</b>를 찾는다. raw 주소로 들어가면 스크립트 없는 조각이 오므로,
      // 수집기는 사람과 같은 길 — 링크 클릭 — 로 가야 한다. 그 링크를 무엇으로 집을지 정하려면
      // 글자와 href 를 함께 봐야 한다.
      report.append(describeMenuLinks(attached, "R. 첫 화면의 진입 링크"));

      // 대조군: id 없는 raw 주소. 조각이 온다는 것을 이 실행에서 다시 확인한다.
      attached.navigate().to("https://www.nhhanaro.co.kr/nahh_70021.do");
      TimeUnit.SECONDS.sleep(3);
      report.append('\n').append(describePage(attached, "L0. 로그인 화면 (id 없음 — 대조군)"));

      // 본 측정: 사용자가 확인해 준 온전한 주소. 여기서는 스크립트가 실려야 한다.
      attached.navigate().to(HanaroOffline.LOGIN_URL);
      TimeUnit.SECONDS.sleep(3);
      report.append('\n').append(describePage(attached, "L1. 로그인 화면 (id 있음 — 수집기가 쓰는 주소)"));
      report.append('\n').append(describeSubmitPath(attached));
    } catch (RuntimeException e) {
      report.append("\n  중단됨 : ").append(describeFailure(e)).append('\n');
    } finally {
      if (attached != null) {
        try {
          manager.closeAttachedBrowser(attached);
        } catch (RuntimeException ignore) {
          // 창이 이미 닫혔을 수 있다
        }
      }
      if (chrome != null && chrome.isAlive()) {
        chrome.destroy();
      }
      try {
        record("HANARO-LOGIN.txt", report.toString());
      } catch (IOException io) {
        System.out.println("[probe] 기록 실패: " + io.getClass().getSimpleName());
      }
      System.out.println(report);
    }
  }

  /**
   * 로그인 제출이 <b>어디로 이어지는지</b>를 훑는다.
   *
   * <p>보이는 칸에 {@code name} 이 있는지, 어떤 요소에 {@code onclick} 이 붙어 있는지, 폼에 {@code onsubmit} 이 있는지, 어떤
   * 스크립트가 실려 있는지 — 이 넷이면 수집기가 어느 길로 제출해야 하는지 정해진다.
   */
  private static String describeSubmitPath(WebDriver driver) {
    StringBuilder out = new StringBuilder("── 제출 경로 단서 ──\n");

    out.append("  [입력칸의 name 유무] — name 이 없으면 그 값은 전송되지 않는다\n");
    for (WebElement input : findAll(driver, By.tagName("input"))) {
      String type = attr(input, "type");
      out.append("   input type=").append(type);
      out.append(" id=").append(DomShapeReport.maskDigits(attr(input, "id")));
      out.append(" name=").append(DomShapeReport.maskDigits(attr(input, "name")));
      out.append(" 보임=").append(isDisplayedSafely(input));
      out.append(onClickOf(input));
      out.append('\n');
    }

    out.append("  [폼의 onsubmit·action]\n");
    for (WebElement form : findAll(driver, By.tagName("form"))) {
      out.append("   form id=").append(DomShapeReport.maskDigits(attr(form, "id")));
      out.append(" action=").append(DomShapeReport.safeUrl(attr(form, "action")));
      String onsubmit = attr(form, "onsubmit");
      out.append(" onsubmit=")
          .append("(없음)".equals(onsubmit) ? "(없음)" : DomShapeReport.callSignature(clip(onsubmit)));
      out.append('\n');
    }

    out.append("  [onclick 이 달린 요소]\n");
    for (WebElement el : findAll(driver, By.cssSelector("a[onclick], button, [onclick]"))) {
      String onclick = onClickOf(el);
      if (onclick.isEmpty()) {
        continue;
      }
      out.append("   ").append(safely(el::getTagName));
      out.append(" id=").append(DomShapeReport.maskDigits(attr(el, "id")));
      out.append(onclick).append('\n');
    }

    out.append("  [실린 스크립트]\n");
    int shown = 0;
    for (WebElement script : findAll(driver, By.tagName("script"))) {
      String src = attr(script, "src");
      if ("(없음)".equals(src) || shown++ >= 15) {
        continue;
      }
      out.append("   ").append(DomShapeReport.maskLongDigitRuns(DomShapeReport.safeUrl(src)));
      out.append('\n');
    }
    return out.toString();
  }

  /**
   * 첫 화면에서 <b>수집기가 클릭할 링크</b>의 후보를 훑는다.
   *
   * <p>이 사이트는 {@code id} 질의가 붙어야 전체 레이아웃으로 렌더된다 — raw 주소로 들어가면 스크립트가 빠진 조각이 오고, 그래서 로그인 제출이 먹지 않았다.
   * 그 {@code id} 값을 알아내 박아 넣는 것은 값이 바뀌면 깨진다. <b>사람과 같이 링크를 누르는 편</b>이 옳다.
   *
   * <p>그래서 여기서 재는 것은 "무엇으로 그 링크를 집을 수 있는가" 다 — 글자, href 에 들어 있는 화면 코드, 그리고 그 둘이 유일한지.
   *
   * <p>링크 글자는 {@code 로그인}·{@code 거래내역 조회} 처럼 사이트가 모두에게 똑같이 주는 구조라 원문을 싣는다({@link
   * DomShapeReport#headerLabel} 의 조건을 그대로 적용한다).
   */
  private static String describeMenuLinks(WebDriver driver, String heading) {
    StringBuilder out = new StringBuilder("── " + heading + " ──\n");
    out.append("  주소 : ")
        .append(DomShapeReport.safeUrl(safely(driver::getCurrentUrl)))
        .append('\n');

    // 수집기가 가야 하는 두 곳의 화면 코드. href 에 이것이 들어 있는 링크가 진입점 후보다.
    List<String> wanted = List.of("nahh_70021", "nahh_70090", "nahh_7002", "login");
    int found = 0;

    for (WebElement a : findAll(driver, By.tagName("a"))) {
      String href = attr(a, "href");
      String label = DomShapeReport.headerLabel(safely(a::getText));

      boolean hrefMatches = false;
      for (String code : wanted) {
        if (href != null && href.toLowerCase().contains(code)) {
          hrefMatches = true;
          break;
        }
      }
      boolean labelMatches = label.contains("로그인") || label.contains("거래내역");
      if (!hrefMatches && !labelMatches) {
        continue;
      }

      found++;
      out.append("   글자=\"").append(label).append('"');
      out.append(" 보임=").append(isDisplayedSafely(a));
      out.append("\n      href=")
          .append(
              href != null && href.startsWith("javascript:")
                  ? DomShapeReport.callSignature(clip(href))
                  : DomShapeReport.maskLongDigitRuns(DomShapeReport.safeUrl(href)));
      out.append('\n');
    }

    if (found == 0) {
      out.append("   (후보 없음 — 첫 화면에 로그인/거래내역 링크가 보이지 않는다)\n");
    }
    return out.toString();
  }

  private static String isDisplayedSafely(WebElement el) {
    try {
      return el.isDisplayed() ? "예" : "아니오";
    } catch (RuntimeException e) {
      return "?";
    }
  }

  // ── 본체 ────────────────────────────────────────────────────────────────

  private void probe(Target target) throws Exception {
    Path profile = Paths.get("build", "probe-profiles", target.id);
    Files.createDirectories(profile);

    if (NativeChromeLoginLauncher.isProfileInUse(profile)) {
      throw new IllegalStateException(
          "이 프로필로 이미 브라우저가 열려 있다. 그 창을 닫고 다시 실행할 것: " + profile.toAbsolutePath());
    }

    announce(target);

    StringBuilder report = new StringBuilder();
    report.append("[하나로 실측] ").append(target.label).append("\n\n");
    report.append("  이 파일에는 값이 없다 — 형태(NNNN-NN-NN, 가×7)와 구조만 담긴다.\n");
    report.append("  그래도 build/ 밖으로 내보내거나 커밋하지 않는다.\n\n");

    boolean reachedByHuman = false;
    boolean dataRowSeen = false;
    boolean detailSeen = false;
    // 3상태다. null 은 '재지 못했다' 이지 '재서 아니오' 가 아니다 — 예외로 못 잰 것을 false 로
    // 접으면 판독문이 재지도 않은 축에 대해 단정하게 된다(SessionCaptureProbe 의 measured 규칙).
    Boolean reachedByUrl = null;
    int tablesSeen = 0;
    int framesSeen = 0;
    Process chrome = null;
    WebDriver attached = null;
    WebDriverManager manager = new WebDriverManager();

    try {
      chrome =
          NativeChromeLoginLauncher.launch(
              profile, target.siteRoot, NativeChromeLoginLauncher.ANY_DEBUG_PORT);

      int port = NativeChromeLoginLauncher.awaitDevToolsPort(profile, ATTACH_TIMEOUT, POLL);
      report.append("  디버깅 포트          : ").append(port > 0 ? "열림" : "열리지 않음").append('\n');
      if (port <= 0) {
        throw new IllegalStateException("DevTools 포트가 열리지 않아 붙을 수 없다.");
      }

      String browser = NativeChromeLoginLauncher.awaitDevToolsBrowser(port, ATTACH_TIMEOUT, POLL);
      report
          .append("  DevTools 응답        : ")
          .append(browser == null ? "없음" : browser)
          .append('\n');

      attached = manager.attachToRunningChrome(NativeChromeLoginLauncher.debuggerAddress(port));

      // 디버깅 포트를 여는 것만으로 navigator.webdriver 가 true 가 된다. 사람이 이 창에서
      // 로그인하기 전에 되돌려야 한다 — 로그인 화면은 붙은 뒤의 새 문서이므로
      // Page.addScriptToEvaluateOnNewDocument 로 주입하는 이 호출이 그 화면에 적용된다.
      // 순서가 뒤바뀌면 사람이 자동화 표식을 켠 브라우저로 실계정에 로그인하게 되고,
      // 그 상태는 로그인이 끝난 뒤에는 되돌릴 수 없다.
      manager.applyStealth(attached);

      // ── 사람이 목표 화면에 도달할 때까지 기다린다 ──────────────────────────────
      reachedByHuman = awaitUrlMarker(attached, target.urlMarker, HUMAN_TIMEOUT);
      report
          .append("  사람이 도달한 화면    : ")
          .append(reachedByHuman ? "목표 화면 확인" : "시간 안에 확인 못 함")
          .append('\n');

      // 도착만으로는 부족하다 — 조회 결과 행이 그려질 때까지 더 기다린다.
      if (reachedByHuman) {
        dataRowSeen = awaitDataRow(attached, target, DATA_ROW_TIMEOUT);
        WebElement list = findListTable(attached, target);
        report
            .append("  거래 목록 표          : ")
            .append(list == null ? "찾지 못함" : "찾음 (값이 든 행 " + filledRowCount(list) + "개)")
            .append('\n');
        report
            .append("  조회 결과 행          : ")
            .append(dataRowSeen ? "확인" : "시간 안에 나타나지 않음")
            .append('\n');
      }

      if (dataRowSeen) {
        report.append('\n').append(describePage(attached, "A. 조회 결과가 그려진 화면"));
      } else if (reachedByHuman) {
        report.append('\n').append(describePage(attached, "A. 목표 화면이나 결과 행이 없는 상태"));
      } else {
        report.append('\n').append(describePage(attached, "A. 시간 초과 시점의 화면 (목표 화면이 아닐 수 있다)"));
      }
      // 아래 판정에 쓸 값을 A 화면에서 그대로 센다. B 로 넘어간 뒤에 세면 다른 화면을 세게 된다.
      tablesSeen = findAll(attached, By.tagName("table")).size();
      framesSeen = frameCount(attached);

      // ── 상세 화면 — 목록에 품목이 없으므로 여기가 items 를 채울 유일한 자리다 ──────
      //
      // 목록은 '대표상품 외 N건' 만 준다. 상세에 무엇이 있는지 모르면 수집기가 무엇을 저장할 수
      // 있는지도 정할 수 없다. B(주소 직접 진입)보다 먼저 해야 한다 — B 가 목록을 지운다.
      if (dataRowSeen) {
        detailSeen = probeDetail(attached, target, report);
      }

      // ── 주소로 직접 도달할 수 있는가 — 수집기가 실제로 할 일이다 ─────────────────
      //
      // 사람이 메뉴를 눌러 들어간 것과, 수집기가 URL 로 바로 들어가는 것은 다른 일이다.
      // 뒤쪽이 되지 않으면 수집기는 메뉴를 눌러야 하고, 그러면 필요한 셀렉터가 늘어난다.
      try {
        attached.navigate().to(target.targetUrl);
        TimeUnit.SECONDS.sleep(3);
        String landed = attached.getCurrentUrl();
        reachedByUrl = landed != null && landed.contains(target.urlMarker);
        report
            .append('\n')
            .append("  주소로 직접 진입      : ")
            .append(reachedByUrl ? "성공" : "밀려남 → " + DomShapeReport.safeUrl(landed))
            .append('\n');
        report.append('\n').append(describePage(attached, "B. 주소로 직접 진입한 화면"));
      } catch (RuntimeException e) {
        report
            .append("\n  주소로 직접 진입      : 예외 — ")
            .append(e.getClass().getSimpleName())
            .append('\n');
      }

      report.append('\n').append(describeCookies(attached));
    } catch (RuntimeException e) {
      // 여기서 삼키는 것은 결과를 감추기 위해서가 아니라 <b>기록을 남기기 위해서</b>다.
      // 예외로 그냥 빠져나가면 실계정 로그인을 한 번 쓰고도 산출물이 하나도 남지 않는다 —
      // 실제로 그렇게 한 회차를 통째로 잃었다. 무슨 일이 있었는지는 아래에 적고, 판정은
      // 끝의 assert 가 그대로 한다.
      report.append("\n  중단됨               : ").append(describeFailure(e)).append('\n');
    } finally {
      if (attached != null) {
        try {
          manager.closeAttachedBrowser(attached);
        } catch (RuntimeException ignore) {
          // 창이 이미 닫혔을 수 있다. 산출물 기록이 우선이다.
        }
      }
      if (chrome != null && chrome.isAlive()) {
        chrome.destroy();
      }

      // 기록은 어떤 경로로 빠져나가든 반드시 남긴다.
      report.append('\n').append(interpretation(reachedByHuman, reachedByUrl));
      try {
        record(target.id.toUpperCase() + ".txt", report.toString());
      } catch (IOException io) {
        System.out.println("[probe] 기록 실패: " + io.getClass().getSimpleName());
      }
      System.out.println(report);
    }

    // 재지 못한 것을 '재서 아니오' 로 접어 넣지 않는다 — 못 쟀으면 그 자체가 실패다.
    assertTrue(reachedByHuman, "목표 화면 도달을 확인하지 못했다. 로그인·메뉴 진입이 끝나기 전에 시간이 지났을 수 있다.\n" + report);

    // 주소는 맞는데 표가 0개인 상태를 성공으로 넘기지 않는다.
    //
    // 구형 .do 사이트가 목록을 프레임 안에 그리면 최상위 문서에는 표가 없다. 그런데 주소는
    // 목표 화면과 일치하므로 위 판정은 통과한다 — 실계정 로그인을 한 번 쓰고도 아무것도 재지
    // 못한 채 초록이 된다. 이 저장소가 반복해서 겪은 '0건이 성공으로 굳는' 형태 그대로다.
    assertTrue(
        tablesSeen > 0,
        "목표 화면에 도달했는데 표가 0개다 — 아무것도 재지 못했다. 프레임 "
            + framesSeen
            + "개. 프레임이 있으면 목록이 그 안에 그려진 것이므로 프레임 전환이 필요하다.\n"
            + report);

    // 표가 있어도 데이터 행이 없으면 파서에 쓸 것이 없다. 첫 실측이 정확히 그 상태였고,
    // 그때는 이 판정이 없어 초록으로 끝났다 — 머리글만 얻고 열 형태는 못 얻었다.
    assertTrue(
        dataRowSeen,
        "거래 목록 표에서 결과 행을 한 줄도 보지 못했다. 둘 중 하나다 — (1) 조회 버튼을 누르기 전에 시간이 지났다,"
            + " (2) 그 기간에 거래가 정말 없다.\n"
            + "달력이 열린 채로 끝났다면 (1) 이다 — 날짜를 고른 뒤 반드시 조회 버튼까지 누를 것.\n"
            + report);

    // 목록은 '대표상품 외 N건' 만 준다. 상세를 못 읽으면 items 를 무엇으로 채울지 알 수 없고,
    // 그것을 모르는 채로 수집기를 쓰면 품목 없는 주문만 쌓인다.
    assertTrue(
        detailSeen, "상세 화면을 읽지 못했다 — 목록만으로는 품목을 알 수 없다. 거래 한 건의 [상세] 버튼을 눌러야 한다.\n" + report);
  }

  /**
   * 사람이 거래 한 건을 눌러 띄운 <b>상세 화면</b>을 읽는다.
   *
   * <h2>왜 변화를 기다리는가</h2>
   *
   * <p>상세가 어떤 형태로 열리는지 <b>모른다.</b> 목록의 진입점은 {@code href="javascript:void(0)"} 이고 실제 동작은 {@code
   * onclick} 에 있어서, 새 창일 수도, 같은 페이지 위의 레이어일 수도, 다른 주소로의 이동일 수도 있다. 셋 중 하나를 골라 기다리면 나머지 둘에서 영원히
   * 기다린다.
   *
   * <p>그래서 특정 신호가 아니라 <b>화면이 달라졌는가</b>를 본다 — 창 수, 주소, 표의 수와 각 표의 행 수를 묶은 서명이 바뀌면 무언가 열린 것이다. 새 창이
   * 생겼으면 그쪽으로 옮겨 읽고, 읽은 뒤에는 원래 창으로 돌아온다(뒤에서 B 가 목록 창을 계속 쓴다).
   *
   * @return 상세를 읽었으면 true
   */
  private static boolean probeDetail(WebDriver driver, Target target, StringBuilder report)
      throws InterruptedException {
    String baseline = pageSignature(driver);
    Set<String> windowsBefore = new LinkedHashSet<>(driver.getWindowHandles());
    String origin = driver.getWindowHandle();

    System.out.println();
    System.out.println("  ▶ 목록을 읽었습니다. 이제 거래 한 건의 우측 버튼을 눌러 상품 목록을 펼쳐 주세요.");
    System.out.println("    같은 화면에서 펼쳐지든 새 창이 뜨든 상관없습니다. 대기 " + DETAIL_TIMEOUT.toMinutes() + "분.");
    System.out.println();

    long deadline = System.nanoTime() + DETAIL_TIMEOUT.toNanos();
    String opened = null;
    boolean changed = false;

    while (System.nanoTime() < deadline) {
      try {
        Set<String> now = driver.getWindowHandles();
        if (now.size() > windowsBefore.size()) {
          for (String handle : now) {
            if (!windowsBefore.contains(handle)) {
              opened = handle;
              break;
            }
          }
          break;
        }
        if (!pageSignature(driver).equals(baseline)) {
          changed = true;
          break;
        }
      } catch (RuntimeException ignore) {
        // 사람이 조작하는 중이면 잠깐 읽히지 않는다.
      }
      if (!browserAlive(driver)) {
        report.append("\n  상세 화면            : 브라우저가 닫혀 읽지 못함\n");
        return false;
      }
      TimeUnit.MILLISECONDS.sleep(POLL.toMillis());
    }

    if (opened == null && !changed) {
      report.append("\n  상세 화면            : 시간 안에 열리지 않음\n");
      return false;
    }

    // 렌더가 끝날 여유를 준다.
    TimeUnit.SECONDS.sleep(3);

    try {
      if (opened != null) {
        driver.switchTo().window(opened);
        report.append("\n  상세 화면            : 새 창으로 열림\n");
      } else {
        report.append("\n  상세 화면            : 같은 창에서 바뀜 (레이어 또는 페이지 이동)\n");
      }
      report.append('\n').append(describePage(driver, "D. 상품 목록을 펼친 화면 (거래 한 건)"));
      return true;
    } finally {
      if (opened != null) {
        try {
          driver.close();
          driver.switchTo().window(origin);
        } catch (RuntimeException ignore) {
          // 창이 이미 닫혔을 수 있다. 기록이 우선이다.
        }
      }
    }
  }

  /**
   * 화면이 달라졌는지 가릴 서명.
   *
   * <p>내용은 담지 않는다 — 창 수·주소·표의 수와 각 표의 행 수뿐이다. 상세가 레이어로 열려도 표가 늘거나 행 수가 달라지므로 잡힌다.
   */
  private static String pageSignature(WebDriver driver) {
    StringBuilder sig = new StringBuilder();
    try {
      sig.append(driver.getWindowHandles().size()).append('|');
      sig.append(driver.getCurrentUrl()).append('|');
    } catch (RuntimeException e) {
      sig.append("?|");
    }
    for (WebElement table : findAll(driver, By.tagName("table"))) {
      // 행 <b>개수</b>가 아니라 <b>보이는 글자 수</b>를 센다.
      //
      // 이 화면의 상품 목록은 새 창도 페이지 이동도 아니고, 거래 행 아래에 이미 들어 있는
      // 행이 펼쳐지는 것이다. 숨은 행도 DOM 에는 있으므로 tr 개수는 펼쳐도 그대로다 —
      // 개수로 감시하면 영원히 못 잡는다(실제로 한 회차를 그렇게 날렸다).
      //
      // getText() 는 <b>보이는</b> 글자만 준다. 펼치면 그 길이가 크게 늘어난다.
      sig.append(safely(table::getText).length()).append(',');
    }
    return sig.toString();
  }

  // ── 화면 읽기 ───────────────────────────────────────────────────────────

  /** 한 화면의 구조를 요약한다. 값은 담지 않는다. */
  private static String describePage(WebDriver driver, String heading) {
    StringBuilder out = new StringBuilder();
    out.append("── ").append(heading).append(" ──\n");
    out.append("  주소   : ")
        .append(DomShapeReport.safeUrl(safely(driver::getCurrentUrl)))
        .append('\n');
    out.append("  제목   : ")
        // 제목은 형태로만 남긴다 — '홍길동님 거래내역' 처럼 20자 이하 · 숫자 없는 제목이
        // 흔하고, headerLabel 의 통과 조건이 정확히 그 모양이라 이름이 그대로 실린다.
        .append(DomShapeReport.shape(safely(driver::getTitle)))
        .append('\n');

    List<WebElement> withId = findAll(driver, By.cssSelector("[id]"));
    out.append("  ").append(DomShapeReport.idCensus(attributes(withId, "id"))).append('\n');

    out.append("  비밀번호 입력칸 : ")
        .append(findAll(driver, By.cssSelector("input[type=password]")).size());
    out.append("  (0 이면 로그인 화면이 아니다)\n");

    // 구형 .do 사이트는 목록을 프레임 안에 그리는 일이 흔하다. 그러면 아래 표·폼·링크는
    // 최상위 문서만 훑은 결과라 전부 0 이 되는데, 주소는 목표 화면과 일치하므로
    // '도달했고 표가 없다' 는 확신에 찬 빈 측정이 남는다. 그 상태를 눈에 보이게 적는다.
    out.append("  프레임 : ").append(frameCount(driver));
    out.append("  (0 이 아니면 아래 표·폼·링크는 최상위 문서만의 것이다)\n");

    out.append('\n').append(describeRepeatedGroups(driver));
    out.append('\n').append(describeTables(driver));
    out.append('\n').append(describeForms(driver));
    out.append('\n').append(describeLinks(driver));
    return out.toString();
  }

  /** 반복 구조로 인정할 최소 형제 수. 이보다 적으면 목록이 아니라 우연한 중복으로 본다. */
  private static final int REPEAT_MIN = 3;

  /** 요약에 실을 반복 구조 종류의 상한. */
  private static final int REPEAT_LIMIT = 12;

  /**
   * <b>표가 아닌 목록</b>을 찾는다 — 같은 {@code 태그+class} 가 여러 번 반복되는 자리.
   *
   * <h2>왜 필요한가</h2>
   *
   * <p>오프라인 사이트는 목록이 {@code table} 이라 표만 훑어도 됐다. 그런데 온라인몰 실측에서는 표가 10개 잡혔는데 <b>셀이 전부 비어 있었다</b> —
   * 전부 레이아웃·탭 템플릿이었고 주문 목록은 그 안에 없었다. 요즘 사이트는 목록을 {@code div}·{@code ul} 로 그린다.
   *
   * <p>표만 보는 프로브는 그런 화면에서 "목록이 없다" 고 보고한다 — 실제로는 못 찾은 것인데. 그래서 <b>구조가 반복되는 자리</b>를 따로 센다. 주문 목록은 같은
   * 모양이 주문 수만큼 반복되므로, 반복 횟수가 가장 많은 무리가 목록일 가능성이 크다.
   *
   * <p>내용은 담지 않는다 — 태그·class·반복 횟수와 <b>첫 무리의 형태</b>만 싣는다.
   */
  private static String describeRepeatedGroups(WebDriver driver) {
    java.util.Map<String, List<WebElement>> groups = new java.util.LinkedHashMap<>();

    for (WebElement el : findAll(driver, By.cssSelector("li, div[class], article, tr[class]"))) {
      String tag = safely(el::getTagName);
      String cls = attr(el, "class");
      if ("(없음)".equals(cls) || "(읽기 실패)".equals(cls)) {
        continue;
      }
      // class 는 그대로 쓰되 숫자만 가린다 — 반복 렌더링되는 행 class 를 한 종으로 묶는다.
      groups
          .computeIfAbsent(tag + "." + DomShapeReport.maskDigits(clip(cls)), k -> new ArrayList<>())
          .add(el);
    }

    List<java.util.Map.Entry<String, List<WebElement>>> repeated = new ArrayList<>();
    for (var e : groups.entrySet()) {
      if (e.getValue().size() >= REPEAT_MIN) {
        repeated.add(e);
      }
    }
    repeated.sort((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()));

    StringBuilder out = new StringBuilder();
    out.append("  [반복 구조] ").append(repeated.size()).append("종 (표가 아닌 목록의 후보)\n");

    int shown = 0;
    for (var e : repeated) {
      if (shown++ >= REPEAT_LIMIT) {
        out.append("   … 이하 생략\n");
        break;
      }
      WebElement first = e.getValue().get(0);
      String visible = safely(first::getText);
      String dom = attr(first, "textContent");

      out.append("   ").append(e.getKey()).append("  ×").append(e.getValue().size()).append('\n');
      out.append("      보이는글자=").append(DomShapeReport.shape(clipText(visible))).append('\n');
      if (dom != null && !"(없음)".equals(dom) && !dom.trim().equals(visible.trim())) {
        out.append("      숨은글자  =").append(DomShapeReport.shape(clipText(dom))).append('\n');
      }
    }
    if (repeated.isEmpty()) {
      out.append("   (반복 구조 없음 — 목록이 아직 안 그려졌거나 조회 전이다)\n");
    }
    return out.toString();
  }

  /** 형태를 뜨기 전에 길이를 자른다. 한 무리가 화면 전체를 담고 있을 수 있다. */
  private static String clipText(String value) {
    if (value == null) {
      return "";
    }
    return value.length() <= 300 ? value : value.substring(0, 300);
  }

  /** 표를 훑는다 — 새 파서가 어느 표의 몇 번째 열을 읽을지가 여기서 정해진다. */
  private static String describeTables(WebDriver driver) {
    List<WebElement> tables = findAll(driver, By.tagName("table"));
    StringBuilder out = new StringBuilder();
    out.append("  [표] ").append(tables.size()).append("개\n");

    int shown = Math.min(tables.size(), TABLE_LIMIT);
    for (int i = 0; i < shown; i++) {
      WebElement table = tables.get(i);
      List<WebElement> rows = findAll(table, By.tagName("tr"));

      out.append("   table[").append(i).append("] ");
      out.append("id=").append(DomShapeReport.maskDigits(attr(table, "id")));
      out.append(" class=").append(DomShapeReport.maskDigits(clip(attr(table, "class"))));
      out.append(" 행=").append(rows.size());
      out.append("  조상=").append(ancestry(table));
      out.append('\n');

      if (rows.isEmpty()) {
        continue;
      }

      // 머리글 — 파서가 th/td 를 키로 짝지을 때 이 문자열이 그대로 필요하다.
      List<String> headers = new ArrayList<>();
      for (WebElement th : findAll(rows.get(0), By.tagName("th"))) {
        headers.add(DomShapeReport.headerLabel(safely(th::getText)));
      }
      if (!headers.isEmpty()) {
        out.append("        th : ").append(String.join(" | ", headers)).append('\n');
      }

      // 데이터 행의 형태 — 열 개수와 각 열의 형식이 여기서 드러난다.
      int sampled = 0;
      for (WebElement row : rows) {
        if (sampled >= SAMPLE_ROWS) {
          break;
        }
        List<WebElement> tds = findAll(row, By.tagName("td"));
        if (tds.isEmpty()) {
          continue;
        }
        List<String> texts = new ArrayList<>();
        for (WebElement td : tds) {
          texts.add(safely(td::getText));
        }
        out.append("        td(").append(tds.size()).append(") : ");
        out.append(DomShapeReport.cellShapes(texts)).append('\n');

        // 숨어 있는 글자를 따로 본다 — 이 화면 설계에서 가장 중요한 측정이다.
        //
        // 상품 목록은 거래 행 아래 접힌 행에 들어 있고, getText() 는 보이는 것만 주므로
        // 접혀 있으면 (빈칸)으로 읽힌다. textContent 는 숨어 있어도 그대로 준다.
        // 둘이 다르면 <b>이미 DOM 에 있다</b>는 뜻이고, 그러면 수집기는 행마다 클릭할 필요 없이
        // 한 번의 페이지 적재로 품목까지 전부 읽을 수 있다. 같으면 펼칠 때 AJAX 로 채워지는
        // 구조라 클릭이 불가피하다. 이 한 줄이 수집기 설계를 가른다.
        String hidden = hiddenTextShapes(tds, texts);
        if (!hidden.isEmpty()) {
          out.append("               ↳ 숨은글자 ").append(hidden).append('\n');

          // 그 칸의 <b>내부 구조</b>. 상품 목록이 요소로 나뉘어 있으면 파서가 요소를 훑으면 되고,
          // 통짜 텍스트면 정규식으로 갈라야 한다 — 파서의 모양이 여기서 갈린다.
          String structure = longCellStructures(tds);
          if (!structure.isEmpty()) {
            out.append("               ↳ 내부구조 ").append(structure).append('\n');
          }
        }

        // 셀 '안' 도 본다. 글자가 없는 칸이 펼침 버튼인 경우가 있다.
        String inner = cellControls(tds);
        if (!inner.isEmpty()) {
          out.append("               └ ").append(inner).append('\n');
        }
        sampled++;
      }
    }
    if (tables.size() > shown) {
      out.append("   … 이하 ").append(tables.size() - shown).append("개 생략\n");
    }
    return out.toString();
  }

  /** 폼을 훑는다 — 기간별 조회가 GET 질의인지 POST 폼인지, 날짜 입력이 무엇인지가 여기서 갈린다. */
  private static String describeForms(WebDriver driver) {
    List<WebElement> forms = findAll(driver, By.tagName("form"));
    StringBuilder out = new StringBuilder();
    out.append("  [폼] ").append(forms.size()).append("개\n");

    for (int i = 0; i < forms.size(); i++) {
      WebElement form = forms.get(i);
      out.append("   form[").append(i).append("] ");
      out.append("id=").append(DomShapeReport.maskDigits(attr(form, "id")));
      out.append(" method=").append(attr(form, "method"));
      out.append(" action=").append(DomShapeReport.safeUrl(attr(form, "action")));
      out.append('\n');

      for (WebElement input : findAll(form, By.tagName("input"))) {
        String type = attr(input, "type");
        if ("password".equalsIgnoreCase(type)) {
          // 비밀번호 칸은 존재만 보고한다. value 는 절대 읽지 않는다.
          out.append("        input type=password (존재만 보고)\n");
          continue;
        }
        out.append("        input type=").append(type);
        out.append(" id=").append(DomShapeReport.maskDigits(attr(input, "id")));
        out.append(" name=").append(DomShapeReport.maskDigits(attr(input, "name")));
        out.append('\n');
      }
      for (WebElement select : findAll(form, By.tagName("select"))) {
        List<WebElement> options = findAll(select, By.tagName("option"));
        out.append("        select id=").append(DomShapeReport.maskDigits(attr(select, "id")));
        out.append(" name=").append(DomShapeReport.maskDigits(attr(select, "name")));
        out.append(" 선택지=").append(options.size());
        List<String> labels = new ArrayList<>();
        for (int k = 0; k < Math.min(options.size(), 8); k++) {
          // headerLabel 을 쓰지 않는다. 선택지에는 점포명·수령인처럼 '20자 이하 · 숫자 없음'
          // 조건을 그대로 만족하는 개인정보가 들어온다. 형태만으로도 무엇을 고르는 칸인지는
          // 드러난다 — 기간 선택은 N가가, 점포 선택은 가×8 로 갈린다.
          labels.add(DomShapeReport.shape(safely(options.get(k)::getText)));
        }
        if (!labels.isEmpty()) {
          out.append(" [").append(String.join(", ", labels)).append(']');
        }
        out.append('\n');
      }
      for (WebElement button : findAll(form, By.tagName("button"))) {
        out.append("        button id=").append(DomShapeReport.maskDigits(attr(button, "id")));
        // 버튼 글자도 형태로만. 셀렉터로 쓸 것은 바로 위에 찍는 id 이지 글자가 아니다.
        out.append(" 글자=").append(DomShapeReport.shape(safely(button::getText)));
        out.append('\n');
      }
    }
    return out.toString();
  }

  /** 링크 경로를 훑는다 — 목록에서 상세로 들어가는 경로가 있는지가 여기서 드러난다. */
  private static String describeLinks(WebDriver driver) {
    Set<String> paths = new LinkedHashSet<>();
    for (WebElement a : findAll(driver, By.tagName("a"))) {
      String href = attr(a, "href");
      if (href == null || href.isBlank() || "(없음)".equals(href)) {
        continue;
      }
      if (href.startsWith("javascript:")) {
        // 함수 이름만 구조다. 인자는 통째로 버린다 — 숫자만 가리면
        // fnGoDetail('유기농바나나', …) 의 상품명이 그대로 남는다.
        paths.add(DomShapeReport.callSignature(clip(href)));
      } else {
        // 경로에 주문번호가 박힌 링크가 있다. 다만 전부 가리면 nahh_70090.do 같은
        // 화면 코드까지 지워져 이 프로브의 산출물 자체가 없어진다 — 긴 숫자만 가린다.
        paths.add(DomShapeReport.maskLongDigitRuns(DomShapeReport.safeUrl(href)));
      }
    }
    StringBuilder out = new StringBuilder();
    out.append("  [링크] 서로 다른 경로 ").append(paths.size()).append("종\n");
    int shown = 0;
    for (String path : paths) {
      if (shown++ >= LINK_LIMIT) {
        out.append("   … 이하 생략\n");
        break;
      }
      out.append("   ").append(path).append('\n');
    }
    return out.toString();
  }

  /**
   * 쿠키를 <b>이름만</b> 훑는다.
   *
   * <p>{@code MallRegistry.authCookieNames} 는 실측된 몰만 채우는 자리다. 여기서 나오는 이름이 그 실측이 된다. 값은 살아 있는 인증
   * 토큰이므로 읽지도 남기지도 않는다.
   */
  private static String describeCookies(WebDriver driver) {
    StringBuilder out = new StringBuilder();
    out.append("── C. 쿠키 (이름만 — 값은 읽지 않는다) ──\n");
    try {
      Set<Cookie> cookies = driver.manage().getCookies();
      out.append("  ").append(cookies.size()).append("개\n");
      for (Cookie cookie : cookies) {
        out.append("   ").append(cookie.getName());
        out.append("  도메인=").append(cookie.getDomain());
        out.append("  ").append(cookie.getExpiry() == null ? "세션" : "영속");
        out.append('\n');
      }
    } catch (RuntimeException e) {
      out.append("  읽기 실패 — ").append(e.getClass().getSimpleName()).append('\n');
    }
    return out.toString();
  }

  // ── 판독 ────────────────────────────────────────────────────────────────

  private static String interpretation(boolean reachedByHuman, Boolean reachedByUrl) {
    StringBuilder out = new StringBuilder("해석 :\n");
    if (!reachedByHuman) {
      out.append("  목표 화면을 확인하지 못했다. 아래 표는 다른 화면일 수 있으므로 셀렉터로 쓰지 말 것.\n");
      return out.toString();
    }
    out.append("  A 의 표에서 행 수가 가장 많고 td 형태가 날짜·금액으로 읽히는 것이 거래 목록이다.\n");
    out.append("  '프레임' 이 0 이 아니면 표·폼·링크는 최상위 문서만의 것이다 — 목록이 안 보이면 그것부터 본다.\n");
    if (reachedByUrl == null) {
      out.append("  주소로 직접 진입은 예외로 재지 못했다 — 되는지 안 되는지 판정하지 않는다.\n");
    } else if (reachedByUrl) {
      out.append("  주소로 직접 진입이 되므로 수집기는 로그인 뒤 그 URL 로 바로 갈 수 있다.\n");
      out.append("  B 가 비어 있고 A 만 차 있으면 조회 조건이 필요한 화면이다 — 폼 항목을 채워야 한다.\n");
    } else {
      out.append("  주소로 직접 진입이 되지 않는다 — 수집기가 메뉴를 눌러 들어가야 하고 셀렉터가 더 필요하다.\n");
    }
    out.append("  td 형태에 8자리 연속 숫자나 긴 식별자가 있으면 serial 꼬리 필드 후보다.\n");
    out.append("  그것이 있으면 '구매일자_구매금액' 합성을 물려받지 않아도 된다(같은 날 같은 금액 충돌이 사라진다).\n");
    return out.toString();
  }

  // ── 도구 ────────────────────────────────────────────────────────────────

  /** 주소에 표식이 나타날 때까지 기다린다. 사람이 조작하는 중이라 예외는 흔하다 — 삼키고 계속 본다. */
  private static boolean awaitUrlMarker(WebDriver driver, String marker, Duration timeout)
      throws InterruptedException {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      try {
        String url = driver.getCurrentUrl();
        if (url != null && url.contains(marker)) {
          // 표가 그려질 여유를 준다. 주소가 바뀌는 시점과 렌더가 끝나는 시점은 다르다.
          TimeUnit.SECONDS.sleep(3);
          return true;
        }
      } catch (RuntimeException ignore) {
        // 사람이 페이지를 넘기는 중이면 잠깐 읽히지 않는다.
      }
      if (!browserAlive(driver)) {
        return false;
      }
      TimeUnit.MILLISECONDS.sleep(POLL.toMillis());
    }
    return false;
  }

  /**
   * 조회 결과 행이 그려질 때까지 기다린다.
   *
   * <p><b>데이터 행의 정의</b>: 값이 들어 있는 {@code td} 가 둘 이상인 {@code tr}. 머리글만 있는 표와, '조회된 자료가 없습니다' 처럼
   * colspan 한 칸짜리 안내 행을 둘 다 걸러낸다 — 첫 실측에서 얻은 것이 정확히 그 한 칸이었다.
   */
  private static boolean awaitDataRow(WebDriver driver, Target target, Duration timeout)
      throws InterruptedException {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      if (hasDataRow(driver, target)) {
        // 표가 다 그려질 여유를 준다 — 첫 행만 붙은 순간에 읽으면 열 형태를 덜 본다.
        TimeUnit.SECONDS.sleep(2);
        return true;
      }
      if (!browserAlive(driver)) {
        return false;
      }
      TimeUnit.MILLISECONDS.sleep(POLL.toMillis());
    }
    return false;
  }

  private static boolean hasDataRow(WebDriver driver, Target target) {
    WebElement list = findListTable(driver, target);
    if (list != null && filledRowCount(list) > 0) {
      return true;
    }
    // 머리글을 아직 모르는 대상(온라인)은 목록이 표가 아닐 수 있다. 표만 보고 기다리면
    // 실제로는 목록이 그려졌는데도 시간이 다 갈 때까지 못 알아챈다.
    return target.listHeaders.isEmpty() && hasFilledRepeatedGroup(driver);
  }

  /** 같은 모양이 여러 번 반복되고 그 안에 글자가 든 무리가 있는가 — 표가 아닌 목록의 신호. */
  private static boolean hasFilledRepeatedGroup(WebDriver driver) {
    java.util.Map<String, Integer> counts = new java.util.HashMap<>();
    java.util.Map<String, Boolean> filled = new java.util.HashMap<>();

    for (WebElement el : findAll(driver, By.cssSelector("li[class], div[class], article"))) {
      String cls = attr(el, "class");
      if ("(없음)".equals(cls) || "(읽기 실패)".equals(cls)) {
        continue;
      }
      String key = safely(el::getTagName) + "." + DomShapeReport.maskDigits(clip(cls));
      counts.merge(key, 1, Integer::sum);
      if (Boolean.FALSE.equals(filled.get(key))) {
        continue;
      }
      String text = safely(el::getText);
      filled.put(key, !text.isBlank() && text.length() > 10);
    }
    for (var e : counts.entrySet()) {
      if (e.getValue() >= REPEAT_MIN && Boolean.TRUE.equals(filled.get(e.getKey()))) {
        return true;
      }
    }
    return false;
  }

  /**
   * 거래 목록 표를 <b>머리글로</b> 골라낸다.
   *
   * <p>화면 전체에서 "값이 든 칸이 둘 이상인 행" 을 찾으면 안 된다. 날짜 입력칸을 누르면 뜨는 <b>달력 위젯도 표</b>이고, 그 요일 줄(일·월·화…)이 그
   * 조건을 그대로 만족한다. 실제로 첫 판정이 거기 걸려 거래 목록이 비어 있는데도 '결과 행 확인' 으로 끝났다 — 막으려던 실패를 판정식이 만들어 낸 것이다.
   *
   * <p>머리글은 사이트가 모두에게 똑같이 주는 구조라 실측값을 그대로 조건으로 쓸 수 있다.
   */
  private static WebElement findListTable(WebDriver driver, Target target) {
    if (!target.listHeaders.isEmpty()) {
      for (WebElement table : findAll(driver, By.tagName("table"))) {
        List<String> headers = new ArrayList<>();
        for (WebElement th : findAll(table, By.tagName("th"))) {
          headers.add(safely(th::getText).trim());
        }
        if (headers.containsAll(target.listHeaders)) {
          return table;
        }
      }
      return null;
    }

    // 머리글을 아직 실측하지 못한 대상(온라인)은 구조로 고른다 — 값이 든 행이 가장 많은 표.
    // 달력은 이름으로 제외한다. 이 갈래는 머리글을 알아내기 위한 것이므로, 실측한 뒤에는
    // 위쪽 갈래로 옮겨 고정하는 것이 맞다.
    WebElement best = null;
    int bestRows = 0;
    for (WebElement table : findAll(driver, By.tagName("table"))) {
      if (attr(table, "class").contains("datepicker")) {
        continue;
      }
      int rows = filledRowCount(table);
      if (rows > bestRows) {
        bestRows = rows;
        best = table;
      }
    }
    return best;
  }

  /** {@code textContent} 를 형태로 뜰 때의 길이 상한. 접힌 행에는 품목이 통째로 들어 있을 수 있다. */
  private static final int HIDDEN_TEXT_LIMIT = 400;

  /**
   * 보이지 않는 글자를 형태로 남긴다.
   *
   * <p>{@code getText()} 는 <b>보이는</b> 글자만 주고, {@code textContent} 는 숨어 있어도 준다. 접힌 상품 목록은 앞쪽에서
   * {@code (빈칸)} 으로 읽히므로 이 비교가 없으면 "품목이 없다" 로 오독한다.
   *
   * <p>둘이 같은 칸은 싣지 않는다 — 이미 위 줄에 있는 것을 두 번 적을 이유가 없다.
   *
   * @param cells 한 행의 td 들
   * @param visible 같은 순서로 읽어 둔 {@code getText()} 결과
   * @return {@code [0] 가가가 NN가 …} 형태. 숨은 글자가 없으면 빈 문자열
   */
  private static String hiddenTextShapes(List<WebElement> cells, List<String> visible) {
    List<String> parts = new ArrayList<>();
    for (int i = 0; i < cells.size(); i++) {
      String dom = attr(cells.get(i), "textContent");
      if (dom == null || "(없음)".equals(dom) || "(읽기 실패)".equals(dom)) {
        continue;
      }
      String seen = i < visible.size() ? visible.get(i) : "";
      if (dom.trim().equals(seen == null ? "" : seen.trim())) {
        continue; // 보이는 것과 같다 — 숨은 것이 없다
      }
      boolean truncated = dom.length() > HIDDEN_TEXT_LIMIT;
      String sample = truncated ? dom.substring(0, HIDDEN_TEXT_LIMIT) : dom;
      parts.add(
          "["
              + i
              + "] "
              + DomShapeReport.shape(sample)
              + (truncated ? " …(" + dom.length() + "자)" : ""));
    }
    return String.join(" | ", parts);
  }

  /** 이 길이를 넘는 칸은 '무언가 담고 있는 칸' 으로 보고 내부 구조를 훑는다. */
  private static final int LONG_CELL_THRESHOLD = 200;

  /**
   * 글자가 많이 든 칸의 <b>내부 태그 구성</b>을 훑는다.
   *
   * <p>펼쳐지는 상품 목록은 칸 하나 안에 통째로 들어 있다. 그 안이 {@code li} 나 {@code div} 로 품목마다 나뉘어 있으면 파서는 요소를 훑으면 되고,
   * {@code br} 로만 나뉜 통짜 텍스트면 정규식으로 갈라야 한다. <b>둘은 실패 방식이 다르다</b> — 요소 기반은 어긋나면 0건이 되고, 정규식 기반은 어긋나면
   * 엉뚱한 값이 조용히 들어온다. 어느 쪽을 쓸지는 추측이 아니라 이 측정으로 정한다.
   *
   * @param cells 한 행의 td 들
   * @return {@code [0] div=21, span=42} 형태. 대상이 없으면 빈 문자열
   */
  private static String longCellStructures(List<WebElement> cells) {
    List<String> parts = new ArrayList<>();
    for (int i = 0; i < cells.size(); i++) {
      String dom = attr(cells.get(i), "textContent");
      if (dom == null || dom.length() < LONG_CELL_THRESHOLD) {
        continue;
      }
      List<String> tags = new ArrayList<>();
      for (WebElement child : findAll(cells.get(i), By.cssSelector("*"))) {
        tags.add(safely(child::getTagName));
      }
      String census = DomShapeReport.tagCensus(tags);
      parts.add("[" + i + "] " + (census.isEmpty() ? "자손 없음 (통짜 텍스트)" : census));
    }
    return String.join(" | ", parts);
  }

  /**
   * 행의 각 칸 <b>안에</b> 있는 조작 요소를 훑는다.
   *
   * <p>거래 목록의 마지막 칸처럼 <b>글자가 없는 칸</b>이 실제로는 상세보기 링크나 아이콘 버튼인 경우가 있다. 목록에 품목이 없는 화면에서는 그 진입점이 있느냐가
   * 무엇을 수집할 수 있는지를 가른다.
   *
   * @param cells 한 행의 td 들
   * @return {@code [5] a href=… , img} 형태. 아무 것도 없으면 빈 문자열
   */
  private static String cellControls(List<WebElement> cells) {
    List<String> parts = new ArrayList<>();
    for (int i = 0; i < cells.size(); i++) {
      List<String> found = new ArrayList<>();
      for (WebElement a : findAll(cells.get(i), By.tagName("a"))) {
        String href = attr(a, "href");
        String shown =
            href.startsWith("javascript:")
                ? DomShapeReport.callSignature(clip(href))
                : DomShapeReport.maskLongDigitRuns(DomShapeReport.safeUrl(href));
        // href 가 javascript:void(0) 이면 실제 동작은 onclick 에 있다. 그 함수 이름이 없으면
        // 수집기가 상세로 들어갈 방법을 알 수 없다 — 목록에 품목이 없는 화면에서는 이것이 전부다.
        found.add("a href=" + shown + onClickOf(a));
      }
      for (WebElement img : findAll(cells.get(i), By.tagName("img"))) {
        found.add("img alt=" + DomShapeReport.shape(attr(img, "alt")));
      }
      for (WebElement button : findAll(cells.get(i), By.tagName("button"))) {
        found.add("button id=" + DomShapeReport.maskDigits(attr(button, "id")) + onClickOf(button));
      }
      for (WebElement input : findAll(cells.get(i), By.cssSelector("input"))) {
        found.add("input type=" + attr(input, "type"));
      }
      if (!found.isEmpty()) {
        parts.add("[" + i + "] " + String.join(", ", found));
      }
    }
    return String.join(" | ", parts);
  }

  /**
   * {@code onclick} 의 <b>함수 이름만</b> 읽는다.
   *
   * <p>구형 사이트는 {@code href="javascript:void(0)"} 에 실제 동작을 {@code onclick} 으로 붙인다. 수집기가 상세로 들어가려면 그
   * 함수 이름을 알아야 하는데, 인자에는 거래번호·상품명이 실리므로 {@link DomShapeReport#callSignature} 로 인자를 버린다.
   *
   * @return {@code onclick=fnView(…)} 형태(앞에 공백 한 칸). 없으면 빈 문자열
   */
  private static String onClickOf(WebElement element) {
    String onclick = attr(element, "onclick");
    if (onclick == null || "(없음)".equals(onclick) || "(읽기 실패)".equals(onclick)) {
      return "";
    }
    // 함수 호출이 아니면(예: location.href='/d?nm=우유') 인자를 버릴 괄호가 없다.
    // callSignature 의 '괄호 없음' 갈래는 숫자만 가리므로 그런 값은 형태로 떨어뜨린다.
    if (!onclick.contains("(")) {
      return " onclick=" + DomShapeReport.shape(clip(onclick));
    }
    return " onclick=" + DomShapeReport.callSignature(clip(onclick));
  }

  /** 값이 든 칸이 둘 이상인 행의 수. 머리글만 있는 표와 안내문 한 칸짜리 행을 둘 다 걸러낸다. */
  private static int filledRowCount(WebElement table) {
    int rows = 0;
    for (WebElement row : findAll(table, By.tagName("tr"))) {
      int filled = 0;
      for (WebElement td : findAll(row, By.tagName("td"))) {
        String text = safely(td::getText);
        if (!text.isBlank() && !"(읽기 실패)".equals(text)) {
          filled++;
        }
      }
      if (filled >= 2) {
        rows++;
      }
    }
    return rows;
  }

  private static void announce(Target target) {
    System.out.println();
    System.out.println("========================================================");
    System.out.println("  [하나로 실측] " + target.label);
    System.out.println("  순정 Chrome 이 뜹니다. 아래를 직접 해 주세요.");
    System.out.println();
    for (String step : target.humanSteps) {
      System.out.println("    " + step);
    }
    System.out.println();
    System.out.println("  ★★ 창을 직접 닫지 마세요. 다 끝나면 프로브가 창을 스스로 닫습니다.");
    System.out.println("     창이 저절로 닫히는 것이 '끝났다' 는 신호입니다.");
    System.out.println("     먼저 닫으면 그 회차는 버려지고 로그인을 한 번 헛쓰게 됩니다.");
    System.out.println();
    System.out.println("  ★ 목록에 '행이 실제로 보일 때까지' 기다립니다 — 조회 버튼을 꼭 눌러 주세요.");
    System.out.println("    결과가 0건이면 프로브는 실패로 끝납니다(기간을 넓혀 다시 조회해 주세요).");
    System.out.println();
    System.out.println("  프로브는 화면의 '형태' 만 읽습니다 — 값도, 비밀번호도 읽지 않습니다.");
    System.out.println(
        "  대기 상한: 화면 도달 "
            + HUMAN_TIMEOUT.toMinutes()
            + "분 + 결과 행 "
            + DATA_ROW_TIMEOUT.toMinutes()
            + "분.");
    System.out.println("========================================================");
    System.out.println();
  }

  /** 조상 4대의 태그·id 를 훑는다 — 표를 고를 XPath 를 쓰려면 위쪽 id 가 필요하다. */
  private static String ancestry(WebElement element) {
    List<String> chain = new ArrayList<>();
    WebElement cursor = element;
    for (int i = 0; i < 4; i++) {
      try {
        cursor = cursor.findElement(By.xpath("./.."));
      } catch (RuntimeException e) {
        break;
      }
      String tag = safely(cursor::getTagName);
      String id = attr(cursor, "id");
      chain.add(tag + (id == null || "(없음)".equals(id) ? "" : "#" + DomShapeReport.maskDigits(id)));
      if ("html".equalsIgnoreCase(tag)) {
        break;
      }
    }
    return chain.isEmpty() ? "(없음)" : String.join(" < ", chain);
  }

  /**
   * 브라우저가 아직 살아 있는가.
   *
   * <p><b>대기 루프가 이것을 봐야 하는 이유.</b> 대기 루프는 사람이 조작하는 중의 일시적인 읽기 실패를 삼키도록 돼 있다. 그런데 창이 <b>닫혀</b> 버린
   * 경우도 똑같이 읽기 실패로 보이므로, 구분하지 않으면 죽은 브라우저를 상대로 남은 타임아웃을 전부 태운다 — 실제로 한 회차가 그렇게 40분을 헛돌았다.
   *
   * <p>세션이 끊긴 것과 잠깐 안 읽히는 것은 예외 종류로 갈린다.
   */
  private static boolean browserAlive(WebDriver driver) {
    try {
      return !driver.getWindowHandles().isEmpty();
    } catch (NoSuchSessionException | NoSuchWindowException e) {
      return false;
    } catch (RuntimeException e) {
      // 그 밖의 실패는 일시적인 것으로 본다 — 살아 있다고 답해 대기를 이어 간다.
      return true;
    }
  }

  /** 예외를 기록용 한 줄로. 메시지에 주소가 섞여 있으면 종류만 남긴다. */
  private static String describeFailure(RuntimeException e) {
    String type = e.getClass().getSimpleName();
    String message = e.getMessage();
    if (message == null || message.isBlank()) {
      return type;
    }
    String firstLine = message.lines().findFirst().orElse("").trim();
    if (firstLine.contains("://")) {
      return type + " (메시지에 주소가 있어 생략)";
    }
    return type + " — " + clip(firstLine);
  }

  /** 최상위 문서의 프레임 수. 0 이 아니면 이 화면의 표는 프레임 안에 있을 수 있다. */
  private static int frameCount(WebDriver driver) {
    return findAll(driver, By.tagName("iframe")).size()
        + findAll(driver, By.tagName("frame")).size();
  }

  private static List<WebElement> findAll(WebDriver driver, By by) {
    try {
      return driver.findElements(by);
    } catch (RuntimeException e) {
      return List.of();
    }
  }

  private static List<WebElement> findAll(WebElement parent, By by) {
    try {
      return parent.findElements(by);
    } catch (RuntimeException e) {
      return List.of();
    }
  }

  private static List<String> attributes(List<WebElement> elements, String name) {
    List<String> out = new ArrayList<>();
    for (WebElement el : elements) {
      String value = attr(el, name);
      if (value != null && !"(없음)".equals(value)) {
        out.add(value);
      }
    }
    return out;
  }

  private static String attr(WebElement element, String name) {
    try {
      String value = element.getAttribute(name);
      return (value == null || value.isBlank()) ? "(없음)" : value.trim();
    } catch (RuntimeException e) {
      return "(읽기 실패)";
    }
  }

  /** 긴 값은 자른다 — class 하나가 요약을 다 덮는 일이 있다. */
  private static String clip(String value) {
    if (value == null) {
      return "(없음)";
    }
    return value.length() <= 80 ? value : value.substring(0, 80) + "…";
  }

  /** 사람이 조작 중인 브라우저라 어떤 읽기든 실패할 수 있다. 실패를 값으로 접지 않는다. */
  private static String safely(java.util.function.Supplier<String> read) {
    try {
      String value = read.get();
      return value == null ? "(읽기 실패)" : value;
    } catch (RuntimeException e) {
      return "(읽기 실패)";
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

package com.jiniebox.jangbogo.svc.mall;

import com.jiniebox.jangbogo.svc.util.NativeChromeLoginLauncher;
import com.jiniebox.jangbogo.svc.util.WebDriverManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * 오아시스마켓 주문목록 화면의 <b>조회 범위 수단</b>을 실측한다.
 *
 * <h2>왜 필요한가</h2>
 *
 * <p>{@code Oasis.navigatePurchased} 는 주문목록으로 이동해 <b>그 순간 보이는 행만</b> 훑는다.
 *
 * <pre>
 * driver.navigate().to("https://www.oasis.co.kr/myPage/orderList");
 * List&lt;WebElement&gt; rows = orderListOuter.findElements(By.xpath("//div[@class='mypageOrderstatus']"));
 * </pre>
 *
 * <p><b>기간을 지정하지 않는다</b> — 사이트 기본 구간이 무엇이든 그것을 받는다. 그리고 <b>페이지를 넘기지 않는다</b> — 목록이 여러 쪽이면 첫 쪽만 들어온다.
 * 둘 다 조용히 잘리는 형태라, 잘린 만큼은 워터마크 전진으로 <b>영구히 봉인된다</b>(SSG 가 최근 1개월에 묶여 두 달 전 주문 15건을 잃고 있던 것과 같은
 * 계열이다).
 *
 * <p>고치려면 이 화면이 <b>무엇을 주는지</b> 알아야 하는데, 저장소에도 문서에도 기록이 없다. 지금 아는 셀렉터는 목록 행과 상세 링크뿐이다.
 *
 * <h2>추측하면 안 되는 이유</h2>
 *
 * <p>바로 앞 두 사례가 근거다. 하나로는 {@code sf_m3} 같은 이름에서 뜻을 읽을 수 없었고(이름은 3, 뜻은 1개월), SSG 는 표준 {@code new
 * Event} 가 그 페이지에서만 죽었다. <b>실사이트를 밟기 전까지 테스트는 전부 초록이었다.</b> 여기서 "오아시스도 날짜 칸이 있겠지" 로 시작하면 같은 값을 또
 * 치른다.
 *
 * <h2>왜 사람이 로그인하는가</h2>
 *
 * <p>{@code SsgPeriodProbe} 와 같다. 프로브는 <b>자격증명을 다루지 않는다</b> — 순정 Chrome 을 띄워 사람이 직접 로그인하고, 프로브는 그
 * 브라우저에 CDP 로 밖에서 붙어 화면만 읽는다. 자동 로그인이 한 번도 더 일어나지 않으므로 로그인 간격 규칙과도 무관하다.
 *
 * <h2>산출물에 무엇이 담기는가</h2>
 *
 * <p>읽는 화면은 <b>그 사람의 주문 목록</b>이고 이 저장소는 PUBLIC 이다. 그래서 주문 행은 <b>개수만 센다</b> — 상품명·금액·날짜를 옮기지 않는다. 폼
 * 안의 값과 선택 상자의 옵션은 {@link DomShapeReport} 로 형태만 옮긴다({@code 2026.06} → {@code NNNN.NN}). 그 형태가 곧 이
 * 화면이 요구하는 표기다.
 *
 * <p>산출물은 {@code build/} 아래에만 쓰고 <b>커밋하지 않는다</b>.
 *
 * <h2>무엇을 알아내야 구현할 수 있는가</h2>
 *
 * <ol>
 *   <li><b>기간을 정하는 수단이 있는가</b> — 날짜 칸인지, 연/월 선택 상자인지, 기간 탭인지. 셋은 조작 방법이 전혀 다르다
 *   <li><b>그 수단이 요구하는 표기</b> — 틀리면 사이트는 보통 예외가 아니라 <b>빈 목록</b>을 준다
 *   <li><b>페이지를 넘기는 수단이 있는가</b> — 쪽 번호인지, '더보기' 버튼인지, 무한 스크롤인지
 *   <li><b>기본으로 몇 행을 주는가</b> — 상한이 있으면 그 수에서 잘린다
 *   <li><b>조회 조건이 주소에 실리는가</b> — 실리면 폼을 밟지 않고 주소로 갈 수 있다
 *   <li><b>제공 범위 제한 안내가 있는가</b>
 * </ol>
 *
 * <h2>실행</h2>
 *
 * <pre>
 * gradlew test -PincludeProbe --tests "*OasisPeriodProbe*"
 * </pre>
 *
 * <p>순정 Chrome 이 뜬다. <b>로그인하고 주문목록 화면까지 들어간 뒤 창을 그대로 둔다.</b>
 *
 * @author KIUNSEA
 */
@Tag("probe")
class OasisPeriodProbe {

  /** 주문목록 화면. {@code Oasis.navigatePurchased} 가 쓰는 주소 그대로다. */
  private static final String ORDER_LIST = "https://www.oasis.co.kr/myPage/orderList";

  /** 수집기가 목록 행으로 세는 셀렉터. 기본 노출 행 수를 이걸로 센다. */
  private static final By ORDER_ROW = By.cssSelector("div.mypageOrderstatus");

  private static final Duration HUMAN_TIMEOUT = Duration.ofMinutes(20);
  private static final Duration ATTACH_TIMEOUT = Duration.ofSeconds(60);
  private static final Duration POLL = Duration.ofMillis(500);

  @Test
  @DisplayName("주문목록의 기간·쪽넘김 수단 구조를 뜬다")
  void orderListRangeControlsShape() throws Exception {
    Path profile = Paths.get("build", "probe-profiles", "oasis-period");
    Files.createDirectories(profile);

    StringBuilder report = new StringBuilder();
    report.append("[오아시스 실측] 주문목록 조회 범위 수단\n\n");
    report.append("  값은 담지 않는다 — 폼 요소의 이름·형태만. 주문 행은 개수만 센다.\n");
    report.append("  목적: 기간을 정하는 법과 페이지를 넘기는 법을 정한다.\n\n");

    Process chrome = null;
    WebDriver attached = null;
    WebDriverManager manager = new WebDriverManager();

    try {
      chrome =
          NativeChromeLoginLauncher.launch(
              profile, "https://www.oasis.co.kr/", NativeChromeLoginLauncher.ANY_DEBUG_PORT);

      int port = NativeChromeLoginLauncher.awaitDevToolsPort(profile, ATTACH_TIMEOUT, POLL);
      if (port <= 0) {
        throw new IllegalStateException("DevTools 포트가 열리지 않아 붙을 수 없다.");
      }
      NativeChromeLoginLauncher.awaitDevToolsBrowser(port, ATTACH_TIMEOUT, POLL);
      attached = manager.attachToRunningChrome(NativeChromeLoginLauncher.debuggerAddress(port));
      manager.applyStealth(attached);

      System.out.println();
      System.out.println("  [probe] 뜬 Chrome 에서 오아시스에 로그인하고 '주문목록' 화면까지 들어가 주세요.");
      System.out.println("  [probe] 주소: " + ORDER_LIST);
      System.out.println("  [probe] 창을 닫지 마세요.");
      System.out.println();

      awaitOrderList(attached);
      TimeUnit.SECONDS.sleep(2);

      report.append(describeUrl(attached));
      report.append('\n').append(describeDateInputs(attached));
      report.append('\n').append(describeSelects(attached));
      report.append('\n').append(describePeriodControls(attached));
      report.append('\n').append(describePaging(attached));
      report.append('\n').append(describeRowCount(attached));
      report.append('\n').append(describeRangeNotice(attached));

    } catch (RuntimeException | InterruptedException e) {
      report.append("\n  중단됨 : ").append(e.getClass().getSimpleName()).append('\n');
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
        record(report.toString());
      } catch (IOException io) {
        System.out.println("[probe] 기록 실패: " + io.getClass().getSimpleName());
      }
      System.out.println(report);
    }
  }

  /** 사람이 주문목록에 도착할 때까지 기다린다. 주소로 판정한다 — 지금 확실히 아는 신호가 그것뿐이다. */
  private static void awaitOrderList(WebDriver driver) throws InterruptedException {
    Instant deadline = Instant.now().plus(HUMAN_TIMEOUT);
    while (Instant.now().isBefore(deadline)) {
      try {
        String url = driver.getCurrentUrl();
        if (url != null && url.contains("orderList")) {
          return;
        }
      } catch (RuntimeException ignore) {
        // 사람이 탭을 옮기는 중일 수 있다
      }
      TimeUnit.MILLISECONDS.sleep(POLL.toMillis());
    }
    throw new IllegalStateException("주문목록 화면에 도달하지 않았다 (사람 대기 시간 초과).");
  }

  /** 날짜로 보이는 입력칸. SSG 는 여기서 답이 나왔다. */
  private static String describeDateInputs(WebDriver driver) {
    StringBuilder sb = new StringBuilder("P1. 날짜 입력칸\n");
    int found = 0;

    for (WebElement in : driver.findElements(By.tagName("input"))) {
      try {
        String id = attr(in, "id");
        String name = attr(in, "name");
        String type = attr(in, "type");
        String haystack = (id + " " + name + " " + attr(in, "class")).toLowerCase();
        if (!"date".equals(type)
            && !haystack.matches(".*(dt|date|start|end|from|to|begin|term|period|cal).*")) {
          continue;
        }
        found++;
        sb.append("     - id=")
            .append(blankAs(id))
            .append(" name=")
            .append(blankAs(name))
            .append(" type=")
            .append(blankAs(type))
            .append('\n');
        sb.append("       값형태=")
            .append(DomShapeReport.shape(attr(in, "value")))
            .append("  readonly=")
            .append(!attr(in, "readonly").isEmpty())
            .append("  보임=")
            .append(safeDisplayed(in))
            .append('\n');
      } catch (RuntimeException stale) {
        // 요소 하나 때문에 전체 측정을 버리지 않는다
      }
    }
    if (found == 0) {
      sb.append("     (없음) — 날짜 칸이 아니라면 선택 상자나 탭이다. P2·P3 를 본다.\n");
    }
    return sb.toString();
  }

  /** 선택 상자. 국내 몰의 주문목록은 연/월 드롭다운을 쓰는 경우가 많다. */
  private static String describeSelects(WebDriver driver) {
    StringBuilder sb = new StringBuilder("P2. 선택 상자 (연/월 후보)\n");
    List<WebElement> selects = driver.findElements(By.tagName("select"));

    if (selects.isEmpty()) {
      sb.append("     (없음)\n");
      return sb.toString();
    }
    for (WebElement sel : selects) {
      try {
        List<WebElement> options = sel.findElements(By.tagName("option"));
        sb.append("     - id=")
            .append(blankAs(attr(sel, "id")))
            .append(" name=")
            .append(blankAs(attr(sel, "name")))
            .append(" 옵션=")
            .append(options.size())
            .append("개  보임=")
            .append(safeDisplayed(sel))
            .append('\n');
        // 옵션 글자는 형태만. 앞 세 개면 표기 규칙을 알기에 충분하다.
        StringBuilder shapes = new StringBuilder();
        for (int i = 0; i < Math.min(3, options.size()); i++) {
          shapes.append(DomShapeReport.headerLabel(options.get(i).getText())).append(' ');
        }
        sb.append("       옵션형태= ").append(shapes.toString().trim()).append('\n');
      } catch (RuntimeException stale) {
        // 무시
      }
    }
    return sb.toString();
  }

  /** 기간 탭·버튼·라벨. '최근 3개월' 같은 글자를 단 조작 지점을 찾는다. */
  private static String describePeriodControls(WebDriver driver) {
    StringBuilder sb = new StringBuilder("P3. 기간 탭·버튼\n");
    try {
      Object found =
          ((JavascriptExecutor) driver)
              .executeScript(
                  "var out=[];"
                      + "document.querySelectorAll('a,button,label,li,span').forEach(function(e){"
                      + "  var t=(e.textContent||'').trim();"
                      + "  if(t.length<=12 && /(개월|１년|1년|전체|기간|주일|오늘)/.test(t)){"
                      + "    out.push(t+' ['+e.tagName.toLowerCase()"
                      + "      +(e.id?'#'+e.id:'')+(e.className?'.'+String(e.className).split(' ')[0]:'')+']');"
                      + "  }"
                      + "});"
                      + "return out.slice(0,12);");
      List<?> list = found instanceof List ? (List<?>) found : List.of();
      if (list.isEmpty()) {
        sb.append("     (없음) — 기간을 고르는 조작 지점이 화면에 없다는 뜻이다.\n");
      }
      for (Object t : list) {
        sb.append("     - ").append(DomShapeReport.maskDigits(String.valueOf(t))).append('\n');
      }
    } catch (RuntimeException e) {
      sb.append("     측정 실패: ").append(e.getClass().getSimpleName()).append('\n');
    }
    return sb.toString();
  }

  /**
   * 쪽 넘김 수단.
   *
   * <p><b>기간과 따로 재는 이유.</b> 기간을 넓혀도 첫 쪽만 읽으면 넓힌 만큼이 그대로 잘린다 — 그리고 잘린 구간은 워터마크가 전진하면서 영구히 봉인된다. 둘은
   * 함께 있어야 의미가 있다.
   */
  private static String describePaging(WebDriver driver) {
    StringBuilder sb = new StringBuilder("P4. 쪽 넘김 수단\n");
    for (String selector :
        List.of(
            ".paging",
            ".pagination",
            ".paginate",
            "[class*='paging']",
            "[class*='page']",
            "[class*='more']",
            "button[class*='more']",
            "a[class*='next']")) {
      int n = driver.findElements(By.cssSelector(selector)).size();
      if (n > 0) {
        sb.append("     - ").append(selector).append(" : ").append(n).append("개\n");
      }
    }
    try {
      Object more =
          ((JavascriptExecutor) driver)
              .executeScript(
                  "var out=[];"
                      + "document.querySelectorAll('a,button').forEach(function(e){"
                      + "  var t=(e.textContent||'').trim();"
                      + "  if(t.length<=10 && /(더보기|더 보기|다음|이전|더불러|모두)/.test(t)){"
                      + "    out.push(t+' ['+e.tagName.toLowerCase()"
                      + "      +(e.id?'#'+e.id:'')+(e.className?'.'+String(e.className).split(' ')[0]:'')+']');"
                      + "  }"
                      + "});"
                      + "return out.slice(0,8);");
      List<?> list = more instanceof List ? (List<?>) more : List.of();
      for (Object t : list) {
        sb.append("     - 글자: ").append(String.valueOf(t)).append('\n');
      }
      if (sb.indexOf("- ") < 0) {
        sb.append("     (없음) — 쪽 넘김 수단이 없으면 무한 스크롤일 수 있다. 스크롤 뒤 행 수 변화를 봐야 한다.\n");
      }
    } catch (RuntimeException e) {
      sb.append("     측정 실패: ").append(e.getClass().getSimpleName()).append('\n');
    }
    return sb.toString();
  }

  /**
   * 기본으로 보이는 주문 행 수. <b>개수만 센다 — 내용은 읽지 않는다.</b>
   *
   * <p>스크롤을 내려 본 뒤 다시 센다. 수가 늘면 무한 스크롤이고, 그러면 지금 코드는 첫 화면만 읽고 있다는 뜻이다.
   */
  private static String describeRowCount(WebDriver driver) {
    StringBuilder sb = new StringBuilder("P5. 주문 행 수 (내용은 읽지 않는다)\n");
    try {
      int before = driver.findElements(ORDER_ROW).size();
      sb.append("     - 진입 직후 : ").append(before).append("행\n");

      ((JavascriptExecutor) driver)
          .executeScript("window.scrollTo(0, document.body.scrollHeight);");
      TimeUnit.SECONDS.sleep(3);
      int after = driver.findElements(ORDER_ROW).size();
      sb.append("     - 맨 아래로 스크롤 후 : ").append(after).append("행");
      sb.append(after > before ? "   <-- 늘었다: 무한 스크롤이다\n" : "   (변화 없음)\n");
    } catch (RuntimeException | InterruptedException e) {
      sb.append("     측정 실패: ").append(e.getClass().getSimpleName()).append('\n');
    }
    return sb.toString();
  }

  /** "최근 N개월까지" 같은 안내. 있으면 조회 범위의 상한을 알 수 있다. */
  private static String describeRangeNotice(WebDriver driver) {
    StringBuilder sb = new StringBuilder("P6. 조회 범위 제한 안내\n");
    try {
      Object found =
          ((JavascriptExecutor) driver)
              .executeScript(
                  "var out=[];"
                      + "document.querySelectorAll('p,span,em,li,div').forEach(function(e){"
                      + "  var t=(e.textContent||'').trim();"
                      + "  if(t.length<80 && /(최대|최근|이내|까지).*(개월|일|년)/.test(t)) out.push(t);"
                      + "});"
                      + "return out.slice(0,8);");
      List<?> list = found instanceof List ? (List<?>) found : List.of();
      if (list.isEmpty()) {
        sb.append("     (없음)\n");
      }
      for (Object t : list) {
        sb.append("     - ").append(DomShapeReport.maskDigits(String.valueOf(t))).append('\n');
      }
    } catch (RuntimeException e) {
      sb.append("     측정 실패: ").append(e.getClass().getSimpleName()).append('\n');
    }
    return sb.toString();
  }

  /** 주소는 질의 문자열의 값을 지운다 — 조회 조건과 세션 토큰이 거기 실린다. */
  private static String describeUrl(WebDriver driver) {
    return "U0. 사람이 도착한 주소\n     " + DomShapeReport.safeUrl(driver.getCurrentUrl()) + "\n";
  }

  private static String attr(WebElement e, String name) {
    try {
      String v = e.getAttribute(name);
      return v == null ? "" : v;
    } catch (RuntimeException stale) {
      return "";
    }
  }

  private static boolean safeDisplayed(WebElement e) {
    try {
      return e.isDisplayed();
    } catch (RuntimeException stale) {
      return false;
    }
  }

  private static String blankAs(String v) {
    return v == null || v.isEmpty() ? "(없음)" : v;
  }

  /** {@code build/} 아래에만 쓴다. 커밋 대상이 아니다. */
  private static void record(String report) throws IOException {
    Path dir = Paths.get("build", "probe-reports");
    Files.createDirectories(dir);
    Path out = dir.resolve("OASIS-PERIOD.txt");
    Files.writeString(out, report, StandardCharsets.UTF_8);
    System.out.println("[probe] 기록: " + out.toAbsolutePath());
  }
}

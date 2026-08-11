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
 * SSG 구매내역 화면의 <b>조회 기간 UI</b> 를 실측한다.
 *
 * <h2>왜 필요한가</h2>
 *
 * <p>{@code Ssg.navigatePurchased} 는 매 회차 <b>최근 1개월 고정</b>으로 조회한다.
 *
 * <pre>
 * ClickUtil.safeClick(driver, By.xpath("//label[@for='sf_m3']")); // 단기간 조회 (1개월전부터 지금까지)
 * </pre>
 *
 * <p>시작점이 언제나 "오늘 기준 1개월 전" 이라 <b>되돌아가지 않는다.</b> 앱이 한 달 넘게 돌지 않으면 그 사이의 구매는 다음 회차에도 조회 범위 밖이고,
 * <b>영영 들어오지 않는다.</b> {@link CollectPeriod} 가 하나로에서 정확히 이 실패를 막으려고 만들어졌지만 SSG 에는 적용되지 않았다.
 *
 * <p>고치려면 <b>계산한 구간을 이 화면에 넣는 법</b>을 알아야 하는데, <b>저장소에도 문서에도 그 기록이 없다.</b> 아는 것은 프리셋 라벨 하나({@code
 * sf_m3})와 조회 버튼({@code _d_sch_button}) 뿐이고, 날짜 입력칸이 있는지·형식이 무엇인지·프리셋이 몇 종인지는 아무도 재 두지 않았다.
 *
 * <h2>추측하면 안 되는 이유</h2>
 *
 * <p>{@code sf_m3} 의 주석은 그 값이 <b>1개월</b>이라고 적고 있다. 이름은 3인데 뜻은 1이다 — 이름에서 뜻을 유추할 수 없다는 증거가 이미 코드 안에
 * 있다. 여기서 {@code sf_m6} 을 "6개월이겠지" 하고 넣으면 엉뚱한 구간을 조회하고, 그 결과는 <b>0건이거나 조용히 모자란 목록</b>이라 실패로 드러나지
 * 않는다. 이 저장소가 반복해서 대가를 치른 실패 모양이다.
 *
 * <h2>왜 사람이 로그인하는가</h2>
 *
 * <p>둘 다 이유다. 첫째, 프로브는 <b>자격증명을 다루지 않는다</b> — 순정 Chrome 을 띄워 사람이 직접 로그인하고, 프로브는 그 브라우저에 CDP 로 밖에서
 * 붙어 화면만 읽는다. 둘째, <b>{@code mall_signin_delay} 6시간 규칙</b>이다. 같은 계정으로 자동 로그인을 반복하는 것은 차단 패턴이고, 사람이
 * 이미 연 세션에 붙으면 로그인이 한 번도 더 일어나지 않는다.
 *
 * <h2>산출물에 무엇이 담기는가</h2>
 *
 * <p>읽는 화면은 <b>그 사람의 구매 내역</b>이고 이 저장소는 PUBLIC 이다. 그래서 이 프로브는 <b>조회 폼만</b> 본다 — 주문 행은 읽지 않는다. 폼 안의
 * 값(날짜 칸에 들어 있는 기본값 등)도 {@link DomShapeReport} 로 형태만 옮긴다({@code 2099.12.31} → {@code NNNN.NN.NN}).
 * 그 형태가 곧 <b>이 화면이 요구하는 날짜 표기</b>라, 구현에 필요한 것은 그것으로 충분하다.
 *
 * <p>산출물은 {@code build/} 아래에만 쓰고 <b>커밋하지 않는다</b>.
 *
 * <h2>무엇을 알아내야 구현할 수 있는가</h2>
 *
 * <ol>
 *   <li><b>날짜 입력칸이 있는가</b> — 있으면 {@code CollectPeriod} 가 계산한 구간을 그대로 넣는다
 *   <li><b>그 칸이 요구하는 표기</b> — {@code yyyy.MM.dd} 인지 {@code yyyyMMdd} 인지. 틀리면 사이트가 예외가 아니라 <b>빈
 *       목록</b>을 준다
 *   <li><b>{@code readonly} 인가</b> — 달력 위젯이면 {@code sendKeys} 가 먹지 않아 JS 로 넣고 change 를 쏘아야 한다
 *   <li><b>프리셋이 몇 종인가</b> — 날짜 칸이 없으면 가장 긴 프리셋이 차선책이 된다
 *   <li><b>조회가 주소에 실리는가</b> — 실리면 폼을 밟지 않고 주소로 갈 수 있다
 *   <li><b>최대 조회 범위 제한이 있는가</b> — 있으면 {@code CollectPeriod.split} 의 {@code maxDays} 가 그 값이다
 * </ol>
 *
 * <h2>실행</h2>
 *
 * <pre>
 * ./gradlew test -PincludeProbe --tests '*SsgPeriodProbe*'
 * </pre>
 *
 * <p>순정 Chrome 이 뜬다. <b>로그인하고 구매내역 화면까지 들어간 뒤 창을 그대로 둔다.</b> 조회 버튼은 누르지 않아도 된다 — 이 프로브가 보는 것은 폼이지
 * 결과가 아니다.
 *
 * @author KIUNSEA
 */
@Tag("probe")
class SsgPeriodProbe {

  /** 구매내역 화면. {@code Ssg.navigatePurchased} 가 쓰는 주소 그대로다. */
  private static final String PURCHASE_LIST =
      "https://www.ssg.com/myssg/productMng/purchaseList.ssg?menu=purchaseList";

  /** 사람이 로그인하고 구매내역까지 들어오는 것을 기다리는 상한. */
  private static final Duration HUMAN_TIMEOUT = Duration.ofMinutes(20);

  private static final Duration ATTACH_TIMEOUT = Duration.ofSeconds(60);
  private static final Duration POLL = Duration.ofMillis(500);

  /** 조회 폼이 있을 만한 자리. 하나만 보면 사이트가 감싸는 방식이 바뀌었을 때 통째로 놓친다. */
  private static final List<String> FORM_SCOPES =
      List.of("#cmSchForm", ".cm_sch_form", ".sch_form", "form", "#area_sch", ".my_sch");

  @Test
  @DisplayName("구매내역 조회 기간 UI 의 구조를 뜬다")
  void purchasePeriodFormShape() throws Exception {
    Path profile = Paths.get("build", "probe-profiles", "ssg-period");
    Files.createDirectories(profile);

    StringBuilder report = new StringBuilder();
    report.append("[SSG 실측] 구매내역 조회 기간 UI\n\n");
    report.append("  값은 담지 않는다 — 폼 요소의 이름·형태만. 주문 행은 읽지 않는다.\n");
    report.append("  목적: CollectPeriod 가 계산한 구간을 이 화면에 넣는 법을 정한다.\n\n");

    Process chrome = null;
    WebDriver attached = null;
    WebDriverManager manager = new WebDriverManager();

    try {
      chrome =
          NativeChromeLoginLauncher.launch(
              profile, "https://www.ssg.com/", NativeChromeLoginLauncher.ANY_DEBUG_PORT);

      int port = NativeChromeLoginLauncher.awaitDevToolsPort(profile, ATTACH_TIMEOUT, POLL);
      if (port <= 0) {
        throw new IllegalStateException("DevTools 포트가 열리지 않아 붙을 수 없다.");
      }
      NativeChromeLoginLauncher.awaitDevToolsBrowser(port, ATTACH_TIMEOUT, POLL);
      attached = manager.attachToRunningChrome(NativeChromeLoginLauncher.debuggerAddress(port));
      manager.applyStealth(attached);

      System.out.println();
      System.out.println("  [probe] 뜬 Chrome 에서 SSG 에 로그인하고 '구매내역' 화면까지 들어가 주세요.");
      System.out.println("  [probe] 조회 버튼은 누르지 않아도 됩니다. 창을 닫지 마세요.");
      System.out.println();

      awaitPurchaseList(attached);
      TimeUnit.SECONDS.sleep(2);

      report.append(describeUrl(attached, "U0. 사람이 도착한 주소"));
      report.append('\n').append(describePeriodInputs(attached));
      report.append('\n').append(describePresets(attached));
      report.append('\n').append(describeSearchButton(attached));
      report.append('\n').append(describeRangeNotice(attached));
      report.append('\n').append(describeFormScopes(attached));

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

  /** 사람이 구매내역 화면에 도착할 때까지 기다린다. 주소로 판정한다 — 지금 확실히 아는 신호가 그것뿐이다. */
  private static void awaitPurchaseList(WebDriver driver) throws InterruptedException {
    Instant deadline = Instant.now().plus(HUMAN_TIMEOUT);
    while (Instant.now().isBefore(deadline)) {
      try {
        String url = driver.getCurrentUrl();
        if (url != null && url.contains("purchaseList")) {
          return;
        }
      } catch (RuntimeException ignore) {
        // 사람이 탭을 옮기는 중일 수 있다
      }
      TimeUnit.MILLISECONDS.sleep(POLL.toMillis());
    }
    throw new IllegalStateException("구매내역 화면에 도달하지 않았다 (사람 대기 시간 초과).");
  }

  /**
   * <b>이 프로브의 본 측정.</b> 날짜로 보이는 입력칸을 찾아 이름·형식·수정 가능 여부를 적는다.
   *
   * <p>값은 {@link DomShapeReport#shape} 로 형태만 옮긴다 — 그 형태가 곧 이 화면이 요구하는 날짜 표기다.
   */
  private static String describePeriodInputs(WebDriver driver) {
    StringBuilder sb = new StringBuilder("P1. 날짜 입력칸 (본 측정)\n");
    List<WebElement> inputs = driver.findElements(By.tagName("input"));
    int found = 0;

    for (WebElement in : inputs) {
      try {
        String id = attr(in, "id");
        String name = attr(in, "name");
        String type = attr(in, "type");
        String haystack = (id + " " + name + " " + attr(in, "class")).toLowerCase();

        boolean dateLike =
            "date".equals(type)
                || haystack.matches(".*(dt|date|start|end|from|to|begin|term|period|cal).*");
        if (!dateLike) {
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
            .append("  placeholder=")
            .append(DomShapeReport.shape(attr(in, "placeholder")))
            .append('\n');
        sb.append("       readonly=")
            .append(attr(in, "readonly") != null && !attr(in, "readonly").isEmpty())
            .append("  disabled=")
            .append(!in.isEnabled())
            .append("  보임=")
            .append(safeDisplayed(in))
            .append("  maxlength=")
            .append(blankAs(attr(in, "maxlength")))
            .append('\n');
      } catch (RuntimeException stale) {
        // 요소 하나가 낡은 것으로 전체 측정을 버리지 않는다
      }
    }
    if (found == 0) {
      sb.append("     (없음) — 날짜 칸이 없다면 프리셋이 유일한 수단이다. P2 를 본다.\n");
    }
    return sb.toString();
  }

  /** 기간 프리셋 후보. {@code sf_} 로 시작하는 것과, 라벨이 가리키는 대상 전부를 본다. */
  private static String describePresets(WebDriver driver) {
    StringBuilder sb = new StringBuilder("P2. 기간 프리셋 (라벨 → 대상)\n");
    List<WebElement> labels = driver.findElements(By.tagName("label"));
    int found = 0;

    for (WebElement label : labels) {
      try {
        String forId = attr(label, "for");
        if (forId == null || forId.isEmpty()) {
          continue;
        }
        String text = label.getText();
        // 기간 프리셋으로 보이는 것만. 화면 전체 라벨을 다 적으면 산출물이 지도가 된다.
        if (!forId.startsWith("sf_") && !text.matches(".*(개월|일|년|기간|전체).*")) {
          continue;
        }
        found++;
        sb.append("     - for=")
            .append(forId)
            .append("  글자=")
            .append(DomShapeReport.headerLabel(text))
            .append("  보임=")
            .append(safeDisplayed(label))
            .append('\n');
      } catch (RuntimeException stale) {
        // 무시
      }
    }
    if (found == 0) {
      sb.append("     (없음) — 현재 코드가 누르는 sf_m3 조차 없다면 화면이 개편된 것이다.\n");
    }
    return sb.toString();
  }

  /** 조회 버튼. 현재 코드가 {@code _d_sch_button} 을 JS 클릭한다 — 그것이 아직 있는지부터 본다. */
  private static String describeSearchButton(WebDriver driver) {
    StringBuilder sb = new StringBuilder("P3. 조회 버튼\n");
    for (String selector :
        List.of("#_d_sch_button", "[id*='sch_button']", "button[type='submit']")) {
      List<WebElement> found = driver.findElements(By.cssSelector(selector));
      sb.append("     - ").append(selector).append(" : ").append(found.size()).append("개");
      if (!found.isEmpty()) {
        WebElement first = found.get(0);
        sb.append("  보임=")
            .append(safeDisplayed(first))
            .append("  onclick=")
            .append(DomShapeReport.callSignature(attr(first, "onclick")));
      }
      sb.append('\n');
    }
    return sb.toString();
  }

  /** "최대 N개월까지 조회" 같은 안내. 있으면 그 값이 {@code CollectPeriod.split(maxDays)} 가 된다. */
  private static String describeRangeNotice(WebDriver driver) {
    StringBuilder sb = new StringBuilder("P4. 조회 범위 제한 안내\n");
    try {
      Object text =
          ((JavascriptExecutor) driver)
              .executeScript(
                  "var out=[];"
                      + "document.querySelectorAll('p,span,em,li,div').forEach(function(e){"
                      + "  var t=(e.textContent||'').trim();"
                      + "  if(t.length<80 && /(최대|최근|이내|까지).*(개월|일|년)/.test(t)) out.push(t);"
                      + "});"
                      + "return out.slice(0,8);");
      List<?> found = text instanceof List ? (List<?>) text : List.of();
      if (found.isEmpty()) {
        sb.append("     (없음) — 상한이 안내되지 않으면 split 은 켜지 않는다(모르는 값을 넣지 않는다).\n");
      }
      for (Object t : found) {
        sb.append("     - ").append(DomShapeReport.maskDigits(String.valueOf(t))).append('\n');
      }
    } catch (RuntimeException e) {
      sb.append("     측정 실패: ").append(e.getClass().getSimpleName()).append('\n');
    }
    return sb.toString();
  }

  /** 폼이 어떤 껍데기에 들어 있는지. 셀렉터를 폼 안으로 좁힐 수 있으면 오탐이 준다. */
  private static String describeFormScopes(WebDriver driver) {
    StringBuilder sb = new StringBuilder("P5. 폼 껍데기 후보\n");
    for (String scope : FORM_SCOPES) {
      int n = driver.findElements(By.cssSelector(scope)).size();
      sb.append("     - ").append(scope).append(" : ").append(n).append("개\n");
    }
    return sb.toString();
  }

  /** 주소는 질의 문자열의 값을 지운다 — 기간과 세션 토큰이 거기 실린다. */
  private static String describeUrl(WebDriver driver, String title) {
    return title + "\n     " + DomShapeReport.safeUrl(driver.getCurrentUrl()) + "\n";
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
    Path out = dir.resolve("SSG-PERIOD.txt");
    Files.writeString(out, report, StandardCharsets.UTF_8);
    System.out.println("[probe] 기록: " + out.toAbsolutePath());
  }
}

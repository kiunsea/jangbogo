package com.jiniebox.jangbogo.svc.mall;

import com.jiniebox.jangbogo.dao.JbgCollectBreakerDataAccessObject;
import com.jiniebox.jangbogo.dao.JbgOrderDataAccessObject;
import com.jiniebox.jangbogo.svc.ifc.MallSession;
import com.jiniebox.jangbogo.svc.ifc.PurchasedCollector;
import com.jiniebox.jangbogo.svc.util.CollectPeriod;
import com.jiniebox.jangbogo.svc.util.CollectStep;
import com.jiniebox.jangbogo.svc.util.WebDriverManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoAlertPresentException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * 하나로마트 <b>오프라인</b> 거래내역 수집기 ({@code nhhanaro.co.kr}).
 *
 * <h2>왜 새로 만들었나</h2>
 *
 * <p>하나로마트가 서비스를 개편해 오프라인 거래내역을 {@code nonghyupmall.com} 에서 {@code nhhanaro.co.kr} 로 분리했다. 기존
 * {@link Hanaro} 는 구 주소만 탐색하므로 오프라인을 영영 못 가져온다 — 셀렉터가 깨진 것이 아니라 <b>대상이 옮겨 간 것</b>이다.
 *
 * <h2>이 화면의 성질 (2026-08-11 실측)</h2>
 *
 * <ul>
 *   <li>진입점 {@code nahh_70090.do}. 프레임 없음. 표는 화면에 <b>하나뿐</b>이다
 *   <li>조회는 <b>같은 주소로 POST</b> 한다 — {@code st_dt}/{@code ed_dt} 를 채우고 제출해야 목록이 나온다. 그래서 조회 전후의 주소가
 *       같고, 주소만으로는 "조회했는지" 를 알 수 없다
 *   <li>표는 <b>거래 행과 품목 행이 번갈아</b> 온다. 거래 행은 {@code td} 6칸, 품목 행은 colspan 한 칸이다
 *   <li><b>품목 행은 접혀 있지만 DOM 에는 이미 다 있다.</b> 사람이 우측 버튼으로 펼치기 전에도 {@code textContent} 로 전부 읽힌다 — 그래서
 *       <b>행마다 클릭하지 않는다</b>. 클릭 순회는 느리고, 페이지가 다시 그려질 때마다 요소가 낡아 깨진다
 *   <li>품목은 {@code ul} 하나 = 품목 하나, 그 안에 {@code li} 여섯 개다(두 거래에서 120/20 과 66/11 로 비율이 정확히 6이었다)
 * </ul>
 *
 * <h2>왜 품목을 li 자리 번호로 읽지 않는가</h2>
 *
 * <p><b>여섯 칸 중 무엇이 상품명이고 무엇이 수량인지는 실측하지 못했다.</b> 자리 번호로 읽으면 그 추측이 틀렸을 때 <b>엉뚱한 값이 조용히 저장된다</b> — 이
 * 저장소가 가장 싫어하는 실패 모양이다.
 *
 * <p>그래서 자리가 아니라 <b>값의 형태</b>로 고른다. 수량은 {@code 1EA} 꼴, 금액은 쉼표가 섞인 숫자, 나머지가 이름이다. 형태로 고르면 사이트가 칸 순서를
 * 바꿔도 따라가고, 형태가 아예 안 맞으면 그 품목은 <b>버려지고 세어진다</b>(로그에 남는다). 틀린 값을 넣는 것보다 빠뜨리고 드러내는 편이 낫다.
 *
 * <h2>serial 이 날짜_금액인 이유</h2>
 *
 * <p>이 화면은 <b>주문번호를 주지 않는다.</b> 실측한 여섯 열 어디에도 8자리 이상 식별자가 없다. {@link Hanaro} 와 같은 제약이라 같은 방식으로
 * {@code 구매일자_구매금액} 을 합성한다 — 같은 날 같은 금액으로 두 번 사면 한 건으로 접힌다는 뜻이고, 그 성질은 의도된 것이다.
 *
 * @author KIUNSEA
 */
public class HanaroOffline extends MallSession implements PurchasedCollector {

  private static final Logger log = LogManager.getLogger(HanaroOffline.class);

  /** 수집 로그·브레이커에 쓰는 몰 이름. */
  static final String MALL = "HANARO_OFFLINE";

  /**
   * 수집기 이름. {@code MallRegistry} 와 {@code jbg_order.collector} 가 이 값을 쓴다.
   *
   * <p><b>상수로 두는 이유.</b> 이 문자열은 조회 시작일을 유도할 때 {@code WHERE collector=?} 로 쓰인다. 레지스트리와 여기에 문자열을 따로
   * 적으면 한쪽만 고쳐져도 컴파일은 통과하고, 그때 기준일이 늘 비어 매 회차 기본 범위를 통째로 훑는다 — 조용히.
   */
  public static final String COLLECTOR = "HanaroOffline";

  /**
   * 거래내역 조회 진입점. <b>{@code id} 질의를 반드시 붙인다.</b>
   *
   * <p>{@link #LOGIN_URL} 과 같은 이유다 — {@code id} 없이 들어가면 CMS 껍데기가 빠진 조각이 오고, 그러면 스타일도 스크립트도 실리지 않는다.
   * 프로브가 {@code id} 없는 주소로 진입했을 때 제목이 비고 id 가 4종뿐인 화면을 실측했다.
   *
   * <p>조회 폼은 스크립트에 기대는 부분이 있다(날짜 선택기, 쪽 이동 {@code page_link}). 조각으로 들어가면 폼은 그려지되 조회가 성립하지 않을 수 있다 —
   * 로그인에서 정확히 그 일이 일어났다.
   *
   * <p>{@code id} 값은 CMS 메뉴 코드이고 사용자가 확인해 줬다(2026-08-11). 모두에게 같은 값이라 개인 정보가 아니다.
   */
  static final String TRANSACTION_URL =
      "https://www.nhhanaro.co.kr/nahh_70090.do?id=nahh001_050300000000";

  /**
   * 사이트 첫 화면. <b>여기를 먼저 들른 다음</b> 속 화면으로 간다.
   *
   * <p>이 사이트는 속 주소로 바로 들어가면 <b>레이아웃과 스크립트가 빠진 조각</b>을 준다 — 프로브가 {@code nahh_70090.do} 직접 진입에서 제목이
   * 비고 id 가 4종뿐인 화면을 실측했다. 로그인 화면도 같아서, 스타일 없는 폼만 그려지고 <b>제출 버튼을 눌러도 아무 일도 일어나지 않는다</b>(실계정 첫 실행에서
   * 확인: 두 칸에 값이 들어간 채 화면이 그대로였다).
   *
   * <p>로그인이 스크립트에 기대는 이유는 폼 구성에서 드러난다 — 사람이 치는 칸({@code userI}·비밀번호)과 <b>따로</b> 숨은 폼에 {@code
   * userId}/{@code userPw} 가 있다. 보이는 값을 읽어 숨은 폼에 넣고 그쪽을 제출하는 구조이고, 그 스크립트가 없으면 클릭이 무의미하다.
   *
   * <p>{@code Hanaro} 도 같은 이유로 첫 화면을 먼저 연다.
   */
  static final String SITE_ROOT = "https://www.nhhanaro.co.kr";

  /**
   * 로그인 화면. <b>{@code id} 질의를 반드시 붙인다.</b>
   *
   * <p>이 사이트는 CMS 껍데기 위에 화면을 얹는다. {@code id} 없이 {@code nahh_70021.do} 로만 들어가면 <b>스타일도 스크립트도 없는
   * 조각</b>이 온다 — 실측에서 제목이 비고 id 가 4종뿐이었으며, 실린 스크립트는 jquery 셋뿐이었다.
   *
   * <p>그 상태에서는 로그인이 성립하지 않는다. 폼에 {@code onsubmit="return jf_form_check(...)"} 가 걸려 있는데 <b>그 함수를
   * 정의하는 스크립트가 실리지 않고</b>, 보이는 입력칸에는 {@code name} 이 없어 그대로 제출해도 빈 값이 간다. 실계정 실행이 정확히 여기서 막혔다 — 두 칸에
   * 값이 들어간 채 화면이 그대로였다.
   *
   * <p>{@code id} 값은 CMS 메뉴 코드이고 사용자가 확인해 줬다(2026-08-11). 개인 정보가 아니라 모두에게 같은 값이다.
   */
  static final String LOGIN_URL =
      "https://www.nhhanaro.co.kr/nahh_70021.do?id=nahh001_060100000000";

  /** 조회 기간 입력칸 (실측한 name/id). */
  static final By START_DATE = By.id("st_dt");

  static final By END_DATE = By.id("ed_dt");

  /** 목록 표. 화면에 표가 하나뿐이라 태그로 충분하지만, 머리글로 한 번 더 확인한다. */
  static final By TABLE = By.tagName("table");

  static final By ROWS = By.tagName("tr");
  static final By CELLS = By.tagName("td");
  static final By HEADER_CELLS = By.tagName("th");

  /** 품목 하나 = ul 하나 (실측). */
  static final By ITEM_GROUPS = By.tagName("ul");

  static final By ITEM_FIELDS = By.tagName("li");

  /** 거래 행임을 가리는 최소 칸 수. 실측값은 6칸이다. */
  static final int TRANSACTION_CELLS = 6;

  /**
   * 훑을 쪽 수의 상한. 무한루프 방지용이며, 닿으면 <b>경고를 남긴다</b>.
   *
   * <p>조용히 자르면 안 되는 이유가 있다 — 못 가져온 뒤쪽 거래가 있어도 워터마크는 가져온 것 기준으로 전진하므로, 그 구간은 다음 회차에 다시 조회되지 않고
   * <b>영구히 봉인된다.</b>
   */
  static final int MAX_PAGES = 100;

  /** 실측한 머리글. 목록 표를 다른 표와 가르는 데 쓴다. */
  static final List<String> LIST_HEADERS = List.of("일시", "내역", "매장");

  /** 거래 행에서 각 값이 있는 자리 (실측). */
  static final int COL_DATE = 1;

  static final int COL_STORE = 3;
  static final int COL_AMOUNT = 4;

  /** 수량 칸의 형태. {@code 1EA} 처럼 숫자 뒤에 짧은 영문 단위가 붙는다. */
  private static final Pattern QUANTITY = Pattern.compile("^(\\d+)\\s*[A-Za-z]{1,3}$");

  /** 금액 칸의 형태. 숫자와 쉼표만 있고 적어도 한 자리는 숫자다. */
  private static final Pattern AMOUNT = Pattern.compile("^[0-9][0-9,]*$");

  public HanaroOffline(String id, String pass) {
    super(id, pass);
  }

  @Override
  public JSONArray getItems() {
    JSONArray resArr = null;

    WebDriverManager wdm = new WebDriverManager();
    WebDriver driver = wdm.getWebDriver();

    try {
      if (driver == null) {
        throw CollectStep.wrap(
            null, MALL, "init-webdriver", null, new IllegalStateException("WebDriver 생성 실패"));
      }

      boolean signedIn = CollectStep.call(driver, MALL, "signin", () -> this.signin(driver));
      if (!signedIn) {
        // 무엇 때문에 실패했는지를 메시지에 싣는다.
        //
        // 첫 실계정 실행이 "로그인 실패 — 자격증명 또는 사이트 구조 변경" 한 줄만 남겨, 자격증명이
        // 틀린 것인지 제출이 아예 안 된 것인지 가리려고 스크린샷을 눈으로 봐야 했다. 착지한 주소와
        // 비밀번호 칸이 아직 보이는지만 있어도 그 둘은 갈린다.
        throw CollectStep.wrap(
            driver, MALL, "signin", null, new IllegalStateException(describeSigninFailure(driver)));
      }

      this.delayTime(1500);

      resArr =
          CollectStep.call(driver, MALL, "navigatePurchased", () -> this.navigatePurchased(driver));

      try {
        this.signout(driver);
      } catch (Exception ignore) {
        log.warn("HanaroOffline 로그아웃 중 오류(무시): {}", ignore.getMessage());
      }
    } finally {
      try {
        if (driver != null) driver.quit();
      } catch (Exception ignore) {
        // 드라이버 정리 실패는 수집 결과에 영향을 주지 않는다
      }
    }

    return resArr == null ? new JSONArray() : resArr;
  }

  @Override
  public boolean signin(WebDriver driver) {
    // 첫 화면을 먼저 연다. 로그인 주소로 바로 가면 스크립트 없는 조각이 와서 제출이 먹지 않는다
    // (SITE_ROOT javadoc 참조 — 실계정 첫 실행에서 확인한 실패다).
    driver.get(SITE_ROOT);
    this.delayTime(2000);

    driver.navigate().to(LOGIN_URL);
    this.delayTime(2000);

    // 보이는 폼에 친다. 실측에서 로그인 화면에는 폼이 셋이었고, id=userId/userPw 는
    // <b>숨은</b> 폼(form[1])의 hidden 입력이다. 거기에 sendKeys 하면 W3C 규약상
    // ElementNotInteractableException 이 나서 매 회차 signin 단계에서 죽는다.
    // 사람이 실제로 치는 칸은 form[2] 의 id=userI(텍스트)와 비밀번호 칸이다.
    driver.findElement(By.id("userI")).sendKeys(this.USER_ID);
    driver.findElement(By.cssSelector("input[type=password]")).sendKeys(this.USER_PASS);
    driver.findElement(By.cssSelector("input[type=submit]")).click();

    this.delayTime(2000); // 페이지 이동 후엔 세션 유지를 위해 지연이 필요하다

    // 이 사이트는 로그인 결과를 alert 으로 알린다(실측: "로그인 하였습니다.").
    // alert 이 열려 있으면 그 뒤의 어떤 조회도 UnhandledAlertException 으로 터진다 —
    // 실계정 실행이 로그인에 <b>성공하고도</b> 이 예외로 죽었다.
    String alerted = dismissAlert(driver);
    if (alerted != null) {
      log.debug("로그인 알림 처리: {}", alerted.length() + "자");
      this.delayTime(1000);
    }

    return isSignedIn(driver);
  }

  /**
   * 열려 있는 alert 을 닫고 그 글자를 돌려준다.
   *
   * <p>이 사이트는 로그인 성공·실패를 <b>둘 다</b> alert 으로 알린다. 닫지 않으면 그 뒤의 모든 WebDriver 호출이 {@code
   * UnhandledAlertException} 으로 터지므로, 판정하기 <b>전에</b> 반드시 치운다.
   *
   * <p><b>글자를 로그에 그대로 싣지 않는다</b> — 사이트가 안내문에 계정 관련 문구를 넣을 수 있다. 판정에 필요한 것은 내용이 아니라 "알림이 있었는가" 다.
   *
   * @return alert 의 글자. 없었으면 null
   */
  String dismissAlert(WebDriver driver) {
    try {
      Alert alert = driver.switchTo().alert();
      String text = alert.getText();
      alert.accept();
      return text == null ? "" : text;
    } catch (NoAlertPresentException e) {
      return null;
    } catch (RuntimeException e) {
      // 닫지 못했으면 그 사실을 남긴다. 여기서 삼키면 다음 호출이 같은 예외로 터진다.
      log.warn("알림을 닫지 못했다: {}", e.getClass().getSimpleName());
      return null;
    }
  }

  /**
   * 로그인 상태인지 판정한다.
   *
   * <p><b>'존재' 가 아니라 '보임' 으로 판정한다.</b> 사이트가 로그아웃 어포던스를 DOM 에 두고 감추는 일이 있어, 존재로 판정하면 로그인하지 않았는데도
   * 성공으로 읽는다({@code Ssg.isSignedIn} 이 v0.18.0 에서 같은 이유로 고쳐졌다).
   *
   * <p>여기서는 <b>로그인 화면의 표식이 보이지 않는가</b>로 본다 — 실측에서 세션이 끊기면 로그인 화면으로 밀리고 거기에는 비밀번호 칸이 보인다.
   */
  boolean isSignedIn(WebDriver driver) {
    // 판정 전에 알림부터 치운다 — 열려 있으면 아래 getCurrentUrl 이 그대로 터진다.
    dismissAlert(driver);

    String url = driver.getCurrentUrl();
    if (url != null && url.contains("nahh_70021")) {
      return false;
    }
    return !anyVisible(driver.findElements(By.cssSelector("input[type=password]")));
  }

  /**
   * 로그인 실패의 <b>모양</b>을 한 줄로 만든다.
   *
   * <p>계정 값은 담지 않는다 — 이 문자열은 수집 로그에 그대로 저장된다. 담는 것은 착지한 화면의 성질뿐이다.
   *
   * <p>가르려는 것은 둘이다. <b>로그인 화면에 그대로 머물러 있으면</b> 제출이 먹지 않은 것이고(스크립트 미적재, 셀렉터 어긋남), <b>다른 화면으로 갔는데도
   * 로그인 상태가 아니면</b> 자격증명이나 추가 인증 문제다.
   */
  String describeSigninFailure(WebDriver driver) {
    String url;
    try {
      url = driver.getCurrentUrl();
    } catch (RuntimeException e) {
      return "로그인 실패 — 화면 주소를 읽지 못했다 (" + e.getClass().getSimpleName() + ")";
    }

    boolean stillOnLogin = url != null && url.contains("nahh_70021");
    boolean passwordVisible =
        anyVisible(driver.findElements(By.cssSelector("input[type=password]")));

    if (stillOnLogin || passwordVisible) {
      return "로그인 실패 — 제출 뒤에도 로그인 화면에 머물러 있다."
          + " 자격증명 문제가 아니라 제출이 먹지 않았을 가능성이 크다(스크립트 미적재/셀렉터 변경)."
          + " 착지 주소="
          + url;
    }
    return "로그인 실패 — 로그인 화면을 벗어났으나 로그인 상태가 아니다." + " 자격증명 또는 추가 인증 가능성. 착지 주소=" + url;
  }

  /** 하나라도 보이면 true. 감춰 둔 요소는 세지 않는다. */
  static boolean anyVisible(List<WebElement> elements) {
    if (elements == null) {
      return false;
    }
    for (WebElement el : elements) {
      try {
        if (el.isDisplayed()) {
          return true;
        }
      } catch (RuntimeException ignore) {
        // 읽을 수 없는 요소는 보이지 않는 것으로 본다
      }
    }
    return false;
  }

  @Override
  public void signout(WebDriver driver) {
    this.delayTime(1000);
    try {
      driver.findElement(By.cssSelector("a[href*='fnlogout']")).click();
      this.delayTime(2000);
      log.debug("로그아웃 완료");
    } catch (Exception e) {
      log.debug("로그아웃 처리 중 예외: {}", e.getMessage());
    }
  }

  /**
   * 조회 구간을 유도한 뒤 거래내역을 수집한다.
   *
   * <h2>왜 기간을 계산해서 넣는가</h2>
   *
   * <p>이 화면은 기본 조회 기간이 최근 며칠이라, 그 사이에 구매가 없으면 "조회된 자료가 없습니다" 를 준다. <b>그것은 수집 실패가 아니라 사이트의 정상
   * 동작</b>이고, 기본값을 그대로 쓰면 대부분의 회차가 0건이 된다.
   *
   * <p>그래서 {@link CollectPeriod} 가 <b>이 수집기가 마지막으로 저장한 구매일부터 오늘까지</b>를 계산한다. 기준일은 따로 저장하지 않고 {@code
   * jbg_order} 에서 유도하므로, 저장이 실패한 회차는 기준일도 뒤에 남아 다음 회차가 다시 가져온다.
   */
  @Override
  public JSONArray navigatePurchased(WebDriver driver) {
    CollectPeriod.Window window = resolveWindow();
    log.debug("조회 구간 {} ~ {}", window.startYmd(), window.endYmd());
    return collectPeriod(driver, window.startYmd(), window.endYmd());
  }

  /**
   * 조회 구간을 정한다.
   *
   * <p>기준일 조회가 실패해도 수집을 멈추지 않는다 — 기본 조회 범위로 되돌아간다. 겹쳐 가져오는 것은 중복 판정이 걸러 내지만, 이 자리에서 예외로 빠지면 그 회차는
   * 통째로 0건이 된다.
   *
   * <p><b>값을 둘 읽는다.</b> 저장된 최대 구매일 하나만으로는 부족하다 — 이전 회차의 <b>부분 저장 실패</b>가 가장 늦은 날짜가 아닌 자리에서 났으면 그
   * 최대값이 실패한 날짜를 이미 지나가 있다. 그래서 {@code retry_from_date}(저장을 확인하지 못한 가장 이른 구매일)를 함께 읽어 <b>둘 중 이른
   * 쪽</b>부터 조회한다. 자세한 이유는 {@link CollectPeriod} javadoc 에 있다.
   */
  CollectPeriod.Window resolveWindow() {
    String lastStored = null;
    try {
      lastStored =
          new JbgOrderDataAccessObject()
              .getLastCollectedDate(String.valueOf(MallRegistry.HANARO.seq()), COLLECTOR);
    } catch (Exception e) {
      log.warn("마지막 수집일 조회 실패 — 기본 범위로 조회한다: {}", e.getMessage());
    }

    // 이 조회는 스스로 예외를 밖으로 내지 않는다(DAO 가 삼키고 null 을 준다). 바닥이 없으면
    // 이 컬럼이 생기기 전과 똑같이 유도값만으로 조회한다.
    String retryFrom =
        new JbgCollectBreakerDataAccessObject().getRetryFrom(MallRegistry.HANARO.seq(), COLLECTOR);
    if (retryFrom != null) {
      log.info("이전 회차가 저장하지 못한 구간이 있다 — {} 부터 다시 조회한다", retryFrom);
    }

    return CollectPeriod.resolve(lastStored, retryFrom, LocalDate.now());
  }

  /**
   * 지정한 기간의 거래내역을 조회해 수집한다.
   *
   * @param driver WebDriver
   * @param startYmd 조회 시작일 {@code yyyyMMdd}
   * @param endYmd 조회 종료일 {@code yyyyMMdd}
   * @return 수집한 거래 목록
   */
  @SuppressWarnings("unchecked")
  JSONArray collectPeriod(WebDriver driver, String startYmd, String endYmd) {
    driver.navigate().to(TRANSACTION_URL);
    this.delayTime(2000);

    JSONArray all = new JSONArray();

    for (int page = 1; page <= MAX_PAGES; page++) {
      submitPeriod(driver, startYmd, endYmd, page);
      this.delayTime(2500);

      // 조회 결과도 alert 으로 알릴 수 있다(기간 오류, 결과 없음 등). 로그인에서 이미 한 번
      // 여기 걸려 죽었으므로 파싱 전에 치운다 — 열려 있으면 findElements 부터 터진다.
      dismissAlert(driver);

      JSONArray rows = parseTransactions(driver);

      // 첫 쪽이 비면 형식을 의심한다. 날짜 형식은 실측하지 못했고, 틀렸을 때 이 사이트는
      // 예외가 아니라 <b>빈 목록</b>을 준다 — 그대로 두면 0건이 성공으로 굳는다.
      if (rows.isEmpty() && page == 1) {
        rows = retryWithDashedDates(driver, startYmd, endYmd);
      }

      // 쪽마다 몇 건을 가져왔는지 남긴다.
      //
      // <b>완전성이 로그에 보여야 한다.</b> 이 줄이 없던 첫 실계정 성공에서, "2년치가 4건뿐인가"
      // 를 판단하려고 전체 소요 시간으로 쪽 수를 <b>추론</b>해야 했다. 추론은 계측이 아니다.
      // 쪽별 건수가 있으면 "1쪽 N건, 2쪽 0건" 처럼 어디서 끝났는지가 그대로 읽힌다.
      log.debug("조회 {}쪽 — {}건", page, rows.size());

      if (rows.isEmpty()) {
        if (page == 1) {
          log.info("조회 결과 0건 — 그 기간에 거래가 없거나 조회가 성립하지 않았다.");
        }
        break;
      }
      all.addAll(rows);

      if (page == MAX_PAGES) {
        // 조용히 자르지 않는다. 여기서 멈추면 그 뒤는 워터마크가 전진하며 봉인된다.
        log.warn("페이지 상한 {} 도달 — 뒷 페이지가 남아 있을 수 있다.", MAX_PAGES);
      }
    }

    log.debug("하나로 오프라인 거래내역 수집 완료 - 거래 수: {}", all.size());
    return all;
  }

  /**
   * {@code yyyy-MM-dd} 로 한 번 더 조회한다.
   *
   * <p><b>날짜 형식을 실측하지 못했다.</b> 화면이 날짜를 {@code YYYY-MM-DD} 로 보여 주고 datepicker 가 붙어 있어 그쪽일 가능성이 크지만,
   * 확인하지 않았다. 틀렸을 때 이 사이트는 예외를 주지 않고 빈 목록을 주므로 <b>조용히 0건</b>이 된다 — 그래서 추측 하나에 걸지 않고 둘 다 시도한다.
   *
   * <p>겹쳐 조회하는 비용은 없다. 중복은 저장 단계의 중복 판정이 걸러 낸다.
   */
  private JSONArray retryWithDashedDates(WebDriver driver, String startYmd, String endYmd) {
    if (startYmd.length() != 8 || endYmd.length() != 8) {
      return new JSONArray();
    }
    log.warn("yyyyMMdd 조회가 0건 — yyyy-MM-dd 형식으로 한 번 더 시도한다.");

    submitPeriod(driver, withDashes(startYmd), withDashes(endYmd), 1);
    this.delayTime(2500);
    return parseTransactions(driver);
  }

  private static String withDashes(String ymd) {
    return ymd.substring(0, 4) + "-" + ymd.substring(4, 6) + "-" + ymd.substring(6);
  }

  /**
   * 기간과 쪽 번호를 채우고 제출한다.
   *
   * <p>조회는 <b>같은 주소로 POST</b> 하므로 주소는 바뀌지 않는다. 쪽 번호는 조회 폼 안의 hidden {@code page} 필드다 — 실측에서 {@code
   * st_dt}/{@code ed_dt} 와 같은 폼에 있었다. 화면의 페이지 이동은 {@code javascript:page_link(…)} 지만, 그 함수 이름에 기대는
   * 대신 폼 필드를 직접 채운다(함수 이름이 바뀌어도 따라간다).
   */
  void submitPeriod(WebDriver driver, String startYmd, String endYmd, int page) {
    WebElement start = driver.findElement(START_DATE);
    WebElement end = driver.findElement(END_DATE);

    start.clear();
    start.sendKeys(startYmd);
    end.clear();
    end.sendKeys(endYmd);

    if (driver instanceof JavascriptExecutor js) {
      js.executeScript(
          "var p = document.getElementById('page'); if (p) { p.value = arguments[0]; }",
          String.valueOf(page));
    }

    driver.findElement(By.cssSelector("input[type=submit]")).click();
  }

  // ── 파서 (브라우저 없이 검사할 수 있게 열어 둔다) ─────────────────────────

  /**
   * 목록 표에서 거래를 훑는다.
   *
   * <p><b>DOM 추출만 한다</b> — 페이지 이동·클릭·지연을 포함하지 않는다. 브라우저 없이 단위테스트할 수 있도록 package-private 로 열어 둔다. 검증
   * 대상은 추출 후의 변환 규칙이며, 셀렉터가 실제 사이트와 맞는지는 원리상 단위테스트로 알 수 없다.
   *
   * @param driver WebDriver
   * @return 거래 목록. 표를 못 찾으면 빈 배열
   */
  @SuppressWarnings("unchecked")
  JSONArray parseTransactions(WebDriver driver) {
    JSONArray out = new JSONArray();

    WebElement table = findListTable(driver);
    if (table == null) {
      log.debug("거래 목록 표를 찾지 못했다 — 머리글이 바뀌었거나 조회가 되지 않았다.");
      return out;
    }

    List<WebElement> rows = table.findElements(ROWS);
    for (int i = 0; i < rows.size(); i++) {
      List<WebElement> cells = rows.get(i).findElements(CELLS);
      if (cells.size() < TRANSACTION_CELLS) {
        continue; // 머리글 행, 품목 행, 안내문 행
      }

      JSONObject transaction = parseTransactionRow(cells);
      if (transaction == null) {
        continue;
      }

      // 품목은 <b>바로 다음 행</b>에 접힌 채로 들어 있다. 클릭하지 않고 그대로 읽는다.
      JSONArray items = (i + 1 < rows.size()) ? parseItemRow(rows.get(i + 1)) : new JSONArray();
      transaction.put("items", items);

      out.add(transaction);
    }
    return out;
  }

  /**
   * 목록 표를 머리글로 가려낸다.
   *
   * <p>실측 시점에는 화면에 표가 하나뿐이었지만, 그것에 기대면 사이트가 표를 하나 더 두는 날 조용히 엉뚱한 표를 읽는다. 머리글로 고르면 그때는 <b>0건</b>이 되어
   * 드러난다.
   */
  WebElement findListTable(WebDriver driver) {
    for (WebElement table : driver.findElements(TABLE)) {
      List<String> headers = new ArrayList<>();
      for (WebElement th : table.findElements(HEADER_CELLS)) {
        headers.add(th.getText().trim());
      }
      if (headers.containsAll(LIST_HEADERS)) {
        return table;
      }
    }
    return null;
  }

  /**
   * 거래 행 하나를 읽는다.
   *
   * @param cells 그 행의 td 들 ({@link #TRANSACTION_CELLS} 칸 이상)
   * @return 거래 JSON. 구매일자가 없으면 null
   */
  @SuppressWarnings("unchecked")
  JSONObject parseTransactionRow(List<WebElement> cells) {
    // 숫자만 남기고 앞 8자리를 쓴다.
    //
    // 실측한 칸은 NNNN-NN-NN(날짜만)이라 하이픈만 떼도 충분하지만, 머리글이 '일시' 다.
    // 사이트가 시각을 붙이는 날 하이픈만 떼면 8자리를 넘겨 <b>중복 판정이 통째로 어긋나고</b>
    // 매 회차 같은 거래가 새 행으로 쌓인다. 구분자가 점으로 바뀌어도 마찬가지다.
    String datetime = cells.get(COL_DATE).getText().replaceAll("[^0-9]", "");
    if (datetime.length() < 8) {
      return null;
    }
    datetime = datetime.substring(0, 8);

    String amount = cells.get(COL_AMOUNT).getText().replaceAll("[^0-9]", "");

    String store = cells.get(COL_STORE).getText().trim();

    JSONObject transaction = new JSONObject();
    transaction.put("datetime", datetime);
    transaction.put("mallname", store);
    // 이 화면은 주문번호를 주지 않는다. 클래스 javadoc 참조.
    //
    // 금액이 비면 날짜 하나만 남는데, 그러면 중복 판정 키가 '그 날짜' 가 되어 <b>그날 거래 전체가
    // 한 건으로 접힌다.</b> Emart 가 같은 이유로 날짜만으로는 serial 을 만들기를 거부한다
    // (EmartSerialFallbackTest.refusesToBuildADateOnlySerial). 여기서는 점포명을 꼬리로 붙여
    // 최소한 점포가 다르면 갈리게 한다.
    String tail = amount.isEmpty() ? store : amount;
    transaction.put("serial", tail.isEmpty() ? datetime : datetime + "_" + tail);
    return transaction;
  }

  /**
   * 품목 행 하나를 읽는다.
   *
   * <p><b>보이지 않아도 읽는다.</b> 이 행은 접혀 있는 것이 정상이고 {@code getText()} 는 빈 문자열을 준다. 그래서 {@code
   * textContent} 를 쓴다 — 실측에서 접힌 채로도 품목이 전부 들어 있었다.
   *
   * @param row 거래 행 바로 다음 행
   * @return 품목 배열. 없으면 빈 배열(null 이 아니다 — 수신측이 배열임을 검사한다)
   */
  @SuppressWarnings("unchecked")
  JSONArray parseItemRow(WebElement row) {
    JSONArray items = new JSONArray();

    List<WebElement> groups = row.findElements(ITEM_GROUPS);
    int skipped = 0;
    for (WebElement group : groups) {
      JSONObject item = parseItem(group.findElements(ITEM_FIELDS));
      if (item == null) {
        skipped++;
        continue;
      }
      items.add(item);
    }

    if (skipped > 0) {
      // 조용히 버리지 않는다 — 형태 판별이 어긋나면 여기 숫자가 커진다.
      log.debug("품목 {}건을 형태 불일치로 건너뜀 (전체 {}건)", skipped, groups.size());
    }
    return items;
  }

  /**
   * 품목 하나를 읽는다 — <b>자리 번호가 아니라 값의 형태로</b> 고른다.
   *
   * <p>여섯 칸 중 무엇이 무엇인지는 실측하지 못했다. 자리로 읽으면 추측이 틀렸을 때 엉뚱한 값이 조용히 저장되므로, 수량({@code 1EA} 꼴)과 금액(쉼표 숫자)을
   * 형태로 집어내고 나머지를 이름으로 본다.
   *
   * @param fields 그 품목의 li 들
   * @return 품목 JSON. 이름을 못 고르면 null
   */
  @SuppressWarnings("unchecked")
  JSONObject parseItem(List<WebElement> fields) {
    String qty = null;
    String price = null;
    List<String> nameParts = new ArrayList<>();

    for (WebElement field : fields) {
      String text = field.getText().trim();
      if (text.isEmpty()) {
        text = textContentOf(field);
      }
      if (text.isEmpty()) {
        continue;
      }

      var quantity = QUANTITY.matcher(text);
      if (qty == null && quantity.matches()) {
        qty = quantity.group(1); // 수신측 계약은 수량이 숫자다 — 단위는 뗀다
        continue;
      }
      if (AMOUNT.matcher(text).matches()) {
        price = text; // 마지막 금액이 이긴다 (단가보다 금액이 뒤에 온다)
        continue;
      }
      nameParts.add(text);
    }

    if (nameParts.isEmpty()) {
      return null;
    }

    JSONObject item = new JSONObject();
    item.put("name", String.join(" ", nameParts));
    item.put("qty", qty == null ? "1" : qty);
    if (price != null) {
      item.put("price", price);
    }
    return item;
  }

  /** 접혀 있는 요소의 글자. {@code getText()} 는 보이는 것만 주므로 이쪽을 쓴다. */
  private static String textContentOf(WebElement element) {
    try {
      String value = element.getAttribute("textContent");
      return value == null ? "" : value.trim();
    } catch (RuntimeException e) {
      return "";
    }
  }
}

package com.jiniebox.jangbogo.svc.mall;

import com.jiniebox.jangbogo.dao.JbgCollectBreakerDataAccessObject;
import com.jiniebox.jangbogo.dao.JbgOrderDataAccessObject;
import com.jiniebox.jangbogo.svc.ifc.MallSession;
import com.jiniebox.jangbogo.svc.ifc.PurchasedCollector;
import com.jiniebox.jangbogo.svc.util.ClickUtil;
import com.jiniebox.jangbogo.svc.util.CollectPeriod;
import com.jiniebox.jangbogo.svc.util.CollectStep;
import com.jiniebox.jangbogo.svc.util.WebDriverManager;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WindowType;

/**
 * https://www.ssg.com 을 조회하는 클래스이다. ('이마트', '트레이더스', '노브랜드' 의 온라인몰 구매 내역) <SSG 구매내역 안내에 표기 내용> - 매장
 * 구매 내역은 구매일자 2일후부터 MY SSG 에서 확인 가능하다. - 신세계포인트를 적립 받은 구매내역만 확인 가능하다. - TRADERS 에서 구매했던 상품중 온라인 구매
 * 가능한 상품만 조회됨 위와 같은 이유로 '이마트', '트레이더스', '노브랜드' 의 매장 구매내역은 Emart.java 에서 처리한다.
 * https://www.omnibuscode.com/board/PRJ_SOBA/60320
 *
 * @author KIUNSEA
 */
public class Ssg extends MallSession implements PurchasedCollector {

  private Logger log = LogManager.getLogger(Ssg.class);

  /**
   * 수집기 이름. {@code MallRegistry} 와 {@code jbg_order.collector} 가 이 값을 쓴다.
   *
   * <p><b>상수로 두는 이유.</b> 이 문자열은 조회 시작일을 유도할 때 {@code WHERE collector=?} 로 쓰인다. 레지스트리와 여기에 문자열을 따로
   * 적으면 한쪽만 고쳐져도 컴파일은 통과하고, 그때 기준일이 늘 비어 매 회차 기본 범위를 통째로 훑는다 — 조용히. ({@code
   * HanaroOffline.COLLECTOR} 가 같은 이유로 같은 형태다)
   */
  public static final String COLLECTOR = "SSG";

  /** 구매내역 조회 화면. */
  static final String PURCHASE_LIST_URL =
      "https://www.ssg.com/myssg/productMng/purchaseList.ssg?menu=purchaseList";

  /**
   * 조회 시작·종료일 입력칸 (2026-08-11 실측).
   *
   * <p>프로브가 잰 값 그대로다 — {@code type=text}, {@code maxlength=10}, {@code readonly=false}, 화면에 보임. 값의
   * 형태는 {@code NNNN-NN-NN} 이었고 그것이 곧 {@link #SEARCH_DATE_FORMAT} 이다.
   */
  static final By SEARCH_START_DATE = By.id("_d_sch_start_dt");

  static final By SEARCH_END_DATE = By.id("_d_sch_end_dt");

  /** 조회 버튼. {@code onclick} 속성이 비어 있어(실측) 스크립트가 이벤트를 붙인다 — 그래서 JS 클릭으로 누른다. */
  static final By SEARCH_BUTTON = By.id("_d_sch_button");

  /**
   * 이 화면이 요구하는 날짜 표기 (2026-08-11 실측).
   *
   * <p><b>추측이 아니다.</b> 프로브가 두 칸의 기존 값에서 {@code NNNN-NN-NN} 형태를 읽었고 {@code maxlength} 가 10 이다. 형식이
   * 틀리면 이 사이트는 예외가 아니라 <b>빈 목록</b>을 주므로, 틀린 표기는 '정상인 0건' 으로 굳는다.
   */
  static final java.time.format.DateTimeFormatter SEARCH_DATE_FORMAT =
      java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");

  /**
   * @param id
   * @param pass
   */
  public Ssg(String id, String pass) {
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
            null, "SSG", "init-webdriver", null, new IllegalStateException("WebDriver 생성 실패"));
      }
      boolean signedIn = CollectStep.call(driver, "SSG", "signin", () -> this.signin(driver));
      if (!signedIn) {
        throw CollectStep.wrap(
            driver,
            "SSG",
            "signin",
            null,
            new IllegalStateException("로그인 실패 — 자격증명 또는 사이트 구조 변경 가능성"));
      }

      this.delayTime(1500);

      /** 데이터 수집 */
      resArr =
          CollectStep.call(
              driver, "SSG", "navigatePurchased", () -> this.navigatePurchased(driver));

      // 마무리
      try {
        this.signout(driver);
      } catch (Exception ignore) {
        // 로그아웃 실패는 수집 결과에 영향 없음
        log.warn("SSG 로그아웃 중 오류(무시): {}", ignore.getMessage());
      }
    } finally {
      try {
        if (driver != null) driver.quit();
      } catch (Exception ignore) {
      }
    }

    if (resArr == null) {
      resArr = new JSONArray();
    }

    return resArr;
  }

  @Override
  public boolean signin(WebDriver driver) {

    driver.get("https://member.ssg.com/member/popup/popupLogin.ssg");
    this.delayTime(1500);

    // 로그인 시작
    driver.findElement(By.id("mem_id")).sendKeys(this.USER_ID);
    driver.findElement(By.id("mem_pw")).sendKeys(this.USER_PASS);
    WebElement elemLogin = driver.findElement(By.id("loginBtn"));
    ClickUtil.safeClick(driver, elemLogin, "#loginBtn"); // 로그인 버튼 클릭 (프로모션 배너 오버레이 대응)

    this.delayTime(1500); // 페이지 이동후엔 세션 유지를 위해 지연시간이 필요하다

    return this.isSignedIn(driver);
  }

  /**
   * 실제 로그인 상태를 판정한다. (Task #889)
   *
   * <p>이전에는 판정 로직이 주석 처리된 채 무조건 {@code false} 를 반환했다. 그 결과 SSG 수집은 로그인이 성공해도 항상 "로그인 실패"로 끝나 실제 실패
   * 원인을 구분할 수 없었다. 여기서는 로그인을 "완벽하게" 만들지 않고, **관측된 상태를 정직하게 반환**하는 데까지만 책임진다. 로그인 안정화 자체는 세션 프로필
   * 재사용으로 다룬다.
   *
   * <p>판정 기준은 로그아웃 어포던스의 <b>존재</b>가 아니라 <b>보임</b>이다. SSG 홈은 로그아웃 어포던스를 로그아웃 상태에서도 DOM 에 심어두고 JS 로
   * 감춘다 — 세션 프로필 재사용 실측에서 로그아웃 상태의 홈을 열었을 때 존재=1, 보임=0 이 나왔다. 그래서 {@code
   * findElements(...).isEmpty()} 로 존재만 보면 로그아웃 상태에서도 true 가 나오고, 로그인 실패가 성공으로 둔갑해 수집이 뒤쪽의 엉뚱한 단계에서
   * 깨진다. 실패 원인을 구분하지 못하던 원래 문제로 되돌아가는 것이니 {@code isEmpty()} 기준으로 되돌리지 말 것.
   *
   * <p>{@code findElements} 를 쓰므로 요소가 아예 없어도 예외가 아니라 {@code false} 가 된다.
   *
   * @param driver WebDriver 인스턴스
   * @return 로그인된 상태로 관측되면 true
   */
  private boolean isSignedIn(WebDriver driver) {
    driver.navigate().to("https://www.ssg.com/");
    this.delayTime(1500);

    return judgeSignedIn(
        driver.findElements(By.id("logoutBtn")),
        driver.findElements(By.cssSelector("a[href*='logout']")));
  }

  // ---------------------------------------------------------------------------------------------
  // 아래 두 메서드는 판정 규칙만 갖는다 — 페이지 이동·지연·셀렉터 선택을 포함하지 않는다. 아래쪽 파서들과
  // 같은 이유로 분리했다: 브라우저 없이 '보임' 기준이 지켜지는지 검사할 수 있는 지점을 만들기 위해서다.
  // 셀렉터가 실사이트와 맞는지는 원리상 단위테스트로 알 수 없다. 여기서 검증되는 것은 '무엇을 로그인으로
  // 볼 것인가' 뿐이다.
  // ---------------------------------------------------------------------------------------------

  /**
   * 로그아웃 어포던스 후보들로 로그인 여부를 판정한다.
   *
   * <p>두 셀렉터를 OR 로 보는 범위는 원래 판정에서 그대로 가져왔다. 이번에 바뀐 것은 '존재' 를 '보임' 으로 좁힌 것뿐이다.
   *
   * @param logoutById {@code #logoutBtn} 으로 찾은 요소들
   * @param logoutByHref {@code a[href*='logout']} 으로 찾은 요소들
   * @return 어느 쪽이든 화면에 보이는 요소가 하나라도 있으면 true
   */
  boolean judgeSignedIn(List<WebElement> logoutById, List<WebElement> logoutByHref) {
    boolean byId = anyVisible(logoutById);
    boolean byHref = anyVisible(logoutByHref);
    boolean signedIn = byId || byHref;

    log.debug(
        "SSG 로그인 판정: signedIn={} (보이는 logoutBtn={}, 보이는 logoutHref={})", signedIn, byId, byHref);
    return signedIn;
  }

  /**
   * 목록 안에 화면에 실제로 보이는 요소가 하나라도 있는지 본다.
   *
   * <p>{@link StaleElementReferenceException} 은 <b>요소 하나 단위로</b> 삼킨다. 홈 진입 직후에는 헤더가 비동기로 다시 그려질 수
   * 있어 {@code findElements} 로 받아둔 핸들 일부가 곧바로 무효가 될 수 있는데, 이걸 목록 전체를 감싸서 잡으면 뒤에 남은 멀쩡한 어포던스를 보지 못하고
   * 로그인 성공을 실패로 판정한다. 오탐의 방향만 반대로 바뀔 뿐이다.
   *
   * @param candidates {@code findElements} 결과. null 이나 빈 목록이면 false
   * @return 보이는 요소가 하나라도 있으면 true
   */
  boolean anyVisible(List<WebElement> candidates) {
    if (candidates == null) {
      return false;
    }
    for (WebElement candidate : candidates) {
      try {
        if (candidate.isDisplayed()) {
          return true;
        }
      } catch (StaleElementReferenceException stale) {
        // 이 후보 하나만 버리고 다음으로 간다. 남은 후보까지 버리면 판정이 반대로 틀린다.
        log.debug("SSG 로그인 판정 중 요소가 DOM 에서 사라짐 — 이 후보만 건너뜀: {}", stale.getMessage());
      }
    }
    return false;
  }

  @Override
  public void signout(WebDriver driver) {
    this.delayTime(2000);

    driver.get("https://eapp.emart.com/webapp/my?mallType=E&trcknCode=menu_my");
    JavascriptExecutor js = (JavascriptExecutor) driver;
    js.executeScript("logout();"); // call javascrip funtion
    Alert alert = driver.switchTo().alert();
    alert.accept(); // 확인 버튼 클릭
  }

  /**
   * 조회 구간을 정한다.
   *
   * <p>기준일 조회가 실패해도 수집을 멈추지 않는다 — 기본 조회 범위로 되돌아간다. 이 자리에서 예외로 빠지면 그 회차는 통째로 0건이 된다.
   *
   * <p><b>값을 둘 읽는다.</b> 저장된 최대 구매일과 재조회 바닥(저장을 확인하지 못한 가장 이른 구매일) 중 <b>이른 쪽</b>부터 조회한다. 이유는 {@link
   * CollectPeriod} javadoc 에 있다.
   */
  CollectPeriod.Window resolveWindow() {
    String lastStored = null;
    try {
      lastStored =
          new JbgOrderDataAccessObject()
              .getLastCollectedDate(String.valueOf(MallRegistry.SSG_GROUP.seq()), COLLECTOR);
    } catch (Exception e) {
      log.warn("마지막 수집일 조회 실패 — 기본 범위로 조회한다: {}", e.getMessage());
    }

    String retryFrom =
        new JbgCollectBreakerDataAccessObject()
            .getRetryFrom(MallRegistry.SSG_GROUP.seq(), COLLECTOR);
    if (retryFrom != null) {
      log.info("이전 회차가 저장하지 못한 구간이 있다 — {} 부터 다시 조회한다", retryFrom);
    }

    return CollectPeriod.resolve(lastStored, retryFrom, LocalDate.now());
  }

  /**
   * 구매 내역 화면으로 이동해 <b>계산한 구간</b>으로 조회한다.
   *
   * <h2>왜 프리셋을 누르지 않는가</h2>
   *
   * <p>예전에는 {@code //label[@for='sf_m3']}(실측 결과 <b>1개월</b>) 을 눌렀다. 그러면 시작점이 언제나 "오늘 기준 1개월 전" 이라
   * <b>되돌아가지 않는다</b> — 앱이 한 달 넘게 돌지 않으면 그 사이 구매는 다음 회차에도 조회 범위 밖이고 영영 들어오지 않는다. 프리셋 이름에서 뜻을 읽을 수
   * 없다는 것도 그때 드러났다({@code m3} 인데 1개월이다).
   *
   * <p>2026-08-11 실측에서 <b>시작·종료일을 직접 넣는 칸</b>이 확인됐다. 그래서 프리셋을 버리고 {@link CollectPeriod} 가 계산한 구간을
   * 그대로 넣는다.
   *
   * <h2>값을 JS 로 넣는 이유</h2>
   *
   * <p>이 칸에는 달력 위젯이 붙어 있을 수 있고, 그 경우 {@code sendKeys} 는 위젯을 띄워 뒤이은 클릭을 가로챈다. 값을 직접 넣고 {@code
   * change} 를 쏘면 위젯을 건드리지 않는다.
   *
   * <h2>넣은 값이 남았는지 확인하는 이유</h2>
   *
   * <p>이 사이트는 조회 조건이 틀려도 <b>예외가 아니라 빈 목록</b>을 준다. 사이트가 우리가 넣은 날짜를 되돌리면(프리셋이 이기거나, 제공 범위를 넘어 잘리면) 그
   * 회차는 '정상인 0건' 으로 굳는다. 그래서 조회 뒤에 칸을 다시 읽어 어긋나면 <b>경고를 남긴다</b>.
   *
   * <p><b>어긋나도 수집을 멈추지는 않는다.</b> 사이트가 제공 범위에 맞춰 잘라 준 것일 수 있고, 그때 결과는 여전히 쓸모 있다. 멈추는 쪽으로 틀리면 사람이
   * 알아채기 전까지 아무것도 모이지 않는다.
   */
  @Override
  public JSONArray navigatePurchased(WebDriver driver) {

    String mainWindowHandle = driver.getWindowHandle();
    JavascriptExecutor js = (JavascriptExecutor) driver;

    // 구매 내역
    driver.navigate().to(PURCHASE_LIST_URL);

    CollectPeriod.Window window = resolveWindow();
    String startText = window.start().format(SEARCH_DATE_FORMAT);
    String endText = window.end().format(SEARCH_DATE_FORMAT);
    log.debug("SSG 조회 구간 {} ~ {}", startText, endText);

    setSearchDate(driver, js, SEARCH_START_DATE, startText);
    setSearchDate(driver, js, SEARCH_END_DATE, endText);

    WebElement aElem = driver.findElement(SEARCH_BUTTON);
    js.executeScript("arguments[0].click();", aElem);
    this.delayTime(1500);

    warnIfPeriodWasNotHonoured(driver, startText, endText);

    JSONArray resJsonArr = new JSONArray();
    JSONObject orderJson = null;
    JSONObject itemJson = null;
    JSONArray itemJsonArr = null;

    this.onlinePurchaseList(
        driver, orderJson, itemJsonArr, itemJson, resJsonArr, mainWindowHandle, js);
    // 2024.12.05 오프라인 매장 구매 내역은 Emart.java 클래스에서 처리하도록 한다. (Task #752)
    //        this.offlinePurchaseList(driver, orderJson, itemJsonArr, itemJson, resJsonArr,
    // mainWindowHandle, js);

    return resJsonArr;
  }

  /**
   * 조회 날짜 칸에 값을 넣는다. 값을 직접 설정하고 {@code change} 를 쏜다 (달력 위젯을 건드리지 않는다).
   *
   * @param driver WebDriver
   * @param js 스크립트 실행기
   * @param field 날짜 칸
   * @param value {@code yyyy-MM-dd}
   */
  private void setSearchDate(WebDriver driver, JavascriptExecutor js, By field, String value) {
    WebElement input = driver.findElement(field);
    js.executeScript(
        "arguments[0].value = arguments[1];"
            + "arguments[0].dispatchEvent(new Event('input', {bubbles: true}));"
            + "arguments[0].dispatchEvent(new Event('change', {bubbles: true}));",
        input,
        value);
  }

  /**
   * 조회 뒤에도 우리가 넣은 구간이 칸에 남아 있는지 본다. 어긋나면 경고만 남기고 계속 간다.
   *
   * <p>숫자만 뽑아 견준다 — 사이트가 표기를 다듬는 것(점↔하이픈)까지 어긋남으로 세면 정상 회차마다 경고가 뜨고, 그러면 경고가 무시당하기 시작한다.
   */
  private void warnIfPeriodWasNotHonoured(
      WebDriver driver, String expectedFrom, String expectedTo) {
    String actualFrom = readSearchDate(driver, SEARCH_START_DATE);
    String actualTo = readSearchDate(driver, SEARCH_END_DATE);

    if (actualFrom == null || actualTo == null) {
      log.warn("SSG 조회 기간 칸을 다시 읽지 못했다 — 구간이 반영됐는지 확인할 수 없다.");
      return;
    }
    if (!digitsOf(expectedFrom).equals(digitsOf(actualFrom))
        || !digitsOf(expectedTo).equals(digitsOf(actualTo))) {
      log.warn(
          "SSG 가 요청한 조회 구간을 되돌렸다 — 요청 {}~{}, 화면 {}~{}."
              + " 사이트 제공 범위를 넘었거나 프리셋이 이겼을 수 있다. 이 회차의 0건은 '정상' 으로 읽지 말 것.",
          expectedFrom,
          expectedTo,
          actualFrom,
          actualTo);
    }
  }

  /** 날짜 칸의 현재 값. 읽지 못하면 null — 못 읽은 것과 어긋난 것을 구분한다. */
  private String readSearchDate(WebDriver driver, By field) {
    try {
      return driver.findElement(field).getAttribute("value");
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static String digitsOf(String value) {
    return value == null ? "" : value.replaceAll("[^0-9]", "");
  }

  /**
   * 온라인 구매 내역 (이마트, 트레이더스, 노브랜드)
   *
   * @param driver
   * @param orderJson
   * @param itemJsonArr
   * @param itemJson
   * @param resJsonArr 조회 결과를 누적 저장할 변수
   * @param mainWindowHandle
   * @param js
   */
  private void onlinePurchaseList(
      WebDriver driver,
      JSONObject orderJson,
      JSONArray itemJsonArr,
      JSONObject itemJson,
      JSONArray resJsonArr,
      String mainWindowHandle,
      JavascriptExecutor js)
      throws NoSuchElementException {

    log.debug("SSG 온라인 구매내역 조회");

    WebElement onlinePurchase_page =
        driver.findElement(
            By.xpath(
                "//*[@id='onlinePurchaseList']/div[@class='paginate']/div[@class='paging notranslate']"));
    List<WebElement> onlinePurchase_pages = onlinePurchase_page.findElements(By.xpath(".//*"));
    for (int i = 0; i < onlinePurchase_pages.size(); i++) {

      List<WebElement> elems_onlinePurchase_tr =
          driver.findElements(
              By.xpath(
                  "//*[@id='onlinePurchaseList']/div[@class='section data_tbl']/table/tbody/tr"));
      if (elems_onlinePurchase_tr.size() > 0) {
        Iterator<WebElement> onIter = elems_onlinePurchase_tr.iterator();
        while (onIter.hasNext()) {
          WebElement onTrElem = (WebElement) onIter.next();

          orderJson = parseOnlineOrderRow(onTrElem);
          if (orderJson == null) {
            continue; // 구매 내역 없음 안내 행
          }

          String orderDetailPage = onTrElem.findElement(By.xpath("td[4]/a")).getAttribute("href");
          driver.switchTo().newWindow(WindowType.WINDOW);
          driver.navigate().to(orderDetailPage);
          this.delayTime(1500); // 스크립트 실행후에는 완료시까지 지연 시간이 필요하다

          itemJsonArr = parseOnlineOrderItems(driver);

          orderJson.put("items", itemJsonArr);
          resJsonArr.add(orderJson);

          driver.close();
          driver.switchTo().window(mainWindowHandle);
        }
      }

      if ((i + 1) < onlinePurchase_pages.size()) {
        WebElement onPageElem = (WebElement) onlinePurchase_pages.get(i + 1);
        String onClickStr = onPageElem.getAttribute("onclick");
        js.executeScript(onClickStr); // call javascrip funtion
        this.delayTime(1500);
      }
    }
  }

  // ---------------------------------------------------------------------------------------------
  // 아래 두 메서드는 DOM 추출만 한다 — 페이지 이동·창 전환·지연을 포함하지 않는다.
  // onlinePurchaseList 안에 뒤섞여 있던 것을 분리한 것이며 로직은 그대로다. 브라우저 없이 단위테스트
  // 할 수 있는 지점을 만들기 위한 분리다(판단 대기 8). 검증 대상은 추출 후의 '변환 규칙'이다 —
  // 셀렉터가 실제 사이트와 맞는지는 원리상 단위테스트로 알 수 없고 실사이트에서만 드러난다.
  // ---------------------------------------------------------------------------------------------

  /**
   * 온라인 구매 목록의 한 행에서 주문 식별 정보를 뽑는다.
   *
   * <p>구매 내역이 없으면 SSG 는 "해당기간내에 온라인몰에서 구매하신 내역이 없습니다." 안내 행 하나를 렌더링한다. 이 행은 td 가 1개(colspan)뿐이라 데이터
   * 행으로 파싱하면 {@code td[2]/p} 에서 예외가 나고 수집이 통째로 실패한다. td 가 4개 미만이면 데이터 행이 아니라고 보고 {@code null} 을
   * 반환한다. (오프라인 목록을 다루는 {@code offlinePurchaseList} 는 이미 같은 방식으로 걸러낸다)
   *
   * <p>변환 규칙: 주문번호의 하이픈과 구매일자의 점을 제거한다. 수신측 계약은 {@code datetime} 이 {@code YYYYMMDD} 8자리일 것을 요구한다.
   *
   * @param onTrElem 온라인 구매 목록의 {@code tr}
   * @return {@code serial}, {@code datetime}, {@code mallname} 이 담긴 JSON. 데이터 행이 아니면 null
   */
  @SuppressWarnings("unchecked")
  JSONObject parseOnlineOrderRow(WebElement onTrElem) {
    if (onTrElem.findElements(By.xpath("td")).size() < 4) {
      log.debug("SSG 온라인 구매내역 없음 (안내 행) — 건너뜀: {}", onTrElem.getText());
      return null;
    }

    String date = onTrElem.findElement(By.xpath("td[1]/p")).getText();
    String orderNum = onTrElem.findElement(By.xpath("td[2]/p")).getText();
    String mallName = onTrElem.findElement(By.xpath("td[3]/p[1]/span/i/span")).getText();

    JSONObject orderJson = new JSONObject();
    orderJson.put("serial", orderNum != null ? orderNum.replace("-", "") : null);
    orderJson.put("datetime", date != null ? date.replace(".", "") : null);
    orderJson.put("mallname", mallName);
    return orderJson;
  }

  /**
   * 열려 있는 주문 상세 페이지에서 상품 목록을 읽는다.
   *
   * @param driver 상세 페이지가 떠 있는 WebDriver
   * @return {@code name}, {@code qty} 를 담은 상품 JSON 배열 (상품이 없으면 빈 배열)
   */
  @SuppressWarnings("unchecked")
  JSONArray parseOnlineOrderItems(WebDriver driver) {
    List<WebElement> elems_orderinfo_tr =
        driver.findElements(
            By.xpath("//div[@name='divShppUnit']/div[@class='codr_unit']/table/tbody/tr"));

    JSONArray itemJsonArr = new JSONArray();
    Iterator<WebElement> orderIter = elems_orderinfo_tr.iterator();
    while (orderIter.hasNext()) {
      WebElement orderElem = (WebElement) orderIter.next();
      String itemName =
          orderElem.findElement(By.xpath("td[@class='codr_unit_cont']/p/a/span/span")).getText();
      String itemNum =
          orderElem
              .findElement(
                  By.xpath(
                      "td[@class='codr_unit_pricewrap']/span[@class='codr_unit_count']/em[@class='num notranslate']"))
              .getText();

      JSONObject itemJson = new JSONObject();
      itemJson.put("name", itemName);
      itemJson.put("qty", itemNum);
      itemJsonArr.add(itemJson);
    }
    return itemJsonArr;
  }

  /**
   * 매장 구매 내역 (트레이더스)
   *
   * @param driver
   * @param orderJson
   * @param itemJsonArr
   * @param itemJson
   * @param resJsonArr 조회 결과를 누적 저장할 변수
   * @param mainWindowHandle
   * @param js
   */
  private void offlinePurchaseList(
      WebDriver driver,
      JSONObject orderJson,
      JSONArray itemJsonArr,
      JSONObject itemJson,
      JSONArray resJsonArr,
      String mainWindowHandle,
      JavascriptExecutor js) {

    log.debug("SSG 오프라인 구매내역 조회");

    List<WebElement> tmpElems = null;
    WebElement offlinePurchase_page =
        driver.findElement(
            By.xpath(
                "//*[@id='offPurchaseList']/div[@class='paginate']/div[@class='paging notranslate']"));
    List<WebElement> offlinePurchase_pages = offlinePurchase_page.findElements(By.xpath(".//*"));
    for (int i = 0; i < offlinePurchase_pages.size(); i++) {
      List<WebElement> elems_offlinePurchase_tr =
          driver.findElements(
              By.xpath("//*[@id='offPurchaseList']/div[@class='section data_tbl']/table/tbody/tr"));
      Iterator<WebElement> offIter = elems_offlinePurchase_tr.iterator();
      while (offIter.hasNext()) {
        WebElement offTrElem = (WebElement) offIter.next();
        tmpElems = offTrElem.findElements(By.xpath("td"));

        if (tmpElems.size() > 1) {
          String date = offTrElem.findElement(By.xpath("td[1]/p")).getText();
          String branchName = offTrElem.findElement(By.xpath("td[3]/p")).getText();
          WebElement elemA = offTrElem.findElement(By.xpath("td[5]/a"));

          orderJson = new JSONObject();
          orderJson.put("serial", "OFFLINE");
          orderJson.put("datetime", date != null ? date.replace(".", "") : null);
          orderJson.put("mallname", branchName);

          String orderDetailPage = elemA.getAttribute("href");
          driver.switchTo().newWindow(WindowType.WINDOW);
          driver.navigate().to(orderDetailPage);
          this.delayTime(1500);

          String mallName =
              driver
                  .findElement(
                      By.xpath(
                          "//*[@id='content']/div[@class='section']/div/div/div[@class='fr']/p/span/img"))
                  .getAttribute("alt");
          if (!"이마트".equals(mallName.trim()) || branchName.indexOf("TRADERS") > -1) {
            // 이마트 내역은 Emart.java (eapp.emart.com) 에서 조회한다. (#643)

            orderJson.put("mallname", (mallName != null ? mallName + " " : "") + branchName);

            List<WebElement> elems_orderinfo_tr =
                driver
                    .findElement(By.xpath("//*[@id='content']/div[2]/table/tbody"))
                    .findElements(By.tagName("tr"));
            itemJsonArr = new JSONArray();
            for (int j = 0; j < elems_orderinfo_tr.size(); j++) {
              WebElement trElem = (WebElement) elems_orderinfo_tr.get(j);
              String itemName =
                  trElem
                      .findElements(By.tagName("td"))
                      .get(2)
                      .findElement(By.xpath("a/span"))
                      .getText();

              itemJson = new JSONObject();
              itemJson.put("name", itemName);
              itemJson.put("qty", "1");
              itemJsonArr.add(itemJson);
            }

            orderJson.put("items", itemJsonArr);
            resJsonArr.add(orderJson);
          }

          driver.close();
          driver.switchTo().window(mainWindowHandle);
        }
      }

      if ((i + 1) < offlinePurchase_pages.size()) {
        WebElement offPageElem = (WebElement) offlinePurchase_pages.get(i + 1);
        String onClickStr = offPageElem.getAttribute("onclick");
        js.executeScript(onClickStr); // call javascrip funtion
        this.delayTime(1500);
      }
    }
  }
}

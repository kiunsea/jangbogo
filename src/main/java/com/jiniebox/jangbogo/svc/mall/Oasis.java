package com.jiniebox.jangbogo.svc.mall;

import com.jiniebox.jangbogo.svc.ifc.MallSession;
import com.jiniebox.jangbogo.svc.ifc.PurchasedCollector;
import com.jiniebox.jangbogo.svc.util.ClickUtil;
import com.jiniebox.jangbogo.svc.util.CollectStep;
import com.jiniebox.jangbogo.svc.util.WebDriverManager;
import com.jiniebox.jangbogo.util.JinieboxUtil;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

public class Oasis extends MallSession implements PurchasedCollector {

  private Logger log = LogManager.getLogger(Oasis.class);
  private String mallName = "오아시스마켓";

  /**
   * 쪽 넘김 블록 (2026-08-12 실측).
   *
   * <p>실측 시점의 속은 이랬다 — 쪽이 하나뿐이고 <b>이동할 링크가 없다.</b>
   *
   * <pre>
   * &lt;div class="paging-wrap"&gt;&lt;ul&gt;&lt;li&gt;&lt;b&gt;1&lt;/b&gt;&lt;/li&gt;&lt;/ul&gt;&lt;/div&gt;
   * </pre>
   */
  static final By PAGING_BLOCK = By.cssSelector("div.paging-wrap");

  /**
   * 다른 쪽으로 가는 링크.
   *
   * <p>실측에서 <b>0개</b>였다(쪽이 하나뿐이라 현재 쪽을 {@code <b>} 로만 표시한다). 그래서 이 수가 0보다 크면 <b>쪽이 늘어났다</b>는 뜻이고,
   * 지금 코드는 그중 첫 쪽만 읽고 있다는 뜻이다.
   */
  static final By PAGING_LINK = By.cssSelector("div.paging-wrap a");

  /**
   * @param id
   * @param pass
   */
  public Oasis(String id, String pass) {
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
            null, mallName, "init-webdriver", null, new IllegalStateException("WebDriver 생성 실패"));
      }
      boolean signedIn = CollectStep.call(driver, mallName, "signin", () -> this.signin(driver));
      if (!signedIn) {
        throw CollectStep.wrap(
            driver,
            mallName,
            "signin",
            null,
            new IllegalStateException("로그인 실패 — 자격증명 또는 사이트 구조 변경 가능성"));
      }

      this.delayTime(1500);

      /** 데이터 수집 */
      resArr =
          CollectStep.call(
              driver, mallName, "navigatePurchased", () -> this.navigatePurchased(driver));

      // 마무리
      try {
        this.signout(driver);
      } catch (Exception ignore) {
        log.warn("Oasis 로그아웃 중 오류(무시): {}", ignore.getMessage());
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
    driver.get("https://www.oasis.co.kr/login");
    this.delayTime(1500);

    // 로그인 시작
    driver.findElement(By.id("userId")).sendKeys(this.USER_ID);
    driver.findElement(By.id("password")).sendKeys(this.USER_PASS);

    WebElement elemLogin =
        driver.findElement(
            By.cssSelector(
                "#sec_login > div.loginTabCont.idCont > div > form > div.btn_login > a"));
    if (WebDriverManager.isEdge(driver)) {
      elemLogin =
          driver.findElement(
              By.cssSelector(
                  "#sec_login > div.loginTabCont.idCont > div > form > div.btn_login.on > a"));
    }
    ClickUtil.safeClick(driver, elemLogin, "oasis-login"); // 로그인 버튼 클릭
    this.delayTime(3000); // 페이지 이동후엔 세션 유지를 위해 지연시간이 필요하다

    driver.navigate().to("https://www.oasis.co.kr/myPage/main");
    this.delayTime(1500);

    // 로그인 성공 여부 확인
    WebElement btnLogout =
        driver.findElement(
            By.cssSelector(
                "#header > div.header_area > div > div.tMenu > ul.tMenu_unb > li:nth-child(1) > a"));

    if ("로그아웃".equals(btnLogout.getText())) {
      log.debug("로그인 성공");
      return true;
    }

    return false;
  }

  @Override
  public void signout(WebDriver driver) {
    // 오아시스 로그아웃
    driver.navigate().to("https://www.oasis.co.kr/logout");
  }

  @Override
  public JSONArray navigatePurchased(WebDriver driver) {

    JSONArray resJsonArr = new JSONArray();
    JSONObject orderJson = null;
    JSONArray itemJsonArr = null;
    JSONObject itemJson = null;

    // 구매 내역
    driver.navigate().to("https://www.oasis.co.kr/myPage/orderList");

    String mainWindowHandle = driver.getWindowHandle(); // 구매내역을 메인으로

    WebElement orderListOuter = driver.findElement(By.cssSelector("div.mypage-orderinfo-wrap"));
    List<WebElement> elems_orderList_div =
        orderListOuter.findElements(By.xpath("//div[@class='mypageOrderstatus']"));
    Iterator<WebElement> olIter = elems_orderList_div.iterator();
    while (olIter.hasNext()) {
      WebElement orderDiv = (WebElement) olIter.next();

      orderJson = parseOrderSummary(orderDiv);
      String link = extractDetailLink(orderDiv);

      // 상세페이지 오픈
      JavascriptExecutor js = (JavascriptExecutor) driver;
      js.executeScript("window.open(arguments[0])", link);

      Set<String> allWindows = driver.getWindowHandles();
      for (String windowHandle : allWindows) {
        if (!windowHandle.equals(mainWindowHandle)) {
          driver.switchTo().window(windowHandle);
          break;
        }
      }

      // 상세페이지 조회
      this.delayTime(1500);

      applyOrderDetail(driver, orderJson);
      resJsonArr.add(orderJson);
      driver.close();

      // 메인으로 복귀
      driver.switchTo().window(mainWindowHandle);
      this.delayTime(1500);
    }

    warnIfMorePagesExist(driver, resJsonArr.size());

    return resJsonArr;
  }

  /**
   * 목록이 <b>여러 쪽인데 첫 쪽만 읽었는지</b>를 알아채고 경고한다.
   *
   * <h2>왜 순회하지 않고 경고만 하는가</h2>
   *
   * <p>이 수집기는 주문목록의 <b>첫 쪽만</b> 읽는다. 쪽을 넘기지 않으므로 목록이 여러 쪽이면 뒤쪽은 들어오지 않고, 못 가져온 주문은 다음 회차에 워터마크가
   * 전진하면서 <b>영구히 봉인된다.</b>
   *
   * <p>그런데 <b>순회를 쓸 근거가 없다.</b> 2026-08-12 실측에서 이 계정의 목록은 1쪽 6건이 전부였고, 쪽 넘김 블록 안에 이동할 링크가 <b>하나도
   * 없었다</b>({@code <ul><li><b>1</b></li></ul>}). 즉 <b>2쪽이 어떻게 생겼는지 볼 기회가 없었다</b> — 링크인지 스크립트인지, 현재
   * 쪽을 무엇으로 표시하는지, 마지막 쪽에서 어떻게 달라지는지 전부 미측정이다.
   *
   * <p>여기서 순회를 짜면 그것은 <b>추측</b>이고, 틀렸을 때의 실패 모양이 최악이다 — 셀렉터가 어긋나면 조용히 첫 쪽만 읽고 <b>성공으로 기록된다.</b> 지금과
   * 똑같이 동작하면서 "쪽을 넘긴다" 는 거짓 인상만 남는다. 이 저장소가 반복해서 대가를 치른 형태다.
   *
   * <p>그래서 <b>고치는 대신 드러낸다.</b> 쪽이 늘어나는 날 이 경고가 뜨고, 그때가 2쪽을 실측할 수 있는 첫 순간이다. 조용한 손실을 보이는 신호로 바꾸는 것이
   * 지금 정직하게 할 수 있는 전부다.
   *
   * <p><b>판정 근거.</b> 실측 시점의 링크 수가 0이었다. 0보다 크면 쪽이 늘어난 것이다 — 정상 상태에서 오탐이 나지 않는다는 뜻이고, 그래야 이 경고가
   * 무시당하지 않는다.
   *
   * @param driver 주문목록이 떠 있는 드라이버
   * @param collected 이번에 읽어 낸 주문 수
   */
  void warnIfMorePagesExist(WebDriver driver, int collected) {
    try {
      int links = driver.findElements(PAGING_LINK).size();
      if (links > 0) {
        log.warn(
            "오아시스 주문목록이 여러 쪽이다 (쪽 이동 링크 {}개) — 이 수집기는 첫 쪽 {}건만 읽는다."
                + " 뒤쪽 주문은 들어오지 않고, 다음 회차에는 조회 시작일이 지나가 다시 오지 않는다."
                + " 쪽 넘김 구현이 필요하다 (OasisPeriodProbe 로 2쪽 구조를 실측할 것).",
            links,
            collected);
      } else if (driver.findElements(PAGING_BLOCK).isEmpty()) {
        // 블록 자체가 사라졌다면 화면이 개편된 것이다. 이 감시는 그 순간부터 아무것도 보지 못한다.
        log.warn("오아시스 쪽 넘김 블록을 찾지 못했다 — 화면이 바뀌었을 수 있다. 여러 쪽 여부를 더 이상 감시하지 못한다.");
      }
    } catch (RuntimeException e) {
      // 감시가 수집을 깨뜨리지 않는다. 못 본 것과 없는 것은 로그에서 갈린다.
      log.warn("오아시스 쪽 수 확인 실패 — 여러 쪽 여부를 알 수 없다: {}", e.getMessage());
    }
  }

  // ---------------------------------------------------------------------------------------------
  // 아래 세 메서드는 DOM 추출만 한다 — 페이지 이동·창 전환·지연을 포함하지 않는다.
  // navigatePurchased 안에 뒤섞여 있던 것을 분리한 것이며 로직은 그대로다. 브라우저 없이 단위테스트
  // 할 수 있는 지점을 만들기 위한 분리다(판단 대기 8). 검증 대상은 추출 후의 '변환 규칙'이다 —
  // 셀렉터가 실제 사이트와 맞는지는 원리상 단위테스트로 알 수 없고 실사이트에서만 드러난다.
  // ---------------------------------------------------------------------------------------------

  /**
   * 주문 목록의 한 행에서 주문 식별 정보를 뽑는다.
   *
   * <p>오아시스는 주문번호를 {@code (2026-0729-1234)} 처럼 괄호로 감싸 렌더링한다. 괄호를 벗겨 낸 값이 수신측 계약의 {@code serial} 이
   * 된다.
   *
   * @param orderDiv 주문 목록의 한 행 ({@code div.mypageOrderstatus})
   * @return {@code serial}, {@code mallname} 이 담긴 JSON
   */
  @SuppressWarnings("unchecked")
  JSONObject parseOrderSummary(WebElement orderDiv) {
    JSONObject orderJson = new JSONObject();

    WebElement orderNum = orderDiv.findElement(By.cssSelector("div.orderBoxInfo > div > span"));
    String serial = orderNum.getText();
    if ((serial.length() > 2) && (serial.indexOf('(') > -1)) {
      serial = serial.trim();
      serial = serial.substring(1, serial.length() - 1);
    }
    orderJson.put("serial", serial);
    log.debug("주문 시리얼: {}", serial);

    orderJson.put("mallname", this.mallName);
    return orderJson;
  }

  /**
   * 주문 목록의 한 행에서 상세 페이지 링크를 뽑는다.
   *
   * @param orderDiv 주문 목록의 한 행
   * @return 상세 페이지 URL
   */
  String extractDetailLink(WebElement orderDiv) {
    WebElement detailA =
        orderDiv.findElement(By.cssSelector("div.productArea > div > div.orderProduct > a"));
    return detailA.getAttribute("href");
  }

  /**
   * 열려 있는 상세 페이지에서 구매일자와 상품 목록을 읽어 주문 JSON 을 채운다.
   *
   * <p>가격은 없을 수 있다({@code NoSuchElementException} 을 정상 흐름으로 삼킨다). 수량·상품명은 필수다.
   *
   * @param driver 상세 페이지가 떠 있는 WebDriver
   * @param orderJson {@link #parseOrderSummary} 가 만든 JSON. {@code datetime}, {@code items} 가 채워진다
   */
  @SuppressWarnings("unchecked")
  void applyOrderDetail(WebDriver driver, JSONObject orderJson) {
    WebElement orderDate = driver.findElement(By.cssSelector("div.orderBoxInfo > strong"));
    String dateTxt = orderDate.getText().trim();
    orderJson.put("datetime", JinieboxUtil.delDatedot(dateTxt));

    WebElement itemsOuter = driver.findElement(By.cssSelector("div.productWrap"));
    List<WebElement> elem_itemList_div =
        itemsOuter.findElements(By.xpath("//div[@class='product']"));

    JSONArray itemJsonArr = new JSONArray();
    Iterator<WebElement> itemIter = elem_itemList_div.iterator();
    while (itemIter.hasNext()) {
      WebElement itemDiv = (WebElement) itemIter.next();
      JSONObject itemJson = new JSONObject();

      WebElement elemName =
          itemDiv.findElement(By.cssSelector("div.orderProduct > a > div > span > em"));
      itemJson.put("name", elemName.getText());

      WebElement elemCntSpan = itemDiv.findElement(By.cssSelector("p.orderCount"));
      itemJson.put("qty", elemCntSpan.getText());

      try {
        WebElement elemPriceSpan =
            itemDiv.findElement(
                By.cssSelector("div.orderBuyPrice > span.priceAfter > em:nth-child(2)"));
        itemJson.put("price", elemPriceSpan.getText());
      } catch (NoSuchElementException e) {
        // 자식 요소가 없는 경우 예외 처리
        log.debug("부모 요소 내에서 자식 요소를 찾을 수 없습니다.");
      }

      itemJsonArr.add(itemJson);
    }
    orderJson.put("items", itemJsonArr);
  }
}

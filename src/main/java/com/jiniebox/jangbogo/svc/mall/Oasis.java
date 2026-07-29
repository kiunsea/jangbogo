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

    return resJsonArr;
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

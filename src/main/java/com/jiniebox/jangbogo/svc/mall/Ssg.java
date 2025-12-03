package com.jiniebox.jangbogo.svc.mall;

import com.jiniebox.jangbogo.svc.ifc.MallSession;
import com.jiniebox.jangbogo.svc.ifc.PurchasedCollector;
import com.jiniebox.jangbogo.svc.util.WebDriverManager;
import com.jiniebox.jangbogo.util.ExceptionUtil;
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
      if (driver != null && this.signin(driver)) {

        this.delayTime(1500);

        /** 데이터 수집 */
        resArr = this.navigatePurchased(driver);

        // 마무리
        this.signout(driver);
      }
    } catch (Exception e) {
      log.error(ExceptionUtil.getExceptionInfo(e));
    } finally {
      driver.quit();
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
    elemLogin.click(); // 로그인 버튼 클릭

    this.delayTime(1500); // 페이지 이동후엔 세션 유지를 위해 지연시간이 필요하다

    // 로그인 성공 여부 확인
    /**
     * TODO Task #889 WebElement elemLogoutButton =
     * driver.findElement(By.xpath("//*[@id='logoutBtn']/a")); String elemOnclick =
     * elemLogoutButton.getAttribute("onclick");
     *
     * <p>if (elemLogoutButton != null && elemOnclick.indexOf("logout") > -1) { log.debug("[버튼 요소]
     * onclick 속성: {}", elemOnclick); return true; }
     */
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

  @Override
  public JSONArray navigatePurchased(WebDriver driver) {

    String mainWindowHandle = driver.getWindowHandle();
    JavascriptExecutor js = (JavascriptExecutor) driver;

    // 구매 내역
    driver.navigate().to("https://www.ssg.com/myssg/productMng/purchaseList.ssg?menu=purchaseList");

    driver.findElement(By.xpath("//label[@for='sf_m3']")).click(); // 단기간 조회 (1개월전부터 지금까지)
    WebElement aElem = driver.findElement(By.id("_d_sch_button"));
    js.executeScript("arguments[0].click();", aElem);
    this.delayTime(1500);

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
          String date = onTrElem.findElement(By.xpath("td[1]/p")).getText();
          String orderNum = onTrElem.findElement(By.xpath("td[2]/p")).getText();
          String mallName = onTrElem.findElement(By.xpath("td[3]/p[1]/span/i/span")).getText();
          WebElement elemA = onTrElem.findElement(By.xpath("td[4]/a"));

          orderJson = new JSONObject();
          orderJson.put("serial", orderNum != null ? orderNum.replace("-", "") : null);
          orderJson.put("datetime", date != null ? date.replace(".", "") : null);
          orderJson.put("mallname", mallName);

          String orderDetailPage = elemA.getAttribute("href");
          driver.switchTo().newWindow(WindowType.WINDOW);
          driver.navigate().to(orderDetailPage);
          this.delayTime(1500); // 스크립트 실행후에는 완료시까지 지연 시간이 필요하다

          List<WebElement> elems_orderinfo_tr =
              driver.findElements(
                  By.xpath("//div[@name='divShppUnit']/div[@class='codr_unit']/table/tbody/tr"));

          itemJsonArr = new JSONArray();
          Iterator<WebElement> orderIter = elems_orderinfo_tr.iterator();
          while (orderIter.hasNext()) {
            WebElement orderElem = (WebElement) orderIter.next();
            String itemName =
                orderElem
                    .findElement(By.xpath("td[@class='codr_unit_cont']/p/a/span/span"))
                    .getText();
            String itemNum =
                orderElem
                    .findElement(
                        By.xpath(
                            "td[@class='codr_unit_pricewrap']/span[@class='codr_unit_count']/em[@class='num notranslate']"))
                    .getText();

            itemJson = new JSONObject();
            itemJson.put("name", itemName);
            itemJson.put("qty", itemNum);
            itemJsonArr.add(itemJson);
          }

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

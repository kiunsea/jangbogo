package com.jiniebox.jangbogo.svc.mall;

import com.jiniebox.jangbogo.dao.JbgOrderDataAccessObject;
import com.jiniebox.jangbogo.svc.ifc.MallSession;
import com.jiniebox.jangbogo.svc.ifc.PurchasedCollector;
import com.jiniebox.jangbogo.svc.util.WebDriverManager;
import com.jiniebox.jangbogo.util.ExceptionUtil;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * https://www.nonghyupmall.com 을 조회하는 클래스이다. (하나로마트 오프라인 매장 구매 내역) 사이트 메뉴 : 마이페이지 > 하나로마트 > 마트구매영수증
 * 보기
 *
 * @author KIUNSEA
 */
public class Hanaro extends MallSession implements PurchasedCollector {

  private static final Logger log = LogManager.getLogger(Hanaro.class);

  private static final String PURCHASE_HISTORY_URL =
      "https://www.nonghyupmall.com/BCI1020M/eltRctwList.nh";

  /**
   * @param id
   * @param pass
   */
  public Hanaro(String id, String pass) {
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
    driver.get("https://www.nonghyupmall.com");
    this.delayTime(2000);

    driver.navigate().to("https://www.nonghyupmall.com/BC41000R/loginViewPage.nh");
    this.delayTime(2000);

    // 로그인 시작
    driver.findElement(By.id("userID")).sendKeys(this.USER_ID);
    driver.findElement(By.id("password")).sendKeys(this.USER_PASS);

    WebElement elemLogin =
        driver.findElement(
            By.cssSelector(
                "#loginForm > div.inner > div > div.login-box > div.login-form > button"));
    elemLogin.click(); // 로그인 버튼 클릭

    this.delayTime(2000); // 페이지 이동후엔 세션 유지를 위해 지연시간이 필요하다

    // 로그인 성공 여부 확인
    WebElement btnLogout = driver.findElement(By.id("a_id_logout"));

    if ("로그아웃".equals(btnLogout.getText())) {
      log.debug("로그인 성공");
      return true;
    }

    return false;
  }

  @Override
  public void signout(WebDriver driver) {
    this.delayTime(1000);

    try {
      WebElement btnLogout = driver.findElement(By.id("a_id_logout"));
      btnLogout.click();
      this.delayTime(2000);
      log.debug("로그아웃 완료");
    } catch (Exception e) {
      log.debug("로그아웃 처리 중 예외: " + e.getMessage());
    }
  }

  /**
   * 마트구매영수증 목록을 순회하며 구매 내역을 수집한다. DB에 저장된 serial 값을 확인하여 이미 수집된 영수증은 건너뛴다.
   *
   * @param driver WebDriver 인스턴스
   * @return 수집된 영수증 목록 (미수집 건만 포함)
   */
  @SuppressWarnings("unchecked")
  @Override
  public JSONArray navigatePurchased(WebDriver driver) {

    JSONArray resArr = new JSONArray();

    // 마트구매영수증 목록 페이지 이동
    driver.navigate().to(PURCHASE_HISTORY_URL);
    this.delayTime(2000);

    // 목록의 영수증 행 찾기
    List<WebElement> receiptRows =
        driver.findElements(By.xpath("//*[@id='content']//table//tbody//tr"));
    int receiptCount = receiptRows.size();
    log.debug("영수증 목록 개수: {}", receiptCount);

    if (receiptCount == 0) {
      // 행이 없는 경우: 단일 상세보기 버튼으로 시도
      try {
        WebElement detailBtn = driver.findElement(By.xpath("//*[@id=\"eltRctwDtlView\"]"));
        detailBtn.click();
        this.delayTime(2000);
      } catch (Exception e) {
        log.debug("상세보기 버튼을 찾을 수 없음 (영수증 없음)");
        return resArr;
      }

      JSONObject receipt = parseDetailPage(driver);
      if (receipt != null && !isAlreadyCollected(receipt)) {
        resArr.add(receipt);
      }

      log.debug("하나로마트 구매 내역 수집 완료 - 영수증 수: {}", resArr.size());
      return resArr;
    }

    // 여러 영수증 순회
    for (int idx = 0; idx < receiptCount; idx++) {
      try {
        // 목록 페이지로 복귀 (첫 번째 제외)
        if (idx > 0) {
          driver.navigate().to(PURCHASE_HISTORY_URL);
          this.delayTime(2000);
          receiptRows = driver.findElements(By.xpath("//*[@id='content']//table//tbody//tr"));
          if (idx >= receiptRows.size()) break;
        }

        // 행 클릭하여 선택
        receiptRows.get(idx).click();
        this.delayTime(500);

        // 상세보기 버튼 클릭
        WebElement detailBtn = driver.findElement(By.xpath("//*[@id=\"eltRctwDtlView\"]"));
        detailBtn.click();
        this.delayTime(2000);

        // 상세 페이지 파싱
        JSONObject receipt = parseDetailPage(driver);
        if (receipt == null) continue;

        // 이미 수집된 영수증인지 확인
        if (isAlreadyCollected(receipt)) {
          log.debug("이미 수집된 영수증 건너뜀 - serial: {}", receipt.get("serial"));
          continue;
        }

        resArr.add(receipt);
      } catch (Exception e) {
        log.debug("영수증 [{}] 처리 중 오류: {}", idx + 1, e.getMessage());
      }
    }

    log.debug("하나로마트 구매 내역 수집 완료 - 영수증 수: {}", resArr.size());
    return resArr;
  }

  /**
   * 상세 페이지에서 영수증 정보를 파싱한다.
   *
   * @param driver WebDriver 인스턴스
   * @return 파싱된 영수증 JSON (실패 시 null)
   */
  @SuppressWarnings("unchecked")
  private JSONObject parseDetailPage(WebDriver driver) {
    List<WebElement> tables = driver.findElements(By.tagName("table"));

    if (tables.size() < 2) {
      log.debug("상세 페이지 테이블 부족 (발견: {}개)", tables.size());
      return null;
    }

    JSONObject receiptJson = new JSONObject();
    String datetime = null;
    String totalAmount = null;

    // table[0]: 구매 요약 정보 파싱 (구매일자, 구매처, 구매금액)
    WebElement summaryTable = tables.get(0);
    List<WebElement> summaryRows = summaryTable.findElements(By.tagName("tr"));

    for (WebElement row : summaryRows) {
      List<WebElement> thList = row.findElements(By.tagName("th"));
      List<WebElement> tdList = row.findElements(By.tagName("td"));

      for (int i = 0; i < thList.size() && i < tdList.size(); i++) {
        String key = thList.get(i).getText().trim();
        String value = tdList.get(i).getText().trim();

        if ("구매일자".equals(key)) {
          datetime = value.replace("-", "");
          receiptJson.put("datetime", datetime);
        } else if ("구매처".equals(key)) {
          receiptJson.put("mallname", value);
        } else if ("구매금액".equals(key)) {
          totalAmount = value.replaceAll("[^0-9]", "");
        }
      }
    }

    // serial: 구매일자_구매금액 조합으로 unique 식별
    String serial = datetime != null ? datetime : "";
    if (totalAmount != null) {
      serial += "_" + totalAmount;
    }
    receiptJson.put("serial", serial);

    // table[1]: 품목 목록 파싱 (품목 / 수량 / 금액)
    WebElement itemsTable = tables.get(1);
    List<WebElement> itemRows = itemsTable.findElements(By.xpath(".//tbody//tr"));

    JSONArray itemArr = new JSONArray();
    for (WebElement row : itemRows) {
      List<WebElement> tdList = row.findElements(By.tagName("td"));

      if (tdList.size() >= 3) {
        String name = tdList.get(0).getText().trim();
        String qty = tdList.get(1).getText().trim();
        String price = tdList.get(2).getText().trim();

        if ("품목".equals(name)) continue; // 헤더 행 skip

        JSONObject itemJson = new JSONObject();
        itemJson.put("name", name);
        itemJson.put("qty", qty);
        itemJson.put("price", price);
        itemArr.add(itemJson);
      }
    }
    receiptJson.put("items", itemArr);

    return receiptJson;
  }

  /**
   * DB에서 이미 수집된 영수증인지 확인한다.
   *
   * @param receipt 파싱된 영수증 JSON (serial, datetime 포함)
   * @return 이미 수집되었으면 true
   */
  private boolean isAlreadyCollected(JSONObject receipt) {
    try {
      String serial = (String) receipt.get("serial");
      String datetime = (String) receipt.get("datetime");
      if (serial != null && datetime != null) {
        JbgOrderDataAccessObject joDao = new JbgOrderDataAccessObject();
        JSONObject existing = joDao.getOrder(serial, datetime, null);
        return existing != null;
      }
    } catch (Exception e) {
      log.debug("기존 주문 확인 중 오류: {}", e.getMessage());
    }
    return false;
  }
}

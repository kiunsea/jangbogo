package com.jiniebox.jangbogo.mall;

import com.jiniebox.jangbogo.svc.util.WebDriverManager;
import java.util.List;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * 하나로마트(nonghyupmall.com) 크롤링 테스트 클래스
 *
 * 사이트 메뉴 : 마이페이지 > 하나로마트 > 마트구매영수증 보기 
 *
 * Step by step으로 서비스 페이지를 크롤링하고 페이지를 이동해가며 코드를 완성하기 위한 테스트 클래스입니다.
 * main 함수로 실행 가능하며, 각 테스트 메서드를 개별적으로 호출하여 단계별 테스트가 가능합니다.
 *
 * 테스트 순서:
 * 1. testWebDriverSetup() - WebDriver 셋업 테스트
 * 2. testSignin() - 로그인 테스트
 * 3. testNavigateToPurchased() - 구매 내역 페이지 이동 테스트
 * 4. testParsePurchased() - 구매 내역 파싱 테스트
 *
 * @author KIUNSEA
 */
public class HanaroTest {

  // 테스트용 로그인 정보 (실제 값으로 변경 필요)
  private static final String TEST_USER_ID = "otonapoi";
  private static final String TEST_USER_PASS = "Wtbtsaas7625@";

  // 하나로마트 URL
  private static final String BASE_URL = "https://www.nonghyupmall.com";
  private static final String LOGIN_URL = "https://www.nonghyupmall.com/BC41000R/loginViewPage.nh";

  // TODO: 구매 내역 페이지 URL을 탐색하여 업데이트
  private static final String PURCHASE_HISTORY_URL = "https://www.nonghyupmall.com/BCI1020M/eltRctwList.nh";

  private WebDriver driver;
  private JavascriptExecutor js;

  public static void main(String[] args) {
    System.out.println("=".repeat(60));
    System.out.println("Hanaro Mall Crawling Test");
    System.out.println("=".repeat(60));

    HanaroTest test = new HanaroTest();

    try {
      // Step 1: WebDriver 셋업
      test.testWebDriverSetup();

      // Step 2: 로그인 테스트
      test.testSignin();

      // 통합 테스트: 목록 → 상세 → 파싱 전체 흐름
      test.testFullFlow();

      System.out.println("\n" + "=".repeat(60));
      System.out.println("테스트 완료!");
      System.out.println("=".repeat(60));

    } catch (Exception e) {
      System.err.println("테스트 중 오류 발생: " + e.getMessage());
      e.printStackTrace();
    } finally {
      test.cleanup();
    }
  }

  /**
   * Step 1: WebDriver 셋업 테스트
   */
  public void testWebDriverSetup() {
    System.out.println("\n[Step 1] WebDriver 셋업 테스트");
    System.out.println("-".repeat(50));

    WebDriverManager wdm = new WebDriverManager();
    driver = wdm.getWebDriver();

    if (driver != null) {
      js = (JavascriptExecutor) driver;
      System.out.println("WebDriver 셋업 성공");
      System.out.println("  - Browser: " + WebDriverManager.getBrowserName(driver));
    } else {
      throw new RuntimeException("WebDriver 셋업 실패");
    }
  }

  /**
   * Step 2: 로그인 테스트
   */
  public void testSignin() {
    System.out.println("\n[Step 2] 로그인 테스트");
    System.out.println("-".repeat(50));

    // 메인 페이지 접속
    driver.get(BASE_URL);
    delayTime(2000);
    System.out.println("메인 페이지 접속 완료: " + BASE_URL);

    // 로그인 페이지로 이동
    driver.navigate().to(LOGIN_URL);
    delayTime(2000);
    System.out.println("로그인 페이지 이동 완료: " + LOGIN_URL);

    // 현재 페이지 정보 출력
    System.out.println("  - 현재 URL: " + driver.getCurrentUrl());
    System.out.println("  - 페이지 타이틀: " + driver.getTitle());

    // 로그인 폼 요소 확인
    try {
      WebElement userIdField = driver.findElement(By.id("userID"));
      WebElement passwordField = driver.findElement(By.id("password"));
      WebElement loginButton = driver.findElement(
          By.cssSelector("#loginForm > div.inner > div > div.login-box > div.login-form > button"));

      System.out.println("로그인 폼 요소 확인:");
      System.out.println("  - 아이디 입력 필드: " + (userIdField != null ? "찾음" : "없음"));
      System.out.println("  - 비밀번호 입력 필드: " + (passwordField != null ? "찾음" : "없음"));
      System.out.println("  - 로그인 버튼: " + (loginButton != null ? "찾음" : "없음"));

      // 로그인 실행
      userIdField.sendKeys(TEST_USER_ID);
      passwordField.sendKeys(TEST_USER_PASS);
      System.out.println("로그인 정보 입력 완료");

      loginButton.click();
      System.out.println("로그인 버튼 클릭");

      delayTime(3000);

      // 로그인 성공 여부 확인
      try {
        WebElement btnLogout = driver.findElement(By.id("a_id_logout"));
        if ("로그아웃".equals(btnLogout.getText())) {
          System.out.println("로그인 성공!");
          System.out.println("  - 현재 URL: " + driver.getCurrentUrl());
        } else {
          System.out.println("로그인 상태 확인 필요 - 로그아웃 버튼 텍스트: " + btnLogout.getText());
        }
      } catch (Exception e) {
        System.out.println("로그인 실패 또는 로그아웃 버튼을 찾을 수 없음");
        System.out.println("  - 현재 URL: " + driver.getCurrentUrl());
        System.out.println("  - 오류: " + e.getMessage());
      }

    } catch (Exception e) {
      System.err.println("로그인 폼 요소를 찾을 수 없음: " + e.getMessage());
    }
  }

  /**
   * Step 3: 구매 내역 페이지 이동 테스트
   *
   * 페이지 구조:
   * - 영수증 목록 컨테이너: //*[@id="content"] > table > tbody > tr
   * - 상세보기 버튼: //*[@id="eltRctwDtlView"]
   */
  public void testNavigateToPurchased() {
    System.out.println("\n[Step 3] 구매 내역 페이지 이동 테스트");
    System.out.println("-".repeat(50));

    // 마트구매영수증 페이지로 이동
    System.out.println("구매 내역 페이지로 이동 시도...");
    driver.navigate().to(PURCHASE_HISTORY_URL);
    delayTime(2000);

    System.out.println("  - 현재 URL: " + driver.getCurrentUrl());
    System.out.println("  - 페이지 타이틀: " + driver.getTitle());

    // 영수증 목록 테이블 분석
    analyzeReceiptTable();
  }

  /**
   * 영수증 목록 테이블 분석
   *
   * 구조: //*[@id="content"] > table > tbody > tr
   */
  private void analyzeReceiptTable() {
    System.out.println("\n[영수증 목록 테이블 분석]");

    try {
      // #content 내의 table > tbody > tr 목록 조회
      WebElement contentElem = driver.findElement(By.xpath("//*[@id=\"content\"]"));
      System.out.println("content 요소 찾음: " + (contentElem != null));

      // tbody 내의 tr 목록 조회
      List<WebElement> trList = driver.findElements(By.xpath("//*[@id=\"content\"]//table//tbody//tr"));
      System.out.println("영수증 목록 (tr) 개수: " + trList.size());

      // 각 tr의 정보 출력
      for (int i = 0; i < trList.size(); i++) {
        WebElement tr = trList.get(i);
        List<WebElement> tdList = tr.findElements(By.tagName("td"));

        System.out.println("\n  [" + i + "] TR 정보:");
        System.out.println("      TD 개수: " + tdList.size());

        for (int j = 0; j < tdList.size(); j++) {
          String tdText = tdList.get(j).getText().trim();
          if (tdText.length() > 50) tdText = tdText.substring(0, 50) + "...";
          System.out.println("      TD[" + j + "]: " + tdText);
        }

        // 상세보기 버튼 확인
        try {
          WebElement detailBtn = tr.findElement(By.xpath(".//*[@id=\"eltRctwDtlView\"]"));
          if (detailBtn != null) {
            System.out.println("      상세보기 버튼 발견!");
          }
        } catch (Exception e) {
          // 상세보기 버튼이 없는 행일 수 있음
        }
      }

    } catch (Exception e) {
      System.out.println("영수증 목록 테이블 분석 실패: " + e.getMessage());
      e.printStackTrace();
    }
  }

  /**
   * 영수증 상세 페이지 이동 테스트
   *
   * 첫 번째 영수증의 상세보기 버튼을 클릭하여 상세 페이지로 이동
   */
  public void testNavigateToDetail() {
    System.out.println("\n[영수증 상세 페이지 이동 테스트]");
    System.out.println("-".repeat(50));

    try {
      // 첫 번째 상세보기 버튼 클릭
      WebElement detailBtn = driver.findElement(By.xpath("//*[@id=\"eltRctwDtlView\"]"));
      System.out.println("상세보기 버튼 찾음");

      detailBtn.click();
      System.out.println("상세보기 버튼 클릭");

      delayTime(2000);

      System.out.println("  - 현재 URL: " + driver.getCurrentUrl());
      System.out.println("  - 페이지 타이틀: " + driver.getTitle());

      // 상세 페이지 구조 분석
      analyzeDetailPage();

    } catch (Exception e) {
      System.out.println("상세 페이지 이동 실패: " + e.getMessage());
      e.printStackTrace();
    }
  }

  /**
   * 영수증 상세 페이지 구조 분석
   */
  private void analyzeDetailPage() {
    System.out.println("\n[상세 페이지 구조 분석]");

    // 페이지 내 주요 요소 탐색
    try {
      // 테이블 요소 탐색
      List<WebElement> tables = driver.findElements(By.tagName("table"));
      System.out.println("테이블 요소 개수: " + tables.size());

      for (int i = 0; i < tables.size(); i++) {
        WebElement table = tables.get(i);
        System.out.println("  [" + i + "] class: " + table.getAttribute("class") +
                          ", id: " + table.getAttribute("id"));

        // 테이블 내용 미리보기
        String tableText = table.getText();
        if (tableText.length() > 200) tableText = tableText.substring(0, 200) + "...";
        System.out.println("      내용: " + tableText);
      }
    } catch (Exception e) {
      System.out.println("상세 페이지 분석 실패: " + e.getMessage());
    }

    // pre 태그 확인 (Emart처럼 영수증이 pre 태그로 표시될 수 있음)
    try {
      List<WebElement> preList = driver.findElements(By.tagName("pre"));
      System.out.println("\npre 요소 개수: " + preList.size());

      for (int i = 0; i < preList.size(); i++) {
        String preText = preList.get(i).getText();
        if (preText.length() > 300) preText = preText.substring(0, 300) + "...";
        System.out.println("  [" + i + "] " + preText);
      }
    } catch (Exception e) {
      System.out.println("pre 요소 탐색 실패: " + e.getMessage());
    }
  }

  /**
   * 현재 페이지 구조 분석 헬퍼 메서드 (일반용)
   */
  private void analyzePageStructure() {
    System.out.println("\n[페이지 구조 분석]");

    // 테이블 요소 탐색
    try {
      List<WebElement> tables = driver.findElements(By.tagName("table"));
      System.out.println("테이블 요소 개수: " + tables.size());

      for (int i = 0; i < tables.size(); i++) {
        WebElement table = tables.get(i);
        System.out.println("  [" + i + "] class: " + table.getAttribute("class") +
                          ", id: " + table.getAttribute("id"));
      }
    } catch (Exception e) {
      System.out.println("테이블 요소 탐색 실패: " + e.getMessage());
    }

    // 링크 요소 중 마이페이지/주문 관련 탐색
    try {
      List<WebElement> links = driver.findElements(By.tagName("a"));
      System.out.println("\n마이페이지/주문 관련 링크:");

      for (WebElement link : links) {
        String href = link.getAttribute("href");
        String text = link.getText().trim();

        if (href != null && (href.contains("mypage") || href.contains("order") ||
            href.contains("purchase") || href.contains("history") || href.contains("Rctw"))) {
          System.out.println("  - [" + text + "] -> " + href);
        }
      }
    } catch (Exception e) {
      System.out.println("링크 탐색 실패: " + e.getMessage());
    }
  }

  /**
   * Step 4: 상세 페이지 영수증 파싱 테스트
   *
   * 상세 페이지 구조 (eltRctwDtlList.nh):
   * - table[0]: 구매 요약 정보 (구매일자, 구매처, 구매금액)
   * - table[1]: 품목 목록 (품목 / 수량 / 금액)
   * - table[2~10]: 비어있는 테이블
   */
  @SuppressWarnings("unchecked")
  public void testParsePurchased() {
    System.out.println("\n[Step 4] 상세 페이지 영수증 파싱 테스트");
    System.out.println("-".repeat(50));

    try {
      List<WebElement> tables = driver.findElements(By.tagName("table"));

      if (tables.size() < 2) {
        System.out.println("테이블이 부족합니다. (발견: " + tables.size() + "개)");
        return;
      }

      JSONObject receiptJson = new JSONObject();

      // ---- table[0]: 구매 요약 정보 파싱 ----
      WebElement summaryTable = tables.get(0);
      List<WebElement> summaryRows = summaryTable.findElements(By.tagName("tr"));

      System.out.println("\n[구매 요약 정보]");
      for (WebElement row : summaryRows) {
        List<WebElement> thList = row.findElements(By.tagName("th"));
        List<WebElement> tdList = row.findElements(By.tagName("td"));

        for (int i = 0; i < thList.size() && i < tdList.size(); i++) {
          String key = thList.get(i).getText().trim();
          String value = tdList.get(i).getText().trim();
          System.out.println("  " + key + ": " + value);

          if ("구매일자".equals(key)) {
            receiptJson.put("datetime", value.replace("-", ""));
          } else if ("구매처".equals(key)) {
            receiptJson.put("mallname", value);
          } else if ("구매금액".equals(key)) {
            receiptJson.put("totalAmount", value);
          }
        }
      }

      // ---- table[1]: 품목 목록 파싱 ----
      WebElement itemsTable = tables.get(1);
      List<WebElement> itemRows = itemsTable.findElements(By.xpath(".//tbody//tr"));

      System.out.println("\n[품목 목록]");
      System.out.println(String.format("  %-30s %5s %10s", "품목", "수량", "금액"));
      System.out.println("  " + "-".repeat(50));

      JSONArray itemArr = new JSONArray();
      for (WebElement row : itemRows) {
        List<WebElement> tdList = row.findElements(By.tagName("td"));

        if (tdList.size() >= 3) {
          String name = tdList.get(0).getText().trim();
          String qty = tdList.get(1).getText().trim();
          String price = tdList.get(2).getText().trim();

          // 헤더 행 skip
          if ("품목".equals(name)) continue;

          System.out.println(String.format("  %-30s %5s %10s", name, qty, price));

          JSONObject itemJson = new JSONObject();
          itemJson.put("name", name);
          itemJson.put("qty", qty);
          itemJson.put("price", price);
          itemArr.add(itemJson);
        }
      }
      receiptJson.put("items", itemArr);

      System.out.println("  " + "-".repeat(50));
      System.out.println("  총 품목 수: " + itemArr.size());

      // ---- 결과 출력 ----
      System.out.println("\n[파싱 결과 JSON]");
      System.out.println(receiptJson.toJSONString());

    } catch (Exception e) {
      System.err.println("파싱 중 오류 발생: " + e.getMessage());
      e.printStackTrace();
    }
  }

  /**
   * 통합 테스트: 목록 → 상세 → 파싱 전체 흐름
   *
   * Hanaro.navigatePurchased() 구현에 활용할 수 있는 전체 흐름 테스트.
   * 흐름:
   * 1. 마트구매영수증 목록 페이지(eltRctwList.nh) 이동
   * 2. 상세보기 버튼(eltRctwDtlView) 클릭 → 상세 페이지(eltRctwDtlList.nh) 이동
   * 3. 상세 페이지에서 table[0](요약), table[1](품목) 파싱
   * 4. 목록 페이지로 복귀
   */
  @SuppressWarnings("unchecked")
  public void testFullFlow() {
    System.out.println("\n[통합 테스트] 목록 → 상세 → 파싱 전체 흐름");
    System.out.println("-".repeat(50));

    JSONArray resArr = new JSONArray();

    // 1. 마트구매영수증 목록 페이지 이동
    driver.navigate().to(PURCHASE_HISTORY_URL);
    delayTime(2000);
    System.out.println("목록 페이지 이동 완료: " + driver.getCurrentUrl());

    // 2. 상세보기 버튼 클릭 → 상세 페이지 이동
    try {
      WebElement detailBtn = driver.findElement(By.xpath("//*[@id=\"eltRctwDtlView\"]"));
      detailBtn.click();
      delayTime(2000);
      System.out.println("상세 페이지 이동 완료: " + driver.getCurrentUrl());
    } catch (Exception e) {
      System.out.println("상세보기 버튼을 찾을 수 없음 (영수증 없음)");
      System.out.println("결과: " + resArr.toJSONString());
      return;
    }

    // 3. 상세 페이지 파싱
    List<WebElement> tables = driver.findElements(By.tagName("table"));

    if (tables.size() < 2) {
      System.out.println("상세 페이지 테이블 부족 (발견: " + tables.size() + "개)");
      return;
    }

    // 3-1. table[0]: 구매 요약 정보 파싱
    WebElement summaryTable = tables.get(0);
    List<WebElement> summaryRows = summaryTable.findElements(By.tagName("tr"));

    JSONObject receiptJson = new JSONObject();
    for (WebElement row : summaryRows) {
      List<WebElement> thList = row.findElements(By.tagName("th"));
      List<WebElement> tdList = row.findElements(By.tagName("td"));

      for (int i = 0; i < thList.size() && i < tdList.size(); i++) {
        String key = thList.get(i).getText().trim();
        String value = tdList.get(i).getText().trim();

        if ("구매일자".equals(key)) {
          receiptJson.put("datetime", value.replace("-", ""));
        } else if ("구매처".equals(key)) {
          receiptJson.put("mallname", value);
        } else if ("구매금액".equals(key)) {
          receiptJson.put("totalAmount", value);
        }
      }
    }

    // 3-2. table[1]: 품목 목록 파싱
    WebElement itemsTable = tables.get(1);
    List<WebElement> itemRows = itemsTable.findElements(By.xpath(".//tbody//tr"));

    JSONArray itemArr = new JSONArray();
    for (WebElement row : itemRows) {
      List<WebElement> tdList = row.findElements(By.tagName("td"));

      if (tdList.size() >= 3) {
        String name = tdList.get(0).getText().trim();
        String qty = tdList.get(1).getText().trim();
        String price = tdList.get(2).getText().trim();

        if ("품목".equals(name)) continue;

        JSONObject itemJson = new JSONObject();
        itemJson.put("name", name);
        itemJson.put("qty", qty);
        itemJson.put("price", price);
        itemArr.add(itemJson);
      }
    }
    receiptJson.put("items", itemArr);
    resArr.add(receiptJson);

    // 4. 결과 출력
    System.out.println("\n[통합 테스트 결과]");
    System.out.println("  영수증 수: " + resArr.size());
    System.out.println("  구매처: " + receiptJson.get("mallname"));
    System.out.println("  구매일자: " + receiptJson.get("datetime"));
    System.out.println("  구매금액: " + receiptJson.get("totalAmount"));
    System.out.println("  품목 수: " + itemArr.size());
    System.out.println("\n  JSON:");
    System.out.println("  " + resArr.toJSONString());
  }

  /**
   * 로그아웃 테스트
   */
  public void testSignout() {
    System.out.println("\n[로그아웃 테스트]");
    System.out.println("-".repeat(50));

    try {
      WebElement btnLogout = driver.findElement(By.id("a_id_logout"));
      btnLogout.click();
      delayTime(2000);
      System.out.println("로그아웃 완료");
    } catch (Exception e) {
      System.out.println("로그아웃 실패: " + e.getMessage());
    }
  }

  /**
   * 지연 시간 적용
   */
  private void delayTime(long millisecond) {
    try {
      Thread.sleep(millisecond);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * 리소스 정리
   */
  public void cleanup() {
    if (driver != null) {
      driver.quit();
      System.out.println("\nWebDriver 종료");
    }
  }

  // ========================================
  // 유틸리티 메서드들 (개발 중 페이지 분석용)
  // ========================================

  /**
   * 현재 페이지의 HTML 소스 일부 출력 (디버깅용)
   */
  public void printPageSource(int maxLength) {
    String source = driver.getPageSource();
    System.out.println("\n[페이지 소스 (처음 " + maxLength + "자)]");
    System.out.println(source.substring(0, Math.min(source.length(), maxLength)));
  }

  /**
   * 특정 CSS 셀렉터로 요소 탐색 (디버깅용)
   */
  public void findElementsByCss(String cssSelector) {
    System.out.println("\n[CSS 셀렉터 탐색: " + cssSelector + "]");
    try {
      List<WebElement> elements = driver.findElements(By.cssSelector(cssSelector));
      System.out.println("찾은 요소 개수: " + elements.size());

      for (int i = 0; i < Math.min(elements.size(), 10); i++) {
        WebElement elem = elements.get(i);
        String text = elem.getText();
        if (text.length() > 100) text = text.substring(0, 100) + "...";
        System.out.println("  [" + i + "] " + elem.getTagName() + ": " + text);
      }
    } catch (Exception e) {
      System.out.println("탐색 실패: " + e.getMessage());
    }
  }

  /**
   * 특정 XPath로 요소 탐색 (디버깅용)
   */
  public void findElementsByXpath(String xpath) {
    System.out.println("\n[XPath 탐색: " + xpath + "]");
    try {
      List<WebElement> elements = driver.findElements(By.xpath(xpath));
      System.out.println("찾은 요소 개수: " + elements.size());

      for (int i = 0; i < Math.min(elements.size(), 10); i++) {
        WebElement elem = elements.get(i);
        String text = elem.getText();
        if (text.length() > 100) text = text.substring(0, 100) + "...";
        System.out.println("  [" + i + "] " + elem.getTagName() + ": " + text);
      }
    } catch (Exception e) {
      System.out.println("탐색 실패: " + e.getMessage());
    }
  }

  /**
   * JavaScript 실행 (디버깅용)
   */
  public Object executeScript(String script) {
    System.out.println("\n[JavaScript 실행]");
    System.out.println("Script: " + script);
    try {
      Object result = js.executeScript(script);
      System.out.println("Result: " + result);
      return result;
    } catch (Exception e) {
      System.out.println("실행 실패: " + e.getMessage());
      return null;
    }
  }

  /**
   * 현재 URL로 이동 (디버깅용)
   */
  public void navigateTo(String url) {
    System.out.println("\n[페이지 이동: " + url + "]");
    driver.navigate().to(url);
    delayTime(2000);
    System.out.println("현재 URL: " + driver.getCurrentUrl());
    System.out.println("페이지 타이틀: " + driver.getTitle());
  }

  /**
   * 스크린샷 정보 출력 (디버깅용)
   * 현재 페이지의 윈도우 핸들 및 크기 정보 출력
   */
  public void printWindowInfo() {
    System.out.println("\n[윈도우 정보]");
    System.out.println("현재 핸들: " + driver.getWindowHandle());
    System.out.println("모든 핸들: " + driver.getWindowHandles());
    System.out.println("윈도우 크기: " + driver.manage().window().getSize());
  }
}

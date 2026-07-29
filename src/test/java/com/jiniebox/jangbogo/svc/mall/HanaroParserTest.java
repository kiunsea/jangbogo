package com.jiniebox.jangbogo.svc.mall;

import static com.jiniebox.jangbogo.svc.mall.MallDomFixtures.cells;
import static com.jiniebox.jangbogo.svc.mall.MallDomFixtures.children;
import static com.jiniebox.jangbogo.svc.mall.MallDomFixtures.noChildren;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * 하나로마트 영수증 상세 페이지 DOM 추출 규칙 검증 (판단 대기 8).
 *
 * <p>브라우저·네트워크·DB 를 쓰지 않는다 — {@code parseDetailPage} 는 DOM 만 읽는다(중복 판정을 하는 {@code
 * isAlreadyCollected} 는 별개 메서드다). 검증 범위에 대해서는 {@link MallDomFixtures} 참조.
 *
 * @author KIUNSEA
 */
class HanaroParserTest {

  private static final By TABLE = By.tagName("table");
  private static final By TR = By.tagName("tr");
  private static final By TH = By.tagName("th");
  private static final By TD = By.tagName("td");
  private static final By ITEM_ROWS = By.xpath(".//tbody//tr");

  private final Hanaro hanaro = new Hanaro("test-id", "test-pass");

  /** th/td 쌍으로 된 요약 테이블 행. */
  private static WebElement summaryRow(String key, String value) {
    WebElement tr = mock(WebElement.class);
    children(tr, TH, cells(key));
    children(tr, TD, cells(value));
    return tr;
  }

  private static WebElement itemRow(String... tdTexts) {
    WebElement tr = mock(WebElement.class);
    children(tr, TD, cells(tdTexts));
    return tr;
  }

  private static WebElement summaryTable(WebElement... rows) {
    WebElement table = mock(WebElement.class);
    children(table, TR, rows);
    return table;
  }

  private static WebElement itemsTable(WebElement... rows) {
    WebElement table = mock(WebElement.class);
    if (rows.length == 0) {
      noChildren(table, ITEM_ROWS);
    } else {
      children(table, ITEM_ROWS, rows);
    }
    return table;
  }

  private static WebDriver page(WebElement... tables) {
    WebDriver driver = mock(WebDriver.class);
    when(driver.findElements(TABLE)).thenReturn(List.of(tables));
    return driver;
  }

  /** 표준 영수증 한 장. */
  private static WebDriver receiptPage(
      String date, String store, String amount, WebElement... items) {
    return page(
        summaryTable(
            summaryRow("구매일자", date), summaryRow("구매처", store), summaryRow("구매금액", amount)),
        itemsTable(items));
  }

  // ---------------------------------------------------------------- 구조 가드

  @Test
  @DisplayName("테이블이 2개 미만이면 null 을 반환한다")
  void returnsNullWhenTablesAreMissing() {
    assertNull(hanaro.parseDetailPage(page()));
    assertNull(hanaro.parseDetailPage(page(summaryTable())));
  }

  // ---------------------------------------------------------------- 요약 정보

  @Test
  @DisplayName("구매일자의 하이픈을 제거해 YYYYMMDD 로 만든다")
  void normalizesPurchaseDate() {
    JSONObject receipt = hanaro.parseDetailPage(receiptPage("2026-07-29", "하나로마트 양재점", "12,345원"));

    assertNotNull(receipt);
    assertEquals("20260729", receipt.get("datetime"), "수신측 계약은 datetime 이 8자리 숫자일 것을 요구한다.");
    assertEquals("하나로마트 양재점", receipt.get("mallname"));
  }

  @Test
  @DisplayName("serial 은 구매일자_구매금액(숫자만) 으로 합성된다")
  void synthesizesSerialFromDateAndAmount() {
    // 하나로마트는 주문번호를 주지 않는다. 이 합성값이 중복 판정의 기준이 된다.
    JSONObject receipt = hanaro.parseDetailPage(receiptPage("2026-07-29", "하나로마트 양재점", "12,345원"));

    assertEquals("20260729_12345", receipt.get("serial"));
  }

  @Test
  @DisplayName("구매금액이 없으면 serial 은 구매일자만으로 만든다")
  void synthesizesSerialWithoutAmount() {
    JSONObject receipt =
        hanaro.parseDetailPage(
            page(
                summaryTable(summaryRow("구매일자", "2026-07-29"), summaryRow("구매처", "하나로마트 양재점")),
                itemsTable()));

    assertEquals("20260729", receipt.get("serial"));
  }

  @Test
  @DisplayName("모르는 요약 항목은 무시한다")
  void ignoresUnknownSummaryKeys() {
    JSONObject receipt =
        hanaro.parseDetailPage(
            page(
                summaryTable(
                    summaryRow("영수증번호", "R-1"),
                    summaryRow("구매일자", "2026-07-29"),
                    summaryRow("결제수단", "신용카드"),
                    summaryRow("구매처", "하나로마트 양재점"),
                    summaryRow("구매금액", "8,000원")),
                itemsTable()));

    assertEquals("20260729", receipt.get("datetime"));
    assertEquals("20260729_8000", receipt.get("serial"));
    assertTrue(!receipt.containsKey("영수증번호"));
  }

  // ---------------------------------------------------------------- 품목 목록

  @Test
  @DisplayName("품목의 이름·수량·금액을 수집한다")
  void collectsItemFields() {
    JSONObject receipt =
        hanaro.parseDetailPage(
            receiptPage("2026-07-29", "하나로마트 양재점", "12,345원", itemRow("국산 대파", "2", "3,980")));

    JSONArray items = (JSONArray) receipt.get("items");
    assertEquals(1, items.size());

    JSONObject item = (JSONObject) items.get(0);
    assertEquals("국산 대파", item.get("name"));
    assertEquals("2", item.get("qty"));
    assertEquals("3,980", item.get("price"));
  }

  @Test
  @DisplayName("헤더 행(품목/수량/금액)은 상품으로 세지 않는다")
  void skipsTheHeaderRow() {
    JSONObject receipt =
        hanaro.parseDetailPage(
            receiptPage(
                "2026-07-29",
                "하나로마트 양재점",
                "12,345원",
                itemRow("품목", "수량", "금액"),
                itemRow("국산 대파", "2", "3,980")));

    JSONArray items = (JSONArray) receipt.get("items");
    assertEquals(1, items.size(), "헤더 행이 상품으로 섞이면 수신측에 '품목'이라는 상품이 저장된다.");
    assertEquals("국산 대파", ((JSONObject) items.get(0)).get("name"));
  }

  @Test
  @DisplayName("td 가 3개 미만인 행은 건너뛴다")
  void skipsRowsWithTooFewCells() {
    JSONObject receipt =
        hanaro.parseDetailPage(
            receiptPage(
                "2026-07-29",
                "하나로마트 양재점",
                "12,345원",
                itemRow("합계"),
                itemRow("부가세", "1,120"),
                itemRow("국산 대파", "2", "3,980")));

    JSONArray items = (JSONArray) receipt.get("items");
    assertEquals(1, items.size());
    assertEquals("국산 대파", ((JSONObject) items.get(0)).get("name"));
  }

  @Test
  @DisplayName("품목이 없으면 빈 배열을 넣는다")
  void putsEmptyArrayWhenNoItems() {
    JSONObject receipt = hanaro.parseDetailPage(receiptPage("2026-07-29", "하나로마트 양재점", "0원"));

    JSONArray items = (JSONArray) receipt.get("items");
    assertTrue(items.isEmpty(), "items 는 null 이 아니라 빈 배열이어야 한다 — 수신측이 배열임을 검사한다.");
  }

  @Test
  @DisplayName("여러 품목을 순서대로 수집한다")
  void collectsMultipleItemsInOrder() {
    JSONObject receipt =
        hanaro.parseDetailPage(
            receiptPage(
                "2026-07-29",
                "하나로마트 양재점",
                "12,345원",
                itemRow("품목", "수량", "금액"),
                itemRow("국산 대파", "2", "3,980"),
                itemRow("무항생제 계란", "1", "6,980")));

    JSONArray items = (JSONArray) receipt.get("items");
    assertEquals(2, items.size());
    assertEquals("국산 대파", ((JSONObject) items.get(0)).get("name"));
    assertEquals("무항생제 계란", ((JSONObject) items.get(1)).get("name"));
  }
}

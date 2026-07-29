package com.jiniebox.jangbogo.svc.mall;

import static com.jiniebox.jangbogo.svc.mall.MallDomFixtures.cells;
import static com.jiniebox.jangbogo.svc.mall.MallDomFixtures.child;
import static com.jiniebox.jangbogo.svc.mall.MallDomFixtures.children;
import static com.jiniebox.jangbogo.svc.mall.MallDomFixtures.text;
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
 * SSG 온라인 구매내역 DOM 추출 규칙 검증 (판단 대기 8).
 *
 * <p>브라우저·네트워크·DB 를 쓰지 않는다. 검증 범위에 대해서는 {@link MallDomFixtures} 참조.
 *
 * @author KIUNSEA
 */
class SsgParserTest {

  private static final By TD = By.xpath("td");
  private static final By DATE = By.xpath("td[1]/p");
  private static final By ORDER_NUM = By.xpath("td[2]/p");
  private static final By MALL_NAME = By.xpath("td[3]/p[1]/span/i/span");

  private static final By ITEM_ROWS =
      By.xpath("//div[@name='divShppUnit']/div[@class='codr_unit']/table/tbody/tr");
  private static final By ITEM_NAME = By.xpath("td[@class='codr_unit_cont']/p/a/span/span");
  private static final By ITEM_QTY =
      By.xpath(
          "td[@class='codr_unit_pricewrap']/span[@class='codr_unit_count']/em[@class='num notranslate']");

  private final Ssg ssg = new Ssg("test-id", "test-pass");

  /** td 4개짜리 정상 데이터 행. */
  private static WebElement dataRow(String date, String orderNum, String mallName) {
    WebElement tr = mock(WebElement.class);
    children(tr, TD, cells("date", "order", "mall", "link"));
    child(tr, DATE, text(date));
    child(tr, ORDER_NUM, text(orderNum));
    child(tr, MALL_NAME, text(mallName));
    return tr;
  }

  private static WebElement itemRow(String name, String qty) {
    WebElement tr = mock(WebElement.class);
    child(tr, ITEM_NAME, text(name));
    child(tr, ITEM_QTY, text(qty));
    return tr;
  }

  private static WebDriver detailPage(WebElement... itemRows) {
    WebDriver driver = mock(WebDriver.class);
    when(driver.findElements(ITEM_ROWS)).thenReturn(List.of(itemRows));
    return driver;
  }

  // ---------------------------------------------------------------- parseOnlineOrderRow

  @Test
  @DisplayName("주문번호의 하이픈과 구매일자의 점을 제거한다")
  void normalizesSerialAndDate() {
    JSONObject order = ssg.parseOnlineOrderRow(dataRow("2026.07.29", "1234-5678-9012", "이마트몰"));

    assertNotNull(order);
    assertEquals("123456789012", order.get("serial"));
    assertEquals("20260729", order.get("datetime"), "수신측 계약은 datetime 이 8자리 숫자일 것을 요구한다.");
    assertEquals("이마트몰", order.get("mallname"));
  }

  @Test
  @DisplayName("구매 내역 없음 안내 행(td 1개)은 건너뛴다")
  void skipsTheEmptyStateRow() {
    WebElement tr = mock(WebElement.class);
    children(tr, TD, cells("해당기간내에 온라인몰에서 구매하신 내역이 없습니다."));
    when(tr.getText()).thenReturn("해당기간내에 온라인몰에서 구매하신 내역이 없습니다.");

    // 이 행을 데이터 행으로 파싱하면 td[2]/p 에서 예외가 나고 수집이 통째로 실패한다.
    assertNull(ssg.parseOnlineOrderRow(tr));
  }

  @Test
  @DisplayName("td 가 3개인 행도 데이터 행으로 보지 않는다")
  void skipsRowWithTooFewCells() {
    WebElement tr = mock(WebElement.class);
    children(tr, TD, cells("a", "b", "c"));
    when(tr.getText()).thenReturn("a b c");

    assertNull(ssg.parseOnlineOrderRow(tr));
  }

  @Test
  @DisplayName("td 가 5개 이상인 행은 데이터 행으로 처리한다")
  void acceptsRowWithExtraCells() {
    WebElement tr = mock(WebElement.class);
    children(tr, TD, cells("a", "b", "c", "d", "e"));
    child(tr, DATE, text("2026.07.29"));
    child(tr, ORDER_NUM, text("1111-2222"));
    child(tr, MALL_NAME, text("SSG.COM"));

    JSONObject order = ssg.parseOnlineOrderRow(tr);

    assertNotNull(order, "컬럼이 늘어난 것만으로 데이터 행을 버리면 수집이 조용히 0건이 된다.");
    assertEquals("11112222", order.get("serial"));
  }

  @Test
  @DisplayName("하이픈이 없는 주문번호는 그대로 둔다")
  void keepsSerialWithoutHyphen() {
    JSONObject order = ssg.parseOnlineOrderRow(dataRow("2026.07.29", "123456789012", "이마트몰"));

    assertEquals("123456789012", order.get("serial"));
  }

  // ---------------------------------------------------------------- parseOnlineOrderItems

  @Test
  @DisplayName("상세 페이지에서 상품의 이름·수량을 수집한다")
  void collectsItemNameAndQty() {
    JSONArray items = ssg.parseOnlineOrderItems(detailPage(itemRow("신선한 우유 1L", "2")));

    assertEquals(1, items.size());
    JSONObject item = (JSONObject) items.get(0);
    assertEquals("신선한 우유 1L", item.get("name"));
    assertEquals("2", item.get("qty"));
  }

  @Test
  @DisplayName("상품 행이 없으면 빈 배열을 반환한다")
  void returnsEmptyArrayWhenNoItemRows() {
    JSONArray items = ssg.parseOnlineOrderItems(detailPage());

    assertTrue(items.isEmpty(), "null 이 아니라 빈 배열이어야 한다 — 수신측이 items 가 배열인지 검사한다.");
  }

  @Test
  @DisplayName("여러 상품을 순서대로 수집한다")
  void collectsMultipleItemsInOrder() {
    JSONArray items =
        ssg.parseOnlineOrderItems(detailPage(itemRow("첫번째 상품", "1"), itemRow("두번째 상품", "5")));

    assertEquals(2, items.size());
    assertEquals("첫번째 상품", ((JSONObject) items.get(0)).get("name"));
    assertEquals("5", ((JSONObject) items.get(1)).get("qty"));
  }
}

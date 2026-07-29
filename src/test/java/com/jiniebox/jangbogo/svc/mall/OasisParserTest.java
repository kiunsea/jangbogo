package com.jiniebox.jangbogo.svc.mall;

import static com.jiniebox.jangbogo.svc.mall.MallDomFixtures.attr;
import static com.jiniebox.jangbogo.svc.mall.MallDomFixtures.child;
import static com.jiniebox.jangbogo.svc.mall.MallDomFixtures.children;
import static com.jiniebox.jangbogo.svc.mall.MallDomFixtures.noChild;
import static com.jiniebox.jangbogo.svc.mall.MallDomFixtures.noChildren;
import static com.jiniebox.jangbogo.svc.mall.MallDomFixtures.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * 오아시스마켓 DOM 추출 규칙 검증 (판단 대기 8).
 *
 * <p>브라우저·네트워크·DB 를 쓰지 않는다. 검증 범위에 대해서는 {@link MallDomFixtures} 참조.
 *
 * @author KIUNSEA
 */
class OasisParserTest {

  private static final By ORDER_NUM = By.cssSelector("div.orderBoxInfo > div > span");
  private static final By DETAIL_LINK =
      By.cssSelector("div.productArea > div > div.orderProduct > a");
  private static final By ORDER_DATE = By.cssSelector("div.orderBoxInfo > strong");
  private static final By ITEMS_OUTER = By.cssSelector("div.productWrap");
  private static final By ITEM_DIV = By.xpath("//div[@class='product']");
  private static final By ITEM_NAME = By.cssSelector("div.orderProduct > a > div > span > em");
  private static final By ITEM_QTY = By.cssSelector("p.orderCount");
  private static final By ITEM_PRICE =
      By.cssSelector("div.orderBuyPrice > span.priceAfter > em:nth-child(2)");

  private final Oasis oasis = new Oasis("test-id", "test-pass");

  private static WebElement orderRow(String serialText) {
    WebElement row = mock(WebElement.class);
    child(row, ORDER_NUM, text(serialText));
    return row;
  }

  private static WebElement itemDiv(String name, String qty, String price) {
    WebElement div = mock(WebElement.class);
    child(div, ITEM_NAME, text(name));
    child(div, ITEM_QTY, text(qty));
    if (price == null) {
      noChild(div, ITEM_PRICE);
    } else {
      child(div, ITEM_PRICE, text(price));
    }
    return div;
  }

  private static WebDriver detailPage(String dateText, WebElement... items) {
    // 목 생성은 반드시 when(...) 밖에서 끝낸다. thenReturn(text(...)) 처럼 인자 안에서 새 목을
    // 스터빙하면 Mockito 가 중첩 스터빙으로 보고 UnfinishedStubbingException 을 던진다.
    WebElement dateEl = text(dateText);
    WebElement outer = mock(WebElement.class);

    WebDriver driver = mock(WebDriver.class);
    when(driver.findElement(ORDER_DATE)).thenReturn(dateEl);
    when(driver.findElement(ITEMS_OUTER)).thenReturn(outer);
    if (items.length == 0) {
      noChildren(outer, ITEM_DIV);
    } else {
      children(outer, ITEM_DIV, items);
    }
    return driver;
  }

  // ---------------------------------------------------------------- parseOrderSummary

  @Test
  @DisplayName("주문번호를 감싼 괄호를 벗겨 serial 로 쓴다")
  void stripsParenthesesFromSerial() {
    JSONObject order = oasis.parseOrderSummary(orderRow("(2026-0729-1234)"));

    assertEquals("2026-0729-1234", order.get("serial"));
  }

  @Test
  @DisplayName("괄호 없는 주문번호는 그대로 둔다")
  void keepsSerialWithoutParentheses() {
    JSONObject order = oasis.parseOrderSummary(orderRow("20260729ABCD"));

    assertEquals("20260729ABCD", order.get("serial"));
  }

  @Test
  @DisplayName("두 글자 이하 주문번호는 잘라내지 않는다")
  void doesNotTruncateVeryShortSerial() {
    // substring(1, len-1) 을 무조건 적용하면 짧은 값이 빈 문자열이 된다. 길이 가드가 그것을 막는다.
    JSONObject order = oasis.parseOrderSummary(orderRow("()"));

    assertEquals("()", order.get("serial"));
  }

  @Test
  @DisplayName("mallname 은 오아시스마켓 고정값이다")
  void setsFixedMallName() {
    JSONObject order = oasis.parseOrderSummary(orderRow("(A1)"));

    assertEquals("오아시스마켓", order.get("mallname"));
  }

  // ---------------------------------------------------------------- extractDetailLink

  @Test
  @DisplayName("상세 링크는 a 태그의 href 다")
  void extractsDetailHref() {
    WebElement row = mock(WebElement.class);
    child(row, DETAIL_LINK, attr("href", "https://www.oasis.co.kr/myPage/orderDetail?no=1"));

    assertEquals("https://www.oasis.co.kr/myPage/orderDetail?no=1", oasis.extractDetailLink(row));
  }

  // ---------------------------------------------------------------- applyOrderDetail

  @Test
  @DisplayName("구매일자의 점을 제거해 YYYYMMDD 로 만든다")
  void normalizesPurchaseDate() {
    JSONObject order = new JSONObject();

    oasis.applyOrderDetail(detailPage("2026.07.29"), order);

    assertEquals("20260729", order.get("datetime"), "수신측 계약은 datetime 이 8자리 숫자일 것을 요구한다.");
  }

  @Test
  @DisplayName("구매일자 앞뒤 공백을 제거한다")
  void trimsPurchaseDate() {
    JSONObject order = new JSONObject();

    oasis.applyOrderDetail(detailPage("  2026.07.29  "), order);

    assertEquals("20260729", order.get("datetime"));
  }

  @Test
  @DisplayName("상품의 이름·수량·가격을 수집한다")
  void collectsItemFields() {
    JSONObject order = new JSONObject();

    oasis.applyOrderDetail(detailPage("2026.07.29", itemDiv("유기농 바나나", "2", "5,900")), order);

    JSONArray items = (JSONArray) order.get("items");
    assertEquals(1, items.size());

    JSONObject item = (JSONObject) items.get(0);
    assertEquals("유기농 바나나", item.get("name"));
    assertEquals("2", item.get("qty"));
    assertEquals("5,900", item.get("price"));
  }

  @Test
  @DisplayName("가격 요소가 없는 상품도 이름·수량은 수집한다")
  void keepsItemWhenPriceMissing() {
    JSONObject order = new JSONObject();

    oasis.applyOrderDetail(detailPage("2026.07.29", itemDiv("무항생제 계란", "1", null)), order);

    JSONArray items = (JSONArray) order.get("items");
    assertEquals(1, items.size(), "가격이 없다고 상품을 버리면 안 된다 — 수신측 계약의 필수 필드는 name/qty 다.");

    JSONObject item = (JSONObject) items.get(0);
    assertEquals("무항생제 계란", item.get("name"));
    assertEquals("1", item.get("qty"));
    assertFalse(item.containsKey("price"));
  }

  @Test
  @DisplayName("상품이 하나도 없으면 빈 배열을 넣는다")
  void putsEmptyArrayWhenNoItems() {
    JSONObject order = new JSONObject();

    oasis.applyOrderDetail(detailPage("2026.07.29"), order);

    JSONArray items = (JSONArray) order.get("items");
    assertTrue(items.isEmpty(), "items 는 null 이 아니라 빈 배열이어야 한다 — 수신측이 배열임을 검사한다.");
  }

  @Test
  @DisplayName("여러 상품을 순서대로 수집한다")
  void collectsMultipleItemsInOrder() {
    JSONObject order = new JSONObject();

    oasis.applyOrderDetail(
        detailPage(
            "2026.07.29",
            itemDiv("첫번째 상품", "1", "1,000"),
            itemDiv("두번째 상품", "3", "2,000"),
            itemDiv("세번째 상품", "2", null)),
        order);

    JSONArray items = (JSONArray) order.get("items");
    assertEquals(3, items.size());
    assertEquals("첫번째 상품", ((JSONObject) items.get(0)).get("name"));
    assertEquals("두번째 상품", ((JSONObject) items.get(1)).get("name"));
    assertEquals("세번째 상품", ((JSONObject) items.get(2)).get("name"));
    assertNull(((JSONObject) items.get(2)).get("price"));
  }
}

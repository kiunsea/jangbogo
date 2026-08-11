package com.jiniebox.jangbogo.svc.mall;

import static com.jiniebox.jangbogo.svc.mall.MallDomFixtures.cells;
import static com.jiniebox.jangbogo.svc.mall.MallDomFixtures.children;
import static com.jiniebox.jangbogo.svc.mall.MallDomFixtures.noChildren;
import static com.jiniebox.jangbogo.svc.mall.MallDomFixtures.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.NoAlertPresentException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * 하나로 오프라인 파서의 <b>변환 규칙</b>을 고정한다.
 *
 * <p>픽스처는 {@link MallDomFixtures} 로 손수 세운 목이다. 따라서 여기서 검증하는 것은 <b>셀렉터가 실사이트와 맞는지가 아니라</b> 추출한 값을
 * 수신측 계약으로 바꾸는 규칙이다 — 하이픈 제거, 금액에서 숫자만 남기기, serial 합성, 품목을 자리가 아닌 형태로 고르기.
 *
 * <p>여기 쓰인 상품명·점포명·날짜·금액은 전부 <b>합성</b>이다. 실제 값을 테스트에 넣지 않는다.
 *
 * @author KIUNSEA
 */
class HanaroOfflineParserTest {

  private final HanaroOffline hanaro = new HanaroOffline("test-id", "test-pass");

  // ── 거래 행 ─────────────────────────────────────────────────────────────

  @Test
  @DisplayName("구매일자는 하이픈을 떼어 8자리로 만든다 — 수신측 계약이다")
  void normalizesTheTransactionDate() {
    JSONObject tx =
        hanaro.parseTransactionRow(row("1", "2099-12-31", "합성내역", "합성점포", "12,345", ""));

    assertEquals("20991231", tx.get("datetime"));
  }

  @Test
  @DisplayName("serial 은 구매일자_구매금액 이다 — 이 화면은 주문번호를 주지 않는다")
  void synthesizesSerialFromDateAndAmount() {
    JSONObject tx =
        hanaro.parseTransactionRow(row("1", "2099-12-31", "합성내역", "합성점포", "12,345", ""));

    assertEquals("20991231_12345", tx.get("serial"), "금액에서 쉼표를 떼고 붙여야 한다.");
  }

  @Test
  @DisplayName("금액에서 숫자만 남긴다 — 쉼표·단위가 섞여도")
  void keepsOnlyDigitsOfTheAmount() {
    JSONObject tx =
        hanaro.parseTransactionRow(row("1", "2099-12-31", "합성내역", "합성점포", "12,345원", ""));

    assertEquals("20991231_12345", tx.get("serial"));
  }

  @Test
  @DisplayName("매장은 그대로 mallname 이 된다")
  void keepsTheStoreName() {
    JSONObject tx = hanaro.parseTransactionRow(row("1", "2099-12-31", "합성내역", " 합성점포 ", "100", ""));

    assertEquals("합성점포", tx.get("mallname"));
  }

  @Test
  @DisplayName("구매일자가 없는 행은 버린다 — 저장 단계에서 어차피 스킵된다")
  void dropsRowsWithoutADate() {
    assertNull(hanaro.parseTransactionRow(row("1", "  ", "합성내역", "합성점포", "100", "")));
  }

  // ── 품목: 자리가 아니라 형태로 고른다 ─────────────────────────────────────

  @Test
  @DisplayName("수량은 1EA 꼴에서 숫자만 뽑는다 — 수신측 계약이 숫자다")
  void readsQuantityByShape() {
    JSONObject item = hanaro.parseItem(List.of(text("합성상품"), text("2EA"), text("3,000")));

    assertEquals("합성상품", item.get("name"));
    assertEquals("2", item.get("qty"), "단위를 떼고 숫자만 남겨야 한다.");
    assertEquals("3,000", item.get("price"));
  }

  @Test
  @DisplayName("칸 순서가 바뀌어도 형태로 찾아낸다 — 자리 번호에 기대지 않는 이유다")
  void survivesReorderedFields() {
    JSONObject item = hanaro.parseItem(List.of(text("1EA"), text("9,900"), text("합성상품")));

    assertEquals("합성상품", item.get("name"));
    assertEquals("1", item.get("qty"));
  }

  @Test
  @DisplayName("빈 칸은 무시한다 — 여섯 칸 중 일부는 비어 있다")
  void ignoresBlankFields() {
    JSONObject item =
        hanaro.parseItem(List.of(text("합성상품"), text("  "), text("1EA"), text(""), text("1,000")));

    assertEquals("합성상품", item.get("name"));
    assertEquals("1", item.get("qty"));
  }

  @Test
  @DisplayName("이름에 슬래시·괄호·숫자가 섞여도 이름으로 남는다")
  void keepsComplexProductNames() {
    // 실측에서 (가가가)가가가/NNNa/가가 같은 형태가 나왔다 — 이름 안에 규격·괄호가 섞인다.
    JSONObject item =
        hanaro.parseItem(List.of(text("(합성)상품명/500g/봉지"), text("1EA"), text("12,000")));

    assertEquals("(합성)상품명/500g/봉지", item.get("name"));
    assertEquals("1", item.get("qty"));
  }

  @Test
  @DisplayName("수량을 못 찾으면 1 로 둔다 — 이름이 있으면 버리지 않는다")
  void defaultsQuantityToOne() {
    JSONObject item = hanaro.parseItem(List.of(text("합성상품"), text("3,000")));

    assertEquals("1", item.get("qty"));
  }

  @Test
  @DisplayName("이름을 못 고르면 버린다 — 틀린 값을 넣느니 빠뜨린다")
  void dropsItemsWithoutAName() {
    assertNull(hanaro.parseItem(List.of(text("1EA"), text("3,000"))));
    assertNull(hanaro.parseItem(List.of()));
  }

  @Test
  @DisplayName("접힌 칸은 textContent 로 읽는다 — getText 는 빈 문자열을 준다")
  void readsCollapsedFieldsFromTextContent() {
    // 이 화면의 품목 행은 접혀 있는 것이 정상이다. 보이는 글자만 읽으면 전부 빈칸이 된다.
    WebElement collapsed = mock(WebElement.class);
    when(collapsed.getText()).thenReturn("");
    when(collapsed.getAttribute("textContent")).thenReturn("합성상품");

    JSONObject item = hanaro.parseItem(List.of(collapsed, text("1EA")));

    assertNotNull(item, "접힌 칸을 못 읽어 품목이 통째로 버려졌다.");
    assertEquals("합성상품", item.get("name"));
  }

  // ── 품목 행 ─────────────────────────────────────────────────────────────

  @Test
  @DisplayName("ul 하나가 품목 하나다")
  void readsOneItemPerGroup() {
    WebElement itemRow = mock(WebElement.class);
    children(
        itemRow,
        HanaroOffline.ITEM_GROUPS,
        group("합성상품A", "1EA", "1,000"),
        group("합성상품B", "2EA", "2,000"));

    JSONArray items = hanaro.parseItemRow(itemRow);

    assertEquals(2, items.size());
    assertEquals("합성상품A", ((JSONObject) items.get(0)).get("name"));
    assertEquals("2", ((JSONObject) items.get(1)).get("qty"));
  }

  @Test
  @DisplayName("품목이 없으면 null 이 아니라 빈 배열이다 — 수신측이 배열임을 검사한다")
  void returnsEmptyArrayWhenThereAreNoItems() {
    WebElement itemRow = mock(WebElement.class);
    noChildren(itemRow, HanaroOffline.ITEM_GROUPS);

    JSONArray items = hanaro.parseItemRow(itemRow);

    assertNotNull(items);
    assertTrue(items.isEmpty());
  }

  // ── 표 전체 ─────────────────────────────────────────────────────────────

  @Test
  @DisplayName("거래 행과 품목 행이 번갈아 오는 구조를 읽는다")
  void pairsEachTransactionWithTheFollowingItemRow() {
    WebElement txRow = rowElement(cells("1", "2099-12-31", "합성내역 외 2건", "합성점포", "3,000", ""));
    WebElement itemRow = mock(WebElement.class);
    noChildren(itemRow, HanaroOffline.CELLS);
    children(itemRow, HanaroOffline.ITEM_GROUPS, group("합성상품", "1EA", "3,000"));

    WebElement table = tableWith(List.of(headerRow(), txRow, itemRow));
    JSONArray out = hanaro.parseTransactions(driverWith(table));

    assertEquals(1, out.size());
    JSONObject tx = (JSONObject) out.get(0);
    assertEquals("20991231", tx.get("datetime"));
    assertEquals(1, ((JSONArray) tx.get("items")).size());
  }

  @Test
  @DisplayName("머리글이 맞는 표가 없으면 0건이다 — 엉뚱한 표를 읽지 않는다")
  void returnsNothingWhenTheHeaderDoesNotMatch() {
    // 미끼 표에 <b>읽을 수 있는 거래 행을 넣는다.</b> 행이 없으면 머리글 필터를 없애도 결과가
    // 비어 있어 이 테스트가 초록으로 남는다 — 판별식이 죽은 채로 통과하던 첫 판이 그랬다.
    // 달력 위젯이 이 모양이다: 표이고, 행이 있고, 머리글이 요일이다.
    WebElement decoyRow = rowElement(cells("1", "2099-12-31", "합성내역", "합성점포", "9,900", ""));
    WebElement decoy = mock(WebElement.class);
    children(decoy, HanaroOffline.HEADER_CELLS, cells("일", "월", "화"));
    children(decoy, HanaroOffline.ROWS, decoyRow);

    JSONArray out = hanaro.parseTransactions(driverWith(decoy));

    assertTrue(out.isEmpty(), "머리글이 다른 표에서 거래를 읽었다 — 달력 위젯을 거래 목록으로 오인한다.");
  }

  @Test
  @DisplayName("머리글이 맞는 표만 골라 읽는다 — 미끼 표가 먼저 와도")
  void picksTheListTableEvenWhenADecoyComesFirst() {
    WebElement decoyRow = rowElement(cells("1", "2099-01-01", "미끼", "미끼점포", "1,000", ""));
    WebElement decoy = mock(WebElement.class);
    children(decoy, HanaroOffline.HEADER_CELLS, cells("일", "월", "화"));
    children(decoy, HanaroOffline.ROWS, decoyRow);

    WebElement txRow = rowElement(cells("1", "2099-12-31", "합성내역", "합성점포", "3,000", ""));
    WebElement itemRow = mock(WebElement.class);
    noChildren(itemRow, HanaroOffline.CELLS);
    children(itemRow, HanaroOffline.ITEM_GROUPS, group("합성상품", "1EA", "3,000"));
    WebElement real = tableWith(List.of(headerRow(), txRow, itemRow));

    JSONArray out = hanaro.parseTransactions(driverWith(decoy, real));

    assertEquals(1, out.size(), "미끼 표를 읽었거나 진짜 표를 놓쳤다.");
    assertEquals("20991231", ((JSONObject) out.get(0)).get("datetime"));
  }

  @Test
  @DisplayName("일시에 시각이 붙어도 8자리로 자른다 — 중복 판정 키가 어긋나면 매 회차 쌓인다")
  void keepsOnlyTheDatePartOfATimestamp() {
    JSONObject tx =
        hanaro.parseTransactionRow(row("1", "2099-12-31 14:32", "합성내역", "합성점포", "12,345", ""));

    assertEquals("20991231", tx.get("datetime"), "시각까지 붙어 들어가면 중복 판정이 통째로 깨진다.");
    assertEquals("20991231_12345", tx.get("serial"));
  }

  @Test
  @DisplayName("구분자가 점이어도 읽는다")
  void acceptsDotSeparatedDates() {
    JSONObject tx = hanaro.parseTransactionRow(row("1", "2099.12.31", "합성내역", "합성점포", "100", ""));

    assertEquals("20991231", tx.get("datetime"));
  }

  @Test
  @DisplayName("금액이 비면 점포명을 꼬리로 쓴다 — 날짜만으로는 그날 거래가 다 접힌다")
  void doesNotBuildADateOnlySerial() {
    JSONObject a = hanaro.parseTransactionRow(row("1", "2099-12-31", "합성내역", "합성점포A", "", ""));
    JSONObject b = hanaro.parseTransactionRow(row("2", "2099-12-31", "합성내역", "합성점포B", "", ""));

    assertEquals("20991231_합성점포A", a.get("serial"));
    assertTrue(!a.get("serial").equals(b.get("serial")), "점포가 달라도 같은 serial 이면 한 건으로 접힌다.");
  }

  // ── 로그인 알림 ─────────────────────────────────────────────────────────
  //
  // 이 사이트는 로그인 결과를 alert 으로 알린다("로그인 하였습니다."). 닫지 않으면 그 뒤의 모든
  // WebDriver 호출이 UnhandledAlertException 으로 터진다 — 실계정 실행이 로그인에 성공하고도
  // 그 예외로 죽었고, 단위테스트로는 한 번도 드러나지 않았다. 여기서 고정한다.

  @Test
  @DisplayName("열려 있는 알림을 닫고 글자를 돌려준다")
  void dismissesAnOpenAlert() {
    WebDriver driver = mock(WebDriver.class);
    WebDriver.TargetLocator locator = mock(WebDriver.TargetLocator.class);
    Alert alert = mock(Alert.class);
    when(driver.switchTo()).thenReturn(locator);
    when(locator.alert()).thenReturn(alert);
    when(alert.getText()).thenReturn("로그인 하였습니다.");

    assertEquals("로그인 하였습니다.", hanaro.dismissAlert(driver));
    verify(alert).accept();
  }

  @Test
  @DisplayName("알림이 없으면 null 이고 예외를 밖으로 내보내지 않는다")
  void returnsNullWhenThereIsNoAlert() {
    WebDriver driver = mock(WebDriver.class);
    WebDriver.TargetLocator locator = mock(WebDriver.TargetLocator.class);
    when(driver.switchTo()).thenReturn(locator);
    when(locator.alert()).thenThrow(new NoAlertPresentException());

    assertNull(hanaro.dismissAlert(driver));
  }

  @Test
  @DisplayName("로그인 판정이 알림을 먼저 치운다 — 안 치우면 그 자리에서 터진다")
  void signedInCheckDismissesTheAlertFirst() {
    WebDriver driver = mock(WebDriver.class);
    WebDriver.TargetLocator locator = mock(WebDriver.TargetLocator.class);
    Alert alert = mock(Alert.class);
    when(driver.switchTo()).thenReturn(locator);
    when(locator.alert()).thenReturn(alert);
    when(alert.getText()).thenReturn("로그인 하였습니다.");
    when(driver.getCurrentUrl()).thenReturn("https://www.nhhanaro.co.kr/nahh_70090.do?id=x");
    when(driver.findElements(By.cssSelector("input[type=password]"))).thenReturn(List.of());

    assertTrue(hanaro.isSignedIn(driver), "알림을 치운 뒤 로그인 상태로 읽혀야 한다.");
    verify(alert).accept();
  }

  // ── 조립 ────────────────────────────────────────────────────────────────

  private static List<WebElement> row(String... texts) {
    return List.of(cells(texts));
  }

  private static WebElement rowElement(WebElement... tds) {
    WebElement tr = mock(WebElement.class);
    children(tr, HanaroOffline.CELLS, tds);
    return tr;
  }

  private static WebElement headerRow() {
    WebElement tr = mock(WebElement.class);
    noChildren(tr, HanaroOffline.CELLS);
    return tr;
  }

  private static WebElement group(String... fieldTexts) {
    WebElement ul = mock(WebElement.class);
    children(ul, HanaroOffline.ITEM_FIELDS, cells(fieldTexts));
    return ul;
  }

  private static WebElement tableWith(List<WebElement> rows) {
    WebElement table = mock(WebElement.class);
    children(table, HanaroOffline.HEADER_CELLS, cells("번호", "일시", "내역", "매장", "구매금액(원)", ""));
    children(table, HanaroOffline.ROWS, rows.toArray(new WebElement[0]));
    return table;
  }

  private static WebDriver driverWith(WebElement... tables) {
    WebDriver driver = mock(WebDriver.class);
    when(driver.findElements(HanaroOffline.TABLE)).thenReturn(List.of(tables));
    return driver;
  }
}

package com.jiniebox.jangbogo.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jiniebox.jangbogo.svc.mall.Emart;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link Emart#parseReceipt(String)} 픽스처 단위테스트 (Phase 2-8).
 *
 * <p>목적은 정확성 증명이 아니라 <b>무회귀 판정 단위</b>다. 여기 적힌 값은 Phase 1 수정 완료 시점(v0.10.3)의 파서가 실제로 내놓은 출력이며, 이후
 * 변경이 이 출력을 바꾸면 테스트가 깨져 의도한 변경인지 확인하게 만든다. 실계정 재수집이 아니라 이 비교로 회귀를 판정한다.
 *
 * <p>브라우저·네트워크·DB 를 쓰지 않는다. {@code parseReceipt} 는 영수증 팝업 {@code <pre>} 의 텍스트만 받고, {@code new
 * Emart(id, pass)} 는 필드 대입뿐이라 그대로 호출할 수 있다.
 *
 * <p>픽스처는 전부 합성이다. 이유와 규칙은 {@code src/test/resources/fixtures/emart/README.md} 참조.
 *
 * <p>현행 동작을 고정하되 바람직하지 않은 것은 {@code 현행 동작} 주석으로 표시했다. 그 동작을 고칠 때는 이 테스트도 함께 고쳐야 하며, 그때의 실패는 회귀가 아니라
 * 의도된 변경이다.
 */
class EmartReceiptParserTest {

  private final Emart emart = new Emart("test-id", "test-pass");

  @Test
  @DisplayName("표준 4열 행 — 상품명·단가·수량·금액을 순서대로 매핑한다")
  void parsesStandardFourColumnRows() {
    JSONArray items = parseItems("basic-4col");

    assertEquals(2, items.size());
    assertItem(items, 0, "테스트상품가", "1,000", "2", "2,000");
    assertItem(items, 1, "테스트상품나", "3,500", "1", "3,500");
  }

  @Test
  @DisplayName("5열 행 — 선행 기호가 별도 컬럼이면 두 번째 컬럼을 상품명으로 쓴다")
  void parsesFiveColumnRowByShiftingOneColumn() {
    JSONArray items = parseItems("starred-5col");

    assertEquals(2, items.size());
    // "*" 가 3칸 이상 공백으로 분리되어 별도 컬럼이 되면 상품명은 인덱스 1 로 밀린다.
    // 기호 자체는 결과에 남지 않는다.
    assertItem(items, 0, "테스트우유 오리지널2입", "5,720", "1", "5,720");
    assertItem(items, 1, "테스트상품나", "3,500", "1", "3,500");
  }

  @Test
  @DisplayName("줄바꿈 결합 — 상품명 줄 다음에 정보 줄이 오는 형태를 한 행으로 합친다")
  void combinesNameLineFollowedByDataLine() {
    JSONArray items = parseItems("wrapped-name-first");

    assertEquals(2, items.size());
    // 현행 동작: 줄번호 "01 " 이 상품명에 그대로 남는다. combineExtraPattern01 은 줄번호를
    // 분리하지 않고 상품명 컬럼을 통째로 옮긴다.
    assertItem(items, 0, "01 테스트과자", "800", "1", "800");
    assertItem(items, 1, "02 테스트우유", "2,500", "1", "2,500");
  }

  @Test
  @DisplayName("줄바꿈 결합 — 정보 줄 다음에 상품명 줄이 오는 형태를 한 행으로 합친다")
  void combinesDataLineFollowedByNameLine() {
    JSONArray items = parseItems("wrapped-data-first");

    assertEquals(2, items.size());
    assertItem(items, 0, "01 테스트라면", "1,200", "2", "2,400");
    // 뒤따라온 상품명 줄에는 줄번호가 없으므로 이쪽은 깨끗하게 나온다.
    assertItem(items, 1, "테스트세제리필", "6,900", "1", "6,900");
  }

  @Test
  @DisplayName("할인 행 — 단가가 음수면 아이템으로 취급하지 않는다")
  void skipsDiscountRows() {
    JSONArray items = parseItems("discount-row");

    assertEquals(2, items.size());
    assertItem(items, 0, "테스트채소", "2,000", "1", "2,000");
    assertItem(items, 1, "테스트과일", "4,000", "1", "4,000");
    assertFalse(items.toString().contains("테스트할인지원"), "할인 행이 아이템 목록에 남아서는 안 된다");
  }

  @Test
  @DisplayName("요약 행 — 합계·부가세·품목수량과 숫자만 있는 행을 걸러낸다")
  void skipsSummaryAndNumericOnlyRows() {
    JSONArray items = parseItems("summary-rows");

    // 아이템 1건만 남고 요약 6행 + 숫자 행 + 빈 행은 전부 제거된다.
    assertEquals(1, items.size());
    assertItem(items, 0, "테스트상품가", "1,000", "2", "2,000");
  }

  @Test
  @DisplayName("아이템 구간이 비어 있으면 빈 목록을 반환한다 (예외를 던지지 않는다)")
  void returnsEmptyListWhenNoItemRows() {
    JSONArray items = parseItems("empty-items");

    assertNotNull(items);
    assertTrue(items.isEmpty());
  }

  @Test
  @DisplayName("컬럼이 4개 미만인 행 — 현행 동작: 빈 객체가 목록에 추가된다")
  void shortRowProducesEmptyItem() {
    JSONArray items = parseItems("short-row");

    // 현행 동작(결함). Emart.parseReceipt 의 itemArr.add(itemJson) 이 컬럼 수 분기 밖에 있어서,
    // 어느 분기에도 걸리지 않은 행이 빈 JSONObject 로 들어간다.
    //
    // 지금은 무해하다 — MallOrderUpdaterRunner 가 item.has("name") 으로 걸러내므로
    // jbg_item 에는 도달하지 않는다. 다만 그 하류 가드에 의존하고 있다는 뜻이다.
    // 이 동작을 고치면 아래 단정이 깨진다. 그때의 실패는 회귀가 아니라 의도된 변경이다.
    assertEquals(2, items.size());
    assertItem(items, 0, "테스트상품가", "1,000", "2", "2,000");
    assertTrue(((JSONObject) items.get(1)).isEmpty(), "4열 미만 행은 현재 빈 객체로 들어간다");
  }

  // 헬퍼

  private JSONArray parseItems(String fixtureName) {
    JSONObject receipt = emart.parseReceipt(readFixture(fixtureName));
    assertNotNull(receipt, "parseReceipt 는 null 을 반환하지 않는다");
    Object items = receipt.get("items");
    assertNotNull(items, "결과에 items 키가 있어야 한다");
    return (JSONArray) items;
  }

  private void assertItem(
      JSONArray items, int index, String name, String price, String qty, String sum) {
    JSONObject item = (JSONObject) items.get(index);
    assertEquals(name, item.get("name"), "items[" + index + "].name");
    assertEquals(price, item.get("price"), "items[" + index + "].price");
    assertEquals(qty, item.get("qty"), "items[" + index + "].qty");
    assertEquals(sum, item.get("sum"), "items[" + index + "].sum");
  }

  private String readFixture(String name) {
    String resource = "fixtures/emart/" + name + ".txt";
    try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
      if (in == null) {
        throw new IllegalStateException("픽스처를 찾을 수 없음: " + resource);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}

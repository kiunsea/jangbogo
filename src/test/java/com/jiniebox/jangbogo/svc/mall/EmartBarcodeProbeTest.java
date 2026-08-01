package com.jiniebox.jangbogo.svc.mall;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 바코드 미인식 시 남기는 구조 프로브 요약 검증 (B-3 후속).
 *
 * <p>실측에서 원인이 {@code #barcodeTargetRec} 안에 div 가 0개인 것으로 좁혀졌으나, 그것만으로는 세 갈래를 구분할 수 없다 — 바코드가
 * canvas/svg/이미지로 <b>그려지는</b> 것인지, 컨테이너가 정말 <b>비어 있는</b> 것인지, 아니면 대기가 짧아 <b>아직 안 그려진</b> 것인지. 프로브는
 * 그 판단에 필요한 신호만 모은다.
 *
 * <p>이 저장소는 PUBLIC 이고 로그는 배포본에 남으므로, 요약에 <b>구매 정보가 섞이지 않는 것</b>이 기능 요건이다. 텍스트는 내용이 아니라 길이만 센다. 아래
 * 테스트가 그 경계를 고정한다.
 *
 * <p>순수 함수라 브라우저·네트워크·DB 를 쓰지 않는다.
 *
 * @author KIUNSEA
 */
class EmartBarcodeProbeTest {

  @Test
  @DisplayName("컨테이너가 없으면 그 사실만 남긴다")
  void reportsMissingContainer() {
    // 셀렉터가 어긋났거나 팝업 전환이 실패한 경우. 다른 신호는 의미가 없다.
    assertEquals(
        "#barcodeTargetRec 없음",
        Emart.describeBarcodeContainer(false, "무시됨", "td", List.of("div"), 99));
  }

  @Test
  @DisplayName("자손 0개 · 텍스트 0자 — 그 영수증에 바코드가 없다")
  void reportsAnEmptyContainer() {
    String summary = Emart.describeBarcodeContainer(true, null, "td", List.of(), 0);

    assertEquals("#barcodeTargetRec 있음, 부모=td, 자손 0개, 텍스트 0자", summary);
    assertFalse(summary.contains("["), "자손이 없으면 집계 괄호를 붙이지 않는다: " + summary);
  }

  @Test
  @DisplayName("canvas 가 잡히면 바코드는 그려지는 것이다 — 텍스트 추출로는 못 읽는다")
  void surfacesCanvasRendering() {
    String summary =
        Emart.describeBarcodeContainer(true, "barcode-area", "div", List.of("canvas"), 0);

    assertTrue(summary.contains("canvas=1"), summary);
    assertTrue(summary.contains("class=barcode-area"), summary);
  }

  @Test
  @DisplayName("태그 집계는 많은 것부터, 같으면 이름순")
  void censusOrdersByCountThenName() {
    assertEquals("div=3, img=2, canvas=1", Emart.tagCensus(censusInput()));
  }

  private static List<String> censusInput() {
    return Arrays.asList("img", "div", "canvas", "div", "img", "div");
  }

  @Test
  @DisplayName("태그명은 대소문자를 가리지 않고 한 칸으로 센다")
  void censusNormalizesCase() {
    // Selenium 은 getTagName() 을 소문자로 주지만, 대문자로 오는 구현을 만나도
    // SVG 와 svg 가 따로 세어지면 판독이 어긋난다.
    assertEquals("svg=3", Emart.tagCensus(Arrays.asList("SVG", "svg", " Svg ")));
  }

  @Test
  @DisplayName("읽지 못한 태그명은 ? 로 세되 버리지 않는다")
  void censusKeepsUnreadableTags() {
    // 자손이 몇 개인지는 아는데 이름을 못 읽은 상태와, 자손이 아예 없는 상태는 다르다.
    assertEquals("?=2, div=1", Emart.tagCensus(Arrays.asList(null, "div", "   ")));
  }

  @Test
  @DisplayName("텍스트를 못 읽은 것과 0자를 구분한다")
  void distinguishesUnreadableTextFromEmptyText() {
    assertTrue(
        Emart.describeBarcodeContainer(true, null, "td", List.of(), -1).contains("텍스트 읽기 실패"));
    assertTrue(Emart.describeBarcodeContainer(true, null, "td", List.of(), 0).contains("텍스트 0자"));
  }

  @Test
  @DisplayName("부모를 못 읽어도 나머지 신호는 남긴다")
  void survivesUnreadableParent() {
    String summary = Emart.describeBarcodeContainer(true, null, null, List.of("img"), 0);

    assertTrue(summary.contains("부모=?"), summary);
    assertTrue(summary.contains("img=1"), summary);
  }

  @Test
  @DisplayName("자손 목록이 null 이어도 요약을 만든다")
  void toleratesNullDescendantList() {
    assertEquals(
        "#barcodeTargetRec 있음, 부모=td, 자손 0개, 텍스트 0자",
        Emart.describeBarcodeContainer(true, null, "td", null, 0));
  }

  @Test
  @DisplayName("빈 class 속성은 요약을 어지럽히지 않는다")
  void ignoresBlankClassAttribute() {
    assertFalse(Emart.describeBarcodeContainer(true, "   ", "td", List.of(), 0).contains("class="));
  }

  // ---------------------------------------------------------------
  // 2차 프로브 — 대체 컨테이너 탐색과 본문 날짜 추출 가능성 (B-3 후속)
  // ---------------------------------------------------------------

  @Test
  @DisplayName("팝업 구조는 태그 집계와 id 목록을 함께 준다")
  void describesPopupStructure() {
    String summary =
        Emart.describePopupStructure(
            Arrays.asList("DIV", "SPAN", "DIV", "PRE"), Arrays.asList("receiptArea", "printBtn"));

    assertTrue(summary.contains("태그 4개"), summary);
    assertTrue(summary.contains("div=2"), summary);
    assertTrue(summary.contains("id 2종"), summary);
    assertTrue(summary.contains("printBtn"), summary);
    assertTrue(summary.contains("receiptArea"), summary);
  }

  @Test
  @DisplayName("id 의 숫자는 가린다 — 주문번호가 섞여 있을 수 있다")
  void masksDigitsInsideIds() {
    String summary =
        Emart.describePopupStructure(
            List.of("DIV"), Arrays.asList("order_20991231123", "barcode1"));

    assertFalse(summary.contains("20991231"), "id 에 박힌 주문번호가 그대로 남았다: " + summary);
    assertTrue(summary.contains("order_NNNNNNNNNNN"), summary);
    assertTrue(summary.contains("barcodeN"), summary);
  }

  @Test
  @DisplayName("같은 형태의 id 는 한 종으로 묶는다")
  void collapsesIdsThatDifferOnlyByDigits() {
    // 반복 렌더링되는 행 id(item_1, item_2, …)가 목록을 가득 채우면 정작 볼 것이 밀린다.
    String summary =
        Emart.describePopupStructure(
            List.of("DIV"), Arrays.asList("item_1", "item_2", "item_3", "barcodeTargetRec"));

    assertTrue(summary.contains("id 2종"), summary);
    assertTrue(summary.contains("barcodeTargetRec"), summary);
  }

  @Test
  @DisplayName("id 가 너무 많으면 잘라내고 말줄임을 붙인다")
  void truncatesLongIdLists() {
    // 숫자를 쓰지 않는다 — 마스킹이 서로 다른 id 를 한 종으로 합쳐 버리면 상한에 닿지 않는다.
    List<String> many = new java.util.ArrayList<>();
    for (int i = 0; i < Emart.ID_SAMPLE_LIMIT + 5; i++) {
      many.add("box" + (char) ('a' + i / 26) + (char) ('a' + i % 26));
    }
    String summary = Emart.describePopupStructure(List.of("DIV"), many);

    assertTrue(summary.contains("id 45종"), "마스킹이 id 를 합쳐 버렸다: " + summary);
    assertTrue(summary.endsWith("…]"), "상한을 넘겼는데 말줄임이 없다: " + summary);
  }

  @Test
  @DisplayName("본문이 비면 그 사실만 남긴다")
  void reportsMissingBody() {
    assertEquals("본문 없음", Emart.describeReceiptDatePatterns(null));
    assertEquals("본문 없음", Emart.describeReceiptDatePatterns(""));
  }

  @Test
  @DisplayName("본문의 날짜는 형식과 위치만 보고한다 — 값은 싣지 않는다")
  void reportsDateFormatWithoutTheValue() {
    // 이 판정이 이 프로브의 존재 이유다. 본문에서 구매일시를 뽑을 수 있으면
    // serial·datetime 을 바코드에 의존하지 않아도 되고, 결함이 해결된다.
    String body = String.join("\n", "이마트 테스트점", "거래일시: 2099-12-31 14:32", "테스트상품가 1,000 2 2,000");

    String summary = Emart.describeReceiptDatePatterns(body);

    assertTrue(summary.contains("줄수=3"), summary);
    assertTrue(summary.contains("YYYY-MM-DD=1(줄2)"), summary);
    assertTrue(summary.contains("HH:MM=1(줄2)"), summary);
    assertFalse(summary.contains("2099-12-31"), "날짜 값이 그대로 실렸다: " + summary);
    assertFalse(summary.contains("14:32"), "시각 값이 그대로 실렸다: " + summary);
    assertFalse(summary.contains("테스트상품가"), "본문 내용이 실렸다: " + summary);
    assertFalse(summary.contains("거래일시"), "본문 라벨이 실렸다: " + summary);
  }

  @Test
  @DisplayName("날짜가 없으면 전부 0 으로 보고한다")
  void reportsZeroWhenNoDateIsPresent() {
    String summary = Emart.describeReceiptDatePatterns("상 품 명 단 가 수량 금 액\n합계 5,500");

    assertTrue(summary.contains("YYYY-MM-DD=0"), summary);
    assertTrue(summary.contains("8연속숫자=0"), summary);
    assertFalse(summary.contains("(줄"), "매치가 없는데 위치가 붙었다: " + summary);
  }

  @Test
  @DisplayName("금액의 자릿수를 8연속숫자로 오인하지 않는다")
  void doesNotMistakeAmountsForDates() {
    // 1,000 처럼 쉼표가 있는 금액은 연속 8자리가 아니다. 쉼표 없는 큰 수만 걸린다.
    String summary = Emart.describeReceiptDatePatterns("합 계 1,234,567\n포인트 12345");

    assertTrue(summary.contains("8연속숫자=0"), summary);
  }

  @Test
  @DisplayName("여러 형식이 섞여 있으면 각각 세고 첫 위치를 준다")
  void countsEachFormatSeparately() {
    String body =
        String.join("\n", "머리말", "2099/12/31", "유효 2099년 8월 24일 까지", "20991231001234567890");

    String summary = Emart.describeReceiptDatePatterns(body);

    assertTrue(summary.contains("YYYY/MM/DD=1(줄2)"), summary);
    assertTrue(summary.contains("YYYY년MM월DD일=1(줄3)"), summary);
    // 20자리 연속 숫자는 8자리 경계 조건에 걸리지 않는다 — 바코드를 날짜로 오인하지 않는다
    assertTrue(summary.contains("8연속숫자=0"), summary);
  }

  @Test
  @DisplayName("요약에는 구매 정보가 섞이지 않는다 — 텍스트 내용을 받지 않는다")
  void neverCarriesReceiptContent() {
    // 이 프로젝트는 PUBLIC 저장소이고 로그는 배포본에 남는다. 프로브가 담을 수 있는 것은
    // 태그 이름·개수·길이뿐이며, 시그니처에 텍스트 내용을 받는 자리가 애초에 없다.
    // 여기서 고정하는 것은 "길이를 넣으면 길이만 나온다" 는 것이다.
    String summary =
        Emart.describeBarcodeContainer(true, "barcode", "div", List.of("canvas"), 18_000);

    assertTrue(summary.contains("18000자"), summary);
    assertEquals(
        1, summary.split("18000", -1).length - 1, "길이가 두 번 이상 나오면 다른 값이 새고 있는 것이다: " + summary);
  }
}

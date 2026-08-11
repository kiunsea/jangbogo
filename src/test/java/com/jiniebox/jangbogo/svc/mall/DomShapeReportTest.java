package com.jiniebox.jangbogo.svc.mall;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 화면 형태 요약이 <b>원문을 흘리지 않는지</b>를 고정한다 (하나로 온라인/오프라인 분리).
 *
 * <h2>이 테스트의 존재 이유</h2>
 *
 * <p>{@link DomShapeReport} 는 사람의 구매 화면을 읽어 작업 산출물로 남기는 코드가 쓴다. 이 저장소는 PUBLIC 이므로 <b>요약에 구매 정보가 섞이지
 * 않는 것이 기능 요건</b>이고, 그 경계는 눈으로 읽어서는 지켜지지 않는다. 아래 테스트가 경계 그 자체다.
 *
 * <p>여기 쓰인 상품명·점포명·날짜는 전부 <b>합성</b>이다. 실제 값을 쓰면 부분 마스킹으로도 재식별 위험이 남는다.
 *
 * <p>순수 함수라 브라우저·네트워크·DB 를 쓰지 않는다.
 *
 * @author KIUNSEA
 */
class DomShapeReportTest {

  // ── 형태 ────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("날짜는 구분자만 남고 숫자는 전부 N 이 된다")
  void shapesADate() {
    // 파서가 하이픈 제거 규칙을 둘지 정하려면 구분자가 보여야 한다.
    assertEquals("NNNN-NN-NN", DomShapeReport.shape("2099-12-31"));
    assertEquals("NNNN.NN.NN", DomShapeReport.shape("2099.12.31"));
    assertEquals("NNNNNNNN", DomShapeReport.shape("20991231"));
  }

  @Test
  @DisplayName("금액은 쉼표가 남는다 — 숫자만 남기는 정규화가 필요한지 여기서 갈린다")
  void shapesAnAmount() {
    // 단위 '원' 은 한글이므로 예외 없이 가려진다. 가리지 않으면 '한글은 전부 가린다' 는 규칙에
    // 구멍이 생기고, 그 구멍으로 점포명 같은 짧은 한글이 새어 나온다. 쉼표가 남는 것만으로
    // "숫자만 남기는 정규화가 필요하다" 는 판단에는 충분하다.
    assertEquals("NN,NNN가", DomShapeReport.shape("12,345원"));
    assertEquals("NNN,NNN", DomShapeReport.shape("123,456"));
  }

  @Test
  @DisplayName("한글은 가, 라틴은 a, 그 밖의 글자는 x")
  void shapesLettersByScript() {
    assertEquals("가가가", DomShapeReport.shape("상품명"));
    assertEquals("aaaa", DomShapeReport.shape("Milk"));
    assertEquals("xx", DomShapeReport.shape("牛乳"));
  }

  @Test
  @DisplayName("긴 문자열은 접는다 — 짧은 것은 그대로 읽히는 편이 쓸모 있다")
  void collapsesOnlyLongRuns() {
    assertEquals("가".repeat(DomShapeReport.RUN_LIMIT), DomShapeReport.shape("가".repeat(12)));
    assertEquals("가×13", DomShapeReport.shape("나".repeat(13)));
  }

  @Test
  @DisplayName("공백은 길이와 무관하게 한 칸으로 접는다")
  void collapsesWhitespace() {
    assertEquals("가가 NN", DomShapeReport.shape("우유    12"));
    assertEquals("가 가", DomShapeReport.shape("우\t\n유"));
  }

  @Test
  @DisplayName("빈칸과 읽기 실패를 구분한다")
  void distinguishesBlankFromUnreadable() {
    assertEquals("(읽기 실패)", DomShapeReport.shape(null));
    assertEquals("(빈칸)", DomShapeReport.shape("   "));
  }

  @Test
  @DisplayName("원문의 글자는 요약에 남지 않는다 — 이 테스트가 이 클래스의 존재 이유다")
  void neverCarriesTheOriginalText() {
    // 합성 상품명·합성 점포명. 형태만 남고 글자는 하나도 남지 않아야 한다.
    String summary = DomShapeReport.shape("테스트점 우유 1000ml");

    assertFalse(summary.contains("테스트"), summary);
    assertFalse(summary.contains("우유"), summary);
    assertFalse(summary.contains("ml"), summary);
    assertFalse(summary.contains("1000"), summary);
    assertTrue(summary.contains("가"), summary);
    assertTrue(summary.contains("N"), summary);
  }

  // ── 라벨 ────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("표 머리글은 원문 그대로 — 파서가 이 문자열로 분기한다")
  void keepsShortDigitFreeLabels() {
    assertEquals("구매일자", DomShapeReport.headerLabel("구매일자"));
    assertEquals("상품명", DomShapeReport.headerLabel(" 상품명 "));
    assertEquals("주문/배송 조회", DomShapeReport.headerLabel("주문/배송 조회"));
  }

  @Test
  @DisplayName("머리글 자리에 숫자가 있으면 데이터로 보고 형태로 떨어뜨린다")
  void demotesLabelsThatContainDigits() {
    // 머리글 없는 표는 첫 행이 데이터다. 그것을 라벨로 믿으면 구매 정보가 그대로 실린다.
    String out = DomShapeReport.headerLabel("2099-12-31");

    assertEquals("NNNN-NN-NN", out);
    assertFalse(out.contains("2099"), out);
  }

  @Test
  @DisplayName("머리글이 길면 데이터로 보고 형태로 떨어뜨린다")
  void demotesLongLabels() {
    String longText = "가".repeat(DomShapeReport.LABEL_LENGTH_LIMIT + 1);

    assertFalse(DomShapeReport.headerLabel(longText).contains("가가가가가"), "긴 원문이 그대로 실렸다");
    assertTrue(DomShapeReport.headerLabel(longText).contains("×"), "접히지 않았다");
  }

  // ── 식별자 ──────────────────────────────────────────────────────────────

  @Test
  @DisplayName("id 에 박힌 주문번호는 자릿수만 남기고 가린다")
  void masksDigitsInsideIds() {
    assertEquals("order_NNNNNNNN", DomShapeReport.maskDigits("order_20991231"));
    assertFalse(DomShapeReport.maskDigits("order_20991231").contains("2099"));
  }

  @Test
  @DisplayName("숫자만 다른 id 는 한 종으로 묶는다")
  void collapsesIdsThatDifferOnlyByDigits() {
    String census = DomShapeReport.idCensus(List.of("item_1", "item_2", "item_3", "periodSearch"));

    assertTrue(census.startsWith("id 2종"), census);
    assertTrue(census.contains("item_N"), census);
    assertTrue(census.contains("periodSearch"), census);
  }

  @Test
  @DisplayName("id 가 없거나 전부 공백이면 0종")
  void reportsZeroIdSpecies() {
    assertEquals("id 0종", DomShapeReport.idCensus(null));
    assertEquals("id 0종", DomShapeReport.idCensus(Arrays.asList(null, "", "   ")));
  }

  @Test
  @DisplayName("id 가 너무 많으면 잘라내고 말줄임을 붙인다")
  void truncatesLongIdLists() {
    // 숫자를 쓰지 않는다 — 마스킹이 서로 다른 id 를 한 종으로 합쳐 버리면 상한에 닿지 않는다.
    List<String> many = new java.util.ArrayList<>();
    for (int i = 0; i < DomShapeReport.ID_SAMPLE_LIMIT + 5; i++) {
      many.add("box" + (char) ('a' + i / 26) + (char) ('a' + i % 26));
    }
    String census = DomShapeReport.idCensus(many);

    assertTrue(census.startsWith("id 45종"), "마스킹이 id 를 합쳐 버렸다: " + census);
    assertTrue(census.endsWith(", …]"), "상한을 넘겼는데 말줄임이 없다: " + census);
  }

  @Test
  @DisplayName("태그 집계는 많은 것부터, 같으면 이름순, 대소문자 무시")
  void censusOrdersByCountThenName() {
    assertEquals(
        "div=3, img=2, canvas=1",
        DomShapeReport.tagCensus(Arrays.asList("img", "DIV", "canvas", "div", "IMG", "Div")));
  }

  @Test
  @DisplayName("읽지 못한 태그명은 ? 로 세되 버리지 않는다")
  void censusKeepsUnreadableTags() {
    assertEquals("?=2, div=1", DomShapeReport.tagCensus(Arrays.asList(null, "div", "   ")));
  }

  // ── 주소 ────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("질의 문자열은 이름만 남기고 값을 지운다")
  void stripsQueryValues() {
    String out =
        DomShapeReport.safeUrl("https://example.test/list.do?startDt=20991201&endDt=20991231");

    assertTrue(out.startsWith("https://example.test/list.do?startDt&endDt"), out);
    assertFalse(out.contains("20991201"), "조회 기간이 그대로 남았다: " + out);
    assertFalse(out.contains("20991231"), out);
  }

  @Test
  @DisplayName("세션 토큰이 질의에 실려 있어도 값은 남지 않는다")
  void stripsSessionTokensFromQuery() {
    String out = DomShapeReport.safeUrl("https://example.test/a.do?jsessionid=abc123def&page=1");

    assertFalse(out.contains("abc123def"), "세션 토큰이 남았다: " + out);
    assertTrue(out.contains("jsessionid"), out);
  }

  @Test
  @DisplayName("질의가 없으면 주소를 그대로 둔다 — 화면 코드가 이 프로브의 산출물이다")
  void keepsPlainUrls() {
    assertEquals(
        "https://www.example.test/nahh_70090.do",
        DomShapeReport.safeUrl("https://www.example.test/nahh_70090.do"));
    assertEquals("(없음)", DomShapeReport.safeUrl(null));
  }

  @Test
  @DisplayName("경로에 붙은 세션 id 는 지운다 — 구형 .do 는 쿠키가 없으면 경로에 싣는다")
  void stripsPathJsessionId() {
    String out =
        DomShapeReport.safeUrl("https://example.test/list.do;jsessionid=A1B2C3D4E5?page=1");

    assertFalse(out.contains("A1B2C3D4E5"), "경로에 실린 세션 id 가 남았다: " + out);
    assertTrue(out.startsWith("https://example.test/list.do?page"), out);
  }

  @Test
  @DisplayName("세션 id 를 지운 뒤에도 질의 파라미터 이름은 어긋나지 않는다")
  void stripsPathJsessionIdWithoutShiftingTheQuery() {
    // 잘라낸 길이만큼 인덱스가 밀리면 엉뚱한 곳을 자른다 — 그 실수를 여기서 고정한다.
    String out =
        DomShapeReport.safeUrl("https://example.test/a.do;JSESSIONID=zz?startDt=1&endDt=2");

    assertEquals("https://example.test/a.do?startDt&endDt (값 가림)", out);
  }

  // ── 긴 숫자 ─────────────────────────────────────────────────────────────

  @Test
  @DisplayName("짧은 숫자는 살리고 긴 숫자만 가린다 — 화면 코드는 남고 주문번호는 죽는다")
  void masksOnlyLongDigitRuns() {
    assertEquals("nahh_70090.do", DomShapeReport.maskLongDigitRuns("nahh_70090.do"));
    assertEquals("/order/NNNNNNNNNN", DomShapeReport.maskLongDigitRuns("/order/2099123199"));
  }

  @Test
  @DisplayName("경계는 6자리 — 5자리는 살고 6자리는 죽는다")
  void masksAtTheDigitRunBoundary() {
    assertEquals("a12345", DomShapeReport.maskLongDigitRuns("a12345"));
    assertEquals("aNNNNNN", DomShapeReport.maskLongDigitRuns("a123456"));
  }

  // ── javascript: 링크 ────────────────────────────────────────────────────

  @Test
  @DisplayName("javascript 링크는 함수 이름만 남기고 인자를 통째로 버린다")
  void keepsOnlyTheFunctionName() {
    // 이 테스트가 실제 누출을 재현한다 — 숫자만 가리던 판에서는 상품명이 그대로 남았다.
    String out = DomShapeReport.callSignature("javascript:fnGoDetail('합성상품명','2099123456')");

    assertEquals("javascript:fnGoDetail(…)", out);
    assertFalse(out.contains("합성상품명"), "인자의 상품명이 남았다: " + out);
    assertFalse(out.contains("2099"), out);
  }

  @Test
  @DisplayName("괄호가 없는 javascript 링크는 숫자만 가린다")
  void masksDigitsWhenThereAreNoArguments() {
    assertEquals("javascript:goPageN", DomShapeReport.callSignature("javascript:goPage2"));
    assertEquals("javascript:void(…)", DomShapeReport.callSignature("javascript:void(0)"));
  }

  // ── 행 ──────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("셀 형태는 0 부터 번호를 붙인다 — 파서의 td 인덱스와 맞아야 한다")
  void numbersCellsFromZero() {
    String out = DomShapeReport.cellShapes(List.of("2099-12-31", "우유", "12,345원"));

    assertEquals("[0] NNNN-NN-NN | [1] 가가 | [2] NN,NNN가", out);
  }

  @Test
  @DisplayName("셀 목록에도 원문이 새지 않는다")
  void cellShapesCarryNoContent() {
    String out = DomShapeReport.cellShapes(List.of("테스트점", "합성상품", "9,900원"));

    assertFalse(out.contains("테스트점"), out);
    assertFalse(out.contains("합성상품"), out);
    assertFalse(out.contains("9,900"), out);
    assertTrue(out.contains("N,NNN가"), out);
  }

  @Test
  @DisplayName("셀이 없으면 그 사실만 남긴다")
  void reportsMissingCells() {
    assertEquals("(셀 없음)", DomShapeReport.cellShapes(null));
    assertEquals("(셀 없음)", DomShapeReport.cellShapes(List.of()));
  }
}

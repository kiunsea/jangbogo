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

package com.jiniebox.jangbogo.svc.mall;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 영수증 바코드 추출 검증 (판단 대기 B-3).
 *
 * <p>실측에서 17건 중 2건(12%)이 바코드 미인식이었다. 이전 코드는 {@code #barcodeTargetRec div} 중 마지막 것의 텍스트를 무조건 바코드로
 * 썼다.
 *
 * <p>순수 함수라 브라우저·네트워크·DB 를 쓰지 않는다.
 *
 * @author KIUNSEA
 */
class EmartBarcodeTest {

  /** 실제 형태: 앞 8자리가 구매일자(YYYYMMDD)이고 뒤에 매장·POS 번호가 붙는다. */
  private static final String VALID = "202607290012345678";

  @Test
  @DisplayName("바코드처럼 생긴 마지막 값을 고른다")
  void picksTheLastBarcodeLikeValue() {
    assertEquals(VALID, Emart.extractReceiptBarcode(List.of("영수증", VALID)));
  }

  @Test
  @DisplayName("뒤에 라벨 div 가 붙어도 바코드를 찾아낸다")
  void skipsTrailingNonBarcodeDivs() {
    // "마지막 div" 라는 위치 가정이 깨지는 경우다. 이전 코드는 '모바일 영수증' 을 serial 로 넣었다.
    assertEquals(VALID, Emart.extractReceiptBarcode(List.of("헤더", VALID, "모바일 영수증", "· 유효기간 30일")));
  }

  @Test
  @DisplayName("공백을 제거하고 돌려준다")
  void stripsWhitespace() {
    // 공백이 앞에 붙어 있으면 substring(0,8) 이 엉뚱한 datetime 을 만든다.
    assertEquals(VALID, Emart.extractReceiptBarcode(List.of("  2026 0729 0012345678  ")));
  }

  @Test
  @DisplayName("후보가 하나뿐이어도 바코드면 받는다")
  void acceptsSingleCandidate() {
    // 이전 코드는 size() > 1 이라야 읽어서, div 가 하나뿐인 영수증을 통째로 놓쳤다.
    assertEquals(VALID, Emart.extractReceiptBarcode(List.of(VALID)));
  }

  @Test
  @DisplayName("빈 문자열은 바코드로 보지 않는다 — substring 크래시를 막는다")
  void rejectsEmptyText() {
    // 이전 코드는 "" 를 바코드로 받아 substring(0,8) 에서 StringIndexOutOfBoundsException 을 냈다.
    assertNull(Emart.extractReceiptBarcode(List.of("", "   ")));
  }

  @Test
  @DisplayName("8자 미만은 거른다")
  void rejectsTooShort() {
    assertNull(Emart.extractReceiptBarcode(List.of("2026072")));
  }

  @Test
  @DisplayName("앞 8자가 숫자가 아니면 거른다")
  void rejectsNonNumericHead() {
    assertNull(Emart.extractReceiptBarcode(List.of("영수증번호12345678")));
  }

  @Test
  @DisplayName("앞 8자가 날짜 범위를 벗어나면 거른다")
  void rejectsImplausibleDate() {
    // 전화번호·금액 같은 긴 숫자가 바코드로 잘못 잡히는 것을 막는다.
    assertNull(Emart.extractReceiptBarcode(List.of("20261329001234")), "13월");
    assertNull(Emart.extractReceiptBarcode(List.of("20260732001234")), "32일");
    assertNull(Emart.extractReceiptBarcode(List.of("01012345678")), "휴대폰 번호");
  }

  @Test
  @DisplayName("후보가 없거나 null 이면 추측하지 않고 null 을 돌려준다")
  void returnsNullWhenNothingMatches() {
    // 틀린 serial 로 저장하는 것보다 못 찾았다고 하는 편이 낫다.
    assertNull(Emart.extractReceiptBarcode(null));
    assertNull(Emart.extractReceiptBarcode(List.of()));
    assertNull(Emart.extractReceiptBarcode(List.of("모바일 영수증", "유효기간")));
  }

  @Test
  @DisplayName("null 원소가 섞여 있어도 나머지 후보를 살린다")
  void toleratesNullElements() {
    List<String> candidates = new ArrayList<>(Arrays.asList(VALID, null, null));

    assertEquals(VALID, Emart.extractReceiptBarcode(candidates));
  }
}

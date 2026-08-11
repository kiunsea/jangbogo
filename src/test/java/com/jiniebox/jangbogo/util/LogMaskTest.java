package com.jiniebox.jangbogo.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 구매 데이터 마스킹의 계약을 <b>값으로</b> 고정한다.
 *
 * <p>{@code PurchaseDataLogMaskingTest} 는 "로그 호출이 마스킹을 거치는가" 라는 <b>형태</b>를 본다. 그것만으로는 마스킹이 실제로 값을
 * 지우는지 알 수 없다 — {@code shape} 가 입력을 그대로 돌려주도록 바뀌어도 그 검사는 초록이다. 여기서 값을 못 박는 이유다.
 *
 * <p>여기 쓰인 주문번호와 날짜는 전부 <b>합성</b>이다. 실제 구매 데이터를 테스트에 넣지 않는다.
 *
 * @author KIUNSEA
 */
class LogMaskTest {

  // ── shape: 형식만 남기고 숫자를 지운다 ──────────────────────────────────

  @Test
  @DisplayName("주문번호의 숫자를 지우고 구분자와 자릿수만 남긴다")
  void shapeErasesDigitsAndKeepsFormat() {
    // 오아시스 주문번호 형태(합성). 자릿수·하이픈 위치가 남아야 형식 이상을 눈으로 가릴 수 있다.
    assertEquals("NN-NNNNNNNNNN-NNNNN-NNNN", LogMask.shape("00-1234567890-12345-1234"));
  }

  @Test
  @DisplayName("구매일자도 자릿수만 남는다 — 8자리인지 아닌지가 진단의 핵심이다")
  void shapeKeepsTheLengthOfAPurchaseDate() {
    // 구매일자가 8자리가 아니어서 스킵된 주문이 실제로 있었다. 그 판단이 로그에서 되어야 한다.
    assertEquals("NNNNNNNN", LogMask.shape("20991231"));
    assertEquals("NNNN", LogMask.shape("2099"));
    assertEquals("NNNN-NN-NN", LogMask.shape("2099-12-31"));
  }

  @Test
  @DisplayName("원본 숫자가 한 글자도 남지 않는다")
  void shapeLeavesNoDigitBehind() {
    String masked = LogMask.shape("00-1234567890-12345-1234");

    assertFalse(masked.matches(".*\\d.*"), "숫자가 남았다 — 되돌릴 수 있는 값이 로그에 실린다: " + masked);
  }

  @Test
  @DisplayName("없는 값과 빈 값을 구분한다")
  void shapeDistinguishesMissingFromBlank() {
    // 하나로 합치면 '값이 오지 않았다' 와 '값이 깨졌다' 가 같은 모양이 되어 원인이 묻힌다.
    assertEquals(LogMask.NONE, LogMask.shape(null));
    assertEquals(LogMask.BLANK, LogMask.shape("   "));
  }

  @Test
  @DisplayName("어떤 입력에도 던지지 않고 null 도 돌려주지 않는다")
  void shapeNeverThrowsAndNeverReturnsNull() {
    // 마스킹이 던지면 그것을 부르는 로그 줄이 통째로 사라지거나 수집이 멈춘다.
    for (String input : new String[] {null, "", " ", "한글만", "😀", "0"}) {
      assertNotNull(LogMask.shape(input), "입력 [" + input + "] 에서 null 을 돌려줬다.");
    }
  }

  // ── name: 앞 한 글자 + 길이 ────────────────────────────────────────────

  @Test
  @DisplayName("매장명·상품명은 앞 한 글자와 길이만 남는다")
  void nameKeepsOnlyTheFirstCharacterAndLength() {
    // 형태의 정본은 AccountIdMasker 다. 여기서는 위임이 실제로 그 형태를 내는지 확인한다.
    assertEquals(AccountIdMasker.mask("이마트 어딘가점"), LogMask.name("이마트 어딘가점"));
    assertEquals("이***(8자)", LogMask.name("이마트 어딘가점"));
  }

  @Test
  @DisplayName("두 글자 이하는 앞 글자도 남기지 않는다")
  void veryShortNamesKeepNothing() {
    assertEquals("***(2자)", LogMask.name("우유"));
  }

  @Test
  @DisplayName("없는 값과 빈 값을 구분한다 (name)")
  void nameDistinguishesMissingFromBlank() {
    assertEquals(AccountIdMasker.NONE, LogMask.name(null));
    assertEquals(AccountIdMasker.BLANK, LogMask.name(""));
  }

  // ── 대조군 ───────────────────────────────────────────────────────────

  @Test
  @DisplayName("대조군 — 마스킹이 입력을 그대로 돌려주지 않는다")
  void maskingActuallyChangesTheValue() {
    // shape 가 항등함수가 되어도 PurchaseDataLogMaskingTest 는 초록이다(형태만 보므로).
    // 그 상태를 여기서 막는다.
    String order = "00-1234567890-12345-1234";
    String store = "이마트 어딘가점";

    assertFalse(order.equals(LogMask.shape(order)), "shape 가 입력을 그대로 돌려준다 — 마스킹이 죽었다.");
    assertFalse(store.equals(LogMask.name(store)), "name 이 입력을 그대로 돌려준다 — 마스킹이 죽었다.");
  }
}

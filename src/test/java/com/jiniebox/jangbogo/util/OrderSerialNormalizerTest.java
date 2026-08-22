package com.jiniebox.jangbogo.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 주문번호 표기 정규화의 계약을 <b>값으로</b> 고정한다. 브라우저·네트워크·DB 를 쓰지 않는다.
 *
 * <p>여기 쓰인 주문번호는 전부 <b>합성</b>이다. 형태만 실제 것을 빌렸다 — 오아시스 {@code 00-NNNNNNNNNN-NNNNN-NNNN}, SSG 영수증
 * {@code NNNNNNNNXXXXXX}, 이마트 영수증 바코드(숫자 26자리).
 *
 * @author KIUNSEA
 */
class OrderSerialNormalizerTest {

  private static final String BARE = "00-1234567890-12345-1234";

  @Test
  @DisplayName("'주문번호 : ' 꼬리표를 벗긴다 — 2026-08 실측 표기")
  void stripsLeadingLabel() {
    assertEquals(BARE, OrderSerialNormalizer.normalize("주문번호 : " + BARE));
    assertEquals(BARE, OrderSerialNormalizer.normalize(BARE));
  }

  @Test
  @DisplayName("콜론·공백의 유무, 전각 콜론, '주문 번호' 띄어쓰기를 가리지 않는다")
  void toleratesLabelSpelling() {
    assertEquals(BARE, OrderSerialNormalizer.normalize("주문번호: " + BARE));
    assertEquals(BARE, OrderSerialNormalizer.normalize("주문번호 :" + BARE));
    assertEquals(BARE, OrderSerialNormalizer.normalize("주문번호:" + BARE));
    assertEquals(BARE, OrderSerialNormalizer.normalize("주문번호 " + BARE));
    assertEquals(BARE, OrderSerialNormalizer.normalize("주문 번호 : " + BARE));
    assertEquals(BARE, OrderSerialNormalizer.normalize("주문번호 ： " + BARE));
    assertEquals(BARE, OrderSerialNormalizer.normalize("  주문번호 : " + BARE + "  "));
  }

  @Test
  @DisplayName("값 전체를 감싼 괄호를 벗긴다 — 오아시스가 한때 쓰던 표기")
  void stripsWrappingParentheses() {
    assertEquals("2026-0729-1234", OrderSerialNormalizer.normalize("(2026-0729-1234)"));
    assertEquals(BARE, OrderSerialNormalizer.normalize("주문번호 : (" + BARE + ")"));
    assertEquals(BARE, OrderSerialNormalizer.normalize("(주문번호 : " + BARE + ")"));
  }

  @Test
  @DisplayName("값 안쪽은 건드리지 않는다 — 하이픈, 안쪽 괄호, 영수증 번호")
  void leavesInsideAlone() {
    assertEquals("ABC(1)", OrderSerialNormalizer.normalize("ABC(1)"));
    // SSG 영수증 형태(합성)
    assertEquals("20991231ABCDEF", OrderSerialNormalizer.normalize("20991231ABCDEF"));
    // 이마트 영수증 바코드 형태(합성)
    assertEquals(
        "20991231123456789012345678",
        OrderSerialNormalizer.normalize("20991231123456789012345678"));
    // 두 글자 이하는 자르지 않는다 — OasisParserTest 의 길이 가드와 같은 이유
    assertEquals("()", OrderSerialNormalizer.normalize("()"));
  }

  @Test
  @DisplayName("null 은 null, 공백뿐이면 빈 문자열 — 어떤 입력에도 던지지 않는다")
  void handlesNullAndBlank() {
    assertNull(OrderSerialNormalizer.normalize(null));
    assertEquals("", OrderSerialNormalizer.normalize("   "));
  }

  @Test
  @DisplayName("두 번 적용해도 같다 — 수집기와 DAO 가 각각 거쳐도 결과가 흔들리지 않는다")
  void isIdempotent() {
    String once = OrderSerialNormalizer.normalize("주문번호 : (" + BARE + ")");
    assertEquals(once, OrderSerialNormalizer.normalize(once));
  }
}

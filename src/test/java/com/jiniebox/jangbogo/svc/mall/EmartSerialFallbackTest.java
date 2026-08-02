package com.jiniebox.jangbogo.svc.mall;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 신 템플릿 영수증의 {@code serial} 복구 검증 (B-3 해결 경로 B).
 *
 * <p>이마트가 영수증 화면을 개편해 {@code ern-} 접두 템플릿이 섞여 내려온다. 그 템플릿에는 {@code #barcodeTargetRec} 이 없고 식별자가
 * {@code biz_date} / {@code pos_str_code} / {@code pos_no} / {@code tran_no} 로 분해돼 있다.
 *
 * <h2>이 테스트가 지키는 것</h2>
 *
 * <p>중복 판정이 {@code serial_num} + {@code date_time} <b>조합</b>이므로, 날짜만으로 serial 을 만들면 <b>같은 날 산 영수증이
 * 한 건으로 뭉개진다</b>. 저장된 이력에 같은 날짜 3건이 실재하므로 가상의 위험이 아니다. 아래 테스트가 "꼬리가 없으면 만들지 않는다"는 규칙을 고정한다.
 *
 * <p>순수 함수라 브라우저·네트워크·DB 를 쓰지 않는다.
 *
 * @author KIUNSEA
 */
class EmartSerialFallbackTest {

  @Test
  @DisplayName("분해된 필드로 serial 을 조립한다 — 앞 8자리는 구매일자다")
  void composesSerialFromFields() {
    String serial = Emart.composeSerialFromFields("20991231", "1234", "05", "0042");

    assertEquals("209912311234050042", serial);
    assertEquals("20991231", serial.substring(0, 8), "호출측이 앞 8자리를 datetime 으로 쓴다");
  }

  @Test
  @DisplayName("구분자가 섞인 날짜도 받아들인다")
  void toleratesDelimitersInFields() {
    // 신 템플릿이 2099-12-31 처럼 내려줄 수 있다. 숫자만 남기고 본다.
    assertEquals(
        "209912311234050042", Emart.composeSerialFromFields("2099-12-31", "1234", "05", "0042"));
  }

  @Test
  @DisplayName("꼬리가 없으면 만들지 않는다 — 같은 날 영수증이 뭉개진다")
  void refusesToBuildADateOnlySerial() {
    // 이것이 이 클래스의 존재 이유다. serial='20991231' 로 저장하면 그날의 두 번째 영수증이
    // '기존 주문'으로 판정돼 통째로 스킵된다.
    assertNull(Emart.composeSerialFromFields("20991231", null, null, null));
    assertNull(Emart.composeSerialFromFields("20991231", "", "  ", ""));
    assertNull(Emart.composeSerialFromFields("20991231", "abc", "-", "/"), "숫자가 없는 꼬리는 꼬리가 아니다");
  }

  @Test
  @DisplayName("꼬리가 하나라도 있으면 만든다")
  void buildsWhenAnyTailFieldIsPresent() {
    assertEquals("2099123105", Emart.composeSerialFromFields("20991231", null, "05", null));
    assertEquals("209912310042", Emart.composeSerialFromFields("20991231", "", "", "0042"));
  }

  @Test
  @DisplayName("날짜가 없거나 짧으면 만들지 않는다")
  void refusesWithoutAUsableDate() {
    assertNull(Emart.composeSerialFromFields(null, "1234", "05", "0042"));
    assertNull(Emart.composeSerialFromFields("", "1234", "05", "0042"));
    assertNull(
        Emart.composeSerialFromFields("2099123", "1234", "05", "0042"), "7자리는 YYYYMMDD 가 아니다");
  }

  @Test
  @DisplayName("날짜 범위가 어긋나면 만들지 않는다")
  void refusesAnImpossibleDate() {
    // 엉뚱한 필드를 날짜로 오인해 조립하면 틀린 serial 로 저장된다. 만들지 않는 편이 낫다.
    assertNull(Emart.composeSerialFromFields("20991331", "1234", "05", "0042"), "13월");
    assertNull(Emart.composeSerialFromFields("20991200", "1234", "05", "0042"), "0일");
  }

  @Test
  @DisplayName("같은 날 다른 영수증은 다른 serial 이 된다")
  void distinguishesReceiptsBoughtOnTheSameDay() {
    // 중복 판정을 통과하려면 이것이 성립해야 한다.
    String first = Emart.composeSerialFromFields("20991231", "1234", "05", "0042");
    String second = Emart.composeSerialFromFields("20991231", "1234", "05", "0043");

    assertNotNull(first);
    assertNotNull(second);
    assertTrue(!first.equals(second), "같은 날 두 영수증이 같은 serial 이 됐다: " + first);
  }

  @Test
  @DisplayName("같은 영수증은 재수집해도 같은 serial 이 된다")
  void isStableAcrossCollections() {
    // 재현되지 않는 serial 은 재수집 때마다 중복 주문을 만든다.
    assertEquals(
        Emart.composeSerialFromFields("20991231", "1234", "05", "0042"),
        Emart.composeSerialFromFields("20991231", "1234", "05", "0042"));
  }

  // ---------------------------------------------------------------
  // 경로 C — 본문 날짜. 저장에는 쓰지 않고 진단으로만 쓴다.
  // ---------------------------------------------------------------

  @Test
  @DisplayName("본문에서 구매일자를 뽑는다")
  void extractsDateFromBody() {
    assertEquals("20991231", Emart.extractDateFromBody("머리말\n거래일시: 2099-12-31 14:32\n합계"));
    assertEquals("20991231", Emart.extractDateFromBody("2099/12/31"));
    assertEquals("20991231", Emart.extractDateFromBody("2099.12.31"));
  }

  @Test
  @DisplayName("날짜처럼 생겼어도 범위가 어긋나면 건너뛰고 다음 후보를 본다")
  void skipsImpossibleDatesInBody() {
    assertEquals("20991231", Emart.extractDateFromBody("코드 1234-99-99\n거래일시 2099-12-31"));
  }

  @Test
  @DisplayName("본문에 날짜가 없으면 null")
  void returnsNullWhenBodyHasNoDate() {
    assertNull(Emart.extractDateFromBody(null));
    assertNull(Emart.extractDateFromBody(""));
    assertNull(Emart.extractDateFromBody("상 품 명 단 가 수량 금 액\n합계 5,500"));
  }

  // ---------------------------------------------------------------
  // 진단 문자열 — 값이 아니라 형태만 남는다
  // ---------------------------------------------------------------

  @Test
  @DisplayName("값의 형태만 기술한다 — 값 자체는 남기지 않는다")
  void describesShapeNotValue() {
    assertEquals("없음", Emart.shapeOf(null));
    assertEquals("빈값", Emart.shapeOf("   "));
    assertEquals("숫자8자", Emart.shapeOf("20991231"));
    assertEquals("10자(숫자8)", Emart.shapeOf("2099-12-31"));

    // 진단에 값이 새지 않는지 — 자릿수만 나온다
    String shape = Emart.shapeOf("209912311234050042");
    assertTrue(shape.contains("18"), shape);
    assertTrue(!shape.contains("2099"), "값이 그대로 실렸다: " + shape);
  }

  @Test
  @DisplayName("경로 대조는 채택값이 아니라 A2 와 B 를 비교한다")
  void comparesTheTwoPathsNotTheChosenValue() {
    // 채택값과 비교하면 B 를 채택했을 때 늘 '일치'가 나와 아무것도 알려주지 않는다.
    // 실측 첫 회차의 로그가 정확히 그 상태였다.
    assertEquals("A2=B", Emart.describeRecoveryAgreement("209912311234", "209912311234"));
    assertEquals("A2≠B(A2 채택)", Emart.describeRecoveryAgreement("209912311234", "209912319999"));
    assertEquals("B만 성공", Emart.describeRecoveryAgreement(null, "209912311234"));
    assertEquals("A2만 성공", Emart.describeRecoveryAgreement("209912311234", null));
    assertEquals("둘 다 실패", Emart.describeRecoveryAgreement(null, null));
  }

  @Test
  @DisplayName("숫자만 남기는 정규화")
  void stripsNonDigits() {
    assertEquals("", Emart.digitsOnly(null));
    assertEquals("", Emart.digitsOnly("---"));
    assertEquals("20991231", Emart.digitsOnly("2099-12-31"));
  }
}

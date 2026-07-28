package com.jiniebox.jangbogo.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 수집 실행의 SUCCESS/FAIL 판정 규칙을 고정한다.
 *
 * <p>배경: 기존 판정식은 {@code skippedOrders > 0 && orderCount == 0} 을 FAIL 로 봤다. 그 결과 2026-07-28 실행에서
 * 17건을 정상 수집하고 15건이 중복, 2건이 키 누락으로 스킵된 정상 상황이 FAIL 로 기록됐다. 데이터가 따라잡힌 뒤에는 신규 주문이 0인 것이 정상이므로 이 오탐은 매
 * 주기마다 반복된다.
 */
class MallOrderUpdaterRunnerStatusTest {

  @Test
  @DisplayName("신규 주문이 있으면 성공")
  void newOrdersMeanSuccess() {
    assertEquals("SUCCESS", MallOrderUpdaterRunner.decideStatus(15, 0, 2));
  }

  @Test
  @DisplayName("신규 0 이어도 중복으로 걸러진 주문이 있으면 성공 (따라잡힌 상태의 정상 재실행)")
  void allDuplicatesMeanSuccess() {
    assertEquals("SUCCESS", MallOrderUpdaterRunner.decideStatus(0, 15, 2));
  }

  @Test
  @DisplayName("스킵이 없고 아무것도 안 걸렸으면 성공 (수집 결과가 원래 비어 있는 경우)")
  void emptyCollectionIsSuccess() {
    assertEquals("SUCCESS", MallOrderUpdaterRunner.decideStatus(0, 0, 0));
  }

  @Test
  @DisplayName("수집해 온 주문이 전부 저장 불가였으면 실패")
  void everythingUnusableMeansFailure() {
    assertEquals("FAIL", MallOrderUpdaterRunner.decideStatus(0, 0, 3));
  }

  @Test
  @DisplayName("일부만 저장돼도 성공 — 부분 스킵은 실패가 아니다")
  void partialSkipIsStillSuccess() {
    assertEquals("SUCCESS", MallOrderUpdaterRunner.decideStatus(1, 0, 5));
  }
}

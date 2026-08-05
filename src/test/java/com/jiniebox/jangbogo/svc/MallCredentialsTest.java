package com.jiniebox.jangbogo.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 자격증명이 로그로 새지 않는지 검증 (Phase 5-19).
 *
 * <p>record 는 {@code toString} 을 자동으로 만들고 그 안에 <b>모든 필드를 그대로 찍는다.</b> 로그 한 줄이나 예외 메시지 하나면 비밀번호가 파일에
 * 남는다. 그래서 직접 덮어썼고, 그 사실을 여기서 고정한다.
 *
 * <p>양성 대조군을 함께 둔다 — 접근자는 진짜 값을 돌려준다. 그것을 확인하지 않으면 "값이 애초에 비어 있어서" 통과한 것과 구분되지 않는다.
 *
 * <p>브라우저·네트워크·DB 를 쓰지 않는다.
 *
 * @author KIUNSEA
 */
class MallCredentialsTest {

  private static final String ID = "collector-id";
  private static final String PW = "collector-secret";

  @Test
  @DisplayName("toString 에 비밀번호도 아이디도 없다")
  void toStringHidesTheValues() {
    String printed = new MallCredentials(ID, PW).toString();

    assertFalse(printed.contains(PW), "비밀번호가 그대로 찍혔다: " + printed);
    assertFalse(printed.contains(ID), "아이디가 그대로 찍혔다: " + printed);
    assertTrue(printed.contains("비공개"), "가려졌다는 표시가 없다: " + printed);
  }

  @Test
  @DisplayName("[양성 대조군] 접근자는 진짜 값을 돌려준다")
  void accessorsStillReturnTheRealValues() {
    MallCredentials credentials = new MallCredentials(ID, PW);

    assertEquals(ID, credentials.id());
    assertEquals(PW, credentials.pw());
  }

  @Test
  @DisplayName("아이디가 없으면 없다고만 적는다")
  void reportsWhetherTheIdIsPresent() {
    assertTrue(new MallCredentials(null, PW).toString().contains("아이디 없음"));
    assertTrue(new MallCredentials("", PW).toString().contains("아이디 없음"));
    assertTrue(new MallCredentials(ID, PW).toString().contains("아이디 있음"));
  }
}

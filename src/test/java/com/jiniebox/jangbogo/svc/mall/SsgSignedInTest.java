package com.jiniebox.jangbogo.svc.mall;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;

/**
 * SSG 로그인 판정 규칙 검증 — 기준이 '존재' 가 아니라 '보임' 인지.
 *
 * <p>SSG 홈은 로그아웃 상태에서도 로그아웃 어포던스를 DOM 에 심어두고 JS 로 감춘다(실측: 존재=1, 보임=0). 그래서 {@code
 * findElements(...).isEmpty()} 로 존재만 보던 판정은 로그아웃 상태에서도 로그인 성공을 반환했고, 수집은 한참 뒤 엉뚱한 단계에서 깨졌다. 아래
 * 음성/양성 대조군이 그 되돌림을 막는다 — 특히 "존재하지만 안 보임" 케이스가 이 파일의 존재 이유다.
 *
 * <p>브라우저·네트워크·DB 를 쓰지 않는다. 셀렉터가 실사이트와 맞는지는 원리상 여기서 알 수 없고, 검증 대상은 '무엇을 로그인으로 볼 것인가' 뿐이다.
 *
 * @author KIUNSEA
 */
class SsgSignedInTest {

  private final Ssg ssg = new Ssg("test-id", "test-pass");

  /** 화면에 실제로 보이는 로그아웃 어포던스. */
  private static WebElement visible() {
    WebElement el = mock(WebElement.class);
    when(el.isDisplayed()).thenReturn(true);
    return el;
  }

  /** DOM 에는 있지만 감춰진 요소 — 로그아웃 상태의 SSG 홈이 내려주는 모양이다. */
  private static WebElement hidden() {
    WebElement el = mock(WebElement.class);
    when(el.isDisplayed()).thenReturn(false);
    return el;
  }

  /** 목록을 받아둔 뒤 DOM 에서 갈아끼워진 요소. */
  private static WebElement stale() {
    WebElement el = mock(WebElement.class);
    when(el.isDisplayed()).thenThrow(new StaleElementReferenceException("판정 도중 요소가 교체됨"));
    return el;
  }

  // ---------------------------------------------------------------- 음성/양성 대조군

  @Test
  @DisplayName("로그아웃 어포던스가 보이면 로그인 상태로 판정한다")
  void signedInWhenLogoutAffordanceIsVisible() {
    assertTrue(ssg.judgeSignedIn(List.of(visible()), List.of()));
  }

  @Test
  @DisplayName("로그아웃 어포던스가 DOM 에 있어도 보이지 않으면 로그아웃 상태로 판정한다")
  void signedOutWhenLogoutAffordanceIsPresentButHidden() {
    // 이 케이스가 원래 버그다. 존재만 보면 여기서 true 가 나오고, 로그인 실패가 성공으로 둔갑한다.
    assertFalse(
        ssg.judgeSignedIn(List.of(hidden()), List.of(hidden())),
        "SSG 홈은 로그아웃 상태에서도 어포던스를 DOM 에 둔다(실측: 존재=1, 보임=0). 존재 기준으로 되돌리면 이 단언이 깨진다.");
  }

  @Test
  @DisplayName("로그아웃 어포던스가 아예 없으면 로그아웃 상태로 판정한다")
  void signedOutWhenNoLogoutAffordanceExists() {
    assertFalse(ssg.judgeSignedIn(List.of(), List.of()));
  }

  @Test
  @DisplayName("id 로는 안 보여도 href 로 보이면 로그인 상태로 판정한다")
  void signedInWhenOnlyTheHrefAffordanceIsVisible() {
    assertTrue(
        ssg.judgeSignedIn(List.of(hidden()), List.of(visible())),
        "두 셀렉터는 OR 이다. 한쪽만 보면 헤더 구조가 바뀔 때마다 로그인 성공이 실패로 뒤집힌다.");
  }

  // ---------------------------------------------------------------- anyVisible 세부

  @Test
  @DisplayName("감춰진 요소 뒤에 보이는 요소가 있으면 보이는 것으로 본다")
  void findsAVisibleElementBehindHiddenOnes() {
    assertTrue(ssg.anyVisible(List.of(hidden(), hidden(), visible())));
  }

  @Test
  @DisplayName("요소 하나가 stale 이어도 나머지 후보로 판정을 이어간다")
  void skipsOnlyTheStaleElement() {
    assertTrue(
        ssg.anyVisible(List.of(stale(), visible())),
        "stale 을 목록 전체 단위로 잡으면 남은 멀쩡한 어포던스를 못 보고 로그인 성공을 실패로 판정한다.");
  }

  @Test
  @DisplayName("후보가 전부 stale 이면 예외를 던지지 않고 false 를 돌려준다")
  void returnsFalseWhenEveryCandidateIsStale() {
    // 실패는 예외가 아니라 값으로 돌려준다 — 판정 중 예외가 새면 수집 전체가 그 자리에서 끊긴다.
    assertFalse(ssg.anyVisible(List.of(stale(), stale())));
  }

  @Test
  @DisplayName("후보 목록이 null 이어도 예외 없이 로그아웃으로 본다")
  void treatsNullCandidateListAsSignedOut() {
    assertFalse(ssg.anyVisible(null));
  }
}

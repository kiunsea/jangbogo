package com.jiniebox.jangbogo.svc.util;

import static com.jiniebox.jangbogo.svc.util.SessionExpiryDetector.Verdict.ACTIVE;
import static com.jiniebox.jangbogo.svc.util.SessionExpiryDetector.Verdict.EXPIRED;
import static com.jiniebox.jangbogo.svc.util.SessionExpiryDetector.Verdict.NOT_JUDGED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jiniebox.jangbogo.svc.util.SessionExpiryDetector.LoginSignals;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.openqa.selenium.By;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * 세션 만료 판정 검증 (Phase 5-10).
 *
 * <p>이 판정이 틀리는 방향은 두 가지이고 값이 완전히 다르다.
 *
 * <ul>
 *   <li><b>만료를 놓치면</b> 다음 회차에 다시 드러난다. 그동안 0건이 쌓이지만 회복 가능하다.
 *   <li><b>정상을 만료로 읽으면</b> 그 몰이 일시중단돼 사람이 알아채기 전까지 아무것도 모으지 않는다.
 * </ul>
 *
 * <p>그래서 아래 테스트의 무게중심은 <b>오탐을 막는 쪽</b>에 있다 — 선언하지 않은 몰은 아예 판정하지 않고, 주소를 읽지 못한 회차는 만료로 세지 않는다.
 *
 * <p>브라우저·네트워크·DB 를 쓰지 않는다. 관측 경로만 가짜 드라이버로 확인한다.
 *
 * @author KIUNSEA
 */
class SessionExpiryDetectorTest {

  /** 실측된 몰의 선언을 흉내낸 값. 레지스트리 실제 값은 MallRegistryTest 가 고정한다. */
  private static final LoginSignals DECLARED =
      new LoginSignals(List.of("login"), List.of("#mem_id", "#mem_pw"));

  private static final String MEMBER_URL =
      "https://www.ssg.com/myssg/productMng/purchaseList.ssg?menu=purchaseList";

  private static final String LOGIN_URL =
      "https://member.ssg.com/member/login.ssg?returnUrl=purchaseList.ssg";

  // ---------------------------------------------------------------
  // 네 갈래 — 임무가 요구한 판정
  // ---------------------------------------------------------------

  @Test
  @DisplayName("로그인 URL 로 밀렸으면 만료다")
  void redirectedToLoginIsExpired() {
    // 로그인 화면 주소에는 원래 가려던 회원 페이지가 되돌아갈 파라미터로 붙어 있다.
    // 회원 페이지 표식이 들어 있다고 도달로 읽으면 만료가 정상으로 둔갑한다.
    assertEquals(EXPIRED, SessionExpiryDetector.judge(DECLARED, LOGIN_URL, false));
  }

  @Test
  @DisplayName("로그인 요소가 보이면 만료다 — 주소가 그대로여도 그렇다")
  void visibleLoginFormIsExpired() {
    // SPA 는 주소를 바꾸지 않고 화면만 로그인 폼으로 갈아 끼울 수 있다.
    // 주소만 보는 판정이었다면 이 경우를 통째로 놓친다.
    assertEquals(EXPIRED, SessionExpiryDetector.judge(DECLARED, MEMBER_URL, true));
  }

  @Test
  @DisplayName("정상 회원 페이지면 만료가 아니다")
  void memberPageIsActive() {
    assertEquals(ACTIVE, SessionExpiryDetector.judge(DECLARED, MEMBER_URL, false));
  }

  @Test
  @DisplayName("셀렉터를 선언하지 않은 몰은 판정하지 않는다")
  void undeclaredMallIsNotJudged() {
    // 모르는 몰에 마커를 추측해 넣으면 정상 회원 페이지를 만료로 읽어 멀쩡한 수집을 멈춘다.
    // 판정을 하지 않는 쪽이 안전하다 — 놓친 만료는 다음 회차에 다시 드러난다.
    assertEquals(NOT_JUDGED, SessionExpiryDetector.judge(LoginSignals.UNDECLARED, LOGIN_URL, true));
    assertEquals(NOT_JUDGED, SessionExpiryDetector.judge(null, LOGIN_URL, true));

    // 주소 마커만 선언된 반쪽 상태도 판정하지 않는다. 보임 기준이 이 판정의 축이다.
    LoginSignals urlOnly = new LoginSignals(List.of("login"), List.of());
    assertFalse(urlOnly.isDeclared());
    assertEquals(NOT_JUDGED, SessionExpiryDetector.judge(urlOnly, LOGIN_URL, true));
  }

  // ---------------------------------------------------------------
  // 오탐을 막는 규칙
  // ---------------------------------------------------------------

  @Test
  @DisplayName("주소를 읽지 못한 회차를 만료로 세지 않는다")
  void unreadableUrlIsNotExpiry() {
    // 브라우저가 잠깐 흔들려 주소를 못 읽은 것을 만료로 세면 멀쩡한 세션이 회차마다 버려진다.
    assertEquals(ACTIVE, SessionExpiryDetector.judge(DECLARED, null, false));
    assertEquals(ACTIVE, SessionExpiryDetector.judge(DECLARED, "   ", false));

    // 다만 로그인 폼이 보이면 주소와 무관하게 만료다.
    assertEquals(EXPIRED, SessionExpiryDetector.judge(DECLARED, null, true));
  }

  @Test
  @DisplayName("빈 마커·빈 셀렉터는 선언에서 걸러 낸다")
  void blankEntriesAreDropped() {
    // 빈 문자열은 어떤 주소와도 맞아 떨어진다. 걸러 내지 않으면 '선언은 돼 있는데 항상 만료' 가 된다.
    LoginSignals dirty =
        new LoginSignals(
            Arrays.asList("", "  ", "login", null), Arrays.asList(" #mem_id ", "", null));

    assertEquals(List.of("login"), dirty.urlMarkers());
    assertEquals(List.of("#mem_id"), dirty.selectors());
    assertEquals(ACTIVE, SessionExpiryDetector.judge(dirty, MEMBER_URL, false));
  }

  @Test
  @DisplayName("null 목록을 넘겨도 선언이 깨지지 않는다")
  void nullListsBecomeEmpty() {
    LoginSignals none = new LoginSignals(null, null);

    assertTrue(none.urlMarkers().isEmpty());
    assertTrue(none.selectors().isEmpty());
    assertFalse(none.isDeclared());
  }

  @Test
  @DisplayName("주소 마커는 대소문자를 가리지 않는다")
  void urlMarkerIsCaseInsensitive() {
    // 몰이 리다이렉트 주소의 표기를 바꾸는 것만으로 판정이 죽으면 안 된다.
    assertEquals(
        EXPIRED, SessionExpiryDetector.judge(DECLARED, "https://member.ssg.com/LOGIN.ssg", false));
  }

  // ---------------------------------------------------------------
  // 보임 기준 — 존재로 되돌리지 말 것
  // ---------------------------------------------------------------

  @Test
  @DisplayName("DOM 에 있어도 감춰져 있으면 보이는 것이 아니다")
  void hiddenElementsDoNotCount() {
    // 사이트가 로그인 어포던스를 로그아웃 상태가 아닐 때도 DOM 에 심어두고 감춘다.
    // isEmpty() 기준으로 되돌리면 정상 회원 페이지가 매번 만료로 읽힌다.
    assertFalse(SessionExpiryDetector.anyVisible(List.of(element(false), element(false))));
    assertTrue(SessionExpiryDetector.anyVisible(List.of(element(false), element(true))));
  }

  @Test
  @DisplayName("사라진 요소 하나 때문에 나머지를 버리지 않는다")
  void staleElementsAreSkippedOneByOne() {
    // 목록 전체를 감싸서 잡으면 뒤에 남은 멀쩡한 요소를 보지 못해 판정이 반대로 틀린다.
    WebElement stale = mock(WebElement.class);
    when(stale.isDisplayed()).thenThrow(new StaleElementReferenceException("사라짐"));

    assertTrue(SessionExpiryDetector.anyVisible(List.of(stale, element(true))));
    assertFalse(SessionExpiryDetector.anyVisible(List.of(stale, element(false))));
  }

  @Test
  @DisplayName("빈 목록·null 은 보이지 않는 것으로 다룬다")
  void emptyCandidatesAreNotVisible() {
    assertFalse(SessionExpiryDetector.anyVisible(null));
    assertFalse(SessionExpiryDetector.anyVisible(List.of()));
  }

  // ---------------------------------------------------------------
  // 관측 경로 (부수효과 경계)
  // ---------------------------------------------------------------

  @Test
  @DisplayName("선언하지 않은 몰에서는 브라우저를 건드리지도 않는다")
  void doesNotTouchTheBrowserWhenUndeclared() {
    // 판정하지 않을 것을 관측부터 하면 회차마다 불필요한 왕복과 대기가 생긴다.
    WebDriver driver = mock(WebDriver.class);
    AtomicLong waited = new AtomicLong();

    assertEquals(
        NOT_JUDGED,
        SessionExpiryDetector.observe(driver, "SsgSession", LoginSignals.UNDECLARED, waited::set));

    verifyNoInteractions(driver);
    assertEquals(0L, waited.get(), "판정하지도 않을 회차에서 대기했다.");
  }

  @Test
  @DisplayName("드라이버가 없으면 만료가 아니라 '판정 안 함' 이다")
  void missingDriverIsNotExpiry() {
    // 우리 쪽 고장을 사용자의 세션 만료로 둔갑시키면 엉뚱한 안내가 나가고 몰이 중단된다.
    assertEquals(NOT_JUDGED, SessionExpiryDetector.observe(null, "SsgSession", DECLARED, l -> {}));
  }

  @Test
  @DisplayName("관측 뒤 판정한다 — 로그인 화면이면 만료")
  void observesThenJudges() {
    WebDriver driver = mock(WebDriver.class);
    when(driver.getCurrentUrl()).thenReturn(LOGIN_URL);
    when(driver.findElements(Mockito.any(By.class))).thenReturn(new ArrayList<>());
    AtomicLong waited = new AtomicLong();

    assertEquals(
        EXPIRED, SessionExpiryDetector.observe(driver, "SsgSession", DECLARED, waited::set));
    assertEquals(
        SessionExpiryDetector.RENDER_SETTLE_MILLIS,
        waited.get(),
        "SPA 가 자리 잡기를 기다리지 않으면 관측이 흔들려 오탐이 난다.");
  }

  @Test
  @DisplayName("회원 페이지에 그대로 있고 로그인 폼도 안 보이면 살아 있다")
  void observesAnActiveSession() {
    // 가짜 요소는 바깥 stubbing 을 시작하기 '전' 에 만든다. thenReturn 의 인자 안에서 만들면
    // element() 안의 when() 이 아직 안 끝난 findElements stubbing 위에 겹쳐 Mockito 가
    // UnfinishedStubbingException 을 던진다 — 단언이 실행되기도 전에 죽는다.
    WebElement hidden = element(false);

    WebDriver driver = mock(WebDriver.class);
    when(driver.getCurrentUrl()).thenReturn(MEMBER_URL);
    when(driver.findElements(Mockito.any(By.class))).thenReturn(List.of(hidden));

    assertEquals(ACTIVE, SessionExpiryDetector.observe(driver, "SsgSession", DECLARED, l -> {}));
  }

  // ---------------------------------------------------------------
  // 기록에 쓰이는 값 / 이번 국면의 경계
  // ---------------------------------------------------------------

  @Test
  @DisplayName("만료에는 사람이 읽고 행동할 수 있는 사유가 붙는다")
  void expiryCarriesAnActionableReason() {
    // 이 문자열은 jbg_collect_log 의 SKIPPED 사유로 저장되고 화면에 그대로 나간다.
    // 사유가 행동으로 이어지지 않으면 남기는 의미가 없다.
    String reason = EXPIRED.reason();

    assertNotNull(reason);
    assertTrue(reason.contains("브라우저로 로그인"), "실제 버튼 이름이 사유에 없다: " + reason);
    assertTrue(EXPIRED.expired());

    assertNull(ACTIVE.reason(), "만료가 아닌 판정에 사유가 붙었다.");
    assertNull(NOT_JUDGED.reason());
    assertFalse(ACTIVE.expired());
    assertFalse(NOT_JUDGED.expired());
  }

  @Test
  @DisplayName("시간 기반 만료 임계값을 두지 않는다")
  void doesNotJudgeByElapsedTime() throws Exception {
    // 세션 수명 실측이 아직이라 지금 숫자를 박으면 근거 없는 상수가 그대로 굳는다. 짧으면 살아 있는
    // 세션을 매 회차 버리고, 길면 죽은 세션으로 계속 두드린다. 그 경계가 무너지는 형태는 '시계를
    // 읽는 한 줄' 이므로 소스 형태로 감시한다 — 판정 결과만 봐서는 재발을 잡을 수 없다.
    String code =
        Files.readString(
                Path.of("src/main/java/com/jiniebox/jangbogo/svc/util/SessionExpiryDetector.java"),
                StandardCharsets.UTF_8)
            .replaceAll("(?s)/\\*\\*.*?\\*/", "")
            .replaceAll("(?m)^\\s*//.*$", "");

    // 대조군. 아래는 전부 "없다" 형태의 단언이라, 소스를 못 읽었거나 주석 제거가 코드까지
    // 지워서 code 가 빈 문자열이 되면 <b>전부 그대로 초록</b>이 된다. 이 세션에서 실제로 두 번
    // 겪은 형태다 — 만료 감지는 테스트 25건이 초록인 채 프로덕션 호출자가 0건이었고, 배포
    // 산출물 가드는 판별식을 무력화해도 5건이 전부 통과했다. 먼저 '정말 읽었는가' 를 묻는다.
    assertTrue(
        code.contains("class SessionExpiryDetector"),
        "감시 대상 소스를 읽지 못했거나 주석 제거가 실행되는 코드까지 지웠다. 빈 문자열에는 어떤 시계도 없으므로 아래 단언은 항상 통과한다.");

    String[] clocks = {"System.currentTimeMillis", "Instant.now", "LocalDateTime.now", "new Date("};
    for (String clock : clocks) {
      assertFalse(code.contains(clock), "만료 판정이 시계를 읽는다: " + clock);
    }
  }

  /** 보임 여부만 대답하는 가짜 요소. */
  private static WebElement element(boolean displayed) {
    WebElement element = mock(WebElement.class);
    when(element.isDisplayed()).thenReturn(displayed);
    return element;
  }
}

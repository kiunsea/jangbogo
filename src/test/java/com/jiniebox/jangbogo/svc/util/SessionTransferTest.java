package com.jiniebox.jangbogo.svc.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

/**
 * 세션 캡처·주입 검증 (Phase 5-15).
 *
 * <p>브라우저를 띄우지 않는다 — CDP 로 오간 값만 들여다본다. 실제 왕복은 프로브({@code @Tag("probe")})가 맡는다.
 *
 * <p>여기서 고정하는 것은 <b>세션 쿠키가 살아서 건너가는가</b>다. 우리가 옮기려는 인증 쿠키가 바로 그 종류라, 만료 처리 한 줄이 잘못되면 캡처는 성공으로 보이는데
 * 주입한 브라우저는 로그아웃 상태가 된다.
 *
 * @author KIUNSEA
 */
class SessionTransferTest {

  /** {@code Network.getAllCookies} 가 주는 형태에 가깝게 만든 세션 쿠키. */
  private static Map<String, Object> sessionCookie(String name) {
    Map<String, Object> cookie = new LinkedHashMap<>();
    cookie.put("name", name);
    cookie.put("value", "토큰값");
    cookie.put("domain", ".ssg.com");
    cookie.put("path", "/");
    cookie.put("expires", -1);
    cookie.put("size", 42);
    cookie.put("httpOnly", true);
    cookie.put("secure", true);
    cookie.put("session", true);
    return cookie;
  }

  private static ChromeDriver cdpDriver() {
    return mock(ChromeDriver.class);
  }

  @Test
  @DisplayName("만료가 없는 세션 쿠키는 만료 필드를 떼고 넘긴다")
  void dropsNonPositiveExpirySoSessionCookiesSurvive() {
    // CDP 는 만료를 '주지 않으면' 세션 쿠키로 본다. -1 을 그대로 주면 1970년으로 읽어
    // 넣는 즉시 버릴 수 있다 — 우리가 옮기려는 인증 쿠키가 바로 이 종류다.
    Map<String, Object> clean =
        SessionTransfer.sanitize(List.of(sessionCookie("JSESSIONID"))).get(0);

    assertFalse(clean.containsKey("expires"), "만료 -1 을 그대로 넘겼다. 실제: " + clean.keySet());
    assertEquals("JSESSIONID", clean.get("name"));
    assertEquals(".ssg.com", clean.get("domain"));
    assertEquals(true, clean.get("httpOnly"));
    assertEquals(true, clean.get("secure"));
  }

  @Test
  @DisplayName("실제 만료가 있는 쿠키는 만료를 그대로 유지한다")
  void keepsRealExpiry() {
    Map<String, Object> raw = sessionCookie("keepId");
    raw.put("expires", 1893456000.0); // 2030-01-01

    Map<String, Object> clean = SessionTransfer.sanitize(List.of(raw)).get(0);

    assertEquals(1893456000.0, ((Number) clean.get("expires")).doubleValue());
  }

  @Test
  @DisplayName("뜨는 쪽만 주는 필드는 뗀다 — 그대로 되돌려 넣을 수 없다")
  void dropsFieldsThatSetCookiesDoesNotAccept() {
    Map<String, Object> clean = SessionTransfer.sanitize(List.of(sessionCookie("FSID"))).get(0);

    for (String field : SessionTransfer.NON_SETTABLE_FIELDS) {
      assertFalse(clean.containsKey(field), field + " 가 남았다. 실제: " + clean.keySet());
    }
  }

  @Test
  @DisplayName("캡처는 CDP 로 뜨고 다듬은 결과를 스냅샷에 담는다")
  @SuppressWarnings("unchecked")
  void captureReadsThroughCdp() {
    ChromeDriver driver = cdpDriver();
    when(driver.executeCdpCommand(eq("Network.getAllCookies"), any()))
        .thenReturn(
            Map.of("cookies", List.of(sessionCookie("LOGIN_YN"), sessionCookie("MEMBER_ID"))));

    SessionSnapshot snapshot = SessionTransfer.capture(driver);

    assertEquals(2, snapshot.size());
    assertTrue(snapshot.hasCookie("LOGIN_YN"));
    assertFalse(snapshot.cookies().get(0).containsKey("size"), "다듬지 않은 값이 스냅샷에 들어갔다.");
  }

  @Test
  @DisplayName("쿠키가 없으면 빈 스냅샷이다 — null 을 흘리지 않는다")
  void captureWithoutCookiesYieldsEmptySnapshot() {
    ChromeDriver driver = cdpDriver();
    when(driver.executeCdpCommand(anyString(), any())).thenReturn(Map.of("cookies", List.of()));

    assertTrue(SessionTransfer.capture(driver).isEmpty());
  }

  @Test
  @DisplayName("CDP 응답 형태가 다르면 '쿠키가 없다' 로 뭉개지 않는다")
  void captureFailsLoudlyOnUnexpectedShape() {
    // '응답 형태가 다르다' 를 '쿠키 0개' 로 보고하면 사람은 로그인·창을 다시 확인하는 데
    // 시간을 쓴다. 실제 원인은 붙은 대상이 살아 있는 브라우저가 아닌 것일 수 있다.
    ChromeDriver driver = cdpDriver();
    when(driver.executeCdpCommand(anyString(), any())).thenReturn(Map.of("result", "이상한값"));

    assertThrows(IllegalStateException.class, () -> SessionTransfer.capture(driver));
  }

  @Test
  @DisplayName("주입은 스냅샷을 CDP 로 넘기고, 들어갔는지 되읽어 센다")
  @SuppressWarnings("unchecked")
  void injectWritesThroughCdpAndVerifies() {
    ChromeDriver driver = cdpDriver();
    List<Map<String, Object>> clean =
        SessionTransfer.sanitize(List.of(sessionCookie("JSESSIONID")));
    SessionSnapshot snapshot = SessionSnapshot.of(clean, Instant.now());
    // 되읽기에서 그대로 보이면 반영된 것이다.
    when(driver.executeCdpCommand(eq("Network.getAllCookies"), any()))
        .thenReturn(Map.of("cookies", clean));

    assertEquals(1, SessionTransfer.inject(driver, snapshot));

    ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
    verify(driver).executeCdpCommand(eq("Network.setCookies"), params.capture());
    List<Map<String, Object>> sent = (List<Map<String, Object>>) params.getValue().get("cookies");
    assertEquals("JSESSIONID", sent.get(0).get("name"));
  }

  @Test
  @DisplayName("넣으라고 한 것이 안 들어갔으면 그 수를 돌려준다 — 요청 수를 성공으로 보고하지 않는다")
  void injectReportsWhatActuallyLanded() {
    // CDP setCookies 는 쿠키별 성패를 알려주지 않는다. 요청 수를 그대로 돌려주면
    // '12개 주입 완료' 뒤에 로그아웃 상태가 되는 실패를 아무도 잡지 못한다.
    ChromeDriver driver = cdpDriver();
    List<Map<String, Object>> clean =
        SessionTransfer.sanitize(List.of(sessionCookie("JSESSIONID"), sessionCookie("LOGIN_YN")));
    SessionSnapshot snapshot = SessionSnapshot.of(clean, Instant.now());
    when(driver.executeCdpCommand(eq("Network.getAllCookies"), any()))
        .thenReturn(Map.of("cookies", List.of(clean.get(0))));

    assertEquals(1, SessionTransfer.inject(driver, snapshot), "반영되지 않은 쿠키까지 성공으로 셌다.");
  }

  @Test
  @DisplayName("되읽기가 실패하면 요청 수를 보고하되 주입을 실패로 단정하지 않는다")
  void injectFallsBackWhenVerificationFails() {
    ChromeDriver driver = cdpDriver();
    List<Map<String, Object>> clean =
        SessionTransfer.sanitize(List.of(sessionCookie("JSESSIONID")));
    SessionSnapshot snapshot = SessionSnapshot.of(clean, Instant.now());
    when(driver.executeCdpCommand(eq("Network.getAllCookies"), any()))
        .thenThrow(new IllegalStateException("CDP 오류"));

    assertEquals(1, SessionTransfer.inject(driver, snapshot));
  }

  @Test
  @DisplayName("빈 스냅샷은 주입하지 않는다 — 넣은 것처럼 보이면 안 된다")
  void doesNotInjectEmptySnapshot() {
    // 빈 목록으로 CDP 를 불러도 성공으로 돌아온다. 그러면 '세션을 넣었는데 로그아웃 상태'라는
    // 가장 가리기 어려운 실패가 된다.
    ChromeDriver driver = cdpDriver();

    assertEquals(0, SessionTransfer.inject(driver, SessionSnapshot.empty()));
    assertEquals(0, SessionTransfer.inject(driver, null));

    verify(driver, never()).executeCdpCommand(anyString(), any());
  }

  @Test
  @DisplayName("CDP 를 지원하지 않는 드라이버면 조용히 넘기지 않는다")
  void rejectsNonCdpDriver() {
    WebDriver plain = mock(WebDriver.class);

    assertThrows(IllegalArgumentException.class, () -> SessionTransfer.capture(plain));
    assertThrows(
        IllegalArgumentException.class,
        () -> SessionTransfer.inject(plain, SessionSnapshot.empty()));
  }

  @Test
  @DisplayName("스냅샷을 문자열로 만들어도 토큰이 새지 않는다")
  void toStringNeverLeaksTokens() {
    // 로그·예외 메시지에 스냅샷이 그대로 실리는 일은 반드시 생긴다.
    // 그때 세션 토큰 전량이 파일에 남으면 안 된다.
    SessionSnapshot snapshot =
        SessionSnapshot.of(List.of(sessionCookie("JSESSIONID")), Instant.now());

    String text = snapshot.toString();

    assertFalse(text.contains("토큰값"), "쿠키 값이 문자열에 실렸다: " + text);
    assertTrue(text.contains("1"), "개수는 남아야 진단이 된다: " + text);
  }

  @Test
  @DisplayName("스냅샷은 원본과 분리된 복사본을 갖는다")
  void snapshotIsIndependentOfTheSourceList() {
    Map<String, Object> mutable = sessionCookie("JSESSIONID");
    List<Map<String, Object>> source = new java.util.ArrayList<>(List.of(mutable));

    SessionSnapshot snapshot = SessionSnapshot.of(source, Instant.now());
    source.clear();
    mutable.put("value", "바뀐값");

    assertEquals(1, snapshot.size(), "원본 목록을 비웠더니 스냅샷도 비었다.");
    assertEquals("토큰값", snapshot.cookies().get(0).get("value"), "원본 수정이 스냅샷에 새어 들어갔다.");
  }
}

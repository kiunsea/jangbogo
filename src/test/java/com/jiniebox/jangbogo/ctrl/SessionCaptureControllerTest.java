package com.jiniebox.jangbogo.ctrl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jiniebox.jangbogo.svc.SessionCaptureService;
import com.jiniebox.jangbogo.svc.SessionCaptureService.CaptureResult;
import com.jiniebox.jangbogo.svc.SessionCaptureService.Outcome;
import com.jiniebox.jangbogo.sys.SessionConstants;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 세션 캡처 컨트롤러의 결과 매핑 검증 (Phase 5-15).
 *
 * <p>브라우저·서비스 로직 없이 컨트롤러의 얇은 층만 잰다 — 결과 코드가 JSON 으로 옳게 옮겨지는지, 관리자 로그인 시각이 서비스로 넘어가는지, 쿠키 값이 응답에 새지
 * 않는지.
 *
 * @author KIUNSEA
 */
class SessionCaptureControllerTest {

  private static HttpSession sessionWithLoginTime(Long loginTime) {
    HttpSession session = mock(HttpSession.class);
    when(session.getAttribute(SessionConstants.SESSION_LOGIN_TIME_KEY)).thenReturn(loginTime);
    return session;
  }

  @Test
  @DisplayName("start 는 세션의 LOGIN_TIME 을 서비스로 넘긴다")
  void startPassesAdminLoginTime() {
    SessionCaptureService svc = mock(SessionCaptureService.class);
    when(svc.start(eq("1"), eq(12345L))).thenReturn(new CaptureResult(Outcome.STARTED, -1, -1));

    ObjectNode json = new SessionCaptureController(svc).start("1", sessionWithLoginTime(12345L));

    assertEquals("STARTED", json.get("outcome").asText());
    assertTrue(json.get("success").asBoolean());
  }

  @Test
  @DisplayName("막힘은 success=false 로 내려간다")
  void blockedIsNotSuccess() {
    SessionCaptureService svc = mock(SessionCaptureService.class);
    when(svc.start(eq("1"), org.mockito.ArgumentMatchers.anyLong()))
        .thenReturn(new CaptureResult(Outcome.KILLSWITCH_OFF, -1, -1));

    ObjectNode json = new SessionCaptureController(svc).start("1", sessionWithLoginTime(12345L));

    assertEquals("KILLSWITCH_OFF", json.get("outcome").asText());
    assertFalse(json.get("success").asBoolean());
  }

  @Test
  @DisplayName("capture 성공은 쿠키 개수를 담는다 (값이 아니라 개수만)")
  void captureIncludesCookieCount() {
    SessionCaptureService svc = mock(SessionCaptureService.class);
    when(svc.capture(eq("1"))).thenReturn(new CaptureResult(Outcome.CAPTURED, 7, -1));

    ObjectNode json = new SessionCaptureController(svc).capture("1");

    assertEquals("CAPTURED", json.get("outcome").asText());
    assertEquals(7, json.get("cookieCount").asInt());
    assertTrue(json.get("success").asBoolean());
  }

  @Test
  @DisplayName("status 는 잔여 시간을 담는다")
  void statusIncludesRemaining() {
    SessionCaptureService svc = mock(SessionCaptureService.class);
    when(svc.status(eq("1"))).thenReturn(new CaptureResult(Outcome.AWAITING_LOGIN, -1, 600000L));

    ObjectNode json = new SessionCaptureController(svc).status("1");

    assertEquals("AWAITING_LOGIN", json.get("outcome").asText());
    assertEquals(600000L, json.get("remainingMillis").asLong());
  }

  @Test
  @DisplayName("LOGIN_TIME 이 없어도 깨지지 않는다 — 없으면 지금으로 본다")
  void missingLoginTimeDoesNotBreak() {
    SessionCaptureService svc = mock(SessionCaptureService.class);
    when(svc.start(eq("1"), org.mockito.ArgumentMatchers.anyLong()))
        .thenReturn(new CaptureResult(Outcome.STARTED, -1, -1));

    // LOGIN_TIME 이 null 이어도 예외 없이 처리되어야 한다.
    ObjectNode json = new SessionCaptureController(svc).start("1", sessionWithLoginTime(null));

    assertEquals("STARTED", json.get("outcome").asText());
  }
}

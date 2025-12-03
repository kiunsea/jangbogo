package com.jiniebox.jangbogo.sys;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * 인증 인터셉터 (개선 버전)
 *
 * <p>- 전역 세션 검사 처리 - 로깅 강화 - 세션 타임아웃 처리 - 예외 경로 패턴 개선 - 뷰에 자동으로 사용자 정보 추가
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

  private static final Logger logger = LogManager.getLogger(AuthInterceptor.class);

  // 인증 제외 경로 목록 (화이트리스트)
  private static final List<String> EXCLUDE_PATHS =
      Arrays.asList(
          "/signin",
          "/api/login",
          "/api/session-check",
          "/error",
          "/actuator" // Spring Boot Actuator 엔드포인트
          );

  // 정적 리소스 경로 패턴
  private static final List<String> STATIC_PATTERNS =
      Arrays.asList("/css/", "/js/", "/images/", "/static/", "/favicon.ico");

  /** 컨트롤러 실행 전 세션 검사 */
  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {

    String requestURI = request.getRequestURI();
    String method = request.getMethod();

    logger.debug("세션 검사 - URI: {}, Method: {}", requestURI, method);

    // 1. 제외 경로 체크
    if (isExcludedPath(requestURI)) {
      logger.debug("제외 경로: {}", requestURI);
      return true;
    }

    // 2. 세션 검사
    HttpSession session = request.getSession(false);

    if (!isAuthenticated(session)) {
      logger.warn("인증 실패 - URI: {}, IP: {}", requestURI, getClientIP(request));
      handleUnauthorized(request, response, requestURI);
      return false;
    }

    // 3. 세션 활동 시간 갱신
    updateSessionActivity(session);

    // 4. 세션 정보 로깅
    logSessionInfo(session, requestURI);

    return true;
  }

  /** 컨트롤러 실행 후 (뷰 렌더링 전) 모든 뷰에 사용자 정보를 자동으로 추가 */
  @Override
  public void postHandle(
      HttpServletRequest request,
      HttpServletResponse response,
      Object handler,
      ModelAndView modelAndView)
      throws Exception {

    if (modelAndView != null) {
      // 모든 뷰에 사용자 정보 자동 추가
      HttpSession session = request.getSession(false);
      if (session != null) {
        String username = (String) session.getAttribute(SessionConstants.SESSION_USERNAME_KEY);
        modelAndView.addObject("currentUser", username);
        modelAndView.addObject("isAuthenticated", true);
      }
    }
  }

  /** 요청 완료 후 (뷰 렌더링 후) 예외 발생 시 로깅 */
  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
      throws Exception {

    if (ex != null) {
      logger.error("요청 처리 중 예외 발생 - URI: {}", request.getRequestURI(), ex);
    }
  }

  /** 제외 경로 체크 */
  private boolean isExcludedPath(String requestURI) {
    // 정적 리소스 체크
    for (String pattern : STATIC_PATTERNS) {
      if (requestURI.startsWith(pattern)) {
        return true;
      }
    }

    // 제외 경로 체크 (정확히 일치하거나 시작하는 경로)
    for (String excludePath : EXCLUDE_PATHS) {
      if (requestURI.equals(excludePath) || requestURI.startsWith(excludePath + "/")) {
        return true;
      }
    }

    return false;
  }

  /** 인증 여부 체크 세션 존재 여부와 타임아웃을 확인 */
  private boolean isAuthenticated(HttpSession session) {
    if (session == null) {
      return false;
    }

    Boolean isLoggedIn = (Boolean) session.getAttribute(SessionConstants.SESSION_ADMIN_KEY);

    // 세션 타임아웃 체크 (선택사항)
    if (isLoggedIn != null && isLoggedIn) {
      Long loginTime = (Long) session.getAttribute(SessionConstants.SESSION_LOGIN_TIME_KEY);
      if (loginTime != null) {
        long elapsedMinutes = (System.currentTimeMillis() - loginTime) / (1000 * 60);
        if (elapsedMinutes > SessionConstants.SESSION_TIMEOUT_MINUTES) {
          logger.info("세션 타임아웃 - 경과 시간: {}분", elapsedMinutes);
          return false;
        }
      }
    }

    return isLoggedIn != null && isLoggedIn;
  }

  /** 미인증 처리 API 요청과 페이지 요청을 구분하여 처리 */
  private void handleUnauthorized(
      HttpServletRequest request, HttpServletResponse response, String requestURI)
      throws IOException {

    // API 요청인 경우 JSON 응답
    if (isApiRequest(requestURI)) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setContentType("application/json");
      response.setCharacterEncoding("UTF-8");

      ObjectMapper objectMapper = new ObjectMapper();
      ObjectNode errorResponse = objectMapper.createObjectNode();
      errorResponse.put("success", false);
      errorResponse.put("code", 401);
      errorResponse.put("message", "로그인이 필요합니다.");
      errorResponse.put("redirectUrl", "/signin");

      response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
    // 페이지 요청인 경우 로그인 페이지로 리다이렉트
    else {
      String redirectUrl = "/signin";

      // 원래 요청 URL을 파라미터로 전달 (로그인 후 복귀용)
      if (!requestURI.equals("/")) {
        redirectUrl += "?redirect=" + requestURI;
      }

      response.sendRedirect(redirectUrl);
    }
  }

  /** API 요청 여부 체크 */
  private boolean isApiRequest(String requestURI) {
    return requestURI.startsWith("/api/")
        || requestURI.startsWith("/malls/")
        || requestURI.startsWith("/mall/");
  }

  /** 세션 활동 시간 갱신 */
  private void updateSessionActivity(HttpSession session) {
    session.setAttribute(SessionConstants.SESSION_LAST_ACTIVITY_KEY, System.currentTimeMillis());
  }

  /** 세션 정보 로깅 */
  private void logSessionInfo(HttpSession session, String requestURI) {
    if (logger.isDebugEnabled()) {
      String username = (String) session.getAttribute(SessionConstants.SESSION_USERNAME_KEY);
      logger.debug("인증 성공 - User: {}, URI: {}, Session: {}", username, requestURI, session.getId());
    }
  }

  /** 클라이언트 IP 가져오기 프록시 환경에서도 실제 클라이언트 IP를 가져옴 */
  private String getClientIP(HttpServletRequest request) {
    String ip = request.getHeader("X-Forwarded-For");
    if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
      ip = request.getHeader("Proxy-Client-IP");
    }
    if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
      ip = request.getHeader("WL-Proxy-Client-IP");
    }
    if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
      ip = request.getHeader("HTTP_X_FORWARDED_FOR");
    }
    if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
      ip = request.getRemoteAddr();
    }
    return ip;
  }
}

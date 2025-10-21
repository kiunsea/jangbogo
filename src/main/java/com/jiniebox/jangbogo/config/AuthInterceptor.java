package com.jiniebox.jangbogo.config;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * 인증 인터셉터
 * 보호된 페이지 접근 시 세션을 체크하여 로그인 여부를 확인
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String SESSION_ADMIN_KEY = "ADMIN_LOGGED_IN";
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) 
            throws Exception {
        
        String requestURI = request.getRequestURI();
        
        // 로그인 페이지, 로그인 API, 정적 리소스는 인터셉터 제외
        if (requestURI.startsWith("/signin") || 
            requestURI.startsWith("/api/login") || 
            requestURI.startsWith("/api/session-check") ||
            requestURI.startsWith("/welcome") ||
            requestURI.startsWith("/css/") || 
            requestURI.startsWith("/js/") || 
            requestURI.startsWith("/images/") ||
            requestURI.startsWith("/static/")) {
            return true;
        }
        
        HttpSession session = request.getSession(false);
        
        // 세션이 없거나 로그인되지 않은 경우
        if (session == null || session.getAttribute(SESSION_ADMIN_KEY) == null) {
            
            // API 요청인 경우 401 에러 반환
            if (requestURI.startsWith("/api/")) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"success\":false,\"message\":\"로그인이 필요합니다.\",\"code\":401}");
                return false;
            }
            
            // 일반 페이지 요청인 경우 로그인 페이지로 리다이렉트
            response.sendRedirect("/signin?redirect=" + requestURI);
            return false;
        }
        
        // 로그인되어 있으면 계속 진행
        return true;
    }
}


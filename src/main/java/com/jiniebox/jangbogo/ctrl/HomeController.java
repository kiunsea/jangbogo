package com.jiniebox.jangbogo.ctrl;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import com.jiniebox.jangbogo.sys.SessionConstants;

import jakarta.servlet.http.HttpSession;

/**
 * 홈 컨트롤러 (개선 버전)
 * 
 * - SessionConstants를 사용하여 세션 키 중앙 관리
 * - Interceptor에서 전역 세션 검사를 수행하므로 index()의 세션 체크는 제거 가능
 * - signin()은 Interceptor 제외 경로이므로 내부 체크 유지
 */
@Controller
public class HomeController {

    /**
     * 메인 페이지
     * Interceptor에서 이미 세션 체크를 수행하므로 여기까지 도달했다면 인증된 상태
     */
    @GetMapping("/")
    public String index(HttpSession session) {
        // ✅ 개선: Interceptor가 세션 체크를 수행하므로 중복 제거
        // 여기까지 도달했다면 이미 로그인된 상태
        
        return "index";
    }
    
    /**
     * 로그인 페이지
     * Interceptor 제외 경로이므로 내부에서 세션 체크 수행
     */
    @GetMapping("/signin")
    public String signin(HttpSession session) {
        // 세션 체크 (AuthInterceptor와 동일한 로직 사용)
        if (session != null) {
            Boolean isLoggedIn = (Boolean) session.getAttribute(SessionConstants.SESSION_ADMIN_KEY);
            
            // 세션 유효성 검사 (타임아웃 체크 포함)
            if (isLoggedIn != null && isLoggedIn) {
                // 세션 타임아웃 체크
                Long loginTime = (Long) session.getAttribute(SessionConstants.SESSION_LOGIN_TIME_KEY);
                if (loginTime != null) {
                    long elapsedMinutes = (System.currentTimeMillis() - loginTime) / (1000 * 60);
                    if (elapsedMinutes <= SessionConstants.SESSION_TIMEOUT_MINUTES) {
                        // 유효한 세션이면 메인 페이지로 리다이렉트
                        return "redirect:/";
                    } else {
                        // 세션 타임아웃 - 세션 무효화
                        session.invalidate();
                    }
                } else {
                    // loginTime이 없어도 로그인 상태면 메인으로 이동 (하위 호환성)
                    return "redirect:/";
                }
            }
        }
        
        // 로그인되지 않았거나 세션이 만료된 경우 로그인 페이지 표시
        return "signin";
    }
    
}
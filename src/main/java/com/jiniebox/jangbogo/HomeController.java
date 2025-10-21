package com.jiniebox.jangbogo;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpSession;

@Controller
public class HomeController {

    private static final String SESSION_ADMIN_KEY = "ADMIN_LOGGED_IN";

    /**
     * 메인 페이지
     * 로그인 확인 후 접근 가능
     */
    @GetMapping("/")
    public String index(HttpSession session) {
        // 세션 체크
        Boolean isLoggedIn = (Boolean) session.getAttribute(SESSION_ADMIN_KEY);
        
        if (isLoggedIn == null || !isLoggedIn) {
            // 로그인되지 않았으면 로그인 페이지로 리다이렉트
            return "redirect:/signin";
        }
        
        // 로그인되어 있으면 인덱스 페이지 표시
        return "index";
    }
    
    /**
     * 로그인 페이지
     */
    @GetMapping("/signin")
    public String signin(HttpSession session) {
        // 이미 로그인되어 있으면 메인 페이지로 리다이렉트
        Boolean isLoggedIn = (Boolean) session.getAttribute(SESSION_ADMIN_KEY);
        
        if (isLoggedIn != null && isLoggedIn) {
            return "redirect:/";
        }
        
        return "signin";
    }
    
}
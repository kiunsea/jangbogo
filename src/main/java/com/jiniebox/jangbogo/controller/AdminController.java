package com.jiniebox.jangbogo.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.servlet.http.HttpSession;

@Controller
public class AdminController {
    
    @Value("${admin.id}")
    private String adminId;
    
    @Value("${admin.pass}")
    private String adminPass;
    
    private static final String SESSION_ADMIN_KEY = "ADMIN_LOGGED_IN";
    private static final String SESSION_USERNAME_KEY = "ADMIN_USERNAME";
    
    /**
     * 로그인 API
     * POST /api/login
     */
    @PostMapping("/api/login")
    @ResponseBody
    public JsonNode login(@RequestBody LoginRequest loginRequest, HttpSession session) {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode response = objectMapper.createObjectNode();
        
        try {
            String inputId = loginRequest.getUsername();
            String inputPass = loginRequest.getPassword();
            
            // 입력값 검증
            if (inputId == null || inputId.trim().isEmpty() || 
                inputPass == null || inputPass.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "아이디와 비밀번호를 입력해주세요.");
                return response;
            }
            
            // 로그인 검증
            if (adminId.equals(inputId) && adminPass.equals(inputPass)) {
                // 세션에 로그인 정보 저장
                session.setAttribute(SESSION_ADMIN_KEY, true);
                session.setAttribute(SESSION_USERNAME_KEY, inputId);
                
                response.put("success", true);
                response.put("message", "로그인 성공");
                response.put("username", inputId);
                
                System.out.println("✓ 로그인 성공: " + inputId + " (Session ID: " + session.getId() + ")");
            } else {
                response.put("success", false);
                response.put("message", "아이디 또는 비밀번호가 일치하지 않습니다.");
                
                System.out.println("✗ 로그인 실패: " + inputId);
            }
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "로그인 처리 중 오류가 발생했습니다.");
            e.printStackTrace();
        }
        
        return response;
    }
    
    /**
     * 로그아웃 API
     * POST /api/logout
     */
    @PostMapping("/api/logout")
    @ResponseBody
    public JsonNode logout(HttpSession session) {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode response = objectMapper.createObjectNode();
        
        try {
            String username = (String) session.getAttribute(SESSION_USERNAME_KEY);
            
            // 세션 무효화
            session.invalidate();
            
            response.put("success", true);
            response.put("message", "로그아웃되었습니다.");
            
            System.out.println("✓ 로그아웃 성공: " + username);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "로그아웃 처리 중 오류가 발생했습니다.");
            e.printStackTrace();
        }
        
        return response;
    }
    
    /**
     * 세션 체크 API
     * GET /api/session-check
     */
    @GetMapping("/api/session-check")
    @ResponseBody
    public JsonNode checkSession(HttpSession session) {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode response = objectMapper.createObjectNode();
        
        Boolean isLoggedIn = (Boolean) session.getAttribute(SESSION_ADMIN_KEY);
        String username = (String) session.getAttribute(SESSION_USERNAME_KEY);
        
        if (isLoggedIn != null && isLoggedIn) {
            response.put("authenticated", true);
            response.put("username", username);
        } else {
            response.put("authenticated", false);
        }
        
        return response;
    }
    
    /**
     * 보호된 API 예제 - 세션 체크 필요
     * GET /api/getinfo
     */
    @GetMapping("/api/getinfo")
    @ResponseBody
    public JsonNode getInfo(HttpSession session) {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode node = objectMapper.createObjectNode();
        
        // 세션 체크
        Boolean isLoggedIn = (Boolean) session.getAttribute(SESSION_ADMIN_KEY);
        if (isLoggedIn == null || !isLoggedIn) {
            node.put("success", false);
            node.put("code", 401);
            node.put("message", "로그인이 필요합니다.");
            return node;
        }
        
        String username = (String) session.getAttribute(SESSION_USERNAME_KEY);
        
        System.out.println("Admin id: " + adminId);
        System.out.println("Current user: " + username);
        
        node.put("success", true);
        node.put("message", "장보고 프로젝트");
        node.put("code", 200);
        node.put("currentUser", username);
        
        // 중첩 객체 추가
        ObjectNode userData = objectMapper.createObjectNode();
        userData.put("name", "홍길동");
        userData.put("level", 5);
        node.set("user", userData);
        
        return node;
    }
    
    /**
     * 로그인 요청 DTO
     */
    public static class LoginRequest {
        private String username;
        private String password;
        
        public String getUsername() {
            return username;
        }
        
        public void setUsername(String username) {
            this.username = username;
        }
        
        public String getPassword() {
            return password;
        }
        
        public void setPassword(String password) {
            this.password = password;
        }
    }

}
package com.jiniebox.jangbogo.ctrl;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jiniebox.jangbogo.dao.JbgMallDataAccessObject;
import com.jiniebox.jangbogo.dto.JangbogoConfig;
import com.jiniebox.jangbogo.svc.JangBoGoManager;
import com.jiniebox.jangbogo.sys.EnvSYS;
import com.jiniebox.jangbogo.sys.SessionConstants;

import jakarta.servlet.http.HttpSession;

/**
 * 관리자 컨트롤러 (개선 버전)
 * 
 * - SessionConstants를 사용하여 세션 키 중앙 관리
 * - Interceptor에서 전역 세션 검사를 수행하므로 개별 메서드의 중복 체크 제거
 * - 로그인/로그아웃/세션 체크 API 제공
 */
@Controller
public class AdminController {
    
    private static final Logger logger = LogManager.getLogger(AdminController.class);
    
    @Value("${admin.id}")
    private String adminId;
    
    @Value("${admin.pass}")
    private String adminPass;
    
    @Autowired
    private JangbogoConfig jangbogoConfig;
    
    @Autowired
    private JangBoGoManager jangBoGoManager;
    
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
                // 세션에 로그인 정보 저장 (SessionConstants 사용)
                session.setAttribute(SessionConstants.SESSION_ADMIN_KEY, true);
                session.setAttribute(SessionConstants.SESSION_USERNAME_KEY, inputId);
                session.setAttribute(SessionConstants.SESSION_LOGIN_TIME_KEY, System.currentTimeMillis());
                
                // 세션 타임아웃 설정
                session.setMaxInactiveInterval(SessionConstants.SESSION_TIMEOUT_MINUTES * 60);
                
                response.put("success", true);
                response.put("message", "로그인 성공");
                response.put("username", inputId);
                
                logger.info("로그인 성공: {} (Session ID: {})", inputId, session.getId());
            } else {
                response.put("success", false);
                response.put("message", "아이디 또는 비밀번호가 일치하지 않습니다.");
                
                logger.warn("로그인 실패: {}", inputId);
            }
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "로그인 처리 중 오류가 발생했습니다.");
            logger.error("로그인 처리 오류", e);
        }
        
        return response;
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
            String username = (String) session.getAttribute(SessionConstants.SESSION_USERNAME_KEY);
            
            // 세션 무효화
            session.invalidate();
            
            response.put("success", true);
            response.put("message", "로그아웃되었습니다.");
            
            logger.info("로그아웃 성공: {}", username);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "로그아웃 처리 중 오류가 발생했습니다.");
            logger.error("로그아웃 처리 오류", e);
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
        
        Boolean isLoggedIn = (Boolean) session.getAttribute(SessionConstants.SESSION_ADMIN_KEY);
        String username = (String) session.getAttribute(SessionConstants.SESSION_USERNAME_KEY);
        
        if (isLoggedIn != null && isLoggedIn) {
            response.put("authenticated", true);
            response.put("username", username);
        } else {
            response.put("authenticated", false);
        }
        
        return response;
    }
    
    /**
     * 쇼핑몰 정보 목록
     * @throws Exception 
     */
    @GetMapping("/malls/getinfo")
    @ResponseBody
    public JsonNode getInfo(HttpSession session) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode node = objectMapper.createObjectNode();
        
        // ✅ 개선: 중복 세션 체크 제거 (Interceptor가 처리)
        // 세션이 여기까지 도달했다면 이미 인증된 상태
        
        String username = (String) session.getAttribute(SessionConstants.SESSION_USERNAME_KEY);
        
        logger.debug("사용자 정보 조회 - Admin ID: {}, Current User: {}", adminId, username);
        
        node.put("success", true);
        node.put("message", "장보고 프로젝트");
        node.put("code", 200);
        node.put("currentUser", username);
        
        JbgMallDataAccessObject jaDao = new JbgMallDataAccessObject();
        List<JSONObject> malls = jaDao.getAllMalls();
        JangBoGoManager.addCippKeys(malls); // browser local storage 에서 조회하기 위한 id key 와 pw key 를 설정
        
        node.put("malls", malls.toString());
        

        
        //TODO 이후는 테스트 코드로 삭제 예정
        
        // 사용자 정의 설정 값 가져오기 예제
        // 방법 1: 직접 접근
        node.put("localdbName", jangbogoConfig.getLocaldbName());
        node.put("localdbPath", jangbogoConfig.getLocaldbPath());
        
        // 방법 2: get() 메서드 사용 (PropertiesUtil.get("LOCALDB_NAME") 스타일)
        String dbName = jangbogoConfig.get("LOCALDB_NAME");
        String dbPath = jangbogoConfig.get("LOCALDB_PATH");
        String appVersion = jangbogoConfig.get("APP_VERSION");
        
        logger.info("설정 값 조회 - LOCALDB_NAME: {}, LOCALDB_PATH: {}, APP_VERSION: {}", 
                    dbName, dbPath, appVersion);
        
        // 설정 정보를 JSON에 추가
        ObjectNode configNode = objectMapper.createObjectNode();
        configNode.put("localdbName", dbName);
        configNode.put("localdbPath", dbPath);
        configNode.put("maxRetryCount", jangbogoConfig.getMaxRetryCount());
        configNode.put("timeoutSeconds", jangbogoConfig.getTimeoutSeconds());
        configNode.put("debugMode", jangbogoConfig.isDebugMode());
        configNode.put("appVersion", appVersion);
        node.set("config", configNode);
        
        // 중첩 객체 추가
        ObjectNode userData = objectMapper.createObjectNode();
        userData.put("name", "홍길동");
        userData.put("level", 5);
        node.set("user", userData);
        
        return node;
    }
    
    // DTO 정의 (이전 답변과 동일)
    public static class MallConnectionRequest {
        private String id;
        private String pass;
        
        public void setId(String id) { this.id = id; }
        public void setPass(String pass) { this.pass = pass; }
        public String getId() { return id; }
        public String getPass() { return pass; }
    }
    
    /**
     * 쇼핑몰 계정 연결
     * POST /mall/connect
     * 
     * @param seqMall 쇼핑몰 시퀀스
     * @param statusTo 요청 상태 (0: 연결해제, 1: 연결)
     * @param usrid 사용자 ID (연결 시 필수)
     * @param usrpass 사용자 비밀번호 (연결 시 필수)
     * @return JSON 응답
     * @throws Exception 
     */
    @PostMapping("/mall/connect")
    @ResponseBody
    public JsonNode connectToMall(
            @RequestParam("seq") String seqMall,
            @RequestParam("statusTo") String statusTo,
            @RequestParam(value = "usrid", required = false, defaultValue = "") String usrid,
            @RequestParam(value = "usrpass", required = false, defaultValue = "") String usrpass,
            HttpSession session) throws Exception {
        
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode response = objectMapper.createObjectNode();
        
        String resCode = EnvSYS.RESCODE_FAIL;
        String resMsg = EnvSYS.RESMSG_FAIL;
        
        try {
            // 파라미터 로깅
            logger.info("쇼핑몰 연결 요청 - seq: {}, statusTo: {}, usrid: {}", 
                       seqMall, statusTo, (usrid != null && !usrid.isEmpty() ? "***" : "empty"));
            
            String username = (String) session.getAttribute(SessionConstants.SESSION_USERNAME_KEY);
            
            // 연결 해제 처리
            if ("0".equals(statusTo)) {
                JbgMallDataAccessObject jaDao = new JbgMallDataAccessObject();
                jaDao.setAccountStatus(seqMall, 0);
                
                response.put("success", true);
                response.put("code", "000");
                response.put("message", "연결을 해제하였습니다.");
                response.put("status", 0);
                
                logger.info("쇼핑몰 연결 해제 완료 - seq: {}, user: {}", seqMall, username);
            }
            // 연결 처리
            else if ("1".equals(statusTo)) {
                // 입력값 검증
                if (usrid == null || usrid.trim().isEmpty() || 
                    usrpass == null || usrpass.trim().isEmpty()) {
                    response.put("success", false);
                    response.put("code", "001");
                    response.put("message", "아이디와 비밀번호를 입력해주세요.");
                    return response;
                }
                
                int resultConn = jangBoGoManager.connectToMall(seqMall, usrid, usrpass);
                logger.info("쇼핑몰 연결 결과 - seq: {}, user: {} = "+resultConn);
                if (resultConn == 1) {
                    /**
                     * 연결테스트에 성공한 경우
                     */
                    //TODO 나머지 처리
                    // 1. 암호화 키 생성
                    // 2. ID/Pass 암호화
                    // 3. File 과 DB 저장
                } else if (resultConn == 0) {
                    // 연결테스트에 실패한 경우
                    response.put("status", 2);
                    resCode = EnvSYS.RESCODE_FAIL;
                    resMsg = "연결에 실패하였습니다.";
                } else if (resultConn == 2) {
                    response.put("status", 0);
                    resCode = EnvSYS.RESCODE_FAIL;
                    resMsg = "마지막 시도 이후 일정 시간이 경과되어야만 합니다.(30분 이상)";
                }
                
            }
            else {
                response.put("success", false);
                resCode = "002";
                resMsg = "잘못된 요청입니다.";
            }
            
        } catch (Exception e) {
            response.put("success", false);
            resCode = "999";
            resMsg = "처리 실패: " + e.getMessage();
            logger.error("쇼핑몰 연결 처리 오류", e);
        }
        
        response.put("code", resCode);
        response.put("msg", resMsg);
        
        return response;
    }

}
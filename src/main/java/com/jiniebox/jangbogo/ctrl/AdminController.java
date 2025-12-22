package com.jiniebox.jangbogo.ctrl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jiniebox.jangbogo.dao.JbgMallDataAccessObject;
import com.jiniebox.jangbogo.dto.JangbogoConfig;
import com.jiniebox.jangbogo.svc.JangBoGoManager;
import com.jiniebox.jangbogo.svc.MallAccountYmlService;
import com.jiniebox.jangbogo.sys.EnvSYS;
import com.jiniebox.jangbogo.sys.SessionConstants;
import com.jiniebox.jangbogo.util.StringEncrypter;
import jakarta.servlet.http.HttpSession;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.crypto.BadPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 관리자 컨트롤러 (개선 버전)
 *
 * <p>- SessionConstants를 사용하여 세션 키 중앙 관리 - Interceptor에서 전역 세션 검사를 수행하므로 개별 메서드의 중복 체크 제거 -
 * 로그인/로그아웃/세션 체크 API 제공
 */
@Controller
public class AdminController {

  private static final Logger logger = LogManager.getLogger(AdminController.class);

  @Autowired private com.jiniebox.jangbogo.svc.AdminCredentialService adminCredentialService;

  @Autowired private JangbogoConfig jangbogoConfig;

  @Autowired private JangBoGoManager jangBoGoManager;

  @Autowired private MallAccountYmlService mallAccountYmlService;

  @Autowired private com.jiniebox.jangbogo.svc.MallSchedulerService mallSchedulerService;

  @Autowired private com.jiniebox.jangbogo.svc.ExportService exportService;

  /** 로그인 API POST /api/login */
  @PostMapping("/api/login")
  @ResponseBody
  public JsonNode login(@RequestBody LoginRequest loginRequest, HttpSession session) {
    ObjectMapper objectMapper = new ObjectMapper();
    ObjectNode response = objectMapper.createObjectNode();

    try {
      String inputId = loginRequest.getUsername();
      String inputPass = loginRequest.getPassword();

      // 입력값 검증
      if (inputId == null
          || inputId.trim().isEmpty()
          || inputPass == null
          || inputPass.trim().isEmpty()) {
        response.put("success", false);
        response.put("message", "아이디와 비밀번호를 입력해주세요.");
        return response;
      }

      // 로그인 검증
      if (adminCredentialService.matches(inputId, inputPass)) {
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

  /** 관리자 프로필 정보 조회 */
  @GetMapping("/api/admin/profile")
  @ResponseBody
  public JsonNode getAdminProfile() {
    ObjectMapper objectMapper = new ObjectMapper();
    ObjectNode response = objectMapper.createObjectNode();
    try {
      com.jiniebox.jangbogo.svc.AdminCredentialService.AdminCredentials credentials =
          adminCredentialService.getCredentials();
      response.put("success", true);
      response.put("adminId", credentials.getAdminId());
      response.put("hasPassword", credentials.hasPassword());
      response.put("configPath", adminCredentialService.getConfigFilePath());
      response.put("configSource", adminCredentialService.getEffectiveSource());
    } catch (Exception e) {
      logger.error("관리자 프로필 정보 조회 실패", e);
      response.put("success", false);
      response.put("message", "관리자 정보를 조회할 수 없습니다.");
    }
    return response;
  }

  /** 관리자 프로필 업데이트 */
  @PostMapping("/api/admin/profile")
  @ResponseBody
  public JsonNode updateAdminProfile(@RequestBody AdminProfileRequest request) {
    ObjectMapper objectMapper = new ObjectMapper();
    ObjectNode response = objectMapper.createObjectNode();

    String newAdminId = request != null ? request.getAdminId() : null;
    String newAdminPass = request != null ? request.getAdminPass() : null;

    if (newAdminId == null || newAdminId.trim().isEmpty()) {
      response.put("success", false);
      response.put("message", "관리자 아이디를 입력해주세요.");
      return response;
    }

    String normalizedPass = newAdminPass != null ? newAdminPass.trim() : "";
    boolean updatePassword = !normalizedPass.isEmpty();

    try {
      adminCredentialService.updateCredentials(
          newAdminId.trim(), updatePassword ? normalizedPass : null, updatePassword);

      response.put("success", true);
      response.put("message", "관리자 정보가 저장되었습니다.");
      response.put("configPath", adminCredentialService.getConfigFilePath());
      response.put("configSource", adminCredentialService.getEffectiveSource());
    } catch (IllegalArgumentException e) {
      response.put("success", false);
      response.put("message", e.getMessage());
    } catch (IOException e) {
      logger.error("관리자 정보 저장 실패", e);
      response.put("success", false);
      response.put("message", "관리자 정보를 저장하지 못했습니다: " + e.getMessage());
    }
    return response;
  }

  /** 로그인 요청 DTO */
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

  /** 관리자 프로필 요청 DTO */
  public static class AdminProfileRequest {
    private String adminId;
    private String adminPass;

    public String getAdminId() {
      return adminId;
    }

    public void setAdminId(String adminId) {
      this.adminId = adminId;
    }

    public String getAdminPass() {
      return adminPass;
    }

    public void setAdminPass(String adminPass) {
      this.adminPass = adminPass;
    }
  }

  /** 로그아웃 API POST /api/logout */
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

  /** 세션 체크 API GET /api/session-check */
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
   *
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

    logger.debug(
        "사용자 정보 조회 - Admin ID: {}, Current User: {}",
        adminCredentialService.getCredentials().getAdminId(),
        username);

    node.put("success", true);
    node.put("message", "장보고 프로젝트");
    node.put("code", 200);
    node.put("currentUser", username);

    // Windows 사용자 내문서 경로 제공
    String userHome = System.getProperty("user.home");
    String documentsPath = userHome + "\\Documents\\jangbogo_exports";
    try {
      File defaultDir = new File(documentsPath);
      if (!defaultDir.exists()) {
        if (defaultDir.mkdirs()) {
          logger.info("기본 내보내기 폴더 생성: {}", documentsPath);
        } else {
          logger.warn("기본 내보내기 폴더를 생성하지 못했습니다: {}", documentsPath);
        }
      }
    } catch (SecurityException se) {
      logger.warn("기본 내보내기 폴더 생성 중 권한 오류: {}", se.getMessage());
    }
    node.put("defaultExportPath", documentsPath);

    // 파일 저장 설정 조회
    try {
      com.jiniebox.jangbogo.dao.JbgExportConfigDataAccessObject exportConfigDao =
          new com.jiniebox.jangbogo.dao.JbgExportConfigDataAccessObject();
      JSONObject exportConfig = exportConfigDao.getConfig();
      node.set("exportConfig", objectMapper.valueToTree(exportConfig));
    } catch (Exception e) {
      logger.warn("파일 저장 설정 조회 실패: {}", e.getMessage());
    }

    JbgMallDataAccessObject jaDao = new JbgMallDataAccessObject();
    List<JSONObject> malls = jaDao.getAllMalls(true);

    // malls 후처리: encrypt_key/encrypt_iv로 mall_account.yml의 cipher id 복호화 → mall.usrid 추가, 키 값은 제거
    try {
      for (JSONObject mall : malls) {
        // enc key/iv 추출
        String encKeyBase64 =
            mall.get("encrypt_key") != null ? mall.get("encrypt_key").toString() : "";
        String encIvBase64 =
            mall.get("encrypt_iv") != null ? mall.get("encrypt_iv").toString() : "";

        if (encKeyBase64.isEmpty() || encIvBase64.isEmpty()) {
          // 키가 없으면 스킵하고 키 필드는 제거만 수행
          mall.remove("encrypt_key");
          mall.remove("encrypt_iv");
          continue;
        }

        // seq → 문자열로 변환하여 YAML에서 조회
        String seqStr = String.valueOf(mall.get("seq"));
        java.util.Optional<com.jiniebox.jangbogo.dto.MallAccount> accOpt =
            mallAccountYmlService.getAccountBySeq(seqStr);
        if (accOpt.isEmpty()) {
          // 계정 정보가 없으면 키 필드 제거만 수행
          mall.remove("encrypt_key");
          mall.remove("encrypt_iv");
          continue;
        }

        String cipherUsrId = accOpt.get().getId();
        if (cipherUsrId == null || cipherUsrId.isEmpty()) {
          mall.remove("encrypt_key");
          mall.remove("encrypt_iv");
          continue;
        }

        // base64 → Key/IV 복원 후 복호화
        javax.crypto.SecretKey secKey = StringEncrypter.decodeBase64ToSecretKey(encKeyBase64);
        javax.crypto.spec.IvParameterSpec ivSpec = StringEncrypter.decodeBase64ToIv(encIvBase64);
        String decrypted =
            StringEncrypter.decrypt(StringEncrypter.ALGORITHM, cipherUsrId, secKey, ivSpec);
        if (decrypted != null && decrypted.startsWith("%")) {
          decrypted = decrypted.substring(1);
        }

        // mall에 usrid 저장, 키 필드는 제거
        mall.put("usrid", decrypted);
        mall.remove("encrypt_key");
        mall.remove("encrypt_iv");
      }
    } catch (Exception e) {
      logger.warn("malls 복호화 후처리 중 오류: {}", e.getMessage());
    }

    node.set("malls", objectMapper.valueToTree(malls));

    return node;
  }

  // DTO 정의 (이전 답변과 동일)
  public static class MallConnectionRequest {
    private String id;
    private String pass;

    public void setId(String id) {
      this.id = id;
    }

    public void setPass(String pass) {
      this.pass = pass;
    }

    public String getId() {
      return id;
    }

    public String getPass() {
      return pass;
    }
  }

  /**
   * 쇼핑몰 계정 연결 POST /mall/connect
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
      HttpSession session)
      throws Exception {

    ObjectMapper objectMapper = new ObjectMapper();
    ObjectNode response = objectMapper.createObjectNode();

    String resCode = EnvSYS.RESCODE_FAIL;
    String resMsg = EnvSYS.RESMSG_FAIL;

    try {
      // 파라미터 로깅
      logger.info(
          "쇼핑몰 연결 요청 - seq: {}, statusTo: {}, usrid: {}",
          seqMall,
          statusTo,
          (usrid != null && !usrid.isEmpty() ? "***" : "empty"));

      JbgMallDataAccessObject jaDao = new JbgMallDataAccessObject();
      String username = (String) session.getAttribute(SessionConstants.SESSION_USERNAME_KEY);

      // 연결 해제 처리
      if ("0".equals(statusTo)) {
        jaDao.setAccountStatus(seqMall, 0);

        response.put("success", true);
        resCode = EnvSYS.RESCODE_SUCC;
        resMsg = "연결을 해제하였습니다.";
        response.put("status", 0);

        logger.info("쇼핑몰 연결 해제 완료 - seq: {}, user: {}", seqMall, username);
      }
      // 연결 처리
      else if ("1".equals(statusTo)) {
        // 입력값 검증
        if (usrid == null
            || usrid.trim().isEmpty()
            || usrpass == null
            || usrpass.trim().isEmpty()) {
          response.put("success", false);
          response.put("code", "001");
          response.put("message", "아이디와 비밀번호를 입력해주세요.");
          return response;
        }

        int resultConn = jangBoGoManager.connectToMall(seqMall, usrid, usrpass);
        logger.info("쇼핑몰 연결 결과 - seq: {}, user: {} = " + resultConn);
        if (resultConn == 1) {
          /** 연결테스트에 성공한 경우 */
          SecretKey key = StringEncrypter.generateKey(256);
          IvParameterSpec iv = StringEncrypter.generateIv();

          // 생성한 encrypt 키들을 base64로 변환하여 db에 저장 (갱신)
          String keyBase64 = StringEncrypter.encodeSecretKeyToBase64(key);
          String ivBase64 = StringEncrypter.encodeIvToBase64(iv);
          jaDao.update(seqMall, 1, keyBase64, ivBase64);

          // 암호화된 계정 정보 생성
          String cipherUsrId =
              StringEncrypter.encrypt(StringEncrypter.ALGORITHM, "%" + usrid, key, iv);
          String cipherUsrPw =
              StringEncrypter.encrypt(StringEncrypter.ALGORITHM, "%" + usrpass, key, iv);

          // mall_account.yml에 계정 정보 저장
          try {
            // seqMall로부터 쇼핑몰 정보 조회 (site ID 획득)
            JSONObject mallInfo = jaDao.getMall(seqMall);

            if (mallInfo == null) {
              logger.error("쇼핑몰 정보를 찾을 수 없습니다: seq={}", seqMall);
              response.put("success", false);
              response.put("code", "003");
              response.put("message", "쇼핑몰 정보를 찾을 수 없습니다.");
              response.put("status", 0);
              return response;
            }

            // 쇼핑몰 ID 추출 (mall_account.yml의 site 필드로 사용)
            String mallSite =
                mallInfo.get("id").toString(); // jbg_mall.id 컬럼 값 (예: "coupang", "gmarket")

            logger.debug("쇼핑몰 정보 조회 완료 - seq: {}, site: {}", seqMall, mallSite);

            // mall_account.yml에 계정 정보 저장
            //    - seq: 쇼핑몰 시퀀스 (seqMall)
            //    - site: 쇼핑몰 ID (mallSite)
            //    - id: 암호화된 사용자 ID (cipherUsrId)
            //    - pass: 암호화된 사용자 비밀번호 (cipherUsrPw)
            mallAccountYmlService.saveAccountBySeq(
                seqMall, // seq: 쇼핑몰 시퀀스 번호
                mallSite, // site: 쇼핑몰 ID
                cipherUsrId, // id: 암호화된 사용자 ID
                cipherUsrPw // pass: 암호화된 사용자 비밀번호
                );

            logger.info("mall_account.yml에 계정 정보 저장 완료 - seq: {}, site: {}", seqMall, mallSite);

          } catch (IOException e) {
            // YAML 파일 저장 실패
            logger.error("mall_account.yml 저장 실패 - seqMall: {}", seqMall, e);
            // DB 업데이트는 성공했지만 YAML 저장 실패 - 경고만 남기고 계속 진행
            logger.warn("계정 정보 저장 중 오류가 발생했지만 연결은 완료되었습니다: {}", e.getMessage());
          } catch (Exception e) {
            // 쇼핑몰 정보 조회 실패 등 기타 오류
            logger.error("쇼핑몰 계정 정보 저장 처리 중 오류 발생 - seqMall: {}", seqMall, e);
            // DB 업데이트는 성공했지만 후처리 실패 - 경고만 남기고 계속 진행
            logger.warn("계정 정보 저장 처리 중 오류가 발생했지만 연결은 완료되었습니다: {}", e.getMessage());
          }

          // 저장 성공 응답
          response.put("success", true);
          resCode = EnvSYS.RESCODE_SUCC;
          resMsg = "연결 완료 및 계정 정보 저장 완료";
          response.put("status", 1);

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

      } else {
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

  /**
   * 선택한 쇼핑몰들에 대한 자동 수집 실행 - executeNow=true: 즉시 실행 후 주기적 스케줄링 시작 - executeNow=false: 주기적 스케줄링만 시작
   * (즉시 실행 안 함) - 이미 수집 작업 실행 중인 쇼핑몰은 스킵 POST /malls/auto-collect
   */
  @PostMapping("/malls/auto-collect")
  @ResponseBody
  public JsonNode autoCollectSelected(@RequestBody AutoCollectRequest req) {
    ObjectMapper objectMapper = new ObjectMapper();
    ObjectNode response = objectMapper.createObjectNode();

    List<String> processed = new ArrayList<>();
    List<String> scheduled = new ArrayList<>();
    List<String> skipped = new ArrayList<>();
    List<String> decryptionFailed = new ArrayList<>();

    // 모든 쇼핑몰에서 신규 추가된 주문 seq 수집
    List<Integer> allNewOrderSeqs = new ArrayList<>();

    try {
      if (req == null || req.getSeqs() == null || req.getSeqs().isEmpty()) {
        response.put("success", false);
        response.put("message", "선택된 쇼핑몰이 없습니다.");
        return response;
      }

      // 즉시 실행 여부 확인 (기본값: false)
      boolean executeNow = req.getExecuteNow() != null && req.getExecuteNow();
      logger.info("자동수집 요청 - 선택된 쇼핑몰: {}, 즉시 실행: {}", req.getSeqs().size(), executeNow);

      long sleepMs = 500; // 즉시 실행 간 간격(경미한 완충)
      JbgMallDataAccessObject jaDao = new JbgMallDataAccessObject();

      for (String seq : req.getSeqs()) {
        try {
          // 이미 수집 작업이 실행 중이면 스킵 (브라우저 실행 중)
          if (jangBoGoManager.isCollecting(seq)) {
            skipped.add(seq);
            logger.warn("쇼핑몰 seq={} 이미 수집 중, 건너뜀", seq);
            continue;
          }

          // 스케줄링 중이어도 실행 중이 아니면 사용자 요청 처리 (즉시 실행)
          boolean wasScheduled = mallSchedulerService.isScheduled(seq);
          if (wasScheduled) {
            logger.info("쇼핑몰 seq={} 스케줄링 중이지만 사용자 요청으로 즉시 실행", seq);
          }

          JSONObject mall = jaDao.getMall(seq);
          if (mall == null) {
            skipped.add(seq);
            try {
              jaDao.update(seq, 0, null, null);
            } catch (Exception ignore) {
            }
            continue;
          }

          String encKeyBase64 =
              mall.get("encrypt_key") != null ? mall.get("encrypt_key").toString() : "";
          String encIvBase64 =
              mall.get("encrypt_iv") != null ? mall.get("encrypt_iv").toString() : "";
          if (encKeyBase64.isEmpty() || encIvBase64.isEmpty()) {
            skipped.add(seq);
            try {
              jaDao.update(seq, 0, null, null);
            } catch (Exception ignore) {
            }
            continue;
          }

          Optional<com.jiniebox.jangbogo.dto.MallAccount> accOpt =
              mallAccountYmlService.getAccountBySeq(seq);
          if (accOpt.isEmpty()) {
            skipped.add(seq);
            try {
              jaDao.update(seq, 0, null, null);
            } catch (Exception ignore) {
            }
            continue;
          }

          String cipherUsrId = accOpt.get().getId();
          String cipherUsrPw = accOpt.get().getPass();
          if (cipherUsrId == null
              || cipherUsrId.isEmpty()
              || cipherUsrPw == null
              || cipherUsrPw.isEmpty()) {
            skipped.add(seq);
            try {
              jaDao.update(seq, 0, null, null);
            } catch (Exception ignore) {
            }
            continue;
          }

          javax.crypto.SecretKey secKey =
              StringEncrypter.decodeBase64ToSecretKey(encKeyBase64.trim());
          javax.crypto.spec.IvParameterSpec ivSpec =
              StringEncrypter.decodeBase64ToIv(encIvBase64.trim());

          String id =
              StringEncrypter.decrypt(StringEncrypter.ALGORITHM, cipherUsrId, secKey, ivSpec);
          String pw =
              StringEncrypter.decrypt(StringEncrypter.ALGORITHM, cipherUsrPw, secKey, ivSpec);
          if (id != null && id.startsWith("%")) id = id.substring(1);
          if (pw != null && pw.startsWith("%")) pw = pw.substring(1);
          if (id == null || pw == null) {
            skipped.add(seq);
            try {
              jaDao.update(seq, 0, null, null);
            } catch (Exception ignore) {
            }
            continue;
          }

          // executeNow 플래그에 따라 즉시 실행 여부 결정
          if (executeNow) {
            // 즉시 실행 (동기 실행 + 신규 주문 seq 수집)
            List<Integer> newSeqs = jangBoGoManager.updateItemsAndGetNewSeqs(seq, id, pw);
            allNewOrderSeqs.addAll(newSeqs);

            processed.add(seq);
            logger.info("쇼핑몰 seq={} 즉시 수집 실행 완료, 신규 주문: {}개", seq, newSeqs.size());
            try {
              Thread.sleep(sleepMs);
            } catch (InterruptedException ignore) {
            }
          } else {
            logger.info("쇼핑몰 seq={} 즉시 실행 건너뜀 (executeNow=false)", seq);
          }

          // 주기적 스케줄링 시작 (주기 > 0이면, 다음 실행은 주기만큼 대기 후)
          Integer intervalMinutes =
              mall.get("collect_interval_minutes") != null
                  ? Integer.parseInt(mall.get("collect_interval_minutes").toString())
                  : 0;
          if (intervalMinutes != null && intervalMinutes > 0) {
            mallSchedulerService.scheduleMall(seq, intervalMinutes);
            scheduled.add(seq);
            logger.info("쇼핑몰 seq={} 주기적 스케줄 등록 (다음 자동수집: {}분 후)", seq, intervalMinutes);
          }
        } catch (BadPaddingException bpe) {
          logger.warn("쇼핑몰 seq={} 자동수집 복호화 실패 (BadPaddingException)", seq);
          // 계정 복호화 오류 발생 시 DB 키/IV 삭제 및 상태 0 초기화
          try {
            jaDao.update(seq, 0, null, null);
            logger.info("쇼핑몰 seq={} 암호화 키/IV 초기화 및 account_status=0 설정 완료", seq);
          } catch (Exception resetEx) {
            logger.warn("쇼핑몰 seq={} DB 인증정보 초기화 실패: {}", seq, resetEx.getMessage());
          }
          decryptionFailed.add(seq);
        } catch (Exception perMallEx) {
          logger.warn("쇼핑몰 seq={} 자동수집 오류", seq, perMallEx);
          skipped.add(seq);
          try {
            jaDao.update(seq, 0, null, null);
          } catch (Exception ignore) {
          }
        }
      }

      response.put("success", true);
      response.set("processed", objectMapper.valueToTree(processed));
      response.set("scheduled", objectMapper.valueToTree(scheduled));
      response.set("skipped", objectMapper.valueToTree(skipped));
      response.set("decryptionFailed", objectMapper.valueToTree(decryptionFailed));

      // 메시지 구성
      StringBuilder message = new StringBuilder();
      if (executeNow && !processed.isEmpty()) {
        message.append(processed.size()).append("개 쇼핑몰 즉시 수집 시작");
      }
      if (!scheduled.isEmpty()) {
        if (message.length() > 0) message.append(", ");
        message.append(scheduled.size()).append("개 쇼핑몰 주기적 수집 활성화");
      }
      if (!executeNow && scheduled.isEmpty() && processed.isEmpty()) {
        message.append("주기적 수집만 활성화됨 (즉시 실행 안 함)");
      }
      if (!decryptionFailed.isEmpty()) {
        if (message.length() > 0) message.append(". ");
        message.append("일부 쇼핑몰 계정 복호화 실패. 해당 쇼핑몰에서 '계정연결'을 다시 시도해 주세요.");
      }
      if (message.length() == 0) {
        message.append("처리된 쇼핑몰이 없습니다.");
      }

      response.put("message", message.toString());

      // 구매내역 수집 시 파일 저장 옵션 확인 및 실행 (수집 및 DB 저장 완료 후)
      if (executeNow) {
        if (!allNewOrderSeqs.isEmpty()) {
          logger.info("신규 추가된 주문 총 {}개 - seq: {}", allNewOrderSeqs.size(), allNewOrderSeqs);
        } else {
          logger.info("신규 추가된 주문이 없습니다.");
        }

        try {
          com.jiniebox.jangbogo.dao.JbgExportConfigDataAccessObject exportConfigDao =
              new com.jiniebox.jangbogo.dao.JbgExportConfigDataAccessObject();
          JSONObject exportConfig = exportConfigDao.getConfig();

          // 설정 검증
          String validationError = validateExportConfig(exportConfig);
          if (validationError != null) {
            logger.warn("파일 저장/FTP 설정 검증 실패: {}", validationError);
            response.put("configValidationError", validationError);
          }

          Integer autoSaveEnabled =
              exportConfig.get("auto_save_enabled") != null
                  ? Integer.parseInt(exportConfig.get("auto_save_enabled").toString())
                  : 0;

          Integer saveToJinieboxCfg = null;
          if (exportConfig.get("save_to_jiniebox") != null) {
            saveToJinieboxCfg = Integer.parseInt(exportConfig.get("save_to_jiniebox").toString());
          } else if (exportConfig.get("save_to_ftp") != null) { // 구 설정 호환
            saveToJinieboxCfg = Integer.parseInt(exportConfig.get("save_to_ftp").toString());
          }
          int saveToJinieboxVal = (saveToJinieboxCfg != null) ? saveToJinieboxCfg : 0;

          boolean shouldAutoSave = (autoSaveEnabled == 1);
          boolean shouldUploadToFtp = (saveToJinieboxVal == 1);

          String savePath =
              exportConfig.get("save_path") != null ? exportConfig.get("save_path").toString() : "";
          String format =
              exportConfig.get("save_format") != null
                  ? exportConfig.get("save_format").toString()
                  : "json";

          String exportedFile = null;
          boolean exportedSuccessfully = false;
          String ftpReadyFile = null;
          boolean ftpReadyFileGenerated = false;
          boolean fileExportFailed = false; // 파일 저장 실패 플래그

          // 신규 주문이 있는 경우 파일 저장
          if (!allNewOrderSeqs.isEmpty() && (shouldAutoSave || shouldUploadToFtp)) {
            if (!savePath.isEmpty()) {
              try {
                exportedFile =
                    exportService.exportOrdersBySeqList(savePath, format, allNewOrderSeqs);
                exportedSuccessfully = true;

                response.put("exportedFile", exportedFile);
                response.put("newOrderCount", allNewOrderSeqs.size());

                if (shouldAutoSave) {
                  response.put("autoSaved", true);
                  logger.info(
                      "구매내역 수집 후 신규 데이터 파일 자동저장 완료: {}, 주문: {}개",
                      exportedFile,
                      allNewOrderSeqs.size());
                } else {
                  logger.info("FTP 업로드를 위해 파일을 생성했습니다: {}", exportedFile);
                }
              } catch (Exception exportEx) {
                fileExportFailed = true;
                logger.error("구매내역 수집 후 파일 생성 실패: {}", exportEx.getMessage(), exportEx);
                if (shouldAutoSave) {
                  response.put("autoSaveError", getErrorMessage(exportEx));
                }
                // 파일 저장 실패 시 FTP 업로드도 명시적으로 건너뛰기
                if (shouldUploadToFtp) {
                  response.put("autoFtpUploaded", false);
                  response.put("autoFtpError", "파일 생성 실패로 인해 FTP 업로드를 건너뜁니다: " + getErrorMessage(exportEx));
                  logger.warn("파일 저장 실패로 인해 FTP 업로드를 건너뜁니다.");
                }
              }
            } else {
              if (shouldAutoSave) {
                logger.warn("파일 저장 경로가 설정되지 않아 파일 저장을 건너뜁니다.");
                response.put("autoSaveError", "파일 저장 경로가 설정되지 않았습니다.");
              }
              if (shouldUploadToFtp) {
                logger.warn("FTP 업로드를 요청했지만 파일 저장 경로가 없어 업로드를 건너뜁니다.");
                response.put("autoFtpUploaded", false);
                response.put("autoFtpError", "파일 저장 경로가 설정되지 않았습니다.");
              }
            }
          } else if (allNewOrderSeqs.isEmpty() && shouldUploadToFtp) {
            // 신규 주문이 없어도 FTP 업로드 설정이 있으면 상태 파일 생성
            if (!savePath.isEmpty()) {
              try {
                ftpReadyFile = exportService.createEmptyStatusFile(savePath);
                ftpReadyFileGenerated = true;
                logger.info("신규 주문 없음, FTP 업로드용 상태 파일 생성: {}", ftpReadyFile);
              } catch (Exception statusEx) {
                logger.error("상태 파일 생성 실패: {}", statusEx.getMessage(), statusEx);
                response.put("autoFtpUploaded", false);
                response.put("autoFtpError", "상태 파일 생성 실패: " + getErrorMessage(statusEx));
              }
            } else {
              logger.warn("FTP 업로드를 요청했지만 파일 저장 경로가 없어 상태 파일 생성을 건너뜁니다.");
              response.put("autoFtpUploaded", false);
              response.put("autoFtpError", "파일 저장 경로가 설정되지 않았습니다.");
            }
          } else if (!shouldAutoSave && !shouldUploadToFtp) {
            logger.debug("자동 저장 및 FTP 업로드 옵션이 모두 비활성화되어 있습니다.");
          }

          // FTP 자동 업로드 처리 (파일 저장 실패 시 건너뛰기)
          if (shouldUploadToFtp && !fileExportFailed) {
            if ((exportedSuccessfully && exportedFile != null && !exportedFile.isEmpty())
                || (ftpReadyFileGenerated && ftpReadyFile != null && !ftpReadyFile.isEmpty())) {

              // 신규 주문이 있으면 jiniebox JSON 생성, 없으면 상태 파일 사용
              if (!allNewOrderSeqs.isEmpty() && !ftpReadyFileGenerated) {
                try {
                  ftpReadyFile =
                      exportService.exportToJinieboxFileBySeqList(savePath, allNewOrderSeqs);
                  ftpReadyFileGenerated = true;
                  logger.info("FTP 업로드용 jiniebox JSON 생성: {}", ftpReadyFile);
                } catch (Exception ftpJsonEx) {
                  logger.error("FTP 업로드용 JSON 생성 실패", ftpJsonEx);
                  response.put("autoFtpUploaded", false);
                  response.put("autoFtpError", "FTP 업로드용 JSON 생성 실패: " + getErrorMessage(ftpJsonEx));
                  ftpReadyFile = null;
                }
              }

              if (ftpReadyFile != null) {
                String ftpAddress =
                    exportConfig.get("ftp_address") != null
                        ? exportConfig.get("ftp_address").toString()
                        : "";
                String ftpId =
                    exportConfig.get("ftp_id") != null ? exportConfig.get("ftp_id").toString() : "";
                String ftpPass = "";
                try {
                  ftpPass = exportConfigDao.getDecryptedFtpPassword();
                } catch (Exception e) {
                  logger.error("FTP 비밀번호 복호화 실패", e);
                }

                int ftpEncryptEnabledVal = 1;
                if (exportConfig.get("ftp_encrypt_enabled") != null) {
                  try {
                    ftpEncryptEnabledVal =
                        Integer.parseInt(exportConfig.get("ftp_encrypt_enabled").toString());
                  } catch (NumberFormatException ignore) {
                  }
                }
                boolean ftpEncryptEnabled = (ftpEncryptEnabledVal == 1);

                String publicKey =
                    exportConfig.get("public_key") != null
                        ? exportConfig.get("public_key").toString()
                        : "";

                if (!ftpAddress.isEmpty() && !ftpId.isEmpty() && !ftpPass.isEmpty()) {
                  String fileToUpload = ftpReadyFile;
                  boolean fileEncrypted = false;

                  try {
                    if (ftpEncryptEnabled) {
                      if (!publicKey.isEmpty()) {
                        String encryptedFilePath = ftpReadyFile + ".encrypted";
                        logger.info("자동 FTP 업로드를 위한 파일 암호화 시작");

                        boolean encryptSuccess =
                            com.jiniebox.jangbogo.util.security.RsaFileEncryption.encryptFile(
                                ftpReadyFile, encryptedFilePath, publicKey);

                        if (encryptSuccess) {
                          fileToUpload = encryptedFilePath;
                          fileEncrypted = true;
                          logger.info("자동 FTP 업로드용 암호화 완료: {}", encryptedFilePath);
                        } else {
                          logger.warn("자동 FTP 업로드용 파일 암호화 실패 - 평문 업로드 진행");
                        }
                      } else {
                        logger.warn("FTP 암호화가 활성화되어 있으나 Public Key가 없습니다. 평문으로 업로드합니다.");
                      }
                    } else {
                      logger.info("자동 FTP 업로드: 암호화 비활성화 상태(평문 업로드)");
                    }

                    boolean uploadSuccess =
                        com.jiniebox.jangbogo.util.FtpUploadUtil.uploadFile(
                            ftpAddress, ftpId, ftpPass, fileToUpload);

                    if (uploadSuccess) {
                      response.put("autoFtpUploaded", true);
                      response.put("autoFtpEncrypted", fileEncrypted);
                      logger.info("자동 FTP 업로드 완료 - 서버: {}, 암호화: {}", ftpAddress, fileEncrypted);
                    } else {
                      response.put("autoFtpUploaded", false);
                      response.put("autoFtpError", "FTP 업로드에 실패했습니다.");
                      logger.warn("자동 FTP 업로드 실패 - 서버: {}", ftpAddress);
                    }

                  } catch (Exception ftpUploadEx) {
                    response.put("autoFtpUploaded", false);
                    response.put("autoFtpError", ftpUploadEx.getMessage());
                    logger.warn("자동 FTP 업로드 중 오류 발생: {}", ftpUploadEx.getMessage(), ftpUploadEx);
                  } finally {
                    // 임시 파일 삭제 (재시도 로직 포함)
                    if (fileEncrypted) {
                      deleteTempFileSafely(fileToUpload, "암호화 임시 파일", 3);
                    }
                    if (ftpReadyFileGenerated && ftpReadyFile != null && !ftpReadyFile.equals(fileToUpload)) {
                      deleteTempFileSafely(ftpReadyFile, "FTP 업로드용 임시 JSON 파일", 3);
                    }
                  }
                } else {
                  logger.warn("자동 FTP 업로드를 위한 정보가 불완전합니다. (주소/아이디/비밀번호 확인)");
                  response.put("autoFtpUploaded", false);
                  response.put("autoFtpError", "FTP 정보가 설정되지 않았습니다.");
                  if (ftpReadyFileGenerated && ftpReadyFile != null) {
                    deleteTempFileSafely(ftpReadyFile, "FTP 업로드용 임시 파일", 3);
                  }
                }
              }
            } else {
              logger.warn("자동 FTP 업로드를 수행할 파일이 생성되지 않았습니다.");
              response.put("autoFtpUploaded", false);
              response.put("autoFtpError", "FTP 업로드용 파일이 없습니다.");
            }
          }

        } catch (Exception configEx) {
          logger.error("파일 저장/FTP 설정 확인 중 오류: {}", getErrorMessage(configEx), configEx);
          response.put("configError", getErrorMessage(configEx));
        }
      }

      return response;
    } catch (IllegalArgumentException e) {
      logger.error("자동 수집 요청 처리 오류 (잘못된 인자): {}", getErrorMessage(e), e);
      response.put("success", false);
      response.put("message", "잘못된 요청입니다: " + getErrorMessage(e));
      return response;
    } catch (IllegalStateException e) {
      logger.error("자동 수집 요청 처리 오류 (상태 오류): {}", getErrorMessage(e), e);
      response.put("success", false);
      response.put("message", "시스템 상태 오류: " + getErrorMessage(e));
      return response;
    } catch (SecurityException e) {
      logger.error("자동 수집 요청 처리 오류 (보안 오류): {}", getErrorMessage(e), e);
      response.put("success", false);
      response.put("message", "보안 오류가 발생했습니다: " + getErrorMessage(e));
      return response;
    } catch (Exception e) {
      logger.error("자동 수집 요청 처리 오류 (일반 오류): {}", getErrorMessage(e), e);
      response.put("success", false);
      response.put("message", "자동 수집 처리 중 오류가 발생했습니다: " + getErrorMessage(e));
      return response;
    }
  }

  /**
   * 내보내기 설정 검증
   *
   * @param exportConfig 내보내기 설정
   * @return 검증 오류 메시지 (null이면 검증 통과)
   */
  private String validateExportConfig(JSONObject exportConfig) {
    if (exportConfig == null) {
      return "내보내기 설정이 없습니다.";
    }

    Integer autoSaveEnabled =
        exportConfig.get("auto_save_enabled") != null
            ? Integer.parseInt(exportConfig.get("auto_save_enabled").toString())
            : 0;
    Integer saveToJinieboxCfg = null;
    if (exportConfig.get("save_to_jiniebox") != null) {
      saveToJinieboxCfg = Integer.parseInt(exportConfig.get("save_to_jiniebox").toString());
    } else if (exportConfig.get("save_to_ftp") != null) {
      saveToJinieboxCfg = Integer.parseInt(exportConfig.get("save_to_ftp").toString());
    }
    int saveToJinieboxVal = (saveToJinieboxCfg != null) ? saveToJinieboxCfg : 0;

    boolean shouldAutoSave = (autoSaveEnabled == 1);
    boolean shouldUploadToFtp = (saveToJinieboxVal == 1);

    if (shouldAutoSave || shouldUploadToFtp) {
      String savePath =
          exportConfig.get("save_path") != null ? exportConfig.get("save_path").toString() : "";
      if (savePath == null || savePath.trim().isEmpty()) {
        return "파일 저장 경로가 설정되지 않았습니다.";
      }
    }

    if (shouldUploadToFtp) {
      String ftpAddress =
          exportConfig.get("ftp_address") != null
              ? exportConfig.get("ftp_address").toString()
              : "";
      String ftpId =
          exportConfig.get("ftp_id") != null ? exportConfig.get("ftp_id").toString() : "";

      if (ftpAddress == null || ftpAddress.trim().isEmpty()) {
        return "FTP 서버 주소가 설정되지 않았습니다.";
      }
      if (ftpId == null || ftpId.trim().isEmpty()) {
        return "FTP 사용자 ID가 설정되지 않았습니다.";
      }
    }

    return null; // 검증 통과
  }

  /**
   * 예외에서 사용자 친화적인 오류 메시지 추출
   *
   * @param e 예외
   * @return 오류 메시지
   */
  private String getErrorMessage(Exception e) {
    if (e == null) {
      return "알 수 없는 오류";
    }
    String message = e.getMessage();
    if (message == null || message.trim().isEmpty()) {
      return e.getClass().getSimpleName();
    }
    return message;
  }

  /**
   * 임시 파일 안전 삭제 (재시도 로직 포함)
   *
   * @param filePath 삭제할 파일 경로
   * @param fileDescription 파일 설명 (로깅용)
   * @param maxRetries 최대 재시도 횟수
   */
  private void deleteTempFileSafely(String filePath, String fileDescription, int maxRetries) {
    if (filePath == null || filePath.isEmpty()) {
      return;
    }

    java.io.File file = new java.io.File(filePath);
    if (!file.exists()) {
      return;
    }

    for (int attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        if (file.delete()) {
          logger.debug("{} 삭제 완료: {}", fileDescription, filePath);
          return;
        } else {
          if (attempt < maxRetries) {
            logger.debug("{} 삭제 실패 (시도 {}/{}), 재시도 중...", fileDescription, attempt, maxRetries);
            try {
              Thread.sleep(100 * attempt); // 백오프: 100ms, 200ms, 300ms
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              return;
            }
          } else {
            logger.warn("{} 삭제 실패 (최대 재시도 횟수 초과): {}", fileDescription, filePath);
          }
        }
      } catch (Exception e) {
        if (attempt < maxRetries) {
          logger.debug("{} 삭제 중 오류 (시도 {}/{}): {}", fileDescription, attempt, maxRetries, e.getMessage());
          try {
            Thread.sleep(100 * attempt);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return;
          }
        } else {
          logger.warn("{} 삭제 중 오류 (최대 재시도 횟수 초과): {}", fileDescription, e.getMessage());
        }
      }
    }
  }

  public static class AutoCollectRequest {
    private java.util.List<String> seqs;
    private java.util.Map<String, Integer> intervals; // seq -> intervalMinutes
    private Boolean executeNow; // 즉시 실행 여부

    public java.util.List<String> getSeqs() {
      return seqs;
    }

    public void setSeqs(java.util.List<String> seqs) {
      this.seqs = seqs;
    }

    public java.util.Map<String, Integer> getIntervals() {
      return intervals;
    }

    public void setIntervals(java.util.Map<String, Integer> intervals) {
      this.intervals = intervals;
    }

    public Boolean getExecuteNow() {
      return executeNow;
    }

    public void setExecuteNow(Boolean executeNow) {
      this.executeNow = executeNow;
    }
  }

  /**
   * 자동수집 체크박스 상태 및 주기 저장 (전체 저장) POST /malls/auto-collect/flags body: { seqs: ["1","2", ...], //
   * 체크된 seq 전체 목록 intervals: { "1": 30, "2": 60, ... } // seq별 주기(분) }
   */
  @PostMapping("/malls/auto-collect/flags")
  @ResponseBody
  public JsonNode saveAutoCollectFlags(@RequestBody AutoCollectRequest req) {
    ObjectMapper objectMapper = new ObjectMapper();
    ObjectNode response = objectMapper.createObjectNode();
    try {
      JbgMallDataAccessObject jaDao = new JbgMallDataAccessObject();

      // 체크박스 상태와 주기 시간 DB에 저장
      java.util.List<String> seqs = req != null ? req.getSeqs() : java.util.Collections.emptyList();
      java.util.Map<String, Integer> intervals =
          req != null ? req.getIntervals() : java.util.Collections.emptyMap();

      jaDao.saveAutoCollectFlags(seqs, intervals);

      // 스케줄링 상태 동기화
      // 1. 체크 해제된 쇼핑몰 스케줄 취소
      java.util.List<JSONObject> allMalls = jaDao.getAllMalls(false);
      for (JSONObject mall : allMalls) {
        String seq = String.valueOf(mall.get("seq"));
        if (!seqs.contains(seq)) {
          // 체크 해제된 쇼핑몰은 스케줄 취소
          mallSchedulerService.cancelMall(seq);
          logger.debug("체크 해제된 쇼핑몰 seq={} 스케줄 취소", seq);
        }
      }

      // 2. 체크된 쇼핑몰 스케줄 업데이트
      for (String seq : seqs) {
        Integer intervalMinutes = intervals.getOrDefault(seq, 0);
        if (intervalMinutes != null && intervalMinutes > 0) {
          // 주기가 설정된 쇼핑몰만 스케줄링
          mallSchedulerService.scheduleMall(seq, intervalMinutes);
          logger.debug("쇼핑몰 seq={} 스케줄 등록 (주기: {}분)", seq, intervalMinutes);
        } else {
          // 주기가 0이면 스케줄 취소
          mallSchedulerService.cancelMall(seq);
          logger.debug("쇼핑몰 seq={} 스케줄 취소 (주기=0)", seq);
        }
      }

      response.put("success", true);
      response.put("message", "자동수집 설정이 저장되었습니다.");
      return response;
    } catch (Exception e) {
      logger.error("자동수집 설정 저장 오류", e);
      response.put("success", false);
      response.put("message", "자동수집 설정 저장 중 오류가 발생했습니다.");
      return response;
    }
  }

  /** 구매정보 파일 저장 POST /export/orders body: { savePath: "경로", format: "json|yaml|csv" } */
  @PostMapping("/export/orders")
  @ResponseBody
  public JsonNode exportOrders(@RequestBody ExportRequest req) {
    ObjectMapper objectMapper = new ObjectMapper();
    ObjectNode response = objectMapper.createObjectNode();

    try {
      // 입력값 검증
      if (req == null || req.getSavePath() == null || req.getSavePath().trim().isEmpty()) {
        response.put("success", false);
        response.put("message", "저장 경로를 입력해주세요.");
        return response;
      }

      String format = req.getFormat();
      if (format == null || format.trim().isEmpty()) {
        format = "json"; // 기본값
      }

      // limit 파라미터 처리 (0 또는 null이면 전체)
      int limit = (req.getLimit() != null && req.getLimit() > 0) ? req.getLimit() : 0;

      // saveToJiniebox 파라미터 처리 (요청 값이 있으면 사용, 없으면 DB 설정 사용)
      Boolean saveToJiniebox = req.getSaveToJiniebox();

      logger.info(
          "파일 저장 요청 - 경로: {}, 포맷: {}, limit: {}, FTP 업로드: {}",
          req.getSavePath(),
          format,
          limit > 0 ? limit : "전체",
          saveToJiniebox != null ? (saveToJiniebox ? "활성화" : "비활성화") : "DB 설정 사용");

      // 파일명 생성
      java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss");
      String timestamp = dateFormat.format(new java.util.Date());
      String fileName = "purchase_" + timestamp + ".json";
      String filePath = req.getSavePath() + java.io.File.separator + fileName;

      // jiniebox 형식 JSON 파일 생성
      exportService.exportToJiniebox(filePath, limit);

      response.put("success", true);
      response.put("message", "파일 저장이 완료되었습니다.");
      response.put("filePath", filePath);

      logger.info("구매정보 파일 저장 완료 - 파일: {}", filePath);

      // FTP 설정 확인 및 업로드
      try {
        com.jiniebox.jangbogo.dao.JbgExportConfigDataAccessObject exportConfigDao =
            new com.jiniebox.jangbogo.dao.JbgExportConfigDataAccessObject();
        org.json.simple.JSONObject exportConfig = exportConfigDao.getConfig();

        if (exportConfig != null) {
          // 요청 파라미터로 전달된 saveToJiniebox 값 우선 사용
          boolean shouldUploadToFtp = false;
          boolean ftpEncryptEnabled = true;

          if (saveToJiniebox != null) {
            // 요청에서 명시적으로 지정된 경우
            shouldUploadToFtp = saveToJiniebox;
            logger.info("FTP 업로드 여부: 요청 값 사용 = {}", shouldUploadToFtp);
          } else {
            // 요청 값이 없으면 DB 설정 사용
            Object saveToJinieboxObj = exportConfig.get("save_to_jiniebox");
            int saveToJinieboxDb = 0;

            if (saveToJinieboxObj instanceof Number) {
              saveToJinieboxDb = ((Number) saveToJinieboxObj).intValue();
            } else if (saveToJinieboxObj instanceof String) {
              try {
                saveToJinieboxDb = Integer.parseInt((String) saveToJinieboxObj);
              } catch (NumberFormatException e) {
                logger.warn("save_to_jiniebox 값 변환 실패: {}", saveToJinieboxObj);
              }
            }
            shouldUploadToFtp = (saveToJinieboxDb == 1);
            logger.info("FTP 업로드 여부: DB 설정 사용 = {}", shouldUploadToFtp);
          }

          Object ftpEncryptObj = exportConfig.get("ftp_encrypt_enabled");
          if (ftpEncryptObj instanceof Number) {
            ftpEncryptEnabled = (((Number) ftpEncryptObj).intValue() == 1);
          } else if (ftpEncryptObj instanceof String) {
            ftpEncryptEnabled = "1".equals(ftpEncryptObj);
          }

          // FTP 업로드가 활성화되어 있는 경우
          if (shouldUploadToFtp) {
            String ftpAddress =
                exportConfig.get("ftp_address") != null
                    ? exportConfig.get("ftp_address").toString()
                    : "";
            String ftpId =
                exportConfig.get("ftp_id") != null ? exportConfig.get("ftp_id").toString() : "";

            // FTP 비밀번호는 보안을 위해 별도 메서드로 복호화
            String ftpPass = "";
            try {
              ftpPass = exportConfigDao.getDecryptedFtpPassword();
            } catch (Exception e) {
              logger.error("FTP 비밀번호 복호화 실패", e);
            }

            String publicKey =
                exportConfig.get("public_key") != null
                    ? exportConfig.get("public_key").toString()
                    : "";

            // FTP 정보 검증
            if (!ftpAddress.isEmpty() && !ftpId.isEmpty() && !ftpPass.isEmpty()) {

              String fileToUpload = filePath;
              boolean fileEncrypted = false;

              if (ftpEncryptEnabled) {
                // Public Key가 있으면 파일 암호화
                if (!publicKey.isEmpty()) {
                  try {
                    String encryptedFilePath = filePath + ".encrypted";
                    logger.info("파일 암호화 시작 - Public Key 사용");

                    boolean encryptSuccess =
                        com.jiniebox.jangbogo.util.security.RsaFileEncryption.encryptFile(
                            filePath, encryptedFilePath, publicKey);

                    if (encryptSuccess) {
                      fileToUpload = encryptedFilePath;
                      fileEncrypted = true;
                      logger.info("파일 암호화 완료: {}", encryptedFilePath);
                    } else {
                      logger.warn("파일 암호화 실패 - 원본 파일 업로드");
                    }
                  } catch (Exception encEx) {
                    logger.warn("파일 암호화 중 오류 - 원본 파일 업로드: {}", encEx.getMessage());
                  }
                } else {
                  logger.warn("FTP 암호화가 활성화되어 있으나 Public Key가 없습니다. 평문으로 업로드합니다.");
                }
              } else {
                logger.info("FTP 암호화 옵션이 비활성화되어 평문 파일을 업로드합니다.");
              }

              logger.info("FTP 업로드 시작 - 서버: {}, 파일: {}", ftpAddress, fileToUpload);

              boolean uploadSuccess =
                  com.jiniebox.jangbogo.util.FtpUploadUtil.uploadFile(
                      ftpAddress, ftpId, ftpPass, fileToUpload);

              if (uploadSuccess) {
                String ftpMessage = "파일 저장 및 FTP 업로드가 완료되었습니다.";
                if (fileEncrypted) {
                  ftpMessage += " (암호화됨)";
                }
                response.put("message", ftpMessage);
                response.put("ftpUploaded", true);
                response.put("encrypted", fileEncrypted);
                logger.info("FTP 업로드 완료 - 서버: {}, 암호화: {}", ftpAddress, fileEncrypted);
              } else {
                String ftpWarning = "파일은 저장되었으나 FTP 업로드에 실패했습니다.";
                response.put("message", ftpWarning);
                response.put("ftpUploaded", false);
                logger.warn("FTP 업로드 실패 - 서버: {}", ftpAddress);
              }

              // 암호화된 임시 파일 삭제
              if (fileEncrypted) {
                try {
                  java.io.File encFile = new java.io.File(fileToUpload);
                  if (encFile.exists()) {
                    encFile.delete();
                    logger.debug("암호화된 임시 파일 삭제: {}", fileToUpload);
                  }
                } catch (Exception delEx) {
                  logger.warn("암호화된 임시 파일 삭제 실패: {}", delEx.getMessage());
                }
              }
            } else {
              logger.warn("FTP 정보가 불완전합니다. 업로드를 건너뜁니다.");
            }
          } else {
            // FTP 업로드가 비활성화된 경우
            logger.info("FTP 업로드 비활성화 - 로컬 저장만 완료");
            response.put("ftpUploaded", false);
          }
        }
      } catch (Exception ftpEx) {
        // FTP 업로드 실패해도 파일 저장은 성공했으므로 경고만 표시
        logger.warn("FTP 업로드 중 오류 발생: {}", ftpEx.getMessage(), ftpEx);
        response.put("message", "파일은 저장되었으나 FTP 업로드 중 오류가 발생했습니다.");
        response.put("ftpUploaded", false);
      }

    } catch (UnsupportedOperationException e) {
      response.put("success", false);
      response.put("message", e.getMessage());
      logger.warn("지원하지 않는 포맷: {}", e.getMessage());
    } catch (Exception e) {
      response.put("success", false);
      response.put("message", "파일 저장 중 오류가 발생했습니다: " + e.getMessage());
      logger.error("파일 저장 오류", e);
    }

    return response;
  }

  /**
   * 파일 저장 설정 업데이트 POST /export/config body: { savePath: "경로", format: "json|yaml|csv",
   * autoSaveEnabled: true|false } autoSaveEnabled: 구매내역 수집시 함께 저장 여부
   */
  @PostMapping("/export/config")
  @ResponseBody
  public JsonNode updateExportConfig(@RequestBody ExportConfigRequest req) {
    ObjectMapper objectMapper = new ObjectMapper();
    ObjectNode response = objectMapper.createObjectNode();

    try {
      String savePath = req.getSavePath() != null ? req.getSavePath().trim() : "";
      String format = req.getFormat() != null ? req.getFormat().trim() : "json";
      int autoSaveEnabled = (req.getAutoSaveEnabled() != null && req.getAutoSaveEnabled()) ? 1 : 0;
      int ftpEncryptEnabled = 1;
      if (req.getFtpEncryptEnabled() != null) {
        ftpEncryptEnabled = req.getFtpEncryptEnabled() ? 1 : 0;
      }
      int saveToJiniebox = (req.getSaveToJiniebox() != null && req.getSaveToJiniebox()) ? 1 : 0;
      String ftpAddress = req.getFtpAddress() != null ? req.getFtpAddress().trim() : "";
      String ftpId = req.getFtpId() != null ? req.getFtpId().trim() : "";
      // ftpPass가 null이면 null로 유지, 빈 문자열이면 null로 변환 (기존 비밀번호 유지)
      String ftpPass =
          (req.getFtpPass() != null && !req.getFtpPass().trim().isEmpty())
              ? req.getFtpPass().trim()
              : null;

      // Public Key 추출 및 검증 (요청에서 전달된 경우만 저장)
      String publicKey = req.getPublicKey() != null ? req.getPublicKey().trim() : "";

      // 지니박스 저장이 활성화된 경우 FTP 정보 검증
      if (saveToJiniebox == 1) {
        java.util.List<String> missingFields = new java.util.ArrayList<>();
        if (ftpAddress.isEmpty()) missingFields.add("FTP 주소");
        if (ftpId.isEmpty()) missingFields.add("FTP 아이디");

        // 비밀번호는 DB에 기존 값이 있으면 필수가 아님
        // 여기서는 클라이언트가 이미 검증했으므로 생략

        if (!missingFields.isEmpty()) {
          response.put("success", false);
          response.put(
              "message", "지니박스 저장을 사용하려면 다음 정보를 모두 입력해야 합니다: " + String.join(", ", missingFields));
          logger.warn("FTP 정보 검증 실패 - 누락된 필드: {}", missingFields);
          return response;
        }

        if (ftpEncryptEnabled == 1 && (publicKey == null || publicKey.isEmpty())) {
          response.put("success", false);
          response.put("message", "FTP 파일 암호화를 사용하려면 Public Key를 입력해야 합니다.");
          logger.warn("FTP 암호화 사용 설정이지만 Public Key가 비어있음");
          return response;
        }

        logger.info(
            "FTP 정보 검증 성공 - 주소: {}, 아이디: {}, 비밀번호 변경: {}, 암호화 사용: {}",
            ftpAddress,
            ftpId,
            (ftpPass != null ? "예" : "아니오 (기존 값 유지)"),
            (ftpEncryptEnabled == 1 ? "예" : "아니오"));
      }

      // Public Key 유효성 검사 (Base64 포맷 확인)
      if (!publicKey.isEmpty()) {
        try {
          java.util.Base64.getDecoder().decode(publicKey);
          logger.info("Public Key 포맷 검증 완료 (길이: {})", publicKey.length());
        } catch (IllegalArgumentException e) {
          logger.error("잘못된 Public Key 포맷 (Base64가 아님): {}", e.getMessage());
          response.put("success", false);
          response.put("message", "Public Key 포맷이 올바르지 않습니다. Base64 형식이어야 합니다.");
          return response;
        }
      }

      com.jiniebox.jangbogo.dao.JbgExportConfigDataAccessObject exportConfigDao =
          new com.jiniebox.jangbogo.dao.JbgExportConfigDataAccessObject();
      exportConfigDao.updateConfig(
          savePath,
          format,
          autoSaveEnabled,
          saveToJiniebox,
          ftpAddress,
          ftpId,
          ftpPass,
          publicKey,
          ftpEncryptEnabled);

      response.put("success", true);
      response.put("message", "파일 저장 설정이 저장되었습니다.");

      logger.info(
          "파일 저장 설정 업데이트 - path: {}, format: {}, autoSave: {}, jiniebox: {}, ftpEncrypt: {}",
          savePath,
          format,
          autoSaveEnabled,
          saveToJiniebox,
          ftpEncryptEnabled);

    } catch (Exception e) {
      response.put("success", false);
      response.put("message", "설정 저장 중 오류가 발생했습니다: " + e.getMessage());
      logger.error("파일 저장 설정 업데이트 오류", e);
    }

    return response;
  }

  /** 파일 저장 요청 DTO */
  public static class ExportRequest {
    private String savePath;
    private String format;
    private Integer limit; // 조회 개수 제한 (0 또는 null이면 전체, 100이면 최근 100개)
    private Boolean saveToJiniebox; // 지니박스 FTP 업로드 여부

    public String getSavePath() {
      return savePath;
    }

    public void setSavePath(String savePath) {
      this.savePath = savePath;
    }

    public String getFormat() {
      return format;
    }

    public void setFormat(String format) {
      this.format = format;
    }

    public Integer getLimit() {
      return limit;
    }

    public void setLimit(Integer limit) {
      this.limit = limit;
    }

    public Boolean getSaveToJiniebox() {
      return saveToJiniebox;
    }

    public void setSaveToJiniebox(Boolean saveToJiniebox) {
      this.saveToJiniebox = saveToJiniebox;
    }
  }

  /** 파일 저장 설정 요청 DTO */
  public static class ExportConfigRequest {
    private String savePath;
    private String format;
    private Boolean autoSaveEnabled;
    private Boolean saveToJiniebox;
    private String ftpAddress;
    private String ftpId;
    private String ftpPass;
    private String publicKey;
    private Boolean ftpEncryptEnabled;

    public String getSavePath() {
      return savePath;
    }

    public void setSavePath(String savePath) {
      this.savePath = savePath;
    }

    public String getFormat() {
      return format;
    }

    public void setFormat(String format) {
      this.format = format;
    }

    public Boolean getAutoSaveEnabled() {
      return autoSaveEnabled;
    }

    public void setAutoSaveEnabled(Boolean autoSaveEnabled) {
      this.autoSaveEnabled = autoSaveEnabled;
    }

    public Boolean getSaveToJiniebox() {
      return saveToJiniebox;
    }

    public void setSaveToJiniebox(Boolean saveToJiniebox) {
      this.saveToJiniebox = saveToJiniebox;
    }

    public String getFtpAddress() {
      return ftpAddress;
    }

    public void setFtpAddress(String ftpAddress) {
      this.ftpAddress = ftpAddress;
    }

    public String getFtpId() {
      return ftpId;
    }

    public void setFtpId(String ftpId) {
      this.ftpId = ftpId;
    }

    public String getFtpPass() {
      return ftpPass;
    }

    public void setFtpPass(String ftpPass) {
      this.ftpPass = ftpPass;
    }

    public String getPublicKey() {
      return publicKey;
    }

    public void setPublicKey(String publicKey) {
      this.publicKey = publicKey;
    }

    public Boolean getFtpEncryptEnabled() {
      return ftpEncryptEnabled;
    }

    public void setFtpEncryptEnabled(Boolean ftpEncryptEnabled) {
      this.ftpEncryptEnabled = ftpEncryptEnabled;
    }
  }
}

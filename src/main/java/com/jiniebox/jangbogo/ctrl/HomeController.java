package com.jiniebox.jangbogo.ctrl;

import com.jiniebox.jangbogo.dao.JbgExportConfigDataAccessObject;
import com.jiniebox.jangbogo.sys.SessionConstants;
import jakarta.servlet.http.HttpSession;
import java.io.File;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 홈 컨트롤러 (개선 버전)
 *
 * <p>- SessionConstants를 사용하여 세션 키 중앙 관리 - Interceptor에서 전역 세션 검사를 수행하므로 index()의 세션 체크는 제거 가능 -
 * signin()은 Interceptor 제외 경로이므로 내부 체크 유지
 */
@Controller
public class HomeController {

  private static final Logger logger = LogManager.getLogger(HomeController.class);

  /** 메인 페이지 Interceptor에서 이미 세션 체크를 수행하므로 여기까지 도달했다면 인증된 상태 */
  @GetMapping("/")
  public String index(HttpSession session, Model model) {
    // ✅ 개선: Interceptor가 세션 체크를 수행하므로 중복 제거
    // 여기까지 도달했다면 이미 로그인된 상태

    model.addAttribute("activePage", "dashboard");
    try {
      // DB에서 저장 경로 설정 가져오기
      JbgExportConfigDataAccessObject exportConfigDao = new JbgExportConfigDataAccessObject();
      JSONObject exportConfig = exportConfigDao.getConfig();

      String savedPath =
          exportConfig != null && exportConfig.get("save_path") != null
              ? exportConfig.get("save_path").toString()
              : "";

      // DB에 저장된 경로가 없거나 비어있으면 기본 경로 사용
      String defaultSavePath;
      if (savedPath == null || savedPath.trim().isEmpty()) {
        // 설치 경로의 exports 폴더 절대 경로
        defaultSavePath = new File("exports").getAbsolutePath();
        logger.info("DB에 저장 경로가 없어 기본 경로 사용: {}", defaultSavePath);
      } else {
        defaultSavePath = savedPath;
        logger.debug("DB에서 저장 경로 로드: {}", defaultSavePath);
      }

      model.addAttribute("defaultSavePath", defaultSavePath);

    } catch (Exception e) {
      // 오류 발생 시 기본 경로 사용
      String fallbackPath = new File("exports").getAbsolutePath();
      model.addAttribute("defaultSavePath", fallbackPath);
      logger.error("저장 경로 설정 조회 중 오류 발생, 기본 경로 사용: {}", fallbackPath, e);
    }

    return "index";
  }

  /** 관리자 프로필 페이지 */
  @GetMapping("/profile")
  public String profile(Model model) {
    model.addAttribute("activePage", "profile");
    return "profile";
  }

  /** 로그인 페이지 Interceptor 제외 경로이므로 내부에서 세션 체크 수행 */
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

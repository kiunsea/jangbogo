package com.jiniebox.jangbogo.svc.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

/**
 * 캡처용 마스킹 프로필 브라우저 기동을 격리 재현한다 (진단 전용, 실계정·로그인 불필요).
 *
 * <p>{@code getWebDriver("chrome", profileDir)} 가 SessionNotCreatedException 을 내는지, 낸다면 그 전체 메시지가
 * 무엇인지 확인한다. 실서비스 catch 가 클래스명만 남겨 근본 원인이 가려졌기에 여기서 전부 찍는다.
 */
@Tag("probe")
class CaptureLaunchDiagProbe {

  @Test
  @DisplayName("DIAG — 마스킹 프로필로 ChromeDriver 를 격리 기동한다")
  void reproduceMaskedProfileLaunch() throws Exception {
    Path profile = Paths.get("build", "probe-profiles", "capture-launch-diag");
    if (Files.exists(profile)) {
      try (var w = Files.walk(profile)) {
        w.sorted(Comparator.reverseOrder())
            .forEach(
                p -> {
                  try {
                    Files.deleteIfExists(p);
                  } catch (Exception ignore) {
                    // 남은 파일은 다음 실행이 덮어쓴다
                  }
                });
      }
    }
    Files.createDirectories(profile);

    WebDriver driver = null;
    try {
      driver = new WebDriverManager().getWebDriver("chrome", profile);
      driver.get("about:blank");
      Object wd = ((JavascriptExecutor) driver).executeScript("return navigator.webdriver");
      System.out.println("[DIAG] 성공 — navigator.webdriver=" + wd);
    } catch (Throwable t) {
      System.out.println("[DIAG] 실패 — 예외 사슬:");
      for (Throwable c = t; c != null; c = c.getCause()) {
        System.out.println("[DIAG]   " + c.getClass().getName() + " :: " + c.getMessage());
      }
      throw t;
    } finally {
      if (driver != null) {
        try {
          driver.quit();
        } catch (Exception ignore) {
          // 정리 단계
        }
      }
    }
  }
}

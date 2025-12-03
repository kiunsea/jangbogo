package com.jiniebox.jangbogo.dev;

import com.jiniebox.jangbogo.dto.JangbogoConfig;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * JangbogoConfig 사용 예제
 *
 * <p>config/jbg_config.yml 파일의 설정값을 읽어오는 예제
 */
@Component
public class JangbogoConfigExample {

  @Autowired private JangbogoConfig jangbogoConfig;

  /** 모든 예제 실행 */
  public void runAllExamples() {
    System.out.println("========================================");
    System.out.println("JangbogoConfig 사용 예제");
    System.out.println("========================================\n");

    example1_directAccess();
    example2_getMethod();
    example3_getAllConfig();
    example4_specificUseCases();

    System.out.println("\n========================================");
    System.out.println("모든 예제 실행 완료!");
    System.out.println("========================================");
  }

  /** 예제 1: 직접 필드 접근 (Getter 사용) */
  public void example1_directAccess() {
    System.out.println("=== 예제 1: 직접 필드 접근 ===");

    System.out.println("localdbName: " + jangbogoConfig.getLocaldbName());
    System.out.println("localdbPath: " + jangbogoConfig.getLocaldbPath());
    System.out.println("maxRetryCount: " + jangbogoConfig.getMaxRetryCount());
    System.out.println("timeoutSeconds: " + jangbogoConfig.getTimeoutSeconds());
    System.out.println("debugMode: " + jangbogoConfig.isDebugMode());
    System.out.println("appVersion: " + jangbogoConfig.getAppVersion());
    System.out.println("mallSigninDelay: " + jangbogoConfig.getMallSigninDelay());
    System.out.println("defaultWebDriver: " + jangbogoConfig.getDefaultWebDriver());
    System.out.println("browserHeadless: " + jangbogoConfig.isBrowserHeadless());

    System.out.println();
  }

  /** 예제 2: get() 메서드 사용 (PropertiesUtil 스타일) */
  public void example2_getMethod() {
    System.out.println("=== 예제 2: get() 메서드 사용 ===");

    // 언더스코어 방식
    System.out.println("LOCALDB_NAME: " + jangbogoConfig.get("LOCALDB_NAME"));
    System.out.println("LOCALDB_PATH: " + jangbogoConfig.get("LOCALDB_PATH"));
    System.out.println("MAX_RETRY_COUNT: " + jangbogoConfig.get("MAX_RETRY_COUNT"));
    System.out.println("TIMEOUT_SECONDS: " + jangbogoConfig.get("TIMEOUT_SECONDS"));
    System.out.println("DEBUG_MODE: " + jangbogoConfig.get("DEBUG_MODE"));
    System.out.println("APP_VERSION: " + jangbogoConfig.get("APP_VERSION"));

    // 하이픈 방식
    System.out.println("MALL-SIGNIN-DELAY: " + jangbogoConfig.get("MALL-SIGNIN-DELAY"));
    System.out.println("DEFAULT-WEB-DRIVER: " + jangbogoConfig.get("DEFAULT-WEB-DRIVER"));
    System.out.println("BROWSER-HEADLESS: " + jangbogoConfig.get("BROWSER-HEADLESS"));

    // 대소문자 무관
    System.out.println("localdb_name (소문자): " + jangbogoConfig.get("localdb_name"));
    System.out.println("LocalDb-Name (혼합): " + jangbogoConfig.get("LocalDb-Name"));

    System.out.println();
  }

  /** 예제 3: 모든 설정 조회 */
  public void example3_getAllConfig() {
    System.out.println("=== 예제 3: 모든 설정 조회 ===");

    Map<String, Object> allConfig = jangbogoConfig.getAllConfig();

    System.out.println("전체 설정 목록:");
    allConfig.forEach(
        (key, value) -> {
          System.out.println("  " + key + " = " + value);
        });

    System.out.println("총 " + allConfig.size() + "개의 설정");

    System.out.println();
  }

  /** 예제 4: 실제 사용 사례 */
  public void example4_specificUseCases() {
    System.out.println("=== 예제 4: 실제 사용 사례 ===");

    // 1. DB 연결 정보
    String dbPath = jangbogoConfig.getLocaldbPath() + "/" + jangbogoConfig.getLocaldbName() + ".db";
    System.out.println("DB 경로: " + dbPath);

    // 2. 재시도 로직
    int maxRetry = jangbogoConfig.getMaxRetryCount();
    System.out.println("최대 재시도 횟수: " + maxRetry);

    for (int i = 0; i < maxRetry; i++) {
      System.out.println("  재시도 " + (i + 1) + "/" + maxRetry);
    }

    // 3. 타임아웃 설정
    int timeout = jangbogoConfig.getTimeoutSeconds();
    System.out.println("타임아웃: " + timeout + "초");

    // 4. 디버그 모드 확인
    if (jangbogoConfig.isDebugMode()) {
      System.out.println("디버그 모드 활성화됨");
    } else {
      System.out.println("디버그 모드 비활성화됨");
    }

    // 5. WebDriver 설정
    String driver = jangbogoConfig.getDefaultWebDriver();
    boolean headless = jangbogoConfig.isBrowserHeadless();
    System.out.println("WebDriver: " + driver + " (Headless: " + headless + ")");

    // 6. 로그인 지연 시간 계산
    long delayMs = jangbogoConfig.getMallSigninDelay();
    long delayMinutes = delayMs / (1000 * 60);
    long delayHours = delayMinutes / 60;
    System.out.println("쇼핑몰 로그인 지연: " + delayMs + "ms (" + delayHours + "시간)");

    System.out.println();
  }

  /** 예제 5: 설정 파일 재로드 (개발용) */
  public void example5_reloadConfig() {
    System.out.println("=== 예제 5: 설정 파일 재로드 ===");

    System.out.println("설정 재로드 전 - localdbName: " + jangbogoConfig.getLocaldbName());

    jangbogoConfig.reloadConfig();

    System.out.println("설정 재로드 후 - localdbName: " + jangbogoConfig.getLocaldbName());
    System.out.println("✓ 설정 재로드 완료");

    System.out.println();
  }
}

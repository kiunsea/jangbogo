package com.jiniebox.jangbogo.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

/**
 * 장보고 프로젝트 사용자 정의 설정
 *
 * <p>config/jbg_config.yml 파일에서 설정을 로드하여 사용 application.yml의 설정보다 우선순위가 높음
 */
@Component
public class JangbogoConfig {

  private static final Logger logger = LogManager.getLogger(JangbogoConfig.class);

  private static final String CONFIG_FILE_PATH = "config/jbg_config.yml";

  // 필드
  private String localdbName;
  private String localdbPath;
  private int maxRetryCount;
  private int timeoutSeconds;
  private boolean debugMode;
  private String appVersion;
  private long mallSigninDelay;
  private String defaultWebDriver;
  private boolean browserHeadless;

  // 내부 변수 맵 (get() 메서드에서 사용)
  private Map<String, Object> configMap = new HashMap<>();

  /** 애플리케이션 시작 시 config/jbg_config.yml 파일에서 설정 로드 */
  @PostConstruct
  public void loadConfig() {
    logger.info("========== JangbogoConfig 초기화 시작 ==========");

    File configFile = new File(CONFIG_FILE_PATH);

    if (!configFile.exists()) {
      logger.warn("설정 파일이 존재하지 않습니다: {}", CONFIG_FILE_PATH);
      logger.warn("application.yml의 기본 설정을 사용합니다.");
      loadDefaultConfig();
      return;
    }

    try {
      // YAML 파일 읽기
      ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
      @SuppressWarnings("unchecked")
      Map<String, Object> yamlData = yamlMapper.readValue(configFile, Map.class);

      // 필드에 값 설정
      this.localdbName = getStringValue(yamlData, "localdb-name");
      this.localdbPath = getStringValue(yamlData, "localdb-path");
      this.maxRetryCount = getIntValue(yamlData, "max-retry-count", 3);
      this.timeoutSeconds = getIntValue(yamlData, "timeout-seconds", 30);
      this.debugMode = getBooleanValue(yamlData, "debug-mode", false);
      this.appVersion = getStringValue(yamlData, "app-version");
      this.mallSigninDelay = getLongValue(yamlData, "mall-signin-delay", 21600000L);
      this.defaultWebDriver = getStringValue(yamlData, "default-web-driver", "chrome");
      this.browserHeadless = getBooleanValue(yamlData, "browser-headless", false);

      // 내부 맵에 저장 (get() 메서드에서 사용)
      populateConfigMap(yamlData);

      logger.info("설정 파일 로드 완료: {}", CONFIG_FILE_PATH);
      logger.info("로드된 설정: {}", this);

    } catch (IOException e) {
      logger.error("설정 파일 로드 실패: {}", CONFIG_FILE_PATH, e);
      logger.warn("application.yml의 기본 설정을 사용합니다.");
      loadDefaultConfig();
    }

    logger.info("========== JangbogoConfig 초기화 완료 ==========");
  }

  /** 기본 설정 로드 (jbg_config.yml이 없을 때) */
  private void loadDefaultConfig() {
    this.localdbName = "jangbogo-dev";
    this.localdbPath = "./db";
    this.maxRetryCount = 3;
    this.timeoutSeconds = 30;
    this.debugMode = true;
    this.appVersion = "1.0.0";
    this.mallSigninDelay = 21600000L;
    this.defaultWebDriver = "chrome";
    this.browserHeadless = false;

    populateConfigMapFromFields();
  }

  /** YAML 데이터를 내부 맵에 저장 */
  private void populateConfigMap(Map<String, Object> yamlData) {
    configMap.clear();

    // YAML 데이터를 그대로 맵에 저장
    yamlData.forEach(
        (key, value) -> {
          // 하이픈을 언더스코어로 변환하여 저장 (양쪽 다 지원)
          String normalizedKey = key.toUpperCase().replace("-", "_");
          configMap.put(normalizedKey, value);
          configMap.put(key.toUpperCase().replace("-", "-"), value); // 하이픈 그대로도 저장
        });

    logger.debug("Config Map 초기화 완료: {} entries", configMap.size());
  }

  /** 필드 값으로 내부 맵 초기화 */
  private void populateConfigMapFromFields() {
    configMap.clear();
    configMap.put("LOCALDB_NAME", localdbName);
    configMap.put("LOCALDB-NAME", localdbName);
    configMap.put("LOCALDB_PATH", localdbPath);
    configMap.put("LOCALDB-PATH", localdbPath);
    configMap.put("MAX_RETRY_COUNT", maxRetryCount);
    configMap.put("MAX-RETRY-COUNT", maxRetryCount);
    configMap.put("TIMEOUT_SECONDS", timeoutSeconds);
    configMap.put("TIMEOUT-SECONDS", timeoutSeconds);
    configMap.put("DEBUG_MODE", debugMode);
    configMap.put("DEBUG-MODE", debugMode);
    configMap.put("APP_VERSION", appVersion);
    configMap.put("APP-VERSION", appVersion);
    configMap.put("MALL_SIGNIN_DELAY", mallSigninDelay);
    configMap.put("MALL-SIGNIN-DELAY", mallSigninDelay);
    configMap.put("DEFAULT_WEB_DRIVER", defaultWebDriver);
    configMap.put("DEFAULT-WEB-DRIVER", defaultWebDriver);
    configMap.put("BROWSER_HEADLESS", browserHeadless);
    configMap.put("BROWSER-HEADLESS", browserHeadless);
  }

  /** YAML에서 문자열 값 추출 */
  private String getStringValue(Map<String, Object> map, String key) {
    return getStringValue(map, key, null);
  }

  private String getStringValue(Map<String, Object> map, String key, String defaultValue) {
    Object value = map.get(key);
    return value != null ? value.toString() : defaultValue;
  }

  /** YAML에서 정수 값 추출 */
  private int getIntValue(Map<String, Object> map, String key, int defaultValue) {
    Object value = map.get(key);
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    return defaultValue;
  }

  /** YAML에서 Long 값 추출 */
  private long getLongValue(Map<String, Object> map, String key, long defaultValue) {
    Object value = map.get(key);
    if (value instanceof Number) {
      return ((Number) value).longValue();
    }
    return defaultValue;
  }

  /** YAML에서 Boolean 값 추출 */
  private boolean getBooleanValue(Map<String, Object> map, String key, boolean defaultValue) {
    Object value = map.get(key);
    if (value instanceof Boolean) {
      return (Boolean) value;
    }
    return defaultValue;
  }

  // ========== Getters and Setters ==========

  public String getLocaldbName() {
    return localdbName;
  }

  public void setLocaldbName(String localdbName) {
    this.localdbName = localdbName;
  }

  public String getLocaldbPath() {
    return localdbPath;
  }

  public void setLocaldbPath(String localdbPath) {
    this.localdbPath = localdbPath;
  }

  public int getMaxRetryCount() {
    return maxRetryCount;
  }

  public void setMaxRetryCount(int maxRetryCount) {
    this.maxRetryCount = maxRetryCount;
  }

  public int getTimeoutSeconds() {
    return timeoutSeconds;
  }

  public void setTimeoutSeconds(int timeoutSeconds) {
    this.timeoutSeconds = timeoutSeconds;
  }

  public boolean isDebugMode() {
    return debugMode;
  }

  public void setDebugMode(boolean debugMode) {
    this.debugMode = debugMode;
  }

  public String getAppVersion() {
    return appVersion;
  }

  public void setAppVersion(String appVersion) {
    this.appVersion = appVersion;
  }

  public long getMallSigninDelay() {
    return mallSigninDelay;
  }

  public void setMallSigninDelay(long mallSigninDelay) {
    this.mallSigninDelay = mallSigninDelay;
  }

  public String getDefaultWebDriver() {
    return defaultWebDriver;
  }

  public void setDefaultWebDriver(String defaultWebDriver) {
    this.defaultWebDriver = defaultWebDriver;
  }

  public boolean isBrowserHeadless() {
    return browserHeadless;
  }

  public void setBrowserHeadless(boolean browserHeadless) {
    this.browserHeadless = browserHeadless;
  }

  /**
   * 특정 속성값을 key로 가져오는 메서드 PropertiesUtil.get("LOCALDB_NAME") 같은 방식 지원
   *
   * @param propertyName 속성 이름 (대소문자 구분 없음, 하이픈/언더스코어 모두 지원)
   * @return 속성 값 (문자열로 변환)
   */
  public String get(String propertyName) {
    if (propertyName == null) {
      return null;
    }

    // 대소문자 구분 없이 조회
    String key = propertyName.toUpperCase().replace("_", "-").replace("-", "_");

    // 먼저 configMap에서 찾기
    Object value = configMap.get(key);
    if (value == null) {
      // 하이픈 버전으로도 시도
      key = propertyName.toUpperCase().replace("_", "-");
      value = configMap.get(key);
    }

    // 값이 있으면 문자열로 변환하여 반환
    if (value != null) {
      return value.toString();
    }

    // configMap에 없으면 필드에서 직접 가져오기 (fallback)
    return getFromFields(propertyName.toUpperCase());
  }

  /** 필드에서 직접 값 가져오기 (fallback) */
  private String getFromFields(String propertyName) {
    switch (propertyName.replace("-", "_")) {
      case "LOCALDB_NAME":
        return localdbName;
      case "LOCALDB_PATH":
        return localdbPath;
      case "MAX_RETRY_COUNT":
        return String.valueOf(maxRetryCount);
      case "TIMEOUT_SECONDS":
        return String.valueOf(timeoutSeconds);
      case "DEBUG_MODE":
        return String.valueOf(debugMode);
      case "APP_VERSION":
        return appVersion;
      case "MALL_SIGNIN_DELAY":
        return String.valueOf(mallSigninDelay);
      case "DEFAULT_WEB_DRIVER":
        return defaultWebDriver;
      case "BROWSER_HEADLESS":
        return String.valueOf(browserHeadless);
      default:
        return null;
    }
  }

  /** 설정 파일 재로드 */
  public void reloadConfig() {
    logger.info("설정 파일 재로드 시작...");
    loadConfig();
  }

  /** 모든 설정 값을 Map으로 반환 */
  public Map<String, Object> getAllConfig() {
    return new HashMap<>(configMap);
  }

  @Override
  public String toString() {
    return "JangbogoConfig{"
        + "localdbName='"
        + localdbName
        + '\''
        + ", localdbPath='"
        + localdbPath
        + '\''
        + ", maxRetryCount="
        + maxRetryCount
        + ", timeoutSeconds="
        + timeoutSeconds
        + ", debugMode="
        + debugMode
        + ", appVersion='"
        + appVersion
        + '\''
        + ", mallSigninDelay="
        + mallSigninDelay
        + ", defaultWebDriver='"
        + defaultWebDriver
        + '\''
        + ", browserHeadless="
        + browserHeadless
        + '}';
  }
}

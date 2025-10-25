package com.jiniebox.jangbogo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 장보고 프로젝트 사용자 정의 설정
 * application.yml의 jangbogo.config 값을 바인딩
 */
@Component
@ConfigurationProperties(prefix = "jangbogo.config")
public class JangbogoConfig {
    
    private String localdbName;
    private String localdbPath;
    private int maxRetryCount;
    private int timeoutSeconds;
    private boolean debugMode;
    private String appVersion;
    
    // Getters and Setters
    
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
    
    /**
     * 특정 속성값을 key로 가져오는 메서드
     * PropertiesUtil.get("LOCALDB_NAME") 같은 방식 지원
     */
    public String get(String propertyName) {
        switch (propertyName.toUpperCase()) {
            case "LOCALDB_NAME":
            case "LOCALDB-NAME":
                return localdbName;
            case "LOCALDB_PATH":
            case "LOCALDB-PATH":
                return localdbPath;
            case "MAX_RETRY_COUNT":
            case "MAX-RETRY-COUNT":
                return String.valueOf(maxRetryCount);
            case "TIMEOUT_SECONDS":
            case "TIMEOUT-SECONDS":
                return String.valueOf(timeoutSeconds);
            case "DEBUG_MODE":
            case "DEBUG-MODE":
                return String.valueOf(debugMode);
            case "APP_VERSION":
            case "APP-VERSION":
                return appVersion;
            default:
                return null;
        }
    }
    
    @Override
    public String toString() {
        return "JangbogoConfig{" +
                "localdbName='" + localdbName + '\'' +
                ", localdbPath='" + localdbPath + '\'' +
                ", maxRetryCount=" + maxRetryCount +
                ", timeoutSeconds=" + timeoutSeconds +
                ", debugMode=" + debugMode +
                ", appVersion='" + appVersion + '\'' +
                '}';
    }
}


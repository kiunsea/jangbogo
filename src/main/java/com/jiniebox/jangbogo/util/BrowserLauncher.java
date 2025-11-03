package com.jiniebox.jangbogo.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

/**
 * 브라우저 자동 실행 유틸리티
 * 서버가 준비될 때까지 대기 후 브라우저를 자동으로 실행합니다.
 */
public class BrowserLauncher {
    
    private static final Logger logger = LoggerFactory.getLogger(BrowserLauncher.class);
    private static final String APP_URL = "http://127.0.0.1:8282";
    private static final int MAX_WAIT_SECONDS = 30;
    private static final int CHECK_INTERVAL_MS = 1000;
    
    /**
     * 서버가 준비될 때까지 대기한 후 브라우저를 실행합니다.
     * 별도 스레드에서 실행되어 메인 애플리케이션 시작을 차단하지 않습니다.
     */
    public static void launchWhenReady() {
        Thread browserThread = new Thread(() -> {
            try {
                logger.info("서버 준비 대기 중...");
                
                if (waitForServerReady()) {
                    logger.info("서버 준비 완료, 브라우저 실행 중...");
                    openBrowser();
                } else {
                    logger.warn("서버 준비 시간 초과. 브라우저를 수동으로 열어주세요: {}", APP_URL);
                }
            } catch (Exception e) {
                logger.error("브라우저 실행 중 오류 발생", e);
            }
        }, "BrowserLauncher");
        
        browserThread.setDaemon(true);
        browserThread.start();
    }
    
    /**
     * 브라우저를 즉시 실행합니다 (서버 대기 없음)
     */
    public static void launch() {
        try {
            openBrowser();
        } catch (Exception e) {
            logger.error("브라우저 실행 실패", e);
        }
    }
    
    /**
     * 서버가 준비될 때까지 대기합니다.
     * @return 서버 준비 완료 시 true, 타임아웃 시 false
     */
    private static boolean waitForServerReady() {
        int attempts = 0;
        int maxAttempts = MAX_WAIT_SECONDS * 1000 / CHECK_INTERVAL_MS;
        
        while (attempts < maxAttempts) {
            if (isServerReady()) {
                return true;
            }
            
            try {
                Thread.sleep(CHECK_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            
            attempts++;
        }
        
        return false;
    }
    
    /**
     * 서버가 응답하는지 확인합니다.
     * @return 서버가 응답하면 true, 아니면 false
     */
    private static boolean isServerReady() {
        try {
            URL url = new URL(APP_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(1000);
            connection.setReadTimeout(1000);
            
            int responseCode = connection.getResponseCode();
            connection.disconnect();
            
            return responseCode == 200 || responseCode == 302;
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * 기본 브라우저로 애플리케이션 URL을 엽니다.
     */
    private static void openBrowser() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(APP_URL));
                logger.info("브라우저 실행 완료: {}", APP_URL);
            } else {
                logger.warn("Desktop.browse가 지원되지 않습니다. URL을 수동으로 열어주세요: {}", APP_URL);
                
                // Windows에서 cmd를 통해 브라우저 열기 시도
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + APP_URL);
                    logger.info("rundll32로 브라우저 실행 완료");
                }
            }
        } catch (Exception e) {
            logger.error("브라우저 실행 실패: {}", e.getMessage());
        }
    }
}


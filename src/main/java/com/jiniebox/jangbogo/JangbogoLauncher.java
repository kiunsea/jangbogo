package com.jiniebox.jangbogo;

import com.jiniebox.jangbogo.sys.TrayApplication;
import com.jiniebox.jangbogo.util.BrowserLauncher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Arrays;
import java.util.List;

/**
 * Jangbogo 애플리케이션 런처
 * 실행 모드에 따라 다른 동작을 수행합니다.
 * 
 * 실행 모드:
 * - --service: 서비스 모드 (브라우저 자동 실행 안 함, 트레이 아이콘 없음)
 * - --tray: 트레이 모드 (브라우저 자동 실행 + 트레이 아이콘)
 * - --install-complete: 설치 완료 모드 (브라우저 자동 실행 + 트레이 아이콘)
 * - 인자 없음: 일반 실행 (개발 모드, 브라우저 자동 실행)
 */
public class JangbogoLauncher {
    
    private static final Logger logger = LoggerFactory.getLogger(JangbogoLauncher.class);
    
    private static final String MODE_SERVICE = "--service";
    private static final String MODE_TRAY = "--tray";
    private static final String MODE_INSTALL_COMPLETE = "--install-complete";
    
    public static void main(String[] args) {
        // Spring Boot 시작 전에 필요한 디렉토리 생성 (로깅 시스템 초기화 전)
        createRequiredDirectories();
        
        System.out.println("Jangbogo 애플리케이션 시작 - 인자: " + Arrays.toString(args));
        
        // 실행 모드 결정
        ExecutionMode mode = determineExecutionMode(args);
        System.out.println("실행 모드: " + mode);
        
        // 실행 모드에 따른 처리
        switch (mode) {
            case SERVICE:
                launchServiceMode(args);
                break;
            case TRAY:
                launchTrayMode(args);
                break;
            case INSTALL_COMPLETE:
                launchInstallCompleteMode(args);
                break;
            case NORMAL:
            default:
                launchNormalMode(args);
                break;
        }
    }
    
    /**
     * 애플리케이션 실행에 필요한 디렉토리 생성 (Spring Boot 시작 전)
     */
    private static void createRequiredDirectories() {
        String[] requiredDirs = {"db", "logs", "exports"};
        for (String dir : requiredDirs) {
            java.io.File directory = new java.io.File(dir);
            if (!directory.exists()) {
                boolean created = directory.mkdirs();
                if (created) {
                    System.out.println("✓ 디렉토리 생성됨: " + directory.getAbsolutePath());
                }
            }
        }
    }
    
    /**
     * 실행 모드를 결정합니다.
     */
    private static ExecutionMode determineExecutionMode(String[] args) {
        List<String> argList = Arrays.asList(args);
        
        if (argList.contains(MODE_SERVICE)) {
            return ExecutionMode.SERVICE;
        } else if (argList.contains(MODE_TRAY)) {
            return ExecutionMode.TRAY;
        } else if (argList.contains(MODE_INSTALL_COMPLETE)) {
            return ExecutionMode.INSTALL_COMPLETE;
        } else {
            return ExecutionMode.NORMAL;
        }
    }
    
    /**
     * 서비스 모드로 실행 (OS 재시작 시 자동 실행)
     * - Spring Boot 애플리케이션 시작
     * - 브라우저 자동 실행 안 함
     * - 트레이 아이콘 없음
     */
    private static void launchServiceMode(String[] args) {
        logger.info("서비스 모드로 실행 - 브라우저 자동 실행 안 함");
        
        // Spring Boot 애플리케이션 시작
        String[] filteredArgs = filterModeArguments(args);
        SpringApplication.run(JangbogoApplication.class, filteredArgs);
        
        logger.info("서비스 모드 실행 완료");
    }
    
    /**
     * 트레이 모드로 실행 (사용자가 직접 실행)
     * - Spring Boot 애플리케이션 시작
     * - 트레이 아이콘 표시
     * - 브라우저 자동 실행
     */
    private static void launchTrayMode(String[] args) {
        logger.info("트레이 모드로 실행 - 브라우저 자동 실행 + 트레이 아이콘");
        
        // 트레이 아이콘 초기화
        TrayApplication.initialize();
        
        // Spring Boot 애플리케이션 시작
        String[] filteredArgs = filterModeArguments(args);
        ConfigurableApplicationContext context = SpringApplication.run(JangbogoApplication.class, filteredArgs);
        
        // 브라우저 자동 실행
        BrowserLauncher.launchWhenReady();
        
        logger.info("트레이 모드 실행 완료");
    }
    
    /**
     * 설치 완료 모드로 실행
     * - 트레이 아이콘만 표시
     * - 브라우저 자동 실행
     * - Spring Boot는 이미 서비스로 실행 중이므로 시작하지 않음
     */
    private static void launchInstallCompleteMode(String[] args) {
        logger.info("설치 완료 모드로 실행 - 트레이 아이콘 + 브라우저 자동 실행");
        
        try {
            // 트레이 아이콘 초기화
            TrayApplication.initialize();
            
            // 서버가 시작될 때까지 대기 후 브라우저 실행
            BrowserLauncher.launchWhenReady();
            
            logger.info("설치 완료 모드 실행 완료 - 트레이 애플리케이션 대기 중");
            
            // 트레이 애플리케이션이 종료될 때까지 대기
            Thread.currentThread().join();
            
        } catch (InterruptedException e) {
            logger.warn("트레이 애플리케이션 대기 중 인터럽트 발생", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("설치 완료 모드 실행 중 오류", e);
        }
    }
    
    /**
     * 일반 모드로 실행 (개발 모드)
     * - Spring Boot 애플리케이션 시작
     * - 브라우저 자동 실행
     */
    private static void launchNormalMode(String[] args) {
        logger.info("일반 모드로 실행 - 브라우저 자동 실행");
        
        // Spring Boot 애플리케이션 시작
        SpringApplication.run(JangbogoApplication.class, args);
        
        // 브라우저 자동 실행
        BrowserLauncher.launchWhenReady();
        
        logger.info("일반 모드 실행 완료");
    }
    
    /**
     * 모드 인자를 제거한 나머지 인자를 반환합니다.
     */
    private static String[] filterModeArguments(String[] args) {
        return Arrays.stream(args)
                .filter(arg -> !arg.equals(MODE_SERVICE) 
                        && !arg.equals(MODE_TRAY) 
                        && !arg.equals(MODE_INSTALL_COMPLETE))
                .toArray(String[]::new);
    }
    
    /**
     * 실행 모드 열거형
     */
    private enum ExecutionMode {
        SERVICE,            // 서비스 모드 (백그라운드)
        TRAY,              // 트레이 모드 (사용자 실행)
        INSTALL_COMPLETE,  // 설치 완료 모드
        NORMAL             // 일반 모드 (개발)
    }
}


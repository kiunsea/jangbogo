package com.jiniebox.jangbogo;

import com.jiniebox.jangbogo.util.BrowserLauncher;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;

/**
 * Jangbogo 애플리케이션 런처 실행 모드에 따라 다른 동작을 수행합니다.
 *
 * <p>실행 모드: - --service: 서비스 모드 (WinSW 등 OS 서비스가 기동, 브라우저 자동 실행 안 함) - 인자 없음: 일반 실행 (개발 모드, 브라우저 자동
 * 실행)
 *
 * <p>배포 경로의 트레이 UI는 PowerShell 스크립트(`Jangbogo-Tray.ps1`)가 담당하므로 Java 기반 트레이/설치완료 모드는 제공하지 않습니다.
 */
public class JangbogoLauncher {

  private static final Logger logger = LoggerFactory.getLogger(JangbogoLauncher.class);

  private static final String MODE_SERVICE = "--service";

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
      case NORMAL:
      default:
        launchNormalMode(args);
        break;
    }
  }

  /** 애플리케이션 실행에 필요한 디렉토리 생성 (Spring Boot 시작 전) */
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

  /** 실행 모드를 결정합니다. */
  private static ExecutionMode determineExecutionMode(String[] args) {
    List<String> argList = Arrays.asList(args);

    if (argList.contains(MODE_SERVICE)) {
      return ExecutionMode.SERVICE;
    } else {
      return ExecutionMode.NORMAL;
    }
  }

  /** 서비스 모드로 실행 (OS 재시작 시 자동 실행) - Spring Boot 애플리케이션 시작 - 브라우저 자동 실행 안 함 - 트레이 아이콘 없음 */
  private static void launchServiceMode(String[] args) {
    logger.info("서비스 모드로 실행 - 브라우저 자동 실행 안 함");

    // Spring Boot 애플리케이션 시작
    String[] filteredArgs = filterModeArguments(args);
    SpringApplication.run(JangbogoApplication.class, filteredArgs);

    logger.info("서비스 모드 실행 완료");
  }

  /** 일반 모드로 실행 (개발 모드) - Spring Boot 애플리케이션 시작 - 브라우저 자동 실행 */
  private static void launchNormalMode(String[] args) {
    logger.info("일반 모드로 실행 - 브라우저 자동 실행");

    // Spring Boot 애플리케이션 시작
    SpringApplication.run(JangbogoApplication.class, args);

    // 브라우저 자동 실행
    BrowserLauncher.launchWhenReady();

    logger.info("일반 모드 실행 완료");
  }

  /** 모드 인자를 제거한 나머지 인자를 반환합니다. */
  private static String[] filterModeArguments(String[] args) {
    return Arrays.stream(args).filter(arg -> !arg.equals(MODE_SERVICE)).toArray(String[]::new);
  }

  /** 실행 모드 열거형 */
  private enum ExecutionMode {
    SERVICE, // 서비스 모드 (백그라운드)
    NORMAL // 일반 모드 (개발)
  }
}

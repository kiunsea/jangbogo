package com.jiniebox.jangbogo.svc.util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.Alert;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

/**
 * Selenium WebDriver 스크린샷 저장 + 보관 기간 관리.
 *
 * <p>저장 경로: {@code logs/screenshots/yyyyMMdd/{mall}-{HHmmss}-{nano}.png} (기준 폴더는 시스템 프로퍼티 {@code
 * jangbogo.screenshot.dir} 로 덮어쓸 수 있다 — 테스트 격리용)
 *
 * <p>보관 정책: {@link #cleanupOldScreenshots(int)} 호출 시 N일 이전 폴더 일괄 삭제.
 *
 * @author KIUNSEA
 */
public final class ScreenshotUtil {

  private static final Logger logger = LogManager.getLogger(ScreenshotUtil.class);

  private static final String BASE_DIR_PROPERTY = "jangbogo.screenshot.dir";
  private static final String DEFAULT_BASE_DIR = "logs/screenshots";
  private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
  private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HHmmss");

  private ScreenshotUtil() {}

  /** 스크린샷 기준 폴더. 테스트가 기준선 트리를 건드리지 않도록 시스템 프로퍼티로 덮어쓸 수 있다. */
  private static String baseDir() {
    String override = System.getProperty(BASE_DIR_PROPERTY);
    return (override == null || override.isBlank()) ? DEFAULT_BASE_DIR : override;
  }

  /**
   * 열려 있는 JS 대화상자(alert/confirm/prompt)가 있으면 <b>텍스트를 읽어 반환하고 닫는다.</b>
   *
   * <p>왜 필요한가 — Chrome 은 W3C 기본값 {@code unhandledPromptBehavior = "dismiss and notify"} 로 동작한다(이
   * 프로젝트는 이 capability 를 따로 지정하지 않는다). 그래서 대화상자가 떠 있는 상태에서 {@code getCurrentUrl()} 같은 평범한 명령을 먼저
   * 호출하면 드라이버가 <b>대화상자를 먼저 닫아 버리고</b> {@code UnhandledAlertException} 을 던진다. 그 결과 실패 원인이 적혀 있는
   * <b>대화상자 문구가 통째로 사라지고</b> URL 도 함께 유실된다. 스크린샷을 찍어도 이미 대화상자는 화면에 없다.
   *
   * <p>그래서 컨텍스트 수집의 <b>맨 처음</b>에 이 메서드를 불러야 한다. 문구를 확보한 뒤에야 URL·타이틀· 스크린샷이 정상적으로 얻어진다.
   *
   * <p>닫을 때는 {@code accept()} 가 아니라 {@code dismiss()} 를 쓴다. confirm 대화상자에서 accept 는 "확인"을 누르는 것이라
   * 뜻하지 않은 동작(주문 취소 등)을 유발할 수 있다. 인자가 하나뿐인 alert 에서는 dismiss 도 accept 와 같게 처리된다.
   *
   * @param driver Selenium WebDriver (null 허용)
   * @return 대화상자 문구. 대화상자가 없거나 처리할 수 없으면 null
   */
  public static String consumePendingAlert(WebDriver driver) {
    if (driver == null) {
      return null;
    }
    try {
      Alert alert = driver.switchTo().alert();
      if (alert == null) {
        return null;
      }
      String text;
      try {
        text = alert.getText();
      } catch (Exception e) {
        text = null;
      }
      try {
        alert.dismiss();
      } catch (Exception e) {
        logger.debug("대화상자 닫기 실패(무시): {}", e.getMessage());
      }
      logger.warn("수집 중 브라우저 대화상자 감지 — 문구: {}", text);
      return text;
    } catch (Throwable ignore) {
      // NoAlertPresentException 이 정상 경로다. 그 외 드라이버 미지원도 조용히 넘긴다.
      return null;
    }
  }

  /**
   * 현재 WebDriver 화면을 PNG로 저장한다.
   *
   * @param driver Selenium WebDriver (TakesScreenshot 미지원이면 null 반환)
   * @param mallName 파일명에 포함할 쇼핑몰 식별자
   * @return 저장된 파일 경로 (실패 시 null)
   */
  public static String capture(WebDriver driver, String mallName) {
    if (driver == null || !(driver instanceof TakesScreenshot)) {
      return null;
    }

    // 대화상자가 떠 있으면 getScreenshotAs 가 UnhandledAlertException 으로 실패한다.
    // 스크린샷이 가장 필요한 상황(수집 실패 직후)이 바로 그 상황이므로, 먼저 닫고 찍는다.
    // 통상은 CollectStep.wrap 이 이미 처리한 뒤라 여기서는 null 이 돌아온다 — 직접 호출자를 위한 방어다.
    consumePendingAlert(driver);

    try {
      LocalDateTime now = LocalDateTime.now();
      String dayDir = DAY_FMT.format(now.toLocalDate());
      String time = TIME_FMT.format(now);
      String safeMall =
          (mallName == null || mallName.isBlank())
              ? "unknown"
              : mallName.replaceAll("[^A-Za-z0-9_\\-]", "_");
      String fileName = safeMall + "-" + time + "-" + System.nanoTime() + ".png";

      Path dir = Path.of(baseDir(), dayDir);
      Files.createDirectories(dir);
      Path target = dir.resolve(fileName);

      File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
      Files.copy(src.toPath(), target, StandardCopyOption.REPLACE_EXISTING);

      // 원본 임시 파일 정리
      try {
        Files.deleteIfExists(src.toPath());
      } catch (Exception ignore) {
      }

      String result = target.toString().replace('\\', '/');
      logger.debug("스크린샷 저장: {}", result);
      return result;
    } catch (Exception e) {
      logger.warn("스크린샷 저장 실패: {}", e.getMessage());
      return null;
    }
  }

  /**
   * 보관 기한이 지난 스크린샷 일자 폴더를 삭제한다.
   *
   * @param keepDays 보관할 일수 (예: 30)
   */
  public static void cleanupOldScreenshots(int keepDays) {
    Path base = Path.of(baseDir());
    if (!Files.isDirectory(base)) {
      return;
    }
    LocalDate threshold = LocalDate.now().minusDays(keepDays);

    try (Stream<Path> children = Files.list(base)) {
      children
          .filter(Files::isDirectory)
          .forEach(
              dir -> {
                String name = dir.getFileName().toString();
                try {
                  LocalDate folderDate = LocalDate.parse(name, DAY_FMT);
                  if (folderDate.isBefore(threshold)) {
                    deleteRecursively(dir);
                    logger.info("오래된 스크린샷 폴더 삭제: {}", dir);
                  }
                } catch (Exception ignore) {
                  // 폴더명이 yyyyMMdd 형식이 아니면 무시
                }
              });
    } catch (Exception e) {
      logger.warn("스크린샷 보관기간 정리 중 오류: {}", e.getMessage());
    }
  }

  private static void deleteRecursively(Path dir) {
    try (Stream<Path> walk = Files.walk(dir)) {
      walk.sorted((a, b) -> b.getNameCount() - a.getNameCount())
          .forEach(
              p -> {
                try {
                  Files.deleteIfExists(p);
                } catch (Exception ignore) {
                }
              });
    } catch (Exception e) {
      logger.warn("폴더 삭제 실패 {}: {}", dir, e.getMessage());
    }
  }

  /** 현재 보관 기준 시각(테스트 가독용). */
  public static long currentEpochMs() {
    return LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
  }
}

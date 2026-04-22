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
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

/**
 * Selenium WebDriver 스크린샷 저장 + 보관 기간 관리.
 *
 * <p>저장 경로: logs/screenshots/yyyyMMdd/{mall}-{HHmmss}-{nano}.png
 *
 * <p>보관 정책: {@link #cleanupOldScreenshots(int)} 호출 시 N일 이전 폴더 일괄 삭제.
 *
 * @author KIUNSEA
 */
public final class ScreenshotUtil {

  private static final Logger logger = LogManager.getLogger(ScreenshotUtil.class);

  private static final String BASE_DIR = "logs/screenshots";
  private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
  private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HHmmss");

  private ScreenshotUtil() {}

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
    try {
      LocalDateTime now = LocalDateTime.now();
      String dayDir = DAY_FMT.format(now.toLocalDate());
      String time = TIME_FMT.format(now);
      String safeMall =
          (mallName == null || mallName.isBlank())
              ? "unknown"
              : mallName.replaceAll("[^A-Za-z0-9_\\-]", "_");
      String fileName = safeMall + "-" + time + "-" + System.nanoTime() + ".png";

      Path dir = Path.of(BASE_DIR, dayDir);
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
    Path base = Path.of(BASE_DIR);
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

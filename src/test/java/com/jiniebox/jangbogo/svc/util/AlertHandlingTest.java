package com.jiniebox.jangbogo.svc.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.jiniebox.jangbogo.svc.CollectException;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.openqa.selenium.Alert;
import org.openqa.selenium.NoAlertPresentException;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

/**
 * 수집 실패 컨텍스트 수집 시 브라우저 대화상자 처리 검증 (Phase 3-7).
 *
 * <p>배경: Chrome 은 W3C 기본값 {@code unhandledPromptBehavior = "dismiss and notify"} 로 동작한다. 대화상자가 떠 있을
 * 때 {@code getCurrentUrl()} 같은 명령을 먼저 호출하면 드라이버가 대화상자를 닫아 버린 뒤 예외를 던지고, {@code CollectStep.wrap} 의
 * {@code safe()} 가 그 예외를 삼켜 <b>URL 은 null 이 되고 실패 원인이 적힌 대화상자 문구는 통째로 사라진다.</b> 그래서 대화상자를 <b>가장
 * 먼저</b> 읽고 닫아야 한다.
 *
 * <p>브라우저·네트워크·DB 를 쓰지 않는다. Selenium 인터페이스를 Mockito 로 세운다.
 *
 * @author KIUNSEA
 */
class AlertHandlingTest {

  private static final String SCREENSHOT_DIR_PROPERTY = "jangbogo.screenshot.dir";

  private String previousScreenshotDir;

  @AfterEach
  void restoreScreenshotDir() {
    if (previousScreenshotDir == null) {
      System.clearProperty(SCREENSHOT_DIR_PROPERTY);
    } else {
      System.setProperty(SCREENSHOT_DIR_PROPERTY, previousScreenshotDir);
    }
  }

  /** 스크린샷 기준 폴더를 테스트 전용 임시 폴더로 돌린다 (개발 트리의 logs/ 를 건드리지 않기 위해). */
  private void redirectScreenshotDir(Path dir) {
    previousScreenshotDir = System.getProperty(SCREENSHOT_DIR_PROPERTY);
    System.setProperty(SCREENSHOT_DIR_PROPERTY, dir.toString());
  }

  /** alert 이 떠 있는 드라이버 목. 첫 조회에서만 alert 을 주고, 이후에는 실제처럼 '없음'을 던진다. */
  private static WebDriver driverWithAlert(Alert alert) {
    WebDriver driver = mock(WebDriver.class, withSettings().extraInterfaces(TakesScreenshot.class));
    WebDriver.TargetLocator locator = mock(WebDriver.TargetLocator.class);
    when(driver.switchTo()).thenReturn(locator);
    when(locator.alert()).thenReturn(alert).thenThrow(new NoAlertPresentException());
    return driver;
  }

  private static WebDriver driverWithoutAlert() {
    WebDriver driver = mock(WebDriver.class, withSettings().extraInterfaces(TakesScreenshot.class));
    WebDriver.TargetLocator locator = mock(WebDriver.TargetLocator.class);
    when(driver.switchTo()).thenReturn(locator);
    when(locator.alert()).thenThrow(new NoAlertPresentException());
    return driver;
  }

  // ---------------------------------------------------------------- consumePendingAlert

  @Test
  @DisplayName("대화상자가 떠 있으면 문구를 반환하고 닫는다")
  void consumePendingAlertReturnsTextAndDismisses() {
    Alert alert = mock(Alert.class);
    when(alert.getText()).thenReturn("로그인 정보가 올바르지 않습니다.");
    WebDriver driver = driverWithAlert(alert);

    String text = ScreenshotUtil.consumePendingAlert(driver);

    assertEquals("로그인 정보가 올바르지 않습니다.", text);
    verify(alert).dismiss();
  }

  @Test
  @DisplayName("confirm 대화상자는 accept 가 아니라 dismiss 로 닫는다")
  void consumePendingAlertDismissesRatherThanAccepts() {
    Alert alert = mock(Alert.class);
    when(alert.getText()).thenReturn("주문을 취소하시겠습니까?");
    WebDriver driver = driverWithAlert(alert);

    ScreenshotUtil.consumePendingAlert(driver);

    // accept() 는 "확인"을 누르는 것이다. 실패 진단을 하려다 실제 동작을 일으켜서는 안 된다.
    verify(alert).dismiss();
    verify(alert, org.mockito.Mockito.never()).accept();
  }

  @Test
  @DisplayName("대화상자가 없으면 null 을 반환하고 예외를 밖으로 내지 않는다")
  void consumePendingAlertReturnsNullWhenAbsent() {
    assertNull(ScreenshotUtil.consumePendingAlert(driverWithoutAlert()));
  }

  @Test
  @DisplayName("driver 가 null 이어도 안전하다")
  void consumePendingAlertHandlesNullDriver() {
    assertNull(ScreenshotUtil.consumePendingAlert(null));
  }

  @Test
  @DisplayName("문구 조회가 실패해도 대화상자는 닫고 null 을 반환한다")
  void consumePendingAlertStillDismissesWhenTextUnavailable() {
    Alert alert = mock(Alert.class);
    when(alert.getText()).thenThrow(new RuntimeException("text unavailable"));
    WebDriver driver = driverWithAlert(alert);

    assertNull(ScreenshotUtil.consumePendingAlert(driver));
    verify(alert).dismiss();
  }

  // ---------------------------------------------------------------- capture

  @Test
  @DisplayName("대화상자가 떠 있어도 스크린샷이 저장된다")
  void captureSucceedsWhileAlertIsOpen(@TempDir Path tempDir) throws Exception {
    redirectScreenshotDir(tempDir.resolve("screenshots"));

    File source = tempDir.resolve("shot.png").toFile();
    Files.writeString(source.toPath(), "fake-png", StandardCharsets.UTF_8);

    Alert alert = mock(Alert.class);
    when(alert.getText()).thenReturn("세션이 만료되었습니다.");
    WebDriver driver = driverWithAlert(alert);
    when(((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE)).thenReturn(source);

    String saved = ScreenshotUtil.capture(driver, "ssg");

    assertNotNull(saved, "대화상자가 떠 있을 때야말로 스크린샷이 필요하다 — null 이면 안 된다.");
    assertTrue(Files.exists(Path.of(saved)), "저장 경로에 파일이 없다: " + saved);
    assertTrue(saved.contains("ssg-"), "파일명에 몰 식별자가 없다: " + saved);
    verify(alert).dismiss();
  }

  @Test
  @DisplayName("몰 이름의 경로 문자는 파일명에서 제거된다")
  void captureSanitizesMallName(@TempDir Path tempDir) throws Exception {
    redirectScreenshotDir(tempDir.resolve("screenshots"));

    File source = tempDir.resolve("shot.png").toFile();
    Files.writeString(source.toPath(), "fake-png", StandardCharsets.UTF_8);

    WebDriver driver = driverWithoutAlert();
    when(((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE)).thenReturn(source);

    String saved = ScreenshotUtil.capture(driver, "../../etc");

    assertNotNull(saved);
    assertFalse(saved.contains(".."), "몰 이름의 상위 경로 표기가 그대로 남았다: " + saved);
  }

  // ---------------------------------------------------------------- CollectStep.wrap

  @Test
  @DisplayName("wrap 은 대화상자를 URL 조회보다 먼저 처리한다")
  void wrapConsumesAlertBeforeQueryingUrl(@TempDir Path tempDir) throws Exception {
    redirectScreenshotDir(tempDir.resolve("screenshots"));

    File source = tempDir.resolve("shot.png").toFile();
    Files.writeString(source.toPath(), "fake-png", StandardCharsets.UTF_8);

    Alert alert = mock(Alert.class);
    when(alert.getText()).thenReturn("비밀번호가 5회 틀렸습니다.");
    WebDriver driver = driverWithAlert(alert);
    when(driver.getCurrentUrl()).thenReturn("https://www.ssg.com/login");
    when(driver.getTitle()).thenReturn("로그인");
    when(((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE)).thenReturn(source);

    CollectException e =
        CollectStep.wrap(driver, "ssg", "signin", null, new NoSuchElementException("no such"));

    // 순서가 이 수정의 핵심이다. URL 을 먼저 물으면 드라이버가 대화상자를 닫아 문구가 사라진다.
    InOrder order = inOrder(alert, driver);
    order.verify(alert).dismiss();
    order.verify(driver).getCurrentUrl();

    assertTrue(
        e.getMessage().contains("비밀번호가 5회 틀렸습니다."), "대화상자 문구가 예외 메시지에 남지 않았다: " + e.getMessage());
    assertEquals("https://www.ssg.com/login", e.getCurrentUrl(), "대화상자를 먼저 닫았으므로 URL 도 남아야 한다.");
    assertEquals("로그인", e.getPageTitle());
    assertNotNull(e.getScreenshotPath(), "스크린샷 경로가 비었다.");
  }

  @Test
  @DisplayName("여러 줄 대화상자 문구는 한 줄로 접혀 기록된다")
  void wrapFlattensMultilineAlertText(@TempDir Path tempDir) throws Exception {
    redirectScreenshotDir(tempDir.resolve("screenshots"));

    Alert alert = mock(Alert.class);
    when(alert.getText()).thenReturn("오류가 발생했습니다.\n다시 시도해 주세요.");
    WebDriver driver = driverWithAlert(alert);
    when(((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE))
        .thenThrow(new RuntimeException("no screenshot"));

    CollectException e =
        CollectStep.wrap(driver, "oasis", "navigatePurchased", null, new RuntimeException("boom"));

    assertTrue(
        e.getMessage().contains("오류가 발생했습니다. 다시 시도해 주세요."),
        "여러 줄 문구가 한 줄로 접히지 않았다: " + e.getMessage());
    assertFalse(e.getMessage().contains("\n"), "예외 메시지에 줄바꿈이 남았다.");
  }

  @Test
  @DisplayName("대화상자가 없으면 메시지에 alert 구간을 붙이지 않는다")
  void wrapOmitsAlertSegmentWhenNoAlert(@TempDir Path tempDir) {
    redirectScreenshotDir(tempDir.resolve("screenshots"));

    WebDriver driver = driverWithoutAlert();
    when(driver.getCurrentUrl()).thenReturn("https://www.oasis.co.kr/myPage/orderList");
    when(((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE))
        .thenThrow(new RuntimeException("no screenshot"));

    CollectException e =
        CollectStep.wrap(
            driver, "oasis", "navigatePurchased", "div.x", new RuntimeException("boom"));

    assertFalse(e.getMessage().contains("alert="), "대화상자가 없는데 alert 구간이 붙었다: " + e.getMessage());
    assertTrue(e.getMessage().contains("target=div.x"));
    assertEquals("https://www.oasis.co.kr/myPage/orderList", e.getCurrentUrl());
  }
}

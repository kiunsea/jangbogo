package com.jiniebox.jangbogo.svc.mall;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.List;
import org.json.simple.JSONArray;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * Emart 영수증 두 원본(이마트 / 트레이더스)의 부분 실패 격리 검증 (B-2).
 *
 * <p>실측 사례: 트레이더스 영수증 페이지가 응답하지 않아 {@code TimeoutException} 이 났고, 그 예외가 그대로 올라가며 <b>이미 모아 둔 이마트 결과가
 * 통째로 버려졌다.</b> 두 호출이 한 줄로 이어져 있어 {@code resJsonArr} 이 반환되지 못했기 때문이다.
 *
 * <p>브라우저·네트워크·DB 를 쓰지 않는다. Selenium 인터페이스를 Mockito 로 세운다.
 *
 * @author KIUNSEA
 */
class EmartReceiptIsolationTest {

  private static final String EMART_URL = "https://eapp.emart.com/myemart/jornalV3.do?jornalGbn=E";
  private static final String TRADERS_URL =
      "https://eapp.emart.com/myemart/jornalV3.do?jornalGbn=T";

  private static final By RECEIPT_LIST = By.id("receipt_list");
  private static final By PREV_MONTH = By.cssSelector(".btn-prev-month");

  private final Emart emart = new Emart("test-id", "test-pass");

  /**
   * 영수증이 하나도 없는 정상 페이지를 흉내 낸다.
   *
   * <p>{@code receipt_list} 는 있는데 {@code li} 가 없고 이전 달 버튼도 없으면, 수집기는 루프를 빠져나와 빈 결과를 정상 반환한다 — 예외가
   * 아니다.
   */
  private static void stubEmptyButHealthyPage(WebDriver driver) {
    WebElement list = mock(WebElement.class);
    when(list.findElements(By.tagName("li"))).thenReturn(List.of());
    when(driver.findElement(RECEIPT_LIST)).thenReturn(list);
    when(driver.findElement(PREV_MONTH)).thenThrow(new NoSuchElementException("더 이상 없음"));
  }

  /** 특정 URL 로의 이동만 실패하는 driver. */
  private static WebDriver driverFailingOn(String failingUrl, RuntimeException failure) {
    WebDriver driver =
        mock(WebDriver.class, withSettings().extraInterfaces(JavascriptExecutor.class));
    WebDriver.Navigation navigation = mock(WebDriver.Navigation.class);

    when(driver.getWindowHandle()).thenReturn("main");
    when(driver.navigate()).thenReturn(navigation);
    org.mockito.Mockito.doThrow(failure).when(navigation).to(failingUrl);

    stubEmptyButHealthyPage(driver);
    return driver;
  }

  @Test
  @DisplayName("트레이더스가 죽어도 이마트 쪽은 시도되고 예외가 올라가지 않는다")
  void tradersFailureDoesNotAbortEmart() {
    WebDriver driver = driverFailingOn(TRADERS_URL, new TimeoutException("트레이더스 응답 없음"));

    JSONArray result = emart.navigateReceipt(driver);

    // 한쪽이라도 정상 완주했으면 실패가 아니다. 이전에는 여기서 예외가 올라갔다.
    assertEquals(0, result.size(), "이번 픽스처는 영수증이 없는 정상 페이지라 0건이 맞다.");
    verify(driver.navigate()).to(EMART_URL);
    verify(driver.navigate()).to(TRADERS_URL);
  }

  @Test
  @DisplayName("이마트가 죽어도 트레이더스는 반드시 시도된다")
  void emartFailureDoesNotSkipTraders() {
    WebDriver driver = driverFailingOn(EMART_URL, new TimeoutException("이마트 응답 없음"));

    emart.navigateReceipt(driver);

    // 앞쪽 실패가 뒤쪽을 건너뛰게 하면 안 된다 — 그것이 이 구조의 핵심이다.
    verify(driver.navigate()).to(TRADERS_URL);
  }

  @Test
  @DisplayName("둘 다 죽으면 첫 실패를 그대로 올린다")
  void bothFailuresPropagateTheFirst() {
    TimeoutException emartFailure = new TimeoutException("이마트 응답 없음");
    WebDriver driver =
        mock(WebDriver.class, withSettings().extraInterfaces(JavascriptExecutor.class));
    WebDriver.Navigation navigation = mock(WebDriver.Navigation.class);
    when(driver.getWindowHandle()).thenReturn("main");
    when(driver.navigate()).thenReturn(navigation);
    org.mockito.Mockito.doThrow(emartFailure).when(navigation).to(EMART_URL);
    org.mockito.Mockito.doThrow(new TimeoutException("트레이더스 응답 없음"))
        .when(navigation)
        .to(TRADERS_URL);

    RuntimeException thrown =
        assertThrows(RuntimeException.class, () -> emart.navigateReceipt(driver));

    assertSame(emartFailure, thrown, "먼저 만난 실패를 올려야 컨텍스트가 맞는다.");
    verify(driver.navigate()).to(EMART_URL);
    verify(driver.navigate()).to(TRADERS_URL);
  }

  @Test
  @DisplayName("둘 다 정상이면 예외 없이 결과를 돌려준다")
  void bothHealthyReturnsResult() {
    WebDriver driver =
        mock(WebDriver.class, withSettings().extraInterfaces(JavascriptExecutor.class));
    when(driver.getWindowHandle()).thenReturn("main");
    when(driver.navigate()).thenReturn(mock(WebDriver.Navigation.class));
    stubEmptyButHealthyPage(driver);

    JSONArray result = emart.navigateReceipt(driver);

    assertTrue(result.isEmpty());
  }
}

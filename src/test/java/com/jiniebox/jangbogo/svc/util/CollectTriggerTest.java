package com.jiniebox.jangbogo.svc.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 요청 주체별 대기 상한 검증 (Phase 5-19).
 *
 * <p>즉시수집이 스케줄러와 같은 상한(300 초)을 쓰면 안 되는 이유는 값이 커서가 아니라 <b>기다리는 주체가 다르기 때문</b>이다. 스케줄러 뒤에는 아무도 없지만
 * 즉시수집 뒤에는 사람이 응답을 기다린다. 게다가 선택한 몰을 하나씩 도는 루프라 상한이 곱해진다.
 *
 * <p>브라우저·네트워크·DB 를 쓰지 않는다.
 *
 * @author KIUNSEA
 */
class CollectTriggerTest {

  @AfterEach
  void clearProperties() {
    System.clearProperty(BrowserConcurrencyLimiter.TIMEOUT_PROPERTY);
    System.clearProperty(BrowserConcurrencyLimiter.IMMEDIATE_TIMEOUT_PROPERTY);
  }

  @Test
  @DisplayName("즉시수집 상한은 스케줄러보다 짧다")
  void immediateWaitsLessThanScheduled() {
    assertTrue(
        CollectTrigger.IMMEDIATE.acquireTimeoutMillis()
            < CollectTrigger.SCHEDULED.acquireTimeoutMillis(),
        "즉시수집이 스케줄러만큼 기다리면 동기 응답이 몰 수만큼 매달린다.");
  }

  @Test
  @DisplayName("기본값은 스케줄러 300 초 · 즉시수집 5 초")
  void defaults() {
    assertEquals(
        BrowserConcurrencyLimiter.DEFAULT_TIMEOUT_SECONDS * 1000L,
        CollectTrigger.SCHEDULED.acquireTimeoutMillis());
    assertEquals(
        BrowserConcurrencyLimiter.DEFAULT_IMMEDIATE_TIMEOUT_SECONDS * 1000L,
        CollectTrigger.IMMEDIATE.acquireTimeoutMillis());
  }

  @Test
  @DisplayName("설정으로 각각 따로 바꿀 수 있다")
  void propertiesOverrideEachSideIndependently() {
    System.setProperty(BrowserConcurrencyLimiter.TIMEOUT_PROPERTY, "120");
    System.setProperty(BrowserConcurrencyLimiter.IMMEDIATE_TIMEOUT_PROPERTY, "3");

    assertEquals(120_000L, CollectTrigger.SCHEDULED.acquireTimeoutMillis());
    assertEquals(3_000L, CollectTrigger.IMMEDIATE.acquireTimeoutMillis());
  }

  @Test
  @DisplayName("0 은 유효한 값이다 — 기다리지 않는다는 뜻")
  void zeroMeansDoNotWait() {
    System.setProperty(BrowserConcurrencyLimiter.IMMEDIATE_TIMEOUT_PROPERTY, "0");

    assertEquals(0L, CollectTrigger.IMMEDIATE.acquireTimeoutMillis());
  }

  @Test
  @DisplayName("음수나 숫자가 아니면 기본값으로 돌아간다")
  void invalidValuesFallBack() {
    System.setProperty(BrowserConcurrencyLimiter.IMMEDIATE_TIMEOUT_PROPERTY, "-1");
    assertEquals(
        BrowserConcurrencyLimiter.DEFAULT_IMMEDIATE_TIMEOUT_SECONDS * 1000L,
        CollectTrigger.IMMEDIATE.acquireTimeoutMillis());

    System.setProperty(BrowserConcurrencyLimiter.IMMEDIATE_TIMEOUT_PROPERTY, "곧바로");
    assertEquals(
        BrowserConcurrencyLimiter.DEFAULT_IMMEDIATE_TIMEOUT_SECONDS * 1000L,
        CollectTrigger.IMMEDIATE.acquireTimeoutMillis());
  }
}

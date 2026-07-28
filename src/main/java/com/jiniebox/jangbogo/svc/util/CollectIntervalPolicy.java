package com.jiniebox.jangbogo.svc.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 자동수집 주기 하한 정책 (Phase 3-2).
 *
 * <p>수집 주기가 짧을수록 같은 계정으로 로그인을 반복하게 되고, 그것이 곧 봇 차단을 자초하는 트래픽 프로파일이다. 그런데 하한 검증이 코드 어디에도 없었다 — UI 는
 * {@code min="0"}, 서버는 {@code > 0} 검사뿐이라 UI 조작이나 API 직접 호출로 1분 주기가 저장될 수 있었다.
 *
 * <p>규칙은 두 가지뿐이다.
 *
 * <ul>
 *   <li>{@code 0} 은 유효하다 — "자동수집 안 함"을 뜻한다.
 *   <li>{@code 0} 보다 크면 반드시 {@link #minMinutes()} 이상이어야 한다.
 * </ul>
 *
 * <p>즉 {@code 0 < 값 < 하한} 구간만 거부한다.
 *
 * <p><b>하한은 사용자 설정이 아니라 안전장치다.</b> 그래서 화면에 노출하지 않고 시스템 프로퍼티 {@value #MIN_PROPERTY} 로만 바꿀 수 있게 했다.
 * 개발 중 짧은 주기로 시험해야 하면 {@code -Djangbogo.collect.min-interval-minutes=10} 으로 기동한다. 바꾸기 번거로운 것이 의도다.
 */
public final class CollectIntervalPolicy {

  private static final Logger logger = LogManager.getLogger(CollectIntervalPolicy.class);

  /** 자동수집을 하지 않음을 뜻하는 값. */
  public static final int DISABLED = 0;

  /**
   * 기본 하한 (분).
   *
   * <p>운영 주기는 결정 5 에 따라 720분(1일 2회)이다. 하한을 720 으로 두면 사용자가 주기를 조금도 조절할 수 없으므로, 그 절반인 360분(1일 4회)을
   * 하한으로 둔다. 720 은 기본값, 360 은 넘지 말아야 할 선이다.
   */
  public static final int DEFAULT_MIN_MINUTES = 360;

  /** 하한 재정의 시스템 프로퍼티. */
  public static final String MIN_PROPERTY = "jangbogo.collect.min-interval-minutes";

  private CollectIntervalPolicy() {}

  /** 현재 적용되는 하한(분). */
  public static int minMinutes() {
    String raw = System.getProperty(MIN_PROPERTY);
    if (raw == null || raw.isBlank()) {
      return DEFAULT_MIN_MINUTES;
    }
    try {
      int parsed = Integer.parseInt(raw.trim());
      return parsed > 0 ? parsed : DEFAULT_MIN_MINUTES;
    } catch (NumberFormatException e) {
      logger.warn("{} 값을 해석할 수 없어 기본 하한 {}분을 사용합니다: {}", MIN_PROPERTY, DEFAULT_MIN_MINUTES, raw);
      return DEFAULT_MIN_MINUTES;
    }
  }

  /**
   * 저장을 허용할 값인지 판정한다.
   *
   * @param minutes 주기 (분). {@code null} 은 허용하지 않는다
   * @return 0 이거나 하한 이상이면 true
   */
  public static boolean isAllowed(Integer minutes) {
    if (minutes == null || minutes < DISABLED) {
      return false;
    }
    return minutes == DISABLED || minutes >= minMinutes();
  }

  /**
   * 이미 저장돼 있는 값을 스케줄에 쓸 때 하한으로 끌어올린다.
   *
   * <p>저장 시점에 거부하더라도 그 이전에 들어간 값은 DB 에 남아 있다. 조용히 쓰지 않고 경고를 남긴 뒤 하한으로 올린다.
   *
   * @param seq 쇼핑몰 시퀀스 (로그용)
   * @param minutes 저장돼 있던 주기
   * @return 실제로 스케줄에 쓸 주기
   */
  public static int clampForSchedule(String seq, int minutes) {
    int min = minMinutes();
    if (minutes > DISABLED && minutes < min) {
      logger.warn(
          "쇼핑몰 seq={} 의 저장된 수집 주기 {}분이 하한 {}분 미만입니다. {}분으로 올려 스케줄합니다." + " (설정 화면에서 저장하면 값이 정정됩니다)",
          seq,
          minutes,
          min,
          min);
      return min;
    }
    return minutes;
  }

  /** 거부 사유를 사람이 읽을 문장으로 만든다. */
  public static String rejectionMessage(Integer minutes) {
    return "수집 주기는 0(자동수집 안 함) 또는 " + minMinutes() + "분 이상이어야 합니다. 입력값: " + minutes;
  }
}

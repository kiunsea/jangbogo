package com.jiniebox.jangbogo.sys;

/**
 * 세션 관련 상수 중앙 관리
 *
 * <p>세션 키, 타임아웃 등 세션 관련 상수를 한 곳에서 관리하여 코드 중복을 방지하고 유지보수성을 향상시킵니다.
 */
public class SessionConstants {

  // ===== 세션 키 =====

  /** 관리자 로그인 여부를 나타내는 세션 키 */
  public static final String SESSION_ADMIN_KEY = "ADMIN_LOGGED_IN";

  /** 로그인한 사용자명을 저장하는 세션 키 */
  public static final String SESSION_USERNAME_KEY = "ADMIN_USERNAME";

  /** 사용자 시퀀스를 저장하는 세션 키 */
  public static final String SESSION_USER_SEQ_KEY = "USER_SEQ";

  /** 로그인 시각을 저장하는 세션 키 (밀리초) */
  public static final String SESSION_LOGIN_TIME_KEY = "LOGIN_TIME";

  /** 마지막 활동 시각을 저장하는 세션 키 (밀리초) */
  public static final String SESSION_LAST_ACTIVITY_KEY = "LAST_ACTIVITY_TIME";

  // ===== 세션 타임아웃 =====

  /** 세션 타임아웃 시간 (분) */
  public static final int SESSION_TIMEOUT_MINUTES = 30;

  /** 인스턴스화 방지 */
  private SessionConstants() {
    throw new AssertionError("상수 클래스는 인스턴스화할 수 없습니다.");
  }
}

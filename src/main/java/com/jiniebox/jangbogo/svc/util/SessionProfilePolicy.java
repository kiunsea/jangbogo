package com.jiniebox.jangbogo.svc.util;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 세션 프로필 재사용의 마스터 킬스위치와 경로 정책 (Phase 5-12 · 5-14).
 *
 * <h2>왜 킬스위치가 먼저인가</h2>
 *
 * <p>프로필 재사용은 수집 경로 전체를 바꾼다 — 로그인 화면을 밟지 않고, 브라우저를 사람이 쓰던 프로필로 띄우며, 락과 소유자 검사가 붙는다. 그중 하나라도 어긋나면
 * 수집이 통째로 멈춘다.
 *
 * <p>그래서 <b>기능 코드보다 스위치를 먼저 만든다.</b> 이 값이 꺼져 있으면 Phase 5 의 어떤 코드도 관여하지 않고 기존 동작 그대로 돈다. 새 코드를 다 넣어
 * 두고도 배포본은 여전히 예전처럼 도는 상태를 유지할 수 있다는 뜻이다. 문제가 생기면 롤백이 아니라 스위치 하나로 되돌린다.
 *
 * <p><b>기본값은 꺼짐이다.</b> 몰별 옵트인({@code session_profile_enabled})이 켜져 있어도 이 스위치가 꺼져 있으면 아무 일도 하지 않는다 —
 * 두 겹으로 막는다.
 *
 * <h2>프로필 루트</h2>
 *
 * <p>기본 위치는 {@code %LOCALAPPDATA%\Jangbogo\profiles} 다. 설치 루트 아래가 아니라 사용자 프로필 아래에 두는 이유는 두 가지다 — 설치
 * 폴더가 저장소 트리 안에 있을 수 있어(개발 환경) 로그인 세션이 커밋될 위험이 있고, 설치 폴더는 재설치·업데이트 때 통째로 갈릴 수 있다.
 *
 * <p>이 클래스는 상태를 갖지 않는다. 브라우저·네트워크·DB 를 쓰지 않는다.
 *
 * @author KIUNSEA
 */
public final class SessionProfilePolicy {

  private static final Logger logger = LogManager.getLogger(SessionProfilePolicy.class);

  /** 마스터 킬스위치. 켜야 Phase 5 경로가 살아난다. */
  public static final String ENABLED_PROPERTY = "jangbogo.session-profile.enabled";

  /** 프로필 루트 재정의. */
  public static final String ROOT_PROPERTY = "jangbogo.session-profile.root";

  /** 프로필 루트 기본 위치의 하위 경로. {@code %LOCALAPPDATA%} 아래에 놓인다. */
  public static final String DEFAULT_SUBPATH = "Jangbogo/profiles";

  private SessionProfilePolicy() {}

  /**
   * 세션 프로필 재사용이 켜져 있는지.
   *
   * <p>기본값은 {@code false} 다. 명시적으로 {@code true} 를 줘야만 켜진다 — 오타나 빈 값은 꺼진 것으로 본다.
   *
   * @return 켜져 있으면 true
   */
  public static boolean isEnabled() {
    return Boolean.parseBoolean(trimmed(System.getProperty(ENABLED_PROPERTY)));
  }

  /**
   * 몰 하나에 대해 프로필 경로를 쓸지 판정한다.
   *
   * <p>마스터 스위치와 몰별 옵트인이 <b>둘 다</b> 켜져야 한다. 어느 한쪽만으로는 켜지지 않는다.
   *
   * @param mallOptedIn 그 몰의 {@code session_profile_enabled} 값이 1 인지
   * @return 프로필 경로를 쓸지
   */
  public static boolean appliesTo(boolean mallOptedIn) {
    return isEnabled() && mallOptedIn;
  }

  /**
   * 프로필 루트 디렉터리.
   *
   * <p>재정의가 없으면 {@code %LOCALAPPDATA%\Jangbogo\profiles} 를 쓴다. {@code LOCALAPPDATA} 조차 없는
   * 환경(비-Windows 테스트 등)에서는 사용자 홈 아래로 떨어진다 — 설치 폴더 아래로는 절대 가지 않는다.
   *
   * @return 프로필 루트 경로
   */
  public static Path profileRoot() {
    String override = trimmed(System.getProperty(ROOT_PROPERTY));
    if (override != null) {
      return Paths.get(override);
    }

    String localAppData = trimmed(System.getenv("LOCALAPPDATA"));
    String base = localAppData != null ? localAppData : trimmed(System.getProperty("user.home"));
    if (base == null) {
      // 여기까지 오면 환경이 비정상이다. 현재 디렉터리로 떨어뜨리되 조용히 넘기지 않는다.
      logger.warn("LOCALAPPDATA 도 user.home 도 없다. 프로필 루트를 현재 디렉터리 아래로 잡는다.");
      base = ".";
    }
    return Paths.get(base).resolve(DEFAULT_SUBPATH);
  }

  /**
   * 몰 하나의 프로필 디렉터리.
   *
   * @param profileName {@code session_profile_name} 값
   * @return 프로필 디렉터리 경로
   * @throws IllegalArgumentException 이름이 비었거나 경로 구분자·상위 참조를 담고 있으면
   */
  public static Path profileDir(String profileName) {
    String name = trimmed(profileName);
    if (name == null) {
      throw new IllegalArgumentException("프로필 이름이 비었다.");
    }
    // 이름은 DB 에서 온다. 경로 조각이 섞이면 프로필 루트 밖으로 나갈 수 있다.
    if (name.contains("/") || name.contains("\\") || name.contains("..")) {
      throw new IllegalArgumentException("프로필 이름에 경로 구분자나 상위 참조를 쓸 수 없다: " + profileName);
    }
    return profileRoot().resolve(name);
  }

  private static String trimmed(String value) {
    if (value == null) {
      return null;
    }
    String t = value.trim();
    return t.isEmpty() ? null : t;
  }
}

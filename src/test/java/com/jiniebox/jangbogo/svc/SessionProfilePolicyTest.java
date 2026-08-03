package com.jiniebox.jangbogo.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jiniebox.jangbogo.svc.util.SessionProfilePolicy;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 세션 프로필 마스터 킬스위치·경로 정책 검증 (Phase 5-12 · 5-14).
 *
 * <p>이 스위치의 목적은 <b>Phase 5 코드를 다 넣어 두고도 배포본이 예전처럼 돌게</b> 하는 것이다. 그래서 "기본은 꺼짐"과 "두 겹으로 막는다"가 기능
 * 요건이고, 아래 테스트가 그 둘을 고정한다.
 *
 * <p>브라우저·네트워크·DB 를 쓰지 않는다.
 *
 * @author KIUNSEA
 */
class SessionProfilePolicyTest {

  @Test
  @DisplayName("기본은 꺼짐이다")
  void isOffByDefault() {
    withProperty(
        SessionProfilePolicy.ENABLED_PROPERTY,
        null,
        () -> assertFalse(SessionProfilePolicy.isEnabled()));
  }

  @Test
  @DisplayName("명시적으로 true 라야 켜진다 — 오타는 꺼진 것으로 본다")
  void onlyExplicitTrueEnables() {
    withProperty(
        SessionProfilePolicy.ENABLED_PROPERTY,
        "true",
        () -> assertTrue(SessionProfilePolicy.isEnabled()));
    withProperty(
        SessionProfilePolicy.ENABLED_PROPERTY,
        "TRUE",
        () -> assertTrue(SessionProfilePolicy.isEnabled()));
    withProperty(
        SessionProfilePolicy.ENABLED_PROPERTY,
        "1",
        () -> assertFalse(SessionProfilePolicy.isEnabled()));
    withProperty(
        SessionProfilePolicy.ENABLED_PROPERTY,
        "yes",
        () -> assertFalse(SessionProfilePolicy.isEnabled()));
    withProperty(
        SessionProfilePolicy.ENABLED_PROPERTY,
        "  ",
        () -> assertFalse(SessionProfilePolicy.isEnabled()));
  }

  @Test
  @DisplayName("마스터 스위치와 몰별 옵트인이 둘 다 켜져야 적용된다")
  void requiresBothSwitches() {
    // 두 겹으로 막는 것이 요건이다. 한쪽만으로 켜지면 킬스위치가 킬스위치가 아니다.
    withProperty(
        SessionProfilePolicy.ENABLED_PROPERTY,
        "true",
        () -> {
          assertTrue(SessionProfilePolicy.appliesTo(true));
          assertFalse(SessionProfilePolicy.appliesTo(false), "몰이 옵트인하지 않았는데 적용됐다.");
        });
    withProperty(
        SessionProfilePolicy.ENABLED_PROPERTY,
        null,
        () -> assertFalse(SessionProfilePolicy.appliesTo(true), "마스터가 꺼졌는데 적용됐다."));
  }

  @Test
  @DisplayName("프로필 루트는 재정의할 수 있다")
  void profileRootIsOverridable() {
    withProperty(
        SessionProfilePolicy.ROOT_PROPERTY,
        "D:/tmp/prof",
        () -> assertEquals(Path.of("D:/tmp/prof"), SessionProfilePolicy.profileRoot()));
  }

  @Test
  @DisplayName("프로필 루트 기본값은 설치 폴더 아래가 아니다")
  void defaultProfileRootIsNotUnderTheInstallFolder() {
    // 설치 폴더가 저장소 트리 안일 수 있고(개발 환경) 재설치 때 통째로 갈린다.
    // 로그인 세션이 그 두 경우 모두에 노출된다.
    withProperty(
        SessionProfilePolicy.ROOT_PROPERTY,
        null,
        () -> {
          Path root = SessionProfilePolicy.profileRoot();
          assertTrue(
              root.toString().replace('\\', '/').endsWith(SessionProfilePolicy.DEFAULT_SUBPATH),
              "기본 프로필 루트가 예상 위치가 아니다: " + root);
          assertTrue(root.isAbsolute() || root.startsWith("."), "루트가 상대 경로로 떨어졌다: " + root);
        });
  }

  @Test
  @DisplayName("프로필 이름으로 루트를 벗어날 수 없다")
  void profileNameCannotEscapeTheRoot() {
    // 이름은 DB 에서 온다. 경로 조각이 섞이면 프로필 루트 밖에 디렉터리를 만들게 된다.
    withProperty(
        SessionProfilePolicy.ROOT_PROPERTY,
        "D:/tmp/prof",
        () -> {
          assertEquals(Path.of("D:/tmp/prof/ssg"), SessionProfilePolicy.profileDir("ssg"));

          for (String bad : new String[] {"../evil", "a/b", "a\\b", "..", null, "", "   "}) {
            assertThrows(
                IllegalArgumentException.class,
                () -> SessionProfilePolicy.profileDir(bad),
                "거부해야 할 이름이 통과했다: " + bad);
          }
        });
  }

  /** 시스템 프로퍼티를 잠시 바꿔 실행하고 원래대로 되돌린다. {@code value} 가 null 이면 지운 상태로 실행한다. */
  private static void withProperty(String key, String value, Runnable body) {
    String previous = System.getProperty(key);
    try {
      if (value == null) {
        System.clearProperty(key);
      } else {
        System.setProperty(key, value);
      }
      body.run();
    } finally {
      if (previous == null) {
        System.clearProperty(key);
      } else {
        System.setProperty(key, previous);
      }
    }
  }
}

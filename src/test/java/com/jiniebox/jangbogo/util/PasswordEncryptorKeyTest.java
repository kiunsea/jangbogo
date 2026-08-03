package com.jiniebox.jangbogo.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 암호화 키를 소스 밖에서 지정할 수 있는지 검증 (B-1).
 *
 * <p>키·IV 가 소스에 상수로 박혀 있었고 이 저장소는 PUBLIC 이다 — 즉 <b>기본값을 쓰는 설치본의 암호문은 보호되지 않는다.</b> 그런데도 값을 그냥 바꾸지
 * 못하는 이유는, 바꾸는 순간 기존 DB 의 암호문(FTP 비밀번호 등)을 복호화할 수 없게 되기 때문이다.
 *
 * <p>그래서 <b>덮어쓸 수 있게 열어 두되 기본값은 건드리지 않는</b> 방식을 택했다. 아래 테스트가 그 계약을 양쪽 다 고정한다 — 재정의가 먹는 것과, 재정의가 없을
 * 때 기존 데이터가 계속 열리는 것.
 *
 * <p>키 접근자를 public 으로 열지 않으려고 이 테스트만 같은 패키지에 둔다.
 *
 * @author KIUNSEA
 */
class PasswordEncryptorKeyTest {

  @Test
  @DisplayName("키는 시스템 프로퍼티로 덮어쓸 수 있다")
  void encryptionKeyIsOverridable() {
    withProperty(
        PasswordEncryptor.KEY_PROPERTY,
        "테스트용키",
        () -> assertEquals("테스트용키", PasswordEncryptor.resolveKeySource()));
  }

  @Test
  @DisplayName("IV 도 덮어쓸 수 있다")
  void ivIsOverridable() {
    withProperty(
        PasswordEncryptor.IV_PROPERTY,
        "테스트IV",
        () -> assertEquals("테스트IV", PasswordEncryptor.resolveIvSource()));
  }

  @Test
  @DisplayName("재정의가 없으면 기존 기본값을 쓴다 — 기존 암호문이 계속 복호화돼야 한다")
  void fallsBackToTheLegacyDefault() {
    // 기본값을 바꾸면 이미 저장된 비밀번호를 못 읽는다. 이 값은 마이그레이션 전까지 고정이다.
    withProperty(
        PasswordEncryptor.KEY_PROPERTY,
        null,
        () ->
            assertEquals(
                "jangbogo2024SecretKeyForFtpPassword256bit", PasswordEncryptor.resolveKeySource()));
  }

  @Test
  @DisplayName("빈 문자열은 재정의로 보지 않는다")
  void blankOverrideIsIgnored() {
    withProperty(
        PasswordEncryptor.KEY_PROPERTY,
        "   ",
        () -> assertNull(PasswordEncryptor.overrideOf(PasswordEncryptor.KEY_PROPERTY)));
  }

  @Test
  @DisplayName("암호화·복호화 왕복이 유지된다")
  void encryptDecryptRoundTripStillWorks() {
    String encrypted = PasswordEncryptor.encrypt("비밀번호123!");

    assertNotNull(encrypted);
    assertFalse(encrypted.contains("비밀번호123!"), "평문이 그대로 남았다: " + encrypted);
    assertEquals("비밀번호123!", PasswordEncryptor.decrypt(encrypted));
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

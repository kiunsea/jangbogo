package com.jiniebox.jangbogo.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
  @DisplayName("재정의도 발급된 키도 없으면 기존 기본값으로 떨어진다 — 기존 암호문이 계속 복호화돼야 한다")
  void fallsBackToTheLegacyDefault() {
    // 기본값을 바꾸면 이미 저장된 비밀번호를 못 읽는다. 이 값은 고정이다.
    //
    // 이제 이 경로로 내려오는 것은 키를 발급하지 못한 설치본뿐이다. 발급이 정상인 경우의
    // 계약은 CryptoKeyProvisioningTest 가 잰다. 여기서는 '있는 키 파일'을 일부러 치워
    // 마지막 안전망만 본다 — 치우지 않으면 이 테스트가 다른 테스트의 발급 결과에 끌려간다.
    withProperty(
        CryptoKeyStore.KEY_FILE_PROPERTY,
        new File(System.getProperty("java.io.tmpdir"), "jangbogo-absent-" + System.nanoTime())
            .getAbsolutePath(),
        () ->
            withProperty(
                PasswordEncryptor.KEY_PROPERTY,
                null,
                () ->
                    assertEquals(
                        "jangbogo2024SecretKeyForFtpPassword256bit",
                        PasswordEncryptor.resolveKeySource())));
  }

  @Test
  @DisplayName("발급된 키가 있으면 공개 기본값보다 그것이 이긴다")
  void aProvisionedKeyBeatsTheBuiltInDefault() throws IOException {
    File keyFile =
        File.createTempFile(
            "jangbogo-crypto-", ".key", new File(System.getProperty("java.io.tmpdir")));
    keyFile.deleteOnExit();
    Files.writeString(keyFile.toPath(), "key=발급된키값\niv=발급된IV\n", StandardCharsets.UTF_8);

    withProperty(
        CryptoKeyStore.KEY_FILE_PROPERTY,
        keyFile.getAbsolutePath(),
        () ->
            withProperty(
                PasswordEncryptor.KEY_PROPERTY,
                null,
                () -> {
                  assertEquals("발급된키값", PasswordEncryptor.resolveKeySource());
                  assertEquals("발급된IV", PasswordEncryptor.resolveIvSource());
                }));
  }

  @Test
  @DisplayName("명시적 재정의는 발급된 키보다도 위다 — 되돌릴 수단이 밀리면 안 된다")
  void anExplicitOverrideOutranksTheProvisionedKey() throws IOException {
    File keyFile =
        File.createTempFile(
            "jangbogo-crypto-", ".key", new File(System.getProperty("java.io.tmpdir")));
    keyFile.deleteOnExit();
    Files.writeString(keyFile.toPath(), "key=발급된키값\n", StandardCharsets.UTF_8);

    withProperty(
        CryptoKeyStore.KEY_FILE_PROPERTY,
        keyFile.getAbsolutePath(),
        () ->
            withProperty(
                PasswordEncryptor.KEY_PROPERTY,
                "명령줄로준키",
                () -> assertEquals("명령줄로준키", PasswordEncryptor.resolveKeySource())));
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

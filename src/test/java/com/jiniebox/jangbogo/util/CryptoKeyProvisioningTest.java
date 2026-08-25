package com.jiniebox.jangbogo.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 설치본마다 키를 발급하고, 공개 기본키로 잠긴 값을 그 키로 옮기는지 검증한다.
 *
 * <h2>무엇을 지키려는가</h2>
 *
 * <p>{@link PasswordEncryptor} 의 기본 키·IV 는 <b>공개 저장소의 소스에 그대로 들어 있다.</b> 그 값으로 잠긴 {@code
 * jbg_export_config.ftp_pass} 는 암호문의 형태만 갖췄을 뿐 아무도 막지 못한다.
 *
 * <p>덮어쓸 길은 예전부터 있었지만 배포본에서 닿지 않았고(서비스 파일은 {@code -D} 를 금지하고, 설정 파일은 이 클래스에 보이지 않았다), 결국 <b>경고만 남기고
 * 공개된 값을 계속 쓰는</b> 상태였다. 이 저장소가 방금 두 판에 걸쳐 고친 "경고만 남기고 그대로 진행" 과 같은 모양이다.
 *
 * <p>여기서 재는 것은 셋이다 — 키가 발급되는가, 발급된 키가 기본값을 이기는가, 기존 암호문이 <b>잃지 않고</b> 새 키로 옮겨지는가.
 *
 * <p>DB·네트워크를 쓰지 않는다. 키 파일 위치를 임시 디렉터리로 돌려 작업 트리를 건드리지 않는다.
 */
class CryptoKeyProvisioningTest {

  @TempDir File workDir;

  private static final String DEFAULT_KEY = "jangbogo2024SecretKeyForFtpPassword256bit";

  @Test
  @DisplayName("키 파일이 없으면 발급한다 — 그리고 그 키가 공개 기본값을 이긴다")
  void provisioningCreatesAKeyThatBeatsTheBuiltInDefault() {
    withKeyFile(
        newKeyFile(),
        () -> {
          assertTrue(CryptoKeyProvisioning.ensureProvisioned(), "키를 발급하지 않았다.");
          assertTrue(CryptoKeyStore.exists(), "키 파일이 만들어지지 않았다.");

          String active = PasswordEncryptor.resolveKeySource();
          assertNotEquals(DEFAULT_KEY, active, "발급해 놓고 공개된 기본키를 계속 쓴다 — 아무것도 나아지지 않는다.");
          assertFalse(PasswordEncryptor.usingDefaultKey());
          assertNotEquals(
              "jangbogo2024IV16", PasswordEncryptor.resolveIvSource(), "IV 는 여전히 공개된 값이다.");
        });
  }

  @Test
  @DisplayName("이미 있는 키는 절대 덮어쓰지 않는다 — 덮어쓰면 저장된 비밀번호를 영원히 못 읽는다")
  void provisioningNeverOverwritesAnExistingKey() {
    withKeyFile(
        newKeyFile(),
        () -> {
          CryptoKeyProvisioning.ensureProvisioned();
          String first = PasswordEncryptor.resolveKeySource();

          assertFalse(CryptoKeyProvisioning.ensureProvisioned(), "두 번째 호출이 또 발급했다.");
          assertEquals(first, PasswordEncryptor.resolveKeySource(), "키가 바뀌었다 — 기존 암호문이 죽는다.");
        });
  }

  @Test
  @DisplayName("발급된 키는 설치본마다 다르다")
  void everyInstallationGetsItsOwnKey() {
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < 5; i++) {
      File keyFile = new File(workDir, "install" + i + ".key");
      withKeyFile(
          keyFile,
          () -> {
            CryptoKeyProvisioning.ensureProvisioned();
            seen.add(PasswordEncryptor.resolveKeySource());
          });
    }
    assertEquals(5, seen.size(), "설치본마다 같은 키가 나왔다 — 하나가 새면 전부 열린다.");
  }

  @Test
  @DisplayName("설정으로 키를 지정하면 발급하지 않는다 — 출처가 둘이 되면 안 된다")
  void anExplicitKeyStopsProvisioning() {
    File keyFile = newKeyFile();
    withKeyFile(
        keyFile,
        () ->
            withProperty(
                PasswordEncryptor.KEY_PROPERTY,
                "운영자가지정한키",
                () -> {
                  assertFalse(CryptoKeyProvisioning.ensureProvisioned());
                  assertFalse(keyFile.exists(), "운영자가 키를 들고 있는데 파일을 만들었다.");
                  assertEquals("운영자가지정한키", PasswordEncryptor.resolveKeySource());
                }));
  }

  @Test
  @DisplayName("기본키로 잠겨 있던 비밀번호가 발급된 키로 옮겨진다")
  void aLegacyPasswordIsMovedOntoTheProvisionedKey() {
    // 1) 키를 발급하기 전 — 공개된 기본키로 잠긴다.
    String legacyCiphertext =
        withKeyFile(newKeyFile(), () -> PasswordEncryptor.encrypt("실제FTP비밀번호!"));
    assertNotNull(legacyCiphertext);

    // 2) 키를 발급하고 옮긴다.
    withKeyFile(
        newKeyFile(),
        () -> {
          CryptoKeyProvisioning.ensureProvisioned();

          String migrated = CryptoKeyProvisioning.reEncryptIfLegacy(legacyCiphertext);

          assertNotNull(migrated, "옮기지 않았다 — 공개된 키로 계속 열린다.");
          assertNotEquals(legacyCiphertext, migrated);
          assertEquals("실제FTP비밀번호!", PasswordEncryptor.decrypt(migrated), "옮기면서 값이 달라졌다.");
        });
  }

  @Test
  @DisplayName("이미 옮긴 값은 다시 건드리지 않는다")
  void anAlreadyMigratedPasswordIsLeftAlone() {
    withKeyFile(
        newKeyFile(),
        () -> {
          CryptoKeyProvisioning.ensureProvisioned();
          String current = PasswordEncryptor.encrypt("비밀번호");

          assertNull(CryptoKeyProvisioning.reEncryptIfLegacy(current), "옮길 필요가 없는데 다시 잠갔다.");
        });
  }

  @Test
  @DisplayName("키가 없어 발급하지 못한 상태에서는 옮기지 않는다")
  void nothingIsMigratedWhileStillOnTheDefaultKey() {
    withKeyFile(
        newKeyFile(),
        () -> {
          // 발급하지 않았으므로 활성 키가 곧 기본키다.
          assertTrue(PasswordEncryptor.usingDefaultKey());
          assertNull(CryptoKeyProvisioning.reEncryptIfLegacy(PasswordEncryptor.encrypt("비밀번호")));
        });
  }

  @Test
  @DisplayName("어떤 키로도 열리지 않는 값은 그대로 둔다 — 덮어쓰면 단서마저 사라진다")
  void anUnreadableValueIsNotOverwritten() {
    withKeyFile(
        newKeyFile(),
        () -> {
          CryptoKeyProvisioning.ensureProvisioned();
          assertNull(CryptoKeyProvisioning.reEncryptIfLegacy("이건암호문이아니다"));
        });
  }

  @Test
  @DisplayName("옮기는 도중에 죽어도 비밀번호를 잃지 않는다 — 기본키로 되읽는다")
  void anInterruptedMigrationStillReadsTheOldValue() {
    // 키 발급은 됐는데 재암호화가 DB 에 반영되기 전에 프로세스가 죽은 상태를 재현한다.
    String legacyCiphertext =
        withKeyFile(newKeyFile(), () -> PasswordEncryptor.encrypt("잃으면안되는비밀번호"));

    withKeyFile(
        newKeyFile(),
        () -> {
          CryptoKeyProvisioning.ensureProvisioned();

          assertEquals(
              "잃으면안되는비밀번호",
              PasswordEncryptor.decrypt(legacyCiphertext),
              "새 키로 도는데 옛 암호문을 못 읽는다 — 운영자가 비밀번호를 다시 입력해야 한다.");
        });
  }

  @Test
  @DisplayName("키 파일이 사람이 읽을 수 있는 형태이고 경고 문구를 담는다")
  void theKeyFileExplainsItself() throws Exception {
    File keyFile = newKeyFile();
    withKeyFile(keyFile, CryptoKeyProvisioning::ensureProvisioned);

    String content = Files.readString(keyFile.toPath(), StandardCharsets.UTF_8);

    assertTrue(content.contains("key="), "키 항목이 없다.");
    assertTrue(content.contains("iv="), "IV 항목이 없다.");
    assertTrue(content.contains("다시 읽지 못합니다"), "지우면 어떻게 되는지 파일 안에 적혀 있지 않다.");
  }

  // ---------------------------------------------------------------------------

  private File newKeyFile() {
    return new File(workDir, "crypto-" + System.nanoTime() + ".key");
  }

  /** 키 파일 위치를 임시 경로로 돌려 실행한다. 작업 트리에 키를 남기지 않기 위한 것이다. */
  private static <T> T withKeyFile(File keyFile, java.util.function.Supplier<T> body) {
    String previous = System.getProperty(CryptoKeyStore.KEY_FILE_PROPERTY);
    try {
      System.setProperty(CryptoKeyStore.KEY_FILE_PROPERTY, keyFile.getAbsolutePath());
      return body.get();
    } finally {
      if (previous == null) {
        System.clearProperty(CryptoKeyStore.KEY_FILE_PROPERTY);
      } else {
        System.setProperty(CryptoKeyStore.KEY_FILE_PROPERTY, previous);
      }
    }
  }

  private static void withKeyFile(File keyFile, Runnable body) {
    withKeyFile(
        keyFile,
        () -> {
          body.run();
          return null;
        });
  }

  private static void withProperty(String key, String value, Runnable body) {
    String previous = System.getProperty(key);
    try {
      System.setProperty(key, value);
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

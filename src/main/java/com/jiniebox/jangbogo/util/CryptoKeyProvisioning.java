package com.jiniebox.jangbogo.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 키 발급과 기존 암호문 이전을 한 자리에서 정한다.
 *
 * <p>DB 를 모른다. 암호문을 <b>받고 돌려주기만</b> 한다 — 어디서 읽고 어디에 쓸지는 부르는 쪽이 안다. 키 내부(기본값·활성 키)를 만지려면 {@link
 * PasswordEncryptor} 와 같은 패키지여야 하는데, 그 접근자를 public 으로 여는 대신 이 클래스를 여기 두었다.
 *
 * <h2>순서와 그 이유</h2>
 *
 * <ol>
 *   <li><b>발급을 먼저 한다.</b> 그래야 이전에 실패해도 다음 기동이 같은 키로 다시 시도한다. 재암호화를 먼저 하면 어떤 키로 잠글지가 정해지지 않는다.
 *   <li><b>이전은 못 해도 된다.</b> {@link PasswordEncryptor#decrypt} 가 기본키로 되읽으므로, 이전이 끊겨도 비밀번호를 잃지 않는다. 그
 *       되읽기가 있어서 이 순서를 마음 놓고 쓸 수 있다.
 *   <li><b>새 암호문은 반드시 왕복 검증한다.</b> 검증 없이 덮어쓰면, 잘못 잠긴 값이 기본키로도 활성 키로도 안 열리는 상태가 된다 — 그때는 되읽기도 못 구한다.
 * </ol>
 *
 * <p>명시적 재정의({@code -D}·환경변수·설정 파일)가 있으면 <b>발급하지 않는다.</b> 키를 운영자가 들고 있겠다는 뜻이므로 파일을 만들면 출처가 둘이 된다.
 * 다만 이전은 그 경우에도 한다 — 운영자가 키를 처음 지정한 순간, 기존 비밀번호는 기본키로 잠겨 있기 때문이다. 예전에는 그 자리에서 값이 조용히 못 읽는 상태가 됐다.
 */
public final class CryptoKeyProvisioning {

  private static final Logger logger = LoggerFactory.getLogger(CryptoKeyProvisioning.class);

  private CryptoKeyProvisioning() {}

  /**
   * 이 설치본의 키를 확보한다.
   *
   * <p>명시적 재정의가 있으면 아무것도 하지 않는다. 없고 키 파일도 없으면 새로 발급한다.
   *
   * @return 이번 호출이 새 키를 발급했으면 true
   */
  public static boolean ensureProvisioned() {
    if (PasswordEncryptor.overrideOf(PasswordEncryptor.KEY_PROPERTY) != null) {
      logger.info("암호화 키가 설정으로 지정돼 있습니다. 발급하지 않습니다.");
      return false;
    }
    return CryptoKeyStore.provision();
  }

  /**
   * 공개 기본키로 잠긴 암호문이면 이 설치본의 키로 다시 잠근다.
   *
   * @param encryptedPassword 저장돼 있던 암호문. 비어 있으면 할 일이 없다
   * @return 새로 잠근 암호문. 옮길 필요가 없거나 옮기지 못했으면 {@code null}
   */
  public static String reEncryptIfLegacy(String encryptedPassword) {
    if (encryptedPassword == null || encryptedPassword.isEmpty()) {
      return null;
    }
    if (PasswordEncryptor.usingDefaultKey()) {
      // 옮길 곳이 없다. 키를 발급하지 못한 설치본이며, 그 사실은 이미 경고로 남았다.
      return null;
    }

    try {
      PasswordEncryptor.decryptWithActiveKey(encryptedPassword);
      // 이미 이 설치본의 키로 잠겨 있다.
      return null;
    } catch (Exception notYetMigrated) {
      // 아래에서 기본키로 열어 본다.
    }

    String plain;
    try {
      plain = PasswordEncryptor.decryptWithDefaultKey(encryptedPassword);
    } catch (Exception e) {
      // 활성 키로도 기본키로도 안 열린다. 우리가 아는 어떤 키의 것도 아니므로 건드리지 않는다 —
      // 덮어쓰면 남아 있던 단서마저 사라진다.
      logger.error("저장된 비밀번호를 알고 있는 어떤 키로도 읽지 못했습니다. 그대로 둡니다 - 저장 설정에서 다시 입력해야 할 수 있습니다.");
      return null;
    }

    String reEncrypted = PasswordEncryptor.encrypt(plain);
    if (reEncrypted == null || reEncrypted.isEmpty()) {
      logger.error("비밀번호를 이 설치본의 키로 다시 잠그지 못했습니다. 기존 값을 그대로 둡니다.");
      return null;
    }

    // 왕복 검증. 이걸 빼면 잘못 잠긴 값이 두 키 어느 쪽으로도 안 열리는 상태가 된다.
    try {
      if (!plain.equals(PasswordEncryptor.decryptWithActiveKey(reEncrypted))) {
        logger.error("다시 잠근 비밀번호가 원래 값으로 돌아오지 않습니다. 기존 값을 그대로 둡니다.");
        return null;
      }
    } catch (Exception e) {
      logger.error("다시 잠근 비밀번호를 되읽지 못했습니다. 기존 값을 그대로 둡니다 - {}", e.getMessage());
      return null;
    }

    logger.info("저장된 FTP 비밀번호를 이 설치본의 키로 옮겼습니다. 공개된 기본키로는 더 이상 열리지 않습니다.");
    return reEncrypted;
  }
}

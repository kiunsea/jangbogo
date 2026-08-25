package com.jiniebox.jangbogo.util;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 비밀번호 암호화/복호화 유틸리티
 *
 * <p>FTP 비밀번호 등 민감한 정보를 암호화하여 DB에 저장하고 사용 시 복호화하는 기능을 제공합니다.
 *
 * @author KIUNSEA
 */
public class PasswordEncryptor {

  private static final Logger logger = LoggerFactory.getLogger(PasswordEncryptor.class);

  /** 키 재정의 시스템 프로퍼티 / 환경변수 (B-1). */
  public static final String KEY_PROPERTY = "jangbogo.crypto.key";

  /** IV 재정의 시스템 프로퍼티 / 환경변수 (B-1). */
  public static final String IV_PROPERTY = "jangbogo.crypto.iv";

  // 소스에 박힌 기본 키·IV.
  //
  // 이 저장소는 PUBLIC 이므로 아래 두 값은 이미 공개돼 있다 — 즉 이 값으로 잠긴 암호문은
  // 보호되지 않는다. 그런데도 값을 지우지 못하는 이유는, 지우는 순간 이 값으로 암호화된
  // 기존 DB 의 비밀번호를 읽을 방법이 사라지기 때문이다.
  //
  // 그래서 지우는 대신 밀어냈다 — CryptoKeyStore 가 설치본마다 키를 발급하고,
  // CryptoKeyProvisioning 이 기존 암호문을 그 키로 옮긴다. 아래 값은 옮기기 전에 저장된
  // 것을 읽어 내는 용도로만 남는다(decrypt 의 되읽기 경로).
  private static final String DEFAULT_KEY = "jangbogo2024SecretKeyForFtpPassword256bit";
  private static final String DEFAULT_IV = "jangbogo2024IV16";

  /** 기본값 사용 경고를 한 번만 남기기 위한 플래그. 매 호출마다 찍으면 로그가 무의미해진다. */
  private static boolean defaultKeyWarned = false;

  /** 구 암호문 되읽기 경고를 한 번만 남기기 위한 플래그. */
  private static boolean legacyReadWarned = false;

  /**
   * 재정의 값을 읽는다. 시스템 프로퍼티 → 환경변수 순으로 본다.
   *
   * <p>환경변수 이름은 점을 밑줄로 바꾸고 대문자로 올린 형태다 ({@code JANGBOGO_CRYPTO_KEY}).
   *
   * @param property 시스템 프로퍼티 이름
   * @return 설정된 값. 없으면 null
   */
  static String overrideOf(String property) {
    String value = System.getProperty(property);
    if (value == null || value.isBlank()) {
      value = System.getenv(property.replace('.', '_').toUpperCase());
    }
    return (value == null || value.isBlank()) ? null : value;
  }

  /**
   * 실제로 쓸 키 원본 문자열.
   *
   * <p>순서는 <b>명시적 재정의 → 이 설치본에 발급된 키 → 소스의 기본값</b>이다. 재정의를 맨 위에 두는 이유는, 사고가 났을 때 되돌리려고 붙인 명령줄 인자가
   * 파일에 밀리면 되돌릴 수단이 사라지기 때문이다.
   *
   * <p>기본값까지 내려오는 것은 이제 <b>키를 발급하지 못한 설치본</b>뿐이다. 그때만 경고한다.
   */
  static String resolveKeySource() {
    String override = overrideOf(KEY_PROPERTY);
    if (override != null) {
      return override;
    }
    String provisioned = CryptoKeyStore.key();
    if (provisioned != null) {
      return provisioned;
    }
    warnDefaultOnce();
    return DEFAULT_KEY;
  }

  /** 실제로 쓸 IV 원본 문자열. 순서는 키와 같다. */
  static String resolveIvSource() {
    String override = overrideOf(IV_PROPERTY);
    if (override != null) {
      return override;
    }
    String provisioned = CryptoKeyStore.iv();
    return provisioned != null ? provisioned : DEFAULT_IV;
  }

  /** 지금 쓰는 키가 소스에 박힌 공개 기본값인가. */
  static boolean usingDefaultKey() {
    return DEFAULT_KEY.equals(resolveKeySource());
  }

  private static synchronized void warnDefaultOnce() {
    if (!defaultKeyWarned) {
      defaultKeyWarned = true;
      logger.warn(
          "암호화 키가 기본값이다. 이 값은 공개 저장소의 소스에 들어 있으므로 저장된 비밀번호가 보호되지 않는다."
              + " -D{}=<키> 또는 환경변수 {} 로 지정할 것.",
          KEY_PROPERTY,
          KEY_PROPERTY.replace('.', '_').toUpperCase());
    }
  }

  private static SecretKey secretKey = null;
  private static IvParameterSpec iv = null;

  /**
   * 마지막으로 키 객체를 만들 때 쓴 원본 문자열.
   *
   * <p>이걸 함께 들고 있어야 <b>기동 중에 키가 바뀌는 순간</b>을 따라갈 수 있다. 기동 태스크가 키를 새로 발급하는데, 그전에 무언가 한 번이라도 복호화를
   * 시도했다면 예전 코드는 기본키로 만든 객체를 계속 들고 있었다 — 발급을 해 놓고도 옛 키로 도는, 로그만 봐서는 알 수 없는 상태가 된다.
   */
  private static String cachedKeySource = null;

  private static String cachedIvSource = null;

  /** 암호화 키 및 IV 초기화. 원본이 바뀌었으면 다시 만든다. */
  private static synchronized void initializeEncryptionKey() {
    String keySource = resolveKeySource();
    String ivSource = resolveIvSource();

    if (secretKey != null
        && iv != null
        && keySource.equals(cachedKeySource)
        && ivSource.equals(cachedIvSource)) {
      return;
    }

    try {
      secretKey = toKey(keySource);
      iv = toIv(ivSource);
      cachedKeySource = keySource;
      cachedIvSource = ivSource;

      logger.debug("암호화 키 및 IV 초기화 완료");
    } catch (Exception e) {
      logger.error("암호화 키 초기화 실패", e);
      throw new RuntimeException("암호화 키 초기화 실패", e);
    }
  }

  /** 원본 문자열의 UTF-8 바이트를 32바이트(256비트) 버퍼에 앞에서부터 채운다. 모자란 자리는 0 이다. */
  private static SecretKey toKey(String source) throws Exception {
    byte[] keyBytes = new byte[32];
    byte[] sourceBytes = source.getBytes("UTF-8");
    System.arraycopy(sourceBytes, 0, keyBytes, 0, Math.min(sourceBytes.length, 32));
    return new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");
  }

  /** 원본 문자열의 UTF-8 바이트를 16바이트 버퍼에 앞에서부터 채운다. */
  private static IvParameterSpec toIv(String source) throws Exception {
    byte[] ivBytes = new byte[16];
    byte[] ivSourceBytes = source.getBytes("UTF-8");
    System.arraycopy(ivSourceBytes, 0, ivBytes, 0, Math.min(ivSourceBytes.length, 16));
    return new IvParameterSpec(ivBytes);
  }

  /**
   * 지금 쓰는 키로 복호화한다. 실패하면 예외를 그대로 올린다.
   *
   * <p>{@link #decrypt(String)} 과 달리 실패를 빈 문자열로 삼키지 않는다 — 마이그레이션은 "못 읽었다" 와 "빈 비밀번호였다" 를 구분해야 한다.
   */
  static String decryptWithActiveKey(String encryptedPassword) throws Exception {
    initializeEncryptionKey();
    return StringEncrypter.decrypt(StringEncrypter.ALGORITHM, encryptedPassword, secretKey, iv);
  }

  /** 소스에 박힌 공개 기본값으로 복호화한다. 키를 옮기기 전에 저장된 값을 읽어 내는 용도다. */
  static String decryptWithDefaultKey(String encryptedPassword) throws Exception {
    return StringEncrypter.decrypt(
        StringEncrypter.ALGORITHM, encryptedPassword, toKey(DEFAULT_KEY), toIv(DEFAULT_IV));
  }

  private static synchronized void warnLegacyReadOnce() {
    if (!legacyReadWarned) {
      legacyReadWarned = true;
      logger.warn(
          "공개된 기본키로 잠긴 비밀번호를 읽었다. 이 설치본의 키로 아직 옮기지 못한 값이다 -"
              + " 다음 기동에서 다시 시도하며, 계속 이 줄이 보이면 저장 설정에서 비밀번호를 한 번 다시 저장할 것.");
    }
  }

  /**
   * 비밀번호를 암호화합니다.
   *
   * @param plainPassword 평문 비밀번호
   * @return 암호화된 비밀번호 (Base64 인코딩)
   */
  public static String encrypt(String plainPassword) {
    if (plainPassword == null || plainPassword.isEmpty()) {
      return "";
    }

    try {
      initializeEncryptionKey();

      String encrypted =
          StringEncrypter.encrypt(StringEncrypter.ALGORITHM, plainPassword, secretKey, iv);

      logger.debug("비밀번호 암호화 완료 (길이: {})", encrypted.length());
      return encrypted;

    } catch (Exception e) {
      logger.error("비밀번호 암호화 실패", e);
      // 암호화 실패 시 빈 문자열 반환 (또는 예외 던지기)
      return "";
    }
  }

  /**
   * 암호화된 비밀번호를 복호화합니다.
   *
   * @param encryptedPassword 암호화된 비밀번호 (Base64 인코딩)
   * @return 평문 비밀번호
   */
  public static String decrypt(String encryptedPassword) {
    if (encryptedPassword == null || encryptedPassword.isEmpty()) {
      return "";
    }

    try {
      String decrypted = decryptWithActiveKey(encryptedPassword);
      logger.debug("비밀번호 복호화 완료");
      return decrypted;

    } catch (Exception primary) {
      // 지금 키로 못 읽었다. 이 설치본의 키로 아직 옮기지 못한 값일 수 있으므로 기본키로 한 번 더 본다.
      //
      // 이 되읽기가 있어야 키 발급과 재암호화 사이에 프로세스가 죽어도 비밀번호를 잃지 않는다.
      // 없으면 두 작업의 순서를 어떻게 잡든 그 사이에 창이 남고, 그 창에 걸리면 운영자가
      // 비밀번호를 다시 입력하는 것 말고는 방법이 없다.
      if (!usingDefaultKey()) {
        try {
          String legacy = decryptWithDefaultKey(encryptedPassword);
          warnLegacyReadOnce();
          return legacy;
        } catch (Exception stillNo) {
          // 기본키로도 아니다. 아래에서 원래 실패를 남긴다.
        }
      }

      logger.error("비밀번호 복호화 실패", primary);
      // 복호화 실패 시 빈 문자열 반환 (또는 예외 던지기)
      return "";
    }
  }

  /** 암호화 테스트 */
  public static void main(String[] args) {
    System.out.println("========================================");
    System.out.println("비밀번호 암호화/복호화 테스트");
    System.out.println("========================================\n");

    String[] testPasswords = {"password123", "mySecretPass!@#", "한글비밀번호테스트", "a1b2c3d4e5"};

    for (String plainPass : testPasswords) {
      System.out.println("원본 비밀번호: " + plainPass);

      // 암호화
      String encrypted = encrypt(plainPass);
      System.out.println("암호화 결과: " + encrypted);

      // 복호화
      String decrypted = decrypt(encrypted);
      System.out.println("복호화 결과: " + decrypted);

      // 검증
      boolean isMatch = plainPass.equals(decrypted);
      System.out.println("일치 여부: " + (isMatch ? "✓ 성공" : "✗ 실패"));
      System.out.println();
    }

    System.out.println("========================================");
    System.out.println("테스트 완료");
    System.out.println("========================================");
  }
}

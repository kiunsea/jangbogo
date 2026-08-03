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
  // 이 저장소는 PUBLIC 이므로 아래 두 값은 이미 공개돼 있다 — 즉 기본값을 쓰는 설치본의
  // 암호문은 보호되지 않는다. 그런데도 값을 바꾸지 못하는 이유는, 바꾸는 순간 기존 DB 의
  // 암호문(FTP 비밀번호 등)을 복호화할 수 없게 되기 때문이다. 데이터를 잃는 것보다는
  // "덮어쓸 수 있게 열어 두고, 기본값을 쓰면 경고한다"가 낫다.
  //
  // 재암호화 마이그레이션은 별도 과제다. 그때 이 기본값 경로를 지운다.
  private static final String DEFAULT_KEY = "jangbogo2024SecretKeyForFtpPassword256bit";
  private static final String DEFAULT_IV = "jangbogo2024IV16";

  /** 기본값 사용 경고를 한 번만 남기기 위한 플래그. 매 호출마다 찍으면 로그가 무의미해진다. */
  private static boolean defaultKeyWarned = false;

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

  /** 실제로 쓸 키 원본 문자열. 재정의가 없으면 기본값을 쓰고 한 번 경고한다. */
  static String resolveKeySource() {
    String override = overrideOf(KEY_PROPERTY);
    if (override != null) {
      return override;
    }
    warnDefaultOnce();
    return DEFAULT_KEY;
  }

  /** 실제로 쓸 IV 원본 문자열. */
  static String resolveIvSource() {
    String override = overrideOf(IV_PROPERTY);
    return override != null ? override : DEFAULT_IV;
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

  /** 암호화 키 및 IV 초기화 */
  private static void initializeEncryptionKey() {
    if (secretKey == null || iv == null) {
      try {
        // 키를 문자열에서 생성 (32바이트 = 256비트)
        byte[] keyBytes = new byte[32];
        byte[] sourceBytes = resolveKeySource().getBytes("UTF-8");
        System.arraycopy(sourceBytes, 0, keyBytes, 0, Math.min(sourceBytes.length, 32));
        secretKey = new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");

        // IV 생성 (16바이트)
        byte[] ivBytes = new byte[16];
        byte[] ivSourceBytes = resolveIvSource().getBytes("UTF-8");
        System.arraycopy(ivSourceBytes, 0, ivBytes, 0, Math.min(ivSourceBytes.length, 16));
        iv = new IvParameterSpec(ivBytes);

        logger.debug("암호화 키 및 IV 초기화 완료");
      } catch (Exception e) {
        logger.error("암호화 키 초기화 실패", e);
        throw new RuntimeException("암호화 키 초기화 실패", e);
      }
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
      initializeEncryptionKey();

      String decrypted =
          StringEncrypter.decrypt(StringEncrypter.ALGORITHM, encryptedPassword, secretKey, iv);

      logger.debug("비밀번호 복호화 완료");
      return decrypted;

    } catch (Exception e) {
      logger.error("비밀번호 복호화 실패", e);
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

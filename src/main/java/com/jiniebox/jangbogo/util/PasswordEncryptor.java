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

  // 암호화 키 (Base64 인코딩된 AES 256비트 키)
  // TODO: 운영 환경에서는 환경변수나 설정 파일에서 가져오도록 변경 필요
  private static final String ENCRYPTION_KEY_BASE64 = "jangbogo2024SecretKeyForFtpPassword256bit";

  // IV (Base64 인코딩된 16바이트 IV)
  private static final String ENCRYPTION_IV_BASE64 = "jangbogo2024IV16";

  private static SecretKey secretKey = null;
  private static IvParameterSpec iv = null;

  /** 암호화 키 및 IV 초기화 */
  private static void initializeEncryptionKey() {
    if (secretKey == null || iv == null) {
      try {
        // 키를 고정 문자열에서 생성 (32바이트 = 256비트)
        byte[] keyBytes = new byte[32];
        byte[] sourceBytes = ENCRYPTION_KEY_BASE64.getBytes("UTF-8");
        System.arraycopy(sourceBytes, 0, keyBytes, 0, Math.min(sourceBytes.length, 32));
        secretKey = new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");

        // IV 생성 (16바이트)
        byte[] ivBytes = new byte[16];
        byte[] ivSourceBytes = ENCRYPTION_IV_BASE64.getBytes("UTF-8");
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

package com.jiniebox.jangbogo.util.security;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RSA를 사용한 파일 암호화 유틸리티
 *
 * <p>Public Key로 파일을 암호화하여 FTP 전송 시 사용
 */
public class RsaFileEncryption {

  private static final Logger logger = LoggerFactory.getLogger(RsaFileEncryption.class);

  /**
   * Base64 인코딩된 Public Key 문자열을 PublicKey 객체로 변환
   *
   * @param base64PublicKey Base64 인코딩된 Public Key
   * @return PublicKey 객체
   * @throws Exception
   */
  public static PublicKey getPublicKeyFromBase64(String base64PublicKey) throws Exception {
    byte[] keyBytes = Base64.getDecoder().decode(base64PublicKey);
    X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    return keyFactory.generatePublic(keySpec);
  }

  /**
   * Public Key로 파일을 암호화
   *
   * @param sourceFilePath 원본 파일 경로
   * @param encryptedFilePath 암호화된 파일 경로
   * @param base64PublicKey Base64 인코딩된 Public Key
   * @return 암호화 성공 여부
   */
  public static boolean encryptFile(
      String sourceFilePath, String encryptedFilePath, String base64PublicKey) {
    FileInputStream inputStream = null;
    FileOutputStream outputStream = null;

    try {
      File sourceFile = new File(sourceFilePath);
      if (!sourceFile.exists()) {
        logger.error("암호화할 파일이 존재하지 않습니다: {}", sourceFilePath);
        return false;
      }

      // Public Key 로드
      PublicKey publicKey = getPublicKeyFromBase64(base64PublicKey);

      // Cipher 초기화
      Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
      cipher.init(Cipher.ENCRYPT_MODE, publicKey);

      // 파일 읽기
      inputStream = new FileInputStream(sourceFile);
      byte[] fileData = inputStream.readAllBytes();

      logger.info("파일 암호화 시작 - 원본 크기: {} bytes", fileData.length);

      // RSA는 데이터 크기 제한이 있으므로 작은 파일만 암호화 가능
      // 일반적으로 2048비트 키는 최대 245바이트까지만 암호화 가능
      // 큰 파일은 AES로 암호화하고 AES 키만 RSA로 암호화하는 Hybrid 방식 사용 필요
      if (fileData.length > 245) {
        logger.warn("파일이 너무 큽니다 ({} bytes). RSA로 직접 암호화할 수 없습니다.", fileData.length);
        logger.info("Hybrid 암호화 모드로 전환합니다 (AES + RSA)");
        return encryptFileLarge(sourceFilePath, encryptedFilePath, publicKey);
      }

      // 파일 암호화
      byte[] encryptedData = cipher.doFinal(fileData);

      // 암호화된 데이터를 파일로 저장
      outputStream = new FileOutputStream(encryptedFilePath);
      outputStream.write(encryptedData);

      logger.info(
          "파일 암호화 완료: {} -> {} (암호화 크기: {} bytes)",
          sourceFilePath,
          encryptedFilePath,
          encryptedData.length);

      return true;

    } catch (Exception e) {
      logger.error("파일 암호화 중 오류 발생: {}", e.getMessage(), e);
      return false;
    } finally {
      try {
        if (inputStream != null) inputStream.close();
        if (outputStream != null) outputStream.close();
      } catch (Exception e) {
        logger.warn("스트림 닫기 실패", e);
      }
    }
  }

  /**
   * Hybrid 방식으로 큰 파일 암호화 (AES + RSA) AES로 파일을 암호화하고, AES 키를 RSA로 암호화
   *
   * @param sourceFilePath 원본 파일 경로
   * @param encryptedFilePath 암호화된 파일 경로
   * @param publicKey RSA Public Key
   * @return 암호화 성공 여부
   */
  private static boolean encryptFileLarge(
      String sourceFilePath, String encryptedFilePath, PublicKey publicKey) {
    FileInputStream inputStream = null;
    FileOutputStream outputStream = null;

    try {
      // AES 키 생성 (256비트)
      javax.crypto.KeyGenerator keyGen = javax.crypto.KeyGenerator.getInstance("AES");
      keyGen.init(256);
      javax.crypto.SecretKey aesKey = keyGen.generateKey();

      // 파일을 AES로 암호화
      Cipher aesCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      aesCipher.init(Cipher.ENCRYPT_MODE, aesKey);

      inputStream = new FileInputStream(sourceFilePath);
      byte[] fileData = inputStream.readAllBytes();
      byte[] iv = aesCipher.getIV();
      byte[] encryptedFileData = aesCipher.doFinal(fileData);

      // AES 키를 RSA로 암호화
      Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
      rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey);
      byte[] encryptedAesKey = rsaCipher.doFinal(aesKey.getEncoded());

      // 저장 형식: [AES키크기(4byte)][암호화된AES키][IV크기(4byte)][IV][암호화된파일데이터]
      outputStream = new FileOutputStream(encryptedFilePath);

      // AES 키 크기 저장
      outputStream.write(intToBytes(encryptedAesKey.length));
      // 암호화된 AES 키 저장
      outputStream.write(encryptedAesKey);
      // IV 크기 저장
      outputStream.write(intToBytes(iv.length));
      // IV 저장
      outputStream.write(iv);
      // 암호화된 파일 데이터 저장
      outputStream.write(encryptedFileData);

      logger.info(
          "Hybrid 암호화 완료: {} -> {} (원본: {} bytes, 암호화: {} bytes)",
          sourceFilePath,
          encryptedFilePath,
          fileData.length,
          4 + encryptedAesKey.length + 4 + iv.length + encryptedFileData.length);

      return true;

    } catch (Exception e) {
      logger.error("Hybrid 파일 암호화 중 오류 발생: {}", e.getMessage(), e);
      return false;
    } finally {
      try {
        if (inputStream != null) inputStream.close();
        if (outputStream != null) outputStream.close();
      } catch (Exception e) {
        logger.warn("스트림 닫기 실패", e);
      }
    }
  }

  /** int를 4바이트 배열로 변환 */
  private static byte[] intToBytes(int value) {
    return new byte[] {
      (byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value
    };
  }
}

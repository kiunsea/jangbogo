package com.jiniebox.jangbogo.security;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * RSA 키 쌍 생성 예제 클래스
 *
 * <p>RSA 알고리즘을 사용하여 Public Key와 Private Key를 생성하고 출력합니다.
 */
public class RsaKeyGeneratorExample {

  /**
   * RSA 키 쌍 생성 (기본 2048 비트)
   *
   * @return KeyPair (Public Key + Private Key)
   * @throws Exception
   */
  public static KeyPair generateRsaKeyPair() throws Exception {
    return generateRsaKeyPair(2048);
  }

  /**
   * RSA 키 쌍 생성 (비트 크기 지정)
   *
   * @param keySize 키 크기 (비트) - 일반적으로 1024, 2048, 4096
   * @return KeyPair (Public Key + Private Key)
   * @throws Exception
   */
  public static KeyPair generateRsaKeyPair(int keySize) throws Exception {
    // KeyPairGenerator 초기화 (RSA 알고리즘)
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");

    // SecureRandom을 사용하여 랜덤성 강화
    SecureRandom secureRandom = new SecureRandom();

    // 키 크기 설정 및 초기화
    keyPairGenerator.initialize(keySize, secureRandom);

    // 키 쌍 생성
    KeyPair keyPair = keyPairGenerator.generateKeyPair();

    return keyPair;
  }

  /**
   * Public Key를 Base64 인코딩된 문자열로 변환
   *
   * @param publicKey Public Key
   * @return Base64 인코딩된 문자열
   */
  public static String publicKeyToBase64(PublicKey publicKey) {
    return Base64.getEncoder().encodeToString(publicKey.getEncoded());
  }

  /**
   * Private Key를 Base64 인코딩된 문자열로 변환
   *
   * @param privateKey Private Key
   * @return Base64 인코딩된 문자열
   */
  public static String privateKeyToBase64(PrivateKey privateKey) {
    return Base64.getEncoder().encodeToString(privateKey.getEncoded());
  }

  /**
   * Public Key를 PEM 형식으로 출력
   *
   * @param publicKey Public Key
   * @return PEM 형식 문자열
   */
  public static String publicKeyToPem(PublicKey publicKey) {
    String base64 = publicKeyToBase64(publicKey);
    StringBuilder pem = new StringBuilder();
    pem.append("-----BEGIN PUBLIC KEY-----\n");

    // 64자마다 줄바꿈
    int index = 0;
    while (index < base64.length()) {
      pem.append(base64, index, Math.min(index + 64, base64.length()));
      pem.append("\n");
      index += 64;
    }

    pem.append("-----END PUBLIC KEY-----");
    return pem.toString();
  }

  /**
   * Private Key를 PEM 형식으로 출력
   *
   * @param privateKey Private Key
   * @return PEM 형식 문자열
   */
  public static String privateKeyToPem(PrivateKey privateKey) {
    String base64 = privateKeyToBase64(privateKey);
    StringBuilder pem = new StringBuilder();
    pem.append("-----BEGIN PRIVATE KEY-----\n");

    // 64자마다 줄바꿈
    int index = 0;
    while (index < base64.length()) {
      pem.append(base64, index, Math.min(index + 64, base64.length()));
      pem.append("\n");
      index += 64;
    }

    pem.append("-----END PRIVATE KEY-----");
    return pem.toString();
  }

  /**
   * 키 정보 출력
   *
   * @param keyPair RSA 키 쌍
   */
  public static void printKeyInfo(KeyPair keyPair) {
    PublicKey publicKey = keyPair.getPublic();
    PrivateKey privateKey = keyPair.getPrivate();

    System.out.println("=".repeat(100));
    System.out.println("RSA 키 쌍 정보");
    System.out.println("=".repeat(100));

    System.out.println("\n[Public Key - 알고리즘]");
    System.out.println(publicKey.getAlgorithm());

    System.out.println("\n[Public Key - 형식]");
    System.out.println(publicKey.getFormat());

    System.out.println("\n[Private Key - 알고리즘]");
    System.out.println(privateKey.getAlgorithm());

    System.out.println("\n[Private Key - 형식]");
    System.out.println(privateKey.getFormat());

    System.out.println("\n" + "=".repeat(100));
  }

  /**
   * 메인 메서드 - RSA 키 생성 예제 실행
   *
   * @param args 명령행 인자
   */
  public static void main(String[] args) {
    try {
      System.out.println("\n" + "=".repeat(100));
      System.out.println("RSA 키 쌍 생성 예제");
      System.out.println("=".repeat(100));

      // ===== 예제 1: 기본 2048 비트 키 생성 =====
      System.out.println("\n[예제 1] 2048 비트 RSA 키 쌍 생성");
      System.out.println("-".repeat(100));

      KeyPair keyPair2048 = generateRsaKeyPair();
      printKeyInfo(keyPair2048);

      // Base64 형식으로 출력
      System.out.println("\n[Public Key - Base64]");
      System.out.println(publicKeyToBase64(keyPair2048.getPublic()));

      System.out.println("\n[Private Key - Base64]");
      System.out.println(privateKeyToBase64(keyPair2048.getPrivate()));

      // PEM 형식으로 출력
      System.out.println("\n[Public Key - PEM 형식]");
      System.out.println(publicKeyToPem(keyPair2048.getPublic()));

      System.out.println("\n[Private Key - PEM 형식]");
      System.out.println(privateKeyToPem(keyPair2048.getPrivate()));

      // ===== 예제 2: 1024 비트 키 생성 (빠른 테스트용) =====
      System.out.println("\n\n" + "=".repeat(100));
      System.out.println("[예제 2] 1024 비트 RSA 키 쌍 생성 (테스트용)");
      System.out.println("=".repeat(100));

      KeyPair keyPair1024 = generateRsaKeyPair(1024);

      System.out.println("\n[Public Key - PEM 형식]");
      System.out.println(publicKeyToPem(keyPair1024.getPublic()));

      System.out.println("\n[Private Key - PEM 형식]");
      System.out.println(privateKeyToPem(keyPair1024.getPrivate()));

      // ===== 예제 3: 4096 비트 키 생성 (높은 보안) =====
      System.out.println("\n\n" + "=".repeat(100));
      System.out.println("[예제 3] 4096 비트 RSA 키 쌍 생성 (높은 보안, 생성 시간이 오래 걸릴 수 있음)");
      System.out.println("=".repeat(100));

      long startTime = System.currentTimeMillis();
      KeyPair keyPair4096 = generateRsaKeyPair(4096);
      long endTime = System.currentTimeMillis();

      System.out.println("\n생성 시간: " + (endTime - startTime) + " ms");

      System.out.println("\n[Public Key - PEM 형식]");
      System.out.println(publicKeyToPem(keyPair4096.getPublic()));

      System.out.println("\n[Private Key - PEM 형식]");
      System.out.println(privateKeyToPem(keyPair4096.getPrivate()));

      // ===== 사용 가이드 =====
      System.out.println("\n\n" + "=".repeat(100));
      System.out.println("사용 가이드");
      System.out.println("=".repeat(100));
      System.out.println("1. Public Key는 공개해도 안전합니다. (암호화, 서명 검증에 사용)");
      System.out.println("2. Private Key는 절대 공개하지 말고 안전하게 보관하세요. (복호화, 서명 생성에 사용)");
      System.out.println("3. 키 크기 권장:");
      System.out.println("   - 1024 비트: 테스트용 (보안 취약, 프로덕션 비권장)");
      System.out.println("   - 2048 비트: 일반적인 용도 (권장)");
      System.out.println("   - 4096 비트: 높은 보안이 필요한 경우");
      System.out.println("4. PEM 형식은 파일로 저장하거나 다른 시스템과 호환할 때 사용");
      System.out.println("5. Base64 형식은 데이터베이스 저장이나 HTTP 전송 시 사용");
      System.out.println("=".repeat(100));

    } catch (Exception e) {
      System.err.println("오류 발생: " + e.getMessage());
      e.printStackTrace();
    }
  }
}

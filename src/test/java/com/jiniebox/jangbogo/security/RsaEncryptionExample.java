package com.jiniebox.jangbogo.security;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

import javax.crypto.Cipher;

/**
 * RSA 암호화/복호화 예제 클래스
 * 
 * Public Key로 암호화하고 Private Key로 복호화하는 예제를 제공합니다.
 */
public class RsaEncryptionExample {
    
    /**
     * Public Key로 데이터 암호화
     * 
     * @param data 암호화할 평문 데이터
     * @param publicKey Public Key
     * @return 암호화된 데이터 (Base64 인코딩)
     * @throws Exception
     */
    public static String encrypt(String data, PublicKey publicKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        
        byte[] encryptedBytes = cipher.doFinal(data.getBytes("UTF-8"));
        
        // Base64로 인코딩하여 문자열로 반환
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }
    
    /**
     * Private Key로 데이터 복호화
     * 
     * @param encryptedData 암호화된 데이터 (Base64 인코딩)
     * @param privateKey Private Key
     * @return 복호화된 평문 데이터
     * @throws Exception
     */
    public static String decrypt(String encryptedData, PrivateKey privateKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        
        // Base64 디코딩
        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedData);
        
        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
        
        return new String(decryptedBytes, "UTF-8");
    }
    
    /**
     * 메인 메서드 - RSA 암호화/복호화 예제 실행
     * 
     * @param args 명령행 인자
     */
    public static void main(String[] args) {
        try {
            System.out.println("\n" + "=".repeat(100));
            System.out.println("RSA 암호화/복호화 예제");
            System.out.println("=".repeat(100));
            
            // 1. RSA 키 쌍 생성
            System.out.println("\n[1단계] RSA 키 쌍 생성 (2048 비트)");
            System.out.println("-".repeat(100));
            
            KeyPair keyPair = RsaKeyGeneratorExample.generateRsaKeyPair(2048);
            PublicKey publicKey = keyPair.getPublic();
            PrivateKey privateKey = keyPair.getPrivate();
            
            System.out.println("✓ 키 쌍 생성 완료");
            
            // 2. 암호화할 데이터 준비
            System.out.println("\n[2단계] 암호화할 데이터 준비");
            System.out.println("-".repeat(100));
            
            String originalData = "안녕하세요! 이것은 RSA 암호화 테스트입니다.";
            System.out.println("원본 데이터: " + originalData);
            
            // 3. Public Key로 암호화
            System.out.println("\n[3단계] Public Key로 암호화");
            System.out.println("-".repeat(100));
            
            String encryptedData = encrypt(originalData, publicKey);
            System.out.println("암호화된 데이터 (Base64):");
            System.out.println(encryptedData);
            
            // 4. Private Key로 복호화
            System.out.println("\n[4단계] Private Key로 복호화");
            System.out.println("-".repeat(100));
            
            String decryptedData = decrypt(encryptedData, privateKey);
            System.out.println("복호화된 데이터: " + decryptedData);
            
            // 5. 검증
            System.out.println("\n[5단계] 검증");
            System.out.println("-".repeat(100));
            
            boolean isMatch = originalData.equals(decryptedData);
            System.out.println("원본과 복호화 데이터 일치 여부: " + (isMatch ? "✓ 일치" : "✗ 불일치"));
            
            // 6. 여러 데이터 암호화 테스트
            System.out.println("\n[6단계] 여러 데이터 암호화/복호화 테스트");
            System.out.println("-".repeat(100));
            
            String[] testData = {
                "test@example.com",
                "password123!@#",
                "사용자ID: admin",
                "12345",
                "https://api.example.com/endpoint"
            };
            
            System.out.printf("%-40s %-20s %-20s%n", "원본 데이터", "암호화 크기(Bytes)", "복호화 일치 여부");
            System.out.println("-".repeat(100));
            
            for (String data : testData) {
                String encrypted = encrypt(data, publicKey);
                String decrypted = decrypt(encrypted, privateKey);
                boolean match = data.equals(decrypted);
                
                System.out.printf("%-40s %-20d %-20s%n", 
                    data.length() > 40 ? data.substring(0, 37) + "..." : data,
                    Base64.getDecoder().decode(encrypted).length,
                    match ? "✓ 일치" : "✗ 불일치"
                );
            }
            
            // 7. 생성된 키 출력 (PEM 형식)
            System.out.println("\n\n[7단계] 생성된 키 출력 (PEM 형식)");
            System.out.println("=".repeat(100));
            
            System.out.println("\n[Public Key]");
            System.out.println(RsaKeyGeneratorExample.publicKeyToPem(publicKey));
            
            System.out.println("\n[Private Key]");
            System.out.println(RsaKeyGeneratorExample.privateKeyToPem(privateKey));
            
            // 8. 주의사항
            System.out.println("\n\n" + "=".repeat(100));
            System.out.println("⚠ 주의사항");
            System.out.println("=".repeat(100));
            System.out.println("1. RSA는 암호화할 수 있는 데이터 크기에 제한이 있습니다:");
            System.out.println("   - 2048 비트 키: 최대 약 245 바이트");
            System.out.println("   - 4096 비트 키: 최대 약 501 바이트");
            System.out.println("2. 큰 데이터는 AES 등 대칭키로 암호화하고, RSA로 대칭키를 암호화하는 방식 사용");
            System.out.println("3. Private Key는 절대 공개하거나 전송하지 마세요!");
            System.out.println("4. 프로덕션 환경에서는 키를 안전한 곳에 저장하세요 (Key Store, HSM 등)");
            System.out.println("=".repeat(100));
            
        } catch (Exception e) {
            System.err.println("오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }
}


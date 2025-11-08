package com.jiniebox.jangbogo.util.security;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * RSA 키 쌍 생성 유틸리티 클래스
 */
public class RsaKeyGenerator {
    
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
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        SecureRandom secureRandom = new SecureRandom();
        keyPairGenerator.initialize(keySize, secureRandom);
        return keyPairGenerator.generateKeyPair();
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
     * Public Key를 PEM 형식으로 변환
     * 
     * @param publicKey Public Key
     * @return PEM 형식 문자열
     */
    public static String publicKeyToPem(PublicKey publicKey) {
        String base64 = publicKeyToBase64(publicKey);
        StringBuilder pem = new StringBuilder();
        pem.append("-----BEGIN PUBLIC KEY-----\n");
        
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
     * Private Key를 PEM 형식으로 변환
     * 
     * @param privateKey Private Key
     * @return PEM 형식 문자열
     */
    public static String privateKeyToPem(PrivateKey privateKey) {
        String base64 = privateKeyToBase64(privateKey);
        StringBuilder pem = new StringBuilder();
        pem.append("-----BEGIN PRIVATE KEY-----\n");
        
        int index = 0;
        while (index < base64.length()) {
            pem.append(base64, index, Math.min(index + 64, base64.length()));
            pem.append("\n");
            index += 64;
        }
        
        pem.append("-----END PRIVATE KEY-----");
        return pem.toString();
    }
}


package com.jiniebox.jangbogo.util;

import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 대칭키 암호화 알고리즘 AES 방식으로 문자열 String 을 암호화/복호화 할 수 있는 클래스<br/>
 * 예제는 main 함수 참고
 * 
 * @author KIUNSEA
 */
public class StringEncrypter {

    public static String ALGORITHM = "AES/CBC/PKCS5Padding";
    
    /**
     * AES 암호화에 사용할 키를 생성합니다.
     * 
     * @param n 입력 가능 키 길이는 128, 192, 256
     * @return
     * @throws Exception
     */
    public static SecretKey generateKey(int n) throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(n);
        return keyGenerator.generateKey();
    }

    /**
     * 초기화 벡터(IV)를 생성합니다.
     * 
     * @return
     */
    public static IvParameterSpec generateIv() {
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        return new IvParameterSpec(iv);
    }

    /**
     * 문자열을 AES 암호화합니다.
     * 
     * @param algorithm
     * @param input
     * @param key
     * @param iv
     * @return Base64(Encrypt) Text
     * @throws Exception
     */
    public static String encrypt(String algorithm, String input, SecretKey key, IvParameterSpec iv) throws Exception {
        Cipher cipher = Cipher.getInstance(algorithm);
        cipher.init(Cipher.ENCRYPT_MODE, key, iv);
        byte[] encryptedTxt = cipher.doFinal(input.getBytes());
        String base64Txt = Base64.getUrlEncoder().encodeToString(encryptedTxt);
        return base64Txt;
    }

    /**
     * AES로 암호화된 문자열을 복호화합니다.
     * 
     * @param algorithm
     * @param base64Txt
     * @param key
     * @param iv
     * @return
     * @throws Exception
     */
    public static String decrypt(String algorithm, String base64Txt, SecretKey key, IvParameterSpec iv) throws Exception {
        byte[] encryptedTxt = Base64.getUrlDecoder().decode(base64Txt);
        Cipher cipher = Cipher.getInstance(algorithm);
        cipher.init(Cipher.DECRYPT_MODE, key, iv);
        byte[] plainText = cipher.doFinal(encryptedTxt);
        return new String(plainText);
    }
    
    /**
     * SecretKey 객체를 Base64 인코딩된 문자열로 변환
     * 
     * @param secretKey
     * @return
     */
    public static String encodeSecretKeyToBase64(SecretKey secretKey) {
        return Base64.getEncoder().encodeToString(secretKey.getEncoded());
    }
    
    /**
     * Base64 인코딩된 문자열 key를 SecretKey 객체로 변환
     * 
     * @param base64EncodedKey
     * @return
     */
    public static SecretKey decodeBase64ToSecretKey(String base64EncodedKey) {
        byte[] decodedKey = Base64.getDecoder().decode(base64EncodedKey);
        return new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
    }

    /**
     * IvParameterSpec 객체를 Base64 인코딩된 문자열로 변환
     * 
     * @param ivParameterSpec
     * @return
     */
    public static String encodeIvToBase64(IvParameterSpec ivParameterSpec) {
        return Base64.getEncoder().encodeToString(ivParameterSpec.getIV());
    }
    
    /**
     * Base64 인코딩된 문자열 iv를 IvParameterSpec 객체로 변환
     * 
     * @param base64EncodedIv
     * @return
     */
    public static IvParameterSpec decodeBase64ToIv(String base64EncodedIv) {
        byte[] decodedIv = Base64.getDecoder().decode(base64EncodedIv);
        return new IvParameterSpec(decodedIv);
    }

    public static void main(String[] args) throws Exception {
        /**
         * 평문의 문자열을 암호화하고 복호화 하는 예제
         */
        String input = "안녕하세요! 이것은 암호화될 메시지입니다.";
        SecretKey key = generateKey(128);
        IvParameterSpec iv = generateIv();

        String algorithm = StringEncrypter.ALGORITHM;
        String cipherText = encrypt(algorithm, input, key, iv);
        String plainText = decrypt(algorithm, cipherText, key, iv);

        System.out.println("원본 메시지: " + input);
        System.out.println("암호화된 메시지: " + cipherText);
        System.out.println("복호화된 메시지: " + plainText);
        
        /**
         * 암호화/복호화에 사용하는 key 와 iv 를 base64 문자열로 전환하고 이를 다시 원래의 클래스 인스턴스로 복원하는 예제
         */
        String keyBase64Str = encodeSecretKeyToBase64(key);
        String ivBase64Str = encodeIvToBase64(iv);
        SecretKey keyRecovery = decodeBase64ToSecretKey(keyBase64Str);
        IvParameterSpec ivRecovery = decodeBase64ToIv(ivBase64Str);
        
    }
}

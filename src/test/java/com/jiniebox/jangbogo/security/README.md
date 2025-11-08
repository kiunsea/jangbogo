# RSA 암호화 예제

**패키지:** `com.omnibuscode.security`  
**위치:** `src/test/java/com/omnibuscode/security/`

---

## 📁 클래스 구성

### 1. RsaKeyGeneratorExample.java
RSA 키 쌍(Public Key + Private Key)을 생성하는 예제입니다.

**주요 기능:**
- RSA 키 쌍 생성 (1024, 2048, 4096 비트)
- Public Key/Private Key를 Base64 형식으로 변환
- Public Key/Private Key를 PEM 형식으로 변환
- 키 정보 출력

**실행 방법:**
```bash
# Eclipse/IntelliJ에서 main() 메서드 실행
# 또는 명령행에서
java -cp ... com.omnibuscode.security.RsaKeyGeneratorExample
```

**출력 예시:**
```
=== RSA 키 쌍 생성 예제 ===

[예제 1] 2048 비트 RSA 키 쌍 생성
✓ 키 쌍 생성 완료

[Public Key - PEM 형식]
-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...
-----END PUBLIC KEY-----

[Private Key - PEM 형식]
-----BEGIN PRIVATE KEY-----
MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEA...
-----END PRIVATE KEY-----
```

---

### 2. RsaEncryptionExample.java
RSA를 사용하여 데이터를 암호화/복호화하는 예제입니다.

**주요 기능:**
- Public Key로 데이터 암호화
- Private Key로 데이터 복호화
- 여러 데이터 타입 테스트
- 암호화/복호화 검증

**실행 방법:**
```bash
# Eclipse/IntelliJ에서 main() 메서드 실행
java -cp ... com.omnibuscode.security.RsaEncryptionExample
```

**사용 예시:**
```java
// 1. 키 쌍 생성
KeyPair keyPair = RsaKeyGeneratorExample.generateRsaKeyPair(2048);

// 2. 암호화
String encrypted = RsaEncryptionExample.encrypt("비밀 데이터", keyPair.getPublic());

// 3. 복호화
String decrypted = RsaEncryptionExample.decrypt(encrypted, keyPair.getPrivate());
```

---

## 🔐 RSA 암호화 개념

### 비대칭 암호화 (Asymmetric Encryption)

```
[암호화]
평문 데이터 + Public Key  →  암호화된 데이터

[복호화]
암호화된 데이터 + Private Key  →  평문 데이터
```

### 주요 특징

1. **Public Key (공개 키)**
   - 공개해도 안전
   - 데이터 암호화에 사용
   - 서명 검증에 사용

2. **Private Key (개인 키)**
   - 절대 공개하면 안됨
   - 데이터 복호화에 사용
   - 서명 생성에 사용

---

## 📊 키 크기별 비교

| 키 크기 | 보안 수준 | 생성 속도 | 암호화 속도 | 최대 데이터 크기 | 용도 |
|---------|----------|----------|------------|----------------|------|
| 1024 비트 | 낮음 ⚠ | 빠름 | 빠름 | ~117 bytes | 테스트용만 |
| 2048 비트 | 중간 ✓ | 보통 | 보통 | ~245 bytes | 일반적인 용도 (권장) |
| 4096 비트 | 높음 ✓✓ | 느림 | 느림 | ~501 bytes | 높은 보안 필요 시 |

---

## 💡 사용 시나리오

### 시나리오 1: 비밀번호 저장
```java
// 서버에서 키 쌍 생성 (최초 1회)
KeyPair keyPair = RsaKeyGeneratorExample.generateRsaKeyPair(2048);

// Public Key는 클라이언트에 전달
String publicKeyPem = RsaKeyGeneratorExample.publicKeyToPem(keyPair.getPublic());

// Private Key는 서버에 안전하게 보관
String privateKeyPem = RsaKeyGeneratorExample.privateKeyToPem(keyPair.getPrivate());

// 클라이언트: 비밀번호 암호화 (Public Key 사용)
String encryptedPassword = RsaEncryptionExample.encrypt("myPassword123", publicKey);

// 서버: 비밀번호 복호화 (Private Key 사용)
String password = RsaEncryptionExample.decrypt(encryptedPassword, privateKey);
```

### 시나리오 2: API 키 암호화
```java
KeyPair keyPair = RsaKeyGeneratorExample.generateRsaKeyPair();

String apiKey = "sk-1234567890abcdef";
String encrypted = RsaEncryptionExample.encrypt(apiKey, keyPair.getPublic());

// 암호화된 API 키를 DB에 저장
saveToDatabase(encrypted);

// 필요할 때 복호화
String decryptedApiKey = RsaEncryptionExample.decrypt(encrypted, keyPair.getPrivate());
```

### 시나리오 3: 대용량 데이터 암호화 (Hybrid)
```java
// RSA는 작은 데이터만 암호화 가능하므로
// 대용량 데이터는 AES로 암호화하고, AES 키만 RSA로 암호화

// 1. AES 대칭키 생성 (예: 256비트)
String aesKey = generateRandomAesKey();

// 2. 대용량 데이터는 AES로 암호화
String encryptedData = AesEncryption.encrypt(largeData, aesKey);

// 3. AES 키만 RSA로 암호화
String encryptedAesKey = RsaEncryptionExample.encrypt(aesKey, rsaPublicKey);

// 4. 전송/저장: encryptedData + encryptedAesKey

// 5. 복호화 시:
String decryptedAesKey = RsaEncryptionExample.decrypt(encryptedAesKey, rsaPrivateKey);
String decryptedData = AesEncryption.decrypt(encryptedData, decryptedAesKey);
```

---

## ⚠ 주의사항

### 1. 데이터 크기 제한
RSA는 키 크기에 따라 암호화 가능한 데이터 크기가 제한됩니다:

```
최대 데이터 크기 = (키 크기 / 8) - 11 (PKCS#1 padding)

예시:
- 2048 비트 키: (2048/8) - 11 = 245 바이트
```

**큰 데이터는 Hybrid 암호화 방식을 사용하세요!**

### 2. Private Key 보안

```java
// ✗ 나쁜 예: Private Key를 하드코딩
String privateKey = "MIIEvQIBADANBgk...";

// ✓ 좋은 예: 환경 변수나 Key Store 사용
String privateKey = System.getenv("RSA_PRIVATE_KEY");

// ✓ 더 좋은 예: Java KeyStore 사용
KeyStore keyStore = KeyStore.getInstance("PKCS12");
PrivateKey privateKey = (PrivateKey) keyStore.getKey("mykey", password);
```

### 3. 키 재사용

```java
// 한 번 생성한 키 쌍은 재사용하세요
// 매번 새로 생성하면 이전에 암호화한 데이터를 복호화할 수 없습니다!

// ✓ 애플리케이션 시작 시 한 번만 로드
public class SecurityConfig {
    private static KeyPair keyPair;
    
    static {
        try {
            // 저장된 키 로드 또는 새로 생성
            keyPair = loadOrGenerateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public static KeyPair getKeyPair() {
        return keyPair;
    }
}
```

---

## 🔗 관련 리소스

### 참고 문서
- [Java Cryptography Architecture (JCA)](https://docs.oracle.com/javase/8/docs/technotes/guides/security/crypto/CryptoSpec.html)
- [RSA 알고리즘 설명](https://ko.wikipedia.org/wiki/RSA_%EC%95%94%ED%98%B8)

### 기존 프로젝트 코드
- `ClovaServlet.java` - RSA 서명 검증 예제 (line 366-382)
  - Public Key를 사용하여 서명을 검증하는 실제 사용 사례

---

## 🚀 빠른 시작

### 1. 키 생성만 필요한 경우
```java
RsaKeyGeneratorExample.main(null);
```

### 2. 암호화/복호화까지 필요한 경우
```java
RsaEncryptionExample.main(null);
```

### 3. 코드에서 직접 사용
```java
import com.omnibuscode.security.RsaKeyGeneratorExample;
import com.omnibuscode.security.RsaEncryptionExample;
import java.security.KeyPair;

// 키 생성
KeyPair keyPair = RsaKeyGeneratorExample.generateRsaKeyPair(2048);

// 암호화
String encrypted = RsaEncryptionExample.encrypt("데이터", keyPair.getPublic());

// 복호화
String decrypted = RsaEncryptionExample.decrypt(encrypted, keyPair.getPrivate());
```

---

**마지막 업데이트:** 2025-11-08


# FileZilla Server 1.11.1 완벽 호환 가이드

## 🎯 최종 해결 방법 (SimpleFtpTlsUploadTest)

FileZilla Server 1.11.1과의 완벽한 호환을 위해 다음 순서를 정확히 따라야 합니다.

---

## ✅ 적용된 완전한 해결책

### 올바른 순서 (RFC 4217 준수)

```java
// 1. FTPS 클라이언트 생성
FTPSClient ftpsClient = new FTPSClient("SSL", false);

// 2. FileZilla Server 호환 설정
ftpsClient.setEndpointCheckingEnabled(false);
ftpsClient.setUseEPSVwithIPv4(true);
ftpsClient.setBufferSize(0);
ftpsClient.setAutodetectUTF8(false);
ftpsClient.setControlKeepAliveTimeout(300);

// 3. 서버 연결
ftpsClient.connect(host, 21);

// 4. 로그인 (먼저!)
ftpsClient.login(user, pass);

// 5. TLS 보호 채널 설정 (로그인 후!)
ftpsClient.execPBSZ(0);
ftpsClient.execPROT("P");

// 6. ⭐⭐⭐ 핵심: TLS 세션 재개 강제
ftpsClient.setEnabledSessionCreation(false);

// 7. 전송 모드 설정
ftpsClient.enterLocalPassiveMode();
ftpsClient.setFileType(FTP.BINARY_FILE_TYPE);

// 8. 파일 전송 (이제 정상 작동!)
ftpsClient.storeFile(fileName, inputStream);
```

---

## 🔑 핵심 포인트

### 1. setEnabledSessionCreation(false) ⭐⭐⭐

**위치:** `execPROT("P")` 이후, 데이터 채널 열기 전

**역할:**
- 새 TLS 세션 생성을 막음
- 데이터 연결이 제어 연결의 TLS 세션을 재사용하도록 강제
- FileZilla Server 1.x의 세션 재개 요구사항 충족

### 2. RFC 4217 순서 준수

```
연결 → 로그인 → PBSZ → PROT → setEnabledSessionCreation(false) → 데이터 전송
```

**잘못된 순서:**
```
❌ 연결 → PBSZ → PROT → 로그인  (일부 서버에서 실패)
```

**올바른 순서:**
```
✅ 연결 → 로그인 → PBSZ → PROT → setEnabledSessionCreation(false)
```

### 3. SSL 프로토콜 사용

```java
new FTPSClient("SSL", false)  // FileZilla Server 권장
// vs
new FTPSClient("TLS", false)  // 일부 환경에서 문제
```

---

## 📋 SimpleFtpTlsUploadTest 실행 흐름

```
1. 설정 파일 로드 (ftp-test.properties)
   ↓
2. FTPS 클라이언트 생성 + 호환 설정 6가지
   ↓
3. 서버 연결
   ↓
4. 로그인 ⭐ (먼저!)
   ↓
5. PBSZ 0
   ↓
6. PROT P
   ↓
7. setEnabledSessionCreation(false) ⭐⭐⭐ (핵심!)
   ↓
8. Passive 모드 + Binary 타입
   ↓
9. 파일 업로드 → 성공! ✅
```

---

## 🔍 FileZilla Server 1.11.1 특성

### TLS 세션 재개 강제

FileZilla Server 1.x는:
- TLS 세션 재개를 **강제**로 요구
- GUI에서 끌 수 없음 (0.9.x와 차이점)
- 클라이언트가 세션을 재사용해야 함

### 해결 방법

**서버 설정:** (거의 불가능)
- FileZilla Server 1.x에서는 GUI로 변경 불가

**클라이언트 설정:** (유일한 해법)
- `setEnabledSessionCreation(false)` 사용
- 제어 연결의 TLS 세션을 데이터 연결에서 재사용

---

## 📝 ftp-test.properties 최종 설정

```properties
ftp.host=jiniebox.com
ftp.port=21
ftp.user=jiniebox
ftp.password=qhqh1923!
ftp.mode=PASSIVE       # 수동형 (필수)
ftp.prot=P             # 전체 암호화 (가능!)
```

---

## 🧪 테스트 실행

### IDE에서:
```
SimpleFtpTlsUploadTest.java
→ main() 메서드 우클릭
→ Run
```

### 성공 시 출력:
```
🔐 TLS 보안 채널 설정 중...
  - PBSZ 0 실행 완료
  - PROT P 설정 완료 (제어/데이터 채널 모두 암호화)
  - 세션 재개 강제 활성화 (FileZilla Server 1.x 호환) ⭐
    └─ 데이터 연결 시 제어 연결의 TLS 세션 재사용
  ✅ TLS 보안 채널 설정 완료

📤 파일 업로드 중...
  ✅ 파일 업로드 성공!
```

---

## 🔐 보안 수준

| 항목 | 상태 |
|------|------|
| 사용자명/비밀번호 | 🔒 TLS 암호화 |
| FTP 명령어 | 🔒 TLS 암호화 |
| 파일 데이터 | 🔒 TLS 암호화 (PROT P) |
| 전송 모드 | ✅ PASSIVE (방화벽 친화) |

**최고 보안 수준 달성!** 🔐

---

## 📚 참고 자료

### RFC 4217 - FTP over TLS
```
정확한 명령어 순서:
1. 연결
2. 로그인 (AUTH TLS 이후)
3. PBSZ 0
4. PROT P/C
5. 데이터 전송
```

### Apache Commons Net
- 버전: 3.11.1 사용 중
- `setEnabledSessionCreation(false)`: FileZilla Server 필수
- RFC 4217 순서 준수 필요

---

## ✨ 핵심 요약

1. ✅ `new FTPSClient("SSL", false)` 사용
2. ✅ 연결 → **로그인** → PBSZ → PROT (순서 중요!)
3. ✅ **`setEnabledSessionCreation(false)`** ⭐⭐⭐ 핵심!
4. ✅ PASSIVE 모드 사용
5. ✅ 6가지 호환 설정 적용

---

**Copyright © 2025 jiniebox.com**

**Contact**: kiunsea@gmail.com











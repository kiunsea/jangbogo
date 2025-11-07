# FTP TLS 테스트 설정 가이드

이 문서는 FTP over TLS (FTPS) 테스트를 실행하기 위한 설정 방법을 안내합니다.

---

## 🎯 테스트 목적

- FTP TLS 연결 기능 검증
- 파일 업로드/다운로드 테스트
- 다양한 FTP 서버 환경 호환성 확인

---

## ⚙️ 설정 방법

### 방법 1: 환경 변수 사용 (권장)

#### Windows PowerShell
```powershell
$env:FTP_HOST = "ftp.example.com"
$env:FTP_PORT = "21"
$env:FTP_USER = "your_username"
$env:FTP_PASS = "your_password"
```

#### Windows CMD
```batch
set FTP_HOST=ftp.example.com
set FTP_PORT=21
set FTP_USER=your_username
set FTP_PASS=your_password
```

#### Linux/Mac
```bash
export FTP_HOST=ftp.example.com
export FTP_PORT=21
export FTP_USER=your_username
export FTP_PASS=your_password
```

### 방법 2: 프로퍼티 파일 사용

1. **예제 파일 복사**
```bash
cp src/test/resources/ftp-test.properties.example src/test/resources/ftp-test.properties
```

2. **파일 편집**
```properties
ftp.host=ftp.example.com
ftp.port=21
ftp.user=your_username
ftp.password=your_password
```

3. **.gitignore 확인**
`ftp-test.properties` 파일이 Git에 커밋되지 않도록 확인:
```
# .gitignore에 이미 추가됨
src/test/resources/ftp-test.properties
```

---

## 🧪 테스트 실행

### 1. 전체 FTP 테스트 실행

```bash
# @Disabled 어노테이션을 먼저 제거해야 합니다
./gradlew test --tests "FtpTlsConnectionTest"
```

### 2. 특정 테스트만 실행

```bash
# 기본 연결 테스트
./gradlew test --tests "FtpTlsConnectionTest.testBasicFtpsConnection"

# 파일 목록 조회
./gradlew test --tests "FtpTlsConnectionTest.testListDirectories"

# 파일 업로드
./gradlew test --tests "FtpTlsConnectionTest.testUploadFile"
```

### 3. 설정 테스트 실행

```bash
./gradlew test --tests "FtpTlsConfigTest"
```

---

## 🌐 공개 테스트 서버

테스트용으로 사용 가능한 공개 FTP 서버:

### 1. DLPTEST.COM
```
Host: ftp.dlptest.com
Port: 21
User: dlpuser
Pass: rNrKYTX9g7z3RgJRmxWuGHbeu
```

**설정 예시:**
```batch
set FTP_HOST=ftp.dlptest.com
set FTP_PORT=21
set FTP_USER=dlpuser
set FTP_PASS=rNrKYTX9g7z3RgJRmxWuGHbeu
```

### 2. Rebex Test Server
```
Host: test.rebex.net
Port: 21
User: demo
Pass: password
```

**설정 예시:**
```batch
set FTP_HOST=test.rebex.net
set FTP_PORT=21
set FTP_USER=demo
set FTP_PASS=password
```

---

## 🔧 문제 해결

### 문제 1: 테스트가 @Disabled 상태

**해결:**
`FtpTlsConnectionTest.java` 파일에서 다음 줄을 제거:
```java
@Disabled("실제 FTP 서버 정보 필요 - 수동 테스트용")
```

### 문제 2: Connection Timeout

**원인:** 방화벽 또는 잘못된 호스트

**해결:**
```bash
# 포트 확인
telnet ftp.example.com 21

# 또는 PowerShell
Test-NetConnection -ComputerName ftp.example.com -Port 21
```

### 문제 3: SSL Handshake 실패

**원인:** 자체 서명 인증서

**해결:** 
`testFtpsConnectionWithSelfSignedCert` 테스트 사용

### 문제 4: 425 Can't open data connection

**원인:** Passive 모드 필요

**해결:**
이미 테스트에 Passive 모드가 설정되어 있습니다:
```java
ftpsClient.enterLocalPassiveMode();
```

---

## 📊 테스트 커버리지

### 현재 테스트 항목

| 테스트 | 설명 | 상태 |
|--------|------|------|
| testBasicFtpsConnection | 기본 연결 및 로그인 | ✅ |
| testFtpsConnectionWithSelfSignedCert | 자체 서명 인증서 | ✅ |
| testListDirectories | 디렉터리 목록 조회 | ✅ |
| testChangeDirectory | 디렉터리 변경 | ✅ |
| testDownloadFile | 파일 다운로드 | ✅ |
| testUploadFile | 파일 업로드 | ✅ |
| testTlsProtocolVersions | TLS 버전 테스트 | ✅ |
| testConnectionWithTimeout | 타임아웃 설정 | ✅ |
| testActiveAndPassiveMode | Active/Passive 모드 | ✅ |
| testConnectionStatus | 연결 상태 확인 | ✅ |
| testEncryptedDataChannel | 암호화 채널 | ✅ |
| testInvalidCredentials | 잘못된 인증 | ✅ |
| testReconnection | 재연결 | ✅ |
| testCustomFtpCommands | 커스텀 명령 | ✅ |

---

## 🔍 디버그 모드

SSL/TLS 디버그 정보를 보려면:

```java
// 테스트 클래스의 setUp() 메서드에 추가
System.setProperty("javax.net.debug", "ssl,handshake");
```

또는 실행 시:
```bash
./gradlew test --tests FtpTlsConnectionTest -Djavax.net.debug=ssl,handshake
```

---

## 📝 예제 테스트 실행 로그

```
[INFO] FTP 서버 정보: ftp.dlptest.com:21
[INFO] 서버 응답 코드: 220
[INFO] FTP 로그인 성공: dlpuser
[INFO] 서버 시스템 타입: UNIX Type: L8
[INFO] 현재 디렉터리: /
[INFO] 파일/디렉터리 개수: 3
[INFO] [FILE] test.txt - 1024 bytes
[INFO] [DIR] uploads - 0 bytes
[INFO] 파일 업로드 성공: test_upload_1699123456789.txt
[INFO] 테스트 파일 삭제: true
```

---

## 🛡️ 보안 권장사항

1. **환경 변수 사용**: 코드나 프로퍼티 파일에 비밀번호 저장 금지
2. **프로퍼티 파일 보호**: `.gitignore`에 추가
3. **인증서 검증**: 프로덕션에서는 항상 인증서 검증
4. **최소 권한 원칙**: FTP 계정에 필요한 최소 권한만 부여
5. **정기적인 비밀번호 변경**

---

## 📚 참고 자료

- [Apache Commons Net](https://commons.apache.org/proper/commons-net/)
- [FTPSClient JavaDoc](https://commons.apache.org/proper/commons-net/apidocs/org/apache/commons/net/ftp/FTPSClient.html)
- [RFC 4217 - FTP over TLS](https://www.rfc-editor.org/rfc/rfc4217)

---

## 🤝 기여

FTP 테스트 관련 개선 사항이나 버그 발견 시:
- GitHub Issues: https://github.com/kiunsea/jangbogo/issues
- Email: kiunsea@gmail.com

---

**Copyright © 2025 jiniebox.com**


# FTP TLS 테스트 - Quick Start Guide

Main 함수로 실행 가능한 간단한 FTP TLS 텍스트 파일 업로드 테스트입니다.

---

## 🎯 개요

`SimpleFtpTlsUploadTest.java`는 main 메서드로 직접 실행할 수 있는 FTP over TLS 연결 및 파일 업로드 테스트 프로그램입니다.

**테스트 내용:**
1. ✅ FTP TLS 서버 연결
2. ✅ 로그인 인증
3. ✅ 디렉터리 목록 조회
4. ✅ 텍스트 파일 업로드
5. ✅ 업로드 확인
6. ✅ 파일 삭제

---

## 🚀 가장 빠른 실행 방법 (IDE)

### IntelliJ IDEA / Eclipse

1. **파일 열기**
   - `src/test/java/com/jiniebox/jangbogo/SimpleFtpTlsUploadTest.java`

2. **환경 변수 설정 (선택사항)**
   - 기본값으로 공개 테스트 서버 사용 (dlptest.com)
   - 다른 서버 사용 시 환경 변수 설정

3. **실행**
   - `main()` 메서드에서 **마우스 우클릭**
   - **"Run 'SimpleFtpTlsUploadTest.main()'"** 선택

4. **결과 확인**
   - 콘솔에서 실시간 진행 상황 확인

---

## ⚙️ 환경 변수 설정 (선택사항)

### 기본값 사용 (권장 - 처음 테스트)

환경 변수를 설정하지 않으면 자동으로 공개 테스트 서버 사용:
- Host: `ftp.dlptest.com`
- Port: `21`
- User: `dlpuser`
- Pass: `rNrKYTX9g7z3RgJRmxWuGHbeu`

**별도 설정 없이 바로 실행 가능!** ✨

### 다른 서버 사용하기

#### Windows (CMD)
```batch
set FTP_HOST=your-ftp-server.com
set FTP_PORT=21
set FTP_USER=your_username
set FTP_PASS=your_password
```

#### Windows (PowerShell)
```powershell
$env:FTP_HOST = "your-ftp-server.com"
$env:FTP_PORT = "21"
$env:FTP_USER = "your_username"
$env:FTP_PASS = "your_password"
```

#### Linux/Mac
```bash
export FTP_HOST=your-ftp-server.com
export FTP_PORT=21
export FTP_USER=your_username
export FTP_PASS=your_password
```

---

## 📝 실행 스크립트 사용

### Windows
```batch
# 배치 파일 실행
run-ftp-test.bat

# 또는 PowerShell
.\run-ftp-test.ps1
```

### Linux/Mac
```bash
# 실행 권한 부여
chmod +x run-ftp-test.sh

# 실행
./run-ftp-test.sh
```

---

## 💻 코드 구조

### Main 함수 내 로직 흐름

```java
public static void main(String[] args) {
    // 1. 환경 변수에서 FTP 서버 정보 로드
    String ftpHost = System.getenv().getOrDefault("FTP_HOST", "ftp.dlptest.com");
    
    // 2. FTPS 클라이언트 생성
    FTPSClient ftpsClient = new FTPSClient("TLS", false);
    
    // 3. 자체 서명 인증서 허용 (테스트용)
    ftpsClient.setTrustManager(...);
    
    // 4. 서버 연결
    ftpsClient.connect(ftpHost, ftpPort);
    
    // 5. TLS 보안 채널 설정
    ftpsClient.execPBSZ(0);
    ftpsClient.execPROT("P");
    
    // 6. 로그인
    ftpsClient.login(ftpUser, ftpPassword);
    
    // 7. 전송 모드 설정
    ftpsClient.setFileType(FTP.BINARY_FILE_TYPE);
    ftpsClient.enterLocalPassiveMode();
    
    // 8. 디렉터리 확인
    String currentDir = ftpsClient.printWorkingDirectory();
    
    // 9. 파일 목록 조회
    FTPFile[] files = ftpsClient.listFiles();
    
    // 10. 테스트 파일 생성
    String fileName = "jangbogo_test_" + timestamp + ".txt";
    String content = "테스트 내용...";
    
    // 11. 파일 업로드
    ftpsClient.storeFile(fileName, inputStream);
    
    // 12. 업로드 확인
    FTPFile[] uploaded = ftpsClient.listFiles(fileName);
    
    // 13. 파일 삭제 (정리)
    ftpsClient.deleteFile(fileName);
    
    // 14. 연결 종료
    ftpsClient.logout();
    ftpsClient.disconnect();
}
```

**모든 로직이 main 메서드 안에 순차적으로 구현되어 있습니다!** ✅

---

## 📊 예상 실행 결과

```
═══════════════════════════════════════════════════════════
  FTP over TLS (FTPS) 텍스트 파일 업로드 테스트
═══════════════════════════════════════════════════════════

📋 FTP 서버 정보:
  - Host: ftp.dlptest.com
  - Port: 21
  - User: dlpuser
  - Pass: rNr***

🔧 FTPS 클라이언트 생성 중...
  ⚠️  자체 서명 인증서 허용 (테스트 전용)
  ✅ 클라이언트 생성 완료

🌐 FTP 서버 연결 중...
  - 연결 대상: ftp.dlptest.com:21
  - 응답 코드: 220
  - 응답 메시지: 220 DLP Test FTP Server
  ✅ 서버 연결 성공

🔐 TLS 보안 채널 설정 중...
  - PBSZ 0 실행 완료
  - PROT P 실행 완료 (데이터 채널 암호화)
  ✅ TLS 보안 채널 설정 완료

🔑 FTP 서버 로그인 중...
  - 사용자: dlpuser
  ✅ 로그인 성공

⚙️  FTP 전송 모드 설정 중...
  - 파일 타입: BINARY
  - 전송 모드: PASSIVE
  ✅ 전송 모드 설정 완료

📁 현재 디렉터리 확인 중...
  - 현재 위치: /

📂 파일 목록 조회 중...
  - 파일/폴더 개수: 2
  - 목록:
    [FILE] test.txt (1024 bytes)
    [DIR] uploads (0 bytes)

📝 업로드할 테스트 파일 생성 중...
  - 파일명: jangbogo_test_20251105_143530.txt
  - 파일 크기: 512 bytes
  ✅ 테스트 파일 생성 완료

📤 파일 업로드 중...
  - 업로드 대상: jangbogo_test_20251105_143530.txt
  ✅ 파일 업로드 성공!

🔍 업로드된 파일 확인 중...
  - 파일명: jangbogo_test_20251105_143530.txt
  - 크기: 512 bytes
  - 수정 시간: Tue Nov 05 14:35:30 KST 2025
  ✅ 파일 확인 완료

🗑️  테스트 파일 삭제 중...
  ✅ 파일 삭제 완료

═══════════════════════════════════════════════════════════
  ✅ 모든 테스트 완료!
═══════════════════════════════════════════════════════════

테스트 결과 요약:
  ✅ FTP TLS 연결
  ✅ 로그인 인증
  ✅ 디렉터리 목록 조회
  ✅ 텍스트 파일 업로드
  ✅ 파일 확인
  ✅ 파일 삭제

FTP TLS 연결이 정상적으로 작동합니다! 🎉

🔌 FTP 연결 종료 중...
  ✅ 연결 종료 완료

프로그램을 종료합니다.
```

---

## 🛠️ 커스터마이징

### 업로드할 파일 내용 변경

`SimpleFtpTlsUploadTest.java`의 9번 섹션 (line ~160)에서:

```java
StringBuilder content = new StringBuilder();
content.append("원하는 내용 입력\n");
content.append("여러 줄 추가 가능\n");
```

### 파일명 변경

```java
String testFileName = "custom_name_" + timestamp + ".txt";
```

### 업로드 후 삭제하지 않기

12번 섹션 주석 처리:
```java
// boolean deleteSuccess = ftpsClient.deleteFile(testFileName);
```

---

## 🔧 문제 해결

### Connection refused

**원인:** 서버 접속 불가

**해결:**
```batch
# 네트워크 연결 테스트
ping ftp.dlptest.com

# 포트 확인
telnet ftp.dlptest.com 21
```

### Login failed

**원인:** 잘못된 인증 정보

**해결:**
```java
// 코드에서 직접 확인
System.out.println("FTP_USER: " + System.getenv("FTP_USER"));
System.out.println("FTP_PASS: " + System.getenv("FTP_PASS"));
```

### Upload failed

**원인:** 쓰기 권한 없음

**해결:**
- 다른 디렉터리 시도
- 또는 쓰기 권한이 있는 서버 사용

---

## 📦 프로젝트 파일

```
jangbogo/
├── src/test/java/com/jiniebox/jangbogo/
│   ├── SimpleFtpTlsUploadTest.java       ⭐ Main 클래스
│   ├── FtpTlsConnectionTest.java         (JUnit 테스트)
│   ├── FtpTlsConfigTest.java            (설정 테스트)
│   ├── SIMPLE_FTP_TEST_README.md        (이 문서)
│   └── README_FTP_TEST.md
│
├── src/test/resources/
│   ├── ftp-test.properties.example      (설정 예제)
│   └── FTP_TEST_SETUP.md
│
├── run-ftp-test.bat                     (Windows 실행 스크립트)
├── run-ftp-test.ps1                     (PowerShell 스크립트)
└── run-ftp-test.sh                      (Linux/Mac 스크립트)
```

---

## 🌟 장점

### SimpleFtpTlsUploadTest의 특징

✅ **간단함**: 모든 로직이 main 메서드 안에 있음
✅ **독립적**: JUnit 의존성 없음 (단순 Java 애플리케이션)
✅ **직관적**: 단계별로 진행 상황 출력
✅ **안전함**: 테스트 후 자동으로 파일 삭제
✅ **유연함**: 환경 변수로 서버 정보 변경 가능

### 다른 테스트 클래스와 비교

| 특징 | SimpleFtpTlsUploadTest | FtpTlsConnectionTest |
|------|------------------------|----------------------|
| 실행 방법 | main() 메서드 | JUnit 테스트 |
| 의존성 | Commons Net만 | JUnit + Commons Net |
| 용도 | 빠른 수동 테스트 | 자동화된 단위 테스트 |
| 로직 위치 | main 안에 모두 | 여러 메서드로 분리 |
| 출력 | 상세한 진행 상황 | 테스트 결과만 |

---

## 💡 사용 시나리오

### 시나리오 1: 처음 FTP TLS 테스트
```
1. IDE에서 SimpleFtpTlsUploadTest.java 열기
2. main() 우클릭 → Run
3. 콘솔에서 결과 확인
```

### 시나리오 2: 특정 FTP 서버 테스트
```
1. 환경 변수 설정:
   set FTP_HOST=my-server.com
   set FTP_USER=myuser
   set FTP_PASS=mypass

2. IDE 재시작 (환경 변수 적용)
3. main() 실행
```

### 시나리오 3: 정기적인 연결 테스트
```
1. run-ftp-test.bat 실행
2. 자동으로 빌드 → 테스트 실행
3. 결과 확인
```

---

## 🔐 보안 고려사항

### ⚠️ 현재 코드 (테스트 전용)

```java
// 자체 서명 인증서 허용 - 테스트 전용!
ftpsClient.setTrustManager(new X509TrustManager() { ... });
```

### ✅ 프로덕션 환경

프로덕션에서는 다음과 같이 변경:

```java
// 자체 서명 인증서 허용 코드 제거
// ftpsClient.setTrustManager(...); // 이 줄 삭제

// 기본 TrustManager 사용 (정상적인 인증서 검증)
```

---

## 📞 문의

문제가 발생하거나 질문이 있으면:

- **Email**: kiunsea@gmail.com
- **GitHub Issues**: https://github.com/kiunsea/jangbogo/issues
- **Website**: https://www.omnibuscode.com

---

## 📚 추가 문서

- **상세 테스트**: `README_FTP_TEST.md`
- **설정 가이드**: `FTP_TEST_SETUP.md`
- **JUnit 테스트**: `FtpTlsConnectionTest.java`

---

**Copyright © 2025 jiniebox.com**

**Contact**: kiunsea@gmail.com


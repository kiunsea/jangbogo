# FTP over TLS (FTPS) 연결 테스트 가이드

## 📋 개요

Jangbogo 프로젝트에서 FTP over TLS (FTPS) 연결을 테스트하기 위한 테스트 클래스입니다.

## 🧪 테스트 클래스

### 1. FtpTlsConnectionTest.java
FTP TLS 연결의 핵심 기능을 테스트합니다.

**테스트 항목:**
- ✅ 기본 FTPS 연결 및 로그인
- ✅ 자체 서명 인증서 처리
- ✅ 디렉터리 목록 조회
- ✅ 디렉터리 변경
- ✅ 파일 다운로드
- ✅ 파일 업로드
- ✅ TLS 프로토콜 버전 (TLS 1.2)
- ✅ 타임아웃 설정
- ✅ Active/Passive 모드
- ✅ 연결 상태 확인
- ✅ 암호화된 데이터 채널
- ✅ 잘못된 인증 정보 처리
- ✅ 재연결 테스트
- ✅ 커스텀 FTP 명령 실행

### 2. FtpTlsConfigTest.java
설정 파일 로드 및 설정 기반 연결을 테스트합니다.

**테스트 항목:**
- ✅ 환경 변수 로드
- ✅ 프로퍼티 파일 로드
- ✅ 빌더 패턴 설정
- ✅ 설정 기반 클라이언트 생성

---

## 🚀 Quick Start

### 1. 의존성 추가 확인

`build.gradle`에 다음 의존성이 있는지 확인:

```gradle
implementation 'commons-net:commons-net:3.11.1'
```

### 2. 환경 변수 설정 (권장)

#### Windows
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

### 3. 또는 프로퍼티 파일 사용

```bash
# 설정 파일 복사
cp src/test/resources/ftp-test.properties.example src/test/resources/ftp-test.properties

# 파일 편집 (실제 FTP 서버 정보 입력)
notepad src/test/resources/ftp-test.properties
```

### 4. 테스트 실행

```bash
# @Disabled 어노테이션 제거 후
./gradlew test --tests FtpTlsConnectionTest

# 특정 테스트만 실행
./gradlew test --tests FtpTlsConnectionTest.testBasicFtpsConnection
```

---

## 📖 사용 예제

### 기본 연결 예제

```java
FTPSClient ftpsClient = new FTPSClient("TLS", false);

try {
    // 연결
    ftpsClient.connect("ftp.example.com", 21);
    
    // TLS 설정
    ftpsClient.execPBSZ(0);
    ftpsClient.execPROT("P");
    
    // 로그인
    boolean success = ftpsClient.login("username", "password");
    
    if (success) {
        // Passive 모드
        ftpsClient.enterLocalPassiveMode();
        
        // 파일 목록 조회
        FTPFile[] files = ftpsClient.listFiles();
        
        for (FTPFile file : files) {
            System.out.println(file.getName());
        }
    }
    
} finally {
    if (ftpsClient.isConnected()) {
        ftpsClient.logout();
        ftpsClient.disconnect();
    }
}
```

### 설정 기반 연결 예제

```java
// 설정 생성
FtpConfig config = FtpConfig.builder()
    .host("ftp.example.com")
    .port(21)
    .username("user")
    .password("pass")
    .protocol("TLS")
    .implicit(false)
    .passiveMode(true)
    .connectTimeout(10000)
    .dataTimeout(30000)
    .build();

// 클라이언트 생성
FTPSClient client = new FTPSClient(config.getProtocol(), config.isImplicit());
client.setConnectTimeout(config.getConnectTimeout());
client.setDataTimeout(config.getDataTimeout());

// 연결
client.connect(config.getHost(), config.getPort());
client.execPBSZ(0);
client.execPROT("P");
client.login(config.getUsername(), config.getPassword());

if (config.isPassiveMode()) {
    client.enterLocalPassiveMode();
}
```

---

## 🔐 보안 주의사항

### 1. 인증서 검증

**프로덕션 환경:**
```java
// ✅ 올바른 방법: 인증서 검증
FTPSClient ftpsClient = new FTPSClient("TLS", false);
// 기본 TrustManager 사용 (인증서 검증)
```

**테스트 환경 (자체 서명 인증서):**
```java
// ⚠️ 테스트 전용: 모든 인증서 신뢰
ftpsClient.setTrustManager(trustAllManager);
```

### 2. 비밀번호 관리

❌ **하지 말아야 할 것:**
```java
// 코드에 하드코딩
String password = "mypassword";  // 절대 금지!
```

✅ **권장 방법:**
```java
// 환경 변수 사용
String password = System.getenv("FTP_PASS");

// 또는 외부 설정 파일 (Git 제외)
Properties props = loadSecureProperties();
String password = props.getProperty("ftp.password");
```

### 3. .gitignore 설정

다음 파일들을 `.gitignore`에 추가:
```
# FTP 테스트 설정 (민감 정보 포함)
src/test/resources/ftp-test.properties
**/ftp-test.properties
```

---

## 📊 테스트 시나리오

### 시나리오 1: 공개 FTP 서버 테스트
```java
// anonymous 로그인 지원 서버
FTP_HOST=ftp.dlptest.com
FTP_PORT=21
FTP_USER=dlpuser
FTP_PASS=rNrKYTX9g7z3RgJRmxWuGHbeu
```

### 시나리오 2: 프라이빗 FTP 서버
```java
// 회사 내부 FTP 서버
FTP_HOST=internal-ftp.company.com
FTP_PORT=990  // Implicit TLS
FTP_USER=employee123
FTP_PASS=SecurePassword123!
```

### 시나리오 3: Explicit vs Implicit TLS

**Explicit TLS (포트 21):**
```java
FTPSClient client = new FTPSClient("TLS", false);
client.connect(host, 21);
client.execPBSZ(0);
client.execPROT("P");
```

**Implicit TLS (포트 990):**
```java
FTPSClient client = new FTPSClient("TLS", true);
client.connect(host, 990);
// PBSZ/PROT 명령 불필요
```

---

## 🐛 문제 해결

### 문제 1: `Connection refused`

**원인:** 방화벽 또는 서버 미실행

**해결:**
```bash
# 포트 확인
telnet ftp.example.com 21

# Windows 방화벽 확인
netsh advfirewall firewall show rule name=all | findstr 21
```

### 문제 2: `SSL handshake failed`

**원인:** TLS 버전 불일치 또는 인증서 문제

**해결:**
```java
// TLS 1.2 명시
ftpsClient.setEnabledProtocols(new String[]{"TLSv1.2"});

// 또는 자체 서명 인증서 허용 (테스트용)
ftpsClient.setTrustManager(createTrustAllManager());
```

### 문제 3: `425 Can't open data connection`

**원인:** Active 모드에서 방화벽 차단

**해결:**
```java
// Passive 모드로 변경
ftpsClient.enterLocalPassiveMode();
```

### 문제 4: `Authentication failed`

**원인:** 잘못된 사용자명/비밀번호

**해결:**
```java
// 환경 변수 확인
System.out.println("FTP_USER: " + System.getenv("FTP_USER"));
System.out.println("FTP_PASS: " + (System.getenv("FTP_PASS") != null ? "***" : "null"));

// 특수문자 이스케이프 확인
```

---

## 📚 추가 리소스

### Apache Commons Net 문서
- [공식 문서](https://commons.apache.org/proper/commons-net/)
- [FTPSClient API](https://commons.apache.org/proper/commons-net/apidocs/org/apache/commons/net/ftp/FTPSClient.html)

### FTP/FTPS 표준
- [RFC 959 - FTP](https://www.rfc-editor.org/rfc/rfc959)
- [RFC 4217 - FTP over TLS](https://www.rfc-editor.org/rfc/rfc4217)

### 공개 테스트 FTP 서버
- [DLPTEST.COM](https://dlptest.com/ftp-test/)
- [Rebex Test Server](https://test.rebex.net/)

---

## 🤝 기여

FTP 테스트 관련 개선 사항이나 추가 시나리오가 있으면:
- GitHub Issues: https://github.com/kiunsea/jangbogo/issues
- Pull Request 환영

---

## 📄 라이선스

이 테스트 코드는 Jangbogo 프로젝트의 AGPL-3.0-or-later 라이선스를 따릅니다.

---

**Copyright © 2025 [jiniebox.com](https://jiniebox.com)**

**Contact**: kiunsea@gmail.com


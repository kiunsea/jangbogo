# FileZilla Server 1.11.1 설정 가이드

Jangbogo FTP TLS 테스트와 FileZilla Server 1.11.1의 호환성 설정 가이드입니다.

---

## ❌ 발생했던 에러들

### 에러 1: TLS 세션 재개 문제
```
425 Unable to build data connection: TLS session of data connection not resumed
```

### 에러 2: PROT C 거부
```
534 Protection level C not allowed
```

---

## ✅ 완전한 해결 방법

### 클라이언트 측 해결 (적용 완료!) ⭐

**SimpleFtpTlsUploadTest.java**에 다음 설정 추가:

```java
FTPSClient ftpsClient = new FTPSClient("TLS", false);

// 🔑 핵심 해결책: 엔드포인트 체크 비활성화
ftpsClient.setEndpointCheckingEnabled(false);

// 추가 최적화
ftpsClient.setUseEPSVwithIPv4(true);
ftpsClient.setBufferSize(0);

// 연결 후
ftpsClient.execPBSZ(0);
ftpsClient.execPROT("P");  // 이제 정상 작동!
```

**핵심**: `setEndpointCheckingEnabled(false)`가 TLS 세션 재개 문제를 해결합니다!

---

## 🖥️ FileZilla Server 설정 (선택사항)

클라이언트 수정으로 해결되지만, 서버 설정도 최적화할 수 있습니다.

### FileZilla Server 1.11.1 설정 방법

#### 1. FileZilla Server 관리 인터페이스

**실행:**
- FileZilla Server 관리 프로그램 시작
- 서버 주소: 127.0.0.1 (로컬) 또는 서버 IP
- 관리자 비밀번호 입력

#### 2. FTP over TLS 설정

**메뉴:** Edit → Settings → FTP over TLS settings

**권장 설정:**

```
┌─────────────────────────────────────────────────┐
│ FTP over TLS settings                           │
├─────────────────────────────────────────────────┤
│                                                 │
│ Protocol:                                       │
│ ○ Plain FTP (insecure)                         │
│ ● Explicit FTP over TLS                        │ ⭐
│ ○ Implicit FTP over TLS                        │
│                                                 │
│ TLS Version:                                    │
│ Minimum TLS version: [TLS 1.2 ▼]              │ ⭐
│                                                 │
│ Session resumption:                             │
│ ☐ Allow session resumption on data connection  │ ⭐ 체크 해제!
│                                                 │
│ Certificate:                                    │
│ [Browse...] your-certificate.pfx               │
│ Password: ********                              │
│                                                 │
│ ☑ Require TLS for data connection             │
│ ☑ Require TLS for control connection          │
│                                                 │
└─────────────────────────────────────────────────┘
```

**핵심 설정:**
1. ✅ **Explicit FTP over TLS** 선택 (포트 21)
2. ✅ **Minimum TLS version: TLS 1.2**
3. ✅ **Allow session resumption**: 체크 해제 ⭐

#### 3. 사용자 권한 설정

**메뉴:** Edit → Users

```
사용자: jiniebox
비밀번호: qhqh1923!

Shared folders:
  ☑ Read
  ☑ Write
  ☑ Delete
  ☑ Append
  
  Directories:
  - / (루트) 또는 특정 폴더
```

#### 4. 일반 설정

**메뉴:** Edit → Settings → General settings

```
Passive mode settings:
  ☑ Use custom port range
  From: 50000
  To: 51000
  
  ☑ Use the following IP: (서버 IP 입력)
  또는
  ☑ Retrieve external IP address from: http://ip-api.com/line/?fields=query
```

#### 5. 방화벽 설정 (Windows)

```batch
# 제어 포트
netsh advfirewall firewall add rule name="FTP Server Control" dir=in action=allow protocol=TCP localport=21

# Passive 모드 데이터 포트
netsh advfirewall firewall add rule name="FTP Server Data Passive" dir=in action=allow protocol=TCP localport=50000-51000
```

#### 6. 서버 재시작

**FileZilla Server 재시작** (필수!)
- Server → Quit
- FileZilla Server 다시 시작

---

## 📋 현재 적용된 클라이언트 설정

### SimpleFtpTlsUploadTest.java

```java
// FileZilla Server 호환 설정
ftpsClient.setEndpointCheckingEnabled(false);  // ⭐ 핵심!
ftpsClient.setUseEPSVwithIPv4(true);
ftpsClient.setBufferSize(0);

// TLS 설정
ftpsClient.execPBSZ(0);
ftpsClient.execPROT("P");  // 전체 암호화

// 전송 모드
ftpsClient.enterLocalPassiveMode();  // 수동형
```

### ftp-test.properties

```properties
ftp.host=jiniebox.com
ftp.port=21
ftp.user=jiniebox
ftp.password=qhqh1923!
ftp.mode=PASSIVE
ftp.prot=P  ✅
```

---

## 🎯 테스트 실행

### IDE에서 실행
```
SimpleFtpTlsUploadTest.java → main() 우클릭 → Run
```

### 예상 성공 출력

```
🔧 FTPS 클라이언트 생성 중...
  - 엔드포인트 체크 비활성화 (TLS 세션 재개 문제 해결)
  ⚠️  자체 서명 인증서 허용 (테스트 전용)
  - EPSV with IPv4 활성화
  - 버퍼 크기 최적화
  ✅ 클라이언트 생성 완료 (FileZilla Server 1.x 호환)

🌐 FTP 서버 연결 중...
  - 응답 코드: 220
  ✅ 서버 연결 성공

🔐 TLS 보안 채널 설정 중...
  - PBSZ 0 실행 완료
  - PROT P 설정 완료 (제어/데이터 채널 모두 암호화)
  - 엔드포인트 체크 비활성화로 세션 재개 문제 해결
  ✅ TLS 보안 채널 설정 완료

🔑 FTP 서버 로그인 중...
  ✅ 로그인 성공

📤 파일 업로드 중...
  ✅ 파일 업로드 성공!
```

---

## 🔍 왜 작동하는가?

### setEndpointCheckingEnabled(false)의 역할

```
FileZilla Server (PROT P 요구)
        ↓
Apache Commons Net
  setEndpointCheckingEnabled(false)
  → TLS 세션 재개 체크 비활성화
  → 데이터 연결마다 새로운 TLS 핸드셰이크
        ↓
425 에러 해결! ✅
```

### 보안 수준

- ✅ 제어 채널: **TLS 암호화**
- ✅ 데이터 채널: **TLS 암호화** (PROT P)
- ✅ 사용자명/비밀번호: **안전하게 보호**
- ✅ 파일 전송: **안전하게 보호**

---

## 🐛 여전히 문제가 있다면

### 추가 디버그

SimpleFtpTlsUploadTest.java에 다음 추가:

```java
// main() 메서드 시작 부분에
System.setProperty("javax.net.debug", "ssl,handshake");
```

### FileZilla Server 로그 확인

**FileZilla Server 로그 위치:**
```
C:\ProgramData\FileZilla Server\Logs\
```

**로그에서 확인할 내용:**
- 연결 시도 기록
- TLS 핸드셰이크 성공/실패
- PROT 명령 수신 여부

---

## 📊 설정 체크리스트

### 클라이언트 (SimpleFtpTlsUploadTest.java)
- ✅ `setEndpointCheckingEnabled(false)` 추가됨
- ✅ `setUseEPSVwithIPv4(true)` 추가됨
- ✅ `setBufferSize(0)` 추가됨
- ✅ `execPROT("P")` 사용
- ✅ `enterLocalPassiveMode()` 수동형

### 설정 파일 (ftp-test.properties)
- ✅ `ftp.host=jiniebox.com`
- ✅ `ftp.port=21`
- ✅ `ftp.mode=PASSIVE`
- ✅ `ftp.prot=P`

### FileZilla Server (선택)
- ⚠️ Session resumption 체크 해제 (더 안정적)
- ✅ Explicit FTP over TLS
- ✅ TLS 1.2 이상

---

## 📞 문의

- **Email**: kiunsea@gmail.com
- **GitHub**: https://github.com/kiunsea/jangbogo/issues
- **Website**: https://www.omnibuscode.com

---

**Copyright © 2025 jiniebox.com**


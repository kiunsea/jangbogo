# FileZilla Server TLS Session Resumption 완전 해결 가이드

## ❌ 계속 발생하는 에러

```
425 Unable to build data connection: TLS session of data connection not resumed
```

이 에러는 **FileZilla Server가 반드시 설정 변경이 필요한 문제**입니다.

---

## 🎯 완전한 해결 방법 (서버 설정 필수!)

### ⭐ 방법 1: FileZilla Server 설정 변경 (필수!)

#### 1-1. FileZilla Server 관리 인터페이스 열기

```
FileZilla Server.exe 실행
또는 이미 실행 중이면 우하단 시스템 트레이 아이콘 클릭
```

#### 1-2. Settings 열기

```
메뉴: Edit → Settings (또는 설정)
```

#### 1-3. FTP over TLS 설정 변경

**왼쪽 메뉴에서 "FTP over TLS settings" 선택**

**다음 설정을 정확히 따라하세요:**

```
┌─────────────────────────────────────────────────┐
│ FTP over TLS settings                           │
├─────────────────────────────────────────────────┤
│                                                 │
│ Enable FTP over TLS support:                   │
│ ● Explicit (FTPES)                             │ ← 이것 선택
│                                                 │
│ Minimum TLS version: TLS 1.2                   │
│                                                 │
│ ⭐⭐⭐ 중요! ⭐⭐⭐                                │
│ Data connection TLS resumption:                │
│ ☐ Allow TLS session resumption                │ ← 체크 해제!
│                                                 │
│ 또는 영문 버전:                                 │
│ ☐ Reuse TLS session of control connection     │ ← 체크 해제!
│    for data connections                         │
│                                                 │
└─────────────────────────────────────────────────┘
```

**핵심 체크박스:**

🔍 **찾아야 할 체크박스 (버전마다 문구가 다를 수 있음):**
- ☐ "Allow TLS session resumption"
- ☐ "Reuse TLS session of control connection for data connections"
- ☐ "Enable session resumption on data connection"
- ☐ "Session resumption"

**→ 이 중 하나를 찾아서 체크 해제!**

📸 **스크린샷 예시:**
```
Settings → FTP over TLS settings

[ ] Allow TLS session resumption on data connection  ← 이거!
```

#### 1-4. 설정 저장 및 재시작 (필수!)

1. **Apply** 또는 **OK** 버튼 클릭
2. **FileZilla Server 완전 재시작** (매우 중요!)
   ```
   방법 1: Server → Quit → FileZilla Server 다시 시작
   
   방법 2: Windows 서비스 재시작
   - services.msc 실행
   - FileZilla Server 찾기
   - 우클릭 → 다시 시작
   ```

3. **설정 확인**
   - 재시작 후 Settings 다시 열어서 체크 해제 유지되는지 확인

---

## 💻 클라이언트 코드 수정 (이미 적용됨!)

서버 설정과 함께 작동하도록 클라이언트도 수정했습니다:

### 적용된 수정 사항

```java
// 1. SSL 프로토콜 사용 (TLS 대신)
ftpsClient = new FTPSClient("SSL", false);

// 2. 엔드포인트 체크 비활성화
ftpsClient.setEndpointCheckingEnabled(false);

// 3. EPSV 활성화
ftpsClient.setUseEPSVwithIPv4(true);

// 4. 버퍼 크기 0
ftpsClient.setBufferSize(0);

// 5. UTF-8 자동 감지 비활성화
ftpsClient.setAutodetectUTF8(false);

// 6. Keep-Alive 설정
ftpsClient.setControlKeepAliveTimeout(300);
```

---

## 🔍 FileZilla Server 설정 찾는 방법

### FileZilla Server 1.x (최신 버전)

**경로:**
```
Edit → Settings → FTP over TLS settings → Advanced
```

**설정 화면에서 찾기:**
```
Ctrl + F 검색: "resumption" 또는 "reuse"
```

### FileZilla Server 0.9.x (구버전)

**경로:**
```
Edit → Settings → SSL/TLS Settings
```

**설정 이름:**
```
☐ Allow explicit SSL/TLS
☐ Disallow plain unencrypted FTP
```

---

## 📋 완전한 해결 체크리스트

### 서버 측 (FileZilla Server)
- [ ] Settings → FTP over TLS settings 열기
- [ ] TLS session resumption 관련 체크박스 **해제**
- [ ] Apply/OK 클릭
- [ ] **FileZilla Server 재시작** (필수!)
- [ ] 설정 재확인

### 클라이언트 측 (SimpleFtpTlsUploadTest)
- [x] `new FTPSClient("SSL", false)` 사용
- [x] `setEndpointCheckingEnabled(false)`
- [x] `setUseEPSVwithIPv4(true)`
- [x] `setBufferSize(0)`
- [x] `setAutodetectUTF8(false)`
- [x] `setControlKeepAliveTimeout(300)`
- [x] Passive 모드 사용

### 테스트
- [ ] SimpleFtpTlsUploadTest main() 실행
- [ ] 에러 없이 파일 업로드 성공 확인

---

**Copyright © 2025 jiniebox.com**
**Contact**: kiunsea@gmail.com


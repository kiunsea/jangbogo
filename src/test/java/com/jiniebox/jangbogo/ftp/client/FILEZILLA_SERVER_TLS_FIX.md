# FileZilla Server TLS 세션 재개 에러 해결 가이드

## ❌ 에러 상황

- **서버**: FileZilla Server 1.11.1
- **에러**: `425 Unable to build data connection: TLS session of data connection not resumed`
- **원인**: FileZilla Server의 TLS 세션 재개 설정 문제

---

## 🔧 해결 방법 A: FileZilla Server 설정 변경 (권장)

### 1. FileZilla Server 관리 인터페이스 열기

1. **FileZilla Server** 실행
2. **Edit** → **Settings** 메뉴 선택

### 2. FTP over TLS 설정

**Settings 창에서:**

1. 왼쪽 메뉴에서 **"FTP over TLS settings"** 선택

2. 다음 옵션들을 확인/변경:

#### 옵션 1: TLS 세션 재개 비활성화 ⭐ (가장 확실)

```
☐ Allow session resumption on the data connection
```
- 이 체크박스를 **해제**하세요
- 또는 영문 버전에서: "Disallow TLS session resumption"을 **체크**

#### 옵션 2: 프로토콜 설정

```
○ Require explicit FTP over TLS
```
- Explicit FTP over TLS 선택 (포트 21)

#### 옵션 3: 최소 TLS 버전

```
Minimum TLS version: TLS 1.2
```

### 3. 설정 저장 및 재시작

1. **OK** 버튼 클릭
2. **FileZilla Server 재시작** (필수!)
   - Server → Quit
   - FileZilla Server 다시 시작

---

## 🔧 해결 방법 B: 클라이언트 코드 수정 (서버 변경 불가 시)

이미 적용된 설정들:

### 1. PROT C 사용 (데이터 채널 비암호화)

`ftp-test.properties` 파일 수정:

```properties
# P 대신 C 사용
ftp.prot=C
```

또는 SimpleFtpTlsUploadTest.java에서 PROT P를 강제로 C로 변경:

```java
// 5번 섹션 수정 (line ~188)
// try-catch 제거하고 직접 PROT C 사용
ftpsClient.execPBSZ(0);
ftpsClient.execPROT("C");  // P 대신 C 사용
System.out.println("  - PROT C 설정 (제어 채널만 암호화)");
```

### 2. 이미 적용된 설정 확인

SimpleFtpTlsUploadTest.java에 이미 다음이 적용됨:
- ✅ `setUseEPSVwithIPv4(true)`
- ✅ `setBufferSize(0)`
- ✅ PROT P 실패 시 자동 폴백

---

## 📝 추천 조합

### 조합 1: 서버 설정 변경 (최고 보안)

**FileZilla Server:**
```
☐ Allow session resumption on the data connection  (체크 해제)
```

**클라이언트 (ftp-test.properties):**
```properties
ftp.mode=PASSIVE
ftp.prot=P  # 데이터 채널도 암호화
```

### 조합 2: 클라이언트만 수정 (빠른 해결)

**클라이언트 (ftp-test.properties):**
```properties
ftp.mode=PASSIVE
ftp.prot=C  # 제어 채널만 암호화 ⭐
```

---

## 🧪 테스트 순서

### 1단계: 서버 설정 변경 시도
```
FileZilla Server Settings
→ FTP over TLS settings
→ "Allow session resumption" 체크 해제
→ OK → Server 재시작
```

### 2단계: SimpleFtpTlsUploadTest 재실행
```
IDE → main() 우클릭 → Run
```

### 3단계: 여전히 에러 발생 시
```properties
# ftp-test.properties
ftp.prot=C  # P 대신 C로 변경
```

---

## 🔍 FileZilla Server 1.11.1 특이사항

### 알려진 이슈

FileZilla Server 1.x 버전에서는 기본적으로 TLS 세션 재개가 활성화되어 있어 일부 클라이언트와 호환성 문제가 있습니다.

### 권장 설정 (FileZilla Server)

```
FTP over TLS settings:
  Protocol: Explicit FTP over TLS
  ☐ Allow session resumption on the data connection  ← 체크 해제!
  
  Minimum TLS version: TLS 1.2
  
  Certificate:
  - 유효한 인증서 사용 (자체 서명도 가능)
```

---

## 💻 클라이언트 코드 강제 수정

만약 서버 설정을 변경할 수 없다면, 다음 코드로 수정하세요:

### SimpleFtpTlsUploadTest.java 5번 섹션 수정

**현재 코드:**
```java
try {
    ftpsClient.execPROT("P");
    System.out.println("  - PROT P 실행 완료 (데이터 채널 암호화)");
} catch (Exception e) {
    System.out.println("  ⚠️  PROT P 실패, PROT C로 재시도");
    ftpsClient.execPROT("C");
    System.out.println("  - PROT C 실행 완료 (제어 채널만 암호화)");
}
```

**강제 PROT C로 변경:**
```java
// PROT C 직접 사용 (FileZilla Server 호환)
ftpsClient.execPROT("C");
System.out.println("  - PROT C 설정 (제어 채널만 암호화)");
System.out.println("  - FileZilla Server 호환 모드");
```

---

## 📊 보안 수준 비교

| 설정 | 제어 채널 | 데이터 채널 | FileZilla 호환 |
|------|----------|------------|---------------|
| PROT P | TLS 암호화 | TLS 암호화 | ⚠️ 설정 필요 |
| PROT C | TLS 암호화 | 평문 | ✅ 완벽 호환 |

**참고:** PROT C도 사용자명/비밀번호는 암호화되므로 기본 보안은 유지됩니다.

---

## 🎯 즉시 해결 방법

가장 빠른 방법은 **ftp-test.properties** 파일만 수정:

```properties
ftp.host=jiniebox.com
ftp.port=21
ftp.user=jiniebox
ftp.password=qhqh1923!
ftp.mode=PASSIVE
ftp.prot=C              ⭐ 이것만 P에서 C로 변경!
```

저장 후 main() 재실행!

---

## 📞 추가 정보

### FileZilla Server 공식 문서
- [FileZilla Server TLS Settings](https://wiki.filezilla-project.org/FTP_over_TLS)

### 문의
- **Email**: kiunsea@gmail.com
- **GitHub**: https://github.com/kiunsea/jangbogo/issues

---

**Copyright © 2025 jiniebox.com**


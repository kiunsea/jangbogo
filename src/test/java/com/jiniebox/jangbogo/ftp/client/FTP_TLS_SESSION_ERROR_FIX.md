# FTP TLS 세션 재개 에러 해결 가이드

## ❌ 에러 메시지

```
425 Unable to build data connection: TLS session of data connection not resumed.
```

---

## 🔍 원인

이 에러는 FTP over TLS에서 **데이터 연결**을 맺을 때 발생하는 문제입니다.

### 상세 설명

FTP over TLS는 두 가지 연결을 사용합니다:
1. **제어 연결 (Control Connection)**: 명령어 전송 (PORT 21)
2. **데이터 연결 (Data Connection)**: 실제 파일 전송 (동적 포트)

일부 FTP 서버는 데이터 연결 시 제어 연결의 **TLS 세션을 재개(resume)하려고 시도**하는데, 서버가 이를 지원하지 않거나 설정이 맞지 않으면 이 에러가 발생합니다.

---

## ✅ 해결 방법

SimpleFtpTlsUploadTest.java에 다음 설정들이 추가되었습니다:

### 1. EPSV 명령어 사용
```java
ftpsClient.setUseEPSVwithIPv4(true);
```
- EPSV (Extended Passive Mode) 사용
- IPv4 환경에서 데이터 연결 개선

### 2. 버퍼 크기 조정
```java
ftpsClient.setBufferSize(0);
```
- TLS 세션 재개 문제 완화

### 3. PROT C 폴백
```java
try {
    ftpsClient.execPROT("P");  // 데이터 채널 암호화
} catch (Exception e) {
    ftpsClient.execPROT("C");  // 제어 채널만 암호화
}
```

---

## 📝 적용된 수정 사항

### SimpleFtpTlsUploadTest.java

```java
// FTPS 클라이언트 생성 시
ftpsClient = new FTPSClient("TLS", false);

// ✅ 추가된 설정
ftpsClient.setUseEPSVwithIPv4(true);    // EPSV 사용
ftpsClient.setBufferSize(0);             // 버퍼 크기 설정

// 연결 후
ftpsClient.execPBSZ(0);

// ✅ PROT P 시도, 실패 시 PROT C로 폴백
try {
    ftpsClient.execPROT("P");  // 모든 채널 암호화
} catch (Exception e) {
    ftpsClient.execPROT("C");  // 제어 채널만 암호화
}
```

---

## 🔧 프로퍼티 파일 옵션

`src/test/resources/ftp-test.properties`에 다음 옵션이 추가되었습니다:

```properties
# TLS 보호 수준
# P - 제어/데이터 채널 모두 암호화 (권장)
# C - 제어 채널만 암호화 (TLS 세션 재개 문제 해결용)
ftp.prot=P
```

**에러 발생 시** `ftp.prot=C`로 변경하세요.

---

## 🎯 다른 해결 방법들

### 방법 1: PROT C 사용 (권장)
```properties
ftp.prot=C
```
- 제어 채널만 TLS로 암호화
- 데이터 채널은 암호화하지 않음
- ⚠️ 보안이 약간 낮아지지만 호환성 최고

### 방법 2: Explicit TLS 확인
```java
FTPSClient ftpsClient = new FTPSClient("TLS", false);  // false = Explicit TLS
```
- Implicit TLS (포트 990)가 아닌 Explicit TLS (포트 21) 사용 확인

### 방법 3: 서버 TLS 설정 변경
서버 관리자라면 다음을 확인:
- TLS 세션 재개(Session Resumption) 활성화
- TLS 버전 (TLS 1.2 이상 권장)

---

## 🧪 테스트 실행

### 1. 수정된 코드로 재실행

IDE에서:
```
SimpleFtpTlsUploadTest.java → main() 우클릭 → Run
```

### 2. 예상 출력

```
🔧 FTPS 클라이언트 생성 중...
  - EPSV with IPv4 활성화 (데이터 연결 개선)
  - 버퍼 크기 설정 (TLS 세션 재개 문제 해결)
  ⚠️  자체 서명 인증서 허용 (테스트 전용)
  ✅ 클라이언트 생성 완료

🔐 TLS 보안 채널 설정 중...
  - PBSZ 0 실행 완료
  - PROT P 실행 완료 (데이터 채널 암호화)
  ✅ TLS 보안 채널 설정 완료
```

또는 PROT P 실패 시:

```
🔐 TLS 보안 채널 설정 중...
  - PBSZ 0 실행 완료
  ⚠️  PROT P 실패, PROT C로 재시도 (데이터 채널 비암호화)
  - PROT C 실행 완료 (제어 채널만 암호화)
  ✅ TLS 보안 채널 설정 완료
```

---

## 📊 보안 수준 비교

| 설정 | 제어 채널 | 데이터 채널 | 보안 수준 | 호환성 |
|------|----------|------------|---------|--------|
| PROT P | 암호화 ✅ | 암호화 ✅ | 높음 ⭐⭐⭐ | 보통 |
| PROT C | 암호화 ✅ | 평문 ❌ | 중간 ⭐⭐ | 높음 ✅ |

**권장:**
- 프로덕션: PROT P (최대 보안)
- 테스트/문제 해결: PROT C (최대 호환성)

---

## 💡 추가 해결책

### 만약 여전히 에러가 발생한다면

1. **프로퍼티 파일 수정**
```properties
ftp.prot=C
```

2. **또는 코드에서 직접 PROT C 사용**
SimpleFtpTlsUploadTest.java의 5번 섹션 수정:
```java
// PROT P 대신 PROT C 직접 사용
ftpsClient.execPROT("C");
```

3. **Active 모드 시도**
```properties
ftp.mode=ACTIVE
```

4. **포트 확인**
```properties
# Explicit TLS
ftp.port=21

# Implicit TLS 시도
# ftp.port=990
```

---

## 📞 추가 지원

문제가 계속되면:
- **Email**: kiunsea@gmail.com
- **GitHub Issues**: https://github.com/kiunsea/jangbogo/issues

로그 파일 첨부:
```
콘솔 출력 전체 복사
또는 스크린샷
```

---

**Copyright © 2025 jiniebox.com**


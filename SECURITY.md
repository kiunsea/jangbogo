# Security Policy

Jangbogo 프로젝트의 보안 정책 및 취약점 보고 절차입니다.

---

## 지원 버전

현재 보안 업데이트를 받는 버전:

| 버전 | 지원 여부 |
| ---- | --------- |
| 0.5.x | ✅ 지원 |
| < 0.5.0 | ❌ 지원 종료 |

---

## 보안 취약점 보고

보안 취약점을 발견하셨다면 **공개 이슈로 제출하지 마시고** 다음 방법으로 비공개 보고해주세요.

### 보고 방법

**GitHub Security Advisory** (권장): 
https://github.com/jiniebox/jangbogo/security/advisories/new

또는

**GitHub Issues** (민감하지 않은 경우):
https://github.com/jiniebox/jangbogo/issues

### 보고 시 포함할 정보

1. **취약점 유형**
   - SQL Injection, XSS, CSRF, 인증 우회 등

2. **영향 범위**
   - 어떤 기능에 영향을 미치는지
   - 공격 시나리오

3. **재현 방법**
   - 단계별 재현 절차
   - PoC (Proof of Concept) 코드

4. **영향도 평가**
   - 높음 / 중간 / 낮음
   - CVSS 점수 (가능한 경우)

5. **제안 해결 방법** (선택사항)
   - 수정 제안이 있다면

### 응답 시간

- **초기 응답**: 48시간 이내
- **진행 상황 업데이트**: 주 1회
- **패치 릴리스**: 심각도에 따라 1주일~1개월

---

## 보안 모범 사례

Jangbogo를 안전하게 사용하기 위한 권장 사항:

### 1. 관리자 계정 보안

❌ **하지 말아야 할 것:**
```yaml
# application.yml에 평문 저장
admin:
  id: admin
  pass: 1234
```

✅ **권장 방법:**
```bash
# 환경 변수 사용
set ADMIN_ID=strong_admin_id
set ADMIN_PASS=VeryStr0ng!P@ssw0rd#2025
```

또는:
```properties
# config/admin.properties (Git 제외)
admin.id=strong_admin_id
admin.pass=VeryStr0ng!P@ssw0rd#2025
```

### 2. 네트워크 보안

✅ **기본 설정 (안전)**
```yaml
server:
  address: 127.0.0.1  # localhost만 허용
  port: 8282
```

❌ **외부 노출 금지**
```yaml
# 이렇게 설정하지 마세요!
server:
  address: 0.0.0.0  # 모든 네트워크 인터페이스
```

### 3. 데이터베이스 보안

- ✅ `db/jangbogo-dev.db` 파일 권한 제한
- ✅ 정기적인 백업
- ✅ 민감한 데이터 암호화 (쇼핑몰 계정)

### 4. 쇼핑몰 계정 보안

- ✅ 계정 정보는 AES 암호화되어 저장
- ✅ 암호화 키는 시스템별로 자동 생성
- ✅ `.gitignore`에 `config/mall_account.yml` 포함

### 5. 로그 파일 보안

- ✅ 로그에 비밀번호 기록 안 함
- ✅ 민감 정보 마스킹 처리
- ⚠️ 로그 파일 공유 시 민감 정보 확인

---

## 알려진 보안 고려사항

### 1. Selenium WebDriver

**이슈**: Selenium이 실제 브라우저를 제어하여 쇼핑몰에 로그인

**완화 방법**:
- WebDriver는 로컬에서만 실행
- 세션 정보는 임시 프로파일에만 저장
- 브라우저 종료 시 세션 삭제

### 2. 로컬 데이터 저장

**이슈**: 모든 데이터가 로컬 파일로 저장

**완화 방법**:
- SQLite 데이터베이스 암호화 권장
- 중요 데이터는 백업 및 접근 권한 관리
- Windows 파일 시스템 암호화 (BitLocker) 권장

### 3. localhost 바인딩

**이슈**: 현재 버전은 localhost(127.0.0.1)만 접근 가능

**장점**: 외부 공격 불가능  
**단점**: 원격 접속 불가능

원격 접속이 필요한 경우:
- VPN 사용 권장
- 또는 리버스 프록시 (nginx, Apache) + HTTPS + 인증

---

## 보안 업데이트 내역

### v0.5.0 (2025-11-04)

- ✅ 관리자 계정 환경 변수 지원
- ✅ 세션 타임아웃 설정 (30분)
- ✅ localhost 전용 바인딩
- ✅ 쇼핑몰 계정 AES 암호화
- ✅ AuthInterceptor 전역 세션 검사

---

## 보안 체크리스트

배포 전 확인 사항:

- [ ] 기본 관리자 계정 변경
- [ ] `config/admin.properties`를 `.gitignore`에 추가
- [ ] `config/mall_account.yml`을 `.gitignore`에 추가
- [ ] `db/*.db` 파일을 `.gitignore`에 추가
- [ ] 로그에 비밀번호 기록 안 되는지 확인
- [ ] HTTPS 사용 (외부 노출 시)
- [ ] 정기적인 백업 설정
- [ ] 의존성 취약점 검사 (Dependabot 등)

---

## 의존성 보안

### 자동 보안 스캔

GitHub Dependabot을 활성화하여 의존성 취약점을 자동으로 검사하세요.

### 수동 검사

```bash
# Gradle 의존성 취약점 검사
.\gradlew dependencyCheckAnalyze
```

### 주요 의존성

- Spring Boot 3.5.6
- Selenium 4.x
- SQLite JDBC 3.45.3.0
- Jackson 2.17.x
- Bootstrap 5

---

## 책임 있는 공개 (Responsible Disclosure)

보안 취약점은 다음 절차로 공개됩니다:

1. **비공개 보고**: 취약점 발견 시 비공개 보고
2. **확인 및 수정**: 개발팀이 확인 후 패치 작성
3. **패치 릴리스**: 보안 패치 릴리스
4. **공개**: 패치 릴리스 후 90일 이내 취약점 공개
5. **크레딧**: 보고자에게 크레딧 제공

---

## 보안 관련 리소스

- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [CWE - Common Weakness Enumeration](https://cwe.mitre.org/)
- [Spring Security Guide](https://spring.io/guides/topicals/spring-security-architecture)

---

## 라이선스

이 보안 정책은 프로젝트의 AGPL-3.0-or-later 라이선스에 따릅니다.

---

**마지막 업데이트**: 2025-11-04  
**버전**: 0.5.0


# 개발자 문서

Jangbogo 개발자 및 유지보수 담당자를 위한 기술 문서입니다.

---

## 📚 문서 목록

### 1. [배포 구현 요약](DISTRIBUTION_IMPLEMENTATION_SUMMARY.md)
**대상:** 아키텍트, 시니어 개발자

**내용:**
- Custom JRE 번들링 구현 방식
- jlink + ZIP 배포 전략 선택 이유
- 기술적 결정 사항 및 배경
- 구현 타임라인

**주요 주제:**
- JRE 번들링 vs JRE 별도 설치
- jlink를 통한 경량 JRE 생성
- Gradle 빌드 자동화

---

### 2. [DAO 통합 가이드](DAO_INTEGRATION_GUIDE.md)
**대상:** 백엔드 개발자

**내용:**
- `jbg_mall` 테이블 통합
- DAO 클래스 구조 및 설계
- 쇼핑몰 계정 정보 암호화 처리
- SQL 쿼리 예제

**핵심 클래스:**
- `JbgMallDataAccessObject`
- `JbgOrderDataAccessObject`
- `JbgItemDataAccessObject`

---

### 3. [설정 가이드](JBG_CONFIG_GUIDE.md)
**대상:** 시스템 개발자, DevOps

**내용:**
- `jbg_config.yml` 구조 및 스키마
- `JangbogoConfig` 클래스 사용법
- 설정 우선순위 (환경변수 > 파일 > 기본값)
- 환경별 설정 (local, prod)

**설정 파일:**
- `config/jbg_config.yml`
- `config/mall_account.yml`
- `config/admin.properties`

---

### 4. [로그인 시스템 가이드](LOGIN_GUIDE.md)
**대상:** 프론트엔드 개발자, 백엔드 개발자

**내용:**
- 로그인 화면 구현 (Thymeleaf)
- 세션 기반 인증 구조
- API 엔드포인트 명세
- Admin 계정 관리 방법

**핵심 컴포넌트:**
- `AdminController`
- `AuthInterceptor`
- `UserSession`

---

### 5. [세션 개선 가이드](SESSION_IMPROVEMENT_GUIDE.md)
**대상:** 백엔드 개발자

**내용:**
- `AuthInterceptor` 구현 상세
- `SessionConstants` 중앙 관리 패턴
- 전역 세션 검사 로직
- 코드 개선 사항 및 리팩토링

**개선 내역:**
- 세션 타임아웃 처리
- 로그인 체크 자동화
- 세션 상수 통일

---

### 6. [개발 로그 (DEVLOG)](DEVLOG.md)
**대상:** 모든 개발자

**내용:**
- 버전별 개발 작업 상세 내역
- 구현 배경, 변경 파일, 영향 범위
- 테스트 권장 사항

---

### 7. [Eclipse IDE 설정 가이드](ECLIPSE_SETUP_GUIDE.md)
**대상:** 모든 개발자 (Eclipse 사용자)

**내용:**
- Eclipse에서 Gradle 프로젝트 import 방법
- Buildship 플러그인 설치
- JUnit 테스트 실행 방법
- Spring Boot 애플리케이션 실행 및 디버깅
- 문제 해결 가이드

**주요 기능:**
- 프로젝트 import 및 설정
- 테스트 실행 (JUnit 5)
- 애플리케이션 실행 모드 설정
- 디버깅 방법

---

## 🎯 학습 경로

### 초급 개발자 (새로 합류)

**0주차: 개발 환경 설정** (Eclipse 사용자)
1. [Eclipse IDE 설정 가이드](ECLIPSE_SETUP_GUIDE.md) (30분)

**1주차: 프로젝트 이해**
1. [설정 가이드](JBG_CONFIG_GUIDE.md) (1시간)
2. [로그인 시스템 가이드](LOGIN_GUIDE.md) (2시간)
3. [DAO 통합 가이드](DAO_INTEGRATION_GUIDE.md) (2시간)

**2주차: 심화 학습**
4. [세션 개선 가이드](SESSION_IMPROVEMENT_GUIDE.md) (1시간)
5. [배포 구현 요약](DISTRIBUTION_IMPLEMENTATION_SUMMARY.md) (1시간)

---

### 중급 개발자 (기능 추가)

**신규 쇼핑몰 추가**
1. [DAO 통합 가이드](DAO_INTEGRATION_GUIDE.md) - 데이터 모델 이해
2. 소스 코드: `src/main/java/com/jiniebox/jangbogo/svc/mall/`
3. 테스트 및 배포

**설정 변경**
1. [설정 가이드](JBG_CONFIG_GUIDE.md) - 설정 구조 이해
2. `config/` 폴더 수정
3. 테스트

---

### 시니어 개발자 (아키텍처 개선)

**배포 프로세스 개선**
1. [배포 구현 요약](DISTRIBUTION_IMPLEMENTATION_SUMMARY.md) - 현재 구조 이해
2. `build.gradle` 분석
3. 개선안 제시

**보안 강화**
1. [로그인 시스템 가이드](LOGIN_GUIDE.md)
2. [DAO 통합 가이드](DAO_INTEGRATION_GUIDE.md) - 암호화 부분
3. 보안 취약점 분석

---

## 📋 문서별 우선순위

| 우선순위 | 문서 | 필수 여부 | 소요 시간 |
|---------|------|-----------|-----------|
| ⭐⭐⭐ | Eclipse IDE 설정 가이드 | 필수 (Eclipse 사용자) | 30분 |
| ⭐⭐⭐ | 설정 가이드 | 필수 | 1시간 |
| ⭐⭐⭐ | 로그인 시스템 가이드 | 필수 | 2시간 |
| ⭐⭐⭐ | DAO 통합 가이드 | 필수 | 2시간 |
| ⭐⭐ | 세션 개선 가이드 | 권장 | 1시간 |
| ⭐ | 배포 구현 요약 | 선택 | 1시간 |

---

## 🔍 주제별 문서 찾기

### 데이터베이스 관련
→ [DAO 통합 가이드](DAO_INTEGRATION_GUIDE.md)

### 인증/세션 관련
→ [로그인 시스템 가이드](LOGIN_GUIDE.md)  
→ [세션 개선 가이드](SESSION_IMPROVEMENT_GUIDE.md)

### 설정 관련
→ [설정 가이드](JBG_CONFIG_GUIDE.md)

### 배포 관련
→ [배포 구현 요약](DISTRIBUTION_IMPLEMENTATION_SUMMARY.md)  
→ [빌드 가이드](../user/BUILD_GUIDE.md)

### 개발 환경 설정
→ [Eclipse IDE 설정 가이드](ECLIPSE_SETUP_GUIDE.md)

---

## 🔗 관련 문서

- [전체 문서 인덱스](../README.md)
- [사용자 문서](../user/)

---

## 💡 기여 가이드

코드 기여 시 다음 문서를 참고하세요:

1. **새 기능 추가**
   - 관련 DAO 가이드 확인
   - 설정 가이드에 따라 설정 추가
   - 테스트 작성

2. **버그 수정**
   - 로그 파일 분석
   - 관련 문서 확인
   - 수정 및 테스트

3. **문서 작성**
   - 기존 문서 구조 참고
   - 코드 예제 포함
   - 링크 정확성 확인

---

**버전**: 0.6.1
**최종 업데이트**: 2026-02-15


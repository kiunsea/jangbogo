# Jangbogo 프로젝트 문서

Jangbogo v1.0.0 프로젝트의 모든 문서가 이 폴더에 정리되어 있습니다.

## 📚 사용자 문서

사용자 및 배포 담당자를 위한 문서:

### 1. [빌드 가이드](BUILD_GUIDE.md)
배포 패키지 빌드 방법
- Custom JRE 생성 (jlink)
- packageDist 태스크
- 빌드 옵션 커스터마이징
- 트러블슈팅

### 2. [배포 가이드](DEPLOYMENT_GUIDE.md)
설치 및 배포 방법
- ZIP 파일 설치
- Jangbogo.bat 실행
- Windows 서비스 등록
- 관리자 계정 설정
- 문제 해결

### 3. [사용자 매뉴얼](USER_GUIDE.md)
기능 사용법
- 서비스 접속 및 로그인
- 쇼핑몰 계정 연결
- 구매내역 수집 (수동/자동)
- 구매정보 저장
- FAQ

---

## 🔧 개발자 문서

개발자 및 유지보수 담당자를 위한 문서:

### 1. [배포 구현 요약](DISTRIBUTION_IMPLEMENTATION_SUMMARY.md)
Custom JRE 번들링 구현 내역
- 구현 방식 및 선택 이유
- jlink + ZIP 배포 전략
- 기술적 결정 사항
- 구현 타임라인

### 2. [DAO 통합 가이드](DAO_INTEGRATION_GUIDE.md)
데이터베이스 접근 계층
- jbg_mall 테이블 통합
- DAO 클래스 구조
- 암호화 처리
- 쿼리 예제

### 3. [설정 가이드](JBG_CONFIG_GUIDE.md)
설정 파일 관리
- jbg_config.yml 구조
- JangbogoConfig 클래스
- 설정 우선순위
- 환경별 설정

### 4. [로그인 시스템 가이드](LOGIN_GUIDE.md)
인증 및 세션 관리
- 로그인 화면 구현
- 세션 기반 인증
- API 엔드포인트
- Admin 계정 관리

### 5. [세션 개선 가이드](SESSION_IMPROVEMENT_GUIDE.md)
세션 처리 개선 내역
- AuthInterceptor 구현
- SessionConstants 중앙 관리
- 전역 세션 검사
- 코드 개선 사항

### 6. [Spring Boot 참고](HELP.md)
Spring Boot 기본 참고 문서
- Gradle 빌드
- Spring Web
- Thymeleaf
- 추가 리소스

---

## 📁 문서 구조

```
doc/
├─ README.md                              [이 파일] 문서 인덱스
│
├─ 사용자 문서/
│  ├─ BUILD_GUIDE.md                      빌드 가이드
│  ├─ DEPLOYMENT_GUIDE.md                 배포 가이드
│  └─ USER_GUIDE.md                       사용자 매뉴얼
│
└─ 개발자 문서/
   ├─ DISTRIBUTION_IMPLEMENTATION_SUMMARY.md  배포 구현 요약
   ├─ DAO_INTEGRATION_GUIDE.md            DAO 가이드
   ├─ JBG_CONFIG_GUIDE.md                 설정 가이드
   ├─ LOGIN_GUIDE.md                      로그인 가이드
   ├─ SESSION_IMPROVEMENT_GUIDE.md        세션 가이드
   └─ HELP.md                             Spring Boot 참고
```

---

## 🚀 빠른 시작

### 사용자

1. [배포 가이드](DEPLOYMENT_GUIDE.md) 읽기
2. ZIP 파일 압축 해제
3. [사용자 매뉴얼](USER_GUIDE.md)에 따라 사용

### 개발자

1. [빌드 가이드](BUILD_GUIDE.md) 읽기
2. 개발 환경 설정
3. 코드 수정 및 빌드

---

## 📋 버전 정보

- **프로젝트**: Jangbogo (장보고)
- **버전**: 0.5.0
- **라이선스**: AGPL-3.0-or-later
- **최종 업데이트**: 2025-11-04

---

## 📮 문의 및 기여

- GitHub Issues를 통해 버그 리포트 및 기능 제안
- Pull Request 환영
- 라이선스 및 법적 문의

---

**Copyright © 2025 jiniebox**


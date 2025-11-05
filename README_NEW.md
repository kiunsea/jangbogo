# JANGBOGO (장보고) 🧾🛍️
**온라인 쇼핑몰 구매내역 자동 수집·관리 프로그램**

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3-blue.svg)](https://www.gnu.org/licenses/agpl-3.0)
![Java](https://img.shields.io/badge/backend-Spring%20Boot%20(Java%2021)-orange)
![UI](https://img.shields.io/badge/client-Bootstrap%205-7952B3)
![Platform](https://img.shields.io/badge/platform-Windows-blue)
![Status](https://img.shields.io/badge/status-v0.5.0-orange)

**JANGBOGO**는 여러 온라인 쇼핑몰의 **구매내역을 자동으로 수집**하여 **로컬 DB 및 파일(JSON/YAML/CSV)**로 저장하는 프로그램입니다.  
웹 기반 관리자 화면에서 쇼핑몰 계정 연결, 자동 수집 스케줄 설정, 파일 내보내기 등을 간편하게 관리할 수 있습니다.

## 📚 문서

### 사용자 문서
- **[배포 가이드](doc/DEPLOYMENT_GUIDE.md)** - 빌드, 설치, 실행, Windows 서비스 등록
- **[사용자 매뉴얼](doc/USER_GUIDE.md)** - 쇼핑몰 연결, 구매내역 수집, 파일 내보내기
- **[빌드 가이드](doc/BUILD_GUIDE.md)** - 배포 패키지 빌드 방법

### 개발자 문서
- **[배포 구현 요약](doc/DISTRIBUTION_IMPLEMENTATION_SUMMARY.md)** - Custom JRE 번들링 구현 내역
- **[DAO 통합 가이드](doc/DAO_INTEGRATION_GUIDE.md)** - 데이터베이스 접근 계층
- **[설정 가이드](doc/JBG_CONFIG_GUIDE.md)** - 설정 파일 관리
- **[로그인 시스템](doc/LOGIN_GUIDE.md)** - 인증 및 세션 관리
- **[세션 개선](doc/SESSION_IMPROVEMENT_GUIDE.md)** - 세션 처리 개선 내역

---

## ✨ 핵심 기능

- **다중 쇼핑몰 지원**: SSG, 오아시스, 하나로마트 등
- **자동 수집**: 설정한 주기마다 자동으로 구매내역 수집
- **다양한 저장 형식**: JSON, CSV, Excel로 내보내기
- **로컬 저장**: 모든 데이터는 사용자 PC에 안전하게 보관
- **웹 기반 UI**: Bootstrap 5 기반 브라우저 관리 화면
- **Windows 서비스**: 시스템 시작 시 자동 실행 가능

> **중요**: 각 쇼핑몰 **이용약관/robots.txt/개인정보** 관련 규정을 준수하세요. 공식 API가 있을 경우 API 사용을 우선 검토하십시오.

---

## 🧱 아키텍처 개요

- **Server (Spring Boot)**  
  - REST API (`/api/**`) 제공: 설정 조회/저장, 수집 시작/중지, 상태 조회 등  
  - 수집 오케스트레이션: Selenium 드라이버 관리, 어댑터 실행, 저장소 I/O
- **Client (Bootstrap 5 최소 UI)**  
  - `/admin` 경로에 단일 페이지 렌더링  
  - Fetch API로 서버의 `/api/**` 엔드포인트 호출  
  - 설정/상태 관리 중심의 UI

---

## 📁 프로젝트 구조

```
src/main/java/com/jiniebox/jangbogo/
├─ ctrl/       # Controller - REST API 엔드포인트
├─ dao/        # Data Access Object - 데이터베이스 접근
├─ dto/        # Data Transfer Object - 데이터 전송 객체
├─ svc/        # Service - 비즈니스 로직 및 쇼핑몰별 구현
│  └─ mall/    # 쇼핑몰별 크롤링 구현체
├─ sys/        # System - 인증, 세션, 시스템 설정
└─ util/       # Utility - 유틸리티 클래스
```

> 자세한 프로젝트 구조는 [개발자 문서](doc/README.md)를 참조하세요.

---

## 🚀 빠른 시작

### 1) 요구사항
- **JDK 21+** (개발/빌드 시)
- **Edge 또는 Chrome** (Selenium 크롤링용)

### 2) 개발 환경 실행
```bash
./gradlew clean bootRun
```

브라우저에서: <http://localhost:8282>

### 3) 배포 패키지 빌드

Java 설치 없이 실행 가능한 배포 패키지 빌드:

```bash
./gradlew clean bootJar createJre packageDist
```

빌드 결과물: `build/distributions/Jangbogo-distribution.zip`

**ZIP 파일 내용:**
- `Jangbogo.bat` - 실행 스크립트
- `jangbogo-0.5.0.jar` - 애플리케이션
- `jre/` - Java 21 런타임 번들 (JRE 설치 불필요)
- `service/` - Windows 서비스 설정 파일
- `사용설명서.txt` - 설치 및 설정 가이드
- `사용자_매뉴얼.txt` - 기능 사용 가이드

**자세한 내용**: [배포 가이드](doc/DEPLOYMENT_GUIDE.md), [사용자 매뉴얼](doc/USER_GUIDE.md)

---

## ⚙️ 설정 및 보안

### 관리자 계정 설정

다음 3가지 방법으로 관리자 계정을 안전하게 설정할 수 있습니다:

1. **환경 변수** (권장) - `ADMIN_ID`, `ADMIN_PASS`
2. **외부 설정 파일** - `config/admin.properties`
3. **프로필별 설정** - `application-{profile}.yml`

자세한 설정 방법은 [배포 가이드](doc/DEPLOYMENT_GUIDE.md) 및 [설정 가이드](doc/JBG_CONFIG_GUIDE.md)를 참조하세요.

### 보안 권장사항

- ✅ 기본 비밀번호 즉시 변경
- ✅ 환경 변수 또는 외부 파일 사용
- ✅ 강력한 비밀번호 사용 (최소 12자)
- ✅ localhost(127.0.0.1) 전용 바인딩
- ✅ 쇼핑몰 계정 정보 AES 암호화
- ⚠️ Git에 민감정보 커밋 금지

자세한 내용은 [SECURITY.md](SECURITY.md)를 참조하세요.

---

## 🧾 라이선스

- 본 프로젝트는 **AGPL-3.0-or-later** 입니다.  
- 네트워크를 통한 상호작용이 이루어지는 개작본 제공 시 **변경한 소스코드 제공 의무**가 발생합니다.  
- 배포 시 `LICENSE`와 `NOTICE`를 포함하세요.

**주요 의존성 (3rd-party)**:
- Spring Boot (Apache-2.0)
- Selenium (Apache-2.0)
- Bootstrap 5 (MIT)
- SQLite JDBC (Apache-2.0)
- Jackson (Apache-2.0)

---

## 🤝 기여 (Contributing)

기여를 환영합니다! 다음 문서를 참조하세요:

- **[CONTRIBUTING.md](CONTRIBUTING.md)** - 기여 가이드
- **[CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)** - 행동 강령

이슈/PR 제출 시:
- Conventional Commits 규칙 사용 (`feat:`, `fix:`, `docs:` 등)
- 테스트 및 문서 업데이트 포함
- 코드 스타일 가이드 준수

---

## 📮 문의

- **GitHub Issues**: https://github.com/kiunsea/jangbogo/issues
- **Email**: kiunsea@gmail.com
- **Website**: https://jiniebox.com
- **Security**: [SECURITY.md](SECURITY.md) 참조
- **License**: [LICENSE](LICENSE) 참조

---

**Copyright © 2025 [jiniebox.com](https://jiniebox.com)**


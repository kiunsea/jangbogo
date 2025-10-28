# JANGBOGO (장보고) 🧾🛍️
**온라인/오프라인 쇼핑몰 구매내역 수집·관리 – Spring Boot 서버 + 설정용 최소 UI(Bootstrap 5)**

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3-blue.svg)](https://www.gnu.org/licenses/agpl-3.0)
![Java](https://img.shields.io/badge/backend-Spring%20Boot%20(Java)-orange)
![UI](https://img.shields.io/badge/client-Bootstrap%205-7952B3)
![Platform](https://img.shields.io/badge/platform-Server%20(Java)-blue)
![Status](https://img.shields.io/badge/status-Alpha-lightgrey)

**JANGBOGO**는 여러 온라인 쇼핑몰의 **구매내역을 수집**하여 **로컬 파일(NDJSON/JSON)** 에 저장하는 프로젝트입니다.  
본 저장소의 **UI는 “설정 관리 전용”**으로, 수집 파이프라인 실행/스케줄·사이트별 활성화·크롤러 옵션 등을 간단한 Bootstrap 5 페이지에서 제어합니다.  
(즉, **거래 내역을 보여주는 대시보드가 아닌** _관리자 설정 화면_ 입니다.)

---

## ✨ 핵심 기능

- **설정(UI)**
  - 쇼핑몰 어댑터 **활성/비활성** 전환
  - **헤드리스/지연/타임아웃/프로필 경로** 등 크롤러 옵션 관리
  - **저장 경로/형식(NDJSON/JSON)** 관리
  - **스케줄(CRON/간격)** 설정 및 **즉시 실행/중지**
  - **구성 내보내기/가져오기**(JSON)
- **수집(서버)**
  - Spring Boot REST API로 수집 잡 트리거/상태 조회
  - Selenium 기반 페이지 탐색/파싱/저장
  - 실행 로그/스크린샷(옵션)/스냅샷(옵션) 기록
- **보안/안정성**
  - Admin 인증(간단한 Basic/Sesssion 중 택1)로 설정 화면 보호
  - 비밀정보는 Windows **자격 증명 관리자/DPAPI** 등 외부 안전 저장소 활용 권장
  - 명시적 대기/재시도/랜덤 지연으로 안정성 강화

> **중요**: 각 쇼핑몰 **이용약관/robots.txt/개인정보** 관련 규정을 준수하세요. 공식 API가 있을 경우 API 사용을 우선 검토하십시오.

---

## 🧱 아키텍처 개요

- **Server (Spring Boot)**  
  - REST API (`/api/**`) 제공: 설정 조회/저장, 수집 시작/중지, 상태 조회 등  
  - 수집 오케스트레이션: Selenium 드라이버 관리, 어댑터 실행, 저장소 I/O
- **Client (Bootstrap 5 최소 UI)**  
  - `/admin` 경로에 단일 페이지(정적 파일) 렌더링  
  - Fetch API로 서버의 `/api/**` 엔드포인트 호출  
  - _대시보드는 없고, 설정/상태만_ 노출

---

## 📁 프로젝트 구조

### 소스 패키지 구조

```
JANGBOGO/
├─ src/main/java/com/jiniebox/jangbogo/
│  ├─ ctrl/                              # Controller - 클라이언트 요청 처리
│  │  └─ AdminController.java           # 관리자 API 엔드포인트
│  │
│  ├─ dao/                               # Data Access Object - 데이터베이스 연결 및 처리
│  │  ├─ CommonDataAccessObject.java    # 공통 DAO 기반 클래스
│  │  ├─ JbgMallDataAccessObject.java   # 쇼핑몰 정보 DAO
│  │  └─ JbgOrderDataAccessObject.java  # 주문 정보 DAO
│  │
│  ├─ dev/                               # Development - 개발 테스트용
│  │  ├─ DevTestController.java         # 개발 테스트 엔드포인트
│  │  ├─ JangbogoConfigExample.java     # 설정 사용 예제
│  │  └─ MallAccountYmlExample.java     # 계정 관리 예제
│  │
│  ├─ dto/                               # Data Transfer Object - 데이터 전송 객체
│  │  ├─ JangbogoConfig.java            # 장보고 설정 정보
│  │  ├─ MallAccount.java               # 쇼핑몰 계정 정보
│  │  └─ MallAccountYml.java            # 쇼핑몰 계정 YAML 구조
│  │
│  ├─ svc/                               # Service - 쇼핑몰 접속 및 요청 처리
│  │  ├─ JangBoGoManager.java           # 장보고 메인 서비스
│  │  ├─ MallOrderUpdater.java          # 주문 내역 수집
│  │  ├─ MallOrderUpdaterRunner.java    # 수집 실행기
│  │  ├─ MallAccountYmlService.java     # 계정 관리 서비스
│  │  ├─ ifc/                            # Interface - 서비스 인터페이스
│  │  │  ├─ MallSession.java
│  │  │  ├─ PurchasedCollector.java
│  │  │  └─ ReceiptCollector.java
│  │  ├─ mall/                           # 쇼핑몰별 구현체
│  │  │  ├─ Coupang.java
│  │  │  ├─ Emart.java
│  │  │  ├─ Hanaro.java
│  │  │  ├─ Oasis.java
│  │  │  └─ Ssg.java
│  │  └─ util/                           # 서비스 유틸리티
│  │     └─ WebDriverManager.java       # Selenium WebDriver 관리
│  │
│  ├─ sys/                               # System - 시스템 설정 및 인증
│  │  ├─ AuthInterceptor.java           # 인증 인터셉터
│  │  ├─ SessionConstants.java          # 세션 상수 관리
│  │  ├─ WebMvcConfig.java              # Spring MVC 설정
│  │  ├─ UserSession.java               # 사용자 세션 정보
│  │  └─ EnvSYS.java                    # 시스템 환경 상수
│  │
│  ├─ util/                              # Utility - 유틸리티 클래스
│  │  ├─ ExceptionUtil.java             # 예외 처리 유틸
│  │  ├─ JinieboxUtil.java              # 범용 유틸리티
│  │  ├─ JSONUtil.java                  # JSON 처리 유틸
│  │  └─ NumberUtil.java                # 숫자 처리 유틸
│  │
│  └─ JangbogoApplication.java          # Spring Boot 메인 클래스
│
├─ src/main/resources/
│  ├─ templates/                         # Thymeleaf 템플릿
│  │  ├─ index.html                      # 메인 페이지
│  │  ├─ signin.html                     # 로그인 페이지
│  │  ├─ layout.html                     # 레이아웃 베이스
│  │  └─ fragments/                      # 공통 프래그먼트
│  │     ├─ header.html
│  │     ├─ footer.html
│  │     └─ logout-script.html
│  ├─ static/                            # 정적 리소스
│  │  └─ js/
│  │     └─ jangbogo.js                  # 공통 JavaScript
│  ├─ application.yml                    # 메인 설정 파일
│  ├─ application-local.yml              # 로컬 환경 설정
│  ├─ application-prod.yml               # 운영 환경 설정
│  ├─ log4j2-spring.xml                  # 로그 설정
│  ├─ schema.sql                         # DB 스키마
│  └─ data.sql                           # 초기 데이터
│
├─ config/                               # 외부 설정 파일
│  ├─ admin.properties                   # Admin 계정 (Git 제외)
│  ├─ admin.properties.example           # Admin 계정 예제
│  ├─ jbg_config.yml                     # 장보고 설정
│  ├─ jbg_config.yml.example             # 장보고 설정 예제
│  ├─ mall_account.yml                   # 쇼핑몰 계정 (Git 제외)
│  ├─ mall_account.yml.example           # 쇼핑몰 계정 예제
│  └─ backup/                            # 자동 백업 폴더
│
├─ db/                                   # SQLite 데이터베이스
│  └─ jangbogo-dev.db
│
├─ logs/                                 # 로그 파일
│  ├─ jangbogo.log
│  └─ error.log
│
├─ build.gradle                          # Gradle 빌드 설정
├─ gradle.properties                     # Gradle 속성
├─ LICENSE                               # AGPL-3.0 라이선스
├─ NOTICE                                # 고지 사항
└─ README.md                             # 프로젝트 문서
```

### 패키지 상세 설명

| 패키지 | Full Name | 설명 |
|--------|-----------|------|
| **ctrl** | **Controller** | 클라이언트 요청 처리<br/>- REST API 엔드포인트 정의<br/>- HTTP 요청/응답 처리<br/>- 세션 관리 |
| **dao** | **Data Access Object** | 데이터베이스 연결 및 처리<br/>- JDBC를 통한 DB 접근<br/>- CRUD 쿼리 실행<br/>- 트랜잭션 관리 |
| **dev** | **Development** | 개발 테스트용<br/>- 개발용 테스트 API<br/>- 예제 코드<br/>- 디버깅 도구 |
| **dto** | **Data Transfer Object** | 데이터 전송 객체<br/>- 장보고 설정 정보 관리<br/>- 쇼핑몰 계정 정보<br/>- YAML/JSON 바인딩 |
| **svc** | **Service** | 쇼핑몰 접속 및 요청 처리<br/>- 비즈니스 로직 구현<br/>- 쇼핑몰별 크롤링<br/>- WebDriver 관리 |
| **sys** | **System** | 시스템 설정 및 인증<br/>- 인증/권한 관리<br/>- 세션 관리<br/>- 시스템 상수 정의 |
| **util** | **Utility** | 유틸리티 클래스<br/>- 공통 함수<br/>- 데이터 변환<br/>- 예외 처리 헬퍼 |

---

## 🚀 빠른 시작

### 1) 요구사항
- **JDK 21+**
- **Edge 또는 Chrome** (Selenium 크롤링용)
- (선택) Windows에서 자격 증명 관리자 사용 시 Powershell/권한

### 2) 실행
```bash
cd server
./gradlew clean bootRun
# 또는
./gradlew clean build
java -jar build/libs/jangbogo-server-1.0.0.jar
```

브라우저에서: <http://localhost:8080/admin>  
기본 REST API 베이스: `/api`

---

## ⚙️ 설정(application.yml 예시)

```yaml
server:
  port: 8080

spring:
  main:
    banner-mode: "off"

jangbogo:
  storage:
    format: ndjson         # ndjson | json
    dir: "${LOCALAPPDATA:/tmp}/JANGBOGO/data"
  crawler:
    headless: false
    delayMs: 800
    timeoutSec: 20
    userDataDir: "${LOCALAPPDATA:/tmp}/JANGBOGO/profile"
  schedule:
    enabled: false
    cron: "0 0 3 * * *"    # 매일 03:00
  sites:
    examplemall:
      enabled: true
      baseUrl: "https://www.example.com"
      loginMode: "manual"  # manual | password | otp
security:
  admin:
    username: "admin"
    password: "change-me"
    allowedOrigins: []     # 필요 시 CORS 화이트리스트
```

> 비밀번호/토큰 등 민감정보는 평문 저장 금지.  
> Windows **자격 증명 관리자/DPAPI** 또는 환경변수/외부 Vault 사용을 권장합니다.

---

## 🔐 Admin 계정 설정 가이드

프로젝트는 3가지 방법으로 Admin 계정을 안전하게 관리할 수 있습니다.

### Option 1: 환경변수 사용 (권장)

환경변수를 통해 설정값을 주입합니다:

```bash
# Windows PowerShell
$env:ADMIN_ID="your_admin_id"
$env:ADMIN_PASS="your_secure_password"
./gradlew bootRun

# Linux/Mac
export ADMIN_ID=your_admin_id
export ADMIN_PASS=your_secure_password
./gradlew bootRun
```

`application.yml`에서 자동으로 환경변수를 읽습니다:
```yaml
admin:
  id: ${ADMIN_ID:admin}      # 환경변수가 없으면 'admin' 사용
  pass: ${ADMIN_PASS:admin1234}
```

### Option 2: Profile별 설정 파일 분리

개발/운영 환경을 분리하여 관리합니다:

**개발 환경 실행:**
```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```
- 설정 파일: `src/main/resources/application-local.yml`
- 개발용 계정 정보 포함 (Git 커밋 가능)

**운영 환경 실행:**
```bash
java -jar build/libs/jangbogo-1.0.0.jar --spring.profiles.active=prod
```
- 설정 파일: `src/main/resources/application-prod.yml`
- 운영 계정 정보 포함 (Git 커밋 금지 - `.gitignore` 처리됨)

### Option 3: 외부 Properties 파일 (최고 보안)

민감한 정보를 프로젝트 외부 파일로 분리합니다:

**1) 설정 파일 생성:**
```bash
# config/admin.properties.example 파일을 복사
cp config/admin.properties.example config/admin.properties

# 실제 값으로 수정
# admin.id=your_real_admin_id
# admin.pass=your_real_secure_password
```

**2) 자동 로드:**
`application.yml`에서 자동으로 `config/admin.properties` 파일을 import합니다:
```yaml
spring:
  config:
    import: optional:file:./config/admin.properties
```

**3) 보안:**
- `config/admin.properties`는 `.gitignore`에 등록되어 Git에 커밋되지 않음
- 운영 서버에 수동으로 배포 필요
- 파일 권한 설정 권장 (Linux: `chmod 600`)

### 설정 파일 우선순위

다음 순서로 설정값이 적용됩니다 (나중 것이 우선):
1. `application.yml` (기본값)
2. `config/admin.properties` (외부 파일)
3. `application-{profile}.yml` (profile 설정)
4. 환경변수 `${ADMIN_ID}`, `${ADMIN_PASS}` (최우선)

### 보안 체크리스트

- ✅ `config/admin.properties`를 `.gitignore`에 추가
- ✅ `application-prod.yml`을 `.gitignore`에 추가
- ✅ 운영 환경에서는 환경변수 또는 외부 파일 사용
- ✅ 강력한 비밀번호 사용 (최소 12자, 대소문자+숫자+특수문자)
- ⚠️ Git에 민감정보 커밋하지 않기
- ⚠️ 운영 DB 파일(`*.db`)도 `.gitignore` 처리

---

## 🧩 설정용 최소 UI (Bootstrap 5)

- 경로: `src/main/resources/static/admin/index.html`  
- CDN 기반 Bootstrap 5 사용, 간단한 Form + Fetch 호출 구성

예시 스켈레톤:
```html
<!doctype html>
<html lang="ko">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>JANGBOGO 설정</title>
  <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
</head>
<body class="container py-4">
  <h1 class="h3 mb-3">JANGBOGO 설정</h1>
  <form id="configForm" class="vstack gap-3">
    <!-- storage.format, storage.dir, crawler.headless 등 간단 입력 -->
    <button class="btn btn-primary" type="submit">저장</button>
    <button class="btn btn-outline-secondary" id="btnStart" type="button">즉시 수집 시작</button>
  </form>
  <script>
    // fetch('/api/config') 로드 → 폼 바인딩, 제출 시 PUT /api/config
  </script>
</body>
</html>
```

> _주의_: 이 UI는 **설정/상태 관리만** 제공합니다. 구매내역 자체를 보여주는 화면은 포함하지 않습니다.

---

## 🗄️ 데이터 포맷

- **NDJSON(권장)**: 1행 = 1레코드(주문/아이템/스냅샷 등). 대용량·증분 처리에 유리.
- 기본 경로: Windows `%LOCALAPPDATA%/JANGBOGO/data` (리눅스는 `$XDG_DATA_HOME` 또는 `/tmp` 대체)  
- 스키마 버전 필드를 포함하세요: `"schemaVersion": 1`

예시(단일 레코드):
```json
{"schemaVersion":1,"site":"examplemall","orderId":"A-123","date":"2025-10-06","total":32800,"currency":"KRW","items":[{"name":"USB-C 케이블","qty":2,"price":6400}],"status":"DELIVERED","ts":"2025-10-06T03:10:00Z"}
```

---

## 🔐 보안 권장사항

- `/admin` 및 `/api/**`에 **인증/권한** 적용(기본은 로컬 관리자만 접근)  
- 운영 환경에서는 **HTTPS** 및 **Reverse Proxy**(IP 제한, Basic Auth) 고려  
- 크롤링 헤더/지연/빈도 제한 설정으로 서비스 정책 준수  
- 민감정보는 **외부 보안 저장소**(Credential Manager/Vault) 사용

---

## 🧾 라이선스

- 본 프로젝트는 **AGPL-3.0-or-later** 입니다.  
- 네트워크를 통한 상호작용이 이루어지는 개작본 제공 시 **변경한 소스코드 제공 의무**가 발생합니다.  
- 배포 시 `LICENSE`와 `NOTICE`를 포함하세요.

소스 파일 헤더(SPDX) 예:
```java
/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (c) 2025 jiniebox
 */
```
```js
/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (c) 2025 jiniebox
 */
```

**3rd-party (예시)**: Bootstrap 5 (MIT), Popper.js (MIT), Selenium (Apache-2.0), Jackson (Apache-2.0), SLF4J (MIT), Logback (EPL/LGPL).

---

## 🤝 기여(Contributing)

- 이슈/PR 환영합니다. 설정/어댑터/스케줄 관련 제안은 스펙에 맞춰 주세요.  
- 커밋 메시지: Conventional Commits 권장 (`feat:`, `fix:`, `docs:`, …).  
- CI에서 **라이선스 스캔/SBOM**(Syft/Trivy/FOSSLight 등) 자동화 권장.

---

## 🧩 Gradle 의존성 예시 (server)

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security") // 선택: /admin 보호
    implementation("org.seleniumhq.selenium:selenium-java:4.23.0")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("org.slf4j:slf4j-api:2.0.13")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.6")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
```

---

## 📮 문의

- Copyright © **jiniebox**
- 라이선스/법적 문의 및 기능 제안은 GitHub Issues를 이용해주세요.

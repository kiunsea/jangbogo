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

## 📁 프로젝트 구조(예시)

```
JANGBOGO/
├─ server/                                # Spring Boot
│  ├─ src/main/java/...
│  │  ├─ com.jiniebox.jangbogo
│  │  │  ├─ api/                         # REST 컨트롤러
│  │  │  ├─ service/                     # 수집/스케줄/보안 로직
│  │  │  ├─ adapters/                    # 쇼핑몰별 어댑터
│  │  │  └─ config/                      # 설정 바인딩/밸리데이션
│  ├─ src/main/resources/
│  │  ├─ static/admin/                   # 설정용 최소 UI (Bootstrap 페이지)
│  │  │  └─ index.html
│  │  └─ application.yml
│  ├─ build.gradle.kts
│  └─ NOTICE
├─ LICENSE
└─ README.md
```

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

# JANGBOGO (장보고) 🧾🛍️  
온라인/오프라인 쇼핑몰 **구매내역 수집·관리** 오픈소스

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3-blue.svg)](https://www.gnu.org/licenses/agpl-3.0)
![Java](https://img.shields.io/badge/language-Java-orange)
![JavaScript](https://img.shields.io/badge/language-JavaScript-yellow)
![Bootstrap 5](https://img.shields.io/badge/UI-Bootstrap%205-7952B3)
![Platform](https://img.shields.io/badge/platform-Windows%20Desktop-blue)
![Status](https://img.shields.io/badge/status-Alpha-lightgrey)

**JANGBOGO**는 사용자가 여러 온라인 쇼핑몰에서의 **구매내역을 한 곳에 모아** 확인·검색·내보내기 할 수 있도록 돕는 데스크톱 앱입니다.  
백엔드는 **Java**, 프런트엔드는 **JavaScript + Bootstrap 5**로 구성되며, **Selenium**을 이용해 웹 화면을 자동 탐색/수집하고 **로컬 JSON(NDJSON)** 형식으로 저장합니다. Windows 배포는 **`jpackage`** 로 제공합니다.

---

## ✨ 주요 기능

- 여러 쇼핑몰 주문/결제/배송 **구매내역 수집**
- 구매내역 **검색/필터/정렬/내보내기(CSV/JSON)**
- **NDJSON(JSON Lines)** 기반 저장 – 대용량·증분 저장에 유리
- **오프라인 우선** 로컬 데이터 보관(사용자 PC)
- **윈도우 인스톨러(MSI/EXE)** 제공(jpackage)
- **확장 가능한 어댑터 구조**: 쇼핑몰별 수집 로직 플러그인화

---

## 🛠 기술 스택

- **Backend**: Java 21+, Gradle, Selenium 4, Jackson, SLF4J/Logback  
- **Frontend**: JavaScript (ES2020+), Bootstrap 5, Fetch API  
- **Packaging**: jpackage (MSI/EXE), 선택적으로 jlink 경량 런타임  
- **데이터**: UTF-8 NDJSON / JSON (스키마 버전 관리)

---

## 📁 프로젝트 구조(예시)

```
JANGBOGO/
├─ backend/                     # Java API & 수집 오케스트레이션
│  ├─ src/main/java/...
│  ├─ src/main/resources/
│  ├─ build.gradle.kts
│  └─ NOTICE
├─ frontend/                    # JS + Bootstrap5 UI
│  ├─ src/                      # HTML/CSS/JS
│  ├─ public/                   # 정적 파일
│  ├─ package.json
│  └─ NOTICE
├─ data/                        # 기본 로컬 데이터 디렉터리(런타임 생성)
├─ tools/                       # sbom/라이선스 스캔, 스크립트
├─ LICENSE                      # AGPL-3.0
├─ NOTICE                       # 제3자 라이선스 고지 요약
└─ README.md
```

---

## 🚀 빠른 시작

### 1) 필수 요건
- **Windows 10/11**
- **JDK 21+**
- **Node.js 20+ (프런트 빌드 시)**
- (권장) Edge 또는 Chrome 설치

### 2) 백엔드 실행
```bash
cd backend
# Gradle Wrapper 사용 권장
./gradlew clean build run
```

### 3) 프런트엔드 실행(개발 모드)
```bash
cd frontend
npm install
npm run dev   # 개발 서버(정적 프록시/SPA 등 프로젝트 설정에 맞게)
```

> 기본적으로 백엔드는 `http://localhost:8080` (예시), 프런트는 `http://localhost:5173` 등으로 뜨도록 설정합니다. 실제 포트는 프로젝트 설정을 참고하세요.

---

## ⚙️ 설정(예시)

### 애플리케이션 설정 파일
`backend/src/main/resources/application.properties` 또는 `config/app.config.json`(선호 형식)을 사용합니다.

`app.config.json` 예시:
```json
{
  "schemaVersion": 1,
  "storage": {
    "format": "ndjson",
    "dir": "%LOCALAPPDATA%/JANGBOGO/data"
  },
  "crawler": {
    "headless": false,
    "delayMs": 800,
    "timeoutSec": 20,
    "userDataDir": "%LOCALAPPDATA%/JANGBOGO/profile"
  },
  "sites": {
    "examplemall": {
      "enabled": true,
      "baseUrl": "https://www.example.com",
      "loginMode": "manual"  // 또는 "password", "otp"
    }
  }
}
```

> 인증정보(아이디/비밀번호/토큰 등)는 평문 저장 금지. **Windows 자격 증명 관리자/DPAPI** 등을 통해 암호화 저장을 사용하세요.

---

## 🧱 데이터 포맷

### NDJSON(권장)
각 줄에 한 건의 JSON 기록을 저장합니다.
```json
{"schemaVersion":1,"site":"examplemall","orderId":"A-123","date":"2025-10-06","buyer":"홍길동","total":32800,"currency":"KRW","items":[{"name":"USB-C 케이블","sku":"UC-1M","qty":2,"price":6400}],"status":"DELIVERED","ts":"2025-10-06T03:10:00Z"}
{"schemaVersion":1,"site":"examplemall","orderId":"A-124","date":"2025-10-05","buyer":"홍길동","total":129000,"currency":"KRW","items":[{"name":"블루투스 이어폰","sku":"BT-101","qty":1,"price":129000}],"status":"SHIPPED","ts":"2025-10-06T03:12:10Z"}
```

### 스키마(요약)
- `schemaVersion`: 스키마 버전
- `site`: 쇼핑몰 식별자
- `orderId`, `date`, `buyer`, `items[] {name, sku, qty, price}`, `total`, `currency`, `status`
- 감사/추적: `ts`(수집 시각), 필요 시 `snapshotHtmlPath`, `screenshotPath`

> 스키마 변경 시 마이그레이션 스크립트와 함께 `schemaVersion`을 올려주세요.

---

## 🧭 수집(크롤링) 동작 개요

1. Selenium으로 대상 쇼핑몰 접속  
2. 로그인(수동/자동·OTP 처리)  
3. 주문 내역 페이지 탐색 → 페이지네이션 처리  
4. DOM 파싱 → **안정 셀렉터**(id/data-*) 우선, 명시적 대기 사용  
5. 항목 단위로 **NDJSON Append** 저장  
6. 필요 시 스크린샷/HTML 스냅샷과 함께 로깅

> **반봇/약관 준수**를 위해 요청 간 랜덤 지연·빈도 제한·사용자 개입 모드를 제공하세요. 공식 API가 있는 경우 API 사용을 우선 검토합니다.

---

## 🖥 배포(Windows 인스톨러)

### jpackage (MSI 예시)
```powershell
jpackage `
  --type msi `
  --input backenduild\libs `
  --main-jar jangbogo-app-all.jar `
  --name "JANGBOGO" `
  --app-version 1.0.0 `
  --vendor "jiniebox" `
  --win-menu --win-shortcut --win-dir-chooser --win-per-user-install `
  --icon .\packagingpp.ico `
  --license-file .\LICENSE
```
- 코드 서명 인증서를 사용하면 SmartScreen 경고를 줄일 수 있습니다.  
- `jlink`로 경량 런타임 이미지를 만들고 `--runtime-image`를 지정하면 배포 크기를 줄일 수 있습니다.

---

## 🧩 어댑터 추가 가이드(새 쇼핑몰 연결)

1. `backend/src/main/java/.../adapters/<SiteName>Adapter.java` 생성  
2. **로그인 → 목록 탐색 → 상세 파싱** 3단계 메서드 분리  
3. 셀렉터는 `data-testid`/`id` 우선, XPath는 최후수단  
4. 예외 발생 시 **재시도 + 스크린샷 + DOM 저장**  
5. 결과를 표준 레코드(Map/DTO)로 변환 후 `NDJSON`에 append

어댑터 등록:
```json
"sites": {
  "examplemall2": { "enabled": true, "baseUrl": "https://mall2.example.com", "loginMode": "manual" }
}
```

---

## 🔐 개인정보 & 법적 고지

- 본 프로젝트는 **사용자 개인 PC에 로컬 저장**을 기본으로 합니다.  
- **개인정보보호법(PIPA)** 등 해당 법령과 각 쇼핑몰 **이용약관/robots.txt**를 준수하세요.  
- 자동화 금지 조항이 있는 서비스는 사용자가 **명시적 동의**를 했더라도 법적 분쟁 소지가 있을 수 있습니다.  
- 수집 목적/보관기간/삭제 절차를 **설정 화면(Privacy/About)**에 안내하세요.  
- 인증정보는 **암호화 저장**(Windows 자격 증명 관리자/DPAPI)하고 가급적 세션/토큰을 사용하십시오.

---

## 🧾 라이선스

- 본 프로젝트는 **AGPL-3.0-or-later** 라이선스를 따릅니다.  
- 네트워크를 통한 상호작용이 이루어지는 개작본을 제공하는 경우, **변경한 소스코드 제공 의무**가 발생합니다.  
- `LICENSE`(AGPL-3.0) 및 `NOTICE` 파일을 배포물에 포함하세요.

소스 파일 헤더 예시(SPDX):
```java
/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (c) 2025 jiniebox
 */
```
```js
/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 Copyright (c) 2025 jiniebox
 */
```

**제3자 구성요소(예시)**  
- Bootstrap 5 (MIT), Popper.js (MIT)  
- Selenium (Apache-2.0)  
- Jackson (Apache-2.0)  
- SLF4J (MIT), Logback (EPL-1.0/LGPL-2.1)  
각 소프트웨어의 라이선스 요건(고지/사본 포함)을 준수하세요.

---

## 🤝 기여(Contributing)

- 이슈/PR 환영합니다. 재현 가능한 버그 리포트 템플릿을 사용해주세요.  
- 커밋 메시지는 **Conventional Commits** 권장: `feat:`, `fix:`, `docs:`, `chore:` …  
- CI에서 **라이선스 스캔/SBOM**(Syft/Trivy/FOSSLight 등) 자동화를 권장합니다.

---

## 🗺 로드맵(초안)

- [ ] 쇼핑몰 어댑터 템플릿/CLI 생성기  
- [ ] UI: 월별 지출 차트/카테고리 통계(리포트)  
- [ ] 내보내기: XLSX, 가계부 앱 포맷  
- [ ] 자동 업데이트 채널(winget/자체 업데이터)  
- [ ] 다국어(i18n) & 다크 모드  
- [ ] OTP/CAPTCHA 반자동 처리 UX

---

## 📮 문의

- 저작권자(Copyright): **jiniebox**  
- 이슈/기능 제안: GitHub Issues 탭 이용

---

### 부록: Gradle/의존성 예시

```kotlin
// backend/build.gradle.kts
dependencies {
    implementation("org.seleniumhq.selenium:selenium-java:4.23.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.core:jackson-core:2.17.2")
    implementation("org.slf4j:slf4j-api:2.0.13")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.6")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
}
```

```json
// frontend/package.json (요약)
{
  "name": "jangbogo-ui",
  "version": "1.0.0",
  "license": "AGPL-3.0-or-later",
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "bootstrap": "^5.3.3"
  }
}
```

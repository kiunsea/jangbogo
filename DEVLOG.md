# DevLog: Jangbogo 작업 이력

## 개요

이 DEVLOG는 프로젝트의 작업 이력을 기록합니다.

**작업 기록 형식**: 각 작업은 `YYYY-MM-DD HH:MM` 형식의 일자로 기록됩니다.

---

## 주요 변경사항

### [2026-04-23 09:00] v0.9.0 - 트레이 이중화 정리 + 배포 가이드 재구성

#### 작업 개요

v0.7.0 에서 PowerShell 기반 트레이(`Jangbogo-Tray.ps1`)를 배포 기본으로 도입하면서 Java 기반 `TrayApplication` 이 죽은 코드로 남아 있었음. 실제 배포 경로(`install.bat` → WinSW `--service` + PowerShell 트레이)에서 Java 트레이를 호출하지 않음을 확인하고 안전하게 제거. 동시에 `DEPLOYMENT_GUIDE.md` 본문을 "원스톱 표준 / 고급 수동 / 문제 해결" 3부로 재구성.

#### 배경

- SESSION_HANDOFF 의 후속 작업 후보 #3 (DEPLOYMENT_GUIDE 정제) + #4 (트레이 앱 이중화 정리) 를 한 번에 처리.
- 조사 결과, `packageDist` 태스크가 `packaging/distribution/` 과 `packaging/winsw/` 만 ZIP 에 넣으므로 `TrayApplication` 과 `--tray` / `--install-complete` 플래그는 **실제 배포 경로에서 완전히 미사용** 임이 확인됨.
- v0.8.1 docs patch 에서 원스톱 설치 섹션을 추가했으나, 본문 뒷부분은 여전히 수동 절차 중심이라 "원스톱이 표준이고 수동은 참고" 라는 방향성이 명확히 드러나지 않았음.

#### 상세 내용

**1. Java 트레이 제거**
- `src/main/java/com/jiniebox/jangbogo/sys/TrayApplication.java` 삭제.
- `JangbogoLauncher.java`:
  - `MODE_TRAY` / `MODE_INSTALL_COMPLETE` 상수 제거.
  - `launchTrayMode()` / `launchInstallCompleteMode()` 메서드 제거.
  - `ExecutionMode` 열거형을 `SERVICE` / `NORMAL` 로 축소.
  - `filterModeArguments()` 를 `--service` 하나만 필터링하도록 단순화.
  - 클래스 Javadoc 을 배포 트레이는 PowerShell 스크립트가 담당한다는 사실로 갱신.

**2. DEPLOYMENT_GUIDE.md 재구성**
- 목차를 3부 (🚀 표준 / 🔧 고급·수동 / 🚨 문제 해결) 로 재편.
- "설치 방법" → "수동 설치", "실행 방법" → "수동 실행", "Windows 서비스 등록" → "수동 Windows 서비스 등록", "제거 방법" → "수동 제거" 로 개명.
- 각 수동 섹션 상단에 "💡 `install.bat` / `uninstall.bat` 이 이 단계를 자동화합니다" 안내 블록 추가.
- 하단 버전 표시 0.6.0 → 0.9.0, 최종 수정일 2026-04-23 으로 갱신.
- 외부 파일에서 `DEPLOYMENT_GUIDE.md#설치-방법` 등 옛 앵커 참조는 0건 — 링크 깨짐 없음.

**3. CLAUDE.md 동기화**
- 프로젝트 구조 설명의 `sys/` 항목에서 `TrayApplication` 제거.
- 실행 모드 섹션을 `--service` / 인자 없음 두 가지로 축소하고, 배포 트레이는 PowerShell 이 담당한다는 주석 추가.

#### 검증

- `./gradlew compileJava` → BUILD SUCCESSFUL.
- `./gradlew spotlessApply` → 포맷 적용 완료.
- 영향 범위 재확인: `packaging/distribution/install.bat` 과 `packaging/winsw/jangbogo-service.xml` 은 `--service` 만 사용 → 변경 영향 없음.

#### 메모

- `packaging/scripts/` 폴더(post-install.bat / pre-uninstall.bat / Jangbogo.bat v0.5.5 참조)는 jpackage 시도 실패 시절 유물로 확인됨. `packageDist` 어디에서도 참조되지 않음. 이번 범위에서는 건드리지 않고 별도 정리 태스크로 spawn.

---

### [2026-04-18 15:00] v0.8.0 - 수집 실패 상세 진단 기능

#### 작업 개요

쇼핑몰 크롤링이 사이트 구조 변경 등으로 실패할 때, 실패 시점의 컨텍스트(단계명, 현재 URL, 페이지 타이틀, 타겟 셀렉터, 스크린샷)를 자동으로 포착해 DB에 기록하고 UI에서 모달로 상세 확인할 수 있도록 기능을 강화했습니다.

#### 배경

- v0.7.0에 `jbg_collect_log` 테이블과 `/collect-logs` 페이지를 추가했으나, 각 쇼핑몰 크롤러(Ssg/Oasis/Emart/Hanaro)의 `getItems()` 메서드가 내부 try/catch에서 예외를 `log.error`로만 기록하고 빈 배열을 반환하는 패턴이었습니다.
- 이 때문에 상위 `MallOrderUpdaterRunner`는 "성공(0건 수집)"으로 인식하여 FAIL 로그조차 남기지 않았습니다.
- 사용자가 "자꾸 실패하는 사이트가 있는 것 같은데 이력이 없다"고 보고한 원인이 바로 이것이었습니다.
- 또한 설령 FAIL이 기록되어도 스택트레이스만 있어 "어느 단계에서", "어떤 URL에서", "어떤 셀렉터를 찾다가" 실패했는지 알 수 없었습니다.

#### 상세 내용

**1. 신규 유틸 클래스 (`svc/`)**
- `CollectException`: stepName/currentUrl/pageTitle/targetSelector/screenshotPath + cause를 담는 도메인 예외.
- `CollectStep`: `CollectStep.call(driver, mallName, "signin", () -> ...)` 형태로 Selenium 작업을 래핑. 예외 발생 시 WebDriver에서 URL/타이틀을 자동 추출하고 스크린샷을 찍어 `CollectException`으로 변환.
- `ScreenshotUtil`: `logs/screenshots/yyyyMMdd/{mall}-{HHmmss}-{nano}.png` 저장, 30일 이전 폴더 자동 정리.

**2. DB 스키마 마이그레이션**
- `schema.sql`에 5개 컬럼 추가: step_name, current_url, page_title, target_selector, screenshot_path (모두 TEXT)
- `StartupTasks.migrateCollectLogSchema()`: 애플리케이션 시작 시 `PRAGMA table_info`로 기존 컬럼 확인 후 없는 것만 `ALTER TABLE ADD COLUMN`. 기존 사용자 데이터 100% 보존.

**3. DAO 확장 (`JbgCollectLogDataAccessObject`)**
- 확장 시그니처 `addLog(..., stepName, currentUrl, pageTitle, targetSelector, screenshotPath, ...)` 추가.
- 기존 9파라미터 시그니처는 delegation으로 하위호환.
- `getLog(seq)` 단일 조회 메서드 추가.
- `mapRow()`에 `safeGetString()`으로 신규 컬럼이 없어도 안전하게 동작.

**4. 쇼핑몰 크롤러 리팩토링 (Ssg/Oasis/Emart/Hanaro)**
- `getItems()`의 swallow catch 패턴 제거 → `CollectStep.call/wrap`으로 교체.
- 로그인 실패(`signin()` false 반환) 시 "로그인 실패 — 자격증명 또는 사이트 구조 변경 가능성" 메시지와 함께 `CollectException` throw.
- driver.quit()는 여전히 finally에서 수행.

**5. 상위 레이어 수정**
- `MallOrderUpdaterRunner.run()`: catch 블록에서 `unwrapCollectException()`으로 예외 체인을 거슬러 올라가 `CollectException` 추출 후 컨텍스트 필드를 DAO에 전달.
- `MallSchedulerService.runCollectForMall()`: 동일 패턴 적용. `saveFailLog()`에는 step="scheduler-precheck" 지정.

**6. API 확장 (`AdminController`)**
- `GET /api/collect-logs/{seq}`: 단일 로그 상세 조회.
- `GET /api/collect-logs/{seq}/screenshot`: PNG 스트리밍. `logs/screenshots` 경로 외부 접근 차단(보안).

**7. UI 확장 (`collect-logs.html`)**
- 테이블에 "실패 단계" 컬럼 추가 (회색 배지로 step_name 표시).
- 테이블에 "상세" 컬럼 추가 — "보기" 버튼 클릭 시 Bootstrap 모달 오픈.
- 상세 모달 내용: 실행 시간, 쇼핑몰, 상태, 실패 단계, 현재 URL(클릭 시 새 탭 오픈), 페이지 타이틀, 타겟 셀렉터(코드 스타일), 오류 메시지, 스크린샷 썸네일(클릭 시 확대 모달), 스택트레이스(pre).
- 필터 바에 쇼핑몰/실패 단계 드롭다운 추가 (클라이언트 사이드 필터링).
- 스크린샷 확대용 별도 모달(modal-xl).

#### 기대 효과

- 사이트 구조 변경으로 인한 실패를 즉시 인지 가능. 어느 셀렉터를 찾다가 실패했는지 바로 확인.
- 스크린샷으로 시각적 증거 확보. 로그인 페이지가 바뀐 건지, 중간 페이지가 바뀐 건지 한눈에 파악.
- 쇼핑몰별/단계별 실패 분포 집계 가능 (예: "하나로마트는 navigatePurchased 단계에서 반복 실패").

### [2026-04-17 14:00] v0.7.0 - 수집 오류 로그 + Windows 서비스 관리 + muse-agent 패턴 이식

#### 작업 개요

v0.6.1 이후 누적된 세 덩어리의 미커밋 변경을 v0.7.0으로 묶어 릴리스합니다.
1) 자동 수집 실행 결과를 DB에 기록하고 UI에서 조회 가능하게 하는 오류 로그 기능
2) Windows 서비스 설치/제거를 원스톱으로 처리하는 `install.bat`/`uninstall.bat` 추가
3) `if-only/muse-agent` 프로젝트의 성공 패턴을 참고해 배포 패키지 구조를 전면 재정렬

#### 배경 및 요구사항

- 자동 수집 실패 원인을 사용자가 대시보드에서 바로 확인할 수 있어야 함
- Windows 서비스 등록/해제를 수동 CLI 없이 원클릭 처리 필요
- 기존 `install.bat`이 UTF-8로 저장되어 cmd.exe의 CP949 파싱과 충돌해 `'cho'은(는) 내부 또는 외부 명령...` 같은 오류로 실행 불가 상태 → 인코딩 문제를 근본적으로 제거해야 함
- 배포 패키지의 견고성/자동화 수준을 muse-agent 수준으로 끌어올려 릴리스 신뢰도를 확보

#### 상세 내용

**1) 수집 실행 오류 로그 기능**

- DB: `schema.sql`에 `jbg_collect_log` 테이블 추가
- DAO: `JbgCollectLogDataAccessObject` 신규 — `addLog`, `getAllLogs`, `getFailLogs`, `getSummary`
- 서비스 계층:
  - `MallOrderUpdaterRunner.run()` — 수집 완료/실패 시 `saveCollectLog()` 호출
  - `MallSchedulerService.runCollectForMall()` — 검증 실패/예외 시 `saveFailLog()` 호출
- API: `GET /api/collect-logs/summary`, `/failures`, 전체 조회 3개 엔드포인트
- UI: `collect-logs.html` 신규 페이지, 대시보드 "실행 결과 요약" 카드, 헤더 "오류 로그" 메뉴
- `HomeController`에 `/collect-logs` 라우트 추가

**2) Windows 서비스 통합 관리 (muse-agent 패턴 풀 이식)**

- `install.bat` (신규, 영문): 관리자 권한 + JAR 자동 탐지 + Unblock + JRE 자동 다운로드 + XML 자동 동기화 + 기존 프로세스 정리 옵션 + 포트 점유 체크 + 서비스 RUNNING 폴링(20초) + 실패 시 로그 자동 tail + 대시보드 ready polling(45초) + 단축아이콘 생성 + 트레이 기동 + 브라우저 오픈
- `uninstall.bat` (신규, 100% ASCII): 관리자 권한 + 서비스 stop/uninstall + 프로세스 kill + 단축아이콘 삭제
- `Jangbogo-Tray.ps1` (신규): `NotifyIcon` 기반, 메뉴(Open Dashboard / Status / Start / Stop / Restart / Exit), 쇼핑카트 아이콘 on-the-fly 생성
- `create-shortcuts.ps1` (신규): 바탕화면 `Jangbogo Tray.lnk` + `Jangbogo Dashboard.url`, 시작 메뉴 `Jangbogo Tray.lnk`
- `download-jre.ps1` (신규): Temurin JRE 21 Windows x64 자동 다운로드
- `Jangbogo.bat` (전면 재작성, 영문): JAR 자동 탐지, 시스템 Java ≥21 검증, JRE fallback, 포트 충돌 시 대체 포트 프롬프트

**3) 빌드 / WinSW XML**

- `build.gradle`: version 0.6.1 → 0.7.0, `packageDist`에 PS1 3종 include 추가
- `packaging/winsw/jangbogo-service.xml`: JAR 참조 0.6.1 → 0.7.0, `--service` 인자 포함
- `bat/build_package.bat`: `--no-daemon` 옵션 추가

**4) CLAUDE.md 프로젝트 가이드**

- Release/Push 워크플로우 정의 (자동 버전 bump 기본, 애매할 때만 승인)
- DAO 패턴, 실행 모드, DB 스키마, API 목록 명문화

**5) .gitignore**

- `.claude/` 추가

#### 검증

- `./gradlew packageDist --no-daemon` 빌드 성공
- 배포 ZIP 내부 구성 확인: 신규 PS1 3종 + 영문 bat 3종 + JAR + JRE + WinSW XML 포함

---

### [2026-02-15] 애플리케이션 시작 시 1회 수집 기능 추가

#### 작업 개요

애플리케이션 기동 시 스케줄링 복원 **이전**에, 자동 수집이 설정된 쇼핑몰에 대해 **1회 수집**을 실행하도록 기능을 추가했습니다.  
기존에는 스케줄 복원 후 첫 수집이 "주기(분)" 만큼 지연되어 실행되었으나, 이번 변경으로 시작 직후 1회 수집이 먼저 수행된 뒤 주기별 스케줄이 동작합니다.

#### 배경 및 요구사항

- 사용자가 자동 수집을 설정해 두었을 때, PC 재부팅 또는 Jangbogo 재시작 후에도 바로 최신 구매내역을 확보하고자 함
- 스케줄만 복원하면 첫 수집까지 최대 "주기(분)" 만큼 대기해야 하는 문제 해결
- 예: 주기 120분 → 기존에는 시작 후 120분 뒤 첫 수집, 변경 후 시작 직후 1회 수집 + 120분마다 주기 수집


### 2026-01-28 - 하나로마트(Hanaro) 쇼핑몰 통합

#### 새로운 기능

**1. HanaroTest 클래스 생성** (`src/test/java/com/jiniebox/jangbogo/mall/HanaroTest.java`)
- nonghyupmall.com 크롤링을 위한 step-by-step 테스트 클래스 작성
- WebDriver 셋업, 로그인, 페이지 이동, 파싱을 단계별로 검증
- `testFullFlow()` 메서드로 통합 테스트 수행
- 실제 사이트 구조 분석 및 파싱 로직 검증 완료 (26개 품목 파싱 확인)

**2. Hanaro 클래스 완성** (`src/main/java/com/jiniebox/jangbogo/svc/mall/Hanaro.java`)
- `signin()`: nonghyupmall.com 로그인 처리
- `signout()`: `a_id_logout` 버튼 클릭으로 로그아웃
- `navigatePurchased()`: 마트구매영수증 목록 순회 및 수집
  - 목록 페이지(`eltRctwList.nh`) 이동
  - 영수증 행 순회 (여러 건 지원)
  - 각 행 클릭 → 상세보기 버튼(`eltRctwDtlView`) 클릭 → 상세 페이지 파싱
  - DB serial 조회로 이미 수집된 영수증 건너뛰기
- `parseDetailPage()`: 상세 페이지 파싱 로직 분리
  - table[0]: 요약 정보 (구매일자, 구매처, 구매금액)
  - table[1]: 품목 목록 (품목명, 수량, 금액)
- `isAlreadyCollected()`: DB에서 serial+datetime으로 중복 확인

**3. 시스템 통합**
- `JangBoGoManager.getMallSession()`: seq=3 → `new Hanaro()` 매핑 추가
- `MallOrderUpdater.collectItems()`: seq=3 분기 추가로 Hanaro 수집 지원
- `ExportService.getMallIdFromSeq()`: case 3 → "hanaro" 반환 추가

**4. 관리 화면 UI 추가** (`src/main/resources/templates/index.html`)
- HANARO 카드 블록 추가 (연한 초록색 배경 `#e8f8e8`)
- 계정 연결 버튼 (`btn_signin_hanaro`, seq=3)
- 자동 수집 주기 설정 (`data-seq="3"`)
- `openSigninMallForm()` 함수에 seq=3 분기 추가: "하나로마트 계정연결"

#### 변경사항

**1. Serial 형식 개선**
- 기존: 구매일자만 사용
- 변경: `구매일자_구매금액` 조합으로 unique 식별자 생성
- 예시: `20260125_35400` (2026년 1월 25일, 35,400원)

**2. 중복 수집 방지 로직**
- `navigatePurchased()` 단계에서 DB 조회로 이미 수집된 영수증 건너뛰기
- `JbgOrderDataAccessObject.getOrder(serial, datetime, null)` 활용
- 불필요한 크롤링 방지로 효율성 향상

#### 기술적 세부사항

**크롤링 흐름**:
1. nonghyupmall.com 메인 → 로그인 페이지 이동
2. `#userID`, `#password` 필드에 계정 정보 입력
3. 로그인 버튼 클릭 → `a_id_logout` 버튼 존재 확인으로 성공 여부 판단
4. 마트구매영수증 목록 페이지(`BCI1020M/eltRctwList.nh`) 이동
5. 영수증 목록 행 순회 (`//*[@id='content']//table//tbody//tr`)
6. 각 행 클릭 → `eltRctwDtlView` 버튼 클릭 → 상세 페이지
7. 상세 페이지에서 table[0](요약), table[1](품목) 파싱
8. serial 생성 후 DB 중복 체크 → 미수집 건만 결과에 추가

**파싱 구조**:
- 요약 테이블: `th`/`td` 쌍으로 key-value 추출
- 품목 테이블: `tbody//tr` 순회, 각 행의 `td` 3개 (품목/수량/금액)
- 헤더 행 건너뛰기: `"품목".equals(name)` 체크

#### 파일 변경 목록

| 파일 | 변경 유형 | 설명 |
|------|----------|------|
| `HanaroTest.java` | 신규 | 크롤링 테스트 클래스 |
| `Hanaro.java` | 수정 | navigatePurchased, signout, parseDetailPage, isAlreadyCollected 구현 |
| `JangBoGoManager.java` | 수정 | seq=3 Hanaro 매핑 추가 |
| `MallOrderUpdater.java` | 수정 | seq=3 수집 분기 추가 |
| `ExportService.java` | 수정 | getMallIdFromSeq case 3 추가 |
| `index.html` | 수정 | HANARO 카드 및 모달 지원 추가 |
| `data.sql` | 기존 | seq=3, id='hanaro' 이미 등록됨 |

---

## 테스트

### 단위 테스트

### 통합 테스트

### 빌드 테스트

---

## 체크리스트

---

## 관련 이슈

---

## 참고 문서

---

## 배포 정보

---

## 통계

---

## 리뷰 요청사항

---

## 향후 계획

---

## 기타 참고사항

### Breaking Changes

### Migration Guide

### Known Issues

---

## 작업 기록 형식 가이드

새로운 작업을 추가할 때는 다음 형식을 사용하세요:

```markdown
### YYYY-MM-DD HH:MM - 작업 제목

#### 새로운 기능
- 기능 설명

#### 변경사항
- 변경 내용

#### 버그 수정
- 수정 내용

#### 개선사항
- 개선 내용
```

**참고**: 
- 날짜 형식: `YYYY-MM-DD HH:MM` (예: 2026-01-15 14:30)
- 작업은 날짜순으로 정렬 (최신 작업이 위에)

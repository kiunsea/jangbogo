# DevLog: Jangbogo 작업 이력

## 개요

이 DEVLOG는 프로젝트의 작업 이력을 기록합니다.

**작업 기록 형식**: 각 작업은 `YYYY-MM-DD HH:MM` 형식의 일자로 기록됩니다.

---

## 주요 변경사항

### [2026-07-29 00:10] Phase 1 — 수집 장애 버그 일괄 수정 및 기준선 확보 (v0.10.3)

#### 작업 개요

`doc/PLAN-2026-07-28-REV1-decisions-applied.md` 의 Phase 1(버그 일괄 수정)을 수행하고, 종료 조건인 "수집 1회 이상 성공(기준선 확보)"을 달성. 2026-05-30 이후 수집 0건이던 상태에서 **주문 22건 / 아이템 168건**을 실제로 수집했다.

#### 배경

Phase 0A 실측으로 드러난 사실은 "봇 차단"이 아니었다. 배포본 로그(`logs/jangbogo-2026-05-30-1.log.gz`)에 이마트 프로모션 배너 `<img>` 가 로그인 버튼을 덮어 `ElementClickInterceptedException` 이 발생한 순간이 그대로 남아 있었고, 그 뒤 약 2개월간 모든 예약 수집이 `account_status가 1이 아님, 건너뜀` 으로 끝났다.

#### 상세 내용

**1. 기준 DB 결정 (착수 전 확인)**

회귀 판정 기준 DB 를 개발 트리 초기화(0/0)로 확정. `db/backup/jangbogo-dev.db.baseline-2026-07-28` 에 기존 데이터(주문 7 / 아이템 53)를 백업한 뒤 `jbg_order`·`jbg_item` 을 비우고 시작.

**2. 계획서 8개 항목 처리**

| # | 대상 | 처리 |
|---|---|---|
| 1+2 | `WebDriverManager.java` | `new ChromeDriver(options)` + headless 조건 정상화를 **같은 커밋**에서. 54만 고치면 무효였던 headless 가 처음 적용되어 "설정 없음 → headless 기동"이 되는 것을 막기 위함 |
| 3 | `WebDriverManager.java` `@Autowired` | `JangbogoConfig.getInstance()` 홀더로 교체 |
| 4 | `Ssg.java` 하드코딩 `return false` | `isSignedIn()` 으로 관측 상태 반환. 로그인 "완성"이 아니라 "정직화"까지만 |
| 5 | click 11지점 | `ClickUtil.safeClick()` 도입 |
| 6 | `settings.gradle` | 죽은 `includeBuild` 제거 |
| 7 | `jbg_collect_log` | **검증 결과 자동 생성되지 않음.** `migrateCollectLogSchema` 는 ALTER 만 하고, `spring.sql.init` 은 `continue-on-error` 로 실패를 삼켰다. `CREATE TABLE IF NOT EXISTS` 추가 |
| 8 | `ExportService.getMallIdFromSeq` | jiniebox `getSeqById()` 확인 결과 `emart`/`ssg` 둘 다 seq 1 로 매핑되어 무해 → **현행 유지** |

**3. 실행으로 발견해 추가 수정한 3건**

- **SSG 빈 결과 행 파싱 실패** — 조회 기간에 구매내역이 없으면 안내 문구 행(td 1개)이 렌더링되는데 이를 데이터 행으로 파싱해 `td[2]/p` 에서 예외. 이 예외가 seq=1 수집 전체를 중단시켜 Emart 에 도달하지 못하게 한 직접 원인이었다.
- **수집기 간 실패 격리** — seq=1 의 SSG/Emart 가 예외 격리 없이 순차 실행되어 한쪽 실패가 다른 쪽을 막았다. 격리하되 실패를 삼키지 않도록 `partialFailures` → `jbg_collect_log` FAIL 행 경로를 만들었다(v0.8.0 에서 제거한 swallow 패턴의 부활 방지).
- **성공/실패 판정 오탐** — `스킵 > 0 && 신규 == 0` 을 FAIL 로 보던 식 때문에, 17건을 정상 수집하고 15건 중복·2건 키 누락인 정상 상황이 FAIL 로 기록됐다. 데이터가 따라잡히면 상시화되는 오탐.

**4. 실행 검증 (실계정, seq=1 → 이후 seq=2 포함)**

- `ChromeDriver 기동 (headless=false)` — 옵션 전달·headless 정상 (항목 1·2·3)
- `SSG 로그인 판정: signedIn=true` — 4회 연속 재현 (항목 4)
- 이마트 로그인 통과, `data-body=mypage` — 2026-05-30 장애 지점 (항목 5)
- `jbg_collect_log 테이블 확인/생성 완료` + 실제 행 기록 (항목 7)
- **격리 로직 실전 검증**: 마지막 실행에서 Emart 가 `StaleElementReferenceException` 으로 실패했으나 SSG 결과는 보존되고 수집은 계속됐으며, 실패는 `Emart:navigateReceipt` FAIL 행으로 별도 기록됨. 수정 전이었다면 seq=1 전체가 FAIL 이고 아무 데이터도 남지 않았다.
- seq=2(오아시스)도 처음으로 수집 성공 — 7건 / 51아이템

**5. CDP 사용 가능 실증**

계획서 §[정정]의 전제를 실측 확인. typed DevTools(`selenium-devtools-vNNN`)는 Chrome 150 용 아티팩트가 없어 사용 불가지만, `ChromiumDriver.executeCdpCommand` 는 정상 동작한다. `Page.addScriptToEvaluateOnNewDocument` 로 주입한 스크립트가 실제 사이트(ssg.com)에서도 유지되고 `navigator.webdriver` 가 `undefined` 로 마스킹되는 것까지 확인했다. Phase 4A 의 T2 는 이 방식으로 진행 가능하다.

#### 변경 파일

| 파일 | 구분 | 내용 |
|---|---|---|
| `svc/util/WebDriverManager.java` | 수정 | ChromeOptions 전달, headless 조건, 설정 조회 경로 |
| `dto/JangbogoConfig.java` | 수정 | `getInstance()` 인스턴스 홀더 추가 |
| `svc/util/ClickUtil.java` | 신규 | `safeClick` 헬퍼 |
| `svc/mall/Ssg.java` | 수정 | `isSignedIn()`, 빈 결과 행 스킵, safeClick 적용 |
| `svc/mall/Emart.java` / `Hanaro.java` / `Oasis.java` | 수정 | safeClick 적용 |
| `svc/MallOrderUpdater.java` | 수정 | 수집기 격리(`collectFrom`, `partialFailures`) |
| `svc/MallOrderUpdaterRunner.java` | 수정 | 부분 실패 기록, `decideStatus` 분리 |
| `boot/StartupTasks.java` | 수정 | `ensureCollectLogTable` 추가 |
| `settings.gradle` | 수정 | 죽은 `includeBuild` 제거 |
| `build.gradle` | 수정 | `ext['selenium.version'] = '4.31.0'` 로 버전 일원화 |
| `svc/MallOrderUpdaterIsolationTest.java` | 신규 | 격리 회귀 테스트 5건 |
| `svc/MallOrderUpdaterRunnerStatusTest.java` | 신규 | 상태 판정 회귀 테스트 5건 |
| `CLAUDE.md` | 수정 | 외부 의존 항목 갱신(doribox 참조 제거 반영) |

#### 운영 설정 변경 (DB)

- `jbg_mall.collect_interval_minutes` — seq=1,2 를 **720분**으로 (결정 5)
- seq=2 는 검증 중 일시적으로 `auto_collect=0` 으로 두었다가 원복 완료

#### 남은 판단 대기 항목

1. `./gradlew test` 가 실계정 수집을 실행한다 — `JangbogoApplicationTests` 가 전체 컨텍스트를 로드하면 `StartupTasks` 의 `ApplicationReadyEvent` 가 수집을 건다. CI 에서도 시도된다. 기동 수집을 프로퍼티로 가드할지 결정 필요.
2. Emart `navigateReceipt` 의 `StaleElementReferenceException` — 간헐적 발생.
3. 영수증 바코드(`#barcodeTargetRec`) 미인식 2건 — serial/date_time 이 비어 스킵된다. 스킵 동작 자체는 올바르다고 확정됐으나 원인은 미조사.
4. `jbg_collect_log` 의 수정 전 기록 2행(seq 1·3) 삭제 여부.

---

### [2026-05-01 04:00] v0.10.2 정식 릴리즈 발행 (v0.8.0 이후 첫 태그)

#### 작업 개요

v0.8.0 (2026-04-18) 이후 main 에 누적된 다섯 차례의 push (v0.9.0 / v0.9.1 / v0.10.0 / v0.10.1 / v0.10.2) 를 v0.10.2 정식 릴리즈로 묶어 발행. CLAUDE.md 의 Release 워크플로우 8~11 단계 수행.

#### 상세 내용

1. `release/RELEASE_NOTES_v0.10.2.md` 신규 작성 — v0.9.0 ~ v0.10.2 누적 변경을 "새로운 기능 / 내부 개선 / 버그·안정화 / 새 API / 호환성·업그레이드 / 설치 방법" 으로 재구성.
2. release notes 파일 commit + main push (release.yml 이 태그 push 시 해당 파일을 body 로 사용하므로 태그 푸시 전에 main 에 들어가 있어야 함).
3. `git tag v0.10.2` + `git push origin v0.10.2` 로 release.yml 트리거.
4. release.yml 자동 실행 모니터링 — `clean bootJar createJre packageDist` → `Jangbogo-v0.10.2.zip` 생성 → GitHub Release 발행.

#### 메모

- 이번 ZIP 빌드 환경(GitHub Actions runner) 에는 `packaging/winsw/jangbogo-service.exe` 가 없으므로, ZIP 의 `service/` 폴더에는 exe 가 빠진 채로 발행됨. 사용자가 `install.bat` 실행 시 v0.10.2 의 fallback (`download-winsw.ps1`) 이 자동으로 받아오므로 사용자 측 영향 없음 — 의도된 동작.
- 향후 빌드 환경에 사전 다운로드된 WinSW 를 두고 싶으면 release.yml 에 `download-winsw.ps1` 호출 단계를 추가 가능. 현재는 fallback 으로 충분하다고 판단해 단순 유지.

---

### [2026-05-01 03:30] v0.10.2 - WinSW 자동 다운로드 fallback

#### 작업 개요

배포 ZIP 의 `service\jangbogo-service.exe`(WinSW 실행파일) 가 누락되어 `install.bat` 이 즉시 종료되던 문제를 해결. `download-jre.ps1` 과 동일한 패턴으로 `download-winsw.ps1` 을 신규 추가하고, install.bat 이 누락 감지 시 자동 호출하도록 변경.

#### 배경

- `packaging/winsw/jangbogo-service.exe` 는 `.gitignore` 의 `packaging/winsw/jangbogo-service.exe` 라인에 의해 git 추적 제외. 각 빌드 환경에서 한 번 받아둬야 함.
- 빌드 환경에 해당 파일이 없으면 `packageDist` 태스크가 `from('packaging/winsw') { include '*.exe' }` 단계에서 그냥 빈 결과로 처리해 ZIP 에 exe 가 들어가지 않음 (Gradle 의 include 패턴은 매칭 0건이어도 에러를 안 냄).
- 사용자가 새 빌드 환경에서 ZIP 을 만들고 압축 풀어 install.bat 실행 시 "service\jangbogo-service.exe not found" 에러로 막힘. 실제 발생함.
- `download-jre.ps1` 이 이미 동일한 fallback 패턴을 사용하고 있어 (JRE 누락 → Temurin 자동 다운로드) 동일 패턴 적용이 자연스러움.

#### 상세 내용

**1. `download-winsw.ps1` 신규**
- `packaging/distribution/download-winsw.ps1` 추가.
- WinSW v2.12.0 (`WinSW-x64.exe`, .NET Framework 4.6.1 빌드) 을 GitHub Releases 에서 다운로드.
- 다운로드 후 `Unblock-File` 로 MOTW 제거 (Windows 가 인터넷에서 받은 exe 를 차단하면 sc 등록이 거부됨).
- TLS 1.2 강제, 이미 존재 시 skip, 실패 시 throw → install.bat 의 `errorlevel` 검사가 잡아냄.
- PowerShell 5.1 의 비-BOM UTF-8 파일 파스 한계 때문에 한글 주석 + em-dash(`—`) 조합이 파스 에러를 일으켜 영문 주석으로 작성. (`Jangbogo-Tray.ps1` 처럼 BOM 없는 한글이 통과되는 케이스도 있어 케이스별로 차이가 있음 — 안전하게 영문 사용.)

**2. `install.bat` 의 WinSW 누락 분기 변경**
- 기존: WinSW 없으면 `[ERROR] ... Please place the WinSW executable first.` 출력 후 `exit /b 1`.
- 변경: `download-winsw.ps1` 존재 시 자동 호출 → `errorlevel` 검사 → exe 생성 재확인. 다운로드 스크립트도 없거나 실패 시에만 에러로 종료.
- 기존에 WinSW exe 가 있던 사용자는 분기 자체에 진입하지 않아 동작 변동 없음.

**3. `build.gradle` 의 `packageDist`**
- `from('packaging/distribution')` 의 `include` 목록에 `'download-winsw.ps1'` 추가 → 새 ZIP 부터 자동 포함.

#### 검증

- `Parser::ParseFile` 로 `download-winsw.ps1` syntax 0 errors.
- 기존 사용자(빌드 환경에 WinSW 있음) 는 분기 미진입 → 회귀 없음.
- 신규 사용자(WinSW 없음) 는 install.bat 한 번에 다운로드 + 설치 완결.

#### 메모

- WinSW v3.x (alpha) 도 사용 가능하지만 .NET Core 의존성 부담 + production 채택 사례 부족으로 v2.12.0 채택. 향후 .NET 4.6 호환성 이슈 발생 시 재검토.
- 현재 사용자는 이번 push 가 main 에 들어간 후 ZIP 재빌드 + 압축 풀어 install.bat 재실행하면 자동 복구. 또는 즉시 한 줄 PowerShell 다운로드(이전 메시지)로도 복구 가능.

---

### [2026-04-23 17:30] v0.10.1 - 트레이 아이콘 재시작 도구 추가

#### 작업 개요

OS 재부팅 후 Windows 시스템 트레이의 NotifyIcon 새로고침 누락으로 `Jangbogo-Tray.ps1` 프로세스는 살아있지만 아이콘이 보이지 않는 상황을 위해 별도 재시작 진입점을 추가. 사용자가 바탕화면 단축아이콘 한 번으로 트레이를 안전하게 다시 띄울 수 있도록 함.

#### 배경

- v0.7.0 에서 PowerShell 트레이를 도입한 이후, 일부 환경에서 재부팅 직후 트레이 아이콘이 보이지 않는 현상이 보고됨.
- 원인은 Windows explorer.exe 의 알려진 새로고침 버그 — 프로세스 자체는 정상이지만 알림 영역에서 누락됨.
- 기존 "Jangbogo Tray" 단축아이콘을 그대로 더블클릭하면 새 인스턴스가 추가되어 좀비 프로세스가 누적될 수 있어, 명시적 재시작 경로가 필요했음.

#### 상세 내용

**1. `Jangbogo-Tray.ps1` 단일 인스턴스 보호**
- 스크립트 상단에 `param([switch]$Restart)` 추가.
- 글로벌 Mutex `Global\JangbogoTrayInstance` 획득 시도 → 이미 잡혀 있으면 안내 메시지 후 즉시 종료.
- `-Restart` 모드: `Get-CimInstance Win32_Process` 로 같은 스크립트(`Jangbogo-Tray.ps1`)를 실행 중인 powershell.exe / pwsh.exe 프로세스를 모두 찾아 `Stop-Process -Force` 후 400ms 대기 → mutex 획득 → 트레이 시작.
- "Exit Tray" 메뉴 핸들러에서 `ReleaseMutex()` + `Dispose()` 추가.

**2. `Restart-Tray.bat` 신규 (`packaging/distribution/`)**
- 관리자 권한 불필요한 일회성 배치 파일.
- `start "" /B powershell -NoProfile -ExecutionPolicy Bypass -STA -WindowStyle Hidden -File "%SCRIPT_DIR%Jangbogo-Tray.ps1" -Restart` 호출.
- 실행 후 3초 대기 후 자동 종료 (사용자가 메시지를 읽을 시간 확보).

**3. `create-shortcuts.ps1` 확장**
- 바탕화면에 "Restart Jangbogo Tray.lnk" 생성 (`-Restart` 인자 포함).
- 시작 메뉴에 동일 단축아이콘 추가.
- 출력 메시지에 사용 안내(Tip) 라인 추가.

**4. `build.gradle` 의 `packageDist` 태스크**
- `from('packaging/distribution')` 의 `include` 목록에 `'Restart-Tray.bat'` 추가 → 배포 ZIP 에 자동 포함.

**5. `사용설명서.txt` FAQ Q11 추가**
- "컴퓨터를 재부팅했더니 트레이 아이콘이 안 보여요" 질문에 3가지 해결 방법 (바탕화면 단축아이콘 / Restart-Tray.bat / 시작 메뉴) 안내.
- "서비스는 계속 동작 중이므로 데이터 수집에는 영향 없음" 명시 → 사용자 안심.

#### 검증

- PowerShell 두 스크립트(`Jangbogo-Tray.ps1`, `create-shortcuts.ps1`) AST 파스 에러 0건.
- `install.bat` 의 트레이 기동 부분(183행)은 사전에 기존 프로세스를 정리(95행)하므로 mutex 와 충돌하지 않음 — 추가 변경 불필요.

#### 메모

- 새 단축아이콘 이름은 영문 "Restart Jangbogo Tray" — 다른 단축아이콘들("Jangbogo Tray", "Jangbogo Dashboard")과 톤을 맞춤. 한글로 표기하면 일부 Windows 환경에서 단축아이콘 이름 인코딩 문제가 발생할 수 있어 영문 유지.
- 향후 주기적 watchdog (예: 5분마다 트레이 프로세스 살아있는지 체크하고 죽었으면 재시작) 까지 도입할 수도 있으나, 현재는 사용자가 인지했을 때 바로 복구할 수 있는 도구를 제공하는 선에서 멈춤. 자동 watchdog 은 좀비 프로세스 위험이 있어 신중히 설계 필요.

---

### [2026-04-23 16:00] v0.10.0 - 구매 내역 조회 UI 추가

#### 작업 개요

수집된 주문/아이템 데이터를 웹 UI 에서 직접 열람할 수 있는 `/orders` 페이지를 신규 추가. SESSION_HANDOFF 의 후속 작업 후보 #5 (수집 데이터 브라우저 조회 UI) 착수. 기존에는 파일 내보내기(`/export/orders`) 경로로만 데이터 접근이 가능했고 웹 UI 는 없었음.

#### 배경

- 초기 v0.7.0 논의 단계에서 거론되었으나, 실제 사용자 요구는 "수집 실패 진단" 이었음이 확인되어 v0.8.0 에서 `/collect-logs` 기능으로 방향 전환 → 조회 UI 는 SESSION_HANDOFF 보류 항목으로 유지.
- 이번 세션에서 2·3·4·5 번 후속 작업을 연속 진행하며 재개.
- DAO (`JbgOrderDataAccessObject.getAllOrders`, `JbgItemDataAccessObject.getItemsByOrder`) 와 서비스 레이어의 order+item 조인 패턴(`ExportService.collectOrderData`) 이 이미 존재해 조회 API 는 얇게 구현 가능했음.

#### 상세 내용

**1. 신규 API (`AdminController`)**
- `GET /api/orders?limit=N&mall=X&dateFrom=YYYYMMDD&dateTo=YYYYMMDD`
  - `JbgOrderDataAccessObject.getAllOrders(limit)` 조회 후, 각 주문에 대해 `JbgItemDataAccessObject.getItemsByOrder(seq)` 를 호출해 `items` 배열과 `item_count` 를 enrich.
  - 쇼핑몰(`mall_name`) / 기간(`date_time`) 서버 사이드 필터 지원.
- `GET /api/orders/{seq}` — 단일 주문 상세.

**2. 신규 페이지 (`/orders`)**
- `HomeController.orders()` 라우트, `activePage=orders`.
- `templates/orders.html` 신규 — `collect-logs.html` 구조 기반:
  - Bootstrap 5 테이블 + 필터 바(쇼핑몰 드롭다운, 최대 건수 선택) + 새로고침 버튼.
  - 요약 배지: 조회된 주문 수, 누적 아이템 수.
  - 상세 모달: 주문 메타데이터 + 아이템 테이블(아이템명/수량/등록시간).
  - 날짜 포매팅: `date_time` (INTEGER YYYYMMDD) → `YYYY-MM-DD` 변환, `insert_time` (millisecond epoch) → 로컬 타임스탬프.

**3. 네비게이션**
- `fragments/header.html` 에 "구매 내역" 메뉴(`bi-bag-check` 아이콘) 추가. 순서: 대시보드 / 구매 내역 / 오류 로그 / 계정 설정.

#### 검증

- `./gradlew compileJava` → BUILD SUCCESSFUL.
- `./gradlew spotlessApply` → 포맷 적용 완료.
- 기존 `/export/orders` 경로에 변화 없음 — 새 UI 는 읽기 전용으로 완전히 분리됨.

#### 메모

- 단일 주문 상세(`GET /api/orders/{seq}`) 는 현재 `getAllOrders(0)` 후 순회하는 간단 구현. 주문 건수가 수만 건 이상으로 늘어나면 `JbgOrderDataAccessObject` 에 `getOrder(seq)` 메서드를 추가해 교체 예정. 현 규모에서는 과잉 최적화로 판단.
- 페이지네이션은 현재 "최대 건수 select" (100/200/500/1000) 로만 제공. 향후 필요 시 커서 기반 무한 스크롤 또는 페이지 번호 방식 추가 가능.

---

### [2026-04-23 14:00] v0.9.1 - packaging/scripts/ 유물 폴더 제거

#### 작업 개요

v0.9.0 세션에서 후속 cleanup 태스크로 미뤄둔 `packaging/scripts/` 폴더를 정리. 구성 파일 3개 모두 jpackage 시도 실패 시절 유물이었음을 재확인하고 전체 삭제.

#### 배경

- v0.9.0 DEVLOG 메모 섹션 및 SESSION_HANDOFF 의 후속 작업 후보 #2 로 명시되어 있던 항목.
- `build.gradle`(138~163행)의 `packageDist` 태스크는 `packaging/distribution/` 과 `packaging/winsw/` 만 ZIP 에 포함 — `packaging/scripts/` 는 참조하지 않음.
- `post-install.bat` 은 `jangbogo.exe --install-complete` 를 호출하는데, 해당 플래그는 v0.9.0 에서 Java 트레이와 함께 제거됨. `jangbogo.exe` 산출물도 jpackage 시절 산물이라 현재는 생성되지 않음.
- `pre-uninstall.bat` 은 동일한 `jangbogo.exe` 프로세스를 종료하는 로직이라 마찬가지로 무효.
- `Jangbogo.bat` 은 `jangbogo-0.5.5.jar` 을 참조하는 v0.5.5 시절 스크립트. 현재 배포 ZIP 에는 `packaging/distribution/Jangbogo.bat` 이 버전 자동 감지 로직과 함께 들어감.

#### 상세 내용

1. **폴더 삭제**: `packaging/scripts/` 전체 제거 (`post-install.bat`, `pre-uninstall.bat`, `Jangbogo.bat`).
2. **.gitignore 정리**: 78행 `!packaging/scripts/*` 화이트리스트 예외 제거 (대상 디렉터리 사라짐).
3. **DISTRIBUTION_IMPLEMENTATION_SUMMARY.md 점검**: `packaging/scripts` 언급 없음 — 수정 불필요.
4. **버전 bump**: 0.9.0 → 0.9.1 (patch, 사용자 영향 제로).

#### 검증

- `packageDist` 태스크 정의 재확인: `from('packaging/distribution')`, `from('packaging/winsw')` 만 참조 → 배포 ZIP 구조 변동 없음.
- 저장소 전체에서 `packaging/scripts` 또는 `packaging\\scripts` grep → 문서(DEVLOG/SESSION_HANDOFF) 내 과거 참조 언급만 남고 실행 경로 참조 0건.

#### 메모

- 태그/릴리스 발행하지 않음 (cleanup patch 원칙).
- SESSION_HANDOFF 후속 작업 후보 #2 항목은 다음 세션 핸드오프 갱신 시 "완료" 로 정리 예정.

---

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

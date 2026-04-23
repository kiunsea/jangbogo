# CLAUDE.md - Jangbogo 프로젝트 가이드

## Release / Push 워크플로우

### 공통 원칙
- **버전 결정은 기본적으로 자동**. 미커밋 변경사항을 분석해 SemVer 규칙으로 bump 한다.
  - bug fix → **patch**
  - 기능 추가 → **minor**
  - 호환성 깨짐 변경 → **major**
- 판단이 애매한 경우(예: 기능 추가 + 소규모 breaking, 어느 bump인지 애매)에만 사용자 승인을 요청한다. 명확할 때는 자동 결정하고 결과만 보고한다.
- `build.gradle`의 `version` 값을 갱신한다.
- 문서 형식:
  - `CHANGELOG.md` — Keep a Changelog 형식. `## [x.y.z] - YYYY-MM-DD` 헤더 + `### Added / Changed / Fixed / Removed` 섹션.
  - `DEVLOG.md` — `### [YYYY-MM-DD HH:MM] 제목` + 작업 개요 / 배경 / 상세 내용.

### Push 워크플로우 (경량, 일상 작업용)
1. **버전 결정** — 변경사항 분석 후 자동 bump (애매할 때만 승인 요청)
2. **문서 업데이트** — `CHANGELOG.md`, `DEVLOG.md` 에 각 형식으로 항목 추가
3. **build.gradle 버전 갱신**
4. **Commit & Push** — main 브랜치
5. **GitHub Actions CI 결과 확인** — `gh run list` / `gh run watch`
6. **실패 시** — 원인 분석 → 수정 → 재커밋/재push → CI 재확인 (success까지 반복)
7. CI success 확인으로 **종료** (태그/릴리스 없음)

### Release 워크플로우 (배포용)
Push 워크플로우의 1~7단계를 모두 수행한 뒤, 이어서:

8. **릴리스 노트 작성** — `release/RELEASE_NOTES_v<버전>.md` 생성
9. **태그 생성 & push** — `git tag v<버전> && git push origin v<버전>`
10. **release.yml 워크플로우 자동 실행 확인** — 빌드 → `Jangbogo-v<버전>.zip` 생성 → GitHub Release 발행까지 모니터링
11. **실패 시** — 원인 분석 → 수정 → 필요 시 태그 재생성 → 재시도

### 참고
- `release.yml`은 `v*` 태그 push 시에만 트리거된다. 릴리스 노트 파일이 없으면 6단계에서 실패하므로 **태그 push 전에 반드시 커밋**되어 있어야 한다.
- CI 확인은 `gh run watch <run-id>` 또는 `gh run list --limit 1` 후 conclusion 필드 확인.

---

## 프로젝트 개요
- **JANGBOGO (장보고)**: 온라인 쇼핑몰 구매내역 자동 수집/관리 프로그램
- **기술 스택**: Java 21, Spring Boot 3.5.6, Gradle, SQLite, Thymeleaf, Bootstrap 5, Selenium WebDriver, Log4j2
- **라이선스**: AGPL-3.0

## 빌드 및 실행
```bash
# 개발 모드 실행
bat\test_run.bat
# 또는: ./gradlew clean bootRun

# 배포 패키지 빌드
bat\build_package.bat
# 또는: ./gradlew clean bootJar createJre packageDist --no-daemon

# 클린 빌드
bat\clean_build.bat

# 코드 포맷 적용
./gradlew spotlessApply

# 컴파일 확인
./gradlew compileJava
```

## 프로젝트 구조
```
src/main/java/com/jiniebox/jangbogo/
├─ boot/       # 시작 태스크 (StartupTasks)
├─ ctrl/       # Controller - REST API (AdminController, HomeController)
├─ dao/        # DAO - SQLite 접근 (LocalDBConnection 기반)
├─ dev/        # 개발 유틸리티
├─ dto/        # DTO (JangbogoConfig, MallAccount 등)
├─ svc/        # Service - 비즈니스 로직
│  ├─ ifc/     # 인터페이스 (MallSession, PurchasedCollector, ReceiptCollector)
│  ├─ mall/    # 쇼핑몰별 크롤링 (Ssg, Oasis, Coupang, Emart, Hanaro)
│  └─ util/    # WebDriverManager
├─ sys/        # 시스템 (AuthInterceptor, WebMvcConfig)
└─ util/       # 유틸리티 (FtpUploadUtil, PasswordEncryptor, RSA 등)
```

## 핵심 파일 및 패턴

### DAO 패턴
- `LocalDBConnection` 직접 사용 (Spring JdbcTemplate 아님)
- `conn.executeQuery(query)` - SELECT 조회
- `conn.txPstmtExecuteUpdate(query, params...)` - PreparedStatement INSERT/UPDATE
- `conn.txOpen()` / `conn.txCommit()` / `conn.txRollBack()` - 트랜잭션
- `LocalDBConnection`에 `getConnection()` 메서드 없음 (private Connection)
- 모든 DAO는 `CommonDataAccessObject` 상속

### 뷰 템플릿
- Thymeleaf Layout Dialect 사용: `layout.html` → 각 페이지가 `layout:decorate` + `layout:fragment="content"`
- `header.html`: nav (대시보드, 오류 로그, 계정 설정), `activePage` 변수로 active 표시
- `index.html`: 대시보드 (쇼핑 데이터 수집, 쇼핑몰 목록, 구매정보 저장 옵션, 실행 결과 요약)
- `collect-logs.html`: 수집 실행 오류 로그 조회 페이지
- `signin.html`, `profile.html`

### 실행 모드 (JangbogoLauncher)
- `--service`: 서비스 모드 (WinSW가 기동, 브라우저 자동 실행 없음)
- 인자 없음: 일반 개발 모드 (브라우저 자동 실행)
- 배포 환경의 트레이 UI는 PowerShell `Jangbogo-Tray.ps1` 이 담당 (Java 트레이 없음, v0.9.0 에서 제거됨)

### 코드 포맷
- Spotless + Google Java Format 1.17.0
- `./gradlew spotlessApply` 로 포맷 적용

### 외부 의존
- `settings.gradle`에서 `D:/GIT/doribox` 로컬 라이브러리 선택적 참조

## DB 스키마 (SQLite)
- `jbg_item` - 구매 아이템
- `jbg_mall` - 쇼핑몰 계정 정보 (암호화)
- `jbg_order` - 구매 주문
- `jbg_export_config` - 파일 내보내기 설정 (단일 레코드)
- `jbg_collect_log` - 수집 실행 로그 (성공/실패, 오류 메시지)

## API 엔드포인트 주요 목록
- `POST /api/login`, `POST /api/logout`, `GET /api/session-check`
- `GET /malls/getinfo` - 쇼핑몰 정보 + 내보내기 설정
- `POST /mall/connect` - 쇼핑몰 계정 연결
- `POST /malls/auto-collect` - 자동수집 실행
- `POST /malls/auto-collect/flags` - 자동수집 설정 저장
- `POST /export/orders`, `POST /export/config` - 내보내기
- `GET /api/collect-logs/summary` - 수집 실행 요약 (전체/성공/실패)
- `GET /api/collect-logs/failures` - 실패 로그 목록
- `GET /api/collect-logs` - 전체 로그 목록

## 버전 히스토리 요약 (v0.7.0 이후)

### v0.8.1 (2026-04-22) — docs patch, 태그 없음
문서 정합성 patch. 기능/API/스키마 변경 없음.
- 루트 README: v0.7.0 원스톱 설치 + v0.8.0 수집 실패 상세 진단 기능 요약 반영
- DEPLOYMENT_GUIDE 최상단에 "🚀 원스톱 설치" 섹션 추가
- 문서 전반의 구 버전 참조(0.5.x/0.6.x) → 0.8.1 일괄 정정
- `packaging/winsw/jangbogo-service.xml` JAR 파일명 동기화

### v0.8.0 (2026-04-18) — 수집 실패 상세 진단
Selenium 크롤링 실패 시 단계명·URL·페이지 타이틀·셀렉터·스크린샷 자동 기록.
- 신규 클래스:
  - `svc/CollectException.java` — 실패 컨텍스트 포함 도메인 예외
  - `svc/util/CollectStep.java` — Selenium 호출 감싸 예외 래핑
  - `svc/util/ScreenshotUtil.java` — PNG 저장 + 30일 보관 정리
- DB: `jbg_collect_log` 테이블에 5개 컬럼(step_name/current_url/page_title/target_selector/screenshot_path) 추가 + `StartupTasks.migrateCollectLogSchema()` 자동 ALTER
- DAO: `JbgCollectLogDataAccessObject.addLog(...)` 확장 시그니처 + `getLog(seq)` 단일 조회
- 5개 쇼핑몰 크롤러(Ssg/Oasis/Emart/Hanaro) `getItems()` swallow catch 제거 → `CollectStep.call` 로 교체
- `MallOrderUpdaterRunner`/`MallSchedulerService` catch 블록에서 `CollectException` 언래핑 후 컨텍스트 추출
- 신규 API: `GET /api/collect-logs/{seq}`, `GET /api/collect-logs/{seq}/screenshot` (경로 탐색 공격 차단)
- `collect-logs.html` 상세 모달 UI + 쇼핑몰/단계 필터 드롭다운 + 스크린샷 확대 모달

### v0.7.0 (2026-04-17) — 수집 오류 로그 + Windows 서비스 관리 + muse-agent 패턴 이식
- `jbg_collect_log` 테이블 신규 (수집 실행 이력)
- `JbgCollectLogDataAccessObject`, AdminController 3개 엔드포인트
- `collect-logs.html` 페이지, 대시보드 "실행 결과 요약" 카드, 네비 메뉴
- Windows 서비스 스크립트 세트 (muse-agent 패턴 풀 이식):
  - `install.bat` — 관리자 권한 + WinSW 등록 + 포트 체크 + RUNNING 폴링 + 로그 tail + 대시보드 ready polling + 단축아이콘 + 트레이 기동
  - `uninstall.bat` — 100% ASCII 영문
  - `Jangbogo.bat` — 포트 충돌 프롬프트, JRE 자동 다운로드 fallback
  - `Jangbogo-Tray.ps1`, `create-shortcuts.ps1`, `download-jre.ps1`
- `release.yml` 에 `permissions: contents: write` 추가

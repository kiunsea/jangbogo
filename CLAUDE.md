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
├─ sys/        # 시스템 (AuthInterceptor, TrayApplication, WebMvcConfig)
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
- `--service`: 서비스 모드 (브라우저/트레이 없음)
- `--tray`: 트레이 모드 (브라우저 + 트레이 아이콘)
- `--install-complete`: 설치 완료 모드 (트레이 + 브라우저, Spring Boot 미시작)
- 인자 없음: 일반 개발 모드

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

## 최근 작업 내역 (v0.6.1 이후 진행 중)

### 수집 실행 오류 로그 기능 (구현 완료, 미커밋)
1. **DB**: `jbg_collect_log` 테이블 추가 (`schema.sql`)
2. **DAO**: `JbgCollectLogDataAccessObject` 신규 생성 - addLog, getAllLogs, getFailLogs, getSummary
3. **서비스 계층 로그 기록**:
   - `MallOrderUpdaterRunner.run()` - 수집 완료/실패 시 `saveCollectLog()` 호출
   - `MallSchedulerService.runCollectForMall()` - 검증 실패/예외 시 `saveFailLog()` 호출
4. **API**: AdminController에 3개 엔드포인트 추가 (summary, failures, 전체)
5. **UI**:
   - `index.html` - 대시보드 블럭 경계 강화 (`.dashboard-section` CSS), "실행 결과 요약" 카드 추가
   - `collect-logs.html` - 오류 로그 조회 페이지 신규 생성
   - `header.html` - "오류 로그" 네비게이션 메뉴 추가
6. **HomeController**: `/collect-logs` 라우트 추가

### Windows 서비스 관리 (구현 완료, 미커밋)
1. **install.bat / uninstall.bat**: `packaging/distribution/` 에 신규 생성
   - 관리자 권한 체크, WinSW 서비스 설치/시작/제거
   - 설치 후 `--install-complete` 모드로 트레이 앱 실행
2. **TrayApplication 메뉴 변경**: 대시보드, 서비스 재시작(restart), 서비스 종료(stop), 종료
   - WinSW 경로: `winsw/` → `service/` 로 변경
3. **jangbogo-service.xml**: JAR 버전 0.5.5 → 0.6.1, `--service` 인자 추가
4. **build.gradle**: packageDist에 install.bat, uninstall.bat 포함

### 빌드 스크립트 수정 (구현 완료, 미커밋)
- `bat/build_package.bat`: `--no-daemon` 옵션 추가 (Gradle 데몬 통신 오류 방지)

### 변경된 파일 목록
**수정:**
- `src/main/resources/schema.sql`
- `src/main/resources/templates/index.html`
- `src/main/resources/templates/fragments/header.html`
- `src/main/java/.../ctrl/AdminController.java`
- `src/main/java/.../ctrl/HomeController.java`
- `src/main/java/.../svc/MallOrderUpdaterRunner.java`
- `src/main/java/.../svc/MallSchedulerService.java`
- `src/main/java/.../sys/TrayApplication.java`
- `packaging/winsw/jangbogo-service.xml`
- `build.gradle`
- `bat/build_package.bat`

**신규:**
- `src/main/java/.../dao/JbgCollectLogDataAccessObject.java`
- `src/main/resources/templates/collect-logs.html`
- `packaging/distribution/install.bat`
- `packaging/distribution/uninstall.bat`

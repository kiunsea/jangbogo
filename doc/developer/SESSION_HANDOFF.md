# Jangbogo — 다음 세션 핸드오프

> 이 문서는 **직전 세션의 상태를 다음 세션 Claude가 빠르게 파악**하기 위한 스냅샷입니다.
> 마지막 갱신: **2026-04-23 (v0.9.0 push 직후)**

---

## 📸 현재 상태 스냅샷

| 항목 | 값 |
|---|---|
| **브랜치** | `main` |
| **빌드 버전** | `0.9.0` (build.gradle) |
| **최근 태그** | `v0.8.0` (배포된 최종 릴리즈) |
| **v0.8.1 / v0.9.0 태그/릴리즈** | ❌ **없음** (둘 다 main 에만 push — docs patch / 죽은코드 제거) |
| **uncommitted** | 없음 (clean) |

**빠른 검증 명령**:
```bash
git status                           # clean 이어야 함
git log --oneline -5                 # v0.9.0 커밋이 HEAD
./gradlew compileJava                # 컴파일 통과 확인
```

---

## 🗂️ 작업 라인 구분

### ✅ 완료(committed, pushed, 필요 시 릴리즈됨)
- v0.7.0 릴리즈 — 수집 오류 로그 + Windows 서비스 관리 세트 + muse-agent 패턴
- v0.8.0 릴리즈 — 수집 실패 상세 진단 (CollectException/CollectStep/ScreenshotUtil, schema migration, UI 모달)
- v0.8.1 docs patch — 문서 전반의 버전 정합성 + v0.7.0/v0.8.0 기능 반영 **(태그는 미생성)**
- v0.9.0 minor — Java TrayApplication + `--tray` / `--install-complete` 플래그 제거(실제 배포 경로 미사용) + DEPLOYMENT_GUIDE 3부 구성(원스톱/고급/문제해결) **(태그는 미생성, main 에만 push)**

### ⏳ 후속 작업 후보 (우선순위순)

#### 1. 수집 데이터 브라우저 조회 UI (세션 진행 중)
- `jbg_order` / `jbg_item` 목록 · 상세 페이지. 내보내기 경로 외에 웹 UI 없음.
- 이 세션에서 바로 이어서 착수하기로 한 작업.

#### 2. `packaging/scripts/` 폴더 정리
- `post-install.bat` / `pre-uninstall.bat` 은 jpackage 시도 실패(→ ZIP 배포로 전환) 시절 유물.
- `Jangbogo.bat` 은 v0.5.5 JAR 을 참조하는 옛 버전 (배포 ZIP 에는 `packaging/distribution/Jangbogo.bat` 이 들어감).
- `build.gradle` 의 `packageDist` 어디에서도 참조되지 않음 — 전체 폴더 삭제 안전.
- 별도 cleanup 태스크로 분리됨.

#### 3. Eclipse 로컬 설정 오염 재발 방지 (모니터링만)
- `.gitignore` 는 `.project` / `.settings/` / `.classpath` / `bin/` 를 정상 무시 중.
- `.settings/org.eclipse.buildship.core.prefs` 의 `arguments=` 라인이 Cursor 의 Red Hat Java 확장에 의해 절대 경로로 오염되는 이슈가 과거 관찰됨 → 현재 세션 확인 시점엔 비어 있음 (OK).

#### 4. DEPLOYMENT_GUIDE 후속 정제 (선택)
- v0.9.0 에서 "🚀 표준 / 🔧 고급 / 🚨 문제해결" 3부로 재구성 완료.
- 세부 수동 절차 중 일부 (예: 관리자 계정 설정의 방법 3) 는 여전히 구체적 tune 필요 — 사용자가 활용하게 되면 피드백 받아 다듬기.

---

## 📂 핵심 파일 위치 (v0.8.x 기준)

### 수집 실패 진단 레이어 (v0.8.0 도입)
```
src/main/java/com/jiniebox/jangbogo/
├─ svc/
│  ├─ CollectException.java            # 실패 컨텍스트 도메인 예외
│  ├─ MallOrderUpdaterRunner.java      # catch 블록에서 unwrap
│  ├─ MallSchedulerService.java        # 동일 패턴
│  ├─ mall/                            # Ssg / Oasis / Emart / Hanaro (CollectStep 적용됨)
│  └─ util/
│     ├─ CollectStep.java              # Selenium 래퍼 + 자동 스크린샷
│     └─ ScreenshotUtil.java           # PNG 저장 + 30일 보관
├─ boot/StartupTasks.java              # migrateCollectLogSchema() — 기존 DB에 ALTER TABLE
├─ dao/JbgCollectLogDataAccessObject.java  # addLog(...13 args) + getLog(seq)
└─ ctrl/AdminController.java           # GET /api/collect-logs/{seq} / screenshot
src/main/resources/
├─ schema.sql                          # jbg_collect_log 확장 컬럼
└─ templates/collect-logs.html         # 모달 상세 뷰
```

### 배포 세트 (v0.7.0 도입)
```
packaging/
├─ winsw/jangbogo-service.xml          # JAR 0.8.1 로 동기화됨 (install.bat 이 런타임 재작성도 함)
└─ distribution/
   ├─ install.bat                      # 관리자 권한 필수, muse-agent 패턴
   ├─ uninstall.bat                    # 100% ASCII
   ├─ Jangbogo.bat                     # 단독 실행, 포트 충돌 프롬프트
   ├─ Jangbogo-Tray.ps1                # PowerShell 트레이
   ├─ create-shortcuts.ps1             # 바탕화면/시작메뉴
   ├─ download-jre.ps1                 # Temurin JRE 21 자동 다운로드
   └─ 설치가이드.txt / 사용설명서.txt / 고급가이드.txt
```

### 워크플로우 정의
- `CLAUDE.md` 상단 "Release / Push 워크플로우" 섹션 — **반드시 준수**
  - 버전 자동 판단 (bug fix = patch, 기능 추가 = minor, 호환성 깨짐 = major)
  - CHANGELOG (Keep a Changelog 형식) + DEVLOG (`### [YYYY-MM-DD HH:MM]` 형식)
  - Push 워크플로우 (1~7): 태그/릴리즈 없이 CI success 까지
  - Release 워크플로우 (8~11): 릴리즈 노트 → 태그 → release.yml 모니터링

---

## 🔧 자주 쓰는 명령

```bash
# 개발 모드 실행
bat\test_run.bat

# 배포 패키지 빌드 (clean + bootJar + createJre + packageDist)
bat\build_package.bat

# 코드 포맷
./gradlew spotlessApply

# CI 상태 확인
gh run list --limit 3
gh run watch <run-id> --exit-status

# 릴리즈 확인
gh release view v0.8.0
```

---

## 🚨 알려진 함정

1. **Eclipse import 시 `.settings/org.eclipse.buildship.core.prefs` 의 `arguments=` 라인 오염** — Cursor/VS Code Red Hat Java 확장이 절대 경로 init-script 를 써넣는 경우가 있음. 해당 라인 비우면 Eclipse Buildship sync 정상화.

2. **`release.yml` 의 `contents: write` 권한** — v0.7.0 에서 추가됨. 제거하면 태그 push 시 403.

3. **WinSW `<arguments>` 의 JAR 파일명** — install.bat 이 PowerShell 로 런타임 재작성하지만, 소스 파일(`packaging/winsw/jangbogo-service.xml`)도 버전 bump 시 맞춰서 동기화하는 것이 정합성 상 권장.

4. **`jbg_collect_log` 스키마 마이그레이션** — `StartupTasks.migrateCollectLogSchema()` 가 `PRAGMA table_info` 로 체크 후 `ALTER TABLE ADD COLUMN` 수행. 기존 DB는 첫 실행 시 자동으로 5개 컬럼 추가됨.

5. **스크린샷 보관 위치** — `logs/screenshots/yyyyMMdd/{mall}-{timestamp}.png`. StartupTasks 가 시작 시 30일 이전 폴더 정리.

---

## 🎯 다음 세션 시작 시 추천 절차

1. 이 문서 맨 위 "📸 현재 상태 스냅샷" 의 검증 명령 실행으로 상태 확인
2. `git log --oneline -10` 으로 최근 커밋 맥락 파악
3. `CLAUDE.md` 상단 "Release / Push 워크플로우" 섹션 재확인 (정책 변경 없는 한 동일)
4. 사용자 요청 받은 뒤, 이 문서의 "⏳ 후속 작업 후보" 가 관련 있으면 참고

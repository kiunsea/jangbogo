# Jangbogo v0.7.0

**출시일**: 2026-04-18

이번 릴리스는 **자동 수집 실행 오류 로그 기능**, **Windows 서비스 원스톱 설치/제거**, 그리고 **muse-agent 패턴의 배포 패키지 전면 재정렬**을 포함합니다. 기존 배포 스크립트의 한글 인코딩 문제를 근본적으로 해결했고, 설치/운영 신뢰성이 크게 개선되었습니다.

## 주요 변경사항

### ✨ 새로운 기능

- **수집 실행 오류 로그**: 자동 수집의 성공/실패 내역과 오류 메시지를 DB에 기록하고, 대시보드 "실행 결과 요약" 카드와 별도 **오류 로그 페이지**(`/collect-logs`)에서 조회할 수 있습니다.
- **Windows 서비스 통합 스크립트**: `install.bat` 한 번으로 관리자 권한 확인 → WinSW 서비스 설치/시작 → 바탕화면·시작메뉴 단축아이콘 생성 → 트레이 기동 → 대시보드 자동 오픈까지 원스톱 처리됩니다. `uninstall.bat`으로 깔끔한 제거도 가능합니다.
- **PowerShell 기반 시스템 트레이** (`Jangbogo-Tray.ps1`): 대시보드 열기, 서비스 상태/시작/중지/재시작 메뉴를 제공합니다. Spring Boot 미기동 상태에서도 독립 실행됩니다.
- **JRE 자동 다운로드** (`download-jre.ps1`): 번들 JRE가 없으면 Eclipse Temurin JRE 21을 자동으로 받아 배치합니다.
- **단축아이콘 자동 생성** (`create-shortcuts.ps1`): 바탕화면에 `Jangbogo Tray.lnk`, `Jangbogo Dashboard.url`, 시작 메뉴에 `Jangbogo Tray.lnk`를 생성합니다.

### 🔧 개선사항

- **install.bat 견고성 강화**: 서비스 `RUNNING` 상태 폴링(최대 20초), 실패 시 `service\logs`·`logs\jangbogo.log` 자동 tail, 대시보드 HTTP ready polling(최대 45초), 포트 충돌 사전 감지, JAR 파일명에 맞춘 WinSW XML 자동 동기화.
- **Jangbogo.bat 강화**: JAR 자동 탐지, 시스템 Java 버전 검증(≥21), JRE 자동 다운로드 fallback, 포트 8282 점유 시 다른 포트 입력 프롬프트(1024–65535 검증).
- **트레이 메뉴 개편**: 대시보드 / 서비스 재시작 / 서비스 종료 / 종료 메뉴로 정비.
- **packageDist 태스크 확장**: 새로 추가된 PowerShell 스크립트 3종이 배포 ZIP에 포함됩니다.
- **빌드 스크립트 안정화**: `build_package.bat`에 `--no-daemon` 옵션 추가 (Gradle 데몬 통신 오류 방지).

### 🐛 버그 수정

- **배치 파일 인코딩 깨짐 해결**: `install.bat`/`uninstall.bat`이 UTF-8로 저장되어 cmd.exe의 CP949 파싱과 충돌해 `'cho'은(는) 내부 또는 외부 명령...` 같은 오류를 발생시키던 문제를, 배치 파일 텍스트를 영문 기반으로 재작성해 원천 제거했습니다.

### 📚 문서

- **CLAUDE.md 프로젝트 가이드** 신규 추가: Release/Push 워크플로우, DAO 패턴, 실행 모드, DB 스키마, API 엔드포인트 목록 명문화.

## 설치 방법

1. `Jangbogo-v0.7.0.zip`을 다운로드해 원하는 위치(관리자 권한 접근 가능한 경로)에 압축 해제합니다.
2. **관리자 권한으로** `install.bat`을 실행합니다.
3. 설치가 완료되면 브라우저에서 자동으로 대시보드(http://localhost:8282)가 열립니다.
4. 바탕화면의 `Jangbogo Tray`, `Jangbogo Dashboard` 단축아이콘으로 빠르게 접근할 수 있습니다.

## 제거 방법

관리자 권한으로 `uninstall.bat`을 실행합니다. 서비스와 단축아이콘이 제거되며, 데이터(`db`, `logs`, `exports`)는 보존됩니다.

## 호환성

- Windows 10/11 x64
- 번들 JRE 21 포함 (시스템 Java 불필요)
- 시스템 Java 21 이상 설치 시 번들 JRE를 건너뛰고 시스템 Java 사용 가능

## 업그레이드 안내

기존 v0.6.x 사용자는 다음 순서로 업그레이드하세요.

1. 기존 설치 폴더에서 `uninstall.bat` 실행 (관리자 권한)
2. `db/`, `exports/` 폴더만 별도 보관 (데이터 보존)
3. v0.7.0 zip을 새 폴더에 압축 해제하고 보관한 `db/`, `exports/`를 복사
4. 새 폴더에서 `install.bat` 실행 (관리자 권한)

## 기여자

- Kiunsea (@kiunsea)
- Claude (Anthropic)

---

**전체 변경사항**: [CHANGELOG.md](../CHANGELOG.md)
**개발 로그**: [DEVLOG.md](../DEVLOG.md)

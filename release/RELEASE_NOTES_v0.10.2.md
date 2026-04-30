# Jangbogo v0.10.2

**출시일**: 2026-05-01

이번 릴리스는 v0.8.0 이후 누적된 다섯 차례의 main 푸시(v0.9.0 ~ v0.10.2)를 정식 배포로 묶은 버전입니다. 핵심 키워드는 **트레이/배포 안정화**와 **구매 내역 조회 UI 신설**입니다. 데이터베이스 스키마는 v0.8.0 과 동일하므로 기존 사용자는 제자리 업그레이드가 가능합니다.

## 주요 변경사항

### ✨ 새로운 기능

- **구매 내역 조회 페이지 (`/orders`)** *(v0.10.0)* — 수집된 주문(`jbg_order`)과 아이템(`jbg_item`)을 웹 UI 에서 직접 열람할 수 있습니다. 기존에는 파일 내보내기(export) 경로로만 접근 가능했던 데이터를 대시보드 네비에서 바로 확인할 수 있습니다.
  - 테이블: 주문 seq / 구매일자 / 쇼핑몰 / 주문번호 / 아이템 수 / 등록시간 / 상세 모달.
  - 필터: 쇼핑몰 드롭다운(자동 채움) + 최대 건수(100/200/500/1000).
  - 상세 모달: 주문 메타데이터 + 아이템 목록(이름/수량/등록시간) 테이블.
- **트레이 아이콘 재시작 도구** *(v0.10.1)* — Windows 재부팅 후 explorer 의 NotifyIcon 새로고침 누락으로 트레이 아이콘이 보이지 않는 상황을 위해 별도 진입점을 추가했습니다.
  - 바탕화면/시작 메뉴에 "Restart Jangbogo Tray" 단축아이콘 자동 생성.
  - `Restart-Tray.bat` 신규 (관리자 권한 불필요).
  - `Jangbogo-Tray.ps1` 에 글로벌 Mutex 단일 인스턴스 보호 + `-Restart` 인자 지원.
- **WinSW 자동 다운로드 fallback** *(v0.10.2)* — 빌드 환경에 WinSW 가 없어도 사용자가 `install.bat` 한 번에 모든 것이 자동 처리되도록 개선했습니다.
  - 신규 `download-winsw.ps1` 이 WinSW v2.12.0 을 GitHub Releases 에서 받아오며, MOTW 차단까지 자동 해제합니다.
  - `download-jre.ps1` 과 동일한 fallback 패턴.

### 🔧 내부 개선

- **트레이 앱 이중화 제거** *(v0.9.0)* — Java 기반 `TrayApplication` 을 완전히 삭제하고 PowerShell 트레이(`Jangbogo-Tray.ps1`) 로 일원화. `--tray` / `--install-complete` 실행 플래그 정리. `JangbogoLauncher` 의 `ExecutionMode` 를 `SERVICE` / `NORMAL` 두 값으로 단순화.
- **`packaging/scripts/` 유물 폴더 제거** *(v0.9.1)* — jpackage 시도 실패 시절 (→ Custom JRE + ZIP 배포 전환) 의 `post-install.bat` / `pre-uninstall.bat` / 옛 `Jangbogo.bat` (v0.5.5 JAR 참조) 삭제. `packageDist` 어디에서도 참조되지 않던 죽은 파일.
- **DEPLOYMENT_GUIDE 3부 구성 재편** *(v0.9.0)* — "🚀 표준 (원스톱 설치) / 🔧 고급·수동 / 🚨 문제 해결" 으로 목차 재정렬. 각 수동 섹션 상단에 "`install.bat` 이 이 단계를 자동화합니다" 안내 블록을 추가해 고급 참고자료 성격을 명확화.
- **사용설명서 FAQ 확장** *(v0.10.1)* — Q11 "재부팅 후 트레이 아이콘이 안 보일 때" 항목 추가. 3가지 복구 방법 안내 + 서비스(데이터 수집) 자체에는 영향 없음 명시.

### 🐛 버그/안정화

- **첫 설치 시 WinSW 누락으로 install.bat 이 멈추던 문제** *(v0.10.2)* — `.gitignore` 로 git 미추적인 `packaging/winsw/jangbogo-service.exe` 가 빌드 환경에 없으면 ZIP 에서 빠지고 사용자가 직접 받아야 했습니다. 이제 `install.bat` 이 자동 처리합니다.
- **재부팅 후 트레이 좀비 인스턴스 누적 가능성** *(v0.10.1)* — 사용자가 단축아이콘을 반복 클릭해도 글로벌 Mutex 로 중복 실행이 차단됩니다.

## 새 API / 페이지 (v0.10.0)

| 경로 | 설명 |
|---|---|
| `GET /orders` | 구매 내역 조회 페이지 |
| `GET /api/orders?limit=N&mall=X&dateFrom=YYYYMMDD&dateTo=YYYYMMDD` | 주문 목록 + 각 주문의 `items` 배열 enrich |
| `GET /api/orders/{seq}` | 단일 주문 + 아이템 상세 |

## 호환성 및 업그레이드

- **Windows 10/11 x64, JRE 21 번들 포함** (없으면 자동 다운로드).
- **WinSW** 도 누락 시 자동 다운로드 (v2.12.0, .NET Framework 4.6.1 빌드).
- **DB 스키마 변경 없음** — v0.8.0 의 마이그레이션 이후 동일. 기존 사용자는 제자리 업그레이드.
- **공개 API 호환** — 기존 `/api/collect-logs*` / `/export/*` 엔드포인트 동작 동일. `/api/orders*` 는 신규 추가.
- **제거된 API/플래그** *(v0.9.0)* — `--tray` / `--install-complete` 실행 플래그 (외부 스크립트에서 호출하던 적 없음, 영향 0).

## 설치 방법

1. `Jangbogo-v0.10.2.zip` 다운로드 후 원하는 위치에 압축 해제.
2. **관리자 권한으로** `install.bat` 실행 (WinSW / JRE 누락 시 자동 다운로드).
3. 자동으로 대시보드(http://localhost:8282)가 열리며, 바탕화면에 단축아이콘이 생성됩니다.
4. (재부팅 후 트레이가 안 보이면) 바탕화면 "Restart Jangbogo Tray" 더블클릭.

## 업그레이드 시 확인할 점

- 새로 생긴 **"구매 내역" 메뉴**(`/orders`) 에서 누적된 주문/아이템이 정상 표시되는지 확인.
- 트레이 아이콘이 정상 등장하는지 확인. 안 보이면 "Restart Jangbogo Tray" 단축아이콘 사용.
- 재설치라면 기존 서비스가 자동 정리되는지(설치 중 cleanup 프롬프트) 확인.

## 기여자

- Kiunsea (@kiunsea)
- Claude (Anthropic)

---

**전체 변경사항**: [CHANGELOG.md](../CHANGELOG.md)
**개발 로그**: [DEVLOG.md](../DEVLOG.md)

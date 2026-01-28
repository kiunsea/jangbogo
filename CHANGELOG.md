# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.6.0] - 2026-01-28

### Added

- **하나로마트(Hanaro) 쇼핑몰 완전 통합**: nonghyupmall.com의 마트구매영수증 수집 기능을 완성했습니다. 오프라인 하나로마트 매장에서의 구매 내역을 자동으로 수집할 수 있습니다.
- **HanaroTest 테스트 클래스**: step-by-step 크롤링 개발 및 검증을 위한 테스트 클래스를 추가했습니다.
- **관리 화면 HANARO 카드**: 관리 화면에 하나로마트 계정 연결 및 자동 수집 설정 UI를 추가했습니다.

### Changed

- **Serial 형식 개선**: 하나로마트 영수증의 serial을 `구매일자_구매금액` 조합으로 생성하여 동일 날짜 복수 구매도 구분할 수 있도록 개선했습니다.
- **중복 수집 방지 강화**: 크롤링 단계에서 DB의 serial 값을 확인하여 이미 수집된 영수증은 건너뛰도록 개선했습니다. 불필요한 크롤링을 방지하여 효율성이 향상되었습니다.
- **여러 영수증 순회 지원**: 하나로마트 영수증 목록에 여러 건이 있을 경우 모든 영수증을 순회하며 수집합니다.

### Fixed

- **ExportService mall_id 누락 수정**: 파일 저장 시 하나로마트의 mall_id가 null로 출력되던 문제를 수정했습니다. `getMallIdFromSeq()` 메서드에 case 3 ("hanaro") 매핑을 추가했습니다.

---

## [0.5.5] - 2025-12-22

### Fixed

- **이마트 구매내역 수집 오류 수정**: 영수증 목록이 비어있을 때 `IndexOutOfBoundsException`이 발생하던 문제를 해결했습니다. 빈 리스트 체크를 추가하여 안전하게 처리하도록 개선했습니다.

---

## [0.5.4] - 2025-12-21

### Changed

- **데이터베이스 트랜잭션 개선**: 구매내역 수집 시 주문과 아이템을 하나의 트랜잭션으로 처리하도록 개선했습니다. 주문 저장 후 아이템 저장 실패 시 전체 롤백되어 데이터 일관성이 보장됩니다.
- **SQL Injection 방지**: 주문 및 아이템 저장 시 PreparedStatement를 사용하여 SQL Injection 공격을 방지했습니다. 문자열 연결 방식에서 파라미터 바인딩 방식으로 변경되었습니다.

### Fixed

- 주문은 저장되었으나 아이템이 없는 불일치 상태가 발생할 수 있던 문제를 해결했습니다.
- 특수문자가 포함된 주문번호나 상품명으로 인한 SQL 오류를 방지했습니다.

---

## [0.5.3] - 2025-12-09

### Changed

- UI 개선: '구매내역 수집시 옵션' 블록 내의 항목들에 depth 표시를 추가하여 계층 구조를 명확하게 표시했습니다. 하위 항목에는 작은 원형 아이콘을 사용하여 시각적 구분을 강화했습니다.
- UI 개선: 'FTP 업로드 시 파일 암호화'와 'Public Key (암호화용)' 항목을 하나의 블록으로 묶고 연한 아이보리 배경색(`#f5f5f0`)을 적용하여 관련 설정을 그룹화했습니다.
- UI 개선: 쇼핑몰 목록의 각 쇼핑몰에 연한 파스텔 톤 배경색을 적용하여 시각적 구분을 개선했습니다. SSG(신세계)는 연한 파스텔 블루(`#e8f4f8`), OASIS(오아시스)는 연한 파스텔 라벤더(`#f4e8f8`)로 표시됩니다.

---

## [0.5.2] - 2025-11-15

### Added

- `계정 설정` 화면에서 관리자 아이디·비밀번호를 직접 수정할 수 있는 UI와 API를 제공했습니다. 저장 성공/실패 여부를 즉시 안내하고, `admin.properties`와 애플리케이션 세션에 동시에 반영합니다.

### Changed

- `profile.html` 스크립트를 레이아웃 fragment 내부에 포함시켜 템플릿 확장 시 모든 로직이 누락 없이 내려가도록 정리했습니다.
- `test_run.bat` 실행 시 Gradle Clean, 캐시 디렉터리 삭제, 템플릿 캐시 비활성화를 자동으로 수행해 개발 중 최신 템플릿/정적 리소스가 항상 로드되도록 했습니다.

### Fixed

- 계정 정보 저장 버튼이 폼 기본 제출만 트리거하던 문제를 수정하고, JSON Payload가 `/api/admin/profile`로 안전하게 전송되도록 했습니다.

---

## [0.5.1] - 2025-11-14

### Fixed

- FTP 자동 업로드 및 “FTP로 저장” 기능이 서로 다른 JSON 포맷을 사용해 jiniebox에서 복호화 후 파싱이 실패하던 문제를 해결했습니다. 이제 두 경로 모두 jiniebox `JangbogoDataParser`가 기대하는 배열 구조(JSON array)를 생성합니다.
- 기본 내보내기 경로(`C:\Users\<사용자>\Documents\jangbogo_exports`)가 존재하지 않을 경우 서버 재시작 후 Public Key 입력 필드가 비어 보이거나 저장 실패하던 문제를 방지하기 위해 폴더를 자동으로 생성합니다.

### Changed

- FTP 자동 업로드 시 생성되는 임시 JSON 파일과 선택적 암호화 결과 파일을 업로드 후 즉시 정리하여 디스크 점유를 줄였습니다.

---

## [0.5.0] - 2025-11-04

### Added

#### 배포 및 설치
- Custom JRE 번들링 시스템 (jlink 사용)
- Java 설치 불필요한 배포 패키지 생성 (ZIP)
- Windows 서비스 등록 지원 (WinSW)
- 브라우저 자동 실행 기능
- 사용설명서 및 사용자 매뉴얼 (txt 형식)

#### 구매내역 수집
- 신규 구매내역만 파일로 저장하는 기능
- "구매내역 수집시 함께 저장" 자동 저장 옵션
- 파일 저장 설정 (경로, 포맷, 자동저장 여부)
- Excel 형식 내보내기 지원

#### 쇼핑몰 지원
- SSG(신세계, 이마트, 트레이더스) 구매내역 수집
- 오아시스 구매내역 수집
- 하나로마트 구매내역 수집
- 개별 쇼핑몰 자동 수집 주기 설정

#### UI/UX
- Bootstrap 5 기반 모던 UI
- 로그인 화면 개선 (AJAX 기반)
- 세션 기반 인증 시스템
- 실시간 수집 진행 상황 표시
- 사용자 친화적인 에러 메시지

### Changed

#### 데이터베이스
- `jbg_mall` 테이블과 `jbg_access` 테이블 통합
- `jbg_export_config` 테이블 추가 (파일 저장 설정)
- `auto_collect` 및 `collect_interval_minutes` 컬럼 추가

#### 아키텍처
- DAO 클래스 통합 (`JbgMallDataAccessObject`)
- 서비스 레이어 분리 및 개선
- 세션 관리 전역 처리 (AuthInterceptor)
- 설정 파일 외부화 (config/ 폴더)

#### 빌드 시스템
- jpackage에서 Custom JRE + ZIP 배포로 변경
- Gradle 태스크 개선 (`createJre`, `packageDist`)
- 배포 스크립트 자동화 (`Jangbogo.bat`)

### Fixed

- 파일 저장 시 중복 저장 방지
- 자동 수집 중복 실행 방지
- 데이터베이스 연결 누수 문제 해결
- 세션 타임아웃 처리 개선
- 암호화 키 관리 안정성 향상

### Security

- 관리자 계정 환경 변수 지원
- 쇼핑몰 계정 정보 AES 암호화
- localhost(127.0.0.1) 전용 서버 바인딩
- 세션 타임아웃 설정 (30분)
- CSRF 방지 (Spring Security)

### Documentation

- README.md 개선
- 사용자 가이드 3종 작성
  - 사용설명서.txt (설치 및 설정)
  - 사용자_매뉴얼.txt (상세 사용법)
  - README.md (빠른 시작)
- 개발자 문서 작성
  - BUILD_GUIDE.md
  - DEPLOYMENT_GUIDE.md
  - USER_GUIDE.md
  - DISTRIBUTION_IMPLEMENTATION_SUMMARY.md
  - DAO_INTEGRATION_GUIDE.md
  - JBG_CONFIG_GUIDE.md
  - LOGIN_GUIDE.md
  - SESSION_IMPROVEMENT_GUIDE.md
- doc/ 폴더로 문서 통합 정리

---

## [Unreleased]

### Planned

- 자동 업데이트 기능
- 추가 쇼핑몰 지원 (쿠팡, 이마트몰 등)
- 구매 통계 및 분석 기능
- 데이터 시각화 대시보드
- 모바일 앱 연동

---

## 버전 관리 정책

### 버전 번호 규칙 (Semantic Versioning)

**MAJOR.MINOR.PATCH** (예: 1.2.3)

- **MAJOR**: 호환성이 깨지는 변경
- **MINOR**: 하위 호환성 유지하며 기능 추가
- **PATCH**: 하위 호환성 유지하며 버그 수정

### 릴리스 주기

- **메이저 릴리스**: 필요 시
- **마이너 릴리스**: 분기별 (3개월)
- **패치 릴리스**: 필요 시 (버그 수정)

---

## 변경 이력 작성 가이드

각 릴리스마다 다음 카테고리로 변경 사항을 분류:

- **Added**: 새로운 기능
- **Changed**: 기존 기능 변경
- **Deprecated**: 향후 제거될 기능
- **Removed**: 제거된 기능
- **Fixed**: 버그 수정
- **Security**: 보안 관련 변경

---

## 연락처

- **Email**: kiunsea@gmail.com
- **Website**: https://jiniebox.com
- **GitHub**: https://github.com/kiunsea/jangbogo

---

**Copyright © 2025 [jiniebox.com](https://jiniebox.com)**

---

[0.6.0]: https://github.com/kiunsea/jangbogo/releases/tag/v0.6.0
[0.5.5]: https://github.com/kiunsea/jangbogo/releases/tag/v0.5.5
[0.5.4]: https://github.com/kiunsea/jangbogo/releases/tag/v0.5.4
[0.5.3]: https://github.com/kiunsea/jangbogo/releases/tag/v0.5.3
[0.5.2]: https://github.com/kiunsea/jangbogo/releases/tag/v0.5.2
[0.5.1]: https://github.com/kiunsea/jangbogo/releases/tag/v0.5.1
[0.5.0]: https://github.com/kiunsea/jangbogo/releases/tag/v0.5.0
[Unreleased]: https://github.com/kiunsea/jangbogo/compare/v0.6.0...HEAD


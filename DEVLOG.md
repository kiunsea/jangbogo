# DevLog: Jangbogo 작업 이력

## 개요

이 DEVLOG는 프로젝트의 작업 이력을 기록합니다.

**작업 기록 형식**: 각 작업은 `YYYY-MM-DD HH:MM` 형식의 일자로 기록됩니다.

---

## 주요 변경사항

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

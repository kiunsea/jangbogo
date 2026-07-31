# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.13.1] - 2026-07-31

seq → 쇼핑몰 매핑을 한 곳으로 모은 릴리스. **동작 변경은 없습니다** — 순수 리팩터링입니다.

### Changed

- **`MallRegistry` 신설**: `seq` 로 분기하는 하드코딩된 사슬이 세 곳에 흩어져 서로 다른 답을 내놓고 있었습니다.

  | 경로 | seq=1 → |
  |---|---|
  | `data.sql` 시드 | `id='ssg'` |
  | `JangBoGoManager.getMallSession` | `new Emart` 하나만 |
  | `MallOrderUpdater.collectItems` | `Ssg` + `Emart` 둘 |
  | `ExportService.getMallIdFromSeq` | `"emart"` |

  몰을 하나 추가하려면 네 곳을 손대야 했고, **한 곳을 빠뜨려도 컴파일은 통과**했습니다. 이제 선언은 `MallRegistry` 한 곳입니다.
- **`mall_id` 와 내보내기 `mall_id` 를 일부러 통일하지 않았습니다.** seq=1 은 각각 `ssg`(jangbogo DB)와 `emart`(jiniebox 페이로드)로 남습니다. 수신측이 둘 다 seq 1 로 받으므로 어느 쪽이든 동작하지만, **검증할 수 없는 전송 포맷 변경을 리팩터링의 부수효과로 끼워 넣지 않습니다**(수신측 운영 시드는 저장소에 없어 확인 불가). 값이 다르다는 사실을 레지스트리 한 곳에 적어 두는 것으로 대신합니다.
- **계정 연결 검증은 여전히 수집기 하나로만** 합니다(seq=1 은 `Emart`). §9-2 확인에 따르면 `SSG` 로 바꿔도 인증은 성공하지만, 검증 로그인이 향하는 사이트를 바꾸는 것은 리팩터링이 낼 변화가 아닙니다.
- `Coupang` 은 등록하지 않았습니다. 컴파일만 되는 껍데기이고 `data.sql`·DB·화면 어디에도 배선되어 있지 않아, 레지스트리에 넣으면 "지원되는 몰"로 보이게 됩니다. Phase 4B(법무 게이트) 통과 후에 추가합니다.

### Added

- **`MallRegistryTest`(10건)**: 수집기 순서·팩토리 클래스·검증 수집기 단일성·내보내기 id 불변, 그리고 **레지스트리의 `mallId` 가 `data.sql` 시드와 일치하는지**. 둘이 갈라지면 계정 연결·수집·내보내기가 서로 다른 몰을 가리키게 되므로 CI 가 잡습니다. 수집기를 만들기만 하고 `getItems()` 는 부르지 않아 브라우저를 띄우지 않습니다.

---

## [0.13.0] - 2026-07-31

조용한 실패를 드러내는 릴리스. 수집 성공 판정을 세분하고 수집기별 하트비트를 화면에 노출합니다.

### Fixed

- **예외 없이 0건을 받으면 성공으로 기록되던 문제**(Phase 3-4): SSG 는 구매 목록의 `tr` 을 `findElements` 로 찾는데, 셀렉터가 어긋나면 예외가 아니라 **빈 목록**이 돌아오고 루프가 통째로 건너뛰어집니다. 로그인은 성공했으므로 예외도 없습니다.
  - v0.12.0 부터는 이것이 더 나빴습니다 — 브레이커의 **연속 실패 카운트까지 초기화**해서, 셀렉터가 깨지면 영구히 조용해집니다.
  - 이제 0건은 `EMPTY` 로 구분해 기록합니다. **실패로 단정하지는 않습니다** — 정말 안 산 경우와 한 회차만으로는 구분할 수 없기 때문입니다. 대신 그 상태가 얼마나 오래 이어지는지로 판단합니다.
  - 로그인 상태 확인은 이미 네 몰 모두 하고 있었습니다(Phase 1). 3-4 에서 남아 있던 것은 건수 쪽뿐이었습니다.

### Added

- **수집기 하트비트**(Phase 3-5): 수집기마다 마지막 성공 시각과 **마지막으로 실제 데이터를 받은 시각**을 따로 추적합니다. 0건 수집은 앞의 것만 갱신하고 뒤의 것은 그대로 둡니다 — 그 시각이 멈춰 있어야 조용한 실패를 알아챌 수 있습니다.
- **`CollectHealthPolicy`** — 네 가지 경보를 우선순위대로 판정합니다.
  - `TRIPPED` — 브레이커가 열렸다
  - `STALE` — 마지막 성공이 주기의 **2배**를 넘었다 (수집 자체가 안 돌고 있다)
  - `NO_DATA` — 마지막 실제 데이터가 주기의 **14배**를 넘었다 (720분 주기에서 7일). **조용한 실패의 신호지만 자동 차단하지 않고 알리기만 합니다** — 두 달 장을 안 본 사용자를 장애로 처리하면 안 됩니다.
  - `NEVER_RAN` — 아직 성공한 적이 없다
- **`GET /api/collect-health`** — 수집기별 판정 결과와 주의가 필요한 개수를 반환합니다.
- **대시보드 경보 배너**: '실행 결과 요약' 카드 안에 이상이 있는 수집기만 표시합니다. **정상일 때는 아무것도 그리지 않습니다** — 늘 떠 있는 배너는 읽히지 않게 되고, 정작 문제가 생겼을 때도 눈에 안 띕니다.

### Added (테스트)

- `CollectHealthPolicyTest`(10건) — 우선순위(트립 > STALE > NO_DATA), 한 회차 지연은 경보하지 않음, 데이터를 받은 적 없는 신규 계정 처리. 순수 함수라 **실제로 기다리지 않고** 일주일 뒤를 검증합니다.
- `CollectBreakerStateTest` +3건 — 0건 수집이 `last_nonempty_time` 을 밀지 않는지, 하트비트가 몰 이름·주기를 함께 싣는지, 몰 행이 없어도 하트비트가 나오는지(LEFT JOIN).

---

## [0.12.0] - 2026-07-31

수집기별 서킷 브레이커와 백오프를 도입한 릴리스. **자동 수집이 스스로 멈출 수 있게 되었으므로 minor 입니다.**

### Added

- **수집기 서킷 브레이커**(`CollectBreakerPolicy` + `jbg_collect_breaker` 테이블): 같은 수집기가 연속으로 실패하면 자동으로 차단합니다.
  - **차단 단위는 수집기입니다.** seq=1 은 수집기가 둘(SSG·Emart)이라 몰 단위로 세면 한쪽이 계속 죽어도 다른 쪽이 성공하는 한 몰 결과는 SUCCESS 로 남아, **한쪽 수집기 단독 장애를 영원히 잡지 못합니다.**
  - **트립 조건은 둘 중 먼저 오는 쪽**입니다 — 연속 **3회** 실패, 또는 실패가 끊기지 않은 채 **48시간** 경과. 횟수만 쓰면 주기가 길수록 차단이 늦어지므로(원안 5회 × 720분 = **2.5일**) 벽시계 상한을 함께 둡니다.
  - 트립 후 **24시간이 지나면 한 번 복귀를 시도**합니다(half-open). 성공하면 상태가 초기화되고, 실패하면 다시 차단됩니다. 이 경로가 없으면 차단은 사람이 개입하기 전까지 영구 정지가 됩니다.
  - **상태는 테이블에 남습니다.** 메모리에만 두면 재기동이 차단된 수집기를 되살려 다시 사이트를 두드립니다.
- **지수 백오프**: 실패한 수집기는 다음 시도를 `주기 × 2^연속실패` 만큼 미룹니다(상한 24시간). 운영 주기 720분에서는 한 번에 상한에 닿아 사실상 "한 회차 건너뛰기"가 되고, 주기를 하한(360분)까지 낮춘 경우 360 → 720 → 1440 으로 단계가 생깁니다.
- **`jbg_collect_log.collector` 컬럼**: 수집기 이름이 1급 필드가 되었습니다. 이전에는 `step_name` 앞에 `"SSG:signin"` 처럼 접두사로 붙어 있어 수집기별 집계에 문자열 파싱이 필요했습니다.
- **수집기별 성공·건너뜀 기록**: 이전에는 실패만 남아 **연속 실패 카운트를 리셋할 근거가 없었습니다.** 이제 한 회차가 수집기마다 한 행(SUCCESS/FAIL/SKIPPED) + 실행 단위 집계 한 행(`collector` 가 null)을 남깁니다.

### Changed

- **차단은 스케줄 취소가 아니라 수집기 진입 지점의 건너뛰기로 구현했습니다.** 결정은 "해당 몰 스케줄 일시중단"이었으나, 차단 단위가 수집기인데 스케줄은 몰 단위라 그대로 하면 **멀쩡한 수집기까지 멈춥니다.** 브라우저는 각 수집기의 `getItems()` 안에서 뜨므로 진입 전에 걸러내면 **로그인 시도도 브라우저 기동도 일어나지 않습니다** — 사이트를 그만 두드린다는 목적은 동일하게 달성되고, 냉각 후 자동 복귀도 그냥 됩니다.
- **`auto_collect` 컬럼은 어느 경우에도 건드리지 않습니다.** 코드가 사용자 설정을 덮으면 재개할 때 원래 값을 복원할 근거가 사라집니다.
- **수집 요약 통계의 집계 단위가 '수집기 시도'로 바뀝니다.** 수집기 행이 있는 회차에서는 실행 단위 집계 행을 빼고, 수집기 행이 없는 회차(v0.11.x 이전 기록)는 그대로 셉니다 — **기존 기록의 숫자는 이 변경으로 달라지지 않습니다.** `SKIPPED` 는 성공도 실패도 아니므로 집계에서 제외합니다.

### Added (테스트)

- `CollectBreakerPolicyTest`(17건) — 정책이 순수 함수라 시각까지 인자로 받으므로 **실제로 기다리지 않고** 24시간 뒤를 검증합니다. 백오프 배증·상한, 횟수 트립, 시간 트립, half-open 복귀, 성공 시 초기화.
- `CollectBreakerStateTest`(7건) — 복합 PK upsert, 수집기별 독립 추적, **재시작을 넘어 트립이 남는지**, 성공 시각 보존, 트립 목록 조회.

---

## [0.11.8] - 2026-07-29

DB 스키마 선언을 `schema.sql` 한 곳으로 모은 릴리스.

### Fixed

- **신규 설치와 기존 설치가 서로 다른 경로로 같은 스키마에 도달하던 문제**: 컬럼 8개(`jbg_mall.auto_collect`, `jbg_item.qty`, `jbg_export_config` 의 FTP 관련 6개)가 `schema.sql` 에 **선언되어 있지 않고** DAO 의 런타임 `ALTER TABLE` 에만 있었습니다. 신규 설치는 그 컬럼들이 없는 테이블을 받고, DAO 가 처음 호출될 때 뒤늦게 메워지는 구조였습니다. 8개 전부 `schema.sql` 로 선언을 옮겼습니다.
- **복제된 DDL 이 원본과 어긋나 있던 문제**: `JbgExportConfigDataAccessObject` 가 들고 있던 `CREATE TABLE jbg_export_config` 는 `schema.sql` 의 것과 달리 `updated_time` 과 `last_export_time` 두 컬럼이 빠져 있었습니다. `StartupTasks` 도 `jbg_collect_log` 의 DDL 을 자바 문자열로 복제해 두고 있었습니다. 두 복제본을 모두 제거했습니다.

### Changed

- **스키마 진화를 `SchemaMigrator` 한 곳이 소유합니다**(신규). `schema.sql` 을 읽어 실제 DB 와 `PRAGMA table_info` 로 대조하고, 없는 테이블은 그 파일의 DDL 로 만들고 없는 컬럼만 `ALTER TABLE ADD COLUMN` 으로 채웁니다. **컬럼 목록을 자바 코드에 다시 적지 않으므로 두 목록이 어긋날 수가 없습니다.**
  - 흩어져 있던 마이그레이션 **5경로**를 흡수했습니다 — `StartupTasks.migrateCollectLogSchema`, `JbgMallDataAccessObject.ensureAutoCollectColumns`, `JbgExportConfigDataAccessObject.ensureExportConfigTable`, `JbgItemDataAccessObject.ensureQtyColumn`, 그리고 각 DAO 의 호출 지점 14곳. (계획서가 지목한 것은 3경로였고, 착수 후 2경로를 추가로 찾았습니다.)
  - `ALTER` 로 안전하게 붙일 수 없는 컬럼(PRIMARY KEY / UNIQUE / 기본값 없는 NOT NULL)은 **시도하지 않고 경고만** 남깁니다. 테이블 재작성이 필요한 변경은 사람이 판단해야 합니다.
- **조회 경로에서 예외 기반 컬럼 탐지가 사라졌습니다.** 이전에는 `jbg_mall` 조회 한 번에 `SELECT col` 을 던져 보고 예외를 잡는 탐지가 두 번, `jbg_item` 은 다섯 경로가 각각 한 번씩 돌았습니다. 이제 보정은 JVM 당 1회이고, 이후 호출은 `AtomicBoolean` 한 번 읽는 비용입니다.
- 보정 시점은 두 곳입니다 — 기동 시 `StartupTasks`, 그리고 안전망으로 `CommonDataAccessObject` 생성자. 웹서버는 `ApplicationReadyEvent` 보다 먼저 뜨므로 그 사이 요청도 스키마가 보장됩니다.

### Added

- **스키마 마이그레이션 테스트**(`SchemaMigratorTest`, 16건): `schema.sql` 파싱(주석·괄호 안 콤마·테이블 제약), 빈 DB 전체 생성, 구버전 테이블 보정 시 **기존 행 보존**, 멱등성, JVM 당 1회 가드, `ALTER` 불가 컬럼 회피, DAO 생성만으로 보정이 걸리는지. 테스트마다 `@TempDir` 의 새 SQLite 파일을 쓰므로 기준선 DB 에 닿지 않습니다.

---

## [0.11.7] - 2026-07-29

죽은 드라이버 경로 필드를 제거한 릴리스. 동작 변경은 없습니다.

### Removed

- **`WebDriverManager` 의 미사용 필드 5개**(`CHROME_DRIVER_ID` / `CHROME_DRIVER_PATH` / `CHROME_BINARY_PATH` / `EDGE_DRIVER_ID` / `EDGE_DRIVER_PATH`): 선언만 되어 있고 대입도 참조도 한 번도 없었습니다. 상속 클래스도 설정 키도 없었습니다. "여기에 드라이버 경로를 넣는 통로가 있다"는 인상만 주고 있었습니다.
  - **드라이버 경로는 이 필드가 없어도 지정할 수 있습니다.** Selenium 이 표준 시스템 프로퍼티 `webdriver.chrome.driver` 를 직접 읽고, 값이 있으면 Selenium Manager 의 자동 다운로드를 건너뜁니다. 폐쇄망에서는 `java -Dwebdriver.chrome.driver=... -jar jangbogo-x.y.z.jar` 로 기동하면 됩니다. **제거로 잃는 기능은 없습니다.**
  - 브라우저 실행 파일(binary) 지정은 사정이 다릅니다. Selenium 4 에 대응하는 표준 프로퍼티가 없고 `ChromeOptions.setBinary()` 로만 가능합니다. 이 배선은 Phase 5(프로필 재사용)의 바이너리 핀 고정과 같은 문제라 그쪽에서 함께 설계합니다. **아직 미구현이라는 점은 그대로입니다.**

### Added

- **ChromeDriver 기동 옵션 테스트**(`WebDriverOptionsTest`, 5건): headless 기본값(설정이 없으면 켜지 않음), `--headless=new` 적용, 공통 인자(`--remote-allow-origins`, user-agent) 유지를 검증합니다. 브라우저를 띄우지 않고 `ChromeOptions` 객체만 확인합니다.

### Changed

- ChromeDriver 옵션 조립을 `buildChromeOptions(boolean)` 으로 분리했습니다(순수 코드 이동). `headless` 를 설정에서 읽지 않고 인자로 받으므로 테스트가 설정 싱글턴에 의존하지 않습니다.

---

## [0.11.6] - 2026-07-29

Oasis·Hanaro·SSG 파서에 브라우저 없는 회귀 테스트를 붙인 릴리스. 동작 변경은 없습니다.

### Added

- **쇼핑몰 DOM 추출 규칙 테스트 29건**: 셀렉터로 뽑아낸 값을 수신측 계약으로 바꾸는 변환 규칙을 검증합니다. Selenium 인터페이스를 Mockito 로 세우므로 브라우저·네트워크·DB 를 쓰지 않습니다.
  - `OasisParserTest`(11건) — 주문번호 괄호 제거(짧은 값 보호 포함), 구매일자 점 제거·공백 제거, 가격 없는 상품 보존, 상품 0건일 때 빈 배열
  - `HanaroParserTest`(10건) — 테이블 부족 시 `null`, 구매일자 하이픈 제거, `구매일자_구매금액` serial 합성(금액 없을 때 포함), 헤더행 skip, `td` 3개 미만 행 skip
  - `SsgParserTest`(8건) — 주문번호 하이픈 제거, 구매일자 점 제거, "구매 내역 없음" 안내 행 skip, `td` 개수 가드(3개 이하 거부 / 5개 이상 허용)
- **검증 범위를 명시했습니다**: 실사이트 HTML 을 캡처할 수 없어(앱 기동 = 실계정 로그인) 픽스처는 손으로 세운 `WebElement` 트리입니다. 따라서 이 테스트가 잡는 것은 **변환 규칙의 회귀이지 셀렉터의 정확성이 아닙니다.** 셀렉터가 실사이트와 맞는지는 원리상 단위테스트로 알 수 없습니다.

### Changed

- **파싱 지점을 별도 메서드로 분리했습니다**(순수 코드 이동, 로직 동일). 기존에는 `navigatePurchased` 하나에 페이지 이동·창 전환·지연·DOM 추출이 뒤섞여 있어 브라우저 없이는 어느 부분도 검증할 수 없었습니다.
  - `Oasis` — `parseOrderSummary` / `extractDetailLink` / `applyOrderDetail`
  - `Ssg` — `parseOnlineOrderRow` / `parseOnlineOrderItems`
  - `Hanaro` — 기존 `parseDetailPage` 의 가시성만 확대(`private` → package-private)

---

## [0.11.5] - 2026-07-29

'신규 주문 없음' 상태 파일이 수신측에서 매번 실패하던 문제를 고친 릴리스.

### Fixed

- **상태 파일의 최상위 구조가 주문 파일과 달라 매 회차 실패하던 문제**: 주문 파일(`jangbogo_orders_*_ftp.json`)은 JSON **배열**을 보내는데, 신규 주문이 없을 때 보내는 상태 파일(`jangbogo_status_*_ftp.json`)은 JSON **객체**(`{"status":…,"timestamp":…,"message":…,"orders":[]}`)를 보내고 있었습니다.
  - 수신측은 최상위 노드가 배열인지 검사한 뒤에야 주문을 순회합니다. 객체는 그 검사에서 걸려 **예외 없이 매번 실패**했고, 파일은 `failed/` 로 옮겨졌습니다. 그때 **복호화된 평문 JSON 이 함께 디스크에 기록**됩니다(상태 파일에는 구매 데이터가 없어 개인정보 노출은 없었습니다).
  - 상태 파일도 빈 배열 `[]` 로 보냅니다. 주문 0건으로 정상 파싱되어 DB 에 아무 영향 없이 `committed/` 로 이동합니다.
  - 버려지는 필드에 대해 — `timestamp` 는 파일명에 이미 들어 있고, `status`/`message` 는 `jangbogo_status_` 접두사와 빈 배열로 드러납니다. 페이로드 계약 `[{serial, datetime, mall_id, mallname, items:[…]}]` 에는 메타데이터를 실을 자리가 없습니다.
  - 상태 파일 발송 자체는 유지합니다. 이 파일의 존재가 "수집은 돌았고 신규가 없었다"는 하트비트이고, 보내지 않으면 수신측에서 그 상태와 "수집 자체가 죽었다"를 구분할 수 없습니다.

### Added

- **상태 파일 구조 테스트**(`ExportStatusFileTest`, 5건): 수신측이 적용하는 조건(최상위 배열)을 그대로 재현하고, 파일명 규칙과 과거 포맷의 부활을 함께 감시합니다.

---

## [0.11.4] - 2026-07-29

수집 실패 진단 정보가 브라우저 대화상자 때문에 유실되던 문제를 고친 릴리스.

### Fixed

- **수집 실패 시 대화상자 문구와 URL 이 통째로 사라지던 문제**: 쇼핑몰이 `alert`/`confirm` 을 띄운 채 수집이 실패하면, 실패 원인이 적혀 있는 그 문구가 어디에도 기록되지 않았습니다.
  - 원인은 순서입니다. Chrome 은 W3C 기본값 `unhandledPromptBehavior = "dismiss and notify"` 로 동작하므로, 대화상자가 떠 있을 때 `getCurrentUrl()` 같은 평범한 명령을 먼저 호출하면 드라이버가 **대화상자를 먼저 닫아 버리고** 예외를 던집니다. `CollectStep.wrap` 은 그 예외를 삼켜 URL 을 `null` 로 두었고, 문구는 그 시점에 이미 사라진 뒤였습니다. 뒤이은 스크린샷에도 대화상자는 찍히지 않습니다.
  - 이제 `CollectStep.wrap` 이 **가장 먼저** 대화상자를 읽어 기록하고 닫습니다. 그 뒤에 URL·페이지 타이틀·스크린샷을 수집하므로 **셋 다 정상적으로 남습니다.** 문구는 수집 오류 로그의 메시지에 `(alert="…")` 로 붙습니다(여러 줄은 한 줄로 접고 300자에서 자릅니다).
  - 닫을 때는 `accept()` 가 아니라 `dismiss()` 를 씁니다. `confirm` 에서 `accept` 는 "확인"을 누르는 것이라, 진단하려다 실제 동작을 일으킬 수 있습니다.
- **대화상자가 떠 있으면 스크린샷이 저장되지 않던 문제**: `ScreenshotUtil.capture()` 가 `getScreenshotAs` 를 바로 호출해 `UnhandledAlertException` 으로 실패할 수 있었습니다. 캡처 전에 대화상자를 정리합니다.

### Added

- **대화상자 처리 테스트**(`AlertHandlingTest`, 10건): 문구 확보·`dismiss` 선택·URL 보존·**순서**(대화상자 처리가 URL 조회보다 먼저인지)를 검증합니다. Selenium 인터페이스를 Mockito 로 세우므로 브라우저를 띄우지 않습니다.

### Changed

- `ScreenshotUtil` 의 저장 기준 폴더를 시스템 프로퍼티 `jangbogo.screenshot.dir` 로 덮어쓸 수 있게 했습니다(기본값 `logs/screenshots`, 동작 변경 없음). 테스트가 개발 트리에 PNG 를 남기지 않도록 `test` 태스크가 `build/test-screenshots` 로 돌립니다 — `jangbogo.localdb.url` 격리와 같은 방식입니다.

---

## [0.11.3] - 2026-07-29

WinSW 서비스 정의의 JAR 버전 드리프트를 구조적으로 제거한 릴리스.

### Fixed

- **배포본으로 서비스를 수동 등록하면 없는 JAR 를 실행하던 문제**: `packaging/winsw/jangbogo-service.xml` 이 `jangbogo-0.8.1.jar` 을 가리키고 있었습니다(당시 앱 버전 0.11.2). 이 파일은 릴리스 ZIP 의 `service/` 에 그대로 들어갑니다.
  - `install.bat` 으로 설치하면 설치 시점에 폴더의 실제 JAR 이름으로 XML 이 자동 동기화되므로 영향이 없었습니다. 그러나 `service/README.md` 는 `install.bat` 없이 `jangbogo-service.exe install` 을 직접 실행하는 절차를 안내하고 있었고, **그 경로에는 동기화가 없습니다.** 서비스는 등록되지만 시작에 실패합니다.
  - 이제 저장소의 XML 에는 버전을 적지 않습니다. `@JAR_NAME@` 토큰만 두고, `packageDist` 가 `bootJar` 가 실제로 만든 파일명으로 치환합니다. **버전의 단일 출처는 `build.gradle` 의 `version` 하나입니다.**
- **서비스 README 의 낡은 정보 3건**: JAR 이름을 `jangbogo-0.6.0.jar` 로 적고 있었고(3곳), WinSW 버전을 `v3.0.0-alpha.11` 로 적고 있었습니다(실제 `download-winsw.ps1` 이 받는 것은 `v2.12.0`). 문서에서 버전을 걷어내고, 수동 등록 절차에 **JAR 이름 일치 확인 단계**를 추가했습니다.

### Added

- **서비스 정의 드리프트 감시 테스트**(`ServiceDescriptorTest`, 4건): XML 의 실행 인자에 버전이 박히면, README 에 버전이 박히면, `packageDist` 의 토큰 치환이 사라지면, `install.bat` 의 자동 동기화가 사라지면 각각 CI 에서 잡힙니다.

---

## [0.11.2] - 2026-07-29

수집 주기 하한을 코드로 강제한 릴리스.

### Added

- **수집 주기 하한 정책**(`svc/util/CollectIntervalPolicy`): 자동수집 주기는 `0`(자동수집 안 함) 또는 **360분 이상**이어야 합니다. 즉 `0 < 값 < 360` 구간만 거부합니다. 운영 기본값 720분(1일 2회)은 그대로 유효하며, 360분은 넘지 말아야 할 선입니다.
  - 하한은 사용자 설정이 아니라 안전장치이므로 화면에 노출하지 않습니다. 개발 중 짧은 주기로 시험하려면 `-Djangbogo.collect.min-interval-minutes=10` 으로 기동합니다.

### Fixed

- **수집 주기 하한 검증이 코드 어디에도 없던 문제**: 화면은 `min="0"`, 서버는 `> 0` 검사뿐이라 화면 조작이나 API 직접 호출로 1분 주기를 저장할 수 있었습니다. 주기가 짧으면 같은 계정으로 로그인을 반복하게 되어 쇼핑몰 차단을 자초합니다. 이제 세 지점에서 막습니다.
  - **화면** — 저장 전에 검사하고 사유를 알립니다.
  - **서버 저장** — `POST /malls/auto-collect/flags` 가 거부하고 사유를 반환합니다. DAO 의 두 쓰기 경로(`saveAutoCollectFlags`, `updateCollectInterval`)도 각각 막으므로, 화면을 거치지 않는 호출도 통과하지 못합니다.
  - **스케줄 등록** — 검증이 생기기 전에 이미 저장된 값이 DB 에 남아 있을 수 있습니다. 조용히 쓰지 않고 경고를 남긴 뒤 하한으로 올려 스케줄합니다.

---

## [0.11.1] - 2026-07-29

Phase 2(기준선 동결) 종결 릴리스. 의존성 정합성 수정 외에 동작 변경은 없습니다.

### Fixed

- **jackson 모듈 버전이 어긋나 있던 문제**: `build.gradle` 이 `jackson-dataformat-yaml` 만 `2.15.3` 으로 고정해, Spring Boot 의존성 관리가 해석하는 `jackson-core`·`jackson-databind`(2.19.2)와 4개 마이너 버전이 달랐습니다. jackson 은 모듈 버전 일치를 전제하므로 보증되지 않는 조합이었고, 이 경로는 설정 로드·계정 파일·내보내기가 모두 사용합니다. 관측된 장애는 없었으나 잠재 위험이었습니다. 버전 선언을 제거해 정렬했습니다.

### Added

- **jackson YAML 호환성 테스트**(`YamlMapperCompatibilityTest`, 4건): 프로젝트가 YAML 을 다루는 세 방식을 각각 검증하고, 모듈 버전 일치를 함께 검사합니다. 버전이 다시 어긋나면 CI 에서 잡힙니다.

### Changed

- `CLAUDE.md` 의 외부 의존 항목에서 저장소에 포함되지 않은 문서에 대한 참조와 특정 PC 의 절대경로 서술을 제거했습니다.
- `DEVLOG.md` 에 `BASELINE 2026-07-29` 을 기록했습니다. 이후 회귀 판정은 실계정 재수집이 아니라 테스트 통과 여부와 이 수치 비교로 합니다.

---

## [0.11.0] - 2026-07-29

FTP 전송 실패분이 조용히 사라지던 경로를 막은 릴리스.

### Added

- **FTP 전송 실패분 보류 큐**(`svc/util/FtpPendingQueue`): 업로드에 실패한 신규 주문 전송분을 `{save_path}/pending/` 에 보관하고 다음 전송 회차에 오래된 것부터 재시도합니다. 보관 상한은 50건 / 14일이며, 상한 초과로 폐기할 때는 반드시 경고 로그를 남깁니다. 재전송은 첫 실패에서 중단합니다 — FTP 서버가 죽어 있으면 파일마다 연결 타임아웃(15초)을 물어 수집 사이클을 오래 붙잡기 때문이고, 같은 서버이므로 첫 건이 실패하면 나머지도 실패합니다.

### Fixed

- **FTP 업로드 실패 시 전송분이 삭제되던 문제**: `MallSchedulerService` 의 `finally` 가 업로드 성공 여부와 무관하게 전송 파일을 삭제했고, 실패는 경고 로그 한 줄로만 남았습니다. jiniebox 로 보내는 파일은 **증분**(그 회차 신규 주문만)이라 다음 회차가 이를 대신 보내주지 않습니다. 주문 자체는 `jbg_order`/`jbg_item` 에 남지만 **배송 상태가 없어 무엇이 미도달인지 알 방법이 없었습니다**. 이제 신규 주문 전송분은 실패 시 삭제 대신 보류 큐로 이동합니다. 암호화가 켜져 있으면 암호문만 보류되고 평문 원본은 즉시 삭제되므로 평문이 적체되지 않습니다.
- **상태 파일이 이중으로 생성되던 문제**: 신규 주문이 없을 때 `processFileExport` 와 `processFtpUpload` 가 각각 `createEmptyStatusFile` 을 호출했습니다. 두 호출이 초 경계를 넘으면 앞의 파일이 업로드되지도 삭제되지도 않고 남았습니다. 상태 파일 생성을 `processFtpUpload` 한 곳으로 모았습니다. (기존에 남아 있던 고아 파일 6건은 이번에 정리했습니다.)
- **자동저장이 꺼져 있어도 로컬 파일을 쓰던 문제**: `exportOrdersBySeqList` 를 무조건 실행하고 `auto_save_enabled` 로는 로그만 감쌌습니다. 이제 자동저장이 꺼져 있으면 파일을 만들지 않습니다.

### Changed

- FTP 자격증명(주소/아이디/비밀번호)을 **전송 파일 생성보다 먼저** 확인합니다. 보낼 수 없는 상태에서 파일을 만들었다가 지우던 순서를 뒤집었습니다.

---

## [0.10.5] - 2026-07-29

Emart 영수증 파서의 무회귀 판정 단위를 만든 테스트 릴리스. 운영 코드 변경은 없습니다.

### Added

- **Emart 영수증 파서 픽스처 단위테스트**(`EmartReceiptParserTest`, 8건): `Emart.parseReceipt(String)` 의 분기 — 표준 4열, 선행 기호 5열, `combineExtraPattern01` 양방향 줄결합, 할인행 스킵, 요약행 필터, 빈 아이템 구간, 4열 미만 행 — 을 브라우저·네트워크·DB 없이 검증합니다. 앞으로의 회귀 판정은 실계정 재수집이 아니라 이 비교로 합니다.
- **파서 픽스처**(`src/test/resources/fixtures/emart/`, 8종 + 데이터 규칙 README): 픽스처는 **전부 합성**입니다. 이 저장소는 공개돼 있고 실제 영수증은 상품명·금액·구매일시·바코드가 모두 든 구매 이력 그 자체라, 부분 마스킹으로는 재식별 위험이 남습니다. 실물을 마스킹하는 대신 파서 분기를 덮도록 손으로 지은 데이터를 써서 분기 커버리지는 확보하고 개인정보는 애초에 존재하지 않게 했습니다.

---

## [0.10.4] - 2026-07-29

테스트 실행이 실계정 수집을 일으키던 문제를 차단한 안전성 수정 릴리스. 기능 변경은 없습니다.

### Fixed

- **`./gradlew test` 가 실계정 수집을 실행하던 문제**: `JangbogoApplicationTests` 가 `@SpringBootTest` 로 전체 컨텍스트를 로드하면 `StartupTasks` 의 `ApplicationReadyEvent` 가 발화하고, `runInitialCollection()` 이 **동기 호출**이라 실계정 로그인과 브라우저 수집이 끝날 때까지 기동을 붙잡았습니다. CI 는 `build.yml`·`ci.yml` 양쪽에서 테스트를 돌리므로 push 할 때마다 같은 일이 시도됐습니다. 기동 수집을 `jangbogo.startup.collect.enabled` 프로퍼티로 가드하고, 코드 기본값을 `false` 로 두어 프로퍼티가 정의되지 않은 컨텍스트에서는 자동으로 수집이 꺼지게 했습니다. 운영 실행은 `application.yml` 에서 명시적으로 `true` 를 선언합니다. DB 스키마 마이그레이션은 가드 밖에 두어 "앱은 띄우되 수집만 끈다" 가 스키마 갱신까지 끄지 않도록 했습니다.
- **테스트가 운영 DB 파일을 직접 열던 문제**: `LocalDBConnection` 은 Spring `DataSource` 를 거치지 않고 직접 JDBC 로 접속하며 접속 문자열이 하드코딩돼 있어, `src/test/resources/application.yml` 의 `spring.datasource.url` 이 **아무 효과가 없었습니다**. 그 결과 테스트가 기준선 DB(`./db/jangbogo-dev.db`)를 열고 DDL 을 실행했습니다. 접속 대상을 시스템 프로퍼티 `jangbogo.localdb.url` 로 오버라이드할 수 있게 하고(기본값은 기존 값이라 운영 동작 불변), `build.gradle` 의 `test` 태스크가 `build/test-db/` 를 가리키도록 했습니다. 수집 가드와 독립적으로 동작하는 2차 방어선입니다.

---

## [0.10.3] - 2026-07-29

수집이 2026-05-30 이후 한 건도 성공하지 못하던 원인을 제거한 버그 수정 릴리스. 수정 후 실측으로 주문 22건 / 아이템 168건을 수집해 기준선을 확보했습니다.

### Fixed

- **ChromeOptions 가 드라이버에 전달되지 않던 문제**: `WebDriverManager.getWebDriver()` 가 `ChromeOptions` 를 구성해 놓고 `new ChromeDriver()` 를 인자 없이 호출해, user-agent·`--remote-allow-origins` 를 포함한 모든 옵션이 무시되고 있었습니다. `new ChromeDriver(options)` 로 수정했습니다.
- **headless 판정이 반대로 되어 있던 문제**: `BROWSER_HEADLESS` 가 **false 일 때** headless 를 켜는 조건이었습니다. 위 옵션 미전달 때문에 그동안 실제로 적용되지는 않았으나, 옵션 전달을 고치는 순간 "설정 없음 → headless 기동"이 되어 로그인 화면 진단이 불가능해지므로 **같은 커밋에서 함께** 바로잡았습니다. 아울러 `headless` → `--headless=new` 로 교체했습니다.
- **빈이 아닌 클래스의 `@Autowired` 가 동작하지 않던 문제**: `WebDriverManager` 는 크롤러에서 `new` 로 생성되므로 `@Autowired JangbogoConfig` 가 주입되지 않고, `@PostConstruct` 가 실행되지 않은 빈 설정을 참조했습니다. `JangbogoConfig.getInstance()` 홀더를 통해 Spring 이 관리하는 인스턴스를 조회하도록 변경했습니다.
- **SSG 로그인이 항상 실패로 보고되던 문제**: `Ssg.signin()` 이 판정 로직을 주석 처리한 채 무조건 `false` 를 반환했습니다(Task #889). 그 결과 로그인에 성공해도 수집이 중단되어 Emart 단계에 도달조차 하지 못했습니다. 로그아웃 어포던스 관측으로 실제 상태를 반환하도록 수정했습니다.
- **오버레이에 클릭이 가로채이던 문제**: 2026-05-30 장애는 봇 차단이 아니라 이마트 프로모션 배너 `<img>` 가 로그인 버튼을 덮어 `ElementClickInterceptedException` 이 발생한 것이었습니다. 신규 `svc/util/ClickUtil.safeClick()` 이 스크롤 → 클릭 가능 대기 → JS 클릭 폴백 순으로 처리하며, Emart/Hanaro/Oasis/Ssg 의 클릭 지점 11곳에 적용했습니다.
- **SSG 구매내역이 없을 때 수집 전체가 실패하던 문제**: 조회 기간에 온라인몰 구매내역이 없으면 SSG 는 안내 문구 행 하나를 렌더링하는데, 이를 데이터 행으로 파싱해 예외가 발생했습니다. `offlinePurchaseList` 와 동일하게 td 개수로 걸러냅니다.
- **한 수집기의 실패가 다른 수집기까지 막던 문제**: seq=1 은 SSG 와 Emart 두 수집기를 순차 실행하는데 예외 격리가 없어, SSG 실패 시 Emart 수집이 아예 실행되지 않았습니다. 이제 수집기별로 격리되며, 실패는 삼켜지지 않고 `jbg_collect_log` 에 `step_name = "<수집기>:<단계>"` 형태의 FAIL 행으로 별도 기록됩니다. 시도한 수집기가 전부 실패한 경우에만 수집 실패로 전파됩니다.
- **정상 실행이 FAIL 로 기록되던 문제**: 성공/실패 판정이 `스킵 > 0 && 신규 주문 == 0` 이어서, 이미 수집을 마친 뒤의 재실행(전량 중복)이 실패로 기록됐습니다. 데이터가 따라잡힌 시점부터 모든 주기 실행이 영구히 실패로 남는 오탐입니다. "수집해 온 주문이 하나도 쓸 수 없었을 때"만 FAIL 로 판정하도록 수정했습니다.
- **`jbg_collect_log` 테이블이 생성되지 않던 문제**: `StartupTasks.migrateCollectLogSchema()` 는 컬럼 추가(ALTER)만 수행하고 테이블 생성은 `schema.sql` + `spring.sql.init` 에 의존했는데, 그 경로가 `continue-on-error: true` 로 실패를 삼켜 테이블이 없는 채로 남을 수 있었습니다(개발 트리 DB 가 실제로 그 상태였습니다). 기동 시 `CREATE TABLE IF NOT EXISTS` 로 직접 보장합니다.
- **Selenium 버전 선언과 실제가 어긋나던 문제**: `build.gradle` 은 `selenium-java:4.25.0` 을 선언했으나 Spring Boot 의존성 관리가 나머지 selenium 모듈을 4.31.0 으로 해석해, 집합 POM 만 4.25.0 인 혼재 상태였습니다. CDP 경고가 안내하는 아티팩트 좌표도 4.31.0 기준이라 선언을 보고 맞추면 계속 어긋납니다. `ext['selenium.version']` 으로 일원화했습니다.

### Added

- **`svc/util/ClickUtil`**: 오버레이·레이지로딩에 강한 클릭 헬퍼.
- **수집기 격리 회귀 테스트**(`MallOrderUpdaterIsolationTest`, 5건): 한 수집기의 실패가 다른 수집기를 막지 않는지, 그리고 그 실패가 컨텍스트와 함께 남는지를 실계정·브라우저 없이 검증합니다.
- **수집 상태 판정 회귀 테스트**(`MallOrderUpdaterRunnerStatusTest`, 5건).

### Removed

- **`settings.gradle` 의 죽은 `includeBuild('D:/GIT/doribox')`**: 대상이 없어 no-op 이었고, PUBLIC 저장소에 PRIVATE 저장소명과 특정 PC 절대경로가 노출되며, 해당 경로에 clone 이 존재하면 빌드 구성이 조용히 바뀌는 문제가 있었습니다. doribox 패키지 import 방침은 폐기 확정입니다.

---

## [0.10.2] - 2026-05-01

### Added

- **WinSW 자동 다운로드 fallback**: 배포 ZIP 에 `service\jangbogo-service.exe` 가 누락된 경우(빌드 환경에 WinSW 가 없어 `packageDist` 시점에 ZIP 에 들어가지 못한 경우) `install.bat` 이 즉시 종료하지 않고 동봉된 `download-winsw.ps1` 을 호출해 자동으로 받아옵니다.
  - 신규 `packaging/distribution/download-winsw.ps1`: WinSW v2.12.0 (`net461` 빌드, 안정 버전) 을 GitHub Releases 에서 받아 `service\jangbogo-service.exe` 로 저장 + `Unblock-File` 로 MOTW 제거.
  - `download-jre.ps1` 과 동일한 패턴 — TLS 1.2 강제, 이미 존재하면 skip, 실패 시 명확한 에러 메시지.

### Changed

- **`install.bat` 의 WinSW 누락 처리**: 기존 "[ERROR] not found → exit /b 1" 분기를 제거하고, `download-winsw.ps1` 이 있으면 자동 호출, 없거나 다운로드 실패 시에만 에러로 종료하도록 변경. 기존 사용자(이미 WinSW 가 있는 경우)에게는 영향 없음.
- **`build.gradle` 의 `packageDist` 태스크**: 배포 ZIP 의 `include` 목록에 `download-winsw.ps1` 추가.

### Fixed

- **첫 설치 시 WinSW 누락으로 install.bat 이 멈추던 문제**: 빌드 환경에 따라 `packaging/winsw/jangbogo-service.exe` 가 없으면 (`.gitignore` 로 git 미추적이므로 새 환경에서는 항상 누락) ZIP 에도 빠져 사용자가 직접 받아야 했습니다. v0.10.2 부터 install.bat 이 자동으로 처리합니다.

---

## [0.10.1] - 2026-04-23

### Added

- **트레이 아이콘 재시작 도구**: OS 재부팅 후 Windows 시스템 트레이가 새로고침되지 않아 `Jangbogo-Tray.ps1` 프로세스는 살아있지만 아이콘이 보이지 않는 상황을 위해 **재시작용 진입점**을 추가했습니다.
  - `packaging/distribution/Restart-Tray.bat` 신규 — 관리자 권한 없이 실행 가능한 일회성 배치 파일.
  - 바탕화면 / 시작 메뉴에 "Restart Jangbogo Tray" 단축아이콘을 자동 생성 (`create-shortcuts.ps1` 확장).
  - `Jangbogo-Tray.ps1` 의 `-Restart` 인자: 호출 시 같은 스크립트로 떠있는 PowerShell 프로세스를 모두 종료한 후 새 트레이를 띄웁니다.

### Changed

- **`Jangbogo-Tray.ps1` 단일 인스턴스 보호**: 글로벌 Mutex(`Global\JangbogoTrayInstance`)로 중복 실행을 차단합니다. 이미 다른 인스턴스가 떠 있으면 안내 메시지를 띄우고 즉시 종료합니다. `-Restart` 인자로 호출하면 기존 인스턴스를 종료한 후 진행합니다.
- **`build.gradle` 의 `packageDist` 태스크**: 배포 ZIP 에 `Restart-Tray.bat` 파일을 포함하도록 `include` 목록 갱신.
- **사용설명서.txt**: FAQ 에 Q11 "컴퓨터를 재부팅했더니 트레이 아이콘이 안 보여요" 항목 추가. 3가지 해결 방법(바탕화면 단축아이콘 / Restart-Tray.bat / 시작 메뉴) 안내 및 "서비스는 계속 동작 중이므로 데이터 수집에는 영향 없음" 명시.

### Notes

- 스키마 / API 변경 없음. 기존 단축아이콘 "Jangbogo Tray" 동작은 그대로 유지되며, 추가된 "Restart Jangbogo Tray" 가 보조 진입점 역할만 수행합니다.
- `install.bat` 은 트레이 기동 전에 기존 PS 프로세스를 정리하므로 mutex 충돌이 발생하지 않습니다 (변경 없음).

---

## [0.10.0] - 2026-04-23

### Added

- **구매 내역 조회 페이지 (`/orders`)**: 수집된 주문(`jbg_order`)과 해당 아이템(`jbg_item`)을 웹 UI 에서 직접 열람할 수 있습니다. 기존에는 파일 내보내기(export) 경로로만 접근 가능했던 데이터를 대시보드 네비에서 바로 확인할 수 있습니다.
  - 테이블 컬럼: 주문 seq / 구매일자 / 쇼핑몰 / 주문번호 / 아이템 수 / 등록시간 / 상세.
  - 필터: 쇼핑몰 드롭다운(수집된 쇼핑몰만 자동 채움), 최대 건수(100/200/500/1000).
  - 상세 모달: 주문 메타데이터 + 아이템 목록(아이템명/수량/등록시간) 테이블.
  - 요약 배지: 현재 조건으로 조회된 주문 수와 누적 아이템 수.
- **구매 내역 조회 API** (`AdminController`):
  - `GET /api/orders?limit=N&mall=X&dateFrom=YYYYMMDD&dateTo=YYYYMMDD` — 주문 목록 + 각 주문의 아이템 배열을 포함해 반환. 서버 사이드 필터 지원.
  - `GET /api/orders/{seq}` — 단일 주문 + 아이템 상세 조회.
- **네비게이션 메뉴 "구매 내역" 추가**: `fragments/header.html` 의 네비 바에 "대시보드 / 구매 내역 / 오류 로그 / 계정 설정" 순서로 배치.

### Notes

- DAO / 스키마 변경 없음. `JbgOrderDataAccessObject.getAllOrders()` + `JbgItemDataAccessObject.getItemsByOrder()` 기존 메서드 조합으로 구현.
- 기존 파일 내보내기 경로(`/export/orders`) 는 그대로 유지. 새 페이지는 읽기 전용 조회 용도.

---

## [0.9.1] - 2026-04-23

### Removed

- **`packaging/scripts/` 폴더 제거**: `post-install.bat`, `pre-uninstall.bat`, `Jangbogo.bat`(v0.5.5 JAR 참조) 3개 파일 모두 jpackage 시도 실패 시절(→ Custom JRE + ZIP 배포로 전환) 유물이었습니다. `packageDist` 태스크 어디에서도 참조되지 않으며, `post-install.bat` 이 호출하던 `jangbogo.exe --install-complete` 는 v0.9.0 에서 Java 트레이와 함께 이미 제거된 상태라 실행 자체가 불가능했습니다.
- **`.gitignore` 의 `!packaging/scripts/*` 예외 규칙 제거**: 대상 디렉터리가 사라졌으므로 화이트리스트 라인도 정리.

### Notes

- 소스 코드/스키마/API 변경 없음. 배포 ZIP (`packageDist`) 산출물 구조 및 `install.bat` / WinSW / PowerShell 트레이 동작 모두 동일합니다.
- 태그/릴리스 발행 없음 (cleanup patch, 사용자 영향 제로).

---

## [0.9.0] - 2026-04-23

### Removed

- **Java 기반 트레이 애플리케이션 제거**: `src/main/java/.../sys/TrayApplication.java` 삭제. 배포 경로(`install.bat`)는 v0.7.0부터 PowerShell `Jangbogo-Tray.ps1` 만 사용해 왔으며, Java 트레이는 실제로 호출되지 않는 죽은 코드 상태였습니다.
- **`--tray` / `--install-complete` 실행 플래그 제거**: `JangbogoLauncher` 에서 해당 모드 분기와 관련 메서드를 제거했습니다. 배포 산출물에서 이 플래그를 호출하는 스크립트는 없으므로 외부 영향 없음.

### Changed

- **`JangbogoLauncher` 단순화**: `ExecutionMode` 열거형을 `SERVICE` / `NORMAL` 두 값으로 축소. `--service` (WinSW용)와 인자 없음(개발 모드)만 지원합니다.
- **DEPLOYMENT_GUIDE 본문 재구성**: 목차를 "🚀 표준 (원스톱 설치)" / "🔧 고급·수동 절차" / "🚨 문제 해결" 3부로 분리. 기존 "설치 방법/실행 방법/Windows 서비스 등록/제거 방법" 섹션을 "수동 ~" 로 개명하고 각 섹션 상단에 "원스톱 `install.bat` 이 이 작업을 자동화합니다" 안내 블록을 추가해 고급 참고자료 성격을 명확히 했습니다. 하단 버전 표시 0.6.0 → 0.8.1 → 0.9.0 로 갱신.
- **CLAUDE.md 실행 모드 섹션 갱신**: 제거된 플래그 서술 삭제, PowerShell 트레이 위임 사실 명시. `sys/` 패키지 설명에서 `TrayApplication` 제거.

### Notes

- 스키마/API 변경 없음. 기존 설치본은 그대로 동작하며, `install.bat` / WinSW / PowerShell 트레이 경로에 영향 없음.
- 배포 ZIP (`packageDist`) 산출물 구조 동일.

---

## [0.8.1] - 2026-04-22

### Docs

- **루트 README**: "핵심 기능"에 v0.7.0 원스톱 설치, v0.8.0 수집 실패 상세 진단 항목 추가. 배포 ZIP 내용에 install.bat/uninstall.bat/Jangbogo-Tray.ps1/create-shortcuts.ps1/download-jre.ps1 등 신규 파일 반영. "설치 방법" 섹션 신규 추가 (관리자 권한 install.bat 기본 경로 안내).
- **사용자/개발자 가이드 버전 문자열 일괄 정정**: README.md, BUILD_GUIDE.md, DEPLOYMENT_GUIDE.md, USER_GUIDE.md, doc/user/README.md, doc/developer/DISTRIBUTION_IMPLEMENTATION_SUMMARY.md 의 `jangbogo-0.5.x.jar` / `0.6.0.jar` / `0.6.1.jar` 참조 및 "v0.5.0", "v0.6.0", "v0.6.1" 서술을 **v0.8.1 / jangbogo-0.8.1.jar 로 통일**.
- **한글 가이드 정합성**: `packaging/distribution/설치가이드.txt` / `사용설명서.txt` / `고급가이드.txt` / `packaging/distribution/README.md` 의 JAR 파일명과 버전 서술 갱신.
- **WinSW 설정 동기화**: `packaging/winsw/jangbogo-service.xml` 의 `<arguments>` 내 JAR 파일명을 0.8.0 → 0.8.1 로 갱신. (install.bat 이 실제 JAR 파일명을 감지해 XML을 런타임에 재작성하지만, 소스 정합성 유지 차원)

### Notes

- 기능/API/스키마 변경 없음. v0.8.0 의 동작과 완전히 동일.
- 배포 산출물 재생성만 필요한 **문서 정합성 patch**.

---

## [0.8.0] - 2026-04-18

### Added

- **수집 실패 상세 진단 기능**: 쇼핑몰 크롤링 실패 시 실패 단계명, 현재 URL, 페이지 타이틀, 타겟 셀렉터, 스크린샷을 자동으로 기록합니다. 사이트 구조 변경으로 인한 탐색 실패를 정확히 식별할 수 있습니다.
- **CollectException + CollectStep 유틸리티**: Selenium 작업을 감싸 예외 발생 시 컨텍스트를 자동으로 포착해 상위로 전파합니다. 기존처럼 `log.error`로 삼킨 뒤 빈 결과를 리턴하던 패턴을 제거하여, 모든 수집 실패가 `jbg_collect_log`에 FAIL 상태로 기록됩니다.
- **ScreenshotUtil**: 실패 시점 WebDriver 화면을 `logs/screenshots/yyyyMMdd/{mall}-{timestamp}.png`로 저장하고, 30일 이전 폴더를 자동 정리합니다.
- **수집 로그 상세 모달**: `/collect-logs` 페이지에 "보기" 버튼을 추가했습니다. 클릭 시 단계명/URL/타이틀/셀렉터/스크린샷 썸네일(클릭 시 확대)/전체 스택트레이스를 모달로 표시합니다.
- **실패 단계 필터**: 수집 로그 화면에 쇼핑몰 드롭다운과 실패 단계 드롭다운을 추가했습니다. "어느 쇼핑몰의 어떤 단계에서 자주 실패하는지" 빠르게 파악할 수 있습니다.
- **스크린샷 서빙 API**: `GET /api/collect-logs/{seq}/screenshot` — 로그 seq로 해당 실패 시점 PNG를 스트리밍합니다. `logs/screenshots` 경로 밖의 파일 접근은 차단됩니다.
- **단일 로그 조회 API**: `GET /api/collect-logs/{seq}` — 특정 실행의 전체 컨텍스트 조회.

### Changed

- **`jbg_collect_log` 스키마 확장**: `step_name`, `current_url`, `page_title`, `target_selector`, `screenshot_path` 5개 컬럼 추가. 기존 사용자는 애플리케이션 시작 시 `StartupTasks`가 자동으로 `ALTER TABLE ADD COLUMN`을 수행해 데이터 보존된 채 마이그레이션됩니다.
- **쇼핑몰 크롤러 리팩토링** (Ssg/Oasis/Emart/Hanaro): `getItems()` 메서드의 try/catch가 예외를 삼키지 않고 `CollectException`으로 래핑해 전파합니다. 로그인 실패/네비게이션 실패 시 즉시 FAIL 로그가 기록됩니다.
- **MallOrderUpdaterRunner / MallSchedulerService**: catch 블록에서 `CollectException` 여부를 확인하여 컨텍스트 필드를 추출해 DAO에 전달합니다.
- **JbgCollectLogDataAccessObject**: 확장 시그니처 `addLog(...)` 추가 (13개 파라미터). 기존 9개 파라미터 시그니처는 delegation으로 하위호환 유지.

### Fixed

- **수집 실패 시 이력 누락 문제**: 사이트 구조 변경 등으로 Selenium이 다음 단계로 진행하지 못해도, 기존에는 `getItems()` 내부에서 예외를 삼키고 빈 배열을 반환해 상위 runner가 "성공"으로 인식 → `jbg_collect_log`에 FAIL 로그가 남지 않던 버그를 해결했습니다. 이제 모든 수집 실패가 상세 컨텍스트와 함께 기록됩니다.

---

## [0.7.0] - 2026-04-17

### Added

- **수집 실행 오류 로그 기능**: `jbg_collect_log` 테이블을 추가해 자동 수집 실행 결과(성공/실패, 오류 메시지)를 기록합니다. 대시보드에 "실행 결과 요약" 카드를 추가하고 별도 "오류 로그" 페이지(`/collect-logs`)에서 실패 내역을 조회할 수 있습니다. `GET /api/collect-logs/summary`, `GET /api/collect-logs/failures`, `GET /api/collect-logs` 엔드포인트를 추가했습니다.
- **Windows 서비스 통합 설치/제거 스크립트**: `install.bat`, `uninstall.bat`을 신규 추가해 관리자 권한 확인, WinSW 서비스 설치/시작/제거, 바탕화면/시작메뉴 단축아이콘 생성, 트레이 기동까지 원스톱으로 처리합니다.
- **PowerShell 기반 시스템 트레이**: `Jangbogo-Tray.ps1`을 추가해 대시보드 열기, 서비스 상태 조회, 시작/중지/재시작, 트레이 종료 메뉴를 제공합니다. Spring Boot 미기동 상태에서도 독립 실행됩니다.
- **JRE 자동 다운로드**: `download-jre.ps1`로 Temurin JRE 21을 자동 다운로드해 번들 JRE가 누락된 환경에서도 실행 가능합니다.
- **단축아이콘 자동 생성**: `create-shortcuts.ps1`이 바탕화면과 시작 메뉴에 트레이 앱과 대시보드 URL 단축아이콘을 생성합니다.
- **CLAUDE.md 프로젝트 가이드**: Release/Push 워크플로우, DAO 패턴, 실행 모드, DB 스키마, API 목록 등 프로젝트 운영 가이드를 신규 추가했습니다.

### Changed

- **install.bat / uninstall.bat 전면 영문화**: 배치 파일 텍스트를 영문 기반으로 재작성해 CP949/UTF-8 코드페이지 혼재 시 발생하던 문자 깨짐/파싱 오류를 원천 차단했습니다.
- **Jangbogo.bat 강화**: JAR 자동 탐지, 시스템 Java 버전 검증(≥21), JRE 자동 다운로드 fallback, 포트 점유 시 다른 포트 입력 프롬프트를 추가했습니다.
- **install.bat 견고성 강화**: 서비스 `sc query RUNNING` 폴링(최대 20초), 실패 시 `service\logs` 및 `logs\jangbogo.log` 자동 tail, 대시보드 HTTP ready polling(최대 45초), 포트 충돌 사전 감지, JAR 파일명에 맞춘 WinSW XML 자동 동기화 기능을 추가했습니다.
- **Gradle 빌드 스크립트**: `build_package.bat`에 `--no-daemon` 옵션을 추가해 Gradle 데몬 통신 오류를 방지합니다.
- **packageDist 태스크 확장**: 새로 추가된 PowerShell 스크립트들을 배포 ZIP에 포함하도록 `build.gradle`의 `packageDist` 태스크를 갱신했습니다.
- **트레이 메뉴 개편**: `TrayApplication`의 메뉴 구성을 대시보드 / 서비스 재시작 / 서비스 종료 / 종료로 정비했습니다.

### Fixed

- **배치 파일 인코딩 문제**: `install.bat`/`uninstall.bat`이 UTF-8로 저장되어 cmd.exe의 CP949 파싱과 충돌해 `'cho'은(는) 내부 또는 외부 명령...` 형태의 오류를 발생시키던 문제를 해결했습니다.

---

## [0.6.1] - 2026-02-15

### Added

- **애플리케이션 시작 시 1회 수집**: 스케줄링 복원 전에 자동 수집 대상 쇼핑몰에 대해 1회 수집을 실행하도록 `StartupTasks.runInitialCollection()` 및 `MallSchedulerService.runOneTimeCollection()` 추가

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


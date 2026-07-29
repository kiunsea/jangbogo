# DevLog: Jangbogo 작업 이력

## 개요

이 DEVLOG는 프로젝트의 작업 이력을 기록합니다.

**작업 기록 형식**: 각 작업은 `YYYY-MM-DD HH:MM` 형식의 일자로 기록됩니다.

---

## 주요 변경사항

### [2026-07-29 13:30] 판단 대기 8 — Oasis·Hanaro·SSG 파서 테스트 (v0.11.6)

#### 결정

**파싱 seam 추출 + Mockito.** 사용자 승인을 받고 착수했다.

제시한 4안과 채택/기각 사유는 아래와 같다.

| 안 | 판정 | 사유 |
|---|---|---|
| **seam 추출 + Mockito** | **채택** | 순수 코드 이동이라 회귀 위험이 가장 낮고, 실제 회귀가 나는 지점(변환 규칙)을 정확히 덮는다 |
| seam 추출 + `navigatePurchased` 전체 목킹 | 기각 | 커버리지는 넓지만 스텁 분량이 3~4배이고, 목이 실제 드라이버 의미론과 어긋나면 **통과하는 거짓 테스트**가 생긴다 |
| jsoup 순수 파서로 재작성 | 기각 | 이번 세션은 실사이트 HTML 을 캡처할 수 없다(앱 기동 = 실계정 로그인). 픽스처가 어차피 손으로 지어낸 HTML 이 되므로, 의존성 추가와 셀렉터 전면 재작성의 값을 못 한다 |
| 보류 | 기각 | 세 몰의 수집 경로에 회귀 테스트가 하나도 없는 상태가 계속된다 |

#### 무엇을 검증할 수 있고 무엇은 못 하는가 — 먼저 못 박는다

실사이트 HTML 이 없으므로 픽스처는 손으로 세운 `WebElement` 트리다. 그래서 이 테스트가 잡는 것은 **셀렉터의 정확성이 아니라 변환 규칙의 회귀**다.

- **잡는다** — serial 괄호 제거, 하이픈·점 제거, 헤더행 skip, `td` 개수 가드, 선택 필드(가격) 누락 허용, 빈 목록일 때 `null` 이 아닌 빈 배열
- **못 잡는다** — 셀렉터가 실사이트 DOM 과 맞는지. 이건 원리상 단위테스트로 알 수 없고 실사이트에서만 드러난다

이 한계를 `MallDomFixtures` javadoc 에 적어 두었다. 나중에 "테스트가 있는데 왜 수집이 깨졌나"를 묻지 않기 위해서다.

#### seam 추출 — 순수 코드 이동

| 몰 | 분리한 메서드 |
|---|---|
| `Oasis` | `parseOrderSummary(WebElement)` / `extractDetailLink(WebElement)` / `applyOrderDetail(WebDriver, JSONObject)` |
| `Ssg` | `parseOnlineOrderRow(WebElement)` / `parseOnlineOrderItems(WebDriver)` |
| `Hanaro` | `parseDetailPage` 는 이미 분리돼 있었다 — 가시성만 `private` → package-private |

로직은 한 줄도 바꾸지 않았다. 분리한 메서드에는 페이지 이동·창 전환·지연이 들어가지 않는다.

#### 잡아낸 것 — 기존 가드의 의미가 테스트로 고정됐다

SSG 의 `td.size() < 4` 가드는 Phase 1 에서 들어간 것이고, 원래 주석은 "구매 내역이 없으면 안내 행 하나가 렌더링된다"였다. 테스트로 옮기면서 경계를 양쪽에서 못 박았다 — **3개 이하는 거부하고 5개 이상은 허용한다.** 컬럼이 늘어난 것만으로 데이터 행을 버리면 수집이 조용히 0건이 되기 때문이다.

Oasis 의 serial 괄호 벗기기도 마찬가지다. `substring(1, len-1)` 을 무조건 적용하면 짧은 값이 빈 문자열이 된다. 길이 가드가 그것을 막고 있는데, 그 사실이 코드에는 적혀 있지 않았다. 테스트가 그 의도를 고정한다.

#### 개발 중 걸린 것

`when(driver.findElement(BY)).thenReturn(text("..."))` 처럼 `thenReturn` 인자 안에서 새 목을 스터빙하면 Mockito 가 중첩 스터빙으로 보고 `UnfinishedStubbingException` 을 던진다. 목 생성을 `when(...)` 밖에서 끝내도록 고쳤고, 재발 방지로 그 이유를 주석에 남겼다.

#### 테스트 수

29건 추가(Oasis 11 / Hanaro 10 / SSG 8). `HanaroTest` 는 `@Test` 가 없는 `main()` 드라이버라 Gradle 이 실행하지 않는다 — 이번에 추가한 것과 별개다.

---

### [2026-07-29 12:55] Phase 3-6 — 상태 파일 포맷 불일치 (v0.11.5)

#### 작업 개요

`buildJinieboxJsonFromOrders` 는 JSON 배열을 만드는데 `createEmptyStatusFile` 은 JSON 객체를 썼다. 같은 수신측에 최상위 구조가 다른 두 종류가 올라갔다.

#### 수신측 기대값 — 물어볼 필요가 없었다

지시는 "어느 쪽에 맞출지는 수신측 기대값을 확인할 수 없으니 판단 근거를 정리해서 물어라"였다. 확인해 보니 **수신측 코드가 로컬에 있었다.** 읽어서 확정했다.

수신측 파서는 최상위 노드가 배열인지 검사하고, 아니면 그 자리에서 검증에 실패한다. 통과한 뒤에야 주문을 하나씩 순회한다. 즉 **배열은 선택지가 아니라 하드 게이트**다. 현재의 객체 형태는 예외 없이 매번 실패한다.

방증도 있다. 수신측 파일 이동 모듈의 주석 예시 파일명이 하필 `jangbogo_status_…_ftp.json.encrypted.failed.log` 다. 상태 파일이 `failed/` 로 가는 것은 이미 관측돼 있던 사실이다.

빈 배열을 넣으면 어떻게 되는지도 끝까지 따라갔다.

| 단계 | 객체 (기존) | 빈 배열 (변경 후) |
|---|---|---|
| 파서 | 최상위 검사 실패 | 통과, 주문 0건 |
| 주문 순회 | 도달 못 함 | 루프 미진입 |
| DB | 영향 없음 | 영향 없음 |
| 알림 | — | 발송 없음 (순회 안에서 만들어지는데 순회를 안 한다) |
| 파일 | `failed/` + **복호화 평문 JSON 기록** | `committed/` |

상태 파일에는 구매 데이터가 없으므로 평문이 남아도 개인정보 노출은 아니었다. 다만 파일이 계속 쌓였다.

#### 남은 판단은 포맷이 아니라 "계속 보낼 것인가"였다

포맷은 증거로 확정됐으므로, 실제 결정이 필요한 것만 물었다. **사용자 결정: 빈 배열로 바꾸고 계속 보낸다.**

근거는 하트비트다. 이 파일의 존재 자체가 "수집은 돌았고 신규가 없었다"는 신호다. 보내지 않으면 수신측에서 그 상태와 "수집 자체가 죽었다"를 구분할 수 없다 — 판단 대기 11(배송/도달 추적 부재)과 직결된다.

#### 버려지는 필드

`status`·`timestamp`·`message` 3개가 사라진다. 손실은 사실상 없다.

- `timestamp` — 파일명 `jangbogo_status_20260729_125500_ftp.json` 에 이미 초 단위로 들어 있다.
- `status: no_new_orders` — `jangbogo_status_` 접두사 + 빈 배열로 충분히 드러난다.
- `message` — 사람이 읽는 문장이고 수신측 소비자가 없다.

페이로드 계약 `[{serial, datetime, mall_id, mallname, items:[…]}]` 에는 애초에 메타데이터를 실을 자리가 없다. 억지로 넣으려면 가짜 주문 한 건을 만들어야 하는데 그건 훨씬 나쁘다.

#### 계획서 항목 중 하나는 해당 없음

계획서 3-10 의 "기존 `failed/` 평문 적체 정리"는 **jangbogo 작업이 아니다.** `failed/` 는 수신측 디렉터리 개념이고 jangbogo 코드에는 존재하지 않는다. 지시대로 재조사하지 않았다.

---

### [2026-07-29 12:35] Phase 3-7 — 수집 실패 시 대화상자 문구·URL 유실 (v0.11.4)

#### 작업 개요

계획서는 이 건을 "`ScreenshotUtil.capture()` 가 `getScreenshotAs` 를 바로 호출해 alert 이 뜨면 실패하고 null 을 반환한다 — 스크린샷이 가장 필요한 상황에서 아무것도 안 남는다"로 적었다. 착수 전에 코드를 다시 읽었더니 **실제 손실은 스크린샷이 아니었다.**

#### 배경 — 손실은 순서에서 나온다

`WebDriverManager` 는 `unhandledPromptBehavior` capability 를 지정하지 않는다. 그래서 chromedriver 는 W3C 기본값 `"dismiss and notify"` 로 동작한다. 이 모드에서는 대화상자가 떠 있을 때 들어온 명령이 **먼저 대화상자를 닫고** 그 다음 오류를 돌려준다.

`CollectStep.wrap` 의 수집 순서는 이랬다.

```java
String url   = safe(() -> driver.getCurrentUrl());   // ← 여기서 대화상자가 닫힌다
String title = safe(() -> driver.getTitle());
String screenshot = ScreenshotUtil.capture(driver, mallName);
```

1. `getCurrentUrl()` 이 대화상자를 닫고 `UnhandledAlertException` 을 던진다.
2. `safe()` 가 그 예외를 삼킨다 → **`url = null`**.
3. 그 시점에 대화상자는 이미 사라졌으므로 `getTitle()` 과 `capture()` 는 오히려 **성공한다.**

즉 계획서가 지목한 스크린샷은 대개 정상적으로 찍히고, 대신 **실패 원인이 적혀 있는 대화상자 문구가 아무 데도 기록되지 않은 채 소멸하며 URL 까지 함께 유실된다.** 찍힌 스크린샷에도 대화상자는 없다 — 이미 닫힌 뒤다.

`ScreenshotUtil` 단독 수정으로는 이 손실을 못 막는다. 고칠 지점은 `wrap` 의 **순서**다.

#### 수정

`wrap` 의 맨 처음에 대화상자를 처리한다.

```java
String alertText = ScreenshotUtil.consumePendingAlert(driver);  // 문구 확보 + 닫기
String url   = safe(() -> driver.getCurrentUrl());              // 이제 정상 동작
String title = safe(() -> driver.getTitle());
String screenshot = ScreenshotUtil.capture(driver, mallName);
```

문구는 예외 메시지에 `(alert="…")` 로 붙어 `jbg_collect_log` 에 남는다. 여러 줄은 한 줄로 접고 300자에서 자른다.

`capture()` 에도 같은 처리를 방어적으로 넣었다. 통상은 `wrap` 이 이미 처리한 뒤라 두 번째 호출은 "대화상자 없음"으로 돌아온다. 직접 호출자와, `unhandledPromptBehavior` 가 `ignore` 인 환경·Edge 경로를 위한 것이다.

#### dismiss 인가 accept 인가

`dismiss()` 를 쓴다. 인자가 하나뿐인 `alert` 에서는 둘이 같지만, `confirm` 에서 `accept` 는 "확인"을 누르는 것이다. **진단하려다 실제 동작(주문 취소 등)을 일으켜서는 안 된다.** 테스트가 `accept()` 가 호출되지 않는 것까지 검증한다.

기존에 의도적으로 `accept()` 를 쓰는 `Ssg.signout` 은 정상 경로라 영향이 없다.

#### 테스트 격리

`ScreenshotUtil` 의 기준 폴더를 시스템 프로퍼티 `jangbogo.screenshot.dir` 로 덮어쓸 수 있게 했다(기본값 불변). `test` 태스크가 `build/test-screenshots` 로 돌린다. `jangbogo.localdb.url` 격리와 같은 방식이며, 테스트가 개발 트리의 `logs/` 에 PNG 를 남기지 않는다. 실행 후 `git status` 로 오염 없음을 확인했다.

#### 부수 확인 — Phase 3-11 선취

같은 파일을 읽다 확인했다. `WebDriverManager.java:20` 의 `CHROME_DRIVER_PATH` / `CHROME_BINARY_PATH` 는 **선언만 있고 대입도 참조도 0회**다. 3-11 은 "실배선 또는 삭제"가 아니라 사실상 삭제 건이다. 이번 커밋 범위에는 넣지 않았다.

---

### [2026-07-29 12:10] Phase 3-8 — WinSW 서비스 정의 버전 드리프트 제거 (v0.11.3)

#### 작업 개요

`packaging/winsw/jangbogo-service.xml:16` 이 `jangbogo-0.8.1.jar` 을 가리키고 있었다. 당시 앱 버전은 0.11.2 다. 버전을 고쳐 적는 대신 **버전을 적을 자리 자체를 없앴다.**

#### 배경 — 계획서의 판단을 착수 전에 재확인했고, 두 군데가 달랐다

계획서는 "배포본으로 서비스를 설치하면 존재하지 않는 JAR 를 실행하게 된다"고 적었다. 절반만 맞다.

`install.bat:90` 이 이미 설치 시점에 XML 을 동기화하고 있다. 폴더에서 `dir /b /o:-d jangbogo-*.jar` 로 실제 JAR 을 찾아 `/service/arguments` 노드를 통째로 갈아끼운다. 그래서 **`install.bat` 경로로 설치하면 0.8.1 참조는 실행되지 않는다.**

문제는 다른 경로다. `packaging/winsw/README.md` 는 `install.bat` 을 언급하지 않고 관리자 명령 프롬프트에서 `jangbogo-service.exe install` 을 직접 실행하라고 안내한다. 그 README 도 릴리스 ZIP 의 `service/` 에 들어간다. **README 절차를 그대로 따르면 동기화가 일어나지 않고, 서비스는 등록되지만 시작에 실패한다.**

게다가 드리프트 지점은 XML 하나가 아니었다.

| 위치 | 적혀 있던 값 | 실제 |
|---|---|---|
| `jangbogo-service.xml:16` | `jangbogo-0.8.1.jar` | 0.11.2 |
| `README.md` 설정 예시(2곳) | `jangbogo-0.6.0.jar` | 0.11.2 |
| `README.md` 문제 해결 | `jangbogo-0.6.0.jar` | 0.11.2 |
| `README.md` WinSW 버전 | `v3.0.0-alpha.11` | `download-winsw.ps1` 은 `v2.12.0` 을 받는다 |

버전을 다섯 군데에 적어 두면 다섯 군데가 각자 낡는다. 실제로 그렇게 됐다.

#### 채택안 — 버전을 두 번 적지 않는다

저장소의 XML 에는 `@JAR_NAME@` 토큰만 둔다. `packageDist` 가 `bootJar.archiveFileName` 으로 치환한다(`ReplaceTokens`). 앱 버전이 오르면 ZIP 안의 XML 도 같이 오른다 — 사람이 손댈 자리가 없다.

`@JAR_NAME@` 은 유효한 JAR 이름이 아니라서 **자리표시자임이 한눈에 보인다.** `jangbogo-0.8.1.jar` 처럼 그럴듯하면서 틀린 값보다 낫다. 저장소 파일을 그대로 복사해 쓰면 즉시 실패하고, 그 실패가 곧 "치환 단계를 빠뜨렸다"는 신호다.

README 에서는 버전을 전부 걷어내고 `jangbogo-x.y.z.jar` 자리표시자로 바꿨다. 수동 등록 절차에는 **3-1 JAR 이름 확인** 단계를 새로 넣었다(`dir ..\jangbogo-*.jar` 와 XML 대조).

#### install.bat 의 동기화는 그대로 둔다

두 장치는 서로를 대체하지 않는다.

- `packageDist` 치환 → **ZIP 을 만든 시점**의 정합성을 보장한다.
- `install.bat` 동기화 → **JAR 만 갈아끼우고 재설치하는 폴더**의 정합성을 보장한다.

앱만 새 JAR 로 교체한 뒤 `install.bat` 을 다시 도는 것은 실제로 있는 운용이라, 후자를 빼면 드리프트가 되살아난다. `ServiceDescriptorTest` 가 이 단계의 존재도 함께 감시한다.

#### 검증

`./gradlew packageDist` 로 실제 ZIP 을 만들어 확인했다. 앱은 기동하지 않았다.

```
service/jangbogo-service.xml:
  <arguments>-Xms256m -Xmx1024m -jar "%BASE%\..\jangbogo-0.11.2.jar" --service</arguments>
ZIP 최상단: jangbogo-0.11.2.jar
```

두 이름이 일치한다.

#### 남은 것

`bat\clean_build.bat:52,55,56` 이 `jangbogo-0.5.0.jar` 을 적고 있다. 개발용 스크립트라 배포본에 들어가지 않고 릴리스 경로에 영향이 없어 이번 범위에서 제외했다. 별건으로 정리 대상이다.

---

### [2026-07-29 09:40] Phase 3-2 — 수집 주기 하한 코드 강제 (v0.11.2)

#### 작업 개요

3순위로 판정한 Phase 3-2 를 구현했다. 720분 적용은 이미 DB 에 되어 있었고, 미착수였던 **하한 코드 강제**가 이번 작업이다.

#### 배경

주기가 짧을수록 같은 계정으로 로그인을 반복하게 되고, 그것이 곧 봇 차단을 자초하는 트래픽 프로파일이다. 그런데 하한 검증이 코드 어디에도 없었다 — 화면은 `min="0"`, 서버는 `> 0` 검사뿐이었다. 화면의 `min` 속성만 바꿔서는 API 직접 호출을 못 막는다.

실제로 수정 전 seq=1 은 **10분** 주기였다(Phase 1 에서 720분으로 변경). 이론적 위험이 아니라 이미 한 번 일어났던 상태다.

#### 하한 값 — 360분

계획서 743행이 "하한(360분)과 기본값(720분)의 관계도 함께 확정 필요"(새 질문 8)로 남겨 둔 값을 그대로 채택했다.

- 하한을 720 으로 두면 사용자가 주기를 조금도 조절할 수 없다. 720 은 **기본값**이지 **최소값**이 아니다.
- 그 절반인 360분(1일 4회)을 넘지 말아야 할 선으로 둔다.
- `0` 은 계속 유효하다 — "자동수집 안 함"을 뜻하는 기존 의미다. 따라서 거부 구간은 `0 < 값 < 360` 뿐이다.

**하한은 사용자 설정이 아니라 안전장치다.** 그래서 화면에 노출하지 않고 시스템 프로퍼티 `jangbogo.collect.min-interval-minutes` 로만 바꿀 수 있게 했다(`jangbogo.localdb.url` 과 같은 방식). 개발 중 짧은 주기로 시험하려면 `-Djangbogo.collect.min-interval-minutes=10` 으로 기동한다. 바꾸기 번거로운 것이 의도다.

#### 강제 지점 3곳

계획서 수용 기준이 "UI 와 서버 양쪽에서 하한 미만 값 저장이 거부된다"인데, 실측해 보니 **한 곳이 더 필요했다.**

| 지점 | 동작 |
|---|---|
| 화면 (`index.html`) | 저장 전에 검사하고 사유를 알린다. `min`/`placeholder` 도 함께 손봤다 |
| 서버 저장 (`AdminController`, DAO 2곳) | 거부하고 사유를 반환한다 |
| **스케줄 등록** (`MallSchedulerService`) | 경고를 남긴 뒤 하한으로 올려 스케줄한다 |

세 번째가 필요한 이유: **저장 시점에 거부해도 그 이전에 들어간 값은 DB 에 그대로 남아 있다.** seq=1 의 10분이 실제 사례다. 검증만 추가하고 끝내면 기존 값은 계속 그대로 쓰인다. 조용히 클램프하지 않고 경고를 남기는 것도 같은 이유다 — 값이 정정된 것이 아니라 런타임에 덮인 것이므로 사용자가 알아야 한다.

DAO 를 강제 지점에 넣은 것은 쓰기 경로가 `saveAutoCollectFlags` 와 `updateCollectInterval` 둘이기 때문이다. Controller 에만 두면 다른 호출자가 생겼을 때 뚫린다. Controller 검증은 사용자에게 친절한 메시지를 주기 위한 것이고, DAO 가 실제 방벽이다.

스케줄 경로는 `scheduleMall` 하나로 수렴한다(호출부 3곳: `StartupTasks`, `AdminController` 2곳). 그래서 그 안에서 한 번만 막으면 된다. 호출부가 없는 `scheduleMallImmediate` 에도 같은 규칙을 넣어 뒀다.

#### 검증

`CollectIntervalPolicyTest` 10건 — 경계값(0/1/10/359/360/720), null·음수, 클램프 동작, 시스템 프로퍼티 재정의, 잘못된 프로퍼티 값의 기본값 복귀, 거부 메시지 내용.

전체 59건 / 실패 0. 기준선 DB md5 `909dfe48…` 불변. 앱 기동·실계정 접근 0.

#### 변경 파일

| 파일 | 구분 | 내용 |
|---|---|---|
| `svc/util/CollectIntervalPolicy.java` | 신규 | 하한 정책 |
| `dao/JbgMallDataAccessObject.java` | 수정 | 쓰기 경로 2곳 강제 |
| `ctrl/AdminController.java` | 수정 | 저장 요청 거부 + 사유 반환 |
| `svc/MallSchedulerService.java` | 수정 | 스케줄 시 클램프 |
| `templates/index.html` | 수정 | 저장 전 검사 + 입력 속성 |
| `src/test/java/.../svc/CollectIntervalPolicyTest.java` | 신규 | 회귀 테스트 10건 |

#### 남는 것

- **기존 DB 값은 자동 정정되지 않는다.** 현재 seq=1·2 는 720분이라 문제 없지만, 하한 미만 값이 남아 있으면 스케줄 시 클램프될 뿐 DB 는 그대로다. 설정 화면에서 저장하면 정정된다.
- 새 질문 8 의 나머지 절반("720분이 전 몰 공통인가 몰별인가")은 여전히 열려 있다. 현재는 몰별 컬럼이므로 몰별이고, 하한만 공통이다.

---

### [2026-07-29 09:00] BASELINE 2026-07-29 — Phase 2 종결 (v0.11.1)

#### 작업 개요

Phase 2 의 잔여 기록 항목(2-4, 2-9)을 처리해 **Phase 2 를 종결**한다. 함께 판단 대기 7(jackson 스큐)을 해소하고 `CLAUDE.md` 의 깨진 참조를 정리했다.

---

#### BASELINE 2026-07-29 (2-9)

**이 시점의 값이 이후 모든 Phase 의 무회귀 판정 기준이다.**

| 항목 | 값 |
|---|---|
| `jbg_order` | **22** (Emart 15 + Oasis 7) |
| `jbg_item` | **168** (117 + 51) |
| DB md5 | `909dfe48822aea77bf4f6806a37073ac` |
| `jbg_collect_log` | 6행 (seq 1·3 은 Phase 1 수정 전 기록 — **보존 결정**) |
| 백업 | `db/backup/jangbogo-dev.db.baseline-2026-07-28` (초기화 전 주문 7 / 아이템 53) |
| 몰 상태 | seq=1 ssg, seq=2 oasis 모두 `account_status=1`·`auto_collect=1`·**720분**. seq=3 hanaro 미연결 |
| 자동화 판정 단위 | 테스트 49건 (실패 0 / 스킵 15) |

**판정 방식**: 회귀는 **실계정 재수집이 아니라 테스트 통과 여부와 위 수치 비교**로 판정한다. Emart 파서는 `EmartReceiptParserTest` 의 합성 픽스처가, FTP 전송 실패 경로는 `FtpPendingQueueTest` 가, jackson 정렬은 `YamlMapperCompatibilityTest` 가 각각 담당한다.

DB md5 는 이번 세션 내내(수집 가드 검증 → 파서 픽스처 → FTP 큐 → jackson 정렬) 한 번도 바뀌지 않았다. 모든 작업이 실계정·실 DB 접근 0 으로 수행됐다는 뜻이다.

---

#### 호스트 환경 기록 (2-4)

| 항목 | 값 |
|---|---|
| OS | Windows 11 Pro (10.0.22621) |
| 설치 Chrome | **150.0.7871.187** |
| chromedriver 캐시 최대 | **150.0.7871.124** |
| `app_bound_encrypted_key` | **존재** (Chrome `Local State`) |
| `encrypted_key` | 존재 |

**계획서 정정 2건**

1. **4A-0 은 이미 충족됐다.** 계획서는 "chromedriver 캐시 최대 148, 설치 Chrome 150 → 실행 파일 다운로드이므로 사용자 승인 필요"로 적고 있으나, 캐시에 `150.0.7871.124` 가 이미 있다. 이전 세션에서 Selenium Manager 가 받아 둔 것이다(새 질문 5 의 "자동 다운로드 허용" 결정에 따른 결과). chromedriver 는 메이저 버전으로 매칭되므로 150.0.7871.124 ↔ Chrome 150.0.7871.187 조합은 유효하다. **Phase 4A 의 T1~T4 는 다운로드 승인 없이 착수 가능하다.**
2. **`app_bound_encrypted_key` 가 존재한다.** Chrome 127+ 의 App-Bound Encryption 이 활성이라는 뜻이다. 쿠키 복호화가 이를 기록한 애플리케이션에 묶이므로 **쿠키를 꺼내 쓰는 방식은 성립하지 않는다.** Phase 5 가 쿠키 추출이 아니라 **프로필 재사용**을 택한 것이 환경적으로도 옳다는 근거가 된다.

---

#### 판단 대기 7 해소 — jackson 스큐 정렬

`build.gradle` 이 `jackson-dataformat-yaml` 만 2.15.3 으로 못박아 BOM 해석값(2.19.2)과 4개 마이너가 어긋나 있었다. 버전 선언을 제거해 정렬했고, 결과적으로 `core`/`databind`/`dataformat-yaml`/`bom` 모두 2.19.2 로 수렴했다(강제 표기 `->` 소멸).

`YamlMapperCompatibilityTest` 4건이 이를 감시한다. 프로젝트가 YAML 을 다루는 세 방식(`JangbogoConfig` 의 기본 팩토리 읽기, `MallAccountYmlService` 의 builder + `findAndRegisterModules` 왕복, `ExportService` 의 `MINIMIZE_QUOTES` 쓰기)을 각각 덮고, 모듈 버전 일치 검사를 더했다.

**가드의 실효를 실증했다** — 수정 전 상태에서 버전 검사가 `expected: <2.19> but was: <2.15>` 로 정확히 실패했다. 나머지 3건은 스큐 상태에서도 통과했는데, 이것이 그동안 드러나지 않은 이유다. 관측된 장애는 없었으므로 잠재 위험이었다.

이로써 **판단 대기 9(의존성 스큐 상시 탐지)도 함께 해소**됐다. 파일 diff 가 아니라 테스트가 감시한다.

---

#### `CLAUDE.md` 깨진 참조 정리

`CLAUDE.md` 가 미커밋 문서(미결 4)를 참조해 공개 저장소에서 깨진 링크가 돼 있었다. 참조를 제거하면서 PRIVATE 저장소명과 특정 PC 절대경로 서술도 함께 걷어냈다. 내용상 필요한 것은 "외부 패키지를 import 하지 않는다"는 방침뿐이고, 그 경위는 저장소에 없는 내부 문서에 있다는 사실만 남겼다.

---

#### Phase 2 항목 최종 현황

| # | 상태 |
|---|---|
| 2-1 | **완료** — 재정의 후 측정·기록. 부산물로 jackson 스큐 발견 |
| 2-2 | **완료** — seq=1·2 실측 (seq=3 은 `account_status=0` 미연결) |
| 2-3 | **완료(Emart)** — 마스킹 규칙 = 합성 픽스처. 나머지 몰은 2-8 과 함께 보류 |
| 2-4 | **완료** — 위 호스트 환경 기록 |
| 2-5 | **보류** — 배포 baseline 실측. 운영이 콘솔 실행으로 확정돼 우선순위가 내려갔다 |
| 2-6 | **보류** — 쿠팡 메일 샘플. Phase 4B 게이트 입력이라 4B 착수 시 함께 |
| 2-7 | **보류** — jiniebox 운영 시드 확인. §9-2 와 묶인다 |
| 2-8 | **완료(Emart)** — 파서 픽스처 8건. Oasis·Hanaro·SSG 는 순수 파서 부재로 판단 대기 8 |
| 2-9 | **완료** — 위 BASELINE |

**Phase 2 를 종결한다.** 목표였던 "수정된 코드 기준의 무회귀 판정 단위"는 확보됐다. 보류 3건(2-5·2-6·2-7)은 Phase 2 의 목표에 필수가 아니며 각각 다른 Phase 의 선행 작업으로 이관한다.

#### 변경 파일

| 파일 | 구분 | 내용 |
|---|---|---|
| `build.gradle` | 수정 | jackson 버전 선언 제거, 버전 0.11.1 |
| `src/test/java/.../svc/YamlMapperCompatibilityTest.java` | 신규 | jackson 호환성·버전 일치 감시 4건 |
| `CLAUDE.md` | 수정 | 깨진 참조 + 절대경로 서술 제거 |

---

### [2026-07-29 08:20] Phase 3-1 — FTP 전송 실패분 보류 큐 (v0.11.0)

#### 작업 개요

우선순위 1위로 판정한 Phase 3-1 을 구현했다. 조사 과정에서 같은 경로의 부수 결함 2건이 드러나 함께 고쳤고, 그 결함이 디스크에 남긴 고아 파일 6건도 정리했다.

#### 배경 — 무엇이 실제로 유실됐나

`MallSchedulerService.processFtpUpload` 의 `finally` 가 업로드 성공 여부와 **무관하게** 전송 파일을 삭제했다. 실패는 `logger.warn` 한 줄이 전부였다.

핵심은 내보내기가 **증분**이라는 점이다. `exportToJinieboxFileBySeqList(savePath, newOrderSeqs)` 는 그 회차 신규 주문만 담는다. 다음 회차는 자기 신규분만 보내므로 실패한 회차의 주문을 대신 보내주지 않는다.

**정정**: 조사 초기에 "영구 데이터 유실"로 판단했으나 과했다. 주문 자체는 `jbg_order`/`jbg_item` 에 영구 보존되고, `save_path` 에 로컬 내보내기 JSON 도 남는다(11개 확인, 2026-02-27~07-29). 실제로 잃는 것은 **jiniebox 로의 전달**과 **전달 실패 사실의 기록**이다. 후자가 본질이다 — 배송 상태가 코드 어디에도 없어 무엇이 미도달인지 알 방법이 없었다.

`save_path` 실측이 이를 뒷받침했다.

| 항목 | 수 | 의미 |
|---|---|---|
| `jangbogo_orders_*.json` (로컬 내보내기) | 11 | 정상 |
| `jangbogo_orders_*_ftp.json` (전송용) | **0** | 성공했는지 실패 후 버려졌는지 **구분 불가** |
| `jangbogo_status_*_ftp.json` (고아) | **6** | 정상이면 0 |
| `last_export_time` | `none` | 11번 내보내고도 미갱신 |

전송용 고아가 0 이라는 사실이 오히려 문제였다. **증거의 부재 자체가 결함**이다.

`jbg_export_config` 는 이 경로가 활성임을 보여줬다 — 주소·아이디·비밀번호 모두 설정, `ftp_encrypt_enabled=1`, `auto_save_enabled=1`, `save_to_jiniebox=1`. 앱이 정지 상태라 출혈 중은 아니었고, 기동하는 순간 활성화되는 상태였다.

#### 설계 판단 — 왜 파일 큐인가

대안은 `jbg_order` 에 배송 상태 컬럼을 두는 것이었다. 파일 큐를 택한 이유:

- DB 방식은 `jbg_order` ALTER 가 필요하고 미결 3(SQLite ALTER 편도)과 얽힌다.
- **수신측 jiniebox 의 멱등성이 미확인**이다. 파일 큐는 "보낸 적 없는 바이트만" 재전송하므로 중복 위험이 가장 작다.
- 기준선을 막 확보한 상태에서 변경면이 가장 좁다.

구현상 유의점 3가지:

1. **파일명을 바꾸지 않는다.** `FtpUploadUtil:68` 이 원격 파일명을 `localFile.getName()` 으로 쓴다. 이름이 바뀌면 수신측에 다른 이름으로 올라간다. 충돌할 때만 접두사·확장자 체인을 유지한 채 `_r1` 을 끼워 넣는다.
2. **첫 실패에서 중단한다.** `FtpUploadUtil` 의 연결 타임아웃이 15초라, 보류 50건이 쌓인 상태에서 FTP 가 죽어 있으면 12분 넘게 수집 사이클을 붙잡는다. 같은 서버이므로 첫 건이 실패하면 나머지도 실패한다.
3. **폐기는 반드시 경고로 남긴다.** 상한 초과분을 조용히 버리면 지금 고치려는 결함과 같은 종류가 된다.

상태 파일(`jangbogo_status_*`)은 큐에 넣지 않는다. "신규 없음" 하트비트라 뒤늦게 보내면 수신측 시각을 오도한다.

**사용자 결정**: 상한 50건 / 14일(720분 주기·2몰 기준 약 56회차분). 암호화가 꺼진 구성에서는 평문도 보류한다 — 사용자가 평문 업로드를 택한 것과 노출 수준이 같고 기간 상한이 체류를 제한한다. 암호화가 켜져 있으면(현재 설정) 암호문만 보류되고 평문 원본은 즉시 삭제되므로 평문이 적체되지 않는다.

#### 함께 고친 부수 결함 2건

1. **상태 파일 이중 생성** — `processFileExport:440` 과 `processFtpUpload:478` 이 각각 `createEmptyStatusFile` 을 호출했다. 보통은 같은 초라 파일명이 같아 한 개로 겹치지만, 초 경계를 넘으면 앞의 것이 업로드되지도 삭제되지도 않고 남았다. 디스크의 고아 6건(2026-02-27~05-21)이 그 증거다. 생성을 `processFtpUpload` 한 곳으로 모았다.
2. **`auto_save_enabled=0` 인데도 로컬 파일을 씀** — `exportOrdersBySeqList` 를 무조건 실행하고 `shouldAutoSave` 로는 로그만 감쌌다. 현재 `auto_save=1` 이라 발현되지 않았을 뿐이다.

추가로 FTP 자격증명 확인을 전송 파일 생성보다 **앞으로** 옮겼다. 보낼 수 없는 상태에서 파일을 만들었다가 지우던 순서를 뒤집은 것이다.

#### 고아 파일 정리

`jangbogo_status_*` 6건(평문 5 + 암호문 1)을 삭제했다. 내용은 `{"orders":[],"status":"no_new_orders","timestamp":"..."}` 로 구매 데이터가 없음을 확인한 뒤 지웠다. 되돌릴 수 있도록 세션 스크래치패드에 사본을 남겼다.

#### 검증

`FtpPendingQueueTest` 8건 — FTP 서버·네트워크 없이 검증한다. 업로드 수행자를 함수 인터페이스로 주입할 수 있게 만든 이유가 이것이다.

| 테스트 | 확인하는 것 |
|---|---|
| 실패분 이동 | 삭제되지 않고 `pending/` 으로, 이름·내용 보존 |
| 재전송 성공 | 보류분 제거, 오래된 것부터(FIFO) |
| 재전송 실패 | 파일 유지 + **첫 실패에서 중단** |
| 업로더 예외 | 실패로 취급하고 보류분 보존 |
| 건수 상한 | 오래된 것부터 폐기 |
| 기간 상한 | 초과분 폐기 |
| 이름 충돌 | 둘 다 보존, 접두사·확장자 유지 |
| 빈 큐 | 업로드 시도 자체를 하지 않음 |

전체 45건 / 실패 0. 기준선 DB md5 `909dfe48…` 불변. 앱 기동·실계정 접근 0.

#### 변경 파일

| 파일 | 구분 | 내용 |
|---|---|---|
| `svc/util/FtpPendingQueue.java` | 신규 | 보류 큐 |
| `svc/MallSchedulerService.java` | 수정 | 보류 큐 배선, 부수 결함 2건, 자격증명 확인 순서 |
| `src/test/java/.../svc/FtpPendingQueueTest.java` | 신규 | 회귀 테스트 8건 |
| `build.gradle` | 수정 | 버전 0.11.0 |

#### 남는 것

- **배송 상태 자체는 여전히 없다.** 보류 큐는 "전송 실패분을 잃지 않는다"까지만 보장한다. 어떤 주문이 언제 수신측에 도달했는지를 알려면 별도 상태 추적이 필요하다. Phase 3-5(하트비트)와 함께 볼 항목이다.
- `last_export_time` 이 이 경로에서 갱신되지 않는다. 판단 대기로 올린다.
- 보류 건수가 로그에만 남는다. 대시보드·`jbg_collect_log` 노출은 Phase 3-5 와 겹치므로 이번에는 하지 않았다.

---

### [2026-07-29 07:10] Phase 2 — 2-1 재정의 및 2-8 파서 픽스처 단위테스트 (v0.10.5)

#### 작업 개요

Phase 2(기준선 동결 · 파서 픽스처) 중 **2-1 을 재정의**하고 **2-8(파서 픽스처 단위테스트)을 완료**했다. 2-8 이 Phase 2 의 실질 작업이었다.

#### 2-1 재정의

원래 정의는 "`gradlew dependencies --configuration runtimeClasspath > baseline-deps.txt` — 선언 4.25.0 vs 실효 4.31.0 **스큐를 기록**" 이었다. 그런데 Phase 1 에서 `ext['selenium.version'] = '4.31.0'` 으로 **스큐 자체를 제거**했으므로 기록할 대상이 사라졌다.

**재정의**: 목적은 "스큐 기록"이 아니라 **"실효 좌표 동결 + 스큐 재발 탐지"** 다. 스큐라는 결함 유형은 없어지지 않았고, 이후 Phase 가 의존성 드리프트를 회귀로 오인하지 않으려면 실효 해석 상태를 알고 있어야 한다.

**측정 결과** (`runtimeClasspath`, 240줄)

| 항목 | 결과 |
|---|---|
| selenium 모듈 | **16개 전부 4.31.0 으로 일원화** — Phase 1 조치의 실효 확인 |
| typed DevTools 아티팩트 | `selenium-devtools-v133`·`v134`·`v135` **만** 존재. Chrome 150 용이 없다는 사실이 의존성 그래프로 확인된다 (기동 시 CDP 경고의 근거) |
| **새로 발견한 스큐** | `jackson-dataformat-yaml` 이 **2.15.3**, `jackson-core`·`jackson-databind` 는 **2.19.2** |

**jackson 스큐 상세**: `build.gradle:72` 가 `jackson-dataformat-yaml:2.15.3` 을 명시 고정하는데, Spring Boot BOM 은 나머지 jackson 모듈을 2.19.2 로 해석한다. jackson 은 모듈 버전 일치를 전제하는데 4개 마이너가 갈려 있다. 그리고 이 경로는 `JangbogoConfig`(기동 설정 로드) · `MallAccountYmlService`(계정 자격증명) · `ExportService` 가 모두 쓴다. 현재 관측된 장애는 없다.

**동결 산출물에 대한 판단**: 240줄 덤프를 파일로 커밋하는 안은 채택하지 않았다. 아무도 diff 하지 않으면 썩는 파일이 하나 느는 것뿐이고, 재생성 명령이 한 줄이라 언제든 다시 뜰 수 있다. 대신 위 측정 결과를 이 항목에 고정한다. 지속적 탐지가 필요하다면 파일 diff 가 아니라 **버전 일치를 검사하는 테스트**가 맞다 — 판단 대기로 올린다.

#### 2-8 파서 픽스처 단위테스트

**대상 선정**: `Emart.parseReceipt(String)` 하나로 좁혔다.

- `parseReceipt` 는 `public` 이고 인자가 문자열뿐이며 `new Emart(id, pass)` 는 필드 대입만 한다. **리팩터링 0 으로 즉시 테스트 가능**하고, 코드베이스에서 가장 분기가 많은 파서다.
- 반면 **Oasis · Hanaro · SSG 는 순수 파싱 함수가 없다.** `navigatePurchased(WebDriver)` / `parseDetailPage(WebDriver)` 안에서 네비게이션 · 창 전환 · DOM 추출이 뒤섞여 있다. 브라우저 없이 검증하려면 Selenium 목킹(자기 목을 검증하는 꼴이라 무회귀 가치가 낮다) 아니면 순수 파서 추출 리팩터링(기준선을 막 확보한 코드를 건드리는 실질 리스크)이 필요하다. 판단 대기로 올린다.

**픽스처 성격 — 합성으로 확정**: §9-13(픽스처 개인정보)이 미해소이고 이 저장소는 PUBLIC 이다. 실제 영수증은 상품명·수량·금액·구매일시·매장·바코드가 모두 든 구매 이력 그 자체라 부분 마스킹으로는 재식별 위험이 남는다. 실물을 마스킹하는 대신 **파서 분기를 덮도록 손으로 지은 데이터**를 쓴다. 분기 커버리지는 확보하면서 개인정보는 애초에 존재하지 않게 하는 것이 목적이다. 규칙은 `src/test/resources/fixtures/emart/README.md` 에 명문화했다. 이로써 2-3(픽스처 저장 + 마스킹 규칙 확정)의 마스킹 규칙 부분도 Emart 범위에서 확정됐다.

**작성 절차**: 단정문을 코드 독해가 아니라 **실제 파서 출력**에 근거시켰다. 픽스처를 만든 뒤 임시 덤프 테스트로 8종의 실제 결과를 먼저 관측하고, 그 값으로 단정문을 썼다. 특성화 테스트(characterization test)이므로 "옳은 값"이 아니라 "현재 값"을 고정한다.

**픽스처 8종과 덮는 분기**

| 픽스처 | 분기 |
|---|---|
| `basic-4col` | 표준 4열 (`arrSize == 4`) |
| `starred-5col` | 선행 기호가 별도 컬럼인 5열 (`arrSize >= 5`), 상품명이 인덱스 1 로 밀림 |
| `wrapped-name-first` | `combineExtraPattern01` — 상품명 줄 → 정보 줄 |
| `wrapped-data-first` | `combineExtraPattern01` — 정보 줄 → 상품명 줄 |
| `discount-row` | 단가에 `-` 가 있는 할인행 스킵 |
| `summary-rows` | `cancelKeys` 요약행 6종 + 숫자만 있는 행 + 빈 행 필터 |
| `empty-items` | 구분선 사이가 비어 있음 (예외 없이 빈 배열) |
| `short-row` | 4열 미만 행 |

**테스트가 고정한 현행 동작 2건 (결함이지만 이번에 고치지 않음)**

1. **줄번호가 상품명에 남는다** — `wrapped-name-first` 에서 결과 상품명이 `"01 테스트과자"` 다. `combineExtraPattern01` 이 줄번호를 분리하지 않고 상품명 컬럼을 통째로 옮긴다.
2. **4열 미만 행이 빈 객체로 목록에 들어간다** — `Emart.parseReceipt` 의 `itemArr.add(itemJson)`(:377)이 컬럼 수 분기 밖에 있다. 어느 분기에도 걸리지 않은 행이 `{}` 로 추가된다. **지금은 무해하다** — `MallOrderUpdaterRunner:172` 의 `item.has("name")` 가드가 걸러내므로 `jbg_item` 에는 도달하지 않는다. 다만 하류 가드에 의존하고 있다는 뜻이다.

두 건 모두 Phase 2 의 목적이 "동결"이지 "수정"이 아니므로 손대지 않았다. 테스트 주석에 `현행 동작` 으로 표시했고, 고칠 때 테스트가 깨지면 그것은 회귀가 아니라 의도된 변경이다.

**결과**: 8건 전부 통과. 브라우저·네트워크·DB 접근 0.

#### 변경 파일

| 파일 | 구분 | 내용 |
|---|---|---|
| `src/test/java/.../parser/EmartReceiptParserTest.java` | 신규 | 파서 픽스처 테스트 8건 |
| `src/test/resources/fixtures/emart/*.txt` | 신규 | 합성 픽스처 8종 |
| `src/test/resources/fixtures/emart/README.md` | 신규 | 데이터 규칙(마스킹 규칙) + 영수증 형식 |
| `build.gradle` | 수정 | 버전 0.10.5 |

#### Phase 2 항목 현황

| # | 상태 |
|---|---|
| 2-1 | **완료** (재정의 후 측정·기록) |
| 2-2 | 완료 (이전 세션 실측, seq=1·2. seq=3 하나로는 `account_status=0` 미연결) |
| 2-3 | **Emart 범위 완료** — 마스킹 규칙 확정 + 픽스처 저장. 나머지 몰은 2-8 과 함께 보류 |
| 2-8 | **Emart 완료.** Oasis·Hanaro·SSG 는 순수 파서 부재로 판단 대기 |
| 2-4·2-5·2-6·2-7·2-9 | 미착수 |

#### 판단 대기 (추가)

7. **jackson 모듈 버전 스큐** — `build.gradle:72` 의 `jackson-dataformat-yaml:2.15.3` 명시 고정을 제거해 BOM(2.19.2)에 맞출 것인가. 한 줄 삭제로 정렬되지만 기준선을 막 확보한 상태에서 런타임 의존성을 바꾸는 일이다.
8. **Oasis·Hanaro·SSG 파서 테스트 방식** — 순수 파서 추출 리팩터링 / Selenium 목킹 / 보류 중 택일.
9. **의존성 스큐 상시 탐지** — jackson·selenium 모듈 버전 일치를 검사하는 테스트를 둘 것인가.

---

### [2026-07-29 06:30] 판단 대기 B-1 해소 — 기동 수집 프로퍼티 가드 및 테스트 DB 격리 (v0.10.4)

#### 작업 개요

`[2026-07-29 00:10]` 항목의 "남은 판단 대기" 1번(`./gradlew test` 가 실계정 수집을 실행한다)을 사용자 승인 후 해소했다. 조사 과정에서 같은 뿌리의 두 번째 결함(테스트가 기준선 DB 파일을 직접 연다)이 드러나 함께 처리했다.

#### 배경

`JangbogoApplicationTests` 는 `@SpringBootTest` 로 전체 컨텍스트를 로드한다. 그러면 `StartupTasks.onApplicationReady()` 가 발화하고 `runInitialCollection()` 이 실행되는데, 이 호출은 **동기**다(`MallSchedulerService.runOneTimeCollection` → `runCollectForMall` 직접 호출). 즉 테스트가 실계정 로그인과 브라우저 수집이 끝날 때까지 블록된다. CI 는 `build.yml`(windows)·`ci.yml`(ubuntu) 양쪽에서 `./gradlew test` 를 돌리고 `gradlew build` 도 test 를 포함하므로, push 1회당 4번 시도된다.

조사 중 확인한 것:

- `ApplicationReadyEvent` 진입점은 `StartupTasks` **하나뿐**이다(전체 grep).
- `config/jbg_config.yml` 의 `auto-update-items-on-startup: true` 는 **코드에서 참조되지 않는 죽은 설정**이다.
- `JangbogoConfig` 는 Spring 프로퍼티가 아니라 `config/jbg_config.yml` 을 직접 읽는다. 따라서 이 파일로는 프로퍼티 가드를 만들 수 없어 Spring 프로퍼티를 신설했다.
- `LocalDBConnection` 이 접속 문자열을 하드코딩하고 있어 `src/test/resources/application.yml` 의 `spring.datasource.url: jdbc:sqlite::memory:` 가 **DAO 계층에는 아무 효과가 없었다**(그 설정은 `spring.sql.init` 경로에만 쓰인다).

#### 상세 내용

**1. 기동 수집 가드 — `jangbogo.startup.collect.enabled`**

`StartupTasks` 에 `@Value("${jangbogo.startup.collect.enabled:false}")` 를 두고 `runInitialCollection()` + `restoreIndividualSchedules()` 를 감쌌다. `migrateCollectLogSchema()` 와 스크린샷 정리는 가드 **밖**이다 — "앱은 띄우되 수집만 끈다" 를 해도 스키마는 최신이어야 하기 때문이다.

코드 기본값을 `false` 로 둔 것이 설계의 핵심이다. 프로퍼티 소스가 없는 컨텍스트(테스트 슬라이스, 앞으로 추가될 CI 잡, 컨텍스트만 띄우는 외부 도구)에서 자동으로 안전한 쪽으로 떨어지고, 수집을 켜는 것이 명시적 행위가 된다. `src/main/resources/application.yml` 이 `true` 를 선언하므로 콘솔 운영 동작은 바뀌지 않는다.

건너뛸 때 info 로그를 남겨 "왜 수집이 안 도나" 진단 비용을 없앴다.

**2. 테스트 DB 격리 — `jangbogo.localdb.url`**

`LocalDBConnection` 의 `DB_URL` 을 `System.getProperty("jangbogo.localdb.url", "jdbc:sqlite:./db/jangbogo-dev.db")` 로 바꾸고, `build.gradle` 의 `test` 태스크에서만 `build/test-db/jangbogo-test.db` 를 가리키게 했다. 기본값이 기존 값이라 운영 동작은 불변이고 DAO 는 한 줄도 손대지 않았다.

이것이 가드와 **독립적인 2차 방어선**이다. 가드가 어떤 이유로 실패해도 테스트가 보는 DB 에는 `jbg_mall` 이 없으므로 수집 대상이 0건이고 실계정에 도달할 수 없다.

**3. 검증 (`./gradlew test` 실측)**

| 확인 | 결과 |
|---|---|
| 기준선 DB md5 | `909dfe48822aea77bf4f6806a37073ac` — 실행 전후 동일, mtime·크기까지 불변 |
| `jbg_order` / `jbg_item` | 22 / 168 유지 |
| WAL·shm 잔여 | 없음 |
| 격리 DB | `build/test-db/jangbogo-test.db` 생성, 테이블은 `jbg_collect_log`(0행)뿐 |
| 가드 로그 | `06:27:11.704 기동 수집 비활성 … 건너뜁니다` |
| 브라우저·로그인 시도 | 0건 |
| 테스트 | 29건 / 실패 0 / 오류 0 (스킵 15 — `FtpTlsConnectionTest` `@Disabled` 14 포함) |
| 소요 | 27초 (수집이 돌면 수 분) |

#### 변경 파일

| 파일 | 구분 | 내용 |
|---|---|---|
| `boot/StartupTasks.java` | 수정 | `startupCollectEnabled` 가드 |
| `dao/LocalDBConnection.java` | 수정 | `jangbogo.localdb.url` 오버라이드 |
| `src/main/resources/application.yml` | 수정 | `jangbogo.startup.collect.enabled: true` |
| `src/test/resources/application.yml` | 수정 | 같은 키 `false` + DataSource 적용 범위 주석 |
| `build.gradle` | 수정 | 버전 0.10.4, `test` 태스크 DB 격리 |

#### 남은 판단 대기 항목

`[2026-07-29 00:10]` 항목의 1번은 해소. 2·3·4 는 그대로 남는다.

2. Emart `navigateReceipt` 의 `StaleElementReferenceException` — 간헐적 발생.
3. 영수증 바코드(`#barcodeTargetRec`) 미인식 2건 — 스킵 동작은 올바르나 원인 미조사.
4. `jbg_collect_log` 의 수정 전 기록 2행(seq 1·3) — **보존으로 결정**(2026-07-29). Phase 1 수정의 before 증거이고, 회귀 판정은 `jbg_order`/`jbg_item` 수치로 하므로 영향이 없다.

새로 기록하는 항목:

5. `dev/JangbogoConfigExample.java:97` 이 `JangbogoConfig` 로 DB 경로를 별도 조립한다. 개발 예제 클래스라 런타임 경로가 아니지만, DB 경로 출처가 두 곳이라는 사실은 남아 있다.
6. `CLAUDE.md` 가 `doc/CROSS-PROJECT-PROPAGATION.md` 를 참조하는데 이 문서는 미결 4(PUBLIC 저장소 공개 판단) 때문에 미커밋이다. 공개 저장소에서 깨진 참조가 된다.

---

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

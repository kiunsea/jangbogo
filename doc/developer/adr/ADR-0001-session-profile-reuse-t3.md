# ADR-0001 — 세션 프로필 재사용의 성립 조건 (T3 판정)

- 상태: 확정
- 일자: 2026-08-05
- 대상: Phase 4A (엔진 판정 게이트) · Phase 5 (몰별 옵트인 세션 프로필 재사용)

## 배경

"사람이 브라우저에서 1회 로그인해 둔 프로필을 자동 수집이 재사용한다"는 전략을 세웠다.
셀렉터 기반 로그인이 불안정한 것을 근본에서 해결하려는 것이었다.

Phase 4A 는 이 전략의 성립 여부를 **엔진 질문**으로 규정했다 — "Selenium 단독으로 프로필 재사용이
되는가, 아니면 Playwright(Phase 5B)가 필요한가".

## 측정

브라우저를 실제로 띄워 T1·T2·T3·T4 를 수행했다. 산출물은 `build/probe-artifacts/` 에 있다
(개인정보가 담겨 커밋하지 않는다). 재현 방법은 `SessionProfileReuseProbe` javadoc 참조.

### T1 — 자동화 표식 제거: PASS

실제 `chrome.exe` 명령줄을 덤프해 적용 전후를 비교했다.

| | 적용 전 | 적용 후 |
|---|---|---|
| `--enable-automation` | 있음 | 없음 |
| `--test-type=webdriver` | 있음 | 없음 |

같은 덤프에서 `--user-data-dir=<경로>` 가 실제 프로세스 명령줄에 도달한 것도 확인했다.

### T2 — 지문 동등성: PASS

같은 식을 세 조건에서 재고 비교했다(외부 사이트 접속 0).

```
(a) 순정 chrome.exe       : "webdriver":"false"
(b) Selenium 기본         : "webdriver":"true"
(c) Selenium+표식제거+CDP : "webdriver":"false"   ← (a) 와 완전 일치
```

**여기서 마스킹 방식을 정정했다.** 흔한 레시피는 `navigator.webdriver` 를 `undefined` 로 지우지만,
그것은 이 속성이 자동화일 때만 존재하던 옛 Chrome 기준이다. 지금 Chrome 은 평소에도 이 값이 있고
`false` 다. `undefined` 로 지우면 순정과 **다른** 상태가 되어 오히려 눈에 띈다. 프로토타입이 아니라
인스턴스에 정의하는 것도 같은 이유로 구분 가능하다.

### T4 — chromedriver 기동: 문제 없음

`--remote-debugging-port=0` 없이도 기동된다. 덤프를 보면 chromedriver 150 이 그 인자를 스스로
붙이므로 pipe 전환 이슈는 이 환경에서 재현되지 않는다.

### T3 — 세션 재사용: **FAIL (ssg · oasis 모두)**

두 몰에서 각각 사람이 순정 Chrome 으로 로그인하고 창을 닫은 뒤 같은 프로필을 다시 열었다.

| | ssg | oasis |
|---|---|---|
| 로그인으로 새로 생긴 영속 쿠키 | `keepId`, `mbrLoginId` (아이디 저장용) | 16개 전부 분석·추적 쿠키(`_ga*`·`_fbp`·`wcs_bt` 등) |
| 인증으로 보이는 영속 쿠키 | 없음 | 없음 |
| **순정 Chrome** 재기동 → 회원 페이지 | 로그인 화면으로 밀림 | 로그인 화면으로 밀림 |
| **Selenium** 으로 같은 것 | 동일하게 밀림 | 동일하게 밀림 |

### 추가 측정 — Playwright · 세션 이관

위 결론이 난 뒤 Playwright 로도 재 봤다. 엔진이 변수가 아니라는 것을 추론이 아니라 측정으로 닫기 위해서다.
같은 로그인 한 번으로 두 가지를 갈랐다.

| 측정 | 방식 | 결과 |
|---|---|---|
| PW-2 | Playwright **프로필 재사용** | **실패** — Selenium·순정과 같다 |
| PW-3 | Playwright **`storageState` 주입** | **성공** — 프로필 없는 새 브라우저에 세션이 옮겨졌다 |
| ST-1 | **Selenium** 주입 (CDP `Network.setCookies`) | **성공** |
| ST-2 | **Selenium** 캡처(`Network.getAllCookies`) + 주입 왕복 | **성공** |

같은 엔진·같은 로그인·같은 몰에서 PW-2 는 실패하고 PW-3 은 성공했다. **갈린 것은 엔진이 아니라 방식이다.**
그리고 그 방식은 Selenium 으로도 그대로 된다 — ST-2 가 캡처·주입 양쪽을 기존 스택만으로 닫았다.

캡처된 쿠키가 원인을 확정해 준다. ssg 의 인증 쿠키(`LOGIN_YN`·`MEMBER_ID`·`MBR_ID_ED_NO`·`JSESSIONID`
(www·member 양쪽)·`FSID` 계열)가 **전부 세션 스코프**였다. 브라우저를 닫으면 사라지므로 프로필에 남을 수 없고,
살아 있을 때 뜨면 옮길 수 있다. ST-1 결과는 스크린샷으로도 확인했다(로그인된 마이페이지 구매내역 화면).

## 결정

**T3 의 FAIL 은 경로 A(Selenium)의 실패가 아니다. Phase 5B(Playwright) 를 상정할 근거가 되지 않는다.**

**Playwright 는 도입하지 않는다.** 유일하게 유효했던 방식(세션 이관)이 Selenium 으로 그대로 되므로
도입 근거가 없다. 계획서 수용 기준 5(`main` 의 `build.gradle` 에 Playwright 의존성 없음)와 결정 3 을 지킨다.

근거는 순정 Chrome 대조군이다. Selenium 을 빼고 사람이 쓰는 것과 똑같은 브라우저로 같은 프로필을
열어도 결과가 **동일**했다. 자동화가 걸러진 것이 아니라 **프로필에 유효한 세션이 없다.**
엔진을 바꿔도 같은 벽이다.

### 원인: 두 몰이 세션 단위 인증을 쓴다

로그인으로 남은 영속 쿠키가 어느 쪽도 인증 토큰이 아니었다. ssg 는 '아이디 저장'용 둘,
oasis 는 분석·추적용 열여섯. 즉 인증은 **세션 쿠키**로 이뤄지고, 브라우저를 닫으면 사라진다.

Chrome 설정으로 우회되는지 별도로 확인했다(`SessionCookieSurvivalProbe`, 실계정 불필요).
로컬 페이지가 세션 쿠키와 영속 쿠키를 하나씩 심고, 창을 **정상 종료**한 뒤 프로필을 다시 띄워
페이지가 `document.cookie` 를 읽게 했다.

| | 재기동 후 세션 쿠키 | 재기동 후 영속 쿠키 |
|---|---|---|
| 기본 설정 | 없음 | 있음 |
| "중단한 위치에서 계속하기" 켬 | 없음 | 있음 |

영속 쿠키가 양쪽 다 살아남았으므로 측정 자체는 정상이다(양성 대조군). 설정이 실제로 적용됐는지도
프로필의 `Preferences` 로 확인했다. **세션 쿠키는 어느 설정으로도 브라우저 재시작을 넘지 못한다.**

### 따라서 이 전략의 성립 조건

세션 프로필 재사용은 **사이트가 영속 로그인(자동 로그인)을 제공할 때만** 성립한다.
그 조건은 몰마다 다르고, 우리가 코드로 바꿀 수 없다.

- **ssg** — 로그인 화면에 '아이디 저장'만 있고 '로그인 상태 유지'가 없다. 대상이 될 수 없다.
- **oasis** — 마찬가지로 영속 인증 쿠키가 남지 않는다. 대상이 될 수 없다.

## 파급

1. **Phase 5B(Playwright)는 여전히 미착수로 둔다.** 이 FAIL 이 5B 의 근거가 아니고, 유효한 방식은
   기존 스택으로 되기 때문이다. `build.gradle` 에 Playwright 의존성을 남기지 않는다.
2. **Phase 5 의 전략을 "프로필 재사용"에서 "세션 이관"으로 바꿔야 한다.** 프로필을 다시 여는 방식은
   영속 로그인을 주는 몰에서만 성립하는데, 확인한 두 몰 다 주지 않는다. 반면 세션 이관은 둘 다에서 통한다.
   `session_profile_*` 컬럼과 `SessionProfileGate` 는 대부분 재사용할 수 있으나, 저장 대상이
   **프로필 디렉터리 경로**에서 **세션 스냅샷**으로 바뀐다.
3. **새 질문 7 의 옵트인 대상 몰(coupang + ssg)을 다시 본다.** 세션 이관이면 ssg 도 다시 대상이 된다 —
   다만 아래 미해결 3건이 정리된 뒤에 판단할 일이다.

## 세션 이관으로 갈 때 아직 답이 없는 것 (정직하게)

1. **캡처를 어디서 하나.** 이번 측정은 자동화가 붙은 브라우저에서 로그인이 이뤄진 뒤에 떴다. 운영에서
   사람이 순정 Chrome 으로 로그인하면 거기서는 뜰 수단이 없다. 후보는 두 가지다 —
   (a) 사람이 자동화 브라우저 안에서 로그인한다(로그인 시점에 봇 방어를 만난다)
   (b) 순정 chrome.exe 를 `--remote-debugging-port` 로 띄우고 CDP 로 붙어 쿠키만 뜬다(브라우저 자체는 순정).
   **(b) 가 유력하나 미측정이다.**
2. **세션 수명을 모른다.** 이관된 세션이 며칠 가는지 재지 않았다. 이것이 곧 "사람이 얼마나 자주 다시
   로그인해야 하는가"이고, 5-10(만료 감지)의 설계 입력이다.
3. **세션 스냅샷은 살아 있는 인증 토큰이다.** 지금은 `build/` 아래 평문 JSON 이다(gitignore 대상이라 커밋되지
   않지만, 그것과 안전은 다른 문제다). 제품이 되려면 자격증명과 같은 수준으로 다뤄야 한다 —
   현행 `mall_account.yml` + DB 키 분리 방식이 그대로 참고가 된다. 프로필 경로를 저장하던 때보다
   **위험이 올라간다**: 프로필은 OS 계정에 묶여 있었지만 스냅샷은 파일 하나로 옮겨진다.

## 재현 방법

프로브는 전부 `@Tag("probe")` 라 일반 빌드에서 빠진다.

```
# 프로필 재사용 판정 (사람 로그인 1회)
./gradlew test -PincludeProbe --tests '*SessionProfileReuseProbe.step1a*' -Djangbogo.probe.mall=ssg
  → 로그인 후 창을 닫는다
./gradlew test -PincludeProbe --tests '*SessionProfileReuseProbe.step1b*' -Djangbogo.probe.mall=ssg
./gradlew test -PincludeProbe --tests '*SessionProfileReuseProbe.step2*'  -Djangbogo.probe.mall=ssg
./gradlew test -PincludeProbe --tests '*SessionProfileReuseProbe.step3*'  -Djangbogo.probe.mall=ssg

# 세션 이관 (Selenium 단독)
./gradlew test -PincludeProbe --tests '*SeleniumSessionTransferProbe*' -Djangbogo.probe.mall=ssg

# 자동화 표식 (실계정 불필요)
./gradlew test -PincludeProbe --tests '*ChromeFingerprintProbe*'
./gradlew test -PincludeProbe --tests '*SessionCookieSurvivalProbe*'
```

대상 몰은 **기본값이 없다.** 지정하지 않으면 멈춘다 — 조용히 엉뚱한 몰로 도는 것을 한 번 겪었기 때문이다.

## 이번 측정에서 배운 것 (방법론)

- **대조군 없이는 FAIL 의 원인을 못 가른다.** 첫 판에는 Selenium 결과만 보고 "봇 탐지"로 갈 뻔했다.
  순정 Chrome 으로 같은 것을 재는 대조군이 그 오진을 막았다.
- **양성 대조군이 없으면 '없다'는 결과를 믿을 수 없다.** 세션 쿠키가 안 남았다는 결과는 영속 쿠키가
  남았다는 사실이 함께 있어야 의미가 있다.
- **관측 지점을 틀리면 맞는 실험도 틀린 답을 준다.** 세션 쿠키 생존을 디스크(Cookies DB)로 재려 했으나,
  복원이 일어난다면 그것은 브라우저 메모리에 있다. 재기동한 브라우저가 `document.cookie` 를 읽게
  바꾼 뒤에야 사이트가 보는 것과 같은 관점이 됐다.
- **측정 실패를 데이터로 섞지 말 것.** 쿠키 파일 읽기 실패를 '새 쿠키 1개'로 세는 바람에
  "이 몰은 영속 로그인을 주지 않는다"는 확신에 찬 오답이 한 번 나왔다. 지금은 읽기 실패면 예외로 멈춘다.
- **사람을 시계와 경쟁시키지 말 것.** 로그인을 20분 안에 마치라고 기다리다 타임아웃이 나면서 코드가
  Chrome 을 강제 종료했고, 그러면 쿠키가 디스크에 내려가지 않아 측정이 무의미해진다. 지금은 띄우기와
  확인이 분리돼 있어 기다리지 않는다.

## 부수 발견 (별건)

- **`Ssg.isSignedIn()` 이 로그아웃 상태에서도 `true` 를 반환한다.** `findElements(...).isEmpty()` 로
  판정하는데 가시성을 보지 않아, 숨겨진 로그아웃 버튼을 로그인으로 읽는다(실측: 존재=1, 보임=0).
  `isDisplayed()` 를 함께 봐야 한다.
- **즉시수집(`AdminController`)이 세션 프로필 게이트를 우회한다.** 게이트는 스케줄러 경로에만 있다.
- **게이트가 옵트인 OFF 몰에도 매 회차 실행 컨텍스트를 탐지한다.** 단축평가가 안 되는 인자 배치 탓이고,
  같은 판정을 하는 `StartupTasks` 쪽은 올바르게 되어 있어 두 곳이 어긋나 있다.

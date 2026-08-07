package com.jiniebox.jangbogo.svc.mall;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jiniebox.jangbogo.svc.mall.SessionCollector.Result;
import com.jiniebox.jangbogo.svc.mall.SessionCollector.SkipCause;
import com.jiniebox.jangbogo.svc.util.MallProfileLock;
import com.jiniebox.jangbogo.svc.util.SessionExpiryDetector;
import com.jiniebox.jangbogo.svc.util.SessionProfileGate;
import com.jiniebox.jangbogo.svc.util.SessionProfilePolicy;
import com.jiniebox.jangbogo.svc.util.SessionSnapshot;
import com.jiniebox.jangbogo.svc.util.SessionSnapshotTestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongConsumer;
import org.json.simple.JSONArray;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * 세션 주입 수집 경로의 계약 검증 (Phase 5-9′ 파일럿).
 *
 * <p><b>브라우저·네트워크·DB 를 쓰지 않는다.</b> 드라이버·스냅샷 조회·주입·파싱을 전부 대역으로 갈아 끼우고, 이 클래스가 책임지는 <b>순서와 판정</b>만
 * 잰다. 셀렉터가 실사이트와 맞는지는 원리상 단위테스트로 알 수 없고 프로브가 맡는다.
 *
 * <p>여기서 고정하려는 것은 실사이트에서 <b>예외 없이 조용히 실패</b>하는 것들이다 — 선방문을 빼먹으면 주입이 무시되고, 세션이 없는데 비밀번호로 폴백하면 목적이 소리
 * 없이 무너진다. 둘 다 로그도 예외도 남기지 않으므로 여기 말고는 잡을 자리가 없다.
 *
 * @author KIUNSEA
 */
class SsgSessionCollectorTest {

  private static final String SEQ = "1";

  /** 로그인 화면으로 밀렸을 때의 주소. 되돌아갈 주소에 회원 페이지 표식이 함께 실려 온다. */
  private static final String BOUNCED_TO_LOGIN =
      "https://member.ssg.com/member/login.ssg?returnURL="
          + "https%3A%2F%2Fwww.ssg.com%2Fmyssg%2FproductMng%2FpurchaseList.ssg";

  /** 호출 순서를 받아 적는다. 이 목록의 <b>순서</b>가 검증 대상이다. */
  private List<String> calls;

  private WebDriver driver;
  private String previousRoot;

  /** 프로필 루트. 락 파일도 이 아래({@code .locks/})에 실제로 만들어진다. */
  private Path profileRoot;

  /** 잠들지 않는다 — 지연은 실사이트용이고 여기서 재는 것은 순서다. */
  private final LongConsumer noSleep = millis -> {};

  @BeforeEach
  void setUp(@TempDir Path tempProfileRoot) {
    previousRoot = System.getProperty(SessionProfilePolicy.ROOT_PROPERTY);
    // 프로필 경로 계산이 사용자의 실제 %LOCALAPPDATA% 를 가리키지 않게 한다.
    // (대역이 드라이버를 띄우지 않으므로 프로필 디렉터리가 실제로 만들어지지는 않는다.
    //  락 파일은 다르다 — 프로세스 경계 락이 진짜인지 재려면 실제 파일 락을 걸어야 한다)
    profileRoot = tempProfileRoot;
    System.setProperty(SessionProfilePolicy.ROOT_PROPERTY, tempProfileRoot.toString());

    calls = new ArrayList<>();
    driver = mock(WebDriver.class);
    WebDriver.Navigation navigation = mock(WebDriver.Navigation.class);
    when(driver.navigate()).thenReturn(navigation);
    doAnswer(
            invocation -> {
              calls.add("get:" + invocation.getArgument(0));
              return null;
            })
        .when(driver)
        .get(anyString());
    doAnswer(
            invocation -> {
              calls.add("navigate:" + invocation.getArgument(0));
              return null;
            })
        .when(navigation)
        .to(anyString());
  }

  @AfterEach
  void tearDown() {
    if (previousRoot == null) {
      System.clearProperty(SessionProfilePolicy.ROOT_PROPERTY);
    } else {
      System.setProperty(SessionProfilePolicy.ROOT_PROPERTY, previousRoot);
    }
  }

  /**
   * 대역을 배선한 수집기를 만든다.
   *
   * @param snapshot 조회가 돌려줄 스냅샷
   * @param applied 주입이 반영됐다고 보고할 쿠키 수
   * @param landedUrl 회원 페이지 이동 뒤의 주소
   * @param items 파싱이 돌려줄 주문 목록
   * @return 대역이 배선된 수집기
   */
  private SsgSessionCollector collector(
      SessionSnapshot snapshot, int applied, String landedUrl, JSONArray items) {
    return collector(SEQ, snapshot, applied, landedUrl, items);
  }

  /**
   * 몰을 골라 대역을 배선한다.
   *
   * @param seqMall 어느 몰로 도는가. 로그인 신호 선언이 몰마다 다르므로 만료 판정이 갈린다
   */
  private SsgSessionCollector collector(
      String seqMall, SessionSnapshot snapshot, int applied, String landedUrl, JSONArray items) {
    when(driver.getCurrentUrl()).thenReturn(landedUrl);
    return new SsgSessionCollector(
        seqMall,
        profileDir -> {
          // 고아 정리 대역. 이 줄이 목록에 남았다는 것은 '그 프로필을 문 크롬을 전부 죽였다' 는 뜻이다.
          calls.add("sweep:" + profileDir.getFileName());
          return 0;
        },
        profileDir -> {
          calls.add("launch:" + profileDir.getFileName());
          return driver;
        },
        seq -> {
          calls.add("load:" + seq);
          return snapshot;
        },
        (usedDriver, used) -> {
          calls.add("inject:" + used.size());
          return applied;
        },
        usedDriver -> {
          calls.add("read");
          return items;
        },
        noSleep);
  }

  private static JSONArray orders(String... serials) {
    JSONArray arr = new JSONArray();
    for (String serial : serials) {
      arr.add(serial);
    }
    return arr;
  }

  @Test
  @DisplayName("대상 도메인 선방문이 주입보다 먼저 일어난다")
  void visitsTheMallDomainBeforeInjecting() {
    // about:blank 상태에서 주입하면 일부 브라우저 버전이 조용히 무시한다. 이 순서가 뒤집히면
    // 예외도 로그도 없이 로그인 화면으로 밀리므로, 실사이트에서만 드러나는 회귀가 된다.
    SsgSessionCollector sut =
        collector(
            SessionSnapshotTestSupport.withCookies(3),
            3,
            SsgSessionCollector.MEMBER_URL,
            orders("A"));

    sut.collect();

    int home = calls.indexOf("get:" + SsgSessionCollector.HOME_URL);
    int inject = calls.indexOf("inject:3");
    assertTrue(home >= 0, "대상 도메인 선방문이 아예 없다: " + calls);
    assertTrue(inject >= 0, "주입이 일어나지 않았다: " + calls);
    assertTrue(home < inject, "선방문이 주입보다 뒤에 있다 — 주입이 조용히 무시된다: " + calls);
  }

  @Test
  @DisplayName("주입 성공 경로의 호출 순서가 프로브 실측 순서와 같다")
  void followsTheMeasuredCallOrder() {
    SsgSessionCollector sut =
        collector(
            SessionSnapshotTestSupport.withCookies(2),
            2,
            SsgSessionCollector.MEMBER_URL,
            orders("A", "B"));

    Result result = sut.collect();

    assertEquals(
        List.of(
            "load:" + SEQ,
            // 고아 정리는 프로필 락을 쥔 뒤에만 일어난다. 기동보다 앞이라는 것도 여기서 고정된다.
            "sweep:ssg-inject",
            "launch:ssg-inject",
            "get:" + SsgSessionCollector.HOME_URL,
            "inject:2",
            "navigate:" + SsgSessionCollector.MEMBER_URL,
            "read"),
        calls);
    assertFalse(result.isSkipped());
    assertEquals(2, result.items().size());
    verify(driver).quit();
  }

  @Test
  @DisplayName("저장된 세션이 없으면 브라우저를 띄우지 않고 건너뛴다 — 비밀번호로 폴백하지 않는다")
  void skipsWithoutOpeningABrowserWhenThereIsNoSession() {
    // 폴백하면 화면에는 수집 성공으로 보이면서 매 회차 로그인 폼에 비밀번호가 다시 제출된다.
    // '비밀번호 없이 수집' 이 아무 로그도 남기지 않고 무너지는 유일한 경로라 여기서 고정한다.
    SsgSessionCollector sut = collector(SessionSnapshot.empty(), 0, null, orders());

    Result result = sut.collect();

    assertTrue(result.isSkipped());
    assertEquals(SkipCause.NO_SESSION, result.skipCause());
    assertFalse(result.isSessionExpired(), "세션이 '없는' 것을 '만료' 로 읽으면 몰이 통째로 일시중단된다.");
    assertEquals(SsgSessionCollector.REASON_NO_SESSION, result.skipReason());
    assertTrue(result.items().isEmpty());
    assertEquals(List.of("load:" + SEQ), calls, "세션이 없는데 브라우저를 띄웠다: " + calls);
  }

  @Test
  @DisplayName("반영된 쿠키가 0개면 파싱까지 가지 않고 건너뛴다")
  void skipsWhenNothingWasApplied() {
    SsgSessionCollector sut =
        collector(
            SessionSnapshotTestSupport.withCookies(3),
            0,
            SsgSessionCollector.MEMBER_URL,
            orders("A"));

    Result result = sut.collect();

    assertTrue(result.isSkipped());
    assertEquals(SkipCause.NOTHING_APPLIED, result.skipCause());
    assertFalse(result.isSessionExpired(), "주입 실패는 만료가 아니다 — 사람이 할 일이 다르다.");
    assertEquals(SsgSessionCollector.REASON_NOTHING_APPLIED, result.skipReason());
    assertFalse(calls.contains("read"), "주입이 성립하지 않았는데 파싱까지 갔다: " + calls);
    verify(driver).quit();
  }

  @Test
  @DisplayName("부분 주입은 만료로 보지 않는다")
  void partialInjectionIsNotTreatedAsExpiry() {
    // 캡처는 도메인을 가리지 않고 전량을 뜨므로 스냅샷에는 대상 몰과 무관한 쿠키가 섞여 있다.
    // 그중 일부가 거부되는 것은 정상이다 — 만료로 보면 멀쩡한 세션이 매 회차 건너뛰어진다.
    SsgSessionCollector sut =
        collector(
            SessionSnapshotTestSupport.withCookies(5),
            1,
            SsgSessionCollector.MEMBER_URL,
            orders("A"));

    Result result = sut.collect();

    assertFalse(result.isSkipped(), "부분 주입을 만료로 읽었다: " + result.skipReason());
    assertEquals(1, result.items().size());
  }

  @Test
  @DisplayName("로그인 화면으로 밀리면 만료로 보고 파싱 전에 건너뛴다")
  void skipsWhenPushedBackToTheLoginPage() {
    // 도달 확인을 파싱 뒤로 미루면 만료가 셀렉터 실패로 둔갑해, 사람은 '다시 로그인' 대신
    // '사이트 구조가 바뀌었다' 를 뒤진다.
    SsgSessionCollector sut =
        collector(SessionSnapshotTestSupport.withCookies(3), 3, BOUNCED_TO_LOGIN, orders("A"));

    Result result = sut.collect();

    assertTrue(result.isSkipped());
    assertEquals(SkipCause.SESSION_EXPIRED, result.skipCause());
    assertTrue(result.isSessionExpired(), "만료가 종류로 실리지 않으면 위쪽 일시중단이 통째로 무동작이 된다.");
    assertEquals(
        SessionExpiryDetector.Verdict.EXPIRED.reason(),
        result.skipReason(),
        "판정한 쪽과 사유를 적는 쪽이 갈리면 같은 사실이 화면에 두 문구로 쌓인다.");
    assertFalse(calls.contains("read"), "만료된 세션으로 파싱까지 갔다: " + calls);
    verify(driver).quit();
  }

  @Test
  @DisplayName("주소가 그대로여도 로그인 폼이 보이면 만료다 — 레지스트리의 셀렉터를 실제로 쓴다")
  void visibleLoginFormIsExpiryEvenWhenTheUrlDidNotChange() {
    // 사이트가 주소를 바꾸지 않고 화면만 로그인 폼으로 갈아 끼우면 주소만 보는 판정은 만료를
    // 통째로 놓친다 (Ssg.isSignedIn 을 고치게 만든 그 형태다). 이 축이 살아 있는지를 잰다.
    WebElement visibleLoginField = mock(WebElement.class);
    when(visibleLoginField.isDisplayed()).thenReturn(true);
    when(driver.findElements(Mockito.any(By.class))).thenReturn(List.of(visibleLoginField));

    SsgSessionCollector sut =
        collector(
            SessionSnapshotTestSupport.withCookies(3),
            3,
            SsgSessionCollector.MEMBER_URL,
            orders("A"));

    Result result = sut.collect();

    assertTrue(result.isSessionExpired(), "로그인 폼이 보이는데 만료로 읽지 않았다: " + result.skipReason());
    assertFalse(calls.contains("read"), "만료된 세션으로 파싱까지 갔다: " + calls);
  }

  @Test
  @DisplayName("로그인 신호를 선언하지 않은 몰에서는 만료로 단정하지 않고 통과시킨다")
  void undeclaredMallsAreNeverJudgedAsExpired() {
    // ssg 전용 실측 마커(purchaselist)를 모르는 몰에 들이대면 정상 회원 페이지를 만료로 읽어
    // 멀쩡한 수집이 멈춘다 — 만료를 한 회차 놓치는 것보다 나쁘다. seq=2(oasis)는 미선언이다.
    SsgSessionCollector sut =
        collector("2", SessionSnapshotTestSupport.withCookies(3), 3, BOUNCED_TO_LOGIN, orders("A"));

    Result result = sut.collect();

    assertFalse(result.isSkipped(), "선언하지 않은 몰을 추측으로 만료 판정했다: " + result.skipReason());
    assertEquals(1, result.items().size());
  }

  @Test
  @DisplayName("파싱 실패는 건너뜀으로 뭉뚱그리지 않고 예외로 올린다")
  void parsingFailureIsNotSwallowedAsASkip() {
    // 세션은 멀쩡한데 페이지 구조가 어긋난 것이다. 건너뜀으로 뭉뚱그리면, 사람이 세션을 다시 떠도
    // 해결되지 않는 문제가 '세션을 다시 뜨세요' 로만 안내된다.
    AtomicBoolean quit = new AtomicBoolean(false);
    doAnswer(
            invocation -> {
              quit.set(true);
              return null;
            })
        .when(driver)
        .quit();
    when(driver.getCurrentUrl()).thenReturn(SsgSessionCollector.MEMBER_URL);

    SsgSessionCollector sut =
        new SsgSessionCollector(
            SEQ,
            profileDir -> 0,
            profileDir -> driver,
            seq -> SessionSnapshotTestSupport.withCookies(3),
            (usedDriver, used) -> 3,
            usedDriver -> {
              throw new IllegalStateException("셀렉터 어긋남");
            },
            noSleep);

    assertThrows(RuntimeException.class, sut::collect);
    assertTrue(quit.get(), "실패해도 브라우저는 닫아야 한다.");
  }

  @Test
  @DisplayName("주입용 프로필은 캡처 프로필과 다른 디렉터리를 쓴다")
  void injectionProfileIsSeparateFromTheCaptureProfile() {
    // 같으면 사람이 '브라우저로 로그인' 창을 열어 둔 동안 주기 수집이 겹치는 순간
    // "user data directory is already in use" 로 수집이 통째로 죽는다.
    assertNotEquals(MallRegistry.SSG_GROUP.mallId(), SsgSessionCollector.injectProfileName("1"));
    assertEquals("ssg-inject", SsgSessionCollector.injectProfileName("1"));
    assertEquals("session-inject", SsgSessionCollector.injectProfileName("99"), "등록되지 않은 seq");
  }

  // ── 프로필 락 (프로세스 간 안전장치) ──────────────────────────────────────

  /**
   * 이 수집기가 잠그는 키. 주입용 프로필 이름이 곧 락 키다.
   *
   * <p>이름을 여기 베껴 적지 않고 프로덕션에서 가져온다 — 베껴 적으면 프로덕션이 키를 바꿔도 이 테스트는 <b>제가 만든 다른 락</b>을 잡고 통과한다. 그 이름
   * 자체를 고정하는 일은 {@code injectionProfileIsSeparateFromTheCaptureProfile} 이 맡는다.
   */
  private static final String INJECT_PROFILE = SsgSessionCollector.injectProfileName(SEQ);

  @Test
  @DisplayName("다른 쪽이 프로필 락을 쥐고 있으면 고아 정리도 기동도 하지 않고 건너뛴다")
  void doesNotSweepOrLaunchWhileAnotherProcessHoldsTheProfile() {
    // killOrphanProfileChrome 은 그 프로필을 문 chrome.exe 를 가리지 않고 전부 죽인다.
    // '전부 고아다' 라는 전제는 이 JVM 안에서만 참이라, 락 없이 부르면 다른 프로세스가 그 프로필로
    // 돌리고 있는 살아 있는 브라우저를 죽인다. 그것을 막는 것이 이 배선의 목적 전체다.
    try (MallProfileLock heldElsewhere = MallProfileLock.tryAcquireProfile(INJECT_PROFILE)) {
      assertEquals(MallProfileLock.Outcome.ACQUIRED, heldElsewhere.outcome(), "테스트 전제가 깨졌다.");

      SsgSessionCollector sut =
          collector(
              SessionSnapshotTestSupport.withCookies(3),
              3,
              SsgSessionCollector.MEMBER_URL,
              orders("A"));

      Result result = sut.collect();

      assertTrue(result.isSkipped());
      assertEquals(SkipCause.PROFILE_LOCKED, result.skipCause());
      assertEquals(
          SessionProfileGate.Decision.PROFILE_LOCKED.reason(),
          result.skipReason(),
          "판정한 쪽과 사유를 적는 쪽이 갈리면 같은 사실이 화면에 두 문구로 쌓인다.");
      // 대역 호출 여부를 직접 센다. '건너뛰었다' 는 결과만 보면 죽이고 나서 건너뛴 경우를 놓친다.
      assertFalse(calls.contains("sweep:" + INJECT_PROFILE), "락을 못 잡았는데 고아를 죽였다: " + calls);
      assertFalse(calls.contains("launch:" + INJECT_PROFILE), "락을 못 잡았는데 브라우저를 띄웠다: " + calls);
      assertEquals(List.of("load:" + SEQ), calls, "락 실패 회차가 다른 일을 했다: " + calls);
    }
  }

  @Test
  @DisplayName("락 실패는 만료로 승격되지 않는다 — 브레이커도 일시중단도 건드리지 않는다")
  void aLockedProfileIsNotPromotedToExpiry() {
    // 세션이 죽은 것이 아니라 잠깐 겹친 것뿐이다. 만료로 승격하면 다음 회차면 저절로 풀릴 일이
    // 사람이 다시 로그인할 때까지 몰 전체를 멈춘다. FAIL 로 세면 브레이커가 열린다.
    assertFalse(SkipCause.PROFILE_LOCKED.sessionExpired());

    try (MallProfileLock heldElsewhere = MallProfileLock.tryAcquireProfile(INJECT_PROFILE)) {
      assertEquals(MallProfileLock.Outcome.ACQUIRED, heldElsewhere.outcome(), "테스트 전제가 깨졌다.");

      Result result =
          collector(
                  SessionSnapshotTestSupport.withCookies(3),
                  3,
                  SsgSessionCollector.MEMBER_URL,
                  orders("A"))
              .collect();

      assertFalse(result.isSessionExpired(), "프로필 겹침을 만료로 읽으면 몰이 통째로 일시중단된다.");
      assertTrue(result.items().isEmpty());
    }
  }

  @Test
  @DisplayName("고아 정리는 락을 쥔 상태에서만 일어난다 — 잡기 전에 죽이면 배선이 무의미하다")
  void theSweepHappensWhileHoldingTheLock() {
    // 순서가 뒤집히면(죽인 다음에 잠그면) 락이 아무것도 막지 못한다. 그런데 '먼저 잡았다' 는
    // 호출 순서 목록만으로는 알 수 없으므로, 정리 시점에 밖에서 같은 락을 잡아 본다.
    AtomicReference<MallProfileLock.Outcome> whileSweeping = new AtomicReference<>();
    when(driver.getCurrentUrl()).thenReturn(SsgSessionCollector.MEMBER_URL);

    SsgSessionCollector sut =
        new SsgSessionCollector(
            SEQ,
            profileDir -> {
              try (MallProfileLock probe = MallProfileLock.tryAcquireProfile(INJECT_PROFILE)) {
                whileSweeping.set(probe.outcome());
              }
              return 0;
            },
            profileDir -> driver,
            seq -> SessionSnapshotTestSupport.withCookies(3),
            (usedDriver, used) -> 3,
            usedDriver -> orders("A"),
            noSleep);

    sut.collect();

    assertEquals(
        MallProfileLock.Outcome.HELD_BY_OTHER,
        whileSweeping.get(),
        "고아를 죽이는 시점에 프로필 락을 쥐고 있지 않았다 — 잠그기 전에 죽였다는 뜻이다.");
  }

  @Test
  @DisplayName("회차가 끝나면 락을 놓는다 — 다음 회차가 영구히 막히지 않는다")
  void theLockIsReleasedWhenTheRoundEnds() {
    collector(
            SessionSnapshotTestSupport.withCookies(3),
            3,
            SsgSessionCollector.MEMBER_URL,
            orders("A"))
        .collect();

    try (MallProfileLock next = MallProfileLock.tryAcquireProfile(INJECT_PROFILE)) {
      assertEquals(MallProfileLock.Outcome.ACQUIRED, next.outcome(), "수집이 끝났는데 락이 잡힌 채로 남았다.");
    }
  }

  @Test
  @DisplayName("파싱이 던진 회차에서도 락을 놓는다")
  void theLockIsReleasedEvenWhenParsingThrows() {
    when(driver.getCurrentUrl()).thenReturn(SsgSessionCollector.MEMBER_URL);

    SsgSessionCollector sut =
        new SsgSessionCollector(
            SEQ,
            profileDir -> 0,
            profileDir -> driver,
            seq -> SessionSnapshotTestSupport.withCookies(3),
            (usedDriver, used) -> 3,
            usedDriver -> {
              throw new IllegalStateException("셀렉터 어긋남");
            },
            noSleep);

    assertThrows(RuntimeException.class, sut::collect);

    try (MallProfileLock next = MallProfileLock.tryAcquireProfile(INJECT_PROFILE)) {
      assertEquals(MallProfileLock.Outcome.ACQUIRED, next.outcome(), "예외가 난 회차가 락을 쥔 채로 빠져나갔다.");
    }
  }

  @Test
  @DisplayName("잠글 수 없는 환경(UNAVAILABLE)은 막지 않는다 — 경고만 남기고 진행한다")
  void anUnlockableEnvironmentDoesNotBlockTheRound() throws Exception {
    // 파일 락이 지원되지 않는 위치가 있다(네트워크 드라이브 등). 못 잠근다고 수집을 멈출 근거는
    // 없다는 것이 게이트의 명시적 판단이다. 락 파일 자리에 디렉터리를 두어 획득을 실패시킨다.
    Files.createDirectories(profileRoot.resolve(".locks").resolve(INJECT_PROFILE + ".lock"));

    Result result =
        collector(
                SessionSnapshotTestSupport.withCookies(3),
                3,
                SsgSessionCollector.MEMBER_URL,
                orders("A"))
            .collect();

    assertFalse(result.isSkipped(), "잠글 수 없다는 이유로 수집을 막았다: " + result.skipReason());
    assertTrue(calls.contains("launch:" + INJECT_PROFILE), "UNAVAILABLE 인데 진행하지 않았다: " + calls);
    assertEquals(1, result.items().size());
  }

  @Test
  @DisplayName("만료 판정 기준은 레지스트리 선언에서만 온다 — 수집기가 자기 마커를 따로 들지 않는다")
  void expirySignalsComeFromTheRegistryOnly() {
    // 같은 지식이 두 곳에 있으면 한쪽만 고쳐도 컴파일과 테스트가 통과하고 판정만 어긋난다.
    assertEquals(
        MallRegistry.SSG_GROUP.loginSignals(),
        SsgSessionCollector.loginSignals("1"),
        "수집기가 레지스트리와 다른 신호를 쓴다.");
    assertEquals(
        SessionExpiryDetector.LoginSignals.UNDECLARED,
        SsgSessionCollector.loginSignals("99"),
        "등록되지 않은 seq 에 남의 마커를 들이대면 정상 페이지를 만료로 읽는다.");
    assertFalse(
        SsgSessionCollector.loginSignals("2").isDeclared(), "oasis 는 아직 실측되지 않았다 — 판정 대상이 아니다.");
  }
}

package com.jiniebox.jangbogo.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jiniebox.jangbogo.svc.SessionCaptureService.CaptureResult;
import com.jiniebox.jangbogo.svc.SessionCaptureService.Outcome;
import com.jiniebox.jangbogo.svc.util.ExecutionContextDetector.ExecutionContext;
import com.jiniebox.jangbogo.svc.util.SessionProfilePolicy;
import com.jiniebox.jangbogo.svc.util.SessionSnapshot;
import com.jiniebox.jangbogo.svc.util.SessionSnapshotStore;
import com.jiniebox.jangbogo.svc.util.SessionSnapshotTestSupport;
import com.jiniebox.jangbogo.svc.util.WebDriverManager;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;

/**
 * 세션 캡처 오케스트레이션 검증 (Phase 5-15).
 *
 * <p>브라우저를 실제로 띄우지 않는다 — {@link WebDriverManager}·캡처 함수·저장소를 mock 으로 갈아 끼워 상태 전이와 자원 정리만 잰다. 실제 왕복은
 * {@code SessionCaptureProbe} 가 맡는다.
 *
 * @author KIUNSEA
 */
class SessionCaptureServiceTest {

  private String previousEnabled;

  private WebDriverManager wdm;
  private SessionSnapshotStore store;
  private WebDriver driver;
  private Supplier<ExecutionContext> interactive;

  @BeforeEach
  void setUp() {
    previousEnabled = System.getProperty(SessionProfilePolicy.ENABLED_PROPERTY);
    // 캡처는 킬스위치 뒤에 있다 — 대부분의 테스트는 켠 상태를 전제한다.
    System.setProperty(SessionProfilePolicy.ENABLED_PROPERTY, "true");

    wdm = mock(WebDriverManager.class);
    store = mock(SessionSnapshotStore.class);
    driver = mock(WebDriver.class, RETURNS_DEEP_STUBS);
    when(wdm.getWebDriver(eq(WebDriverManager.BROWSER_NAME_CHROME), any())).thenReturn(driver);
    interactive = () -> ExecutionContext.INTERACTIVE;
  }

  @AfterEach
  void tearDown() {
    if (previousEnabled == null) {
      System.clearProperty(SessionProfilePolicy.ENABLED_PROPERTY);
    } else {
      System.setProperty(SessionProfilePolicy.ENABLED_PROPERTY, previousEnabled);
    }
  }

  private SessionCaptureService service(Function<WebDriver, SessionSnapshot> capturer) {
    return new SessionCaptureService(wdm, store, interactive, capturer);
  }

  private static long now() {
    return System.currentTimeMillis();
  }

  // ── 순수: 창 상한 계산 ─────────────────────────────────────────────────────

  @Test
  @DisplayName("잔여 시간은 관리자 30분 상한에서 경과분을 뺀 값이고 0 아래로 안 내려간다")
  void remainingWindowIsAdminSessionCap() {
    long base = 1_000_000_000_000L;
    // 방금 로그인 → 30분 근처
    assertEquals(30L * 60 * 1000, SessionCaptureService.remainingWindowMillis(base, base));
    // 20분 경과 → 10분
    assertEquals(
        10L * 60 * 1000, SessionCaptureService.remainingWindowMillis(base, base + 20L * 60 * 1000));
    // 31분 경과 → 0 (음수로 안 감)
    assertEquals(0L, SessionCaptureService.remainingWindowMillis(base, base + 31L * 60 * 1000));
  }

  // ── start ────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("킬스위치가 꺼져 있으면 브라우저를 띄우지 않는다")
  void startBlockedWhenKillswitchOff() {
    System.setProperty(SessionProfilePolicy.ENABLED_PROPERTY, "false");

    CaptureResult r = service(d -> SessionSnapshot.empty()).start("1", now());

    assertEquals(Outcome.KILLSWITCH_OFF, r.outcome());
    verify(wdm, never()).getWebDriver(any(), any());
  }

  @Test
  @DisplayName("등록되지 않은 몰은 값으로 거부한다")
  void startUnknownMall() {
    CaptureResult r = service(d -> SessionSnapshot.empty()).start("999", now());
    assertEquals(Outcome.UNKNOWN_MALL, r.outcome());
    verify(wdm, never()).getWebDriver(any(), any());
  }

  @Test
  @DisplayName("세션 0 자리에서는 브라우저를 띄우지 않고 값으로 거부한다")
  void startRequiresUserSession() {
    SessionCaptureService svc =
        new SessionCaptureService(
            wdm, store, () -> ExecutionContext.SERVICE_SESSION_0, d -> SessionSnapshot.empty());

    CaptureResult r = svc.start("1", now());

    assertEquals(Outcome.REQUIRES_USER_SESSION, r.outcome());
    verify(wdm, never()).getWebDriver(any(), any());
  }

  @Test
  @DisplayName("start 성공이면 마스킹 브라우저를 띄우고 로그인 페이지로 보낸다")
  void startLaunchesAndNavigates() {
    CaptureResult r = service(d -> SessionSnapshot.empty()).start("1", now());

    assertEquals(Outcome.STARTED, r.outcome());
    verify(wdm).getWebDriver(eq(WebDriverManager.BROWSER_NAME_CHROME), any());
    verify(driver.navigate()).to("https://www.ssg.com/");
  }

  @Test
  @DisplayName("이미 진행 중이면 두 번째 start 는 값으로 거부한다 (한 번에 하나)")
  void startRejectsSecondWhileActive() {
    SessionCaptureService svc = service(d -> SessionSnapshot.empty());
    svc.start("1", now());

    CaptureResult r = svc.start("2", now());

    assertEquals(Outcome.ALREADY_ACTIVE, r.outcome());
  }

  @Test
  @DisplayName("start 는 기동 전에 프로필을 문 고아 브라우저를 예방 정리한다")
  void startSweepsStaleOrphansBeforeLaunch() {
    service(d -> SessionSnapshot.empty()).start("1", now());

    org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(wdm);
    inOrder.verify(wdm).killOrphanProfileChrome(any());
    inOrder.verify(wdm).getWebDriver(eq(WebDriverManager.BROWSER_NAME_CHROME), any());
  }

  @Test
  @DisplayName("기동이 던지면 값으로 실패하고 핸드셰이크 고아를 마저 정리한다 — 락 잔류가 다음 시도를 못 막는다")
  void startLaunchFailureKillsOrphans() {
    when(wdm.getWebDriver(eq(WebDriverManager.BROWSER_NAME_CHROME), any()))
        .thenThrow(new RuntimeException("session not created"));
    SessionCaptureService svc = service(d -> SessionSnapshot.empty());

    CaptureResult r = svc.start("1", now());

    assertEquals(Outcome.LAUNCH_FAILED, r.outcome());
    // 기동 전 예방 정리 + 실패 후 정리, 두 번 닿는다.
    verify(wdm, times(2)).killOrphanProfileChrome(any());
    // 실패가 활성으로 남지 않아 다음 start 가 ALREADY_ACTIVE 로 막히지 않는다.
    assertEquals(Outcome.NO_ACTIVE_CAPTURE, svc.status("1").outcome());
  }

  // ── capture ────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("로그인을 마쳤으면 세션을 떠서 저장하고 브라우저를 닫는다")
  void captureSavesAndClosesBrowser() throws Exception {
    SessionSnapshot captured = SessionSnapshotTestSupport.withCookies(2);
    SessionCaptureService svc = service(d -> captured);
    svc.start("1", now());

    CaptureResult r = svc.capture("1");

    assertEquals(Outcome.CAPTURED, r.outcome());
    assertEquals(2, r.cookieCount());
    verify(store).save(eq("1"), eq(captured));
    verify(driver).quit();
    // 활성이 비워졌다.
    assertEquals(Outcome.NO_ACTIVE_CAPTURE, svc.status("1").outcome());
  }

  @Test
  @DisplayName("아직 로그인 세션이 없으면 브라우저를 유지하고 값으로만 알린다")
  void captureWithoutSessionKeepsBrowser() throws Exception {
    SessionCaptureService svc = service(d -> SessionSnapshot.empty());
    svc.start("1", now());

    CaptureResult r = svc.capture("1");

    assertEquals(Outcome.NOT_LOGGED_IN, r.outcome());
    verify(store, never()).save(any(), any());
    verify(driver, never()).quit();
    // 여전히 진행 중이라 다시 시도할 수 있다.
    assertEquals(Outcome.AWAITING_LOGIN, svc.status("1").outcome());
  }

  @Test
  @DisplayName("저장이 깨지면 브라우저를 유지한다 — 세션은 떴으므로 재시도 가능")
  void captureKeepsBrowserOnSaveFailure() throws Exception {
    SessionSnapshot captured = SessionSnapshotTestSupport.withCookies(1);
    org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(store).save(any(), any());
    SessionCaptureService svc = service(d -> captured);
    svc.start("1", now());

    CaptureResult r = svc.capture("1");

    assertEquals(Outcome.SAVE_FAILED, r.outcome());
    verify(driver, never()).quit();
    assertEquals(Outcome.AWAITING_LOGIN, svc.status("1").outcome());
  }

  @Test
  @DisplayName("진행 중인 캡처가 없으면 capture 는 값으로 거부한다")
  void captureWithNoActive() {
    CaptureResult r = service(d -> SessionSnapshot.empty()).capture("1");
    assertEquals(Outcome.NO_ACTIVE_CAPTURE, r.outcome());
  }

  // ── cancel · status ─────────────────────────────────────────────────────────

  @Test
  @DisplayName("취소하면 브라우저를 닫고 상태를 비운다")
  void cancelClosesBrowser() {
    SessionCaptureService svc = service(d -> SessionSnapshot.empty());
    svc.start("1", now());

    CaptureResult r = svc.cancel("1");

    assertEquals(Outcome.CANCELLED, r.outcome());
    verify(driver).quit();
    assertEquals(Outcome.NO_ACTIVE_CAPTURE, svc.status("1").outcome());
  }

  @Test
  @DisplayName("이미 만료된 관리자 세션이면 start 가 브라우저를 안 띄운다")
  void startRejectsExpiredAdminSession() {
    long base = 1_000_000_000_000L;
    SessionCaptureService svc =
        new SessionCaptureService(
            wdm, store, interactive, d -> SessionSnapshot.empty(), () -> base);

    // 31분 전 로그인 → 잔여 0
    CaptureResult r = svc.start("1", base - 31L * 60 * 1000);

    assertEquals(Outcome.EXPIRED, r.outcome());
    verify(wdm, never()).getWebDriver(any(), any());
  }

  @Test
  @DisplayName("진행 중에 관리자 세션 시간이 다 되면 status 가 창을 닫는다")
  void statusExpiresWhenAdminSessionElapsed() {
    long[] nowHolder = {1_000_000_000_000L};
    SessionCaptureService svc =
        new SessionCaptureService(
            wdm, store, interactive, d -> SessionSnapshot.empty(), () -> nowHolder[0]);

    // 방금 로그인해 start 는 성공한다.
    assertEquals(Outcome.STARTED, svc.start("1", nowHolder[0]).outcome());

    // 31분 흐른다.
    nowHolder[0] += 31L * 60 * 1000;
    CaptureResult r = svc.status("1");

    assertEquals(Outcome.EXPIRED, r.outcome());
    verify(driver).quit();
    assertEquals(Outcome.NO_ACTIVE_CAPTURE, svc.status("1").outcome());
  }

  @Test
  @DisplayName("진행 중이면 status 가 잔여 시간을 돌려준다")
  void statusReportsRemaining() {
    SessionCaptureService svc = service(d -> SessionSnapshot.empty());
    svc.start("1", now());

    CaptureResult r = svc.status("1");

    assertEquals(Outcome.AWAITING_LOGIN, r.outcome());
    assertTrue(r.remainingMillis() > 0, "잔여 시간이 양수여야 한다.");
  }

  @Test
  @DisplayName("다른 몰의 status 는 진행 중인 것이 없다고 본다 — 이전 캡처 상태를 잘못 읽지 않는다")
  void statusForDifferentMallReportsNoActive() {
    SessionCaptureService svc = service(d -> SessionSnapshot.empty());
    svc.start("1", now());

    assertEquals(Outcome.NO_ACTIVE_CAPTURE, svc.status("2").outcome());
    assertEquals(Outcome.AWAITING_LOGIN, svc.status("1").outcome());
  }

  // ── 고아 회수 (리퍼) ──────────────────────────────────────────────────────

  @Test
  @DisplayName("폴링이 끊겨 유휴가 오래되면 리퍼가 브라우저를 닫는다")
  void reapClosesIdleCapture() {
    long[] nowHolder = {1_000_000_000_000L};
    SessionCaptureService svc =
        new SessionCaptureService(
            wdm, store, interactive, d -> SessionSnapshot.empty(), () -> nowHolder[0]);
    svc.start("1", nowHolder[0]);

    // 창(30분)은 남았지만 유휴가 임계를 넘는다.
    nowHolder[0] += SessionCaptureService.IDLE_TIMEOUT_MILLIS + 1000;
    svc.reapStale();

    verify(driver).quit();
    assertEquals(Outcome.NO_ACTIVE_CAPTURE, svc.status("1").outcome());
  }

  @Test
  @DisplayName("아직 유휴가 짧으면 리퍼는 건드리지 않는다")
  void reapKeepsFreshCapture() {
    long[] nowHolder = {1_000_000_000_000L};
    SessionCaptureService svc =
        new SessionCaptureService(
            wdm, store, interactive, d -> SessionSnapshot.empty(), () -> nowHolder[0]);
    svc.start("1", nowHolder[0]);

    nowHolder[0] += SessionCaptureService.IDLE_TIMEOUT_MILLIS / 2;
    svc.reapStale();

    verify(driver, never()).quit();
    assertEquals(Outcome.AWAITING_LOGIN, svc.status("1").outcome());
  }

  @Test
  @DisplayName("status 폴링이 유휴 시계를 되돌려 리퍼 회수를 막는다")
  void statusPollResetsIdleClock() {
    long[] nowHolder = {1_000_000_000_000L};
    SessionCaptureService svc =
        new SessionCaptureService(
            wdm, store, interactive, d -> SessionSnapshot.empty(), () -> nowHolder[0]);
    svc.start("1", nowHolder[0]);

    // 임계 직전에 폴링 → lastTouch 갱신
    nowHolder[0] += SessionCaptureService.IDLE_TIMEOUT_MILLIS - 1000;
    assertEquals(Outcome.AWAITING_LOGIN, svc.status("1").outcome());

    // 다시 임계 직전까지만 흐른다 → 폴링 덕에 유휴가 리셋됐으므로 회수되지 않는다.
    nowHolder[0] += SessionCaptureService.IDLE_TIMEOUT_MILLIS - 1000;
    svc.reapStale();

    verify(driver, never()).quit();
    assertEquals(Outcome.AWAITING_LOGIN, svc.status("1").outcome());
  }

  @Test
  @DisplayName("만료된 이전 캡처가 새 start 를 영구히 막지 않는다 — start 가 먼저 회수한다")
  void startReapsExpiredActiveThenProceeds() {
    long[] nowHolder = {1_000_000_000_000L};
    SessionCaptureService svc =
        new SessionCaptureService(
            wdm, store, interactive, d -> SessionSnapshot.empty(), () -> nowHolder[0]);
    svc.start("1", nowHolder[0]);

    // 관리자 창이 다 됐다(31분). 폴링은 끊긴 채다.
    nowHolder[0] += 31L * 60 * 1000;

    // 두 번째 start 는 ALREADY_ACTIVE 로 막히지 않고, 방치된 창을 회수한 뒤 진행한다.
    CaptureResult r = svc.start("1", nowHolder[0]);

    assertEquals(Outcome.STARTED, r.outcome());
    verify(driver).quit(); // 방치된 이전 브라우저가 닫혔다
  }
}

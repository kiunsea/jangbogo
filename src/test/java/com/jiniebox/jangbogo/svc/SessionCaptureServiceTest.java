package com.jiniebox.jangbogo.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.jiniebox.jangbogo.svc.SessionCaptureService.ProfileIdentityWriter;
import com.jiniebox.jangbogo.svc.mall.MallRegistry;
import com.jiniebox.jangbogo.svc.util.ExecutionContextDetector.ExecutionContext;
import com.jiniebox.jangbogo.svc.util.SessionProfilePolicy;
import com.jiniebox.jangbogo.svc.util.SessionSnapshot;
import com.jiniebox.jangbogo.svc.util.SessionSnapshotStore;
import com.jiniebox.jangbogo.svc.util.SessionSnapshotTestSupport;
import com.jiniebox.jangbogo.svc.util.SessionTransfer;
import com.jiniebox.jangbogo.svc.util.WebDriverManager;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

/**
 * 세션 캡처 오케스트레이션 검증 (Phase 5-15).
 *
 * <p>브라우저를 실제로 띄우지 않는다 — {@link WebDriverManager}·캡처 함수·저장소를 mock 으로 갈아 끼워 상태 전이와 자원 정리만 잰다. 실제 왕복은
 * {@code SessionCaptureProbe} 가 맡는다.
 *
 * @author KIUNSEA
 */
class SessionCaptureServiceTest {

  /**
   * 캡처가 게이트 신원 컬럼에 <b>무엇을</b> 커밋했는지 받아 적는 대역. DB 를 쓰지 않는다.
   *
   * <p>값이 아니라 호출을 기록한다 — 검증하려는 것이 '무엇을 썼는가' 이고, 그게 {@code start} 가 만든 디렉터리 이름과 같아야 한다는 것이 이 단계의 핵심
   * 계약이기 때문이다.
   */
  private static final class RecordingIdentityWriter implements ProfileIdentityWriter {
    final List<String[]> writes = new ArrayList<>();

    /** 설정하면 커밋이 던진다 — '스냅샷은 들어갔는데 신원만 못 썼다' 를 재현한다. */
    Exception failure;

    @Override
    public void write(String seqMall, String profileName, String profileOwner) throws Exception {
      if (failure != null) {
        throw failure;
      }
      writes.add(new String[] {seqMall, profileName, profileOwner});
    }
  }

  /**
   * ssg 가 선언한 인증 쿠키 하나. 이름을 여기 베껴 적지 않고 레지스트리에서 가져온다 — 이름 자체를 고정하는 일은 {@code MallRegistryTest} 가
   * 맡고, 여기서 재는 것은 "선언된 이름이 있으면 그것을 본다" 는 서비스의 판정이다.
   */
  private static final String SSG_AUTH_COOKIE = MallRegistry.SSG_GROUP.authCookieNames().get(0);

  private String previousEnabled;
  private String previousRoot;

  private WebDriverManager wdm;
  private SessionSnapshotStore store;
  private WebDriver driver;
  private Supplier<ExecutionContext> interactive;
  private RecordingIdentityWriter identity;

  @BeforeEach
  void setUp(@TempDir Path profileRoot) {
    previousEnabled = System.getProperty(SessionProfilePolicy.ENABLED_PROPERTY);
    previousRoot = System.getProperty(SessionProfilePolicy.ROOT_PROPERTY);
    // 캡처는 킬스위치 뒤에 있다 — 대부분의 테스트는 켠 상태를 전제한다.
    System.setProperty(SessionProfilePolicy.ENABLED_PROPERTY, "true");
    // start 는 프로필 디렉터리를 실제로 만든다. 임시 루트로 돌리지 않으면 테스트가
    // 사용자의 진짜 %LOCALAPPDATA% 아래에 폴더를 남긴다.
    System.setProperty(SessionProfilePolicy.ROOT_PROPERTY, profileRoot.toString());

    wdm = mock(WebDriverManager.class);
    store = mock(SessionSnapshotStore.class);
    driver = mock(WebDriver.class, RETURNS_DEEP_STUBS);
    when(wdm.getWebDriver(eq(WebDriverManager.BROWSER_NAME_CHROME), any())).thenReturn(driver);
    interactive = () -> ExecutionContext.INTERACTIVE;
    identity = new RecordingIdentityWriter();
  }

  @AfterEach
  void tearDown() {
    restore(SessionProfilePolicy.ENABLED_PROPERTY, previousEnabled);
    restore(SessionProfilePolicy.ROOT_PROPERTY, previousRoot);
  }

  private static void restore(String key, String previous) {
    if (previous == null) {
      System.clearProperty(key);
    } else {
      System.setProperty(key, previous);
    }
  }

  private SessionCaptureService service(Function<WebDriver, SessionSnapshot> capturer) {
    return new SessionCaptureService(wdm, store, interactive, capturer, identity);
  }

  private SessionCaptureService service(
      Function<WebDriver, SessionSnapshot> capturer, LongSupplier clock) {
    return new SessionCaptureService(wdm, store, interactive, capturer, clock, identity);
  }

  private static long now() {
    return System.currentTimeMillis();
  }

  /**
   * 이름이 정해진 쿠키를 담은 스냅샷.
   *
   * <p>{@code SessionSnapshot.of} 는 {@code svc.util} 안에서만 열려 있고(살아 있는 토큰이라 그 폐쇄가 설계 목적이다) 이 패키지에서는
   * 부를 수 없다. 그래서 프로덕션이 실제로 쓰는 경로({@link SessionTransfer#capture})를 그대로 태워 만든다 — 서비스가 받게 될 스냅샷과 같은
   * 방식으로 다듬어진 것이 온다.
   */
  private static SessionSnapshot snapshotOf(String... cookieNames) {
    List<Map<String, Object>> cookies = new ArrayList<>();
    for (String name : cookieNames) {
      Map<String, Object> cookie = new LinkedHashMap<>();
      cookie.put("name", name);
      cookie.put("value", "토큰값");
      cookie.put("domain", ".example.com");
      cookie.put("path", "/");
      cookies.add(cookie);
    }
    ChromeDriver cdp = mock(ChromeDriver.class);
    when(cdp.executeCdpCommand(eq("Network.getAllCookies"), any()))
        .thenReturn(Map.of("cookies", cookies));
    return SessionTransfer.capture(cdp);
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
            wdm,
            store,
            () -> ExecutionContext.SERVICE_SESSION_0,
            d -> SessionSnapshot.empty(),
            identity);

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
    SessionSnapshot captured = snapshotOf(SSG_AUTH_COOKIE, "_ga");
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
    SessionSnapshot captured = snapshotOf(SSG_AUTH_COOKIE);
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

  // ── 인증 확인 (성공 판정) ────────────────────────────────────────────────────

  @Test
  @DisplayName("쿠키는 있어도 인증 쿠키가 없으면 저장하지 않고 브라우저를 유지한다")
  void captureRejectsSnapshotWithoutAuthCookie() throws Exception {
    // 몰 첫 화면만 열어도 세션·추적 쿠키는 생긴다. 빈 검사만으로 성공을 내면 사람이 로그인을
    // 건너뛴 채 눌러도 미인증 스냅샷이 저장되고, 실패는 나중에 주입 수집이 로그인 화면으로
    // 밀릴 때에야 드러난다.
    SessionCaptureService svc = service(d -> snapshotOf("_ga", "wcs_bt"));
    svc.start("1", now());

    CaptureResult r = svc.capture("1");

    assertEquals(Outcome.NOT_LOGGED_IN, r.outcome());
    verify(store, never()).save(any(), any());
    assertTrue(identity.writes.isEmpty(), "미인증 스냅샷인데 프로필이 준비됐다고 기록했다.");
    // 브라우저는 살아 있어야 한다 — 사람이 지금 로그인하고 다시 누르면 그대로 이어져야 한다.
    verify(driver, never()).quit();
    assertEquals(Outcome.AWAITING_LOGIN, svc.status("1").outcome());
  }

  @Test
  @DisplayName("인증 쿠키가 있으면 그대로 저장한다")
  void captureAcceptsSnapshotWithAuthCookie() throws Exception {
    SessionSnapshot captured = snapshotOf("_ga", SSG_AUTH_COOKIE);
    SessionCaptureService svc = service(d -> captured);
    svc.start("1", now());

    assertEquals(Outcome.CAPTURED, svc.capture("1").outcome());

    verify(store).save(eq("1"), eq(captured));
    verify(driver).quit();
  }

  @Test
  @DisplayName("인증 쿠키 이름이 확정되지 않은 몰은 기존대로 통과시킨다")
  void captureAllowsMallsWithoutDeclaredAuthCookie() throws Exception {
    // 모르는 채로 막으면 정상 로그인한 사람도 계속 튕겨 그 몰의 캡처 자체가 불가능해진다 —
    // 미인증 스냅샷 한 건보다 나쁜 역전이다.
    assertTrue(MallRegistry.OASIS.authCookieNames().isEmpty(), "이 테스트가 서 있는 전제가 깨졌다.");
    // 이름이 무엇이든 상관없다는 것이 요점이라 이름을 정하지 않은 스냅샷을 쓴다.
    SessionSnapshot captured = SessionSnapshotTestSupport.withCookies(1);
    SessionCaptureService svc = service(d -> captured);
    svc.start("2", now());

    assertEquals(Outcome.CAPTURED, svc.capture("2").outcome());

    verify(store).save(eq("2"), eq(captured));
  }

  @Test
  @DisplayName("인증 판정은 하나라도 있으면 통과이고, 미선언이면 검사하지 않는다")
  void authCookieDecisionIsAnyMatchAndSkippedWhenUndeclared() {
    SessionSnapshot snapshot = snapshotOf("A", "B");

    // 미선언 몰 — 검사 자체를 하지 않는다.
    assertTrue(SessionCaptureService.hasAuthCookie(snapshot, List.of()));
    // 전부를 요구하지 않는다. 하나만 맞아도 통과다.
    assertTrue(SessionCaptureService.hasAuthCookie(snapshot, List.of("없는이름", "B")));
    assertFalse(SessionCaptureService.hasAuthCookie(snapshot, List.of("없는이름")));
  }

  // ── 게이트가 읽을 신원 기록 ────────────────────────────────────────────────────

  @Test
  @DisplayName("캡처에 성공하면 게이트가 읽을 프로필 이름과 OS 계정을 함께 기록한다")
  void captureRecordsTheGateIdentity() {
    // v0.16.0 의 실제 상태를 막는 테스트다. 그때 캡처는 스냅샷 키/IV/마지막 로그인 시각만 커밋했고
    // session_profile_name 을 쓰는 코드가 저장소에 하나도 없었다 — 그래서 몰 옵트인을 켜면 수집이
    // 코드 한 줄 돌기 전에 전부 PROFILE_MISSING 으로 건너뛰어졌다. 저장이 성공했다는 것만으로는
    // 아무것도 보장되지 않는다.
    SessionCaptureService svc = service(d -> snapshotOf(SSG_AUTH_COOKIE, "_ga"));
    svc.start("1", now());

    assertEquals(Outcome.CAPTURED, svc.capture("1").outcome());

    assertEquals(1, identity.writes.size(), "신원을 기록하지 않았다 — 게이트가 계속 막는다.");
    String[] written = identity.writes.get(0);
    assertEquals("1", written[0]);
    assertEquals(MallRegistry.SSG_GROUP.mallId(), written[1]);
    // 소유자는 게이트에 '현재 계정' 으로 넘어가는 값과 같은 소스여야 한다(JangBoGoManager 가
    // System.getProperty("user.name") 을 넘긴다). 다른 소스로 적으면 표기가 갈리는 순간
    // PROFILE_OWNER_MISMATCH 라는 새 차단이 생긴다. 실제 계정명은 소스에 남기지 않는다.
    assertEquals(System.getProperty("user.name"), written[2]);
  }

  @Test
  @DisplayName("기록한 프로필 이름은 캡처가 실제로 연 디렉터리 이름과 같다")
  void theRecordedNameMatchesTheDirectoryThatWasActuallyOpened() {
    // 이 둘이 갈리면 게이트는 통과하는데 수집이 '빈 프로필' 을 연다 — 로그인 화면에서 멈춘 채
    // 타임아웃되고, 로그만 보고는 원인을 가릴 수 없다. 이름을 두 곳에서 따로 구하지 않는다는
    // 계약을 여기서 잰다.
    SessionCaptureService svc = service(d -> snapshotOf(SSG_AUTH_COOKIE));
    svc.start("1", now());
    svc.capture("1");

    ArgumentCaptor<Path> opened = ArgumentCaptor.forClass(Path.class);
    verify(wdm).getWebDriver(eq(WebDriverManager.BROWSER_NAME_CHROME), opened.capture());

    assertEquals(
        opened.getValue().getFileName().toString(),
        identity.writes.get(0)[1],
        "기록한 이름과 실제로 연 프로필 디렉터리가 다르다.");
  }

  @Test
  @DisplayName("신원 기록이 깨지면 성공으로 알리지 않고 브라우저를 유지한다")
  void captureReportsWhenTheIdentityCommitFails() {
    // 스냅샷은 이미 들어갔지만 게이트는 여전히 막는 상태다. 이걸 '저장했습니다' 로 알리면
    // 이후 수집이 조용히 전량 건너뛰어지고 사람은 단서를 못 얻는다.
    identity.failure = new IllegalStateException("commit boom");
    SessionCaptureService svc = service(d -> snapshotOf(SSG_AUTH_COOKIE));
    svc.start("1", now());

    CaptureResult r = svc.capture("1");

    assertEquals(Outcome.PROFILE_MARK_FAILED, r.outcome());
    verify(driver, never()).quit();
    // 다시 누를 수 있어야 한다.
    assertEquals(Outcome.AWAITING_LOGIN, svc.status("1").outcome());
  }

  @Test
  @DisplayName("저장이 깨진 회차는 신원도 기록하지 않는다 — 없는 프로필을 있다고 적지 않는다")
  void captureDoesNotRecordIdentityWhenSaveFails() throws Exception {
    org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(store).save(any(), any());
    SessionCaptureService svc = service(d -> snapshotOf(SSG_AUTH_COOKIE));
    svc.start("1", now());

    assertEquals(Outcome.SAVE_FAILED, svc.capture("1").outcome());

    assertTrue(identity.writes.isEmpty(), "저장이 실패했는데 프로필이 준비됐다고 기록했다.");
  }

  @Test
  @DisplayName("아직 로그인 전이면 신원을 기록하지 않는다")
  void captureDoesNotRecordIdentityBeforeLogin() {
    SessionCaptureService svc = service(d -> SessionSnapshot.empty());
    svc.start("1", now());

    assertEquals(Outcome.NOT_LOGGED_IN, svc.capture("1").outcome());

    assertTrue(identity.writes.isEmpty(), "로그인 전인데 프로필이 준비됐다고 기록했다.");
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
    SessionCaptureService svc = service(d -> SessionSnapshot.empty(), () -> base);

    // 31분 전 로그인 → 잔여 0
    CaptureResult r = svc.start("1", base - 31L * 60 * 1000);

    assertEquals(Outcome.EXPIRED, r.outcome());
    verify(wdm, never()).getWebDriver(any(), any());
  }

  @Test
  @DisplayName("진행 중에 관리자 세션 시간이 다 되면 status 가 창을 닫는다")
  void statusExpiresWhenAdminSessionElapsed() {
    long[] nowHolder = {1_000_000_000_000L};
    SessionCaptureService svc = service(d -> SessionSnapshot.empty(), () -> nowHolder[0]);

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
    SessionCaptureService svc = service(d -> SessionSnapshot.empty(), () -> nowHolder[0]);
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
    SessionCaptureService svc = service(d -> SessionSnapshot.empty(), () -> nowHolder[0]);
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
    SessionCaptureService svc = service(d -> SessionSnapshot.empty(), () -> nowHolder[0]);
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
    SessionCaptureService svc = service(d -> SessionSnapshot.empty(), () -> nowHolder[0]);
    svc.start("1", nowHolder[0]);

    // 관리자 창이 다 됐다(31분). 폴링은 끊긴 채다.
    nowHolder[0] += 31L * 60 * 1000;

    // 두 번째 start 는 ALREADY_ACTIVE 로 막히지 않고, 방치된 창을 회수한 뒤 진행한다.
    CaptureResult r = svc.start("1", nowHolder[0]);

    assertEquals(Outcome.STARTED, r.outcome());
    verify(driver).quit(); // 방치된 이전 브라우저가 닫혔다
  }
}

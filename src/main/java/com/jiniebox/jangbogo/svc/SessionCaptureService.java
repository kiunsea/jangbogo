package com.jiniebox.jangbogo.svc;

import com.jiniebox.jangbogo.svc.mall.MallRegistry;
import com.jiniebox.jangbogo.svc.util.ExecutionContextDetector;
import com.jiniebox.jangbogo.svc.util.ExecutionContextDetector.ExecutionContext;
import com.jiniebox.jangbogo.svc.util.SessionProfilePolicy;
import com.jiniebox.jangbogo.svc.util.SessionSnapshot;
import com.jiniebox.jangbogo.svc.util.SessionSnapshotStore;
import com.jiniebox.jangbogo.svc.util.SessionTransfer;
import com.jiniebox.jangbogo.svc.util.WebDriverManager;
import com.jiniebox.jangbogo.sys.SessionConstants;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Service;

/**
 * 세션 캡처를 사람의 로그인과 맞물려 진행한다 (Phase 5-15 · 후보 (a)).
 *
 * <h2>왜 비동기 + 폴링인가</h2>
 *
 * <p>사람이 브라우저에서 로그인하는 데 걸리는 시간은 알 수 없다. 요청 하나를 붙잡고 기다리면(SSE·{@code DeferredResult}) 그 스레드가 사람의 속도에
 * 묶이고, 타임아웃으로 죽으면서 <b>브라우저를 강제 종료</b>해 로그인해 둔 세션을 날린다(T3 프로브가 겪은 일 — "기다리지 않는 것이 설계다"). 그래서 {@link
 * #start}·{@link #status}·{@link #capture}·{@link #cancel} 로 나눠, 완료는 사람이 <b>[로그인을 마쳤습니다]</b> 를 눌러
 * 알린다.
 *
 * <h2>막힘·실패는 값으로</h2>
 *
 * <p>어떤 경로도 예외를 밖으로 던지지 않는다. 컨트롤러가 예외를 계정 문제로 오해해 연결을 끊는 형태를 이미 겪었다(5-19). 막힌 이유는 {@link Outcome}
 * 으로 돌려주고 계정·세션은 건드리지 않는다.
 *
 * <h2>킬스위치 뒤에 둔다</h2>
 *
 * <p>{@link SessionProfilePolicy#isEnabled()} 가 꺼져 있으면 캡처는 아무 일도 하지 않는다({@link
 * Outcome#KILLSWITCH_OFF}). "이 스위치가 꺼져 있으면 Phase 5 코드가 관여하지 않는다"는 계약을 지킨다. 몰별 {@code
 * session_profile_enabled} 는 여기서 켜지 않으므로 수집 게이트는 그대로 통과한다.
 *
 * <h2>브라우저 자원</h2>
 *
 * <p>한 번에 하나만 활성이다(단일 관리자·단일 브라우저 전제). 캡처 브라우저는 사람 페이스로 몇 분씩 열려 있으므로 {@code
 * BrowserConcurrencyLimiter} 의 수집 자리를 <b>잡지 않는다</b> — 잡으면 그 시간 동안 주기 수집이 굶는다. 창의 상한은 관리자 세션 30분 잔여
 * 아래로 캡한다({@link #remainingWindowMillis}).
 *
 * <h2>고아 브라우저를 서버가 회수한다</h2>
 *
 * <p>브라우저는 <b>살아 있는 인증 세션</b>을 쥐고 있다. 프런트 폴링이 끊기면(탭 닫힘·페이지 이동·네트워크 단절) 그 창이 방치될 수 있으므로, 상태 정리를
 * 클라이언트 폴링에만 기대지 않는다. 데몬 리퍼가 주기적으로 돌며 <b>유휴({@value #IDLE_TIMEOUT_MILLIS}ms 동안 폴링 없음)</b>이거나 관리자 세션
 * 창이 만료된 캡처를 회수하고, {@link #start} 도 진입 시 오래된 활성을 먼저 회수해 영구 잠김({@code ALREADY_ACTIVE})을 막는다. 종료 시에는
 * {@link #shutdown()} 이 남은 창을 닫는다.
 *
 * <p><b>알면서 받아들인 것:</b> 각 메서드는 이 객체의 모니터를 잡은 채 브라우저 기동·CDP 캡처 같은 블로킹 호출을 한다. 그 호출이 오래 걸리면 같은 락을
 * 기다리는 {@code status}/{@code cancel} 이 그 뒤로 직렬화된다. 단일 관리자·로컬 실행이라는 전제와 Selenium 자체 타임아웃(페이지 로드 60초)
 * 아래에서 실질적 위험이 낮아 락 구조를 단순하게 두었다. 진짜 문제가 관측되면 캡처 호출을 락 밖으로 빼는 것이 다음 수다.
 *
 * @author KIUNSEA
 */
@Service
public class SessionCaptureService {

  private static final Logger logger = LogManager.getLogger(SessionCaptureService.class);

  /** 캡처 한 단계의 결과 코드. 막힘·실패도 예외가 아니라 이 값으로 돌린다. */
  public enum Outcome {
    STARTED("브라우저를 띄웠습니다. 로그인한 뒤 [로그인을 마쳤습니다] 를 눌러 주세요."),
    KILLSWITCH_OFF("세션 프로필 기능이 꺼져 있습니다."),
    UNKNOWN_MALL("등록되지 않은 쇼핑몰입니다."),
    REQUIRES_USER_SESSION("브라우저를 띄울 수 없는 실행 환경입니다. 사람이 로그인한 세션에서 실행해야 합니다."),
    ALREADY_ACTIVE("이미 진행 중인 세션 캡처가 있습니다. 마치거나 취소한 뒤 다시 시도하세요."),
    LAUNCH_FAILED("브라우저를 띄우지 못했습니다."),
    AWAITING_LOGIN("로그인을 기다리는 중입니다."),
    EXPIRED("관리자 세션 시간이 다 되어 캡처 창을 닫았습니다. 다시 로그인한 뒤 시도하세요."),
    NO_ACTIVE_CAPTURE("진행 중인 세션 캡처가 없습니다."),
    NOT_LOGGED_IN("아직 로그인 세션이 없습니다. 창에서 로그인을 마친 뒤 다시 눌러 주세요."),
    CAPTURED("세션을 저장했습니다."),
    CAPTURE_FAILED("세션을 뜨지 못했습니다."),
    SAVE_FAILED("세션을 저장하지 못했습니다."),
    CANCELLED("세션 캡처를 취소했습니다.");

    private final String message;

    Outcome(String message) {
      this.message = message;
    }

    public String message() {
      return message;
    }
  }

  /**
   * 한 단계의 결과. 값만 담는다 — 쿠키 값이나 세션 토큰은 절대 싣지 않는다.
   *
   * @param outcome 결과 코드
   * @param cookieCount 캡처 성공 시 저장한 쿠키 수. 아니면 -1
   * @param remainingMillis 캡처 창 잔여 시간(ms). 해당 없으면 -1
   */
  public record CaptureResult(Outcome outcome, int cookieCount, long remainingMillis) {

    static CaptureResult of(Outcome outcome) {
      return new CaptureResult(outcome, -1, -1);
    }

    static CaptureResult awaiting(long remainingMillis) {
      return new CaptureResult(Outcome.AWAITING_LOGIN, -1, remainingMillis);
    }

    static CaptureResult captured(int cookieCount) {
      return new CaptureResult(Outcome.CAPTURED, cookieCount, -1);
    }
  }

  /** 폴링이 이만큼 끊기면 클라이언트가 사라진 것으로 보고 회수한다. 프런트는 3초마다 폴링한다. */
  static final long IDLE_TIMEOUT_MILLIS = 120_000L;

  /** 리퍼가 도는 주기. */
  static final long REAPER_INTERVAL_MILLIS = 30_000L;

  /** 진행 중인 캡처 하나. 살아 있는 브라우저를 들고 있다 — 값은 담지 않는다. */
  private static final class ActiveCapture {
    final String seqMall;
    final WebDriver driver;
    final long loginTimeMillis;

    /** 마지막으로 폴링이 닿은 시각. 리퍼가 유휴를 판정하는 근거다. */
    long lastTouchMillis;

    ActiveCapture(String seqMall, WebDriver driver, long loginTimeMillis, long lastTouchMillis) {
      this.seqMall = seqMall;
      this.driver = driver;
      this.loginTimeMillis = loginTimeMillis;
      this.lastTouchMillis = lastTouchMillis;
    }
  }

  private final WebDriverManager webDriverManager;
  private final SessionSnapshotStore store;
  private final Supplier<ExecutionContext> contextSupplier;
  private final Function<WebDriver, SessionSnapshot> capturer;
  private final LongSupplier clock;

  /**
   * 고아 캡처를 회수하는 데몬 리퍼. {@link #startReaper()} 에서만 스케줄한다(테스트는 스레드 없이 {@link #reapStale()} 을 직접 부른다).
   */
  private final ScheduledExecutorService reaper =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "session-capture-reaper");
            t.setDaemon(true);
            return t;
          });

  /** 진행 중인 캡처. 한 번에 하나. 접근은 이 객체의 모니터로 직렬화한다. */
  private ActiveCapture active;

  /** Spring 이 쓰는 기본 생성자 — 실제 브라우저·저장소·컨텍스트·캡처를 배선한다. */
  public SessionCaptureService() {
    this(
        new WebDriverManager(),
        new SessionSnapshotStore(),
        ExecutionContextDetector::detect,
        SessionTransfer::capture);
  }

  /** 테스트가 협력자를 갈아 끼우는 생성자(시계는 실제 시각). */
  SessionCaptureService(
      WebDriverManager webDriverManager,
      SessionSnapshotStore store,
      Supplier<ExecutionContext> contextSupplier,
      Function<WebDriver, SessionSnapshot> capturer) {
    this(webDriverManager, store, contextSupplier, capturer, System::currentTimeMillis);
  }

  /** 시계까지 갈아 끼우는 생성자 — 창 상한 만료를 결정적으로 재현하는 테스트가 쓴다. */
  SessionCaptureService(
      WebDriverManager webDriverManager,
      SessionSnapshotStore store,
      Supplier<ExecutionContext> contextSupplier,
      Function<WebDriver, SessionSnapshot> capturer,
      LongSupplier clock) {
    this.webDriverManager = webDriverManager;
    this.store = store;
    this.contextSupplier = contextSupplier;
    this.capturer = capturer;
    this.clock = clock;
  }

  /** 데몬 리퍼를 스케줄한다. Spring 이 관리하는 빈에서만 불린다 — 테스트 생성자는 스레드를 띄우지 않는다. */
  @PostConstruct
  void startReaper() {
    reaper.scheduleAtFixedRate(
        this::reapStaleQuietly,
        REAPER_INTERVAL_MILLIS,
        REAPER_INTERVAL_MILLIS,
        TimeUnit.MILLISECONDS);
  }

  /** 종료 시 리퍼를 멈추고 남아 있는 캡처 브라우저를 닫는다 — 살아 있는 세션을 매달아 두지 않는다. */
  @PreDestroy
  synchronized void shutdown() {
    reaper.shutdownNow();
    if (active != null) {
      quitQuietly(active.driver);
      active = null;
    }
  }

  /** 리퍼 스레드가 부른다. 예외가 새면 스케줄이 멈추므로 삼킨다. */
  private void reapStaleQuietly() {
    try {
      reapStale();
    } catch (RuntimeException e) {
      logger.debug("리퍼 실행 중 예외({})", e.getClass().getSimpleName());
    }
  }

  /**
   * 유휴이거나 창이 만료된 캡처를 회수한다.
   *
   * <p>회수 근거 둘 — (1) 폴링이 {@value #IDLE_TIMEOUT_MILLIS}ms 넘게 끊겼다(클라이언트가 사라졌다), (2) 관리자 세션 30분 창이 다
   * 됐다. 어느 쪽이든 살아 있는 세션을 쥔 브라우저를 닫고 상태를 비운다.
   */
  synchronized void reapStale() {
    if (active == null) {
      return;
    }
    long now = clock.getAsLong();
    boolean windowExpired = remainingWindowMillis(active.loginTimeMillis, now) <= 0;
    boolean idle = (now - active.lastTouchMillis) > IDLE_TIMEOUT_MILLIS;
    if (windowExpired || idle) {
      logger.info("방치된 세션 캡처를 회수한다 — seq={} (창만료={}, 유휴={})", active.seqMall, windowExpired, idle);
      quitQuietly(active.driver);
      active = null;
    }
  }

  /**
   * 캡처를 시작한다 — 프로필로 마스킹된 브라우저를 띄우고 로그인 페이지로 보낸다. <b>기다리지 않는다.</b>
   *
   * @param seqMall 쇼핑몰 시퀀스
   * @param loginTimeMillis 관리자 세션 로그인 시각. 캡처 창 상한을 여기서 잰다
   * @return 결과 값
   */
  public synchronized CaptureResult start(String seqMall, long loginTimeMillis) {
    if (!SessionProfilePolicy.isEnabled()) {
      return CaptureResult.of(Outcome.KILLSWITCH_OFF);
    }
    Optional<MallRegistry> mall = MallRegistry.bySeq(seqMall);
    if (mall.isEmpty()) {
      return CaptureResult.of(Outcome.UNKNOWN_MALL);
    }
    ExecutionContext context = contextSupplier.get();
    if (context == null || !context.canRunHeadedBrowser()) {
      return CaptureResult.of(Outcome.REQUIRES_USER_SESSION);
    }
    long now = clock.getAsLong();
    if (remainingWindowMillis(loginTimeMillis, now) <= 0) {
      return CaptureResult.of(Outcome.EXPIRED);
    }
    // 방치된(유휴·만료) 이전 캡처를 먼저 회수한다 — 폴링이 끊긴 캡처가 영구 잠김을 만들지 않게.
    reapStale();
    if (active != null) {
      return CaptureResult.of(Outcome.ALREADY_ACTIVE);
    }

    WebDriver driver = null;
    Path profileDir = null;
    try {
      profileDir = SessionProfilePolicy.profileDir(mall.get().mallId());
      Files.createDirectories(profileDir);
      // 이전 실패·강제 종료가 남긴 고아 브라우저가 프로필 락을 쥐고 있으면 이번 기동도 "user data
      // directory is already in use" 로 실패한다. 활성 캡처가 없는 지금 이 프로필을 문 크롬은 전부
      // 고아이므로 먼저 정리한다 — 실측에서 고아 하나가 이후 시도 전부를 막았다.
      int stale = webDriverManager.killOrphanProfileChrome(profileDir);
      if (stale > 0) {
        logger.info("이전 캡처가 남긴 고아 브라우저 {}개를 정리했다 — seq={}", stale, normalize(seqMall));
      }
      // profileDir 이 있으므로 WebDriverManager 가 마스킹을 자동으로 건다(WebDriverManager:138-140).
      driver = webDriverManager.getWebDriver(WebDriverManager.BROWSER_NAME_CHROME, profileDir);
      driver.navigate().to(mall.get().loginUrl());
      active = new ActiveCapture(normalize(seqMall), driver, loginTimeMillis, now);
      logger.info("세션 캡처 시작 — seq={} (로그인 대기)", normalize(seqMall));
      return CaptureResult.of(Outcome.STARTED);
    } catch (Exception e) {
      // 실패는 값으로. 부분 생성된 브라우저는 정리한다. 핸드셰이크 실패면 driver 가 null 인 채
      // chrome.exe 만 떠 있어 quit() 으로 닿을 수 없다 — 프로필 명령줄로 찾아 마저 정리한다.
      quitQuietly(driver);
      if (profileDir != null) {
        int orphans = webDriverManager.killOrphanProfileChrome(profileDir);
        if (orphans > 0) {
          logger.info("기동 실패로 남은 고아 브라우저 {}개를 정리했다.", orphans);
        }
      }
      active = null;
      // 클래스명만 남기면 근본 원인이 가려진다(실측 — "user data directory already in use" 를 못 봤다).
      // 기동은 로그인 전이라 메시지에 세션 값이 섞일 수 없다. 첫 줄만 warn, 전체 스택은 debug.
      logger.warn("세션 캡처 시작 실패({}): {}", e.getClass().getSimpleName(), firstLine(e.getMessage()));
      logger.debug("세션 캡처 시작 실패 상세", e);
      return CaptureResult.of(Outcome.LAUNCH_FAILED);
    }
  }

  /**
   * 지금 상태를 돌려준다. 관리자 세션 시간이 다 됐으면 창을 닫는다.
   *
   * @param seqMall 쇼핑몰 시퀀스
   * @return 결과 값
   */
  public synchronized CaptureResult status(String seqMall) {
    // 요청한 몰이 지금 활성인 그 몰이 아니면 진행 중인 것이 없다고 본다 — 다른 몰 모달이 이전 캡처의 상태를
    // 잘못 읽는 것을 막는다.
    if (active == null || !active.seqMall.equals(normalize(seqMall))) {
      return CaptureResult.of(Outcome.NO_ACTIVE_CAPTURE);
    }
    long now = clock.getAsLong();
    long remaining = remainingWindowMillis(active.loginTimeMillis, now);
    if (remaining <= 0) {
      // 상한은 관리자 세션 잔여다. 넘기면 창을 닫는다 — 보안 동작(슬라이딩 만료)은 바꾸지 않는다.
      quitQuietly(active.driver);
      active = null;
      return CaptureResult.of(Outcome.EXPIRED);
    }
    // 폴링이 살아 있다는 신호. 리퍼가 이 캡처를 유휴로 회수하지 않게 한다.
    active.lastTouchMillis = now;
    return CaptureResult.awaiting(remaining);
  }

  /**
   * 사람이 로그인을 마쳤다. 세션을 떠서 저장한다.
   *
   * <p>로그인 전이면({@link Outcome#NOT_LOGGED_IN}) 브라우저를 <b>유지</b>한다 — 사람이 로그인하고 다시 누를 수 있어야 한다. 저장까지
   * 성공해야 브라우저를 닫는다.
   *
   * @param seqMall 쇼핑몰 시퀀스
   * @return 결과 값
   */
  public synchronized CaptureResult capture(String seqMall) {
    if (active == null || !active.seqMall.equals(normalize(seqMall))) {
      return CaptureResult.of(Outcome.NO_ACTIVE_CAPTURE);
    }

    SessionSnapshot snapshot;
    try {
      snapshot = capturer.apply(active.driver);
    } catch (RuntimeException e) {
      // 캡처 자체가 실패했다(창이 이미 닫혔을 수 있다). 브라우저·활성 상태는 유지하고 사람이 취소로 정리하게 둔다.
      logger.warn("세션 캡처 실패({})", e.getClass().getSimpleName());
      return CaptureResult.of(Outcome.CAPTURE_FAILED);
    }

    if (snapshot == null || snapshot.isEmpty()) {
      // 로그인 전이다. 브라우저를 유지한다 — 값으로만 알린다.
      return CaptureResult.of(Outcome.NOT_LOGGED_IN);
    }

    try {
      store.save(active.seqMall, snapshot);
    } catch (Exception e) {
      // 세션은 떴는데 저장이 깨졌다. 브라우저를 유지해 다시 시도할 수 있게 한다.
      logger.warn("세션 저장 실패({})", e.getClass().getSimpleName());
      return CaptureResult.of(Outcome.SAVE_FAILED);
    }

    int count = snapshot.size();
    quitQuietly(active.driver);
    active = null;
    logger.info("세션 캡처 완료 — 쿠키 {}개 저장 (값은 기록하지 않는다)", count);
    return CaptureResult.captured(count);
  }

  /**
   * 진행 중인 캡처를 취소한다. 브라우저를 닫고 상태를 비운다.
   *
   * @param seqMall 쇼핑몰 시퀀스(단일 활성이라 참고용)
   * @return 결과 값
   */
  public synchronized CaptureResult cancel(String seqMall) {
    if (active == null) {
      return CaptureResult.of(Outcome.NO_ACTIVE_CAPTURE);
    }
    quitQuietly(active.driver);
    active = null;
    return CaptureResult.of(Outcome.CANCELLED);
  }

  /**
   * 캡처 창의 잔여 시간(ms). 관리자 세션 30분 상한에서 경과분을 뺀 값이며 0 아래로 내려가지 않는다.
   *
   * <p>순수 계산이다 — 브라우저·시계를 만지지 않는다. {@code nowMillis} 를 받아 테스트가 시간을 고정할 수 있게 한다.
   *
   * @param loginTimeMillis 관리자 로그인 시각
   * @param nowMillis 현재 시각
   * @return 잔여 시간(ms). 0 이상
   */
  static long remainingWindowMillis(long loginTimeMillis, long nowMillis) {
    long windowMillis = SessionConstants.SESSION_TIMEOUT_MINUTES * 60L * 1000L;
    long remaining = windowMillis - (nowMillis - loginTimeMillis);
    return Math.max(0L, remaining);
  }

  private static String normalize(String seqMall) {
    return seqMall == null ? "" : seqMall.trim();
  }

  /**
   * 예외 메시지의 첫 줄. Selenium 메시지는 원인 한 줄 뒤에 Host/Build info 여러 줄이 붙는다 — 진단에 필요한 것은 첫 줄이고, 나머지는 debug
   * 스택으로 본다.
   */
  private static String firstLine(String message) {
    if (message == null || message.isBlank()) {
      return "(메시지 없음)";
    }
    int cut = message.indexOf('\n');
    return (cut < 0 ? message : message.substring(0, cut)).trim();
  }

  private static void quitQuietly(WebDriver driver) {
    if (driver == null) {
      return;
    }
    try {
      driver.quit();
    } catch (RuntimeException e) {
      logger.debug("드라이버 종료 중 예외({})", e.getClass().getSimpleName());
    }
  }
}

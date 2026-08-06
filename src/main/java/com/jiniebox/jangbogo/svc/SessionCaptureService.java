package com.jiniebox.jangbogo.svc;

import com.jiniebox.jangbogo.dao.JbgMallDataAccessObject;
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
import java.util.List;
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
 * <h2>캡처가 게이트의 판정 근거를 남긴다</h2>
 *
 * <p>{@code SessionProfileGate} 는 {@code session_profile_name}·{@code session_profile_owner} 로
 * 판정하는데, v0.16.0 까지 캡처는 스냅샷 키/IV/마지막 로그인 시각만 커밋했고 그 두 컬럼을 쓰는 코드가 저장소에 <b>하나도 없었다</b>. 그래서 몰 옵트인을 켜는
 * 순간 수집이 전량 {@code PROFILE_MISSING} 으로 건너뛰어졌다. {@link #capture} 는 저장에 성공한 직후 {@link
 * ProfileIdentityWriter} 로 그 신원을 커밋한다 — <b>여기서 쓰는 이름은 {@link #start} 가 프로필 디렉터리를 만들 때 쓴 그 값이어야
 * 한다.</b> 다르면 게이트는 통과하는데 수집이 빈 프로필을 연다. 그래서 이름을 다시 계산하지 않고 {@link ActiveCapture} 에 실어 나른다.
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
    // 스냅샷이 아예 빈 경우와, 쿠키는 있는데 인증 쿠키가 없는 경우 둘 다 이 값이다.
    // 사람이 놓인 상태(아직 로그인 안 함)와 해야 할 일(창에서 로그인하고 다시 누르기)이 같아서다.
    NOT_LOGGED_IN("아직 로그인 세션이 없습니다. 창에서 로그인을 마친 뒤 다시 눌러 주세요."),
    CAPTURED("세션을 저장했습니다."),
    CAPTURE_FAILED("세션을 뜨지 못했습니다."),
    SAVE_FAILED("세션을 저장하지 못했습니다."),
    // SAVE_FAILED 와 나눈다. 스냅샷은 들어갔는데 게이트가 읽는 신원 컬럼만 비어 있는 상태이고,
    // 그 상태를 '저장 성공' 으로 알리면 이후 수집이 조용히 전량 SKIPPED 된 채 원인을 못 찾는다.
    PROFILE_MARK_FAILED("세션은 저장했지만 프로필 정보를 기록하지 못했습니다. 다시 눌러 주세요."),
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

  /**
   * 게이트가 읽는 신원 컬럼을 커밋하는 자리. DB 를 붙잡는 유일한 지점이라 여기만 갈아 끼우면 테스트가 파일·DB 없이 돈다.
   *
   * <p>실패를 삼키지 않고 그대로 올린다 — 커밋이 깨졌는데 성공으로 알리면 {@link Outcome#PROFILE_MARK_FAILED} 를 나눈 의미가 없어진다.
   */
  @FunctionalInterface
  interface ProfileIdentityWriter {
    void write(String seqMall, String profileName, String profileOwner) throws Exception;
  }

  /** 진행 중인 캡처 하나. 살아 있는 브라우저를 들고 있다 — 값은 담지 않는다. */
  private static final class ActiveCapture {
    final String seqMall;

    /**
     * 이 캡처가 실제로 연 프로필 디렉터리 이름({@code MallRegistry.mallId()}).
     *
     * <p>{@link SessionCaptureService#capture} 는 seq 만 알고 몰을 모른다. 거기서 다시 조회해 이름을 만들면 {@link
     * SessionCaptureService#start} 가 쓴 값과 갈릴 여지가 생기고, 갈리는 순간 게이트는 통과하는데 수집이 <b>빈 프로필</b>을 연다. 그래서
     * 만든 값을 그대로 실어 나른다.
     */
    final String profileName;

    /**
     * 이 몰에서 '로그인됐다' 를 가르는 인증 쿠키 이름들({@link MallRegistry#authCookieNames()}). 확정되지 않은 몰은 빈 목록이고, 그러면
     * 검사하지 않는다.
     *
     * <p>{@link SessionCaptureService#start} 가 이미 레지스트리에서 몰을 찾았으므로 그 값을 그대로 실어 나른다. {@code capture}
     * 에서 seq 로 다시 찾으면 "못 찾았을 때" 라는 실제로는 올 수 없는 분기가 생기고, 거기에 무엇을 넣든(통과·차단) 아무도 실행하지 않는 두 번째 정책이 된다.
     */
    final List<String> authCookieNames;

    final WebDriver driver;
    final long loginTimeMillis;

    /** 마지막으로 폴링이 닿은 시각. 리퍼가 유휴를 판정하는 근거다. */
    long lastTouchMillis;

    ActiveCapture(
        String seqMall,
        String profileName,
        List<String> authCookieNames,
        WebDriver driver,
        long loginTimeMillis,
        long lastTouchMillis) {
      this.seqMall = seqMall;
      this.profileName = profileName;
      this.authCookieNames = authCookieNames;
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
  private final ProfileIdentityWriter identityWriter;

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

  /** Spring 이 쓰는 기본 생성자 — 실제 브라우저·저장소·컨텍스트·캡처·DAO 를 배선한다. */
  public SessionCaptureService() {
    this(
        new WebDriverManager(),
        new SessionSnapshotStore(),
        ExecutionContextDetector::detect,
        SessionTransfer::capture,
        System::currentTimeMillis,
        (seqMall, profileName, profileOwner) ->
            new JbgMallDataAccessObject()
                .saveSessionProfileIdentity(seqMall, profileName, profileOwner));
  }

  /** 테스트가 협력자를 갈아 끼우는 생성자(시계는 실제 시각). */
  SessionCaptureService(
      WebDriverManager webDriverManager,
      SessionSnapshotStore store,
      Supplier<ExecutionContext> contextSupplier,
      Function<WebDriver, SessionSnapshot> capturer,
      ProfileIdentityWriter identityWriter) {
    this(
        webDriverManager,
        store,
        contextSupplier,
        capturer,
        System::currentTimeMillis,
        identityWriter);
  }

  /** 시계까지 갈아 끼우는 생성자 — 창 상한 만료를 결정적으로 재현하는 테스트가 쓴다. */
  SessionCaptureService(
      WebDriverManager webDriverManager,
      SessionSnapshotStore store,
      Supplier<ExecutionContext> contextSupplier,
      Function<WebDriver, SessionSnapshot> capturer,
      LongSupplier clock,
      ProfileIdentityWriter identityWriter) {
    this.webDriverManager = webDriverManager;
    this.store = store;
    this.contextSupplier = contextSupplier;
    this.capturer = capturer;
    this.clock = clock;
    this.identityWriter = identityWriter;
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
      // 이 한 값이 디렉터리 이름이자 나중에 게이트가 읽을 session_profile_name 이 된다.
      // 두 곳에서 따로 구하면 갈릴 수 있어 여기서 한 번만 정하고 ActiveCapture 로 실어 나른다.
      String profileName = mall.get().mallId();
      profileDir = SessionProfilePolicy.profileDir(profileName);
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
      active =
          new ActiveCapture(
              normalize(seqMall),
              profileName,
              mall.get().authCookieNames(),
              driver,
              loginTimeMillis,
              now);
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
   * <p><b>스냅샷이 비어 있지 않다는 것은 로그인의 증거가 못 된다.</b> 몰 첫 화면만 열어도 쿠키는 생기므로, 빈 검사만 두면 사람이 로그인을 건너뛴 채 눌러도
   * {@link Outcome#CAPTURED} 로 끝나고 <b>미인증 스냅샷이 저장된다.</b> 그 실패는 나중에 주입 수집이 로그인 화면으로 밀릴 때에야 드러난다. 그래서
   * 저장 <b>전에</b> {@link MallRegistry#authCookieNames()} 의 인증 쿠키를 {@link
   * SessionSnapshot#hasCookie(String)} 로 확인한다. <b>이름이 실측된 몰만 검사하고, 미선언 몰은 기존대로 통과시킨다</b> — 모르는 채로
   * 막으면 정상 로그인까지 튕겨 그 몰의 캡처가 아예 불가능해진다(판단 근거는 레지스트리 javadoc).
   *
   * <p>이때 돌려주는 값은 새 코드가 아니라 {@link Outcome#NOT_LOGGED_IN} 이다. 사람이 놓인 상태가 실제로 '아직 로그인하지 않았다' 이고, 화면이
   * 안내할 다음 행동도 빈 스냅샷일 때와 똑같다. 코드를 나누면 프런트가 같은 안내를 두 벌 구현하게 되고 사람이 보는 차이는 없다. 진단에 필요한 구분(쿠키는 있었는데 인증
   * 쿠키만 없었다)은 로그로 남긴다.
   *
   * <p>저장 뒤에 <b>게이트가 읽을 신원({@code session_profile_name}·{@code owner}·{@code status})을 커밋</b>한다. 그게
   * 이 캡처가 수집으로 이어지는 유일한 연결이다 — 빠지면 스냅샷은 있는데 게이트가 {@code PROFILE_MISSING} 으로 막는다. 그래서 이 커밋이 깨지면 성공으로
   * 알리지 않고 {@link Outcome#PROFILE_MARK_FAILED} 로 돌려주며 브라우저를 유지한다.
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

    if (!hasAuthCookie(snapshot, active.authCookieNames)) {
      // 쿠키는 있는데 인증 쿠키가 없다 = 첫 화면만 열어 둔 상태다. 여기서 저장하면 미인증 스냅샷이
      // 굳고, 화면에는 '세션을 저장했습니다' 로만 보인다. 브라우저는 유지한다 — 사람이 지금 로그인하고
      // 다시 누르면 그대로 이어진다.
      logger.warn(
          "인증 쿠키가 없어 저장하지 않는다 — seq={}, 쿠키 {}개 (로그인 전으로 본다)", active.seqMall, snapshot.size());
      return CaptureResult.of(Outcome.NOT_LOGGED_IN);
    }

    try {
      store.save(active.seqMall, snapshot);
    } catch (Exception e) {
      // 세션은 떴는데 저장이 깨졌다. 브라우저를 유지해 다시 시도할 수 있게 한다.
      logger.warn("세션 저장 실패({})", e.getClass().getSimpleName());
      return CaptureResult.of(Outcome.SAVE_FAILED);
    }

    try {
      // 게이트가 판정에 쓰는 근거를 여기서 남긴다. 이 커밋이 빠지면 캡처는 성공했는데
      // session_profile_name 이 비어 있어 이후 수집이 전량 PROFILE_MISSING 으로 건너뛰어진다 —
      // 화면에는 '세션을 저장했습니다' 로만 보여서 원인을 찾을 단서가 없다(v0.16.0 의 실제 상태).
      //
      // 소유자는 반드시 이 소스여야 한다. JangBoGoManager 가 게이트에 현재 계정으로 넘기는 값과
      // 같은 소스라야 표기가 갈리지 않는다 — 갈리면 PROFILE_OWNER_MISMATCH 라는 새 차단이 생긴다.
      identityWriter.write(active.seqMall, active.profileName, System.getProperty("user.name"));
    } catch (Exception e) {
      // 스냅샷은 이미 들어갔다. 브라우저를 유지해 다시 누를 수 있게 하고, 값으로 구분해 알린다.
      // 여기서 성공으로 알리면 '저장은 됐는데 수집은 전부 건너뜀' 이라는 조용한 상태가 굳는다.
      logger.warn("세션 프로필 신원 기록 실패({})", e.getClass().getSimpleName());
      return CaptureResult.of(Outcome.PROFILE_MARK_FAILED);
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

  /**
   * 이 스냅샷이 로그인된 세션으로 보이는지. 쿠키 <b>이름만</b> 본다 — 값은 살아 있는 인증 토큰이라 판정에도 꺼내지 않는다.
   *
   * <p><b>이름이 확정된 몰만 검사한다.</b> {@code names} 가 비어 있으면 검사하지 않고 통과시킨다. 실측된 것은 ssg 뿐이고, 모르는 몰까지 막으면 정상
   * 로그인한 사람도 계속 튕겨 그 몰의 캡처가 아예 불가능해진다 — 미인증 스냅샷 한 건보다 나쁜 역전이다.
   *
   * <p>선언돼 있으면 <b>하나라도</b> 있으면 통과다. 전부를 요구하면 몰이 쿠키 구성을 조금만 바꿔도 같은 역전이 난다.
   *
   * <p>순수 판정이다 — 브라우저·저장소를 만지지 않는다.
   *
   * @param snapshot 캡처한 스냅샷 (비어 있지 않은 것이 전제다)
   * @param names 이 몰의 인증 쿠키 이름. 비어 있으면 검사하지 않는다
   * @return 통과시켜도 되면 true
   */
  static boolean hasAuthCookie(SessionSnapshot snapshot, List<String> names) {
    if (names == null || names.isEmpty()) {
      return true;
    }
    for (String name : names) {
      if (snapshot.hasCookie(name)) {
        return true;
      }
    }
    return false;
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

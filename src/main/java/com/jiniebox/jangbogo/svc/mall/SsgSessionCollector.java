package com.jiniebox.jangbogo.svc.mall;

import com.jiniebox.jangbogo.svc.util.CollectStep;
import com.jiniebox.jangbogo.svc.util.MallProfileLock;
import com.jiniebox.jangbogo.svc.util.SessionExpiryDetector;
import com.jiniebox.jangbogo.svc.util.SessionProfileGate;
import com.jiniebox.jangbogo.svc.util.SessionProfilePolicy;
import com.jiniebox.jangbogo.svc.util.SessionSnapshot;
import com.jiniebox.jangbogo.svc.util.SessionSnapshotStore;
import com.jiniebox.jangbogo.svc.util.SessionTransfer;
import com.jiniebox.jangbogo.svc.util.WebDriverManager;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.LongConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.openqa.selenium.WebDriver;

/**
 * ssg.com 을 <b>비밀번호 없이</b> 수집한다 — 저장된 세션을 주입해 회원 주문내역으로 직행한다 (Phase 5-9′ 파일럿).
 *
 * <h2>이 클래스가 존재하는 이유</h2>
 *
 * <p>{@link Ssg} 는 매 회차 {@code member.ssg.com} 로그인 폼에 아이디·비밀번호를 제출한다. 이 프로젝트가 줄이려는 것이 바로 그 반복 제출이다.
 * 부품({@code SessionSnapshotStore.load}·{@code SessionTransfer.inject}·{@code
 * WebDriverManager.getWebDriver(name, profileDir)})은 이미 있었고 <b>프로덕션 호출자가 0건</b>이었다. 여기가 그 호출자다.
 *
 * <p>{@link Ssg} 는 한 줄도 바뀌지 않는다. 목록 파싱은 이미 검증된 {@link Ssg#navigatePurchased(WebDriver)} 를 그대로 부른다 —
 * 그 메서드는 로그인을 하지 않고 주문내역 페이지만 읽으므로, 로그인된 브라우저만 넘겨주면 그대로 성립한다.
 *
 * <h2>호출 순서 — 어긋나면 조용히 실패한다</h2>
 *
 * <p>{@code SeleniumSessionTransferProbe} 가 실측한 순서를 그대로 옮겼다.
 *
 * <ol>
 *   <li>스냅샷을 먼저 읽는다 (아래 '왜 load 가 먼저인가')
 *   <li>{@code getWebDriver("chrome", profileDir)} — <b>{@code profileDir} 이 있어야</b> 자동화 표식 제거와
 *       {@code navigator.webdriver} 마스킹이 붙는다({@code WebDriverManager} 는 {@code profileDir} 이 null
 *       이면 그 옵션을 하나도 달지 않는다)
 *   <li><b>대상 도메인 선방문</b> — {@code about:blank} 상태에서 주입하면 일부 브라우저 버전이 <b>조용히 무시한다</b>({@code
 *       SessionTransfer.inject} javadoc 의 근거). 이 한 줄이 빠지면 예외도 없이 로그인 화면으로 밀린다
 *   <li>{@code SessionTransfer.inject}
 *   <li>회원 주문내역으로 직행
 * </ol>
 *
 * <p><b>왜 {@code load} 를 브라우저보다 먼저 하나.</b> 프로브는 드라이버를 먼저 띄웠지만, 그것은 프로브가 세션 파일이 있다는 전제로 돌기 때문이다. 무인
 * 수집에서는 '아직 캡처하지 않았다' 가 가장 흔한 상태이고, 드라이버를 먼저 띄우면 <b>매 회차 브라우저를 띄웠다가 아무것도 못 하고 닫는다.</b> {@code load}
 * 는 DB·파일만 만지므로 앞으로 당겨도 3~5 의 순서에는 영향이 없다. 지켜야 하는 불변식은 <b>선방문이 주입보다 앞</b>이라는 것 하나다.
 *
 * <h2>세션이 없거나 만료면 — 건너뛴다. 비밀번호로 폴백하지 않는다</h2>
 *
 * <p>{@code load} 는 부재·복호화 실패를 예외가 아니라 빈 스냅샷으로 준다. 여기서 <b>기존 아이디/비밀번호로 폴백하면</b> 화면에는 수집 성공으로 보이면서
 * 실제로는 매 회차 로그인 폼에 비밀번호가 다시 제출된다 — '비밀번호 없이 수집' 이라는 목적이 <b>아무 로그도 남기지 않고</b> 무너진다. 그리고 그 상태에서는 세션이
 * 왜 없는지 아무도 묻지 않게 되어 영원히 복구되지 않는다.
 *
 * <p>그래서 건너뛴다. 사유는 {@code jbg_collect_log} 에 SKIPPED 로 남고 화면에 그대로 나가므로, 사람이 '브라우저로 로그인' 을 다시 눌러 복구할
 * 수 있다. 폴백은 그 신호를 지우지만 건너뜀은 남긴다.
 *
 * <h2>부분 주입은 만료로 보지 않는다</h2>
 *
 * <p>{@code inject} 는 되읽어 확인한 개수를 돌려주고, 요청보다 적으면 경고만 남긴다. 이 값이 적다는 것을 <b>만료로 단정하지 않는다.</b> 캡처는 CDP
 * {@code Network.getAllCookies} 로 도메인을 가리지 않고 전량을 뜨므로 스냅샷에는 대상 몰과 무관한 호스트의 쿠키가 섞여 있고, 그중 일부가 거부되는
 * 것은 <b>정상</b>이다. 만료로 보면 멀쩡한 세션이 매 회차 건너뛰어진다.
 *
 * <p>대신 두 가지는 가른다 — 반영이 <b>0개</b>면 주입이 성립하지 않은 것이므로 건너뛴다. 그리고 진짜 만료 판정은 <b>회원 페이지에 도달했는가</b>로 한다.
 * 그것이 이 축에서 실측 가능한 유일한 신호다.
 *
 * <h2>만료 판정은 여기서 하지 않는다 — {@link SessionExpiryDetector} 에 맡긴다</h2>
 *
 * <p>이 클래스는 한때 자기 상수({@code MEMBER_MARKER}·{@code LOGIN_MARKER})로 도달 여부를 직접 갈랐다. 같은 지식이 {@code
 * MallRegistry.loginSignals()} 에도 선언돼 있었으므로 <b>같은 사실이 두 곳에 있었다.</b> 한쪽만 고치면 컴파일도 테스트도 통과하는데 판정만
 * 어긋난다 — 이 프로젝트가 반복해서 고쳐 온 결함 형태 그대로다. 그래서 판정을 레지스트리 선언 한 곳으로 모으고, 여기서는 관측기를 <b>부르기만</b> 한다.
 *
 * <p>덤으로 판정 축이 하나 늘었다. 예전 판정은 주소만 봤는데, 사이트가 주소를 바꾸지 않고 화면만 로그인 폼으로 갈아 끼우면 만료를 통째로 놓친다({@code
 * Ssg.isSignedIn} 을 고치게 만든 그 형태다). 관측기는 <b>로그인 요소가 보이는가</b>를 함께 본다.
 *
 * <h2>신호를 선언하지 않은 몰에서는 — 만료 판정을 하지 않고 <b>통과시킨다</b></h2>
 *
 * <p>{@code loginSignals()} 가 미선언({@code UNDECLARED})이면 관측기는 {@code NOT_JUDGED} 를 준다. 그때 예전의 주소 표식
 * 판정으로 되돌아가지 <b>않는다.</b> 그 표식({@code purchaselist})은 ssg 전용 실측값이라, 선언조차 없는 몰에 그것을 들이대는 것은 <b>추측한
 * 마커로 만료를 단정하는 것</b>과 같다. 정상 회원 페이지를 만료로 읽으면 사람이 알아채기 전까지 그 몰은 아무것도 모으지 않는다 — 만료를 한 회차 놓치는 것보다 나쁘다.
 *
 * <p>그래서 통과시킨다. 통과한 뒤 실제로 만료였다면 파싱이 0건을 내놓고, 그것은 {@code EMPTY} 로 기록되어 {@code CollectHealthPolicy} 가
 * 지켜본다 — 조용히 사라지지 않는다. 신호를 실측해 레지스트리에 채우는 순간 그 몰도 만료 판정 대상이 된다.
 *
 * <h2>프로필 락 — 캡처 프로필을 쓰지 않는다</h2>
 *
 * <p>주입용 드라이버도 {@code profileDir} 을 넘겨야 마스킹이 걸린다. 그런데 그 경로를 <b>캡처 프로필과 같게 두면</b> 사람이 '브라우저로 로그인' 창을
 * 열어 둔 동안 주기 수집이 겹치는 순간 {@code "user data directory is already in use"} 로 수집이 통째로 죽는다. 캡처 창은 사람
 * 페이스로 몇 분씩 열려 있어 겹칠 창이 넓다.
 *
 * <p>그래서 {@code <몰id>-inject} 라는 <b>별도 디렉터리</b>를 쓴다. 잃는 것이 없다 — 인증 쿠키가 세션 스코프라 프로필에는 애초에 남지 않고(그것이
 * 이 국면의 출발점이다), 여기서 프로필을 넘기는 이유는 오직 마스킹 옵션을 켜기 위해서다. 덤으로 캡처 프로필이 자동화 흔적으로 오염되지 않는다.
 *
 * <p>기동 전에 이 프로필을 문 고아 Chrome 을 정리한다. ChromeDriver 는 chrome.exe 를 띄운 뒤에 핸드셰이크하므로, 핸드셰이크가 깨지면 {@code
 * quit()} 으로 닿을 수 없는 브라우저가 락을 쥔 채 남는다 — 실측에서 고아 하나가 이후 시도 전부를 막았다.
 *
 * <h2>고아 정리 앞에 프로세스 경계 락을 잡는다</h2>
 *
 * <p>바로 위의 그 정리({@code WebDriverManager.killOrphanProfileChrome})는 이 프로필을 문 chrome.exe 를 <b>가리지 않고
 * 전부</b> 죽인다. "이 경로만 쓰는 디렉터리라 지금 물고 있는 것은 전부 고아다" 라는 전제는 <b>이 JVM 안에서만</b> 참이다. 서비스로 도는 인스턴스와 사용자가
 * 직접 띄운 인스턴스는 다른 프로세스이므로, 한쪽 수집이 도는 중에 다른 쪽이 이 정리를 부르면 <b>멀쩡히 돌고 있는 수집 브라우저</b>를 죽인다.
 *
 * <p>그래서 {@link MallProfileLock} 을 <b>정리보다 먼저</b> 잡는다. 못 잡으면({@code HELD_BY_OTHER}) 죽이지 않고 그대로 물러난다
 * — 판정은 {@code SessionProfileGate.fromLockOutcome} 에 맡기고, 결과는 {@code SkipCause.PROFILE_LOCKED} 로 값에
 * 실어 보낸다. 순서가 뒤집히면 죽인 다음에 잠그는 꼴이라 락이 아무것도 막지 못한다.
 *
 * <p><b>브레이커는 건드리지 않는다.</b> 건너뜀은 실패도 성공도 아니다({@code MallOrderUpdater.collectFromSession} 이 그 규칙을 갖고
 * 있고, 만료 처리와 같다). 프로필이 겹친 것을 실패로 세면 연속 실패가 쌓여 수집기가 자동 차단되는데, 정작 원인은 <b>다음 회차면 저절로 풀릴 일</b>이다.
 *
 * <p>{@code UNAVAILABLE}(파일 락을 걸 수 없는 환경)은 막지 않는다 — 막을 근거가 없다는 것이 게이트의 명시적 판단이다. 경고는 락 쪽이 남긴다.
 *
 * <p>획득 순서는 <b>{@code BrowserConcurrencyLimiter} 바깥 / 프로필 락 안쪽</b>이다. 제한기 자리는 {@code
 * JangBoGoManager} 가 {@code CollectAdmission} 으로 이 메서드보다 <b>앞에서</b> 잡으므로 그대로 성립한다. 뒤집으면 세마포어를 기다리는
 * 동안 프로세스 경계 락을 놀리면서 쥐게 된다.
 *
 * @author KIUNSEA
 */
public class SsgSessionCollector implements SessionCollector {

  private static final Logger log = LogManager.getLogger(SsgSessionCollector.class);

  /** {@code jbg_collect_log.collector}·브레이커 키에 쓰이는 이름. {@code MallRegistry} 선언과 같아야 한다. */
  static final String COLLECTOR_NAME = "SsgSession";

  /**
   * 주입 전에 반드시 들르는 도메인.
   *
   * <p>{@code MallRegistry.SSG_GROUP.loginUrl()} 과 같은 값이지만 <b>일부러 그것을 참조하지 않는다.</b> 저쪽은 '사람을 착지시킬
   * 로그인 지점' 이라 편의로 고를 수 있는 값이고(그 javadoc 이 그렇게 적고 있다), 이쪽은 '쿠키를 받을 도메인' 이라 바뀌면 주입이 조용히 무시된다. 뜻이 다른
   * 두 값을 한 상수로 묶으면 저쪽을 편의로 바꾼 날 이쪽이 말없이 깨진다.
   */
  static final String HOME_URL = "https://www.ssg.com/";

  /** 회원 주문내역. 프로브가 실측한 주소다. */
  static final String MEMBER_URL =
      "https://www.ssg.com/myssg/productMng/purchaseList.ssg?menu=purchaseList";

  /** 주입용 프로필 이름의 꼬리. 캡처 프로필({@code <몰id>})과 반드시 달라야 한다. */
  static final String INJECT_PROFILE_SUFFIX = "-inject";

  /** 몰 첫 화면을 연 뒤의 대기(ms). 프로브 실측값. */
  static final long AFTER_HOME_MILLIS = 1500L;

  /** 회원 페이지로 이동한 뒤의 대기(ms). 프로브 실측값. */
  static final long AFTER_MEMBER_MILLIS = 3000L;

  static final String REASON_NO_SESSION = "저장된 로그인 세션이 없다. 대시보드에서 '브라우저로 로그인' 을 먼저 실행할 것";

  static final String REASON_NOTHING_APPLIED = "세션을 주입했으나 반영된 쿠키가 0개다. '브라우저로 로그인' 을 다시 실행할 것";

  // 만료 사유 문구는 여기 두지 않는다. SessionExpiryDetector.Verdict.EXPIRED 가 들고 있는 것을 그대로
  // 실어 보낸다 — 판정한 쪽과 사유를 적는 쪽이 갈리면 같은 사실이 화면에 두 문구로 쌓이고, 사유로
  // 거르는 화면에서 한쪽이 통째로 안 보인다.

  // ── 협력자 (브라우저·DB·파일을 만지는 지점은 전부 여기로 모은다) ──────────────────────────
  //
  // 이렇게 나눈 이유는 하나다: 위 javadoc 이 적은 '호출 순서' 가 실제로 지켜지는지를 브라우저 없이
  // 검사할 수 있어야 하기 때문이다. 순서가 어긋나면 예외가 아니라 '조용한 실패' 가 되므로,
  // 실사이트에서만 드러나는 형태로 두면 아무도 회귀를 잡지 못한다.

  /**
   * 이 프로필을 물고 있는 고아 Chrome 을 정리하고 죽인 수를 돌려준다.
   *
   * <p><b>드라이버 기동과 따로 둔 이유는 순서를 검사할 수 있게 하기 위해서다.</b> 이 호출은 프로필 락을 쥔 뒤에만 일어나야 하는데, 기동 함수 안에 숨겨 두면
   * "락을 못 잡았는데도 죽였다" 는 회귀를 브라우저 없이 잡을 방법이 없다. 대역으로 호출 여부를 직접 셀 수 있어야 한다.
   */
  @FunctionalInterface
  interface OrphanSweeper {
    int sweep(Path profileDir);
  }

  /** 마스킹이 걸린 드라이버를 띄운다. */
  @FunctionalInterface
  interface DriverLauncher {
    WebDriver launch(Path profileDir);
  }

  /** 저장된 스냅샷을 되읽는다. 없으면 빈 스냅샷이다 (예외를 던지지 않는다). */
  @FunctionalInterface
  interface SnapshotLoader {
    SessionSnapshot load(String seqMall);
  }

  /** 스냅샷을 브라우저에 넣고, 되읽어 확인한 개수를 돌려준다. */
  @FunctionalInterface
  interface SnapshotInjector {
    int inject(WebDriver driver, SessionSnapshot snapshot);
  }

  /** 열려 있는 회원 주문내역 페이지에서 주문 목록을 읽는다. */
  @FunctionalInterface
  interface PurchaseListReader {
    JSONArray read(WebDriver driver);
  }

  private final String seqMall;
  private final OrphanSweeper sweeper;
  private final DriverLauncher launcher;
  private final SnapshotLoader loader;
  private final SnapshotInjector injector;
  private final PurchaseListReader reader;
  private final LongConsumer sleeper;

  /**
   * 프로덕션 배선.
   *
   * <p>여기서 만드는 것은 전부 <b>지연 실행</b>이다 — 생성자는 DB·파일·브라우저를 건드리지 않는다. {@code MallRegistry} 가 이 생성자를
   * 참조하므로 (레지스트리를 읽기만 하는 코드에서) 생성만으로 부수효과가 나면 안 된다.
   *
   * @param seqMall {@code jbg_mall.seq}. 스냅샷을 찾는 키다
   */
  public SsgSessionCollector(String seqMall) {
    this(
        seqMall,
        SsgSessionCollector::sweepOrphans,
        SsgSessionCollector::launchMaskedDriver,
        new SessionSnapshotStore()::load,
        SessionTransfer::inject,
        // 파싱은 이미 검증된 Ssg 의 것을 그대로 쓴다. 자격증명은 navigatePurchased 가 읽지 않으므로
        // null 로 만든다 — 이 경로에서 비밀번호가 메모리에 오르지 않는다는 사실이 여기서도 유지된다.
        driver -> new Ssg(null, null).navigatePurchased(driver),
        SsgSessionCollector::sleep);
  }

  /** 테스트가 협력자를 갈아 끼우는 생성자. */
  SsgSessionCollector(
      String seqMall,
      OrphanSweeper sweeper,
      DriverLauncher launcher,
      SnapshotLoader loader,
      SnapshotInjector injector,
      PurchaseListReader reader,
      LongConsumer sleeper) {
    this.seqMall = seqMall;
    this.sweeper = sweeper;
    this.launcher = launcher;
    this.loader = loader;
    this.injector = injector;
    this.reader = reader;
    this.sleeper = sleeper;
  }

  @Override
  public Result collect() {
    // 1) 세션부터 본다. 없으면 브라우저를 아예 띄우지 않는다 (클래스 javadoc 참조).
    SessionSnapshot snapshot = loader.load(seqMall);
    if (snapshot == null || snapshot.isEmpty()) {
      log.warn("{} 건너뜀 — 저장된 세션이 없다 (seq={}). 브라우저를 띄우지 않는다.", COLLECTOR_NAME, seqMall);
      return Result.skipped(SkipCause.NO_SESSION, REASON_NO_SESSION);
    }

    String profileName = injectProfileName(seqMall);
    Path profileDir = SessionProfilePolicy.profileDir(profileName);

    // 2) 프로필 락. 여기는 한 메서드 안에서 끝나므로 try-with-resources 로 충분하다.
    //    이 블록을 아래 고아 정리보다 앞에 두는 것이 이 배선의 전부다 (클래스 javadoc 참조).
    try (MallProfileLock lock = MallProfileLock.tryAcquireProfile(profileName)) {
      SessionProfileGate.Decision decision = SessionProfileGate.fromLockOutcome(lock.outcome());
      if (!decision.canProceed()) {
        // 못 잡았으면 고아 정리도 기동도 하지 않는다. 지금 그 프로필을 물고 있는 것은
        // '고아' 가 아니라 다른 프로세스가 쓰고 있는 살아 있는 브라우저다.
        log.warn("{} 건너뜀 — 프로필 '{}' 을 다른 프로세스가 쓰고 있다.", COLLECTOR_NAME, profileName);
        return Result.skipped(SkipCause.PROFILE_LOCKED, decision.reason());
      }
      // UNAVAILABLE 은 여기서 막지 않는다 — 막을 근거가 없다는 것이 게이트의 판단이다.
      return collectWithProfile(snapshot, profileDir);
    }
  }

  /**
   * 프로필 락을 쥔 상태에서 한 회차를 돌린다.
   *
   * <p>{@link #collect()} 에서 갈라낸 이유는 락 획득과 실제 수집을 눈으로 갈라 두기 위해서다 — 이 메서드에 들어왔다는 것 자체가 "그 프로필은 지금 우리
   * 것이다" 라는 뜻이고, 고아 정리를 불러도 되는 근거가 그것 하나다.
   *
   * @param snapshot 되읽은 세션 스냅샷 (비어 있지 않은 것이 전제다)
   * @param profileDir 주입용 프로필 디렉터리
   * @return 수집 결과 또는 건너뛴 사유
   */
  private Result collectWithProfile(SessionSnapshot snapshot, Path profileDir) {
    // 이 디렉터리는 주입 경로만 쓴다. 락을 쥔 지금 이 경로를 문 크롬이 있다면 이전 실패가 남긴 고아다.
    int orphans = sweeper.sweep(profileDir);
    if (orphans > 0) {
      log.info("주입용 프로필을 물고 있던 고아 브라우저 {}개를 정리했다.", orphans);
    }

    WebDriver driver = launcher.launch(profileDir);
    if (driver == null) {
      // 세션은 멀쩡한데 우리 쪽이 브라우저를 못 띄운 것이다. 건너뜀으로 뭉뚱그리면 사람이
      // '세션을 다시 뜨라' 는 엉뚱한 안내를 받고, 진짜 원인은 어디에도 남지 않는다.
      throw CollectStep.wrap(
          null,
          COLLECTOR_NAME,
          "init-webdriver",
          null,
          new IllegalStateException("WebDriver 생성 실패 (프로필=" + profileDir.getFileName() + ")"));
    }

    try {
      // 3) 대상 도메인 선방문. 이 줄을 주입 뒤로 옮기면 예외 없이 조용히 실패한다.
      CollectStep.run(driver, COLLECTOR_NAME, "visit-home", () -> driver.get(HOME_URL));
      sleeper.accept(AFTER_HOME_MILLIS);

      // 4) 주입.
      int applied =
          CollectStep.call(
              driver, COLLECTOR_NAME, "inject-session", () -> injector.inject(driver, snapshot));
      if (applied <= 0) {
        log.warn("{} 건너뜀 — 쿠키 {}개를 주입했으나 반영이 0개다.", COLLECTOR_NAME, snapshot.size());
        return Result.skipped(SkipCause.NOTHING_APPLIED, REASON_NOTHING_APPLIED);
      }
      if (applied < snapshot.size()) {
        // 만료가 아니다 — 스냅샷에는 대상 몰과 무관한 호스트의 쿠키가 섞여 있다 (클래스 javadoc 참조).
        log.info("{} 세션 주입 — 요청 {}개 중 {}개 반영.", COLLECTOR_NAME, snapshot.size(), applied);
      }

      // 5) 회원 주문내역으로 직행. signin() 은 부르지 않는다.
      CollectStep.run(
          driver, COLLECTOR_NAME, "navigate-member", () -> driver.navigate().to(MEMBER_URL));
      sleeper.accept(AFTER_MEMBER_MILLIS);

      // 파싱 '앞' 에서 만료를 확인한다. 뒤로 미루면 만료가 셀렉터 실패로 둔갑해,
      // 사람은 '다시 로그인' 대신 '사이트 구조가 바뀌었다' 를 뒤지게 된다.
      //
      // 대기 함수를 넘기는 이유는 이 클래스가 이미 하나를 들고 있기 때문이다. 관측기가 자기 것을
      // 따로 쓰면 한 회차에 대역으로 갈아 끼울 수 없는 sleep 이 하나 더 생긴다.
      SessionExpiryDetector.Verdict verdict =
          SessionExpiryDetector.observe(driver, COLLECTOR_NAME, loginSignals(seqMall), sleeper);
      if (verdict.expired()) {
        log.warn("{} 건너뜀 — 저장된 세션이 만료됐다.", COLLECTOR_NAME);
        return Result.skipped(SkipCause.SESSION_EXPIRED, verdict.reason());
      }

      // Ssg.navigatePurchased 가 같은 주소를 한 번 더 연다. 한 번의 여분 로드를 감수한 것이다 —
      // Ssg 를 무변경으로 두는 것이 이 국면의 제약이고, 위 도달 확인은 파싱 앞에 있어야 한다.
      return Result.collected(
          CollectStep.call(driver, COLLECTOR_NAME, "navigatePurchased", () -> reader.read(driver)));
    } finally {
      quitQuietly(driver);
    }
  }

  // ── 순수 함수 (브라우저·네트워크·DB·파일을 건드리지 않는다) ─────────────────────────

  /**
   * 주입용 프로필 이름.
   *
   * <p><b>캡처 프로필과 달라야 한다.</b> 캡처는 {@code MallRegistry.mallId()} 를 그대로 디렉터리 이름으로 쓴다({@code
   * SessionCaptureService.start}). 같은 이름을 쓰면 캡처 창이 열려 있는 동안 수집이 락 충돌로 죽는다.
   *
   * @param seqMall {@code jbg_mall.seq}
   * @return {@code <몰id>-inject}. 등록되지 않은 seq 면 {@code session-inject}
   */
  static String injectProfileName(String seqMall) {
    String base = MallRegistry.bySeq(seqMall).map(MallRegistry::mallId).orElse("session");
    return base + INJECT_PROFILE_SUFFIX;
  }

  /**
   * 이 몰의 로그인 화면 신호를 레지스트리에서 읽는다.
   *
   * <p>등록되지 않은 seq 면 미선언으로 본다 — 모르는 몰에 ssg 의 마커를 들이대면 정상 회원 페이지를 만료로 읽는다(클래스 javadoc 의 '통과시킨다' 참조).
   *
   * @param seqMall {@code jbg_mall.seq}
   * @return 로그인 화면 신호. 없으면 {@code UNDECLARED}
   */
  static SessionExpiryDetector.LoginSignals loginSignals(String seqMall) {
    return MallRegistry.bySeq(seqMall)
        .map(MallRegistry::loginSignals)
        .orElse(SessionExpiryDetector.LoginSignals.UNDECLARED);
  }

  // ── 프로덕션 협력자 구현 ──────────────────────────────────────────────

  /**
   * 이 프로필을 물고 있는 고아 Chrome 을 정리한다.
   *
   * <p><b>{@code MallProfileLock} 을 쥔 뒤에만 불린다</b>({@link #collectWithProfile} 안). 락 없이 부르면 다른 프로세스가
   * 같은 프로필로 돌리고 있는 살아 있는 브라우저를 죽인다 — {@code WebDriverManager.killOrphanProfileChrome} 의 호출 계약이 그
   * 사실을 적고 있다.
   */
  private static int sweepOrphans(Path profileDir) {
    return new WebDriverManager().killOrphanProfileChrome(profileDir);
  }

  /**
   * 마스킹이 걸린 드라이버를 띄운다.
   *
   * <p>{@code profileDir} 을 넘기는 것이 핵심이다 — {@code WebDriverManager} 는 이 값이 null 이면 자동화 표식 제거도 {@code
   * navigator.webdriver} 마스킹도 붙이지 않는다.
   *
   * <p>고아 정리는 여기서 하지 않는다. 그 호출은 락을 쥔 뒤라는 조건이 붙는데, 기동 함수 안에 숨겨 두면 조건이 지켜지는지를 밖에서 볼 수 없다({@link
   * OrphanSweeper} javadoc 참조).
   */
  private static WebDriver launchMaskedDriver(Path profileDir) {
    try {
      Files.createDirectories(profileDir);
    } catch (IOException e) {
      // 프로필 디렉터리를 못 만들면 마스킹 없이 도는 것이 아니라 아예 기동이 어긋난다.
      // 삼키면 '왜 매번 로그인 화면이지' 로만 보이므로 올린다 — 호출부가 FAIL 로 기록한다.
      throw new UncheckedIOException("주입용 프로필 디렉터리를 만들지 못했다: " + profileDir.getFileName(), e);
    }
    return new WebDriverManager().getWebDriver(WebDriverManager.BROWSER_NAME_CHROME, profileDir);
  }

  /** 페이지 전환 뒤의 대기. 인터럽트를 삼키지 않고 플래그를 되살린다. */
  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** 정리 단계다 — 여기서 던지면 수집 결과가 통째로 사라진다. */
  private static void quitQuietly(WebDriver driver) {
    if (driver == null) {
      return;
    }
    try {
      driver.quit();
    } catch (RuntimeException e) {
      log.debug("드라이버 종료 중 예외({})", e.getClass().getSimpleName());
    }
  }
}

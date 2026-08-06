package com.jiniebox.jangbogo.svc.util;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 수집이 동시에 띄우는 브라우저 수 제한 (Phase 5-11). <b>범위는 수집 경로다</b> — 아래 "범위" 절 참조.
 *
 * <h2>{@link MallProfileLock} 과 무엇이 다른가</h2>
 *
 * <p>둘은 겹치지 않는다.
 *
 * <ul>
 *   <li>{@code MallProfileLock} — <b>프로필 하나</b>를 <b>프로세스 사이</b>에서 보호한다. 같은 프로필을 두 Chrome 이 열면 세션이
 *       깨지기 때문이다.
 *   <li>이 클래스 — <b>수집이 띄우는 브라우저 개수</b>를 <b>이 JVM 안</b>에서 제한한다. 프로필이 서로 달라도 Chrome 을 동시에 여럿 띄우면
 *       메모리·CPU 가 무너지고, 같은 시각에 여러 몰로 나가는 트래픽은 그 자체가 눈에 띄는 패턴이다.
 * </ul>
 *
 * <p>그래서 프로필 락을 통과해도 이 제한에 걸릴 수 있고, 그 반대도 성립한다.
 *
 * <h2>⚠ 범위 — "브라우저 총 개수" 가 아니다 (2026-08-07 정정)</h2>
 *
 * <p>이 javadoc 은 오래 <b>"브라우저 총 개수를 이 JVM 안에서 제한한다"</b> 고 적고 있었다. <b>실제 배선은 수집 경로 한 곳뿐이다</b> —
 * {@code JangBoGoManager} 가 {@code CollectAdmission} 을 통해 잡는 자리 하나이며, 스케줄 수집과 즉시수집이 5-19 에서 그 통로로
 * 합쳐졌다. 그 밖에서 브라우저를 띄우는 경로가 <b>둘</b> 있고 둘 다 이 제한기 밖이다.
 *
 * <ul>
 *   <li><b>계정 연결</b>({@code JangBoGoManager.connectToMall}) — 자리를 잡지 않고 곧바로 {@code getWebDriver()}
 *       를 부른다.
 *   <li><b>세션 캡처</b>({@code SessionCaptureService}) — <b>일부러</b> 잡지 않는다. 캡처 창은 사람 페이스로 몇 분씩 열려 있어,
 *       자리를 쥐면 그동안 주기 수집이 통째로 굶는다. 그쪽 javadoc 에 그 판단이 적혀 있다.
 * </ul>
 *
 * <p>문구를 실제 범위로 좁힌 이유는, 총량 보장으로 읽으면 <b>"제한기가 있으니 동시에 하나뿐" 이라는 전제로 다른 코드를 짜게 되기</b> 때문이다. 실제로는 수집이
 * 도는 중에 사람이 계정 연결이나 세션 캡처를 누르면 Chrome 이 둘 뜬다.
 *
 * <h3>배선을 넓히지 않는 이유</h3>
 *
 * <p>넓히는 것이 옳아 보이지만 <b>지금은 하지 않는다.</b> 계정 연결은 {@code connectToMall} 이 {@code int} 로
 * <b>1(성공)/0(실패)/2(시간경과필요)</b> 를 돌려주는 규약이고, 자리를 못 잡은 경우는 셋 중 어느 것도 아니다. 실패(0)로 접으면 화면이 <b>"계정 정보가
 * 유효하지 않다"</b> 로 안내한다 — 사용자가 비밀번호를 의심하게 만드는 <b>거짓 진단</b>이고, 계정을 다시 입력하게 만드는 쪽이라 나쁘다. 제대로 하려면 {@code
 * BROWSER_BUSY} 를 네 번째 값으로 두고 호출부와 화면 안내까지 함께 고쳐야 한다.
 *
 * <p>그 변경은 이 클래스의 문제가 아니라 계정 연결 경로의 계약 변경이므로 별도 작업으로 둔다. 그때까지 <b>여기 적힌 범위가 사실</b>이다 — 지키지 못할 보장을
 * 문구로 약속하지 않는다.
 *
 * <h2>왜 기다리는가 — 프로필 락과 반대 판단이다</h2>
 *
 * <p>프로필 락은 <b>기다리지 않는다.</b> 잡고 있는 쪽이 다른 프로세스라 언제 끝날지 알 수 없기 때문이다.
 *
 * <p>이 제한은 <b>기다린다.</b> 붙잡고 있는 것이 같은 JVM 안의 다른 수집이고, 수집은 몇 분이면 끝난다는 것을 안다. 여기서 즉시 포기하면 그 몰은 <b>다음
 * 주기까지 통째로 건너뛴다</b> — 주기가 12시간이므로 반나절을 잃는다. 몇 분 기다려 차례를 받는 편이 낫다.
 *
 * <p>다만 무한정 기다리지는 않는다. 수집 하나가 멈춰 서면 대기 스레드가 쌓이기만 한다. 기본 대기 상한은 {@value #DEFAULT_TIMEOUT_SECONDS}
 * 초이고, 넘기면 {@code SKIPPED/BROWSER_BUSY} 로 기록하고 물러난다.
 *
 * <p>브라우저·네트워크·DB 를 쓰지 않는다.
 *
 * @author KIUNSEA
 */
public final class BrowserConcurrencyLimiter {

  private static final Logger logger = LogManager.getLogger(BrowserConcurrencyLimiter.class);

  /** 수집이 동시에 띄울 수 있는 브라우저 수. */
  public static final String PERMITS_PROPERTY = "jangbogo.browser.concurrency";

  /** 차례를 기다리는 상한(초). */
  public static final String TIMEOUT_PROPERTY = "jangbogo.browser.acquire-timeout-sec";

  /**
   * 기본 동시 실행 수.
   *
   * <p>1 이다. 이 앱은 개인 PC 에서 돌고 Chrome 하나가 수백 MB 를 쓴다. 늘려야 할 이유가 생기기 전까지는 늘리지 않는다.
   */
  public static final int DEFAULT_PERMITS = 1;

  /**
   * 기본 대기 상한(초).
   *
   * <p>300 초다. 실측한 수집 한 회차가 약 2분 30초이므로, 앞선 수집이 끝나기를 기다리기에 넉넉하면서 멈춘 수집에 무한정 매달리지는 않는 값이다.
   */
  public static final int DEFAULT_TIMEOUT_SECONDS = 300;

  /** 즉시수집이 차례를 기다리는 상한(초). */
  public static final String IMMEDIATE_TIMEOUT_PROPERTY =
      "jangbogo.browser.acquire-timeout-immediate-sec";

  /**
   * 즉시수집의 기본 대기 상한(초).
   *
   * <p>5 초다. {@value #DEFAULT_TIMEOUT_SECONDS} 초를 그대로 쓰지 않는 이유는 즉시수집이 <b>사람이 화면 앞에서 기다리는 동기 HTTP
   * 요청</b>이고, 선택한 몰을 하나씩 도는 루프이기 때문이다. 상한이 300 초면 최악의 경우 몰 수 × 5 분 동안 응답이 매달린다.
   *
   * <p>300 초라는 값의 근거는 "다음 주기까지 반나절을 잃느니 기다린다" 는 스케줄러의 계산이었다. 즉시수집에는 그 계산이 성립하지 않는다 — 사용자가 몇 초 뒤에 다시
   * 누르면 된다.
   */
  public static final int DEFAULT_IMMEDIATE_TIMEOUT_SECONDS = 5;

  private static volatile BrowserConcurrencyLimiter shared;

  private final Semaphore semaphore;
  private final int permits;

  /** 테스트가 직접 만들 수 있도록 열어 둔다. 운영 경로는 {@link #shared()} 를 쓴다. */
  BrowserConcurrencyLimiter(int permits) {
    if (permits < 1) {
      throw new IllegalArgumentException("동시 실행 수는 1 이상이어야 한다: " + permits);
    }
    this.permits = permits;
    // 공정 모드다. 먼저 기다린 몰이 먼저 받는다 — 아니면 한 몰이 계속 밀릴 수 있다.
    this.semaphore = new Semaphore(permits, true);
  }

  /**
   * 이 JVM 이 공유하는 제한기.
   *
   * <p>수집 경로가 세는 자리를 하나로 모으는 것이 목적이라 인스턴스가 여럿이면 의미가 없다(각자 제 몫의 자리를 따로 세게 된다). 설정은 최초 호출 시점에 한 번만
   * 읽는다.
   */
  public static BrowserConcurrencyLimiter shared() {
    BrowserConcurrencyLimiter local = shared;
    if (local == null) {
      synchronized (BrowserConcurrencyLimiter.class) {
        local = shared;
        if (local == null) {
          local = new BrowserConcurrencyLimiter(configuredPermits());
          shared = local;
          logger.info("브라우저 동시 실행 제한: {}개", local.permits);
        }
      }
    }
    return local;
  }

  /** 설정된 동시 실행 수. 값이 없거나 이상하면 기본값. */
  static int configuredPermits() {
    return positiveIntProperty(PERMITS_PROPERTY, DEFAULT_PERMITS);
  }

  /** 설정된 대기 상한(초). 0 이면 기다리지 않는다. */
  static int configuredTimeoutSeconds() {
    return nonNegativeIntProperty(TIMEOUT_PROPERTY, DEFAULT_TIMEOUT_SECONDS);
  }

  /** 설정된 즉시수집 대기 상한(초). 0 이면 기다리지 않는다. */
  static int configuredImmediateTimeoutSeconds() {
    return nonNegativeIntProperty(IMMEDIATE_TIMEOUT_PROPERTY, DEFAULT_IMMEDIATE_TIMEOUT_SECONDS);
  }

  /** 0 도 유효한 값이다 — "기다리지 않는다" 는 뜻이라 음수만 걸러 낸다. */
  private static int nonNegativeIntProperty(String property, int fallback) {
    String raw = System.getProperty(property);
    if (raw == null || raw.isBlank()) {
      return fallback;
    }
    try {
      int parsed = Integer.parseInt(raw.trim());
      return parsed >= 0 ? parsed : fallback;
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  private static int positiveIntProperty(String property, int fallback) {
    String raw = System.getProperty(property);
    if (raw == null || raw.isBlank()) {
      return fallback;
    }
    try {
      int parsed = Integer.parseInt(raw.trim());
      return parsed > 0 ? parsed : fallback;
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  /** 설정된 대기 상한만큼 기다려 자리를 잡는다. */
  public Lease acquire() {
    return acquire(configuredTimeoutSeconds() * 1000L);
  }

  /**
   * 자리를 잡는다. 최대 {@code timeoutMillis} 만큼 기다린다.
   *
   * @param timeoutMillis 대기 상한(밀리초). 0 이면 기다리지 않는다
   * @return 결과를 담은 임대권. 반드시 close 할 것
   */
  public Lease acquire(long timeoutMillis) {
    try {
      boolean got = semaphore.tryAcquire(Math.max(0L, timeoutMillis), TimeUnit.MILLISECONDS);
      if (!got) {
        logger.info(
            "브라우저 자리가 나지 않았다({}ms 대기, 제한 {}개). 이번 회차는 BROWSER_BUSY 로 건너뛴다.",
            timeoutMillis,
            permits);
      }
      return new Lease(got);
    } catch (InterruptedException e) {
      // 인터럽트를 삼키면 종료 신호가 사라진다. 플래그를 되살리고 물러난다.
      Thread.currentThread().interrupt();
      logger.info("브라우저 자리를 기다리다 인터럽트됐다. 이번 회차는 건너뛴다.");
      return new Lease(false);
    }
  }

  /** 현재 남은 자리 수. 진단용이다. */
  public int availablePermits() {
    return semaphore.availablePermits();
  }

  /** 잡은 자리. try-with-resources 로 쓴다. */
  public final class Lease implements AutoCloseable {

    private final boolean acquired;
    private boolean released;

    private Lease(boolean acquired) {
      this.acquired = acquired;
    }

    /** 자리를 잡았는지. false 면 {@code SKIPPED/BROWSER_BUSY} 다. */
    public boolean acquired() {
      return acquired;
    }

    @Override
    public void close() {
      // 두 번 놓으면 없던 자리가 생긴다. 한 번만 놓는다.
      if (acquired && !released) {
        released = true;
        semaphore.release();
      }
    }
  }
}

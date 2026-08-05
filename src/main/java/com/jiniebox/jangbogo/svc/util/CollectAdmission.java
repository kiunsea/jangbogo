package com.jiniebox.jangbogo.svc.util;

/**
 * 수집 한 회차를 시작해도 되는지 판정하고, 되면 브라우저 자리를 잡는다 (Phase 5-19).
 *
 * <h2>순서가 왜 게이트 먼저인가</h2>
 *
 * <p>게이트에 막힐 몰이 자리를 먼저 잡으면, 그 자리를 잡고 있는 동안 <b>실제로 수집할 수 있는 다른 몰이 굶는다.</b> 동시 실행 수가 기본 1 이므로 자리는
 * 하나뿐이고, 잡았다 곧바로 놓는 사이에도 대기 중인 쪽은 밀린다. 그래서 판정이 먼저다.
 *
 * <p>뒤집힌 순서는 테스트로 고정해 뒀다 — 게이트에 막힌 판정은 {@link BrowserConcurrencyLimiter#availablePermits()} 를 건드리지
 * 않는다.
 *
 * <h2>막힌 것은 실패가 아니다</h2>
 *
 * <p>이 판정은 예외를 던지지 않는다. 즉시수집 경로의 포괄 {@code catch} 가 예외를 계정 문제로 읽어 계정 연결을 끊어 버리기 때문이다. 막힌 이유는
 * <b>값으로</b> 돌려주고, 계정은 건드리지 않는다.
 *
 * <p>브라우저·네트워크·DB 를 쓰지 않는다. 세마포어 하나만 만진다.
 *
 * @author KIUNSEA
 */
public final class CollectAdmission implements AutoCloseable {

  /** 판정 결과. */
  public enum Verdict {
    /** 수집해도 된다. 브라우저 자리를 잡아 뒀다. */
    ADMITTED,
    /** 세션 프로필 게이트에 막혔다. 자리는 잡지 않았다. */
    GATE_BLOCKED,
    /** 자리가 나지 않았다. */
    BROWSER_BUSY
  }

  /** 수집 로그에 남길 코드. 게이트는 {@code Decision} 이름을 그대로 쓴다. */
  public static final String BROWSER_BUSY_CODE = "BROWSER_BUSY";

  private final Verdict verdict;
  private final String code;
  private final String reason;
  private final BrowserConcurrencyLimiter.Lease lease;

  private CollectAdmission(
      Verdict verdict, String code, String reason, BrowserConcurrencyLimiter.Lease lease) {
    this.verdict = verdict;
    this.code = code;
    this.reason = reason;
    this.lease = lease;
  }

  /**
   * 게이트를 보고, 통과하면 자리를 잡는다.
   *
   * @param gate 세션 프로필 게이트 판정. 옵트인하지 않은 몰은 {@code PROCEED} 라 그대로 통과한다
   * @param limiter 브라우저 동시 실행 제한기
   * @param acquireTimeoutMillis 자리를 기다릴 상한. 0 이면 기다리지 않는다
   * @return 판정 결과. {@link #admitted()} 가 true 면 반드시 close 할 것
   */
  public static CollectAdmission evaluate(
      SessionProfileGate.Decision gate,
      BrowserConcurrencyLimiter limiter,
      long acquireTimeoutMillis) {

    if (gate == null) {
      throw new IllegalArgumentException("게이트 판정이 없다. 통과로 간주하면 게이트가 죽은 채로 돈다.");
    }
    if (limiter == null) {
      throw new IllegalArgumentException("제한기가 없다. 없이 진행하면 브라우저 총량 제한이 사라진다.");
    }

    if (!gate.canProceed()) {
      // 자리를 잡지 않는다. 위 javadoc 의 이유.
      return new CollectAdmission(Verdict.GATE_BLOCKED, gate.name(), gate.reason(), null);
    }

    BrowserConcurrencyLimiter.Lease acquired = limiter.acquire(acquireTimeoutMillis);
    if (!acquired.acquired()) {
      return new CollectAdmission(
          Verdict.BROWSER_BUSY,
          BROWSER_BUSY_CODE,
          "다른 수집이 브라우저를 쓰고 있다 (" + acquireTimeoutMillis + "ms 기다림)",
          null);
    }
    return new CollectAdmission(Verdict.ADMITTED, null, null, acquired);
  }

  /** 수집해도 되는가. */
  public boolean admitted() {
    return verdict == Verdict.ADMITTED;
  }

  public Verdict verdict() {
    return verdict;
  }

  /** 수집 로그에 남길 코드. 통과했으면 null 이다. */
  public String code() {
    return code;
  }

  /** 사람이 읽을 사유. 통과했으면 null 이다. */
  public String reason() {
    return reason;
  }

  @Override
  public void close() {
    if (lease != null) {
      lease.close();
    }
  }

  @Override
  public String toString() {
    return admitted() ? "CollectAdmission[통과]" : "CollectAdmission[" + code + " — " + reason + "]";
  }
}

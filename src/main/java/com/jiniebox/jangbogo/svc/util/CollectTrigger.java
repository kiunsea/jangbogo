package com.jiniebox.jangbogo.svc.util;

/**
 * 수집을 누가 요청했는가 (Phase 5-19).
 *
 * <p>이 값이 가르는 것은 하나다 — <b>브라우저 자리를 얼마나 기다릴 것인가.</b>
 *
 * <ul>
 *   <li>{@link #SCHEDULED} — 아무도 화면 앞에서 기다리지 않는다. 여기서 포기하면 그 몰은 다음 주기까지 통째로 건너뛴다. 넉넉히 기다린다.
 *   <li>{@link #IMMEDIATE} — 사람이 버튼을 누르고 응답을 기다리는 동기 요청이다. 오래 매달리면 그 자체가 고장으로 보인다. 짧게 기다리고 물러난다.
 * </ul>
 *
 * <p>상한 값 자체는 {@link BrowserConcurrencyLimiter} 가 소유한다. 이 열거형은 "어느 값을 쓸 것인가" 만 정한다 — 호출부가 밀리초 숫자를
 * 직접 넘기면 그 숫자가 두 곳으로 갈라지기 때문이다.
 *
 * <p>브라우저·네트워크·DB 를 쓰지 않는다.
 *
 * @author KIUNSEA
 */
public enum CollectTrigger {

  /** 스케줄러가 주기적으로 돌린 수집. */
  SCHEDULED,

  /** 사용자가 화면에서 눌러 실행한 즉시수집. */
  IMMEDIATE;

  /** 이 요청이 브라우저 자리를 기다릴 상한(밀리초). */
  public long acquireTimeoutMillis() {
    int seconds =
        this == IMMEDIATE
            ? BrowserConcurrencyLimiter.configuredImmediateTimeoutSeconds()
            : BrowserConcurrencyLimiter.configuredTimeoutSeconds();
    return seconds * 1000L;
  }
}

package com.jiniebox.jangbogo.svc;

import com.jiniebox.jangbogo.svc.util.CollectAdmission;
import com.jiniebox.jangbogo.svc.util.SessionExpiryDetector;
import java.util.List;

/**
 * 수집 한 회차의 결과 (Phase 5-19).
 *
 * <p>예전에는 {@code List<Integer>} 하나만 돌려줬다. 그래서 <b>막힌 것과 아무것도 못 찾은 것이 같은 모양</b>이었다 — 빈 목록이 돌아오면 호출부는
 * "신규 주문이 없었다" 로 읽는다. 실제로는 이미 수집 중이라 건너뛴 회차였을 수 있다.
 *
 * <p>막힌 이유를 예외가 아니라 값으로 돌려주는 것도 의도다. 즉시수집 경로의 포괄 {@code catch} 가 예외를 계정 문제로 읽어 계정 연결을 끊기 때문에, 게이트나
 * 제한기에 막혔다고 사용자의 계정이 끊기면 안 된다.
 *
 * @param status 결과 구분
 * @param code 수집 로그에 남길 코드. 수집에 성공했으면 null
 * @param reason 사람이 읽을 사유. 수집에 성공했으면 null
 * @param newOrderSeqs 신규 주문 seq 목록. 수집하지 못했으면 빈 목록
 * @author KIUNSEA
 */
public record MallCollectOutcome(
    Status status, String code, String reason, List<Integer> newOrderSeqs) {

  /** 결과 구분. */
  public enum Status {
    /** 수집이 끝났다. */
    COLLECTED,
    /** 세션 프로필 게이트에 막혔다. */
    GATE_BLOCKED,
    /** 브라우저 자리가 나지 않았다. */
    BROWSER_BUSY,
    /** 이 몰의 수집이 이미 실행 중이다. */
    ALREADY_RUNNING,
    /**
     * 주입한 세션이 죽어 로그인 화면으로 밀렸다 (Phase 5-10).
     *
     * <p><b>새 {@code status} 값을 만들지 않았다는 점이 중요하다.</b> 여기 있는 것은 이 record 안의 결과 구분이고, {@code
     * jbg_collect_log.status} 에는 기존 {@code SKIPPED} 가 그대로 들어간다. 만료 전용 status 를 DB 에 새로 파면 {@code
     * JbgCollectLogDataAccessObject} 의 {@code getFailLogs}({@code status='FAIL'})와 {@code
     * getSummary} 를 <b>둘 다</b> 고쳐야 하고, 한쪽이라도 빠뜨리면 세션 만료가 화면에서 조용히 사라진다. {@code SKIPPED} 는 이미 저장 통로와
     * 화면 배지를 갖고 있다.
     */
    SESSION_EXPIRED
  }

  /** 수집 로그에 남길 코드. */
  public static final String ALREADY_RUNNING_CODE = "ALREADY_RUNNING";

  /**
   * 수집 로그에 남길 코드 — 세션 만료 (Phase 5-10).
   *
   * <p>이 문자열이 스케줄 수집과 즉시수집 <b>양쪽에서 같아야</b> 한다. 두 경로가 각자 문구를 들면 같은 사실이 수집 로그에 두 이름으로 쌓이고, 단계·사유로 거르는
   * 화면에서 한쪽이 통째로 안 보인다. 그래서 사유는 결과값이 들고 다니고 기록기는 그것을 그대로 적기만 한다.
   */
  public static final String SESSION_EXPIRED_CODE = "SESSION_EXPIRED";

  /** 수집 로그의 단계 이름 — 세션 프로필 게이트. 5-4 부터 쓰던 값이라 그대로 유지한다. */
  public static final String STEP_SESSION_PROFILE_GATE = "session-profile-gate";

  /** 수집 로그의 단계 이름 — 브라우저 자리 부족. */
  public static final String STEP_BROWSER_CONCURRENCY = "browser-concurrency";

  /** 수집 로그의 단계 이름 — 같은 몰 수집이 이미 실행 중. */
  public static final String STEP_ALREADY_RUNNING = "already-running";

  /**
   * 수집 로그의 단계 이름 — 세션 만료 (Phase 5-10).
   *
   * <p>{@link #STEP_SESSION_PROFILE_GATE} 와 <b>달라야 한다.</b> 게이트는 "쓸 프로필이 없다·다른 계정이 만들었다" 처럼 <b>시도하기
   * 전에</b> 막힌 것이고, 이쪽은 <b>실제로 주입해 보고</b> 로그인 화면으로 밀린 것이다. 사람이 할 일도 다르다 — 앞은 프로필 문제이고 뒤는 다시 로그인이다. 한
   * 이름으로 뭉뚱그리면 수집 로그 화면의 단계 필터가 그 둘을 갈라 주지 못한다.
   *
   * <p>{@code SessionExpiryDetector.STEP_DETECT_EXPIRY} 와 같은 값을 쓴다. 관측 도중 난 오류와 관측 결과가 같은 단계 이름으로
   * 묶여야, 화면에서 한 번에 볼 수 있다.
   */
  public static final String STEP_SESSION_EXPIRY = SessionExpiryDetector.STEP_DETECT_EXPIRY;

  public MallCollectOutcome {
    newOrderSeqs = newOrderSeqs == null ? List.of() : List.copyOf(newOrderSeqs);
  }

  /** 수집이 실제로 돌았는가. 빈 목록이어도 돈 것은 돈 것이다. */
  public boolean collected() {
    return status == Status.COLLECTED;
  }

  /**
   * 저장해 둔 세션이 죽어서 물러난 회차인가 (Phase 5-10).
   *
   * <p>호출부가 이 구분을 보는 이유는 <b>안내와 후속 조치가 다르기</b> 때문이다. 게이트·브라우저 자리 부족은 "잠시 뒤 다시" 로 풀리지만 만료는 사람이 다시
   * 로그인해야 풀린다. 같은 통에 담으면 둘 중 하나는 반드시 틀린 말을 하게 된다.
   *
   * <p><b>이 회차는 실패가 아니다.</b> {@code FAIL} 로 세면 {@code MallOrderUpdater.recordBreaker} 가 연속 실패로 계산해
   * 서킷 브레이커가 열리고, 사람이 세션을 다시 떠도 쿨다운이 끝날 때까지 그 수집기는 돌지 않는다. 사이트 장애가 아닌 일로 수집이 자동 차단되는 것이다.
   */
  public boolean sessionExpired() {
    return status == Status.SESSION_EXPIRED;
  }

  /**
   * 수집 로그에 남길 단계 이름. 수집이 돌았으면 null.
   *
   * <p>막힌 사유마다 다른 이름을 쓴다. 예전에는 기록기가 단계 이름을 {@code session-profile-gate} 로 박아 뒀는데, 브라우저 자리 부족까지 그
   * 이름으로 저장되면 <b>세션 프로필을 켠 적도 없는 사용자가 그 기능에서 원인을 찾게 된다.</b> 수집 로그 화면이 이 값으로 단계 필터를 만들기 때문이다.
   */
  public String stepName() {
    return switch (status) {
      case GATE_BLOCKED -> STEP_SESSION_PROFILE_GATE;
      case BROWSER_BUSY -> STEP_BROWSER_CONCURRENCY;
      case ALREADY_RUNNING -> STEP_ALREADY_RUNNING;
      case SESSION_EXPIRED -> STEP_SESSION_EXPIRY;
      case COLLECTED -> null;
    };
  }

  public static MallCollectOutcome success(List<Integer> newOrderSeqs) {
    return new MallCollectOutcome(Status.COLLECTED, null, null, newOrderSeqs);
  }

  /** 진입 판정에 막힌 결과로 바꾼다. */
  public static MallCollectOutcome blockedBy(CollectAdmission admission) {
    if (admission == null || admission.admitted()) {
      throw new IllegalArgumentException("막히지 않은 판정을 차단 결과로 바꿀 수 없다: " + admission);
    }
    Status status =
        admission.verdict() == CollectAdmission.Verdict.GATE_BLOCKED
            ? Status.GATE_BLOCKED
            : Status.BROWSER_BUSY;
    return new MallCollectOutcome(status, admission.code(), admission.reason(), List.of());
  }

  public static MallCollectOutcome alreadyRunning() {
    return new MallCollectOutcome(
        Status.ALREADY_RUNNING, ALREADY_RUNNING_CODE, "이 몰의 수집이 이미 실행 중이다", List.of());
  }

  /**
   * 주입한 세션이 죽어 물러난 결과로 바꾼다 (Phase 5-10).
   *
   * <p>사유를 인자로 받되 비어 있으면 {@code SessionExpiryDetector.Verdict.EXPIRED} 의 문구를 쓴다. 판정한 쪽이 더 구체적인 사정을
   * 알 수도 있어서 열어 두지만, <b>비어 있는 사유로는 만들 수 없게</b> 한다 — 사유 없는 건너뜀은 화면에서 원인 없는 공백으로 보이고, 사람이 할 수 있는 일이
   * 없다.
   *
   * @param reason 사람이 읽고 다음 행동을 알 수 있는 사유. 비어 있으면 기본 문구를 쓴다
   * @return 세션 만료 결과
   */
  public static MallCollectOutcome sessionExpired(String reason) {
    String text =
        (reason == null || reason.isBlank())
            ? SessionExpiryDetector.Verdict.EXPIRED.reason()
            : reason.trim();
    return new MallCollectOutcome(Status.SESSION_EXPIRED, SESSION_EXPIRED_CODE, text, List.of());
  }
}

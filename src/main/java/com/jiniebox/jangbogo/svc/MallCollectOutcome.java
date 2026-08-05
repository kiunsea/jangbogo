package com.jiniebox.jangbogo.svc;

import com.jiniebox.jangbogo.svc.util.CollectAdmission;
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
    ALREADY_RUNNING
  }

  /** 수집 로그에 남길 코드. */
  public static final String ALREADY_RUNNING_CODE = "ALREADY_RUNNING";

  /** 수집 로그의 단계 이름 — 세션 프로필 게이트. 5-4 부터 쓰던 값이라 그대로 유지한다. */
  public static final String STEP_SESSION_PROFILE_GATE = "session-profile-gate";

  /** 수집 로그의 단계 이름 — 브라우저 자리 부족. */
  public static final String STEP_BROWSER_CONCURRENCY = "browser-concurrency";

  /** 수집 로그의 단계 이름 — 같은 몰 수집이 이미 실행 중. */
  public static final String STEP_ALREADY_RUNNING = "already-running";

  public MallCollectOutcome {
    newOrderSeqs = newOrderSeqs == null ? List.of() : List.copyOf(newOrderSeqs);
  }

  /** 수집이 실제로 돌았는가. 빈 목록이어도 돈 것은 돈 것이다. */
  public boolean collected() {
    return status == Status.COLLECTED;
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
}

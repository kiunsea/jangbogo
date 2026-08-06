package com.jiniebox.jangbogo.svc.util;

import com.jiniebox.jangbogo.svc.util.ExecutionContextDetector.ExecutionContext;
import java.util.function.Supplier;

/**
 * 세션 프로필 재사용의 진입 게이트 (Phase 5-4 · 5-5).
 *
 * <h2>무엇을 막는가</h2>
 *
 * <p>프로필 재사용은 조건이 갖춰져야만 성립한다. 조건이 어긋난 채로 시도하면 <b>수집이 실패로 끝나는 게 아니라 로그인 화면에서 멈춘 채 타임아웃</b>되고, 로그만
 * 보고는 원인을 가릴 수 없다. 그래서 시도하기 전에 판정하고, 막힌 이유를 이름으로 남긴다.
 *
 * <p>네 가지를 구분한다.
 *
 * <ul>
 *   <li>{@link Decision#REQUIRES_USER_SESSION} — 세션 0 이라 브라우저를 띄울 자리가 없다. 사람이 로그인한 세션에서 돌려야 한다.
 *   <li>{@link Decision#PROFILE_MISSING} — 쓸 프로필이 없다. 아직 만들지 않았거나 지워졌다.
 *   <li>{@link Decision#PROFILE_OWNER_MISMATCH} — 다른 OS 계정이 만든 프로필이다. 열어도 세션이 살아나지 않는다.
 *   <li>{@link Decision#PROFILE_LOCKED} — 다른 프로세스가 그 프로필을 쓰고 있다.
 * </ul>
 *
 * <h2>이 판정이 읽는 두 값을 누가 쓰는가</h2>
 *
 * <p>{@code session_profile_name}·{@code session_profile_owner} 는 <b>세션 캡처가 성공한 그 순간에만</b>
 * 채워진다({@code JbgMallDataAccessObject.saveSessionProfileIdentity}). v0.16.0 까지는 캡처가 키/IV/마지막 로그인
 * 시각만 커밋하고 이 두 값을 쓰는 코드가 저장소에 하나도 없어서, 몰 옵트인을 켜는 순간 <b>수집이 코드 한 줄 돌기 전에 전부 {@link
 * Decision#PROFILE_MISSING} 로 건너뛰어졌다.</b> 이 게이트를 고칠 때는 그 쓰기 경로가 살아 있는지를 함께 봐야 한다 — 판정 함수만 맞아서는 아무
 * 일도 일어나지 않는다.
 *
 * <h2>꺼져 있으면 아무것도 하지 않는다</h2>
 *
 * <p>마스터 킬스위치가 꺼져 있거나 그 몰이 옵트인하지 않았으면 <b>무조건 {@link Decision#PROCEED}</b> 다. 즉 이 게이트를 수집 경로에 끼워도 기존
 * 동작은 바뀌지 않는다. 그것이 이 배선의 전제다.
 *
 * <p>순수 함수다. 브라우저·네트워크·DB·파일을 건드리지 않는다 — 락 획득처럼 부수효과가 있는 일은 호출측이 하고 결과만 넘긴다.
 *
 * @author KIUNSEA
 */
public final class SessionProfileGate {

  private SessionProfileGate() {}

  /** 게이트 판정. */
  public enum Decision {
    /** 통과. 수집을 진행한다. */
    PROCEED(null),
    /** 세션 0 — 사람이 로그인한 세션이 필요하다. */
    REQUIRES_USER_SESSION("세션 프로필을 쓰려면 사람이 로그인한 세션에서 실행해야 한다 (현재 세션 0)"),
    /**
     * 쓸 프로필이 없다.
     *
     * <p>사유 문구가 가리키는 것은 <b>대시보드에 실제로 있는 버튼 이름</b>이어야 한다. 이 문자열은 {@code jbg_collect_log} 의 SKIPPED
     * 사유로 저장되고 화면에도 그대로 나가는데, 예전 문구는 '몰 로그인 세션 만들기' 라는 <b>존재하지 않는 메뉴</b>를 지목하고 있었다 — 읽은 사람이 할 수 있는
     * 일이 없어서 막힌 채로 방치된다.
     */
    PROFILE_MISSING("세션 프로필이 없다. 대시보드에서 '브라우저로 로그인' 을 먼저 실행할 것"),
    /** 다른 OS 계정이 만든 프로필이다. */
    PROFILE_OWNER_MISMATCH("세션 프로필을 만든 OS 계정이 현재 계정과 다르다"),
    /** 다른 프로세스가 쓰고 있다. */
    PROFILE_LOCKED("세션 프로필을 다른 프로세스가 사용 중이다");

    private final String reason;

    Decision(String reason) {
      this.reason = reason;
    }

    /** 수집을 진행해도 되는지. */
    public boolean canProceed() {
      return this == PROCEED;
    }

    /** 수집 로그에 남길 사유. {@link #PROCEED} 는 null 이다. */
    public String reason() {
      return reason;
    }
  }

  /**
   * 프로필을 열기 전에 확인할 수 있는 것들을 판정한다.
   *
   * <p>락은 여기서 보지 않는다 — 락 획득은 파일을 만드는 부수효과가 있으므로, 이 판정을 통과한 뒤 호출측이 잡고 그 결과를 {@link #fromLockOutcome}
   * 로 옮긴다. 값싼 검사를 먼저 하고 비싼 것을 나중에 한다.
   *
   * <h2>실행 컨텍스트를 왜 {@link Supplier} 로 받는가 (Phase 5-18)</h2>
   *
   * <p>이 판정은 옵트인하지 않은 몰에서 첫 줄에 바로 통과한다. 그런데 값으로 받으면 <b>자바가 호출 전에 인자를 전부 평가</b>하므로, 통과할 것이 뻔한 몰에서도
   * 실행 컨텍스트 탐지가 매 회차 돈다 — 그 탐지는 {@code tasklist} 외부 프로세스를 띄우고 최대 5초를 기다린다. 옵트인 OFF 몰의 런타임 동작이 그대로여야
   * 한다는 수용 기준 10 에 어긋난다.
   *
   * <p>공급자로 받으면 단축평가가 <b>이 안으로</b> 들어와 호출부가 실수할 여지가 없어진다. 값으로 받던 때는 호출부마다 규약을 지켜야 했고, 실제로 두
   * 곳(스케줄러·기동)이 어긋나 있었다.
   *
   * @param profileApplies {@code SessionProfilePolicy.appliesTo(몰 옵트인)} 결과
   * @param contextSupplier 실행 컨텍스트 공급자. <b>적용 대상일 때만 호출된다</b>
   * @param profileName {@code session_profile_name} (없으면 null)
   * @param profileOwner {@code session_profile_owner} (없으면 null)
   * @param currentOwner 현재 OS 계정
   * @return 판정
   */
  public static Decision evaluate(
      boolean profileApplies,
      Supplier<ExecutionContext> contextSupplier,
      String profileName,
      String profileOwner,
      String currentOwner) {

    // 꺼져 있으면 기존 경로 그대로다. 이 게이트가 동작을 바꾸지 않는다는 보장이 여기서 나온다.
    // 공급자를 부르지 않는 것도 그 보장의 일부다 — 부르면 매 회차 tasklist 가 돈다.
    if (!profileApplies) {
      return Decision.PROCEED;
    }

    ExecutionContext context = contextSupplier == null ? null : contextSupplier.get();

    // 세션 0 이면 나머지를 볼 것도 없다 — 프로필이 멀쩡해도 띄울 자리가 없다.
    if (context == null || !context.canRunHeadedBrowser()) {
      return Decision.REQUIRES_USER_SESSION;
    }

    if (isBlank(profileName)) {
      return Decision.PROFILE_MISSING;
    }

    // 소유자가 기록돼 있지 않으면 검사하지 않는다 — 예전에 만든 프로필을 소유자 미기록만으로
    // 막으면 멀쩡한 프로필을 못 쓰게 된다. 기록돼 있을 때만 대조한다.
    if (!isBlank(profileOwner) && !profileOwner.trim().equalsIgnoreCase(trim(currentOwner))) {
      return Decision.PROFILE_OWNER_MISMATCH;
    }

    return Decision.PROCEED;
  }

  /**
   * 락 결과를 게이트 판정으로 옮긴다.
   *
   * <p>{@code UNAVAILABLE}(잠글 수 없는 환경)은 막지 않는다 — 막을 근거가 없다. 그 경우는 {@code MallProfileLock} 이 경고를
   * 남긴다.
   *
   * @param outcome 락 획득 결과
   * @return 판정
   */
  public static Decision fromLockOutcome(MallProfileLock.Outcome outcome) {
    return outcome == MallProfileLock.Outcome.HELD_BY_OTHER
        ? Decision.PROFILE_LOCKED
        : Decision.PROCEED;
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  private static String trim(String value) {
    return value == null ? "" : value.trim();
  }
}

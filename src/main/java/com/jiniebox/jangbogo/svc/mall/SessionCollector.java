package com.jiniebox.jangbogo.svc.mall;

import org.json.simple.JSONArray;

/**
 * 저장해 둔 세션을 브라우저에 주입해 <b>로그인 폼을 밟지 않고</b> 회원 페이지에서 수집하는 수집기 (Phase 5-9′).
 *
 * <h2>왜 MallSession 을 상속하지 않는가</h2>
 *
 * <p>{@code MallSession} 은 {@code signin(WebDriver)} 구현을 요구한다. 그런데 이 경로가 하는 일이 바로 <b>그 메서드를 부르지 않는
 * 것</b>이라, 상속하면 "불러서는 안 되는 메서드" 를 반드시 채워야 한다. 거기에 무엇을 넣어도 거짓이다 — {@code true} 는 로그인하지 않았는데 했다고 말하고,
 * {@code false} 는 멀쩡히 살아 있는 세션을 로그인 실패로 보고한다. 그 거짓은 나중에 수집이 엉뚱한 단계에서 깨질 때에야 드러난다. 계약이 다르면 타입도 나누는
 * 편이 낫다.
 *
 * <p>({@code MallSession} 무변경·기존 5개 수집기 무변경은 이 국면의 확정 제약이기도 하다. 새 경로는 별도 구현체로만 붙는다.)
 *
 * <h2>실패·건너뜀을 값으로 돌린다</h2>
 *
 * <p>세션이 없거나 만료된 것은 <b>실패가 아니라 건너뜀</b>이다. 예외로 올리면 위쪽의 포괄 {@code catch} 가 그것을 계정 문제로 읽어 계정 연결을 끊는다 —
 * 이미 한 번 실제로 일어난 사고다(5-19). 그래서 이 인터페이스는 예외가 아니라 {@link Result} 를 돌려준다.
 *
 * <p>반대로 <b>우리 쪽 고장</b>(드라이버를 못 띄웠다·페이지 구조가 어긋났다)은 예외 그대로 둔다. 그것은 사람이 세션을 다시 떠도 해결되지 않는 문제라, 건너뜀으로
 * 뭉뚱그리면 원인이 영영 드러나지 않는다. 호출부({@code MallOrderUpdater})가 그 예외를 받아 FAIL 로 기록한다.
 *
 * @author KIUNSEA
 */
public interface SessionCollector {

  /**
   * 한 회차를 돌린다.
   *
   * @return 수집 결과 또는 건너뛴 사유
   */
  Result collect();

  /**
   * 왜 건너뛰었는가 — <b>종류</b>다 (Phase 5-10 배선).
   *
   * <h2>왜 사유 문자열로는 안 되는가</h2>
   *
   * <p>건너뜀 중 <b>세션 만료 하나만</b> 위쪽에서 다르게 다뤄진다 — 그 회차는 {@code MallCollectOutcome.sessionExpired} 로
   * 승격되어 몰이 일시중단되고, 사람이 다시 로그인할 때까지 주기 수집이 물러난다. 나머지 건너뜀은 그냥 한 행 남기고 끝난다.
   *
   * <p>그 갈림을 <b>사유 문자열 비교</b>로 하면 문구를 다듬는 순간 조용히 깨진다. 문구는 화면에 그대로 나가는 값이라 앞으로도 계속 손보게 되어 있고, 고친 사람은
   * 그것이 판정 기준인 줄 모른다. 컴파일도 통과하고 테스트도 (문구를 함께 고쳤다면) 통과하는데 <b>만료만 승격되지 않는다.</b> 그래서 종류를 값으로 들고 다닌다.
   */
  enum SkipCause {

    /** 캡처해 둔 세션이 아예 없다. 사람이 '브라우저로 로그인' 을 아직 안 눌렀을 수 있다. */
    NO_SESSION,

    /** 주입은 했는데 반영된 쿠키가 0개다. 스냅샷이 깨졌거나 도메인이 어긋났다. */
    NOTHING_APPLIED,

    /**
     * 주입한 세션이 죽어 로그인 화면으로 밀렸다 — <b>이 종류만 승격된다.</b>
     *
     * <p>{@link #NO_SESSION} 을 함께 승격하지 않는 이유는 그것이 <b>정상 과도 상태</b>이기 때문이다. 옵트인만 켜고 아직 캡처하지 않은 몰이 그
     * 상태인데, 그것으로 몰을 일시중단하면 같은 몰의 자격증명 수집기(seq=1 의 Emart)까지 함께 멈춘다. 만료는 실제로 주입해 보고 <b>관측한</b> 사실이라
     * 다르다.
     */
    SESSION_EXPIRED;

    /** 이 건너뜀을 수집 결과(만료)로 승격해야 하는가. */
    public boolean sessionExpired() {
      return this == SESSION_EXPIRED;
    }
  }

  /**
   * 세션 주입 수집 한 회차의 결과.
   *
   * <p>{@code skipCause} 가 있으면 건너뜀이고, 없으면 수집이다. 둘을 한 값에 담는 이유는 호출부가 <b>둘 중 하나를 빠뜨릴 수 없게</b> 하기 위해서다
   * — 예외와 반환값으로 나누면 건너뜀이 조용히 0건 성공으로 흡수되는 경로가 생긴다.
   *
   * @param items 수집한 주문 목록. 건너뛰었으면 빈 배열
   * @param skipCause 건너뛴 <b>종류</b>. 수집했으면 null. 위쪽 분기는 이 값으로만 갈린다 ({@link SkipCause} 참조)
   * @param skipReason 건너뛴 사유. 수집했으면 null. 이 문자열은 {@code jbg_collect_log} 에 SKIPPED 사유로 저장되고 화면에도
   *     그대로 나가므로, <b>읽은 사람이 할 수 있는 일</b>을 가리켜야 한다
   */
  record Result(JSONArray items, SkipCause skipCause, String skipReason) {

    public Result {
      items = items == null ? new JSONArray() : items;
      // 종류와 사유는 함께 있거나 함께 없어야 한다. 한쪽만 있으면 둘 중 하나가 반드시 거짓말을 한다 —
      // 사유 없는 건너뜀은 화면에 원인 없는 공백으로 나가고, 종류 없는 건너뜀은 만료여도 승격되지 않는다.
      boolean hasCause = skipCause != null;
      boolean hasReason = skipReason != null && !skipReason.isBlank();
      if (hasCause != hasReason) {
        throw new IllegalArgumentException(
            "건너뜀의 종류와 사유가 어긋난다: 종류=" + skipCause + ", 사유=" + skipReason);
      }
    }

    /**
     * 수집에 성공했다.
     *
     * @param items 수집한 주문 목록. null 이면 빈 배열로 정규화한다
     * @return 결과
     */
    public static Result collected(JSONArray items) {
      return new Result(items, null, null);
    }

    /**
     * 이번 회차는 돌리지 않았다.
     *
     * @param cause 건너뛴 종류. 위쪽 분기가 이 값으로 갈린다
     * @param reason 사람이 읽고 다음 행동을 알 수 있는 사유
     * @return 결과
     */
    public static Result skipped(SkipCause cause, String reason) {
      return new Result(new JSONArray(), cause, reason);
    }

    /** 건너뛴 회차인가. */
    public boolean isSkipped() {
      return skipCause != null;
    }

    /** 저장해 둔 세션이 죽어서 물러난 회차인가. */
    public boolean isSessionExpired() {
      return skipCause != null && skipCause.sessionExpired();
    }
  }
}

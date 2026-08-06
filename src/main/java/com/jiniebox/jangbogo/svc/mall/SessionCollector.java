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
   * 세션 주입 수집 한 회차의 결과.
   *
   * <p>{@code skipReason} 이 있으면 건너뜀이고, 없으면 수집이다. 둘을 한 값에 담는 이유는 호출부가 <b>둘 중 하나를 빠뜨릴 수 없게</b> 하기
   * 위해서다 — 예외와 반환값으로 나누면 건너뜀이 조용히 0건 성공으로 흡수되는 경로가 생긴다.
   *
   * @param items 수집한 주문 목록. 건너뛰었으면 빈 배열
   * @param skipReason 건너뛴 사유. 수집했으면 null. 이 문자열은 {@code jbg_collect_log} 에 SKIPPED 사유로 저장되고 화면에도
   *     그대로 나가므로, <b>읽은 사람이 할 수 있는 일</b>을 가리켜야 한다
   */
  record Result(JSONArray items, String skipReason) {

    /**
     * 수집에 성공했다.
     *
     * @param items 수집한 주문 목록. null 이면 빈 배열로 정규화한다
     * @return 결과
     */
    public static Result collected(JSONArray items) {
      return new Result(items == null ? new JSONArray() : items, null);
    }

    /**
     * 이번 회차는 돌리지 않았다.
     *
     * @param reason 사람이 읽고 다음 행동을 알 수 있는 사유
     * @return 결과
     */
    public static Result skipped(String reason) {
      return new Result(new JSONArray(), reason);
    }

    /** 건너뛴 회차인가. */
    public boolean isSkipped() {
      return skipReason != null;
    }
  }
}

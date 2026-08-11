package com.jiniebox.jangbogo.util;

/**
 * 로그에 남는 <b>구매 데이터</b>를 가린다 (주문번호·구매일자·매장명·상품명).
 *
 * <h2>왜 필요한가 — 실제로 새고 있었다</h2>
 *
 * <p>2026-08-12 실계정 수집을 지켜보다 발견했다. 수집 한 회차가 로그에 이만큼을 평문으로 남겼다.
 *
 * <ul>
 *   <li>주문번호 전체 ({@code Oasis}·{@code MallOrderUpdaterRunner}·{@code JbgOrderDataAccessObject})
 *   <li>구매일자와 <b>실제 매장명</b> ({@code 이마트 ○○점})
 *   <li>상품명 ({@code MallOrderUpdaterRunner} 의 아이템 저장 로그)
 * </ul>
 *
 * <p>{@code AccountIdMasker} 가 계정 아이디에 대해 같은 일을 이미 하고 있었는데, <b>구매 데이터는 그 대상이 아니었다.</b> 그래서 "아이디는
 * 가리고 구매 이력은 그대로 남기는" 상태가 됐다 — 정작 이 프로그램이 다루는 데이터의 본체는 후자다.
 *
 * <p>{@code logs/} 가 {@code .gitignore} 되는 것으로 끝이 아니다. {@code SECURITY.md} 는 기여자에게 <b>버그 리포트에 로그를
 * 붙이라</b>고 안내하고, 그 안내는 로그 줄이 <b>그대로 붙일 수 있는 상태</b>일 때만 성립한다. 사람이 매번 손으로 지운다는 가정은 반드시 한 번은 깨진다.
 *
 * <h2>두 가지 형태를 쓰는 이유</h2>
 *
 * <p>가려야 할 값이 두 종류이고, <b>진단에 남아야 하는 것이 서로 다르다.</b>
 *
 * <ul>
 *   <li><b>주문번호·구매일자는 {@link #shape}</b> — 숫자를 {@code N} 으로 바꿔 <b>형식만</b> 남긴다. 이 값들에서 사람이 묻는 질문은
 *       "값이 무엇인가" 가 아니라 <b>"형식이 깨졌는가"</b> 다(구매일자 8자리가 아니어서 스킵된 주문이 실제로 있었다). 형식이 남으면 그 질문에 답할 수 있다.
 *   <li><b>매장명·상품명은 {@link #name}</b> — 숫자가 거의 없어 {@code shape} 로는 아무것도 가려지지 않는다. 앞 한 글자와 길이만 남긴다.
 * </ul>
 *
 * <h2>세션 ID 는 여기서 다루지 않는다 — 아예 남기지 않는다</h2>
 *
 * <p>세션 ID 는 <b>살아 있는 자격증명</b>이다. 가려서 남길 이유가 없다 — 일부만 남겨도 그것으로 좁혀 들어갈 수 있고, 진단에 세션 값이 필요한 경우가 애초에
 * 없다("누가 로그인했는가" 는 가린 아이디로 충분하다). 그래서 이 클래스에 세션용 함수를 두지 않았다. <b>함수가 있으면 언젠가 쓰인다.</b>
 *
 * <h2>되돌릴 수 없는 것이 설계다</h2>
 *
 * <p>{@link #shape} 는 숫자를 통째로 지우고, {@link #name} 은 앞 글자와 길이가 같은 서로 다른 값을 <b>같은 결과로 뭉갠다.</b> 되돌리는
 * 함수가 존재할 수 없다는 뜻이다. 해시를 쓰지 않은 이유는 {@code AccountIdMasker} 와 같다 — 후보 공간이 좁은 값은 해시를 찍어 두면 사전 대입으로
 * 돌아온다.
 *
 * <p>순수 함수다 — 로거·설정·시계·DB 를 건드리지 않는다. 어떤 입력에도 던지지 않고 null 도 돌려주지 않는다. 마스킹이 예외를 던지면 그것을 부르는 로그 줄이
 * 통째로 사라지거나 수집이 멈추는데, 진단용 한 줄을 위해 그런 위험을 지는 것은 앞뒤가 맞지 않는다.
 *
 * @author KIUNSEA
 */
public final class LogMask {

  /** 값이 아예 없다. */
  public static final String NONE = "(없음)";

  /** 값은 왔는데 비어 있다. {@link #NONE} 과 나누는 이유는 원인이 다르기 때문이다. */
  public static final String BLANK = "(빈값)";

  private LogMask() {}

  /**
   * 숫자를 {@code N} 으로 바꿔 <b>형식만</b> 남긴다. 주문번호·구매일자용.
   *
   * <p>{@code 00-0152608091-82046-8579} 는 {@code NN-NNNNNNNNNN-NNNNN-NNNN} 이 되고, {@code 20260809} 는
   * {@code NNNNNNNN} 이 된다. 자릿수와 구분자가 남으므로 <b>형식이 깨진 값</b>은 그대로 눈에 띈다 — 그것이 이 자리의 진단 목적이다.
   *
   * @param value 주문번호·구매일자 등. null·빈 문자열이어도 된다
   * @return 숫자가 지워진 문자열. 절대 null 이 아니다
   */
  public static String shape(String value) {
    if (value == null) {
      return NONE;
    }
    if (value.isBlank()) {
      return BLANK;
    }
    return value.replaceAll("\\d", "N");
  }

  /**
   * 앞 한 글자 + 고정 마스크 + 글자 수. 매장명·상품명용.
   *
   * <p>형태는 {@link AccountIdMasker#mask} 를 <b>그대로 위임해</b> 쓴다. 같은 형태를 두 벌 구현하면 한쪽만 고쳐지고, 그때 어느 쪽이 맞는지
   * 아무도 모른다 — 이 저장소가 반복해서 겪은 형태다. 쓰임(계정 아이디 / 구매 데이터)이 달라 클래스는 나누되 <b>형태의 정본은 하나</b>다.
   *
   * @param value 매장명·상품명 등. null·빈 문자열이어도 된다
   * @return 원본을 복원할 수 없는 표시 문자열. 절대 null 이 아니다
   */
  public static String name(String value) {
    return AccountIdMasker.mask(value);
  }
}

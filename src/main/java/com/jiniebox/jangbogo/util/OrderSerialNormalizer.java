package com.jiniebox.jangbogo.util;

import java.util.regex.Pattern;

/**
 * 주문번호({@code jbg_order.serial_num})의 <b>표기</b>를 하나로 맞춘다.
 *
 * <h2>왜 필요한가 — 같은 주문이 두 번 저장됐다</h2>
 *
 * <p>오아시스 주문목록의 주문번호 칸은 한때 {@code (2026-0729-1234)} 처럼 괄호로 감싸 왔고, 2026-08 실측에서는 {@code 주문번호 :
 * 00-NNNNNNNNNN-NNNNN-NNNN} 처럼 꼬리표가 붙어 온다. 수집기({@code svc.mall.Oasis})는 괄호만 벗겼으므로 꼬리표가 그대로 {@code
 * serial} 이 되어 SQLite 에 들어갔고, FTP 페이로드는 저장된 값을 그대로 실었다. 수신측에는 같은 주문이 자체 수집기로 이미 꼬리표 없이 들어가 있었는데, 양쪽
 * 중복 검사가 문자열을 그대로 견주므로 {@code 00-…} 와 {@code 주문번호 : 00-…} 를 다른 주문으로 봤다 — 수신측 운영 DB 에서 오아시스 주문 4건이
 * 실제로 두 번씩 저장됐고, 같은 날·같은 이름의 아이템 수량이 두 배가 됐다.
 *
 * <h2>어디서 쓰는가 — 두 자리 모두</h2>
 *
 * <ul>
 *   <li><b>수집기가 값을 만들 때</b> ({@code Oasis.parseOrderSummary}) — 깨끗한 값이 저장·전송되도록.
 *   <li><b>DAO 가 저장하고 견줄 때</b> ({@code JbgOrderDataAccessObject}) — 이미 꼬리표째 저장된 행과도 같은 주문으로 맞닿도록.
 *       파서만 고치면 다음 회차의 깨끗한 값이 저장된 꼬리표 값과 문자열로 안 맞아 <b>이번엔 이쪽 DB 안에서</b> 중복이 나고, 그 중복이 그대로 수신측으로 다시
 *       나간다(오아시스 수집기는 첫 쪽 전체를 매 회차 다시 읽는다).
 * </ul>
 *
 * <h2>하는 일은 셋뿐이다</h2>
 *
 * <ol>
 *   <li>앞뒤 공백 제거
 *   <li>앞에 붙은 {@code 주문번호 :} 꼬리표 제거 — 콜론(반각·전각)·공백의 유무는 가리지 않는다
 *   <li>값 전체를 감싼 괄호 제거
 * </ol>
 *
 * <p>값 안쪽은 건드리지 않는다. 하이픈과 자릿수는 주문번호의 일부이고, 영수증 번호(SSG·이마트·하나로)는 여기서 아무 변화도 겪지 않아야 한다. 순수 함수다 — 어떤
 * 입력에도 던지지 않는다.
 *
 * @author KIUNSEA
 */
public final class OrderSerialNormalizer {

  /** 앞에 붙는 꼬리표. "주문번호" / "주문 번호", 콜론 유무, 주변 공백 유무를 모두 받는다. */
  private static final Pattern LEADING_LABEL = Pattern.compile("^주문\\s*번호\\s*[:：]?\\s*");

  private OrderSerialNormalizer() {}

  /**
   * 주문번호 표기를 맞춘다.
   *
   * @param raw 수집기가 페이지에서 읽은 값, 또는 DB 에 저장돼 있던 값 (null 가능)
   * @return 정규화한 주문번호. 입력이 null 이면 null
   */
  public static String normalize(String raw) {
    if (raw == null) {
      return null;
    }
    String s = stripLabel(raw.trim());
    // 괄호로 "감싼" 형태만 벗긴다. 괄호가 어디든 있으면 앞뒤 한 글자씩 자르던 예전 방식은
    // "주문번호 : (00-…)" 같은 값에서 엉뚱한 글자를 잘라 냈다.
    if (s.length() > 2 && s.startsWith("(") && s.endsWith(")")) {
      s = stripLabel(s.substring(1, s.length() - 1).trim());
    }
    return s.trim();
  }

  private static String stripLabel(String s) {
    return LEADING_LABEL.matcher(s).replaceFirst("");
  }
}

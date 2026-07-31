package com.jiniebox.jangbogo.svc.util;

import java.util.regex.Pattern;

/**
 * 예외 메시지를 사람이 읽을 한 줄로 줄인다.
 *
 * <h2>왜 필요했나</h2>
 *
 * <p>Selenium 예외의 {@code getMessage()} 는 진단 블록을 통째로 달고 온다 — {@code Build info} / {@code System
 * info} / {@code Driver info} / {@code Command} / {@code Capabilities} / {@code Session ID}. 실측한
 * Emart 타임아웃 한 건이 <b>1,293자</b>였다.
 *
 * <p>이 값이 그대로 두 곳에 저장되고 있었다.
 *
 * <ul>
 *   <li>{@code jbg_collect_log.error_message} — 화면 목록·상세 모달에 뿌려진다
 *   <li>{@code jbg_collect_breaker.last_reason} — "사람이 읽을 사유" 칸인데 1,293자가 들어갔다
 * </ul>
 *
 * <p>전체 스택은 {@code error_detail} 이 따로 갖고 있고(같은 사례에서 15,314자), 실패 컨텍스트는 단계명·URL· 스크린샷으로 이미 잡힌다. 요약
 * 칸까지 원문을 담을 이유가 없다.
 *
 * <h2>자르는 방식</h2>
 *
 * <p>글자 수로만 자르면 정작 쓸모 있는 앞부분이 남고 뒤가 잘리는 대신, 잘린 자리에 진단 블록의 앞머리가 남는다. 그래서 <b>Selenium 이 붙이는 정형 문구를 먼저
 * 잘라내고</b> 그 다음에 길이를 맞춘다.
 *
 * @author KIUNSEA
 */
public final class ErrorSummary {

  /** 요약 최대 길이. {@code CollectStep} 이 alert 문구에 쓰던 값과 맞춘다. */
  public static final int MAX_LENGTH = 300;

  /**
   * Selenium 이 메시지 뒤에 붙이는 정형 블록의 시작 표지.
   *
   * <p>이 지점부터는 예외마다 같은 형식의 환경 정보라 요약에 값을 더하지 않는다.
   */
  private static final Pattern BOILERPLATE =
      Pattern.compile(
          "\\R\\s*(Build info:|System info:|Driver info:|Capabilities \\{|Session ID:"
              + "|For documentation on this error)",
          Pattern.CASE_INSENSITIVE);

  private ErrorSummary() {}

  /**
   * 예외 메시지를 한 줄 요약으로 줄인다.
   *
   * @param message 원본 메시지 (null 허용)
   * @return 요약. 입력이 null 이면 null, 공백뿐이면 빈 문자열
   */
  public static String summarize(String message) {
    if (message == null) {
      return null;
    }

    // 1) Selenium 정형 블록 이후를 버린다.
    String head = BOILERPLATE.split(message, 2)[0];

    // 2) 남은 줄바꿈·연속 공백을 한 칸으로 접는다.
    String flat = head.replaceAll("\\s*\\R\\s*", " ").replaceAll("\\s{2,}", " ").trim();

    // 3) 길이를 맞춘다.
    return flat.length() > MAX_LENGTH ? flat.substring(0, MAX_LENGTH) + "…" : flat;
  }

  /**
   * 예외를 {@code 예외이름: 요약} 형태의 한 줄로 만든다.
   *
   * @param t 예외 (null 허용)
   * @return 요약 문자열. {@code t} 가 null 이면 {@code "unknown"}
   */
  public static String summarize(Throwable t) {
    if (t == null) {
      return "unknown";
    }
    String summarized = summarize(t.getMessage());
    if (summarized == null || summarized.isEmpty()) {
      return t.getClass().getSimpleName();
    }
    return t.getClass().getSimpleName() + ": " + summarized;
  }
}

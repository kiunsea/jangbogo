package com.jiniebox.jangbogo.svc.util;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 살아 있는 브라우저에서 뜬 세션 (Phase 5-15).
 *
 * <h2>이것이 무엇인가</h2>
 *
 * <p>사람이 로그인한 브라우저의 쿠키를 통째로 옮겨 담은 것이다. 프로필 디렉터리를 다시 여는 방식이 성립하지 않는 몰(ADR-0001)에서 자동 수집이 로그인 상태를 얻는
 * 유일한 수단이다.
 *
 * <h2>다루는 규칙</h2>
 *
 * <p><b>이 안에 든 값은 살아 있는 인증 토큰이다.</b> 계정 비밀번호와 같은 등급으로 다뤄야 하며, 프로필 경로를 저장하던 때보다 위험이 <b>올라간다</b> —
 * 프로필은 OS 계정에 묶여 있었지만 이것은 파일 하나로 옮겨진다.
 *
 * <ul>
 *   <li>{@link #toString()} 은 <b>값을 담지 않는다.</b> 로그·예외 메시지에 그대로 실려도 토큰이 새지 않게 하기 위해서다.
 *   <li>평문으로 디스크에 쓰는 경로는 <b>여기에 없다.</b> 저장은 암호화와 함께 온다(5-16).
 * </ul>
 *
 * @author KIUNSEA
 */
public final class SessionSnapshot {

  private final List<Map<String, Object>> cookies;
  private final Instant capturedAt;

  private SessionSnapshot(List<Map<String, Object>> cookies, Instant capturedAt) {
    this.cookies = cookies;
    this.capturedAt = capturedAt;
  }

  /**
   * 캡처 결과로부터 스냅샷을 만든다.
   *
   * <p><b>패키지 안에서만 만든다.</b> 값이 살아 있는 인증 토큰이라, 아무 데서나 목록을 넣어 만들 수 있으면 다듬기 규칙({@link
   * SessionTransfer#sanitize})을 우회한 스냅샷이 돌아다니게 된다. 5-16(저장·복원)도 같은 패키지에 둔다.
   *
   * @param cookies CDP {@code Network.getAllCookies} 가 준 쿠키 목록
   * @param capturedAt 캡처 시각
   * @return 스냅샷 (원본과 분리된 복사본을 갖는다). 중첩 객체까지 깊게 복사하지는 않는다
   */
  static SessionSnapshot of(List<Map<String, Object>> cookies, Instant capturedAt) {
    List<Map<String, Object>> copy = new ArrayList<>();
    if (cookies != null) {
      for (Map<String, Object> cookie : cookies) {
        if (cookie != null && !cookie.isEmpty()) {
          copy.add(Collections.unmodifiableMap(new LinkedHashMap<>(cookie)));
        }
      }
    }
    return new SessionSnapshot(
        Collections.unmodifiableList(copy), capturedAt == null ? Instant.now() : capturedAt);
  }

  /** 빈 스냅샷. 캡처가 아무것도 얻지 못한 상태를 null 없이 표현한다. */
  public static SessionSnapshot empty() {
    return new SessionSnapshot(List.of(), Instant.EPOCH);
  }

  /**
   * 쿠키 목록.
   *
   * <p><b>패키지 밖으로 열지 않는다.</b> 이 한 줄이 열려 있으면 나중에 누군가 로그 한 줄이나 직렬화 한 줄로 세션 토큰 전량을 밖으로 내보낼 수 있고,
   * {@link #toString()} 의 방어는 그 경로를 막지 못한다. 바깥에서 필요한 판정은 {@link #size()} · {@link #isEmpty()} ·
   * {@link #hasCookie(String)} 로 충분하다.
   *
   * @return CDP 가 받는 형태의 쿠키 목록 (수정 불가)
   */
  List<Map<String, Object>> cookies() {
    return cookies;
  }

  /**
   * @return 캡처 시각
   */
  public Instant capturedAt() {
    return capturedAt;
  }

  /**
   * @return 쿠키 개수
   */
  public int size() {
    return cookies.size();
  }

  /**
   * @return 쿠키가 하나도 없으면 true
   */
  public boolean isEmpty() {
    return cookies.isEmpty();
  }

  /**
   * 이 스냅샷에 특정 이름의 쿠키가 있는지.
   *
   * <p>값이 아니라 <b>이름만</b> 본다. 세션이 살아 있는지 판정할 때 쓴다.
   *
   * @param name 쿠키 이름
   * @return 있으면 true
   */
  public boolean hasCookie(String name) {
    if (name == null) {
      return false;
    }
    return cookies.stream().anyMatch(c -> name.equals(c.get("name")));
  }

  /**
   * 값이 담기지 않은 요약.
   *
   * <p>기본 구현을 그대로 두면 로그 한 줄에 세션 토큰 전량이 실린다. 그래서 개수와 시각만 남긴다.
   */
  @Override
  public String toString() {
    return "SessionSnapshot[쿠키 " + cookies.size() + "개, 캡처 " + capturedAt + ", 값 비공개]";
  }
}

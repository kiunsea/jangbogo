package com.jiniebox.jangbogo.svc;

/**
 * 복호화된 쇼핑몰 자격증명 (Phase 5-19).
 *
 * <p>이 값은 <b>살아 있는 계정 정보</b>다. {@link #toString()} 이 비밀번호를 내보내지 않게 직접 덮어썼다 — record 가 자동으로 만드는
 * {@code toString} 은 모든 필드를 그대로 찍기 때문에, 로그 한 줄이나 예외 메시지 하나로 비밀번호가 새어 나간다.
 *
 * @author KIUNSEA
 */
public record MallCredentials(String id, String pw) {

  @Override
  public String toString() {
    return "MallCredentials[아이디 " + (id == null || id.isEmpty() ? "없음" : "있음") + ", 비밀번호 비공개]";
  }
}

package com.jiniebox.jangbogo.svc;

/**
 * 자격증명을 <b>필요해진 순간에</b> 만들어 내는 공급자 (Phase 5-19).
 *
 * <p>값이 아니라 공급자로 받는 이유는 순서 때문이다. 수집 진입 판정(세션 프로필 게이트 · 브라우저 동시 실행 제한)은 자격증명이 없어도 내릴 수 있고, 막히면 복호화할
 * 이유가 없다. 값으로 받으면 자바가 호출 전에 평가하므로 <b>막힐 회차에서도 비밀번호가 메모리에 올라온다.</b>
 *
 * <p>같은 이유로 {@code SessionProfileGate} 도 실행 컨텍스트를 공급자로 받는다 (Phase 5-18).
 *
 * <p>{@link java.util.function.Supplier} 를 쓰지 않는 것은 복호화가 검사 예외를 던지기 때문이다. 호출부의 기존 {@code
 * BadPaddingException} 처리를 그대로 두려면 예외가 그대로 올라와야 한다.
 *
 * @author KIUNSEA
 */
@FunctionalInterface
public interface MallCredentialSupplier {

  /**
   * 자격증명을 만든다. 진입 판정을 통과한 뒤에만 호출된다.
   *
   * @return 복호화된 자격증명
   * @throws Exception 복호화 실패 등. 호출부가 계정 문제로 처리한다
   */
  MallCredentials get() throws Exception;
}

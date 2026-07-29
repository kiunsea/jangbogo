package com.jiniebox.jangbogo.svc.mall;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;

/**
 * 쇼핑몰 파서 테스트용 DOM 목 조립 헬퍼 (판단 대기 8).
 *
 * <p>실사이트 HTML 을 이 세션에서 캡처할 수 없으므로(앱 기동 = 실계정 로그인) 픽스처는 손으로 세운 {@link WebElement} 트리다. 따라서 이 테스트들이
 * 검증하는 것은 <b>셀렉터가 실사이트와 맞는지가 아니라, 추출한 값을 수신측 계약으로 바꾸는 변환 규칙</b>이다 — serial 괄호 제거, 하이픈·점 제거, 헤더행
 * skip, td 개수 가드, 선택 필드 누락 허용 같은 것들. 셀렉터 정확성은 원리상 단위테스트로 알 수 없고 실사이트에서만 드러난다.
 *
 * <p>Selenium 의 {@code By} 는 {@code toString()} 기준 equals 를 갖고 있어 Mockito 인자 매칭이 그대로 동작한다.
 *
 * @author KIUNSEA
 */
final class MallDomFixtures {

  private MallDomFixtures() {}

  /** getText() 만 응답하는 잎 요소. */
  static WebElement text(String value) {
    WebElement el = mock(WebElement.class);
    when(el.getText()).thenReturn(value);
    return el;
  }

  /** getAttribute(name) 에 응답하는 요소. */
  static WebElement attr(String name, String value) {
    WebElement el = mock(WebElement.class);
    when(el.getAttribute(name)).thenReturn(value);
    return el;
  }

  /** 아무 자식도 없는 요소 (findElement 는 NoSuchElementException, findElements 는 빈 목록). */
  static WebElement empty() {
    WebElement el = mock(WebElement.class);
    when(el.getText()).thenReturn("");
    return el;
  }

  /** {@code parent.findElement(by)} 가 {@code child} 를 반환하도록 연결한다. */
  static void child(WebElement parent, By by, WebElement childEl) {
    when(parent.findElement(by)).thenReturn(childEl);
  }

  /** {@code parent.findElement(by)} 가 요소 없음을 던지도록 연결한다. */
  static void noChild(WebElement parent, By by) {
    when(parent.findElement(by)).thenThrow(new NoSuchElementException(by.toString()));
  }

  /** {@code parent.findElements(by)} 가 주어진 목록을 반환하도록 연결한다. */
  static void children(WebElement parent, By by, WebElement... childEls) {
    when(parent.findElements(by)).thenReturn(Arrays.asList(childEls));
  }

  /** {@code parent.findElements(by)} 가 빈 목록을 반환하도록 연결한다. */
  static void noChildren(WebElement parent, By by) {
    when(parent.findElements(by)).thenReturn(List.of());
  }

  /** 텍스트를 가진 셀(td/th) 목록. */
  static WebElement[] cells(String... texts) {
    WebElement[] out = new WebElement[texts.length];
    for (int i = 0; i < texts.length; i++) {
      out[i] = text(texts[i]);
    }
    return out;
  }
}

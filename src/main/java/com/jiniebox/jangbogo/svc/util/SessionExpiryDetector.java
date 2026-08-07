package com.jiniebox.jangbogo.svc.util;

import java.util.List;
import java.util.Locale;
import java.util.function.LongConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * 주입한 세션이 죽었는지를 <b>런타임 관측만으로</b> 판정한다 (Phase 5-10).
 *
 * <h2>무엇을 가르는가</h2>
 *
 * <p>세션 주입 수집은 로그인 폼을 밟지 않고 회원 페이지로 직행한다. 세션이 살아 있으면 그 페이지가 열리고, 죽어 있으면 사이트가 <b>로그인 화면으로 되민다.</b> 그
 * 되밀림은 예외가 아니다 — 그냥 다른 페이지가 열릴 뿐이라, 판정하지 않으면 뒤쪽 파서가 "셀렉터가 어긋났다"는 엉뚱한 실패로 끝난다. 그러면 사람은 '다시 로그인' 대신
 * '사이트 구조가 바뀌었다' 를 뒤진다.
 *
 * <h2>왜 '존재' 가 아니라 '보임' 인가</h2>
 *
 * <p>2차에서 {@code Ssg.isSignedIn} 을 고친 것과 같은 이유다. 사이트가 로그인/로그아웃 어포던스를 <b>DOM 에 심어두고 JS 로 감춘다</b> —
 * 실측에서 로그아웃 상태의 홈이 존재=1, 보임=0 이었다. 그래서 {@code findElements(...).isEmpty()} 로 존재만 보면 판정이 통째로 뒤집힌다.
 * 여기서도 {@code isDisplayed()} 로만 센다. SPA 라 화면이 자리 잡을 짧은 대기가 필요한 것도 같은 이유다.
 *
 * <h2>이 판정이 하지 <b>않는</b> 것 — 시간 기반 만료</h2>
 *
 * <p>"마지막 로그인으로부터 N 시간이 지나면 만료" 같은 임계값을 <b>일부러 두지 않았다.</b> 세션 수명을 실계정으로 다일 관측하는 일이 아직 끝나지 않아서, 지금
 * 숫자를 박으면 근거 없는 상수가 그대로 굳는다. 그 상수가 짧으면 <b>멀쩡히 살아 있는 세션을 매 회차 버리고</b>, 길면 죽은 세션으로 계속 두드린다. 어느 쪽이든 관측
 * 없이 만든 손해다.
 *
 * <p>그래서 이 클래스가 보는 것은 <b>지금 브라우저가 어디에 있고 무엇이 보이는가</b> 뿐이다. 시각·경과시간·저장된 타임스탬프를 읽지 않는다. 수명 측정이 끝나면 그
 * 판정은 <b>여기가 아니라</b> 별도 정책으로 붙는다 — 관측 판정과 추정 판정을 한 함수에 섞으면 어느 쪽이 막았는지 로그에서 가릴 수 없게 된다.
 *
 * <h2>선언하지 않은 몰은 판정하지 않는다</h2>
 *
 * <p>{@link LoginSignals} 가 비어 있으면 {@link Verdict#NOT_JUDGED} 다. 모르는 몰에 마커를 추측해 넣으면 <b>정상 회원 페이지를
 * 만료로 읽어 멀쩡한 수집을 멈춘다.</b> 만료를 한 회차 놓치는 것보다 그쪽이 나쁘다 — 놓친 만료는 다음 회차에 다시 드러나지만, 오탐으로 멈춘 몰은 사람이 알아채기
 * 전까지 아무것도 모으지 않는다. {@code MallRegistry.authCookieNames()} 가 같은 이유로 같은 규칙을 쓴다.
 *
 * <h2>순수 함수와 부수효과의 경계</h2>
 *
 * <p>{@code SessionProfileGate} 의 규약을 그대로 따른다 — <b>판정은 순수 함수</b>이고 브라우저·네트워크·DB·파일을 건드리지 않는다. 관측은
 * {@link #observe} 한 자리에만 모아 두고, 그 안의 WebDriver 접촉은 전부 {@link CollectStep} 으로 감싸 실패 컨텍스트(URL·타이틀
 * ·셀렉터·스크린샷)를 남긴다. 감싸지 않으면 만료 판정 중에 난 오류가 컨텍스트 없는 맨 예외로 올라가, 진단 정보를 붙이려고 만든 단계에서 진단 정보를 잃는다.
 *
 * <h2>만료는 실패가 아니다 (호출측 계약)</h2>
 *
 * <p>{@link Verdict#EXPIRED} 를 받은 호출측은 이것을 <b>FAIL 이 아니라 SKIPPED</b> 로 기록해야 한다. FAIL 로 세면 {@code
 * MallOrderUpdater.recordBreaker} 가 연속 실패로 계산해 서킷 브레이커가 열리고, 사람이 세션을 다시 떠도 쿨다운이 끝날 때까지 그 수집기는 돌지
 * 않는다. 즉 <b>사이트 장애도 아닌 일로 수집이 자동 차단된다.</b> 사유 코드는 {@code MallCollectOutcome.SESSION_EXPIRED_CODE}
 * 다.
 *
 * @author KIUNSEA
 */
public final class SessionExpiryDetector {

  private static final Logger log = LogManager.getLogger(SessionExpiryDetector.class);

  private SessionExpiryDetector() {}

  /**
   * 관측 전에 주는 짧은 대기(ms).
   *
   * <p><b>만료 임계값이 아니다.</b> 화면이 자리 잡기를 기다리는 렌더 대기다 — 회원 페이지가 SPA 로 그려지는 동안 관측하면 로그인 요소가 잠깐 보이거나 주소가
   * 아직 이전 값일 수 있고, 그 순간을 만료로 읽으면 오탐이 된다.
   *
   * <p>값은 기존 수집기들이 페이지 이동 뒤에 쓰는 대기와 같다({@code Ssg} 가 홈으로 이동한 뒤 로그인 여부를 보기 전에 쓰는 값). 새 숫자를 만들지 않고 이미
   * 그 사이트에서 통하던 값을 그대로 쓴다.
   */
  public static final long RENDER_SETTLE_MILLIS = 1500L;

  /** 실패 컨텍스트에 남길 단계 이름. {@code jbg_collect_log.step_name} 의 단계 필터에 그대로 나간다. */
  public static final String STEP_DETECT_EXPIRY = "detect-session-expiry";

  /**
   * 어떤 몰의 로그인 화면을 무엇으로 가를 것인가 — <b>선언</b>이다.
   *
   * <p>이 값들이 {@code MallRegistry} 에 모여 있는 이유는, 지금까지 같은 지식이 몰 크롤러 네 곳에 흩어져 있었기 때문이다. 흩어져 있으면 한 곳을
   * 고쳐도 나머지가 옛 값을 들고 있고, 어느 쪽이 맞는지 아무도 모른다.
   *
   * <p><b>실측한 것만 넣는다.</b> 비어 있으면 그 몰은 만료 판정을 하지 않는다(클래스 javadoc 참조).
   *
   * @param urlMarkers 주소에 이 조각이 들어 있으면 로그인 화면으로 본다(소문자 비교). 하나라도 맞으면 만료다
   * @param selectors 로그인 화면에만 <b>보이는</b> 요소의 CSS 셀렉터. 하나라도 보이면 만료다
   */
  public record LoginSignals(List<String> urlMarkers, List<String> selectors) {

    /** 아직 실측하지 않은 몰. 이 값을 가진 몰은 만료 판정 대상이 아니다. */
    public static final LoginSignals UNDECLARED = new LoginSignals(List.of(), List.of());

    public LoginSignals {
      // 빈 문자열 하나가 섞이면 어떤 주소·어떤 화면과도 맞아 떨어져 전 회차가 만료가 된다.
      // '선언은 돼 있는데 항상 만료' 라는 최악의 형태라 여기서 걸러 낸다.
      urlMarkers = normalize(urlMarkers);
      selectors = normalize(selectors);
    }

    /**
     * 이 몰의 만료를 판정할 수 있는가.
     *
     * <p>기준은 <b>셀렉터</b>다. 주소 마커만 있는 상태로 판정을 켜지 않는 이유는 {@code Ssg.isSignedIn} 의 교훈 때문이다 — 사이트가 주소를
     * 바꾸지 않고 화면만 로그인 폼으로 갈아 끼우면 주소만 보는 판정은 만료를 통째로 놓친다. 보임 기준이 이 판정의 축이고 주소는 보강이다.
     */
    public boolean isDeclared() {
      return !selectors.isEmpty();
    }

    private static List<String> normalize(List<String> values) {
      if (values == null) {
        return List.of();
      }
      return values.stream().filter(v -> v != null && !v.isBlank()).map(String::trim).toList();
    }
  }

  /** 판정 결과. */
  public enum Verdict {
    /** 세션이 살아 있다 — 회원 페이지에 그대로 있다. */
    ACTIVE(null),
    /** 세션이 죽었다 — 로그인 화면으로 밀렸다. */
    EXPIRED("저장된 로그인 세션이 만료되어 로그인 화면으로 밀렸다. '브라우저로 로그인' 을 다시 실행할 것"),
    /** 판정하지 않았다 — 이 몰의 로그인 신호가 실측되지 않았다. */
    NOT_JUDGED(null);

    private final String reason;

    Verdict(String reason) {
      this.reason = reason;
    }

    /** 만료로 판정됐는가. */
    public boolean expired() {
      return this == EXPIRED;
    }

    /**
     * 수집 로그에 남길 사유. 만료가 아니면 null 이다.
     *
     * <p>문구는 <b>읽은 사람이 할 수 있는 일</b>을 가리켜야 한다. 이 문자열은 {@code jbg_collect_log} 의 SKIPPED 사유로 저장되고 화면에
     * 그대로 나간다 — 사유가 행동으로 이어지지 않으면 남기는 의미가 없다({@code SessionProfileGate.Decision.PROFILE_MISSING} 이
     * 같은 이유로 실제 버튼 이름을 지목한다).
     */
    public String reason() {
      return reason;
    }
  }

  // ── 순수 판정 (브라우저·네트워크·DB·파일을 건드리지 않는다) ─────────────────────────

  /**
   * 관측값으로 만료 여부를 판정한다. <b>순수 함수다.</b>
   *
   * <p>주소와 보임을 <b>OR</b> 로 본다. 둘 다 요구하면 어느 한쪽만 나타나는 형태에서 만료를 놓친다 — 사이트가 주소를 바꾸지 않고 폼만 띄우는 경우와, 주소만
   * 로그인으로 바뀌고 폼이 아직 안 그려진 순간이 실제로 둘 다 있다.
   *
   * <p>주소를 읽지 못했으면(null·공백) 주소 쪽 판정은 하지 않는다. 읽지 못한 것을 만료로 세면 브라우저가 잠깐 흔들린 회차마다 멀쩡한 세션이 버려진다. 놓치는
   * 쪽으로 틀리는 것은 다음 회차에 다시 드러나지만, 멈추는 쪽으로 틀리면 사람이 알아채기 전까지 아무것도 모이지 않는다.
   *
   * @param signals 이 몰의 로그인 신호 선언. null 이거나 미선언이면 판정하지 않는다
   * @param currentUrl 지금 열려 있는 주소. 읽지 못했으면 null
   * @param loginElementVisible 로그인 화면 요소가 화면에 <b>보이는가</b>
   * @return 판정
   */
  public static Verdict judge(
      LoginSignals signals, String currentUrl, boolean loginElementVisible) {

    if (signals == null || !signals.isDeclared()) {
      return Verdict.NOT_JUDGED;
    }
    if (redirectedToLogin(signals, currentUrl) || loginElementVisible) {
      return Verdict.EXPIRED;
    }
    return Verdict.ACTIVE;
  }

  /**
   * 주소가 로그인 화면으로 밀렸는가.
   *
   * <p>대소문자를 가리지 않는다 — 몰이 리다이렉트 주소의 표기를 바꾸는 것만으로 판정이 죽으면 안 된다.
   *
   * @param signals 로그인 신호 선언
   * @param currentUrl 지금 열려 있는 주소
   * @return 마커가 하나라도 들어 있으면 true
   */
  static boolean redirectedToLogin(LoginSignals signals, String currentUrl) {
    if (signals == null || currentUrl == null || currentUrl.isBlank()) {
      return false;
    }
    String lower = currentUrl.toLowerCase(Locale.ROOT);
    for (String marker : signals.urlMarkers()) {
      if (lower.contains(marker.toLowerCase(Locale.ROOT))) {
        return true;
      }
    }
    return false;
  }

  /**
   * 목록 안에 화면에 실제로 보이는 요소가 하나라도 있는지 본다.
   *
   * <p>{@link StaleElementReferenceException} 은 <b>요소 하나 단위로</b> 삼킨다. 페이지 진입 직후에는 헤더가 비동기로 다시 그려질 수
   * 있어 받아둔 핸들 일부가 곧바로 무효가 되는데, 목록 전체를 감싸서 잡으면 뒤에 남은 멀쩡한 요소를 보지 못한다. 오탐의 방향만 반대로 바뀔 뿐이다. ({@code
   * Ssg.anyVisible} 이 같은 이유로 같은 처리를 한다. 그쪽은 {@code MallSession} 계열의 인스턴스 메서드라 여기서 부를 수 없어 규칙만 옮겼다.)
   *
   * @param candidates {@code findElements} 결과. null 이나 빈 목록이면 false
   * @return 보이는 요소가 하나라도 있으면 true
   */
  static boolean anyVisible(List<WebElement> candidates) {
    if (candidates == null) {
      return false;
    }
    for (WebElement candidate : candidates) {
      try {
        if (candidate != null && candidate.isDisplayed()) {
          return true;
        }
      } catch (StaleElementReferenceException stale) {
        log.debug("만료 판정 중 요소가 DOM 에서 사라짐 — 이 후보만 건너뜀: {}", stale.getMessage());
      }
    }
    return false;
  }

  // ── 부수효과 경계 (브라우저를 만지는 곳은 여기뿐이다) ────────────────────────────

  /**
   * 브라우저에서 관측한 뒤 {@link #judge} 로 판정한다.
   *
   * <p>선언이 없는 몰에서는 <b>브라우저를 건드리지도 않는다.</b> 판정하지 않을 것을 관측부터 하면 회차마다 불필요한 왕복과 대기가 생긴다.
   *
   * <p>WebDriver 접촉은 전부 {@link CollectStep} 으로 감싼다. 셀렉터 조회는 <b>셀렉터별로</b> 감싸므로, 어느 셀렉터에서 깨졌는지가 실패
   * 컨텍스트에 그대로 남는다.
   *
   * <p><b>지금 프로덕션 호출자가 없다.</b> 이 관측이 붙을 자리는 세션 주입 수집기의 회원 페이지 도달 확인 지점인데(그 수집기는 지금 주소 한 조각만 보고 만료를
   * 단정한다), 이번 변경에서 그 파일은 손대지 않기로 되어 있어 배선이 뒤로 밀렸다. 그 배선 전까지 만료 판정은 <b>일어나지 않는다</b> — 아래 기록·일시중단 경로는
   * 만들어져 있으나 입력이 오지 않는 상태다. 이 문단이 지워질 때가 배선이 끝난 때다.
   *
   * @param driver 회원 페이지로 이동을 마친 드라이버. null 이면 판정하지 않는다
   * @param collectorName 실패 컨텍스트·스크린샷 파일명에 쓸 수집기 이름
   * @param signals 이 몰의 로그인 신호 선언
   * @return 판정
   */
  public static Verdict observe(WebDriver driver, String collectorName, LoginSignals signals) {
    return observe(driver, collectorName, signals, SessionExpiryDetector::sleep);
  }

  /**
   * 대기 방식을 갈아 끼울 수 있는 형태. 테스트가 실제로 기다리지 않게 하려고 나눠 두었다.
   *
   * @param driver 회원 페이지로 이동을 마친 드라이버
   * @param collectorName 실패 컨텍스트에 쓸 수집기 이름
   * @param signals 이 몰의 로그인 신호 선언
   * @param settle 렌더 대기. null 이면 기다리지 않는다
   * @return 판정
   */
  static Verdict observe(
      WebDriver driver, String collectorName, LoginSignals signals, LongConsumer settle) {

    if (signals == null || !signals.isDeclared()) {
      return Verdict.NOT_JUDGED;
    }
    if (driver == null) {
      // 관측할 수단이 없다. 여기서 EXPIRED 를 돌려주면 우리 쪽 고장이 사용자의 세션 만료로 둔갑한다.
      return Verdict.NOT_JUDGED;
    }
    if (settle != null) {
      settle.accept(RENDER_SETTLE_MILLIS);
    }

    String currentUrl =
        CollectStep.call(driver, collectorName, STEP_DETECT_EXPIRY, driver::getCurrentUrl);
    boolean visible = anyLoginElementVisible(driver, collectorName, signals);

    Verdict verdict = judge(signals, currentUrl, visible);
    if (verdict.expired()) {
      log.warn("{} 세션 만료로 관측됨 (로그인 요소 보임={})", collectorName, visible);
    }
    return verdict;
  }

  /** 선언된 셀렉터 중 화면에 보이는 것이 하나라도 있는가. */
  private static boolean anyLoginElementVisible(
      WebDriver driver, String collectorName, LoginSignals signals) {

    for (String selector : signals.selectors()) {
      List<WebElement> found =
          CollectStep.callWithSelector(
              driver,
              collectorName,
              STEP_DETECT_EXPIRY,
              selector,
              () -> driver.findElements(By.cssSelector(selector)));
      if (anyVisible(found)) {
        return true;
      }
    }
    return false;
  }

  /** 렌더 대기. 인터럽트를 삼키지 않고 플래그를 되살린다. */
  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}

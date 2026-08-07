package com.jiniebox.jangbogo.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jiniebox.jangbogo.dto.MallAccount;
import com.jiniebox.jangbogo.svc.JangBoGoManager.CollectBlocker;
import com.jiniebox.jangbogo.svc.JangBoGoManager.CollectEligibility;
import com.jiniebox.jangbogo.svc.util.BrowserConcurrencyLimiter;
import com.jiniebox.jangbogo.svc.util.CollectTrigger;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 수집 진입 통로가 자격 판정을 요구하는지 검증 (Phase 5-20).
 *
 * <h2>왜 이 가드가 필요한가</h2>
 *
 * <p>5-19 가 세션 프로필 게이트를 공통 통로로 모았을 때 배운 것이 있다 — <b>규약을 호출부가 지키게 두면 두 경로는 언젠가 갈린다.</b> 실제로 즉시수집이
 * 게이트를 우회하고 있었고, 그전 5-18 에서도 같은 형태를 한 번 고쳤다.
 *
 * <p>그래서 자격 판정을 인자로 요구한다. {@code qualify} 를 거치지 않고는 {@code collect} 를 부를 수 없고, 앞 회차의 판정이 반복문 안에서 남아
 * 넘어가는 것도 seq 대조로 막는다.
 *
 * <p>아래 호출은 모두 <b>게이트와 브라우저 자리 확보보다 앞에서</b> 거부된다. 그래서 이 테스트는 브라우저를 띄우지 않는다 — 마지막 검사가 자리를 축내지 않았음을
 * 함께 확인한다.
 *
 * <p>브라우저·네트워크·DB 를 쓰지 않는다.
 *
 * @author KIUNSEA
 */
class CollectEntryGuardTest {

  private static final MallAccount STORED = new MallAccount("1", "mall", "cipher-id", "cipher-pw");

  @Test
  @DisplayName("판정 없이 수집을 시작할 수 없다")
  void collectRequiresAVerdict() {
    JangBoGoManager manager = new JangBoGoManager();

    assertThrows(
        IllegalArgumentException.class,
        () -> manager.collect(mall(1), null, () -> credentials(), CollectTrigger.SCHEDULED),
        "판정 없이 통과하면 이 단계가 세운 자격 판정을 통째로 비켜 가는 통로가 생긴다.");
  }

  @Test
  @DisplayName("자격이 없는 판정으로 수집을 시작할 수 없다")
  void collectRefusesAnUnqualifiedVerdict() {
    JangBoGoManager manager = new JangBoGoManager();
    CollectEligibility rejected =
        new CollectEligibility(
            "1", CollectEligibility.Route.NONE, null, CollectBlocker.ACCOUNT_NOT_FOUND);

    assertThrows(
        IllegalArgumentException.class,
        () -> manager.collect(mall(1), rejected, () -> credentials(), CollectTrigger.SCHEDULED));
  }

  @Test
  @DisplayName("다른 몰의 판정으로 수집을 시작할 수 없다")
  void collectRefusesAVerdictForAnotherMall() {
    // 판정과 실행이 떨어져 있으면 반복문 안에서 앞 회차의 값이 남아 넘어가는 형태가 언젠가 생긴다.
    JangBoGoManager manager = new JangBoGoManager();
    CollectEligibility other =
        new CollectEligibility("2", CollectEligibility.Route.CREDENTIALS, STORED, null);

    assertThrows(
        IllegalArgumentException.class,
        () -> manager.collect(mall(1), other, () -> credentials(), CollectTrigger.SCHEDULED));
  }

  @Test
  @DisplayName("자격증명 경로인데 공급자가 없으면 거부한다 — 그리고 브라우저 자리를 축내지 않는다")
  void collectRefusesACredentialRouteWithoutASupplier() {
    JangBoGoManager manager = new JangBoGoManager();
    CollectEligibility credentialRoute =
        new CollectEligibility("1", CollectEligibility.Route.CREDENTIALS, STORED, null);

    int before = BrowserConcurrencyLimiter.shared().availablePermits();

    assertThrows(
        IllegalArgumentException.class,
        () -> manager.collect(mall(1), credentialRoute, null, CollectTrigger.SCHEDULED));

    assertEquals(
        before,
        BrowserConcurrencyLimiter.shared().availablePermits(),
        "거부된 호출이 브라우저 자리를 잡았다 — 실제로 수집할 수 있는 다른 몰이 그만큼 굶는다.");
  }

  @Test
  @DisplayName("몰 정보 없이 수집을 시작할 수 없다")
  void collectRequiresTheMallRow() {
    JangBoGoManager manager = new JangBoGoManager();
    CollectEligibility credentialRoute =
        new CollectEligibility("1", CollectEligibility.Route.CREDENTIALS, STORED, null);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            manager.collect(null, credentialRoute, () -> credentials(), CollectTrigger.SCHEDULED));
  }

  private static JSONObject mall(int seq) {
    JSONObject mall = new JSONObject();
    mall.put("seq", seq);
    mall.put("account_status", 1);
    mall.put("session_profile_enabled", 0);
    return mall;
  }

  /** 실제로 호출되면 안 되는 공급자. 위 호출들은 전부 그 앞에서 거부된다. */
  private static MallCredentials credentials() {
    throw new AssertionError("거부돼야 할 호출에서 자격증명 공급자가 호출됐다.");
  }
}

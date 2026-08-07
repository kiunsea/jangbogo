package com.jiniebox.jangbogo.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jiniebox.jangbogo.svc.MallOrderUpdater.CollectOutcome;
import com.jiniebox.jangbogo.svc.MallOrderUpdater.PlannedCollector;
import com.jiniebox.jangbogo.svc.mall.MallRegistry;
import com.jiniebox.jangbogo.svc.mall.SessionCollector;
import com.jiniebox.jangbogo.svc.util.SessionProfilePolicy;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.simple.JSONArray;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 세션 주입 경로의 진입 판정과 실행 계획 검증 (Phase 5-9′).
 *
 * <p>브라우저·네트워크를 쓰지 않는다. 옵트인 조회는 대역으로 갈아 끼우고, 수집 동작은 람다로 주입한다.
 *
 * <p>여기서 지키려는 것은 수용 기준 10 이다 — <b>옵트인 OFF 몰의 런타임 동작이 한 톨도 바뀌지 않는다.</b> 그래서 "세션 경로를 안 탄다" 만 보지 않고
 * <b>DB 조회조차 늘지 않는다</b>는 것까지 잰다. 조회 한 번이 늘어난 것은 로그에 남지 않아 사람이 알아챌 방법이 없다.
 *
 * @author KIUNSEA
 */
class MallOrderUpdaterSessionRouteTest {

  private String previousEnabled;

  @BeforeEach
  void setUp() {
    previousEnabled = System.getProperty(SessionProfilePolicy.ENABLED_PROPERTY);
    System.clearProperty(SessionProfilePolicy.ENABLED_PROPERTY);
  }

  @AfterEach
  void tearDown() {
    if (previousEnabled == null) {
      System.clearProperty(SessionProfilePolicy.ENABLED_PROPERTY);
    } else {
      System.setProperty(SessionProfilePolicy.ENABLED_PROPERTY, previousEnabled);
    }
  }

  private static void killSwitchOn() {
    System.setProperty(SessionProfilePolicy.ENABLED_PROPERTY, "true");
  }

  private static JSONArray orders(String... serials) {
    JSONArray arr = new JSONArray();
    for (String serial : serials) {
      arr.add(serial);
    }
    return arr;
  }

  // ── 이중 게이트 ────────────────────────────────────────────────────

  @Test
  @DisplayName("마스터 킬스위치가 꺼져 있으면 옵트인을 조회조차 하지 않는다")
  void killSwitchOffDoesNotEvenQueryTheOptIn() {
    // 기본값이 꺼짐이므로 대부분의 실행이 이 경로다. 여기서 DB 를 읽으면 옵트인과 무관한
    // 모든 몰의 매 회차에 조회가 하나씩 늘어난다.
    AtomicBoolean queried = new AtomicBoolean(false);
    MallOrderUpdater mou =
        new MallOrderUpdater(
            seq -> {
              queried.set(true);
              return true;
            });

    assertFalse(mou.sessionRouteApplies(MallRegistry.SSG_GROUP, "1"));
    assertFalse(queried.get(), "킬스위치가 꺼져 있는데 DB 를 읽었다.");
  }

  @Test
  @DisplayName("세션 수집기를 선언하지 않은 몰은 킬스위치가 켜져 있어도 조회하지 않는다")
  void mallsWithoutASessionCollectorAreNotQueried() {
    killSwitchOn();
    AtomicBoolean queried = new AtomicBoolean(false);
    MallOrderUpdater mou =
        new MallOrderUpdater(
            seq -> {
              queried.set(true);
              return true;
            });

    assertFalse(mou.sessionRouteApplies(MallRegistry.OASIS, "2"));
    assertFalse(queried.get(), "세션 경로가 없는 몰인데 DB 를 읽었다.");
  }

  @Test
  @DisplayName("몰이 옵트인하지 않았으면 세션 경로를 쓰지 않는다")
  void mallOptOutKeepsTheExistingRoute() {
    killSwitchOn();
    MallOrderUpdater mou = new MallOrderUpdater(seq -> false);

    assertFalse(mou.sessionRouteApplies(MallRegistry.SSG_GROUP, "1"));
  }

  @Test
  @DisplayName("킬스위치와 몰 옵트인이 둘 다 켜져야 세션 경로가 열린다")
  void bothGatesMustBeOn() {
    killSwitchOn();
    MallOrderUpdater mou = new MallOrderUpdater(seq -> true);

    assertTrue(mou.sessionRouteApplies(MallRegistry.SSG_GROUP, "1"));
  }

  @Test
  @DisplayName("옵트인 조회가 실패하면 기능 기본값인 '꺼짐' 으로 읽어 기존 경로로 간다")
  void optInLookupFailureFallsBackToTheExistingRoute() {
    // 세션이 없을 때 비밀번호로 폴백하지 않는 것과는 다른 질문이다 — 저기는 옵트인한 것이
    // 확실한 상태이고, 여기는 옵트인했는지 자체를 모르는 상태다.
    killSwitchOn();
    MallOrderUpdater mou =
        new MallOrderUpdater(
            seq -> {
              throw new IllegalStateException("DB 조회 실패");
            });

    assertFalse(mou.sessionRouteApplies(MallRegistry.SSG_GROUP, "1"));
  }

  // ── 실행 계획 ──────────────────────────────────────────────────────

  @Test
  @DisplayName("게이트가 꺼져 있으면 실행 계획이 통합 전과 완전히 같다")
  void planIsUnchangedWhenTheGateIsOff() {
    List<PlannedCollector> plan =
        MallOrderUpdater.planCollectors(MallRegistry.SSG_GROUP, false, true);

    assertEquals(List.of("SSG", "Emart"), plan.stream().map(PlannedCollector::name).toList());
    assertTrue(plan.stream().noneMatch(PlannedCollector::isSession), "세션 경로가 꺼졌는데 계획에 들어갔다.");
    assertTrue(plan.stream().noneMatch(PlannedCollector::isSkipped), "자격증명이 있는데 건너뛰었다.");
  }

  @Test
  @DisplayName("게이트가 켜지면 SSG 자리만 세션 수집기로 대체되고 선언 순서는 그대로다")
  void sessionCollectorSubstitutesInPlace() {
    // 순서가 바뀌면 실행 순서가 바뀐다. 목록을 나눠 들고 다니면 대체된 자리가 맨 뒤로 밀린다.
    List<PlannedCollector> plan =
        MallOrderUpdater.planCollectors(MallRegistry.SSG_GROUP, true, true);

    assertEquals(
        List.of("SsgSession", "Emart"), plan.stream().map(PlannedCollector::name).toList());
    assertTrue(plan.get(0).isSession(), "SSG 자리가 세션 경로로 대체되지 않았다.");
    assertFalse(plan.get(1).isSession(), "Emart 는 세션 경로가 아니다 — 도메인도 로그인 폼도 다르다.");
  }

  @Test
  @DisplayName("세션 수집기를 선언하지 않은 몰은 게이트가 켜져도 계획이 그대로다")
  void mallsWithoutSessionCollectorsAreUnaffected() {
    List<PlannedCollector> plan = MallOrderUpdater.planCollectors(MallRegistry.OASIS, true, true);

    assertEquals(List.of("Oasis"), plan.stream().map(PlannedCollector::name).toList());
    assertFalse(plan.get(0).isSession());
  }

  @Test
  @DisplayName("등록되지 않은 몰은 빈 계획이다")
  void unknownMallHasNoPlan() {
    assertTrue(MallOrderUpdater.planCollectors(null, true, true).isEmpty());
    assertTrue(MallOrderUpdater.planCollectors(null, false, false).isEmpty());
  }

  // ── 자격증명이 없는 회차 ─────────────────────────────────────────────

  @Test
  @DisplayName("자격증명이 없으면 자격증명 수집기를 건너뛴다 — 세션 경로가 Emart 를 죽이지 않는다")
  void credentialCollectorsAreSkippedWhenThereAreNoCredentials() {
    // 이 검사가 없으면 세션만 있는 seq=1 에서 Emart 가 null 아이디/비밀번호로 로그인을 시도해
    // 매 회차 FAIL 이 쌓이고, 브레이커가 Emart 를 자동 차단한다. 사람이 세션을 다시 떠도
    // 쿨다운이 끝날 때까지 오프라인 영수증 수집이 돌아오지 않는다.
    List<PlannedCollector> plan =
        MallOrderUpdater.planCollectors(MallRegistry.SSG_GROUP, true, false);

    assertEquals(
        List.of("SsgSession", "Emart"),
        plan.stream().map(PlannedCollector::name).toList(),
        "자리를 통째로 없애면 사유가 어디에도 남지 않아 '왜 Emart 가 안 도는가' 를 물을 단서가 사라진다.");
    assertTrue(plan.get(0).isSession(), "세션 수집기는 비밀번호를 쓰지 않으므로 자격증명 유무와 무관하다.");
    assertTrue(plan.get(1).isSkipped(), "자격증명이 없는데 Emart 가 로그인을 시도하게 뒀다.");
    assertEquals(MallOrderUpdater.REASON_NO_CREDENTIALS, plan.get(1).skipReason());
  }

  @Test
  @DisplayName("자격증명이 없으면 세션 경로가 없는 몰은 통째로 건너뛴다")
  void mallsWithoutASessionRouteAreFullySkippedWithoutCredentials() {
    List<PlannedCollector> plan = MallOrderUpdater.planCollectors(MallRegistry.OASIS, false, false);

    assertEquals(List.of("Oasis"), plan.stream().map(PlannedCollector::name).toList());
    assertTrue(plan.get(0).isSkipped());
  }

  // ── 세션 수집기 결과 기록 ────────────────────────────────────────────

  @Test
  @DisplayName("세션 수집기가 건너뛰면 SKIPPED 로 남고 실패로 세지 않는다")
  void skipIsRecordedAsSkippedNotFailure() {
    // 실패로 세면 세션이 없다는 이유만으로 연속 실패가 쌓여 수집기가 자동 차단되고,
    // 사람이 세션을 다시 떠도 쿨다운이 끝날 때까지 돌지 않는다.
    MallOrderUpdater mou = new MallOrderUpdater(seq -> true);

    JSONArray items =
        mou.collectFromSession(
            "SsgSession",
            0,
            0,
            () ->
                SessionCollector.Result.skipped(
                    SessionCollector.SkipCause.NO_SESSION, "저장된 로그인 세션이 없다"));

    assertTrue(items.isEmpty());
    assertTrue(mou.getPartialFailures().isEmpty(), "건너뜀이 실패로 기록됐다.");

    List<CollectOutcome> outcomes = mou.getOutcomes();
    assertEquals(1, outcomes.size());
    assertEquals(CollectOutcome.SKIPPED, outcomes.get(0).status());
    assertEquals("저장된 로그인 세션이 없다", outcomes.get(0).reason(), "사유가 화면까지 그대로 나가야 한다.");
    assertFalse(outcomes.get(0).sessionExpired(), "세션 부재를 만료로 승격하면 몰이 통째로 일시중단된다.");
  }

  @Test
  @DisplayName("세션 만료는 종류로 실려 올라온다 — 사유 문자열로 판정하지 않는다")
  void expiryIsCarriedAsATypeNotAString() {
    // 문구는 화면에 나가는 값이라 앞으로도 계속 다듬게 된다. 그때 판정이 조용히 깨지면
    // 만료만 승격되지 않고, 일시중단·재개가 통째로 무동작이 된다.
    MallOrderUpdater mou = new MallOrderUpdater(seq -> true);

    mou.collectFromSession(
        "SsgSession",
        0,
        0,
        () ->
            SessionCollector.Result.skipped(
                SessionCollector.SkipCause.SESSION_EXPIRED, "문구는 언제든 바뀔 수 있다"));

    CollectOutcome outcome = mou.getOutcomes().get(0);
    assertEquals(CollectOutcome.SKIPPED, outcome.status(), "만료를 FAIL 로 세면 브레이커가 열린다.");
    assertTrue(outcome.sessionExpired(), "만료 종류가 위로 올라오지 않으면 승격 자체가 일어나지 않는다.");
    assertTrue(mou.getPartialFailures().isEmpty());
  }

  @Test
  @DisplayName("프로필이 잠긴 회차도 SKIPPED 로 남고 브레이커를 건드리지 않는다")
  void aLockedProfileIsRecordedAsSkippedNotFailure() {
    // 프로필 겹침은 다음 회차면 저절로 풀린다. 실패로 세면 연속 실패가 쌓여 브레이커가 열리고,
    // 겹침이 끝난 뒤에도 쿨다운이 지날 때까지 그 수집기가 돌지 않는다 — 원인과 증상이 완전히
    // 어긋나서 사람이 세션을 다시 떠도 아무 변화가 없다.
    MallOrderUpdater mou = new MallOrderUpdater(seq -> true);

    JSONArray items =
        mou.collectFromSession(
            "SsgSession",
            0,
            0,
            () ->
                SessionCollector.Result.skipped(
                    SessionCollector.SkipCause.PROFILE_LOCKED, "문구는 언제든 바뀔 수 있다"));

    assertTrue(items.isEmpty());
    assertTrue(mou.getPartialFailures().isEmpty(), "프로필 겹침이 실패로 기록됐다 — 브레이커가 열린다.");

    CollectOutcome outcome = mou.getOutcomes().get(0);
    assertEquals(CollectOutcome.SKIPPED, outcome.status());
    assertFalse(outcome.sessionExpired(), "프로필 겹침을 만료로 승격하면 몰이 통째로 일시중단된다.");
  }

  @Test
  @DisplayName("세션 수집기가 주문을 가져오면 SUCCESS 로 남는다")
  void collectedOrdersAreRecordedAsSuccess() {
    MallOrderUpdater mou = new MallOrderUpdater(seq -> true);

    JSONArray items =
        mou.collectFromSession(
            "SsgSession", 0, 0, () -> SessionCollector.Result.collected(orders("A", "B")));

    assertEquals(2, items.size());
    assertEquals(CollectOutcome.SUCCESS, mou.getOutcomes().get(0).status());
  }

  @Test
  @DisplayName("세션 수집기가 0건이면 EMPTY 로 남는다 — 건너뜀과 구분한다")
  void emptyResultIsRecordedAsEmpty() {
    // 0건은 '정말 안 샀다' 와 '셀렉터가 어긋났다' 를 한 회차로 구분할 수 없는 상태다.
    // 세션이 없어서 아예 돌지 않은 것(SKIPPED)과는 다른 사실이라 섞지 않는다.
    MallOrderUpdater mou = new MallOrderUpdater(seq -> true);

    mou.collectFromSession("SsgSession", 0, 0, () -> SessionCollector.Result.collected(orders()));

    assertEquals(CollectOutcome.EMPTY, mou.getOutcomes().get(0).status());
  }

  @Test
  @DisplayName("세션 수집기가 던진 예외는 컨텍스트와 함께 FAIL 로 남는다")
  void thrownExceptionIsRecordedAsFailure() {
    MallOrderUpdater mou = new MallOrderUpdater(seq -> true);

    JSONArray items =
        mou.collectFromSession(
            "SsgSession",
            0,
            0,
            () -> {
              throw new IllegalStateException("셀렉터 어긋남");
            });

    assertTrue(items.isEmpty());
    assertEquals(1, mou.getPartialFailures().size());
    assertEquals("SsgSession", mou.getPartialFailures().get(0).collector());
  }

  @Test
  @DisplayName("세션 수집기가 결과를 안 주면 0건 성공으로 묻지 않고 FAIL 로 남긴다")
  void nullResultIsAFailureNotASilentZero() {
    MallOrderUpdater mou = new MallOrderUpdater(seq -> true);

    mou.collectFromSession("SsgSession", 0, 0, () -> null);

    assertEquals(1, mou.getPartialFailures().size(), "계약 위반이 조용한 0건으로 묻혔다.");
  }
}

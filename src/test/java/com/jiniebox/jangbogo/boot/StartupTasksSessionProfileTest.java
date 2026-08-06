package com.jiniebox.jangbogo.boot;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.jiniebox.jangbogo.svc.MallSchedulerService;
import com.jiniebox.jangbogo.svc.util.ExecutionContextDetector;
import com.jiniebox.jangbogo.svc.util.SessionProfileGate;
import com.jiniebox.jangbogo.svc.util.SessionProfilePolicy;
import java.util.List;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 부팅 경로가 세션 프로필 몰을 어떻게 건너뛰는지 검증한다 (Phase 5-5 보완).
 *
 * <p>고정하려는 것은 <b>같은 사실이 부팅 한 번에 두 행 남지 않는다</b>는 것이다. 1회 수집과 스케줄 복원이 둘 다 사유를 적으면 2행이 된다.
 *
 * <p>1회 수집 쪽은 조용히 물러나고, 사유는 스케줄 복원 쪽 1행으로만 남는다.
 *
 * <p>스케줄러를 mock 으로 갈아끼운다. 진짜를 쓰면 실계정 로그인과 브라우저가 뜬다 — 테스트가 해서는 안 되는 일이다.
 *
 * <p>브라우저·네트워크·DB 를 쓰지 않는다.
 *
 * @author KIUNSEA
 */
class StartupTasksSessionProfileTest {

  private final MallSchedulerService scheduler = mock(MallSchedulerService.class);

  private String previousKillSwitch;
  private String previousExecutionContext;

  @BeforeEach
  void rememberProperties() {
    previousKillSwitch = System.getProperty(SessionProfilePolicy.ENABLED_PROPERTY);
    previousExecutionContext = System.getProperty(ExecutionContextDetector.OVERRIDE_PROPERTY);
  }

  @AfterEach
  void restoreProperties() {
    // 같은 프로퍼티를 다른 테스트 클래스도 만진다. 원래 값으로 되돌리지 않으면 실행 순서에 따라
    // 엉뚱한 곳이 깨지고, 그 실패는 원인을 찾기 어렵다.
    restore(SessionProfilePolicy.ENABLED_PROPERTY, previousKillSwitch);
    restore(ExecutionContextDetector.OVERRIDE_PROPERTY, previousExecutionContext);
  }

  @Test
  @DisplayName("세션 0 에서 세션 프로필 몰은 1회 수집을 조용히 건너뛴다")
  void initialCollectionSkipsBlockedMallSilently() {
    givenOptInBlockedBySession0();

    bootWith(scheduler, mall("1", true)).runInitialCollection();

    // 스케줄러를 아예 부르지 않는다. runOneTimeCollection 을 부르면 그 안에서 공통 게이트에 막혀
    // SKIPPED 가 한 행 적힌다. 사유도 여기서는 적지 않는다 — 그것이 "조용히" 의 뜻이다.
    verifyNoInteractions(scheduler);
  }

  @Test
  @DisplayName("부팅 한 번에 세션 프로필 사유는 정확히 1행만 남는다")
  void bootLeavesExactlyOneSkipRecord() {
    givenOptInBlockedBySession0();
    StartupTasks tasks = bootWith(scheduler, mall("1", true));

    // 실제 부팅 순서 그대로다 — 1회 수집이 먼저, 스케줄 복원이 뒤.
    tasks.runInitialCollection();
    tasks.restoreIndividualSchedules();

    // 1회 수집이 조용히 물러났으므로 수집기가 SKIPPED 를 적을 기회 자체가 없다.
    verify(scheduler, never()).runOneTimeCollection(anyString());
    verify(scheduler, never()).scheduleMall(anyString(), anyInt());

    // 사유는 스케줄 복원 쪽 한 번뿐이다. 이 횟수가 2 가 되면 수집 로그 화면에서 부팅 한 번이
    // 두 번 막힌 것으로 보인다.
    SessionProfileGate.Decision expected = SessionProfileGate.Decision.REQUIRES_USER_SESSION;
    verify(scheduler, times(1)).logSessionProfileSkip(eq("1"), anyString(), eq(expected));
  }

  @Test
  @DisplayName("사람이 로그인한 세션에서는 세션 프로필 몰도 1회 수집을 그대로 한다")
  void interactiveSessionStillCollects() {
    // 가드가 과하게 막으면 수집이 통째로 멈춘다. 막는 조건은 "세션 0" 하나뿐이어야 한다.
    System.setProperty(SessionProfilePolicy.ENABLED_PROPERTY, "true");
    System.setProperty(ExecutionContextDetector.OVERRIDE_PROPERTY, "interactive");

    bootWith(scheduler, mall("1", true)).runInitialCollection();

    verify(scheduler, times(1)).runOneTimeCollection("1");
  }

  @Test
  @DisplayName("옵트인하지 않은 몰은 세션 0 이라도 1회 수집을 한다")
  void mallWithoutOptInIsUnaffected() {
    // 세션 프로필을 켠 적 없는 사용자의 동작이 이 변경으로 달라지면 안 된다.
    givenOptInBlockedBySession0();

    bootWith(scheduler, mall("2", false)).runInitialCollection();

    verify(scheduler, times(1)).runOneTimeCollection("2");
  }

  @Test
  @DisplayName("마스터 킬스위치가 꺼져 있으면 세션 0 이라도 1회 수집을 한다")
  void killSwitchOffKeepsOldBehaviour() {
    // 킬스위치의 목적은 "코드를 다 넣어 두고도 예전처럼 돈다" 이다. 가드도 그 아래에 있어야 한다.
    System.clearProperty(SessionProfilePolicy.ENABLED_PROPERTY);
    System.setProperty(ExecutionContextDetector.OVERRIDE_PROPERTY, "service");

    bootWith(scheduler, mall("1", true)).runInitialCollection();

    verify(scheduler, times(1)).runOneTimeCollection("1");
  }

  // 유틸리티 메서드

  /** 세션 프로필 재사용은 켜져 있고 여기는 데스크톱이 없는 자리 — 가드가 걸리는 유일한 조합이다. */
  private static void givenOptInBlockedBySession0() {
    System.setProperty(SessionProfilePolicy.ENABLED_PROPERTY, "true");
    System.setProperty(ExecutionContextDetector.OVERRIDE_PROPERTY, "service");
  }

  /** 협력자를 갈아끼운 StartupTasks. DAO 대신 넘긴 목록을 읽으므로 DB 가 필요 없다. */
  private static StartupTasks bootWith(MallSchedulerService scheduler, JSONObject... malls) {
    List<JSONObject> source = List.of(malls);
    return new StartupTasks(scheduler, () -> source);
  }

  /** 자동수집이 켜진 몰 한 건. {@code sessionProfileOptIn} 이 몰별 옵트인 값이다. */
  @SuppressWarnings("unchecked")
  private static JSONObject mall(String seq, boolean sessionProfileOptIn) {
    JSONObject mall = new JSONObject();
    mall.put("seq", seq);
    mall.put("name", "테스트몰" + seq);
    mall.put("auto_collect", 1);
    mall.put("collect_interval_minutes", 720);
    mall.put("session_profile_enabled", sessionProfileOptIn ? 1 : 0);
    return mall;
  }

  private static void restore(String key, String value) {
    if (value == null) {
      System.clearProperty(key);
    } else {
      System.setProperty(key, value);
    }
  }
}

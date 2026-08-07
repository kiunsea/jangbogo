package com.jiniebox.jangbogo.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jiniebox.jangbogo.dao.JbgMallDataAccessObject;
import com.jiniebox.jangbogo.dto.MallAccount;
import com.jiniebox.jangbogo.svc.JangBoGoManager.CollectBlocker;
import com.jiniebox.jangbogo.svc.JangBoGoManager.CollectEligibility;
import com.jiniebox.jangbogo.svc.util.CollectTrigger;
import com.jiniebox.jangbogo.svc.util.SessionProfilePolicy;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * 수집 자격 판정 검증 (Phase 5-20).
 *
 * <h2>이 테스트가 지키는 것</h2>
 *
 * <p>세션 캡처는 {@code account_status} 를 건드리지 않는다. 그런데 예전 판정은 그 값과 저장된 자격증명을 요구했으므로, <b>"비밀번호 없이 세션만으로
 * 수집" 이 수집기 코드 한 줄 돌기 전에 끊겼다.</b> 아래 테스트가 그 지점을 고정한다.
 *
 * <p>동시에 <b>넓어지기만 하고 좁아지지 않았음</b>도 고정한다. 게이트가 꺼져 있으면 판정은 예전과 글자 하나까지 같아야 한다 — 사유 문구가 그대로 수집 로그와 화면에
 * 나가기 때문이다.
 *
 * <p>브라우저·네트워크·DB 를 쓰지 않는다. 판정 재료는 호출부가 이미 조회한 몰 행 하나뿐이다.
 *
 * @author KIUNSEA
 */
class CollectEligibilityTest {

  /** 저장된 계정이 있는 몰. 값은 암호문 자리라 형태만 맞으면 된다. */
  private static final MallAccount STORED = new MallAccount("1", "mall", "cipher-id", "cipher-pw");

  // ---------------------------------------------------------------
  // 게이트가 꺼져 있을 때 — 예전과 같아야 한다
  // ---------------------------------------------------------------

  @Test
  @DisplayName("자격증명이 갖춰져 있으면 예전처럼 자격증명 경로다")
  void storedCredentialsTakeTheOldRoute() throws Throwable {
    withSessionProfileSwitch(
        false,
        () -> {
          CollectEligibility verdict =
              JangBoGoManager.qualify(connectedMall(), () -> STORED, CollectTrigger.SCHEDULED);

          assertTrue(verdict.qualified());
          assertTrue(verdict.needsCredentials(), "자격증명이 있는데 복호화를 건너뛰면 로그인할 수 없다.");
          assertEquals(CollectEligibility.Route.CREDENTIALS, verdict.route());
          assertSame(STORED, verdict.account(), "판정이 읽은 계정을 호출부가 다시 읽게 하면 파일을 두 번 읽는다.");
          assertNull(verdict.reason());
        });
  }

  @Test
  @DisplayName("[대조군] 킬스위치가 꺼져 있으면 세션이 준비돼 있어도 판정이 예전과 같다")
  void sessionIsInvisibleWhileTheKillSwitchIsOff() throws Throwable {
    // 옵트인 OFF 몰의 동작이 한 톨도 바뀌지 않는다는 보장이 여기서 나온다.
    JSONObject mall = sessionOnlyMall();

    withSessionProfileSwitch(
        false,
        () -> {
          CollectEligibility verdict =
              JangBoGoManager.qualify(mall, () -> null, CollectTrigger.IMMEDIATE);

          assertFalse(verdict.qualified(), "킬스위치가 꺼졌는데 세션 경로가 살아났다.");
          assertEquals(CollectBlocker.ENCRYPT_KEY_MISSING, verdict.blocker());
          assertEquals("암호화 키/IV 누락", verdict.reason(), "예전 사유 문구가 바뀌면 수집 로그 화면의 글이 바뀐다.");
        });
  }

  @Test
  @DisplayName("사유 문구는 예전 그대로다 — 수집 로그와 화면에 그대로 나간다")
  void blockerReasonsAreUnchanged() {
    assertEquals("암호화 키/IV 누락", CollectBlocker.ENCRYPT_KEY_MISSING.reason());
    assertEquals("mall_account.yml에 계정 정보 없음", CollectBlocker.ACCOUNT_NOT_FOUND.reason());
    assertEquals("mall_account.yml에 아이디/비밀번호 비어있음", CollectBlocker.ACCOUNT_EMPTY.reason());

    // 연결한 적 없는 몰은 예전에도 기록 없이 물러났다. 기록으로 승격시키면 주기마다 같은 줄이 쌓인다.
    assertFalse(CollectBlocker.NOT_CONNECTED.worthLogging());
    assertTrue(CollectBlocker.ENCRYPT_KEY_MISSING.worthLogging());
    assertTrue(CollectBlocker.ACCOUNT_NOT_FOUND.worthLogging());
    assertTrue(CollectBlocker.ACCOUNT_EMPTY.worthLogging());
  }

  @Test
  @DisplayName("계정 파일은 앞의 값싼 검사를 통과한 뒤에만 읽는다")
  void theAccountFileIsReadLast() throws Throwable {
    AtomicInteger reads = new AtomicInteger();

    withSessionProfileSwitch(
        false,
        () -> {
          // 연결되지 않은 몰 — account_status 에서 먼저 걸린다.
          JSONObject notConnected = connectedMall();
          notConnected.put("account_status", 0);
          JangBoGoManager.qualify(
              notConnected,
              () -> {
                reads.incrementAndGet();
                return STORED;
              },
              CollectTrigger.SCHEDULED);

          // 키가 없는 몰 — 그 다음 검사에서 걸린다.
          JangBoGoManager.qualify(
              sessionOnlyMall(),
              () -> {
                reads.incrementAndGet();
                return STORED;
              },
              CollectTrigger.SCHEDULED);

          assertEquals(0, reads.get(), "막힐 회차에서도 계정 파일을 읽었다. 예전 판정 순서는 그렇지 않았다.");
        });
  }

  // ---------------------------------------------------------------
  // account_status — 넓히기만 하고 좁히지 않는다
  // ---------------------------------------------------------------

  @Test
  @DisplayName("무인 수집은 연결되지 않은 몰을 조용히 건너뛴다")
  void unattendedCollectionSkipsUnconnectedMalls() throws Throwable {
    JSONObject mall = connectedMall();
    mall.put("account_status", 0);

    withSessionProfileSwitch(
        false,
        () -> {
          CollectEligibility verdict =
              JangBoGoManager.qualify(mall, () -> STORED, CollectTrigger.SCHEDULED);

          assertFalse(verdict.qualified());
          assertEquals(CollectBlocker.NOT_CONNECTED, verdict.blocker());
          assertFalse(verdict.worthLogging(), "매 주기 같은 줄을 쌓으면 수집 로그 화면이 무의미해진다.");
        });
  }

  @Test
  @DisplayName("[대조군] 사람이 눌러 실행한 즉시수집은 예전처럼 account_status 를 보지 않는다")
  void immediateCollectionIgnoresAccountStatusAsBefore() throws Throwable {
    // 여기에 검사를 붙이면 판정이 좁아진다 — 연결을 해제해 둔 몰을 골라 눌렀을 때
    // 예전에는 돌던 것이 안 돌게 된다. 이 단계가 하려는 일은 넓히는 것이다.
    JSONObject mall = connectedMall();
    mall.put("account_status", 0);

    withSessionProfileSwitch(
        false,
        () -> {
          CollectEligibility verdict =
              JangBoGoManager.qualify(mall, () -> STORED, CollectTrigger.IMMEDIATE);

          assertTrue(verdict.qualified());
          assertEquals(CollectEligibility.Route.CREDENTIALS, verdict.route());
        });
  }

  // ---------------------------------------------------------------
  // 세션만 있는 몰 — 이 국면의 목적
  // ---------------------------------------------------------------

  @Test
  @DisplayName("자격증명이 없어도 캡처해 둔 세션이 있으면 수집 대상이다")
  void aCapturedSessionAloneQualifies() throws Throwable {
    // 세션 캡처는 account_status 를 건드리지 않는다. 예전 판정은 그 값과 저장된 자격증명을
    // 요구했으므로 이 몰은 수집기 코드 한 줄 돌기 전에 전량 건너뛰어졌다.
    JSONObject mall = sessionOnlyMall();

    withSessionProfileSwitch(
        true,
        () -> {
          CollectEligibility verdict =
              JangBoGoManager.qualify(mall, () -> null, CollectTrigger.SCHEDULED);

          assertTrue(verdict.qualified(), "세션만 있는 몰이 여전히 수집 대상에서 빠진다.");
          assertEquals(CollectEligibility.Route.SESSION, verdict.route());
          assertFalse(verdict.needsCredentials(), "복호화할 암호문이 없는데 복호화를 요구했다.");
          assertNull(verdict.account());
          assertNull(verdict.reason());
        });
  }

  @Test
  @DisplayName("몰이 옵트인하지 않았으면 세션이 준비돼 있어도 자격이 없다 — 이중 게이트")
  void theSecondGateStillApplies() throws Throwable {
    JSONObject mall = sessionOnlyMall();
    mall.put("session_profile_enabled", 0);

    withSessionProfileSwitch(
        true,
        () -> {
          CollectEligibility verdict =
              JangBoGoManager.qualify(mall, () -> null, CollectTrigger.SCHEDULED);

          assertFalse(verdict.qualified(), "마스터만 켜고 몰 옵트인 없이 세션 경로가 열렸다.");
        });
  }

  @Test
  @DisplayName("캡처를 마치지 않은 프로필은 세션 자격이 아니다")
  void anUnfinishedCaptureIsNotASession() throws Throwable {
    // 이름과 상태는 캡처가 성공한 그 순간에만 함께 채워진다. 둘 중 하나만 보고 통과시키면
    // 게이트는 통과하는데 수집이 빈 프로필을 열어 로그인 화면에서 멈춘 채 타임아웃된다.
    withSessionProfileSwitch(
        true,
        () -> {
          JSONObject noStatus = sessionOnlyMall();
          noStatus.put("session_profile_status", null);
          assertFalse(
              JangBoGoManager.qualify(noStatus, () -> null, CollectTrigger.SCHEDULED).qualified());

          JSONObject noName = sessionOnlyMall();
          noName.put("session_profile_name", "   ");
          assertFalse(
              JangBoGoManager.qualify(noName, () -> null, CollectTrigger.SCHEDULED).qualified());

          JSONObject otherStatus = sessionOnlyMall();
          otherStatus.put("session_profile_status", "EXPIRED");
          assertFalse(
              JangBoGoManager.qualify(otherStatus, () -> null, CollectTrigger.SCHEDULED)
                  .qualified());
        });
  }

  @Test
  @DisplayName("자격증명이 함께 있으면 세션이 준비돼 있어도 자격증명 경로다")
  void credentialsWinWhenBothAreAvailable() throws Throwable {
    // 세션 수집기는 자기가 선언한 자리만 대체하고 나머지는 그대로 자격증명으로 돈다.
    // 여기서 세션을 고르면 복호화를 건너뛰어 그 나머지가 아이디/비밀번호 없이 돈다.
    JSONObject mall = connectedMall();
    mall.put("session_profile_enabled", 1);
    mall.put("session_profile_name", "mall");
    mall.put("session_profile_status", JbgMallDataAccessObject.SESSION_PROFILE_STATUS_READY);

    withSessionProfileSwitch(
        true,
        () -> {
          CollectEligibility verdict =
              JangBoGoManager.qualify(mall, () -> STORED, CollectTrigger.SCHEDULED);

          assertEquals(CollectEligibility.Route.CREDENTIALS, verdict.route());
          assertTrue(verdict.needsCredentials());
        });
  }

  @Test
  @DisplayName("자격증명도 세션도 없으면 자격증명 쪽 사유를 그대로 돌려준다")
  void withoutEitherTheOldReasonSurvives() throws Throwable {
    withSessionProfileSwitch(
        true,
        () -> {
          JSONObject mall = connectedMall();
          CollectEligibility empty =
              JangBoGoManager.qualify(mall, () -> null, CollectTrigger.SCHEDULED);
          assertEquals(CollectBlocker.ACCOUNT_NOT_FOUND, empty.blocker());

          CollectEligibility blank =
              JangBoGoManager.qualify(
                  mall, () -> new MallAccount("1", "mall", "", ""), CollectTrigger.SCHEDULED);
          assertEquals(CollectBlocker.ACCOUNT_EMPTY, blank.blocker());
        });
  }

  // ---------------------------------------------------------------
  // 판정 객체 자체
  // ---------------------------------------------------------------

  @Test
  @DisplayName("자격과 사유가 어긋난 판정은 만들 수 없다")
  void aBrokenVerdictCannotBeBuilt() {
    // 자격이 있는데 사유가 붙은 판정이 흘러가면 화면에 이유 없는 건너뜀으로 나온다.
    assertThrows(
        IllegalArgumentException.class,
        () -> new CollectEligibility("1", CollectEligibility.Route.NONE, null, null),
        "자격이 없다면서 사유가 없는 판정이 만들어졌다.");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CollectEligibility(
                "1", CollectEligibility.Route.SESSION, null, CollectBlocker.ACCOUNT_EMPTY),
        "자격이 있다면서 사유가 붙은 판정이 만들어졌다.");
    assertThrows(
        IllegalArgumentException.class,
        () -> new CollectEligibility("1", CollectEligibility.Route.CREDENTIALS, null, null),
        "자격증명 경로인데 계정이 없는 판정이 만들어졌다.");
  }

  @Test
  @DisplayName("판정을 찍어도 저장된 계정이 새어 나가지 않는다")
  void toStringDoesNotLeakTheAccount() throws Throwable {
    withSessionProfileSwitch(
        false,
        () -> {
          String printed =
              JangBoGoManager.qualify(connectedMall(), () -> STORED, CollectTrigger.SCHEDULED)
                  .toString();

          assertFalse(printed.contains("cipher-id"), "판정 문자열에 계정 암호문이 실렸다: " + printed);
          assertFalse(printed.contains("cipher-pw"), "판정 문자열에 계정 암호문이 실렸다: " + printed);
        });
  }

  @Test
  @DisplayName("몰 정보 없이 판정할 수 없다")
  void refusesToJudgeWithoutTheMallRow() {
    assertThrows(
        IllegalArgumentException.class,
        () -> JangBoGoManager.qualify(null, () -> STORED, CollectTrigger.SCHEDULED));
  }

  // ---------------------------------------------------------------
  // 도우미
  // ---------------------------------------------------------------

  /** 계정을 연결해 둔 몰 행. 세션 프로필은 쓰지 않는다. */
  private static JSONObject connectedMall() {
    JSONObject mall = new JSONObject();
    mall.put("seq", 1);
    mall.put("account_status", 1);
    mall.put("encrypt_key", "key-base64");
    mall.put("encrypt_iv", "iv-base64");
    mall.put("session_profile_enabled", 0);
    return mall;
  }

  /** 세션 캡처만 마친 몰 행 — 자격증명도 account_status 도 없다. */
  private static JSONObject sessionOnlyMall() {
    JSONObject mall = new JSONObject();
    mall.put("seq", 1);
    mall.put("account_status", 0);
    mall.put("session_profile_enabled", 1);
    mall.put("session_profile_name", "mall");
    mall.put("session_profile_status", JbgMallDataAccessObject.SESSION_PROFILE_STATUS_READY);
    return mall;
  }

  /** 마스터 킬스위치를 잠시 바꿔 실행하고 원래대로 되돌린다. */
  private static void withSessionProfileSwitch(boolean enabled, Executable body) throws Throwable {
    String previous = System.getProperty(SessionProfilePolicy.ENABLED_PROPERTY);
    try {
      if (enabled) {
        System.setProperty(SessionProfilePolicy.ENABLED_PROPERTY, "true");
      } else {
        System.clearProperty(SessionProfilePolicy.ENABLED_PROPERTY);
      }
      body.execute();
    } finally {
      if (previous == null) {
        System.clearProperty(SessionProfilePolicy.ENABLED_PROPERTY);
      } else {
        System.setProperty(SessionProfilePolicy.ENABLED_PROPERTY, previous);
      }
    }
  }
}

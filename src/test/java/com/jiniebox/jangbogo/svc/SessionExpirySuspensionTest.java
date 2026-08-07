package com.jiniebox.jangbogo.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jiniebox.jangbogo.svc.mall.MallRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 세션 만료가 <b>실패가 아니라 건너뜀</b>으로 기록되고, <b>만료된 수집기만</b> 일시중단되는지 검증 (Phase 5-10).
 *
 * <h2>왜 이 한 글자가 중요한가</h2>
 *
 * <p>만료는 <b>수집기가 실제로 돈</b> 회차에 드러난다. 즉 브레이커 표({@code jbg_collect_breaker})가 갱신되는 구간이다. 그래서 이것을
 * {@code FAIL} 로 적으면 {@code MallOrderUpdater.recordBreaker} 가 연속 실패로 세어 브레이커가 열리고, 사람이 세션을 다시 떠도
 * 쿨다운이 끝날 때까지 그 수집기는 돌지 않는다 — <b>사이트 장애도 아닌 일로 수집이 자동 차단된다.</b> 규칙이 깨지는 형태는 상태 문자열 한 개라, 그것을 실제로 읽어
 * 확인한다.
 *
 * <h2>왜 일시중단 키가 몰이 아니라 몰+수집기인가</h2>
 *
 * <p>통합회원(seq=1)에는 수집기가 둘 붙어 있고 둘은 서로 독립적인 데이터원이다. 세션 주입 경로가 대체하는 자리는 그중 하나뿐이라, 그쪽 세션이 죽어도 나머지 하나는
 * 저장된 자격증명으로 멀쩡히 돈다. 그런데 몰 단위로 멈추면 <b>멀쩡한 쪽까지 사람이 다시 로그인할 때까지 함께 선다.</b> 여기서 재는 것이 그 경계다.
 *
 * <h2>실계정 DB 를 건드리지 않는다</h2>
 *
 * <p>기본 기록기는 사용자의 실제 수집 이력에 쓴다. 그래서 {@code MallSchedulerService} 는 기록 지점을 갈아 끼울 수 있게 열어 두었고, 여기서는 그
 * 자리에 받아 적기만 하는 가짜를 꽂는다. DB·브라우저·네트워크를 쓰지 않는다.
 *
 * @author KIUNSEA
 */
class SessionExpirySuspensionTest {

  private static final Path SCHEDULER =
      Path.of("src/main/java/com/jiniebox/jangbogo/svc/MallSchedulerService.java");

  /**
   * 수집기가 둘인 몰. 하나는 세션 주입 경로가 대체하고 하나는 자격증명 경로로 남는다.
   *
   * <p>이름은 {@code MallRegistry} 선언에서 온다. 여기 적힌 것과 저쪽이 갈리면 일시중단이 엉뚱한 자리에 걸리므로, 상수로 두지 않고 <b>레지스트리를
   * 직접 읽어</b> 아래 {@code collectorNamesAreStillTheDeclaredOnes} 가 대조한다.
   */
  private static final String MALL_WITH_TWO_COLLECTORS = "1";

  private static final String SESSION_COLLECTOR = "SsgSession";
  private static final String REPLACED_COLLECTOR = "SSG";
  private static final String CREDENTIAL_COLLECTOR = "Emart";

  /** 세션 주입 수집기를 선언하지 않은 몰. */
  private static final String MALL_WITHOUT_SESSION_COLLECTOR = "2";

  /** 기록된 한 행. */
  private record Row(int seqMall, String status, String code, String reason, String step) {}

  private static List<Row> capture(MallSchedulerService scheduler) {
    List<Row> rows = new ArrayList<>();
    scheduler.useLogWriter(
        (seqMall, mallName, status, code, reason, step, startedAt) ->
            rows.add(new Row(seqMall, status, code, reason, step)));
    return rows;
  }

  @Test
  @DisplayName("세션 만료는 FAIL 이 아니라 SKIPPED 로 기록된다")
  void expiryIsRecordedAsSkippedNotFail() {
    MallSchedulerService scheduler = new MallSchedulerService();
    List<Row> rows = capture(scheduler);

    scheduler.suspendForExpiredSession(
        MALL_WITH_TWO_COLLECTORS, "테스트몰", null, System.currentTimeMillis());

    assertEquals(1, rows.size(), "만료를 한 행으로 남기지 않았다.");
    Row row = rows.get(0);

    assertEquals(
        MallSchedulerService.LOG_STATUS_SKIPPED,
        row.status(),
        "만료를 FAIL 로 적으면 브레이커가 연속 실패로 세어 수집기가 자동 차단된다.");
    assertNotEquals(MallSchedulerService.LOG_STATUS_FAIL, row.status());
    assertEquals(MallCollectOutcome.SESSION_EXPIRED_CODE, row.code());
    assertEquals(MallCollectOutcome.STEP_SESSION_EXPIRY, row.step());
    assertEquals(1, row.seqMall());
  }

  @Test
  @DisplayName("사유가 비어 있어도 행동으로 이어지는 문구가 남는다")
  void aBlankReasonFallsBackToAnActionableOne() {
    // 사유 없는 건너뜀은 화면에서 원인 없는 공백으로 보이고, 읽은 사람이 할 수 있는 일이 없다.
    MallSchedulerService scheduler = new MallSchedulerService();
    List<Row> rows = capture(scheduler);

    scheduler.suspendForExpiredSession(
        MALL_WITH_TWO_COLLECTORS, null, "  ", System.currentTimeMillis());

    assertFalse(rows.get(0).reason().isBlank());
  }

  @Test
  @DisplayName("전달된 사유는 그대로 남는다 — 두 경로가 같은 문구를 쓴다")
  void theGivenReasonIsKept() {
    MallSchedulerService scheduler = new MallSchedulerService();
    List<Row> rows = capture(scheduler);

    String reason = MallCollectOutcome.sessionExpired(null).reason();
    scheduler.suspendForExpiredSession(
        MALL_WITH_TWO_COLLECTORS, "테스트몰", reason, System.currentTimeMillis());

    assertEquals(reason, rows.get(0).reason());
  }

  @Test
  @DisplayName("같은 자리의 만료를 두 번 적지 않는다")
  void suspensionIsIdempotent() {
    // 주기마다 같은 줄이 쌓이면 수집 로그 화면이 무의미해진다 — 사람이 그 표를 보는 이유가
    // 몇 번 막혔는지를 세기 위해서다.
    MallSchedulerService scheduler = new MallSchedulerService();
    List<Row> rows = capture(scheduler);

    assertFalse(
        scheduler.isSuspendedForExpiredSession(MALL_WITH_TWO_COLLECTORS, SESSION_COLLECTOR));

    long now = System.currentTimeMillis();
    scheduler.suspendForExpiredSession(MALL_WITH_TWO_COLLECTORS, "테스트몰", "만료", now);
    scheduler.suspendForExpiredSession(MALL_WITH_TWO_COLLECTORS, "테스트몰", "만료", now);

    assertTrue(scheduler.isSuspendedForExpiredSession(MALL_WITH_TWO_COLLECTORS, SESSION_COLLECTOR));
    assertEquals(1, rows.size(), "같은 자리의 만료를 두 번 적었다.");
  }

  // ---------------------------------------------------------------
  // 키 세분화 — 이 국면의 본체
  // ---------------------------------------------------------------

  @Test
  @DisplayName("한 수집기의 세션이 죽어도 같은 몰의 다른 수집기는 계속 수집된다")
  void oneDeadCollectorDoesNotStopTheOtherOne() {
    // 예전에는 여기서 몰 전체가 멈췄다. 세션 주입 경로가 대체하는 자리는 하나뿐인데, 그쪽이 죽었다는
    // 이유로 저장된 자격증명으로 멀쩡히 도는 나머지 자리까지 사람이 다시 로그인할 때까지 섰다.
    MallSchedulerService scheduler = new MallSchedulerService();
    capture(scheduler);

    scheduler.suspendForExpiredSession(
        MALL_WITH_TWO_COLLECTORS, "테스트몰", "세션이 죽었다", System.currentTimeMillis());

    assertTrue(
        scheduler.isSuspendedForExpiredSession(MALL_WITH_TWO_COLLECTORS, SESSION_COLLECTOR),
        "만료된 세션 수집기가 중단되지 않았다 — 죽은 세션으로 매 회차 계속 두드리게 된다.");
    assertFalse(
        scheduler.isSuspendedForExpiredSession(MALL_WITH_TWO_COLLECTORS, CREDENTIAL_COLLECTOR),
        "다른 수집기까지 중단됐다.");
    assertFalse(
        scheduler.allCollectorsSuspendedForExpiredSession(MALL_WITH_TWO_COLLECTORS),
        "진입 지점이 이번 회차를 통째로 건너뛴다 — 멀쩡한 수집기가 사람 손을 기다리게 된다.");
  }

  @Test
  @DisplayName("돌릴 수 있는 자리가 하나도 없는 회차에서만 진입 지점이 물러난다")
  void theRoundIsSkippedOnlyWhenEveryPositionIsDead() {
    MallSchedulerService scheduler = new MallSchedulerService();
    capture(scheduler);
    long now = System.currentTimeMillis();

    // 대체된 자리(SSG↔SsgSession) 하나만 죽은 상태 — 나머지 자리가 살아 있으므로 회차는 돈다.
    scheduler.suspendCollectors(
        MALL_WITH_TWO_COLLECTORS, List.of(SESSION_COLLECTOR), "테스트몰", "세션이 죽었다", now);
    assertFalse(scheduler.allCollectorsSuspendedForExpiredSession(MALL_WITH_TWO_COLLECTORS));

    // 나머지 자리까지 죽으면 그때는 브라우저를 띄우는 것 자체가 헛일이라 물러난다.
    scheduler.suspendCollectors(
        MALL_WITH_TWO_COLLECTORS, List.of(CREDENTIAL_COLLECTOR), "테스트몰", "세션이 죽었다", now);
    assertTrue(
        scheduler.allCollectorsSuspendedForExpiredSession(MALL_WITH_TWO_COLLECTORS),
        "돌릴 자리가 하나도 없는데 회차를 시작한다.");
  }

  @Test
  @DisplayName("자리 세는 규칙이 수집 계획과 같다 — 대체된 자리는 어느 이름으로 막혀도 막힌 것이다")
  void aPositionIsDeadWhicheverOfItsTwoNamesIsSuspended() {
    // 세션 수집기는 자격증명 수집기 자리를 '대체' 한다. 그래서 그 자리는 두 이름 중 하나만 중단돼도
    // 이번 회차에 못 돈다. 계획(MallOrderUpdater.planCollectors)과 다르게 세면 '돌 수 있는데
    // 물러나는' 회차가 생기고, 그 몰은 아무것도 모으지 않으면서 로그도 남기지 않는다.
    MallSchedulerService scheduler = new MallSchedulerService();
    capture(scheduler);

    scheduler.suspendCollectors(
        MALL_WITH_TWO_COLLECTORS,
        List.of(REPLACED_COLLECTOR, CREDENTIAL_COLLECTOR),
        "테스트몰",
        "만료",
        System.currentTimeMillis());

    assertTrue(scheduler.allCollectorsSuspendedForExpiredSession(MALL_WITH_TWO_COLLECTORS));
  }

  @Test
  @DisplayName("재개는 그 수집기만 푼다")
  void resumeReleasesOnlyThatCollector() {
    MallSchedulerService scheduler = new MallSchedulerService();
    capture(scheduler);
    long now = System.currentTimeMillis();

    scheduler.suspendCollectors(
        MALL_WITH_TWO_COLLECTORS,
        List.of(SESSION_COLLECTOR, CREDENTIAL_COLLECTOR),
        "테스트몰",
        "만료",
        now);

    assertTrue(scheduler.resumeAfterSessionRecovery(MALL_WITH_TWO_COLLECTORS, SESSION_COLLECTOR));

    assertFalse(
        scheduler.isSuspendedForExpiredSession(MALL_WITH_TWO_COLLECTORS, SESSION_COLLECTOR),
        "푼 자리가 여전히 중단 중이다.");
    assertTrue(
        scheduler.isSuspendedForExpiredSession(MALL_WITH_TWO_COLLECTORS, CREDENTIAL_COLLECTOR),
        "한 자리를 풀었는데 다른 자리까지 함께 풀렸다 — 죽은 세션으로 다시 두드리게 된다.");
    assertFalse(
        scheduler.resumeAfterSessionRecovery(MALL_WITH_TWO_COLLECTORS, SESSION_COLLECTOR),
        "중단 중이 아닌데 풀었다고 답했다.");
  }

  @Test
  @DisplayName("사용자가 몰을 골라 요청하면 그 몰의 중단이 전부 풀린다")
  void aUserRequestReleasesEveryCollectorOfThatMall() {
    // 사용자가 화면에서 고르는 것은 몰이고, 그 몰에 세션을 다시 떠 준 것이다. 중단은 세션 주입
    // 수집기에만 걸리므로 이 호출이 자격증명 쪽 설정을 함께 되돌리는 일은 없다.
    MallSchedulerService scheduler = new MallSchedulerService();
    List<Row> rows = capture(scheduler);
    long now = System.currentTimeMillis();

    scheduler.suspendCollectors(
        MALL_WITH_TWO_COLLECTORS,
        List.of(SESSION_COLLECTOR, CREDENTIAL_COLLECTOR),
        "테스트몰",
        "만료",
        now);

    assertTrue(scheduler.resumeAfterSessionRecovery(MALL_WITH_TWO_COLLECTORS));
    assertTrue(scheduler.suspendedCollectors(MALL_WITH_TWO_COLLECTORS).isEmpty());
    assertFalse(
        scheduler.resumeAfterSessionRecovery(MALL_WITH_TWO_COLLECTORS), "중단 중이 아닌데 풀었다고 답했다.");

    // 풀린 뒤 다시 만료되면 그때는 새 행을 적는다. 그 행은 사용자가 직접 누른 결과다.
    scheduler.suspendForExpiredSession(MALL_WITH_TWO_COLLECTORS, "테스트몰", "만료", now);
    assertEquals(2, rows.size());
  }

  @Test
  @DisplayName("다른 몰의 수집은 멈추지 않는다")
  void suspensionIsPerMall() {
    MallSchedulerService scheduler = new MallSchedulerService();
    capture(scheduler);

    scheduler.suspendForExpiredSession(
        MALL_WITH_TWO_COLLECTORS, "테스트몰", "만료", System.currentTimeMillis());

    assertTrue(scheduler.isSuspendedForExpiredSession(MALL_WITH_TWO_COLLECTORS, SESSION_COLLECTOR));
    assertFalse(
        scheduler.isSuspendedForExpiredSession("3", SESSION_COLLECTOR), "한 몰의 만료가 다른 몰까지 멈췄다.");
    assertFalse(scheduler.allCollectorsSuspendedForExpiredSession("3"));
    assertFalse(scheduler.isSuspendedForExpiredSession(null, SESSION_COLLECTOR));
    assertFalse(scheduler.isSuspendedForExpiredSession("", SESSION_COLLECTOR));
    assertFalse(scheduler.isSuspendedForExpiredSession(MALL_WITH_TWO_COLLECTORS, null));
  }

  @Test
  @DisplayName("귀속할 수집기를 못 찾으면 기록만 남기고 아무것도 멈추지 않는다")
  void anUnattributableExpiryStopsNothing() {
    // 추측으로 자리를 고르면 멀쩡한 수집기가 남의 만료로 서는데, 그것이 이 국면이 고치려는 사고다.
    // 안 멈춰서 잃는 것은 다음 회차의 헛수고 한 번뿐이다.
    MallSchedulerService scheduler = new MallSchedulerService();
    List<Row> rows = capture(scheduler);

    scheduler.suspendForExpiredSession(
        MALL_WITHOUT_SESSION_COLLECTOR, "옆몰", "만료", System.currentTimeMillis());

    assertEquals(1, rows.size(), "만료 사실이 어디에도 남지 않았다.");
    assertEquals(MallSchedulerService.LOG_STATUS_SKIPPED, rows.get(0).status());
    assertTrue(scheduler.suspendedCollectors(MALL_WITHOUT_SESSION_COLLECTOR).isEmpty());
    assertFalse(
        scheduler.allCollectorsSuspendedForExpiredSession(MALL_WITHOUT_SESSION_COLLECTOR),
        "귀속할 자리도 모르면서 회차를 통째로 막았다.");
  }

  @Test
  @DisplayName("일시중단이 걸리는 이름은 레지스트리가 선언한 그 이름이다")
  void collectorNamesAreStillTheDeclaredOnes() {
    // 이 이름은 jbg_collect_log.collector·브레이커 키와 같은 값이다. 여기 적은 이름과 레지스트리
    // 선언이 갈리면 일시중단이 아무 자리에도 걸리지 않고, 그 사실은 로그에도 드러나지 않는다.
    MallRegistry mall = MallRegistry.bySeq(MALL_WITH_TWO_COLLECTORS).orElseThrow();

    List<String> credentials =
        mall.collectors().stream().map(MallRegistry.CollectorSpec::name).toList();
    List<String> sessions =
        mall.sessionCollectors().stream().map(MallRegistry.SessionCollectorSpec::name).toList();

    assertTrue(credentials.contains(REPLACED_COLLECTOR), "대체되는 자리 이름이 레지스트리에 없다: " + credentials);
    assertTrue(credentials.contains(CREDENTIAL_COLLECTOR), "자격증명 자리 이름이 레지스트리에 없다: " + credentials);
    assertTrue(sessions.contains(SESSION_COLLECTOR), "세션 수집기 이름이 레지스트리에 없다: " + sessions);
  }

  // ---------------------------------------------------------------
  // 일시중단을 '어떻게' 구현했는가 — 형태로만 드러나는 규칙
  // ---------------------------------------------------------------

  @Test
  @DisplayName("일시중단은 스케줄 취소가 아니라 진입 지점 건너뛰기다")
  void suspensionSkipsAtTheEntryPointInsteadOfCancelling() throws Exception {
    // cancel 로 끄면 등록 정보가 사라져 isScheduled 가 false 가 되고, 사용자가 세션을 다시 떠도
    // 화면은 '주기 수집 꺼짐' 으로 보인 채 다음 기동까지 복구되지 않는다. 되살릴 주체가 없다.
    String code = sourceWithoutComments(SCHEDULER);

    int entry = code.indexOf("private void runCollectForMall(");
    assertTrue(entry >= 0, "수집 진입 지점을 찾지 못했다. 이 테스트를 손봐야 한다.");

    int guard = code.indexOf("allCollectorsSuspendedForExpiredSession(seq)", entry);
    int firstDbCall = code.indexOf("new JbgMallDataAccessObject()", entry);
    assertTrue(guard > entry, "진입 지점에서 일시중단을 보지 않는다.");
    assertTrue(guard < firstDbCall, "일시중단 확인이 DB 조회보다 뒤에 있다 — 막을 회차에서 왕복이 생긴다.");

    int suspendStart = code.indexOf("public void suspendForExpiredSession(");
    int suspendEnd = code.indexOf("public boolean isSuspendedForExpiredSession(", suspendStart);
    assertTrue(suspendStart >= 0 && suspendEnd > suspendStart);

    String suspendBody = code.substring(suspendStart, suspendEnd);
    assertFalse(suspendBody.contains("cancel"), "일시중단을 스케줄 취소로 구현했다 — 되살릴 주체가 없어진다.");
  }

  @Test
  @DisplayName("일시중단이 auto_collect 설정을 건드리지 않는다")
  void suspensionNeverTouchesTheAutoCollectColumn() throws Exception {
    // saveAutoCollectFlags 는 전체를 0 으로 초기화한 뒤 선택분만 되돌린다. 한 번만 불려도
    // 사용자가 켜 둔 다른 몰의 설정까지 통째로 날아간다. 일시중단은 런타임 사실이지 사용자 설정이 아니다.
    String code = sourceWithoutComments(SCHEDULER);

    // 대조군. 아래 둘은 "그 낱말이 없다" 형태라, 소스를 못 읽었거나 주석 제거가 실행되는 코드까지
    // 지워 code 가 비면 <b>그대로 초록</b>이 된다. 먼저 '정말 읽었는가' 를 묻는다.
    assertTrue(
        code.contains("class MallSchedulerService"),
        "감시 대상 소스를 읽지 못했거나 주석 제거가 실행되는 코드까지 지웠다 —"
            + " 빈 문자열에는 어떤 컬럼 이름도 없으므로 아래 두 단언은 무엇이 있어도 통과한다.");

    assertFalse(code.contains("saveAutoCollectFlags"), "스케줄러가 자동수집 설정 저장을 부른다.");
    assertFalse(code.contains("auto_collect"), "스케줄러가 auto_collect 컬럼을 직접 만진다.");
  }

  @Test
  @DisplayName("대조군: 주석 제거가 주석만 지우고 실행되는 코드는 남긴다")
  void theCommentStripperKeepsExecutableCode(@TempDir Path tempDir) throws Exception {
    // suspensionNeverTouchesTheAutoCollectColumn 의 판별부는 이 주석 제거 하나뿐이고, 그
    // 단언은 전부 "그 낱말이 없다" 형태다. 그래서 제거가 빈 문자열을 돌려주게 바뀌면 그대로
    // 초록이 된다. (suspensionSkipsAtTheEntryPointInsteadOfCancelling 은 indexOf 결과를
    // 먼저 확인하므로 빈 문자열에서는 빨개진다 — 그쪽은 이미 절반쯤 대조군이 있는 셈이다.)
    //
    // 반대 방향도 실재하는 위험이다. MallSchedulerService 의 javadoc 은 auto_collect 컬럼과
    // saveAutoCollectFlags 를 <b>설명으로</b> 적어 두고 있어서(왜 건드리면 안 되는지가 거기
    // 있다), 제거가 사라지면 이 감시가 늘 빨개진다. 그때 사람은 설명을 지우거나 검사를 끈다.
    //
    // 이 세션에서 실제로 두 번 겪은 형태다. (1) 세션 만료 감지는 단위 테스트 25건이 초록인 채
    // 프로덕션 호출자가 0건이었고, (2) 배포 산출물 가드는 판별식을 무력화해도 5건이 전부
    // 통과했다. 둘 다 '초록인데 아무 일도 안 하는' 상태였고, 가드가 없는 것보다 나쁘다 —
    // 없으면 사람이 알기라도 하는데 이쪽은 아무도 모른 채 지나간다.
    //
    // 그래서 저장소 상태가 아니라 판별부에 직접 입력을 넣는다. "지금 위반이 0건이다" 에
    // 기대는 대조군은 판별부가 죽어도 같은 결론을 내므로 무의미하다.
    Path sample = tempDir.resolve("Sample.java");
    Files.writeString(
        sample,
        "/** auto_collect 컬럼은 건드리지 않는다고 적어만 둔 javadoc. */\n"
            + "class Sample {\n"
            + "  // 예전엔 여기서 saveAutoCollectFlags 를 불렀다\n"
            + "  void run() {\n"
            + "    int visited = 1;\n"
            + "  }\n"
            + "}\n",
        StandardCharsets.UTF_8);

    String stripped = sourceWithoutComments(sample);

    assertFalse(
        stripped.contains("auto_collect"), "javadoc 에 적은 설명이 코드로 세어졌다 — 설명을 적을수록 감시가 빨개진다.");
    assertFalse(stripped.contains("saveAutoCollectFlags"), "주석으로 남겨 둔 옛 호출이 세어졌다 — 같은 이유로 오탐이 된다.");
    assertTrue(
        stripped.contains("int visited = 1;"),
        "주석 제거가 실행되는 코드까지 지웠다. 빈 문자열에는 어떤 낱말도 없으므로 위 형태 감시가 무엇이 있어도 초록이 된다.");
  }

  /** 주석을 걷어낸 소스. 주석에 적힌 설명이 형태 감시에 걸리지 않게 한다. */
  private static String sourceWithoutComments(Path path) throws Exception {
    return Files.readString(path, StandardCharsets.UTF_8)
        .replaceAll("(?s)/\\*.*?\\*/", "")
        .replaceAll("(?m)^\\s*//.*$", "");
  }
}

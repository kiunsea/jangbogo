package com.jiniebox.jangbogo.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 세션 만료가 <b>실패가 아니라 건너뜀</b>으로 기록되고, 그 몰이 일시중단되는지 검증 (Phase 5-10).
 *
 * <h2>왜 이 한 글자가 중요한가</h2>
 *
 * <p>만료는 <b>수집기가 실제로 돈</b> 회차에 드러난다. 즉 브레이커 표({@code jbg_collect_breaker})가 갱신되는 구간이다. 그래서 이것을
 * {@code FAIL} 로 적으면 {@code MallOrderUpdater.recordBreaker} 가 연속 실패로 세어 브레이커가 열리고, 사람이 세션을 다시 떠도
 * 쿨다운이 끝날 때까지 그 수집기는 돌지 않는다 — <b>사이트 장애도 아닌 일로 수집이 자동 차단된다.</b> 규칙이 깨지는 형태는 상태 문자열 한 개라, 그것을 실제로 읽어
 * 확인한다.
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

    scheduler.suspendForExpiredSession("1", "테스트몰", null, System.currentTimeMillis());

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

    scheduler.suspendForExpiredSession("2", null, "  ", System.currentTimeMillis());

    assertFalse(rows.get(0).reason().isBlank());
  }

  @Test
  @DisplayName("전달된 사유는 그대로 남는다 — 두 경로가 같은 문구를 쓴다")
  void theGivenReasonIsKept() {
    MallSchedulerService scheduler = new MallSchedulerService();
    List<Row> rows = capture(scheduler);

    String reason = MallCollectOutcome.sessionExpired(null).reason();
    scheduler.suspendForExpiredSession("1", "테스트몰", reason, System.currentTimeMillis());

    assertEquals(reason, rows.get(0).reason());
  }

  @Test
  @DisplayName("중단된 몰은 진입 지점에서 건너뛰고, 같은 사실을 두 번 적지 않는다")
  void suspensionIsIdempotent() {
    // 주기마다 같은 줄이 쌓이면 수집 로그 화면이 무의미해진다 — 사람이 그 표를 보는 이유가
    // 몇 번 막혔는지를 세기 위해서다.
    MallSchedulerService scheduler = new MallSchedulerService();
    List<Row> rows = capture(scheduler);

    assertFalse(scheduler.isSuspendedForExpiredSession("1"));

    scheduler.suspendForExpiredSession("1", "테스트몰", "만료", System.currentTimeMillis());
    scheduler.suspendForExpiredSession("1", "테스트몰", "만료", System.currentTimeMillis());

    assertTrue(scheduler.isSuspendedForExpiredSession("1"));
    assertEquals(1, rows.size(), "같은 몰의 만료를 두 번 적었다.");
  }

  @Test
  @DisplayName("다른 몰의 수집은 멈추지 않는다")
  void suspensionIsPerMall() {
    MallSchedulerService scheduler = new MallSchedulerService();
    capture(scheduler);

    scheduler.suspendForExpiredSession("1", "테스트몰", "만료", System.currentTimeMillis());

    assertTrue(scheduler.isSuspendedForExpiredSession("1"));
    assertFalse(scheduler.isSuspendedForExpiredSession("2"), "한 몰의 만료가 다른 몰까지 멈췄다.");
    assertFalse(scheduler.isSuspendedForExpiredSession(null));
    assertFalse(scheduler.isSuspendedForExpiredSession(""));
  }

  @Test
  @DisplayName("사람이 다시 로그인하면 풀린다 — 되살릴 통로가 없으면 다음 기동까지 멈춘 채로 남는다")
  void suspensionCanBeReleased() {
    MallSchedulerService scheduler = new MallSchedulerService();
    List<Row> rows = capture(scheduler);

    scheduler.suspendForExpiredSession("1", "테스트몰", "만료", System.currentTimeMillis());

    assertTrue(scheduler.resumeAfterSessionRecovery("1"));
    assertFalse(scheduler.isSuspendedForExpiredSession("1"));
    assertFalse(scheduler.resumeAfterSessionRecovery("1"), "중단 중이 아닌데 풀었다고 답했다.");

    // 풀린 뒤 다시 만료되면 그때는 새 행을 적는다. 그 행은 사용자가 직접 누른 결과다.
    scheduler.suspendForExpiredSession("1", "테스트몰", "만료", System.currentTimeMillis());
    assertEquals(2, rows.size());
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

    int guard = code.indexOf("isSuspendedForExpiredSession(seq)", entry);
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

    assertFalse(code.contains("saveAutoCollectFlags"), "스케줄러가 자동수집 설정 저장을 부른다.");
    assertFalse(code.contains("auto_collect"), "스케줄러가 auto_collect 컬럼을 직접 만진다.");
  }

  /** 주석을 걷어낸 소스. 주석에 적힌 설명이 형태 감시에 걸리지 않게 한다. */
  private static String sourceWithoutComments(Path path) throws Exception {
    return Files.readString(path, StandardCharsets.UTF_8)
        .replaceAll("(?s)/\\*.*?\\*/", "")
        .replaceAll("(?m)^\\s*//.*$", "");
  }
}

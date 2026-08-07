package com.jiniebox.jangbogo.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jiniebox.jangbogo.svc.mall.SessionCollector;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.json.simple.JSONArray;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 세션 만료 판정이 <b>실제 수집 경로에 배선돼 있는지</b> 감시한다 (Phase 5-10 배선).
 *
 * <h2>왜 이 테스트가 따로 있어야 하나</h2>
 *
 * <p>만료 기능은 부품이 전부 만들어지고 <b>테스트 25건이 초록인 채로</b> 한 국면을 통째로 지나갔다. 그런데 {@code
 * SessionExpiryDetector.observe} 의 프로덕션 호출자가 0건이었고 {@code MallCollectOutcome.sessionExpired(...)} 를
 * 만드는 코드도 없었다. 그래서
 *
 * <ul>
 *   <li>{@code outcome.sessionExpired()} 는 언제나 false 였고
 *   <li>{@code MallSchedulerService}·{@code AdminController} 의 만료 분기는 <b>도달 불가 코드</b>였으며
 *   <li>일시중단·재개가 전부 무동작이었다.
 * </ul>
 *
 * <p>단위 테스트는 부품을 <b>직접 불러서</b> 검사하므로 이 상태를 원리상 잡지 못한다. 부품이 옳다는 것과 그 부품이 불린다는 것은 다른 사실이고, 사용자에게 닿는
 * 것은 뒤쪽뿐이다. 그래서 여기서는 <b>소스에서 호출자 수를 직접 센다</b> ({@code SecurityHardeningTest} 가 같은 이유로 같은 방식을 쓴다).
 *
 * <p>브라우저·네트워크·DB 를 쓰지 않는다.
 *
 * @author KIUNSEA
 */
class SessionExpiryWiringTest {

  private static final Path MAIN = Path.of("src/main/java");

  // 경로는 문자열로 비교한다. Path.equals 는 구분자 표기에 걸려 OS 마다 다르게 답할 수 있는데,
  // 그 실패는 "배선이 없다" 와 똑같은 모양으로 나타나 원인을 엉뚱한 곳에서 찾게 만든다.
  private static final String DETECTOR =
      "com/jiniebox/jangbogo/svc/util/SessionExpiryDetector.java";
  private static final String OUTCOME = "com/jiniebox/jangbogo/svc/MallCollectOutcome.java";
  private static final String RUNNER = "com/jiniebox/jangbogo/svc/MallOrderUpdaterRunner.java";
  private static final String COLLECTOR = "com/jiniebox/jangbogo/svc/mall/SsgSessionCollector.java";

  /** 배선이 끊겼을 때 사람이 읽을 문장. 어느 단언에서 터지든 같은 취지를 읽게 한다. */
  private static final String WHY =
      "\n테스트가 초록이어도 배선이 없으면 사용자에겐 아무 일도 일어나지 않는다."
          + "\n부품이 옳다는 것과 그 부품이 불린다는 것은 다른 사실이고, 사용자에게 닿는 것은 뒤쪽뿐이다.";

  // ---------------------------------------------------------------
  // 배선 감시 — 호출자 수를 소스에서 직접 센다
  // ---------------------------------------------------------------

  @Test
  @DisplayName("만료 관측(observe)의 프로덕션 호출자가 1건 이상 있다")
  void theExpiryObserverIsActuallyCalledInProduction() throws Exception {
    List<String> callers = productionFilesContaining("SessionExpiryDetector.observe(", DETECTOR);

    assertFalse(
        callers.isEmpty(),
        "SessionExpiryDetector.observe 를 부르는 프로덕션 코드가 0건이다 — 만료는 관측조차 되지 않는다." + WHY);
    assertTrue(
        callers.contains(COLLECTOR),
        "만료 관측이 세션 주입 수집기에 붙어 있지 않다. 관측은 회원 페이지 이동 직후·파싱 직전에 일어나야 한다: " + callers);
  }

  @Test
  @DisplayName("만료 결과(MallCollectOutcome.sessionExpired)를 만드는 프로덕션 호출자가 1건 이상 있다")
  void theExpiryOutcomeIsActuallyProducedInProduction() throws Exception {
    List<String> producers =
        productionFilesContaining("MallCollectOutcome.sessionExpired(", OUTCOME);

    assertFalse(
        producers.isEmpty(),
        "MallCollectOutcome.sessionExpired 를 만드는 프로덕션 코드가 0건이다 —"
            + " outcome.sessionExpired() 는 영원히 false 이고, 스케줄러·즉시수집의 만료 분기는 도달 불가 코드다."
            + WHY);
    assertTrue(
        producers.contains(RUNNER),
        "만료 승격이 수집 실행기에 없다. 만료는 수집기별 SKIPPED 에서 끝나면 안 된다: " + producers);
  }

  @Test
  @DisplayName("승격된 만료를 실제로 받아 가는 호출자가 있다 — 마지막 한 홉")
  void thePromotedExpiryIsConsumedByTheCollectEntryPoint() throws Exception {
    // 이 단언만 다른 파일의 몫이다. MallOrderUpdaterRunner 가 만료를 승격해도, 수집의 유일한 통로인
    // JangBoGoManager.collect 가 그것을 읽지 않으면 결과는 언제나 success 로 나가고 만료는 사라진다.
    //
    // 필요한 변경은 한 줄이다 — collect() 끝의
    //     return MallCollectOutcome.success(newOrderSeqs);
    // 를
    //     return runner.collectOutcome();
    // 로 바꾸면 된다 (newOrderSeqs 를 읽는 로그 줄은 그대로 둔다).
    List<String> consumers = productionFilesContaining(".collectOutcome()", RUNNER);

    assertFalse(
        consumers.isEmpty(),
        "MallOrderUpdaterRunner.collectOutcome() 을 읽는 프로덕션 코드가 0건이다."
            + "\nJangBoGoManager.collect 가 아직 MallCollectOutcome.success(...) 를 무조건 돌려주고 있다."
            + "\n→ collect() 의 마지막 return 을 runner.collectOutcome() 으로 바꿔라."
            + WHY);
  }

  // ---------------------------------------------------------------
  // 승격 규칙 — 언제 만료를 실행 결과로 올리는가
  // ---------------------------------------------------------------

  @Test
  @DisplayName("만료 회차는 만료 결과로 승격된다")
  void anExpiredRoundIsPromoted() {
    List<MallOrderUpdater.CollectOutcome> outcomes =
        List.of(skipped("SsgSession", SessionCollector.SkipCause.SESSION_EXPIRED, "세션이 죽었다"));

    assertEquals(
        "세션이 죽었다", MallOrderUpdaterRunner.promotableExpiryReason(outcomes, new JSONArray()));
  }

  @Test
  @DisplayName("만료가 아닌 건너뜀은 승격되지 않는다 — 종류로 가른다")
  void otherSkipsAreNotPromoted() {
    // 세션이 아직 없는 것은 옵트인만 켠 몰의 정상 과도 상태다. 그것으로 몰을 일시중단하면
    // 같은 몰의 자격증명 수집기까지 함께 멈춘다.
    List<MallOrderUpdater.CollectOutcome> outcomes =
        List.of(
            skipped("SsgSession", SessionCollector.SkipCause.NO_SESSION, "세션이 없다"),
            skipped("Emart", null, MallOrderUpdater.REASON_NO_CREDENTIALS));

    assertNull(MallOrderUpdaterRunner.promotableExpiryReason(outcomes, new JSONArray()));
  }

  @Test
  @DisplayName("다른 수집기가 주문을 가져온 회차는 만료로 승격하지 않는다")
  void aRoundThatCollectedSomethingIsNotPromoted() {
    // 승격하면 만료 결과의 신규 주문 목록이 비어 있어 스케줄러가 내보내기 앞에서 물러난다.
    // 방금 저장한 주문은 다음 회차에 중복으로 걸러져 영영 나가지 않는다.
    JSONArray collected = new JSONArray();
    collected.add("주문 하나");

    List<MallOrderUpdater.CollectOutcome> outcomes =
        List.of(skipped("SsgSession", SessionCollector.SkipCause.SESSION_EXPIRED, "세션이 죽었다"));

    assertNull(MallOrderUpdaterRunner.promotableExpiryReason(outcomes, collected));
  }

  @Test
  @DisplayName("만료가 없으면 승격할 것이 없다")
  void nothingToPromoteWithoutExpiry() {
    assertNull(MallOrderUpdaterRunner.promotableExpiryReason(null, null));
    assertNull(MallOrderUpdaterRunner.promotableExpiryReason(List.of(), new JSONArray()));
  }

  @Test
  @DisplayName("만료가 없는 회차의 실행 결과는 예전 그대로 성공이다")
  void anOrdinaryRoundStillReportsSuccess() {
    // 만료 배선이 옵트인과 무관한 몰의 동작을 바꾸면 안 된다.
    MallOrderUpdaterRunner runner = new MallOrderUpdaterRunner("2", "아이디", "비밀번호");
    MallCollectOutcome outcome = runner.collectOutcome();

    assertTrue(outcome.collected());
    assertFalse(outcome.sessionExpired());
    assertTrue(outcome.newOrderSeqs().isEmpty());
  }

  // ---------------------------------------------------------------

  /** 건너뜀 결과 하나를 만든다. */
  private static MallOrderUpdater.CollectOutcome skipped(
      String collector, SessionCollector.SkipCause cause, String reason) {
    return new MallOrderUpdater.CollectOutcome(
        collector, MallOrderUpdater.CollectOutcome.SKIPPED, null, reason, cause);
  }

  /**
   * {@code src/main} 에서 이 조각을 담고 있는 파일들을 찾는다.
   *
   * <p><b>주석을 걷어내고 본다.</b> javadoc 이 "여기를 부른다" 라고 적어 둔 것과 실제로 부르는 것은 다른 사실인데, 주석을 함께 세면 설명 한 줄이
   * 배선으로 오인되어 이 감시가 통째로 무의미해진다.
   *
   * @param needle 찾을 호출 형태
   * @param excluded 선언 자체가 들어 있는 파일 ({@code src/main/java} 기준 상대경로). 자기 자신을 호출자로 세지 않는다
   * @return 그 조각을 담은 파일 목록 ({@code src/main/java} 기준 상대경로)
   */
  private static List<String> productionFilesContaining(String needle, String excluded)
      throws IOException {

    List<String> found = new ArrayList<>();
    try (Stream<Path> files = Files.walk(MAIN)) {
      for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
        String relative = MAIN.relativize(file).toString().replace('\\', '/');
        if (relative.equals(excluded)) {
          continue;
        }
        if (sourceWithoutComments(file).contains(needle)) {
          found.add(relative);
        }
      }
    }
    return found;
  }

  /** 주석을 걷어낸 소스. */
  private static String sourceWithoutComments(Path path) throws IOException {
    return Files.readString(path, StandardCharsets.UTF_8)
        .replaceAll("(?s)/\\*.*?\\*/", "")
        .replaceAll("(?m)^\\s*//.*$", "");
  }
}

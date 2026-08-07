package com.jiniebox.jangbogo.ctrl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.jiniebox.jangbogo.dao.JbgMallDataAccessObject;
import com.jiniebox.jangbogo.dao.SchemaTestSupport;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 몰별 세션 옵트인 배선 검증 — 엔드포인트와 그것을 부르는 화면 (Phase 5-21).
 *
 * <h2>왜 전용 엔드포인트를 따로 재는가</h2>
 *
 * <p>기존 {@code /malls/auto-collect/flags} 는 <b>"seqs 에 없는 몰 = 해제"</b> 시맨틱이다. 세션 옵트인을 거기 실으면 화면 일부만
 * 담아 보낸 요청 하나가 다른 몰의 옵트인을 조용히 끄고, 그 사고는 다음 회차에 <b>그 몰의 수집이 통째로 사라지는 형태</b>로만 드러난다 — 화면에는 아무 변화가 없다.
 * 그래서 "다른 몰을 안 건드린다" 를 DAO 뿐 아니라 요청 단위로도 못 박는다.
 *
 * <h2>왜 화면까지 보나</h2>
 *
 * <p>백엔드가 아무리 분리돼 있어도 체크박스가 {@code mall-check} class 를 재사용하면 그 순간 <b>자동수집 저장 로직이 함께 발화</b>한다. 그 결합은
 * 자바 쪽 테스트로는 절대 잡히지 않고, 형태로만 드러난다. 그래서 템플릿 형태를 함께 본다 — {@code ImmediateCollectConnectionTest} 와 같은
 * 방식이다.
 *
 * <p>브라우저·네트워크를 쓰지 않는다. DB 는 {@code @TempDir} 의 새 파일이라 기준선 DB 에 닿지 않는다.
 *
 * @author KIUNSEA
 */
class SessionOptInWiringTest {

  private static final String DB_URL_PROPERTY = "jangbogo.localdb.url";

  private static final Path INDEX_HTML = Path.of("src/main/resources/templates/index.html");

  private String previousUrl;

  @BeforeEach
  void isolateDatabase(@TempDir Path tempDir) throws Exception {
    previousUrl = System.getProperty(DB_URL_PROPERTY);
    String dbUrl =
        "jdbc:sqlite:" + tempDir.resolve("optin-endpoint-test.db").toString().replace('\\', '/');
    System.setProperty(DB_URL_PROPERTY, dbUrl);
    SchemaTestSupport.remigrate();

    try (Connection c = DriverManager.getConnection(dbUrl);
        Statement s = c.createStatement()) {
      s.executeUpdate(
          "INSERT INTO jbg_mall (seq, id, name, account_status, session_profile_enabled)"
              + " VALUES (1, 'ssg', '테스트몰', 1, 0)");
      s.executeUpdate(
          "INSERT INTO jbg_mall (seq, id, name, account_status, session_profile_enabled)"
              + " VALUES (2, 'oasis', '옆몰', 1, 0)");
    }
  }

  @AfterEach
  void restoreDatabaseUrl() {
    SchemaTestSupport.reset();
    if (previousUrl == null) {
      System.clearProperty(DB_URL_PROPERTY);
    } else {
      System.setProperty(DB_URL_PROPERTY, previousUrl);
    }
  }

  private static JsonNode toggle(String seq, Boolean enabled) {
    AdminController.SessionOptInRequest req = new AdminController.SessionOptInRequest();
    req.setSeq(seq);
    req.setEnabled(enabled);
    return new AdminController().setSessionProfileOptIn(req);
  }

  private static int optIn(String seq) throws Exception {
    JSONObject mall = new JbgMallDataAccessObject().getMall(seq);
    assertNotNull(mall, "몰을 찾지 못했다: seq=" + seq);
    return ((Number) mall.get("session_profile_enabled")).intValue();
  }

  // ---------------------------------------------------------------
  // 엔드포인트
  // ---------------------------------------------------------------

  @Test
  @DisplayName("옵트인 요청이 지정한 몰만 켠다")
  void togglingOnAffectsOnlyTheRequestedMall() throws Exception {
    JsonNode res = toggle("1", true);

    assertTrue(res.get("success").asBoolean(), res.toString());
    assertEquals(1, optIn("1"));
    assertEquals(0, optIn("2"), "요청에 없던 몰이 함께 켜졌다.");
  }

  @Test
  @DisplayName("한 몰을 끄는 요청이 다른 몰의 옵트인을 끄지 않는다")
  void togglingOffDoesNotClearTheOtherMalls() throws Exception {
    toggle("1", true);
    toggle("2", true);

    // 여기가 /malls/auto-collect/flags 에 얹었을 때 실제로 터지는 자리다 — 그 API 는 요청에 없는
    // 몰을 전부 해제하므로, 1번만 담긴 이 요청 하나로 2번의 옵트인이 함께 사라진다.
    JsonNode res = toggle("1", false);

    assertTrue(res.get("success").asBoolean(), res.toString());
    assertEquals(0, optIn("1"));
    assertEquals(1, optIn("2"), "요청에 없던 몰의 옵트인이 조용히 꺼졌다.");
  }

  @Test
  @DisplayName("enabled 를 빠뜨린 요청은 거절한다 — 조용히 끄지 않는다")
  void missingEnabledIsRejectedInsteadOfTurningOff() throws Exception {
    toggle("1", true);

    JsonNode res = toggle("1", null);

    assertFalse(res.get("success").asBoolean());
    assertEquals(1, optIn("1"), "빠뜨린 값을 false 로 읽어 옵트인을 껐다.");
  }

  @Test
  @DisplayName("몰을 지정하지 않은 요청은 거절한다")
  void missingSeqIsRejected() {
    assertFalse(toggle(null, true).get("success").asBoolean());
    assertFalse(toggle("   ", true).get("success").asBoolean());
  }

  @Test
  @DisplayName("없는 몰은 성공이라 말하지 않는다")
  void unknownMallIsNotReportedAsSaved() {
    JsonNode res = toggle("99", true);

    assertFalse(res.get("success").asBoolean(), "아무 행도 안 고쳤는데 저장됐다고 답한다.");
  }

  @Test
  @DisplayName("숫자가 아닌 seq 는 예외가 아니라 실패 응답으로 돌아온다")
  void nonNumericSeqComesBackAsAFailedResponse() throws Exception {
    JsonNode res = toggle("1 OR 1=1", true);

    assertFalse(res.get("success").asBoolean());
    // 숫자만 남기고 깎는 필터가 되살아나면 "1 OR 1=1" 이 "111" 이나 "1" 로 바뀐다 —
    // SQL 은 안 깨지는데 대상이 조용히 바뀐다.
    assertEquals(0, optIn("1"), "필터가 값을 깎아 엉뚱한 몰을 켰다.");
  }

  // ---------------------------------------------------------------
  // 화면 배선
  // ---------------------------------------------------------------

  private static String indexHtml() throws Exception {
    return Files.readString(INDEX_HTML, StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("세션 옵트인 체크박스는 자동수집 체크박스와 class 를 공유하지 않는다")
  void theOptInCheckboxDoesNotReuseTheAutoCollectClass() throws Exception {
    String html = indexHtml();

    List<String> shared = new ArrayList<>();
    Matcher inputs = Pattern.compile("<input[^>]*session-opt-in[^>]*>").matcher(html);
    int count = 0;
    while (inputs.find()) {
      count++;
      if (inputs.group().contains("mall-check")) {
        shared.add(inputs.group().replaceAll("\\s+", " ").trim());
      }
    }

    assertEquals(3, count, "화면의 몰 수와 세션 옵트인 체크박스 수가 다르다.");
    assertTrue(shared.isEmpty(), "세션 옵트인이 mall-check 를 재사용한다 — 체크 한 번에 자동수집 저장까지 발화한다: " + shared);
  }

  @Test
  @DisplayName("옵트인 저장은 전용 엔드포인트로 가고 자동수집 저장 API 를 타지 않는다")
  void theOptInSaveUsesItsOwnEndpoint() throws Exception {
    String html = indexHtml();

    int start = html.indexOf("function saveSessionOptIn(");
    assertTrue(start > 0, "옵트인 저장 함수를 찾지 못했다. 이 테스트를 손봐야 한다.");
    int end = html.indexOf("function refreshSessionStates(", start);
    assertTrue(end > start, "옵트인 저장 함수의 끝을 찾지 못했다.");
    String body = html.substring(start, end);

    assertTrue(body.contains("/malls/session-profile/opt-in"), "옵트인 저장이 전용 엔드포인트를 쓰지 않는다.");
    assertFalse(
        body.contains("/malls/auto-collect/flags"),
        "옵트인 저장이 자동수집 저장 API 를 탄다 — 그 API 는 요청에 없는 몰을 해제한다.");
  }

  @Test
  @DisplayName("세 몰 모두 상태 배지와 '옵트인 ON 인데 세션 없음' 경고 자리를 갖는다")
  void everyMallHasABadgeAndAWarningSlot() throws Exception {
    String html = indexHtml();

    // class 와 data-seq 가 같은 요소 안에 있어야 한다. 둘을 따로 contains 로 보면 한쪽이
    // 다른 몰의 것이어도 통과해 버려서, 배너가 한 몰에만 붙어 있는 상태를 못 잡는다.
    for (String seq : new String[] {"1", "2", "3"}) {
      assertTrue(
          Pattern.compile("<div[^>]*session-warning[^>]*data-seq=\"" + seq + "\"", Pattern.DOTALL)
              .matcher(html)
              .find(),
          "seq=" + seq + " 에 경고 배너 자리가 없다 — 스위치만 켠 상태가 무증상이 된다.");
      assertTrue(
          Pattern.compile("<span[^>]*session-badge[^>]*data-seq=\"" + seq + "\"")
              .matcher(html)
              .find(),
          "seq=" + seq + " 에 세션 상태 배지가 없다 — 캡처 여부를 화면이 말하지 않는다.");
    }
  }

  @Test
  @DisplayName("캡처 안내가 '로그인 상태 유지' 를 굵게 요구한다")
  void theCaptureNoticeDemandsKeepMeSignedIn() throws Exception {
    String html = indexHtml();

    // 체크하지 않고 뜬 세션은 브라우저를 닫는 순간 사라진다 — 캡처는 '성공' 하고 수집만 조용히 실패한다.
    assertTrue(html.contains("<strong>'로그인 상태 유지'</strong>"), "모달 안내에 '로그인 상태 유지' 강조가 없다.");
  }
}

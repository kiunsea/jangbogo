package com.jiniebox.jangbogo.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 암호화 관문이 <b>실제 전송 경로에 물려 있는지</b>를 소스에서 직접 센다.
 *
 * <h2>왜 이 가드가 필요한가</h2>
 *
 * <p>관문 클래스와 그 단위테스트만으로는 아무 것도 보증되지 않는다. 세 경로 중 하나라도 예전처럼 직접 암호화하고 실패하면 평문으로 내려보내면, 관문 테스트는 전부 초록인
 * 채로 평문이 계속 나간다. 이 저장소는 "테스트는 초록인데 프로덕션 호출자가 0건" 을 여러 번 겪었다.
 *
 * <p>그래서 여기서는 실행이 아니라 <b>소스 형태</b>를 본다. 세 자리는 이것이다.
 *
 * <ul>
 *   <li>{@code AdminController.autoCollectSelected} — 자동 수집 경로
 *   <li>{@code AdminController.exportOrders} — 수동 내보내기 경로
 *   <li>{@code MallSchedulerService.processFtpUpload} — 스케줄 수집 경로
 * </ul>
 *
 * <p>브라우저·네트워크·DB 를 쓰지 않는다.
 */
class FtpTransferRefusalWiringTest {

  private static final Path MAIN = Path.of("src/main/java");

  private static final String GATE = "com/jiniebox/jangbogo/svc/util/FtpEncryptionGate.java";
  private static final String RSA = "com/jiniebox/jangbogo/util/security/RsaFileEncryption.java";
  private static final String CONTROLLER = "com/jiniebox/jangbogo/ctrl/AdminController.java";
  private static final String SCHEDULER = "com/jiniebox/jangbogo/svc/MallSchedulerService.java";

  private static final String AUTO_COLLECT = "public JsonNode autoCollectSelected(";
  private static final String EXPORT_ORDERS = "public JsonNode exportOrders(";
  private static final String PROCESS_FTP_UPLOAD = "private void processFtpUpload(";

  @Test
  @DisplayName("암호화는 관문에서만 한다 — 다른 자리에서 직접 하면 그 자리에 강등 분기가 다시 생긴다")
  void onlyTheGateEncrypts() throws IOException {
    List<String> callers = productionFilesContaining("RsaFileEncryption", List.of(GATE, RSA));

    assertEquals(
        List.of(),
        callers,
        "관문 밖에서 파일 암호화를 직접 부른다 — 그 자리는 실패했을 때 무엇을 할지 스스로 정하게 되고,"
            + " 예전 세 자리가 모두 '평문 업로드 진행' 을 골랐다.");
  }

  @Test
  @DisplayName("FTP 업로드를 부르는 파일은 관문을 물린 두 곳뿐이다")
  void onlyTheGuardedFilesUpload() throws IOException {
    List<String> callers = productionFilesContaining("FtpUploadUtil.uploadFile(", List.of());

    assertEquals(
        List.of(CONTROLLER, SCHEDULER).stream().sorted().toList(),
        callers.stream().sorted().toList(),
        "관문을 거치지 않는 새 업로드 경로가 생겼다 — 그 경로는 이 PR 이 고친 규칙을 모른다.");
  }

  @Test
  @DisplayName("세 경로 모두 관문의 거절을 실제로 확인한다")
  void allThreePathsHonourTheRefusal() throws IOException {
    for (String[] site :
        new String[][] {
          {CONTROLLER, AUTO_COLLECT, "자동 수집"},
          {CONTROLLER, EXPORT_ORDERS, "수동 내보내기"},
          {SCHEDULER, PROCESS_FTP_UPLOAD, "스케줄 수집"}
        }) {
      String body = methodBody(sourceWithoutComments(MAIN.resolve(site[0])), site[1]);

      assertFalse(body.isEmpty(), site[2] + " 경로의 메서드를 찾지 못했다 — 이름이 바뀌었으면 이 가드도 따라와야 한다.");
      assertTrue(
          body.contains("gate.prepare("),
          site[2] + " 경로가 관문을 거치지 않는다 — 무엇이 회선에 실리는지 그 자리가 혼자 정하고 있다.");
      assertTrue(
          body.contains(".isRefused()"),
          site[2] + " 경로가 관문의 거절을 확인하지 않는다 — 관문을 부르기만 하고 결과를 무시하면 아무 것도 안 바뀐다.");
    }
  }

  @Test
  @DisplayName("증분 경로 둘은 못 보낸 신규 주문분을 보류 큐에 넣는다")
  void theIncrementalPathsQueueWhatTheyCouldNotSend() throws IOException {
    // 수동 내보내기(exportOrders)는 일부러 뺐다. 그 경로의 산출물은 사용자가 저장을 요청해
    // 응답으로 경로까지 받은 파일이라, 큐로 옮기면 그 자리에서 사라진다. 증분도 아니라
    // 다음 회차에 전체 내보내기가 통째로 재전송된다. 파일은 저장 경로에 남으므로 유실도 없다.
    for (String[] site :
        new String[][] {
          {CONTROLLER, AUTO_COLLECT, "자동 수집"}, {SCHEDULER, PROCESS_FTP_UPLOAD, "스케줄 수집"}
        }) {
      String body = methodBody(sourceWithoutComments(MAIN.resolve(site[0])), site[1]);

      assertTrue(
          body.contains("pendingQueue.enqueue("),
          site[2] + " 경로가 실패분을 보류 큐에 넣지 않는다 — 내보내기가 증분이라 그 회차 주문이 영영 도달하지 못한다.");
      assertTrue(
          body.contains("pendingQueue.drain("), site[2] + " 경로가 보류분을 재전송하지 않는다 — 쌓이기만 하고 나가지 않는다.");
    }
  }

  @Test
  @DisplayName("재전송도 같은 관문을 통과한다 — 큐에는 평문이 들어 있을 수 있다")
  void resendAlsoPassesThroughTheGate() throws IOException {
    // 암호화가 안 돼서 못 보낸 회차분은 평문 상태로 큐에 들어간다. 재전송이 관문을 건너뛰면
    // 바로 그 평문이 다음 회차에 그대로 나간다 — 고친 것이 한 회차만 유효해진다.
    for (String[] site :
        new String[][] {
          {CONTROLLER, AUTO_COLLECT, "자동 수집"}, {SCHEDULER, PROCESS_FTP_UPLOAD, "스케줄 수집"}
        }) {
      String body = methodBody(sourceWithoutComments(MAIN.resolve(site[0])), site[1]);
      String drain = callArgument(body, "pendingQueue.drain(");

      assertFalse(drain.isEmpty(), site[2] + " 경로에서 재전송 호출을 찾지 못했다.");
      assertTrue(
          drain.contains("gate.prepare("), site[2] + " 경로의 재전송이 관문을 건너뛴다 — 큐에 남은 평문이 그대로 나간다.");
      assertTrue(drain.contains(".isRefused()"), site[2] + " 경로의 재전송이 관문의 거절을 확인하지 않는다.");
    }
  }

  @Test
  @DisplayName("평문 강등 문구가 프로덕션 어디에도 남아 있지 않다")
  void theDowngradeBranchesAreGone() throws IOException {
    // 이 문구들이 곧 강등 분기였다. 문구만 지우고 동작을 남기는 일은 없으므로,
    // 문구의 부재는 분기의 부재와 같이 움직인다.
    for (String phrase : new String[] {"평문 업로드 진행", "평문으로 업로드합니다", "원본 파일 업로드"}) {
      assertEquals(
          List.of(),
          productionFilesContaining(phrase, List.of()),
          "평문 강등 분기가 남아 있다: \"" + phrase + "\"");
    }
  }

  /**
   * 그 호출의 <b>괄호 안</b>만 잘라 낸다.
   *
   * <p>{@code indexOf} 뒤를 통째로 보면 그 호출 <i>다음</i>에 오는 코드가 함께 걸린다. 처음 이 가드를 그렇게 썼다가, 재전송 람다에서 관문을 통째로
   * 걷어냈는데도 뒤쪽 본 전송의 {@code gate.prepare(} 가 걸려 초록이었다 — 가드가 아무 것도 지키지 못하는 상태였다.
   */
  private static String callArgument(String source, String call) {
    int open = source.indexOf(call);
    if (open < 0) {
      return "";
    }
    int start = open + call.length();
    int cursor = start;
    int depth = 1;
    while (cursor < source.length() && depth > 0) {
      char c = source.charAt(cursor);
      if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth--;
      }
      cursor++;
    }
    return source.substring(start, Math.max(start, cursor - 1));
  }

  /**
   * 그 메서드 선언부터 <b>다음 메서드 선언 직전까지</b>를 잘라 낸다.
   *
   * <p>파일 전체에서 찾으면 옆 메서드의 배선이 함께 걸린다. {@code AdminController} 는 자동 수집과 수동 내보내기를 한 파일에 들고 있어, 한쪽만
   * 고쳐도 파일 단위 검사는 통과한다.
   */
  private static String methodBody(String source, String signature) {
    int start = source.indexOf(signature);
    if (start < 0) {
      return "";
    }
    int next = source.indexOf("\n  public ", start + signature.length());
    int nextPrivate = source.indexOf("\n  private ", start + signature.length());
    if (nextPrivate >= 0 && (next < 0 || nextPrivate < next)) {
      next = nextPrivate;
    }
    return next < 0 ? source.substring(start) : source.substring(start, next);
  }

  /**
   * {@code src/main} 에서 이 조각을 담고 있는 파일들을 찾는다.
   *
   * <p><b>주석을 걷어내고 본다.</b> javadoc 이 "관문을 거친다" 라고 적어 둔 것과 실제로 거치는 것은 다른 사실이다.
   */
  private static List<String> productionFilesContaining(String needle, List<String> excluded)
      throws IOException {

    List<String> found = new ArrayList<>();
    try (Stream<Path> files = Files.walk(MAIN)) {
      for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
        String relative = MAIN.relativize(file).toString().replace('\\', '/');
        if (excluded.contains(relative)) {
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

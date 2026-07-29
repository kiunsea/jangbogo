package com.jiniebox.jangbogo.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * '신규 주문 없음' 상태 파일의 최상위 구조 검증 (Phase 3-6).
 *
 * <p>수신측(jiniebox) 파서는 최상위 노드가 배열이 아니면 즉시 예외를 던지고 그 파일을 {@code failed/} 로 옮긴다. 그때 <b>복호화된 평문 JSON 이
 * 함께 디스크에 남는다.</b> 과거 상태 파일은 JSON 객체 ({@code {"status":…,"orders":[]}})였고 그래서 매 회차 예외 없이 실패했다.
 *
 * <p>이 테스트는 수신측이 실제로 적용하는 게이트({@code root.isArray()})를 그대로 재현한다. 브라우저· 네트워크·DB 를 쓰지 않는다 — {@code
 * createEmptyStatusFile} 은 파일 IO 만 한다.
 *
 * @author KIUNSEA
 */
class ExportStatusFileTest {

  private final ExportService exportService = new ExportService();
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  @DisplayName("상태 파일의 최상위 노드는 배열이다 (수신측 파서의 하드 게이트)")
  void statusFileTopLevelIsAnArray(@TempDir Path tempDir) throws Exception {
    String path = exportService.createEmptyStatusFile(tempDir.toString());

    String content = Files.readString(Path.of(path), StandardCharsets.UTF_8);
    JsonNode root = mapper.readTree(content);

    // 수신측이 주문 순회에 앞서 적용하는 바로 그 조건.
    assertTrue(
        root.isArray(),
        "최상위가 배열이 아니다. 수신측은 이 파일을 failed/ 로 보내고 평문 JSON 을 함께 남긴다. 실제 내용: " + content);
    assertEquals(0, root.size(), "신규 주문이 없을 때의 상태 파일은 0건이어야 한다: " + content);
  }

  @Test
  @DisplayName("상태 파일에는 주문 파일과 다른 최상위 구조를 쓰지 않는다")
  void statusFileMatchesTheOrderPayloadShape(@TempDir Path tempDir) throws Exception {
    String path = exportService.createEmptyStatusFile(tempDir.toString());
    String content = Files.readString(Path.of(path), StandardCharsets.UTF_8).trim();

    // 주문 0건일 때 buildJinieboxJsonFromOrders 가 내보내는 값과 같아야 한다.
    assertEquals("[]", content, "주문 파일과 상태 파일의 최상위 구조가 다시 갈라졌다.");

    // 과거 포맷이 되살아나는 것을 명시적으로 막는다.
    assertTrue(!content.contains("no_new_orders"), "폐기된 status 필드가 되살아났다: " + content);
    assertTrue(!content.contains("\"orders\""), "폐기된 orders 래퍼가 되살아났다: " + content);
  }

  @Test
  @DisplayName("파일명은 jangbogo_status_<yyyyMMdd>_<HHmmss>_ftp.json 규칙을 따른다")
  void statusFileNameFollowsTheConvention(@TempDir Path tempDir) throws Exception {
    String path = exportService.createEmptyStatusFile(tempDir.toString());
    String name = Path.of(path).getFileName().toString();

    assertTrue(
        name.matches("jangbogo_status_\\d{8}_\\d{6}_ftp\\.json"),
        "파일명 규칙이 바뀌었다: " + name + " — FtpPendingQueue 가 상태 파일을 접두사로 구분한다.");
  }

  @Test
  @DisplayName("저장 폴더가 없으면 만들어 준다")
  void createsTheTargetDirectoryWhenMissing(@TempDir Path tempDir) throws Exception {
    Path nested = tempDir.resolve("exports").resolve("ftp");

    String path = exportService.createEmptyStatusFile(nested.toString());

    assertTrue(Files.isRegularFile(Path.of(path)), "상태 파일이 만들어지지 않았다: " + path);
  }

  @Test
  @DisplayName("저장 경로가 비어 있으면 거부한다")
  void rejectsBlankDirectory() {
    assertThrows(IllegalArgumentException.class, () -> exportService.createEmptyStatusFile(null));
    assertThrows(IllegalArgumentException.class, () -> exportService.createEmptyStatusFile("   "));
  }
}

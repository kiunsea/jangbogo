package com.jiniebox.jangbogo.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jiniebox.jangbogo.svc.util.FtpPendingQueue;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link FtpPendingQueue} 회귀 테스트 (Phase 3-1).
 *
 * <p>FTP 서버·네트워크 없이 검증한다. 업로드 수행자를 주입할 수 있게 만든 이유가 이것이다.
 *
 * <p>지키려는 성질은 하나다 — <b>전송되지 못한 신규 주문분이 조용히 사라지지 않는다.</b>
 */
class FtpPendingQueueTest {

  @TempDir File savePath;

  @Test
  @DisplayName("업로드 실패분은 삭제되지 않고 보류 큐로 이동한다")
  void enqueueMovesFileIntoPendingDirectory() throws IOException {
    FtpPendingQueue queue = new FtpPendingQueue(savePath.getAbsolutePath());
    File failed = writeFile("jangbogo_orders_20260729_000215_ftp.json", "{\"orders\":[1,2]}");

    assertTrue(queue.enqueue(failed));

    assertFalse(failed.exists(), "원본은 원위치에 남지 않는다");
    assertEquals(1, queue.size());

    File moved = new File(queue.getDirectory(), "jangbogo_orders_20260729_000215_ftp.json");
    assertTrue(moved.isFile(), "보류 디렉터리에 같은 이름으로 존재해야 한다");
    assertEquals("{\"orders\":[1,2]}", Files.readString(moved.toPath(), StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("재전송에 성공하면 보류분이 제거된다")
  void drainRemovesSuccessfullyResentFiles() throws IOException {
    FtpPendingQueue queue = new FtpPendingQueue(savePath.getAbsolutePath());
    queue.enqueue(writeFile("jangbogo_orders_20260729_000100_ftp.json", "a"));
    queue.enqueue(writeFile("jangbogo_orders_20260729_000200_ftp.json", "b"));

    List<String> attempted = new ArrayList<>();
    int sent =
        queue.drain(
            file -> {
              attempted.add(file.getName());
              return true;
            });

    assertEquals(2, sent);
    assertEquals(0, queue.size());
    assertEquals(
        List.of(
            "jangbogo_orders_20260729_000100_ftp.json", "jangbogo_orders_20260729_000200_ftp.json"),
        attempted,
        "오래된 것부터 전송한다 (파일명 = 시각 순서)");
  }

  @Test
  @DisplayName("재전송이 실패하면 파일을 남기고 첫 실패에서 중단한다")
  void drainStopsAtFirstFailureAndKeepsFiles() throws IOException {
    FtpPendingQueue queue = new FtpPendingQueue(savePath.getAbsolutePath());
    queue.enqueue(writeFile("jangbogo_orders_20260729_000100_ftp.json", "a"));
    queue.enqueue(writeFile("jangbogo_orders_20260729_000200_ftp.json", "b"));
    queue.enqueue(writeFile("jangbogo_orders_20260729_000300_ftp.json", "c"));

    List<String> attempted = new ArrayList<>();
    int sent =
        queue.drain(
            file -> {
              attempted.add(file.getName());
              // 두 번째에서 실패. FTP 가 죽었다면 나머지도 실패하므로 더 시도하지 않아야 한다.
              return attempted.size() < 2;
            });

    assertEquals(1, sent);
    assertEquals(2, attempted.size(), "첫 실패 이후로는 시도하지 않는다");
    assertEquals(2, queue.size(), "실패분과 미시도분이 모두 남는다");
  }

  @Test
  @DisplayName("업로드 수행자가 예외를 던져도 보류분을 잃지 않는다")
  void drainTreatsUploaderExceptionAsFailure() throws IOException {
    FtpPendingQueue queue = new FtpPendingQueue(savePath.getAbsolutePath());
    queue.enqueue(writeFile("jangbogo_orders_20260729_000100_ftp.json", "a"));

    int sent =
        queue.drain(
            file -> {
              throw new IllegalStateException("FTP 연결 끊김");
            });

    assertEquals(0, sent);
    assertEquals(1, queue.size());
  }

  @Test
  @DisplayName("건수 상한을 넘으면 오래된 것부터 폐기한다")
  void enforcesMaxFileCount() throws IOException {
    FtpPendingQueue queue = new FtpPendingQueue(savePath.getAbsolutePath(), 2, Long.MAX_VALUE);
    queue.enqueue(writeFile("jangbogo_orders_20260729_000100_ftp.json", "a"));
    queue.enqueue(writeFile("jangbogo_orders_20260729_000200_ftp.json", "b"));
    queue.enqueue(writeFile("jangbogo_orders_20260729_000300_ftp.json", "c"));

    assertEquals(2, queue.size());
    assertFalse(
        new File(queue.getDirectory(), "jangbogo_orders_20260729_000100_ftp.json").exists(),
        "가장 오래된 것이 폐기된다");
    assertTrue(
        new File(queue.getDirectory(), "jangbogo_orders_20260729_000300_ftp.json").exists(),
        "가장 최근 것은 남는다");
  }

  @Test
  @DisplayName("보관 기간을 넘긴 보류분은 폐기한다")
  void enforcesMaxAge() throws IOException {
    long oneDay = 24L * 60 * 60 * 1000;
    FtpPendingQueue queue = new FtpPendingQueue(savePath.getAbsolutePath(), 50, oneDay);

    queue.enqueue(writeFile("jangbogo_orders_20260701_000100_ftp.json", "old"));
    File stale = new File(queue.getDirectory(), "jangbogo_orders_20260701_000100_ftp.json");
    assertTrue(stale.setLastModified(System.currentTimeMillis() - (3 * oneDay)));

    queue.enqueue(writeFile("jangbogo_orders_20260729_000100_ftp.json", "new"));

    assertEquals(1, queue.size());
    assertFalse(stale.exists(), "기간 초과분은 폐기된다");
  }

  @Test
  @DisplayName("이름이 겹쳐도 둘 다 보존하고 접두사·확장자를 유지한다")
  void keepsBothFilesOnNameCollision() throws IOException {
    FtpPendingQueue queue = new FtpPendingQueue(savePath.getAbsolutePath());
    String name = "jangbogo_orders_20260729_000215_ftp.json.encrypted";

    queue.enqueue(writeFile(name, "first"));
    queue.enqueue(writeFile(name, "second"));

    assertEquals(2, queue.size(), "같은 이름이어도 덮어쓰지 않는다");
    assertTrue(new File(queue.getDirectory(), name).isFile());

    File renamed =
        new File(queue.getDirectory(), "jangbogo_orders_20260729_000215_ftp_r1.json.encrypted");
    assertTrue(renamed.isFile(), "접두사와 확장자 체인을 유지한 채 _r1 을 끼워 넣는다");
    assertEquals("second", Files.readString(renamed.toPath(), StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("보류가 없으면 재전송은 아무 일도 하지 않는다")
  void drainIsNoOpWhenEmpty() {
    FtpPendingQueue queue = new FtpPendingQueue(savePath.getAbsolutePath());

    int sent =
        queue.drain(
            file -> {
              throw new AssertionError("보류가 없는데 업로드를 시도했다");
            });

    assertEquals(0, sent);
    assertEquals(0, queue.size());
  }

  // 헬퍼

  private File writeFile(String name, String content) throws IOException {
    File file = new File(savePath, name);
    Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
    return file;
  }
}

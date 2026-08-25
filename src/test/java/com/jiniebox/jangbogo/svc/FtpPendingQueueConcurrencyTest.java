package com.jiniebox.jangbogo.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jiniebox.jangbogo.svc.util.FtpPendingQueue;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 보류 큐를 <b>둘이 동시에</b> 훑을 때의 성질을 잰다.
 *
 * <h2>왜 필요한가</h2>
 *
 * <p>보류분을 훑는 주체가 둘이 됐다 — 스케줄 수집과 자동 수집(대시보드 실행). 잠그지 않으면 두 쪽이 같은 목록을 얻어 <b>같은 증분을 각각 올린다.</b> 수신측
 * 중복 검사가 걸러내기는 하지만 그건 완화지 방어가 아니고, 뒤늦은 삭제는 이미 사라진 파일에 대해 헛경고를 남긴다.
 *
 * <p>그리고 재전송용 암호문 임시본은 큐 <b>밖</b>에 있어야 한다. 예전처럼 원본 옆에 만들면, 정리 전에 프로세스가 죽었을 때 그 임시본이 다음 회차의 목록에 짐인 척
 * 섞여 회차를 중간에 끊는다.
 *
 * <p>네트워크·FTP 서버·DB 를 쓰지 않는다.
 */
class FtpPendingQueueConcurrencyTest {

  @TempDir File savePath;

  @Test
  @DisplayName("두 회차가 겹치면 한쪽만 훑는다 — 같은 보류분이 두 번 나가지 않는다")
  void aSecondDrainStandsDownWhileTheFirstIsRunning() throws Exception {
    FtpPendingQueue queue = new FtpPendingQueue(savePath.getAbsolutePath());
    queue.enqueue(writeIncrement("jangbogo_orders_20260825_100000_ftp.json"));
    queue.enqueue(writeIncrement("jangbogo_orders_20260825_100100_ftp.json"));

    List<String> uploaded = new CopyOnWriteArrayList<>();
    CountDownLatch firstIsInside = new CountDownLatch(1);
    CountDownLatch secondIsDone = new CountDownLatch(1);
    AtomicInteger secondSent = new AtomicInteger(-1);

    // 첫 회차 — 첫 건을 잡은 채로 둘째 회차가 끝날 때까지 머문다.
    Thread first =
        new Thread(
            () ->
                queue.drain(
                    file -> {
                      uploaded.add(file.getName());
                      firstIsInside.countDown();
                      try {
                        secondIsDone.await(5, TimeUnit.SECONDS);
                      } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                      }
                      return true;
                    }));

    // 둘째 회차 — 첫 회차가 안에 들어간 뒤에 시작한다.
    Thread second =
        new Thread(
            () -> {
              try {
                firstIsInside.await(5, TimeUnit.SECONDS);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              secondSent.set(
                  queue.drain(
                      file -> {
                        uploaded.add(file.getName());
                        return true;
                      }));
              secondIsDone.countDown();
            });

    first.start();
    second.start();
    second.join(10_000);
    first.join(10_000);

    assertEquals(0, secondSent.get(), "겹친 회차가 물러나지 않고 함께 훑었다 — 같은 증분이 두 번 나간다.");
    assertEquals(2, uploaded.size(), "올라간 건수가 보류 건수와 다르다 — 중복 전송이 있었다.");
    assertEquals(
        List.of(
            "jangbogo_orders_20260825_100000_ftp.json", "jangbogo_orders_20260825_100100_ftp.json"),
        uploaded,
        "같은 파일이 두 번 올라갔거나 순서가 깨졌다.");
    assertEquals(0, queue.size(), "재전송에 성공했는데 큐가 비워지지 않았다.");
  }

  @Test
  @DisplayName("지난 회차가 남긴 스테이징 임시본은 회차 시작에 정리된다")
  void staleStagingLeftoversArePurgedBeforeTheRoundStarts() throws IOException {
    FtpPendingQueue queue = new FtpPendingQueue(savePath.getAbsolutePath());
    queue.enqueue(writeIncrement("jangbogo_orders_20260825_100000_ftp.json"));

    // 업로드와 정리 사이에서 죽은 회차가 남긴 것.
    File staging = queue.getStagingDirectory();
    assertTrue(staging.mkdirs() || staging.isDirectory());
    File stale = new File(staging, "jangbogo_orders_20260825_090000_ftp.json.encrypted");
    Files.writeString(stale.toPath(), "ENC(지난 회차)", StandardCharsets.UTF_8);

    queue.drain(file -> true);

    assertFalse(stale.exists(), "지난 회차의 임시본이 그대로 남았다 — 계속 쌓인다.");
  }

  @Test
  @DisplayName("스테이징의 임시본은 보류 목록에 섞이지 않는다 — 회차가 중간에 끊기던 자리다")
  void stagingLeftoversAreNeverTreatedAsPayload() throws IOException {
    FtpPendingQueue queue = new FtpPendingQueue(savePath.getAbsolutePath());
    File payload = writeIncrement("jangbogo_orders_20260825_100000_ftp.json");
    queue.enqueue(payload);

    File staging = queue.getStagingDirectory();
    assertTrue(staging.mkdirs() || staging.isDirectory());
    Files.writeString(
        new File(staging, "jangbogo_orders_20260825_100000_ftp.json.encrypted").toPath(),
        "ENC(...)",
        StandardCharsets.UTF_8);

    assertEquals(1, queue.size(), "스테이징의 임시본이 보류 건수로 세어졌다.");

    List<String> uploaded = new CopyOnWriteArrayList<>();
    int sent =
        queue.drain(
            file -> {
              uploaded.add(file.getName());
              return true;
            });

    assertEquals(1, sent);
    assertEquals(
        List.of("jangbogo_orders_20260825_100000_ftp.json"), uploaded, "임시본까지 짐으로 보고 올리려 들었다.");
  }

  @Test
  @DisplayName("락 파일은 보류 목록에 섞이지 않는다 — 수신측에 올라가면 안 된다")
  void theLockFileIsNeverUploaded() throws IOException {
    FtpPendingQueue queue = new FtpPendingQueue(savePath.getAbsolutePath());
    queue.enqueue(writeIncrement("jangbogo_orders_20260825_100000_ftp.json"));

    // 첫 회차가 락 파일을 만든다.
    queue.drain(file -> true);
    assertEquals(0, queue.size());

    File lockFile = new File(queue.getDirectory(), ".drain.lock");
    assertTrue(lockFile.isFile(), "락 파일이 만들어지지 않았다 — 이 테스트의 전제가 깨졌다.");

    List<String> uploaded = new CopyOnWriteArrayList<>();
    int sent =
        queue.drain(
            file -> {
              uploaded.add(file.getName());
              return true;
            });

    assertEquals(0, sent, "보낼 짐이 없는데 무언가를 보냈다.");
    assertEquals(List.of(), uploaded, "락 파일을 수신측에 올리려 들었다.");
  }

  @Test
  @DisplayName("보류가 없으면 락 파일도 만들지 않는다")
  void anEmptyQueueLeavesNoLockFileBehind() {
    FtpPendingQueue queue = new FtpPendingQueue(savePath.getAbsolutePath());

    assertEquals(0, queue.drain(file -> true));

    assertFalse(queue.getDirectory().exists(), "보낼 것이 없는데 보류 디렉터리를 만들었다.");
  }

  private File writeIncrement(String name) throws IOException {
    File file = new File(savePath, name);
    Files.writeString(file.toPath(), "{\"orders\":[1,2]}", StandardCharsets.UTF_8);
    return file;
  }
}

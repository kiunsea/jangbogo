package com.jiniebox.jangbogo.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jiniebox.jangbogo.svc.util.FtpEncryptionGate;
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
 * 관문({@link FtpEncryptionGate})과 보류 큐({@link FtpPendingQueue})가 <b>함께</b> 만들어 내는 성질을 잰다.
 *
 * <p>둘 중 하나만으로는 이 결함이 안 고쳐진다.
 *
 * <ul>
 *   <li>관문만 있고 큐가 없으면: 평문은 안 나가는데 <b>그 회차 신규 주문이 그대로 사라진다.</b> 내보내기가 증분이라 수신측에 영원히 도달하지 못한다. 실제로
 *       2026-08-24 에 그 형태로 유실됐다 — 업로드가 {@code Connection refused} 로 실패한 뒤 {@code finally} 가 임시파일을
 *       지웠고, {@code pending/} 은 생성조차 되지 않았다.
 *   <li>큐만 있고 관문이 없으면: 유실은 없는데 <b>평문이 나간다.</b> 고치려던 것이 그대로다.
 * </ul>
 *
 * <p>여기서 재는 것은 세 가지다 — (a) 암호화 실패 시 업로드 호출이 <b>일어나지 않는다</b>, (b) 공개키가 없을 때도 같다, (c) 못 보낸 것은 {@code
 * pending/} 에 들어가 다음 회차에 다시 나간다.
 *
 * <p>이 테스트가 호출부의 실제 배선을 대신 증명하지는 않는다. 그쪽은 {@code FtpTransferRefusalWiringTest} 가 소스 형태로 본다 — 이 저장소는
 * "테스트는 초록인데 프로덕션 호출자가 0건" 을 여러 번 겪었다.
 *
 * <p>네트워크·FTP 서버·DB 를 쓰지 않는다.
 */
class FtpTransferRefusalTest {

  @TempDir File savePath;

  /** 호출 사실을 기록하는 업로더. "부르지 않았다" 를 재려면 부른 것을 세어야 한다. */
  private static final class RecordingUploader {
    private final List<String> attempts = new ArrayList<>();
    private boolean serverUp;

    RecordingUploader(boolean serverUp) {
      this.serverUp = serverUp;
    }

    boolean upload(String path) {
      attempts.add(new File(path).getName());
      return serverUp;
    }
  }

  /** 원본에 표식을 붙여 "암호화한 척" 하는 수행자. */
  private static final FtpEncryptionGate.Encryptor MARKS_AS_ENCRYPTED =
      (src, dst, key) -> {
        try {
          Files.writeString(
              new File(dst).toPath(),
              "ENC(" + Files.readString(new File(src).toPath(), StandardCharsets.UTF_8) + ")",
              StandardCharsets.UTF_8);
          return true;
        } catch (IOException e) {
          return false;
        }
      };

  private static final FtpEncryptionGate.Encryptor ALWAYS_FAILS = (src, dst, key) -> false;

  @Test
  @DisplayName("(a) 암호화 켬 + 암호화 실패 → 업로드를 부르지 않고, 실패로 보고하며, 보류 큐에 넣는다")
  void encryptionFailureNeverReachesTheWire() throws IOException {
    RecordingUploader uploader = new RecordingUploader(true); // 서버는 멀쩡하다
    FtpEncryptionGate gate = new FtpEncryptionGate(true, "공개키있음", ALWAYS_FAILS);
    FtpPendingQueue queue = new FtpPendingQueue(savePath.getAbsolutePath());
    File increment = writeIncrement("jangbogo_orders_20260824_101500_ftp.json");

    Outcome outcome = sendOneRound(gate, queue, uploader, increment.getAbsolutePath(), true);

    assertTrue(outcome.refused, "암호화에 실패했는데 전송을 진행했다.");
    assertEquals(List.of(), uploader.attempts, "업로드를 불렀다 — 서버가 멀쩡하므로 이 호출은 평문을 그대로 실어 보낸다.");
    assertFalse(outcome.reported, "응답이 성공이라 보고했다 — 운영자는 평문이 나간 줄도, 안 나간 줄도 모른다.");
    assertEquals(1, queue.size(), "보내지 못한 신규 주문분이 보류 큐에 없다 — 그 회차 주문이 사라진다.");
    assertFalse(increment.exists(), "원위치에 남았다 — 큐로 옮겨져야 다음 회차에 다시 나간다.");
  }

  @Test
  @DisplayName("(b) 암호화 켬 + 공개키 없음 → 업로드를 부르지 않고, 실패로 보고하며, 보류 큐에 넣는다")
  void missingPublicKeyNeverReachesTheWire() throws IOException {
    RecordingUploader uploader = new RecordingUploader(true);
    FtpEncryptionGate gate = new FtpEncryptionGate(true, "", MARKS_AS_ENCRYPTED);
    FtpPendingQueue queue = new FtpPendingQueue(savePath.getAbsolutePath());
    File increment = writeIncrement("jangbogo_orders_20260824_101500_ftp.json");

    Outcome outcome = sendOneRound(gate, queue, uploader, increment.getAbsolutePath(), true);

    assertTrue(outcome.refused, "공개키가 없는데 전송을 진행했다.");
    assertEquals(List.of(), uploader.attempts, "업로드를 불렀다 — 평문이 그대로 실린다.");
    assertFalse(outcome.reported);
    assertEquals(1, queue.size());
  }

  @Test
  @DisplayName("(c) 암호화는 됐는데 업로드가 실패하면 암호문이 보류 큐로 간다")
  void uploadFailureIsQueuedAsCiphertext() throws IOException {
    RecordingUploader uploader = new RecordingUploader(false); // Connection refused
    FtpEncryptionGate gate = new FtpEncryptionGate(true, "공개키있음", MARKS_AS_ENCRYPTED);
    FtpPendingQueue queue = new FtpPendingQueue(savePath.getAbsolutePath());
    File increment = writeIncrement("jangbogo_orders_20260824_101500_ftp.json");

    Outcome outcome = sendOneRound(gate, queue, uploader, increment.getAbsolutePath(), true);

    assertFalse(outcome.reported);
    assertEquals(
        List.of("jangbogo_orders_20260824_101500_ftp.json.encrypted"),
        uploader.attempts,
        "회선에 실린 것이 암호문이 아니다.");
    assertEquals(1, queue.size(), "2026-08-24 실측 형태 — 실패분이 어디에도 남지 않았다.");
    assertTrue(
        new File(queue.getDirectory(), "jangbogo_orders_20260824_101500_ftp.json.encrypted")
            .isFile(),
        "보류 큐에 암호문이 아닌 것이 들어갔다.");
    assertFalse(increment.exists(), "평문 원본은 남기지 않는다 — 큐에는 암호문만 남는다.");
  }

  @Test
  @DisplayName("(c) 상태 파일은 실패해도 큐에 넣지 않는다 — 뒤늦은 하트비트는 수신측 시각을 오도한다")
  void statusHeartbeatIsNotQueued() throws IOException {
    RecordingUploader uploader = new RecordingUploader(false);
    FtpEncryptionGate gate = new FtpEncryptionGate(true, "공개키있음", MARKS_AS_ENCRYPTED);
    FtpPendingQueue queue = new FtpPendingQueue(savePath.getAbsolutePath());
    File status = writeIncrement("jangbogo_status_20260824_101500_ftp.json");

    sendOneRound(gate, queue, uploader, status.getAbsolutePath(), false /* 신규 주문 없음 */);

    assertEquals(0, queue.size(), "상태 파일이 보류 큐에 쌓였다 — 다음 회차에 과거 시각이 나간다.");
  }

  @Test
  @DisplayName("거절로 큐에 남은 평문은, 설정을 고친 다음 회차에 암호문으로 나간다")
  void queuedPlaintextGoesOutEncryptedOnceTheKeyIsFixed() throws IOException {
    FtpPendingQueue queue = new FtpPendingQueue(savePath.getAbsolutePath());

    // 1회차 — 공개키가 없어 거절, 평문이 큐에 남는다.
    RecordingUploader firstRound = new RecordingUploader(true);
    File increment = writeIncrement("jangbogo_orders_20260824_101500_ftp.json");
    sendOneRound(
        new FtpEncryptionGate(true, "", MARKS_AS_ENCRYPTED),
        queue,
        firstRound,
        increment.getAbsolutePath(),
        true);
    assertEquals(List.of(), firstRound.attempts);
    assertEquals(1, queue.size());

    // 2회차 — 공개키를 넣었다. 큐가 비워지면서 암호문으로 나간다.
    RecordingUploader secondRound = new RecordingUploader(true);
    FtpEncryptionGate fixed = new FtpEncryptionGate(true, "공개키있음", MARKS_AS_ENCRYPTED);
    int sent = drain(queue, fixed, secondRound);

    assertEquals(1, sent);
    assertEquals(
        List.of("jangbogo_orders_20260824_101500_ftp.json.encrypted"),
        secondRound.attempts,
        "큐에 남아 있던 평문이 평문 그대로 나갔다 — 관문을 재전송에도 물려야 한다.");
    assertEquals(0, queue.size(), "재전송에 성공했는데 큐가 비워지지 않았다.");
    assertEquals(
        0,
        queue.getDirectory().listFiles().length,
        "재전송용 암호화 임시본이 큐에 남았다 — 다음 회차에 같은 주문이 한 번 더 나간다.");
  }

  @Test
  @DisplayName("설정이 그대로면 재전송도 거절되고, 보류분은 큐에 남는다")
  void queuedPlaintextStaysQueuedWhileTheKeyIsStillMissing() throws IOException {
    FtpPendingQueue queue = new FtpPendingQueue(savePath.getAbsolutePath());
    queue.enqueue(writeIncrement("jangbogo_orders_20260824_101500_ftp.json"));

    RecordingUploader uploader = new RecordingUploader(true);
    int sent = drain(queue, new FtpEncryptionGate(true, "", MARKS_AS_ENCRYPTED), uploader);

    assertEquals(0, sent);
    assertEquals(List.of(), uploader.attempts, "재전송에서 평문이 나갔다.");
    assertEquals(1, queue.size(), "보류분을 잃었다 — 설정을 고쳐도 되살릴 수 없다.");
  }

  // ---------------------------------------------------------------------------
  // 호출부(AdminController.autoCollect / MallSchedulerService.processFtpUpload)가
  // 밟는 순서를 그대로 옮긴 것이다. 순서 자체가 계약이라 여기서 한 번 고정한다.
  // ---------------------------------------------------------------------------

  /** {@code uploaded} 는 응답의 {@code autoFtpUploaded} 에 해당한다. */
  private static final class Outcome {
    boolean refused;
    boolean reported;
  }

  private Outcome sendOneRound(
      FtpEncryptionGate gate,
      FtpPendingQueue queue,
      RecordingUploader uploader,
      String ftpReadyFile,
      boolean hasNewOrders) {

    Outcome outcome = new Outcome();
    String fileToUpload = ftpReadyFile;
    boolean uploadSuccess = false;

    FtpEncryptionGate.Prepared prepared = gate.prepare(ftpReadyFile);
    if (prepared.isRefused()) {
      outcome.refused = true;
    } else {
      fileToUpload = prepared.getFileToUpload();
      uploadSuccess = uploader.upload(fileToUpload);
    }
    outcome.reported = uploadSuccess;

    if (!uploadSuccess && hasNewOrders) {
      queue.enqueue(new File(fileToUpload));
    }
    // 암호화한 경우 평문 원본은 성공·실패와 무관하게 지운다. 큐에는 암호문만 남는다.
    if (!ftpReadyFile.equals(fileToUpload)) {
      new File(ftpReadyFile).delete();
    }
    return outcome;
  }

  private int drain(FtpPendingQueue queue, FtpEncryptionGate gate, RecordingUploader uploader) {
    return queue.drain(
        file -> {
          FtpEncryptionGate.Prepared prepared = gate.prepare(file.getAbsolutePath());
          if (prepared.isRefused()) {
            return false;
          }
          String resendPath = prepared.getFileToUpload();
          try {
            return uploader.upload(resendPath);
          } finally {
            if (!file.getAbsolutePath().equals(resendPath)) {
              new File(resendPath).delete();
            }
          }
        });
  }

  private File writeIncrement(String name) throws IOException {
    File file = new File(savePath, name);
    Files.writeString(file.toPath(), "{\"orders\":[1,2]}", StandardCharsets.UTF_8);
    return file;
  }
}

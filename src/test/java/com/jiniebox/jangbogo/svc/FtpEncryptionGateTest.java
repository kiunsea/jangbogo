package com.jiniebox.jangbogo.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jiniebox.jangbogo.svc.util.FtpEncryptionGate;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link FtpEncryptionGate} 회귀 테스트.
 *
 * <p>지키려는 성질은 하나다 — <b>암호화를 요구한 상태에서 평문이 회선에 실리지 않는다.</b>
 *
 * <p>고치기 전에는 세 경로(자동수집·수동 내보내기·스케줄 수집)가 모두 이렇게 돼 있었다.
 *
 * <pre>{@code
 * if (encryptSuccess) { fileToUpload = encryptedFilePath; fileEncrypted = true; }
 * else { logger.warn("... 암호화 실패 - 평문 업로드 진행"); }   // ← 여기서 평문이 나갔다
 * }</pre>
 *
 * <p>경고 한 줄만 남고 업로드는 그대로 진행됐으며, 응답은 {@code autoFtpUploaded=true} 로 <b>성공</b>이라 보고했다. 채널 자체도 평문
 * FTP(수신측에 TLS 설정이 없다)라 강등은 곧 노출이고, 실리는 것은 주문번호·상품명·구매일자다.
 *
 * <p>실제 RSA 는 정상 동작하므로 실패 경로는 수행자를 주입하지 않고는 재현할 수 없다. 그래서 {@link FtpEncryptionGate.Encryptor} 가 열려
 * 있다 — 이 결함이 오래 남은 이유가 바로 "실패를 만들어 볼 수 없었다" 는 것이다.
 *
 * <p>네트워크·FTP 서버·DB 를 쓰지 않는다.
 */
class FtpEncryptionGateTest {

  @TempDir File workDir;

  /** 아무 것도 하지 않고 실패만 돌려주는 수행자. */
  private static final FtpEncryptionGate.Encryptor ALWAYS_FAILS = (src, dst, key) -> false;

  /** 부분 산출물만 남기고 실패하는 수행자. 스트림을 열었다가 도중에 터진 상황이다. */
  private static FtpEncryptionGate.Encryptor failsAfterWritingPartial() {
    return (src, dst, key) -> {
      try {
        Files.writeString(new File(dst).toPath(), "부분 산출물", StandardCharsets.UTF_8);
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
      return false;
    };
  }

  /** 원본 뒤에 표식을 붙여 "암호화한 척" 하는 수행자. */
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

  @Test
  @DisplayName("암호화 켬 + 암호화 실패 → 거절한다 (평문으로 강등하지 않는다)")
  void encryptionFailureIsRefusedInsteadOfDowngraded() throws IOException {
    FtpEncryptionGate gate = new FtpEncryptionGate(true, "공개키있음", ALWAYS_FAILS);
    File plain = writeFile("jangbogo_orders_20260824_101500_ftp.json", "{\"orders\":[1]}");

    FtpEncryptionGate.Prepared prepared = gate.prepare(plain.getAbsolutePath());

    assertTrue(prepared.isRefused(), "암호화에 실패했는데 전송을 허용했다 — 평문이 회선에 실린다.");
    assertEquals(FtpEncryptionGate.Decision.REFUSED, prepared.getDecision());
    assertNull(prepared.getFileToUpload(), "거절인데 올릴 파일을 돌려준다 — 호출부가 그대로 올린다.");
    assertTrue(prepared.getReason().contains("암호화"), "거절 사유가 응답·로그에 실릴 만큼 구체적이지 않다.");
    assertTrue(plain.isFile(), "거절해도 원본은 남아 있어야 보류 큐로 넘길 수 있다.");
  }

  @Test
  @DisplayName("암호화 켬 + 공개키 없음 → 거절한다 (평문으로 강등하지 않는다)")
  void missingPublicKeyIsRefusedInsteadOfDowngraded() throws IOException {
    File plain = writeFile("jangbogo_orders_20260824_101500_ftp.json", "{\"orders\":[1]}");

    for (String noKey : new String[] {"", null}) {
      FtpEncryptionGate gate = new FtpEncryptionGate(true, noKey, MARKS_AS_ENCRYPTED);

      FtpEncryptionGate.Prepared prepared = gate.prepare(plain.getAbsolutePath());

      assertTrue(prepared.isRefused(), "공개키가 없는데 전송을 허용했다 — 평문이 회선에 실린다. (키=" + noKey + ")");
      assertNull(prepared.getFileToUpload());
      assertTrue(prepared.getReason().contains("Public Key"), "거절 사유가 무엇을 고쳐야 하는지 알려주지 않는다.");
    }
    assertTrue(plain.isFile(), "거절해도 원본은 남아 있어야 보류 큐로 넘길 수 있다.");
  }

  @Test
  @DisplayName("암호화 실패로 남은 부분 산출물은 지운다 — 깨진 암호문이 큐에 실리면 안 된다")
  void partialCiphertextIsDiscardedOnFailure() throws IOException {
    FtpEncryptionGate gate = new FtpEncryptionGate(true, "공개키있음", failsAfterWritingPartial());
    File plain = writeFile("jangbogo_orders_20260824_101500_ftp.json", "{\"orders\":[1]}");
    File partial = new File(plain.getAbsolutePath() + ".encrypted");

    assertTrue(gate.prepare(plain.getAbsolutePath()).isRefused());

    assertFalse(partial.exists(), "암호화에 실패하고도 부분 산출물이 남았다 — 다음 회차에 깨진 암호문이 그대로 올라간다.");
  }

  @Test
  @DisplayName("암호화 켬 + 공개키 있음 + 성공 → 암호문을 보낸다")
  void successfulEncryptionSendsCiphertext() throws IOException {
    FtpEncryptionGate gate = new FtpEncryptionGate(true, "공개키있음", MARKS_AS_ENCRYPTED);
    File plain = writeFile("jangbogo_orders_20260824_101500_ftp.json", "본문");

    FtpEncryptionGate.Prepared prepared = gate.prepare(plain.getAbsolutePath());

    assertFalse(prepared.isRefused());
    assertTrue(prepared.isEncrypted());
    assertEquals(plain.getAbsolutePath() + ".encrypted", prepared.getFileToUpload());
    assertEquals(
        "ENC(본문)",
        Files.readString(new File(prepared.getFileToUpload()).toPath(), StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("암호화 끔 → 평문을 그대로 보낸다 (운영자의 명시적 선택이다)")
  void disabledEncryptionSendsPlaintext() throws IOException {
    FtpEncryptionGate gate = new FtpEncryptionGate(false, "", ALWAYS_FAILS);
    File plain = writeFile("jangbogo_orders_20260824_101500_ftp.json", "본문");

    FtpEncryptionGate.Prepared prepared = gate.prepare(plain.getAbsolutePath());

    assertFalse(prepared.isRefused(), "암호화를 끈 것은 명시적 선택이다. 이것까지 막으면 기능이 죽는다.");
    assertEquals(FtpEncryptionGate.Decision.PLAINTEXT, prepared.getDecision());
    assertEquals(plain.getAbsolutePath(), prepared.getFileToUpload());
    assertFalse(prepared.isEncrypted());
  }

  @Test
  @DisplayName("이미 암호문이면 다시 암호화하지 않는다 — 두 번 씌우면 수신측이 풀지 못한다")
  void ciphertextFromTheQueueIsNotEncryptedTwice() throws IOException {
    FtpEncryptionGate gate = new FtpEncryptionGate(true, "공개키있음", MARKS_AS_ENCRYPTED);
    File ciphertext = writeFile("jangbogo_orders_20260824_101500_ftp.json.encrypted", "ENC(본문)");

    FtpEncryptionGate.Prepared prepared = gate.prepare(ciphertext.getAbsolutePath());

    assertTrue(prepared.isEncrypted());
    assertEquals(ciphertext.getAbsolutePath(), prepared.getFileToUpload());
    assertFalse(new File(ciphertext.getAbsolutePath() + ".encrypted").exists(), "암호문에 한 겹을 더 씌웠다.");
  }

  @Test
  @DisplayName("암호화 수행자가 예외를 던져도 거절로 처리한다")
  void encryptorThrowingIsTreatedAsRefusal() throws IOException {
    FtpEncryptionGate gate =
        new FtpEncryptionGate(
            true,
            "공개키있음",
            (src, dst, key) -> {
              throw new IllegalStateException("키 파싱 실패");
            });
    File plain = writeFile("jangbogo_orders_20260824_101500_ftp.json", "본문");

    FtpEncryptionGate.Prepared prepared = gate.prepare(plain.getAbsolutePath());

    assertTrue(prepared.isRefused(), "예외가 새어 나가 호출부의 catch 로 떨어지면 평문 경로로 흐를 수 있다.");
  }

  @Test
  @DisplayName("보낼 파일 경로가 없으면 거절한다")
  void missingPathIsRefused() {
    FtpEncryptionGate gate = new FtpEncryptionGate(true, "공개키있음", MARKS_AS_ENCRYPTED);

    assertTrue(gate.prepare(null).isRefused());
    assertTrue(gate.prepare("").isRefused());
  }

  private File writeFile(String name, String content) throws IOException {
    File file = new File(workDir, name);
    Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
    return file;
  }
}

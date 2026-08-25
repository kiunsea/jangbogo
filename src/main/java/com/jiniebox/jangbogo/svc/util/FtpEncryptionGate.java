package com.jiniebox.jangbogo.svc.util;

import com.jiniebox.jangbogo.util.security.RsaFileEncryption;
import java.io.File;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * FTP 전송 직전의 암호화 관문.
 *
 * <p>무엇이 실제로 회선에 실리는지를 여기서만 정한다. 예전에는 자동수집·수동 내보내기·스케줄 수집 세 경로가 각자 같은 분기를 따로 들고 있었고, 세 곳 모두 <b>암호화를
 * 켰는데 암호화가 안 되면 평문으로 강등해서 그대로 보냈다.</b> 경고 한 줄만 남기고 응답은 성공이라 보고했으므로, 운영자는 개인 구매 이력이 평문으로 나간 것을 알 방법이
 * 없었다. 채널 자체도 평문 FTP 라(수신측에 TLS 설정이 없다) 강등은 곧 노출이다.
 *
 * <p>규칙:
 *
 * <ul>
 *   <li><b>이미 암호문이면 그대로 보낸다.</b> 보류 큐에 쌓인 {@code .encrypted} 를 다시 암호화하지 않기 위한 것이다.
 *   <li><b>암호화가 꺼져 있으면 평문으로 보낸다.</b> 운영자의 명시적 선택이므로 막지 않는다. 다만 로그에는 남긴다.
 *   <li><b>암호화가 켜져 있는데 공개키가 없으면 거절한다.</b> 보내지 않는다.
 *   <li><b>암호화가 켜져 있는데 암호화에 실패하면 거절한다.</b> 보내지 않는다.
 * </ul>
 *
 * <p>거절은 조용하지 않다. {@link Prepared#getReason()} 이 사유 문장을 들고 오고, 호출부는 그것을 응답과 로그에 실패로 싣는다. 보낼 파일은
 * 호출부가 {@link FtpPendingQueue} 로 넘겨 다음 회차에 다시 시도한다 — 설정을 고치면 그때 암호문으로 나간다.
 *
 * <p>암호화 수행자를 주입할 수 있게 둔 것은 <b>실패를 강제할 수 있어야 하기 때문</b>이다. 실제 RSA 는 잘 동작하므로(실측 확인) 실패 경로는 주입 없이는
 * 테스트할 수 없고, 테스트할 수 없는 채로 두었던 것이 이 결함이 오래 남은 이유다.
 */
public final class FtpEncryptionGate {

  private static final Logger logger = LogManager.getLogger(FtpEncryptionGate.class);

  /** 암호문 파일 확장자. {@code RsaFileEncryption} 산출물과 보류 큐 파일명이 공유한다. */
  public static final String ENCRYPTED_SUFFIX = ".encrypted";

  /** 전송 판정. */
  public enum Decision {
    /** 암호화가 꺼져 있어 평문 그대로 보낸다. */
    PLAINTEXT,
    /** 암호문을 보낸다. */
    ENCRYPTED,
    /** 보내지 않는다. */
    REFUSED
  }

  /** 암호화 수행자. 실패 경로를 단위테스트할 수 있도록 주입한다. */
  public interface Encryptor {
    /**
     * @param sourcePath 평문 파일 경로
     * @param targetPath 암호문 파일 경로
     * @param publicKey Base64 공개키
     * @return 성공 여부
     */
    boolean encrypt(String sourcePath, String targetPath, String publicKey);
  }

  /** {@link #prepare(String)} 의 판정 결과. */
  public static final class Prepared {

    private final Decision decision;
    private final String fileToUpload;
    private final String reason;

    private Prepared(Decision decision, String fileToUpload, String reason) {
      this.decision = decision;
      this.fileToUpload = fileToUpload;
      this.reason = reason;
    }

    public Decision getDecision() {
      return decision;
    }

    /** 실제로 업로드할 파일 경로. 거절된 경우 {@code null}. */
    public String getFileToUpload() {
      return fileToUpload;
    }

    /** 거절 사유. 거절이 아니면 {@code null}. */
    public String getReason() {
      return reason;
    }

    /** 보내면 안 되는 상태인가. */
    public boolean isRefused() {
      return decision == Decision.REFUSED;
    }

    /** 실릴 것이 암호문인가. */
    public boolean isEncrypted() {
      return decision == Decision.ENCRYPTED;
    }
  }

  private final boolean encryptEnabled;
  private final String publicKey;
  private final Encryptor encryptor;

  public FtpEncryptionGate(boolean encryptEnabled, String publicKey) {
    this(encryptEnabled, publicKey, RsaFileEncryption::encryptFile);
  }

  public FtpEncryptionGate(boolean encryptEnabled, String publicKey, Encryptor encryptor) {
    this.encryptEnabled = encryptEnabled;
    this.publicKey = (publicKey == null) ? "" : publicKey;
    this.encryptor = encryptor;
  }

  /** 암호화가 요구된 상태인가. 호출부가 실패를 어떻게 보고할지 고를 때 쓴다. */
  public boolean isEncryptRequired() {
    return encryptEnabled;
  }

  /**
   * 전송할 파일을 정한다.
   *
   * @param filePath 보내려는 파일 경로 (평문이거나, 보류 큐에서 꺼낸 암호문)
   * @return 판정 결과. {@link Prepared#isRefused()} 면 업로드를 호출하지 말 것.
   */
  public Prepared prepare(String filePath) {
    return prepare(filePath, (filePath == null) ? null : filePath + ENCRYPTED_SUFFIX);
  }

  /**
   * 전송할 파일을 정하되, 암호문을 <b>지정한 자리</b>에 만든다.
   *
   * <p>보류분을 재전송할 때 쓴다. 암호문을 원본 옆에 만들면 그것이 보류 디렉터리 안에 떨어져, 정리 전에 프로세스가 죽으면 다음 회차의 목록에 짐인 척 섞인다. 그래서
   * 재전송 경로는 큐 밖의 스테이징 자리를 넘긴다.
   *
   * @param filePath 보내려는 파일 경로
   * @param encryptedTarget 암호문을 만들 자리. 상위 디렉터리는 여기서 만든다
   * @return 판정 결과. {@link Prepared#isRefused()} 면 업로드를 호출하지 말 것.
   */
  public Prepared prepare(String filePath, String encryptedTarget) {
    if (filePath == null || filePath.isEmpty()) {
      return new Prepared(Decision.REFUSED, null, "보낼 파일 경로가 없습니다.");
    }

    // 보류 큐에서 꺼낸 암호문은 다시 암호화하지 않는다. 두 번 씌우면 수신측이 풀지 못한다.
    if (filePath.endsWith(ENCRYPTED_SUFFIX)) {
      return new Prepared(Decision.ENCRYPTED, filePath, null);
    }

    if (!encryptEnabled) {
      logger.info("FTP 암호화가 꺼져 있어 평문으로 전송합니다: {}", new File(filePath).getName());
      return new Prepared(Decision.PLAINTEXT, filePath, null);
    }

    if (publicKey.isEmpty()) {
      String reason = "FTP 암호화가 켜져 있으나 Public Key가 없어 전송하지 않았습니다.";
      logger.error("{} 평문 강등은 하지 않습니다 - 파일: {}", reason, new File(filePath).getName());
      return new Prepared(Decision.REFUSED, null, reason);
    }

    String encryptedFilePath =
        (encryptedTarget == null || encryptedTarget.isEmpty())
            ? filePath + ENCRYPTED_SUFFIX
            : encryptedTarget;

    boolean encrypted;
    try {
      // 스테이징처럼 아직 없는 자리를 받을 수 있다. 없으면 암호화가 파일을 못 열고 실패하는데,
      // 그 실패는 곧 거절이라 보낼 수 있는 것을 못 보내게 된다.
      File parent = new File(encryptedFilePath).getParentFile();
      if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
        logger.error("암호문을 둘 디렉터리를 만들 수 없습니다: {}", parent.getAbsolutePath());
      }
      encrypted = encryptor.encrypt(filePath, encryptedFilePath, publicKey);
    } catch (Exception e) {
      logger.error("FTP 전송용 암호화 중 오류: {}", e.getMessage(), e);
      encrypted = false;
    }

    if (!encrypted) {
      // 실패 시 남은 부분 산출물은 지운다. 그대로 두면 보류 큐에 깨진 암호문이 실릴 수 있다.
      discardPartial(encryptedFilePath);
      String reason = "FTP 전송용 파일 암호화에 실패해 전송하지 않았습니다.";
      logger.error("{} 평문 강등은 하지 않습니다 - 파일: {}", reason, new File(filePath).getName());
      return new Prepared(Decision.REFUSED, null, reason);
    }

    return new Prepared(Decision.ENCRYPTED, encryptedFilePath, null);
  }

  private void discardPartial(String encryptedFilePath) {
    File partial = new File(encryptedFilePath);
    if (!partial.isFile()) {
      return;
    }
    if (partial.delete()) {
      logger.warn("암호화 실패로 남은 부분 산출물을 지웠습니다: {}", partial.getName());
    } else {
      logger.error("암호화 실패로 남은 부분 산출물을 지우지 못했습니다: {}", partial.getAbsolutePath());
    }
  }
}

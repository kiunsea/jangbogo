package com.jiniebox.jangbogo.svc.util;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * FTP 전송 실패분 보류 큐.
 *
 * <p>수집 후 jiniebox 로 보내는 파일은 <b>증분</b>이다({@code exportToJinieboxFileBySeqList} 가 그 회차 신규 주문만 담는다).
 * 예전에는 업로드 실패 시 {@code finally} 가 파일을 지워 버려서 그 회차 주문이 수신측에 영원히 도달하지 못했고, 도달하지 못했다는 사실조차 어디에도 남지
 * 않았다. 주문 자체는 {@code jbg_order}/{@code jbg_item} 에 남지만 <b>배송 상태가 없어</b> 무엇이 미도달인지 알 방법이 없었다.
 *
 * <p>이 클래스는 실패분을 {@code {savePath}/pending/} 에 보관하고 다음 전송 회차에 오래된 것부터 재시도한다.
 *
 * <p>설계상 유의점:
 *
 * <ul>
 *   <li><b>파일명을 바꾸지 않는다.</b> {@code FtpUploadUtil} 이 원격 파일명을 {@code localFile.getName()} 으로 쓰므로 이름을
 *       바꾸면 수신측에 다른 이름으로 올라간다. 이름이 충돌할 때만 {@code _r1} 을 끼워 넣되 접두사와 확장자는 보존한다.
 *   <li><b>첫 실패에서 중단한다.</b> FTP 가 죽어 있으면 파일마다 연결 타임아웃(15초)을 물어, 보류가 쌓여 있을수록 수집 사이클을 오래 붙잡는다. 어차피 같은
 *       서버라 첫 건이 실패하면 나머지도 실패한다.
 *   <li><b>폐기는 반드시 경고로 남긴다.</b> 상한 초과로 버리는 것을 조용히 하면 지금 고치려는 결함과 같은 종류가 된다.
 * </ul>
 *
 * <p>상태 파일({@code jangbogo_status_*})은 이 큐에 넣지 않는다. "신규 없음" 하트비트라 뒤늦게 보내면 수신측 시각을 오도한다. 넣고 빼는 판단은
 * 호출부가 한다.
 */
public class FtpPendingQueue {

  private static final Logger logger = LogManager.getLogger(FtpPendingQueue.class);

  /** 보류 디렉터리 이름 ({@code savePath} 하위). */
  public static final String DIR_NAME = "pending";

  /** 기본 보류 건수 상한. */
  public static final int DEFAULT_MAX_FILES = 50;

  /** 기본 보관 기간 상한 (14일). 720분 주기·2몰 기준 약 56회차분이다. */
  public static final long DEFAULT_MAX_AGE_MILLIS = 14L * 24 * 60 * 60 * 1000;

  /** 업로드 수행자. FTP 서버 없이 단위테스트할 수 있도록 주입한다. */
  public interface Uploader {
    /**
     * @param file 업로드할 파일
     * @return 성공 여부
     */
    boolean upload(File file);
  }

  private final File dir;
  private final int maxFiles;
  private final long maxAgeMillis;

  public FtpPendingQueue(String savePath) {
    this(savePath, DEFAULT_MAX_FILES, DEFAULT_MAX_AGE_MILLIS);
  }

  public FtpPendingQueue(String savePath, int maxFiles, long maxAgeMillis) {
    this.dir = new File(savePath, DIR_NAME);
    this.maxFiles = maxFiles;
    this.maxAgeMillis = maxAgeMillis;
  }

  /** 보류 디렉터리. 아직 만들어지지 않았을 수 있다. */
  public File getDirectory() {
    return dir;
  }

  /** 현재 보류 건수. */
  public int size() {
    return listPending().size();
  }

  /**
   * 업로드에 실패한 파일을 보류 큐로 옮긴다.
   *
   * <p>이동에 실패하면 원본을 그 자리에 남긴다. 지우는 것보다 낫기 때문이다.
   *
   * @param file 업로드에 실패한 파일
   * @return 적재 성공 여부
   */
  public boolean enqueue(File file) {
    if (file == null || !file.isFile()) {
      return false;
    }

    try {
      if (!dir.isDirectory() && !dir.mkdirs()) {
        logger.error("보류 큐 디렉터리를 만들 수 없습니다: {} - 실패분을 원위치에 남깁니다", dir.getAbsolutePath());
        return false;
      }

      File target = resolveTarget(file.getName());
      Files.move(file.toPath(), target.toPath());
      logger.warn("FTP 업로드 실패분을 보류 큐에 적재했습니다: {} (보류 {}건)", target.getName(), size());

      enforceLimits();
      return true;
    } catch (Exception e) {
      logger.error("보류 큐 적재 실패: {} - {} (실패분을 원위치에 남깁니다)", file.getName(), e.getMessage());
      return false;
    }
  }

  /**
   * 보류분을 오래된 것부터 재전송한다. 첫 실패에서 중단하고 나머지는 다음 회차로 미룬다.
   *
   * @param uploader 업로드 수행자
   * @return 재전송에 성공한 건수
   */
  public int drain(Uploader uploader) {
    enforceLimits();

    List<File> files = listPending();
    if (files.isEmpty()) {
      return 0;
    }

    logger.info("FTP 보류분 재전송 시작 - {}건", files.size());

    int sent = 0;
    for (File file : files) {
      boolean uploaded;
      try {
        uploaded = uploader.upload(file);
      } catch (Exception e) {
        logger.warn("보류분 재전송 중 오류: {} - {}", file.getName(), e.getMessage());
        uploaded = false;
      }

      if (!uploaded) {
        logger.warn(
            "보류분 재전송 실패: {} - 여기서 중단합니다 (남은 {}건은 다음 회차에 재시도)", file.getName(), files.size() - sent);
        break;
      }

      if (!file.delete()) {
        logger.warn("재전송에 성공했으나 보류 파일 삭제에 실패했습니다: {}", file.getAbsolutePath());
      }
      sent++;
    }

    if (sent > 0) {
      logger.info("FTP 보류분 재전송 완료 - {}건 전송, {}건 남음", sent, size());
    }
    return sent;
  }

  /** 보류 목록을 오래된 순(파일명 오름차순 = 시각 오름차순)으로 반환한다. */
  private List<File> listPending() {
    File[] found = dir.listFiles(File::isFile);
    if (found == null || found.length == 0) {
      return new ArrayList<>();
    }
    List<File> files = new ArrayList<>(Arrays.asList(found));
    files.sort(Comparator.comparing(File::getName));
    return files;
  }

  /** 보관 기간·건수 상한을 적용한다. 초과분은 오래된 것부터 폐기하고 경고를 남긴다. */
  private void enforceLimits() {
    List<File> files = listPending();
    if (files.isEmpty()) {
      return;
    }

    long cutoff = System.currentTimeMillis() - maxAgeMillis;
    List<File> remaining = new ArrayList<>();
    for (File file : files) {
      if (file.lastModified() < cutoff) {
        discard(file, "보관기간 " + (maxAgeMillis / (24 * 60 * 60 * 1000L)) + "일 초과");
      } else {
        remaining.add(file);
      }
    }

    int excess = remaining.size() - maxFiles;
    for (int i = 0; i < excess; i++) {
      discard(remaining.get(i), "보류 건수 상한 " + maxFiles + "건 초과");
    }
  }

  private void discard(File file, String reason) {
    if (file.delete()) {
      logger.warn("보류분 폐기: {} ({}) - 이 파일의 내용은 수신측에 전달되지 않습니다", file.getName(), reason);
    } else {
      logger.error("보류분 폐기 실패: {} ({})", file.getAbsolutePath(), reason);
    }
  }

  /**
   * 보류 디렉터리에서 쓸 수 있는 대상 파일을 고른다.
   *
   * <p>원격 파일명이 로컬 파일명 그대로 쓰이므로 이름을 보존한다. 같은 이름이 이미 있으면 접두사와 확장자는 유지한 채 {@code _r1}, {@code _r2} 를
   * 끼워 넣는다. 예: {@code jangbogo_orders_20260729_000215_ftp.json.encrypted} → {@code
   * jangbogo_orders_20260729_000215_ftp_r1.json.encrypted}
   */
  private File resolveTarget(String name) {
    File candidate = new File(dir, name);
    if (!candidate.exists()) {
      return candidate;
    }

    int dot = name.indexOf('.');
    String base = (dot < 0) ? name : name.substring(0, dot);
    String ext = (dot < 0) ? "" : name.substring(dot);

    for (int i = 1; i < 1000; i++) {
      File alternate = new File(dir, base + "_r" + i + ext);
      if (!alternate.exists()) {
        return alternate;
      }
    }
    return new File(dir, base + "_r" + System.nanoTime() + ext);
  }
}

package com.jiniebox.jangbogo.svc.util;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
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

  /**
   * 재전송용 임시 산출물을 두는 곳 ({@code pending} 하위).
   *
   * <p>재전송할 때 암호화를 새로 하면 암호문 임시본이 생긴다. 예전에는 그것을 원본 옆, 즉 <b>보류 디렉터리 안에</b> 만들었다. 업로드와 정리 사이에 프로세스가
   * 죽으면 임시본이 그대로 남고, 다음 회차의 목록에 {@code X.json} 과 {@code X.json.encrypted} 가 함께 잡힌다. 앞의 것을 처리하면서 같은
   * 이름의 임시본을 덮어썼다가 성공 후 지우므로, 뒤의 항목은 <b>방금 사라진 파일</b>이 된다 — 업로드가 실패하고 {@link #drain} 이 거기서 회차를 끊는다.
   * 보류가 쌓여 있을수록 한 회차에 한 건씩만 나가게 된다.
   *
   * <p>그래서 임시본을 목록 밖으로 뺀다. 디렉터리라 파일 목록에 걸리지 않고, 회차 시작마다 비우므로 지난 회차가 남긴 것은 그 자리에서 정리된다.
   */
  static final String STAGING_DIR_NAME = ".staging";

  /**
   * 재전송 락 파일 이름 ({@code pending} 하위).
   *
   * <p>보류분을 훑는 주체가 둘이다 — 스케줄 수집과 자동 수집(대시보드 실행). 같은 디렉터리를 잠금 없이 훑으면 두 쪽이 같은 목록을 얻어 같은 증분을 각각 올린다.
   * 수신측 중복 검사가 걸러내기는 하나 그건 완화지 방어가 아니고, 뒤늦은 {@code delete} 는 이미 사라진 파일에 대해 헛경고를 남긴다.
   *
   * <p>파일 락이라 프로세스 경계도 넘는다 — 서비스로 도는 인스턴스와 사람이 직접 띄운 인스턴스가 같은 {@code savePath} 를 볼 수 있다.
   */
  static final String LOCK_FILE_NAME = ".drain.lock";

  /** 기본 보류 건수 상한. */
  public static final int DEFAULT_MAX_FILES = 50;

  /** 기본 보관 기간 상한 (14일). 720분 주기·2몰 기준 약 56회차분이다. */
  public static final long DEFAULT_MAX_AGE_MILLIS = 14L * 24 * 60 * 60 * 1000;

  /** 적재 시 이름이 겹쳤을 때 다시 고를 횟수. */
  private static final int ENQUEUE_RENAME_ATTEMPTS = 5;

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

  /**
   * 재전송용 임시 산출물을 둘 곳. 경로만 돌려준다 — 만드는 것은 쓰는 쪽이다.
   *
   * <p>여기 있는 것은 <b>사본</b>이다. 원본은 보류 디렉터리에 그대로 있으므로, 이 안의 파일은 언제 지워도 잃는 것이 없다.
   */
  public File getStagingDirectory() {
    return new File(dir, STAGING_DIR_NAME);
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

      // 이름을 고르고 옮기는 사이에 다른 회차가 같은 이름을 차지할 수 있다(자동 수집과 스케줄
      // 수집이 동시에 실패분을 넣는 경우). 그때 move 는 FileAlreadyExists 로 터지는데, 예전에는
      // 그것이 곧 "원위치에 남깁니다" 였다 — 파일은 살지만 큐에 들어가지 못해 재시도되지 않는다.
      // 이름만 다시 고르면 되는 일이므로 몇 번 다시 시도한다.
      for (int attempt = 1; ; attempt++) {
        File target = resolveTarget(file.getName());
        try {
          Files.move(file.toPath(), target.toPath());
          logger.warn("FTP 업로드 실패분을 보류 큐에 적재했습니다: {} (보류 {}건)", target.getName(), size());
          enforceLimits();
          return true;
        } catch (FileAlreadyExistsException raced) {
          if (attempt >= ENQUEUE_RENAME_ATTEMPTS) {
            throw raced;
          }
          logger.info("보류 큐 이름이 겹쳤습니다: {} - 다시 고릅니다", target.getName());
        }
      }
    } catch (Exception e) {
      logger.error("보류 큐 적재 실패: {} - {} (실패분을 원위치에 남깁니다)", file.getName(), e.getMessage());
      return false;
    }
  }

  /**
   * 보류분을 오래된 것부터 재전송한다. 첫 실패에서 중단하고 나머지는 다음 회차로 미룬다.
   *
   * <p>같은 디렉터리를 훑는 회차가 하나뿐이도록 잠근다. 이미 다른 회차가 돌고 있으면 <b>기다리지 않고 물러난다</b> — 어차피 그쪽이 같은 목록을 보내고 있고,
   * 기다리면 HTTP 요청 스레드가 FTP 타임아웃만큼 붙잡힌다.
   *
   * @param uploader 업로드 수행자
   * @return 재전송에 성공한 건수
   */
  public int drain(Uploader uploader) {
    // 보류가 없으면 락 파일도 만들지 않는다. 쓰지도 않을 파일을 savePath 에 남기지 않기 위해서다.
    if (!dir.isDirectory()) {
      return 0;
    }

    try (DrainLock lock = DrainLock.tryAcquire(new File(dir, LOCK_FILE_NAME))) {
      if (!lock.canProceed()) {
        logger.info("다른 회차가 이미 보류분을 재전송 중입니다. 이번 회차는 건너뜁니다.");
        return 0;
      }
      return drainExclusively(uploader);
    }
  }

  /** 락을 쥔 상태의 재전송 본문. */
  private int drainExclusively(Uploader uploader) {
    // 지난 회차가 남긴 임시본을 먼저 치운다. 원본은 보류 디렉터리에 있으므로 잃는 것이 없다.
    purgeStaging();
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

  /**
   * 보류 목록을 오래된 순(파일명 오름차순 = 시각 오름차순)으로 반환한다.
   *
   * <p>점으로 시작하는 이름은 <b>큐 자신의 살림</b>이지 전송할 짐이 아니다({@code .drain.lock}, {@code .staging}). 내보내기 파일명은
   * {@code jangbogo_orders_}·{@code jangbogo_status_}·{@code purchase_} 로 시작하므로 이 규칙에 걸리지 않는다. 걸러내지
   * 않으면 락 파일을 수신측에 올리려 든다.
   */
  private List<File> listPending() {
    File[] found = dir.listFiles(f -> f.isFile() && !f.getName().startsWith("."));
    if (found == null || found.length == 0) {
      return new ArrayList<>();
    }
    List<File> files = new ArrayList<>(Arrays.asList(found));
    files.sort(Comparator.comparing(File::getName));
    return files;
  }

  /**
   * 재전송용 임시 산출물을 모두 지운다.
   *
   * <p>여기 남아 있다는 것은 지난 회차가 업로드와 정리 사이에서 죽었다는 뜻이다. 조용히 지우지 않는다 — 회차가 비정상 종료했다는 사실 자체가 진단 재료다.
   */
  private void purgeStaging() {
    File[] leftovers = getStagingDirectory().listFiles(File::isFile);
    if (leftovers == null || leftovers.length == 0) {
      return;
    }

    int removed = 0;
    for (File leftover : leftovers) {
      if (leftover.delete()) {
        removed++;
      } else {
        logger.warn("재전송 임시본을 지우지 못했습니다: {}", leftover.getAbsolutePath());
      }
    }
    logger.warn("지난 회차가 남긴 재전송 임시본 {}건을 정리했습니다 - 그 회차는 정리 전에 끝난 것으로 보입니다", removed);
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

  /**
   * 재전송 회차를 하나로 묶는 락.
   *
   * <p>{@code MallProfileLock} 과 장치는 같지만 그쪽을 쓰지 않는다. 그 클래스는 실패할 때 <b>"프로필 …이 다른 프로세스에 잡혀 있다"</b> 로
   * 남기는데, FTP 보류 큐에는 프로필이라는 것이 없어 로그를 읽는 사람을 엉뚱한 데로 보낸다. 이 저장소가 반복해서 데인 것이 바로 "로그에 답이 적혀 있는데 다른 것을
   * 가리키고 있었다" 이다.
   *
   * <p>세 상태를 두 상태로 접는다 — 잠글 수 없는 환경(네트워크 드라이브 등)은 <b>진행</b>이다. 막을 근거가 없는데 멈추면 보류분이 영영 나가지 못한다.
   */
  private static final class DrainLock implements AutoCloseable {

    private final FileChannel channel;
    private final FileLock lock;
    private final boolean heldByOther;

    private DrainLock(FileChannel channel, FileLock lock, boolean heldByOther) {
      this.channel = channel;
      this.lock = lock;
      this.heldByOther = heldByOther;
    }

    static DrainLock tryAcquire(File lockFile) {
      FileChannel channel = null;
      try {
        channel =
            FileChannel.open(
                lockFile.toPath(),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.READ);

        FileLock acquired = channel.tryLock();
        if (acquired == null) {
          closeQuietly(channel);
          return new DrainLock(null, null, true);
        }
        return new DrainLock(channel, acquired, false);

      } catch (OverlappingFileLockException sameJvm) {
        // 같은 JVM 의 다른 스레드가 이미 쥐고 있다. 남이 쥔 것과 결과는 같다.
        closeQuietly(channel);
        return new DrainLock(null, null, true);

      } catch (IOException | RuntimeException e) {
        // 파일 락을 지원하지 않는 위치일 수 있다. 잠그지 못한다고 재전송을 멈출 이유는 없다.
        closeQuietly(channel);
        logger.warn("보류 큐 락을 걸 수 없습니다({}). 경고만 남기고 진행합니다.", e.getClass().getSimpleName());
        return new DrainLock(null, null, false);
      }
    }

    boolean canProceed() {
      return !heldByOther;
    }

    @Override
    public void close() {
      // 락 파일 자체는 지우지 않는다. 지우면 다른 프로세스가 이미 연 핸들과 새로 만든 파일이
      // 갈라져 락이 무의미해진다. 빈 파일 하나가 남는 비용이 그 위험보다 싸다.
      if (lock != null) {
        try {
          lock.release();
        } catch (IOException e) {
          logger.warn("보류 큐 락 해제 실패: {}", e.getMessage());
        }
      }
      closeQuietly(channel);
    }

    private static void closeQuietly(FileChannel channel) {
      if (channel == null) {
        return;
      }
      try {
        channel.close();
      } catch (IOException ignore) {
        // 닫지 못해도 할 수 있는 것이 없다.
      }
    }
  }
}

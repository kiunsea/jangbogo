package com.jiniebox.jangbogo.svc.util;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 프로필 단위 락 (Phase 5-3).
 *
 * <h2>왜 필요한가</h2>
 *
 * <p>Chrome 은 같은 사용자 프로필을 두 프로세스가 동시에 열면 프로필을 망가뜨리거나 한쪽이 조용히 다른 디렉터리로 떨어진다. 사람이 로그인해 둔 세션이 거기 들어
 * 있으므로 망가지면 다시 로그인해야 한다.
 *
 * <p>기존 코드에도 동시 실행 방지가 있었지만 {@code ConcurrentHashMap} 기반이라 <b>JVM 안에서만</b> 유효했다. 서비스로 도는 인스턴스와 사용자가
 * 직접 띄운 인스턴스는 다른 프로세스라 그 장치로는 막히지 않는다. 파일 락이라야 프로세스 경계를 넘는다.
 *
 * <h2>규칙</h2>
 *
 * <ul>
 *   <li><b>비차단이다.</b> {@code tryLock} 이 실패하면 기다리지 않고 즉시 물러난다 — 수집은 나중에 다시 돌면 되지만, 멈춰 선 스케줄러는 다음
 *       회차까지 통째로 막는다.
 *   <li><b>키는 몰 seq 가 아니라 프로필 이름이다.</b> 두 몰이 같은 프로필을 공유할 수 있다(같은 계정을 쓰는 몰이 실재한다). seq 로 잠그면 그 경우를
 *       놓친다.
 *   <li><b>락 파일은 지우지 않는다.</b> 지우는 순간 다른 프로세스가 이미 연 파일 핸들과 새로 만든 파일이 갈라져 락이 무의미해진다. 빈 파일 하나가 남는 비용이
 *       그 위험보다 싸다.
 *   <li><b>{@code BrowserConcurrencyLimiter} 와 함께 쓸 때는 이 락이 안쪽이다.</b> 획득 순서는 제한기 바깥 / 프로필 락 안쪽 — 락을
 *       바깥에 두면 세마포어 대기 중 프로세스 경계 락을 놀리면서 쥔다. 아직 수집 경로에 배선하지 않았다(Phase 5-3 재검토 항목). 배선하는 쪽이 이 순서를
 *       지켜야 한다.
 * </ul>
 *
 * <p>세 상태를 구분한다 — 잡았다 / 남이 갖고 있다 / 잠글 수 없는 환경이다. 마지막은 실패가 아니다(네트워크 드라이브 등에서 파일 락이 지원되지 않을 수 있다).
 * 호출측이 정책을 정하도록 구분해서 돌려준다.
 *
 * <p>브라우저·네트워크·DB 를 쓰지 않는다. {@link AutoCloseable} 이므로 try-with-resources 로 쓴다.
 *
 * @author KIUNSEA
 */
public final class MallProfileLock implements AutoCloseable {

  private static final Logger logger = LogManager.getLogger(MallProfileLock.class);

  /** 락 파일이 모이는 디렉터리 이름. {@code .gitignore} 에 등재돼 있다. */
  static final String LOCK_DIR = ".locks";

  /** 획득 결과. */
  public enum Outcome {
    /** 이 프로세스가 락을 잡았다. */
    ACQUIRED,
    /** 다른 프로세스가 이미 갖고 있다. 이번 회차는 건너뛴다. */
    HELD_BY_OTHER,
    /** 잠글 수 없는 환경이다. 경고하고 진행한다 — 막을 근거가 없으므로 멈추지도 않는다. */
    UNAVAILABLE
  }

  private final Outcome outcome;
  private final String profileName;
  private final FileChannel channel;
  private final FileLock lock;

  private MallProfileLock(Outcome outcome, String profileName, FileChannel channel, FileLock lock) {
    this.outcome = outcome;
    this.profileName = profileName;
    this.channel = channel;
    this.lock = lock;
  }

  /**
   * 프로필 락을 시도한다. 기다리지 않는다.
   *
   * @param lockRoot 락 파일을 둘 루트 (보통 프로필 루트)
   * @param profileName 프로필 이름 — 이것이 곧 락 키다
   * @return 결과를 담은 락 객체. 반드시 close 할 것
   */
  public static MallProfileLock tryAcquire(Path lockRoot, String profileName) {
    if (profileName == null || profileName.isBlank()) {
      throw new IllegalArgumentException("프로필 이름이 비었다 — 락 키가 없다.");
    }
    String name = profileName.trim();
    Path lockFile = lockRoot.resolve(LOCK_DIR).resolve(name + ".lock");

    FileChannel channel = null;
    try {
      Files.createDirectories(lockFile.getParent());
      channel =
          FileChannel.open(
              lockFile,
              StandardOpenOption.CREATE,
              StandardOpenOption.WRITE,
              StandardOpenOption.READ);

      FileLock acquired = channel.tryLock();
      if (acquired == null) {
        // 다른 프로세스가 갖고 있다.
        closeQuietly(channel);
        logger.info("프로필 '{}' 이 다른 프로세스에 잡혀 있다. 이번 회차는 건너뛴다.", name);
        return new MallProfileLock(Outcome.HELD_BY_OTHER, name, null, null);
      }
      return new MallProfileLock(Outcome.ACQUIRED, name, channel, acquired);

    } catch (OverlappingFileLockException e) {
      // 같은 JVM 안에서 이미 잡고 있다. 남이 갖고 있는 것과 결과는 같다.
      closeQuietly(channel);
      logger.info("프로필 '{}' 을 같은 프로세스가 이미 잡고 있다. 건너뛴다.", name);
      return new MallProfileLock(Outcome.HELD_BY_OTHER, name, null, null);

    } catch (IOException | RuntimeException e) {
      // 파일 락이 지원되지 않는 위치일 수 있다. 못 잠근다고 수집을 멈출 이유는 없다 —
      // 다만 조용히 넘기지는 않는다.
      closeQuietly(channel);
      logger.warn("프로필 '{}' 락을 걸 수 없다({}). 경고만 남기고 진행한다.", name, e.getClass().getSimpleName());
      return new MallProfileLock(Outcome.UNAVAILABLE, name, null, null);
    }
  }

  /** 획득 결과. */
  public Outcome outcome() {
    return outcome;
  }

  /** 이번 회차를 진행해도 되는지. {@code HELD_BY_OTHER} 일 때만 false 다. */
  public boolean canProceed() {
    return outcome != Outcome.HELD_BY_OTHER;
  }

  /** 락 키로 쓰인 프로필 이름. */
  public String profileName() {
    return profileName;
  }

  @Override
  public void close() {
    // 락 파일 자체는 지우지 않는다 (위 주석 참조). 핸들만 놓는다.
    if (lock != null) {
      try {
        lock.release();
      } catch (IOException e) {
        logger.warn("프로필 '{}' 락 해제 실패: {}", profileName, e.getMessage());
      }
    }
    closeQuietly(channel);
  }

  private static void closeQuietly(FileChannel channel) {
    if (channel != null) {
      try {
        channel.close();
      } catch (IOException ignore) {
        // 닫기 실패는 알릴 것이 없다
      }
    }
  }
}

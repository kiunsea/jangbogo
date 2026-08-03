package com.jiniebox.jangbogo.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jiniebox.jangbogo.svc.util.MallProfileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 프로필 단위 락 검증 (Phase 5-3).
 *
 * <p>Chrome 은 같은 프로필을 두 프로세스가 열면 프로필을 망가뜨린다. 그 안에 사람이 로그인해 둔 세션이 들어 있으므로 망가지면 다시 로그인해야 한다.
 *
 * <p>기존 동시 실행 방지는 {@code ConcurrentHashMap} 기반이라 <b>JVM 안에서만</b> 유효했다 — 서비스 인스턴스와 사용자가 직접 띄운 인스턴스는
 * 다른 프로세스라 막히지 않았다. 파일 락이라야 그 경계를 넘는다.
 *
 * <p>브라우저·네트워크·DB 를 쓰지 않는다. 임시 디렉터리에서 실제 파일 락을 건다.
 *
 * @author KIUNSEA
 */
class MallProfileLockTest {

  @Test
  @DisplayName("비어 있는 프로필에는 락을 잡는다")
  void acquiresOnAFreeProfile(@TempDir Path root) {
    try (MallProfileLock lock = MallProfileLock.tryAcquire(root, "ssg")) {
      assertEquals(MallProfileLock.Outcome.ACQUIRED, lock.outcome());
      assertTrue(lock.canProceed());
      assertEquals("ssg", lock.profileName());
    }
  }

  @Test
  @DisplayName("이미 잡힌 프로필은 기다리지 않고 물러난다")
  void doesNotWaitWhenAlreadyHeld(@TempDir Path root) {
    // 기다리면 스케줄러가 다음 회차까지 통째로 막힌다. 수집은 나중에 다시 돌면 된다.
    try (MallProfileLock first = MallProfileLock.tryAcquire(root, "ssg")) {
      assertEquals(MallProfileLock.Outcome.ACQUIRED, first.outcome());

      try (MallProfileLock second = MallProfileLock.tryAcquire(root, "ssg")) {
        assertEquals(MallProfileLock.Outcome.HELD_BY_OTHER, second.outcome());
        assertFalse(second.canProceed(), "잡히지 않았는데 진행 가능으로 판정했다.");
      }
    }
  }

  @Test
  @DisplayName("놓으면 다시 잡을 수 있다")
  void releasesOnClose(@TempDir Path root) {
    try (MallProfileLock first = MallProfileLock.tryAcquire(root, "ssg")) {
      assertEquals(MallProfileLock.Outcome.ACQUIRED, first.outcome());
    }
    try (MallProfileLock again = MallProfileLock.tryAcquire(root, "ssg")) {
      assertEquals(MallProfileLock.Outcome.ACQUIRED, again.outcome(), "해제 후에도 잠겨 있다.");
    }
  }

  @Test
  @DisplayName("프로필이 다르면 서로 막지 않는다")
  void differentProfilesDoNotBlockEachOther(@TempDir Path root) {
    try (MallProfileLock ssg = MallProfileLock.tryAcquire(root, "ssg");
        MallProfileLock oasis = MallProfileLock.tryAcquire(root, "oasis")) {
      assertEquals(MallProfileLock.Outcome.ACQUIRED, ssg.outcome());
      assertEquals(MallProfileLock.Outcome.ACQUIRED, oasis.outcome());
    }
  }

  @Test
  @DisplayName("같은 프로필을 공유하는 몰은 서로 막는다 — 키는 몰이 아니라 프로필이다")
  void mallsSharingAProfileBlockEachOther(@TempDir Path root) {
    // ssg 와 emart 는 같은 계정을 쓴다. seq 로 잠그면 이 경우를 놓친다.
    try (MallProfileLock ssg = MallProfileLock.tryAcquire(root, "shinsegae");
        MallProfileLock emart = MallProfileLock.tryAcquire(root, "shinsegae")) {
      assertEquals(MallProfileLock.Outcome.ACQUIRED, ssg.outcome());
      assertEquals(MallProfileLock.Outcome.HELD_BY_OTHER, emart.outcome());
    }
  }

  @Test
  @DisplayName("락 파일은 해제 후에도 지우지 않는다")
  void keepsTheLockFileAfterRelease(@TempDir Path root) {
    // 지우면 다른 프로세스가 이미 연 핸들과 새로 만든 파일이 갈라져 락이 무의미해진다.
    try (MallProfileLock lock = MallProfileLock.tryAcquire(root, "ssg")) {
      assertEquals(MallProfileLock.Outcome.ACQUIRED, lock.outcome());
    }
    assertTrue(
        Files.isRegularFile(root.resolve(".locks").resolve("ssg.lock")),
        "락 파일이 사라졌다 — 다음 획득이 다른 파일을 잡게 된다.");
  }

  @Test
  @DisplayName("락 디렉터리는 gitignore 대상 이름을 쓴다")
  void usesTheIgnoredLockDirectory(@TempDir Path root) {
    try (MallProfileLock lock = MallProfileLock.tryAcquire(root, "ssg")) {
      assertEquals(MallProfileLock.Outcome.ACQUIRED, lock.outcome());
    }
    assertTrue(
        Files.isDirectory(root.resolve(".locks")), ".locks 디렉터리를 쓰지 않는다 — .gitignore 규칙과 어긋난다.");
  }

  @Test
  @DisplayName("프로필 이름이 없으면 거부한다 — 락 키가 없다는 뜻이다")
  void rejectsAMissingProfileName(@TempDir Path root) {
    for (String bad : new String[] {null, "", "   "}) {
      assertThrows(
          IllegalArgumentException.class,
          () -> MallProfileLock.tryAcquire(root, bad),
          "빈 이름이 통과했다: " + bad);
    }
  }

  @Test
  @DisplayName("잠글 수 없는 위치는 실패가 아니라 UNAVAILABLE 이다")
  void unlockablePathIsNotAFailure(@TempDir Path root) throws Exception {
    // 파일 락이 지원되지 않는 위치가 있을 수 있다(네트워크 드라이브 등).
    // 못 잠근다고 수집을 멈출 이유는 없다 — 다만 조용히 넘기지도 않는다.
    //
    // 락 파일 자리에 디렉터리를 만들어 FileChannel.open 이 실패하게 한다.
    Files.createDirectories(root.resolve(".locks").resolve("ssg.lock"));

    try (MallProfileLock lock = MallProfileLock.tryAcquire(root, "ssg")) {
      assertEquals(MallProfileLock.Outcome.UNAVAILABLE, lock.outcome());
      assertTrue(lock.canProceed(), "잠글 수 없다고 수집을 막으면 안 된다.");
    }
  }
}

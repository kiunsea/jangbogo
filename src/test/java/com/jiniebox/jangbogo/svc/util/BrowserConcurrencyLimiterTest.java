package com.jiniebox.jangbogo.svc.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * 브라우저 동시 실행 제한 검증 (Phase 5-11).
 *
 * <p>이 제한은 {@code MallProfileLock} 과 역할이 다르다 — 락은 <b>프로필 하나</b>를 <b>프로세스 사이</b>에서 지키고, 이 제한은
 * <b>브라우저 총 개수</b>를 <b>JVM 안</b>에서 묶는다. 프로필이 서로 달라도 Chrome 을 여럿 띄우면 메모리가 무너진다.
 *
 * <p>기다리는 정책도 반대다. 프로필 락은 상대가 다른 프로세스라 언제 끝날지 몰라 즉시 물러나지만, 이 제한은 상대가 같은 JVM 의 수집이고 몇 분이면 끝난다는 것을
 * 안다. 즉시 포기하면 그 몰은 다음 주기(12시간)까지 통째로 건너뛴다.
 *
 * <p>브라우저·네트워크·DB 를 쓰지 않는다. 짧은 타임아웃으로만 검증해 테스트가 느려지지 않게 한다.
 *
 * @author KIUNSEA
 */
class BrowserConcurrencyLimiterTest {

  @Test
  @DisplayName("자리가 있으면 잡는다")
  void acquiresWhenFree() {
    BrowserConcurrencyLimiter limiter = new BrowserConcurrencyLimiter(1);

    try (BrowserConcurrencyLimiter.Lease lease = limiter.acquire(0)) {
      assertTrue(lease.acquired());
      assertEquals(0, limiter.availablePermits());
    }
    assertEquals(1, limiter.availablePermits(), "닫았는데 자리가 돌아오지 않았다.");
  }

  @Test
  @DisplayName("자리가 없으면 대기 상한까지 기다렸다가 물러난다")
  @Timeout(10)
  void givesUpAfterTheTimeout() {
    BrowserConcurrencyLimiter limiter = new BrowserConcurrencyLimiter(1);

    try (BrowserConcurrencyLimiter.Lease held = limiter.acquire(0)) {
      assertTrue(held.acquired());

      long start = System.nanoTime();
      try (BrowserConcurrencyLimiter.Lease second = limiter.acquire(200)) {
        long waitedMs = (System.nanoTime() - start) / 1_000_000L;

        assertFalse(second.acquired(), "자리가 없는데 잡았다고 판정했다.");
        assertTrue(waitedMs >= 150, "대기 상한을 기다리지 않고 즉시 포기했다: " + waitedMs + "ms");
      }
    }
  }

  @Test
  @DisplayName("앞선 수집이 끝나면 기다리던 쪽이 자리를 받는다")
  @Timeout(10)
  void aWaiterGetsTheSlotWhenTheHolderFinishes() throws Exception {
    // 이것이 '기다린다'는 판단의 근거다. 즉시 포기했다면 그 몰은 다음 주기까지 수집하지 못한다.
    BrowserConcurrencyLimiter limiter = new BrowserConcurrencyLimiter(1);
    CountDownLatch holderAcquired = new CountDownLatch(1);
    CountDownLatch waiterDone = new CountDownLatch(1);
    AtomicBoolean waiterGotIt = new AtomicBoolean(false);

    Thread holder =
        new Thread(
            () -> {
              try (BrowserConcurrencyLimiter.Lease lease = limiter.acquire(0)) {
                holderAcquired.countDown();
                Thread.sleep(150); // 수집이 도는 중
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });

    Thread waiter =
        new Thread(
            () -> {
              try {
                holderAcquired.await(5, TimeUnit.SECONDS);
                try (BrowserConcurrencyLimiter.Lease lease = limiter.acquire(5_000)) {
                  waiterGotIt.set(lease.acquired());
                }
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              } finally {
                waiterDone.countDown();
              }
            });

    holder.start();
    waiter.start();
    assertTrue(waiterDone.await(8, TimeUnit.SECONDS), "대기 스레드가 끝나지 않았다.");
    holder.join(2_000);

    assertTrue(waiterGotIt.get(), "앞선 수집이 끝났는데도 자리를 받지 못했다.");
    assertEquals(1, limiter.availablePermits());
  }

  @Test
  @DisplayName("허용 수만큼은 동시에 잡힌다")
  void allowsAsManyAsConfigured() {
    BrowserConcurrencyLimiter limiter = new BrowserConcurrencyLimiter(2);

    try (BrowserConcurrencyLimiter.Lease a = limiter.acquire(0);
        BrowserConcurrencyLimiter.Lease b = limiter.acquire(0);
        BrowserConcurrencyLimiter.Lease c = limiter.acquire(0)) {
      assertTrue(a.acquired());
      assertTrue(b.acquired());
      assertFalse(c.acquired(), "허용 수를 넘겨 잡았다.");
    }
    assertEquals(2, limiter.availablePermits());
  }

  @Test
  @DisplayName("잡지 못한 임대권을 닫아도 자리가 늘지 않는다")
  void closingAFailedLeaseDoesNotCreateAPermit() {
    BrowserConcurrencyLimiter limiter = new BrowserConcurrencyLimiter(1);

    try (BrowserConcurrencyLimiter.Lease held = limiter.acquire(0)) {
      assertTrue(held.acquired());
      limiter.acquire(0).close(); // 실패한 임대권을 닫는다
      assertEquals(0, limiter.availablePermits(), "없던 자리가 생겼다.");
    }
    assertEquals(1, limiter.availablePermits());
  }

  @Test
  @DisplayName("같은 임대권을 두 번 닫아도 자리가 늘지 않는다")
  void closingTwiceIsSafe() {
    // try-with-resources 안에서 명시적으로 close 를 부르는 코드가 섞이면 실제로 두 번 닫힌다.
    BrowserConcurrencyLimiter limiter = new BrowserConcurrencyLimiter(1);

    BrowserConcurrencyLimiter.Lease lease = limiter.acquire(0);
    assertTrue(lease.acquired());
    lease.close();
    lease.close();

    assertEquals(1, limiter.availablePermits(), "두 번 닫아 자리가 늘었다.");
  }

  @Test
  @DisplayName("동시 실행 수는 1 미만일 수 없다")
  void rejectsNonPositivePermits() {
    for (int bad : new int[] {0, -1}) {
      assertThrows(IllegalArgumentException.class, () -> new BrowserConcurrencyLimiter(bad));
    }
  }

  @Test
  @DisplayName("설정 기본값 — 브라우저 1개, 대기 상한 300초")
  void defaultsAreConservative() {
    // 개인 PC 에서 돌고 Chrome 하나가 수백 MB 를 쓴다. 늘려야 할 이유가 생기기 전엔 늘리지 않는다.
    withProperty(
        BrowserConcurrencyLimiter.PERMITS_PROPERTY,
        null,
        () -> assertEquals(1, BrowserConcurrencyLimiter.configuredPermits()));
    withProperty(
        BrowserConcurrencyLimiter.TIMEOUT_PROPERTY,
        null,
        () -> assertEquals(300, BrowserConcurrencyLimiter.configuredTimeoutSeconds()));
  }

  @Test
  @DisplayName("설정은 덮어쓸 수 있고, 이상한 값은 기본값으로 떨어진다")
  void configurationIsOverridableAndDefensive() {
    withProperty(
        BrowserConcurrencyLimiter.PERMITS_PROPERTY,
        "3",
        () -> assertEquals(3, BrowserConcurrencyLimiter.configuredPermits()));
    withProperty(
        BrowserConcurrencyLimiter.PERMITS_PROPERTY,
        "0",
        () -> assertEquals(1, BrowserConcurrencyLimiter.configuredPermits()));
    withProperty(
        BrowserConcurrencyLimiter.PERMITS_PROPERTY,
        "이상한값",
        () -> assertEquals(1, BrowserConcurrencyLimiter.configuredPermits()));

    // 0 은 유효하다 — "기다리지 않는다"는 뜻이다. 음수만 기본값으로 되돌린다.
    withProperty(
        BrowserConcurrencyLimiter.TIMEOUT_PROPERTY,
        "0",
        () -> assertEquals(0, BrowserConcurrencyLimiter.configuredTimeoutSeconds()));
    withProperty(
        BrowserConcurrencyLimiter.TIMEOUT_PROPERTY,
        "-5",
        () -> assertEquals(300, BrowserConcurrencyLimiter.configuredTimeoutSeconds()));
  }

  @Test
  @DisplayName("공유 인스턴스는 하나뿐이다 — 여럿이면 총량 제한이 무의미하다")
  void sharedInstanceIsASingleton() {
    assertTrue(BrowserConcurrencyLimiter.shared() == BrowserConcurrencyLimiter.shared());
  }

  /** 시스템 프로퍼티를 잠시 바꿔 실행하고 원래대로 되돌린다. {@code value} 가 null 이면 지운 상태로 실행한다. */
  private static void withProperty(String key, String value, Runnable body) {
    String previous = System.getProperty(key);
    try {
      if (value == null) {
        System.clearProperty(key);
      } else {
        System.setProperty(key, value);
      }
      body.run();
    } finally {
      if (previous == null) {
        System.clearProperty(key);
      } else {
        System.setProperty(key, previous);
      }
    }
  }
}

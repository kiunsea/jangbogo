package com.jiniebox.jangbogo.svc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 프로필 락이 <b>실제 코드 경로에 배선돼 있는지</b> 감시한다 (Phase 5-3 배선).
 *
 * <h2>왜 이 테스트가 따로 있어야 하나</h2>
 *
 * <p>{@code MallProfileLock} 은 클래스도 테스트도 완성된 채로 한 국면을 통째로 지나갔다. 그런데 <b>프로덕션 호출자가 0건</b>이라 {@code
 * SessionProfileGate.PROFILE_LOCKED} 는 런타임에 도달할 수 없는 값이었고, 그동안 {@code
 * WebDriverManager.killOrphanProfileChrome} 은 <b>락 없이</b> 세 자리에서 불리고 있었다. 그 정리는 프로필을 문 chrome.exe 를
 * 가리지 않고 전부 죽이므로, 서비스로 도는 인스턴스가 사용자가 직접 띄운 인스턴스의 <b>사람이 로그인해 둔 살아 있는 창</b>을 죽일 수 있었다.
 *
 * <p>단위 테스트는 부품을 직접 불러서 검사하므로 이 상태를 원리상 잡지 못한다. 락이 옳다는 것과 그 락이 쓰인다는 것은 다른 사실이고, 사용자에게 닿는 것은 뒤쪽뿐이다.
 * 그래서 여기서는 <b>소스에서 호출자 수를 직접 센다</b> ({@code SessionExpiryWiringTest} 가 같은 이유로 같은 방식을 쓴다).
 *
 * <p>이 프로젝트는 "부품은 있는데 호출자가 0건" 결함을 반복해서 겪었다. 이 파일은 그 재발을 막는 자리다.
 *
 * <p>브라우저·네트워크·DB 를 쓰지 않는다. 소스 파일만 읽는다.
 *
 * @author KIUNSEA
 */
class MallProfileLockWiringTest {

  private static final Path MAIN = Path.of("src/main/java");

  // 경로는 문자열로 비교한다. Path.equals 는 구분자 표기에 걸려 OS 마다 다르게 답할 수 있는데,
  // 그 실패는 "배선이 없다" 와 똑같은 모양으로 나타나 원인을 엉뚱한 곳에서 찾게 만든다.
  private static final String LOCK = "com/jiniebox/jangbogo/svc/util/MallProfileLock.java";
  private static final String GATE = "com/jiniebox/jangbogo/svc/util/SessionProfileGate.java";
  private static final String DRIVER_MANAGER =
      "com/jiniebox/jangbogo/svc/util/WebDriverManager.java";
  private static final String CAPTURE = "com/jiniebox/jangbogo/svc/SessionCaptureService.java";
  private static final String COLLECTOR = "com/jiniebox/jangbogo/svc/mall/SsgSessionCollector.java";

  /** 배선이 끊겼을 때 사람이 읽을 문장. 어느 단언에서 터지든 같은 취지를 읽게 한다. */
  private static final String WHY =
      "\n테스트가 초록이어도 배선이 없으면 사용자에겐 아무 일도 일어나지 않는다."
          + "\n부품이 옳다는 것과 그 부품이 불린다는 것은 다른 사실이고, 사용자에게 닿는 것은 뒤쪽뿐이다.";

  @Test
  @DisplayName("프로필 락 획득의 프로덕션 호출자가 1건 이상 있다")
  void theProfileLockIsActuallyAcquiredInProduction() throws Exception {
    List<String> callers = productionFilesContaining("MallProfileLock.tryAcquireProfile(", LOCK);

    assertFalse(
        callers.isEmpty(),
        "MallProfileLock 을 잡는 프로덕션 코드가 0건이다 —"
            + " 프로세스 경계 보호가 통째로 없고 SessionProfileGate.PROFILE_LOCKED 는 도달 불가 값이다."
            + WHY);
    assertTrue(
        callers.contains(CAPTURE),
        "캡처 경로가 프로필 락을 잡지 않는다. 사람이 로그인해 둔 그 창이 가장 잃으면 안 되는 것이다: " + callers);
    assertTrue(
        callers.contains(COLLECTOR),
        "세션 주입 수집 경로가 프로필 락을 잡지 않는다. 수집도 고아 정리를 부르므로 같은 사고가 난다: " + callers);
  }

  @Test
  @DisplayName("락 결과를 게이트 판정으로 옮기는 프로덕션 호출자가 1건 이상 있다")
  void theLockOutcomeIsActuallyTranslatedIntoADecision() throws Exception {
    // 락을 잡기만 하고 판정을 제 손으로 하면, 같은 규칙(UNAVAILABLE 은 막지 않는다)이 호출부마다
    // 따로 적히고 한쪽만 고쳐도 컴파일과 테스트가 통과한다 — 이 프로젝트가 반복해서 고쳐 온 형태다.
    List<String> callers = productionFilesContaining("SessionProfileGate.fromLockOutcome(", GATE);

    assertFalse(
        callers.isEmpty(),
        "SessionProfileGate.fromLockOutcome 을 부르는 프로덕션 코드가 0건이다 —"
            + " PROFILE_LOCKED 판정이 어디에서도 만들어지지 않는다."
            + WHY);
  }

  @Test
  @DisplayName("고아 정리를 부르는 프로덕션 코드는 전부 프로필 락을 함께 쓴다")
  void everyOrphanSweepCallerAlsoHoldsTheProfileLock() throws Exception {
    // 이것이 이 파일의 핵심 단언이다. 호출자가 하나라도 락 없이 정리를 부르면, 그 순간 다른
    // 프로세스의 살아 있는 로그인 세션을 죽일 수 있다 — 죽은 뒤에는 되돌릴 방법이 없다
    // (인증 쿠키가 세션 스코프라 창이 닫히면 사라진다).
    List<String> sweepers = productionFilesContaining("killOrphanProfileChrome(", DRIVER_MANAGER);

    assertFalse(
        sweepers.isEmpty(),
        "killOrphanProfileChrome 을 부르는 프로덕션 코드가 0건이다 — 이 테스트가 서 있는 전제가 깨졌다."
            + "\n정리가 정말 필요 없어졌다면 이 테스트도 함께 지워야 한다."
            + WHY);

    List<String> unguarded = new ArrayList<>();
    for (String sweeper : sweepers) {
      if (!sourceWithoutComments(MAIN.resolve(sweeper)).contains("MallProfileLock")) {
        unguarded.add(sweeper);
      }
    }

    assertTrue(
        unguarded.isEmpty(),
        "프로필 락 없이 고아 정리를 부르는 프로덕션 코드가 있다: "
            + unguarded
            + "\n그 정리는 프로필을 문 chrome.exe 를 가리지 않고 전부 죽인다. '활성 캡처가 없으니 전부 고아다' 라는"
            + " 전제는 이 JVM 안에서만 참이라, 락 없이 부르면 다른 프로세스에서 사람이 로그인해 둔 창까지 죽인다."
            + "\n→ 부르기 '전에' MallProfileLock.tryAcquireProfile(프로필이름) 으로 잠그고, 못 잡으면 부르지 마라."
            + WHY);
  }

  // ---------------------------------------------------------------

  /**
   * {@code src/main} 에서 이 조각을 담고 있는 파일들을 찾는다.
   *
   * <p><b>주석을 걷어내고 본다.</b> javadoc 이 "여기를 부른다" 라고 적어 둔 것과 실제로 부르는 것은 다른 사실인데, 주석을 함께 세면 설명 한 줄이
   * 배선으로 오인되어 이 감시가 통째로 무의미해진다. 이 파일이 감시하는 계약은 {@code WebDriverManager} javadoc 에도 적혀 있으므로 특히 그렇다.
   *
   * @param needle 찾을 호출 형태
   * @param excluded 선언 자체가 들어 있는 파일 ({@code src/main/java} 기준 상대경로). 자기 자신을 호출자로 세지 않는다
   * @return 그 조각을 담은 파일 목록 ({@code src/main/java} 기준 상대경로)
   */
  private static List<String> productionFilesContaining(String needle, String excluded)
      throws IOException {

    List<String> found = new ArrayList<>();
    try (Stream<Path> files = Files.walk(MAIN)) {
      for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
        String relative = MAIN.relativize(file).toString().replace('\\', '/');
        if (relative.equals(excluded)) {
          continue;
        }
        if (sourceWithoutComments(file).contains(needle)) {
          found.add(relative);
        }
      }
    }
    return found;
  }

  /** 주석을 걷어낸 소스. */
  private static String sourceWithoutComments(Path path) throws IOException {
    return Files.readString(path, StandardCharsets.UTF_8)
        .replaceAll("(?s)/\\*.*?\\*/", "")
        .replaceAll("(?m)^\\s*//.*$", "");
  }
}

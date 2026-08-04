package com.jiniebox.jangbogo.svc.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 순정 Chrome 런처의 명령 조립·락 판정 검증 (Phase 4A-3).
 *
 * <p>브라우저를 띄우지 않는다 — 조립된 명령 토큰과 락 파일만 들여다본다. 실제 기동은 프로브(`@Tag("probe")`)가 맡는다.
 *
 * @author KIUNSEA
 */
class NativeChromeLoginLauncherTest {

  private static final Path CHROME = Paths.get("C:", "chrome", "chrome.exe");

  @Test
  @DisplayName("프로필 경로를 -- 접두사와 절대경로로 넘긴다")
  void carriesProfilePathWithPrefixAndAbsolutePath() {
    Path profile = Paths.get("build", "probe-profile");
    List<String> command = NativeChromeLoginLauncher.buildCommand(CHROME, profile, null);

    assertTrue(
        command.contains("--user-data-dir=" + profile.toAbsolutePath()),
        "접두사가 빠지면 Chrome 이 조용히 기본 프로필을 연다 — 사람은 로그인했다고 믿지만"
            + " 그 세션은 우리 프로필에 없다. 실제 명령: "
            + command);
    assertFalse(
        command.stream().anyMatch(a -> a.startsWith("user-data-dir=")), "실제 명령: " + command);
  }

  @Test
  @DisplayName("여는 쪽과 같은 하위 프로필을 쓴다")
  void usesTheSameProfileDirectoryAsTheReader() {
    // 만드는 쪽과 여는 쪽이 다른 하위 프로필을 보면 로그인해 둔 세션을 못 찾는다.
    assertTrue(
        NativeChromeLoginLauncher.buildCommand(CHROME, Paths.get("p"), null)
            .contains("--profile-directory=" + WebDriverManager.PROFILE_DIRECTORY));
  }

  @Test
  @DisplayName("이미 떠 있는 Chrome 에 흡수되지 않도록 새 창을 강제한다")
  void forcesANewWindow() {
    // --new-window 가 없으면 이미 떠 있는 인스턴스가 URL 만 받아 열고
    // 우리 프로필은 열리지 않은 채 프로세스가 즉시 끝난다.
    assertTrue(
        NativeChromeLoginLauncher.buildCommand(CHROME, Paths.get("p"), null)
            .contains("--new-window"));
  }

  @Test
  @DisplayName("첫 실행 안내 창을 띄우지 않는다")
  void suppressesFirstRunPrompts() {
    List<String> command = NativeChromeLoginLauncher.buildCommand(CHROME, Paths.get("p"), null);

    assertTrue(command.contains("--no-first-run"), "실제 명령: " + command);
    assertTrue(command.contains("--no-default-browser-check"), "실제 명령: " + command);
  }

  @Test
  @DisplayName("URL 은 마지막 인자로 붙고, 없으면 생략한다")
  void appendsUrlLast() {
    List<String> withUrl =
        NativeChromeLoginLauncher.buildCommand(CHROME, Paths.get("p"), "https://www.ssg.com/");
    assertEquals("https://www.ssg.com/", withUrl.get(withUrl.size() - 1));

    List<String> withoutUrl = NativeChromeLoginLauncher.buildCommand(CHROME, Paths.get("p"), "   ");
    assertFalse(withoutUrl.stream().anyMatch(a -> a.startsWith("http")), "실제 명령: " + withoutUrl);
  }

  @Test
  @DisplayName("실행 파일은 여는 쪽과 같은 프로퍼티로 지정한다")
  void binaryPropertyIsSharedWithTheReader() {
    // 프로필을 만든 Chrome 과 여는 Chrome 이 다르면 세션이 살아나지 않을 수 있다.
    // 두 경로가 같은 프로퍼티를 봐야 그 사고를 막는다.
    assertEquals(
        WebDriverManager.CHROME_BINARY_PROPERTY, NativeChromeLoginLauncher.CHROME_BINARY_PROPERTY);

    String previous = System.getProperty(NativeChromeLoginLauncher.CHROME_BINARY_PROPERTY);
    try {
      System.setProperty(
          NativeChromeLoginLauncher.CHROME_BINARY_PROPERTY, "D:\\pinned\\chrome.exe");
      assertEquals(
          Paths.get("D:\\pinned\\chrome.exe"), NativeChromeLoginLauncher.resolveChromeBinary());
    } finally {
      if (previous == null) {
        System.clearProperty(NativeChromeLoginLauncher.CHROME_BINARY_PROPERTY);
      } else {
        System.setProperty(NativeChromeLoginLauncher.CHROME_BINARY_PROPERTY, previous);
      }
    }
  }

  @Test
  @DisplayName("실행 파일을 못 찾으면 조용히 넘기지 않는다")
  void failsLoudlyWhenChromeIsMissing() {
    String previous = System.getProperty(NativeChromeLoginLauncher.CHROME_BINARY_PROPERTY);
    try {
      // 빈 값은 없는 것으로 보고 표준 위치를 훑는다. 표준 위치에도 없으면 예외다.
      System.setProperty(NativeChromeLoginLauncher.CHROME_BINARY_PROPERTY, "   ");
      boolean installed =
          NativeChromeLoginLauncher.STANDARD_BINARY_PATHS.stream()
              .anyMatch(p -> Files.isRegularFile(Paths.get(p)));
      if (!installed) {
        assertThrows(IllegalStateException.class, NativeChromeLoginLauncher::resolveChromeBinary);
      }
    } finally {
      if (previous == null) {
        System.clearProperty(NativeChromeLoginLauncher.CHROME_BINARY_PROPERTY);
      } else {
        System.setProperty(NativeChromeLoginLauncher.CHROME_BINARY_PROPERTY, previous);
      }
    }
  }

  @Test
  @DisplayName("표준 입출력을 물려주지 않는다 — 물려주면 호출자가 창이 닫힐 때까지 못 끝난다")
  void doesNotInheritStandardStreams() {
    // 실측: inheritIO() 로 띄웠더니 Chrome 이 부모의 스트림 핸들을 잡아, 창을 띄우고 바로
    // 반환해야 하는 호출이 사람이 로그인을 마칠 때까지 3시간 13분을 붙잡혀 있었다.
    ProcessBuilder builder =
        NativeChromeLoginLauncher.newProcessBuilder(CHROME, Paths.get("p"), null);

    assertEquals(
        ProcessBuilder.Redirect.DISCARD,
        builder.redirectOutput(),
        "표준 출력을 물려주면 호출자가 브라우저 수명에 묶인다.");
    assertEquals(
        ProcessBuilder.Redirect.DISCARD, builder.redirectError(), "표준 오류를 물려주면 호출자가 브라우저 수명에 묶인다.");
  }

  @Test
  @DisplayName("락 파일이 없으면 쓰이지 않는 상태로 본다")
  void treatsMissingLockFileAsFree(@TempDir Path profile) {
    assertFalse(NativeChromeLoginLauncher.isProfileInUse(profile));
  }

  @Test
  @DisplayName("락 파일이 잡혀 있으면 사용 중으로 본다 — 창이 닫혔다는 판정의 근거")
  void detectsHeldLockFile(@TempDir Path profile) throws IOException {
    // Process.waitFor() 는 사람이 로그인하기 전에 돌아올 수 있다.
    // 창이 정말 닫혔는지는 이 락으로만 알 수 있다.
    Path lockFile = profile.resolve(NativeChromeLoginLauncher.LOCK_FILE);
    Files.createFile(lockFile);

    try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.WRITE)) {
      FileLock held = channel.tryLock();
      assertTrue(NativeChromeLoginLauncher.isProfileInUse(profile), "잡혀 있는데 비었다고 판정했다.");
      held.release();
    }

    assertFalse(NativeChromeLoginLauncher.isProfileInUse(profile), "놓았는데 잡혀 있다고 판정했다.");
  }

  @Test
  @DisplayName("놓이지 않으면 대기가 시간 안에 false 로 끝난다")
  void awaitTimesOutWhileStillHeld(@TempDir Path profile) throws IOException {
    Path lockFile = profile.resolve(NativeChromeLoginLauncher.LOCK_FILE);
    Files.createFile(lockFile);

    try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.WRITE)) {
      FileLock held = channel.tryLock();
      assertFalse(
          NativeChromeLoginLauncher.awaitProfileRelease(
              profile, Duration.ofMillis(150), Duration.ofMillis(20)),
          "잡혀 있는데 해제됐다고 판정했다 — 로그인 전에 다음 단계로 넘어간다.");
      held.release();
    }

    assertTrue(
        NativeChromeLoginLauncher.awaitProfileRelease(
            profile, Duration.ofSeconds(2), Duration.ofMillis(20)));
  }
}

package com.jiniebox.jangbogo.svc.util;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 사람이 직접 로그인할 순정 Chrome 을 띄운다 (Phase 4A-3 · 5-6).
 *
 * <h2>왜 Selenium 이 아니라 순정 chrome.exe 인가</h2>
 *
 * <p>세션을 <b>만드는</b> 일과 <b>여는</b> 일은 다른 문제다. 만드는 쪽은 사람이 로그인하는 절차이므로 자동화 표식이 하나도 없는 편이 낫다. Selenium
 * 으로 띄우면 아무리 표식을 지워도 지운 흔적이 남을 수 있고, 로그인 단계에서 막히면 "프로필 재사용이 안 되는 것"인지 "로그인 자체가 막힌 것"인지 구분할 수 없게 된다.
 *
 * <p>그래서 만드는 쪽은 <b>순수 JDK {@link ProcessBuilder}</b> 로 chrome.exe 를 그냥 실행한다. Selenium·chromedriver
 * 가 관여하지 않으므로 사람이 평소 쓰는 브라우저와 구분되지 않는다.
 *
 * <h2>창이 닫혔다고 끝난 게 아니다</h2>
 *
 * <p>Chrome 은 실행된 프로세스가 브라우저 수명주기를 그대로 갖지 않는다. 이미 떠 있는 인스턴스에 넘기고 즉시 빠지거나, 권한이 다르면 자신을 재실행하고 원래
 * 프로세스는 곧바로 종료한다. 그래서 {@code Process.waitFor()} 는 <b>사람이 로그인하기 전에 돌아올 수 있다.</b>
 *
 * <p>믿을 수 있는 신호는 <b>프로필 락 해제</b>다. Chrome 이 프로필을 쓰는 동안에는 {@code lockfile} 을 붙잡고 있고, 완전히 닫혀야 놓는다.
 * 쿠키가 디스크로 내려가는 것도 그 시점이다 — 강제 종료로 끝내면 로그인해 놓고도 세션이 남지 않을 수 있다.
 *
 * <p>브라우저를 띄우는 것 말고는 네트워크·DB 를 쓰지 않는다.
 *
 * @author KIUNSEA
 */
public final class NativeChromeLoginLauncher {

  private static final Logger logger = LogManager.getLogger(NativeChromeLoginLauncher.class);

  /** Chrome 실행 파일 경로 재정의. 없으면 표준 설치 위치를 훑는다. */
  public static final String CHROME_BINARY_PROPERTY = WebDriverManager.CHROME_BINARY_PROPERTY;

  /** Chrome 이 프로필을 쓰는 동안 붙잡고 있는 파일. */
  static final String LOCK_FILE = "lockfile";

  /** 표준 설치 위치. 위에서부터 찾는다. */
  static final List<String> STANDARD_BINARY_PATHS =
      List.of(
          "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
          "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe");

  private NativeChromeLoginLauncher() {}

  /**
   * 실행할 chrome.exe 를 찾는다.
   *
   * <p>프로퍼티가 있으면 그것을 쓴다 — 프로필을 만든 Chrome 과 여는 Chrome 을 같은 것으로 고정해야 하므로, 두 경로가 같은 프로퍼티를 본다({@link
   * WebDriverManager#CHROME_BINARY_PROPERTY}).
   *
   * @return chrome.exe 경로
   * @throws IllegalStateException 찾지 못하면
   */
  public static Path resolveChromeBinary() {
    String override = System.getProperty(CHROME_BINARY_PROPERTY);
    if (override != null && !override.isBlank()) {
      return Paths.get(override.trim());
    }
    for (String candidate : STANDARD_BINARY_PATHS) {
      Path path = Paths.get(candidate);
      if (Files.isRegularFile(path)) {
        return path;
      }
    }
    throw new IllegalStateException(
        "chrome.exe 를 찾을 수 없다. -D" + CHROME_BINARY_PROPERTY + "=<경로> 로 지정할 것.");
  }

  /**
   * 실행 명령을 조립한다.
   *
   * <p>브라우저를 띄우지 않고 검증할 수 있도록 분리했다. {@code --user-data-dir} 의 <b>{@code --} 접두사와 절대경로</b>는 여기서도 같은
   * 이유로 필수다 — 빠지면 Chrome 이 조용히 기본 프로필을 열고, 사람은 로그인했다고 믿지만 그 세션은 우리 프로필에 없다.
   *
   * @param chromeBinary chrome.exe
   * @param profileDir 프로필 디렉터리
   * @param url 처음 열 주소. 없으면 생략
   * @return 명령 토큰
   */
  public static List<String> buildCommand(Path chromeBinary, Path profileDir, String url) {
    List<String> command = new ArrayList<>();
    command.add(chromeBinary.toString());
    command.add("--user-data-dir=" + profileDir.toAbsolutePath());
    command.add("--profile-directory=" + WebDriverManager.PROFILE_DIRECTORY);
    // 새 창으로 띄운다. 안 그러면 이미 떠 있는 Chrome 이 URL 만 받아 열고
    // 우리 프로필은 열리지 않은 채 프로세스가 즉시 끝난다.
    command.add("--new-window");
    // 첫 실행 안내·기본 브라우저 확인 창이 로그인 화면을 가린다.
    command.add("--no-first-run");
    command.add("--no-default-browser-check");
    if (url != null && !url.isBlank()) {
      command.add(url.trim());
    }
    return command;
  }

  /**
   * 사람이 로그인할 Chrome 을 띄운다.
   *
   * @param profileDir 프로필 디렉터리. 없으면 만든다
   * @param url 처음 열 주소
   * @return 실행된 프로세스. <b>수명주기를 믿지 말 것</b> — {@link #awaitProfileRelease} 를 쓴다
   * @throws IOException 실행 실패
   */
  public static Process launch(Path profileDir, String url) throws IOException {
    Files.createDirectories(profileDir);
    Path chrome = resolveChromeBinary();

    logger.info("순정 Chrome 기동: {}", String.join(" ", buildCommand(chrome, profileDir, url)));
    return newProcessBuilder(chrome, profileDir, url).start();
  }

  /**
   * 기동에 쓸 {@link ProcessBuilder} 를 조립한다.
   *
   * <p><b>표준 입출력을 물려주지 않는다.</b> {@code inheritIO()} 로 띄우면 Chrome 이 부모의 스트림 핸들을 잡고 있어, <b>부모가 먼저 끝나려
   * 해도 Chrome 이 닫힐 때까지 끝나지 못한다.</b> 실측으로 3시간 13분을 붙잡힌 적이 있다 — 창을 띄우고 바로 반환해야 하는 자리에서 사람이 로그인을 마칠
   * 때까지 호출자가 통째로 멈춰 있었다.
   *
   * <p>사람이 쓰라고 띄우는 브라우저이므로 콘솔 출력을 받을 이유도 없다. 진단이 필요하면 {@code {프로필}/chrome_debug.log} 를 본다.
   *
   * @param chromeBinary chrome.exe
   * @param profileDir 프로필 디렉터리
   * @param url 처음 열 주소
   * @return 조립된 ProcessBuilder
   */
  static ProcessBuilder newProcessBuilder(Path chromeBinary, Path profileDir, String url) {
    return new ProcessBuilder(buildCommand(chromeBinary, profileDir, url))
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD);
  }

  /**
   * 프로필이 지금 쓰이고 있는지.
   *
   * <p>{@code lockfile} 을 배타적으로 잠글 수 있으면 Chrome 이 놓은 것이다. 파일이 아예 없으면 아직 한 번도 열리지 않았거나 이미 닫힌 것이므로 역시
   * 쓰이지 않는 상태로 본다.
   *
   * @param profileDir 프로필 디렉터리
   * @return 다른 프로세스가 쓰고 있으면 true
   */
  public static boolean isProfileInUse(Path profileDir) {
    Path lockFile = profileDir.resolve(LOCK_FILE);
    if (!Files.exists(lockFile)) {
      return false;
    }
    try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.WRITE)) {
      FileLock lock = channel.tryLock();
      if (lock == null) {
        return true;
      }
      lock.release();
      return false;
    } catch (IOException | RuntimeException e) {
      // 열 수 없다는 것 자체가 누군가 쥐고 있다는 신호다.
      return true;
    }
  }

  /**
   * 프로필이 놓일 때까지 기다린다.
   *
   * <p>이 대기가 <b>"사람이 로그인을 끝내고 창을 닫았다"</b>의 판정이다. {@code Process.waitFor()} 로는 알 수 없다.
   *
   * @param profileDir 프로필 디렉터리
   * @param timeout 대기 상한
   * @param pollInterval 확인 주기
   * @return 시간 안에 놓였으면 true
   */
  public static boolean awaitProfileRelease(
      Path profileDir, Duration timeout, Duration pollInterval) {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      if (!isProfileInUse(profileDir)) {
        // 쿠키가 디스크로 내려가는 데 약간의 여유를 준다.
        sleep(pollInterval);
        if (!isProfileInUse(profileDir)) {
          return true;
        }
      }
      sleep(pollInterval);
    }
    return false;
  }

  private static void sleep(Duration duration) {
    try {
      Thread.sleep(Math.max(1L, duration.toMillis()));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}

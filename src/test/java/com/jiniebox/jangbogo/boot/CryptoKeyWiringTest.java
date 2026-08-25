package com.jiniebox.jangbogo.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * 암호화 키 장치가 <b>실제로 기동 경로에 물려 있는지</b>를 소스에서 직접 센다.
 *
 * <h2>왜 이 가드가 필요한가</h2>
 *
 * <p>이 기능은 두 줄이 빠지면 통째로 죽는데, 둘 다 <b>컴파일도 되고 단위테스트도 전부 통과한다.</b>
 *
 * <ul>
 *   <li>{@code spring.factories} 에서 {@code CryptoPropertyBridge} 줄이 빠지면 그 다리는 그냥 실행되지 않는다. 설정 파일에
 *       키를 적어도 먹지 않는, 배포본에서만 드러나는 상태가 된다 — 그 파일의 기존 주석이 같은 경고를 이미 적어 두었다.
 *   <li>{@code StartupTasks.onApplicationReady} 에서 발급 호출이 빠지면 키가 영영 만들어지지 않는다. 그러면 공개된 기본키를 계속 쓰면서도
 *       테스트는 초록이다.
 * </ul>
 *
 * <p>이 저장소는 "테스트는 초록인데 프로덕션 호출자가 0건" 을 여러 번 겪었다. 그래서 숫자를 센다.
 *
 * <p>브라우저·네트워크·DB 를 쓰지 않는다.
 */
class CryptoKeyWiringTest {

  private static final Path MAIN = Path.of("src/main/java");
  private static final Path FACTORIES = Path.of("src/main/resources/META-INF/spring.factories");
  private static final Path STARTUP_TASKS =
      MAIN.resolve("com/jiniebox/jangbogo/boot/StartupTasks.java");

  @Test
  @DisplayName("설정 다리가 spring.factories 에 등록돼 있다 — 빠지면 조용히 실행되지 않는다")
  void theBridgeIsRegistered() throws IOException {
    String factories = Files.readString(FACTORIES, StandardCharsets.UTF_8);

    assertTrue(
        factories.contains("com.jiniebox.jangbogo.boot.CryptoPropertyBridge"),
        "CryptoPropertyBridge 가 등록돼 있지 않다 — config/application.yml 의 암호화 키가 먹지 않는다.");
    assertTrue(
        factories.contains("com.jiniebox.jangbogo.boot.SessionProfilePropertyBridge"),
        "기존 다리가 함께 사라졌다 — 두 다리는 같은 줄을 공유한다.");
  }

  @Test
  @DisplayName("기동이 실제로 키를 확보한다 — 부르지 않으면 공개 기본키로 계속 돈다")
  void startupActuallyProvisionsTheKey() throws IOException {
    String source = sourceWithoutComments(STARTUP_TASKS);

    assertTrue(
        source.contains("CryptoKeyProvisioning.ensureProvisioned()"),
        "발급을 부르는 자리가 없다 — 키가 영영 만들어지지 않는다.");

    // 호출이 파일에 들어 있는 것과 그 호출이 도달하는 것은 다른 사실이다.
    // 기동 회차의 본문에서 한 번 더 센다.
    String onReady = methodBody(source, "public void onApplicationReady()");
    assertFalse(onReady.isEmpty(), "기동 진입점을 찾지 못했다 — 이름이 바뀌었으면 이 가드도 따라와야 한다.");
    assertTrue(
        onReady.contains("provisionCryptoKey()"), "기동 회차가 키 확보를 거치지 않는다 — 발급 코드는 있으나 도달하지 않는다.");
  }

  @Test
  @DisplayName("이전이 ftp_pass 한 칸만 건드린다 — updateConfig 는 public_key 까지 덮는다")
  void theMigrationTouchesOnlyThePasswordColumn() throws IOException {
    String provision =
        methodBody(sourceWithoutComments(STARTUP_TASKS), "void provisionCryptoKey()");

    assertFalse(provision.isEmpty(), "이전 메서드를 찾지 못했다.");
    assertTrue(
        provision.contains("updateEncryptedFtpPassword("), "좁은 갱신 메서드를 쓰지 않는다 — 한 칸만 바꿔야 한다.");
    assertFalse(
        provision.contains("updateConfig("),
        "updateConfig 로 이전한다 — 그것은 전체 필드를 덮어써서 public_key 가 빈 값이 되고 FTP 암호화가 통째로 깨진다.");
  }

  @Test
  @DisplayName("어떤 다리도 암호화 키 값을 로그에 싣지 않는다")
  void noBridgeLogsTheKeyItself() throws IOException {
    String bridge =
        sourceWithoutComments(MAIN.resolve("com/jiniebox/jangbogo/boot/CryptoPropertyBridge.java"));

    // 세션 프로필 다리는 key=value 를 통째로 찍는다. 여기서 같은 형태를 쓰면
    // 키를 소스에서 빼내려고 만든 장치가 키를 로그 파일에 흘린다.
    assertFalse(bridge.contains("+ \"=\" + value"), "옮긴 값을 로그에 이어 붙인다 — 암호화 키가 평문으로 로그에 남는다.");
    assertFalse(bridge.contains("value.trim() + "), "옮긴 값을 로그 문자열에 섞는다.");
  }

  @Test
  @DisplayName("발급된 키 파일이 저장소에 커밋되지 않도록 막혀 있다")
  void theKeyFileIsGitIgnored() throws IOException {
    String gitignore = Files.readString(Path.of(".gitignore"), StandardCharsets.UTF_8);

    assertTrue(
        gitignore.contains("config/jangbogo-crypto.key"),
        "키 파일이 .gitignore 에 없다 — 커밋되면 공개 저장소에 키가 올라가, 이 기능이 막으려던 상태가 그대로 된다.");
  }

  @Test
  @DisplayName("암호화 키를 읽는 자리는 PasswordEncryptor 하나뿐이다")
  void onlyOneClassReadsTheKey() throws IOException {
    List<String> readers =
        productionFilesContaining(
            "CryptoKeyStore.key()", "com/jiniebox/jangbogo/util/PasswordEncryptor.java");

    assertEquals(List.of(), readers, "관문 밖에서 키를 직접 읽는다 — 해석 순서(재정의 > 발급 > 기본값)가 두 벌이 된다.");
  }

  /** 그 메서드 선언부터 다음 메서드 선언 직전까지를 잘라 낸다. */
  private static String methodBody(String source, String signature) {
    int start = source.indexOf(signature);
    if (start < 0) {
      return "";
    }
    int next = source.indexOf("\n  public ", start + signature.length());
    int nextPackagePrivate = source.indexOf("\n  void ", start + signature.length());
    int nextPrivate = source.indexOf("\n  private ", start + signature.length());
    for (int candidate : new int[] {nextPackagePrivate, nextPrivate}) {
      if (candidate >= 0 && (next < 0 || candidate < next)) {
        next = candidate;
      }
    }
    return next < 0 ? source.substring(start) : source.substring(start, next);
  }

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

  /** 주석을 걷어낸 소스. javadoc 이 적어 둔 것과 실제로 부르는 것은 다른 사실이다. */
  private static String sourceWithoutComments(Path path) throws IOException {
    return Files.readString(path, StandardCharsets.UTF_8)
        .replaceAll("(?s)/\\*.*?\\*/", "")
        .replaceAll("(?m)^\\s*//.*$", "");
  }
}

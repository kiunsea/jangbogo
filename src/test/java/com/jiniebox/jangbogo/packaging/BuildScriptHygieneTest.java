package com.jiniebox.jangbogo.packaging;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 배치 스크립트가 {@code build/} 를 직접 지우지 못하게 막는다.
 *
 * <h2>왜 필요한가 — clean 가드만으로는 부족하다</h2>
 *
 * <p>{@code build.gradle} 의 clean 가드는 {@code build/} 아래에 앱 DB 가 있으면 {@code clean} 을 멈춘다. 그런데 그것은
 * <b>Gradle 을 거치는 경로만</b> 지킨다. 배치 파일이 {@code rmdir /s /q build} 로 직접 지우면 가드는 아무 일도 하지 않는다 — Gradle
 * 이 관여하지 않기 때문이다.
 *
 * <p>가정이 아니다. {@code test_run.bat} 이 실제로 {@code gradlew clean} 뒤에 {@code rmdir /s /q} 로 {@code
 * build}·{@code bin}·{@code .gradle} 을 통째로 다시 지우고 있었다. 이 프로젝트는 배포 패키지를 {@code build/distributions}
 * 아래에 풀어 그 자리에서 실행하는 관행이 있고, 그렇게 실행된 인스턴스는 자기 {@code db/} 를 그 안에 만든다. 즉 그 한 줄에 <b>실제 구매 내역이 통째로
 * 사라진다.</b> 실제로 사라졌고, {@code rmdir} 도 Gradle 의 delete 도 휴지통을 거치지 않아 복구 수단이 없었다.
 *
 * <h2>무엇을 막고 무엇을 허용하나</h2>
 *
 * <p>막는 것은 <b>Gradle 을 우회하는 직접 삭제</b>다. {@code gradlew clean} 자체는 막지 않는다 — 그쪽은 clean 가드가 지키고,
 * {@code clean_build.bat}·{@code build_package.bat} 은 그것을 정당하게 쓴다. 다만 {@code test_run.bat} 은 소스에서
 * 띄우는 개발 반복용이라 애초에 지울 이유가 없으므로 {@code clean} 호출 자체를 금지한다.
 *
 * <p>파일만 읽는다. 브라우저·네트워크·DB 를 쓰지 않는다.
 *
 * @author KIUNSEA
 */
class BuildScriptHygieneTest {

  /** 배치 스크립트가 있는 자리. 저장소 안의 {@code .bat} 은 전부 검사 대상이다. */
  private static final List<Path> SCRIPT_ROOTS = List.of(Path.of("bat"), Path.of("packaging"));

  /**
   * Gradle 을 거치지 않고 디렉터리를 통째로 지우는 명령의 형태.
   *
   * <p>{@code rmdir /s} 와 {@code rd /s} 는 같은 명령의 두 이름이고, {@code del /s} 는 파일만 지우지만 재귀라 결과가 같다. 옵션
   * 순서와 대소문자는 자유롭게 쓰이므로({@code /S /Q}, {@code /q /s}) 소문자로 낮춘 뒤 명령 이름과 재귀 옵션이 함께 있는지로 본다.
   */
  private static final List<String> RECURSIVE_DELETE_COMMANDS = List.of("rmdir", "rd ", "del ");

  /** 지워지면 실데이터가 사라지는 디렉터리. 빌드 산출물과 사용자 데이터가 섞이는 자리들이다. */
  private static final List<String> PROTECTED_DIRECTORIES = List.of("build", "bin", ".gradle");

  @Test
  @DisplayName("배치 스크립트가 build 를 직접 지우지 않는다 — clean 가드를 우회하기 때문")
  void keepsBatchScriptsFromDeletingBuildDirectly() throws Exception {
    List<String> offenders = new ArrayList<>();

    for (Path script : batchScripts()) {
      List<String> lines = Files.readAllLines(script, StandardCharsets.UTF_8);
      for (int i = 0; i < lines.size(); i++) {
        String line = lines.get(i);
        if (deletesProtectedDirectory(line)) {
          offenders.add(script.toString().replace('\\', '/') + ":" + (i + 1) + "  " + line.trim());
        }
      }
    }

    assertTrue(
        offenders.isEmpty(),
        "배치 스크립트가 build/bin/.gradle 을 직접 지운다. build.gradle 의 clean 가드는 Gradle 을 거치는 경로만 지키므로 이 형태는"
            + " 통째로 우회한다. 이 프로젝트는 배포 패키지를 build/distributions 아래에 풀어 그 자리에서 실행하는 관행이 있고, 그 인스턴스는"
            + " 자기 db/ 를 그 안에 만든다 — 실제로 그 한 줄에 구매 내역이 사라졌고 휴지통을 거치지 않아 복구하지 못했다."
            + " 클린 빌드가 필요하면 gradlew clean 을 써라(가드가 지킨다):\n  "
            + String.join("\n  ", offenders));
  }

  @Test
  @DisplayName("개발 테스트 실행 스크립트는 clean 을 부르지 않는다")
  void keepsTheDevelopmentRunScriptFreeOfClean() throws Exception {
    Path testRun = Path.of("bat", "test_run.bat");
    assertTrue(Files.isRegularFile(testRun), "bat/test_run.bat 이 없다. 테스트 작업 디렉터리는 프로젝트 루트여야 한다.");

    // 소스를 고치고 한 번 띄워 보는 것이 이 스크립트의 역할이다. 매번 전체를 지우고 다시 받을
    // 이유가 없고, 개발 중에는 build/ 아래에 배포본 인스턴스가 함께 있을 가능성이 가장 높다.
    // 클린 빌드가 필요하면 clean_build.bat 이 따로 있다.
    //
    // 주석을 먼저 걷어낸다. 이 검사를 처음 넣었을 때 실제로 걸린 것이 명령이 아니라 "예전에는
    // 여기서 gradlew clean 을 돌렸다" 는 설명 문구였다. 그대로 두면 통과시키는 방법이 두 가지가
    // 되는데(명령을 지우거나, 설명을 지우거나) 뒤쪽은 근거만 사라지고 위험은 그대로다.
    String script = commandsOf(Files.readString(testRun, StandardCharsets.UTF_8));
    assertFalse(
        callsGradleClean(script),
        "bat/test_run.bat 이 clean 을 부른다. 이 스크립트는 소스에서 띄우는 개발 반복용이라 지울 이유가 없다."
            + " 클린 빌드가 필요하면 clean_build.bat 을 써라.");
  }

  @Test
  @DisplayName("판별식이 실제 위험 형태는 잡고 정상 명령은 통과시킨다")
  void theDetectionRuleItselfCatchesTheDangerousForms() {
    // 대조군이다. 위 두 검사는 "지금 위반이 0건" 이면 통과하므로, 판별식이 통째로 죽어도 초록이
    // 된다 — 이 프로젝트가 실제로 두 번 겪은 형태다(만료 감지가 테스트 25건 초록인 채 호출자
    // 0건이었던 것, 배포 산출물 가드의 판별식을 무력화해도 5건이 전부 통과했던 것).
    // 그래서 저장소 상태가 아니라 판별부에 직접 입력을 넣는다.

    // 걸려야 하는 것 — 실제로 test_run.bat 에 있던 줄과 그 변형들
    for (String dangerous :
        List.of(
            "rmdir /s /q \"build\"",
            "rmdir /s /q build",
            "RMDIR /S /Q BUILD",
            "rd /s /q bin",
            "rd /q /s .gradle",
            "del /s /q build\\libs\\*.jar",
            "        rmdir /s /q \"%%D\"  REM CACHE_DIRS=build bin .gradle")) {
      assertTrue(
          deletesProtectedDirectory(dangerous),
          "판별식이 위험한 삭제를 놓친다: " + dangerous + "\n이 형태가 통과하면 가드가 있으나 마나다.");
    }

    // 걸리면 안 되는 것 — 오탐이 나기 시작하면 사람은 판별식을 고치는 대신 꺼 버린다.
    for (String safe :
        List.of(
            "call gradlew.bat clean build", // Gradle 경유 — clean 가드가 지킨다
            "rmdir /s /q \"%TEMP%\\jangbogo-work\"", // 보호 대상이 아닌 경로
            "del build.log", // 재귀가 아니다
            "echo 이전 빌드 결과를 삭제하고 새로 빌드합니다", // 설명 문구
            "REM rmdir /s /q build 는 clean 가드를 우회하므로 쓰지 않는다", // 금지를 설명하는 주석
            "if exist \"build\\libs\" echo found")) {
      assertFalse(
          deletesProtectedDirectory(safe), "판별식이 정상 명령을 잘못 잡는다: " + safe + "\n오탐이 늘면 가드가 꺼진다.");
    }

    // clean 판별도 같이 두들긴다.
    assertTrue(callsGradleClean("call gradlew.bat clean"), "gradlew clean 호출을 놓친다.");
    assertTrue(
        callsGradleClean("call gradlew.bat clean bootjar createjre packagedist"), "복합 호출을 놓친다.");
    assertFalse(callsGradleClean("call gradlew.bat bootrun"), "bootRun 을 clean 으로 잘못 본다.");
    assertFalse(
        callsGradleClean("rem 클린 빌드가 필요하면 clean_build.bat 을 써라"),
        "설명 주석의 clean_build 라는 낱말을 호출로 잘못 본다.");

    // 주석 걷어내기도 두들긴다. 이 검사를 처음 넣었을 때 실제로 여기서 걸렸다 — 명령이 아니라
    // "예전에는 gradlew clean 을 돌렸다" 는 설명 문구였다.
    assertFalse(
        callsGradleClean(commandsOf("REM 예전에는 여기서 gradlew clean 을 돌렸다.\ncall gradlew.bat bootRun")),
        "주석에 적힌 설명이 명령으로 읽힌다. 그러면 근거를 지우는 것으로 가드를 통과시킬 수 있게 된다.");
    assertTrue(
        callsGradleClean(commandsOf("REM 설명\ncall gradlew.bat clean build")),
        "주석을 걷어내면서 실제 명령까지 지워졌다. 그러면 이 가드는 아무것도 지키지 않는다.");
  }

  // ---------------------------------------------------------------
  // 판별부 — 대조군이 직접 두들길 수 있도록 함수로 둔다.
  // 검사 본문에 붙여 두면 판별식을 죽여도 아무 테스트가 안 깨진다.
  // ---------------------------------------------------------------

  /**
   * 한 줄이 보호 대상 디렉터리를 재귀 삭제하는지.
   *
   * <p>주석(<code>REM</code>·<code>::</code>)은 제외한다 — 금지 사실을 설명하는 주석이 스스로 걸리면, 그 설명을 지우는 것으로 통과시키게 되어
   * 근거만 사라진다.
   */
  private static boolean deletesProtectedDirectory(String rawLine) {
    String line = rawLine.trim().toLowerCase(Locale.ROOT);
    if (line.startsWith("rem ") || line.startsWith("::") || line.startsWith("echo ")) {
      return false;
    }
    boolean recursive = line.contains("/s");
    if (!recursive) {
      return false;
    }
    boolean deleteCommand = RECURSIVE_DELETE_COMMANDS.stream().anyMatch(line::contains);
    if (!deleteCommand) {
      return false;
    }
    // 경로 어디에 있든 보호 대상 이름이 토큰으로 등장하면 걸린다. 변수로 감싼 형태
    // (CACHE_DIRS=build bin .gradle 를 %%D 로 도는 루프)도 그 선언이 같은 줄에 있으면 잡힌다.
    for (String dir : PROTECTED_DIRECTORIES) {
      if (line.matches(
          ".*(^|[\\s\"'\\\\/=])" + java.util.regex.Pattern.quote(dir) + "([\\s\"'\\\\/]|$).*")) {
        return true;
      }
    }
    return false;
  }

  /** 한 줄(또는 스크립트 전문)이 gradle 의 clean 태스크를 호출하는지. */
  private static boolean callsGradleClean(String lowercased) {
    return lowercased.matches("(?s).*gradlew(\\.bat)?[^\\r\\n]*\\sclean(\\s|$).*");
  }

  /**
   * 배치 스크립트에서 주석을 걷어내고 실행되는 줄만 남긴다.
   *
   * <p>가드가 설명 문구에 걸리면 "무엇을 하지 말라" 고 적어 둔 근거를 지우는 것으로 통과시킬 수 있게 된다. 그러면 위험은 그대로인데 이유만 사라진다 — 이 저장소는
   * 실제로 그 형태를 겪었다(배포 산출물 가드가 {@code build.gradle} 주석의 낱말 하나에 걸려 있었다).
   */
  private static String commandsOf(String script) {
    StringBuilder commands = new StringBuilder();
    for (String raw : script.split("\\R")) {
      String line = raw.trim().toLowerCase(Locale.ROOT);
      if (line.startsWith("rem ") || line.startsWith("rem\t") || line.equals("rem")) {
        continue;
      }
      if (line.startsWith("::") || line.startsWith("echo ")) {
        continue;
      }
      commands.append(line).append('\n');
    }
    return commands.toString();
  }

  /** 저장소 안의 {@code .bat} 전부. 루트가 없으면 건너뛴다(패키징 폴더가 없는 체크아웃 대비). */
  private static List<Path> batchScripts() throws IOException {
    List<Path> scripts = new ArrayList<>();
    for (Path root : SCRIPT_ROOTS) {
      if (!Files.isDirectory(root)) {
        continue;
      }
      try (Stream<Path> files = Files.walk(root)) {
        files
            .filter(Files::isRegularFile)
            .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".bat"))
            .forEach(scripts::add);
      }
    }
    assertFalse(scripts.isEmpty(), "검사할 배치 스크립트를 하나도 찾지 못했다. 수집기가 조용히 좁아지면 이 가드는 아무것도 지키지 않는다.");
    return scripts;
  }
}

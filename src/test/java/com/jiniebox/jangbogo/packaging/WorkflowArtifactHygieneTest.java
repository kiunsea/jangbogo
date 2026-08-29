package com.jiniebox.jangbogo.packaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CI 아티팩트가 다시 무한히 쌓이지 못하게 워크플로의 <b>형태</b>를 감시한다.
 *
 * <h2>왜 필요한가 — retention-days 는 저장 quota 를 지켜 주지 않는다</h2>
 *
 * <p>{@code build.yml} 의 업로드에는 {@code retention-days: 7} 이 붙어 있었다. 그러면 알아서 사라질 것으로 봤지만 그렇지 않다.
 * <b>만료된 아티팩트는 GitHub 의 GC 가 실제로 걷어갈 때까지 목록에 남아 저장 quota 를 계속 잡는다.</b> 2026-08-29 실측: 이 저장소에 228벌
 * 7,637MB 가 쌓여 있었고 그중 6,046MB(184벌)가 이미 만료된 것이었으며, 가장 오래된 것은 {@code retention-days: 7} 인데도 2025-11
 * 자였다. 계정 단위 한도는 500MB 이므로 15배다.
 *
 * <p>피해는 이 저장소가 아니라 <b>같은 계정의 다른 저장소</b>에서 먼저 났다. quota 가 계정 단위라 doribox-studio 는 2026-08-07 이후 모든
 * Build 에서 업로드가 {@code Failed to CreateArtifact: Artifact storage quota has been hit} 로 실패했고, 그쪽
 * 업로드에는 {@code continue-on-error: true} 가 붙어 있어 Build 가 계속 초록이라 3주간 아무도 몰랐다.
 *
 * <h2>무엇을 감시하나</h2>
 *
 * <p>고친 것은 셋이다 — (1) {@code build.yml} 이 업로드 <b>앞</b>에서 같은 이름의 이전 벌을 지운다, (2) 그 스텝이 실제로 돌 수 있게
 * {@code actions: write} 와 {@code shell: bash} 가 붙어 있다, (3) {@code release.yml} 이 Release 자산과 똑같은
 * zip 을 한 번 더 아티팩트로 올리지 않는다. 셋 다 <b>YAML 한 줄이면 조용히 되돌아간다</b>. 되돌아가도 CI 는 초록이고, 다시 몇 달 뒤 다른 저장소가 먼저
 * 아파야 알게 된다 — 이번에 겪은 그대로다.
 *
 * <p>파일만 읽는다. 브라우저·네트워크·DB 를 쓰지 않는다.
 *
 * @author KIUNSEA
 */
class WorkflowArtifactHygieneTest {

  private static final Path BUILD_WORKFLOW = Path.of(".github", "workflows", "build.yml");
  private static final Path RELEASE_WORKFLOW = Path.of(".github", "workflows", "release.yml");
  private static final Path PRUNE_SCRIPT = Path.of(".github", "scripts", "prune-artifacts.sh");
  private static final Path GIT_ATTRIBUTES = Path.of(".gitattributes");

  /** 정리 스크립트 호출의 표식. 주석이 아니라 {@code run:} 줄에서만 찾는다. */
  private static final String PRUNE_SCRIPT_NAME = "prune-artifacts.sh";

  /** 전역 판정(정렬·자르기)을 jq 안으로 들여오는 형태. 페이지마다 적용돼 조용히 어긋난다. */
  private static final List<String> GLOBAL_ORDERING_IN_JQ =
      List.of("sort", "limit(", "first(", "max_by", "min_by");

  @Test
  @DisplayName("정리가 업로드보다 앞에 있다 — 뒤에 두면 이번 회차 업로드 공간을 못 비운다")
  void keepsThePruneAheadOfTheUpload() throws Exception {
    List<String> steps = stepBlocks(read(BUILD_WORKFLOW));

    int firstPruneAll = -1;
    int firstUpload = -1;
    for (int i = 0; i < steps.size(); i++) {
      String command = pruneCommandOf(steps.get(i));
      if (firstPruneAll < 0 && command != null && "all".equals(pruneMode(command))) {
        firstPruneAll = i;
      }
      if (firstUpload < 0 && uploadsArtifact(steps.get(i))) {
        firstUpload = i;
      }
    }

    // 수집기가 조용히 좁아지면 아래 비교가 통째로 무의미해진다. 먼저 둘 다 찾았는지부터 센다.
    assertTrue(
        firstUpload >= 0,
        "build.yml 에서 upload-artifact 스텝을 하나도 찾지 못했다. 업로드가 없어졌거나 스텝 분해가 깨진 것이다 —"
            + " 어느 쪽이든 이 가드는 아무것도 지키지 않는다.");
    assertTrue(
        firstPruneAll >= 0,
        "build.yml 에 mode=all 사전 정리 스텝이 없다. 이 스텝이 없어서 228벌 7,637MB 가 쌓였고, 계정 단위 quota 를"
            + " 먹어 같은 계정의 다른 저장소 CI 가 3주간 아티팩트를 못 올렸다.");
    assertTrue(
        firstPruneAll < firstUpload,
        "정리 스텝이 업로드 뒤에 있다. 뒤에 두면 **이번 회차 업로드를 위한 공간을 못 비운다** — quota 가 이미 차 있으면"
            + " 그 업로드가 실패하고, 정리는 실패한 뒤에야 돈다. 반드시 업로드 앞에 둬라.");
  }

  @Test
  @DisplayName("올리는 이름은 앞·뒤 정리 스텝 **양쪽** 인자에 들어 있다")
  void keepsEveryUploadedNameUnderPruning() throws Exception {
    String yaml = read(BUILD_WORKFLOW);
    List<String> steps = stepBlocks(yaml);

    // 모드별로 따로 모은다. 두 스텝의 인자를 합쳐서 보면 **공간을 실제로 비우는 사전 정리(all)에서만
    // 이름이 빠진 경우를 놓친다** — 뒤쪽 keep-newest 에 그 이름이 남아 있으면 합집합에는 들어 있기
    // 때문이다. 그런데 뒤쪽은 이미 올리고 난 다음이라 이번 회차의 공간을 비우지 못한다. 즉 합집합으로
    // 보는 순간 이 가드는 정확히 이번 사태의 형태를 통과시킨다(변이 테스트로 실제로 확인했다).
    List<String> uploaded = new ArrayList<>();
    Set<String> prunedByAll = new LinkedHashSet<>();
    Set<String> prunedByKeepNewest = new LinkedHashSet<>();
    for (String step : steps) {
      if (uploadsArtifact(step)) {
        uploaded.add(uploadedArtifactName(step));
      }
      String command = pruneCommandOf(step);
      if (command == null) {
        continue;
      }
      if ("all".equals(pruneMode(command))) {
        prunedByAll.addAll(prunedNames(command));
      } else if ("keep-newest".equals(pruneMode(command))) {
        prunedByKeepNewest.addAll(prunedNames(command));
      }
    }

    // 대조군 없이도 수집기 고장을 잡는다 — 스텝 분해가 깨지면 uploaded 가 조용히 0이 되고,
    // 그러면 아래 검사는 "위반 0건" 으로 통과해 버린다.
    assertEquals(
        countOccurrences(commentFreeYaml(yaml), "uses: actions/upload-artifact@"),
        uploaded.size(),
        "upload-artifact 스텝 수와 이름을 뽑아낸 수가 다르다. 스텝 분해나 이름 추출이 깨졌다는 뜻이라,"
            + " 아래 '정리 대상에 들어 있는가' 검사가 통째로 헛돈다.");

    List<String> offenders = new ArrayList<>();
    for (String name : uploaded) {
      if (!prunedByAll.contains(name)) {
        offenders.add(name + " — 사전 정리(all) 인자에 없다");
      }
      if (!prunedByKeepNewest.contains(name)) {
        offenders.add(name + " — 수렴 가드(keep-newest) 인자에 없다");
      }
    }
    assertTrue(
        offenders.isEmpty(),
        "정리 스텝의 인자 목록에 없는 이름을 올린다. 그 이름만 사정권 밖에서 무한히 쌓인다 — 이번 사태가 정확히 그"
            + " 모양이었다. prune-artifacts.sh 호출 **두 곳 모두**의 뒤에 이름을 추가해라:\n  "
            + String.join("\n  ", offenders));
  }

  @Test
  @DisplayName("정리 스텝은 shell: bash 로 돌고, 실패해도 빌드를 깨지 않는다")
  void keepsEveryPruneStepOnBashAndNonFatal() throws Exception {
    List<String> steps = stepBlocks(read(BUILD_WORKFLOW));
    List<String> offenders = new ArrayList<>();
    Set<String> modes = new LinkedHashSet<>();

    for (String step : steps) {
      String command = pruneCommandOf(step);
      if (command == null) {
        continue;
      }
      modes.add(String.valueOf(pruneMode(command)));
      // runs-on: windows-latest 라 기본 셸이 PowerShell 이다. shell: bash 를 빠뜨리면 PowerShell 이
      // 이 .sh 를 해석하려 들어 스텝이 죽는데, continue-on-error 때문에 초록으로 넘어간다.
      // 즉 정리가 통째로 멈춘 것을 아무도 모르게 된다.
      if (!declaresBashShell(step)) {
        offenders.add(stepTitle(step) + " — shell: bash 가 없다");
      }
      // 정리는 편의 기능이다. gh api 가 한 번 흔들렸다고 빌드 결과가 바뀌면 안 된다.
      if (!declaresContinueOnError(step)) {
        offenders.add(stepTitle(step) + " — continue-on-error: true 가 없다");
      }
    }

    assertTrue(offenders.isEmpty(), "정리 스텝의 실행 조건이 빠졌다:\n  " + String.join("\n  ", offenders));
    assertTrue(
        modes.contains("all") && modes.contains("keep-newest"),
        "정리 스텝이 앞(all)·뒤(keep-newest) 두 벌로 갖춰져 있지 않다. 찾은 모드: "
            + modes
            + ". 앞은 이번 회차 공간을 비우고, 뒤는 두 run 이 겹쳐 끝났을 때 최신 1벌로 수렴시킨다.");
  }

  @Test
  @DisplayName("build.yml 이 actions: write 와 contents: read 를 함께 선언한다")
  void keepsArtifactDeletionPermissionAlongsideCheckout() throws Exception {
    Set<String> permissions = permissionsOf(read(BUILD_WORKFLOW));

    assertTrue(
        permissions.contains("actions: write"),
        "build.yml 에 actions: write 가 없다. 아티팩트 삭제 API 가 403 으로 막히는데, 정리 스텝은"
            + " continue-on-error 라 그대로 초록으로 넘어간다 — 정리가 아무것도 못 하는 것을 아무도 모르게 된다.");
    assertTrue(
        permissions.contains("contents: read"),
        "build.yml 에 contents: read 가 없다. permissions 를 명시하는 순간 **나열하지 않은 스코프는 none 이 된다** —"
            + " 그동안 암묵적으로 쓰던 체크아웃 권한이 사라져 actions/checkout 이 죽는다.");
  }

  @Test
  @DisplayName("정리 스크립트가 만료분을 건너뛰지 않는다")
  void keepsExpiredArtifactsInsideThePruneScope() throws Exception {
    String commands = shellCommandsOf(read(PRUNE_SCRIPT));

    // 주석을 먼저 걷어낸다. 이 스크립트의 주석은 "expired 로 거르지 않는다" 는 이유를 길게 적어
    // 두었는데, 그 설명 자체가 걸리면 통과시키는 방법이 두 가지가 된다(코드를 고치거나, 설명을
    // 지우거나). 뒤쪽은 근거만 사라지고 위험은 그대로다.
    assertFalse(
        commands.contains("expired"),
        "정리 스크립트가 expired 로 아티팩트를 거른다. **만료분이 quota 를 잡는 것이 이번 사태의 원인이다** —"
            + " 만료돼도 GitHub GC 전까지 목록에 남는다(실측: 7,637MB 중 6,046MB 가 만료분,"
            + " 그중엔 retention-days: 7 인데 9개월 넘은 것도 있었다). 거르면 그 누적분이 영영 사정권 밖이 된다.");
  }

  @Test
  @DisplayName("전역 정렬·자르기를 jq 안에서 하지 않는다")
  void keepsGlobalOrderingOutOfThePerPageJqFilter() throws Exception {
    String commands = shellCommandsOf(read(PRUNE_SCRIPT));
    List<String> offenders = new ArrayList<>();

    for (String line : commands.split("\\R")) {
      if (!line.contains("--jq")) {
        continue;
      }
      for (String form : GLOBAL_ORDERING_IN_JQ) {
        if (line.contains(form)) {
          offenders.add(line.trim() + "   (" + form + ")");
        }
      }
    }

    assertTrue(
        offenders.isEmpty(),
        "jq 필터 안에서 전역 판정을 한다. gh 의 --paginate 는 --jq 를 **페이지마다** 적용하므로"
            + " '최신 1벌만 남긴다' 같은 판정을 jq 에 두면 페이지마다 1벌씩 남아 조용히 어긋난다."
            + " 이 저장소는 정리 직전 228벌이라 per_page=100 경계를 실제로 넘었다. 정렬·자르기는 셸에서 해라:\n  "
            + String.join("\n  ", offenders));

    // 위 검사는 "위반 0건" 이면 통과한다 — 정렬을 jq 에서 빼면서 셸 쪽에도 안 넣으면 그대로 초록이
    // 되고, keep-newest 가 아무것도 남기지 않거나 아무것도 지우지 않는 상태가 된다.
    assertTrue(
        commands.contains("sort -r"),
        "셸 쪽 정렬(sort -r)이 사라졌다. jq 에서도 셸에서도 정렬하지 않으면 keep-newest 의 '최신' 판정이"
            + " GitHub API 의 응답 순서에 통째로 의존하게 된다.");
  }

  @Test
  @DisplayName("release.yml 이 Release 자산을 아티팩트로 중복 업로드하지 않는다")
  void keepsTheReleaseWorkflowFreeOfDuplicateArtifactUpload() throws Exception {
    String yaml = commentFreeYaml(read(RELEASE_WORKFLOW));

    assertFalse(
        yaml.contains("actions/upload-artifact@"),
        "release.yml 이 아티팩트를 올린다. 같은 zip 을 Create Release 가 이미 Release 자산으로 올린 직후라"
            + " 완전한 중복이고(실측: v0.7.0·v0.8.0·v0.10.2·v0.18.1·v0.22.0 다섯 벌 509MB 가 전부 Release 에도"
            + " 그대로 있었다), 릴리스마다 102MB 가 90일씩 쌓인다. Release 자산은 저장 quota 를 먹지 않지만"
            + " 아티팩트는 계정 단위 500MB 를 먹는다.");

    // 사본이 불필요한 근거는 Release 자산이 실제로 발행된다는 것이다. 그 스텝까지 사라지면
    // 위 검사는 통과하는데 배포물이 아무 데도 안 남는다.
    assertTrue(
        yaml.contains("softprops/action-gh-release@"),
        "release.yml 에 Release 생성 스텝이 없다. 아티팩트 사본을 지운 근거가 'Release 자산으로 받을 수 있다'"
            + " 였는데, 그 Release 자체가 없으면 배포물이 아무 데도 남지 않는다.");
  }

  @Test
  @DisplayName("셸 스크립트는 LF 로 저장되고 .gitattributes 가 그것을 강제한다")
  void keepsShellScriptsOnLfEndings() throws Exception {
    byte[] bytes = Files.readAllBytes(existing(PRUNE_SCRIPT));
    int carriageReturns = 0;
    for (byte b : bytes) {
      if (b == '\r') {
        carriageReturns++;
      }
    }
    assertEquals(
        0,
        carriageReturns,
        "정리 스크립트에 CR 이 섞여 있다. CRLF 셸 스크립트는 러너에서 $'\\r': command not found 로 죽는다."
            + " 이 job 은 windows-latest 라 편집기가 무심코 CRLF 로 저장하기 쉽다.");

    String attributes = read(GIT_ATTRIBUTES);
    boolean declared = false;
    for (String line : attributes.split("\\R")) {
      String normalized = line.trim().toLowerCase(Locale.ROOT);
      if (normalized.startsWith("#")) {
        continue;
      }
      if (normalized.startsWith("*.sh") && normalized.contains("eol=lf")) {
        declared = true;
      }
    }
    assertTrue(
        declared,
        ".gitattributes 에 `*.sh text eol=lf` 가 없다. 위 CR 검사는 지금 이 체크아웃만 보므로,"
            + " 선언이 빠지면 Windows 에서 clone 한 사람의 작업본이 CRLF 로 바뀌어도 알 수 없다.");
  }

  @Test
  @DisplayName("판별식이 실제 위험 형태는 잡고 정상 워크플로는 통과시킨다")
  void theDetectionRuleItselfCatchesTheDangerousForms() {
    // 대조군이다. 위 검사들은 대부분 "지금 위반이 0건" 이면 통과하므로, 판별식이 통째로 죽어도
    // 초록이 된다 — 이 프로젝트가 실제로 두 번 겪은 형태다(만료 감지가 테스트 25건 초록인 채
    // 호출자 0건이었던 것, 배포 산출물 가드의 판별식을 무력화해도 5건이 전부 통과했던 것).
    // 그래서 저장소 상태가 아니라 판별부에 직접 입력을 넣는다.

    String workflow =
        String.join(
            "\n",
            "jobs:",
            "  build:",
            "    runs-on: windows-latest",
            "    permissions:",
            "      contents: read",
            "      actions: write",
            "    steps:",
            "    - name: Prune build artifacts before upload",
            "      continue-on-error: true",
            "      shell: bash",
            "      run: bash .github/scripts/prune-artifacts.sh \"owner/repo\" all app-jar test-results",
            "      # 주석 안의 uses: actions/upload-artifact@v7 은 스텝이 아니다",
            "    - name: Upload build artifacts",
            "      uses: actions/upload-artifact@v7",
            "      with:",
            "        name: app-jar",
            "        path: build/libs/*.jar");

    List<String> steps = stepBlocks(workflow);
    assertEquals(2, steps.size(), "스텝 분해가 틀렸다. '- name:' 두 개를 두 블록으로 나눠야 한다.");

    // 순서 판정 — 정리가 0번, 업로드가 1번이어야 한다.
    assertEquals("all", pruneMode(pruneCommandOf(steps.get(0))), "mode=all 을 못 읽는다.");
    assertTrue(uploadsArtifact(steps.get(1)), "업로드 스텝을 못 알아본다.");
    assertEquals("app-jar", uploadedArtifactName(steps.get(1)), "아티팩트 이름을 잘못 뽑는다.");
    assertEquals(
        List.of("app-jar", "test-results"),
        prunedNames(pruneCommandOf(steps.get(0))),
        "정리 대상 이름 목록을 잘못 뽑는다. repo·mode 인자를 이름으로 오인하면 검사가 헛돈다.");

    // 실제 워크플로가 쓰는 형태 — ${{ github.repository }} 는 공백을 품고 있어 그냥 자르면 세 토큰이
    // 되고, 모드 인자의 자리가 밀려 all 대신 github.repository 를 모드로 읽는다.
    String realShape =
        "bash .github/scripts/prune-artifacts.sh \"${{ github.repository }}\""
            + " keep-newest jangbogo-jar test-results";
    assertEquals("keep-newest", pruneMode(realShape), "${{ }} 표현식 때문에 모드 인자의 자리가 밀린다.");
    assertEquals(
        List.of("jangbogo-jar", "test-results"),
        prunedNames(realShape),
        "${{ }} 표현식 때문에 이름 목록에 표현식 조각이 섞인다.");

    // 주석은 스텝이 아니다. 걸리기 시작하면 "금지를 설명하는 주석" 을 지워야 통과하게 된다.
    assertFalse(
        uploadsArtifact(steps.get(0)),
        "주석 줄의 upload-artifact 를 실제 업로드로 잘못 본다. 그러면 근거를 적어 둔 주석이 스스로 위반이 된다.");
    assertFalse(
        commentFreeYaml("      # uses: actions/upload-artifact@v7").contains("upload-artifact"),
        "주석 걷어내기가 동작하지 않는다.");
    assertTrue(
        commentFreeYaml("      uses: actions/upload-artifact@v7").contains("upload-artifact"),
        "주석을 걷어내면서 실제 명령까지 지워졌다. 그러면 이 가드는 아무것도 지키지 않는다.");

    // 권한 판정 — 들여쓰기로 블록 끝을 잡는다.
    Set<String> permissions = permissionsOf(workflow);
    assertTrue(permissions.contains("actions: write"), "actions: write 를 못 읽는다.");
    assertTrue(permissions.contains("contents: read"), "contents: read 를 못 읽는다.");
    assertFalse(
        permissionsOf("    permissions:\n      contents: read\n    steps:\n    - name: x")
            .contains("actions: write"),
        "permissions 블록 밖까지 읽는다. 블록이 끝나는 지점을 들여쓰기로 잡아야 한다.");

    // 셸·실패내성 판정.
    assertTrue(declaresBashShell(steps.get(0)), "shell: bash 를 못 알아본다.");
    assertFalse(
        declaresBashShell("    - name: x\n      run: bash prune-artifacts.sh"),
        "shell 선언이 없는데 있다고 본다 — windows 러너에서 PowerShell 로 돌아 죽는 형태다.");
    assertTrue(declaresContinueOnError(steps.get(0)), "continue-on-error 를 못 알아본다.");
    assertFalse(
        declaresContinueOnError("    - name: x\n      continue-on-error: false"),
        "continue-on-error: false 를 true 로 본다.");

    // 정리 호출 판정 — run: 줄에서만 찾는다.
    assertEquals(
        null,
        pruneCommandOf("    - name: x\n      # prune-artifacts.sh 를 여기서 부르곤 했다"),
        "주석에 적힌 스크립트 이름을 호출로 본다.");
    assertEquals(
        null, pruneCommandOf("    - name: x\n      run: ./gradlew test"), "관계없는 run 을 정리 호출로 본다.");
    assertEquals(
        null,
        pruneMode("bash .github/scripts/prune-artifacts.sh \"owner/repo\""),
        "모드 인자가 없는데 모드를 읽어 낸다.");
  }

  // ---------------------------------------------------------------
  // 판별부 — 대조군이 직접 두들길 수 있도록 함수로 둔다.
  // 검사 본문에 붙여 두면 판별식을 죽여도 아무 테스트가 안 깨진다.
  // ---------------------------------------------------------------

  /**
   * 워크플로 YAML 을 스텝 단위로 나눈다.
   *
   * <p>{@code - name:} 으로 시작하는 줄이 스텝의 첫 줄이다. 첫 스텝 앞의 머리말({@code on:}·{@code permissions:} 등)은 버린다.
   */
  private static List<String> stepBlocks(String workflowYaml) {
    List<String> blocks = new ArrayList<>();
    StringBuilder current = null;
    for (String line : workflowYaml.split("\\R")) {
      if (line.trim().startsWith("- name:")) {
        if (current != null) {
          blocks.add(current.toString());
        }
        current = new StringBuilder();
      }
      if (current != null) {
        current.append(line).append('\n');
      }
    }
    if (current != null) {
      blocks.add(current.toString());
    }
    return blocks;
  }

  /** 스텝 제목({@code - name:} 뒤). 위반을 사람이 찾아갈 수 있게 쓴다. */
  private static String stepTitle(String stepBlock) {
    for (String line : stepBlock.split("\\R")) {
      String trimmed = line.trim();
      if (trimmed.startsWith("- name:")) {
        return trimmed.substring("- name:".length()).trim();
      }
    }
    return "(이름 없는 스텝)";
  }

  /** 이 스텝이 {@code run:} 으로 정리 스크립트를 부르면 그 명령줄, 아니면 null. 주석은 보지 않는다. */
  private static String pruneCommandOf(String stepBlock) {
    for (String line : stepBlock.split("\\R")) {
      String trimmed = line.trim();
      if (trimmed.startsWith("run:") && trimmed.contains(PRUNE_SCRIPT_NAME)) {
        return trimmed.substring("run:".length()).trim();
      }
    }
    return null;
  }

  /** 정리 명령의 모드 인자({@code all}·{@code keep-newest}). 못 읽으면 null. */
  private static String pruneMode(String pruneCommand) {
    List<String> arguments = argumentsAfterScript(pruneCommand);
    return arguments.size() >= 2 ? arguments.get(1) : null;
  }

  /** 정리 명령이 넘기는 아티팩트 이름들(repo·mode 뒤의 나머지 인자). */
  private static List<String> prunedNames(String pruneCommand) {
    List<String> arguments = argumentsAfterScript(pruneCommand);
    return arguments.size() > 2 ? List.copyOf(arguments.subList(2, arguments.size())) : List.of();
  }

  /**
   * 스크립트 경로 뒤의 인자들.
   *
   * <p>{@code ${{ github.repository }}} 를 <b>먼저 한 토큰으로 접는다.</b> 공백으로 그냥 자르면 이 표현식 하나가 세 토큰으로 갈라져 모드
   * 인자의 자리가 밀리고, 그러면 {@code all} 대신 {@code github.repository} 를 모드로 읽어 검사가 통째로 헛돈다 — 실제 워크플로가 쓰는
   * 형태라 대조군에서 반드시 함께 두들긴다.
   */
  private static List<String> argumentsAfterScript(String pruneCommand) {
    if (pruneCommand == null) {
      return List.of();
    }
    String collapsed = pruneCommand.replaceAll("\\$\\{\\{[^}]*\\}\\}", "GITHUB_EXPRESSION");
    List<String> arguments = new ArrayList<>();
    boolean afterScript = false;
    for (String token : collapsed.trim().split("\\s+")) {
      if (!afterScript) {
        afterScript = token.endsWith(PRUNE_SCRIPT_NAME);
        continue;
      }
      String unquoted = token.replace("\"", "").replace("'", "").trim();
      if (!unquoted.isEmpty()) {
        arguments.add(unquoted);
      }
    }
    return arguments;
  }

  /** 이 스텝이 실제로 아티팩트를 올리는지. 주석에 적힌 액션 이름은 세지 않는다. */
  private static boolean uploadsArtifact(String stepBlock) {
    for (String line : stepBlock.split("\\R")) {
      String trimmed = line.trim();
      if (trimmed.startsWith("uses:") && trimmed.contains("actions/upload-artifact@")) {
        return true;
      }
    }
    return false;
  }

  /** 업로드 스텝의 {@code with.name}. 스텝 제목({@code - name:})과 구별해 뽑는다. */
  private static String uploadedArtifactName(String stepBlock) {
    for (String line : stepBlock.split("\\R")) {
      String trimmed = line.trim();
      if (trimmed.startsWith("name:")) {
        return trimmed.substring("name:".length()).trim();
      }
    }
    return "(이름 없음)";
  }

  /** 이 스텝이 {@code shell: bash} 를 명시하는지. windows 러너의 기본 셸은 PowerShell 이다. */
  private static boolean declaresBashShell(String stepBlock) {
    for (String line : stepBlock.split("\\R")) {
      if (line.trim().equals("shell: bash")) {
        return true;
      }
    }
    return false;
  }

  /** 이 스텝이 실패해도 빌드를 깨지 않는지. */
  private static boolean declaresContinueOnError(String stepBlock) {
    for (String line : stepBlock.split("\\R")) {
      if (line.trim().equals("continue-on-error: true")) {
        return true;
      }
    }
    return false;
  }

  /**
   * {@code permissions:} 블록이 선언한 스코프들({@code "actions: write"} 형태).
   *
   * <p>블록의 끝은 들여쓰기로 잡는다 — {@code permissions:} 보다 얕거나 같은 줄이 나오면 끝이다. 그러지 않으면 뒤따르는 {@code steps:}
   * 전체를 권한 선언으로 읽어 어떤 스코프든 "있다" 고 답하게 된다.
   */
  private static Set<String> permissionsOf(String workflowYaml) {
    List<String> lines = List.of(workflowYaml.split("\\R"));
    Set<String> scopes = new LinkedHashSet<>();
    for (int i = 0; i < lines.size(); i++) {
      if (!lines.get(i).trim().equals("permissions:")) {
        continue;
      }
      int baseIndent = indentOf(lines.get(i));
      for (int j = i + 1; j < lines.size(); j++) {
        String line = lines.get(j);
        if (line.isBlank()) {
          continue;
        }
        if (indentOf(line) <= baseIndent) {
          break;
        }
        String trimmed = line.trim();
        if (!trimmed.startsWith("#")) {
          scopes.add(trimmed.toLowerCase(Locale.ROOT));
        }
      }
    }
    return scopes;
  }

  /** 줄 앞 공백 수. */
  private static int indentOf(String line) {
    int indent = 0;
    while (indent < line.length() && line.charAt(indent) == ' ') {
      indent++;
    }
    return indent;
  }

  /**
   * YAML 에서 주석 줄을 걷어낸다.
   *
   * <p>가드가 설명 문구에 걸리면 "무엇을 하지 말라" 고 적어 둔 근거를 지우는 것으로 통과시킬 수 있게 된다. 그러면 위험은 그대로인데 이유만 사라진다 — 이 저장소는
   * 실제로 그 형태를 겪었다({@code BuildScriptHygieneTest} 참고).
   */
  private static String commentFreeYaml(String workflowYaml) {
    StringBuilder kept = new StringBuilder();
    for (String line : workflowYaml.split("\\R")) {
      if (!line.trim().startsWith("#")) {
        kept.append(line).append('\n');
      }
    }
    return kept.toString();
  }

  /** 셸 스크립트에서 주석과 shebang 을 걷어내고 실행되는 줄만 남긴다. */
  private static String shellCommandsOf(String script) {
    StringBuilder commands = new StringBuilder();
    for (String line : script.split("\\R")) {
      String trimmed = line.trim();
      if (trimmed.startsWith("#")) {
        continue;
      }
      // 줄 끝 주석도 걷어낸다 — `set +e # 이유...` 같은 형태가 실제로 있다.
      int comment = line.indexOf(" #");
      commands.append(comment >= 0 ? line.substring(0, comment) : line).append('\n');
    }
    return commands.toString();
  }

  private static int countOccurrences(String haystack, String needle) {
    int count = 0;
    int at = haystack.indexOf(needle);
    while (at >= 0) {
      count++;
      at = haystack.indexOf(needle, at + needle.length());
    }
    return count;
  }

  private static String read(Path path) throws IOException {
    return Files.readString(existing(path), StandardCharsets.UTF_8);
  }

  private static Path existing(Path path) {
    assertTrue(
        Files.isRegularFile(path),
        path.toString().replace('\\', '/') + " 가 없다. 테스트 작업 디렉터리는 프로젝트 루트여야 한다.");
    return path;
  }
}

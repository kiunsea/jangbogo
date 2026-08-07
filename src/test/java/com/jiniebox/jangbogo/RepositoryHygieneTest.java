package com.jiniebox.jangbogo;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * 자격증명·세션이 PUBLIC 저장소로 새지 않게 하는 {@code .gitignore} 규칙 감시와, 소스 트리에 백업·임시 파일이 눌러앉지 않게 하는 감시.
 *
 * <h2>왜 테스트로 묶는가</h2>
 *
 * <p>이 저장소는 PUBLIC 이다. 아래 규칙들은 <b>한 줄이 사라지면 그 순간부터 조용히 새기 시작하고</b>, 커밋되고 나면 이력에 남아 되돌릴 수 없다. 실제로 이
 * 프로젝트는 죽은 빌드 선언 하나 때문에 특정 PC 절대경로와 비공개 저장소명이 공개 이력에 남은 전례가 있다.
 *
 * <p>특히 {@code profiles/} 는 <b>디렉터리가 아직 생기지도 않은 시점에 미리 막아 둔 것</b>이라, 눈에 보이는 파일이 없어서 "쓰지 않는 규칙"으로
 * 오해되고 지워지기 쉽다. Phase 5(프로필 재사용)가 그 폴더를 만들면 사람이 한 번 로그인한 세션 — 쿠키와 토큰 — 이 그대로 들어간다.
 *
 * <p>파일만 읽는다. 브라우저·네트워크·DB 를 쓰지 않는다.
 *
 * @author KIUNSEA
 */
class RepositoryHygieneTest {

  private static final Path GITIGNORE = Path.of(".gitignore");

  /** 지워지면 자격증명·세션·구매내역이 새는 규칙들. 값은 {@code .gitignore} 의 패턴 그대로다. */
  private static final List<String> MUST_IGNORE =
      List.of(
          "profiles/", // 실로그인 Chrome 세션 (Phase 5)
          ".locks/", // 프로필 단위 락 — 런타임 상태이고 지우지 않는 규칙이라 쌓인다
          "config/mall_account.yml", // 쇼핑몰 계정
          "config/admin.properties", // 관리자 자격증명
          "db/*.db", // 구매내역 + jbg_mall 의 암호화 키
          "db/backup/", // 위 DB 의 백업 (확장자가 제각각이라 위 패턴에 안 걸린다)
          "logs/"); // 수집 로그 (DEBUG 에 구매 상세가 남는다)

  @Test
  @DisplayName("자격증명·세션이 담기는 경로는 전부 무시 대상이다")
  void keepsEveryCredentialBearingPathIgnored() throws Exception {
    assertTrue(Files.isRegularFile(GITIGNORE), ".gitignore 가 없다. 테스트 작업 디렉터리는 프로젝트 루트여야 한다.");
    List<String> lines =
        Files.readAllLines(GITIGNORE, StandardCharsets.UTF_8).stream()
            .map(String::trim)
            .filter(line -> !line.isEmpty() && !line.startsWith("#"))
            .toList();

    for (String pattern : MUST_IGNORE) {
      assertTrue(
          lines.contains(pattern),
          ".gitignore 에서 '" + pattern + "' 규칙이 사라졌다. 이 저장소는 PUBLIC 이고, 한 번 커밋되면 이력에 남아 되돌릴 수 없다.");
    }
  }

  @Test
  @DisplayName("세션 인계 계획서는 커밋하지 않는다")
  void keepsInternalPlanDocsOutOfTheRepository() throws Exception {
    String gitignore = Files.readString(GITIGNORE, StandardCharsets.UTF_8);

    // 정본은 비공개 저장소에 있다. 여기서는 패턴으로 막아 앞으로 만들 계획서도 기본적으로 걸리게 한다.
    assertTrue(
        gitignore.contains("doc/PLAN-*.md"),
        "doc/PLAN-*.md 규칙이 사라졌다. 세션 인계 문서에는 운영 환경 세부와 미완 작업이 섞인다.");
  }

  // ---------------------------------------------------------------
  // 소스 트리의 백업·임시 파일
  // ---------------------------------------------------------------

  /**
   * 백업·임시 파일로 읽히는 이름의 꼬리표.
   *
   * <p>{@code ~} 는 편집기 백업, {@code .swp}/{@code .swo} 는 vim 스왑, {@code .orig}/{@code .rej} 는 병합·패치
   * 찌꺼기, 나머지는 사람이 손으로 붙이는 이름이다.
   */
  private static final List<String> BACKUP_SUFFIXES =
      List.of(
          ".bak", ".backup", ".orig", ".rej", ".tmp", ".temp", ".old", ".save", ".swp", ".swo",
          "~");

  /** 백업·임시 파일 검사 대상. 소스 트리 전체다 — 자바든 리소스든 템플릿이든 백업본이 남을 이유가 없다. */
  private static final Path SRC_ROOT = Path.of("src");

  /** 자바 소스만 있어야 하는 운영 소스 루트. */
  private static final Path MAIN_JAVA_ROOT = Path.of("src/main/java");

  @Test
  @DisplayName("소스 트리에 백업·임시 파일이 남아 있지 않다")
  void keepsBackupAndTemporaryFilesOutOfTheSourceTree() throws Exception {
    // 실제로 있었던 일이다. src/main/java/.../dao/JbgAccessDataAccessObject.java.bak 이
    // 2025-12-18 통합 때 남아 PUBLIC 저장소에 커밋된 채로 있었다. 두 가지가 동시에 나쁘다.
    //
    //  1) 모든 소스 형태 가드가 이 파일을 건너뛰었다. SecurityHardeningTest 의 dao 스캔이
    //     ".java 로 끝나는 파일" 만 걸렀기 때문이다. 그 안에는 값을 그대로 WHERE 절에 이어 붙인
    //     자리가 여섯 곳 있었고 "dao 패키지 전수 스캔" 이라는 가드의 주장이 사실이 아니었다.
    //     확장자 하나만 바꿔 두면 어떤 가드든 통째로 우회된다 — 이게 핵심이다.
    //  2) 죽은 코드가 공개돼 있었다. 이번 건은 자격증명이 없어 유출은 아니었지만, 백업은 보통
    //     "고치기 전 상태" 라서 방금 제거한 문제를 그대로 담고 있다.
    //
    // 그래서 확장자 목록을 넓히는 것으로 끝내지 않고, 백업 파일 자체가 트리에 못 들어오게 막는다.
    // 가드가 스스로 가드를 우회당하지 않게 하는 층이다.
    List<String> offenders = new ArrayList<>();

    if (Files.isDirectory(SRC_ROOT)) {
      try (Stream<Path> files = Files.walk(SRC_ROOT)) {
        for (Path file : files.filter(Files::isRegularFile).toList()) {
          // 대소문자를 맞춰 비교한다. 개발 PC 가 Windows 라 파일시스템이 .BAK 와 .bak 을 같은 이름으로
          // 취급하는데, 여기서 대소문자를 따지면 .BAK 로 남긴 것만 조용히 빠져나간다.
          String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
          for (String suffix : BACKUP_SUFFIXES) {
            if (name.endsWith(suffix)) {
              offenders.add(file.toString().replace('\\', '/'));
              break;
            }
          }
        }
      }
    }

    assertTrue(
        offenders.isEmpty(),
        "소스 트리에 백업·임시 파일이 있다. 두 가지가 동시에 문제다 — (1) 확장자가 .java 가 아니라서 소스 형태를 보는 보안 가드가"
            + " 전부 이 파일을 건너뛴다(전수 스캔이라는 주장이 거짓이 된다), (2) 이 저장소는 PUBLIC 이라 죽은 코드가 그대로 공개된다."
            + " 백업이 필요하면 저장소 밖에 두고, 필요 없으면 지워라:\n  "
            + String.join("\n  ", offenders));
  }

  @Test
  @DisplayName("운영 소스 루트에는 자바 파일만 있다")
  void keepsMainJavaRootFreeOfNonJavaFiles() throws Exception {
    // 위 검사는 "알고 있는 꼬리표" 만 잡는다. 목록에 없는 이름으로 남기면 — Foo.java.20251218,
    // Foo.java.merge — 그대로 빠져나간다. 목록을 늘리는 것으로는 끝이 나지 않는다.
    //
    // src/main/java 는 그 문제를 목록 없이 막을 수 있는 자리다. 여기에 자바 소스가 아닌 파일이
    // 있을 이유가 없고(리소스는 src/main/resources 로 간다), 그래서 "무엇을 금지할지" 가 아니라
    // "무엇만 허용할지" 로 쓸 수 있다. 허용 목록은 이름이 하나뿐이라 늘어나지 않는다.
    //
    // src/test/java 에는 FTP 수동 테스트용 README·실행 스크립트가 함께 있어 같은 규칙을 못 쓴다.
    // 대신 그쪽은 위의 꼬리표 검사가 맡는다. 운영 산출물에 실리는 쪽만 엄격히 간다.
    List<String> offenders = new ArrayList<>();

    if (Files.isDirectory(MAIN_JAVA_ROOT)) {
      try (Stream<Path> files = Files.walk(MAIN_JAVA_ROOT)) {
        for (Path file : files.filter(Files::isRegularFile).toList()) {
          if (!file.getFileName().toString().endsWith(".java")) {
            offenders.add(file.toString().replace('\\', '/'));
          }
        }
      }
    }

    assertTrue(
        offenders.isEmpty(),
        "src/main/java 아래에 자바 소스가 아닌 파일이 있다. 확장자가 .java 가 아닌 파일은 소스 형태를 보는 가드가 전부 건너뛰므로,"
            + " 이 자리는 어떤 검사도 닿지 않는 사각지대가 된다. 리소스는 src/main/resources 로, 문서는 doc/ 로 옮겨라:\n  "
            + String.join("\n  ", offenders));
  }

  @Test
  @DisplayName("백업·임시 파일 패턴이 .gitignore 에 있다")
  void keepsBackupPatternsIgnored() throws Exception {
    // 위 두 검사는 이미 들어온 파일을 잡는다. .gitignore 는 애초에 스테이징되지 않게 막는다 —
    // 두 층이 다 있어야 "실수로 add . 했다" 가 사고로 이어지지 않는다.
    List<String> lines =
        Files.readAllLines(GITIGNORE, StandardCharsets.UTF_8).stream()
            .map(String::trim)
            .filter(line -> !line.isEmpty() && !line.startsWith("#"))
            .toList();

    for (String pattern : List.of("*.bak", "*.orig", "*.rej", "*.tmp", "*.old", "*~")) {
      assertTrue(
          lines.contains(pattern),
          ".gitignore 에서 '"
              + pattern
              + "' 규칙이 사라졌다. 백업 파일은 확장자 때문에 소스 형태 가드를 통째로 우회하고, 이 저장소는 PUBLIC 이라 그대로 공개된다.");
    }
  }
}

package com.jiniebox.jangbogo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
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

  /**
   * 지워지면 자격증명·세션·구매내역이 새는 규칙들. 값은 {@code .gitignore} 의 패턴 그대로다.
   *
   * <p>아래 목록은 {@link #PERSONAL_DATA_DIRECTORIES}·{@link #PERSONAL_DATA_SUFFIXES}·{@link
   * #PERSONAL_DATA_FILES} 와 같은 대상을 가리킨다. 두 층은 역할이 다르다 — 여기(무시 규칙)는 <b>애초에 스테이징되지 않게</b> 막고, 저쪽(추적
   * 파일 검사)은 <b>이미 추적이 시작된 것</b>을 잡는다. git 은 이미 추적 중인 파일에는 무시 규칙을 적용하지 않으므로 한쪽만으로는 구멍이 남는다.
   */
  private static final List<String> MUST_IGNORE =
      List.of(
          "profiles/", // 실로그인 Chrome 세션 (Phase 5)
          ".locks/", // 프로필 단위 락 — 런타임 상태이고 지우지 않는 규칙이라 쌓인다
          "config/mall_account.yml", // 쇼핑몰 계정
          "config/admin.properties", // 관리자 자격증명
          "*.db", // 구매내역 + jbg_mall 의 암호화 키 (db/ 밖에 생겨도)
          "*.db-wal", // SQLite 가 DB 옆에 따로 만드는 것들. 내용은 같은 개인 데이터다
          "*.db-shm",
          "*.sqlite", // 도구·스크립트가 다른 확장자로 만들어 두는 경우
          "*.sqlite3",
          "db/", // db/ 아래는 확장자와 무관하게 전부
          "db/*.db", // 구매내역 + jbg_mall 의 암호화 키
          "db/backup/", // 위 DB 의 백업 (확장자가 제각각이라 위 패턴에 안 걸린다)
          // 자격증명 사본이 쌓이는 곳이다. 파일명 패턴(mall_account_*.yml)만 막으면 손으로 붙인
          // 다른 이름이 그대로 빠져나간다 — 아래 추적 파일 검사는 이 폴더를 통째로 보는데
          // 무시 규칙만 좁으면 두 층이 어긋나, 실제로 -f 없이 스테이징되는 것을 확인했다.
          "config/backup/",
          "logs/", // 수집 로그 (DEBUG 에 구매 상세가 남는다)
          "*.log", // logs/ 밖으로 떨어진 로그
          "exports/"); // 내보낸 구매내역 CSV/XLSX — 파일 하나가 곧 구매 이력 전체다

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

  // ---------------------------------------------------------------
  // git 이 실제로 추적 중인 파일
  // ---------------------------------------------------------------

  /** git 저장소 표식. worktree·submodule 에서는 디렉터리가 아니라 파일이므로 존재 여부만 본다. */
  private static final Path GIT_MARKER = Path.of(".git");

  /**
   * 개인 데이터만 담기는 디렉터리. 이 아래는 <b>확장자와 무관하게</b> 전부 걸린다.
   *
   * <p>확장자 목록만으로 거르면 {@code db/jangbogo-dev.db-journal}, {@code exports/2026-08.csv}, {@code
   * logs/screenshots/ssg-01.png}(수집 실패 화면 캡처 — 주문 목록이 그대로 찍힌다) 가 전부 빠져나간다. 목록을 늘려 쫓는 방식은 끝이 나지
   * 않으므로, 커밋할 것이 애초에 없는 디렉터리는 통째로 막는다.
   *
   * <p>경로 어디에 나와도 걸리게 한다. 배포본을 저장소 트리 안에서 압축 해제하면 {@code dist/Jangbogo/db/} 처럼 한 단계 아래에 같은 구조가 그대로
   * 생기고, 그 자리에서 앱을 실행하면 런타임이 DB 를 만든다.
   */
  private static final List<String> PERSONAL_DATA_DIRECTORIES =
      List.of("db/", "logs/", "exports/", "config/backup/", "profiles/");

  /** 개인 데이터 파일의 확장자. 어느 디렉터리에 있든 걸린다. */
  private static final List<String> PERSONAL_DATA_SUFFIXES =
      List.of(".db", ".db-wal", ".db-shm", ".sqlite", ".sqlite3", ".log");

  /**
   * 자격증명이 든 설정 파일. <b>이름이 정확히 일치할 때만</b> 걸린다.
   *
   * <p>{@code startsWith}/{@code contains} 로 보면 저장소에 실재하는 {@code config/mall_account.yml.example} 이
   * 함께 걸린다. 템플릿은 값이 비어 있고, 사용자가 어떤 키를 채워야 하는지 알려면 커밋돼 있어야 하는 파일이다. 오탐으로 초록이 깨지면 사람은 가드를 고치는 대신 느슨하게
   * 만든다 — 그게 가드가 죽는 가장 흔한 경로다.
   */
  private static final List<String> PERSONAL_DATA_FILES =
      List.of("config/mall_account.yml", "config/admin.properties");

  /** 걸리면 안 되는 이름들. 셋 다 저장소에 실재하며 커밋돼야 하는 템플릿이다. */
  private static final List<String> SAFE_CONFIG_TEMPLATES =
      List.of(
          "config/mall_account.yml.example",
          "config/admin.properties.example",
          "config/jbg_config.yml.example");

  /** 반드시 걸려야 하는 이름들. 판별식이 좁아졌는지 보는 대조군이다. */
  private static final List<String> PERSONAL_DATA_SAMPLES =
      List.of(
          "config/mall_account.yml",
          "config/admin.properties",
          "db/jangbogo-dev.db",
          "db/jangbogo-dev.db-shm",
          "db/README.md", // 확장자가 달라도 db/ 아래면 걸린다
          "logs/jangbogo.log",
          "logs/screenshots/ssg-20260807.png",
          "exports/orders-202608.csv",
          "dist/Jangbogo/db/jangbogo.db"); // 배포본을 트리 안에서 푼 경우

  @Test
  @DisplayName("git 이 추적 중인 파일에 개인 데이터가 없다")
  void keepsPersonalDataOutOfTrackedFiles() throws Exception {
    // 위의 두 가드는 '.gitignore 에 규칙이 있는가' 와 '소스 트리에 백업 파일이 있는가' 만 본다.
    // 둘 다 통과하면서 개인 데이터가 커밋돼 있을 수 있다 — git 은 이미 추적 중인 파일에는 무시 규칙을
    // 적용하지 않기 때문이다. 규칙을 넣기 전에 한 번 add 된 파일은 .gitignore 를 아무리 고쳐도 계속 따라온다.
    //
    // 가정이 아니라 실제로 겪은 일이다 — 개발용 DB 가 이 PUBLIC 저장소에 커밋된 적이 있다. 그 DB 에는
    // 개발자 본인의 구매 내역과 jbg_mall 의 계정 암호화 키가 들어 있다. 지금은 .gitignore 도 있고 파일도
    // 제거됐지만, 당시 이것을 잡아 줄 검사가 하나도 없었다. 이 테스트가 그 검사다.
    //
    // 파일시스템을 걷지 않고 git 에게 묻는 이유: 여기서 문제가 되는 것은 '디스크에 있는가' 가 아니라
    // '저장소에 실려 나가는가' 다. 개발용 DB 는 개발 중 늘 디스크에 있고 있어야 정상이며, 유일하게
    // 문제인 상태는 그것이 git 의 인덱스에 들어간 순간이다.
    List<String> offenders = new ArrayList<>();
    for (String path : listTrackedFiles()) {
      if (looksLikePersonalData(path)) {
        offenders.add(path);
      }
    }

    assertTrue(
        offenders.isEmpty(),
        "git 이 추적 중인 파일에 개인 데이터가 있다. 이 저장소는 PUBLIC 이고, 이 프로그램이 다루는 것은 공공 데이터가 아니라"
            + " 프로그램을 실행한 사람의 개인 구매 내역이다 — 쇼핑몰 계정과 그 암호화 키까지 같은 DB 에 있다."
            + " 한 번 push 되면 나중에 파일을 지워도 이력에 남아 회수할 수 없다(이 저장소가 이미 겪은 일이다)."
            + " 지금 추적에서 빼고(git rm --cached <파일>) .gitignore 에 해당 규칙이 있는지 확인해라:\n  "
            + String.join("\n  ", offenders));
  }

  @Test
  @DisplayName("설정 예시 파일은 통과시키고 실제 개인 데이터만 잡는다")
  void separatesExampleTemplatesFromRealPersonalData() {
    // 판별식만 따로 본다. git 없이도 돌고, 저장소가 깨끗한 동안에도 판별식이 살아 있는지 확인된다.
    for (String template : SAFE_CONFIG_TEMPLATES) {
      assertFalse(
          looksLikePersonalData(template),
          template
              + " 는 값이 비어 있는 템플릿인데 개인 데이터로 걸렸다. 판별식이 이름을 정확히 비교하지 않고 startsWith/contains"
              + " 로 보고 있다는 뜻이다. 이대로 두면 정상 커밋 때마다 초록이 깨지고, 그러면 가드가 느슨해지거나 꺼진다.");
    }

    // 이 대조군이 없으면 판별식이 아무것도 안 잡게 바뀌어도 위 검사와 추적 파일 검사가 둘 다 통과한다.
    // '가드가 죽었는데 초록' 이 가장 위험한 상태다.
    for (String sample : PERSONAL_DATA_SAMPLES) {
      assertTrue(
          looksLikePersonalData(sample),
          sample + " 를 개인 데이터로 잡지 못했다. 판별식이 좁아졌다 — 이 상태면 추적 파일 검사가 통과해도 아무것도 확인하지 않은 것이다.");
    }
  }

  /** 추적 경로 하나가 개인 데이터로 읽히는지 본다. 경로 구분자가 {@code /} 로 통일된 값을 받는다. */
  private static boolean looksLikePersonalData(String path) {
    // 대소문자를 무시한다. 개발 PC 가 Windows 라 .DB 와 .db 가 같은 파일인데, 여기서 대소문자를 따지면
    // 대문자로 남긴 것만 조용히 빠져나간다.
    String lower = path.toLowerCase(Locale.ROOT);

    for (String directory : PERSONAL_DATA_DIRECTORIES) {
      if (lower.startsWith(directory) || lower.contains("/" + directory)) {
        return true;
      }
    }
    for (String suffix : PERSONAL_DATA_SUFFIXES) {
      if (lower.endsWith(suffix)) {
        return true;
      }
    }
    return PERSONAL_DATA_FILES.contains(lower);
  }

  /**
   * git 이 실제로 추적 중인 파일 목록. 저장소 루트 기준 상대경로이고 구분자는 {@code /} 다.
   *
   * <p>{@code -z} 를 쓴다. 기본 출력은 경로에 한글·공백이 있으면 따옴표로 감싸고 이스케이프하는데, 그러면 여기서 다시 풀어야 하고 한 글자만 어긋나도
   * 오탐·미탐이 된다. {@code -z} 는 NUL 로만 끊고 이름을 가공하지 않는다.
   *
   * <p>git 을 돌리지 못하면 <b>건너뛰지 않고 실패시킨다</b>. 조용히 통과시키면 "개인 데이터가 없다" 와 "확인하지 못했다" 가 구분되지 않고, CI 이미지에서
   * git 이 빠진 날부터 이 검사는 아무 일도 하지 않으면서 초록만 낸다. 가드가 죽은 것을 아무도 모르는 상태가 가드가 없는 것보다 나쁘다.
   */
  private static List<String> listTrackedFiles() throws Exception {
    Assumptions.assumeTrue(
        Files.exists(GIT_MARKER),
        ".git 이 없어 추적 파일을 확인할 수 없다. 소스 압축본을 풀어 빌드한 경우이며, 이 트리에는 git 이력 자체가 없으므로"
            + " 이 가드가 막으려는 사고(개인 데이터가 커밋돼 이력에 남는 것)도 일어날 수 없다."
            + " clone 한 트리에서는 건너뛰지 않는다.");

    Process process;
    try {
      process = new ProcessBuilder("git", "ls-files", "-z").start();
    } catch (IOException e) {
      return fail(cannotVerifyMessage("git 실행 파일을 찾지 못했다: " + e.getMessage()));
    }

    // stdout 을 먼저 끝까지 비운다. 목록이 수백 개라 파이프 버퍼를 넘기므로, 여기서 안 읽고 waitFor 부터
    // 부르면 git 은 쓰다 막히고 이쪽은 기다리다 막혀 교착된다. stderr 는 오류 몇 줄이라 버퍼 안에서 끝난다.
    byte[] stdout;
    try (InputStream out = process.getInputStream()) {
      stdout = out.readAllBytes();
    }
    if (!process.waitFor(60, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      return fail(cannotVerifyMessage("git ls-files 가 60초 안에 끝나지 않았다."));
    }

    int exitCode = process.exitValue();
    if (exitCode != 0) {
      String stderr;
      try (InputStream err = process.getErrorStream()) {
        stderr = new String(err.readAllBytes(), StandardCharsets.UTF_8).trim();
      }
      return fail(cannotVerifyMessage("git ls-files 가 " + exitCode + " 로 끝났다: " + stderr));
    }

    List<String> tracked = new ArrayList<>();
    for (String path : new String(stdout, StandardCharsets.UTF_8).split("\0")) {
      if (!path.isBlank()) {
        tracked.add(path);
      }
    }

    // 빈 목록이 나오면 성공한 것처럼 보이지만 검사는 아무것도 하지 않은 것이다. 작업 디렉터리가 저장소 밖으로
    // 바뀌었거나 인덱스를 못 읽은 상태이므로, 통과가 아니라 실패로 다뤄야 한다.
    assertFalse(tracked.isEmpty(), cannotVerifyMessage("git ls-files 가 성공했는데 추적 파일이 하나도 없다고 한다."));
    return tracked;
  }

  /** git 을 돌리지 못했을 때의 실패 메시지. 왜 건너뛰지 않고 실패시키는지를 함께 적는다. */
  private static String cannotVerifyMessage(String cause) {
    return "추적 파일 검사를 돌리지 못했다 — "
        + cause
        + "\n.git 이 있는데 git 을 돌리지 못했다는 것은 '개인 데이터가 없다' 가 아니라 '확인하지 못했다' 는 뜻이다."
        + " 이 저장소는 PUBLIC 이고 다루는 것이 사람의 개인 구매 내역이라, 확인하지 못한 채 초록을 내면 가드가 없는 것과 같다."
        + " git 을 PATH 에 두고 다시 돌려라. 이력이 없는 트리라면 .git 이 없을 것이고, 그때는 건너뛴다.";
  }
}

package com.jiniebox.jangbogo.packaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 배포 산출물에 <b>개발자 개인 데이터</b>가 실리지 않게 하는 감시 (G2).
 *
 * <h2>배경 — 왜 이 층이 따로 필요한가</h2>
 *
 * <p>이 저장소는 PUBLIC 이고, 이 프로그램이 다루는 것은 프로그램을 실행하는 사람의 <b>개인 구매 내역</b>이다. 공공 데이터가 아니다. 개발 중 유입된 데이터는
 * 개발자 개인의 것이며 배포본에 들어가면 그대로 공개되고, 한 번 나가면 회수할 수 없다.
 *
 * <p>지금 배포 경로는 안전하다 — v0.18.1 실측으로 릴리스 ZIP 300 개 엔트리에 {@code db}·{@code exports}·{@code
 * logs}·{@code config} 가 하나도 없다. 그런데 그것은 {@code packageDist} 가 담을 대상을 <b>파일명으로 열거</b>하기 때문에 생긴 결과일
 * 뿐이다. 누가 편의를 위해 {@code from(projectDir)} 같은 넓은 지정을 하나 더 붙이는 순간 조용히 새고, 그때 알려 줄 것이 아무 것도 없다.
 *
 * <p>그래서 두 층으로 본다.
 *
 * <ol>
 *   <li><b>소스 형태</b> — {@code build.gradle} 의 {@code packageDist} 안에 산출물 검증이 살아 있는지. 검증은 사람이 "빌드가
 *       느려진다"·"오탐이다" 같은 이유로 지우기 쉬운데, 지워져도 빌드는 초록이라 아무도 모른다.
 *   <li><b>실물</b> — 이미 만들어진 {@code build/distributions/*.zip} 을 실제로 열어 금지 엔트리가 없는지. 빌드하지 않고, ZIP 이
 *       없으면 이 검사만 건너뛴다.
 * </ol>
 *
 * <p>엔트리 <b>이름</b>만 읽는다. 내용은 열지 않는다 — 개인 데이터이기도 하고, {@link ZipFile} 이 중앙 디렉터리만 읽으므로 100MB 짜리 배포 ZIP
 * 도 순식간에 끝난다.
 *
 * <p>브라우저·네트워크·DB 를 쓰지 않는다. 앱을 기동하지 않고 빌드도 돌리지 않는다.
 *
 * @author KIUNSEA
 */
class DistributionHygieneTest {

  private static final Path BUILD_GRADLE = Path.of("build.gradle");

  /** 이미 만들어진 배포 산출물이 놓이는 자리. 없을 수 있다 — CI 의 {@code test} 만 도는 실행에는 아직 없다. */
  private static final Path DISTRIBUTIONS = Path.of("build/distributions");

  /** DB 파일 꼬리표. {@code -wal}/{@code -shm} 은 SQLite 가 함께 만드는 저널이라 본체와 같이 막아야 한다. */
  private static final List<String> FORBIDDEN_SUFFIXES =
      List.of(".db", ".db-wal", ".db-shm", ".sqlite", ".sqlite3");

  /** 통째로 개인 데이터인 디렉터리. {@code logs} 는 DEBUG 에 구매 상세가 남고, {@code exports} 는 내보낸 구매 내역 자체다. */
  private static final List<String> FORBIDDEN_PREFIXES = List.of("db/", "logs/", "exports/");

  /**
   * 값이 든 설정 파일. 이름이 정확히 같을 때만 걸리므로 {@code .example} 은 여기 걸리지 않는다.
   *
   * <p><b>서식이 배포본에 필요해서 허용하는 것이 아니다.</b> 지금 {@code packageDist} 의 {@code include} 목록에는 {@code
   * .example} 이 없고, v0.18.1 실측으로도 릴리스 ZIP 300 개 엔트리에 {@code .example} 은 0 건이다. 허용은 <b>방어적 대비</b>다 —
   * 나중에 서식을 배포본에 담기로 하거나 이름 비교를 접미사 일치로 바꾸는 순간 값이 비어 있는 서식이 개인 데이터로 걸리는데, 그때 사람은 판별식을 고치는 대신 느슨하게
   * 만든다. 그것이 가드가 죽는 가장 흔한 경로다.
   */
  private static final List<String> FORBIDDEN_NAMES =
      List.of("mall_account.yml", "admin.properties");

  // ---------------------------------------------------------------
  // 1층 — 검증이 build.gradle 에서 사라지지 않았는가
  // ---------------------------------------------------------------

  @Test
  @DisplayName("packageDist 가 방금 만든 ZIP 을 스스로 열어 검사한다")
  void packageDistInspectsItsOwnArchive() throws Exception {
    String block = packageDistBlock();

    assertTrue(
        block.contains("java.util.zip.ZipFile"),
        "packageDist 가 산출물을 열어 보지 않는다. from(...) 목록만으로는 '결과적으로 안전' 할 뿐이고,"
            + " 넓은 지정이 하나 추가되면 그 순간부터 개발자 개인 구매 내역이 PUBLIC 배포물에 실린다.");
  }

  @Test
  @DisplayName("검사에 걸리면 빌드가 실패한다 — 경고로 끝나지 않는다")
  void theInspectionFailsTheBuild() throws Exception {
    String block = packageDistBlock();

    // println 으로 알리기만 하면 릴리스 절차는 그대로 굴러간다. 사람은 100 줄 넘는 빌드 로그에서
    // 경고 한 줄을 놓치고, ZIP 은 이미 만들어진 채 태그가 붙는다. 반드시 멈춰 세워야 한다.
    assertTrue(
        block.contains("throw new GradleException"),
        "산출물 검사가 빌드를 실패시키지 않는다. 경고만 남기면 릴리스는 그대로 진행되고, 배포된 개인 데이터는 회수할 수 없다.");
  }

  @Test
  @DisplayName("금지 목록에 개인 데이터 경로가 전부 들어 있다")
  void theForbiddenListCoversEveryPersonalDataPath() throws Exception {
    String block = packageDistBlock();

    // 목록에서 한 줄이 빠지면 그 종류만 조용히 통과한다. 특히 -wal/-shm 은 "DB 파일이 아니다" 라고
    // 오해되기 쉬운데, WAL 에는 아직 본체에 반영되지 않은 최근 구매 내역이 그대로 들어 있다.
    List<String> required = new ArrayList<>();
    required.addAll(FORBIDDEN_SUFFIXES);
    required.addAll(FORBIDDEN_PREFIXES);
    required.addAll(FORBIDDEN_NAMES);

    for (String token : required) {
      assertTrue(
          block.contains("'" + token + "'"),
          "packageDist 의 산출물 검사에서 '" + token + "' 이 빠졌다. 이 종류만 검사를 그대로 통과해 PUBLIC 배포물에 실린다.");
    }
  }

  @Test
  @DisplayName("설정 서식(.example)이 허용된다는 근거가 검사에 남아 있다")
  void theConfigTemplateIsNotTreatedAsPersonalData() throws Exception {
    // 금지 이름을 '접미사 일치' 로 바꿔 놓으면 mall_account.yml.example 까지 함께 걸린다. 지금은
    // 서식이 배포본에 담기지 않으므로(아래 참조) 당장 터지지는 않지만, 저장소 트리를 검사하는
    // 다른 층과 규칙이 갈라지고 서식을 담기로 하는 순간 정상 파일이 개인 데이터로 걸린다.
    // 그때 사람은 판별식을 고치는 대신 느슨하게 만든다.
    //
    // 사실 관계를 정확히 적어 둔다: v0.18.1 실측으로 릴리스 ZIP 300 개 엔트리에 .example 은
    // 0 건이고, packageDist 의 include 목록에도 없다. 즉 허용 규칙은 '배포본에 필요해서' 가
    // 아니라 방어적 대비다.
    //
    // 이 단언은 build.gradle 의 주석 문구에 걸려 있다 — 지금 packageDist 블록에서 '.example'
    // 이 등장하는 곳은 그 주석뿐이다. 주석을 손보면 여기가 함께 빨개지므로, 문구를 바꿀 때는
    // '.example' 이라는 표기를 남겨 두거나 이 단언을 같이 옮겨야 한다.
    String block = packageDistBlock();

    assertTrue(
        block.contains(".example"),
        "설정 서식(.example)이 허용된다는 근거가 검사에 없다. 이름 비교를 접미사 일치로 바꾸면 값이 비어 있는 서식까지"
            + " 개인 데이터로 걸리고, 그때 사람은 판별식을 고치는 대신 느슨하게 만든다.");
  }

  // ---------------------------------------------------------------
  // 2층 — 실제로 만들어진 ZIP 에 개인 데이터가 없는가
  // ---------------------------------------------------------------

  @Test
  @DisplayName("이미 만들어진 배포 ZIP 에 개인 데이터가 실려 있지 않다")
  void existingDistributionArchivesCarryNoPersonalData() throws Exception {
    // 여기서 빌드를 돌리지 않는다. 배포본과 사용자 DB 가 build/ 아래에 있어서, 테스트가 빌드를
    // 트리거하면 실제 데이터가 지워지는 사고로 이어진다 (전례 있음). 있으면 보고, 없으면 넘어간다.
    assumeTrue(Files.isDirectory(DISTRIBUTIONS), "build/distributions 가 없다 — 배포 빌드를 아직 돌리지 않았다.");

    List<Path> archives;
    try (Stream<Path> files = Files.list(DISTRIBUTIONS)) {
      archives =
          files
              .filter(Files::isRegularFile)
              .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip"))
              .toList();
    }
    assumeTrue(!archives.isEmpty(), "build/distributions 에 ZIP 이 없다 — 검사할 산출물이 아직 없다.");

    List<String> offenders = new ArrayList<>();
    for (Path archive : archives) {
      for (String entry : forbiddenEntries(archive)) {
        offenders.add(archive.getFileName() + " → " + entry);
      }
    }

    assertTrue(
        offenders.isEmpty(),
        "배포 ZIP 에 개인 데이터로 읽히는 항목이 있다. 이 저장소도 배포물도 PUBLIC 이고, 이 프로그램이 다루는 것은"
            + " 프로그램을 실행하는 사람의 개인 구매 내역이다. 실리면 개발자 개인의 구매 내역·수집 로그·쇼핑몰 계정이"
            + " 그대로 공개되고, 한 번 나가면 회수할 수 없다:\n  "
            + String.join("\n  ", offenders));
  }

  // ---------------------------------------------------------------
  // 3층 — 판별식 자체가 살아 있는가
  // ---------------------------------------------------------------

  /** 반드시 걸려야 하는 엔트리 이름. 판별식이 좁아졌는지 보는 대조군이다. */
  private static final List<String> FORBIDDEN_SAMPLES =
      List.of(
          "db/jangbogo.db",
          "db/jangbogo.db-wal",
          "db/jangbogo.db-shm",
          "db/README.md", // 확장자가 달라도 db/ 아래면 걸린다
          "logs/jangbogo.log",
          "logs/screenshots/ssg-20260807.png",
          "exports/orders-202608.csv",
          "config/mall_account.yml",
          "config/admin.properties",
          "Jangbogo/data/collected.sqlite3",
          "DB/JANGBOGO.DB"); // 대문자로 남겨도 빠져나가지 않는다

  /**
   * 걸리면 안 되는 엔트리 이름.
   *
   * <p>뒤의 넷은 배포본에 실제로 들어가는 것들이다. 앞의 {@code .example} 셋은 <b>지금 배포본에 들어가지 않는다</b> — v0.18.1 실측으로 릴리스
   * ZIP 300 개 엔트리에 {@code .example} 은 0 건이고 {@code packageDist} 의 {@code include} 목록에도 없다. 그래도 함께
   * 두는 이유는 이름 비교가 접미사 일치로 바뀌는 순간 <b>값이 비어 있는 서식이 개인 데이터로 걸리기</b> 때문이다. 그 오탐이 나면 사람은 판별식을 고치는 대신
   * 느슨하게 만든다.
   */
  private static final List<String> ALLOWED_SAMPLES =
      List.of(
          "config/mall_account.yml.example",
          "config/admin.properties.example",
          "config/jbg_config.yml.example",
          "Jangbogo.bat",
          "jre/bin/java.exe",
          "lib/jangbogo-0.18.1.jar",
          "packaging/winsw/jangbogo-service.xml");

  @Test
  @DisplayName("판별식이 개인 데이터는 잡고 배포본 정상 파일은 통과시킨다")
  void theDetectionRuleItselfStillCatchesPersonalData() {
    // 위의 다섯 검사는 이것 하나가 죽어도 전부 초록으로 남는다. 실제로 확인했다 —
    // isForbidden 이 무조건 false 를 돌려주게 바꿔도 소스 형태 4건은 build.gradle 문자열만
    // 보므로 그대로 통과하고, 실물 ZIP 검사는 "금지 엔트리 0건" 이라는 같은 결론에 도달한다.
    // 즉 판별식이 통째로 죽은 채 초록만 나는 상태가 만들어진다. 가드가 없는 것보다 나쁘다 —
    // 없으면 사람이 알기라도 하는데, 이쪽은 아무도 모른 채 배포가 나간다.
    //
    // 그래서 판별식을 ZIP 없이 직접 두들긴다. 이 검사는 git·빌드 산출물에 의존하지 않으므로
    // CI 에서 build/distributions 가 비어 있어도 항상 돈다.
    for (String sample : FORBIDDEN_SAMPLES) {
      assertTrue(
          isForbidden(sample),
          sample + " 를 개인 데이터로 잡지 못했다. 판별식이 좁아졌다 — 이 상태면 실물 ZIP 검사가 통과해도" + " 아무것도 확인하지 않은 것이다.");
    }

    // 오탐도 같이 막는다. 정상 파일이 걸리기 시작하면 사람은 판별식을 고치는 대신 느슨하게
    // 만들거나 꺼 버린다 — 가드가 죽는 가장 흔한 경로다. .example 은 지금 배포본에 담기지
    // 않지만(ALLOWED_SAMPLES javadoc 참조) 이름 비교가 접미사 일치로 바뀌면 값이 비어 있는
    // 서식이 개인 데이터로 걸리므로 미리 못 박아 둔다.
    for (String sample : ALLOWED_SAMPLES) {
      assertFalse(
          isForbidden(sample),
          sample
              + " 를 개인 데이터로 잡았다. 값이 든 실제 파일만 막아야 한다. 이대로 두면 릴리스마다"
              + " 초록이 깨지고, 그러면 검사가 느슨해지거나 꺼진다.");
    }
  }

  @Test
  @DisplayName("ZIP 엔트리 스캔이 실제로 엔트리를 훑고 금지 대상만 골라낸다")
  void theArchiveScanActuallyReadsEntries(@TempDir Path tempDir) throws Exception {
    // 2층(실물 ZIP 검사)은 build/distributions 가 없으면 assumeTrue 로 통째로 건너뛴다. CI 의
    // test 실행에는 대개 ZIP 이 없으므로, 엔트리를 훑는 forbiddenEntries 는 사실상 어디에서도
    // 실행되지 않은 채 '있으면 본다' 는 주장만 남는다. 그 상태에서 이 함수가 늘 빈 목록을
    // 돌려주게 바뀌어도 알려 줄 것이 없다 — 저장소가 깨끗한 동안에는 결론이 같기 때문이다.
    // 위의 isForbidden 대조군도 이 자리는 못 덮는다. 그쪽은 '이름 하나를 어떻게 판정하는가' 만
    // 보고, 여기서 죽는 것은 '중앙 디렉터리를 정말 열어 이름을 꺼내는가' 다.
    //
    // 이 세션에서 실제로 두 번 겪은 형태다. (1) 세션 만료 감지는 단위 테스트 25건이 초록인 채
    // 프로덕션 호출자가 0건이었고, (2) 이 파일의 isForbidden 을 무조건 false 로 바꿔도 나머지
    // 검사가 전부 통과했다. 둘 다 '초록인데 아무 일도 안 하는' 상태였다.
    //
    // 그래서 실물 배포본이 아니라 여기서 만든 작은 ZIP 을 넣는다. 이름만 흉내 낸 빈 엔트리라
    // 개인 데이터가 없고, @TempDir 안이라 build/ 아래의 배포본·사용자 DB 에 닿지 않는다.
    // 파일명은 ASCII 로 둔다. 이 검사는 파일명 인코딩이 아니라 엔트리 판별을 재는 자리인데,
    // 이름에 한글을 쓰면 OS 의 파일명 인코딩이 실패 원인으로 섞여 들어와 원인을 엉뚱한 데서 찾게 된다.
    Path archive = tempDir.resolve("Jangbogo-control-sample.zip");
    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
      for (String entry :
          new String[] {
            "Jangbogo.bat", // 정상 파일 — 걸리면 안 된다
            "config/mall_account.yml.example", // 값이 없는 서식 — 걸리면 안 된다
            "db/jangbogo.db", // 구매 내역 + 계정 암호화 키
            "logs/jangbogo.log" // DEBUG 에 구매 상세가 남는다
          }) {
        zip.putNextEntry(new ZipEntry(entry));
        zip.closeEntry();
      }
    }

    assertEquals(
        List.of("db/jangbogo.db", "logs/jangbogo.log"),
        forbiddenEntries(archive),
        "ZIP 안의 금지 엔트리를 그대로 집어내지 못했다. 이 상태면 실물 ZIP 검사는 무엇이 실려 있어도 초록이다 —"
            + " '개인 데이터가 없다' 와 '아무것도 열어 보지 않았다' 가 같은 모양이라 구분되지 않는다.");
  }

  @Test
  @DisplayName("packageDist 블록만 잘라 온다 — 파일 전체를 돌려주면 1층 검사가 전부 무의미해진다")
  void thePackageDistBlockIsReallyJustThatBlock() throws Exception {
    // 1층의 네 검사는 전부 block.contains(문자열) 하나에 걸려 있다. 그래서 블록 추출이
    // build.gradle '파일 전체' 를 돌려주게 바뀌면 네 검사 모두 그대로 초록이다 — 필요한
    // 문자열이 파일 어딘가에는 다 있기 때문이다. 그러면 "packageDist '안에' 산출물 검사가
    // 살아 있다" 는 이 파일의 주장이 거짓이 되는데, 알려 줄 것이 하나도 없다.
    //
    // 이 세션에서 실제로 두 번 겪은 형태다. (1) 세션 만료 감지는 단위 테스트 25건이 초록인 채
    // 프로덕션 호출자가 0건이었고, (2) 이 파일의 isForbidden 을 무조건 false 로 바꿔도 나머지
    // 다섯 검사가 전부 통과했다. 판별부를 직접 두들기지 않는 가드는 죽어도 아무도 모른다.
    String gradle = Files.readString(BUILD_GRADLE, StandardCharsets.UTF_8);
    String block = packageDistBlock();

    assertTrue(block.startsWith("{"), "블록이 여는 중괄호에서 시작하지 않는다 — 경계 계산이 깨졌다.");
    assertTrue(block.endsWith("}"), "블록이 닫는 중괄호에서 끝나지 않는다 — 경계 계산이 깨졌다.");
    assertTrue(
        block.length() < gradle.length(), "블록 추출이 build.gradle 전체를 돌려준다 — 1층 검사가 아무 것도 보증하지 않는다.");

    // 안쪽은 정말 packageDist 인가.
    assertTrue(block.contains("archiveFileName"), "잘라 온 조각이 packageDist 블록이 아니다.");

    // 바깥의 다른 태스크까지 삼키지 않는가. 이 둘은 packageDist 앞에 따로 선언돼 있다.
    assertFalse(
        block.contains("tasks.register('createJre')"), "블록이 createJre 태스크까지 삼켰다 — 경계가 무너졌다.");
    assertFalse(
        block.contains("tasks.register('generateSourceNotice')"),
        "블록이 generateSourceNotice 태스크까지 삼켰다 — 경계가 무너졌다.");
  }

  // ---------------------------------------------------------------

  /**
   * ZIP 안에서 금지 대상으로 읽히는 엔트리 이름들.
   *
   * <p><b>이름만 본다.</b> 내용을 열면 그것 자체가 개인 데이터를 읽는 행위이고, 중앙 디렉터리만 훑는 지금은 100MB 짜리 배포 ZIP 도 순식간에 끝난다.
   */
  private static List<String> forbiddenEntries(Path archive) throws IOException {
    List<String> offenders = new ArrayList<>();
    try (ZipFile zip = new ZipFile(archive.toFile())) {
      Enumeration<? extends ZipEntry> entries = zip.entries();
      while (entries.hasMoreElements()) {
        String name = entries.nextElement().getName().replace('\\', '/');
        if (isForbidden(name)) {
          offenders.add(name);
        }
      }
    }
    offenders.sort(String::compareTo);
    return offenders;
  }

  /**
   * 엔트리 이름 하나가 금지 대상인지.
   *
   * <p>대소문자를 낮춰 비교한다. 개발 PC 가 Windows 라 파일시스템이 {@code .DB} 와 {@code .db} 를 같은 이름으로 취급하는데, 여기서 대소문자를
   * 따지면 대문자로 남은 것만 조용히 빠져나간다. {@code build.gradle} 쪽 검사와 같은 규칙이다 — 두 층이 다른 답을 내면 안 된다.
   */
  private static boolean isForbidden(String entryName) {
    String name = entryName.toLowerCase(Locale.ROOT);
    String leaf = name.substring(name.lastIndexOf('/') + 1);

    for (String prefix : FORBIDDEN_PREFIXES) {
      if (name.startsWith(prefix)) {
        return true;
      }
    }
    for (String suffix : FORBIDDEN_SUFFIXES) {
      if (leaf.endsWith(suffix)) {
        return true;
      }
    }
    return FORBIDDEN_NAMES.contains(leaf);
  }

  /**
   * {@code packageDist} 태스크 블록의 소스만 잘라 온다.
   *
   * <p>파일 전체에서 문자열을 찾으면 다른 태스크나 주석에 같은 낱말이 있는 것만으로 통과한다 — 그 순간 이 감시는 아무 것도 보증하지 않는다. 중괄호를 세어 블록 경계를
   * 잡으므로 태스크가 파일 끝이 아닌 자리로 옮겨져도 계속 맞는다.
   */
  private static String packageDistBlock() throws IOException {
    assertTrue(
        Files.isRegularFile(BUILD_GRADLE),
        "build.gradle 이 없다. 테스트 작업 디렉터리는 프로젝트 루트여야 한다 (Gradle Test 기본값).");
    String gradle = Files.readString(BUILD_GRADLE, StandardCharsets.UTF_8);

    int declaration = gradle.indexOf("tasks.register('packageDist'");
    assertTrue(declaration >= 0, "packageDist 태스크를 찾지 못했다. 배포 패키지를 만드는 자리가 바뀌었다면 이 감시도 함께 옮겨라.");

    int open = gradle.indexOf('{', declaration);
    assertTrue(open > 0, "packageDist 블록의 시작 중괄호를 찾지 못했다.");

    int depth = 0;
    for (int i = open; i < gradle.length(); i++) {
      char c = gradle.charAt(i);
      if (c == '{') {
        depth++;
      } else if (c == '}') {
        depth--;
        if (depth == 0) {
          return gradle.substring(open, i + 1);
        }
      }
    }
    throw new IllegalStateException("packageDist 블록의 끝 중괄호를 찾지 못했다. build.gradle 이 깨졌다.");
  }
}

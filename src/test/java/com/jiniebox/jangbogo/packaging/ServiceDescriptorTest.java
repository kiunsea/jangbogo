package com.jiniebox.jangbogo.packaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

/**
 * WinSW 서비스 정의 파일의 버전 드리프트 감시 (Phase 3-8).
 *
 * <p>배경: {@code packaging/winsw/jangbogo-service.xml} 의 {@code <arguments>} 는 실행할 JAR 파일명을 담는다. 여기에
 * 버전을 직접 적어 두면 앱 버전이 올라갈 때마다 조용히 어긋난다 — 실제로 앱이 0.11.2 일 때 이 파일은 {@code jangbogo-0.8.1.jar} 을 가리키고
 * 있었고, 그 파일이 릴리스 ZIP 에 그대로 들어갔다.
 *
 * <p>대책은 <b>버전을 두 번 적지 않는 것</b>이다. 저장소 원본에는 {@code @JAR_NAME@} 토큰만 두고, {@code build.gradle} 의
 * {@code packageDist} 가 {@code bootJar} 가 실제로 만든 파일명으로 치환한다. 버전의 단일 출처는 {@code build.gradle} 의
 * {@code version} 하나다.
 *
 * <p>이 테스트는 그 규칙이 다시 깨지는 것을 막는다. 브라우저·네트워크·DB 를 쓰지 않는다.
 *
 * @author KIUNSEA
 */
class ServiceDescriptorTest {

  private static final Path SERVICE_XML = Path.of("packaging/winsw/jangbogo-service.xml");
  private static final Path SERVICE_README = Path.of("packaging/winsw/README.md");
  private static final Path INSTALL_BAT = Path.of("packaging/distribution/install.bat");
  private static final Path CLEAN_BUILD_BAT = Path.of("bat/clean_build.bat");

  /** 예: jangbogo-0.8.1.jar — 버전이 박힌 JAR 참조. */
  private static final Pattern VERSIONED_JAR =
      Pattern.compile("jangbogo-\\d+\\.\\d+\\.\\d+[^\\s\"']*\\.jar");

  private static final String TOKEN = "@JAR_NAME@";

  /** XML 주석 블록. 과거 사례를 설명하는 주석에는 버전이 박힌 이름이 등장해도 된다 — 실행되지 않기 때문이다. */
  private static final Pattern XML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

  private static String read(Path path) throws Exception {
    assertTrue(
        Files.isRegularFile(path), path + " 이 없다. 테스트 작업 디렉터리는 프로젝트 루트여야 한다 (Gradle Test 기본값).");
    return Files.readString(path, StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("서비스 XML 의 arguments 는 버전이 아니라 @JAR_NAME@ 토큰을 가리킨다")
  void argumentsUsesTokenNotHardcodedVersion() throws Exception {
    String xml = read(SERVICE_XML);

    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    DocumentBuilder builder = factory.newDocumentBuilder();

    Document doc;
    try (var in = Files.newInputStream(SERVICE_XML)) {
      doc = builder.parse(in);
    }

    XPath xpath = XPathFactory.newInstance().newXPath();
    String arguments = xpath.evaluate("/service/arguments", doc).trim();

    assertFalse(arguments.isEmpty(), "/service/arguments 노드가 비어 있다.");
    assertTrue(
        arguments.contains(TOKEN), "arguments 가 " + TOKEN + " 토큰을 담고 있지 않다. 실제 값: " + arguments);
    assertTrue(arguments.contains("--service"), "arguments 에 --service 인자가 빠졌다: " + arguments);

    // 토큰 방식을 쓰면서 동시에 버전을 적어 두는 어중간한 상태를 막는다.
    // 주석은 제외한다 — 이 파일의 주석은 과거 드리프트 사례(jangbogo-0.8.1.jar)를 근거로 남겨 둔 것이고,
    // 주석은 실행되지 않으므로 드리프트가 아니다.
    String withoutComments = XML_COMMENT.matcher(xml).replaceAll("");
    Matcher m = VERSIONED_JAR.matcher(withoutComments);
    assertFalse(
        m.find(),
        "서비스 XML 의 실행 경로에 버전이 박힌 JAR 참조가 남아 있다: "
            + (m.reset().find() ? m.group() : "")
            + " — 버전의 단일 출처는 build.gradle 의 version 하나뿐이다.");
  }

  @Test
  @DisplayName("서비스 README 에는 버전이 박힌 JAR 이름을 적지 않는다")
  void readmeDoesNotPinAJarVersion() throws Exception {
    String readme = read(SERVICE_README);

    Matcher m = VERSIONED_JAR.matcher(readme);
    assertFalse(
        m.find(),
        "README 에 버전이 박힌 JAR 이름이 있다: "
            + (m.reset().find() ? m.group() : "")
            + " — 문서의 버전이 실제 JAR 과 어긋나면 그대로 서비스 시작 실패가 된다. "
            + "jangbogo-x.y.z.jar 같은 자리표시자를 쓰고 실제 이름은 dir 로 확인하도록 안내한다.");
  }

  @Test
  @DisplayName("install.bat 의 서비스 XML 자동 동기화 단계가 유지된다")
  void installBatStillSyncsServiceXml() throws Exception {
    String bat = read(INSTALL_BAT);

    // packageDist 의 토큰 치환은 'ZIP 을 만든 시점'을 보장하고,
    // install.bat 의 동기화는 'JAR 만 갈아끼운 폴더'를 보장한다. 두 장치는 서로를 대체하지 않는다.
    assertTrue(
        bat.contains("/service/arguments"),
        "install.bat 이 더 이상 /service/arguments 를 갱신하지 않는다. "
            + "JAR 만 교체하고 재설치하는 경로에서 버전 드리프트가 되살아난다.");
    assertTrue(
        bat.contains("jangbogo-*.jar"),
        "install.bat 이 폴더의 실제 JAR 을 탐지하지 않는다 (dir /b /o:-d jangbogo-*.jar).");
  }

  @Test
  @DisplayName("clean_build.bat 은 버전이 박힌 JAR 이름 대신 와일드카드로 산출물을 찾는다")
  void cleanBuildBatDetectsTheJarInsteadOfPinningAVersion() throws Exception {
    // 이 파일은 packageDist 를 거치지 않고 저장소에서 직접 실행된다. 토큰 치환(3-8 방식)은
    // '복사하는 빌드 단계'가 있어야 성립하므로 여기서는 쓸 수 없다 — install.bat 과 같은
    // 실행 시점 와일드카드 탐지가 맞다. 실제로 0.5.0 참조가 8개 버전 동안 방치돼 있었다.
    String bat = read(CLEAN_BUILD_BAT);

    Matcher m = VERSIONED_JAR.matcher(bat);
    assertFalse(
        m.find(),
        "clean_build.bat 에 버전이 박힌 JAR 참조가 있다: "
            + (m.reset().find() ? m.group() : "")
            + " — 버전의 단일 출처는 build.gradle 의 version 하나뿐이다.");
    assertTrue(bat.contains("jangbogo-*.jar"), "clean_build.bat 이 빌드 산출물을 와일드카드로 탐지하지 않는다.");
  }

  @Test
  @DisplayName("packageDist 가 서비스 XML 을 토큰 치환해서 담는다")
  void packageDistFiltersTheServiceXml() throws Exception {
    String gradle = read(Path.of("build.gradle"));

    assertTrue(
        gradle.contains("ReplaceTokens"),
        "packageDist 에 ReplaceTokens 필터가 없다. 토큰이 치환되지 않은 채 ZIP 에 들어간다.");
    assertTrue(
        gradle.contains("'JAR_NAME'") || gradle.contains("\"JAR_NAME\""),
        "packageDist 의 ReplaceTokens 가 JAR_NAME 토큰을 다루지 않는다.");

    // XML 이 필터 없는 복사 블록에 다시 잡히면 치환본이 원본으로 덮일 수 있다.
    assertEquals(
        0,
        countOccurrences(gradle, "'*.exe', '*.xml', '*.md'"),
        "packageDist 가 서비스 XML 을 필터 없이 복사하는 블록으로 되돌아갔다.");
  }

  // ---------------------------------------------------------------
  // 대조군 — 판별식 자체가 살아 있는가
  //
  // 위의 세 검사는 전부 "VERSIONED_JAR 가 아무것도 못 찾았다" 형태다. 그래서 이 정규식을
  // 아무것도 맞지 않게 바꾸면 <b>세 검사가 전부 그대로 초록</b>이 된다. 저장소가 지금 토큰
  // 방식을 지키고 있다는 사실은 정규식이 살아 있다는 근거가 못 된다 — 죽은 정규식도 같은
  // 결론을 낸다.
  //
  // 이 세션에서 실제로 두 번 겪은 형태다. (1) 세션 만료 감지는 단위 테스트 25건이 초록인 채
  // 프로덕션 호출자가 0건이었고, (2) 배포 산출물 가드는 판별식을 무력화해도 5건이 전부
  // 통과했다. 둘 다 '초록인데 아무 일도 안 하는' 상태였다.
  // ---------------------------------------------------------------

  @Test
  @DisplayName("대조군: 버전 박힌 JAR 정규식이 실제 드리프트 형태를 잡는다")
  void theVersionedJarPatternStillCatchesPinnedNames() {
    for (String pinned :
        new String[] {
          "jangbogo-0.8.1.jar", // 실제로 8개 버전 동안 방치됐던 그 이름
          "jangbogo-0.11.2.jar",
          "jangbogo-1.0.0-SNAPSHOT.jar",
          "-jar \"%BASE%\\..\\jangbogo-0.18.2.jar\" --service"
        }) {
      assertTrue(
          VERSIONED_JAR.matcher(pinned).find(),
          "버전이 박힌 JAR 참조인데 정규식이 잡지 못했다. 이 상태면 드리프트 감시 세 건이 무엇이 있어도 초록이다: " + pinned);
    }

    // 오탐도 같이 막는다. 자리표시자·와일드카드·토큰이 걸리기 시작하면 초록을 되찾으려고
    // 사람이 버전을 다시 박아 넣는 쪽으로 고친다 — 정확히 이 감시가 막으려는 일이다.
    for (String safe :
        new String[] {
          "jangbogo-x.y.z.jar",
          "dir /b /o:-d jangbogo-*.jar",
          TOKEN,
          "-jar \"%BASE%\\..\\" + TOKEN + "\" --service"
        }) {
      assertFalse(VERSIONED_JAR.matcher(safe).find(), "버전이 박히지 않은 참조인데 드리프트로 걸렸다(오탐): " + safe);
    }
  }

  @Test
  @DisplayName("대조군: XML 주석 제거가 주석만 지운다")
  void theXmlCommentStripperOnlyRemovesComments() {
    // 주석 제거가 사라지면 이 파일의 주석에 남겨 둔 과거 사례(jangbogo-0.8.1.jar)가 걸려 빨개진다.
    // 그때 사람은 주석을 지우게 되고, 다음 사람은 왜 토큰을 쓰는지 알 수 없게 된다.
    // 반대로 제거가 너무 넓어지면 실행되는 노드까지 사라져 드리프트를 못 잡는다. 양쪽을 못 박는다.
    String xml =
        "<service>\n"
            + "  <!-- 예전엔 여기에 jangbogo-0.8.1.jar 이 박혀 있었다 -->\n"
            + "  <arguments>-jar \"%BASE%\\..\\@JAR_NAME@\" --service</arguments>\n"
            + "</service>\n";

    String withoutComments = XML_COMMENT.matcher(xml).replaceAll("");

    assertFalse(
        VERSIONED_JAR.matcher(withoutComments).find(), "주석 안의 과거 사례가 실행 경로로 세어졌다 — 설명을 적을수록 빨개진다.");
    assertTrue(
        withoutComments.contains(TOKEN),
        "주석 제거가 실행되는 노드까지 지웠다. 빈 문자열에는 어떤 버전도 없으므로 드리프트 감시가 통째로 무동작이 된다.");
  }

  @Test
  @DisplayName("대조군: 등장 횟수 세기가 실제로 세고 없는 조각은 0 으로 답한다")
  void theOccurrenceCounterActuallyCounts() {
    // packageDistFiltersTheServiceXml 의 마지막 단언은 assertEquals(0, countOccurrences(...)) 다.
    // 그래서 이 도우미가 무조건 0 을 돌려주게 바뀌면 그 단언은 <b>무엇이 있어도 초록</b>이 된다 —
    // 서비스 XML 이 필터 없는 복사 블록으로 되돌아가도 아무도 모른 채 지나간다. 지금 build.gradle
    // 에 그 블록이 없다는 사실은 세기가 살아 있다는 근거가 못 된다. 죽은 세기도 같은 답을 낸다.
    //
    // 이 세션에서 실제로 두 번 겪은 형태다. (1) 세션 만료 감지는 단위 테스트 25건이 초록인 채
    // 프로덕션 호출자가 0건이었고, (2) 배포 산출물 가드는 판별식을 무력화해도 5건이 전부 통과했다.
    // 그래서 저장소의 build.gradle 이 아니라 도우미에 직접 입력을 넣는다.
    String needle = "'*.exe', '*.xml', '*.md'";
    String reverted = "from('packaging/winsw') {\n    include " + needle + "\n  }";
    String filtered = "from('packaging/winsw') {\n    include 'jangbogo-service.xml'\n  }";

    assertEquals(
        1,
        countOccurrences(reverted, needle),
        "실제로 되돌아간 형태를 세지 못했다. 이 상태면 필터 없는 복사 블록 감시가 통째로 무동작이다.");
    assertEquals(2, countOccurrences(needle + "\n" + needle, needle), "두 번 나온 것을 한 번으로 셌다.");
    assertEquals(
        0,
        countOccurrences(filtered, needle),
        "없는 조각을 찾았다고 답한다 — 그러면 정상 상태에서 늘 빨개지고, 사람은 세기를 고치는 대신 검사를 지운다.");
  }

  private static int countOccurrences(String haystack, String needle) {
    int count = 0;
    int idx = 0;
    while ((idx = haystack.indexOf(needle, idx)) != -1) {
      count++;
      idx += needle.length();
    }
    return count;
  }
}

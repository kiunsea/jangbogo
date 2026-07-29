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

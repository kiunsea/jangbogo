package com.jiniebox.jangbogo.packaging;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 배포 ZIP 의 라이선스 고지 누락 감시 (미결 1).
 *
 * <h2>배경</h2>
 *
 * <p>이 프로젝트는 AGPL-3.0-or-later 이고 배포 ZIP 에는 jlink 로 만든 OpenJDK 런타임까지 함께 담긴다. 그런데 {@code
 * packageDist} 의 include 목록에 {@code LICENSE} 도 {@code NOTICE} 도 없어서, <b>여러 버전이 라이선스 전문 없이 배포됐다.</b>
 * AGPL 제4조는 사본과 함께 라이선스 전문을 전달할 것을 요구하고, 정작 이 저장소의 {@code NOTICE} 자신이 "배포 시에는 LICENSE 와 함께 이 NOTICE
 * 를 포함해 주십시오"라고 적어 두고 있었다.
 *
 * <p>대응 소스 안내도 같이 담는다 — AGPL 제6조가 요구하는 "대응 소스를 찾을 수 있는 명확한 안내"이며, 번들 JRE(GPLv2 + Classpath
 * Exception)의 소스 취득처도 여기서 알린다.
 *
 * <h2>버전을 두 번 적지 않는다</h2>
 *
 * <p>안내문의 앱 버전과 JRE 버전은 빌드 시점에 채운다. 저장소 원본에는 토큰만 둔다. CI 는 {@code setup-java} 가 주는 JDK 로 빌드하므로 로컬과
 * JRE 버전이 다를 수 있고, 숫자를 손으로 적으면 반드시 어긋난다. {@link ServiceDescriptorTest} 가 WinSW XML 에 대해 지키는 규칙과 같다.
 *
 * <p>브라우저·네트워크·DB 를 쓰지 않는다.
 *
 * @author KIUNSEA
 */
class DistributionLicenseTest {

  private static final Path BUILD_GRADLE = Path.of("build.gradle");
  private static final Path LICENSE = Path.of("LICENSE");
  private static final Path NOTICE = Path.of("NOTICE");
  private static final Path SOURCE_NOTICE =
      Path.of("packaging/distribution/CORRESPONDING-SOURCE.txt");

  /** 예: 0.13.9 — 안내문 원본에 박혀 있으면 안 되는 버전 표기. */
  private static final Pattern PINNED_VERSION = Pattern.compile("\\b\\d+\\.\\d+\\.\\d+\\b");

  private static String read(Path path) throws Exception {
    assertTrue(
        Files.isRegularFile(path), path + " 이 없다. 테스트 작업 디렉터리는 프로젝트 루트여야 한다 (Gradle Test 기본값).");
    return Files.readString(path, StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("저장소 루트에 AGPL 전문과 NOTICE 가 있다")
  void repositoryCarriesTheLicenseTexts() throws Exception {
    String license = read(LICENSE);
    assertTrue(
        license.contains("GNU AFFERO GENERAL PUBLIC LICENSE"),
        "LICENSE 가 AGPL 전문이 아니다. build.gradle 의 라이선스 선언과 어긋난다.");
    assertTrue(license.contains("Version 3"), "AGPL 버전 표기가 없다.");

    assertTrue(read(NOTICE).contains("AGPL-3.0"), "NOTICE 에 라이선스 식별자가 없다.");
  }

  @Test
  @DisplayName("packageDist 가 LICENSE 와 NOTICE 를 ZIP 에 담는다")
  void packageDistShipsTheLicenseAndNotice() throws Exception {
    String gradle = read(BUILD_GRADLE);

    assertTrue(
        gradle.contains("include 'LICENSE', 'NOTICE'"),
        "packageDist 가 LICENSE·NOTICE 를 담지 않는다. AGPL 제4조는 사본과 함께 라이선스 전문을 전달할 것을 요구한다.");
  }

  @Test
  @DisplayName("packageDist 가 대응 소스 안내를 ZIP 에 담는다")
  void packageDistShipsTheCorrespondingSourceNotice() throws Exception {
    String gradle = read(BUILD_GRADLE);

    assertTrue(
        gradle.contains("generateSourceNotice"),
        "대응 소스 안내 생성 태스크가 없다. AGPL 제6조의 '대응 소스를 찾을 수 있는 명확한 안내'가 빠진다.");
    assertTrue(gradle.contains("include 'CORRESPONDING-SOURCE.txt'"), "생성된 안내문이 ZIP 에 포함되지 않는다.");
    assertTrue(
        gradle.contains("dependsOn bootJar, createJre, generateSourceNotice"),
        "packageDist 가 generateSourceNotice 에 의존하지 않는다. 안내문이 없거나 낡은 채로 담길 수 있다.");
  }

  @Test
  @DisplayName("안내문 원본에는 버전을 적지 않는다 — 토큰만 둔다")
  void theSourceNoticeTemplateUsesTokensNotPinnedVersions() throws Exception {
    String template = read(SOURCE_NOTICE);

    assertTrue(template.contains("@APP_VERSION@"), "앱 버전 토큰이 없다.");
    assertTrue(template.contains("@JAVA_VERSION@"), "JRE 버전 토큰이 없다.");

    Matcher m = PINNED_VERSION.matcher(template);
    assertFalse(
        m.find(),
        "안내문 원본에 버전이 박혀 있다: "
            + (m.reset().find() ? m.group() : "")
            + " — 버전의 단일 출처는 build.gradle 의 version 과 번들 JRE 의 release 파일이다.");
  }

  @Test
  @DisplayName("안내문은 세 구성요소의 소스 취득처를 모두 알린다")
  void theSourceNoticeCoversEveryBundledComponent() throws Exception {
    String template = read(SOURCE_NOTICE);

    // 1) 앱 본체 — AGPL 대응 소스
    assertTrue(template.contains("AGPL-3.0"), "본체 라이선스 표기가 없다.");
    assertTrue(
        template.contains("github.com/kiunsea/jangbogo"), "대응 소스 위치가 없다 — AGPL 제6조가 요구하는 안내다.");

    // 2) 번들 JRE — GPLv2 + Classpath Exception. 바이너리를 배포하므로 소스 취득처를 알려야 한다.
    assertTrue(template.contains("Classpath Exception"), "번들 JRE 의 라이선스 예외 표기가 없다.");
    assertTrue(template.contains("adoptium"), "번들 JRE 의 소스 취득처가 없다.");
    assertTrue(template.contains("jre/legal"), "번들 JRE 의 라이선스 전문 위치 안내가 없다.");

    // 3) 제3자 라이브러리
    assertTrue(template.contains("NOTICE"), "제3자 고지 파일 안내가 없다.");

    // AGPL 제13조(네트워크 상호작용)는 웹 UI 를 띄우는 이 앱에 직접 걸리는 조항이다.
    assertTrue(template.contains("제13조"), "AGPL 제13조 안내가 없다 — 이 앱은 네트워크로 접근하는 UI 를 제공한다.");
  }

  @Test
  @DisplayName("안내문이 자리표시자만 남긴 채 배포되지 않는다")
  void theTemplateIsNotShippedRaw() throws Exception {
    String gradle = read(BUILD_GRADLE);

    // packaging/distribution 을 통째로 담는 블록에 .txt 가 섞여 들어가면 치환 전 원본이 ZIP 에 들어간다.
    // 현재 그 블록은 파일명을 하나씩 열거하므로, CORRESPONDING-SOURCE.txt 가 거기 없어야 한다.
    int distBlock = gradle.indexOf("from('packaging/distribution')");
    assertTrue(distBlock > 0, "packaging/distribution 복사 블록을 찾지 못했다.");
    int blockEnd = gradle.indexOf("}", gradle.indexOf("into('/')", distBlock));
    String block = gradle.substring(distBlock, blockEnd);

    // 잘라 온 조각이 비었거나 엉뚱한 자리이면 아래 단언은 무엇이 있어도 통과한다. "복사되지
    // 않는다" 와 "아무것도 보지 않았다" 가 같은 모양이 되므로 먼저 조각부터 확인한다.
    assertTrue(
        block.contains("Jangbogo.bat"),
        "잘라 온 조각이 packaging/distribution 복사 블록이 아니다 — 아래 단언이 아무 것도 보증하지 않는다.");

    assertFalse(
        block.contains("CORRESPONDING-SOURCE.txt"), "안내문 원본이 필터 없이 복사된다 — 토큰이 치환되지 않은 채 배포된다.");
  }

  // ---------------------------------------------------------------
  // 대조군 — 판별식 자체가 살아 있는가
  // ---------------------------------------------------------------

  @Test
  @DisplayName("대조군: 버전 표기 정규식이 박힌 버전을 잡고 토큰은 통과시킨다")
  void thePinnedVersionPatternStillCatchesVersions() {
    // theSourceNoticeTemplateUsesTokensNotPinnedVersions 는 "정규식이 아무것도 못 찾았다"
    // 형태다. 그래서 PINNED_VERSION 을 아무것도 맞지 않게 바꾸면 그대로 초록이 된다. 저장소가
    // 지금 토큰만 쓰고 있다는 사실은 정규식이 살아 있다는 근거가 못 된다 — 죽은 정규식도 같은
    // 결론을 낸다.
    //
    // 이 세션에서 실제로 두 번 겪은 형태다. (1) 세션 만료 감지는 단위 테스트 25건이 초록인 채
    // 프로덕션 호출자가 0건이었고, (2) 배포 산출물 가드는 판별식을 무력화해도 5건이 전부 통과했다.
    for (String pinned :
        new String[] {"장보고 0.13.9 기준", "번들 JRE 21.0.11 기준", "jangbogo-0.18.2.jar"}) {
      assertTrue(
          PINNED_VERSION.matcher(pinned).find(),
          "박힌 버전인데 정규식이 잡지 못했다. 이 상태면 안내문에 버전을 적어 두어도 초록이다: " + pinned);
    }

    // 알아 둘 것: 이 정규식은 낱말 경계(\b)로 시작해서 'v0.13.9' 처럼 글자가 앞에 붙은 표기는
    // 잡지 못한다. 안내문 원본이 그 형태로 버전을 적으면 이 감시를 빠져나간다. 대조군을 쓰다
    // 확인한 사실이라 여기 남긴다 — 좁히려면 정규식을 고쳐야 하고, 그때 이 대조군에 'v0.13.9'
    // 를 걸려야 하는 입력으로 옮겨 적어라.
    assertFalse(PINNED_VERSION.matcher("v0.13.9").find(), "정규식이 넓어졌다면 위 주석과 이 단언을 함께 고쳐라.");

    // 오탐도 같이 막는다. 라이선스 식별자(AGPL-3.0)나 조항 번호가 걸리기 시작하면 안내문이
    // 늘 빨개지고, 그때 사람은 정규식을 고치는 대신 이 검사를 지운다.
    for (String safe :
        new String[] {"@APP_VERSION@", "@JAVA_VERSION@", "AGPL-3.0-or-later", "AGPL 제13조"}) {
      assertFalse(PINNED_VERSION.matcher(safe).find(), "버전 표기가 아닌데 걸렸다(오탐): " + safe);
    }
  }
}

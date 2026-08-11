package com.jiniebox.jangbogo.ctrl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 화면들이 <b>같은 판</b>의 프런트엔드 자산을 싣는지 본다.
 *
 * <h2>왜 필요한가</h2>
 *
 * <p>로그인 화면({@code signin.html})은 레이아웃을 쓰지 않아 자기 {@code <head>} 를 들고 있고, 나머지 다섯 장은 {@code
 * fragments/header.html}·{@code footer.html} 을 통한다. 그래서 <b>같은 사실(어느 판을 쓰는가)이 두 곳에 산다.</b>
 *
 * <p>실제로 갈렸다 — 2026-08-11 확인에서 fragments 는 {@code bootstrap@5.3.2}, 로그인 화면은 {@code 5.3.3} 이었다. 판이
 * 갈려도 <b>화면은 대체로 멀쩡히 그려진다.</b> 그래서 아무도 모르다가, 두 판 사이에서 바뀐 클래스명이나 컴포넌트 동작 하나를 밟는 날 <b>한쪽 화면에서만</b>
 * 깨진다. 그때는 원인을 CDN 버전까지 되짚어야 한다.
 *
 * <p><b>패키지별로 하나</b>만 허용한다. 판을 올릴 때는 전부 같이 올려야 한다는 뜻이고, 그것이 이 검사가 강제하려는 것 전부다. 어떤 판이 옳은지는 정하지 않는다 —
 * 그건 사람이 고를 일이고, 여기서 특정 숫자를 박으면 판을 올릴 때마다 이 파일이 함께 빨개진다.
 */
class ScreenAssetVersionTest {

  private static final Path TEMPLATES = Path.of("src/main/resources/templates");

  /** {@code cdn.jsdelivr.net/npm/<패키지>@<판>/...} 에서 패키지와 판을 뽑는다. */
  private static final Pattern CDN_PACKAGE =
      Pattern.compile("cdn\\.jsdelivr\\.net/npm/([a-z0-9@._/-]+?)@([0-9][0-9A-Za-z.+-]*)/");

  @Test
  @DisplayName("한 패키지는 화면 전체에서 같은 판을 쓴다")
  void everyScreenLoadsTheSameVersionOfEachPackage() throws IOException {
    Map<String, Map<String, Set<Path>>> byPackage = cdnUsage();

    for (Map.Entry<String, Map<String, Set<Path>>> pkg : byPackage.entrySet()) {
      assertEquals(
          1,
          pkg.getValue().size(),
          () ->
              "화면마다 "
                  + pkg.getKey()
                  + " 의 판이 다르다 — 한쪽 화면에서만 깨지는 결함이 되고, 원인을 CDN 버전까지 되짚어야 한다: "
                  + pkg.getValue());
    }
  }

  // ── 대조군 — 판별식이 살아 있는가 ────────────────────────────────────────

  @Test
  @DisplayName("대조군 — CDN 자산을 실제로 찾아낸다")
  void theProbeIsNotAlwaysGreen() throws IOException {
    // 정규식이 한 건도 못 찾으면 위 검사는 빈 반복으로 통과한다. 판이 제각각이어도
    // 초록인 상태라, 최소한 부트스트랩 두 패키지는 찾아내는지 본다.
    Set<String> packages = cdnUsage().keySet();

    assertFalse(packages.isEmpty(), "CDN 자산을 한 건도 찾지 못했다 — 위 검사가 무의미하다.");
    assertTrue(packages.contains("bootstrap"), "bootstrap 을 찾지 못했다: " + packages);
    assertTrue(packages.contains("bootstrap-icons"), "bootstrap-icons 를 찾지 못했다: " + packages);
  }

  /** 패키지 → 판 → 그 판을 싣는 화면들. */
  private static Map<String, Map<String, Set<Path>>> cdnUsage() throws IOException {
    assertTrue(
        Files.isDirectory(TEMPLATES), "화면 폴더를 찾지 못했다(경로가 바뀌었나): " + TEMPLATES.toAbsolutePath());

    Map<String, Map<String, Set<Path>>> usage = new TreeMap<>();
    for (Path screen : screens()) {
      Matcher matcher = CDN_PACKAGE.matcher(Files.readString(screen, StandardCharsets.UTF_8));
      while (matcher.find()) {
        usage
            .computeIfAbsent(matcher.group(1), k -> new LinkedHashMap<>())
            .computeIfAbsent(matcher.group(2), k -> new TreeSet<>())
            .add(TEMPLATES.relativize(screen));
      }
    }
    return usage;
  }

  private static List<Path> screens() throws IOException {
    try (Stream<Path> files = Files.walk(TEMPLATES)) {
      return files.filter(p -> p.toString().endsWith(".html")).sorted().toList();
    }
  }
}

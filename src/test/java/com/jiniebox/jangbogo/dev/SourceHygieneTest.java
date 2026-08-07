package com.jiniebox.jangbogo.dev;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 소스에 보이지 않는 제어문자가 섞이지 않았는지.
 *
 * <h2>왜 이런 테스트가 필요한가</h2>
 *
 * <p>편집 도구가 문자열 리터럴 자리에 <b>NUL 바이트</b>를 넣는 사고가 실제로 있었다. 구분자로 공백을 쓰려던 자리에 {@code 0x00} 이 들어갔는데,
 *
 * <ul>
 *   <li>컴파일은 통과했고,
 *   <li>단위테스트도 통과했으며(구분자 값이 무엇이든 동작이 같았다),
 *   <li>화면에도 보이지 않았고,
 *   <li><b>포맷 검사만 CI 에서 실패</b>해서 원인을 바이트 단위로 파고들어야 했다.
 * </ul>
 *
 * <p>이 검사는 그 한 바퀴를 없앤다 — 사람이 읽을 수 없는 문자는 소스에 있을 이유가 없다.
 *
 * <p>파일만 읽는다. 브라우저·네트워크·DB 를 쓰지 않는다.
 *
 * @author KIUNSEA
 */
class SourceHygieneTest {

  /** 소스에 허용하는 제어문자 — 탭과 줄바꿈뿐이다. */
  private static boolean allowed(int ch) {
    return ch == '\t' || ch == '\n' || ch == '\r';
  }

  private static boolean isControl(int ch) {
    return (ch < 0x20 || ch == 0x7F) && !allowed(ch);
  }

  /** 검사 대상 자바 소스 전부. 대조군이 "정말 훑었는가" 를 물을 수 있게 따로 떼어 둔다. */
  private static List<Path> javaSources() throws IOException {
    List<Path> sources = new ArrayList<>();
    for (String root : new String[] {"src/main/java", "src/test/java"}) {
      Path dir = Paths.get(root);
      if (!Files.isDirectory(dir)) {
        continue;
      }
      try (Stream<Path> files = Files.walk(dir)) {
        sources.addAll(files.filter(p -> p.toString().endsWith(".java")).toList());
      }
    }
    return sources;
  }

  @Test
  @DisplayName("자바 소스에 보이지 않는 제어문자가 없다")
  void javaSourcesCarryNoInvisibleControlCharacters() throws IOException {
    List<String> offenders = new ArrayList<>();

    for (Path file : javaSources()) {
      byte[] bytes = Files.readAllBytes(file);
      for (int i = 0; i < bytes.length; i++) {
        int ch = bytes[i] & 0xFF;
        if (isControl(ch)) {
          // 값이 아니라 위치만 보고한다. 어느 파일 몇 번째 바이트인지면 충분하다.
          offenders.add(
              String.format("%s (바이트 %d, 0x%02X)", file.toString().replace('\\', '/'), i, ch));
          break;
        }
      }
    }

    assertTrue(
        offenders.isEmpty(),
        "보이지 않는 제어문자가 소스에 섞였다. 컴파일·테스트는 통과하지만 포맷 검사가 CI 에서 깨진다:\n  "
            + String.join("\n  ", offenders));
  }

  // ---------------------------------------------------------------
  // 대조군 — 판별식 자체가 살아 있는가
  // ---------------------------------------------------------------

  @Test
  @DisplayName("제어문자 판별식이 보이지 않는 문자는 잡고 정상 바이트는 통과시킨다")
  void theControlCharacterRuleItselfStillCatchesThem() {
    // 위 검사는 이것 하나가 죽어도 초록으로 남는다. isControl 이 무조건 false 를 돌려주게
    // 바꿔도 지금 소스가 깨끗하니 offender 는 그대로 0건이고 결론이 같다. 즉 '판별식이 통째로
    // 죽은 채 초록만 나는' 상태가 만들어진다 — 이 세션에서 실제로 두 번 겪은 형태다.
    // (1) 세션 만료 감지는 단위 테스트 25건이 초록인 채 프로덕션 호출자가 0건이었고,
    // (2) 배포 산출물 가드는 판별식을 무력화해도 테스트 5건이 전부 통과했다.
    for (int ch : new int[] {0x00, 0x01, 0x07, 0x0B, 0x0C, 0x1B, 0x7F}) {
      assertTrue(
          isControl(ch),
          String.format("0x%02X 를 제어문자로 잡지 못했다. 실제 사고는 문자열 리터럴에 들어간 NUL(0x00) 이었다.", ch));
    }

    // 탭·줄바꿈은 소스에 정상적으로 있는 문자다. 여기가 걸리기 시작하면 모든 파일이 offender 로
    // 나오고, 그때 사람은 판별식을 고치는 대신 검사를 꺼 버린다.
    for (int ch : new int[] {'\t', '\n', '\r', ' ', 'A', '_', 0x7E}) {
      assertFalse(isControl(ch), String.format("0x%02X 는 정상 문자인데 제어문자로 걸렸다.", ch));
    }

    // 한글은 UTF-8 에서 여러 바이트로 쪼개진다. 검사가 바이트 단위라 이 바이트들이 걸리면
    // 한국어 주석이 있는 파일이 전부 offender 가 된다.
    for (int ch : new int[] {0xEA, 0xB0, 0x80, 0xFF}) {
      assertFalse(isControl(ch), String.format("0x%02X (UTF-8 다중바이트) 가 제어문자로 걸렸다.", ch));
    }
  }

  @Test
  @DisplayName("소스 스캔이 실제로 파일을 훑는다")
  void theSourceScanActuallyVisitsFiles() throws IOException {
    // 수집기가 빈 목록을 돌려주면 위 검사는 offender 0건으로 초록이 된다. "제어문자가 없다" 와
    // "아무것도 읽지 않았다" 는 결과가 같은 모양이라 구분되지 않는다.
    assertFalse(javaSources().isEmpty(), "자바 소스를 하나도 찾지 못했다 — 작업 디렉터리가 프로젝트 루트가 아니거나 경로가 바뀌었다.");
  }
}

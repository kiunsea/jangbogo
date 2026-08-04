package com.jiniebox.jangbogo.dev;

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

  @Test
  @DisplayName("자바 소스에 보이지 않는 제어문자가 없다")
  void javaSourcesCarryNoInvisibleControlCharacters() throws IOException {
    List<String> offenders = new ArrayList<>();

    for (String root : new String[] {"src/main/java", "src/test/java"}) {
      Path dir = Paths.get(root);
      if (!Files.isDirectory(dir)) {
        continue;
      }
      try (Stream<Path> files = Files.walk(dir)) {
        for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
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
      }
    }

    assertTrue(
        offenders.isEmpty(),
        "보이지 않는 제어문자가 소스에 섞였다. 컴파일·테스트는 통과하지만 포맷 검사가 CI 에서 깨진다:\n  "
            + String.join("\n  ", offenders));
  }
}

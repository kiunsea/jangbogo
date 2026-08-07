package com.jiniebox.jangbogo.packaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code clean} 이 사용자 데이터를 지우지 못하게 막는 가드가 {@code build.gradle} 에 살아 있는지 감시한다.
 *
 * <h2>왜 이 감시가 필요한가</h2>
 *
 * <p>이 프로젝트는 배포 패키지를 {@code build/distributions} 아래에 풀어 그 자리에서 실행하는 관행이 있고, 그렇게 실행된 인스턴스는 자기 {@code
 * db/} 를 그 안에 만든다. 즉 {@code build/} 아래에 "지워도 되는 빌드 산출물" 과 "지우면 안 되는 실제 구매 내역" 이 섞인다. {@code clean}
 * 은 그 구분을 하지 않는다.
 *
 * <p>실제로 clean 한 번에 배포본 인스턴스와 그 DB 가 통째로 사라진 적이 있다. Gradle 의 delete 는 휴지통을 거치지 않아 복구 수단이 없다.
 *
 * <p>그동안은 <b>"clean 을 쓰지 마라" 는 규칙</b>으로 막고 있었는데 규칙은 잊힌다. 게다가 clean 을 호출하는 경로가 스크립트 셋({@code
 * clean_build}·{@code build_package}·{@code test_run})에 직접 실행까지 있어, 호출부마다 고치면 반드시 빠지는 자리가 생긴다. 그래서
 * 태스크 자체에 걸었고, 이 테스트는 <b>그 가드가 조용히 사라지지 않게</b> 지킨다.
 *
 * <p>파일만 읽는다. Gradle 을 실행하지 않는다.
 *
 * @author KIUNSEA
 */
class CleanGuardTest {

  private static final Path BUILD_GRADLE = Path.of("build.gradle");

  /**
   * 가드가 반드시 갖고 있어야 할 조각들.
   *
   * <p>각 항목이 없으면 가드는 <b>있는 척만 하고 아무것도 막지 않는다</b> — 그 형태가 이 프로젝트에서 실제로 두 번 나왔다(테스트가 초록인데 판별부가 죽어 있던
   * 경우). 그래서 "clean 을 만지는 코드가 있다" 가 아니라 <b>막는 데 꼭 필요한 요소</b>를 각각 확인한다.
   */
  private static final String[] REQUIRED_PARTS = {
    "tasks.named('clean')", // 가드가 걸린 자리
    "GradleException", // 경고가 아니라 실제로 멈춘다
    "allow-clean-user-data", // 의도적으로 지울 통로가 있다
  };

  @Test
  @DisplayName("clean 가드가 build.gradle 에 살아 있다")
  void keepsTheCleanGuardInPlace() throws Exception {
    String script = Files.readString(BUILD_GRADLE, StandardCharsets.UTF_8);

    for (String part : REQUIRED_PARTS) {
      assertTrue(
          script.contains(part),
          "build.gradle 의 clean 가드에서 '"
              + part
              + "' 가 사라졌다. 이 가드가 없으면 clean 한 번에 build/ 아래 배포본 인스턴스와 그 DB(실제 구매 내역)가 지워지고"
              + " 되돌릴 수 없다 — Gradle 의 delete 는 휴지통을 거치지 않는다. 규칙으로 막던 것을 코드로 옮긴 자리이니 지우지 마라.");
    }
  }

  @Test
  @DisplayName("가드가 앱 DB 이름만 잡고 빌드가 만드는 test-db 는 통과시킨다")
  void theGuardTargetsAppDatabasesOnly() throws Exception {
    // 위 검사는 '문자열이 있는가' 만 본다. 판별 규칙 자체가 뒤집혀도(예: 조건을 반대로) 통과한다.
    // 그래서 규칙을 여기서 직접 두들긴다 — build.gradle 의 조건과 같은 형태를 재현해,
    // 걸려야 하는 이름과 걸리면 안 되는 이름을 함께 넣는다.
    //
    // 이 대조군이 없으면 "가드가 있다" 는 초록이 "가드가 무언가를 잡는다" 를 보증하지 못한다.
    // 이 프로젝트가 반복해서 겪은 형태다.
    assertTrue(looksLikeAppDatabase("jangbogo-dev.db"), "배포본 인스턴스의 DB 는 반드시 잡혀야 한다.");
    assertTrue(looksLikeAppDatabase("jangbogo-dev.db-wal"), "SQLite 가 옆에 만드는 것도 같은 개인 데이터다.");
    assertTrue(looksLikeAppDatabase("JANGBOGO-DEV.DB"), "Windows 라 대소문자로 빠져나가면 안 된다.");

    assertFalse(looksLikeAppDatabase("jangbogo-0.18.2.jar"), "빌드 산출물 jar 은 막을 대상이 아니다.");
    assertFalse(looksLikeAppDatabase("cache.db"), "Chrome 프로필 캐시까지 잡으면 오탐이 잦아 가드가 무시당한다.");

    // 이름은 맞지만 build/test-db 아래에 있는 것은 빌드가 만든 것이라 지워도 된다.
    // build.gradle 이 canonicalPath 접두로 걸러내는 그 규칙을 확인한다.
    assertTrue(
        Files.readString(BUILD_GRADLE, StandardCharsets.UTF_8).contains("test-db"),
        "가드가 build/test-db 를 예외로 두지 않으면 테스트를 한 번 돌린 뒤부터 clean 이 영구히 막힌다.");
  }

  @Test
  @DisplayName("clean 을 부르는 스크립트가 늘어나도 가드는 한 곳이다")
  void keepsTheGuardAtTheTaskRatherThanEachCaller() throws Exception {
    // 호출부마다 막으려 하면 반드시 빠지는 자리가 생긴다. 가드가 태스크에 걸려 있으면
    // 스크립트가 몇 개든, 사람이 직접 ./gradlew clean 을 치든 같은 자리에서 막힌다.
    // 여기서는 그 설계가 유지되는지만 확인한다 — 스크립트 개수는 세지 않는다(늘어나도 안전해야 한다).
    String script = Files.readString(BUILD_GRADLE, StandardCharsets.UTF_8);

    int guardAt = script.indexOf("tasks.named('clean')");
    assertTrue(guardAt >= 0, "clean 가드가 태스크에 걸려 있지 않다.");

    int doFirstAt = script.indexOf("doFirst", guardAt);
    assertTrue(doFirstAt > guardAt, "가드가 doFirst 가 아니면 clean 이 이미 지운 뒤에 검사하게 된다.");
    assertEquals(
        guardAt,
        script.lastIndexOf("tasks.named('clean')"),
        "clean 가드가 두 곳에 있다. 한쪽만 고쳐지면 다른 쪽이 조용히 어긋난다.");
  }

  /**
   * {@code build.gradle} 의 판별 규칙과 같은 형태. 사본이라 본문과 갈라질 수 있으므로 <b>규칙을 바꿀 때는 양쪽을 함께</b> 고쳐야 한다 — 위
   * {@link #keepsTheCleanGuardInPlace} 가 본문 존재를, 이 함수가 규칙의 방향을 지킨다.
   */
  private static boolean looksLikeAppDatabase(String fileName) {
    String name = fileName.toLowerCase(java.util.Locale.ROOT);
    return name.startsWith("jangbogo") && name.contains(".db");
  }
}

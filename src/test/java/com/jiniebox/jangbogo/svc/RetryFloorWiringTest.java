package com.jiniebox.jangbogo.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 재조회 바닥이 <b>실제로 쓰이는지</b>를 소스에서 직접 센다.
 *
 * <h2>왜 이 가드가 필요한가</h2>
 *
 * <p>이 결함은 <b>양쪽이 다 붙어야</b> 고쳐진다 — 저장부가 실패한 날짜를 적고, 조회부가 그것을 읽어 시작일을 되돌려야 한다. 한쪽만 있으면 단위테스트는 전부
 * 초록인데 실제로는 아무것도 바뀌지 않는다.
 *
 * <ul>
 *   <li>적기만 하고 읽지 않으면: 컬럼에 값만 쌓이고 조회 구간은 예전 그대로다. <b>구멍은 계속 봉인된다.</b>
 *   <li>읽기만 하고 적지 않으면: 바닥이 언제나 비어 있어 {@code resolve} 가 예전 경로로만 돈다. 역시 <b>무동작</b>이다.
 * </ul>
 *
 * <p>이 저장소는 "테스트는 초록인데 프로덕션 호출자가 0건" 을 세 번 겪었다. 그래서 숫자를 센다.
 */
class RetryFloorWiringTest {

  private static final Path MAIN = Path.of("src/main/java");

  private static final String RUNNER = "com/jiniebox/jangbogo/svc/MallOrderUpdaterRunner.java";
  private static final String DAO =
      "com/jiniebox/jangbogo/dao/JbgCollectBreakerDataAccessObject.java";
  private static final String COLLECTOR = "com/jiniebox/jangbogo/svc/mall/HanaroOffline.java";

  @Test
  @DisplayName("저장부가 바닥을 적는다 — 러너가 유일한 호출자다")
  void theRunnerWritesTheFloor() throws IOException {
    List<String> callers = productionFilesContaining(".saveRetryFrom(", DAO);

    assertFalse(callers.isEmpty(), "재조회 바닥을 적는 프로덕션 호출자가 0건이다 — 구멍이 계속 봉인된다.");
    assertEquals(List.of(RUNNER), callers, "바닥을 적는 자리가 러너 말고 또 있다 — 규칙이 갈린다.");

    // 파일에 호출이 들어 있는 것과 그 호출이 실제로 도달하는 것은 다른 사실이다. 위 검사만
    // 두고 회차 끝의 호출 한 줄을 지웠더니 그대로 초록이었다 — 이 저장소가 반복해서 겪은
    // 'green-by-construction' 그 자체다. 그래서 저장 회차의 본문에서 한 번 더 센다.
    assertTrue(
        methodBody(sourceWithoutComments(MAIN.resolve(RUNNER)), "public void run()")
            .contains("recordRetryFloors("),
        "회차가 끝나도 바닥을 적지 않는다 — 적는 코드는 있으나 도달하지 않는다.");
  }

  @Test
  @DisplayName("조회부가 바닥을 읽는다 — 읽지 않으면 적어도 소용없다")
  void theCollectorReadsTheFloor() throws IOException {
    List<String> callers = productionFilesContaining(".getRetryFrom(", DAO);

    assertFalse(callers.isEmpty(), "재조회 바닥을 읽는 프로덕션 호출자가 0건이다 — 적어도 조회 구간이 안 바뀐다.");
    assertTrue(callers.contains(COLLECTOR), "기간 조회형 수집기가 바닥을 읽지 않는다.");
  }

  @Test
  @DisplayName("바닥을 받는 resolve 오버로드에 프로덕션 호출자가 있다")
  void theTwoArgumentResolveIsActuallyUsed() throws IOException {
    // 오버로드를 만들어 놓고 예전 것을 계속 부르면 컴파일도 테스트도 통과한다.
    // 그때 조회 구간은 한 글자도 바뀌지 않는다.
    List<String> callers =
        productionFilesContaining(
            "CollectPeriod.resolve(lastStored, retryFrom,",
            "com/jiniebox/jangbogo/svc/util/CollectPeriod.java");

    assertFalse(callers.isEmpty(), "바닥을 넘기는 호출자가 0건이다 — 오버로드가 죽어 있다.");
  }

  @Test
  @DisplayName("브레이커 상태 저장이 바닥 컬럼을 함께 덮지 않는다")
  void savingBreakerStateDoesNotTouchTheFloorColumn() throws IOException {
    // saveState 의 UPSERT 가 retry_from_date 를 SET 절에 넣으면, 매 회차 브레이커 갱신이
    // 바닥을 지운다. 그러면 다음 회차가 구멍을 조회하지 못해 고친 것이 무효가 된다.
    // (실제 동작은 CollectWindowWatermarkTest 가 DB 로 재고, 여기서는 선언을 본다.)
    String saveState =
        methodBody(sourceWithoutComments(MAIN.resolve(DAO)), "public void saveState(");

    assertTrue(saveState.contains("INSERT INTO jbg_collect_breaker"), "브레이커 저장 SQL 을 찾지 못했다.");
    assertFalse(
        saveState.contains("retry_from_date"),
        "브레이커 상태 저장이 재조회 바닥을 함께 덮는다 — 바닥이 한 회차만 살아 있다가 사라진다.");
  }

  /**
   * 그 메서드 선언부터 <b>다음 메서드 선언 직전까지</b>를 잘라 낸다.
   *
   * <p>파일 전체에서 문자열을 찾으면 옆 메서드의 SQL 이 함께 걸린다 — 처음 이 가드를 그렇게 썼다가 {@code getRetryFrom} 의 SELECT 를
   * {@code saveState} 의 것으로 오인해 헛되이 붉었다.
   */
  private static String methodBody(String source, String signature) {
    int start = source.indexOf(signature);
    if (start < 0) {
      return "";
    }
    int next = source.indexOf("\n  public ", start + signature.length());
    return next < 0 ? source.substring(start) : source.substring(start, next);
  }

  /**
   * {@code src/main} 에서 이 조각을 담고 있는 파일들을 찾는다.
   *
   * <p><b>주석을 걷어내고 본다.</b> javadoc 이 "여기를 부른다" 라고 적어 둔 것과 실제로 부르는 것은 다른 사실인데, 주석을 함께 세면 설명 한 줄이
   * 배선으로 오인되어 이 감시가 통째로 무의미해진다.
   */
  private static List<String> productionFilesContaining(String needle, String excluded)
      throws IOException {

    List<String> found = new ArrayList<>();
    try (Stream<Path> files = Files.walk(MAIN)) {
      for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
        String relative = MAIN.relativize(file).toString().replace('\\', '/');
        if (relative.equals(excluded)) {
          continue;
        }
        if (sourceWithoutComments(file).contains(needle)) {
          found.add(relative);
        }
      }
    }
    return found;
  }

  /** 주석을 걷어낸 소스. */
  private static String sourceWithoutComments(Path path) throws IOException {
    return Files.readString(path, StandardCharsets.UTF_8)
        .replaceAll("(?s)/\\*.*?\\*/", "")
        .replaceAll("(?m)^\\s*//.*$", "");
  }
}

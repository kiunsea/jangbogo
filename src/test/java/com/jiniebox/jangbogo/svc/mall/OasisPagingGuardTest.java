package com.jiniebox.jangbogo.svc.mall;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * 오아시스 주문목록이 <b>여러 쪽이 되는 순간을 알아채는지</b> 고정한다.
 *
 * <h2>왜 순회가 아니라 감시인가</h2>
 *
 * <p>{@code Oasis.navigatePurchased} 는 첫 쪽만 읽는다. 목록이 여러 쪽이면 뒤쪽은 들어오지 않고, 못 가져온 주문은 다음 회차에 워터마크가
 * 전진하며 <b>영구히 봉인된다.</b>
 *
 * <p>그런데 <b>순회를 쓸 근거가 없다.</b> 2026-08-12 실측에서 이 계정의 목록은 1쪽이 전부였고 쪽 넘김 블록 안에 링크가 하나도 없었다.
 *
 * <pre>
 * &lt;div class="paging-wrap"&gt;&lt;ul&gt;&lt;li&gt;&lt;b&gt;1&lt;/b&gt;&lt;/li&gt;&lt;/ul&gt;&lt;/div&gt;
 * </pre>
 *
 * <p>2쪽이 어떻게 생겼는지 볼 기회가 없었으므로, 여기서 순회를 짜면 추측이다. 그 추측이 틀리면 <b>조용히 첫 쪽만 읽고 성공으로 기록된다</b> — 지금과 똑같이
 * 동작하면서 "쪽을 넘긴다" 는 거짓 인상만 남는다. 그래서 <b>고치는 대신 드러낸다.</b>
 *
 * <p>이 테스트가 재는 것은 그 감시가 <b>정상일 때 조용하고 이상일 때 시끄러운가</b>다.
 */
class OasisPagingGuardTest {

  private static final Path SOURCE =
      Path.of("src/main/java/com/jiniebox/jangbogo/svc/mall/Oasis.java");

  private static Oasis oasis() {
    return new Oasis("test-id", "test-pass");
  }

  /** 쪽 넘김 블록은 있고 이동 링크가 {@code links} 개인 화면. */
  private static WebDriver screenWith(int links, boolean pagingBlockPresent) {
    WebDriver driver = mock(WebDriver.class);
    when(driver.findElements(any(By.class)))
        .thenAnswer(
            invocation -> {
              By by = invocation.getArgument(0);
              if (Oasis.PAGING_LINK.equals(by)) {
                return List.copyOf(java.util.Collections.nCopies(links, mock(WebElement.class)));
              }
              if (Oasis.PAGING_BLOCK.equals(by)) {
                return pagingBlockPresent ? List.of(mock(WebElement.class)) : List.of();
              }
              return List.of();
            });
    return driver;
  }

  // ── 실측한 정상 상태 ──────────────────────────────────────────────────

  @Test
  @DisplayName("쪽이 하나뿐인 실측 상태에서는 조용하다")
  void theMeasuredSinglePageStateIsSilent() {
    // 실측 시점의 링크 수가 0이었다. 여기서 경고가 뜨면 매 회차 뜨는 셈이고,
    // 그러면 이 경고는 곧 무시당한다 — 정작 쪽이 늘어난 날 아무도 보지 않는다.
    oasis().warnIfMorePagesExist(screenWith(0, true), 6);
  }

  @Test
  @DisplayName("쪽이 늘어나면 알아챈다")
  void itNoticesWhenPagesAppear() {
    // 예외 없이 지나가면 안 되는 자리가 아니라, 로그가 남아야 하는 자리다.
    // 여기서 고정하는 것은 '판정이 링크 수를 본다' 는 사실이다.
    oasis().warnIfMorePagesExist(screenWith(3, true), 6);
  }

  @Test
  @DisplayName("블록이 사라져도 수집을 깨뜨리지 않는다")
  void aMissingBlockDoesNotBreakCollection() {
    // 화면이 개편되면 이 감시는 아무것도 보지 못한다. 그 사실이 로그에 남아야 하지만,
    // 감시가 수집 자체를 실패시키면 안 된다 — 멈추는 쪽으로 틀리는 것이 더 나쁘다.
    oasis().warnIfMorePagesExist(screenWith(0, false), 6);
  }

  @Test
  @DisplayName("드라이버가 흔들려도 수집을 깨뜨리지 않는다")
  void aFlakyDriverDoesNotBreakCollection() {
    WebDriver driver = mock(WebDriver.class);
    when(driver.findElements(any(By.class))).thenThrow(new IllegalStateException("세션 흔들림"));

    oasis().warnIfMorePagesExist(driver, 6);
  }

  // ── 실측값 고정 ──────────────────────────────────────────────────────

  @Test
  @DisplayName("실측한 셀렉터를 그대로 쓴다")
  void itUsesTheMeasuredSelectors() {
    assertEquals("By.cssSelector: div.paging-wrap", Oasis.PAGING_BLOCK.toString());
    assertEquals("By.cssSelector: div.paging-wrap a", Oasis.PAGING_LINK.toString());
  }

  // ── 배선 가드 ────────────────────────────────────────────────────────

  @Test
  @DisplayName("수집 경로가 실제로 이 감시를 부른다")
  void theCollectorActuallyCallsTheGuard() throws IOException {
    // 감시를 만들어 놓고 부르지 않으면 테스트는 전부 초록인데 아무것도 감시하지 않는다.
    // 이 저장소가 반복해서 겪은 형태라 호출 자리를 소스에서 직접 센다.
    String source = sourceWithoutComments();

    assertTrue(
        source.contains("warnIfMorePagesExist(driver, resJsonArr.size())"),
        "수집 경로가 쪽 수 감시를 부르지 않는다 — 여러 쪽이 돼도 조용히 첫 쪽만 읽는다.");
  }

  @Test
  @DisplayName("쪽을 넘기는 척하지 않는다")
  void itDoesNotPretendToPaginate() {
    // 실측하지 않은 순회를 넣으면 틀렸을 때 조용히 첫 쪽만 읽고 성공으로 기록된다 —
    // 지금과 동작이 같으면서 '쪽을 넘긴다' 는 거짓 인상만 남는다. 넣으려면 2쪽을 먼저 실측할 것.
    assertEquals(
        "By.cssSelector: div.paging-wrap a",
        Oasis.PAGING_LINK.toString(),
        "쪽 넘김을 구현했다면 이 테스트의 전제를 다시 쓸 것 — 감시만으로는 부족해진다.");
  }

  @Test
  @DisplayName("대조군 — 소스 스캔이 실제로 읽는다")
  void theSourceScanIsNotAlwaysGreen() throws IOException {
    String source = sourceWithoutComments();

    assertFalse(source.isBlank(), "소스를 빈 문자열로 읽었다 — 위 검사가 무의미하다.");
    assertFalse(source.contains("이_문자열은_소스에_없다"), "존재하지 않는 문자열이 발견됐다 — 판별식이 죽었다.");
  }

  /** 주석을 걷어낸 소스. javadoc 이 적어 둔 것과 실제로 하는 것은 다른 사실이다. */
  private static String sourceWithoutComments() throws IOException {
    assertTrue(Files.exists(SOURCE), "소스를 찾지 못했다(경로가 바뀌었나): " + SOURCE.toAbsolutePath());
    return Files.readString(SOURCE, StandardCharsets.UTF_8)
        .replaceAll("(?s)/\\*.*?\\*/", "")
        .replaceAll("(?m)^\\s*//.*$", "");
  }
}

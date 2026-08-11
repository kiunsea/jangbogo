package com.jiniebox.jangbogo.svc.mall;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 화면이 안내하는 쇼핑몰 주소가 <b>실제로 수집하는 사이트</b>와 같은지 본다.
 *
 * <h2>왜 필요한가</h2>
 *
 * <p>메인 화면의 쇼핑몰 카드는 주소를 <b>HTML 에 직접 적어</b> 두고 있다. 같은 사실이 {@link MallRegistry} 에도 있으므로 <b>한 사실이 두
 * 곳에 산다</b> — 이 저장소가 반복해서 대가를 치른 형태다.
 *
 * <p>실제로 치렀다. v0.19.0 이 하나로 수집을 {@code nhhanaro.co.kr} 로 옮겼는데 화면은 구 주소({@code nonghyupmall.com})를
 * 그대로 안내하고 있었다. 컴파일도 테스트도 전부 초록이었고, <b>사용자가 화면에서 발견했다.</b> 링크를 누른 사람은 이 앱이 더 이상 수집하지 않는 사이트에 착지해
 * "구매내역이 여기 있는데 왜 안 모이나" 를 묻게 된다.
 *
 * <p>카드를 모델에서 그리도록 바꾸면 중복 자체가 사라지지만, 그것은 화면 세 곳을 다시 짜는 일이라 여기서 할 일이 아니다. 대신 <b>어긋나면 빨개지게</b> 한다.
 *
 * <p>비교는 <b>호스트</b>로 한다. 화면은 사람이 들어갈 첫 화면을, 레지스트리는 로그인 지점을 담아 경로가 다를 수 있다(오아시스가 {@code /login} 이다).
 * 경로까지 맞추라고 하면 정상 상태에서 빨개진다.
 */
class MallSiteLinkTest {

  private static final Path INDEX = Path.of("src/main/resources/templates/index.html");

  /** {@code URL : <a ... href="...">} — 줄바꿈 위치가 카드마다 달라 태그 경계로 찾는다. */
  private static final Pattern URL_ROW =
      Pattern.compile("URL\\s*:\\s*<a\\b[^>]*?href=\"([^\"]+)\"", Pattern.DOTALL);

  @Test
  @DisplayName("화면이 안내하는 주소가 등록된 몰의 사이트와 같다")
  void everyDisplayedUrlPointsAtARegisteredMall() throws IOException {
    Set<String> displayed = displayedHosts();
    Set<String> registered = registeredHosts();

    assertEquals(
        registered, displayed, "화면의 안내 주소와 실제 수집 대상이 어긋났다 — 링크를 누른 사람이 수집하지 않는 사이트에 착지한다.");
  }

  @Test
  @DisplayName("수집을 그만둔 사이트를 화면이 계속 안내하지 않는다")
  void theScreenDoesNotLinkToAbandonedSites() throws IOException {
    // v0.19.0 이 하나로 수집을 옮긴 뒤 이 자리가 구 주소로 남아 있었다. 위 검사와 결론은 같지만
    // 실패 메시지가 다르다 — 어느 방향으로 어긋났는지가 바로 읽혀야 고치는 사람이 헤매지 않는다.
    String source = Files.readString(INDEX, StandardCharsets.UTF_8);
    Set<String> registered = registeredHosts();

    for (String host : displayedHosts()) {
      assertTrue(
          registered.contains(host),
          "등록된 수집기가 쓰지 않는 사이트를 화면이 안내한다: " + host + " (등록된 것: " + registered + ")");
    }
    assertFalse(source.contains("nonghyupmall.com"), "온라인몰은 등록에서 뺐는데 화면이 아직 그리로 보낸다.");
  }

  // ── 대조군 — 판별식이 살아 있는가 ────────────────────────────────────────

  @Test
  @DisplayName("대조군 — 주소 행을 실제로 찾아낸다")
  void theProbeIsNotAlwaysGreen() throws IOException {
    // 정규식이 한 건도 못 찾으면 위 두 검사는 빈 집합끼리 비교하거나 빈 반복으로 통과한다.
    // 화면이 통째로 잘못돼도 초록인 상태라, 찾은 개수를 등록된 몰 수로 고정한다.
    assertEquals(
        MallRegistry.values().length,
        displayedHosts().size(),
        "쇼핑몰 카드의 주소 행을 등록된 몰 수만큼 찾지 못했다 — 검사가 죽었거나 카드가 빠졌다.");
  }

  /** 화면의 쇼핑몰 카드가 안내하는 호스트들. */
  private static Set<String> displayedHosts() throws IOException {
    assertTrue(Files.exists(INDEX), "화면 파일을 찾지 못했다(경로가 바뀌었나): " + INDEX.toAbsolutePath());

    Set<String> hosts = new TreeSet<>();
    Matcher matcher = URL_ROW.matcher(Files.readString(INDEX, StandardCharsets.UTF_8));
    while (matcher.find()) {
      hosts.add(hostOf(matcher.group(1)));
    }
    return hosts;
  }

  /** 등록된 몰들이 실제로 로그인하는 호스트들. */
  private static Set<String> registeredHosts() {
    Set<String> hosts = new TreeSet<>();
    for (MallRegistry mall : MallRegistry.values()) {
      hosts.add(hostOf(mall.loginUrl()));
    }
    return new LinkedHashSet<>(hosts);
  }

  private static String hostOf(String url) {
    URI uri = URI.create(url.trim());
    assertTrue(uri.getHost() != null && !uri.getHost().isBlank(), "호스트를 읽을 수 없는 주소다: " + url);
    return uri.getHost();
  }
}

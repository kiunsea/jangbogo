package com.jiniebox.jangbogo.svc.mall;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 오프라인 수집기가 <b>실제로 돌 자리에 꽂혀 있는지</b>를 고정한다.
 *
 * <h2>왜 이 가드가 필요한가</h2>
 *
 * <p>이 저장소는 "테스트는 초록인데 프로덕션 호출자가 0건" 을 세 번 겪었다. 수집기는 특히 그렇게 되기 쉽다 — 클래스를 만들고 파서 테스트를 붙이면 전부 초록이지만,
 * {@code MallRegistry} 에 넣지 않으면 <b>한 번도 실행되지 않는다.</b> 그리고 그 상태는 아무것도 실패시키지 않는다.
 *
 * <p>여기서 고정하는 것은 넷이다 — 등록됐는가, 이름이 유일한가, 이름이 한 곳에서만 정의되는가, 실측하지 않은 자리를 추측으로 채우지 않았는가.
 *
 * @author KIUNSEA
 */
class HanaroOfflineWiringTest {

  private static final String CREDENTIAL_ID = "test-id";
  private static final String CREDENTIAL_PW = "test-pass";

  @Test
  @DisplayName("HANARO 는 오프라인 수집기 하나만 돌린다")
  void hanaroRunsOnlyTheOfflineCollector() {
    // 온라인몰은 사용자가 이용하지 않는다(2026-08-11 확인). 등록해 두면 매 회차 안 쓰는
    // 사이트에 실계정 로그인을 한 번 더 하고, 늘 0건이라 '정상인 0건' 과 '깨진 셀렉터' 를
    // 가릴 수 없게 만든다.
    List<MallRegistry.CollectorSpec> collectors = MallRegistry.HANARO.collectors();

    assertEquals(1, collectors.size(), "쓰지 않는 수집기가 등록돼 있다 — 매 회차 로그인을 헛쓴다.");
    assertEquals(HanaroOffline.COLLECTOR, collectors.get(0).name());
  }

  @Test
  @DisplayName("공장이 실제로 그 클래스를 만든다 — 등록만 하고 딴 것을 만들면 조용히 어긋난다")
  void factoryProducesTheOfflineCollector() {
    MallRegistry.CollectorSpec offline = MallRegistry.HANARO.collectors().get(0);

    assertInstanceOf(
        HanaroOffline.class, offline.create(CREDENTIAL_ID, CREDENTIAL_PW), "공장이 다른 수집기를 만든다.");
  }

  @Test
  @DisplayName("계정 검증과 세션 착지가 오프라인 사이트를 향한다")
  void verificationAndLoginPointAtTheOfflineSite() {
    // 검증 수집기가 예전 이름으로 남아 있으면 폴백이 첫 수집기를 집어 <b>우연히</b> 맞는다 —
    // 선언과 실제가 어긋난 채 조용히 굴러가는 상태다. 이름으로 실제로 찾히는지 본다.
    assertEquals(
        HanaroOffline.COLLECTOR,
        MallRegistry.HANARO.verificationCollector().name(),
        "계정 연결이 쓰지 않는 사이트로 로그인한다.");
    assertEquals(
        HanaroOffline.LOGIN_URL,
        MallRegistry.HANARO.loginUrl(),
        "세션 캡처가 구 사이트로 착지한다 — 거기서 만든 세션은 이 몰의 수집에 쓰이지 않는다.");
  }

  @Test
  @DisplayName("수집기 이름은 저장소 전체에서 유일하다 — 겹치면 브레이커 상태가 한 키에 섞인다")
  void collectorNameIsUnique() {
    long same = 0;
    for (MallRegistry mall : MallRegistry.values()) {
      for (MallRegistry.CollectorSpec spec : mall.collectors()) {
        if (spec.name().equals(HanaroOffline.COLLECTOR)) {
          same++;
        }
      }
      for (MallRegistry.SessionCollectorSpec spec : mall.sessionCollectors()) {
        if (spec.name().equals(HanaroOffline.COLLECTOR)) {
          same++;
        }
      }
    }
    assertEquals(1, same, "같은 수집기 이름이 두 번 선언됐다.");
  }

  @Test
  @DisplayName("레지스트리가 이름을 문자열로 다시 적지 않는다 — 두 곳에 적으면 워터마크가 조용히 끊긴다")
  void registryReferencesTheNameConstant() throws IOException {
    String source = sourceOf("src/main/java/com/jiniebox/jangbogo/svc/mall/MallRegistry.java");

    assertTrue(
        source.contains("HanaroOffline.COLLECTOR"),
        "레지스트리가 상수 대신 문자열 리터럴을 적고 있다 — 한쪽만 고쳐져도 컴파일은 통과한다.");
    assertFalse(source.contains("\"HanaroOffline\""), "레지스트리에 수집기 이름이 문자열로 박혀 있다. 상수를 참조할 것.");
  }

  @Test
  @DisplayName("수집기가 조회 시작일을 자기 이름으로 좁혀 유도한다")
  void derivesItsOwnWatermark() throws IOException {
    String source = sourceOf("src/main/java/com/jiniebox/jangbogo/svc/mall/HanaroOffline.java");

    assertTrue(
        source.contains("getLastCollectedDate("), "마지막 수집일을 조회하지 않는다 — 매 회차 기본 범위를 통째로 훑게 된다.");
    assertTrue(source.contains("CollectPeriod.resolve("), "조회 구간을 정책으로 계산하지 않는다.");

    // 그냥 contains("COLLECTOR") 는 동어반복이다 — 상수 <b>선언</b>만으로도 만족된다.
    // 조회 호출에 그 상수를 실제로 넘기는지를 봐야 한다. 첫 판이 그 구멍으로 초록이었다.
    int call = source.indexOf("getLastCollectedDate(");
    String callSite = source.substring(call, Math.min(source.length(), call + 200));
    assertTrue(
        callSite.contains("COLLECTOR"),
        "조회 호출이 자기 수집기 이름을 넘기지 않는다 — 몰 단위로 구하면 다른 수집기의 최신 주문에 시작점이 밀린다: " + callSite);
  }

  @Test
  @DisplayName("실측하지 않은 자리는 비어 있다 — 추측해 채우면 만료를 정상으로 읽는다")
  void doesNotGuessUnmeasuredCapabilities() {
    // 로그인/로그아웃 상태의 쿠키 '이름' 이 같다는 것이 실측으로 확인됐다. 이름만으로는 인증 여부를
    // 가릴 수 없으므로 하나라도 넣으면 미인증 스냅샷이 그대로 통과한다.
    assertTrue(MallRegistry.HANARO.authCookieNames().isEmpty(), "미실측 몰에 인증 쿠키 이름을 추측해 넣었다.");
    assertTrue(MallRegistry.HANARO.sessionCollectors().isEmpty(), "미실측 몰에 세션 주입 경로를 넣었다.");
  }

  // ── 대조군 — 판별식이 살아 있는가 ────────────────────────────────────────

  @Test
  @DisplayName("대조군 — 없는 문자열은 찾지 못한다")
  void theProbeIsNotAlwaysGreen() throws IOException {
    String source = sourceOf("src/main/java/com/jiniebox/jangbogo/svc/mall/HanaroOffline.java");

    assertFalse(source.isBlank(), "소스를 빈 문자열로 읽었다 — 위 검사들은 전부 무의미하다.");
    assertFalse(source.contains("이_문자열은_소스에_없다"), "존재하지 않는 문자열이 발견됐다 — 판별식이 죽었다.");
  }

  private static String sourceOf(String relativePath) throws IOException {
    Path path = Paths.get(relativePath);
    assertTrue(Files.exists(path), "소스를 찾지 못했다(경로가 바뀌었나): " + path.toAbsolutePath());
    return Files.readString(path, StandardCharsets.UTF_8);
  }
}

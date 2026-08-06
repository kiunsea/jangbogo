package com.jiniebox.jangbogo.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jiniebox.jangbogo.svc.util.SessionProfilePolicy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;

/**
 * 설정 파일 → System property 다리 검증.
 *
 * <h2>무엇을 고정하는가</h2>
 *
 * <p>이 다리는 <b>배포본에서 킬스위치를 켤 수단이 아예 없던 것</b>을 메우려고 만들었다. 그래서 "옮겨진다" 만 확인하면 부족하고, 옮기는 과정에서 원래 성립하던
 * 성질이 깨지지 않았는지가 같이 걸려 있다.
 *
 * <ul>
 *   <li>설정에 적으면 {@link SessionProfilePolicy} 가 그 값을 본다 — 이게 없으면 배포본은 여전히 못 켠다.
 *   <li>{@code -D} 가 이긴다 — 사고 났을 때 되돌리려고 붙인 명령줄 인자가 설정 파일에 밀리면 안 된다.
 *   <li>값이 없으면 아무 일도 없다 — 기본 꺼짐이 깨지면 킬스위치가 킬스위치가 아니다.
 * </ul>
 *
 * <p>여기에 <b>등록</b>도 같이 본다. 클래스가 아무리 옳아도 {@code spring.factories} 한 줄이 빠지면 그냥 실행되지 않는다 — 컴파일도 되고 나머지
 * 테스트도 통과하는데 배포본에서만 설정이 안 먹는, 가장 알아채기 어려운 실패다.
 *
 * <p>브라우저·네트워크·DB 를 쓰지 않는다.
 *
 * @author KIUNSEA
 */
class SessionProfilePropertyBridgeTest {

  /** 접두사 스캔이 정본 키 두 개 말고도 동작하는지 보려고 쓰는 가짜 키. */
  private static final String PROBE_KEY = SessionProfilePropertyBridge.PREFIX + "probe";

  private static final Path APPLICATION_YML = Path.of("src/main/resources/application.yml");

  private static final Path SPRING_FACTORIES =
      Path.of("src/main/resources/META-INF/spring.factories");

  private final Map<String, String> saved = new HashMap<>();

  /** 이 테스트가 만지는 시스템 프로퍼티는 JVM 전역이다. 새어 나가면 다른 테스트가 엉뚱하게 깨진다. */
  private static List<String> touchedKeys() {
    return List.of(
        SessionProfilePolicy.ENABLED_PROPERTY, SessionProfilePolicy.ROOT_PROPERTY, PROBE_KEY);
  }

  @BeforeEach
  void clearTouchedProperties() {
    for (String key : touchedKeys()) {
      String previous = System.getProperty(key);
      if (previous != null) {
        saved.put(key, previous);
      }
      System.clearProperty(key);
    }
  }

  @AfterEach
  void restoreTouchedProperties() {
    for (String key : touchedKeys()) {
      System.clearProperty(key);
    }
    saved.forEach(System::setProperty);
    saved.clear();
  }

  // ---------------------------------------------------------------
  // (a) 설정값을 옮긴다
  // ---------------------------------------------------------------

  @Test
  @DisplayName("설정 파일에 적은 값이 System property 로 옮겨진다 — 배포본에서 켤 수 있게 되는 지점이다")
  void copiesConfiguredValueIntoSystemProperties() {
    bridge(environmentWith(Map.of(SessionProfilePolicy.ENABLED_PROPERTY, "true")));

    assertEquals("true", System.getProperty(SessionProfilePolicy.ENABLED_PROPERTY));
    assertTrue(SessionProfilePolicy.isEnabled(), "설정으로 켰는데 정책은 꺼짐으로 읽는다 — 다리가 끊겼다.");
  }

  @Test
  @DisplayName("프로필 루트도 함께 옮긴다")
  void copiesTheProfileRootToo() {
    // 실제 PC 경로를 쓰지 않는다. 이 저장소는 PUBLIC 이다.
    bridge(environmentWith(Map.of(SessionProfilePolicy.ROOT_PROPERTY, "build/test-profiles")));

    assertEquals("build/test-profiles", System.getProperty(SessionProfilePolicy.ROOT_PROPERTY));
    assertEquals(Path.of("build/test-profiles"), SessionProfilePolicy.profileRoot());
  }

  @Test
  @DisplayName("접두사 아래 키는 목록에 없어도 옮긴다 — 키가 늘 때 이 다리를 같이 고쳐야 하는 상황을 막는다")
  void copiesAnyKeyUnderThePrefix() {
    bridge(environmentWith(Map.of(PROBE_KEY, "값")));

    assertEquals("값", System.getProperty(PROBE_KEY));
  }

  // ---------------------------------------------------------------
  // (b) 명령줄이 이긴다
  // ---------------------------------------------------------------

  @Test
  @DisplayName("이미 -D 로 준 값은 설정 파일이 덮어쓰지 못한다 — 양쪽 방향 모두")
  void neverOverwritesAnExistingSystemProperty() {
    // 끄려고 붙인 -D 가 설정 파일에 밀리면, 사고 났을 때 되돌릴 수단이 사라진다.
    System.setProperty(SessionProfilePolicy.ENABLED_PROPERTY, "false");
    bridge(environmentWith(Map.of(SessionProfilePolicy.ENABLED_PROPERTY, "true")));

    assertEquals("false", System.getProperty(SessionProfilePolicy.ENABLED_PROPERTY));
    assertFalse(SessionProfilePolicy.isEnabled(), "-D 로 끈 것이 설정 파일에 밀렸다.");

    // 반대 방향도 같다. 한 번 띄워 보려고 붙인 -D 가 무시되면 안 된다.
    System.setProperty(SessionProfilePolicy.ENABLED_PROPERTY, "true");
    bridge(environmentWith(Map.of(SessionProfilePolicy.ENABLED_PROPERTY, "false")));

    assertEquals("true", System.getProperty(SessionProfilePolicy.ENABLED_PROPERTY));
    assertTrue(SessionProfilePolicy.isEnabled(), "-D 로 켠 것이 설정 파일에 밀렸다.");
  }

  // ---------------------------------------------------------------
  // (c) 값이 없으면 기본 꺼짐 그대로
  // ---------------------------------------------------------------

  @Test
  @DisplayName("설정에 값이 없으면 아무것도 심지 않는다 — 기본은 여전히 꺼짐이다")
  void plantsNothingWhenNothingIsConfigured() {
    bridge(environmentWith(Map.of()));

    assertNull(System.getProperty(SessionProfilePolicy.ENABLED_PROPERTY), "없는 값을 만들어 냈다.");
    assertNull(System.getProperty(SessionProfilePolicy.ROOT_PROPERTY), "없는 값을 만들어 냈다.");
    assertFalse(SessionProfilePolicy.isEnabled());
  }

  @Test
  @DisplayName("빈 값은 값이 아니다 — 공백만 적어 둔 설정으로는 켜지지 않는다")
  void treatsBlankAsAbsent() {
    bridge(environmentWith(Map.of(SessionProfilePolicy.ENABLED_PROPERTY, "   ")));

    assertNull(System.getProperty(SessionProfilePolicy.ENABLED_PROPERTY));
    assertFalse(SessionProfilePolicy.isEnabled());
  }

  // ---------------------------------------------------------------
  // 등록과 배포 기본값 — 여기가 깨지면 위 전부가 무의미해진다
  // ---------------------------------------------------------------

  @Test
  @DisplayName("spring.factories 에 EnvironmentPostProcessor 로 등록돼 있다")
  void isRegisteredAsAnEnvironmentPostProcessor() throws Exception {
    String factories = read(SPRING_FACTORIES);

    assertTrue(
        factories.contains("org.springframework.boot.env.EnvironmentPostProcessor"),
        "spring.factories 에 EnvironmentPostProcessor 키가 없다. 다리가 아예 실행되지 않는다.");
    assertTrue(
        factories.contains(SessionProfilePropertyBridge.class.getName()),
        "spring.factories 에 다리 클래스 등록이 없다. 배포본에서만 설정이 먹지 않는 상태가 된다.");
  }

  @Test
  @DisplayName("ConfigData 보다 뒤에 돈다 — 앞서 돌면 설정 파일이 아직 안 읽힌 상태다")
  void runsAfterConfigDataIsLoaded() {
    assertEquals(
        Ordered.LOWEST_PRECEDENCE,
        new SessionProfilePropertyBridge().getOrder(),
        "우선순위를 올리면 application.yml 과 config/ 폴더가 읽히기 전에 돌아 빈손으로 끝난다.");
  }

  @Test
  @DisplayName("application.yml 이 킬스위치를 기본 false 로 배포한다")
  void shipsTheSwitchTurnedOff() throws Exception {
    List<String> lines = Files.readAllLines(APPLICATION_YML, StandardCharsets.UTF_8);

    int declaration = -1;
    for (int i = 0; i < lines.size(); i++) {
      if (lines.get(i).trim().equals("session-profile:")) {
        declaration = i;
        break;
      }
    }
    assertTrue(
        declaration >= 0,
        "application.yml 에 session-profile 블록이 없다. 이 블록이 사라지면 배포본에서 킬스위치를 켤 수단이 다시 없어진다.");

    String firstEntry = null;
    for (int i = declaration + 1; i < lines.size(); i++) {
      String line = lines.get(i).trim();
      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }
      firstEntry = line;
      break;
    }
    assertEquals(
        "enabled: false",
        firstEntry,
        "배포 기본값이 꺼짐이 아니다. 명시적으로 true 를 줘야만 켜지는 성질을 깨면 킬스위치가 킬스위치가 아니다.");
  }

  // ---------------------------------------------------------------
  // 도우미
  // ---------------------------------------------------------------

  private static void bridge(StandardEnvironment environment) {
    // SpringApplication 은 이 다리가 쓰지 않는다. 실제 기동을 흉내 낼 이유가 없다.
    new SessionProfilePropertyBridge(LogFactory.getLog(SessionProfilePropertyBridge.class))
        .postProcessEnvironment(environment, null);
  }

  /**
   * 지정한 값만 담은 환경.
   *
   * <p>실제 시스템 프로퍼티·환경변수 원본은 떼어 낸다. 붙여 두면 이 테스트가 개발 PC 에 뭐가 설정돼 있느냐에 따라 다르게 돈다 — 특히 "값이 없으면 아무것도 심지
   * 않는다" 는 그 순간 기계마다 결과가 갈린다.
   */
  private static StandardEnvironment environmentWith(Map<String, Object> properties) {
    StandardEnvironment environment = new StandardEnvironment();
    MutablePropertySources sources = environment.getPropertySources();
    sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
    sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
    sources.addFirst(new MapPropertySource("테스트설정", new LinkedHashMap<>(properties)));
    return environment;
  }

  private static String read(Path path) throws Exception {
    assertTrue(
        Files.isRegularFile(path), path + " 이 없다. 테스트 작업 디렉터리는 프로젝트 루트여야 한다 (Gradle Test 기본값).");
    return Files.readString(path, StandardCharsets.UTF_8);
  }
}

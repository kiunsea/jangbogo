package com.jiniebox.jangbogo.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * jackson YAML 매퍼 호환성 테스트 (Phase 2-1 후속).
 *
 * <p>이 프로젝트는 YAML 을 세 곳에서 서로 다른 방식으로 다룬다.
 *
 * <ul>
 *   <li>{@code JangbogoConfig} — {@code new ObjectMapper(new YAMLFactory())} 로 {@code
 *       config/jbg_config.yml} 읽기 (기동 경로)
 *   <li>{@code MallAccountYmlService} — {@code YAMLFactory.builder()} + {@code
 *       findAndRegisterModules()} 로 계정 파일 읽고 쓰기
 *   <li>{@code ExportService} — {@code YAMLFactory} 에 {@code MINIMIZE_QUOTES} 를 켜서 내보내기
 * </ul>
 *
 * <p>과거 {@code build.gradle} 이 {@code jackson-dataformat-yaml} 만 2.15.3 으로 못박아, Spring Boot BOM 이
 * 해석하는 {@code jackson-core}/{@code jackson-databind}(2.19.2)와 4개 마이너가 어긋나 있었다. jackson 은 모듈 버전 일치를
 * 전제하므로 이 조합은 보증되지 않는다. 선언을 제거해 BOM 에 정렬하면서, 위 세 사용 패턴이 실제로 동작하는지를 이 테스트로 고정한다.
 *
 * <p>버전 일치 자체도 함께 검사한다. 다시 어긋나면 여기서 잡힌다.
 */
class YamlMapperCompatibilityTest {

  @TempDir File tempDir;

  @Test
  @DisplayName("jackson databind 와 dataformat-yaml 이 같은 버전으로 해석된다")
  void yamlModuleVersionMatchesDatabind() {
    Version databind = new ObjectMapper().version();
    Version yaml = new YAMLFactory().version();

    assertEquals(
        databind.getMajorVersion() + "." + databind.getMinorVersion(),
        yaml.getMajorVersion() + "." + yaml.getMinorVersion(),
        "jackson 모듈 버전이 어긋났다. build.gradle 이 jackson-dataformat-yaml 버전을 직접 고정하고 있지 않은지 확인할 것"
            + " (databind="
            + databind
            + ", yaml="
            + yaml
            + ")");
  }

  @Test
  @DisplayName("JangbogoConfig 방식 — 기본 YAMLFactory 로 설정 맵을 읽는다")
  void readsConfigMapWithPlainFactory() throws IOException {
    File yml = write("jbg_config.yml", "localdb-name: jangbogo-dev\nmax-retry-count: 3\n");

    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    @SuppressWarnings("unchecked")
    Map<String, Object> loaded = mapper.readValue(yml, Map.class);

    assertEquals("jangbogo-dev", loaded.get("localdb-name"));
    assertEquals(3, loaded.get("max-retry-count"));
  }

  @Test
  @DisplayName("MallAccountYmlService 방식 — builder + findAndRegisterModules 로 왕복한다")
  void roundTripsWithBuilderConfiguredFactory() throws IOException {
    YAMLFactory factory =
        YAMLFactory.builder().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER).build();
    ObjectMapper mapper = new ObjectMapper(factory);
    mapper.findAndRegisterModules();

    Map<String, Object> source = new LinkedHashMap<>();
    source.put("site", "테스트몰");
    source.put("enabled", true);

    File yml = new File(tempDir, "account.yml");
    mapper.writerWithDefaultPrettyPrinter().writeValue(yml, source);

    String text = Files.readString(yml.toPath(), StandardCharsets.UTF_8);
    assertFalse(text.startsWith("---"), "WRITE_DOC_START_MARKER 비활성이 적용되어야 한다");

    @SuppressWarnings("unchecked")
    Map<String, Object> loaded = mapper.readValue(yml, Map.class);
    assertEquals(source, loaded, "쓰고 읽은 값이 같아야 한다");
  }

  @Test
  @DisplayName("ExportService 방식 — MINIMIZE_QUOTES 를 켠 매퍼로 내보낸다")
  void writesWithMinimizeQuotes() throws IOException {
    ObjectMapper mapper =
        new ObjectMapper(
            new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));

    Map<String, Object> source = new LinkedHashMap<>();
    source.put("name", "테스트상품가");
    source.put("qty", 2);

    File yml = new File(tempDir, "export.yml");
    mapper.writeValue(yml, source);

    String text = Files.readString(yml.toPath(), StandardCharsets.UTF_8);
    assertNotNull(text);
    assertTrue(text.contains("테스트상품가"), "값이 그대로 기록되어야 한다");
    assertFalse(text.contains("\"테스트상품가\""), "MINIMIZE_QUOTES 가 적용되어 불필요한 따옴표가 없어야 한다");
  }

  private File write(String name, String content) throws IOException {
    File file = new File(tempDir, name);
    Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
    return file;
  }
}

package com.jiniebox.jangbogo.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code jbg_order.collector} 가 <b>실제로 채워지는지</b>를 끝에서 끝까지 고정한다 (하나로 온라인/오프라인 분리).
 *
 * <h2>왜 이 가드가 필요한가</h2>
 *
 * <p>기간 조회형 수집기는 "이 수집기가 마지막으로 저장한 구매일" 부터 다시 조회한다. 그 기준일은 {@code jbg_order.collector} 로 좁힌 최대값에서
 * 유도되므로, <b>그 컬럼이 비면 기준일이 늘 없음이 되어 매 회차 기본 범위를 통째로 다시 훑는다.</b>
 *
 * <p>그런데 그 상태는 <b>아무것도 실패시키지 않는다.</b> 수집은 되고, 저장도 되고, 테스트도 전부 초록이다. 그저 조회 범위가 조용히 넓어질 뿐이라 아무도 모른다.
 * 이 저장소는 "테스트는 초록인데 호출자가 0건" 을 세 번 겪었고, 그때마다 이런 형태였다.
 *
 * <p>연결 고리가 셋이라 어느 하나만 끊겨도 같은 결과가 된다.
 *
 * <ol>
 *   <li>{@code MallOrderUpdater} 가 수집 결과에 수집기 이름을 새긴다
 *   <li>{@code MallOrderUpdaterRunner} 가 그 값을 읽어 저장부로 넘긴다
 *   <li>{@code JbgOrderDataAccessObject} 의 INSERT 가 그 컬럼을 포함한다
 * </ol>
 *
 * <p>1번은 아래에서 <b>실제로 실행해</b> 확인하고, 2·3번은 소스를 읽어 확인한다(DB·브라우저 없이 도는 묶음을 유지해야 하므로).
 *
 * @author KIUNSEA
 */
class CollectorStampWiringTest {

  // ── 1. 새기는가 (실행으로 확인) ──────────────────────────────────────────

  @Test
  @DisplayName("수집 결과의 모든 주문에 수집기 이름이 새겨진다")
  void stampsEveryOrderWithItsCollector() {
    MallOrderUpdater mou = new MallOrderUpdater();

    JSONArray result = mou.collectFrom("HanaroOffline", () -> ordersOf(3));

    assertEquals(3, result.size());
    for (Object item : result) {
      JSONObject order = (JSONObject) item;
      assertEquals(
          "HanaroOffline",
          order.get(MallOrderUpdater.COLLECTOR_KEY),
          "주문에 수집기 이름이 없다 — 다음 회차 조회 시작일을 유도할 수 없다.");
    }
  }

  @Test
  @DisplayName("수집기마다 자기 이름이 새겨진다 — 한 몰에 수집기가 둘일 때 섞이면 안 된다")
  void stampsEachCollectorSeparately() {
    // 이 구분이 무너지면 온라인 주문이 최근이라는 이유로 오프라인 조회 시작점이 밀려
    // 그 사이의 오프라인 거래를 영구히 놓친다. 그것이 이 컬럼을 만든 이유다.
    MallOrderUpdater mou = new MallOrderUpdater();

    JSONArray online = mou.collectFrom("Hanaro", () -> ordersOf(1));
    JSONArray offline = mou.collectFrom("HanaroOffline", () -> ordersOf(1));

    assertEquals("Hanaro", ((JSONObject) online.get(0)).get(MallOrderUpdater.COLLECTOR_KEY));
    assertEquals(
        "HanaroOffline", ((JSONObject) offline.get(0)).get(MallOrderUpdater.COLLECTOR_KEY));
  }

  @Test
  @DisplayName("0건이면 새길 것도 없다 — 빈 배열을 돌려준다")
  void survivesAnEmptyResult() {
    MallOrderUpdater mou = new MallOrderUpdater();

    assertTrue(mou.collectFrom("Hanaro", JSONArray::new).isEmpty());
    assertTrue(mou.collectFrom("Hanaro", () -> null).isEmpty());
  }

  // ── 2·3. 넘기고 저장하는가 (소스로 확인) ─────────────────────────────────

  @Test
  @DisplayName("저장부가 수집기 이름을 읽어 DAO 로 넘긴다")
  void runnerPassesTheCollectorToTheDao() throws IOException {
    String source = sourceOf("src/main/java/com/jiniebox/jangbogo/svc/MallOrderUpdaterRunner.java");

    assertTrue(
        source.contains("MallOrderUpdater.COLLECTOR_KEY"),
        "저장부가 새겨진 값을 읽지 않는다 — 문자열을 따로 적었거나 아예 안 읽는다.");
    assertTrue(
        source.contains("addWithConnection(") && source.contains("collector)"),
        "읽기는 하는데 DAO 로 넘기지 않는다 — 값이 중간에서 버려진다.");
  }

  @Test
  @DisplayName("DAO 의 주문 INSERT 가 collector 컬럼을 포함한다")
  void insertIncludesTheCollectorColumn() throws IOException {
    String source =
        sourceOf("src/main/java/com/jiniebox/jangbogo/dao/JbgOrderDataAccessObject.java");

    assertTrue(
        source.contains(
            "INSERT INTO jbg_order (serial_num, date_time, mall_name, seq_mall," + " collector)"),
        "살아 있는 INSERT 가 collector 를 빼고 있다.");
    assertTrue(
        source.contains("seq_mall=? AND collector=?"),
        "기준일 조회가 수집기로 좁히지 않는다 — 몰 단위로 구하면 다른 수집기의 최신 주문에 시작점이 밀린다.");
  }

  @Test
  @DisplayName("스키마에 collector 컬럼이 선언돼 있다 — SchemaMigrator 가 이것을 보고 ALTER 한다")
  void schemaDeclaresTheCollectorColumn() throws IOException {
    String schema = sourceOf("src/main/resources/schema.sql");

    int orderTable = schema.indexOf("CREATE TABLE IF NOT EXISTS jbg_order");
    assertTrue(orderTable >= 0, "jbg_order 정의를 찾지 못했다.");
    String body = schema.substring(orderTable, schema.indexOf(");", orderTable));

    assertTrue(body.contains("collector"), "jbg_order 에 collector 컬럼 선언이 없다.");
  }

  // ── 대조군 — 판별식이 살아 있는가 ────────────────────────────────────────
  //
  // 위 네 검사는 "찾으면 통과" 라 파일을 못 읽어도, 경로가 틀려도 조용히 초록이 될 수 있다.
  // 이 저장소는 판별식을 무력화해도 전부 통과하던 가드를 실제로 겪었다.

  @Test
  @DisplayName("대조군 — 없는 문자열은 찾지 못한다")
  void theProbeIsNotAlwaysGreen() throws IOException {
    String source =
        sourceOf("src/main/java/com/jiniebox/jangbogo/dao/JbgOrderDataAccessObject.java");

    assertFalse(source.contains("이_문자열은_소스에_없다"), "존재하지 않는 문자열이 발견됐다 — 판별식이 죽었다.");
    assertFalse(source.isBlank(), "소스를 빈 문자열로 읽었다 — 위 검사들은 전부 무의미하다.");
  }

  // ── 도구 ────────────────────────────────────────────────────────────────

  private static String sourceOf(String relativePath) throws IOException {
    Path path = Paths.get(relativePath);
    assertTrue(Files.exists(path), "소스를 찾지 못했다(경로가 바뀌었나): " + path.toAbsolutePath());
    return Files.readString(path, StandardCharsets.UTF_8);
  }

  @SuppressWarnings("unchecked")
  private static JSONArray ordersOf(int count) {
    JSONArray arr = new JSONArray();
    for (int i = 0; i < count; i++) {
      JSONObject order = new JSONObject();
      // 합성값이다. 실제 구매일·금액을 테스트에 넣지 않는다.
      order.put("serial", "2099010" + i + "_1000");
      order.put("datetime", "2099010" + i);
      order.put("items", new JSONArray());
      arr.add(order);
    }
    return arr;
  }
}

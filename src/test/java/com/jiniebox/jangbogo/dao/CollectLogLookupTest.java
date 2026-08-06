package com.jiniebox.jangbogo.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 단일 로그 조회({@link JbgCollectLogDataAccessObject#getLog(int)})가 실제로 그 seq 의 행을 연다는 것을 고정한다.
 *
 * <p>이 자리는 오랫동안 {@code WHERE seq = " + seq} 문자열 조립이었다. 통합 단계에서 바인딩으로 옮겼는데, <b>그 경로를 지나가는 테스트가 한 건도
 * 없었다.</b> 프로덕션 호출자는 상세 모달과 스크린샷 서빙 둘뿐이고 화면으로만 확인되는 자리다.
 *
 * <p>테스트가 필요한 진짜 이유는 실패 형태다. {@code getLog} 는 예외를 삼키고 {@code null} 을 돌려준다. 그래서 바인딩이 어긋나면 오류가 나는 게
 * 아니라 화면에 <b>"로그를 찾을 수 없습니다"</b> 만 뜬다 — 데이터는 멀쩡히 있는데 조회가 죽은 상태와, 정말로 그 행이 없는 상태가 화면에서 같아 보인다. 그 형태는
 * 사람이 알아채기까지 오래 걸린다.
 *
 * <p>행을 여러 개 넣고 <b>각각을 지목해서</b> 여는 이유도 같다. 한 건만 넣고 조회하면 자리표시자가 엉뚱한 값에 묶여 있어도(또는 조건이 통째로 무시돼도) 어차피 그
 * 한 행이 나와서 통과한다.
 *
 * <p>테스트마다 {@code @TempDir} 의 새 SQLite 파일을 쓴다 — 기준선 DB 에 닿지 않는다.
 *
 * @author KIUNSEA
 */
class CollectLogLookupTest {

  private static final String DB_URL_PROPERTY = "jangbogo.localdb.url";

  private String previousUrl;
  private JbgCollectLogDataAccessObject dao;

  @BeforeEach
  void isolateDatabase(@TempDir Path tempDir) {
    previousUrl = System.getProperty(DB_URL_PROPERTY);
    String dbUrl =
        "jdbc:sqlite:" + tempDir.resolve("collect-log-lookup.db").toString().replace('\\', '/');
    System.setProperty(DB_URL_PROPERTY, dbUrl);
    SchemaMigrator.resetForTest();
    dao = new JbgCollectLogDataAccessObject(); // 생성자가 스키마를 보장한다
  }

  @AfterEach
  void restoreDatabase() {
    SchemaMigrator.resetForTest();
    if (previousUrl == null) {
      System.clearProperty(DB_URL_PROPERTY);
    } else {
      System.setProperty(DB_URL_PROPERTY, previousUrl);
    }
  }

  @Test
  @DisplayName("단일 조회는 지목한 seq 의 행을 연다 — 이웃 행이 아니다")
  void getLogOpensExactlyTheRequestedRow() {
    addLog("첫째몰", "SUCCESS", 1000L);
    addLog("둘째몰", "FAIL", 2000L);
    addLog("셋째몰", "SKIPPED", 3000L);

    JSONArray all = dao.getAllLogs(10);
    assertEquals(3, all.size(), "사전 조건이 깨졌다 — 넣은 행이 다 들어가지 않았다.");

    // 넣은 행 전부를 각각 지목해서 연다. 하나라도 다른 행이 나오면 자리표시자가 어긋난 것이다.
    for (Object entry : all) {
      JSONObject expected = (JSONObject) entry;
      int seq = ((Number) expected.get("seq")).intValue();

      JSONObject actual = dao.getLog(seq);

      assertNotNull(actual, "seq=" + seq + " 행이 분명히 있는데 조회가 null 을 돌려줬다. 바인딩이 죽으면 이 형태로 나타난다.");
      assertEquals(seq, ((Number) actual.get("seq")).intValue());
      assertEquals(expected.get("mall_name"), actual.get("mall_name"), "다른 행이 열렸다.");
      assertEquals(expected.get("status"), actual.get("status"), "다른 행이 열렸다.");
    }
  }

  @Test
  @DisplayName("없는 seq 는 아무 행도 열지 않는다 — 아무거나 돌려주지 않는다")
  void getLogReturnsNullForUnknownSeq() {
    addLog("첫째몰", "SUCCESS", 1000L);

    // 조건이 통째로 무시되면(자리표시자가 안 묶이면) 여기서 첫 행이 나온다.
    assertNull(dao.getLog(999_999), "없는 seq 인데 행이 나왔다 — WHERE 조건이 실제로 적용되지 않는다.");
  }

  private void addLog(String mallName, String status, long startedAt) {
    dao.addLog(
        1,
        mallName,
        "SSG",
        status,
        0,
        0,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        startedAt,
        startedAt + 1);
  }
}

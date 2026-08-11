package com.jiniebox.jangbogo.svc.mall;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.jiniebox.jangbogo.dao.JbgCollectBreakerDataAccessObject;
import com.jiniebox.jangbogo.dao.JbgOrderDataAccessObject;
import com.jiniebox.jangbogo.dao.LocalDBConnection;
import com.jiniebox.jangbogo.dao.SchemaTestSupport;
import com.jiniebox.jangbogo.svc.util.CollectBreakerPolicy;
import com.jiniebox.jangbogo.svc.util.CollectPeriod;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 부분 저장 실패가 조회 기준일을 넘기지 못하게 하는 경계 (재현 테스트).
 *
 * <h2>무엇이 깨져 있었나</h2>
 *
 * <p>{@code CollectPeriod} javadoc 은 자기교정을 약속한다 — "저장이 실패하면 기준일도 자동으로 뒤로 남아 다음 회차가 다시 가져온다". 그 약속에는
 * 조건이 숨어 있었다: <b>실패가 그 회차에서 가장 늦은 날짜여야 한다.</b> {@code MallOrderUpdaterRunner} 는 주문 하나가 깨지면 그것만 롤백하고
 * 루프를 계속하므로, 같은 회차의 더 늦은 주문이 커밋되면 {@code MAX(date_time)} 이 실패한 날짜를 <b>지나간다.</b> 다음 회차의 시작일이 그 뒤가
 * 되고, 그 구간은 영영 조회되지 않는다.
 *
 * <p>고치기 전 이 테스트의 첫 건은 실제로 붉었다 — {@code 저장하지 못한 20260601 을 지나 20260610 부터 조회한다}.
 *
 * <h2>왜 여기서 재는가</h2>
 *
 * <p>구멍은 저장부(러너)에서 나지만 <b>손해는 조회부에서</b> 난다. 그래서 회차가 남긴 DB 상태를 그대로 만들어 놓고 다음 회차의 조회 구간을 묻는다. 바닥을 정하는
 * 쪽 규칙은 {@code MallOrderUpdaterRetryFloorTest} 가 따로 잰다.
 *
 * <p>테스트마다 {@code @TempDir} 의 새 SQLite 파일을 쓴다 — 기준선 DB 에 닿지 않는다.
 */
class CollectWindowWatermarkTest {

  private static final String DB_URL_PROPERTY = "jangbogo.localdb.url";
  private static final int SEQ_HANARO = MallRegistry.HANARO.seq();

  /** 저장에 실패해 롤백된 주문의 구매일. */
  private static final String UNSAVED_YMD = "20260601";

  /** 같은 회차에서 커밋된, 더 늦은 주문의 구매일. */
  private static final String SAVED_YMD = "20260610";

  private String previousUrl;

  @BeforeEach
  void isolateDatabase(@TempDir Path tempDir) {
    previousUrl = System.getProperty(DB_URL_PROPERTY);
    System.setProperty(
        DB_URL_PROPERTY,
        "jdbc:sqlite:" + tempDir.resolve("watermark-test.db").toString().replace('\\', '/'));
    SchemaTestSupport.remigrate();
  }

  @AfterEach
  void restoreDatabase() {
    SchemaTestSupport.reset();
    if (previousUrl == null) {
      System.clearProperty(DB_URL_PROPERTY);
    } else {
      System.setProperty(DB_URL_PROPERTY, previousUrl);
    }
  }

  @Test
  @DisplayName("부분 저장 실패가 있었던 회차 뒤의 조회 시작일은 실패한 날짜를 지나가지 않는다")
  void aPartiallyFailedRoundDoesNotSealTheUnsavedDate() throws Exception {
    // 회차가 남긴 상태를 그대로 만든다 — 이른 날짜는 롤백돼 없고, 늦은 날짜만 커밋됐다.
    commitOrder(SAVED_YMD);
    breakerDao().saveRetryFrom(SEQ_HANARO, HanaroOffline.COLLECTOR, UNSAVED_YMD);

    CollectPeriod.Window next = offline().resolveWindow();

    assertFalse(
        next.start().isAfter(LocalDate.of(2026, 6, 1)),
        "저장하지 못한 " + UNSAVED_YMD + " 을 지나 " + next.startYmd() + " 부터 조회한다 — 그 구간은 영영 다시 조회되지 않는다.");
    assertEquals(UNSAVED_YMD, next.startYmd(), "실패한 날짜부터 다시 조회해야 한다.");
  }

  @Test
  @DisplayName("바닥이 없으면 예전 그대로 — 저장된 최대 구매일부터 조회한다")
  void withoutAFloorTheWindowIsUnchanged() throws Exception {
    // 이 수정이 기존 동작을 건드리지 않는다는 것을 고정한다. 공용 경로라 회귀가 곧 전 몰의 손해다.
    commitOrder(SAVED_YMD);

    assertEquals(SAVED_YMD, offline().resolveWindow().startYmd());
  }

  @Test
  @DisplayName("바닥이 저장된 최대 구매일보다 늦으면 무시한다 — 시작일을 앞당기지 않는다")
  void aLaterFloorNeverPushesTheWindowForward() throws Exception {
    // 바닥은 뒤로 당기는 값이다. 앞으로 미는 데 쓰이면 그 순간 이 클래스가 막으려는 결함이 된다.
    commitOrder(UNSAVED_YMD);
    breakerDao().saveRetryFrom(SEQ_HANARO, HanaroOffline.COLLECTOR, SAVED_YMD);

    assertEquals(UNSAVED_YMD, offline().resolveWindow().startYmd());
  }

  @Test
  @DisplayName("바닥을 지우면 유도값으로 되돌아간다")
  void clearingTheFloorRestoresTheDerivedStart() throws Exception {
    commitOrder(SAVED_YMD);
    breakerDao().saveRetryFrom(SEQ_HANARO, HanaroOffline.COLLECTOR, UNSAVED_YMD);
    breakerDao().saveRetryFrom(SEQ_HANARO, HanaroOffline.COLLECTOR, null);

    assertEquals(SAVED_YMD, offline().resolveWindow().startYmd());
  }

  @Test
  @DisplayName("바닥은 다른 수집기의 조회 구간을 건드리지 않는다")
  void theFloorIsPerCollector() throws Exception {
    // 한 몰에 수집기가 둘일 수 있다. 몰 단위로 적으면 한쪽의 저장 실패가 다른 쪽을 매 회차
    // 2년치 재조회로 끌고 간다 — collector 를 나눈 이유가 여기서도 그대로 적용된다.
    commitOrder(SAVED_YMD);
    breakerDao().saveRetryFrom(SEQ_HANARO, "OtherCollector", "20250101");

    assertEquals(SAVED_YMD, offline().resolveWindow().startYmd());
  }

  @Test
  @DisplayName("브레이커 상태 갱신이 바닥을 지우지 않는다")
  void savingBreakerStateKeepsTheFloor() throws Exception {
    // 매 회차 브레이커가 갱신되므로, 그 UPSERT 가 이 컬럼을 함께 덮으면 바닥은 늘 한 회차만
    // 살아 있다가 사라진다. 다음 회차가 구멍을 조회하지 못하므로 고친 것이 무효가 된다.
    commitOrder(SAVED_YMD);
    breakerDao().saveRetryFrom(SEQ_HANARO, HanaroOffline.COLLECTOR, UNSAVED_YMD);

    breakerDao()
        .saveState(
            SEQ_HANARO,
            HanaroOffline.COLLECTOR,
            CollectBreakerPolicy.onSuccess(),
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            "성공");

    assertEquals(
        UNSAVED_YMD,
        breakerDao().getRetryFrom(SEQ_HANARO, HanaroOffline.COLLECTOR),
        "브레이커 갱신이 재조회 바닥을 지웠다.");
  }

  private static HanaroOffline offline() {
    return new HanaroOffline("test-id", "test-pass");
  }

  private static JbgCollectBreakerDataAccessObject breakerDao() {
    return new JbgCollectBreakerDataAccessObject();
  }

  /** 수집 회차가 주문 하나를 커밋했을 때와 같은 상태를 만든다. */
  private void commitOrder(String ymd) throws Exception {
    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();
      conn.txOpen();
      new JbgOrderDataAccessObject()
          .addWithConnection(
              conn, ymd + "_1000", ymd, null, String.valueOf(SEQ_HANARO), HanaroOffline.COLLECTOR);
      conn.txCommit();
    } finally {
      if (conn != null) {
        conn.close();
      }
    }
  }
}

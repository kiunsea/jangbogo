package com.jiniebox.jangbogo.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 이번 회차가 각 수집기에 남길 <b>재조회 바닥</b>을 정하는 규칙.
 *
 * <p>배경: 러너는 주문 하나가 깨지면 그것만 롤백하고 루프를 계속한다. 같은 회차의 더 늦은 주문이 커밋되면 {@code jbg_order} 의 {@code
 * MAX(date_time)} 이 실패한 날짜를 지나가고, 다음 회차의 조회 시작일이 그 뒤가 되어 <b>실패한 구간이 영영 조회되지 않는다.</b> 그래서 "저장을 확인하지
 * 못한 가장 이른 구매일" 을 바닥으로 적어 시작일을 되돌린다.
 *
 * <p>여기서 재는 것은 <b>그 바닥을 언제 적고 언제 지우는가</b> 뿐이다. 적힌 바닥이 실제로 조회 구간을 되돌리는지는 {@code
 * CollectWindowWatermarkTest} 가 실제 DB 로 잰다.
 */
class MallOrderUpdaterRetryFloorTest {

  private static final String COLLECTOR = "HanaroOffline";
  private static final String OTHER = "Emart";

  private static MallOrderUpdater.CollectOutcome success(String collector) {
    return new MallOrderUpdater.CollectOutcome(
        collector, MallOrderUpdater.CollectOutcome.SUCCESS, null, null);
  }

  private static MallOrderUpdater.CollectOutcome empty(String collector) {
    return new MallOrderUpdater.CollectOutcome(
        collector, MallOrderUpdater.CollectOutcome.EMPTY, null, "수집 0건");
  }

  private static MallOrderUpdater.CollectOutcome failed(String collector) {
    return new MallOrderUpdater.CollectOutcome(
        collector, MallOrderUpdater.CollectOutcome.FAIL, null, null);
  }

  private static MallOrderUpdater.CollectOutcome skipped(String collector) {
    return new MallOrderUpdater.CollectOutcome(
        collector, MallOrderUpdater.CollectOutcome.SKIPPED, null, "브레이커 차단");
  }

  private static Map<String, String> unsaved(String collector, String ymd) {
    Map<String, String> map = new LinkedHashMap<>();
    map.put(collector, ymd);
    return map;
  }

  // ── 바닥을 적는 자리 ──────────────────────────────────────────────────

  @Test
  @DisplayName("저장하지 못한 주문이 있으면 그 날짜가 바닥이 된다")
  void anUnsavedOrderBecomesTheFloor() {
    Map<String, String> floors =
        MallOrderUpdaterRunner.retryFloors(
            List.of(success(COLLECTOR)), unsaved(COLLECTOR, "20260601"));

    assertEquals("20260601", floors.get(COLLECTOR));
  }

  @Test
  @DisplayName("저장하지 못한 주문이 여럿이면 가장 이른 날짜가 바닥이 된다")
  void theEarliestUnsavedDateWins() {
    // 늦은 쪽을 남기면 그보다 이른 구멍은 그대로 봉인된다.
    Map<String, String> ledger = new LinkedHashMap<>();
    MallOrderUpdaterRunner.noteUnsaved(ledger, COLLECTOR, "20260610");
    MallOrderUpdaterRunner.noteUnsaved(ledger, COLLECTOR, "20260601");
    MallOrderUpdaterRunner.noteUnsaved(ledger, COLLECTOR, "20260615");

    assertEquals(
        "20260601",
        MallOrderUpdaterRunner.retryFloors(List.of(success(COLLECTOR)), ledger).get(COLLECTOR));
  }

  @Test
  @DisplayName("0건 회차에서도 저장 실패가 있었으면 바닥을 적는다")
  void anEmptyRoundStillRecordsWhatItFailedToSave() {
    Map<String, String> floors =
        MallOrderUpdaterRunner.retryFloors(
            List.of(empty(COLLECTOR)), unsaved(COLLECTOR, "20260601"));

    assertEquals("20260601", floors.get(COLLECTOR));
  }

  // ── 바닥을 지우는 자리 ────────────────────────────────────────────────

  @Test
  @DisplayName("전부 저장된 성공 회차는 바닥을 지운다")
  void aFullySavedSuccessClearsTheFloor() {
    Map<String, String> floors =
        MallOrderUpdaterRunner.retryFloors(List.of(success(COLLECTOR)), Map.of());

    assertTrue(floors.containsKey(COLLECTOR), "지우라는 지시가 없다 — 바닥이 영원히 남는다.");
    assertNull(floors.get(COLLECTOR), "null 이 '지우라' 는 뜻이다.");
  }

  @Test
  @DisplayName("0건(EMPTY) 만으로는 바닥을 지우지 않는다")
  void anEmptyRoundNeverClearsTheFloor() {
    // 0건은 '정말 안 샀다' 와 '셀렉터가 깨졌다' 를 구분하지 못한다. 뒤쪽인데 지우면
    // 이전 회차의 구멍이 그 순간 봉인된다 — 이 수정이 막으려는 바로 그 실패다.
    Map<String, String> floors =
        MallOrderUpdaterRunner.retryFloors(List.of(empty(COLLECTOR)), Map.of());

    assertFalse(floors.containsKey(COLLECTOR), "0건 회차가 바닥을 지웠다.");
  }

  // ── 손대지 않는 자리 ──────────────────────────────────────────────────

  @Test
  @DisplayName("실패한 수집기의 바닥은 건드리지 않는다")
  void aFailedCollectorIsLeftAlone() {
    // 자기 조회 구간을 한 번도 훑지 못했다. 지우면 이전 구멍이 사라지고, 새로 적으면
    // 훑지도 않은 구간을 근거로 적는 것이 된다.
    assertTrue(MallOrderUpdaterRunner.retryFloors(List.of(failed(COLLECTOR)), Map.of()).isEmpty());
  }

  @Test
  @DisplayName("건너뛴 수집기의 바닥은 건드리지 않는다")
  void aSkippedCollectorIsLeftAlone() {
    assertTrue(MallOrderUpdaterRunner.retryFloors(List.of(skipped(COLLECTOR)), Map.of()).isEmpty());
  }

  @Test
  @DisplayName("한 수집기의 저장 실패가 다른 수집기의 바닥을 만들지 않는다")
  void oneCollectorsFailureDoesNotLeakIntoAnother() {
    // seq=1 은 수집기가 둘이다. 몰 단위로 적으면 한쪽의 실패가 다른 쪽을 매 회차
    // 2년치 재조회로 끌고 간다.
    Map<String, String> floors =
        MallOrderUpdaterRunner.retryFloors(
            List.of(success(COLLECTOR), success(OTHER)), unsaved(COLLECTOR, "20260601"));

    assertEquals("20260601", floors.get(COLLECTOR));
    assertNull(floors.get(OTHER), "멀쩡한 수집기에 남의 실패 날짜가 적혔다.");
  }

  // ── 적을 수 없는 값 ───────────────────────────────────────────────────

  @Test
  @DisplayName("수집기 이름이 없는 주문은 바닥을 만들지 않는다")
  void anUnstampedOrderCannotDefineAFloor() {
    // 어느 조회 시작일을 되돌려야 할지 정할 수 없다.
    Map<String, String> ledger = new LinkedHashMap<>();
    MallOrderUpdaterRunner.noteUnsaved(ledger, null, "20260601");
    MallOrderUpdaterRunner.noteUnsaved(ledger, "  ", "20260601");

    assertTrue(ledger.isEmpty());
  }

  @Test
  @DisplayName("구매일을 읽지 못한 주문은 바닥을 만들지 않는다")
  void anOrderWithoutADateCannotDefineAFloor() {
    // 저장할 키가 없어 다시 가져와도 같은 자리에서 걸린다. 바닥으로 삼으면
    // 그 수집기가 영원히 기본 범위를 훑는다.
    Map<String, String> ledger = new LinkedHashMap<>();
    MallOrderUpdaterRunner.noteUnsaved(ledger, COLLECTOR, null);

    assertTrue(ledger.isEmpty());
  }

  @Test
  @DisplayName("결과 목록이 비어 있으면 아무것도 적지 않는다")
  void noOutcomesMeansNoWrites() {
    assertTrue(
        MallOrderUpdaterRunner.retryFloors(List.of(), unsaved(COLLECTOR, "20260601")).isEmpty());
    assertTrue(MallOrderUpdaterRunner.retryFloors(null, null).isEmpty());
  }
}

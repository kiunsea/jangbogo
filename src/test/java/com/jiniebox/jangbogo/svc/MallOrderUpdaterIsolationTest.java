package com.jiniebox.jangbogo.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jiniebox.jangbogo.svc.util.CollectStep;
import java.util.List;
import org.json.simple.JSONArray;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * seq=1 처럼 수집기가 여러 개인 쇼핑몰에서, 한 수집기의 실패가 다른 수집기를 막지 않는지 검증한다.
 *
 * <p>배경: 2026-07-28 실행에서 SSG 구매목록 파싱 실패가 seq=1 수집 전체를 중단시켜 Emart(오프라인 영수증)에 도달조차 못 했다. 두 수집기는 서로
 * 독립적인 데이터원이므로 격리되어야 한다. 다만 격리가 "실패를 삼키는 것"이 되면 v0.8.0 에서 제거한 결함이 되살아나므로, 실패는 반드시 {@code
 * partialFailures} 에 남아 호출측이 jbg_collect_log 에 기록할 수 있어야 한다.
 *
 * <p>실계정·브라우저 없이 동작하도록 수집 동작을 람다로 주입해 검증한다.
 */
class MallOrderUpdaterIsolationTest {

  private static JSONArray itemsOf(String... names) {
    JSONArray arr = new JSONArray();
    for (String n : names) {
      arr.add(n);
    }
    return arr;
  }

  @Test
  @DisplayName("앞 수집기가 실패해도 뒤 수집기는 실행되고 결과가 보존된다")
  void failureInOneCollectorDoesNotBlockTheNext() {
    MallOrderUpdater mou = new MallOrderUpdater();

    JSONArray failed =
        mou.collectFrom(
            "SSG",
            () -> {
              throw CollectStep.wrap(
                  null, "SSG", "navigatePurchased", "td[2]/p", new IllegalStateException("파싱 실패"));
            });
    JSONArray succeeded = mou.collectFrom("Emart", () -> itemsOf("영수증1", "영수증2"));

    // 실패한 쪽은 빈 배열 — 예외가 전파되지 않아 다음 수집기가 실행될 수 있다
    assertTrue(failed.isEmpty(), "실패한 수집기는 빈 결과를 반환해야 한다");
    // 뒤 수집기의 결과는 온전히 보존된다
    assertEquals(2, succeeded.size(), "뒤 수집기 결과가 보존되어야 한다");
  }

  @Test
  @DisplayName("격리된 실패는 삼켜지지 않고 부분 실패 목록에 컨텍스트와 함께 남는다")
  void isolatedFailureIsStillRecorded() {
    MallOrderUpdater mou = new MallOrderUpdater();

    mou.collectFrom(
        "SSG",
        () -> {
          throw CollectStep.wrap(
              null, "SSG", "navigatePurchased", "td[2]/p", new IllegalStateException("파싱 실패"));
        });
    mou.collectFrom("Emart", () -> itemsOf("영수증1"));

    List<MallOrderUpdater.CollectFailure> failures = mou.getPartialFailures();
    assertEquals(1, failures.size(), "실패한 수집기 수만큼 기록되어야 한다");

    MallOrderUpdater.CollectFailure f = failures.get(0);
    assertEquals("SSG", f.collector(), "어느 수집기가 실패했는지 구분되어야 한다");
    assertNotNull(f.cause(), "실패 컨텍스트가 있어야 한다");
    assertEquals("navigatePurchased", f.cause().getStepName(), "실패 단계가 보존되어야 한다");
    assertEquals("td[2]/p", f.cause().getTargetSelector(), "실패 셀렉터가 보존되어야 한다");
  }

  @Test
  @DisplayName("CollectException 이 아닌 예외도 컨텍스트로 감싸 기록된다")
  void nonCollectExceptionIsWrapped() {
    MallOrderUpdater mou = new MallOrderUpdater();

    JSONArray result =
        mou.collectFrom(
            "Oasis",
            () -> {
              throw new RuntimeException("드라이버 생성 실패");
            });

    assertTrue(result.isEmpty());
    List<MallOrderUpdater.CollectFailure> failures = mou.getPartialFailures();
    assertEquals(1, failures.size());
    assertNotNull(failures.get(0).cause(), "일반 예외도 CollectException 으로 감싸져야 한다");
  }

  @Test
  @DisplayName("모두 성공하면 부분 실패는 비어 있다")
  void noFailuresWhenAllSucceed() {
    MallOrderUpdater mou = new MallOrderUpdater();

    mou.collectFrom("SSG", () -> itemsOf("주문1"));
    mou.collectFrom("Emart", () -> itemsOf("영수증1"));

    assertTrue(mou.getPartialFailures().isEmpty(), "성공만 했으면 부분 실패가 없어야 한다");
  }

  @Test
  @DisplayName("수집기가 null 을 반환해도 빈 배열로 정규화된다")
  void nullResultIsNormalized() {
    MallOrderUpdater mou = new MallOrderUpdater();

    JSONArray result = mou.collectFrom("Hanaro", () -> null);

    assertNotNull(result, "null 대신 빈 배열이어야 한다");
    assertTrue(result.isEmpty());
    assertTrue(mou.getPartialFailures().isEmpty(), "null 반환은 실패가 아니다");
  }
}

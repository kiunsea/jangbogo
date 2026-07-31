package com.jiniebox.jangbogo.svc;

import com.jiniebox.jangbogo.dao.JbgCollectBreakerDataAccessObject;
import com.jiniebox.jangbogo.dao.JbgMallDataAccessObject;
import com.jiniebox.jangbogo.svc.mall.Emart;
import com.jiniebox.jangbogo.svc.mall.Hanaro;
import com.jiniebox.jangbogo.svc.mall.Oasis;
import com.jiniebox.jangbogo.svc.mall.Ssg;
import com.jiniebox.jangbogo.svc.util.CollectBreakerPolicy;
import com.jiniebox.jangbogo.svc.util.CollectStep;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class MallOrderUpdater {

  private static final Logger logger = LogManager.getLogger(MallOrderUpdater.class);

  /**
   * 한 쇼핑몰(seq)에 수집기가 여러 개 붙는 경우(seq=1 은 SSG + Emart), 일부만 실패한 사실.
   *
   * @param collector 수집기 이름 (SSG / Emart 등)
   * @param cause 실패 컨텍스트를 담은 예외
   */
  public record CollectFailure(String collector, CollectException cause) {}

  /**
   * 수집기 하나의 이번 회차 결과.
   *
   * <p>실패만 모으던 {@link CollectFailure} 로는 <b>연속 실패 카운트를 리셋할 근거가 없다</b> — 성공을 기록하지 않으면 "이번엔 됐다"를 알 수
   * 없기 때문이다. 그래서 성공·실패·건너뜀을 모두 남긴다. (Phase 3-3)
   *
   * @param collector 수집기 이름 (SSG / Emart 등)
   * @param status SUCCESS / FAIL / SKIPPED
   * @param cause 실패 컨텍스트 (실패가 아니면 null)
   * @param reason 건너뛴 사유 (건너뜀이 아니면 null)
   */
  public record CollectOutcome(
      String collector, String status, CollectException cause, String reason) {

    public static final String SUCCESS = "SUCCESS";
    public static final String FAIL = "FAIL";
    public static final String SKIPPED = "SKIPPED";

    public boolean isFailure() {
      return FAIL.equals(status);
    }

    public boolean isSuccess() {
      return SUCCESS.equals(status);
    }
  }

  /** 이번 수집에서 각 수집기가 어떻게 끝났는지. */
  private final List<CollectOutcome> outcomes = new ArrayList<>();

  /** 브레이커 상태 저장소. 판정 규칙은 {@link CollectBreakerPolicy} 가 갖는다. */
  private final JbgCollectBreakerDataAccessObject breakerDao =
      new JbgCollectBreakerDataAccessObject();

  /**
   * 이번 수집의 수집기별 결과를 반환한다. 호출측(runner)이 각각을 jbg_collect_log 에 기록해야 한다.
   *
   * @return 수집기별 결과 (없으면 빈 리스트)
   */
  public List<CollectOutcome> getOutcomes() {
    return new ArrayList<>(outcomes);
  }

  /**
   * 이번 수집에서 발생한 부분 실패 목록을 반환한다.
   *
   * @return 부분 실패 목록 (없으면 빈 리스트)
   */
  public List<CollectFailure> getPartialFailures() {
    List<CollectFailure> failures = new ArrayList<>();
    for (CollectOutcome outcome : outcomes) {
      if (outcome.isFailure()) {
        failures.add(new CollectFailure(outcome.collector(), outcome.cause()));
      }
    }
    return failures;
  }

  /**
   * 각 쇼핑몰에서의 주문 내역들을 수집한다
   *
   * @return
   * @throws Exception
   */
  public JSONArray collectItems(String seqMall, String mallId, String mallPw) throws Exception {
    logger.info("구매내역 수집 시작 - seqMall: {}, mallId: {}", seqMall, mallId);

    // 수집 시작 전에 로그인 시간 갱신
    JbgMallDataAccessObject jaDao = new JbgMallDataAccessObject();
    JSONObject accessInfo = jaDao.getAccessInfo(seqMall);
    if (accessInfo == null) {
      jaDao.add(seqMall, 1, null, null);
    } else {
      jaDao.updateLastSigninTime(seqMall);
    }

    // 브레이커의 백오프 기준값. 주기를 못 읽으면 0 을 넘겨 정책 기본값을 쓰게 한다.
    int intervalMinutes = 0;
    if (accessInfo != null && accessInfo.get("collect_interval_minutes") != null) {
      try {
        intervalMinutes = Integer.parseInt(accessInfo.get("collect_interval_minutes").toString());
      } catch (NumberFormatException ignore) {
      }
    }
    final int interval = intervalMinutes;

    JSONArray itemArr = new JSONArray();
    int seqMallInt = Integer.parseInt(seqMall);
    int attempted = 0;
    if (seqMallInt == 1) {
      // seq=1 은 수집기가 둘이다. 한쪽이 실패해도 다른 쪽은 반드시 시도한다.
      // (SSG 온라인몰과 Emart 오프라인 영수증은 서로 독립적인 데이터원이라, 하나의 실패가 다른 하나를 막을 이유가 없다)
      attempted = 2;
      itemArr.addAll(
          collectFrom("SSG", seqMallInt, interval, () -> new Ssg(mallId, mallPw).getItems()));
      itemArr.addAll(
          collectFrom("Emart", seqMallInt, interval, () -> new Emart(mallId, mallPw).getItems()));
    } else if (seqMallInt == 2) {
      attempted = 1;
      itemArr.addAll(
          collectFrom("Oasis", seqMallInt, interval, () -> new Oasis(mallId, mallPw).getItems()));
    } else if (seqMallInt == 3) {
      attempted = 1;
      itemArr.addAll(
          collectFrom("Hanaro", seqMallInt, interval, () -> new Hanaro(mallId, mallPw).getItems()));
    }

    List<CollectFailure> failures = getPartialFailures();

    // 시도한 수집기가 전부 실패했다면 이번 수집은 실패다. 컨텍스트를 담은 채로 상위에 전파한다.
    // 건너뛴 수집기는 실패가 아니므로 이 판정에 들어가지 않는다 — 브레이커가 이미 판단해서 막은 것을
    // 다시 실패로 세면 같은 사실이 두 번 계산된다.
    if (attempted > 0 && failures.size() == attempted) {
      throw failures.get(0).cause();
    }

    long skipped = outcomes.stream().filter(o -> CollectOutcome.SKIPPED.equals(o.status())).count();
    logger.info(
        "전체 수집 완료 - 총 {} 건 (부분 실패 {} 건, 브레이커 건너뜀 {} 건)", itemArr.size(), failures.size(), skipped);
    return itemArr;
  }

  /**
   * 수집기 하나를 실행한다. 실패해도 예외를 전파하지 않고 {@link #partialFailures} 에 쌓아 다음 수집기가 계속 실행되게 한다.
   *
   * <p>실패를 삼키는 것이 아니다 — 호출측 runner 가 {@link #getPartialFailures()} 로 꺼내 jbg_collect_log 에 FAIL 로
   * 기록한다. 이 구분이 없으면 v0.8.0 에서 제거한 "예외를 삼키고 빈 결과를 반환해 성공으로 보이는" 패턴이 되살아난다.
   *
   * @param name 수집기 이름 (로그·실패 기록용)
   * @param collector 수집 동작
   * @return 수집 결과 (실패 시 빈 배열)
   */
  // 테스트에서 직접 호출하기 위해 package-private
  JSONArray collectFrom(String name, Supplier<JSONArray> collector) {
    return collectFrom(name, 0, 0, collector);
  }

  /**
   * 브레이커 판정을 거쳐 수집기 하나를 실행한다.
   *
   * <p><b>판정을 수집기 진입 전에 한다.</b> 브라우저는 각 수집기의 {@code getItems()} 안에서 뜨므로, 여기서 걸러내면 로그인 시도도 브라우저 기동도
   * 일어나지 않는다 — 차단된 사이트를 그만 두드린다는 목적이 그대로 달성된다. (Phase 3-3)
   *
   * @param name 수집기 이름
   * @param seqMall 쇼핑몰 seq ({@code 0} 이면 브레이커를 적용하지 않는다 — 단위테스트용)
   * @param intervalMinutes 몰의 수집 주기 (백오프 기준값)
   * @param collector 수집 동작
   * @return 수집 결과 (실패·건너뜀이면 빈 배열)
   */
  JSONArray collectFrom(
      String name, int seqMall, int intervalMinutes, Supplier<JSONArray> collector) {

    if (seqMall > 0) {
      CollectBreakerPolicy.State state = breakerDao.getState(seqMall, name);
      CollectBreakerPolicy.Decision decision =
          CollectBreakerPolicy.decide(state, intervalMinutes, System.currentTimeMillis());
      if (!decision.shouldRun()) {
        logger.warn(
            "{} 수집 건너뜀 ({}) - 재시도 예정: {}",
            name,
            decision.reason,
            new java.util.Date(decision.notBefore));
        outcomes.add(new CollectOutcome(name, CollectOutcome.SKIPPED, null, decision.reason));
        return new JSONArray();
      }
    }

    logger.info("{} 구매 내역 수집 시작", name);
    try {
      JSONArray items = collector.get();
      logger.info("{} 수집 완료 - {} 건", name, items != null ? items.size() : 0);
      outcomes.add(new CollectOutcome(name, CollectOutcome.SUCCESS, null, null));
      recordBreaker(seqMall, name, true, null);
      return items != null ? items : new JSONArray();
    } catch (Exception e) {
      CollectException ce = unwrapCollectException(e);
      if (ce == null) {
        ce = CollectStep.wrap(null, name, "collect", null, e);
      }
      outcomes.add(new CollectOutcome(name, CollectOutcome.FAIL, ce, null));
      recordBreaker(seqMall, name, false, ce.getMessage());
      logger.error(
          "{} 수집 실패 (다른 수집기는 계속 진행) - 단계: {}, 원인: {}", name, ce.getStepName(), e.getMessage());
      return new JSONArray();
    }
  }

  /** 브레이커 상태를 갱신한다. 트립이 새로 발생하면 경보를 남긴다. */
  private void recordBreaker(int seqMall, String name, boolean success, String reason) {
    if (seqMall <= 0) {
      return;
    }
    try {
      long now = System.currentTimeMillis();
      if (success) {
        breakerDao.saveState(seqMall, name, CollectBreakerPolicy.onSuccess(), now, "성공");
        return;
      }

      CollectBreakerPolicy.State before = breakerDao.getState(seqMall, name);
      CollectBreakerPolicy.State after = CollectBreakerPolicy.onFailure(before, now);
      breakerDao.saveState(seqMall, name, after, 0, reason);

      if (after.isTripped() && !before.isTripped()) {
        // 경보. 결정 1 의 "스케줄 일시중단 + 경보" 중 경보에 해당한다.
        logger.error(
            "[수집기 자동 차단] {} (몰 seq={}) — 연속 {}회 실패로 브레이커가 열렸다. {}분 뒤 한 번 복귀를 시도한다."
                + " auto_collect 설정은 건드리지 않았다. 마지막 사유: {}",
            name,
            seqMall,
            after.consecutiveFailures,
            CollectBreakerPolicy.cooldownMinutes(),
            reason);
      }
    } catch (Exception e) {
      // 브레이커 갱신 실패가 수집 자체를 막아서는 안 된다.
      logger.warn("브레이커 상태 갱신 실패 ({}): {}", name, e.getMessage());
    }
  }

  /** 예외 체인을 거슬러 올라가 첫 번째 CollectException을 찾는다. 없으면 null. */
  private CollectException unwrapCollectException(Throwable t) {
    Throwable cur = t;
    int safety = 0;
    while (cur != null && safety++ < 20) {
      if (cur instanceof CollectException) {
        return (CollectException) cur;
      }
      cur = cur.getCause();
    }
    return null;
  }

  //    /**
  //     * 수집한 구매내역을 item 테이블에 반영한다.
  //     *
  //     * @param root
  //     * @param seqStore
  //     * @param seqUser
  //     * @param seqMall
  //     * @param seqBox
  //     * @return JsonNode : {item_count:00, messages:[...]}
  //     * @throws Exception
  //     */
  //    public JsonNode updateItems(JsonNode root, String seqStore, String seqUser, String seqMall,
  // String seqBox) throws Exception {
  //
  //        ObjectMapper objectMapper = new ObjectMapper();
  //        ObjectNode msgObjNode = null;
  //
  //        AutomationService autoSvc = new AutomationService();
  //        int seqBoxInt = Integer.parseInt(seqBox);
  //
  //        JbgOrderDataAccessObject joDao = new JbgOrderDataAccessObject();
  //        int seqOrder = -1;
  //        String serial, datetime, mallName = null;
  //        JsonNode items = null;
  //
  //        try {
  //            msgObjNode = objectMapper.createObjectNode();
  //            ArrayNode msgesArrNode = msgObjNode.putArray("messages");
  //            int itemCount = 0;
  //
  //            for (JsonNode order : root) {
  //                serial = order.has("serial") ? order.get("serial").asText().trim() : null;
  //                datetime = order.has("datetime") ? order.get("datetime").asText().trim() : null;
  //                mallName = order.has("mallname") ? order.get("mallname").asText().trim() : null;
  //
  //                JSONObject jsonOrder = joDao.getOrder(serial, datetime, null); //같은 날짜에 동일한
  // 구매정보가 있는지 확인
  //                if (jsonOrder != null) {
  //                    // 구매번호와 구매일자가 동일한 경우 스킵
  //
  //                } else if (datetime != null) {
  //                    // 구매일은 필수!!
  //
  //                    seqOrder = joDao.add(serial, datetime, mallName, seqMall, seqUser);
  //                    items = order.get("items");
  //
  //                    //FCM용 body message 저장
  //                    msgesArrNode.add("- '" + mallName + "'에서 '" +
  // JinieboxUtil.addDatedot(datetime) + "' 일자로 구매한 상품이 " + items.size() + "가지");
  //                    itemCount += items.size();
  //
  //                    for (JsonNode item : items) {
  //
  //                        ItemDataAccessObject itemDao = new ItemDataAccessObject();
  //
  //                        /**
  //                         * box 내에서 아이템 이름으로 검색하여 item 수량을 증감시킨다 (당일 추가된 만료일이 동일한 아이템이 없는 경우 새롭게
  // insert 함)
  //                         */
  //                        if (item.has("name") && item.get("name") != null) {
  //                            String itemName = item.get("name").asText();
  //
  //                            int seqBoxAuto = autoSvc.checkRules(itemName, seqStore);
  //                            int seqToBox = seqBoxAuto > -1 ? seqBoxAuto : seqBoxInt; //분류 자동화에
  // 적용되는 경우 지정된 보관함으로 이동
  //
  //                            int qty = Integer.parseInt(item.get("qty").asText());
  //                            JSONObject itemJson = itemDao.getSomedayItem(seqToBox, itemName,
  // datetime, null);
  //                            if (itemJson != null) { // 기존 아이템에 반영
  //                                String addedQty =
  // (Integer.parseInt(itemJson.get("qty").toString()) + qty) + "";
  //                                itemDao.updateItem(itemJson.get("seq").toString(), null,
  // addedQty, null, null, null, seqOrder);
  //                            } else { // 새로운 아이템으로 등록
  //                                itemDao.insertItem(Integer.parseInt(seqUser), seqToBox,
  // itemName, qty, 0, Integer.parseInt(datetime), seqOrder);
  //                            }
  //
  //                        }
  //                    }
  //                }
  //            }
  //
  //            msgObjNode.put("item_count", itemCount);
  //
  //        } catch (Exception e) {
  //            log.debug(ExceptionUtil.getExceptionInfo(e));
  //            throw e;
  //        }
  //
  //        return msgObjNode;
  //    }
}

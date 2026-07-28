package com.jiniebox.jangbogo.svc;

import com.jiniebox.jangbogo.dao.JbgMallDataAccessObject;
import com.jiniebox.jangbogo.svc.mall.Emart;
import com.jiniebox.jangbogo.svc.mall.Hanaro;
import com.jiniebox.jangbogo.svc.mall.Oasis;
import com.jiniebox.jangbogo.svc.mall.Ssg;
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

  /** 이번 수집에서 발생한 부분 실패 목록. 성공한 수집기가 하나라도 있으면 예외 대신 여기에 쌓인다. */
  private final List<CollectFailure> partialFailures = new ArrayList<>();

  /**
   * 이번 수집에서 발생한 부분 실패 목록을 반환한다. 호출측(runner)이 각각을 jbg_collect_log 에 FAIL 로 기록해야 한다.
   *
   * @return 부분 실패 목록 (없으면 빈 리스트)
   */
  public List<CollectFailure> getPartialFailures() {
    return new ArrayList<>(partialFailures);
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

    JSONArray itemArr = new JSONArray();
    int seqMallInt = Integer.parseInt(seqMall);
    int attempted = 0;
    if (seqMallInt == 1) {
      // seq=1 은 수집기가 둘이다. 한쪽이 실패해도 다른 쪽은 반드시 시도한다.
      // (SSG 온라인몰과 Emart 오프라인 영수증은 서로 독립적인 데이터원이라, 하나의 실패가 다른 하나를 막을 이유가 없다)
      attempted = 2;
      itemArr.addAll(collectFrom("SSG", () -> new Ssg(mallId, mallPw).getItems()));
      itemArr.addAll(collectFrom("Emart", () -> new Emart(mallId, mallPw).getItems()));
    } else if (seqMallInt == 2) {
      attempted = 1;
      itemArr.addAll(collectFrom("Oasis", () -> new Oasis(mallId, mallPw).getItems()));
    } else if (seqMallInt == 3) {
      attempted = 1;
      itemArr.addAll(collectFrom("Hanaro", () -> new Hanaro(mallId, mallPw).getItems()));
    }

    // 시도한 수집기가 전부 실패했다면 이번 수집은 실패다. 컨텍스트를 담은 채로 상위에 전파한다.
    if (attempted > 0 && partialFailures.size() == attempted) {
      throw partialFailures.get(0).cause();
    }

    logger.info("전체 수집 완료 - 총 {} 건 (부분 실패 {} 건)", itemArr.size(), partialFailures.size());
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
    logger.info("{} 구매 내역 수집 시작", name);
    try {
      JSONArray items = collector.get();
      logger.info("{} 수집 완료 - {} 건", name, items != null ? items.size() : 0);
      return items != null ? items : new JSONArray();
    } catch (Exception e) {
      CollectException ce = unwrapCollectException(e);
      if (ce == null) {
        ce = CollectStep.wrap(null, name, "collect", null, e);
      }
      partialFailures.add(new CollectFailure(name, ce));
      logger.error(
          "{} 수집 실패 (다른 수집기는 계속 진행) - 단계: {}, 원인: {}", name, ce.getStepName(), e.getMessage());
      return new JSONArray();
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

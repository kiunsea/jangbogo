package com.jiniebox.jangbogo.svc;

import com.jiniebox.jangbogo.dao.JbgMallDataAccessObject;
import com.jiniebox.jangbogo.svc.mall.Emart;
import com.jiniebox.jangbogo.svc.mall.Oasis;
import com.jiniebox.jangbogo.svc.mall.Ssg;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class MallOrderUpdater {

  /** 서버 환경 변경시 log4j.properties 설정 정보도 함께 변경 필요 */
  private Logger log = LogManager.getLogger(MallOrderUpdater.class);

  /**
   * 각 쇼핑몰에서의 주문 내역들을 수집한다
   *
   * @return
   * @throws Exception
   */
  public JSONArray collectItems(String seqMall, String mallId, String mallPw) throws Exception {
    log.info("구매내역 수집 시작 - seqMall: {}, mallId: {}", seqMall, mallId);

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
    if (seqMallInt == 1) {
      log.info("SSG 구매 내역 수집 시작");
      JSONArray ssgItems = new Ssg(mallId, mallPw).getItems();
      log.info("SSG 수집 완료 - {} 건", ssgItems != null ? ssgItems.size() : 0);
      if (ssgItems != null) itemArr.addAll(ssgItems);

      log.info("Emart 구매 내역 수집 시작");
      JSONArray emartItems = new Emart(mallId, mallPw).getItems();
      log.info("Emart 수집 완료 - {} 건", emartItems != null ? emartItems.size() : 0);
      if (emartItems != null) itemArr.addAll(emartItems);
    } else if (seqMallInt == 2) {
      log.info("Oasis 구매 내역 수집 시작");
      JSONArray oasisItems = new Oasis(mallId, mallPw).getItems();
      log.info("Oasis 수집 완료 - {} 건", oasisItems != null ? oasisItems.size() : 0);
      if (oasisItems != null) itemArr.addAll(oasisItems);
    }

    log.info("전체 수집 완료 - 총 {} 건", itemArr.size());
    return itemArr;
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

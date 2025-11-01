package com.jiniebox.jangbogo.svc;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiniebox.jangbogo.dao.JbgItemDataAccessObject;
import com.jiniebox.jangbogo.dao.JbgMallDataAccessObject;
import com.jiniebox.jangbogo.dao.JbgOrderDataAccessObject;
import com.jiniebox.jangbogo.util.ExceptionUtil;
import com.jiniebox.jangbogo.util.JSONUtil;

public class MallOrderUpdaterRunner  implements Runnable {

    /**
     * 서버 환경 변경시 log4j.properties 설정 정보도 함께 변경 필요
     */
    private Logger log = LogManager.getLogger(MallOrderUpdaterRunner.class);
    private String seqMall, mallId, mallPw;
    
    /**
     * @param seqMall 수집할 쇼핑몰
     * @param mallId
     * @param mallPw
     */
    public MallOrderUpdaterRunner(String seqMall, String mallId, String mallPw) {
        this.seqMall = seqMall;
        this.mallId = mallId;
        this.mallPw = mallPw;
    }
    
    @Override
    public void run() {

        // 1. 각 쇼핑몰에서의 주문 내역들을 수집한다.
        // 2. 지니박스 데이터베이스에 저장한다.
        try {
            MallOrderUpdater mou = new MallOrderUpdater();
            JSONArray itemArr = mou.collectItems(this.seqMall, this.mallId, this.mallPw);
            
            JbgMallDataAccessObject jmDao = new JbgMallDataAccessObject();
            String mallName = jmDao.getName(this.seqMall);
            
            log.info("===========================================================================");
            log.info("쇼핑몰: {} (seq={})", mallName, this.seqMall);
            log.info("수집된 주문 개수: {}", itemArr != null ? itemArr.size() : 0);
            log.info("===========================================================================");
            
            if (itemArr == null || itemArr.isEmpty()) {
                log.warn("수집된 주문 데이터가 없습니다. 쇼핑몰 seq={}", this.seqMall);
                return;
            }
            
            log.debug(JSONUtil.JsonEnterConvert(itemArr.toJSONString()));
            log.debug("------------------------------------------------------------------------------------------------------------------------------------------------------");
            
            
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(itemArr.toJSONString());
            
            // 추출한 아이템 목록을 jbg_order와 jbg_item 테이블에 저장
            JbgOrderDataAccessObject joDao = new JbgOrderDataAccessObject();
            JbgItemDataAccessObject jiDao = new JbgItemDataAccessObject();
            int itemCount = 0;
            int orderCount = 0;
            int existingOrderCount = 0; // 이미 등록된 주문 개수
            int skippedOrders = 0;
            
            try {
                if (root != null && root.isArray()) {
                    log.info("JSON 파싱 완료, 처리할 주문 개수: {}", root.size());
                    
                    for (JsonNode order : root) {
                        try {
                            // 주문 정보 추출
                            String serial = order.has("serial") ? order.get("serial").asText().trim() : "";
                            String datetime = order.has("datetime") ? order.get("datetime").asText().trim() : "";
                            String orderMallName = order.has("mallname") ? order.get("mallname").asText().trim() : null;
                            
                            log.debug("주문 처리 중 - serial: {}, datetime: {}, mallname: {}", serial, datetime, orderMallName);
                            
                            // datetime이 없으면 스킵
                            if (datetime == null || datetime.isEmpty()) {
                                log.warn("주문 datetime이 없어 스킵합니다. serial: {}", serial);
                                skippedOrders++;
                                continue;
                            }
                            
                            // 동일한 serial_num과 date_time이 있는지 확인 (중복 방지)
                            JSONObject existingOrder = joDao.getOrder(serial, datetime, null);
                            int seqOrder = -1;
                            
                            if (existingOrder != null) {
                                // 이미 존재하는 주문이면 아이템 저장 건너뜀 (중복 방지)
                                seqOrder = Integer.parseInt(existingOrder.get("seq").toString());
                                existingOrderCount++;
                                log.debug("기존 주문 발견, 아이템 저장 건너뜀 - seq_order: {}, serial: {}, datetime: {}", 
                                         seqOrder, serial, datetime);
                                continue; // 다음 주문으로
                            }
                            
                            // 새로운 주문 등록
                            // datetime 형식 변환 (YYYYMMDD -> INTEGER)
                            int dateTimeInt = 0;
                            try {
                                dateTimeInt = Integer.parseInt(datetime);
                            } catch (NumberFormatException e) {
                                log.warn("datetime 형식 오류: {}, serial: {}", datetime, serial);
                                skippedOrders++;
                                continue;
                            }
                            
                            seqOrder = joDao.add(serial, String.valueOf(dateTimeInt), orderMallName, this.seqMall);
                            orderCount++;
                            log.info("새 주문 등록 완료, seq_order: {}, serial: {}, datetime: {}, mallname: {}", 
                                     seqOrder, serial, datetime, orderMallName);
                            
                            // 새 주문일 때만 아이템 목록 처리
                            JsonNode items = order.get("items");
                            if (items != null && items.isArray()) {
                                for (JsonNode item : items) {
                                    try {
                                        if (item.has("name") && item.get("name") != null) {
                                            String itemName = item.get("name").asText().trim();
                                            if (itemName.isEmpty()) {
                                                continue;
                                            }
                                            
                                            // 아이템 등록 (seq_order 연결)
                                            jiDao.add(itemName, String.valueOf(seqOrder));
                                            itemCount++;
                                            log.debug("아이템 저장 완료: {}, seq_order: {}", itemName, seqOrder);
                                        }
                                    } catch (Exception itemEx) {
                                        log.warn("아이템 저장 중 오류 발생: {}", ExceptionUtil.getExceptionInfo(itemEx));
                                    }
                                }
                            }
                        } catch (Exception orderEx) {
                            log.warn("주문 저장 중 오류 발생: {}", ExceptionUtil.getExceptionInfo(orderEx));
                        }
                    }
                }
                
                log.info("===========================================================================");
                log.info("쇼핑몰 수집 완료 - mall: {} (seq={})", mallName, this.seqMall);
                log.info("신규 주문: {}개, 신규 아이템: {}개", orderCount, itemCount);
                log.info("기존 주문(중복): {}개, 스킵된 주문: {}개", existingOrderCount, skippedOrders);
                log.info("===========================================================================");
            } catch (Exception e) {
                log.error("아이템 저장 처리 중 오류 발생: {}", ExceptionUtil.getExceptionInfo(e));
            }
            
        } catch (Exception e) {
            log.error("쇼핑몰 수집 실행 중 오류 발생: {}", ExceptionUtil.getExceptionInfo(e));
        }
        
    }
}

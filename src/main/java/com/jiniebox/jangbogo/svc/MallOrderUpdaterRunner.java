package com.jiniebox.jangbogo.svc;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiniebox.jangbogo.dao.JbgMallDataAccessObject;
import com.jiniebox.jangbogo.util.ExceptionUtil;
import com.jiniebox.jangbogo.util.JSONUtil;

public class MallOrderUpdaterRunner  implements Runnable {

    /**
     * 서버 환경 변경시 log4j.properties 설정 정보도 함께 변경 필요
     */
    private Logger log = LogManager.getLogger(MallOrderUpdaterRunner.class);
    private String seqStore, seqPendBox, seqMall, mallId, mallPw;
    
    /**
     * @param seqUser
     * @param seqPendBox 등록대기 박스
     * @param seqMall 수집할 쇼핑몰
     * @param mallId
     * @param mallPw
     */
    public MallOrderUpdaterRunner(String seqStore, String seqPendBox, String seqMall, String mallId, String mallPw) {
        this.seqStore = seqStore;
        this.seqPendBox = seqPendBox; //등록대기 보관함
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
            
            log.debug("===========================================================================");
            log.debug("**** " + mallName);
            log.debug("===========================================================================");
            log.debug(JSONUtil.JsonEnterConvert(itemArr.toJSONString()));
            log.debug("------------------------------------------------------------------------------------------------------------------------------------------------------");
            
            
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(itemArr.toJSONString());
            
            //TODO 추출한 아이템 목록을 item table 에 저장해야만 한다.
            
            
            
            
//            JsonNode fcmMsges = mou.updateItems(root, seqStore, seqMall, seqPendBox);
//            JsonNode itemCnt = fcmMsges.get("item_count");
//            
//            if (itemCnt != null && Integer.parseInt(itemCnt.toString()) > 0) {
//                String titleStr = "'" + mallName + "' 에서 수집한 아이템이 총 '" + itemCnt + "'개 있습니다.";
//                StringBuffer bodySb = new StringBuffer();
//                JsonNode msgArr = fcmMsges.get("messages");
//                if (msgArr.isArray()) {
//                    for (JsonNode msg : msgArr) {
//                        bodySb.append(msg.asText() + System.lineSeparator());
//                    }
//                }
//
//                FcmTokenDataAccessObject ftDao = new FcmTokenDataAccessObject();
//                List<String> usrTokens = ftDao.getTokens(seqUser);
//
//                if (usrTokens != null && usrTokens.size() > 0) {
//                    FcmNotificationUtil fcmNoti = FcmNotificationUtil.getInstance();
//                    fcmNoti.sendNotifications(usrTokens, titleStr, bodySb.toString());
//                }
//            }
            
        } catch (Exception e) {
            log.debug(ExceptionUtil.getExceptionInfo(e));
        }
        
    }
}

package com.jiniebox.jangbogo.svc;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.jiniebox.jangbogo.dao.JbgMallDataAccessObject;
import com.jiniebox.jangbogo.dto.JangbogoConfig;
import com.jiniebox.jangbogo.svc.ifc.MallSession;
import com.jiniebox.jangbogo.svc.mall.Emart;
import com.jiniebox.jangbogo.svc.mall.Oasis;
import com.jiniebox.jangbogo.svc.util.WebDriverManager;
import com.jiniebox.jangbogo.sys.UserSession;
import com.jiniebox.jangbogo.util.JinieboxUtil;
import com.jiniebox.jangbogo.util.NumberUtil;

/**
 * 장보고 관리자
 * 쇼핑몰 연결 및 주문 수집 기능 제공
 */
@Service
public class JangBoGoManager {

    private static final Logger logger = LogManager.getLogger(JangBoGoManager.class);
    
    @Autowired
    private JangbogoConfig jangbogoConfig;
    
    // 현재 실행 중인 쇼핑몰 seq 추적
    private final Set<String> runningCollections = ConcurrentHashMap.newKeySet();

    /**
     * 쇼핑몰 목록에 사용자 아이디 원본값을 저장하여 반환한다.
     * 
     * @param malls
     * @param us
     */
    public static void addMallUsrid(List<JSONObject> malls, UserSession us) {
        JSONObject mallInfo = null;
        JSONObject mallMap = JinieboxUtil.listToMap(malls);
        String mallSeq, mallUsrid = null;
        Iterator<String> mallSeqs = mallMap.keySet().iterator();
        while (mallSeqs.hasNext()) {
            mallSeq = mallSeqs.next().toString();
            mallUsrid = us.getMallUsrid(mallSeq);
            if (mallUsrid != null) {
                mallInfo = (JSONObject) mallMap.get(mallSeq);
                mallInfo.put("usrid", mallUsrid);
            }
        }
    }

    /**
     * 쇼핑몰 연결 테스트후 장보고에 등록
     * 
     * @param seqMall
     * @param seqUser
     * @param usrid
     * @param usrpw
     * @return 1 : 성공, 0 : 실패, 2 : 시간경과필요 
     * @throws Exception
     */
    public int connectToMall(String seqMall, String usrid, String usrpw) throws Exception {

        if (Integer.parseInt(seqMall) != 1) {
            if (!this.elapsedSigninTime(seqMall)) { //유효한 시간 체크
                return 2;
            }
        }

        int rtnVal = 0;
        WebDriverManager wdm = new WebDriverManager();
        WebDriver driver = wdm.getWebDriver();

        MallSession mallObj = this.getMallSession(seqMall, usrid, usrpw);
        if (mallObj != null) {
            boolean validUser = false;
            try {
                validUser = mallObj.signin(driver); // 계정 정보가 유효한지 테스트
            } catch (Exception e) {
                logger.debug(e.getMessage());
            }

            if (validUser) {
                JbgMallDataAccessObject jaDao = new JbgMallDataAccessObject();

                int chkRst = -1;
                if (seqMall != null) {
                    chkRst = jaDao.checkAccountStatus(seqMall);
                }

                if (chkRst < 0) {
                    jaDao.add(seqMall, 1, null, null);
                } else {
                    jaDao.setAccountStatus(seqMall, 1);
                }

                mallObj.signout(driver);
                rtnVal = 1;
            }
        }
        
        // 드라이버 종료
        driver.quit();
        driver = null;

        return rtnVal;
    }

    /**
     * 온라인/오프라인 쇼핑몰에서 구매한 아이템 내역들을 수집하고 지니박스 데이터베이스에 반영한다.
     *
     * @param seqMall 수집할 쇼핑몰
     * @param mallId {seqMall:{usrid:OOO,usrpw:OOO}}
     * @param mallPw
     * @throws Exception
     */
    public void updateItems(String seqMall, String mallId, String mallPw) throws Exception {
        // 이미 실행 중이면 무시
        if (runningCollections.contains(seqMall)) {
            logger.warn("쇼핑몰 seq={} 이미 수집 작업 실행 중, 건너뜀", seqMall);
            return;
        }
        
        // 실행 중으로 표시
        runningCollections.add(seqMall);
        logger.info("쇼핑몰 seq={} 수집 작업 시작", seqMall);
        
        // Thread로 실행하되 종료 시 Set에서 제거
        new Thread(() -> {
            try {
                new MallOrderUpdaterRunner(seqMall, mallId, mallPw).run();
            } finally {
                runningCollections.remove(seqMall);
                logger.info("쇼핑몰 seq={} 수집 작업 완료", seqMall);
            }
        }).start();
    }
    
    /**
     * 구매 아이템 목록 수집 (동기 실행 + 신규 주문 seq 반환)
     * 
     * @param seqMall 수집할 쇼핑몰
     * @param mallId 쇼핑몰 사용자 아이디
     * @param mallPw 쇼핑몰 사용자 비밀번호
     * @return 신규 추가된 주문 seq 목록
     * @throws Exception
     */
    public List<Integer> updateItemsAndGetNewSeqs(String seqMall, String mallId, String mallPw) throws Exception {
        // 이미 실행 중이면 빈 리스트 반환
        if (runningCollections.contains(seqMall)) {
            logger.warn("쇼핑몰 seq={} 이미 수집 작업 실행 중, 건너뜀", seqMall);
            return new java.util.ArrayList<>();
        }
        
        // 실행 중으로 표시
        runningCollections.add(seqMall);
        logger.info("쇼핑몰 seq={} 수집 작업 시작 (동기 실행)", seqMall);
        
        try {
            // 동기 실행하여 결과 받기
            MallOrderUpdaterRunner runner = new MallOrderUpdaterRunner(seqMall, mallId, mallPw);
            runner.run();
            
            // 신규 추가된 주문 seq 목록 반환
            List<Integer> newOrderSeqs = runner.getNewOrderSeqs();
            logger.info("쇼핑몰 seq={} 수집 작업 완료, 신규 주문: {}개", seqMall, newOrderSeqs.size());
            
            return newOrderSeqs;
        } finally {
            runningCollections.remove(seqMall);
        }
    }
    
    /**
     * 특정 쇼핑몰의 수집 작업이 현재 실행 중인지 확인
     * 
     * @param seqMall 쇼핑몰 시퀀스
     * @return 실행 중이면 true
     */
    public boolean isCollecting(String seqMall) {
        return runningCollections.contains(seqMall);
    }
    
    /**
     * mall sequence 에 따라 mall instance 를 생성하여 반환
     * @param seqMall
     * @param usrid
     * @param usrpw
     * @return
     */
    private MallSession getMallSession(String seqMall, String usrid, String usrpw) {
        int seqMallInt = NumberUtil.isNumber(seqMall) ? Integer.parseInt(seqMall) : -1;
        if (seqMallInt > 0) {
            MallSession mallMgn = null;
            if (seqMallInt == 1) {
                mallMgn = new Emart(usrid, usrpw);
            } else if (seqMallInt == 2) {
                mallMgn = new Oasis(usrid, usrpw);
            }
            return mallMgn;
        }
        return null;
    }

    /**
     * 지정한 시간이 경과되었는지 여부를 확인 (짧은시간동안 로그인이 여러번 수행되면 emart 와 같은 특정 사이트에서 중복로그인으로 간주함)
     * 
     * @param seqMall
     * @return
     * @throws Exception
     */
    private boolean elapsedSigninTime(String seqMall) throws Exception {

        JbgMallDataAccessObject jaDao = new JbgMallDataAccessObject();
        JSONObject accessInfo = jaDao.getAccessInfo(seqMall);

        Object lastSigninTime = accessInfo != null ? accessInfo.get("time") : 0;
        if (lastSigninTime != null) {
            long curr = System.currentTimeMillis();
            long lastSignin = Long.parseLong(lastSigninTime.toString());
            long delay = Long.parseLong(jangbogoConfig.get("MALL_SIGNIN_DELAY"));
            if ((curr - lastSignin) > delay) {
                logger.debug("쇼핑몰 seq={} 사용자 구매내역 조회 시작", seqMall);
                return true;
            } else {
                logger.debug("설정된 대기시간={}ms, 경과시간={}ms", delay, (curr - lastSignin));
            }
        }

        return false;
    }
}

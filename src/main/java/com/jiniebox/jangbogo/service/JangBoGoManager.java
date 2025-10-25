package com.jiniebox.jangbogo.service;

import java.util.Iterator;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.PropertiesUtil;
import org.json.simple.JSONObject;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;

import com.jiniebox.jangbogo.config.JangbogoConfig;
import com.jiniebox.jangbogo.dao.JbgMallDataAccessObject;
import com.jiniebox.jangbogo.service.ifc.MallSession;
import com.jiniebox.jangbogo.service.mall.Emart;
import com.jiniebox.jangbogo.service.mall.Oasis;
import com.jiniebox.jangbogo.service.util.WebDriverManager;
import com.jiniebox.jangbogo.sys.UserSession;
import com.jiniebox.jangbogo.utils.JinieboxUtil;
import com.jiniebox.jangbogo.utils.NumberUtil;

public class JangBoGoManager {

    private static final Logger logger = LogManager.getLogger(JangBoGoManager.class);
    
    @Autowired
    private JangbogoConfig jangbogoConfig;

    /**
     * browser local storage 에서 조회하기 위한 ciphered id key
     * 
     * @param seqMall
     * @return
     */
    public static String getCipidkey(String seqMall) {
        return "jangbogo^" + seqMall + "%i";
    }
    
    /**
     * browser local storage 에서 조회하기 위한 ciphered pw key
     * 
     * @param seqMall
     * @param seqUser
     * @return
     */
    public static String getCippwkey(String seqMall) {
        return "jangbogo^" + seqMall + "%p";
    }
    
    /**
     * 쇼핑몰 계정 정보를 조회하기 위한 id와 pw 의 keys를 malls 에 일괄 추가
     * browser local storage 에서 조회하기 위한 id key 와 pw key
     * 
     * @param malls
     * @param seqUser
     */
    public static void addCippKeys(List<JSONObject> malls) {
        // browser local storage 에서 조회하기 위한 id key 와 pw key 를 설정
        JSONObject mallJson = null;
        Iterator<JSONObject> mallIter = malls.iterator();
        while (mallIter.hasNext()) {
            mallJson = mallIter.next();
            mallJson.put("cipidkey", getCipidkey(mallJson.get("seq").toString()));
            mallJson.put("cippwkey", getCippwkey(mallJson.get("seq").toString()));
        }
    }
    
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
     * @param seqPendBox 등록대기 박스
     * @param seqMall 수집할 쇼핑몰
     * @param mallId {seqMall:{usrid:OOO,usrpw:OOO}}
     * @param mallPw
     * @throws Exception
     */
    public void updateItems(String seqStore, String seqPendBox, String seqMall, String mallId, String mallPw) throws Exception {
        if (this.elapsedSigninTime(seqMall)) {
            new Thread(new MallOrderUpdaterRunner(seqStore, seqPendBox, seqMall, mallId, mallPw)).start();
//            try {
//                // 스레드들이 동시 실행되지 않도록 2초 동안 대기
//                Thread.sleep(2000); // 2000 밀리초 = 2초
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//                log.error(ExceptionUtil.getExceptionInfo(e));
//            }
        } else {
            logger.debug("사용자의 [" + seqMall + "] 사이트 접속 가능 시간이 아니기 때문에 작업이 취소되었습니다.");
        }
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
                logger.debug("["+seqMall+"]Mall 의 사용자 구매내역을 조회 시작~");
                return true;
            } else {
                logger.debug("settingtime-" + delay + ", elapsetime-" + (curr - lastSignin));
            }
        }

        return false;
    }
}

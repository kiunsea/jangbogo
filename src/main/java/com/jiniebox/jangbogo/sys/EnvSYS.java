package com.jiniebox.jangbogo.sys;

/**
 * 단일 객체 저장용 싱글톤 클래스
 * @author KIUNSEA
 *
 */
public class EnvSYS {
    
    /**
     * 000 : 수행 성공
     */
    public static final String RESCODE_SUCC = "000";
    /**
     * 001 : 수행 실패
     */
    public static final String RESCODE_FAIL = "001";
    public static final String RESMSG_FAIL = "* 시스템 에러입니다";
    public static final String RESMSG_INVREQ= "* 잘못된 요청입니다 (명령어가 없습니다)";
    public static final String RESMSG_USERSESSION_FAIL = "사용자 로그인 정보가 없습니다\n계속하시려면 로그인 해주세요";
    
    private static EnvSYS instance;
    private EnvSYS() {
        ;
    }
    public static synchronized EnvSYS getInstance() {
        if (instance == null) {
            instance = new EnvSYS();
        }
        return instance;
    }
    
}
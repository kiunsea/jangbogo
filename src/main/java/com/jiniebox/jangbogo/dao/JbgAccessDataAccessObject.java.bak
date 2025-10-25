package com.jiniebox.jangbogo.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;

import com.jiniebox.jangbogo.utils.ExceptionUtil;
import com.jiniebox.jangbogo.utils.JinieboxUtil;

/**
 * 서비스 이용자 접속 정보
 * 
 * @author KIUNSEA
 *
 */
public class JbgAccessDataAccessObject extends CommonDataAccessObject {

    /**
     * 서버 환경 변경시 log4j.properties 설정 정보도 함께 변경 필요
     */
    private static final Logger log = LogManager.getLogger(JbgAccessDataAccessObject.class);

    public JbgAccessDataAccessObject() {;}
    
    /**
     * 쇼핑몰 전체 목록과 함께 접속 상태를 반환 (status > -1, 1:접속가능, 0:접속불가)
     * 
     * @return
     * @throws Exception
     */
    public List<JSONObject> getAccessInfos() throws Exception {
        LocalDBConnection conn = null;
        try {
            conn = new LocalDBConnection();
            StringBuffer querySb = new StringBuffer("SELECT m.seq seq, m.id id, a.account_status status");
            querySb.append(" FROM jbg_mall m LEFT JOIN jbg_access a");
            querySb.append(" ON a.seq_jbgmall = m.seq");
            querySb.append(" AND a.account_status > -1");
            log.debug("LOCALDB-QUERY------------------------------------------------------------------------------");
            log.debug(querySb);
            ResultSet rset = conn.executeQuery(querySb.toString());

            List<JSONObject> malls = null;
            if (rset != null) {
                malls = new ArrayList<JSONObject>();
                JSONObject mJson = null;
                while (rset.next()) {
                    mJson = new JSONObject();
                    mJson.put("seq", rset.getInt("seq"));
                    mJson.put("id", rset.getString("id"));
                    mJson.put("status", rset.getInt("status"));
                    malls.add(mJson);
                }
                return malls;
            } else {
                return null;
            }
        } catch (Exception e) {
            log.error("* 프로그램 수행중 에러 발생");
            log.error(ExceptionUtil.getExceptionInfo(e));
            throw e;
        } finally {
            conn.close();
        }
    }
    
    /**
     *  서비스 이용 가능 여부를 조회
     * 
     * @param seqJbgmall
     * @return 서비스 상태 (0:사용안함, 1:사용함, 2:계정오류, 3:프로그램오류), -1:미등록
     * @throws Exception
     */
    public int checkAccountStatus(String seqJbgmall) throws Exception {
        LocalDBConnection conn = null;
        try {
            conn = new LocalDBConnection();
            StringBuffer querySb = new StringBuffer("SELECT account_status from jbg_access");
            querySb.append(" WHERE seq_jbgmall=" + seqJbgmall);

            log.debug("LOCALDB-QUERY------------------------------------------------------------------------------");
            log.debug(querySb);
            ResultSet rset = conn.executeQuery(querySb.toString());

            int status = -1;
            if (rset != null) {
                if (rset.next()) {
                    status = rset.getInt("account_status");
                    return status;
                }
            }
            return status;

        } catch (Exception e) {
            log.error("* 프로그램 수행중 에러 발생");
            log.error(ExceptionUtil.getExceptionInfo(e));
            throw e;
        } finally {
            conn.close();
        }
    }
    
    /**
     * 쇼핑몰 사용자로 등록
     * 
     * @param seqJbgmall 필수
     * @param accountStatus 1:이용 가능, 0:이용 불가, -1:미등록
     * @param encryptKey null 허용
     * @param encryptIv null 허용
     * @return
     * @throws Exception
     */
    public boolean add(String seqJbgmall, int accountStatus, String encryptKey, String encryptIv) throws Exception {

        StringBuffer querySb = new StringBuffer();
        querySb.append("INSERT INTO jbg_access (");
        querySb.append("seq_jbgmall");
        querySb.append(",account_status");
        if (!JinieboxUtil.isEmpty(encryptKey))
            querySb.append(",encrypt_key");
        if (!JinieboxUtil.isEmpty(encryptIv))
            querySb.append(",encrypt_iv");
        querySb.append(",last_signin_time");
        querySb.append(") values (");
        querySb.append(seqJbgmall);
        querySb.append(", " + accountStatus);
        if (!JinieboxUtil.isEmpty(encryptKey))
            querySb.append(", '" + encryptKey + "'");
        if (!JinieboxUtil.isEmpty(encryptIv))
            querySb.append(", '" + encryptIv + "'");
        querySb.append(", " + System.currentTimeMillis());
        querySb.append(")");
        log.debug("LOCALDB-QUERY------------------------------------------------------------------------------");
        log.debug(querySb);

        LocalDBConnection conn = null;
        try {
            conn = new LocalDBConnection();
            conn.txOpen();
            conn.txExecuteUpdate(querySb.toString());
            conn.txCommit();
        } catch (SQLException e) {
            log.error("* 아이고!! ㅜ.ㅜ 데이터베이스 업데이트 에러 발생");
            log.error(ExceptionUtil.getExceptionInfo(e));
            throw e;
        } catch (Exception e) {
            log.error("* 데이터베이스 업데이트 에러 발생");
            log.error(ExceptionUtil.getExceptionInfo(e));
            conn.txRollBack();
            throw e;
        } finally {
            conn.close();            
        }

        return true;
    }

    /**
     * '마지막 접속 시간'을 제외하여 나머지 필드에 대해 업데이트를 수행
     * 
     * @param seqJbgmall 필수
     * @param accountStatus  1:이용 가능, 0:이용 불가, -1:미등록
     * @param encryptKey null 허용
     * @param encryptIv null 허용
     * @throws Exception
     */
    public void update(String seqJbgmall, int accountStatus, String encryptKey, String encryptIv) throws Exception {
        LocalDBConnection conn = null;
        try {
            conn = new LocalDBConnection();
            conn.txOpen();

            StringBuffer querySb = new StringBuffer();
            querySb.append("UPDATE jbg_access set");
            querySb.append(" account_status=" + accountStatus);
            if (!JinieboxUtil.isEmpty(encryptKey))
                querySb.append(", encrypt_key='" + encryptKey + "'");
            if (!JinieboxUtil.isEmpty(encryptIv))
                querySb.append(", encrypt_iv='" + encryptIv + "'");
            querySb.append(" WHERE seq_jbgmall=" + seqJbgmall);

            String query = querySb.toString();
            log.debug("LOCAL-QUERY------------------------------------------------------------------------------");
            log.debug(query);
            conn.txExecuteUpdate(query);
            conn.txCommit();
        } catch (SQLException e) {
            log.error("* 아이고!! ㅜ.ㅜ 데이터베이스 업데이트 에러 발생");
            log.error(ExceptionUtil.getExceptionInfo(e));
            throw e;
        } catch (Exception e) {
            log.error("* 아이고!! ㅜ.ㅜ 데이터베이스 업데이트 에러 발생");
            log.error(ExceptionUtil.getExceptionInfo(e));
            conn.txRollBack();
            throw e;
        } finally {
            conn.close();
        }
    }    
    
    /**
     * 마지막 로그인 시간을 현재시간으로 설정한다.
     * 
     * @param seqJbgmall
     * @param seqUser
     * @throws Exception
     */
    public void updateLastSigninTime(String seqJbgmall) throws Exception {
        LocalDBConnection conn = null;
        try {
            conn = new LocalDBConnection();
            conn.txOpen();

            StringBuffer querySb = new StringBuffer();
            querySb.append("UPDATE jbg_access set");
            querySb.append(" last_signin_time=" + System.currentTimeMillis());
            querySb.append(" WHERE seq_jbgmall=" + seqJbgmall);

            String query = querySb.toString();
            log.debug("LOCAL-QUERY------------------------------------------------------------------------------");
            log.debug(query);
            conn.txExecuteUpdate(query);
            conn.txCommit();
        } catch (SQLException e) {
            log.error("* 아이고!! ㅜ.ㅜ 데이터베이스 업데이트 에러 발생");
            log.error(ExceptionUtil.getExceptionInfo(e));
            throw e;
        } catch (Exception e) {
            log.error("* 아이고!! ㅜ.ㅜ 데이터베이스 업데이트 에러 발생");
            log.error(ExceptionUtil.getExceptionInfo(e));
            conn.txRollBack();
            throw e;
        } finally {
            conn.close();
        }
    }
    
    /**
     * @param seqJbgmall
     * @param seqUser
     * @param accountStatus 서비스 상태 (0:사용안함, 1:사용함, 2:계정오류, 3:프로그램오류)
     * @throws Exception
     */
    public void setAccountStatus(String seqJbgmall, int accountStatus) throws Exception {
        LocalDBConnection conn = null;
        try {
            conn = new LocalDBConnection();
            conn.txOpen();

            StringBuffer querySb = new StringBuffer();
            querySb.append("UPDATE jbg_access set");
            querySb.append(" account_status=" + accountStatus);
            if (accountStatus == 1) {
                querySb.append(", last_signin_time=" + System.currentTimeMillis());
            }
            querySb.append(" WHERE seq_jbgmall=" + seqJbgmall);

            String query = querySb.toString();
            log.debug("LOCAL-QUERY------------------------------------------------------------------------------");
            log.debug(query);
            conn.txExecuteUpdate(query);
            conn.txCommit();
        } catch (SQLException e) {
            log.error("* 아이고!! ㅜ.ㅜ 데이터베이스 업데이트 에러 발생");
            log.error(ExceptionUtil.getExceptionInfo(e));
            throw e;
        } catch (Exception e) {
            log.error("* 아이고!! ㅜ.ㅜ 데이터베이스 업데이트 에러 발생");
            log.error(ExceptionUtil.getExceptionInfo(e));
            conn.txRollBack();
            throw e;
        } finally {
            conn.close();
        }
    }
    
    /**
     * 쇼핑몰 접속 정보
     * 
     * @param seqMall
     * @return  {status, enckey, enciv, time}
     * @throws Exception
     */
    public JSONObject getAccessInfo(String seqMall) throws Exception {
        LocalDBConnection conn = null;
        try {
            conn = new LocalDBConnection();
            StringBuffer querySb = new StringBuffer("SELECT account_status status, encrypt_key, encrypt_iv, last_signin_time time");
            querySb.append(" FROM jbg_access");
            querySb.append(" WHERE seq_jbgmall = " + seqMall);
            log.debug("LOCALDB-QUERY------------------------------------------------------------------------------");
            log.debug(querySb);
            ResultSet rset = conn.executeQuery(querySb.toString());

            if (rset != null) {
                JSONObject mJson = null;
                if (rset.next()) {
                    mJson = new JSONObject();
                    mJson.put("status", rset.getInt("status"));
                    mJson.put("enckey", rset.getString("encrypt_key"));
                    mJson.put("enciv", rset.getString("encrypt_iv"));
                    mJson.put("time", rset.getLong("time"));
                }
                return mJson;
            } else {
                return null;
            }
        } catch (Exception e) {
            log.error("* 프로그램 수행중 에러 발생");
            log.error(ExceptionUtil.getExceptionInfo(e));
            throw e;
        } finally {
            conn.close();
        }
    }
   
}

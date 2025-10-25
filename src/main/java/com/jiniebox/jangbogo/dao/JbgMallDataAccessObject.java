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
 * 쇼핑몰 정보 DAO (통합)
 * 
 * jbg_mall 테이블에 접근하는 모든 기능을 통합
 * - 기존 JbgAccessDataAccessObject와 JbgMallDataAccessObject를 통합
 * 
 * @author KIUNSEA
 */
public class JbgMallDataAccessObject extends CommonDataAccessObject {

    private static final Logger log = LogManager.getLogger(JbgMallDataAccessObject.class);

    public JbgMallDataAccessObject() {
        // 기본 생성자
    }
    
    // ========== 쇼핑몰 조회 메서드 ==========
    
    /**
     * 쇼핑몰 전체 목록 조회 (기본 정보만)
     * 
     * @return 쇼핑몰 목록 (seq, id, name, details)
     * @throws Exception
     */
    public List<JSONObject> getMalls() throws Exception {
        LocalDBConnection conn = null;
        try {
            conn = new LocalDBConnection();
            StringBuffer querySb = new StringBuffer("SELECT seq, id, name, details");
            querySb.append(" FROM jbg_mall");
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
                    mJson.put("name", rset.getString("name"));
                    mJson.put("details", rset.getString("details"));
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
            if (conn != null) {
                conn.close();
            }
        }
    }
    
    /**
     * 쇼핑몰 전체 목록 조회 (모든 필드 포함)
     * 
     * @return 쇼핑몰 목록 (모든 필드)
     * @throws Exception
     */
    public List<JSONObject> getAllMalls() throws Exception {
        LocalDBConnection conn = null;
        try {
            conn = new LocalDBConnection();
            StringBuffer querySb = new StringBuffer("SELECT seq, id, name, details, ");
            querySb.append("encrypt_key, encrypt_iv, account_status, last_signin_time");
            querySb.append(" FROM jbg_mall");
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
                    mJson.put("name", rset.getString("name"));
                    mJson.put("details", rset.getString("details"));
                    mJson.put("encrypt_key", rset.getString("encrypt_key"));
                    mJson.put("encrypt_iv", rset.getString("encrypt_iv"));
                    mJson.put("account_status", rset.getInt("account_status"));
                    mJson.put("last_signin_time", rset.getLong("last_signin_time"));
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
            if (conn != null) {
                conn.close();
            }
        }
    }
    
    /**
     * 특정 쇼핑몰의 전체 정보 조회 (모든 필드)
     * 
     * @param seq 쇼핑몰 시퀀스
     * @return 쇼핑몰 전체 정보 (모든 필드)
     * @throws Exception
     */
    public JSONObject getMall(String seq) throws Exception {
        LocalDBConnection conn = null;
        try {
            conn = new LocalDBConnection();
            StringBuffer querySb = new StringBuffer("SELECT seq, id, name, details, ");
            querySb.append("encrypt_key, encrypt_iv, account_status, last_signin_time");
            querySb.append(" FROM jbg_mall");
            querySb.append(" WHERE seq=" + seq);
            log.debug("LOCALDB-QUERY------------------------------------------------------------------------------");
            log.debug(querySb);
            ResultSet rset = conn.executeQuery(querySb.toString());

            if (rset != null) {
                JSONObject mJson = null;
                if (rset.next()) {
                    mJson = new JSONObject();
                    mJson.put("seq", rset.getInt("seq"));
                    mJson.put("id", rset.getString("id"));
                    mJson.put("name", rset.getString("name"));
                    mJson.put("details", rset.getString("details"));
                    mJson.put("encrypt_key", rset.getString("encrypt_key"));
                    mJson.put("encrypt_iv", rset.getString("encrypt_iv"));
                    mJson.put("account_status", rset.getInt("account_status"));
                    mJson.put("last_signin_time", rset.getLong("last_signin_time"));
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
            if (conn != null) {
                conn.close();
            }
        }
    }
    
    /**
     * 쇼핑몰 전체 목록과 접속 상태 조회 (통합 - 구 JbgAccessDataAccessObject.getAccessInfos)
     * 
     * @return 쇼핑몰 목록 (seq, id, status, encrypt_key, encrypt_iv, last_signin_time 포함)
     * @throws Exception
     */
    public List<JSONObject> getAccessInfos() throws Exception {
        LocalDBConnection conn = null;
        try {
            conn = new LocalDBConnection();
            StringBuffer querySb = new StringBuffer("SELECT seq, id, name, details, ");
            querySb.append("account_status status, encrypt_key, encrypt_iv, last_signin_time");
            querySb.append(" FROM jbg_mall");
            querySb.append(" WHERE account_status > -1");
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
                    mJson.put("name", rset.getString("name"));
                    mJson.put("details", rset.getString("details"));
                    mJson.put("status", rset.getInt("status"));
                    mJson.put("encrypt_key", rset.getString("encrypt_key"));
                    mJson.put("encrypt_iv", rset.getString("encrypt_iv"));
                    mJson.put("last_signin_time", rset.getLong("last_signin_time"));
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
            if (conn != null) {
                conn.close();
            }
        }
    }
    
    /**
     * 쇼핑몰 접속 정보 조회 (통합 - 구 JbgAccessDataAccessObject.getAccessInfo)
     * 
     * @param seqMall 쇼핑몰 시퀀스
     * @return {status, enckey, enciv, time}
     * @throws Exception
     */
    public JSONObject getAccessInfo(String seqMall) throws Exception {
        LocalDBConnection conn = null;
        try {
            conn = new LocalDBConnection();
            StringBuffer querySb = new StringBuffer("SELECT account_status status, encrypt_key, encrypt_iv, last_signin_time time");
            querySb.append(" FROM jbg_mall");
            querySb.append(" WHERE seq = " + seqMall);
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
            if (conn != null) {
                conn.close();
            }
        }
    }
    
//    /**
//     * 저장소 공유 정보 추가 <br/>
//     * authority : 'R' - 읽기전용(기본값), 'M' - 등록/수정/삭제 가능
//     * @param seqUser
//     * @param typeObject
//     * @param seqObject
//     * @param authority
//     * @return
//     * @throws Exception
//     */
//    public boolean add(String seqUser, String typeObject, String seqObject, String authority) throws Exception {
//
//        StringBuffer querySb = new StringBuffer();
//        querySb.append("INSERT INTO share (");
//        querySb.append("seq_user,");
//        querySb.append("type_object,");
//        querySb.append("seq_object,");
//        querySb.append("authority");
//        querySb.append(") values (");
//        querySb.append(seqUser + ", ");
//        querySb.append("'" + typeObject + "', ");
//        querySb.append(seqObject + ", ");
//        querySb.append("'" + authority + "'");
//        querySb.append(")");
//        log.debug("LOCALDB-QUERY------------------------------------------------------------------------------");
//        log.debug(querySb);
//
//        LocalDBConnection conn = null;
//        try {
//            conn = new LocalDBConnection();
//            conn.txOpen();
//            conn.txExecuteUpdate(querySb.toString());
//            conn.txCommit();
//        } catch (SQLException sqle) {
//            log.error("* 아이고!! ㅜ.ㅜ 데이터베이스 업데이트 에러 발생");
//            log.error(sqle.getMessage());
//            log.debug(ExceptionUtil.getExceptionInfo(sqle.getStackTrace()));
//            sqllog.error(ExceptionUtil.getExceptionInfo(e));
//            throw sqle;
//        } catch (Exception e) {
//            log.error("* 데이터베이스 업데이트 에러 발생");
//            log.error(e.getMessage());
//            log.debug(ExceptionUtil.getExceptionInfo(e.getStackTrace()));
//            log.error(ExceptionUtil.getExceptionInfo(e));
//            conn.txRollBack();
//            throw e;
//        } finally {
//            conn.close();            
//        }
//
//        return true;
//    }

    /**
     * 시퀀스로 이름 조회
     * 
     * @param seq 쇼핑몰 시퀀스
     * @return 쇼핑몰 이름
     * @throws Exception
     */
    public String getName(String seq) throws Exception {
        LocalDBConnection conn = null;
        try {
            conn = new LocalDBConnection();
            StringBuffer querySb = new StringBuffer("SELECT name from jbg_mall");
            querySb.append(" WHERE seq=" + seq);

            log.debug("LOCALDB-QUERY------------------------------------------------------------------------------");
            log.debug(querySb);
            ResultSet rset = conn.executeQuery(querySb.toString());

            if (rset != null) {
                String name = null;
                if (rset.next()) {
                    name = rset.getString("name");
                    return name;
                }
            }
            return null;

        } catch (Exception e) {
            log.error("* 프로그램 수행중 에러 발생");
            log.error(ExceptionUtil.getExceptionInfo(e));
            throw e;
        } finally {
            if (conn != null) {
                conn.close();
            }
        }
    }
    
    // ========== 계정 상태 관련 메서드 (통합 - 구 JbgAccessDataAccessObject) ==========
    
    /**
     * 서비스 이용 가능 여부 조회 (통합 - 구 JbgAccessDataAccessObject.checkAccountStatus)
     * 
     * @param seqJbgmall 쇼핑몰 시퀀스
     * @return 서비스 상태 (0:사용안함, 1:사용함, 2:계정오류, 3:프로그램오류), -1:미등록
     * @throws Exception
     */
    public int checkAccountStatus(String seqJbgmall) throws Exception {
        LocalDBConnection conn = null;
        try {
            conn = new LocalDBConnection();
            StringBuffer querySb = new StringBuffer("SELECT account_status from jbg_mall");
            querySb.append(" WHERE seq=" + seqJbgmall);

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
            if (conn != null) {
                conn.close();
            }
        }
    }
    
    /**
     * 쇼핑몰 계정 정보 업데이트 (통합 - 구 JbgAccessDataAccessObject.update)
     * 
     * @param seqJbgmall 필수
     * @param accountStatus 1:이용 가능, 0:이용 불가, -1:미등록
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
            querySb.append("UPDATE jbg_mall SET");
            querySb.append(" account_status=" + accountStatus);
            if (!JinieboxUtil.isEmpty(encryptKey))
                querySb.append(", encrypt_key='" + encryptKey + "'");
            if (!JinieboxUtil.isEmpty(encryptIv))
                querySb.append(", encrypt_iv='" + encryptIv + "'");
            querySb.append(" WHERE seq=" + seqJbgmall);

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
            if (conn != null) {
                conn.txRollBack();
            }
            throw e;
        } finally {
            if (conn != null) {
                conn.close();
            }
        }
    }
    
    /**
     * 마지막 로그인 시간을 현재시간으로 설정 (통합 - 구 JbgAccessDataAccessObject.updateLastSigninTime)
     * 
     * @param seqJbgmall 쇼핑몰 시퀀스
     * @throws Exception
     */
    public void updateLastSigninTime(String seqJbgmall) throws Exception {
        LocalDBConnection conn = null;
        try {
            conn = new LocalDBConnection();
            conn.txOpen();

            StringBuffer querySb = new StringBuffer();
            querySb.append("UPDATE jbg_mall SET");
            querySb.append(" last_signin_time=" + System.currentTimeMillis());
            querySb.append(" WHERE seq=" + seqJbgmall);

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
            if (conn != null) {
                conn.txRollBack();
            }
            throw e;
        } finally {
            if (conn != null) {
                conn.close();
            }
        }
    }
    
    /**
     * 계정 상태 설정 (통합 - 구 JbgAccessDataAccessObject.setAccountStatus)
     * 
     * @param seqJbgmall 쇼핑몰 시퀀스
     * @param accountStatus 서비스 상태 (0:사용안함, 1:사용함, 2:계정오류, 3:프로그램오류)
     * @throws Exception
     */
    public void setAccountStatus(String seqJbgmall, int accountStatus) throws Exception {
        LocalDBConnection conn = null;
        try {
            conn = new LocalDBConnection();
            conn.txOpen();

            StringBuffer querySb = new StringBuffer();
            querySb.append("UPDATE jbg_mall SET");
            querySb.append(" account_status=" + accountStatus);
            if (accountStatus == 1) {
                querySb.append(", last_signin_time=" + System.currentTimeMillis());
            }
            querySb.append(" WHERE seq=" + seqJbgmall);

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
            if (conn != null) {
                conn.txRollBack();
            }
            throw e;
        } finally {
            if (conn != null) {
                conn.close();
            }
        }
    }
    
    /**
     * 쇼핑몰 계정 정보 등록 (통합 - 구 JbgAccessDataAccessObject.add)
     * 
     * 주의: 테이블 통합으로 인해 이 메서드는 UPDATE 방식으로 동작
     * 기존 jbg_mall 레코드의 encrypt_key, encrypt_iv, account_status를 업데이트
     * 
     * @param seqJbgmall 필수
     * @param accountStatus 1:이용 가능, 0:이용 불가, -1:미등록
     * @param encryptKey null 허용
     * @param encryptIv null 허용
     * @return
     * @throws Exception
     */
    public boolean add(String seqJbgmall, int accountStatus, String encryptKey, String encryptIv) throws Exception {
        LocalDBConnection conn = null;
        try {
            conn = new LocalDBConnection();
            conn.txOpen();
            
            StringBuffer querySb = new StringBuffer();
            querySb.append("UPDATE jbg_mall SET");
            querySb.append(" account_status=" + accountStatus);
            if (!JinieboxUtil.isEmpty(encryptKey))
                querySb.append(", encrypt_key='" + encryptKey + "'");
            if (!JinieboxUtil.isEmpty(encryptIv))
                querySb.append(", encrypt_iv='" + encryptIv + "'");
            querySb.append(", last_signin_time=" + System.currentTimeMillis());
            querySb.append(" WHERE seq=" + seqJbgmall);
            
            String query = querySb.toString();
            log.debug("LOCALDB-QUERY------------------------------------------------------------------------------");
            log.debug(query);
            conn.txExecuteUpdate(query);
            conn.txCommit();
            
            return true;
        } catch (SQLException e) {
            log.error("* 아이고!! ㅜ.ㅜ 데이터베이스 업데이트 에러 발생");
            log.error(ExceptionUtil.getExceptionInfo(e));
            throw e;
        } catch (Exception e) {
            log.error("* 데이터베이스 업데이트 에러 발생");
            log.error(ExceptionUtil.getExceptionInfo(e));
            if (conn != null) {
                conn.txRollBack();
            }
            throw e;
        } finally {
            if (conn != null) {
                conn.close();
            }
        }
    }
    
//    /**
//     * 특정 객체(저장소, 보관함, 나눔함)에 대한 공유 정보 삭제
//     * @param typeObject
//     * @param seqObject
//     * @return
//     * @throws Exception
//     */
//    public boolean delete(String typeObject, String seqObject) throws Exception {
//
//        boolean chkSeqObj = NumberUtil.isNumber(seqObject);
//
//        if (typeObject == null || !chkSeqObj) {
//            // 모든 공유정보를 삭제하게 되므로 실패해야 한다.
//            return false;
//        }
//
//        StringBuffer querySb = new StringBuffer();
//        querySb.append("DELETE from share");
//        querySb.append(" WHERE 1=1");
//        if (chkSeqObj) {
//            querySb.append(" AND type_object='" + typeObject + "'");
//            querySb.append(" AND seq_object=" + seqObject);
//        }
//
//        String query = querySb.toString();
//        log.debug("LOCAL-QUERY------------------------------------------------------------------------------");
//        log.debug(query);
//
//        LocalDBConnection conn = null;
//        try {
//            conn = new LocalDBConnection();
//            conn.txOpen();
//            conn.txExecuteUpdate(query);
//            conn.txCommit();
//        } catch (SQLException sqle) {
//            log.error("* 아이고!! ㅜ.ㅜ 데이터베이스 업데이트 에러 발생");
//            log.error(sqle.getMessage());
//            log.debug(ExceptionUtil.getExceptionInfo(sqle.getStackTrace()));
//            sqllog.error(ExceptionUtil.getExceptionInfo(e));
//            throw sqle;
//        } catch (Exception e) {
//            log.error("* 아이고!! ㅜ.ㅜ 데이터베이스 업데이트 에러 발생");
//            log.error(e.getMessage());
//            log.debug(ExceptionUtil.getExceptionInfo(e.getStackTrace()));
//            log.error(ExceptionUtil.getExceptionInfo(e));
//            conn.txRollBack();
//            throw e;
//        } finally {
//            conn.close();
//        }
//        
//        return true;
//    }
}

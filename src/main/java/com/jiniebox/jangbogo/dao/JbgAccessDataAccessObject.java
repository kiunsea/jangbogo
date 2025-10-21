//package com.jiniebox.jangbogo.dao;
//
//import java.sql.ResultSet;
//import java.sql.SQLException;
//import java.util.ArrayList;
//import java.util.List;
//
//import org.apache.commons.logging.Log;
//import org.apache.commons.logging.LogFactory;
//import org.apache.log4j.LogManager;
//import org.apache.log4j.Logger;
//import org.json.simple.JSONObject;
//
//import com.omnibuscode.base.LocalDBConnection;
//import com.omnibuscode.util.JinieboxUtil;
//import com.omnibuscode.utils.ExceptionUtil;
//import com.omnibuscode.utils.NumberUtil;
//
///**
// * 서비스 이용자 접속 정보
// * 
// * @author KIUNSEA
// *
// */
//public class JbgAccessDataAccessObject extends CommonDataAccessObject {
//
//    /**
//     * 서버 환경 변경시 log4j.properties 설정 정보도 함께 변경 필요
//     */
//	private Logger log = LogManager.getLogger(JbgAccessDataAccessObject.class);
//
//    public JbgAccessDataAccessObject() {;}
//    
//    /**
//     * 쇼핑몰 전체 목록과 함께 접속 상태를 반환 (status > -1, 1:접속가능, 0:접속불가)
//     * 
//     * @param seqUser
//     * @return
//     * @throws Exception
//     */
//    public List<JSONObject> getAccessInfos(String seqUser) throws Exception {
//        LocalDBConnection conn = null;
//        try {
//            conn = new LocalDBConnection();
//            StringBuffer querySb = new StringBuffer("SELECT m.seq seq, m.id id, a.account_status status");
//            querySb.append(" FROM jbg_mall m LEFT JOIN jbg_access a");
//            querySb.append(" ON a.seq_jbgmall = m.seq");
//            querySb.append(" AND a.seq_user = " + seqUser);
//            querySb.append(" AND a.account_status > -1");
//            log.debug("LOCALDB-QUERY------------------------------------------------------------------------------");
//            log.debug(querySb);
//            ResultSet rset = conn.executeQuery(querySb.toString());
//
//            List<JSONObject> malls = null;
//            if (rset != null) {
//                malls = new ArrayList<JSONObject>();
//                JSONObject mJson = null;
//                while (rset.next()) {
//                    mJson = new JSONObject();
//                    mJson.put("seq", rset.getInt("seq"));
//                    mJson.put("id", rset.getString("id"));
//                    mJson.put("status", rset.getInt("status"));
//                    malls.add(mJson);
//                }
//                return malls;
//            } else {
//                return null;
//            }
//        } catch (Exception e) {
//            log.error("* 프로그램 수행중 에러 발생");
//            log.error(ExceptionUtil.getExceptionInfo(e));
//            throw e;
//        } finally {
//            conn.close();
//        }
//    }
//    
//    /**
//     *  서비스 이용 가능 여부를 조회
//     * 
//     * @param seqJbgmall
//     * @param seqUser
//     * @return 서비스 상태 (0:사용안함, 1:사용함, 2:계정오류, 3:프로그램오류), -1:미등록
//     * @throws Exception
//     */
//    public int checkAccountStatus(String seqJbgmall, String seqUser) throws Exception {
//        LocalDBConnection conn = null;
//        try {
//            conn = new LocalDBConnection();
//            StringBuffer querySb = new StringBuffer("SELECT account_status from jbg_access");
//            querySb.append(" WHERE seq_jbgmall=" + seqJbgmall);
//            querySb.append(" AND seq_user=" + seqUser);
//
//            log.debug("LOCALDB-QUERY------------------------------------------------------------------------------");
//            log.debug(querySb);
//            ResultSet rset = conn.executeQuery(querySb.toString());
//
//            int status = -1;
//            if (rset != null) {
//                if (rset.next()) {
//                    status = rset.getInt("account_status");
//                    return status;
//                }
//            }
//            return status;
//
//        } catch (Exception e) {
//            log.error("* 프로그램 수행중 에러 발생");
//            log.error(ExceptionUtil.getExceptionInfo(e));
//            throw e;
//        } finally {
//            conn.close();
//        }
//    }
//    
//    /**
//     * 쇼핑몰 사용자로 등록
//     * 
//     * @param seqJbgmall 필수
//     * @param seqUser 필수
//     * @param accountStatus 1:이용 가능, 0:이용 불가, -1:미등록
//     * @param encryptKey null 허용
//     * @param encryptIv null 허용
//     * @return
//     * @throws Exception
//     */
//    public boolean add(String seqJbgmall, String seqUser, int accountStatus, String encryptKey, String encryptIv) throws Exception {
//
//        StringBuffer querySb = new StringBuffer();
//        querySb.append("INSERT INTO jbg_access (");
//        querySb.append("seq_jbgmall");
//        querySb.append(",seq_user");
//        querySb.append(",account_status");
//        if (!JinieboxUtil.isEmpty(encryptKey))
//            querySb.append(",encrypt_key");
//        if (!JinieboxUtil.isEmpty(encryptIv))
//            querySb.append(",encrypt_iv");
//        querySb.append(",last_signin_time");
//        querySb.append(") values (");
//        querySb.append(seqJbgmall);
//        querySb.append(", " + seqUser);
//        querySb.append(", " + accountStatus);
//        if (!JinieboxUtil.isEmpty(encryptKey))
//            querySb.append(", '" + encryptKey + "'");
//        if (!JinieboxUtil.isEmpty(encryptIv))
//            querySb.append(", '" + encryptIv + "'");
//        querySb.append(", " + System.currentTimeMillis());
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
//        } catch (SQLException e) {
//            log.error("* 아이고!! ㅜ.ㅜ 데이터베이스 업데이트 에러 발생");
//            log.error(ExceptionUtil.getExceptionInfo(e));
//            throw e;
//        } catch (Exception e) {
//            log.error("* 데이터베이스 업데이트 에러 발생");
//            log.error(ExceptionUtil.getExceptionInfo(e));
//            conn.txRollBack();
//            throw e;
//        } finally {
//            conn.close();            
//        }
//
//        return true;
//    }
//
//    /**
//     * '마지막 접속 시간'을 제외하여 나머지 필드에 대해 업데이트를 수행
//     * 
//     * @param seqJbgmall 필수
//     * @param seqUser 필수
//     * @param accountStatus  1:이용 가능, 0:이용 불가, -1:미등록
//     * @param encryptKey null 허용
//     * @param encryptIv null 허용
//     * @throws Exception
//     */
//    public void update(String seqJbgmall, String seqUser, int accountStatus, String encryptKey, String encryptIv) throws Exception {
//        LocalDBConnection conn = null;
//        try {
//            conn = new LocalDBConnection();
//            conn.txOpen();
//
//            StringBuffer querySb = new StringBuffer();
//            querySb.append("UPDATE jbg_access set");
//            querySb.append(" account_status=" + accountStatus);
//            if (!JinieboxUtil.isEmpty(encryptKey))
//                querySb.append(", encrypt_key='" + encryptKey + "'");
//            if (!JinieboxUtil.isEmpty(encryptIv))
//                querySb.append(", encrypt_iv='" + encryptIv + "'");
//            querySb.append(" WHERE seq_jbgmall=" + seqJbgmall);
//            querySb.append(" AND seq_user=" + seqUser);
//
//            String query = querySb.toString();
//            log.debug("LOCAL-QUERY------------------------------------------------------------------------------");
//            log.debug(query);
//            conn.txExecuteUpdate(query);
//            conn.txCommit();
//        } catch (SQLException e) {
//            log.error("* 아이고!! ㅜ.ㅜ 데이터베이스 업데이트 에러 발생");
//            log.error(ExceptionUtil.getExceptionInfo(e));
//            throw e;
//        } catch (Exception e) {
//            log.error("* 아이고!! ㅜ.ㅜ 데이터베이스 업데이트 에러 발생");
//            log.error(ExceptionUtil.getExceptionInfo(e));
//            conn.txRollBack();
//            throw e;
//        } finally {
//            conn.close();
//        }
//    }    
//    
//    /**
//     * 마지막 로그인 시간을 현재시간으로 설정한다.
//     * 
//     * @param seqJbgmall
//     * @param seqUser
//     * @throws Exception
//     */
//    public void updateLastSigninTime(String seqJbgmall, String seqUser) throws Exception {
//        LocalDBConnection conn = null;
//        try {
//            conn = new LocalDBConnection();
//            conn.txOpen();
//
//            StringBuffer querySb = new StringBuffer();
//            querySb.append("UPDATE jbg_access set");
//            querySb.append(" last_signin_time=" + System.currentTimeMillis());
//            querySb.append(" WHERE seq_jbgmall=" + seqJbgmall);
//            querySb.append(" AND seq_user=" + seqUser);
//
//            String query = querySb.toString();
//            log.debug("LOCAL-QUERY------------------------------------------------------------------------------");
//            log.debug(query);
//            conn.txExecuteUpdate(query);
//            conn.txCommit();
//        } catch (SQLException e) {
//            log.error("* 아이고!! ㅜ.ㅜ 데이터베이스 업데이트 에러 발생");
//            log.error(ExceptionUtil.getExceptionInfo(e));
//            throw e;
//        } catch (Exception e) {
//            log.error("* 아이고!! ㅜ.ㅜ 데이터베이스 업데이트 에러 발생");
//            log.error(ExceptionUtil.getExceptionInfo(e));
//            conn.txRollBack();
//            throw e;
//        } finally {
//            conn.close();
//        }
//    }
//    
//    /**
//     * @param seqJbgmall
//     * @param seqUser
//     * @param accountStatus 서비스 상태 (0:사용안함, 1:사용함, 2:계정오류, 3:프로그램오류)
//     * @throws Exception
//     */
//    public void setAccountStatus(String seqJbgmall, String seqUser, int accountStatus) throws Exception {
//        LocalDBConnection conn = null;
//        try {
//            conn = new LocalDBConnection();
//            conn.txOpen();
//
//            StringBuffer querySb = new StringBuffer();
//            querySb.append("UPDATE jbg_access set");
//            querySb.append(" account_status=" + accountStatus);
//            if (accountStatus == 1) {
//                querySb.append(", last_signin_time=" + System.currentTimeMillis());
//            }
//            querySb.append(" WHERE seq_jbgmall=" + seqJbgmall);
//            querySb.append(" AND seq_user=" + seqUser);
//
//            String query = querySb.toString();
//            log.debug("LOCAL-QUERY------------------------------------------------------------------------------");
//            log.debug(query);
//            conn.txExecuteUpdate(query);
//            conn.txCommit();
//        } catch (SQLException e) {
//            log.error("* 아이고!! ㅜ.ㅜ 데이터베이스 업데이트 에러 발생");
//            log.error(ExceptionUtil.getExceptionInfo(e));
//            throw e;
//        } catch (Exception e) {
//            log.error("* 아이고!! ㅜ.ㅜ 데이터베이스 업데이트 에러 발생");
//            log.error(ExceptionUtil.getExceptionInfo(e));
//            conn.txRollBack();
//            throw e;
//        } finally {
//            conn.close();
//        }
//    }
//    
//    /**
//     * 쇼핑몰 접속 정보
//     * 
//     * @param seqMall
//     * @param seqUser
//     * @return  {status, enckey, enciv, time}
//     * @throws Exception
//     */
//    public JSONObject getAccessInfo(String seqMall, String seqUser) throws Exception {
//        LocalDBConnection conn = null;
//        try {
//            conn = new LocalDBConnection();
//            StringBuffer querySb = new StringBuffer("SELECT account_status status, encrypt_key, encrypt_iv, last_signin_time time");
//            querySb.append(" FROM jbg_access");
//            querySb.append(" WHERE seq_jbgmall = " + seqMall);
//            querySb.append(" AND seq_user = " + seqUser);
//            log.debug("LOCALDB-QUERY------------------------------------------------------------------------------");
//            log.debug(querySb);
//            ResultSet rset = conn.executeQuery(querySb.toString());
//
//            if (rset != null) {
//                JSONObject mJson = null;
//                if (rset.next()) {
//                    mJson = new JSONObject();
//                    mJson.put("status", rset.getInt("status"));
//                    mJson.put("enckey", rset.getString("encrypt_key"));
//                    mJson.put("enciv", rset.getString("encrypt_iv"));
//                    mJson.put("time", rset.getLong("time"));
//                }
//                return mJson;
//            } else {
//                return null;
//            }
//        } catch (Exception e) {
//            log.error("* 프로그램 수행중 에러 발생");
//            log.error(ExceptionUtil.getExceptionInfo(e));
//            throw e;
//        } finally {
//            conn.close();
//        }
//    }
//   
////    /**
////     * 특정 객체(저장소, 보관함, 나눔함)에 대한 공유 정보 삭제
////     * @param typeObject
////     * @param seqObject
////     * @return
////     * @throws Exception
////     */
////    public boolean delete(String typeObject, String seqObject) throws Exception {
////
////        boolean chkSeqObj = NumberUtil.isNumber(seqObject);
////
////        if (typeObject == null || !chkSeqObj) {
////            // 모든 공유정보를 삭제하게 되므로 실패해야 한다.
////            return false;
////        }
////
////        StringBuffer querySb = new StringBuffer();
////        querySb.append("DELETE from share");
////        querySb.append(" WHERE 1=1");
////        if (chkSeqObj) {
////            querySb.append(" AND type_object='" + typeObject + "'");
////            querySb.append(" AND seq_object=" + seqObject);
////        }
////
////        String query = querySb.toString();
////        log.debug("LOCAL-QUERY------------------------------------------------------------------------------");
////        log.debug(query);
////
////        LocalDBConnection conn = null;
////        try {
////            conn = new LocalDBConnection();
////            conn.txOpen();
////            conn.txExecuteUpdate(query);
////            conn.txCommit();
////        } catch (SQLException sqle) {
////            log.error("* 아이고!! ㅜ.ㅜ 데이터베이스 업데이트 에러 발생");
////            log.error(sqle.getMessage());
////            log.debug(ExceptionUtil.getExceptionInfo(sqle.getStackTrace()));
////            sqllog.error(ExceptionUtil.getExceptionInfo(e));
////            throw sqle;
////        } catch (Exception e) {
////            log.error("* 아이고!! ㅜ.ㅜ 데이터베이스 업데이트 에러 발생");
////            log.error(e.getMessage());
////            log.debug(ExceptionUtil.getExceptionInfo(e.getStackTrace()));
////            log.error(ExceptionUtil.getExceptionInfo(e));
////            conn.txRollBack();
////            throw e;
////        } finally {
////            conn.close();
////        }
////        
////        return true;
////    }
//}

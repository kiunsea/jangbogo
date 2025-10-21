//package com.jiniebox.jangbogo.dao;
//
//import java.sql.ResultSet;
//import java.sql.SQLException;
//
//import org.apache.log4j.LogManager;
//import org.apache.log4j.Logger;
//import org.json.simple.JSONObject;
//
//import com.omnibuscode.base.LocalDBConnection;
//import com.omnibuscode.utils.ExceptionUtil;
//
//public class JbgOrderDataAccessObject extends CommonDataAccessObject {
//
//    /**
//     * 서버 환경 변경시 log4j.properties 설정 정보도 함께 변경 필요
//     */
//	private Logger log = LogManager.getLogger(JbgOrderDataAccessObject.class);
//
//    public JbgOrderDataAccessObject() {;}
//    
//    public int add(String serialNum, String dateTime, String mallName, String seqMall, String seqUser) throws Exception {
//
//        int seqOrder = -1;
//        
//        StringBuffer querySb = new StringBuffer();
//        querySb.append("INSERT INTO jbg_order (");
//        querySb.append("serial_num,");
//        querySb.append("date_time,");
//        querySb.append("mall_name,");
//        querySb.append("seq_jbgmall,");
//        querySb.append("seq_user");
//        querySb.append(") values (");
//        querySb.append("'"+serialNum + "'");
//        querySb.append(", "+ dateTime);
//        querySb.append(", '" + mallName + "'");
//        querySb.append(", "+ seqMall);
//        querySb.append(", "+ seqUser);
//        querySb.append(")");
//        log.debug("LOCALDB-QUERY------------------------------------------------------------------------------");
//        log.debug(querySb);
//
//        LocalDBConnection conn = null;
//        try {
//            conn = new LocalDBConnection();
//            conn.txOpen();
//            conn.txExecuteUpdate(querySb.toString());
//            seqOrder = this.getLastInsertSeq(conn);
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
//        return seqOrder;
//    }
//    
//    /**
//     * 구매정보를 조회
//     * 
//     * @param serialNum 필수
//     * @param dateTime 필수
//     * @param seqUser 옵션 (null 가능)
//     * @return
//     * @throws Exception
//     */
//    public JSONObject getOrder(String serialNum, String dateTime, String seqUser) throws Exception {
//
//        LocalDBConnection conn = null;
//        try {
//            conn = new LocalDBConnection();
//            StringBuffer querySb = new StringBuffer("SELECT seq, seq_jbgmall FROM jbg_order");
//            querySb.append(" WHERE serial_num='" + serialNum + "'");
//            querySb.append(" AND date_time=" + dateTime);
//            if (seqUser != null) {
//                querySb.append(" AND seq_user=" + seqUser);
//            }
//            log.debug("LOCALDB-QUERY------------------------------------------------------------------------------");
//            log.debug(querySb);
//            ResultSet rset = conn.executeQuery(querySb.toString());
//
//            JSONObject jsonObj = null;
//            if (rset != null) {
//                while (rset.next()) {
//                    jsonObj = new JSONObject();
//                    jsonObj.put("seq", rset.getInt("seq"));
//                    jsonObj.put("seq_jbgmall", rset.getInt("seq_jbgmall"));
//                }
//                return jsonObj;
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
////     * 시퀀스로 이름 조회
////     * 
////     * @param seq
////     * @return
////     * @throws Exception
////     */
////    public String getName(int seq) throws Exception {
////        LocalDBConnection conn = null;
////        try {
////            conn = new LocalDBConnection();
////            StringBuffer querySb = new StringBuffer("SELECT name from jbg_mall");
////            querySb.append(" WHERE seq=" + seq);
////
////            log.debug("LOCALDB-QUERY------------------------------------------------------------------------------");
////            log.debug(querySb);
////            ResultSet rset = conn.executeQuery(querySb.toString());
////
////            if (rset != null) {
////                String name = null;
////                if (rset.next()) {
////                    name = rset.getString("name");
////                    return name;
////                }
////            }
////            return null;
////
////        } catch (Exception e) {
////            log.error("* 프로그램 수행중 에러 발생");
////            log.error(ExceptionUtil.getExceptionInfo(e));
////            log.error(e.getMessage());
////            log.debug(ExceptionUtil.getExceptionInfo(e.getStackTrace()));
////            throw e;
////        } finally {
////            conn.close();
////        }
////    }
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

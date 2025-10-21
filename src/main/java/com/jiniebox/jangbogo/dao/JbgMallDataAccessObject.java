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
//import com.omnibuscode.utils.ExceptionUtil;
//import com.omnibuscode.utils.NumberUtil;
//
//public class JbgMallDataAccessObject extends CommonDataAccessObject {
//
//    /**
//     * 서버 환경 변경시 log4j.properties 설정 정보도 함께 변경 필요
//     */
//	private Logger log = LogManager.getLogger(JbgMallDataAccessObject.class);
//
//    public JbgMallDataAccessObject() {;}
//    
//    /**
//     * TODO
//     * 쇼핑몰의 리스트
//     * 
//     * @param seqUser
//     * @return
//     * @throws Exception
//     */
//    public List<JSONObject> getMalls() throws Exception {
//        LocalDBConnection conn = null;
//        try {
//            conn = new LocalDBConnection();
//            StringBuffer querySb = new StringBuffer("SELECT seq, id, name, details");
//            querySb.append(" FROM jbg_mall");
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
//                    mJson.put("name", rset.getInt("name"));
//                    mJson.put("details", rset.getInt("details"));
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
////    /**
////     * 저장소 공유 정보 추가 <br/>
////     * authority : 'R' - 읽기전용(기본값), 'M' - 등록/수정/삭제 가능
////     * @param seqUser
////     * @param typeObject
////     * @param seqObject
////     * @param authority
////     * @return
////     * @throws Exception
////     */
////    public boolean add(String seqUser, String typeObject, String seqObject, String authority) throws Exception {
////
////        StringBuffer querySb = new StringBuffer();
////        querySb.append("INSERT INTO share (");
////        querySb.append("seq_user,");
////        querySb.append("type_object,");
////        querySb.append("seq_object,");
////        querySb.append("authority");
////        querySb.append(") values (");
////        querySb.append(seqUser + ", ");
////        querySb.append("'" + typeObject + "', ");
////        querySb.append(seqObject + ", ");
////        querySb.append("'" + authority + "'");
////        querySb.append(")");
////        log.debug("LOCALDB-QUERY------------------------------------------------------------------------------");
////        log.debug(querySb);
////
////        LocalDBConnection conn = null;
////        try {
////            conn = new LocalDBConnection();
////            conn.txOpen();
////            conn.txExecuteUpdate(querySb.toString());
////            conn.txCommit();
////        } catch (SQLException sqle) {
////            log.error("* 아이고!! ㅜ.ㅜ 데이터베이스 업데이트 에러 발생");
////            log.error(sqle.getMessage());
////            log.debug(ExceptionUtil.getExceptionInfo(sqle.getStackTrace()));
////            sqllog.error(ExceptionUtil.getExceptionInfo(e));
////            throw sqle;
////        } catch (Exception e) {
////            log.error("* 데이터베이스 업데이트 에러 발생");
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
//
//    /**
//     * 시퀀스로 이름 조회
//     * 
//     * @param seq
//     * @return
//     * @throws Exception
//     */
//    public String getName(String seq) throws Exception {
//        LocalDBConnection conn = null;
//        try {
//            conn = new LocalDBConnection();
//            StringBuffer querySb = new StringBuffer("SELECT name from jbg_mall");
//            querySb.append(" WHERE seq=" + seq);
//
//            log.debug("LOCALDB-QUERY------------------------------------------------------------------------------");
//            log.debug(querySb);
//            ResultSet rset = conn.executeQuery(querySb.toString());
//
//            if (rset != null) {
//                String name = null;
//                if (rset.next()) {
//                    name = rset.getString("name");
//                    return name;
//                }
//            }
//            return null;
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

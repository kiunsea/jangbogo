package com.jiniebox.jangbogo.dao;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;

import com.jiniebox.jangbogo.util.ExceptionUtil;

/**
 * 주문 정보 DAO
 * 
 * jbg_order 테이블에 접근하는 모든 기능 제공
 * 
 * @author KIUNSEA
 */
public class JbgOrderDataAccessObject extends CommonDataAccessObject {

    /**
     * 서버 환경 변경시 log4j.properties 설정 정보도 함께 변경 필요
     */
    private Logger log = LogManager.getLogger(JbgOrderDataAccessObject.class);

    public JbgOrderDataAccessObject() {
        // 기본 생성자
    }
    
    /**
     * 주문 정보 등록
     * 
     * @param serialNum 시리얼 번호 (영수증 바코드 또는 주문번호)
     * @param dateTime 구매일자 (YYYYMMDD 형식의 정수)
     * @param mallName 매장명
     * @param seqMall 쇼핑몰 시퀀스
     * @return 생성된 주문 시퀀스
     * @throws Exception
     */
    public int add(String serialNum, String dateTime, String mallName, String seqMall) throws Exception {

        int seqOrder = -1;
        
        StringBuffer querySb = new StringBuffer();
        querySb.append("INSERT INTO jbg_order (");
        querySb.append("serial_num,");
        querySb.append("date_time,");
        querySb.append("mall_name,");
        querySb.append("seq_mall");
        querySb.append(") values (");
        querySb.append("'" + serialNum + "'");
        querySb.append(", " + dateTime);
        if (mallName != null && !mallName.isEmpty()) {
            querySb.append(", '" + mallName + "'");
        } else {
            querySb.append(", NULL");
        }
        querySb.append(", " + seqMall);
        querySb.append(")");
        log.debug("LOCALDB-QUERY------------------------------------------------------------------------------");
        log.debug(querySb);

        LocalDBConnection conn = null;
        try {
            conn = new LocalDBConnection();
            conn.txOpen();
            
            log.info("주문 등록 시도 - serial: {}, datetime: {}, mallName: {}, seqMall: {}", 
                     serialNum, dateTime, mallName, seqMall);
            
            conn.txExecuteUpdate(querySb.toString());
            
            // SQLite에서는 last_insert_rowid() 사용
            ResultSet rset = conn.executeQuery("SELECT last_insert_rowid() id");
            if (rset != null && rset.next()) {
                seqOrder = rset.getInt("id");
                log.info("주문 등록 성공 - seq_order: {}", seqOrder);
            } else {
                log.warn("주문 등록 후 seq_order 조회 실패");
            }
            
            conn.txCommit();
            log.debug("트랜잭션 커밋 완료");
        } catch (SQLException e) {
            log.error("* 아이고!! ㅜ.ㅜ 데이터베이스 업데이트 에러 발생");
            log.error(ExceptionUtil.getExceptionInfo(e));
            if (conn != null) {
                conn.txRollBack();
            }
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

        return seqOrder;
    }
    
    /**
     * 구매정보를 조회
     * 
     * @param serialNum 필수
     * @param dateTime 필수 (YYYYMMDD 형식 문자열)
     * @param seqUser 옵션 (null 가능, 현재 schema에는 seq_user 컬럼이 없지만 호환성을 위해 유지)
     * @return 주문 정보 (seq, seq_mall 포함)
     * @throws Exception
     */
    public JSONObject getOrder(String serialNum, String dateTime, String seqUser) throws Exception {

        LocalDBConnection conn = null;
        try {
            conn = new LocalDBConnection();
            StringBuffer querySb = new StringBuffer("SELECT seq, seq_mall FROM jbg_order");
            querySb.append(" WHERE serial_num='" + serialNum + "'");
            querySb.append(" AND date_time=" + dateTime);
            // seq_user 컬럼이 schema에 없으므로 제외
            log.debug("LOCALDB-QUERY------------------------------------------------------------------------------");
            log.debug(querySb);
            ResultSet rset = conn.executeQuery(querySb.toString());

            JSONObject jsonObj = null;
            if (rset != null) {
                if (rset.next()) {
                    jsonObj = new JSONObject();
                    jsonObj.put("seq", rset.getInt("seq"));
                    jsonObj.put("seq_mall", rset.getInt("seq_mall"));
                }
                return jsonObj;
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
}

package com.jiniebox.jangbogo.dao;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import com.jiniebox.jangbogo.dto.JangbogoConfig;
import com.jiniebox.jangbogo.util.ExceptionUtil;

public class CommonDataAccessObject {
    
    private static final Logger logger = LogManager.getLogger(CommonDataAccessObject.class);
    
    @Autowired
    private JangbogoConfig jangbogoConfig;
    
    /**
     * AUTO_INCREMENT 컬럼의 다음 시퀀스
     * @param tableName
     * @return
     * @throws Exception
     */
    protected int getNextSeq(String tableName) throws Exception {
        LocalDBConnection conn = null;
        try {
            conn = new LocalDBConnection();
            StringBuffer querySb = new StringBuffer(
                    "SELECT AUTO_INCREMENT seq "
                    + "FROM information_schema.tables "
                    + "WHERE table_schema = '" + jangbogoConfig.get("LOCALDB_NAME") + "' AND table_name = '" + tableName + "';");
            logger.debug("LOCALDB-QUERY------------------------------------------------------------------------------");
            logger.debug(querySb);
            ResultSet rset = conn.executeQuery(querySb.toString());

            if (rset != null) {
                if (rset.next()) {
                    return rset.getInt("seq");
                }
                return -1;
            }
            return -1;
        } catch (Exception e) {
            logger.error("* 프로그램 수행중 에러 발생");
            logger.error(ExceptionUtil.getExceptionInfo(e));
            throw e;
        } finally {
            conn.close();
        }
    }
    
    /**
     * 가장 최근에 성공적으로 수행된 INSERT 구문의 첫번째 AUTO_INCREMENT column의 값을 반환받는 쿼리
     * 
     * @return
     * @throws SQLException 
     * @throws InstantiationException 
     * @throws IllegalAccessException 
     * @throws ClassNotFoundException 
     */
    protected int getLastInsertSeq(LocalDBConnection conn) throws SQLException {
        int seq = -1;
        String getLastIdQuery = "SELECT LAST_INSERT_ID() id";
        ResultSet rset = conn.executeQuery(getLastIdQuery);
        if (rset != null) {
            if (rset.next()) {
                seq = rset.getInt("id");
            }
        }

        return seq;
    }
    
    /**
     * 조회 결과 사이즈 반환
     * 
     * @param rset
     * @return
     * @throws SQLException
     */
    protected int getResultSetSize(ResultSet rset) throws SQLException {
        if (rset != null) {
            rset.last();
            int cnt = rset.getRow();
            rset.first();
            return cnt;
        } else {
            return -1;
        }
    }
    
    /**
     * NULL 과 EMPTY 값 체크
     * 
     * @param val
     * @return
     */
    protected boolean isNotNull(Object val) {
        return val != null && val.toString().length() > 0; // 공백도 null 로 인식하게
    }
}

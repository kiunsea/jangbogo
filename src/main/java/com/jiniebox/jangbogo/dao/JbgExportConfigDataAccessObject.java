package com.jiniebox.jangbogo.dao;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;

import com.jiniebox.jangbogo.util.ExceptionUtil;

/**
 * 파일 저장 설정 DAO
 * 
 * jbg_export_config 테이블에 접근하는 모든 기능 제공
 * 
 * @author KIUNSEA
 */
public class JbgExportConfigDataAccessObject extends CommonDataAccessObject {

    private static final Logger log = LogManager.getLogger(JbgExportConfigDataAccessObject.class);

    public JbgExportConfigDataAccessObject() {
        // 기본 생성자
    }
    
    /**
     * 파일 저장 설정 조회 (단일 설정)
     * 
     * @return 설정 정보 (save_path, save_format, auto_save_enabled)
     * @throws Exception
     */
    public JSONObject getConfig() throws Exception {
        LocalDBConnection conn = null;
        try {
            conn = new LocalDBConnection();
            ensureExportConfigTable(conn);
            
            StringBuffer querySb = new StringBuffer("SELECT id, save_path, save_format, auto_save_enabled");
            querySb.append(" FROM jbg_export_config WHERE id=1");
            log.debug("LOCALDB-QUERY------------------------------------------------------------------------------");
            log.debug(querySb);
            ResultSet rset = conn.executeQuery(querySb.toString());

            JSONObject config = null;
            if (rset != null && rset.next()) {
                config = new JSONObject();
                config.put("id", rset.getInt("id"));
                config.put("save_path", rset.getString("save_path"));
                config.put("save_format", rset.getString("save_format"));
                config.put("auto_save_enabled", rset.getInt("auto_save_enabled"));
            } else {
                // 설정이 없으면 기본값 생성
                config = createDefaultConfig();
            }
            return config;
        } catch (Exception e) {
            log.error("설정 조회 중 에러 발생");
            log.error(ExceptionUtil.getExceptionInfo(e));
            throw e;
        } finally {
            if (conn != null) {
                conn.close();
            }
        }
    }
    
    /**
     * 파일 저장 설정 업데이트
     * 
     * @param savePath 저장 경로
     * @param saveFormat 저장 포맷
     * @param autoSaveEnabled 구매내역 수집시 함께 저장 여부 (0 or 1)
     * @throws Exception
     */
    public void updateConfig(String savePath, String saveFormat, int autoSaveEnabled) throws Exception {
        LocalDBConnection conn = null;
        try {
            conn = new LocalDBConnection();
            ensureExportConfigTable(conn);
            conn.txOpen();
            
            // 기존 설정 확인
            ResultSet checkRset = conn.executeQuery("SELECT id FROM jbg_export_config WHERE id=1");
            boolean exists = checkRset != null && checkRset.next();
            
            if (exists) {
                // 업데이트
                StringBuffer querySb = new StringBuffer();
                querySb.append("UPDATE jbg_export_config SET");
                querySb.append(" save_path='" + savePath + "'");
                querySb.append(", save_format='" + saveFormat + "'");
                querySb.append(", auto_save_enabled=" + autoSaveEnabled);
                querySb.append(" WHERE id=1");
                
                log.debug("LOCALDB-QUERY------------------------------------------------------------------------------");
                log.debug(querySb);
                conn.txExecuteUpdate(querySb.toString());
                log.info("파일 저장 설정 업데이트 완료");
            } else {
                // 삽입
                StringBuffer querySb = new StringBuffer();
                querySb.append("INSERT INTO jbg_export_config (id, save_path, save_format, auto_save_enabled)");
                querySb.append(" VALUES (1, '" + savePath + "', '" + saveFormat + "', " + autoSaveEnabled + ")");
                
                log.debug("LOCALDB-QUERY------------------------------------------------------------------------------");
                log.debug(querySb);
                conn.txExecuteUpdate(querySb.toString());
                log.info("파일 저장 설정 생성 완료");
            }
            
            conn.txCommit();
        } catch (SQLException e) {
            log.error("설정 저장 중 DB 에러 발생");
            log.error(ExceptionUtil.getExceptionInfo(e));
            if (conn != null) conn.txRollBack();
            throw e;
        } catch (Exception e) {
            log.error("설정 저장 중 에러 발생");
            log.error(ExceptionUtil.getExceptionInfo(e));
            if (conn != null) conn.txRollBack();
            throw e;
        } finally {
            if (conn != null) conn.close();
        }
    }
    
    /**
     * jbg_export_config 테이블이 없으면 생성
     */
    private void ensureExportConfigTable(LocalDBConnection conn) {
        try {
            conn.executeQuery("SELECT id FROM jbg_export_config LIMIT 1");
        } catch (Exception e) {
            try {
                conn.txOpen();
                String createTable = "CREATE TABLE IF NOT EXISTS jbg_export_config (" +
                    "id INTEGER PRIMARY KEY DEFAULT 1, " +
                    "save_path TEXT NOT NULL DEFAULT '', " +
                    "save_format TEXT NOT NULL DEFAULT 'json', " +
                    "auto_save_enabled INTEGER NOT NULL DEFAULT 0)";
                conn.txExecuteUpdate(createTable);
                conn.txCommit();
                log.info("jbg_export_config 테이블 생성 완료");
            } catch (Exception ex) {
                try { conn.txRollBack(); } catch (Exception ignore) {}
                log.debug("jbg_export_config 테이블 확인: {}", ex.getMessage());
            }
        }
    }
    
    /**
     * 기본 설정 생성 및 반환
     */
    private JSONObject createDefaultConfig() throws Exception {
        JSONObject config = new JSONObject();
        config.put("id", 1);
        config.put("save_path", "");
        config.put("save_format", "json");
        config.put("auto_save_enabled", 0);
        
        // DB에 기본값 삽입
        updateConfig("", "json", 0);
        
        return config;
    }
}


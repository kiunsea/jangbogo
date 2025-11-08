package com.jiniebox.jangbogo.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;

import com.jiniebox.jangbogo.util.ExceptionUtil;

/**
 * 아이템 정보 DAO
 * 
 * jbg_item 테이블에 접근하는 모든 기능 제공
 * 
 * @author KIUNSEA
 */
public class JbgItemDataAccessObject extends CommonDataAccessObject {

    private static final Logger log = LogManager.getLogger(JbgItemDataAccessObject.class);

    public JbgItemDataAccessObject() {
        // 기본 생성자
    }
    
    // ========== 아이템 조회 메서드 ==========
    
    /**
     * 아이템 전체 목록 조회 (모든 필드 포함)
     * 
     * @return 아이템 목록 (모든 필드)
     * @throws Exception
     */
    public List<JSONObject> getAllItems() throws Exception {
        LocalDBConnection conn = null;
        try {
            conn = new LocalDBConnection();
            
            // qty 컬럼 존재 여부 확인 및 자동 생성
            ensureQtyColumn(conn);
            
            StringBuffer querySb = new StringBuffer("SELECT seq, name, seq_order, qty, insert_time");
            querySb.append(" FROM jbg_item");
            querySb.append(" ORDER BY seq DESC");
            log.debug("LOCALDB-QUERY------------------------------------------------------------------------------");
            log.debug(querySb);
            ResultSet rset = conn.executeQuery(querySb.toString());

            List<JSONObject> items = null;
            if (rset != null) {
                items = new ArrayList<JSONObject>();
                JSONObject itemJson = null;
                while (rset.next()) {
                    itemJson = new JSONObject();
                    itemJson.put("seq", rset.getInt("seq"));
                    itemJson.put("name", rset.getString("name"));
                    itemJson.put("seq_order", safeGetInt(rset, "seq_order", 0));
                    itemJson.put("qty", safeGetString(rset, "qty", ""));
                    itemJson.put("insert_time", safeGetLong(rset, "insert_time", 0));
                    items.add(itemJson);
                }
                return items;
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
     * 특정 아이템의 전체 정보 조회 (모든 필드)
     * 
     * @param seq 아이템 시퀀스
     * @return 아이템 전체 정보 (모든 필드)
     * @throws Exception
     */
    public JSONObject getItem(String seq) throws Exception {
        LocalDBConnection conn = null;
        try {
            conn = new LocalDBConnection();
            
            // qty 컬럼 존재 여부 확인 및 자동 생성
            ensureQtyColumn(conn);
            
            StringBuffer querySb = new StringBuffer("SELECT seq, name, seq_order, qty, insert_time");
            querySb.append(" FROM jbg_item");
            querySb.append(" WHERE seq=" + seq);
            log.debug("LOCALDB-QUERY------------------------------------------------------------------------------");
            log.debug(querySb);
            ResultSet rset = conn.executeQuery(querySb.toString());

            if (rset != null) {
                JSONObject itemJson = null;
                if (rset.next()) {
                    itemJson = new JSONObject();
                    itemJson.put("seq", rset.getInt("seq"));
                    itemJson.put("name", rset.getString("name"));
                    itemJson.put("seq_order", safeGetInt(rset, "seq_order", 0));
                    itemJson.put("qty", safeGetString(rset, "qty", ""));
                    itemJson.put("insert_time", safeGetLong(rset, "insert_time", 0));
                }
                return itemJson;
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
     * 주문 시퀀스로 아이템 목록 조회
     * 
     * @param seqOrder 주문 시퀀스
     * @return 아이템 목록
     * @throws Exception
     */
    public List<JSONObject> getItemsByOrder(String seqOrder) throws Exception {
        LocalDBConnection conn = null;
        try {
            conn = new LocalDBConnection();
            
            // qty 컬럼 존재 여부 확인 및 자동 생성
            ensureQtyColumn(conn);
            
            StringBuffer querySb = new StringBuffer("SELECT seq, name, seq_order, qty, insert_time");
            querySb.append(" FROM jbg_item");
            querySb.append(" WHERE seq_order=" + seqOrder);
            querySb.append(" ORDER BY seq DESC");
            log.debug("LOCALDB-QUERY------------------------------------------------------------------------------");
            log.debug(querySb);
            ResultSet rset = conn.executeQuery(querySb.toString());

            List<JSONObject> items = null;
            if (rset != null) {
                items = new ArrayList<JSONObject>();
                JSONObject itemJson = null;
                while (rset.next()) {
                    itemJson = new JSONObject();
                    itemJson.put("seq", rset.getInt("seq"));
                    itemJson.put("name", rset.getString("name"));
                    itemJson.put("seq_order", safeGetInt(rset, "seq_order", 0));
                    itemJson.put("qty", safeGetString(rset, "qty", ""));
                    itemJson.put("insert_time", safeGetLong(rset, "insert_time", 0));
                    items.add(itemJson);
                }
                return items;
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
    
    // ========== 아이템 등록/수정 메서드 ==========
    
    /**
     * 아이템 등록
     * 
     * @param name 아이템명 (필수)
     * @param seqOrder 주문 시퀀스 (옵션, null 가능)
     * @return 생성된 아이템 시퀀스
     * @throws Exception
     */
    public int add(String name, String seqOrder) throws Exception {
        return add(name, seqOrder, null);
    }
    
    /**
     * 아이템 등록 (수량 포함)
     * 
     * @param name 아이템명 (필수)
     * @param seqOrder 주문 시퀀스 (옵션, null 가능)
     * @param qty 수량 (옵션, null 가능)
     * @return 생성된 아이템 시퀀스
     * @throws Exception
     */
    public int add(String name, String seqOrder, String qty) throws Exception {
        LocalDBConnection conn = null;
        try {
            conn = new LocalDBConnection();
            
            // qty 컬럼이 있는지 확인하고 없으면 추가
            ensureQtyColumn(conn);
            
            conn.txOpen();
            
            StringBuffer querySb = new StringBuffer();
            querySb.append("INSERT INTO jbg_item (");
            querySb.append("name");
            if (seqOrder != null && !seqOrder.isEmpty()) {
                querySb.append(", seq_order");
            }
            if (qty != null && !qty.isEmpty()) {
                querySb.append(", qty");
            }
            querySb.append(", insert_time");
            querySb.append(") values (");
            querySb.append("'" + name + "'");
            if (seqOrder != null && !seqOrder.isEmpty()) {
                querySb.append(", " + seqOrder);
            }
            if (qty != null && !qty.isEmpty()) {
                querySb.append(", '" + qty + "'");
            }
            querySb.append(", " + System.currentTimeMillis());
            querySb.append(")");
            
            String query = querySb.toString();
            log.debug("LOCALDB-QUERY------------------------------------------------------------------------------");
            log.debug(query);
            conn.txExecuteUpdate(query);
            
            int seq = this.getLastInsertSeq(conn);
            conn.txCommit();
            
            return seq;
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
    }
    
    /**
     * jbg_item 테이블에 qty 컬럼이 있는지 확인하고 없으면 추가
     */
    private void ensureQtyColumn(LocalDBConnection conn) {
        try {
            // qty 컬럼 존재 여부 확인
            conn.executeQuery("SELECT qty FROM jbg_item LIMIT 1");
        } catch (Exception e) {
            // qty 컬럼이 없으면 추가
            try {
                conn.txOpen();
                conn.txExecuteUpdate("ALTER TABLE jbg_item ADD COLUMN qty TEXT DEFAULT ''");
                conn.txCommit();
                log.info("jbg_item 테이블에 qty 컬럼 추가 완료");
            } catch (Exception ex) {
                try { conn.txRollBack(); } catch (Exception ignore) {}
                log.debug("qty 컬럼 추가 체크: {}", ex.getMessage());
            }
        }
    }
    
    /**
     * 아이템 정보 업데이트
     * 
     * @param seq 아이템 시퀀스 (필수)
     * @param name 아이템명 (옵션, null 가능)
     * @param seqOrder 주문 시퀀스 (옵션, null 가능)
     * @throws Exception
     */
    public void update(String seq, String name, String seqOrder) throws Exception {
        LocalDBConnection conn = null;
        try {
            conn = new LocalDBConnection();
            conn.txOpen();

            StringBuffer querySb = new StringBuffer();
            querySb.append("UPDATE jbg_item SET");
            boolean hasUpdate = false;
            if (name != null && !name.isEmpty()) {
                querySb.append(" name='" + name + "'");
                hasUpdate = true;
            }
            if (seqOrder != null) {
                if (hasUpdate) {
                    querySb.append(",");
                }
                if (seqOrder.isEmpty()) {
                    querySb.append(" seq_order=NULL");
                } else {
                    querySb.append(" seq_order=" + seqOrder);
                }
                hasUpdate = true;
            }
            if (!hasUpdate) {
                log.warn("업데이트할 항목이 없습니다. seq={}", seq);
                conn.txRollBack();
                return;
            }
            querySb.append(" WHERE seq=" + seq);

            String query = querySb.toString();
            log.debug("LOCALDB-QUERY------------------------------------------------------------------------------");
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
    
    // ========== 아이템 삭제 메서드 ==========
    
    /**
     * 아이템 삭제
     * 
     * @param seq 아이템 시퀀스
     * @return 삭제 성공 여부
     * @throws Exception
     */
    public boolean delete(String seq) throws Exception {
        LocalDBConnection conn = null;
        try {
            conn = new LocalDBConnection();
            conn.txOpen();

            StringBuffer querySb = new StringBuffer();
            querySb.append("DELETE FROM jbg_item");
            querySb.append(" WHERE seq=" + seq);

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
     * 주문 시퀀스로 연관된 모든 아이템 삭제
     * 
     * @param seqOrder 주문 시퀀스
     * @return 삭제 성공 여부
     * @throws Exception
     */
    public boolean deleteByOrder(String seqOrder) throws Exception {
        LocalDBConnection conn = null;
        try {
            conn = new LocalDBConnection();
            conn.txOpen();

            StringBuffer querySb = new StringBuffer();
            querySb.append("DELETE FROM jbg_item");
            querySb.append(" WHERE seq_order=" + seqOrder);

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
    
    // ========== 유틸리티 메서드 ==========
    
    /**
     * ResultSet에서 안전하게 정수 값 가져오기
     * 
     * @param rset ResultSet
     * @param columnName 컬럼명
     * @param defaultValue 기본값
     * @return 정수 값
     */
    private int safeGetInt(ResultSet rset, String columnName, int defaultValue) {
        try {
            int value = rset.getInt(columnName);
            if (rset.wasNull()) {
                return defaultValue;
            }
            return value;
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    /**
     * ResultSet에서 안전하게 Long 값 가져오기
     * 
     * @param rset ResultSet
     * @param columnName 컬럼명
     * @param defaultValue 기본값
     * @return Long 값
     */
    private long safeGetLong(ResultSet rset, String columnName, long defaultValue) {
        try {
            long value = rset.getLong(columnName);
            if (rset.wasNull()) {
                return defaultValue;
            }
            return value;
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    /**
     * ResultSet에서 안전하게 String 값 가져오기
     * 
     * @param rset ResultSet
     * @param columnName 컬럼명
     * @param defaultValue 기본값
     * @return String 값
     */
    private String safeGetString(ResultSet rset, String columnName, String defaultValue) {
        try {
            String value = rset.getString(columnName);
            if (value == null) {
                return defaultValue;
            }
            return value;
        } catch (Exception e) {
            return defaultValue;
        }
    }
}


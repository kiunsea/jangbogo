package com.jiniebox.jangbogo.dao;

import com.jiniebox.jangbogo.util.ExceptionUtil;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;

/**
 * 주문 정보 DAO
 *
 * <p>jbg_order 테이블에 접근하는 모든 기능 제공
 *
 * @author KIUNSEA
 */
public class JbgOrderDataAccessObject extends CommonDataAccessObject {

  /** 서버 환경 변경시 log4j.properties 설정 정보도 함께 변경 필요 */
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
  public int add(String serialNum, String dateTime, String mallName, String seqMall)
      throws Exception {

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
    log.debug(
        "LOCALDB-QUERY------------------------------------------------------------------------------");
    log.debug(querySb);

    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();
      conn.txOpen();

      log.info(
          "주문 등록 시도 - serial: {}, datetime: {}, mallName: {}, seqMall: {}",
          serialNum,
          dateTime,
          mallName,
          seqMall);

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
   * 주문 정보 등록 (기존 Connection 사용, PreparedStatement로 SQL Injection 방지)
   *
   * <p>트랜잭션 관리는 호출자가 담당합니다. 이 메서드는 기존 트랜잭션 내에서 실행됩니다.
   *
   * @param conn 기존 LocalDBConnection (트랜잭션이 이미 시작된 상태)
   * @param serialNum 시리얼 번호 (영수증 바코드 또는 주문번호)
   * @param dateTime 구매일자 (YYYYMMDD 형식의 정수 문자열)
   * @param mallName 매장명
   * @param seqMall 쇼핑몰 시퀀스
   * @return 생성된 주문 시퀀스
   * @throws Exception
   */
  public int addWithConnection(
      LocalDBConnection conn, String serialNum, String dateTime, String mallName, String seqMall)
      throws Exception {

    int seqOrder = -1;

    // PreparedStatement 사용으로 SQL Injection 방지
    String query =
        "INSERT INTO jbg_order (serial_num, date_time, mall_name, seq_mall) VALUES (?, ?, ?, ?)";

    log.debug(
        "LOCALDB-QUERY------------------------------------------------------------------------------");
    log.debug(
        "{} [serialNum={}, dateTime={}, mallName={}, seqMall={}]",
        query,
        serialNum,
        dateTime,
        mallName,
        seqMall);

    log.info(
        "주문 등록 시도 (트랜잭션 내) - serial: {}, datetime: {}, mallName: {}, seqMall: {}",
        serialNum,
        dateTime,
        mallName,
        seqMall);

    // PreparedStatement로 실행
    conn.txPstmtExecuteUpdate(query, serialNum, dateTime, mallName, seqMall);

    // SQLite에서는 last_insert_rowid() 사용
    ResultSet rset = conn.executeQuery("SELECT last_insert_rowid() id");
    if (rset != null && rset.next()) {
      seqOrder = rset.getInt("id");
      log.info("주문 등록 성공 (트랜잭션 내) - seq_order: {}", seqOrder);
    } else {
      log.warn("주문 등록 후 seq_order 조회 실패");
    }

    return seqOrder;
  }

  /**
   * 모든 주문 조회
   *
   * @return 주문 목록 (seq, serial_num, date_time, mall_name, seq_mall 포함)
   * @throws Exception
   */
  public List<JSONObject> getAllOrders() throws Exception {
    return getAllOrders(0); // 제한 없이 전체 조회
  }

  /**
   * 주문 조회 (개수 제한 옵션)
   *
   * @param limit 조회 개수 (0이면 전체, 양수면 해당 개수만큼만 조회)
   * @return 주문 목록 (seq, serial_num, date_time, mall_name, seq_mall 포함)
   * @throws Exception
   */
  public List<JSONObject> getAllOrders(int limit) throws Exception {
    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();
      StringBuffer querySb =
          new StringBuffer("SELECT seq, serial_num, date_time, mall_name, seq_mall FROM jbg_order");
      querySb.append(" ORDER BY date_time DESC, seq DESC");

      if (limit > 0) {
        querySb.append(" LIMIT " + limit);
      }

      log.debug(
          "LOCALDB-QUERY------------------------------------------------------------------------------");
      log.debug(querySb);
      ResultSet rset = conn.executeQuery(querySb.toString());

      List<JSONObject> orders = null;
      if (rset != null) {
        orders = new java.util.ArrayList<>();
        while (rset.next()) {
          JSONObject orderJson = new JSONObject();
          orderJson.put("seq", rset.getInt("seq"));
          orderJson.put("serial_num", rset.getString("serial_num"));
          orderJson.put("date_time", rset.getInt("date_time"));
          orderJson.put("mall_name", rset.getString("mall_name"));
          orderJson.put("seq_mall", rset.getInt("seq_mall"));
          orders.add(orderJson);
        }
      }

      log.info(
          "주문 조회 완료 - count: {}, limit: {}",
          orders != null ? orders.size() : 0,
          limit > 0 ? limit : "전체");

      return orders;
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
   * 현재 저장된 주문의 최대 seq 값 조회
   *
   * @return 최대 seq 값, 데이터가 없으면 0
   * @throws Exception
   */
  public int getMaxSeq() throws Exception {
    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();
      StringBuffer querySb = new StringBuffer("SELECT MAX(seq) as max_seq FROM jbg_order");
      log.debug(
          "LOCALDB-QUERY------------------------------------------------------------------------------");
      log.debug(querySb);
      ResultSet rset = conn.executeQuery(querySb.toString());

      int maxSeq = 0;
      if (rset != null && rset.next()) {
        maxSeq = rset.getInt("max_seq");
      }
      log.debug("현재 최대 주문 seq: {}", maxSeq);
      return maxSeq;
    } catch (Exception e) {
      log.error("* 최대 seq 조회 중 에러 발생");
      log.error(ExceptionUtil.getExceptionInfo(e));
      throw e;
    } finally {
      if (conn != null) {
        conn.close();
      }
    }
  }

  /**
   * 특정 seq 이후의 주문 조회 (새로 추가된 주문만)
   *
   * @param afterSeq 이 seq 이후의 주문만 조회
   * @return 주문 목록
   * @throws Exception
   */
  public List<JSONObject> getOrdersAfterSeq(int afterSeq) throws Exception {
    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();
      StringBuffer querySb =
          new StringBuffer("SELECT seq, serial_num, date_time, mall_name, seq_mall FROM jbg_order");
      querySb.append(" WHERE seq > " + afterSeq);
      querySb.append(" ORDER BY date_time DESC, seq DESC");
      log.debug(
          "LOCALDB-QUERY------------------------------------------------------------------------------");
      log.debug(querySb);
      ResultSet rset = conn.executeQuery(querySb.toString());

      List<JSONObject> orders = null;
      if (rset != null) {
        orders = new java.util.ArrayList<>();
        while (rset.next()) {
          JSONObject orderJson = new JSONObject();
          orderJson.put("seq", rset.getInt("seq"));
          orderJson.put("serial_num", rset.getString("serial_num"));
          orderJson.put("date_time", rset.getInt("date_time"));
          orderJson.put("mall_name", rset.getString("mall_name"));
          orderJson.put("seq_mall", rset.getInt("seq_mall"));
          orders.add(orderJson);
        }
      }
      log.info(
          "신규 주문 조회 완료 - afterSeq: {}, count: {}", afterSeq, orders != null ? orders.size() : 0);
      return orders;
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
   * 특정 seq 목록의 주문 조회
   *
   * @param seqList 조회할 주문 seq 목록
   * @return 주문 목록
   * @throws Exception
   */
  public List<JSONObject> getOrdersBySeqList(List<Integer> seqList) throws Exception {
    if (seqList == null || seqList.isEmpty()) {
      return new java.util.ArrayList<>();
    }

    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();

      // IN 절 생성
      StringBuilder seqsIn = new StringBuilder();
      for (int i = 0; i < seqList.size(); i++) {
        if (i > 0) seqsIn.append(",");
        seqsIn.append(seqList.get(i));
      }

      StringBuffer querySb =
          new StringBuffer("SELECT seq, serial_num, date_time, mall_name, seq_mall FROM jbg_order");
      querySb.append(" WHERE seq IN (" + seqsIn.toString() + ")");
      querySb.append(" ORDER BY date_time DESC, seq DESC");
      log.debug(
          "LOCALDB-QUERY------------------------------------------------------------------------------");
      log.debug(querySb);
      ResultSet rset = conn.executeQuery(querySb.toString());

      List<JSONObject> orders = null;
      if (rset != null) {
        orders = new java.util.ArrayList<>();
        while (rset.next()) {
          JSONObject orderJson = new JSONObject();
          orderJson.put("seq", rset.getInt("seq"));
          orderJson.put("serial_num", rset.getString("serial_num"));
          orderJson.put("date_time", rset.getInt("date_time"));
          orderJson.put("mall_name", rset.getString("mall_name"));
          orderJson.put("seq_mall", rset.getInt("seq_mall"));
          orders.add(orderJson);
        }
      }
      log.info(
          "seq 목록으로 주문 조회 완료 - seqs: {}, count: {}", seqList, orders != null ? orders.size() : 0);
      return orders;
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
      log.debug(
          "LOCALDB-QUERY------------------------------------------------------------------------------");
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

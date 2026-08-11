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

    // 값을 문자열로 이어 붙이지 않는다 (B-3). serial_num·mall_name 은 수집한 웹페이지에서
    // 온 값이라 우리가 통제하지 못한다. 매장명에 작은따옴표 하나만 있어도 INSERT 가 깨지고,
    // 그 자리는 그대로 SQL 주입 지점이 된다.
    String query =
        "INSERT INTO jbg_order (serial_num, date_time, mall_name, seq_mall) VALUES (?, ?, ?, ?)";
    log.debug(
        "LOCALDB-QUERY------------------------------------------------------------------------------");
    log.debug(query);

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

      conn.txPstmtExecuteUpdate(query, serialNum, dateTime, mallName, seqMall);

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
      // 주문 1건 + 아이템 N건이 한 트랜잭션에 묶여 있다. 인라인 txRollBack() 이 던지면 원래 실패
      // 원인이 그 예외로 덮여, "어느 아이템에서 깨졌나" 를 잃는다.
      rollbackQuietly(conn);
      throw e;
    } catch (Exception e) {
      log.error("* 데이터베이스 업데이트 에러 발생");
      log.error(ExceptionUtil.getExceptionInfo(e));
      rollbackQuietly(conn);
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
  /**
   * 그 수집기가 <b>실제로 저장한</b> 가장 최근 구매일을 돌려준다 (기간 조회형 수집기의 조회 시작점).
   *
   * <p><b>따로 저장한 기준일이 아니라 데이터에서 유도한다.</b> 기준일을 별도로 적어 두면 저장이 실패한 회차에도 앞서 나갈 수 있고, 그러면 그 구간은 다음 회차의
   * 시작점이 더 뒤라서 <b>영영 조회되지 않는다.</b> 여기처럼 유도하면 저장이 실패한 만큼 기준일도 자동으로 뒤에 남아 다시 가져온다.
   *
   * <p><b>{@code collector} 로 반드시 좁힌다.</b> 한 몰에 수집기가 둘일 수 있다(하나로 = 온라인 + 오프라인). 몰 단위로 최대값을 구하면 온라인
   * 주문이 최근이라는 이유로 오프라인 조회 시작점이 밀려 그 사이의 오프라인 거래를 영구히 놓친다.
   *
   * <p>기존 행은 {@code collector} 가 NULL 이라 어느 수집기로도 잡히지 않는다 — 그 수집기는 처음 한 번 기본 조회 범위로 되돌아간다. 겹쳐 가져올 뿐
   * 잃지 않으므로 의도된 동작이다.
   *
   * @param seqMall 쇼핑몰 seq
   * @param collector 수집기 이름 ({@code MallRegistry.CollectorSpec.name})
   * @return {@code yyyyMMdd} 문자열. 그 수집기가 저장한 것이 하나도 없으면 null
   * @throws Exception 조회 실패
   */
  public String getLastCollectedDate(String seqMall, String collector) throws Exception {
    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();
      String query =
          "SELECT MAX(date_time) AS last_date FROM jbg_order WHERE seq_mall=? AND collector=?";
      log.debug(
          "LOCALDB-QUERY------------------------------------------------------------------------------");
      // 값은 로그에 싣지 않는다 — 구매일이 그대로 남는다.
      log.debug(query);

      ResultSet rset = conn.executeQuery(query, seqMall, collector);
      if (rset != null && rset.next()) {
        String last = rset.getString("last_date");
        // MAX 는 행이 없으면 NULL 을 준다. 0 은 date_time 기본값이라 '없음' 과 같이 다룬다.
        if (last != null && !last.isBlank() && !"0".equals(last.trim())) {
          return last.trim();
        }
      }
      return null;
    } finally {
      if (conn != null) {
        conn.close();
      }
    }
  }

  public int addWithConnection(
      LocalDBConnection conn,
      String serialNum,
      String dateTime,
      String mallName,
      String seqMall,
      String collector)
      throws Exception {

    int seqOrder = -1;

    // PreparedStatement 사용으로 SQL Injection 방지
    String query =
        "INSERT INTO jbg_order (serial_num, date_time, mall_name, seq_mall, collector)"
            + " VALUES (?, ?, ?, ?, ?)";

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
    conn.txPstmtExecuteUpdate(query, serialNum, dateTime, mallName, seqMall, collector);

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
   * <p><b>수집 경로가 매 회차 부르는 살아 있는 조회다.</b> 중복 방지 판정({@code MallOrderUpdaterRunner}, {@code
   * svc.mall.Hanaro})이 여기로 들어온다. 그리고 넘어오는 {@code serialNum} 은 <b>페이지 텍스트에서 합성한 영수증 번호</b>라 숫자 필터를
   * 거치지 않는다 — 이 자리는 "외부 입력 경로는 이미 다 막았다" 는 판단에서 빠져 있었고, 실제로는 값을 그대로 WHERE 절에 이어 붙이고 있었다. 따옴표 하나면
   * 조회가 깨지고, 깨진 조회는 중복 판정을 무너뜨려 같은 주문을 다시 쌓는다.
   *
   * <p>{@code dateTime} 은 문자열 그대로 바인딩한다. {@code date_time} 은 INTEGER 컬럼이라 SQLite 가 비교 시 숫자 친화도를 적용해
   * 예전 동작과 같은 결과를 낸다. 여기서 미리 파싱하지 <b>않는</b> 이유는 호출부가 정규화 전 원본 문자열을 넘기기 때문이다 — 파싱을 끼우면 중복 판정 기준이 조용히
   * 달라진다.
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
      // seq_user 컬럼이 schema에 없으므로 조건에서 제외
      String query = "SELECT seq, seq_mall FROM jbg_order WHERE serial_num=? AND date_time=?";
      log.debug(
          "LOCALDB-QUERY------------------------------------------------------------------------------");
      // 값은 로그에 싣지 않는다. 예전에는 조립된 쿼리를 찍어 영수증 번호와 구매일자가 그대로 로그에 남았다.
      log.debug(query);
      ResultSet rset = conn.executeQuery(query, serialNum, dateTime);

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

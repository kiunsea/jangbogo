package com.jiniebox.jangbogo.dao;

import com.jiniebox.jangbogo.util.ExceptionUtil;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;

/**
 * 아이템 정보 DAO
 *
 * <p>jbg_item 테이블에 접근하는 모든 기능 제공
 *
 * @author KIUNSEA
 */
public class JbgItemDataAccessObject extends CommonDataAccessObject {

  private static final Logger log = LogManager.getLogger(JbgItemDataAccessObject.class);

  /** seq · seq_order 는 둘 다 SQLite INTEGER 컬럼 값이다. 10진 숫자 외에는 존재할 수 없다. */
  private static final Pattern SEQ_DIGITS = Pattern.compile("[0-9]+");

  public JbgItemDataAccessObject() {
    // 기본 생성자
  }

  /**
   * seq 계열 문자열을 바인딩할 숫자로 바꾼다. 10진 숫자가 아니면 {@code null}.
   *
   * <p><b>숫자 필터로 깎지 않는다.</b> {@code replaceAll("[^0-9]", "")} 는 거르는 게 아니라 깎는 것이라, 숫자가 섞인 문자열을 남은
   * 숫자만 이어 붙인 <b>다른 유효한 seq</b> 로 바꿔 놓는다. 이 파일에는 {@code deleteByOrder} 가 있다 — 대상이 조용히 바뀌면 <b>남의 주문에
   * 딸린 아이템이 지워진다.</b> 못 알아보는 값은 깎지 말고 아무 것도 고르지 않아야 한다.
   */
  private static Long parseSeq(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    if (!SEQ_DIGITS.matcher(trimmed).matches()) {
      return null;
    }
    try {
      return Long.valueOf(trimmed);
    } catch (NumberFormatException tooManyDigits) {
      // long 범위를 넘은 자릿수. 존재할 수 없는 seq 라 '해당 없음' 과 같다.
      return null;
    }
  }

  /**
   * 열려 있던 트랜잭션을 되돌린다. <b>되돌리기가 실패해도 던지지 않는다.</b>
   *
   * <p>{@code delete}/{@code deleteByOrder} 가 {@code catch (SQLException)} 에서 롤백 없이 그대로 다시 던지고 있었다.
   * DELETE 가 깨질 때 실제로 타는 것이 그 쪽이라, 정작 되돌려야 할 경우에만 되돌리지 않고 있었다.
   *
   * <p>던지지 않는 이유: 롤백 실패 예외가 원래 실패 원인을 덮으면 "왜 삭제가 깨졌나" 를 잃는다. 진단 가치가 큰 쪽은 원인이다.
   */
  // rollbackQuietly 는 CommonDataAccessObject 로 올렸다 (JbgMallDataAccessObject 와 복제였다).

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

      StringBuffer querySb = new StringBuffer("SELECT seq, name, seq_order, qty, insert_time");
      querySb.append(" FROM jbg_item");
      querySb.append(" ORDER BY seq DESC");
      log.debug(
          "LOCALDB-QUERY------------------------------------------------------------------------------");
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
   * @param seq 아이템 시퀀스. 숫자가 아니면 없는 아이템과 같게 본다
   * @return 아이템 전체 정보 (모든 필드). 없으면 null
   * @throws Exception
   */
  public JSONObject getItem(String seq) throws Exception {
    Long seqNo = parseSeq(seq);
    if (seqNo == null) {
      return null;
    }
    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();

      StringBuffer querySb = new StringBuffer("SELECT seq, name, seq_order, qty, insert_time");
      querySb.append(" FROM jbg_item");
      querySb.append(" WHERE seq=?");
      log.debug(
          "LOCALDB-QUERY------------------------------------------------------------------------------");
      log.debug(querySb);
      ResultSet rset = conn.executeQuery(querySb.toString(), seqNo);

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
   * @param seqOrder 주문 시퀀스. 숫자가 아니면 딸린 아이템이 없는 것과 같게 본다
   * @return 아이템 목록. 없으면 빈 목록
   * @throws Exception
   */
  public List<JSONObject> getItemsByOrder(String seqOrder) throws Exception {
    Long orderNo = parseSeq(seqOrder);
    if (orderNo == null) {
      // 내보내기(ExportService)가 주문마다 부른다. null 대신 빈 목록을 주는 이유는, 행이 없을 때
      // 이미 빈 목록이 나오기 때문이다 — 두 경우의 반환 형태를 다르게 두면 호출부가 한쪽만 방어한다.
      return new ArrayList<JSONObject>();
    }
    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();

      StringBuffer querySb = new StringBuffer("SELECT seq, name, seq_order, qty, insert_time");
      querySb.append(" FROM jbg_item");
      querySb.append(" WHERE seq_order=?");
      querySb.append(" ORDER BY seq DESC");
      log.debug(
          "LOCALDB-QUERY------------------------------------------------------------------------------");
      log.debug(querySb);
      ResultSet rset = conn.executeQuery(querySb.toString(), orderNo);

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

      conn.txOpen();

      // 값을 문자열로 이어 붙이지 않는다 (B-3). 상품명은 수집한 웹페이지에서 온 값이라 우리가
      // 통제하지 못한다 — 따옴표 하나로 INSERT 가 깨지고 그 자리가 곧 주입 지점이다.
      //
      // 구현은 addWithConnection 하나로 모은다. 같은 INSERT 를 두 벌 두면 한쪽만 고쳐지는데,
      // 실제로 그랬다 — 수집기가 쓰는 addWithConnection 은 진작 PreparedStatement 였고
      // 이 오버로드만 문자열 조립으로 남아 있었다.
      int seq = this.addWithConnection(conn, name, seqOrder, qty);
      conn.txCommit();

      return seq;
    } catch (SQLException e) {
      log.error("* 아이고!! ㅜ.ㅜ 데이터베이스 업데이트 에러 발생");
      log.error(ExceptionUtil.getExceptionInfo(e));
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
  }

  /**
   * 아이템 등록 (기존 Connection 사용, PreparedStatement로 SQL Injection 방지)
   *
   * <p>트랜잭션 관리는 호출자가 담당합니다. 이 메서드는 기존 트랜잭션 내에서 실행됩니다.
   *
   * @param conn 기존 LocalDBConnection (트랜잭션이 이미 시작된 상태)
   * @param name 아이템명 (필수)
   * @param seqOrder 주문 시퀀스 (옵션, null 가능)
   * @param qty 수량 (옵션, null 가능)
   * @return 생성된 아이템 시퀀스
   * @throws Exception
   */
  public int addWithConnection(LocalDBConnection conn, String name, String seqOrder, String qty)
      throws Exception {

    // PreparedStatement 사용으로 SQL Injection 방지
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
    querySb.append(") values (?");

    // 파라미터 목록 생성
    java.util.List<Object> params = new java.util.ArrayList<>();
    params.add(name);

    if (seqOrder != null && !seqOrder.isEmpty()) {
      querySb.append(", ?");
      params.add(Integer.parseInt(seqOrder));
    }
    if (qty != null && !qty.isEmpty()) {
      querySb.append(", ?");
      params.add(qty);
    }
    querySb.append(", ?");
    params.add(System.currentTimeMillis());
    querySb.append(")");

    String query = querySb.toString();
    log.debug(
        "LOCALDB-QUERY------------------------------------------------------------------------------");
    log.debug("{} [name={}, seqOrder={}, qty={}]", query, name, seqOrder, qty);

    // PreparedStatement로 실행
    conn.txPstmtExecuteUpdate(query, params.toArray());

    // 생성된 seq 조회
    int seq = getLastInsertSeq(conn);

    return seq;
  }

  // 여기 있던 ensureQtyColumn 은 제거했다 (Phase 3-10).
  //
  // qty 컬럼 보정을 DAO 의 다섯 경로가 각자 호출하고 있었다. 선언은 schema.sql 로 옮겼고
  // (그전에는 schema.sql 에 없이 런타임 ALTER 에만 있었다), 보정은 SchemaMigrator 가 한다.

  // 여기 있던 update(seq, name, seqOrder) 는 제거했다.
  //
  // 아이템명을 SET 절에 이어 붙이던(name='...') 마지막 조립 지점이었는데, 고칠 대상이 아니라
  // 지울 대상이었다 — 호출부가 MallOrderUpdater 의 주석 처리된 updateItems 하나뿐이었고 그마저도
  // 이 프로젝트에 없는 클래스(ItemDataAccessObject)를 부르고 있었다. 살아 있는 수집 경로는
  // addWithConnection 만 쓴다.
  //
  // 죽은 코드를 PreparedStatement 로 고쳐 두면 "여기도 안전하다" 는 인상만 남기고 실제로는
  // 아무 것도 지키지 않는다. 되살릴 일이 생기면 그때 바인딩으로 새로 쓴다.

  // ========== 아이템 삭제 메서드 ==========

  /**
   * 아이템 삭제
   *
   * @param seq 아이템 시퀀스
   * @return 삭제 성공 여부. 숫자가 아닌 seq 는 아무 것도 지우지 않고 false
   * @throws Exception
   */
  public boolean delete(String seq) throws Exception {
    Long seqNo = parseSeq(seq);
    if (seqNo == null) {
      // 예외로 올리지 않고 값으로 돌린다. 삭제 실패를 예외로 던지면 위쪽 포괄 catch 가 이를
      // 다른 사고로 오인해 계정 연결을 끊은 전례가 있다. "아무 것도 안 지웠다" 가 정확한 사실이다.
      log.warn("삭제 요청의 아이템 seq 가 숫자가 아니다 — 아무 것도 지우지 않는다.");
      return false;
    }
    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();
      conn.txOpen();

      String query = "DELETE FROM jbg_item WHERE seq=?";
      log.debug(
          "LOCALDB-QUERY------------------------------------------------------------------------------");
      log.debug(query);
      conn.txPstmtExecuteUpdate(query, seqNo);
      conn.txCommit();

      return true;
    } catch (SQLException e) {
      log.error("* 아이고!! ㅜ.ㅜ 데이터베이스 업데이트 에러 발생");
      log.error(ExceptionUtil.getExceptionInfo(e));
      rollbackQuietly(conn);
      throw e;
    } catch (Exception e) {
      log.error("* 아이고!! ㅜ.ㅜ 데이터베이스 업데이트 에러 발생");
      log.error(ExceptionUtil.getExceptionInfo(e));
      rollbackQuietly(conn);
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
   * @return 삭제 성공 여부. 숫자가 아닌 seqOrder 는 아무 것도 지우지 않고 false
   * @throws Exception
   */
  public boolean deleteByOrder(String seqOrder) throws Exception {
    Long orderNo = parseSeq(seqOrder);
    if (orderNo == null) {
      // 이 파일에서 가장 위험한 자리다. WHERE 절이 깨지면 지우려던 한 주문이 아니라 테이블 전체가
      // 대상이 될 수 있다. 못 알아보는 값은 숫자만 골라 뽑지 말고 아예 문장을 만들지 않는다.
      log.warn("삭제 요청의 주문 seq 가 숫자가 아니다 — 아무 것도 지우지 않는다.");
      return false;
    }
    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();
      conn.txOpen();

      String query = "DELETE FROM jbg_item WHERE seq_order=?";
      log.debug(
          "LOCALDB-QUERY------------------------------------------------------------------------------");
      log.debug(query);
      conn.txPstmtExecuteUpdate(query, orderNo);
      conn.txCommit();

      return true;
    } catch (SQLException e) {
      log.error("* 아이고!! ㅜ.ㅜ 데이터베이스 업데이트 에러 발생");
      log.error(ExceptionUtil.getExceptionInfo(e));
      rollbackQuietly(conn);
      throw e;
    } catch (Exception e) {
      log.error("* 아이고!! ㅜ.ㅜ 데이터베이스 업데이트 에러 발생");
      log.error(ExceptionUtil.getExceptionInfo(e));
      rollbackQuietly(conn);
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

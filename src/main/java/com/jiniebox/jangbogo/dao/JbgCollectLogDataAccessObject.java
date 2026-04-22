package com.jiniebox.jangbogo.dao;

import java.sql.ResultSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * 수집 실행 로그 데이터 접근 객체
 *
 * @author KIUNSEA
 */
public class JbgCollectLogDataAccessObject extends CommonDataAccessObject {

  private static final Logger logger = LogManager.getLogger(JbgCollectLogDataAccessObject.class);

  /** 기본 시그니처 (하위호환). 신규 컬럼은 모두 null로 저장된다. */
  public void addLog(
      int seqMall,
      String mallName,
      String status,
      int orderCount,
      int itemCount,
      String errorMessage,
      String errorDetail,
      long startedAt,
      long finishedAt) {
    addLog(
        seqMall,
        mallName,
        status,
        orderCount,
        itemCount,
        errorMessage,
        errorDetail,
        null,
        null,
        null,
        null,
        null,
        startedAt,
        finishedAt);
  }

  /**
   * 수집 실행 로그 저장 (확장 시그니처 — v0.8.0 컨텍스트 컬럼 포함)
   *
   * @param seqMall 쇼핑몰 seq
   * @param mallName 쇼핑몰 이름
   * @param status SUCCESS / FAIL
   * @param orderCount 수집된 주문 수
   * @param itemCount 수집된 아이템 수
   * @param errorMessage 오류 메시지
   * @param errorDetail 상세 오류 (스택트레이스)
   * @param stepName 실패한 단계명
   * @param currentUrl 실패 시점 현재 URL
   * @param pageTitle 페이지 타이틀
   * @param targetSelector 타겟 셀렉터
   * @param screenshotPath 스크린샷 파일 경로
   * @param startedAt 실행 시작 시간 (ms)
   * @param finishedAt 실행 종료 시간 (ms)
   */
  public void addLog(
      int seqMall,
      String mallName,
      String status,
      int orderCount,
      int itemCount,
      String errorMessage,
      String errorDetail,
      String stepName,
      String currentUrl,
      String pageTitle,
      String targetSelector,
      String screenshotPath,
      long startedAt,
      long finishedAt) {

    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();
      String query =
          "INSERT INTO jbg_collect_log "
              + "(seq_mall, mall_name, status, order_count, item_count, "
              + "error_message, error_detail, step_name, current_url, page_title, "
              + "target_selector, screenshot_path, started_at, finished_at, insert_time) "
              + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

      conn.txPstmtExecuteUpdate(
          query,
          seqMall,
          mallName,
          status,
          orderCount,
          itemCount,
          errorMessage,
          errorDetail,
          stepName,
          currentUrl,
          pageTitle,
          targetSelector,
          screenshotPath,
          startedAt,
          finishedAt,
          System.currentTimeMillis());

      logger.debug(
          "수집 로그 저장 완료 - mall: {} (seq={}), status: {}, step: {}, orders: {}, items: {}",
          mallName,
          seqMall,
          status,
          stepName,
          orderCount,
          itemCount);
    } catch (Exception e) {
      logger.error("수집 로그 저장 실패: {}", e.getMessage(), e);
    } finally {
      if (conn != null) {
        try {
          conn.close();
        } catch (Exception e) {
          logger.warn("Connection 종료 중 오류: {}", e.getMessage());
        }
      }
    }
  }

  /** 단일 로그 조회 (스크린샷 서빙 등 상세 조회용) */
  public JSONObject getLog(int seq) {
    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();
      String query = SELECT_COLUMNS + " FROM jbg_collect_log WHERE seq = " + seq;
      ResultSet rset = conn.executeQuery(query);
      if (rset != null && rset.next()) {
        return mapRow(rset);
      }
    } catch (Exception e) {
      logger.error("로그 조회 실패: {}", e.getMessage(), e);
    } finally {
      if (conn != null) {
        try {
          conn.close();
        } catch (Exception e) {
          logger.warn("Connection 종료 중 오류: {}", e.getMessage());
        }
      }
    }
    return null;
  }

  /** 전체 로그 조회 (최신순) */
  @SuppressWarnings("unchecked")
  public JSONArray getAllLogs(int limit) {
    JSONArray result = new JSONArray();
    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();
      String query = SELECT_COLUMNS + " FROM jbg_collect_log ORDER BY seq DESC LIMIT " + limit;

      ResultSet rset = conn.executeQuery(query);
      while (rset != null && rset.next()) {
        result.add(mapRow(rset));
      }
    } catch (Exception e) {
      logger.error("전체 로그 조회 실패: {}", e.getMessage(), e);
    } finally {
      if (conn != null) {
        try {
          conn.close();
        } catch (Exception e) {
          logger.warn("Connection 종료 중 오류: {}", e.getMessage());
        }
      }
    }
    return result;
  }

  /** 실패 로그만 조회 (최신순) */
  @SuppressWarnings("unchecked")
  public JSONArray getFailLogs(int limit) {
    JSONArray result = new JSONArray();
    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();
      String query =
          SELECT_COLUMNS
              + " FROM jbg_collect_log WHERE status = 'FAIL' ORDER BY seq DESC LIMIT "
              + limit;

      ResultSet rset = conn.executeQuery(query);
      while (rset != null && rset.next()) {
        result.add(mapRow(rset));
      }
    } catch (Exception e) {
      logger.error("실패 로그 조회 실패: {}", e.getMessage(), e);
    } finally {
      if (conn != null) {
        try {
          conn.close();
        } catch (Exception e) {
          logger.warn("Connection 종료 중 오류: {}", e.getMessage());
        }
      }
    }
    return result;
  }

  /** 요약 통계 조회 (전체 실행 횟수, 성공 건수, 실패 건수) */
  @SuppressWarnings("unchecked")
  public JSONObject getSummary() {
    JSONObject result = new JSONObject();
    result.put("total", 0);
    result.put("success", 0);
    result.put("fail", 0);

    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();
      String query =
          "SELECT "
              + "COUNT(*) as total, "
              + "SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) as success, "
              + "SUM(CASE WHEN status = 'FAIL' THEN 1 ELSE 0 END) as fail "
              + "FROM jbg_collect_log";

      ResultSet rset = conn.executeQuery(query);
      if (rset != null && rset.next()) {
        result.put("total", rset.getInt("total"));
        result.put("success", rset.getInt("success"));
        result.put("fail", rset.getInt("fail"));
      }
    } catch (Exception e) {
      logger.error("요약 통계 조회 실패: {}", e.getMessage(), e);
    } finally {
      if (conn != null) {
        try {
          conn.close();
        } catch (Exception e) {
          logger.warn("Connection 종료 중 오류: {}", e.getMessage());
        }
      }
    }
    return result;
  }

  private static final String SELECT_COLUMNS =
      "SELECT seq, seq_mall, mall_name, status, order_count, item_count, "
          + "error_message, error_detail, step_name, current_url, page_title, "
          + "target_selector, screenshot_path, started_at, finished_at, insert_time";

  /** ResultSet 한 행을 JSONObject로 매핑 (신규 컬럼이 없는 환경에서도 안전하게 동작) */
  @SuppressWarnings("unchecked")
  private JSONObject mapRow(ResultSet rset) throws Exception {
    JSONObject row = new JSONObject();
    row.put("seq", rset.getInt("seq"));
    row.put("seq_mall", rset.getInt("seq_mall"));
    row.put("mall_name", rset.getString("mall_name"));
    row.put("status", rset.getString("status"));
    row.put("order_count", rset.getInt("order_count"));
    row.put("item_count", rset.getInt("item_count"));
    row.put("error_message", rset.getString("error_message"));
    row.put("error_detail", rset.getString("error_detail"));
    row.put("step_name", safeGetString(rset, "step_name"));
    row.put("current_url", safeGetString(rset, "current_url"));
    row.put("page_title", safeGetString(rset, "page_title"));
    row.put("target_selector", safeGetString(rset, "target_selector"));
    row.put("screenshot_path", safeGetString(rset, "screenshot_path"));
    row.put("started_at", rset.getLong("started_at"));
    row.put("finished_at", rset.getLong("finished_at"));
    row.put("insert_time", rset.getLong("insert_time"));
    return row;
  }

  private String safeGetString(ResultSet rset, String column) {
    try {
      return rset.getString(column);
    } catch (Exception e) {
      return null;
    }
  }
}

package com.jiniebox.jangbogo.dao;

import com.jiniebox.jangbogo.svc.util.CollectBreakerPolicy;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;

/**
 * 수집기 서킷 브레이커 상태 저장소 (Phase 3-3).
 *
 * <p>상태를 메모리가 아니라 테이블에 두는 이유는 하나다 — <b>트립은 재시작을 넘어 살아남아야 한다.</b> 메모리에만 있으면 재기동이 차단된 수집기를 되살려 다시
 * 사이트를 두드린다. 자동 차단의 목적이 그 순간 사라진다.
 *
 * <p>판정 자체는 하지 않는다. 규칙은 {@link CollectBreakerPolicy} 가 순수 함수로 갖고 있고, 이 클래스는 읽고 쓰기만 한다.
 *
 * @author KIUNSEA
 */
public class JbgCollectBreakerDataAccessObject extends CommonDataAccessObject {

  private static final Logger log = LogManager.getLogger(JbgCollectBreakerDataAccessObject.class);

  /**
   * 한 수집기의 상태를 읽는다.
   *
   * @param seqMall 쇼핑몰 seq
   * @param collector 수집기 이름
   * @return 저장된 상태. 행이 없거나 조회에 실패하면 {@link CollectBreakerPolicy.State#healthy()}
   */
  public CollectBreakerPolicy.State getState(int seqMall, String collector) {
    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();
      ResultSet rset =
          conn.executeQuery(
              "SELECT consecutive_failures, streak_started_time, last_failure_time, tripped_time"
                  + " FROM jbg_collect_breaker"
                  + " WHERE seq_mall = "
                  + seqMall
                  + " AND collector = '"
                  + escape(collector)
                  + "'");
      if (rset != null && rset.next()) {
        return new CollectBreakerPolicy.State(
            rset.getInt("consecutive_failures"),
            rset.getLong("streak_started_time"),
            rset.getLong("last_failure_time"),
            rset.getLong("tripped_time"));
      }
    } catch (Exception e) {
      // 상태를 못 읽었다고 수집을 막지 않는다. 정상으로 보고 진행한다.
      log.warn("브레이커 상태 조회 실패 (seq={}, collector={}): {}", seqMall, collector, e.getMessage());
    } finally {
      close(conn);
    }
    return CollectBreakerPolicy.State.healthy();
  }

  /**
   * 한 수집기의 상태를 저장한다 (없으면 삽입, 있으면 갱신).
   *
   * @param seqMall 쇼핑몰 seq
   * @param collector 수집기 이름
   * @param state 저장할 상태
   * @param lastSuccessTime 마지막 성공 시각. {@code 0} 이면 기존 값을 유지한다
   * @param reason 사람이 읽을 사유
   */
  public void saveState(
      int seqMall,
      String collector,
      CollectBreakerPolicy.State state,
      long lastSuccessTime,
      String reason) {
    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();
      conn.txOpen();
      conn.txPstmtExecuteUpdate(
          "INSERT INTO jbg_collect_breaker"
              + " (seq_mall, collector, consecutive_failures, streak_started_time,"
              + "  last_failure_time, last_success_time, tripped_time, last_reason)"
              + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
              + " ON CONFLICT(seq_mall, collector) DO UPDATE SET"
              + "  consecutive_failures = excluded.consecutive_failures,"
              + "  streak_started_time = excluded.streak_started_time,"
              + "  last_failure_time = excluded.last_failure_time,"
              // 성공 시각은 0 을 넘기면 기존 값을 지우지 않고 유지한다.
              + "  last_success_time = CASE WHEN excluded.last_success_time > 0"
              + "                           THEN excluded.last_success_time"
              + "                           ELSE jbg_collect_breaker.last_success_time END,"
              + "  tripped_time = excluded.tripped_time,"
              + "  last_reason = excluded.last_reason",
          seqMall,
          collector,
          state.consecutiveFailures,
          state.streakStartedTime,
          state.lastFailureTime,
          lastSuccessTime,
          state.trippedTime,
          reason);
      conn.txCommit();
    } catch (Exception e) {
      rollback(conn);
      log.warn("브레이커 상태 저장 실패 (seq={}, collector={}): {}", seqMall, collector, e.getMessage());
    } finally {
      close(conn);
    }
  }

  /**
   * 현재 트립된 수집기 목록을 반환한다. 대시보드 경보용.
   *
   * @return {@code seq_mall}, {@code collector}, {@code consecutive_failures}, {@code
   *     tripped_time}, {@code last_reason} 을 담은 JSON 목록 (없으면 빈 목록)
   */
  @SuppressWarnings("unchecked")
  public List<JSONObject> getTripped() {
    List<JSONObject> result = new ArrayList<>();
    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();
      ResultSet rset =
          conn.executeQuery(
              "SELECT seq_mall, collector, consecutive_failures, tripped_time, last_reason"
                  + " FROM jbg_collect_breaker WHERE tripped_time > 0 ORDER BY tripped_time DESC");
      while (rset != null && rset.next()) {
        JSONObject row = new JSONObject();
        row.put("seq_mall", rset.getInt("seq_mall"));
        row.put("collector", rset.getString("collector"));
        row.put("consecutive_failures", rset.getInt("consecutive_failures"));
        row.put("tripped_time", rset.getLong("tripped_time"));
        row.put("last_reason", rset.getString("last_reason"));
        result.add(row);
      }
    } catch (Exception e) {
      log.warn("트립된 수집기 조회 실패: {}", e.getMessage());
    } finally {
      close(conn);
    }
    return result;
  }

  /** 작은따옴표만 막으면 되는 자리다. 값은 코드가 정한 수집기 이름이라 외부 입력이 아니다. */
  private static String escape(String value) {
    return value == null ? "" : value.replace("'", "''");
  }

  private static void close(LocalDBConnection conn) {
    if (conn != null) {
      try {
        conn.close();
      } catch (Exception ignore) {
      }
    }
  }

  private static void rollback(LocalDBConnection conn) {
    if (conn != null) {
      try {
        conn.txRollBack();
      } catch (Exception ignore) {
      }
    }
  }
}

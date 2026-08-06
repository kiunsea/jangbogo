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
      // 직접 이스케이프는 바인딩의 대체가 아니다. 지금 collector 가 MallRegistry 의 내부 상수라
      // 외부 입력이 아닌 것은 맞지만, 그 사실은 호출부가 하나 늘어나는 순간 조용히 깨진다.
      // 안전한지 여부를 값의 출처로 논증해야 하는 코드는 언젠가 틀린다.
      ResultSet rset =
          conn.executeQuery(
              "SELECT consecutive_failures, streak_started_time, last_failure_time, tripped_time"
                  + " FROM jbg_collect_breaker"
                  + " WHERE seq_mall = ? AND collector = ?",
              seqMall,
              collector);
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
   * @param lastNonEmptyTime 마지막으로 실제 데이터를 받은 시각. {@code 0} 이면 기존 값을 유지한다 — 0건 수집이 이 시각을 밀어 버리면 조용한
   *     실패를 알아챌 신호가 사라진다 (Phase 3-4)
   * @param reason 사람이 읽을 사유
   */
  public void saveState(
      int seqMall,
      String collector,
      CollectBreakerPolicy.State state,
      long lastSuccessTime,
      long lastNonEmptyTime,
      String reason) {
    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();
      conn.txOpen();
      conn.txPstmtExecuteUpdate(
          "INSERT INTO jbg_collect_breaker"
              + " (seq_mall, collector, consecutive_failures, streak_started_time,"
              + "  last_failure_time, last_success_time, last_nonempty_time, tripped_time,"
              + "  last_reason)"
              + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
              + " ON CONFLICT(seq_mall, collector) DO UPDATE SET"
              + "  consecutive_failures = excluded.consecutive_failures,"
              + "  streak_started_time = excluded.streak_started_time,"
              + "  last_failure_time = excluded.last_failure_time,"
              // 시각 두 개는 0 을 넘기면 기존 값을 지우지 않고 유지한다.
              + "  last_success_time = CASE WHEN excluded.last_success_time > 0"
              + "                           THEN excluded.last_success_time"
              + "                           ELSE jbg_collect_breaker.last_success_time END,"
              + "  last_nonempty_time = CASE WHEN excluded.last_nonempty_time > 0"
              + "                            THEN excluded.last_nonempty_time"
              + "                            ELSE jbg_collect_breaker.last_nonempty_time END,"
              + "  tripped_time = excluded.tripped_time,"
              + "  last_reason = excluded.last_reason",
          seqMall,
          collector,
          state.consecutiveFailures,
          state.streakStartedTime,
          state.lastFailureTime,
          lastSuccessTime,
          lastNonEmptyTime,
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

  /**
   * 모든 수집기의 하트비트 행을 반환한다 (Phase 3-5).
   *
   * <p>몰의 수집 주기를 함께 붙여 준다 — 건강도 판정이 "마지막 성공 이후 주기의 N배"라 주기 없이는 판정할 수 없다.
   *
   * @return {@code seq_mall}, {@code mall_name}, {@code collector}, {@code
   *     collect_interval_minutes}, {@code last_success_time}, {@code last_nonempty_time}, {@code
   *     consecutive_failures}, {@code tripped_time}, {@code last_reason} 을 담은 JSON 목록
   */
  @SuppressWarnings("unchecked")
  public List<JSONObject> getHeartbeats() {
    List<JSONObject> result = new ArrayList<>();
    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();
      ResultSet rset =
          conn.executeQuery(
              "SELECT b.seq_mall, m.name AS mall_name, b.collector,"
                  + " COALESCE(m.collect_interval_minutes, 0) AS collect_interval_minutes,"
                  + " b.last_success_time, b.last_nonempty_time, b.consecutive_failures,"
                  + " b.tripped_time, b.last_reason"
                  + " FROM jbg_collect_breaker b"
                  + " LEFT JOIN jbg_mall m ON m.seq = b.seq_mall"
                  + " ORDER BY b.seq_mall, b.collector");
      while (rset != null && rset.next()) {
        JSONObject row = new JSONObject();
        row.put("seq_mall", rset.getInt("seq_mall"));
        row.put("mall_name", rset.getString("mall_name"));
        row.put("collector", rset.getString("collector"));
        row.put("collect_interval_minutes", rset.getInt("collect_interval_minutes"));
        row.put("last_success_time", rset.getLong("last_success_time"));
        row.put("last_nonempty_time", rset.getLong("last_nonempty_time"));
        row.put("consecutive_failures", rset.getInt("consecutive_failures"));
        row.put("tripped_time", rset.getLong("tripped_time"));
        row.put("last_reason", rset.getString("last_reason"));
        result.add(row);
      }
    } catch (Exception e) {
      log.warn("하트비트 조회 실패: {}", e.getMessage());
    } finally {
      close(conn);
    }
    return result;
  }

  // escape(String) 은 지웠다. 유일한 호출부였던 getState 가 바인딩으로 바뀌었고, 이스케이프 헬퍼를
  // 남겨 두면 다음에 조회를 추가하는 사람이 바인딩 대신 그것을 집는다.

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

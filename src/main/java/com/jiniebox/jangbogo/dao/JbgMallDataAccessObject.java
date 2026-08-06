package com.jiniebox.jangbogo.dao;

import com.jiniebox.jangbogo.svc.util.CollectIntervalPolicy;
import com.jiniebox.jangbogo.util.ExceptionUtil;
import com.jiniebox.jangbogo.util.JinieboxUtil;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;

/**
 * 쇼핑몰 정보 DAO
 *
 * @author KIUNSEA
 */
public class JbgMallDataAccessObject extends CommonDataAccessObject {

  /**
   * 세션 프로필 컬럼 (Phase 5). SELECT 목록에 붙여 쓴다.
   *
   * <p>한 곳에 모아 둔 이유가 있다 — 컬럼을 {@code schema.sql} 에 선언하고 게이트 코드까지 다 만들어 놓고도 <b>DAO 의 SELECT 목록에 넣지
   * 않아 게이트가 통째로 죽어 있었다.</b> 값이 항상 null 이라 옵트인이 영원히 false 였고, 실측에서야 드러났다. 조회 지점이 여러 곳이므로 목록을 흩어 두면
   * 같은 일이 또 생긴다.
   */
  private static final String SESSION_PROFILE_COLUMNS =
      ", session_profile_enabled, session_profile_name, session_profile_status,"
          + " session_profile_last_login, session_profile_owner";

  /**
   * {@code session_profile_status} 에 이 프로그램이 쓰는 <b>유일한</b> 값.
   *
   * <p>스키마 주석은 {@code NONE / READY / EXPIRED / LOCKED} 를 열거하지만, 쓰기는 READY 하나로 좁힌다 — 이유는 {@link
   * #saveSessionProfileIdentity} javadoc 참조.
   */
  public static final String SESSION_PROFILE_STATUS_READY = "READY";

  /** 조회 결과의 세션 프로필 컬럼을 JSON 에 옮긴다. */
  private void putSessionProfileFields(java.sql.ResultSet rset, org.json.simple.JSONObject mJson)
      throws java.sql.SQLException {
    mJson.put("session_profile_enabled", safeGetInt(rset, "session_profile_enabled", 0));
    mJson.put("session_profile_name", rset.getString("session_profile_name"));
    mJson.put("session_profile_status", rset.getString("session_profile_status"));
    mJson.put("session_profile_last_login", rset.getLong("session_profile_last_login"));
    mJson.put("session_profile_owner", rset.getString("session_profile_owner"));
  }

  private static final Logger log = LogManager.getLogger(JbgMallDataAccessObject.class);

  public JbgMallDataAccessObject() {
    // 기본 생성자
  }

  // ========== 쇼핑몰 조회 메서드 ==========

  /**
   * 쇼핑몰 전체 목록 조회 (모든 필드 포함)
   *
   * @param addEncField 암호화 정보 추가 여부
   * @return 쇼핑몰 목록 (모든 필드)
   * @throws Exception
   */
  public List<JSONObject> getAllMalls(boolean addEncField) throws Exception {
    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();
      StringBuffer querySb = new StringBuffer("SELECT seq, id, name, details");
      if (addEncField) {
        querySb.append(", encrypt_key, encrypt_iv");
      }
      querySb.append(", account_status, last_signin_time, auto_collect, collect_interval_minutes");
      querySb.append(SESSION_PROFILE_COLUMNS);
      querySb.append(" FROM jbg_mall");
      log.debug(
          "LOCALDB-QUERY------------------------------------------------------------------------------");
      log.debug(querySb);
      ResultSet rset = conn.executeQuery(querySb.toString());

      List<JSONObject> malls = null;
      if (rset != null) {
        malls = new ArrayList<JSONObject>();
        JSONObject mJson = null;
        while (rset.next()) {
          mJson = new JSONObject();
          mJson.put("seq", rset.getInt("seq"));
          mJson.put("id", rset.getString("id"));
          mJson.put("name", rset.getString("name"));
          mJson.put("details", rset.getString("details"));
          if (addEncField) {
            mJson.put("encrypt_key", rset.getString("encrypt_key"));
            mJson.put("encrypt_iv", rset.getString("encrypt_iv"));
          }
          mJson.put("account_status", rset.getInt("account_status"));
          mJson.put("last_signin_time", rset.getLong("last_signin_time"));
          mJson.put("auto_collect", safeGetInt(rset, "auto_collect", 0));
          mJson.put("collect_interval_minutes", safeGetInt(rset, "collect_interval_minutes", 0));
          putSessionProfileFields(rset, mJson);
          malls.add(mJson);
        }
        return malls;
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
   * 특정 쇼핑몰의 전체 정보 조회 (모든 필드)
   *
   * @param seq 쇼핑몰 시퀀스. 숫자가 아니면 없는 몰과 같게 본다
   * @return 쇼핑몰 전체 정보 (모든 필드). 없으면 null
   * @throws Exception
   */
  public JSONObject getMall(String seq) throws Exception {
    // 여기가 확인된 주입 도달 경로였다. AdminController 의 @RequestParam("seq") 가 필터 하나 없이
    // 이 자리까지 그대로 온다. 로그인 세션 뒤라는 사실은 완화지 방어가 아니다.
    Long seqNo = readSeq(seq);
    if (seqNo == null) {
      return null;
    }
    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();
      StringBuffer querySb = new StringBuffer("SELECT seq, id, name, details, ");
      querySb.append(
          "encrypt_key, encrypt_iv, account_status, last_signin_time, auto_collect, collect_interval_minutes");
      querySb.append(SESSION_PROFILE_COLUMNS);
      querySb.append(" FROM jbg_mall");
      querySb.append(" WHERE seq=?");
      log.debug(
          "LOCALDB-QUERY------------------------------------------------------------------------------");
      log.debug(querySb);
      ResultSet rset = conn.executeQuery(querySb.toString(), seqNo);

      if (rset != null) {
        JSONObject mJson = null;
        if (rset.next()) {
          mJson = new JSONObject();
          mJson.put("seq", rset.getInt("seq"));
          mJson.put("id", rset.getString("id"));
          mJson.put("name", rset.getString("name"));
          mJson.put("details", rset.getString("details"));
          mJson.put("encrypt_key", rset.getString("encrypt_key"));
          mJson.put("encrypt_iv", rset.getString("encrypt_iv"));
          mJson.put("account_status", rset.getInt("account_status"));
          mJson.put("last_signin_time", rset.getLong("last_signin_time"));
          mJson.put("auto_collect", safeGetInt(rset, "auto_collect", 0));
          mJson.put("collect_interval_minutes", safeGetInt(rset, "collect_interval_minutes", 0));
          putSessionProfileFields(rset, mJson);
        }
        return mJson;
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

  // ========== Auto-collect flag helpers ==========

  // 여기 있던 ensureAutoCollectColumns 는 제거했다 (Phase 3-10).
  //
  // auto_collect / collect_interval_minutes 컬럼 보정을 DAO 의 네 경로(getAllMalls, getAccessInfo,
  // saveAutoCollectFlags, updateCollectInterval)가 각자 호출하고 있었다. 즉 조회 한 번마다
  // "SELECT col 을 던져 보고 예외를 잡는" 탐지가 두 번씩 돌았다.
  //
  // 이제 스키마 진화는 SchemaMigrator 한 곳이 소유한다. 선언은 schema.sql 에만 두고
  // (auto_collect 는 이 통합에서 schema.sql 에 추가했다 — 그전에는 런타임 ALTER 에만 있어
  // 신규 설치와 기존 설치가 서로 다른 경로로 같은 스키마에 도달하고 있었다),
  // 마이그레이터가 PRAGMA table_info 로 대조해 없는 것만 채운다.
  //
  // 보정 시점은 CommonDataAccessObject 생성자다 — 모든 DAO 가 상속하므로 이 클래스가
  // 직접 부를 필요가 없고, JVM 당 1회 보장이라 매 호출 비용도 사라진다.

  private int safeGetInt(ResultSet rset, String col, int defVal) {
    try {
      return rset.getInt(col);
    } catch (Exception e) {
      return defVal;
    }
  }

  /**
   * 선택된 seq들만 auto_collect=1로 저장하고 나머지는 0으로 초기화 주기 시간도 함께 저장
   *
   * @param selectedSeqs 체크된 쇼핑몰 seq 목록
   * @param intervals seq를 키로 하고 주기(분)를 값으로 하는 맵 (null 가능)
   */
  public void saveAutoCollectFlags(
      List<String> selectedSeqs, java.util.Map<String, Integer> intervals) throws Exception {
    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();
      conn.txOpen();

      // 모든 쇼핑몰의 auto_collect를 0으로 초기화
      conn.txExecuteUpdate("UPDATE jbg_mall SET auto_collect=0");

      // 선택된 쇼핑몰은 auto_collect=1로 설정
      //
      // IN 목록의 길이가 입력에 따라 변하므로 자리표시자만 그 수만큼 만들고 값은 전부 바인딩한다.
      // 숫자 필터로 깎아 이어 붙이던 예전 방식은 "1 OR ..." 같은 값을 "1..." 로 바꿔 엉뚱한 몰의
      // 자동수집을 켜 놓을 수 있었다 — 화면에서 체크하지도 않은 몰이 돌기 시작한다.
      if (selectedSeqs != null && !selectedSeqs.isEmpty()) {
        List<Object> seqNos = new ArrayList<>();
        for (String selected : selectedSeqs) {
          Long seqNo = readSeq(selected);
          if (seqNo == null) {
            // 한 항목이 이상하다고 전체를 되돌리지 않는다. 되돌리면 위에서 이미 0 으로 초기화한
            // 상태가 살아나는 게 아니라, 사용자가 방금 켠 나머지 몰들까지 통째로 취소된다.
            log.warn("자동수집 대상에 숫자가 아닌 seq 가 섞여 있어 건너뛴다.");
            continue;
          }
          seqNos.add(seqNo);
        }
        if (!seqNos.isEmpty()) {
          String placeholders = String.join(",", Collections.nCopies(seqNos.size(), "?"));
          conn.txPstmtExecuteUpdate(
              "UPDATE jbg_mall SET auto_collect=1 WHERE seq IN (" + placeholders + ")",
              seqNos.toArray());
        }
      }

      // 주기 시간 업데이트 (intervals 맵이 있으면)
      if (intervals != null && !intervals.isEmpty()) {
        for (java.util.Map.Entry<String, Integer> entry : intervals.entrySet()) {
          Long seqNo = readSeq(entry.getKey());
          Integer minutes = entry.getValue();
          if (seqNo != null && minutes != null) {
            // 하한 미만은 저장하지 않는다. 호출부(AdminController)가 미리 걸러내므로 여기까지 오면
            // API 직접 호출 등 화면을 거치지 않은 경로다. 트랜잭션을 되돌리고 사유를 알린다.
            if (!CollectIntervalPolicy.isAllowed(minutes)) {
              throw new IllegalArgumentException(
                  "쇼핑몰 seq " + seqNo + " " + CollectIntervalPolicy.rejectionMessage(minutes));
            }
            conn.txPstmtExecuteUpdate(
                "UPDATE jbg_mall SET collect_interval_minutes=? WHERE seq=?", minutes, seqNo);
            log.debug("쇼핑몰 seq={} 수집주기 업데이트 완료: {}분", seqNo, minutes);
          }
        }
      }

      conn.txCommit();
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
      if (conn != null) conn.close();
    }
  }

  /**
   * 특정 쇼핑몰의 수집 주기 업데이트
   *
   * @param seq 쇼핑몰 시퀀스
   * @param intervalMinutes 주기 (분 단위)
   */
  public void updateCollectInterval(String seq, int intervalMinutes) throws Exception {
    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();
      conn.txOpen();

      long seqNo = writeSeq(seq);

      // 하한 강제. saveAutoCollectFlags 와 같은 규칙을 적용한다.
      if (!CollectIntervalPolicy.isAllowed(intervalMinutes)) {
        throw new IllegalArgumentException(
            "쇼핑몰 seq " + seqNo + " " + CollectIntervalPolicy.rejectionMessage(intervalMinutes));
      }

      String query = "UPDATE jbg_mall SET collect_interval_minutes=? WHERE seq=?";
      log.debug(
          "LOCALDB-QUERY------------------------------------------------------------------------------");
      log.debug(query);
      conn.txPstmtExecuteUpdate(query, intervalMinutes, seqNo);
      conn.txCommit();
    } catch (SQLException e) {
      log.error("* 데이터베이스 업데이트 에러 발생");
      log.error(ExceptionUtil.getExceptionInfo(e));
      rollbackQuietly(conn);
      throw e;
    } catch (Exception e) {
      log.error("* 데이터베이스 업데이트 에러 발생");
      log.error(ExceptionUtil.getExceptionInfo(e));
      rollbackQuietly(conn);
      throw e;
    } finally {
      if (conn != null) conn.close();
    }
  }

  /**
   * 쇼핑몰 전체 목록과 접속 상태 조회 (통합 - 구 JbgAccessDataAccessObject.getAccessInfos)
   *
   * @return 쇼핑몰 목록 (seq, id, status, encrypt_key, encrypt_iv, last_signin_time 포함)
   * @throws Exception
   */
  public List<JSONObject> getAccessInfos() throws Exception {
    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();
      StringBuffer querySb = new StringBuffer("SELECT seq, id, name, details, ");
      querySb.append("account_status status, encrypt_key, encrypt_iv, last_signin_time");
      querySb.append(" FROM jbg_mall");
      querySb.append(" WHERE account_status > -1");
      log.debug(
          "LOCALDB-QUERY------------------------------------------------------------------------------");
      log.debug(querySb);
      ResultSet rset = conn.executeQuery(querySb.toString());

      List<JSONObject> malls = null;
      if (rset != null) {
        malls = new ArrayList<JSONObject>();
        JSONObject mJson = null;
        while (rset.next()) {
          mJson = new JSONObject();
          mJson.put("seq", rset.getInt("seq"));
          mJson.put("id", rset.getString("id"));
          mJson.put("name", rset.getString("name"));
          mJson.put("details", rset.getString("details"));
          mJson.put("status", rset.getInt("status"));
          mJson.put("encrypt_key", rset.getString("encrypt_key"));
          mJson.put("encrypt_iv", rset.getString("encrypt_iv"));
          mJson.put("last_signin_time", rset.getLong("last_signin_time"));
          malls.add(mJson);
        }
        return malls;
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
   * 쇼핑몰 접속 정보 조회 (통합 - 구 JbgAccessDataAccessObject.getAccessInfo)
   *
   * @param seqMall 쇼핑몰 시퀀스
   * @return {status, enckey, enciv, time}
   * @throws Exception
   */
  public JSONObject getAccessInfo(String seqMall) throws Exception {
    Long seqNo = readSeq(seqMall);
    if (seqNo == null) {
      return null;
    }
    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();
      StringBuffer querySb =
          new StringBuffer(
              "SELECT account_status status, encrypt_key, encrypt_iv, last_signin_time time");
      querySb.append(" FROM jbg_mall");
      querySb.append(" WHERE seq = ?");
      log.debug(
          "LOCALDB-QUERY------------------------------------------------------------------------------");
      log.debug(querySb);
      ResultSet rset = conn.executeQuery(querySb.toString(), seqNo);

      if (rset != null) {
        JSONObject mJson = null;
        if (rset.next()) {
          mJson = new JSONObject();
          mJson.put("status", rset.getInt("status"));
          mJson.put("enckey", rset.getString("encrypt_key"));
          mJson.put("enciv", rset.getString("encrypt_iv"));
          mJson.put("time", rset.getLong("time"));
        }
        return mJson;
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

  //    /**
  //     * 저장소 공유 정보 추가 <br/>
  //     * authority : 'R' - 읽기전용(기본값), 'M' - 등록/수정/삭제 가능
  //     * @param seqUser
  //     * @param typeObject
  //     * @param seqObject
  //     * @param authority
  //     * @return
  //     * @throws Exception
  //     */
  //    public boolean add(String seqUser, String typeObject, String seqObject, String authority)
  // throws Exception {
  //
  //        StringBuffer querySb = new StringBuffer();
  //        querySb.append("INSERT INTO share (");
  //        querySb.append("seq_user,");
  //        querySb.append("type_object,");
  //        querySb.append("seq_object,");
  //        querySb.append("authority");
  //        querySb.append(") values (");
  //        querySb.append(seqUser + ", ");
  //        querySb.append("'" + typeObject + "', ");
  //        querySb.append(seqObject + ", ");
  //        querySb.append("'" + authority + "'");
  //        querySb.append(")");
  //
  // log.debug("LOCALDB-QUERY------------------------------------------------------------------------------");
  //        log.debug(querySb);
  //
  //        LocalDBConnection conn = null;
  //        try {
  //            conn = new LocalDBConnection();
  //            conn.txOpen();
  //            conn.txExecuteUpdate(querySb.toString());
  //            conn.txCommit();
  //        } catch (SQLException sqle) {
  //            log.error("* 아이고!! ㅜ.ㅜ 데이터베이스 업데이트 에러 발생");
  //            log.error(sqle.getMessage());
  //            log.debug(ExceptionUtil.getExceptionInfo(sqle.getStackTrace()));
  //            sqllog.error(ExceptionUtil.getExceptionInfo(e));
  //            throw sqle;
  //        } catch (Exception e) {
  //            log.error("* 데이터베이스 업데이트 에러 발생");
  //            log.error(e.getMessage());
  //            log.debug(ExceptionUtil.getExceptionInfo(e.getStackTrace()));
  //            log.error(ExceptionUtil.getExceptionInfo(e));
  //            conn.txRollBack();
  //            throw e;
  //        } finally {
  //            conn.close();
  //        }
  //
  //        return true;
  //    }

  /**
   * 시퀀스로 이름 조회
   *
   * @param seq 쇼핑몰 시퀀스
   * @return 쇼핑몰 이름
   * @throws Exception
   */
  public String getName(String seq) throws Exception {
    Long seqNo = readSeq(seq);
    if (seqNo == null) {
      return null;
    }
    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();
      StringBuffer querySb = new StringBuffer("SELECT name from jbg_mall");
      querySb.append(" WHERE seq=?");

      log.debug(
          "LOCALDB-QUERY------------------------------------------------------------------------------");
      log.debug(querySb);
      ResultSet rset = conn.executeQuery(querySb.toString(), seqNo);

      if (rset != null) {
        String name = null;
        if (rset.next()) {
          name = rset.getString("name");
          return name;
        }
      }
      return null;

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

  // ========== 계정 상태 관련 메서드 (통합 - 구 JbgAccessDataAccessObject) ==========

  /**
   * 서비스 이용 가능 여부 조회 (통합 - 구 JbgAccessDataAccessObject.checkAccountStatus)
   *
   * @param seqJbgmall 쇼핑몰 시퀀스
   * @return 서비스 상태 (0:사용안함, 1:사용함, 2:계정오류, 3:프로그램오류), -1:미등록
   * @throws Exception
   */
  public int checkAccountStatus(String seqJbgmall) throws Exception {
    // getMall 과 함께 확인된 주입 도달 경로다. 숫자가 아니면 '미등록'(-1) 으로 본다 —
    // 호출부(JangBoGoManager)는 -1 을 받으면 add(...) 로 넘어가고, 거기서 writeSeq 가 다시 막는다.
    // 즉 잘못된 seq 가 조용히 성공으로 흐르지는 않는다.
    Long seqNo = readSeq(seqJbgmall);
    if (seqNo == null) {
      return -1;
    }
    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();
      StringBuffer querySb = new StringBuffer("SELECT account_status from jbg_mall");
      querySb.append(" WHERE seq=?");

      log.debug(
          "LOCALDB-QUERY------------------------------------------------------------------------------");
      log.debug(querySb);
      ResultSet rset = conn.executeQuery(querySb.toString(), seqNo);

      int status = -1;
      if (rset != null) {
        if (rset.next()) {
          status = rset.getInt("account_status");
          return status;
        }
      }
      return status;

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
   * 쇼핑몰 계정 정보 업데이트 (통합 - 구 JbgAccessDataAccessObject.update)
   *
   * @param seqJbgmall 필수
   * @param accountStatus 1:이용 가능, 0:이용 불가, -1:미등록
   * @param encryptKey null 허용
   * @param encryptIv null 허용
   * @throws Exception
   */
  public void update(String seqJbgmall, int accountStatus, String encryptKey, String encryptIv)
      throws Exception {
    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();
      conn.txOpen();

      // 값을 이어 붙이지 않는다. encrypt_key/encrypt_iv 는 base64 라 '+' 와 '/' 가 섞여 있고,
      // 이 값이 깨지면 계정 비밀번호를 다시 열 수 없다 — 조용히 복호화만 실패하는 형태로 나타나
      // 원인을 찾기 어렵다. SET 절이 값에 따라 늘고 주는 형태만 그대로 두고 자리를 ? 로 바꾼다.
      StringBuilder setSb = new StringBuilder(" account_status=?");
      List<Object> params = new ArrayList<>();
      params.add(accountStatus);
      if (!JinieboxUtil.isEmpty(encryptKey)) {
        setSb.append(", encrypt_key=?");
        params.add(encryptKey);
      }
      if (!JinieboxUtil.isEmpty(encryptIv)) {
        setSb.append(", encrypt_iv=?");
        params.add(encryptIv);
      }
      params.add(writeSeq(seqJbgmall));

      String query = "UPDATE jbg_mall SET" + setSb + " WHERE seq=?";
      log.debug(
          "LOCAL-QUERY------------------------------------------------------------------------------");
      // 값은 싣지 않는다. 예전에는 조립된 쿼리를 찍어 암호화 키가 그대로 로그에 남았다.
      log.debug(query);
      conn.txPstmtExecuteUpdate(query, params.toArray());
      conn.txCommit();
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
   * 세션 스냅샷 암호화 키/IV 를 읽는다 (Phase 5-16).
   *
   * <p><b>전용 조회다.</b> 이 값은 살아 있는 세션 토큰을 여는 열쇠라 {@code getMall}/{@code getAllMalls} 의 넓은 SELECT 목록에
   * 섞지 않는다 — 그 결과 JSON 은 화면·로그로 흐르기 때문이다. 복호화가 필요한 곳({@code SessionSnapshotStore.load})에서만 이 메서드로
   * 좁게 읽는다.
   *
   * @param seqMall 쇼핑몰 시퀀스
   * @return {@code {snapshot_key, snapshot_iv}}. 몰이 없으면 null. 저장된 적 없으면 각 값이 null
   * @throws Exception 조회 오류
   */
  public JSONObject getSessionSnapshotKeys(String seqMall) throws Exception {
    Long seqNo = readSeq(seqMall);
    if (seqNo == null) {
      return null;
    }
    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();
      ResultSet rset =
          conn.executeQuery(
              "SELECT session_snapshot_key, session_snapshot_iv FROM jbg_mall WHERE seq=?", seqNo);
      if (rset != null && rset.next()) {
        JSONObject json = new JSONObject();
        json.put("snapshot_key", rset.getString("session_snapshot_key"));
        json.put("snapshot_iv", rset.getString("session_snapshot_iv"));
        return json;
      }
      return null;
    } catch (Exception e) {
      log.error("* 세션 스냅샷 키 조회 에러");
      log.error(ExceptionUtil.getExceptionInfo(e));
      throw e;
    } finally {
      if (conn != null) {
        conn.close();
      }
    }
  }

  /**
   * 세션 스냅샷 암호화 키/IV 와 캡처 시각을 저장한다 (Phase 5-16).
   *
   * <p><b>{@link #update} 를 쓰지 않는다.</b> 그 메서드는 null 인 값을 SET 절에서 통째로 빼므로(이름과 달리 지우지 못한다), 키를 명시적으로
   * 덮어써야 하는 이 저장에는 맞지 않는다. PreparedStatement 로 세 값을 그대로 쓴다.
   *
   * <p>{@code capturedAtMillis} 를 {@code session_profile_last_login} 에 함께 기록한다 — 사람이 마지막으로 로그인해 세션을
   * 뜬 시각이고, 5-17(세션 수명 측정)의 입력이다.
   *
   * @param seqMall 쇼핑몰 시퀀스
   * @param snapshotKey base64 SecretKey (매 저장마다 새로 생성된 것)
   * @param snapshotIv base64 IV
   * @param capturedAtMillis 세션을 뜬 시각 (millisecond)
   * @throws Exception 저장 오류
   */
  public void saveSessionSnapshotKeys(
      String seqMall, String snapshotKey, String snapshotIv, long capturedAtMillis)
      throws Exception {
    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();
      long seqNo = writeSeq(seqMall);
      conn.txPstmtExecuteUpdate(
          "UPDATE jbg_mall SET session_snapshot_key=?, session_snapshot_iv=?,"
              + " session_profile_last_login=? WHERE seq=?",
          snapshotKey,
          snapshotIv,
          capturedAtMillis,
          seqNo);
    } catch (Exception e) {
      log.error("* 세션 스냅샷 키 저장 에러");
      log.error(ExceptionUtil.getExceptionInfo(e));
      throw e;
    } finally {
      if (conn != null) {
        conn.close();
      }
    }
  }

  /**
   * 세션 프로필의 신원(이름·소유자·상태)을 기록한다. <b>캡처가 성공한 순간에만 부른다.</b>
   *
   * <h3>왜 이 메서드가 따로 있어야 했나</h3>
   *
   * <p>{@code SessionProfileGate.evaluate} 는 <b>프로필 디렉터리 모델</b>로 판정한다 — {@code
   * session_profile_name} 이 비면 {@code PROFILE_MISSING} 으로 막고, {@code session_profile_owner} 로 OS
   * 계정을 대조한다. 그런데 v0.16.0 의 캡처가 커밋하는 것은 {@link #saveSessionSnapshotKeys} 의 세 값(키·IV·마지막 로그인 시각)뿐이었고
   * 이 세 컬럼을 쓰는 코드가 저장소에 하나도 없었다. 그 상태로 몰 옵트인을 켜면 <b>수집이 한 줄도 돌기 전에 전량 SKIPPED</b> 된다. 그 구멍을 닫는 것이 이
   * 메서드다.
   *
   * <h3>넘기는 값이 정해져 있다</h3>
   *
   * <ul>
   *   <li>{@code profileName} 은 반드시 {@code MallRegistry.mallId()} — 게이트가 통과시킨 이 이름이 그대로 {@code
   *       SessionProfilePolicy.profileDir()} 에 들어간다. 캡처가 실제로 만든 디렉터리와 다르면 <b>게이트는 통과하는데 수집이 빈 프로필을
   *       연다</b>(로그인 화면에서 멈춘 채 타임아웃 — 로그만 보고는 원인을 못 가린다).
   *   <li>{@code profileOwner} 는 반드시 {@code System.getProperty("user.name")} — 게이트에 현재 계정으로 넘어가는 값과
   *       <b>같은 소스</b>여야 한다. 다른 소스(환경변수 USERNAME 등)로 적으면 표기가 갈리는 순간 {@code PROFILE_OWNER_MISMATCH}
   *       라는 새 차단이 생긴다. null 이면 owner 를 비워 두는 셈이고, 게이트는 미기록 소유자를 검사하지 않으므로 막히지는 않는다.
   * </ul>
   *
   * <h3>상태는 READY 만 쓴다</h3>
   *
   * <p>{@code session_profile_status} 에 {@code EXPIRED} 를 <b>쓰지 않는다.</b> 만료는 시간이 흘러서 생기는 것인데 그 값을
   * 때맞춰 갱신해 줄 주체가 이 프로그램에 없다 — 한 번 굳으면 세션이 멀쩡히 살아 있어도 만료로 남는다. 만료 여부는 {@code
   * session_profile_last_login} 에서 <b>파생 계산</b>할 것이고, 이 컬럼은 '캡처가 성공해 프로필이 준비됐다' 는 사실만 담는다.
   *
   * <h3>PreparedStatement 인 이유</h3>
   *
   * <p>프로필 이름·OS 계정명은 따옴표가 섞일 수 있는 외부 입력이다. {@link #saveSessionSnapshotKeys} 와 같은 {@code
   * txPstmtExecuteUpdate} 패턴을 쓴다.
   *
   * <p>이 파일에 남아 있던 문자열 조립은 숫자 자리까지 전부 걷어냈다. 따옴표로 감싸지 않은 {@code seq} 자리가 마지막까지 남아 있었고, 그 자리가 실제 주입
   * 도달 경로였다.
   *
   * <p>키 저장과 <b>같은 문장에 합치지 않은</b> 이유는, 키 커밋은 {@code SessionSnapshotStore} 안쪽(몰 이름을 모르는 자리)에서 일어나고 이
   * 기록은 캡처 서비스(몰을 아는 자리)에서 일어나기 때문이다. SET 절이 겹치지 않아 서로를 지우지도 않는다.
   *
   * @param seqMall 쇼핑몰 시퀀스
   * @param profileName 프로필 디렉터리 이름. {@code MallRegistry.mallId()} 값
   * @param profileOwner 프로필을 만든 OS 계정. {@code System.getProperty("user.name")} 값
   * @throws Exception 저장 오류
   */
  public void saveSessionProfileIdentity(String seqMall, String profileName, String profileOwner)
      throws Exception {
    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();
      long seqNo = writeSeq(seqMall);
      conn.txPstmtExecuteUpdate(
          "UPDATE jbg_mall SET session_profile_name=?, session_profile_owner=?,"
              + " session_profile_status=? WHERE seq=?",
          profileName,
          profileOwner,
          SESSION_PROFILE_STATUS_READY,
          seqNo);
    } catch (Exception e) {
      log.error("* 세션 프로필 신원 저장 에러");
      log.error(ExceptionUtil.getExceptionInfo(e));
      throw e;
    } finally {
      if (conn != null) {
        conn.close();
      }
    }
  }

  /** seq 는 전부 SQLite INTEGER 컬럼 값이다. 10진 숫자 외에는 존재할 수 없다. */
  private static final Pattern SEQ_DIGITS = Pattern.compile("[0-9]+");

  /**
   * 조회에 쓸 seq. 10진 숫자가 아니면 {@code null} 을 돌린다.
   *
   * <p><b>예전의 {@code replaceAll("[^0-9]", "")} 를 되살리지 마라.</b> 그 필터는 거르는 것이 아니라 <b>깎는다</b> — 숫자가 섞인
   * 공격 문자열을 남은 숫자만 이어 붙인 <b>다른 유효한 seq</b> 로 바꿔 놓고 아무 일도 없었다는 듯 남의 몰을 읽어 온다. 조립을 막으려고 깔아 둔 필터가 그
   * 자체로 조용한 오조회가 되는 것이 필터 단독 방어의 실제 실패 형태다.
   *
   * <p>실패를 예외가 아니라 값으로 돌리는 이유: 조회 호출부(AdminController·MallSchedulerService·SessionSnapshotStore)는
   * 이미 '몰 없음' 을 null 로 받아 처리한다. 예외로 올리면 무인 수집의 포괄 catch 가 계정 문제로 읽어 연결을 끊는다.
   */
  private static Long readSeq(String seq) {
    if (seq == null) {
      return null;
    }
    String trimmed = seq.trim();
    if (!SEQ_DIGITS.matcher(trimmed).matches()) {
      return null;
    }
    try {
      return Long.valueOf(trimmed);
    } catch (NumberFormatException tooManyDigits) {
      // long 범위를 넘은 자릿수. 어차피 존재할 수 없는 seq 라 '결과 없음' 과 같다.
      return null;
    }
  }

  /**
   * 갱신에 쓸 seq. 숫자가 아니면 던진다.
   *
   * <p>조회와 달리 갱신은 대상이 틀린 것을 조용히 넘기면 안 된다. seq 가 깨진 채로 흘러가면 WHERE 가 아무 행도 고르지 못한 것을 '성공' 으로 보고하고,
   * 화면은 저장됐다고 말한다. 여기서 끊는다.
   */
  private static long writeSeq(String seq) {
    Long parsed = readSeq(seq);
    if (parsed == null) {
      throw new IllegalArgumentException("유효한 seq 가 아니다: " + seq);
    }
    return parsed;
  }

  /**
   * 열려 있던 트랜잭션을 되돌린다. <b>되돌리기가 실패해도 던지지 않는다.</b>
   *
   * <p>이 파일의 갱신 여러 개가 {@code catch (SQLException)} 에서 롤백 없이 그대로 다시 던지고 있었다. DB 갱신이 깨질 때 실제로 타는 것은 그
   * 쪽이다 — 뒤에 있는 {@code catch (Exception)} 의 롤백은 SQL 이 아닌 사유로 터질 때만 돈다. 즉 <b>정작 되돌려야 할 경우에만 안
   * 되돌리고</b> 있었다.
   *
   * <p>던지지 않는 이유는 따로 있다. 롤백이 실패하면 그 예외가 <b>원래 실패 원인을 덮는다</b> — "왜 갱신이 깨졌나" 를 잃고 "롤백이 안 됐다" 만 남는다.
   * 진단 가치가 큰 쪽은 원인이므로 롤백 실패는 로그로만 남기고 원래 예외를 그대로 위로 올린다.
   */
  // rollbackQuietly 는 CommonDataAccessObject 로 올렸다. 여기와 JbgItemDataAccessObject 에
  // 같은 구현이 복제돼 있었는데, 복제된 예외 처리 규칙은 한쪽만 고쳐지면서 조용히 갈라진다.

  /**
   * 마지막 로그인 시간을 현재시간으로 설정 (통합 - 구 JbgAccessDataAccessObject.updateLastSigninTime)
   *
   * @param seqJbgmall 쇼핑몰 시퀀스
   * @throws Exception
   */
  public void updateLastSigninTime(String seqJbgmall) throws Exception {
    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();
      conn.txOpen();

      long seqNo = writeSeq(seqJbgmall);
      String query = "UPDATE jbg_mall SET last_signin_time=? WHERE seq=?";
      log.debug(
          "LOCAL-QUERY------------------------------------------------------------------------------");
      log.debug(query);
      conn.txPstmtExecuteUpdate(query, System.currentTimeMillis(), seqNo);
      conn.txCommit();
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
   * 계정 상태 설정 (통합 - 구 JbgAccessDataAccessObject.setAccountStatus)
   *
   * @param seqJbgmall 쇼핑몰 시퀀스
   * @param accountStatus 서비스 상태 (0:사용안함, 1:사용함, 2:계정오류, 3:프로그램오류)
   * @throws Exception
   */
  public void setAccountStatus(String seqJbgmall, int accountStatus) throws Exception {
    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();
      conn.txOpen();

      // SET 절이 accountStatus 값에 따라 늘고 주는 형태만 남기고 값은 전부 자리표시자로 뺀다.
      StringBuilder setSb = new StringBuilder(" account_status=?");
      List<Object> params = new ArrayList<>();
      params.add(accountStatus);
      if (accountStatus == 1) {
        setSb.append(", last_signin_time=?");
        params.add(System.currentTimeMillis());
      }
      params.add(writeSeq(seqJbgmall));

      String query = "UPDATE jbg_mall SET" + setSb + " WHERE seq=?";
      log.debug(
          "LOCAL-QUERY------------------------------------------------------------------------------");
      log.debug(query);
      conn.txPstmtExecuteUpdate(query, params.toArray());
      conn.txCommit();
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
   * 쇼핑몰 계정 정보 등록 (통합 - 구 JbgAccessDataAccessObject.add)
   *
   * <p>주의: 테이블 통합으로 인해 이 메서드는 UPDATE 방식으로 동작 기존 jbg_mall 레코드의 encrypt_key, encrypt_iv,
   * account_status를 업데이트
   *
   * @param seqJbgmall 필수
   * @param accountStatus 1:이용 가능, 0:이용 불가, -1:미등록
   * @param encryptKey null 허용
   * @param encryptIv null 허용
   * @return
   * @throws Exception
   */
  public boolean add(String seqJbgmall, int accountStatus, String encryptKey, String encryptIv)
      throws Exception {
    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();
      conn.txOpen();

      // update(...) 와 같은 이유로 바인딩을 쓴다 — 여기 base64 키가 깨지면 계정 복호화가 조용히 실패한다.
      StringBuilder setSb = new StringBuilder(" account_status=?");
      List<Object> params = new ArrayList<>();
      params.add(accountStatus);
      if (!JinieboxUtil.isEmpty(encryptKey)) {
        setSb.append(", encrypt_key=?");
        params.add(encryptKey);
      }
      if (!JinieboxUtil.isEmpty(encryptIv)) {
        setSb.append(", encrypt_iv=?");
        params.add(encryptIv);
      }
      setSb.append(", last_signin_time=?");
      params.add(System.currentTimeMillis());
      params.add(writeSeq(seqJbgmall));

      String query = "UPDATE jbg_mall SET" + setSb + " WHERE seq=?";
      log.debug(
          "LOCALDB-QUERY------------------------------------------------------------------------------");
      // 값은 싣지 않는다. 예전에는 조립된 쿼리를 찍어 암호화 키가 그대로 로그에 남았다.
      log.debug(query);
      conn.txPstmtExecuteUpdate(query, params.toArray());
      conn.txCommit();

      return true;
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

  //    /**
  //     * 특정 객체(저장소, 보관함, 나눔함)에 대한 공유 정보 삭제
  //     * @param typeObject
  //     * @param seqObject
  //     * @return
  //     * @throws Exception
  //     */
  //    public boolean delete(String typeObject, String seqObject) throws Exception {
  //
  //        boolean chkSeqObj = NumberUtil.isNumber(seqObject);
  //
  //        if (typeObject == null || !chkSeqObj) {
  //            // 모든 공유정보를 삭제하게 되므로 실패해야 한다.
  //            return false;
  //        }
  //
  //        StringBuffer querySb = new StringBuffer();
  //        querySb.append("DELETE from share");
  //        querySb.append(" WHERE 1=1");
  //        if (chkSeqObj) {
  //            querySb.append(" AND type_object='" + typeObject + "'");
  //            querySb.append(" AND seq_object=" + seqObject);
  //        }
  //
  //        String query = querySb.toString();
  //
  // log.debug("LOCAL-QUERY------------------------------------------------------------------------------");
  //        log.debug(query);
  //
  //        LocalDBConnection conn = null;
  //        try {
  //            conn = new LocalDBConnection();
  //            conn.txOpen();
  //            conn.txExecuteUpdate(query);
  //            conn.txCommit();
  //        } catch (SQLException sqle) {
  //            log.error("* 아이고!! ㅜ.ㅜ 데이터베이스 업데이트 에러 발생");
  //            log.error(sqle.getMessage());
  //            log.debug(ExceptionUtil.getExceptionInfo(sqle.getStackTrace()));
  //            sqllog.error(ExceptionUtil.getExceptionInfo(e));
  //            throw sqle;
  //        } catch (Exception e) {
  //            log.error("* 아이고!! ㅜ.ㅜ 데이터베이스 업데이트 에러 발생");
  //            log.error(e.getMessage());
  //            log.debug(ExceptionUtil.getExceptionInfo(e.getStackTrace()));
  //            log.error(ExceptionUtil.getExceptionInfo(e));
  //            conn.txRollBack();
  //            throw e;
  //        } finally {
  //            conn.close();
  //        }
  //
  //        return true;
  //    }
}

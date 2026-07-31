package com.jiniebox.jangbogo.dao;

import com.jiniebox.jangbogo.dto.JangbogoConfig;
import com.jiniebox.jangbogo.util.ExceptionUtil;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

public class CommonDataAccessObject {

  private static final Logger logger = LogManager.getLogger(CommonDataAccessObject.class);

  @Autowired private JangbogoConfig jangbogoConfig;

  /**
   * DAO 를 만들기 전에 스키마가 최신 선언에 맞는지 보장한다 (Phase 3-10).
   *
   * <p>{@link SchemaMigrator#ensureMigrated()} 는 JVM 당 한 번만 실제 작업을 하므로, 여기 두어도 첫 호출 이후에는 {@code
   * AtomicBoolean} 한 번 읽는 비용뿐이다. 통합 전 {@code JbgMallDataAccessObject} 가 조회마다 예외 기반 컬럼 탐지를 두 번씩 돌리던
   * 것보다 싸다.
   *
   * <p>기동 시 {@code StartupTasks} 도 같은 메서드를 부른다. 이쪽은 <b>안전망</b>이다 — 웹서버는 {@code
   * ApplicationReadyEvent} 보다 먼저 뜨므로 그 사이에 들어온 요청도 스키마가 보장돼야 한다.
   */
  protected CommonDataAccessObject() {
    SchemaMigrator.ensureMigrated();
  }

  /**
   * AUTO_INCREMENT 컬럼의 다음 시퀀스
   *
   * @param tableName
   * @return
   * @throws Exception
   */
  protected int getNextSeq(String tableName) throws Exception {
    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();
      StringBuffer querySb =
          new StringBuffer(
              "SELECT AUTO_INCREMENT seq "
                  + "FROM information_schema.tables "
                  + "WHERE table_schema = '"
                  + jangbogoConfig.get("LOCALDB_NAME")
                  + "' AND table_name = '"
                  + tableName
                  + "';");
      logger.debug(
          "LOCALDB-QUERY------------------------------------------------------------------------------");
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
      if (conn != null) {
        conn.close();
      }
    }
  }

  /**
   * 가장 최근에 성공적으로 수행된 INSERT 구문의 첫번째 AUTO_INCREMENT column의 값을 반환받는 쿼리 SQLite에서는 last_insert_rowid()
   * 사용
   *
   * @param conn 데이터베이스 연결 객체
   * @return 마지막 INSERT로 생성된 시퀀스 값
   * @throws SQLException
   */
  protected int getLastInsertSeq(LocalDBConnection conn) throws SQLException {
    int seq = -1;
    String getLastIdQuery = "SELECT last_insert_rowid() id";
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

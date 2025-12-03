package com.jiniebox.jangbogo.dev;

import com.jiniebox.jangbogo.dao.LocalDBConnection;
import com.jiniebox.jangbogo.util.ExceptionUtil;
import java.sql.SQLException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 개발용 데이터베이스 초기화 유틸리티
 *
 * @author KIUNSEA
 */
public class DatabaseResetUtil {

  private static final Logger logger = LogManager.getLogger(DatabaseResetUtil.class);

  /** jbg_order와 jbg_item 테이블의 데이터 및 시퀀스 초기화 */
  public static void resetOrderAndItemTables() {
    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();
      conn.txOpen();

      logger.info("===========================================================================");
      logger.info("데이터베이스 초기화 시작");
      logger.info("===========================================================================");

      // 1. jbg_item 데이터 삭제
      conn.txExecuteUpdate("DELETE FROM jbg_item");
      logger.info("jbg_item 테이블 데이터 삭제 완료");

      // 2. jbg_order 데이터 삭제
      conn.txExecuteUpdate("DELETE FROM jbg_order");
      logger.info("jbg_order 테이블 데이터 삭제 완료");

      // 3. AUTO_INCREMENT 시퀀스 초기화
      conn.txExecuteUpdate("DELETE FROM sqlite_sequence WHERE name='jbg_item'");
      logger.info("jbg_item AUTO_INCREMENT 시퀀스 초기화 완료");

      conn.txExecuteUpdate("DELETE FROM sqlite_sequence WHERE name='jbg_order'");
      logger.info("jbg_order AUTO_INCREMENT 시퀀스 초기화 완료");

      conn.txCommit();

      logger.info("===========================================================================");
      logger.info("데이터베이스 초기화 완료");
      logger.info("===========================================================================");

    } catch (SQLException e) {
      logger.error("데이터베이스 초기화 실패 (SQLException)");
      logger.error(ExceptionUtil.getExceptionInfo(e));
      if (conn != null) {
        try {
          conn.txRollBack();
          logger.info("트랜잭션 롤백 완료");
        } catch (Exception rollbackEx) {
          logger.error("롤백 실패: {}", rollbackEx.getMessage());
        }
      }
    } catch (Exception e) {
      logger.error("데이터베이스 초기화 실패");
      logger.error(ExceptionUtil.getExceptionInfo(e));
      if (conn != null) {
        try {
          conn.txRollBack();
        } catch (Exception rollbackEx) {
          logger.error("롤백 실패: {}", rollbackEx.getMessage());
        }
      }
    } finally {
      if (conn != null) {
        try {
          conn.close();
        } catch (Exception closeEx) {
          logger.error("DB 연결 종료 실패: {}", closeEx.getMessage());
        }
      }
    }
  }

  /** 테스트용 메인 메서드 */
  public static void main(String[] args) {
    resetOrderAndItemTables();
  }
}

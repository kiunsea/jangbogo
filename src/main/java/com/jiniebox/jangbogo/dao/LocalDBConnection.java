package com.jiniebox.jangbogo.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * @author KIUNSEA
 */
public class LocalDBConnection {

  /** 기본 접속 대상. 운영·개발 콘솔 실행이 사용하는 값이다. */
  private static final String DEFAULT_DB_URL = "jdbc:sqlite:./db/jangbogo-dev.db";

  /**
   * 접속 대상 오버라이드 키.
   *
   * <p>이 클래스는 Spring DataSource 를 거치지 않고 직접 JDBC 로 접속하므로 {@code application.yml} 의 {@code
   * spring.datasource.url} 이 적용되지 않는다. 그 결과 테스트가 기준선 DB 파일을 그대로 열게 되므로, 테스트 실행에서만 다른 파일을 가리킬 수 있도록
   * 시스템 프로퍼티로 뺀다. 지정하지 않으면 {@link #DEFAULT_DB_URL} 이므로 운영 동작은 바뀌지 않는다.
   *
   * <p>설정 위치: {@code build.gradle} 의 {@code tasks.named('test')}
   */
  private static final String DB_URL_PROPERTY = "jangbogo.localdb.url";

  // JDBC정보를 셋팅
  private final String DB_URL = System.getProperty(DB_URL_PROPERTY, DEFAULT_DB_URL);
  private final String DB_DRIVER = "org.sqlite.JDBC";

  private Connection conn = null;
  private Statement stmt = null;
  private ResultSet rset = null;

  public LocalDBConnection() throws ClassNotFoundException, SQLException {

    // DB CONNECT
    // 1. JDBC 드라이버 로드
    Class.forName(DB_DRIVER);

    // 2. 데이터베이스 연결
    conn = DriverManager.getConnection(DB_URL);

    // 3. SQLite PRAGMA 설정
    try (Statement pragmaStmt = conn.createStatement()) {
      pragmaStmt.execute("PRAGMA foreign_keys=ON");
      pragmaStmt.execute("PRAGMA journal_mode=WAL");
    }

    // 4. Statement 생성
    if (conn != null) {
      stmt = conn.createStatement();
    }
  }

  public ResultSet executeQuery(String query) throws SQLException {
    if (stmt != null) {
      rset = stmt.executeQuery(query);
    }
    return rset;
  }

  public void txOpen() throws SQLException {
    conn.setAutoCommit(false);
  }

  public void txClose() throws SQLException {
    conn.setAutoCommit(true);
  }

  public void txCommit() throws SQLException {
    conn.commit();
  }

  public void txRollBack() throws SQLException {
    conn.rollback();
  }

  public void txExecuteUpdate(String query) throws SQLException {
    if (stmt != null) {
      stmt.executeUpdate(query);
    }
  }

  /**
   * PreparedStatement를 사용한 UPDATE/INSERT/DELETE 실행
   *
   * @param query SQL 쿼리 (? placeholder 포함)
   * @param params 가변 인자로 전달되는 파라미터들 (타입 제한 없음)
   * @return 영향받은 행의 개수
   * @throws SQLException
   */
  public int txPstmtExecuteUpdate(String query, Object... params) throws SQLException {
    if (conn == null) {
      throw new SQLException("Database connection is not established");
    }

    try (PreparedStatement pstmt = conn.prepareStatement(query)) {
      // 가변 인자로 전달된 파라미터들을 PreparedStatement에 설정
      for (int i = 0; i < params.length; i++) {
        Object param = params[i];

        // null 처리
        if (param == null) {
          pstmt.setObject(i + 1, null);
        }
        // 타입별로 적절한 setter 호출
        else if (param instanceof String) {
          pstmt.setString(i + 1, (String) param);
        } else if (param instanceof Integer) {
          pstmt.setInt(i + 1, (Integer) param);
        } else if (param instanceof Long) {
          pstmt.setLong(i + 1, (Long) param);
        } else if (param instanceof Double) {
          pstmt.setDouble(i + 1, (Double) param);
        } else if (param instanceof Float) {
          pstmt.setFloat(i + 1, (Float) param);
        } else if (param instanceof Boolean) {
          pstmt.setBoolean(i + 1, (Boolean) param);
        } else if (param instanceof java.sql.Date) {
          pstmt.setDate(i + 1, (java.sql.Date) param);
        } else if (param instanceof java.sql.Timestamp) {
          pstmt.setTimestamp(i + 1, (java.sql.Timestamp) param);
        } else if (param instanceof java.util.Date) {
          pstmt.setTimestamp(i + 1, new java.sql.Timestamp(((java.util.Date) param).getTime()));
        } else if (param instanceof byte[]) {
          pstmt.setBytes(i + 1, (byte[]) param);
        } else {
          // 기타 타입은 setObject로 처리
          pstmt.setObject(i + 1, param);
        }
      }

      // UPDATE/INSERT/DELETE 실행
      return pstmt.executeUpdate();
    }
  }

  public void close() throws SQLException {

    try {
      if (rset != null) {
        rset.close();
        rset = null;
      }
      if (stmt != null) {
        stmt.close();
        stmt = null;
      }
      if (conn != null) {
        conn.close();
        conn = null;
      }
    } catch (SQLException e) {
      throw e;
    } finally { // finally 를 이용해서 무조건 모든 리소스를 반환토록 한다.
      if (rset != null) {
        try { // close 시에도 exception 이 발생할 수 있기 때문에 각각도 예외처리를 한다.
          rset.close();
        } catch (SQLException e) {
          throw e;
        }
      }
      if (stmt != null) {
        try {
          stmt.close();
        } catch (SQLException e) {
          throw e;
        }
      }
      if (conn != null) {
        try {
          conn.close();
        } catch (SQLException e) {
          throw e;
        }
      }
    }
  }
}

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

  /**
   * {@link #executeQuery(String, Object...)} 가 연 PreparedStatement.
   *
   * <p><b>필드로 들고 있어야 한다.</b> try-with-resources 로 닫으면 방금 돌려준 ResultSet 이 함께 죽어 호출부가 첫 {@code
   * next()} 에서 "ResultSet closed" 를 만난다. 조회는 결과를 밖으로 넘겨야 하므로 수명이 문장보다 길다. {@link #close()} 가 커넥션과
   * 함께 정리한다 — 이 클래스의 기존 {@code rset}/{@code stmt} 수명 규약과 같은 방식이다.
   */
  private PreparedStatement pstmt = null;

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

  /**
   * PreparedStatement 를 사용한 SELECT 실행.
   *
   * <p><b>이 경로가 없어서 조회가 전부 문자열 조립으로 남아 있었다.</b> 쓰기({@link #txPstmtExecuteUpdate})만 막아 두고 읽기에는 바인딩
   * 수단을 주지 않았으니, DAO 가 조회를 쓰려면 이어 붙이는 것 말고 선택지가 없었다. 실제로 {@code JbgOrderDataAccessObject.getOrder}
   * 가 수집한 영수증 번호를 그대로 WHERE 절에 붙이고 있었고, 그 번호는 페이지 텍스트에서 합성한 값이라 숫자 필터조차 거치지 않는다.
   *
   * <p>기존 {@link #executeQuery(String)} 는 그대로 둔다 — 호출자가 많다. 인자 없이 부르면 자바 오버로드 해석이 가변인자가 아닌 쪽을 고르므로
   * 기존 호출의 동작은 바뀌지 않는다.
   *
   * <p>이전에 이 메서드로 연 PreparedStatement 는 여기서 닫는다. 한 커넥션에서 조회를 두 번 하면 앞의 것은 더 쓰지 않는다는 뜻이고, 이는 하나의
   * {@code Statement} 로 {@code executeQuery} 를 두 번 부를 때와 같은 규약이다.
   *
   * @param query SQL 쿼리 (? placeholder 포함)
   * @param params 가변 인자로 전달되는 파라미터들 (타입 제한 없음)
   * @return 조회 결과. 커넥션·문장 수명은 이 객체가 소유하므로 {@link #close()} 전에 다 읽어야 한다
   * @throws SQLException 조회 실패
   */
  public ResultSet executeQuery(String query, Object... params) throws SQLException {
    if (conn == null) {
      throw new SQLException("Database connection is not established");
    }

    if (pstmt != null) {
      try {
        pstmt.close();
      } catch (SQLException ignore) {
        // 앞선 조회를 닫지 못한 것이 이번 조회를 막을 이유는 없다. 커넥션이 닫히면 함께 정리된다.
      }
      pstmt = null;
    }

    PreparedStatement ps = conn.prepareStatement(query);
    try {
      bindParams(ps, params);
      rset = ps.executeQuery();
    } catch (SQLException e) {
      // 실패한 문장은 필드에 남기지 않으므로 여기서 닫지 않으면 close() 까지 새어 나간다.
      try {
        ps.close();
      } catch (SQLException ignore) {
        // 정리 실패가 원래 실패 원인을 덮지 않게 한다.
      }
      throw e;
    }
    pstmt = ps;
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

    try (PreparedStatement updateStmt = conn.prepareStatement(query)) {
      bindParams(updateStmt, params);

      // UPDATE/INSERT/DELETE 실행
      return updateStmt.executeUpdate();
    }
  }

  /**
   * 가변 인자로 전달된 파라미터들을 PreparedStatement 에 설정한다.
   *
   * <p>조회와 갱신이 <b>같은 바인딩 규칙</b>을 쓰게 하려고 따로 뺐다. 두 벌로 두면 한쪽만 타입이 추가되고, 그 차이는 "갱신은 되는데 같은 값으로 조회하면 안
   * 걸린다" 는 형태로 나타난다 — 중복 방지 조회가 그런 식으로 조용히 어긋나면 같은 주문이 두 번 쌓인다.
   */
  private void bindParams(PreparedStatement ps, Object... params) throws SQLException {
    for (int i = 0; i < params.length; i++) {
      Object param = params[i];

      // null 처리
      if (param == null) {
        ps.setObject(i + 1, null);
      }
      // 타입별로 적절한 setter 호출
      else if (param instanceof String) {
        ps.setString(i + 1, (String) param);
      } else if (param instanceof Integer) {
        ps.setInt(i + 1, (Integer) param);
      } else if (param instanceof Long) {
        ps.setLong(i + 1, (Long) param);
      } else if (param instanceof Double) {
        ps.setDouble(i + 1, (Double) param);
      } else if (param instanceof Float) {
        ps.setFloat(i + 1, (Float) param);
      } else if (param instanceof Boolean) {
        ps.setBoolean(i + 1, (Boolean) param);
      } else if (param instanceof java.sql.Date) {
        ps.setDate(i + 1, (java.sql.Date) param);
      } else if (param instanceof java.sql.Timestamp) {
        ps.setTimestamp(i + 1, (java.sql.Timestamp) param);
      } else if (param instanceof java.util.Date) {
        ps.setTimestamp(i + 1, new java.sql.Timestamp(((java.util.Date) param).getTime()));
      } else if (param instanceof byte[]) {
        ps.setBytes(i + 1, (byte[]) param);
      } else {
        // 기타 타입은 setObject로 처리
        ps.setObject(i + 1, param);
      }
    }
  }

  public void close() throws SQLException {

    try {
      if (rset != null) {
        rset.close();
        rset = null;
      }
      if (pstmt != null) {
        pstmt.close();
        pstmt = null;
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
      if (pstmt != null) {
        try {
          pstmt.close();
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

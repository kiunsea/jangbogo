package com.jiniebox.jangbogo.dao;

import com.jiniebox.jangbogo.util.ExceptionUtil;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CommonDataAccessObject {

  private static final Logger commonLog = LogManager.getLogger(CommonDataAccessObject.class);

  /**
   * 트랜잭션을 되돌리되, 되돌리기가 실패해도 그 예외를 밖으로 내지 않는다.
   *
   * <p>왜 삼키는가 — 되돌리기는 이미 <b>다른 실패를 처리하는 도중</b>에 불린다. 여기서 나온 예외를 그대로 올리면 원래 실패 원인을 덮어써서 "왜 갱신이 깨졌나"
   * 를 잃는다. 사람이 보는 것은 되돌리기가 실패했다는 사실뿐이고, 정작 고쳐야 할 원인은 사라진다. 그래서 되돌리기 실패는 로그로만 남기고 원래 예외를 그대로 올리게 둔다.
   *
   * <p>대가는 분명하다 — "되돌리기가 실패했다" 는 사실이 호출부로 전파되지 않는다. 트랜잭션이 정말로 안 닫힌 상황을 프로그램이 감지할 수단은 없고 로그가 유일한
   * 단서다. 그래도 원인을 잃는 쪽이 더 나쁘다고 보고 이렇게 둔다.
   *
   * <p>여기 있는 이유 — 한때 이 메서드가 두 DAO 에 똑같이 복제돼 있었다. 복제된 예외 처리 규칙은 한쪽만 고쳐지면서 조용히 갈라진다. 트랜잭션을 여는 DAO 는
   * 전부 이 클래스를 상속하므로 규칙을 여기 한 곳에 둔다.
   *
   * @param conn 되돌릴 연결. {@code null} 이면 아무 것도 하지 않는다
   */
  protected static void rollbackQuietly(LocalDBConnection conn) {
    if (conn == null) {
      return;
    }
    try {
      conn.txRollBack();
    } catch (SQLException rollbackFailure) {
      commonLog.error("* 트랜잭션 되돌리기 실패 (원래 실패 원인은 뒤따르는 예외를 볼 것)");
      commonLog.error(ExceptionUtil.getExceptionInfo(rollbackFailure));
    }
  }

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

  // getNextSeq(String) 은 지웠다. 세 가지 이유가 겹쳐 되살릴 수 없는 코드였다.
  //
  //  1. 호출부가 하나도 없다 (protected 였고 어떤 하위 DAO 도 부르지 않았다).
  //  2. information_schema.tables 는 MySQL 의 것이고 SQLite 에는 없다. 불렸다면 매번 예외였다.
  //  3. 테이블 스키마명을 jangbogoConfig 에서 읽는데, 이 프로젝트의 DAO 는 Spring 빈이 아니라
  //     new 로 만든다. 즉 그 필드는 주입된 적이 없어 NPE 가 먼저 났을 것이다.
  //
  // 남겨 두면 손해만 있었다 — 시퀀스 채번 수단이 있는 것처럼 보여 다음 사람이 집어 들고,
  // SQL 조립 가드는 이 파일 하나 때문에 예외 목록을 들고 있어야 했다. 새 시퀀스가 필요하면
  // getLastInsertSeq(conn)(SQLite 의 last_insert_rowid) 를 쓴다.

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

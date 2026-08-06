package com.jiniebox.jangbogo.dao;

import com.jiniebox.jangbogo.util.ExceptionUtil;
import com.jiniebox.jangbogo.util.PasswordEncryptor;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;

/**
 * 파일 저장 설정 DAO
 *
 * <p>jbg_export_config 테이블에 접근하는 모든 기능 제공
 *
 * @author KIUNSEA
 */
public class JbgExportConfigDataAccessObject extends CommonDataAccessObject {

  private static final Logger log = LogManager.getLogger(JbgExportConfigDataAccessObject.class);

  public JbgExportConfigDataAccessObject() {
    // 기본 생성자
  }

  /**
   * 파일 저장 설정 조회 (단일 설정)
   *
   * @return 설정 정보 (save_path, save_format, auto_save_enabled, save_to_jiniebox, ftp_address,
   *     ftp_id, ftp_pass, public_key)
   * @throws Exception
   */
  public JSONObject getConfig() throws Exception {
    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();

      StringBuffer querySb =
          new StringBuffer("SELECT id, save_path, save_format, auto_save_enabled, ");
      querySb.append(
          "save_to_jiniebox, ftp_address, ftp_id, ftp_pass, public_key, ftp_encrypt_enabled, ");
      querySb.append("last_export_time, last_ftp_upload_time");
      querySb.append(" FROM jbg_export_config WHERE id=1");
      log.debug(
          "LOCALDB-QUERY------------------------------------------------------------------------------");
      log.debug(querySb);
      ResultSet rset = conn.executeQuery(querySb.toString());

      JSONObject config = null;
      if (rset != null && rset.next()) {
        config = new JSONObject();
        config.put("id", rset.getInt("id"));
        config.put("save_path", rset.getString("save_path"));
        config.put("save_format", rset.getString("save_format"));
        config.put("auto_save_enabled", rset.getInt("auto_save_enabled"));
        config.put("save_to_jiniebox", rset.getInt("save_to_jiniebox"));
        config.put("ftp_address", rset.getString("ftp_address"));
        config.put("ftp_id", rset.getString("ftp_id"));

        // ftp_pass는 보안을 위해 평문을 전송하지 않고, 존재 여부만 전달
        String encryptedPass = rset.getString("ftp_pass");
        boolean hasPassword = (encryptedPass != null && !encryptedPass.isEmpty());
        config.put("has_ftp_password", hasPassword);

        config.put("public_key", rset.getString("public_key"));
        config.put("ftp_encrypt_enabled", rset.getInt("ftp_encrypt_enabled"));
        config.put("last_export_time", rset.getLong("last_export_time"));
        config.put("last_ftp_upload_time", rset.getLong("last_ftp_upload_time"));
      } else {
        // 설정이 없으면 기본값 생성
        config = createDefaultConfig();
      }
      return config;
    } catch (Exception e) {
      log.error("설정 조회 중 에러 발생");
      log.error(ExceptionUtil.getExceptionInfo(e));
      throw e;
    } finally {
      if (conn != null) {
        conn.close();
      }
    }
  }

  /**
   * 파일 저장 설정 업데이트
   *
   * @param savePath 저장 경로
   * @param saveFormat 저장 포맷
   * @param autoSaveEnabled 구매내역 수집시 함께 저장 여부 (0 or 1)
   * @param saveToJiniebox 지니박스에도 함께 저장 여부 (0 or 1)
   * @param ftpAddress FTP 주소
   * @param ftpId FTP 아이디
   * @param ftpPass FTP 비밀번호
   * @param publicKey RSA Public Key (Base64) - 파일 암호화용
   * @throws Exception
   */
  public void updateConfig(
      String savePath,
      String saveFormat,
      int autoSaveEnabled,
      int saveToJiniebox,
      String ftpAddress,
      String ftpId,
      String ftpPass,
      String publicKey,
      int ftpEncryptEnabled)
      throws Exception {
    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();
      conn.txOpen();

      // 기존 설정 확인
      ResultSet checkRset =
          conn.executeQuery("SELECT id, ftp_pass FROM jbg_export_config WHERE id=1");
      boolean exists = false;
      String existingEncryptedPass = "";
      if (checkRset != null && checkRset.next()) {
        exists = true;
        existingEncryptedPass = checkRset.getString("ftp_pass");
      }

      // ftp_pass 암호화 (null이면 기존 값 유지)
      String encryptedPass = "";
      if (ftpPass != null && !ftpPass.isEmpty()) {
        // 새로운 비밀번호가 입력되었으면 암호화
        encryptedPass = PasswordEncryptor.encrypt(ftpPass);
        log.debug(
            "FTP 비밀번호 암호화 완료 (원본 길이: {}, 암호화 길이: {})", ftpPass.length(), encryptedPass.length());
      } else if (exists) {
        // null이거나 빈 문자열이면 기존 암호화된 값 유지
        encryptedPass = existingEncryptedPass;
        log.debug("FTP 비밀번호 변경 없음 - 기존 암호화된 값 유지");
      }

      // 값을 이어 붙이지 않는다. 특히 ftp_pass 는 PasswordEncryptor 가 만든 Base64 라 '+' 와 '/' 가 섞여 있고,
      // 저장 경로·Public Key 도 사용자가 붙여 넣는 값이다. 따옴표가 하나만 들어와도 설정 저장이 통째로 깨지고
      // 그 자리는 그대로 주입 지점이 된다.
      //
      // 문자열 인자는 여기서 빈 문자열로 보정한다. 해당 컬럼들이 NOT NULL 이라 null 을 그대로 바인딩하면
      // 제약 위반으로 저장이 실패한다 — 이어 붙이던 시절에는 "null" 이라는 네 글자가 저장돼 실패처럼 보이지
      // 않았을 뿐이다. save_format 만 컬럼 기본값과 같은 'json' 으로 맞춘다 (빈 값이면 내보내기 포맷이 사라진다).
      String path = savePath != null ? savePath : "";
      String format = (saveFormat != null && !saveFormat.isEmpty()) ? saveFormat : "json";
      String address = ftpAddress != null ? ftpAddress : "";
      String id = ftpId != null ? ftpId : "";
      String pubKey = publicKey != null ? publicKey : "";
      // 기존 행의 ftp_pass 가 NULL 이면 위 유지 분기가 null 을 그대로 물고 온다.
      String pass = encryptedPass != null ? encryptedPass : "";

      if (exists) {
        // 업데이트
        String query =
            "UPDATE jbg_export_config SET save_path=?, save_format=?, auto_save_enabled=?,"
                + " save_to_jiniebox=?, ftp_address=?, ftp_id=?, ftp_pass=?, public_key=?,"
                + " ftp_encrypt_enabled=? WHERE id=1";

        log.debug(
            "LOCALDB-QUERY------------------------------------------------------------------------------");
        // 값은 싣지 않는다. 예전에는 조립된 쿼리를 찍어 암호화된 비밀번호가 그대로 로그에 남았다.
        log.debug(query);
        conn.txPstmtExecuteUpdate(
            query,
            path,
            format,
            autoSaveEnabled,
            saveToJiniebox,
            address,
            id,
            pass, // 암호화된 비밀번호 저장
            pubKey,
            ftpEncryptEnabled);
        log.info("파일 저장 설정 업데이트 완료 (FTP 비밀번호 암호화됨)");
      } else {
        // 삽입
        String query =
            "INSERT INTO jbg_export_config (id, save_path, save_format, auto_save_enabled,"
                + " save_to_jiniebox, ftp_address, ftp_id, ftp_pass, public_key,"
                + " ftp_encrypt_enabled) VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        log.debug(
            "LOCALDB-QUERY------------------------------------------------------------------------------");
        log.debug(query);
        conn.txPstmtExecuteUpdate(
            query,
            path,
            format,
            autoSaveEnabled,
            saveToJiniebox,
            address,
            id,
            pass, // 암호화된 비밀번호 저장
            pubKey,
            ftpEncryptEnabled);
        log.info("파일 저장 설정 생성 완료 (FTP 비밀번호 암호화됨)");
      }

      conn.txCommit();
    } catch (SQLException e) {
      log.error("설정 저장 중 DB 에러 발생");
      log.error(ExceptionUtil.getExceptionInfo(e));
      // 되돌리기를 인라인 txRollBack() 으로 하면 그것이 던지는 순간 원래 실패 원인(e)이 사라진다.
      // 여기는 FTP 비밀번호 암호화까지 묶인 다중 문장 트랜잭션이라 원인을 잃으면 추적이 어렵다.
      rollbackQuietly(conn);
      throw e;
    } catch (Exception e) {
      log.error("설정 저장 중 에러 발생");
      log.error(ExceptionUtil.getExceptionInfo(e));
      rollbackQuietly(conn);
      throw e;
    } finally {
      if (conn != null) conn.close();
    }
  }

  /**
   * 로컬 파일 저장이 성공한 시각을 기록한다 (판단 대기 10).
   *
   * <p>{@code last_export_time} 은 {@code schema.sql} 에 선언만 되어 있고 <b>읽지도 쓰지도 표시하지도 않는 죽은 컬럼</b>이었다.
   * 그래서 로컬 내보내기 파일이 11개나 쌓여 있는데도 값이 비어 있었다. "내보내기가 마지막으로 언제 됐나"를 확인할 방법이 없었다는 뜻이다.
   *
   * @param timeMillis 저장 성공 시각
   */
  public void touchLastExportTime(long timeMillis) {
    touchTime("last_export_time", timeMillis);
  }

  /**
   * FTP 전송이 성공한 시각을 기록한다 (판단 대기 10).
   *
   * <p>로컬 저장과 <b>따로</b> 센다. 두 경로는 각각의 설정 플래그({@code auto_save_enabled} / {@code save_to_jiniebox})로
   * 켜고 끌 수 있어 한쪽만 도는 구성이 정상이다. 하나의 시각으로 뭉치면 "파일은 저장되는데 전송만 죽은" 상태를 구분할 수 없다.
   *
   * @param timeMillis 전송 성공 시각
   */
  public void touchLastFtpUploadTime(long timeMillis) {
    touchTime("last_ftp_upload_time", timeMillis);
  }

  /** 시각 컬럼 하나를 갱신한다. 실패해도 예외를 밖으로 내지 않는다 — 기록 실패가 내보내기를 되돌릴 이유는 없다. */
  private void touchTime(String column, long timeMillis) {
    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();
      conn.txOpen();
      int updated =
          conn.txPstmtExecuteUpdate(
              "UPDATE jbg_export_config SET " + column + " = ? WHERE id = 1", timeMillis);
      if (updated == 0) {
        // 설정 행이 아직 없는 경우. 내보내기가 돌았다는 것은 경로 설정이 있었다는 뜻이라 드물지만,
        // 행이 없으면 UPDATE 는 조용히 0을 반환하므로 명시적으로 남긴다.
        log.debug("{} 갱신 대상 행이 없다 (jbg_export_config id=1 미생성)", column);
      }
      conn.txCommit();
    } catch (Exception e) {
      if (conn != null) {
        try {
          conn.txRollBack();
        } catch (Exception ignore) {
        }
      }
      log.warn("{} 갱신 실패: {}", column, e.getMessage());
    } finally {
      if (conn != null) {
        try {
          conn.close();
        } catch (Exception ignore) {
        }
      }
    }
  }

  // 여기 있던 ensureExportConfigTable 은 제거했다 (Phase 3-10).
  //
  // 테이블 생성 + 컬럼 6개(save_to_jiniebox / ftp_address / ftp_id / ftp_pass / public_key /
  // ftp_encrypt_enabled) 보정을 DAO 의 세 경로가 각자 호출하고 있었다. 게다가 여기 있던
  // CREATE TABLE 은 schema.sql 의 것과 이미 어긋나 있었다 — updated_time 과 last_export_time
  // 두 컬럼이 빠져 있었다. 복제된 DDL 은 이렇게 조용히 갈라진다.
  //
  // 6개 컬럼은 schema.sql 에 선언을 옮겼고, 보정은 SchemaMigrator 가 그 선언을 읽어 수행한다.

  /** 기본 설정 생성 및 반환 */
  private JSONObject createDefaultConfig() throws Exception {
    JSONObject config = new JSONObject();
    config.put("id", 1);
    config.put("save_path", "");
    config.put("save_format", "json");
    config.put("auto_save_enabled", 0);
    config.put("save_to_jiniebox", 0);
    config.put("ftp_address", "");
    config.put("ftp_id", "");
    config.put("has_ftp_password", false); // 비밀번호 없음
    config.put("public_key", "");
    config.put("ftp_encrypt_enabled", 1);
    config.put("last_export_time", 0L);
    config.put("last_ftp_upload_time", 0L);

    // DB에 기본값 삽입
    updateConfig("", "json", 0, 0, "", "", "", "", 1);

    return config;
  }

  /**
   * FTP 접속용 복호화된 비밀번호 조회 (내부 사용 전용 - 브라우저로 전송하지 않음)
   *
   * @return 복호화된 FTP 비밀번호 (없으면 빈 문자열)
   * @throws Exception
   */
  public String getDecryptedFtpPassword() throws Exception {
    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();

      StringBuffer querySb = new StringBuffer("SELECT ftp_pass FROM jbg_export_config WHERE id=1");
      log.debug(
          "LOCALDB-QUERY------------------------------------------------------------------------------");
      log.debug(querySb);
      ResultSet rset = conn.executeQuery(querySb.toString());

      if (rset != null && rset.next()) {
        String encryptedPass = rset.getString("ftp_pass");
        if (encryptedPass != null && !encryptedPass.isEmpty()) {
          String decryptedPass = PasswordEncryptor.decrypt(encryptedPass);
          log.debug("FTP 비밀번호 복호화 완료 (내부 사용)");
          return decryptedPass;
        }
      }
      return "";
    } catch (Exception e) {
      log.error("FTP 비밀번호 복호화 중 에러 발생");
      log.error(ExceptionUtil.getExceptionInfo(e));
      throw e;
    } finally {
      if (conn != null) {
        conn.close();
      }
    }
  }
}

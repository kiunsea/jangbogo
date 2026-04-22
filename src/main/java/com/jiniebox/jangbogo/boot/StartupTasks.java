package com.jiniebox.jangbogo.boot;

import com.jiniebox.jangbogo.dao.JbgMallDataAccessObject;
import com.jiniebox.jangbogo.dao.LocalDBConnection;
import com.jiniebox.jangbogo.svc.MallSchedulerService;
import com.jiniebox.jangbogo.svc.util.ScreenshotUtil;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 애플리케이션 시작 시 초기화 작업 수행 - 개별 쇼핑몰 스케줄링 복원 */
@Component
public class StartupTasks {
  private static final Logger logger = LogManager.getLogger(StartupTasks.class);

  @Autowired private MallSchedulerService mallSchedulerService;

  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    try {
      logger.info("장보고 애플리케이션 시작 - 초기화 작업 시작");

      // 0. DB 스키마 마이그레이션 (기존 사용자 데이터 보존하며 컬럼 추가)
      migrateCollectLogSchema();

      // 1. 스케줄링 대상 쇼핑몰에 대해 1회 수집 실행
      runInitialCollection();

      // 2. 개별 쇼핑몰 스케줄링 복원 (사용자가 설정한 주기대로 동작)
      restoreIndividualSchedules();

      // 3. 오래된 스크린샷 정리 (30일 이전)
      try {
        ScreenshotUtil.cleanupOldScreenshots(30);
      } catch (Exception cleanupEx) {
        logger.warn("스크린샷 보관기간 정리 실패: {}", cleanupEx.getMessage());
      }
    } catch (Exception e) {
      logger.error("시작 시 초기화 작업 실패", e);
    }
  }

  /**
   * jbg_collect_log 테이블에 신규 컬럼이 없으면 ALTER TABLE로 추가한다. SQLite 기준, 기존 데이터는 보존되며 nullable로 추가된다.
   *
   * <p>v0.8.0에서 추가된 컬럼: step_name, current_url, page_title, target_selector, screenshot_path
   */
  private void migrateCollectLogSchema() {
    String[] requiredColumns = {
      "step_name", "current_url", "page_title", "target_selector", "screenshot_path"
    };

    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();
      Set<String> existing = new HashSet<>();
      ResultSet rs = conn.executeQuery("PRAGMA table_info(jbg_collect_log)");
      while (rs != null && rs.next()) {
        existing.add(rs.getString("name"));
      }

      for (String col : requiredColumns) {
        if (!existing.contains(col)) {
          try {
            conn.txPstmtExecuteUpdate("ALTER TABLE jbg_collect_log ADD COLUMN " + col + " TEXT");
            logger.info("jbg_collect_log 컬럼 추가: {}", col);
          } catch (Exception alterEx) {
            logger.warn("jbg_collect_log 컬럼 {} 추가 실패: {}", col, alterEx.getMessage());
          }
        }
      }
    } catch (Exception e) {
      logger.warn("jbg_collect_log 스키마 마이그레이션 실패: {}", e.getMessage());
    } finally {
      if (conn != null) {
        try {
          conn.close();
        } catch (Exception ignore) {
        }
      }
    }
  }

  /** 애플리케이션 시작 시 스케줄링 대상 쇼핑몰에 대해 1회 수집 실행 (스케줄링 복원 전에 호출됨) */
  private void runInitialCollection() {
    try {
      logger.info("장보고 애플리케이션 시작 - 1회 수집 실행 중");

      JbgMallDataAccessObject jaDao = new JbgMallDataAccessObject();
      List<JSONObject> malls = jaDao.getAllMalls(false);

      int collectedCount = 0;
      for (JSONObject mall : malls) {
        try {
          Integer autoCollect = asInt(mall.get("auto_collect"));
          Integer intervalMinutes = asInt(mall.get("collect_interval_minutes"));
          String seq = str(mall.get("seq"));

          // auto_collect=1이고 주기가 설정된 쇼핑몰만 1회 수집
          if (autoCollect != null
              && autoCollect == 1
              && intervalMinutes != null
              && intervalMinutes > 0) {
            mallSchedulerService.runOneTimeCollection(seq);
            collectedCount++;
          }
        } catch (Exception ex) {
          logger.warn("쇼핑몰 seq={} 1회 수집 실패: {}", str(mall.get("seq")), ex.getMessage());
        }
      }

      if (collectedCount > 0) {
        logger.info("쇼핑몰 1회 수집 완료 (대상: {}개)", collectedCount);
      } else {
        logger.info("1회 수집 대상 쇼핑몰 없음");
      }
    } catch (Exception e) {
      logger.error("1회 수집 실행 실패", e);
    }
  }

  /** 애플리케이션 시작 시 DB에 저장된 개별 쇼핑몰 스케줄링 복원 */
  private void restoreIndividualSchedules() {
    try {
      JbgMallDataAccessObject jaDao = new JbgMallDataAccessObject();
      List<JSONObject> malls = jaDao.getAllMalls(false);

      int restoredCount = 0;
      for (JSONObject mall : malls) {
        try {
          Integer autoCollect = asInt(mall.get("auto_collect"));
          Integer intervalMinutes = asInt(mall.get("collect_interval_minutes"));
          String seq = str(mall.get("seq"));

          // auto_collect=1이고 주기가 설정된 쇼핑몰만 스케줄링
          if (autoCollect != null
              && autoCollect == 1
              && intervalMinutes != null
              && intervalMinutes > 0) {
            mallSchedulerService.scheduleMall(seq, intervalMinutes);
            restoredCount++;
            logger.info("쇼핑몰 seq={} 스케줄 복원 완료 (주기: {}분)", seq, intervalMinutes);
          }
        } catch (Exception ex) {
          logger.warn("쇼핑몰 스케줄 복원 실패: {}", ex.getMessage());
        }
      }

      if (restoredCount > 0) {
        logger.info("개별 쇼핑몰 스케줄 {}개 복원 완료", restoredCount);
      } else {
        logger.info("복원할 개별 쇼핑몰 스케줄 없음");
      }
    } catch (Exception e) {
      logger.error("개별 스케줄 복원 실패", e);
    }
  }

  // 유틸리티 메서드

  private Integer asInt(Object o) {
    if (o instanceof Number) return ((Number) o).intValue();
    try {
      return o != null ? Integer.parseInt(o.toString()) : null;
    } catch (Exception e) {
      return null;
    }
  }

  private String str(Object o) {
    return o == null ? "" : String.valueOf(o);
  }
}

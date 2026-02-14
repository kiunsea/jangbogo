package com.jiniebox.jangbogo.boot;

import com.jiniebox.jangbogo.dao.JbgMallDataAccessObject;
import com.jiniebox.jangbogo.svc.MallSchedulerService;
import java.util.List;
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

      // 1. 스케줄링 대상 쇼핑몰에 대해 1회 수집 실행
      runInitialCollection();

      // 2. 개별 쇼핑몰 스케줄링 복원 (사용자가 설정한 주기대로 동작)
      restoreIndividualSchedules();
    } catch (Exception e) {
      logger.error("시작 시 초기화 작업 실패", e);
    }
  }

  /**
   * 애플리케이션 시작 시 스케줄링 대상 쇼핑몰에 대해 1회 수집 실행
   * (스케줄링 복원 전에 호출됨)
   */
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

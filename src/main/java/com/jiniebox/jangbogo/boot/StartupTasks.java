package com.jiniebox.jangbogo.boot;

import com.jiniebox.jangbogo.dao.JbgMallDataAccessObject;
import com.jiniebox.jangbogo.dao.SchemaMigrator;
import com.jiniebox.jangbogo.svc.MallSchedulerService;
import com.jiniebox.jangbogo.svc.util.ScreenshotUtil;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 애플리케이션 시작 시 초기화 작업 수행 - 개별 쇼핑몰 스케줄링 복원 */
@Component
public class StartupTasks {
  private static final Logger logger = LogManager.getLogger(StartupTasks.class);

  @Autowired private MallSchedulerService mallSchedulerService;

  /**
   * 기동 시 수집(1회 수집 + 스케줄 복원) 활성화 여부.
   *
   * <p>기본값을 {@code false} 로 둔 것은 의도적이다. 이 프로퍼티가 정의되지 않은 컨텍스트 — 테스트({@code @SpringBootTest}), CI,
   * 컨텍스트만 띄우는 외부 도구 — 에서 자동으로 안전한 쪽으로 떨어져야 한다. {@code runInitialCollection()} 은 동기 호출이라 실계정 로그인과
   * 브라우저 수집이 끝날 때까지 기동을 붙잡는다. 수집을 켜는 것은 {@code src/main/resources/application.yml} 에서 명시적으로 선언한다.
   */
  @Value("${jangbogo.startup.collect.enabled:false}")
  private boolean startupCollectEnabled;

  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    try {
      logger.info("장보고 애플리케이션 시작 - 초기화 작업 시작");

      // 0. DB 스키마 보정 (기존 사용자 데이터 보존하며 없는 테이블·컬럼만 채운다)
      //    수집 가드 밖에 둔다. "앱은 띄우되 수집만 끈다" 를 해도 스키마는 최신이어야 한다.
      //
      //    이전에는 이 자리에 jbg_collect_log 전용 마이그레이션이 있었고, 그 안에 schema.sql 의
      //    CREATE TABLE 을 자바 문자열로 복제해 두고 있었다. 복제본은 원본과 어긋나기 마련이다.
      //    이제 SchemaMigrator 가 schema.sql 을 직접 읽어 대조하므로 선언은 한 곳뿐이다. (Phase 3-10)
      SchemaMigrator.ensureMigrated();

      if (startupCollectEnabled) {
        // 1. 스케줄링 대상 쇼핑몰에 대해 1회 수집 실행
        runInitialCollection();

        // 2. 개별 쇼핑몰 스케줄링 복원 (사용자가 설정한 주기대로 동작)
        restoreIndividualSchedules();
      } else {
        logger.info("기동 수집 비활성 (jangbogo.startup.collect.enabled=false) - 1회 수집·스케줄 복원을 건너뜁니다");
      }

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

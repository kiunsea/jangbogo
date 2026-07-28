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

      // 0. DB 스키마 마이그레이션 (기존 사용자 데이터 보존하며 컬럼 추가)
      //    수집 가드 밖에 둔다. "앱은 띄우되 수집만 끈다" 를 해도 스키마는 최신이어야 한다.
      migrateCollectLogSchema();

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

  /**
   * jbg_collect_log 테이블을 보장한다. 테이블이 없으면 생성하고, 신규 컬럼이 없으면 ALTER TABLE로 추가한다. SQLite 기준, 기존 데이터는 보존되며
   * nullable로 추가된다.
   *
   * <p>v0.8.0에서 추가된 컬럼: step_name, current_url, page_title, target_selector, screenshot_path
   *
   * <p>테이블 생성 자체는 {@code schema.sql} + {@code spring.sql.init} 이 담당하지만, 그 경로는 {@code
   * continue-on-error: true} 라 실패해도 조용히 넘어간다. 실제로 개발 트리 DB 에는 테이블이 없는 채로 남아 있었고, 이 메서드는 컬럼 추가만 하므로
   * '테이블 없음' 을 '컬럼 추가 실패' 경고로만 남기고 지나갔다. 수집 이력이 통째로 유실되는 경로이므로 여기서 직접 보장한다.
   */
  private void migrateCollectLogSchema() {
    String[] requiredColumns = {
      "step_name", "current_url", "page_title", "target_selector", "screenshot_path"
    };

    LocalDBConnection conn = null;
    try {
      conn = new LocalDBConnection();

      ensureCollectLogTable(conn);

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

  /**
   * jbg_collect_log 테이블이 없으면 생성한다. 컬럼 구성은 {@code src/main/resources/schema.sql} 과 동일하게 유지해야 한다.
   *
   * @param conn 로컬 DB 커넥션
   */
  private void ensureCollectLogTable(LocalDBConnection conn) {
    String createTable =
        "CREATE TABLE IF NOT EXISTS jbg_collect_log ("
            + "seq INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "seq_mall INTEGER NOT NULL, "
            + "mall_name TEXT, "
            + "status TEXT NOT NULL DEFAULT 'SUCCESS', "
            + "order_count INTEGER DEFAULT 0, "
            + "item_count INTEGER DEFAULT 0, "
            + "error_message TEXT, "
            + "error_detail TEXT, "
            + "step_name TEXT, "
            + "current_url TEXT, "
            + "page_title TEXT, "
            + "target_selector TEXT, "
            + "screenshot_path TEXT, "
            + "started_at INTEGER, "
            + "finished_at INTEGER, "
            + "insert_time INTEGER)";
    try {
      conn.txPstmtExecuteUpdate(createTable);
      logger.info("jbg_collect_log 테이블 확인/생성 완료");
    } catch (Exception e) {
      logger.warn("jbg_collect_log 테이블 생성 실패: {}", e.getMessage());
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

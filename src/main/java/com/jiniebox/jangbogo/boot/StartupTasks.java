package com.jiniebox.jangbogo.boot;

import com.jiniebox.jangbogo.dao.JbgMallDataAccessObject;
import com.jiniebox.jangbogo.dao.SchemaMigrator;
import com.jiniebox.jangbogo.svc.MallSchedulerService;
import com.jiniebox.jangbogo.svc.util.ExecutionContextDetector;
import com.jiniebox.jangbogo.svc.util.ScreenshotUtil;
import com.jiniebox.jangbogo.svc.util.SessionProfileGate;
import com.jiniebox.jangbogo.svc.util.SessionProfilePolicy;
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
   * 몰 목록 공급자. 운영에서는 DAO 를 그대로 부른다.
   *
   * <p>필드로 뽑은 이유는 테스트다. 두 메서드가 각자 DAO 를 만들어 쓰면 판정 하나를 보려고 실제 DB 가 있어야 한다.
   *
   * <p>람다로 감싼 것도 의도적이다. DAO 생성을 호출 시점까지 미뤄야 컨텍스트만 띄우는 자리에서 DB 를 건드리지 않는다.
   */
  private final MallSource mallSource;

  /**
   * 기동 시 수집(1회 수집 + 스케줄 복원) 활성화 여부.
   *
   * <p>기본값을 {@code false} 로 둔 것은 의도적이다. 이 프로퍼티가 정의되지 않은 컨텍스트 — 테스트({@code @SpringBootTest}), CI,
   * 컨텍스트만 띄우는 외부 도구 — 에서 자동으로 안전한 쪽으로 떨어져야 한다. {@code runInitialCollection()} 은 동기 호출이라 실계정 로그인과
   * 브라우저 수집이 끝날 때까지 기동을 붙잡는다. 수집을 켜는 것은 {@code src/main/resources/application.yml} 에서 명시적으로 선언한다.
   */
  @Value("${jangbogo.startup.collect.enabled:false}")
  private boolean startupCollectEnabled;

  /**
   * 이 부팅에서 판별한 실행 컨텍스트. 처음 필요할 때 한 번만 구한다.
   *
   * <p>{@code ExecutionContextDetector.detect()} 는 {@code tasklist} 프로세스를 띄우고 결과를 INFO 로 남긴다. 1회
   * 수집과 스케줄 복원이 같은 판정을 쓰게 되면서 옵트인한 몰마다 두 번씩 불리는데, 그러면 프로세스 기동과 로그 줄이 몰 수에 비례해 늘어난다. 사유 SKIPPED 를 한
   * 행으로 줄여 놓고 그 옆에 같은 판별 로그를 여러 줄 쌓으면 로그를 읽기 어렵게 만든 원래 문제로 되돌아간다.
   *
   * <p>캐시해도 되는 근거는 <b>실행 컨텍스트가 프로세스 수명 안에서 변할 수 없다</b>는 것이다 — 세션 0 에서 뜬 프로세스가 도중에 사람의 세션으로 옮겨가지
   * 않는다.
   */
  private ExecutionContextDetector.ExecutionContext executionContext;

  /**
   * Spring 이 쓰는 생성자. 협력자는 필드 주입으로 들어온다.
   *
   * <p>생성자가 둘인데 어느 쪽에도 {@code @Autowired} 가 없으면 Spring 은 기본 생성자를 고른다. 이것을 지우면 기동이 깨진다.
   */
  public StartupTasks() {
    this.mallSource = () -> new JbgMallDataAccessObject().getAllMalls(false);
  }

  /**
   * 테스트가 협력자를 갈아끼우기 위한 seam.
   *
   * <p>부팅 경로의 판정을 검증하려면 실제 수집이 돌면 안 된다 — 실계정 로그인과 브라우저가 뜬다.
   */
  StartupTasks(MallSchedulerService mallSchedulerService, MallSource mallSource) {
    this.mallSchedulerService = mallSchedulerService;
    this.mallSource = mallSource;
  }

  /**
   * 몰 목록 공급자의 타입. {@link java.util.function.Supplier} 를 쓰지 않는 이유가 있다.
   *
   * <p>{@code getAllMalls} 는 검사 예외를 던진다. {@code Supplier} 로 받으면 람다 안에서 그 예외를 삼키거나 {@code
   * RuntimeException} 으로 바꿔 던져야 하는데, 둘 다 이 자리에서 하면 안 되는 일이다 — 삼키면 DB 를 못 읽은 부팅이 "수집 대상 없음" 으로 보이고,
   * 바꿔 던지면 원인 예외가 한 겹 더 감싸져 로그에서 멀어진다.
   *
   * <p>검사 예외를 그대로 통과시키면 호출부의 {@code catch (Exception e)} 가 예전과 똑같이 받는다. 즉 seam 을 넣기 전과 실패 동작이 같다.
   */
  @FunctionalInterface
  interface MallSource {
    List<JSONObject> get() throws Exception;
  }

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

  /**
   * 애플리케이션 시작 시 스케줄링 대상 쇼핑몰에 대해 1회 수집 실행 (스케줄링 복원 전에 호출됨).
   *
   * <p>패키지 전용인 것은 테스트 때문이다. {@code onApplicationReady} 는 스키마 마이그레이션까지 함께 하므로 판정만 볼 수 없다.
   */
  void runInitialCollection() {
    try {
      logger.info("장보고 애플리케이션 시작 - 1회 수집 실행 중");

      List<JSONObject> malls = mallSource.get();

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

            // 세션 프로필을 쓰는 몰인데 여기가 세션 0 이면 1회 수집도 하지 않는다 (Phase 5-5 보완).
            //
            // 아무 로그도 남기지 않는 것이 핵심이다. runOneTimeCollection 을 그냥 부르면 공통
            // 게이트에 막혀 MallSchedulerService 가 SKIPPED 를 한 행 적고, 곧이어 도는
            // restoreIndividualSchedules 가 또 한 행을 적어 부팅 한 번에 두 행이 된다.
            //
            // 사유는 스케줄 복원 쪽 한 행으로 통일한다. 같은 사실을 두 번 적으면 수집 로그
            // 화면에서 몇 번 막혔는지 셀 수 없게 되는데, 사람이 그 표를 보는 이유가 그 횟수다.
            if (blockedBySessionProfile(mall)) {
              continue;
            }

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

  /**
   * 이 몰이 세션 프로필을 쓰는데 지금 자리에서는 브라우저를 띄울 수 없는지 (Phase 5-5).
   *
   * <p>킬스위치가 꺼져 있거나 옵트인하지 않았으면 항상 false 다 — 기존 동작이 그대로 유지된다.
   *
   * <p>1회 수집과 스케줄 복원이 <b>같은 판정</b>을 써야 한다. 한쪽만 막으면 그쪽이 수집기까지 내려가 SKIPPED 를 또 남긴다.
   */
  private boolean blockedBySessionProfile(JSONObject mall) {
    Integer optIn = asInt(mall.get("session_profile_enabled"));
    if (!SessionProfilePolicy.appliesTo(optIn != null && optIn == 1)) {
      return false;
    }
    return !executionContext().canRunHeadedBrowser();
  }

  /**
   * 실행 컨텍스트를 한 번만 판별해 재사용한다.
   *
   * <p>지연 초기화인 것은 의도적이다. 생성자에서 구하면 Spring 이 빈을 만드는 시점 — {@code ApplicationReadyEvent} 보다 한참 앞 — 에
   * {@code tasklist} 가 돌고, 수집을 아예 끈 기동에서도 그 비용과 로그가 생긴다. 옵트인한 몰이 하나도 없으면 여기는 한 번도 불리지 않는다.
   */
  private ExecutionContextDetector.ExecutionContext executionContext() {
    if (executionContext == null) {
      executionContext = ExecutionContextDetector.detect();
    }
    return executionContext;
  }

  /**
   * 애플리케이션 시작 시 DB에 저장된 개별 쇼핑몰 스케줄링 복원.
   *
   * <p>패키지 전용인 이유는 {@link #runInitialCollection()} 과 같다 — 테스트가 따로 부른다.
   */
  void restoreIndividualSchedules() {
    try {
      List<JSONObject> malls = mallSource.get();

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

            // 세션 프로필을 쓰는 몰인데 여기가 세션 0 이면 등록하지 않는다 (Phase 5-5).
            //
            // 등록해 봤자 매 회차가 게이트에 막혀 SKIPPED 만 쌓인다. 12시간마다 같은 줄이
            // 반복되면 로그가 무의미해지고, 정작 사람이 봐야 할 때 눈에 띄지 않는다.
            // 한 번만 남기고 등록을 건너뛴다.
            if (blockedBySessionProfile(mall)) {
              logger.info("쇼핑몰 seq={} 세션 프로필 몰인데 세션 0 이라 스케줄을 등록하지 않는다", seq);
              mallSchedulerService.logSessionProfileSkip(
                  seq, str(mall.get("name")), SessionProfileGate.Decision.REQUIRES_USER_SESSION);
              continue;
            }

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

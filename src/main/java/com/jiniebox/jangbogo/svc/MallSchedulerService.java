package com.jiniebox.jangbogo.svc;

import com.jiniebox.jangbogo.dao.JbgCollectLogDataAccessObject;
import com.jiniebox.jangbogo.dao.JbgMallDataAccessObject;
import com.jiniebox.jangbogo.dto.MallAccount;
import com.jiniebox.jangbogo.svc.util.CollectIntervalPolicy;
import com.jiniebox.jangbogo.svc.util.CollectTrigger;
import com.jiniebox.jangbogo.svc.util.ErrorSummary;
import com.jiniebox.jangbogo.svc.util.FtpPendingQueue;
import com.jiniebox.jangbogo.svc.util.SessionProfileGate;
import com.jiniebox.jangbogo.util.ExceptionUtil;
import com.jiniebox.jangbogo.util.StringEncrypter;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 각 쇼핑몰별 자동수집 스케줄링 관리 서비스
 *
 * @author KIUNSEA
 */
@Service
public class MallSchedulerService {

  private static final Logger logger = LogManager.getLogger(MallSchedulerService.class);

  @Autowired private JangBoGoManager jangBoGoManager;

  @Autowired private MallAccountYmlService mallAccountYmlService;

  @Autowired private ExportService exportService;

  // 쇼핑몰별 스케줄 관리 맵 (seq -> ScheduledFuture)
  private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

  /**
   * 세션 만료로 일시중단된 쇼핑몰 (seq -> 사유) (Phase 5-10).
   *
   * <h2>왜 스케줄을 취소하지 않는가</h2>
   *
   * <p>{@link ScheduledFuture#cancel} 로 끄면 <b>되살릴 주체가 없다.</b> 등록 정보는 {@link #scheduledTasks} 에서
   * 사라지고 {@link #isScheduled} 는 false 를 돌려주므로, 사용자가 세션을 다시 떠도 화면은 "주기 수집 꺼짐" 으로 보이고 다음 기동까지 복구되지
   * 않는다. 그래서 스케줄은 그대로 두고 <b>진입 지점에서 건너뛴다</b> — 되돌리는 것이 맵에서 한 줄 지우는 일이면 복구 경로가 실수할 여지가 없다.
   *
   * <h2>{@code auto_collect} 컬럼은 절대 건드리지 않는다</h2>
   *
   * <p>DB 로 일시중단을 표현하려면 그 컬럼을 눌러야 하는데, 되돌리는 유일한 통로인 {@code
   * JbgMallDataAccessObject.saveAutoCollectFlags} 는 <b>전체를 0 으로 초기화한 뒤 선택분만 되돌린다.</b> 즉 그 경로가 한 번
   * 돌면 사용자가 켜 둔 다른 몰의 설정까지 통째로 날아간다. 일시중단은 이번 프로세스의 런타임 사실이지 사용자 설정이 아니므로 메모리에만 둔다.
   *
   * <h2>몰 단위 상태 컬럼에도 적지 않는다</h2>
   *
   * <p>{@code jbg_mall.session_profile_status} 는 캡처 성공 시 {@code READY} 만 쓰는 현행을 유지한다. 만료를 그쪽에도 두면 두
   * 곳이 같은 사실을 들고 있게 되는데, 갱신 주체가 없는 쪽이 굳어 화면과 동작이 갈린다. 만료는 <b>수집 로그의 사유</b>로만 표현한다.
   */
  private final Map<String, String> sessionExpiredMalls = new ConcurrentHashMap<>();

  // 스케줄러 (쓰레드 풀 크기 5)
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);

  /**
   * 특정 쇼핑몰의 자동수집 스케줄링 시작/업데이트
   *
   * @param seq 쇼핑몰 시퀀스
   * @param intervalMinutes 주기 (분 단위)
   */
  public void scheduleMall(String seq, int intervalMinutes) {
    if (seq == null || seq.isEmpty()) {
      logger.warn("스케줄 등록 실패: seq가 null이거나 비어있음");
      return;
    }

    // 기존 스케줄이 있으면 취소
    cancelMall(seq);

    // 주기가 0 이하면 스케줄링하지 않음
    if (intervalMinutes <= 0) {
      logger.info("쇼핑몰 seq={} 주기={}분, 스케줄링 안함", seq, intervalMinutes);
      return;
    }

    // 하한 강제. 저장 시점 검증 이전에 DB 에 들어간 값이 남아 있을 수 있으므로,
    // 조용히 쓰지 않고 경고를 남긴 뒤 하한으로 올린다.
    intervalMinutes = CollectIntervalPolicy.clampForSchedule(seq, intervalMinutes);

    // 새로운 스케줄 시작
    ScheduledFuture<?> future =
        scheduler.scheduleAtFixedRate(
            () -> {
              try {
                logger.info("쇼핑몰 seq={} 예약 수집 시작", seq);
                runCollectForMall(seq);
                logger.info("쇼핑몰 seq={} 예약 수집 완료", seq);
              } catch (Exception e) {
                logger.error("쇼핑몰 seq={} 예약 수집 오류: {}", seq, e.getMessage(), e);
              }
            },
            intervalMinutes, // 초기 지연 (첫 실행까지 주기만큼 대기)
            intervalMinutes, // 주기
            TimeUnit.MINUTES);

    scheduledTasks.put(seq, future);
    logger.info("쇼핑몰 seq={} 스케줄 등록 완료 (주기: {}분)", seq, intervalMinutes);
  }

  /**
   * 특정 쇼핑몰 즉시 실행 후 스케줄링 시작
   *
   * @param seq 쇼핑몰 시퀀스
   * @param intervalMinutes 주기 (분 단위)
   */
  public void scheduleMallImmediate(String seq, int intervalMinutes) {
    if (seq == null || seq.isEmpty()) {
      logger.warn("즉시 스케줄 등록 실패: seq가 null이거나 비어있음");
      return;
    }

    // 기존 스케줄이 있으면 취소
    cancelMall(seq);

    // 주기가 0 이하면 스케줄링하지 않음
    if (intervalMinutes <= 0) {
      logger.info("쇼핑몰 seq={} 주기={}분, 즉시 스케줄링 안함", seq, intervalMinutes);
      return;
    }

    // 하한 강제 (scheduleMall 과 동일 규칙)
    intervalMinutes = CollectIntervalPolicy.clampForSchedule(seq, intervalMinutes);

    // 새로운 스케줄 시작 (초기 지연 0으로 즉시 실행)
    ScheduledFuture<?> future =
        scheduler.scheduleAtFixedRate(
            () -> {
              try {
                logger.info("쇼핑몰 seq={} 예약 수집 시작", seq);
                runCollectForMall(seq);
                logger.info("쇼핑몰 seq={} 예약 수집 완료", seq);
              } catch (Exception e) {
                logger.error("쇼핑몰 seq={} 예약 수집 오류: {}", seq, e.getMessage(), e);
              }
            },
            0, // 초기 지연 0 (즉시 실행)
            intervalMinutes, // 주기
            TimeUnit.MINUTES);

    scheduledTasks.put(seq, future);
    logger.info("쇼핑몰 seq={} 즉시 스케줄 등록 완료 (주기: {}분)", seq, intervalMinutes);
  }

  /**
   * 특정 쇼핑몰의 스케줄링 취소
   *
   * @param seq 쇼핑몰 시퀀스
   */
  public void cancelMall(String seq) {
    if (seq == null || seq.isEmpty()) {
      return;
    }

    ScheduledFuture<?> future = scheduledTasks.remove(seq);
    if (future != null && !future.isCancelled()) {
      future.cancel(false);
      logger.info("쇼핑몰 seq={} 스케줄 취소 완료", seq);
    }
  }

  /**
   * 특정 쇼핑몰이 현재 스케줄링 중인지 확인
   *
   * @param seq 쇼핑몰 시퀀스
   * @return 스케줄링 중이면 true
   */
  public boolean isScheduled(String seq) {
    if (seq == null || seq.isEmpty()) {
      logger.debug("isScheduled({}): seq가 null이거나 비어있음, false 반환", seq);
      return false;
    }

    ScheduledFuture<?> future = scheduledTasks.get(seq);

    if (future == null) {
      logger.debug("isScheduled({}): 스케줄 없음, false 반환", seq);
      return false;
    }

    boolean isCancelled = future.isCancelled();
    boolean isDone = future.isDone();
    boolean isScheduled = !isCancelled && !isDone;

    logger.debug(
        "isScheduled({}): 스케줄 존재, 취소됨={}, 완료됨={}, 결과={}", seq, isCancelled, isDone, isScheduled);

    return isScheduled;
  }

  /** 모든 스케줄링 취소 */
  public void cancelAll() {
    logger.info("모든 스케줄 작업 취소 중");
    for (Map.Entry<String, ScheduledFuture<?>> entry : scheduledTasks.entrySet()) {
      ScheduledFuture<?> future = entry.getValue();
      if (future != null && !future.isCancelled()) {
        future.cancel(false);
      }
    }
    scheduledTasks.clear();
  }

  /**
   * 특정 쇼핑몰에 대해 1회성 수집 실행 (애플리케이션 시작 시 초기 수집용)
   *
   * @param seq 쇼핑몰 시퀀스
   */
  public void runOneTimeCollection(String seq) {
    try {
      runCollectForMall(seq);
    } catch (Exception e) {
      logger.error("쇼핑몰 seq={} 1회 수집 오류: {}", seq, e.getMessage(), e);
    }
  }

  /**
   * 특정 쇼핑몰 수집 실행
   *
   * @param seq 쇼핑몰 시퀀스
   */
  private void runCollectForMall(String seq) {
    long startedAt = System.currentTimeMillis();
    String mallName = null;
    int seqInt = 0;
    try {
      seqInt = Integer.parseInt(seq);
    } catch (NumberFormatException ignore) {
    }

    // 세션 만료 일시중단 — 이것이 그 '진입 지점' 이다 (Phase 5-10).
    //
    // 여기서 물러나므로 브라우저도 뜨지 않고 몰 사이트도 두드리지 않는다. 스케줄 자체는 살아 있어
    // 사용자가 세션을 다시 뜨는 순간 원래 주기로 돌아온다 (sessionExpiredMalls javadoc 참조).
    //
    // 로그를 남기지 않는 것이 핵심이다. 사유는 만료를 관측한 그 회차에 이미 한 행 적혔고, 여기서
    // 또 적으면 주기마다 같은 줄이 쌓여 수집 로그 화면이 무의미해진다 — 사람이 그 표를 보는 이유가
    // 몇 번 막혔는지를 세기 위해서다. StartupTasks 가 같은 이유로 같은 선택을 하고 있다.
    if (isSuspendedForExpiredSession(seq)) {
      logger.debug("쇼핑몰 seq={} 세션 만료로 일시중단 중 — 이번 회차 건너뜀 ({})", seq, sessionExpiredMalls.get(seq));
      return;
    }

    try {
      JbgMallDataAccessObject jaDao = new JbgMallDataAccessObject();
      JSONObject mall = jaDao.getMall(seq);

      if (mall == null) {
        logger.warn("쇼핑몰 seq={} DB에서 찾을 수 없음", seq);
        saveFailLog(seqInt, null, "DB에서 쇼핑몰을 찾을 수 없음 (seq=" + seq + ")", null, startedAt);
        return;
      }

      mallName = str(mall.get("name"));

      // 세션 프로필 게이트는 여기 있었지만 공통 통로로 옮겼다 (Phase 5-19).
      //
      // 즉시수집이 이 판정을 우회하고 있었고, 규약을 호출부가 지키게 두는 한 두 경로는 다시 갈린다.
      // 이제 JangBoGoManager.collect 안에서 게이트와 브라우저 동시 실행 제한을 함께 본다.
      // 막힌 사유는 아래에서 outcome 으로 받아 그대로 SKIPPED 로 남긴다.

      // 수집 자격 판정도 같은 이유로 옮겼다 (Phase 5-20).
      //
      // account_status·암호화 키/IV·저장된 계정을 여기서 한 벌, 즉시수집(AdminController)에서 또 한 벌
      // 보고 있었고 두 벌은 이미 갈려 있었다. 더 나쁜 것은 즉시수집 쪽이 건너뛸 때 account_status 를
      // 0 으로 눌러 연결을 끊었다는 점이다 — 여기만 고치면 즉시수집 한 번에 원복된다.
      //
      // 계정은 값이 아니라 공급자로 넘긴다. account_status 나 키에서 이미 걸리는 회차에서는
      // mall_account.yml 을 읽지 않는다 — 예전 판정 순서가 정확히 그랬다.
      String encKeyBase64 = str(mall.get("encrypt_key"));
      String encIvBase64 = str(mall.get("encrypt_iv"));

      JangBoGoManager.CollectEligibility eligibility =
          JangBoGoManager.qualify(
              mall,
              () -> mallAccountYmlService.getAccountBySeq(seq).orElse(null),
              CollectTrigger.SCHEDULED);

      if (!eligibility.qualified()) {
        if (eligibility.worthLogging()) {
          logger.warn("쇼핑몰 seq={} {}", seq, eligibility.reason());
          saveFailLog(seqInt, mallName, eligibility.reason(), null, startedAt);
        } else {
          // 연결한 적 없는 몰은 예전에도 조용히 물러났다. 기록으로 승격시키면 주기마다 같은 줄이 쌓인다.
          logger.debug("쇼핑몰 seq={} {}, 건너뜀", seq, eligibility.reason());
        }
        return;
      }

      // 수집 실행 (동기 실행하여 신규 주문 seq 수집).
      //
      // 복호화를 값이 아니라 공급자로 넘긴다 (Phase 5-19). 게이트나 브라우저 자리에 막히는 회차에서는
      // 이 람다가 호출되지 않으므로 비밀번호가 메모리에 오르지 않는다.
      //
      // 세션 경로면 복호화할 것 자체가 없으므로 공급자를 만들지 않는다 (Phase 5-20).
      logger.info("쇼핑몰 seq={} 구매내역 수집 시작", seq);
      MallCollectOutcome outcome =
          jangBoGoManager.collect(
              mall,
              eligibility,
              eligibility.needsCredentials()
                  ? () -> decryptStoredAccount(encKeyBase64, encIvBase64, eligibility.account())
                  : null,
              CollectTrigger.SCHEDULED);

      if (outcome.sessionExpired()) {
        // 세션이 죽었다. 한 행 남기고 이 몰을 일시중단한다 (Phase 5-10).
        //
        // 실패가 아니므로 FAIL 로 적지 않는다 — FAIL 로 적으면 화면 집계가 사이트 장애로 세고,
        // 무엇보다 이 사실이 브레이커로 흘러가면(MallOrderUpdater.recordBreaker) 연속 실패가 쌓여
        // 수집기가 자동 차단된다. 사람이 세션을 다시 떠도 쿨다운이 끝날 때까지 돌지 않는다.
        suspendForExpiredSession(seq, mallName, outcome.reason(), startedAt);
        return;
      }

      if (!outcome.collected()) {
        // 막힌 것은 실패가 아니다. 사유만 남기고 물러난다.
        logger.info("쇼핑몰 seq={} 수집 건너뜀 — {} ({})", seq, outcome.code(), outcome.reason());
        saveSkipLog(
            seqInt, mallName, outcome.code(), outcome.reason(), outcome.stepName(), startedAt);
        return;
      }

      List<Integer> newOrderSeqs = outcome.newOrderSeqs();

      // 파일 저장 및 FTP 업로드 처리
      if (!newOrderSeqs.isEmpty() || shouldProcessFileExport(seq)) {
        processFileExport(seq, newOrderSeqs);
      }

    } catch (Exception e) {
      logger.error("쇼핑몰 seq={} 수집 실행 중 오류: {}", seq, e.getMessage(), e);
      // CollectException이면 컨텍스트 추출
      CollectException ce = unwrapCollectException(e);
      JbgCollectLogDataAccessObject logDao = new JbgCollectLogDataAccessObject();
      logDao.addLog(
          seqInt,
          mallName,
          "FAIL",
          0,
          0,
          ErrorSummary.summarize(e),
          ExceptionUtil.getExceptionInfo(e),
          ce != null ? ce.getStepName() : "scheduler",
          ce != null ? ce.getCurrentUrl() : null,
          ce != null ? ce.getPageTitle() : null,
          ce != null ? ce.getTargetSelector() : null,
          ce != null ? ce.getScreenshotPath() : null,
          startedAt,
          System.currentTimeMillis());
    }
  }

  /**
   * 저장된 암호문을 복호화한다.
   *
   * <p>{@code MallCredentialSupplier} 로 감싸 넘긴다 — 게이트나 브라우저 자리에 막히는 회차에서는 호출되지 않아 비밀번호가 메모리에 오르지
   * 않는다.
   *
   * <p>실패는 예외로 올린다. 이쪽은 실제로 계정 문제가 맞다.
   */
  private static MallCredentials decryptStoredAccount(
      String encKeyBase64, String encIvBase64, MallAccount account) throws Exception {

    SecretKey secKey = StringEncrypter.decodeBase64ToSecretKey(encKeyBase64.trim());
    IvParameterSpec ivSpec = StringEncrypter.decodeBase64ToIv(encIvBase64.trim());

    String usrid =
        StringEncrypter.decrypt(StringEncrypter.ALGORITHM, account.getId(), secKey, ivSpec);
    String usrpw =
        StringEncrypter.decrypt(StringEncrypter.ALGORITHM, account.getPass(), secKey, ivSpec);

    if (usrid != null && usrid.startsWith("%")) usrid = usrid.substring(1);
    if (usrpw != null && usrpw.startsWith("%")) usrpw = usrpw.substring(1);

    if (usrid == null || usrpw == null) {
      throw new IllegalStateException("계정 복호화 실패");
    }
    return new MallCredentials(usrid, usrpw);
  }

  /** 예외 체인에서 첫 번째 CollectException 찾기 */
  private CollectException unwrapCollectException(Throwable t) {
    Throwable cur = t;
    int safety = 0;
    while (cur != null && safety++ < 20) {
      if (cur instanceof CollectException) {
        return (CollectException) cur;
      }
      cur = cur.getCause();
    }
    return null;
  }

  /**
   * 세션 프로필 때문에 스케줄을 등록하지 않았음을 기록한다 (Phase 5-5).
   *
   * <p>기동 시 한 번만 남긴다. 등록해 두고 매 회차 막히게 두면 같은 줄이 12시간마다 쌓여 로그가 무의미해진다.
   */
  public void logSessionProfileSkip(String seq, String mallName, SessionProfileGate.Decision gate) {
    int seqInt = 0;
    try {
      seqInt = Integer.parseInt(seq);
    } catch (NumberFormatException ignore) {
    }
    long now = System.currentTimeMillis();
    saveSkipLog(
        seqInt,
        mallName,
        gate.name(),
        gate.reason(),
        MallCollectOutcome.STEP_SESSION_PROFILE_GATE,
        now);
  }

  /**
   * 세션 만료를 기록하고 이 몰의 수집을 일시중단한다 (Phase 5-10).
   *
   * <p>기록은 <b>이미 있는 {@link #saveSkipLog} 통로</b>를 그대로 탄다. 새 통로를 파지 않는 이유는 상태값 때문이다 — 만료는 {@code
   * SKIPPED} 이고 사유 코드가 {@code SESSION_EXPIRED} 다. 만료 전용 status 를 만들면 {@code
   * JbgCollectLogDataAccessObject} 의 실패 목록({@code status='FAIL'})과 요약 질의를 둘 다 고쳐야 하고, 안 고치면 만료가
   * 화면에서 조용히 사라진다.
   *
   * <p><b>스케줄 수집과 즉시수집이 같은 메서드를 부른다.</b> 두 경로가 각자 기록하면 코드·단계·중단 여부가 언젠가 갈리는데, 이 프로젝트는 그 형태의 결함을 이미
   * 두 번 고쳤다(5-18 게이트, 5-19 즉시수집 우회). 규약을 호출부가 지키게 두면 두 경로는 갈린다.
   *
   * <p>같은 몰이 이미 중단돼 있으면 <b>다시 적지 않는다.</b> 사용자가 즉시수집을 연달아 누르면 같은 사실이 그만큼 쌓이는데, 그 표를 보는 이유가 몇 번 막혔는지를
   * 세기 위해서다.
   *
   * @param seq 쇼핑몰 시퀀스
   * @param mallName 쇼핑몰 이름 (로그 표시용). 모르면 null
   * @param reason 사람이 읽을 사유. 비어 있으면 결과값이 실어 준 기본 문구가 들어온다
   * @param startedAt 이 회차의 시작 시각
   */
  public void suspendForExpiredSession(String seq, String mallName, String reason, long startedAt) {
    if (seq == null || seq.isEmpty()) {
      return;
    }
    String text =
        (reason == null || reason.isBlank()) ? MallCollectOutcome.SESSION_EXPIRED_CODE : reason;
    String previous = sessionExpiredMalls.putIfAbsent(seq, text);
    if (previous != null) {
      logger.debug("쇼핑몰 seq={} 이미 세션 만료로 중단 중 — 같은 사유를 다시 적지 않는다", seq);
      return;
    }

    logger.warn("쇼핑몰 seq={} 세션 만료로 자동수집을 일시중단한다 — {}", seq, text);
    saveSkipLog(
        parseSeq(seq),
        mallName,
        MallCollectOutcome.SESSION_EXPIRED_CODE,
        text,
        MallCollectOutcome.STEP_SESSION_EXPIRY,
        startedAt);
  }

  /**
   * 이 몰이 세션 만료로 일시중단돼 있는가 (Phase 5-10).
   *
   * @param seq 쇼핑몰 시퀀스
   * @return 중단 중이면 true
   */
  public boolean isSuspendedForExpiredSession(String seq) {
    return seq != null && !seq.isEmpty() && sessionExpiredMalls.containsKey(seq);
  }

  /**
   * 세션 만료 일시중단을 푼다 (Phase 5-10).
   *
   * <p><b>사람이 무언가를 한 순간에만 부른다.</b> 세션이 되살아났는지는 실제로 주입해 봐야만 알 수 있어서, 시간이 지났다는 이유로 스스로 풀면 죽은 세션으로 몰을
   * 계속 두드리게 된다. 반대로 아무도 풀지 않으면 사용자가 다시 로그인해도 다음 기동까지 수집이 돌아오지 않는다.
   *
   * <p>그래서 지금 부르는 곳은 <b>사용자가 그 몰을 직접 골라 보낸 자동수집 요청</b>({@code AdminController.autoCollectSelected})
   * 하나다. 세션 캡처를 마친 직후에도 풀리는 것이 자연스러운데, 그 호출은 캡처 완료 경로에 붙어야 한다.
   *
   * @param seq 쇼핑몰 시퀀스
   * @return 실제로 중단 중이어서 풀었으면 true
   */
  public boolean resumeAfterSessionRecovery(String seq) {
    if (seq == null || seq.isEmpty()) {
      return false;
    }
    boolean released = sessionExpiredMalls.remove(seq) != null;
    if (released) {
      logger.info("쇼핑몰 seq={} 세션 만료 일시중단 해제 — 다음 회차부터 예정대로 수집한다", seq);
    }
    return released;
  }

  /** 문자열 seq 를 로그 저장용 정수로. 숫자가 아니면 0 이다(예전 저장 형태 그대로). */
  private static int parseSeq(String seq) {
    try {
      return Integer.parseInt(seq.trim());
    } catch (RuntimeException ignore) {
      return 0;
    }
  }

  /**
   * 수집을 건너뛴 것을 기록한다 (Phase 5-4, 5-19 에서 확대).
   *
   * <p>실패가 아니라 <b>건너뜀</b>이다. 막은 것은 우리 쪽 조건이지 사이트가 아니다. {@code SKIPPED} 는 수집 로그 화면의 성공·실패 집계와 실패
   * 목록에서 모두 빠진다({@code JbgCollectLogDataAccessObject} 의 요약 질의가 {@code SUCCESS}·{@code FAIL} 만 센다).
   *
   * <p>(정정) 예전 주석은 "FAIL 로 적으면 브레이커가 연속 실패로 세어 몰을 차단한다" 고 적었지만 그렇지 않다. 브레이커의 입력은 {@code
   * jbg_collect_breaker} 뿐이고 그 표는 수집기가 실제로 돈 회차에만 갱신된다 — 이 표를 읽지 않는다. 실제 차이는 화면 집계다.
   *
   * <p>단계 이름을 인자로 받는 이유는 5-19 다. 게이트 말고도 브라우저 자리 부족·중복 실행이 이 통로로 들어오는데, 단계를 게이트로 박아 두면 세션 프로필을 켠 적
   * 없는 사용자가 그 기능에서 원인을 찾게 된다.
   *
   * <p><b>세션 만료(5-10)만은 위 (정정)이 그대로 적용되지 않는다.</b> 만료는 <b>수집기가 실제로 돈</b> 회차에 드러나므로 브레이커 표가 갱신되는
   * 구간이다. 그래서 그것을 {@code FAIL} 로 적으면 {@code MallOrderUpdater.recordBreaker} 가 연속 실패로 세어 브레이커가 열리고,
   * 사람이 세션을 다시 떠도 쿨다운이 끝날 때까지 그 수집기는 돌지 않는다 — 사이트 장애가 아닌 일로 수집이 자동 차단된다. 만료가 반드시 이 통로로 와야 하는 이유다.
   */
  private void saveSkipLog(
      int seqMall, String mallName, String code, String reason, String step, long startedAt) {
    try {
      logWriter.write(seqMall, mallName, LOG_STATUS_SKIPPED, code, reason, step, startedAt);
    } catch (Exception e) {
      logger.warn("건너뜀 로그 저장 실패: {}", e.getMessage());
    }
  }

  /** 실패 로그를 DB에 저장 (간단 버전 — step="scheduler-precheck") */
  private void saveFailLog(
      int seqMall, String mallName, String errorMessage, String errorDetail, long startedAt) {
    try {
      logWriter.write(
          seqMall,
          mallName,
          LOG_STATUS_FAIL,
          errorMessage,
          errorDetail,
          STEP_SCHEDULER_PRECHECK,
          startedAt);
    } catch (Exception e) {
      logger.warn("실패 로그 저장 중 오류: {}", e.getMessage());
    }
  }

  /**
   * 수집 로그 한 행을 실제로 남기는 자리 (Phase 5-10).
   *
   * <h2>왜 갈아 끼울 수 있게 했나</h2>
   *
   * <p>"세션 만료를 {@code FAIL} 이 아니라 {@code SKIPPED} 로 적는다" 는 이 국면의 핵심 규칙인데, 그것을 확인하려면 <b>무엇이
   * 적혔는지</b>를 봐야 한다. 그런데 기본 구현은 사용자의 실제 DB 에 쓴다 — 테스트가 그것을 돌리면 실계정 수집 이력에 가짜 행이 섞이고, 최악의 경우 없던 DB
   * 파일을 만든다. 그래서 저장 지점을 하나로 모으고 테스트에서만 갈아 끼운다.
   *
   * <p>규칙이 깨지는 형태는 <b>상태 문자열 한 개</b>다. 그것을 실행으로 잡을 수 있는 유일한 방법이 이 이음매다.
   */
  @FunctionalInterface
  interface CollectLogWriter {

    /**
     * 수집 로그 한 행.
     *
     * @param seqMall 쇼핑몰 seq (숫자가 아니면 0)
     * @param mallName 쇼핑몰 이름. 모르면 null
     * @param status {@code SUCCESS} / {@code FAIL} / {@code SKIPPED}
     * @param code 사유 코드
     * @param reason 사람이 읽을 사유
     * @param step 단계 이름
     * @param startedAt 회차 시작 시각
     * @throws Exception 저장 실패. 호출부가 삼킨다 — 로그를 못 남긴 것이 수집을 막아서는 안 된다
     */
    void write(
        int seqMall,
        String mallName,
        String status,
        String code,
        String reason,
        String step,
        long startedAt)
        throws Exception;
  }

  /** 수집 로그 상태 — 건너뜀. 화면의 성공·실패 집계와 실패 목록에서 모두 빠진다. */
  static final String LOG_STATUS_SKIPPED = "SKIPPED";

  /** 수집 로그 상태 — 실패. <b>세션 만료를 여기에 넣으면 안 된다</b> (saveSkipLog javadoc 참조). */
  static final String LOG_STATUS_FAIL = "FAIL";

  /** 수집 시작 전 검사에서 걸린 실패의 단계 이름. */
  static final String STEP_SCHEDULER_PRECHECK = "scheduler-precheck";

  /** 기록 지점. 기본은 DB 다. */
  private volatile CollectLogWriter logWriter = MallSchedulerService::writeCollectLogToDatabase;

  /**
   * 기록 지점을 갈아 끼운다 — <b>테스트 전용</b>.
   *
   * @param writer 갈아 끼울 기록기. null 이면 DB 기본 구현으로 되돌린다
   */
  void useLogWriter(CollectLogWriter writer) {
    this.logWriter = (writer == null) ? MallSchedulerService::writeCollectLogToDatabase : writer;
  }

  /** 기본 기록 구현. 예전 {@code addLog} 호출을 그대로 옮겼다. */
  private static void writeCollectLogToDatabase(
      int seqMall,
      String mallName,
      String status,
      String code,
      String reason,
      String step,
      long startedAt) {

    new JbgCollectLogDataAccessObject()
        .addLog(
            seqMall,
            mallName,
            status,
            0,
            0,
            code,
            reason,
            step,
            null,
            null,
            null,
            null,
            startedAt,
            System.currentTimeMillis());
  }

  /**
   * 파일 저장/FTP 업로드가 필요한지 확인
   *
   * @param seq 쇼핑몰 시퀀스
   * @return 파일 저장/FTP 업로드 필요 여부
   */
  private boolean shouldProcessFileExport(String seq) {
    try {
      com.jiniebox.jangbogo.dao.JbgExportConfigDataAccessObject exportConfigDao =
          new com.jiniebox.jangbogo.dao.JbgExportConfigDataAccessObject();
      org.json.simple.JSONObject exportConfig = exportConfigDao.getConfig();

      Integer autoSaveEnabled =
          exportConfig.get("auto_save_enabled") != null
              ? Integer.parseInt(exportConfig.get("auto_save_enabled").toString())
              : 0;

      Integer saveToJinieboxCfg = null;
      if (exportConfig.get("save_to_jiniebox") != null) {
        saveToJinieboxCfg = Integer.parseInt(exportConfig.get("save_to_jiniebox").toString());
      } else if (exportConfig.get("save_to_ftp") != null) {
        saveToJinieboxCfg = Integer.parseInt(exportConfig.get("save_to_ftp").toString());
      }
      int saveToJinieboxVal = (saveToJinieboxCfg != null) ? saveToJinieboxCfg : 0;

      return (autoSaveEnabled == 1) || (saveToJinieboxVal == 1);
    } catch (Exception e) {
      logger.warn("파일 저장 설정 확인 중 오류: {}", e.getMessage());
      return false;
    }
  }

  /**
   * 파일 저장 및 FTP 업로드 처리
   *
   * @param seq 쇼핑몰 시퀀스
   * @param newOrderSeqs 신규 주문 seq 목록
   */
  private void processFileExport(String seq, List<Integer> newOrderSeqs) {
    try {
      com.jiniebox.jangbogo.dao.JbgExportConfigDataAccessObject exportConfigDao =
          new com.jiniebox.jangbogo.dao.JbgExportConfigDataAccessObject();
      org.json.simple.JSONObject exportConfig = exportConfigDao.getConfig();

      Integer autoSaveEnabled =
          exportConfig.get("auto_save_enabled") != null
              ? Integer.parseInt(exportConfig.get("auto_save_enabled").toString())
              : 0;

      Integer saveToJinieboxCfg = null;
      if (exportConfig.get("save_to_jiniebox") != null) {
        saveToJinieboxCfg = Integer.parseInt(exportConfig.get("save_to_jiniebox").toString());
      } else if (exportConfig.get("save_to_ftp") != null) {
        saveToJinieboxCfg = Integer.parseInt(exportConfig.get("save_to_ftp").toString());
      }
      int saveToJinieboxVal = (saveToJinieboxCfg != null) ? saveToJinieboxCfg : 0;

      boolean shouldAutoSave = (autoSaveEnabled == 1);
      boolean shouldUploadToFtp = (saveToJinieboxVal == 1);

      String savePath =
          exportConfig.get("save_path") != null ? exportConfig.get("save_path").toString() : "";
      String format =
          exportConfig.get("save_format") != null
              ? exportConfig.get("save_format").toString()
              : "json";

      if (savePath.isEmpty()) {
        if (shouldAutoSave || shouldUploadToFtp) {
          logger.warn("쇼핑몰 seq={} 파일 저장 경로가 설정되지 않아 파일 저장을 건너뜁니다.", seq);
        }
        return;
      }

      // 신규 주문이 있는 경우 파일 저장
      if (!newOrderSeqs.isEmpty()) {
        try {
          // 자동저장이 꺼져 있으면 로컬 파일을 만들지 않는다.
          // 과거에는 exportOrdersBySeqList 를 무조건 실행하고 로그만 shouldAutoSave 로 감쌌기 때문에,
          // 사용자가 자동저장을 껐어도 savePath 에 파일이 쌓였다.
          if (shouldAutoSave) {
            String exportedFile =
                exportService.exportOrdersBySeqList(savePath, format, newOrderSeqs);
            logger.info(
                "쇼핑몰 seq={} 스케줄 수집 후 파일 자동저장 완료: {}, 주문: {}개",
                seq,
                exportedFile,
                newOrderSeqs.size());
          }

          // FTP 업로드 처리
          if (shouldUploadToFtp) {
            processFtpUpload(seq, exportConfig, exportConfigDao, savePath, newOrderSeqs);
          }
        } catch (Exception exportEx) {
          logger.error("쇼핑몰 seq={} 파일 저장 실패: {}", seq, exportEx.getMessage(), exportEx);
        }
      } else if (shouldUploadToFtp) {
        // 신규 주문이 없어도 FTP 업로드 설정이 있으면 상태 파일을 보낸다.
        // 상태 파일 생성은 processFtpUpload 안에서 한 번만 한다. 과거에는 여기서도 한 번 만들어
        // 두 호출이 초 경계를 넘으면 앞의 파일이 업로드되지도 삭제되지도 않고 남았다.
        logger.info("쇼핑몰 seq={} 신규 주문 없음, 상태 파일 전송", seq);
        processFtpUpload(seq, exportConfig, exportConfigDao, savePath, new java.util.ArrayList<>());
      }
    } catch (Exception e) {
      logger.error("쇼핑몰 seq={} 파일 저장/FTP 처리 중 오류: {}", seq, e.getMessage(), e);
    }
  }

  /**
   * FTP 업로드 처리
   *
   * @param seq 쇼핑몰 시퀀스
   * @param exportConfig 내보내기 설정
   * @param exportConfigDao 내보내기 설정 DAO
   * @param savePath 저장 경로
   * @param newOrderSeqs 신규 주문 seq 목록
   */
  private void processFtpUpload(
      String seq,
      org.json.simple.JSONObject exportConfig,
      com.jiniebox.jangbogo.dao.JbgExportConfigDataAccessObject exportConfigDao,
      String savePath,
      List<Integer> newOrderSeqs) {
    try {
      final String ftpAddress =
          exportConfig.get("ftp_address") != null ? exportConfig.get("ftp_address").toString() : "";
      final String ftpId =
          exportConfig.get("ftp_id") != null ? exportConfig.get("ftp_id").toString() : "";

      String decryptedPass = "";
      try {
        decryptedPass = exportConfigDao.getDecryptedFtpPassword();
      } catch (Exception e) {
        logger.error("쇼핑몰 seq={} FTP 비밀번호 복호화 실패", seq, e);
      }
      final String ftpPass = decryptedPass;

      // 자격증명을 먼저 확인한다. 보낼 수 없는 상태라면 파일을 만들지 않는다 —
      // 과거에는 파일을 먼저 만들고 나서 자격증명이 없으면 지웠다.
      if (ftpAddress.isEmpty() || ftpId.isEmpty() || ftpPass.isEmpty()) {
        logger.warn("쇼핑몰 seq={} FTP 업로드 정보가 불완전합니다. (주소/아이디/비밀번호 확인)", seq);
        return;
      }

      int ftpEncryptEnabledVal = 1;
      if (exportConfig.get("ftp_encrypt_enabled") != null) {
        try {
          ftpEncryptEnabledVal =
              Integer.parseInt(exportConfig.get("ftp_encrypt_enabled").toString());
        } catch (NumberFormatException ignore) {
        }
      }
      boolean ftpEncryptEnabled = (ftpEncryptEnabledVal == 1);

      String publicKey =
          exportConfig.get("public_key") != null ? exportConfig.get("public_key").toString() : "";

      // 1. 지난 회차에 실패해 보류된 것부터 재전송한다. 신규분보다 먼저 보내 순서를 지킨다.
      FtpPendingQueue pendingQueue = new FtpPendingQueue(savePath);
      pendingQueue.drain(
          file -> {
            boolean resent =
                com.jiniebox.jangbogo.util.FtpUploadUtil.uploadFile(
                    ftpAddress, ftpId, ftpPass, file.getAbsolutePath());
            if (resent) {
              // 보류분 재전송도 실제 도달이다. 이걸 빼면 "밀린 것만 나가는" 기간 동안
              // 마지막 전송 시각이 멈춰 보인다. (판단 대기 10)
              exportService.recordFtpUpload();
            }
            return resent;
          });

      // 2. 이번 회차 전송분 생성
      boolean hasNewOrders = !newOrderSeqs.isEmpty();
      String ftpReadyFile;
      if (hasNewOrders) {
        ftpReadyFile = exportService.exportToJinieboxFileBySeqList(savePath, newOrderSeqs);
        logger.info("쇼핑몰 seq={} FTP 업로드용 jiniebox JSON 생성: {}", seq, ftpReadyFile);
      } else {
        ftpReadyFile = exportService.createEmptyStatusFile(savePath);
        logger.info("쇼핑몰 seq={} FTP 업로드용 상태 파일 생성: {}", seq, ftpReadyFile);
      }

      if (ftpReadyFile == null) {
        return;
      }

      String fileToUpload = ftpReadyFile;
      boolean fileEncrypted = false;
      boolean uploadSuccess = false;

      try {
        if (ftpEncryptEnabled && !publicKey.isEmpty()) {
          String encryptedFilePath = ftpReadyFile + ".encrypted";
          logger.info("쇼핑몰 seq={} FTP 업로드용 파일 암호화 시작", seq);

          boolean encryptSuccess =
              com.jiniebox.jangbogo.util.security.RsaFileEncryption.encryptFile(
                  ftpReadyFile, encryptedFilePath, publicKey);

          if (encryptSuccess) {
            fileToUpload = encryptedFilePath;
            fileEncrypted = true;
            logger.info("쇼핑몰 seq={} FTP 업로드용 암호화 완료: {}", seq, encryptedFilePath);
          } else {
            logger.warn("쇼핑몰 seq={} FTP 업로드용 파일 암호화 실패 - 평문 업로드 진행", seq);
          }
        }

        uploadSuccess =
            com.jiniebox.jangbogo.util.FtpUploadUtil.uploadFile(
                ftpAddress, ftpId, ftpPass, fileToUpload);

        if (uploadSuccess) {
          logger.info(
              "쇼핑몰 seq={} 스케줄 수집 후 FTP 업로드 완료 - 서버: {}, 암호화: {}", seq, ftpAddress, fileEncrypted);
          exportService.recordFtpUpload();
        } else {
          logger.warn("쇼핑몰 seq={} 스케줄 수집 후 FTP 업로드 실패 - 서버: {}", seq, ftpAddress);
        }
      } catch (Exception ftpUploadEx) {
        logger.error("쇼핑몰 seq={} FTP 업로드 중 오류: {}", seq, ftpUploadEx.getMessage(), ftpUploadEx);
      } finally {
        // 신규 주문분이 전송되지 못했으면 지우지 않고 보류 큐에 넣는다. 내보내기가 증분이라
        // 여기서 지우면 그 주문들은 수신측에 영원히 도달하지 못한다.
        // 상태 파일은 "신규 없음" 하트비트라 뒤늦게 보내면 시각을 오도하므로 큐에 넣지 않는다.
        if (!uploadSuccess && hasNewOrders) {
          pendingQueue.enqueue(new java.io.File(fileToUpload));
        } else {
          deleteTempFileSafely(fileToUpload, fileEncrypted ? "암호화 임시 파일" : "FTP 업로드용 임시 파일", 3);
        }

        // 암호화한 경우 평문 원본은 성공·실패와 무관하게 지운다. 보류 큐에는 암호문만 남는다.
        if (!ftpReadyFile.equals(fileToUpload)) {
          deleteTempFileSafely(ftpReadyFile, "FTP 업로드용 임시 JSON 파일", 3);
        }
      }
    } catch (Exception e) {
      logger.error("쇼핑몰 seq={} FTP 업로드 처리 중 오류: {}", seq, e.getMessage(), e);
    }
  }

  /**
   * 임시 파일 안전 삭제 (재시도 로직 포함)
   *
   * @param filePath 삭제할 파일 경로
   * @param fileDescription 파일 설명 (로깅용)
   * @param maxRetries 최대 재시도 횟수
   */
  private void deleteTempFileSafely(String filePath, String fileDescription, int maxRetries) {
    if (filePath == null || filePath.isEmpty()) {
      return;
    }

    java.io.File file = new java.io.File(filePath);
    if (!file.exists()) {
      return;
    }

    for (int attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        if (file.delete()) {
          logger.debug("{} 삭제 완료: {}", fileDescription, filePath);
          return;
        } else {
          if (attempt < maxRetries) {
            logger.debug("{} 삭제 실패 (시도 {}/{}), 재시도 중...", fileDescription, attempt, maxRetries);
            try {
              Thread.sleep(100 * attempt); // 백오프: 100ms, 200ms, 300ms
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              return;
            }
          } else {
            logger.warn("{} 삭제 실패 (최대 재시도 횟수 초과): {}", fileDescription, filePath);
          }
        }
      } catch (Exception e) {
        if (attempt < maxRetries) {
          logger.debug(
              "{} 삭제 중 오류 (시도 {}/{}): {}", fileDescription, attempt, maxRetries, e.getMessage());
          try {
            Thread.sleep(100 * attempt);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return;
          }
        } else {
          logger.warn("{} 삭제 중 오류 (최대 재시도 횟수 초과): {}", fileDescription, e.getMessage());
        }
      }
    }
  }

  /** 애플리케이션 종료 시 모든 스케줄 정리 */
  @PreDestroy
  public void shutdown() {
    logger.info("MallSchedulerService 종료 중");
    cancelAll();
    scheduler.shutdown();
    try {
      if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
        scheduler.shutdownNow();
      }
    } catch (InterruptedException e) {
      scheduler.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  // 유틸리티 메서드

  // asInt 는 지웠다 (Phase 5-20). 유일한 호출부가 account_status 판정이었고, 그 판정은
  // JangBoGoManager.qualify 로 옮겼다. 남겨 두면 판정이 여기에도 있는 것처럼 읽힌다.

  private String str(Object o) {
    return o == null ? "" : String.valueOf(o);
  }
}

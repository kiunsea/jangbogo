package com.jiniebox.jangbogo.svc;

import com.jiniebox.jangbogo.dao.JbgMallDataAccessObject;
import com.jiniebox.jangbogo.dto.JangbogoConfig;
import com.jiniebox.jangbogo.svc.ifc.MallSession;
import com.jiniebox.jangbogo.svc.mall.MallRegistry;
import com.jiniebox.jangbogo.svc.util.BrowserConcurrencyLimiter;
import com.jiniebox.jangbogo.svc.util.CollectAdmission;
import com.jiniebox.jangbogo.svc.util.CollectTrigger;
import com.jiniebox.jangbogo.svc.util.ExecutionContextDetector;
import com.jiniebox.jangbogo.svc.util.SessionProfileGate;
import com.jiniebox.jangbogo.svc.util.SessionProfilePolicy;
import com.jiniebox.jangbogo.svc.util.WebDriverManager;
import com.jiniebox.jangbogo.sys.UserSession;
import com.jiniebox.jangbogo.util.JinieboxUtil;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 장보고 관리자 쇼핑몰 연결 및 주문 수집 기능 제공 */
@Service
public class JangBoGoManager {

  private static final Logger logger = LogManager.getLogger(JangBoGoManager.class);

  @Autowired private JangbogoConfig jangbogoConfig;

  // 현재 실행 중인 쇼핑몰 seq 추적
  private final Set<String> runningCollections = ConcurrentHashMap.newKeySet();

  /**
   * 쇼핑몰 목록에 사용자 아이디 원본값을 저장하여 반환한다.
   *
   * @param malls
   * @param us
   */
  public static void addMallUsrid(List<JSONObject> malls, UserSession us) {
    JSONObject mallInfo = null;
    JSONObject mallMap = JinieboxUtil.listToMap(malls);
    String mallSeq, mallUsrid = null;
    Iterator<String> mallSeqs = mallMap.keySet().iterator();
    while (mallSeqs.hasNext()) {
      mallSeq = mallSeqs.next().toString();
      mallUsrid = us.getMallUsrid(mallSeq);
      if (mallUsrid != null) {
        mallInfo = (JSONObject) mallMap.get(mallSeq);
        mallInfo.put("usrid", mallUsrid);
      }
    }
  }

  /**
   * 쇼핑몰 연결 테스트후 장보고에 등록
   *
   * @param seqMall
   * @param seqUser
   * @param usrid
   * @param usrpw
   * @return 1 : 성공, 0 : 실패, 2 : 시간경과필요
   * @throws Exception
   */
  public int connectToMall(String seqMall, String usrid, String usrpw) throws Exception {

    if (Integer.parseInt(seqMall) != 1) {
      if (!this.elapsedSigninTime(seqMall)) { // 유효한 시간 체크
        return 2;
      }
    }

    int rtnVal = 0;
    WebDriverManager wdm = new WebDriverManager();
    WebDriver driver = wdm.getWebDriver();

    MallSession mallObj = this.getMallSession(seqMall, usrid, usrpw);
    if (mallObj != null) {
      boolean validUser = false;
      try {
        validUser = mallObj.signin(driver); // 계정 정보가 유효한지 테스트
      } catch (Exception e) {
        logger.debug(e.getMessage());
      }

      if (validUser) {
        JbgMallDataAccessObject jaDao = new JbgMallDataAccessObject();

        int chkRst = -1;
        if (seqMall != null) {
          chkRst = jaDao.checkAccountStatus(seqMall);
        }

        if (chkRst < 0) {
          jaDao.add(seqMall, 1, null, null);
        } else {
          jaDao.setAccountStatus(seqMall, 1);
        }

        mallObj.signout(driver);
        rtnVal = 1;
      }
    }

    // 드라이버 종료
    driver.quit();
    driver = null;

    return rtnVal;
  }

  /**
   * 공통 수집 실행 로직 (내부 메서드)
   *
   * @param seqMall 수집할 쇼핑몰
   * @param mallId 쇼핑몰 사용자 아이디
   * @param mallPw 쇼핑몰 사용자 비밀번호
   * @return MallOrderUpdaterRunner 인스턴스 (실행 완료 후 신규 주문 seq 조회 가능)
   * @throws IllegalStateException 이미 실행 중인 경우
   */
  private MallOrderUpdaterRunner executeCollectionInternal(
      String seqMall, String mallId, String mallPw) throws IllegalStateException {
    // 이미 실행 중이면 예외 발생
    if (runningCollections.contains(seqMall)) {
      throw new IllegalStateException("쇼핑몰 seq=" + seqMall + " 이미 수집 작업 실행 중");
    }

    // 실행 중으로 표시 (Thread 생성 전에 보장)
    runningCollections.add(seqMall);
    logger.info("쇼핑몰 seq={} 수집 작업 시작", seqMall);

    try {
      MallOrderUpdaterRunner runner = new MallOrderUpdaterRunner(seqMall, mallId, mallPw);
      return runner;
    } catch (Exception e) {
      // 생성 실패 시 즉시 제거
      runningCollections.remove(seqMall);
      logger.error("쇼핑몰 seq={} 수집 작업 준비 실패", seqMall, e);
      throw new RuntimeException("수집 작업 준비 실패: " + e.getMessage(), e);
    }
  }

  // 비동기 수집 진입점 updateItems 는 여기 있었지만 지웠다 (Phase 5-19).
  //
  // 호출처가 0 이었고(양 repo grep 확인), 게이트와 브라우저 동시 실행 제한을 거치지 않고 곧바로
  // executeCollectionInternal 로 들어갔다. 즉 이 단계가 세우려는 정책을 통째로 비켜 가는 통로가
  // 쓰이지 않은 채 열려 있었다. 필요해지면 collect 를 감싸서 다시 만든다.

  /**
   * 구매 아이템 목록 수집 (동기 실행). <b>수집으로 들어가는 유일한 통로다</b> (Phase 5-19).
   *
   * <p>스케줄러와 즉시수집이 여기서 만난다. 예전에는 실행 정책(세션 프로필 게이트)이 스케줄러 쪽에만 있어서 <b>즉시수집이 게이트를 우회</b>했다. 규약을 호출부가
   * 지키게 두면 두 경로는 언젠가 갈린다 — Phase 5-18 에서 같은 형태의 결함을 이미 한 번 고쳤다. 그래서 판정을 이 안으로 들여왔고, 예전 진입점 {@code
   * updateItemsAndGetNewSeqs} 는 지웠다. 우회할 방법이 남아 있으면 우회는 결국 생긴다.
   *
   * <p>순서는 <b>게이트 → 제한기 → 자격증명</b> 이다. 자격증명을 값이 아니라 {@link MallCredentialSupplier} 로 받는 것이 이 순서를
   * 성립시킨다 — 막힐 회차에서는 비밀번호가 아예 복호화되지 않는다.
   *
   * <p>막힌 회차는 예외가 아니라 {@link MallCollectOutcome} 으로 돌아온다. 즉시수집의 포괄 {@code catch} 가 예외를 계정 문제로 읽어 계정
   * 연결을 끊기 때문이다.
   *
   * @param mall 쇼핑몰 행 (seq · 세션 프로필 열을 읽는다). 호출부가 이미 조회한 것을 그대로 넘긴다
   * @param credentials 자격증명 공급자. 진입 판정을 통과한 뒤에만 호출된다
   * @param trigger 누가 요청했는가. 브라우저 자리를 얼마나 기다릴지가 갈린다
   * @return 수집 결과 또는 막힌 사유
   * @throws Exception 수집 중 오류, 또는 자격증명 공급자가 던진 예외
   */
  public MallCollectOutcome collect(
      JSONObject mall, MallCredentialSupplier credentials, CollectTrigger trigger)
      throws Exception {

    if (mall == null) {
      throw new IllegalArgumentException("쇼핑몰 정보가 없다.");
    }
    String seqMall = String.valueOf(mall.get("seq"));

    // 세션 프로필 게이트 (Phase 5-4).
    //
    // 마스터 킬스위치가 꺼져 있거나 이 몰이 옵트인하지 않았으면 PROCEED 라 기존 경로 그대로다.
    // 실행 컨텍스트는 공급자로 넘긴다 (Phase 5-18) — 값으로 넘기면 옵트인하지 않은 몰에서도 매 회차 tasklist 가 돈다.
    SessionProfileGate.Decision gate =
        SessionProfileGate.evaluate(
            SessionProfilePolicy.appliesTo(asInt(mall.get("session_profile_enabled")) == 1),
            ExecutionContextDetector::detect,
            str(mall.get("session_profile_name")),
            str(mall.get("session_profile_owner")),
            System.getProperty("user.name"));

    try (CollectAdmission admission =
        CollectAdmission.evaluate(
            gate, BrowserConcurrencyLimiter.shared(), trigger.acquireTimeoutMillis())) {

      if (!admission.admitted()) {
        logger.info("쇼핑몰 seq={} 수집 진입 차단 — {}", seqMall, admission);
        return MallCollectOutcome.blockedBy(admission);
      }

      // 여기서 처음 복호화한다. 위에서 막혔으면 비밀번호는 메모리에 오르지 않는다.
      MallCredentials creds = credentials.get();

      MallOrderUpdaterRunner runner;
      try {
        runner = executeCollectionInternal(seqMall, creds.id(), creds.pw());
      } catch (IllegalStateException e) {
        logger.warn("쇼핑몰 seq={} 이미 수집 작업 실행 중, 건너뜀", seqMall);
        return MallCollectOutcome.alreadyRunning();
      } catch (RuntimeException e) {
        logger.error("쇼핑몰 seq={} 수집 작업 준비 실패", seqMall, e);
        throw e;
      }

      try {
        // 동기 실행하여 결과 받기
        runner.run();

        List<Integer> newOrderSeqs = runner.getNewOrderSeqs();
        logger.info("쇼핑몰 seq={} 수집 작업 완료, 신규 주문: {}개", seqMall, newOrderSeqs.size());

        return MallCollectOutcome.success(newOrderSeqs);
      } finally {
        runningCollections.remove(seqMall);
      }
    }
  }

  /** JSONObject 의 값을 문자열로. 없으면 null. */
  private static String str(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  /** JSONObject 의 값을 int 로. 없거나 숫자가 아니면 0 — 즉 "옵트인하지 않음" 으로 읽힌다. */
  private static int asInt(Object value) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    if (value == null) {
      return 0;
    }
    try {
      return Integer.parseInt(String.valueOf(value).trim());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  /**
   * 특정 쇼핑몰의 수집 작업이 현재 실행 중인지 확인
   *
   * @param seqMall 쇼핑몰 시퀀스
   * @return 실행 중이면 true
   */
  public boolean isCollecting(String seqMall) {
    return runningCollections.contains(seqMall);
  }

  /**
   * mall sequence 에 따라 <b>자격증명 검증용</b> mall instance 를 생성하여 반환.
   *
   * <p>seq 로 분기하던 하드코딩 사슬을 {@link MallRegistry} 로 옮겼다 (Phase 3-12). 수집기가 여럿인 몰(seq=1)이라도 검증은 하나로만
   * 한다 — 연결 한 번에 두 사이트로 로그인하면 이 프로젝트가 줄이려는 반복 로그인을 스스로 늘리게 된다.
   *
   * @param seqMall 쇼핑몰 seq
   * @param usrid 아이디
   * @param usrpw 비밀번호
   * @return 검증용 수집기. 등록되지 않은 seq 면 null
   */
  private MallSession getMallSession(String seqMall, String usrid, String usrpw) {
    return MallRegistry.bySeq(seqMall)
        .map(mall -> mall.verificationCollector().create(usrid, usrpw))
        .orElse(null);
  }

  /**
   * 지정한 시간이 경과되었는지 여부를 확인 (짧은시간동안 로그인이 여러번 수행되면 emart 와 같은 특정 사이트에서 중복로그인으로 간주함)
   *
   * @param seqMall
   * @return
   * @throws Exception
   */
  private boolean elapsedSigninTime(String seqMall) throws Exception {

    JbgMallDataAccessObject jaDao = new JbgMallDataAccessObject();
    JSONObject accessInfo = jaDao.getAccessInfo(seqMall);

    Object lastSigninTime = accessInfo != null ? accessInfo.get("time") : 0;
    if (lastSigninTime != null) {
      long curr = System.currentTimeMillis();
      long lastSignin = Long.parseLong(lastSigninTime.toString());
      long delay = Long.parseLong(jangbogoConfig.get("MALL_SIGNIN_DELAY"));
      if ((curr - lastSignin) > delay) {
        logger.debug("쇼핑몰 seq={} 사용자 구매내역 조회 시작", seqMall);
        return true;
      } else {
        logger.debug("설정된 대기시간={}ms, 경과시간={}ms", delay, (curr - lastSignin));
      }
    }

    return false;
  }
}

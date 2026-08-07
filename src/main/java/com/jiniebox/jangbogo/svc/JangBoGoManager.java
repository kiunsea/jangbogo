package com.jiniebox.jangbogo.svc;

import com.jiniebox.jangbogo.dao.JbgMallDataAccessObject;
import com.jiniebox.jangbogo.dto.JangbogoConfig;
import com.jiniebox.jangbogo.dto.MallAccount;
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
   * 저장된 계정을 <b>필요해진 순간에</b> 읽는 공급자 (Phase 5-20).
   *
   * <p>공급자로 받는 이유는 {@link MallCredentialSupplier} 와 같다 — 순서다. {@code account_status} 나 암호화 키에서 이미
   * 걸리는 회차는 계정 파일을 읽을 이유가 없고, 값으로 받으면 자바가 호출 전에 평가하므로 <b>막힐 회차에서도 매번 파일을 읽는다.</b> 예전 판정 순서가 정확히 그
   * 순서였으므로, 공급자로 받아야 옵트인하지 않은 몰의 동작이 한 톨도 바뀌지 않는다.
   *
   * <p>{@link java.util.function.Supplier} 를 쓰지 않는 것은 계정 조회가 {@code IOException} 을 던지기 때문이다.
   */
  @FunctionalInterface
  public interface StoredAccountSupplier {

    /**
     * 저장된 계정을 읽는다.
     *
     * @return 저장된 계정. 없으면 {@code null}
     * @throws Exception 계정 파일 읽기 실패. 호출부가 그대로 받는다
     */
    MallAccount get() throws Exception;
  }

  /**
   * 수집 자격을 막은 사유 (Phase 5-20).
   *
   * <p>문구를 예전 그대로 옮겼다. 이 값은 {@code jbg_collect_log} 에 남고 화면에 그대로 나가므로, 판정을 한 곳으로 모으면서 문구까지 바꾸면 옵트인과
   * 무관한 사용자에게 <b>없던 변화가 보인다.</b>
   */
  public enum CollectBlocker {

    /**
     * 계정이 연결돼 있지 않다.
     *
     * <p><b>로그를 남기지 않는다.</b> 예전 스케줄러는 이 경우 debug 한 줄만 남기고 물러났다. 여기서 기록으로 승격시키면 연결한 적 없는 몰이 주기마다 같은
     * 줄을 쌓아 수집 로그 화면이 무의미해진다.
     */
    NOT_CONNECTED("계정이 연결돼 있지 않음", false),

    /** 암호문을 열 열쇠가 없다. */
    ENCRYPT_KEY_MISSING("암호화 키/IV 누락", true),

    /** 저장된 계정 자체가 없다. */
    ACCOUNT_NOT_FOUND("mall_account.yml에 계정 정보 없음", true),

    /** 계정은 있는데 아이디나 비밀번호가 비어 있다. */
    ACCOUNT_EMPTY("mall_account.yml에 아이디/비밀번호 비어있음", true);

    private final String reason;
    private final boolean worthLogging;

    CollectBlocker(String reason, boolean worthLogging) {
      this.reason = reason;
      this.worthLogging = worthLogging;
    }

    /** 사람이 읽을 사유. */
    public String reason() {
      return reason;
    }

    /** 수집 로그에 한 행으로 남길 사유인가. */
    public boolean worthLogging() {
      return worthLogging;
    }
  }

  /**
   * 이번 회차에 이 몰을 수집할 자격이 있는가 (Phase 5-20).
   *
   * <p>{@code seqMall} 을 함께 들고 다니는 이유는 {@link #collect} 가 <b>다른 몰의 판정으로 수집이 시작되는 것을 막기 위해서</b>다.
   * 판정과 실행이 떨어져 있으면 반복문 안에서 앞 회차의 값이 남아 넘어가는 형태가 언젠가 생긴다.
   *
   * @param seqMall 이 판정이 가리키는 쇼핑몰 seq
   * @param route 어느 경로로 수집할 것인가
   * @param account 자격증명 경로에서 쓸 저장된 계정(암호문). 세션 경로·자격 없음이면 null
   * @param blocker 자격이 없는 사유. 자격이 있으면 null
   */
  public record CollectEligibility(
      String seqMall, Route route, MallAccount account, CollectBlocker blocker) {

    /** 수집 경로. */
    public enum Route {
      /** 저장된 아이디/비밀번호로 로그인해 수집한다 (기존 경로). */
      CREDENTIALS,
      /** 캡처해 둔 로그인 세션만으로 수집한다 — 비밀번호를 열지 않는다. */
      SESSION,
      /** 어느 쪽도 쓸 수 없다. */
      NONE
    }

    public CollectEligibility {
      if (route == null) {
        throw new IllegalArgumentException("수집 경로가 없다.");
      }
      // 자격과 사유가 함께 있거나 함께 없으면 판정이 깨진 것이다. 그 상태로 흘러가면
      // '자격이 있는데 사유가 붙은' 회차가 화면에 이유 없는 건너뜀으로 나온다.
      if ((route == Route.NONE) != (blocker != null)) {
        throw new IllegalArgumentException("자격 판정과 사유가 어긋난다: route=" + route);
      }
      if (route == Route.CREDENTIALS && account == null) {
        throw new IllegalArgumentException("자격증명 경로인데 계정이 없다.");
      }
    }

    /** 수집을 시작해도 되는가. */
    public boolean qualified() {
      return route != Route.NONE;
    }

    /** 비밀번호를 복호화해야 하는가. 세션 경로면 false 다. */
    public boolean needsCredentials() {
      return route == Route.CREDENTIALS;
    }

    /** 수집 로그에 남길 코드. 자격이 있으면 null. */
    public String code() {
      return blocker == null ? null : blocker.name();
    }

    /** 사람이 읽을 사유. 자격이 있으면 null. */
    public String reason() {
      return blocker == null ? null : blocker.reason();
    }

    /** 이 사유를 수집 로그에 한 행으로 남길 것인가. */
    public boolean worthLogging() {
      return blocker != null && blocker.worthLogging();
    }

    /**
     * record 가 자동으로 만드는 {@code toString} 을 덮어쓴다.
     *
     * <p>{@link #account} 는 저장된 계정 객체다. 자동 생성 {@code toString} 은 모든 필드를 그대로 찍으므로 <b>로그 한 줄이나 예외 메시지
     * 하나로 계정 암호문이 새어 나간다.</b> {@link MallCredentials} 가 같은 이유로 같은 처리를 하고 있다.
     */
    @Override
    public String toString() {
      return "CollectEligibility[seq="
          + seqMall
          + ", 경로="
          + route
          + (blocker == null ? "" : ", 사유=" + blocker.name())
          + "]";
    }
  }

  /**
   * 이 몰을 수집할 자격이 있는지 판정한다 — <b>자격 판정이 사는 유일한 자리다</b> (Phase 5-20).
   *
   * <h2>왜 여기로 모았나</h2>
   *
   * <p>5-19 가 세션 프로필 게이트를 이 클래스로 모은 것과 같은 이유다. 판정이 스케줄러와 즉시수집에 <b>각각 한 벌씩</b> 있었고, 두 벌은 이미 갈려 있었다 —
   * 스케줄러는 {@code account_status} 를 보고 즉시수집은 보지 않았으며, 즉시수집은 건너뛸 때 {@code account_status} 를 0 으로 눌러
   * <b>연결을 적극적으로 끊었다.</b> 그래서 스케줄러만 고쳐도 즉시수집 한 번에 원복됐다. 규약을 호출부가 지키게 두면 두 경로는 언젠가 갈린다.
   *
   * <h2>무엇을 넓혔나</h2>
   *
   * <p>예전 판정은 "저장된 자격증명이 있어야 수집 대상" 이었다. 그런데 {@code account_status=1} 은 {@link #connectToMall} 이 실제
   * 아이디/비밀번호 로그인에 성공해야만 세팅되고 세션 캡처는 그 값을 건드리지 않는다. 즉 <b>"비밀번호 없이 세션만으로 수집" 이 이 지점에서 끊겼다.</b> 이제 판정은
   * <b>자격증명이 있거나 또는 쓸 수 있는 세션 스냅샷이 있으면 수집 대상</b>이다.
   *
   * <h2>순서 — 자격증명을 먼저 본다</h2>
   *
   * <p>세션을 먼저 고르면 두 가지가 함께 망가진다.
   *
   * <ul>
   *   <li>게이트가 꺼져 있을 때 예전 판정과 <b>같음을 보장할 수 없다.</b> 지금은 세션 분기가 항상 false 라 위 절만으로 예전과 완전히 같다.
   *   <li>수집기가 둘인 몰에서 세션이 <b>대체하지 못한 자리</b>가 굶는다. 세션 경로는 자기가 선언한 자리만 대체하고 나머지는 그대로 자격증명으로 도는데, 여기서
   *       세션을 먼저 고르면 복호화를 건너뛰어 그 나머지가 아이디/비밀번호 없이 돈다.
   * </ul>
   *
   * <p>DB 를 읽지 않는다 — 세션 유무는 호출부가 <b>이미 조회한</b> 몰 행({@code getMall})의 세션 프로필 컬럼으로 본다. 여기서 다시 조회하면
   * 회차마다 왕복이 하나 늘고, 그 값이 호출부가 게이트에 넘긴 값과 갈릴 수 있다.
   *
   * @param mall 쇼핑몰 행. 호출부가 이미 조회한 것을 그대로 넘긴다
   * @param account 저장된 계정 공급자. 위 순서에 따라 <b>필요할 때만</b> 호출된다
   * @param trigger 누가 요청했는가. {@code account_status} 를 볼지가 갈린다
   * @return 자격 판정
   * @throws Exception 계정 공급자가 던진 예외
   */
  public static CollectEligibility qualify(
      JSONObject mall, StoredAccountSupplier account, CollectTrigger trigger) throws Exception {

    if (mall == null) {
      throw new IllegalArgumentException("쇼핑몰 정보가 없다.");
    }
    String seqMall = String.valueOf(mall.get("seq"));

    CollectBlocker blocker = credentialBlocker(mall, trigger);
    MallAccount stored = null;
    if (blocker == null) {
      stored = account == null ? null : account.get();
      if (stored == null) {
        blocker = CollectBlocker.ACCOUNT_NOT_FOUND;
      } else if (JinieboxUtil.isEmpty(stored.getId()) || JinieboxUtil.isEmpty(stored.getPass())) {
        blocker = CollectBlocker.ACCOUNT_EMPTY;
      }
    }

    if (blocker == null) {
      return new CollectEligibility(seqMall, CollectEligibility.Route.CREDENTIALS, stored, null);
    }

    // 자격증명이 없어도 캡처해 둔 세션이 있으면 수집 대상이다. 이 국면의 목적이 여기 한 줄이다.
    if (sessionSnapshotUsable(mall)) {
      return new CollectEligibility(seqMall, CollectEligibility.Route.SESSION, null, null);
    }

    // 세션도 없다. 자격증명 쪽에서 처음 걸린 사유를 그대로 돌려준다 — 예전 문구와 같아야 한다.
    return new CollectEligibility(seqMall, CollectEligibility.Route.NONE, null, blocker);
  }

  /**
   * 자격증명 경로가 막히는 사유. 막히지 않으면 null.
   *
   * <p>순서를 예전 그대로 지킨다. {@code account_status} → 암호화 키/IV → 저장된 계정. 계정 파일 읽기는 이 두 검사를 통과한 뒤에만 일어난다.
   *
   * <p><b>{@code account_status} 를 무인 수집에서만 보는 것은 예전 그대로다.</b> 스케줄러는 보고 즉시수집은 보지 않았다. 여기서 즉시수집에도 이
   * 검사를 붙이면 판정이 <b>좁아진다</b> — 사용자가 연결을 해제해 둔 몰을 화면에서 골라 눌렀을 때 예전에는 돌던 것이 안 돌게 된다. 이 단계가 하려는 일은 넓히는
   * 것이지 좁히는 것이 아니다. 두 값이 갈려 있다는 사실 자체는 그대로 두되, 갈림을 <b>한 곳에 적어 둔다</b>.
   */
  private static CollectBlocker credentialBlocker(JSONObject mall, CollectTrigger trigger) {
    if (trigger == CollectTrigger.SCHEDULED && asInt(mall.get("account_status")) != 1) {
      return CollectBlocker.NOT_CONNECTED;
    }
    if (JinieboxUtil.isEmpty(str(mall.get("encrypt_key")))
        || JinieboxUtil.isEmpty(str(mall.get("encrypt_iv")))) {
      return CollectBlocker.ENCRYPT_KEY_MISSING;
    }
    return null;
  }

  /**
   * 이 몰에 <b>쓸 수 있는</b> 세션 스냅샷이 있는가 (Phase 5-20).
   *
   * <p>이중 게이트(마스터 킬스위치 + 몰별 옵트인) 뒤에 둔다. 킬스위치가 꺼져 있으면 첫 줄에서 false 라, 이 함수가 자격 판정에 끼어도 옵트인하지 않은 몰의
   * 판정은 예전과 완전히 같다. 그것이 이 배선의 전제다.
   *
   * <p>판정 재료는 <b>이미 조회한 몰 행</b>이다. {@code session_profile_name} 과 {@code session_profile_status} 는
   * 세션 캡처가 성공한 그 순간에만 함께 채워진다({@code JbgMallDataAccessObject.saveSessionProfileIdentity}). 그래서 이 두
   * 값이 '캡처를 마쳤다' 는 사실을 가리키는 유일한 신호이고, 새 컬럼을 팔 이유가 없다 — SQLite 는 컬럼을 되돌리기가 사실상 편도다.
   *
   * <p>여기서 <b>만료를 판정하지 않는다.</b> 만료는 시간이 흘러서 생기는데 그 값을 때맞춰 갱신해 줄 주체가 이 프로그램에 없다. 만료된 세션은 수집기가 실제로
   * 주입해 봐야 드러나고, 그때 {@code SKIPPED} 사유로 남는다 — 두 곳에 만료를 두면 갱신 주체가 없는 쪽이 굳는다.
   */
  private static boolean sessionSnapshotUsable(JSONObject mall) {
    if (!SessionProfilePolicy.appliesTo(asInt(mall.get("session_profile_enabled")) == 1)) {
      return false;
    }
    String profileName = str(mall.get("session_profile_name"));
    if (profileName == null || profileName.trim().isEmpty()) {
      return false;
    }
    String status = str(mall.get("session_profile_status"));
    return status != null
        && JbgMallDataAccessObject.SESSION_PROFILE_STATUS_READY.equals(status.trim());
  }

  /**
   * 판정 없이, 혹은 남의 판정으로 수집이 시작되는 것을 막는다 (Phase 5-20).
   *
   * <p>여기만 값이 아니라 예외다. 나머지 막힘은 전부 {@link MallCollectOutcome} 으로 돌려주는데, 그것들은 <b>사용자 상태</b>라 화면에 사유로
   * 나가야 하기 때문이다. 이 셋은 사용자 상태가 아니라 <b>호출부가 통로를 어긴 것</b>이다. 값으로 돌려주면 건너뜀 로그 한 줄로 묻혀 배선이 어긋난 채로 계속 돈다.
   *
   * <p>게이트보다 <b>앞</b>에 둔다. 브라우저 자리를 잡기 전에 걸러야 잘못된 호출이 남의 수집을 굶기지 않는다.
   */
  private static void requireQualified(
      String seqMall, CollectEligibility eligibility, MallCredentialSupplier credentials) {
    if (eligibility == null) {
      throw new IllegalArgumentException(
          "수집 자격 판정 없이 collect 를 불렀다. qualify 를 거쳐라. seq=" + seqMall);
    }
    if (!seqMall.equals(eligibility.seqMall())) {
      throw new IllegalArgumentException(
          "다른 몰의 자격 판정으로 수집을 시작할 수 없다. seq=" + seqMall + ", 판정=" + eligibility);
    }
    if (!eligibility.qualified()) {
      throw new IllegalArgumentException(
          "자격이 없는 몰로 collect 를 불렀다. 호출부가 판정을 보고 물러나야 한다. 판정=" + eligibility);
    }
    if (eligibility.needsCredentials() && credentials == null) {
      throw new IllegalArgumentException("자격증명 경로인데 공급자가 없다. seq=" + seqMall);
    }
  }

  /**
   * 구매 아이템 목록 수집 (동기 실행). <b>수집으로 들어가는 유일한 통로다</b> (Phase 5-19).
   *
   * <p>스케줄러와 즉시수집이 여기서 만난다. 예전에는 실행 정책(세션 프로필 게이트)이 스케줄러 쪽에만 있어서 <b>즉시수집이 게이트를 우회</b>했다. 규약을 호출부가
   * 지키게 두면 두 경로는 언젠가 갈린다 — Phase 5-18 에서 같은 형태의 결함을 이미 한 번 고쳤다. 그래서 판정을 이 안으로 들여왔고, 예전 진입점 {@code
   * updateItemsAndGetNewSeqs} 는 지웠다. 우회할 방법이 남아 있으면 우회는 결국 생긴다.
   *
   * <p>순서는 <b>자격 → 게이트 → 제한기 → 자격증명</b> 이다. 자격증명을 값이 아니라 {@link MallCredentialSupplier} 로 받는 것이 이
   * 순서를 성립시킨다 — 막힐 회차에서는 비밀번호가 아예 복호화되지 않는다.
   *
   * <p>자격 판정을 <b>인자로 요구한다</b> (Phase 5-20). {@link #qualify} 의 결과 없이는 이 메서드를 부를 수 없고, 판정에 실린 seq 가
   * 이 몰과 다르면 거부한다. 판정을 안에서 다시 내리지 않는 이유는 호출부가 <b>자격이 없는 몰을 각자 다르게 알려야</b> 하기 때문이다 — 스케줄러는 수집 로그에
   * 남기고 즉시수집은 응답 JSON 으로 돌려준다. 판정 자체는 한 벌이고 통보만 갈린다.
   *
   * <p>막힌 회차는 예외가 아니라 {@link MallCollectOutcome} 으로 돌아온다. 즉시수집의 포괄 {@code catch} 가 예외를 계정 문제로 읽어 계정
   * 연결을 끊기 때문이다.
   *
   * @param mall 쇼핑몰 행 (seq · 세션 프로필 열을 읽는다). 호출부가 이미 조회한 것을 그대로 넘긴다
   * @param eligibility {@link #qualify} 가 이 몰에 대해 내린 판정. 자격이 없으면 부를 수 없다
   * @param credentials 자격증명 공급자. 진입 판정을 통과한 뒤에만 호출된다. 세션 경로면 null 이어도 된다
   * @param trigger 누가 요청했는가. 브라우저 자리를 얼마나 기다릴지가 갈린다
   * @return 수집 결과 또는 막힌 사유
   * @throws Exception 수집 중 오류, 또는 자격증명 공급자가 던진 예외
   */
  public MallCollectOutcome collect(
      JSONObject mall,
      CollectEligibility eligibility,
      MallCredentialSupplier credentials,
      CollectTrigger trigger)
      throws Exception {

    if (mall == null) {
      throw new IllegalArgumentException("쇼핑몰 정보가 없다.");
    }
    String seqMall = String.valueOf(mall.get("seq"));

    requireQualified(seqMall, eligibility, credentials);

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
      //
      // 세션 경로는 복호화 자체가 없다 (Phase 5-20). 저장된 자격증명이 없는 것이 그 몰의 정상 상태이므로
      // 여기서 '없으면 실패' 로 다루면 이 국면의 목적이 진입부에서 다시 무너진다.
      String mallId = null;
      String mallPw = null;
      if (eligibility.needsCredentials()) {
        MallCredentials creds = credentials.get();
        mallId = creds.id();
        mallPw = creds.pw();
      } else {
        logger.info("쇼핑몰 seq={} 저장된 세션으로 수집한다 — 비밀번호를 복호화하지 않는다", seqMall);
      }

      MallOrderUpdaterRunner runner;
      try {
        runner = executeCollectionInternal(seqMall, mallId, mallPw);
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

        // 실행기가 판정한 결과를 그대로 돌려준다 — 여기서 success 를 새로 만들면 이번 회차의 세션 만료가
        // 통째로 지워진다 (Phase 5-10 배선의 마지막 한 홉).
        //
        // 만료가 아닌 회차에는 collectOutcome() 이 정확히 MallCollectOutcome.success(newOrderSeqs) 를
        // 돌려주므로 기존 동작은 한 톨도 바뀌지 않는다. 만료 회차에만 결과가 갈리고, 그것을 스케줄러는
        // 일시중단으로, 즉시수집은 응답 JSON 의 sessionExpired 목록으로 읽는다.
        return runner.collectOutcome();
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

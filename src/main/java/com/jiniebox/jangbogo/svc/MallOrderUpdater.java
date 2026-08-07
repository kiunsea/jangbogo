package com.jiniebox.jangbogo.svc;

import com.jiniebox.jangbogo.dao.JbgCollectBreakerDataAccessObject;
import com.jiniebox.jangbogo.dao.JbgMallDataAccessObject;
import com.jiniebox.jangbogo.svc.mall.MallRegistry;
import com.jiniebox.jangbogo.svc.mall.SessionCollector;
import com.jiniebox.jangbogo.svc.util.CollectBreakerPolicy;
import com.jiniebox.jangbogo.svc.util.CollectStep;
import com.jiniebox.jangbogo.svc.util.SessionProfilePolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class MallOrderUpdater {

  private static final Logger logger = LogManager.getLogger(MallOrderUpdater.class);

  /**
   * 한 쇼핑몰(seq)에 수집기가 여러 개 붙는 경우(seq=1 은 SSG + Emart), 일부만 실패한 사실.
   *
   * @param collector 수집기 이름 (SSG / Emart 등)
   * @param cause 실패 컨텍스트를 담은 예외
   */
  public record CollectFailure(String collector, CollectException cause) {}

  /**
   * 수집기 하나의 이번 회차 결과.
   *
   * <p>실패만 모으던 {@link CollectFailure} 로는 <b>연속 실패 카운트를 리셋할 근거가 없다</b> — 성공을 기록하지 않으면 "이번엔 됐다"를 알 수
   * 없기 때문이다. 그래서 성공·실패·건너뜀을 모두 남긴다. (Phase 3-3)
   *
   * @param collector 수집기 이름 (SSG / Emart 등)
   * @param status SUCCESS / FAIL / SKIPPED
   * @param cause 실패 컨텍스트 (실패가 아니면 null)
   * @param reason 건너뛴 사유 (건너뜀이 아니면 null)
   * @param skipCause 건너뛴 <b>종류</b> (건너뜀이 아니거나 종류를 모르면 null). 세션 만료만 위쪽에서 다르게 다뤄지는데, 그 갈림을 사유 문자열
   *     비교로 하면 문구를 다듬는 순간 조용히 깨진다 ({@link SessionCollector.SkipCause} 참조)
   */
  public record CollectOutcome(
      String collector,
      String status,
      CollectException cause,
      String reason,
      SessionCollector.SkipCause skipCause) {

    /** 종류를 가릴 것이 없는 결과(성공·실패·브레이커 차단)를 위한 짧은 형태. */
    public CollectOutcome(String collector, String status, CollectException cause, String reason) {
      this(collector, status, cause, reason, null);
    }

    public static final String SUCCESS = "SUCCESS";
    public static final String FAIL = "FAIL";
    public static final String SKIPPED = "SKIPPED";

    /**
     * 예외 없이 0건을 받았다.
     *
     * <p>{@code SUCCESS} 와 구분하는 이유는 <b>0건이 두 가지를 뜻하기 때문</b>이다 — 정말 안 샀거나, 셀렉터가 깨져 아무것도 못 읽었거나. 한
     * 회차만 봐서는 구분할 수 없으므로 추측하지 않고 구분해 기록만 한다. 얼마나 오래 이어지는지는 {@code CollectHealthPolicy} 가 본다. (Phase
     * 3-4)
     *
     * <p>실패는 아니므로 브레이커의 연속 실패 카운트는 초기화한다 — 로그인도 됐고 예외도 없었으니 사이트는 살아 있다.
     */
    public static final String EMPTY = "EMPTY";

    public boolean isFailure() {
      return FAIL.equals(status);
    }

    public boolean isSuccess() {
      return SUCCESS.equals(status);
    }

    /** 브레이커 관점에서 '실패가 아닌 완주'인가. {@code SUCCESS} 와 {@code EMPTY} 가 여기 해당한다. */
    public boolean isCompleted() {
      return SUCCESS.equals(status) || EMPTY.equals(status);
    }

    /**
     * 주입한 세션이 죽어서 물러난 회차인가 (Phase 5-10 배선).
     *
     * <p>{@code MallOrderUpdaterRunner} 가 이 값을 보고 실행 결과를 {@code MallCollectOutcome.sessionExpired}
     * 로 승격한다. 승격되지 않으면 스케줄러·즉시수집의 만료 분기는 <b>도달 불가 코드</b>가 되고, 일시중단·재개가 전부 무동작이 된다.
     */
    public boolean sessionExpired() {
      return skipCause != null && skipCause.sessionExpired();
    }
  }

  /**
   * 이번 회차에 돌릴 수집기 한 자리 (Phase 5-9′).
   *
   * <p>자격증명 경로와 세션 주입 경로 중 <b>하나만</b> 채워진다. 두 목록을 따로 들고 다니지 않는 이유는 <b>선언 순서를 지키기 위해서</b>다 — seq=1 은
   * SSG 다음 Emart 순으로 돌게 선언돼 있고, 세션 경로가 SSG 자리를 대체해도 그 순서는 그대로여야 한다. 목록을 나누면 대체된 자리가 맨 뒤로 밀린다.
   *
   * <p>셋째 형태가 있다 — <b>돌리지 않기로 이미 정해진 자리</b>({@link #skipped})다. 계획에서 아예 빼지 않는 이유는 그러면 그 수집기가 화면에서
   * 소리 없이 사라지기 때문이다. 자리를 남겨 두면 사유가 한 행으로 남아 사람이 "왜 Emart 가 안 도는가" 를 물을 수 있다.
   *
   * @param name 수집기 이름 (로그·브레이커 키)
   * @param credential 자격증명 수집기. 세션 자리·건너뛴 자리면 null
   * @param session 세션 주입 수집기. 자격증명 자리·건너뛴 자리면 null
   * @param skipReason 이 자리를 돌리지 않는 사유. 돌릴 자리면 null
   */
  record PlannedCollector(
      String name,
      MallRegistry.CollectorSpec credential,
      MallRegistry.SessionCollectorSpec session,
      String skipReason) {

    static PlannedCollector of(MallRegistry.CollectorSpec spec) {
      return new PlannedCollector(spec.name(), spec, null, null);
    }

    static PlannedCollector of(MallRegistry.SessionCollectorSpec spec) {
      return new PlannedCollector(spec.name(), null, spec, null);
    }

    /** 돌리지 않기로 이미 정해진 자리. 브레이커는 건드리지 않는다. */
    static PlannedCollector skipped(String name, String reason) {
      return new PlannedCollector(name, null, null, reason);
    }

    boolean isSession() {
      return session != null;
    }

    /** 이번 회차에 돌리지 않는 자리인가. */
    boolean isSkipped() {
      return credential == null && session == null;
    }
  }

  /**
   * 몰별 세션 옵트인({@code jbg_mall.session_profile_enabled})을 읽는 자리.
   *
   * <p>DB 를 붙잡는 유일한 지점이라 여기만 갈아 끼우면 이중 게이트 판정을 DB 없이 검사할 수 있다. 실패를 삼키지 않고 올린다 — 삼키면 '옵트인이 꺼져 있다' 와
   * '읽지 못했다' 가 같은 값이 되어, 옵트인을 켜 뒀는데 조회가 깨진 상태를 아무도 못 본다.
   */
  @FunctionalInterface
  interface MallOptInReader {
    boolean isOptedIn(String seqMall) throws Exception;
  }

  /**
   * 저장된 자격증명이 없어 자격증명 수집기를 건너뛴 사유.
   *
   * <p>화면에 그대로 나가는 문구다. "실패" 가 아니라 "건너뛴다" 라고 적는 것이 중요하다 — 세션만으로 도는 몰에서는 이것이 <b>정상 상태</b>이고, 실패로 읽히면
   * 사람이 있지도 않은 계정 문제를 뒤진다.
   */
  static final String REASON_NO_CREDENTIALS = "저장된 자격증명이 없어 이 수집기는 건너뛴다";

  /** 이번 수집에서 각 수집기가 어떻게 끝났는지. */
  private final List<CollectOutcome> outcomes = new ArrayList<>();

  /** 브레이커 상태 저장소. 판정 규칙은 {@link CollectBreakerPolicy} 가 갖는다. */
  private final JbgCollectBreakerDataAccessObject breakerDao =
      new JbgCollectBreakerDataAccessObject();

  /** 몰별 세션 옵트인 조회. 기본값은 DB 다. */
  private final MallOptInReader optInReader;

  /** 프로덕션 배선. */
  public MallOrderUpdater() {
    this(MallOrderUpdater::readOptInFromDatabase);
  }

  /** 테스트가 옵트인 조회를 갈아 끼우는 생성자. */
  MallOrderUpdater(MallOptInReader optInReader) {
    this.optInReader = optInReader;
  }

  /**
   * 이번 수집의 수집기별 결과를 반환한다. 호출측(runner)이 각각을 jbg_collect_log 에 기록해야 한다.
   *
   * @return 수집기별 결과 (없으면 빈 리스트)
   */
  public List<CollectOutcome> getOutcomes() {
    return new ArrayList<>(outcomes);
  }

  /**
   * 이번 수집에서 발생한 부분 실패 목록을 반환한다.
   *
   * @return 부분 실패 목록 (없으면 빈 리스트)
   */
  public List<CollectFailure> getPartialFailures() {
    List<CollectFailure> failures = new ArrayList<>();
    for (CollectOutcome outcome : outcomes) {
      if (outcome.isFailure()) {
        failures.add(new CollectFailure(outcome.collector(), outcome.cause()));
      }
    }
    return failures;
  }

  /**
   * 각 쇼핑몰에서의 주문 내역들을 수집한다
   *
   * @return
   * @throws Exception
   */
  public JSONArray collectItems(String seqMall, String mallId, String mallPw) throws Exception {
    logger.info("구매내역 수집 시작 - seqMall: {}, mallId: {}", seqMall, mallId);

    // 수집 시작 전에 로그인 시간 갱신
    JbgMallDataAccessObject jaDao = new JbgMallDataAccessObject();
    JSONObject accessInfo = jaDao.getAccessInfo(seqMall);
    if (accessInfo == null) {
      jaDao.add(seqMall, 1, null, null);
    } else {
      jaDao.updateLastSigninTime(seqMall);
    }

    // 브레이커의 백오프 기준값. 주기를 못 읽으면 0 을 넘겨 정책 기본값을 쓰게 한다.
    int intervalMinutes = 0;
    if (accessInfo != null && accessInfo.get("collect_interval_minutes") != null) {
      try {
        intervalMinutes = Integer.parseInt(accessInfo.get("collect_interval_minutes").toString());
      } catch (NumberFormatException ignore) {
      }
    }
    final int interval = intervalMinutes;

    JSONArray itemArr = new JSONArray();
    int seqMallInt = Integer.parseInt(seqMall);

    // 어떤 수집기를 돌릴지는 MallRegistry 한 곳이 선언한다 (Phase 3-12).
    // 수집기가 여럿인 몰(seq=1)에서 한쪽이 실패해도 다른 쪽은 반드시 시도한다 — SSG 온라인몰과
    // Emart 오프라인 영수증은 서로 독립적인 데이터원이라 하나의 실패가 다른 하나를 막을 이유가 없다.
    //
    // 세션 주입 경로는 이중 게이트(마스터 킬스위치 + 몰별 옵트인) 뒤에 있다 (Phase 5-9′).
    // 게이트가 꺼져 있으면 아래 plan 은 예전과 완전히 같은 목록이 되고, 아래 루프도 예전과 같은
    // 분기만 탄다 — 옵트인 OFF 몰의 런타임 동작이 바뀌지 않는다는 보장이 거기서 나온다.
    MallRegistry mall = MallRegistry.bySeq(seqMallInt).orElse(null);
    List<PlannedCollector> plan =
        planCollectors(
            mall, sessionRouteApplies(mall, String.valueOf(seqMallInt)), hasCredentials(mallId));
    if (plan.isEmpty()) {
      logger.warn("등록되지 않은 쇼핑몰 seq={} — 수집기가 없다", seqMall);
    }

    int attempted = 0;
    for (PlannedCollector planned : plan) {
      if (planned.isSkipped()) {
        // 돌리지 않기로 계획 단계에서 이미 정해진 자리다 (지금은 '자격증명 없음' 하나뿐).
        //
        // 브레이커를 건드리지 않는다. 세션 만료를 FAIL 로 세지 않는 것과 같은 이유다 — 자격증명이
        // 없는 것은 사이트 장애가 아닌데 연속 실패로 계산되면 브레이커가 그 수집기를 자동 차단하고,
        // 사람이 계정을 다시 연결해도 쿨다운이 끝날 때까지 돌지 않는다.
        //
        // '시도' 로도 세지 않는다. 세면 아래 전멸 판정(전부 실패면 예외로 올린다)의 분모가 부풀어,
        // 실제로 돈 수집기가 전부 실패해도 그 사실이 상위로 올라가지 않는다.
        logger.warn("{} 수집 건너뜀 — {}", planned.name(), planned.skipReason());
        outcomes.add(
            new CollectOutcome(planned.name(), CollectOutcome.SKIPPED, null, planned.skipReason()));
        continue;
      }

      attempted++;
      if (planned.isSession()) {
        MallRegistry.SessionCollectorSpec spec = planned.session();
        // seq 는 정규화한 값을 넘긴다 — 스냅샷 파일명·DB 키로 쓰이므로 공백이 섞인 원문을 그대로
        // 넘기면 캡처가 쓴 자리와 수집이 읽는 자리가 갈릴 수 있다.
        itemArr.addAll(
            collectFromSession(
                spec.name(),
                seqMallInt,
                interval,
                () -> spec.create(String.valueOf(seqMallInt)).collect()));
      } else {
        MallRegistry.CollectorSpec spec = planned.credential();
        itemArr.addAll(
            collectFrom(
                spec.name(), seqMallInt, interval, () -> spec.create(mallId, mallPw).getItems()));
      }
    }

    List<CollectFailure> failures = getPartialFailures();

    // 시도한 수집기가 전부 실패했다면 이번 수집은 실패다. 컨텍스트를 담은 채로 상위에 전파한다.
    // 건너뛴 수집기는 실패가 아니므로 이 판정에 들어가지 않는다 — 브레이커가 이미 판단해서 막은 것을
    // 다시 실패로 세면 같은 사실이 두 번 계산된다.
    if (attempted > 0 && failures.size() == attempted) {
      throw failures.get(0).cause();
    }

    // 이 수는 더 이상 브레이커 것만이 아니다 — 자격증명 없음·세션 없음·주입 반영 0개·세션 만료가
    // 모두 SKIPPED 로 들어온다. 예전 문구("브레이커 건너뜀")를 그대로 두면 세션이 죽어서 물러난
    // 회차를 읽은 사람이 브레이커 상태부터 뒤지게 된다. 사유별 내역은 수집기별 행에 남아 있다.
    long skipped = outcomes.stream().filter(o -> CollectOutcome.SKIPPED.equals(o.status())).count();
    logger.info(
        "전체 수집 완료 - 총 {} 건 (부분 실패 {} 건, 건너뜀 {} 건)", itemArr.size(), failures.size(), skipped);
    return itemArr;
  }

  /**
   * 이 몰에 세션 주입 경로를 적용할지 판정한다 — <b>이중 게이트</b> (Phase 5-9′).
   *
   * <p>순서가 곧 비용 순서다. 값싼 것부터 본다.
   *
   * <ol>
   *   <li><b>마스터 킬스위치.</b> {@code System.getProperty} 하나라 I/O 가 없다. 기본값이 꺼짐이므로 <b>대부분의 실행은 여기서
   *       끝난다</b> — 그 아래 어떤 코드도, DB 조회조차 돌지 않는다.
   *   <li><b>그 몰이 세션 수집기를 선언했는가.</b> 메모리 조회다. 선언하지 않은 몰(oasis·hanaro)은 킬스위치가 켜져 있어도 여기서 끝나므로 조회가 늘지
   *       않는다.
   *   <li><b>몰별 옵트인.</b> 여기서 처음 DB 를 읽는다. 즉 추가 조회는 <b>세션 경로를 실제로 쓸 수 있는 몰</b>에서만 생긴다.
   * </ol>
   *
   * <p><b>조회가 실패하면 기존 경로로 간다.</b> 앞의 {@code SsgSessionCollector} 는 "세션이 없으면 비밀번호로 폴백하지 않는다"고 정했는데,
   * 여기는 반대로 정한 것처럼 보인다. 다른 질문이라 그렇다 — 저기는 <b>옵트인한 것이 확실한</b> 몰에서 세션만 없는 상황이고(그때 폴백하면 사용자가 켠 설정을 코드가
   * 몰래 뒤집는다), 여기는 <b>옵트인했는지 자체를 모르는</b> 상황이다. 모르면 기능 전체의 기본값인 '꺼짐' 으로 읽는 것이 킬스위치의 계약과 같다.
   *
   * @param mall 등록된 몰. 등록되지 않은 seq 면 null
   * @param seqMall {@code jbg_mall.seq} (숫자 문자열)
   * @return 세션 주입 경로를 쓸지
   */
  boolean sessionRouteApplies(MallRegistry mall, String seqMall) {
    if (!SessionProfilePolicy.isEnabled()) {
      return false;
    }
    if (mall == null || mall.sessionCollectors().isEmpty()) {
      return false;
    }
    try {
      return optInReader.isOptedIn(seqMall);
    } catch (Exception e) {
      logger.warn("세션 옵트인 조회 실패({}) — 기존 경로로 진행한다. seq={}", e.getClass().getSimpleName(), seqMall);
      return false;
    }
  }

  /**
   * 이번 회차에 돌릴 수집기 목록을 정한다.
   *
   * <p>순수 함수다 — DB·브라우저·시스템 프로퍼티를 읽지 않는다. 판정 결과({@code sessionRouteApplies})를 값으로 받는다.
   *
   * <p>세션 수집기는 자기가 선언한 {@code replaces} 자리를 <b>대체</b>한다. 덧붙이지 않는 이유는, 둘 다 돌리면 비밀번호 로그인이 그대로 남아 이
   * 국면의 목적이 성립하지 않기 때문이다. 대체되지 않은 자리(seq=1 의 Emart)는 그대로 자격증명 경로로 돈다.
   *
   * <h2>자격증명이 없으면 자격증명 수집기를 건너뛴다 — 킬스위치를 켜기 전 필수 조건이다</h2>
   *
   * <p>이 검사가 없으면 <b>세션만 있는 seq=1 이 Emart 를 자동으로 죽인다.</b> 세션 경로는 SSG 자리 하나만 대체하므로 Emart 는 자격증명 경로로
   * 남는데, 그 몰에는 저장된 아이디·비밀번호가 없어 {@code mallId}·{@code mallPw} 가 null 로 내려온다. 그대로 돌리면 null 로 로그인을
   * 시도해 매 회차 FAIL 이 쌓이고, 연속 실패가 임계를 넘는 순간 <b>브레이커가 Emart 를 차단한다.</b> 그다음부터는 사람이 세션을 다시 떠도 쿨다운이 끝날
   * 때까지 Emart 는 돌지 않는다 — 세션 기능을 켠 것만으로 멀쩡하던 오프라인 영수증 수집이 멈춘다.
   *
   * <p>그래서 그 자리를 <b>건너뜀</b>으로 남긴다. 계획에서 통째로 빼지 않는 이유는 그러면 사유가 어디에도 남지 않아, 사람이 "Emart 는 왜 안 도는가" 를
   * 물을 단서가 사라지기 때문이다.
   *
   * <p>세션 수집기는 이 검사에 걸리지 않는다. 그쪽은 애초에 비밀번호를 쓰지 않으므로 자격증명이 없는 것이 <b>정상</b>이다.
   *
   * @param mall 등록된 몰. null 이면 빈 목록
   * @param sessionRouteApplies 이중 게이트 판정 결과
   * @param hasCredentials 이번 회차에 쓸 수 있는 아이디·비밀번호가 있는가
   * @return 선언 순서를 유지한 실행 계획
   */
  static List<PlannedCollector> planCollectors(
      MallRegistry mall, boolean sessionRouteApplies, boolean hasCredentials) {
    if (mall == null) {
      return List.of();
    }
    List<MallRegistry.SessionCollectorSpec> sessions =
        sessionRouteApplies ? mall.sessionCollectors() : List.of();

    List<PlannedCollector> plan = new ArrayList<>();
    List<String> substituted = new ArrayList<>();
    for (MallRegistry.CollectorSpec spec : mall.collectors()) {
      Optional<MallRegistry.SessionCollectorSpec> replacement =
          sessions.stream().filter(s -> spec.name().equals(s.replaces())).findFirst();
      if (replacement.isPresent()) {
        plan.add(PlannedCollector.of(replacement.get()));
        substituted.add(replacement.get().name());
      } else if (!hasCredentials) {
        plan.add(PlannedCollector.skipped(spec.name(), REASON_NO_CREDENTIALS));
      } else {
        plan.add(PlannedCollector.of(spec));
      }
    }

    // replaces 가 어느 수집기와도 맞지 않는 세션 수집기는 뒤에 덧붙인다. 레지스트리 선언 오류이고
    // MallRegistryTest 가 막고 있지만, 만약 뚫린다면 '조용히 없애는' 쪽이 더 나쁘다 — 그러면
    // 비밀번호 경로가 소리 없이 되살아나고 로그에는 아무 흔적도 남지 않는다. 덧붙이면 로그에
    // 수집기가 둘 다 찍혀 즉시 눈에 띈다.
    for (MallRegistry.SessionCollectorSpec session : sessions) {
      if (!substituted.contains(session.name())) {
        plan.add(PlannedCollector.of(session));
      }
    }
    return plan;
  }

  /**
   * 이번 회차에 쓸 수 있는 자격증명이 있는가.
   *
   * <p>{@code JangBoGoManager.collect} 는 세션 경로에서 복호화를 아예 하지 않고 {@code mallId} 를 null 로 내려보낸다 — 그것이
   * '비밀번호를 메모리에 올리지 않는다' 는 이 국면의 목적이다. 즉 여기서 보는 null 은 사고가 아니라 <b>정상 신호</b>다.
   *
   * <p>공백도 없는 것으로 본다. 빈 문자열로 로그인 폼을 제출하면 실패하는 것은 null 과 같은데, 그것이 FAIL 로 쌓이면 브레이커가 열린다.
   */
  private static boolean hasCredentials(String mallId) {
    return mallId != null && !mallId.isBlank();
  }

  /**
   * 몰별 세션 옵트인을 DB 에서 읽는 기본 구현.
   *
   * @param seqMall {@code jbg_mall.seq} (숫자 문자열)
   * @return {@code session_profile_enabled} 가 1 이면 true
   * @throws Exception 조회 실패
   */
  private static boolean readOptInFromDatabase(String seqMall) throws Exception {
    JSONObject mall = new JbgMallDataAccessObject().getMall(seqMall);
    if (mall == null) {
      return false;
    }
    Object value = mall.get("session_profile_enabled");
    if (value instanceof Number number) {
      return number.intValue() == 1;
    }
    return value != null && "1".equals(String.valueOf(value).trim());
  }

  /**
   * 수집기 하나를 실행한다. 실패해도 예외를 전파하지 않고 {@link #partialFailures} 에 쌓아 다음 수집기가 계속 실행되게 한다.
   *
   * <p>실패를 삼키는 것이 아니다 — 호출측 runner 가 {@link #getPartialFailures()} 로 꺼내 jbg_collect_log 에 FAIL 로
   * 기록한다. 이 구분이 없으면 v0.8.0 에서 제거한 "예외를 삼키고 빈 결과를 반환해 성공으로 보이는" 패턴이 되살아난다.
   *
   * @param name 수집기 이름 (로그·실패 기록용)
   * @param collector 수집 동작
   * @return 수집 결과 (실패 시 빈 배열)
   */
  // 테스트에서 직접 호출하기 위해 package-private
  JSONArray collectFrom(String name, Supplier<JSONArray> collector) {
    return collectFrom(name, 0, 0, collector);
  }

  /**
   * 브레이커 판정을 거쳐 수집기 하나를 실행한다.
   *
   * <p><b>판정을 수집기 진입 전에 한다.</b> 브라우저는 각 수집기의 {@code getItems()} 안에서 뜨므로, 여기서 걸러내면 로그인 시도도 브라우저 기동도
   * 일어나지 않는다 — 차단된 사이트를 그만 두드린다는 목적이 그대로 달성된다. (Phase 3-3)
   *
   * @param name 수집기 이름
   * @param seqMall 쇼핑몰 seq ({@code 0} 이면 브레이커를 적용하지 않는다 — 단위테스트용)
   * @param intervalMinutes 몰의 수집 주기 (백오프 기준값)
   * @param collector 수집 동작
   * @return 수집 결과 (실패·건너뜀이면 빈 배열)
   */
  JSONArray collectFrom(
      String name, int seqMall, int intervalMinutes, Supplier<JSONArray> collector) {

    if (breakerBlocks(name, seqMall, intervalMinutes)) {
      return new JSONArray();
    }

    logger.info("{} 구매 내역 수집 시작", name);
    try {
      return recordItems(name, seqMall, collector.get());
    } catch (Exception e) {
      return recordFailure(name, seqMall, e);
    }
  }

  /**
   * 브레이커 판정을 거쳐 <b>세션 주입</b> 수집기 하나를 실행한다 (Phase 5-9′).
   *
   * <p>{@link #collectFrom} 과 다른 점은 하나다 — 이 경로는 <b>건너뜀을 스스로 판단</b>한다. 세션이 없거나 만료된 회차는 실패가 아니라
   * SKIPPED 이고, 그때는 <b>브레이커를 건드리지 않는다.</b> 건드리면 두 가지가 함께 망가진다.
   *
   * <ul>
   *   <li>실패로 세면 세션이 없다는 이유만으로 연속 실패가 쌓여 수집기가 자동 차단되고, 사람이 세션을 다시 떠도 쿨다운이 끝날 때까지 돌지 않는다.
   *   <li>성공으로 세면 {@code last_nonempty_time} 판정이 흐려져, 조용한 실패를 잡아내는 유일한 신호가 사라진다.
   * </ul>
   *
   * <p>사유 문자열은 수집기가 준 것을 그대로 쓴다. 그것이 {@code jbg_collect_log} 에 남고 화면에 나가 사람이 다음 행동(다시 캡처)을 알게 되는
   * 경로다.
   *
   * @param name 수집기 이름
   * @param seqMall 쇼핑몰 seq ({@code 0} 이면 브레이커를 적용하지 않는다 — 단위테스트용)
   * @param intervalMinutes 몰의 수집 주기 (백오프 기준값)
   * @param collector 수집 동작. 예외가 아니라 값으로 건너뜀을 알린다
   * @return 수집 결과 (실패·건너뜀이면 빈 배열)
   */
  JSONArray collectFromSession(
      String name, int seqMall, int intervalMinutes, Supplier<SessionCollector.Result> collector) {

    if (breakerBlocks(name, seqMall, intervalMinutes)) {
      return new JSONArray();
    }

    logger.info("{} 세션 주입 수집 시작 (로그인 폼을 밟지 않는다)", name);
    try {
      SessionCollector.Result result = collector.get();
      if (result == null) {
        // 계약 위반이다. 빈 결과로 정규화하면 '0건 성공' 으로 묻히므로 실패로 올린다.
        throw new IllegalStateException("세션 수집기가 결과를 돌려주지 않았다: " + name);
      }
      if (result.isSkipped()) {
        logger.warn("{} 세션 주입 수집 건너뜀 — {}", name, result.skipReason());
        // 종류를 그대로 실어 올린다. 위쪽(MallOrderUpdaterRunner)이 만료만 골라 실행 결과로 승격하는데,
        // 그 판정을 사유 문자열 비교로 하면 문구를 다듬는 순간 조용히 깨진다.
        //
        // recordBreaker 를 부르지 않는다는 점이 이 분기의 핵심이다. 건너뜀은 실패도 성공도 아니다 —
        // 실패로 세면 세션이 죽었다는 이유만으로 연속 실패가 쌓여 수집기가 자동 차단되고(사람이 세션을
        // 다시 떠도 쿨다운 전까지 안 돈다), 성공으로 세면 last_nonempty_time 이 갱신돼 조용한 실패를
        // 잡아내는 유일한 신호가 사라진다.
        outcomes.add(
            new CollectOutcome(
                name, CollectOutcome.SKIPPED, null, result.skipReason(), result.skipCause()));
        return new JSONArray();
      }
      return recordItems(name, seqMall, result.items());
    } catch (Exception e) {
      return recordFailure(name, seqMall, e);
    }
  }

  /**
   * 브레이커가 이번 회차를 막는지 본다. 막으면 SKIPPED 를 기록한다.
   *
   * <p><b>판정을 수집기 진입 전에 한다.</b> 브라우저는 각 수집기 안에서 뜨므로, 여기서 걸러내면 로그인 시도도 브라우저 기동도 일어나지 않는다 — 차단된 사이트를
   * 그만 두드린다는 목적이 그대로 달성된다. (Phase 3-3)
   *
   * @return 막혔으면 true
   */
  private boolean breakerBlocks(String name, int seqMall, int intervalMinutes) {
    if (seqMall <= 0) {
      return false;
    }
    CollectBreakerPolicy.State state = breakerDao.getState(seqMall, name);
    CollectBreakerPolicy.Decision decision =
        CollectBreakerPolicy.decide(state, intervalMinutes, System.currentTimeMillis());
    if (decision.shouldRun()) {
      return false;
    }
    logger.warn(
        "{} 수집 건너뜀 ({}) - 재시도 예정: {}",
        name,
        decision.reason,
        new java.util.Date(decision.notBefore));
    outcomes.add(new CollectOutcome(name, CollectOutcome.SKIPPED, null, decision.reason));
    return true;
  }

  /** 수집 결과를 SUCCESS/EMPTY 로 기록한다. 두 수집 경로가 같은 규칙을 쓰도록 한 곳에 둔다. */
  private JSONArray recordItems(String name, int seqMall, JSONArray items) {
    int count = (items != null) ? items.size() : 0;

    if (count == 0) {
      // 예외도 없고 로그인도 됐는데 0건이다. 성공으로 뭉뚱그리지 않는다 (Phase 3-4).
      logger.warn("{} 수집 0건 — 조회 기간에 구매가 없었거나 셀렉터가 어긋났다. 한 회차로는 구분할 수 없어 EMPTY 로 기록한다.", name);
      outcomes.add(new CollectOutcome(name, CollectOutcome.EMPTY, null, "수집 0건"));
      recordBreaker(seqMall, name, CollectOutcome.EMPTY, null);
      return new JSONArray();
    }

    logger.info("{} 수집 완료 - {} 건", name, count);
    outcomes.add(new CollectOutcome(name, CollectOutcome.SUCCESS, null, null));
    recordBreaker(seqMall, name, CollectOutcome.SUCCESS, null);
    return items;
  }

  /** 수집 실패를 컨텍스트와 함께 기록한다. 두 수집 경로가 같은 규칙을 쓰도록 한 곳에 둔다. */
  private JSONArray recordFailure(String name, int seqMall, Exception e) {
    CollectException ce = unwrapCollectException(e);
    if (ce == null) {
      ce = CollectStep.wrap(null, name, "collect", null, e);
    }
    outcomes.add(new CollectOutcome(name, CollectOutcome.FAIL, ce, null));
    recordBreaker(seqMall, name, CollectOutcome.FAIL, ce.getMessage());
    logger.error(
        "{} 수집 실패 (다른 수집기는 계속 진행) - 단계: {}, 원인: {}", name, ce.getStepName(), e.getMessage());
    return new JSONArray();
  }

  /**
   * 브레이커 상태를 갱신한다. 트립이 새로 발생하면 경보를 남긴다.
   *
   * <p>{@code EMPTY} 는 실패가 아니므로 연속 실패 카운트를 초기화한다 — 로그인도 됐고 예외도 없었으니 사이트는 살아 있다. 다만 {@code
   * last_nonempty_time} 은 갱신하지 않는다. 0건이 오래 이어지는 것이 조용한 실패의 유일한 신호라, 그 시각이 그대로 멈춰 있어야 {@code
   * CollectHealthPolicy} 가 알아챌 수 있다. (Phase 3-4)
   */
  private void recordBreaker(int seqMall, String name, String status, String reason) {
    if (seqMall <= 0) {
      return;
    }
    try {
      long now = System.currentTimeMillis();
      if (!CollectOutcome.FAIL.equals(status)) {
        boolean gotData = CollectOutcome.SUCCESS.equals(status);
        breakerDao.saveState(
            seqMall,
            name,
            CollectBreakerPolicy.onSuccess(),
            now,
            gotData ? now : 0,
            gotData ? "성공" : "수집 0건");
        return;
      }

      CollectBreakerPolicy.State before = breakerDao.getState(seqMall, name);
      CollectBreakerPolicy.State after = CollectBreakerPolicy.onFailure(before, now);
      breakerDao.saveState(seqMall, name, after, 0, 0, reason);

      if (after.isTripped() && !before.isTripped()) {
        // 경보. 결정 1 의 "스케줄 일시중단 + 경보" 중 경보에 해당한다.
        logger.error(
            "[수집기 자동 차단] {} (몰 seq={}) — 연속 {}회 실패로 브레이커가 열렸다. {}분 뒤 한 번 복귀를 시도한다."
                + " auto_collect 설정은 건드리지 않았다. 마지막 사유: {}",
            name,
            seqMall,
            after.consecutiveFailures,
            CollectBreakerPolicy.cooldownMinutes(),
            reason);
      }
    } catch (Exception e) {
      // 브레이커 갱신 실패가 수집 자체를 막아서는 안 된다.
      logger.warn("브레이커 상태 갱신 실패 ({}): {}", name, e.getMessage());
    }
  }

  /** 예외 체인을 거슬러 올라가 첫 번째 CollectException을 찾는다. 없으면 null. */
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

  // 여기 있던 주석 처리된 updateItems 는 제거했다.
  //
  // JbgItemDataAccessObject.update 의 유일한 '호출부'가 이것이었다. 그래서 그 메서드가 죽은 채로
  // SQL 문자열 조립을 품고 남아 있었고, 보안 점검 때마다 살아 있는 코드처럼 다시 검토됐다.
  // 본문도 이 프로젝트에 없는 클래스(ItemDataAccessObject·AutomationService)와 지금은 다른
  // 시그니처인 joDao.add(...) 를 부르고 있어 되살릴 수 있는 상태가 아니었다.
  //
  // 살아 있는 반영 경로는 MallOrderUpdaterRunner 다.
}

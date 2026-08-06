package com.jiniebox.jangbogo.svc.mall;

import com.jiniebox.jangbogo.svc.ifc.MallSession;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * 쇼핑몰 seq 에 무엇이 딸려 있는지를 선언하는 단일 레지스트리 (Phase 3-12).
 *
 * <h2>왜 필요했나</h2>
 *
 * <p>{@code seq} 로 분기하는 하드코딩된 사슬이 세 곳에 흩어져 있었고, 서로 다른 답을 내놓고 있었다.
 *
 * <table border="1">
 *   <caption>통합 전 seq=1 의 매핑</caption>
 *   <tr><th>경로</th><th>seq=1 →</th></tr>
 *   <tr><td>{@code data.sql} 시드</td><td>{@code id='ssg'}</td></tr>
 *   <tr><td>{@code JangBoGoManager.getMallSession}</td><td>{@code new Emart} 하나만</td></tr>
 *   <tr><td>{@code MallOrderUpdater.collectItems}</td><td>{@code Ssg} + {@code Emart} 둘</td></tr>
 *   <tr><td>{@code ExportService.getMallIdFromSeq}</td><td>{@code "emart"}</td></tr>
 * </table>
 *
 * <p>몰을 하나 추가하려면 네 곳을 손대야 했고, 한 곳을 빠뜨려도 컴파일은 통과한다.
 *
 * <h2>왜 §9-2 답변 없이는 손댈 수 없었나</h2>
 *
 * <p>계정 연결은 seq=1 자격증명을 {@code eapp.emart.com} 으로 보내고, 수집은 같은 값을 거기에 더해 {@code member.ssg.com} 으로도
 * 보낸다. <b>두 경로가 서로 다른 로그인 폼에 같은 아이디·비밀번호를 제출한다.</b> 그 상태에서 매핑을 아무렇게나 통일하면 엉뚱한 사이트에 자격증명을 반복 제출하게
 * 되는데, 그것이 바로 이 계획이 줄이려는 리스크다.
 *
 * <p>사용자 확인으로 <b>seq=1 자격증명은 SSG 통합회원 하나이고 두 사이트 모두에서 인증에 성공한다</b>는 것이 확정되어 통합이 가능해졌다.
 *
 * <h2>id 가 둘인 이유</h2>
 *
 * <p>{@link #mallId()} 는 jangbogo 자신의 {@code jbg_mall.id} 이고, {@link #exportId()} 는 jiniebox 로 보내는
 * 페이로드의 {@code mall_id} 다. seq=1 에서만 두 값이 다르다({@code ssg} vs {@code emart}).
 *
 * <p><b>일부러 통일하지 않았다.</b> 수신측은 자기 {@code jbg_mall.id} 로 먼저 찾고 실패하면 폴백 스위치를 타는데, 그 폴백이 {@code emart}
 * 와 {@code ssg} 를 <b>둘 다 seq 1 로</b> 받는다. 즉 어느 쪽을 보내도 결과는 같다. 그렇다면 검증할 수 없는 변경(수신측 운영 시드는 저장소에 없다)을
 * 리팩터링의 부수효과로 끼워 넣을 이유가 없다. 값이 다르다는 사실 자체를 여기 한 곳에 적어 두는 것이 이 클래스의 일이다.
 *
 * <h2>coupang 이 없는 이유</h2>
 *
 * <p>{@code Coupang} 은 컴파일만 되는 껍데기이고 {@code data.sql}·DB·화면 어디에도 배선되어 있지 않다. 여기 넣으면 "지원되는 몰"로 보이게
 * 된다. Phase 4B(법무 게이트) 통과 후에 추가한다.
 *
 * @author KIUNSEA
 */
public enum MallRegistry {

  /** SSG 계열 — 온라인몰(ssg.com)과 오프라인 영수증(eapp.emart.com)이 서로 독립적인 데이터원이다. */
  SSG_GROUP(
      1,
      "ssg",
      "emart",
      List.of(new CollectorSpec("SSG", Ssg::new), new CollectorSpec("Emart", Emart::new)),
      "Emart",
      "https://www.ssg.com/",
      // ADR-0001 T3 이 캡처된 쿠키에서 실제로 확인한 이름들. 같은 문단이 함께 적고 있는
      // JSESSIONID·FSID 는 일부러 뺐다 — 사유는 authCookieNames() javadoc 참조.
      List.of("LOGIN_YN", "MEMBER_ID", "MBR_ID_ED_NO")),

  /** 오아시스마켓. */
  OASIS(
      2,
      "oasis",
      "oasis",
      List.of(new CollectorSpec("Oasis", Oasis::new)),
      "Oasis",
      "https://www.oasis.co.kr/login",
      // 인증 쿠키 이름 미확정. 추측해 채우면 정상 로그인까지 튕겨 캡처가 불가능해진다 — 비워 둔다.
      List.of()),

  /** 하나로마트. */
  HANARO(
      3,
      "hanaro",
      "hanaro",
      List.of(new CollectorSpec("Hanaro", Hanaro::new)),
      "Hanaro",
      "https://www.nonghyupmall.com/BC41000R/loginViewPage.nh",
      // 인증 쿠키 이름 미확정. OASIS 와 같은 이유로 비워 둔다.
      List.of());

  /** {@code mall_id} 를 못 찾았을 때 내보내기가 쓰는 값. */
  public static final String UNKNOWN_EXPORT_ID = "unknown";

  /**
   * 수집기 하나의 선언.
   *
   * @param name 수집기 이름. {@code jbg_collect_log.collector} 와 브레이커 키에 그대로 쓰이므로 <b>바꾸면 기존 상태와 연결이
   *     끊긴다</b>
   * @param factory 아이디·비밀번호로 수집기를 만드는 함수
   */
  public record CollectorSpec(String name, BiFunction<String, String, MallSession> factory) {

    /** 수집기 인스턴스를 만든다. */
    public MallSession create(String userId, String userPass) {
      return factory.apply(userId, userPass);
    }
  }

  private final int seq;
  private final String mallId;
  private final String exportId;
  private final List<CollectorSpec> collectors;
  private final String verificationCollectorName;
  private final String loginUrl;
  private final List<String> authCookieNames;

  MallRegistry(
      int seq,
      String mallId,
      String exportId,
      List<CollectorSpec> collectors,
      String verificationCollectorName,
      String loginUrl,
      List<String> authCookieNames) {
    this.seq = seq;
    this.mallId = mallId;
    this.exportId = exportId;
    this.collectors = collectors;
    this.verificationCollectorName = verificationCollectorName;
    this.loginUrl = loginUrl;
    this.authCookieNames = authCookieNames;
  }

  /** {@code jbg_mall.seq}. */
  public int seq() {
    return seq;
  }

  /** jangbogo 자신의 {@code jbg_mall.id}. */
  public String mallId() {
    return mallId;
  }

  /** jiniebox 페이로드의 {@code mall_id}. seq=1 에서만 {@link #mallId()} 와 다르다 (클래스 javadoc 참조). */
  public String exportId() {
    return exportId;
  }

  /** 이 몰에서 돌릴 수집기들. 선언 순서대로 실행된다. */
  public List<CollectorSpec> collectors() {
    return collectors;
  }

  /**
   * 세션 캡처 시 사람을 착지시킬 로그인 페이지 (Phase 5-15).
   *
   * <p><b>정확성이 아니라 편의다.</b> 캡처는 CDP {@code Network.getAllCookies} 로 도메인을 가리지 않고 전량을 뜨므로, 사람이 이 창에서
   * 어느 도메인에 로그인하든 그 쿠키가 담긴다. SSG 계열처럼 데이터원이 둘이어도 한 캡처로 둘 다 담을 수 있어, 여기서는 대표 로그인 지점 하나만 준다.
   *
   * @return 로그인 페이지 URL
   */
  public String loginUrl() {
    return loginUrl;
  }

  /**
   * 캡처한 스냅샷이 <b>로그인된 세션</b>인지 가를 때 이름을 볼 인증 쿠키들 (Phase 5-15).
   *
   * <p><b>왜 필요한가.</b> 캡처 성공 판정이 "스냅샷이 비어 있지 않다" 뿐이면 로그인하지 않아도 통과한다. 몰 첫 화면만 열어도 세션·추적 쿠키는 생기므로, 사람이
   * 로그인을 건너뛴 채 [로그인을 마쳤습니다] 를 눌러도 미인증 스냅샷이 저장된다. 그 실패는 나중에 주입 수집이 로그인 화면으로 밀릴 때에야 드러나고, 그 시점에는 원인을
   * 캡처까지 되짚을 단서가 없다.
   *
   * <p><b>비어 있으면 검사하지 않고 통과시킨다.</b> 이름이 실측된 것은 ssg 뿐이고(ADR-0001 T3 이 캡처된 쿠키 목록에서 확인했다) oasis·hanaro
   * 는 어느 쿠키가 인증을 나르는지 모른다. 모르는 몰까지 막으면 정상적으로 로그인한 사람도 계속 튕겨 <b>그 몰의 캡처 자체가 불가능해진다</b> — 미인증 스냅샷 한
   * 건보다 나쁜 역전이다. 그래서 모르는 것은 비워 두고 통과시킨다.
   *
   * <p>같은 이유로 <b>실측하지 않은 이름을 추측해 적지 않는다.</b> 틀린 이름 하나가 그 몰의 캡처를 전부 막는다. 캡처된 쿠키 목록으로 확인한 뒤에만 채운다.
   *
   * <p><b>왜 JSESSIONID·FSID 를 뺐나.</b> ADR-0001 은 ssg 의 세션 스코프 쿠키로 {@code LOGIN_YN}·{@code
   * MEMBER_ID}·{@code MBR_ID_ED_NO} 와 함께 {@code JSESSIONID}(www·member 양쪽)·{@code FSID} 계열도 적고 있으나,
   * 뒤 둘은 서블릿 세션·추적 식별자라 <b>로그인 전에도 발급된다.</b> 판정에 넣으면 첫 화면만 열어도 통과해 이 검사가 있으나 마나가 된다 — 그것이 바로 여기서
   * 막으려는 상황이다.
   *
   * <p>판정은 <b>하나라도 있으면 인증</b>이다. 전부를 요구하면 몰이 쿠키 구성을 조금만 바꿔도 정상 로그인이 막히는데, 그 역시 위와 같은 역전 사고다.
   *
   * @return 인증 쿠키 이름. 실측으로 확정되지 않았으면 빈 목록
   */
  public List<String> authCookieNames() {
    return authCookieNames;
  }

  /**
   * 계정 연결 시 자격증명을 검증할 수집기.
   *
   * <p>수집기가 여럿이어도 <b>검증은 하나로만 한다.</b> 연결 한 번에 두 사이트로 로그인하면 이 프로젝트가 줄이려는 반복 로그인을 스스로 늘리게 된다.
   *
   * <p>seq=1 이 {@code Emart} 인 것은 통합 전 동작을 그대로 옮긴 것이다. §9-2 확인에 따르면 {@code SSG} 로 바꿔도 인증은 성공하지만, 검증
   * 로그인이 향하는 사이트를 바꾸는 것은 리팩터링이 낼 변화가 아니다.
   */
  public CollectorSpec verificationCollector() {
    return collectors.stream()
        .filter(c -> c.name().equals(verificationCollectorName))
        .findFirst()
        .orElse(collectors.get(0));
  }

  /**
   * seq 로 몰을 찾는다.
   *
   * @param seq {@code jbg_mall.seq}
   * @return 등록된 몰. 없으면 {@link Optional#empty()}
   */
  public static Optional<MallRegistry> bySeq(int seq) {
    for (MallRegistry mall : values()) {
      if (mall.seq == seq) {
        return Optional.of(mall);
      }
    }
    return Optional.empty();
  }

  /**
   * seq 문자열로 몰을 찾는다. 숫자가 아니면 비어 있는 값을 돌려준다.
   *
   * @param seq {@code jbg_mall.seq} 문자열
   * @return 등록된 몰. 없거나 숫자가 아니면 {@link Optional#empty()}
   */
  public static Optional<MallRegistry> bySeq(String seq) {
    if (seq == null || seq.isBlank()) {
      return Optional.empty();
    }
    try {
      return bySeq(Integer.parseInt(seq.trim()));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }
}

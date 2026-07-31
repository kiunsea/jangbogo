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
      "Emart"),

  /** 오아시스마켓. */
  OASIS(2, "oasis", "oasis", List.of(new CollectorSpec("Oasis", Oasis::new)), "Oasis"),

  /** 하나로마트. */
  HANARO(3, "hanaro", "hanaro", List.of(new CollectorSpec("Hanaro", Hanaro::new)), "Hanaro");

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

  MallRegistry(
      int seq,
      String mallId,
      String exportId,
      List<CollectorSpec> collectors,
      String verificationCollectorName) {
    this.seq = seq;
    this.mallId = mallId;
    this.exportId = exportId;
    this.collectors = collectors;
    this.verificationCollectorName = verificationCollectorName;
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

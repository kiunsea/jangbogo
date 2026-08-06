package com.jiniebox.jangbogo.svc.mall;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jiniebox.jangbogo.svc.mall.MallRegistry.CollectorSpec;
import com.jiniebox.jangbogo.svc.mall.MallRegistry.SessionCollectorSpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 쇼핑몰 레지스트리 검증 (Phase 3-12).
 *
 * <p>브라우저·네트워크·DB 를 쓰지 않는다 — 수집기를 <b>만들기만</b> 하고 {@code getItems()} 는 부르지 않는다.
 *
 * @author KIUNSEA
 */
class MallRegistryTest {

  private static final String CREDENTIAL_ID = "test-id";
  private static final String CREDENTIAL_PW = "test-pass";

  @Test
  @DisplayName("등록되지 않은 seq 는 비어 있는 값을 돌려준다")
  void unknownSeqIsEmpty() {
    assertTrue(MallRegistry.bySeq(0).isEmpty());
    assertTrue(MallRegistry.bySeq(99).isEmpty());
    assertTrue(MallRegistry.bySeq("abc").isEmpty(), "숫자가 아니면 예외가 아니라 빈 값이어야 한다.");
    assertTrue(MallRegistry.bySeq((String) null).isEmpty());
    assertTrue(MallRegistry.bySeq("  ").isEmpty());
  }

  @Test
  @DisplayName("문자열 seq 도 앞뒤 공백을 허용한다")
  void acceptsPaddedStringSeq() {
    assertEquals(MallRegistry.SSG_GROUP, MallRegistry.bySeq(" 1 ").orElseThrow());
  }

  @Test
  @DisplayName("seq=1 은 SSG·Emart 두 수집기를 선언 순서대로 가진다")
  void ssgGroupHasTwoCollectorsInOrder() {
    List<CollectorSpec> collectors = MallRegistry.bySeq(1).orElseThrow().collectors();

    assertEquals(2, collectors.size());
    assertEquals("SSG", collectors.get(0).name(), "순서가 바뀌면 실행 순서도 바뀐다.");
    assertEquals("Emart", collectors.get(1).name());
  }

  @Test
  @DisplayName("수집기 팩토리가 올바른 클래스를 만든다")
  void factoriesProduceTheRightCollectors() {
    assertInstanceOf(
        Ssg.class, MallRegistry.SSG_GROUP.collectors().get(0).create(CREDENTIAL_ID, CREDENTIAL_PW));
    assertInstanceOf(
        Emart.class,
        MallRegistry.SSG_GROUP.collectors().get(1).create(CREDENTIAL_ID, CREDENTIAL_PW));
    assertInstanceOf(
        Oasis.class, MallRegistry.OASIS.collectors().get(0).create(CREDENTIAL_ID, CREDENTIAL_PW));
    assertInstanceOf(
        Hanaro.class, MallRegistry.HANARO.collectors().get(0).create(CREDENTIAL_ID, CREDENTIAL_PW));
  }

  @Test
  @DisplayName("계정 연결 검증은 수집기 하나로만 한다")
  void verificationUsesExactlyOneCollector() {
    // 연결 한 번에 두 사이트로 로그인하면 이 프로젝트가 줄이려는 반복 로그인을 스스로 늘리게 된다.
    for (MallRegistry mall : MallRegistry.values()) {
      CollectorSpec spec = mall.verificationCollector();

      assertNotNull(spec, mall + " 에 검증용 수집기가 없다.");
      assertTrue(mall.collectors().contains(spec), mall + " 의 검증용 수집기가 수집기 목록에 없다: " + spec.name());
    }
  }

  @Test
  @DisplayName("seq=1 의 검증 수집기는 Emart 다 (통합 전 동작 유지)")
  void ssgGroupVerifiesWithEmart() {
    // §9-2 확인에 따르면 SSG 로 바꿔도 인증은 성공한다. 다만 검증 로그인이 향하는 사이트를 바꾸는 것은
    // 리팩터링이 낼 변화가 아니다.
    assertEquals("Emart", MallRegistry.SSG_GROUP.verificationCollector().name());
  }

  @Test
  @DisplayName("내보내기 id 는 통합 전과 같다")
  void exportIdsAreUnchanged() {
    // 수신측은 emart 와 ssg 를 둘 다 seq 1 로 받으므로 어느 쪽이든 동작하지만,
    // 검증할 수 없는 전송 포맷 변경을 리팩터링에 끼워 넣지 않는다.
    assertEquals("emart", MallRegistry.bySeq(1).orElseThrow().exportId());
    assertEquals("oasis", MallRegistry.bySeq(2).orElseThrow().exportId());
    assertEquals("hanaro", MallRegistry.bySeq(3).orElseThrow().exportId());
  }

  @Test
  @DisplayName("레지스트리의 mallId 가 data.sql 시드와 일치한다")
  void mallIdsMatchTheDatabaseSeed() throws Exception {
    // 레지스트리와 시드가 갈라지면 계정 연결·수집·내보내기가 서로 다른 몰을 가리키게 된다.
    String dataSql =
        Files.readString(Path.of("src/main/resources/data.sql"), StandardCharsets.UTF_8);

    for (MallRegistry mall : MallRegistry.values()) {
      Pattern seeded =
          Pattern.compile("\\(\\s*" + mall.seq() + "\\s*,\\s*'([^']+)'", Pattern.MULTILINE);
      Matcher m = seeded.matcher(dataSql);

      assertTrue(m.find(), "data.sql 에 seq=" + mall.seq() + " 시드가 없다.");
      assertEquals(
          m.group(1),
          mall.mallId(),
          "seq=" + mall.seq() + " 의 id 가 data.sql 시드와 다르다. 레지스트리와 시드는 같아야 한다.");
    }
  }

  @Test
  @DisplayName("seq 와 수집기 이름에 중복이 없다")
  void seqAndCollectorNamesAreUnique() {
    // 수집기 이름은 jbg_collect_log.collector 와 브레이커 키로 쓰인다. 겹치면 상태가 섞인다.
    List<Integer> seqs = Arrays.stream(MallRegistry.values()).map(MallRegistry::seq).toList();
    assertEquals(seqs.size(), Set.copyOf(seqs).size(), "seq 가 중복됐다: " + seqs);

    // 세션 주입 수집기도 같은 이름 공간을 쓴다. 자격증명 수집기와 겹치면 두 경로의 브레이커 상태가
    // 한 키에 섞여, 세션 만료로 건너뛴 것이 비밀번호 로그인 실패로 계산된다.
    List<String> names =
        Arrays.stream(MallRegistry.values())
            .flatMap(
                m ->
                    Stream.concat(
                        m.collectors().stream().map(CollectorSpec::name),
                        m.sessionCollectors().stream().map(SessionCollectorSpec::name)))
            .toList();
    assertEquals(names.size(), Set.copyOf(names).size(), "수집기 이름이 중복됐다: " + names);
  }

  @Test
  @DisplayName("세션 주입 수집기는 실재하는 수집기 자리를 대체한다 (Phase 5-9′)")
  void sessionCollectorsReplaceADeclaredCollector() {
    // replaces 가 어긋나면 그 자리가 대체되지 않아 비밀번호 로그인이 그대로 남는다. 즉 '비밀번호
    // 없이 수집' 이 컴파일도 통과하고 테스트도 없이 조용히 무너진다. 여기서만 막을 수 있다.
    for (MallRegistry mall : MallRegistry.values()) {
      List<String> declared = mall.collectors().stream().map(CollectorSpec::name).toList();
      for (SessionCollectorSpec session : mall.sessionCollectors()) {
        assertTrue(
            declared.contains(session.replaces()),
            mall + " 의 세션 수집기가 없는 자리를 대체하려 한다: " + session.replaces() + " / 선언: " + declared);
        assertNotEquals(
            session.replaces(),
            session.name(),
            mall + " 의 세션 수집기 이름이 대체 대상과 같다 — 브레이커 상태가 한 키에 섞인다.");
      }
    }
  }

  @Test
  @DisplayName("세션 주입 수집기를 선언한 몰은 아직 ssg 하나뿐이다")
  void onlySsgDeclaresASessionCollector() {
    // 파일럿 범위다. 나머지 몰은 '로그인 화면으로 밀렸다' 를 가를 마커를 실측하지 않았고,
    // 추측해 넣으면 만료를 도달로 읽어 0건이 성공으로 굳는다.
    assertEquals(1, MallRegistry.SSG_GROUP.sessionCollectors().size());
    assertTrue(MallRegistry.OASIS.sessionCollectors().isEmpty(), "미실측 몰에 세션 경로를 넣었다.");
    assertTrue(MallRegistry.HANARO.sessionCollectors().isEmpty(), "미실측 몰에 세션 경로를 넣었다.");
  }

  @Test
  @DisplayName("ssg 의 세션 수집기는 SSG 자리를 대체하고 Emart 는 건드리지 않는다")
  void ssgSessionCollectorReplacesOnlyTheOnlineMall() {
    // Emart 는 eapp.emart.com 이라 도메인도 로그인 폼도 다르다. 그쪽 세션이 이 스냅샷에 담겨
    // 있다는 근거가 없으므로 대체하면 조용히 0건이 된다.
    SessionCollectorSpec spec = MallRegistry.SSG_GROUP.sessionCollectors().get(0);

    assertEquals("SSG", spec.replaces());
    assertEquals("SsgSession", spec.name());
    assertInstanceOf(SsgSessionCollector.class, spec.create("1"));
  }

  @Test
  @DisplayName("몰마다 세션 캡처 착지용 로그인 URL 이 있다 (Phase 5-15)")
  void everyMallHasHttpsLoginUrl() {
    // 자격증명 없이 seq 만으로 이 값을 얻어야 캡처 start 가 로그인 페이지로 착지시킬 수 있다.
    for (MallRegistry mall : MallRegistry.values()) {
      String url = mall.loginUrl();
      assertNotNull(url, mall + " 에 로그인 URL 이 없다.");
      assertTrue(url.startsWith("https://"), mall + " 의 로그인 URL 이 https 가 아니다: " + url);
    }
  }

  @Test
  @DisplayName("로그인 URL 이 몰별로 고정돼 있다")
  void loginUrlsArePinned() {
    assertEquals("https://www.ssg.com/", MallRegistry.bySeq(1).orElseThrow().loginUrl());
    assertEquals("https://www.oasis.co.kr/login", MallRegistry.bySeq(2).orElseThrow().loginUrl());
    assertEquals(
        "https://www.nonghyupmall.com/BC41000R/loginViewPage.nh",
        MallRegistry.bySeq(3).orElseThrow().loginUrl());
  }

  @Test
  @DisplayName("실측된 몰만 인증 쿠키 이름을 선언한다 (Phase 5-15)")
  void onlyMeasuredMallsDeclareAuthCookieNames() {
    // 실측된 것은 ssg 뿐이다(ADR-0001 T3). 모르는 몰까지 인증 검사로 막으면 정상 로그인한 사람도
    // 계속 튕겨 그 몰의 캡처 자체가 불가능해진다 — 미인증 스냅샷 한 건보다 나쁜 역전이다.
    assertFalse(MallRegistry.SSG_GROUP.authCookieNames().isEmpty(), "실측된 ssg 에 이름이 없다.");
    assertTrue(MallRegistry.OASIS.authCookieNames().isEmpty(), "미실측 몰에 이름을 추측해 넣었다.");
    assertTrue(MallRegistry.HANARO.authCookieNames().isEmpty(), "미실측 몰에 이름을 추측해 넣었다.");
  }

  @Test
  @DisplayName("ssg 의 인증 쿠키 이름은 실측값으로 고정한다")
  void ssgAuthCookieNamesArePinned() {
    // 캡처 성공 판정이 통째로 이 목록에 달려 있다. 여기를 손대는 것은 판정 기준을 바꾸는 일이므로
    // 캡처된 쿠키 목록으로 다시 확인한 뒤에만 한다.
    assertEquals(
        List.of("LOGIN_YN", "MEMBER_ID", "MBR_ID_ED_NO"), MallRegistry.SSG_GROUP.authCookieNames());
  }

  @Test
  @DisplayName("로그인 전에도 발급되는 쿠키는 인증 판정에 쓰지 않는다")
  void authCookieNamesExcludeCookiesIssuedBeforeLogin() {
    // JSESSIONID(서블릿 세션)·FSID(추적 식별자)는 첫 화면만 열어도 생긴다. 판정에 넣으면
    // 로그인하지 않아도 통과해 이 검사가 있으나 마나가 된다 — 그것이 막으려던 상황 자체다.
    for (MallRegistry mall : MallRegistry.values()) {
      for (String name : mall.authCookieNames()) {
        assertFalse(name.startsWith("JSESSIONID"), mall + " 가 로그인 전에도 생기는 쿠키를 판정에 쓴다: " + name);
        assertFalse(name.startsWith("FSID"), mall + " 가 로그인 전에도 생기는 쿠키를 판정에 쓴다: " + name);
      }
    }
  }

  @Test
  @DisplayName("인증 쿠키 목록에 빈 이름이 섞이지 않는다")
  void authCookieNamesHaveNoBlankEntries() {
    // 빈 이름은 어떤 스냅샷과도 매치되지 않는다. 목록이 '선언됐다' 로만 보이면서 실제로는
    // 그 몰의 캡처를 전부 막게 된다.
    for (MallRegistry mall : MallRegistry.values()) {
      List<String> names = mall.authCookieNames();
      assertNotNull(names, mall + " 의 인증 쿠키 목록이 null 이다.");
      for (String name : names) {
        assertNotNull(name, mall + " 의 인증 쿠키 이름이 null 이다.");
        assertFalse(name.isBlank(), mall + " 에 빈 인증 쿠키 이름이 있다.");
      }
    }
  }

  @Test
  @DisplayName("coupang 은 아직 등록하지 않는다")
  void coupangIsNotRegistered() {
    // 컴파일만 되는 껍데기다. 여기 넣으면 "지원되는 몰"로 보인다. Phase 4B 통과 후에 추가한다.
    boolean hasCoupang =
        Arrays.stream(MallRegistry.values()).anyMatch(m -> "coupang".equalsIgnoreCase(m.mallId()));

    assertFalse(hasCoupang);
  }
}

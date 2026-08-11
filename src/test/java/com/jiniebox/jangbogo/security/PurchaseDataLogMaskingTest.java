package com.jiniebox.jangbogo.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 로그에 <b>세션 ID 와 구매 데이터</b>가 평문으로 남지 않게 한다.
 *
 * <h2>실제로 새고 있었다 (2026-08-12 실계정 확인)</h2>
 *
 * <p>수집 한 회차를 지켜보다 발견했다. 로그가 이만큼을 그대로 남겼다.
 *
 * <ul>
 *   <li><b>살아 있는 세션 ID</b> — {@code AdminController} 로그인 성공(INFO), {@code AuthInterceptor} 인증
 *       성공(DEBUG). {@code SECURITY.md} 의 "세션 값은 로그 어디에도 싣지 않는다" 를 정면으로 깨고 있었다.
 *   <li><b>주문번호 전체</b> — {@code Oasis}·{@code MallOrderUpdaterRunner}·{@code
 *       JbgOrderDataAccessObject}·{@code ExportService}
 *   <li><b>구매일자와 실제 매장명</b> — {@code Emart} 의 복구 로그는 주석이 "값은 남기지 않는다" 라고 적어 놓고 매장명을 찍고 있었다
 *   <li><b>상품명</b> — {@code MallOrderUpdaterRunner} 의 아이템 저장 로그
 * </ul>
 *
 * <p>{@code AccountIdLogMaskingTest} 가 계정 아이디에 대해 같은 일을 하고 있었지만 <b>구매 데이터는 대상이 아니었다.</b> 그래서 "아이디는
 * 가리고 구매 이력은 그대로 남기는" 상태가 됐다 — 정작 이 프로그램이 다루는 데이터의 본체는 후자다.
 *
 * <h2>세션 ID 는 가리지 않고 지운다</h2>
 *
 * <p>살아 있는 자격증명이라 일부만 남겨도 좁혀 들어갈 수 있고, 진단에 세션 값이 필요한 경우가 애초에 없다. 그래서 {@code LogMask} 에 세션용 함수를 두지
 * 않았다 — <b>함수가 있으면 언젠가 쓰인다.</b>
 *
 * <h2>이 가드가 잡지 <b>못하는</b> 것</h2>
 *
 * <p>감시는 <b>이름 목록</b> 기반이라 목록에 없는 이름은 잡지 못한다. 특히 {@code mallName} 은 파일마다 뜻이 달라 전역으로 걸 수 없다 — 러너에서는
 * {@code jbg_mall.name}(앱이 정한 몰 이름, 개인정보 아님)이고 {@code Emart}·{@code ExportService} 에서는 <b>실제
 * 매장명</b>이다. 그래서 그쪽은 자리별로 못 박았다. <b>"통과했으니 로그에 구매 데이터가 없다" 로 읽지 마라.</b>
 *
 * <p>브라우저·네트워크·DB 를 쓰지 않는다. 소스 <b>형태</b>만 본다.
 *
 * @author KIUNSEA
 */
class PurchaseDataLogMaskingTest {

  /** 운영 소스 전체. {@code AccountIdLogMaskingTest} 가 svc 만 보다 컨트롤러를 놓친 것이 이번 사고의 절반이다. */
  private static final Path MAIN = Path.of("src/main/java");

  /** 로그 호출 한 건. {@code AccountIdLogMaskingTest} 와 같은 형태를 쓴다 — 두 벌 두면 한쪽만 좁아진다. */
  private static final Pattern LOG_CALL =
      Pattern.compile(
          "(?s)(?:logger|log|LOGGER|LOG)\\.(?:trace|debug|info|warn|error|fatal)\\(.*?\\);");

  /**
   * 로그 인자로 실리면 안 되는 구매 데이터 이름들.
   *
   * <p>{@code mallName} 은 <b>일부러 뺐다</b> — 파일마다 뜻이 달라 전역으로 걸면 정상인 자리가 빨개진다(클래스 javadoc 참조). 그쪽은 아래
   * {@code storeNameSitesAreMasked} 가 자리별로 못 박는다.
   */
  private static final Pattern RAW_PURCHASE_VALUE =
      Pattern.compile(
          "(?<![\\w.])(?:serialNum|serial|dateTime|datetime|orderMallName|itemName)(?![\\w])");

  /** 세션 ID 를 꺼내는 형태. 가리는 것이 아니라 <b>아예 실리면 안 된다.</b> */
  private static final Pattern SESSION_ID =
      Pattern.compile("(?<![\\w.])(?:session|s)\\.getId\\(\\)");

  /**
   * 스캔 전 정규화. 주석과 문자열 리터럴을 지우고, <b>이미 가려진 호출</b>을 통째로 치환한다.
   *
   * <p>줄 주석을 블록 주석보다 먼저 지운다 — 주석으로 묶어 둔 코드 안에 javadoc 여는 표시가 있으면 블록부터 지울 때 경계가 엉뚱하게 잡힌다.
   */
  static String normalize(String source) {
    return source
        .replaceAll("(?m)^\\s*//.*$", "")
        .replaceAll("(?s)/\\*.*?\\*/", "")
        .replaceAll("LogMask\\.(?:shape|name)\\([^()]*\\)", "MASKED")
        .replaceAll("AccountIdMasker\\.mask\\([^()]*\\)", "MASKED")
        .replaceAll("\"(?:\\\\.|[^\"\\\\])*\"", "\"\"");
  }

  /** 로그 호출 안에 그대로 실린 값 이름을 모은다. 프로덕션 스캔과 대조군이 <b>같은 함수</b>를 통과한다. */
  static List<String> rawValuesInLogCalls(String source, Pattern forbidden) {
    List<String> hits = new ArrayList<>();
    Matcher call = LOG_CALL.matcher(normalize(source));
    while (call.find()) {
      Matcher raw = forbidden.matcher(call.group());
      while (raw.find()) {
        hits.add(raw.group());
      }
    }
    return hits;
  }

  /** 운영 소스 아래의 <b>모든 파일</b>. 확장자도 깊이도 제한하지 않는다 — 목록을 만드는 순간 사각지대가 생긴다. */
  private static List<Path> mainSources() throws Exception {
    try (Stream<Path> files = Files.walk(MAIN)) {
      return files.filter(Files::isRegularFile).toList();
    }
  }

  private static String reportName(Path path) {
    return MAIN.relativize(path).toString().replace('\\', '/');
  }

  // ── 프로덕션 검사 ─────────────────────────────────────────────────────

  @Test
  @DisplayName("어떤 로그도 세션 ID 를 싣지 않는다")
  void noLogCallCarriesASessionId() throws Exception {
    List<String> offenders = new ArrayList<>();
    for (Path path : mainSources()) {
      for (String hit :
          rawValuesInLogCalls(Files.readString(path, StandardCharsets.UTF_8), SESSION_ID)) {
        offenders.add(reportName(path) + " → " + hit);
      }
    }

    // 마스킹으로 해결하지 마라. 세션 ID 는 일부만 남겨도 좁혀 들어갈 수 있고, 진단에 필요하지도 않다.
    assertTrue(offenders.isEmpty(), "살아 있는 세션 ID 가 로그 인자로 실린다(가리지 말고 빼라): " + offenders);
  }

  @Test
  @DisplayName("어떤 로그도 주문번호·구매일자·상품명을 그대로 싣지 않는다")
  void noLogCallCarriesRawPurchaseValues() throws Exception {
    List<String> offenders = new ArrayList<>();
    for (Path path : mainSources()) {
      for (String hit :
          rawValuesInLogCalls(Files.readString(path, StandardCharsets.UTF_8), RAW_PURCHASE_VALUE)) {
        offenders.add(reportName(path) + " → " + hit);
      }
    }

    // 면제 목록을 만들지 마라. 예외가 하나라도 있으면 통과 건수는 그 파일에 대해 아무 것도 보증하지
    // 않는다. 새 offender 가 나오면 등록할 게 아니라 LogMask 로 감싸라.
    assertTrue(offenders.isEmpty(), "구매 데이터가 로그 인자로 그대로 실린다(LogMask 로 감쌀 것): " + offenders);
  }

  @Test
  @DisplayName("실제 매장명을 찍는 자리는 가려서 찍는다")
  void storeNameSitesAreMasked() throws Exception {
    // mallName 은 파일마다 뜻이 달라 전역 감시를 걸 수 없다. 실제 매장명이 실리는 자리만 못 박는다.
    // Emart 의 복구 로그는 주석이 "값은 남기지 않는다" 라고 적어 놓고 매장명을 찍고 있었다.
    for (String relative :
        List.of(
            "com/jiniebox/jangbogo/svc/mall/Emart.java",
            "com/jiniebox/jangbogo/svc/ExportService.java",
            "com/jiniebox/jangbogo/dao/JbgOrderDataAccessObject.java")) {
      String source = Files.readString(MAIN.resolve(relative), StandardCharsets.UTF_8);

      assertTrue(source.contains("class "), relative + " 를 읽지 못했다 — 아래 단언이 항상 통과한다.");
      assertTrue(
          source.contains("LogMask.name(mallName)"), relative + " 가 실제 매장명을 가리지 않고 로그에 싣는다.");
    }
  }

  // ── 대조군 — 판별식이 살아 있는가 ────────────────────────────────────────

  @Test
  @DisplayName("대조군 — 가려지지 않은 값은 잡고, 가려진 값은 통과시킨다")
  void theDetectionRuleItselfStillWorks() {
    // 위 검사들은 저장소가 깨끗한 동안 목록을 비워도 초록이다. 그래서 판별식에 직접
    // "걸려야 하는 형태" 와 "걸리면 안 되는 형태" 를 넣는다.
    assertEquals(
        List.of("serial"),
        rawValuesInLogCalls("log.debug(\"주문: {}\", serial);", RAW_PURCHASE_VALUE),
        "가려지지 않은 주문번호를 놓쳤다.");
    assertEquals(
        List.of(),
        rawValuesInLogCalls("log.debug(\"주문: {}\", LogMask.shape(serial));", RAW_PURCHASE_VALUE),
        "가린 값을 위반으로 셌다 — 정상 코드가 빨개진다.");
    assertEquals(
        List.of("session.getId()"),
        rawValuesInLogCalls("log.info(\"s: {}\", session.getId());", SESSION_ID),
        "세션 ID 를 놓쳤다.");
  }

  @Test
  @DisplayName("대조군 — 주석과 문자열 안의 이름은 위반이 아니다")
  void namesInsideCommentsAndStringsAreNotViolations() {
    // 이 파일 자신의 javadoc 이 serial·datetime 을 잔뜩 적고 있다. 주석을 세면
    // 설명이 위반으로 잡혀 가드가 스스로를 막는다.
    assertEquals(
        List.of(),
        rawValuesInLogCalls("// log.debug(\"{}\", serial);", RAW_PURCHASE_VALUE),
        "주석 안의 이름을 위반으로 셌다.");
    assertEquals(
        List.of(),
        rawValuesInLogCalls("log.debug(\"serial 자릿수={}\", n);", RAW_PURCHASE_VALUE),
        "문자열 리터럴 안의 이름을 위반으로 셌다.");
  }

  @Test
  @DisplayName("대조군 — 스캔이 실제로 파일을 훑는다")
  void theScanActuallyReadsFiles() throws Exception {
    List<Path> scanned = mainSources();

    assertFalse(scanned.isEmpty(), "운영 소스를 한 개도 찾지 못했다 — 위 검사가 전부 무의미하다.");
    assertTrue(scanned.size() > 50, "훑은 파일이 너무 적다(" + scanned.size() + "개) — 경로가 어긋났을 수 있다.");
  }
}

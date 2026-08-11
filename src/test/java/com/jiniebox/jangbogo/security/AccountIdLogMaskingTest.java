package com.jiniebox.jangbogo.security;

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
 * 계정 아이디가 로그 인자로 되돌아오는 것을 막는다.
 *
 * <p><b>실제로 새고 있었다.</b> {@code MallOrderUpdater.collectItems} 의 첫 줄이 쇼핑몰 로그인 아이디를 INFO 로 그대로 찍었다.
 * 세션 쿠키는 개수만, 암호화 키는 길이만 남기던 저장소에서 계정 아이디만 예외였다. {@code SECURITY.md} 는 기여자에게 "버그 리포트에 로그를 붙일 때 계정
 * 아이디를 지우라" 고 안내하는데, 그 안내는 사람이 매번 손으로 지운다는 가정 위에 서 있다 — 그런 가정은 반드시 한 번은 깨진다.
 *
 * <p>고치는 것만으로는 재발을 막지 못한다. 로그 한 줄을 추가하는 일은 너무 사소해서 아무도 보안 검토를 하지 않고, 그때 손에 잡히는 변수가 마침 {@code
 * mallId} 다. 그래서 <b>형태</b>를 못 박는다.
 *
 * <h2>무엇을 보는가</h2>
 *
 * <p>소스를 정규화한 뒤 <b>로그 호출 안</b>에 계정 값 이름이 그대로 들어 있는지 본다. 정규화가 하는 일이 판정의 핵심이다.
 *
 * <ul>
 *   <li><b>문자열 리터럴을 지운다.</b> 위반은 이름을 <i>말하는</i> 것이 아니라 값을 <i>싣는</i> 것이다. 리터럴을 남기면 {@code "…mallId:
 *       {}"} 처럼 문구에만 나오는 자리가 걸려, 사람이 정규식을 고치는 대신 스캔을 꺼 버린다.
 *   <li><b>마스킹을 거친 자리를 지운다.</b> {@code AccountIdMasker.mask(mallId)} 는 위반이 아니다. 이 치환이 없으면 <b>고친 코드가
 *       스스로 걸린다.</b>
 *   <li><b>주석을 지운다.</b> "이렇게 쓰면 안 된다" 를 주석에 적을수록 가드가 빨개지면, 사람은 설명을 지운다.
 * </ul>
 *
 * <h2>이 스캔이 못 잡는 것 — 알고 남긴다</h2>
 *
 * <p>감시 대상은 <b>이름 목록</b>({@link #RAW_ACCOUNT_VALUE})이고, 목록에 없는 이름은 잡지 못한다. <b>"통과했으니 로그에 계정이 없다" 로
 * 읽지 마라</b> — 그 오해가 이 가드를 가드가 없는 것보다 나쁘게 만든다.
 *
 * <h2>스캔 경로를 운영 소스 전체로 넓혔다 (2026-08-12)</h2>
 *
 * <p>이 가드는 원래 {@code svc} 아래만 봤고, 그 한계를 여기에 "고쳐지는 대로 넓혀야 한다" 고 적어 두었다. <b>그 사이에 실제로 새고 있었다</b> —
 * 2026-08-12 실계정 수집을 지켜보다 {@code ctrl/AdminController} 가 관리자 아이디와 <b>살아 있는 세션 ID</b> 를 한 줄에 INFO 로
 * 찍는 것을 봤다. 목록에 적어 둔 자리가 목록에 적혀 있다는 이유로 계속 새고 있었던 것이다.
 *
 * <p>그래서 적어 두었던 자리를 전부 고치고({@code AdminCredentialService}·{@code AdminController}·{@code
 * FtpUploadUtil}·{@code AuthInterceptor}) 스캔 경로를 {@code src/main/java} 전체로 넓혔다. <b>목록은 비웠다</b> — 남겨
 * 두면 다음 사람이 "여기는 원래 예외" 로 읽는다.
 *
 * <p>세션 ID 와 구매 데이터(주문번호·구매일자·매장명·상품명)는 {@code PurchaseDataLogMaskingTest} 가 따로 본다. 가려야 할 이유와 가리는
 * 방식이 달라 판정을 한 파일에 섞지 않았다.
 *
 * <p>브라우저·네트워크·DB 를 쓰지 않는다. 소스 <b>형태</b>만 본다.
 *
 * @author KIUNSEA
 */
class AccountIdLogMaskingTest {

  /**
   * 운영 소스 전체.
   *
   * <p>원래 {@code svc} 아래만 봤다. 그 바깥에서 실제로 샜다(클래스 javadoc 참조) — 경로를 좁히는 것이 곧 사각지대를 만드는 것이었다.
   */
  private static final Path SCAN_ROOT = Path.of("src/main/java");

  /** 수집 파이프라인. 자격증명이 실제로 흐르는 구간이라 개별 검사가 여기를 따로 못 박는다. */
  private static final Path SVC_DIR = SCAN_ROOT.resolve("com/jiniebox/jangbogo/svc");

  /** 실제로 새던 자리. */
  private static final Path UPDATER = SVC_DIR.resolve("MallOrderUpdater.java");

  /**
   * 로그 호출 한 건. {@code logger.info(} 부터 처음 만나는 {@code );} 까지.
   *
   * <p>{@code log} 와 {@code logger} 를 모두 본다 — 이 저장소는 두 이름을 다 쓴다({@code WebDriverManager} 는 {@code
   * log}). 하나만 보면 다른 이름을 쓰는 파일이 통째로 사각지대가 된다.
   *
   * <p>{@code .*?} 가 짧게 무는 것이 안전한 방향이다. 문자열 리터럴을 먼저 지우므로 메시지 안에 있던 {@code );} 는 이미 사라졌고, 남은 첫
   * {@code );} 는 그 호출의 끝이다.
   */
  private static final Pattern LOG_CALL =
      Pattern.compile(
          "(?s)(?:logger|log|LOGGER|LOG)\\.(?:trace|debug|info|warn|error|fatal)\\(.*?\\);");

  /**
   * 로그 인자로 실리면 안 되는 이름들.
   *
   * <p>아이디만이 아니라 비밀번호 이름도 함께 본다. 비밀번호가 로그에 실리는 사고가 더 크고, 두 값은 같은 자리에서 같은 손으로 다뤄진다 — 한쪽만 감시하면 나머지는
   * 다음 사람이 자연스럽게 놓는다.
   *
   * <p>{@code (?<![\w.])} 로 앞이 점이거나 식별자면 뺀다. {@code MallRegistry.mallId()} 는 몰의 식별자(ssg·oasis)이지
   * 사람의 로그인 아이디가 아니라서 가려야 할 값이 아니다 — 그 둘이 이름을 공유하는 것 자체가 이번 사고를 오래 살려 둔 이유이기도 하다.
   */
  private static final Pattern RAW_ACCOUNT_VALUE =
      Pattern.compile(
          "(?<![\\w.])(?:mallId|mallPw|usrid|usrpw|usrpass|USER_ID|USER_PASS"
              // 2026-08-12 추가. 이 이름들이 목록에 없어서, 경로를 넓힌 뒤에도 세 자리가
              // 초록인 채로 새고 있었다 — 관리자 아이디 2곳과 FTP 사용자명 1곳.
              // 목록 기반 감시의 한계가 그대로 드러난 자리라 여기 적어 둔다.
              + "|adminId|currentAdminId|ftpUser|ftpId)(?![\\w])");

  /**
   * 스캔에 들어가기 전 소스를 정규화한다. 클래스 javadoc 의 세 항목을 그대로 수행한다.
   *
   * <p>줄 주석을 블록 주석보다 먼저 지운다. 주석으로 통째로 묶어 둔 코드 안에 javadoc 여는 표시가 들어 있으면 블록부터 지울 때 경계가 엉뚱하게 잡힌다
   * ({@code SecurityHardeningTest} 가 같은 이유로 같은 순서를 쓴다).
   */
  static String normalize(String source) {
    return source
        .replaceAll("(?m)^\\s*//.*$", "")
        .replaceAll("(?s)/\\*.*?\\*/", "")
        .replaceAll("AccountIdMasker\\.mask\\([^()]*\\)", "MASKED")
        .replaceAll("\"(?:\\\\.|[^\"\\\\])*\"", "\"\"");
  }

  /**
   * 로그 호출 안에 그대로 실린 계정 값 이름을 모은다.
   *
   * <p>프로덕션 스캔과 대조군이 <b>같은 함수</b>를 통과한다. 나누면 대조군이 초록인 채로 프로덕션 스캔만 죽는 상태가 만들어지는데, 그것이 이 저장소가 두 번 겪은
   * '가드가 죽었는데 초록' 의 형태다.
   */
  static List<String> rawAccountValuesInLogCalls(String source) {
    List<String> hits = new ArrayList<>();
    Matcher call = LOG_CALL.matcher(normalize(source));
    while (call.find()) {
      Matcher raw = RAW_ACCOUNT_VALUE.matcher(call.group());
      while (raw.find()) {
        hits.add(raw.group());
      }
    }
    return hits;
  }

  /**
   * svc 아래의 <b>모든 파일</b>. 확장자도 깊이도 제한하지 않는다.
   *
   * <p>{@code SecurityHardeningTest} 가 배운 것을 그대로 쓴다 — 확장자를 걸렀더니 {@code .java.bak} 하나가 전수 스캔을 통째로
   * 빠져나갔고, 그 파일에는 실제 위반이 여섯 곳 있었다. 목록을 만드는 순간 목록에 없는 것이 사각지대가 된다.
   */
  private static List<Path> svcSources() throws Exception {
    try (Stream<Path> files = Files.walk(SCAN_ROOT)) {
      return files.filter(Files::isRegularFile).toList();
    }
  }

  private static String reportName(Path path) {
    return SCAN_ROOT.relativize(path).toString().replace('\\', '/');
  }

  // ---------------------------------------------------------------
  // 프로덕션 검사
  // ---------------------------------------------------------------

  @Test
  @DisplayName("수집 시작 로그는 계정 아이디를 마스킹해서 남긴다")
  void collectStartLogMasksTheAccountId() throws Exception {
    String source = Files.readString(UPDATER, StandardCharsets.UTF_8);

    // 먼저 '정말 읽었는가' 를 묻는다. 아래 단언들은 "없다" 와 "아무것도 못 읽었다" 를 구분하지 못한다.
    assertTrue(
        source.contains("class MallOrderUpdater"), "MallOrderUpdater 를 읽지 못했다 — 아래 단언이 항상 통과한다.");

    assertTrue(
        source.contains("import com.jiniebox.jangbogo.util.AccountIdMasker;"),
        "마스킹 유틸을 import 하지 않는다 — 배선이 끊겼다.");
    assertTrue(
        source.contains("AccountIdMasker.mask(mallId)"),
        "수집 시작 로그가 계정 아이디를 마스킹하지 않는다. 이 줄이 실제로 실계정 아이디를 로그 파일에 남기던 자리다.");
  }

  @Test
  @DisplayName("운영 소스 어디에서도 계정 아이디·비밀번호를 로그에 그대로 싣지 않는다")
  void noLogCallInServiceLayerCarriesRawAccountValues() throws Exception {
    List<String> offenders = new ArrayList<>();
    for (Path path : svcSources()) {
      for (String hit :
          rawAccountValuesInLogCalls(Files.readString(path, StandardCharsets.UTF_8))) {
        offenders.add(reportName(path) + " → " + hit);
      }
    }

    // 면제 목록을 만들지 마라. 예외가 하나라도 있으면 통과 건수는 그 파일에 대해 아무 것도 보증하지
    // 않는다. 새 offender 가 나오면 등록할 게 아니라 AccountIdMasker.mask 로 감싸라.
    assertTrue(
        offenders.isEmpty(), "계정 값이 로그 인자로 그대로 실린다(AccountIdMasker.mask 로 감쌀 것): " + offenders);
  }

  // ---------------------------------------------------------------
  // 대조군 — 판별식 자체가 살아 있는가
  //
  // 위 스캔은 "offender 가 0건이다" 형태다. 정규식이 아무것도 맞지 않게 되거나 normalize 가 빈
  // 문자열을, svcSources 가 빈 목록을 돌려주면 <b>그대로 초록</b>이 된다. 저장소가 지금 깨끗하다는
  // 사실은 판별식이 살아 있다는 근거가 못 된다 — 죽은 판별식도 같은 결론에 도달한다.
  //
  // 이 프로젝트가 실제로 두 번 겪은 형태다. 세션 만료 감지는 단위 테스트 25건이 초록인 채로
  // 프로덕션 호출자가 0건이었고, 배포 산출물 가드는 판별식을 무조건 false 로 바꿔도 5건이 전부
  // 통과했다. 그래서 아래는 저장소 상태를 보지 않고 판별식에 입력을 직접 넣는다.
  // ---------------------------------------------------------------

  @Test
  @DisplayName("대조군: 판별식이 실제 유출 형태를 잡는다")
  void theScanStillCatchesRawAccountValuesInLogs() {
    // 첫 줄이 실제로 로그 파일에 실계정 아이디를 남기던 그 형태다(라벨까지 그대로).
    for (String leaking :
        new String[] {
          "class X { void f() { logger.info(\"구매내역 수집 시작 - seqMall: {}, mallId: {}\","
              + " seqMall, mallId); } }",
          "class X { void f() { logger.error(\"로그인 실패 - {}\", usrid); } }",
          "class X { void f() { log.warn(\"세션 없음: {}\", USER_ID); } }",
          "class X { void f() { logger.debug(\n    \"계정 {} / {}\",\n    mallId,\n    mallPw); } }"
        }) {
      assertFalse(
          rawAccountValuesInLogCalls(leaking).isEmpty(),
          "유출 형태인데 판별식이 잡지 못했다. 이 상태면 svc 전수 스캔은 무엇이 있어도 초록이다: " + leaking);
    }
  }

  @Test
  @DisplayName("대조군: 올바른 형태와 로그가 아닌 사용은 걸리지 않는다")
  void theScanDoesNotFlagMaskedOrNonLogUsages() {
    // 오탐이 나기 시작하면 사람은 정규식을 고치는 대신 예외 목록을 만들거나 스캔을 꺼 버린다 —
    // 가드가 죽는 가장 흔한 경로다. 그래서 반대 방향도 함께 못 박는다.
    for (String clean :
        new String[] {
          // 고친 뒤의 형태. 이 치환이 없으면 고친 코드가 스스로 걸린다.
          "class X { void f() { logger.info(\"수집 시작 - 계정: {}\","
              + " AccountIdMasker.mask(mallId)); } }",
          // 로그가 아닌 사용. 값을 넘기는 것 자체는 막을 이유가 없다.
          "class X { void f() { if (!hasCredentials(mallId)) { return; } } }",
          "class X { void f() { spec.create(mallId, mallPw).getItems(); } }",
          // 이름이 메시지 문구에만 나오는 자리. 값은 싣지 않는다.
          "class X { void f() { logger.warn(\"mallId 는 로그에 싣지 않는다\"); } }",
          // 몰의 식별자(ssg·oasis)는 사람의 로그인 아이디가 아니다.
          "class X { void f() { logger.info(\"프로필: {}\", mall.mallId()); } }"
        }) {
      assertTrue(
          rawAccountValuesInLogCalls(clean).isEmpty(),
          "올바른 형태인데 위반으로 걸렸다(오탐): " + clean + " → " + rawAccountValuesInLogCalls(clean));
    }
  }

  @Test
  @DisplayName("대조군: 정규화가 주석·리터럴만 지우고 실행되는 로그 호출은 남긴다")
  void theNormalizerKeepsExecutableLogCalls() {
    // normalize 가 빈 문자열을 돌려주게 바뀌면 위 스캔은 전부 통과한다. 반대로 주석을 안 걷어내면
    // "이렇게 쓰면 안 된다" 를 적은 javadoc 이 스스로 걸려, 설명을 적을수록 가드가 빨개진다.
    // 그때 사람은 설명을 지우거나 가드를 끈다. 양쪽을 함께 못 박는다.
    String sample =
        "/** javadoc 예시: logger.info(\"…{}\", mallId) 처럼 쓰면 안 된다. */\n"
            + "class Sample {\n"
            + "  // 죽은 코드: logger.warn(\"…{}\", usrid);\n"
            + "  void f() { logger.info(\"계정 {}\", AccountIdMasker.mask(mallId)); }\n"
            + "}\n";

    assertTrue(
        rawAccountValuesInLogCalls(sample).isEmpty(),
        "주석에 적어 둔 예시가 실행되는 코드로 세어졌다 — 설명을 적을수록 가드가 빨개진다.");
    assertTrue(
        normalize(sample).contains("logger.info("),
        "정규화가 실행되는 로그 호출까지 지웠다. 이 상태면 svc 스캔은 빈 문자열을 훑으므로 무엇이 있어도 초록이다.");
  }

  @Test
  @DisplayName("대조군: 전수 스캔이 실제로 파일을 훑고 로그 호출을 찾아낸다")
  void theServiceScanActuallyVisitsFilesAndFindsLogCalls() throws Exception {
    // svcSources() 가 빈 목록을, LOG_CALL 이 0건을 돌려주면 전수 스캔은 offender 0건으로 초록이
    // 된다. "위반이 없다" 와 "아무것도 보지 않았다" 는 같은 모양이라 구분되지 않는다.
    List<String> scanned = svcSources().stream().map(AccountIdLogMaskingTest::reportName).toList();

    assertFalse(scanned.isEmpty(), "전수 스캔이 파일을 하나도 찾지 못했다 — 작업 디렉터리가 프로젝트 루트가 아니거나 경로가 바뀌었다.");
    // svc 안팎을 모두 넣는다. 경로를 넓힌 것이 실제로 반영됐는지는 svc 바깥 파일이 잡히는지로만
    // 확인된다 — svc 것만 확인하면 예전 범위로 되돌아가도 이 대조군은 초록이다.
    for (String required :
        new String[] {
          "com/jiniebox/jangbogo/svc/MallOrderUpdater.java",
          "com/jiniebox/jangbogo/svc/MallSchedulerService.java",
          "com/jiniebox/jangbogo/svc/mall/SsgSessionCollector.java",
          "com/jiniebox/jangbogo/ctrl/AdminController.java",
          "com/jiniebox/jangbogo/sys/AuthInterceptor.java",
          "com/jiniebox/jangbogo/util/FtpUploadUtil.java"
        }) {
      assertTrue(scanned.contains(required), "전수 스캔이 " + required + " 를 건너뛴다: " + scanned);
    }

    int calls = 0;
    Matcher matcher =
        LOG_CALL.matcher(normalize(Files.readString(UPDATER, StandardCharsets.UTF_8)));
    while (matcher.find()) {
      calls++;
    }
    assertTrue(
        calls >= 5, "MallOrderUpdater 에서 로그 호출을 " + calls + " 건밖에 찾지 못했다 — 판별식이 로그를 못 보고 있다.");
  }
}

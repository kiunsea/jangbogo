package com.jiniebox.jangbogo.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jiniebox.jangbogo.dao.LocalDBConnection;
import com.jiniebox.jangbogo.dev.DevTestController;
import com.jiniebox.jangbogo.dev.HelloController;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

/**
 * 보안 하드닝 회귀 감시 (B-2 · B-3 · B-4).
 *
 * <p>계획서가 "별도 과제"로 분류해 둔 항목들이다. 이 저장소는 PUBLIC 이라 <b>소스가 이미 공개돼 있고</b>, 그래서 각 항목이 이론적 위험이 아니다.
 *
 * <p>키 관련(B-1)은 접근자를 public 으로 열지 않으려고 {@code util} 패키지의 별도 테스트에 둔다.
 *
 * <p>브라우저·네트워크·DB 를 쓰지 않는다.
 *
 * @author KIUNSEA
 */
class SecurityHardeningTest {

  /** DAO 소스 디렉터리. 이 테스트는 실행이 아니라 <b>소스 형태</b>를 본다 — DB 를 켜지 않고도 재발을 잡기 위해서다. */
  private static final Path DAO_DIR = Path.of("src/main/java/com/jiniebox/jangbogo/dao");

  /**
   * SQL 에 값을 이어 붙인 흔적.
   *
   * <p><b>예전 가드는 {@code "'" + } 한 형태만 봤다.</b> 그래서 따옴표가 더 긴 리터럴 <i>끝</i>에 붙은 {@code "col='" + val}
   * 형태를 통째로 놓쳤다. 테스트 434건이 전부 통과하는 동안 {@code JbgOrderDataAccessObject.getOrder} 는 수집한 영수증 번호를 그대로
   * WHERE 절에 이어 붙이고 있었고, 그 값은 페이지 텍스트에서 합성한 것이라 숫자 필터조차 거치지 않는다. 가드가 못 보는 형태가 하나라도 있으면 통과 건수는 그 지점에
   * 대해 아무 것도 보증하지 않는다.
   *
   * <p>그래서 특정 표현이 아니라 <b>리터럴 경계에 붙은 작은따옴표</b>를 본다. 여는 쪽({@code ='"} 뒤에 {@code +})과 닫는 쪽({@code +}
   * 뒤에 {@code "'})을 모두 잡으므로 두 형태가 한 번에 걸리고, {@code \s*} 라 줄바꿈을 사이에 두고 이어 붙인 것도 걸린다.
   *
   * <p><b>아직 못 보는 형태가 있다 — 따옴표 없이 붙는 숫자 자리다.</b> {@code " WHERE seq=" + seq} 처럼 값을 따옴표로 감싸지 않는 조립은
   * 이 정규식에 걸리지 않는다. 그런 자리가 {@code JbgMallDataAccessObject}·{@code JbgItemDataAccessObject} 에 여럿 남아
   * 있고, 일부는 {@code replaceAll("[^0-9]", "")} 로 숫자만 남기지만 <b>전부는 아니다</b> — {@code getMall}/{@code
   * checkAccountStatus} 는 {@code @RequestParam("seq")} 로 들어온 문자열을 그대로 붙인다.
   *
   * <p>그러니 아래 스캔이 초록이라는 것을 "dao 에 주입 지점이 없다" 로 읽지 마라. 보증 범위는 <b>따옴표로 감싼 값</b>까지다. 숫자 자리까지 막으려면 그
   * 호출부들을 바인딩으로 옮기는 별도 작업이 필요하고, 그때 이 정규식도 함께 넓혀야 한다.
   */
  private static final Pattern SQL_QUOTE_CONCAT = Pattern.compile("'\"\\s*\\+|\\+\\s*\"'");

  // 예외 목록(CONCAT_DEBT)은 없앴다. 통합 단계에서 남아 있던 두 건을 실제로 처리했기 때문이다.
  //
  //  - CommonDataAccessObject.getNextSeq — 호출부가 없고 SQLite 에 없는 information_schema 를
  //    읽던 죽은 경로여서 삭제했다.
  //  - JbgCollectBreakerDataAccessObject.getState — escape() 조립을 바인딩으로 바꿨다.
  //
  // 목록이 있는 한 아래 스캔은 "이 파일들만 빼고" 라는 조건부 보증이었다. 이제 무조건이다.
  // 다시 목록을 만들어 예외를 추가하지 마라 — 예외가 하나라도 있으면 통과 건수는 그 지점에 대해
  // 아무 것도 보증하지 않는다는 것이 이 가드가 처음 놓쳤던 실패의 형태다.

  /** 줄 전체 주석을 걷어낸 소스. 죽은 코드를 주석으로 남겨 둔 자리가 있어 그대로 두면 오탐이 된다. */
  private static String codeOf(Path path) throws Exception {
    return Files.readString(path, StandardCharsets.UTF_8).replaceAll("(?m)^\\s*//.*$", "");
  }

  // ---------------------------------------------------------------
  // B-2 — 개발용 엔드포인트가 운영 JAR 에 등록되지 않는다
  // ---------------------------------------------------------------

  @Test
  @DisplayName("dev 컨트롤러는 dev 프로파일에서만 등록된다")
  void devControllersAreProfileGated() {
    // /dev/reset-database 는 주문·아이템을 통째로 지운다. 인증이 앞을 막고 있었지만
    // 파괴적 기능이 운영 산출물에 실려 있는 것 자체가 문제였다.
    for (Class<?> type : new Class<?>[] {DevTestController.class, HelloController.class}) {
      Profile profile = type.getAnnotation(Profile.class);
      assertNotNull(profile, type.getSimpleName() + " 에 @Profile 이 없다 — 운영 JAR 에 등록된다.");
      assertTrue(
          java.util.Arrays.asList(profile.value()).contains("dev"),
          type.getSimpleName() + " 의 @Profile 이 dev 가 아니다: " + String.join(",", profile.value()));
    }
  }

  // ---------------------------------------------------------------
  // B-3 — 외부에서 온 값을 SQL 에 이어 붙이지 않는다
  // ---------------------------------------------------------------

  @Test
  @DisplayName("값을 다루는 DAO 는 SQL 에 값을 이어 붙이지 않는다")
  void valueHandlingDaosUseBoundParameters() throws Exception {
    // 이 넷이 다루는 값은 전부 우리가 통제하지 못한다 — 영수증 번호·매장명·상품명은 수집한 웹페이지에서,
    // ftp_pass 와 encrypt_key/iv 는 사용자 폼과 암호화 결과에서 온다. 뒤의 둘은 base64 라 '+' 와 '/'
    // 가 섞여 있고, 조립하다 깨지면 "저장은 됐는데 복호화만 안 되는" 형태로 조용히 나타난다.
    //
    // 실제로 이 프로젝트는 같은 INSERT 를 두 벌 갖고 있었고 한쪽만 고쳐져 있었다 —
    // 수집기가 쓰는 addWithConnection 은 진작 PreparedStatement 였고, add(...) 오버로드만
    // 문자열 조립으로 남아 있었다. 두 벌을 두면 반드시 갈라진다.
    for (String dao :
        new String[] {
          "JbgOrderDataAccessObject",
          "JbgItemDataAccessObject",
          "JbgExportConfigDataAccessObject",
          "JbgMallDataAccessObject"
        }) {
      String code = codeOf(DAO_DIR.resolve(dao + ".java"));

      assertFalse(
          SQL_QUOTE_CONCAT.matcher(code).find(),
          dao + " 에 SQL 문자열 조립(작은따옴표 + 값)이 남아 있다. 바인딩 파라미터를 쓸 것.");
      assertTrue(code.contains("txPstmtExecuteUpdate"), dao + " 가 PreparedStatement 를 쓰지 않는다.");
    }
  }

  @Test
  @DisplayName("dao 패키지 어디에도 SQL 문자열 조립이 새로 생기지 않는다")
  void noNewSqlConcatenationAppearsInDaoPackage() throws Exception {
    // 파일 목록을 손으로 적어 두면 새로 만든 DAO 가 감시 밖에 남는다. 실제로 이번에 드러난 지점도
    // "그 DAO 는 목록에 없었다" 는 이유로 오래 살아남았다. 그래서 디렉터리를 통째로 훑는다.
    List<String> offenders = new ArrayList<>();
    List<Path> sources;
    try (Stream<Path> files = Files.list(DAO_DIR)) {
      sources = files.filter(p -> p.getFileName().toString().endsWith(".java")).toList();
    }

    for (Path path : sources) {
      // 예외 없이 전부 본다. 면제 목록을 두면 그 파일 안에서 새 조립이 생겨도 못 잡는다.
      if (SQL_QUOTE_CONCAT.matcher(codeOf(path)).find()) {
        offenders.add(path.getFileName().toString());
      }
    }

    assertTrue(offenders.isEmpty(), "SQL 문자열 조립이 새로 생겼다(바인딩으로 쓸 것): " + offenders);
  }

  @Test
  @DisplayName("브레이커 상태 조회는 수집기 이름을 바인딩한다")
  void breakerStateLookupBindsCollectorName() throws Exception {
    // 예전에는 수집기 이름을 escape() 로 감싸 WHERE 절에 붙였다. 값이 내부 상수라 당장은 안전하다는
    // 논증이었는데, 그 논증은 호출부가 하나 늘어나는 순간 조용히 무효가 된다. 형태를 못 박아 둔다.
    String code = codeOf(DAO_DIR.resolve("JbgCollectBreakerDataAccessObject.java"));

    assertTrue(code.contains("collector = ?"), "getState 가 수집기 이름을 바인딩하지 않는다.");
    assertFalse(code.contains("escape("), "이스케이프 헬퍼가 되살아났다 — 바인딩의 대체가 아니다.");
  }

  @Test
  @DisplayName("영수증 번호 중복 조회는 바인딩 파라미터로 한다")
  void receiptLookupBindsSerialNumber() throws Exception {
    // getOrder 는 죽은 코드가 아니다 — MallOrderUpdaterRunner 와 svc.mall.Hanaro 가 중복 방지
    // 판정으로 매 회차 부른다. 조회가 깨지면 "이미 있는 주문"을 못 찾아 같은 주문이 다시 쌓인다.
    // 형태를 못 박아 둔다: 조립으로 되돌아가면 여기서 걸린다.
    String code = codeOf(DAO_DIR.resolve("JbgOrderDataAccessObject.java"));

    assertTrue(code.contains("serial_num=?"), "getOrder 가 serial_num 을 바인딩하지 않는다.");
    assertFalse(code.contains("serial_num='"), "getOrder 가 영수증 번호를 WHERE 절에 이어 붙인다.");
  }

  @Test
  @DisplayName("조회에도 바인딩 경로가 있다 — 없으면 DAO 는 이어 붙일 수밖에 없다")
  void connectionOffersBoundSelectPath() {
    // 조립이 남아 있던 진짜 이유가 이것이었다. 쓰기(txPstmtExecuteUpdate)만 만들어 두고 읽기에는
    // 바인딩 수단을 주지 않으면, 조회를 쓰는 DAO 에게는 이어 붙이는 것 말고 선택지가 없다.
    assertDoesNotThrow(
        () -> {
          LocalDBConnection.class.getMethod("executeQuery", String.class, Object[].class);
        },
        "조회용 PreparedStatement 경로가 없다. 쓰기만 막으면 읽기가 그대로 주입 지점이 된다.");
    assertDoesNotThrow(
        () -> {
          LocalDBConnection.class.getMethod("executeQuery", String.class);
        },
        "기존 executeQuery(String) 시그니처가 사라졌다 — 호출자가 많아 깨면 안 된다.");
  }

  // ---------------------------------------------------------------
  // B-4 — 인증 화이트리스트에 actuator 를 남겨 두지 않는다
  // ---------------------------------------------------------------

  @Test
  @DisplayName("actuator 는 인증 예외 목록에 없다")
  void actuatorIsNotWhitelisted() throws Exception {
    // 지금은 의존성이 없어 404 지만, 나중에 넣으면 health·env·beans 가 무인증으로 열린 채
    // 시작하게 된다. 쓰지도 않는 예외를 미리 깔아 두지 않는다.
    for (String file : new String[] {"sys/AuthInterceptor.java", "sys/WebMvcConfig.java"}) {
      String source =
          Files.readString(
              Path.of("src/main/java/com/jiniebox/jangbogo/" + file), StandardCharsets.UTF_8);
      String code = source.replaceAll("(?m)^\\s*//.*$", "");

      assertFalse(code.contains("\"/actuator"), file + " 에 actuator 인증 예외가 남아 있다.");
    }
  }
}

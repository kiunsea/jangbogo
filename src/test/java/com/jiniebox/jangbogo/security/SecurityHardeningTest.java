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
import java.util.regex.Matcher;
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
   * <p>따옴표 없이 붙는 숫자 자리는 <b>원리상</b> 이 정규식에 걸리지 않는다. 그 형태는 {@link #SQL_NUMERIC_CONCAT} 가 맡는다 — 둘을 함께
   * 봐야 "dao 에 조립이 없다" 가 성립한다.
   */
  private static final Pattern SQL_QUOTE_CONCAT = Pattern.compile("'\"\\s*\\+|\\+\\s*\"'");

  /**
   * 따옴표 없이 붙는 <b>숫자 자리</b> 조립.
   *
   * <p>{@link #SQL_QUOTE_CONCAT} 만 있던 시절의 사각지대다. 값을 따옴표로 감싸지 않는 자리는 리터럴이 {@code =} 로 끝나고 바로 값이 이어
   * 붙는다. 그 형태가 {@code JbgMallDataAccessObject}·{@code JbgItemDataAccessObject} 에 열 곳 넘게 남아 있었고,
   * <b>그 중 절반은 숫자 필터조차 거치지 않았다</b> — {@code getMall} 과 {@code checkAccountStatus} 는 {@code
   * AdminController} 의 {@code @RequestParam} 문자열을 필터 하나 없이 WHERE 절까지 흘려보냈다. 로그인 세션 뒤라는 사실은 완화지 방어가
   * 아니다.
   *
   * <p>그래서 <b>리터럴 끝의 {@code =}</b> 를 본다. {@code =} 와 닫는 큰따옴표 사이, 큰따옴표와 {@code +} 사이 모두 {@code \s*}
   * 라 공백을 넣거나 줄을 바꿔 붙인 것도 걸린다. 자리표시자를 쓰면 리터럴이 {@code =?} 로 끝나므로 걸리지 않는다.
   *
   * <p><b>오탐처럼 보이는 것에 예외 목록으로 답하지 마라.</b> dao 패키지에서 리터럴이 {@code =} 로 끝나며 값이 이어 붙는 형태는 SQL 조립 말고 쓸
   * 데가 거의 없다. 로그·예외 메시지가 걸린다면 log4j 의 {@code {}} 자리표시자를 쓰거나 구분자를 바꿔라 — 실제로 이 통합에서 {@code
   * JbgMallDataAccessObject} 의 메시지 두 개를 그렇게 고쳤다. 목록을 만드는 순간 그 파일 안에서 새로 생기는 조립은 못 잡는다.
   */
  private static final Pattern SQL_NUMERIC_CONCAT = Pattern.compile("=\\s*\"\\s*\\+");

  /**
   * {@code catch (SQLException)} 이 그대로 다시 던지는 형태.
   *
   * <p>여러 갱신이 {@code catch (SQLException)} 에서는 <b>롤백 없이</b> 던지고, 뒤에 오는 {@code catch (Exception)}
   * 에서만 되돌리고 있었다. 순서상 SQL 실패는 앞쪽에서 잡히므로 <b>정작 되돌려야 할 경우에만 안 되돌린다.</b> 두 catch 를 나란히 놓고 읽으면 대칭처럼 보여서
   * 오래 살아남은 형태다.
   *
   * <p>{@code [^{}]} 로 중괄호 없는 구간만 훑는다 — catch 블록 하나를 벗어나지 않는다는 뜻이다. {@code throw} 에 닿지 않는 catch(로그만
   * 남기고 삼키는 자리)는 애초에 걸리지 않는다.
   */
  private static final Pattern SQLEXCEPTION_RETHROW =
      Pattern.compile("catch \\(SQLException \\w+\\) \\{[^{}]*?throw");

  // 예외 목록(CONCAT_DEBT)은 없앴다. 통합 단계에서 남아 있던 두 건을 실제로 처리했기 때문이다.
  //
  //  - CommonDataAccessObject.getNextSeq — 호출부가 없고 SQLite 에 없는 information_schema 를
  //    읽던 죽은 경로여서 삭제했다.
  //  - JbgCollectBreakerDataAccessObject.getState — escape() 조립을 바인딩으로 바꿨다.
  //
  // 목록이 있는 한 아래 스캔은 "이 파일들만 빼고" 라는 조건부 보증이었다. 이제 무조건이다.
  // 다시 목록을 만들어 예외를 추가하지 마라 — 예외가 하나라도 있으면 통과 건수는 그 지점에 대해
  // 아무 것도 보증하지 않는다는 것이 이 가드가 처음 놓쳤던 실패의 형태다.
  //
  // 숫자 자리 스캔에는 아직 한 자리가 남아 있는데, 그것도 "이름 목록" 이 아니라 "int 파라미터라
  // 주입이 성립하지 않는다" 는 근거에 묶여 있다. 근거가 사라지면 단언이 먼저 깨진다 —
  // noNumericSlotConcatenationAppearsInDaoPackage 를 볼 것.

  /**
   * 주석을 걷어낸 소스. 실행되는 코드만 남긴다.
   *
   * <p>줄 주석은 처음부터 걷어냈다 — 죽은 코드를 주석으로 남겨 둔 자리가 있어 그대로 두면 오탐이 된다. <b>블록 주석(javadoc)까지 걷어내는 것은 이번에
   * 추가했다.</b> 금지하려는 형태를 javadoc 에 예시로 적는 순간 그 파일이 스스로 걸리기 때문이다. 이 통합에서 DAO javadoc 에 "이렇게 하면 안 된다"
   * 를 적어야 했고, 걷어내지 않았으면 설명을 적을수록 가드가 빨개지는 상태가 됐다.
   *
   * <p>줄 주석을 먼저 지운다. {@code JbgMallDataAccessObject} 에는 메서드 통째로 {@code //} 로 묶어 둔 자리가 있고 그 안에
   * javadoc 여는 표시가 들어 있다 — 블록부터 지우면 주석 경계가 엉뚱하게 잡힌다.
   */
  private static String codeOf(Path path) throws Exception {
    return Files.readString(path, StandardCharsets.UTF_8)
        .replaceAll("(?m)^\\s*//.*$", "")
        .replaceAll("(?s)/\\*.*?\\*/", "");
  }

  /**
   * dao 패키지 아래의 <b>모든 파일</b>. 목록을 손으로 적으면 새 DAO 가 감시 밖에 남는다.
   *
   * <p><b>예전에는 {@code .java} 로 끝나는 것만 걸렀다.</b> 그래서 죽은 백업본 하나가 — mall access/info 테이블을 통합하며 남긴
   * {@code .java.bak} 이다 — 아래 모든 스캔을 조용히 빠져나갔다. 그 안에는 값을 그대로 WHERE 절에 이어 붙인 자리가 여섯 곳 있었고, 그 파일이
   * PUBLIC 저장소에 커밋된 채였다. 스캔이 "dao 패키지 전수" 라고 주장하는 동안 실제로는 <b>확장자 하나가 통째로 사각지대</b>였다는 뜻이다.
   *
   * <p>그래서 <b>확장자를 보지 않는다.</b> 백업이든 임시본이든 새 확장자든, dao 디렉터리에 들어온 텍스트는 전부 본다. 확장자 목록을 다시 만들지 마라 — 목록을
   * 만드는 순간 목록에 없는 확장자가 다시 사각지대가 되고, 그것이 이번 사고의 형태 그 자체다.
   *
   * <p>깊이도 제한하지 않는다({@code Files.list} 가 아니라 {@code Files.walk}). 하위 패키지를 하나 만들면 그 안의 DAO 가 통째로 감시
   * 밖에 남는데, 그건 "목록을 손으로 적는" 것과 정확히 같은 실패다.
   *
   * <p>디렉터리만 걸러 낸다({@code isRegularFile}). {@code Files.readString} 이 디렉터리에서 던지면 가드가 걸린 것이 아니라 인프라가
   * 깨진 것처럼 보여서, 읽는 사람이 원인을 엉뚱한 데서 찾게 된다.
   */
  private static List<Path> daoSources() throws Exception {
    try (Stream<Path> files = Files.walk(DAO_DIR)) {
      return files.filter(Files::isRegularFile).toList();
    }
  }

  /**
   * 실패 메시지에 쓸 이름. 파일명만 적으면 하위 패키지가 생겼을 때 어느 파일인지 알 수 없다 — {@link #daoSources()} 가 깊이를 제한하지 않게 된 뒤로는
   * 파일명이 유일하지 않다. 구분자는 OS 마다 다르므로 {@code /} 로 고정한다.
   */
  private static String reportName(Path path) {
    return DAO_DIR.relativize(path).toString().replace('\\', '/');
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
      assertFalse(
          SQL_NUMERIC_CONCAT.matcher(code).find(),
          dao + " 에 따옴표 없는 숫자 자리 조립이 남아 있다. 바인딩 파라미터를 쓸 것.");
      assertTrue(code.contains("txPstmtExecuteUpdate"), dao + " 가 PreparedStatement 를 쓰지 않는다.");
    }
  }

  @Test
  @DisplayName("dao 패키지 어디에도 SQL 문자열 조립이 새로 생기지 않는다")
  void noNewSqlConcatenationAppearsInDaoPackage() throws Exception {
    // 파일 목록을 손으로 적어 두면 새로 만든 DAO 가 감시 밖에 남는다. 실제로 이번에 드러난 지점도
    // "그 DAO 는 목록에 없었다" 는 이유로 오래 살아남았다. 그래서 디렉터리를 통째로 훑는다.
    List<String> offenders = new ArrayList<>();

    for (Path path : daoSources()) {
      // 예외 없이 전부 본다. 면제 목록을 두면 그 파일 안에서 새 조립이 생겨도 못 잡는다.
      if (SQL_QUOTE_CONCAT.matcher(codeOf(path)).find()) {
        offenders.add(reportName(path));
      }
    }

    assertTrue(offenders.isEmpty(), "SQL 문자열 조립이 새로 생겼다(바인딩으로 쓸 것): " + offenders);
  }

  @Test
  @DisplayName("dao 패키지 어디에도 따옴표 없는 숫자 자리 조립이 새로 생기지 않는다")
  void noNumericSlotConcatenationAppearsInDaoPackage() throws Exception {
    List<String> offenders = new ArrayList<>();
    for (Path path : daoSources()) {
      if (SQL_NUMERIC_CONCAT.matcher(codeOf(path)).find()) {
        offenders.add(reportName(path));
      }
    }

    // 예외 없는 전수 스캔이다. 한때 JbgCollectLogDataAccessObject.getLog 한 자리가 조건부로
    // 허용돼 있었다 — 파라미터가 int 라 자바 타입이 숫자 외의 문자를 만들 수 없다는 근거였다.
    // 그 자리도 바인딩으로 옮겨져 허용이 사라졌고, 그래서 목록을 두지 않는다.
    //
    // 다시 목록을 만들지 마라. "이 자리는 int 라 안전하다" 는 논증은 시그니처가 바뀌는 순간
    // 조용히 무효가 되는데, 그 사실을 알려 주는 것은 아무것도 없다. 새 offender 가 나오면
    // 예외로 등록할 게 아니라 바인딩으로 옮겨라.
    assertTrue(offenders.isEmpty(), "따옴표 없는 숫자 자리 조립이 새로 생겼다(바인딩으로 쓸 것): " + offenders);
  }

  @Test
  @DisplayName("seq 는 숫자 필터로 깎지 않고 바인딩으로 넘긴다")
  void seqIsBoundInsteadOfDigitFiltered() throws Exception {
    // 필터를 방어로 착각하면 안 된다. replaceAll("[^0-9]", "") 는 거르는 게 아니라 깎는다 —
    // 숫자가 섞인 문자열을 남은 숫자만 이어 붙인 '다른 유효한 seq' 로 바꿔 놓는다. 즉 SQL 은 안
    // 깨지는데 대상이 조용히 바뀐다. JbgItemDataAccessObject.deleteByOrder 에서 그 일이 나면
    // 남의 주문에 딸린 아이템이 지워진다.
    for (String dao : new String[] {"JbgMallDataAccessObject", "JbgItemDataAccessObject"}) {
      String code = codeOf(DAO_DIR.resolve(dao + ".java"));

      assertFalse(
          code.contains("replaceAll(\"[^0-9]\""),
          dao + " 가 seq 를 숫자만 남기고 깎는다. 못 알아보는 값은 깎지 말고 아무 것도 고르지 않아야 한다.");
      assertTrue(code.contains("WHERE seq=?"), dao + " 의 seq 조회·갱신이 자리표시자를 쓰지 않는다.");
    }
  }

  @Test
  @DisplayName("트랜잭션을 여는 DAO 는 SQLException 에서도 되돌린 뒤 던진다")
  void sqlExceptionPathsRollBackBeforeRethrow() throws Exception {
    // 트랜잭션을 여는 DAO 전부가 대상이다. LocalDBConnection 만 빠져 있는데, 그쪽의 SQLException
    // 재던지기는 close() 의 자원 정리라 되돌릴 트랜잭션이 자체가 없다 — 봐 준 게 아니라 대상이
    // 다르다.
    //
    // 되돌리기는 반드시 rollbackQuietly(CommonDataAccessObject) 를 거쳐야 한다. 인라인
    // conn.txRollBack() 은 그것이 던지는 순간 원래 실패 원인을 덮어써서, 정작 고쳐야 할 원인이
    // 사라지고 "되돌리기가 실패했다" 만 남는다.
    for (String dao :
        new String[] {
          "JbgMallDataAccessObject",
          "JbgItemDataAccessObject",
          "JbgExportConfigDataAccessObject",
          "JbgOrderDataAccessObject"
        }) {
      String code = codeOf(DAO_DIR.resolve(dao + ".java"));

      List<String> naked = new ArrayList<>();
      Matcher matcher = SQLEXCEPTION_RETHROW.matcher(code);
      while (matcher.find()) {
        if (!matcher.group().contains("rollbackQuietly")) {
          naked.add(matcher.group().replaceAll("\\s+", " ").trim());
        }
      }

      assertTrue(naked.isEmpty(), dao + " 의 SQLException 경로가 되돌리지 않고 다시 던진다: " + naked);
    }
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

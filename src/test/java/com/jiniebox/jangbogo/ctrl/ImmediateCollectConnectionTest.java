package com.jiniebox.jangbogo.ctrl;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 즉시수집이 건너뛰면서 계정 연결을 끊지 않는지 감시한다 (Phase 5-20).
 *
 * <h2>왜 소스를 보나</h2>
 *
 * <p>이 규칙이 깨지는 형태는 <b>DB 쓰기 한 줄</b>이다. 실행으로 확인하려면 Spring 컨텍스트와 SQLite 와 mall_account.yml 이 모두 필요하고,
 * 그 준비 자체가 실계정 파일을 건드린다. 반면 재발은 소스 형태로 정확히 드러난다 — 건너뛰는 분기 옆에 {@code account_status} 를 0 으로 누르는 호출이
 * 다시 생기는 것이다. 그래서 {@code SecurityHardeningTest} 와 같은 방식으로 소스 형태를 본다.
 *
 * <h2>무엇이 문제였나</h2>
 *
 * <p>즉시수집은 스케줄러의 자격 판정을 복제해 두고, 건너뛸 때마다 {@code jaDao.update(seq, 0, null, null)} 로 계정 상태를 0 으로 눌렀다.
 * 세션만 있는 몰은 저장된 자격증명이 없는 것이 <b>정상 상태</b>인데 그 몰이 즉시수집 목록에 한 번만 들어가도 연결이 끊긴 것으로 바뀌었다. 스케줄러 쪽만 넓혀도 여기서
 * 원복됐다.
 *
 * <p>복호화 실패({@code BadPaddingException})만은 예외다. 그때는 실제로 저장된 암호문을 열 수 없어 사람이 다시 연결해야 한다.
 *
 * <p>브라우저·네트워크·DB 를 쓰지 않는다.
 *
 * @author KIUNSEA
 */
class ImmediateCollectConnectionTest {

  private static final Path ADMIN_CONTROLLER =
      Path.of("src/main/java/com/jiniebox/jangbogo/ctrl/AdminController.java");

  private static final Path SCHEDULER =
      Path.of("src/main/java/com/jiniebox/jangbogo/svc/MallSchedulerService.java");

  /**
   * 계정 상태를 0 으로 누르는 호출.
   *
   * <p>{@code seq} 를 첫 인자로 받는 형태만 본다 — 계정 연결 화면의 {@code update(seqMall, 1, ...)} 는 사용자가 직접 요청한 갱신이라
   * 이 감시 대상이 아니다.
   */
  private static final Pattern RESET_ACCOUNT_STATUS =
      Pattern.compile("update\\(\\s*seq\\s*,\\s*0\\s*,");

  @Test
  @DisplayName("즉시수집이 건너뛸 때 계정 연결을 끊지 않는다")
  void skippingDoesNotBreakTheConnection() throws Exception {
    String code = sourceWithoutLineComments(ADMIN_CONTROLLER);

    int badPaddingStart = code.indexOf("catch (BadPaddingException");
    int badPaddingEnd = code.indexOf("catch (Exception perMallEx", badPaddingStart + 1);
    assertTrue(badPaddingStart >= 0, "BadPaddingException 처리를 찾지 못했다. 이 테스트를 손봐야 한다.");
    assertTrue(badPaddingEnd > badPaddingStart, "BadPaddingException 블록의 끝을 찾지 못했다.");

    List<Integer> outside = new ArrayList<>();
    int total = 0;
    Matcher matcher = RESET_ACCOUNT_STATUS.matcher(code);
    while (matcher.find()) {
      total++;
      if (matcher.start() < badPaddingStart || matcher.start() > badPaddingEnd) {
        outside.add(lineOf(code, matcher.start()));
      }
    }

    assertTrue(
        outside.isEmpty(),
        "복호화 실패 처리 밖에서 account_status 를 0 으로 눌렀다 (줄 "
            + outside
            + "). 세션만 있는 몰은 자격증명이 없는 것이 정상이지 연결이 끊긴 것이 아니다.");
    assertEquals(1, total, "복호화 실패 처리의 계정 상태 초기화까지 사라졌다 — 그쪽은 실제로 재연결이 필요하다.");
  }

  @Test
  @DisplayName("자격 없는 몰은 '계정 연결 끊김' 으로 그려지는 목록에 담기지 않는다")
  void unqualifiedMallsGetTheirOwnBucket() throws Exception {
    // 대시보드는 응답의 skipped 목록에 담긴 몰을 '계정연결' 버튼 상태로 되돌린다.
    // 서버가 끊지 않기로 한 이상 화면만 끊긴 것처럼 보이면 사용자는 멀쩡한 연결을 다시 맺으려 한다.
    String code = sourceWithoutLineComments(ADMIN_CONTROLLER);

    assertTrue(code.contains("notQualified.add(seq)"), "자격 없는 몰을 담는 별도 목록이 없다.");
    assertTrue(
        code.contains("response.set(\"notQualified\""), "자격 없는 몰 목록이 응답에 실리지 않는다 — 사용자가 원인을 못 본다.");
  }

  @Test
  @DisplayName("자격 판정은 두 경로 모두 JangBoGoManager 한 곳에서 받아 온다")
  void bothPathsShareOneVerdict() throws Exception {
    // 판정을 각자 들고 있으면 두 경로는 언젠가 갈린다. 실제로 갈려 있었고, 그 결과가 위 두 테스트다.
    for (Path source : new Path[] {ADMIN_CONTROLLER, SCHEDULER}) {
      String code = sourceWithoutLineComments(source);

      assertTrue(
          code.contains("JangBoGoManager.qualify("), source.getFileName() + " 가 공통 자격 판정을 쓰지 않는다.");
      assertTrue(
          !code.contains("mall_account.yml에 계정 정보 없음")
              && !code.contains("mall_account.yml에 아이디/비밀번호 비어있음"),
          source.getFileName() + " 에 자격 판정 사유가 복제돼 있다. 문구가 갈리면 화면에 다른 말이 나간다.");
    }
  }

  @Test
  @DisplayName("세션 만료 몰은 '계정 연결 끊김' 으로 그려지는 목록에 담기지 않는다 (Phase 5-10)")
  void expiredSessionMallsGetTheirOwnBucket() throws Exception {
    // 만료된 것은 세션이지 계정이 아니다. skipped 에 담으면 대시보드가 그 몰을 '계정연결' 버튼으로
    // 되돌려, 사용자는 멀쩡한 연결을 다시 맺으려 한다. blocked 에 담아도 틀린다 — 그쪽 안내는
    // '잠시 뒤 다시' 인데 만료는 기다린다고 풀리지 않는다.
    String code = sourceWithoutLineComments(ADMIN_CONTROLLER);

    assertTrue(code.contains("sessionExpired.add(seq)"), "만료된 몰을 담는 별도 목록이 없다.");
    assertTrue(
        code.contains("response.set(\"sessionExpired\""), "만료 목록이 응답에 실리지 않는다 — 사용자가 원인을 못 본다.");
    assertTrue(code.contains("'브라우저로 로그인' 을 다시 실행"), "만료 안내가 사용자가 할 일을 가리키지 않는다.");
  }

  @Test
  @DisplayName("즉시수집도 스케줄러의 만료 통로를 그대로 쓴다")
  void bothPathsShareOneExpiryChannel() throws Exception {
    // 두 경로가 각자 기록하면 코드·단계·중단 여부가 언젠가 갈린다. 실제로 게이트(5-18)와
    // 즉시수집 우회(5-19)에서 같은 형태의 결함을 이미 두 번 고쳤다.
    String immediate = sourceWithoutLineComments(ADMIN_CONTROLLER);

    assertTrue(
        immediate.contains("mallSchedulerService.suspendForExpiredSession("),
        "즉시수집이 만료를 스케줄러의 공통 통로로 보내지 않는다.");
    assertTrue(
        immediate.contains("mallSchedulerService.resumeAfterSessionRecovery(seq)"),
        "사용자가 직접 고른 요청에서 일시중단을 풀지 않는다 — 다시 로그인해도 다음 기동까지 수집이 돌아오지 않는다.");
  }

  // ---------------------------------------------------------------
  // 대조군 — 판별식 자체가 살아 있는가
  // ---------------------------------------------------------------

  @Test
  @DisplayName("대조군: 계정 상태 초기화 정규식이 실제 호출 형태를 잡는다")
  void theResetPatternStillCatchesTheCall() {
    // skippingDoesNotBreakTheConnection 은 assertEquals(1, total) 덕분에 정규식이 완전히
    // 죽으면 빨개진다 — 이 파일에는 대조군이 절반쯤 이미 있는 셈이다. 그래도 '어떤 형태를
    // 잡는가' 는 아무 데서도 확인되지 않아서, 공백이 낀 형태나 다른 인자 조합으로 되살아나면
    // 조용히 빠져나간다.
    //
    // 이 세션에서 실제로 두 번 겪은 형태다 — 만료 감지는 테스트 25건이 초록인 채 프로덕션
    // 호출자가 0건이었고, 배포 산출물 가드는 판별식을 무력화해도 5건이 전부 통과했다.
    for (String reset :
        new String[] {
          "jaDao.update(seq, 0, null, null);",
          "jaDao.update( seq , 0 , null, null);",
          "jaDao.update(seq,0,null,null);"
        }) {
      assertTrue(
          RESET_ACCOUNT_STATUS.matcher(reset).find(),
          "계정 상태를 0 으로 누르는 호출인데 정규식이 잡지 못했다. 이 상태면 이 감시가 통째로 무동작이다: " + reset);
    }

    // 사용자가 직접 요청한 갱신은 대상이 아니다. 여기가 걸리면 계정 연결 화면이 감시에 걸려,
    // 사람은 정규식을 고치는 대신 검사를 지운다.
    for (String allowed :
        new String[] {
          "jaDao.update(seqMall, 1, encId, encPw);",
          "jaDao.update(seq, 1, null, null);",
          "notQualified.add(seq);"
        }) {
      assertFalse(
          RESET_ACCOUNT_STATUS.matcher(allowed).find(), "감시 대상이 아닌 호출이 걸렸다(오탐): " + allowed);
    }
  }

  /** 줄 주석을 걷어낸 소스. 주석에 적힌 예전 코드 형태가 감시에 걸리지 않게 한다. */
  private static String sourceWithoutLineComments(Path path) throws Exception {
    String source = Files.readString(path, StandardCharsets.UTF_8);
    return source.replaceAll("(?m)^\\s*//.*$", "");
  }

  /** 실패 메시지에 실을 줄 번호. */
  private static int lineOf(String code, int index) {
    int line = 1;
    for (int i = 0; i < index && i < code.length(); i++) {
      if (code.charAt(i) == '\n') {
        line++;
      }
    }
    return line;
  }
}

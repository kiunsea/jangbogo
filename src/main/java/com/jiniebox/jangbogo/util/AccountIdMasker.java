package com.jiniebox.jangbogo.util;

/**
 * 로그에 남는 계정 아이디를 가린다.
 *
 * <p><b>실제로 새고 있었다.</b> {@code MallOrderUpdater} 의 수집 시작 로그가 쇼핑몰 로그인 아이디를 INFO 로 그대로 찍었다 — 이 프로젝트가
 * 세션 쿠키·암호화 키에 대해서는 "값은 기록하지 않는다" 를 철저히 지키는 동안 계정 아이디만 예외였다. 아이디는 자격증명의 절반이라, 비밀번호가 없어도 크리덴셜 스터핑과
 * 표적 피싱의 입력이 된다.
 *
 * <p>{@code logs/} 가 {@code .gitignore} 되는 것으로 끝이 아니다. {@code SECURITY.md} 는 기여자에게 <b>"버그 리포트에 로그가
 * 필요하면 해당 줄만 발췌하고 계정 아이디를 지운 뒤 올리라"</b> 고 안내한다. 그 안내는 사람이 매번 손으로 지워 준다는 가정 위에 서 있고, 그런 가정은 반드시 한 번은
 * 깨진다. 로그 줄 자체가 <b>그대로 붙일 수 있는 상태</b>여야 안내가 성립한다.
 *
 * <h2>왜 통째로 지우지 않는가</h2>
 *
 * <p>지우면 진단이 죽는다. 이 값은 "이번 회차에 쓸 수 있는 자격증명이 있었는가" 를 가르고({@code MallOrderUpdater.hasCredentials}), 그
 * 판정이 수집기를 돌릴지 건너뛸지를 정한다. 즉 그 줄은 "왜 이 수집기가 안 돌았는가" 를 사람이 물을 수 있는 자리다. 그래서 <b>계정이 바뀐 것·몰마다 다른 계정을 쓰는
 * 것은 구분되고, 원본은 복원되지 않는</b> 형태가 필요하다.
 *
 * <h2>형태: 앞 한 글자 + 고정 마스크 + 글자 수</h2>
 *
 * <p>{@code hong***(11자)} 가 아니라 {@code h***(11자)} 다. 셋을 정해 두었다.
 *
 * <ul>
 *   <li><b>앞 한 글자만 남긴다.</b> 두 글자를 남기면 짧은 아이디에서 원본의 대부분이 그대로 나온다.
 *   <li><b>별표 수는 값과 무관하게 고정이다.</b> 길이만큼 찍으면 마스크 자체가 길이를 다시 흘리고, 긴 아이디에서 로그 줄만 길어진다. 길이는 괄호 안에 <b>한
 *       번만</b> 적는다.
 *   <li><b>1~2 글자는 앞 글자도 남기지 않는다.</b> 두 글자짜리에서 한 글자를 남기는 것은 절반을 공개하는 것이다.
 * </ul>
 *
 * <p><b>복원되지 않는다는 것이 설계다.</b> 앞 글자와 길이가 같은 서로 다른 아이디는 <b>같은 결과로 뭉개진다</b> — 되돌리는 함수가 존재할 수 없다는 뜻이다.
 * 해시(예: SHA-256 앞 몇 자리)를 쓰지 않은 이유도 여기 있다. 아이디는 후보 공간이 좁아 해시를 찍어 두면 사전 대입으로 되돌아오는데, 이쪽은 되돌릴 원본이 애초에
 * 남지 않는다.
 *
 * <p>순수 함수다 — 로거·설정·시계·DB 를 건드리지 않는다. {@code AccountIdMaskerTest} 가 값으로 계약을 고정하고, {@code
 * AccountIdLogMaskingTest} 가 로그 자리로 되돌아가는 것을 막는다.
 *
 * @author KIUNSEA
 */
public final class AccountIdMasker {

  /**
   * 값이 아예 없다.
   *
   * <p>세션 주입 경로는 복호화를 하지 않고 아이디를 null 로 내려보낸다 — 그것이 그 경로의 <b>정상 상태</b>다. 그래서 "실패" 로 읽히지 않는 문구를 쓴다.
   */
  public static final String NONE = "(없음)";

  /**
   * 값은 왔는데 비어 있다.
   *
   * <p>{@link #NONE} 과 나누는 이유는 <b>원인이 다르기 때문</b>이다. null 은 복호화를 아예 하지 않은 정상 경로이고, 빈 문자열은 저장된 계정이
   * 깨졌다는 신호다. 하나로 합치면 데이터 사고가 정상 상태에 묻혀 아무도 보지 못한다.
   */
  public static final String BLANK = "(빈값)";

  /** 별표 수는 값과 무관하게 고정한다. 길이만큼 찍으면 마스크가 길이를 다시 흘린다. */
  private static final String STARS = "***";

  /** 앞 글자를 남겨도 되는 최소 글자 수. 2 글자에서 한 글자를 남기면 절반이 그대로 공개된다. */
  private static final int MIN_LENGTH_TO_KEEP_PREFIX = 3;

  private AccountIdMasker() {}

  /**
   * 계정 아이디를 로그에 실을 수 있는 형태로 가린다.
   *
   * <p>어떤 입력에도 던지지 않고 null 도 돌려주지 않는다. 마스킹이 예외를 던지면 그것을 부르는 로그 줄이 통째로 사라지거나 수집이 멈추는데, 진단용 한 줄을 위해
   * 그런 위험을 지는 것은 앞뒤가 맞지 않는다.
   *
   * @param accountId 쇼핑몰 로그인 아이디. null·빈 문자열이어도 된다
   * @return 원본을 복원할 수 없는 표시 문자열. 절대 null 이 아니다
   */
  public static String mask(String accountId) {
    if (accountId == null) {
      return NONE;
    }
    if (accountId.isBlank()) {
      return BLANK;
    }

    // 글자 수는 코드포인트로 센다. String.length() 는 UTF-16 단위라 보조 평면 문자가 섞이면
    // 사람이 세는 글자 수와 어긋나고, 그 어긋난 수가 진단에 쓰이면 없는 문제를 뒤지게 된다.
    int length = accountId.codePointCount(0, accountId.length());
    if (length < MIN_LENGTH_TO_KEEP_PREFIX) {
      return STARS + "(" + length + "자)";
    }

    // charAt(0) 이 아니라 codePointAt(0) 이다. 앞 글자가 보조 평면 문자면 charAt 은 서로게이트
    // 한쪽만 떼어 내고, 그 조각이 로그 파일에 들어가면 줄 전체가 깨진 문자로 나온다.
    String head = new String(Character.toChars(accountId.codePointAt(0)));
    return head + STARS + "(" + length + "자)";
  }
}

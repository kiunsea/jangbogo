package com.jiniebox.jangbogo.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 계정 아이디 마스킹의 계약 고정.
 *
 * <p>마스킹은 두 가지를 <b>동시에</b> 만족해야 쓸모가 있다 — 원본이 복원되지 않을 것, 그러면서도 어느 계정인지 구분은 될 것. 하나만 보면 잘못된 방향으로 다듬기
 * 쉽다. 복원 불가만 보면 "전부 {@code ***} 로 바꾸자" 가 되어 진단이 죽고(그러면 "왜 이 수집기가 안 돌았는가" 에 답할 단서가 사라진다), 구분 가능만 보면
 * 앞 글자를 늘리다가 원본이 도로 나온다. 그래서 아래는 양쪽을 나란히 못 박는다.
 *
 * <p>여기 쓰는 아이디는 <b>전부 합성 값</b>이다. 이 저장소는 PUBLIC 이라, 실제 로그인 아이디를 테스트에 적으면 아이디를 가리려고 넣은 작업이 그 자리에서
 * 원본을 공개하는 꼴이 된다.
 *
 * <p>브라우저·네트워크·DB 를 쓰지 않는다.
 *
 * @author KIUNSEA
 */
class AccountIdMaskerTest {

  @Test
  @DisplayName("null 은 '없음' 으로 남는다 — 세션 경로의 정상 상태라 실패로 읽히면 안 된다")
  void nullBecomesNoneMarker() {
    // 세션 주입 경로는 복호화를 아예 하지 않고 null 을 내려보낸다. 그것이 그 경로의 정상 상태다.
    assertEquals(AccountIdMasker.NONE, AccountIdMasker.mask(null));
  }

  @Test
  @DisplayName("빈 값은 '빈값' 으로 남는다 — null 과 합치면 데이터 사고가 정상 상태에 묻힌다")
  void blankBecomesBlankMarker() {
    // null 은 '복호화를 안 했다'(정상), 빈 문자열은 '저장된 계정이 깨졌다'(사고)다. 원인이 다르므로
    // 표시도 달라야 한다. 공백만 있는 값도 같은 사고다 — 로그인 폼에 넣으면 빈 값과 똑같이 실패한다.
    assertEquals(AccountIdMasker.BLANK, AccountIdMasker.mask(""));
    assertEquals(AccountIdMasker.BLANK, AccountIdMasker.mask("   "));
    assertEquals(AccountIdMasker.BLANK, AccountIdMasker.mask("\t\n"));

    assertNotEquals(
        AccountIdMasker.NONE, AccountIdMasker.BLANK, "'없음' 과 '빈값' 이 같은 문자열이면 두 원인을 구분할 수 없다.");
  }

  @Test
  @DisplayName("1~2 글자는 앞 글자도 남기지 않는다 — 한 글자를 남기면 절반이 공개된다")
  void veryShortIdsKeepNoPrefix() {
    assertEquals("***(1자)", AccountIdMasker.mask("a"));
    assertEquals("***(2자)", AccountIdMasker.mask("ab"));

    // 짧은 값에서 깨지지 않는 것만으로는 부족하다. 앞 글자가 새지 않는지까지 본다.
    assertFalse(AccountIdMasker.mask("ab").contains("a"), "2 글자 아이디에서 앞 글자가 그대로 나왔다 — 절반이 공개된다.");
  }

  @Test
  @DisplayName("3 글자부터는 앞 한 글자와 글자 수만 남는다")
  void longerIdsKeepOnlyFirstCharacterAndLength() {
    assertEquals("a***(3자)", AccountIdMasker.mask("abc"));
    assertEquals("s***(10자)", AccountIdMasker.mask("shopperkey"));
  }

  @Test
  @DisplayName("원본이 복원되지 않는다 — 앞 글자 뒤로는 한 글자도 남지 않는다")
  void maskedValueDoesNotLeakTheOriginal() {
    // 숫자가 없는 합성 아이디를 쓴다. 결과에 글자 수(숫자)가 들어가므로, 원본에 숫자가 섞이면
    // 아래 '한 글자도 남지 않는다' 검사가 그 숫자 때문에 헷갈린다.
    String id = "shopperkey";
    String masked = AccountIdMasker.mask(id);

    assertFalse(masked.contains(id), "결과에 원본이 통째로 들어 있다: " + masked);
    assertFalse(masked.contains(id.substring(1)), "앞 글자만 떼고 나머지가 그대로 남았다: " + masked);
    for (int i = 1; i < id.length(); i++) {
      assertFalse(
          masked.contains(String.valueOf(id.charAt(i))),
          "앞 글자 뒤의 글자가 결과에 남았다(" + id.charAt(i) + "): " + masked);
    }
  }

  @Test
  @DisplayName("앞 글자와 길이가 같은 다른 계정은 같은 결과로 뭉개진다 — 되돌리는 함수가 존재할 수 없다")
  void differentIdsWithSameShapeCollide() {
    // 이것이 '복원 불가' 의 근거다. 같은 결과를 내는 원본이 여럿이면 되돌리는 함수 자체가 없다.
    // 해시를 안 쓴 이유이기도 하다 — 아이디는 후보 공간이 좁아 해시는 사전 대입으로 되돌아온다.
    assertEquals(
        AccountIdMasker.mask("alpha-one"),
        AccountIdMasker.mask("abcde-fgh"),
        "앞 글자·길이가 같은데 결과가 다르다 — 결과에 원본의 다른 정보가 새고 있다는 뜻이다.");
  }

  @Test
  @DisplayName("그래도 계정 구분은 된다 — 앞 글자나 길이가 다르면 결과가 다르다")
  void maskedValueStillDistinguishesAccounts() {
    // 구분이 안 되면 마스킹이 아니라 삭제다. "몰마다 다른 계정을 쓰는가", "계정이 바뀌었는가" 를
    // 로그만 보고 물을 수 있어야 이 줄을 남길 이유가 있다.
    assertNotEquals(
        AccountIdMasker.mask("alpha-one"), AccountIdMasker.mask("bravo-one"), "앞 글자가 다른데 결과가 같다.");
    assertNotEquals(
        AccountIdMasker.mask("alpha-one"), AccountIdMasker.mask("alpha-two-x"), "길이가 다른데 결과가 같다.");
  }

  @Test
  @DisplayName("별표 수가 길이를 다시 흘리지 않는다")
  void maskWidthIsFixedRegardlessOfLength() {
    // 별표를 길이만큼 찍으면 괄호 안의 숫자를 지워도 마스크가 길이를 그대로 말해 준다. 길이를
    // 한 번만 적기로 한 결정이 코드에서 유지되는지 본다.
    String shortId = AccountIdMasker.mask("abc");
    String longId = AccountIdMasker.mask("abcdefghijklmnopqrstuvwxyz0123456789");

    assertEquals(countStars(shortId), countStars(longId), "별표 수가 값에 따라 변한다 — 마스크가 길이를 흘린다.");
    assertTrue(longId.length() < 16, "긴 아이디에서 결과가 길이에 비례해 늘어난다: " + longId);
  }

  @Test
  @DisplayName("보조 평면 문자로 시작해도 깨지지 않는다 — 서로게이트 한쪽만 떨어져 나가면 로그 줄이 깨진다")
  void supplementaryCharactersSurviveIntact() {
    // charAt(0) 으로 앞 글자를 떼면 여기서 서로게이트 한쪽만 나가고, 그 조각이 로그 파일에 들어가면
    // 줄 전체가 깨진 문자로 나온다. 실제 아이디에 흔하지는 않지만, '깨지지 않는다' 는 계약은
    // 흔한 입력에서만 성립하면 계약이 아니다.
    String astral = new String(Character.toChars(0x1F600));
    String masked = AccountIdMasker.mask(astral + "bcd");

    assertEquals(astral + "***(4자)", masked, "코드포인트로 세지 않아 글자 수나 앞 글자가 어긋났다.");
    assertFalse(hasLoneSurrogate(masked), "결과에 짝 없는 서로게이트가 남았다 — 로그 줄이 깨진다: " + masked);
  }

  @Test
  @DisplayName("어떤 입력에도 던지지 않고 null 을 돌려주지 않는다")
  void maskNeverThrowsAndNeverReturnsNull() {
    // 마스킹이 던지면 그것을 부르는 로그 줄이 통째로 사라지거나 수집이 멈춘다. 진단용 한 줄을 위해
    // 그런 위험을 지는 것은 앞뒤가 맞지 않는다.
    for (String input : new String[] {null, "", " ", "a", "ab", "abc", "가나다라마", "@#$%^&*()"}) {
      assertNotNull(AccountIdMasker.mask(input), "결과가 null 이다. 입력: " + input);
    }
  }

  private static long countStars(String value) {
    return value.chars().filter(c -> c == '*').count();
  }

  /**
   * 짝을 잃은 서로게이트가 있는가. 양쪽을 다 본다 — 앞짝만 남은 경우와 뒷짝만 남은 경우가 모두 깨진 문자로 나온다.
   *
   * <p>이 검사 자체가 죽는 것도 막아야 해서 {@link #theLoneSurrogateDetectorActuallyDetects()} 가 뒤를 받친다.
   */
  private static boolean hasLoneSurrogate(String value) {
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (Character.isHighSurrogate(c)) {
        if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(i + 1))) {
          return true;
        }
      } else if (Character.isLowSurrogate(c)) {
        if (i == 0 || !Character.isHighSurrogate(value.charAt(i - 1))) {
          return true;
        }
      }
    }
    return false;
  }

  @Test
  @DisplayName("대조군: 짝 없는 서로게이트 판별식이 실제로 판별한다")
  void theLoneSurrogateDetectorActuallyDetects() {
    // 위 검사는 "짝 없는 서로게이트가 없다" 형태다. 판별식이 항상 false 를 돌려주게 바뀌면 그대로
    // 초록이 된다 — 이 저장소가 두 번 겪은 '가드가 죽었는데 초록' 이 정확히 그 형태다.
    String astral = new String(Character.toChars(0x1F600));

    assertTrue(
        hasLoneSurrogate(astral.substring(0, 1)), "앞짝만 남은 조각을 잡지 못한다 — 이 판별식은 아무것도 보증하지 않는다.");
    assertTrue(hasLoneSurrogate(astral.substring(1)), "뒷짝만 남은 조각을 잡지 못한다.");
    assertFalse(hasLoneSurrogate(astral + "***(4자)"), "멀쩡한 문자열이 깨진 것으로 걸린다(오탐).");
    assertFalse(hasLoneSurrogate("s***(10자)"), "서로게이트가 없는 문자열이 걸린다(오탐).");
  }
}

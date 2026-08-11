package com.jiniebox.jangbogo.svc.mall;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 실사이트 화면을 <b>내용 없이 형태만</b> 요약하는 순수 함수 모음 (하나로 온라인/오프라인 분리).
 *
 * <h2>왜 필요한가</h2>
 *
 * <p>새 수집기의 셀렉터를 쓰려면 실제 화면의 표 구조·열 개수·값의 형식을 알아야 한다. 그런데 그 화면은 <b>사람의 구매 내역</b>이고 이 저장소는 PUBLIC 이다.
 * 화면을 그대로 떠서 남기면 그 순간 개인정보가 작업 산출물이 된다.
 *
 * <p>그래서 프로브는 값을 옮기지 않고 <b>형태</b>만 옮긴다. {@code 2026-08-10} 은 {@code NNNN-NN-NN} 이 되고 상품명은 {@code
 * 가×7} 이 된다. 파서를 쓰는 데 필요한 것은 후자로 충분하다 — 열이 몇 개인지, 몇 번째가 날짜인지, 금액에 쉼표와 '원' 이 붙는지는 형태만으로 다 드러난다.
 *
 * <p>이 규칙이 실제로 지켜지는지는 {@link DomShapeReportTest} 가 고정한다. 특히 <b>원문이 요약에 남지 않는다</b>는 것이 기능 요건이며 그 경계를
 * 테스트가 지킨다.
 *
 * <p>브라우저·네트워크·DB 를 쓰지 않는 순수 함수라 기본 테스트 묶음에서 돈다.
 *
 * @author KIUNSEA
 */
final class DomShapeReport {

  private DomShapeReport() {}

  /** 같은 문자 종류가 이보다 길게 이어지면 {@code 가×20} 형태로 접는다. */
  static final int RUN_LIMIT = 12;

  /** {@link #idCensus} 가 나열하는 id 종의 상한. */
  static final int ID_SAMPLE_LIMIT = 40;

  /** 원문을 그대로 실어도 되는 라벨의 길이 상한. {@link #headerLabel} 참조. */
  static final int LABEL_LENGTH_LIMIT = 20;

  /** 이 자리 수 이상 이어지는 숫자는 식별자로 본다. {@link #maskLongDigitRuns} 참조. */
  static final int LONG_DIGIT_RUN = 6;

  // ── 형태 ────────────────────────────────────────────────────────────────

  /**
   * 텍스트를 문자 종류로 바꾼다 — 내용은 사라지고 형식만 남는다.
   *
   * <p>숫자는 {@code N}, 한글은 {@code 가}, 라틴 글자는 {@code a}, 그 밖의 글자는 {@code x} 로 바뀐다. <b>구두점과 통화 기호는 그대로
   * 둔다</b> — {@code 12,345원} 과 {@code 12345} 를 가르는 것이 파서가 정규화 규칙을 정할 때 필요한 정보이고, 쉼표 자체는 아무것도 식별하지
   * 않는다.
   *
   * <p>공백은 길이와 무관하게 한 칸으로 접는다.
   *
   * @param text 화면에서 읽은 원문. null 이나 공백일 수 있다
   * @return 형태 문자열. 원문의 어떤 글자도 포함하지 않는다(구두점 제외)
   */
  static String shape(String text) {
    if (text == null) {
      return "(읽기 실패)";
    }
    String trimmed = text.trim();
    if (trimmed.isEmpty()) {
      return "(빈칸)";
    }

    StringBuilder classes = new StringBuilder();
    for (int i = 0; i < trimmed.length(); ) {
      int cp = trimmed.codePointAt(i);
      classes.append(classOf(cp));
      i += Character.charCount(cp);
    }
    return collapseRuns(classes.toString());
  }

  /** 한 글자를 문자 종류 한 글자로. */
  private static char classOf(int cp) {
    if (Character.isWhitespace(cp)) {
      return ' ';
    }
    if (Character.isDigit(cp)) {
      return 'N';
    }
    if (isHangul(cp)) {
      return '가';
    }
    if (cp < 128 && Character.isLetter(cp)) {
      return 'a';
    }
    if (Character.isLetter(cp)) {
      return 'x';
    }
    return (char) cp;
  }

  private static boolean isHangul(int cp) {
    return (cp >= 0xAC00 && cp <= 0xD7A3) // 완성형 음절
        || (cp >= 0x1100 && cp <= 0x11FF) // 자모
        || (cp >= 0x3130 && cp <= 0x318F); // 호환 자모
  }

  /**
   * 같은 글자가 {@link #RUN_LIMIT} 보다 길게 이어지면 {@code 가×20} 으로 접는다.
   *
   * <p>날짜·금액처럼 짧은 것은 {@code NNNN-NN-NN} 그대로 읽히는 편이 훨씬 쓸모 있고, 긴 상품명은 접어야 요약이 읽힌다. 공백은 길이와 무관하게 한
   * 칸이다.
   */
  private static String collapseRuns(String classes) {
    StringBuilder out = new StringBuilder();
    int i = 0;
    while (i < classes.length()) {
      char c = classes.charAt(i);
      int run = 1;
      while (i + run < classes.length() && classes.charAt(i + run) == c) {
        run++;
      }
      if (c == ' ') {
        out.append(' ');
      } else if (run <= RUN_LIMIT) {
        out.append(String.valueOf(c).repeat(run));
      } else {
        out.append(c).append('×').append(run);
      }
      i += run;
    }
    return out.toString();
  }

  /**
   * 라벨을 요약한다 — 표의 머리글은 원문을 그대로 실어도 되는 <b>유일한</b> 자리다.
   *
   * <p>{@code 주문일자}·{@code 상품명} 같은 머리글은 사이트가 모두에게 똑같이 주는 구조이지 그 사람의 정보가 아니고, 파서가 th/td 를 키로 짝지을 때 이
   * 문자열이 그대로 필요하다({@code Hanaro.parseDetailPage} 가 {@code "구매일자"} 로 분기하는 것이 그 예다).
   *
   * <p>다만 <b>머리글 자리에 데이터가 들어 있는 표</b>가 있다. 그래서 두 가지 조건을 건다 — 길이가 {@link #LABEL_LENGTH_LIMIT} 이하이고
   * 숫자를 포함하지 않을 것. 하나라도 어기면 라벨로 보지 않고 {@link #shape} 로 떨어뜨린다.
   *
   * <p><b>{@code <th>} 밖에서 부르지 말 것.</b> 이 두 조건은 점포명·지점명·사람 이름의 모양과 정확히 겹친다 — {@code 하나로마트양재점} 은 8자에
   * 숫자가 없어 그대로 통과한다. 그래서 제목·선택지·버튼 글자처럼 <b>사이트가 모두에게 똑같이 주지 않는</b> 텍스트에 쓰면 그 자리가 곧 누출 경로가 된다. 실제로 이
   * 프로브의 첫 판이 세 자리에서 그랬고, 전부 {@link #shape} 로 바꿨다.
   *
   * @param text 머리글 셀에서 읽은 원문
   * @return 라벨이면 원문, 아니면 형태
   */
  static String headerLabel(String text) {
    if (text == null) {
      return "(읽기 실패)";
    }
    String trimmed = text.trim();
    if (trimmed.isEmpty()) {
      return "(빈칸)";
    }
    if (trimmed.length() > LABEL_LENGTH_LIMIT) {
      return shape(trimmed);
    }
    for (int i = 0; i < trimmed.length(); i++) {
      if (Character.isDigit(trimmed.charAt(i))) {
        return shape(trimmed);
      }
    }
    return trimmed;
  }

  // ── 식별자 ──────────────────────────────────────────────────────────────

  /**
   * 값에 박힌 숫자를 가린다.
   *
   * <p>{@code order_20260810123} 같은 id 에는 주문번호가 그대로 들어 있다. 자릿수는 남기되 값은 지운다 — 자릿수가 같으면 같은 종류의 id 로
   * 묶이므로 반복 렌더링되는 행을 한 종으로 접을 수 있다.
   *
   * @param value id·name·class 등
   * @return 숫자가 {@code N} 으로 바뀐 값
   */
  static String maskDigits(String value) {
    if (value == null) {
      return null;
    }
    StringBuilder out = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      out.append(Character.isDigit(c) ? 'N' : c);
    }
    return out.toString();
  }

  /**
   * <b>긴</b> 숫자만 가린다.
   *
   * <p>{@link #maskDigits} 는 숫자를 전부 지우므로 주소에 쓰면 {@code nahh_70090.do} 가 {@code nahh_NNNNN.do} 가 된다
   * — 그 화면 코드가 바로 이 프로브가 얻으려는 산출물이라, 전부 가리면 측정 자체가 사라진다.
   *
   * <p>반면 주문번호·회원번호는 길다. 그래서 <b>{@link #LONG_DIGIT_RUN} 자리 이상 이어지는 숫자만</b> 가린다. 화면 코드(5자리 이하)는 살고
   * 식별자는 죽는 경계다.
   *
   * @param value 주소나 경로
   * @return 긴 숫자가 {@code N} 으로 바뀐 값
   */
  static String maskLongDigitRuns(String value) {
    if (value == null) {
      return null;
    }
    StringBuilder out = new StringBuilder(value.length());
    int i = 0;
    while (i < value.length()) {
      if (!Character.isDigit(value.charAt(i))) {
        out.append(value.charAt(i));
        i++;
        continue;
      }
      int run = 0;
      while (i + run < value.length() && Character.isDigit(value.charAt(i + run))) {
        run++;
      }
      out.append(run >= LONG_DIGIT_RUN ? "N".repeat(run) : value.substring(i, i + run));
      i += run;
    }
    return out.toString();
  }

  /**
   * {@code javascript:} 링크에서 <b>함수 이름만</b> 남기고 인자를 통째로 버린다.
   *
   * <p>목록에서 상세로 들어가는 링크는 보통 {@code javascript:fnGoDetail('...','...')} 꼴이고, 그 인자에 주문번호뿐 아니라
   * <b>상품명·점포명이 그대로 실린다.</b> 숫자만 가리는 것으로는 한글도 라틴 글자도 남는다.
   *
   * <p>파서를 쓰는 데 필요한 것은 <b>어떤 함수를 부르는가</b>이지 그 인자가 아니다 — 인자는 어차피 행마다 다르고, 수집기는 행을 클릭해서 얻는다.
   *
   * @param href {@code javascript:} 로 시작하는 링크
   * @return {@code javascript:fnGoDetail(…)} 형태. 괄호가 없으면 숫자만 가린 원본
   */
  static String callSignature(String href) {
    if (href == null) {
      return null;
    }
    int paren = href.indexOf('(');
    if (paren < 0) {
      return maskDigits(href);
    }
    return maskDigits(href.substring(0, paren)) + "(…)";
  }

  /**
   * id 목록을 마스킹해 종별로 묶고 상한을 적용한다.
   *
   * <p>{@code item_1, item_2, item_3} 은 마스킹 뒤 전부 {@code item_N} 이라 한 종이 된다. 반복 행이 목록을 가득 채워 정작 볼 것이
   * 밀리는 것을 막는다.
   *
   * @param ids id 목록. null 이거나 빈 값이 섞여도 된다
   * @return {@code id 3종 [a, b, c]} 형태. 없으면 {@code id 0종}
   */
  static String idCensus(List<String> ids) {
    Set<String> species = new LinkedHashSet<>();
    if (ids != null) {
      for (String id : ids) {
        if (id != null && !id.isBlank()) {
          species.add(maskDigits(id.trim()));
        }
      }
    }
    if (species.isEmpty()) {
      return "id 0종";
    }
    List<String> shown = new ArrayList<>(species);
    boolean truncated = shown.size() > ID_SAMPLE_LIMIT;
    if (truncated) {
      shown = shown.subList(0, ID_SAMPLE_LIMIT);
    }
    return "id " + species.size() + "종 [" + String.join(", ", shown) + (truncated ? ", …]" : "]");
  }

  /**
   * 태그 이름을 집계한다 — 많은 것부터, 같으면 이름순.
   *
   * <p>대소문자를 가리지 않는다. 읽지 못한 이름은 {@code ?} 로 세되 버리지 않는다 — "자손이 몇 개인지는 아는데 이름을 못 읽었다" 와 "자손이 없다" 는 다른
   * 상태다.
   *
   * @param tags 태그 이름 목록
   * @return {@code div=3, img=2} 형태. 비어 있으면 빈 문자열
   */
  static String tagCensus(List<String> tags) {
    if (tags == null || tags.isEmpty()) {
      return "";
    }
    Map<String, Integer> counts = new TreeMap<>();
    for (String tag : tags) {
      String key = (tag == null || tag.isBlank()) ? "?" : tag.trim().toLowerCase();
      counts.merge(key, 1, Integer::sum);
    }
    List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
    entries.sort(
        Comparator.comparing(Map.Entry<String, Integer>::getValue)
            .reversed()
            .thenComparing(Map.Entry::getKey));

    List<String> parts = new ArrayList<>();
    for (Map.Entry<String, Integer> e : entries) {
      parts.add(e.getKey() + "=" + e.getValue());
    }
    return String.join(", ", parts);
  }

  // ── 주소 ────────────────────────────────────────────────────────────────

  /**
   * 주소에서 <b>질의 문자열의 값을 지우고 이름만</b> 남긴다.
   *
   * <p>기간별 조회는 날짜를 질의로 넘길 가능성이 크고, 그 값은 그 사람이 언제 무엇을 샀는지를 좁히는 정보다. 세션 토큰이 질의에 실리는 사이트도 있다. 파서를 쓰는 데
   * 필요한 것은 <b>어떤 파라미터가 있는가</b>이지 그 값이 아니다.
   *
   * @param url 브라우저가 보고한 현재 주소
   * @return {@code https://host/path?a&b (값 가림)} 형태
   */
  static String safeUrl(String url) {
    if (url == null || url.isBlank()) {
      return "(없음)";
    }
    // 서블릿 경로 파라미터. 구형 .do/.nh 사이트는 쿠키가 없을 때 세션 id 를 경로에 붙인다
    // (…/list.do;jsessionid=ABC123?page=1). 그 값은 살아 있는 세션 그 자체다.
    String stripped = url.replaceAll("(?i);jsessionid=[^?/]*", "");

    int q = stripped.indexOf('?');
    if (q < 0) {
      return stripped;
    }
    String base = stripped.substring(0, q);
    List<String> names = new ArrayList<>();
    for (String pair : stripped.substring(q + 1).split("&")) {
      if (pair.isBlank()) {
        continue;
      }
      int eq = pair.indexOf('=');
      names.add(eq < 0 ? pair : pair.substring(0, eq));
    }
    if (names.isEmpty()) {
      return base;
    }
    return base + "?" + String.join("&", names) + " (값 가림)";
  }

  // ── 행 ──────────────────────────────────────────────────────────────────

  /**
   * 셀 목록을 인덱스가 붙은 형태 목록으로 만든다.
   *
   * <p>인덱스는 0 부터다 — 파서가 {@code tdList.get(0)} 으로 읽으므로 그 번호와 그대로 맞아야 옮겨 적을 때 어긋나지 않는다.
   *
   * @param cellTexts 셀에서 읽은 원문 목록
   * @return {@code [0] NNNN-NN-NN | [1] 가×7 | [2] NN,NNN원}
   */
  static String cellShapes(List<String> cellTexts) {
    if (cellTexts == null || cellTexts.isEmpty()) {
      return "(셀 없음)";
    }
    List<String> parts = new ArrayList<>();
    for (int i = 0; i < cellTexts.size(); i++) {
      parts.add("[" + i + "] " + shape(cellTexts.get(i)));
    }
    return String.join(" | ", parts);
  }
}

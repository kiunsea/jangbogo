package com.jiniebox.jangbogo.svc.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 기간 조회형 수집기가 <b>어느 구간을 조회할지</b> 정하는 정책 (하나로 온라인/오프라인 분리).
 *
 * <h2>왜 필요한가</h2>
 *
 * <p>하나로마트의 온라인·오프라인 조회 화면은 둘 다 <b>기간을 지정해야</b> 목록을 준다. 기본 기간은 최근 며칠이라, 그 사이에 구매가 없으면 화면은 "조회된 자료가
 * 없습니다" 를 준다. 실측에서 정확히 그 화면을 봤다 — <b>그것은 수집 실패가 아니라 사이트의 정상 동작이다.</b>
 *
 * <p>따라서 올바른 동작은 기본 기간을 그대로 쓰는 것이 아니라, <b>이미 저장한 마지막 구매일부터 오늘까지</b>를 조회해 아직 저장하지 않은 것을 찾는 것이다.
 *
 * <h2>기준일을 저장하지 않고 유도하는 이유</h2>
 *
 * <p>"마지막으로 어디까지 수집했다" 를 따로 저장하면 그 값이 실제 데이터와 어긋날 수 있다. 저장에 실패했는데 기준일만 앞서 나가면 그 구간은 <b>영영 조회되지
 * 않는다</b> — 다음 회차의 시작점이 더 뒤로 가기 때문에 되돌아오지도 않는다. 그래서 기준일은 {@code jbg_order} 에 <b>실제로 들어 있는</b> 최대
 * 구매일에서 유도한다. 저장이 실패하면 기준일도 자동으로 뒤로 남아 다음 회차가 다시 가져온다.
 *
 * <p>그 최대값은 반드시 <b>수집기별</b>로 구해야 한다. 한 몰에 수집기가 둘인데 몰 단위로 구하면, 온라인 주문이 최근이라는 이유로 오프라인 조회 시작점이 밀려
 * <b>그 사이의 오프라인 거래를 영구히 놓친다.</b>
 *
 * <h2>유도만으로는 부족한 자리 — 부분 저장 실패</h2>
 *
 * <p>위 자기교정에는 <b>조건이 하나 숨어 있다</b>: 실패한 주문이 그 회차에서 <b>가장 늦은 날짜</b>여야 한다는 것. {@code
 * MallOrderUpdaterRunner} 는 주문 하나가 깨지면 그것만 롤백하고 루프를 계속하므로, <b>같은 회차의 더 늦은 주문이 커밋되면</b> {@code
 * MAX(date_time)} 이 실패한 날짜를 지나간다. 그러면 다음 회차의 시작일이 그 뒤가 되어 실패한 구간은 <b>영영 조회되지 않는다</b> — 유도가 막으려던 바로
 * 그 실패가 유도 안에서 일어난다.
 *
 * <p>그래서 유도값 하나로 끝내지 않고, <b>저장을 확인하지 못한 가장 이른 구매일</b>({@code jbg_collect_breaker.retry_from_date})을
 * 함께 읽어 <b>둘 중 이른 쪽</b>을 시작일로 쓴다({@link #resolve(String, String, LocalDate)}).
 *
 * <p><b>이것은 "기준일을 저장하는 것" 이 아니다.</b> 저장하는 값은 앞으로 나아가는 기준일이 아니라 <b>뒤로 당기는 바닥</b>이다. 방향이 반대라 실패 모양도
 * 반대다 — 이 값이 틀리거나 낡으면 겹쳐 가져올 뿐이고, 위 문단이 경계하는 "앞서 나가서 구간을 봉인하는" 손해는 구조적으로 생기지 않는다. 이 클래스가 일관되게 고르는
 * 방향 그대로다.
 *
 * <h2>경계를 포함하는 이유</h2>
 *
 * <p>시작일은 마지막 저장일을 <b>포함</b>한다. 같은 날 나중에 산 것이 있으면 제외 경계에서는 놓치기 때문이다. 겹치는 구간은 중복 판정이 걸러 내므로 손해가 없다 —
 * <b>덜 가져오는 쪽보다 겹쳐 가져오는 쪽이 안전하다</b>는 것이 이 클래스의 일관된 선택이다.
 *
 * <p>순수 함수라 브라우저·네트워크·DB 를 쓰지 않는다.
 *
 * @author KIUNSEA
 */
public final class CollectPeriod {

  private CollectPeriod() {}

  /**
   * 저장된 구매 내역이 하나도 없는 수집기의 첫 조회 범위 (사용자 결정 2026-08-10).
   *
   * <p>짧으면 그 이전 내역은 영영 들어오지 않고, 길면 첫 회차가 오래 걸리고 사이트 부하도 커진다. 2년은 그 사이에서 고른 값이다.
   */
  public static final int DEFAULT_LOOKBACK_YEARS = 2;

  /** {@code jbg_order.date_time} 과 같은 형식. */
  private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

  /**
   * 조회할 구간 하나.
   *
   * @param start 시작일(포함)
   * @param end 종료일(포함)
   */
  public record Window(LocalDate start, LocalDate end) {

    /** {@code yyyyMMdd}. 사이트가 다른 형식을 요구하면 {@link #start()} 를 받아 그쪽에서 다시 찍는다. */
    public String startYmd() {
      return start.format(YMD);
    }

    /** {@code yyyyMMdd}. */
    public String endYmd() {
      return end.format(YMD);
    }

    /** 구간 길이(일). 시작일과 종료일이 같으면 1 이다. */
    public long days() {
      return java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
    }
  }

  /**
   * 마지막 저장일부터 오늘까지의 구간을 정한다.
   *
   * @param lastStoredYmd {@code jbg_order} 에 있는 그 수집기의 최대 구매일({@code yyyyMMdd}). 없으면 null 또는 빈 값
   * @param today 오늘. 테스트가 고정할 수 있도록 인자로 받는다
   * @return 조회 구간
   */
  public static Window resolve(String lastStoredYmd, LocalDate today) {
    return resolve(lastStoredYmd, today, DEFAULT_LOOKBACK_YEARS);
  }

  /**
   * 마지막 저장일과 <b>재조회 바닥</b> 중 이른 쪽부터 오늘까지의 구간을 정한다.
   *
   * <p>부분 저장 실패가 남긴 구멍을 다시 조회하기 위한 형태다 (클래스 javadoc 참조). 바닥이 없으면 {@link #resolve(String,
   * LocalDate)} 와 완전히 같다.
   *
   * @param lastStoredYmd 그 수집기의 최대 구매일({@code yyyyMMdd}). 없으면 null 또는 빈 값
   * @param retryFromYmd 저장을 확인하지 못한 가장 이른 구매일({@code yyyyMMdd}). 없으면 null 또는 빈 값
   * @param today 오늘
   * @return 조회 구간
   */
  public static Window resolve(String lastStoredYmd, String retryFromYmd, LocalDate today) {
    return resolve(lastStoredYmd, retryFromYmd, today, DEFAULT_LOOKBACK_YEARS);
  }

  /**
   * 마지막 저장일과 재조회 바닥 중 이른 쪽부터 오늘까지의 구간을 정한다.
   *
   * <p><b>바닥이 있으면 저장된 것이 없어도 기본 범위로 되돌아가지 않는다.</b> 바닥은 "여기부터는 다시 봐야 한다"는 관측 사실이므로, 그것만으로 시작일이 정해진다.
   * 다만 기본 범위(2년)가 바닥보다 더 이르면 그쪽을 쓴다 — <b>언제나 이른 쪽</b>이라는 규칙 하나로 통일한다.
   *
   * @param lastStoredYmd 그 수집기의 최대 구매일({@code yyyyMMdd}). 없으면 null 또는 빈 값
   * @param retryFromYmd 저장을 확인하지 못한 가장 이른 구매일({@code yyyyMMdd}). 없으면 null 또는 빈 값
   * @param today 오늘
   * @param lookbackYears 저장된 것이 없을 때 거슬러 올라갈 햇수
   * @return 조회 구간
   */
  public static Window resolve(
      String lastStoredYmd, String retryFromYmd, LocalDate today, int lookbackYears) {

    Window derived = resolve(lastStoredYmd, today, lookbackYears);
    LocalDate floor = parseOrNull(retryFromYmd);

    if (floor == null || !floor.isBefore(derived.start())) {
      return derived;
    }
    // 바닥이 미래면 구간이 뒤집힌다. 유도값과 같은 규칙으로 오늘까지 당긴다.
    return new Window(floor.isAfter(today) ? today : floor, derived.end());
  }

  /**
   * 두 구매일 중 이른 쪽. 재조회 바닥을 회차 안에서 누적할 때 쓴다.
   *
   * <p><b>읽을 수 없는 값은 없는 것으로 본다.</b> 형식이 깨진 값을 바닥으로 삼으면 {@link #resolve} 가 그것을 무시해 결국 같은 결과가 되지만, 그
   * 판단을 두 곳에서 하면 한쪽만 고쳐질 수 있다.
   *
   * @param a 구매일 {@code yyyyMMdd}. 없거나 읽을 수 없으면 null·빈 값
   * @param b 구매일 {@code yyyyMMdd}. 없거나 읽을 수 없으면 null·빈 값
   * @return 이른 쪽. 둘 다 읽을 수 없으면 null
   */
  public static String earlier(String a, String b) {
    LocalDate left = parseOrNull(a);
    LocalDate right = parseOrNull(b);

    if (left == null) {
      return right == null ? null : right.format(YMD);
    }
    if (right == null) {
      return left.format(YMD);
    }
    return (left.isBefore(right) ? left : right).format(YMD);
  }

  /**
   * 마지막 저장일부터 오늘까지의 구간을 정한다.
   *
   * <p><b>읽을 수 없는 값은 '없음' 으로 본다.</b> 형식이 깨진 값을 만나면 조회를 건너뛰거나 예외를 던지는 대신 기본 조회 범위로 되돌아간다 — 그쪽이 겹쳐
   * 가져올 뿐 잃지 않는다.
   *
   * <p><b>미래 날짜는 오늘로 당긴다.</b> 시계 오차나 잘못 저장된 값 때문에 시작일이 종료일보다 뒤가 되면 구간이 뒤집혀 사이트가 무엇을 돌려줄지 알 수 없다.
   *
   * @param lastStoredYmd 그 수집기의 최대 구매일({@code yyyyMMdd}). 없으면 null 또는 빈 값
   * @param today 오늘
   * @param lookbackYears 저장된 것이 없을 때 거슬러 올라갈 햇수
   * @return 조회 구간
   */
  public static Window resolve(String lastStoredYmd, LocalDate today, int lookbackYears) {
    LocalDate last = parseOrNull(lastStoredYmd);

    if (last == null) {
      return new Window(today.minusYears(lookbackYears), today);
    }
    if (last.isAfter(today)) {
      return new Window(today, today);
    }
    return new Window(last, today);
  }

  /**
   * 사이트가 한 번에 조회할 수 있는 범위에 상한을 두면 구간을 쪼갠다.
   *
   * <p><b>{@code maxDays} 가 0 이하이면 쪼개지 않는다.</b> 하나로 두 사이트의 상한은 <b>아직 실측하지 않았다</b> — 모르는 값을 추측해 넣으면
   * 필요 없는 조회를 반복하거나, 반대로 상한을 넘겨 사이트가 조용히 빈 목록을 돌려줄 수 있다. 실측한 뒤에 값을 준다.
   *
   * @param window 전체 구간
   * @param maxDays 한 번에 조회할 수 있는 최대 일수. 0 이하이면 쪼개지 않는다
   * @return 앞에서부터 차례로 조회할 구간들. 항상 한 개 이상
   */
  public static List<Window> split(Window window, int maxDays) {
    if (maxDays <= 0 || window.days() <= maxDays) {
      return List.of(window);
    }

    List<Window> windows = new ArrayList<>();
    LocalDate cursor = window.start();
    while (!cursor.isAfter(window.end())) {
      LocalDate chunkEnd = cursor.plusDays(maxDays - 1L);
      if (chunkEnd.isAfter(window.end())) {
        chunkEnd = window.end();
      }
      windows.add(new Window(cursor, chunkEnd));
      cursor = chunkEnd.plusDays(1);
    }
    return windows;
  }

  /** 읽을 수 없으면 null. 예외를 밖으로 내보내지 않는다 — 판단은 호출부가 아니라 여기서 끝낸다. */
  private static LocalDate parseOrNull(String ymd) {
    if (ymd == null || ymd.isBlank()) {
      return null;
    }
    String trimmed = ymd.trim();
    if (trimmed.length() != 8) {
      return null;
    }
    try {
      return LocalDate.parse(trimmed, YMD);
    } catch (RuntimeException e) {
      return null;
    }
  }
}

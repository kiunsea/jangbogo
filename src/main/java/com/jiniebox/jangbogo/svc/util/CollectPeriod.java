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

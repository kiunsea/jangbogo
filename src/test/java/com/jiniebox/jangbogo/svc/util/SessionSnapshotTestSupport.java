package com.jiniebox.jangbogo.svc.util;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 다른 패키지의 테스트가 {@link SessionSnapshot} 을 만들 수 있게 하는 테스트 헬퍼.
 *
 * <p>{@link SessionSnapshot#of} 는 package-private 이라 {@code svc.util} 밖에서는 부를 수 없다(그 폐쇄가 설계 목적이다).
 * 프로덕션 API 를 넓히지 않으려고, 같은 패키지의 <b>테스트 소스</b>에 이 접근점을 둔다.
 */
public final class SessionSnapshotTestSupport {

  private SessionSnapshotTestSupport() {}

  /**
   * 지정한 개수만큼 쿠키를 담은 스냅샷을 만든다.
   *
   * @param count 쿠키 수
   * @return 스냅샷
   */
  public static SessionSnapshot withCookies(int count) {
    List<Map<String, Object>> cookies = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      Map<String, Object> c = new LinkedHashMap<>();
      c.put("name", "C" + i);
      c.put("value", "v" + i);
      c.put("domain", ".ssg.com");
      c.put("path", "/");
      cookies.add(c);
    }
    return SessionSnapshot.of(cookies, Instant.ofEpochMilli(1700000000000L));
  }
}

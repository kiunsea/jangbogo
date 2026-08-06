package com.jiniebox.jangbogo.ctrl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jiniebox.jangbogo.svc.SessionCaptureService;
import com.jiniebox.jangbogo.svc.SessionCaptureService.CaptureResult;
import com.jiniebox.jangbogo.svc.SessionCaptureService.Outcome;
import com.jiniebox.jangbogo.sys.SessionConstants;
import jakarta.servlet.http.HttpSession;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 세션 캡처 흐름의 진입점 (Phase 5-15).
 *
 * <p>비동기 + 폴링이다 — 사람이 브라우저에서 로그인하는 동안 요청을 붙잡지 않는다. {@code start} 로 창을 띄우고, 프런트가 {@code status} 를
 * 폴링하며, 사람이 <b>[로그인을 마쳤습니다]</b> 를 누르면 {@code capture} 가 세션을 떠서 저장한다. {@code cancel} 로 접는다.
 *
 * <p><b>세션 값은 응답에 담기지 않는다.</b> {@link SessionCaptureService.CaptureResult} 는 개수·잔여시간만 들고 있고 쿠키 값은
 * 애초에 여기까지 오지 않는다.
 *
 * <p>경로가 {@code /mall/} 로 시작하므로 {@code AuthInterceptor} 가 이미 관리자 인증을 건다. 관리자 세션의 {@code LOGIN_TIME}
 * 을 서비스에 넘겨 캡처 창 상한을 그 30분 잔여 아래로 캡한다.
 *
 * @author KIUNSEA
 */
@RestController
@RequestMapping("/mall/session")
public class SessionCaptureController {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** 프런트가 정상 흐름으로 다룰 결과들. 나머지는 막힘·실패로 본다. */
  private static final Set<Outcome> OK =
      Set.of(
          Outcome.STARTED,
          Outcome.AWAITING_LOGIN,
          Outcome.CAPTURED,
          Outcome.CANCELLED,
          Outcome.NO_ACTIVE_CAPTURE);

  private final SessionCaptureService captureService;

  @Autowired
  public SessionCaptureController(SessionCaptureService captureService) {
    this.captureService = captureService;
  }

  /** 캡처 시작 — 마스킹 브라우저를 띄우고 로그인 페이지로 보낸다. 기다리지 않는다. */
  @PostMapping("/start")
  public ObjectNode start(@RequestParam("seqMall") String seqMall, HttpSession session) {
    return toJson(captureService.start(seqMall, loginTime(session)));
  }

  /** 지금 상태 폴링. 관리자 세션 시간이 다 되면 창을 닫고 만료를 알린다. */
  @GetMapping("/status")
  public ObjectNode status(@RequestParam("seqMall") String seqMall) {
    return toJson(captureService.status(seqMall));
  }

  /** 사람이 로그인을 마쳤다 — 세션을 떠서 저장한다. */
  @PostMapping("/capture")
  public ObjectNode capture(@RequestParam("seqMall") String seqMall) {
    return toJson(captureService.capture(seqMall));
  }

  /** 진행 중인 캡처를 취소한다. */
  @PostMapping("/cancel")
  public ObjectNode cancel(@RequestParam("seqMall") String seqMall) {
    return toJson(captureService.cancel(seqMall));
  }

  /**
   * 관리자 로그인 시각을 세션에서 읽는다. 슬라이딩하지 않는 고정값이다({@code AuthInterceptor} 가 이 값으로 30분을 잰다). 없으면 지금으로 본다 —
   * 인증을 통과해 여기 온 요청이라 보통은 존재한다.
   */
  private static long loginTime(HttpSession session) {
    Object v = session.getAttribute(SessionConstants.SESSION_LOGIN_TIME_KEY);
    return v instanceof Long ? (Long) v : System.currentTimeMillis();
  }

  private static ObjectNode toJson(CaptureResult r) {
    ObjectNode node = MAPPER.createObjectNode();
    node.put("success", OK.contains(r.outcome()));
    node.put("outcome", r.outcome().name());
    node.put("message", r.outcome().message());
    if (r.cookieCount() >= 0) {
      node.put("cookieCount", r.cookieCount());
    }
    if (r.remainingMillis() >= 0) {
      node.put("remainingMillis", r.remainingMillis());
    }
    return node;
  }
}

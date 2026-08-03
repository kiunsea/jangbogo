package com.jiniebox.jangbogo.svc.util;

import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 이 프로세스가 세션 0(윈도우 서비스)에서 도는지 판별한다 (Phase 5-1).
 *
 * <h2>왜 알아야 하는가</h2>
 *
 * <p>프로필 재사용은 <b>사람이 쓰던 Chrome 프로필로 브라우저를 띄우는</b> 방식이다. 그런데 윈도우 서비스는 세션 0 에서 돌고 세션 0 에는 <b>데스크톱이
 * 없다.</b> 거기서 headed 브라우저를 띄우면 창이 그려지지 않거나, 뜨더라도 사람이 볼 수 없다.
 *
 * <p>이걸 모르면 증상이 고약해진다 — 수집이 "실패"하는 것이 아니라 <b>로그인 화면에서 멈춘 채 타임아웃</b>된다. 원인이 화면에 나오지 않으므로 로그만 보고는
 * 프로필이 잘못됐는지, 세션이 만료됐는지, 애초에 띄울 수 없는 자리인지 구분할 수 없다.
 *
 * <p>그래서 <b>먼저 판별하고, 세션 0 이면 시도하지 않고 그 사실을 기록한다.</b>
 *
 * <h2>판단 순서</h2>
 *
 * <ol>
 *   <li><b>명시적 재정의</b> — {@value #OVERRIDE_PROPERTY}. 사람이 아는 것이 가장 정확하다.
 *   <li><b>세션 번호</b> — {@code tasklist} 가 돌려주는 값. 0 이면 서비스다. 권위값이다.
 *   <li><b>휴리스틱 3종</b> — 런처가 심은 실행 모드, {@code SESSIONNAME}, 머신 계정 여부.
 * </ol>
 *
 * <p>어느 것으로도 못 정하면 {@link ExecutionContext#UNKNOWN} 이다. <b>모르는 것을 안다고 하지 않는다</b> — 호출측이 보수적으로 굴 수
 * 있게 구분해서 돌려준다.
 *
 * <p>판단 로직은 전부 순수 함수로 분리했다. 외부에 나가는 것은 {@link #detect()} 뿐이다.
 *
 * @author KIUNSEA
 */
public final class ExecutionContextDetector {

  private static final Logger logger = LogManager.getLogger(ExecutionContextDetector.class);

  /** 사람이 직접 지정하는 재정의. {@code service} / {@code interactive} 를 받는다. */
  public static final String OVERRIDE_PROPERTY = "jangbogo.execution-context";

  /**
   * 런처가 심는 실행 모드.
   *
   * <p>{@code JangbogoLauncher} 가 {@code --service} 인자를 Spring 에 넘기기 전에 걸러내므로, 그 사실을 프로퍼티로 남겨야 뒤쪽
   * 코드가 알 수 있다.
   */
  public static final String LAUNCH_MODE_PROPERTY = "jangbogo.launch.mode";

  /** 서비스 모드를 뜻하는 {@value #LAUNCH_MODE_PROPERTY} 값. */
  public static final String LAUNCH_MODE_SERVICE = "service";

  private ExecutionContextDetector() {}

  /** 판별 결과. */
  public enum ExecutionContext {
    /** 세션 0 — 데스크톱이 없다. headed 브라우저를 띄울 수 없다. */
    SERVICE_SESSION_0,
    /** 사람이 로그인한 세션 — 브라우저를 띄울 수 있다. */
    INTERACTIVE,
    /** 판별하지 못했다. */
    UNKNOWN;

    /** headed 브라우저를 띄울 수 있는 자리인지. {@code UNKNOWN} 은 보수적으로 false 다. */
    public boolean canRunHeadedBrowser() {
      return this == INTERACTIVE;
    }
  }

  /**
   * 현재 실행 컨텍스트를 판별한다.
   *
   * <p>{@code tasklist} 호출이 포함되므로 외부 프로세스를 하나 띄운다. 실패하면 휴리스틱으로 떨어진다 — 판별 때문에 기동이 막히지는 않는다.
   */
  public static ExecutionContext detect() {
    ExecutionContext result =
        decide(
            System.getProperty(OVERRIDE_PROPERTY),
            currentSessionNumber(ExecutionContextDetector::readTasklist),
            System.getProperty(LAUNCH_MODE_PROPERTY),
            System.getenv("SESSIONNAME"),
            System.getProperty("user.name"));
    logger.info("실행 컨텍스트 판별: {}", result);
    return result;
  }

  // ---------------------------------------------------------------
  // 순수 판단 로직
  // ---------------------------------------------------------------

  /**
   * 신호들을 모아 결론을 낸다.
   *
   * @param override {@value #OVERRIDE_PROPERTY} 값 (없으면 null)
   * @param sessionNumber {@code tasklist} 가 준 세션 번호 (못 읽었으면 null)
   * @param launchMode {@value #LAUNCH_MODE_PROPERTY} 값 (없으면 null)
   * @param sessionName {@code SESSIONNAME} 환경변수 (없으면 null)
   * @param userName {@code user.name} (없으면 null)
   */
  static ExecutionContext decide(
      String override,
      Integer sessionNumber,
      String launchMode,
      String sessionName,
      String userName) {

    // 1) 사람이 지정한 값이 가장 우선이다.
    ExecutionContext explicit = fromOverride(override);
    if (explicit != ExecutionContext.UNKNOWN) {
      return explicit;
    }

    // 2) 세션 번호는 권위값이다. 0 이면 서비스, 그 외면 사람이 로그인한 세션이다.
    if (sessionNumber != null) {
      return sessionNumber == 0 ? ExecutionContext.SERVICE_SESSION_0 : ExecutionContext.INTERACTIVE;
    }

    // 3) 휴리스틱. 우리가 심은 값이 가장 믿을 만하다.
    if (LAUNCH_MODE_SERVICE.equalsIgnoreCase(trim(launchMode))) {
      return ExecutionContext.SERVICE_SESSION_0;
    }
    return fromEnvironment(sessionName, userName);
  }

  /** 명시적 재정의를 읽는다. 알 수 없는 값은 재정의로 보지 않는다. */
  static ExecutionContext fromOverride(String raw) {
    String value = trim(raw);
    if (value == null) {
      return ExecutionContext.UNKNOWN;
    }
    if ("service".equalsIgnoreCase(value) || "session0".equalsIgnoreCase(value)) {
      return ExecutionContext.SERVICE_SESSION_0;
    }
    if ("interactive".equalsIgnoreCase(value) || "console".equalsIgnoreCase(value)) {
      return ExecutionContext.INTERACTIVE;
    }
    return ExecutionContext.UNKNOWN;
  }

  /**
   * 환경 신호 둘로 판정한다.
   *
   * <p>{@code SESSIONNAME} 은 세션 0 에서 {@code Services} 이거나 아예 없고, 사람이 로그인한 자리에서는 {@code Console} 또는
   * {@code RDP-Tcp#N} 이다. 머신 계정({@code NAME$})으로 도는 것은 LocalSystem 서비스의 표식이다.
   *
   * <p>두 신호가 엇갈리면 {@code UNKNOWN} 이다 — 한쪽을 임의로 우선하면 틀린 확신을 만든다.
   */
  static ExecutionContext fromEnvironment(String sessionName, String userName) {
    boolean serviceSignal = false;
    boolean interactiveSignal = false;

    String session = trim(sessionName);
    if (session != null) {
      if ("Services".equalsIgnoreCase(session)) {
        serviceSignal = true;
      } else if ("Console".equalsIgnoreCase(session) || session.startsWith("RDP-Tcp")) {
        interactiveSignal = true;
      }
    }

    String user = trim(userName);
    if (user != null && user.endsWith("$")) {
      serviceSignal = true; // 머신 계정 = LocalSystem
    }

    if (serviceSignal && !interactiveSignal) {
      return ExecutionContext.SERVICE_SESSION_0;
    }
    if (interactiveSignal && !serviceSignal) {
      return ExecutionContext.INTERACTIVE;
    }
    return ExecutionContext.UNKNOWN;
  }

  /**
   * {@code tasklist /FO CSV /NH} 출력에서 세션 번호를 뽑는다.
   *
   * <p>형식: {@code "java.exe","1234","Services","0","123,456 K"} — 4번째 칸이 세션 번호다. 숫자에 쉼표가 들어가므로 단순
   * split 은 쓸 수 없고, 따옴표로 묶인 칸을 그대로 읽어야 한다.
   *
   * @param tasklistOutput 명령 출력 (null 허용)
   * @return 세션 번호. 못 읽으면 null
   */
  static Integer parseSessionNumber(String tasklistOutput) {
    if (tasklistOutput == null || tasklistOutput.isBlank()) {
      return null;
    }
    for (String line : tasklistOutput.split("\\r?\\n")) {
      String trimmed = line.trim();
      if (trimmed.isEmpty() || !trimmed.startsWith("\"")) {
        continue; // "INFO: No tasks are running..." 같은 안내문
      }
      java.util.List<String> fields = new java.util.ArrayList<>();
      java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([^\"]*)\"").matcher(trimmed);
      while (m.find()) {
        fields.add(m.group(1));
      }
      if (fields.size() < 4) {
        continue;
      }
      try {
        return Integer.parseInt(fields.get(3).trim());
      } catch (NumberFormatException e) {
        // 이 줄은 형식이 다르다. 다음 줄을 본다.
      }
    }
    return null;
  }

  /**
   * 세션 번호를 읽는다. 어떤 실패도 밖으로 내보내지 않는다.
   *
   * @param outputSupplier {@code tasklist} 출력 공급자 (테스트가 갈아끼운다)
   * @return 세션 번호. 못 읽으면 null
   */
  static Integer currentSessionNumber(Supplier<String> outputSupplier) {
    try {
      return parseSessionNumber(outputSupplier.get());
    } catch (RuntimeException e) {
      logger.debug("세션 번호를 읽지 못했다({}). 휴리스틱으로 판별한다.", e.getClass().getSimpleName());
      return null;
    }
  }

  /** 자기 PID 로 {@code tasklist} 를 돌려 한 줄을 얻는다. 실패하면 null. */
  private static String readTasklist() {
    if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
      return null; // 윈도우 전용 명령이다
    }
    try {
      long pid = ProcessHandle.current().pid();
      Process process =
          new ProcessBuilder("tasklist", "/FI", "PID eq " + pid, "/FO", "CSV", "/NH")
              .redirectErrorStream(true)
              .start();
      String output;
      try (var in = process.getInputStream()) {
        output = new String(in.readAllBytes());
      }
      // 오래 매달리지 않는다. 판별 하나 때문에 기동이 늦어질 이유가 없다.
      if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
        process.destroyForcibly();
        return null;
      }
      return output;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    } catch (Exception e) {
      return null;
    }
  }

  private static String trim(String value) {
    if (value == null) {
      return null;
    }
    String t = value.trim();
    return t.isEmpty() ? null : t;
  }
}

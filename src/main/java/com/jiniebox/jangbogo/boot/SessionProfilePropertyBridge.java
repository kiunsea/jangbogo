package com.jiniebox.jangbogo.boot;

import com.jiniebox.jangbogo.svc.util.SessionProfilePolicy;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;

/**
 * {@code jangbogo.session-profile.*} 설정값을 System property 로 옮기는 다리.
 *
 * <h2>왜 이게 필요한가</h2>
 *
 * <p>{@link SessionProfilePolicy} 는 이 값들을 <b>{@code System.getProperty} 로만</b> 읽는다. 그 클래스를 상태도 스프링
 * 의존도 없는 순수 정적 유틸로 둔 대가다 — <b>Spring 설정(yml/properties)에 적은 값은 그 클래스에 보이지 않는다.</b>
 *
 * <p>그 결과 배포본에서는 마스터 킬스위치를 켤 방법 자체가 없었다. 개발자가 {@code -D} 를 직접 붙여 띄울 때만 동작했고, 설치본을 쓰는 쪽에는 그럴 수단이 없다.
 * 이 다리가 그 구멍을 메운다.
 *
 * <h2>왜 하필 EnvironmentPostProcessor 인가</h2>
 *
 * <p>{@code @PostConstruct} 나 {@code ApplicationRunner} 로 옮기면 <b>늦다.</b> 그 시점에는 이미 빈이 만들어지고 있고, 기동
 * 태스크가 먼저 돌아 {@link SessionProfilePolicy#isEnabled()} 를 꺼진 값으로 읽어 갈 수 있다. 한 번 꺼진 것으로 판정하고 지나간 경로는
 * 돌아오지 않는다.
 *
 * <p>{@code EnvironmentPostProcessor} 는 컨텍스트 refresh 이전, 빈이 하나도 만들어지기 전에 돈다. 그래서 여기여야 한다. 등록은
 * {@code META-INF/spring.factories} 에 한다 — Spring Boot 3.5 에서도 이 인터페이스만은 여전히 그 파일로 찾는다({@code
 * AutoConfiguration.imports} 가 아니다).
 *
 * <p>{@link #getOrder()} 가 가장 낮은 우선순위인 것도 의도다. application.yml 과 설치 폴더의 {@code config/} 를 읽어 넣는
 * {@code ConfigDataEnvironmentPostProcessor} 가 끝난 <b>뒤에</b> 돌아야 그 값들이 보인다.
 *
 * <h2>깨면 안 되는 성질</h2>
 *
 * <ul>
 *   <li><b>기본값은 꺼짐이다.</b> 설정에 값이 없으면 아무것도 심지 않는다. 값 해석({@code true} 만 켜짐, 오타는 꺼짐)도 여기서 하지 않고 문자열을
 *       그대로 옮기기만 한다 — 파싱 규칙의 단일 출처는 {@link SessionProfilePolicy} 하나다.
 *   <li><b>{@code -D} 가 이긴다.</b> 이미 값이 있으면 덮어쓰지 않는다. 사고가 났을 때 되돌리려고 붙인 명령줄 인자가 설정 파일에 밀리면, 되돌릴 수단이
 *       사라진다.
 * </ul>
 *
 * <p>이 클래스는 파일·네트워크·DB 를 쓰지 않는다.
 *
 * @author KIUNSEA
 */
public class SessionProfilePropertyBridge implements EnvironmentPostProcessor, Ordered {

  /** 옮길 키의 접두사. 이 아래는 전부 대상이다. */
  static final String PREFIX = "jangbogo.session-profile.";

  private final Log log;

  /**
   * 기동 시 스프링이 쓰는 생성자.
   *
   * <p>여기 들어오는 {@code Log} 는 스프링이 넣어 주는 <b>지연 로거</b>다. 이 시점에는 Log4j2 가 아직 초기화되지 않아, 직접 로거를 잡아 남긴 줄은
   * 로그 파일에 남지 않는다. 스위치가 켜졌다는 사실이 로그에 없으면 나중에 수집 경로가 왜 달라졌는지 추적할 방법이 없다.
   *
   * @param log 스프링이 주입하는 지연 로거
   */
  public SessionProfilePropertyBridge(Log log) {
    this.log = log;
  }

  /**
   * 인자 없는 생성자 (안전망).
   *
   * <p>스프링이 {@code Log} 를 못 넣어 주는 상황이 와도 <b>앱이 아예 안 뜨는 일</b>만은 막는다. 생성자를 하나만 두면 그런 경우 인스턴스화 실패가 그대로
   * 기동 실패가 된다 — 킬스위치를 읽으려다 앱을 죽이는 것은 앞뒤가 바뀐 일이다.
   */
  public SessionProfilePropertyBridge() {
    this(LogFactory.getLog(SessionProfilePropertyBridge.class));
  }

  /**
   * 가장 늦게 돈다.
   *
   * <p>설정 파일을 읽어 넣는 {@code ConfigDataEnvironmentPostProcessor} 보다 뒤여야 그 값들이 보인다. 앞서 돌면 설정 파일에 뭘 적어도
   * 빈손으로 끝난다.
   *
   * @return {@link Ordered#LOWEST_PRECEDENCE}
   */
  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    for (String key : bridgeableKeys(environment)) {
      bridge(environment, key);
    }
  }

  /**
   * 옮길 키 목록.
   *
   * <p>정본 키 두 개는 열거 결과와 상관없이 <b>항상</b> 시도한다. 환경변수로만 준 경우 그 속성원본은 대문자·밑줄 이름으로 열거되므로 접두사 스캔에는 걸리지 않는다
   * — 점 표기 이름을 직접 물어봐야만 스프링의 완화된 이름 매칭이 동작한다.
   *
   * <p>거기에 접두사 스캔을 더한 이유는, 나중에 이 접두사 아래 키가 늘어도 이 파일을 같이 고치지 않게 하기 위해서다. 스위치와 실제 코드가 따로 노는 상태가 이 기능이
   * 배포에서 막혔던 원인이다.
   */
  private static Set<String> bridgeableKeys(ConfigurableEnvironment environment) {
    Set<String> keys = new LinkedHashSet<>();
    keys.add(SessionProfilePolicy.ENABLED_PROPERTY);
    keys.add(SessionProfilePolicy.ROOT_PROPERTY);

    for (PropertySource<?> source : environment.getPropertySources()) {
      if (source instanceof EnumerablePropertySource<?> enumerable) {
        for (String name : enumerable.getPropertyNames()) {
          if (name.startsWith(PREFIX)) {
            keys.add(name);
          }
        }
      }
    }
    return keys;
  }

  /** 키 하나를 옮긴다. 옮기지 못하는 경우는 전부 "꺼진 채로 둔다" 로 끝난다. */
  private void bridge(ConfigurableEnvironment environment, String key) {
    if (System.getProperty(key) != null) {
      // 명령줄이 이긴다. 빈 문자열(-Dkey=)도 사람이 명시적으로 넣은 값으로 보고 건드리지 않는다 —
      // 그 경우 판정은 꺼짐으로 떨어지므로, 애매한 입력일수록 안전한 쪽으로 기운다.
      return;
    }

    String value;
    try {
      value = environment.getProperty(key);
    } catch (RuntimeException e) {
      // 설정 파일의 오타(치환되지 않는 ${...} 등) 하나가 기동 실패가 되면 안 된다. 그렇다고 조용히
      // 넘기지도 않는다 — 꺼진 채로 떴다는 사실을 남겨야 "켠 줄 알았는데 안 켜진" 상태를 알아챈다.
      log.warn("세션 프로필 설정 '" + key + "' 를 읽지 못해 건너뛴다. 이 값은 꺼진 채로 기동한다.", e);
      return;
    }

    if (value == null || value.isBlank()) {
      return;
    }

    System.setProperty(key, value.trim());
    log.info("세션 프로필 설정을 시스템 프로퍼티로 옮겼다: " + key + "=" + value.trim());
  }
}

package com.jiniebox.jangbogo.boot;

import com.jiniebox.jangbogo.util.CryptoKeyStore;
import com.jiniebox.jangbogo.util.PasswordEncryptor;
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
 * {@code jangbogo.crypto.*} 설정값을 System property 로 옮기는 다리.
 *
 * <h2>왜 이게 필요한가</h2>
 *
 * <p>{@link PasswordEncryptor} 는 이 값들을 <b>{@code System.getProperty} 와 환경변수로만</b> 읽는다. 그런데 배포본에서 그
 * 둘에 값을 넣을 자리가 마땅치 않다.
 *
 * <ul>
 *   <li>서비스 등록 파일({@code jangbogo-service.xml})은 <b>{@code -D} 를 적지 말라고 명시</b>한다 — {@code
 *       install.bat} 이 설치할 때마다 {@code /service/arguments} 노드를 통째로 새로 써서 적어 둔 {@code -D} 가 그 순간
 *       사라진다.
 *   <li>그래서 그 파일은 설정을 {@code config\application.yml} 에 적으라고 안내하는데, 정작 그 값이 {@link
 *       PasswordEncryptor} 에는 보이지 않았다.
 * </ul>
 *
 * <p>즉 <b>기본키 경고가 안내하는 해법을 배포본에서 실행할 방법이 없었다.</b> {@link SessionProfilePropertyBridge} 가 메운 것과 같은
 * 모양의 구멍이고, 같은 방법으로 메운다.
 *
 * <h2>세션 프로필 다리와 다른 점 하나</h2>
 *
 * <p><b>값을 로그에 남기지 않는다.</b> 그쪽은 {@code key=value} 를 통째로 찍는데, 여기서 같은 짓을 하면 <b>암호화 키가 로그 파일에 평문으로
 * 남는다.</b> 키를 소스에서 빼내려고 만든 장치가 키를 로그에 흘리면 아무 것도 나아지지 않는다. 옮겼다는 사실만 남긴다.
 *
 * <p>등록은 {@code META-INF/spring.factories} 에 한다. 그 줄이 사라지면 이 다리는 그냥 실행되지 않는다 — 컴파일도 테스트도 통과하는데
 * 배포본에서만 설정이 먹지 않는 상태가 된다.
 *
 * <p>이 클래스는 파일·네트워크·DB 를 쓰지 않는다.
 */
public class CryptoPropertyBridge implements EnvironmentPostProcessor, Ordered {

  /** 옮길 키의 접두사. 이 아래는 전부 대상이다. */
  static final String PREFIX = "jangbogo.crypto.";

  private final Log log;

  /** 기동 시 스프링이 쓰는 생성자. 여기 들어오는 {@code Log} 는 스프링이 넣어 주는 지연 로거다. */
  public CryptoPropertyBridge(Log log) {
    this.log = log;
  }

  /** 인자 없는 생성자 (안전망). 로거를 못 받는다고 앱이 아예 안 뜨는 일만은 막는다. */
  public CryptoPropertyBridge() {
    this(LogFactory.getLog(CryptoPropertyBridge.class));
  }

  /** 설정 파일을 읽어 넣는 {@code ConfigDataEnvironmentPostProcessor} 보다 뒤여야 그 값들이 보인다. */
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
   * <p>정본 키 셋은 열거 결과와 상관없이 <b>항상</b> 시도한다. 환경변수로만 준 경우 그 속성원본은 대문자·밑줄 이름으로 열거되므로 접두사 스캔에는 걸리지 않는다 —
   * 점 표기 이름을 직접 물어봐야만 스프링의 완화된 이름 매칭이 동작한다.
   */
  private static Set<String> bridgeableKeys(ConfigurableEnvironment environment) {
    Set<String> keys = new LinkedHashSet<>();
    keys.add(PasswordEncryptor.KEY_PROPERTY);
    keys.add(PasswordEncryptor.IV_PROPERTY);
    keys.add(CryptoKeyStore.KEY_FILE_PROPERTY);

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

  /** 키 하나를 옮긴다. 옮기지 못하는 경우는 전부 "설정하지 않은 것으로 둔다" 로 끝난다. */
  private void bridge(ConfigurableEnvironment environment, String key) {
    if (System.getProperty(key) != null) {
      // 명령줄이 이긴다. 사고가 났을 때 되돌리려고 붙인 인자가 설정 파일에 밀리면 되돌릴 수단이 사라진다.
      return;
    }

    String value;
    try {
      value = environment.getProperty(key);
    } catch (RuntimeException e) {
      // 설정 파일의 오타 하나가 기동 실패가 되면 안 된다. 그렇다고 조용히 넘기지도 않는다.
      log.warn("암호화 설정 '" + key + "' 를 읽지 못해 건너뛴다. 이 값은 지정되지 않은 것으로 기동한다.", e);
      return;
    }

    if (value == null || value.isBlank()) {
      return;
    }

    System.setProperty(key, value.trim());
    // 값은 절대 찍지 않는다. 이 다리가 옮기는 것이 바로 암호화 키다.
    log.info("암호화 설정을 시스템 프로퍼티로 옮겼다: " + key + " (값은 로그에 남기지 않는다)");
  }
}

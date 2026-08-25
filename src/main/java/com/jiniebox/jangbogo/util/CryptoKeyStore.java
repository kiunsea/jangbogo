package com.jiniebox.jangbogo.util;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.Properties;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 이 설치본에만 있는 암호화 키·IV 를 보관한다.
 *
 * <h2>왜 필요한가</h2>
 *
 * <p>{@link PasswordEncryptor} 의 기본 키·IV 는 <b>공개 저장소의 소스에 그대로 들어 있다.</b> 그래서 기본값을 쓰는 설치본의 {@code
 * jbg_export_config.ftp_pass} 는 암호문이라는 형태만 갖췄을 뿐 아무도 막지 못한다.
 *
 * <p>{@code -Djangbogo.crypto.key=} 로 덮어쓰는 길은 예전부터 열려 있었지만 <b>지원되는 배포 경로로는 닿지 않았다.</b> 서비스 등록 파일은
 * {@code -D} 를 적지 말라고 명시한다({@code install.bat} 이 설치할 때마다 {@code /service/arguments} 를 통째로 새로 쓴다),
 * 그래서 설정은 {@code config\application.yml} 에 적으라고 안내하는데 {@link PasswordEncryptor} 는 시스템 프로퍼티와 환경변수만
 * 본다. 즉 경고가 안내하는 해법을 배포본에서 실행할 방법이 없었다.
 *
 * <p>그 구멍을 두 갈래로 막는다 — {@code CryptoPropertyBridge} 가 설정 파일의 값을 시스템 프로퍼티로 옮기고, 아무것도 지정하지 않은 설치본은 이
 * 클래스가 <b>스스로 키를 발급한다.</b> 운영자가 문서를 읽지 않아도 공개된 값이 쓰이지는 않게 하는 것이 목적이다.
 *
 * <h2>이것이 막는 것과 막지 못하는 것</h2>
 *
 * <p>키 파일은 DB 와 같은 장비에 있다. 그러므로 <b>파일시스템을 통째로 가져가는 공격은 막지 못한다.</b> 막는 것은 두 가지다 — 소스 공개(지금의 실제 노출)와,
 * DB 파일만 새어 나가는 경우(백업본, 복사본). 그 이상을 주장하지 않는다.
 *
 * <h2>깨면 안 되는 성질</h2>
 *
 * <ul>
 *   <li><b>읽기와 발급을 나눈다.</b> 여기서는 <b>읽기만</b> 한다. 발급은 기동 태스크가 명시적으로 부른다 — 키를 만드는 순간은 로그에 남아야 하고, 암호화가
 *       필요할 때마다 아무 스레드가 파일을 만들게 두면 그 순간을 아무도 모른다.
 *   <li><b>이미 있는 키는 절대 덮어쓰지 않는다.</b> 덮어쓰면 그 설치본의 저장된 비밀번호를 영원히 읽지 못한다.
 * </ul>
 */
public final class CryptoKeyStore {

  private static final Logger logger = LoggerFactory.getLogger(CryptoKeyStore.class);

  /** 키 파일 위치 재정의. 테스트와, 설치 폴더 밖에 두려는 운영자를 위한 것이다. */
  public static final String KEY_FILE_PROPERTY = "jangbogo.crypto.key-file";

  /**
   * 기본 키 파일 위치.
   *
   * <p>{@code config/} 인 이유는 그 폴더가 이미 이 설치본만의 비밀을 담는 자리이기 때문이다({@code admin.properties}, {@code
   * mall_account.yml}). 배포 패키지에 들어 있지 않으므로 새 판을 그 위에 풀어도 살아남는다.
   */
  static final String DEFAULT_KEY_FILE = "config/jangbogo-crypto.key";

  private static final String KEY_ENTRY = "key";
  private static final String IV_ENTRY = "iv";

  /**
   * 키·IV 의 길이.
   *
   * <p>{@link PasswordEncryptor} 는 원본 문자열의 UTF-8 바이트를 32(키)·16(IV)바이트 버퍼에 <b>앞에서부터 복사</b>하고 모자란 자리는
   * 0 으로 남긴다. 그래서 아래 알파벳(ASCII 1바이트)으로 정확히 그 길이를 채워야 버퍼가 전부 난수가 된다. 64자 알파벳이므로 문자당 6비트 — 키 192비트,
   * IV 96비트다.
   */
  private static final int KEY_CHARS = 32;

  private static final int IV_CHARS = 16;

  /** URL 안전 Base64 알파벳. 설정 파일에 그대로 적어도 이스케이프가 필요 없다. */
  private static final String ALPHABET =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

  private CryptoKeyStore() {}

  /** 키 파일 경로. */
  public static File file() {
    String override = PasswordEncryptor.overrideOf(KEY_FILE_PROPERTY);
    return new File(override != null ? override : DEFAULT_KEY_FILE);
  }

  /** 발급된 키. 파일이 없거나 읽을 수 없으면 {@code null}. */
  static String key() {
    return entry(KEY_ENTRY);
  }

  /** 발급된 IV. 파일이 없거나 읽을 수 없으면 {@code null}. */
  static String iv() {
    return entry(IV_ENTRY);
  }

  private static String entry(String name) {
    File keyFile = file();
    if (!keyFile.isFile()) {
      return null;
    }
    try (Reader reader = Files.newBufferedReader(keyFile.toPath(), StandardCharsets.UTF_8)) {
      Properties properties = new Properties();
      properties.load(reader);
      String value = properties.getProperty(name);
      return (value == null || value.isBlank()) ? null : value.trim();
    } catch (IOException | RuntimeException e) {
      // 읽지 못하면 기본값으로 떨어진다. 조용히 넘기면 "왜 다시 경고가 뜨지" 를 설명할 수 없다.
      logger.error(
          "암호화 키 파일을 읽지 못했습니다: {} - {} (기본값으로 되돌아갑니다)", keyFile.getAbsolutePath(), e.getMessage());
      return null;
    }
  }

  /** 키 파일이 이미 있는가. */
  public static boolean exists() {
    return file().isFile();
  }

  /**
   * 키 파일이 없으면 새로 발급한다. 이미 있으면 <b>손대지 않는다.</b>
   *
   * @return 이번 호출이 새로 발급했으면 true
   */
  public static synchronized boolean provision() {
    File keyFile = file();
    if (keyFile.isFile()) {
      return false;
    }

    try {
      Path parent = keyFile.toPath().toAbsolutePath().getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }

      // Properties.store 로 머리말을 쓰지 않는다. 그 메서드는 주석의 ASCII 밖 문자를 전부
      // 유니코드 이스케이프(역슬래시 u 표기)로 바꿔서, 정작 사람이 읽어야 할 경고가
      // 읽을 수 없는 줄이 된다. 형식은 그대로 properties 라 읽는 쪽은 Properties.load 를 계속 쓴다.
      String content =
          String.join(
              System.lineSeparator(),
              "# 이 설치본 전용 암호화 키입니다. 저장된 FTP 비밀번호가 이 값으로 잠겨 있습니다.",
              "#",
              "# 지우거나 바꾸면 저장된 비밀번호를 다시 읽지 못합니다 - 관리 화면에서 다시 입력해야 합니다.",
              "# 이 파일은 백업 대상입니다. 저장소에 커밋하지 마십시오.",
              "",
              KEY_ENTRY + "=" + randomToken(KEY_CHARS),
              IV_ENTRY + "=" + randomToken(IV_CHARS),
              "");

      try (Writer writer = Files.newBufferedWriter(keyFile.toPath(), StandardCharsets.UTF_8)) {
        writer.write(content);
      }

      restrictPermissions(keyFile.toPath());
      logger.info("이 설치본 전용 암호화 키를 발급했습니다: {}", keyFile.getAbsolutePath());
      return true;

    } catch (IOException | RuntimeException e) {
      logger.error(
          "암호화 키를 발급하지 못했습니다: {} - {} (공개된 기본값을 그대로 씁니다)",
          keyFile.getAbsolutePath(),
          e.getMessage());
      return false;
    }
  }

  /**
   * 가능한 환경에서 소유자만 읽도록 좁힌다.
   *
   * <p>Windows 에는 POSIX 권한이 없어 조용히 넘어간다. 그 경우 이 파일은 {@code config/} 의 ACL 을 따르는데, 같은 폴더의 {@code
   * admin.properties}·{@code mall_account.yml} 과 같은 조건이다.
   */
  private static void restrictPermissions(Path path) {
    try {
      Set<PosixFilePermission> ownerOnly =
          EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
      Files.setPosixFilePermissions(path, ownerOnly);
    } catch (IOException | RuntimeException ignored) {
      // POSIX 가 아니거나 지원하지 않는 파일시스템이다. 권한을 못 좁힌다고 발급을 되돌릴 이유는 없다.
    }
  }

  private static String randomToken(int length) {
    SecureRandom random = new SecureRandom();
    StringBuilder token = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      token.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
    }
    return token.toString();
  }
}

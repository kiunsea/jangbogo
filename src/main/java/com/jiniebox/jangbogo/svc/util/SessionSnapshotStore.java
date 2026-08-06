package com.jiniebox.jangbogo.svc.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiniebox.jangbogo.dao.JbgMallDataAccessObject;
import com.jiniebox.jangbogo.util.StringEncrypter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;

/**
 * 세션 스냅샷을 암호화해 저장하고 되읽는다 (Phase 5-16).
 *
 * <h2>키를 계정 키와 나눠 쓰는 이유</h2>
 *
 * <p>몰 계정용 {@code encrypt_key}/{@code encrypt_iv} 를 재사용하지 않는다. 계정 재연결이 성공할 때마다 그 키는 새로 생성되어 덮어써지므로,
 * 그 키로 암호화한 스냅샷은 다음 복호화에서 {@code BadPadding} 으로 죽고 로그에는 '계정 복호화 실패' 로만 남는다. 그래서 스냅샷은 <b>저장할 때마다 자기
 * 키/IV 를 새로 만들고</b>({@link StringEncrypter#generateKey}·{@link StringEncrypter#generateIv}), 키는
 * {@code jbg_mall} 의 스냅샷 전용 컬럼에, 암호문은 프로필 루트 아래 파일에 <b>나눠</b> 둔다.
 *
 * <h2>직렬화를 base64 로 한 번 감싸는 이유</h2>
 *
 * <p>{@link StringEncrypter} 가 charset 을 명시하지 않는다({@code input.getBytes()}·{@code new
 * String(bytes)}). 쿠키 값에 비-ASCII 가 섞이면 왕복이 플랫폼 기본 charset 에 의존해, <b>실패가 복호화 시점에 나서 캡처는 성공한 것처럼
 * 보인다.</b> 그래서 JSON 을 UTF-8 바이트로 만든 뒤 <b>base64 로 ASCII 에 고정</b>해서 암호화한다 — 암호기에 들어가고 나오는 것은 언제나
 * ASCII 다.
 *
 * <h2>값을 내지 않는다</h2>
 *
 * <p>쿠키 값과 키는 살아 있는 인증 자산이다. 이 클래스는 개수·시각·실패 클래스명만 로그로 남기고 <b>값 자체는 로그·예외 메시지·toString 어디에도 싣지
 * 않는다.</b> {@link SessionSnapshot#cookies()} 가 package-private 이라 이 저장 코드는 같은 패키지에 둔다.
 *
 * @author KIUNSEA
 */
public class SessionSnapshotStore {

  private static final Logger logger = LogManager.getLogger(SessionSnapshotStore.class);

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final TypeReference<List<Map<String, Object>>> COOKIE_LIST =
      new TypeReference<List<Map<String, Object>>>() {};

  /** 암호문 파일이 놓이는 프로필 루트 하위 폴더. 프로필 작업 디렉터리와 섞이지 않게 나눈다. */
  static final String SNAPSHOT_SUBDIR = "snapshots";

  /** 암호문 파일 확장자. */
  static final String SNAPSHOT_SUFFIX = ".session";

  /**
   * 스냅샷을 암호화해 저장한다.
   *
   * <h3>왜 임시 파일 → 키 커밋 → 원자적 교체인가</h3>
   *
   * <p>이 몰에 <b>이미 유효한 스냅샷이 있는 상태에서 다시 캡처</b>하는 경우가 핵심이다. 예전 순서(암호문 파일을 먼저 덮어쓰고 키를 나중에 쓰기)에서는, 키
   * UPDATE 가 일시적으로 실패하면(WAL 에서 스케줄러가 {@code jbg_mall} 을 쓰는 중 SQLITE_BUSY, 디스크 오류 등) DB 는 옛 키를 가리키는데
   * 파일은 새 암호문이 되어 <b>둘이 어긋나 이전의 멀쩡한 세션이 영구히 복호화 불능</b>이 된다. 그런데 호출부에는 {@code SAVE_FAILED} 로만 돌아가
   * '아무것도 잃지 않았다'고 오해하게 된다.
   *
   * <p>그래서 새 암호문은 <b>임시 파일</b>에 먼저 쓰고(기존 파일은 손대지 않는다), 키를 먼저 커밋한 뒤, 그때서야 임시 파일을 제자리로 <b>원자적으로</b>
   * 옮긴다. 키 커밋이 실패하면 임시 파일을 지우고 던진다 — 기존 파일·기존 키가 그대로라 <b>이전 세션이 살아 있다.</b>
   *
   * @param seqMall 쇼핑몰 시퀀스
   * @param snapshot 저장할 스냅샷. 비어 있으면 저장하지 않는다
   * @throws IllegalArgumentException 스냅샷이 비어 있으면
   * @throws Exception 직렬화·암호화·저장 오류
   */
  public void save(String seqMall, SessionSnapshot snapshot) throws Exception {
    if (snapshot == null || snapshot.isEmpty()) {
      // 빈 스냅샷을 저장하면 '세션이 있다'는 키만 남고 열면 아무것도 없다. 그건 캡처 실패지 저장 대상이 아니다.
      throw new IllegalArgumentException("빈 스냅샷은 저장하지 않는다.");
    }

    String base64Plain = serializeToBase64(snapshot);
    Encrypted enc = encrypt(base64Plain);

    Path file = snapshotFile(seqMall);
    Files.createDirectories(file.getParent());
    Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
    // 새 암호문을 임시 파일에 먼저 쓴다 — 기존 파일은 아직 건드리지 않는다. base64url(ASCII)이라 US-ASCII 로 정확히 왕복한다.
    Files.writeString(tmp, enc.cipher, StandardCharsets.US_ASCII);

    try {
      // 키를 먼저 커밋한다. 여기서 실패하면 기존 파일·기존 키가 그대로라 이전 세션이 살아 있다.
      persistKeys(seqMall, enc.key, enc.iv, snapshot.capturedAt().toEpochMilli());
    } catch (Exception e) {
      deleteQuietly(tmp);
      throw e;
    }

    // 키가 커밋된 뒤에만 암호문을 제자리로 옮긴다.
    moveIntoPlace(tmp, file);

    logger.info("세션 스냅샷 저장 완료 — 쿠키 {}개 (값은 기록하지 않는다)", snapshot.size());
  }

  /**
   * 키/IV 와 캡처 시각을 DB 에 커밋한다.
   *
   * <p>{@code protected} 인 이유는 테스트가 '키 커밋 실패' 를 주입해 이전 세션이 살아남는지 확인할 수 있게 하기 위해서다 — 기본 구현은 DAO 로
   * 쓴다.
   */
  protected void persistKeys(String seqMall, String key, String iv, long capturedAtMillis)
      throws Exception {
    new JbgMallDataAccessObject().saveSessionSnapshotKeys(seqMall, key, iv, capturedAtMillis);
  }

  private static void moveIntoPlace(Path tmp, Path file) throws IOException {
    try {
      Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException atomicUnsupported) {
      // 원자적 교체가 안 되는 파일시스템 — 교체로 대체한다(어긋나는 창이 훨씬 작다).
      Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static void deleteQuietly(Path p) {
    try {
      Files.deleteIfExists(p);
    } catch (IOException ignore) {
      // 임시 파일 잔재는 다음 저장이 덮어쓴다.
    }
  }

  /**
   * 저장된 스냅샷을 되읽는다.
   *
   * <p>부재·복호화 실패는 예외가 아니라 <b>빈 스냅샷</b>으로 돌려준다. 무인 수집이 이 값을 예외로 받으면 포괄 {@code catch} 가 계정 문제로 읽어 계정
   * 연결을 끊기 때문이다(5-19 와 같은 이유).
   *
   * @param seqMall 쇼핑몰 시퀀스
   * @return 복원된 스냅샷. 없거나 못 읽으면 {@link SessionSnapshot#empty()}
   */
  public SessionSnapshot load(String seqMall) {
    try {
      JSONObject keys = new JbgMallDataAccessObject().getSessionSnapshotKeys(seqMall);
      String key = keys == null ? null : (String) keys.get("snapshot_key");
      String iv = keys == null ? null : (String) keys.get("snapshot_iv");
      if (key == null || iv == null) {
        // '아직 캡처하지 않음' 이다. 흔한 상태라 debug 로만 남긴다 — 아래 '파손' 과 구분된다.
        logger.debug("세션 스냅샷 키가 없다 (seq={}) — 아직 캡처하지 않음.", seqMall);
        return SessionSnapshot.empty();
      }
      Path file = snapshotFile(seqMall);
      if (!Files.exists(file)) {
        logger.warn("스냅샷 키는 있으나 암호문 파일이 없다 (seq={}). 빈 스냅샷으로 본다.", seqMall);
        return SessionSnapshot.empty();
      }
      String cipher = Files.readString(file, StandardCharsets.US_ASCII);
      String base64Plain = decrypt(cipher, key, iv);
      return deserializeFromBase64(base64Plain);
    } catch (Exception e) {
      // 값이 실릴 여지를 두지 않는다 — seq(비밀 아님)와 클래스명만 남긴다. '키는 있는데 못 읽음' 은 파손 신호다.
      logger.warn("세션 스냅샷 복원 실패 (seq={}, {}). 빈 스냅샷으로 본다.", seqMall, e.getClass().getSimpleName());
      return SessionSnapshot.empty();
    }
  }

  // ── 순수 함수 (브라우저·네트워크·DB·파일을 건드리지 않는다) ─────────────────────────

  /**
   * 스냅샷을 JSON → UTF-8 → base64(ASCII) 로 직렬화한다.
   *
   * @param snapshot 직렬화할 스냅샷
   * @return base64 문자열(ASCII)
   * @throws Exception JSON 직렬화 오류
   */
  static String serializeToBase64(SessionSnapshot snapshot) throws Exception {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("capturedAt", snapshot.capturedAt().toEpochMilli());
    payload.put("cookies", snapshot.cookies());
    String json = MAPPER.writeValueAsString(payload);
    return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * base64(ASCII) → UTF-8 → JSON 을 스냅샷으로 되돌린다.
   *
   * @param base64Plain {@link #serializeToBase64} 가 만든 값
   * @return 스냅샷
   * @throws Exception JSON 파싱 오류
   */
  static SessionSnapshot deserializeFromBase64(String base64Plain) throws Exception {
    byte[] jsonBytes = Base64.getDecoder().decode(base64Plain);
    String json = new String(jsonBytes, StandardCharsets.UTF_8);
    JsonNode root = MAPPER.readTree(json);
    long capturedAt = root.path("capturedAt").asLong(0L);
    List<Map<String, Object>> cookies = MAPPER.convertValue(root.path("cookies"), COOKIE_LIST);
    return SessionSnapshot.of(cookies, Instant.ofEpochMilli(capturedAt));
  }

  /**
   * 새 키/IV 를 만들어 평문을 암호화한다.
   *
   * <p><b>매 호출마다 키가 새로 나온다.</b> 몰 계정 키를 재사용하지 않는다는 성질이 여기서 나온다.
   *
   * @param base64Plain ASCII 평문
   * @return 암호문과 그 키/IV(base64)
   * @throws Exception 암호화 오류
   */
  static Encrypted encrypt(String base64Plain) throws Exception {
    SecretKey key = StringEncrypter.generateKey(256);
    IvParameterSpec iv = StringEncrypter.generateIv();
    String cipher = StringEncrypter.encrypt(StringEncrypter.ALGORITHM, base64Plain, key, iv);
    return new Encrypted(
        cipher, StringEncrypter.encodeSecretKeyToBase64(key), StringEncrypter.encodeIvToBase64(iv));
  }

  /**
   * 키/IV 로 암호문을 복호화한다.
   *
   * @param cipher 암호문
   * @param base64Key base64 SecretKey
   * @param base64Iv base64 IV
   * @return ASCII 평문
   * @throws Exception 복호화 오류(키/IV 불일치 시 BadPadding/IllegalBlockSize 등)
   */
  static String decrypt(String cipher, String base64Key, String base64Iv) throws Exception {
    SecretKey key = StringEncrypter.decodeBase64ToSecretKey(base64Key);
    IvParameterSpec iv = StringEncrypter.decodeBase64ToIv(base64Iv);
    return StringEncrypter.decrypt(StringEncrypter.ALGORITHM, cipher, key, iv);
  }

  /**
   * 몰 하나의 암호문 파일 경로. {@code profileRoot()/snapshots/<seq>.session}.
   *
   * @param seqMall 쇼핑몰 시퀀스
   * @return 암호문 파일 경로
   * @throws IllegalArgumentException seq 가 숫자가 아니면
   */
  static Path snapshotFile(String seqMall) {
    String seq = seqMall == null ? "" : seqMall.replaceAll("[^0-9]", "");
    if (seq.isEmpty()) {
      throw new IllegalArgumentException("유효한 seq 가 아니다: " + seqMall);
    }
    return SessionProfilePolicy.profileRoot()
        .resolve(SNAPSHOT_SUBDIR)
        .resolve(seq + SNAPSHOT_SUFFIX);
  }

  /**
   * 암호문과 그 키/IV 한 묶음.
   *
   * <p><b>toString 이 값을 내지 않는다.</b> record 의 기본 toString 은 모든 필드를 찍어 키가 로그로 샐 수 있다.
   */
  static final class Encrypted {
    final String cipher;
    final String key;
    final String iv;

    Encrypted(String cipher, String key, String iv) {
      this.cipher = cipher;
      this.key = key;
      this.iv = iv;
    }

    @Override
    public String toString() {
      return "Encrypted[값 비공개]";
    }
  }
}

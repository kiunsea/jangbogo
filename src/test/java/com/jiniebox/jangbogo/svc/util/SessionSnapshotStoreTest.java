package com.jiniebox.jangbogo.svc.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jiniebox.jangbogo.dao.JbgMallDataAccessObject;
import com.jiniebox.jangbogo.dao.SchemaTestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 세션 스냅샷 직렬화·암호화·저장 검증 (Phase 5-16).
 *
 * <p>순수 함수(직렬화·암호화)는 브라우저·DB 없이 곧바로 잰다. 저장·복원 왕복은 임시 SQLite + 임시 프로필 루트로 경계까지 건넌다 — 5차 교훈(순수 함수와 배선
 * 사이의 틈)을 여기서도 막는다.
 *
 * @author KIUNSEA
 */
class SessionSnapshotStoreTest {

  private static final String DB_URL_PROPERTY = "jangbogo.localdb.url";

  /** 재사용하면 안 되는 몰 계정 키. save 가 만든 키가 이것과 같으면 회전 함정에 걸린 것이다. */
  private static final String MALL_KEY = "MALL-ENCRYPT-KEY-do-not-reuse";

  private String previousDbUrl;
  private String previousRoot;

  @BeforeEach
  void isolate(@TempDir Path tempDir) throws Exception {
    previousDbUrl = System.getProperty(DB_URL_PROPERTY);
    previousRoot = System.getProperty(SessionProfilePolicy.ROOT_PROPERTY);

    String dbUrl = "jdbc:sqlite:" + tempDir.resolve("store-test.db").toString().replace('\\', '/');
    System.setProperty(DB_URL_PROPERTY, dbUrl);
    System.setProperty(SessionProfilePolicy.ROOT_PROPERTY, tempDir.resolve("profiles").toString());

    SchemaTestSupport.remigrate();

    try (Connection c = DriverManager.getConnection(dbUrl);
        Statement s = c.createStatement()) {
      s.executeUpdate(
          "INSERT INTO jbg_mall (seq, id, name, account_status, encrypt_key)"
              + " VALUES (1, 'ssg', '테스트몰', 1, '"
              + MALL_KEY
              + "')");
    }
  }

  @AfterEach
  void restore() {
    SchemaTestSupport.reset();
    restoreProp(DB_URL_PROPERTY, previousDbUrl);
    restoreProp(SessionProfilePolicy.ROOT_PROPERTY, previousRoot);
  }

  private static void restoreProp(String key, String prev) {
    if (prev == null) {
      System.clearProperty(key);
    } else {
      System.setProperty(key, prev);
    }
  }

  @SafeVarargs
  private static SessionSnapshot snapshotWith(Map<String, Object>... cookies) {
    List<Map<String, Object>> list = new ArrayList<>();
    for (Map<String, Object> c : cookies) {
      list.add(c);
    }
    return SessionSnapshot.of(list, Instant.ofEpochMilli(1700000000000L));
  }

  private static Map<String, Object> cookie(String name, String value, String domain) {
    Map<String, Object> c = new LinkedHashMap<>();
    c.put("name", name);
    c.put("value", value);
    c.put("domain", domain);
    c.put("path", "/");
    return c;
  }

  // ── 순수: 직렬화 왕복 ──────────────────────────────────────────────────────

  @Test
  @DisplayName("직렬화 왕복이 쿠키 이름·값·시각을 보존한다")
  void serializeRoundTripPreservesCookies() throws Exception {
    SessionSnapshot in = snapshotWith(cookie("SID", "abc123", ".ssg.com"));

    SessionSnapshot out =
        SessionSnapshotStore.deserializeFromBase64(SessionSnapshotStore.serializeToBase64(in));

    assertEquals(1, out.size());
    assertTrue(out.hasCookie("SID"));
    assertEquals("abc123", out.cookies().get(0).get("value"));
    assertEquals(1700000000000L, out.capturedAt().toEpochMilli());
  }

  @Test
  @DisplayName("비-ASCII 쿠키 값도 왕복에서 깨지지 않는다 — charset 함정 방어")
  void nonAsciiCookieValueSurvivesRoundTrip() throws Exception {
    // StringEncrypter 가 charset 미명시라, base64 로 ASCII 고정하지 않으면 여기서 값이 깨진다.
    SessionSnapshot in = snapshotWith(cookie("SID", "토큰값-한글-🍪", ".ssg.com"));

    SessionSnapshot out =
        SessionSnapshotStore.deserializeFromBase64(SessionSnapshotStore.serializeToBase64(in));

    assertEquals("토큰값-한글-🍪", out.cookies().get(0).get("value"));
  }

  @Test
  @DisplayName("암호화 왕복이 비-ASCII 값을 보존한다 (StringEncrypter 를 실제로 통과)")
  void cryptoRoundTripPreservesNonAscii() throws Exception {
    SessionSnapshot in = snapshotWith(cookie("SID", "값-한글-value", ".ssg.com"));

    String base64 = SessionSnapshotStore.serializeToBase64(in);
    SessionSnapshotStore.Encrypted enc = SessionSnapshotStore.encrypt(base64);
    String decrypted = SessionSnapshotStore.decrypt(enc.cipher, enc.key, enc.iv);
    SessionSnapshot out = SessionSnapshotStore.deserializeFromBase64(decrypted);

    assertEquals("값-한글-value", out.cookies().get(0).get("value"));
  }

  // ── 순수: 암호화 ──────────────────────────────────────────────────────────

  @Test
  @DisplayName("매 암호화마다 키가 새로 나온다 — 몰 계정 키 재사용 금지의 근거")
  void everyEncryptGeneratesFreshKey() throws Exception {
    SessionSnapshotStore.Encrypted a = SessionSnapshotStore.encrypt("QUJD");
    SessionSnapshotStore.Encrypted b = SessionSnapshotStore.encrypt("QUJD");

    assertNotEquals(a.key, b.key, "두 번 암호화했는데 키가 같다 — 회전이 아니다.");
    assertNotEquals(a.iv, b.iv);
  }

  @Test
  @DisplayName("다른 키로는 원문이 복원되지 않는다 — 키 없이는 못 연다")
  void wrongKeyDoesNotRecoverPlaintext() throws Exception {
    // 실제 직렬화 크기의 평문을 쓴다. 틀린 키는 대개 BadPadding 으로 던지지만, 우연히 유효 패딩이
    // 나오면 예외 없이 쓰레기를 돌려준다 — 그래서 '던진다'가 아니라 '원문이 안 나온다'로 고정한다.
    String plain =
        SessionSnapshotStore.serializeToBase64(
            snapshotWith(cookie("SID", "sess-토큰-value", ".ssg.com")));
    SessionSnapshotStore.Encrypted a = SessionSnapshotStore.encrypt(plain);
    SessionSnapshotStore.Encrypted b = SessionSnapshotStore.encrypt(plain);

    String recovered;
    try {
      recovered = SessionSnapshotStore.decrypt(a.cipher, b.key, b.iv);
    } catch (Exception e) {
      recovered = null; // 던졌다 — 그것도 '못 연다'
    }
    assertNotEquals(plain, recovered, "다른 키로 원문이 복원됐다 — 키 분리가 무의미해진다.");
  }

  @Test
  @DisplayName("Encrypted.toString 이 값을 내지 않는다")
  void encryptedToStringHidesValues() throws Exception {
    SessionSnapshotStore.Encrypted enc = SessionSnapshotStore.encrypt("QUJD");
    String s = enc.toString();
    assertFalse(s.contains(enc.key), "toString 에 키가 실렸다.");
    assertFalse(s.contains(enc.cipher), "toString 에 암호문이 실렸다.");
  }

  // ── 경계: 저장·복원 ───────────────────────────────────────────────────────

  @Test
  @DisplayName("저장한 세션을 그대로 되읽는다 (임시 DB + 임시 프로필 루트)")
  void saveThenLoadRoundTrips() throws Exception {
    SessionSnapshotStore store = new SessionSnapshotStore();
    SessionSnapshot in =
        snapshotWith(cookie("SID", "sess-토큰", ".ssg.com"), cookie("CID", "c2", ".emart.com"));

    store.save("1", in);
    SessionSnapshot out = store.load("1");

    assertEquals(2, out.size());
    assertTrue(out.hasCookie("SID"));
    assertTrue(out.hasCookie("CID"));
    assertEquals("sess-토큰", out.cookies().get(0).get("value"));
  }

  @Test
  @DisplayName("저장이 만든 키는 몰 계정 encrypt_key 와 다르다 — 회전 함정 방어")
  void savedKeyDiffersFromMallAccountKey() throws Exception {
    new SessionSnapshotStore().save("1", snapshotWith(cookie("SID", "x", ".ssg.com")));

    JSONObject keys = new JbgMallDataAccessObject().getSessionSnapshotKeys("1");
    assertNotEquals(MALL_KEY, keys.get("snapshot_key"), "스냅샷이 몰 계정 키를 재사용했다.");
  }

  @Test
  @DisplayName("암호문 파일은 프로필 루트 아래에 놓인다")
  void ciphertextFileLivesUnderProfileRoot() throws Exception {
    new SessionSnapshotStore().save("1", snapshotWith(cookie("SID", "x", ".ssg.com")));

    Path file = SessionSnapshotStore.snapshotFile("1");
    assertTrue(Files.exists(file), "암호문 파일이 없다.");
    assertTrue(
        file.toAbsolutePath().startsWith(SessionProfilePolicy.profileRoot().toAbsolutePath()),
        "암호문 파일이 프로필 루트 밖에 있다: " + file);
  }

  @Test
  @DisplayName("빈 스냅샷은 저장하지 않는다 — 열면 아무것도 없는 '세션 있음' 을 막는다")
  void emptySnapshotIsRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new SessionSnapshotStore().save("1", SessionSnapshot.empty()));
  }

  @Test
  @DisplayName("저장한 적 없으면 빈 스냅샷으로 온다 — 부재는 예외가 아니라 값")
  void loadAbsentReturnsEmpty() {
    assertTrue(new SessionSnapshotStore().load("1").isEmpty());
  }

  @Test
  @DisplayName("키만 있고 파일이 없으면 빈 스냅샷 — 조용히 값으로 떨어진다")
  void loadWithKeyButNoFileReturnsEmpty() throws Exception {
    // 파일 없이 DAO 에만 키를 심는다.
    new JbgMallDataAccessObject().saveSessionSnapshotKeys("1", "K", "V", 1700000000000L);
    assertTrue(new SessionSnapshotStore().load("1").isEmpty());
  }

  @Test
  @DisplayName("키 커밋이 실패해도 이전 스냅샷은 살아남는다 — 덮어쓰기 전에 원자적으로 교체")
  void persistFailurePreservesPreviousSnapshot() throws Exception {
    // 1) 정상 저장으로 이전 상태를 만든다.
    new SessionSnapshotStore().save("1", snapshotWith(cookie("A", "old", ".ssg.com")));

    // 2) 키 커밋이 실패하는 저장 (WAL SQLITE_BUSY·디스크 오류의 대역).
    SessionSnapshotStore failing =
        new SessionSnapshotStore() {
          @Override
          protected void persistKeys(String seq, String k, String iv, long t) throws Exception {
            throw new RuntimeException("db busy");
          }
        };
    assertThrows(
        Exception.class, () -> failing.save("1", snapshotWith(cookie("B", "new", ".ssg.com"))));

    // 3) 이전 세션이 그대로 열린다 (B 가 아니라 A).
    SessionSnapshot loaded = new SessionSnapshotStore().load("1");
    assertTrue(loaded.hasCookie("A"), "키 커밋 실패로 이전 스냅샷이 파손됐다.");
    assertFalse(loaded.hasCookie("B"));

    // 임시 파일 잔재가 없다.
    Path file = SessionSnapshotStore.snapshotFile("1");
    assertFalse(Files.exists(file.resolveSibling(file.getFileName() + ".tmp")), "임시 파일이 남았다.");
  }

  @Test
  @DisplayName("덮어쓰기 저장은 새 세션으로 교체하고 임시 파일을 남기지 않는다")
  void overwriteReplacesAndLeavesNoTemp() throws Exception {
    SessionSnapshotStore store = new SessionSnapshotStore();
    store.save("1", snapshotWith(cookie("A", "v1", ".ssg.com")));
    store.save("1", snapshotWith(cookie("B", "v2", ".ssg.com")));

    SessionSnapshot loaded = store.load("1");
    assertTrue(loaded.hasCookie("B"));
    assertFalse(loaded.hasCookie("A"));

    Path file = SessionSnapshotStore.snapshotFile("1");
    assertFalse(Files.exists(file.resolveSibling(file.getFileName() + ".tmp")));
  }
}

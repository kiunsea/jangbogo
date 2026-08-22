package com.jiniebox.jangbogo.svc;

import com.jiniebox.jangbogo.dao.JbgExportConfigDataAccessObject;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 내보내기 경로가 이 장비의 것인지 확인하고, 다른 장비에서 옮겨온 경로면 이 장비 기준으로 되잡는다.
 *
 * <p><b>왜 필요한가.</b> {@code jbg_export_config.save_path} 는 설치 당시 장비의 절대경로다. DB 를 다른 장비로 옮기면 그 값도
 * 따라온다. 그런데 {@code ExportService} 는 저장 직전에 {@code mkdirs()} 를 부르므로, 존재하지 않는 경로를 받아도 실패하지 않고 <b>그
 * 자리에 디렉터리를 새로 만들어 버린다.</b> 그 결과 이 장비에 없는 사용자 이름으로 된 트리가 조용히 생기고, 내보낸 파일은 아무도 찾지 않는 곳에 쌓인다. 오류도 경고도
 * 나지 않는다.
 *
 * <p>이관 작업자가 이 사실을 알아채려면 파일이 어디에 생겼는지 직접 뒤져야 하는데, 그럴 이유가 없으므로 아무도 뒤지지 않는다. 그래서 기동 시에 한 번 판정한다.
 *
 * <p><b>왜 기동 시인가.</b> 첫 자동수집이 성공하는 순간 이미 디렉터리가 생긴다. 화면을 열어야 도는 자리에 두면 늦는다.
 */
@Service
public class ExportPathMigrationService {

  private static final Logger logger = LogManager.getLogger(ExportPathMigrationService.class);

  /** 내보내기 폴더 이름. 이 장비 기준 경로를 만들 때 홈 아래에 붙인다. */
  static final String EXPORT_DIR_NAME = "jangbogo_exports";

  @Autowired private ExportService exportService;

  /**
   * 저장된 경로에 대한 판정.
   *
   * <p>{@link #FOREIGN} 만 자동 교체 대상이다. {@link #MISSING} 을 교체하지 않는 것은 의도적이다 — 작업자가 일부러 아직 만들지 않은 경로를
   * 지정해 두었을 수 있고, 그 경우 {@code mkdirs()} 가 만드는 것이 옳은 동작이다. 근거 없이 되잡으면 사람이 정한 설정을 앱이 되돌리는 셈이 된다.
   */
  enum Verdict {
    /** 비어 있다. 최초 설치. */
    UNSET,
    /** 이 장비에서 쓸 수 있는 경로다. */
    OK,
    /** 없는 경로지만 이 장비 홈 아래라 만들면 그만이다. */
    MISSING,
    /** 다른 장비의 좌표다. 이 장비에서는 의미가 없다. */
    FOREIGN
  }

  /** 판정과 처리 결과. 호출부와 테스트가 함께 본다. */
  public static class Result {
    public final Verdict verdict;
    public final String oldPath;
    public final String newPath;
    public final boolean changed;
    public final String regeneratedFile;

    Result(Verdict verdict, String oldPath, String newPath, boolean changed, String regenerated) {
      this.verdict = verdict;
      this.oldPath = oldPath;
      this.newPath = newPath;
      this.changed = changed;
      this.regeneratedFile = regenerated;
    }
  }

  /**
   * 이 장비에 맞는 내보내기 경로를 만든다.
   *
   * <p>Windows 는 내문서 아래, 그 외 OS 는 홈 바로 아래에 둔다. Windows 에 "내문서" 관례가 있는 것이지 다른 OS 에 {@code
   * ~/Documents} 가 항상 있는 것은 아니다.
   *
   * <p>{@code Paths.get} 으로 잇는 것도 이유가 있다. 기존 {@code AdminController} 는 {@code userHome +
   * "\\Documents\\..."} 로 구분자를 박아 두었는데, 그러면 Windows 밖에서 경로 하나짜리 이름이 된다.
   */
  public static String canonicalExportPath() {
    String home = System.getProperty("user.home");
    if (isWindows()) {
      return Paths.get(home, "Documents", EXPORT_DIR_NAME).toString();
    }
    return Paths.get(home, EXPORT_DIR_NAME).toString();
  }

  private static boolean isWindows() {
    String os = System.getProperty("os.name");
    return os != null && os.toLowerCase().contains("win");
  }

  /**
   * 저장된 경로가 이 장비 것인지 판정한다.
   *
   * <p>다른 장비의 좌표라고 단정하는 근거는 둘뿐이다. 애매한 것은 건드리지 않는다.
   *
   * <ol>
   *   <li><b>루트가 없다.</b> {@code D:\...} 인데 이 장비에 D 드라이브가 없으면 이 경로는 이 장비에서 성립할 수 없다.
   *   <li><b>남의 홈이다.</b> 홈의 부모(예: {@code C:\Users}) 아래인데 내 홈 아래가 아니면 다른 계정의 프로필이다.
   * </ol>
   *
   * <p>둘 다 아니면서 없는 경로는 {@link Verdict#MISSING} 이다 — 만들면 그만이고, 작업자의 의도일 수 있다.
   */
  static Verdict classify(String stored, String home) {
    if (stored == null || stored.trim().isEmpty()) {
      return Verdict.UNSET;
    }

    Path p;
    try {
      p = Paths.get(stored.trim());
    } catch (Exception e) {
      // 이 장비의 파일시스템이 받아들이지 못하는 문자열이면 그 자체로 남의 좌표다.
      return Verdict.FOREIGN;
    }

    if (Files.isDirectory(p)) {
      return Verdict.OK;
    }

    Path root = p.getRoot();
    if (root != null && !Files.exists(root)) {
      return Verdict.FOREIGN;
    }

    if (home != null && !home.trim().isEmpty()) {
      Path homePath = Paths.get(home);
      Path homeParent = homePath.getParent();
      boolean underMyHome = p.startsWith(homePath);
      boolean underUsersRoot = homeParent != null && p.startsWith(homeParent);
      if (underUsersRoot && !underMyHome) {
        return Verdict.FOREIGN;
      }
    }

    return Verdict.MISSING;
  }

  /**
   * 기동 시 한 번 부른다. 이관이 감지되면 경로를 되잡고 DB 에서 파일을 다시 만든다.
   *
   * <p>예외를 밖으로 던지지 않는다. 내보내기 경로 문제로 앱이 뜨지 못하면 사람이 관리 화면에 들어가 고칠 수도 없다.
   */
  public Result migrateIfNeeded() {
    String home = System.getProperty("user.home");
    String stored = "";
    JSONObject config = null;

    try {
      JbgExportConfigDataAccessObject dao = new JbgExportConfigDataAccessObject();
      config = dao.getConfig();
      if (config != null && config.get("save_path") != null) {
        stored = String.valueOf(config.get("save_path"));
      }
    } catch (Exception e) {
      logger.warn("내보내기 설정을 읽지 못해 경로 점검을 건너뜁니다: {}", e.getMessage());
      return new Result(Verdict.OK, stored, stored, false, null);
    }

    Verdict verdict = classify(stored, home);
    String canonical = canonicalExportPath();

    switch (verdict) {
      case OK:
        return new Result(verdict, stored, stored, false, null);

      case MISSING:
        // 되잡지 않는다. mkdirs 가 만들 자리이고, 이 장비 홈 아래라 이상하지 않다.
        logger.info("내보내기 경로가 아직 없습니다 (저장 시 생성됩니다): {}", stored);
        return new Result(verdict, stored, stored, false, null);

      case UNSET:
        logger.info("내보내기 경로가 설정되지 않아 이 장비 기준으로 지정합니다: {}", canonical);
        break;

      case FOREIGN:
      default:
        warnMigrationDetected(stored, canonical);
        break;
    }

    String regenerated = null;
    try {
      new File(canonical).mkdirs();
      applyPath(config, canonical);

      if (verdict == Verdict.FOREIGN) {
        // 내보낸 파일은 SQLite 의 구매내역에서 파생된 산출물이다. 이관 작업자가 이전 장비에서
        // 파일을 들고 오지 않아도, DB 만 넘어왔으면 여기서 그대로 다시 만들 수 있다.
        regenerated = regenerate(config, canonical);
      }
    } catch (Exception e) {
      logger.error("내보내기 경로 교체 실패 - 수동으로 설정해야 합니다: {}", e.getMessage(), e);
      return new Result(verdict, stored, canonical, false, null);
    }

    return new Result(verdict, stored, canonical, true, regenerated);
  }

  /**
   * 이관 사실을 눈에 띄게 남긴다.
   *
   * <p>여러 줄 배너인 것은 의도적이다. 기동 로그 한가운데 한 줄로 적으면 다음 부팅의 수십 줄에 묻힌다. 이 메시지는 이관 작업자가 <b>딱 한 번</b> 봐야 하는
   * 것이고, 놓치면 파일이 어디 있는지 모르게 된다.
   */
  private void warnMigrationDetected(String oldPath, String newPath) {
    logger.warn("========================================================");
    logger.warn(" 다른 장비에서 옮겨온 내보내기 경로를 발견했습니다");
    logger.warn("========================================================");
    logger.warn("  이전 장비 경로 : {}", oldPath);
    logger.warn("  이 장비 경로   : {}", newPath);
    logger.warn("");
    logger.warn("  이 장비에 없는 좌표라 그대로 두면 위 이전 경로가 새로 만들어지고");
    logger.warn("  내보낸 파일이 아무도 보지 않는 곳에 쌓입니다. 경로를 바꿉니다.");
    logger.warn("");
    logger.warn("  구매내역은 DB 에 있으므로 새 경로에 다시 만듭니다.");
    logger.warn("  이전 장비의 내보낸 파일을 보관하려면 직접 복사하십시오.");
    logger.warn("========================================================");
  }

  /**
   * save_path 만 바꿔 저장한다.
   *
   * <p>{@code updateConfig} 는 전체 필드를 덮어쓴다. 그래서 나머지는 읽은 값을 그대로 되돌려 준다. {@code ftpPass} 에 null 을 넘기는
   * 것은 기존 암호문을 유지하라는 뜻이고(그 처리가 DAO 안에 있다), 반대로 {@code publicKey} 는 null 을 넘기면 빈 값으로 덮어써 <b>암호화가
   * 깨진다.</b> 그래서 공개키만은 반드시 실어 보낸다.
   */
  private void applyPath(JSONObject config, String newPath) throws Exception {
    JbgExportConfigDataAccessObject dao = new JbgExportConfigDataAccessObject();
    dao.updateConfig(
        newPath,
        str(config, "save_format", "json"),
        intOf(config, "auto_save_enabled"),
        intOf(config, "save_to_jiniebox"),
        str(config, "ftp_address", ""),
        str(config, "ftp_id", ""),
        null, // 기존 비밀번호 유지
        str(config, "public_key", ""),
        intOf(config, "ftp_encrypt_enabled"));
    logger.info("내보내기 경로를 이 장비 기준으로 바꿨습니다: {}", newPath);
  }

  /** DB 의 구매내역으로 새 경로에 파일을 다시 만든다. */
  private String regenerate(JSONObject config, String newPath) {
    try {
      String format = str(config, "save_format", "json");
      String file = exportService.exportAllOrders(newPath, format);
      logger.warn("  DB 에서 재생성 완료 : {}", file);
      return file;
    } catch (Exception e) {
      // 재생성이 실패해도 경로 교체는 유효하다. 다음 수집분부터는 올바른 자리에 쌓인다.
      logger.warn("  DB 재생성 실패 (경로 교체는 완료됨): {}", e.getMessage());
      return null;
    }
  }

  private String str(JSONObject o, String key, String fallback) {
    if (o == null || o.get(key) == null) return fallback;
    String v = String.valueOf(o.get(key));
    return v.trim().isEmpty() ? fallback : v;
  }

  private int intOf(JSONObject o, String key) {
    if (o == null || o.get(key) == null) return 0;
    try {
      return Integer.parseInt(String.valueOf(o.get(key)).trim());
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}

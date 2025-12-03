package com.jiniebox.jangbogo.svc;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 관리자 계정/비밀번호를 중앙에서 관리하고 config/admin.properties 파일과 동기화하는 서비스. */
@Service
public class AdminCredentialService {

  private static final Logger logger = LogManager.getLogger(AdminCredentialService.class);

  @Value("${admin.id}")
  private String defaultAdminId;

  @Value("${admin.pass}")
  private String defaultAdminPass;

  @Value("${admin.config.path:./config/admin.properties}")
  private String adminConfigPath;

  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  private volatile String currentAdminId;
  private volatile String currentAdminPass;

  @PostConstruct
  public void init() {
    reloadCredentials();
  }

  /** admin.properties를 다시 읽어 현재 자격을 갱신합니다. */
  public void reloadCredentials() {
    lock.writeLock().lock();
    try {
      Properties props = loadPropertiesSafely();
      currentAdminId = props.getProperty("admin.id", defaultAdminId);
      currentAdminPass = props.getProperty("admin.pass", defaultAdminPass);
      logger.info("관리자 계정 정보 로드 완료 - id: {}", currentAdminId);
    } catch (Exception e) {
      currentAdminId = defaultAdminId;
      currentAdminPass = defaultAdminPass;
      logger.warn("admin.properties를 불러오지 못하여 기본 값을 사용합니다: {}", e.getMessage());
    } finally {
      lock.writeLock().unlock();
    }
  }

  /** 현재 저장된 관리자 계정 정보를 반환합니다. */
  public AdminCredentials getCredentials() {
    lock.readLock().lock();
    try {
      return new AdminCredentials(currentAdminId, currentAdminPass);
    } finally {
      lock.readLock().unlock();
    }
  }

  /** 로그인 요청을 검증합니다. */
  public boolean matches(String adminId, String adminPass) {
    if (adminId == null || adminPass == null) {
      return false;
    }
    lock.readLock().lock();
    try {
      return Objects.equals(currentAdminId, adminId) && Objects.equals(currentAdminPass, adminPass);
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * 관리자 아이디/비밀번호를 갱신하고 파일에 즉시 저장합니다.
   *
   * @param newAdminId 새 아이디(필수)
   * @param newAdminPass 새 비밀번호(선택)
   * @param updatePassword true면 비밀번호를 newAdminPass로 덮어씀
   */
  public void updateCredentials(String newAdminId, String newAdminPass, boolean updatePassword)
      throws IOException {
    if (newAdminId == null || newAdminId.trim().isEmpty()) {
      throw new IllegalArgumentException("관리자 아이디는 비워둘 수 없습니다.");
    }

    lock.writeLock().lock();
    try {
      String trimmedId = newAdminId.trim();
      String passwordToSave =
          updatePassword ? (newAdminPass != null ? newAdminPass : "") : currentAdminPass;
      if (passwordToSave == null) {
        passwordToSave = "";
      }

      Properties props = new Properties();
      props.setProperty("admin.id", trimmedId);
      props.setProperty("admin.pass", passwordToSave);

      File configFile = getConfigFile();
      File parent = configFile.getParentFile();
      if (parent != null && !parent.exists()) {
        parent.mkdirs();
      }

      try (FileOutputStream fos = new FileOutputStream(configFile)) {
        props.store(fos, "Jangbogo admin credentials");
      }

      currentAdminId = trimmedId;
      currentAdminPass = passwordToSave;
      logger.info("관리자 계정 정보가 업데이트되었습니다 - id: {}", currentAdminId);
    } finally {
      lock.writeLock().unlock();
    }
  }

  /** admin.properties 파일 경로를 반환합니다. */
  public String getConfigFilePath() {
    return getConfigFile().getAbsolutePath();
  }

  /** 현재 자격 정보가 어디에서 로드되었는지 반환합니다. */
  public String getEffectiveSource() {
    File file = getConfigFile();
    return file.exists() ? "admin.properties" : "application.yml";
  }

  private Properties loadPropertiesSafely() throws IOException {
    Properties props = new Properties();
    File configFile = getConfigFile();
    if (configFile.exists()) {
      try (FileInputStream fis = new FileInputStream(configFile)) {
        props.load(fis);
      }
    } else {
      // config 파일이 없으면 기본값을 파일로 생성
      File parent = configFile.getParentFile();
      if (parent != null && !parent.exists()) {
        parent.mkdirs();
      }
      props.setProperty("admin.id", defaultAdminId);
      props.setProperty("admin.pass", defaultAdminPass);
      try (FileOutputStream fos = new FileOutputStream(configFile)) {
        props.store(fos, "Jangbogo admin credentials");
      }
    }
    return props;
  }

  private File getConfigFile() {
    return Path.of(adminConfigPath).toFile();
  }

  /** 관리자 자격 정보 DTO */
  public static class AdminCredentials {
    private final String adminId;
    private final String adminPass;

    public AdminCredentials(String adminId, String adminPass) {
      this.adminId = adminId;
      this.adminPass = adminPass;
    }

    public String getAdminId() {
      return adminId;
    }

    public String getAdminPass() {
      return adminPass;
    }

    public boolean hasPassword() {
      return adminPass != null && !adminPass.isEmpty();
    }
  }
}

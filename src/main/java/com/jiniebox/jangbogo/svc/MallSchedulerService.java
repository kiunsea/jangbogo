package com.jiniebox.jangbogo.svc;

import com.jiniebox.jangbogo.dao.JbgMallDataAccessObject;
import com.jiniebox.jangbogo.dto.MallAccount;
import com.jiniebox.jangbogo.util.JinieboxUtil;
import com.jiniebox.jangbogo.util.StringEncrypter;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 각 쇼핑몰별 자동수집 스케줄링 관리 서비스
 *
 * @author KIUNSEA
 */
@Service
public class MallSchedulerService {

  private static final Logger logger = LogManager.getLogger(MallSchedulerService.class);

  @Autowired private JangBoGoManager jangBoGoManager;

  @Autowired private MallAccountYmlService mallAccountYmlService;

  @Autowired private ExportService exportService;

  // 쇼핑몰별 스케줄 관리 맵 (seq -> ScheduledFuture)
  private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

  // 스케줄러 (쓰레드 풀 크기 5)
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);

  /**
   * 특정 쇼핑몰의 자동수집 스케줄링 시작/업데이트
   *
   * @param seq 쇼핑몰 시퀀스
   * @param intervalMinutes 주기 (분 단위)
   */
  public void scheduleMall(String seq, int intervalMinutes) {
    if (seq == null || seq.isEmpty()) {
      logger.warn("스케줄 등록 실패: seq가 null이거나 비어있음");
      return;
    }

    // 기존 스케줄이 있으면 취소
    cancelMall(seq);

    // 주기가 0 이하면 스케줄링하지 않음
    if (intervalMinutes <= 0) {
      logger.info("쇼핑몰 seq={} 주기={}분, 스케줄링 안함", seq, intervalMinutes);
      return;
    }

    // 새로운 스케줄 시작
    ScheduledFuture<?> future =
        scheduler.scheduleAtFixedRate(
            () -> {
              try {
                logger.info("쇼핑몰 seq={} 예약 수집 시작", seq);
                runCollectForMall(seq);
                logger.info("쇼핑몰 seq={} 예약 수집 완료", seq);
              } catch (Exception e) {
                logger.error("쇼핑몰 seq={} 예약 수집 오류: {}", seq, e.getMessage(), e);
              }
            },
            intervalMinutes, // 초기 지연 (첫 실행까지 주기만큼 대기)
            intervalMinutes, // 주기
            TimeUnit.MINUTES);

    scheduledTasks.put(seq, future);
    logger.info("쇼핑몰 seq={} 스케줄 등록 완료 (주기: {}분)", seq, intervalMinutes);
  }

  /**
   * 특정 쇼핑몰 즉시 실행 후 스케줄링 시작
   *
   * @param seq 쇼핑몰 시퀀스
   * @param intervalMinutes 주기 (분 단위)
   */
  public void scheduleMallImmediate(String seq, int intervalMinutes) {
    if (seq == null || seq.isEmpty()) {
      logger.warn("즉시 스케줄 등록 실패: seq가 null이거나 비어있음");
      return;
    }

    // 기존 스케줄이 있으면 취소
    cancelMall(seq);

    // 주기가 0 이하면 스케줄링하지 않음
    if (intervalMinutes <= 0) {
      logger.info("쇼핑몰 seq={} 주기={}분, 즉시 스케줄링 안함", seq, intervalMinutes);
      return;
    }

    // 새로운 스케줄 시작 (초기 지연 0으로 즉시 실행)
    ScheduledFuture<?> future =
        scheduler.scheduleAtFixedRate(
            () -> {
              try {
                logger.info("쇼핑몰 seq={} 예약 수집 시작", seq);
                runCollectForMall(seq);
                logger.info("쇼핑몰 seq={} 예약 수집 완료", seq);
              } catch (Exception e) {
                logger.error("쇼핑몰 seq={} 예약 수집 오류: {}", seq, e.getMessage(), e);
              }
            },
            0, // 초기 지연 0 (즉시 실행)
            intervalMinutes, // 주기
            TimeUnit.MINUTES);

    scheduledTasks.put(seq, future);
    logger.info("쇼핑몰 seq={} 즉시 스케줄 등록 완료 (주기: {}분)", seq, intervalMinutes);
  }

  /**
   * 특정 쇼핑몰의 스케줄링 취소
   *
   * @param seq 쇼핑몰 시퀀스
   */
  public void cancelMall(String seq) {
    if (seq == null || seq.isEmpty()) {
      return;
    }

    ScheduledFuture<?> future = scheduledTasks.remove(seq);
    if (future != null && !future.isCancelled()) {
      future.cancel(false);
      logger.info("쇼핑몰 seq={} 스케줄 취소 완료", seq);
    }
  }

  /**
   * 특정 쇼핑몰이 현재 스케줄링 중인지 확인
   *
   * @param seq 쇼핑몰 시퀀스
   * @return 스케줄링 중이면 true
   */
  public boolean isScheduled(String seq) {
    if (seq == null || seq.isEmpty()) {
      logger.debug("isScheduled({}): seq가 null이거나 비어있음, false 반환", seq);
      return false;
    }

    ScheduledFuture<?> future = scheduledTasks.get(seq);

    if (future == null) {
      logger.debug("isScheduled({}): 스케줄 없음, false 반환", seq);
      return false;
    }

    boolean isCancelled = future.isCancelled();
    boolean isDone = future.isDone();
    boolean isScheduled = !isCancelled && !isDone;

    logger.debug(
        "isScheduled({}): 스케줄 존재, 취소됨={}, 완료됨={}, 결과={}", seq, isCancelled, isDone, isScheduled);

    return isScheduled;
  }

  /** 모든 스케줄링 취소 */
  public void cancelAll() {
    logger.info("모든 스케줄 작업 취소 중");
    for (Map.Entry<String, ScheduledFuture<?>> entry : scheduledTasks.entrySet()) {
      ScheduledFuture<?> future = entry.getValue();
      if (future != null && !future.isCancelled()) {
        future.cancel(false);
      }
    }
    scheduledTasks.clear();
  }

  /**
   * 특정 쇼핑몰 수집 실행
   *
   * @param seq 쇼핑몰 시퀀스
   */
  private void runCollectForMall(String seq) {
    try {
      JbgMallDataAccessObject jaDao = new JbgMallDataAccessObject();
      JSONObject mall = jaDao.getMall(seq);

      if (mall == null) {
        logger.warn("쇼핑몰 seq={} DB에서 찾을 수 없음", seq);
        return;
      }

      Integer accountStatus = asInt(mall.get("account_status"));
      if (accountStatus == null || accountStatus != 1) {
        logger.debug("쇼핑몰 seq={} account_status가 1이 아님, 건너뜀", seq);
        return;
      }

      String encKeyBase64 = str(mall.get("encrypt_key"));
      String encIvBase64 = str(mall.get("encrypt_iv"));

      if (JinieboxUtil.isEmpty(encKeyBase64) || JinieboxUtil.isEmpty(encIvBase64)) {
        logger.warn("쇼핑몰 seq={} 암호화 키/IV 누락", seq);
        return;
      }

      Optional<MallAccount> accOpt = mallAccountYmlService.getAccountBySeq(seq);
      if (accOpt.isEmpty()) {
        logger.warn("쇼핑몰 seq={} mall_account.yml에 계정 정보 없음", seq);
        return;
      }

      String cipherId = str(accOpt.get().getId());
      String cipherPw = str(accOpt.get().getPass());

      if (JinieboxUtil.isEmpty(cipherId) || JinieboxUtil.isEmpty(cipherPw)) {
        logger.warn("쇼핑몰 seq={} mall_account.yml에 아이디/비밀번호 비어있음", seq);
        return;
      }

      // 복호화
      SecretKey secKey = StringEncrypter.decodeBase64ToSecretKey(encKeyBase64.trim());
      IvParameterSpec ivSpec = StringEncrypter.decodeBase64ToIv(encIvBase64.trim());

      String usrid = StringEncrypter.decrypt(StringEncrypter.ALGORITHM, cipherId, secKey, ivSpec);
      String usrpw = StringEncrypter.decrypt(StringEncrypter.ALGORITHM, cipherPw, secKey, ivSpec);

      if (usrid != null && usrid.startsWith("%")) usrid = usrid.substring(1);
      if (usrpw != null && usrpw.startsWith("%")) usrpw = usrpw.substring(1);

      if (usrid == null || usrpw == null) {
        logger.warn("쇼핑몰 seq={} 계정 복호화 실패", seq);
        return;
      }

      // 수집 실행 (동기 실행하여 신규 주문 seq 수집)
      logger.info("쇼핑몰 seq={} 구매내역 수집 시작", seq);
      List<Integer> newOrderSeqs = jangBoGoManager.updateItemsAndGetNewSeqs(seq, usrid, usrpw);

      // 파일 저장 및 FTP 업로드 처리
      if (!newOrderSeqs.isEmpty() || shouldProcessFileExport(seq)) {
        processFileExport(seq, newOrderSeqs);
      }

    } catch (Exception e) {
      logger.error("쇼핑몰 seq={} 수집 실행 중 오류: {}", seq, e.getMessage(), e);
    }
  }

  /**
   * 파일 저장/FTP 업로드가 필요한지 확인
   *
   * @param seq 쇼핑몰 시퀀스
   * @return 파일 저장/FTP 업로드 필요 여부
   */
  private boolean shouldProcessFileExport(String seq) {
    try {
      com.jiniebox.jangbogo.dao.JbgExportConfigDataAccessObject exportConfigDao =
          new com.jiniebox.jangbogo.dao.JbgExportConfigDataAccessObject();
      org.json.simple.JSONObject exportConfig = exportConfigDao.getConfig();

      Integer autoSaveEnabled =
          exportConfig.get("auto_save_enabled") != null
              ? Integer.parseInt(exportConfig.get("auto_save_enabled").toString())
              : 0;

      Integer saveToJinieboxCfg = null;
      if (exportConfig.get("save_to_jiniebox") != null) {
        saveToJinieboxCfg = Integer.parseInt(exportConfig.get("save_to_jiniebox").toString());
      } else if (exportConfig.get("save_to_ftp") != null) {
        saveToJinieboxCfg = Integer.parseInt(exportConfig.get("save_to_ftp").toString());
      }
      int saveToJinieboxVal = (saveToJinieboxCfg != null) ? saveToJinieboxCfg : 0;

      return (autoSaveEnabled == 1) || (saveToJinieboxVal == 1);
    } catch (Exception e) {
      logger.warn("파일 저장 설정 확인 중 오류: {}", e.getMessage());
      return false;
    }
  }

  /**
   * 파일 저장 및 FTP 업로드 처리
   *
   * @param seq 쇼핑몰 시퀀스
   * @param newOrderSeqs 신규 주문 seq 목록
   */
  private void processFileExport(String seq, List<Integer> newOrderSeqs) {
    try {
      com.jiniebox.jangbogo.dao.JbgExportConfigDataAccessObject exportConfigDao =
          new com.jiniebox.jangbogo.dao.JbgExportConfigDataAccessObject();
      org.json.simple.JSONObject exportConfig = exportConfigDao.getConfig();

      Integer autoSaveEnabled =
          exportConfig.get("auto_save_enabled") != null
              ? Integer.parseInt(exportConfig.get("auto_save_enabled").toString())
              : 0;

      Integer saveToJinieboxCfg = null;
      if (exportConfig.get("save_to_jiniebox") != null) {
        saveToJinieboxCfg = Integer.parseInt(exportConfig.get("save_to_jiniebox").toString());
      } else if (exportConfig.get("save_to_ftp") != null) {
        saveToJinieboxCfg = Integer.parseInt(exportConfig.get("save_to_ftp").toString());
      }
      int saveToJinieboxVal = (saveToJinieboxCfg != null) ? saveToJinieboxCfg : 0;

      boolean shouldAutoSave = (autoSaveEnabled == 1);
      boolean shouldUploadToFtp = (saveToJinieboxVal == 1);

      String savePath =
          exportConfig.get("save_path") != null ? exportConfig.get("save_path").toString() : "";
      String format =
          exportConfig.get("save_format") != null
              ? exportConfig.get("save_format").toString()
              : "json";

      if (savePath.isEmpty()) {
        if (shouldAutoSave || shouldUploadToFtp) {
          logger.warn("쇼핑몰 seq={} 파일 저장 경로가 설정되지 않아 파일 저장을 건너뜁니다.", seq);
        }
        return;
      }

      // 신규 주문이 있는 경우 파일 저장
      if (!newOrderSeqs.isEmpty()) {
        try {
          String exportedFile = exportService.exportOrdersBySeqList(savePath, format, newOrderSeqs);
          if (shouldAutoSave) {
            logger.info(
                "쇼핑몰 seq={} 스케줄 수집 후 파일 자동저장 완료: {}, 주문: {}개",
                seq,
                exportedFile,
                newOrderSeqs.size());
          }

          // FTP 업로드 처리
          if (shouldUploadToFtp) {
            processFtpUpload(seq, exportConfig, exportConfigDao, savePath, newOrderSeqs);
          }
        } catch (Exception exportEx) {
          logger.error("쇼핑몰 seq={} 파일 저장 실패: {}", seq, exportEx.getMessage(), exportEx);
        }
      } else if (shouldUploadToFtp) {
        // 신규 주문이 없어도 FTP 업로드 설정이 있으면 상태 파일 생성
        try {
          String statusFile = exportService.createEmptyStatusFile(savePath);
          logger.info("쇼핑몰 seq={} 신규 주문 없음, 상태 파일 생성: {}", seq, statusFile);
          processFtpUpload(
              seq, exportConfig, exportConfigDao, savePath, new java.util.ArrayList<>());
        } catch (Exception statusEx) {
          logger.warn("쇼핑몰 seq={} 상태 파일 생성 실패: {}", seq, statusEx.getMessage());
        }
      }
    } catch (Exception e) {
      logger.error("쇼핑몰 seq={} 파일 저장/FTP 처리 중 오류: {}", seq, e.getMessage(), e);
    }
  }

  /**
   * FTP 업로드 처리
   *
   * @param seq 쇼핑몰 시퀀스
   * @param exportConfig 내보내기 설정
   * @param exportConfigDao 내보내기 설정 DAO
   * @param savePath 저장 경로
   * @param newOrderSeqs 신규 주문 seq 목록
   */
  private void processFtpUpload(
      String seq,
      org.json.simple.JSONObject exportConfig,
      com.jiniebox.jangbogo.dao.JbgExportConfigDataAccessObject exportConfigDao,
      String savePath,
      List<Integer> newOrderSeqs) {
    try {
      String ftpReadyFile = null;
      boolean ftpReadyFileGenerated = false;

      if (!newOrderSeqs.isEmpty()) {
        ftpReadyFile = exportService.exportToJinieboxFileBySeqList(savePath, newOrderSeqs);
        ftpReadyFileGenerated = true;
        logger.info("쇼핑몰 seq={} FTP 업로드용 jiniebox JSON 생성: {}", seq, ftpReadyFile);
      } else {
        // 신규 주문이 없으면 상태 파일 사용
        ftpReadyFile = exportService.createEmptyStatusFile(savePath);
        ftpReadyFileGenerated = true;
        logger.info("쇼핑몰 seq={} FTP 업로드용 상태 파일 생성: {}", seq, ftpReadyFile);
      }

      if (ftpReadyFile != null) {
        String ftpAddress =
            exportConfig.get("ftp_address") != null
                ? exportConfig.get("ftp_address").toString()
                : "";
        String ftpId =
            exportConfig.get("ftp_id") != null ? exportConfig.get("ftp_id").toString() : "";
        String ftpPass = "";
        try {
          ftpPass = exportConfigDao.getDecryptedFtpPassword();
        } catch (Exception e) {
          logger.error("쇼핑몰 seq={} FTP 비밀번호 복호화 실패", seq, e);
        }

        int ftpEncryptEnabledVal = 1;
        if (exportConfig.get("ftp_encrypt_enabled") != null) {
          try {
            ftpEncryptEnabledVal =
                Integer.parseInt(exportConfig.get("ftp_encrypt_enabled").toString());
          } catch (NumberFormatException ignore) {
          }
        }
        boolean ftpEncryptEnabled = (ftpEncryptEnabledVal == 1);

        String publicKey =
            exportConfig.get("public_key") != null ? exportConfig.get("public_key").toString() : "";

        if (!ftpAddress.isEmpty() && !ftpId.isEmpty() && !ftpPass.isEmpty()) {
          String fileToUpload = ftpReadyFile;
          boolean fileEncrypted = false;

          try {
            if (ftpEncryptEnabled && !publicKey.isEmpty()) {
              String encryptedFilePath = ftpReadyFile + ".encrypted";
              logger.info("쇼핑몰 seq={} FTP 업로드용 파일 암호화 시작", seq);

              boolean encryptSuccess =
                  com.jiniebox.jangbogo.util.security.RsaFileEncryption.encryptFile(
                      ftpReadyFile, encryptedFilePath, publicKey);

              if (encryptSuccess) {
                fileToUpload = encryptedFilePath;
                fileEncrypted = true;
                logger.info("쇼핑몰 seq={} FTP 업로드용 암호화 완료: {}", seq, encryptedFilePath);
              } else {
                logger.warn("쇼핑몰 seq={} FTP 업로드용 파일 암호화 실패 - 평문 업로드 진행", seq);
              }
            }

            boolean uploadSuccess =
                com.jiniebox.jangbogo.util.FtpUploadUtil.uploadFile(
                    ftpAddress, ftpId, ftpPass, fileToUpload);

            if (uploadSuccess) {
              logger.info(
                  "쇼핑몰 seq={} 스케줄 수집 후 FTP 업로드 완료 - 서버: {}, 암호화: {}",
                  seq,
                  ftpAddress,
                  fileEncrypted);
            } else {
              logger.warn("쇼핑몰 seq={} 스케줄 수집 후 FTP 업로드 실패 - 서버: {}", seq, ftpAddress);
            }
          } catch (Exception ftpUploadEx) {
            logger.error("쇼핑몰 seq={} FTP 업로드 중 오류: {}", seq, ftpUploadEx.getMessage(), ftpUploadEx);
          } finally {
            // 임시 파일 삭제 (재시도 로직 포함)
            deleteTempFileSafely(fileToUpload, "암호화 임시 파일", 3);
            if (ftpReadyFileGenerated
                && ftpReadyFile != null
                && !ftpReadyFile.equals(fileToUpload)) {
              deleteTempFileSafely(ftpReadyFile, "FTP 업로드용 임시 JSON 파일", 3);
            }
          }
        } else {
          logger.warn("쇼핑몰 seq={} FTP 업로드 정보가 불완전합니다. (주소/아이디/비밀번호 확인)", seq);
          if (ftpReadyFileGenerated && ftpReadyFile != null) {
            deleteTempFileSafely(ftpReadyFile, "FTP 업로드용 임시 파일", 3);
          }
        }
      }
    } catch (Exception e) {
      logger.error("쇼핑몰 seq={} FTP 업로드 처리 중 오류: {}", seq, e.getMessage(), e);
    }
  }

  /**
   * 임시 파일 안전 삭제 (재시도 로직 포함)
   *
   * @param filePath 삭제할 파일 경로
   * @param fileDescription 파일 설명 (로깅용)
   * @param maxRetries 최대 재시도 횟수
   */
  private void deleteTempFileSafely(String filePath, String fileDescription, int maxRetries) {
    if (filePath == null || filePath.isEmpty()) {
      return;
    }

    java.io.File file = new java.io.File(filePath);
    if (!file.exists()) {
      return;
    }

    for (int attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        if (file.delete()) {
          logger.debug("{} 삭제 완료: {}", fileDescription, filePath);
          return;
        } else {
          if (attempt < maxRetries) {
            logger.debug("{} 삭제 실패 (시도 {}/{}), 재시도 중...", fileDescription, attempt, maxRetries);
            try {
              Thread.sleep(100 * attempt); // 백오프: 100ms, 200ms, 300ms
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              return;
            }
          } else {
            logger.warn("{} 삭제 실패 (최대 재시도 횟수 초과): {}", fileDescription, filePath);
          }
        }
      } catch (Exception e) {
        if (attempt < maxRetries) {
          logger.debug(
              "{} 삭제 중 오류 (시도 {}/{}): {}", fileDescription, attempt, maxRetries, e.getMessage());
          try {
            Thread.sleep(100 * attempt);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return;
          }
        } else {
          logger.warn("{} 삭제 중 오류 (최대 재시도 횟수 초과): {}", fileDescription, e.getMessage());
        }
      }
    }
  }

  /** 애플리케이션 종료 시 모든 스케줄 정리 */
  @PreDestroy
  public void shutdown() {
    logger.info("MallSchedulerService 종료 중");
    cancelAll();
    scheduler.shutdown();
    try {
      if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
        scheduler.shutdownNow();
      }
    } catch (InterruptedException e) {
      scheduler.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  // 유틸리티 메서드

  private Integer asInt(Object o) {
    if (o instanceof Number) return ((Number) o).intValue();
    try {
      return o != null ? Integer.parseInt(o.toString()) : null;
    } catch (Exception e) {
      return null;
    }
  }

  private String str(Object o) {
    return o == null ? "" : String.valueOf(o);
  }
}

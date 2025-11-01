package com.jiniebox.jangbogo.svc;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.jiniebox.jangbogo.dao.JbgMallDataAccessObject;
import com.jiniebox.jangbogo.dto.MallAccount;
import com.jiniebox.jangbogo.util.JinieboxUtil;
import com.jiniebox.jangbogo.util.StringEncrypter;

/**
 * 각 쇼핑몰별 자동수집 스케줄링 관리 서비스
 * 
 * @author KIUNSEA
 */
@Service
public class MallSchedulerService {
    
    private static final Logger logger = LogManager.getLogger(MallSchedulerService.class);
    
    @Autowired
    private JangBoGoManager jangBoGoManager;
    
    @Autowired
    private MallAccountYmlService mallAccountYmlService;
    
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
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
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
            TimeUnit.MINUTES
        );
        
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
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
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
            TimeUnit.MINUTES
        );
        
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
        
        logger.debug("isScheduled({}): 스케줄 존재, 취소됨={}, 완료됨={}, 결과={}", 
                     seq, isCancelled, isDone, isScheduled);
        
        return isScheduled;
    }
    
    /**
     * 모든 스케줄링 취소
     */
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
            
            // 수집 실행 (updateItems 내부에서 중복 실행 체크함)
            logger.info("쇼핑몰 seq={} 구매내역 수집 시작", seq);
            jangBoGoManager.updateItems(seq, usrid, usrpw);
            
        } catch (Exception e) {
            logger.error("쇼핑몰 seq={} 수집 실행 중 오류: {}", seq, e.getMessage(), e);
        }
    }
    
    /**
     * 애플리케이션 종료 시 모든 스케줄 정리
     */
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
        if (o instanceof Number) return ((Number)o).intValue();
        try { return o != null ? Integer.parseInt(o.toString()) : null; }
        catch (Exception e) { return null; }
    }
    
    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}

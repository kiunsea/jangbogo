package com.jiniebox.jangbogo.boot;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jiniebox.jangbogo.dao.JbgMallDataAccessObject;
import com.jiniebox.jangbogo.dto.JangbogoConfig;
import com.jiniebox.jangbogo.dto.MallAccount;
import com.jiniebox.jangbogo.svc.JangBoGoManager;
import com.jiniebox.jangbogo.svc.MallAccountYmlService;
import com.jiniebox.jangbogo.util.JinieboxUtil;
import com.jiniebox.jangbogo.util.StringEncrypter;

@Component
public class StartupTasks {
	private static final Logger logger = LogManager.getLogger(StartupTasks.class);

	@Autowired
	private JangbogoConfig jangbogoConfig;

	@Autowired
	private JangBoGoManager jangBoGoManager;

	@Autowired
	private MallAccountYmlService mallAccountYmlService;

	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
		try {
			long sleepMs = parseLong(getCfg("startup-update-sleep-ms", "1500"), 1500L);
			long intervalMs = parseLong(getCfg("auto-collect-interval-ms", "3600000"), 3600000L);

            if ("true".equalsIgnoreCase(getCfg("auto-update-items-on-startup", "false"))) {
				logger.info("Auto updateItems on startup is enabled");
                runAutoCollectAll(sleepMs, true);
			} else {
				logger.info("Auto updateItems on startup is disabled");
			}

			// 주기적 자동 수집 스케줄링
			scheduler.scheduleAtFixedRate(() -> {
                try {
                    logger.info("Scheduled auto-collect tick started");
                    runAutoCollectAll(sleepMs, false);
					logger.info("Scheduled auto-collect tick finished");
				} catch (Exception e) {
					logger.error("Scheduled auto-collect error", e);
				}
			}, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
		} catch (Exception e) {
			logger.error("Failed to schedule auto-collect on startup", e);
		}
	}

    // 활성화된 모든 쇼핑몰(seq) 대상으로 순차 실행
    private void runAutoCollectAll(long sleepMs, boolean isStartupPhase) throws Exception {
		JbgMallDataAccessObject jaDao = new JbgMallDataAccessObject();
		List<JSONObject> malls = jaDao.getAllMalls(true); // encrypt_key, encrypt_iv 포함
		if (malls == null || malls.isEmpty()) {
			logger.info("No malls found to process");
			return;
		}
		for (JSONObject mall : malls) {
			try {
				Integer status = asInt(mall.get("account_status"));
				if (status == null || status != 1) continue;

				String seq = String.valueOf(mall.get("seq"));
				String encKeyBase64 = str(mall.get("encrypt_key"));
				String encIvBase64  = str(mall.get("encrypt_iv"));
				if (JinieboxUtil.isEmpty(encKeyBase64) || JinieboxUtil.isEmpty(encIvBase64)) {
					logger.warn("Mall seq={} skipped: missing encrypt_key/iv", seq);
					continue;
				}

				Optional<MallAccount> accOpt = mallAccountYmlService.getAccountBySeq(seq);
				if (accOpt.isEmpty()) {
					logger.warn("Mall seq={} skipped: no account in mall_account.yml", seq);
					continue;
				}

				String cipherId = str(accOpt.get().getId());
				String cipherPw = str(accOpt.get().getPass());
				if (JinieboxUtil.isEmpty(cipherId) || JinieboxUtil.isEmpty(cipherPw)) {
					logger.warn("Mall seq={} skipped: empty id/pw in mall_account.yml", seq);
					continue;
				}

                String usrid = tryDecrypt(encKeyBase64, encIvBase64, cipherId);
                String usrpw = tryDecrypt(encKeyBase64, encIvBase64, cipherPw);
                if (usrid == null || usrpw == null) {
                    logger.warn("Mall seq={} skipped: decryption failed", seq);
                    if (isStartupPhase) {
                        try {
                            // 프로그램 시작 시 복호화 실패하면 키/IV와 YAML 계정을 삭제
                            jaDao.update(seq, 0, null, null);
                            boolean removed = mallAccountYmlService.removeAccountBySeq(seq);
                            logger.info("Startup cleanup for seq={} done (db key/iv cleared, yaml removed={})", seq, removed);
                        } catch (Exception cleanupEx) {
                            logger.warn("Startup cleanup failed for seq={}: {}", seq, cleanupEx.getMessage());
                        }
                    }
                    continue;
                }

				logger.info("Starting updateItems for mall seq={}", seq);
				jangBoGoManager.updateItems(seq, usrid, usrpw);

				if (sleepMs > 0) {
					try { Thread.sleep(sleepMs); } catch (InterruptedException ignore) {}
				}
			} catch (Exception perMallEx) {
				logger.warn("Auto-collect error for mall seq={}", mall.get("seq"), perMallEx);
			}
		}
	}

	private String tryDecrypt(String keyB64, String ivB64, String cipherText) {
		try {
			String k = keyB64 != null ? keyB64.trim() : "";
			String v = ivB64 != null ? ivB64.trim() : "";
			if (k.isEmpty() || v.isEmpty() || JinieboxUtil.isEmpty(cipherText)) return null;

			SecretKey secKey = StringEncrypter.decodeBase64ToSecretKey(k);
			IvParameterSpec ivSpec = StringEncrypter.decodeBase64ToIv(v);

			String decrypted = StringEncrypter.decrypt(StringEncrypter.ALGORITHM, cipherText, secKey, ivSpec);
			if (decrypted != null && decrypted.startsWith("%")) decrypted = decrypted.substring(1);
			return decrypted;
		} catch (Exception ex) {
			logger.debug("Decryption failed: {}", ex.getMessage());
			return null;
		}
	}

	private String getCfg(String key, String defVal) {
		try { String v = jangbogoConfig.get(key); return v != null ? v : defVal; }
		catch (Exception e) { return defVal; }
	}

	private long parseLong(String s, long defVal) {
		try { return Long.parseLong(s); } catch (Exception e) { return defVal; }
	}

	private Integer asInt(Object o) {
		if (o instanceof Number) return ((Number)o).intValue();
		try { return o != null ? Integer.parseInt(o.toString()) : null; }
		catch (Exception e) { return null; }
	}

	private String str(Object o) {
		return o == null ? "" : String.valueOf(o);
	}
}

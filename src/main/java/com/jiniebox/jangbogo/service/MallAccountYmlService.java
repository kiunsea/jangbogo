package com.jiniebox.jangbogo.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.jiniebox.jangbogo.dto.MallAccount;
import com.jiniebox.jangbogo.dto.MallAccountYml;

/**
 * mall_account.yml 파일 관리 서비스
 * 
 * - YAML 파일 읽기/쓰기
 * - 쇼핑몰 계정 정보 CRUD
 * - 자동 백업 기능
 * - 트랜잭션 안전성 보장
 */
@Service
public class MallAccountYmlService {
    
    private static final Logger logger = LogManager.getLogger(MallAccountYmlService.class);
    
    private static final String YAML_FILE_PATH = "config/mall_account.yml";
    private static final String BACKUP_DIR = "config/backup";
    private static final String EXAMPLE_FILE_PATH = "config/mall_account.yml.example";
    
    private final ObjectMapper yamlMapper;
    
    public MallAccountYmlService() {
        // YAML 매퍼 설정
        YAMLFactory yamlFactory = YAMLFactory.builder()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)  // --- 제거
                .build();
        
        this.yamlMapper = new ObjectMapper(yamlFactory);
        this.yamlMapper.findAndRegisterModules();
    }
    
    /**
     * YAML 파일 읽기
     */
    public MallAccountYml readYaml() throws IOException {
        File file = new File(YAML_FILE_PATH);
        
        // 파일이 없으면 빈 객체 반환
        if (!file.exists()) {
            logger.warn("mall_account.yml 파일이 존재하지 않습니다. 새로운 파일을 생성합니다.");
            return new MallAccountYml();
        }
        
        try {
            MallAccountYml yaml = yamlMapper.readValue(file, MallAccountYml.class);
            logger.debug("mall_account.yml 파일 읽기 성공: {} accounts", 
                        yaml.getAccountCount());
            return yaml;
        } catch (Exception e) {
            logger.error("mall_account.yml 파일 읽기 실패", e);
            throw new IOException("YAML 파일 읽기 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * YAML 파일 쓰기
     */
    public void writeYaml(MallAccountYml yaml) throws IOException {
        File file = new File(YAML_FILE_PATH);
        
        // 디렉토리 생성
        file.getParentFile().mkdirs();
        
        // 기존 파일 백업
        if (file.exists()) {
            backupFile();
        }
        
        try {
            // 임시 파일에 먼저 쓰기 (트랜잭션 안전성)
            File tempFile = new File(YAML_FILE_PATH + ".tmp");
            yamlMapper.writerWithDefaultPrettyPrinter()
                      .writeValue(tempFile, yaml);
            
            // 임시 파일을 실제 파일로 이동 (원자적 연산)
            Files.move(tempFile.toPath(), file.toPath(), 
                      StandardCopyOption.REPLACE_EXISTING,
                      StandardCopyOption.ATOMIC_MOVE);
            
            logger.info("mall_account.yml 파일 쓰기 성공: {} accounts", 
                       yaml.getAccountCount());
        } catch (Exception e) {
            logger.error("mall_account.yml 파일 쓰기 실패", e);
            throw new IOException("YAML 파일 쓰기 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 파일 백업
     */
    private void backupFile() throws IOException {
        File sourceFile = new File(YAML_FILE_PATH);
        if (!sourceFile.exists()) {
            return;
        }
        
        // 백업 디렉토리 생성
        Path backupDir = Paths.get(BACKUP_DIR);
        Files.createDirectories(backupDir);
        
        // 백업 파일명: mall_account_20251025_103000.yml
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String backupFileName = "mall_account_" + timestamp + ".yml";
        Path backupPath = backupDir.resolve(backupFileName);
        
        // 파일 복사
        Files.copy(sourceFile.toPath(), backupPath, 
                  StandardCopyOption.REPLACE_EXISTING);
        
        logger.info("파일 백업 완료: {}", backupFileName);
        
        // 오래된 백업 파일 삭제 (최근 10개만 유지)
        cleanOldBackups(10);
    }
    
    /**
     * 오래된 백업 파일 삭제
     */
    private void cleanOldBackups(int keepCount) {
        try {
            File backupDir = new File(BACKUP_DIR);
            if (!backupDir.exists() || !backupDir.isDirectory()) {
                return;
            }
            
            File[] backupFiles = backupDir.listFiles((dir, name) -> 
                name.startsWith("mall_account_") && name.endsWith(".yml"));
            
            if (backupFiles == null || backupFiles.length <= keepCount) {
                return;
            }
            
            // 파일을 수정 시간 기준으로 정렬
            java.util.Arrays.sort(backupFiles, 
                (f1, f2) -> Long.compare(f1.lastModified(), f2.lastModified()));
            
            // 오래된 파일 삭제
            int deleteCount = backupFiles.length - keepCount;
            for (int i = 0; i < deleteCount; i++) {
                if (backupFiles[i].delete()) {
                    logger.debug("오래된 백업 파일 삭제: {}", backupFiles[i].getName());
                }
            }
            
        } catch (Exception e) {
            logger.error("백업 파일 정리 중 오류 발생", e);
        }
    }
    
    // ========== 계정 관련 메서드 ==========
    
    /**
     * 모든 계정 조회
     */
    public List<MallAccount> getAllAccounts() throws IOException {
        MallAccountYml yaml = readYaml();
        return yaml.getAccounts();
    }
    
    /**
     * 특정 사이트 계정 조회
     */
    public Optional<MallAccount> getAccount(String site) throws IOException {
        MallAccountYml yaml = readYaml();
        return yaml.getAccountBySite(site);
    }
    
    /**
     * 계정 추가 또는 업데이트
     */
    public void saveAccount(MallAccount account) throws IOException {
        if (account == null || account.getSite() == null) {
            throw new IllegalArgumentException("계정 정보가 올바르지 않습니다.");
        }
        
        MallAccountYml yaml = readYaml();
        yaml.addOrUpdateAccount(account);
        writeYaml(yaml);
        
        logger.info("계정 저장 완료: {}", account.getSite());
    }
    
    /**
     * 계정 추가 또는 업데이트 (사이트명, ID, 비밀번호)
     */
    public void saveAccount(String site, String id, String pass) throws IOException {
        if (site == null || site.isEmpty()) {
            throw new IllegalArgumentException("사이트명은 필수입니다.");
        }
        
        MallAccount account = new MallAccount(site, id, pass);
        saveAccount(account);
    }
    
    /**
     * 계정 삭제
     */
    public boolean removeAccount(String site) throws IOException {
        if (site == null || site.isEmpty()) {
            throw new IllegalArgumentException("사이트명은 필수입니다.");
        }
        
        MallAccountYml yaml = readYaml();
        boolean removed = yaml.removeAccount(site);
        
        if (removed) {
            writeYaml(yaml);
            logger.info("계정 삭제 완료: {}", site);
        } else {
            logger.warn("삭제할 계정을 찾을 수 없습니다: {}", site);
        }
        
        return removed;
    }
    
    /**
     * 계정 존재 여부 확인
     */
    public boolean hasAccount(String site) throws IOException {
        MallAccountYml yaml = readYaml();
        return yaml.hasAccount(site);
    }
    
    /**
     * 등록된 모든 사이트명 조회
     */
    public List<String> getAllSites() throws IOException {
        MallAccountYml yaml = readYaml();
        return yaml.getAllSites();
    }
    
    /**
     * 계정 수 조회
     */
    public int getAccountCount() throws IOException {
        MallAccountYml yaml = readYaml();
        return yaml.getAccountCount();
    }
    
    /**
     * 파일 존재 여부 확인
     */
    public boolean fileExists() {
        return new File(YAML_FILE_PATH).exists();
    }
    
    /**
     * YAML 파일 초기화
     */
    public void initializeYaml() throws IOException {
        logger.warn("mall_account.yml 파일을 초기화합니다.");
        
        MallAccountYml yaml = new MallAccountYml();
        writeYaml(yaml);
        
        logger.info("mall_account.yml 파일 초기화 완료");
    }
    
    /**
     * 예제 파일 생성
     */
    public void createExampleFile() throws IOException {
        File exampleFile = new File(EXAMPLE_FILE_PATH);
        
        if (exampleFile.exists()) {
            logger.info("예제 파일이 이미 존재합니다: {}", EXAMPLE_FILE_PATH);
            return;
        }
        
        MallAccountYml yaml = new MallAccountYml();
        
        // 예제 데이터 추가
        yaml.addOrUpdateAccount(new MallAccount("coupang", "coupang_id", "coupang_pass"));
        yaml.addOrUpdateAccount(new MallAccount("gmarket", "gmarket_id", "gmarket_pass"));
        yaml.addOrUpdateAccount(new MallAccount("ssg", "ssg_id", "ssg_pass"));
        yaml.addOrUpdateAccount(new MallAccount("oasis", "oasis_id", "oasis_pass"));
        
        // 예제 파일로 저장
        yamlMapper.writerWithDefaultPrettyPrinter()
                  .writeValue(exampleFile, yaml);
        
        logger.info("예제 파일 생성 완료: {}", EXAMPLE_FILE_PATH);
    }
}


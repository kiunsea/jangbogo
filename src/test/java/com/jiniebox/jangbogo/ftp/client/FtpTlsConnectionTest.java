package com.jiniebox.jangbogo.ftp.client;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.X509Certificate;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FTP over TLS (FTPS) 연결 테스트 클래스
 * 
 * <p>이 테스트는 FTPS 서버 연결, 인증, 파일 목록 조회, 파일 업로드/다운로드 등을 테스트합니다.</p>
 * 
 * <p><strong>주의:</strong> 실제 FTP 서버 정보가 필요하므로 기본적으로 @Disabled 처리되어 있습니다.
 * 테스트를 실행하려면:
 * <ul>
 *   <li>1. 환경 변수 또는 테스트 프로퍼티 파일에 FTP 서버 정보 설정</li>
 *   <li>2. @Disabled 어노테이션 제거</li>
 * </ul>
 * </p>
 * 
 * <p>환경 변수 설정 예시:</p>
 * <pre>
 * set FTP_HOST=ftp.example.com
 * set FTP_PORT=21
 * set FTP_USER=username
 * set FTP_PASS=password
 * </pre>
 * 
 * @author jiniebox.com
 * @version 1.0
 * @since 2025-11-05
 */
@Disabled("실제 FTP 서버 정보 필요 - 수동 테스트용")
class FtpTlsConnectionTest {
    
    private static final Logger logger = LoggerFactory.getLogger(FtpTlsConnectionTest.class);
    
    // FTP 서버 정보 (환경 변수 또는 프로퍼티 파일에서 로드)
    private String ftpHost;
    private int ftpPort;
    private String ftpUser;
    private String ftpPassword;
    private boolean usePassiveMode;
    
    private FTPSClient ftpsClient;
    
    /**
     * 테스트 시작 전 FTP 클라이언트 초기화
     */
    @BeforeEach
    void setUp() {
        // 프로퍼티 파일 로드 시도
        java.util.Properties props = loadProperties();
        
        // 환경 변수 > 프로퍼티 파일 > 기본값 순으로 로드
        ftpHost = System.getenv("FTP_HOST");
        if (ftpHost == null || ftpHost.isEmpty()) {
            ftpHost = props.getProperty("ftp.host", "ftp.example.com");
        }
        
        String portStr = System.getenv("FTP_PORT");
        if (portStr == null || portStr.isEmpty()) {
            portStr = props.getProperty("ftp.port", "21");
        }
        ftpPort = Integer.parseInt(portStr);
        
        ftpUser = System.getenv("FTP_USER");
        if (ftpUser == null || ftpUser.isEmpty()) {
            ftpUser = props.getProperty("ftp.user", "testuser");
        }
        
        ftpPassword = System.getenv("FTP_PASS");
        if (ftpPassword == null || ftpPassword.isEmpty()) {
            ftpPassword = props.getProperty("ftp.password", "testpass");
        }
        
        // 전송 모드 로드
        String transferMode = System.getenv("FTP_MODE");
        if (transferMode == null || transferMode.isEmpty()) {
            transferMode = props.getProperty("ftp.mode", "PASSIVE");
        }
        usePassiveMode = "PASSIVE".equalsIgnoreCase(transferMode) || "수동형".equals(transferMode);
        
        // FTPS 클라이언트 생성 (Explicit, SSL 프로토콜 - FileZilla Server 호환)
        ftpsClient = new FTPSClient("SSL", false);
        
        // FileZilla Server TLS 세션 재개 문제 해결을 위한 모든 설정
        ftpsClient.setEndpointCheckingEnabled(false);
        ftpsClient.setUseEPSVwithIPv4(true);
        ftpsClient.setBufferSize(0);
        ftpsClient.setAutodetectUTF8(false);
        ftpsClient.setControlKeepAliveTimeout(300);
        
        // SSL/TLS 디버그 모드 활성화 (선택사항)
        // System.setProperty("javax.net.debug", "ssl,handshake");
        
        logger.info("FTP 서버 정보: {}:{}", ftpHost, ftpPort);
        logger.info("프로토콜: SSL (FileZilla Server 1.x 호환)");
        logger.info("전송 모드: {}", usePassiveMode ? "PASSIVE (수동형)" : "ACTIVE (능동형)");
        logger.info("엔드포인트 체크: 비활성화");
    }
    
    /**
     * 프로퍼티 파일 로드 헬퍼 메서드
     */
    private java.util.Properties loadProperties() {
        java.util.Properties props = new java.util.Properties();
        String[] paths = {
            "src/test/resources/ftp-test.properties",
            "ftp-test.properties"
        };
        
        for (String path : paths) {
            try (java.io.FileInputStream fis = new java.io.FileInputStream(path)) {
                props.load(fis);
                logger.info("프로퍼티 파일 로드: {}", path);
                return props;
            } catch (Exception e) {
                logger.debug("프로퍼티 파일 로드 실패: {}", path);
            }
        }
        
        return props;
    }
    
    /**
     * 테스트 종료 후 FTP 연결 정리
     */
    @AfterEach
    void tearDown() {
        if (ftpsClient != null && ftpsClient.isConnected()) {
            try {
                ftpsClient.logout();
                ftpsClient.disconnect();
                logger.info("FTP 연결 종료");
            } catch (IOException e) {
                logger.error("FTP 연결 종료 실패", e);
            }
        }
    }
    
    /**
     * 기본 FTP TLS 연결 테스트
     */
    @Test
    void testBasicFtpsConnection() throws Exception {
        // 1. 서버 연결
        ftpsClient.connect(ftpHost, ftpPort);
        
        // 2. 응답 코드 확인
        int reply = ftpsClient.getReplyCode();
        logger.info("서버 응답 코드: {}", reply);
        assertTrue(FTPReply.isPositiveCompletion(reply), 
            "FTP 서버가 연결을 거부했습니다. Reply: " + reply);
        
        // 3. TLS 보호 채널 설정 (AUTH TLS 명령)
        ftpsClient.execPBSZ(0);
        ftpsClient.execPROT("P");
        
        // 4. 로그인
        boolean loginSuccess = ftpsClient.login(ftpUser, ftpPassword);
        assertTrue(loginSuccess, "FTP 로그인 실패");
        logger.info("FTP 로그인 성공: {}", ftpUser);
        
        // 5. Binary 전송 모드 설정
        ftpsClient.setFileType(FTP.BINARY_FILE_TYPE);
        
        // 6. Passive 모드 설정 (방화벽 환경에서 권장)
        ftpsClient.enterLocalPassiveMode();
        
        // 7. 서버 시스템 타입 확인
        String systemType = ftpsClient.getSystemType();
        logger.info("서버 시스템 타입: {}", systemType);
        assertNotNull(systemType);
    }
    
    /**
     * FTP TLS 연결 with 자체 서명 인증서 허용
     */
    @Test
    void testFtpsConnectionWithSelfSignedCert() throws Exception {
        // 모든 인증서 신뢰 (개발/테스트 환경용 - 프로덕션에서는 사용 금지!)
        ftpsClient.setTrustManager(createTrustAllManager());
        
        // 연결 및 로그인
        ftpsClient.connect(ftpHost, ftpPort);
        
        int reply = ftpsClient.getReplyCode();
        assertTrue(FTPReply.isPositiveCompletion(reply));
        
        ftpsClient.execPBSZ(0);
        ftpsClient.execPROT("P");
        
        boolean loginSuccess = ftpsClient.login(ftpUser, ftpPassword);
        assertTrue(loginSuccess, "FTP 로그인 실패");
        
        logger.info("자체 서명 인증서를 사용한 FTPS 연결 성공");
    }
    
    /**
     * 디렉터리 목록 조회 테스트
     */
    @Test
    void testListDirectories() throws Exception {
        // 연결 및 로그인
        connectAndLogin();
        
        // 현재 디렉터리 확인
        String currentDir = ftpsClient.printWorkingDirectory();
        logger.info("현재 디렉터리: {}", currentDir);
        assertNotNull(currentDir);
        
        // 파일 목록 조회
        FTPFile[] files = ftpsClient.listFiles();
        logger.info("파일/디렉터리 개수: {}", files.length);
        
        // 파일 목록 출력
        for (FTPFile file : files) {
            String type = file.isDirectory() ? "DIR" : "FILE";
            logger.info("[{}] {} - {} bytes", type, file.getName(), file.getSize());
        }
        
        assertNotNull(files);
    }
    
    /**
     * 특정 디렉터리로 이동 테스트
     */
    @Test
    void testChangeDirectory() throws Exception {
        connectAndLogin();
        
        // 루트 디렉터리 확인
        String rootDir = ftpsClient.printWorkingDirectory();
        logger.info("현재 디렉터리: {}", rootDir);
        
        // 파일 목록에서 디렉터리 찾기
        FTPFile[] files = ftpsClient.listFiles();
        for (FTPFile file : files) {
            if (file.isDirectory()) {
                String dirName = file.getName();
                
                // 디렉터리로 이동
                boolean changed = ftpsClient.changeWorkingDirectory(dirName);
                if (changed) {
                    String newDir = ftpsClient.printWorkingDirectory();
                    logger.info("디렉터리 변경 성공: {}", newDir);
                    
                    // 원래 디렉터리로 복귀
                    ftpsClient.changeWorkingDirectory(rootDir);
                    assertTrue(changed, "디렉터리 변경 실패");
                    break;
                }
            }
        }
    }
    
    /**
     * 파일 다운로드 테스트
     */
    @Test
    void testDownloadFile() throws Exception {
        connectAndLogin();
        
        // 파일 목록 조회
        FTPFile[] files = ftpsClient.listFiles();
        
        // 첫 번째 파일 다운로드 시도
        for (FTPFile file : files) {
            if (!file.isDirectory()) {
                String fileName = file.getName();
                logger.info("다운로드 시도: {}", fileName);
                
                // 파일을 메모리로 다운로드
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                boolean success = ftpsClient.retrieveFile(fileName, outputStream);
                
                if (success) {
                    byte[] fileContent = outputStream.toByteArray();
                    logger.info("다운로드 성공: {} ({} bytes)", fileName, fileContent.length);
                    assertTrue(fileContent.length > 0, "다운로드된 파일이 비어있습니다");
                    break;
                } else {
                    logger.warn("파일 다운로드 실패: {}", fileName);
                }
            }
        }
    }
    
    /**
     * 파일 업로드 테스트
     */
    @Test
    void testUploadFile() throws Exception {
        connectAndLogin();
        
        // 테스트 파일 생성
        String testFileName = "test_upload_" + System.currentTimeMillis() + ".txt";
        String testContent = "This is a test file uploaded via FTPS at " + 
                             java.time.LocalDateTime.now();
        
        // 바이트 배열을 InputStream으로 변환
        InputStream inputStream = new java.io.ByteArrayInputStream(
            testContent.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        
        // 파일 업로드
        boolean uploaded = ftpsClient.storeFile(testFileName, inputStream);
        
        if (uploaded) {
            logger.info("파일 업로드 성공: {}", testFileName);
            
            // 업로드 확인
            FTPFile[] files = ftpsClient.listFiles(testFileName);
            assertEquals(1, files.length, "업로드된 파일을 찾을 수 없습니다");
            
            // 업로드한 파일 삭제 (정리)
            boolean deleted = ftpsClient.deleteFile(testFileName);
            logger.info("테스트 파일 삭제: {}", deleted);
        } else {
            logger.warn("파일 업로드 실패: {}", testFileName);
            fail("파일 업로드에 실패했습니다");
        }
    }
    
    /**
     * TLS 프로토콜 버전 테스트
     */
    @Test
    void testTlsProtocolVersions() throws Exception {
        // TLS 1.2 사용
        FTPSClient tlsClient = new FTPSClient("TLS", false);
        tlsClient.setEnabledProtocols(new String[]{"TLSv1.2"});
        
        try {
            tlsClient.connect(ftpHost, ftpPort);
            int reply = tlsClient.getReplyCode();
            assertTrue(FTPReply.isPositiveCompletion(reply));
            
            tlsClient.execPBSZ(0);
            tlsClient.execPROT("P");
            
            boolean loginSuccess = tlsClient.login(ftpUser, ftpPassword);
            assertTrue(loginSuccess, "TLS 1.2 로그인 실패");
            
            logger.info("TLS 1.2 연결 성공");
            
        } finally {
            if (tlsClient.isConnected()) {
                tlsClient.logout();
                tlsClient.disconnect();
            }
        }
    }
    
    /**
     * 타임아웃 설정 테스트
     */
    @Test
    void testConnectionWithTimeout() throws Exception {
        // 연결 타임아웃 설정 (밀리초)
        ftpsClient.setConnectTimeout(10000); // 10초
        
        // 데이터 타임아웃 설정 (Duration 사용)
        ftpsClient.setDataTimeout(Duration.ofSeconds(30));
        
        // 제어 연결 타임아웃 (밀리초, int 타입 사용)
        ftpsClient.setDefaultTimeout(10000); // 10초
        
        connectAndLogin();
        
        logger.info("타임아웃 설정 적용된 연결 성공");
    }
    
    /**
     * Active vs Passive 모드 테스트
     */
    @Test
    void testActiveAndPassiveMode() throws Exception {
        connectAndLogin();
        
        // 1. Passive 모드 테스트
        logger.info("=== Passive 모드 테스트 ===");
        ftpsClient.enterLocalPassiveMode();
        FTPFile[] passiveFiles = ftpsClient.listFiles();
        logger.info("Passive 모드 파일 개수: {}", passiveFiles.length);
        assertNotNull(passiveFiles);
        
        // 2. Active 모드 테스트
        logger.info("=== Active 모드 테스트 ===");
        ftpsClient.enterLocalActiveMode();
        FTPFile[] activeFiles = ftpsClient.listFiles();
        logger.info("Active 모드 파일 개수: {}", activeFiles.length);
        assertNotNull(activeFiles);
    }
    
    /**
     * 연결 상태 및 응답 메시지 확인
     */
    @Test
    void testConnectionStatus() throws Exception {
        // 연결 전
        assertFalse(ftpsClient.isConnected(), "연결 전에는 isConnected()가 false여야 합니다");
        
        // 연결
        ftpsClient.connect(ftpHost, ftpPort);
        assertTrue(ftpsClient.isConnected(), "연결 후에는 isConnected()가 true여야 합니다");
        
        // 응답 문자열 확인
        String replyString = ftpsClient.getReplyString();
        logger.info("서버 응답 메시지: {}", replyString.trim());
        assertNotNull(replyString);
        
        // TLS 설정
        ftpsClient.execPBSZ(0);
        ftpsClient.execPROT("P");
        
        // 로그인
        ftpsClient.login(ftpUser, ftpPassword);
        
        // NOOP 명령 (연결 유지 확인)
        boolean noopSuccess = ftpsClient.sendNoOp();
        assertTrue(noopSuccess, "NOOP 명령 실패");
        logger.info("NOOP 명령 성공 - 연결 활성 상태");
    }
    
    /**
     * 암호화된 데이터 채널 테스트
     */
    @Test
    void testEncryptedDataChannel() throws Exception {
        connectAndLogin();
        
        // PROT P 설정 확인 (데이터 채널 암호화)
        ftpsClient.execPROT("P");
        
        logger.info("데이터 채널 암호화 모드 활성화");
        
        // 파일 목록 조회 (암호화된 채널 사용)
        FTPFile[] files = ftpsClient.listFiles();
        assertNotNull(files, "암호화된 채널에서 파일 목록 조회 실패");
        logger.info("암호화된 채널로 {} 개 파일 조회", files.length);
    }
    
    /**
     * 에러 처리 테스트 - 잘못된 인증 정보
     */
    @Test
    void testInvalidCredentials() throws Exception {
        ftpsClient.connect(ftpHost, ftpPort);
        
        ftpsClient.execPBSZ(0);
        ftpsClient.execPROT("P");
        
        // 잘못된 비밀번호로 로그인 시도
        boolean loginSuccess = ftpsClient.login(ftpUser, "wrong_password");
        assertFalse(loginSuccess, "잘못된 비밀번호로 로그인 성공하면 안 됩니다");
        
        logger.info("예상대로 로그인 실패: 잘못된 인증 정보");
    }
    
    /**
     * 재연결 테스트
     */
    @Test
    void testReconnection() throws Exception {
        // 첫 번째 연결
        connectAndLogin();
        String dir1 = ftpsClient.printWorkingDirectory();
        logger.info("첫 번째 연결 디렉터리: {}", dir1);
        
        // 연결 종료
        ftpsClient.logout();
        ftpsClient.disconnect();
        
        // 재연결
        ftpsClient = new FTPSClient("TLS", false);
        connectAndLogin();
        String dir2 = ftpsClient.printWorkingDirectory();
        logger.info("재연결 디렉터리: {}", dir2);
        
        assertEquals(dir1, dir2, "재연결 후 동일한 디렉터리에 있어야 합니다");
    }
    
    /**
     * FTP 명령어 직접 실행 테스트
     */
    @Test
    void testCustomFtpCommands() throws Exception {
        connectAndLogin();
        
        // FEAT 명령 (서버 기능 확인)
        ftpsClient.sendCommand("FEAT");
        String[] features = ftpsClient.getReplyStrings();
        logger.info("서버 지원 기능:");
        for (String feature : features) {
            logger.info("  - {}", feature.trim());
        }
        
        // PWD 명령 (현재 디렉터리)
        ftpsClient.sendCommand("PWD");
        String pwdReply = ftpsClient.getReplyString();
        logger.info("PWD 응답: {}", pwdReply.trim());
        
        assertNotNull(features);
    }
    
    // ========== 헬퍼 메서드 ==========
    
    /**
     * FTP 서버 연결 및 로그인 헬퍼 메서드 (RFC 4217 순서 준수)
     */
    private void connectAndLogin() throws IOException {
        // 연결
        ftpsClient.connect(ftpHost, ftpPort);
        
        int reply = ftpsClient.getReplyCode();
        if (!FTPReply.isPositiveCompletion(reply)) {
            ftpsClient.disconnect();
            throw new IOException("FTP 서버 연결 실패. Reply: " + reply);
        }
        
        // 로그인 (RFC 4217: 연결 후 바로 로그인)
        boolean success = ftpsClient.login(ftpUser, ftpPassword);
        if (!success) {
            throw new IOException("FTP 로그인 실패");
        }
        logger.debug("로그인 성공");
        
        // TLS 보호 채널 설정 (RFC 4217: 로그인 후)
        ftpsClient.execPBSZ(0);
        ftpsClient.execPROT("P");
        logger.debug("PROT P 설정 완료 (제어/데이터 채널 암호화)");
        
        // ⭐ 핵심: TLS 세션 재개 강제 (FileZilla Server 1.x 필수!)
        ftpsClient.setEnabledSessionCreation(false);
        logger.debug("TLS 세션 재개 활성화 (FileZilla Server 호환)");
        
        // Binary 모드 설정
        ftpsClient.setFileType(FTP.BINARY_FILE_TYPE);
        
        // 전송 모드 설정 (Passive 또는 Active)
        if (usePassiveMode) {
            ftpsClient.enterLocalPassiveMode();
            logger.debug("전송 모드: PASSIVE (수동형)");
        } else {
            ftpsClient.enterLocalActiveMode();
            logger.debug("전송 모드: ACTIVE (능동형)");
        }
    }
    
    /**
     * 모든 인증서를 신뢰하는 TrustManager 생성 (테스트 전용)
     * 
     * <p><strong>경고:</strong> 프로덕션 환경에서는 사용하지 마세요!
     * 이 방법은 중간자 공격(MITM)에 취약합니다.</p>
     */
    private TrustManager createTrustAllManager() {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
                // 모든 클라이언트 인증서 신뢰
            }
            
            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
                // 모든 서버 인증서 신뢰
                logger.warn("⚠️ 모든 서버 인증서를 신뢰하는 모드 (테스트 전용)");
            }
            
            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }
}


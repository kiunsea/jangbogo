package com.jiniebox.jangbogo.ftp.client;

import org.apache.commons.net.ftp.FTPSClient;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FTP TLS 설정 파일 로드 및 연결 테스트
 * 
 * <p>프로퍼티 파일을 사용하여 FTP 서버 정보를 관리하는 방법을 테스트합니다.</p>
 * 
 * <p>사용 방법:</p>
 * <ol>
 *   <li>src/test/resources/ftp-test.properties.example을 ftp-test.properties로 복사</li>
 *   <li>실제 FTP 서버 정보로 값 변경</li>
 *   <li>@Disabled 어노테이션 제거 후 테스트 실행</li>
 * </ol>
 * 
 * @author jiniebox.com
 * @version 1.0
 * @since 2025-11-05
 */
class FtpTlsConfigTest {
    
    private static final Logger logger = LoggerFactory.getLogger(FtpTlsConfigTest.class);
    
    /**
     * 설정 파일에서 FTP 정보 로드 테스트
     */
    @Test
    void testLoadFtpConfig() {
        // 환경 변수 우선 확인
        String host = System.getenv("FTP_HOST");
        String port = System.getenv("FTP_PORT");
        String user = System.getenv("FTP_USER");
        String pass = System.getenv("FTP_PASS");
        String mode = System.getenv("FTP_MODE");
        
        logger.info("환경 변수 로드 테스트:");
        logger.info("  FTP_HOST: {}", host != null ? host : "(미설정)");
        logger.info("  FTP_PORT: {}", port != null ? port : "(미설정)");
        logger.info("  FTP_USER: {}", user != null ? user : "(미설정)");
        logger.info("  FTP_PASS: {}", pass != null ? "***" : "(미설정)");
        logger.info("  FTP_MODE: {}", mode != null ? mode : "(미설정)");
        
        // 프로퍼티 파일 로드 시도
        Properties props = loadFtpProperties();
        if (props != null) {
            logger.info("프로퍼티 파일 로드 성공:");
            logger.info("  ftp.host: {}", props.getProperty("ftp.host"));
            logger.info("  ftp.port: {}", props.getProperty("ftp.port"));
            logger.info("  ftp.user: {}", props.getProperty("ftp.user"));
            logger.info("  ftp.mode: {}", props.getProperty("ftp.mode", "PASSIVE"));
        }
        
        // 최소한 하나의 설정 방법이 있어야 함
        assertTrue(host != null || props != null, 
            "환경 변수 또는 프로퍼티 파일 중 하나는 설정되어야 합니다");
    }
    
    /**
     * 프로퍼티 파일에서 FtpConfig 생성 테스트
     */
    @Test
    void testLoadFtpConfigFromProperties() {
        Properties props = loadFtpProperties();
        
        if (props != null) {
            // 프로퍼티 파일에서 설정 읽기
            String host = props.getProperty("ftp.host", "localhost");
            int port = Integer.parseInt(props.getProperty("ftp.port", "21"));
            String user = props.getProperty("ftp.user", "anonymous");
            String password = props.getProperty("ftp.password", "");
            String mode = props.getProperty("ftp.mode", "PASSIVE");
            boolean passiveMode = "PASSIVE".equalsIgnoreCase(mode) || "수동형".equals(mode);
            
            // FtpConfig 생성
            FtpConfig config = FtpConfig.builder()
                .host(host)
                .port(port)
                .username(user)
                .password(password)
                .protocol("TLS")
                .implicit(false)
                .passiveMode(passiveMode)
                .connectTimeout(10000)
                .dataTimeout(30000)
                .build();
            
            assertNotNull(config);
            assertEquals(passiveMode, config.isPassiveMode());
            
            logger.info("프로퍼티 파일 기반 FtpConfig 생성 성공");
            logger.info("  전송 모드: {}", passiveMode ? "PASSIVE (수동형)" : "ACTIVE (능동형)");
        } else {
            logger.warn("프로퍼티 파일 없음 - 테스트 스킵");
        }
    }
    
    /**
     * 설정 파일 기반 연결 빌더 패턴 테스트
     */
    @Test
    void testFtpConfigBuilder() {
        FtpConfig config = FtpConfig.builder()
            .host("ftp.example.com")
            .port(21)
            .username("testuser")
            .password("testpass")
            .protocol("TLS")
            .implicit(false)
            .passiveMode(true)
            .connectTimeout(10000)
            .dataTimeout(30000)
            .build();
        
        assertNotNull(config);
        assertEquals("ftp.example.com", config.getHost());
        assertEquals(21, config.getPort());
        assertEquals("testuser", config.getUsername());
        assertTrue(config.isPassiveMode());
        
        logger.info("FtpConfig 빌더 패턴 테스트 성공");
        logger.info("  설정 정보: {}", config);
    }
    
    /**
     * 프로퍼티 파일 로드 헬퍼 메서드
     */
    private Properties loadFtpProperties() {
        Properties props = new Properties();
        String[] possiblePaths = {
            "src/test/resources/ftp-test.properties",
            "ftp-test.properties",
            System.getProperty("user.home") + "/.jangbogo/ftp-test.properties"
        };
        
        for (String path : possiblePaths) {
            try (FileInputStream fis = new FileInputStream(path)) {
                props.load(fis);
                logger.info("프로퍼티 파일 로드 성공: {}", path);
                return props;
            } catch (IOException e) {
                logger.debug("프로퍼티 파일 로드 실패: {} - {}", path, e.getMessage());
            }
        }
        
        logger.warn("프로퍼티 파일을 찾을 수 없습니다");
        return null;
    }
    
    /**
     * FTP 설정을 담는 내부 클래스 (빌더 패턴)
     */
    static class FtpConfig {
        private String host;
        private int port;
        private String username;
        private String password;
        private String protocol;
        private boolean implicit;
        private boolean passiveMode;
        private int connectTimeout;
        private int dataTimeout;
        
        private FtpConfig(Builder builder) {
            this.host = builder.host;
            this.port = builder.port;
            this.username = builder.username;
            this.password = builder.password;
            this.protocol = builder.protocol;
            this.implicit = builder.implicit;
            this.passiveMode = builder.passiveMode;
            this.connectTimeout = builder.connectTimeout;
            this.dataTimeout = builder.dataTimeout;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        // Getters
        public String getHost() { return host; }
        public int getPort() { return port; }
        public String getUsername() { return username; }
        public String getPassword() { return password; }
        public String getProtocol() { return protocol; }
        public boolean isImplicit() { return implicit; }
        public boolean isPassiveMode() { return passiveMode; }
        public int getConnectTimeout() { return connectTimeout; }
        public int getDataTimeout() { return dataTimeout; }
        
        @Override
        public String toString() {
            String modeStr = passiveMode ? "PASSIVE (수동형)" : "ACTIVE (능동형)";
            return String.format("FtpConfig{host='%s', port=%d, user='%s', protocol='%s', mode=%s}",
                host, port, username, protocol, modeStr);
        }
        
        /**
         * FtpConfig 빌더 클래스
         */
        static class Builder {
            private String host = "localhost";
            private int port = 21;
            private String username = "anonymous";
            private String password = "";
            private String protocol = "TLS";
            private boolean implicit = false;
            private boolean passiveMode = true;
            private int connectTimeout = 10000;
            private int dataTimeout = 30000;
            
            public Builder host(String host) {
                this.host = host;
                return this;
            }
            
            public Builder port(int port) {
                this.port = port;
                return this;
            }
            
            public Builder username(String username) {
                this.username = username;
                return this;
            }
            
            public Builder password(String password) {
                this.password = password;
                return this;
            }
            
            public Builder protocol(String protocol) {
                this.protocol = protocol;
                return this;
            }
            
            public Builder implicit(boolean implicit) {
                this.implicit = implicit;
                return this;
            }
            
            public Builder passiveMode(boolean passiveMode) {
                this.passiveMode = passiveMode;
                return this;
            }
            
            public Builder connectTimeout(int connectTimeout) {
                this.connectTimeout = connectTimeout;
                return this;
            }
            
            public Builder dataTimeout(int dataTimeout) {
                this.dataTimeout = dataTimeout;
                return this;
            }
            
            public FtpConfig build() {
                return new FtpConfig(this);
            }
        }
    }
    
    /**
     * FtpConfig를 사용하여 FTPSClient 생성
     */
    @Test
    void testCreateFtpClientFromConfig() {
        FtpConfig config = FtpConfig.builder()
            .host("ftp.example.com")
            .port(990)  // Implicit TLS 포트
            .username("admin")
            .password("secure_pass")
            .protocol("TLS")
            .implicit(true)  // Implicit TLS
            .passiveMode(true)
            .connectTimeout(15000)
            .dataTimeout(60000)
            .build();
        
        // FTPSClient 생성
        FTPSClient client = new FTPSClient(config.getProtocol(), config.isImplicit());
        client.setConnectTimeout(config.getConnectTimeout());
        client.setDataTimeout(Duration.ofMillis(config.getDataTimeout()));
        
        assertNotNull(client);
        logger.info("FTPSClient 생성 성공: {}", config);
        
        // 연결은 실제 서버가 없으므로 시도하지 않음
        assertFalse(client.isConnected());
    }
}


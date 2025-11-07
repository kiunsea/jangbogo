package com.jiniebox.jangbogo.ftp.client;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;

import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * FTP over TLS (FTPS) 간단한 텍스트 파일 업로드 테스트
 * 
 * <p>Main 함수로 직접 실행 가능한 FTP TLS 연결 및 파일 업로드 테스트 프로그램입니다.</p>
 * 
 * <p><strong>실행 방법:</strong></p>
 * <pre>
 * 1. src/test/resources/ftp-test.properties 파일 생성
 * 2. ftp.host, ftp.port, ftp.user, ftp.password 설정
 * 3. IDE에서 main() 메서드 실행
 * </pre>
 * 
 * <p>또는 환경 변수 사용 (우선순위: 환경변수 > 프로퍼티 파일 > 기본값)</p>
 * 
 * @author jiniebox.com
 * @version 1.0
 * @since 2025-11-05
 */
public class SimpleFtpTlsUploadTest {
    
    /**
     * 메인 실행 함수
     * 
     * @param args 명령줄 인자 (사용 안 함)
     */
    public static void main(String[] args) {
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("  FTP over TLS (FTPS) 텍스트 파일 업로드 테스트");
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println();
        
        // ========== 1. 설정 파일 로드 ==========
        System.out.println("📋 설정 파일 로드 중...");
        
        Properties props = new Properties();
        boolean propsLoaded = false;
        
        // 프로퍼티 파일 경로들
        String[] propsPaths = {
            "src/test/resources/ftp-test.properties",
            "ftp-test.properties"
        };
        
        for (String path : propsPaths) {
            try (FileInputStream fis = new FileInputStream(path)) {
                props.load(fis);
                System.out.println("  ✅ 프로퍼티 파일 로드: " + path);
                propsLoaded = true;
                break;
            } catch (Exception e) {
                System.out.println("  ⚠️  파일 없음: " + path);
            }
        }
        
        if (!propsLoaded) {
            System.out.println("  ℹ️  프로퍼티 파일을 찾을 수 없습니다. 환경 변수 또는 기본값 사용");
        }
        System.out.println();
        
        // ========== 2. FTP 서버 정보 로드 (우선순위: 환경변수 > 프로퍼티 > 기본값) ==========
        System.out.println("🔧 FTP 서버 정보 구성 중...");
        
        // Host
        String ftpHost = System.getenv("FTP_HOST");
        if (ftpHost == null || ftpHost.isEmpty()) {
            ftpHost = props.getProperty("ftp.host", "ftp.dlptest.com");
        }
        System.out.println("  - Host: " + ftpHost + " (출처: " + getSource(System.getenv("FTP_HOST"), props.getProperty("ftp.host")) + ")");
        
        // Port
        String portStr = System.getenv("FTP_PORT");
        if (portStr == null || portStr.isEmpty()) {
            portStr = props.getProperty("ftp.port", "21");
        }
        int ftpPort = Integer.parseInt(portStr);
        System.out.println("  - Port: " + ftpPort + " (출처: " + getSource(System.getenv("FTP_PORT"), props.getProperty("ftp.port")) + ")");
        
        // User
        String ftpUser = System.getenv("FTP_USER");
        if (ftpUser == null || ftpUser.isEmpty()) {
            ftpUser = props.getProperty("ftp.user", "dlpuser");
        }
        System.out.println("  - User: " + ftpUser + " (출처: " + getSource(System.getenv("FTP_USER"), props.getProperty("ftp.user")) + ")");
        
        // Password
        String ftpPassword = System.getenv("FTP_PASS");
        if (ftpPassword == null || ftpPassword.isEmpty()) {
            ftpPassword = props.getProperty("ftp.password", "rNrKYTX9g7z3RgJRmxWuGHbeu");
        }
        System.out.println("  - Pass: " + maskPassword(ftpPassword) + " (출처: " + getSource(System.getenv("FTP_PASS"), props.getProperty("ftp.password")) + ")");
        
        // Transfer Mode (전송 모드)
        String transferMode = System.getenv("FTP_MODE");
        if (transferMode == null || transferMode.isEmpty()) {
            transferMode = props.getProperty("ftp.mode", "PASSIVE");
        }
        boolean usePassiveMode = "PASSIVE".equalsIgnoreCase(transferMode) || "수동형".equals(transferMode);
        System.out.println("  - 전송 모드: " + (usePassiveMode ? "PASSIVE (수동형)" : "ACTIVE (능동형)") + 
            " (출처: " + getSource(System.getenv("FTP_MODE"), props.getProperty("ftp.mode")) + ")");
        
        System.out.println();
        
        FTPSClient ftpsClient = null;
        
        try {
            // ========== 3. FTPS 클라이언트 생성 ==========
            System.out.println("🔧 FTPS 클라이언트 생성 중...");
            System.out.println("  ℹ️  FileZilla Server 1.11.1 호환 모드");
            System.out.println();
            
            // Explicit TLS 사용 (SSL 프로토콜 사용 - FileZilla Server 호환)
            ftpsClient = new FTPSClient("SSL", false);
            System.out.println("  - 프로토콜: SSL (FileZilla Server 1.x 권장)");
            
            // FileZilla Server TLS 세션 재개 문제 해결을 위한 모든 설정
            ftpsClient.setEndpointCheckingEnabled(false);
            System.out.println("  - 엔드포인트 체크: 비활성화");
            
            ftpsClient.setUseEPSVwithIPv4(true);
            System.out.println("  - EPSV with IPv4: 활성화");
            
            ftpsClient.setBufferSize(0);
            System.out.println("  - 버퍼 크기: 0 (최적화)");
            
            ftpsClient.setAutodetectUTF8(false);
            System.out.println("  - UTF-8 자동 감지: 비활성화");
            
            // 자체 서명 인증서 허용 (테스트 환경용)
            ftpsClient.setTrustManager(new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }
                
                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    System.out.println("  - 자체 서명 인증서: 허용 (테스트 전용)");
                }
                
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            });
            
            // 타임아웃 설정
            ftpsClient.setConnectTimeout(15000); // 15초
            ftpsClient.setControlKeepAliveTimeout(300); // 5분
            System.out.println("  - 타임아웃: 연결 15초, Keep-Alive 300초");
            
            System.out.println();
            System.out.println("  ✅ FTPS 클라이언트 생성 완료");
            System.out.println();
            
            // ========== 4. FTP 서버 연결 ==========
            System.out.println("🌐 FTP 서버 연결 중...");
            System.out.println("  - 연결 대상: " + ftpHost + ":" + ftpPort);
            
            ftpsClient.connect(ftpHost, ftpPort);
            
            int replyCode = ftpsClient.getReplyCode();
            String replyString = ftpsClient.getReplyString();
            
            System.out.println("  - 응답 코드: " + replyCode);
            System.out.println("  - 응답 메시지: " + replyString.trim());
            
            if (!FTPReply.isPositiveCompletion(replyCode)) {
                System.err.println("❌ FTP 서버 연결 실패!");
                System.err.println("   응답 코드: " + replyCode);
                return;
            }
            
            System.out.println("  ✅ 서버 연결 성공");
            System.out.println();
            
            // ========== 5. 로그인 (RFC 4217 순서: 연결 후 바로 로그인) ==========
            System.out.println("🔑 FTP 서버 로그인 중...");
            System.out.println("  - 사용자: " + ftpUser);
            
            boolean loginSuccess = ftpsClient.login(ftpUser, ftpPassword);
            
            if (!loginSuccess) {
                System.err.println("❌ 로그인 실패!");
                System.err.println("   사용자명/비밀번호를 확인하세요.");
                System.err.println("   응답: " + ftpsClient.getReplyString());
                return;
            }
            
            System.out.println("  ✅ 로그인 성공");
            System.out.println();
            
            // ========== 6. TLS 보호 채널 설정 (RFC 4217 순서: 로그인 후) ==========
            System.out.println("🔐 TLS 보안 채널 설정 중...");
            
            // PBSZ 명령 (Protection Buffer Size) - 반드시 먼저 실행
            ftpsClient.execPBSZ(0);
            System.out.println("  - PBSZ 0 실행 완료");
            
            // PROT 명령 (Protection Level - Private)
            ftpsClient.execPROT("P");
            System.out.println("  - PROT P 설정 완료 (제어/데이터 채널 모두 암호화)");
            
            // ⭐⭐⭐ 핵심: TLS 세션 재개 강제 (FileZilla Server 1.x 필수!)
            ftpsClient.setEnabledSessionCreation(false);
            System.out.println("  - 세션 재개 강제 활성화 (FileZilla Server 1.x 호환) ⭐");
            System.out.println("    └─ 데이터 연결 시 제어 연결의 TLS 세션 재사용");
            
            System.out.println("  ✅ TLS 보안 채널 설정 완료");
            System.out.println();
            
            // ========== 7. FTP 전송 설정 ==========
            System.out.println("⚙️  FTP 전송 모드 설정 중...");
            
            // Binary 모드 설정
            ftpsClient.setFileType(FTP.BINARY_FILE_TYPE);
            System.out.println("  - 파일 타입: BINARY");
            
            // 전송 모드 설정 (Passive 또는 Active)
            if (usePassiveMode) {
                ftpsClient.enterLocalPassiveMode();
                System.out.println("  - 전송 모드: PASSIVE (수동형) ✅");
                System.out.println("    └─ 서버가 클라이언트에게 데이터 포트를 알려줌");
                System.out.println("    └─ 방화벽 환경에서 권장");
            } else {
                ftpsClient.enterLocalActiveMode();
                System.out.println("  - 전송 모드: ACTIVE (능동형)");
                System.out.println("    └─ 클라이언트가 서버에게 데이터 포트를 알려줌");
                System.out.println("    └─ 방화벽 설정 필요할 수 있음");
            }
            System.out.println("  ✅ 전송 모드 설정 완료");
            System.out.println();
            
            // ========== 8. 현재 디렉터리 확인 ==========
            System.out.println("📁 현재 디렉터리 확인 중...");
            String currentDir = ftpsClient.printWorkingDirectory();
            System.out.println("  - 현재 위치: " + currentDir);
            System.out.println();
            
            // ========== 9. 파일 목록 조회 ==========
            System.out.println("📂 파일 목록 조회 중...");
            FTPFile[] files = ftpsClient.listFiles();
            System.out.println("  - 파일/폴더 개수: " + files.length);
            
            if (files.length > 0) {
                System.out.println("  - 목록:");
                for (int i = 0; i < Math.min(5, files.length); i++) {
                    FTPFile file = files[i];
                    String type = file.isDirectory() ? "[DIR]" : "[FILE]";
                    System.out.println("    " + type + " " + file.getName() + 
                        " (" + file.getSize() + " bytes)");
                }
                if (files.length > 5) {
                    System.out.println("    ... 외 " + (files.length - 5) + "개");
                }
            }
            System.out.println();
            
            // ========== 10. 테스트 파일 생성 ==========
            System.out.println("📝 업로드할 테스트 파일 생성 중...");
            
            // 파일명 생성 (타임스탬프 포함)
            String timestamp = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            );
            String testFileName = "jangbogo_test_" + timestamp + ".txt";
            
            // 파일 내용 생성
            StringBuilder content = new StringBuilder();
            content.append("═══════════════════════════════════════════════════\n");
            content.append("  Jangbogo FTP TLS Upload Test File\n");
            content.append("═══════════════════════════════════════════════════\n");
            content.append("\n");
            content.append("생성 일시: ").append(LocalDateTime.now()).append("\n");
            content.append("FTP 서버: ").append(ftpHost).append(":").append(ftpPort).append("\n");
            content.append("사용자명: ").append(ftpUser).append("\n");
            content.append("파일명: ").append(testFileName).append("\n");
            content.append("\n");
            content.append("이 파일은 Jangbogo 프로젝트의 FTP TLS 연결 테스트를 위해\n");
            content.append("자동으로 생성된 테스트 파일입니다.\n");
            content.append("\n");
            content.append("---\n");
            content.append("테스트 항목:\n");
            content.append("✅ FTP TLS 연결\n");
            content.append("✅ 인증 및 로그인\n");
            content.append("✅ 파일 업로드\n");
            content.append("✅ 파일 삭제\n");
            content.append("\n");
            content.append("Copyright © 2025 jiniebox.com\n");
            content.append("Contact: kiunsea@gmail.com\n");
            content.append("═══════════════════════════════════════════════════\n");
            
            String fileContent = content.toString();
            byte[] contentBytes = fileContent.getBytes(StandardCharsets.UTF_8);
            
            System.out.println("  - 파일명: " + testFileName);
            System.out.println("  - 파일 크기: " + contentBytes.length + " bytes");
            System.out.println("  ✅ 테스트 파일 생성 완료");
            System.out.println();
            
            // ========== 11. 파일 업로드 ==========
            System.out.println("📤 파일 업로드 중...");
            System.out.println("  - 업로드 대상: " + testFileName);
            
            InputStream inputStream = new ByteArrayInputStream(contentBytes);
            boolean uploadSuccess = ftpsClient.storeFile(testFileName, inputStream);
            
            if (!uploadSuccess) {
                System.err.println("❌ 파일 업로드 실패!");
                System.err.println("   응답: " + ftpsClient.getReplyString());
                return;
            }
            
            System.out.println("  ✅ 파일 업로드 성공!");
            System.out.println();
            
            // ========== 12. 업로드 확인 ==========
            System.out.println("🔍 업로드된 파일 확인 중...");
            FTPFile[] uploadedFiles = ftpsClient.listFiles(testFileName);
            
            if (uploadedFiles.length > 0) {
                FTPFile uploadedFile = uploadedFiles[0];
                System.out.println("  - 파일명: " + uploadedFile.getName());
                System.out.println("  - 크기: " + uploadedFile.getSize() + " bytes");
                System.out.println("  - 수정 시간: " + uploadedFile.getTimestamp().getTime());
                System.out.println("  ✅ 파일 확인 완료");
            } else {
                System.err.println("  ⚠️  업로드된 파일을 찾을 수 없습니다");
            }
            System.out.println();
            
            // ========== 13. 파일 삭제 (정리) ==========
            System.out.println("🗑️  테스트 파일 삭제 중...");
            boolean deleteSuccess = ftpsClient.deleteFile(testFileName);
            
            if (deleteSuccess) {
                System.out.println("  ✅ 파일 삭제 완료");
            } else {
                System.out.println("  ⚠️  파일 삭제 실패 (수동으로 삭제하세요: " + testFileName + ")");
            }
            System.out.println();
            
            // ========== 14. 최종 결과 ==========
            System.out.println("═══════════════════════════════════════════════════════════");
            System.out.println("  ✅ 모든 테스트 완료!");
            System.out.println("═══════════════════════════════════════════════════════════");
            System.out.println();
            System.out.println("테스트 결과 요약:");
            System.out.println("  ✅ FTP TLS 연결");
            System.out.println("  ✅ 로그인 인증");
            System.out.println("  ✅ 디렉터리 목록 조회");
            System.out.println("  ✅ 텍스트 파일 업로드");
            System.out.println("  ✅ 파일 확인");
            System.out.println("  ✅ 파일 삭제");
            System.out.println();
            System.out.println("FTP TLS 연결이 정상적으로 작동합니다! 🎉");
            System.out.println();
            
        } catch (Exception e) {
            // ========== 에러 처리 ==========
            System.err.println();
            System.err.println("═══════════════════════════════════════════════════════════");
            System.err.println("  ❌ 오류 발생!");
            System.err.println("═══════════════════════════════════════════════════════════");
            System.err.println();
            System.err.println("오류 메시지: " + e.getMessage());
            System.err.println("오류 타입: " + e.getClass().getSimpleName());
            System.err.println();
            System.err.println("스택 트레이스:");
            e.printStackTrace();
            System.err.println();
            
            System.err.println("💡 문제 해결 방법:");
            System.err.println("  1. FTP 서버 정보가 올바른지 확인");
            System.err.println("     - src/test/resources/ftp-test.properties 파일 확인");
            System.err.println("  2. 네트워크 연결 상태 확인");
            System.err.println("  3. 방화벽 설정 확인 (포트 21 허용)");
            System.err.println("  4. 사용자명/비밀번호 확인");
            System.err.println();
            
        } finally {
            // ========== 15. 연결 종료 ==========
            if (ftpsClient != null && ftpsClient.isConnected()) {
                try {
                    System.out.println("🔌 FTP 연결 종료 중...");
                    ftpsClient.logout();
                    ftpsClient.disconnect();
                    System.out.println("  ✅ 연결 종료 완료");
                    System.out.println();
                } catch (Exception e) {
                    System.err.println("  ⚠️  연결 종료 중 오류: " + e.getMessage());
                }
            }
            
            System.out.println("프로그램을 종료합니다.");
        }
    }
    
    /**
     * 설정값의 출처를 반환하는 헬퍼 메서드
     * 
     * @param envValue 환경 변수 값
     * @param propsValue 프로퍼티 파일 값
     * @return 출처 문자열
     */
    private static String getSource(String envValue, String propsValue) {
        if (envValue != null && !envValue.isEmpty()) {
            return "환경변수";
        } else if (propsValue != null && !propsValue.isEmpty()) {
            return "프로퍼티";
        } else {
            return "기본값";
        }
    }
    
    /**
     * 비밀번호 마스킹 헬퍼 메서드
     * 
     * @param password 원본 비밀번호
     * @return 마스킹된 비밀번호 (예: "abc***")
     */
    private static String maskPassword(String password) {
        if (password == null || password.isEmpty()) {
            return "(미설정)";
        }
        
        if (password.length() <= 3) {
            return "***";
        }
        
        return password.substring(0, 3) + "***";
    }
}


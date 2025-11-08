package com.jiniebox.jangbogo.util;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * FTP 파일 업로드 유틸리티 클래스
 */
public class FtpUploadUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(FtpUploadUtil.class);
    
    /**
     * FTP 주소 정보를 파싱하여 호스트와 포트를 반환합니다.
     * 
     * @param ftpAddress FTP 주소 (예: "ftp.example.com", "127.0.0.1:21", "ftp.example.com:2121")
     * @return [host, port] 배열
     */
    private static String[] parseFtpAddress(String ftpAddress) {
        String host;
        int port = 21; // 기본 FTP 포트
        
        if (ftpAddress.contains(":")) {
            String[] parts = ftpAddress.split(":");
            host = parts[0];
            try {
                port = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                logger.warn("잘못된 포트 번호, 기본값 21 사용: {}", parts[1]);
            }
        } else {
            host = ftpAddress;
        }
        
        return new String[]{host, String.valueOf(port)};
    }
    
    /**
     * 파일을 FTP 서버에 업로드합니다.
     * 
     * @param ftpAddress FTP 서버 주소 (예: "ftp.example.com" 또는 "127.0.0.1:21")
     * @param ftpUser FTP 사용자 아이디
     * @param ftpPassword FTP 비밀번호
     * @param localFilePath 업로드할 로컬 파일 경로
     * @return 업로드 성공 여부
     */
    public static boolean uploadFile(String ftpAddress, String ftpUser, String ftpPassword, String localFilePath) {
        FTPClient ftpClient = null;
        FileInputStream inputStream = null;
        
        try {
            // FTP 주소 파싱
            String[] addressInfo = parseFtpAddress(ftpAddress);
            String host = addressInfo[0];
            int port = Integer.parseInt(addressInfo[1]);
            
            File localFile = new File(localFilePath);
            if (!localFile.exists()) {
                logger.error("업로드할 파일이 존재하지 않습니다: {}", localFilePath);
                return false;
            }
            
            String remoteFileName = localFile.getName();
            
            logger.info("FTP 업로드 시작 - 서버: {}:{}, 파일: {}", host, port, remoteFileName);
            
            // FTP 클라이언트 생성
            ftpClient = new FTPClient();
            
            // 타임아웃 설정
            ftpClient.setConnectTimeout(15000); // 15초
            
            // FTP 서버 연결
            ftpClient.connect(host, port);
            int replyCode = ftpClient.getReplyCode();
            
            if (!FTPReply.isPositiveCompletion(replyCode)) {
                logger.error("FTP 서버 연결 실패 - Reply Code: {}", replyCode);
                return false;
            }
            
            logger.info("FTP 서버 연결 성공");
            
            // 로그인
            boolean loginSuccess = ftpClient.login(ftpUser, ftpPassword);
            if (!loginSuccess) {
                logger.error("FTP 로그인 실패 - User: {}", ftpUser);
                return false;
            }
            
            logger.info("FTP 로그인 성공");
            
            // 바이너리 모드 설정
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
            
            // Passive 모드 사용
            ftpClient.enterLocalPassiveMode();
            
            logger.info("FTP 파일 업로드 중: {}", remoteFileName);
            
            // 파일 업로드
            inputStream = new FileInputStream(localFile);
            boolean uploadSuccess = ftpClient.storeFile(remoteFileName, inputStream);
            
            if (uploadSuccess) {
                logger.info("FTP 업로드 성공: {} -> {}:{}/{}", localFilePath, host, port, remoteFileName);
                return true;
            } else {
                logger.error("FTP 업로드 실패: {} - Reply: {}", localFilePath, ftpClient.getReplyString());
                return false;
            }
            
        } catch (Exception e) {
            logger.error("FTP 업로드 중 오류 발생: {}", e.getMessage(), e);
            return false;
        } finally {
            // 리소스 정리
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException e) {
                logger.warn("InputStream 닫기 실패", e);
            }
            
            try {
                if (ftpClient != null && ftpClient.isConnected()) {
                    ftpClient.logout();
                    ftpClient.disconnect();
                }
            } catch (IOException e) {
                logger.warn("FTP 연결 종료 실패", e);
            }
        }
    }
}


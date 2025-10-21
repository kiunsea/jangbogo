package com.jiniebox.jangbogo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Jangbogo 프로젝트의 datasource 연결을 테스트하는 클래스
 * main 함수로 실행 가능
 */
public class JdbcConnectionTest {

    // application.yml의 datasource 설정
    private static final String DB_URL = "jdbc:sqlite:./db/jangbogo-dev.db";
    private static final String DB_DRIVER = "org.sqlite.JDBC";
    
    public static void main(String[] args) {
        System.out.println("=".repeat(50));
        System.out.println("Jangbogo JDBC Connection Test");
        System.out.println("=".repeat(50));
        
        Connection conn = null;
        
        try {
            // 1. JDBC 드라이버 로드
            Class.forName(DB_DRIVER);
            System.out.println("✓ JDBC 드라이버 로드 성공");
            
            // 2. 데이터베이스 연결
            conn = DriverManager.getConnection(DB_URL);
            System.out.println("✓ 데이터베이스 연결 성공");
            System.out.println("  - URL: " + DB_URL);
            
            // 3. SQLite PRAGMA 설정 (application.yml의 connection-init-sql)
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA foreign_keys=ON");
                stmt.execute("PRAGMA journal_mode=WAL");
                System.out.println("✓ PRAGMA 설정 완료");
            }
            
            // 4. 데이터베이스 메타데이터 확인
            System.out.println("\n[데이터베이스 정보]");
            System.out.println("  - Product Name: " + conn.getMetaData().getDatabaseProductName());
            System.out.println("  - Product Version: " + conn.getMetaData().getDatabaseProductVersion());
            System.out.println("  - Driver Name: " + conn.getMetaData().getDriverName());
            System.out.println("  - Driver Version: " + conn.getMetaData().getDriverVersion());
            
            // 5. 테이블 존재 확인
            testTableExists(conn);
            
            // 6. 데이터 조회 테스트
            testSelectData(conn);
            
            // 7. 데이터 삽입 테스트
            testInsertData(conn);
            
            // 8. 삽입 후 다시 조회
            testSelectData(conn);
            
            System.out.println("\n" + "=".repeat(50));
            System.out.println("모든 테스트 완료!");
            System.out.println("=".repeat(50));
            
        } catch (ClassNotFoundException e) {
            System.err.println("✗ JDBC 드라이버를 찾을 수 없습니다: " + e.getMessage());
            e.printStackTrace();
        } catch (SQLException e) {
            System.err.println("✗ 데이터베이스 오류 발생: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // 9. 연결 종료
            if (conn != null) {
                try {
                    conn.close();
                    System.out.println("\n✓ 데이터베이스 연결 종료");
                } catch (SQLException e) {
                    System.err.println("✗ 연결 종료 중 오류 발생: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * 테이블 존재 여부 확인
     */
    private static void testTableExists(Connection conn) throws SQLException {
        System.out.println("\n[테이블 존재 확인]");
        
        String query = "SELECT name FROM sqlite_master WHERE type='table' AND name='purchases'";
        
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            
            if (rs.next()) {
                System.out.println("✓ 'purchases' 테이블이 존재합니다.");
            } else {
                System.out.println("✗ 'purchases' 테이블이 존재하지 않습니다.");
            }
        }
    }
    
    /**
     * 데이터 조회 테스트
     */
    private static void testSelectData(Connection conn) throws SQLException {
        System.out.println("\n[데이터 조회 테스트]");
        
        String query = "SELECT id, item_name, price FROM purchases ORDER BY id";
        
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            
            int count = 0;
            System.out.println(String.format("  %-5s %-20s %-10s", "ID", "상품명", "가격"));
            System.out.println("  " + "-".repeat(40));
            
            while (rs.next()) {
                int id = rs.getInt("id");
                String itemName = rs.getString("item_name");
                int price = rs.getInt("price");
                
                System.out.println(String.format("  %-5d %-20s %-10d원", id, itemName, price));
                count++;
            }
            
            System.out.println("  " + "-".repeat(40));
            System.out.println("✓ 총 " + count + "개의 레코드 조회됨");
        }
    }
    
    /**
     * 데이터 삽입 테스트
     */
    private static void testInsertData(Connection conn) throws SQLException {
        System.out.println("\n[데이터 삽입 테스트]");
        
        String insertQuery = "INSERT INTO purchases (item_name, price) VALUES (?, ?)";
        
        try (PreparedStatement pstmt = conn.prepareStatement(insertQuery)) {
            // 현재 시간을 포함한 테스트 데이터
            String testItem = "테스트_" + System.currentTimeMillis();
            int testPrice = 5000;
            
            pstmt.setString(1, testItem);
            pstmt.setInt(2, testPrice);
            
            int affected = pstmt.executeUpdate();
            
            if (affected > 0) {
                System.out.println("✓ 데이터 삽입 성공");
                System.out.println("  - 상품명: " + testItem);
                System.out.println("  - 가격: " + testPrice + "원");
            } else {
                System.out.println("✗ 데이터 삽입 실패");
            }
        }
    }
}


package com.jiniebox.jangbogo.dev;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.jiniebox.jangbogo.dto.MallAccount;
import com.jiniebox.jangbogo.service.MallAccountYmlService;

/**
 * MallAccountYmlService 사용 예제
 * 
 * 내부 관리용으로 쇼핑몰 계정 정보를 YAML 파일에 저장하고 조회하는 예제
 */
@Component
public class MallAccountYmlExample {
    
    @Autowired
    private MallAccountYmlService mallAccountService;
    
    /**
     * 예제 실행
     */
    public void runAllExamples() {
        System.out.println("========================================");
        System.out.println("Mall Account YAML Service 예제 시작");
        System.out.println("========================================\n");
        
        try {
            example1_initializeAndCreateExample();
            example2_addAccounts();
            example3_getAllAccounts();
            example4_getSpecificAccount();
            example5_updateAccount();
            example6_getAllSites();
            example7_removeAccount();
            example8_getAccountCount();
            
            System.out.println("\n========================================");
            System.out.println("모든 예제 실행 완료!");
            System.out.println("========================================");
            
        } catch (Exception e) {
            System.err.println("예제 실행 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 예제 1: YAML 파일 초기화 및 예제 파일 생성
     */
    public void example1_initializeAndCreateExample() throws Exception {
        System.out.println("=== 예제 1: YAML 파일 초기화 ===");
        
        // YAML 파일 초기화
        mallAccountService.initializeYaml();
        System.out.println("✓ mall_account.yml 파일 초기화 완료");
        
        // 예제 파일 생성
        mallAccountService.createExampleFile();
        System.out.println("✓ mall_account.yml.example 파일 생성 완료");
        
        System.out.println();
    }
    
    /**
     * 예제 2: 계정 추가
     */
    public void example2_addAccounts() throws Exception {
        System.out.println("=== 예제 2: 계정 추가 ===");
        
        // 방법 1: MallAccount 객체 생성하여 추가
        MallAccount coupang = new MallAccount("coupang", "my_coupang_id", "coupang_pass123");
        mallAccountService.saveAccount(coupang);
        System.out.println("✓ 쿠팡 계정 추가: " + coupang);
        
        // 방법 2: 사이트명, ID, 비밀번호 직접 전달
        mallAccountService.saveAccount("gmarket", "my_gmarket_id", "gmarket_pass456");
        System.out.println("✓ 지마켓 계정 추가");
        
        mallAccountService.saveAccount("ssg", "my_ssg_id", "ssg_pass789");
        System.out.println("✓ SSG 계정 추가");
        
        mallAccountService.saveAccount("oasis", "my_oasis_id", "oasis_pass000");
        System.out.println("✓ 오아시스 계정 추가");
        
        System.out.println();
    }
    
    /**
     * 예제 3: 모든 계정 조회
     */
    public void example3_getAllAccounts() throws Exception {
        System.out.println("=== 예제 3: 모든 계정 조회 ===");
        
        List<MallAccount> accounts = mallAccountService.getAllAccounts();
        
        System.out.println("등록된 계정 목록:");
        for (MallAccount account : accounts) {
            System.out.println("  - " + account.getSite() + 
                             " | ID: " + account.getId() + 
                             " | Pass: " + maskPassword(account.getPass()));
        }
        System.out.println("총 " + accounts.size() + "개의 계정이 등록되어 있습니다.");
        
        System.out.println();
    }
    
    /**
     * 예제 4: 특정 사이트 계정 조회
     */
    public void example4_getSpecificAccount() throws Exception {
        System.out.println("=== 예제 4: 특정 사이트 계정 조회 ===");
        
        String targetSite = "coupang";
        Optional<MallAccount> account = mallAccountService.getAccount(targetSite);
        
        if (account.isPresent()) {
            MallAccount acc = account.get();
            System.out.println("사이트: " + acc.getSite());
            System.out.println("아이디: " + acc.getId());
            System.out.println("비밀번호: " + maskPassword(acc.getPass()));
        } else {
            System.out.println(targetSite + " 계정을 찾을 수 없습니다.");
        }
        
        System.out.println();
    }
    
    /**
     * 예제 5: 계정 업데이트
     */
    public void example5_updateAccount() throws Exception {
        System.out.println("=== 예제 5: 계정 업데이트 ===");
        
        String site = "gmarket";
        System.out.println(site + " 계정 업데이트 중...");
        
        // 기존 계정 조회
        Optional<MallAccount> oldAccount = mallAccountService.getAccount(site);
        if (oldAccount.isPresent()) {
            System.out.println("  기존 ID: " + oldAccount.get().getId());
        }
        
        // 계정 업데이트 (같은 사이트명으로 저장하면 자동 업데이트)
        mallAccountService.saveAccount(site, "updated_gmarket_id", "new_password_456");
        
        // 업데이트 확인
        Optional<MallAccount> newAccount = mallAccountService.getAccount(site);
        if (newAccount.isPresent()) {
            System.out.println("  새 ID: " + newAccount.get().getId());
            System.out.println("✓ 계정 업데이트 완료");
        }
        
        System.out.println();
    }
    
    /**
     * 예제 6: 등록된 사이트 목록 조회
     */
    public void example6_getAllSites() throws Exception {
        System.out.println("=== 예제 6: 등록된 사이트 목록 ===");
        
        List<String> sites = mallAccountService.getAllSites();
        
        System.out.println("등록된 사이트:");
        for (int i = 0; i < sites.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + sites.get(i));
        }
        
        System.out.println();
    }
    
    /**
     * 예제 7: 계정 삭제
     */
    public void example7_removeAccount() throws Exception {
        System.out.println("=== 예제 7: 계정 삭제 ===");
        
        String siteToDelete = "oasis";
        
        // 삭제 전 확인
        if (mallAccountService.hasAccount(siteToDelete)) {
            System.out.println(siteToDelete + " 계정이 존재합니다. 삭제를 진행합니다...");
            
            boolean removed = mallAccountService.removeAccount(siteToDelete);
            
            if (removed) {
                System.out.println("✓ " + siteToDelete + " 계정 삭제 완료");
            } else {
                System.out.println("✗ 계정 삭제 실패");
            }
        } else {
            System.out.println(siteToDelete + " 계정이 존재하지 않습니다.");
        }
        
        System.out.println();
    }
    
    /**
     * 예제 8: 계정 수 조회
     */
    public void example8_getAccountCount() throws Exception {
        System.out.println("=== 예제 8: 계정 수 조회 ===");
        
        int count = mallAccountService.getAccountCount();
        System.out.println("현재 등록된 계정 수: " + count + "개");
        
        System.out.println();
    }
    
    /**
     * 비밀번호 마스킹 처리
     */
    private String maskPassword(String password) {
        if (password == null || password.isEmpty()) {
            return "null";
        }
        if (password.length() <= 3) {
            return "***";
        }
        return password.substring(0, 3) + "***";
    }
    
    /**
     * 개별 예제 메서드들 (필요시 개별 호출 가능)
     */
    
    /**
     * 간단한 추가 예제
     */
    public void simpleAddExample() throws Exception {
        System.out.println("=== 간단한 계정 추가 예제 ===");
        
        mallAccountService.saveAccount("emart", "emart_user", "emart_pass");
        System.out.println("✓ 이마트 계정 추가 완료");
    }
    
    /**
     * 간단한 조회 예제
     */
    public void simpleGetExample() throws Exception {
        System.out.println("=== 간단한 계정 조회 예제 ===");
        
        Optional<MallAccount> account = mallAccountService.getAccount("emart");
        account.ifPresent(acc -> {
            System.out.println("사이트: " + acc.getSite());
            System.out.println("ID: " + acc.getId());
        });
    }
    
    /**
     * 여러 계정 일괄 추가 예제
     */
    public void batchAddExample() throws Exception {
        System.out.println("=== 여러 계정 일괄 추가 예제 ===");
        
        String[][] accounts = {
            {"11st", "11st_user", "11st_pass"},
            {"auction", "auction_user", "auction_pass"},
            {"homeplus", "homeplus_user", "homeplus_pass"}
        };
        
        for (String[] acc : accounts) {
            mallAccountService.saveAccount(acc[0], acc[1], acc[2]);
            System.out.println("✓ " + acc[0] + " 계정 추가");
        }
        
        System.out.println("총 " + accounts.length + "개 계정 추가 완료");
    }
    
    /**
     * 특정 패턴의 사이트 찾기 예제
     */
    public void findSitesExample() throws Exception {
        System.out.println("=== 특정 패턴의 사이트 찾기 예제 ===");
        
        String searchPattern = "market";  // "market"이 포함된 사이트 찾기
        
        List<String> sites = mallAccountService.getAllSites();
        List<String> matchedSites = sites.stream()
                .filter(site -> site.toLowerCase().contains(searchPattern.toLowerCase()))
                .toList();
        
        System.out.println("'" + searchPattern + "' 패턴과 일치하는 사이트:");
        matchedSites.forEach(site -> System.out.println("  - " + site));
        
        System.out.println("총 " + matchedSites.size() + "개 발견");
    }
}


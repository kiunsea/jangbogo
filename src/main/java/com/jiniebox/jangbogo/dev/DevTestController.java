package com.jiniebox.jangbogo.dev;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 개발용 테스트 컨트롤러
 * 예제 실행을 위한 간단한 엔드포인트 제공
 * 
 * 방법 1: 브라우저에서 테스트
 *   1. Spring Boot 애플리케이션 실행
 *   2. 브라우저에서 접속:
 *      - http://localhost:8282/dev/test-mall-account (모든 예제 실행)
 *      - http://localhost:8282/dev/simple-add (간단한 추가)
 *      - http://localhost:8282/dev/simple-get (간단한 조회)
 *   3. 콘솔 로그 확인
 * 
 * 방법 2: 코드에서 직접 사용
 *   @Autowired
 *   private MallAccountYmlService mallAccountService;
 *   // 계정 추가
 *   mallAccountService.saveAccount("coupang", "my_id", "my_pass");
 *   // 계정 조회
 *   Optional<MallAccount> account = mallAccountService.getAccount("coupang");
 *   // 모든 계정 조회
 *   List<MallAccount> accounts = mallAccountService.getAllAccounts();
 *   // 계정 삭제
 *   mallAccountService.removeAccount("coupang");
 * 
 */
@RestController
@RequestMapping("/dev")
public class DevTestController {
    
    @Autowired
    private MallAccountYmlExample mallAccountYmlExample;
    
    /**
     * 모든 예제 실행
     * GET /dev/test-mall-account
     */
    @GetMapping("/test-mall-account")
    public String testMallAccount() {
        try {
            mallAccountYmlExample.runAllExamples();
            return "✓ 모든 예제 실행 완료! 콘솔 로그를 확인하세요.";
        } catch (Exception e) {
            return "✗ 예제 실행 실패: " + e.getMessage();
        }
    }
    
    /**
     * 간단한 추가 예제
     * GET /dev/simple-add
     */
    @GetMapping("/simple-add")
    public String simpleAdd() {
        try {
            mallAccountYmlExample.simpleAddExample();
            return "✓ 계정 추가 완료!";
        } catch (Exception e) {
            return "✗ 실패: " + e.getMessage();
        }
    }
    
    /**
     * 간단한 조회 예제
     * GET /dev/simple-get
     */
    @GetMapping("/simple-get")
    public String simpleGet() {
        try {
            mallAccountYmlExample.simpleGetExample();
            return "✓ 계정 조회 완료! 콘솔 로그를 확인하세요.";
        } catch (Exception e) {
            return "✗ 실패: " + e.getMessage();
        }
    }
}


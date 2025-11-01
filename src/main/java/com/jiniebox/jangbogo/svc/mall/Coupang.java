package com.jiniebox.jangbogo.svc.mall;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.openqa.selenium.WebDriver;

import com.jiniebox.jangbogo.svc.ifc.MallSession;
import com.jiniebox.jangbogo.svc.ifc.PurchasedCollector;

/**
 * - 2024.08.28
 * 현재 쿠팡에서는 웹크롤링을 막고 있다. 
 * 이를 우회할 방법을 찾지 못하여 쿠팡 크롤링 개발은 중지한다.
 * 때문에 현재 클래스는 우선 보류후 추후 파기하도록 한다.
 * @author KIUNSEA
 *
 */
public class Coupang extends MallSession implements PurchasedCollector {
    
    private Logger log = LogManager.getLogger(Coupang.class);

    /**
     * @param id
     * @param pass
     */
    public Coupang(String id, String pass) {
        super(id, pass);
    }

    @Override
    public JSONArray navigatePurchased(WebDriver driver) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public JSONArray getItems() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean signin(WebDriver driver) {
        driver.get("https://www.coupang.com");
        this.delayTime(1500);
        
//        WebElement btnLogin = driver.findElement(By.cssSelector("#login > a"));
//        btnLogin.click();
//        this.delayTime(1500);
//
//        // 로그인 시작
//        driver.findElement(By.xpath("//*[@id='login-email-input']")).sendKeys(this.USER_ID);
//        driver.findElement(By.xpath("//*[@id='login-password-input']")).sendKeys(this.USER_PASS);
//        
//        WebElement elemLogin = driver.findElement(By.xpath("//*[@id='memberLogin']/div[1]/form/div[5]/button"));
//        elemLogin.click(); // 로그인 버튼 클릭
//        
//        this.delayTime(3000); // 페이지 이동후엔 세션 유지를 위해 지연시간이 필요하다
//        
//        //로그인 성공 여부 확인
//        WebElement elemMyCoupang = driver.findElement(By.cssSelector("#header > section > div.clearFix.search-form-wrap > ul > li.my-coupang.more"));
//        
//        if (elemMyCoupang != null) {
//            log.debug("[button element] onclick ->>>>>> " + elemMyCoupang);
//            return true;
//        }
        
        return false;
    }

    @Override
    public void signout(WebDriver driver) {
        log.debug("쇼핑몰에서 로그아웃");
    }

}

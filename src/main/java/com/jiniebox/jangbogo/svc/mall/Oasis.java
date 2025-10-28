package com.jiniebox.jangbogo.svc.mall;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import com.jiniebox.jangbogo.svc.ifc.MallSession;
import com.jiniebox.jangbogo.svc.ifc.PurchasedCollector;
import com.jiniebox.jangbogo.svc.util.WebDriverManager;
import com.jiniebox.jangbogo.util.ExceptionUtil;
import com.jiniebox.jangbogo.util.JinieboxUtil;

public class Oasis extends MallSession implements PurchasedCollector {

    private Logger log = LogManager.getLogger(Oasis.class);
    private String mallName = "오아시스마켓";

    /**
     * @param id
     * @param pass
     */
    public Oasis(String id, String pass) {
        super(id, pass);
    }

    @Override
    public JSONArray getItems() {
        JSONArray resArr = null;

        WebDriverManager wdm = new WebDriverManager();
        WebDriver driver = wdm.getWebDriver();

        try {
            if (driver != null && this.signin(driver)) {

                this.delayTime(1500);

                /**
                 * 데이터 수집
                 */
                resArr = this.navigatePurchased(driver);

                // 마무리
                this.signout(driver);
            }
        } catch (Exception e) {
            log.error(ExceptionUtil.getExceptionInfo(e));
        } finally {
            driver.quit();
        }

        if (resArr == null) {
            resArr = new JSONArray();
        }

        return resArr;
    }

    @Override
    public boolean signin(WebDriver driver) {
        driver.get("https://www.oasis.co.kr/login");
        this.delayTime(1500);

        // 로그인 시작
        driver.findElement(By.id("userId")).sendKeys(this.USER_ID);
        driver.findElement(By.id("password")).sendKeys(this.USER_PASS);
        
        WebElement elemLogin = driver.findElement(By.cssSelector("#sec_login > div.loginTabCont.idCont > div > form > div.btn_login > a"));
        if (WebDriverManager.isEdge(driver)) {
            elemLogin = driver.findElement(By.cssSelector("#sec_login > div.loginTabCont.idCont > div > form > div.btn_login.on > a"));
        }
        elemLogin.click(); // 로그인 버튼 클릭
        this.delayTime(3000); // 페이지 이동후엔 세션 유지를 위해 지연시간이 필요하다

        driver.navigate().to("https://www.oasis.co.kr/myPage/main");
        this.delayTime(1500);

        // 로그인 성공 여부 확인
        WebElement btnLogout = driver.findElement(
                By.cssSelector("#header > div.header_area > div > div.tMenu > ul.tMenu_unb > li:nth-child(1) > a"));

        if ("로그아웃".equals(btnLogout.getText())) {
            log.debug("[signin success!!] >>>>>> ");
            return true;
        }

        return false;
    }

    @Override
    public void signout(WebDriver driver) {
        // 오아시스 로그아웃
        driver.navigate().to("https://www.oasis.co.kr/logout");
    }

    @Override
    public JSONArray navigatePurchased(WebDriver driver) {

        JSONArray resJsonArr = new JSONArray();
        JSONObject orderJson = null;
        JSONArray itemJsonArr = null;
        JSONObject itemJson = null;

        // 구매 내역
        driver.navigate().to("https://www.oasis.co.kr/myPage/orderList");
        
        String mainWindowHandle = driver.getWindowHandle(); //구매내역을 메인으로
        
        WebElement orderListOuter = driver.findElement(By.cssSelector("div.mypage-orderinfo-wrap"));
        List<WebElement> elems_orderList_div = orderListOuter.findElements(By.xpath("//div[@class='mypageOrderstatus']"));
        Iterator<WebElement> olIter = elems_orderList_div.iterator();
        while (olIter.hasNext()) {
            WebElement orderDiv = (WebElement) olIter.next();
            
            orderJson = new JSONObject();
            
            WebElement orderNum = (WebElement) orderDiv.findElement(By.cssSelector("div.orderBoxInfo > div > span"));
            String serial = orderNum.getText();
			if ((serial.length() > 2) && (serial.indexOf('(') > -1)) {
				serial = serial.trim();
				serial = serial.substring(1, serial.length() - 1);
			}
            orderJson.put("serial", serial);
            log.debug("Order Serial:" + serial);
            
            WebElement detailA = (WebElement) orderDiv.findElement(By.cssSelector("div.productArea > div > div.orderProduct > a"));
            String link = detailA.getAttribute("href");
            
            orderJson.put("mallname", this.mallName);
            
            //상세페이지 오픈
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("window.open(arguments[0])", link);

            Set<String> allWindows = driver.getWindowHandles();
            for (String windowHandle : allWindows) {
                if (!windowHandle.equals(mainWindowHandle)) {
                    driver.switchTo().window(windowHandle);
                    break;
                }
            }
            
            //상세페이지 조회
            this.delayTime(1500);
            itemJsonArr = new JSONArray();
            
            WebElement orderDate = (WebElement) driver.findElement(By.cssSelector("div.orderBoxInfo > strong"));
            String dateTxt = orderDate.getText().trim();
            orderJson.put("datetime", JinieboxUtil.delDatedot(dateTxt));
            
            WebElement itemsOuter = driver.findElement(By.cssSelector("div.productWrap"));
            List<WebElement> elem_itemList_div = itemsOuter.findElements(By.xpath("//div[@class='product']"));
            Iterator<WebElement> itemIter = elem_itemList_div.iterator();
            while (itemIter.hasNext()) {
                WebElement itemDiv = (WebElement) itemIter.next();
                itemJson = new JSONObject();

                WebElement elemName = (WebElement) itemDiv.findElement(By.cssSelector("div.orderProduct > a > div > span > em"));
                itemJson.put("name", elemName.getText());

                WebElement elemCntSpan = (WebElement) itemDiv.findElement(By.cssSelector("p.orderCount"));
                String qtyStr = elemCntSpan.getText();
                itemJson.put("qty", qtyStr);

                try {
                    WebElement elemPriceSpan = (WebElement) itemDiv.findElement(By.cssSelector("div.orderBuyPrice > span.priceAfter > em:nth-child(2)"));
                    itemJson.put("price", elemPriceSpan.getText());
                } catch (NoSuchElementException e) {
                    // 자식 요소가 없는 경우 예외 처리
                    log.debug("Child element not found within the parent element.");
                }

                itemJsonArr.add(itemJson);
            }
            orderJson.put("items", itemJsonArr);
            resJsonArr.add(orderJson);
            driver.close();

            // 메인으로 복귀
            driver.switchTo().window(mainWindowHandle);
            this.delayTime(1500);
        }

        return resJsonArr;
    }

}

package com.jiniebox.jangbogo.svc.mall;

import com.jiniebox.jangbogo.svc.ifc.MallSession;
import com.jiniebox.jangbogo.svc.ifc.PurchasedCollector;
import com.jiniebox.jangbogo.svc.util.WebDriverManager;
import com.jiniebox.jangbogo.util.ExceptionUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

public class Hanaro extends MallSession implements PurchasedCollector {

  private Logger log = LogManager.getLogger(Hanaro.class);

  /**
   * @param id
   * @param pass
   */
  public Hanaro(String id, String pass) {
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

        /** 데이터 수집 */
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
    driver.get("https://www.nonghyupmall.com");
    this.delayTime(2000);

    driver.navigate().to("https://www.nonghyupmall.com/BC41000R/loginViewPage.nh");
    this.delayTime(2000);

    // 로그인 시작
    driver.findElement(By.id("userID")).sendKeys(this.USER_ID);
    driver.findElement(By.id("password")).sendKeys(this.USER_PASS);

    WebElement elemLogin =
        driver.findElement(
            By.cssSelector(
                "#loginForm > div.inner > div > div.login-box > div.login-form > button"));
    elemLogin.click(); // 로그인 버튼 클릭

    this.delayTime(2000); // 페이지 이동후엔 세션 유지를 위해 지연시간이 필요하다

    // 로그인 성공 여부 확인
    WebElement btnLogout = driver.findElement(By.id("a_id_logout"));

    if ("로그아웃".equals(btnLogout.getText())) {
      log.debug("로그인 성공");
      return true;
    }

    return false;
  }

  @Override
  public void signout(WebDriver driver) {
    // TODO Auto-generated method stub

  }

  @Override
  public JSONArray navigatePurchased(WebDriver driver) {
    // TODO Auto-generated method stub
    return null;
  }
}

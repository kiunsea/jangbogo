package com.jiniebox.jangbogo.svc.ifc;

import com.jiniebox.jangbogo.util.ExceptionUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;

/**
 * 쇼핑몰 접속 정보 관리 클래스
 *
 * @author KIUNSEA
 */
public abstract class MallSession {

  private static final Logger logger = LogManager.getLogger(MallSession.class);

  protected String USER_ID, USER_PASS;

  public MallSession(String id, String pass) {
    this.USER_ID = id;
    this.USER_PASS = pass;
  }

  /**
   * 쇼핑몰에서 구매한 상품들 목록을 수집하여 반환한다. (전자영수증과 구매내역들을 분석한 결과)
   *
   * @return 데이터 구조 > <br>
   *     [{ serial:"String", datetime:"YYYYMMDD", mallname:"String", items:[{ price:"String",
   *     name:"String", qty:"Integer", sum:"String" }] }]
   */
  public abstract JSONArray getItems();

  /**
   * 쇼핑몰에 로그인 처리한다.
   *
   * @param driver
   * @return 성공 여부
   */
  public abstract boolean signin(WebDriver driver)
      throws NoSuchElementException,
          StaleElementReferenceException,
          WebDriverException,
          NullPointerException;

  /**
   * 쇼핑몰에 로그아웃 처리한다.
   *
   * @param driver
   */
  public abstract void signout(WebDriver driver);

  /**
   * 페이지 이동전에 세션 유지를 위해 지연시간이 필요하다 (2초 이상)
   *
   * @param millisecond (1000 -> 1초)
   */
  protected void delayTime(long millisecond) {
    try {
      Thread.sleep(millisecond);
    } catch (InterruptedException e) {
      logger.error(ExceptionUtil.getExceptionInfo(e));
    }
  }
}

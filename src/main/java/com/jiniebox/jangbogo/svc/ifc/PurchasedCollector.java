package com.jiniebox.jangbogo.svc.ifc;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.openqa.selenium.WebDriver;

/**
 * 구매내역 수집기 (종이 영수증이 아닌 웹문서로 내역을 직접 출력하는 쇼핑몰용)
 * @author KIUNSEA
 *
 */
public interface PurchasedCollector {

    /**
     * 구매내역 페이지를 추적후 분석하여 결과를 반환한다.
     * @param driver
     * @return 분석 결과 구매목록
     */
    public JSONArray navigatePurchased(WebDriver driver);
}

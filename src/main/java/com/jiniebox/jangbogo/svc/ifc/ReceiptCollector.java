package com.jiniebox.jangbogo.svc.ifc;

import java.util.List;

import org.json.simple.JSONObject;
import org.openqa.selenium.WebDriver;

/**
 * 종이영수증 내역을 분석하고 수집
 * @author KIUNSEA
 *
 */
public interface ReceiptCollector {

    /**
     * 전자영수증 페이지를 추적한다.
     * @param driver
     * @return 분석 결과 구매목록
     * @throws InterruptedException 
     */
    public List<JSONObject> navigateReceipt(WebDriver driver);
    /**
     * 전자영수증 내용을 분석하여 결과를 반환한다.
     * @param content
     * @return 분석 결과 구매정보
     */
    public JSONObject parseReceipt(String content);
}

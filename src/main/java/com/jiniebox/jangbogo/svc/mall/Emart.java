package com.jiniebox.jangbogo.svc.mall;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.UnhandledAlertException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import com.jiniebox.jangbogo.svc.ifc.MallSession;
import com.jiniebox.jangbogo.svc.ifc.ReceiptCollector;
import com.jiniebox.jangbogo.svc.util.WebDriverManager;
import com.jiniebox.jangbogo.util.ExceptionUtil;
import com.jiniebox.jangbogo.util.NumberUtil;

/**
 * https://eapp.emart.com 을 조회하는 클래스이다. ('이마트', '트레이더스', '노브랜드' 의 오프라인 매장 구매 내역)
 * 구매일에서 2일이 지나면 MY SSG(ssg.com) 에서도 매장 구매 내역 확인이 가능하다. (하지만 '노브랜드' 는 MY SSG 에서 조회 안됨)
 * 때문에 빠른 조회를 위해 현재 클래스에서 매장 구매 내역을 취한다.
 * https://www.omnibuscode.com/board/PRJ_SOBA/60320
 * 
 * @author KIUNSEA
 *
 */
public class Emart extends MallSession implements ReceiptCollector {

    private static final Logger logger = LogManager.getLogger(Emart.class);
    
    private Set<String> colNameKeys = new HashSet<String>(); // 영수증에서 아이템 라인인지 확인하기 위한 컬럼셋 정보
    
    /**
     * @param id
     * @param pass
     */
    public Emart(String id, String pass) {
        super(id, pass);
        
        colNameKeys.add("상 품 명");
        colNameKeys.add("단  가");
        colNameKeys.add("수량");
        colNameKeys.add("금  액");
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
                resArr = this.navigateReceipt(driver);

                // 마무리
                this.signout(driver);
            }
        } catch (Exception e) {
            logger.error(ExceptionUtil.getExceptionInfo(e));
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

        driver.get("https://eapp.emart.com/login/login.do?retUrl=/webapp/my?mallType=E");
        this.delayTime(1500);
        String mainWindowHandle = driver.getWindowHandle();

        // 로그인 시작
        driver.findElement(By.id("userId")).sendKeys(this.USER_ID);
        driver.findElement(By.id("userPw")).sendKeys(this.USER_PASS);
        driver.findElement(By.id("loginBtn")).click(); // 로그인 버튼 클릭

        // reCAPTCHA 감지
        boolean isCaptchaPresent = false;
        try {
            WebElement element = driver.findElement(By.cssSelector("iframe"));
            if (element != null) {
                driver.switchTo().frame(element);
                isCaptchaPresent = driver.findElements(By.cssSelector(".recaptcha-checkbox-checkmark")).size() > 0;
            }
            driver.switchTo().window(mainWindowHandle);
        } catch (UnhandledAlertException uae) {
            logger.error(uae.getMessage());
        }
        if (isCaptchaPresent) {
            logger.debug("!!!!!!!!!!!!!!!!!!!!!!!!!!!!! reCAPTCHA 발견 ㅜ.ㅜ");
            return false;
        }
        
        this.delayTime(3000);
        
        //로그인 성공 여부 확인
        WebElement bodyElement = driver.findElement(By.tagName("body"));
        String elemDataBody = bodyElement.getAttribute("data-body");
        logger.debug("[body element] data-body ->>>>>> " + elemDataBody);
        
        this.delayTime(3000);
        
        WebElement elemLogoutButton = driver.findElement(By.xpath("//button[contains(text(), '로그아웃')]"));
        String elemOnclick = elemLogoutButton.getAttribute("onclick");
        
        if (elemDataBody != null && "mypage".equals(elemDataBody)) {
            if (elemLogoutButton != null && "logout();".equals(elemOnclick)) {
                logger.debug("[button element] onclick ->>>>>> " + elemOnclick);
                return true;
            }
        }
        
        return false;
    }

    @Override
    public void signout(WebDriver driver) {
        this.delayTime(2000);
        
        driver.navigate().to("https://eapp.emart.com/webapp/my?mallType=E&trcknCode=menu_my");
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("logout();"); // call javascrip funtion
        this.delayTime(2000);
        Alert alert = driver.switchTo().alert();
        alert.accept(); // 확인 버튼 클릭
    }

    @Override
    public JSONArray navigateReceipt(WebDriver driver) {

        JSONArray resJsonArr = new JSONArray();
        
        String mainWindowHandle = driver.getWindowHandle();
        JavascriptExecutor js = (JavascriptExecutor) driver;
        this.delayTime(2000);
        
        // 이마트 모바일 영수증
        driver.navigate().to("https://eapp.emart.com/myemart/jornalV3.do?jornalGbn=E");
        this.delayTime(2000);
        resJsonArr.addAll(this.inspectReceipts(driver, mainWindowHandle, js));

        // 트레이더스 모바일 영수증
        driver.navigate().to("https://eapp.emart.com/myemart/jornalV3.do?jornalGbn=T");
        this.delayTime(2000);
        resJsonArr.addAll(this.inspectReceipts(driver, mainWindowHandle, js));

        return resJsonArr;
    }
    
    /**
     * 영수증 목록 페이지를 조회
     * @param driver
     * @param mainWindowHandle
     * @param js
     * @return
     */
    private JSONArray inspectReceipts(WebDriver driver, String mainWindowHandle, JavascriptExecutor js) {
        
        JSONArray resJsonArr = new JSONArray();
        
        WebElement ul_elem = null;
        List<WebElement> li_elems = null;
        WebElement a_elem, name_elem = null;
        String liTxt, onClickStr, mallName = null;

        WebElement btn_prev = null;
        for (int i = 0; i < 3; i++) {
            ul_elem = driver.findElement(By.id("receipt_list"));
            li_elems = ul_elem.findElements(By.tagName("li"));
            liTxt = li_elems.get(0).getText();
            
            if (liTxt.indexOf("영수증 내역이 없습니다") < 0) {
                
                for (WebElement li_elem : li_elems) {
                    
                    a_elem = li_elem.findElement(By.tagName("a"));
                    name_elem = a_elem.findElement(By.xpath(".//div[@class='left']/div[@class='name']"));
                    mallName = name_elem != null ? name_elem.getText() : null;
                    if (mallName != null 
                            && (mallName.indexOf("이마트") > -1 
                                    || mallName.indexOf("트레이더스") > -1
                                    || mallName.indexOf("노브랜드") > -1)) { // 이마트, 트레이더스, 노브랜드 상품 구매 내역에 대해서 조회
                        
                        onClickStr = a_elem.getAttribute("onclick");
                        js.executeScript(onClickStr); // call javascrip funtion
                        this.delayTime(2000);

                        String receiptBarcode = null;
                        for (String handle : driver.getWindowHandles()) {
                            if (!handle.equals(mainWindowHandle)) {
                                // 팝업창으로 스위치
                                driver.switchTo().window(handle);

                                // 영수증 바코드 출력 (id가 'barcodeTargetRec'인 div 요소의 하위 div 요소들 선택)
                                List<WebElement> divElements = driver.findElements(By.cssSelector("#barcodeTargetRec div"));
                                if (divElements.size() > 1) {
                                    // 마지막 div 요소의 텍스트 값 가져오기
                                    receiptBarcode = divElements.get(divElements.size() - 1).getText();
                                }

                                // 상품 구매내역 추출
                                WebElement preElem = driver.findElement(By.tagName("pre"));
                                String receiptDetail = preElem.getText();
                                JSONObject detailJo = this.parseReceipt(receiptDetail);
                                if (receiptBarcode != null) {
                                    detailJo.put("serial", receiptBarcode);
                                    detailJo.put("datetime", receiptBarcode.substring(0, 8));
                                }
                                detailJo.put("mallname", mallName);
                                
                                resJsonArr.add(detailJo);
                                
                                // 팝업창 닫기
                                driver.close();
                            }
                        }
                    }

                    // 부모 창으로 다시 스위치
                    driver.switchTo().window(mainWindowHandle);
                }
            }
            
            btn_prev = driver.findElement(By.cssSelector(".btn-prev-month"));
            btn_prev.click();
        }
        
        return resJsonArr;
    }

    @Override
    public JSONObject parseReceipt(String content) {
        String[] rdArray = content.split("\\n");

        String colNameRow = null;
        List<String> itemRows = new ArrayList<String>();
        int divisionCnt = 0;
        for (String line : rdArray) {
            if (line.indexOf("-----------------------") > -1) {
                divisionCnt++;
                continue;
            }

            if (divisionCnt == 1) {
                colNameRow = line.trim(); // 항목명 라인 설정
            } else if (divisionCnt == 2) {
                itemRows.add(line.trim()); // 아이템 목록 저장
            } else if (divisionCnt == 3) {
                break;
            }
        }
        
        Set<String> cancelKeys = new HashSet<String>();
        cancelKeys.add("품목 수량");
        cancelKeys.add("면 세  물 품");
        cancelKeys.add("과 세  물 품");
        cancelKeys.add("부   가   세");
        cancelKeys.add("합        계");
        cancelKeys.add("결 제 대 상");
        
        List<String> newRows = new ArrayList<String>();
        String line = null;
        for (int i = 0; i < itemRows.size(); i++) {
            line = itemRows.get(i);
            
            if (line.trim().length() < 1) {
                //row 값이 없는 경우 skip
                continue;
            } else {
                boolean skipRow = false;
                for (String cancelKey:cancelKeys) {
                    if (NumberUtil.isNumber(line) || line.indexOf(cancelKey) > -1) {
                        //아이템 정보가 아닌 row 는 skip
                        skipRow = true;
                        break;
                    }
                }
                
                if (skipRow)
                    continue;
            }
            
            //row내의 컬럼값이 공백인지 검사하여 재작성
            StringBuffer newLine = new StringBuffer();
            String[] itemInfo = line.split("   ");
            for (String part : itemInfo) {
                if (part.trim().length() > 0) {
                    newLine.append("  " + part.trim());
                }
            }
            newRows.add(newLine.toString().trim());
        }
        itemRows = this.combineExtraPattern01(newRows);
        
        /*
         * json 작성
         */
        JSONObject receiptJson = new JSONObject();
        JSONArray itemArr = new JSONArray();
        JSONObject itemJson = null;
        for (String itemRow : itemRows) {
            itemJson = new JSONObject();
            String[] itemInfoArr = itemRow.trim().split("  ");
            int arrSize = itemInfoArr.length;
            
            if (arrSize > 3 && arrSize < 5) {
                if (itemInfoArr[1].indexOf("-") > -1) {
                    // 할인 정보의 경우 skip (ex> "[농식품부 할인지원] -600")
                    continue;
                }

                itemJson.put("name", itemInfoArr[0]);
                if (arrSize > 1)
                    itemJson.put("price", itemInfoArr[1]);
                if (arrSize > 2)
                    itemJson.put("qty", itemInfoArr[2]);
                if (arrSize > 3)
                    itemJson.put("sum", itemInfoArr[3]);
            } else if (arrSize >= 5) {
                // "* 매일우유 오리지널2입 5,720 1 5,720" 형태 대응
                itemJson.put("name", itemInfoArr[1]);
                itemJson.put("price", itemInfoArr[2]);
                itemJson.put("qty", itemInfoArr[3]);
                itemJson.put("sum", itemInfoArr[4]);
            }
            
            itemArr.add(itemJson);
        }
        receiptJson.put("items", itemArr);
        
        
        return receiptJson;
    }
    
    /**
     * 구매 상품 목록 포맷이 예외 패턴인지 검사하여 재조합
     * 위와 같이 줄바꿈 형태인 경우 (상품명과 나머지 데이터의 출력 라인이 다름)
     * 라인으로 분리된 데이터를 결합하는 동작을 한다
     * @return List 재조합된 목록
     */
    private List<String> combineExtraPattern01(List<String> itemRows) {
        
        if (itemRows.size() < 1)
            return itemRows;
        
        List<String> newRows = new ArrayList<String>();
        String[] firstLineParts = itemRows.get(0).toString().trim().split("  ");
        if (firstLineParts[0].trim().length() > 1 && NumberUtil.isNumber(firstLineParts[0].trim().substring(0, 2))) {
            String[] itemRow01, itemRow02 = null;
            for (int i = 0; i < itemRows.size(); i++) {
                if ((i + 1) < itemRows.size()) {
                    
                    itemRow01 = itemRows.get(i).toString().trim().split("  ");
                    itemRow02 = itemRows.get(i + 1).toString().trim().split("  ");
                    
                    if ((itemRow01.length < itemRow02.length) && itemRow02.length > 3) {
                        /**
                         * 상품명이 나오고 다음 라인에 나머지 정보가 나옴
                         * "01  해태 자유시간아몬드"
                         * "8801019207655        800    1         800"
                         */
                        newRows.add(((itemRow01.length > 1) ? itemRow01[1] : itemRow01[0]) 
                                + "  " + itemRow02[1] 
                                + "  " + itemRow02[2] 
                                + "  " + itemRow02[3]);
                        i++;
                    } else if ((itemRow01.length > itemRow02.length) && itemRow01.length > 3) {
                        /**
                         * 상품명이 나머지 정보들의 다음 라인에 나옴
                         * "8806078813199     6,900   1     6,900"
                         * "20N매일쓰는위생적인"
                         */
                        newRows.add(((itemRow02.length > 1) ? itemRow02[1] : itemRow02[0]) 
                                + "  " + itemRow01[1] 
                                + "  " + itemRow01[2] 
                                + "  " + itemRow01[3]);
                        i++;
                    } else {
                        newRows.add(itemRows.get(i));
                    }
                    
                } else {
                    newRows.add(itemRows.get(i));
                }
            }
            return newRows;
        }

        return itemRows;
    }

}

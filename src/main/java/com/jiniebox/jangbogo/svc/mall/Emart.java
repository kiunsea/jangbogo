package com.jiniebox.jangbogo.svc.mall;

import com.jiniebox.jangbogo.svc.ifc.MallSession;
import com.jiniebox.jangbogo.svc.ifc.ReceiptCollector;
import com.jiniebox.jangbogo.svc.util.ClickUtil;
import com.jiniebox.jangbogo.svc.util.CollectStep;
import com.jiniebox.jangbogo.svc.util.ErrorSummary;
import com.jiniebox.jangbogo.svc.util.WebDriverManager;
import com.jiniebox.jangbogo.util.NumberUtil;
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

/**
 * https://eapp.emart.com 을 조회하는 클래스이다. ('이마트', '트레이더스', '노브랜드' 의 오프라인 매장 구매 내역) 구매일에서 2일이 지나면 MY
 * SSG(ssg.com) 에서도 매장 구매 내역 확인이 가능하다. (하지만 '노브랜드' 는 MY SSG 에서 조회 안됨) 때문에 빠른 조회를 위해 현재 클래스에서 매장 구매
 * 내역을 취한다. https://www.omnibuscode.com/board/PRJ_SOBA/60320
 *
 * @author KIUNSEA
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
      if (driver == null) {
        throw CollectStep.wrap(
            null, "EMART", "init-webdriver", null, new IllegalStateException("WebDriver 생성 실패"));
      }
      boolean signedIn = CollectStep.call(driver, "EMART", "signin", () -> this.signin(driver));
      if (!signedIn) {
        throw CollectStep.wrap(
            driver,
            "EMART",
            "signin",
            null,
            new IllegalStateException("로그인 실패 — 자격증명 또는 사이트 구조 변경 가능성"));
      }

      this.delayTime(1500);

      /** 데이터 수집 */
      resArr =
          CollectStep.call(driver, "EMART", "navigateReceipt", () -> this.navigateReceipt(driver));

      // 마무리
      try {
        this.signout(driver);
      } catch (Exception ignore) {
        logger.warn("Emart 로그아웃 중 오류(무시): {}", ignore.getMessage());
      }
    } finally {
      try {
        if (driver != null) driver.quit();
      } catch (Exception ignore) {
      }
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
    ClickUtil.safeClick(driver, By.id("loginBtn")); // 로그인 버튼 클릭 (프로모션 배너 오버레이 대응)

    // reCAPTCHA 감지
    boolean isCaptchaPresent = false;
    try {
      WebElement element = driver.findElement(By.cssSelector("iframe"));
      if (element != null) {
        driver.switchTo().frame(element);
        isCaptchaPresent =
            driver.findElements(By.cssSelector(".recaptcha-checkbox-checkmark")).size() > 0;
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

    // 로그인 성공 여부 확인
    WebElement bodyElement = driver.findElement(By.tagName("body"));
    String elemDataBody = bodyElement.getAttribute("data-body");
    logger.debug("[body element] data-body ->>>>>> " + elemDataBody);

    this.delayTime(3000);

    WebElement elemLogoutButton =
        driver.findElement(By.xpath("//button[contains(text(), '로그아웃')]"));
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

  /** 모바일 영수증 페이지. {@code jornalGbn} 이 E 면 이마트, T 면 트레이더스다. */
  private static final String JOURNAL_URL = "https://eapp.emart.com/myemart/jornalV3.do?jornalGbn=";

  @Override
  public JSONArray navigateReceipt(WebDriver driver) {

    JSONArray resJsonArr = new JSONArray();

    String mainWindowHandle = driver.getWindowHandle();
    JavascriptExecutor js = (JavascriptExecutor) driver;
    this.delayTime(2000);

    // 두 영수증 원본은 서로 독립적이다. 한쪽이 죽어도 다른 쪽 결과는 살린다 (B-2).
    //
    // 이전에는 두 호출이 한 줄로 이어져 있어서, 트레이더스 페이지가 응답하지 않으면 그 예외가
    // 그대로 올라가며 <b>이미 모아 둔 이마트 결과가 통째로 버려졌다.</b> resJsonArr 이 반환되지
    // 못하기 때문이다. MallOrderUpdater 가 SSG·Emart 사이에 이미 적용해 둔 부분 실패 격리가
    // 이 한 겹 아래에는 없었다.
    List<RuntimeException> failures = new ArrayList<>();
    resJsonArr.addAll(collectJournal(driver, mainWindowHandle, js, "E", "이마트", failures));
    resJsonArr.addAll(collectJournal(driver, mainWindowHandle, js, "T", "트레이더스", failures));

    // 둘 다 실패했을 때만 이번 수집을 실패로 올린다. 한쪽이라도 건졌으면 그건 결과다.
    if (failures.size() == 2) {
      throw failures.get(0);
    }

    return resJsonArr;
  }

  /**
   * 영수증 원본 하나(이마트 또는 트레이더스)를 수집한다. 실패해도 예외를 전파하지 않고 {@code failures} 에 쌓는다.
   *
   * <p>실패를 삼키는 것이 아니다 — 호출측이 둘 다 실패했는지 보고 판단하며, 부분 실패는 로그에 남는다.
   *
   * @param gbn {@code jornalGbn} 값 (E / T)
   * @param label 로그용 이름
   * @param failures 실패 누적 목록
   * @return 수집 결과 (실패 시 빈 배열)
   */
  private JSONArray collectJournal(
      WebDriver driver,
      String mainWindowHandle,
      JavascriptExecutor js,
      String gbn,
      String label,
      List<RuntimeException> failures) {
    try {
      driver.navigate().to(JOURNAL_URL + gbn);
      this.delayTime(2000);
      return this.inspectReceipts(driver, mainWindowHandle, js);
    } catch (Exception e) {
      logger.error("{} 영수증 수집 실패 (다른 쪽은 계속 진행) - 원인: {}", label, ErrorSummary.summarize(e));
      failures.add(
          e instanceof RuntimeException re
              ? re
              : new IllegalStateException(label + " 영수증 수집 실패", e));
      return new JSONArray();
    }
  }

  /**
   * 영수증 목록 페이지를 조회
   *
   * @param driver
   * @param mainWindowHandle
   * @param js
   * @return
   */
  private JSONArray inspectReceipts(
      WebDriver driver, String mainWindowHandle, JavascriptExecutor js) {

    JSONArray resJsonArr = new JSONArray();

    WebElement ul_elem = null;
    List<WebElement> li_elems = null;
    WebElement a_elem, name_elem = null;
    String liTxt, onClickStr, mallName = null;

    WebElement btn_prev = null;
    for (int i = 0; i < 3; i++) {
      ul_elem = driver.findElement(By.id("receipt_list"));
      li_elems = ul_elem.findElements(By.tagName("li"));

      // 영수증 목록이 비어있는 경우 처리
      if (li_elems == null || li_elems.isEmpty()) {
        logger.debug("영수증 목록이 비어있습니다. 다음 페이지로 이동합니다.");
        try {
          btn_prev = driver.findElement(By.cssSelector(".btn-prev-month"));
          ClickUtil.safeClick(driver, btn_prev, ".btn-prev-month");
          this.delayTime(2000);
        } catch (Exception e) {
          logger.debug("이전 달 버튼을 찾을 수 없습니다. 루프를 종료합니다.");
          break;
        }
        continue;
      }

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

                // 영수증 바코드 추출 (id가 'barcodeTargetRec' 인 div 의 하위 div 들)
                List<WebElement> divElements =
                    driver.findElements(By.cssSelector("#barcodeTargetRec div"));
                List<String> barcodeTexts = new ArrayList<>();
                for (WebElement div : divElements) {
                  try {
                    barcodeTexts.add(div.getText());
                  } catch (Exception e) {
                    barcodeTexts.add(null); // 개별 요소를 못 읽어도 나머지 후보는 살린다
                  }
                }
                receiptBarcode = extractReceiptBarcode(barcodeTexts);

                // 상품 구매내역 추출
                WebElement preElem = driver.findElement(By.tagName("pre"));
                String receiptDetail = preElem.getText();
                JSONObject detailJo = this.parseReceipt(receiptDetail);
                if (receiptBarcode != null) {
                  detailJo.put("serial", receiptBarcode);
                  detailJo.put("datetime", receiptBarcode.substring(0, 8));
                } else {
                  // 구 템플릿(A)에서 못 읽었다. 신 템플릿 경로를 차례로 시도한다 (B-3 해결).
                  //
                  // 이마트가 영수증 화면을 개편해 ern- 접두 템플릿이 섞여 내려온다. 그 템플릿에는
                  // #barcodeTargetRec 이 아예 없고, 대신 바코드 요소(#barcode-no / #ern-barcode)와
                  // 분해된 식별 필드(biz_date / pos_str_code / pos_no / tran_no)가 있다.
                  List<String> newTemplateTexts = new ArrayList<>();
                  for (String selector : NEW_TEMPLATE_BARCODE_SELECTORS) {
                    newTemplateTexts.add(readElementValue(driver, selector));
                  }
                  String fromNewBarcode = extractReceiptBarcode(newTemplateTexts); // 경로 A2
                  receiptBarcode = fromNewBarcode;

                  String[] fields = new String[NEW_TEMPLATE_FIELD_SELECTORS.length];
                  for (int f = 0; f < NEW_TEMPLATE_FIELD_SELECTORS.length; f++) {
                    fields[f] = readElementValue(driver, NEW_TEMPLATE_FIELD_SELECTORS[f]);
                  }
                  String composed =
                      composeSerialFromFields(fields[0], fields[1], fields[2], fields[3]); // 경로 B

                  String source = null;
                  if (receiptBarcode != null) {
                    source = "A2(신 템플릿 바코드 요소)";
                  } else if (composed != null) {
                    receiptBarcode = composed;
                    source = "B(필드 조합)";
                  }

                  if (receiptBarcode != null) {
                    detailJo.put("serial", receiptBarcode);
                    detailJo.put("datetime", receiptBarcode.substring(0, 8));
                    // 값은 남기지 않는다 — 자릿수와 출처만 남겨 회귀를 알아볼 수 있게 한다.
                    logger.info(
                        "구 템플릿 바코드 부재 — {} 로 복구했다. mall={}, serial 자릿수={}, 경로 대조={}",
                        source,
                        mallName,
                        receiptBarcode.length(),
                        describeRecoveryAgreement(fromNewBarcode, composed));
                  }
                }

                if (receiptBarcode == null) {
                  // serial·datetime 이 없으면 저장 단계에서 조용히 버려진다(MallOrderUpdaterRunner 가
                  // datetime 없는 주문을 skippedOrders 로 세고 건너뛴다). 왜 버려졌는지는 남긴다.
                  //
                  // 구조 프로브(B-3 후속): 실측에서 "후보 div=0개" 로 원인이 좁혀졌으나, 그것만으로는
                  // 바코드가 canvas/svg/img 로 그려지는 것인지, 컨테이너가 정말 비어 있는 것인지,
                  // 아니면 2초 대기가 짧아 아직 안 그려진 것인지를 가를 수 없다. 두 시점을 떠서 비교한다.
                  String structureAtRead = probeBarcodeContainer(driver);
                  this.delayTime(1500);
                  String structureAfterWait = probeBarcodeContainer(driver);
                  logger.warn(
                      "영수증 바코드를 읽지 못했다 — serial·datetime 없이 수집되어 저장 단계에서 버려진다."
                          + " mall={}, 후보 div={}개, 구조=[읽은 시점] {} / [+1.5s] {}",
                      mallName,
                      divElements.size(),
                      structureAtRead,
                      structureAfterWait);

                  // 2차 프로브: #barcodeTargetRec 이 없다는 것까지는 확정됐으므로, 이제
                  // (1) 그 자리에 대신 무엇이 있는지 (2) 본문에서 날짜를 얻을 수 있는지를 본다.
                  // (2)가 되면 바코드에 의존하지 않고 serial·datetime 을 만들 수 있어 결함이 해결된다.
                  logger.warn(
                      "  └ 팝업 구조: {} | 본문 날짜 패턴: {}",
                      probePopupStructure(driver),
                      describeReceiptDatePatterns(receiptDetail));

                  // A2·B 가 왜 실패했는지 — 요소가 없는지, 있는데 형식이 안 맞는지.
                  // 값이 아니라 자릿수만 남긴다.
                  StringBuilder paths = new StringBuilder();
                  for (String selector : NEW_TEMPLATE_BARCODE_SELECTORS) {
                    paths
                        .append(selector)
                        .append("=")
                        .append(shapeOf(readElementValue(driver, selector)))
                        .append(" ");
                  }
                  for (String selector : NEW_TEMPLATE_FIELD_SELECTORS) {
                    paths
                        .append(selector)
                        .append("=")
                        .append(shapeOf(readElementValue(driver, selector)))
                        .append(" ");
                  }
                  logger.warn(
                      "  └ 복구 경로 판독: {}| 본문 날짜={}",
                      paths,
                      extractDateFromBody(receiptDetail) == null ? "없음" : "있음(값 미기록)");
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
      ClickUtil.safeClick(driver, btn_prev, ".btn-prev-month");
    }

    return resJsonArr;
  }

  /**
   * 바코드를 못 읽었을 때 {@code #barcodeTargetRec} 의 <b>구조만</b> 떠서 한 줄로 요약한다 (B-3 후속 진단).
   *
   * <h2>왜 구조만 보는가</h2>
   *
   * <p>이 저장소는 PUBLIC 이고 로그는 배포본에 남는다. 영수증 DOM 을 통째로 덤프하면 상품명·금액·구매일시가 그대로 남는다. 답해야 할 질문은 "바코드가
   * canvas/svg/이미지로 렌더링되는가, 아니면 그 영수증만 바코드가 없는가" 뿐이고, 그 답에는 <b>태그 이름과 개수</b>면 충분하다. 그래서 텍스트는 내용이
   * 아니라 길이만 센다.
   *
   * <p>진단이 수집을 깨뜨리면 원래 결함보다 나쁘다. 어떤 예외도 밖으로 내보내지 않는다.
   *
   * @param driver 영수증 팝업으로 이미 switch 된 드라이버
   * @return 한 줄 요약. 실패해도 문자열을 돌려준다
   */
  private static String probeBarcodeContainer(WebDriver driver) {
    try {
      List<WebElement> containers = driver.findElements(By.id("barcodeTargetRec"));
      if (containers.isEmpty()) {
        return describeBarcodeContainer(false, null, null, null, 0);
      }
      WebElement container = containers.get(0);

      List<String> descendantTags = new ArrayList<>();
      try {
        for (WebElement descendant : container.findElements(By.cssSelector("*"))) {
          descendantTags.add(readTagName(descendant));
        }
      } catch (Exception e) {
        // 자손 조회 자체가 실패해도 나머지 신호는 살린다
      }

      return describeBarcodeContainer(
          true,
          readAttribute(container, "class"),
          readParentTagName(container),
          descendantTags,
          readTextLength(container));
    } catch (Exception e) {
      return "구조 프로브 실패(" + e.getClass().getSimpleName() + ")";
    }
  }

  private static String readTagName(WebElement element) {
    try {
      String tagName = element.getTagName();
      return tagName == null || tagName.isBlank() ? "?" : tagName.toLowerCase();
    } catch (Exception e) {
      return "?";
    }
  }

  private static String readParentTagName(WebElement element) {
    try {
      return readTagName(element.findElement(By.xpath("..")));
    } catch (Exception e) {
      return null;
    }
  }

  private static String readAttribute(WebElement element, String name) {
    try {
      return element.getAttribute(name);
    } catch (Exception e) {
      return null;
    }
  }

  private static int readTextLength(WebElement element) {
    try {
      String text = element.getText();
      return text == null ? 0 : text.trim().length();
    } catch (Exception e) {
      return -1; // 못 읽은 것과 0자를 구분한다
    }
  }

  /**
   * 구조 프로브가 모은 신호를 한 줄로 조립한다 (B-3 후속). 순수 함수라 브라우저 없이 검증한다.
   *
   * <p>판독법 — {@code canvas}/{@code svg}/{@code img} 가 잡히면 바코드는 <b>그려지는</b> 것이고 텍스트 추출로는 영원히 못 읽는다.
   * 자손 0개에 텍스트 0자면 그 영수증에 바코드가 <b>없는</b> 것이다. 컨테이너 자체가 없으면 셀렉터나 팝업 전환이 어긋난 것이다. 두 시점의 요약이 다르면 렌더링이
   * 늦은 것이다.
   *
   * @param containerFound {@code #barcodeTargetRec} 존재 여부
   * @param classAttribute 컨테이너의 class 속성 (구조 식별자. 없으면 null)
   * @param parentTagName 부모 태그명 (못 읽었으면 null)
   * @param descendantTagNames 자손 태그명 전부 (null 허용)
   * @param textLength 컨테이너 텍스트 길이. {@code -1} 은 못 읽음
   * @return 한 줄 요약
   */
  static String describeBarcodeContainer(
      boolean containerFound,
      String classAttribute,
      String parentTagName,
      List<String> descendantTagNames,
      int textLength) {
    if (!containerFound) {
      return "#barcodeTargetRec 없음";
    }

    StringBuilder sb = new StringBuilder("#barcodeTargetRec 있음");
    if (classAttribute != null && !classAttribute.isBlank()) {
      sb.append("(class=").append(classAttribute.trim()).append(")");
    }
    sb.append(", 부모=").append(parentTagName == null ? "?" : parentTagName);

    List<String> tags = descendantTagNames == null ? List.of() : descendantTagNames;
    sb.append(", 자손 ").append(tags.size()).append("개");
    if (!tags.isEmpty()) {
      sb.append(" [").append(tagCensus(tags)).append("]");
    }

    sb.append(", 텍스트 ").append(textLength < 0 ? "읽기 실패" : textLength + "자");
    return sb.toString();
  }

  /** 요약에 싣는 id 개수 상한. 넘으면 잘라내고 말줄임을 붙인다. */
  static final int ID_SAMPLE_LIMIT = 40;

  /**
   * 영수증 팝업 전체의 구조를 뜬다 (B-3 후속 2차 프로브).
   *
   * <p>{@code #barcodeTargetRec} 이 없다는 것은 확정됐다. 그렇다면 <b>그 자리에 대신 무엇이 있는가</b>가 다음 질문이고, 답은 문서에 실제로
   * 존재하는 id 목록에 있다. 대체 컨테이너를 찾으면 셀렉터를 고칠 수 있다.
   *
   * <p>요소마다 왕복하면 수백 번이 되므로 JS 한 번으로 모은다. 실패해도 예외를 내보내지 않는다.
   *
   * @param driver 영수증 팝업으로 switch 된 드라이버
   * @return 한 줄 요약
   */
  private static String probePopupStructure(WebDriver driver) {
    try {
      Object raw =
          ((JavascriptExecutor) driver)
              .executeScript(
                  "var els=document.body.getElementsByTagName('*');var t=[],i=[];"
                      + "for(var k=0;k<els.length;k++){t.push(els[k].tagName);"
                      + "if(els[k].id){i.push(els[k].id);}}"
                      + "return t.join(',')+'|'+i.join(',');");
      // 구분자는 '|' — 태그명과 id 에는 나타나지 않는다
      String[] parts = String.valueOf(raw).split("\\|", -1);
      return describePopupStructure(
          splitCsv(parts.length > 0 ? parts[0] : ""), splitCsv(parts.length > 1 ? parts[1] : ""));
    } catch (Exception e) {
      return "팝업 구조 프로브 실패(" + e.getClass().getSimpleName() + ")";
    }
  }

  private static List<String> splitCsv(String joined) {
    List<String> out = new ArrayList<>();
    if (joined == null || joined.isEmpty()) {
      return out;
    }
    for (String token : joined.split(",")) {
      if (!token.isBlank()) {
        out.add(token.trim());
      }
    }
    return out;
  }

  /**
   * 팝업 문서의 태그 집계와 id 목록을 한 줄로 조립한다 (B-3 후속). 순수 함수라 브라우저 없이 검증한다.
   *
   * <p><b>id 의 숫자는 가린다.</b> id 는 대개 템플릿 식별자지만 {@code order_20991231123} 처럼 주문번호가 섞이는 형태가 있을 수 있다.
   * 숫자를 {@code N} 으로 바꾸면 구조는 그대로 읽히면서 값은 남지 않는다.
   *
   * @param tagNames 문서 내 모든 요소의 태그명
   * @param idAttributes 비어 있지 않은 id 속성값 전부
   * @return 한 줄 요약
   */
  static String describePopupStructure(List<String> tagNames, List<String> idAttributes) {
    List<String> tags = tagNames == null ? List.of() : tagNames;
    List<String> ids = idAttributes == null ? List.of() : idAttributes;

    StringBuilder sb = new StringBuilder("태그 ").append(tags.size()).append("개");
    if (!tags.isEmpty()) {
      sb.append(" [").append(tagCensus(tags)).append("]");
    }

    List<String> masked =
        ids.stream()
            .map(Emart::maskDigits)
            .distinct()
            .sorted()
            .collect(java.util.stream.Collectors.toList());
    sb.append(", id ").append(masked.size()).append("종");
    if (!masked.isEmpty()) {
      sb.append(" [")
          .append(String.join(", ", masked.subList(0, Math.min(ID_SAMPLE_LIMIT, masked.size()))));
      if (masked.size() > ID_SAMPLE_LIMIT) {
        sb.append(", …");
      }
      sb.append("]");
    }
    return sb.toString();
  }

  /** 숫자를 전부 {@code N} 으로 바꾼다. 형식은 남기고 값은 지운다. */
  static String maskDigits(String value) {
    return value == null ? null : value.replaceAll("\\d", "N");
  }

  /** 본문에서 찾아볼 날짜·시각 표기. 이름은 로그에 쓰는 라벨이다. */
  private static final String[][] DATE_PATTERNS = {
    {"YYYY-MM-DD", "\\d{4}-\\d{2}-\\d{2}"},
    {"YYYY/MM/DD", "\\d{4}/\\d{2}/\\d{2}"},
    {"YYYY.MM.DD", "\\d{4}\\.\\d{2}\\.\\d{2}"},
    {"YYYY년MM월DD일", "\\d{4}\\s*년\\s*\\d{1,2}\\s*월\\s*\\d{1,2}\\s*일"},
    {"8연속숫자", "(?<!\\d)\\d{8}(?!\\d)"},
    {"HH:MM", "(?<!\\d)\\d{2}:\\d{2}(?!\\d)"},
  };

  /**
   * 영수증 본문에 날짜가 들어 있는지, 있다면 어떤 <b>형식</b>인지만 보고한다 (B-3 후속 2차 프로브).
   *
   * <h2>왜 값을 싣지 않는가</h2>
   *
   * <p>알아야 할 것은 "본문에서 구매일시를 뽑을 수 있는가, 뽑는다면 어떤 형식인가"다. 그 판단에는 <b>매치 수와 위치</b>면 충분하고, 날짜 값 자체는 구매
   * 이력이라 PUBLIC 저장소의 로그에 남길 것이 아니다. 본문 줄을 그대로 싣는 방법도 쓰지 않는다 — 숫자를 가려도 같은 줄의 상품명이 남는다.
   *
   * <p>매치가 정확히 1건인 패턴이 있으면 그 형식으로 파싱하면 되고, 여러 건이면 라벨을 봐야 하므로 다음 프로브가 필요하다.
   *
   * @param body 영수증 본문({@code <pre>} 텍스트). null 허용
   * @return 한 줄 요약. 날짜 값은 담기지 않는다
   */
  static String describeReceiptDatePatterns(String body) {
    if (body == null || body.isEmpty()) {
      return "본문 없음";
    }
    String[] lines = body.split("\\r?\\n", -1);

    StringBuilder sb = new StringBuilder("줄수=").append(lines.length);
    for (String[] pattern : DATE_PATTERNS) {
      java.util.regex.Pattern compiled = java.util.regex.Pattern.compile(pattern[1]);
      int matches = 0;
      int firstLine = -1;
      for (int i = 0; i < lines.length; i++) {
        java.util.regex.Matcher m = compiled.matcher(lines[i]);
        while (m.find()) {
          matches++;
          if (firstLine < 0) {
            firstLine = i + 1;
          }
        }
      }
      sb.append(", ").append(pattern[0]).append("=").append(matches);
      if (matches > 0) {
        sb.append("(줄").append(firstLine).append(")");
      }
    }
    return sb.toString();
  }

  /**
   * 태그 이름을 세어 {@code canvas=1, div=2} 형태로 만든다. 많은 것부터, 같으면 이름순.
   *
   * @param tagNames 태그명 목록 (null 원소 허용)
   * @return 집계 문자열
   */
  static String tagCensus(List<String> tagNames) {
    java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
    for (String tagName : tagNames) {
      String key = tagName == null || tagName.isBlank() ? "?" : tagName.trim().toLowerCase();
      counts.merge(key, 1, Integer::sum);
    }
    return counts.entrySet().stream()
        .sorted(
            java.util.Comparator.<java.util.Map.Entry<String, Integer>>comparingInt(
                    e -> -e.getValue())
                .thenComparing(java.util.Map.Entry::getKey))
        .map(e -> e.getKey() + "=" + e.getValue())
        .collect(java.util.stream.Collectors.joining(", "));
  }

  /**
   * 바코드 후보 텍스트들 중에서 실제 영수증 바코드를 골라낸다 (판단 대기 B-3).
   *
   * <h2>왜 필요했나</h2>
   *
   * <p>이전 코드는 {@code #barcodeTargetRec div} 중 <b>마지막 것의 텍스트를 무조건</b> 바코드로 썼다. 실측에서 17건 중 2건(12%)이
   * 바코드 미인식이었고, 코드에는 결함이 세 개 겹쳐 있었다.
   *
   * <ul>
   *   <li><b>조용한 유실</b> — div 가 1개 이하면 바코드가 {@code null} 이 되고, 그 영수증은 serial·datetime 없이 수집돼 저장
   *       단계에서 말없이 버려진다({@code skippedOrders} 로만 세어진다).
   *   <li><b>크래시 위험</b> — 마지막 div 의 텍스트가 비었거나 8자 미만이면 호출측의 {@code substring(0, 8)} 이 {@code
   *       StringIndexOutOfBoundsException} 을 던진다. 영수증 하나 때문에 Emart 수집 전체가 무너진다.
   *   <li><b>위치 가정</b> — "마지막 div" 는 렌더링 구조에 대한 가정일 뿐이다. 뒤에 라벨이나 래퍼 div 가 하나 붙으면 엉뚱한 값이 serial 로
   *       들어간다.
   * </ul>
   *
   * <h2>고른 방식</h2>
   *
   * <p>뒤에서부터 훑되 <b>바코드처럼 생긴 것만</b> 받는다. 호출측이 앞 8자리를 {@code YYYYMMDD} 로 쓰므로, 그 용도가 성립하는지를 그대로 판정
   * 기준으로 삼는다 — 공백 제거 후 8자 이상이고, 앞 8자가 숫자이며, 그 8자가 실제 날짜 범위(월 01~12, 일 01~31)여야 한다.
   *
   * <p>못 찾으면 <b>추측하지 않고 {@code null}</b> 을 돌려준다. 틀린 serial 로 저장하는 것보다 낫다.
   *
   * @param candidateTexts 후보 div 들의 텍스트 (null 원소 허용)
   * @return 바코드 문자열 (공백 제거됨). 없으면 null
   */
  static String extractReceiptBarcode(List<String> candidateTexts) {
    if (candidateTexts == null) {
      return null;
    }
    for (int i = candidateTexts.size() - 1; i >= 0; i--) {
      String raw = candidateTexts.get(i);
      if (raw == null) {
        continue;
      }
      String normalized = raw.replaceAll("\\s", "");
      if (looksLikeReceiptBarcode(normalized)) {
        return normalized;
      }
    }
    return null;
  }

  /** 신 템플릿(ern-)의 바코드 요소 셀렉터. 구 템플릿에는 없다. */
  private static final String[] NEW_TEMPLATE_BARCODE_SELECTORS = {"#barcode-no", "#ern-barcode"};

  /** 신 템플릿이 영수증 식별자를 분해해 담아 두는 요소들. 순서가 곧 serial 의 조립 순서다. */
  private static final String[] NEW_TEMPLATE_FIELD_SELECTORS = {
    "#biz_date", "#pos_str_code", "#pos_no", "#tran_no"
  };

  /**
   * 요소의 값을 읽는다. {@code <input>} 이면 텍스트가 비므로 {@code value} 속성을 본다.
   *
   * @return 값. 요소가 없거나 못 읽으면 null
   */
  private static String readElementValue(WebDriver driver, String cssSelector) {
    try {
      List<WebElement> found = driver.findElements(By.cssSelector(cssSelector));
      if (found.isEmpty()) {
        return null;
      }
      WebElement element = found.get(0);
      String text = null;
      try {
        text = element.getText();
      } catch (Exception ignore) {
        // 아래에서 value 속성으로 재시도한다
      }
      if (text != null && !text.isBlank()) {
        return text;
      }
      return readAttribute(element, "value");
    } catch (Exception e) {
      return null;
    }
  }

  /** 숫자만 남긴다. null 은 빈 문자열로 본다. */
  static String digitsOnly(String value) {
    return value == null ? "" : value.replaceAll("\\D", "");
  }

  /**
   * 두 복구 경로가 서로 무엇을 말했는지 한 마디로 남긴다 (B-3).
   *
   * <p>둘 다 값을 냈는데 <b>서로 다르면</b> 어느 쪽이 진짜 바코드인지 확정되지 않았다는 뜻이고, 그때 채택 우선순위(A2 우선)가 틀렸다면 재수집 시 serial
   * 이 흔들려 중복 주문이 생긴다. 그 신호를 놓치지 않으려고 남긴다.
   *
   * <p>주의: "채택된 값과 같은가"를 묻는 것이 아니다. B 를 채택한 경우 채택값과 B 는 당연히 같아서 아무것도 알려주지 않는다.
   *
   * @param fromNewBarcode 경로 A2 결과 (null 허용)
   * @param composed 경로 B 결과 (null 허용)
   * @return 대조 결과
   */
  static String describeRecoveryAgreement(String fromNewBarcode, String composed) {
    if (fromNewBarcode == null && composed == null) {
      return "둘 다 실패";
    }
    if (fromNewBarcode == null) {
      return "B만 성공";
    }
    if (composed == null) {
      return "A2만 성공";
    }
    return fromNewBarcode.equals(composed) ? "A2=B" : "A2≠B(A2 채택)";
  }

  /**
   * 값의 <b>형태</b>만 기술한다 — 요소가 없는지, 비었는지, 몇 자리 숫자인지.
   *
   * <p>진단에는 자릿수면 충분하고 값은 구매 이력이다. {@code 없음} / {@code 빈값} / {@code 숫자8자} / {@code 18자(숫자14)} 형태로
   * 준다.
   *
   * @param value 읽어 온 값 (null 허용)
   * @return 형태 설명
   */
  static String shapeOf(String value) {
    if (value == null) {
      return "없음";
    }
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      return "빈값";
    }
    int digits = digitsOnly(trimmed).length();
    return digits == trimmed.length()
        ? "숫자" + digits + "자"
        : trimmed.length() + "자(숫자" + digits + ")";
  }

  /**
   * 신 템플릿의 분해된 필드로 {@code serial} 을 조립한다 (B-3 해결 경로 B).
   *
   * <h2>왜 꼬리가 반드시 있어야 하는가</h2>
   *
   * <p>중복 판정이 {@code serial_num} + {@code date_time} <b>조합</b>이다({@code
   * JbgOrderDataAccessObject.getOrder}). 날짜만으로 serial 을 만들면 <b>같은 날 산 영수증이 전부 한 건으로 뭉개진다</b> — 첫 건만
   * 저장되고 나머지는 "기존 주문"으로 스킵된다. 실제로 같은 날짜에 3건이 저장된 이력이 있으므로 가상의 위험이 아니다.
   *
   * <p>그래서 매장·POS·거래번호 중 <b>하나라도 읽히지 않으면 null 을 돌려준다.</b> 틀린 키로 저장해 데이터를 잃는 것보다 낫다.
   *
   * <p>앞 8자리를 {@code YYYYMMDD} 로 두는 것은 호출측이 {@code substring(0, 8)} 을 그대로 쓰기 때문이다 — 구 템플릿 바코드와 같은
   * 규약이다.
   *
   * @return 조립된 serial. 날짜가 유효하지 않거나 꼬리가 비면 null
   */
  static String composeSerialFromFields(
      String bizDate, String storeCode, String posNo, String tranNo) {
    String date = digitsOnly(bizDate);
    if (date.length() < 8) {
      return null;
    }
    date = date.substring(0, 8);

    String tail = digitsOnly(storeCode) + digitsOnly(posNo) + digitsOnly(tranNo);
    if (tail.isEmpty()) {
      return null; // 날짜만으로는 같은 날 여러 영수증을 구분할 수 없다
    }

    String serial = date + tail;
    return looksLikeReceiptBarcode(serial) ? serial : null;
  }

  /**
   * 본문에서 구매일자를 뽑는다 (B-3 경로 C). <b>저장에는 쓰지 않는다.</b>
   *
   * <p>2차 프로브 실측에서 본문 10줄째에 {@code YYYY-MM-DD HH:MM} 이 정확히 1건 있음을 확인했다. 다만 이것만으로는 {@code serial} 을
   * 만들 수 없고, serial 없이 저장하면 위 {@link #composeSerialFromFields} 의 주석과 같은 이유로 <b>같은 날 영수증이 뭉개진다.</b>
   * 그래서 진단용으로만 남긴다 — "날짜는 있는데 식별자가 없다"와 "아무것도 없다"를 구분하기 위한 것이다.
   *
   * @param body 영수증 본문
   * @return {@code YYYYMMDD}. 없으면 null
   */
  static String extractDateFromBody(String body) {
    if (body == null || body.isEmpty()) {
      return null;
    }
    java.util.regex.Matcher m =
        java.util.regex.Pattern.compile("(\\d{4})[-/.](\\d{2})[-/.](\\d{2})").matcher(body);
    while (m.find()) {
      String candidate = m.group(1) + m.group(2) + m.group(3);
      if (looksLikeReceiptBarcode(candidate + "0")) { // 8자리 날짜 유효성만 본다
        return candidate;
      }
    }
    return null;
  }

  /** 앞 8자리를 {@code YYYYMMDD} 로 쓸 수 있는 형태인지. */
  private static boolean looksLikeReceiptBarcode(String value) {
    if (value.length() < 8) {
      return false;
    }
    String head = value.substring(0, 8);
    for (int i = 0; i < 8; i++) {
      if (!Character.isDigit(head.charAt(i))) {
        return false;
      }
    }
    int month = Integer.parseInt(head.substring(4, 6));
    int day = Integer.parseInt(head.substring(6, 8));
    return month >= 1 && month <= 12 && day >= 1 && day <= 31;
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
        // row 값이 없는 경우 skip
        continue;
      } else {
        boolean skipRow = false;
        for (String cancelKey : cancelKeys) {
          if (NumberUtil.isNumber(line) || line.indexOf(cancelKey) > -1) {
            // 아이템 정보가 아닌 row 는 skip
            skipRow = true;
            break;
          }
        }

        if (skipRow) continue;
      }

      // row내의 컬럼값이 공백인지 검사하여 재작성
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
        if (arrSize > 1) itemJson.put("price", itemInfoArr[1]);
        if (arrSize > 2) itemJson.put("qty", itemInfoArr[2]);
        if (arrSize > 3) itemJson.put("sum", itemInfoArr[3]);
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
   * 구매 상품 목록 포맷이 예외 패턴인지 검사하여 재조합 위와 같이 줄바꿈 형태인 경우 (상품명과 나머지 데이터의 출력 라인이 다름) 라인으로 분리된 데이터를 결합하는 동작을
   * 한다
   *
   * @return List 재조합된 목록
   */
  private List<String> combineExtraPattern01(List<String> itemRows) {

    if (itemRows.size() < 1) return itemRows;

    List<String> newRows = new ArrayList<String>();
    String[] firstLineParts = itemRows.get(0).toString().trim().split("  ");
    if (firstLineParts[0].trim().length() > 1
        && NumberUtil.isNumber(firstLineParts[0].trim().substring(0, 2))) {
      String[] itemRow01, itemRow02 = null;
      for (int i = 0; i < itemRows.size(); i++) {
        if ((i + 1) < itemRows.size()) {

          itemRow01 = itemRows.get(i).toString().trim().split("  ");
          itemRow02 = itemRows.get(i + 1).toString().trim().split("  ");

          if ((itemRow01.length < itemRow02.length) && itemRow02.length > 3) {
            /** 상품명이 나오고 다음 라인에 나머지 정보가 나옴 "01 해태 자유시간아몬드" "8801019207655 800 1 800" */
            newRows.add(
                ((itemRow01.length > 1) ? itemRow01[1] : itemRow01[0])
                    + "  "
                    + itemRow02[1]
                    + "  "
                    + itemRow02[2]
                    + "  "
                    + itemRow02[3]);
            i++;
          } else if ((itemRow01.length > itemRow02.length) && itemRow01.length > 3) {
            /** 상품명이 나머지 정보들의 다음 라인에 나옴 "8806078813199 6,900 1 6,900" "20N매일쓰는위생적인" */
            newRows.add(
                ((itemRow02.length > 1) ? itemRow02[1] : itemRow02[0])
                    + "  "
                    + itemRow01[1]
                    + "  "
                    + itemRow01[2]
                    + "  "
                    + itemRow01[3]);
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

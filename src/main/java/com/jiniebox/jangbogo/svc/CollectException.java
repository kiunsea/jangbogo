package com.jiniebox.jangbogo.svc;

/**
 * 쇼핑몰 수집 단계 실행 중 발생한 예외. 실패 당시의 컨텍스트(단계명, URL, 페이지 타이틀, 타겟 셀렉터, 스크린샷 경로)를 함께 담아서 상위 레이어가 로그로 기록할 수
 * 있게 한다.
 *
 * @author KIUNSEA
 */
public class CollectException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String stepName;
  private final String currentUrl;
  private final String pageTitle;
  private final String targetSelector;
  private final String screenshotPath;

  public CollectException(
      String stepName,
      String currentUrl,
      String pageTitle,
      String targetSelector,
      String screenshotPath,
      String message,
      Throwable cause) {
    super(message, cause);
    this.stepName = stepName;
    this.currentUrl = currentUrl;
    this.pageTitle = pageTitle;
    this.targetSelector = targetSelector;
    this.screenshotPath = screenshotPath;
  }

  public String getStepName() {
    return stepName;
  }

  public String getCurrentUrl() {
    return currentUrl;
  }

  public String getPageTitle() {
    return pageTitle;
  }

  public String getTargetSelector() {
    return targetSelector;
  }

  public String getScreenshotPath() {
    return screenshotPath;
  }
}

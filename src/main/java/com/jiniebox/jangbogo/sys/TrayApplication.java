package com.jiniebox.jangbogo.sys;

import com.jiniebox.jangbogo.util.BrowserLauncher;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 시스템 트레이 애플리케이션 Windows 시스템 트레이에 아이콘을 표시하고 메뉴를 제공합니다. */
public class TrayApplication {

  private static final Logger logger = LoggerFactory.getLogger(TrayApplication.class);
  private static TrayIcon trayIcon;
  private static boolean isServiceRunning = true;

  /** 시스템 트레이 아이콘을 초기화하고 표시합니다. */
  public static void initialize() {
    if (!SystemTray.isSupported()) {
      logger.warn("시스템 트레이가 지원되지 않습니다.");
      return;
    }

    try {
      SystemTray tray = SystemTray.getSystemTray();
      Image image = loadTrayIcon();

      // 팝업 메뉴 생성
      PopupMenu popup = createPopupMenu();

      // 트레이 아이콘 생성
      trayIcon = new TrayIcon(image, "Jangbogo 구매내역 수집 서비스", popup);
      trayIcon.setImageAutoSize(true);

      // 더블클릭 이벤트: 브라우저 열기
      trayIcon.addActionListener(
          e -> {
            logger.info("트레이 아이콘 더블클릭 - 브라우저 실행");
            BrowserLauncher.launch();
          });

      // 시스템 트레이에 추가
      tray.add(trayIcon);

      logger.info("시스템 트레이 아이콘 초기화 완료");

    } catch (AWTException e) {
      logger.error("시스템 트레이 아이콘 추가 실패", e);
    } catch (IOException e) {
      logger.error("트레이 아이콘 이미지 로드 실패", e);
    }
  }

  /** 팝업 메뉴를 생성합니다. */
  private static PopupMenu createPopupMenu() {
    PopupMenu popup = new PopupMenu();

    // 관리 화면 열기
    MenuItem openBrowserItem = new MenuItem("관리 화면 열기");
    openBrowserItem.addActionListener(createBrowserLaunchAction());
    popup.add(openBrowserItem);

    popup.addSeparator();

    // 서비스 시작
    MenuItem startServiceItem = new MenuItem("서비스 시작");
    startServiceItem.addActionListener(createServiceStartAction());
    popup.add(startServiceItem);

    // 서비스 중지
    MenuItem stopServiceItem = new MenuItem("서비스 중지");
    stopServiceItem.addActionListener(createServiceStopAction());
    popup.add(stopServiceItem);

    popup.addSeparator();

    // 정보
    MenuItem infoItem = new MenuItem("정보");
    infoItem.addActionListener(
        e -> {
          trayIcon.displayMessage(
              "Jangbogo v1.0.0",
              "구매내역 수집 서비스\n접속: http://127.0.0.1:8282",
              TrayIcon.MessageType.INFO);
        });
    popup.add(infoItem);

    popup.addSeparator();

    // 종료
    MenuItem exitItem = new MenuItem("종료");
    exitItem.addActionListener(createExitAction());
    popup.add(exitItem);

    return popup;
  }

  /** 브라우저 실행 액션을 생성합니다. */
  private static ActionListener createBrowserLaunchAction() {
    return e -> {
      logger.info("관리 화면 열기 클릭");
      BrowserLauncher.launch();
    };
  }

  /** 서비스 시작 액션을 생성합니다. */
  private static ActionListener createServiceStartAction() {
    return e -> {
      try {
        logger.info("서비스 시작 시도");

        // WinSW를 통해 서비스 시작
        String installDir = System.getProperty("user.dir");
        ProcessBuilder pb =
            new ProcessBuilder(
                "cmd.exe",
                "/c",
                "cd /d \"" + installDir + "\\winsw\" && jangbogo-service.exe start");

        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode == 0) {
          isServiceRunning = true;
          trayIcon.displayMessage("서비스 시작", "Jangbogo 서비스가 시작되었습니다.", TrayIcon.MessageType.INFO);
          logger.info("서비스 시작 완료");
        } else {
          trayIcon.displayMessage(
              "서비스 시작 실패", "서비스 시작에 실패했습니다. (코드: " + exitCode + ")", TrayIcon.MessageType.ERROR);
          logger.error("서비스 시작 실패: exit code {}", exitCode);
        }
      } catch (Exception ex) {
        logger.error("서비스 시작 중 오류", ex);
        trayIcon.displayMessage("오류", "서비스 시작 중 오류가 발생했습니다.", TrayIcon.MessageType.ERROR);
      }
    };
  }

  /** 서비스 중지 액션을 생성합니다. */
  private static ActionListener createServiceStopAction() {
    return e -> {
      try {
        logger.info("서비스 중지 시도");

        // WinSW를 통해 서비스 중지
        String installDir = System.getProperty("user.dir");
        ProcessBuilder pb =
            new ProcessBuilder(
                "cmd.exe",
                "/c",
                "cd /d \"" + installDir + "\\winsw\" && jangbogo-service.exe stop");

        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode == 0) {
          isServiceRunning = false;
          trayIcon.displayMessage("서비스 중지", "Jangbogo 서비스가 중지되었습니다.", TrayIcon.MessageType.INFO);
          logger.info("서비스 중지 완료");
        } else {
          trayIcon.displayMessage(
              "서비스 중지 실패", "서비스 중지에 실패했습니다. (코드: " + exitCode + ")", TrayIcon.MessageType.WARNING);
          logger.error("서비스 중지 실패: exit code {}", exitCode);
        }
      } catch (Exception ex) {
        logger.error("서비스 중지 중 오류", ex);
        trayIcon.displayMessage("오류", "서비스 중지 중 오류가 발생했습니다.", TrayIcon.MessageType.ERROR);
      }
    };
  }

  /** 종료 액션을 생성합니다. */
  private static ActionListener createExitAction() {
    return e -> {
      logger.info("트레이 애플리케이션 종료");

      if (trayIcon != null) {
        SystemTray.getSystemTray().remove(trayIcon);
      }

      // 트레이 애플리케이션만 종료 (서비스는 계속 실행)
      System.exit(0);
    };
  }

  /** 트레이 아이콘 이미지를 로드합니다. */
  private static Image loadTrayIcon() throws IOException {
    // 리소스에서 아이콘 로드 시도
    InputStream iconStream = TrayApplication.class.getResourceAsStream("/icons/tray-icon.png");

    if (iconStream != null) {
      try {
        return ImageIO.read(iconStream);
      } finally {
        iconStream.close();
      }
    }

    // 리소스가 없으면 기본 아이콘 생성
    logger.warn("트레이 아이콘을 찾을 수 없어 기본 아이콘을 생성합니다.");
    return createDefaultIcon();
  }

  /** 기본 트레이 아이콘을 생성합니다. */
  private static Image createDefaultIcon() {
    int size = 16;
    BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2d = image.createGraphics();

    // 안티앨리어싱 설정
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    // 파란색 원 그리기
    g2d.setColor(new Color(33, 150, 243));
    g2d.fillOval(2, 2, size - 4, size - 4);

    // 흰색 테두리
    g2d.setColor(Color.WHITE);
    g2d.setStroke(new BasicStroke(2));
    g2d.drawOval(2, 2, size - 4, size - 4);

    g2d.dispose();

    return image;
  }

  /** 트레이 알림 메시지를 표시합니다. */
  public static void showNotification(String title, String message, TrayIcon.MessageType type) {
    if (trayIcon != null) {
      trayIcon.displayMessage(title, message, type);
    }
  }
}

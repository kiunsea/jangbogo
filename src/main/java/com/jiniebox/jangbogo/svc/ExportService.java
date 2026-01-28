package com.jiniebox.jangbogo.svc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.jiniebox.jangbogo.dao.JbgItemDataAccessObject;
import com.jiniebox.jangbogo.dao.JbgMallDataAccessObject;
import com.jiniebox.jangbogo.dao.JbgOrderDataAccessObject;
import com.jiniebox.jangbogo.dto.ExportData;
import com.jiniebox.jangbogo.dto.ExportOrder;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.springframework.stereotype.Service;

/** 구매정보 파일 저장 서비스 */
@Service
public class ExportService {

  private static final Logger logger = LogManager.getLogger(ExportService.class);

  /**
   * 모든 구매정보를 조회하여 파일로 저장
   *
   * @param savePath 저장 경로
   * @param format 저장 포맷 (json, yaml, csv, excel)
   * @return 저장된 파일 경로
   * @throws Exception
   */
  public String exportAllOrders(String savePath, String format) throws Exception {
    return exportAllOrders(savePath, format, -1);
  }

  /**
   * 특정 주문 seq 목록만 파일로 저장
   *
   * @param savePath 저장 경로
   * @param format 저장 포맷 (json, yaml, csv, excel)
   * @param orderSeqs 저장할 주문 seq 목록
   * @return 저장된 파일 경로
   * @throws Exception
   */
  public String exportOrdersBySeqList(String savePath, String format, List<Integer> orderSeqs)
      throws Exception {
    if (orderSeqs == null || orderSeqs.isEmpty()) {
      throw new IllegalStateException("저장할 주문이 없습니다.");
    }

    logger.info("신규 주문 파일 저장 시작 - 경로: {}, 포맷: {}, 주문 개수: {}", savePath, format, orderSeqs.size());

    // 1. 특정 seq의 주문 및 아이템만 조회
    ExportData exportData = collectOrderDataBySeqList(orderSeqs);

    // 2. 포맷에 따라 파일 생성
    String filePath = null;
    switch (format.toLowerCase()) {
      case "json":
        filePath = saveAsJson(exportData, savePath);
        break;
      case "yaml":
      case "yml":
        filePath = saveAsYaml(exportData, savePath);
        break;
      case "csv":
        filePath = saveAsCsv(exportData, savePath);
        break;
      case "excel":
        throw new UnsupportedOperationException("Excel 포맷은 아직 지원하지 않습니다.");
      default:
        throw new IllegalArgumentException("지원하지 않는 포맷입니다: " + format);
    }

    logger.info("신규 주문 파일 저장 완료 - 파일: {}, 주문: {}개", filePath, orderSeqs.size());
    return filePath;
  }

  /**
   * 구매정보를 조회하여 파일로 저장 (신규 데이터만 또는 전체)
   *
   * @param savePath 저장 경로
   * @param format 저장 포맷 (json, yaml, csv, excel)
   * @param afterSeq 이 seq 이후의 주문만 저장 (-1이면 전체)
   * @return 저장된 파일 경로
   * @throws Exception
   */
  public String exportAllOrders(String savePath, String format, int afterSeq) throws Exception {
    if (afterSeq >= 0) {
      logger.info("신규 구매정보 파일 저장 시작 - 경로: {}, 포맷: {}, afterSeq: {}", savePath, format, afterSeq);
    } else {
      logger.info("전체 구매정보 파일 저장 시작 - 경로: {}, 포맷: {}", savePath, format);
    }

    // 1. DB에서 주문 및 아이템 조회
    ExportData exportData = collectOrderData(afterSeq);

    // 2. 포맷에 따라 파일 생성
    String filePath = null;
    switch (format.toLowerCase()) {
      case "json":
        filePath = saveAsJson(exportData, savePath);
        break;
      case "yaml":
      case "yml":
        filePath = saveAsYaml(exportData, savePath);
        break;
      case "csv":
        filePath = saveAsCsv(exportData, savePath);
        break;
      case "excel":
        // TODO: Excel 저장 구현
        throw new UnsupportedOperationException("Excel 포맷은 아직 지원하지 않습니다.");
      default:
        throw new IllegalArgumentException("지원하지 않는 포맷입니다: " + format);
    }

    logger.info("구매정보 파일 저장 완료 - 파일: {}", filePath);
    return filePath;
  }

  /**
   * 특정 seq 목록의 주문 및 아이템 데이터 수집
   *
   * @param orderSeqs 조회할 주문 seq 목록
   */
  private ExportData collectOrderDataBySeqList(List<Integer> orderSeqs) throws Exception {
    ExportData exportData = new ExportData();

    // 내보내기 정보 설정
    exportData.setExportInfoValue(
        "export_date", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    exportData.setExportInfoValue("version", "1.0");
    exportData.setExportInfoValue("type", "collected_only");
    exportData.setExportInfoValue("order_count", String.valueOf(orderSeqs.size()));

    JbgMallDataAccessObject mallDao = new JbgMallDataAccessObject();
    JbgOrderDataAccessObject orderDao = new JbgOrderDataAccessObject();
    JbgItemDataAccessObject itemDao = new JbgItemDataAccessObject();

    // 모든 쇼핑몰 조회
    List<JSONObject> malls = mallDao.getAllMalls(false);

    // 쇼핑몰별 주문 조회
    Map<String, String> mallIdMap = new HashMap<>(); // seq -> id 매핑
    for (JSONObject mall : malls) {
      String seq = String.valueOf(mall.get("seq"));
      String mallId = String.valueOf(mall.get("id"));
      mallIdMap.put(seq, mallId);
    }

    // 특정 seq 목록의 주문만 조회
    List<JSONObject> allOrders = orderDao.getOrdersBySeqList(orderSeqs);
    if (allOrders == null || allOrders.isEmpty()) {
      logger.warn("조회된 주문 데이터가 없습니다. seq: {}", orderSeqs);
      throw new IllegalStateException("조회된 주문 데이터가 없습니다.");
    }

    int orderCount = 0;
    int itemCount = 0;

    for (JSONObject orderJson : allOrders) {
      try {
        String seqOrder = String.valueOf(orderJson.get("seq"));
        String serialNum = String.valueOf(orderJson.get("serial_num"));
        String dateTime = String.valueOf(orderJson.get("date_time"));
        String mallName =
            orderJson.get("mall_name") != null ? String.valueOf(orderJson.get("mall_name")) : "";
        String seqMall = String.valueOf(orderJson.get("seq_mall"));

        String mallId = mallIdMap.getOrDefault(seqMall, "unknown");

        ExportOrder exportOrder = new ExportOrder(serialNum, dateTime, mallName, mallId);

        // 해당 주문의 아이템 조회
        List<JSONObject> items = itemDao.getItemsByOrder(seqOrder);
        if (items != null && !items.isEmpty()) {
          for (JSONObject itemJson : items) {
            String itemName = String.valueOf(itemJson.get("name"));
            String qty = itemJson.get("qty") != null ? String.valueOf(itemJson.get("qty")) : "";

            // qty가 있으면 "상품명 (수량: N)" 형태로 저장
            if (qty != null && !qty.trim().isEmpty()) {
              itemName = itemName + " (수량: " + qty + ")";
            }

            exportOrder.addItem(itemName);
            itemCount++;
          }
        }

        exportData.addOrder(mallId, exportOrder);
        orderCount++;
      } catch (Exception e) {
        logger.warn("주문 seq={} 처리 중 오류: {}", orderJson.get("seq"), e.getMessage());
      }
    }

    exportData.setExportInfoValue("total_orders", String.valueOf(orderCount));
    exportData.setExportInfoValue("total_items", String.valueOf(itemCount));

    logger.info("신규 주문 데이터 수집 완료 - 주문: {}개, 아이템: {}개", orderCount, itemCount);
    return exportData;
  }

  /**
   * DB에서 주문 및 아이템 데이터 수집
   *
   * @param afterSeq 이 seq 이후의 주문만 조회 (-1이면 전체)
   */
  private ExportData collectOrderData(int afterSeq) throws Exception {
    ExportData exportData = new ExportData();

    // 내보내기 정보 설정
    exportData.setExportInfoValue(
        "export_date", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    exportData.setExportInfoValue("version", "1.0");
    if (afterSeq >= 0) {
      exportData.setExportInfoValue("type", "new_only");
      exportData.setExportInfoValue("after_seq", String.valueOf(afterSeq));
    } else {
      exportData.setExportInfoValue("type", "all");
    }

    JbgMallDataAccessObject mallDao = new JbgMallDataAccessObject();
    JbgOrderDataAccessObject orderDao = new JbgOrderDataAccessObject();
    JbgItemDataAccessObject itemDao = new JbgItemDataAccessObject();

    // 모든 쇼핑몰 조회
    List<JSONObject> malls = mallDao.getAllMalls(false);

    // 쇼핑몰별 주문 조회
    Map<String, String> mallIdMap = new HashMap<>(); // seq -> id 매핑
    for (JSONObject mall : malls) {
      String seq = String.valueOf(mall.get("seq"));
      String mallId = String.valueOf(mall.get("id"));
      mallIdMap.put(seq, mallId);
    }

    // 주문 조회 (afterSeq 이후 또는 전체)
    List<JSONObject> allOrders;
    if (afterSeq >= 0) {
      allOrders = orderDao.getOrdersAfterSeq(afterSeq);
    } else {
      allOrders = orderDao.getAllOrders();
    }

    if (allOrders == null || allOrders.isEmpty()) {
      String msg = afterSeq >= 0 ? "새로 추가된 주문 데이터가 없습니다." : "저장할 주문 데이터가 없습니다.";
      logger.warn(msg);
      throw new IllegalStateException(msg);
    }

    int orderCount = 0;
    int itemCount = 0;

    for (JSONObject orderJson : allOrders) {
      try {
        String seqOrder = String.valueOf(orderJson.get("seq"));
        String serialNum = String.valueOf(orderJson.get("serial_num"));
        String dateTime = String.valueOf(orderJson.get("date_time"));
        String mallName =
            orderJson.get("mall_name") != null ? String.valueOf(orderJson.get("mall_name")) : "";
        String seqMall = String.valueOf(orderJson.get("seq_mall"));

        String mallId = mallIdMap.getOrDefault(seqMall, "unknown");

        ExportOrder exportOrder = new ExportOrder(serialNum, dateTime, mallName, mallId);

        // 해당 주문의 아이템 조회
        List<JSONObject> items = itemDao.getItemsByOrder(seqOrder);
        if (items != null && !items.isEmpty()) {
          for (JSONObject itemJson : items) {
            String itemName = String.valueOf(itemJson.get("name"));
            String qty = itemJson.get("qty") != null ? String.valueOf(itemJson.get("qty")) : "";

            // qty가 있으면 "상품명 (수량: N)" 형태로 저장
            if (qty != null && !qty.trim().isEmpty()) {
              itemName = itemName + " (수량: " + qty + ")";
            }

            exportOrder.addItem(itemName);
            itemCount++;
          }
        }

        exportData.addOrder(mallId, exportOrder);
        orderCount++;

      } catch (Exception e) {
        logger.warn("주문 데이터 수집 중 오류: {}", e.getMessage());
      }
    }

    exportData.setExportInfoValue("total_orders", orderCount);
    exportData.setExportInfoValue("total_items", itemCount);

    logger.info("데이터 수집 완료 - 주문: {}개, 아이템: {}개", orderCount, itemCount);

    return exportData;
  }

  /** JSON 형식으로 저장 */
  private String saveAsJson(ExportData exportData, String savePath) throws IOException {
    File directory = new File(savePath);
    if (!directory.exists()) {
      directory.mkdirs();
      logger.info("디렉토리 생성: {}", savePath);
    }

    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    String fileName = "jangbogo_orders_" + timestamp + ".json";
    String filePath = savePath + File.separator + fileName;

    ObjectMapper mapper = new ObjectMapper();
    mapper.enable(SerializationFeature.INDENT_OUTPUT);

    mapper.writeValue(new File(filePath), exportData);

    logger.info("JSON 파일 저장 완료: {}", filePath);
    return filePath;
  }

  /** YAML 형식으로 저장 */
  private String saveAsYaml(ExportData exportData, String savePath) throws IOException {
    File directory = new File(savePath);
    if (!directory.exists()) {
      directory.mkdirs();
      logger.info("디렉토리 생성: {}", savePath);
    }

    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    String fileName = "jangbogo_orders_" + timestamp + ".yml";
    String filePath = savePath + File.separator + fileName;

    ObjectMapper yamlMapper =
        new ObjectMapper(
            new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));

    yamlMapper.writeValue(new File(filePath), exportData);

    logger.info("YAML 파일 저장 완료: {}", filePath);
    return filePath;
  }

  /** CSV 형식으로 저장 */
  private String saveAsCsv(ExportData exportData, String savePath) throws IOException {
    File directory = new File(savePath);
    if (!directory.exists()) {
      directory.mkdirs();
      logger.info("디렉토리 생성: {}", savePath);
    }

    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    String fileName = "jangbogo_orders_" + timestamp + ".csv";
    String filePath = savePath + File.separator + fileName;

    try (FileWriter writer = new FileWriter(filePath, java.nio.charset.StandardCharsets.UTF_8)) {
      // CSV 헤더
      writer.write("\uFEFF"); // UTF-8 BOM for Excel
      writer.write("쇼핑몰,영수증번호,구매일자,매장명,상품명\n");

      // 데이터 작성
      Map<String, List<ExportOrder>> malls = exportData.getMalls();
      for (Map.Entry<String, List<ExportOrder>> entry : malls.entrySet()) {
        String mallId = entry.getKey();
        List<ExportOrder> orders = entry.getValue();

        for (ExportOrder order : orders) {
          String serialNum = csvEscape(order.getSerialNum());
          String dateTime = csvEscape(order.getDateTime());
          String mallName = csvEscape(order.getMallName());

          if (order.getItems().isEmpty()) {
            // 아이템 없으면 주문 정보만
            writer.write(String.format("%s,%s,%s,%s,\n", mallId, serialNum, dateTime, mallName));
          } else {
            // 각 아이템마다 한 줄씩
            for (String itemName : order.getItems()) {
              writer.write(
                  String.format(
                      "%s,%s,%s,%s,%s\n",
                      mallId, serialNum, dateTime, mallName, csvEscape(itemName)));
            }
          }
        }
      }
    }

    logger.info("CSV 파일 저장 완료: {}", filePath);
    return filePath;
  }

  /** CSV 값 이스케이프 처리 */
  private String csvEscape(String value) {
    if (value == null) {
      return "";
    }
    // 쉼표, 따옴표, 개행문자가 있으면 따옴표로 감싸기
    if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
  }

  /**
   * jiniebox 형식의 JSON 파일로 저장 (단순 배열 형식)
   *
   * @param outputPath 출력 파일 경로
   * @param limit 조회할 주문 개수 (0이면 전체, 양수면 최근 N개)
   * @return 저장된 파일 경로
   * @throws Exception
   */
  public String exportToJiniebox(String outputPath, int limit) throws Exception {

    logger.info("jiniebox 형식 JSON export 시작: {}, limit: {}", outputPath, limit > 0 ? limit : "전체");

    // 1. JSON 생성
    String jsonContent = exportToJinieboxJson(limit);

    // 2. 파일로 저장
    try (FileWriter fileWriter =
        new FileWriter(outputPath, java.nio.charset.StandardCharsets.UTF_8)) {
      fileWriter.write(jsonContent);
      fileWriter.flush();
    } catch (IOException e) {
      logger.error("파일 저장 실패: {}", outputPath, e);
      throw e;
    }

    logger.info("jiniebox 형식 JSON export 완료: {} (크기: {} bytes)", outputPath, jsonContent.length());

    return outputPath;
  }

  /**
   * DB의 구매 정보를 jiniebox 형식 JSON 문자열로 변환
   *
   * @param limit 조회할 주문 개수 (0이면 전체)
   * @return JSON 문자열 (배열 형식)
   * @throws Exception
   */
  public String exportToJinieboxJson(int limit) throws Exception {

    JbgOrderDataAccessObject orderDao = new JbgOrderDataAccessObject();
    List<JSONObject> orders = orderDao.getAllOrders(limit);
    return buildJinieboxJsonFromOrders(orders);
  }

  /** 지정한 주문 목록을 jiniebox JSON 배열 문자열로 변환 */
  public String exportToJinieboxJsonBySeqList(List<Integer> orderSeqs) throws Exception {
    if (orderSeqs == null || orderSeqs.isEmpty()) {
      throw new IllegalStateException("저장할 주문이 없습니다.");
    }
    JbgOrderDataAccessObject orderDao = new JbgOrderDataAccessObject();
    List<JSONObject> orders = orderDao.getOrdersBySeqList(orderSeqs);
    return buildJinieboxJsonFromOrders(orders);
  }

  /**
   * FTP 업로드 전용 jiniebox JSON 파일 생성 (주문 seq 리스트 기반)
   *
   * @param directory 저장 디렉터리
   * @param orderSeqs 저장할 주문 seq 목록
   */
  public String exportToJinieboxFileBySeqList(String directory, List<Integer> orderSeqs)
      throws Exception {
    if (directory == null || directory.trim().isEmpty()) {
      throw new IllegalArgumentException("FTP 업로드용 파일을 생성하려면 저장 경로가 필요합니다.");
    }
    File dir = new File(directory);
    if (!dir.exists() && !dir.mkdirs()) {
      throw new IOException("저장 경로를 생성할 수 없습니다: " + directory);
    }

    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    String fileName = "jangbogo_orders_" + timestamp + "_ftp.json";
    String filePath = directory + File.separator + fileName;

    String jsonContent = exportToJinieboxJsonBySeqList(orderSeqs);
    try (FileWriter writer = new FileWriter(filePath, java.nio.charset.StandardCharsets.UTF_8)) {
      writer.write(jsonContent);
      writer.flush();
    }

    logger.info("FTP 업로드용 jiniebox JSON 생성 완료: {}", filePath);
    return filePath;
  }

  /**
   * seq_jbgmall → mall_id 변환
   *
   * @param seqMall 쇼핑몰 시퀀스
   * @return 쇼핑몰 ID (emart, ssg, oasis, unknown)
   */
  private String getMallIdFromSeq(int seqMall) {
    switch (seqMall) {
      case 1:
        return "emart"; // 이마트/SSG 그룹
      case 2:
        return "oasis"; // 오아시스
      case 3:
        return "hanaro"; // 하나로마트
      default:
        logger.warn("알 수 없는 쇼핑몰 seq: {}", seqMall);
        return "unknown";
    }
  }

  private String buildJinieboxJsonFromOrders(List<JSONObject> orders) throws Exception {
    if (orders == null || orders.isEmpty()) {
      logger.warn("조회된 주문이 없습니다.");
      return "[]";
    }

    JbgItemDataAccessObject itemDao = new JbgItemDataAccessObject();
    org.json.simple.JSONArray jsonArray = new org.json.simple.JSONArray();

    int totalItems = 0;
    for (JSONObject orderJson : orders) {
      org.json.simple.JSONObject orderObj = new org.json.simple.JSONObject();

      String serialNum = orderJson.get("serial_num").toString();
      String dateTime = orderJson.get("date_time").toString();
      int seqOrder = ((Number) orderJson.get("seq")).intValue();
      int seqMall = ((Number) orderJson.get("seq_mall")).intValue();
      String mallName =
          orderJson.get("mall_name") != null ? orderJson.get("mall_name").toString() : "";

      orderObj.put("serial", serialNum);
      orderObj.put("datetime", dateTime);
      orderObj.put("mall_id", getMallIdFromSeq(seqMall));
      orderObj.put("mallname", mallName);

      logger.debug(
          "주문 처리: serial={}, datetime={}, mall_id={}, mallname={}",
          serialNum,
          dateTime,
          getMallIdFromSeq(seqMall),
          mallName);

      List<JSONObject> items = itemDao.getItemsByOrder(String.valueOf(seqOrder));
      org.json.simple.JSONArray itemsArray = new org.json.simple.JSONArray();
      if (items != null && !items.isEmpty()) {
        for (JSONObject itemJson : items) {
          org.json.simple.JSONObject itemObj = new org.json.simple.JSONObject();
          itemObj.put("name", itemJson.get("name").toString());

          String qty = itemJson.get("qty") != null ? itemJson.get("qty").toString() : "1";
          if (qty.isEmpty()) {
            qty = "1";
          }
          itemObj.put("qty", qty);
          itemsArray.add(itemObj);
        }
        totalItems += itemsArray.size();
        logger.debug("  └─ 상품 {}개", itemsArray.size());
      } else {
        logger.debug("  └─ 상품 없음");
      }

      orderObj.put("items", itemsArray);
      jsonArray.add(orderObj);
    }

    logger.info("jiniebox JSON 생성 완료 - 주문: {}개, 상품: {}개", jsonArray.size(), totalItems);
    return jsonArray.toJSONString();
  }

  /**
   * 신규 주문이 없을 때 FTP 업로드를 위한 상태 파일 생성
   *
   * @param directory 저장 디렉터리
   * @return 생성된 상태 파일 경로
   * @throws Exception
   */
  public String createEmptyStatusFile(String directory) throws Exception {
    if (directory == null || directory.trim().isEmpty()) {
      throw new IllegalArgumentException("상태 파일을 생성하려면 저장 경로가 필요합니다.");
    }
    File dir = new File(directory);
    if (!dir.exists() && !dir.mkdirs()) {
      throw new IOException("저장 경로를 생성할 수 없습니다: " + directory);
    }

    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    String fileName = "jangbogo_status_" + timestamp + "_ftp.json";
    String filePath = directory + File.separator + fileName;

    // 빈 배열과 상태 정보를 포함한 JSON 생성
    org.json.simple.JSONObject statusObj = new org.json.simple.JSONObject();
    statusObj.put("status", "no_new_orders");
    statusObj.put("timestamp", timestamp);
    statusObj.put("message", "신규 주문이 없어 빈 상태 파일을 생성했습니다.");
    statusObj.put("orders", new org.json.simple.JSONArray());

    try (FileWriter writer = new FileWriter(filePath, java.nio.charset.StandardCharsets.UTF_8)) {
      writer.write(statusObj.toJSONString());
      writer.flush();
    }

    logger.info("FTP 업로드용 상태 파일 생성 완료: {}", filePath);
    return filePath;
  }
}

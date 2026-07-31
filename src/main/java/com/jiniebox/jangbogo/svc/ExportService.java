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
    // seq 로 분기하던 하드코딩 사슬을 MallRegistry 로 옮겼다 (Phase 3-12).
    // 값은 통합 전과 동일하다 — seq=1 은 여전히 "emart" 다. 수신측은 emart 와 ssg 를 둘 다 seq 1 로
    // 받으므로 어느 쪽이든 동작하지만, 검증할 수 없는 전송 포맷 변경을 리팩터링에 끼워 넣지 않는다.
    return com.jiniebox.jangbogo.svc.mall.MallRegistry.bySeq(seqMall)
        .map(com.jiniebox.jangbogo.svc.mall.MallRegistry::exportId)
        .orElseGet(
            () -> {
              logger.warn("알 수 없는 쇼핑몰 seq: {}", seqMall);
              return com.jiniebox.jangbogo.svc.mall.MallRegistry.UNKNOWN_EXPORT_ID;
            });
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
   * 신규 주문이 없을 때 FTP 업로드를 위한 상태 파일 생성.
   *
   * <p><b>반드시 최상위 JSON 배열이어야 한다.</b> 수신측은 최상위 노드가 배열인지를 검사한 뒤에야 주문을 순회한다. 배열이 아니면 그 자리에서 검증에 실패하고,
   * 파일은 처리 대기 폴더에서 {@code failed/} 로 옮겨진다 — 게다가 그때 <b>복호화된 평문 JSON 이 함께 디스크에 기록된다.</b>
   *
   * <p>과거 이 메서드는 {@code {"status":…,"timestamp":…,"message":…,"orders":[]}} 형태의 JSON <b>객체</b>를 썼다.
   * 같은 수신측에 최상위 구조가 다른 두 종류(주문 파일=배열, 상태 파일=객체)가 올라갔고, 상태 파일은 <b>매 회차 예외 없이 실패</b>했다. 배열로 보내면 주문
   * 0건으로 정상 파싱돼 DB 에 아무 영향 없이 {@code committed/} 로 이동한다.
   *
   * <p>버려진 필드에 대해 — {@code timestamp} 는 파일명에 이미 들어 있고, {@code status} 와 {@code message} 는 {@code
   * jangbogo_status_} 접두사와 빈 배열로 충분히 드러난다. 페이로드 계약 {@code [{serial, datetime, mall_id, mallname,
   * items:[…]}]} 에는 메타데이터를 실을 자리가 없다.
   *
   * <p>이 파일의 존재 자체가 "수집은 돌았고 신규가 없었다"는 하트비트다. 보내지 않으면 수신측에서 그 상태와 "수집 자체가 죽었다"를 구분할 수 없다. (Phase
   * 3-6)
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

    // 주문 0건 = 빈 배열. 수신측 계약이 최상위 배열이므로 객체로 감싸지 않는다 (위 javadoc 참조).
    String statusJson = new org.json.simple.JSONArray().toJSONString();

    try (FileWriter writer = new FileWriter(filePath, java.nio.charset.StandardCharsets.UTF_8)) {
      writer.write(statusJson);
      writer.flush();
    }

    logger.info("FTP 업로드용 상태 파일 생성 완료 (신규 주문 0건): {}", filePath);
    return filePath;
  }
}

package com.jiniebox.jangbogo.svc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiniebox.jangbogo.dao.JbgItemDataAccessObject;
import com.jiniebox.jangbogo.dao.JbgMallDataAccessObject;
import com.jiniebox.jangbogo.dao.JbgOrderDataAccessObject;
import com.jiniebox.jangbogo.util.ExceptionUtil;
import com.jiniebox.jangbogo.util.JSONUtil;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class MallOrderUpdaterRunner implements Runnable {

  private static final Logger logger = LogManager.getLogger(MallOrderUpdaterRunner.class);

  private String seqMall, mallId, mallPw;

  // 신규 추가된 주문 seq 목록
  private List<Integer> newOrderSeqs = new ArrayList<>();

  /**
   * @param seqMall 수집할 쇼핑몰
   * @param mallId
   * @param mallPw
   */
  public MallOrderUpdaterRunner(String seqMall, String mallId, String mallPw) {
    this.seqMall = seqMall;
    this.mallId = mallId;
    this.mallPw = mallPw;
  }

  @Override
  public void run() {

    // 1. 각 쇼핑몰에서의 주문 내역들을 수집한다.
    // 2. 지니박스 데이터베이스에 저장한다.
    try {
      MallOrderUpdater mou = new MallOrderUpdater();
      JSONArray itemArr = mou.collectItems(this.seqMall, this.mallId, this.mallPw);

      JbgMallDataAccessObject jmDao = new JbgMallDataAccessObject();
      String mallName = jmDao.getName(this.seqMall);

      logger.info("===========================================================================");
      logger.info("쇼핑몰: {} (seq={})", mallName, this.seqMall);
      logger.info("수집된 주문 개수: {}", itemArr != null ? itemArr.size() : 0);
      logger.info("===========================================================================");

      if (itemArr == null || itemArr.isEmpty()) {
        logger.warn("수집된 주문 데이터가 없습니다. 쇼핑몰 seq={}", this.seqMall);
        return;
      }

      logger.debug(JSONUtil.JsonEnterConvert(itemArr.toJSONString()));
      logger.debug(
          "------------------------------------------------------------------------------------------------------------------------------------------------------");

      ObjectMapper mapper = new ObjectMapper();
      JsonNode root = mapper.readTree(itemArr.toJSONString());

      // 추출한 아이템 목록을 jbg_order와 jbg_item 테이블에 저장
      JbgOrderDataAccessObject joDao = new JbgOrderDataAccessObject();
      JbgItemDataAccessObject jiDao = new JbgItemDataAccessObject();
      int itemCount = 0;
      int orderCount = 0;
      int existingOrderCount = 0; // 이미 등록된 주문 개수
      int skippedOrders = 0;

      try {
        if (root != null && root.isArray()) {
          logger.info("JSON 파싱 완료, 처리할 주문 개수: {}", root.size());

          for (JsonNode order : root) {
            try {
              // 주문 정보 추출
              String serial = order.has("serial") ? order.get("serial").asText().trim() : "";
              String datetime = order.has("datetime") ? order.get("datetime").asText().trim() : "";
              String orderMallName =
                  order.has("mallname") ? order.get("mallname").asText().trim() : null;

              logger.debug(
                  "주문 처리 중 - serial: {}, datetime: {}, mallname: {}",
                  serial,
                  datetime,
                  orderMallName);

              // datetime이 없으면 스킵
              if (datetime == null || datetime.isEmpty()) {
                logger.warn("주문 datetime이 없어 스킵합니다. serial: {}", serial);
                skippedOrders++;
                continue;
              }

              // 동일한 serial_num과 date_time이 있는지 확인 (중복 방지)
              JSONObject existingOrder = joDao.getOrder(serial, datetime, null);
              int seqOrder = -1;

              if (existingOrder != null) {
                // 이미 존재하는 주문이면 아이템 저장 건너뜀 (중복 방지)
                seqOrder = Integer.parseInt(existingOrder.get("seq").toString());
                existingOrderCount++;
                logger.debug(
                    "기존 주문 발견, 아이템 저장 건너뜀 - seq_order: {}, serial: {}, datetime: {}",
                    seqOrder,
                    serial,
                    datetime);
                continue; // 다음 주문으로
              }

              // 새로운 주문 등록
              // datetime 형식 변환 (다양한 형식 지원: YYYYMMDD, YYYY-MM-DD, YYYYMMDDHHmmss 등)
              int dateTimeInt = 0;
              try {
                // 숫자만 추출 (YYYYMMDD 형식으로 변환)
                String dateTimeStr = datetime.replaceAll("[^0-9]", "");
                if (dateTimeStr.length() >= 8) {
                  // 최소 8자리 (YYYYMMDD)만 사용
                  dateTimeInt = Integer.parseInt(dateTimeStr.substring(0, 8));
                } else {
                  throw new NumberFormatException("날짜 형식이 너무 짧습니다: " + datetime);
                }
              } catch (NumberFormatException e) {
                logger.warn(
                    "datetime 형식 오류: {}, serial: {}, 오류: {}", datetime, serial, e.getMessage());
                skippedOrders++;
                continue;
              }

              // 주문과 아이템을 하나의 트랜잭션으로 처리
              com.jiniebox.jangbogo.dao.LocalDBConnection conn = null;
              try {
                conn = new com.jiniebox.jangbogo.dao.LocalDBConnection();
                conn.txOpen();

                // 주문 저장 (PreparedStatement 사용, SQL Injection 방지)
                seqOrder =
                    joDao.addWithConnection(
                        conn, serial, String.valueOf(dateTimeInt), orderMallName, this.seqMall);
                orderCount++;

                // 신규 추가된 주문 seq 저장
                newOrderSeqs.add(seqOrder);

                logger.info(
                    "새 주문 등록 완료 (트랜잭션 내), seq_order: {}, serial: {}, datetime: {}, mallname: {}",
                    seqOrder,
                    serial,
                    datetime,
                    orderMallName);

                // 새 주문일 때만 아이템 목록 처리
                JsonNode items = order.get("items");
                if (items != null && items.isArray()) {
                  for (JsonNode item : items) {
                    try {
                      if (item.has("name") && item.get("name") != null) {
                        String itemName = item.get("name").asText().trim();
                        if (itemName.isEmpty()) {
                          continue;
                        }

                        // qty 필드 추출 (있는 경우)
                        String qty = null;
                        if (item.has("qty") && item.get("qty") != null) {
                          qty = item.get("qty").asText().trim();
                        }

                        // 아이템 등록 (PreparedStatement 사용, SQL Injection 방지)
                        jiDao.addWithConnection(conn, itemName, String.valueOf(seqOrder), qty);
                        itemCount++;

                        if (qty != null && !qty.isEmpty()) {
                          logger.debug(
                              "아이템 저장 완료 (트랜잭션 내): {}, qty: {}, seq_order: {}",
                              itemName,
                              qty,
                              seqOrder);
                        } else {
                          logger.debug("아이템 저장 완료 (트랜잭션 내): {}, seq_order: {}", itemName, seqOrder);
                        }
                      }
                    } catch (Exception itemEx) {
                      logger.warn("아이템 저장 중 오류 발생: {}", ExceptionUtil.getExceptionInfo(itemEx));
                      // 아이템 저장 실패 시 전체 트랜잭션 롤백
                      throw itemEx;
                    }
                  }
                }

                // 모든 작업 성공 시 커밋
                conn.txCommit();
                logger.debug("주문 및 아이템 저장 트랜잭션 커밋 완료 - seq_order: {}", seqOrder);

              } catch (Exception txEx) {
                // 트랜잭션 실패 시 롤백
                if (conn != null) {
                  try {
                    conn.txRollBack();
                    logger.warn(
                        "주문 및 아이템 저장 트랜잭션 롤백 - serial: {}, 오류: {}", serial, txEx.getMessage());
                  } catch (Exception rollbackEx) {
                    logger.error("트랜잭션 롤백 실패", rollbackEx);
                  }
                }
                // 롤백 후 예외를 다시 던져서 다음 주문으로 진행
                logger.error("주문 저장 중 트랜잭션 오류 발생: {}", ExceptionUtil.getExceptionInfo(txEx));
                skippedOrders++;
                // newOrderSeqs에서 제거 (롤백되었으므로)
                if (seqOrder > 0 && newOrderSeqs.contains(seqOrder)) {
                  newOrderSeqs.remove(Integer.valueOf(seqOrder));
                }
                orderCount--; // 카운트 조정
              } finally {
                if (conn != null) {
                  try {
                    conn.close();
                  } catch (Exception closeEx) {
                    logger.warn("Connection 종료 중 오류: {}", closeEx.getMessage());
                  }
                }
              }
            } catch (Exception orderEx) {
              logger.warn("주문 저장 중 오류 발생: {}", ExceptionUtil.getExceptionInfo(orderEx));
            }
          }
        }

        logger.info("===========================================================================");
        logger.info("쇼핑몰 수집 완료 - mall: {} (seq={})", mallName, this.seqMall);
        logger.info("신규 주문: {}개, 신규 아이템: {}개", orderCount, itemCount);
        logger.info("기존 주문(중복): {}개, 스킵된 주문: {}개", existingOrderCount, skippedOrders);
        logger.info("신규 주문 seq 목록: {}", newOrderSeqs);
        logger.info("===========================================================================");
      } catch (Exception e) {
        logger.error("아이템 저장 처리 중 오류 발생: {}", ExceptionUtil.getExceptionInfo(e));
      }

    } catch (Exception e) {
      logger.error("쇼핑몰 수집 실행 중 오류 발생: {}", ExceptionUtil.getExceptionInfo(e));
    }
  }

  /**
   * 신규 추가된 주문 seq 목록 조회
   *
   * @return 신규 주문 seq 리스트
   */
  public List<Integer> getNewOrderSeqs() {
    return new ArrayList<>(newOrderSeqs);
  }
}

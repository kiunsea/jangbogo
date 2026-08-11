package com.jiniebox.jangbogo.svc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiniebox.jangbogo.dao.JbgCollectBreakerDataAccessObject;
import com.jiniebox.jangbogo.dao.JbgCollectLogDataAccessObject;
import com.jiniebox.jangbogo.dao.JbgItemDataAccessObject;
import com.jiniebox.jangbogo.dao.JbgMallDataAccessObject;
import com.jiniebox.jangbogo.dao.JbgOrderDataAccessObject;
// CollectException import (svc 패키지 동일이라 불필요하지만 명시)
import com.jiniebox.jangbogo.svc.util.CollectPeriod;
import com.jiniebox.jangbogo.svc.util.ErrorSummary;
import com.jiniebox.jangbogo.util.ExceptionUtil;
import com.jiniebox.jangbogo.util.JSONUtil;
import com.jiniebox.jangbogo.util.LogMask;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
   * 이번 회차에 관측된 세션 만료 사유. 만료가 없었거나 승격 대상이 아니면 null (Phase 5-10 배선).
   *
   * <p>{@link #collectOutcome()} 가 이 값을 읽어 실행 결과를 {@code MallCollectOutcome.sessionExpired} 로 바꾼다.
   * 이 필드가 없던 동안 만료는 수집기별 SKIPPED 한 행에서 끝났고, 그래서 {@code MallSchedulerService}·{@code
   * AdminController} 의 만료 분기는 <b>도달 불가 코드</b>였다 — 테스트는 전부 초록인데 일시중단도 재개도 실제로는 일어나지 않았다.
   */
  private String sessionExpiredReason;

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
    long startedAt = System.currentTimeMillis();
    String mallName = null;
    try {
      MallOrderUpdater mou = new MallOrderUpdater();
      JSONArray itemArr = mou.collectItems(this.seqMall, this.mallId, this.mallPw);
      List<MallOrderUpdater.CollectOutcome> outcomes = mou.getOutcomes();

      // 세션 만료를 '실행 결과' 로 승격한다 — 이 한 줄이 없으면 만료는 수집기 한 행에서 끝나고,
      // 위쪽의 일시중단·재개는 입력이 오지 않아 통째로 무동작이 된다 (Phase 5-10 배선).
      //
      // 승격을 아래 DB 접촉보다 '앞' 에 둔다. 순수 함수라 여기서 돌 수 있고, 뒤에 두면 몰 이름 조회나
      // 로그 저장이 한 번 흔들리는 것만으로 이번 회차의 만료가 통째로 사라진다 — 바깥 catch 로 빠져
      // 이 필드가 null 로 남으면 collectOutcome() 이 '0건 수집 성공' 을 돌려주고, 일시중단이 걸리지
      // 않아 죽은 세션으로 매 주기 몰을 계속 두드리게 된다. 관측한 사실을 DB 가용성에 걸지 않는다.
      this.sessionExpiredReason = promotableExpiryReason(outcomes, itemArr);

      JbgMallDataAccessObject jmDao = new JbgMallDataAccessObject();
      mallName = jmDao.getName(this.seqMall);

      // 수집기별 결과(성공·실패·브레이커 건너뜀)를 각각 한 행으로 남긴다.
      // 실행 단위 집계 행은 아래에서 따로 기록된다.
      recordCollectorOutcomes(outcomes, mallName, startedAt);

      logger.info("===========================================================================");
      logger.info("쇼핑몰: {} (seq={})", mallName, this.seqMall);
      logger.info("수집된 주문 개수: {}", itemArr != null ? itemArr.size() : 0);
      logger.info("===========================================================================");

      if (itemArr == null || itemArr.isEmpty()) {
        if (this.sessionExpiredReason != null) {
          // 세션이 죽어서 0건인 회차다. 여기서 SUCCESS 집계 행을 적으면 화면에는 '수집 성공' 으로
          // 보이면서 실제로는 아무것도 모이지 않는다 — 사람이 다시 로그인해야 한다는 사실이
          // 대시보드에서 지워진다. 만료 행은 호출부(스케줄러·즉시수집)가 SKIPPED 로 한 번 적는다.
          logger.warn("쇼핑몰 seq={} 세션 만료로 수집된 주문이 없다 — 성공으로 적지 않는다", this.seqMall);
          return;
        }
        logger.warn("수집된 주문 데이터가 없습니다. 쇼핑몰 seq={}", this.seqMall);
        // 수집된 데이터가 없어도 성공으로 기록
        saveCollectLog(
            Integer.parseInt(this.seqMall), mallName, "SUCCESS", 0, 0, null, null, startedAt);
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

      // 이번 회차에 <b>저장을 확인하지 못한</b> 주문의 구매일 — 수집기별로 가장 이른 것.
      //
      // 아래 루프는 주문 하나가 깨지면 그것만 롤백하고 계속 간다. 그 격리 자체는 옳지만,
      // 같은 회차의 더 늦은 주문이 커밋되면 jbg_order 의 MAX(date_time) 이 실패한 날짜를
      // 지나가고, 다음 회차의 조회 시작일이 그 뒤가 되어 그 구간은 영영 조회되지 않는다.
      // CollectPeriod 가 약속한 자기교정이 정확히 이 지점에서 깨진다.
      //
      // 그래서 실패한 날짜를 여기 모아 두었다가 회차 끝에서 재조회 바닥으로 적는다.
      Map<String, String> unsavedByCollector = new LinkedHashMap<>();

      try {
        if (root != null && root.isArray()) {
          logger.info("JSON 파싱 완료, 처리할 주문 개수: {}", root.size());

          for (JsonNode order : root) {
            // 아래 try 밖에 둔다 — 저장에 실패한 주문의 구매일을 catch 에서도 적어야 하는데,
            // 안에 두면 그 자리에서 보이지 않는다. 못 적은 실패는 다음 회차에 조회되지 않는다.
            String collector = null;
            String orderYmd = null;
            try {
              // 주문 정보 추출
              String serial = order.has("serial") ? order.get("serial").asText().trim() : "";
              String datetime = order.has("datetime") ? order.get("datetime").asText().trim() : "";
              String orderMallName =
                  order.has("mallname") ? order.get("mallname").asText().trim() : null;

              // 어느 수집기가 가져온 주문인지. MallOrderUpdater.recordItems 가 새겨 준다.
              // 이 값이 jbg_order.collector 가 되고, 다음 회차의 조회 시작일이 여기서 유도된다
              // (CollectPeriod). 비어 있으면 그 수집기는 매 회차 기본 범위를 통째로 다시 훑는다.
              collector =
                  order.has(MallOrderUpdater.COLLECTOR_KEY)
                      ? order.get(MallOrderUpdater.COLLECTOR_KEY).asText().trim()
                      : null;

              // 값을 싣지 않는다 — 주문번호·구매일자·매장명이 그대로 로그에 남는다.
              // 형식만 남겨도 이 줄이 답해야 할 질문("형식이 깨졌는가")에는 답할 수 있다.
              logger.debug(
                  "주문 처리 중 - serial: {}, datetime: {}, mallname: {}",
                  LogMask.shape(serial),
                  LogMask.shape(datetime),
                  LogMask.name(orderMallName));

              // datetime이 없으면 스킵
              if (datetime == null || datetime.isEmpty()) {
                logger.warn("주문 datetime이 없어 스킵합니다. serial: {}", LogMask.shape(serial));
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
                    LogMask.shape(serial),
                    LogMask.shape(datetime));
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
                  // 저장에 실패하면 이 날짜부터 다시 조회해야 한다. 저장 시도 전에 잡아 둔다.
                  orderYmd = String.valueOf(dateTimeInt);
                } else {
                  throw new NumberFormatException("날짜 형식이 너무 짧습니다: " + datetime);
                }
              } catch (NumberFormatException e) {
                // 형식 오류를 알리는 줄이라 형식은 반드시 남아야 한다 — shape 가 정확히 그것만 남긴다.
                logger.warn(
                    "datetime 형식 오류: {}, serial: {}, 오류: {}",
                    LogMask.shape(datetime),
                    LogMask.shape(serial),
                    e.getMessage());
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
                        conn,
                        serial,
                        String.valueOf(dateTimeInt),
                        orderMallName,
                        this.seqMall,
                        collector);
                orderCount++;

                // 신규 추가된 주문 seq 저장
                newOrderSeqs.add(seqOrder);

                logger.info(
                    "새 주문 등록 완료 (트랜잭션 내), seq_order: {}, serial: {}, datetime: {}, mallname: {}",
                    seqOrder,
                    LogMask.shape(serial),
                    LogMask.shape(datetime),
                    LogMask.name(orderMallName));

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
                              LogMask.name(itemName),
                              qty,
                              seqOrder);
                        } else {
                          logger.debug(
                              "아이템 저장 완료 (트랜잭션 내): {}, seq_order: {}",
                              LogMask.name(itemName),
                              seqOrder);
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
                        "주문 및 아이템 저장 트랜잭션 롤백 - serial: {}, 오류: {}",
                        LogMask.shape(serial),
                        txEx.getMessage());
                  } catch (Exception rollbackEx) {
                    logger.error("트랜잭션 롤백 실패", rollbackEx);
                  }
                }
                // 롤백 후 예외를 다시 던져서 다음 주문으로 진행
                logger.error("주문 저장 중 트랜잭션 오류 발생: {}", ExceptionUtil.getExceptionInfo(txEx));
                skippedOrders++;
                // 롤백된 날짜를 적어 둔다. 이것을 빠뜨리면 같은 회차의 더 늦은 주문이 커밋될 때
                // 조회 기준일이 이 날짜를 지나가고, 이 구간은 영영 다시 조회되지 않는다.
                noteUnsaved(unsavedByCollector, collector, orderYmd);
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
              // 중복 조회 등 트랜잭션 밖에서 깨진 경우다. 저장을 확인하지 못한 것은 같으므로
              // 같은 규칙으로 적는다 — 구매일을 아직 못 읽었으면 적을 것이 없다.
              noteUnsaved(unsavedByCollector, collector, orderYmd);
            }
          }
        }

        logger.info("===========================================================================");
        logger.info("쇼핑몰 수집 완료 - mall: {} (seq={})", mallName, this.seqMall);
        logger.info("신규 주문: {}개, 신규 아이템: {}개", orderCount, itemCount);
        logger.info("기존 주문(중복): {}개, 스킵된 주문: {}개", existingOrderCount, skippedOrders);
        logger.info("신규 주문 seq 목록: {}", newOrderSeqs);
        logger.info("===========================================================================");

        // 다음 회차가 어디부터 다시 봐야 하는지를 적는다. 수집 로그보다 앞에 둔다 —
        // 로그 저장이 한 번 흔들리는 것만으로 이번 회차의 구멍이 통째로 사라지면 안 된다.
        recordRetryFloors(outcomes, unsavedByCollector);

        // 수집 결과 로그 저장
        String logStatus = decideStatus(orderCount, existingOrderCount, skippedOrders);
        String logErrorMsg = (skippedOrders > 0) ? "스킵된 주문: " + skippedOrders + "개" : null;
        saveCollectLog(
            Integer.parseInt(this.seqMall),
            mallName,
            logStatus,
            orderCount,
            itemCount,
            logErrorMsg,
            null,
            startedAt);

      } catch (Exception e) {
        logger.error("아이템 저장 처리 중 오류 발생: {}", ExceptionUtil.getExceptionInfo(e));
        saveCollectLog(
            Integer.parseInt(this.seqMall),
            mallName,
            "FAIL",
            0,
            0,
            ErrorSummary.summarize(e),
            ExceptionUtil.getExceptionInfo(e),
            startedAt);
      }

    } catch (Exception e) {
      logger.error("쇼핑몰 수집 실행 중 오류 발생: {}", ExceptionUtil.getExceptionInfo(e));
      int seqMallInt = 0;
      try {
        seqMallInt = Integer.parseInt(this.seqMall);
      } catch (NumberFormatException ignore) {
      }
      // CollectException이면 컨텍스트 추출, 아니면 step="unknown"으로 기록
      CollectException ce = unwrapCollectException(e);
      JbgCollectLogDataAccessObject logDao = new JbgCollectLogDataAccessObject();
      logDao.addLog(
          seqMallInt,
          mallName,
          "FAIL",
          0,
          0,
          ErrorSummary.summarize(e),
          ExceptionUtil.getExceptionInfo(e),
          ce != null ? ce.getStepName() : "unknown",
          ce != null ? ce.getCurrentUrl() : null,
          ce != null ? ce.getPageTitle() : null,
          ce != null ? ce.getTargetSelector() : null,
          ce != null ? ce.getScreenshotPath() : null,
          startedAt,
          System.currentTimeMillis());
    }
  }

  /**
   * 이번 회차의 실행 결과 (Phase 5-10 배선).
   *
   * <p><b>이 메서드가 만료를 사용자에게 닿게 하는 유일한 통로다.</b> {@code JangBoGoManager.collect} 가 이 값을 그대로 돌려주고, 그것을
   * 스케줄러는 일시중단으로, 즉시수집은 응답 JSON 의 {@code sessionExpired} 목록으로 읽는다. 여기서 만료를 흘리면 그 아래 모든 만료 처리가 도달 불가
   * 코드가 된다.
   *
   * @return 세션이 만료된 회차면 만료 결과, 아니면 신규 주문 목록을 실은 성공 결과
   */
  public MallCollectOutcome collectOutcome() {
    if (sessionExpiredReason != null) {
      return MallCollectOutcome.sessionExpired(sessionExpiredReason);
    }
    return MallCollectOutcome.success(newOrderSeqs);
  }

  /**
   * 이번 회차의 만료를 실행 결과로 <b>승격할 것인가</b>, 승격한다면 어떤 사유로 할 것인가.
   *
   * <p>순수 함수다 — DB·브라우저를 건드리지 않는다.
   *
   * <h2>다른 수집기가 주문을 가져왔으면 승격하지 않는다</h2>
   *
   * <p>seq=1 처럼 수집기가 둘인 몰에서, SSG 세션이 죽어도 Emart 오프라인 영수증은 멀쩡히 수집될 수 있다. 그 회차를 만료로 승격하면 두 가지가 함께
   * 망가진다.
   *
   * <ul>
   *   <li>만료 결과는 신규 주문 목록이 <b>빈 목록</b>이라, 스케줄러가 {@code processFileExport} 앞에서 물러난다 — 방금 DB 에 저장한
   *       주문이 내보내기에서 통째로 빠지고, 다음 회차에는 중복으로 걸러져 <b>영영 나가지 않는다.</b>
   *   <li>몰 단위로 일시중단되므로 멀쩡한 Emart 수집까지 사람이 다시 로그인할 때까지 멈춘다.
   * </ul>
   *
   * <p>승격하지 않아도 그 회차의 만료는 <b>수집기별 SKIPPED 한 행</b>으로 남아 수집 로그 화면에 그대로 보인다. 사라지는 것은 '몰 전체 일시중단' 뿐이고,
   * 그것은 애초에 세션 말고는 수집할 방법이 없는 몰을 위한 장치다.
   *
   * @param outcomes 수집기별 결과
   * @param items 이번 회차에 모은 주문 목록
   * @return 승격할 만료 사유. 승격하지 않으면 null
   */
  static String promotableExpiryReason(
      List<MallOrderUpdater.CollectOutcome> outcomes, JSONArray items) {

    if (items != null && !items.isEmpty()) {
      return null;
    }
    if (outcomes == null) {
      return null;
    }
    for (MallOrderUpdater.CollectOutcome outcome : outcomes) {
      if (outcome != null && outcome.sessionExpired()) {
        return outcome.reason();
      }
    }
    return null;
  }

  /**
   * 저장을 확인하지 못한 주문의 구매일을 수집기별로 모은다. <b>가장 이른 것만 남긴다.</b>
   *
   * <p>가장 이른 것이 기준인 이유는, 다음 회차가 <b>모든</b> 구멍을 덮어야 하기 때문이다. 늦은 쪽을 남기면 그보다 이른 구멍은 그대로 봉인된다.
   *
   * <p>수집기 이름이나 구매일이 없으면 적지 않는다. 수집기를 모르면 어느 조회 시작일을 되돌려야 할지 정할 수 없고, 구매일을 못 읽은 주문은 애초에 저장할 키가 없어
   * 다시 가져와도 같은 자리에서 걸린다.
   *
   * @param unsavedByCollector 수집기별 가장 이른 미저장 구매일 (제자리에서 갱신된다)
   * @param collector 수집기 이름
   * @param ymd 저장하지 못한 주문의 구매일 {@code yyyyMMdd}
   */
  static void noteUnsaved(Map<String, String> unsavedByCollector, String collector, String ymd) {
    if (unsavedByCollector == null || collector == null || collector.isBlank() || ymd == null) {
      return;
    }
    unsavedByCollector.merge(collector, ymd, CollectPeriod::earlier);
  }

  /**
   * 이번 회차가 각 수집기에 남길 <b>재조회 바닥</b>을 정한다. 순수 함수다 — DB·브라우저를 건드리지 않는다.
   *
   * <h2>완주한 수집기만 손댄다</h2>
   *
   * <p>실패하거나 건너뛴 수집기는 자기 조회 구간을 <b>한 번도 훑지 못했다.</b> 그 자리의 바닥을 지우면 이전 회차가 남긴 구멍이 사라지고, 새로 적으면 훑지도 않은
   * 구간을 근거로 적는 것이 된다. 아무것도 하지 않는 것이 유일하게 옳다.
   *
   * <h2>0건({@code EMPTY}) 으로는 바닥을 지우지 않는다</h2>
   *
   * <p>0건은 <b>두 가지를 뜻한다</b> — 정말 그 기간에 산 것이 없거나, 셀렉터가 어긋나 아무것도 못 읽었거나. 한 회차로는 구분할 수 없다({@code
   * MallOrderUpdater.CollectOutcome.EMPTY} javadoc). 앞쪽이면 지워도 되지만 뒤쪽이면 지우는 순간 구멍이 봉인된다 — 그리고 그것은 이
   * 수정이 막으려는 바로 그 실패다.
   *
   * <p>그래서 <b>실제로 주문을 받아 온 회차({@code SUCCESS})</b>에서만 지운다. 대가는 0건이 이어지는 동안 그 수집기가 매 회차 조금 더 이른 날짜부터
   * 조회하는 것뿐이고, 겹쳐 가져온 것은 중복 판정이 걸러 낸다.
   *
   * @param outcomes 수집기별 이번 회차 결과
   * @param unsavedByCollector 수집기별 가장 이른 미저장 구매일
   * @return 수집기 이름 → 적을 바닥. 값이 {@code null} 이면 <b>지우라는 뜻</b>이다. 손대지 않을 수집기는 아예 담기지 않는다
   */
  static Map<String, String> retryFloors(
      List<MallOrderUpdater.CollectOutcome> outcomes, Map<String, String> unsavedByCollector) {

    Map<String, String> floors = new LinkedHashMap<>();
    if (outcomes == null) {
      return floors;
    }
    Map<String, String> unsaved =
        unsavedByCollector == null ? Map.of() : new LinkedHashMap<>(unsavedByCollector);

    for (MallOrderUpdater.CollectOutcome outcome : outcomes) {
      if (outcome == null || outcome.collector() == null || outcome.collector().isBlank()) {
        continue;
      }
      String unsavedYmd = unsaved.get(outcome.collector());
      if (unsavedYmd != null) {
        // 훑은 구간에 구멍이 났다. 성공이든 0건이든 상관없이 그 자리를 적는다.
        floors.put(outcome.collector(), unsavedYmd);
      } else if (outcome.isSuccess()) {
        // 주문을 받아 와서 전부 저장했다. 이전 회차가 남긴 구멍도 이 회차가 덮었다.
        floors.put(outcome.collector(), null);
      }
    }
    return floors;
  }

  /** {@link #retryFloors} 가 정한 바닥을 브레이커 테이블에 적는다. 실패해도 수집 결과 기록을 막지 않는다. */
  private void recordRetryFloors(
      List<MallOrderUpdater.CollectOutcome> outcomes, Map<String, String> unsavedByCollector) {

    int seqMallInt;
    try {
      seqMallInt = Integer.parseInt(this.seqMall);
    } catch (NumberFormatException notANumber) {
      return;
    }
    if (seqMallInt <= 0) {
      return;
    }

    Map<String, String> floors = retryFloors(outcomes, unsavedByCollector);
    if (floors.isEmpty()) {
      return;
    }

    JbgCollectBreakerDataAccessObject breakerDao = new JbgCollectBreakerDataAccessObject();
    for (Map.Entry<String, String> floor : floors.entrySet()) {
      if (floor.getValue() != null) {
        logger.warn(
            "수집기 {} 의 주문 일부를 저장하지 못했다 — 다음 회차는 {} 부터 다시 조회한다", floor.getKey(), floor.getValue());
      }
      try {
        breakerDao.saveRetryFrom(seqMallInt, floor.getKey(), floor.getValue());
      } catch (Exception e) {
        logger.warn("재조회 기준일 저장 중 오류 (수집기: {}): {}", floor.getKey(), e.getMessage());
      }
    }
  }

  /**
   * 수집 실행 결과의 SUCCESS/FAIL 을 판정한다.
   *
   * <p>실패로 볼 조건은 "수집해 온 주문이 하나도 쓸 수 없었을 때" 뿐이다.
   *
   * <ul>
   *   <li><b>신규 주문 0 은 실패가 아니다.</b> 이미 수집을 마친 뒤의 재실행은 전부 중복({@code existingOrderCount})으로 걸러지는 것이
   *       정상이다. 이를 FAIL 로 찍으면 데이터가 따라잡힌 시점부터 모든 주기 실행이 영구히 실패로 기록되어 대시보드가 거짓말을 한다.
   *   <li><b>스킵 자체도 실패가 아니다.</b> serial/date_time 이 없는 주문은 키가 없어 저장할 수 없으므로 건너뛰는 것이 의도된 동작이다.
   * </ul>
   *
   * <p>수집기 자체의 실패는 이 판정과 별개로 {@link #recordPartialFailures} 가 FAIL 행으로 남긴다.
   *
   * @param orderCount 신규 등록된 주문 수
   * @param existingOrderCount 이미 등록돼 있어 건너뛴 주문 수
   * @param skippedOrders 키 누락 등으로 저장하지 못한 주문 수
   * @return "SUCCESS" 또는 "FAIL"
   */
  static String decideStatus(int orderCount, int existingOrderCount, int skippedOrders) {
    boolean nothingUsable = orderCount == 0 && existingOrderCount == 0 && skippedOrders > 0;
    return nothingUsable ? "FAIL" : "SUCCESS";
  }

  /**
   * 부분 실패(수집기 일부만 실패)를 각각 jbg_collect_log 에 FAIL 로 기록한다.
   *
   * <p>seq=1 처럼 수집기가 여러 개인 쇼핑몰에서, 한쪽 실패가 다른 쪽 수집을 막지 않도록 격리한 대가로 실패가 로그에서 사라지면 안 된다. step_name 에
   * 수집기 이름을 붙여 어느 쪽이 실패했는지 구분한다.
   *
   * @param failures 부분 실패 목록
   * @param mallName 쇼핑몰 이름
   * @param startedAt 실행 시작 시간
   */
  private void recordCollectorOutcomes(
      List<MallOrderUpdater.CollectOutcome> outcomes, String mallName, long startedAt) {
    if (outcomes == null || outcomes.isEmpty()) {
      return;
    }

    int seqMallInt = 0;
    try {
      seqMallInt = Integer.parseInt(this.seqMall);
    } catch (NumberFormatException ignore) {
    }

    JbgCollectLogDataAccessObject logDao = new JbgCollectLogDataAccessObject();
    for (MallOrderUpdater.CollectOutcome outcome : outcomes) {
      CollectException ce = outcome.cause();
      if (outcome.isFailure()) {
        logger.warn("수집기 실패 기록 - 수집기: {}, 단계: {}", outcome.collector(), ce.getStepName());
      }
      // 만료 행에는 단계 이름을 붙인다. 수집 로그 화면이 이 값으로 단계 필터를 만들기 때문에,
      // 비워 두면 만료가 '단계 없음' 무리에 섞여 사람이 골라 볼 수 없다. 관측 도중 난 오류와
      // 같은 이름이라(SessionExpiryDetector.STEP_DETECT_EXPIRY) 둘이 한 화면에 모인다.
      String step =
          ce != null
              ? ce.getStepName()
              : (outcome.sessionExpired() ? MallCollectOutcome.STEP_SESSION_EXPIRY : null);
      try {
        logDao.addLog(
            seqMallInt,
            mallName,
            outcome.collector(),
            outcome.status(),
            0,
            0,
            ce != null ? ce.getMessage() : outcome.reason(),
            ce != null ? ExceptionUtil.getExceptionInfo(ce) : null,
            step,
            ce != null ? ce.getCurrentUrl() : null,
            ce != null ? ce.getPageTitle() : null,
            ce != null ? ce.getTargetSelector() : null,
            ce != null ? ce.getScreenshotPath() : null,
            startedAt,
            System.currentTimeMillis());
      } catch (Exception e) {
        logger.warn("수집기 결과 로그 저장 중 오류: {}", e.getMessage());
      }
    }
  }

  /** 예외 체인을 거슬러 올라가 첫 번째 CollectException을 찾는다. 없으면 null. */
  private CollectException unwrapCollectException(Throwable t) {
    Throwable cur = t;
    int safety = 0;
    while (cur != null && safety++ < 20) {
      if (cur instanceof CollectException) {
        return (CollectException) cur;
      }
      cur = cur.getCause();
    }
    return null;
  }

  /**
   * 수집 실행 로그를 DB에 저장
   *
   * @param seqMall 쇼핑몰 seq
   * @param mallName 쇼핑몰 이름
   * @param status SUCCESS / FAIL
   * @param orderCount 수집된 주문 수
   * @param itemCount 수집된 아이템 수
   * @param errorMessage 오류 메시지
   * @param errorDetail 상세 오류
   * @param startedAt 실행 시작 시간
   */
  private void saveCollectLog(
      int seqMall,
      String mallName,
      String status,
      int orderCount,
      int itemCount,
      String errorMessage,
      String errorDetail,
      long startedAt) {
    try {
      JbgCollectLogDataAccessObject logDao = new JbgCollectLogDataAccessObject();
      logDao.addLog(
          seqMall,
          mallName,
          status,
          orderCount,
          itemCount,
          errorMessage,
          errorDetail,
          startedAt,
          System.currentTimeMillis());
    } catch (Exception e) {
      logger.warn("수집 로그 저장 중 오류: {}", e.getMessage());
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

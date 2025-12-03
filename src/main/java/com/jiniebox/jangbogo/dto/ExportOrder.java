package com.jiniebox.jangbogo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/** 파일 저장용 주문 정보 DTO */
public class ExportOrder {

  @JsonProperty("serial_num")
  private String serialNum; // 영수증 또는 주문번호

  @JsonProperty("date_time")
  private String dateTime; // 구매일자 (YYYYMMDD)

  @JsonProperty("mall_name")
  private String mallName; // 매장명

  @JsonProperty("mall_id")
  private String mallId; // 쇼핑몰 ID (ssg, oasis 등)

  @JsonProperty("item_cnt")
  private int itemCnt; // 아이템 개수

  @JsonProperty("items")
  private List<String> items; // 구매 아이템 목록 (상품명)

  public ExportOrder() {
    this.items = new ArrayList<>();
  }

  public ExportOrder(String serialNum, String dateTime, String mallName, String mallId) {
    this.serialNum = serialNum;
    this.dateTime = dateTime;
    this.mallName = mallName;
    this.mallId = mallId;
    this.items = new ArrayList<>();
  }

  // Getters and Setters

  public String getSerialNum() {
    return serialNum;
  }

  public void setSerialNum(String serialNum) {
    this.serialNum = serialNum;
  }

  public String getDateTime() {
    return dateTime;
  }

  public void setDateTime(String dateTime) {
    this.dateTime = dateTime;
  }

  public String getMallName() {
    return mallName;
  }

  public void setMallName(String mallName) {
    this.mallName = mallName;
  }

  public String getMallId() {
    return mallId;
  }

  public void setMallId(String mallId) {
    this.mallId = mallId;
  }

  public int getItemCnt() {
    return items != null ? items.size() : 0;
  }

  public void setItemCnt(int itemCnt) {
    this.itemCnt = itemCnt; // JSON 역직렬화용
  }

  public List<String> getItems() {
    return items;
  }

  public void setItems(List<String> items) {
    this.items = items;
  }

  public void addItem(String itemName) {
    if (itemName != null && !itemName.isEmpty()) {
      this.items.add(itemName);
    }
  }
}

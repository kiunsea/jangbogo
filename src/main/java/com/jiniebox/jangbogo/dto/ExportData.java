package com.jiniebox.jangbogo.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 파일 저장용 전체 데이터 구조 DTO
 */
public class ExportData {
    
    @JsonProperty("export_info")
    private Map<String, Object> exportInfo; // 내보내기 정보 (날짜, 버전 등)
    
    @JsonProperty("malls")
    private Map<String, List<ExportOrder>> malls; // 쇼핑몰별 주문 목록
    
    public ExportData() {
        this.exportInfo = new HashMap<>();
        this.malls = new HashMap<>();
    }
    
    public Map<String, Object> getExportInfo() {
        return exportInfo;
    }
    
    public void setExportInfo(Map<String, Object> exportInfo) {
        this.exportInfo = exportInfo;
    }
    
    public Map<String, List<ExportOrder>> getMalls() {
        return malls;
    }
    
    public void setMalls(Map<String, List<ExportOrder>> malls) {
        this.malls = malls;
    }
    
    public void addOrder(String mallId, ExportOrder order) {
        if (!malls.containsKey(mallId)) {
            malls.put(mallId, new ArrayList<>());
        }
        malls.get(mallId).add(order);
    }
    
    public void setExportInfoValue(String key, Object value) {
        this.exportInfo.put(key, value);
    }
}


package com.jiniebox.jangbogo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 쇼핑몰 계정 정보 DTO
 */
public class MallAccount {
    
    @JsonProperty("site")
    private String site;  // 쇼핑몰 사이트명 (coupang, gmarket, ssg, oasis 등)
    
    @JsonProperty("id")
    private String id;    // 사용자 ID
    
    @JsonProperty("pass")
    private String pass;  // 비밀번호
    
    // 기본 생성자
    public MallAccount() {
    }
    
    // 전체 생성자
    public MallAccount(String site, String id, String pass) {
        this.site = site;
        this.id = id;
        this.pass = pass;
    }
    
    // Getters and Setters
    public String getSite() {
        return site;
    }
    
    public void setSite(String site) {
        this.site = site;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getPass() {
        return pass;
    }
    
    public void setPass(String pass) {
        this.pass = pass;
    }
    
    @Override
    public String toString() {
        return "MallAccount{" +
                "site='" + site + '\'' +
                ", id='" + id + '\'' +
                ", pass='" + (pass != null ? "***" : "null") + '\'' +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        MallAccount that = (MallAccount) o;
        return site != null ? site.equals(that.site) : that.site == null;
    }
    
    @Override
    public int hashCode() {
        return site != null ? site.hashCode() : 0;
    }
}


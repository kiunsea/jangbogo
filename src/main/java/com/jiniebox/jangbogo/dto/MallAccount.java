package com.jiniebox.jangbogo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 쇼핑몰 계정 정보 DTO
 */
public class MallAccount {
    
    @JsonProperty("seq")
    private String seq;  // 쇼핑몰 시퀀스 번호
    
    @JsonProperty("site")
    private String site;  // 쇼핑몰 사이트명 (coupang, gmarket, ssg, oasis 등)
    
    @JsonProperty("id")
    private String id;    // 사용자 ID (암호화된 값)
    
    @JsonProperty("pass")
    private String pass;  // 비밀번호 (암호화된 값)
    
    // 기본 생성자
    public MallAccount() {
    }
    
    // 전체 생성자 (seq 포함)
    public MallAccount(String seq, String site, String id, String pass) {
        this.seq = seq;
        this.site = site;
        this.id = id;
        this.pass = pass;
    }
    
    // 기존 생성자 (하위 호환성을 위해 유지)
    public MallAccount(String site, String id, String pass) {
        this.site = site;
        this.id = id;
        this.pass = pass;
    }
    
    // Getters and Setters
    public String getSeq() {
        return seq;
    }
    
    public void setSeq(String seq) {
        this.seq = seq;
    }
    
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
                "seq='" + seq + '\'' +
                ", site='" + site + '\'' +
                ", id='" + id + '\'' +
                ", pass='" + (pass != null ? "***" : "null") + '\'' +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        MallAccount that = (MallAccount) o;
        // seq를 기준으로 비교 (seq가 있으면 seq 기준, 없으면 site 기준)
        if (seq != null && that.seq != null) {
            return seq.equals(that.seq);
        }
        return site != null ? site.equals(that.site) : that.site == null;
    }
    
    @Override
    public int hashCode() {
        // seq를 우선적으로 사용
        if (seq != null) {
            return seq.hashCode();
        }
        return site != null ? site.hashCode() : 0;
    }
}


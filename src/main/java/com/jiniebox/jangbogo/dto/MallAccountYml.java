package com.jiniebox.jangbogo.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * mall_account.yml 전체 구조
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MallAccountYml {
    
    @JsonProperty("accounts")
    private List<MallAccount> accounts;
    
    // 기본 생성자
    public MallAccountYml() {
        this.accounts = new ArrayList<>();
    }
    
    // Getters and Setters
    public List<MallAccount> getAccounts() {
        return accounts;
    }
    
    public void setAccounts(List<MallAccount> accounts) {
        this.accounts = accounts;
    }
    
    /**
     * 특정 사이트의 계정 조회
     */
    public Optional<MallAccount> getAccountBySite(String site) {
        return accounts.stream()
                .filter(account -> site.equals(account.getSite()))
                .findFirst();
    }
    
    /**
     * 계정 추가 (중복 시 업데이트)
     */
    public void addOrUpdateAccount(MallAccount newAccount) {
        // 기존 계정 찾기
        Optional<MallAccount> existing = getAccountBySite(newAccount.getSite());
        
        if (existing.isPresent()) {
            // 업데이트
            MallAccount existingAccount = existing.get();
            existingAccount.setId(newAccount.getId());
            existingAccount.setPass(newAccount.getPass());
        } else {
            // 추가
            accounts.add(newAccount);
        }
    }
    
    /**
     * 계정 삭제
     */
    public boolean removeAccount(String site) {
        return accounts.removeIf(account -> site.equals(account.getSite()));
    }
    
    /**
     * 특정 사이트 계정 존재 여부
     */
    public boolean hasAccount(String site) {
        return getAccountBySite(site).isPresent();
    }
    
    /**
     * 전체 계정 수
     * JsonIgnore로 직렬화 제외
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public int getAccountCount() {
        return accounts.size();
    }
    
    /**
     * 모든 사이트명 목록 조회
     * JsonIgnore로 직렬화 제외
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public List<String> getAllSites() {
        return accounts.stream()
                .map(MallAccount::getSite)
                .toList();
    }
    
    @Override
    public String toString() {
        return "MallAccountYml{" +
                "accounts=" + accounts.size() + " accounts" +
                '}';
    }
}


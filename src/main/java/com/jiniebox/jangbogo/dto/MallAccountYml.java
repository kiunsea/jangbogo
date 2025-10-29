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
     * seq로 계정 조회
     */
    public Optional<MallAccount> getAccountBySeq(String seq) {
        return accounts.stream()
                .filter(account -> seq != null && seq.equals(account.getSeq()))
                .findFirst();
    }
    
    /**
     * 계정 추가 (중복 시 업데이트) - site 기준
     */
    public void addOrUpdateAccount(MallAccount newAccount) {
        // 기존 계정 찾기
        Optional<MallAccount> existing = getAccountBySite(newAccount.getSite());
        
        if (existing.isPresent()) {
            // 업데이트
            MallAccount existingAccount = existing.get();
            existingAccount.setSeq(newAccount.getSeq());
            existingAccount.setId(newAccount.getId());
            existingAccount.setPass(newAccount.getPass());
        } else {
            // 추가
            accounts.add(newAccount);
        }
    }
    
    /**
     * seq 기반 계정 추가/업데이트
     * 
     * 규칙:
     * 1. 같은 seq가 있으면 강제 업데이트
     * 2. 같은 site가 있지만 seq가 다르면, 기존 항목을 제거하고 새로 추가 (site 중복 방지)
     * 3. 둘 다 없으면 새로 추가
     */
    public void addOrUpdateAccountBySeq(MallAccount newAccount) {
        if (newAccount.getSeq() == null || newAccount.getSeq().isEmpty()) {
            throw new IllegalArgumentException("seq는 필수입니다.");
        }
        
        // 같은 seq가 있는지 확인 (우선 처리)
        Optional<MallAccount> existingBySeq = getAccountBySeq(newAccount.getSeq());
        
        if (existingBySeq.isPresent()) {
            // 같은 seq가 있으면 강제 업데이트
            MallAccount existingAccount = existingBySeq.get();
            existingAccount.setSite(newAccount.getSite());
            existingAccount.setId(newAccount.getId());
            existingAccount.setPass(newAccount.getPass());
            return;
        }
        
        // 새 항목 추가
        accounts.add(newAccount);
    }
    
    /**
     * 계정 삭제 - seq 기준
     */
    public boolean removeAccountBySeq(String seq) {
        return accounts.removeIf(account -> seq != null && seq.equals(account.getSeq()));
    }
    
    /**
     * 특정 seq 계정 존재 여부
     */
    public boolean hasAccountBySeq(String seq) {
        return getAccountBySeq(seq).isPresent();
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


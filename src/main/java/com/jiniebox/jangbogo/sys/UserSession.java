package com.jiniebox.jangbogo.sys;

import java.util.ArrayList;
import java.util.List;

import org.json.simple.JSONObject;

public class UserSession {

    private List<String> failedAuthMalls = new ArrayList<String>(); //로그인에 실패한 쇼핑몰 시퀀스 목록
    private JSONObject mallUsrid = null; 		//장보고 쇼핑몰 아이디 목록

    public JSONObject getMallUsrid() {
        return mallUsrid;
    }

    public String getMallUsrid(String seqMall) {
        return (this.mallUsrid != null && this.mallUsrid.get(seqMall) != null) ? mallUsrid.get(seqMall).toString() : null;
    }

    public void addMallUsrid(String seqMall, String mallUsrid) {
        if (this.mallUsrid == null) {
            this.mallUsrid = new JSONObject();
        }
        this.mallUsrid.put(seqMall, mallUsrid);
    }
    
    public void setMallUsrid(JSONObject mallUsrid) {
        this.mallUsrid = mallUsrid;
    }

    /**
     * 인증에 실패한 쇼핑몰인지 확인
     * 
     * @param seq
     * @return
     */
    public boolean checkFailedAuthMall(String seq) {
        return this.failedAuthMalls.contains(seq);
    }
    
    /**
     * 로그인에 실패한 쇼핑몰 시퀀스 목록<br/>
     * 함수 호출이후 목록을 초기화 한다.
     * 
     * @return
     */
    public List<String> getFailedAuthMalls() {
        List<String> rtnList = new ArrayList<String>();
        rtnList.addAll(this.failedAuthMalls);
        this.failedAuthMalls.clear();
        return rtnList;
    }

    /**
     * 로그인에 실패한 쇼핑몰 시퀀스 목록에 추가 (아이디가 없거나 비번 오류등)
     * 
     * @param seqm
     */
    public boolean addFailedAuthMallSeq(String seqm) {
        return this.failedAuthMalls.add(seqm);
    }
    
    public boolean removeFailedAuthMallSeq(String seqm) {
        return this.failedAuthMalls.remove(seqm);
    }

}

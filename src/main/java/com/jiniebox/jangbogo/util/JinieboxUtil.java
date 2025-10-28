package com.jiniebox.jangbogo.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;

public class JinieboxUtil {
	
	/**
	 * 서버 환경 변경시 log4j.properties 설정 정보도 함께 변경 필요
	 */
    private static final Logger logger = LogManager.getLogger(JinieboxUtil.class);

    public static int TEMP_SIZE_LIMIT = 100 * 1024; // 업로드시 사용할 임시 메모리 제한. 100K
    public static long UPLOAD_FILESIZE_LIMIT = 100000 * 1024 * 1024L; // 업로드 사이즈 제한. 10000M
    
    /**
     * 입력값 검사 (null 이거나 공백이라면 true)
     * 
     * @param param
     * @return
     */
    public static boolean isEmpty(String param) {
        if (param == null || param.trim().length() < 1)
            return true;
        return false;
    }
	
	/**
	 * 오늘 일자를 스트링으로 반환하는 유틸함수
	 * @return yyyyMMdd
	 */
	public static String getTodayString() {
		LocalDate today = LocalDate.now();
		int yearInt = today.getYear();
		int monthInt = today.getMonthValue();
		int dayInt = today.getDayOfMonth();

		String monthStr = (monthInt < 10) ? "0" + monthInt : Integer.toString(monthInt);
		String dayStr = (dayInt < 10) ? "0" + dayInt : Integer.toString(dayInt);

		return yearInt + monthStr + dayStr;
	}
	
	/**
	 * 현재 날짜와 시간을 반환
	 * @return yyyyMMddHHmm
	 */
	public static String getNowString() {
		// 현재 날짜와 시간을 가져옵니다.
        LocalDateTime now = LocalDateTime.now();

        // 원하는 포맷을 정의합니다.
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

        // 포맷에 맞게 문자열로 변환하여 반환합니다.
        return now.format(formatter);
	}

	/**
	 * 지정한 일자(yyyyMMdd) 에서 지정한 일수만큼 이후의 일자(yyyyMMdd)
	 * 
	 * @param udate 지정 일자
	 * @param term  초과할 일수
	 * @return
	 * @throws ParseException
	 */
	public static String getNextdayString(String udate, int term) throws ParseException {

		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");

		Calendar cal = Calendar.getInstance();
		cal.setTime(sdf.parse(udate));
		cal.add(Calendar.DAY_OF_MONTH, term);

		int yyyy = cal.get(Calendar.YEAR);
		int mm = cal.get(Calendar.MONTH) + 1;
		int dd = cal.get(Calendar.DAY_OF_MONTH);

		return "" + yyyy + (mm > 9 ? mm : "0" + mm) + (dd > 9 ? dd : "0" + dd);
	}
    
	/**
	 * YYYYMMDD to YYYY.MM.DD
	 * 
	 * @param d
	 * @return
	 */
	public static String addDatedot(String d) {
		String edate = d + "";
		String edate_str = "";
		edate_str += edate.substring(0, 4) + ".";
		edate_str += edate.substring(4, 6) + ".";
		edate_str += edate.substring(6);
		return edate_str;
	}
	
	/**
	 * YYYY.MM.DD to YYYYMMDD
	 * 
	 * @param d
	 * @return
	 */
	public static String delDatedot(String d) {
	    if (d == null) {
            return null;  // 입력이 null인 경우 null 반환
        }
        return d.replace(".", "");  // 모든 점을 빈 문자열로 대체
	};
	
    /**
     * json list 를 key:object 형태의 json map 으로 변형시킨다 (key is "seq" string)
     * 
     * @param jsonlist
     * @return
     */
    public static JSONObject listToMap(List<JSONObject> jsonlist) {
        JSONObject rtnJson = new JSONObject();
        Iterator<JSONObject> jsonIter = jsonlist.iterator();
        JSONObject jsonObj = null;
        while (jsonIter.hasNext()) {
            jsonObj = (JSONObject) jsonIter.next();
            rtnJson.put(jsonObj.get("seq").toString(), jsonObj);
        }
        return rtnJson;
    }
	
    /**
     * 영문 대소문자 랜덤 문자 반환
     * @param length 문자열 길이
     * @return
     */
    public static String generateRandomAlphabet(int length) {

        Random random = new Random();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < length; i++) {
            char c = (char) (random.nextInt(26) + 'a');
            if (random.nextBoolean()) {
                c = Character.toUpperCase(c);
            }
            sb.append(c);
        }

        return sb.toString();
    }
    
    /**
     * 지정한 자리수의 숫자를 랜덤하게 생성
     * @param length 문자열 길이
     * @return
     */
    public static String generateRandomNumber(int length) {

        Random random = new Random();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < length; i++) {
            sb.append(random.nextInt(10));
        }

        return sb.toString();
    }
}

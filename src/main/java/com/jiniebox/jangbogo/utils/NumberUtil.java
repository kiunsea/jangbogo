package com.jiniebox.jangbogo.utils;

import java.math.BigDecimal;

/**
 * @author KIUNSEA
 *
 */
public class NumberUtil {

	/**
	 * Hex -> 10진수 변환
	 * @param hex
	 * @return
	 */
	public static String hexToDecString(String hex) {
		return String.valueOf(hexToDecNumber(hex));
	}
	
	/**
	 * Hex -> 10진수 변환
	 * @param hex
	 * @return
	 */
	public static long hexToDecNumber(String hex) {
		return Long.parseLong(hex, 16);
	}
	
	/**
	 * 10진수 -> Hex 변환
	 * @param dec
	 * @return
	 */
	public static String decToHexString(String dec) {

		long intDec = Long.parseLong(dec);
		return Long.toHexString(intDec).toUpperCase();
	}
	
	/**
	 * 10진수 -> Hex 변환
	 * @param dec
	 * @return
	 */
	public static String decToHexString(long dec) {

		return Long.toHexString(dec).toUpperCase();
	}
	
	/**
	 * 수치형 문자열인지 검사
	 * @param num
	 * @return
	 */
	public static boolean isNumber(String num) {
		return num == null ? false : num.matches("[0-9.]+");
	}
	
	/**
	 * 입력값의 소수점을 기준으로 원하는 자릿수 위치에서 반올림한 문자열을 리턴한다
	 * @param d 입력값
	 * @param digit 반올림으로 출력할 소수점 자릿수
	 * @return
	 */
	public static String decimalRound(double d, int digit) {
        return String.format("%." + digit + "f", d);
	}
	
    /**
     * 실수값에 대해 내림,반올림,올림 처리
     * @param decimal 부동소수
     * @param loc 자릿수 제한 위치 (제한된 자리수의 뒷자리 값으로 올림이나 내림을 취함 -> BigDecimal.setScale의 첫번째 파라미터 newScale를 의미함)
     * @param mode 1 내림 , 2 반올림 , 3 올림
     * @return BigDecimal 처리 결과
     */
    public static BigDecimal decimalRound(float decimal, int loc, int mode) {

        BigDecimal bd = new BigDecimal(decimal);
        BigDecimal result = null;

        if (mode == 1) {
            result = bd.setScale(loc, BigDecimal.ROUND_DOWN); // 내림
        } else if (mode == 2) {
            result = bd.setScale(loc, BigDecimal.ROUND_HALF_UP); // 반올림
        } else if (mode == 3) {
            result = bd.setScale(loc, BigDecimal.ROUND_UP); // 올림
        }

        return result;
    }
    
    /**
     * 실수값에 대해 내림,반올림,올림 처리
     * @param decimal 부동소수
     * @param loc 자릿수 제한 위치 (제한된 자리수의 뒷자리 값으로 올림이나 내림을 취함 -> BigDecimal.setScale의 첫번째 파라미터 newScale를 의미함)
     * @param mode 1 내림 , 2 반올림 , 3 올림
     * @return BigDecimal 처리 결과
     */
    public static BigDecimal decimalRound(double decimal, int loc, int mode) {

        BigDecimal bd = new BigDecimal(decimal);
        BigDecimal result = null;

        if (mode == 1) {
            result = bd.setScale(loc, BigDecimal.ROUND_DOWN); // 내림
        } else if (mode == 2) {
            result = bd.setScale(loc, BigDecimal.ROUND_HALF_UP); // 반올림
        } else if (mode == 3) {
            result = bd.setScale(loc, BigDecimal.ROUND_UP); // 올림
        }

        return result;
    }
    
    /**
     * 실수를 정수로 변환</br>
     * 소수점 부분은 버린다 (반올림이 아님)
     * @param real
     * @return integer
     */
    public static int convertInteger(double real) {
        return new BigDecimal(real).intValue();
    }
}

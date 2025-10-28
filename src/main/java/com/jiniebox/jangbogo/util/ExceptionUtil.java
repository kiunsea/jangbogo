package com.jiniebox.jangbogo.util;

/**
 * @author KIUNSEA
 *
 */
public class ExceptionUtil {

    /**
     * stacktrace 를 출력
     * @param e
     * @return
     */
    public static String getStackTraceString(Exception e) {
        
        StackTraceElement[] ste = e.getStackTrace();
        StringBuffer sb = new StringBuffer(e.getMessage());
        for (int i = 0; i < ste.length; i++) {
            sb.append(System.lineSeparator() + ste[i].toString());
        }

        return sb.toString();
    }
    
    /**
     * stacktrace 와 exception 의 내용을 출력
     * @param e
     * @return
     */
    public static String getExceptionInfo(Throwable e) {
        StringBuffer sb = new StringBuffer();
        sb.append(e.getClass().getName()).append(": ").append(e.getMessage()).append("\n");
        StackTraceElement[] el = e.getStackTrace();
        for (int i = 0; i < el.length; i++) {
            if (i != 0) {
                sb.append("\n");
            }
            sb.append("        ").append("at ").append(el[i].getClassName() + "." + el[i].getMethodName());
            sb.append("(").append(el[i].getFileName()).append(":").append(el[i].getLineNumber()).append(")");
        }

        Throwable innerE = e.getCause();
        if (innerE != null) {
            sb.append("\nCaused by: ");
            sb.append(getExceptionInfo(innerE));
        }

        return sb.toString();
    }
}

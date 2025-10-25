package com.jiniebox.jangbogo.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 설정 (개선 버전)
 * 
 * - 인터셉터 등록 및 제외 경로 관리
 * - 우선순위 설정
 * - 성능 최적화를 위한 명시적 제외 패턴
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private AuthInterceptor authInterceptor;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")  // 모든 경로에 적용
                .excludePathPatterns(    // 명시적 제외 (Interceptor 내부에서도 체크하지만 성능 향상)
                    // 로그인 관련
                    "/signin",
                    "/api/login",
                    "/api/session-check",
                    
                    // 시스템
                    "/error",
                    "/actuator/**",
                    
                    // 정적 리소스
                    "/css/**",
                    "/js/**",
                    "/images/**",
                    "/static/**",
                    "/favicon.ico",
                    "/robots.txt"
                )
                .order(1);  // 우선순위 설정 (숫자가 작을수록 먼저 실행)
    }
}


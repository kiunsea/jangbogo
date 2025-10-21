package com.jiniebox.jangbogo.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 설정
 * 인터셉터 등록
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private AuthInterceptor authInterceptor;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")  // 모든 경로에 적용
                .excludePathPatterns(    // 제외할 경로들
                    "/signin",
                    "/api/login",
                    "/api/session-check",
                    "/welcome",
                    "/css/**",
                    "/js/**",
                    "/images/**",
                    "/static/**",
                    "/error"
                );
    }
}


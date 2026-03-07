package com.scutmmq.config;

import com.scutmmq.interceptor.LoginCertificationInterceptor;
import com.scutmmq.interceptor.RefreshInterceptor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Data
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final RefreshInterceptor refreshInterceptor;

    private final LoginCertificationInterceptor loginCertificationInterceptor;


    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(refreshInterceptor)
                .addPathPatterns("/**")
                .order(0);

        registry.addInterceptor(loginCertificationInterceptor)
                .excludePathPatterns("/user/login","/user/register","/image/upload")
                .order(1);
    }
}

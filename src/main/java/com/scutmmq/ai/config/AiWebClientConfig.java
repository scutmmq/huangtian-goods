package com.scutmmq.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient Bean，仅用于 AI 模块对 AI Provider 的调用。
 * ObjectMapper 直接复用 Spring Boot 自动装配的实例（已包含 JavaTimeModule 等）。
 */
@Configuration
public class AiWebClientConfig {

    @Bean(name = "aiWebClient")
    public WebClient aiWebClient() {
        return WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }
}

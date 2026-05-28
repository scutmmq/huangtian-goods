package com.scutmmq.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI Provider API 配置类。
 * 用于读取和管理 OpenAI-compatible 聊天接口配置。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai.api")
public class AiProviderConfig {

    private String key;

    private String url;

    private String model;

    private String authHeader = "Authorization";

    private String authScheme = "Bearer";

    public void setKey(String key) { this.key = stripQuotes(key); }
    public void setUrl(String url) { this.url = stripQuotes(url); }
    public void setModel(String model) { this.model = stripQuotes(model); }
    public void setAuthHeader(String authHeader) { this.authHeader = stripQuotes(authHeader); }
    public void setAuthScheme(String authScheme) { this.authScheme = stripQuotes(authScheme); }

    /**
     * 兼容 Docker --env-file 不剥引号的情况：从首尾去掉成对的单引号或双引号。
     */
    private static String stripQuotes(String v) {
        if (v == null) return null;
        String trimmed = v.trim();
        if (trimmed.length() >= 2
                && ((trimmed.charAt(0) == '"' && trimmed.charAt(trimmed.length() - 1) == '"')
                    || (trimmed.charAt(0) == '\'' && trimmed.charAt(trimmed.length() - 1) == '\''))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    public void validateReady() {
        if (!hasText(key)) {
            throw new IllegalStateException("ai.api.key is not configured; set AI_API_KEY or ai.api.key");
        }
        if (!hasText(url)) {
            throw new IllegalStateException("ai.api.url is not configured");
        }
        if (!hasText(model)) {
            throw new IllegalStateException("ai.api.model is not configured");
        }
        if (!hasText(authHeader)) {
            throw new IllegalStateException("ai.api.auth-header is not configured");
        }
    }

    public String authValue() {
        if (!hasText(authScheme)) {
            return key;
        }
        return authScheme.trim() + " " + key;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

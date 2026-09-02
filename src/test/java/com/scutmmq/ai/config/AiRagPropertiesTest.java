package com.scutmmq.ai.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * RAG 配置属性校验单元测试。
 */
class AiRagPropertiesTest {

    @Test
    @DisplayName("默认合法配置应通过校验")
    void defaultPropertiesPassValidation() {
        AiRagProperties props = new AiRagProperties();
        assertDoesNotThrow(props::validate);
    }

    @Test
    @DisplayName("非正数向量维度应抛出 IllegalStateException")
    void invalidDimensionThrowsException() {
        AiRagProperties props = new AiRagProperties();
        props.setEmbeddingDimension(0);
        assertThrows(IllegalStateException.class, props::validate);

        props.setEmbeddingDimension(-100);
        assertThrows(IllegalStateException.class, props::validate);
    }

    @Test
    @DisplayName("非正数 Top-K 应抛出 IllegalStateException")
    void invalidTopKThrowsException() {
        AiRagProperties props = new AiRagProperties();
        props.setTopK(0);
        assertThrows(IllegalStateException.class, props::validate);
    }

    @Test
    @DisplayName("超出 0.0~1.0 范围的 minScore 应抛出 IllegalStateException")
    void invalidMinScoreThrowsException() {
        AiRagProperties props = new AiRagProperties();
        props.setMinScore(-0.1);
        assertThrows(IllegalStateException.class, props::validate);

        props.setMinScore(1.1);
        assertThrows(IllegalStateException.class, props::validate);
    }

    @Test
    @DisplayName("RAG 启用且使用 DashScope 时若缺失 API Key 应 Fail-Fast 抛出 IllegalStateException")
    void enabledWithDashScopeMissingApiKeyThrowsException() {
        AiRagProperties props = new AiRagProperties();
        props.setEnabled(true);
        props.setEmbeddingProvider("dashscope");
        props.setEmbeddingApiKey("");

        assertThrows(IllegalStateException.class, props::validate);

        props.setEmbeddingApiKey("   ");
        assertThrows(IllegalStateException.class, props::validate);

        // 设置有效 Key 时通过
        props.setEmbeddingApiKey("sk-test-valid-key");
        assertDoesNotThrow(props::validate);
    }
}

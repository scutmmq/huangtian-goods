package com.scutmmq.ai.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI RAG（检索增强生成）知识库系统的配置属性类。
 * 对应配置文件中的前缀：{@code ai.rag}。
 *
 * <p><b>参数说明与最佳实践：</b></p>
 * <ul>
 *   <li>{@code enabled}: RAG 功能总开关，默认 false，关掉后系统行为零退化。</li>
 *   <li>{@code embeddingProvider}: 向量嵌入提供方，可选 "dashscope"（阿里云百炼云端服务）或 "mock"（离线测试与本地开发）。</li>
 *   <li>{@code embeddingDimension}: 向量维度大小（如 DashScope text-embedding-v3 默认 1024 维）。</li>
 *   <li>{@code topK}: 每次相似度检索召回的最相关分块数量上限，电商场景推荐 3~5 条。</li>
 *   <li>{@code minScore}: 余弦相似度截断阈值（0.0 ~ 1.0），过滤相关度过低的噪声内容，推荐 0.60 ~ 0.70。</li>
 * </ul>
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.rag")
public class AiRagProperties {

    /**
     * RAG 知识库系统能力总开关（默认 false）
     */
    private boolean enabled = false;

    /**
     * Embedding 提供商类型："dashscope" 或 "mock"（默认 "dashscope"）
     */
    private String embeddingProvider = "dashscope";

    /**
     * 向量模型 REST API 请求端点（OpenAI 兼容协议）
     */
    private String embeddingUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings";

    /**
     * 向量模型 API 鉴权密钥（可从环境变量 AI_EMBEDDING_API_KEY 注入）
     */
    private String embeddingApiKey = "";

    /**
     * 向量模型名称（默认 text-embedding-v3）
     */
    private String embeddingModel = "text-embedding-v3";

    /**
     * 向量特征维度（必须与模型输出维度严格对齐，默认 1024）
     */
    private int embeddingDimension = 1024;

    /**
     * 检索召回的最大文档分块数 Top-K（默认 3）
     */
    private int topK = 3;

    /**
     * 最低相似度阈值（0.0 ~ 1.0，低于此分数的检索结果将被过滤，默认 0.65）
     */
    private double minScore = 0.65;

    /**
     * 全量知识库定时构建 cron 表达式（默认每天凌晨 04:00 执行）
     */
    private String ingestCron = "0 0 4 * * ?";

    /**
     * Query 向量的 Redis 缓存过期时间（单位：小时，默认 24 小时）
     */
    private int cacheTtlHours = 24;

    /**
     * 针对外部 Embedding API 的并发速率限制（每秒请求上限，默认 20 QPS）
     */
    private int rateLimitPerSecond = 20;

    /**
     * 启动参数有效性检查。
     */
    @PostConstruct
    public void validate() {
        if (embeddingDimension <= 0) {
            throw new IllegalStateException("ai.rag.embedding-dimension must be positive, got: " + embeddingDimension);
        }
        if (topK <= 0) {
            throw new IllegalStateException("ai.rag.top-k must be positive, got: " + topK);
        }
        if (minScore < 0.0 || minScore > 1.0) {
            throw new IllegalStateException("ai.rag.min-score must be between 0.0 and 1.0, got: " + minScore);
        }
        if (enabled && "dashscope".equalsIgnoreCase(embeddingProvider)) {
            if (embeddingApiKey == null || embeddingApiKey.trim().isEmpty()) {
                throw new IllegalStateException("ai.rag.embedding-api-key must not be blank when ai.rag.enabled=true and provider is dashscope");
            }
        }
    }
}

package com.scutmmq.ai.rag.embedding;

import com.scutmmq.ai.config.AiRagProperties;
import com.scutmmq.ai.observability.RagMetrics;
import com.scutmmq.ai.rag.util.VectorMathUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

/**
 * 带有 Redis 缓存的 Embedding 装饰器服务（@Primary 注入入口）。
 *
 * <p><b>为什么需要对 Query Embedding 进行缓存？</b></p>
 * <p>
 * 在高并发电商问答场景中，用户的常见提问（如“怎么退货”、“支持7天无理由退货吗”、“运费怎么算”）存在大量高频重复。
 * 将 Query 文本哈希后存入 Redis（TTL 24小时）：
 * <ul>
 *   <li>使高频问答的 Embedding 阶段耗时从 ~100ms 降低至 1ms 以内；</li>
 *   <li>大幅削减调用云端大模型 Embedding API 的网络请求量与 Token 账单费用；</li>
 *   <li>具备 Redis 故障容灾降级机制：当 Redis 异常时平滑回源计算，绝不中断业务。</li>
 * </ul>
 * </p>
 */
@Slf4j
@Primary
@Service
public class CachedEmbeddingService implements EmbeddingService {

    private static final String CACHE_PREFIX = "ai:embedding:cache:";

    private final DashScopeEmbeddingService delegate;
    private final MockEmbeddingService mockFallback;
    private final StringRedisTemplate redis;
    private final AiRagProperties props;
    private final RagMetrics metrics;

    public CachedEmbeddingService(DashScopeEmbeddingService delegate,
                                  MockEmbeddingService mockFallback,
                                  StringRedisTemplate redis,
                                  AiRagProperties props,
                                  RagMetrics metrics) {
        this.delegate = delegate;
        this.mockFallback = mockFallback;
        this.redis = redis;
        this.props = props;
        this.metrics = metrics;
    }

    @Override
    public float[] embedQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new float[dimension()];
        }

        String cacheKey = buildCacheKey(query);

        // 1. 尝试读 Redis 缓存
        try {
            if (redis != null) {
                String cachedJson = redis.opsForValue().get(cacheKey);
                if (cachedJson != null && !cachedJson.isEmpty()) {
                    metrics.recordCacheHit();
                    return VectorMathUtils.fromJson(cachedJson);
                }
            }
        } catch (Exception e) {
            log.warn("[AI][RAG] Redis cache get failed, proceeding with direct embedding: {}", e.getMessage());
        }

        metrics.recordCacheMiss();

        // 2. 回源生成向量（优先 DashScope，若未配置或指定 mock 则走 Mock）
        float[] vector;
        if ("mock".equalsIgnoreCase(props.getEmbeddingProvider())) {
            vector = mockFallback.embedQuery(query);
        } else {
            vector = delegate.embedQuery(query);
        }

        // 3. 异步/安全写入 Redis 缓存
        try {
            if (redis != null && vector != null && vector.length > 0) {
                String json = VectorMathUtils.toJson(vector);
                redis.opsForValue().set(cacheKey, json, Duration.ofHours(props.getCacheTtlHours()));
            }
        } catch (Exception e) {
            log.warn("[AI][RAG] Redis cache set failed: {}", e.getMessage());
        }

        return vector;
    }

    @Override
    public List<float[]> embedDocuments(List<String> documents) {
        // 知识库文档批量构建通常规模大且内容随业务更新，直接委托底层 Embedding 服务处理
        if ("mock".equalsIgnoreCase(props.getEmbeddingProvider())) {
            return mockFallback.embedDocuments(documents);
        }
        return delegate.embedDocuments(documents);
    }

    @Override
    public int dimension() {
        return props.getEmbeddingDimension();
    }

    private String buildCacheKey(String query) {
        String provider = props.getEmbeddingProvider() != null ? props.getEmbeddingProvider().toLowerCase() : "dashscope";
        String model = props.getEmbeddingModel() != null ? props.getEmbeddingModel() : "text-embedding-v3";
        int dim = props.getEmbeddingDimension();
        return CACHE_PREFIX + provider + ":" + model + ":" + dim + ":" + sha256(query.trim());
    }

    private static String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 32);
        } catch (Exception e) {
            return Integer.toHexString(text.hashCode());
        }
    }
}

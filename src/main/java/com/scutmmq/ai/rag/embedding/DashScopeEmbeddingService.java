package com.scutmmq.ai.rag.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scutmmq.ai.config.AiRagProperties;
import com.scutmmq.ai.observability.RagMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 阿里云百炼 DashScope（text-embedding-v3）/ OpenAI 兼容 Embedding REST 客户端。
 *
 * <p><b>企业级高可用与 Fail-Fast 设计：</b></p>
 * <ul>
 *   <li><b>速率限制（Rate Limiting）</b>：内置令牌桶限流器，限制每秒向云端发送的请求 QPS，防止 429 Too Many Requests；</li>
 *   <li><b>Fail-Fast 熔断机制</b>：当生产环境云端 API 出现网络超时或鉴权失败时，严禁静默退化为 Mock 随机向量，
 *       而是明确抛出 {@link EmbeddingException} 并记录指标，杜绝跨向量空间导致的随机近邻匹配与系统性全员幻觉；</li>
 *   <li><b>环境隔离</b>：仅在明确配置 {@code ai.rag.embedding-provider=mock} 时允许使用 Mock 嵌入。</li>
 * </ul>
 */
@Slf4j
@Component
public class DashScopeEmbeddingService implements EmbeddingService {

    private final AiRagProperties props;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final RagMetrics metrics;
    private final MockEmbeddingService mockFallback;

    // 进程内滑动窗口/令牌桶速率限制器（依据 props.getRateLimitPerSecond）
    private final AtomicLong lastRateLimitTimestamp = new AtomicLong(System.currentTimeMillis());
    private final AtomicInteger currentSecondCount = new AtomicInteger(0);

    public DashScopeEmbeddingService(AiRagProperties props,
                                     WebClient webClient,
                                     ObjectMapper objectMapper,
                                     RagMetrics metrics,
                                     MockEmbeddingService mockFallback) {
        this.props = props;
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.mockFallback = mockFallback;
    }

    @Override
    public float[] embedQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new float[dimension()];
        }
        List<float[]> embeddings = embedDocuments(Collections.singletonList(query));
        return embeddings.isEmpty() ? new float[dimension()] : embeddings.get(0);
    }

    @Override
    public List<float[]> embedDocuments(List<String> documents) {
        if (documents == null || documents.isEmpty()) {
            return Collections.emptyList();
        }

        // 显式指定 mock 模式时（如测试环境）
        if ("mock".equalsIgnoreCase(props.getEmbeddingProvider())) {
            return mockFallback.embedDocuments(documents);
        }

        // 生产 DashScope 模式下缺少 API Key 时 Fail-Fast
        if (props.getEmbeddingApiKey() == null || props.getEmbeddingApiKey().trim().isEmpty()) {
            log.error("[AI][RAG] Missing required embedding API Key for provider: {}", props.getEmbeddingProvider());
            throw new EmbeddingException("Missing required ai.rag.embedding-api-key for DashScope embedding provider");
        }

        // 速率限制（Rate Limiting）检查
        acquireRateLimitPermission();

        long start = System.currentTimeMillis();
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", props.getEmbeddingModel());
            requestBody.put("dimension", props.getEmbeddingDimension());

            ArrayNode inputArray = requestBody.putArray("input");
            for (String doc : documents) {
                inputArray.add(doc);
            }

            String responseJson = webClient.post()
                    .uri(props.getEmbeddingUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + props.getEmbeddingApiKey().trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(objectMapper.writeValueAsString(requestBody))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            List<float[]> results = parseEmbeddingResponse(responseJson, documents.size());
            long elapsed = System.currentTimeMillis() - start;
            metrics.recordEmbeddingSuccess(elapsed);
            log.info("[AI][RAG] Successfully embedded {} documents in {}ms", documents.size(), elapsed);
            return results;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            metrics.recordEmbeddingFailure(elapsed);
            log.error("[AI][RAG] Remote DashScope embedding API call failed after {}ms: {}", elapsed, e.getMessage());
            // Fail-Fast: 严禁在生产环境静默降级为 Mock 向量
            throw new EmbeddingException("DashScope embedding API request failed: " + e.getMessage(), e);
        }
    }

    @Override
    public int dimension() {
        return props.getEmbeddingDimension();
    }

    /**
     * 简单的滑动秒窗口限流控制。
     */
    private void acquireRateLimitPermission() {
        int maxPermitsPerSec = Math.max(1, props.getRateLimitPerSecond());
        long now = System.currentTimeMillis();
        long lastWindow = lastRateLimitTimestamp.get();

        if (now - lastWindow >= 1000L) {
            if (lastRateLimitTimestamp.compareAndSet(lastWindow, now)) {
                currentSecondCount.set(0);
            }
        }

        if (currentSecondCount.incrementAndGet() > maxPermitsPerSec) {
            try {
                // 简单的平滑等待，防止超频请求 429
                Thread.sleep(Math.max(10, 1000 / maxPermitsPerSec));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private List<float[]> parseEmbeddingResponse(String responseJson, int expectedCount) throws Exception {
        JsonNode root = objectMapper.readTree(responseJson);
        JsonNode dataNode = root.path("data");
        if (!dataNode.isArray()) {
            throw new IllegalStateException("Embedding response missing 'data' array: " + responseJson);
        }

        List<float[]> list = new ArrayList<>(dataNode.size());
        for (JsonNode item : dataNode) {
            JsonNode embeddingNode = item.path("embedding");
            if (embeddingNode.isArray()) {
                float[] vec = new float[embeddingNode.size()];
                for (int i = 0; i < embeddingNode.size(); i++) {
                    vec[i] = (float) embeddingNode.get(i).asDouble();
                }
                list.add(vec);
            }
        }
        return list;
    }
}

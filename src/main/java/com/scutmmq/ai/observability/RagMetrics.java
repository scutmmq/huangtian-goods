package com.scutmmq.ai.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * AI RAG（检索增强生成）知识库模块 Micrometer 监控指标集中管理类。
 *
 * <p><b>为什么需要监控指标？</b></p>
 * <p>
 * 在企业级生产环境中，RAG 系统直接影响问答的准确性与响应延迟。通过监控指标我们可以：
 * <ol>
 *   <li><b>监测检索延迟</b>：跟踪 Embedding 生成与向量库近邻检索耗时（P50 / P95 / P99），防止慢检索拖垮用户体验。</li>
 *   <li><b>监测召回命中率与质量</b>：统计每次 Query 召回的文档数及相关度分布，及时发现知识盲区或切片质量问题。</li>
 *   <li><b>监测外部 API 成本与稳定性</b>：统计云端 Embedding Token 消耗量及调用成功/失败次数，预防限流（Rate Limit）与突发流量费用。</li>
 *   <li><b>安全与合规审计</b>：统计跨租户非法查询拦截、间接 Prompt 注入阻断与空召回防幻觉事件。</li>
 * </ol>
 * </p>
 */
@Slf4j
@Component
public class RagMetrics {

    private final MeterRegistry meter;

    // 向量检索计数器（成功 / 失败）
    private final Counter searchSuccessCounter;
    private final Counter searchFailureCounter;

    // 向量嵌入计数器（成功 / 失败）
    private final Counter embeddingSuccessCounter;
    private final Counter embeddingFailureCounter;

    // 缓存命中 / 未命中计数器
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;

    // 耗时统计（Timer）
    private final Timer searchTimer;
    private final Timer embeddingTimer;

    // 每次检索召回的 Chunk 条数分布
    private final DistributionSummary recallChunksSummary;

    // 召回为空与低于阈值计数
    private final Counter recallEmptyCounter;
    private final Counter recallBelowThresholdCounter;

    // 跨租户与注入拦截计数
    private final Counter crossTenantBlockedCounter;
    private final Counter promptInjectionBlockedCounter;
    private final Counter mainPathInjectedCounter;

    public RagMetrics(MeterRegistry meter) {
        this.meter = meter;

        this.searchSuccessCounter = Counter.builder("ai_rag_search_total")
                .tag("result", "success")
                .description("RAG 向量检索成功次数")
                .register(meter);

        this.searchFailureCounter = Counter.builder("ai_rag_search_total")
                .tag("result", "failure")
                .description("RAG 向量检索失败次数")
                .register(meter);

        this.embeddingSuccessCounter = Counter.builder("ai_rag_embedding_total")
                .tag("result", "success")
                .description("Embedding 向量生成成功次数")
                .register(meter);

        this.embeddingFailureCounter = Counter.builder("ai_rag_embedding_total")
                .tag("result", "failure")
                .description("Embedding 向量生成失败次数")
                .register(meter);

        this.cacheHitCounter = Counter.builder("ai_rag_cache_total")
                .tag("result", "hit")
                .description("Embedding 向量 Redis 缓存命中次数")
                .register(meter);

        this.cacheMissCounter = Counter.builder("ai_rag_cache_total")
                .tag("result", "miss")
                .description("Embedding 向量 Redis 缓存未命中次数")
                .register(meter);

        this.recallEmptyCounter = Counter.builder("ai_rag_recall_empty_total")
                .description("RAG 检索召回为空总次数")
                .register(meter);

        this.recallBelowThresholdCounter = Counter.builder("ai_rag_recall_below_threshold_total")
                .description("RAG 检索相似度低于阈值被过滤的切片总数")
                .register(meter);

        this.crossTenantBlockedCounter = Counter.builder("ai_rag_cross_tenant_queries_total")
                .tag("action", "blocked")
                .description("跨租户非法查询拦截总次数")
                .register(meter);

        this.promptInjectionBlockedCounter = Counter.builder("ai_rag_prompt_injection_blocked_total")
                .tag("source", "rag")
                .description("RAG 知识切片命中 Prompt 注入策略被拦截总次数")
                .register(meter);

        this.mainPathInjectedCounter = Counter.builder("ai_rag_main_path_injected_total")
                .description("主对话链路成功注入 RAG 上下文的总次数")
                .register(meter);

        this.searchTimer = Timer.builder("ai_rag_search_duration_seconds")
                .description("RAG 向量检索总耗时")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meter);

        this.embeddingTimer = Timer.builder("ai_rag_embedding_duration_seconds")
                .description("Embedding 向量模型调用耗时")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meter);

        this.recallChunksSummary = DistributionSummary.builder("ai_rag_recall_chunks")
                .description("每次 RAG 检索召回的 Chunk 数量分布")
                .register(meter);
    }

    public void recordSearchSuccess(long durationMs, int recalledCount) {
        searchSuccessCounter.increment();
        searchTimer.record(durationMs, TimeUnit.MILLISECONDS);
        recallChunksSummary.record(recalledCount);
    }

    public void recordSearchFailure(long durationMs) {
        searchFailureCounter.increment();
        searchTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordEmbeddingSuccess(long durationMs) {
        embeddingSuccessCounter.increment();
        embeddingTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordEmbeddingFailure(long durationMs) {
        embeddingFailureCounter.increment();
        embeddingTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordCacheHit() {
        cacheHitCounter.increment();
    }

    public void recordCacheMiss() {
        cacheMissCounter.increment();
    }

    public void recordRecallEmpty() {
        recallEmptyCounter.increment();
    }

    public void recordBelowThreshold(int count) {
        recallBelowThresholdCounter.increment(count);
    }

    public void recordCrossTenantBlocked() {
        crossTenantBlockedCounter.increment();
    }

    public void recordPromptInjectionBlocked() {
        promptInjectionBlockedCounter.increment();
    }

    public void recordMainPathInjected() {
        mainPathInjectedCounter.increment();
    }
}

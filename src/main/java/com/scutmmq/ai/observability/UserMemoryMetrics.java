package com.scutmmq.ai.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * B3 step10 review-fix: 用户记忆模块(ai_memory_*)Micrometer 指标集中管理。
 *
 * <p>所有 Counter / Gauge / Timer / DistributionSummary 都在构造期预注册,
 * 业务路径只调 1-line helper。 设计目标:
 * <ul>
 *   <li>避免 hot path 上的 {@code meter.counter(name, tag, val)} 反射 lookup</li>
 *   <li>按 sql 名 / field 名 / lag 桶名 dim 的 Counter 走 ConcurrentHashMap 缓存避免重复创建</li>
 *   <li>Gauge 数据源全部在自己内部(AtomicLong for fail_users / cache_hit_ratio),
 *       业务方只调 setter</li>
 * </ul>
 *
 * <p>对应 19 个 step10 新增指标(spec v0.3 §7.2):
 * <ol>
 *   <li>{@code ai_memory_recompute_total{result}}</li>
 *   <li>{@code ai_memory_recompute_duration_seconds}</li>
 *   <li>{@code ai_memory_fail_users}(gauge)</li>
 *   <li>{@code ai_memory_json_overflow_total{json}}</li>
 *   <li>{@code ai_memory_cache_hit_ratio}(gauge)</li>
 *   <li>{@code ai_memory_cache_failure_total}</li>
 *   <li>{@code ai_memory_cache_miss_burst_total}</li>
 *   <li>{@code ai_memory_read_miss_total}</li>
 *   <li>{@code ai_memory_read_stale_total{lag_seconds_bucket}}</li>
 *   <li>{@code ai_memory_injection_token_total}</li>
 *   <li>{@code ai_memory_overflow_drop_total{field}}</li>
 *   <li>{@code ai_memory_prompt_injection_drop_total}</li>
 *   <li>{@code ai_memory_local_rate_limit_total{result}}</li>
 *   <li>{@code ai_memory_db_query_seconds{sql}}</li>
 *   <li>{@code ai_memory_db_long_tx_total}</li>
 * </ol>
 *
 * <p>cron 5 个(ai_memory_cron_*)已在 {@code MemoryCronScheduler} 中独立埋点,见 Task 7。
 */
@Slf4j
@Component
public class UserMemoryMetrics {

    /** DB 查询耗时"长事务"阈值(>1s 视为长事务,记 counter)。 */
    private static final long DB_LONG_TX_THRESHOLD_MS = 1_000L;

    private final MeterRegistry meter;

    // ============================ 预注册的 Counter(tag 固定) ============================

    private final Counter recomputeSuccess;
    private final Counter recomputeFailure;

    private final Counter rateLimitAccept;
    private final Counter rateLimitDrop;

    private final Counter promptInjectionDrop;
    private final Counter readMiss;
    private final Counter dbLongTx;
    private final Counter cacheFailure;
    private final Counter cacheMissBurst;

    // ============================ 预注册的 DistributionSummary / Timer(无 tag) ============================

    private final DistributionSummary recomputeDuration;
    private final DistributionSummary injectionToken;

    // ============================ 按 tag 维度变化的 Counter(map 缓存) ============================

    private final ConcurrentHashMap<String, Counter> overflowDropByField = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> jsonOverflowByField = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> readStaleByBucket = new ConcurrentHashMap<>();
    /** DB 查询耗时按 sql 名缓存 Timer — 避免重复 Timer.builder() 反射 */
    private final ConcurrentHashMap<String, Timer> dbQueryTimers = new ConcurrentHashMap<>();

    // ============================ Gauge 数据源 ============================

    /** ai_memory_fail_users(recompute_status=DISABLED 用户数) */
    private final AtomicLong disabledUserCount = new AtomicLong();

    /** ai_memory_cache_hit_ratio 数据源(cache.get 时 hit / miss +1) */
    private final AtomicLong cacheHitCount = new AtomicLong();
    private final AtomicLong cacheMissCount = new AtomicLong();

    public UserMemoryMetrics(MeterRegistry meter) {
        this.meter = meter;

        // ---- pre-registered Counters ----

        this.recomputeSuccess = Counter.builder("ai_memory_recompute_total")
                .description("重算成功计数").tag("result", "success").register(meter);
        this.recomputeFailure = Counter.builder("ai_memory_recompute_total")
                .description("重算失败计数").tag("result", "failure").register(meter);

        this.rateLimitAccept = Counter.builder("ai_memory_local_rate_limit_total")
                .description("Redis Down 兜底 RateLimiter:accept 计数")
                .tag("result", "accept").register(meter);
        this.rateLimitDrop = Counter.builder("ai_memory_local_rate_limit_total")
                .description("Redis Down 兜底 RateLimiter:drop 计数")
                .tag("result", "drop").register(meter);

        this.promptInjectionDrop = Counter.builder("ai_memory_prompt_injection_drop_total")
                .description("PromptSanitizer DENY_LIST 命中次数").register(meter);
        this.readMiss = Counter.builder("ai_memory_read_miss_total")
                .description("renderMemorySection 空返(new user / 空 JSON)").register(meter);
        this.dbLongTx = Counter.builder("ai_memory_db_long_tx_total")
                .description("DB 查询 >1s 视为长事务计数").register(meter);

        this.cacheFailure = Counter.builder("ai_memory_cache_failure_total")
                .description("缓存 get 失败次数(Redis 连接异常)").register(meter);
        this.cacheMissBurst = Counter.builder("ai_memory_cache_miss_burst_total")
                .description("缓存 miss 突增计数(>5x baseline 告警)").register(meter);

        // ---- pre-registered Summary ----

        this.recomputeDuration = DistributionSummary.builder("ai_memory_recompute_duration_seconds")
                .description("UserMemoryService.recomputeFor 耗时分布(纳秒转秒)")
                .publishPercentiles(0.5, 0.95, 0.99).register(meter);

        this.injectionToken = DistributionSummary.builder("ai_memory_injection_token_total")
                .description("注入到 prompt 的画像 token 估算(基于 chars/2)")
                .publishPercentiles(0.5, 0.95).register(meter);

        // ---- Gauges(绑到自己持有的 AtomicLong 上)----

        Gauge.builder("ai_memory_fail_users", disabledUserCount, AtomicLong::doubleValue)
                .description("recompute_status=DISABLED 用户数(target < 50)")
                .register(meter);

        Gauge.builder("ai_memory_cache_hit_ratio", this, UserMemoryMetrics::computeCacheHitRatio)
                .description("缓存命中率 gauge(target > 0.80)")
                .register(meter);
    }

    // ============================ UserMemoryBuilder helpers ============================

    /** B3 step10: 记录一条 DB 查询耗时到 ai_memory_db_query_seconds{sql};>1s 视为长事务,记 ai_memory_db_long_tx_total。 */
    public void recordDbQuery(String sqlName, Runnable query) {
        Timer t = dbQueryTimers.computeIfAbsent(sqlName, n -> Timer
                .builder("ai_memory_db_query_seconds")
                .description("记忆 SQL 查询耗时分布")
                .tag("sql", n)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meter));
        long start = System.currentTimeMillis();
        try {
            query.run();
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            t.record(elapsed, TimeUnit.MILLISECONDS);
            if (elapsed > DB_LONG_TX_THRESHOLD_MS) {
                dbLongTx.increment();
            }
        }
    }

    /** B3 step10: 记录注入 prompt 的画像 token 数。 */
    public void recordInjectionTokens(long tokens) {
        injectionToken.record(tokens);
    }

    /** B3 step10: 截断后某 section 被丢 +1。field ∈ {top_merchants, preferred_sizes, active_hours, ...} */
    public void recordOverflowDrop(String field) {
        overflowDropByField.computeIfAbsent(field, f -> Counter
                .builder("ai_memory_overflow_drop_total")
                .description("renderForPrompt 截断后被丢的 section 字段")
                .tag("field", f).register(meter)).increment();
    }

    // ============================ UserMemoryService helpers ============================

    /** B3 step10: 重算结束记 ai_memory_recompute_total{result} + ai_memory_recompute_duration_seconds summary。 */
    public void recordRecomputeResult(long startNs, boolean success) {
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
        (success ? recomputeSuccess : recomputeFailure).increment();
        recomputeDuration.record(elapsedMs);
    }

    /** B3 step10: JSON OVERFLOW 降级触发镜像 audit 计数。field ∈ {identity, preference} */
    public void recordJsonOverflow(String field) {
        jsonOverflowByField.computeIfAbsent(field, f -> Counter
                .builder("ai_memory_json_overflow_total")
                .description("JSON OVERFLOW 降级触发次数")
                .tag("json", f).register(meter)).increment();
    }

    /** B3 step10: cache hit 但 db.seq 已过期回源。lagBucket 走 {@link #lagBucket(long)} 5 分桶。 */
    public void recordReadStale(String lagBucket) {
        readStaleByBucket.computeIfAbsent(lagBucket, b -> Counter
                .builder("ai_memory_read_stale_total")
                .description("cache hit 但 db.seq 已过期回源")
                .tag("lag_seconds_bucket", b).register(meter)).increment();
    }

    /** B3 step10: renderMemorySection 空返(new user / empty snapshot)。 */
    public void recordReadMiss() {
        readMiss.increment();
    }

    /** B3 step10: Redis Down 兜底 RateLimiter accept / drop 计数。 */
    public void recordRateLimit(boolean accept) {
        (accept ? rateLimitAccept : rateLimitDrop).increment();
    }

    /** B3 step10: markDisabled 成功时 +1(进程内)。 */
    public void incrementDisabledUserCount() {
        disabledUserCount.incrementAndGet();
    }

    /** B3 step10: @Scheduled 5min 从 DB 刷新 gauge 初值/校正漂移。 */
    public void setDisabledUserCount(long count) {
        disabledUserCount.set(count);
    }

    public long getDisabledUserCount() {
        return disabledUserCount.get();
    }

    // ============================ UserMemoryCache helpers ============================

    public void recordCacheHit() {
        cacheHitCount.incrementAndGet();
    }

    public void recordCacheMiss() {
        cacheMissCount.incrementAndGet();
        cacheMissBurst.increment();
    }

    public void recordCacheFailure() {
        cacheFailure.increment();
        cacheMissCount.incrementAndGet();
    }

    /** 命中率 = hits / (hits + misses);分母为 0 时返 0.0 避免 NaN。 */
    private double computeCacheHitRatio() {
        long hits = cacheHitCount.get();
        long misses = cacheMissCount.get();
        long total = hits + misses;
        return total == 0L ? 0.0 : (double) hits / (double) total;
    }

    // ============================ PromptSanitizer helper ============================

    public void recordPromptInjectionDrop() {
        promptInjectionDrop.increment();
    }

    // ============================ 桶工具(被 UserMemoryService.render 调用) ============================

    /**
     * B3 step10: lag 分桶。{@code dbSeq - cachedSeq} 差值映射到 5 桶。
     * 实际是 {@code compute_seq} 差(每次重算 +1),不是真实秒数 — 对外告警阈值仍有效。
     */
    public static String lagBucket(long lag) {
        if (lag <= 1L) return "0-1";
        if (lag <= 5L) return "2-5";
        if (lag <= 30L) return "6-30";
        if (lag <= 300L) return "31-300";
        return "300+";
    }
}

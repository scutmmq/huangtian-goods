package com.scutmmq.ai.service;

import com.scutmmq.ai.cache.UserMemoryCache;
import com.scutmmq.ai.cache.UserMemoryCache.CacheSnapshot;
import com.scutmmq.ai.config.AiMemoryProperties;
import com.scutmmq.ai.dto.UserMemoryOverviewVO;
import com.scutmmq.ai.entity.TriggerReason;
import com.scutmmq.ai.entity.UserMemoryEntity;
import com.scutmmq.ai.mapper.UserMemoryMapper;
import com.scutmmq.ai.observability.UserMemoryMetrics;
import com.scutmmq.ai.security.PromptSanitizer;
import com.scutmmq.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * B3 step5: 用户长期记忆的协调层。
 *
 * <p>{@link UserMemoryMetrics} 集中所有 ai_memory_* 埋点;{@link UserMemoryBuilder}
 * 负责 SQL 聚合 / sanitize / 截断;{@link UserMemoryCache} 负责 HMAC + Lua TOCTOU。
 */
@Slf4j
@Service
public class UserMemoryService {

    /** 重算乐观锁冲突最多重试 1 次(2 次机会)。 */
    private static final int MAX_OPTIMISTIC_RETRY = 2;

    /** Redis Down 时使用本地令牌桶兜底。 */
    private final ConcurrentHashMap<Long, LocalTokenBucket> localLimiters = new ConcurrentHashMap<>();

    private final StringRedisTemplate redis;
    private final UserMemoryMapper mapper;
    private final UserMemoryBuilder builder;
    private final UserMemoryCache cache;
    @SuppressWarnings("unused") private final PromptSanitizer sanitizer;
    private final AuditService auditService;
    private final AiMemoryProperties props;
    private final Executor asyncExecutor;
    private final UserMemoryMetrics metrics;

    public UserMemoryService(StringRedisTemplate redis,
                             UserMemoryMapper mapper,
                             UserMemoryBuilder builder,
                             UserMemoryCache cache,
                             PromptSanitizer sanitizer,
                             AuditService auditService,
                             AiMemoryProperties props,
                             @Qualifier("memoryAsyncExecutor") Executor asyncExecutor,
                             UserMemoryMetrics metrics) {
        this.redis = redis;
        this.mapper = mapper;
        this.builder = builder;
        this.cache = cache;
        this.sanitizer = sanitizer;
        this.auditService = auditService;
        this.props = props;
        this.asyncExecutor = asyncExecutor;
        this.metrics = metrics;
        // 启动时刷新一次 DISABLED 用户计数(给 ai_memory_fail_users gauge 喂初值);不阻塞业务路径,异常吞掉走 0
        try {
            Long initial = mapper.countDisabledUsers();
            if (initial != null) metrics.setDisabledUserCount(initial);
        } catch (Exception e) {
            log.warn("[AI][MEMORY] initial disabledUserCount refresh failed reason={}", e.getMessage());
        }
    }

    // ============================ 防抖 ============================

    /** Redis SETNX 跨实例合并;Redis 不可用降级到本地令牌桶。true=已提交,false=窗口内被合并。 */
    public boolean scheduleRecompute(Long userId, TriggerReason reason) {
        String key = RedisConstants.MEMORY_COALESCE_KEY_PREFIX + userId;
        try {
            Boolean acquired = redis.opsForValue().setIfAbsent(
                    key,
                    Instant.now().toString(),
                    Duration.ofSeconds(props.getCoalesceTtlSeconds()));
            if (Boolean.TRUE.equals(acquired)) {
                asyncExecutor.execute(() -> recomputeFor(userId, reason));
                return true;
            }
            return false;
        } catch (Exception e) {
            log.warn("[AI][MEMORY] Redis SETNX failed userId={} reason={}, falling back to local limiter",
                    userId, e.getMessage());
            return scheduleWithLocalRateLimit(userId, reason);
        }
    }

    /** 本地令牌桶兜底:每个用户独立桶,容量={@code localRateLimitBurst},每秒补 {@code localRateLimitPerUser}。accept / drop 写审计 + ai_memory_local_rate_limit_total{result}。 */
    private boolean scheduleWithLocalRateLimit(Long userId, TriggerReason reason) {
        LocalTokenBucket limiter = localLimiters.computeIfAbsent(userId, uid ->
                new LocalTokenBucket(
                        Math.max(1, props.getLocalRateLimitBurst()),
                        Math.max(1, props.getLocalRateLimitPerUser())));
        if (limiter.tryAcquire()) {
            metrics.recordRateLimit(true);
            auditService.logDegraded(userId, "DEGRADED_RATE_LIMITED");
            asyncExecutor.execute(() -> recomputeFor(userId, reason));
            return true;
        }
        metrics.recordRateLimit(false);
        auditService.logDegraded(userId, "DEGRADED_RATE_LIMITED_DROP");
        return false;
    }

    // ============================ 重算 ============================

    /** 重算某用户记忆:4 步写(DB@Version) + cache invalidate + audit;失败累加 fail_count,达到 {@code recomputeMaxFailCount} 熔断。成功返 snapshot,失败返 null。 */
    public UserMemorySnapshot recomputeFor(Long userId, TriggerReason reason) {
        long startNs = System.nanoTime();
        UserMemoryEntity entity = mapper.selectById(userId);
        int baseVersion = entity == null ? 0 : entity.getVersion();
        try {
            UserMemorySnapshot snap = doRecompute(userId, reason, entity, baseVersion);
            metrics.recordRecomputeResult(startNs, snap != null);
            return snap;
        } catch (Exception e) {
            metrics.recordRecomputeResult(startNs, false);
            return handleRecomputeFailure(userId, e);
        }
    }

    private UserMemorySnapshot doRecompute(Long userId, TriggerReason reason,
                                           UserMemoryEntity entity, int baseVersion) {
        Throwable lastEx = null;
        for (int attempt = 1; attempt <= MAX_OPTIMISTIC_RETRY; attempt++) {
            try {
                return tryRecomputeOnce(userId, reason, entity, baseVersion + (attempt - 1));
            } catch (DataIntegrityViolationException dive) {
                return handleJsonOverflow(userId, entity, baseVersion, dive);
            } catch (OptimisticLockRetryException retry) {
                lastEx = retry.getCause();
                log.warn("[AI][MEMORY] optimistic lock conflict userId={} attempt={}/{}, retrying",
                        userId, attempt, MAX_OPTIMISTIC_RETRY);
            }
        }
        // 用尽重试 → 记 audit + fail_count++ + 阈值熔断(高竞争场景兜底)
        log.error("[AI][MEMORY] recompute exhausted retry userId={} lastReason={}",
                userId, lastEx == null ? "null" : lastEx.getMessage());
        auditService.logRecomputeFail(userId,
                lastEx == null ? new RuntimeException("optimistic retry exhausted")
                        : new RuntimeException(lastEx.getMessage(), lastEx));
        try {
            mapper.incrementFailCount(userId);
            UserMemoryEntity latest = mapper.selectById(userId);
            if (latest != null && latest.getFailCount() != null
                    && latest.getFailCount() >= props.getRecomputeMaxFailCount()) {
                int marked = mapper.markDisabled(userId);
                if (marked > 0) {
                    metrics.incrementDisabledUserCount();
                }
                log.warn("[AI][MEMORY] recompute DISABLED userId={} after fail_count={}", userId, latest.getFailCount());
            }
        } catch (Exception nested) {
            log.warn("[AI][MEMORY] fail count update failed userId={} reason={}",
                    userId, nested.getMessage());
        }
        return null;
    }

    /** 单次重算尝试。乐观锁冲突(rows=0)抛 {@link OptimisticLockRetryException} 让外层重试;JSON OVERFLOW 抛 {@link DataIntegrityViolationException} 走降级。 */
    private UserMemorySnapshot tryRecomputeOnce(Long userId, TriggerReason reason,
                                                UserMemoryEntity entity, int expectedVersion) {
        UserMemorySnapshot identitySnap = builder.computeIdentity(userId);
        String identityJson = identitySnap == null ? "{}" : identitySnap.identityJson();

        int rowsIdentity = mapper.updateIdentity(userId, identityJson, expectedVersion);
        if (rowsIdentity == 0) {
            throw new OptimisticLockRetryException("updateIdentity rows=0");
        }
        int versionAfterIdentity = expectedVersion + 1;

        UserMemorySnapshot prefSnap = builder.computePreference(userId);
        String prefJson = prefSnap == null ? "{}" : prefSnap.preferenceJson();
        int rowsPreference = mapper.updatePreference(userId, prefJson, versionAfterIdentity);
        if (rowsPreference == 0) {
            throw new OptimisticLockRetryException("updatePreference rows=0");
        }
        int versionAfterPreference = versionAfterIdentity + 1;

        mapper.bumpComputeSeq(userId, versionAfterPreference);
        cache.invalidate(userId);
        auditService.logCompute(userId, reason.name());

        return new UserMemorySnapshot(identityJson, prefJson);
    }

    /** JSON 超长降级:把两个 JSON 都写成 {} 兜底,失效 cache,bump seq,记审计 + ai_memory_json_overflow_total。 */
    private UserMemorySnapshot handleJsonOverflow(Long userId, UserMemoryEntity entity,
                                                  int baseVersion,
                                                  DataIntegrityViolationException dive) {
        String field = dive.getMessage() != null && dive.getMessage().contains("identity") ? "identity" : "preference";
        auditService.logJsonOverflow(userId, field);
        metrics.recordJsonOverflow(field);
        log.warn("[AI][MEMORY] JSON OVERFLOW userId={} field={} reason={}",
                userId, field, dive.getMessage());
        int currentVersion = entity == null ? 0 : entity.getVersion();
        try {
            mapper.updateIdentity(userId, "{}", currentVersion);
            mapper.updatePreference(userId, "{}", currentVersion + 1);
            // 兜底成功后 invalidate cache + bump seq,避免 60s 窗口内返回旧(超 8KB)JSON
            cache.invalidate(userId);
            mapper.bumpComputeSeq(userId, currentVersion + 2);
        } catch (Exception ignore) {
            log.warn("[AI][MEMORY] JSON OVERFLOW fallback write failed userId={} reason={}",
                    userId, ignore.getMessage());
        }
        return new UserMemorySnapshot("{}", "{}");
    }

    /** 重算失败兜底:fail_count++,达到 {@code recomputeMaxFailCount} 熔断。重算失败静默 log,不抛以避免阻塞主链路。 */
    private UserMemorySnapshot handleRecomputeFailure(Long userId, Exception e) {
        log.error("[AI][MEMORY] recompute failed userId={} reason={}", userId, e.getMessage());
        auditService.logRecomputeFail(userId, e);
        try {
            mapper.incrementFailCount(userId);
            UserMemoryEntity latest = mapper.selectById(userId);
            if (latest != null && latest.getFailCount() != null
                    && latest.getFailCount() >= props.getRecomputeMaxFailCount()) {
                int marked = mapper.markDisabled(userId);
                if (marked > 0) {
                    metrics.incrementDisabledUserCount();
                }
                log.warn("[AI][MEMORY] recompute DISABLED userId={} after fail_count={}", userId, latest.getFailCount());
            }
        } catch (Exception nested) {
            log.warn("[AI][MEMORY] fail count update failed userId={} reason={}", userId, nested.getMessage());
        }
        return null;
    }

    // ============================ 渲染 ============================

    /** 渲染当前用户记忆为 markdown:cache hit + db.seq 新鲜 → 直 render;cache hit 但 db.seq 新 → read_stale 回源;cache miss → 回源;新用户 → 返 ""。 */
    public String renderMemorySection(Long userId) {
        Optional<CacheSnapshot> cached = cache.get(userId);
        UserMemorySnapshot snap;
        if (cached.isPresent()) {
            Long dbSeq = mapper.getComputeSeq(userId);
            if (dbSeq != null && dbSeq <= cached.get().computeSeq()) {
                snap = new UserMemorySnapshot(cached.get().identityJson(), cached.get().preferenceJson());
            } else {
                long lag = dbSeq == null ? 0L : Math.max(0L, dbSeq - cached.get().computeSeq());
                metrics.recordReadStale(UserMemoryMetrics.lagBucket(lag));
                snap = loadFromDbOrEmpty(userId, cached.get());
            }
        } else {
            snap = loadFromDbOrEmpty(userId, null);
        }
        String rendered = builder.renderForPrompt(snap);
        if (rendered.isEmpty()) {
            metrics.recordReadMiss();
        }
        return rendered;
    }

    /** 从 DB 读画像 → 写回 cache(setIfAbsentNewer) → 返 snapshot。用户不存在 → 返全空 snapshot。 */
    private UserMemorySnapshot loadFromDbOrEmpty(Long userId, CacheSnapshot fallback) {
        UserMemoryEntity e = mapper.selectById(userId);
        if (e == null) {
            return fallback == null
                    ? new UserMemorySnapshot("{}", "{}")
                    : new UserMemorySnapshot(fallback.identityJson(), fallback.preferenceJson());
        }
        String identityJson = e.getIdentityJson() == null ? "{}" : e.getIdentityJson();
        String preferenceJson = e.getPreferenceJson() == null ? "{}" : e.getPreferenceJson();
        long seq = e.getComputeSeq() == null ? 0L : e.getComputeSeq();
        Instant computed = e.getComputedAt() == null
                ? Instant.now()
                : e.getComputedAt().atZone(java.time.ZoneId.systemDefault()).toInstant();
        cache.setIfAbsentNewer(userId, new CacheSnapshot(identityJson, preferenceJson, seq, computed));
        return new UserMemorySnapshot(identityJson, preferenceJson);
    }

    // ============================ Reset ============================

    /** GDPR Art 17 主动重置:清 JSON + 版 +1,清 cache,记审计,异步清理 audit 表。同步部分必须 < 100ms。 */
    public boolean reset(Long userId) {
        try {
            mapper.resetMemory(userId);
        } catch (Exception e) {
            log.warn("[AI][MEMORY] reset mapper update failed userId={} reason={}", userId, e.getMessage());
        }
        cache.invalidate(userId);
        auditService.logReset(userId, null, null);
        // B3 fix(Bug 2):purgeAuditAsync 已是 @Async("memoryAsyncExecutor"),直接调;
        // 外层再 asyncExecutor.execute 是双层调度,浪费池槽位且延迟增加。
        auditService.purgeAuditAsync(userId);
        return true;
    }

    /** GET /ai/memory:返回元数据 + 字段清单 + 数据用途声明。<b>不暴露</b>原始 JSON 画像内容,避免敏感偏好外泄。 */
    public UserMemoryOverviewVO buildOverview(Long userId) {
        UserMemoryEntity e = mapper.selectById(userId);
        if (e == null) {
            return new UserMemoryOverviewVO(false, false, null, 0,
                    "尚未生成记忆;首次对话后将自动建立。",
                    List.of(), List.of(),
                    "AI 助手个性化推荐;不用于广告投放、不分享给第三方");
        }
        boolean hasIdentity = !"{}".equals(e.getIdentityJson());
        boolean hasPreference = !"{}".equals(e.getPreferenceJson());
        Instant computedAt = e.getComputedAt() == null
                ? Instant.now()
                : e.getComputedAt().atZone(java.time.ZoneId.systemDefault()).toInstant();
        return new UserMemoryOverviewVO(hasIdentity, hasPreference, computedAt,
                e.getVersion() == null ? 0 : e.getVersion(),
                "我们记住了你的基础资料和最近 90 天的购买偏好,用于个性化推荐。",
                List.of("身份档案", "偏好画像"),
                List.of("默认地址", "账号年龄", "价格区间", "偏好类目", "偏好商家", "退货率"),
                "AI 助手个性化推荐;不用于广告投放、不分享给第三方");
    }

    // ============================ 周期刷新 ============================

    /** 每 5 分钟刷新 ai_memory_fail_users gauge(以 DB 为准,避免进程内 AtomicLong 漂移)。{@code recompute_status=0} 的总行数。 */
    @Scheduled(fixedDelay = 5L * 60L * 1000L)
    public void refreshDisabledUserGauge() {
        try {
            Long count = mapper.countDisabledUsers();
            if (count != null) {
                metrics.setDisabledUserCount(count);
            }
        } catch (Exception e) {
            log.debug("[AI][MEMORY] refreshDisabledUserGauge skipped reason={}", e.getMessage());
        }
    }

    // ============================ 内部类 ============================

    /** 内部乐观锁冲突标记 — 让外层 tryRecomputeOnce 区分"重试 vs 真异常"。 */
    private static class OptimisticLockRetryException extends RuntimeException {
        OptimisticLockRetryException(String msg) {
            super(msg);
        }
    }
}

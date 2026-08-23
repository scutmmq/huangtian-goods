package com.scutmmq.ai.service;

import com.scutmmq.ai.cache.UserMemoryCache;
import com.scutmmq.ai.cache.UserMemoryCache.CacheSnapshot;
import com.scutmmq.ai.config.AiMemoryProperties;
import com.scutmmq.ai.entity.TriggerReason;
import com.scutmmq.ai.entity.UserMemoryEntity;
import com.scutmmq.ai.mapper.UserMemoryMapper;
import com.scutmmq.ai.security.PromptSanitizer;
import com.scutmmq.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * B3 step5: 用户长期记忆的协调层。
 *
 * <p>职责边界:
 * <ul>
 *   <li><b>防抖</b> {@link #scheduleRecompute} — Redis SETNX 做跨实例合并,
 *       Redis 不可用时降级为本地 Guava RateLimiter 风格的令牌桶</li>
 *   <li><b>重算</b> {@link #recomputeFor} — 调 Builder 算 identity + preference,
 *       列级 UPDATE(配合 @Version 校验);JSON 超 8KB 自动降级为 {}</li>
 *   <li><b>读路径</b> {@link #renderMemorySection} — cache → DB 顺序回源,
 *       空快照返回 ""(避免给空画像拼 prompt)</li>
 *   <li><b>GDPR 重置</b> {@link #reset} — 同步清空(同步部分 < 100ms),
 *       异步提交 {@link AuditService#purgeAuditAsync}</li>
 *   <li><b>GDPR 知情人概览</b> {@link #buildOverview} — 只返回元数据 + 字段清单,
 *       <b>不暴露</b>原始 JSON 画像内容</li>
 * </ul>
 *
 * <p>关键依赖:
 * <ul>
 *   <li>{@link UserMemoryMapper} — BaseMapper + 7 个列级 UPDATE 方法</li>
 *   <li>{@link UserMemoryBuilder} — Task 4 注入真实实现,本类只关心接口</li>
 *   <li>{@link UserMemoryCache} — HMAC key + Lua TOCTOU 防护</li>
 *   <li>{@code memoryAsyncExecutor} — 已在 AiTaskExecutorConfig 定义(core=1,max=2,queue=50),
 *       由 Task 9 implementer 加了 {@code @EnableAsync};{@link AuditService#purgeAuditAsync} 同池复用</li>
 * </ul>
 */
@Slf4j
@Service
public class UserMemoryService {

    /** 重算乐观锁冲突最多重试 1 次(2 次机会)。 */
    private static final int MAX_OPTIMISTIC_RETRY = 2;

    /** 新用户没有任何画像时,render 返回该占位(空串)。 */
    private static final String EMPTY_RENDER = "";

    /** 兜底 RateLimiter:Redis Down 时使用本地令牌桶。 */
    private final ConcurrentHashMap<Long, LocalTokenBucket> localLimiters = new ConcurrentHashMap<>();

    private final StringRedisTemplate redis;
    private final UserMemoryMapper mapper;
    private final UserMemoryBuilder builder;
    private final UserMemoryCache cache;
    @SuppressWarnings("unused") // 注入以保持接口完整,后续 Task 6 事件里再用
    private final PromptSanitizer sanitizer;
    private final AuditService auditService;
    private final AiMemoryProperties props;
    private final Executor asyncExecutor;

    public UserMemoryService(StringRedisTemplate redis,
                             UserMemoryMapper mapper,
                             UserMemoryBuilder builder,
                             UserMemoryCache cache,
                             PromptSanitizer sanitizer,
                             AuditService auditService,
                             AiMemoryProperties props,
                             @Qualifier("memoryAsyncExecutor") Executor asyncExecutor) {
        this.redis = redis;
        this.mapper = mapper;
        this.builder = builder;
        this.cache = cache;
        this.sanitizer = sanitizer;
        this.auditService = auditService;
        this.props = props;
        this.asyncExecutor = asyncExecutor;
    }

    // ============================ 防抖 ============================

    /**
     * 调度一次重算。Redis 可达时用 SETNX 做跨实例合并;Redis 抛异常降级到
     * 本地令牌桶。返回 {@code true} 表示已成功提交(自己或别人在窗口内已合并则返 {@code false})。
     */
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

    /**
     * 本地令牌桶兜底:每个用户独立桶,容量 = {@code localRateLimitBurst},
     * 每秒补充 {@code localRateLimitPerUser} 个令牌。
     * 桶满放行时记 {@code DEGRADED_RATE_LIMITED},桶空 DROP 记 {@code DEGRADED_RATE_LIMITED_DROP}。
     */
    private boolean scheduleWithLocalRateLimit(Long userId, TriggerReason reason) {
        LocalTokenBucket limiter = localLimiters.computeIfAbsent(userId, uid ->
                new LocalTokenBucket(
                        Math.max(1, props.getLocalRateLimitBurst()),
                        Math.max(1, props.getLocalRateLimitPerUser())));
        if (limiter.tryAcquire()) {
            auditService.logDegraded(userId, "DEGRADED_RATE_LIMITED");
            asyncExecutor.execute(() -> recomputeFor(userId, reason));
            return true;
        }
        auditService.logDegraded(userId, "DEGRADED_RATE_LIMITED_DROP");
        return false;
    }

    // ============================ 重算 ============================

    /**
     * 重算某用户的记忆。整体在内存中跑完后落库 4 个写:
     * computeIdentity → updateIdentity → computePreference → updatePreference → bumpComputeSeq。
     * 失败 3 次(fail_count >= recomputeMaxFailCount)后 {@code recompute_status=0} 熔断,
     * 后续 {@link #scheduleRecompute} 即使 SETNX 成功也走 DB 读路径跳过更新。
     *
     * <p>返回值:成功时返回最终 snapshot(供调用方继续使用),失败返回 null。
     */
    public UserMemorySnapshot recomputeFor(Long userId, TriggerReason reason) {
        UserMemoryEntity entity = mapper.selectById(userId);
        int baseVersion = entity == null ? 0 : entity.getVersion();

        try {
            return doRecompute(userId, reason, entity, baseVersion);
        } catch (Exception e) {
            return handleRecomputeFailure(userId, e);
        }
    }

    private UserMemorySnapshot doRecompute(Long userId, TriggerReason reason,
                                           UserMemoryEntity entity, int baseVersion) {
        Throwable lastEx = null;
        for (int attempt = 1; attempt <= MAX_OPTIMISTIC_RETRY; attempt++) {
            try {
                return tryRecomputeOnce(userId, reason, entity,
                        baseVersion + (attempt - 1) /* 每次重试 version 已在 db 中递增 */);
            } catch (DataIntegrityViolationException dive) {
                return handleJsonOverflow(userId, entity, baseVersion, dive);
            } catch (OptimisticLockRetryException retry) {
                lastEx = retry.getCause();
                log.warn("[AI][MEMORY] optimistic lock conflict userId={} attempt={}/{}, retrying",
                        userId, attempt, MAX_OPTIMISTIC_RETRY);
            }
        }
        // 用尽重试,记录失败 — lastEx 是 Throwable(cause),RuntimeException 构造签名不匹配,
        // 改成不传 cause,只 log 原始 message
        log.error("[AI][MEMORY] recompute exhausted retry userId={} lastReason={}",
                userId, lastEx == null ? "null" : lastEx.getMessage());
        return null;
    }

    /**
     * 单次重算尝试。乐观锁冲突(0 行受影响)抛 {@link OptimisticLockRetryException} 让外层重试。
     * 真正的 JSON OVERFLOW 抛 {@link DataIntegrityViolationException} 走降级。
     */
    private UserMemorySnapshot tryRecomputeOnce(Long userId, TriggerReason reason,
                                                UserMemoryEntity entity, int expectedVersion) {
        // 1. compute identity
        UserMemorySnapshot identitySnap = builder.computeIdentity(userId);
        String identityJson = identitySnap == null ? "{}" : identitySnap.identityJson();

        // 2. write identity (with version check)
        int rowsIdentity = mapper.updateIdentity(userId, identityJson, expectedVersion);
        if (rowsIdentity == 0) {
            throw new OptimisticLockRetryException("updateIdentity rows=0");
        }
        int versionAfterIdentity = expectedVersion + 1;

        // 3. compute preference
        UserMemorySnapshot prefSnap = builder.computePreference(userId);
        String prefJson = prefSnap == null ? "{}" : prefSnap.preferenceJson();
        int rowsPreference = mapper.updatePreference(userId, prefJson, versionAfterIdentity);
        if (rowsPreference == 0) {
            throw new OptimisticLockRetryException("updatePreference rows=0");
        }
        int versionAfterPreference = versionAfterIdentity + 1;

        // 4. bump compute_seq
        mapper.bumpComputeSeq(userId, versionAfterPreference);

        // 5. invalidate cache
        cache.invalidate(userId);

        // 6. audit
        auditService.logCompute(userId, reason.name());

        return new UserMemorySnapshot(identityJson, prefJson);
    }

    /**
     * 处理 JSON 超长触发 chk_identity_size / chk_preference_size 约束冲突:
     * 把两个 JSON 都写成 {} 兜底,记一次 JSON_OVERFLOW 审计。
     */
    private UserMemorySnapshot handleJsonOverflow(Long userId, UserMemoryEntity entity,
                                                  int baseVersion,
                                                  DataIntegrityViolationException dive) {
        String field = dive.getMessage() != null && dive.getMessage().contains("identity") ? "identity" : "preference";
        auditService.logJsonOverflow(userId, field);
        log.warn("[AI][MEMORY] JSON OVERFLOW userId={} field={} reason={}",
                userId, field, dive.getMessage());
        int currentVersion = entity == null ? 0 : entity.getVersion();
        try {
            mapper.updateIdentity(userId, "{}", currentVersion);
            mapper.updatePreference(userId, "{}", currentVersion + 1);
        } catch (Exception ignore) {
            // 兜底也失败 → 不阻塞,只 log
            log.warn("[AI][MEMORY] JSON OVERFLOW fallback write failed userId={} reason={}",
                    userId, ignore.getMessage());
        }
        return new UserMemorySnapshot("{}", "{}");
    }

    /**
     * 重算失败兜底:fail_count++,达到 {@code recomputeMaxFailCount} 熔断。
     * 重算失败不抛 — 静默 log,避免阻塞主链路。
     */
    private UserMemorySnapshot handleRecomputeFailure(Long userId, Exception e) {
        log.error("[AI][MEMORY] recompute failed userId={} reason={}", userId, e.getMessage());
        auditService.logRecomputeFail(userId, e);
        try {
            mapper.incrementFailCount(userId);
            UserMemoryEntity latest = mapper.selectById(userId);
            if (latest != null && latest.getFailCount() != null
                    && latest.getFailCount() >= props.getRecomputeMaxFailCount()) {
                mapper.markDisabled(userId);
                log.warn("[AI][MEMORY] recompute DISABLED userId={} after fail_count={}",
                        userId, latest.getFailCount());
            }
        } catch (Exception nested) {
            log.warn("[AI][MEMORY] fail count update failed userId={} reason={}",
                    userId, nested.getMessage());
        }
        return null;
    }

    // ============================ 渲染 ============================

    /**
     * 把当前用户记忆渲染成可注入 prompt 的 markdown 文本。
     * <ol>
     *   <li>cache hit 且 seq 仍新鲜 → 直接 render</li>
     *   <li>cache hit 但 db.seq 更新 → 回源 DB</li>
     *   <li>cache miss → 回源 DB</li>
     *   <li>新用户(entity 为空)→ 返回 ""</li>
     * </ol>
     */
    public String renderMemorySection(Long userId) {
        Optional<CacheSnapshot> cached = cache.get(userId);
        UserMemorySnapshot snap;

        if (cached.isPresent()) {
            Long dbSeq = mapper.getComputeSeq(userId);
            if (dbSeq != null && dbSeq <= cached.get().computeSeq()) {
                // seq 仍新鲜,直接用缓存
                snap = new UserMemorySnapshot(cached.get().identityJson(), cached.get().preferenceJson());
            } else {
                // seq 已过期,回源 DB
                snap = loadFromDbOrEmpty(userId, cached.get());
            }
        } else {
            snap = loadFromDbOrEmpty(userId, null);
        }

        return builder.renderForPrompt(snap);
    }

    /**
     * 从 DB 读取,写回 cache,然后返回 snapshot。
     * 如果用户不存在(刚注册尚未生成画像)→ 返回全空 snapshot,后续 {@code renderForPrompt}
     * 必须能正确处理空 snapshot 返回 ""。
     */
    private UserMemorySnapshot loadFromDbOrEmpty(Long userId, CacheSnapshot fallback) {
        UserMemoryEntity e = mapper.selectById(userId);
        if (e == null) {
            // 新用户
            if (fallback != null) {
                return new UserMemorySnapshot(fallback.identityJson(), fallback.preferenceJson());
            }
            return new UserMemorySnapshot("{}", "{}");
        }
        String identityJson = e.getIdentityJson() == null ? "{}" : e.getIdentityJson();
        String preferenceJson = e.getPreferenceJson() == null ? "{}" : e.getPreferenceJson();
        long seq = e.getComputeSeq() == null ? 0L : e.getComputeSeq();
        Instant computed = e.getComputedAt() == null
                ? Instant.now()
                : e.getComputedAt().atZone(java.time.ZoneId.systemDefault()).toInstant();
        cache.setIfAbsentNewer(userId,
                new CacheSnapshot(identityJson, preferenceJson, seq, computed));
        return new UserMemorySnapshot(identityJson, preferenceJson);
    }

    // ============================ Reset ============================

    /**
     * GDPR Art 17 用户主动重置:把两个 JSON 字段清空(版 +1),清 cache,记审计,
     * <b>同步</b>部分必须 < 100ms;异步清理 audit 表。
     */
    public boolean reset(Long userId) {
        try {
            mapper.resetMemory(userId);
        } catch (Exception e) {
            log.warn("[AI][MEMORY] reset mapper update failed userId={} reason={}",
                    userId, e.getMessage());
        }
        cache.invalidate(userId);
        auditService.logReset(userId, null, null);
        asyncExecutor.execute(() -> auditService.purgeAuditAsync(userId));
        return true;
    }

    // ============================ GDPR Art 15 ============================

    /**
     * GET /ai/memory:返回用户记忆的元数据 + 字段清单 + 数据用途声明。
     * 不返回原始 JSON 画像内容,避免敏感偏好外泄。
     */
    public UserMemoryOverviewVO buildOverview(Long userId) {
        UserMemoryEntity e = mapper.selectById(userId);
        if (e == null) {
            return new UserMemoryOverviewVO(
                    false, false, null, 0,
                    "尚未生成记忆;首次对话后将自动建立。",
                    List.of(),
                    List.of(),
                    "AI 助手个性化推荐;不用于广告投放、不分享给第三方");
        }
        boolean hasIdentity = !"{}".equals(e.getIdentityJson());
        boolean hasPreference = !"{}".equals(e.getPreferenceJson());
        return new UserMemoryOverviewVO(
                hasIdentity, hasPreference,
                e.getComputedAt() == null ? LocalDateTime.now() : e.getComputedAt(),
                e.getVersion() == null ? 0 : e.getVersion(),
                "我们记住了你的基础资料和最近 90 天的购买偏好,用于个性化推荐。",
                List.of("身份档案", "偏好画像"),
                List.of("默认地址", "账号年龄", "价格区间", "偏好类目", "偏好商家", "退货率"),
                "AI 助手个性化推荐;不用于广告投放、不分享给第三方");
    }

    // ============================ 内部类 ============================

    /**
     * 内部乐观锁冲突标记 — 让外层 tryRecomputeOnce 区分"重试 vs 真异常"。
     */
    private static class OptimisticLockRetryException extends RuntimeException {
        OptimisticLockRetryException(String msg) {
            super(msg);
        }
    }

    /**
     * 简易令牌桶:不引 Guava(项目无依赖),用 synchronized 保证线程安全。
     * 容量 = {@code capacity},每隔 {@code refillIntervalNanos} 补 1 个令牌。
     */
    static final class LocalTokenBucket {
        private final long capacity;
        private final long refillIntervalNanos;
        private long tokens;
        private long lastRefillNanos;

        LocalTokenBucket(long capacity, long refillPerSecond) {
            this.capacity = capacity;
            this.refillIntervalNanos = TimeUnit.SECONDS.toNanos(1) / Math.max(1, refillPerSecond);
            this.tokens = capacity;
            this.lastRefillNanos = System.nanoTime();
        }

        synchronized boolean tryAcquire() {
            long now = System.nanoTime();
            long elapsed = now - lastRefillNanos;
            if (elapsed > 0) {
                long refilled = elapsed / refillIntervalNanos;
                if (refilled > 0) {
                    tokens = Math.min(capacity, tokens + refilled);
                    lastRefillNanos += refilled * refillIntervalNanos;
                }
            }
            if (tokens > 0) {
                tokens--;
                return true;
            }
            return false;
        }
    }
}

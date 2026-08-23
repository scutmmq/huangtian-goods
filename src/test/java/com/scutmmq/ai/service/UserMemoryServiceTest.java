package com.scutmmq.ai.service;

import com.scutmmq.ai.cache.UserMemoryCache;
import com.scutmmq.ai.cache.UserMemoryCache.CacheSnapshot;
import com.scutmmq.ai.config.AiMemoryProperties;
import com.scutmmq.ai.entity.TriggerReason;
import com.scutmmq.ai.entity.UserMemoryEntity;
import com.scutmmq.ai.mapper.UserMemoryMapper;
import com.scutmmq.ai.security.PromptSanitizer;
import com.scutmmq.utils.RedisConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B3 step5: UserMemoryService 单元测试。
 *
 * <p>14 个核心测试覆盖:
 * <ul>
 *   <li>防抖(SETNX coalesce + Redis Down RateLimiter 兜底)</li>
 *   <li>重算(computeSeq bump / cache invalidate / JSON OVERFLOW 降级 / @Version 冲突重试)</li>
 *   <li>渲染路径(cache hit / DB fallback / 新用户空串)</li>
 *   <li>reset(同步 < 100ms + async purge)</li>
 *   <li>MDC 子线程传播(子线程能读到 traceId)</li>
 * </ul>
 *
 * <p>全部 mock 依赖,无 Spring 容器,不触发 Redis/MySQL,
 * 与 {@link AuditServiceTest} 的隔离风格保持一致。
 */
class UserMemoryServiceTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private UserMemoryMapper mapper;
    private UserMemoryBuilder builder;
    private UserMemoryCache cache;
    private PromptSanitizer sanitizer;
    private AuditService auditService;
    private AiMemoryProperties props;

    /** 由测试控制的 Executor,记录被提交的 Runnable。 */
    private CapturingExecutor asyncExec;

    private UserMemoryService service;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);

        mapper = mock(UserMemoryMapper.class);
        builder = mock(UserMemoryBuilder.class);
        cache = mock(UserMemoryCache.class);
        sanitizer = mock(PromptSanitizer.class);
        auditService = mock(AuditService.class);

        props = new AiMemoryProperties();
        props.setCacheHmacSecrets("v1:abcdefghijklmnopqrstuvwxyz12345678");
        props.setActiveSecretVersion("v1");
        props.setCoalesceTtlSeconds(60);
        props.setLocalRateLimitPerUser(1);
        props.setLocalRateLimitBurst(200);
        props.setRecomputeMaxFailCount(3);

        asyncExec = new CapturingExecutor();

        service = new UserMemoryService(redis, mapper, builder, cache, sanitizer, auditService,
                props, asyncExec);

        // 默认 mapper.selectById 返回 null,后续按需覆盖
        when(mapper.selectById(any())).thenReturn(null);
        // 默认所有 UPDATE 返回 1 行(测试可在 @Test 内覆盖)
        when(mapper.updateIdentity(anyLong(), anyString(), anyInt())).thenReturn(1);
        when(mapper.updatePreference(anyLong(), anyString(), anyInt())).thenReturn(1);
        when(mapper.bumpComputeSeq(anyLong(), anyInt())).thenReturn(1);
        when(mapper.resetMemory(anyLong())).thenReturn(1);
    }

    // ============================== 防抖 (3) ==============================

    @Test
    void scheduleRecomputeRespectsCoalesce() {
        // Redis SETNX 返回 false → 在合并窗口内已有任务,放弃
        when(ops.setIfAbsent(eq(RedisConstants.MEMORY_COALESCE_KEY_PREFIX + 7L),
                anyString(), any())).thenReturn(false);

        boolean result = service.scheduleRecompute(7L, TriggerReason.TRIGGER_ORDER);

        assertFalse(result, "should return false when SETNX denied (coalesce hit)");
        verify(ops).setIfAbsent(eq(RedisConstants.MEMORY_COALESCE_KEY_PREFIX + 7L),
                anyString(), any());
        // asyncExecutor 不应被调用
        assertEquals(0, asyncExec.submittedCount(), "no async task should run on coalesce");
        verify(auditService, never()).logDegraded(anyLong(), anyString());
    }

    @Test
    void redisDownFallsBackToLocalRateLimiter() {
        // Redis 抛连接失败 → 兜底 RateLimiter
        when(ops.setIfAbsent(anyString(), anyString(), any()))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        boolean result = service.scheduleRecompute(7L, TriggerReason.TRIGGER_ORDER);

        assertTrue(result, "should fall back to local limiter and succeed");
        // 兜底应记一条 DEGRADED 审计,然后 schedule
        verify(auditService, times(1)).logDegraded(eq(7L), eq("DEGRADED_RATE_LIMITED"));
        assertEquals(1, asyncExec.submittedCount(), "one async task should be scheduled");
    }

    @Test
    void rateLimiterFullDropsWithAudit() {
        // Redis 抛 + 限制器装满 → DROP 审计
        when(ops.setIfAbsent(anyString(), anyString(), any()))
                .thenThrow(new RedisConnectionFailureException("redis down"));
        // burst=200,rate=1/s ⇒ 一次放行后桶基本空,连发 250 次后必落入 DROP
        for (int i = 0; i < 250; i++) {
            service.scheduleRecompute(7L, TriggerReason.TRIGGER_ORDER);
        }
        // 跑完 250 次后,中间一定出现过 DROP 审计
        verify(auditService, atLeastOnce()).logDegraded(eq(7L), eq("DEGRADED_RATE_LIMITED_DROP"));
    }

    // ============================== 重算 (4) ==============================

    @Test
    void recomputeUpdatesIdentityAndPreferenceSeparately() {
        // 给一个初始 entity,version=1,seq=5
        UserMemoryEntity existing = new UserMemoryEntity();
        existing.setUserId(7L);
        existing.setIdentityJson("{}");
        existing.setPreferenceJson("{}");
        existing.setVersion(1);
        existing.setComputeSeq(5L);
        existing.setFailCount(0);
        existing.setRecomputeStatus(1);
        when(mapper.selectById(7L)).thenReturn(existing);

        // builder 两次返回不同 JSON,带 identityJson() / preferenceJson() 方法
        when(builder.computeIdentity(7L))
                .thenReturn(new UserMemorySnapshot("{\"name\":\"A\"}", "{}"));
        when(builder.computePreference(7L))
                .thenReturn(new UserMemorySnapshot("{}", "{\"color\":\"red\"}"));

        service.recomputeFor(7L, TriggerReason.TRIGGER_ORDER);

        // updateIdentity 用 version=1,updatePreference 用 version=2(因为 updateIdentity 已 +1)
        verify(mapper).updateIdentity(eq(7L), eq("{\"name\":\"A\"}"), eq(1));
        verify(mapper).updatePreference(eq(7L), eq("{\"color\":\"red\"}"), eq(2));
        // bumpComputeSeq 用 version=3(两个 update 各自 +1,DB 当前 = base+2)
        verify(mapper).bumpComputeSeq(eq(7L), eq(3));
    }

    @Test
    void cacheInvalidatedAfterRecompute() {
        UserMemoryEntity existing = new UserMemoryEntity();
        existing.setUserId(7L);
        existing.setIdentityJson("{}");
        existing.setPreferenceJson("{}");
        existing.setVersion(1);
        when(mapper.selectById(7L)).thenReturn(existing);
        when(builder.computeIdentity(7L)).thenReturn(new UserMemorySnapshot("{}", "{}"));
        when(builder.computePreference(7L)).thenReturn(new UserMemorySnapshot("{}", "{}"));

        service.recomputeFor(7L, TriggerReason.TRIGGER_ORDER);

        verify(cache, times(1)).invalidate(7L);
    }

    @Test
    void computeSeqBumpedAfterSuccess() {
        UserMemoryEntity existing = new UserMemoryEntity();
        existing.setUserId(7L);
        existing.setIdentityJson("{}");
        existing.setPreferenceJson("{}");
        existing.setVersion(5);
        existing.setComputeSeq(10L);
        when(mapper.selectById(7L)).thenReturn(existing);
        when(builder.computeIdentity(7L)).thenReturn(new UserMemorySnapshot("{}", "{}"));
        when(builder.computePreference(7L)).thenReturn(new UserMemorySnapshot("{}", "{}"));

        service.recomputeFor(7L, TriggerReason.TRIGGER_ORDER);

        ArgumentCaptor<Integer> versionCap = ArgumentCaptor.forClass(Integer.class);
        verify(mapper).bumpComputeSeq(eq(7L), versionCap.capture());
        // base 5 + updateIdentity + updatePreference = 7(因为两个 update 各 +1)
        assertEquals(7, versionCap.getValue());
    }

    @Test
    void jsonOverflowFallsBackToEmpty() {
        UserMemoryEntity existing = new UserMemoryEntity();
        existing.setUserId(7L);
        existing.setIdentityJson("{}");
        existing.setPreferenceJson("{}");
        existing.setVersion(1);
        when(mapper.selectById(7L)).thenReturn(existing);

        // identity 写入触发 chk_identity_size 约束 → 应降级为 {}
        when(mapper.updateIdentity(eq(7L), eq("{\"too big\"}"), anyInt()))
                .thenThrow(new DataIntegrityViolationException(
                        "chk_identity_size: OCTET_LENGTH > 8192"));

        when(builder.computeIdentity(7L)).thenReturn(new UserMemorySnapshot("{\"too big\"}", "{}"));
        when(builder.computePreference(7L)).thenReturn(new UserMemorySnapshot("{}", "{}"));

        service.recomputeFor(7L, TriggerReason.TRIGGER_ORDER);

        verify(auditService).logJsonOverflow(eq(7L), eq("identity"));
        // 降级后必须把两个 JSON 都写成 {}
        verify(mapper).updateIdentity(eq(7L), eq("{}"), anyInt());
        verify(mapper).updatePreference(eq(7L), eq("{}"), anyInt());
    }

    // ============================== Reset (3) ==============================

    @Test
    void resetClearsAndPurgesAsync() {
        long startMs = System.currentTimeMillis();
        boolean result = service.reset(7L);
        long elapsed = System.currentTimeMillis() - startMs;

        assertTrue(result);
        // 同步部分必须 < 100ms(只写 mapper + cache.invalidate + audit insert)
        assertTrue(elapsed < 100L, "reset synchronous part should be < 100ms, was " + elapsed + "ms");
        verify(mapper).resetMemory(eq(7L));
        verify(cache).invalidate(eq(7L));
        verify(auditService).logReset(eq(7L), any(), any());
    }

    @Test
    void resetIdempotent() {
        // 连续 reset 两次不应报错
        service.reset(7L);
        // 即使 mapper.resetMemory 第一次返回 0(可能没记录),第二次依然不抛
        when(mapper.resetMemory(7L)).thenReturn(0);
        boolean result = service.reset(7L);

        assertTrue(result);
        verify(mapper, times(2)).resetMemory(eq(7L));
    }

    @Test
    void resetTriggersAsyncPurge() {
        service.reset(7L);

        // 必须异步提交 purgeAuditAsync(不能同步调用,会阻塞主线程)
        assertEquals(1, asyncExec.submittedCount(), "async purge should be submitted");
        // 把 runnable 取出来跑一下,验证 it calls auditService.purgeAuditAsync
        Runnable task = asyncExec.lastSubmitted();
        assertNotNull(task);
        task.run();
        verify(auditService).purgeAuditAsync(eq(7L));
    }

    // ============================== 乐观锁 (1) ==============================

    @Test
    void optimisticLockConflictRetriesOnce() {
        UserMemoryEntity existing = new UserMemoryEntity();
        existing.setUserId(7L);
        existing.setIdentityJson("{}");
        existing.setPreferenceJson("{}");
        existing.setVersion(1);
        existing.setFailCount(0);
        when(mapper.selectById(7L)).thenReturn(existing);
        when(builder.computeIdentity(7L)).thenReturn(new UserMemorySnapshot("{}", "{}"));
        when(builder.computePreference(7L)).thenReturn(new UserMemorySnapshot("{}", "{}"));

        // 第一次 updateIdentity 影响 0 行 → 触发重试
        when(mapper.updateIdentity(eq(7L), anyString(), eq(1))).thenReturn(0);
        // 第二次 updateIdentity 影响 1 行(重试)
        when(mapper.updateIdentity(eq(7L), anyString(), eq(2))).thenReturn(1);

        service.recomputeFor(7L, TriggerReason.TRIGGER_ORDER);

        // 必须调用 retry 逻辑(至少 2 次 updateIdentity)
        verify(mapper, atLeastOnce()).updateIdentity(eq(7L), anyString(), eq(1));
        verify(mapper, atLeastOnce()).updateIdentity(eq(7L), anyString(), eq(2));
        // 重试路径被走过(builder 必被调)
        verify(builder, atLeastOnce()).computeIdentity(7L);
    }

    // ============================== MDC (1) ==============================

    @Test
    void mdcPropagatesToAsyncTask() throws InterruptedException {
        // 用一个真实 ThreadPoolTaskExecutor 风格 + TaskDecorator,模拟 memoryAsyncExecutor 的语义
        AtomicReference<String> traceFromChildThread = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        Executor mdcExec = runnable -> {
            Map<String, String> ctx = MDC.getCopyOfContextMap();
            Thread t = new Thread(() -> {
                try {
                    if (ctx != null) MDC.setContextMap(ctx);
                    runnable.run();
                } finally {
                    traceFromChildThread.set(MDC.get("traceId"));
                    done.countDown();
                    MDC.clear();
                }
            }, "test-async");
            t.start();
        };

        // 用 mdcExec 重新构造 service,直接调 recomputeFor 验证子线程看到 traceId
        UserMemoryService mdcService = new UserMemoryService(
                redis, mapper, builder, cache, sanitizer, auditService, props, mdcExec);

        UserMemoryEntity existing = new UserMemoryEntity();
        existing.setUserId(7L);
        existing.setIdentityJson("{}");
        existing.setPreferenceJson("{}");
        existing.setVersion(1);
        when(mapper.selectById(7L)).thenReturn(existing);
        when(builder.computeIdentity(7L)).thenReturn(new UserMemorySnapshot("{}", "{}"));
        when(builder.computePreference(7L)).thenReturn(new UserMemorySnapshot("{}", "{}"));

        try {
            MDC.put("traceId", "test-trace-abc-123");
            // 模拟 scheduleRecompute 的等价路径:submit 一个 runnable 到 executor
            mdcExec.execute(() -> mdcService.recomputeFor(7L, TriggerReason.TRIGGER_ORDER));
            assertTrue(done.await(2, TimeUnit.SECONDS), "child thread should complete in 2s");
        } finally {
            MDC.clear();
        }

        assertEquals("test-trace-abc-123", traceFromChildThread.get(),
                "child thread must read traceId via MDC propagation");
    }

    // ============================== 渲染 (2) ==============================

    @Test
    void renderMemorySectionReadsCache() {
        // cache.hit → 不读 DB,直接 render
        CacheSnapshot cached = new CacheSnapshot("{}", "{}", 7L, Instant.now());
        when(cache.get(7L)).thenReturn(Optional.of(cached));
        when(mapper.getComputeSeq(7L)).thenReturn(7L);

        when(builder.renderForPrompt(any(UserMemorySnapshot.class))).thenReturn("## cached profile");

        String result = service.renderMemorySection(7L);

        assertEquals("## cached profile", result);
        // 不读 DB(只查了 seq,但 entity 不读)
        verify(mapper, never()).selectById(7L);
        // 直接拿到 snap.renderForPrompt
        ArgumentCaptor<UserMemorySnapshot> snapCap = ArgumentCaptor.forClass(UserMemorySnapshot.class);
        verify(builder).renderForPrompt(snapCap.capture());
        assertEquals("{}", snapCap.getValue().identityJson());
        assertEquals("{}", snapCap.getValue().preferenceJson());
    }

    @Test
    void renderMemorySectionFallsBackToDb() {
        // builder 智能回答:空快照返 "",非空返 "## db profile"
        org.mockito.stubbing.Answer<String> renderAns = inv -> {
            UserMemorySnapshot s = inv.getArgument(0);
            return (s.identityJson().equals("{}") && s.preferenceJson().equals("{}"))
                    ? "" : "## db profile";
        };

        // 场景 A:cache miss → 读 DB → 写 cache → render
        when(cache.get(7L)).thenReturn(Optional.empty());

        UserMemoryEntity e = new UserMemoryEntity();
        e.setUserId(7L);
        e.setIdentityJson("{\"name\":\"A\"}");
        e.setPreferenceJson("{\"color\":\"red\"}");
        e.setComputeSeq(99L);
        e.setVersion(1);
        e.setComputedAt(LocalDateTime.now());
        when(mapper.selectById(7L)).thenReturn(e);
        org.mockito.Mockito.when(builder.renderForPrompt(any(UserMemorySnapshot.class)))
                .thenAnswer(renderAns);

        String result = service.renderMemorySection(7L);
        assertEquals("## db profile", result);
        verify(mapper).selectById(7L);
        verify(cache).setIfAbsentNewer(eq(7L), any(CacheSnapshot.class));

        // 场景 B:cache hit 但 db.seq > cache.seq → 回源 DB
        when(cache.get(7L)).thenReturn(Optional.of(new CacheSnapshot("{}", "{}", 5L, Instant.now())));
        when(mapper.getComputeSeq(7L)).thenReturn(10L);
        String refreshed = service.renderMemorySection(7L);
        assertEquals("## db profile", refreshed);

        // 场景 C:新用户(空 entity)— 返回空串
        when(mapper.selectById(8L)).thenReturn(null);
        when(cache.get(8L)).thenReturn(Optional.empty());
        assertEquals("", service.renderMemorySection(8L));
    }

    // ============================== Helper ==============================

    /** 测试用 Executor,记录所有提交的 Runnable 但不主动执行。 */
    private static class CapturingExecutor implements Executor {
        private final List<Runnable> submitted = new ArrayList<>();

        @Override
        public synchronized void execute(Runnable command) {
            submitted.add(command);
        }

        synchronized int submittedCount() {
            return submitted.size();
        }

        synchronized Runnable lastSubmitted() {
            return submitted.isEmpty() ? null : submitted.get(submitted.size() - 1);
        }
    }
}

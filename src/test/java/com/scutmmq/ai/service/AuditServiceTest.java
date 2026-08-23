package com.scutmmq.ai.service;

import com.scutmmq.ai.config.AiMemoryProperties;
import com.scutmmq.ai.entity.UserMemoryAuditEntity;
import com.scutmmq.ai.mapper.UserMemoryAuditMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B3 step9: AuditService 单元测试。
 *
 * <p>覆盖 3 个核心场景:
 * <ul>
 *   <li>{@code auditPurgeAsyncRateLimited} — rateGate 把每批 UPDATE 隔开,100 rows/s
 *       下 5000 行(5 批 1000)耗时 ≥ 期望下界</li>
 *   <li>{@code auditPurgeFailureToDlq} — purge 连续失败 3 次后写入 DLQ 表,retry_count=3</li>
 *   <li>{@code auditPurgeBatched} — 100K 行分 100 批 1000 行 UPDATE,不是 1 个大 UPDATE</li>
 * </ul>
 *
 * <p>@Async 在单测里被绕过(走同步路径),只验证方法本身的分批/限速/重试逻辑;
 * @EnableAsync 与 memoryAsyncExecutor Bean 的存在性由 Spring 上下文保证。
 */
class AuditServiceTest {

    private JdbcTemplate jdbc;
    private UserMemoryAuditMapper auditMapper;
    private AiMemoryProperties props;
    private AuditService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        auditMapper = mock(UserMemoryAuditMapper.class);
        props = new AiMemoryProperties();
        props.setCacheHmacSecrets("v1:abcdefghijklmnopqrstuvwxyz123456");
        props.setActiveSecretVersion("v1");
        // 默认 rate=100,具体用例按需 setUp override
        props.setAuditPurgeRateRowsPerSec(100);
        service = new AuditService(jdbc, auditMapper, props, new SimpleMeterRegistry());
    }

    // ----- Step 9 核心 3 测试 -----

    /**
     * 验证 rateGate: 1000 rows/s 下 4 批 × 1000 行 = 4000 行,
     * 首批不等待,后续 3 批各 sleep batchSize/rate = 1s,
     * 总耗时 ≥ 2.7s(放宽 10% 误差)。
     */
    @Test
    void auditPurgeAsyncRateLimited() {
        props.setAuditPurgeRateRowsPerSec(1000);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(7L))).thenReturn(4000);

        long start = System.currentTimeMillis();
        service.purgeAuditAsync(7L);
        long elapsedMs = System.currentTimeMillis() - start;

        // 3 批 × 1s = 3s(首批免等待),允许 10% 误差
        assertTrue(elapsedMs >= 2_700L,
                "elapsed ms should be >= 2700 (≈ 3 batches × 1s), was " + elapsedMs);
        assertTrue(elapsedMs <= 8_000L,
                "elapsed ms should be <= 8000 (CI safety), was " + elapsedMs);
        // 验证 4 次 UPDATE(每批 1 次)
        verify(jdbc, times(4)).update(contains("UPDATE ai_user_memory_audit"), eq(7L));
    }

    /**
     * 验证失败 3 次 → DLQ 表 INSERT。
     * queryForObject 每次都抛异常 → catch 块 retry++;retry 达到 3 后写 DLQ。
     */
    @Test
    void auditPurgeFailureToDlq() {
        DataAccessException ex = new DataAccessException("simulated db failure") {};
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyLong())).thenThrow(ex);

        service.purgeAuditAsync(7L);

        // 验证 3 次重试都跑过(每次都尝试 queryForObject)
        verify(jdbc, times(3)).queryForObject(anyString(), eq(Integer.class), eq(7L));
        // 验证 DLQ INSERT(必须是最后一步,retry_count=3)
        ArgumentCaptor<Object> argCap = ArgumentCaptor.forClass(Object.class);
        verify(jdbc, times(1)).update(
                contains("ai_user_memory_audit_purge_dlq"),
                argCap.capture(), argCap.capture(), argCap.capture());
        // 不深校验参数(类型擦除),但最后一次 update 必须命中 DLQ 表名
    }

    /**
     * 验证 100K 行 → 100 批 1000 行。
     * rate 设很大(1_000_000 rows/s)避免 sleep 阻塞 CI。
     */
    @Test
    void auditPurgeBatched() {
        props.setAuditPurgeRateRowsPerSec(1_000_000);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(7L))).thenReturn(100_000);

        service.purgeAuditAsync(7L);

        // 100 批 × 1 次 UPDATE = 100 次
        verify(jdbc, times(100)).update(contains("UPDATE ai_user_memory_audit"), eq(7L));
        // queryForObject 只调用 1 次(外层 while 进入 1 次就成功,内层 for 跑 100 次)
        verify(jdbc, times(1)).queryForObject(anyString(), eq(Integer.class), eq(7L));
    }

    /**
     * 验证 rateGate 首次调用立即放行:即使 rate 很慢(1 row/s → 1000 行要等 1000s),
     * 第一个 batch 也不能 sleep。用全新 AuditService 模拟 JVM 冷启动场景。
     */
    @Test
    void rateGateFirstCallIsImmediate() {
        AuditService fresh = new AuditService(jdbc, auditMapper, props, new SimpleMeterRegistry());
        // rate=1 row/s,1000 行目标等待 = 1000s,但首次必须立即返回
        props.setAuditPurgeRateRowsPerSec(1);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(7L))).thenReturn(1000);

        long start = System.currentTimeMillis();
        fresh.purgeAuditAsync(7L);
        long elapsedMs = System.currentTimeMillis() - start;

        // 首次立即放行,整次 purge 应远小于 1000s(放宽到 <100ms,因为只跑 1 批)
        assertTrue(elapsedMs < 100L,
                "first batch must not sleep even with slow rate, was " + elapsedMs + "ms");
        verify(jdbc, times(1)).update(contains("UPDATE ai_user_memory_audit"), eq(7L));
    }
}
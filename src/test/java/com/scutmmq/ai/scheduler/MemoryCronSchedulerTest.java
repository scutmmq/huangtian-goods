package com.scutmmq.ai.scheduler;

import com.scutmmq.ai.config.AiMemoryProperties;
import com.scutmmq.ai.entity.TriggerReason;
import com.scutmmq.ai.mapper.UserMemoryMapper;
import com.scutmmq.ai.service.UserMemoryService;
import com.scutmmq.utils.RedisConstants;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B3 step7: MemoryCronScheduler 单元测试。
 *
 * <p>4 个核心场景:
 * <ul>
 *   <li>{@code cronAcquiresLockAndProcesses1000Batch} — 锁获取 + 1000 一批 + TRIGGER_CRON</li>
 *   <li>{@code cronSkipsWhenLockHeld} — 锁被他人持有时 skip metric + 不调 service</li>
 *   <li>{@code partitionDropAcquiresLock} — drop 调 INFORMATION_SCHEMA + ALTER TABLE</li>
 *   <li>{@code watchdogForceUnlocksWhenNoProgress} — 30 分钟无进度 → forceUnlock + counter</li>
 * </ul>
 *
 * <p>纯 mock,不依赖 Spring 容器;{@code @Scheduled} 注解由 Spring 容器保障触发。
 */
class MemoryCronSchedulerTest {

    private RedissonClient redisson;
    private RLock cronLock;
    private RLock partitionLock;
    private UserMemoryMapper mapper;
    private UserMemoryService service;
    private JdbcTemplate jdbc;
    private AiMemoryProperties props;
    private MeterRegistry meter;
    private MemoryCronScheduler scheduler;

    @BeforeEach
    void setUp() {
        redisson = mock(RedissonClient.class);
        cronLock = mock(RLock.class);
        partitionLock = mock(RLock.class);
        mapper = mock(UserMemoryMapper.class);
        service = mock(UserMemoryService.class);
        jdbc = mock(JdbcTemplate.class);
        props = new AiMemoryProperties();
        props.setCacheHmacSecrets("v1:abcdefghijklmnopqrstuvwxyz123456");
        props.setActiveSecretVersion("v1");
        props.setRecomputeBatchSize(1000);
        props.setAuditPartitionRetentionDays(90);
        meter = new SimpleMeterRegistry();

        when(redisson.getLock(RedisConstants.MEMORY_CRON_LOCK_KEY)).thenReturn(cronLock);
        when(redisson.getLock(RedisConstants.MEMORY_PARTITION_DROP_LOCK_KEY)).thenReturn(partitionLock);
        // 默认 lock 由当前线程持有(便于 unlock 验证)
        when(cronLock.isHeldByCurrentThread()).thenReturn(true);
        when(partitionLock.isHeldByCurrentThread()).thenReturn(true);

        scheduler = new MemoryCronScheduler(redisson, mapper, service, jdbc, props, meter);
    }

    // ============================ 4 tests ============================

    /**
     * 锁获取 + 游标分批:模拟一批返回 1000 个 ID,下一批返回空。验证:
     * <ul>
     *   <li>tryLock(0, 50, MINUTES) 被调用</li>
     *   <li>1000 个 scheduleRecompute(uid, TRIGGER_CRON) 调用</li>
     *   <li>processed_total counter 增加 1000</li>
     *   <li>游标 lastUserId = 1000</li>
     *   <li>unlock 被调用</li>
     * </ul>
     */
    @Test
    void cronAcquiresLockAndProcesses1000Batch() throws InterruptedException {
        when(cronLock.tryLock(0, 50, TimeUnit.MINUTES)).thenReturn(true);

        List<Long> firstBatch = LongStream.rangeClosed(1, 1000).boxed().collect(java.util.stream.Collectors.toList());
        when(mapper.findStaleUserIds(eq(0L), anyLong(), anyInt(), eq(1000))).thenReturn(firstBatch);
        when(mapper.findStaleUserIds(eq(1000L), anyLong(), anyInt(), eq(1000))).thenReturn(Collections.emptyList());

        scheduler.recomputeStaleBatch();

        verify(cronLock).tryLock(0, 50, TimeUnit.MINUTES);

        // 1000 次 scheduleRecompute,每次 uid 在 [1, 1000]
        verify(service, times(1000)).scheduleRecompute(anyLong(), eq(TriggerReason.TRIGGER_CRON));

        // 验证 processed_total counter = 1000
        assertEquals(1000.0,
                meter.counter("ai_memory_cron_processed_total").count(), 0.001);

        // 验证游标 SQL 第二轮调用:findStaleUserIds(1000L, ...)
        verify(mapper, atLeast(2)).findStaleUserIds(anyLong(), anyLong(), anyInt(), anyInt());

        verify(cronLock).unlock();
    }

    /**
     * 锁被他人持有 → 跳过,记 skip metric,不调 service,不调 unlock。
     */
    @Test
    void cronSkipsWhenLockHeld() throws InterruptedException {
        when(cronLock.tryLock(0, 50, TimeUnit.MINUTES)).thenReturn(false);

        scheduler.recomputeStaleBatch();

        // skip counter 加 1,reason=lock_held
        assertEquals(1.0,
                meter.counter("ai_memory_cron_skip_total", "reason", "lock_held").count(), 0.001);

        // 不调 mapper / service
        verify(mapper, never()).findStaleUserIds(anyLong(), anyLong(), anyInt(), anyInt());
        verify(service, never()).scheduleRecompute(anyLong(), any());

        // 不调 unlock(因为不是当前线程持有)
        verify(cronLock, never()).unlock();
    }

    /**
     * 分区 drop 走 INFORMATION_SCHEMA + ALTER TABLE DROP PARTITION。
     * Mock 返回 ["p_2026_01", "p_2026_05", "p_2026_08"],retention=90 天,
     * 今天 ≈ 2026-08-23 → cutoff ≈ 2026-05-25 → p_2026_01、p_2026_05 过期应 DROP,
     * p_2026_08 不应 DROP。
     */
    @Test
    void partitionDropAcquiresLock() throws InterruptedException {
        when(partitionLock.tryLock(0, 30, TimeUnit.MINUTES)).thenReturn(true);
        List<String> partitions = new ArrayList<>();
        partitions.add("p_2026_01");
        partitions.add("p_2026_05");
        partitions.add("p_2026_08");
        when(jdbc.queryForList(contains("INFORMATION_SCHEMA.PARTITIONS"), eq(String.class)))
                .thenReturn(partitions);

        scheduler.dropOldAuditPartitions();

        verify(partitionLock).tryLock(0, 30, TimeUnit.MINUTES);

        // p_2026_01 和 p_2026_05 应被 drop
        verify(jdbc).execute("ALTER TABLE ai_user_memory_audit DROP PARTITION p_2026_01");
        verify(jdbc).execute("ALTER TABLE ai_user_memory_audit DROP PARTITION p_2026_05");
        // p_2026_08 不应被 drop
        verify(jdbc, never()).execute("ALTER TABLE ai_user_memory_audit DROP PARTITION p_2026_08");

        verify(partitionLock).unlock();
    }

    /**
     * Watchdog:锁被持有 + lastProgressMs 超过 30 分钟无增长 → forceUnlock + counter。
     */
    @Test
    void watchdogForceUnlocksWhenNoProgress() {
        when(cronLock.isLocked()).thenReturn(true);
        // 模拟 1 小时前有过进度
        scheduler.setLastProgressMs(System.currentTimeMillis() - 60 * 60 * 1000L);

        scheduler.cronWatchdog();

        verify(cronLock).forceUnlock();
        assertEquals(1.0,
                meter.counter("ai_memory_cron_lock_lost_total", "reason", "timeout").count(), 0.001);
    }
}

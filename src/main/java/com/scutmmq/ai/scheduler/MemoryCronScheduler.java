package com.scutmmq.ai.scheduler;

import com.scutmmq.ai.config.AiMemoryProperties;
import com.scutmmq.ai.entity.TriggerReason;
import com.scutmmq.ai.mapper.UserMemoryMapper;
import com.scutmmq.ai.service.UserMemoryService;
import com.scutmmq.utils.RedisConstants;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * B3 step7: 长期记忆后台周期任务。
 *
 * <p>3 个定时任务,共一个组件:
 * <ul>
 *   <li>{@link #recomputeStaleBatch} — 每天凌晨 3 点游标分批扫描陈旧用户,触发重算。
 *       Redisson raw-lock(复用 {@code PayServiceImpl} 模式),显式 {@code leaseTime=50min},
 *       无 watchdog 自动续期;waitTime=0 不等待,锁被占则 skip。</li>
 *   <li>{@link #dropOldAuditPartitions} — 每月 1 号 02:00 清理 {@code ai_user_memory_audit}
 *       分区表 RANGE 分区(> retentionDays 的 {@code p_YYYY_MM});更新
 *       {@code ai_memory_partition_cron_last_success_timestamp_seconds} 健康探针。</li>
 *   <li>{@link #cronWatchdog} — fixedDelay 10 min 检查 cron 锁"被持有但 30min 无进度"→
 *       强制 unlock + {@code ai_memory_cron_lock_lost_total{reason=timeout}} 计数(OPS-B1)。</li>
 * </ul>
 *
 * <p><b>关键决策:</b>
 * <ul>
 *   <li>不依赖 Redisson watchdog(自动续期),用显式 {@code leaseTime=50min} +
 *       独立 watchdog 任务 — 故障时 30 分钟内可观测 + 恢复,而不是无限续期阻塞调度</li>
 *   <li>游标分批按 {@code user_id} 升序 + {@code computed_at < cutoff} + NOT EXISTS reset;
 *       避免 NOT IN 性能坑 + 单批 OOM(见 spec §6.8)</li>
 *   <li>watchdog 进度跨实例共享(Redis {@code MEMORY_CRON_PROGRESS_KEY}) —
 *       Pod B 的 watchdog 能读到 Pod A 的最近进度,实现真正的跨实例僵死检测</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryCronScheduler {

    /** cron 锁最长持有时间 50 分钟(显式租约,无 Redisson 自动续期)。 */
    private static final long CRON_LOCK_LEASE_MINUTES = 50L;

    /** partition drop 锁最长持有时间 30 分钟。 */
    private static final long PARTITION_LOCK_LEASE_MINUTES = 30L;

    /** watchdog 判定"无进度"的阈值:30 分钟。 */
    private static final long WATCHDOG_STALE_MS = 30L * 60L * 1000L;

    /** 重算"陈旧"的判定:computed_at 距今超过 7 天。 */
    private static final long STALE_THRESHOLD_DAYS = 7L;

    /** Redis 进度 key TTL:60 分钟 > leaseTime 50min,保证 leaseTime 兜底之前 key 不被 Redis 自动清掉。 */
    private static final Duration CRON_PROGRESS_TTL = Duration.ofMinutes(60L);

    private final RedissonClient redisson;
    private final UserMemoryMapper mapper;
    private final UserMemoryService service;
    private final JdbcTemplate jdbc;
    private final AiMemoryProperties props;
    private final MeterRegistry meter;
    private final StringRedisTemplate stringRedis;

    // ============================ 1. 重算 cron ============================

    /**
     * 每天凌晨 3 点游标分批扫描陈旧用户,触发重算。
     * 锁被他人持有 → skip;批量内单批失败不影响后续批处理。
     */
    @Scheduled(cron = "${ai.memory.recompute-cron}")
    public void recomputeStaleBatch() {
        RLock lock = redisson.getLock(RedisConstants.MEMORY_CRON_LOCK_KEY);
        boolean locked = false;
        try {
            locked = lock.tryLock(0, CRON_LOCK_LEASE_MINUTES, TimeUnit.MINUTES);
            if (!locked) {
                meter.counter("ai_memory_cron_skip_total", "reason", "lock_held").increment();
                log.warn("[AI][MEMORY] cron lock held by another instance, skipping");
                return;
            }
            meter.counter("ai_memory_cron_run_started_total").increment();
            long startMs = System.currentTimeMillis();
            int batchSize = props.getRecomputeBatchSize();
            long lastUserId = 0L;
            int totalProcessed = 0;
            long cutoffSeconds = Instant.now().minus(STALE_THRESHOLD_DAYS, ChronoUnit.DAYS).getEpochSecond();

            while (true) {
                List<Long> ids = mapper.findStaleUserIds(lastUserId, cutoffSeconds,
                        props.getResetRetentionDays(), batchSize);
                if (ids.isEmpty()) {
                    break;
                }
                for (Long uid : ids) {
                    service.scheduleRecompute(uid, TriggerReason.TRIGGER_CRON);
                }
                lastUserId = ids.get(ids.size() - 1);
                totalProcessed += ids.size();
                meter.counter("ai_memory_cron_processed_total").increment(ids.size());
                // 跨实例共享进度:每批完成后写入 Redis,Pod B 的 watchdog 也能看到 Pod A 的进度
                writeCronProgress(System.currentTimeMillis());
            }

            long durationMs = System.currentTimeMillis() - startMs;
            meter.summary("ai_memory_cron_duration_seconds").record(durationMs / 1000.0);
            log.info("[AI][MEMORY] cron done totalProcessed={} duration={}ms",
                    totalProcessed, durationMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // ============================ 2. 分区 drop cron ============================

    /**
     * 每月 1 号 02:00 清理 ai_user_memory_audit 表的过期 RANGE 分区。
     * 步骤:
     * <ol>
     *   <li>从 INFORMATION_SCHEMA.PARTITIONS 拿所有 p_YYYY_MM 分区名</li>
     *   <li>解析成 LocalDate,过滤掉 ≤ (today - retentionDays) 的</li>
     *   <li>逐个 ALTER TABLE DROP PARTITION</li>
     *   <li>成功 drop 完成后单次更新健康探针 gauge(避免多次 gauge() 只 bind 第一个值的语义错位)</li>
     * </ol>
     * 锁被他人持有 → 静默跳过(每月一次,不告警)。
     */
    @Scheduled(cron = "${ai.memory.partition-drop-cron}")
    public void dropOldAuditPartitions() {
        RLock lock = redisson.getLock(RedisConstants.MEMORY_PARTITION_DROP_LOCK_KEY);
        boolean locked = false;
        boolean droppedAny = false;
        try {
            locked = lock.tryLock(0, PARTITION_LOCK_LEASE_MINUTES, TimeUnit.MINUTES);
            if (!locked) {
                log.warn("[AI][MEMORY] partition-drop lock held by another instance, skipping");
                return;
            }
            List<String> partitions = jdbc.queryForList(
                    "SELECT PARTITION_NAME FROM INFORMATION_SCHEMA.PARTITIONS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_user_memory_audit' "
                            + "AND PARTITION_NAME REGEXP '^p_[0-9]{4}_[0-9]{2}$'",
                    String.class);
            LocalDate cutoff = LocalDate.now().minusDays(props.getAuditPartitionRetentionDays());
            for (String p : partitions) {
                LocalDate partitionMonth = parsePartitionDate(p);
                if (partitionMonth.isBefore(cutoff)) {
                    log.info("[AI][MEMORY] dropping old partition {}", p);
                    jdbc.execute("ALTER TABLE ai_user_memory_audit DROP PARTITION " + p);
                    droppedAny = true;
                }
            }
            // for-loop 结束后单次更新 gauge,反映"最近一次成功 run 的结束时间";
            // 多次 meter.gauge() 调用 Micrometer 只 bind 第一个值,后续覆盖被静默忽略
            if (droppedAny) {
                meter.gauge("ai_memory_partition_cron_last_success_timestamp_seconds",
                        System.currentTimeMillis() / 1000.0);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // ============================ 3. Watchdog ============================

    /**
     * fixedDelay 10 min 检查 cron 锁状态:
     * <ul>
     *   <li>锁未持有 → 无事可做,return</li>
     *   <li>Redis 中无进度记录(从未跑过 cron)→ 不误判,return</li>
     *   <li>锁被持有 + 进度距今 > 30min → 强制 unlock + 计数</li>
     * </ul>
     * 处理的是"上游实例持锁后 OOM 崩溃"或"死锁"场景 — leaseTime 50min 内不会自动释放,
     * 30min 阈值保证有 20min 安全余量让下一个 cron 周期正常起跑。
     * <p>
     * 进度从 Redis 读,实现跨实例检测:Pod B 的 watchdog 能看到 Pod A 的最近进度。
     */
    @Scheduled(fixedDelay = 10L * 60L * 1000L)
    public void cronWatchdog() {
        RLock lock = redisson.getLock(RedisConstants.MEMORY_CRON_LOCK_KEY);
        if (!lock.isLocked()) {
            return;
        }
        long lastProgress = readCronProgress();
        if (lastProgress == 0L) {
            // cron 还没跑过(或集群内其他实例刚启动,历史进度已过期被 Redis 清掉)→ 不误判
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastProgress > WATCHDOG_STALE_MS) {
            try {
                lock.forceUnlock();
                meter.counter("ai_memory_cron_lock_lost_total", "reason", "timeout").increment();
                log.warn("[AI][MEMORY] cron watchdog force_unlock (no progress for {} ms)",
                        now - lastProgress);
            } catch (Exception e) {
                log.warn("[AI][MEMORY] watchdog force_unlock failed reason={}", e.getMessage());
            }
        }
    }

    // ============================ Test hook ============================

    /**
     * 仅供测试注入"上次进度时间戳",生产代码不调用。
     * watchdog 测试需要模拟"30 分钟前有过进度但现在卡死"的场景。
     * 写入 Redis key,模拟"集群内其他实例写过进度"。
     */
    void setLastProgressMs(long ts) {
        writeCronProgress(ts);
    }

    // ============================ 内部方法 ============================

    /**
     * 写入 cron 进度到 Redis(epoch ms 字符串),TTL = 60min > leaseTime 50min,
     * 保证 leaseTime 兜底之前 key 不会被 Redis 自动过期。
     */
    private void writeCronProgress(long ts) {
        try {
            stringRedis.opsForValue().set(
                    RedisConstants.MEMORY_CRON_PROGRESS_KEY,
                    String.valueOf(ts),
                    CRON_PROGRESS_TTL);
        } catch (Exception e) {
            // Redis 不可用时静默降级 — 本实例的 watchdog 仍能依靠 leaseTime 兜底
            log.warn("[AI][MEMORY] write cron progress to redis failed reason={}", e.getMessage());
        }
    }

    /**
     * 从 Redis 读 cron 进度(epoch ms)。key 缺失或 Redis 异常 → 返回 0L(语义同"未跑过")。
     */
    private long readCronProgress() {
        try {
            String val = stringRedis.opsForValue().get(RedisConstants.MEMORY_CRON_PROGRESS_KEY);
            return val == null ? 0L : Long.parseLong(val);
        } catch (Exception e) {
            log.warn("[AI][MEMORY] read cron progress from redis failed reason={}", e.getMessage());
            return 0L;
        }
    }

    /**
     * 解析分区名 {@code p_YYYY_MM} 为 LocalDate(月份第一天)。
     * 格式错误的分区名(理论上不应出现)会被跳过(parse 抛 NumberFormatException 被 catch)。
     */
    private static LocalDate parsePartitionDate(String partitionName) {
        // p_2026_05 → ["p", "2026", "05"]
        String[] parts = partitionName.split("_");
        if (parts.length != 3) {
            throw new IllegalArgumentException("invalid partition name: " + partitionName);
        }
        int year = Integer.parseInt(parts[1]);
        int month = Integer.parseInt(parts[2]);
        return LocalDate.of(year, month, 1);
    }
}
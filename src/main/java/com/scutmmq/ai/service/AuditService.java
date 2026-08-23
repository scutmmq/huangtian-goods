package com.scutmmq.ai.service;

import com.scutmmq.ai.config.AiMemoryProperties;
import com.scutmmq.ai.entity.UserMemoryAuditEntity;
import com.scutmmq.ai.mapper.UserMemoryAuditMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * B3 step9: 用户记忆审计服务。
 *
 * <p>两类写路径:
 * <ol>
 *   <li><b>同步 log</b>(主流程调用):{@link #logCompute} 等 6 个方法,失败仅 log 不抛 —
 *       防止审计副作用阻塞 AI 主链路。</li>
 *   <li><b>异步 purge</b>(GDPR Art 17 触发):{@link #purgeAuditAsync},
 *       限速 100 rows/s(可配),失败 3 次入 {@code ai_user_memory_audit_purge_dlq} DLQ。</li>
 * </ol>
 *
 * <p>为什么用 JdbcTemplate 直接 UPDATE 而不写 mapper:
 * 清表 SQL 带 {@code LIMIT 1000},MyBatis 不支持 LIMIT 占位符语法,
 * 用字符串拼接是 spec 认可的做法(见 §5.1 brief)。
 */
@Slf4j
@Service
public class AuditService {

    /** 每次 UPDATE 的批大小(行数),与 limiter.rate 配合决定批间隔。 */
    private static final int PURGE_BATCH_SIZE = 1000;

    /** purge 失败最大重试次数,达到后入 DLQ 表。 */
    private static final int PURGE_MAX_RETRY = 3;

    private final JdbcTemplate jdbc;
    private final UserMemoryAuditMapper auditMapper;
    private final AiMemoryProperties props;
    private final MeterRegistry meter;

    /** B3 step10: audit 写失败计数 — {@code ai_memory_audit_write_failure_total} */
    private final Counter auditWriteFailureCounter;
    /** B3 step10: 异步清理行数计数 — {@code ai_memory_audit_purged_total} */
    private final Counter auditPurgedCounter;
    /** B3 step10: DLQ 入表计数 — {@code ai_memory_audit_purge_dlq_total} */
    private final Counter auditPurgeDlqCounter;

    /** Rate gate 状态:上一次 batch 的 nanoTime,0 表示尚未初始化(首次调用立即放行并设为 now)。 */
    private long lastBatchNanos = 0L;
    private final Object rateLock = new Object();

    public AuditService(JdbcTemplate jdbc, UserMemoryAuditMapper auditMapper,
                        AiMemoryProperties props, MeterRegistry meter) {
        this.jdbc = jdbc;
        this.auditMapper = auditMapper;
        this.props = props;
        this.meter = meter;
        this.auditWriteFailureCounter = Counter.builder("ai_memory_audit_write_failure_total")
                .description("audit log 写入失败次数")
                .register(meter);
        this.auditPurgedCounter = Counter.builder("ai_memory_audit_purged_total")
                .description("异步 purge 清理行数累计")
                .register(meter);
        this.auditPurgeDlqCounter = Counter.builder("ai_memory_audit_purge_dlq_total")
                .description("purge 失败入 DLQ 表次数")
                .register(meter);
    }

    // ============================ 同步 log(主流程)============================

    public void logCompute(Long userId, String reason) {
        insertSafe(userId, "COMPUTE", null, reason, null, null, null, null, null);
    }

    public void logReset(Long userId, String actorIp, String requestId) {
        insertSafe(userId, "RESET", null, null, null, null, actorIp, requestId, null);
    }

    public void logRecomputeFail(Long userId, Exception e) {
        String msg = e == null ? null : truncate(e.getClass().getSimpleName() + ": " + e.getMessage(), 1024);
        insertSafe(userId, "RECOMPUTE_FAIL", null, null, null, null, null, null, msg);
    }

    public void logJsonOverflow(Long userId, String field) {
        insertSafe(userId, "JSON_OVERFLOW", null, null, null, field, null, null, null);
    }

    /**
     * 降级路径:mode 是 DEGRADED_RATE_LIMITED / DEGRADED_NO_DEBOUNCE /
     * DEGRADED_RATE_LIMITED_DROP 三选一,与 audit 表 chk_action 约束对齐。
     */
    public void logDegraded(Long userId, String mode) {
        insertSafe(userId, mode, null, null, null, null, null, null, null);
    }

    public void logPromptInjectionDrop(Long userId, String input) {
        insertSafe(userId, "PROMPT_INJECTION_DROP", null, null, null, truncate(input, 64), null, null, null);
    }

    // ============================ 异步 purge ============================

    /**
     * 伪匿名化清理:把某 user 在 audit 表的 COMPUTE / OVERFLOW_DROP 行
     * 的 user_id 置 0(联合 0=匿名账户),fields_changed 清空。
     * 限速 100 rows/s 防 IO 风暴;失败 3 次入 DLQ。
     *
     * <p>@Async 走 memoryAsyncExecutor(独立小池,与 aiTaskExecutor 解耦,
     * 防止主 Run 队列被审计清理任务挤占)。需要 {@code @EnableAsync} 才生效。
     */
    @Async("memoryAsyncExecutor")
    public void purgeAuditAsync(Long userId) {
        int retry = 0;
        while (retry < PURGE_MAX_RETRY) {
            try {
                Integer total = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ai_user_memory_audit "
                                + "WHERE user_id=? AND action IN ('COMPUTE','OVERFLOW_DROP')",
                        Integer.class, userId);
                if (total == null || total == 0) {
                    return;
                }
                for (int offset = 0; offset < total; offset += PURGE_BATCH_SIZE) {
                    rateGate(PURGE_BATCH_SIZE);
                    jdbc.update(
                            "UPDATE ai_user_memory_audit "
                                    + "SET user_id = 0, fields_changed = NULL "
                                    + "WHERE user_id=? AND action IN ('COMPUTE','OVERFLOW_DROP') "
                                    + "LIMIT " + PURGE_BATCH_SIZE,
                            userId);
                }
                // B3 step10: 成功路径累计 purge 行数
                auditPurgedCounter.increment(total);
                return;
            } catch (Exception e) {
                retry++;
                log.warn("[AI][AUDIT] purge userId={} retry={} failed: {}", userId, retry, e.getMessage());
                if (retry >= PURGE_MAX_RETRY) {
                    writeDlq(userId, e.getMessage(), retry);
                }
            }
        }
    }

    // ============================ 内部方法 ============================

    private void insertSafe(Long userId, String action, String fieldsChanged,
                            String triggeredBy, Integer tokenEstimate, String fieldDropped,
                            String actorIp, String requestId, String errorMessage) {
        try {
            UserMemoryAuditEntity e = new UserMemoryAuditEntity();
            e.setUserId(userId);
            e.setAction(action);
            e.setFieldsChanged(fieldsChanged);
            e.setTriggeredBy(triggeredBy);
            e.setTokenEstimate(tokenEstimate);
            e.setFieldDropped(fieldDropped);
            e.setActorIp(actorIp);
            e.setRequestId(requestId);
            e.setErrorMessage(errorMessage);
            auditMapper.insert(e);
        } catch (Exception ex) {
            // 审计写入失败仅 log,不阻塞 AI 主链路
            auditWriteFailureCounter.increment();
            log.warn("[AI][AUDIT] insert failed userId={} action={} reason={}",
                    userId, action, ex.getMessage());
        }
    }

    private void writeDlq(Long userId, String errorMsg, int retryCount) {
        try {
            jdbc.update(
                    "INSERT INTO ai_user_memory_audit_purge_dlq (user_id, error_msg, retry_count) "
                            + "VALUES (?, ?, ?)",
                    userId, truncate(errorMsg, 4000), retryCount);
            // B3 step10: DLQ 入表计数(成功路径才计)
            auditPurgeDlqCounter.increment();
        } catch (Exception e) {
            log.error("[AI][AUDIT] DLQ insert failed userId={} reason={}", userId, e.getMessage());
        }
    }

    /**
     * 限速门:每次 batch 前 sleep 至少 batchSize/rate 秒。
     * 首次调用 lastBatchNanos=0,等待为 0 → 立即放行(对应 spec §5.1 "首批不限速")。
     */
    private void rateGate(int permits) {
        long targetNanos = (long) ((double) permits * 1_000_000_000L
                / props.getAuditPurgeRateRowsPerSec());
        synchronized (rateLock) {
            if (lastBatchNanos == 0L) {
                lastBatchNanos = System.nanoTime();
                return;
            }
            long now = System.nanoTime();
            long wait = lastBatchNanos + targetNanos - now;
            if (wait > 0) {
                try {
                    long ms = wait / 1_000_000L;
                    int ns = (int) (wait % 1_000_000L);
                    Thread.sleep(ms, ns);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            lastBatchNanos = System.nanoTime();
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
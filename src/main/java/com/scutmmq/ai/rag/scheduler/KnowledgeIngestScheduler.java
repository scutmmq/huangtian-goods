package com.scutmmq.ai.rag.scheduler;

import com.scutmmq.ai.config.AiRagProperties;
import com.scutmmq.ai.rag.ingest.KnowledgeIngestService;
import com.scutmmq.utils.RedisConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 知识库全量同步周期定时任务。
 *
 * <p><b>分布式调度设计要点：</b></p>
 * <ul>
 *   <li><b>Redisson 分布式锁防重复执行</b>：在集群多实例（多 Pod）部署下，通过 {@code RAG_INGEST_CRON_LOCK_KEY}
 *       保证每日凌晨仅有一台节点执行全量知识库构建；</li>
 *   <li><b>非阻塞跳过（Non-blocking Skip）</b>：{@code tryLock(0, 30, MINUTES)} 设置等待时间为 0，
 *       若锁已被其他节点持有则立即退出，防止任务堆积；</li>
 *   <li><b>开关保护</b>：仅在 {@code ai.rag.enabled=true} 时执行，未启用时不消耗任何计算资源。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeIngestScheduler {

    private final RedissonClient redisson;
    private final KnowledgeIngestService ingestService;
    private final AiRagProperties props;

    /**
     * 每日定时全量重构知识库与规则切片（默认每日凌晨 04:00）。
     */
    @Scheduled(cron = "${ai.rag.ingest-cron:0 0 4 * * ?}")
    public void scheduleFullIngestion() {
        if (!props.isEnabled()) {
            log.debug("[AI][RAG] RAG capability is disabled, skipping scheduled knowledge ingestion");
            return;
        }

        RLock lock = redisson.getLock(RedisConstants.RAG_INGEST_CRON_LOCK_KEY);
        boolean locked = false;
        try {
            // waitTime=0: 锁被占则不等待立即跳过；leaseTime=-1: 启用 Redisson Watchdog 自动看门狗续期，防止长任务中途锁过期
            locked = lock.tryLock(0, -1, TimeUnit.SECONDS);
            if (!locked) {
                log.info("[AI][RAG] Ingest cron lock is held by another instance, skipping this trigger");
                return;
            }

            log.info("[AI][RAG] Acquired cron lock, starting scheduled full knowledge ingestion...");
            int total = ingestService.ingestAll();
            log.info("[AI][RAG] Scheduled full knowledge ingestion finished successfully, total indexed: {}", total);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[AI][RAG] Ingest cron interrupted: {}", e.getMessage());
        } catch (Exception e) {
            log.error("[AI][RAG] Scheduled knowledge ingestion failed: {}", e.getMessage(), e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}

package com.scutmmq.ai.observability;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scutmmq.ai.config.AiRagProperties;
import com.scutmmq.ai.entity.KnowledgeChunkEntity;
import com.scutmmq.ai.mapper.KnowledgeChunkMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * RAG 知识库冷启动就绪探针（K8s Readiness Probe 集成）。
 *
 * <p><b>为什么需要冷启动探针？</b></p>
 * <p>
 * 当运维在生产环境开启 {@code ai.rag.enabled=true} 时，若数据库 {@code ai_knowledge_chunk} 表尚未执行全量构建（切片数为 0），
 * 用户所有的知识问答都会直接命中未召回分支，导致体验受损。
 * 通过本 HealthIndicator：
 * <ul>
 *   <li>在 RAG 启用且知识库切片数 == 0 时，报告 {@code OUT_OF_SERVICE}，使 K8s Readiness 探针失败，阻止生产流量打入空库实例；</li>
 *   <li>在知识库切片构建就绪后（count > 0），探针自动恢复 {@code UP} 并放行流量。</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class RagReadinessIndicator implements HealthIndicator {

    private final AiRagProperties props;
    private final KnowledgeChunkMapper mapper;

    public RagReadinessIndicator(AiRagProperties props, KnowledgeChunkMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    @Override
    public Health health() {
        if (!props.isEnabled()) {
            return Health.up()
                    .withDetail("ragEnabled", false)
                    .withDetail("status", "DISABLED")
                    .build();
        }

        try {
            Long activeCount = mapper.selectCount(
                    new LambdaQueryWrapper<KnowledgeChunkEntity>().eq(KnowledgeChunkEntity::getStatus, 1)
            );

            if (activeCount == null || activeCount == 0L) {
                log.warn("[AI][RAG] Cold start check: RAG is enabled but active knowledge chunk count is 0!");
                return Health.outOfService()
                        .withDetail("ragEnabled", true)
                        .withDetail("activeChunks", 0L)
                        .withDetail("message", "Knowledge base is empty. Please run ingestion before routing traffic.")
                        .build();
            }

            return Health.up()
                    .withDetail("ragEnabled", true)
                    .withDetail("activeChunks", activeCount)
                    .build();
        } catch (Exception e) {
            log.error("[AI][RAG] Readiness probe check failed: {}", e.getMessage());
            return Health.down(e)
                    .withDetail("ragEnabled", true)
                    .build();
        }
    }
}

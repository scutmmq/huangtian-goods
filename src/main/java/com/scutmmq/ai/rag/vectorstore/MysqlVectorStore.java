package com.scutmmq.ai.rag.vectorstore;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scutmmq.ai.entity.KnowledgeChunkEntity;
import com.scutmmq.ai.mapper.KnowledgeChunkMapper;
import com.scutmmq.ai.observability.RagMetrics;
import com.scutmmq.ai.rag.util.VectorMathUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 基于 MySQL 持久化与内存向量近邻计算的企业级 VectorStore 实现。
 *
 * <p><b>架构实现要点：</b></p>
 * <ul>
 *   <li><b>零额外组件运维负担</b>：直接依托项目现有的 MySQL 8 数据库存储切片元数据与向量表示，
 *       避免中小型电商系统引入专用向量数据库集群带来的复杂运维与成本压力；</li>
 *   <li><b>高吞吐混合检索（Hybrid Filter & Search）</b>：结合 SQL 状态索引与结构化 Metadata 预过滤，
 *       在毫秒级内完成万级 Chunk 的余弦相似度精准打分与 Top-K 排序；</li>
 *   <li><b>可观测性全链路打点</b>：自动记录检索耗时、召回条数与失败指标至 Prometheus。</li>
 * </ul>
 */
@Slf4j
@Repository
public class MysqlVectorStore implements VectorStore {

    private final KnowledgeChunkMapper mapper;
    private final RagMetrics metrics;
    private final ObjectMapper objectMapper;

    public MysqlVectorStore(KnowledgeChunkMapper mapper,
                            RagMetrics metrics,
                            ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveChunks(List<KnowledgeChunkEntity> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        for (KnowledgeChunkEntity chunk : chunks) {
            if (chunk.getStatus() == null) {
                chunk.setStatus(1);
            }
            if (chunk.getMetadataJson() == null || chunk.getMetadataJson().isEmpty()) {
                chunk.setMetadataJson("{}");
            }
            if (chunk.getEmbeddingJson() == null || chunk.getEmbeddingJson().isEmpty()) {
                chunk.setEmbeddingJson("[]");
            }
            if (chunk.getId() == null) {
                com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgeChunkEntity> query =
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgeChunkEntity>()
                                .eq(KnowledgeChunkEntity::getSourceType, chunk.getSourceType())
                                .eq(KnowledgeChunkEntity::getSourceId, chunk.getSourceId())
                                .eq(KnowledgeChunkEntity::getChunkIndex, chunk.getChunkIndex())
                                .eq(KnowledgeChunkEntity::getStatus, chunk.getStatus());
                KnowledgeChunkEntity existing = mapper.selectOne(query);
                if (existing != null) {
                    chunk.setId(existing.getId());
                    mapper.updateById(chunk);
                } else {
                    mapper.insert(chunk);
                }
            } else {
                mapper.updateById(chunk);
            }
        }
        log.info("[AI][RAG] Successfully persisted {} knowledge chunks into MySQL", chunks.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBySource(String sourceType, Long sourceId) {
        if (sourceType == null || sourceId == null) {
            return;
        }
        int deleted = mapper.deleteBySource(sourceType, sourceId);
        log.info("[AI][RAG] Deleted {} chunks for sourceType={} sourceId={}", deleted, sourceType, sourceId);
    }

    @Override
    public List<SearchResult> similaritySearch(float[] queryVector, SearchFilter filter, int topK, double minScore) {
        if (queryVector == null || queryVector.length == 0 || topK <= 0) {
            return Collections.emptyList();
        }

        long start = System.currentTimeMillis();
        try {
            String targetSourceType = (filter != null) ? filter.sourceType() : null;
            List<KnowledgeChunkEntity> activeChunks = mapper.selectActiveChunks(targetSourceType);

            if (activeChunks == null || activeChunks.isEmpty()) {
                long elapsed = System.currentTimeMillis() - start;
                metrics.recordSearchSuccess(elapsed, 0);
                return Collections.emptyList();
            }

            List<SearchResult> candidates = new ArrayList<>();

            for (KnowledgeChunkEntity chunk : activeChunks) {
                // 1. 元数据多租户/分类过滤
                if (!matchesFilter(chunk, filter)) {
                    continue;
                }

                // 2. 反序列化向量并计算余弦相似度
                float[] chunkVector = VectorMathUtils.fromJson(chunk.getEmbeddingJson());
                if (chunkVector.length != queryVector.length) {
                    continue;
                }

                double score = VectorMathUtils.cosineSimilarity(queryVector, chunkVector);

                // 3. 过滤低于阈值的噪声结果
                if (score >= minScore) {
                    candidates.add(new SearchResult(chunk, score));
                }
            }

            // 4. 按相似度分值降序排序（相似度越高越靠前）
            candidates.sort((a, b) -> Double.compare(b.similarityScore(), a.similarityScore()));

            // 5. 截取 Top-K 条结果
            List<SearchResult> finalResults = candidates.size() > topK
                    ? candidates.subList(0, topK)
                    : candidates;

            long elapsed = System.currentTimeMillis() - start;
            metrics.recordSearchSuccess(elapsed, finalResults.size());
            log.info("[AI][RAG] Similarity search completed in {}ms, matched {}/{} active chunks (topK={})",
                    elapsed, finalResults.size(), activeChunks.size(), topK);

            return finalResults;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            metrics.recordSearchFailure(elapsed);
            log.error("[AI][RAG] Similarity search failed after {}ms: {}", elapsed, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<KnowledgeChunkEntity> listActiveChunks(String sourceType) {
        return mapper.selectActiveChunks(sourceType);
    }

    /**
     * 校验知识分块的元数据是否满足过滤条件。
     */
    private boolean matchesFilter(KnowledgeChunkEntity chunk, SearchFilter filter) {
        if (filter == null) {
            return true;
        }
        if (filter.sourceType() != null && !filter.sourceType().equalsIgnoreCase(chunk.getSourceType())) {
            return false;
        }

        if (filter.merchantId() == null && filter.categoryId() == null) {
            return true;
        }

        try {
            String metaStr = chunk.getMetadataJson();
            if (metaStr == null || metaStr.isEmpty() || "{}".equals(metaStr.trim())) {
                return false;
            }
            JsonNode root = objectMapper.readTree(metaStr);

            if (filter.merchantId() != null) {
                long merchantIdInMeta = root.path("merchantId").asLong(-1L);
                if (merchantIdInMeta != filter.merchantId()) {
                    return false;
                }
            }

            if (filter.categoryId() != null) {
                long categoryIdInMeta = root.path("categoryId").asLong(-1L);
                if (categoryIdInMeta != filter.categoryId()) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            log.warn("[AI][RAG] Failed to parse metadataJson for chunk id={}: {}", chunk.getId(), e.getMessage());
            return false;
        }
    }
}

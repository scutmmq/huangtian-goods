package com.scutmmq.ai.rag.vectorstore;

import com.scutmmq.ai.entity.KnowledgeChunkEntity;

import java.util.List;

/**
 * 向量存储（Vector Store）统一存储与检索抽象接口。
 *
 * <p><b>接口职责：</b></p>
 * <ul>
 *   <li><b>向量与分块持久化</b>：保存切片文本、元数据与其对应的嵌入向量；</li>
 *   <li><b>增量维护与幂等更新</b>：支持按来源（如特定商品或店铺）快速删除或刷新旧切片；</li>
 *   <li><b>Top-K 相似度召回</b>：结合元数据过滤与余弦相似度计算，返回最匹配的知识切片。</li>
 * </ul>
 */
public interface VectorStore {

    /**
     * 批量持久化保存知识切片实体。
     *
     * @param chunks 知识切片实体列表
     */
    void saveChunks(List<KnowledgeChunkEntity> chunks);

    /**
     * 根据知识来源类型与来源 ID 删除分块（用于增量更新或下架删除）。
     *
     * @param sourceType 知识源类型（PRODUCT, MERCHANT, RULE, FAQ）
     * @param sourceId   知识源实体 ID
     */
    void deleteBySource(String sourceType, Long sourceId);

    /**
     * 基于向量余弦相似度执行近邻搜索，并应用元数据过滤与相似度阈值截断。
     *
     * @param queryVector 查询文本对应的嵌入向量
     * @param filter      元数据过滤条件（支持多租户与类型限制）
     * @param topK        最大召回条数
     * @param minScore    最低相似度阈值
     * @return 按相似度降序排列的检索结果列表
     */
    List<SearchResult> similaritySearch(float[] queryVector, SearchFilter filter, int topK, double minScore);

    /**
     * 获取所有启用的切片列表。
     *
     * @param sourceType 知识源类型（为 null 时查询全量）
     * @return 切片实体列表
     */
    List<KnowledgeChunkEntity> listActiveChunks(String sourceType);
}

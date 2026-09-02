package com.scutmmq.ai.rag.vectorstore;

import com.scutmmq.ai.entity.KnowledgeChunkEntity;

/**
 * 向量相似度检索单条结果对象。
 *
 * @param chunk           检索命中的知识库切片实体
 * @param similarityScore 归一化后的余弦相似度分值，范围 [-1.0, 1.0]，越接近 1.0 表示语义越匹配
 */
public record SearchResult(
        KnowledgeChunkEntity chunk,
        double similarityScore
) {
}

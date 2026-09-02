package com.scutmmq.ai.rag.embedding;

import java.util.List;

/**
 * 文本向量化嵌入（Embedding）统一服务接口。
 *
 * <p><b>什么是文本嵌入（Embedding）？</b></p>
 * <p>
 * Embedding 是将任意长度的自然语言文本映射为一个固定长度的稠密浮点数向量（如 1024 维 float[]）的过程。
 * 向量中的每一个维度代表了文本在深层语义特征空间中的坐标。
 * 语义越相似的文本，在向量空间中的距离越近（余弦相似度越接近 1.0）。
 * </p>
 */
public interface EmbeddingService {

    /**
     * 将单条用户查询文本（Query）转换为向量表示。
     *
     * @param query 用户输入的查询问题或搜索短语
     * @return 稠密浮点数向量
     */
    float[] embedQuery(String query);

    /**
     * 批量将多个知识文档切片（Documents）转换为向量表示。
     * 用于知识库离线或增量构建。
     *
     * @param documents 待嵌入的文档文本列表
     * @return 对应的向量列表，顺序与输入列表严格一一对应
     */
    List<float[]> embedDocuments(List<String> documents);

    /**
     * 获取当前向量模型的特征维度大小（如 1024）。
     *
     * @return 向量维度
     */
    int dimension();
}

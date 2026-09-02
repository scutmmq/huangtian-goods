package com.scutmmq.ai.rag.ingest;

import com.scutmmq.ai.entity.KnowledgeChunkEntity;
import com.scutmmq.ai.rag.embedding.EmbeddingService;
import com.scutmmq.ai.rag.util.VectorMathUtils;
import com.scutmmq.ai.rag.vectorstore.VectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 知识库切片批处理事务服务（独立 Spring Bean，确保 @Transactional AOP 代理生效）。
 *
 * <p><b>为什么需要独立的 TxService？</b></p>
 * <p>
 * 在 Spring 体系中，同类内部方法调用（Self-Invocation，如 {@code this.processBatch()}）
 * 会绕过 Spring 动态代理，导致方法上的 {@code @Transactional} 注解失效。
 * 通过将批处理操作抽取到独立的 Spring Service 组件中，保证每个 Batch 的切片写入都运行在独立的事务边界内，
 * 即使单批次发生异常也不会导致全局长事务回滚。
 * </p>
 */
@Slf4j
@Service
public class KnowledgeIngestTxService {

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;

    public KnowledgeIngestTxService(EmbeddingService embeddingService, VectorStore vectorStore) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
    }

    /**
     * 单批次切片的向量计算与事务性持久化写入。
     *
     * @param batch 待处理的切片批次
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void processBatch(List<KnowledgeChunkEntity> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }

        List<String> contents = batch.stream().map(KnowledgeChunkEntity::getContent).toList();
        List<float[]> embeddings = embeddingService.embedDocuments(contents);

        for (int i = 0; i < batch.size(); i++) {
            float[] vec = embeddings.get(i);
            batch.get(i).setEmbeddingJson(VectorMathUtils.toJson(vec));
        }

        vectorStore.saveChunks(batch);
    }
}

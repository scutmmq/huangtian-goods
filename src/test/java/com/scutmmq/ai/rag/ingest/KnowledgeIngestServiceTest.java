package com.scutmmq.ai.rag.ingest;

import com.scutmmq.ai.entity.KnowledgeChunkEntity;
import com.scutmmq.ai.rag.embedding.EmbeddingService;
import com.scutmmq.ai.rag.vectorstore.VectorStore;
import com.scutmmq.entity.Product;
import com.scutmmq.mapper.CategoryMapper;
import com.scutmmq.mapper.MerchantMapper;
import com.scutmmq.mapper.ProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识库构建流水线 KnowledgeIngestService 单元测试。
 */
class KnowledgeIngestServiceTest {

    private ProductMapper productMapper;
    private CategoryMapper categoryMapper;
    private MerchantMapper merchantMapper;
    private KnowledgeChunker chunker;
    private EmbeddingService embeddingService;
    private VectorStore vectorStore;
    private KnowledgeIngestTxService txService;
    private KnowledgeIngestService ingestService;

    @BeforeEach
    void setUp() {
        productMapper = Mockito.mock(ProductMapper.class);
        categoryMapper = Mockito.mock(CategoryMapper.class);
        merchantMapper = Mockito.mock(MerchantMapper.class);
        chunker = Mockito.mock(KnowledgeChunker.class);
        embeddingService = Mockito.mock(EmbeddingService.class);
        vectorStore = Mockito.mock(VectorStore.class);
        txService = Mockito.mock(KnowledgeIngestTxService.class);

        ingestService = new KnowledgeIngestService(
                productMapper, categoryMapper, merchantMapper,
                chunker, embeddingService, vectorStore, txService
        );
    }

    @Test
    @DisplayName("全量构建应分批委托独立 TxService 事务写入，且单批异常不阻断全局")
    void ingestAllDelegatesToTxServiceInBatches() {
        when(chunker.chunkMallRules()).thenReturn(List.of(
                KnowledgeChunkEntity.builder().id(1L).title("规则1").content("内容1").build(),
                KnowledgeChunkEntity.builder().id(2L).title("规则2").content("内容2").build()
        ));
        when(categoryMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(merchantMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(productMapper.selectList(any())).thenReturn(Collections.emptyList());

        int total = ingestService.ingestAll();

        assertEquals(2, total);
        verify(txService, times(1)).processBatch(any());
    }

    @Test
    @DisplayName("增量下架商品时应触发向量库删除")
    void ingestInactiveProductTriggersDeletion() {
        Product inactiveProduct = new Product();
        inactiveProduct.setId(99L);
        inactiveProduct.setIsActive(0);

        when(productMapper.selectById(99L)).thenReturn(inactiveProduct);

        ingestService.ingestProduct(99L);

        verify(vectorStore, times(1)).deleteBySource(eq("PRODUCT"), eq(99L));
    }
}

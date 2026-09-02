package com.scutmmq.ai.rag.vectorstore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scutmmq.ai.entity.KnowledgeChunkEntity;
import com.scutmmq.ai.mapper.KnowledgeChunkMapper;
import com.scutmmq.ai.observability.RagMetrics;
import com.scutmmq.ai.rag.util.VectorMathUtils;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * MysqlVectorStore 向量检索与元数据过滤单元测试。
 */
class MysqlVectorStoreTest {

    private KnowledgeChunkMapper mapper;
    private MysqlVectorStore vectorStore;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mapper = Mockito.mock(KnowledgeChunkMapper.class);
        RagMetrics metrics = new RagMetrics(new SimpleMeterRegistry());
        objectMapper = new ObjectMapper();
        vectorStore = new MysqlVectorStore(mapper, metrics, objectMapper);
    }

    @Test
    @DisplayName("向量相似度检索应按余弦相似度降序返回 Top-K 结果")
    void similaritySearchRanksByScoreDescending() {
        float[] queryVec = new float[]{1.0f, 0.0f, 0.0f};

        // Chunk 1: 完全同向 (sim = 1.0)
        KnowledgeChunkEntity chunk1 = KnowledgeChunkEntity.builder()
                .id(1L)
                .sourceType("RULE")
                .sourceId(0L)
                .title("7天无理由退货")
                .content("支持7天无理由退货")
                .metadataJson("{}")
                .embeddingJson(VectorMathUtils.toJson(new float[]{1.0f, 0.0f, 0.0f}))
                .status(1)
                .build();

        // Chunk 2: 部分同向 (sim ~ 0.707)
        KnowledgeChunkEntity chunk2 = KnowledgeChunkEntity.builder()
                .id(2L)
                .sourceType("RULE")
                .sourceId(0L)
                .title("售后维修政策")
                .content("提供一年免费质保")
                .metadataJson("{}")
                .embeddingJson(VectorMathUtils.toJson(new float[]{1.0f, 1.0f, 0.0f}))
                .status(1)
                .build();

        // Chunk 3: 正交无关 (sim = 0.0)
        KnowledgeChunkEntity chunk3 = KnowledgeChunkEntity.builder()
                .id(3L)
                .sourceType("PRODUCT")
                .sourceId(10L)
                .title("山地自行车")
                .content("21速变速山地车")
                .metadataJson("{}")
                .embeddingJson(VectorMathUtils.toJson(new float[]{0.0f, 1.0f, 0.0f}))
                .status(1)
                .build();

        when(mapper.selectActiveChunks(any())).thenReturn(List.of(chunk1, chunk2, chunk3));

        List<SearchResult> results = vectorStore.similaritySearch(queryVec, SearchFilter.all(), 2, 0.5);

        assertEquals(2, results.size());
        assertEquals(1L, results.get(0).chunk().getId());
        assertEquals(1.0, results.get(0).similarityScore(), 1e-5);

        assertEquals(2L, results.get(1).chunk().getId());
        assertTrue(results.get(1).similarityScore() > 0.7);
    }

    @Test
    @DisplayName("元数据多租户过滤应精准隔离非目标商家的切片")
    void metadataFilterIsolatesMerchantData() {
        float[] queryVec = new float[]{1.0f, 0.0f, 0.0f};

        // 店铺 1 的政策
        KnowledgeChunkEntity chunkMerchant1 = KnowledgeChunkEntity.builder()
                .id(1L)
                .sourceType("MERCHANT")
                .sourceId(100L)
                .title("小米专卖店包邮政策")
                .content("满99包邮")
                .metadataJson("{\"merchantId\": 100}")
                .embeddingJson(VectorMathUtils.toJson(new float[]{1.0f, 0.0f, 0.0f}))
                .status(1)
                .build();

        // 店铺 2 的政策（向量同样匹配，但 merchantId 不符）
        KnowledgeChunkEntity chunkMerchant2 = KnowledgeChunkEntity.builder()
                .id(2L)
                .sourceType("MERCHANT")
                .sourceId(200L)
                .title("华为专卖店包邮政策")
                .content("满199包邮")
                .metadataJson("{\"merchantId\": 200}")
                .embeddingJson(VectorMathUtils.toJson(new float[]{1.0f, 0.0f, 0.0f}))
                .status(1)
                .build();

        when(mapper.selectActiveChunks(any())).thenReturn(List.of(chunkMerchant1, chunkMerchant2));

        // 仅过滤 merchantId = 100
        SearchFilter filter = SearchFilter.ofMerchant(100L);
        List<SearchResult> results = vectorStore.similaritySearch(queryVec, filter, 5, 0.5);

        assertEquals(1, results.size());
        assertEquals(100L, results.get(0).chunk().getSourceId());
    }
}

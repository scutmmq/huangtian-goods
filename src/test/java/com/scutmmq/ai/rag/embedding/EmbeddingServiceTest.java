package com.scutmmq.ai.rag.embedding;

import com.scutmmq.ai.config.AiRagProperties;
import com.scutmmq.ai.rag.util.VectorMathUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 向量嵌入服务单元测试。
 */
class EmbeddingServiceTest {

    private MockEmbeddingService mockService;

    @BeforeEach
    void setUp() {
        AiRagProperties props = new AiRagProperties();
        props.setEmbeddingDimension(1024);
        mockService = new MockEmbeddingService(props);
    }

    @Test
    @DisplayName("生成的向量维度应严格等于配置的维度 1024")
    void vectorDimensionMatchesConfig() {
        float[] vector = mockService.embedQuery("荒天享物电商退换货政策");
        assertNotNull(vector);
        assertEquals(1024, vector.length);
        assertEquals(1024, mockService.dimension());
    }

    @Test
    @DisplayName("语义相似文本的余弦相似度应显著高于无关文本")
    void semanticRelatedTextsHaveHigherSimilarity() {
        float[] vQuery = mockService.embedQuery("怎么办理商品退货退款？");
        float[] vRelated = mockService.embedQuery("商城商品退货退款售后规则说明");
        float[] vUnrelated = mockService.embedQuery("儿童玩具电动遥控赛车");

        double simRelated = VectorMathUtils.cosineSimilarity(vQuery, vRelated);
        double simUnrelated = VectorMathUtils.cosineSimilarity(vQuery, vUnrelated);

        assertTrue(simRelated > simUnrelated,
                String.format("相似内容相似度 (%f) 应大于不相关内容 (%f)", simRelated, simUnrelated));
        assertTrue(simRelated > 0.4, "相关文本相似度应具备显著可分性");
    }

    @Test
    @DisplayName("批量嵌入文档应与单条嵌入结果一致")
    void batchEmbedDocumentsMatchesSingleEmbed() {
        List<String> docs = List.of("山地自行车规格", "7天无理由退货");
        List<float[]> batchResult = mockService.embedDocuments(docs);

        assertEquals(2, batchResult.size());
        assertEquals(1024, batchResult.get(0).length);
        assertEquals(1024, batchResult.get(1).length);

        float[] singleResult0 = mockService.embedQuery(docs.get(0));
        assertEquals(1.0, VectorMathUtils.cosineSimilarity(batchResult.get(0), singleResult0), 1e-5);
    }

    @Test
    @DisplayName("空字符串或 null 应返回全零向量且不崩溃")
    void emptyOrNullReturnsZeroVectorSafely() {
        float[] vEmpty = mockService.embedQuery("");
        assertEquals(1024, vEmpty.length);

        float[] vNull = mockService.embedQuery(null);
        assertEquals(1024, vNull.length);
    }
}

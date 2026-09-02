package com.scutmmq.ai.rag.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scutmmq.ai.entity.KnowledgeChunkEntity;
import com.scutmmq.entity.Merchant;
import com.scutmmq.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 知识切片构建器单元测试。
 */
class KnowledgeChunkerTest {

    private KnowledgeChunker chunker;

    @BeforeEach
    void setUp() {
        chunker = new KnowledgeChunker(new ObjectMapper());
    }

    @Test
    @DisplayName("商品切片应正确组装标题、内容与结构化元数据")
    void chunkProductGeneratesExpectedStructure() {
        Product product = new Product();
        product.setId(101L);
        product.setName("2026款专业山地自行车");
        product.setDescription("铝合金车架，21速禧玛诺变速器，前后双油碟刹车");
        product.setPrice(new BigDecimal("1299.00"));
        product.setStockQuantity(50);
        product.setCategoryId(5L);
        product.setMerchantId(8L);
        product.setSku("BIKE-2026-MNT");
        product.setIsActive(1);

        KnowledgeChunkEntity chunk = chunker.chunkProduct(product, "户外骑行", "凤凰官方旗舰店");

        assertNotNull(chunk);
        assertEquals("PRODUCT", chunk.getSourceType());
        assertEquals(101L, chunk.getSourceId());
        assertTrue(chunk.getTitle().contains("2026款专业山地自行车"));
        assertTrue(chunk.getContent().contains("铝合金车架"));
        assertTrue(chunk.getContent().contains("1299.00"));
        assertTrue(chunk.getMetadataJson().contains("\"productId\":101"));
        assertTrue(chunk.getMetadataJson().contains("\"merchantId\":8"));
        assertTrue(chunk.getMetadataJson().contains("\"categoryId\":5"));
    }

    @Test
    @DisplayName("商城规则切片应包含 7天无理由、运费险等平台核心政策")
    void chunkMallRulesGeneratesComprehensiveRules() {
        List<KnowledgeChunkEntity> rules = chunker.chunkMallRules();

        assertNotNull(rules);
        assertTrue(rules.size() >= 5, "平台核心规则应不少于 5 条");

        boolean hasReturnPolicy = rules.stream().anyMatch(r -> r.getTitle().contains("7天无理由退货"));
        boolean hasShippingFee = rules.stream().anyMatch(r -> r.getTitle().contains("运费"));

        assertTrue(hasReturnPolicy, "必须包含 7 天无理由退货规则");
        assertTrue(hasShippingFee, "必须包含运费与运费险承担规则");
    }

    @Test
    @DisplayName("商家切片应正确记录店铺名与联系信息")
    void chunkMerchantGeneratesExpectedStructure() {
        Merchant merchant = new Merchant();
        merchant.setId(88L);
        merchant.setName("小米智能家居专营店");
        merchant.setDescription("主营米家生态链智能设备，正品联保");
        merchant.setContactPhone("400-100-5678");

        KnowledgeChunkEntity chunk = chunker.chunkMerchant(merchant);

        assertNotNull(chunk);
        assertEquals("MERCHANT", chunk.getSourceType());
        assertEquals(88L, chunk.getSourceId());
        assertTrue(chunk.getTitle().contains("小米智能家居专营店"));
        assertTrue(chunk.getContent().contains("米家生态链"));
        assertTrue(chunk.getMetadataJson().contains("\"merchantId\":88"));
    }
}

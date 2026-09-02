package com.scutmmq.ai.rag.ingest;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scutmmq.ai.entity.KnowledgeChunkEntity;
import com.scutmmq.ai.rag.embedding.EmbeddingService;
import com.scutmmq.ai.rag.util.VectorMathUtils;
import com.scutmmq.ai.rag.vectorstore.VectorStore;
import com.scutmmq.entity.Category;
import com.scutmmq.entity.Merchant;
import com.scutmmq.entity.Product;
import com.scutmmq.mapper.CategoryMapper;
import com.scutmmq.mapper.MerchantMapper;
import com.scutmmq.mapper.ProductMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库构建与同步处理服务（Knowledge Ingestion Pipeline）。
 *
 * <p><b>ETL 知识流水线高可用架构：</b></p>
 * <ol>
 *   <li><b>分批处理与事务拆分</b>：避免长事务占用数据库连接池，委托独立 {@link KnowledgeIngestTxService} 按批次（Batch 50）进行切片、向量计算与独立事务落库；</li>
 *   <li><b>容错隔离</b>：单个商品或单条切片嵌入失败不阻断整体 Ingestion 进程；</li>
 *   <li><b>增量同步与即时清理</b>：支持单品上架实时增量更新与下架即时清理。</li>
 * </ol>
 */
@Slf4j
@Service
public class KnowledgeIngestService {

    private static final int BATCH_SIZE = 50;

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final MerchantMapper merchantMapper;
    private final KnowledgeChunker chunker;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final KnowledgeIngestTxService txService;

    public KnowledgeIngestService(ProductMapper productMapper,
                                  CategoryMapper categoryMapper,
                                  MerchantMapper merchantMapper,
                                  KnowledgeChunker chunker,
                                  EmbeddingService embeddingService,
                                  VectorStore vectorStore,
                                  KnowledgeIngestTxService txService) {
        this.productMapper = productMapper;
        this.categoryMapper = categoryMapper;
        this.merchantMapper = merchantMapper;
        this.chunker = chunker;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.txService = txService;
    }

    /**
     * 全量知识库同步构建（由定时任务或管理端手动触发）。
     *
     * @return 本次全量同步成功构建并入库的 Chunk 总数
     */
    public int ingestAll() {
        log.info("[AI][RAG] Starting full knowledge ingestion pipeline...");
        long start = System.currentTimeMillis();
        int totalSaved = 0;

        List<KnowledgeChunkEntity> allChunks = new ArrayList<>();

        // 1. 同步商城平台规则与 FAQ
        List<KnowledgeChunkEntity> ruleChunks = chunker.chunkMallRules();
        allChunks.addAll(ruleChunks);

        // 2. 加载品类与商家字典映射表，避免循环 N+1 查询
        Map<Long, String> categoryMap = loadCategoryMap();
        Map<Long, String> merchantMap = loadMerchantMap();

        // 3. 抽取并切片所有上架商品
        List<Product> products = productMapper.selectList(
                new LambdaQueryWrapper<Product>().eq(Product::getIsActive, 1)
        );
        if (products != null) {
            for (Product p : products) {
                String catName = categoryMap.getOrDefault(p.getCategoryId(), "通用分类");
                String merchantName = merchantMap.getOrDefault(p.getMerchantId(), "官方自营");
                KnowledgeChunkEntity chunk = chunker.chunkProduct(p, catName, merchantName);
                if (chunk != null) {
                    allChunks.add(chunk);
                }
            }
        }

        // 4. 抽取并切片所有已审核通过且激活的正常商家
        List<Merchant> merchants = merchantMapper.selectList(
                new LambdaQueryWrapper<Merchant>()
                        .eq(Merchant::getStatus, com.scutmmq.enums.MerchantStatus.NORMAL)
                        .eq(Merchant::getIsActive, 1)
        );
        if (merchants != null) {
            for (Merchant m : merchants) {
                KnowledgeChunkEntity chunk = chunker.chunkMerchant(m);
                if (chunk != null) {
                    allChunks.add(chunk);
                }
            }
        }

        // 5. 按批次（BATCH_SIZE）计算向量并持久化，委托 TxService 确保独立事务隔离
        for (int i = 0; i < allChunks.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, allChunks.size());
            List<KnowledgeChunkEntity> batch = allChunks.subList(i, end);
            try {
                txService.processBatch(batch);
                totalSaved += batch.size();
            } catch (Exception e) {
                log.error("[AI][RAG] Failed to ingest chunk batch [{} - {}]: {}", i, end, e.getMessage());
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("[AI][RAG] Full knowledge ingestion completed in {}ms, total chunks indexed: {}",
                elapsed, totalSaved);
        return totalSaved;
    }

    /**
     * 单个商品的增量同步（商品上架、价格修改或描述更新时触发）。
     *
     * @param productId 商品 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void ingestProduct(Long productId) {
        if (productId == null) {
            return;
        }

        Product product = productMapper.selectById(productId);
        if (product == null || product.getIsActive() == null || product.getIsActive() == 0) {
            // 商品已删除或下架，清理向量库中的旧数据
            deleteProduct(productId);
            return;
        }

        String catName = "通用分类";
        if (product.getCategoryId() != null) {
            Category category = categoryMapper.selectById(product.getCategoryId());
            if (category != null && category.getName() != null) {
                catName = category.getName();
            }
        }

        String merchantName = "官方自营";
        if (product.getMerchantId() != null) {
            Merchant merchant = merchantMapper.selectById(product.getMerchantId());
            if (merchant != null && merchant.getName() != null) {
                merchantName = merchant.getName();
            }
        }

        KnowledgeChunkEntity chunk = chunker.chunkProduct(product, catName, merchantName);
        if (chunk == null) {
            return;
        }

        // 先清理旧切片，再写入新切片
        vectorStore.deleteBySource("PRODUCT", productId);
        float[] vector = embeddingService.embedQuery(chunk.getContent());
        chunk.setEmbeddingJson(VectorMathUtils.toJson(vector));
        vectorStore.saveChunks(Collections.singletonList(chunk));

        log.info("[AI][RAG] Incrementally ingested product knowledge: productId={}", productId);
    }

    /**
     * 单个商品下架或删除时清理切片。
     *
     * @param productId 商品 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteProduct(Long productId) {
        if (productId != null) {
            vectorStore.deleteBySource("PRODUCT", productId);
            log.info("[AI][RAG] Deleted product knowledge from vector store: productId={}", productId);
        }
    }

    private Map<Long, String> loadCategoryMap() {
        Map<Long, String> map = new HashMap<>();
        List<Category> categories = categoryMapper.selectList(null);
        if (categories != null) {
            for (Category c : categories) {
                if (c.getId() != null && c.getName() != null) {
                    map.put(c.getId(), c.getName());
                }
            }
        }
        return map;
    }

    private Map<Long, String> loadMerchantMap() {
        Map<Long, String> map = new HashMap<>();
        List<Merchant> merchants = merchantMapper.selectList(null);
        if (merchants != null) {
            for (Merchant m : merchants) {
                if (m.getId() != null && m.getName() != null) {
                    map.put(m.getId(), m.getName());
                }
            }
        }
        return map;
    }
}

package com.scutmmq.ai.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 知识库分块（Knowledge Chunk）持久化实体类。
 * 对应 MySQL 数据表：{@code ai_knowledge_chunk}。
 *
 * <p><b>什么是知识切片（Chunking）？</b></p>
 * <p>
 * 在大模型 RAG（Retrieval-Augmented Generation，检索增强生成）系统中，
 * 直接将数十万字的完整商品手册或商城规则输入大模型会导致 Context Window 溢出且费用高昂。
 * 知识切片是将长文档按语义单元（如单个商品、单条规则、单问单答）切割成高内聚的较小文本块。
 * 每个分块配有唯一的 Embedding 向量表示与结构化元数据，以便进行高效的向量近邻搜索。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_knowledge_chunk")
public class KnowledgeChunkEntity {

    /**
     * 分块主键 ID（自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 知识来源类型：
     * <ul>
     *   <li>PRODUCT: 商品详细参数与说明</li>
     *   <li>MERCHANT: 商家介绍与店铺专属服务政策</li>
     *   <li>RULE: 商城售后、退换货与通用平台规则</li>
     *   <li>FAQ: 常见高频问答对</li>
     * </ul>
     */
    private String sourceType;

    /**
     * 知识来源实体 ID（例如商品 ID、店铺 ID，全局规则为 0）
     */
    private Long sourceId;

    /**
     * 同一源实体下的分块序号（从 0 开始自增）
     */
    private Integer chunkIndex;

    /**
     * 分块标题（例如：“[退换货政策] 7天无理由退货规则”、“[商品规格] 2026款山地自行车参数”）
     */
    private String title;

    /**
     * 分块文本内容（供向量化与大模型阅读的自然语言文本）
     */
    private String content;

    /**
     * 结构化元数据（JSON 格式字符串）。
     * 例如包含：{@code {"categoryId": 12, "merchantId": 3, "price": 99.00}}。
     * 用于在向量召回前进行租户隔离与类目过滤（Pre-filtering）。
     */
    private String metadataJson;

    /**
     * 文本对应的多维稠密向量 JSON 字符串（例如 1024 维浮点数组 {@code [0.012, -0.045, ...]}）。
     * 用于在向量空间中与用户的 Query 向量进行余弦相似度计算。
     */
    private String embeddingJson;

    /**
     * 分块可用状态：1=启用（可被检索召回），0=禁用（逻辑下架或失效）
     */
    private Integer status;

    /**
     * 创建时间（由数据库或框架自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 最后更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

package com.scutmmq.ai.rag.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scutmmq.ai.entity.KnowledgeChunkEntity;
import com.scutmmq.entity.Merchant;
import com.scutmmq.entity.Product;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库切片（Chunker）构建器。
 * 负责将电商业务实体（商品、商家、平台规则、FAQ）转化为标准化、高内聚的知识切片实体。
 *
 * <p><b>知识分块与安全合规最佳实践：</b></p>
 * <ul>
 *   <li><b>高语义密度（High Information Density）</b>：分块内容包含丰富的实体名称、属性、规则与上下文，便于向量模型提取语义特征；</li>
 *   <li><b>敏感信息保护（PII Protection）</b>：商家联系方式与内部地址等敏感信息不直接输出至开放文本，防止跨店铺泄漏；</li>
 *   <li><b>长文本控制（Token Limit Protection）</b>：超长商品描述进行长度截断保护，防止超出 Embedding 模型上下文窗口；</li>
 *   <li><b>结构化元数据附着（Metadata Attachment）</b>：为每个 Chunk 关联分类、价格、店铺等结构化标签，支撑租户隔离与多维过滤。</li>
 * </ul>
 */
@Slf4j
@Component
public class KnowledgeChunker {

    private static final int MAX_PRODUCT_DESC_LENGTH = 800;
    private final ObjectMapper objectMapper;

    public KnowledgeChunker(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 将单个商品实体转化为知识切片。
     */
    public KnowledgeChunkEntity chunkProduct(Product product, String categoryName, String merchantName) {
        if (product == null) {
            return null;
        }

        String safeCat = (categoryName != null && !categoryName.isEmpty()) ? categoryName : "通用分类";
        String safeMerchant = (merchantName != null && !merchantName.isEmpty()) ? merchantName : "官方自营";
        String rawDesc = (product.getDescription() != null && !product.getDescription().isEmpty())
                ? product.getDescription()
                : "暂无额外描述";

        // 防止超长描述导致 Embedding API 400 失败
        String safeDesc = rawDesc.length() > MAX_PRODUCT_DESC_LENGTH
                ? rawDesc.substring(0, MAX_PRODUCT_DESC_LENGTH) + "..."
                : rawDesc;

        String title = String.format("[商品详情] %s (%s)", product.getName(), safeCat);

        String content = String.format(
                "商品名称：%s\n" +
                "商品分类：%s\n" +
                "所属店铺：%s\n" +
                "销售价格：%s 元\n" +
                "当前库存：%d 件\n" +
                "商品SKU编码：%s\n" +
                "商品详细介绍：%s",
                product.getName(),
                safeCat,
                safeMerchant,
                product.getPrice() != null ? product.getPrice().toPlainString() : "0.00",
                product.getStockQuantity() != null ? product.getStockQuantity() : 0,
                product.getSku() != null ? product.getSku() : "N/A",
                safeDesc
        );

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("productId", product.getId());
        metadata.put("categoryId", product.getCategoryId());
        metadata.put("merchantId", product.getMerchantId());
        metadata.put("price", product.getPrice() != null ? product.getPrice().doubleValue() : 0.0);
        metadata.put("categoryName", safeCat);

        return KnowledgeChunkEntity.builder()
                .sourceType("PRODUCT")
                .sourceId(product.getId())
                .chunkIndex(0)
                .title(title)
                .content(content)
                .metadataJson(toJson(metadata))
                .status(product.getIsActive() != null ? product.getIsActive() : 1)
                .build();
    }

    /**
     * 将商家/店铺实体转化为知识切片（脱敏安全版本）。
     */
    public KnowledgeChunkEntity chunkMerchant(Merchant merchant) {
        if (merchant == null) {
            return null;
        }

        String title = String.format("[店铺服务] %s", merchant.getName());
        String content = String.format(
                "店铺名称：%s\n" +
                "店铺简介：%s\n" +
                "经营范围：综合优质电商百货与品牌正品\n" +
                "配送服务：全国标准电商物流配送",
                merchant.getName(),
                merchant.getDescription() != null ? merchant.getDescription() : "优质电商合作商家"
        );

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("merchantId", merchant.getId());
        metadata.put("merchantName", merchant.getName());

        return KnowledgeChunkEntity.builder()
                .sourceType("MERCHANT")
                .sourceId(merchant.getId())
                .chunkIndex(0)
                .title(title)
                .content(content)
                .metadataJson(toJson(metadata))
                .status(1)
                .build();
    }

    /**
     * 生成商城标准平台规则与售后常见问题（FAQ）切片集合。
     */
    public List<KnowledgeChunkEntity> chunkMallRules() {
        List<KnowledgeChunkEntity> rules = new ArrayList<>();
        long index = 1;

        rules.add(buildRuleChunk(index++, "7天无理由退货政策",
                "【7天无理由退货规则】\n" +
                "1. 适用范围：除定制商品、鲜活易腐商品、数字化商品及拆封后影响人身安全或卫生的贴身商品外，" +
                "普通商品自用户签收次日起 7 天内，均支持无理由退货退款。\n" +
                "2. 商品完好标准：退回商品需保持原有品质、功能，外包装、吊牌、配件、赠品齐全，不影响二次销售。\n" +
                "3. 申请流程：在‘我的订单’找到对应订单，点击‘申请售后’选择‘仅退款’或‘退货退款’。"));

        rules.add(buildRuleChunk(index++, "运费险与退货运费承担规则",
                "【运费承担与运费险政策】\n" +
                "1. 质量问题与商家过错：若因商品质量缺陷、错发漏发、描述不符导致的退换货，往返运费由商家全额承担。\n" +
                "2. 7天无理由与个人原因：因个人喜好、尺码不合适等个人原因申请退货，退回运费由买家自行承担。\n" +
                "3. 运费险自动理赔：购买带有‘退货运费险’标识的商品，卖家同意退款并完成退货签收后，" +
                "保险公司将在 72 小时内自动将理赔款项退回买家支付账户。"));

        rules.add(buildRuleChunk(index++, "订单取消与修改收货地址规则",
                "【订单取消与地址变更政策】\n" +
                "1. 待付款状态：用户可随时自主取消订单，优惠券自动退回账户。\n" +
                "2. 待发货状态：用户可在订单详情点击‘申请退款’，商家审核通过后即刻全额原路退款；如需修改收货地址，请联系在线客服协助拦截修改。\n" +
                "3. 已发货状态：商品已进入物流运输途中，不支持直接修改地址或取消订单，用户可在派送时选择拒收或签收后申请 7 天无理由退货。"));

        rules.add(buildRuleChunk(index++, "发票开具与下载规则说明",
                "【电子发票开具说明】\n" +
                "1. 发票类型：商城所有自营与入驻商家均支持开具正规增值税电子普通发票。\n" +
                "2. 申请入口：订单交易完成（确认收货）后，在‘我的订单-申请开票’中填写抬头（个人或企业税号）及接收邮箱。\n" +
                "3. 开票时效：系统通常在申请提交后 24-48 小时内完成开具，开出后可在订单详情下载 PDF 电子发票。"));

        rules.add(buildRuleChunk(index++, "正品保障与假一赔十政策",
                "【商城正品保障承诺】\n" +
                "1. 品牌直供与资质核验：商城所有入驻商家均经过严格的企业营业执照、品牌授权书及质检报告审核。\n" +
                "2. 假一赔十保障：若经国家法定质检机构鉴定为假冒伪劣商品，商城承诺执行‘假一赔十’并全额退还订单款项。\n" +
                "3. 平台介入维权：买家发起正品争议后，平台专家客服将在 24 小时内介入并先行赔付。"));

        rules.add(buildRuleChunk(index++, "退款到账时效与退回路径说明",
                "【退款到账时间与渠道】\n" +
                "1. 原路退回原则：所有退款均原路退回到买家支付时的账户（微信零钱、微信绑定银行卡、支付宝或信用卡）。\n" +
                "2. 微信零钱/支付宝余额：退款审核通过后实时到账（0-2小时）。\n" +
                "3. 借记卡/储蓄卡：通常在 1-3 个工作日内到账，具体以各商业银行入账时效为准。\n" +
                "4. 信用卡支付：退款通常在 3-7 个工作日内完成冲正或恢复信用额度。"));

        return rules;
    }

    private KnowledgeChunkEntity buildRuleChunk(long index, String title, String content) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("ruleId", index);
        metadata.put("type", "FAQ");

        return KnowledgeChunkEntity.builder()
                .sourceType("RULE")
                .sourceId(index)
                .chunkIndex(0)
                .title("[商城规则] " + title)
                .content(content)
                .metadataJson(toJson(metadata))
                .status(1)
                .build();
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("[AI][RAG] Failed to serialize chunk metadata: {}", e.getMessage());
            return "{}";
        }
    }
}

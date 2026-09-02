package com.scutmmq.ai.rag.vectorstore;

/**
 * 向量检索的多维结构化元数据过滤器（Metadata Pre-filter & Post-filter）。
 *
 * <p><b>为什么需要元数据过滤？</b></p>
 * <p>
 * 单纯的向量搜索仅根据语义相似度检索，容易出现跨品类、跨商家的越权或噪声匹配。
 * 通过在向量检索时传入元数据过滤条件：
 * <ul>
 *   <li><b>多租户安全隔离</b>：查询特定商家私有政策时，只允许召回该商家的 Chunk，杜绝数据越权；</li>
 *   <li><b>品类精准收敛</b>：在咨询“数码电子”类商品问题时，排查无关品类的干扰信息；</li>
 *   <li><b>知识类型限制</b>：可指定仅搜索“商城售后规则（RULE）”或仅搜索“商品参数（PRODUCT）”。</li>
 * </ul>
 * </p>
 *
 * @param sourceType 知识源类型过滤（PRODUCT / MERCHANT / RULE / FAQ，null 表示全类型）
 * @param merchantId 商家 ID 过滤（null 表示不限制商家）
 * @param categoryId 商品分类 ID 过滤（null 表示不限制分类）
 */
public record SearchFilter(
        String sourceType,
        Long merchantId,
        Long categoryId
) {
    public static SearchFilter all() {
        return new SearchFilter(null, null, null);
    }

    public static SearchFilter ofSourceType(String sourceType) {
        return new SearchFilter(sourceType, null, null);
    }

    public static SearchFilter ofMerchant(Long merchantId) {
        return new SearchFilter("MERCHANT", merchantId, null);
    }

    public static SearchFilter ofProduct(Long categoryId) {
        return new SearchFilter("PRODUCT", null, categoryId);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String sourceType;
        private Long merchantId;
        private Long categoryId;

        public Builder sourceType(String sourceType) {
            this.sourceType = sourceType;
            return this;
        }

        public Builder merchantId(Long merchantId) {
            this.merchantId = merchantId;
            return this;
        }

        public Builder categoryId(Long categoryId) {
            this.categoryId = categoryId;
            return this;
        }

        public SearchFilter build() {
            return new SearchFilter(sourceType, merchantId, categoryId);
        }
    }
}

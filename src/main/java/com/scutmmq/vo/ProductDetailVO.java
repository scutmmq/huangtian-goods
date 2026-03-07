package com.scutmmq.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品详情 DTO（用于 /products/{productId} 接口响应）
 */
@Data
public class ProductDetailVO {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stockQuantity;
    private Long categoryId;
    private String sku;
    private String imageUrl;
    private Integer isActive;
    private Long merchantId;
    private LocalDateTime createdTime;
    private Integer favorite;
    private Boolean isFavorite;
    private BigDecimal rating;
    private Integer ratingCount;

    // 嵌套商家信息
    private MerchantSimpleDTO merchant;

    // 嵌套分类信息（含父分类）
    private CategoryDetailDTO category;

    /**
     * 商家简易信息 DTO
     */
    @Data
    public static class MerchantSimpleDTO {
        private Long id;
        private String name;
        private BigDecimal merchantRating;
        private Integer merchantRatingCount;
        private Integer totalSales;
        private String logoUrl;
    }

    /**
     * 分类详情 DTO（含父分类）
     */
    @Data
    public static class CategoryDetailDTO {
        private Long id;
        private String name;
        private ParentCategoryDTO parentCategory;

        /**
         * 父分类 DTO
         */
        @Data
        public static class ParentCategoryDTO {
            private Long id;
            private String name;
        }
    }
}

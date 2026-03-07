package com.scutmmq.vo;

import lombok.Data;
import java.math.BigDecimal;
@Data
public class ProductVO {
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
        private String merchantName; // 来自merchant表
        private String categoryName; // 来自product_category表
        private BigDecimal rating;
        private Integer ratingCount;
        private BigDecimal merchantRating;// 来自merchant表
        private Integer merchantRatingCount;//来自merchant表

}

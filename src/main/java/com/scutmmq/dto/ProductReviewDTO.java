package com.scutmmq.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品评论DTO
 */
@Data
public class ProductReviewDTO {

    /**
     * 商品ID
     */
    private Long productId;

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 订单项ID
     */
    private Long orderItemId;

    /**
     * 评分（1-5星）
     */
    private BigDecimal rating;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 评论图片URL列表
     */
    private String image;

    /**
     * 商家ID
     */
    private Long merchantId;

}
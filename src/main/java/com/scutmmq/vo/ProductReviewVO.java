package com.scutmmq.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品评论VO类
 */
@Data
public class ProductReviewVO {

    /**
     * 评价id
     */
    private Long id;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 商品id
     */
    private Long productId;

    /**
     * 订单id
     */
    private Long orderId;

    /**
     * 订单项id
     */
    private Long orderItemId;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 评论图片
     */
    private String image;

    /**
     * 评分
     */
    private BigDecimal rating;

    /**
     * 评论时间
     */
    private LocalDateTime reviewTime;

    /**
     * 商家回复内容
     */
    private String merchantReply;

    private String merchantName;

    private String merchantLogoUrl;

    /**
     * 商家回复时间
     */
    private LocalDateTime replyTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 用户简要信息
     */
    private UserSimpleInfo user;

    /**
     * 商品简要信息
     */
    private ProductSimpleInfo product;

    /**
     * 用户简要信息内部类
     */
    @Data
    public static class UserSimpleInfo {
        private Long id;
        private String nickName;
        private String avatarUrl;
    }

    /**
     * 商品简要信息内部类
     */
    @Data
    public static class ProductSimpleInfo {
        private Long id;
        private String name;
        private String imageUrl;
    }
}
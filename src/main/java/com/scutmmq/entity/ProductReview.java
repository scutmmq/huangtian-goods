package com.scutmmq.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品评论表实体类
 * 对应表：product_review
 *
 * @author xxx
 * @date 2025-12-04
 */
@Data
@TableName("product_review") // 映射数据库表名
public class ProductReview {

    /**
     * 评价id（主键，自增）
     */
    @TableId(type = IdType.AUTO) // 主键策略：自增（匹配数据库 auto_increment）
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
     * 评论图片（建议用String，存储图片URL/路径，多个可逗号分隔）
     */
    private String image;

    /**
     * 评分（如 4.50、5.00）
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

    /**
     * 商家回复时间
     */
    private LocalDateTime replyTime;

    /**
     * 创建时间（默认当前时间）
     */
    private LocalDateTime createTime;

    /**
     * 更新时间（更新时自动触发）
     */
    private LocalDateTime updateTime;

}
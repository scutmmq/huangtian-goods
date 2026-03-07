package com.scutmmq.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单项表实体类
 * 对应数据库表：order_items
 */
@Data
@TableName("order_items")
public class OrderItems{

    /**
     * 订单项ID（自增主键）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 订单ID（关联orders表）
     */
    @TableField(value = "order_id")
    private Long orderId;

    /**
     * 商品ID（关联product表）
     */
    @TableField(value = "product_id")
    private Long productId;

    /**
     * 商品名称（下单时快照）
     */
    @TableField(value = "product_name")
    private String productName;

    /**
     * 商品单价（下单时快照）
     */
    @TableField(value = "product_price")
    private BigDecimal productPrice;

    /**
     * 购买数量
     */
    @TableField(value = "quantity")
    private Integer quantity;

    /**
     * 小计金额（单价×数量）
     */
    @TableField(value = "subtotal")
    private BigDecimal subtotal;

    /**
     * 商家ID（快照）
     */
    @TableField(value = "merchant_id")
    private Long merchantId;

    /**
     * 是否已评价（默认false）
     */
    @TableField(value = "is_reviewed")
    private Boolean isReviewed;
}

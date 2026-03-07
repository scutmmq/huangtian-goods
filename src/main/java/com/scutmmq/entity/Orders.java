package com.scutmmq.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.scutmmq.enums.OrderStatus;
import com.scutmmq.enums.PaymentMethod;
import com.scutmmq.enums.PaymentStatus;
import com.scutmmq.enums.ShipType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单表实体类
 * 对应数据库表：orders
 */
@Data
@TableName("orders") // 指定数据库表名
public class Orders {

    /**
     * 订单ID（自增主键）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 订单号（唯一标识）
     */
    @TableField(value = "order_number")
    private String orderNumber;

    /**
     * 用户ID（关联user表）
     */
    @TableField(value = "user_id")
    private Long userId;

    /**
     * 商家ID（关联merchant表）
     */
    @TableField(value = "merchant_id")
    private Long merchantId;

    /**
     * 订单总金额
     */
    @TableField(value = "total_amount")
    private BigDecimal totalAmount;

    /**
     * 订单状态
     */
    @TableField(value = "status")
    private OrderStatus status = OrderStatus.PENDING;

    /**
     * 支付方式（如支付宝、微信）
     */
    @TableField(value = "payment_method")
    private PaymentMethod paymentMethod;

    /**
     * 支付状态
     */
    @TableField(value = "payment_status")
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    /**
     * 配送方式（如顺丰、京东物流）
     */
    @TableField("shipping_method") // 允许为null，使用默认策略
    private ShipType shippingMethod;

    /**
     * 物流单号
     */
    @TableField("tracking_number") // 允许为null，使用默认策略
    private String trackingNumber;

    /**
     * 下单时间（默认当前时间，不允许更新）
     */
    @TableField(value = "ordered_time", fill = FieldFill.INSERT)
    private LocalDateTime orderedTime;

    /**
     * 支付时间
     */
    @TableField("paid_time")
    private LocalDateTime paidTime;

    /**
     * 发货时间
     */
    @TableField("shipped_time")
    private LocalDateTime shippedTime;

    /**
     * 送达时间
     */
    @TableField("delivered_time")
    private LocalDateTime deliveredTime;

    /**
     * 收货地址ID（关联user_address表）
     */
    @TableField(value = "shipping_address_id")
    private Long shippingAddressId;

    /**
     * 账单地址ID（关联user_address表）
     */
    @TableField(value = "billing_address_id")
    private Long billingAddressId;

    /**
     * 订单备注
     */
    @TableField(value = "remark")
    private String remark;
}
package com.scutmmq.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品表实体类
 * 对应数据库表：product
 */
@Data
@TableName("product")
public class Product {

    /**
     * 商品ID（自增主键）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 商品名称（非空）
     */
    @TableField("name")
    private String name;

    /**
     * 商品描述（可为空，长文本）
     */
    @TableField("description")
    private String description;

    /**
     * 价格（非空，精确到小数点后两位）
     */
    @TableField("price")
    private BigDecimal price;

    /**
     * 库存数量（默认0）
     */
    @TableField(value = "stock_quantity", fill = FieldFill.INSERT)
    private Integer stockQuantity;

    /**
     * 分类ID（非空，关联product_category表）
     */
    @TableField("category_id")
    private Long categoryId;

    /**
     * 商品SKU（非空，唯一标识）
     */
    @TableField("sku")
    private String sku;

    /**
     * 商品图片URL（默认空字符串）
     */
    @TableField(value = "image_url", fill = FieldFill.INSERT)
    private String imageUrl;

    /**
     * 是否上架（0-下架，1-上架，默认1）
     */
    @TableField(value = "is_active", fill = FieldFill.INSERT)
    private Integer isActive;

    /**
     * 商家ID（非空，关联merchant表）
     */
    @TableField("merchant_id")
    private Long merchantId;

    /**
     * 创建时间（默认当前时间）
     */
    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /**
     * 更新时间（默认当前时间，更新时自动刷新）
     */
    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableField(value = "favorite")
    private Integer favorite;

    @TableField(value = "rating_count")
    private Integer ratingCount;

    @TableField(value = "rating")
    private BigDecimal rating;

}
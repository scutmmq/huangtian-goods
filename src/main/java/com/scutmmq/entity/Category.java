package com.scutmmq.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
/*
 * 商品分类表实体类
 * 对应数据库表：product_category
 */
@Data
@TableName("product_category")
public class Category {

    /**
     * 分类ID（自增主键）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 分类名称（非空）
     */
    @TableField("name")
    private String name;

    /**
     * 父分类ID（0表示一级分类，默认0）
     */
    @TableField(value = "parent_id", fill = FieldFill.INSERT)
    private Long parentId = 0L;

    /**
     * 分类级别（1：一级，2：二级，3：三级，非空）
     */
    @TableField("level")
    private Integer level;

    /**
     * 排序（数值越小越靠前，默认0）
     */
    @TableField(value = "sort", fill = FieldFill.INSERT)
    private Integer sort = 0;

    /**
     * 分类图标URL（可为空）
     */
    @TableField("icon")
    private String icon;

    /**
     * 分类描述（可为空）
     */
    @TableField("description")
    private String description;

    /**
     * 状态（0：禁用，1：启用，默认1）
     */
    @TableField(value = "status", fill = FieldFill.INSERT)
    private Integer status = 1;

    /**
     * 创建时间（默认当前时间，非空）
     */
    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /**
     * 更新时间（默认当前时间，更新时自动刷新，非空）
     */
    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
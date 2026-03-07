package com.scutmmq.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商家用户关联表实体类
 */
@Data
@TableName(value = "merchant_user", autoResultMap = true) // autoResultMap=true 用于JSON字段映射
public class MerchantUser {

    /**
     * 自增ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 商家ID（关联merchant表）
     */
    private Long merchantId;

    /**
     * 用户ID（关联user表）
     */
    private Long userId;


    /**
     * 是否激活：1-激活，0-未激活
     */
    private Integer isActive;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;
}

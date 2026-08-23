package com.scutmmq.ai.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * B3 长期记忆主表实体。对应 ai_user_memory,见 V20260823__ai_user_memory.sql。
 *
 * 注意:userId 同时充当 PRIMARY KEY,所以 @TableId(INPUT) — 业务侧写入。
 * JSON 字段在 MySQL 是 JSON 类型,在 Java 侧使用 String 承载(读写由 MP XML/注解处理器负责转换)。
 */
@Data
@TableName("ai_user_memory")
public class UserMemoryEntity {

    @TableId(type = IdType.INPUT)
    private Long userId;

    private String identityJson;

    private String preferenceJson;

    private LocalDateTime computedAt;

    private Integer recomputeStatus;

    private Integer failCount;

    @Version
    private Integer version;

    private Long computeSeq;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
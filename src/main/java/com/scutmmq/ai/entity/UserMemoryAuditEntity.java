package com.scutmmq.ai.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * B3 长期记忆审计实体。对应 ai_user_memory_audit,见 V20260823__ai_user_memory.sql。
 *
 * <p><b>为什么没有 @TableName / @TableId:</b> 该表 PRIMARY KEY 为复合键
 * {@code (id, created_at)},这是 MySQL 8 InnoDB 分区约束(必须包含分区键)。MyBatis-Plus
 * 的 @TableId 只支持单列主键;若强行标注,MP 会把 {@code id} 当成逻辑 PK,但 DB 层仍按复合
 * PK 校验,容易在 update/delete 路径上误用导致 SQL 异常。
 *
 * <p>因此本实体采用"更稳妥"做法(见 task-1 brief 上下文):
 * <ul>
 *   <li>不标注 @TableName,MyBatis-Plus 完全不介入本实体</li>
 *   <li>对应 mapper 不继承 BaseMapper&lt;UserMemoryAuditEntity&gt;,只通过 @Insert 自定义方法插入</li>
 *   <li>id 由 DB {@code BIGINT UNSIGNED AUTO_INCREMENT} 生成,Java 端只读</li>
 *   <li>createdAt 由 DB {@code DEFAULT CURRENT_TIMESTAMP} 自动填充,Java 端只读</li>
 * </ul>
 */
@Data
public class UserMemoryAuditEntity {

    private Long id;

    private Long userId;

    private String action;

    private String fieldsChanged;

    private String triggeredBy;

    private Integer tokenEstimate;

    private String fieldDropped;

    private String actorIp;

    private String requestId;

    private String errorMessage;

    private LocalDateTime expiresAt;

    private LocalDateTime createdAt;
}
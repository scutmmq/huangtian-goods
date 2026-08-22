package com.scutmmq.ai.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI Run 用量落库行,与 ai_run_usage 表对应。
 *
 * 为什么走单独表 vs 合并到 ai_run:
 * ai_run 关心 Run 状态机;ai_run_usage 关心统计聚合(按 user/role/session)。
 * 分离便于:
 * 1. 不同保留策略(ai_run 长期,ai_run_usage 90 天)
 * 2. 不污染 ai_run 状态机字段
 * 3. 大表按 created_at 分区不影响 Run 状态流转
 *
 * usage 数据从 RunResult 落库,字段语义以 RunResult 为准。
 */
@Data
@TableName("ai_run_usage")
public class AiRunUsage {

    @TableId(type = com.baomidou.mybatisplus.annotation.IdType.AUTO)
    private Long id;

    private String runId;
    private String sessionId;
    private Long userId;
    private String userRole;

    private Integer toolCount;
    private Integer hasDraft;
    private Long totalMs;
    private Long ttftMs;

    private Integer promptTokens;
    private Integer completionTokens;
    private Integer reasoningTokens;
    private Integer totalTokens;
    private Integer promptCostCents;
    private Integer completionCostCents;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

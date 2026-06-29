package com.scutmmq.ai.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_run")
public class AiRun {

    @TableId
    private String id;

    private Long userId;

    private String sessionId;

    private Long userMessageId;

    private Long assistantMessageId;

    /**
     * QUEUED / RUNNING / COMPLETED / FAILED / CANCELLED
     */
    private String status;

    private String errorMessage;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
package com.scutmmq.ai.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_stream_event")
public class AiStreamEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String runId;

    private String sessionId;

    private Long messageId;

    private Long userId;

    /**
     * assistant.delta / tool.started / tool.finished / draft.created / run.completed / run.failed
     */
    private String type;

    private String payloadJson;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
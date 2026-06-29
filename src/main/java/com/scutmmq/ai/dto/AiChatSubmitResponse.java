package com.scutmmq.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * /ai/chat 异步提交响应。
 *
 * 提交一条消息后立刻返回：会话 ID、新建 Run 的 ID、状态、对应消息 ID。
 * 实际生成过程在后台 ai-task-* 线程中执行，前端拿 runId 走 SSE 拉流（Task 7）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiChatSubmitResponse {

    private String sessionId;

    private String runId;

    /**
     * 提交时固定为 AiRunService.STATUS_QUEUED ("QUEUED")。
     */
    private String status;

    private Long userMessageId;

    private Long assistantMessageId;

    private LocalDateTime createdAt;
}
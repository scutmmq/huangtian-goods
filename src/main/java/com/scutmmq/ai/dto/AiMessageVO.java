package com.scutmmq.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 消息视图对象。给前端 /ai/sessions/{id}/messages 用。
 *
 * 与 {@link com.scutmmq.ai.entity.AiMessage} 的差异：
 * - 多了 runId / userMessageId 便于前端把"一条 assistant 消息"和"对应 Run / 对应 user 消息"对起来；
 * - status 字段在前端"看起来 STREAMING"时优先从关联 Run 的状态派生（Run 还活着则跟着 Run 走）。
 * - 多了 draft 字段，把该 assistant 消息关联的草稿（如果有）一起带回去，避免前端再发一次请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiMessageVO {

    private Long id;

    private String sessionId;

    /**
     * user / assistant / tool
     */
    private String role;

    private String content;

    /**
     * STREAMING / COMPLETED / FAILED（assistant 才有；user / tool 始终为 null）。
     * 计算规则见 {@code AiAssistantService#listMessagesAsVO}。
     */
    private String status;

    /**
     * 关联的 Run ID（assistant 才有）。前端用 runId 串到 SSE 流上做断线续传。
     */
    private String runId;

    /**
     * 关联的 user 消息 ID（assistant 才有）—— 同一轮对话里 user / assistant 是成对的。
     */
    private Long userMessageId;

    /**
     * 助手消息关联的草稿（assistant 才有，可空）。
     */
    private AiActionDraftVO draft;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

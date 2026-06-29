package com.scutmmq.ai.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 助手消息关联的草稿视图对象。
 *
 * 由 ai_action_draft 行 + 在 user 端只读地暴露给前端的字段组成。
 * 完整 payload 在用户确认前不向前端开放，只给一个安全子集。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiActionDraftVO {

    private String id;

    private String actionType;

    private String title;

    private String summary;

    /**
     * 完整 payload 的子集，由后端白名单过滤后输出。
     */
    private JsonNode payload;

    private String status;

    private LocalDateTime expiresAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

package com.scutmmq.ai.tool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具执行结果。
 *
 * content: 给模型看的、必须是纯文本的工具反馈。
 * draft:   仅在 DRAFT_ONLY 工具中填，表示当前轮次生成了一个待用户确认的写操作草稿，
 *          AssistantService 在拿到草稿后会把它一并返回给前端。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentToolResult {

    private String content;

    private DraftPayload draft;

    public static AgentToolResult ofText(String content) {
        return new AgentToolResult(content, null);
    }

    public static AgentToolResult ofDraft(String content, DraftPayload draft) {
        return new AgentToolResult(content, draft);
    }

    /**
     * 在工具内组装好的写操作草稿描述。仍未落库。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DraftPayload {
        private String actionType;
        private String title;
        private String summary;
        private com.fasterxml.jackson.databind.JsonNode payload;
    }
}

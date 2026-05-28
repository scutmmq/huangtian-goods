package com.scutmmq.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiChatResponse {

    private String sessionId;

    private String reply;

    private ActionDraftVO actionDraft;

    private List<ToolExecutionVO> toolResults;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionDraftVO {
        private String id;
        private String type;
        private String title;
        private String summary;
        private Object payload;
        private LocalDateTime expiresAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolExecutionVO {
        private String name;
        private String contentPreview;
    }
}

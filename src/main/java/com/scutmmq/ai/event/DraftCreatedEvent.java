package com.scutmmq.ai.event;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 草稿创建事件。当某工具返回了 DraftPayload 时 publish。
 * 处理者用它做:审计 / 入站事件统计 / 邮件告警等扩展。
 */
@Getter
public class DraftCreatedEvent extends ApplicationEvent {

    private final String runId;
    private final String sessionId;
    private final Long userId;
    private final String actionType;
    private final String title;
    private final String summary;
    private final JsonNode payload;

    public DraftCreatedEvent(Object source, String runId, String sessionId, Long userId,
                             String actionType, String title, String summary, JsonNode payload) {
        super(source);
        this.runId = runId;
        this.sessionId = sessionId;
        this.userId = userId;
        this.actionType = actionType;
        this.title = title;
        this.summary = summary;
        this.payload = payload;
    }

    /**
     * 事件载荷的轻量快照(用于 listener 里序列化等场景)。
     */
    public record Payload(String runId, String sessionId, Long userId,
                          String actionType, String title, String summary) {
    }
}

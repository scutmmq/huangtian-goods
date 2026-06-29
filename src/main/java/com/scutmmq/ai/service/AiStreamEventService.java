package com.scutmmq.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scutmmq.ai.entity.AiStreamEvent;
import com.scutmmq.ai.mapper.AiStreamEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 流式事件服务。
 *
 * 写入顺序：先 INSERT 落库，再 BROADCAST 推到在线 SSE 连接。
 * 这样保证：客户端无论何时断线重连，都能从 ai_stream_event 表中按 Last-Event-ID 补齐，
 * 不会因为内存丢失而漏事件。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiStreamEventService {

    public static final String TYPE_ASSISTANT_DELTA = "assistant.delta";
    public static final String TYPE_TOOL_STARTED = "tool.started";
    public static final String TYPE_TOOL_FINISHED = "tool.finished";
    public static final String TYPE_DRAFT_CREATED = "draft.created";
    public static final String TYPE_RUN_COMPLETED = "run.completed";
    public static final String TYPE_RUN_FAILED = "run.failed";

    private final AiStreamEventMapper aiStreamEventMapper;
    private final AiStreamHub aiStreamHub;
    private final ObjectMapper objectMapper;

    /**
     * 追加一条事件：先入库，再广播。
     *
     * @return 持久化后的实体（带自增 id）
     */
    public AiStreamEvent append(String runId,
                                String sessionId,
                                Long messageId,
                                Long userId,
                                String type,
                                JsonNode payload) {
        AiStreamEvent event = new AiStreamEvent();
        event.setRunId(runId);
        event.setSessionId(sessionId);
        event.setMessageId(messageId);
        event.setUserId(userId);
        event.setType(type);
        event.setPayloadJson(writeJson(payload));

        // 先落库。MyBatis-Plus insert 会回填自增 id。
        aiStreamEventMapper.insert(event);
        log.info("[AI][STREAM] appended event id={} runId={} sessionId={} type={} msgId={}",
                event.getId(), runId, sessionId, type, messageId);

        // 落库成功后再广播。广播失败不影响持久化。
        try {
            aiStreamHub.broadcast(sessionId, event);
        } catch (Exception e) {
            log.warn("[AI][STREAM] broadcast failed for event id={} sessionId={}: {}",
                    event.getId(), sessionId, e.getMessage());
        }
        return event;
    }

    /**
     * 查询某 session 在 afterId 之后的事件，按 id 升序，上限 1000 条。
     * 用于 SSE 重连补齐（Last-Event-ID）。
     * afterId 为 null 时当作 0，返回该 session 全部事件。
     */
    public List<AiStreamEvent> queryAfter(String sessionId, Long afterId) {
        if (sessionId == null) {
            return List.of();
        }
        long cursor = afterId == null ? 0L : afterId;
        return aiStreamEventMapper.selectAfterId(sessionId, cursor);
    }

    private String writeJson(JsonNode payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("[AI][STREAM] failed to serialize payload, falling back to toString(): {}",
                    e.getMessage());
            return payload.toString();
        }
    }
}
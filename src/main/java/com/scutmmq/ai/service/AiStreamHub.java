package com.scutmmq.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scutmmq.ai.entity.AiStreamEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * AI 会话的 SSE 连接中枢。
 *
 * 一个 sessionId 可以同时挂多个 SseEmitter（例如同一用户开了多个 tab，
 * 或者桌面/移动端同时在线），广播时逐个推送。
 *
 * 线程安全：
 * - 外层 map 用 ConcurrentHashMap，保证 register/unregister 的并发安全；
 * - 内层 list 用 CopyOnWriteArrayList，广播（遍历）与 register/unregister（写）互不阻塞。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiStreamHub {

    /** SSE 连接超时：30 分钟。 */
    private static final long EMITTER_TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    /**
     * 给指定 session 注册一个新的 SSE 连接，返回 emitter。
     * 调用方负责把 emitter 写到 HTTP 响应里。
     */
    public SseEmitter register(String sessionId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        CopyOnWriteArrayList<SseEmitter> list = emitters.computeIfAbsent(
                sessionId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);

        Runnable cleanup = () -> unregister(sessionId, emitter);
        emitter.onTimeout(cleanup);
        emitter.onCompletion(cleanup);
        emitter.onError(ex -> {
            log.debug("[AI][SSE] emitter error sessionId={}: {}", sessionId, ex == null ? "null" : ex.getMessage());
            cleanup.run();
        });

        log.info("[AI][SSE] registered sessionId={} totalForSession={} totalSessions={}",
                sessionId, list.size(), emitters.size());
        return emitter;
    }

    /**
     * 把 emitter 从 session 列表里移除。列表为空时把整个 session key 也清掉，避免 map 膨胀。
     */
    public void unregister(String sessionId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(sessionId);
        if (list == null) {
            return;
        }
        boolean removed = list.remove(emitter);
        if (list.isEmpty()) {
            // 防止 register 时 computeIfAbsent 又把这个空 list 加回去，先 remove 再清掉
            emitters.remove(sessionId, list);
        }
        if (removed) {
            log.info("[AI][SSE] unregistered sessionId={} remainingForSession={} totalSessions={}",
                    sessionId, list.size(), emitters.size());
        }
    }

    /**
     * 向某个 session 的所有在线 SSE 连接广播一条事件。
     * 单个 emitter 发送失败（IOException / IllegalStateException）会被摘除，其他 emitter 不受影响。
     */
    public void broadcast(String sessionId, AiStreamEvent event) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(sessionId);
        if (list == null || list.isEmpty()) {
            log.debug("[AI][SSE] broadcast skipped (no listeners) sessionId={} eventId={} type={}",
                    sessionId, event.getId(), event.getType());
            return;
        }
        String data = serialize(event);
        int sent = 0;
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event()
                        .name(event.getType())
                        .id(String.valueOf(event.getId()))
                        .data(data));
                sent++;
            } catch (IOException | IllegalStateException e) {
                log.debug("[AI][SSE] broadcast send failed sessionId={} eventId={}: {}",
                        sessionId, event.getId(), e.getMessage());
                unregister(sessionId, emitter);
            }
        }
        log.info("[AI][SSE] broadcast sessionId={} eventId={} type={} listeners={} sent={}",
                sessionId, event.getId(), event.getType(), list.size(), sent);
    }

    /** 当前 session 的在线连接数（用于监控/调试）。 */
    public int listenerCount(String sessionId) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(sessionId);
        return list == null ? 0 : list.size();
    }

    /** 把 AiStreamEvent 序列化为前端能解析的 JSON。 */
    private String serialize(AiStreamEvent event) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "id", event.getId() == null ? 0L : event.getId(),
                    "runId", event.getRunId() == null ? "" : event.getRunId(),
                    "sessionId", event.getSessionId() == null ? "" : event.getSessionId(),
                    "messageId", event.getMessageId() == null ? 0L : event.getMessageId(),
                    "type", event.getType() == null ? "" : event.getType(),
                    "payload", event.getPayloadJson() == null ? "null" : event.getPayloadJson(),
                    "createdAt", event.getCreatedAt() == null ? "" : event.getCreatedAt().toString()
            ));
        } catch (JsonProcessingException e) {
            log.warn("[AI][SSE] serialize event failed id={}: {}", event.getId(), e.getMessage());
            return "{}";
        }
    }
}
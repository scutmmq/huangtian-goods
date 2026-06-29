package com.scutmmq.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.scutmmq.ai.entity.AiMessage;
import com.scutmmq.ai.mapper.AiMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiMessageService {

    /**
     * ai_message.status 的字符串常量。
     * 流式场景：先落 STREAMING → 全部 deltas 写完后改为 COMPLETED / FAILED。
     */
    public static final String MSG_STATUS_STREAMING = "STREAMING";
    public static final String MSG_STATUS_COMPLETED = "COMPLETED";
    public static final String MSG_STATUS_FAILED = "FAILED";

    private final AiMessageMapper aiMessageMapper;

    /**
     * 兼容旧调用方：status 默认为 null。
     */
    public AiMessage append(String sessionId, String role, String content, String metadataJson) {
        return append(sessionId, role, content, null, metadataJson);
    }

    /**
     * 显式指定 status 的版本。流式输出场景下，
     * 助手消息会先以 STREAMING 落库占位，最终再 update 为 COMPLETED / FAILED。
     */
    public AiMessage append(String sessionId, String role, String content, String status, String metadataJson) {
        AiMessage message = new AiMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content == null ? "" : content);
        message.setStatus(status);
        message.setMetadataJson(metadataJson);
        message.setCreatedAt(LocalDateTime.now());
        aiMessageMapper.insert(message);
        return message;
    }

    /**
     * 直接 updateById 一条 AiMessage，常用于流式生成结束后的“占位 -> 最终内容”替换。
     * 调用方负责 setContent / setStatus / setUpdatedAt。
     */
    public void update(AiMessage message) {
        if (message == null || message.getId() == null) {
            return;
        }
        if (message.getUpdatedAt() == null) {
            message.setUpdatedAt(LocalDateTime.now());
        }
        aiMessageMapper.updateById(message);
    }

    public List<AiMessage> listBySession(String sessionId) {
        QueryWrapper<AiMessage> q = new QueryWrapper<>();
        q.eq("session_id", sessionId).orderByAsc("id");
        return aiMessageMapper.selectList(q);
    }

    public List<AiMessage> listRecentBySession(String sessionId, int limit) {
        QueryWrapper<AiMessage> q = new QueryWrapper<>();
        q.eq("session_id", sessionId)
                .orderByDesc("id")
                .last("LIMIT " + Math.max(1, limit));
        List<AiMessage> desc = aiMessageMapper.selectList(q);
        java.util.Collections.reverse(desc);
        return desc;
    }
}
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

    private final AiMessageMapper aiMessageMapper;

    public AiMessage append(String sessionId, String role, String content, String metadataJson) {
        AiMessage message = new AiMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content == null ? "" : content);
        message.setMetadataJson(metadataJson);
        message.setCreatedAt(LocalDateTime.now());
        aiMessageMapper.insert(message);
        return message;
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

package com.scutmmq.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.scutmmq.ai.entity.AiSession;
import com.scutmmq.ai.mapper.AiSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiSessionService {

    private final AiSessionMapper aiSessionMapper;

    public AiSession createSession(Long userId, String title) {
        AiSession session = new AiSession();
        session.setId(UUID.randomUUID().toString());
        session.setUserId(userId);
        session.setTitle(title == null || title.isBlank() ? "新会话" : title);
        session.setMessageCount(0);
        LocalDateTime now = LocalDateTime.now();
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        aiSessionMapper.insert(session);
        return session;
    }

    public AiSession findById(String id) {
        if (id == null) {
            return null;
        }
        return aiSessionMapper.selectById(id);
    }

    public AiSession findByIdForUser(String id, Long userId) {
        AiSession session = findById(id);
        if (session == null || !session.getUserId().equals(userId)) {
            return null;
        }
        return session;
    }

    public List<AiSession> listByUser(Long userId) {
        QueryWrapper<AiSession> q = new QueryWrapper<>();
        q.eq("user_id", userId).orderByDesc("updated_at");
        return aiSessionMapper.selectList(q);
    }

    public void touch(String sessionId, int messageDelta) {
        AiSession session = aiSessionMapper.selectById(sessionId);
        if (session == null) {
            return;
        }
        session.setMessageCount((session.getMessageCount() == null ? 0 : session.getMessageCount()) + messageDelta);
        session.setUpdatedAt(LocalDateTime.now());
        aiSessionMapper.updateById(session);
    }

    public void renameIfDefault(String sessionId, String newTitle) {
        AiSession session = aiSessionMapper.selectById(sessionId);
        if (session == null) {
            return;
        }
        if (!"新会话".equals(session.getTitle())) {
            return;
        }
        if (newTitle == null || newTitle.isBlank()) {
            return;
        }
        String trimmed = newTitle.trim();
        if (trimmed.length() > 30) {
            trimmed = trimmed.substring(0, 30);
        }
        session.setTitle(trimmed);
        aiSessionMapper.updateById(session);
    }
}

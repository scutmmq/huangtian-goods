package com.scutmmq.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scutmmq.ai.config.AiAssistantProperties;
import com.scutmmq.ai.entity.AiActionDraft;
import com.scutmmq.ai.mapper.AiActionDraftMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
public class AiActionDraftService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_FAILED = "FAILED";

    private final AiActionDraftMapper aiActionDraftMapper;
    private final AiAssistantProperties properties;
    private final ObjectMapper objectMapper;

    public AiActionDraftService(AiActionDraftMapper aiActionDraftMapper,
                                AiAssistantProperties properties,
                                ObjectMapper objectMapper) {
        this.aiActionDraftMapper = aiActionDraftMapper;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public AiActionDraft create(Long userId,
                                String sessionId,
                                String actionType,
                                String title,
                                String summary,
                                JsonNode payload) {
        AiActionDraft draft = new AiActionDraft();
        draft.setId(UUID.randomUUID().toString());
        draft.setUserId(userId);
        draft.setSessionId(sessionId);
        draft.setActionType(actionType);
        draft.setTitle(title);
        draft.setSummary(summary);
        draft.setPayloadJson(writeJson(payload));
        draft.setStatus(STATUS_PENDING);
        LocalDateTime now = LocalDateTime.now();
        draft.setCreatedAt(now);
        draft.setUpdatedAt(now);
        draft.setExpiresAt(now.plusMinutes(Math.max(1, properties.getDraftExpireMinutes())));
        aiActionDraftMapper.insert(draft);
        return draft;
    }

    public AiActionDraft findByIdForUser(String draftId, Long userId) {
        if (draftId == null) {
            return null;
        }
        AiActionDraft draft = aiActionDraftMapper.selectById(draftId);
        if (draft == null || !draft.getUserId().equals(userId)) {
            return null;
        }
        return draft;
    }

    public void markConfirmed(AiActionDraft draft, Object resultPayload) {
        draft.setStatus(STATUS_CONFIRMED);
        draft.setResultJson(writeJson(resultPayload));
        draft.setUpdatedAt(LocalDateTime.now());
        aiActionDraftMapper.updateById(draft);
    }

    public void markCancelled(AiActionDraft draft) {
        draft.setStatus(STATUS_CANCELLED);
        draft.setUpdatedAt(LocalDateTime.now());
        aiActionDraftMapper.updateById(draft);
    }

    public void markExpired(AiActionDraft draft) {
        draft.setStatus(STATUS_EXPIRED);
        draft.setUpdatedAt(LocalDateTime.now());
        aiActionDraftMapper.updateById(draft);
    }

    public void markFailed(AiActionDraft draft, String errorMessage) {
        draft.setStatus(STATUS_FAILED);
        draft.setErrorMessage(errorMessage);
        draft.setUpdatedAt(LocalDateTime.now());
        aiActionDraftMapper.updateById(draft);
    }

    public JsonNode readPayload(AiActionDraft draft) {
        if (draft == null || draft.getPayloadJson() == null) {
            return null;
        }
        try {
            return objectMapper.readTree(draft.getPayloadJson());
        } catch (Exception e) {
            log.warn("Failed to read draft payload {}: {}", draft.getId(), e.getMessage());
            return null;
        }
    }

    public boolean isExpired(AiActionDraft draft) {
        return draft != null
                && draft.getExpiresAt() != null
                && LocalDateTime.now().isAfter(draft.getExpiresAt());
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof JsonNode jn) {
                return objectMapper.writeValueAsString(jn);
            }
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Failed to serialize JSON payload: {}", e.getMessage());
            return null;
        }
    }
}

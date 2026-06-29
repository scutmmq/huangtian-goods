package com.scutmmq.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scutmmq.ai.entity.AiMessage;
import com.scutmmq.ai.tool.AgentToolResult;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

/**
 * 把 AgentOrchestrator.runStreaming 的事件落库到 ai_stream_event，并同步更新 assistant 消息的 content/status。
 *
 * <p>生命周期：每个 Run 一个实例，在 AiRunRunnable 里 new 出来，run 完后丢弃。
 * 不是 Spring bean，避免跨 Run 的状态污染。</p>
 *
 * <p>DB 写异常一律吞掉——流式生成不应因为一条事件落库失败而中断。
 * SSE 广播由 AiStreamEventService.append 内部触发，与这里解耦。</p>
 */
@Slf4j
public class PersistingOrchestratorListener implements OrchestratorListener {

    private static final String MSG_STATUS_STREAMING = AiMessageService.MSG_STATUS_STREAMING;
    private static final String MSG_STATUS_COMPLETED = AiMessageService.MSG_STATUS_COMPLETED;
    private static final String MSG_STATUS_FAILED = AiMessageService.MSG_STATUS_FAILED;

    private static final int RESULT_PREVIEW_LIMIT = 2000;
    private static final int ERROR_PREVIEW_LIMIT = 500;

    private final String runId;
    private final String sessionId;
    private final Long userId;
    @SuppressWarnings("unused")
    private final Long userMessageId;
    private final Long assistantMessageId;
    private final AiStreamEventService aiStreamEventService;
    private final AiMessageService aiMessageService;
    @SuppressWarnings("unused")
    private final AiRunService aiRunService;
    private final ObjectMapper objectMapper;

    /**
     * 累积的 assistant 文本。可能在 run 结束前被 asked，更新的就是 contentBuilder.toString()。
     */
    private final StringBuilder contentBuilder = new StringBuilder();

    /**
     * 最后一次草稿（来自 onDraftCreated 或 onRunCompleted）。一次 Run 可能有多个草稿，
     * 但 Runnable 只关心最后一个，够用。
     */
    private AgentToolResult.DraftPayload draftRef;

    private volatile boolean completed = false;
    private volatile boolean failed = false;
    private volatile String failureReason;

    /**
     * runStreaming 完成后写回的最终 reply（可能为 null，例如纯工具调用导致 reply 为空的情况）。
     * null 时回退到 contentBuilder 的累加值。
     */
    private volatile String finalReply;

    public PersistingOrchestratorListener(String runId,
                                          String sessionId,
                                          Long userId,
                                          Long userMessageId,
                                          Long assistantMessageId,
                                          AiStreamEventService aiStreamEventService,
                                          AiMessageService aiMessageService,
                                          AiRunService aiRunService,
                                          ObjectMapper objectMapper) {
        this.runId = runId;
        this.sessionId = sessionId;
        this.userId = userId;
        this.userMessageId = userMessageId;
        this.assistantMessageId = assistantMessageId;
        this.aiStreamEventService = aiStreamEventService;
        this.aiMessageService = aiMessageService;
        this.aiRunService = aiRunService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onAssistantDelta(String delta, int offset) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("delta", delta);
        payload.put("offset", offset);
        appendEvent(AiStreamEventService.TYPE_ASSISTANT_DELTA, payload);

        contentBuilder.append(delta);
        AiMessage msg = new AiMessage();
        msg.setId(assistantMessageId);
        msg.setContent(contentBuilder.toString());
        msg.setStatus(MSG_STATUS_STREAMING);
        msg.setUpdatedAt(LocalDateTime.now());
        updateMessage(msg);
    }

    @Override
    public void onToolStarted(String toolCallId, String name, JsonNode arguments) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("toolCallId", toolCallId == null ? "" : toolCallId);
        payload.put("name", name == null ? "" : name);
        if (arguments != null && !arguments.isNull()) {
            payload.set("arguments", arguments);
        }
        appendEvent(AiStreamEventService.TYPE_TOOL_STARTED, payload);
    }

    @Override
    public void onToolFinished(String toolCallId, String name, String resultContent, boolean hasDraft) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("toolCallId", toolCallId == null ? "" : toolCallId);
        payload.put("name", name == null ? "" : name);
        String preview = previewTruncated(resultContent, RESULT_PREVIEW_LIMIT);
        payload.put("resultPreview", preview);
        payload.put("hasDraft", hasDraft);
        appendEvent(AiStreamEventService.TYPE_TOOL_FINISHED, payload);
    }

    @Override
    public void onDraftCreated(AgentToolResult.DraftPayload draft) {
        if (draft == null) {
            return;
        }
        this.draftRef = draft;
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("actionType", draft.getActionType() == null ? "" : draft.getActionType());
        payload.put("title", draft.getTitle() == null ? "" : draft.getTitle());
        payload.put("summary", draft.getSummary() == null ? "" : draft.getSummary());
        if (draft.getPayload() != null) {
            payload.set("payload", draft.getPayload());
        }
        appendEvent(AiStreamEventService.TYPE_DRAFT_CREATED, payload);
    }

    @Override
    public void onRunCompleted(String reply, AgentToolResult.DraftPayload draft) {
        completed = true;
        if (draft != null) {
            this.draftRef = draft;
        }
        this.finalReply = reply;

        // 信任 orchestrator 给的完整 reply；如果 reply 为空就退回到累加的 contentBuilder。
        String finalContent = reply != null ? reply : contentBuilder.toString();
        // 防御：把累加值刷回 contentBuilder，避免后续 read getFinalContent 时拿到陈旧值。
        contentBuilder.setLength(0);
        contentBuilder.append(finalContent);

        AiMessage msg = new AiMessage();
        msg.setId(assistantMessageId);
        msg.setContent(finalContent);
        msg.setStatus(MSG_STATUS_COMPLETED);
        msg.setUpdatedAt(LocalDateTime.now());
        updateMessage(msg);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("replyLength", finalContent == null ? 0 : finalContent.length());
        payload.put("hasDraft", draft != null);
        appendEvent(AiStreamEventService.TYPE_RUN_COMPLETED, payload);
    }

    @Override
    public void onRunFailed(Throwable error) {
        failed = true;
        String reason = formatReason(error);
        this.failureReason = reason;

        String finalContent = "[生成失败] " + reason + "\n\n" + contentBuilder.toString();
        AiMessage msg = new AiMessage();
        msg.setId(assistantMessageId);
        msg.setContent(finalContent);
        msg.setStatus(MSG_STATUS_FAILED);
        msg.setUpdatedAt(LocalDateTime.now());
        updateMessage(msg);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("error", reason);
        appendEvent(AiStreamEventService.TYPE_RUN_FAILED, payload);
    }

    // ------------------------------------------------------------------
    // 供 AiRunRunnable 读状态
    // ------------------------------------------------------------------

    /**
     * 当前累计的 assistant 文本；若 onRunCompleted 已触发，使用 orchestrator 给的完整 reply。
     */
    public String getFinalContent() {
        if (finalReply != null) {
            return finalReply;
        }
        return contentBuilder.toString();
    }

    public AgentToolResult.DraftPayload getDraft() {
        return draftRef;
    }

    public boolean isCompleted() {
        return completed;
    }

    public boolean isFailed() {
        return failed;
    }

    public String getFailureReason() {
        return failureReason;
    }

    // ------------------------------------------------------------------
    // 私有辅助
    // ------------------------------------------------------------------

    private void appendEvent(String type, JsonNode payload) {
        try {
            aiStreamEventService.append(runId, sessionId, assistantMessageId, userId, type, payload);
        } catch (Exception e) {
            log.warn("[AI][PERSIST] appendEvent failed runId={} type={} reason={}",
                    runId, type, e.getMessage());
        }
    }

    private void updateMessage(AiMessage msg) {
        try {
            aiMessageService.update(msg);
        } catch (Exception e) {
            log.warn("[AI][PERSIST] updateMessage failed runId={} msgId={} reason={}",
                    runId, msg.getId(), e.getMessage());
        }
    }

    private static String previewTruncated(String value, int limit) {
        if (value == null) {
            return "";
        }
        if (value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit) + "...(truncated)";
    }

    private static String formatReason(Throwable error) {
        if (error == null) {
            return "unknown error";
        }
        String msg = error.getMessage();
        if (msg == null || msg.isEmpty()) {
            msg = error.getClass().getSimpleName();
        }
        return previewTruncated(msg, ERROR_PREVIEW_LIMIT);
    }
}

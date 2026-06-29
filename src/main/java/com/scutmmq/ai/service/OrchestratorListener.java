package com.scutmmq.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.scutmmq.ai.tool.AgentToolResult;

/**
 * AgentOrchestrator.runStreaming 的事件回调接口。
 *
 * 整个流式回合会按顺序触发：
 * 1. 若干次 onAssistantDelta（文本增量，可选 reasoning 不单独回调）
 * 2. 若有工具：onToolStarted → 执行 → onToolFinished（若有草稿还会再触发 onDraftCreated）
 * 3. 末尾收尾：onRunCompleted（成功）或 onRunFailed（失败），二者必有其一
 *
 * Task 6/7 会用此接口把事件落到 ai_stream_event 表并广播给前端 SSE。
 */
public interface OrchestratorListener {

    /**
     * assistant 文本增量。offset 是当前已发送的累计长度（方便落库时定位）。
     * 一个回合内可能回调多次，delta 顺序拼接就是最终完整 reply。
     */
    void onAssistantDelta(String delta, int offset);

    /**
     * 工具调用开始。arguments 是已解析好的 JSON（arguments 字段的累积结果已 parse 完成）。
     */
    void onToolStarted(String toolCallId, String name, JsonNode arguments);

    /**
     * 工具调用结束。resultContent 是给模型看的工具反馈文本。hasDraft 标记该工具是否生成了草稿。
     */
    void onToolFinished(String toolCallId, String name, String resultContent, boolean hasDraft);

    /**
     * 草稿被创建（来自 DRAFT_ONLY 工具）。一次回合内可能有多个草稿，各自触发一次。
     */
    void onDraftCreated(AgentToolResult.DraftPayload draft);

    /**
     * 整个 run 成功结束。reply 是最终完整文本，draft 是最后一个草稿（可能为 null）。
     */
    void onRunCompleted(String reply, AgentToolResult.DraftPayload draft);

    /**
     * 异常结束。后续不会再有其他回调。
     */
    void onRunFailed(Throwable error);
}

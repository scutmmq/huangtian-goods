package com.scutmmq.ai.service;

import com.scutmmq.ai.capability.CapabilityRegistry;
import com.scutmmq.ai.capability.RunContext;
import com.scutmmq.ai.capability.RunResult;
import com.scutmmq.ai.tool.UserRole;
import com.scutmmq.ai.client.AiChatClient;
import com.scutmmq.ai.client.StreamChunkListener;
import com.scutmmq.ai.client.ToolCallDelta;
import com.scutmmq.ai.config.AiAssistantProperties;
import com.scutmmq.ai.skill.MallSkillRegistry;
import com.scutmmq.ai.skill.MallSystemPromptProvider;
import com.scutmmq.ai.tool.AgentToolCall;
import com.scutmmq.ai.tool.AgentToolDefinition;
import com.scutmmq.ai.tool.AgentToolResult;
import com.scutmmq.ai.util.DsmlSanitizer;
import com.scutmmq.ai.util.StreamingThinkFilter;
import com.scutmmq.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 流式编排器(2026-08-23 阶段 2 抽出)。
 *
 * <p>把 AgentOrchestrator.runStreamingWithRun 中 340+ 行的流式主循环抽到这个类,
 * 让 AgentOrchestrator 回归"装配 + 入口"职责。
 *
 * <p>职责范围:
 * <ul>
 *   <li>组装 messages(system + history + user)</li>
 *   <li>主循环:流式调用 → 累积 reply/reasoning/tool_calls → 喂回模型</li>
 *   <li>C6 DSML 在源头剔除</li>
 *   <li>maxIter 兜底:不带 tools 强制一次 final text answer</li>
 *   <li>异常路径:catch → 返回兜底回复 + capability RunCompleted(terminal)</li>
 *   <li>capability 事件:RunStarted / RunCompleted</li>
 *   <li>listener 回调:AssistantDelta / ToolStarted/ToolFinished/DraftCreated(由 dispatcher 转交)
 *       / RunCompleted / RunFailed</li>
 * </ul>
 *
 * <p>不负责:
 * <ul>
 *   <li>tool_call arguments 累积(交给 {@link ToolCallAccumulator})</li>
 *   <li>OpenAI 消息格式(交给 {@link ModelMessageBuilder})</li>
 *   <li>工具执行循环 + 权限(交给 {@link ToolExecutionDispatcher})</li>
 * </ul>
 */
@Slf4j
public class StreamingOrchestrator {

    private final AiChatClient aiChatClient;
    private final MallSkillRegistry skillRegistry;
    private final MallSystemPromptProvider promptProvider;
    private final AiAssistantProperties assistantProperties;
    private final CapabilityRegistry capabilityRegistry;
    private final ToolCallAccumulator toolCallAccumulator;
    private final ModelMessageBuilder messageBuilder;
    private final ToolExecutionDispatcher toolDispatcher;

    public StreamingOrchestrator(AiChatClient aiChatClient,
                                  MallSkillRegistry skillRegistry,
                                  MallSystemPromptProvider promptProvider,
                                  AiAssistantProperties assistantProperties,
                                  CapabilityRegistry capabilityRegistry,
                                  ToolCallAccumulator toolCallAccumulator,
                                  ModelMessageBuilder messageBuilder,
                                  ToolExecutionDispatcher toolDispatcher) {
        this.aiChatClient = aiChatClient;
        this.skillRegistry = skillRegistry;
        this.promptProvider = promptProvider;
        this.assistantProperties = assistantProperties;
        this.capabilityRegistry = capabilityRegistry;
        this.toolCallAccumulator = toolCallAccumulator;
        this.messageBuilder = messageBuilder;
        this.toolDispatcher = toolDispatcher;
    }

    /**
     * 流式运行一次完整对话。
     *
     * @param currentUser 当前商城登录用户
     * @param history     会话历史(不含本次用户消息)
     * @param userMessage       本次用户输入
     * @param listener          事件回调
     * @param runId             关联 ai_run.id(可能为 null)
     * @param sessionId         关联 ai_session.id(可能为 null)
     * @param currentMerchantId 当前会话所在商家 ID(多租户 RAG 隔离,可为 null)
     */
    public AgentOrchestrator.AgentResult runStreaming(UserDTO currentUser,
                                                       List<AgentOrchestrator.HistoryMessage> history,
                                                       String userMessage,
                                                       OrchestratorListener listener,
                                                       String runId,
                                                       String sessionId,
                                                       Long currentMerchantId) {
        List<Map<String, Object>> messages = assembleMessages(currentUser, history, userMessage, currentMerchantId);
        List<AgentToolDefinition> tools = skillRegistry.listDefinitions();
        List<AgentOrchestrator.ToolExecutionRecord> executions = new ArrayList<>();
        AgentToolResult.DraftPayload draft = null;
        final String[] replyRef = {""};

        int maxIter = Math.max(1, assistantProperties.getMaxToolIterations());
        final long runStartMs = System.currentTimeMillis();
        final long ttftMsHolder[] = {-1L};
        log.info("[AI][ORCH][STREAM] runStreaming() begin user={} historyCount={} tools={} maxIter={} userMsg=\"{}\"",
                currentUser == null ? null : currentUser.getId(),
                history.size(),
                tools.size(),
                maxIter,
                preview(userMessage, 120));

        // B2:发布 RunStartedEvent
        RunContext runCtx = RunContext.of(
                runId == null ? "local-" + runStartMs : runId,
                sessionId,
                currentUser == null ? null : currentUser.getId(),
                currentUser == null || currentUser.getRole() == null ? UserRole.USER.name() : currentUser.getRole());
        capabilityRegistry.publishRunStarted(runCtx);
        RunResult runResult = RunResult.builder()
                .context(runCtx)
                .replyPreview("")
                .hasDraft(false)
                .toolExecutionCount(0)
                .totalMs(0L)
                .ttftMs(0L)
                .terminal(false)
                .build();

        boolean stillWantsTools = false;
        try {
            for (int iter = 0; iter < maxIter; iter++) {
                log.info("[AI][ORCH][STREAM] ---- iteration {}/{} : sending {} messages to model ----",
                        iter + 1, maxIter, messages.size());
                long t0 = System.currentTimeMillis();

                StringBuilder replyBuilder = new StringBuilder();
                StringBuilder reasoningBuilder = new StringBuilder();
                List<AgentToolCall> toolCalls = new ArrayList<>();
                AtomicBoolean streamFailed = new AtomicBoolean(false);
                StreamingThinkFilter thinkFilter = new StreamingThinkFilter();

                aiChatClient.streamChatCompletion(messages, tools, new StreamChunkListener() {
                    @Override
                    public void onContentDelta(String delta) {
                        if (delta == null || delta.isEmpty()) return;
                        // 过滤 think 标签与 DSML 标签,让 replyBuilder / safeOnAssistantDelta listener 全干净
                        String clean = thinkFilter.filter(delta, reasoningBuilder);
                        if (clean.isEmpty()) {
                            return;
                        }
                        replyBuilder.append(clean);
                        safeOnAssistantDelta(listener, clean, replyBuilder.length() - clean.length());
                        if (ttftMsHolder[0] < 0) {
                            ttftMsHolder[0] = System.currentTimeMillis() - runStartMs;
                        }
                    }

                    @Override
                    public void onReasoningDelta(String delta) {
                        if (delta == null || delta.isEmpty()) return;
                        reasoningBuilder.append(delta);
                    }

                    @Override
                    public void onToolCallDelta(List<ToolCallDelta> deltas) {
                        if (deltas == null || deltas.isEmpty()) return;
                        for (ToolCallDelta d : deltas) {
                            toolCallAccumulator.mergeDelta(toolCalls, d);
                        }
                    }

                    @Override public void onComplete() { /* no-op */ }

                    @Override
                    public void onError(Throwable error) {
                        streamFailed.set(true);
                        log.warn("[AI][ORCH][STREAM] stream reported error: {}", error.getMessage());
                    }
                });

                if (streamFailed.get()) {
                    throw new RuntimeException("AI stream failed (see previous log)");
                }

                String reply = DsmlSanitizer.strip(replyBuilder.toString());
                String reasoning = reasoningBuilder.toString();
                log.info("[AI][ORCH][STREAM] model iteration done in {}ms: contentLen={} toolCalls={} hasReasoning={}",
                        System.currentTimeMillis() - t0,
                        reply.length(),
                        toolCalls.size(),
                        !reasoning.isEmpty());

                if (toolCalls.isEmpty()) {
                    log.info("[AI][ORCH][STREAM] no tool_calls -> final answer. preview=\"{}\"",
                            preview(reply, 200));
                    replyRef[0] = reply;
                    stillWantsTools = false;
                    break;
                }

                // 确保所有有效工具调用都有唯一且一致的 id，并过滤非法空调用
                toolCalls.removeIf(tc -> tc.getName() == null || tc.getName().isBlank());
                for (AgentToolCall call : toolCalls) {
                    if (call.getId() == null || call.getId().isBlank()) {
                        call.setId("call_" + System.nanoTime());
                    }
                }

                // 追加 assistant 消息(走 ModelMessageBuilder)
                messages.add(messageBuilder.buildAssistantToolCallMessage(reply, toolCalls,
                        reasoning.isEmpty() ? null : reasoning));

                // 执行调度并追加 tool 响应消息
                ToolExecutionDispatcher.ExecutionResult dispatchResult =
                        toolDispatcher.dispatch(toolCalls, listener, currentUser, runCtx);

                for (ToolExecutionDispatcher.ExecutionRecord er : dispatchResult.records()) {
                    executions.add(new AgentOrchestrator.ToolExecutionRecord(
                            er.call().getName(), er.call().getArguments(), er.result().getContent()));
                    messages.add(messageBuilder.buildToolResponseMessage(
                            er.call().getId(), er.call().getName(), er.result().getContent()));
                }
                if (dispatchResult.draft() != null) {
                    draft = dispatchResult.draft();
                }

                stillWantsTools = (iter == maxIter - 1);
            }

            // 兜底:maxIter 用尽,强制一次不带 tools 的流式取最终文本
            if (stillWantsTools) {
                log.warn("[AI][ORCH][STREAM] reached maxIter={} with pending tool flow, forcing final text answer", maxIter);
                StringBuilder forcedReply = new StringBuilder();
                AtomicBoolean forcedFailed = new AtomicBoolean(false);
                StreamingThinkFilter forcedThinkFilter = new StreamingThinkFilter();
                try {
                    aiChatClient.streamChatCompletion(messages, List.of(), new StreamChunkListener() {
                        @Override
                        public void onContentDelta(String delta) {
                            if (delta == null || delta.isEmpty()) return;
                            String clean = forcedThinkFilter.filter(delta, null);
                            if (clean.isEmpty()) return;
                            forcedReply.append(clean);
                            safeOnAssistantDelta(listener, clean, forcedReply.length() - clean.length());
                        }
                        @Override public void onReasoningDelta(String delta) { /* 强制收敛不外送 */ }
                        @Override public void onToolCallDelta(List<ToolCallDelta> deltas) { /* 强制收敛不允许调工具 */ }
                        @Override public void onComplete() {}
                        @Override
                        public void onError(Throwable error) {
                            forcedFailed.set(true);
                        }
                    });
                    if (!forcedFailed.get()) {
                        String forced = DsmlSanitizer.strip(forcedReply.toString());
                        if (!forced.isEmpty()) {
                            replyRef[0] = forced;
                        }
                    }
                } catch (Exception e) {
                    log.error("[AI][ORCH][STREAM] forced final call failed: {}", e.getMessage(), e);
                }
                if (replyRef[0].isEmpty()) {
                    replyRef[0] = "我这边查了几轮还没能整理出一个完整答案。要不你换一种方式描述一下需求?";
                }
            }

            long totalMs = System.currentTimeMillis() - runStartMs;
            log.info("[AI][ORCH][STREAM] runStreaming() done. toolExecutions={} draft={} finalReplyLen={} totalMs={}",
                    executions.size(),
                    draft == null ? "none" : draft.getActionType(),
                    replyRef[0].length(),
                    totalMs);
            safeOnRunCompleted(listener, replyRef[0], draft);

            runResult.setReplyPreview(safeTruncate(replyRef[0], 200));
            runResult.setHasDraft(draft != null);
            runResult.setToolExecutionCount(executions.size());
            runResult.setTotalMs(totalMs);
            runResult.setTtftMs(ttftMsHolder[0] < 0 ? totalMs : ttftMsHolder[0]);
            capabilityRegistry.publishRunCompleted(runResult);
            return new AgentOrchestrator.AgentResult(replyRef[0], draft, executions);

        } catch (Exception e) {
            log.error("[AI][ORCH][STREAM] runStreaming failed: {}", e.getMessage(), e);
            safeOnRunFailed(listener, e);
            runResult.setReplyPreview("failed");
            runResult.setTotalMs(System.currentTimeMillis() - runStartMs);
            capabilityRegistry.publishRunCompleted(runResult);
            return new AgentOrchestrator.AgentResult(
                    replyRef[0] == null || replyRef[0].isEmpty()
                            ? "抱歉，AI 这次没给到回复。" : replyRef[0],
                    draft, executions);
        }
    }

    private List<Map<String, Object>> assembleMessages(UserDTO currentUser,
                                                     List<AgentOrchestrator.HistoryMessage> history,
                                                     String userMessage,
                                                     Long currentMerchantId) {
        List<Map<String, Object>> messages = new ArrayList<>();
        // B4 Phase 1.6:三参 buildSystemPrompt 支持多租户 RAG 商家上下文隔离
        messages.add(Map.of("role", "system", "content", promptProvider.buildSystemPrompt(currentUser, userMessage, currentMerchantId)));
        for (AgentOrchestrator.HistoryMessage msg : history) {
            messages.add(Map.of("role", msg.role(), "content", msg.content() == null ? "" : msg.content()));
        }
        messages.add(Map.of("role", "user", "content", userMessage == null ? "" : userMessage));
        return messages;
    }

    private void safeOnAssistantDelta(OrchestratorListener listener, String delta, int offset) {
        try {
            listener.onAssistantDelta(delta, offset);
        } catch (Exception e) {
            log.warn("[AI][ORCH][STREAM] listener.onAssistantDelta threw: {}", e.getMessage(), e);
        }
    }

    private void safeOnRunCompleted(OrchestratorListener listener, String reply, AgentToolResult.DraftPayload draft) {
        try {
            listener.onRunCompleted(reply, draft);
        } catch (Exception e) {
            log.warn("[AI][ORCH][STREAM] listener.onRunCompleted threw: {}", e.getMessage(), e);
        }
    }

    private void safeOnRunFailed(OrchestratorListener listener, Throwable err) {
        try {
            listener.onRunFailed(err);
        } catch (Exception e) {
            log.warn("[AI][ORCH][STREAM] listener.onRunFailed threw: {}", e.getMessage(), e);
        }
    }

    private static String preview(String s, int max) {
        if (s == null) return "";
        String t = s.replace("\n", " ").replace("\r", " ");
        return t.length() <= max ? t : t.substring(0, max) + "...";
    }

    private static String safeTruncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}

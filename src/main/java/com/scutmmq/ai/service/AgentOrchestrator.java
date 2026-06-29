package com.scutmmq.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scutmmq.ai.client.AiChatClient;
import com.scutmmq.ai.client.StreamChunkListener;
import com.scutmmq.ai.client.ToolCallDelta;
import com.scutmmq.ai.config.AiAssistantProperties;
import com.scutmmq.ai.skill.MallSkillRegistry;
import com.scutmmq.ai.skill.MallSystemPromptProvider;
import com.scutmmq.ai.tool.AgentToolCall;
import com.scutmmq.ai.tool.AgentToolDefinition;
import com.scutmmq.ai.tool.AgentToolResult;
import com.scutmmq.ai.tool.MallAgentTool;
import com.scutmmq.ai.util.MallUserContextExecutor;
import com.scutmmq.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 负责一次完整的 AI 对话：装配系统提示词 + 历史 + 工具定义；
 * 调用模型；如果模型返回 tool_calls 就执行工具，把工具输出反喂给模型再次推理，
 * 循环直到产出自然语言回复或达到最大迭代次数。
 */
@Slf4j
@Service
public class AgentOrchestrator {

    private final AiChatClient aiChatClient;
    private final MallSkillRegistry skillRegistry;
    private final MallSystemPromptProvider promptProvider;
    private final AiAssistantProperties assistantProperties;
    private final ObjectMapper objectMapper;

    public AgentOrchestrator(AiChatClient aiChatClient,
                             MallSkillRegistry skillRegistry,
                             MallSystemPromptProvider promptProvider,
                             AiAssistantProperties assistantProperties,
                             ObjectMapper objectMapper) {
        this.aiChatClient = aiChatClient;
        this.skillRegistry = skillRegistry;
        this.promptProvider = promptProvider;
        this.assistantProperties = assistantProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 同步运行一次完整对话。
     *
     * @param currentUser    当前商城登录用户
     * @param history        会话历史（不含本次用户消息），顺序由旧到新
     * @param userMessage    本次用户输入
     * @return Agent 最终响应：含自然语言回复、可选草稿、本次产生的工具调用记录
     */
    public AgentResult run(UserDTO currentUser, List<HistoryMessage> history, String userMessage) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", promptProvider.buildSystemPrompt(currentUser)));
        for (HistoryMessage msg : history) {
            messages.add(Map.of("role", msg.role(), "content", msg.content() == null ? "" : msg.content()));
        }
        messages.add(Map.of("role", "user", "content", userMessage == null ? "" : userMessage));

        List<AgentToolDefinition> tools = skillRegistry.listDefinitions();
        List<ToolExecutionRecord> executions = new ArrayList<>();
        AgentToolResult.DraftPayload draft = null;
        String reply = "";

        int maxIter = Math.max(1, assistantProperties.getMaxToolIterations());
        log.info("[AI][ORCH] run() begin user={} historyCount={} tools={} maxIter={} userMsg=\"{}\"",
                currentUser == null ? null : currentUser.getId(),
                history.size(),
                tools.size(),
                maxIter,
                preview(userMessage, 120));

        boolean stillWantsTools = false;
        for (int iter = 0; iter < maxIter; iter++) {
            log.info("[AI][ORCH] ---- iteration {}/{} : sending {} messages to model ----",
                    iter + 1, maxIter, messages.size());
            long t0 = System.currentTimeMillis();
            AiChatClient.ChatCompletionResult result = aiChatClient.chatCompletion(messages, tools);
            long elapsed = System.currentTimeMillis() - t0;
            reply = result.getContent();
            log.info("[AI][ORCH] model returned in {}ms: contentLen={} toolCalls={} hasReasoning={}",
                    elapsed,
                    reply == null ? 0 : reply.length(),
                    result.getToolCalls().size(),
                    result.getReasoningContent() != null && !result.getReasoningContent().isEmpty());

            if (result.getToolCalls().isEmpty()) {
                log.info("[AI][ORCH] no tool_calls -> treat as final answer. reply preview=\"{}\"",
                        preview(reply, 200));
                stillWantsTools = false;
                break;
            }

            // 追加一条 assistant 消息（带 tool_calls + 可选的 reasoning_content）
            messages.add(buildAssistantToolCallMessage(reply, result.getToolCalls(), result.getReasoningContent()));

            // 执行每个工具
            for (AgentToolCall call : result.getToolCalls()) {
                log.info("[AI][ORCH] tool_call -> name={} id={} args={}",
                        call.getName(), call.getId(), call.getArguments());
                MallAgentTool tool = skillRegistry.findByName(call.getName());
                AgentToolResult toolResult;
                if (tool == null) {
                    log.warn("[AI][ORCH] unknown tool requested by model: {}", call.getName());
                    toolResult = AgentToolResult.ofText("工具不存在: " + call.getName());
                } else {
                    long tt0 = System.currentTimeMillis();
                    toolResult = safeExecute(tool, call.getArguments(), currentUser);
                    log.info("[AI][ORCH] tool {} executed in {}ms mode={} resultPreview=\"{}\" draft={}",
                            call.getName(),
                            System.currentTimeMillis() - tt0,
                            tool.mode(),
                            preview(toolResult.getContent(), 200),
                            toolResult.getDraft() == null ? "none" : toolResult.getDraft().getActionType());
                }

                executions.add(new ToolExecutionRecord(call.getName(), call.getArguments(), toolResult.getContent()));
                if (toolResult.getDraft() != null) {
                    draft = toolResult.getDraft();
                }

                messages.add(buildToolResponseMessage(call.getId(), call.getName(), toolResult.getContent()));
            }

            // 如果是最后一次循环还在请求工具，说明 maxIter 不够。标记下来，循环外强制再喊一次。
            stillWantsTools = (iter == maxIter - 1);
        }

        // 兜底：循环到上限了模型还在要工具，说明它没机会把工具结果整理成自然语言。
        // 这里强制再请求一次，且不再带 tools，逼模型给出文本最终回答，避免前端看到半截话。
        if (stillWantsTools) {
            log.warn("[AI][ORCH] reached maxIter={} with pending tool flow, forcing final text answer", maxIter);
            try {
                AiChatClient.ChatCompletionResult forced = aiChatClient.chatCompletion(messages, List.of());
                if (forced.getContent() != null && !forced.getContent().isEmpty()) {
                    reply = forced.getContent();
                    log.info("[AI][ORCH] forced final reply len={} preview=\"{}\"",
                            reply.length(), preview(reply, 200));
                } else if (reply == null || reply.isEmpty()) {
                    reply = "我这边查了几轮还没能整理出一个完整答案。要不你换一种方式描述一下需求？";
                }
            } catch (Exception e) {
                log.error("[AI][ORCH] forced final call failed: {}", e.getMessage(), e);
                if (reply == null || reply.isEmpty()) {
                    reply = "我这边查了几轮还没能整理出一个完整答案。要不你换一种方式描述一下需求？";
                }
            }
        }

        log.info("[AI][ORCH] run() done. toolExecutions={} draft={} finalReplyLen={}",
                executions.size(),
                draft == null ? "none" : draft.getActionType(),
                reply == null ? 0 : reply.length());
        return new AgentResult(reply, draft, executions);
    }

    /**
     * 流式运行 AI 回合。监听 listener 接收事件。
     * 保留现有 run(user, history, userMessage) 同步方法不变（降级路径）。
     *
     * @param currentUser 当前用户
     * @param history     会话历史
     * @param userMessage 本次用户输入
     * @param listener    事件接收器；不抛异常到外层
     * @return AgentResult（同步结果，供调用方需要时使用；listener 已经收到事件了）
     */
    public AgentResult runStreaming(UserDTO currentUser,
                                    List<HistoryMessage> history,
                                    String userMessage,
                                    OrchestratorListener listener) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", promptProvider.buildSystemPrompt(currentUser)));
        for (HistoryMessage msg : history) {
            messages.add(Map.of("role", msg.role(), "content", msg.content() == null ? "" : msg.content()));
        }
        messages.add(Map.of("role", "user", "content", userMessage == null ? "" : userMessage));

        List<AgentToolDefinition> tools = skillRegistry.listDefinitions();
        List<ToolExecutionRecord> executions = new ArrayList<>();
        AgentToolResult.DraftPayload draft = null;
        final String[] replyRef = {""};

        int maxIter = Math.max(1, assistantProperties.getMaxToolIterations());
        log.info("[AI][ORCH][STREAM] runStreaming() begin user={} historyCount={} tools={} maxIter={} userMsg=\"{}\"",
                currentUser == null ? null : currentUser.getId(),
                history.size(),
                tools.size(),
                maxIter,
                preview(userMessage, 120));

        boolean stillWantsTools = false;
        try {
            for (int iter = 0; iter < maxIter; iter++) {
                log.info("[AI][ORCH][STREAM] ---- iteration {}/{} : sending {} messages to model ----",
                        iter + 1, maxIter, messages.size());
                long t0 = System.currentTimeMillis();

                // 每个迭代各自累积
                StringBuilder replyBuilder = new StringBuilder();
                StringBuilder reasoningBuilder = new StringBuilder();
                List<AgentToolCall> toolCalls = new ArrayList<>();
                AtomicBoolean streamFailed = new AtomicBoolean(false);

                aiChatClient.streamChatCompletion(messages, tools, new StreamChunkListener() {
                    @Override
                    public void onContentDelta(String delta) {
                        if (delta == null || delta.isEmpty()) return;
                        replyBuilder.append(delta);
                        safeOnAssistantDelta(listener, delta, replyBuilder.length() - delta.length());
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
                            mergeToolCallDelta(toolCalls, d);
                        }
                    }

                    @Override
                    public void onComplete() {
                        // no-op: 由外层循环统一处理
                    }

                    @Override
                    public void onError(Throwable error) {
                        streamFailed.set(true);
                        // 不直接回调 onRunFailed，留给外层统一处理（保持位置清晰）
                        log.warn("[AI][ORCH][STREAM] stream reported error: {}", error.getMessage());
                    }
                });

                if (streamFailed.get()) {
                    throw new RuntimeException("AI stream failed (see previous log)");
                }

                String reply = replyBuilder.toString();
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

                // 追加 assistant 消息（带 tool_calls + reasoning_content）
                messages.add(buildAssistantToolCallMessage(reply, toolCalls,
                        reasoning.isEmpty() ? null : reasoning));

                // 顺序执行工具
                for (AgentToolCall call : toolCalls) {
                    log.info("[AI][ORCH][STREAM] tool_call -> name={} id={} args={}",
                            call.getName(), call.getId(), call.getArguments());
                    safeOnToolStarted(listener, call.getId(), call.getName(), call.getArguments());

                    MallAgentTool tool = skillRegistry.findByName(call.getName());
                    AgentToolResult toolResult;
                    if (tool == null) {
                        log.warn("[AI][ORCH][STREAM] unknown tool requested by model: {}", call.getName());
                        toolResult = AgentToolResult.ofText("工具不存在: " + call.getName());
                    } else {
                        long tt0 = System.currentTimeMillis();
                        toolResult = safeExecute(tool, call.getArguments(), currentUser);
                        log.info("[AI][ORCH][STREAM] tool {} executed in {}ms mode={} resultPreview=\"{}\" draft={}",
                                call.getName(),
                                System.currentTimeMillis() - tt0,
                                tool.mode(),
                                preview(toolResult.getContent(), 200),
                                toolResult.getDraft() == null ? "none" : toolResult.getDraft().getActionType());
                    }

                    executions.add(new ToolExecutionRecord(call.getName(), call.getArguments(), toolResult.getContent()));
                    if (toolResult.getDraft() != null) {
                        draft = toolResult.getDraft();
                        safeOnDraftCreated(listener, draft);
                    }
                    safeOnToolFinished(listener, call.getId(), call.getName(), toolResult.getContent(),
                            toolResult.getDraft() != null);

                    messages.add(buildToolResponseMessage(call.getId(), call.getName(), toolResult.getContent()));
                }

                stillWantsTools = (iter == maxIter - 1);
            }

            // 兜底：循环到上限还在要工具时强制一次不带 tools 的流式取最终文本
            if (stillWantsTools) {
                log.warn("[AI][ORCH][STREAM] reached maxIter={} with pending tool flow, forcing final text answer", maxIter);
                StringBuilder forcedReply = new StringBuilder();
                AtomicBoolean forcedFailed = new AtomicBoolean(false);
                try {
                    aiChatClient.streamChatCompletion(messages, List.of(), new StreamChunkListener() {
                        @Override
                        public void onContentDelta(String delta) {
                            if (delta == null || delta.isEmpty()) return;
                            forcedReply.append(delta);
                            safeOnAssistantDelta(listener, delta, forcedReply.length() - delta.length());
                        }

                        @Override
                        public void onReasoningDelta(String delta) {
                            // 强制收敛过程不外送 reasoning
                        }

                        @Override
                        public void onToolCallDelta(List<ToolCallDelta> deltas) {
                            // 强制收敛不允许再调用工具
                        }

                        @Override
                        public void onComplete() {
                        }

                        @Override
                        public void onError(Throwable error) {
                            forcedFailed.set(true);
                        }
                    });
                    if (!forcedFailed.get()) {
                        String forced = forcedReply.toString();
                        if (!forced.isEmpty()) {
                            replyRef[0] = forced;
                        }
                    }
                } catch (Exception e) {
                    log.error("[AI][ORCH][STREAM] forced final call failed: {}", e.getMessage(), e);
                }
                if (replyRef[0].isEmpty()) {
                    replyRef[0] = "我这边查了几轮还没能整理出一个完整答案。要不你换一种方式描述一下需求？";
                }
            }

            log.info("[AI][ORCH][STREAM] runStreaming() done. toolExecutions={} draft={} finalReplyLen={}",
                    executions.size(),
                    draft == null ? "none" : draft.getActionType(),
                    replyRef[0].length());
            safeOnRunCompleted(listener, replyRef[0], draft);
            return new AgentResult(replyRef[0], draft, executions);

        } catch (Exception e) {
            log.error("[AI][ORCH][STREAM] runStreaming failed: {}", e.getMessage(), e);
            safeOnRunFailed(listener, e);
            return new AgentResult(
                    replyRef[0] == null || replyRef[0].isEmpty()
                            ? "抱歉，AI 这次没给到回复。" : replyRef[0],
                    draft, executions);
        }
    }

    /**
     * 把一个 stream 来的 ToolCallDelta 合并进 toolCalls 列表。
     * 按 index 分组：首次出现就新建，后续则把 id/name 补齐 + arguments 字符串拼接。
     */
    private void mergeToolCallDelta(List<AgentToolCall> toolCalls, ToolCallDelta d) {
        if (d == null) return;
        AgentToolCall existing = null;
        for (AgentToolCall tc : toolCalls) {
            if (tc.getId() != null && !tc.getId().isEmpty()
                    && d.getId() != null && !d.getId().isEmpty()
                    && tc.getId().equals(d.getId())) {
                existing = tc;
                break;
            }
        }
        if (existing == null) {
            // 按 index 兜底：在还没有 id 的情况下用 index 匹配
            if (d.getIndex() < toolCalls.size()) {
                AgentToolCall slot = toolCalls.get(d.getIndex());
                if (slot.getId() == null || slot.getId().isEmpty()) {
                    existing = slot;
                }
            }
        }

        if (existing == null) {
            // 新建一条
            String id = d.getId() == null ? "" : d.getId();
            String name = d.getName() == null ? "" : d.getName();
            JsonNode argsNode = parseArgumentsSafely(d.getArgumentsDelta());
            AgentToolCall fresh = new AgentToolCall(id, name, argsNode);
            // 确保 id 尚未被别的占用
            for (AgentToolCall tc : toolCalls) {
                if (id.equals(tc.getId())) {
                    // 已存在的同 id 条目，跳过新建
                    existing = tc;
                    break;
                }
            }
            if (existing == null) {
                toolCalls.add(fresh);
                existing = fresh;
            }
        }

        // 补齐 id/name
        if ((existing.getId() == null || existing.getId().isEmpty()) && d.getId() != null && !d.getId().isEmpty()) {
            existing.setId(d.getId());
        }
        if ((existing.getName() == null || existing.getName().isEmpty()) && d.getName() != null && !d.getName().isEmpty()) {
            existing.setName(d.getName());
        }
        // 追加 arguments 增量（合并到现有 JsonNode 里）
        if (d.getArgumentsDelta() != null && !d.getArgumentsDelta().isEmpty()) {
            JsonNode merged = appendArguments(existing.getArguments(), d.getArgumentsDelta());
            existing.setArguments(merged);
        }
    }

    private JsonNode parseArgumentsSafely(String raw) {
        if (raw == null || raw.isEmpty()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode n = objectMapper.readTree(raw);
            if (n != null) {
                return n;
            }
        } catch (Exception ignored) {
            // 还在累积中，不是完整 JSON
        }
        // 仍在累积：用 raw 字符串保存到 JsonNode 里，方便后续 appendArguments 处理
        ObjectNode node = objectMapper.createObjectNode();
        node.put("__streaming__", raw);
        return node;
    }

    private JsonNode appendArguments(JsonNode current, String delta) {
        if (delta == null || delta.isEmpty()) return current;
        try {
            if (current != null && current.isObject() && current.has("__streaming__")) {
                String prev = current.get("__streaming__").asText("");
                String merged = prev + delta;
                JsonNode n = objectMapper.readTree(merged);
                if (n != null) {
                    return n;
                }
                ObjectNode node = objectMapper.createObjectNode();
                node.put("__streaming__", merged);
                return node;
            }
            if (current != null && current.isObject() && current.size() > 0) {
                // current 已经是完整 JSON —— 合并进来再 parse 一次
                String existing = current.toString();
                JsonNode n = objectMapper.readTree(existing + delta);
                if (n != null) {
                    return n;
                }
            }
        } catch (Exception ignored) {
            // 还没凑齐 JSON
        }
        ObjectNode node = objectMapper.createObjectNode();
        node.put("__streaming__", (current == null ? "" : current.toString()) + delta);
        return node;
    }

    private void safeOnAssistantDelta(OrchestratorListener listener, String delta, int offset) {
        try {
            listener.onAssistantDelta(delta, offset);
        } catch (Exception e) {
            log.warn("[AI][ORCH][STREAM] listener.onAssistantDelta threw: {}", e.getMessage(), e);
        }
    }

    private void safeOnToolStarted(OrchestratorListener listener, String id, String name, JsonNode args) {
        try {
            listener.onToolStarted(id, name, args);
        } catch (Exception e) {
            log.warn("[AI][ORCH][STREAM] listener.onToolStarted threw: {}", e.getMessage(), e);
        }
    }

    private void safeOnToolFinished(OrchestratorListener listener, String id, String name,
                                    String content, boolean hasDraft) {
        try {
            listener.onToolFinished(id, name, content, hasDraft);
        } catch (Exception e) {
            log.warn("[AI][ORCH][STREAM] listener.onToolFinished threw: {}", e.getMessage(), e);
        }
    }

    private void safeOnDraftCreated(OrchestratorListener listener, AgentToolResult.DraftPayload draft) {
        try {
            listener.onDraftCreated(draft);
        } catch (Exception e) {
            log.warn("[AI][ORCH][STREAM] listener.onDraftCreated threw: {}", e.getMessage(), e);
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

    private AgentToolResult safeExecute(MallAgentTool tool, JsonNode arguments, UserDTO currentUser) {
        try {
            return MallUserContextExecutor.runAs(currentUser, () -> tool.execute(arguments));
        } catch (Exception e) {
            log.warn("[AI][ORCH] tool {} execution failed: {}", tool.name(), e.getMessage(), e);
            return AgentToolResult.ofText("工具执行失败: " + e.getMessage());
        }
    }

    private static String preview(String s, int max) {
        if (s == null) return "";
        String t = s.replace("\n", " ").replace("\r", " ");
        return t.length() <= max ? t : t.substring(0, max) + "...";
    }

    private Map<String, Object> buildAssistantToolCallMessage(String content,
                                                               List<AgentToolCall> toolCalls,
                                                               String reasoningContent) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", content == null ? "" : content);
        // DeepSeek thinking 模式：必须原样把 reasoning_content 送回去，否则下一轮 400。
        if (reasoningContent != null && !reasoningContent.isEmpty()) {
            message.put("reasoning_content", reasoningContent);
        }
        List<Map<String, Object>> openAiToolCalls = new ArrayList<>();
        for (AgentToolCall call : toolCalls) {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", call.getName());
            function.put("arguments", argumentsAsString(call.getArguments()));

            Map<String, Object> wrapped = new LinkedHashMap<>();
            wrapped.put("id", call.getId());
            wrapped.put("type", "function");
            wrapped.put("function", function);
            openAiToolCalls.add(wrapped);
        }
        message.put("tool_calls", openAiToolCalls);
        return message;
    }

    private Map<String, Object> buildToolResponseMessage(String toolCallId, String toolName, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "tool");
        message.put("tool_call_id", toolCallId);
        message.put("name", toolName);
        message.put("content", content == null ? "" : content);
        return message;
    }

    private String argumentsAsString(JsonNode arguments) {
        try {
            return objectMapper.writeValueAsString(arguments == null ? objectMapper.createObjectNode() : arguments);
        } catch (Exception e) {
            return "{}";
        }
    }

    public record HistoryMessage(String role, String content) {
    }

    public record ToolExecutionRecord(String name, JsonNode arguments, String content) {
    }

    public record AgentResult(String reply,
                              AgentToolResult.DraftPayload draft,
                              List<ToolExecutionRecord> toolExecutions) {
    }
}

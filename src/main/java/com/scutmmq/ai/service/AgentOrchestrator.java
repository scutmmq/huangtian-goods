package com.scutmmq.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scutmmq.ai.capability.CapabilityRegistry;
import com.scutmmq.ai.capability.RunContext;
import com.scutmmq.ai.capability.RunResult;
import com.scutmmq.ai.capability.ToolContext;
import com.scutmmq.ai.client.AiChatClient;
import com.scutmmq.ai.client.StreamChunkListener;
import com.scutmmq.ai.client.ToolCallDelta;
import com.scutmmq.ai.config.AiAssistantProperties;
import com.scutmmq.ai.security.ToolAccessDeniedException;
import com.scutmmq.ai.security.ToolSecurityInterceptor;
import com.scutmmq.ai.skill.MallSkillRegistry;
import com.scutmmq.ai.skill.MallSystemPromptProvider;
import com.scutmmq.ai.tool.AgentToolCall;
import com.scutmmq.ai.tool.AgentToolDefinition;
import com.scutmmq.ai.tool.AgentToolResult;
import com.scutmmq.ai.tool.MallAgentTool;
import com.scutmmq.ai.tool.UserRole;
import com.scutmmq.ai.util.DsmlSanitizer;
import com.scutmmq.ai.util.MallUserContextExecutor;
import com.scutmmq.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
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

    /**
     * 单测里通过反射调 {@code AgentOrchestrator.appendArguments(...)} 等静态助手方法。
     * 实际逻辑现在在 ToolCallAccumulator 里,这里保留一个共享实例作为委托,
     * 这样单测不需要改也能通过。新代码请直接构造 ToolCallAccumulator。
     */
    private static final ToolCallAccumulator STATIC_ACCUMULATOR =
            new ToolCallAccumulator(new ObjectMapper());

    private final AiChatClient aiChatClient;
    private final MallSkillRegistry skillRegistry;
    private final MallSystemPromptProvider promptProvider;
    private final AiAssistantProperties assistantProperties;
    private final ObjectMapper objectMapper;
    private final CapabilityRegistry capabilityRegistry;
    private final ToolSecurityInterceptor toolSecurityInterceptor;

    /**
     * 2026-08-23 重构抽出:把脆弱的 tool_call 流式累积逻辑封到一个类。
     */
    private final ToolCallAccumulator toolCallAccumulator;

    /**
     * 2026-08-23 重构抽出:OpenAI Chat Completions 协议消息构造。
     */
    private final ModelMessageBuilder messageBuilder;

    /**
     * 2026-08-23 重构抽出:工具执行调度(权限/上下文/事件/C8 重复拦截/C11 phantom 过滤)。
     */
    private final ToolExecutionDispatcher toolDispatcher;

    /**
     * 2026-08-23 阶段 2 抽出:344 行流式主循环。
     */
    private final StreamingOrchestrator streamingOrchestrator;

    public AgentOrchestrator(AiChatClient aiChatClient,
                             MallSkillRegistry skillRegistry,
                             MallSystemPromptProvider promptProvider,
                             AiAssistantProperties assistantProperties,
                             ObjectMapper objectMapper,
                             CapabilityRegistry capabilityRegistry,
                             ToolSecurityInterceptor toolSecurityInterceptor) {
        this.aiChatClient = aiChatClient;
        this.skillRegistry = skillRegistry;
        this.promptProvider = promptProvider;
        this.assistantProperties = assistantProperties;
        this.objectMapper = objectMapper;
        this.capabilityRegistry = capabilityRegistry;
        this.toolSecurityInterceptor = toolSecurityInterceptor;
        // 抽出后的子组件 — 让 AgentOrchestrator 回归"装配 + 主循环"职责
        this.toolCallAccumulator = new ToolCallAccumulator(objectMapper);
        this.messageBuilder = new ModelMessageBuilder(objectMapper);
        this.toolDispatcher = new ToolExecutionDispatcher(
                skillRegistry, toolSecurityInterceptor, capabilityRegistry, toolCallAccumulator);
        this.streamingOrchestrator = new StreamingOrchestrator(
                aiChatClient, skillRegistry, promptProvider, assistantProperties,
                capabilityRegistry, toolCallAccumulator, messageBuilder, toolDispatcher);
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
            // 2026-08-23 重构:OpenAI 协议消息走 ModelMessageBuilder
            messages.add(messageBuilder.buildAssistantToolCallMessage(reply, result.getToolCalls(), result.getReasoningContent()));

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
                    // 同步路径(safeExecute 的逻辑直接走,不走 dispatcher,因为没有 listener/capability)
                    try {
                        toolSecurityInterceptor.preCheck(tool.name());
                        toolResult = MallUserContextExecutor.runAs(currentUser, () -> tool.execute(call.getArguments()));
                    } catch (ToolAccessDeniedException e) {
                        log.info("[AI][ORCH] tool denied: {} - user={} role={}",
                                tool.name(),
                                currentUser == null ? null : currentUser.getId(),
                                e.getUserRole());
                        toolResult = AgentToolResult.ofText("工具 " + tool.name() + " 当前无权限调用(角色=" + e.getUserRole() + ")。");
                    } catch (Exception e) {
                        log.warn("[AI][ORCH] tool {} execution failed: {}", tool.name(), e.getMessage(), e);
                        toolResult = AgentToolResult.ofText("工具执行失败: " + e.getMessage());
                    }
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

                messages.add(messageBuilder.buildToolResponseMessage(call.getId(), call.getName(), toolResult.getContent()));
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
        // 不携带 runId/sessionId 的旧入口,委托给新方法,事件用替代 ID 关联。
        // AiAssistantService 已切到 runStreamingWithRun(...),这里仅作向下兼容。
        return runStreamingWithRun(currentUser, history, userMessage, listener, null, null);
    }

    /**
     * 带 runId / sessionId 的流式运行入口。AiAssistantService 走这里,
     * 让 CapabilityRegistry 发布的事件能精确关联到 ai_run / ai_session 表。
     *
     * <p>2026-08-23 阶段 2 重构:344 行主循环全权委托给 {@link StreamingOrchestrator},
     * AgentOrchestrator 只剩装配 + 入口转发。
     */
    public AgentResult runStreamingWithRun(UserDTO currentUser,
                                           List<HistoryMessage> history,
                                           String userMessage,
                                           OrchestratorListener listener,
                                           String runId,
                                           String sessionId) {
        return streamingOrchestrator.runStreaming(currentUser, history, userMessage, listener, runId, sessionId);
    }

    /**
     * 把一个 stream 来的 ToolCallDelta 合并进 toolCalls 列表。
     * C11 修复:chunk.id 为空(纯 args 续传)时,强制按 index 合并,无论 slot 是否有 id。
     * 旧逻辑要求 slot.id 也为空才合并 → args 续传时创建 phantom entry,
     * phantom 的 args 被后续带完整 id/name 的 chunk 通过 by-id 匹配继承,
     * 导致 draft_create_order 收到 search_products 的 args 等跨污染事故。
     *
     * <p>2026-08-23 重构:实际逻辑搬到 {@link ToolCallAccumulator#mergeDelta},
     * 这里保留为薄委托,让单测可以通过反射调用而不需要重构测试。
     * 新代码请直接调用 ToolCallAccumulator。
     */
    static void mergeToolCallDelta(List<AgentToolCall> toolCalls, ToolCallDelta d) {
        STATIC_ACCUMULATOR.mergeDelta(toolCalls, d);
    }

    /** @deprecated 使用 {@link ToolCallAccumulator#parseFirstChunk} */
    @Deprecated
    static JsonNode parseArgumentsSafely(String raw) {
        return STATIC_ACCUMULATOR.parseFirstChunk(raw);
    }

    /**
     * C10 修复:DeepSeek 流式 tool_call arguments 按字符拆 chunk 发送
     * (先 `{`, 再 `"keyword`, 再 `":"自行车",`, 再 `}` 等),
     * 旧逻辑在 current 是空 `{}` 时把 current.toString() (即 "{}") 和 delta 拼接,
     * 产生 "{}{...}" 这种永远不合法的字符串,args 永远停在 __streaming__ 阶段,
     * 工具收到 keyword=null / productId=null → 全表前 5 / "缺少必填参数"。
     *
     * <p>2026-08-23 重构:实际逻辑搬到 {@link ToolCallAccumulator#appendChunk},
     * 这里保留为薄委托。
     */
    @Deprecated
    static JsonNode appendArguments(JsonNode current, String delta) {
        return STATIC_ACCUMULATOR.appendChunk(current, delta);
    }

    private static String preview(String s, int max) {
        if (s == null) return "";
        String t = s.replace("\n", " ").replace("\r", " ");
        return t.length() <= max ? t : t.substring(0, max) + "...";
    }

    /**
     * C8:计算工具调用签名,用于检测重复。
     * 同一 (name, args 序列化) 在一次 Run 内重复出现即视为死循环信号。
     *
     * <p>2026-08-23 重构:实际逻辑搬到 {@link ToolCallAccumulator#computeSignature}。
     */
    @Deprecated
    static String computeToolSignature(String name, JsonNode arguments) {
        return STATIC_ACCUMULATOR.computeSignature(name, arguments);
    }

    /**
     * C8:被拦截的重复工具调用,返回给模型的 sentinel 内容。
     * 必须明确说「请立即给最终回复,不要再调用此工具」,
     * 模型收到这个 tool_response 后通常会终止迭代。
     *
     * <p>2026-08-23 重构:实际逻辑搬到 {@link ToolCallAccumulator#buildDuplicateSentinel}。
     */
    @Deprecated
    static String buildDuplicateSentinel(String name, int count) {
        return STATIC_ACCUMULATOR.buildDuplicateSentinel(name, count);
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

package com.scutmmq.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scutmmq.ai.client.AiChatClient;
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

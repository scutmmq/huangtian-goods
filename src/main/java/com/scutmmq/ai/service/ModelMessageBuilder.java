package com.scutmmq.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scutmmq.ai.tool.AgentToolCall;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 模型消息构造器 — OpenAI Chat Completions 协议消息格式。
 *
 * <p>把内部的 AgentToolCall / JsonNode 序列化成模型 API 期望的 Map 格式。
 * 抽出独立类后:
 * <ul>
 *   <li>AgentOrchestrator 不再关心 OpenAI 协议字段名(tool_call_id / reasoning_content / function)</li>
 *   <li>如果以后换 Anthropic/Gemini,只需新加一个 builder,orchestrator 不动</li>
 *   <li>argumentsAsString 集中在这里,JsonNode → JSON 字符串</li>
 * </ul>
 *
 * <p>状态:无状态,只依赖注入的 ObjectMapper。
 */
public class ModelMessageBuilder {

    private final ObjectMapper objectMapper;

    public ModelMessageBuilder(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /**
     * 构造 assistant 消息(含 content + reasoning_content + tool_calls)。
     * DeepSeek thinking 模式:必须原样把 reasoning_content 送回去,否则下一轮 400。
     */
    public Map<String, Object> buildAssistantToolCallMessage(String content,
                                                              List<AgentToolCall> toolCalls,
                                                              String reasoningContent) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", content == null ? "" : content);
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

    /**
     * 构造 tool role 消息(把工具执行结果喂回模型)。
     */
    public Map<String, Object> buildToolResponseMessage(String toolCallId, String toolName, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "tool");
        message.put("tool_call_id", toolCallId);
        message.put("name", toolName);
        message.put("content", content == null ? "" : content);
        return message;
    }

    /**
     * 把 JsonNode arguments 序列化成 OpenAI function.arguments 要求的 JSON 字符串。
     * null → "{}"。
     */
    public String argumentsAsString(JsonNode arguments) {
        try {
            return objectMapper.writeValueAsString(
                    arguments == null ? objectMapper.createObjectNode() : arguments);
        } catch (Exception e) {
            return "{}";
        }
    }
}

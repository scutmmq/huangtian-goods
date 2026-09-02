package com.scutmmq.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scutmmq.ai.config.AiProviderConfig;
import com.scutmmq.ai.tool.AgentToolCall;
import com.scutmmq.ai.tool.AgentToolDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OpenAI-compatible AI 聊天客户端。v1 同步阻塞调用，封装工具协议序列化与响应解析。
 */
@Slf4j
@Component
public class AiChatClient {

    private final AiProviderConfig aiProviderConfig;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public AiChatClient(AiProviderConfig aiProviderConfig,
                        WebClient webClient,
                        ObjectMapper objectMapper) {
        this.aiProviderConfig = aiProviderConfig;
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 同步调用 Chat Completions，允许携带工具定义。
     */
    public ChatCompletionResult chatCompletion(List<Map<String, Object>> messages, List<AgentToolDefinition> tools) {
        aiProviderConfig.validateReady();
        int toolCount = tools == null ? 0 : tools.size();
        log.info("[AI][HTTP] -> {} model={} messages={} tools={}",
                aiProviderConfig.getUrl(),
                aiProviderConfig.getModel(),
                messages.size(),
                toolCount);

        // 打印每条消息的角色和长度，便于复现送给模型的上下文
        for (int i = 0; i < messages.size(); i++) {
            Map<String, Object> m = messages.get(i);
            Object role = m.get("role");
            Object content = m.get("content");
            String cstr = content == null ? "" : String.valueOf(content);
            boolean hasToolCalls = m.get("tool_calls") != null;
            boolean hasReasoning = m.get("reasoning_content") != null;
            log.info("[AI][HTTP]   msg[{}] role={} len={} hasToolCalls={} hasReasoning={} preview=\"{}\"",
                    i, role, cstr.length(), hasToolCalls, hasReasoning, preview(cstr, 160));
        }

        String requestBody = buildRequestBody(messages, tools);
        log.debug("[AI][HTTP] requestBody (len={}): {}", requestBody.length(), requestBody);

        long t0 = System.currentTimeMillis();
        String responseBody;
        try {
            responseBody = webClient.post()
                    .uri(aiProviderConfig.getUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(aiProviderConfig.getAuthHeader(), aiProviderConfig.authValue())
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            response -> response.bodyToMono(String.class)
                                    .map(errorBody -> {
                                        log.error("[AI][HTTP] provider error status={} body={}",
                                                response.statusCode(), errorBody);
                                        return new RuntimeException(
                                                "AI Provider error: " + response.statusCode() + " - " + errorBody);
                                    })
                    )
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();
        } catch (Exception e) {
            log.error("[AI][HTTP] call failed in {}ms: {}", System.currentTimeMillis() - t0, e.getMessage());
            throw e;
        }

        long elapsed = System.currentTimeMillis() - t0;
        log.info("[AI][HTTP] <- 200 in {}ms responseLen={}",
                elapsed, responseBody == null ? 0 : responseBody.length());
        log.debug("[AI][HTTP] responseBody: {}", responseBody);

        ChatCompletionResult parsed = parseChatCompletionResponse(responseBody);
        log.info("[AI][HTTP] parsed: contentLen={} toolCalls={} hasReasoning={}",
                parsed.getContent().length(),
                parsed.getToolCalls().size(),
                parsed.getReasoningContent() != null && !parsed.getReasoningContent().isEmpty());
        for (AgentToolCall tc : parsed.getToolCalls()) {
            log.info("[AI][HTTP]   tool_call: id={} name={} args={}",
                    tc.getId(), tc.getName(), tc.getArguments());
        }
        return parsed;
    }

    /**
     * 流式调用 Chat Completions。请求体带 stream: true，按 chunk 回调给 listener。
     * 保留现有 chatCompletion(messages, tools) 同步方法不变（测试/降级路径）。
     *
     * <p><b>线程契约：</b>listener 的所有回调都在调用方的线程上同步触发（方法内部
     * 使用 {@code .blockLast()} 阻塞当前线程），整个方法在流正常结束、收到
     * {@code data: [DONE]}、或遇到致命错误时返回。回调里不要做长时间阻塞或重入本类。
     *
     * <p><b>SSE 行分帧：</b>实现上以 {@code DataBuffer} 接收原始字节，按 {@code \n}
     * 切行，跨缓冲区的半行会被保留到下一个 buffer，确保 {@code data: ...} 行不会因
     * netty flush 边界被截断或合并。
     *
     * <p><b>终态保证：</b>成功流只触发一次 {@code onComplete}；失败流只触发一次
     * {@code onError}；两者不会同时触发，也不会重复触发。
     *
     * @param messages 同 chatCompletion
     * @param tools    同 chatCompletion
     * @param listener 接收每个 chunk 的回调；本方法会捕获所有内部异常并转交给 onError，不会抛到外层
     */
    public void streamChatCompletion(List<Map<String, Object>> messages,
                                     List<AgentToolDefinition> tools,
                                     StreamChunkListener listener) {
        aiProviderConfig.validateReady();
        int toolCount = tools == null ? 0 : tools.size();
        log.info("[AI][HTTP][STREAM] -> {} model={} messages={} tools={}",
                aiProviderConfig.getUrl(),
                aiProviderConfig.getModel(),
                messages.size(),
                toolCount);

        for (int i = 0; i < messages.size(); i++) {
            Map<String, Object> m = messages.get(i);
            Object role = m.get("role");
            Object content = m.get("content");
            String cstr = content == null ? "" : String.valueOf(content);
            boolean hasToolCalls = m.get("tool_calls") != null;
            boolean hasReasoning = m.get("reasoning_content") != null;
            log.info("[AI][HTTP][STREAM]   msg[{}] role={} len={} hasToolCalls={} hasReasoning={} preview=\"{}\"",
                    i, role, cstr.length(), hasToolCalls, hasReasoning, preview(cstr, 160));
        }

        String requestBody = buildStreamRequestBody(messages, tools);
        log.info("[AI][HTTP][STREAM] requestBody: {}", requestBody);

        long t0 = System.currentTimeMillis();
        int[] chunkCount = {0};
        // 终态去重：保证 onComplete 只触发一次（[DONE] 路径 + Flux 终止路径都会试图触发）。
        // onError 不去重 —— 任何 fatal 错误都要转交给 listener。
        AtomicBoolean completed = new AtomicBoolean(false);
        // SSE 行分帧：跨 DataBuffer 边界累积半行，直到遇到 \n 才视为一条完整行。
        StringBuilder lineBuffer = new StringBuilder();
        try {
            webClient.post()
                    .uri(aiProviderConfig.getUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(aiProviderConfig.getAuthHeader(), aiProviderConfig.authValue())
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            response -> response.bodyToMono(String.class)
                                    .map(errorBody -> {
                                        log.error("[AI][HTTP][STREAM] provider error status={} body={} requestBody={}",
                                                response.statusCode(), errorBody, requestBody);
                                        return new RuntimeException(
                                                "AI Provider error: " + response.statusCode() + " - " + errorBody);
                                    })
                    )
                    .bodyToFlux(DataBuffer.class)
                    .timeout(Duration.ofMinutes(5))
                    .doOnNext(buffer -> {
                        try {
                            String chunk = bufferToString(buffer);
                            if (chunk.isEmpty()) return;
                            // 把 chunk 按 \n 切成若干行；最后一段可能是不完整的半行，留在 lineBuffer 里。
                            int start = 0;
                            int idx;
                            while ((idx = chunk.indexOf('\n', start)) >= 0) {
                                lineBuffer.append(chunk, start, idx);
                                String line = lineBuffer.toString();
                                lineBuffer.setLength(0);
                                parseStreamLine(line, listener, chunkCount, completed);
                                start = idx + 1;
                            }
                            if (start < chunk.length()) {
                                lineBuffer.append(chunk, start, chunk.length());
                            }
                        } finally {
                            DataBufferUtils.release(buffer);
                        }
                    })
                    .doOnError(err -> {
                        long elapsed = System.currentTimeMillis() - t0;
                        log.error("[AI][HTTP][STREAM] stream failed in {}ms after {} chunks: {}",
                                elapsed, chunkCount[0], err.getMessage());
                        safeOnError(listener, err);
                    })
                    .doOnComplete(() -> {
                        long elapsed = System.currentTimeMillis() - t0;
                        // 收尾：流正常结束前 flush 残留的半行（多半是尾部没换行的 data: [DONE]）
                        if (lineBuffer.length() > 0) {
                            String tail = lineBuffer.toString();
                            lineBuffer.setLength(0);
                            parseStreamLine(tail, listener, chunkCount, completed);
                        }
                        log.info("[AI][HTTP][STREAM] <- stream complete in {}ms chunks={}",
                                elapsed, chunkCount[0]);
                        if (completed.compareAndSet(false, true)) {
                            safeOnComplete(listener);
                        } else {
                            log.debug("[AI][HTTP][STREAM] onComplete already fired (via [DONE]), skip doOnComplete path");
                        }
                    })
                    .blockLast();
        } catch (Exception e) {
            // 兜底：极少数情况下 blockLast 之外的异常（Flux 装配错误等）走到这里
            long elapsed = System.currentTimeMillis() - t0;
            log.error("[AI][HTTP][STREAM] outer failure in {}ms: {}", elapsed, e.getMessage(), e);
            safeOnError(listener, e);
        }
    }

    private static String bufferToString(DataBuffer buffer) {
        if (buffer == null) return "";
        int len = buffer.readableByteCount();
        if (len == 0) return "";
        byte[] bytes = new byte[len];
        buffer.read(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 构造带 stream: true 的请求体。基于 buildRequestBody 的逻辑，最后追加 stream 字段。
     */
    String buildStreamRequestBody(List<Map<String, Object>> messages, List<AgentToolDefinition> tools) {
        String base = buildRequestBody(messages, tools);
        try {
            JsonNode root = objectMapper.readTree(base);
            if (root instanceof ObjectNode obj) {
                obj.put("stream", true);
                return objectMapper.writeValueAsString(obj);
            }
        } catch (Exception e) {
            log.warn("[AI][HTTP][STREAM] failed to inject stream=true, falling back to base body: {}", e.getMessage());
        }
        return base;
    }

    /**
     * 解析单行 SSE 数据（不包含末尾换行）。
     * 期望格式：`data: {...}` 或 `data: [DONE]`。其余行（event: / : / 空行）跳过。
     * completed 是终态去重标记 —— [DONE] 路径上先 CAS 再调 onComplete，
     * 留出 doOnComplete 路径在 Flux 终止时跳过重复触发。
     */
    void parseStreamLine(String rawLine, StreamChunkListener listener, int[] chunkCount, AtomicBoolean completed) {
        if (rawLine == null) return;
        String line = rawLine.trim();
        if (line.isEmpty()) return;
        // SSE 注释行以 ":" 开头，跳过
        if (line.startsWith(":")) return;
        // event: 行不处理（OpenAI 协议里只需要 data）
        int colonIdx = line.indexOf(':');
        if (colonIdx <= 0) return;
        String field = line.substring(0, colonIdx);
        if (!"data".equals(field)) return;

        String data = line.substring(colonIdx + 1).trim();
        if (data.isEmpty()) return;

        chunkCount[0]++;

        // 流结束标记 —— 抢先 CAS 占用 completed 标记，
        // 后续 doOnComplete 走另一个 if 分支就不会重复触发 onComplete。
        if ("[DONE]".equals(data)) {
            log.info("[AI][HTTP][STREAM] received [DONE] after {} chunks", chunkCount[0]);
            if (completed.compareAndSet(false, true)) {
                safeOnComplete(listener);
            } else {
                log.debug("[AI][HTTP][STREAM] onComplete already fired, skip [DONE] path");
            }
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(data);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.size() == 0) {
                return;
            }
            JsonNode delta = choices.get(0).path("delta");

            // content delta
            if (delta.hasNonNull("content")) {
                String content = delta.get("content").asText("");
                if (!content.isEmpty()) {
                    listener.onContentDelta(content);
                }
            }

            // reasoning_content delta (DeepSeek thinking)
            if (delta.hasNonNull("reasoning_content")) {
                String reasoning = delta.get("reasoning_content").asText("");
                if (!reasoning.isEmpty()) {
                    listener.onReasoningDelta(reasoning);
                }
            }

            // tool_calls delta
            JsonNode toolCallsNode = delta.path("tool_calls");
            if (toolCallsNode.isArray() && toolCallsNode.size() > 0) {
                List<ToolCallDelta> deltas = new ArrayList<>();
                for (JsonNode tc : toolCallsNode) {
                    JsonNode function = tc.path("function");
                    ToolCallDelta d = new ToolCallDelta();
                    d.setIndex(tc.path("index").asInt(0));
                    d.setId(tc.hasNonNull("id") ? tc.get("id").asText("") : "");
                    d.setName(function.hasNonNull("name") ? function.get("name").asText("") : "");
                    d.setArgumentsDelta(function.hasNonNull("arguments") ? function.get("arguments").asText("") : "");
                    deltas.add(d);
                }
                if (!deltas.isEmpty()) {
                    listener.onToolCallDelta(deltas);
                }
            }
        } catch (Exception parseErr) {
            // 单行 JSON 解析失败只记 warn，不打断整条流
            log.warn("[AI][HTTP][STREAM] failed to parse chunk #{} (len={}): {} payload={}",
                    chunkCount[0], data.length(), parseErr.getMessage(),
                    preview(data, 200));
        }
    }

    private void safeOnComplete(StreamChunkListener listener) {
        try {
            listener.onComplete();
        } catch (Exception e) {
            log.warn("[AI][HTTP][STREAM] listener.onComplete threw: {}", e.getMessage(), e);
        }
    }

    private void safeOnError(StreamChunkListener listener, Throwable err) {
        try {
            listener.onError(err);
        } catch (Exception e) {
            log.warn("[AI][HTTP][STREAM] listener.onError threw: {}", e.getMessage(), e);
        }
    }

    private static String preview(String s, int max) {
        if (s == null) return "";
        String t = s.replace("\n", " ").replace("\r", " ");
        return t.length() <= max ? t : t.substring(0, max) + "...";
    }

    String buildRequestBody(List<Map<String, Object>> messages, List<AgentToolDefinition> tools) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", aiProviderConfig.getModel());

        ArrayNode messagesArray = requestBody.putArray("messages");
        for (Map<String, Object> message : messages) {
            ObjectNode messageNode = messagesArray.addObject();
            for (Map.Entry<String, Object> entry : message.entrySet()) {
                putJsonValue(messageNode, entry.getKey(), entry.getValue());
            }
        }

        List<AgentToolDefinition> safeTools = tools == null ? List.of() : tools;
        if (!safeTools.isEmpty()) {
            ArrayNode toolsArray = requestBody.putArray("tools");
            for (AgentToolDefinition tool : safeTools) {
                ObjectNode toolNode = toolsArray.addObject();
                toolNode.put("type", "function");
                ObjectNode functionNode = toolNode.putObject("function");
                functionNode.put("name", tool.getName());
                functionNode.put("description", tool.getDescription());
                functionNode.set("parameters", tool.getParameters());
            }
            requestBody.put("tool_choice", "auto");
        }

        return requestBody.toString();
    }

    ChatCompletionResult parseChatCompletionResponse(String responseBody) {
        if (responseBody == null) {
            return new ChatCompletionResult("抱歉，AI 暂时无响应", List.of(), null);
        }
        try {
            JsonNode rootNode = objectMapper.readTree(responseBody);
            JsonNode choicesNode = rootNode.path("choices");

            if (choicesNode.isArray() && choicesNode.size() > 0) {
                JsonNode messageNode = choicesNode.get(0).path("message");
                String content = messageNode.path("content").asText("");
                List<AgentToolCall> toolCalls = parseToolCalls(messageNode.path("tool_calls"));
                // DeepSeek thinking 模式：assistant 消息会带 reasoning_content，回传时必须原样送回，否则下一轮 400。
                String reasoningContent = null;
                if (messageNode.hasNonNull("reasoning_content")) {
                    reasoningContent = messageNode.get("reasoning_content").asText("");
                }
                return new ChatCompletionResult(content, toolCalls, reasoningContent);
            }

            log.warn("Unexpected response format: {}", responseBody);
            return new ChatCompletionResult("抱歉，AI 响应格式异常", List.of(), null);
        } catch (Exception e) {
            log.error("Failed to parse AI Provider response: {}", e.getMessage(), e);
            return new ChatCompletionResult("抱歉，解析 AI 响应时出错", List.of(), null);
        }
    }

    private List<AgentToolCall> parseToolCalls(JsonNode toolCallsNode) {
        if (toolCallsNode == null || !toolCallsNode.isArray()) {
            return List.of();
        }
        List<AgentToolCall> toolCalls = new ArrayList<>();
        for (JsonNode toolCallNode : toolCallsNode) {
            JsonNode functionNode = toolCallNode.path("function");
            toolCalls.add(new AgentToolCall(
                    toolCallNode.path("id").asText(""),
                    functionNode.path("name").asText(""),
                    parseArguments(functionNode.path("arguments").asText("{}"))
            ));
        }
        return toolCalls;
    }

    private JsonNode parseArguments(String arguments) {
        try {
            return objectMapper.readTree(arguments == null || arguments.isBlank() ? "{}" : arguments);
        } catch (Exception e) {
            ObjectNode fallback = objectMapper.createObjectNode();
            fallback.put("raw", arguments == null ? "" : arguments);
            return fallback;
        }
    }

    private void putJsonValue(ObjectNode node, String key, Object value) {
        if (value == null) {
            node.putNull(key);
        } else if (value instanceof JsonNode jsonNode) {
            node.set(key, jsonNode);
        } else if (value instanceof String stringValue) {
            node.put(key, stringValue);
        } else if (value instanceof Boolean booleanValue) {
            node.put(key, booleanValue);
        } else if (value instanceof Number numberValue) {
            node.putPOJO(key, numberValue);
        } else {
            node.set(key, objectMapper.valueToTree(value));
        }
    }

    public static class ChatCompletionResult {
        private final String content;
        private final List<AgentToolCall> toolCalls;
        /**
         * DeepSeek thinking 模式回来的思考过程；非 thinking 模型为 null。
         * 在 tool-call 多轮对话中，必须把这个字段原样塞回下一轮的 assistant 消息里。
         */
        private final String reasoningContent;

        public ChatCompletionResult(String content, List<AgentToolCall> toolCalls, String reasoningContent) {
            this.content = content == null ? "" : content;
            this.toolCalls = toolCalls == null ? List.of() : toolCalls;
            this.reasoningContent = reasoningContent;
        }

        public String getContent() {
            return content;
        }

        public List<AgentToolCall> getToolCalls() {
            return toolCalls;
        }

        public String getReasoningContent() {
            return reasoningContent;
        }
    }
}

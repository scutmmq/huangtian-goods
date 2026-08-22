package com.scutmmq.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.scutmmq.ai.capability.CapabilityRegistry;
import com.scutmmq.ai.capability.RunContext;
import com.scutmmq.ai.capability.ToolContext;
import com.scutmmq.ai.security.ToolAccessDeniedException;
import com.scutmmq.ai.security.ToolSecurityInterceptor;
import com.scutmmq.ai.skill.MallSkillRegistry;
import com.scutmmq.ai.tool.AgentToolCall;
import com.scutmmq.ai.tool.AgentToolResult;
import com.scutmmq.ai.tool.MallAgentTool;
import com.scutmmq.ai.util.MallUserContextExecutor;
import com.scutmmq.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具执行调度器(2026-08-23 凌晨事故后抽出)。
 *
 * <p>负责:
 * <ol>
 *   <li>C11 安全网 — 过滤掉 name/id 为空的 phantom tool calls(防止 stream 异常留下垃圾条目)</li>
 *   <li>C8 重复拦截 — 同一 (name, args) 第二次起返回 sentinel,逼模型给最终回复</li>
 *   <li>权限校验 — 调用 ToolSecurityInterceptor.preCheck</li>
 *   <li>UserHolder 上下文注入 — 通过 MallUserContextExecutor 包裹 tool.execute</li>
 *   <li>capability 事件发布 — 工具开始/结束都通知 B2 的可观测层</li>
 *   <li>SSE 事件回调 — tool.started / tool.finished</li>
 * </ol>
 *
 * <p>状态:无状态。唯一持久的是 ToolCallAccumulator(签名累积)和 capabilityRegistry。
 */
@Slf4j
public class ToolExecutionDispatcher {

    private final MallSkillRegistry skillRegistry;
    private final ToolSecurityInterceptor toolSecurityInterceptor;
    private final CapabilityRegistry capabilityRegistry;
    private final ToolCallAccumulator accumulator;

    public ToolExecutionDispatcher(MallSkillRegistry skillRegistry,
                                    ToolSecurityInterceptor toolSecurityInterceptor,
                                    CapabilityRegistry capabilityRegistry,
                                    ToolCallAccumulator accumulator) {
        this.skillRegistry = skillRegistry;
        this.toolSecurityInterceptor = toolSecurityInterceptor;
        this.capabilityRegistry = capabilityRegistry;
        this.accumulator = accumulator;
    }

    /**
     * C11 安全网:过滤掉 name/id 为空的 phantom tool calls。
     * <p>DeepSeek stream 偶尔会留下空 name/空 id 的残留条目(例如 chunk 全部带空 id 且 index 不连续),
     * 过滤掉,避免执行时报"工具不存在"以及污染 buildAssistantToolCallMessage 的 tool_calls 字段。
     *
     * @return 被过滤的条目数(用于日志)
     */
    public int filterPhantoms(List<AgentToolCall> toolCalls) {
        int before = toolCalls.size();
        toolCalls.removeIf(tc -> tc.getName() == null || tc.getName().isBlank()
                || tc.getId() == null || tc.getId().isBlank());
        int filtered = before - toolCalls.size();
        if (filtered > 0) {
            log.warn("[AI][ORCH][STREAM] filtered {} phantom tool_calls (empty name or id)", filtered);
        }
        return filtered;
    }

    /**
     * 顺序执行一组 tool calls,带 C8 重复拦截 + capability 事件 + listener 回调。
     *
     * @return ExecutionResult 包含所有工具执行记录和最终 draft
     */
    public ExecutionResult dispatch(List<AgentToolCall> toolCalls,
                                    OrchestratorListener listener,
                                    UserDTO currentUser,
                                    RunContext runCtx) {
        Map<String, Integer> toolCallCounts = new HashMap<>();
        java.util.List<ExecutionRecord> executions = new java.util.ArrayList<>();
        AgentToolResult.DraftPayload draft = null;

        for (AgentToolCall call : toolCalls) {
            log.info("[AI][ORCH][STREAM] tool_call -> name={} id={} args={}",
                    call.getName(), call.getId(), call.getArguments());
            listener.onToolStarted(call.getId(), call.getName(), call.getArguments());

            long toolStartMs = System.currentTimeMillis();

            // C8:重复工具调用检测
            String signature = accumulator.computeSignature(call.getName(), call.getArguments());
            int priorCount = toolCallCounts.getOrDefault(signature, 0);
            toolCallCounts.put(signature, priorCount + 1);

            AgentToolResult toolResult;
            boolean interceptedDuplicate = false;

            if (priorCount >= 1) {
                // 第二次起返回 sentinel,不真正执行
                interceptedDuplicate = true;
                String sentinel = accumulator.buildDuplicateSentinel(call.getName(), priorCount + 1);
                log.warn("[AI][ORCH][STREAM] duplicate tool call intercepted name={} sig={} count={}",
                        call.getName(), signature, priorCount + 1);
                toolResult = AgentToolResult.ofText(sentinel);
            } else {
                toolResult = executeOne(call, currentUser);
            }

            if (toolResult.getDraft() != null) {
                draft = toolResult.getDraft();
                listener.onDraftCreated(draft);
                capabilityRegistry.publishDraftCreated(
                        runCtx.getRunId(), runCtx.getSessionId(), runCtx.getUserId(),
                        draft.getActionType(), draft.getTitle(), draft.getSummary(),
                        draft.getPayload());
            }

            // B2:工具执行完发布 ToolExecutedEvent
            boolean toolSuccess = !interceptedDuplicate
                    && toolResult.getDraft() == null
                    && toolResult.getContent() != null
                    && !toolResult.getContent().startsWith("工具执行失败");
            capabilityRegistry.publishToolExecuted(
                    ToolContext.fromRun(runCtx, call.getName(), call.getId(),
                            call.getArguments(),
                            toolResult.getContent() == null ? "" : safeTruncate(toolResult.getContent(), 200),
                            toolResult.getDraft() != null,
                            toolSuccess,
                            interceptedDuplicate ? "duplicate-intercepted" : null,
                            toolStartMs, System.currentTimeMillis()));
            listener.onToolFinished(call.getId(), call.getName(), toolResult.getContent(),
                    toolResult.getDraft() != null);

            // 把 tool result 喂回模型(由 caller 加进 messages)
            executions.add(new ExecutionRecord(call, toolResult));
        }

        return new ExecutionResult(executions, draft);
    }

    /**
     * 真正执行单个 tool call,带权限校验 + UserHolder 上下文 + 异常捕获。
     */
    private AgentToolResult executeOne(AgentToolCall call, UserDTO currentUser) {
        MallAgentTool tool = skillRegistry.findByName(call.getName());
        if (tool == null) {
            log.warn("[AI][ORCH][STREAM] unknown tool requested by model: {}", call.getName());
            return AgentToolResult.ofText("工具不存在: " + call.getName());
        }
        long tt0 = System.currentTimeMillis();
        AgentToolResult toolResult = safeExecute(tool, call.getArguments(), currentUser);
        log.info("[AI][ORCH][STREAM] tool {} executed in {}ms mode={} resultPreview=\"{}\" draft={}",
                call.getName(),
                System.currentTimeMillis() - tt0,
                tool.mode(),
                preview(toolResult.getContent(), 200),
                toolResult.getDraft() == null ? "none" : toolResult.getDraft().getActionType());
        return toolResult;
    }

    private AgentToolResult safeExecute(MallAgentTool tool, JsonNode arguments, UserDTO currentUser) {
        // 2026-08-23 阶段 2 重构:逻辑搬到 ToolExecutor.safeExecute,统一同步/流式两条路径
        return ToolExecutor.safeExecute(tool, arguments, currentUser, toolSecurityInterceptor);
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

    public record ExecutionRecord(AgentToolCall call, AgentToolResult result) {}

    public record ExecutionResult(java.util.List<ExecutionRecord> records,
                                  AgentToolResult.DraftPayload draft) {}
}

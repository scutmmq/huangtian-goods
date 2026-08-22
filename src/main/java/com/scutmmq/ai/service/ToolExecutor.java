package com.scutmmq.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.scutmmq.ai.security.ToolAccessDeniedException;
import com.scutmmq.ai.security.ToolSecurityInterceptor;
import com.scutmmq.ai.tool.AgentToolResult;
import com.scutmmq.ai.tool.MallAgentTool;
import com.scutmmq.ai.util.MallUserContextExecutor;
import com.scutmmq.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;

/**
 * 工具执行安全包装(2026-08-23 阶段 2 重构抽出)。
 *
 * <p>提供统一的"工具调用 + 权限校验 + UserHolder 上下文注入 + 异常转换" 入口,
 * 避免在 {@link AgentOrchestrator#run} 同步路径 和 {@link ToolExecutionDispatcher}
 * 流式路径里重复同一段 try/catch + preCheck + runAs 模板。
 *
 * <p>行为契约(零行为变化):
 * <ul>
 *   <li>调用 ToolSecurityInterceptor.preCheck(tool.name())</li>
 *   <li>在 MallUserContextExecutor.runAs(currentUser, ...) 里执行 tool.execute(arguments)</li>
 *   <li>ToolAccessDeniedException → 返回 AgentToolResult.ofText("工具 X 当前无权限调用(角色=Y)")</li>
 *   <li>其他 Exception → 返回 AgentToolResult.ofText("工具执行失败: msg")</li>
 * </ul>
 */
@Slf4j
public final class ToolExecutor {

    private ToolExecutor() { /* 静态工具类 */ }

    /**
     * 安全执行单个工具调用。返回值永远是 AgentToolResult(成功或失败都有内容)。
     */
    public static AgentToolResult safeExecute(MallAgentTool tool,
                                             JsonNode arguments,
                                             UserDTO currentUser,
                                             ToolSecurityInterceptor toolSecurityInterceptor) {
        try {
            toolSecurityInterceptor.preCheck(tool.name());
            return MallUserContextExecutor.runAs(currentUser, () -> tool.execute(arguments));
        } catch (ToolAccessDeniedException e) {
            log.info("[AI][ORCH] tool denied: {} - user={} role={}",
                    tool.name(),
                    currentUser == null ? null : currentUser.getId(),
                    e.getUserRole());
            return AgentToolResult.ofText("工具 " + tool.name() + " 当前无权限调用(角色=" + e.getUserRole() + ")。");
        } catch (Exception e) {
            log.warn("[AI][ORCH] tool {} execution failed: {}", tool.name(), e.getMessage(), e);
            return AgentToolResult.ofText("工具执行失败: " + e.getMessage());
        }
    }
}

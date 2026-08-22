package com.scutmmq.ai.security;

import com.scutmmq.ai.tool.UserRole;
import lombok.Getter;

/**
 * 工具访问拒绝异常。当 {@link ToolSecurityInterceptor} 检查发现当前用户角色
 * 不在工具的 allowedRoles 集合内时抛出。
 *
 * 注意:AgentOrchestrator 的 safeExecute 会捕获所有异常并转成 tool error 文案
 * 回喂模型,不会冒泡到 SSE / 前端。所以即使抛此异常,流程仍能继续。
 */
@Getter
public class ToolAccessDeniedException extends RuntimeException {

    private final String toolName;
    private final UserRole userRole;

    public ToolAccessDeniedException(String toolName, UserRole userRole) {
        super("当前角色无权调用工具 " + toolName + ": " + userRole);
        this.toolName = toolName;
        this.userRole = userRole;
    }
}

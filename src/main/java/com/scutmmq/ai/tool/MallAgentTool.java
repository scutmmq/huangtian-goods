package com.scutmmq.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.EnumSet;
import java.util.Set;

/**
 * 商城 AI 工具抽象。每个工具自带名称、描述、JSON Schema 参数定义和执行逻辑。
 * 后端只暴露注册在 MallSkillRegistry 中的工具，模型无法访问任意 HTTP 接口。
 */
public interface MallAgentTool {

    String name();

    ToolMode mode();

    AgentToolDefinition definition();

    /**
     * 执行工具。调用前 UserHolder 中必须已经存放当前用户。
     *
     * @param arguments 模型传入的参数（JSON Schema 校验过的）
     * @return 工具执行结果
     */
    AgentToolResult execute(JsonNode arguments);

    default boolean isAvailable() {
        return true;
    }

    /**
     * 允许调用此工具的用户角色集合。
     * 默认全开（USER / MERCHANT / ADMIN），即登录用户都能调用。
     * 商家专属工具或管理员专属工具需 override。
     *
     * 策略文档 §7A 权限模型:这是第一层（L1）权限边界,
     * 配合 {@link com.scutmmq.ai.security.ToolSecurityInterceptor} 在工具执行前拦截。
     */
    default Set<UserRole> allowedRoles() {
        return EnumSet.allOf(UserRole.class);
    }
}

package com.scutmmq.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;

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
}

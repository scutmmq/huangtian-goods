package com.scutmmq.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型请求执行的工具调用。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentToolCall {

    private String id;

    private String name;

    private JsonNode arguments;
}

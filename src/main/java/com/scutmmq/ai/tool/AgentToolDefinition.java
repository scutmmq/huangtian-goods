package com.scutmmq.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 可暴露给模型的工具定义。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentToolDefinition {

    private String name;

    private String description;

    private JsonNode parameters;
}

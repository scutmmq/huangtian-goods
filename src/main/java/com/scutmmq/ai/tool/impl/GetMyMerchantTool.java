package com.scutmmq.ai.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scutmmq.ai.tool.AgentToolDefinition;
import com.scutmmq.ai.tool.AgentToolResult;
import com.scutmmq.ai.tool.MallAgentTool;
import com.scutmmq.ai.tool.SchemaBuilder;
import com.scutmmq.ai.tool.ToolMode;
import com.scutmmq.entity.Result;
import com.scutmmq.service.MerchantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GetMyMerchantTool implements MallAgentTool {

    private static final String NAME = "get_my_merchant";

    private final MerchantService merchantService;
    private final ObjectMapper objectMapper;

    public GetMyMerchantTool(MerchantService merchantService,
                             ObjectMapper objectMapper) {
        this.merchantService = merchantService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ToolMode mode() {
        return ToolMode.READ_ONLY;
    }

    @Override
    public AgentToolDefinition definition() {
        JsonNode parameters = new SchemaBuilder(objectMapper).build();
        return AgentToolDefinition.builder()
                .name(NAME)
                .description("查询当前用户已注册的商家/店铺信息，包括店铺 id、名称、联系人、状态。" +
                        "用户提到“我的店铺”“商家中心”或想确认是否已注册商家时使用。")
                .parameters(parameters)
                .build();
    }

    @Override
    public AgentToolResult execute(JsonNode arguments) {
        Result result = merchantService.getMerchant();
        if (result == null) {
            return AgentToolResult.ofText("无返回结果。");
        }
        if (result.getCode() == null || result.getCode() != 1) {
            return AgentToolResult.ofText("查询失败: " + result.getMsg());
        }
        if (result.getData() == null) {
            return AgentToolResult.ofText("当前用户尚未注册店铺。");
        }
        try {
            return AgentToolResult.ofText(objectMapper.writeValueAsString(result.getData()));
        } catch (Exception e) {
            log.warn("Serialize merchant failed: {}", e.getMessage());
            return AgentToolResult.ofText(String.valueOf(result.getData()));
        }
    }
}

package com.scutmmq.ai.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scutmmq.ai.tool.AgentToolDefinition;
import com.scutmmq.ai.tool.AgentToolResult;
import com.scutmmq.ai.tool.MallAgentTool;
import com.scutmmq.ai.tool.SchemaBuilder;
import com.scutmmq.ai.tool.ToolMode;
import com.scutmmq.entity.Result;
import com.scutmmq.service.UserAddressService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GetMyAddressesTool implements MallAgentTool {

    private static final String NAME = "get_my_addresses";

    private final UserAddressService userAddressService;
    private final ObjectMapper objectMapper;

    public GetMyAddressesTool(UserAddressService userAddressService,
                              ObjectMapper objectMapper) {
        this.userAddressService = userAddressService;
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
                .description("查询当前登录用户的收货地址列表，返回每条地址的 id、收件人、电话、省市区、详细地址、是否默认。" +
                        "用户提到“我的地址”“默认地址”或在下单流程缺地址时使用。")
                .parameters(parameters)
                .build();
    }

    @Override
    public AgentToolResult execute(JsonNode arguments) {
        Result result = userAddressService.getAddress();
        if (result == null) {
            return AgentToolResult.ofText("无返回结果。");
        }
        if (result.getCode() == null || result.getCode() != 1) {
            return AgentToolResult.ofText("查询失败: " + result.getMsg());
        }
        try {
            return AgentToolResult.ofText(objectMapper.writeValueAsString(result.getData()));
        } catch (Exception e) {
            log.warn("Serialize addresses failed: {}", e.getMessage());
            return AgentToolResult.ofText(String.valueOf(result.getData()));
        }
    }
}

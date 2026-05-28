package com.scutmmq.ai.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scutmmq.ai.tool.AgentToolDefinition;
import com.scutmmq.ai.tool.AgentToolResult;
import com.scutmmq.ai.tool.MallAgentTool;
import com.scutmmq.ai.tool.SchemaBuilder;
import com.scutmmq.ai.tool.ToolMode;
import com.scutmmq.entity.Result;
import com.scutmmq.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GetMyOrdersTool implements MallAgentTool {

    private static final String NAME = "get_my_orders";

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    public GetMyOrdersTool(OrderService orderService,
                           ObjectMapper objectMapper) {
        this.orderService = orderService;
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
        JsonNode parameters = new SchemaBuilder(objectMapper)
                .prop("status", "string", "订单状态过滤；常用值: PENDING_PAYMENT 待支付、PENDING_SHIPPING 待发货、SHIPPED 已发货、COMPLETED 已完成、CANCELLED 已取消。留空表示全部。")
                .build();
        return AgentToolDefinition.builder()
                .name(NAME)
                .description("查询当前登录用户的订单列表。用户问“我的订单”“我有没有待支付的订单”时使用。")
                .parameters(parameters)
                .build();
    }

    @Override
    public AgentToolResult execute(JsonNode arguments) {
        String status = null;
        if (arguments != null && arguments.has("status") && !arguments.get("status").isNull()) {
            String s = arguments.get("status").asText();
            if (s != null && !s.isBlank()) {
                status = s.trim();
            }
        }

        Result result = orderService.getUserOrders(status);
        if (result == null) {
            return AgentToolResult.ofText("无返回结果。");
        }
        if (result.getCode() == null || result.getCode() != 1) {
            return AgentToolResult.ofText("查询失败: " + result.getMsg());
        }
        try {
            return AgentToolResult.ofText(objectMapper.writeValueAsString(result.getData()));
        } catch (Exception e) {
            log.warn("Serialize orders failed: {}", e.getMessage());
            return AgentToolResult.ofText(String.valueOf(result.getData()));
        }
    }
}

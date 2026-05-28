package com.scutmmq.ai.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scutmmq.ai.tool.AgentToolDefinition;
import com.scutmmq.ai.tool.AgentToolResult;
import com.scutmmq.ai.tool.MallAgentTool;
import com.scutmmq.ai.tool.SchemaBuilder;
import com.scutmmq.ai.tool.ToolMode;
import org.springframework.stereotype.Component;

@Component
public class DraftAddCartItemTool implements MallAgentTool {

    public static final String NAME = "draft_add_cart_item";
    public static final String ACTION_TYPE = "ADD_CART_ITEM";

    private final ObjectMapper objectMapper;

    public DraftAddCartItemTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override public String name() { return NAME; }

    @Override public ToolMode mode() { return ToolMode.DRAFT_ONLY; }

    @Override
    public AgentToolDefinition definition() {
        JsonNode parameters = new SchemaBuilder(objectMapper)
                .prop("productId", "integer", "商品 ID。")
                .prop("quantity", "integer", "加入数量，正整数。")
                .require("productId", "quantity")
                .build();
        return AgentToolDefinition.builder()
                .name(NAME)
                .description("生成“加入购物车”草稿。用户表达“加入购物车”意图时使用。等待用户在卡片上确认后才会真正调用。")
                .parameters(parameters)
                .build();
    }

    @Override
    public AgentToolResult execute(JsonNode arguments) {
        if (arguments == null || arguments.get("productId") == null || arguments.get("quantity") == null) {
            return AgentToolResult.ofText("缺少必填参数 productId / quantity。");
        }
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("productId", arguments.get("productId"));
        payload.set("quantity", arguments.get("quantity"));

        String summary = "商品 ID=" + arguments.get("productId").asText()
                + ", 数量=" + arguments.get("quantity").asText();
        AgentToolResult.DraftPayload draft = new AgentToolResult.DraftPayload();
        draft.setActionType(ACTION_TYPE);
        draft.setTitle("确认加入购物车");
        draft.setSummary(summary);
        draft.setPayload(payload);
        return AgentToolResult.ofDraft("已生成加入购物车草稿。" + summary, draft);
    }
}

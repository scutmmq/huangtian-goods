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
public class DraftRegisterMerchantTool implements MallAgentTool {

    public static final String NAME = "draft_register_merchant";
    public static final String ACTION_TYPE = "REGISTER_MERCHANT";

    private final ObjectMapper objectMapper;

    public DraftRegisterMerchantTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override public String name() { return NAME; }
    @Override public ToolMode mode() { return ToolMode.DRAFT_ONLY; }

    @Override
    public AgentToolDefinition definition() {
        JsonNode parameters = new SchemaBuilder(objectMapper)
                .prop("name", "string", "店铺名称。")
                .prop("description", "string", "店铺简介，可选。")
                .prop("contactPerson", "string", "联系人，可选。")
                .prop("contactPhone", "string", "联系电话，可选。")
                .prop("email", "string", "邮箱，可选。")
                .prop("address", "string", "经营地址，可选。")
                .prop("businessLicense", "string", "营业执照号，企业店铺需填。")
                .prop("merchantType", "string", "店铺类型: PERSONAL 个人 / ENTERPRISE 企业。")
                .prop("logoUrl", "string", "Logo 图片 URL，可选。")
                .require("name")
                .build();
        return AgentToolDefinition.builder()
                .name(NAME)
                .description("生成“注册店铺”草稿。用户表达想开店、注册商家时使用。等待用户在卡片上确认后才会真正调用商家注册。")
                .parameters(parameters)
                .build();
    }

    @Override
    public AgentToolResult execute(JsonNode arguments) {
        if (arguments == null || arguments.get("name") == null || arguments.get("name").asText().isBlank()) {
            return AgentToolResult.ofText("缺少必填参数 name。");
        }
        ObjectNode payload = objectMapper.createObjectNode();
        copyIfPresent(payload, arguments, "name");
        copyIfPresent(payload, arguments, "description");
        copyIfPresent(payload, arguments, "contactPerson");
        copyIfPresent(payload, arguments, "contactPhone");
        copyIfPresent(payload, arguments, "email");
        copyIfPresent(payload, arguments, "address");
        copyIfPresent(payload, arguments, "businessLicense");
        copyIfPresent(payload, arguments, "merchantType");
        copyIfPresent(payload, arguments, "logoUrl");

        String summary = "店铺名=" + arguments.get("name").asText()
                + ", 类型=" + (arguments.hasNonNull("merchantType") ? arguments.get("merchantType").asText() : "PERSONAL");
        AgentToolResult.DraftPayload draft = new AgentToolResult.DraftPayload();
        draft.setActionType(ACTION_TYPE);
        draft.setTitle("确认注册店铺");
        draft.setSummary(summary);
        draft.setPayload(payload);
        return AgentToolResult.ofDraft("已生成注册店铺草稿。" + summary, draft);
    }

    private void copyIfPresent(ObjectNode out, JsonNode in, String key) {
        if (in.hasNonNull(key)) {
            out.set(key, in.get(key));
        }
    }
}

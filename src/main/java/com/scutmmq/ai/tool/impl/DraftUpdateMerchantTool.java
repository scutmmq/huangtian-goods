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
public class DraftUpdateMerchantTool implements MallAgentTool {

    public static final String NAME = "draft_update_merchant";
    public static final String ACTION_TYPE = "UPDATE_MERCHANT";

    private final ObjectMapper objectMapper;

    public DraftUpdateMerchantTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override public String name() { return NAME; }
    @Override public ToolMode mode() { return ToolMode.DRAFT_ONLY; }

    @Override
    public AgentToolDefinition definition() {
        JsonNode parameters = new SchemaBuilder(objectMapper)
                .prop("id", "integer", "店铺 ID。必填，且必须属于当前用户。")
                .prop("name", "string", "店铺名称。")
                .prop("description", "string", "简介。")
                .prop("contactPerson", "string", "联系人。")
                .prop("contactPhone", "string", "联系电话。")
                .prop("email", "string", "邮箱。")
                .prop("address", "string", "经营地址。")
                .prop("businessLicense", "string", "营业执照号。")
                .prop("merchantType", "string", "类型: PERSONAL / ENTERPRISE。")
                .prop("logoUrl", "string", "Logo 图片 URL。")
                .require("id")
                .build();
        return AgentToolDefinition.builder()
                .name(NAME)
                .description("生成“修改店铺资料”草稿。等待用户确认后才会执行。绝不能修改店铺状态、销量、评分等系统字段。")
                .parameters(parameters)
                .build();
    }

    @Override
    public AgentToolResult execute(JsonNode arguments) {
        if (arguments == null || arguments.get("id") == null) {
            return AgentToolResult.ofText("缺少必填参数 id。");
        }
        ObjectNode payload = objectMapper.createObjectNode();
        for (String key : new String[]{"id", "name", "description", "contactPerson", "contactPhone",
                "email", "address", "businessLicense", "merchantType", "logoUrl"}) {
            if (arguments.hasNonNull(key)) {
                payload.set(key, arguments.get(key));
            }
        }

        StringBuilder sb = new StringBuilder("店铺 ID=" + arguments.get("id").asText() + " 修改: ");
        payload.fieldNames().forEachRemaining(name -> {
            if (!"id".equals(name)) {
                sb.append(name).append("=").append(payload.get(name).asText()).append("; ");
            }
        });

        AgentToolResult.DraftPayload draft = new AgentToolResult.DraftPayload();
        draft.setActionType(ACTION_TYPE);
        draft.setTitle("确认修改店铺资料");
        draft.setSummary(sb.toString());
        draft.setPayload(payload);
        return AgentToolResult.ofDraft("已生成店铺资料修改草稿。" + sb, draft);
    }
}

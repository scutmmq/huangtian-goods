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
public class DraftUpdateUserProfileTool implements MallAgentTool {

    public static final String NAME = "draft_update_user_profile";
    public static final String ACTION_TYPE = "UPDATE_USER_PROFILE";

    private final ObjectMapper objectMapper;

    public DraftUpdateUserProfileTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override public String name() { return NAME; }
    @Override public ToolMode mode() { return ToolMode.DRAFT_ONLY; }

    @Override
    public AgentToolDefinition definition() {
        JsonNode parameters = new SchemaBuilder(objectMapper)
                .prop("nickName", "string", "昵称，可选。")
                .prop("email", "string", "邮箱，可选。")
                .prop("phone", "string", "手机号，可选。")
                .prop("birthday", "string", "生日，格式 yyyy-MM-dd，可选。")
                .prop("gender", "string", "性别: MALE / FEMALE / OTHER，可选。")
                .prop("address", "string", "默认地址描述，可选。")
                .prop("image", "string", "头像 URL，可选。")
                .build();
        return AgentToolDefinition.builder()
                .name(NAME)
                .description("生成“修改用户资料”草稿。仅允许修改昵称、邮箱、电话、生日、性别、地址、头像，绝不能修改密码或账户状态。等待用户确认后才会执行。")
                .parameters(parameters)
                .build();
    }

    @Override
    public AgentToolResult execute(JsonNode arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return AgentToolResult.ofText("至少需要修改一项字段。");
        }
        ObjectNode payload = objectMapper.createObjectNode();
        for (String key : new String[]{"nickName", "email", "phone", "birthday", "gender", "address", "image"}) {
            if (arguments.hasNonNull(key)) {
                payload.set(key, arguments.get(key));
            }
        }
        if (payload.isEmpty()) {
            return AgentToolResult.ofText("没有可修改的字段。");
        }

        StringBuilder sb = new StringBuilder("将修改: ");
        payload.fieldNames().forEachRemaining(name ->
                sb.append(name).append("=").append(payload.get(name).asText()).append("; "));

        AgentToolResult.DraftPayload draft = new AgentToolResult.DraftPayload();
        draft.setActionType(ACTION_TYPE);
        draft.setTitle("确认修改个人资料");
        draft.setSummary(sb.toString());
        draft.setPayload(payload);
        return AgentToolResult.ofDraft("已生成资料修改草稿。" + sb, draft);
    }
}

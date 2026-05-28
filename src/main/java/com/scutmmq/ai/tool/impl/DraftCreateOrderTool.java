package com.scutmmq.ai.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scutmmq.ai.tool.AgentToolDefinition;
import com.scutmmq.ai.tool.AgentToolResult;
import com.scutmmq.ai.tool.MallAgentTool;
import com.scutmmq.ai.tool.SchemaBuilder;
import com.scutmmq.ai.tool.ToolMode;
import com.scutmmq.dto.UserDTO;
import com.scutmmq.entity.Product;
import com.scutmmq.entity.UserAddress;
import com.scutmmq.mapper.MerchantUserMapper;
import com.scutmmq.mapper.UserAddressMapper;
import com.scutmmq.service.ProductService;
import com.scutmmq.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DraftCreateOrderTool implements MallAgentTool {

    public static final String NAME = "draft_create_order";
    public static final String ACTION_TYPE = "CREATE_ORDER";

    private final ObjectMapper objectMapper;
    private final ProductService productService;
    private final MerchantUserMapper merchantUserMapper;
    private final UserAddressMapper userAddressMapper;

    public DraftCreateOrderTool(ObjectMapper objectMapper,
                                ProductService productService,
                                MerchantUserMapper merchantUserMapper,
                                UserAddressMapper userAddressMapper) {
        this.objectMapper = objectMapper;
        this.productService = productService;
        this.merchantUserMapper = merchantUserMapper;
        this.userAddressMapper = userAddressMapper;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ToolMode mode() {
        return ToolMode.DRAFT_ONLY;
    }

    @Override
    public AgentToolDefinition definition() {
        JsonNode parameters = new SchemaBuilder(objectMapper)
                .prop("productId", "integer", "商品 ID。")
                .prop("quantity", "integer", "购买数量，正整数。")
                .prop("shippingAddressId", "integer", "收货地址 ID。")
                .prop("remark", "string", "订单备注，可选。")
                .require("productId", "quantity", "shippingAddressId")
                .build();
        return AgentToolDefinition.builder()
                .name(NAME)
                .description("当用户明确想购买商品时使用。本工具只生成订单确认草稿，不会真的下单。用户在确认卡片上点击“确认下单”后才会触发后端真正下单。")
                .parameters(parameters)
                .build();
    }

    @Override
    public AgentToolResult execute(JsonNode arguments) {
        if (arguments == null || arguments.get("productId") == null
                || arguments.get("quantity") == null || arguments.get("shippingAddressId") == null) {
            return AgentToolResult.ofText("缺少必填参数 productId / quantity / shippingAddressId。");
        }

        Long productId = arguments.get("productId").asLong();
        int quantity = arguments.get("quantity").asInt();
        Long shippingAddressId = arguments.get("shippingAddressId").asLong();

        if (quantity <= 0) {
            return AgentToolResult.ofText("数量必须为正整数。");
        }

        // 业务前置校验：把后端 OrderService 会做的关键校验提前到草稿生成阶段，
        // 失败信息以 tool 结果回喂模型，模型会自然地告诉用户为什么买不了，
        // 避免用户点完“确认下单”才看到失败。
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null || currentUser.getId() == null) {
            return AgentToolResult.ofText("当前未登录，无法下单。");
        }
        Long userId = currentUser.getId();

        Product product;
        try {
            product = productService.lambdaQuery().eq(Product::getId, productId).one();
        } catch (Exception e) {
            log.warn("[AI][TOOL][draft_create_order] query product failed id={}: {}", productId, e.getMessage());
            return AgentToolResult.ofText("查询商品失败：" + e.getMessage());
        }
        if (product == null) {
            return AgentToolResult.ofText("商品 ID=" + productId + " 不存在。");
        }
        if (product.getIsActive() != null && product.getIsActive() == 0) {
            return AgentToolResult.ofText("商品「" + product.getName() + "」已下架，无法购买。");
        }
        if (product.getStockQuantity() == null || product.getStockQuantity() < quantity) {
            return AgentToolResult.ofText("商品「" + product.getName() + "」库存不足，仅剩 "
                    + (product.getStockQuantity() == null ? 0 : product.getStockQuantity()) + " 件。");
        }

        // 关键：禁止购买本人店铺的商品（与 OrderServiceImpl.addOrder 同步规则）
        Long myMerchantId = null;
        try {
            myMerchantId = merchantUserMapper.getMerchantIdByUserId(userId);
        } catch (Exception ignored) {}
        if (myMerchantId != null && myMerchantId.equals(product.getMerchantId())) {
            log.info("[AI][TOOL][draft_create_order] reject self-store purchase userId={} merchantId={} productId={}",
                    userId, myMerchantId, productId);
            return AgentToolResult.ofText("商品「" + product.getName()
                    + "」属于你自己的店铺，不能下单购买自己的商品。请提示用户切换其他商家的商品。");
        }

        // 收货地址归属校验
        UserAddress address = userAddressMapper.selectById(shippingAddressId);
        if (address == null || !userId.equals(address.getUserId())) {
            return AgentToolResult.ofText("收货地址 ID=" + shippingAddressId + " 不存在或不属于当前用户。");
        }

        // 校验全部通过，生成草稿
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("productId", arguments.get("productId"));
        payload.set("quantity", arguments.get("quantity"));
        payload.set("shippingAddressId", arguments.get("shippingAddressId"));
        if (arguments.hasNonNull("remark")) {
            payload.set("remark", arguments.get("remark"));
        }

        String summary = "商品「" + product.getName() + "」"
                + " ¥" + product.getPrice() + " × " + quantity
                + "，收货：" + safe(address.getRecipient()) + " " + safe(address.getPhone())
                + (arguments.hasNonNull("remark") ? "，备注：" + arguments.get("remark").asText() : "");

        AgentToolResult.DraftPayload draft = new AgentToolResult.DraftPayload();
        draft.setActionType(ACTION_TYPE);
        draft.setTitle("确认下单：" + product.getName());
        draft.setSummary(summary);
        draft.setPayload(payload);
        return AgentToolResult.ofDraft("已生成下单确认草稿，等待用户在确认卡片上点击确认。" + summary, draft);
    }

    private String safe(String s) { return s == null ? "" : s; }
}

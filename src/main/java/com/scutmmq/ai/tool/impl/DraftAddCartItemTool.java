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
import com.scutmmq.mapper.MerchantUserMapper;
import com.scutmmq.service.ProductService;
import com.scutmmq.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DraftAddCartItemTool implements MallAgentTool {

    public static final String NAME = "draft_add_cart_item";
    public static final String ACTION_TYPE = "ADD_CART_ITEM";

    private final ObjectMapper objectMapper;
    private final ProductService productService;
    private final MerchantUserMapper merchantUserMapper;

    public DraftAddCartItemTool(ObjectMapper objectMapper,
                                ProductService productService,
                                MerchantUserMapper merchantUserMapper) {
        this.objectMapper = objectMapper;
        this.productService = productService;
        this.merchantUserMapper = merchantUserMapper;
    }

    @Override public String name() { return NAME; }

    @Override public ToolMode mode() { return ToolMode.DRAFT_ONLY; }

    @Override
    public AgentToolDefinition definition() {
        JsonNode parameters = new SchemaBuilder(objectMapper)
                .prop("productId", "integer", "商品 ID。")
                .prop("quantity", "integer", "加入数量,正整数。")
                .require("productId", "quantity")
                .build();
        return AgentToolDefinition.builder()
                .name(NAME)
                .description("生成「加入购物车」草稿卡片。当用户想加入购物车时必须调用本工具。只有调用本工具前端才会弹出「确认加入购物车」卡片与按钮供用户点击。")
                .parameters(parameters)
                .build();
    }

    @Override
    public AgentToolResult execute(JsonNode arguments) {
        if (arguments == null || arguments.get("productId") == null || arguments.get("quantity") == null) {
            return AgentToolResult.ofText("缺少必填参数 productId / quantity。");
        }

        Long productId = arguments.get("productId").asLong();
        int quantity = arguments.get("quantity").asInt();

        // 数量校验
        if (quantity <= 0) {
            return AgentToolResult.ofText("数量必须为正整数。");
        }

        // 业务前置校验：把后端 CartService 会做的关键校验提前到草稿生成阶段，
        // 失败信息以 tool 结果回喂模型,避免用户点完"确认加购"才看到失败。
        // 校验集合与 DraftCreateOrderTool 对齐(收货地址校验因加购不需要,故省略)。
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null || currentUser.getId() == null) {
            return AgentToolResult.ofText("当前未登录,无法加入购物车。");
        }
        Long userId = currentUser.getId();

        Product product;
        try {
            product = productService.lambdaQuery().eq(Product::getId, productId).one();
        } catch (Exception e) {
            log.warn("[AI][TOOL][draft_add_cart_item] query product failed id={}: {}", productId, e.getMessage());
            return AgentToolResult.ofText("查询商品失败:" + e.getMessage());
        }
        if (product == null) {
            return AgentToolResult.ofText("商品 ID=" + productId + " 不存在。");
        }
        if (product.getIsActive() != null && product.getIsActive() == 0) {
            return AgentToolResult.ofText("商品「" + product.getName() + "」已下架,无法加入购物车。");
        }
        if (product.getStockQuantity() == null || product.getStockQuantity() < quantity) {
            int remain = product.getStockQuantity() == null ? 0 : product.getStockQuantity();
            return AgentToolResult.ofText("商品「" + product.getName() + "」库存不足,仅剩 " + remain + " 件。");
        }

        // 自家店铺商品禁止加购(与 CartService 现有的"不能购买自己店铺商品"语义一致)
        Long myMerchantId = null;
        try {
            myMerchantId = merchantUserMapper.getMerchantIdByUserId(userId);
        } catch (Exception ignored) {}
        if (myMerchantId != null && myMerchantId.equals(product.getMerchantId())) {
            log.info("[AI][TOOL][draft_add_cart_item] reject self-store cart userId={} merchantId={} productId={}",
                    userId, myMerchantId, productId);
            return AgentToolResult.ofText("商品「" + product.getName()
                    + "」属于你自己的店铺,不能加入自己的商品到购物车。");
        }

        // 校验全部通过,生成草稿
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("productId", arguments.get("productId"));
        payload.set("quantity", arguments.get("quantity"));

        String summary = "商品「" + product.getName() + "」"
                + " ¥" + product.getPrice() + " × " + quantity;
        AgentToolResult.DraftPayload draft = new AgentToolResult.DraftPayload();
        draft.setActionType(ACTION_TYPE);
        draft.setTitle("确认加入购物车:" + product.getName());
        draft.setSummary(summary);
        draft.setPayload(payload);
        return AgentToolResult.ofDraft("已生成加入购物车草稿,等待用户在确认卡片上点击确认。" + summary, draft);
    }
}

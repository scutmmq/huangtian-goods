package com.scutmmq.ai.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scutmmq.ai.tool.AgentToolDefinition;
import com.scutmmq.ai.tool.AgentToolResult;
import com.scutmmq.ai.tool.MallAgentTool;
import com.scutmmq.ai.tool.SchemaBuilder;
import com.scutmmq.ai.tool.ToolMode;
import com.scutmmq.entity.Result;
import com.scutmmq.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GetProductDetailTool implements MallAgentTool {

    private static final String NAME = "get_product_detail";

    private final ProductService productService;
    private final ObjectMapper objectMapper;

    public GetProductDetailTool(ProductService productService,
                                ObjectMapper objectMapper) {
        this.productService = productService;
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
                .prop("productId", "integer", "商品 ID，必填。")
                .require("productId")
                .build();
        return AgentToolDefinition.builder()
                .name(NAME)
                .description("根据商品 id 获取商品详情，包括描述、库存、评分、所属商家。用户想看某个具体商品的详细信息时使用。")
                .parameters(parameters)
                .build();
    }

    @Override
    public AgentToolResult execute(JsonNode arguments) {
        JsonNode pid = arguments == null ? null : arguments.get("productId");
        if (pid == null || pid.isNull()) {
            return AgentToolResult.ofText("缺少必填参数 productId。");
        }
        Long productId;
        try {
            productId = pid.isNumber() ? pid.longValue() : Long.parseLong(pid.asText());
        } catch (Exception e) {
            return AgentToolResult.ofText("参数 productId 不是合法整数。");
        }

        Result result = productService.getProductDetail(productId);
        if (result == null) {
            return AgentToolResult.ofText("未找到商品。");
        }
        if (result.getCode() == null || result.getCode() != 1) {
            return AgentToolResult.ofText("查询失败: " + result.getMsg());
        }
        try {
            return AgentToolResult.ofText(objectMapper.writeValueAsString(result.getData()));
        } catch (Exception e) {
            log.warn("Serialize product detail failed: {}", e.getMessage());
            return AgentToolResult.ofText(String.valueOf(result.getData()));
        }
    }
}

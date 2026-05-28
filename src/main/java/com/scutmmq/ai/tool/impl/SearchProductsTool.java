package com.scutmmq.ai.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scutmmq.ai.tool.AgentToolDefinition;
import com.scutmmq.ai.tool.AgentToolResult;
import com.scutmmq.ai.tool.MallAgentTool;
import com.scutmmq.ai.tool.SchemaBuilder;
import com.scutmmq.ai.tool.ToolMode;
import com.scutmmq.entity.PageResult;
import com.scutmmq.entity.Result;
import com.scutmmq.service.ProductService;
import com.scutmmq.vo.ProductVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class SearchProductsTool implements MallAgentTool {

    private static final String NAME = "search_products";

    private final ProductService productService;
    private final ObjectMapper objectMapper;

    public SearchProductsTool(ProductService productService,
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
                .prop("keyword", "string", "商品关键词，例如 排球、手机、文具。可选。")
                .prop("categoryId", "integer", "分类 ID，可选。")
                .prop("minPrice", "integer", "最低价格，单位元，可选。")
                .prop("maxPrice", "integer", "最高价格，单位元，可选。")
                .prop("page", "integer", "页码，默认 1。")
                .prop("pageSize", "integer", "返回数量，默认 5，最大 10。")
                .build();
        return AgentToolDefinition.builder()
                .name(NAME)
                .description("根据关键词、价格范围、分类搜索商城中已上架的真实商品。" +
                        "用户想找商品、对比商品或寻求推荐时使用。返回商品 id、名称、价格、库存、评分、商家。")
                .parameters(parameters)
                .build();
    }

    @Override
    public AgentToolResult execute(JsonNode arguments) {
        String keyword = optString(arguments, "keyword");
        Long categoryId = optLong(arguments, "categoryId");
        Integer minPrice = optInt(arguments, "minPrice");
        Integer maxPrice = optInt(arguments, "maxPrice");
        Integer page = optInt(arguments, "page");
        Integer pageSize = optInt(arguments, "pageSize");
        if (page == null || page <= 0) {
            page = 1;
        }
        if (pageSize == null || pageSize <= 0) {
            pageSize = 5;
        }
        if (pageSize > 10) {
            pageSize = 10;
        }
        log.info("[AI][TOOL][search_products] keyword={} categoryId={} minPrice={} maxPrice={} page={} pageSize={}",
                keyword, categoryId, minPrice, maxPrice, page, pageSize);

        Result result = productService.getProducts(categoryId, null, keyword, minPrice, maxPrice, 1, page, pageSize);
        int firstCount = countProducts(result);
        log.info("[AI][TOOL][search_products] primary search returned {} items", firstCount);

        // 中文模糊兜底：商城 SQL 是死板的 like '%keyword%'，"衣服" 无法命中 "毛衣"，"裙子" 无法命中 "连衣裙"。
        // 当主关键词没找到时，把关键词拆成单个汉字 / 单 token，分别去搜，最后合并去重。
        if (firstCount == 0 && keyword != null && keyword.length() >= 2) {
            List<String> variants = buildKeywordVariants(keyword);
            if (!variants.isEmpty()) {
                log.info("[AI][TOOL][search_products] primary miss, fallback variants={}", variants);
                Map<Long, ProductVO> merged = new LinkedHashMap<>();
                for (String v : variants) {
                    Result r = productService.getProducts(categoryId, null, v, minPrice, maxPrice, 1, 1, pageSize);
                    for (ProductVO p : extractProducts(r)) {
                        if (p != null && p.getId() != null) {
                            merged.putIfAbsent(p.getId(), p);
                        }
                        if (merged.size() >= pageSize) break;
                    }
                    if (merged.size() >= pageSize) break;
                }
                if (!merged.isEmpty()) {
                    log.info("[AI][TOOL][search_products] fallback found {} items", merged.size());
                    String inline = formatProductList(new ArrayList<>(merged.values()), keyword);
                    return AgentToolResult.ofText(inline);
                }
            }
        }

        return AgentToolResult.ofText(formatResult(result));
    }

    /**
     * 把关键词拆成可能的子关键词，用于 fallback 模糊搜索：
     * - "衣服" -> ["衣", "服"]
     * - "裙子" -> ["裙", "子"]
     * - "山地自行车" -> ["山地自行车" 已经在主搜里失败, 这里再拆 "山地", "自行车", "山", "自"...]
     * 简单策略：先按 2 字滑窗，然后按单字。子串去重保留出现顺序。
     */
    private List<String> buildKeywordVariants(String keyword) {
        List<String> out = new ArrayList<>();
        if (keyword == null) return out;
        String k = keyword.trim();
        if (k.length() <= 1) return out;

        // 2-gram 滑窗（仅当原词长 >= 3 才有意义，避免和原 keyword 撞）
        if (k.length() >= 3) {
            for (int i = 0; i + 2 <= k.length(); i++) {
                String g = k.substring(i, i + 2);
                if (!g.equals(k) && !out.contains(g)) out.add(g);
            }
        }
        // 单字
        for (int i = 0; i < k.length(); i++) {
            String c = String.valueOf(k.charAt(i));
            // 跳过 ASCII（"衣" 是单字但 "T" 单字搜没意义）
            if (c.codePointAt(0) < 0x80) continue;
            if (!out.contains(c)) out.add(c);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private int countProducts(Result result) {
        return extractProducts(result).size();
    }

    @SuppressWarnings("unchecked")
    private List<ProductVO> extractProducts(Result result) {
        if (result == null || result.getCode() == null || result.getCode() != 1) return List.of();
        Object data = result.getData();
        if (data instanceof PageResult<?> pr) {
            List<?> rows = pr.getRows();
            if (rows != null) return (List<ProductVO>) rows;
        } else if (data instanceof List<?> ll) {
            return (List<ProductVO>) ll;
        }
        return List.of();
    }

    private String formatProductList(List<ProductVO> products, String originalKeyword) {
        try {
            Map<String, Object> wrap = new LinkedHashMap<>();
            wrap.put("note", "主关键词 [" + originalKeyword + "] 未直接命中，已使用拆字模糊搜索，结果可能为近似匹配。");
            wrap.put("total", products.size());
            wrap.put("products", products);
            return objectMapper.writeValueAsString(wrap);
        } catch (Exception e) {
            return String.valueOf(products);
        }
    }

    private String formatResult(Result result) {
        if (result == null) {
            return "无返回结果。";
        }
        if (result.getCode() == null || result.getCode() != 1) {
            return "搜索失败: " + (result.getMsg() == null ? "未知错误" : result.getMsg());
        }
        try {
            return objectMapper.writeValueAsString(result.getData());
        } catch (Exception e) {
            log.warn("Failed to serialize search results: {}", e.getMessage());
            return String.valueOf(result.getData());
        }
    }

    private String optString(JsonNode node, String name) {
        if (node == null) return null;
        JsonNode v = node.get(name);
        if (v == null || v.isNull() || v.asText().isBlank()) return null;
        return v.asText();
    }

    private Integer optInt(JsonNode node, String name) {
        if (node == null) return null;
        JsonNode v = node.get(name);
        if (v == null || v.isNull()) return null;
        return v.isNumber() ? v.intValue() : tryParseInt(v.asText());
    }

    private Long optLong(JsonNode node, String name) {
        if (node == null) return null;
        JsonNode v = node.get(name);
        if (v == null || v.isNull()) return null;
        if (v.isNumber()) return v.longValue();
        try {
            return Long.parseLong(v.asText());
        } catch (Exception e) {
            return null;
        }
    }

    private Integer tryParseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return null;
        }
    }
}

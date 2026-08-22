package com.scutmmq.ai.tool.impl;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scutmmq.ai.tool.AgentToolResult;
import com.scutmmq.dto.UserDTO;
import com.scutmmq.entity.Product;
import com.scutmmq.mapper.MerchantUserMapper;
import com.scutmmq.service.ProductService;
import com.scutmmq.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * B1:加购草稿前置校验。
 * 验证 DraftAddCartItemTool 在 quantity<=0 / 商品不存在 / 商品下架 / 库存不足 / 自家店铺
 * 这 5 个场景下给出 model 回读文本(不是草稿),校验通过时给出草稿 + 友好 summary。
 *
 * 关联待解决问题文档 P0 #2。
 */
class DraftAddCartItemToolTest {

    private ProductService productService;
    private MerchantUserMapper merchantUserMapper;
    private ObjectMapper objectMapper;
    private DraftAddCartItemTool tool;

    private static final Long PRODUCT_ID = 100L;
    private static final Long MERCHANT_ID = 99L;
    private static final Long MY_MERCHANT_ID = 7L;

    @BeforeEach
    void setUp() {
        productService = mock(ProductService.class);
        merchantUserMapper = mock(MerchantUserMapper.class);
        objectMapper = new ObjectMapper();
        tool = new DraftAddCartItemTool(objectMapper, productService, merchantUserMapper);

        // 设置当前登录用户
        UserDTO currentUser = new UserDTO();
        currentUser.setId(1L);
        UserHolder.saveUser(currentUser);

        // 默认无商家关系(下方按需覆盖)
        lenient().when(merchantUserMapper.getMerchantIdByUserId(any())).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void rejects_quantityLessThanOne() {
        JsonNode args = argsJson(PRODUCT_ID, 0);
        AgentToolResult result = tool.execute(args);
        String text = result.getContent();
        assertNotNull(text, "result should be text on failure");
        assertTrue(text.contains("数量") && text.contains("正整数"),
                "应告知用户数量必须为正整数,实际: " + text);
    }

    @Test
    void rejects_productNotFound() {
        mockProductQuery(PRODUCT_ID, null);
        JsonNode args = argsJson(PRODUCT_ID, 2);
        AgentToolResult result = tool.execute(args);
        String text = result.getContent();
        assertNotNull(text, "商品不存在应返回文本结果而非草稿");
        assertTrue(text.contains("不存在") || text.contains("找不到"),
                "应明确告诉用户商品不存在,实际: " + text);
    }

    @Test
    void rejects_productTakenDown() {
        Product product = newProduct(PRODUCT_ID, "测试商品", 10, MERCHANT_ID, 0);
        mockProductQuery(PRODUCT_ID, product);
        JsonNode args = argsJson(PRODUCT_ID, 1);
        AgentToolResult result = tool.execute(args);
        String text = result.getContent();
        assertNotNull(text);
        assertTrue(text.contains("下架"),
                "已下架商品应明确提示,实际: " + text);
    }

    @Test
    void rejects_insufficientStock() {
        Product product = newProduct(PRODUCT_ID, "测试商品", 2, MERCHANT_ID, 1);
        // 仅留 2 件,但要买 5 件 → 库存不足。默认 helper 把 stock 写成 price+99=101,
        // 这里显式覆盖成 2,以制造"库存不足"场景。
        setField(product, "stockQuantity", 2);
        mockProductQuery(PRODUCT_ID, product);
        JsonNode args = argsJson(PRODUCT_ID, 5);
        AgentToolResult result = tool.execute(args);
        String text = result.getContent();
        assertNotNull(text);
        assertTrue(text.contains("库存"),
                "库存不足应明确提示,实际: " + text);
        assertTrue(text.contains("测试商品"),
                "应展示商品名,便于用户定位,实际: " + text);
    }

    @Test
    void rejects_selfStorePurchase() {
        Product product = newProduct(PRODUCT_ID, "本店商品", 10, MERCHANT_ID, 1);
        mockProductQuery(PRODUCT_ID, product);
        lenient().when(merchantUserMapper.getMerchantIdByUserId(1L)).thenReturn(MY_MERCHANT_ID);
        // 关键:让商品归属于自家店铺
        // 用 reflection 设置 merchantId 因为 setter 不一定暴露
        // 这里我们直接通过反射修改(测试场景)
        setField(product, "merchantId", MY_MERCHANT_ID);

        JsonNode args = argsJson(PRODUCT_ID, 1);
        AgentToolResult result = tool.execute(args);
        String text = result.getContent();
        assertNotNull(text, "自家店铺商品应拒绝加入购物车");
        assertTrue(text.contains("自己的店铺") || text.contains("自家"),
                "应明确告知不能加入自家店铺商品,实际: " + text);
    }

    @Test
    void producesDraft_withProductNameAndPrice_whenValidationPasses() {
        Product product = newProduct(PRODUCT_ID, "苹果", 10, MERCHANT_ID, 1);
        // 让 product.stockQuantity >= quantity
        setField(product, "stockQuantity", 100);
        mockProductQuery(PRODUCT_ID, product);
        lenient().when(merchantUserMapper.getMerchantIdByUserId(1L)).thenReturn(MY_MERCHANT_ID);
        // 关键:商品 merchantId != 当前用户的 myMerchantId
        // MY_MERCHANT_ID = 7L, MERCHANT_ID 已经在 newProduct 时 = 99L → 不冲突

        JsonNode args = argsJson(PRODUCT_ID, 3);
        AgentToolResult result = tool.execute(args);
        assertNotNull(result.getDraft(), "校验全部通过时应返回 DraftPayload");
        AgentToolResult.DraftPayload draft = result.getDraft();

        assertEquals(DraftAddCartItemTool.ACTION_TYPE, draft.getActionType());
        String summary = draft.getSummary();
        assertNotNull(summary);
        assertTrue(summary.contains("苹果"), "summary 应展示商品名,实际: " + summary);
        assertTrue(summary.contains("10") || summary.contains("¥"),
                "summary 应展示单价,实际: " + summary);
        assertTrue(summary.contains("3"),
                "summary 应展示数量,实际: " + summary);
        // 不应再展示"商品 ID=100"
        assertTrue(!summary.contains("ID="),
                "summary 不应再展示商品 ID 字段,实际: " + summary);
    }

    // ─────────────────────── helper ───────────────────────

    private JsonNode argsJson(Long productId, int quantity) {
        var n = objectMapper.createObjectNode();
        n.put("productId", productId);
        n.put("quantity", quantity);
        return n;
    }

    private Product newProduct(Long id, String name, int price, Long merchantId, int isActive) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setPrice(BigDecimal.valueOf(price));
        p.setStockQuantity(price + 99); // 用 price 字段占位让库存够,后续按需覆盖
        p.setMerchantId(merchantId);
        p.setIsActive(isActive);
        return p;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void mockProductQuery(Long productId, Product product) {
        LambdaQueryChainWrapper<Product> chain = mock(LambdaQueryChainWrapper.class);
        lenient().when(chain.eq(any(), any())).thenReturn(chain);
        lenient().when(chain.one()).thenReturn(product);
        lenient().when(productService.lambdaQuery()).thenReturn(chain);
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("failed to set field " + fieldName, e);
        }
    }
}

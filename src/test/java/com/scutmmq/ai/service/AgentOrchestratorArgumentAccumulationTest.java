package com.scutmmq.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C10 修复回归测试 — Tool call arguments 跨 chunk 累积。
 *
 * <p>DeepSeek 流式 tool_call arguments 按字符拆分发送(典型序列:`{` →
 * `"keyword` → `":"自行车",` → `"minPrice":...` → `}`)。旧
 * AgentOrchestrator.appendArguments 在 current 是空 `{}` 时把
 * current.toString()(即 `"{}"`)与 delta 拼接 → 产生 `{}{...}` 永远不合 JSON,
 * args 永远停在 __streaming__,工具收到 keyword=null。
 *
 * <p>修法:统一抽取 raw 字符串 + 拼接 + parse。空 {} 当作 null 处理。
 *
 * 这里通过反射调私有方法验证累积语义。
 */
class AgentOrchestratorArgumentAccumulationTest {

    private static final ObjectMapper M = new ObjectMapper();

    private JsonNode callAppend(JsonNode current, String delta) throws Exception {
        Method m = AgentOrchestrator.class.getDeclaredMethod("appendArguments", JsonNode.class, String.class);
        m.setAccessible(true);
        return (JsonNode) m.invoke(null, current, delta);
    }

    @Test
    void singleChunkEmptyArgs_staysEmpty() throws Exception {
        // 第一帧 args="" → 当前 + delta 都空,appendArguments 直接返回 current
        JsonNode result = callAppend(null, "");
        // 行为契约:empty delta 不修改 current,这里 current 是 null
        // 真实链路里 parseArgumentsSafely 才会处理 raw=="",返回空 {}
        assertEquals(null, result);
    }

    @Test
    void singleChunkPartialBrace_aloneReturnsStreamingObject() throws Exception {
        // 第一帧 args="{" → 当前 null,delta 不空 → 应累积为 {"__streaming__":"{"}
        JsonNode result = callAppend(null, "{");
        assertTrue(result.has("__streaming__"));
        assertEquals("{", result.get("__streaming__").asText());
    }

    @Test
    void singleChunkCompleteJson_parsesImmediately() throws Exception {
        JsonNode result = callAppend(null, "{\"keyword\":\"自行车\"}");
        assertTrue(result.has("keyword"));
        assertEquals("自行车", result.get("keyword").asText());
    }

    @Test
    void splitChunksAccumulate_correctFinalParse() throws Exception {
        // 模拟 DeepSeek 按字符拆分的累积
        JsonNode current = callAppend(null, "{");
        current = callAppend(current, "\"keyword\":\"自行车\"");
        current = callAppend(current, "");

        // 当前帧还没闭合,但 raw 已累积
        assertTrue(current.has("__streaming__"));
        assertEquals("{\"keyword\":\"自行车\"", current.get("__streaming__").asText());

        // 闭合帧
        current = callAppend(current, "}");
        assertTrue(current.has("keyword"), "闭合后应解析成功,不能再停在 __streaming__");
        assertEquals("自行车", current.get("keyword").asText());
        assertTrue(!current.has("__streaming__"));
    }

    @Test
    void splitChunksFromEmptyObject_currentWasEmptyBraces() throws Exception {
        // 关键场景:第一帧 args="" → 空 {};第二帧 args='{"keyword":"X"}'
        // 旧逻辑 bug:{} + '{"keyword":"X"}' = "{}{\"keyword\":\"X\"}" 永远不合 JSON
        // 新逻辑:把空 {} 当 null,只取 delta
        JsonNode emptyArgs = M.createObjectNode();  // 这是 parseArgumentsSafely 在 args="" 时返回的
        JsonNode result = callAppend(emptyArgs, "{\"keyword\":\"单车\"}");

        assertTrue(result.has("keyword"), "空 {} + 完整 JSON → 必须解析出 keyword");
        assertEquals("单车", result.get("keyword").asText());
    }

    @Test
    void splitChunksWithMultipleFields_allPreserved() throws Exception {
        // draft_create_order 场景:productId/quantity/shippingAddressId 三参数
        JsonNode current = callAppend(null, "{\"pro");
        current = callAppend(current, "ductId\":5,\"qua");
        current = callAppend(current, "ntity\":100,\"ship");
        current = callAppend(current, "pingAddressId\":16}");

        assertEquals(5, current.get("productId").asInt());
        assertEquals(100, current.get("quantity").asInt());
        assertEquals(16, current.get("shippingAddressId").asInt());
    }

    @Test
    void multiToolCallIndices_keepsEachAccumulationIndependent() throws Exception {
        // 验证不串行污染:两个 tool call 的累积是独立的(由 mergeToolCallDelta 管,不在这里测)
        // 这里只测 appendArguments 的纯函数语义
        JsonNode current = callAppend(null, "{\"a\":1");
        current = callAppend(current, ",\"b\":2}");
        assertEquals(1, current.get("a").asInt());
        assertEquals(2, current.get("b").asInt());
    }

    @Test
    void chineseCharsInArguments_parsedCorrectly() throws Exception {
        JsonNode current = callAppend(null, "{\"keyword\":\"山");
        current = callAppend(current, "地自行车\"}");
        assertEquals("山地自行车", current.get("keyword").asText());
    }

    @Test
    void chunk1_is_just_open_brace_then_full_object_parsesCleanly() throws Exception {
        // C10.1 关键场景:DeepSeek 真实 chunking 模式 —
        // chunk1 = "{", chunk2 = '{"page": 1, "pageSize": 10}'
        // 旧算法 prev="{" + delta='{"page":...}' = "{{..." 永远不合 JSON
        JsonNode current = callAppend(null, "{");
        current = callAppend(current, "{\"page\": 1, \"pageSize\": 10}");
        assertTrue(current.has("page"), "chunk1='{' + chunk2=完整对象 必须正确解析");
        assertTrue(current.has("pageSize"));
        assertEquals(1, current.get("page").asInt());
        assertEquals(10, current.get("pageSize").asInt());
    }

    @Test
    void emptyBracesStreaming_isOverwrittenByFullObject() throws Exception {
        // 双重防御:即使 __streaming__="" 但 prev 已经写入 "{}",也要正确合并
        // 这里直接构造一个 {"__streaming__": "{}"} 模拟
        ObjectNode stubCurrent = M.createObjectNode();
        stubCurrent.put("__streaming__", "{}");
        JsonNode result = callAppend(stubCurrent, "{\"a\":1}");
        assertTrue(result.has("a"));
        assertEquals(1, result.get("a").asInt());
    }
}

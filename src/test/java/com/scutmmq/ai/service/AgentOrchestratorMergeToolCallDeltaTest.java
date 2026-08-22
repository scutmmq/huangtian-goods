package com.scutmmq.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scutmmq.ai.client.ToolCallDelta;
import com.scutmmq.ai.tool.AgentToolCall;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C11 修复回归 — ToolCallDelta 合并逻辑 bug。
 *
 * <p>2026-08-23 凌晨最终事故:搜索"自行车"返回全表前 5,草稿参数全部 null。
 * 根因:mergeToolCallDelta 的 index fallback 逻辑错误 — 当 chunk 的 id 为空
 * (纯 args 续传)时,要求 slot.id 也为空才合并,导致 args 被孤立成 phantom entry,
 * 后续带完整 id/name 的 chunk 通过 by-id 匹配到 phantom,继承 phantom 的 args。
 *
 * <p>修法:chunk.id 为空时,无论 slot 是否有 id,都按 index 合并;
 * chunk.id/name 都非空且不匹配已有 entry 时,才视为新 tool_call 创建 entry。
 */
class AgentOrchestratorMergeToolCallDeltaTest {

    private static final ObjectMapper M = new ObjectMapper();

    private void merge(List<AgentToolCall> toolCalls, ToolCallDelta d) throws Exception {
        Method m = AgentOrchestrator.class.getDeclaredMethod("mergeToolCallDelta", List.class, ToolCallDelta.class);
        m.setAccessible(true);
        m.invoke(null, toolCalls, d);
    }

    private ToolCallDelta delta(int index, String id, String name, String args) {
        return new ToolCallDelta(index, id, name, args);
    }

    @Test
    void realToolCall_fullFirstChunk_createsEntry() throws Exception {
        List<AgentToolCall> toolCalls = new ArrayList<>();
        merge(toolCalls, delta(0, "call_00", "search_products", ""));

        assertEquals(1, toolCalls.size());
        AgentToolCall tc = toolCalls.get(0);
        assertEquals("call_00", tc.getId());
        assertEquals("search_products", tc.getName());
    }

    @Test
    void argsContinuationChunk_emptyId_mergesByIndexEvenIfSlotHasId() throws Exception {
        // 关键场景:chunk 1 创建 entry,chunk 2 是 args 续传(id 为空)
        // 旧逻辑因 slot.id="call_00" 非空,跳过 index fallback,创建 phantom entry
        // 新逻辑:chunk.id 为空 → 强制按 index 合并
        List<AgentToolCall> toolCalls = new ArrayList<>();
        merge(toolCalls, delta(0, "call_00", "search_products", ""));
        merge(toolCalls, delta(0, "", "", "{\"keyword\":\"自行车\"}"));

        assertEquals(1, toolCalls.size(), "args 续传不应创建 phantom,必须只有 1 个 tool_call");
        AgentToolCall tc = toolCalls.get(0);
        assertEquals("call_00", tc.getId());
        assertEquals("search_products", tc.getName());
        assertTrue(tc.getArguments().has("keyword"));
        assertEquals("自行车", tc.getArguments().get("keyword").asText());
    }

    @Test
    void multipleParallelToolCalls_eachKeepsOwnArgs() throws Exception {
        // 用户场景:search + get_my_addresses + get_my_merchant 并行
        // 关键:每个 tool_call 的 args 不能跨污染
        List<AgentToolCall> toolCalls = new ArrayList<>();
        // search_products chunk1
        merge(toolCalls, delta(0, "call_00", "search_products", ""));
        // search_products chunk2 (args 续传)
        merge(toolCalls, delta(0, "", "", "{\"keyword\":\"自行车\"}"));
        // get_my_addresses chunk1
        merge(toolCalls, delta(1, "call_01", "get_my_addresses", ""));
        // get_my_merchant chunk1
        merge(toolCalls, delta(2, "call_02", "get_my_merchant", ""));

        assertEquals(3, toolCalls.size());
        // 验证 args 没串
        assertTrue(toolCalls.get(0).getArguments().has("keyword"));
        assertEquals("自行车", toolCalls.get(0).getArguments().get("keyword").asText());
        // call_01 and call_02 should have empty args (no contamination from search_products)
        assertEquals(0, toolCalls.get(1).getArguments().size(), "get_my_addresses 不能继承 search_products 的 args");
        assertEquals(0, toolCalls.get(2).getArguments().size(), "get_my_merchant 不能继承 search_products 的 args");
    }

    @Test
    void newToolCallWithFullIdAndName_doesNotMergeIntoExistingSlot() throws Exception {
        // chunk A: search_products full
        // chunk B: 第二个并行工具 full (id+name 都非空)
        // chunk B 应该创建新 entry,不应该合并到 chunk A 的 slot
        List<AgentToolCall> toolCalls = new ArrayList<>();
        merge(toolCalls, delta(0, "call_00", "search_products", ""));
        merge(toolCalls, delta(1, "call_01", "get_my_addresses", ""));

        assertEquals(2, toolCalls.size());
        assertEquals("call_00", toolCalls.get(0).getId());
        assertEquals("call_01", toolCalls.get(1).getId());
    }

    @Test
    void draftCreateOrder_fullArgsInOneChunk_parsesCorrectly() throws Exception {
        // 用户报的核心 bug:draft_create_order 收到 keyword=null
        // 根因是 args 被 phantom 继承
        List<AgentToolCall> toolCalls = new ArrayList<>();
        merge(toolCalls, delta(0, "call_00", "draft_create_order", "{\"productId\":5,\"quantity\":100,\"shippingAddressId\":16}"));

        assertEquals(1, toolCalls.size());
        AgentToolCall tc = toolCalls.get(0);
        assertEquals("draft_create_order", tc.getName());
        assertEquals(5, tc.getArguments().get("productId").asInt());
        assertEquals(100, tc.getArguments().get("quantity").asInt());
        assertEquals(16, tc.getArguments().get("shippingAddressId").asInt());
    }

    @Test
    void mixedChunks_realCallThenPhantomArgsThenSecondCall() throws Exception {
        // 复刻用户日志中的实际模式:
        // chunk A: search_products full
        // chunk B: args 续传 (id 空,index=0)
        // chunk C: 第二个工具 (full)
        List<AgentToolCall> toolCalls = new ArrayList<>();
        merge(toolCalls, delta(0, "call_00", "search_products", ""));
        merge(toolCalls, delta(0, "", "", "{\"keyword\":\"单车\"}"));
        merge(toolCalls, delta(1, "call_01", "get_my_addresses", ""));

        assertEquals(2, toolCalls.size());
        // search_products 收到 keyword="单车"
        assertEquals("单车", toolCalls.get(0).getArguments().get("keyword").asText());
        // get_my_addresses 不能继承单车
        assertEquals(0, toolCalls.get(1).getArguments().size());
    }
}

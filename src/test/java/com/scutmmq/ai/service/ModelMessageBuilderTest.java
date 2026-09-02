package com.scutmmq.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scutmmq.ai.tool.AgentToolCall;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ModelMessageBuilderTest {

    private ModelMessageBuilder builder;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        builder = new ModelMessageBuilder(objectMapper);
    }

    @Test
    void testBuildAssistantToolCallMessage() {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("query", "裙子");
        AgentToolCall call = new AgentToolCall("call_123", "search_products", args);

        Map<String, Object> msg = builder.buildAssistantToolCallMessage("我帮您搜索", List.of(call), null);
        assertEquals("assistant", msg.get("role"));
        assertEquals("我帮您搜索", msg.get("content"));
        assertTrue(msg.containsKey("tool_calls"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) msg.get("tool_calls");
        assertEquals(1, toolCalls.size());
        assertEquals("call_123", toolCalls.get(0).get("id"));
        assertEquals("function", toolCalls.get(0).get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> func = (Map<String, Object>) toolCalls.get(0).get("function");
        assertEquals("search_products", func.get("name"));
        assertEquals("{\"query\":\"裙子\"}", func.get("arguments"));
    }

    @Test
    void testBuildAssistantToolCallMessage_EmptyContentBecomesNull() {
        AgentToolCall call = new AgentToolCall("", "get_my_addresses", objectMapper.createObjectNode());
        Map<String, Object> msg = builder.buildAssistantToolCallMessage("", List.of(call), null);

        assertEquals("assistant", msg.get("role"));
        assertNull(msg.get("content"));
        assertFalse(call.getId().isBlank());
    }

    @Test
    void testBuildToolResponseMessage_NoLegacyName() {
        Map<String, Object> msg = builder.buildToolResponseMessage("call_123", "get_my_addresses", "{\"total\":2}");
        assertEquals("tool", msg.get("role"));
        assertEquals("call_123", msg.get("tool_call_id"));
        assertEquals("{\"total\":2}", msg.get("content"));
        assertFalse(msg.containsKey("name"), "OpenAI standard tool message should not contain 'name'");
    }
}

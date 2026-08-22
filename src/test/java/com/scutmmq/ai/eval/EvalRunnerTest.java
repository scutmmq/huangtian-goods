package com.scutmmq.ai.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.scutmmq.ai.service.AgentOrchestrator;
import com.scutmmq.ai.tool.AgentToolResult;
import com.scutmmq.dto.UserDTO;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * B2.Checkpoint4:EvalRunner 单元测试。
 * 通过 mock AgentOrchestrator,验证断言逻辑正确,
 * 不实际触发 AI API(单测 <100ms 完成)。
 */
class EvalRunnerTest {

    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    @Test
    void parsesSearchProductsCase() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/eval/sample-search-products.yaml")) {
            assertNotNull(in, "sample file should exist on classpath");
            EvalCase ec = yaml.readValue(in, EvalCase.class);
            assertEquals("search-products", ec.getName());
            assertEquals("search_products", ec.getExpectTool());
            assertTrue(ec.getExpectKeywords().contains("饼干"));
            assertEquals(1001L, ec.getUserId());
        }
    }

    @Test
    void runOne_passWhenAllChecksSatisfied() {
        AgentOrchestrator orch = mock(AgentOrchestrator.class);
        AgentOrchestrator.AgentResult result = new AgentOrchestrator.AgentResult(
                "推荐你买饼干呀,这里有几款:",
                null,
                List.of(new AgentOrchestrator.ToolExecutionRecord("search_products", null, "[…]"))
        );
        when(orch.runStreaming(any(UserDTO.class), any(), anyString(), any())).thenReturn(result);

        EvalRunner runner = new EvalRunner(orch);
        EvalCase ec = new EvalCase();
        ec.setName("x");
        ec.setMessage("找饼干");
        ec.setExpectTool("search_products");
        ec.setExpectKeywords(List.of("饼干"));

        EvalVerdict v = runner.runOne(ec);
        assertTrue(v.isPassed(), "verdict should pass: " + v.getReason());
        assertEquals(List.of("search_products"), v.getToolsCalled());
    }

    @Test
    void runOne_failsWhenToolMissing() {
        AgentOrchestrator orch = mock(AgentOrchestrator.class);
        AgentOrchestrator.AgentResult result = new AgentOrchestrator.AgentResult(
                "你好,我可以帮你做菜呢",
                null,
                List.of()
        );
        when(orch.runStreaming(any(), any(), anyString(), any())).thenReturn(result);

        EvalRunner runner = new EvalRunner(orch);
        EvalCase ec = new EvalCase();
        ec.setName("y");
        ec.setMessage("找饼干");
        ec.setExpectTool("search_products");

        EvalVerdict v = runner.runOne(ec);
        assertEquals(false, v.isPassed());
        assertTrue(v.getReason().contains("missing tool"));
    }

    @Test
    void runOne_failsWhenNoToolsViolated() {
        AgentOrchestrator orch = mock(AgentOrchestrator.class);
        AgentOrchestrator.AgentResult result = new AgentOrchestrator.AgentResult(
                "你好",
                null,
                List.of(new AgentOrchestrator.ToolExecutionRecord("get_my_orders", null, "[]"))
        );
        when(orch.runStreaming(any(), any(), anyString(), any())).thenReturn(result);

        EvalRunner runner = new EvalRunner(orch);
        EvalCase ec = new EvalCase();
        ec.setName("z");
        ec.setMessage("你好");
        ec.setExpectNoTools(true);

        EvalVerdict v = runner.runOne(ec);
        assertEquals(false, v.isPassed());
        assertTrue(v.getReason().contains("unexpected tools"));
    }

    @Test
    void runOne_failsWhenNoKeywordHit() {
        AgentOrchestrator orch = mock(AgentOrchestrator.class);
        AgentOrchestrator.AgentResult result = new AgentOrchestrator.AgentResult(
                "嗯嗯好的",
                null,
                List.of(new AgentOrchestrator.ToolExecutionRecord("search_products", null, "[]"))
        );
        when(orch.runStreaming(any(), any(), anyString(), any())).thenReturn(result);

        EvalRunner runner = new EvalRunner(orch);
        EvalCase ec = new EvalCase();
        ec.setName("k");
        ec.setMessage("找东西");
        ec.setExpectTool("search_products");
        ec.setExpectKeywords(List.of("饼干"));

        EvalVerdict v = runner.runOne(ec);
        assertEquals(false, v.isPassed());
        assertTrue(v.getReason().contains("keyword"));
    }

    @Test
    void runOne_swallowsExceptions() {
        AgentOrchestrator orch = mock(AgentOrchestrator.class);
        when(orch.runStreaming(any(), any(), anyString(), any())).thenThrow(new RuntimeException("AI down"));

        EvalRunner runner = new EvalRunner(orch);
        EvalCase ec = new EvalCase();
        ec.setName("e");
        ec.setMessage("x");

        EvalVerdict v = runner.runOne(ec);
        assertEquals(false, v.isPassed());
        assertTrue(v.getReason().contains("exception"));
    }

    // ============ C0-C12 回归断言单元测试 ============

    @Test
    void runOne_failsWhenReplyContainsDsml() throws Exception {
        // C7:reply 含 DSML 标签应被检测为失败
        AgentOrchestrator orch = mock(AgentOrchestrator.class);
        AgentOrchestrator.AgentResult result = new AgentOrchestrator.AgentResult(
                "好的,先调工具 <｜｜DSML｜｜tool_calls>...</｜｜DSML｜｜tool_calls> 看看",
                null,
                List.of(new AgentOrchestrator.ToolExecutionRecord("search_products", null, "{}"))
        );
        when(orch.runStreaming(any(), any(), anyString(), any())).thenReturn(result);

        EvalRunner runner = new EvalRunner(orch);
        EvalCase ec = new EvalCase();
        ec.setName("dsml-leak");
        ec.setMessage("test");
        ec.setExpectReplyNoDsml(true);

        EvalVerdict v = runner.runOne(ec);
        assertEquals(false, v.isPassed(), "DSML in reply must fail C7 check");
        assertTrue(v.getReason().contains("DSML"));
    }

    @Test
    void runOne_passesWhenReplyHasNoDsml() throws Exception {
        AgentOrchestrator orch = mock(AgentOrchestrator.class);
        AgentOrchestrator.AgentResult result = new AgentOrchestrator.AgentResult(
                "好的,推荐你买橡皮擦",
                null,
                List.of()
        );
        when(orch.runStreaming(any(), any(), anyString(), any())).thenReturn(result);

        EvalRunner runner = new EvalRunner(orch);
        EvalCase ec = new EvalCase();
        ec.setName("no-dsml");
        ec.setMessage("hi");
        ec.setExpectReplyNoDsml(true);

        EvalVerdict v = runner.runOne(ec);
        assertEquals(true, v.isPassed());
    }

    @Test
    void runOne_failsWhenToolArgsMissingRequiredField() throws Exception {
        // C11:工具 args 必须含指定字段(防 phantom 跨污染)
        com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode badArgs = m.createObjectNode();
        // 故意不放 keyword,模拟 C11 bug:args 串到不相关字段

        AgentOrchestrator orch = mock(AgentOrchestrator.class);
        AgentOrchestrator.AgentResult result = new AgentOrchestrator.AgentResult(
                "好的",
                null,
                List.of(new AgentOrchestrator.ToolExecutionRecord("search_products", badArgs, "{}"))
        );
        when(orch.runStreaming(any(), any(), anyString(), any())).thenReturn(result);

        EvalRunner runner = new EvalRunner(orch);
        EvalCase ec = new EvalCase();
        ec.setName("c11-args");
        ec.setMessage("test");
        java.util.Map<String, java.util.Map<String, Object>> expect = new java.util.HashMap<>();
        java.util.Map<String, Object> expectedSearch = new java.util.HashMap<>();
        expectedSearch.put("keyword", "自行车");
        expect.put("search_products", expectedSearch);
        ec.setExpectToolArgsContains(expect);

        EvalVerdict v = runner.runOne(ec);
        assertEquals(false, v.isPassed(), "missing keyword should fail C11 check");
        assertTrue(v.getReason().contains("C11"));
    }

    @Test
    void runOne_passesWhenToolArgsHaveRequiredFields() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode goodArgs = m.createObjectNode();
        goodArgs.put("keyword", "自行车");

        AgentOrchestrator orch = mock(AgentOrchestrator.class);
        AgentOrchestrator.AgentResult result = new AgentOrchestrator.AgentResult(
                "ok",
                null,
                List.of(new AgentOrchestrator.ToolExecutionRecord("search_products", goodArgs, "{}"))
        );
        when(orch.runStreaming(any(), any(), anyString(), any())).thenReturn(result);

        EvalRunner runner = new EvalRunner(orch);
        EvalCase ec = new EvalCase();
        ec.setName("c11-ok");
        ec.setMessage("test");
        java.util.Map<String, java.util.Map<String, Object>> expect = new java.util.HashMap<>();
        java.util.Map<String, Object> expectedSearch = new java.util.HashMap<>();
        expectedSearch.put("keyword", "自行车");
        expect.put("search_products", expectedSearch);
        ec.setExpectToolArgsContains(expect);

        EvalVerdict v = runner.runOne(ec);
        assertEquals(true, v.isPassed());
    }

    @Test
    void runOne_failsWhenMaxToolExecutionsExceeded() {
        // C8:工具执行次数 > 上限 = 死循环
        AgentOrchestrator orch = mock(AgentOrchestrator.class);
        List<AgentOrchestrator.ToolExecutionRecord> manyCalls = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            manyCalls.add(new AgentOrchestrator.ToolExecutionRecord(
                    "search_products", null, "{}"));
        }
        AgentOrchestrator.AgentResult result = new AgentOrchestrator.AgentResult(
                "ok", null, manyCalls);
        when(orch.runStreaming(any(), any(), anyString(), any())).thenReturn(result);

        EvalRunner runner = new EvalRunner(orch);
        EvalCase ec = new EvalCase();
        ec.setName("c8-loop");
        ec.setMessage("test");
        ec.setExpectMaxToolExecutions(5);

        EvalVerdict v = runner.runOne(ec);
        assertEquals(false, v.isPassed());
        assertTrue(v.getReason().contains("C8"));
    }

    @Test
    void runOne_failsWhenNoDraftButExpected() {
        // C0:必须产出 draft
        AgentOrchestrator orch = mock(AgentOrchestrator.class);
        AgentOrchestrator.AgentResult result = new AgentOrchestrator.AgentResult(
                "好的", null, List.of());
        when(orch.runStreaming(any(), any(), anyString(), any())).thenReturn(result);

        EvalRunner runner = new EvalRunner(orch);
        EvalCase ec = new EvalCase();
        ec.setName("c0-no-draft");
        ec.setMessage("test");
        ec.setExpectDraft(true);

        EvalVerdict v = runner.runOne(ec);
        assertEquals(false, v.isPassed());
        assertTrue(v.getReason().contains("no draft"));
    }

    @Test
    void runOne_failsWhenDraftPayloadMissingRequiredField() throws Exception {
        // C0:draft payload 必须含指定字段(防幻觉参数)
        com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode badPayload = m.createObjectNode();
        badPayload.put("productId", 5);
        // 故意不放 quantity
        AgentToolResult.DraftPayload dp = new AgentToolResult.DraftPayload(
                "CREATE_ORDER", "test", "summary", badPayload);

        AgentOrchestrator orch = mock(AgentOrchestrator.class);
        AgentOrchestrator.AgentResult result = new AgentOrchestrator.AgentResult(
                "ok", dp, List.of());
        when(orch.runStreaming(any(), any(), anyString(), any())).thenReturn(result);

        EvalRunner runner = new EvalRunner(orch);
        EvalCase ec = new EvalCase();
        ec.setName("c0-bad-payload");
        ec.setMessage("test");
        ec.setExpectDraft(true);
        java.util.Map<String, Object> expectedPayload = new java.util.HashMap<>();
        expectedPayload.put("productId", "5");
        expectedPayload.put("quantity", "100");
        ec.setExpectDraftArgsContains(expectedPayload);

        EvalVerdict v = runner.runOne(ec);
        assertEquals(false, v.isPassed());
        assertTrue(v.getReason().contains("draft payload"));
    }

    @Test
    void runOne_passesWhenDraftPayloadHasAllRequiredFields() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode goodPayload = m.createObjectNode();
        goodPayload.put("productId", 5);
        goodPayload.put("quantity", 100);
        goodPayload.put("shippingAddressId", 16);
        AgentToolResult.DraftPayload dp = new AgentToolResult.DraftPayload(
                "CREATE_ORDER", "test", "summary", goodPayload);

        AgentOrchestrator orch = mock(AgentOrchestrator.class);
        AgentOrchestrator.AgentResult result = new AgentOrchestrator.AgentResult(
                "ok", dp, List.of());
        when(orch.runStreaming(any(), any(), anyString(), any())).thenReturn(result);

        EvalRunner runner = new EvalRunner(orch);
        EvalCase ec = new EvalCase();
        ec.setName("c0-ok");
        ec.setMessage("test");
        ec.setExpectDraft(true);
        java.util.Map<String, Object> expectedPayload = new java.util.HashMap<>();
        expectedPayload.put("productId", "5");
        expectedPayload.put("quantity", "100");
        expectedPayload.put("shippingAddressId", "16");
        ec.setExpectDraftArgsContains(expectedPayload);

        EvalVerdict v = runner.runOne(ec);
        assertEquals(true, v.isPassed(), "expected pass, got reason: " + v.getReason());
    }
}

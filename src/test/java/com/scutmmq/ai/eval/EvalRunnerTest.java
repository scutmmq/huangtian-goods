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
}

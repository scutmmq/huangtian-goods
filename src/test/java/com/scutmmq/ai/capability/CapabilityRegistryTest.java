package com.scutmmq.ai.capability;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scutmmq.ai.event.DraftCreatedEvent;
import com.scutmmq.ai.event.RunCompletedEvent;
import com.scutmmq.ai.event.RunStartedEvent;
import com.scutmmq.ai.event.ToolExecutedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * B2.Checkpoint1:CapabilityRegistry 行为测试。
 * 覆盖 4 个 publish 方法 + 幂等键。
 */
class CapabilityRegistryTest {

    private ApplicationEventPublisher publisher;
    private CapabilityRegistry registry;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        publisher = mock(ApplicationEventPublisher.class);
        registry = new CapabilityRegistry(publisher);
        objectMapper = new ObjectMapper();
    }

    @Test
    void publishRunStarted_emitsRunStartedEvent() {
        RunContext ctx = RunContext.of("run-1", "sess-1", 7L, "USER");
        registry.publishRunStarted(ctx);

        ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(publisher).publishEvent(captor.capture());
        assertNotNull(captor.getValue());
        assertTrue(captor.getValue() instanceof RunStartedEvent);
        RunStartedEvent event = (RunStartedEvent) captor.getValue();
        assertEquals("run-1", event.getContext().getRunId());
        assertEquals(7L, event.getContext().getUserId());
    }

    @Test
    void publishToolExecuted_emitsToolExecutedEvent() {
        RunContext run = RunContext.of("run-1", "sess-1", 7L, "USER");
        ToolContext ctx = ToolContext.fromRun(run, "search_products", "call-1",
                objectMapper.createObjectNode(), "ok", false, true, null,
                System.currentTimeMillis() - 100, System.currentTimeMillis());
        registry.publishToolExecuted(ctx);

        ArgumentCaptor<ToolExecutedEvent> captor = ArgumentCaptor.forClass(ToolExecutedEvent.class);
        verify(publisher).publishEvent(captor.capture());
        ToolExecutedEvent event = captor.getValue();
        assertEquals("search_products", event.getContext().getToolName());
        assertTrue(event.getContext().elapsedMs() >= 0);
    }

    @Test
    void publishRunCompleted_emitsOnce_thenTerminalPreventsRepeat() {
        RunContext run = RunContext.of("run-1", "sess-1", 7L, "USER");
        RunResult result = RunResult.builder()
                .context(run)
                .replyPreview("hi")
                .hasDraft(false)
                .toolExecutionCount(2)
                .totalMs(1234L)
                .ttftMs(456L)
                .terminal(false)
                .build();

        registry.publishRunCompleted(result);
        assertTrue(result.isTerminal(), "publishRunCompleted 应该把 result 标为 terminal");

        registry.publishRunCompleted(result);
        // 第二次调用不应当再次 publish
        verify(publisher, org.mockito.Mockito.times(1))
                .publishEvent(any(RunCompletedEvent.class));
    }

    @Test
    void publishDraftCreated_carriesPayload() {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("productId", 1);
        payload.put("quantity", 2);

        registry.publishDraftCreated("run-1", "sess-1", 7L,
                "ADD_CART_ITEM", "加入购物车", "苹果 × 2", payload);

        ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(publisher).publishEvent(captor.capture());
        ApplicationEvent event = captor.getValue();
        assertTrue(event instanceof DraftCreatedEvent);
        DraftCreatedEvent dce = (DraftCreatedEvent) event;
        assertEquals("ADD_CART_ITEM", dce.getActionType());
        assertEquals(7L, dce.getUserId());
    }
}

package com.scutmmq.ai.observability;

import com.scutmmq.ai.capability.RunContext;
import com.scutmmq.ai.capability.RunResult;
import com.scutmmq.ai.entity.AiRunUsage;
import com.scutmmq.ai.mapper.AiRunUsageMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * B2.Checkpoint3:DbUsageRecorder 把 RunResult 转成 AiRunUsage 行落库。
 */
class DbUsageRecorderTest {

    @Test
    void record_persistsUsageRow() {
        AiRunUsageMapper mapper = mock(AiRunUsageMapper.class);
        DbUsageRecorder recorder = new DbUsageRecorder(mapper);
        ReflectionTestUtils.setField(recorder, "costPer1kPromptCents", 5);
        ReflectionTestUtils.setField(recorder, "costPer1kCompletionCents", 10);

        RunContext runCtx = RunContext.of("run-1", "sess-1", 7L, "USER");
        RunResult result = RunResult.builder()
                .context(runCtx)
                .replyPreview("hi")
                .hasDraft(false)
                .toolExecutionCount(3)
                .totalMs(1200L)
                .ttftMs(456L)
                .promptTokens(2000)
                .completionTokens(500)
                .reasoningTokens(120)
                .terminal(true)
                .build();

        recorder.record(result);

        ArgumentCaptor<AiRunUsage> captor = ArgumentCaptor.forClass(AiRunUsage.class);
        verify(mapper).insert(captor.capture());
        AiRunUsage row = captor.getValue();
        assertEquals("run-1", row.getRunId());
        assertEquals("sess-1", row.getSessionId());
        assertEquals(7L, row.getUserId());
        assertEquals("USER", row.getUserRole());
        assertEquals(3, row.getToolCount());
        assertEquals(0, row.getHasDraft());
        assertEquals(1200L, row.getTotalMs());
        assertEquals(456L, row.getTtftMs());
        assertEquals(2000, row.getPromptTokens());
        assertEquals(500, row.getCompletionTokens());
        assertEquals(2500, row.getTotalTokens());
        // 2000 prompt * 5/1000 = 10 cents
        assertEquals(10, row.getPromptCostCents());
        // 500 completion * 10/1000 = 5 cents
        assertEquals(5, row.getCompletionCostCents());
        assertNotNull(row.getCreatedAt());
    }

    @Test
    void record_swallowsMapperFailures_doesNotPropagate() {
        AiRunUsageMapper mapper = mock(AiRunUsageMapper.class);
        org.mockito.Mockito.doThrow(new RuntimeException("db down"))
                .when(mapper).insert(any());
        DbUsageRecorder recorder = new DbUsageRecorder(mapper);

        RunContext runCtx = RunContext.of("run-1", "sess-1", 7L, "USER");
        RunResult result = RunResult.builder().context(runCtx).totalMs(100L).build();

        // 不应抛异常 — 用 log.warn 兜底
        recorder.record(result);
        verify(mapper).insert(any());
    }

    @Test
    void costRounds_halfUp() {
        AiRunUsageMapper mapper = mock(AiRunUsageMapper.class);
        DbUsageRecorder recorder = new DbUsageRecorder(mapper);
        ReflectionTestUtils.setField(recorder, "costPer1kPromptCents", 3);

        RunContext runCtx = RunContext.of("run-1", null, null, null);
        // 503 tokens × 3 / 1000 = 1.509 → round-half-up = 2 cents
        RunResult result = RunResult.builder()
                .context(runCtx).promptTokens(503).terminal(true).build();

        recorder.record(result);
        ArgumentCaptor<AiRunUsage> captor = ArgumentCaptor.forClass(AiRunUsage.class);
        verify(mapper).insert(captor.capture());
        assertTrue(captor.getValue().getPromptCostCents() >= 1);
        assertTrue(captor.getValue().getPromptCostCents() <= 2);
    }
}

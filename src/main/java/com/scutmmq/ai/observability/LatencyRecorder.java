package com.scutmmq.ai.observability;

import com.scutmmq.ai.capability.AiCapability;
import com.scutmmq.ai.event.RunCompletedEvent;
import com.scutmmq.ai.event.ToolExecutedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Stage 1 可观测 — 延迟统计(工具级 + Run 级)。
 *
 * 当前实现:仅 INFO 日志输出(供临时分析和 locust 跑量时排查用)。
 * Stage 6 接入 Prometheus + Micrometer 时替换为 Timer/Counter。
 *
 * 不异步:不阻塞 Run 主流程(Spring 同步 EventListener 仍会被调用,但只 log 一行)。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.capability.observability.enabled", havingValue = "true")
public class LatencyRecorder implements AiCapability {

    @Override
    public String name() {
        return "observability-latency";
    }

    @Override
    public int order() {
        return 20;
    }

    @EventListener
    public void onToolExecuted(ToolExecutedEvent event) {
        var ctx = event.getContext();
        if (ctx == null) return;
        if (log.isInfoEnabled()) {
            log.info("[AI][LATENCY] tool runId={} tool={} elapsedMs={} success={}",
                    ctx.getRunId(), ctx.getToolName(), ctx.elapsedMs(), ctx.isSuccess());
        }
    }

    @EventListener
    public void onRunCompleted(RunCompletedEvent event) {
        var r = event.getResult();
        if (r == null) return;
        String runId = r.getContext() == null ? null : r.getContext().getRunId();
        log.info("[AI][LATENCY] runId={} totalMs={} ttftMs={} toolCount={}",
                runId, r.getTotalMs(), r.getTtftMs(), r.getToolExecutionCount());
    }
}

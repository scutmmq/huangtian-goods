package com.scutmmq.ai.observability;

import com.scutmmq.ai.capability.AiCapability;
import com.scutmmq.ai.event.RunCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Stage 1 可观测 — token 用量 + 成本落库。
 *
 * 监听 RunCompletedEvent,转发给 {@link UsageRecorder}(默认 Noop,
 * ai.capability.observability.enabled=true 切换为 DbUsageRecorder)。
 *
 * 异步执行 + 失败隔离(@Async + EventListener 默认 try/catch 不会中断主流程)。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.capability.observability.enabled", havingValue = "true")
@RequiredArgsConstructor
public class TokenUsageRecorder implements AiCapability {

    private final UsageRecorder usageRecorder;

    @Override
    public String name() {
        return "observability";
    }

    @Override
    public int order() {
        return 10;
    }

    /**
     * 主流程完成后异步落库。@Async 配 aiTaskExecutor 让它不占用 Run 主线程。
     */
    @Async("aiTaskExecutor")
    @EventListener
    public void onRunCompleted(RunCompletedEvent event) {
        try {
            usageRecorder.record(event.getResult());
            log.debug("[AI][USAGE] persisted runId={} totalTokens={} totalMs={}",
                    event.getResult().getContext() == null ? null : event.getResult().getContext().getRunId(),
                    event.getResult().totalTokens(),
                    event.getResult().getTotalMs());
        } catch (Exception e) {
            log.warn("[AI][USAGE] recorder threw: {}", e.getMessage(), e);
        }
    }
}

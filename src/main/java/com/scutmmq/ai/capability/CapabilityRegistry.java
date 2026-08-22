package com.scutmmq.ai.capability;

import com.scutmmq.ai.event.DraftCreatedEvent;
import com.scutmmq.ai.event.RunCompletedEvent;
import com.scutmmq.ai.event.RunStartedEvent;
import com.scutmmq.ai.event.ToolExecutedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * AI 能力注册中心。Agent 主流程调用它的 publish* 方法发布事件,
 * 各个 Capability (implements {@link AiCapability}) 用 {@code @EventListener} 响应。
 *
 * 设计要点(策略文档 v1.1 修订):
 * - 只走 ApplicationEventPublisher.publishEvent 单一通道,不再做直接回调
 * - {@link RunResult#terminal} 作为幂等键,防止重复 publish
 * - 默认开启状态下 ctor 排序,name 冲突启动期 fail-fast
 * - AgentOrchestrator 通过 ApplicationContext 注入 ApplicationEventPublisher,
 *   这里只是薄包装,方便 mock 和拓展(例如发布前再做一层过滤)
 *
 * 关闭策略:所有 capability impl 加 @ConditionalOnProperty(name="ai.capability.<name>.enabled"),
 * 当 yaml 全 false 时对应的 @EventListener 全部不会注册,publishEvent 就成了空操作,
 * 主流程 0 性能影响。
 */
@Slf4j
@Component
public class CapabilityRegistry {

    private final ApplicationEventPublisher eventPublisher;

    public CapabilityRegistry(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        log.info("[AI][CAP] CapabilityRegistry initialized; capabilities listen via @EventListener");
    }

    public void publishRunStarted(RunContext ctx) {
        if (ctx == null) return;
        eventPublisher.publishEvent(new RunStartedEvent(this, ctx));
    }

    public void publishToolExecuted(ToolContext ctx) {
        if (ctx == null) return;
        eventPublisher.publishEvent(new ToolExecutedEvent(this, ctx));
    }

    public void publishRunCompleted(RunResult result) {
        if (result == null) return;
        if (result.isTerminal()) {
            log.debug("[AI][CAP] skip publish RunCompletedEvent, already terminal: runId={}",
                    result.getContext() == null ? null : result.getContext().getRunId());
            return;
        }
        result.setTerminal(true);
        eventPublisher.publishEvent(new RunCompletedEvent(this, result));
    }

    public void publishDraftCreated(String runId, String sessionId, Long userId,
                                    String actionType, String title, String summary,
                                    com.fasterxml.jackson.databind.JsonNode payload) {
        eventPublisher.publishEvent(new DraftCreatedEvent(
                this, runId, sessionId, userId, actionType, title, summary, payload));
    }
}

package com.scutmmq.ai.event;

import com.scutmmq.ai.capability.RunContext;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * AI Run 开始事件。CapabilityRegistry 在 AgentOrchestrator.run 入口 publish。
 * 处理者通常用它做:埋点开始 / 加载用户记忆 / 注入额外上下文。
 */
@Getter
public class RunStartedEvent extends ApplicationEvent {

    private final transient RunContext context;

    public RunStartedEvent(Object source, RunContext context) {
        super(source);
        this.context = context;
    }
}

package com.scutmmq.ai.event;

import com.scutmmq.ai.capability.RunResult;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * AI Run 结束事件。AgentOrchestrator.run 出口 publish 一次。
 * 由 RunResult.terminal 做幂等键,防止重试/异常路径重复入库。
 */
@Getter
public class RunCompletedEvent extends ApplicationEvent {

    private final transient RunResult result;

    public RunCompletedEvent(Object source, RunResult result) {
        super(source);
        this.result = result;
    }
}

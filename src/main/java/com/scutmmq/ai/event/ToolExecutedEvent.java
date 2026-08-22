package com.scutmmq.ai.event;

import com.scutmmq.ai.capability.ToolContext;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 工具调用结束事件。每次工具执行完后 publish 一次。
 * 处理者用它做:工具级埋点 / 草稿记录追踪。
 */
@Getter
public class ToolExecutedEvent extends ApplicationEvent {

    private final transient ToolContext context;

    public ToolExecutedEvent(Object source, ToolContext context) {
        super(source);
        this.context = context;
    }
}

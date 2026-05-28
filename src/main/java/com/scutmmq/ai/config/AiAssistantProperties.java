package com.scutmmq.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI 助手运行参数。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai.assistant")
public class AiAssistantProperties {

    /**
     * 发送给模型时携带的最近会话消息数量上限。
     */
    private int maxHistoryMessages = 20;

    /**
     * 写操作草稿过期分钟数。过期后必须重新生成。
     */
    private int draftExpireMinutes = 15;

    /**
     * 单次工具调用循环的最大迭代次数，防止模型陷入死循环。
     * deepseek-v4-flash 等 thinking 模型一次任务常拆成 5-6 个 tool_call，
     * 设太小会让回答停在中间，比如“让我先确认一下你的商家关系”这种半截话。
     */
    private int maxToolIterations = 8;
}

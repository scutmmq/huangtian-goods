package com.scutmmq.ai.capability;

/**
 * AI 助手增强能力统一抽象。Agent 主流程不直接调用子能力,而是通过 {@link CapabilityRegistry}
 * 发布事件,各 capability 用 {@code @EventListener} 监听并响应。
 *
 * 设计原则(策略文档 §2.1 铁律 2):
 * 1. 新增 = 增量,绝不修改现有公共方法签名
 * 2. capability 之间通过事件解耦,不互相直接依赖
 * 3. 默认全部 enabled=false;AgentOrchestrator 行为与未启用时完全一致
 * 4. 失败隔离:单个 capability 抛异常不影响主流程(Spring @EventListener 默认就是 try/隔离)
 *
 * 使用方式:
 * - 实现该接口,加 {@code @Component}
 * - 用 {@code @ConditionalOnProperty(name="ai.capability.<name>.enabled", havingValue="true")}
 *   控制 Bean 创建
 * - 通过 {@code @EventListener} 处理 {@link com.scutmmq.ai.event.RunStartedEvent} 等事件
 *
 * 本接口本身保留空 default 方法,让 capability 可以选择实现部分钩子;
 * 但实际推荐直接用 @EventListener 监听事件(更解耦、更易扩展)。
 */
public interface AiCapability {

    /**
     * 唯一标识,与配置项 ai.capability.<name>.enabled 对齐。
     */
    String name();

    /**
     * 启动顺序,越小越先启动。默认 100。
     */
    default int order() {
        return 100;
    }
}

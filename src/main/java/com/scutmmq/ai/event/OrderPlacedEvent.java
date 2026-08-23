package com.scutmmq.ai.event;

import org.springframework.context.ApplicationEvent;

import java.time.Instant;

/**
 * B3 step6: 用户下单事件。
 *
 * <p>由 {@code OrderServiceImpl} 在订单创建成功(返回 {@code Result.success} 前)发布。
 * {@code UserMemoryEventListener} 收到后调度该用户的偏好重算。
 *
 * <p>继承 {@link ApplicationEvent} 以满足 {@code @TransactionalEventListener}
 * 方法签名 {@code (ApplicationEvent event)} 的类型约束。
 *
 * @param source     事件源(publisher Service,通常是 {@code this})
 * @param userId     下单用户 ID
 * @param orderId    订单 ID
 * @param occurredAt 事件发生时间
 */
public class OrderPlacedEvent extends ApplicationEvent {

    private final Long userId;
    private final Long orderId;
    private final Instant occurredAt;

    public OrderPlacedEvent(Object source, Long userId, Long orderId, Instant occurredAt) {
        super(source);
        this.userId = userId;
        this.orderId = orderId;
        this.occurredAt = occurredAt;
    }

    public Long userId() { return userId; }
    public Long orderId() { return orderId; }
    public Instant occurredAt() { return occurredAt; }
}
package com.scutmmq.ai.event;

import org.springframework.context.ApplicationEvent;

import java.time.Instant;

/**
 * B3 step6: 订单退款完成事件。
 *
 * <p>由 {@code OrderServiceImpl.approveReturn} 在退款完成(订单状态置为 REFUNDED)后发布。
 * 触发用户的偏好重算(退货可能改变用户的偏好画像,例如退货率统计)。
 *
 * <p>继承 {@link ApplicationEvent} 以满足 {@code @TransactionalEventListener}
 * 方法签名 {@code (ApplicationEvent event)} 的类型约束。
 *
 * @param source     事件源
 * @param userId     退款用户 ID
 * @param orderId    退款订单 ID
 * @param occurredAt 事件发生时间
 */
public class OrderRefundedEvent extends ApplicationEvent {

    private final Long userId;
    private final Long orderId;
    private final Instant occurredAt;

    public OrderRefundedEvent(Object source, Long userId, Long orderId, Instant occurredAt) {
        super(source);
        this.userId = userId;
        this.orderId = orderId;
        this.occurredAt = occurredAt;
    }

    public Long userId() { return userId; }
    public Long orderId() { return orderId; }
    public Instant occurredAt() { return occurredAt; }
}
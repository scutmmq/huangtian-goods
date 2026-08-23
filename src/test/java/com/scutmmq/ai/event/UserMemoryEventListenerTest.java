package com.scutmmq.ai.event;

import com.scutmmq.ai.entity.TriggerReason;
import com.scutmmq.ai.service.UserMemoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * B3 step6: UserMemoryEventListener 单元测试。
 *
 * <p>覆盖 4 个核心场景:
 * <ul>
 *   <li>OrderPlacedEvent → {@link TriggerReason#TRIGGER_ORDER}</li>
 *   <li>OrderRefundedEvent → {@link TriggerReason#TRIGGER_OTHER}(注:现有枚举无 ORDER_REFUNDED,见 task-6-report.md)</li>
 *   <li>ProfileUpdatedEvent → {@link TriggerReason#TRIGGER_PROFILE_UPDATE}</li>
 *   <li>MerchantRegisteredEvent → {@link TriggerReason#TRIGGER_ONBOARD}(注:现有枚举无 MERCHANT_REGISTERED)</li>
 *   <li>陌生事件类型不被处理</li>
 * </ul>
 *
 * <p>纯 mock,不依赖 Spring 容器;@TransactionalEventListener 注解由 Spring 容器保障。
 */
class UserMemoryEventListenerTest {

    private UserMemoryService service;
    private UserMemoryEventListener listener;

    @BeforeEach
    void setUp() {
        service = mock(UserMemoryService.class);
        listener = new UserMemoryEventListener(service);
    }

    @Test
    void orderPlacedTriggersRecompute() {
        OrderPlacedEvent event = new OrderPlacedEvent("test", 7L, 100L, Instant.now());
        listener.onEvent(event);
        verify(service).scheduleRecompute(7L, TriggerReason.TRIGGER_ORDER);
    }

    @Test
    void orderRefundedTriggersRecompute() {
        OrderRefundedEvent event = new OrderRefundedEvent("test", 7L, 100L, Instant.now());
        listener.onEvent(event);
        // 现有 TriggerReason 枚举无 ORDER_REFUNDED,沿用 TRIGGER_OTHER 占位(详见 task-6-report.md)
        verify(service).scheduleRecompute(7L, TriggerReason.TRIGGER_OTHER);
    }

    @Test
    void profileUpdatedTriggersRecompute() {
        ProfileUpdatedEvent event = new ProfileUpdatedEvent("test", 7L, List.of("email", "phone"));
        listener.onEvent(event);
        verify(service).scheduleRecompute(7L, TriggerReason.TRIGGER_PROFILE_UPDATE);
    }

    @Test
    void merchantRegisteredTriggersRecompute() {
        MerchantRegisteredEvent event = new MerchantRegisteredEvent("test", 7L, 200L);
        listener.onEvent(event);
        // 现有 TriggerReason 枚举无 MERCHANT_REGISTERED,沿用 TRIGGER_ONBOARD(首次注册)占位
        verify(service).scheduleRecompute(7L, TriggerReason.TRIGGER_ONBOARD);
    }

    @Test
    void unknownEventIsIgnored() {
        // 非 4 类事件的 ApplicationEvent 不应触发重算
        ApplicationEvent unknownEvent = new ApplicationEvent("test") {};
        listener.onEvent(unknownEvent);
        verify(service, never()).scheduleRecompute(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any());
    }
}
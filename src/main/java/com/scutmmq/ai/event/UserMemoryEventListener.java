package com.scutmmq.ai.event;

import com.scutmmq.ai.entity.TriggerReason;
import com.scutmmq.ai.service.UserMemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * B3 step6: 长期记忆领域事件监听器。
 *
 * <p>作为 4 类 UserMemory 相关领域事件的统一入口:
 * <ul>
 *   <li>{@link OrderPlacedEvent}      → 调度重算 (TRIGGER_ORDER)</li>
 *   <li>{@link OrderRefundedEvent}    → 调度重算 (TRIGGER_OTHER,现有枚举无 ORDER_REFUNDED,详见 task-6-report.md)</li>
 *   <li>{@link ProfileUpdatedEvent}   → 调度重算 (TRIGGER_PROFILE_UPDATE)</li>
 *   <li>{@link MerchantRegisteredEvent} → 调度重算 (TRIGGER_ONBOARD,首次注册语义)</li>
 * </ul>
 *
 * <p><b>关键约束:</b>用 {@link TransactionalEventListener} 的
 * {@link TransactionPhase#AFTER_COMMIT} 阶段,确保只在数据库事务成功提交后
 * 才触发记忆重算 — 失败回滚的事务不应触发画像变更,否则会出现"事务回滚了但记忆已被重算"
 * 的不一致。
 *
 * <p>事件类型分发使用 {@code instanceof} 模式匹配(JDK 17 标准特性),陌生事件类型直接忽略,
 * 不抛异常,保证该 listener 不影响主链路。
 *
 * <p>被 publish 的事件本身不需要继承 {@code ApplicationEvent} — Spring 4.2+
 * 支持任意 POJO 作为事件载荷。{@link #onEvent(ApplicationEvent)} 签名沿用
 * {@code ApplicationEvent} 仅为了通过 {@link TransactionalEventListener} 的方法签名解析;
 * 实际传入的对象会是上述 4 个 record 之一或任意继承 ApplicationEvent 的事件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserMemoryEventListener {

    private final UserMemoryService service;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEvent(ApplicationEvent event) {
        if (event instanceof OrderPlacedEvent e) {
            service.scheduleRecompute(e.userId(), TriggerReason.TRIGGER_ORDER);
        } else if (event instanceof OrderRefundedEvent e) {
            // 注:现有 TriggerReason 枚举无 ORDER_REFUNDED,沿用 TRIGGER_OTHER 占位
            service.scheduleRecompute(e.userId(), TriggerReason.TRIGGER_OTHER);
        } else if (event instanceof ProfileUpdatedEvent e) {
            service.scheduleRecompute(e.userId(), TriggerReason.TRIGGER_PROFILE_UPDATE);
        } else if (event instanceof MerchantRegisteredEvent e) {
            // 注:现有 TriggerReason 枚举无 MERCHANT_REGISTERED,沿用 TRIGGER_ONBOARD(首次注册)
            service.scheduleRecompute(e.userId(), TriggerReason.TRIGGER_ONBOARD);
        } else {
            log.debug("[AI][MEMORY] ignored unknown event type: {}",
                    event == null ? "null" : event.getClass().getName());
        }
    }
}
package com.scutmmq.ai.event;

import org.springframework.context.ApplicationEvent;

import java.util.List;

/**
 * B3 step6: 用户资料更新事件。
 *
 * <p>由 {@code UserServiceImpl.updateUser} 在资料更新成功后发布。
 * {@code changedFields} 列出本次改动的字段(如 email/phone/nickName/...),
 * 供后续精细化重算(目前仅作为审计/诊断信息)。
 *
 * <p>继承 {@link ApplicationEvent} 以满足 {@code @TransactionalEventListener}
 * 方法签名 {@code (ApplicationEvent event)} 的类型约束。
 *
 * @param source        事件源
 * @param userId        操作用户 ID
 * @param changedFields 改动字段名列表(可能为空)
 */
public class ProfileUpdatedEvent extends ApplicationEvent {

    private final Long userId;
    private final List<String> changedFields;

    public ProfileUpdatedEvent(Object source, Long userId, List<String> changedFields) {
        super(source);
        this.userId = userId;
        this.changedFields = changedFields == null ? List.of() : List.copyOf(changedFields);
    }

    public Long userId() { return userId; }
    public List<String> changedFields() { return changedFields; }
}
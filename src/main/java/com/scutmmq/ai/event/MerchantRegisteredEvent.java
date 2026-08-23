package com.scutmmq.ai.event;

import org.springframework.context.ApplicationEvent;

/**
 * B3 step6: 商家注册成功事件。
 *
 * <p>由 {@code MerchantServiceImpl.addMerchant} 在商家表 + merchant_user 关系写入成功后发布。
 * 触发该用户的偏好重算(用户身份从"买家"升级为"买家+商家",画像应包含商家信息)。
 *
 * <p>继承 {@link ApplicationEvent} 以满足 {@code @TransactionalEventListener}
 * 方法签名 {@code (ApplicationEvent event)} 的类型约束。
 *
 * @param source     事件源
 * @param userId     注册商家用户 ID
 * @param merchantId 新建商家 ID
 */
public class MerchantRegisteredEvent extends ApplicationEvent {

    private final Long userId;
    private final Long merchantId;

    public MerchantRegisteredEvent(Object source, Long userId, Long merchantId) {
        super(source);
        this.userId = userId;
        this.merchantId = merchantId;
    }

    public Long userId() { return userId; }
    public Long merchantId() { return merchantId; }
}
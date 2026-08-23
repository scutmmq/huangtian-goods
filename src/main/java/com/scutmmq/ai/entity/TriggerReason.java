package com.scutmmq.ai.entity;

/**
 * B3 long-term memory 重算触发原因。
 *
 * <p>记录到 audit 表的 {@code triggered_by} 字段,供后续按触发原因聚合分析
 * (例:Nightly Cron 触发占比 / 用户行为触发的占比)。
 */
public enum TriggerReason {

    /** 用户下单后,触发偏好重算。 */
    TRIGGER_ORDER,

    /** 用户加入/移除购物车后触发。 */
    TRIGGER_CART,

    /** 用户更新个人资料(收货地址 / 偏好类目)后触发。 */
    TRIGGER_PROFILE_UPDATE,

    /** 用户主动重置(reset)后触发(应当走 reset 路径,此处仅供诊断)。 */
    TRIGGER_RESET,

    /** 周期 Cron(默认每天凌晨 3 点)触发的全量重算。 */
    TRIGGER_CRON,

    /** 兜底 RateLimiter 降级路径(Redis 不可用时本地调度)。 */
    TRIGGER_DEGRADED,

    /** 首次注册时初始化画像。 */
    TRIGGER_ONBOARD,

    /** 其它未分类场景。 */
    TRIGGER_OTHER
}

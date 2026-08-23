package com.scutmmq.ai.service;

import java.util.concurrent.TimeUnit;

/**
 * 简易令牌桶 — 用于 Redis SETNX 失败时的本地兜底速率限制。
 *
 * <p>为什么不引 Guava:项目无 Guava 依赖(per Task 9 决议);本类用 synchronized
 * + nanoTime 实现等价功能,核心参数 {@code capacity} 决定突发容量,
 * {@code refillPerSecond} 决定稳态速率。线程安全由 synchronized 保证。
 */
final class LocalTokenBucket {

    private final long capacity;
    private final long refillIntervalNanos;
    private long tokens;
    private long lastRefillNanos;

    /**
     * @param capacity         桶容量(突发允许的瞬时请求数)
     * @param refillPerSecond  稳态每秒补充令牌数;≥ 1
     */
    LocalTokenBucket(long capacity, long refillPerSecond) {
        this.capacity = capacity;
        this.refillIntervalNanos = TimeUnit.SECONDS.toNanos(1) / Math.max(1, refillPerSecond);
        this.tokens = capacity;
        this.lastRefillNanos = System.nanoTime();
    }

    /**
     * 尝试获取 1 个令牌。返回 true 表示允许,false 表示被限流。
     * 懒补充:距上次补充 elapsed 时间内累计补回 refillInterval × k 个令牌,
     * 上限为 capacity。最后再扣 1 个。
     */
    synchronized boolean tryAcquire() {
        long now = System.nanoTime();
        long elapsed = now - lastRefillNanos;
        if (elapsed > 0) {
            long refilled = elapsed / refillIntervalNanos;
            if (refilled > 0) {
                tokens = Math.min(capacity, tokens + refilled);
                lastRefillNanos += refilled * refillIntervalNanos;
            }
        }
        if (tokens > 0) {
            tokens--;
            return true;
        }
        return false;
    }
}

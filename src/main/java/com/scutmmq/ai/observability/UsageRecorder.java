package com.scutmmq.ai.observability;

import com.scutmmq.ai.capability.RunResult;

/**
 * 用量记录器抽象。让 {@link com.scutmmq.ai.observability.TokenUsageRecorder} 把 RunResult
 * 落库的策略可替换:
 * - 内存 / 文件 / DB / Kafka / Micrometer 全部可能。
 *
 * 当前实现:
 * - NoopUsageRecorder:默认,不落库
 * - DbUsageRecorder:写 ai_run_usage 表(ai.capability.observability.enabled=true 时使用)
 *
 * 后续 Stage 6 接入 Micrometer + Prometheus 时,可加 PrometheusUsageRecorder。
 */
public interface UsageRecorder {

    /**
     * 记录一次 Run 的用量。可抛异常,但实现里应该尽量不影响主流程
     * (外部用 @Async + try/catch 隔离)。
     */
    void record(RunResult result);
}

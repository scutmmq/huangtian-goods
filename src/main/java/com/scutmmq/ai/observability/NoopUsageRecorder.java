package com.scutmmq.ai.observability;

import com.scutmmq.ai.capability.RunResult;

/**
 * 默认的 no-op 用量记录器。当 ai.capability.observability.enabled=false 时使用,
 * 不写任何数据,不消耗资源。
 */
public class NoopUsageRecorder implements UsageRecorder {

    @Override
    public void record(RunResult result) {
        // no-op
    }
}

package com.scutmmq.ai.capability;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 一次 Run 结束时的汇总结果。{@link #terminal} 是幂等键:
 * 一条 Run 只允许发布一次 RunCompletedEvent(RunResult.terminal=true 后不再 publish)。
 *
 * usage 是用量统计:promptTokens / completionTokens / reasoningTokens(DeepSeek 思维链)。
 * 由 AiChatClient 抽取后塞进来。
 */
@Data
@Builder
@NoArgsConstructor(force = true)
@AllArgsConstructor
public class RunResult {

    private final RunContext context;

    private final String replyPreview;
    private final boolean hasDraft;
    private final int toolExecutionCount;
    private final long totalMs;
    private final long ttftMs;

    private Integer promptTokens;
    private Integer completionTokens;
    private Integer reasoningTokens;

    private boolean terminal;

    public int totalTokens() {
        int pt = promptTokens == null ? 0 : promptTokens;
        int ct = completionTokens == null ? 0 : completionTokens;
        return pt + ct;
    }

    public static RunResult start(RunContext ctx) {
        return RunResult.builder()
                .context(ctx)
                .terminal(false)
                .build();
    }
}

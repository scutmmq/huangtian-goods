package com.scutmmq.ai.capability;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * 单次工具调用结束时的上下文。可观测 capabilities 用它计算每工具耗时、写入 ai_run_usage。
 */
@Data
@Builder
@RequiredArgsConstructor
public class ToolContext {
    private final String runId;
    private final String sessionId;
    private final Long userId;
    private final String toolName;
    private final String toolCallId;
    private final JsonNode arguments;
    private final String resultPreview;
    private final boolean hasDraft;
    private final boolean success;
    private final String errorMessage;
    private final long startedAtMillis;
    private final long endedAtMillis;

    public long elapsedMs() {
        return Math.max(0, endedAtMillis - startedAtMillis);
    }

    public static ToolContext fromRun(RunContext run, String toolName, String toolCallId,
                                       JsonNode arguments, String resultPreview,
                                       boolean hasDraft, boolean success, String errorMessage,
                                       long startedAtMillis, long endedAtMillis) {
        return ToolContext.builder()
                .runId(run.getRunId())
                .sessionId(run.getSessionId())
                .userId(run.getUserId())
                .toolName(toolName)
                .toolCallId(toolCallId)
                .arguments(arguments)
                .resultPreview(resultPreview)
                .hasDraft(hasDraft)
                .success(success)
                .errorMessage(errorMessage)
                .startedAtMillis(startedAtMillis)
                .endedAtMillis(endedAtMillis)
                .build();
    }
}

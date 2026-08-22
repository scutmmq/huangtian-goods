package com.scutmmq.ai.capability;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * AI Run 开始时的上下文。所有字段都是只读快照,接收方不要修改。
 *
 * B2 当前只填基础字段;Stage 4(规划)阶段会扩展 plan/step 关联。
 */
@Data
@Builder
@RequiredArgsConstructor
public class RunContext {
    private final String runId;
    private final String sessionId;
    private final Long userId;
    private final String userRole;
    private final long startedAtMillis;

    public static RunContext of(String runId, String sessionId, Long userId, String userRole) {
        return RunContext.builder()
                .runId(runId)
                .sessionId(sessionId)
                .userId(userId)
                .userRole(userRole)
                .startedAtMillis(System.currentTimeMillis())
                .build();
    }
}

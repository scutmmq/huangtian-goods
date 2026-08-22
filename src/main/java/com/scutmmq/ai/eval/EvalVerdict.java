package com.scutmmq.ai.eval;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 单条用例的评估结果。
 * passed=true 表示所有断言通过;
 * 失败原因(reason)+ 检查项明细(checks)便于 log / Dashboard 排查。
 */
@Data
@Builder
@RequiredArgsConstructor
public class EvalVerdict {

    private final String caseName;
    private final boolean passed;
    private final String reason;
    private final List<String> toolsCalled;
    private final String replyPreview;
    private final long elapsedMs;
    private final List<CheckResult> checks;

    @Data
    @Builder
    @RequiredArgsConstructor
    public static class CheckResult {
        private final String name;
        private final boolean passed;
        private final String detail;
    }
}

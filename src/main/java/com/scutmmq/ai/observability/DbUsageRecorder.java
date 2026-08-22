package com.scutmmq.ai.observability;

import com.scutmmq.ai.capability.RunResult;
import com.scutmmq.ai.entity.AiRunUsage;
import com.scutmmq.ai.mapper.AiRunUsageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 把 RunResult 落库到 ai_run_usage 表。
 * 仅在 ai.capability.observability.enabled=true 时创建(@ConditionalOnProperty);
 * 加 @Primary 是为了在 observability 开启时强制选择 DB 实现,而非 NoopUsageRecorder。
 */
@Slf4j
@Component
@Primary
@ConditionalOnProperty(name = "ai.capability.observability.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DbUsageRecorder implements UsageRecorder {

    private final AiRunUsageMapper aiRunUsageMapper;

    /**
     * 每 1k prompt tokens 折算的成本(分)。
     * 注入自 ai.observability.cost-per-1k-prompt-tokens,
     * 未配置时按 0 算(纯 token 数,不计费)。
     */
    @Value("${ai.observability.cost-per-1k-prompt-tokens:0}")
    private int costPer1kPromptCents = 0;

    @Value("${ai.observability.cost-per-1k-completion-tokens:0}")
    private int costPer1kCompletionCents = 0;

    @Override
    public void record(RunResult result) {
        if (result == null) return;
        try {
            AiRunUsage row = new AiRunUsage();
            if (result.getContext() != null) {
                row.setRunId(result.getContext().getRunId());
                row.setSessionId(result.getContext().getSessionId());
                row.setUserId(result.getContext().getUserId());
                row.setUserRole(result.getContext().getUserRole());
            }
            row.setToolCount(result.getToolExecutionCount());
            row.setHasDraft(result.isHasDraft() ? 1 : 0);
            row.setTotalMs(result.getTotalMs());
            row.setTtftMs(result.getTtftMs());
            row.setPromptTokens(result.getPromptTokens());
            row.setCompletionTokens(result.getCompletionTokens());
            row.setReasoningTokens(result.getReasoningTokens());
            row.setTotalTokens(result.totalTokens());
            row.setPromptCostCents(estimateCost(result.getPromptTokens(), costPer1kPromptCents));
            row.setCompletionCostCents(estimateCost(result.getCompletionTokens(), costPer1kCompletionCents));
            row.setCreatedAt(LocalDateTime.now());
            aiRunUsageMapper.insert(row);
        } catch (Exception e) {
            // 落库失败不要让 Agent 整体失败,记录一行告警即可
            log.warn("[AI][USAGE] failed to persist ai_run_usage: {}", e.getMessage(), e);
        }
    }

    private static Integer estimateCost(Integer tokens, int perThousand) {
        if (tokens == null || perThousand <= 0) return 0;
        // tokens / 1000 * perThousand; 加 (perThousand/2) 做整数四舍五入
        long prod = (long) tokens * perThousand;
        return (int) ((prod + 500) / 1000);
    }
}

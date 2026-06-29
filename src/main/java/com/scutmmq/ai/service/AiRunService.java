package com.scutmmq.ai.service;

import com.scutmmq.ai.entity.AiRun;
import com.scutmmq.ai.mapper.AiRunMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * AI Run 生命周期服务。
 *
 * 一个 Run 表示一次完整的助手响应生成（用户发送一条消息 → 助手流式回复）。
 * - 同步模式下其实只有一个 run；
 * - 异步流式模式下，run 由控制器立刻写入（QUEUED），由 worker 流转到 RUNNING → COMPLETED/FAILED/CANCELLED。
 *
 * 状态机：
 *   QUEUED -> RUNNING -> COMPLETED
 *                      -> FAILED
 *                      -> CANCELLED
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiRunService {

    public static final String STATUS_QUEUED = "QUEUED";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private final AiRunMapper aiRunMapper;

    /**
     * 创建一个处于 QUEUED 状态的 Run。返回插入后的实体（带 id / 时间戳）。
     */
    public AiRun submit(Long userId, String sessionId, Long userMessageId, Long assistantMessageId) {
        AiRun run = new AiRun();
        run.setId(UUID.randomUUID().toString());
        run.setUserId(userId);
        run.setSessionId(sessionId);
        run.setUserMessageId(userMessageId);
        run.setAssistantMessageId(assistantMessageId);
        run.setStatus(STATUS_QUEUED);
        LocalDateTime now = LocalDateTime.now();
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        aiRunMapper.insert(run);
        log.info("[AI][RUN] submitted runId={} sessionId={} userId={} userMsgId={} assistantMsgId={}",
                run.getId(), sessionId, userId, userMessageId, assistantMessageId);
        return run;
    }

    /**
     * 把 Run 标记为 RUNNING。
     * 只有处于 QUEUED 状态才能流转到 RUNNING；
     * 其它状态（RUNNING / COMPLETED / FAILED / CANCELLED）一律不动，避免把终态倒退回 RUNNING 产生不可能的状态。
     */
    public void start(String runId) {
        AiRun run = aiRunMapper.selectById(runId);
        if (run == null) {
            log.warn("[AI][RUN] start: run not found runId={}", runId);
            return;
        }
        String current = run.getStatus();
        if (!STATUS_QUEUED.equals(current)) {
            log.warn("[AI][RUN] start: ignored, currentStatus={} runId={} (only QUEUED can transition to RUNNING)",
                    current, runId);
            return;
        }
        run.setStatus(STATUS_RUNNING);
        run.setUpdatedAt(LocalDateTime.now());
        aiRunMapper.updateById(run);
        log.info("[AI][RUN] started runId={}", runId);
    }

    public void complete(String runId) {
        AiRun run = aiRunMapper.selectById(runId);
        if (run == null) {
            log.warn("[AI][RUN] complete: run not found runId={}", runId);
            return;
        }
        run.setStatus(STATUS_COMPLETED);
        run.setUpdatedAt(LocalDateTime.now());
        aiRunMapper.updateById(run);
        log.info("[AI][RUN] completed runId={}", runId);
    }

    public void fail(String runId, String errorMessage) {
        AiRun run = aiRunMapper.selectById(runId);
        if (run == null) {
            log.warn("[AI][RUN] fail: run not found runId={}", runId);
            return;
        }
        run.setStatus(STATUS_FAILED);
        run.setErrorMessage(errorMessage);
        run.setUpdatedAt(LocalDateTime.now());
        aiRunMapper.updateById(run);
        log.info("[AI][RUN] failed runId={} error={}", runId, errorMessage);
    }

    public void cancel(String runId) {
        AiRun run = aiRunMapper.selectById(runId);
        if (run == null) {
            log.warn("[AI][RUN] cancel: run not found runId={}", runId);
            return;
        }
        run.setStatus(STATUS_CANCELLED);
        run.setUpdatedAt(LocalDateTime.now());
        aiRunMapper.updateById(run);
        log.info("[AI][RUN] cancelled runId={}", runId);
    }

    public AiRun findById(String runId) {
        if (runId == null) {
            return null;
        }
        return aiRunMapper.selectById(runId);
    }
}
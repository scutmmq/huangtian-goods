package com.scutmmq.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 会话级任务调度器：在 Task 3 的 aiTaskExecutor 之上加一层"同会话排队 / 跨会话并发"封装。
 *
 * <ul>
 *   <li>不同 session 的任务互不阻塞，可并行执行（受底层 executor 容量限制）</li>
 *   <li>同一 session 的任务严格按 FIFO 顺序串行执行</li>
 *   <li>如果同一 session 有多个待执行消息，按提交顺序依次消费</li>
 * </ul>
 *
 * 实现要点（"drain loop + flag" 模式）：
 * <ol>
 *   <li>submitForSession 先把 task 塞进该 session 的 FIFO 队列</li>
 *   <li>用 compareAndSet(false, true) 竞争"runner 标志位"，输的会留在队列里等</li>
 *   <li>赢得标志位的线程进入 drain loop，一直 poll 直到队列空</li>
 *   <li>finally 块里再次检查队列（防止最后一个 poll 和清标志位之间有 race），
 *       如果有任务则先 set false 再 CAS 抢标志位，抢到则继续跑</li>
 * </ol>
 *
 * 底层 executor 的 CallerRunsPolicy 仍然有效：底层队列打满时回退到调用线程，
 * 这是 Task 3 已有的反压行为，本层不修改。
 */
@Slf4j
@Component
public class AiSessionTaskScheduler {

    private final ThreadPoolTaskExecutor aiTaskExecutor;

    /** 每个 session 一个 FIFO 队列，存放待执行的 runnable。 */
    private final ConcurrentMap<String, ConcurrentLinkedDeque<Runnable>> sessionQueues = new ConcurrentHashMap<>();

    /** 每个 session 的"当前是否有 runner 在跑"标志。 */
    private final ConcurrentMap<String, AtomicBoolean> sessionActive = new ConcurrentHashMap<>();

    public AiSessionTaskScheduler(@Qualifier("aiTaskExecutor") ThreadPoolTaskExecutor aiTaskExecutor) {
        this.aiTaskExecutor = aiTaskExecutor;
    }

    /**
     * 提交一个任务到指定 session 的队列。
     * - 如果该 session 当前没有正在执行的任务，立即提交到底层 executor
     * - 否则加入该 session 的 FIFO 队列尾部，等当前任务完成后依次执行
     * - 不同 session 的任务互不影响，可并行执行（受底层 executor 容量限制）
     */
    public void submitForSession(String sessionId, Runnable task) {
        // 1. Get or create the session's queue, add the task
        Queue<Runnable> queue = sessionQueues.computeIfAbsent(sessionId, k -> new ConcurrentLinkedDeque<>());
        queue.add(task);

        // 2. Try to start a runner for this session
        AtomicBoolean active = sessionActive.computeIfAbsent(sessionId, k -> new AtomicBoolean(false));
        if (active.compareAndSet(false, true)) {
            // We won the race — start the runner
            aiTaskExecutor.execute(() -> runSessionQueue(sessionId, active));
        }
    }

    private void runSessionQueue(String sessionId, AtomicBoolean active) {
        try {
            Queue<Runnable> queue = sessionQueues.get(sessionId);
            if (queue == null) {
                return;
            }
            while (true) {
                Runnable next = queue.poll();
                if (next == null) {
                    break;
                }
                try {
                    next.run();
                } catch (Exception e) {
                    log.error("[AI][SCHED] task failed in session {}: {}", sessionId, e.getMessage(), e);
                }
            }
        } finally {
            // Re-check race: another submitForSession may have added a task between our last poll and clearing the flag.
            // Two-step CAS: release then immediately re-acquire. If re-acquire fails, another submitter took the flag
            // and will run the queued tasks — we must not dispatch a second runner or serialized-per-session is violated.
            Queue<Runnable> queue = sessionQueues.get(sessionId);
            if (queue != null && !queue.isEmpty()) {
                if (active.compareAndSet(true, false)) {
                    if (active.compareAndSet(false, true)) {
                        aiTaskExecutor.execute(() -> runSessionQueue(sessionId, active));
                        return;
                    }
                    // Lost the race: another submitter grabbed the flag and will run the queued tasks.
                    return;
                }
            } else {
                active.set(false);
            }
        }
    }
}

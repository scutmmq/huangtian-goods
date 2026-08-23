package com.scutmmq.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * AI Run 后台执行的线程池。
 *
 * 每个 Run 在提交后立即落库为 QUEUED，然后扔进这个 executor 由 worker 线程流转。
 * Task 4 会基于这个 executor 再做"同会话排队 / 跨会话并发"封装。
 *
 * 拒绝策略用 CallerRunsPolicy：队列打满时回退到调用线程执行，
 * 等于把响应变慢但不会丢任务 —— 一种轻量的反压。
 *
 * <p>同时声明 {@code memoryAsyncExecutor}:B3 长期记忆清理任务专用小池,
 * 与 aiTaskExecutor 解耦,防止审计清理抢 Run 主线程的 CPU。
 *
 * <p>{@link EnableAsync} 启用 @Async 代理。@Async 方法被外部 bean 调用时
 * Spring 会通过代理分发到对应 executor;若 {@code this.} 自调则无效。
 */
@Configuration
@EnableAsync
public class AiTaskExecutorConfig {

    @Bean(name = "aiTaskExecutor")
    public ThreadPoolTaskExecutor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ai-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * B3 审计清理异步池:core=1,max=2(清理是 IO 密集,2 个 worker 足够),
     * queue=50(短期堆积无影响,长堆积说明 DB 慢应该告警)。
     */
    @Bean(name = "memoryAsyncExecutor")
    public ThreadPoolTaskExecutor memoryAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("ai-memory-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
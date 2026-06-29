package com.scutmmq.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * AI Run 后台执行的线程池。
 *
 * 每个 Run 在提交后立即落库为 QUEUED，然后扔进这个 executor 由 worker 线程流转。
 * Task 4 会基于这个 executor 再做“同会话排队 / 跨会话并发”封装。
 *
 * 拒绝策略用 CallerRunsPolicy：队列打满时回退到调用线程执行，
 * 等于把响应变慢但不会丢任务 —— 一种轻量的反压。
 */
@Configuration
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
}
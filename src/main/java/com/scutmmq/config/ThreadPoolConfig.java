package com.scutmmq.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 全局通用异步任务线程池配置。
 * 提供基于系统并发特征配置的 ThreadPoolTaskExecutor，支持优雅停机与调用方反压。
 */
@Slf4j
@Configuration
@EnableAsync
public class ThreadPoolConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        log.info("初始化全局异步任务线程池，检测到 CPU 核心数: {}", cpuCores);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(2, cpuCores));
        executor.setMaxPoolSize(Math.max(4, cpuCores * 2));
        executor.setQueueCapacity(500);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("mall-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}

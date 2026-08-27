package com.scutmmq.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 全局通用异步线程池配置。
 * 遵循阿里巴巴 Java 开发手册规范：
 * 1. 明确使用 ThreadPoolTaskExecutor / ThreadPoolExecutor 构造；
 * 2. 显式指定核心线程数、最大线程数、有界队列容量；
 * 3. 规范设置线程名称前缀，便于排查与监控；
 * 4. 设置合理的拒绝策略（CallerRunsPolicy）与优雅停机。
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
        // 核心线程数：IO密集型建议 2 * CPU, 混合型建议 2 * CPU 或压测确定
        executor.setCorePoolSize(Math.max(2, cpuCores));
        // 最大线程数
        executor.setMaxPoolSize(Math.max(4, cpuCores * 2));
        // 有界阻塞队列容量，严禁使用无界队列
        executor.setQueueCapacity(500);
        // 空闲线程存活时间（秒）
        executor.setKeepAliveSeconds(60);
        // 线程名称前缀
        executor.setThreadNamePrefix("mall-async-");
        // 拒绝策略：由调用者所在线程执行，提供自然反压，不丢弃任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 优雅停机配置：应用关闭时等待未完成的任务结束
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}

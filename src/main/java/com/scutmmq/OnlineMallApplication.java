package com.scutmmq;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot 入口。MapperScan 覆盖 com.scutmmq.mapper 主 mapper 和 com.scutmmq.ai.mapper AI 子模块;
 * ConfigurationPropertiesScan 让 ai.* 配置类(@ConfigurationProperties)被自动注册。
 *
 * <p>{@link EnableScheduling} 启用 {@code @Scheduled} 注解处理 — B3 step7 MemoryCronScheduler
 * 的 recomputeStaleBatch / dropOldAuditPartitions / cronWatchdog 都依赖此注解。
 */
@SpringBootApplication
@MapperScan({"com.scutmmq.mapper", "com.scutmmq.ai.mapper"})
@ConfigurationPropertiesScan({"com.scutmmq", "com.scutmmq.ai"})
@EnableScheduling
public class OnlineMallApplication {

    public static void main(String[] args) {
        SpringApplication.run(OnlineMallApplication.class, args);
    }

}

package com.scutmmq.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class RedissonConfig {
    @Bean
    public RedissonClient redissonClient(@Value("${spring.data.redis.host}") String host,
                                         @Value("${spring.data.redis.port}") int port,
                                         @Value("${spring.data.redis.password:}") String password,
                                         @Value("${spring.data.redis.database:0}") int database) {
        // 配置
        Config config = new Config();
        SingleServerConfig singleServerConfig = config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setDatabase(database);
        if (StringUtils.hasText(password)) {
            singleServerConfig.setPassword(password);
        }
        //创建RedissonClient对象
        return Redisson.create(config);
    }
}

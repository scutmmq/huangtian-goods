package com.scutmmq.utils;

import lombok.Data;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;


@Component
@Data
public class RedisIdWorker {

    private static  final  long BEGIN_TIMESTAMP =1640995200;
    private static  final  int COUNT_BITS =32; //序列号位数

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 生成订单id
     * @param keyPrefix 业务前缀
     * @return 时间戳+序列号
     */
    public long nextId(String keyPrefix){

        // 1.生成时间戳
        long nowSecond = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond-BEGIN_TIMESTAMP;

        // 2.生成序列号
        // 2.1 获取当前日期，精确到天
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 2.2 自增长
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        // 3.拼接返回
        return  timestamp<<32|count;
    }
}
package com.scutmmq.service.Impl;

import cn.hutool.json.JSONUtil;
import com.scutmmq.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

import static com.scutmmq.utils.RedisConstants.NOTIFY_MERCHANT_STREAM_PREFIX;
import static com.scutmmq.utils.RedisConstants.NOTIFY_USER_STREAM_PREFIX;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final StringRedisTemplate redisTemplate;
    private final com.scutmmq.config.NotifyWebSocketHandler webSocketHandler;

    public void sendToMerchant(Long merchantId, String content) {
        Map<String, String> payload = new HashMap<>();
        payload.put("type", "system");
        payload.put("content", content);
        payload.put("ts", String.valueOf(System.currentTimeMillis()));

        String json = JSONUtil.toJsonStr(payload);
        boolean online = webSocketHandler.pushToMerchant(merchantId, json);
        if (!online) {
            // 商家不在线，写入 Redis Stream，等待其上线时拉取
            redisTemplate.opsForStream().add(NOTIFY_MERCHANT_STREAM_PREFIX + merchantId, payload);
            log.info("商家{}不在线，消息已存入Redis Stream", merchantId);
        } else {
            log.info("已向在线商家{}推送消息", merchantId);
        }
    }

    public void sendToUser(Long userId, String content) {
        Map<String, String> payload = new HashMap<>();
        payload.put("type", "system");
        payload.put("content", content);
        payload.put("ts", String.valueOf(System.currentTimeMillis()));

        String json = JSONUtil.toJsonStr(payload);
        boolean online = webSocketHandler.pushToUser(userId, json);
        if (!online) {
            // 用户不在线，写入 Redis Stream，等待其上线时拉取
            redisTemplate.opsForStream().add(NOTIFY_USER_STREAM_PREFIX + userId, payload);
            log.info("用户{}不在线，消息已存入Redis Stream", userId);
        } else {
            log.info("已向在线用户{}推送消息", userId);
        }
    }
}

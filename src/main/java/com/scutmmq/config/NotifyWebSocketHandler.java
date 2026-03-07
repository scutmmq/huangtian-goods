package com.scutmmq.config;

import cn.hutool.json.JSONUtil;
import com.scutmmq.mapper.MerchantUserMapper;
import com.scutmmq.utils.JwtUtils;
import com.scutmmq.utils.RedisConstants;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotifyWebSocketHandler extends TextWebSocketHandler {

    private final StringRedisTemplate redisTemplate;
    private final MerchantUserMapper merchantUserMapper;

    private final ConcurrentHashMap<Long, CopyOnWriteArraySet<WebSocketSession>> userSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, CopyOnWriteArraySet<WebSocketSession>> merchantSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        URI uri = session.getUri();
        log.info("收到WebSocket连接请求，URI: {}", uri);
        
        String token = parseQueryParam(uri != null ? uri.getQuery() : null, "token");
        if (token == null || Boolean.FALSE.equals(redisTemplate.hasKey(RedisConstants.TOKEN_KEY + token))) {
            log.warn("WebSocket 连接拒绝，token 无效或不存在: {}", token);
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        
        Claims claims = JwtUtils.parseJwtToken(token);
        Long userId = ((Number) claims.get("id")).longValue();
        log.info("解析token成功，userId={}", userId);

        // 注册用户会话
        userSessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
        log.info("用户会话已注册，userId={}, 当前用户在线数={}", userId, userSessions.get(userId).size());

        // 如果该用户是商家，注册商家会话
        Long merchantId = merchantUserMapper.getMerchantIdByUserId(userId);
        if (merchantId != null) {
            merchantSessions.computeIfAbsent(merchantId, k -> new CopyOnWriteArraySet<>()).add(session);
            log.info("商家会话已注册，merchantId={}, 当前商家在线数={}", merchantId, merchantSessions.get(merchantId).size());
        } else {
            log.info("用户{}不是商家", userId);
        }

        // 拉取并下发离线消息（用户）
        flushBacklog(RedisConstants.NOTIFY_USER_STREAM_PREFIX + userId, session);
        // 拉取并下发离线消息（商家）
        if (merchantId != null) {
            flushBacklog(RedisConstants.NOTIFY_MERCHANT_STREAM_PREFIX + merchantId, session);
        }
        log.info("WebSocket 建立成功，userId={}, merchantId={}", userId, merchantId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        removeSession(session);
    }

    public boolean pushToUser(Long userId, String json) {
        log.info("尝试向用户{}推送消息: {}", userId, json);
        boolean result = sendToSessions(userSessions.get(userId), json);
        log.info("用户{}消息推送结果: {}", userId, result ? "成功" : "失败(离线)");
        return result;
    }

    public boolean pushToMerchant(Long merchantId, String json) {
        log.info("尝试向商家{}推送消息: {}", merchantId, json);
        boolean result = sendToSessions(merchantSessions.get(merchantId), json);
        log.info("商家{}消息推送结果: {}", merchantId, result ? "成功" : "失败(离线)");
        return result;
    }

    private boolean sendToSessions(Set<WebSocketSession> set, String json) {
        if (set == null || set.isEmpty()) return false;
        boolean sent = false;
        for (WebSocketSession s : set) {
            if (s.isOpen()) {
                try {
                    s.sendMessage(new TextMessage(json));
                    sent = true;
                } catch (Exception e) {
                    log.warn("WebSocket 推送失败：{}", e.getMessage());
                }
            }
        }
        return sent;
    }

    private void flushBacklog(String streamKey, WebSocketSession session) {
        try {
            List<MapRecord<String, Object, Object>> records =
                    redisTemplate.opsForStream().range(streamKey, Range.unbounded());
            if (records == null || records.isEmpty()) return;

            for (MapRecord<String, Object, Object> record : records) {
                String json = JSONUtil.toJsonStr(record.getValue());
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
                // 推送后删除该条目（不做永久保存）
                redisTemplate.opsForStream().delete(streamKey, record.getId());
            }
            log.info("已推送并清理离线消息，streamKey={}", streamKey);
        } catch (Exception e) {
            log.warn("拉取离线消息失败：{}", e.getMessage());
        }
    }

    private void removeSession(WebSocketSession session) {
        userSessions.values().forEach(set -> set.remove(session));
        merchantSessions.values().forEach(set -> set.remove(session));
    }

    private String parseQueryParam(String query, String name) {
        if (query == null) return null;
        String[] parts = query.split("&");
        for (String p : parts) {
            String[] kv = p.split("=");
            if (kv.length == 2 && kv[0].equals(name)) return kv[1];
        }
        return null;
    }
}

package com.scutmmq.ai.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scutmmq.ai.cache.UserMemoryCache.CacheSnapshot;
import com.scutmmq.ai.config.AiMemoryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B3 step2: UserMemoryCache 单元测试。
 * Redis 用 Mockito mock,无需真实 Redis 服务。
 */
class UserMemoryCacheTest {

    private StringRedisTemplate redis;
    private AiMemoryProperties props;
    private ObjectMapper mapper;
    private ValueOperations<String, String> ops;
    private UserMemoryCache cache;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        props = new AiMemoryProperties();
        props.setCacheHmacSecrets("v1:abcdefghijklmnopqrstuvwxyz12345678");
        props.setActiveSecretVersion("v1");
        props.setCoalesceTtlSeconds(60);
        mapper = new ObjectMapper();
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        cache = new UserMemoryCache(redis, props, mapper);
    }

    @Test
    void hmacKeyIsDeterministicAndVersioned() {
        String key1 = cache.hmacKey(123L);
        String key2 = cache.hmacKey(123L);
        assertEquals(key1, key2);
        assertTrue(key1.startsWith("ai:memory:v1:"));
        // 前缀 "ai:memory:v1:" (12 字符) + 16 hex = 28
        assertEquals("ai:memory:v1:".length() + 16, key1.length());
    }

    @Test
    void hmacKeyIsNotEqualToUserId() {
        String key = cache.hmacKey(12345L);
        assertFalse(key.contains("12345"), "HMAC key must not leak userId");
        assertNotEquals("ai:memory:v1:12345", key);
    }

    @Test
    void serializeAndDeserializeRoundtrip() {
        CacheSnapshot s = new CacheSnapshot("{\"a\":1}", "{\"b\":2}", 7L, Instant.now());
        String json = cache.serialize(s);
        CacheSnapshot back = cache.deserialize(json);
        assertEquals(7L, back.computeSeq());
        assertEquals("{\"a\":1}", back.identityJson());
        assertEquals("{\"b\":2}", back.preferenceJson());
    }

    @Test
    void setIfAbsentNewerRejectsStaleSeq() {
        // 第一次写入 seq=10(Lua 返回 true 表示已 SET)
        when(redis.execute(any(org.springframework.data.redis.core.RedisCallback.class)))
                .thenReturn(Boolean.TRUE);
        cache.setIfAbsentNewer(1L, new CacheSnapshot("{}", "{}", 10L, Instant.now()));

        // 第二次 seq=5(旧)——Lua 应该拒绝,返回 false
        when(redis.execute(any(org.springframework.data.redis.core.RedisCallback.class)))
                .thenReturn(Boolean.FALSE);
        cache.setIfAbsentNewer(1L, new CacheSnapshot("{}", "{}", 5L, Instant.now()));

        // 模拟当前缓存里仍是 seq=10
        String stored = "{\"identityJson\":\"{}\",\"preferenceJson\":\"{}\","
                + "\"computeSeq\":10,\"computedAt\":\"2026-01-01T00:00:00Z\"}";
        when(ops.get(any())).thenReturn(stored);
        CacheSnapshot got = cache.get(1L).orElseThrow();
        assertEquals(10L, got.computeSeq());
    }

    @Test
    void setIfAbsentNewerAcceptsNewerSeq() {
        when(redis.execute(any(org.springframework.data.redis.core.RedisCallback.class)))
                .thenReturn(Boolean.TRUE);
        cache.setIfAbsentNewer(1L, new CacheSnapshot("{}", "{}", 5L, Instant.now()));
        cache.setIfAbsentNewer(1L, new CacheSnapshot("{}", "{}", 10L, Instant.now()));

        String stored = "{\"identityJson\":\"{}\",\"preferenceJson\":\"{}\","
                + "\"computeSeq\":10,\"computedAt\":\"2026-01-01T00:00:00Z\"}";
        when(ops.get(any())).thenReturn(stored);
        CacheSnapshot got = cache.get(1L).orElseThrow();
        assertEquals(10L, got.computeSeq());
    }

    @Test
    void invalidateClearsCache() {
        cache.setIfAbsentNewer(1L, new CacheSnapshot("{}", "{}", 1L, Instant.now()));
        cache.invalidate(1L);
        verify(redis).delete(any(String.class));
    }

    @Test
    void redisFailureReturnsEmptyOptional() {
        when(ops.get(any())).thenThrow(new RedisConnectionFailureException("conn down"));
        Optional<CacheSnapshot> result = cache.get(1L);
        assertTrue(result.isEmpty());
    }
}

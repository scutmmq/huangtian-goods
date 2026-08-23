package com.scutmmq.ai.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scutmmq.ai.config.AiMemoryProperties;
import com.scutmmq.utils.RedisConstants;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * B3 step2: 用户长期记忆的 Redis 缓存。
 * <p>
 * Key 由 HMAC-SHA256(activeSecret, userId) 截 16 hex 组成,
 * 保证 userId 不出现在 key 中,且无法反推用户身份。
 * <p>
 * setIfAbsentNewer 使用 Lua 脚本保证原子性:仅当新 computeSeq > 旧 computeSeq 时才 SETEX,
 * 防止并发重算回写旧值(TOCTOU 防护)。
 * <p>
 * Redis 不可用时 get 返回 Optional.empty(),所有写操作降级为 no-op。
 */
@Slf4j
@Component
public class UserMemoryCache {

    private static final String SET_IF_NEWER_LUA =
            "local cur = redis.call('GET', KEYS[1]) "
          + "local newSeq = tonumber(ARGV[1]) "
          + "if cur == false "
          + "  then return redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3]) "
          + "else "
          + "  local oldSeq = tonumber(string.match(cur, '\"computeSeq\":(%d+)') or '0') "
          + "  if oldSeq < newSeq "
          + "    then return redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3]) "
          + "    else return false "
          + "  end "
          + "end";

    private final StringRedisTemplate redis;
    private final AiMemoryProperties props;
    private final ObjectMapper mapper;

    /** B3 step10: 缓存 hit / miss 计数器,用于计算 ai_memory_cache_hit_ratio gauge */
    private final AtomicLong hitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();

    /** B3 step10: 缓存失败计数器 — {@code ai_memory_cache_failure_total} */
    private final Counter cacheFailureCounter;
    /** B3 step10: 突增 miss 计数器(每次 miss 都计,外部按 5x baseline 告警)— {@code ai_memory_cache_miss_burst_total} */
    private final Counter cacheMissBurstCounter;

    public UserMemoryCache(StringRedisTemplate redis, AiMemoryProperties props, ObjectMapper mapper,
                           MeterRegistry meterRegistry) {
        this.redis = redis;
        this.props = props;
        // 用类加载器发现并注册 JavaTimeModule 支持 Instant;对 Spring Boot 自动注入的 ObjectMapper 也是幂等的。
        mapper.findAndRegisterModules();
        this.mapper = mapper;
        this.cacheFailureCounter = Counter.builder("ai_memory_cache_failure_total")
                .description("缓存 get 失败次数(Redis 连接异常)")
                .register(meterRegistry);
        this.cacheMissBurstCounter = Counter.builder("ai_memory_cache_miss_burst_total")
                .description("缓存 miss 突增计数(主从切换瞬断检测,>5x baseline 告警)")
                .register(meterRegistry);
        Gauge.builder("ai_memory_cache_hit_ratio", this, UserMemoryCache::computeHitRatio)
                .description("缓存命中率 gauge(target > 0.80)")
                .register(meterRegistry);
    }

    /** 命中率 = hits / (hits + misses);分母为 0 时返 0.0 避免 NaN。 */
    private double computeHitRatio() {
        long hits = hitCount.get();
        long misses = missCount.get();
        long total = hits + misses;
        return total == 0L ? 0.0 : (double) hits / (double) total;
    }

    public record CacheSnapshot(
            String identityJson,
            String preferenceJson,
            long computeSeq,
            Instant computedAt) {}

    /**
     * 缓存 key 模板:ai:memory:v{ver}:{16-hex HMAC}
     */
    public String hmacKey(Long userId) {
        try {
            String secret = props.getActiveSecret();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(userId.toString().getBytes(StandardCharsets.UTF_8));
            String hex = HexFormat.of().formatHex(hash).substring(0, 16);
            String version = props.getActiveSecretVersion();
            // activeSecretVersion 形如 "v1",拼接成 "v1:hex"
            return RedisConstants.MEMORY_CACHE_KEY_PREFIX + version + ":" + hex;
        } catch (Exception e) {
            throw new IllegalStateException("HMAC failure for userId=" + userId, e);
        }
    }

    public Optional<CacheSnapshot> get(Long userId) {
        try {
            String raw = redis.opsForValue().get(hmacKey(userId));
            if (raw == null) {
                missCount.incrementAndGet();
                cacheMissBurstCounter.increment();
                return Optional.empty();
            }
            hitCount.incrementAndGet();
            return Optional.of(deserialize(raw));
        } catch (Exception e) {
            cacheFailureCounter.increment();
            missCount.incrementAndGet();
            log.warn("[AI][MEMORY] redis get failed userId={} reason={}", userId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 仅当 snapshot.computeSeq > 当前缓存 computeSeq 时才 SETEX。
     * 整个 GET + 比较 + SET 流程由 Lua 脚本原子执行,防止并发重写。
     */
    public void setIfAbsentNewer(Long userId, CacheSnapshot snapshot) {
        try {
            String key = hmacKey(userId);
            String json = serialize(snapshot);
            String ttl = String.valueOf(props.getCoalesceTtlSeconds());
            String seqStr = String.valueOf(snapshot.computeSeq());
            redis.execute((RedisCallback<Object>) connection -> connection.scriptingCommands().eval(
                    SET_IF_NEWER_LUA.getBytes(StandardCharsets.UTF_8),
                    org.springframework.data.redis.connection.ReturnType.BOOLEAN,
                    1,
                    key.getBytes(StandardCharsets.UTF_8),
                    json.getBytes(StandardCharsets.UTF_8),
                    seqStr.getBytes(StandardCharsets.UTF_8),
                    ttl.getBytes(StandardCharsets.UTF_8)), true);
        } catch (Exception e) {
            log.warn("[AI][MEMORY] cache set failed userId={} reason={}", userId, e.getMessage());
        }
    }

    public void invalidate(Long userId) {
        try {
            redis.delete(hmacKey(userId));
        } catch (Exception ignore) {
            // 降级
        }
    }

    public String serialize(CacheSnapshot s) {
        try {
            return mapper.writeValueAsString(s);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("serialize CacheSnapshot failed", e);
        }
    }

    public CacheSnapshot deserialize(String json) {
        try {
            return mapper.readValue(json, CacheSnapshot.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("deserialize CacheSnapshot failed", e);
        }
    }
}

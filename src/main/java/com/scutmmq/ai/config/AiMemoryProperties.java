package com.scutmmq.ai.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * B3 长期记忆系统配置。13 字段,启动期硬校验,避免运行时才发现 secret 缺失。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.memory")
public class AiMemoryProperties {

    /** 多版本 HMAC 密钥,格式 "v1:secret1,v2:secret2",整段 ≥ 32 字符。 */
    private String cacheHmacSecrets = "v1:dev-only-fallback-please-set-via-env-in-production-min-32-chars";

    /** 当前生效的 secret 版本(必须出现在 cacheHmacSecrets 的 key 集合中)。 */
    private String activeSecretVersion = "v1";

    /** 短窗口合并写阈值(秒),同一用户在该窗口内的 memory 写入会被合并。 */
    private int coalesceTtlSeconds = 60;

    /** 周期重算 cron 表达式(默认每天凌晨 3 点)。 */
    private String recomputeCron = "0 0 3 * * ?";

    /** 分区 drop cron 表达式(默认每月 1 号凌晨 2 点)。 */
    private String partitionDropCron = "0 0 2 1 * ?";

    /** 注入到 prompt 的 memory token 数上限(防止 prompt 膨胀)。 */
    private int promptTokenCap = 600;

    /** 单次重算批大小。 */
    private int recomputeBatchSize = 1000;

    /** 单用户连续失败达到该次数后熔断(暂时跳过重算)。 */
    private int recomputeMaxFailCount = 3;

    /** 用户主动 reset 后保留记忆的天数。 */
    private int resetRetentionDays = 180;

    /** 审计分区保留天数。 */
    private int auditPartitionRetentionDays = 90;

    /** 审计清理速率(rows/sec),防止 IO 风暴。 */
    private int auditPurgeRateRowsPerSec = 100;

    /** 本地速率限制:每用户每秒最多 N 次写请求。 */
    private int localRateLimitPerUser = 1;

    /** 本地速率限制突发容量。 */
    private int localRateLimitBurst = 200;

    /**
     * 启动期硬校验:secret 长度 ≥ 32、active 版本必须在 secrets 中。
     * 失败时立即拒绝启动,避免运行期才发现配置错误。
     */
    @PostConstruct
    public void validate() {
        if (cacheHmacSecrets == null || cacheHmacSecrets.length() < 32) {
            throw new IllegalStateException("ai.memory.cache-hmac-secrets must be >= 32 chars");
        }
        if (activeSecretVersion == null || !cacheHmacSecrets.contains(activeSecretVersion + ":")) {
            throw new IllegalStateException("ai.memory.active-secret-version '" + activeSecretVersion
                    + "' not present in cache-hmac-secrets");
        }
    }

    /**
     * 解析当前生效的 secret 值(去掉 v{N}: 前缀)。
     * 仅在 validate() 通过后调用安全;若版本不存在,抛 IllegalStateException。
     */
    public String getActiveSecret() {
        for (String entry : cacheHmacSecrets.split(",")) {
            String[] kv = entry.trim().split(":", 2);
            if (kv.length == 2 && kv[0].trim().equals(activeSecretVersion)) {
                return kv[1].trim();
            }
        }
        throw new IllegalStateException("active secret not found: " + activeSecretVersion);
    }
}
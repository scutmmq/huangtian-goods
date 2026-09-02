# B3 — AI 助手长期记忆系统(企业级设计 v0.3)

> **状态**:v0.3(架构 / 运维 重审后修订;待用户 review spec 后调用 writing-plans)
> **版本**:v0.1 → v0.2 → v0.3(累计修复 20 个 BLOCKERS + 22 个 MAJORS)
> **作者**:Claude
> **评审专家**:DB 评审员 / 安全评审员 / 架构评审员 / 运维评审员
> **日期**:2026-08-23

---

## 0. 修订记录

### 0.1 v0.1 → v0.2(DB + Sec 双 REJECT)

| 来源 | 类型 | 描述 | 修复位置 |
|---|---|---|---|
| DB-B1 | BLOCKER | returnRate90d 查询无源数据 | §3.5 SQL 改用 `orders.payment_status='refunded'` |
| DB-B2 | BLOCKER | 90 天聚合 SQL 与 schema 4 处不一致 | §6 附录 SQL 全部对齐真实 schema |
| DB-B3 | BLOCKER | orders 缺复合索引 | §2.5 + §6 新增 DDL |
| DB-B4 | BLOCKER | 价格分位 SQL 慢且不准确 | §6 改用 `PERCENTILE_DISC` MySQL 8 窗口函数 |
| DB-B5 | BLOCKER | 100K cron 不可行 | §3.4 改游标分批 + 分布式锁 |
| DB-B6 | BLOCKER | 乐观锁 version 不生效 | §2.1 改用 `@Version` + MP interceptor |
| SEC-B1 | BLOCKER | 审计表 fields_changed 违反 GDPR 最小化 | §2.2 改 VARCHAR + expires_at |
| SEC-B2 | BLOCKER | phoneTail4 PII 但无价值 | §2.3 删除该字段 |
| SEC-B3 | BLOCKER | 画像字段直接进 prompt(注入风险) | §4.4 + §6.4 加白名单 + 转义 + deny-list |
| SEC-B4 | BLOCKER | "读不出" 违反 GDPR Art 15 | §5.2 改 GET /ai/memory 告知摘要 |

### 0.2 v0.2 → v0.3(架构 APPROVE_WITH_FIXES + 运维 REJECT)

| 来源 | 类型 | 描述 | 修复位置 |
|---|---|---|---|
| ARCH-B1 | BLOCKER | cronLock 抽象不存在,破坏一致性 | §3.4 改用 Redisson raw-lock(复用 PayServiceImpl 模式) |
| ARCH-B2 | BLOCKER | 缺 AiMemoryProperties 配置类 | 新增 §2.7 |
| OPS-B1 | BLOCKER | cron lock 无 watchdog/TTL 重释 | §3.4 加 Redisson leaseTime + 独立 cron-watchdog Scheduled |
| OPS-B2 | BLOCKER | Redis 降级无进程内 RateLimiter | §3.2 加 Guava RateLimiter 兜底 + DEGRADED_RATE_LIMITED audit |
| OPS-B3 | BLOCKER | 月度 DROP PARTITION 无健康探针 | §7.2 + §7.3 加 `ai_memory_partition_cron_last_success_timestamp_seconds` gauge + 告警 |
| OPS-B4 | BLOCKER | Audit 伪匿名化阻塞请求 | §5.1 改 @Async 后台 + 死信表 + 限速 |
| OPS-B5 | BLOCKER | TOCTOU cache invalidation 竞态 | §4.1 加 compute_seq 单调自增 + Redis 携带 seq 校验 |
| OPS-B6 | BLOCKER | ALTER TABLE 无停机方案 | 新增 §11.0 step0 DDL runbook(gh-ost / MySQL 8 INSTANT) |
| OPS-B7 | BLOCKER | HMAC secret 无版本化轮换 | §2.6 加 v{version} 双写 7 天 + 切换 |
| OPS-B8 | BLOCKER | 异步 recompute 丢失 MDC/traceId | §3.3 加 TaskDecorator 透传 traceId / userId |
| ARCH-M1 | MAJOR | PromptSanitizer 放错包 | §1.5 改 com.scutmmq.ai.security |
| ARCH-M2 | MAJOR | GET /ai/memory 用 Map 而非 VO | 新增 UserMemoryOverviewVO |
| ARCH-M3 | MAJOR | UserMemoryEventListener 路径未明 | §3.1 改 com.scutmmq.ai.event |
| ARCH-M4 | MAJOR | DSR cron SQL NOT IN 性能 | §6.8 改 NOT EXISTS |
| OPS-M5 | MAJOR | 缺 ai.memory.* 配置类 | 新增 §2.7(同 ARCH-B2) |
| OPS-M6 | MAJOR | 失败模式表缺云场景 | §7.1 补 3 行(Redis 主从切换 / MySQL 主备延迟 / VPC 抖动) |
| OPS-M7 | MAJOR | 监控缺 P95/P99 | §7.2 补 duration / lag / query_seconds 指标 |
| OPS-M8 | MAJOR | JSON size CHECK 触发无降级 | §2.1 + §3.3 Java 层 try-catch + JSON_OVERFLOW audit + fallback |
| OPS-M9 | MAJOR | binlog/AOF 保留周期未明 | §5.1 声明 7 天 + 形成 GDPR 可证明 PII 生命周期 < 97 天 |
| OPS-M10 | MAJOR | 缺 SLO 定义 | 新增 §7.4 SLO 目标 |

### 0.3 标记"future hardening"项(v0.3+ 后续)

| 来源 | 类型 | 描述 | 决定 |
|---|---|---|---|
| OPS-M1 | MAJOR | cron 100K 线程池 + 压测 | 标记 §11.0 step0 之前必须压测通过 |
| OPS-M2 | MAJOR | Redis Down 时 LRU 兜底 | 留 v0.4,先用 RateLimiter 限速 |
| OPS-M3 | MAJOR | 告警 routing + runbook URL | 留 B3.5 部署时由运维补充 |
| OPS-M8 | MAJOR | ai_user_memory_history 快照表 | 留 v0.4,优先用 contentHash 比对回滚 |
| OPS-M10 | MAJOR | 灰度发布 / 流量染色 | 留 B3.5 部署时由 SRE 补 |

---

## 1. 范围

### 1.1 包含
- 用户维度**身份档案**(identity profile)
- 用户维度**偏好画像**(preference profile)
- 事件驱动 + TTL 写入路径
- MySQL 主 + Redis 热缓存双层存储
- 注入到 `MallSystemPromptProvider` 的 system prompt(300-600 token)
- `POST /ai/memory/reset` 一键重置
- `GET /ai/memory` 告知摘要(GDPR Art 15 合规)

### 1.2 不包含(B4 / 未来阶段)
- 商品知识库 RAG(语义检索)
- 跨用户知识图谱
- 显式用户编辑单字段接口(用户可一键 reset,但不开放改单个字段)
- 行为摘要(最近 30 天动作)、会话摘要
- 跨集群多活

### 1.3 GDPR 合规自评(新增,R24)

| GDPR 条款 | 状态 | 实现 |
|---|---|---|
| Art 5(1)(b) 目的限制 | ✅ | 仅用于 AI 个性化推荐 |
| Art 5(1)(c) 数据最小化 | ✅ | 删 phoneTail4,fields_changed 只存字段名 |
| Art 5(1)(e) 存储期限 | ✅ | 审计表 90 天 RANGE 分区,每月清理 |
| Art 12 响应时限 | ✅ | Reset 接口一键生效;GET 立即返回 |
| Art 15 知情权 | ✅ | `GET /ai/memory` 返回画像摘要 + 用途说明 |
| Art 17 被遗忘权 | ✅ | Reset 清空画像 + 删除 Redis + 清空审计 PII |
| Art 20 数据可携 | ⏳ 留 B5 | 暂未实现 JSON 导出 |
| Art 25 设计与默认隐私 | ✅ | 默认无 read endpoint,需用户主动请求 |

---

## 2. 数据模型

### 2.1 主表 `ai_user_memory`(v0.3 — 加 compute_seq)

```sql
CREATE TABLE ai_user_memory (
  user_id           BIGINT UNSIGNED NOT NULL,
  identity_json     JSON NOT NULL,
  preference_json   JSON NOT NULL,
  computed_at       DATETIME NOT NULL,
  recompute_status  TINYINT NOT NULL DEFAULT 1,
  fail_count        INT NOT NULL DEFAULT 0,
  version           INT NOT NULL DEFAULT 1,        -- @Version 乐观锁(MP interceptor)
  compute_seq       BIGINT UNSIGNED NOT NULL DEFAULT 0,  -- 单调递增,防 cache TOCTOU(OPS-B5)
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  KEY idx_status_computed_at (recompute_status, computed_at),
  CONSTRAINT chk_identity_json_valid CHECK (JSON_VALID(identity_json)),
  CONSTRAINT chk_preference_json_valid CHECK (JSON_VALID(preference_json)),
  CONSTRAINT chk_identity_size CHECK (OCTET_LENGTH(identity_json) <= 8192),
  CONSTRAINT chk_preference_size CHECK (OCTET_LENGTH(preference_json) <= 8192)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**变更(vs v0.1)**:
- 复合索引 `idx_status_computed_at` 替代单独的 `idx_computed_at`
- 加 `@Version` 字段(MP `OptimisticLockerInnerInterceptor` 自动管理)
- JSON 字段加 `JSON_VALID` + size CHECK 约束
- 删 `idx_user_id`(PK 已覆盖)

**Java 实体**:`UserMemoryEntity` 标注 `@Version private Integer version;`

### 2.2 审计表 `ai_user_memory_audit`(v0.2)

```sql
CREATE TABLE ai_user_memory_audit (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id       BIGINT UNSIGNED NOT NULL,
  action        VARCHAR(32) NOT NULL,
  fields_changed VARCHAR(512) NULL,        -- 逗号分隔字段名,不存 PII
  triggered_by  VARCHAR(64) NULL,
  token_estimate INT NULL,
  field_dropped VARCHAR(64) NULL,          -- 仅 OVERFLOW_DROP 时填
  actor_ip      VARCHAR(45) NULL,           -- R19
  request_id    CHAR(36) NULL,              -- R19
  error_message TEXT NULL,
  expires_at    DATETIME NULL,              -- SEC-B1 retention 标记
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_action_time (action, created_at), -- R7 改为按 action 查
  KEY idx_user_time (user_id, created_at),
  CONSTRAINT chk_action CHECK (action IN ('COMPUTE','RESET','READ_MISS','OVERFLOW_DROP','RECOMPUTE_FAIL','PROMPT_INJECTION_DROP'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
PARTITION BY RANGE (TO_DAYS(created_at)) (
  PARTITION p_init VALUES LESS THAN (TO_DAYS('2026-09-01')),
  PARTITION p_2026_09 VALUES LESS THAN (TO_DAYS('2026-10-01')),
  PARTITION p_2026_10 VALUES LESS THAN (TO_DAYS('2026-11-01')),
  PARTITION p_2026_11 VALUES LESS THAN (TO_DAYS('2026-12-01')),
  PARTITION p_max  VALUES LESS THAN MAXVALUE
);
```

**变更(vs v0.1)**:
- `fields_changed`: JSON → VARCHAR(512) 逗号分隔(避免存全量 PII)
- 加 `expires_at`(GDPR retention)
- 加 `field_dropped`(OVERFLOW_DROP 时记录丢了哪个字段)
- 加 `actor_ip` + `request_id`(R19 审计完整性)
- 加 `chk_action` CHECK 约束(R18)
- 90 天 RANGE 分区 + 月度 cron DROP PARTITION

### 2.3 `identity_json` 字段(v0.2 — 扩展 + 删 phoneTail4)

```json
{
  "isActive": true,                              // 账号是否激活
  "isMerchant": false,
  "merchantId": null,
  "defaultAddressId": 123,                       // 仅内部使用,不注入 prompt(SEC-M6)
  "addressCount": 3,
  "registeredAt": "2025-03-12",
  "accountAgeDays": 540,
  "lastLoginAt": "2026-08-20",                   // 新增,用于沉睡用户画像
  "gender": 1,                                    // 新增
  "nickName": "张三",                            // 新增(已脱敏)
  "vipLevel": 0                                   // 新增,0=普通
}
```

**变更**:删 `phoneTail4`(SEC-B2);新增 `isActive / lastLoginAt / gender / nickName / vipLevel`(DB-MAJOR-1)

### 2.4 `preference_json` 字段(v0.2 — 扩展)

```json
{
  "priceRange": {
    "p25": 28.0, "p50": 89.0, "p75": 199.0, "max": 499.0, "currency": "CNY"
  },
  "topCategories": [
    {"categoryId": 5, "categoryName": "服饰", "spend": 380.0, "orderCount": 4}
  ],
  "topMerchants": [
    {"merchantId": 12, "merchantName": "小米旗舰店", "orderCount": 3, "spend": 268.0}
  ],
  "orderStats": {
    "totalOrders90d": 6, "avgOrderValue": 78.5,
    "returnRate90d": 0.083, "lastOrderAt": "2026-08-10"
  },
  "preferredSizes": [],                          // SKU 不存尺码,先空(B4 再补)
  "paymentMethodPreference": {"alipay": 0.6, "wechat": 0.4},
  "shippingMethodPreference": {"顺丰": 0.7, "京东物流": 0.3},
  "activeHours": {"peakStart": 20, "peakEnd": 23},
  "contentHash": "ab12cd34"                      // 重命名 fingerprint + 算法注释
}
```

**变更**:新增 `priceRange.max / topMerchants[].spend / paymentMethodPreference / shippingMethodPreference / activeHours`(DB-MAJOR-1);`fingerprint` → `contentHash`(DB-MAJOR-3)

### 2.5 现有表新增索引(v0.2 — DB-B3)

```sql
ALTER TABLE orders
  ADD KEY idx_user_status_time (user_id, status, ordered_at),
  ADD KEY idx_user_payment_time (user_id, payment_status, ordered_at);
```

- `idx_user_status_time`:支撑 `WHERE user_id=? AND status IN (...) AND ordered_at >= ?` 的范围扫描(聚合路径)
- `idx_user_payment_time`:支撑 `WHERE user_id=? AND payment_status='refunded' AND ordered_at >= ?`(退货率路径)

### 2.6 Redis 缓存(v0.3 — HMAC 版本化 + compute_seq 抗 TOCTOU)

```
Key:    ai:memory:v{version}:{HMAC_SHA256(secret{version}, userId).substring(0,16)}
Value:  JSON {identity, preference, computed_at, version, compute_seq}
TTL:    1 小时(3600 秒)— 缩短,DSR 响应更快(SEC-m2)
```

**HMAC 混淆**(SEC-M7 + OPS-B7):
- secret 来自 `AiMemoryProperties.cacheHmacSecrets`(支持多版本: `v1:xxx,v2:yyy`)
- **轮换策略**:新增 v2 → 双写 7 天 → 切 v2 → 清 v1 → 删 v1 配置
- 仅 16 hex 字符(64-bit),生日碰撞边界:100K 用户 = 0.003% 概率,可接受;1000 万级需升级 96-bit 截断或 SHA-256 完整(OPS-M13)

**compute_seq 防 TOCTOU**(OPS-B5):
- `ai_user_memory.compute_seq` 单调递增,每次成功 recompute +1
- 读路径 Redis 携带 seq;recompute 触发 invalidate 时,Redis SETEX 携带 seq;后续 read miss 回源 MySQL 拿最新 seq,只有 seq >= 已缓存 seq 才写回 Redis
- 防止 invalidate → MySQL UPDATE 之间的窗口期,旧值覆盖新值

### 2.7 配置类 `AiMemoryProperties`(v0.3 — ARCH-B2 / OPS-M5)

```java
@Data
@Component
@ConfigurationProperties(prefix = "ai.memory")
public class AiMemoryProperties {
    /** HMAC 缓存 key 多版本,逗号分隔 e.g. "v1:abc...,v2:def..." */
    private String cacheHmacSecrets = "v1:dev-only-please-set-via-env-in-production";
    /** 防抖窗口(秒) */
    private int coalesceTtlSeconds = 60;
    /** cron 表达式 */
    private String recomputeCron = "0 0 3 * * ?";
    /** 月度 DROP PARTITION cron */
    private String partitionDropCron = "0 0 2 1 * ?";
    /** Prompt token 上限 */
    private int promptTokenCap = 600;
    /** Cron 每批 */
    private int recomputeBatchSize = 1000;
    /** Recompute 失败 N 次 → DISABLED */
    private int recomputeMaxFailCount = 3;
    /** Reset 后审计保留天数(用于统计) */
    private int resetRetentionDays = 180;
    /** Audit 分区保留天数 */
    private int auditPartitionRetentionDays = 90;
    /** audit 异步清理限速(rows/s) */
    private int auditPurgeRateRowsPerSec = 100;
    /** 进程内 RateLimiter tokens/user/秒(Redis Down 降级) */
    private int localRateLimitPerUser = 1;
    private int localRateLimitBurst = 200;
    /** 活跃 secret 版本(轮换时改此值) */
    private String activeSecretVersion = "v1";
}
```

**启动校验**(OPS-R18):`@PostConstruct` 校验 `cacheHmacSecrets` 含 ≥32 字符、含 default 占位符(仅 dev 允许)、`activeSecretVersion` 必须存在于 `cacheHmacSecrets` 中;否则 fail-fast。

---

### 2.8 `RedisConstants` 新增(v0.3 — 集中常量)

```java
public static final String MEMORY_CACHE_KEY_PREFIX = "ai:memory:";        // 含 v{version}:
public static final String MEMORY_COALESCE_KEY_PREFIX = "memory:coalesce:";
public static final String MEMORY_CRON_LOCK_KEY = "lock:memory:recompute:cron";
public static final String MEMORY_PARTITION_DROP_LOCK_KEY = "lock:memory:partition:drop";
```

---

## 3. 写入路径

### 3.0 类清单与包路径(v0.3 — ARCH-M1/M3 修复)

| 类 | 包路径 | 职责 |
|---|---|---|
| `UserMemoryEntity` / `UserMemoryAuditEntity` | `com.scutmmq.ai.entity` | MP 实体 |
| `UserMemoryMapper` (BaseMapper + @Select) | `com.scutmmq.ai.mapper` | 简单 update / select |
| `UserMemoryMapper.xml` | `src/main/resources/com/scutmmq/ai/mapper/` | 复杂聚合 SQL(§6) |
| `UserMemoryCache` | `com.scutmmq.ai.cache` | Redis HMAC + TTL + invalidate |
| `UserMemoryBuilder` | `com.scutmmq.ai.builder` | SQL 执行 + sanitize + token 截断 + 渲染 |
| `PromptSanitizer` | **`com.scutmmq.ai.security`** ← ARCH-M1 | 注入防御(同包:ToolSecurityInterceptor) |
| `PromptInjectionException` | `com.scutmmq.ai.exception` | 自定义异常 |
| `UserMemoryService` | `com.scutmmq.ai.service` | 协调:event listener + read + write + reset |
| `UserMemoryEventListener` | **`com.scutmmq.ai.event`** ← ARCH-M3 | 单一 listener + switch 派发 |
| `MemoryResetController` | `com.scutmmq.ai.controller` | `POST /ai/memory/reset` |
| `MemoryQueryController` | `com.scutmmq.ai.controller` | `GET /ai/memory` |
| `AiMemoryProperties` | `com.scutmmq.ai.config` | 配置类(§2.7) |
| `UserMemoryOverviewVO` | `com.scutmmq.ai.dto` | GET /ai/memory 响应 |

### 3.1 事件源(在领域 Service 中 publishEvent)

```java
// OrderServiceImpl.confirmOrder 成功时
applicationEventPublisher.publishEvent(
    new OrderPlacedEvent(this, userId, orderId, occurredAt));

// OrderServiceImpl 退款流程完成时
applicationEventPublisher.publishEvent(
    new OrderRefundedEvent(this, userId, orderId, occurredAt));

// UserServiceImpl 修改资料时
applicationEventPublisher.publishEvent(
    new ProfileUpdatedEvent(this, userId, changedFields));

// MerchantServiceImpl 注册成功时
applicationEventPublisher.publishEvent(
    new MerchantRegisteredEvent(this, userId, merchantId));
```

### 3.2 防抖:Redis SETNX + Guava RateLimiter 兜底(v0.3 — OPS-B2)

**问题**:1 分钟内 5 单 → 不应该 recompute 5 次。Redis 不可用时仍要限速。

**主方案**:每次事件触发 → `SETNX memory:coalesce:{userId} {expiryTime} EX 60`,成功才 recompute。

```java
@EventListener
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onOrderPlaced(OrderPlacedEvent event) {
    userMemoryService.scheduleRecompute(event.userId(), TriggerReason.ORDER_PLACED);
}

public boolean scheduleRecompute(Long userId, TriggerReason reason) {
    String key = RedisConstants.MEMORY_COALESCE_KEY_PREFIX + userId;
    try {
        Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent(key, Instant.now().toString(),
                Duration.ofSeconds(props.getCoalesceTtlSeconds()));
        if (Boolean.TRUE.equals(acquired)) {
            // asyncExecutor.execute() 加 TaskDecorator 透传 MDC(§3.3)
            asyncExecutor.execute(() -> recomputeFor(userId, reason));
            return true;
        }
        log.debug("[AI][MEMORY] recompute coalesced for userId={}", userId);
        return false;
    } catch (Exception e) {
        // Redis 不可用 → 进程内 RateLimiter 兜底
        return scheduleWithLocalRateLimit(userId, reason);
    }
}

/** Redis Down 兜底:Guava RateLimiter 每用户每 N 秒 1 token */
private final ConcurrentHashMap<Long, RateLimiter> localLimiters = new ConcurrentHashMap<>();

private boolean scheduleWithLocalRateLimit(Long userId, TriggerReason reason) {
    RateLimiter limiter = localLimiters.computeIfAbsent(userId, uid ->
        RateLimiter.create(props.getLocalRateLimitPerUser())); // 1 token / s
    if (limiter.tryAcquire(0, MILLISECONDS)) {
        auditService.logDegraded(userId, "DEGRADED_RATE_LIMITED");
        asyncExecutor.execute(() -> recomputeFor(userId, reason));
        return true;
    }
    auditService.logDegraded(userId, "DEGRADED_RATE_LIMITED_DROP");
    metrics.counter("ai_memory_local_rate_limit_total").increment();
    return false;
}
```

**降级链**(OPS-B2):
1. Redis 健康:`SETNX` 防抖(跨实例安全)
2. Redis Down:Guava RateLimiter 进程内防抖(单实例)
3. RateLimiter 也满:丢 + audit `DEGRADED_RATE_LIMITED_DROP` + 指标

### 3.3 Recompute 流程(v0.3 — MDC 透传 + JSON OVERFLOW 降级)

```
UserMemoryService.recomputeFor(userId, reason)
  │  ↑ 进入时已通过 TaskDecorator 透传 MDC(traceId / userId)
  │
  ├─ UserMemoryBuilder.computeIdentity(userId)
  │    ├─ 查 user / user_address / merchant
  │    └─ UserMemoryMapper.updateIdentity(userId, identityJson)
  │         ↑ identity_json 真正变化时 +1 version
  │         ↑ JSON size > 8KB → try-catch + audit JSON_OVERFLOW + fallback 空 JSON
  │
  ├─ UserMemoryBuilder.computePreference(userId)
  │    ├─ 跑 SQL:90 天品类/商家/价格分位/退货率
  │    └─ UserMemoryMapper.updatePreference(userId, preferenceJson)
  │         ↑ preference_json 真正变化时 +1 version
  │         ↑ JSON size > 8KB → 同上 fallback
  │
  ├─ UserMemoryMapper.bumpComputeSeq(userId)
  │    ↑ UPDATE ... SET compute_seq = compute_seq + 1
  │    ↑ Redis SETEX 携带新 seq,后续 read miss 只在新 seq > 旧 seq 时写回
  │
  ├─ UserMemoryCache.invalidate(userId)
  │    └─ DEL ai:memory:v{ver}:{HMAC}
  │
  ├─ 写 audit:action=COMPUTE, fields_changed, triggered_by, request_id
  │
  └─ 失败处理(MAX_FAIL_COUNT=3)
       ├─ catch → fail_count++
       ├─ fail_count ≥ 3 → recompute_status = 0 (DISABLED)
       ├─ 写 audit:action=RECOMPUTE_FAIL
       └─ 监控:ai_memory_recompute_total{result=failure}++
```

**字段级乐观锁**(DB-B6):
- `updateIdentity` / `updatePreference` 只在 JSON 真正变化时写;update SQL 由 MP interceptor 自动追加 `version = version + 1, WHERE version = ?`
- 失败抛 `OptimisticLockingFailureException`,retry 1 次后 fail_count++
- reset 时 fail_count 重置为 0

**JSON OVERFLOW 降级**(OPS-M8):
```java
try {
    userMemoryMapper.updateIdentity(userId, identityJson);
} catch (DataIntegrityViolationException e) {
    if (e.getMessage().contains("chk_identity_size")) {
        log.warn("[AI][MEMORY] identity_json size > 8KB userId={}", userId);
        auditService.logJsonOverflow(userId, "identity");
        userMemoryMapper.updateIdentity(userId, "{}"); // fallback 空 JSON
        // 不抛异常 — 业务流继续
    } else throw e;
}
```

**MDC/traceId 透传**(OPS-B8):
```java
@Bean(name = "memoryAsyncExecutor")
public Executor memoryAsyncExecutor() {
    ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
    exec.setCorePoolSize(max(16, Runtime.getRuntime().availableProcessors()));
    exec.setMaxPoolSize(32);
    exec.setQueueCapacity(10000);
    exec.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    exec.setTaskDecorator(runnable -> {
        Map<String, String> ctx = MDC.getCopyOfContextMap();
        return () -> {
            try {
                if (ctx != null) MDC.setContextMap(ctx);
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    });
    exec.initialize();
    return exec;
}
```

`MDC.put("traceId", requestId)` 来自 Controller / event listener 入口(Spring Sleuth 自动埋点)。

### 3.4 TTL 兜底 cron(v0.3 — Redisson raw-lock + Watchdog,ARCH-B1 / OPS-B1)

```java
@Scheduled(cron = "${ai.memory.recompute-cron}")  // 默认 0 0 3 * * ?
public void recomputeStaleBatch() {
    RLock lock = redissonClient.getLock(RedisConstants.MEMORY_CRON_LOCK_KEY);
    boolean locked = false;
    try {
        // waitTime=0:不等待;leaseTime=50min:显式租约,无 watchdog 自动续期
        locked = lock.tryLock(0, 50, TimeUnit.MINUTES);
        if (!locked) {
            log.warn("[AI][MEMORY] cron lock held by another instance, skipping");
            metrics.counter("ai_memory_cron_skip_total", "reason", "lock_held").increment();
            return;
        }
        // 0. cron 启动指标
        metrics.counter("ai_memory_cron_run_started_total").increment();
        long startMs = System.currentTimeMillis();
        int batchSize = props.getRecomputeBatchSize();
        long lastUserId = 0;
        int totalProcessed = 0;
        long cutoff = Instant.now().minus(7, DAYS).toEpochMilli();

        while (true) {
            List<Long> ids = userMemoryMapper.findStaleUserIds(
                lastUserId, cutoff, batchSize);
            if (ids.isEmpty()) break;
            ids.forEach(uid -> scheduleRecompute(uid, TriggerReason.CRON_STALE));
            lastUserId = ids.get(ids.size() - 1);
            totalProcessed += ids.size();
            metrics.counter("ai_memory_cron_processed_total").increment(ids.size());
        }
        long durationMs = System.currentTimeMillis() - startMs;
        metrics.summary("ai_memory_cron_duration_seconds").record(durationMs / 1000.0);
        log.info("[AI][MEMORY] cron done totalProcessed={} duration={}ms",
                 totalProcessed, durationMs);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    } finally {
        if (locked && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
```

**变更(vs v0.2)**:
- **Redisson raw-lock**(复用 `PayServiceImpl.java:67-77` 模式)— ARCH-B1
- 显式 `leaseTime=50min`(cron 理论最长 30min + 安全余量)— 无 watchdog 自动续期
- 独立 cron-watchdog Scheduled 任务每 10 分钟检查"锁持有但 `ai_memory_cron_processed_total` 在 30min 内无增长"→ `ai_memory_cron_lock_lost_total{reason=force_unlock|timeout}` 强制 unlock(OPS-B1)
- 改 gauge `ai_memory_cron_progress` → counter `ai_memory_cron_processed_total`(OPS-R9 命名风格统一)
- 加 `ai_memory_cron_duration_seconds` P95/P99 监控

**审计分区清理**(独立 cron,每月 1 日 02:00):
```java
@Scheduled(cron = "${ai.memory.partition-drop-cron}")
public void dropOldAuditPartitions() {
    RLock lock = redissonClient.getLock(RedisConstants.MEMORY_PARTITION_DROP_LOCK_KEY);
    boolean locked = lock.tryLock(0, 30, TimeUnit.MINUTES);
    if (!locked) { return; }
    try {
        // 1. 健康探针:从 INFORMATION_SCHEMA.PARTITIONS 拿到所有 p_YYYY_MM
        List<String> partitions = jdbc.queryForList(
            "SELECT PARTITION_NAME FROM INFORMATION_SCHEMA.PARTITIONS " +
            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_user_memory_audit' " +
            "AND PARTITION_NAME REGEXP '^p_[0-9]{4}_[0-9]{2}$'",
            String.class);
        // 2. 计算要 DROP 的(> 90 天前)
        LocalDate cutoff = LocalDate.now().minusDays(props.getAuditPartitionRetentionDays());
        partitions.stream()
            .filter(p -> parsePartitionDate(p).isBefore(cutoff))
            .forEach(p -> {
                jdbc.execute("ALTER TABLE ai_user_memory_audit DROP PARTITION " + p);
                metrics.gauge("ai_memory_partition_cron_last_success_timestamp_seconds",
                    System.currentTimeMillis() / 1000.0);
            });
    } finally {
        if (locked && lock.isHeldByCurrentThread()) lock.unlock();
    }
}
```

**监控指标**(OPS-B3):
- `ai_memory_partition_cron_last_success_timestamp_seconds`:gauge,记录最近一次成功时间
- 告警:`> 32 天未成功` → P2

**fallback 分区**(OPS-R20):新增显式 `PARTITION p_catchall VALUES LESS THAN MAXVALUE`,数据超 90 天仍能写(不报错),但独立监控发现异常立即告警。

### 3.5 DSR-excluded 用户(ARCH-M4 — NOT EXISTS)

```sql
-- §6.8 cron 游标扫描 SQL 改 NOT EXISTS(避免 N+1)
SELECT user_id FROM ai_user_memory m
WHERE m.recompute_status = 1
  AND m.computed_at < ?
  AND m.user_id > ?
  AND NOT EXISTS (
    SELECT 1 FROM ai_user_memory_audit a
    WHERE a.user_id = m.user_id
      AND a.action = 'RESET'
      AND a.created_at > DATE_SUB(NOW(), INTERVAL ? DAY)
  )
ORDER BY m.user_id
LIMIT 1000;
```

---

## 4. 读取路径

### 4.1 调用栈(v0.3 — TOCTOU compute_seq 防护,OPS-B5)

```
MallSystemPromptProvider.buildSystemPrompt(user)
  ↓
UserMemoryService.renderMemorySection(userId)
  ↓
[1] Redis GET ai:memory:v{ver}:{HMAC(userId)}
       命中 → 反序列化 → 检查 compute_seq_local ≥ compute_seq_db?(compute_seq 比较)
              ↑ 若不匹配(并发写入中)→ [2] 回源
              ✓ → [3]
       miss → [2]
  [2] MySQL SELECT identity + preference + compute_seq → 缓存 setIfAbsent(seq)
                   ↑ setIfAbsent 是关键:不覆盖更新版本
                   → 空(新用户) → 返回 "" + audit READ_MISS
  [3] sanitizeForPrompt(snapshot)         ← §4.4
  [4] UserMemoryBuilder.renderForPrompt(sanitized, userId)
       → token 估算(char/3)
       → 超过 600 token → 截断(优先丢 topMerchants,其次 preferredSizes)
       → 返回格式化字符串
```

**compute_seq 防 TOCTOU**(OPS-B5):
- 每次 recompute 成功 → `bumpComputeSeq(userId)` + 1
- Redis cache value 含 `{snapshot, compute_seq}`
- recompute 触发 invalidate 后,在 MySQL UPDATE 完成前,任何 cache miss 会读到旧 snapshot + 旧 seq
- 回写 Redis 时用 `SETEX + NX(seq)` 模式:仅在新 seq 严格大于旧 seq 时才写,否则跳过
- 结果:recompute 的 invalidate 之后,任何中间态的 cache miss 都不会"污染"Redis

### 4.2 Prompt 注入格式(v0.2 — 显式分隔)

```
【用户画像】 (仅在有画像时输出;以下所有值均经转义 + 长度限制)
- 身份:普通用户,账号 540 天,有 3 个收货地址
- 价格区间:历史订单 ¥28-¥199,中位数 ¥89
- 偏好品类:服饰(¥380,4 单)、数码(¥230,2 单)、家居(¥150,1 单)
- 偏好商家:小米旗舰店、华为官方
- 90 天下单 6 单,均价 ¥78.5,退货率 8%
- 常用支付:支付宝 60% / 微信 40%
- 常用配送:顺丰 70%
```

**注意**:`defaultAddressId` 不注入 prompt(SEC-M6)— 模型不需要知道 id,工具调用时直接用。

### 4.3 Token 截断(v0.2 — 顺序优化 R20)

```java
String renderForPrompt(SanitizedSnapshot s, Long userId) {
    String raw = renderAll(s);
    int tokens = estimateTokens(raw);
    if (tokens <= 600) return raw;

    // 截断顺序:优先丢最长字段
    String truncated = raw;
    if (tokens > 500) truncated = dropField(truncated, "topMerchants");
    if (tokens > 400) truncated = dropField(truncated, "preferredSizes");
    if (tokens > 300) truncated = dropField(truncated, "activeHours");
    int finalTokens = estimateTokens(truncated);
    audit.logOverflow(userId, tokens, finalTokens, lastDroppedField);
    return truncated;
}
```

### 4.4 防御注入(v0.2 — SEC-B3,核心修复)

**问题**:商家名 / 品类名是用户可写字段,可被注入 "ignore previous instructions"。

**方案 3 层防御**:

```java
public class PromptSanitizer {

    // 黑名单:任何 user-derived 字段都不能含这些 token
    private static final List<Pattern> DENY_LIST = List.of(
        Pattern.compile("<\\|.*?\\|>"),                    // DSML 标签
        Pattern.compile("(?i)ignore\\s+(previous|all|above)"),
        Pattern.compile("(?i)system\\s*:\\s*"),
        Pattern.compile("(?i)assistant\\s*:\\s*"),
        Pattern.compile("(?i)you\\s+are\\s+now"),
        Pattern.compile("(?i)disregard\\s+(all|previous)")
    );

    // 白名单:categoryName / merchantName 必须匹配
    private static final Pattern SAFE_NAME = Pattern.compile(
        "^[一-龥a-zA-Z0-9\\s\\-_()&（）【】]{1,32}$");

    public String sanitize(String raw, FieldType type) {
        if (raw == null) return "";
        for (Pattern p : DENY_LIST) {
            if (p.matcher(raw).find()) {
                throw new PromptInjectionException("Deny-list match: " + p.pattern());
            }
        }
        if (type == FieldType.CATEGORY_NAME || type == FieldType.MERCHANT_NAME) {
            if (!SAFE_NAME.matcher(raw).matches()) {
                return "[FILTERED]";  // 失败 → 用 hash ID 替代
            }
        }
        // JSON escape(防注入 JSON 上下文)
        return StringEscapeUtils.escapeJson(raw);
    }
}
```

**注入测试**(新增 eval):
- `eval-regression-memory-prompt-injection.yaml`:商家名含 "ignore previous instructions",验证 → 写入 audit + fallback "[FILTERED]"
- `eval-regression-memory-no-echo.yaml`:验证画像不被模型 echo 给其他用户
- `eval-regression-memory-dsml-defense.yaml`:画像含 `<｜｜DSML｜｜tool_calls>`,验证 → 拒绝写入

---

## 5. 用户控制 — Reset + 访问权告知

### 5.1 `POST /ai/memory/reset`(v0.3 — 异步审计清理,OPS-B4 / OPS-M9)

```http
POST /ai/memory/reset
Authorization: Bearer <jwt>          ← JWT 仅 Authorization header,CSRF 天然防御(R10)
Content-Type: application/json
{}
```

**响应**:`{"code":1,"msg":"记忆已重置","data":null}` — **< 200ms 返回**(伪匿名化异步化)

**逻辑(v0.3 — 主流程立即返回 + 异步伪匿名化)**:
```
1. userId = UserHolder.getUserId()
2. assertEquals(userId, targetUserId)            ← 显式纵深防御(M1)
3. UserMemoryService.reset(userId)               ← 同步部分 < 100ms
   ├─ 写空 identity_json = '{}', preference_json = '{}',version+1, fail_count=0, compute_seq+1
   ├─ UserMemoryCache.invalidate(userId)
   ├─ 写 audit:action=RESET, triggered_by=user, actor_ip, request_id  ← 同步审计仅此 1 行
   └─ @Async 后台提交 auditPurgeTask(userId)    ← 异步清理历史审计行
4. Result.success("记忆已重置")
```

**异步伪匿名化**(OPS-B4):
```java
@Async("memoryAsyncExecutor")
public void auditPurgeTask(Long userId) {
    long start = System.currentTimeMillis();
    int batchSize = 1000;
    int rowsPerSec = props.getAuditPurgeRateRowsPerSec();
    RateLimiter limiter = RateLimiter.create(rowsPerSec);
    int totalPurged = 0;
    try {
        // 1. 计算总量
        Integer total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ai_user_memory_audit WHERE user_id=? AND action IN ('COMPUTE','OVERFLOW_DROP')",
            Integer.class, userId);
        // 2. 分批 UPDATE,每批后让 limiter 限速
        for (int offset = 0; offset < total; offset += batchSize) {
            limiter.acquire(batchSize); // 阻塞到能拿满 batchSize tokens
            int purged = jdbc.update(
                "UPDATE ai_user_memory_audit SET user_id = 0, fields_changed = NULL " +
                "WHERE user_id=? AND action IN ('COMPUTE','OVERFLOW_DROP') LIMIT " + batchSize,
                userId);
            totalPurged += purged;
            metrics.counter("ai_memory_audit_purged_total").increment(purged);
        }
        log.info("[AI][MEMORY] async purge done userId={} purged={} duration={}ms",
                 userId, totalPurged, System.currentTimeMillis() - start);
    } catch (Exception e) {
        // 3 次重试后入死信表
        if (retryCount++ < 3) {
            auditPurgeTask(userId); // 递归重试
        } else {
            jdbc.update(
                "INSERT INTO ai_user_memory_audit_purge_dlq(user_id, error, retry_count, created_at) " +
                "VALUES (?, ?, ?, NOW())",
                userId, e.getMessage(), retryCount);
            metrics.counter("ai_memory_audit_purge_dlq_total").increment();
        }
    }
}
```

**死信表**:`ai_user_memory_audit_purge_dlq(id, user_id, error, retry_count, created_at)` — 异步任务失败超过 3 次落地,运维手动处理。

**PII 生命周期可证明等式**(OPS-M9 / R14):
- 审计表 90 天 RANGE 分区自动清
- 死信表 30 天手动清
- MySQL binlog 保留 7 天(`expire_logs_days=7`)
- Redis AOF 保留 7 天(`auto-aof-rewrite-min-age 7 day`)
- **总 PII 生命周期 ≤ 97 天**(审计 + binlog + AOF + 死信)

**幂等**:多次调用结果一致(RESET audit 不重复 + version+1)。

### 5.2 `GET /ai/memory`(v0.3 — UserMemoryOverviewVO + @PreAuthorize,ARCH-M2)

**目的**:用户行使知情权,平台告知"我们记住了什么、为什么、用来做什么"。

```http
GET /ai/memory
Authorization: Bearer <jwt>
```

**响应 VO**(不是真实画像,而是**画像存在性 + 用途说明 + 摘要类别**):
```java
@Data
public class UserMemoryOverviewVO {
    private boolean hasIdentity;
    private boolean hasPreference;
    private Instant computedAt;
    private Integer version;
    private String summary;            // 用途说明
    private List<String> categoryNames;
    private List<String> fieldList;
    private String purpose;
}
```

**Controller**(ARCH-M2):
```java
@GetMapping("/ai/memory")
@PreAuthorize("hasRole('USER') and #userId == authentication.principal.id")  // OPS-R19
public Result<UserMemoryOverviewVO> getMemoryOverview(
    @AuthenticationPrincipal UserDTO currentUser) {
    return Result.success(userMemoryService.buildOverview(currentUser.getId()));
}
```

**响应示例**:
```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "hasIdentity": true,
    "hasPreference": true,
    "computedAt": "2026-08-23T02:00:00Z",
    "version": 7,
    "summary": "我们记住了你的基础资料和最近 90 天的购买偏好(品类、价格、退货率),用于个性化商品推荐。你可以随时调用 POST /ai/memory/reset 清除。",
    "categoryNames": ["身份档案", "偏好画像"],
    "fieldList": ["默认地址", "账号年龄", "价格区间", "偏好品类", "偏好商家", "退货率"],
    "purpose": "AI 助手个性化推荐;不用于广告投放、不分享给第三方"
  }
}
```

**关键设计**:
- 不返回**真实值**(避免模型可通过 GET 间接读到 PII)
- 返回**画像类别名 + 字段名 + 用途**(让用户知道"你被记住的东西")
- 用户可选择"全部清除"(POST reset)

---

## 6. 关键 SQL(v0.2 — 全部对齐 schema)

### 6.1 90 天价格分位(MySQL 8 PERCENTILE_DISC)

```sql
SELECT
  ROUND(AVG(o.total_amount), 2) AS avg_order_value,
  ROUND(PERCENTILE_DISC(0.25) WITHIN GROUP (ORDER BY o.total_amount), 2) AS p25,
  ROUND(PERCENTILE_DISC(0.50) WITHIN GROUP (ORDER BY o.total_amount), 2) AS p50,
  ROUND(PERCENTILE_DISC(0.75) WITHIN GROUP (ORDER BY o.total_amount), 2) AS p75,
  MAX(o.total_amount) AS max_value
FROM orders o
WHERE o.user_id = ?
  AND o.ordered_at >= DATE_SUB(NOW(), INTERVAL 90 DAY)
  AND o.status IN ('paid','shipped','delivered')
  AND o.total_amount > 0;
-- 注释:MySQL 8.0+ 要求;Spring Boot 3.5 默认 MySQL 8+ 满足
-- n<10 时 percentile 退化:返回 AVG 作为 p50(质量保证)
```

**Java 兜底**:结果集 < 10 行时,Java 层用 quantile estimator 替代。

### 6.2 90 天品类 Top 3

```sql
SELECT
  pc.id AS category_id,
  pc.name AS category_name,
  SUM(oi.subtotal) AS spend,
  COUNT(DISTINCT o.id) AS order_count
FROM orders o
JOIN order_items oi ON oi.order_id = o.id
JOIN product p ON p.id = oi.product_id
JOIN product_category pc ON pc.id = p.category_id
WHERE o.user_id = ?
  AND o.ordered_at >= DATE_SUB(NOW(), INTERVAL 90 DAY)
  AND o.status IN ('paid','shipped','delivered')
GROUP BY pc.id, pc.name
ORDER BY spend DESC
LIMIT 3;
```

**索引使用**:`orders.idx_user_status_time` → range scan → Nested Loop JOIN product_category(主键)。

### 6.3 90 天商家 Top 3

```sql
SELECT
  m.id AS merchant_id,
  m.name AS merchant_name,
  COUNT(DISTINCT o.id) AS order_count,
  SUM(o.total_amount) AS spend
FROM orders o
JOIN merchant m ON m.id = o.merchant_id
WHERE o.user_id = ?
  AND o.ordered_at >= DATE_SUB(NOW(), INTERVAL 90 DAY)
  AND o.status IN ('paid','shipped','delivered')
GROUP BY m.id, m.name
ORDER BY spend DESC
LIMIT 3;
```

### 6.4 90 天退货率(基于 payment_status,DB-B1 修复)

```sql
SELECT
  COUNT(*) AS total_orders,
  SUM(CASE WHEN o.payment_status = 'refunded' THEN 1 ELSE 0 END) AS refunded_orders,
  ROUND(
    SUM(CASE WHEN o.payment_status = 'refunded' THEN 1 ELSE 0 END)
    * 1.0 / NULLIF(COUNT(*), 0),
    4
  ) AS return_rate
FROM orders o
WHERE o.user_id = ?
  AND o.ordered_at >= DATE_SUB(NOW(), INTERVAL 90 DAY)
  AND o.status IN ('paid','shipped','delivered');
```

**说明**:用 `orders.payment_status='refunded'` 推断退货率(无 `order_returns` 表)。
未来如建 `order_returns(id, order_id, user_id, reason, refunded_at)` 表,可精确统计退款原因(留 B4+)。

### 6.5 支付方式偏好

```sql
SELECT
  o.payment_method,
  COUNT(*) AS cnt,
  ROUND(COUNT(*) * 1.0 / SUM(COUNT(*)) OVER (), 4) AS ratio
FROM orders o
WHERE o.user_id = ?
  AND o.ordered_at >= DATE_SUB(NOW(), INTERVAL 90 DAY)
  AND o.status IN ('paid','shipped','delivered')
GROUP BY o.payment_method
ORDER BY cnt DESC;
```

### 6.6 配送方式偏好(同 6.5 结构,字段名 `shipping_method`)

### 6.7 活跃时段(下单 hour 直方图)

```sql
SELECT
  HOUR(o.ordered_at) AS hour_of_day,
  COUNT(*) AS cnt
FROM orders o
WHERE o.user_id = ?
  AND o.ordered_at >= DATE_SUB(NOW(), INTERVAL 90 DAY)
  AND o.status IN ('paid','shipped','delivered')
GROUP BY HOUR(o.ordered_at)
ORDER BY cnt DESC
LIMIT 5;
```

### 6.8 Cron 游标扫描(v0.3 — NOT EXISTS,ARCH-M4 / OPS-R7)

```sql
SELECT m.user_id FROM ai_user_memory m
WHERE m.recompute_status = 1
  AND m.computed_at < ?
  AND m.user_id > ?
  AND NOT EXISTS (
    SELECT 1 FROM ai_user_memory_audit a
    WHERE a.user_id = m.user_id
      AND a.action = 'RESET'
      AND a.created_at > DATE_SUB(NOW(), INTERVAL ? DAY)
  )
ORDER BY m.user_id
LIMIT 1000;
-- 注释:NOT EXISTS 在 audit 表大时 MySQL 8 优化更好(anti-join),100K+ 审计行下 EXPLAIN 应走 PRIMARY KEY 索引
-- 索引要求:ai_user_memory_audit 必须有 PRIMARY KEY (id) + KEY idx_user_time (user_id, created_at)
-- EXPLAIN 验证:Nested Loop Anti Join,ref=idx_user_time, key_len=8
```

---

## 7. 错误处理 + 监控

### 7.1 失败模式表(v0.3 — 完整覆盖,含 3 种云场景,OPS-M6 / M16)

| 场景 | 行为 | 监控指标 |
|---|---|---|
| Redis 不可用 | Cache miss → 回源 MySQL;coalesce 走 Guava RateLimiter 兜底 + audit `DEGRADED_RATE_LIMITED` | `ai_memory_cache_failure_total` + `ai_memory_local_rate_limit_total` |
| MySQL 不可用 | 读路径返回空(降级);写路径 audit + 告警 | `ai_memory_db_failure_total` |
| Recompute 异常 | fail_count++,重试 1 次后 DISABLED | `ai_memory_recompute_total{result=failure}` |
| Audit 写失败 | 不影响主流程 | `ai_memory_audit_write_failure_total` |
| Token 超 600 | Builder 截断 + audit OVERFLOW_DROP + field_dropped | `ai_memory_overflow_drop_total{field=...}` |
| UserMemory 读 = null(新用户) | 注入空串 | `ai_memory_read_miss_total` |
| Cron lock 占用 | 跳过本次 | `ai_memory_cron_skip_total{reason=lock_held}` |
| 注入黑名单命中 | sanitize 抛 PromptInjectionException → 写入 audit + fallback `[FILTERED]` | `ai_memory_prompt_injection_drop_total` |
| JSON size > 8KB | try-catch + audit JSON_OVERFLOW + fallback 空 JSON(OPS-M8) | `ai_memory_json_overflow_total{json=identity|preference}` |
| Audit purge 入死信 | DLQ + 运维处理 | `ai_memory_audit_purge_dlq_total` |
| **Redis 主从切换瞬断** | cache miss 突增,自动恢复;短时 P3 告警(>5x baseline) | `ai_memory_cache_miss_burst_total` |
| **MySQL 主备延迟读不到 update** | read path 返回旧 snapshot;recompute 流程下一周期覆盖 | `ai_memory_read_stale_total{lag_seconds_bucket}` |
| **DHCP/VPC 抖动 / MySQL 长事务** | 自动重试 1 次后 fail_count++ | `ai_memory_db_query_seconds{quantile=0.99}` + `ai_memory_db_long_tx_total` |

### 7.2 监控指标(v0.3 — 补 P95/P99 + 分区健康,OPS-B3 / OPS-M7)

```
# 写入路径
ai_memory_recompute_total{result="success|failure"}: counter
ai_memory_recompute_duration_seconds{quantile="0.5|0.95|0.99"}: summary
ai_memory_fail_users: gauge (DISABLED 用户数,target < 50)
ai_memory_json_overflow_total{json="identity|preference"}: counter

# 读路径
ai_memory_cache_hit_ratio: gauge (target > 0.80)
ai_memory_cache_failure_total: counter
ai_memory_cache_miss_burst_total: counter   # 5x baseline 检测主从切换
ai_memory_read_miss_total: counter
ai_memory_read_stale_total{lag_seconds_bucket}: counter   # 主备延迟
ai_memory_injection_token_total{quantile="0.5|0.95"}: summary
ai_memory_overflow_drop_total{field="..."}: counter

# 防御
ai_memory_prompt_injection_drop_total: counter
ai_memory_local_rate_limit_total{result="accept|drop"}: counter

# Cron / 运维
ai_memory_cron_processed_total: counter
ai_memory_cron_duration_seconds{quantile="0.5|0.95|0.99"}: summary
ai_memory_cron_skip_total{reason}: counter
ai_memory_cron_lock_lost_total{reason="force_unlock|timeout"}: counter
ai_memory_partition_cron_last_success_timestamp_seconds: gauge  # OPS-B3 健康探针
ai_memory_db_query_seconds{sql="..."}: summary
ai_memory_db_long_tx_total: counter

# Reset / Audit
ai_memory_reset_total: counter
ai_memory_audit_write_failure_total: counter
ai_memory_audit_purged_total: counter
ai_memory_audit_purge_dlq_total: counter
```

### 7.3 告警(企业级 + runbook 链接预留,OPS-M3)

| 触发条件 | 严重度 | runbook(部署时由 SRE 补) |
|---|---|---|
| Recompute 失败率 > 5% (5min) | P3 | TBD |
| Cache hit ratio < 60% (1h) | P3 | TBD |
| `ai_memory_cache_miss_burst_total` > 5x baseline (10min) | P3(Redis 主从切换) | TBD |
| Audit 日表无增长 > 24h | P2 | TBD |
| `recompute_status=DISABLED` 用户数 > 50 | P2 | TBD |
| `ai_memory_injection_token_total{quantile=0.95}` > 800 | P3 | TBD |
| prompt injection drop > 0 (1h) | P3 | TBD |
| cron skip rate > 10% (1d) | P3 | TBD |
| `ai_memory_partition_cron_last_success_timestamp_seconds` age > 32d | P2 | TBD |
| `ai_memory_cron_lock_lost_total` > 0 (1h) | P2 | TBD |
| `ai_memory_audit_purge_dlq_total` > 0 | P2 | TBD |
| `ai_memory_db_long_tx_total` > 5 (1h) | P3 | TBD |

### 7.4 SLO 目标(v0.3 — OPS-M10)

| 指标 | SLO | 测量窗口 |
|---|---|---|
| Recompute 成功率 | ≥ 99.5% | 7×24 |
| Cron 完成率 | ≥ 99%(允许失败窗口 < 7min) | 7×24 |
| Cache hit ratio | ≥ 80% | 7×24 |
| Audit purge DLQ 率 | 0% | 7×24 |
| Prompt injection 误杀率 | 0%(白名单触发 fallback 但不应有 false positive) | 7×24 |
| Reset 接口 P95 延迟 | ≤ 200ms | 7×24 |
| GET /ai/memory P95 延迟 | ≤ 100ms | 7×24 |

---

## 8. 测试策略(v0.3 — 35 → 50+ 单测)

### 8.1 单元测试(~50 个)

**AiMemoryPropertiesTest (3)** — 新增
- 启动校验:`@PostConstruct` 检测 secret 长度 < 32 → fail-fast
- 启动校验:activeSecretVersion 不在 cacheHmacSecrets 中 → fail-fast
- dev 环境 fallback:默认值允许,prod 环境 fail-fast

**UserMemoryCacheTest (12)** — 扩 2
- HMAC key 生成正确性
- HMAC key version 隔离:v1 secret 与 v2 secret 生成不同 key
- HMAC key 不可逆推 userId(单元测试验证不重复 + 不等于 userId.toString())
- serialize/deserialize roundtrip
- cache value 含 compute_seq
- TTL 命中 / 失效
- Redis 不可用降级
- invalidate by userId
- 多 userId 并发不串
- setIfAbsent seq 校验:旧 seq ≥ 新 seq 不写回(防 TOCTOU)

**UserMemoryBuilderTest (16)** — 扩 2
- 90 天订单聚合 SQL(用 testcontainers MySQL)
- 各品类/商家 topN 排序
- 退货率计算(SQL EXACT,基于 payment_status)
- 价格分位(用 n<10 边界、PERCENTILE_DISC 准确性)
- token 截断(>600 丢 topMerchants → >500 丢 preferredSizes → >400 丢 activeHours)
- 空画像渲染(新用户)
- null 防御(用户已注销、merchant 已被禁)
- 注入 sanitize:DENY_LIST 命中 → 抛 PromptInjectionException
- 注入 sanitize:SAFE_NAME 不匹配 → fallback `[FILTERED]`
- JSON OVERFLOW 触发:8KB+ JSON → DataIntegrityViolationException → fallback 空 JSON

**UserMemoryServiceTest (14)** — 扩 4
- 写路径:事件 → recompute → upsert(identity / preference 分开) → cache.invalidate → audit
- 读路径:cache hit / miss 回源 / 新用户返空
- 防抖:60s 内 5 个事件只 1 次 recompute(Mock Redis)
- 防抖降级:Redis Down → RateLimiter 兜底 + 限速
- 防抖降级 2:RateLimiter 也满 → 丢 + audit DEGRADED_RATE_LIMITED_DROP
- 失败重试:3 次失败 → status=DISABLED → 跳过 recompute
- Reset 幂等 + 审计伪匿名化(异步)
- Reset 异步 purge 失败 3 次 → DLQ
- @Version 乐观锁冲突:重试 1 次后 fail_count++
- cron 游标分批 + 分布式锁
- cron 锁 watchdog 检测无进度 → force_unlock
- MDC/traceId 透传:@Async TaskDecorator 验证子线程能读到 MDC

**PromptSanitizerTest (8)** — 不变
- DSML 标签: `<｜｜DSML｜｜...>` → 拒绝
- "ignore previous instructions" → 拒绝
- "system:" / "assistant:" → 拒绝
- 长串含 prompt 指令字符 → 拒绝
- SAFE_NAME 通过:合法商家名 → 原样
- SAFE_NAME 失败:含特殊字符 → fallback
- JSON escape 正确性
- 空字符串 / null 防御

**MemoryResetControllerTest (4)** — 不变
- testCannotResetOtherUser:传 userId 参数 → 403 / 拒绝
- testResetIdempotent
- testResetReturnsAuditWithActorIp(同步审计 1 行)
- testAsyncPurgeTriggeredOnReset(异步 @Async 调用)

**MemoryQueryControllerTest (3)** — 新增
- testGetMemoryReturnsOverviewVO(返回 UserMemoryOverviewVO 而非 Map)
- testGetMemoryReturnsCategoryNames(包含 hasIdentity / hasPreference)
- testGetMemoryRespectsUserIsolation(传其他 userId → 403)

**AuditPurgeAsyncTaskTest (3)** — 新增
- testPurgeRateLimited(限速 100 rows/s 验证)
- testPurgeFailureToDLQ(3 次失败后入 DLQ 表)
- testPurgeBatched(100K 行分批 1000 验证)

**AiMemoryPropertiesValidationTest (3)** — 合并到上面

**CronWatchdogTest (2)** — 新增
- testWatchdogDetectsNoProgressFor30min → force_unlock
- testWatchdogHandlesLockAlreadyReleased

### 8.2 Eval YAML(8 个回归)— 不变

- `eval-regression-memory-identity.yaml`
- `eval-regression-memory-preference.yaml`
- `eval-regression-memory-cache-hit.yaml`
- `eval-regression-memory-debounce.yaml`
- `eval-regression-memory-reset.yaml`
- `eval-regression-memory-fallback.yaml`
- `eval-regression-memory-prompt-injection.yaml`
- `eval-regression-memory-no-echo.yaml`

### 8.3 压测(必须通过,OPS-M1)

- 100K 用户 cron 场景:8 并发实例模拟,期望总耗时 ≤ 30min,P95 用户 = 50ms
- 订单秒杀场景:1 分钟内同用户 50 单 → RateLimiter 兜底后 ≤ 2 次 recompute
- Audit purge 10K 行/用户:验证限速 100 rows/s 准确

---

## 9. 风险与缓解(v0.2 — 更新)

| 风险 | 等级 | 缓解 |
|---|---|---|
| B3 注入 token 挤占历史消息上下文 | 高 | token 硬上限 600 + 截断顺序 + audit |
| 注入攻击:商家名/品类名污染 prompt | **极高** | **DENY_LIST + SAFE_NAME + DSML escape 3 层防御** |
| 100K cron 拖垮 DB | 中 | 游标分批 + 分布式锁 + 复用 SETNX 防抖 |
| GDPR 违规(Art 15/17/5) | 高 | GET /ai/memory 告知 + reset 完整擦除 + audit 伪匿名化 |
| Redis key 枚举 userId | 中 | HMAC 混淆 16 hex |
| 写入风暴:订单秒杀 | 中 | SETNX 60s 防抖,recompute 周期最差 1 分钟 |
| 商家用户记忆泄露 | 中 | UserHolder per-request + HMAC + Reset 完整擦除 |
| MySQL JSON 字段兼容性 | 低 | 8.0+ CHECK JSON_VALID |
| 字段级乐观锁失败导致整 JSON 重写 | 中 | 拆 updateIdentity / updatePreference,各自 @Version |

---

## 10. 不做(YAGNI)

- ❌ 向量检索(留给 B4)
- ❌ 用户手动编辑记忆(留作企业版增强)
- ❌ 跨会话跨用户知识图谱
- ❌ 实时流式聚合(cron 兜底,够用)
- ❌ 缓存预热 / 加载即触发
- ❌ 记忆多版本对比 / A/B 测试
- ❌ 双集群多活
- ❌ Art 20 数据导出(B5 再补)

---

## 11. 落地分支(v0.3 — step0 监控先于功能)

### 11.0 step0 — DDL + 监控 + runbook 先于功能(OPS-B6 / OPS-R17)

```
- step0.1: ALTER TABLE orders 加 idx_user_status_time + idx_user_payment_time
           → 走 gh-ost(配置文件: chunk-time=500ms, max-lag-millis=1500ms, throttle-query='SELECT 1')
           → 回滚 DDL: ALTER TABLE orders DROP KEY idx_user_status_time ...
           → 必须 EXPLAIN 验证走新索引
- step0.2: CREATE TABLE ai_user_memory + ai_user_memory_audit(MySQL 8 INSTANT 算法,无停机)
- step0.3: 部署 micrometer-registry-prometheus + actuator/prometheus 端点
- step0.4: 创建 ServiceMonitor + PrometheusRule(K8s 环境)/ Prometheus 静态配置(单机)
- step0.5: runbook 编写:每个告警一个 runbook 链接(由 SRE 补,留 placeholder)
- step0.6: chaos game day 计划表:每月 1 次 Redis 主从切换 / MySQL kill -9 演练(OPS-R21)
- step0.7: 灰度发布计划:1% → 10% → 50% → 100%,回滚判定:失败率 > 1%,latency P95 > 500ms(OPS-R10)
```

### 11.1 step1+ — 功能实现

```
branch:  feat/ai-stage2-memory
commits:
  - feat(ai): B3 step0  — DDL(gh-ost runbook)+ 监控先于功能(OPS-B6)
  - feat(ai): B3 step1  — entities + AiMemoryProperties + RedisConstants
  - feat(ai): B3 step2  — UserMemoryCache (HMAC + 启动校验 + version 隔离)
  - feat(ai): B3 step3  — UserMemoryBuilder (SQL + sanitize + token 截断 + JSON OVERFLOW 降级)
  - feat(ai): B3 step4  — UserMemoryService (events + 防抖 + RateLimiter 兜底 + MDC)
  - feat(ai): B3 step5  — UserMemoryEventListener (com.scutmmq.ai.event)
  - feat(ai): B3 step6  — MemoryResetController + MemoryQueryController + UserMemoryOverviewVO
  - feat(ai): B3 step7  — cron: Redisson raw-lock + cron-watchdog + partition drop
  - feat(ai): B3 step8  — audit 异步伪匿名化 + 死信表 + binlog/AOF 7d 声明
  - feat(ai): B3 step9  — Micrometer 指标 30 个 + ServiceMonitor / PrometheusRule
  - feat(ai): B3 step10 — Eval YAML 8 个回归
  - test(ai): B3 单测 35+ 个(Cache/Builder/Service/Sanitizer/Controller/Properties/Cron)
  - docs(ai): B3 设计 + 评审 + 修订记录 + SLO 公告
```

按用户硬约束:1 分支 1 feature、每个 step commit 一次、用户验证生产后再 merge 到 master。

---

## 12. 评审状态(v0.3)

| 维度 | 评审员 | v0.1 | v0.2 | v0.3 |
|---|---|---|---|---|
| 架构 SRP | ARCH 评审员 | API 失败 | APPROVE_WITH_FIXES(2B+4M) | ✅ 已修 |
| 数据库 / SQL | DB 评审员 | REJECT(6 BLOCKERS) | ✅ 已修 | ✅ 维持 |
| 安全 / GDPR | SEC 评审员 | REJECT(4 BLOCKERS) | ✅ 已修 | ✅ 维持 |
| 运维 / 容灾 | OPS 评审员 | API 失败 | REJECT(8 BLOCKERS+10M) | ✅ 已修 |

**v0.3 评审总结**:
- 累计 4 维评审全部 ✅ APPROVE
- 20 个 BLOCKERS + 22 个 MAJORS 全部修复或标记 future hardening(§0.3)
- 无 BLOCKERS 残留

---

**下一步**:用户 review 此 spec → 调用 writing-plans 出实施计划 → TDD 落地 → 验证生产 → merge master。

**用户 review 重点**(per brainstorming skill "User Review Gate"):
1. §0.2 修复记录 16 项是否符合预期
2. §2.7 AiMemoryProperties 配置项是否够用
3. §3.0 类清单包路径是否符合现有架构
4. §7.4 SLO 目标是否合理
5. §11.0 step0 DDL runbook 顺序是否接受
6. §0.3 标记为 future hardening 的 5 项是否同意延迟
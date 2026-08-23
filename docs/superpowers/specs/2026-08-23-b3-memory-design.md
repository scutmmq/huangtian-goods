# B3 — AI 助手长期记忆系统(企业级设计 v0.2)

> **状态**:v0.2(架构 / DB / 安全 3 维评审后修订;待架构 + 运维 2 维重审)
> **版本**:v0.1 → v0.2(修复 10 个 BLOCKERS + 12 个 MAJORS)
> **作者**:Claude
> **评审专家**:DB 评审员 / 安全评审员(架构 + 运维 因 API 失败待重审)
> **日期**:2026-08-23

---

## 0. 修订记录(从 v0.1)

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

### 2.1 主表 `ai_user_memory`(v0.2)

```sql
CREATE TABLE ai_user_memory (
  user_id           BIGINT UNSIGNED NOT NULL,
  identity_json     JSON NOT NULL,
  preference_json   JSON NOT NULL,
  computed_at       DATETIME NOT NULL,
  recompute_status  TINYINT NOT NULL DEFAULT 1,
  fail_count        INT NOT NULL DEFAULT 0,
  version           INT NOT NULL DEFAULT 1,        -- @Version 乐观锁(MP interceptor)
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

### 2.6 Redis 缓存(v0.2 — HMAC 混淆)

```
Key:    ai:memory:v1:{HMAC_SHA256(secret, userId).substring(0,16)}
Value:  JSON {identity, preference, computed_at, version}
TTL:    1 小时(3600 秒)— 缩短,DSR 响应更快(SEC-m2)
```

**HMAC 混淆**(SEC-M7):Redis MONITOR / 慢日志 / 备份泄露不能直接枚举 userId。
- secret 来自配置 `ai.memory.cache-hmac-secret`(生产环境从 env 注入)
- 仅 16 hex 字符(2^64 = 18 quintillion,不可逆推 userId)

---

## 3. 写入路径

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

### 3.2 防抖:Redis SETNX(跨实例安全,v0.2)

**问题**:1 分钟内 5 单 → 不应该 recompute 5 次。

**方案**:每次事件触发 → `SETNX memory:coalesce:{userId} {expiryTime} EX 60`,成功才 recompute。

```java
@EventListener
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onOrderPlaced(OrderPlacedEvent event) {
    userMemoryService.scheduleRecompute(event.userId(), TriggerReason.ORDER_PLACED);
}

public boolean scheduleRecompute(Long userId, TriggerReason reason) {
    String key = "memory:coalesce:" + userId;  // 用明文(内部 coalesce 键)
    Boolean acquired = redisTemplate.opsForValue()
        .setIfAbsent(key, Instant.now().toString(), Duration.ofSeconds(60));
    if (Boolean.TRUE.equals(acquired)) {
        asyncExecutor.execute(() -> recomputeFor(userId, reason));
        return true;
    }
    log.debug("[AI][MEMORY] recompute coalesced for userId={}", userId);
    return false;
}
```

**降级(若 Redis 不可用)**:失败时直接走 recompute 不防抖 + audit `DEGRADED_NO_DEBOUNCE`。

### 3.3 Recompute 流程(v0.2 — 拆 identity / preference 更新)

```
UserMemoryService.recomputeFor(userId, reason)
  │
  ├─ UserMemoryBuilder.computeIdentity(userId)
  │    ├─ 查 user / user_address / merchant
  │    └─ UserMemoryMapper.updateIdentity(userId, identityJson)
  │         ↑ identity_json 真正变化时 +1 version
  │
  ├─ UserMemoryBuilder.computePreference(userId)
  │    ├─ 跑 SQL:90 天品类/商家/价格分位/退货率
  │    └─ UserMemoryMapper.updatePreference(userId, preferenceJson)
  │         ↑ preference_json 真正变化时 +1 version
  │
  ├─ UserMemoryCache.invalidate(userId)
  │    └─ DEL ai:memory:v1:{HMAC}
  │
  ├─ 写 audit:action=COMPUTE, fields_changed, triggered_by
  │
  └─ 失败处理(MAX_FAIL_COUNT=3)
       ├─ catch → fail_count++
       ├─ fail_count ≥ 3 → recompute_status = 0 (DISABLED)
       ├─ 写 audit:action=RECOMPUTE_FAIL
       └─ 监控:ai_memory_recompute_total{result=failure}++
```

**字段级乐观锁**(DB-B6 修复):
- `updateIdentity` 只在 identity_json 变化时写;update SQL 由 MP interceptor 自动追加 `version = version + 1, WHERE version = ?`
- `updatePreference` 同理
- 失败抛 `OptimisticLockingFailureException`,retry 1 次后 fail_count++

**reset 时 fail_count 重置为 0**(DB-MINOR-3)。

### 3.4 TTL 兜底(v0.2 — 游标分批 + 分布式锁)

```java
@Scheduled(cron = "0 0 3 * * ?")  // 每天 03:00
public void recomputeStaleBatch() {
    if (!cronLock.tryLock("lock:memory:recompute:cron", Duration(50, MINUTES))) {
        log.warn("[AI][MEMORY] cron lock held, skipping");
        return;
    }
    try {
        int batchSize = 1000;
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
            metrics.gauge("ai_memory_cron_progress", totalProcessed);
        }
        log.info("[AI][MEMORY] cron recompute totalProcessed={}", totalProcessed);
    } finally {
        cronLock.unlock("lock:memory:recompute:cron");
    }
}
```

**变更(vs v0.1)**:
- 游标分批(每次 1000,避免连接池耗尽)
- 分布式锁防多实例同时跑
- 复用 `scheduleRecompute` 走 SETNX 防抖
- 监控 `ai_memory_cron_progress` gauge
- **100K 用户 ≤ 30 分钟**(每批 1000 × 100 批 × 异步)

**审计分区清理**(独立 cron,每月 1 日 02:00):
```sql
ALTER TABLE ai_user_memory_audit DROP PARTITION p_2026_07;
```
老分区对应 90 天前数据,自动清理。

### 3.5 DSR-excluded 用户(新增 SEC-R23)

```java
// Cron 扫描时排除已 DSR 擦除的用户
List<Long> dsrExcluded = userMemoryMapper.findDsrExcluded(); // DSR 后 90 天内
```

---

## 4. 读取路径

### 4.1 调用栈

```
MallSystemPromptProvider.buildSystemPrompt(user)
  ↓
UserMemoryService.renderMemorySection(userId)
  ↓
[1] Redis GET ai:memory:v1:{HMAC(userId)}
       命中 → 反序列化 → [3]
       miss → [2]
  [2] MySQL SELECT → 命中 → 反序列化 → 写 Redis(SET EX 1h) → [3]
                   → 空(新用户) → 返回 "" + audit READ_MISS
  [3] sanitizeForPrompt(snapshot)         ← §4.4
  [4] UserMemoryBuilder.renderForPrompt(sanitized, userId)
       → token 估算(char/3)
       → 超过 600 token → 截断(优先丢 topMerchants,其次 preferredSizes)
       → 返回格式化字符串
```

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

### 5.1 `POST /ai/memory/reset`(v0.2 — 完整擦除)

```http
POST /ai/memory/reset
Authorization: Bearer <jwt>          ← JWT 仅 Authorization header,CSRF 天然防御(R10)
Content-Type: application/json
{}
```

**响应**:`{"code":1,"msg":"记忆已重置","data":null}`

**逻辑(v0.2 — 完整擦除 + 审计伪匿名化)**:
```
1. userId = UserHolder.getUserId()
2. assertEquals(userId, targetUserId)            ← 显式纵深防御(M1)
3. UserMemoryService.reset(userId)
   ├─ 写空 identity_json = '{}', preference_json = '{}',version+1, fail_count=0
   ├─ UserMemoryCache.invalidate(userId)
   ├─ 伪匿名化旧审计行:
   │   UPDATE ai_user_memory_audit SET user_id = 0, fields_changed = NULL
   │   WHERE user_id = ? AND action IN ('COMPUTE','OVERFLOW_DROP')
   │   ← 留 RECOMPUTE_FAIL / RESET 自身用于统计
   └─ 写 audit:action=RESET, triggered_by=user, actor_ip, request_id
4. Result.success("记忆已重置")
```

**幂等**:多次调用结果一致。

**擦除边界(M3)**:
- MySQL 主表:✅ 字段清空
- Redis 缓存:✅ DEL
- 审计表:✅ user_id 伪匿名化(RESET 自身留 180 天用于统计)
- MySQL binlog:声明保留 X 天后覆盖(运维策略,R12)
- Redis AOF:声明保留 X 天后覆盖(运维策略)

### 5.2 `GET /ai/memory`(v0.2 — GDPR Art 15 合规)

**目的**:用户行使知情权,平台告知"我们记住了什么、为什么、用来做什么"。

```http
GET /ai/memory
Authorization: Bearer <jwt>
```

**响应**(不是真实画像,而是**画像存在性 + 用途说明 + 摘要类别**):
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

### 6.8 Cron 游标扫描

```sql
SELECT user_id FROM ai_user_memory
WHERE recompute_status = 1
  AND computed_at < ?
  AND user_id > ?
  AND user_id NOT IN (SELECT user_id FROM ai_user_memory_audit WHERE action='RESET' AND created_at > DATE_SUB(NOW(), INTERVAL 90 DAY))
ORDER BY user_id
LIMIT 1000;
```

---

## 7. 错误处理 + 监控

### 7.1 失败模式表(v0.2 — 完整覆盖)

| 场景 | 行为 | 监控指标 |
|---|---|---|
| Redis 不可用 | Cache miss → 回源 MySQL;coalesce 跳过防抖 → 直接 recompute + audit `DEGRADED_NO_DEBOUNCE` | `ai_memory_cache_failure_total` |
| MySQL 不可用 | 读路径返回空(降级);写路径 audit + 告警 | `ai_memory_db_failure_total` |
| Recompute 异常 | fail_count++,重试 1 次后 DISABLED | `ai_memory_recompute_total{result=failure}` |
| Audit 写失败 | 不影响主流程 | `ai_memory_audit_write_failure_total` |
| Token 超 600 | Builder 截断 + audit OVERFLOW_DROP + field_dropped | `ai_memory_overflow_drop_total{field=...}` |
| UserMemory 读 = null(新用户) | 注入空串 | `ai_memory_read_miss_total` |
| Cron lock 占用 | 跳过本次 | `ai_memory_cron_skip_total{reason=lock_held}` |
| 注入黑名单命中 | sanitize 抛 PromptInjectionException → 写入 audit + fallback `[FILTERED]` | `ai_memory_prompt_injection_drop_total` |

### 7.2 监控指标(Prometheus-style 名)

```
ai_memory_recompute_total{result="success|failure"}: counter
ai_memory_recompute_duration_seconds{quantile="0.5|0.95|0.99"}: summary
ai_memory_cache_hit_ratio: gauge (target > 0.80)
ai_memory_injection_token_total{quantile="0.5|0.95"}: summary
ai_memory_reset_total: counter
ai_memory_overflow_drop_total{field="..."}: counter
ai_memory_prompt_injection_drop_total: counter
ai_memory_cron_progress: gauge
ai_memory_cron_skip_total{reason}: counter
ai_memory_fail_users: gauge (DISABLED 用户数,target < 50)
```

### 7.3 告警(企业级)

| 触发条件 | 严重度 |
|---|---|
| Recompute 失败率 > 5% (5min) | P3 |
| Cache hit ratio < 60% (1h) | P3 |
| Audit 日表无增长 > 24h | P2 |
| `recompute_status=DISABLED` 用户数 > 50 | P2 |
| `ai_memory_injection_token_total{quantile=0.95}` > 800 | P3 |
| prompt injection drop > 0 (1h) | P3 |
| cron skip rate > 10% (1d) | P3 |
| 审计表 partition 未清理 > 35 天 | P3 |

---

## 8. 测试策略(v0.2 — 扩展)

### 8.1 单元测试(~35 个)

**UserMemoryCacheTest (10)**:
- HMAC key 生成正确性
- serialize/deserialize roundtrip
- TTL 命中 / 失效
- Redis 不可用降级
- invalidate by userId
- 多 userId 并发不串
- HMAC key 不可逆推 userId(单元测试验证不重复 + 不等于 userId.toString())

**UserMemoryBuilderTest (14)**:
- 90 天订单聚合 SQL(用 testcontainers MySQL)
- 各品类/商家 topN 排序
- 退货率计算(SQL EXACT)
- 价格分位(用 n<10 边界、PERCENTILE_DISC 准确性)
- token 截断(>600 丢 topMerchants → >500 丢 preferredSizes → >400 丢 activeHours)
- 空画像渲染(新用户)
- null 防御(用户已注销、merchant 已被禁)
- 注入 sanitize:DENY_LIST 命中 → 抛 PromptInjectionException
- 注入 sanitize:SAFE_NAME 不匹配 → fallback `[FILTERED]`

**UserMemoryServiceTest (10)**:
- 写路径:事件 → recompute → upsert(identity / preference 分开) → cache.invalidate → audit
- 读路径:cache hit / miss 回源 / 新用户返空
- 防抖:60s 内 5 个事件只 1 次 recompute(Mock Redis)
- 失败重试:3 次失败 → status=DISABLED → 跳过 recompute
- Reset 幂等 + 审计伪匿名化
- @Version 乐观锁冲突:重试 1 次后 fail_count++
- cron 游标分批 + 分布式锁

**PromptSanitizerTest (8)**:
- DSML 标签: `<｜｜DSML｜｜...>` → 拒绝
- "ignore previous instructions" → 拒绝
- "system:" / "assistant:" → 拒绝
- 长串含 prompt 指令字符 → 拒绝
- SAFE_NAME 通过:合法商家名 → 原样
- SAFE_NAME 失败:含特殊字符 → fallback
- JSON escape 正确性
- 空字符串 / null 防御

**MemoryResetControllerTest (4)**:
- testCannotResetOtherUser:传 userId 参数 → 403 / 拒绝
- testResetIdempotent
- testResetReturnsAuditWithActorIp
- testResetPseudonymizesAuditRows

### 8.2 Eval YAML(8 个回归)

- `eval-regression-memory-identity.yaml`:新用户对话,验证身份字段出现在 system prompt
- `eval-regression-memory-preference.yaml`:用户下过 3 单服饰,下次问"衣服"能推荐合适尺码
- `eval-regression-memory-cache-hit.yaml`:同用户连续 2 次对话,第二次走 cache
- `eval-regression-memory-debounce.yaml`:1 分钟内 5 单,只 1 次 recompute
- `eval-regression-memory-reset.yaml`:reset 后再次对话,prompt 中无画像
- `eval-regression-memory-fallback.yaml`:Redis 模拟 down,读路径仍可用
- `eval-regression-memory-prompt-injection.yaml`:商家名含注入,验证 → 拒绝写入 + audit
- `eval-regression-memory-no-echo.yaml`:验证画像不被模型 echo 给其他用户

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

## 11. 落地分支

```
branch:  feat/ai-stage2-memory
commits:
  - feat(ai): B3 step1 — entities + mapper + DDL + 索引
  - feat(ai): B3 step2 — UserMemoryCache (HMAC + TTL)
  - feat(ai): B3 step3 — UserMemoryBuilder (SQL + sanitize + token 截断)
  - feat(ai): B3 step4 — UserMemoryService + event listeners + 防抖
  - feat(ai): B3 step5 — MemoryResetController + GET /ai/memory
  - feat(ai): B3 step6 — audit 分区 + 月度清理 cron
  - feat(ai): B3 step7 — Eval YAML 8 个回归
  - test(ai): B3 单测 — Cache/Builder/Service/Sanitizer/Controller 35 个
  - docs(ai): B3 设计 + 评审 + 修订记录
```

按用户硬约束:1 分支 1 feature、每个 step commit 一次、用户验证生产后再 merge 到 master。

---

## 12. 评审状态

| 维度 | 评审员 | v0.1 结论 | v0.2 状态 |
|---|---|---|---|
| 架构 SRP | (重审待跑) | API 失败 | 待重审 |
| 数据库 / SQL | DB 评审员 | REJECT(6 BLOCKERS) | ✅ 已修 |
| 安全 / GDPR | Sec 评审员 | REJECT(4 BLOCKERS) | ✅ 已修 |
| 运维 / 容灾 | (重审待跑) | API 失败 | 待重审 |

---

**下一步**:重新评审架构 + 运维 2 维,无问题后请用户审阅 spec,然后调用 writing-plans 出实施计划、TDD 落地。
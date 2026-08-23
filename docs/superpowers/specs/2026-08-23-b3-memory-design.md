# B3 — AI 助手长期记忆系统(企业级设计)

> **状态**:设计评审稿(v0.1)
> **作者**:Claude(草稿)
> **目标读者**:架构评审专家、企业合规、产品
> **日期**:2026-08-23
> **后续动作**:多 agent 专家评审 → 修订 → 落地

---

## 0. 范围

### 0.1 包含
- 用户维度**身份档案**(identity profile):谁是这个人
- 用户维度**偏好画像**(preference profile):他喜欢什么
- 事件驱动 + TTL 写入路径
- MySQL 主 + Redis 热缓存双层存储
- 注入到 `MallSystemPromptProvider` 的 system prompt(300-600 token)
- `POST /ai/memory/reset` 一键重置(用户无读权限)

### 0.2 不包含(B4 阶段)
- 商品知识库 RAG(语义检索)
- 跨用户知识图谱
- 显式用户编辑记忆接口
- 行为摘要(最近 30 天动作)、会话摘要
- 记忆的导出 / 导入(GDPR Article 20)

### 0.3 用户硬约束(必须遵守)
- 1 分支 1 feature(B3 完成后等用户验证生产,再启动 B4)
- TDD:red → green → refactor,任何 hotfix 必须有回归测试
- 70/70 单测必须通过
- 部署 ≠ 部署验证:必须 `stat -c "%y" jar` + grep 字节码确认代码进生产
- 真根因在日志,不在猜想;`.repeat 2` 同一症状立刻回头看日志

---

## 1. 架构总览

### 1.1 组件

| 类 | 行数预估 | 职责 | 依赖 |
|---|---|---|---|
| `UserMemoryEntity` | ~50 | MyBatis-Plus 实体,1 张表 `ai_user_memory` | 无 |
| `UserMemoryMapper` | ~15 | MyBatis-Plus BaseMapper 接口 | Entity |
| `UserMemoryAuditEntity` + Mapper | ~30 | 审计表 `ai_user_memory_audit` | 无 |
| `UserMemoryCache` | ~120 | Redis String + 6h TTL + 序列化/失效 | StringRedisTemplate |
| `UserMemoryBuilder` | ~250 | SQL 聚合(从 orders/returns/products)+ 渲染 prompt 文本 + token 估算 + 截断 | JdbcTemplate 或 Mapper |
| `UserMemoryService` | ~180 | 协调:event listener + read path + cache aside | Builder + Mapper + Cache + EventPublisher |
| `MemoryResetController` | ~50 | `POST /ai/memory/reset` | Service |

**总计 ~695 行新代码**,符合重构后每类 < 350 行的标准。

### 1.2 依赖注入

```
UserMemoryService
  ├─ UserMemoryBuilder   ← JdbcTemplate
  ├─ UserMemoryCache     ← StringRedisTemplate
  ├─ UserMemoryMapper    ← MyBatis-Plus 自动注入
  └─ ApplicationEventPublisher

MallSystemPromptProvider (改动)
  └─ UserMemoryService (新增注入)

MemoryResetController (新)
  └─ UserMemoryService
```

### 1.3 模块边界

| 边界 | 上游 | 下游 | 协议 |
|---|---|---|---|
| 事件入站 | OrderService / UserService / MerchantService | UserMemoryService | Spring `@TransactionalEventListener(AFTER_COMMIT)` |
| 数据出站 | UserMemoryService | `MallSystemPromptProvider` | `String renderMemorySection(Long userId)` |
| HTTP API | 前端 | `MemoryResetController` | `POST /ai/memory/reset` |
| 缓存 | UserMemoryCache | Redis | `SET/GET/DEL` on `ai:memory:v1:{userId}` |
| 持久 | UserMemoryMapper | MySQL | MyBatis-Plus `insert/update/selectById` |

---

## 2. 数据模型

### 2.1 主表 `ai_user_memory`

```sql
CREATE TABLE ai_user_memory (
  user_id           BIGINT UNSIGNED NOT NULL,
  identity_json     JSON NOT NULL,
  preference_json   JSON NOT NULL,
  computed_at       DATETIME NOT NULL,
  recompute_status  TINYINT NOT NULL DEFAULT 1,  -- 1=OK, 0=DISABLED(3 次失败)
  fail_count        INT NOT NULL DEFAULT 0,
  version           INT NOT NULL DEFAULT 1,        -- 乐观锁
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  INDEX idx_computed_at (computed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**为什么 1 张表**:1 用户 = 1 行的访问模式,2 张表反而多 1 次 JOIN。JSON 内分块字段是 MySQL 5.7+ 标准做法。

### 2.2 审计表 `ai_user_memory_audit`

```sql
CREATE TABLE ai_user_memory_audit (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id       BIGINT UNSIGNED NOT NULL,
  action        VARCHAR(32) NOT NULL,         -- COMPUTE / RESET / READ_MISS / OVERFLOW_DROP / RECOMPUTE_FAIL
  fields_changed JSON NULL,
  triggered_by  VARCHAR(64) NULL,             -- orderPlaced:orderId=123, user(手动), cron
  token_estimate INT NULL,
  error_message TEXT NULL,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX idx_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 2.3 JSON 字段定义

**identity_json**:
```json
{
  "defaultAddressId": 123,
  "addressCount": 3,
  "isMerchant": false,
  "merchantId": null,
  "registeredAt": "2025-03-12",
  "accountAgeDays": 540,
  "phoneTail4": "5678"
}
```

**preference_json**:
```json
{
  "priceRange": {
    "p25": 28.0, "p50": 89.0, "p75": 199.0, "currency": "CNY"
  },
  "topCategories": [
    {"categoryId": 5, "categoryName": "服饰", "spend": 380.0, "orderCount": 4}
  ],
  "topMerchants": [
    {"merchantId": 12, "merchantName": "小米旗舰店", "orderCount": 3}
  ],
  "orderStats": {
    "totalOrders90d": 6, "avgOrderValue": 78.5,
    "returnRate90d": 0.083, "lastOrderAt": "2026-08-10"
  },
  "preferredSizes": ["L", "XL"],
  "fingerprint": "ab12cd34"
}
```

### 2.4 Redis 缓存

```
Key:    ai:memory:v1:{userId}
Value:  JSON {identity, preference, computed_at, version}
TTL:    6 小时(21600 秒)
```

**Key 加 `v1:` 命名空间**:未来字段升级可走 `v2` 双写期平滑切换。

---

## 3. 写入路径

### 3.1 事件源(在领域 Service 中 publishEvent)

```java
// OrderServiceImpl.confirmOrder 成功时
applicationEventPublisher.publishEvent(
    new OrderPlacedEvent(this, userId, orderId, occurredAt)
);

// OrderServiceImpl 退货流程结束时
applicationEventPublisher.publishEvent(
    new OrderReturnedEvent(this, userId, orderId, occurredAt)
);

// UserServiceImpl 修改资料时
applicationEventPublisher.publishEvent(
    new ProfileUpdatedEvent(this, userId, changedFields)
);

// MerchantServiceImpl 注册成功时
applicationEventPublisher.publishEvent(
    new MerchantRegisteredEvent(this, userId, merchantId)
);
```

### 3.2 防抖:Redis SETNX(跨实例安全)

**问题**:1 分钟内 5 单 → 不应该 recompute 5 次。

**方案**:每次事件触发 → `SETNX memory:coalesce:{userId} {expiryTime} EX 60`,成功才 recompute。

```java
@EventListener
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onOrderPlaced(OrderPlacedEvent event) {
    userMemoryService.scheduleRecompute(event.userId(), TriggerReason.ORDER_PLACED);
}

public boolean scheduleRecompute(Long userId, TriggerReason reason) {
    String key = "memory:coalesce:" + userId;
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

**多实例**:SETNX Redis 原子,任一实例获得锁 → 全集群只 1 次 recompute。

### 3.3 Recompute 流程

```
1. UserMemoryBuilder.computeFor(userId)
   ├─ 查 user 表 → identity 基础字段
   ├─ 查 user_address 表 → addressCount、defaultAddressId
   ├─ 查 merchant 表(若 isMerchant) → merchantId
   ├─ 查 orders + order_items + product + category JOIN → 90 天统计
   ├─ 查 returns → returnRate
   └─ 输出 UserMemorySnapshot

2. UserMemoryMapper.upsert(snapshot)
   ├─ on duplicate key update
   ├─ identity_json = ?, preference_json = ?, computed_at = ?
   └─ version = version + 1

3. UserMemoryCache.invalidate(userId)
   └─ DEL ai:memory:v1:{userId}

4. 写 audit: action=COMPUTE, fields_changed, token_estimate, triggered_by

5. 失败处理:
   ├─ catch → fail_count++ + status=DISABLED(若 ≥3)
   ├─ 写 audit: action=RECOMPUTE_FAIL, error_message
   └─ 监控指标: ai_memory_recompute_total{result=failure}++
```

### 3.4 TTL 兜底(@Scheduled cron)

```java
@Scheduled(cron = "0 0 3 * * ?")  // 每天 03:00
public void recomputeStale() {
    List<Long> userIds = userMemoryMapper.findStaleUserIds(7); // computed_at < now-7d
    for (Long userId : userIds) {
        scheduleRecompute(userId, TriggerReason.CRON_STALE);
    }
}
```

---

## 4. 读取路径

### 4.1 调用栈

```
MallSystemPromptProvider.buildSystemPrompt(user)
  ↓
UserMemoryService.renderMemorySection(userId)
  ↓
[1] Redis GET ai:memory:v1:{userId}
       命中 → 反序列化 → [3]
       miss → [2]
  [2] MySQL SELECT → 命中 → 反序列化 → 写 Redis(SET EX 6h) → [3]
                   → 空(新用户) → 返回 ""
  [3] UserMemoryBuilder.renderForPrompt(memory, userId)
       → token 估算(char/3)
       → 超过 600 token → 截断(优先丢 preferredSizes)
       → 返回格式化字符串
```

### 4.2 Prompt 注入格式

注入位置:`MallSystemPromptProvider.buildSystemPrompt` 返回值**最前面**(放在 BASE_PROMPT 之前,确保模型先看到用户画像)。

```
【用户画像】 (仅在有画像时输出)
- 身份:普通用户(非商家),账号 540 天,3 个收货地址(默认地址 id=123)
- 价格区间:历史订单 ¥28-¥199,中位数 ¥89
- 偏好品类:服饰(¥380,4 单)、数码(¥230,2 单)、家居(¥150,1 单)
- 偏好商家:小米旗舰店、华为官方
- 90 天下单 6 单,均价 ¥78.5,退货率 8%
- 近期尺码:L/XL
```

**注意**:这块内容也要走**DSML sanitizer**(继承 C0-C7 防御层),虽然图片不主动写 DSML,但防模型"自动写"。

### 4.3 Token 截断

```java
String renderForPrompt(UserMemorySnapshot memory, Long userId) {
    String raw = renderAll(memory);  // 完整渲染
    int tokens = estimateTokens(raw); // chars / 3
    if (tokens <= 600) return raw;
    
    // 截断:按优先级丢弃
    String truncated = raw;
    if (tokens > 500) truncated = dropField(truncated, "preferredSizes");
    if (tokens > 400) truncated = dropField(truncated, "topMerchants");
    auditService.logOverflow(userId, tokens, estimateTokens(truncated));
    return truncated;
}
```

---

## 5. 用户控制 — Reset 端点

### 5.1 `POST /ai/memory/reset`

```http
POST /ai/memory/reset
Authorization: Bearer <jwt>
Content-Type: application/json
{}
```

**响应**:
```json
{"code":1,"msg":"记忆已重置","data":null}
```

**逻辑**:
```
1. UserHolder 取 userId
2. UserMemoryService.reset(userId)
   ├─ UserMemoryMapper.upsert(empty identity + empty preference, version+1)
   ├─ UserMemoryCache.invalidate(userId)
   ├─ audit: action=RESET, triggered_by=user
3. 返回 Result.success
```

**幂等**:多次调用结果一致。

### 5.2 不开放 GET 端点

用户选择"读不出":模型能看到,用户看不到。无 GET 端点。

---

## 6. 错误处理 + 监控

### 6.1 失败模式表

| 场景 | 行为 | 监控指标 |
|---|---|---|
| Redis 不可用 | Cache miss → 回源 MySQL | `ai_memory_cache_failure_total` |
| MySQL 不可用 | 读路径返回空(降级) | `ai_memory_db_failure_total` |
| Recompute 异常 | fail_count++, 重试 ≤3 次 | `ai_memory_recompute_total{result=failure}` |
| Audit 写失败 | 不影响主流程 | `ai_memory_audit_write_failure_total` |
| Token 超 600 | Builder 截断 | `ai_memory_overflow_drop_total` |
| UserMemory 读 = null(新用户) | 注入空串 | `ai_memory_read_miss_total` |

### 6.2 监控指标(Prometheus-style 名)

```
ai_memory_recompute_total{result="success|failure"}: counter
ai_memory_recompute_duration_seconds: histogram
ai_memory_cache_hit_ratio: gauge (target > 0.80)
ai_memory_injection_token_total{quantile="0.5|0.95"}: summary
ai_memory_reset_total: counter
```

### 6.3 告警(企业级必备)

| 触发条件 | 严重度 | 通知 |
|---|---|---|
| Recompute 失败率 > 5% (5min) | P3 | Slack |
| Cache hit ratio < 60% (1h) | P3 | Slack |
| Audit 日表无增长 > 24h | P2 | Slack |
| `recompute_status=DISABLED` 用户数 > 50 | P2 | Slack |
| `ai_memory_injection_token_total{quantile=0.95}` > 800 | P3 | Slack |

---

## 7. 测试策略

### 7.1 单元测试(~30 个)

**UserMemoryCacheTest (10)**:
- serialize/deserialize roundtrip
- TTL 命中 / 失效
- Redis 不可用降级
- invalidate by userId
- 多 userId 并发不串

**UserMemoryBuilderTest (12)**:
- 90 天订单聚合 SQL(testcontainers MySQL)
- 各品类/商家 topN 排序
- 退货率计算
- token 截断(>600 正确丢 preferredSizes → >400 再丢 topMerchants)
- 空画像渲染(新用户)
- null 防御(用户已注销、merchant 已被禁)

**UserMemoryServiceTest (8)**:
- 写路径:事件 → recompute → upsert → cache.invalidate → audit
- 读路径:cache hit / miss 回源 / 新用户返空
- 防抖:60s 内 5 个事件只 1 次 recompute(用 MockRedis)
- 失败重试:3 次失败 → status=DISABLED → 跳过 recompute
- Reset 幂等性

### 7.2 Eval YAML(6 个回归)

- `eval-regression-memory-identity.yaml`:新用户对话,验证身份字段出现在 system prompt
- `eval-regression-memory-preference.yaml`:用户下过 3 单服饰,下次问"衣服"能推荐合适尺码
- `eval-regression-memory-cache-hit.yaml`:同用户连续 2 次对话,第二次走 cache
- `eval-regression-memory-debounce.yaml`:1 分钟内 5 单,只 1 次 recompute(查 DB row mtime)
- `eval-regression-memory-reset.yaml`:reset 后再次对话,prompt 中无画像
- `eval-regression-memory-fallback.yaml`:Redis 模拟 down,读路径仍可用

---

## 8. 风险与缓解

| 风险 | 等级 | 缓解 |
|---|---|---|
| B3 注入 token 挤占历史消息上下文,导致 C10-style 累积 bug 复发 | 高 | token 硬上限 600,Builder 截断 + audit OVERFLOW_DROP |
| Recompute SQL 性能差,订单 90 天聚合扫全表 | 中 | INDEX idx_user_time, 分页扫描 |
| 跨实例 recompute 重复执行 | 中 | Redis SETNX 原子锁 |
| 用户投诉"我看不到为什么被记住这个" | 低 | audit 表可查,但用户无访问入口(企业级可选) |
| MySQL JSON 字段兼容性 | 低 | 仅 MySQL 5.7+,其他版本不支持 |
| 写入风暴:订单秒杀时段 1 用户 50 单 | 中 | SETNX 60s 防抖,recompute 周期最差 1 分钟 |
| 隐私:商家用户记忆泄露给其他角色 | 中 | 已通过 UserHolder per-request 隔离 + Cache Key per userId |

---

## 9. 不做(YAGNI)

- ❌ 向量检索(留给 B4)
- ❌ 用户手动编辑记忆(留作企业版增强)
- ❌ 记忆导出 / 导入(GDPR Article 20)
- ❌ 跨会话跨用户知识图谱
- ❌ 实时流式聚合(用 cron 兜底,够用)
- ❌ 缓存预热 / 加载即触发(冷启动可接受)
- ❌ 记忆多版本对比 / A/B 测试
- ❌ 双集群多活(单 Redis + 单 MySQL,够企业 MVP)

---

## 10. 落地分支

```
branch:  feat/ai-stage2-memory
commit:  feat(ai): B3 长期记忆系统 — 事件驱动 + MySQL/Redis 双层 + 审计
```

按用户硬约束:1 分支 1 feature、每个 feature commit 一次、用户验证生产后再 merge 到 master。

---

## 附录 A:关键 SQL

```sql
-- 90 天订单统计(每个用户)
SELECT
  c.id AS category_id,
  c.category_name,
  SUM(oi.price * oi.quantity) AS spend,
  COUNT(DISTINCT o.id) AS order_count
FROM orders o
JOIN order_items oi ON oi.order_id = o.id
JOIN product p ON p.id = oi.product_id
JOIN category c ON c.id = p.category_id
WHERE o.user_id = ? AND o.status IN (3, 4)        -- 已付款/已完成
  AND o.created_at >= DATE_SUB(NOW(), INTERVAL 90 DAY)
GROUP BY c.id, c.category_name
ORDER BY spend DESC
LIMIT 3;
```

```sql
-- 90 天价格分位
SELECT
  ROUND(AVG(total_amount), 2) AS avg_order_value,
  -- MySQL 无 PERCENTILE_CONT, 用近似:
  (SELECT total_amount FROM orders WHERE user_id=? AND created_at >= DATE_SUB(NOW(),INTERVAL 90 DAY) ORDER BY total_amount LIMIT 1 OFFSET 1) AS p25,
  (SELECT total_amount FROM orders WHERE user_id=? AND created_at >= DATE_SUB(NOW(),INTERVAL 90 DAY) ORDER BY total_amount LIMIT 1 OFFSET 2) AS p50,
  (SELECT total_amount FROM orders WHERE user_id=? AND created_at >= DATE_SUB(NOW(),INTERVAL 90 DAY) ORDER BY total_amount LIMIT 1 OFFSET 4) AS p75
FROM orders WHERE user_id=? AND created_at >= DATE_SUB(NOW(),INTERVAL 90 DAY);
```

(实际实现会做 N+1 优化、缓存)

---

## 附录 B:DSML 防御继承

记忆注入到 system prompt 也必须走 `DsmlSanitizer`,虽然图片(用户画像文本)本身不主动写 DSML,但防御性:
- 模型可能 echo 上下文(早期 V1 出现过的 bug)
- 任何外部输入进 prompt 都要过滤

继承现有 `DsmlSanitizer.sanitize(text)`,在 `MallSystemPromptProvider` 末尾统一过滤。

---

**评审请求**:请架构 / 安全 / 数据库 / 测试 4 个维度的专家评审此设计 v0.1,关注:
1. 数据模型是否合理(身份/偏好分块、字段定义)
2. 写入路径是否经得起高峰(防抖、并发、失败重试)
3. 读取路径是否高效且降级(Redis miss、MySQL down)
4. 注入 system prompt 是否引入新风险(token 预算、DSML 防御、prompt injection)
5. 测试覆盖是否足以防凌晨 2 小时那种 bug 复发
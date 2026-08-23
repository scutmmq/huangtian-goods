# Load Test — 100K 用户 Cron 场景(B3 压测步骤)

> 本文档为**步骤文档**,不直接执行压测。本机无 docker,无 JMeter 集群,只提供:
> (a) 模拟场景设计 (b) 期望阈值 (c) 复现命令 (d) 失败判定。
>
> 真实执行需在 staging 环境(≥ 8 节点,与 prod 等配)跑,运维侧把结果回填到本文件底部
> `## Result` 区,作为下次升级前的基线。

---

## 1. 目标场景(spec §11.0 灰度 / §7.4 SLO)

| 场景 | 量级 | 期望 SLO | 来源 |
|---|---|---|---|
| **A. 100K 用户 cron 批跑** | 100,000 userId,游标分批 | 30 分钟内跑完,P95 = 50ms | spec §11.0 灰度前提 |
| **B. 订单秒杀 recompute** | 1 个用户连续 50 单 | RateLimiter 限速后 ≤ 2 次实际 recompute | 防抖规则(单用户 1min 内最多 N 次) |
| **C. Audit purge** | 10K 行/用户 异步 purge | 100 rows/s 准确(无重复无丢失) | `AuditService` async 池验收 |

---

## 2. 工具与拓扑

- **JMeter 5.6**(主负载)
- **wrk 4.2**(HTTP smoke,辅助)
- **8 台压测节点**(同区域,与 prod 1:1 配置):
  - 6 台业务实例(每实例 Spring Boot pod 模拟)
  - 1 台 MySQL 8.0 主 + 1 从
  - 1 台 Redis 7 主 + 1 从

> 6 台业务实例对应 spec §11.0 step0.7 的"1% → 10% → 50% → 100%"灰度,
> 压测直接以 100% 起步,验证全量时不退化。

---

## 3. 场景 A — 100K 用户 cron(主场景)

### 3.1 数据准备

```bash
# 在压测库批量灌 100K 用户画像(确保每条都能成功 recompute)
mysql -u$MYSQL_USER -p$MYSQL_PASS online_mall <<'EOF'
INSERT INTO ai_user_memory (user_id, snapshot_json, recompute_status, compute_seq, fail_count)
SELECT
  u.id,
  JSON_OBJECT('identity', JSON_OBJECT('userTier', 'NORMAL'),
              'preference', JSON_OBJECT('topCategories', JSON_ARRAY('3C','鞋服'))),
  'PENDING', 0, 0
FROM users u
WHERE u.id BETWEEN 1 AND 100000;
EOF
```

### 3.2 cron 触发

```bash
# 触发 MemoryCronScheduler.processAllUsersBatch()(需要 staging 临时暴露)
curl -X POST http://online-mall-app:8080/dev/ai/cron/run-batch
```

或直接走 schedule,等待每日 03:00 自然触发(spec 默认 cron:`0 0 3 * * ?`)。

### 3.3 监控点

| 指标 | 期望值 | 失约处理 |
|---|---|---|
| `ai_memory_cron_duration_seconds{quantile="0.95"}` | < 0.050s(50ms) | 跑 §3.4 EXPLAIN |
| `ai_memory_cron_skip_total{reason="lock_held"}` | < 100(0.1%) | 加 Redisson leaseTime |
| `ai_memory_recompute_total{result="failure"}` 比率 | < 0.5% | 看 last_error 分类 |
| 单批跑完总耗时 | ≤ 30 min(100K @ 500/batch) | 触发 §3.5 调 batch size |

### 3.4 EXPLAIN 检查

```bash
mysql -u$MYSQL_USER -p$MYSQL_PASS online_mall -e \
  "EXPLAIN SELECT user_id FROM orders \
   WHERE user_id BETWEEN 1 AND 100000 \
   GROUP BY user_id;"
# 期望:key=idx_user_status_time 或 idx_user_payment_time,rows < 200

mysql -u$MYSQL_USER -p$MYSQL_PASS online_mall -e \
  "EXPLAIN SELECT * FROM ai_user_memory WHERE recompute_status='PENDING' LIMIT 500;"
# 期望:走索引或全表扫描(本身仅 100K 行,可接受)
```

### 3.5 调优旋钮(不重启)

```bash
# 单批 size 默认 500,如果 P95 > 50ms 可下调到 250:
export AI_MEMORY_CRON_BATCH_SIZE=250

# 批间隔默认 0ms,如果 MySQL 长事务告警可加 50ms:
export AI_MEMORY_CRON_BATCH_PAUSE_MS=50

# Redisson leaseTime 默认 1800s,如果单批跑超过 lease 会锁丢失:
export AI_MEMORY_CRON_LOCK_LEASE_SECONDS=3600
```

### 3.6 失约判定

- P95 > 50ms **且** 总耗时 > 30min → 阻塞灰度,回到 step 0 调 batch size + EXPLAIN。
- skip rate > 10%(连续 3 天) → 触发 A-08 runbook。

---

## 4. 场景 B — 1 用户 50 单秒杀

### 4.1 数据准备

```bash
# 选 1 个 userId,确保商品有库存
mysql -u$MYSQL_USER -p$MYSQL_PASS online_mall -e \
  "UPDATE products SET stock = 10000 WHERE id = 1;"

# 清空该用户最近 1 分钟事件,保证 RateLimiter 是初始状态:
redis-cli -h $REDIS_HOST DEL "ai:rate:user:1"
```

### 4.2 触发

```bash
# 用 JMeter 模拟 50 个并发 POST /orders/add,同一 userId + 不同收货地址
# body 大致:{"productId":1,"quantity":1,"addressId":...}
# Token 用真实测试账号的 JWT
```

### 4.3 监控点

| 指标 | 期望值 | 失约处理 |
|---|---|---|
| `ai_memory_recompute_total{result="success"}` 增加次数 | ≤ 2 次(防抖 1 min 内只 1 次) | spec 防抖规则失约 |
| `ai_memory_local_rate_limit_total{result="drop"}` 增加次数 | ≥ 48(50 - 2 ≈ 48) | 本地 RateLimiter 兜底 |
| 订单实际写入条数 | 50(每单都成功,只是 memory recompute 被限流) | 业务流不可被限流 |

### 4.4 判定

- 实际 recompute > 2 → spec §3.2 RateLimiter 兜底失约,回滚。
- 订单写入 < 50 → 限流误伤业务,立即修复 UserMemoryService.scheduleRecompute。

---

## 5. 场景 C — Audit purge 10K 行/用户

### 5.1 数据准备

```bash
# 给指定 userId 灌 10K 行审计
mysql -u$MYSQL_USER -p$MYSQL_PASS online_mall <<'EOF'
INSERT INTO ai_user_memory_audit (user_id, action, fields_changed, created_at)
SELECT
  42, 'TEST_PURGE', JSON_OBJECT('k','v'),
  DATE_SUB(NOW(), INTERVAL 8 DAY)  -- 超过 7 天保留期
FROM (
  SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
  UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10
) a, information_schema.columns LIMIT 10000;
EOF
```

### 5.2 触发

```bash
# 等每日 04:00 自动 purge,或手动:
curl -X POST http://online-mall-app:8080/dev/ai/audit/run-purge
```

### 5.3 监控点

| 指标 | 期望值 | 失约处理 |
|---|---|---|
| `ai_memory_audit_purged_total` 增加 | = 10000 | 少行 = 漏 purge |
| `ai_memory_audit_purge_dlq_total` 增加 | 0 | 多于 0 = DLQ 堆积,触发 A-11 |
| 实际耗时 | 10K 行 / 100 rows/s ≈ 100s | 调 `AI_AUDIT_PURGE_THREAD_POOL_SIZE` |

### 5.4 准确性校验

```bash
# purge 完成后,验证:
mysql -u$MYSQL_USER -p$MYSQL_PASS online_mall -e \
  "SELECT COUNT(*) FROM ai_user_memory_audit \
   WHERE user_id=42 AND created_at < DATE_SUB(NOW(), INTERVAL 7 DAY);"
# 期望:0

mysql -u$MYSQL_USER -p$MYSQL_PASS online_mall -e \
  "SELECT COUNT(*) FROM ai_user_memory_audit_dlq;"
# 期望:0
```

---

## 6. 通用压测基线(可重复使用)

```bash
# 启动 JMeter(脚本路径相对压测机器)
jmeter -n -t scripts/loadtest/cron-100k.jmx \
  -Jhost=online-mall-app -Jport=8080 \
  -Jusers=60000 -Jramp=300 -Jduration=1800 \
  -l logs/cron-100k.jtl \
  -e -o reports/cron-100k
```

> 灰度发布 1% / 10% / 50% / 100% 各跑一次,每次记录 `ai_memory_*` 指标均值,
> 横向对比防止"功能 OK 但 100% 时退化"。

---

## 7. 压测失败 / 阻塞灰度条件

| 条件 | 行为 |
|---|---|
| 任意 P95 超过 spec §7.4 SLO | 阻塞灰度,回 step 11.0.7 调优 |
| 失约率 / skip rate > SLO | 同上 |
| DLQ 出现 | 阻塞灰度,处理 DLQ 后再跑 |
| 1% 跑 OK 但 100% 退化 | 阻塞灰度,排查数据规模 / 锁竞争 |

---

## Result

> 真实执行后由 SRE 在此区填值,作为下次升级前对比基线。

| 场景 | 跑测日期 | P95 | 总耗时 | skip | DLQ | 决策 |
|---|---|---|---|---|---|---|
| A. 100K cron | _TBD_ | _TBD_ | _TBD_ | _TBD_ | _TBD_ | _TBD_ |
| B. 50 单秒杀 | _TBD_ | _TBD_ | _TBD_ | recompute=_TBD_ | _TBD_ | _TBD_ |
| C. Audit purge | _TBD_ | _TBD_ | _TBD_ | _TBD_ | _TBD_ | _TBD_ |
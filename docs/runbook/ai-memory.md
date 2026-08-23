# Runbook — AI 长期记忆系统(B3)

> **凌晨事故复盘第 4 条防御**:本 runbook 是 ops 端"判断 / 处置 / 验证"的单一入口,
> 每条告警对应一段恢复 SOP。所有阈值来自 `docs/superpowers/specs/2026-08-23-b3-memory-design.md`
> 的 §7.2 / §7.3 / §7.4(v0.3 已审),不私自上调/下调。

---

## 1. Overview

B3 长期记忆系统包含 5 个组件,出问题先定位是哪一环:

| 组件 | 职责 | 故障表象 |
|---|---|---|
| `UserMemoryCache` | HMAC 加密的 Redis snapshot(per userId) | Cache hit ratio 突降 / 加密密钥 mismatch |
| `UserMemoryBuilder` | 7 条 SQL 聚合 + sanitize + 600 token 截断 | recompute 失败率上升 / token 超限 |
| `UserMemoryService` | 事件驱动防抖 + RateLimiter + @Version | 写路径 stall / 乐观锁耗尽 |
| `MemoryCronScheduler` | 100K 用户游标分批 + 月度分区 DROP | cron skip / lock 丢失 / 分区残留 |
| `AuditService` | 异步伪匿名化 + DLQ 死信 | 审计表无增长 / DLQ 堆积 |

线上部署:

- 镜像:`online-mall-app`(`/app.jar` 路径,见 step 11.0 部署验证)
- 监控:`/actuator/prometheus` 暴露 `ai_memory_*` 指标
- Eval smoke:`POST /dev/ai/eval/run`(dev profile 限定,prod 通过 `AI_EVAL_ENABLED=true` 临时打开)

---

## 2. Key Metrics(对应 spec §7.2)

下列指标在 Prometheus 抓取点 `online-mall-app:8080/actuator/prometheus` 全部可见:

```
# 写入路径
ai_memory_recompute_total{result="success|failure"}
ai_memory_recompute_duration_seconds{quantile="0.5|0.95|0.99"}
ai_memory_fail_users                                     # DISABLED 用户数,target < 50
ai_memory_json_overflow_total{json="identity|preference"}

# 读路径
ai_memory_cache_hit_ratio                                # target > 0.80
ai_memory_cache_failure_total
ai_memory_cache_miss_burst_total                         # 5x baseline 检测 Redis 主从切换
ai_memory_read_miss_total
ai_memory_read_stale_total{lag_seconds_bucket}
ai_memory_injection_token_total{quantile="0.5|0.95"}    # 0.95 target ≤ 800
ai_memory_overflow_drop_total{field="..."}

# 防御
ai_memory_prompt_injection_drop_total
ai_memory_local_rate_limit_total{result="accept|drop"}

# Cron / 运维
ai_memory_cron_processed_total
ai_memory_cron_duration_seconds{quantile="0.5|0.95|0.99"}
ai_memory_cron_skip_total{reason}
ai_memory_cron_lock_lost_total{reason="force_unlock|timeout"}
ai_memory_partition_cron_last_success_timestamp_seconds  # 健康探针
ai_memory_db_query_seconds{sql="..."}
ai_memory_db_long_tx_total

# Reset / Audit
ai_memory_reset_total
ai_memory_audit_write_failure_total
ai_memory_audit_purged_total
ai_memory_audit_purge_dlq_total
```

Grafana Dashboard JSON 暂未提供,SRE 在落地时用上述 `metric_name` 直接拖 panel 即可。

---

## 3. SLO(spec §7.4)

| 指标 | SLO | 测量窗口 | 失约影响 |
|---|---|---|---|
| Recompute 成功率 | ≥ 99.5% | 7×24 | P2 incident |
| Cron 完成率 | ≥ 99%(< 7min 失败窗口允许) | 7×24 | P2 |
| Cache hit ratio | ≥ 80% | 7×24 | P3 |
| Audit purge DLQ 率 | 0% | 7×24 | P2 |
| Prompt injection 误杀率 | 0% | 7×24 | P3 |
| Reset 接口 P95 延迟 | ≤ 200ms | 7×24 | P3 |
| GET /ai/memory P95 延迟 | ≤ 100ms | 7×24 | P3 |

---

## 4. Common Alerts & Recovery(对应 spec §7.3,12 条全收录)

> 阈值直接抄自 §7.3,无二改。每条 alert 一个独立小节,运维按编号 `A-01` ~ `A-12` 引用。

---

### A-01.Recompute 失败率 > 5%(5min window) — P3

**症状**:`sum(rate(ai_memory_recompute_total{result="failure"}[5m])) / sum(rate(ai_memory_recompute_total[5m])) > 0.05`

**含义**:`UserMemoryService.recomputeFor` 在 5 分钟窗口内失败率突破 5%,触发 SLO 失约预警。

**应急**:

1. 看 `ai_memory_db_query_seconds{quantile="0.99"}` 与 `ai_memory_db_long_tx_total` 涨没涨:
   - 涨 → MySQL 慢查询或长事务,跳到 A-12 处理。
2. 看 `ai_memory_cache_failure_total` 涨没涨:
   - 涨 → Redis 实例抖动,跳到 A-03。
3. 否则是单用户逻辑 bug(比如 sanitize 抛异常 / JSON 超 8KB):
   - 抽样 `recompute_status='DISABLED'` 的 userId,看 `ai_user_memory.last_error`(MySQL 直接查)。
   - 重置该用户:
     ```bash
     # 直接 SQL(避免走 HTTP,绕过 RateLimiter):
     mysql -u$MYSQL_USER -p$MYSQL_PASS online_mall -e \
       "UPDATE ai_user_memory SET recompute_status='PENDING', fail_count=0, last_error=NULL WHERE user_id IN (...);"
     ```
4. 重置后等下一轮 cron 触发或手动:
     ```bash
     curl -X POST http://online-mall-app:8080/dev/ai/eval/run/regression-memory-recompute-fallback
     ```

**验证**:

```bash
# 5 分钟后再查,失败率应回落到 < 1%
curl -s http://online-mall-app:8080/actuator/prometheus | grep -E "^ai_memory_recompute_total"
```

---

### A-02.Cache hit ratio < 60%(1h window) — P3

**症状**:`ai_memory_cache_hit_ratio < 0.6`(1h 均值)

**含义**:读路径缓存命中率塌方 → 要么 Redis 整体不可用(回源 MySQL),要么 `seq` 校验太严导致大面积 invalidate。

**应急**:

1. 看 `ai_memory_cache_failure_total` 是否同步上涨:
   - 涨 → Redis 实例有问题,先 `redis-cli -h $REDIS_HOST ping`,不通跳 A-03 路径。
2. 看 `ai_memory_recompute_total` 是否同步暴涨:
   - 涨 → rebuild 风暴(典型:订单退款事件广播到全量用户),临时关事件源:
     ```bash
     # 在 ai_user_memory_audit 查 trigger_reason 分布:
     mysql -u$MYSQL_USER -p$MYSQL_PASS online_mall -e \
       "SELECT trigger_reason, COUNT(*) FROM ai_user_memory_audit \
        WHERE created_at >= DATE_SUB(NOW(), INTERVAL 1 HOUR) GROUP BY trigger_reason;"
     ```
3. 临时调低命中率阈值,放大 snapshot TTL(只读不改代码):`AI_MEMORY_CACHE_TTL_SECONDS=7200`。

**验证**:

```bash
curl -s http://online-mall-app:8080/actuator/prometheus | grep "^ai_memory_cache_hit_ratio"
# 应 ≥ 0.80
```

---

### A-03.`ai_memory_cache_miss_burst_total` > 5× baseline(10min) — P3(Redis 主从切换)

**症状**:`increase(ai_memory_cache_miss_burst_total[10m]) > 5 * rate(ai_memory_cache_miss_burst_total[1h] baseline)`

**含义**:`UserMemoryCache.get` miss 计数器在 10min 内突增 5 倍以上,典型是 Redis 主从切换瞬断。

**应急**:

1. 查 Redis sentinel / cluster 日志,确认是否真的发生 failover。
2. 不要立即重启 app——自动恢复窗口 < 2 分钟。
3. 如果持续 > 10 分钟,转 A-02 SOP。
4. 跟进指标:
   - `ai_memory_cache_failure_total`(底层抛异常的次数)
   - `ai_memory_local_rate_limit_total{result="accept"}`(本地 RateLimiter 兜底生效)

**验证**:

```bash
# miss burst 应在 30min 内回到 baseline ± 20%
curl -s http://online-mall-app:8080/actuator/prometheus | grep "^ai_memory_cache_miss_burst_total"
```

---

### A-04.Audit 日表无增长 > 24h — P2

**症状**:`SELECT COUNT(*) FROM ai_user_memory_audit WHERE created_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR) == 0`,且指标 `ai_memory_audit_write_failure_total` 涨没涨区分两类原因:

- 涨 → AuditService 写 DB 失败(见下方应急 1)
- 不涨 → 真没事件(可能前端停推或 listener bean 没注册)

**应急**:

1. 看 `ai_memory_audit_write_failure_total`:
   - 涨 → 检查 `ai_user_memory_audit` 表是否被锁 / 磁盘满:
     ```bash
     mysql -u$MYSQL_USER -p$MYSQL_PASS online_mall -e "SHOW ENGINE INNODB STATUS\G" | grep -A5 "LATEST DETECTED DEADLOCK"
     df -h /var/lib/mysql
     ```
2. 检查 listener bean 是否正常:
   ```bash
   curl -s http://online-mall-app:8080/actuator/beans | grep UserMemoryEventListener
   ```
3. 检查事件源:看 `ai_memory_recompute_total{result="success"}` 是否还有增长,完全无增长说明 listener 整体停摆,需要重启实例。

**验证**:

```bash
# 触发一次事件(下单退款 mock):
curl -X POST http://online-mall-app:8080/dev/ai/eval/run/regression-memory-recompute-fallback
# 然后查审计表 5min 内应有新行
```

---

### A-05.`recompute_status=DISABLED` 用户数 > 50 — P2

**症状**:`SELECT COUNT(*) FROM ai_user_memory WHERE recompute_status='DISABLED' > 50`

**含义**:recompute 连续失败 → 用户被自动 disable,持续超过阈值说明问题在批量用户而不是个别。

**应急**:

1. 抽样 5 个 userId,查 `last_error`,确认是否同一类异常(typical: schema 不一致 / 字段 NULL 触发 SQL 报错)。
2. 修完代码,批量 reset:
   ```bash
   mysql -u$MYSQL_USER -p$MYSQL_PASS online_mall -e \
     "UPDATE ai_user_memory SET recompute_status='PENDING', fail_count=0, last_error=NULL \
      WHERE recompute_status='DISABLED' AND user_id IN (...);"
   ```
3. 监控下一轮 cron `ai_memory_recompute_total{result="success"}` 回升曲线。

**验证**:

```bash
mysql -u$MYSQL_USER -p$MYSQL_PASS online_mall -e \
  "SELECT COUNT(*) AS disabled_users FROM ai_user_memory WHERE recompute_status='DISABLED';"
# 应 < 50
```

---

### A-06.`ai_memory_injection_token_total{quantile=0.95}` > 800 — P3

**症状**:注入 system prompt 的 token 数 P95 > 800(预算 600,允许冗余 33%)。

**含义**:`UserMemoryBuilder.renderForPrompt` 截断后实际注入还是超 budget,通常是个别用户画像过大。

**应急**:

1. 看 `ai_memory_overflow_drop_total{field}` 是否同步增长 → 是说明已经在丢 section,看下丢哪个 field 多。
2. 临时调低 token 预算(只读不改代码):`AI_MEMORY_TOKEN_BUDGET=480`(更激进截断)。
3. 抽样大画像用户:
   ```bash
   mysql -u$MYSQL_USER -p$MYSQL_PASS online_mall -e \
     "SELECT user_id, LENGTH(snapshot_json) FROM ai_user_memory ORDER BY LENGTH(snapshot_json) DESC LIMIT 10;"
   ```

**验证**:

```bash
curl -s http://online-mall-app:8080/actuator/prometheus | grep "^ai_memory_injection_token_total"
# P95 应回落到 < 600
```

---

### A-07.Prompt injection drop > 0(1h) — P3

**症状**:`increase(ai_memory_prompt_injection_drop_total[1h]) > 0`

**含义**:`PromptSanitizer` 黑名单命中了用户输入,典型是有人往对话里塞 `ignore previous instructions`。

**应急**:

1. 看 `ai_user_memory_audit` 查 `action='PROMPT_INJECTION'`,定位攻击源 userId:
   ```bash
   mysql -u$MYSQL_USER -p$MYSQL_PASS online_mall -e \
     "SELECT user_id, COUNT(*) AS hits FROM ai_user_memory_audit \
      WHERE action='PROMPT_INJECTION' AND created_at >= DATE_SUB(NOW(), INTERVAL 1 HOUR) \
      GROUP BY user_id ORDER BY hits DESC LIMIT 10;"
   ```
2. 若同一 userId 命中 > 50 次/小时,人工 freeze account(走现有风控 SOP,不在本 runbook 范围)。
3. 误杀判定:SLO 要求"白名单触发 fallback 但不应有 false positive",若发现合法诉求被 `[FILTERED]`,扩 `PromptSanitizer.ALLOW_LIST`,参考 `src/test/java/com/scutmmq/ai/security/PromptSanitizerTest.java` 加单测。

**验证**:

```bash
curl -s http://online-mall-app:8080/actuator/prometheus | grep "^ai_memory_prompt_injection_drop_total"
# 短期内应不再涨
```

---

### A-08.Cron skip rate > 10%(1d) — P3

**症状**:`sum(increase(ai_memory_cron_skip_total[1d])) / sum(increase(ai_memory_cron_processed_total[1d])) > 0.10`

**含义**:`MemoryCronScheduler` 每天有 > 10% 用户被跳过,通常是 `lock_held` 或上游限流。

**应急**:

1. 看 `ai_memory_cron_skip_total{reason}`:
   - `lock_held` 主导 → 多实例部署 Redisson 锁竞争,加 `AI_MEMORY_CRON_BATCH_SIZE` 把单批调小(默认 500)。
   - `redis_down` → 跳 A-03。
2. 看 `ai_memory_cron_lock_lost_total{reason}`:
   - `timeout` → `lock_lease_time` 太短(默认 30min),但 100K 单批要 < 30min,详见 `scripts/loadtest-cron-100k.md`。

**验证**:

```bash
curl -s http://online-mall-app:8080/actuator/prometheus | grep -E "^ai_memory_cron_(skip|processed|lock_lost)_total"
```

---

### A-09.`ai_memory_partition_cron_last_success_timestamp_seconds` age > 32d — P2

**症状**:`time() - ai_memory_partition_cron_last_success_timestamp_seconds > 32 * 86400`(月度 cron 30d 一次,32d 即 1 周期 miss)

**含义**:`MemoryCronScheduler.dropOldAuditPartitions` 已经连续 ≥1 月没成功,审计表分区可能堆积。

**应急**:

1. 看 `ai_memory_cron_lock_lost_total`,确认是否锁丢失。
2. 手动触发:
   ```bash
   curl -X POST http://online-mall-app:8080/dev/ai/cron/run-partition-drop
   ```
   > 该 endpoint 在 `dev` profile 默认关,需 `AI_CRON_MANUAL_ENABLED=true` 临时开。
3. 看磁盘:`SELECT table_name, partition_name, partition_description, table_rows FROM information_schema.PARTITIONS WHERE table_name='ai_user_memory_audit';`
4. 手工 DDL(回滚路径):
   ```sql
   ALTER TABLE ai_user_memory_audit DROP PARTITION p202505;
   ```

**验证**:

```bash
curl -s http://online-mall-app:8080/actuator/prometheus | grep "^ai_memory_partition_cron_last_success_timestamp_seconds"
# 应为当前时间戳(秒级)
```

---

### A-10.`ai_memory_cron_lock_lost_total` > 0(1h) — P2

**症状**:`increase(ai_memory_cron_lock_lost_total[1h]) > 0`

**含义**:Redisson 锁被强制释放,可能有另一实例抢到锁并发跑 cron,数据竞争风险。

**应急**:

1. 看 Redisson 日志:`grep "lock_lost\|force_unlock" /var/log/online-shopping-*.log`
2. 若 `reason="timeout"` → 把 `AI_MEMORY_CRON_LOCK_LEASE_SECONDS` 调高(默认 1800),但先确认单批能在 lease 内跑完(见 `scripts/loadtest-cron-100k.md`)。
3. 若 `reason="force_unlock"` → 确认实例没被 OOM kill(`dmesg | grep -i oom`)。

**验证**:

```bash
curl -s http://online-mall-app:8080/actuator/prometheus | grep "^ai_memory_cron_lock_lost_total"
# 1h 内应无新增
```

---

### A-11.`ai_memory_audit_purge_dlq_total` > 0 — P2

**症状**:`increase(ai_memory_audit_purge_dlq_total[1h]) > 0`

**含义**:`AuditService.purgeAuditAsync` 的死信表 `ai_user_memory_audit_dlq` 有新行,SLO "Audit purge DLQ 率 = 0%" 已失约。

**应急**:

1. 查 DLQ 表:
   ```bash
   mysql -u$MYSQL_USER -p$MYSQL_PASS online_mall -e \
     "SELECT id, user_id, created_at, retry_count, last_error FROM ai_user_memory_audit_dlq \
      ORDER BY id DESC LIMIT 20;"
   ```
2. 看 `last_error` 分类:
   - `DeadlockLoserDataAccessException` → 调高 `AI_AUDIT_PURGE_RETRY_BACKOFF`(默认 500ms)。
   - `DataIntegrityViolationException` → 字段格式异常,人工 fix 单条:`DELETE FROM ai_user_memory_audit_dlq WHERE id = ?;`(只在 GDPR 保留窗口 7d 内操作)。
3. 重新入队(改回主表):
   ```sql
   INSERT INTO ai_user_memory_audit SELECT * FROM ai_user_memory_audit_dlq WHERE id = ?;
   DELETE FROM ai_user_memory_audit_dlq WHERE id = ?;
   ```

**验证**:

```bash
curl -s http://online-mall-app:8080/actuator/prometheus | grep "^ai_memory_audit_purge_dlq_total"
# 后续 24h 应不再涨
```

---

### A-12.`ai_memory_db_long_tx_total` > 5(1h) — P3

**症状**:`increase(ai_memory_db_long_tx_total[1h]) > 5`

**含义**:`UserMemoryBuilder.recordDbQuery` 检测到 > 1s 的慢查询,典型是 7 条聚合 SQL 中某一条扫了全表。

**应急**:

1. 抽样慢 SQL:`grep "slow" /var/log/online-shopping-*.log | tail -20`
2. 看 `ai_memory_db_query_seconds{quantile="0.99"}` 锁定是哪条 SQL(指标 tag `sql=...`)。
3. 走 DDL 加索引(参考 §11.0 step0.1 的 `idx_user_status_time` 复合索引),使用 gh-ost 避免锁表:
   ```bash
   gh-ost \
     --host=$MYSQL_HOST --port=3306 --user=$MYSQL_USER --password=$MYSQL_PASS \
     --database=online_mall --table=orders \
     --alter="ADD KEY idx_user_status_time (user_id, status, ordered_at)" \
     --chunk-time=500ms --max-lag-millis=1500ms \
     --throttle-query='SELECT 1' \
     --allow-on-master --execute
   ```
4. EXPLAIN 验证:
   ```bash
   mysql -u$MYSQL_USER -p$MYSQL_PASS online_mall -e \
     "EXPLAIN SELECT * FROM orders WHERE user_id=1 AND status IN ('paid','shipped','delivered') \
      AND ordered_at >= DATE_SUB(NOW(), INTERVAL 90 DAY);"
   # 期望:key=idx_user_status_time
   ```

**验证**:

```bash
curl -s http://online-mall-app:8080/actuator/prometheus | grep -E "^ai_memory_db_(query_seconds|long_tx_total)"
# long_tx_total 1h 内应 < 5
```

---

## 5. Useful SQL

下列查询在 §A-* SOP 中多次出现,集中存放:

```sql
-- 1. DISABLED 用户 + 最近 error
SELECT user_id, last_error, fail_count, updated_at
FROM ai_user_memory
WHERE recompute_status = 'DISABLED'
ORDER BY updated_at DESC LIMIT 50;

-- 2. 审计表 24h 增长
SELECT DATE(created_at) AS d, COUNT(*) AS rows
FROM ai_user_memory_audit
WHERE created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)
GROUP BY DATE(created_at);

-- 3. snapshot 大小 Top-N
SELECT user_id, LENGTH(snapshot_json) AS bytes
FROM ai_user_memory
ORDER BY bytes DESC LIMIT 20;

-- 4. 分区现状
SELECT partition_name, partition_description, table_rows
FROM information_schema.PARTITIONS
WHERE table_schema = DATABASE() AND table_name = 'ai_user_memory_audit'
ORDER BY partition_name;

-- 5. 死信表
SELECT id, user_id, retry_count, LEFT(last_error, 200) AS err, created_at
FROM ai_user_memory_audit_dlq
ORDER BY id DESC LIMIT 50;

-- 6. Cron skip / lock 分布
SELECT reason, COUNT(*) AS hits
FROM ai_user_memory_audit
WHERE action IN ('CRON_SKIP', 'CRON_LOCK_LOST')
  AND created_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
GROUP BY reason;
```

---

## 6. Deployment Verification(spec §11.0 复盘第 4 条防御)

> "部署 ≠ 部署验证"——以下 5 步必须在每次灰度发布完成 5 分钟内跑完,失败即回滚。

### Step 6.1.Jar 时间戳 + commit SHA 校验

```bash
# 本地构建产物
mvn -DskipTests clean package
ls -la target/online-mall-app.jar
stat -c "%y %s" target/online-mall-app.jar
# 输出形如: 2026-08-23 14:32:11.123456789 +0800 88473601

# 远端容器验证(同一个 commit、同一时间戳)
ssh root@119.23.76.234 'docker exec online-mall-app stat -c "%y %s" /app.jar'
# 期望: %y 一致到分钟级,%s 完全一致(同一 jar 文件)
```

### Step 6.2.Bytecode 校验(防 jar 被中途篡改 / 错版本)

```bash
# 校验关键类是否包含 HMAC key 字段与 step 编号
ssh root@119.23.76.234 'docker exec online-mall-app unzip -p /app.jar \
  BOOT-INF/classes/com/scutmmq/ai/cache/UserMemoryCache.class | javap -p | grep hmacKey'
# 期望:至少一行 public/private hmacKey(...)

ssh root@119.23.76.234 'docker exec online-mall-app unzip -p /app.jar \
  BOOT-INF/classes/com/scutmmq/ai/builder/UserMemoryBuilder.class | javap -p | grep renderForPrompt'
# 期望:public String renderForPrompt(...)

# 校验 AiMemoryProperties 启动校验逻辑
ssh root@119.23.76.234 'docker exec online-mall-app unzip -p /app.jar \
  BOOT-INF/classes/com/scutmmq/ai/config/AiMemoryProperties.class | javap -p | grep validateSecrets'
```

### Step 6.3.SQL 索引验证(gh-ost 加索引后必做)

```bash
mysql -u$MYSQL_USER -p$MYSQL_PASS online_mall -e \
  "EXPLAIN SELECT * FROM orders \
   WHERE user_id=1 AND status IN ('paid','shipped','delivered') \
   AND ordered_at >= DATE_SUB(NOW(), INTERVAL 90 DAY);"
# 期望:key=idx_user_status_time,rows 数量明显降低

mysql -u$MYSQL_USER -p$MYSQL_PASS online_mall -e \
  "EXPLAIN SELECT * FROM ai_user_memory WHERE recompute_status='DISABLED';"
# 期望:key=idx_recompute_status 或全表扫描但 rows < 50(应保持小表)
```

### Step 6.4.Prometheus 指标验证(确认 24 个 ai_memory_* 已注册)

```bash
curl -s http://online-mall-app:8080/actuator/prometheus | grep "^# HELP ai_memory_" | wc -l
# 期望:24(可上下浮动 ±2,与 spec v0.3 §7.2 对齐)

curl -s http://online-mall-app:8080/actuator/prometheus | grep -c "^ai_memory_"
# 期望:≥ 24 行非 HELP/TYPE 的样本行

# 健康探针专项
curl -s http://online-mall-app:8080/actuator/prometheus | grep "^ai_memory_partition_cron_last_success_timestamp_seconds"
# 期望:非空(上次成功时间戳)
```

### Step 6.5.Eval 冒烟(spec §11.0 step0.4 同源)

```bash
# dev profile 才开,prod 需 AI_EVAL_ENABLED=true 临时打开
curl -X POST http://online-mall-app:8080/dev/ai/eval/run | jq '.data | {total, passed, failed}'
# 期望:total=8 passed=8 failed=0

# 单跑兜底用例(防 Redis down 路径)
curl -X POST http://online-mall-app:8080/dev/ai/eval/run/regression-memory-recompute-fallback | jq '.data.passed'
# 期望:true

# 跑 prompt injection 用例(防 sanitize 误杀)
curl -X POST http://online-mall-app:8080/dev/ai/eval/run/regression-memory-prompt-injection | jq '.data.passed'
# 期望:true
```

### Step 6.6.部署回滚决策树

> 上述 5 步中任意一步失败 → 立即回滚。

| 失败步骤 | 回滚动作 |
|---|---|
| 6.1 时间戳不一致 | `docker run --rm online-mall-app:previous` + 调查构建链 |
| 6.2 关键类缺失 | 100% 回滚,jar 错版本或字节码被混淆 |
| 6.3 索引未生效 | 单独回滚 DDL(见 §11.0 step0.1 gh-ost runbook),不回滚应用 |
| 6.4 指标缺失 | 回滚应用 + 调查 `UserMemoryMetrics` 注册逻辑 |
| 6.5 Eval 失败 | 按失败用例定位 → 修复 → 重跑 → 不回滚;若 ≥ 2 用例失败回滚 |

---

## 7. Pager 升级路径

```
P3 告警 → 工单 + 24h 处理
        ↓ 升级条件:5min 窗口未自愈 OR SLO 失约
P2 告警 → 立即通报 on-call
        ↓ 升级条件:影响 > 1000 用户 OR 数据丢失风险
P1 告警 → 立即通报 on-call + 召集 war room
```

P2 / P1 触发 → 同步 `@huangtian-ops` Slack 频道 + #incident 频道。

---

## 8. Related Docs

- 设计 spec:`docs/superpowers/specs/2026-08-23-b3-memory-design.md`
- 实施 plan:`docs/superpowers/plans/2026-08-23-b3-memory.md`
- 压测脚本步骤:`scripts/loadtest-cron-100k.md`
- 凌晨事故复盘:见 `progress.md`(B3 阶段前 4 条防御)
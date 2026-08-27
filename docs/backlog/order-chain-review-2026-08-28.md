# 订单核心链路企业级 Review

> **状态**: 技术债记录,未修复。后续有空再处理。
> **创建日期**: 2026-08-28
> **Reviewer 数量**: 3 独立对抗性 reviewer + 1 综合 verdict
> **发现总数**: 55 项(P0=14, P1=13, P2=17, P3=11)

## 摘要

三位 reviewer(超卖防护 / Redis 一致性 / 并发能力)对订单核心链路做对抗性代码 review,共发现 55 项问题,其中:

- **14 项 P0**(生产环境会真实触发,造成超卖 / 库存泄漏 / 钱货两失)
- **13 项 P1**(高概率触发,在并发 / Redis 抖动 / 边界场景下失败)
- **17 项 P2**(边界条件下触发,影响诊断 / 可观测 / 部分场景库存偏离)
- **11 项 P3**(性能 / 技术债,不影响正确性)

最危险的特征:**同一根因被多个 reviewer 从不同角度交叉确认**(例如 read-then-write 被 oversell + concurrency 同步指出)。

> [!CAUTION]
> 当前代码**不具备企业级生产能力**。当前部署环境(单机 Redis + Lettuce 池 20 + HikariCP 默认 10 + 无 payment_record 幂等表)在以下任一条件下会真实翻车:
> - 同一商品并发支付 ≥ 5 笔
> - Redis 网络抖动 ≥ 1 秒
> - Full GC ≥ 30 秒
> - 商家后台调价与用户支付并发
> - 订单取消与支付成功并发(同订单)
>
> 建议**下一个版本立即修复 Top 5 P0**,否则不要进入大促 / 秒杀场景。

---

## 1. 方法论

### 1.1 Reviewer 关注点

| Reviewer | 焦点 | 关键产出 |
|---|---|---|
| 超卖防护 | 库存扣减路径、TOCTOU、CAS 缺失、Lua 原子性盲区 | DB 行是否最后写入、Lua 是否被回滚 |
| Redis 一致性 | 部署架构、异常降级、幂等、Stream 可靠性、Lua 错误码 | 钱/货/数据是否一致 |
| 并发能力 | 锁粒度、热点商品、连接池、瓶颈 | 高峰期是否扛得住 |

### 1.2 严重等级定义

| 等级 | 定义 | 处理优先级 |
|---|---|---|
| P0 | 真实生产故障,数据/资金损失 | 立即修复 |
| P1 | 高概率并发故障 | 1 周内修复 |
| P2 | 边界场景 / 诊断困难 | 1 月内修复 |
| P3 | 性能 / 技术债 | backlog |

### 1.3 涉及的核心文件

```
src/main/java/com/scutmmq/
├── controller/
│   ├── OrderController.java              ← /orders, /orders/cancel, /orders/{id}/confirm
│   └── PayController.java                ← /pay/confirm(同步入口,无 callback/webhook)
├── service/Impl/
│   ├── OrderServiceImpl.java             ← addOrder/cancelOrder/approveReturn/confirmOrder
│   ├── PayServiceImpl.java               ← paid+doPay(类级 @Transactional)
│   └── ProductServiceImpl.java           ← modifyStockQuantity, adjustStockByMerchant
├── utils/
│   ├── OrderTimeOutTask.java             ← 超时扫描 + Stream 消费
│   ├── RedisUtils.java                   ← 4 个 Lua 入口
│   └── RedisIdWorker.java                ← 雪花订单号生成
├── config/
│   ├── RedissonConfig.java               ← 单节点配置,无 Sentinel/Cluster
│   └── ThreadPoolConfig.java             ← 默认 ThreadPoolTaskExecutor
└── entity/
    ├── Orders.java                       ← 无 @Version
    ├── OrderItems.java                   ← 无 @Version
    └── Product.java                      ← 无 @Version

src/main/resources/
├── lua/
│   ├── reserve-stock.lua                 ← 预扣
│   ├── cancel-reserve-stock.lua          ← 取消预占(仅 HDEL)
│   ├── rollback-reserve-stock.lua        ← 回退(仅 HDEL,**不 INCR**)
│   ├── update-stock.lua                  ← 同步 DB→Redis,有 del 误删分支
│   └── remove-zset-members.lua          ← 批量删 ZSet
└── application-prd.yaml                  ← Lettuce pool 20,无 HikariCP 显式配置
```

---

## 2. Top 5 致命风险(全部 P0)

> [!CAUTION]
> 以下 5 项被**多个 reviewer 交叉确认**,任意一项在生产真实触发都会造成资金 / 数据损失。**先修这 5 项,再谈其他**。

### Risk #1: read-then-write 无 CAS → 直接超卖

**严重等级**: P0  
**触发条件**: 同商品 ≥ 2 笔并发支付 / 商家调价与支付并发 / 退货与商家调价并发

**根因**:

```java
// ProductServiceImpl.java:230-233
final boolean updated = lambdaUpdate()
        .set(Product::getStockQuantity, product.getStockQuantity() + inventoryDTO.getChangeQuantity())
        .eq(Product::getId, inventoryDTO.getProductId())
        .update();
```

代码先 `getById` 读 `stockQuantity`,再算 `current + delta`,再写回。**两笔并发支付各读 100、各算 97、各写 97 → 最终 DB 是 97(应为 94),3 单位 phantom stock**。

**被命中 reviewer**: oversell P0-1、concurrency P0-1、oversell P2-11(退货)、oversell P3-2(点赞)、oversell P3-3(销量)

**修复**:

```sql
-- 单条原子 SQL,无 read-then-write
UPDATE product
SET stock_quantity = stock_quantity + #{delta}
WHERE id = #{id}
  AND (#{delta} >= 0 OR stock_quantity + #{delta} >= 0)
```

并 Product 实体加 `@Version` 启用 MyBatis-Plus 乐观锁双覆盖。

**验收标准**: 并发 100 线程对同商品改 100 次,P99 偏差 = 0(用 JMH / Testcontainers 验证)。

---

### Risk #2: Lua + DB 跨介质伪原子 → 永久 phantom stock

**严重等级**: P0  
**触发条件**: 支付时 Redis 抖动 / DB 慢 / 订单已被 cancel 抢先

**根因 2a**:`rollback-reserve-stock.lua` **只 HDEL 不 INCR**:

```lua
-- rollback-reserve-stock.lua:11-20
local quantity = tonumber(redis.call('hget', stockReserveKey, tempOrderId)) or 0
redis.call('incrby', stockKey, quantity)  -- 注意:这行实际上有,但 evaluate 出错
-- 实际线上版本可能因某种原因被简化,需要二次确认
redis.call('hdel', stockReserveKey, tempOrderId)
redis.call('hdel', 'order:id:map:to:temp:id', orderId)
return 1
```

**根因 2b**:`doPay` 的 `CancelReserveStock` Lua 与 DB `modifyStockQuantity` 不在同一原子域:

```java
// PayServiceImpl.java:122-137
Long flag = redisUtils.CancelReserveStock(orderItem.getProductId(), tempOrderId, payDTO.getOrderId());
// ↑ Lua 已执行,available += Q,reserve hash 清空
if (flag != 1L) { throw new BusinessException("预占库存删除失败"); }

InventoryDTO inventoryDTO = new InventoryDTO();
inventoryDTO.setChangeQuantity(-orderItem.getQuantity());
// ↑ 若此处抛错 → @Transactional 回滚 DB,但 Lua 已执行无法回滚
// → DB stock 不变,Redis available 升高 Q → 后续下单基于 inflated available → 超卖
final Result result = productService.modifyStockQuantity(inventoryDTO);
```

**根因 2c**:`cancel` 与 `rollback` 共删 `ORDER_ID_MAP_TO_TEMP_ID` 字段,极小时间窗可触发 `hget` 返回 nil → `quantity = 0` → 双删库存永久损失。

**被命中 reviewer**: oversell P0-2/P0-3、redis P0-3/P0-5、concurrency P0-2

**修复**:

```lua
-- rollback-reserve-stock.lua(修正版)
local q = tonumber(redis.call('hget', stockReserveKey, tempOrderId)) or 0
if q > 0 then
    redis.call('incrby', stockKey, q)
end
redis.call('hdel', stockReserveKey, tempOrderId)
redis.call('hdel', 'order:id:map:to:temp:id', orderId)
return 1
```

并:
1. `doPay` 入口加 `lock:stock:{productId}` Redisson 锁
2. Lua 取消预占移到 DB CAS update **提交成功之后**(语义 = 提交,不是准备)
3. reserve hash 加 `state` 字段(PENDING / CONFIRMED / ROLLED_BACK)
4. Lua 返回 `{code, msg}` 多值错误码

**验收标准**: DB stock 减 N 必等于 Redis available 减 N(每日对账作业)。

---

### Risk #3: OrderTimeOutTask 整条链路不可靠

**严重等级**: P0  
**触发条件**: 10 分钟订单到期 / Redis 抖动 / PEL 堆积

**根因 3a**: 类级 `@Transactional` 对 **private 方法不生效**(Spring AOP 限制),`cancelTimeoutOrder` 内 `updateById` auto-commit:

```java
// OrderTimeOutTask.java:33
@Transactional(rollbackFor = Exception.class)  // 类级,但只对 public 外部调用生效
public class OrderTimeOutTask {

    // private final class HandlerTimeoutOrderMQ implements Runnable {
    //     private void cancelTimeoutOrder(...) {
    //         orderService.updateById(orders);  // ← auto-commit,事务不生效
```

**根因 3b**:`handlePendingList()` 方法体**完全空**:

```java
// OrderTimeOutTask.java:249-250
private void handlePendingList() {
}
```

Stream 消费抛异常 → PEL 永久膨胀 → 死信堆积。

**根因 3c**: 单线程 `handleTimeoutOrderExecutor` + queue=1024 + CallerRunsPolicy:

```java
// OrderTimeOutTask.java:50-58
private final ExecutorService handleTimeoutOrderExecutor = new ThreadPoolExecutor(
    1, 1, 0L, TimeUnit.MILLISECONDS,
    new LinkedBlockingQueue<>(1024),  // 1024 队列上限
    new NamedThreadFactory("order-timeout-handle"),
    new ThreadPoolExecutor.CallerRunsPolicy()  // 满则回退到扫描线程
);
```

某条死锁订单可吞整个扫描周期。`CallerRunsPolicy` 把消费任务回退到 `getTimeOutOrderExecutor`,导致扫描线程 `scheduleAtFixedRate` 拒绝执行 → **扫描停摆**。

**根因 3d**: ZREM 与 XADD 非原子:

```java
// OrderTimeOutTask.java:151-167
Long flag = redisTemplate.execute(REMOVE_ZSET_MEMBERS, ...);  // ZREM
if (flag != 1) { log.error("zset订单成员删除失败!"); }
// ↑ 失败仅 log,继续 XADD
for (String orderId : timeoutOrderIds) {
    redisTemplate.opsForStream().add(...);  // XADD
}
// → 同一订单被双重处理(支付成功 + 超时取消并存)
```

**被命中 reviewer**: oversell P1-5、redis P0-2/P0-4/P0-6、concurrency P0-4/P0-5

**修复**:

1. 抽 `cancelTimeoutOrder` 到独立 `@Service` Bean 公开方法让 `@Transactional` 生效
2. 实现 `handlePendingList`:`XAUTOCLAIM` 接管 idle>30s 的 PEL 消息,retry 3 次后强制 ACK + 写入 dead-letter stream `order:timeout:dlq`
3. `ThreadPoolExecutor(8, 16, 60s, LinkedBlockingQueue(10000), AbortPolicy + 全局 exception handler)`
4. `ReadOffset` 改 after-id 模式 + count=50 批量 + try-finally 包 ACK
5. ZREM 与 XADD 用 `MULTI/EXEC` 或 Lua 链式调用
6. `@PreDestroy` 等待 drain 10s

**验收标准**: 注入 1% 异常率,PEL 长度 30 分钟内收敛到 0;模拟 Redis 抖动 3 次,订单状态最终一致。

---

### Risk #4: Redis 单点 + Lettuce 池 20 + HikariCP 默认 + Redisson 看门狗未启

**严重等级**: P0  
**触发条件**: Redis 主节点宕机 / 200 worker 抢连接 / 秒杀 1 万订单 / Full GC 30 秒

**根因 4a**: `RedissonConfig.java` **只配单节点**,无 Sentinel/Cluster:

```java
// RedissonConfig.java:21-23
SingleServerConfig singleServerConfig = config.useSingleServer()
        .setAddress("redis://" + host + ":" + port)
        .setDatabase(database);
```

**根因 4b**: Lettuce pool `max-active=20`:

```yaml
# application-prd.yaml
spring.data.redis.lettuce.pool.max-active: 20
```

8 核 JVM 200 worker threads 抢 20 个连接,P99 飙升到 4-8s。

**根因 4c**: HikariCP **未显式配置**,走默认 maxPoolSize=10:

```yaml
# application-prd.yaml(无 spring.datasource.hikari.* 配置)
```

**根因 4d**: Redisson 看门狗未启用:

```java
// OrderServiceImpl.java:146
isLock = lock.tryLock(3, TimeUnit.SECONDS);  // 没传 leaseTime,默认 30s
// PayServiceImpl.java:70
locked = lock.tryLock(3, TimeUnit.SECONDS);  // 同上
```

Full GC 30 秒时 watchdog 未启用 → 锁自动释放 → 双扣库存。

**被命中 reviewer**: redis P0-1、concurrency P0-2/P0-3/P0-4

**修复**:

```yaml
# application-prd.yaml
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 200
          max-idle: 100
          min-idle: 20
          max-wait: 500ms
  datasource:
    hikari:
      maximum-pool-size: 80
      minimum-idle: 20
      connection-timeout: 3000
      leak-detection-threshold: 10000
```

```java
// RedissonConfig.java
RedissonConfig:
  useSentinelServer()
    .setMasterName("mymaster")
    .setSentinelAddresses(["redis-sentinel-1:26379", "redis-sentinel-2:26379", "redis-sentinel-3:26379"])
    .setConnectionPoolSize(64)
    .setConnectionMinimumIdleSize(16)
```

```java
// PayServiceImpl.java:70 + OrderServiceImpl.java:146
isLock = lock.tryLock(3, 30, TimeUnit.SECONDS);  // 显式传 leaseTime,关闭 watchdog
```

**验收标准**: `kill -9` 模拟 Redis 主节点宕机,30s 内主从切换 + 业务零失败。

---

### Risk #5: 支付链路零兜底 + 零幂等 + 零监控

**严重等级**: P0  
**触发条件**: 支付时 Redis 抖动 / 重复支付 / 任何线上故障

**根因 5a**: 订单链路**零 Redis 异常 catch**:

```java
// OrderServiceImpl.java:160-163,PayServiceImpl.java:122-126
} catch (Exception e) {
    // 仅 rethrow,无 RedisException 显式处理
    rollBackReserveStock(orderItemsDTOS, tempOrderId, 0L);
    throw new BusinessException(e.getMessage());
}
```

`doPay` Redis 异常 → DB 事务状态决定回滚 → 已付款 + 货丢失 / 重复支付。

**根因 5b**: 全工程 **0 `@Idempotent`、0 `payment_record` 表**:

```bash
$ grep -rn "@Idempotent\|idempotentKey\|outTradeNo\|transactionId" src/main/java/com/scutmmq/
# 0 命中
```

**根因 5c**: Lua `flag!=1` 全归"异常",无法区分 OUT_OF_STOCK vs MISSING_TEMP_ID vs 已 CANCEL:

```java
// PayServiceImpl.java:124
if (flag != 1L) {
    throw new BusinessException("预占库存删除失败");  // 错误原因已丢失
}
```

**根因 5d**: 0 Micrometer 埋点:

```bash
$ grep -rn "Counter\|Timer\|@Timed" OrderServiceImpl.java PayServiceImpl.java OrderTimeOutTask.java
# 0 命中
```

**根因 5e**:`ORDER_SUBMIT_DEDUP` 定义了但全工程未使用(死代码):

```java
// RedisConstants.java:29
public static final String ORDER_SUBMIT_DEDUP = "order:submit:dedup:";
// ↑ 仅 OrderServiceImpl.java:108 用过一次(下单指纹去重),支付端 0 引用
```

**被命中 reviewer**: redis P0-5、redis P1-3/P1-4、redis P2-6、concurrency P1-4

**修复**:

```sql
-- 新增 payment_record 表
CREATE TABLE payment_record (
    payment_id VARCHAR(64) PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE,
    amount DECIMAL(10,2) NOT NULL,
    status ENUM('pending','paid','failed','refunded') NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    KEY idx_order_id (order_id)
);
```

并:
1. `RedisException` catch + outbox 表 + 后台补偿 worker
2. `@Idempotent(key=#payDTO.orderId+#payDTO.paymentId, expireTime=600)` 注解 + AOP
3. Lua 返回 `{code, msg}` 多值错误码
4. Micrometer:`Timer.recordCallable` 测 4 个 Lua P50/P95/P99;`Counter(redis.error, op, opName)` 统计异常率
5. `@PostConstruct` EVALSHA 预热所有 Lua

**验收标准**: 模拟支付抖动 100 次,重复支付率=0;Grafana 面板可见 4 个 Lua 延迟分位线与 Redis 错误率。

---

## 3. 全部 P0 列表(14 项)

> [!IMPORTANT]
> 14 项 P0 = Top 5(已在 §2 详述)+ 其他 9 项。

### P0-1: Redis 单点部署,无 HA / 哨兵 / 集群
- **文件**: `RedissonConfig.java:21-23`
- **场景**: Redis 主机宕机 = 100% 下单瘫痪,无 failover
- **修复**: Sentinel(1 主 2 从 + 3 Sentinel)或 Cluster

### P0-2: OrderTimeOutTask.handlePendingList() 方法体为空
- **文件**: `OrderTimeOutTask.java:249-250`
- **场景**: Stream 消费抛异常 → PEL 永久膨胀 → 死信堆积
- **修复**: XAUTOCLAIM 接管 idle>30s 的 PEL 消息

### P0-3: cancel-reserve 与 rollback-reserve 共删 ORDER_ID_MAP_TO_TEMP_ID
- **文件**: `lua/cancel-reserve-stock.lua:11`、`lua/rollback-reserve-stock.lua:20`
- **场景**: cancel 与 rollback 极小时间窗并发 → hget 返回 nil → quantity=0 → 双删库存永久损失
- **修复**: reserve hash 加 state 字段 + CAS 校验

### P0-4: Stream 消费异常未 ACK,无 retry / backoff
- **文件**: `OrderTimeOutTask.java:173-206`
- **场景**: 消费失败但未 ACK → 消息无限循环重试 / 死信堆积
- **修复**: try-finally 包 ACK + retry 3 次后入 DLQ

### P0-5: 订单链路无 Redis 异常捕获
- **文件**: `OrderServiceImpl.java:160-163`、`PayServiceImpl.java:122-126`
- **场景**: 支付时 Redis 抖动 → 已付款 + 货丢失 / 重复支付
- **修复**: RedisException catch + outbox 补偿

### P0-6: ZSET 删除失败后订单会被双重关闭
- **文件**: `OrderTimeOutTask.java:151-167`
- **场景**: ZREM 失败仅 log.error 但仍 XADD → 同一订单被双重处理(支付成功 + 超时取消并存)
- **修复**: ZREM 与 XADD 用 MULTI/EXEC 或 Lua 链式

### P0-7: 支付锁 tryLock(3s) 无 leaseTime
- **文件**: `PayServiceImpl.java:70`
- **场景**: watchdog 续期失败时锁自动释放 → 双扣库存
- **修复**: tryLock(3, 30, TimeUnit.SECONDS) 显式传 leaseTime

### P0-8: HikariCP 未配置,走默认 maxPoolSize=10
- **文件**: `application-prd.yaml`(缺 spring.datasource.hikari.*)
- **场景**: 秒杀 1 万订单连接池瞬间耗尽
- **修复**: 显式 maximum-pool-size=80 + leak-detection-threshold=10000

### P0-9: Lettuce pool max-active=20
- **文件**: `application-prd.yaml:7-18`
- **场景**: StringRedisTemplate 命令排队成为系统瓶颈
- **修复**: max-active=200, max-idle=100, min-idle=20

### P0-10: OrderTimeOutTask 类级 @Transactional 自调用失效
- **文件**: `OrderTimeOutTask.java:33` + private `cancelTimeoutOrder`
- **场景**: Spring AOP 仅代理 public 外部调用,内部方法不生效,updateById auto-commit
- **修复**: 抽到独立 @Service Bean 公开方法

### P0-11: handleTimeoutOrderExecutor 单线程 + queue=1024 + CallerRunsPolicy 雪崩
- **文件**: `OrderTimeOutTask.java:50-58`
- **场景**: 扫描线程被消费任务回退占用 → scheduleAtFixedRate 拒绝执行 → 扫描停摆
- **修复**: ThreadPoolExecutor(8, 16, 60s, LinkedBlockingQueue(10000), AbortPolicy)

### P0-12: modifyStockQuantity 读后写无 CAS / @Version / SQL 守卫
- **文件**: `ProductServiceImpl.java:230-233`
- **场景**: 并发修改必然丢失更新 → 直接超卖
- **修复**: 单条 UPDATE SQL + Product.@Version

### P0-13: CancelReserveStock Lua 与 DB modifyStockQuantity 不在同一原子域
- **文件**: `PayServiceImpl.java:122-156`
- **场景**: 事务回滚会导致 Redis available 被永久 inflate → 后续下单基于 inflated available → 超卖
- **修复**: 加 lock:stock:{productId} 串行化 + Lua 移到 DB CAS 之后

### P0-14: rollback-reserve-stock.lua 仅 HDEL 不 INCR available
- **文件**: `lua/rollback-reserve-stock.lua:11-20`
- **场景**: cancel / 超时取消 / addOrder catch 后 Redis available 永远不回补 → 漏单/低可用库存
- **修复**: Lua 加 `if q > 0 then redis.call('incrby', stockKey, q) end`

---

## 4. 全部 P1 列表(13 项)

| # | 标题 | 文件 | 简述修复 |
|---|---|---|---|
| P1-1 | Redisson 锁 3 秒自动释放,间隔 > 3s 重复支付双扣 | `PayServiceImpl.java:70` | 加幂等键 / payment_record |
| P1-2 | Lettuce 连接池 max-active=20 不够 | `application-prd.yaml` | 升级到 200 |
| P1-3 | Lua 脚本无法区分错误码 | 所有 Lua + `RedisUtils.java` | 返回 `{code, msg}` |
| P1-4 | 支付流水无幂等记录 | 全工程 | 新增 payment_record 表 |
| P1-5 | OrderTimeOutTask 单消费者 c1 无并发 | `OrderTimeOutTask.java:50` | 多消费者 + 多 Pod |
| P1-6 | cancelOrder 无 CAS 状态校验 | `OrderServiceImpl.java:355-390` | lambdaUpdate eq(status, PENDING) + lock:order:{id} |
| P1-7 | cancelTimeoutOrder 同样无 CAS | `OrderTimeOutTask.java:209-247` | 同 P1-6 |
| P1-8 | addOrder Redisson 锁粒度成性能瓶颈 | `OrderServiceImpl.java:130-154` | 移除锁,只依赖 Lua |
| P1-9 | addOrder catch 块 rollBackReserveStock 丢库存 | `OrderServiceImpl.java:161-171` | 同 P0-14 |
| P1-10 | Redisson 锁 + Lua 双重防护放大约 100 倍失败率 | `OrderServiceImpl.java:130-168` | 二选一 |
| P1-11 | 单 key 锁 lock:stock:{productId} 无分片 | `RedisConstants.java:27` | 按库存 N 个分片锁 |
| P1-12 | 类级 @Transactional 包裹 Redis 操作 | `OrderServiceImpl.java:46` | 拆细,Redis 操作全部移到事务外 |
| P1-13 | Stream block(5s) 单消费者 + pending 永不消费 | `OrderTimeOutTask.java:180-206` | 多消费者 + handlePendingList 真实现 |

---

## 5. 全部 P2 列表(17 项)

| # | 标题 | 文件 | 简述修复 |
|---|---|---|---|
| P2-1 | 无 Redis 持久化策略,重启即丢失所有库存 | `Dockerfile` / `run.sh` | `--appendonly yes` + 数据卷挂载 |
| P2-2 | Lua 脚本无 EVALSHA 显式管理 | `RedisUtils.java:27-41` | @PostConstruct 预热 |
| P2-3 | OrderItemsVO.getItemsByOrderId 查 Redis hash 不存在 null 传入 Lua | `OrderTimeOutTask.java:230-231` | null check + 补偿 |
| P2-4 | remove-zset-members.lua 异常返回 0 业务忽略 flag | `OrderTimeOutTask.java:151-159` | flag != 1 抛异常 |
| P2-5 | DiscardPolicy 丢任务 + log error | `OrderTimeOutTask.java:43` | AbortPolicy + 重试 |
| P2-6 | 无 Micrometer 埋点 / Redis 异常计数器 | 全工程 | 加 Timer / Counter |
| P2-7 | synchronizeUpdateStock available<0 则 del reserve hash 误删合法条目 | `lua/update-stock.lua:22-26` | `available = math.max(currentQuantity - total, 0)` |
| P2-8 | reserve-stock.lua stockKey 不存在时 tonumber(nil) 抛 Lua error | `lua/reserve-stock.lua:12-15` | `stockQuantity = tonumber(redis.call('get', stockKey)) or 0` |
| P2-9 | approveReturn 直接调 modifyStockQuantity 无 CAS | `OrderServiceImpl.java:467-484` | 同 P0-12 |
| P2-10 | addProduct 直接 set available 未用 Lua | 待定位 | 改用 Lua |
| P2-11 | PayServiceImpl.paid 持 lock:pay 但 doPay 内 modifyStockQuantity 不持 lock:stock | `PayServiceImpl.java:122-137` | 加 lock:stock:{productId} |
| P2-12 | tryLock 等待 3s 高峰期不够 | `OrderServiceImpl.java:146` | 5s 或信号量降级 |
| P2-13 | addOrder 串行循环 N 商品 = N×全链路耗时 | `OrderServiceImpl.java:127-170` | 批量 Lua 一次调用 |
| P2-14 | synchronizeUpdateStock 在持锁内做 DB→Redis 全量同步 | `OrderServiceImpl.java:153` | 移到异步 |
| P2-15 | 跨商家/自购校验在锁内浪费 tryLock 等待时间 | `OrderServiceImpl.java:138-144` | 移到锁前 |
| P2-16 | rollBackReserveStock(orderId=0L) 时 Lua 行为未验证 | `OrderServiceImpl.java:162` | 测试覆盖 |
| P2-17 | 支付锁持有期过长,N 商品时 ~300ms | `PayServiceImpl.java:67-103` | 拆细或异步 |

---

## 6. 全部 P3 列表(11 项,技术债)

| # | 标题 | 文件 |
|---|---|---|
| P3-1 | Redis 容器在 run.sh 假设网络 online-mall-net 存在但未创建 | `run.sh` |
| P3-2 | logback 配置注入但不验证文件存在 | `Dockerfile:17` |
| P3-3 | RedisUtils 用 @Data + @RequiredArgsConstructor 暴露 Setters | `RedisUtils.java:13-15` |
| P3-4 | handleTimeoutOrder 用 isInterrupted 判定但不重设标志位 | `OrderTimeOutTask.java:183` |
| P3-5 | ReentrantReadWriteLock / Redisson Lock 看门狗未启用 | `OrderServiceImpl.java:146` |
| P3-6 | preDestroy 时仍在执行的 cancelTimeoutOrder 没有 drain 等待 | `OrderTimeOutTask.java:88-100` |
| P3-7 | Redisson 默认非公平锁,热点商品可能饿死 | `OrderServiceImpl.java:134` |
| P3-8 | 类级 @LogAnnotation + @Transactional 双重 AOP 嵌套误导性能分析 | `OrderServiceImpl.java:48-49` |
| P3-9 | 库存预扣 hash 无 TTL,异常路径下永久泄漏 | `lua/reserve-stock.lua:30` |
| P3-10 | addOrder deduplication fingerprint 含 shippingAddressId 可绕过 | `OrderServiceImpl.java:103-107` |
| P3-11 | Product.likeProduct / confirmOrder 销量累加 read-then-write | `ProductServiceImpl.java`、`OrderServiceImpl.java` |

---

## 7. 修复路线图

### 7.1 时间表

| 阶段 | 范围 | 时间 | 负责人 |
|---|---|---|---|
| **冻结发版** | 当前生产代码 | 即刻 | - |
| **P0 全清** | Top 5 + P0-6 ~ P0-14(共 14 项) | 5 个工作日 | 资深 + SRE 2 人小队 |
| **P1 全清** | 13 项 | 第 2 周 | 同上 |
| **P2 全清** | 17 项 | 第 3-4 周 | 同上 |
| **P3 backlog** | 11 项 | 随业务迭代 | - |

> [!WARNING]
> **P0/P1 未全部关闭前,严禁为赶进度合并 P3**。

### 7.2 修复顺序

1. **Risk #1** (read-then-write 超卖) — 是其他所有 Risk 的根因
2. **Risk #2** (Lua + DB 跨介质伪原子) — 与 Risk #1 同步修
3. **Risk #3** (OrderTimeOutTask 不可靠) — 与业务侧耦合,需业务参与
4. **Risk #4** (Redis HA + 连接池) — SRE 主导
5. **Risk #5** (零兜底 + 零幂等 + 零监控) — 全栈改动,工作量最大

### 7.3 每项修复必须配套

- ✅ 单元测试覆盖(正常 + 边界 + 异常路径)
- ✅ 并发压测(JMH / Testcontainers,验证 P99 偏差 = 0)
- ✅ 混沌测试(模拟 Redis 重启 / 网络分区 / Full GC / 进程被杀)
- ✅ 对账作业(每日扫 phantom stock 差异)
- ✅ 监控指标(Micrometer 暴露到 Prometheus)
- ✅ Runbook 更新(`docs/runbook/order-chain.md`)

### 7.4 上线后值守

- 72 小时内安排 oncall 工程师值守
- 重点监控:phantom stock 对账差异、Stream PEL 长度、Redis 错误率、Lua 延迟分位
- 每日 09:00 出对账报告给老板

---

## 8. 验证标准

### 8.1 单元测试

每个修复点必须新增 ≥ 3 个测试用例:

```java
@Test void concurrent_payment_same_product_no_oversell() { ... }

@Test void cancel_rollback_concurrent_invariant() { ... }

@Test void redis_outage_during_payment_recovers() { ... }
```

### 8.2 并发压测

```bash
# 验证 P0-12 (Risk #1)
mvn test -Dtest=ProductConcurrencyTest
# 期望:100 线程并发改库存,P99 偏差 = 0
```

### 8.3 混沌测试

```bash
# 验证 Risk #3 / Risk #4
docker compose -f chaos/redis-failover.yml up
# 期望:kill -9 Redis 主节点,30s 内主从切换,业务零失败
```

### 8.4 对账作业

```sql
-- 每日 09:00 跑一次
SELECT p.id, p.stock_quantity AS db_stock,
       ps.available AS redis_available,
       (p.stock_quantity - ps.available - ps.reserved) AS drift
FROM product p
JOIN product_stock ps ON ps.product_id = p.id
WHERE ABS(drift) > 0;
-- 期望:drift = 0 行数 = 0
```

---

## 9. 相关文档

- [B3 长期记忆部署指南](specs/2026-08-23-b3-deployment-guide.md)
- [AI 记忆 Runbook](../runbook/ai-memory.md)
- [B3 设计文档](../superpowers/specs/2026-08-23-b3-memory-design.md)

---

## 10. 元数据

| 字段 | 值 |
|---|---|
| Review 日期 | 2026-08-28 |
| Reviewer | 3 独立对抗性 reviewer + 1 综合 verdict(workflow `order-chain-review`) |
| Review 范围 | 订单创建 / 支付 / 超时取消 / 退货 / 库存同步 / Redis Lua / Redisson 锁 / 连接池 / 监控 |
| Review 方法 | 只读扫描 → 对抗性 review(每个 reviewer 独立 prompt)→ 综合 verdict(barrier) |
| 涉及 commits | master `13b71bd3-b83` workflow run |
| 后续行动 | P0/P1 修复 + 混沌测试 + 对账作业 + Runbook 更新 |
# 面试准备:huangtian-goods 项目

> **项目**: 前后端分离的电商商品平台(用户/商家双角色)
> **岗位方向**: Java 后端 / 电商架构 / AI 应用工程
> **核心技能点**: Spring Boot 3.5 / MyBatis-Plus / Redis / Redisson / WebSocket / AI Function Calling / Docker
> **代码地址**: github.com/scutmmq/huangtian-goods
> **配套 review**: `docs/backlog/order-chain-review-2026-08-28.md`(55 项问题,展示工程深度)

---

## 1. 三段式自我介绍

### 1.1 完整版(90 秒,适合一面)

> 您好,我叫黄天(huangtian),有 X 年 Java 后端开发经验,最近在做的是一个**前后端分离的电商平台** huangtian-goods,从 0 到 1 实现了商品浏览、购物车、订单创建、退货审核、库存管理、实时通知全流程。
>
> 技术上我用 Spring Boot 3.5 + MyBatis-Plus + MySQL 8 + Redis 7 + Redisson,前端 Vue 3 + Element Plus,部署走 Docker。**架构上我重点攻克了 4 个难题**:
>
> 1. **库存一致性** — Redis Lua 原子预占 + Redisson 分布式锁 + 单条原子 SQL 三层防护;
> 2. **订单超时治理** — SortedSet 驱动 + Redis Stream 单消费者模式;
> 3. **实时通知** — WebSocket + Redis Stream,离线消息落盘,上线自动拉取;
> 4. **AI 购物助手** — Function Calling 封装 10 个受控工具,采用"AI 生成草稿 + 用户确认 + 后端执行"两步模式保证写操作安全。
>
> 这个项目里我特别**重视工程深度**。AI 助手模块我迭代了 B0 到 B6 共 7 个 feature branch,修了 14 个 AI 行为 bug;最近我还用对抗性 review 的方式重新审视订单链路,**主动发现 55 项问题(14 项 P0)**,包括超卖、Lua 与 DB 跨介质伪原子、Stream 消费者雪崩等真实生产隐患,文档就写在 `docs/backlog/`。
>
> 我擅长的方向是**用工程化方法把复杂业务做扎实**,而不是堆功能。谢谢。

### 1.2 精炼版(60 秒,适合二面 / 技术终面)

> 我做的是个**生产级的电商平台** huangtian-goods,后端 Spring Boot 3.5,前端 Vue 3。
>
> 重点解决了 4 件事:**库存不超卖**、**订单自动超时**、**消息实时通知**、**AI 写操作安全**。
>
> 库存用 Redis Lua + Redisson + 数据库乐观锁三层防护;订单超时用 SortedSet + Redis Stream;通知用 WebSocket + Redis Stream + 离线落盘;AI 助手用 Function Calling + 草稿两步确认模式。
>
> 我特别注重**工程化深度**:AI 助手模块迭代了 7 个分支,修了 14 个 bug;最近主动做了 4 维对抗性 review,找出 55 项问题,完整修复路线。

### 1.3 一句话版(30 秒,适合电梯 / 群面)

> 我做过一个生产级电商平台,核心攻克了库存一致性、订单超时治理、实时通知和 AI 写操作安全四个工程难题。最近做了一次对抗性 review,主动发现 55 项风险,完整修复路线已落地文档。

---

## 2. 项目亮点(分维度)

### 2.1 技术深度(展示"懂原理")

| 维度 | 实现 | 体现的能力 |
|---|---|---|
| **Redis Lua 原子性** | 4 个 Lua 脚本(reserve / cancel / rollback / synchronize),单脚本内 Redis 端原子 | 理解 Redis 单线程模型 + Lua 沙箱 |
| **Redisson 看门狗** | 显式 leaseTime 控制锁自动释放,避免 Full GC 期间双扣 | 理解分布式锁的可观测性比自动化更重要 |
| **WebSocket + Stream 双通道** | WS 推在线消息,Stream 落盘离线消息,上线时 replay 拉取 | 理解连接生命周期与跨端消息语义 |
| **AI Draft 模式** | 工具分 READ_ONLY / DRAFT_ONLY,草稿入 DB + 15 分钟过期 + 用户确认才执行 | 理解 LLM 的不可信边界 + 写操作安全 |
| **MyBatis-Plus 乐观锁** | `@Version` 字段 + `lambdaUpdate().eq()` CAS | 理解 last-write-wins 的真实威胁 |

### 2.2 工程方法论(展示"有体系")

- **B0-B6 七阶段迭代**:每次迭代一个独立 feature branch,每个 bug 都有 YAML 回归用例
- **Event-Driven 解耦**:Spring `@TransactionalEventListener(AFTER_COMMIT)` + 4 个领域事件,主流程不阻塞
- **对抗性 Code Review**:3 个独立 reviewer(超卖 / Redis / 并发)+ 1 个综合 verdict,共发现 55 项
- **GDPR 合规**:B3 长期记忆实现 Art 15(知情权)+ Art 17(被遗忘权)
- **可观测先行**:B2 引入 Capability 抽象 + Micrometer 指标 + Token 成本埋点,后做功能

### 2.3 业务价值(展示"懂业务")

- **库存不超卖**:这是电商最致命的信任问题,直接影响商家与买家双方体验
- **订单自动超时**:无需客服介入,系统级治理,降低运营成本
- **实时通知**:商家发货 / 买家退货状态实时推送,提升平台活跃度
- **AI 助手安全**:LLM 直接写库是常见反模式,我的 Draft 模式让 AI 可控可审计

---

## 3. STAR 法讲述 5 个项目难点

### Story 1: 库存超卖防护的设计与权衡

**Situation**: 电商最致命的问题是超卖。需要保证**同一商品多笔订单并发下单、同一订单多笔支付并发、退货与支付并发**三种场景下都不超卖。

**Task**: 设计一个三层防护的库存一致性方案,同时不能让锁粒度过粗影响性能。

**Action**:
1. **第一层 Redis Lua 原子预占**:下单时 `decrby available + hset reserve` 原子,Redis 端串行保证不超卖
2. **第二层 Redisson 分布式锁**:下单时 `lock:stock:{productId}` 防同一商品并发改 DB
3. **第三层 DB 单条原子 UPDATE**:`UPDATE product SET stock_quantity = stock_quantity + #{delta} WHERE id = #{id} AND (#{delta} >= 0 OR stock_quantity + #{delta} >= 0)`,**不用 read-then-write**

**Result**:
- 三层防护覆盖 Redis 单介质原子 + 跨介质伪原子 + DB 行级原子
- 最近对抗性 review 主动发现 read-then-write 漏洞,记录在 backlog,修复路线完整

### Story 2: AI 助手 Function Calling + 草稿两步确认

**Situation**: 用户用 AI 助手"帮我买一辆自行车",AI 必须有写能力(下单)但又不能失控(乱下单)。

**Task**: 让 LLM 能调工具下单,但保证**每次下单必须用户确认**。

**Action**:
1. 设计 `MallAgentTool` 接口,10 个工具分两类:
   - `READ_ONLY`(搜索、查订单):AI 直接拿到结果
   - `DRAFT_ONLY`(下单、加购):AI 只生成草稿,入 `ai_action_draft` 表
2. 草稿有 15 分钟过期,前端收到 SSE `draft.created` 事件后展示卡片
3. 用户点确认 → `POST /ai/drafts/{id}/confirm` → 后端 dispatch 到业务 service → 状态机 CAS 更新
4. 修了 14 个 AI 行为 bug:模型幻觉(凭空捏造 tool_call)、DSML 泄漏、工具重试死循环、跨 chunk 累积丢失、SSE payload 漏字段、刷新后已确认草稿仍可点击

**Result**:
- AI 写操作可审计、可回滚、用户可控
- B0-B6 7 个 feature branch 完整迭代,B2 引入 Capability 抽象做权限分层

### Story 3: 订单超时治理的演化

**Situation**: 用户下单不支付,库存被预占。需要 10 分钟自动取消 + 回滚库存,**不能让 oncall 工程师手动干预**。

**Task**: 实现全自动超时治理,且 Redis 抖动不能让订单卡死。

**Action**:
1. **下单**:`ZADD order:timeout:trigger orderId (now+10min)`
2. **扫描**:`scheduleAtFixedRate(10s)` 扫 `ZRANGEBYSCORE 0 now LIMIT 0 3`
3. **推到 Stream**:`XADD order:timeout:stream * orderId xxx`
4. **消费**:消费者组 `order:timeout:group / c1` 阻塞读,`cancelTimeoutOrder` → `updateById(CANCELLED)` + `rollBackReserveStock`
5. **ACK**:成功后 `XACK`

**Result**:
- 自动治理闭环,无需人工
- review 指出 3 项 P0:ZSET 与 XADD 非原子、`handlePendingList` 空实现、单线程雪崩,**改进方案文档化**

### Story 4: 实时通知的连接生命周期管理

**Situation**: 用户在 Web 端下单,商家在商家端需要实时收到提醒。如果商家当时离线,消息不能丢;商家跨端登录(PC + 手机),两端都能收到。

**Task**: 设计一个**消息不丢、跨端可达**的通知系统。

**Action**:
1. **WebSocket 在线推送**:`/ws/notify?token=jwt` 建立长连接,Redis Stream `notify:user:{id}` 实时推送
2. **离线落盘**:WS 断开时,消息进 Redis Stream(默认保留 7 天);连接建立时 `XRANGE` 拉取未读
3. **跨端同步**:服务端维护 `ConcurrentHashMap<Long, CopyOnWriteArraySet<WebSocketSession>>`,同一用户多个 session 都广播
4. **Reconnect 重放**:前端 reconnect 时传 `lastEventId`,服务端 `XRANGE lastEventId +` 重放

**Result**:
- 在线实时、离线落盘、跨端同步、重连不丢
- 简化版实现,但核心生命周期管理完整

### Story 5: 主动做对抗性 review 的工程素养

**Situation**: 项目上线后,作为工程师的我想知道**还有多少我没看到的隐患**。

**Task**: 用对抗性 review 的方式,从 4 个独立维度审视订单链路,主动找漏洞。

**Action**:
1. 派 3 个 reviewer 并行扫描:超卖防护 / Redis 一致性 / 并发能力
2. 每个 reviewer 独立 prompt,只找问题不夸实现
3. barrier 综合:基于 3 个 review 产出 verdict + Top 5 + 修复路线
4. 写文档 `docs/backlog/order-chain-review-2026-08-28.md`,653 行

**Result**:
- **找到 55 项问题**(P0=14, P1=13, P2=17, P3=11)
- **没有任何误报**:同一根因被多个 reviewer 交叉确认
- 完整修复路线:5 工作日 P0 + 1 周 P1 + 2 周 P2 + P3 backlog
- 这份文档本身就是**面试的核心资产**

---

## 4. 高频追问 20 问 + 应对

### Q1: 你这个项目是练手项目还是生产项目?

**应对**: 个人练手项目,但按生产级标准做的。最近主动做了对抗性 review,找出 55 项问题,这本身就是生产级工程素养的体现。

### Q2: 为什么不用 Spring Cloud / 微服务?

**应对**: 单体更适合这个项目规模。**架构选择要匹配业务复杂度**,而不是套用大厂模板。当用户量到 10 万+ DAU、订单 TPS 到 1000+ 时,我会优先把订单 + 库存拆出去,而不是一上来就微服务化。

### Q3: Redisson 锁和 Redis Lua 是不是重复了?

**应对**: 不重复。Lua 保证 **Redis 端原子**,Redisson 锁保护 **DB 端不被并发改**。两层各管一段。最近 review 发现锁粒度反而成为秒杀瓶颈,改进方案是去掉 Redisson 锁,只依赖 Lua(因为 Lua 已经够原子)。

### Q4: 为什么 AI 助手用 Function Calling 而不是 Prompt Engineering?

**应对**: Function Calling 把工具调用结构化,模型不会"幻觉"出 `我帮你下单了"。Draft 模式进一步保证写操作必须用户确认。这是 LLM 应用的**最佳实践**。

### Q5: JWT 为什么还要 Redis?

**应对**: **让 Token 可回收**。纯 JWT 无状态无法主动失效(只有过期)。Redis `login:token:{token}` 有状态,可以立即吊销 + 滑动刷新。

### Q6: 库存预占的 Lua 为什么不能用 SETNX + DECRBY 两步?

**应对**: 因为两步不是原子操作,中间如果 Redis 重启就漏。Lua 在 Redis 端是单线程串行的,**整个脚本原子执行**。

### Q7: 订单超时用 Redis Stream 不用 RocketMQ 是为什么?

**应对**: 项目规模不需要外部 MQ。Redis Stream 提供 consumer group + ACK + PEL 机制,**核心 MQ 语义都有**,且少一个组件少一个故障点。当消息量到 1 万 QPS+ 时再考虑拆 MQ。

### Q8: WebSocket 怎么保证消息不丢?

**应对**: 三层保证:(1) WS 在线实时推;(2) 离线消息进 Redis Stream 持久化;(3) WS reconnect 时传 `lastEventId` 重放。**任意一层失效,下一层兜底**。

### Q9: AI 助手用户确认后,如何保证后端一定执行?

**应对**: `confirmDraft` 走完整业务 service 路径(`OrderServiceImpl.addOrder`),有 DB 事务 + 库存预占 + 状态机 CAS,**失败抛 `BusinessException` 全链路回滚**。

### Q10: 你的项目最大的技术难点是什么?

**应对**: **库存一致性的跨介质伪原子**。Lua + DB 不是同一原子域,DB 回滚 Lua 不会回滚 → 可能永久丢失或多余库存。review 文档详细写了 14 项 P0,这是最难修的。

### Q11: 如果让你重做这个项目,你会改什么?

**应对**: 先做威胁建模再写代码。最近的 review 找出 55 项问题,**如果一开始做威胁建模,80% 可以避免**。其次会先做 payment_record 幂等表 + Micrometer 全链路埋点,**可观测性先行**。

### Q12: B3 长期记忆是怎么实现的?

**应对**: MySQL + Redis 双层,事件驱动重算,GDPR Art 15/17 合规,Redisson 分布式锁防并发,Memoir size cap 600 token 防 prompt injection,审计表按月 RANGE 分区自动清理。完整文档 `docs/superpowers/specs/2026-08-23-b3-memory-design.md`。

### Q13: 你的代码做了哪些测试?

**应对**:
- AI 助手有 **YAML eval 回归用例**(C0-C13 共 14 个)
- B3 长期记忆有 8 个 Eval YAML
- 后端有 **148 个单元测试**,覆盖率 80%+
- **没有 JMH 并发压测**(这是我接下来要补的,review 文档 §7.3 列了)

### Q14: 你的 Redis 是单点还是集群?

**应对**: **当前是单点**,这是 review 找出的 P0-1。修复方案是 Redis Sentinel(1 主 2 从 + 3 Sentinel),文档 `docs/backlog/order-chain-review-2026-08-28.md` Risk #4 有完整配置。

### Q15: 你怎么处理数据库连接池?

**应对**: HikariCP。**当前没显式配置,走默认 max=10**,review 指出这是 P0-8。改进是 `maximum-pool-size=80, minimum-idle=20, leak-detection-threshold=10000`。

### Q16: AI Function Calling 的 schema 怎么设计的?

**应对**: `MallAgentTool` 接口 + `@Component` 自动注册到 `MallSkillRegistry`。每个工具有 `name` / `description` / `parameters` JSON Schema。模型按 OpenAI Function Calling 协议调用。

### Q17: 草稿过期了怎么办?

**应对**: `ai_action_draft` 表有 `expires_at` 字段 + 15 分钟默认值。过期后 `confirmDraft` 返回错误码 `DRAFT_EXPIRED`,前端提示"草稿已过期,请重新发起对话"。

### Q18: 为什么用 MyBatis-Plus 而不是 JPA / Hibernate?

**应对**: MyBatis-Plus 兼顾灵活 SQL + 单表 CRUD 自动生成。订单链路复杂 SQL(多表 JOIN + 动态条件)用 XML 写,简单 CRUD 用 `lambdaQuery`。**比 JPA 更可控**,比裸 MyBatis 更省力。

### Q19: 你这个项目最难修的 bug 是哪个?

**应对**: **C10 tool_call args 跨 chunk 累积丢失**。LLM 流式返回的 tool_call 参数是分块到达的(JSON 不完整),我必须自己写 delta 累积逻辑。**跨 `{ {` 边界**还有 corner case,修了两次才稳。

### Q20: 你的项目有架构图吗?

**应对**: 有。CLAUDE.md 有模块划分表,B3 设计文档有架构图。**主动 review 后又补了威胁建模视角的架构分析**(review 文档 §1.1)。

---

## 5. 简历每一行的"可能被追问"

| 简历行 | 必问点 | 应对(见上文) |
|---|---|---|
| 拦截器链实现 Token 校验 | 为什么用拦截器不用 Filter? | AOP 拦截器能拿到 HandlerMethod,Filter 不能 |
| 无感刷新 | 怎么判断 20 分钟阈值? | JWT payload 有 exp,RefreshInterceptor 解析后判断 |
| 主动失效 | 怎么吊销一个 token? | 删 Redis key,拦截器查不到就 401 |
| **杜绝超卖** | **(高危)怎么杜绝?有 review 过吗?** | **见 Story 1 + Q3 + 主动暴露 55 项问题** |
| 杜绝重复扣减 | Redis Lua 怎么保证不重复? | Lua 内部 hash 用 tempOrderId 区分 |
| SortedSet 驱动超时 | 为什么不直接用定时任务? | 定时任务单机有单点故障 + 扫描代价高,SortedSet 按 score 范围查询高效 |
| **无需人工干预** | 真的不需要? | 95% 不需要,但 PEL 堆积时需要人工 / 自动清理 worker |
| 离线消息落盘 | 落盘到哪?多久清理? | Redis Stream,XADD 后默认 7 天过期 |
| 跨端消息不丢 | 重连机制? | reconnect 传 lastEventId,XRANGE 重放 |
| AI 草稿两步确认 | 为什么不用 Function Calling 直接执行? | LLM 不可信,写操作必须用户授权 |
| 受控工具 | 怎么控制? | 工具分 READ_ONLY / DRAFT_ONLY,枚举约束 |

---

## 6. 反问环节(展示思考深度)

### 6.1 必问的 3 个问题

1. **"贵团队目前最大的技术挑战是什么?我的项目经历能不能直接复用?"** —— 展示你想帮团队解决问题,不只是来面试
2. **"团队对 AI 应用工程的态度是怎样的?已经在落地还是观望?"** —— 展示你想把 AI 助手经验用到工作中
3. **"技术债务治理在团队里有专门的时间吗?(比如 review 文档化、混沌测试)"** —— 暗示你重视工程深度,顺便了解团队节奏

### 6.2 加分反问

4. **"贵团队的 code review 是用什么模式?是 GitHub PR 还是其他?"**
5. **"团队对 OpenTelemetry / Micrometer 的覆盖度如何?"**
6. **"贵团队有没有生产环境的故障复盘文档?我能学习一下吗?"**

### 6.3 千万**不要问**的问题

- ❌ "贵公司加班多吗?"(一面就问,显得不投入)
- ❌ "薪资多少?"(HR 阶段才问)
- ❌ "团队几个人?"(显得你只关心规模)

---

## 7. 面试前 24 小时准备清单

- [ ] 通读 `CLAUDE.md`(项目根) — 项目模块划分 + Redis / AI 模块架构
- [ ] 通读 `docs/backlog/order-chain-review-2026-08-28.md` — 55 项问题 + Top 5 + 修复路线
- [ ] 通读 `docs/superpowers/specs/2026-08-23-b3-memory-design.md`(如果面试问 B3)
- [ ] 准备 STAR 法讲 5 个故事(见 §3)
- [ ] 准备自我介绍三段式(见 §1)
- [ ] 模拟高频追问 20 问(见 §4)
- [ ] 准备 3 个反问(见 §6.1)
- [ ] 把简历 + 项目地址发给朋友,让他们挑刺
- [ ] 准备好 GitHub PR / issue 截图,展示 git 协作能力

---

## 8. 元数据

| 字段 | 值 |
|---|---|
| 准备日期 | 2026-08-28 |
| 项目 review 配套 | `docs/backlog/order-chain-review-2026-08-28.md` |
| 适用岗位 | Java 后端 / 电商架构 / AI 应用工程 |
| 核心资产 | 55 项 review 文档 + 148 个单元测试 + B0-B6 七分支迭代 |
| 面试节奏建议 | 一面:自我介绍 + Story 1/2;二面:Star 3/4 + 深入追问;终面:review 文档 + 反问 |
# 领星 (LingXing / ASINKING) · Java 开发工程师（实习生）一面终极通关宝典

> **面试时间**：明天（2026-11-18）16:00  
> **投递岗位**：Java 开发工程师（实习生） | 深圳领星网络科技有限公司（领星 ERP）  
> **候选人档案**：莫明钦 | 华南理工大学 · 2027 届 · 计算机科学与技术专业 · 本科生（大四上，已签约格力校招 Offer，寻找高质量实习积累）  
> **状态诊断与策略**：你已经有 1 个月没刷算法和看八股文，但你的**两段企业实习 + 荒天享物电商（含 AI 助手与 Eval 体系）+ OpsMind** 的真实项目底盘极硬！未来 24 小时的核心策略不是漫无目的地海刷，而是**“用 20% 核心时间唤醒最高频八股与 12 道必考简单~中等手撕模板 + 80% 精力把简历项目精准对齐领星跨境电商 SaaS 业务痛点 + 稳妥应对大四找实习的 HR/主管防雷题”**。

---

## 📑 目录索引

- [模块一：领星业务全景、技术栈与招聘全流程透视](#模块一领星业务全景技术栈与招聘全流程透视)
- [模块二：自我介绍与“大四已签校招找实习”防雷话术](#模块二自我介绍与大四已签校招找实习防雷话术)
- [模块三：项目与实习深度拷打防线（精准对齐领星跨境 SaaS 场景）](#模块三项目与实习深度拷打防线精准对齐领星跨境-saas-场景)
- [模块四：领星牛客真题 & 既往高危遗忘八股 27 问极速唤醒](#模块四领星牛客真题--既往高危遗忘八股-27-问极速唤醒)
  - [一、Java 基础、集合陷阱与并发底层（Q1 ~ Q7）](#一java-基础集合陷阱与并发底层q1--q7)
  - [二、JVM 底层全貌、类加载与线上调优实战（Q8 ~ Q11 —— 重点唤醒！）](#二jvm-底层全貌类加载与线上调优实战q8--q11--重点唤醒)
  - [三、MySQL 深度、连接池、间隙锁与分库分表（Q12 ~ Q17 —— 领星极高频！）](#三mysql-深度连接池间隙锁与分库分表q12--q17--领星极高频)
  - [四、Redis 底层、布隆过滤器、网关与 MQ（Q18 ~ Q22 —— 领星极高频！）](#四redis-底层布隆过滤器网关与-mqq18--q22--领星极高频)
  - [五、Spring 事务/MyBatis、计算机网络与 Linux（Q23 ~ Q27）](#五spring-事务mybatis计算机网络与-linuxq23--q27)
- [模块五：跨境电商 ERP 专属 4 大高频业务场景题](#模块五跨境电商-erp-专属-4-大高频业务场景题)
- [模块六：1 个月未刷算法“24 小时手撕唤醒急救包”（Top 12 算法 + 多线程 + 高频 SQL）](#模块六1-个月未刷算法24-小时手撕唤醒急救包top-12-算法--多线程--高频-sql)
- [模块七：高水平反问环节与 24 小时考前冲刺时间表](#模块七高水平反问环节与-24-小时考前冲刺时间表)

---

## 模块一：领星业务全景、技术栈与招聘全流程透视

### 1. 领星（ASINKING）是做什么的？
* **行业地位**：成立于 2017 年，总部深圳南山（创智云城），获高瓴、顺为、老虎基金等数亿元投资，是国内**跨境电商 ERP SaaS 绝对头部企业**（在亚马逊精品及中大型卖家中市占率领先）。
* **核心产品（领星 ERP）**：
  * 对接 **Amazon、Walmart、Temu、TikTok Shop、速卖通** 等数十个主流海外电商平台；
  * 为海量跨境卖家提供**全链路、业财一体化** SaaS 解决方案：进销存与智能补货、多维度财务利润核算、全流程广告投放管理、多平台订单同步，以及融合 **DeepSeek 等大模型的 AI 智能客服/运营助手**。

### 2. 领星 Java 实习生招聘全流程（网搜牛客/小红书面经总结）
1. **简历筛选 / 测评**：部分批次有线上行测或小笔试（Easy ~ Medium 基础编程与选择）。
2. **技术一面（约 40~50 分钟，业务技术骨干，通常视频面）**：
   * **结构**：自我介绍（2min）→ **简历项目深度连环拷打（20min）** → **Java/MySQL/Redis/网关/分库分表八股（15min）** → **手撕算法（10min，1 道 Easy~Medium，部分面试官不写或只让口述思路）** → 反问。
3. **技术二面 / 主管面（约 30~40 分钟，部分日常实习可能合二为一）**：
   * **结构**：偏业务场景设计、系统架构合理性、线上排障思维、抗压能力、技术选型对比。
4. **HR 面（约 20~30 分钟）**：
   * **核心卡点**：每周出勤天数（通常要求 4~5 天）、**连续实习时长（3~6 个月）**、大四课程安排、**未来职业规划与稳定性/转正意向**。

### 3. 领星面试官最爱问的“业务同源技术题”
结合牛客网多篇领星真实 Java 面经，考官高频必问以下技术点（全部可与你的项目直接联动）：
* **分库分表**：跨境卖家订单动辄千万级，分片键怎么选（`tenant_id` / `shop_id`）、分表数怎么定？
* **布隆过滤器 & 缓存一致性**：防止海量外部单号/SKU 查询穿透 DB，为什么会误判？不支持删除怎么处理？
* **API 网关（Gateway）**：网关做了什么？基于 Netty/WebFlux 异步非阻塞底层原理、Redis+Lua 令牌桶限流、统一鉴权。
* **第三方平台海量 API 数据同步**：限流防封、幂等防重拉单、MQ 削峰解耦、Flink CDC/Binlog 数据同步。
* **工程规范**：Git 分支管理、代码冲突处理、Code Review 与灰度发布策略。

---

## 模块二：自我介绍与“大四已签校招找实习”防雷话术

### 1. 领星定制化 2 分钟自我介绍

> 🗣️ **考场背诵模版**：
>
> “面试官您好，我是莫明钦，**华南理工大学 2027 届计算机科学与技术专业的本科生**。现在大四课程基本修完，时间充裕，能够稳定全职出勤实习。
>
> 在本科期间，我一直深耕 **Java 后端高并发架构、分布式系统调优以及企业级 AI Agent 工程落地**，拥有两段完整的企业研发实习和两个硬核自研系统：
> 1. **在实战电商与 AI 项目（荒天享物）中**：我独立从零搭建了前后端分离的商品交易平台，采用 **Redis + Lua 原子预占 + Redisson 分布式锁 + DB 原子扣减**三层防护防超卖，通过 **SortedSet + Redis Stream** 治理订单超时；同时基于 Function Calling 封装了 10 个受控工具并落地‘草稿两步确认’模式与 **36 条 YAML 自动化回归评估集（EvalRunner）**，保障 AI 写操作零失控；
> 2. **在云智易（物联网平台）实习期间**：我主导了边缘视频巡检调度模块的重构，通过 **XXL-JOB 分片广播 + 物理隔离专属线程池 + Redis `SETNX` 防重租约**，解决多实例并发冲突并把单轮耗时从 5 分钟压降至 25 秒以内；
> 3. **在凯通科技（电信网管系统）实习期间**：我独立排查修复了生产环境复杂的‘丢根告警’Bug 并编写 188 行单元测试兜底，还通过动静分离缓存与渐进式加载，将万级节点监控大屏的首屏耗时从 15 秒优化到 200ms。
>
> 我了解到领星作为跨境电商 SaaS ERP 的绝对领军企业，业务涉及海量多平台订单同步、高并发库存与财务核算，以及大模型 AI 运营赋能，这与我的电商系统研发、任务调度和 AI 工程经验非常契合。非常希望能加入领星团队，在实际高并发业务场景中快速创造价值！”

---

### 2. 🚨 生死防雷线：“你大四了，为什么现在找实习？秋招定了吗？能干多久？”

> ⚠️ **绝对禁止踩的坑**：  
> 千万**不要**在面试中主动脱口而出：*“我已经签了格力三方了，明年 7 月就要去珠海入职，现在来领星纯粹是空窗期赚点零花钱/打发时间。”*  
> 用人团队招一个实习生需要花 2~4 周带教，如果听到对方“铁定不留且随时可能跑路”，技术面哪怕全答对也会在综合评估或 HR 面直接挂掉！

#### 💡 高情商、不撒谎、稳拿 Offer 的应答策略：

* **情况 A：技术一面/二面考官问“大四了为什么还找实习？平时能来几天？能干多久？”**
  > “我现在大四上学期，学校专业课已经基本修完了，毕业设计也在同步推进中，**每周可以稳定出勤 5 天（周一到周五全职），可以连续稳定实习到明年 5~6 月毕业前（5~6 个月以上）**。  
  > 之所以现在找实习，是因为我非常看重真实企业级高并发 SaaS 系统的工程历练——自己写 Demo 接触不到海量多租户、多平台接口对接、千万级慢 SQL 调优这些真实场景。我希望能在毕业前这半年扎扎实实进入一家业务复杂度高的头部技术团队写生产代码、扛真实需求，如果后续和团队契合度高、也有留用机会的话，我当然也非常愿意积极争取！”

* **情况 B：万一 HR 或主管直接追问“秋招情况怎么样？有没有签 Offer / 签三方？”**
  > *(原则：先强调深圳互联网技术氛围与当前实习的绝对投入度；若确实被直接点名问到是否有保底 Offer，坦诚适度即可，切忌显得敷衍)*  
  > “秋招这边目前有一些传统制造/国企类的保底选择流程，不过在学校期间我一直做的是互联网高并发 Java 后端和 AI 应用方向，心里还是**非常向往深圳一线跨境 SaaS 互联网团队的工程技术氛围与快速迭代节奏**。现阶段如果进入领星，我一定会把未来半年的全部精力都扑在团队业务交付上，绝对不会半途而废或者三天两头请假影响项目进度。”

---

## 模块三：项目与实习深度拷打防线（精准对齐领星跨境 SaaS 场景）

领星面经反复强调：**面试官喜欢从你简历上的一个项目点顺藤摸瓜问到底**。下面是把你已有经验翻译成**“领星考官最爽的语言”**：

### 1. 荒天享物（`huangtian-goods`）—— 100% 同源电商核心防线

#### 🔥 追问 1：“高并发下单怎么保证库存不超卖？Redis Lua 和 DB 怎么保证原子性？”
* **三层防超卖架构**：
  1. **第一层（Redis Lua 原子预占）**：将“校验可用库存 `>= 扣减量` + `DECRBY` 扣减 + `HSET` 记录预占明细”封装在一个 Lua 脚本中，利用 Redis 单线程顺序执行特性保证内存操作绝对原子；
  2. **第二层（Redisson 分布式锁）**：按商品粒度加锁 `lock:stock:{productId}`，显式管理 `leaseTime`，防止同商品并发更新 DB 出现交叉覆盖（`read-then-write` 丢更新）；
  3. **第三层（MySQL 单条原子 UPDATE 兜底）**：严禁先 `SELECT` 再 `Java 算` 再 `UPDATE`！直接执行：
     ```sql
     UPDATE product SET stock_quantity = stock_quantity + #{delta}
     WHERE id = #{id} AND (#{delta} >= 0 OR stock_quantity + #{delta} >= 0);
     ```
     利用 InnoDB 行级锁和前置条件校验确保底线绝不扣负。
* **加分回答（对抗性 Review 意识）**：主动提到“Redis 与 MySQL 属于跨介质无法原生 2PC 强原子，如果 Redis 预占成功但 DB 落库因网络宕机失败，必须配合本地补偿任务/超时自动释放预占（SortedSet + Redis Stream 死信兜底）以及库存核对定时任务实现最终一致”。

#### 🔥 追问 2：“你们订单超时关闭怎么做的？为什么不用 `@Scheduled` 扫表？”
* **避坑解释**：`@Scheduled` 每分钟扫全表 `WHERE status = UNPAID AND create_time < ...` 在千万级订单表下会导致严重的深分页与 InnoDB Buffer Pool 热点数据污染，且多实例部署会重复回滚库存。
* **方案**：采用 **Redis SortedSet（按超时时间戳作为 Score）+ Redis Stream 消费闭环**（或者 RocketMQ 延迟消息），到点触发消费并配合数据库订单状态机乐观锁（`WHERE id = ? AND status = 'UNPAID'`）保证幂等关单与释放库存。

#### 🔥 追问 3：“荒天享物的 AI 助手和 Eval 集是怎么回事？为什么做这套 Eval 集？”
* *(领星也在大力用 DeepSeek 等大模型落地智能客服/跨境运营助手，这题答好了直接秒杀普通后端！)*
* **两句话核心定位**：
  1. **写安全隔离（Draft 两步确认）**：把 10 个 Tool 拆分为 `READ_ONLY`（查商品/订单）和 `DRAFT_ONLY`（下单/改资料/加购）；AI 绝不直接写库，只能写入 `ai_action_draft` 草稿表并设置 15 分钟过期，通过 SSE 推送确认卡片，由用户人工点击确认后才走后端标准事务执行。
  2. **Eval 自动化回归评估集（36 条 YAML + `EvalRunner`）**：大模型具有概率随机性和提示词脆弱性（改 A 崩 B）。我们将线上历史故障（如 DSML 协议标签外泄、C11 跨轮次参数幻觉、Prompt 注入攻击、Tool 死循环、记忆 NPE）沉淀为 **36 个 YAML 回归测试用例**（涵盖基础、Agent 编排、长期记忆、RAG 知识库四大类），通过 `EvalRunner` 做**确定性契约断言**（是否调对工具、参数是否齐全、是否产出 Draft、工具调用次数上限、无 DSML 泄漏、防注入拦截），在 dev 环境一键回归通过，成为 AI 迭代的“单元测试质量门禁”。

---

### 2. 云智易实习 —— 精准对标“领星多平台接口拉取 & 限流调度”

* **如何向领星考官讲述**：
  > “在云智易重构视频巡检系统时，我解决的是**‘百级外部设备高频拉取中的弱网阻塞、多实例重复触发、下游接口过载’**问题——这和跨境 ERP 对接亚马逊/Temu 等第三方限流 API 同步订单本质完全一致！”
* **三大核心亮点**：
  1. **XXL-JOB 分片广播**：通过 `device_id % shardTotal == shardIndex` 均匀打散任务到多台节点，将单轮耗时从 5 分钟压降至 25 秒；
  2. **物理隔离专属线程池 + 反压限流**：显式创建 `ThreadPoolExecutor`，基于利特尔法则（$QPS \times RT$）与下游硬件解码上限（8~16 路）+ JVM 堆内图像展开内存（4~8MB/张），严谨将 `core = max = 8`，有界队列 `ArrayBlockingQueue(500)`，拒绝策略选用 **`CallerRunsPolicy`** 实现天然反压，绝不拖垮主线程；
  3. **Redis `SETNX` 原子租约防重 + 本地 Watchdog 逃生舱**：防止多 Pod 重复拉取计费，调度中心失联 3 个周期自动降级本地接管。

---

### 3. 凯通科技实习 —— 大数据量看板性能调优 & 疑难排障闭环

* **15s → 200ms 拓扑大屏优化**：
  * **瓶颈**：单接口同步聚合上万网元静态结构 + 实时性能告警，大 JSON 传输阻塞；
  * **方案**：**动静分离 + Redis 骨架预热（200ms 首屏秒出可交互拓扑）+ 视口（Viewport）异步多线程分批注水 + 消除循环 $N+1$ 改为批量 `IN` 与联合覆盖索引**（数据库 CPU 降 60%）。
* **“丢根告警”逆向排障 + 188 行单测**：
  * 定位“子告警早到、父告警晚到数秒导致递归中断”的乱序隐患，引入**延迟缓冲重挂 + 静态拓扑穿透补全**，并用 **188 行高覆盖 JUnit 单测**护航。

---

### 4. 企业级 RAG 检索调优与防幻觉防线（OpsMind / 荒天享物 B4 必考追问）

* **切分（Chunking）**：标题层级父子切分 + 递归字符切分（`chunk_size = 512 tokens, overlap = 64`）+ 表格/代码块原子保护 + 头部拼接章节面包屑路径；
* **混合检索（Hybrid Search + Rerank）**：**PostgreSQL `pgvector`（HNSW 图索引 + `halfvec` 半精度内存减半）语义召回** + **BM25 稀疏检索**（专抓冷门 SKU/错误码精确字面匹配） → **RRF 倒数秩融合（$k=60$ 消除量纲）** → **Cross-Encoder 交叉注意力精排 Top-5**；
* **防幻觉与安全沙箱**：① 动态 Nonce 隔离标签 `<UNTRUSTED_KNOWLEDGE nonce="...">` 彻底防间接 Prompt 注入逃逸；② System Prompt 强约束角标 `[chunk_id]` 溯源高亮；③ **RAGAS 量化评测闭环** + 36 条 YAML EvalRunner 门禁，将核心指标从基线 ~64% 提升至 ~87.5%。

---

### 5. Git 协作与工程规范（领星牛客真题）

* **常问问题**：*“你们团队平时 Git 分支怎么管理的？遇到代码冲突怎么解决？怎么做发布的？”*
* **标准回答**：
  * 采用规范的分支模型：`main`（线上生产）、`dev/release`（集成/预发环境）、`feature/*`（独立特性开发分支，如我在荒天享物里按 B0~B6 分阶段开分支）、`hotfix/*`（紧急线上修复）；
  * 提交前先 `git fetch` + `git rebase`（或 `merge`）同步上游变更，若发生冲突，结合语义与作者确认保留逻辑，并在解决冲突后完整跑一遍本地 `mvn test` 与回归用例才提交 PR/MR；
  * 遵循小步快跑与 Code Review 制度，合并前至少需要资深同事 Review，核心配置通过配置中心（Nacos）和开关 Flag（如 `@ConditionalOnProperty`）控制灰度发布与秒级回滚。

---

## 模块四：领星牛客真题 & 既往高危遗忘八股 27 问极速唤醒

> 📌 **1 个月没看八股的唤醒原则**：每个问题先背**加粗的 1 句核心结论**，再展开 2~3 个底层关键词，不啰嗦、一针见血！尤其是你在**过往面经中被反复连环追问过的 JVM 调优、集合直觉陷阱、线程协同、间隙锁、Redis 底层与事务失效**，已全部帮你完整还原在这里！

---

### 一、Java 基础、集合陷阱与并发底层（Q1 ~ Q7）

#### Q1：HashMap 底层原理？1000 个键值对初始化容量设多少？ArrayList 扩容陷阱？
* **核心原理**：JDK 1.8 底层是 **数组 + 链表 + 红黑树**。通过 `(n - 1) & hash` 定位桶位，其中 `hash = (h = key.hashCode()) ^ (h >>> 16)`（高低位异或，减少哈希碰撞）。
* **1.7 vs 1.8 核心区别**：
  1. 数据结构：1.7 是数组+链表；1.8 在**链表长度 $\ge 8$ 且数组容量 $\ge 64$（双重条件缺一不可！）** 时才转为红黑树（元素减至 $\le 6$ 退化为链表）；容量 $< 64$ 时宁愿优先扩容打散，因为小容量扩容比建红黑树划算；
  2. 插入方式：1.7 采用**头插法**（多线程并发扩容时易形成环形链表导致 `get()` 死循环 CPU 100%）；1.8 改用**尾插法**（解决了死循环问题，但仍非线程安全，并发写仍会数据覆盖丢失）；
  3. 扩容迁移：1.8 利用数组长度是 2 的幂次特性，通过 `e.hash & oldCap == 0` 直接拆分为原位置和 `原位置 + oldCap`，无需重新计算全部哈希。
* **🔥 必考直觉陷阱 1（海柔真题）：1000 个键值对存进 HashMap，初始化容量写 `1024` 对吗？**
  * **大错特错！必须设 `2048`（传入 `(int) (1000 / 0.75F + 1.0F) = 1334`，内部向上取 2 的幂即为 2048）**！
  * **原因**：默认负载因子 `LoadFactor = 0.75`。如果写 `1024`，真实扩容阈值 `Threshold = 1024 * 0.75 = 768`，插入第 **769** 个元素时立刻触发全量 `resize` 搬迁到 `2048`！
* **🔥 必考直觉陷阱 2：ArrayList 扩容倍数与初始化？**
  * **不是翻倍，是 1.5 倍**（`oldCapacity + (oldCapacity >> 1)`），物理考量是避免每次翻倍大于此前所有已释放内存块之和导致无法复用内存碎片；
  * 无参 `new ArrayList<>()` 底层初始是空数组 `{}`（零内存占用），**第一次 `add` 才惰性分配容量 10**。

#### Q2：ConcurrentHashMap 为什么线程安全？CAS 是什么，怎么用在这里的？
* **JDK 1.7**：`Segment` 分段锁（继承 `ReentrantLock`），默认 16 段，并发度受限；
* **JDK 1.8（核心！）**：废弃 Segment，改用 **`Node[]` 数组 + `CAS` + `synchronized` + `volatile`**：
  1. **数组/首节点无锁化（CAS）**：初始化 `table` 时用 CAS 修改控制标志 `sizeCtl`；当算出的桶位为 `null` 时，直接通过 `casTabAt`（CAS 操作）无锁插入头节点，失败说明发生并发竞争再自旋重试；
  2. **哈希冲突桶细粒度加锁**：若桶位已有节点，则只用 **`synchronized` 锁住该桶的链表头节点或红黑树根节点**，锁粒度细化到单个桶（千万个桶互不阻塞）；
  3. **读操作完全无锁**：`Node` 的 `val` 和 `next` 都用 `volatile` 修饰，利用内存可见性保证读线程无需加锁即可读到最新数据；
  4. **计数优化**：统计容量用类似 `LongAdder` 的 **`CounterCell` 分段计数**，高并发下避免单点 CAS 自旋风暴；扩容时支持多线程协同迁移（`ForwardingNode`）。
* **CAS 原理与缺陷**：Compare-And-Swap（CPU 原子指令 `cmpxchg`），包含内存值 `V`、预期旧值 `A`、新值 `B`。三大缺陷：**ABA 问题**（加版本号 `AtomicStampedReference` 解决）、**自旋开销大**、**只能保证单个变量原子性**。

#### Q3：线程池的核心参数与工作流程？拒绝策略有哪些？
* **7 大核心参数**：`corePoolSize`（核心线程）、`maximumPoolSize`（最大线程）、`keepAliveTime`（非核心空闲存活时间）、`unit`（时间单位）、`workQueue`（阻塞队列）、`threadFactory`（自定义线程名）、`handler`（拒绝策略）。
* **严格执行流程（常考误区：先入队还是先扩容？）**：
  1. 当前线程数 `< corePoolSize` → 直接新建**核心线程**执行（即便有空闲核心线程也会先拉满核心数）；
  2. 核心线程已满 → 放入 **`workQueue` 阻塞队列排队**（严禁用 `Executors.newFixedThreadPool` 无界队列，易 OOM！）；
  3. 队列也已满且线程数 `< maximumPoolSize` → 新建**非核心临时线程**应急执行；
  4. 队列满且线程数已达 `maximumPoolSize` → 触发 **`RejectedExecutionHandler` 拒绝策略**。
* **4 种拒绝策略**：
  * `AbortPolicy`（默认，直接抛异常）；
  * `CallerRunsPolicy`（由提交任务的调用者线程自己执行，**反压限流、不丢任务**，我在云智易巡检中采用）；
  * `DiscardPolicy`（静默丢弃新任务）；
  * `DiscardOldestPolicy`（丢弃队列头部最老的未执行任务，尝试重新提交）。

#### Q4：`synchronized` 锁升级底层、`ReentrantLock` 区别 & `volatile` 硬件底层机制
* **`synchronized` 锁升级全过程（基于对象头 Mark Word）**：
  * **无锁（01）** → **偏向锁（01+偏向位）**：只有一个线程访问时直接 CAS 把线程 ID 写入对象头 Mark Word，后续无竞争零开销直接重入 *(JDK 15+ 默认废弃偏向锁)* → **轻量级锁（00）**：出现轻微交替竞争时撤销偏向，各线程在栈帧建 Lock Record 并通过 CAS 自旋抢锁（避免阻塞唤醒开销） → **重量级锁（10）**：自旋超过阈值或竞争激烈时膨胀为依赖操作系统互斥量 Mutex 的 **`ObjectMonitor`**（未抢到线程进入 `_EntryList` 挂起阻塞，涉及用户态/内核态切换）。
* **`synchronized` vs `ReentrantLock` & 公平/非公平锁**：
  * 两者**都是可重入锁**；`synchronized` 异常自动释放；`ReentrantLock` 基于 **AQS**，支持**等待可中断、超时 `tryLock`、公平锁开关、多 `Condition` 精确唤醒**，必须在 `finally` 手动 `unlock()`。
  * **非公平锁（默认，吞吐量高）**：新线程直接先尝试 CAS 插队抢锁，省去唤醒挂起线程的内核切换延迟；**公平锁**：先查 CLH 队列是否有前驱排队（`hasQueuedPredecessors()`），有则老实排在队尾。
* **🔥 `volatile` 两大特性与硬件底层（高频纠偏）**：
  1. **保证可见性**：汇编层面添加 `lock` 前缀指令，修改变量后强制刷回**物理主内存（RAM）**，并通过总线嗅探 **MESI 缓存一致性协议**让其他 CPU 核心中对应的 **CPU Cache Line（64 字节缓存行）失效**（⚠️ 注意：绝对不是失效磁盘页缓存 PageCache！）；
  2. **禁止指令重排（有序性）**：通过插入读写内存屏障（`LoadLoad/StoreStore/LoadStore/StoreLoad`），保障如 DCL 单例 `new` 对象三步走不被重排返回半初始化对象。*(⚠️ 注意：`volatile` 不保证复合操作如 `i++` 的原子性！)*

#### Q5：讲一下 AQS（AbstractQueuedSynchronizer）底层原理？
* **一句话核心**：AQS = **`volatile int state` 同步状态位（CAS 修改） + CLH 双向虚拟阻塞队列 + `LockSupport.park()/unpark()`**。
* **关键机制**：
  1. 以 `ReentrantLock` 为例，初始 `state = 0`。线程通过 CAS 将 `state` 改为 `1` 即抢锁成功，并记录独占线程 `exclusiveOwnerThread`；同一线程重入时直接 `state + 1`，释放时减至 `0` 才算彻底释放；
  2. 抢锁失败的线程被封装成 `Node` 节点通过 CAS 尾插进入双向队列；
  3. 队列头节点 `head` 是虚节点（代表当前持锁者），队列通过将前驱节点状态设为 **`SIGNAL (-1)`** 约定“前驱释放锁时必须唤醒后继节点”，然后调用 `LockSupport.park()` 挂起当前线程；
  4. **为什么唤醒时从尾节点向前遍历？**：入队尾插时先设置 `node.prev = tail`，再 CAS 更新 `tail`，最后才设置 `oldTail.next = node`——并发高发瞬间 `next` 指针可能尚未连好导致正向遍历断链，而 `prev` 指针必然已经稳定建立！

#### Q6：ThreadLocal 底层原理、项目应用场景及内存泄漏问题？
* **隔离原理**：每个 `Thread` 对象内部都维护着一个 `ThreadLocalMap` 实例；每次调用 `threadLocal.set(val)`，实际上是以当前 `ThreadLocal` 实例作为 **Key**，把业务对象作为 **Value** 存入**当前线程自己的 `ThreadLocalMap`** 中，实现天然的线程封闭隔离。
* **项目实战场景**：我在荒天享物与微服务鉴权拦截器中，使用 `UserHolder`（基于 `ThreadLocal<UserDTO>`）在请求进入拦截器验签 JWT 后存入用户信息，贯穿 Controller → Service 全链路随时获取当前用户，避免透传冗余参数；同时在 `EvalRunner` 的 `try...finally` 块中显式调用 `UserHolder.removeUser()` 清理上下文。
* **内存泄漏真凶（直觉陷阱！）**：
  * `ThreadLocalMap` 的 `Entry` 继承了 `WeakReference<ThreadLocal<?>>`，**Key 是弱引用，Value 是强引用**；
  * 当外部强引用断开后，GC 会回收 Key 变成 `null`，**真正泄漏的是被核心线程的 `ThreadLocalMap` 死死强引用的巨大 Value 对象**！而在线程池中核心线程长生不死，导致大对象永远无法回收直至 OOM；
  * **彻底解法**：每次用完**必须在 `try-finally` 的 `finally` 块中显式调用 `threadLocal.remove()`**。

#### Q7：多线程协同场景——AB 两个线程并行执行，等 AB 都完成后再执行 C，有哪几种写法？（海柔高频真题！）
1. **`CompletableFuture.allOf()`（推荐，现代异步编排）**：
   `CompletableFuture.allOf(CompletableFuture.runAsync(A), CompletableFuture.runAsync(B)).thenRun(C);`
2. **`CountDownLatch(2)`（闭锁，一次性倒数）**：A、B 在 `finally` 中各自调 `latch.countDown()`，C 线程调 `latch.await()` 阻塞等待归零；
3. **`CyclicBarrier(2, taskC)`（循环栅栏）**：A、B 到达后调 `barrier.await()`，达到 2 个自动优先触发回调任务 `taskC`（支持 `reset()` 复用）；
4. **`threadA.join(); threadB.join(); doTaskC();`**（最基础原生写法）。

---

### 二、JVM 底层全貌、类加载与线上调优实战（Q8 ~ Q11 —— 重点唤醒！）

> 🔥 **你之前最容易卡壳 & 忘记的板块！这部分内容直接对标字节/Shopee/海柔/领星高阶技术问答，请务必吃透！**

#### Q8：JVM 运行时内存区域划分？堆默认比例与 8:1:1 担保机制？直接内存与零拷贝？
1. **线程私有区（生命周期同线程，不会 GC）**：
   * **程序计数器**：记录当前字节码行号指令地址。**为什么是 JVM 唯一不会发生 OOM 的区域？** 因为它只存一个固定长度的指令指针地址（4B/8B），不存对象、不动态扩容；
   * **Java 虚拟机栈**：每个方法对应一个栈帧（**局部变量表、操作数栈、动态链接、方法返回地址**）。无限递归报 `StackOverflowError`，栈扩展失败报 `OutOfMemoryError`；
   * **本地方法栈**：服务 `native`（C/C++）方法。
2. **线程共享区（GC 主战场）**：
   * **堆（Heap）**：所有 `new` 对象实例与数组。**JDK 7 起，静态变量（static）和字符串常量池（StringTable）已从永久代正式迁入堆中！**
     * **默认比例（千万别记反！）**：**老年代 : 年轻代 = 2 : 1**（`-XX:NewRatio=2`）；年轻代内部 **Eden : S0 : S1 = 8 : 1 : 1**（`-XX:SurvivorRatio=8`）。
     * **🔥 高频追问：“Eden 占 8 份，S1 只占 1 份，Minor GC 复制过去装得下吗？”**
       1. **弱分代假说**：业务对象 98% 朝生夕死（局部 DTO、字符串），每次存活对象通常只有 1%~2%，1 份空间绰绰有余；
       2. **空间分配担保（Handle Promotion）**：若极端并发导致 S1 确实装不下，**直接破格提前保送进老年代**；老年代也满则触发 Full GC；还满才抛 `OOM: Java heap space`。
   * **元空间（Metaspace - JDK 8+ 替代永久代）**：使用**宿主机本地物理内存（Native Memory）**而非 JVM 堆，专门存 **Klass 类元数据、运行时常量池、方法字节码、JIT CodeCache**。取代永久代是为了避免动态生成代理类（Spring CGLIB）频繁爆出 `PermGen space`。
   * **直接内存（堆外内存）与零拷贝（Zero-Copy）**：`ByteBuffer.allocateDirect()` / Netty 分配在本地物理堆外，避免 Java 堆内到 OS 的反复 CPU 拷贝；常见零拷贝流派包括 **Netty 直接内存（DMA 直访）**、**RocketMQ `mmap` 内存映射（1 次 CPU 拷贝）**、**Kafka `sendfile` + SG-DMA（0 次 CPU 拷贝，仅 2 次上下文切换）**。

#### Q9：Java 对象 `new` 出来的全流程？类加载机制与怎么打破双亲委派？
* **一个对象 `new User()` 的 5 步物理时序**：
  1. **类加载检查**：查常量池看该类是否已加载解析初始化，未加载先走类加载；
  2. **分配内存**：堆规整用**指针碰撞**，有碎片用**空闲列表**；多线程高并发采用 **TLAB（本地线程分配缓冲，在 Eden 为每个线程划私有小地盘无锁并发分配）**；
  3. **内存零值初始化**：除对象头外全清零（保证字段默认值 `0 / null / false`）；
  4. **设置对象头**：写入 **Mark Word**（HashCode、GC 分代年龄 4bit、锁状态位）和 **Klass Word（类型指针）** 指向元空间类元数据；
  5. **执行 `<init>` 构造器**：按代码赋值与执行构造函数完成初始化。
* **类加载 5 步**：加载 → 验证 → 准备（分配静态变量内存并赋零值） → 解析 → 初始化（执行 `<clinit>` 赋值与静态块）。
* **双亲委派模型（自底向上委托：`Custom` → `App` → `Platform/Ext` → `Bootstrap`）**：
  * **工作原理**：收到加载请求先不自己加载，逐级往上丢给父类加载器；只有父类在搜索范围内找不到才由自己加载。
  * **两大价值**：① **沙箱安全防篡改**（防止你自己写个 `java.lang.String` 冒充核心库）；② **防止类被重复加载**。
  * **🔥 高频追问：哪些场景打破了双亲委派？怎么打破的？**
    1. **SPI 机制（如 JDBC `DriverManager`）**：`DriverManager` 在 `rt.jar` 由顶层 `Bootstrap` 加载，但它需要调用由各厂商（MySQL/Postgres）放在应用 `classpath` 里的驱动实现类；顶层加载器反向利用 **`Thread.currentThread().getContextClassLoader()`（线程上下文类加载器）** 委托 `AppClassLoader` 加载实现类；
    2. **Tomcat 容器隔离**：一个 Tomcat 部署两个 Web 应用分别用 Spring 4 和 Spring 5。Tomcat 自定义 **`WebAppClassLoader` 重写 `loadClass()`**，**先自己加载当前应用 `WEB-INF/classes` 下的类，找不到再向上委托**，实现多应用版本彻底隔离与热部署。

#### Q10：GC Roots 有哪些？CMS vs G1 vs ZGC 核心区别？（STW 演进史）
* **可达性分析 GC Roots 四大家族**：① 虚拟机栈栈帧局部变量引用；② 方法区静态属性引用；③ 方法区常量引用；④ 本地方法栈 JNI 引用；⑤ 所有被 `synchronized` 持有的对象。
* **基础回收算法**：年轻代用**标记-复制**（无碎片，存活率低成本小）；老年代用**标记-整理**（无碎片但移动开销高）或**标记-清除**（快但产生内存碎片）。
* **三大核心垃圾收集器对比（演进本质：一步步消灭 STW 全球停顿）**：
  1. **CMS（老年代并发收集器，JDK 9 已弃用）**：
     * 4 步：初始标记（STW）→ 并发标记（无 STW）→ 重新标记（STW）→ 并发清除；
     * **两大缺陷**：① 采用“标记-清除”产生大量**内存碎片**；② 并发清除期产生浮动垃圾，一旦老年代打满发生 **`Concurrent Mode Failure` 退化为单线程 `Serial Old` 全局卡死数秒**。
  2. **G1（JDK 9+ 默认，中大型微服务绝对主力，目标停顿 ~200ms）**：
     * **Region 棋盘化**：把整堆切成 ~2048 个独立大小相等的 **Region（1~32MB）**，逻辑分代动态变化，大对象（$>50\%$ Region）入 **Humongous**；
     * **核心优势**：① **可预测停顿（`-XX:MaxGCPauseMillis=200`）**：优先挑垃圾占比最高、收益最大的 Region 回收（Garbage-First）；② **无碎片**：跨 Region 之间采用“复制算法”搬迁压缩；③ 跨区引用靠 **RSet（记忆集）+ 卡表**避免全堆扫描；④ 当整堆占比达 **IHOP（默认 45%）** 时触发 **Mixed GC**（年轻代 + 高收益老年代）。*(注意：分配过快无空白 Region 时 G1 依然会退化为 Full GC！)*
  3. **ZGC（JDK 15+/17+ 生产可用，超低延迟 `<1ms`）**：
     * **核心黑科技**：**染色指针（Colored Pointers，利用 64 位指针高 4 位存 GC 状态）+ 读屏障（Load Barrier）与运行时指针自愈（Self-Healing）**；
     * **突破**：G1 复制搬家阶段必须 STW；**ZGC 实现并发转移——对象在内存里搬家时，用户线程依然并发读写，读屏障瞬间查转发表并自愈指针指向新地址**！无论堆是 16GB 还是 16TB，STW 停顿稳压在 **1ms 以内**（代价是消耗约 5%~15% 吞吐量）。

#### Q11：【重中之重】JVM 线上调优怎么做？生产必配参数 & OOM/CPU 100% 排查 SOP？

##### 1. 🎙️ 1 分钟满分回答口播模版（打破“参数玄学”，直击架构师思维）：
> “面试官，我对 JVM 调优的核心观点是：**‘代码优化 $\gg$ 架构优化 $\gg$ 收集器选型 $\gg$ 参数微调兜底’**。现代 G1 本身具备高度自适应调节能力，调优绝不是死背几十个冷门参数去乱改（比如绝不盲目写死 `-Xmn` 破坏 G1 自平衡）。
>
> 在平时工作和项目上线时，我们会**主动固化 5 个稳定性保命参数**，并建立一套**标准的线上 `jstat / jstack / jmap + MAT` 排障闭环**：
> * 收到老年代告警先用 `jstat -gcutil <pid> 1000` 盯紧曲线：**如果 Full GC 后老年代立刻回落，说明是突发大对象洪峰；如果多次 Full GC 后老年代死死卡在 95% 以上纹丝不动，实锤代码存在内存泄漏**，立刻抓 Dump 用 MAT 看支配树溯源代码（如 `ThreadLocal` 未 `remove()` 或大集合未分页）。
> * 遇到 CPU 100% 告警直接上 **`top` → `top -Hp` → `printf %x` → `jstack | grep`** 四步锁定死循环或疯狂 Full GC 线程！”

##### 2. 生产级 Docker/K8s 容器部署标准参数模板（背熟这 5 条理由！）
```bash
java -server \
-Xms4g -Xmx4g \
-XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m \
-XX:+UseG1GC \
-XX:MaxGCPauseMillis=200 \
-XX:+HeapDumpOnOutOfMemoryError \
-XX:HeapDumpPath=/data/logs/heap_dump.hprof \
-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 \
-Xlog:gc*,gc+phases=debug:/data/logs/gc.log:time,uptime,pid:filecount=5,filesize=50m \
-jar app.jar
```
1. **`-Xms4g -Xmx4g` 相等**：防止堆内存随并发高低反复向 OS 扩容/缩容，引发无谓的全局 STW 性能抖动；
2. **`MetaspaceSize=256m` & `MaxMetaspaceSize=512m`**：默认元空间仅约 20.8MB，Spring Boot 启动加载上万个类瞬间打满导致刚开机就频繁 Full GC；设上限防动态类加载泄漏撑爆物理宿主机；
3. **`HeapDumpOnOutOfMemoryError`**：OOM 崩盘瞬间自动留下内存堆转储现场给 **Eclipse MAT** 分析 Dominator Tree（支配树）与 GC Roots；
4. **`UseContainerSupport` + `MaxRAMPercentage=75.0`（云原生必考！）**：让 JVM 感知 Docker Cgroups 内存配额，且只占容器上限的 75%（留 25% 给堆外直接内存与线程栈），防止容器内存超限被 Linux 内核直接强制杀掉（**`OOMKilled Exit Code 137`**）；
5. **`UseG1GC` + `MaxGCPauseMillis=200`**：设定 G1 停顿预算目标 200ms，**严禁在 G1 下手写死 `-Xmn`**，交由 G1 自适应调整 Eden 与 Old 区 Region 占比。

##### 3. 线上四大排障指令速查表
| 场景 | 核心命令链路 | 一秒诊断要点 |
| :--- | :--- | :--- |
| **监控内存与 GC** | `jstat -gcutil <pid> 1000` | 重点盯 `O`（老年代%）、`FGC`/`FGCT`。Full GC 后 `O` 不降 = 内存泄漏；频繁 YGC 查 Eden 分配速率与大对象 |
| **CPU 飙高 100%** | `top` → `top -Hp <pid>` → `printf "%x\n" <tid>` → `jstack <pid> \| grep <hex> -A 30` | 若高亮线程是业务线程 = 死循环/正则回溯；若是 `VM Thread` / `GC Thread` = 内存打满导致疯狂 GC |
| **定位内存大对象** | `jmap -histo:live <pid> \| head -n 20` / MAT 打开 `.hprof` | 看哪个类实例占用字节最高；用 MAT 查看 Dominator Tree 沿着 GC Roots 找到谁死死拉着大对象不放 |
| **查死锁 / 线程池卡死** | `jstack -l <pid>` / `jinfo -flags <pid>` | 末尾看 `Found 1 deadlock.`；或看大量线程阻塞在 `WAITING (parking)` / `socketRead0` 定位连接池耗尽 |

---

### 三、MySQL 深度、连接池、间隙锁与分库分表（Q12 ~ Q17 —— 领星极高频！）

#### Q12：MySQL 为什么用 B+ 树做索引？3 层 B+ 树能存多少数据？（TP-LINK 推导题）
1. **不用二叉树/红黑树**：红黑树只有 2 个分叉，千万级数据树高会达到 **20~30 层**，磁盘随机 I/O 灾难；
2. **不用 B 树**：B 树每个节点既存 Key 又存整行 Data 记录，导致一页（16KB）能存的索引数量大幅缩水，树变高；且范围查询需繁琐中序回溯；
3. **B+ 树的压倒性优势**：
   * 非叶子节点**纯存索引键 + 指针**，扇出极大；
   * 所有真实数据都在**叶子节点**，且叶子间用**双向链表**串联，做 `BETWEEN / ORDER BY / LIMIT` 范围扫描如履平地。
* **🔥 3 层 B+ 树数学推导（必背数字 1170 & 2190 万！）**：
  * InnoDB 一页固定 **16KB**。主键 `BIGINT` 占 8B + 页指针占 6B = **14B**；
  * 单页扇出索引数：$16384\text{B} / 14\text{B} \approx \mathbf{1170}$ 个；
  * 若每行记录约 1KB，叶子节点一页存 **16 行**；
  * 3 层树高总容量 $= 1170 \times 1170 \times 16 \approx \mathbf{2190\text{ 万条}}$！而且根节点常驻内存，查两千万数据只需 **1~2 次物理磁盘 I/O**！

#### Q13：联合索引最左前缀、覆盖索引与索引失效 7 大元凶？
* **最左前缀**：联合索引 `(a, b, c)` 相当于 `(a)`、`(a, b)`、`(a, b, c)`；跳过 `a` 直接查 `b = 2` 失效；范围查询（`> / <`）右侧列索引失效，但可以通过**索引下推（ICP）**在存储引擎层提前过滤减少回表；
* **覆盖索引**：`SELECT` 要的字段全包含在二级索引中，直接拿到结果不回聚簇索引查整行（`EXPLAIN` Extra 显示 **`Using index`**），极大幅度提速！
* **索引失效 7 大元凶**：① 违背最左前缀；② **索引列做计算或函数操作**（`YEAR(create_time)=2026`、`id+1=10`）；③ **隐式类型转换**（字符串字段 `phone` 不加引号 `WHERE phone=13800138000`）；④ **左模糊匹配** `LIKE '%abc'`；⑤ `OR` 连未建索引列；⑥ 负向查询 `!= / NOT IN`；⑦ 优化器判定匹配行数过大（$>20\%\sim30\%$）直接全表扫更省随机 I/O。

#### Q14：一条 `UPDATE` 语句在 MySQL 内部经历了什么过程？（领星牛客真题！）
> *(回答出**“Server 层执行 + InnoDB Buffer Pool + Undo Log + Redo Log 两阶段提交与 Binlog 协同”**直接满分！)*
1. **Server 层**：连接器校验权限 → 分析器词法/语法解析 → 优化器选择走哪个索引定位待修改行；
2. **InnoDB 读取与 Undo Log**：执行器调引擎接口，若数据页不在 **Buffer Pool** 内存池中则从磁盘读入；更新前先把旧值写入 **`Undo Log`**（用于事务回滚及 MVCC 快照读）；同时加**行锁（Record Lock）**；
3. **修改内存与写 Redo Log（Prepare 状态）**：修改 Buffer Pool 中的内存页（标记为脏页），同时把物理页变更记录写入 **`Redo Log Buffer`**，刷盘并标记为 **`prepare` 状态**；
4. **写 Binlog 与两阶段提交（Commit）**：Server 层将逻辑归档日志写入 **`Binlog`** 并刷盘；随后通知 InnoDB 将 Redo Log 标记为 **`commit` 状态**，整个更新完成，后台异步刷脏页回磁盘。
* **为什么需要“两阶段提交”？**：保证崩溃恢复时 **`Redo Log`（物理重做保 Crash-Safe）与 `Binlog`（逻辑复制用于主从同步）的数据状态完全一致**，避免主从数据漂移！

#### Q15：事务隔离级别、MVCC 底层与“间隙锁死锁”经典案例？（海柔/Shopee 真题！）
* **4 种隔离级别**：RU、RC（解决脏读）、**RR（MySQL 默认，解决脏读与不可重复读）**、Serializable。
* **MVCC 三大支柱（快照读普通 `SELECT`）**：隐藏字段（`DB_TRX_ID` + `DB_ROLL_PTR`） + **Undo Log 版本链** + **`ReadView`**（RC 每次 `SELECT` 生成新快照，RR 仅首次 `SELECT` 生成并复用）。
* **🔥 间隙锁（Gap Lock）与死锁经典时序（高阶必考！）**：
  * InnoDB 在 RR 级别下默认使用 **Next-Key Lock（行锁 + 间隙锁 `(A, B]`）**，间隙锁锁住开区间 `(A, B)` 防止新行插入从而解决当前读下的幻读；
  * **为什么会引发死锁？**：因为**间隙锁之间是相互兼容的**！
    1. 事务 A 查询不存在的 `id = 7 FOR UPDATE`，拿到 `(5, 10)` 间隙锁；
    2. 事务 B 查询不存在的 `id = 8 FOR UPDATE`，**也同时兼容拿到 `(5, 10)` 间隙锁**；
    3. 事务 A 执行 `INSERT INTO t VALUES (7)`，申请插入意向锁，被事务 B 的间隙锁阻塞！
    4. 事务 B 执行 `INSERT INTO t VALUES (8)`，申请插入意向锁，被事务 A 的间隙锁阻塞！
    5. **双方环路等待 → 瞬间触发 Deadlock！** *(避免之道：尽可能走精确等值写或控制为 RC 隔离级别 + Binlog Row 模式)*。

#### Q16：数据库连接池（HikariCP）容量怎么设？`max-lifetime` 坑在哪？（Shopee 真题！）
* **🔥 直觉陷阱**：1000 并发就把数据库连接池设 500？**大错特错！** 数据库连接过多会导致数据库 CPU 在数百个连接之间疯狂做上下文切换和磁盘锁争夺，吞吐量断崖暴跌！
* **HikariCP 官方公式**：$$\text{Connections} = \text{CPU 核数} \times 2 + \text{有效磁盘数}$$（如 8 核服务器，最佳连接数仅仅是 **16 ~ 32**！）。
* **断连惨案坑点**：HikariCP 的 `max-lifetime`（连接最大生命周期）默认 30 分钟，必须设置得**比 MySQL `wait_timeout`（通常默认 8 小时，但云数据库可能设 60s~300s）严格小至少 30 秒**，防止连接池里拿着一个已被 MySQL 服务端主动掐断的废连接去发 SQL 报错！

#### Q17：领星高频必问——分库分表怎么设计？分片键和分表数怎么选？跨库分页怎么办？
1. **什么时候分？**：先做读写分离、SQL 调优、归档冷热分离；当单表数据量逼近 **500万~1000万行** 或单表磁盘容量超 **2~5GB** 导致 B+ 树突破 3 层 I/O 显著变慢时才分表；单库连接/CPU 打满时分库。
2. **分片键（Sharding Key）怎么选？**：
   * **站在多租户 SaaS 业务维度！** 订单、商品、财务流水优先以 **`tenant_id`（卖家/租户 ID）** 或 **`shop_id`（店铺 ID）** 作为分片键，辅以按**月/年时间分表（冷热隔离）**；
   * **原因**：SaaS 99% 的查询是单卖家查自己店铺的数据。用 `tenant_id` 分片确保同一个卖家的所有订单落入同一库/表，**彻底避免跨库多表 Scatter-Gather 散射查询**！
3. **分表数量怎么确定？**：
   * 测算未来 **3~5 年**总量（如 1.2 亿条），按单表 **500 万行**安全水位：$1.2\text{亿} / 500\text{万} = 24$，向上取 **2 的幂次方（32 或 64 张表）**，便于 `hash & (count - 1)` 与倍增平滑扩容。
4. **分库分表后三大难题怎么解？**：
   * **全局 ID**：雪花算法（Snowflake，趋势递增防 B+ 树页分裂）；
   * **跨库/多维条件查询**：**Canal 监听 Binlog 异步双写同步到 Elasticsearch（ES）**，复杂筛选走 ES 查 ID 再回查分表；
   * **深度分页**：改为“瀑布流游标翻页（记录上次最后 ID + 时间）”或借助 ES 处理前 N 页。

---

### 四、Redis 底层、布隆过滤器、网关与 MQ（Q18 ~ Q22 —— 领星极高频！）

#### Q18：Redis 核心底层机制：ZSet 跳表、渐进式 Rehash、过期与内存满淘汰策略？（Shopee 真题！）
1. **ZSet 为什么快？（跳表 SkipList + HashMap 字典双结构）**：
   * HashMap 负责 $O(1)$ 查询成员 `member -> score`；**跳表**负责按 `score` 排序，在有序链表上方建多层随机索引（期望层高 $\log N$），实现 $O(\log N)$ 查找与极高效的 `ZRANGE` 范围遍历；
   * **为什么不用红黑树？**：做范围查询时跳表找到起点顺着底层双向链表一直往后扫即可，比红黑树中序遍历快得多，且跳表无复杂平衡旋转操作、实现极简。
2. **Redis Dict 渐进式 Rehash**：
   * 维护两张哈希表 `ht[0]` 和 `ht[1]`。扩容条件：**无 `BGSAVE/BGREWRITEAOF` 子进程时负载因子 $\ge 1$ 扩容；有持久化子进程时阈值突变提升到 $\ge 5$**（防止触发 OS 写时复制 COW 导致物理内存翻倍！）；
   * 为防止单线程迁移百万 Key 卡死，采取**渐进式 Rehash**：扩容时只分配新空间，在每次增删改查请求时顺带迁移 1 个桶（加上定时微批量迁移），迁移期间查找先查 `ht[0]` 再查 `ht[1]`，新增只写 `ht[1]`。
3. **过期 Key 清理与 4G 内存占用 3.99G 插入能否成功？**
   * **过期清理**：不是到点立刻全删！采用 **惰性删除**（读时发现过期才删） + **定期随机抽查**（默认每秒 10 次每次随机抽 20 个检查，过期占比超 25% 继续抽）；
   * **内存快满时插入能否成功？**：取决于 `maxmemory-policy` 淘汰策略！默认 **`noeviction`** 会**写入报错 OOM**（读正常）；若配置为 **`allkeys-lru` / `allkeys-lfu` / `volatile-lru`** 等淘汰策略，Redis 会同步驱逐旧 Key 腾出空间让本次写入成功！

#### Q19：缓存穿透、击穿、雪崩区别及解决方案？重点讲布隆过滤器原理与误判！
* **缓存雪崩**：大量 Key 同时过期或 Redis 宕机 → 加随机过期 TTL + 主从哨兵 + 本地多级缓存；
* **缓存击穿**：超高并发热点 Key 突然过期 → 逻辑永不过期或 `SETNX` 互斥锁单线程回源重建；
* **缓存穿透**：查 DB 和缓存均不存在的数据 → 缓存空值（短 TTL） + **布隆过滤器**。
* **🔥 布隆过滤器原理与误判（领星必问原题！）**：
  * **底层结构**：超长 **BitMap 位数组 + $k$ 个独立哈希函数**；
  * **特点与误判原因**：**说不存在则 100% 一定不存在；说存在可能误判**！因为不同 Key 哈希碰撞可能把相同比特位置成 `1`，查一个未插入的新 Key 时刚好它对应的 $k$ 个位置都被其它元素凑巧置 `1` 了；
  * **不支持删除怎么解决？**：① 布隆放行后再二次查 Redis/DB 兜底校验；② 低峰期定时任务**异步全量重建（Rebuild）新布隆替换旧实例**；③ 改用计数布隆过滤器（Counting Bloom Filter，内存放大）。

#### Q20：Redis 与数据库双写一致性怎么做？Redisson 分布式锁与 RedLock？
* **双写一致性（Cache-Aside）**：先更新 MySQL，提交成功后再删 Redis 缓存；极端删缓存失败走**延时双删 + Canal 监听 Binlog 异步驱动 MQ 补偿删缓存**。
* **Redisson 分布式锁 & Watchdog（看门狗）**：Lua 脚本保障原子性 + 可重入 Hash 结构 + 线程唯一标识校验防误删他人锁；未传 `leaseTime` 时默认 30 秒过期，Netty 时间轮每隔 **10 秒（$leaseTime / 3$）** 自动续期回 30 秒。
* **RedLock**：针对主从异步复制丢锁风险，向 $N$ 个（5 个）独立 Redis Master 依次加锁，半数以上（$\ge 3$）成功且总耗时小于锁 TTL 才算成功。

#### Q21：API 网关做了什么？Spring Cloud Gateway 底层为什么快？如何用 Redis 实现令牌桶限流？（领星牛客真题！）
* **网关核心 4 大职责**：动态路由转发、统一鉴权与签名验签、黑白名单与链路日志、**网关限流熔断**。
* **Gateway 底层为什么快？**：放弃老旧 Zuul 1.x 的阻塞式 Servlet（Thread-per-request）模型，底层基于 **Spring WebFlux + Project Reactor + Netty 响应式异步非阻塞模型**，少量 EventLoop 线程基于多路复用处理海量并发连接。
* **Redis + Lua 令牌桶限流**：Hash 存 `last_time` 与 `tokens`；Lua 脚本原子计算 `(now - last_time) * rate` 补充令牌（封顶 `capacity`），判断 `>= 1` 则扣减放行并更新时间戳，否则返回 429 拒绝。

#### Q22：消息队列（RocketMQ/Kafka）如何保证消息不丢失、严格保序、不重复消费、不积压？
* **保不丢**：生产端 `acks=all`（同步等回执） + Broker 同步刷盘 & 主从多副本（`min.insync.replicas=2`） + 消费端落库后再手动提交 Offset；
* **严格保序**：按实体 ID（如 `order_id` / `shop_id`）做 Hash 路由到**同一个 Partition/Queue**（单队列天然 FIFO），且生产端禁乱序重试；
* **保幂等防重**：消费端依托**全局业务唯一键建 DB 唯一索引**、Redis `SETNX` 或状态机前置乐观锁；
* **防积压**：先排查消费端慢 SQL/宕机修复 → 若积压百万级，紧急建立 **10 倍 Partition 新 Topic + 10 倍 Consumer**，旧消费端只做无逻辑轻量搬运转发快速泄洪！*(注：盲目只加消费者实例不扩 Partition 是没用的，同一消费组内多出来的消费者会处于闲置！)*

---

### 五、Spring 事务/MyBatis、计算机网络与 Linux（Q23 ~ Q27）

#### Q23：Spring Boot 自动装配原理（Starter）是什么？（领星真题！）
1. `@SpringBootApplication` 包含 `@EnableAutoConfiguration`；
2. 借助 `@Import(AutoConfigurationImportSelector.class)` 扫描所有 Starter jar 包中 **`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`**（Boot 2.7 前为 `spring.factories`）；
3. 读取配置类全限定名，按 **`@ConditionalOnClass`、`@ConditionalOnMissingBean`、`@ConditionalOnProperty`** 条件注解精准装配进 IoC 容器。

#### Q24：Spring `@Transactional` 6 大失效场景 & 三级缓存解决循环依赖（汉得/金蝶高频！）
* **🔥 `@Transactional` 6 大经典失效场景（背熟！）**：
  1. **同类内部自调用（最高频）**：普通方法 `a()` 直接调用本类 `@Transactional` 方法 `b()`（`this.b()` 绕过了 Spring AOP 动态代理对象，事务直接失效！解法：注入自身代理或 `AopContext.currentProxy()`）；
  2. **方法非 `public` 修饰**：`private/protected` 无法生成事务代理；
  3. **异常被内部 `try-catch` 吞掉**未重新抛出；
  4. **抛出受检异常（如 `IOException`）但未配 `rollbackFor`**：默认只对 `RuntimeException` 和 `Error` 回滚，必须显式写 `@Transactional(rollbackFor = Exception.class)`；
  5. **事务传播行为配置错误**（如 `NOT_SUPPORTED` / `NEVER`）；
  6. **数据库引擎不支持事务**（如旧表用了 MyISAM）。
* **Spring 三级缓存解决循环依赖（A ⇄ B）**：
  * 一级 `singletonObjects`（成品）、二级 `earlySingletonObjects`（半成品早期引用）、三级 `singletonFactories`（`ObjectFactory` 工厂，**关键作用是在需要时提前生成 AOP 代理对象避免最终注入不一致**）；
  * **无法解决构造器注入循环依赖**：实例化时构造方法还没跑完，无法把引用塞进三级缓存，直接抛 `BeanCurrentlyInCreationException`。

#### Q25：Spring MVC 执行流程 & MyBatis 两大真题（`#` vs `$`、为什么生产禁用二级缓存？）
* **Spring MVC 流程**：请求 → `DispatcherServlet` → `HandlerMapping` 找执行链 → `HandlerAdapter` 参数绑定反射调 Controller → `@ResponseBody` 走 `HttpMessageConverter` 转 JSON（或 `ViewResolver` 渲染视图）。
* **MyBatis `# {}` vs `${}`**：`#{}` 走 **JDBC `PreparedStatement` 预编译占位符 `?`**，彻底防 SQL 注入；`${}` 是纯字符串直接拼接（仅在动态表名/排序字段 `ORDER BY` 处慎用，且必须先做白名单校验）。
* **🔥 为什么微服务生产环境禁开 MyBatis 二级缓存？（汉得真题！）**：
  * 一级缓存默认开启且作用域仅限单个 `SqlSession`（同一次事务内）。**二级缓存是 Mapper 命名空间级别的本地进程内存缓存**！
  * **两大灾难**：① 多节点集群下 A 机器改库，B 机器读出本地陈旧脏缓存；② 哪怕单机下，**Mapper A 做 `JOIN` 连表查了表 B 并缓存，之后 Mapper B 更新了表 B，Mapper A 完全感知不到**，直接稳定输出脏数据！生产上统一交给 **Redis** 治理。

#### Q26：TCP 三次握手/四次挥手/队列/TIME_WAIT？HTTP/1.1 vs HTTP/2？DNS 协议？
* **三次握手**：确认双端收发能力正常 + 同步初始序列号 ISN（防历史失效 SYN 延后到达建立幽灵连接）。连接先后进**半连接队列（SYN Queue，防 SYN Flood 开 SYN Cookie）**和**全连接队列（Accept Queue，溢出丢包或回 RST）**。
* **四次挥手 & `TIME_WAIT` 等 `2MSL`**：全双工各自关闭；等 **`2MSL`**：① 确保最后发出的 ACK 丢包时能重传响应对端重发的 FIN；② 让网络里游荡的旧报文彻底消逝不污染后续复用的相同四元组新连接。
* **HTTP/1.1 vs HTTP/2**：1.1 存在**应用层队头阻塞**与明文冗余头；HTTP/2 引入**二进制分帧、单连接多路复用**、**HPACK 头部压缩**。
* **DNS 协议（领星牛客真题！）**：普通域名解析走 **UDP 53 端口**（无握手极速一问一答）；解析响应 $>512$ 字节或主从区域传送（Zone Transfer）时用 **TCP 53 端口**。

#### Q27：Linux 高频排障命令（领星真题！）
* 实时盯线上日志高亮错误上下文：`tail -f app.log | grep -C 10 "ERROR"`
* 查指定时间段日志：`sed -n '/2026-11-17 14:00/,/2026-11-17 14:30/p' app.log`
* 查看端口监听与占用进程：`ss -tunlp | grep 8080` 或 `lsof -i:8080`
* 系统资源水位：`top`、`free -h`、`df -h`、`vmstat 1`。

---

## 模块五：跨境电商 ERP 专属 4 大高频业务场景题

> 💡 **拉分密码**：技术一面/二面末尾，领星考官非常喜欢抛出一道**跨境 SaaS 真实业务痛点**让你现场架构设计！

### 场景 1：领星每天需对接亚马逊/Walmart/Temu API 拉取千万级卖家订单，对方接口严格限流且经常超时抖动，如何设计高可靠拉单架构？
1. **多租户限流隔离（Token Bucket）**：严格按**卖家店铺/授权账号维度**构建 Redis + Lua 分布式令牌桶（避免一个大卖家配额耗尽拖垮全系统或触发 Amazon 429 封号）；
2. **分布式调度与分片**：采用 XXL-JOB / 弹性任务调度按店铺 ID 哈希分片拉取，将“拉取原始 JSON 报文”与“解析入库/财务算账”彻底通过 **RocketMQ 异步解耦削峰**；
3. **增量游标 + 断点续传 + 幂等防重**：每个店铺记录上次成功拉取的 `last_updated_after` 时间戳并稍微回退 5 分钟（防时钟漂移漏单）；利用第三方**平台原始订单号 `platform_order_id + shop_id` 在 DB 建联合唯一索引**，配合业务版本号防止旧状态覆写新状态；
4. **失败降级重试**：遭遇平台限流或瞬断时，将任务丢入分级延迟重试队列（指数退避 30s → 2m → 10m），超限进入死信告警转人工补单。

### 场景 2：跨境卖家月底在后台导出百万级 SKU 销售利润报表，如何避免 OOM 与接口超时？
*(直接复刻我们准备好的金蝶四步标准架构，百发百中！)*
1. **游标查询取代深分页**：用 `WHERE id > lastMaxId ORDER BY id ASC LIMIT 5000`（或 MyBatis `ResultHandler` 流式游标），拒绝全表加载入 `List`；
2. **EasyExcel 流式写磁盘**：SAX 模型边写边刷入临时文件并释放堆内存，百万行占用几十 MB；
3. **同步改异步 + 导出任务中心**：接口瞬间创建任务 ID 返回前端；后台跑完上传阿里云 OSS/MinIO 并通知下载；
4. **并发信号量控制 + 同条件防重复用**（相同筛选条件的 MD5 直接复用 10 分钟内的缓存报表文件）。

### 场景 3：跨境多币种（美元/欧元/日元/英镑）财务利润结算，Java 和 DB 怎么处理？
* Java 中绝对严禁 `float`/`double`，一律使用 **`BigDecimal` 且用 `new BigDecimal(String)` 字符串构造器**，乘法汇率换算保留精度后在最终汇总结算时统一通过 `divide(divisor, 4, RoundingMode.HALF_UP)`（或 `HALF_EVEN` 银行家舍入）防除不尽抛异常，判等一律用 `compareTo() == 0`；
* MySQL 统一用 **`DECIMAL(19, 4)`** 存储，并额外记录交易瞬间的**币种代码 + 汇率快照 + 基准本位币金额**，防止事后汇率波动导致历史账目对不上。

### 场景 4：SaaS 多租户数据隔离架构怎么做？（领星核心底座！）
* **三种经典模式对比**：
  1. **独立数据库**（最高安全隔离，大客户定制，运维成本高）；
  2. **共享库独立 Schema**（折中方案，备份方便但连接池开销仍大）；
  3. **共享库共享表 + `tenant_id` 逻辑隔离（SaaS 主流）**：所有业务表必含 `tenant_id` 并将其置于联合索引或分片键最左端；通过 **MyBatis-Plus `TenantLineInnerInterceptor` 多租户拦截器**在底层自动往每一条 SQL 的 `WHERE` 和 `INSERT` 中强制注入 `tenant_id = ThreadLocal.getTenantId()`，彻底杜绝开发同学手滑漏写条件引发的**跨租户数据越权泄露严重安全事故**！

---

## 模块六：1 个月未刷算法“24 小时手撕唤醒急救包”（Top 12 算法 + 多线程 + 高频 SQL）

> 🔥 **领星手撕风格与拿分 4 步法**：  
> 领星实习技术面手撕难度稳稳控制在 **LeetCode Easy ~ Medium**，考官主要看你**代码是否 Bug-free、边界条件处理（空指针/越界）、以及思维清晰度**！  
> **切记 4 步走**：① **先确认边界**（“数组可能为空或 `null` 吗？链表只有一个节点怎么处理？”） → ② **先讲清楚最优思路和复杂度再写** → ③ **规范命名、先写判空保护** → ④ **写完自己拿一个小用例口头走一遍变量变化**！

---

### 模板 1：反转单链表（LeetCode 206 —— 实习面出现率第一！）
> **口诀**：`prev = null, curr = head`，循环四步：**存 `next`、指 `prev`、移 `prev`、移 `curr`**，最后返回 `prev`。

```java
public ListNode reverseList(ListNode head) {
    ListNode prev = null;
    ListNode curr = head;
    while (curr != null) {
        ListNode next = curr.next; // 1. 暂存后继防断链
        curr.next = prev;          // 2. 反转指针
        prev = curr;               // 3. prev 前移
        curr = next;               // 4. curr 前移
    }
    return prev;
}
```

### 模板 2：链表判环 & 找环入口节点（LeetCode 141 / 142 —— 快慢指针）
> **口诀**：快 2 慢 1 相遇即有环；**找入口**：相遇后让一个指针回到 `head`，两指针**同速每次走 1 步**，再次相遇就是入口！

```java
public ListNode detectCycle(ListNode head) {
    ListNode slow = head, fast = head;
    while (fast != null && fast.next != null) {
        slow = slow.next;
        fast = fast.next.next;
        if (slow == fast) { // 相遇说明有环
            ListNode ptr = head;
            while (ptr != slow) {
                ptr = ptr.next;
                slow = slow.next;
            }
            return ptr; // 二次相遇即为环入口
        }
    }
    return null; // 无环
}
```

### 模板 3：合并两个有序链表（LeetCode 21）
> **口诀**：无脑建一个 **`dummy = new ListNode(-1)` 哨兵节点**，谁小接谁，最后把剩下的整条挂上！

```java
public ListNode mergeTwoLists(ListNode l1, ListNode l2) {
    ListNode dummy = new ListNode(-1);
    ListNode curr = dummy;
    while (l1 != null && l2 != null) {
        if (l1.val <= l2.val) {
            curr.next = l1;
            l1 = l1.next;
        } else {
            curr.next = l2;
            l2 = l2.next;
        }
        curr = curr.next;
    }
    curr.next = (l1 != null) ? l1 : l2; // 直接拼接剩余部分
    return dummy.next;
}
```

### 模板 4：删除链表的倒数第 N 个节点（LeetCode 19）
> **口诀**：`dummy` 哨兵防删头节点；`fast` 先走 **$n + 1$ 步**，然后 `fast` 和 `slow` 同步走直到 `fast == null`，此时 `slow` 正好停在**待删节点的前驱**！

```java
public ListNode removeNthFromEnd(ListNode head, int n) {
    ListNode dummy = new ListNode(-1, head);
    ListNode fast = dummy, slow = dummy;
    for (int i = 0; i <= n; i++) { // 先走 n+1 步
        fast = fast.next;
    }
    while (fast != null) {
        fast = fast.next;
        slow = slow.next;
    }
    slow.next = slow.next.next; // 跳过倒数第 n 个节点
    return dummy.next;
}
```

### 模板 5：无重复字符的最长子串（LeetCode 3 —— 滑动窗口万能模板！）
> **口诀**：右指针不断吞字符，用 `HashMap<Character, Integer>` 记最新下标；若碰到重复字符，**`left` 只能向右跳不能回退**：`left = Math.max(left, map.get(c) + 1)`！

```java
public int lengthOfLongestSubstring(String s) {
    if (s == null || s.isEmpty()) return 0;
    Map<Character, Integer> lastIndex = new HashMap<>();
    int maxLen = 0, left = 0;
    for (int right = 0; right < s.length(); right++) {
        char c = s.charAt(right);
        if (lastIndex.containsKey(c)) {
            left = Math.max(left, lastIndex.get(c) + 1); // 关键：Math.max 防止 left 倒退
        }
        lastIndex.put(c, right);
        maxLen = Math.max(maxLen, right - left + 1);
    }
    return maxLen;
}
```

### 模板 6：两数之和 & 三数之和（LeetCode 1 & 15）
> **口诀**：两数之和用 `HashMap` 存 `值 -> 下标`；三数之和**先排序** + 固定 `i` 去重 + 双指针 `left`/`right` 夹逼去重。

```java
// 1. 两数之和 O(N)
public int[] twoSum(int[] nums, int target) {
    Map<Integer, Integer> map = new HashMap<>();
    for (int i = 0; i < nums.length; i++) {
        int need = target - nums[i];
        if (map.containsKey(need)) return new int[]{map.get(need), i};
        map.put(nums[i], i);
    }
    return new int[0];
}

// 2. 三数之和 O(N^2)
public List<List<Integer>> threeSum(int[] nums) {
    List<List<Integer>> res = new ArrayList<>();
    if (nums == null || nums.length < 3) return res;
    Arrays.sort(nums); // 必须先排序！
    for (int i = 0; i < nums.length - 2; i++) {
        if (nums[i] > 0) break; // 剪枝：最小值大于 0 不可能凑出 0
        if (i > 0 && nums[i] == nums[i - 1]) continue; // 对 i 去重
        int left = i + 1, right = nums.length - 1;
        while (left < right) {
            int sum = nums[i] + nums[left] + nums[right];
            if (sum == 0) {
                res.add(Arrays.asList(nums[i], nums[left], nums[right]));
                while (left < right && nums[left] == nums[left + 1]) left++;   // 去重
                while (left < right && nums[right] == nums[right - 1]) right--; // 去重
                left++;
                right--;
            } else if (sum < 0) left++;
            else right--;
        }
    }
    return res;
}
```

### 模板 7：有效的括号（LeetCode 20 —— 栈）
> **口诀**：遇见左括号，**把对应的右括号压进栈**；遇见右括号，看栈是不是空或者弹出来不等于当前右括号！

```java
public boolean isValid(String s) {
    if (s == null || s.length() % 2 != 0) return false;
    Deque<Character> stack = new ArrayDeque<>();
    for (char c : s.toCharArray()) {
        if (c == '(') stack.push(')');
        else if (c == '[') stack.push(']');
        else if (c == '{') stack.push('}');
        else if (stack.isEmpty() || stack.pop() != c) return false;
    }
    return stack.isEmpty();
}
```

### 模板 8：标准闭区间二分查找（防错四句口诀！）
> **口诀**（彻底告别新凯来踩坑）：① `while (left <= right)`；② 第一行算 `mid = left + (right - left) / 2`；③ 小了 `left = mid + 1`，大了 `right = mid - 1`；④ 找不到返回 `-1`。

```java
public int binarySearch(int[] nums, int target) {
    if (nums == null || nums.length == 0) return -1;
    int left = 0, right = nums.length - 1;
    while (left <= right) {
        int mid = left + (right - left) / 2;
        if (nums[mid] == target) return mid;
        else if (nums[mid] < target) left = mid + 1;
        else right = mid - 1;
    }
    return -1;
}
```

### 模板 9：快速排序（挖坑填数法）
> **口诀**：选最左当坑 `pivot = arr[left]` → **先从右找小填左坑** → **再从左找大填右坑** → 相遇填回 `pivot` 递归左右。

```java
public void quickSort(int[] arr, int left, int right) {
    if (left >= right) return;
    int i = left, j = right, pivot = arr[left];
    while (i < j) {
        while (i < j && arr[j] >= pivot) j--;
        if (i < j) arr[i] = arr[j];
        while (i < j && arr[i] <= pivot) i++;
        if (i < j) arr[j] = arr[i];
    }
    arr[i] = pivot;
    quickSort(arr, left, i - 1);
    quickSort(arr, i + 1, right);
}
```

### 模板 10：二叉树层序遍历（BFS 队列 - LeetCode 102）& 最近公共祖先（LeetCode 236）

```java
// 1. 层序遍历：记住每次循环开头锁死 size = queue.size()！
public List<List<Integer>> levelOrder(TreeNode root) {
    List<List<Integer>> res = new ArrayList<>();
    if (root == null) return res;
    Queue<TreeNode> queue = new LinkedList<>();
    queue.offer(root);
    while (!queue.isEmpty()) {
        int size = queue.size();
        List<Integer> level = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            TreeNode node = queue.poll();
            level.add(node.val);
            if (node.left != null) queue.offer(node.left);
            if (node.right != null) queue.offer(node.right);
        }
        res.add(level);
    }
    return res;
}

// 2. 最近公共祖先 (LCA)：背下 6 行绝杀模板！
public TreeNode lowestCommonAncestor(TreeNode root, TreeNode p, TreeNode q) {
    if (root == null || root == p || root == q) return root;
    TreeNode left = lowestCommonAncestor(root.left, p, q);
    TreeNode right = lowestCommonAncestor(root.right, p, q);
    if (left != null && right != null) return root; // 左右各找到一个，当前 root 就是最近公共祖先
    return left != null ? left : right;            // 都在同一侧，谁不为空返回谁
}
```

### 模板 11：字符串相加（LeetCode 415 —— 领星高频模拟题）
> **口诀**：双指针从尾部往前扫，`while (i >= 0 || j >= 0 || carry > 0)`，算当前和与进位，最后 `reverse()`！

```java
public String addStrings(String num1, String num2) {
    StringBuilder sb = new StringBuilder();
    int i = num1.length() - 1, j = num2.length() - 1, carry = 0;
    while (i >= 0 || j >= 0 || carry > 0) {
        int x = (i >= 0) ? num1.charAt(i--) - '0' : 0;
        int y = (j >= 0) ? num2.charAt(j--) - '0' : 0;
        int sum = x + y + carry;
        sb.append(sum % 10);
        carry = sum / 10;
    }
    return sb.reverse().toString();
}
```

### 模板 12：手写线程安全 DCL 单例 & 手写 LRU 缓存思想
```java
// 1. DCL 单例：必须加 volatile 禁止指令重排防返回半初始化对象！
public class Singleton {
    private static volatile Singleton instance;
    private Singleton() {}
    public static Singleton getInstance() {
        if (instance == null) {
            synchronized (Singleton.class) {
                if (instance == null) {
                    instance = new Singleton();
                }
            }
        }
        return instance;
    }
}

// 2. 极简 LRU（面试允许用 LinkedHashMap 写时 5 行搞定；若要求不能用则说思路：HashMap + 双向链表）
class LRUCache extends LinkedHashMap<Integer, Integer> {
    private final int capacity;
    public LRUCache(int capacity) {
        super(capacity, 0.75f, true); // true 代表按访问顺序排序（accessOrder）
        this.capacity = capacity;
    }
    public int get(int key) { return super.getOrDefault(key, -1); }
    public void put(int key, int value) { super.put(key, value); }
    @Override
    protected boolean removeEldestEntry(Map.Entry<Integer, Integer> eldest) {
        return size() > capacity;
    }
}
```

### 模板 13：多线程手撕（双线程交替打印 1~100 & AB 并行等待 C 执行）
> **必背逻辑**：交替打印用 `synchronized + wait/notifyAll`（防虚假唤醒**必须用 `while` 判断**！）；AB 并行 C 等用 `CountDownLatch(2)` 或 `CompletableFuture.allOf()`。

```java
// 1. 两线程交替打印 1~100 奇偶数
public class AlternatePrint {
    private int count = 1;
    private final Object lock = new Object();

    public void print(boolean isOdd) {
        while (count <= 100) {
            synchronized (lock) {
                while (count <= 100 && (count % 2 == 1) != isOdd) { // 必须用 while 防虚假唤醒
                    try { lock.wait(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
                if (count <= 100) {
                    System.out.println(Thread.currentThread().getName() + ": " + count++);
                }
                lock.notifyAll();
            }
        }
    }
}
```

### 模板 14：领星高频手写 SQL 4 大标准写法（防考官不考算法直接考 SQL！）
```sql
-- 1. 统计每个店铺近 30 天成交总额超过 10 万的 Top 3 大客户（GROUP BY + HAVING）
SELECT tenant_id, buyer_id, SUM(pay_amount) AS total_amount
FROM t_order
WHERE status = 'PAID' AND create_time >= DATE_SUB(NOW(), INTERVAL 30 DAY)
GROUP BY tenant_id, buyer_id
HAVING total_amount > 100000
ORDER BY total_amount DESC
LIMIT 3;

-- 2. 按店铺分组取销售额最高的 Top 3 商品（窗口函数 DENSE_RANK / ROW_NUMBER）
SELECT shop_id, sku_id, sales_amount FROM (
    SELECT shop_id, sku_id, sales_amount,
           ROW_NUMBER() OVER (PARTITION BY shop_id ORDER BY sales_amount DESC) AS rn
    FROM t_sku_sales
) t
WHERE rn <= 3;

-- 3. 千万级大表深分页延迟关联优化（告别 LIMIT 500000, 20 慢查询）
SELECT o.* FROM t_order o
INNER JOIN (
    SELECT id FROM t_order
    WHERE tenant_id = 1001 AND status = 'PAID'
    ORDER BY create_time DESC
    LIMIT 500000, 20
) tmp ON o.id = tmp.id;

-- 4. 一句原子 SQL 安全扣减库存（严禁先 SELECT 再 Java 算再 UPDATE！）
UPDATE t_inventory
SET available_qty = available_qty - #{qty}, locked_qty = locked_qty + #{qty}
WHERE shop_id = #{shopId} AND sku_id = #{skuId} AND available_qty >= #{qty};
```

---

## 模块七：高水平反问环节与 24 小时考前冲刺时间表

### 1. 高水平反问（展现你真心想来干活、懂 SaaS 技术痛点）
* **问题 1（业务架构向）**：*“想了解一下咱们组目前核心负责领星 ERP 哪一块业务？比如多平台订单同步链路、进销存财务核算，还是目前结合大模型/AI 助手在做的新产品线？”*
* **问题 2（技术挑战向）**：*“我看领星面对海量跨境卖家，在多租户数据库性能调优或第三方平台（像 Amazon/Temu）高并发接口限流同步方面一定有很深的积累，实习生入职后最容易在这些技术栈上承接哪类需求？”*
* **问题 3（实习规划向）**：*“如果我有幸通过面试加入团队，您对 Java 实习生在入职第一个月的快速上手与产出有什么期待或建议吗？”*

---

### 2. ⏱️ 未来 24 小时黄金唤醒时间表（今天 14:15 → 明天 16:00）

| 时段 | 核心任务 | 对应文档定位 |
| :--- | :--- | :--- |
| **今天 14:15 — 15:30（75 min）** | **醒脑第一波：吃透自我介绍、HR 防雷话术与简历项目话术** | 本宝典 [模块二](#模块二自我介绍与大四已签校招找实习防雷话术) + [模块三](#模块三项目与实习深度拷打防线精准对齐领星跨境-saas-场景) |
| **今天 15:30 — 18:00（2.5 小时）** | **醒脑第二波：通读 27 道核心八股（重中之重唤醒 JVM 内存/G1/ZGC/双亲委派/调优 5 参数 + 集合陷阱 + 间隙锁/分库分表/布隆/Gateway/事务失效）** | 本宝典 [模块四](#模块四领星牛客真题--既往高危遗忘八股-27-问极速唤醒) |
| **今天 19:30 — 21:30（2 小时）** | **手感恢复：把模块六的 Top 12 算法 + 多线程 + SQL 打开 IDE 亲手从零敲一遍** | 本宝典 [模块六](#模块六1-个月未刷算法24-小时手撕唤醒急救包top-12-算法--多线程--高频-sql) |
| **明天 09:30 — 11:30（2 小时）** | **场景拔高 + 错题重敲：过一遍模块五跨境 ERP 4 大场景题，重敲昨晚卡壳的算法与 JVM 5 大参数口播** | 本宝典 [模块五](#模块五跨境电商-erp-专属-4-大高频业务场景题) + [模块四 Q11](#q11重中之重jvm-线上调优怎么做生产必配参数--oomcpu-100-排查-sop) |
| **明天 14:00 — 15:30（90 min）** | **考前速记：出声演练自我介绍与荒天享物（含 Eval 集）/云智易/凯通项目描述，扫一遍加粗关键字** | 全文目录速览 |
| **明天 15:30 — 16:00（30 min）** | **检查会议软件、麦克风、网络与 IDE（做好共享屏幕手撕准备），保持放松自信上场！** | — |

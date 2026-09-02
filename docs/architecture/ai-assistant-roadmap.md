# AI 助手分阶段实施 — 分支策略与协调计划

> **For agentic workers:** 本文档是 **路线图级元计划**(meta-plan),定义分支拓扑与每分支的设计目标,**不包含**任何分支内的具体任务级步骤。每个分支开工前会另写一份 `[分支名]-implementation.md` 详细计划,严格按 writing-plans 规范产出任务粒度的 TDD 步骤。
>
> **并行约束**:每个 AI Agent 默认工作区互斥;不要让两个分支同时编辑 `com.scutmmq.ai.*` 同一文件。

**Goal:** 用 git 分支隔离 6 个独立可上线的能力阶段,使每个阶段"设计 → 计划 → 实现 → 验证 → 合入"闭环可控、可回退、可单独 review/撤回。

**Architecture:** 主分支 `master` 始终处于"对外可用且 AI 行为完全不变"状态;每个分支只引入一类能力,默认 `enabled=false`,合入 master 后通过 yaml 配置灰度启用。

**Tech Stack:** Spring Boot 3.5.5 / Java 17 / MyBatis-Plus / MySQL 8 / Redis / Spring `ApplicationEventPublisher` + 自研 `AiCapability` 抽象。(零 Agent 框架依赖)

---

## 0. 元信息与上游文档

- 路线图设计:`huangtian-goods/doc/AI助手拓展路线设计.md` (v1.1)
- 待解决问题清单:`huangtian-goods/doc/AI购物助手待解决问题.md` (2026-06-30)
- 技术实施细节:`huangtian-goods/doc/AI助手拓展技术实施.md`
- 数据迁移脚本模板:`huangtian-goods/doc/migration_001_ai_streaming.sql`
- AI 模块入口:`com.scutmmq.ai.AiAssistantController` (现有)
- 仓库基线:master @ `1ebfc26 文档`,working tree clean

---

## 1. 仓库基线与分支命名

### 1.1 当前仓库状态

```bash
$ git status
On branch master
Your branch is up to date with 'origin/master'.
nothing to commit, working tree clean

$ git log --oneline -3
1ebfc26 文档
48f46e2 fix: persistToolExecutions 跳过空名字的工具执行
48f990b fix: run.completed SSE 事件移到 aiRunService.complete 之后 emit
```

✅ 干净 master,可立刻拉分支。

### 1.2 分支命名规范

```
fix/ai-p0-draft-linkage      # P0-1:草稿关联 assistantMessageId
fix/ai-p0-cart-validation    # P0-2:加购草稿前置校验
feat/ai-stage1-observability # Stage 1:可观测 + 评估(Capability 抽象一并落地)
feat/ai-stage2-memory        # Stage 2:长期记忆 + 短期摘要
feat/ai-stage3-rag           # Stage 3:RAG(商品知识 + 商城规则)
feat/ai-stage4-planner       # Stage 4:规划 + 反思 + 并行工具
feat/ai-stage6-portfolio     # Stage 6:Demo + Grafana + 博客
```

Stage 5(多 Agent)按路线图结论**不实施**,故无对应分支。

### 1.3 分支命名原则

- `fix/*` = 纯修复,可单独回滚对线上行为 0 影响
- `feat/ai-stageN-*` = 一个分支只装一个 Stage,不允许跨 Stage
- 不引入 `epic/*` 聚合分支(分支数少,无需)

---

## 2. 分支拓扑与依赖图

```
master (基线,可发布)
 │
 │  B0  fix/ai-p0-draft-linkage       ──────────────────────╮
 │  B1  fix/ai-p0-cart-validation     ────────────────────╮ │
 │                                                     ▼ ▼
 │                                                  merge B0+B1
 │                                                     │
 │                                            B2 feat/ai-stage1-observability
 │                                            (Capability 抽象 + 权限 + 可观测 + Eval)
 │                                                     │
 │                                            B3 feat/ai-stage2-memory
 │                                            (Memory + HistorySummarizer)
 │                                                     │
 │                                            B4 feat/ai-stage3-rag
 │                                            (Embedding + VectorStore + SearchKnowledgeTool)
 │                                                     │
 │                                            B5 feat/ai-stage4-planner
 │                                            (Planner + Reflector + ParallelToolExecutor)
 │                                                     │
 │                                            B6 feat/ai-stage6-portfolio
 │                                            (Grafana + 博客 + B站 Demo)
 ▼                                                     ▼
(分支合并入 master 后,master 节点也向前推进;不再有长期存在的开发支线)
```

### 2.1 依赖矩阵

| 分支 | 前置分支 | 阻塞后续 |
|---|---|---|
| B0 `fix/ai-p0-draft-linkage` | 无 | B2(Stage 1 需要草稿统计可观测) |
| B1 `fix/ai-p0-cart-validation` | 无(与 B0 独立) | 无 |
| B2 `feat/ai-stage1-observability` | B0 建议在合入前 | B3, B4, B5 |
| B3 `feat/ai-stage2-memory` | B2(消费 `RunStartedEvent` / `ToolExecutedEvent`) | 无 |
| B4 `feat/ai-stage3-rag` | B2(消费事件 + 依赖 `PromptEnricher` 扩展点) | B5(可与 B3 并行收尾) |
| B5 `feat/ai-stage4-planner` | B2, B3, B4(都需要) | B6 |
| B6 `feat/ai-stage6-portfolio` | B5(综合演示需要规划能力) | — |

**并行建议**:
- B0 与 B1 可并行拉分支(`git worktree add` 隔离),但合并需顺序。
- B3 与 B4 在 B2 之后可并行(B3 改 `MallSystemPromptProvider` 的 `PromptEnricher` 列表,B4 走事件总线,主循环文件不交叉)。

### 2.2 Git Worktree(可选,适合 Stage 3/4 跨度大时)

如果某分支开发周期 > 3 天,推荐用 git worktree 隔离:

```bash
git worktree add ../huangtian-goods-stage3 feat/ai-stage3-rag
```

避免主工作区被长期占着不能跑其它指令。

---

## 3. 标准分支协议(每个分支都必须走的 6 步)

> 这是固定协议,**任何分支开工前**严格走完前 3 步,实施期间严格走完 4-6 步。

### Step 1:设计评审(在 master 上,不写代码)
- 读懂路线图文档对应章节 + 待解决问题文档交叉引用
- 输出:本分支的 **设计稿**(可以是追加到本文档或独立 `design-<branch>.md`)
  - 改/新增的文件清单(精确路径)
  - 接口/事件 schema
  - 新表 DDL(若涉及)
  - yaml 配置项
  - 与既有代码的交互点(尤其是 §3.3 实际改动量)
  - 验收测试用例集
- **产出物经过用户评审通过后,才进入 Step 2**

### Step 2:本分支详细实施计划(另写 `[分支名]-implementation.md`)
- 严格按 writing-plans 规范(任务粒度、TDD、Checklist)
- 每个 task 包含:Files / Interfaces / 失败测试 → 实现 → 通过 → commit
- **本计划也需要用户 Review 才进入 Step 3**

### Step 3:拉分支
```bash
git checkout master
git pull --rebase
git checkout -b <branch-name>
git push -u origin <branch-name>
```

### Step 4:TDD 实施(逐步)
- 每完成一个 task:`git add ... && git commit -m "..."`
- Task 累积到一定数量时:`git push` 备份
- 整分支完工时:跑全量测试 + EvalRunner + 手动 curl 验证

### Step 5:分支级验收
按各分支自己的"验收门槛"运行(下文 §6)。

### Step 6:合入 master
```bash
# 本地
git checkout master
git merge --no-ff <branch-name>
git push origin master

# 删除本地/远程分支
git branch -d <branch-name>
git push origin --delete <branch-name>
```
- 合入后**立刻**跑一遍 `mvn clean package -DskipTests` 确保 master 不破。
- 灰度策略(配置开关,不需要代码回滚):`ai.capability.<name>.enabled=false` 默认保持,生产用量通过 Nacos/环境变量切到 1% → 100%。

---

## 4. 分支设计目标(各分支核心)

> 每分支的设计目标、验收门槛、关键改动面。详细任务拆分留到该分支的 `[分支名]-implementation.md`。

### B0 · fix/ai-p0-draft-linkage — 草稿回挂消息

**目标**:修路线图延伸出来的"地基 bug":创建草稿时写入 `assistantMessageId`,前端历史消息列表能正确恢复确认卡片。

**输入**:路线图 v1.1 §7A(感知风险)+ 待解决问题 P0 #1。

**核心改动**:
| 文件 | 改动 |
|---|---|
| `AiActionDraftService.java` | `create(...)` 增加 `Long assistantMessageId` 参数,内部 `setAssistantMessageId` |
| `AiAssistantService.java` | `AiRunRunnable.persistDraftIfPresent(...)` 传入 `assistantMessageId` |
| 新增 `AiActionDraftLinkageTest.java` | 服务层测试:草稿创建后 `findByAssistantMessageId(...)` 能查到 |

**验收**:新增测试通过 + 手动 `curl /ai/chat` 创建草稿,查 DB `ai_action_draft.assistant_message_id` 非空。

**依赖**:无
**风险**:极低(纯字段补齐,DB 列已存在)
**回退**:revert merge commit

---

### B1 · fix/ai-p0-cart-validation — 加购草稿前置校验

**目标**:让 `DraftAddCartItemTool` 的校验对齐 `DraftCreateOrderTool`,避免无效草稿。

**输入**:待解决问题 P0 #2。

**核心改动**:
| 文件 | 改动 |
|---|---|
| `DraftAddCartItemTool.java` | 抽公共校验逻辑(可独立新方法 `validateAddCart(...));` 校验:商品存在、未下架、库存 ≥ quantity、非自家店铺商品 |
| (可选)`DraftValidationUtils.java` | 抽公共校验方法,给两个 draft tool 共享 |
| 新增 `DraftAddCartItemToolTest.java` | 测 5 种校验失败场景 |

**验收**:5 种校验失败场景全部抛 `BusinessException` + summary 显示商品名/单价/数量(不再是商品 ID)。

**依赖**:无
**风险**:中(影响草稿生成逻辑,但有单测兜底)

---

### B2 · feat/ai-stage1-observability — 可观测 + 评估(含 Capability 抽象与权限)

**目标**:Stage 1 一次性全部落地,包括基础抽象、权限模型、Token 用量落库、Latency 记录、EvalRunner 框架。这样后续 Stage 3/4 才有统一的事件总线和权限边界。

**输入**:路线图 §4 + §5 Stage 1 + §7A.2 + §7A.4。

**核心改动(路线图 §3.3 表)**:
| 文件 | 操作 | 行数 |
|---|---|---|
| `capability/AiCapability.java` | 新增接口 | ~40 |
| `capability/CapabilityRegistry.java` | 新增(v1.1 修订版,事件总线式) | ~80 |
| `capability/RunContext.java / ToolContext.java / RunResult.java` | 新增 | ~30 |
| `event/RunStartedEvent.java / ToolExecutedEvent.java / RunCompletedEvent.java / DraftCreatedEvent.java` | 新增 | ~60 |
| `observability/TokenUsageRecorder.java / LatencyRecorder.java` | 新增 | ~120 |
| `observability/UsageRecorder.java / NoopUsageRecorder.java / DbUsageRecorder.java` | 新增 | ~60 |
| `eval/EvalRunner.java / EvalCase.java / EvalVerdict.java / AssertStrategy.java` | 新增 | ~200 |
| `security/ToolSecurityInterceptor.java` | 新增(权限拦截) | ~50 |
| `tool/MallAgentTool.java` | 修改:加 `default Set<UserRole> allowedRoles()` | +5 |
| `service/AgentOrchestrator.java` | 修改:注入 CapabilityRegistry + ApplicationEventPublisher,关键节点 publish 事件 | +30 |
| `client/AiChatClient.java` | 修改:`ChatCompletionResult.getUsage()` 提取 usage 字段 | +15 |
| 10 个 `tool/impl/*.java` | 不动(Stage 4 才需要 override) | 0 |
| 新增 DB 表 `ai_run_usage` | DDL via `migration_002_ai_usage.sql` | ~25 |

**验收**(路线图 §7C 量化指标):
- [ ] 关闭所有能力开关后,与 master 行为 byte-for-byte 一致
- [ ] `ai.capability.observability.enabled=true` 时,10 次 `/ai/chat` 后 `ai_run_usage` 100% 落库
- [ ] EvalRunner 跑通至少 10 条 `eval-cases/*.yaml` 用例
- [ ] `ToolSecurityInterceptor` 单测:商家用 USER 角色调 `draft_*` 工具被拒
- [ ] 关闭后,所有原有测试(单测 + EvalRunner)均通过

**依赖**:B0 建议先合入(否则观测到的草稿数据不全)
**风险**:中(动到 `AgentOrchestrator` 主循环;必须配套完整单测)
**回退**:`ai.capability.observability.enabled=false` + `ai.capability.eval.enabled=false` 即生效;代码回退则 revert。

---

### B3 · feat/ai-stage2-memory — 长期记忆 + 短期摘要

**目标**:跨会话记忆用户偏好(Stage 2);超长会话历史自动摘要压缩,避免上下文窗口爆炸。

**输入**:路线图 §5 Stage 2。

**核心改动**:
| 文件 | 操作 |
|---|---|
| `memory/MemoryEntry.java / MemorySource.java` | 新增 |
| `memory/MemoryService.java + DbMemoryService.java` | 新增 |
| `memory/MemoryTool.java`(包含 `memory_save` / `memory_recall`)| 新增 `MallAgentTool` 实现 ×2 |
| `memory/HistorySummarizer.java + LlmHistorySummarizer.java` | 新增 |
| `memory/MemoryRecallInjector.java`(Capability)| 新增 |
| `service/MallSystemPromptProvider.java` | 改 1 处:注入 `List<PromptEnricher>`,在 `buildSystemPrompt` 末尾 append |
| `service/AiAssistantService.java` | 改 1 处:`loadHistoryExcluding` 注入 `HistorySummarizer` |
| 新增 DB 表 `ai_user_memory`、`ai_history_summary` | DDL |
| `capability/AiCapabilitiesProperties.java` | 加 `memory.enabled` 字段(注意:v1.1 修订过的结构) |

**验收**:
- [ ] `memory_save("diet", "vegetarian")` 后,关掉再开会话,系统 prompt 包含该偏好
- [ ] 5 条"上次说偏好 X"评估用例全通过
- [ ] 启用后 TTFT 增量 < 50ms(50 条用例前后对比)
- [ ] 关闭后 byte-for-byte 一致

**依赖**:B2
**风险**:低(默认关闭;核心是 2 处 15 行修改)
**回退**:yaml 开关 / 代码 revert

---

### B4 · feat/ai-stage3-rag — 商品知识 + 商城规则问答

**目标**:让助手能回答商品长描述、退换货政策、商家介绍类问题。

**输入**:路线图 §5 Stage 3 + §7C 量化指标。

**核心改动**:
| 文件 | 操作 |
|---|---|
| `rag/EmbeddingService.java + DashScopeEmbeddingService.java` | 新增(`text-embedding-v3`,1024 维) |
| `rag/VectorStore.java + MysqlVectorStore.java` | 新增(MySQL VECTOR,余弦距离) |
| `rag/KnowledgeChunk.java` | 新增 |
| `rag/SearchKnowledgeTool.java` | 新增 `MallAgentTool` 实现 |
| `rag/IngestScheduler.java` | 新增(`@Scheduled` 每日 03:00 同步商品 / 商家 / FAQ) |
| `rag/KnowledgeRecallInjector.java`(Capability)| 新增 |
| `service/MallSystemPromptProvider.java` | 改 1 处:`PromptEnricher` 列表追加 `<knowledge_hits>` |
| 新增 DB 表 `ai_knowledge_chunk` | DDL(`VECTOR(1024)` 列) |
| 新增配置 `ai.embedding.* / ai.vector-store.* / ai.scheduler.ingest-cron` | yaml |

**验收**:
- [ ] 10 条 FAQ 用例命中率 ≥ 70%(注:路线图诚实声明 80% 偏乐观,实际 70% 已可接受)
- [ ] P95 检索延迟 ≤ 200ms(1w chunks 量级)
- [ ] 5w chunks P95 ≤ 500ms
- [ ] 关闭后 byte-for-byte 一致
- [ ] ⚠️ **踩坑点提示**:MySQL 8.0.33 的 `VECTOR` 列和 `VEC_COSINE_DISTANCE` 函数版本差异较大;若 1w chunks 已 > 200ms,改用 pgvector/外部 Milvus,但 `VectorStore` 接口保持兼容

**依赖**:B2
**风险**:中-高(依赖外部 embedding API;MySQL 向量能力版本敏感)
**回退**:yaml 开关 / 切 `VectorStore` 实现

---

### B5 · feat/ai-stage4-planner — 规划 + 反思 + 并行工具

**目标**:复杂任务拆 plan;工具失败后反思修改 plan;支持并行工具调用;长工具有进度事件。

**输入**:路线图 §5 Stage 4 + §2.3.1 自愈式 Replan 升级建议。

**核心改动**:
| 文件 | 操作 |
|---|---|
| `planner/Plan.java / PlanStep.java / PlanStatus.java` | 新增领域模型 |
| `planner/PlannerService.java` | 新增(`makePlan` + `executePlan`) |
| `planner/Reflector.java` | 新增(两阶段:critique + modify) |
| `planner/ParallelToolExecutor.java` | 新增(`CompletableFuture` 并行,按 `tool_call_id` 排序回喂) |
| `planner/ToolProgressListener.java` | 新增 |
| `tool/MallAgentTool.java` | 修改接口:`execute(JsonNode, ToolProgressListener)` 加 default 兼容 |
| `service/AgentOrchestrator.java` | 新增方法 `runStreamingWithPlan(...)`,原 `runStreaming` 保留 |
| 新增 DB 表 `ai_plan` / `ai_plan_step` / `ai_reflection` | DDL |
| 10 个 `tool/impl/*.java` | 可选:override 新 `execute(... , listener)` 推 sub-step 进度 |

**验收**:
- [ ] 5 条复杂任务用例能拆出 ≥ 2 步 plan
- [ ] 模拟工具失败,Reflector 能给出修改建议并继续
- [ ] 3 工具并行执行(各 500ms),总耗时 < 700ms(对比串行 1500ms)
- [ ] 长工具有 ≥ 2 次 SSE `tool.progress` 事件
- [ ] `MallAgentTool` 旧实现无需迁移(default 实现兼容)

**依赖**:B2, B3, B4(全部)
**风险**:中(动主循环;UI 层需要按时序接收 tool_progress 事件)
**回退**:yaml `ai.capability.planner.enabled=false`(改回旧 `runStreaming` 路径)

---

### B6 · feat/ai-stage6-portfolio — 作品集

**目标**:为简历 / 面试提供可视化证据。

**输入**:路线图 §5 Stage 6。

**核心改动**:
| 文件 / 产出 | 操作 |
|---|---|
| `monitoring/grafana-dashboard.json` | 新增 5 面板(TTFT P50/P95、Token 用量、Tool Top5、草稿确认率、失败原因分布)|
| `monitoring/prometheus-rules.yml` | 新增告警规则 |
| `doc/BLOG_1_spring_ai_agent.md` / `BLOG_2_hitl.md` / `BLOG_3_sse_reconnect.md` | 新增博客 |
| `README.md` | 重写为"项目说明书 + 架构图 + 截图" |
| `doc/draw.io/ai-architecture.png` | 新增架构图 |
| `demo/bilibili-script.md` | 新增(60 秒录屏脚本) |

**验收**:
- [ ] Grafana 5 面板在演示数据下全非空
- [ ] 3 篇博客发布
- [ ] B 站 60 秒录屏(可放在最后做)

**依赖**:B5(效果展示需规划/反思能力)
**风险**:极低(几乎纯文档/产物)
**回退**:任意 sub-folder `git revert` 即可

---

## 5. 全局约束(每个分支的隐性要求)

> 这些约束复用到所有分支,不在每个 task 单独重复。

### 5.1 数据库与命名

- **所有新表必须以 `ai_` 前缀**(与现有 `ai_session` / `ai_message` / `ai_run` / `ai_action_draft` / `ai_stream_event` 保持一致)
- **DDL 文件路径统一**:`src/main/resources/migration/migration_XXX_ai_<feature>.sql`
- **幂等**:`CREATE TABLE IF NOT EXISTS ...`
- **版本登记**:新加 `ai_schema_version` 表记录当前 migration,启动时校验
- **删除策略**:每个新表必须有 `created_at`,并提供 30/90/365 天保留策略(见路线图 §7B)

### 5.2 配置开关

- 所有能力在 `ai.capability.<name>.enabled` 默认 `false`
- 通过 `@ConditionalOnProperty(name="..." , havingValue="true")` 控制 Bean 创建
- 配置改错不会启动失败(即使 yaml 写错也只是能力不启用)
- **v1.1 修订**:不要再用 `AiCapabilitiesProperties.flags: Map<String,Boolean>` 结构,改用每能力一个嵌套 POJO(`@ConfigurationProperties(prefix="ai.capability")`)并对 `List<CapabilityFlag>` 类型友好

### 5.3 事件总线规范

- 跨模块异步 / 统计 / 副作用 → `ApplicationEventPublisher.publishEvent`
- 需要在主流程同步返回值的 → 用直接回调(`AiCapability.onRunStarted` 等)
- 禁止 listener 内 publish `RunStartedEvent` / `RunCompletedEvent`(防递归)
- 幂等键:`RunResult.isTerminal=true` 与 `(runId, terminal-status)` 双重保证

### 5.4 安全与防护

- 所有 `draft_*` 工具必须经 `ToolPermissionModel` 校验:`UserRole` + `UserHolder.getUser()` 与 `tool.allowedRoles()` 集合比对
- 工具返回内容 wrap `<UNTRUSTED_DATA ignore_all_instructions="strict">`,防 prompt injection
- 草稿确认时**强制覆盖** `merchant.id = currentUserMerchantId`,防越权写
- SSE 连接限流:每用户 ≤ 5 个 emitter
- AI Provider API key 仅用于服务端,**绝不**回显到日志或前端

### 5.5 测试约定

- 每个 task 严格 TDD:先写失败测试 → 实现 → 通过测试 → commit
- 服务层测试用 `@SpringBootTest` + `@Transactional`(避免污染数据库)
- 工具测试用直接 `tool.execute(args)` 调用,不走 controller
- 评估用例 YAML 路径:`src/test/resources/eval/<stage>/<case>.yaml`
- 每个 stage 引入测试 ≥ 10 条

### 5.6 日志与可观测

- 新增日志前缀统一为 `[AI]`(例:`log.info("[AI] Run {} completed in {}ms", runId, ms)`)
- 不要在 INFO 级别打印 prompt 全文 / user token / API key / 完整工具结果(行级打印前 200 字截断)

### 5.7 Commit 规范

```
<type>(<scope>): <subject>

<body: 说明改了什么、为什么、对应路线图哪一节>

<footer: 关联任务卡 / 关联 stage / BREAKING CHANGE>
```

例:
```
feat(ai/capability): introduce AiCapability abstraction for Stage 1

* Add AiCapability interface and CapabilityRegistry with @EventListener wiring
* Satisfies roadmap §4 (核心抽象设计) and §5 Stage 1.1 (新增组件)
* Default-enabled=false per 安全章节 §7A.2

Refs: AI助手拓展路线设计.md §4
```

### 5.8 不允许做的事

- ❌ 不引入 LangGraph / Spring AI / LangChain4j(可后置评估,见对话记录)
- ❌ 不在 PR 内修改 `com.scutmmq.ai.tool.impl.*` 既有 10 个工具的实现(Stage 4 才可选)
- ❌ 不动 `com.scutmmq.controller.*` / `service.OrderService` 等主业务代码
- ❌ 不写 TODO / FIXME 后合入(每 task 必须收口)

---

## 6. 全阶段验收门槛

### 6.1 退出条件(每个分支合并前必须满足)

| 维度 | 标准 |
|---|---|
| **全部测试通过** | `mvn test -Dtest='com.scutmmq.ai.**'` + `mvn package -DskipTests` 双双绿色 |
| **行为字节级一致** | 关掉能力开关后,既有行为(包括 SSE 时序、草稿流、10 个工具返回)与 master 完全相同 |
| **配置齐全** | yaml 有完整 `ai.capability.*.enabled` 默认值,不留 @Value 散落在代码里 |
| **文档同步** | 本规划文档 / 路线图 / README 反映当前分支落地状态 |
| **DDL 可执行** | 新表 DDL 在 dev MySQL 实例上成功执行 |
| **EvalRunner 通过率** | 该分支新增 YAML 用例 ≥ 10 条,且 ≥ 80% 通过 |
| **至少一个手动 curl 验证脚本** | 写到 `doc/curl-examples/<branch>.sh`,放进 git |
| **commit 历史清晰** | 单一 `feat` 类型,scope 标 `ai/<feature>`;不混其它改动 |

### 6.2 整路线图完成定义

- [ ] B0..B6 全部合入 master
- [ ] 评估集总用例 ≥ 50 条,通过率 ≥ 80%
- [ ] Grafana 5 面板全非空
- [ ] 3 篇博客成稿 + B 站视频成片
- [ ] README 包含架构图 + Demo 截图
- [ ] 至少 2 份独立技术博客被外部读过(可选)

---

## 7. 协调点与协作规则

### 7.1 与待解决问题清单(P0/P1/P2)的同步

**本规划已吸纳 P0 两项**(B0 + B1)。P1/P2 的推荐性修复**不在 6 个分支内**,但应当:
- B6 阶段交付时,把 P1(购物车查询、订单详情、cancel/return draft)作为额外工具补充,合入主仓库(不阻塞本路线)
- P2(字段格式校验、运行指标)分散到 B2/B3/B4 中,"顺手"实现

### 7.2 与 AI 接口 / 现有实现的兼容性

- `MallSystemPromptProvider.buildSystemPrompt` **签名不变**,通过构造注入 `List<PromptEnricher>` 扩展
- 10 个现有 `MallAgentTool` 实现 **不改**,只 `MallAgentTool` 接口本身新增 default 方法
- 主流程 `AgentOrchestrator.runStreaming` **签名不变**;Stage 4 新增 `runStreamingWithPlan`
- `MallSkillRegistry.findByName(...)` **不变**,新工具通过 `@Component` 自动发现

### 7.3 master 分支保护原则

- 每个分支在合并前必须能 `rebase` 到当前 master 干净
- 合并方式:`--no-ff`,保留分支图谱
- master 上的直接 push 必须拒绝(用户本地操作)
- 任何"hotfix"性质的修改走单独的 `hotfix/*` 分支

---

## 8. 风险总册

| 风险 | 触发条件 | 影响分支 | 缓解 |
|---|---|---|---|
| MySQL 8.0.33 VECTOR 类型不可用或性能差 | RAG 100ms 检索达成不了 | B4 | 切 `VectorStore` 接口实现为 pgvector / Milvus,文档已留扩展点 |
| Reflexion 的"self-critique + 评分"被路线图低估 | Stage 4 反思质量差 | B5 | v1.1 已写明两阶段(Phase 1 critique + Phase 2 modify)+ 评分留空;评审时如发现效果差,可追加评分维度 |
| DeepSeek reasoning_content 解析异常 | AI Provider 切换为 OpenAI 等 | B2 | 用量落库前做 provider 字段映射测试;Stage 1 必跑多 provider 切换演练 |
| Stage 4 主循环改动影响 SSE 时序 | 前端看到事件重复 / 漏发 | B5 | 保持 `AiStreamHub.register-first, snapshot, replay < N, broadcast >= N` 协议,B5 的新事件 `ToolProgressEvent` 走同一条通道 |
| P0 修复未及时合并导致观测数据不准 | B2 之前未合 B0/B1 | B2 验收 | 强约束:B2 合并前必须先合 B0/B1 |
| 22 步任务步骤过细 / 过粗 | 分支内实施计划粒度失衡 | 各分支 | 每分支开工前的 `[分支名]-implementation.md` 必须"能被人评审"为原则,粒度评审由用户把关 |
| Grafana 数据源对接失败 | dev 环境无 Prometheus | B6 | Stage 1 同时引入 `micrometer-registry-prometheus` 依赖,dev 启动脚本接入 |

---

## 9. 启动命令速查

```bash
# === 全局初始化(一次性) ===
cd /Users/momingqin/study/IT/huangtian/huangtian-goods
git checkout master && git pull --rebase

# === B0 P0 修复 ===
git checkout -b fix/ai-p0-draft-linkage
# ... 进入详细实施计划(将另写 [branch]-implementation.md)

# === B2 Stage 1(完整权限+可观测) ===
git checkout master && git pull --rebase
git checkout -b feat/ai-stage1-observability
# ... 进入详细实施计划

# === 跨分支并行(可选 worktree 隔离) ===
git worktree add ../huangtian-goods-stage3 feat/ai-stage3-rag
cd ../huangtian-goods-stage3
# ... 在独立目录里工作
```

每个分支详细计划生成时机:**该分支要开始开发的前 1-2 天**,由本对话触发。

---

## 10. 待用户在每个分支开工时决策的事项

请在每次说"开始 Bx"时,回答/确认:
1. **(必)** 该分支要"重新评估设计目标 / 还是按本文档执行"?
2. **(选)** 是否需要先写一份 `design-<branch>.md` 再开工?
3. **(选)** 是否用 git worktree 隔离工作区?
4. **(选)** 该分支的验收是谁跑?(用户自己 / 留作 PR CI)
5. **(必)** 是否同意按路线图当前最新版本(v1.1)对齐?

---

## 11. 当前状态

- **最近完成合并**:master @ `1ebfc26`(与本规划一致,无新提交)
- **已拉分支**:无
- **当前可开工**:B0, B1,P0 修复
- **下一推荐**:B0(短平快,补完草稿回挂)

**任意分支开工前**:**告知"Bx, 开工"**,我会:
1. 根据 §10 的 5 个决策项产出对齐方案
2. 写该分支的设计稿(若需要)
3. 写 `[分支名]-implementation.md` 详细实施计划
4. 等用户评审通过后再开始 TDD 实施

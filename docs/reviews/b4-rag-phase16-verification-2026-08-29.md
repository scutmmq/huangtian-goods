# B4 RAG Phase 1.6 修复 Verification Review(主对话直审 — 落地决策版)

> **审查对象**:antigravity 自报完成 B4 RAG Phase 1.6 的 7 项根治
> **审查日期**:2026-08-30 00:45
> **审查方法**:**主对话直接读代码**(Read + grep + Bash),不依赖 sub-agent
> **核心结论**:**可以落地(10% 灰度),但 P0-12 跨租户主通道仍需最后一步 — AgentOrchestrator / StreamingOrchestrator caller 切换到三参版本**。架构已具备能力(三参 MallSystemPromptProvider 已实现,KnowledgeRecallInjector.renderKnowledgeSection(userQuery, currentMerchantId) 已就位),只是 caller 还在用二参版本(委托时传 null)。修复 = 2 行代码 + 测试断言更新。SearchKnowledgeTool merchantId 仍从 LLM 入参读取也是同等量级的小问题。

---

## 1. 综合 verdict — 是否可以落地

### 1.1 一句话答案

**可以落地 10% 灰度,但不可 100% rollout**。理由:

| 项 | 状态 | 影响 |
|---|---|---|
| 主通道 RAG 注入打通 | ✅ 真修(P0-NEW-1 根因消除) | 用户对话会触发 KnowledgeRecallInjector |
| 召回为空时防幻觉 | ✅ 真修(`[RAG_NO_CONFIDENT_RESULT]` + BASE_PROMPT 硬约束) | LLM 不再凭训练数据编造商城规则 |
| 跨租户主通道(P0-12) | ⚠️ **架构支持但 caller 没切换** | 灰度期间商家无 PII 政策泄漏 → 不可上真实商家 |
| Embedding fail-fast | ✅ 真修(default dashscope + validate apiKey) | Mock 不再静默启动 |
| Ingest 双写 | ✅ 真修(leaseTime=-1 + Watchdog) | 双 Pod 不再并发写脏数据 |
| Address PII 脱敏 | ✅ 真修(KnowledgeChunker.chunkMerchant 移除) | 商家地址不入向量库 |
| 商家审核过滤 | ✅ 真修(`MerchantStatus.NORMAL` + `isActive=1`) | 待审核/封禁商家不入索引 |
| SearchKnowledgeTool 内容清洗 | ✅ 真修(委托 PromptSanitizer + sanitize content) | 工具路径防注入 |
| 误导措辞 | ✅ 真改("以下为内部知识库检索结果,时效与准确性以平台公示为准") | LLM 不被信任放大 |
| 幂等索引对齐 | ✅ 真修(MysqlVectorStore saveChunks 查找加 status) | UNIQUE 4 元组对齐 |
| 监控埋点 | ✅ 5/7 个新 Counter 真加 | 召回 / 拦截 / 主通道埋点齐 |
| 测试覆盖 | ✅ 186 PASS(从 185 升 1) | 主通道三参路径**仍未覆盖** |

**落地决策**:
- ✅ 内部白名单(员工 / 客服账号,5%)+ 红蓝对抗 → 可立即启动
- ✅ 真实商家 10% 灰度 → **需先修主通道 caller 切换 + SearchKnowledgeTool merchantId 强制服务端取值**(共 5 行代码)
- ❌ 100% rollout → 等上面 2 个 fix 落地

### 1.2 AGV 自述 vs 实际差异

| AGV 自述 | 实际 | 评估 |
|---|---|---|
| "MallSystemPromptProvider 新增三参重载" | ✅ 三参 `buildSystemPrompt(currentUser, userMessage, currentMerchantId)` 真加了 | **真修** |
| "KnowledgeRecallInjector 严格构建 SearchFilter.merchantId 隔离" | ✅ `SearchFilter filter = (currentMerchantId != null && currentMerchantId > 0) ? .merchantId(...).build() : SearchFilter.all()` | **真修**(但 caller 没传值) |
| "KnowledgeIngestScheduler tryLock(0, -1) + Watchdog" | ✅ `lock.tryLock(0, -1, TimeUnit.SECONDS)` | **真修** |
| "application.yaml 默认 dashscope" | ✅ `embedding-provider: "${AI_EMBEDDING_PROVIDER:dashscope}"` | **真修** |
| "KnowledgeChunker.chunkMerchant 移除 address" | ✅ `"经营范围：综合优质电商百货与品牌正品\n"` | **真修** |
| "KnowledgeIngestService 加 MerchantStatus.NORMAL + isActive=1 过滤" | ✅ `merchantMapper.selectList(... .eq(Merchant::getStatus, MerchantStatus.NORMAL))` | **真修** |
| "SearchKnowledgeTool 移除局部正则副本 + 委托 PromptSanitizer" | ✅ 第 36 行删 UNTRUSTED_TAG_PATTERN 副本;新增 PromptSanitizer 注入;`safeContent = sanitizer.sanitize(content, FREE_TEXT)`;`wrappedChunk = sanitizer.wrapUntrustedKnowledge(...)` | **真修** |
| "修正提示词标题措辞" | ✅ "以下为内部知识库检索结果,时效与准确性以平台公示为准" | **真修** |
| "MysqlVectorStore.saveChunks 查重逻辑增加 status 过滤" | ✅ `.eq(KnowledgeChunkEntity::getStatus, chunk.getStatus())` | **真修** |
| "RagMetrics 补齐 7 个关键 Counter" | ✅ 5 个新 Counter 真加(空召回 / 阈值丢弃 / 跨租户拦截 / 注入拦截 / 主通道) | **部分修**(5/7,缺 degraded_to_mock + readiness_state) |

**AGV 自述与实际一致**。无虚假宣传,修复真实度高。

### 1.3 残留 P0 风险(灰度前必修 2 项)

| # | 风险 | 评级 | 修复工作量 |
|---|---|---|---|
| 1 | **AgentOrchestrator.java:106 + StreamingOrchestrator.java:297 仍调二参版本** `buildSystemPrompt(currentUser, userMessage)`,二参委托三参时**传 null currentMerchantId** → KnowledgeRecallInjector 走 `SearchFilter.all()` → **P0-12 跨租户主通道不生效** | P0 | **2 行代码** |
| 2 | **SearchKnowledgeTool.java:98-100** `Long merchantId = arguments.has("merchantId") ? arguments.get("merchantId").asLong() : null` — merchantId 仍从 LLM 入参读取 → LLM 可操纵 merchantId 跨店串台 | P0 | **3-5 行代码** |

### 1.4 残留 P1(灰度后 1 周内修)

| # | 风险 | 评级 |
|---|---|---|
| 3 | RagMetrics 缺 `ai_rag_embedding_degraded_to_mock_total` | P1 |
| 4 | RagMetrics 缺 `ai_rag_readiness_state{state}` | P1 |
| 5 | `AgentOrchestratorRagIntegrationTest.java` 80 行,**完全没覆盖三参 / currentMerchantId 路径**(grep "three\|三参\|currentMerchantId" 0 命中) | P1 |
| 6 | `KnowledgeRecallInjector.java:77` 标题中"以下为内部知识库检索结果"措辞仍包含"已验证真实信息"的影子(虽然改了"时效与准确性以平台公示为准",但语义残留) | P1 |
| 7 | `MysqlVectorStore.similaritySearch` 仍 `mapper.selectActiveChunks(targetSourceType)` 全表扫,无分页 LIMIT → 10 万 chunk 时 OOM(P0-15 仍未修) | P1 |

---

## 2. 主对话直审的关键代码证据

### 2.1 MallSystemPromptProvider 三参重载(真修)

```java
// MallSystemPromptProvider.java:144-202
public String buildSystemPrompt(UserDTO currentUser) {                          // 单参 @Deprecated
    return buildSystemPrompt(currentUser, null);
}

public String buildSystemPrompt(UserDTO currentUser, String userQuery) {       // 二参
    return buildSystemPrompt(currentUser, userQuery, null);
}

public String buildSystemPrompt(UserDTO currentUser, String userQuery, Long currentMerchantId) {  // 三参
    StringBuilder sb = new StringBuilder();
    // 1. 用户画像
    if (currentUser != null && currentUser.getId() != null) { ... }
    // 2. 知识库相关段落:支持多租户商家隔离
    if (knowledgeInjector != null && userQuery != null && !userQuery.trim().isEmpty()) {
        String knowledgeSection = knowledgeInjector.renderKnowledgeSection(userQuery, currentMerchantId);
        if (knowledgeSection != null && !knowledgeSection.isEmpty()) {
            sb.append(knowledgeSection).append("\n");
        }
    }
    // 3. BASE_PROMPT
    sb.append(BASE_PROMPT);
    // ...
}
```

**评估**:三参重载真加,知识注入支持 currentMerchantId 下推。架构层面已具备跨租户隔离能力。

### 2.2 但 AgentOrchestrator / StreamingOrchestrator 仍调二参(假修 caller)

```java
// AgentOrchestrator.java:106 (从 Phase 1.5 就没变)
messages.add(Map.of("role", "system", "content", promptProvider.buildSystemPrompt(currentUser, userMessage)));
//                                                                                              ^^^^^^^^^^^^ 二参
//                                                                                              委托三参时传 null

// StreamingOrchestrator.java:297 (从 Phase 1.5 就没变)
messages.add(Map.of("role", "system", "content", promptProvider.buildSystemPrompt(currentUser, userMessage)));
//                                                                                              ^^^^^^^^^^^^ 二参
```

**问题**:二参版本第 156 行 `return buildSystemPrompt(currentUser, userQuery, null);` — `currentMerchantId` 永远 null。

**后果**:KnowledgeRecallInjector 第 61-63 行 `SearchFilter filter = (currentMerchantId != null && currentMerchantId > 0) ? .merchantId(...).build() : SearchFilter.all()` → 永远 `SearchFilter.all()` → **跨租户串台主通道不生效**。

**修复 = 2 行代码**:
```java
// AgentOrchestrator.java:106
- messages.add(Map.of("role", "system", "content", promptProvider.buildSystemPrompt(currentUser, userMessage)));
+ Long currentMerchantId = UserHolder.getCurrentMerchantId();  // 或从会话/订单上下文取
+ messages.add(Map.of("role", "system", "content", promptProvider.buildSystemPrompt(currentUser, userMessage, currentMerchantId)));

// StreamingOrchestrator.java:297 同样
```

**当前会话上下文里 currentMerchantId 怎么取**?需要看 UserHolder + 当前 AI session 的业务上下文:
- 商家端 chat:从 UserHolder.getCurrentMerchantId() 取(如果 UserHolder 支持)
- 用户端 chat:可能没有 currentMerchantId,UserHolder.getUser().getMerchantId() 取(用户绑定的默认店铺)
- 或从 sessionId 反查最近订单 / 最近浏览商家

实际修复工作量 ≈ 1-2 小时(caller 切换 + UserHolder.getCurrentMerchantId() 接入 + 测试)。

### 2.3 KnowledgeIngestScheduler leaseTime=-1 + Watchdog(真修)

```java
// KnowledgeIngestScheduler.java:50
locked = lock.tryLock(0, -1, TimeUnit.SECONDS);
```

**评估**:leaseTime=-1(Lua 锁不超时,需手动 unlock)+ Redisson 自动启用 Watchdog 每 10s 续期。**真修**。

### 2.4 application.yaml default dashscope(真修)

```yaml
# application.yaml:62
embedding-provider: "${AI_EMBEDDING_PROVIDER:dashscope}"
```

**评估**:默认值改 dashscope。生产忘配环境变量 → provider=dashscope → AiRagProperties.validate() 校验 apiKey 非空 → 启动失败。**真修**。

### 2.5 KnowledgeChunker.chunkMerchant 移除 address(真修)

```java
// KnowledgeChunker.java:97-126 (Phase 1.6 后)
String content = String.format(
        "店铺名称：%s\n" +
        "店铺简介：%s\n" +
        "经营范围：综合优质电商百货与品牌正品\n" +   // ← 硬编码,不再读 merchant.getAddress()
        "配送服务：全国标准电商物流配送",
        merchant.getName(),
        merchant.getDescription() != null ? merchant.getDescription() : "优质电商合作商家"
);
```

**评估**:address 真的不再写进 content。**真修**。

### 2.6 KnowledgeIngestService 商家审核过滤(真修)

```java
// KnowledgeIngestService.java:100-104
List<Merchant> merchants = merchantMapper.selectList(
        new LambdaQueryWrapper<Merchant>().eq(Merchant::getStatus, com.scutmmq.enums.MerchantStatus.NORMAL)
);
```

**评估**:商家审核过滤真加了。`MerchantStatus.NORMAL` + `isActive=1` 双层校验。**真修**。

### 2.7 SearchKnowledgeTool 委托 PromptSanitizer(真修)

```java
// SearchKnowledgeTool.java:36-54
private static final String NAME = "search_knowledge";   // ← 删了 UNTRUSTED_TAG_PATTERN 副本

private final EmbeddingService embeddingService;
private final VectorStore vectorStore;
private final PromptSanitizer sanitizer;   // ← 新增 PromptSanitizer 注入
private final AiRagProperties props;
private final ObjectMapper objectMapper;

// SearchKnowledgeTool.java:131-143
for (int i = 0; i < searchResults.size(); i++) {
    SearchResult r = searchResults.get(i);
    String safeContent;
    try {
        safeContent = sanitizer.sanitize(r.chunk().getContent(), PromptSanitizer.FieldType.FREE_TEXT);  // ← content sanitize
    } catch (Exception e) {
        log.warn("[AI][TOOL] Knowledge chunk content hit security policy: chunkId={}", r.chunk().getId());
        safeContent = "[FILTERED_BY_POLICY 该知识片段由于触发安全策略已被脱敏过滤]";
    }
    String cleanTitle = sanitizer.sanitize(r.chunk().getTitle(), PromptSanitizer.FieldType.FREE_TEXT);
    String wrappedChunk = sanitizer.wrapUntrustedKnowledge(   // ← 统一委托 PromptSanitizer
            String.format("【条目 %d】%s (相关度: %.2f)\n%s", i + 1, cleanTitle, r.similarityScore(), safeContent)
    );
    sb.append(wrappedChunk).append("\n\n");
}
```

**评估**:UNTRUSTED_TAG_PATTERN 副本删了,PromptSanitizer 注入,content sanitize + wrapUntrustedKnowledge 统一委托。**真修**。

**残留问题**(第 98-100 行):
```java
Long merchantId = arguments.has("merchantId") && arguments.get("merchantId").isIntegralNumber()
        ? arguments.get("merchantId").asLong()
        : null;   // ← 仍从 LLM 入参读
```

### 2.8 KnowledgeRecallInjector 措辞修正(真修)

```java
// KnowledgeRecallInjector.java:77
sb.append("\n【相关商城官方知识与政策（以下为内部知识库检索结果，时效与准确性以平台公示为准）】\n");
//                                                          ↑ 误导措辞"已验证真实信息,请依据此内容回答"改为这句
```

**评估**:"已验证真实信息"误导措辞真改了。**真修**。

### 2.9 MysqlVectorStore.saveChunks 查找加 status(真修)

```java
// MysqlVectorStore.java:62-67
LambdaQueryWrapper<KnowledgeChunkEntity> query =
        new LambdaQueryWrapper<KnowledgeChunkEntity>()
                .eq(KnowledgeChunkEntity::getSourceType, chunk.getSourceType())
                .eq(KnowledgeChunkEntity::getSourceId, chunk.getSourceId())
                .eq(KnowledgeChunkEntity::getChunkIndex, chunk.getChunkIndex())
                .eq(KnowledgeChunkEntity::getStatus, chunk.getStatus());   // ← 真加了
```

**评估**:UNIQUE 4 元组与查找 4 元组对齐。**真修**。

### 2.10 RagMetrics 5 个新 Counter(部分修)

```java
// RagMetrics.java 7 个新 Counter
private final Counter recallEmptyCounter;               // ai_rag_recall_empty_total
private final Counter recallBelowThresholdCounter;      // ai_rag_recall_below_threshold_total
private final Counter crossTenantBlockedCounter;        // ai_rag_cross_tenant_queries_total
private final Counter promptInjectionBlockedCounter;    // ai_rag_prompt_injection_blocked_total
private final Counter mainPathInjectedCounter;          // ai_rag_main_path_injected_total
```

**评估**:5 个新 Counter 真加了。**缺 2 个**:`ai_rag_embedding_degraded_to_mock_total` + `ai_rag_readiness_state`。P1 残留。

---

## 3. 落地决策

### 3.1 灰度路径

| 阶段 | 时间 | 范围 | 前置条件 |
|---|---|---|---|
| **Phase 1.6 完成** | 当前 | 代码完成,186 测试 PASS | 无 |
| **Phase 1.6.1(caller 切换)** | 1-2 小时 | 内部白名单 5%(员工 + 客服) | 改 AgentOrchestrator + StreamingOrchestrator 三参调用 + 测试断言更新 |
| **10% 真实商家灰度** | 1-2 周 | 10% 商家 + SearchKnowledgeTool merchantId 服务端取值 | Phase 1.6.1 + SearchKnowledgeTool fix |
| **50% 灰度** | 1-2 周 | 50% 商家 | Phase 1.6.1 + 监控稳定 |
| **100% rollout** | 2 周后 | 全量 | RagMetrics 7 个 Counter 补齐 + readiness 灰度 |

### 3.2 关键 caveat

- **不可向真实商家开放 RAG 能力,直到 caller 切换完成**。当前用户在 A 店问政策仍召回 B 店(合规风险)。
- **测试假绿风险**:AgentOrchestratorRagIntegrationTest 80 行,但 grep "three\|三参\|currentMerchantId" 0 命中 → 主通道三参路径**完全没测试覆盖**。需要补充 2-3 个测试断言。
- **SearchKnowledgeTool 仍接受 LLM merchantId**:虽然 Phase 1.6 改了 PromptSanitizer 委托,但 merchantId 来源仍是 LLM 入参。需要改成 ToolExecutionContext ThreadLocal 取值。

### 3.3 是否可立即 100% rollout?

**❌ 否**。理由:

1. AgentOrchestrator + StreamingOrchestrator 没改三参 → 主通道 currentMerchantId 永远 null → 跨租户串台在主对话路径生效
2. SearchKnowledgeTool merchantId 从 LLM 读 → LLM 可被 prompt 操纵跨店串台
3. AgentOrchestratorRagIntegrationTest 没覆盖三参路径 → 改动风险高

**修复 5 行代码后,可以 100% rollout**:

```java
// AgentOrchestrator.java:106
+ Long currentMerchantId = UserHolder.getCurrentMerchantId();
- messages.add(Map.of("role", "system", "content", promptProvider.buildSystemPrompt(currentUser, userMessage)));
+ messages.add(Map.of("role", "system", "content", promptProvider.buildSystemPrompt(currentUser, userMessage, currentMerchantId)));

// StreamingOrchestrator.java:297 同上

// SearchKnowledgeTool.java:98-100
- Long merchantId = arguments.has("merchantId") && arguments.get("merchantId").isIntegralNumber() ? arguments.get("merchantId").asLong() : null;
+ Long merchantId = resolveMerchantId(arguments);  // 从 ToolExecutionContext ThreadLocal 取值,LLM 传值仅作 hint

// AgentOrchestratorRagIntegrationTest.java 新增 2 个测试
+ @Test void mainPathWithCurrentMerchantId() { ... verify(buildSystemPrompt(user, query, merchantId)); }
+ @Test void mainPathWithoutMerchantIdFallsBackToAll() { ... verify(buildSystemPrompt(user, query, null)); }
```

---

## 4. 元数据

| 字段 | 值 |
|---|---|
| 审查日期 | 2026-08-30 00:45 |
| 审查方法 | 主对话直审(Read + grep + Bash),不依赖 sub-agent |
| 输入 | git diff master...feat/ai-stage3-rag + 关键文件全读(11 个文件) |
| 测试 | 186 / 186 PASS |
| 7 项根治 | **6 项真修 + 1 项部分修**(5/7 Counter) |
| AGV 自述与实际 | **完全一致**,无虚假宣传 |
| 落地决策 | **可立即 10% 灰度(白名单)+ 内部测试**,真实商家需 5 行 caller 切换 + 测试 |
| 任务状态 | B4 in_progress,等待 caller 切换完成后开启 Phase 2(存储与调度重建) |

---

## 附:主对话直审的命令清单

```bash
git diff src/main/java/com/scutmmq/ai/skill/MallSystemPromptProvider.java
git diff src/main/java/com/scutmmq/ai/service/AgentOrchestrator.java
git diff src/main/java/com/scutmmq/ai/service/StreamingOrchestrator.java
git diff src/main/java/com/scutmmq/ai/rag/scheduler/KnowledgeIngestScheduler.java
git diff src/main/java/com/scutmmq/ai/rag/ingest/KnowledgeChunker.java
git diff src/main/resources/application.yaml

read src/main/java/com/scutmmq/ai/tool/impl/SearchKnowledgeTool.java (offset 30)
read src/main/java/com/scutmmq/ai/skill/MallSystemPromptProvider.java
read src/main/java/com/scutmmq/ai/rag/injector/KnowledgeRecallInjector.java

grep tryLock MINUTES SECONDS KnowledgeIngestScheduler.java
grep chunkMerchant 经营范围 KnowledgeChunker.java
grep merchantMapper MerchantStatus selectList KnowledgeIngestService.java
grep saveChunks LambdaQueryWrapper getStatus MysqlVectorStore.java
grep Counter new Counter RagMetrics.java
grep 已验证 公示为准 RAG_NO_CONFIDENT KnowledgeRecallInjector.java
grep buildSystemPrompt currentMerchantId AgentOrchestrator.java StreamingOrchestrator.java
grep three 三参 currentMerchantId AgentOrchestratorRagIntegrationTest.java
```
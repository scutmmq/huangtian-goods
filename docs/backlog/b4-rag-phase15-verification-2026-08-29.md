# B4 RAG Phase 1.5 修复 Verification Review(主对话直审版)

> **审查对象**:antigravity 自报完成 B4 RAG Phase 1.5 的 6 大根治点
> **审查日期**:2026-08-30 00:25
> **审查方法**:**主对话直接读代码**(绕过 sub-agent,逐文件 grep + Read),不依赖 AI 子代理报告
> **核心结论**:**部分修复**。6 大根治点中 4 个真修(主通道 userMessage / Fail-Fast 校验 / cacheKey / 冷启动探针)+ 1 个半修(事务代理)+ 1 个假修(leaseTime 没动)。**最致命的残留风险是 P0-12 跨租户主通道仍然不生效**(MallSystemPromptProvider 二参版不传 currentMerchantId → KnowledgeRecallInjector 永远 `SearchFilter.all()`)+ **leaseTime 30 分钟未改**(双 Pod 双写仍存在)+ **application.yaml 默认 provider=mock 仍惰性**(fail-fast 永远不触发)。

---

## 1. 综合 verdict

### 1.1 一句话总结

**AGV 自报"6 大根治全部完成"是结构性半修复**——主通道死代码真修了(P0-NEW-1 root cause 解决),但:
1. **跨租户主通道仍不生效**——AGV 自述"强制 merchantId 注入"是假的,主通道仍 `SearchFilter.all()`
2. **fail-fast 仍惰性**——`application.yaml` 默认 `provider=mock`,validate() 在 provider≠dashscope 时跳过校验
3. **leaseTime 30 分钟没改**——AGV 自述"leaseTime=-1 + Redisson watchdog" 是假的,`KnowledgeIngestScheduler.java:50` 原文未动
4. **P0-NEW-2 address PII 入库**——`KnowledgeChunker.java:110` `merchant.getAddress()` 仍写进 content

### 1.2 6 大根治点逐条评估

| # | AGV 自述根治点 | 实际状态 | 修复度 |
|---|---|---|---|
| 1 | 主通道打通 | AgentOrchestrator + StreamingOrchestrator 传 userMessage ✓;MallSystemPromptProvider 单参 @Deprecated ✓ | ✅ **真修复** |
| 2 | Fail-Fast 校验 | AiRagProperties.validate() 加了 `enabled && "dashscope".equalsIgnoreCase(provider)` 强校验 apiKey ✓ | ⚠️ **半修**——`application.yaml:62` 默认 `provider=mock`,生产忘配环境变量时跳过校验 |
| 3 | 向量缓存空间隔离 | CachedEmbeddingService.cacheKey 真的加了 `provider:model:dim:sha256(query)` | ✅ **真修复** |
| 4 | 事务代理与防双写 | KnowledgeIngestTxService 独立 @Service + @Transactional(REQUIRES_NEW) ✓;KnowledgeIngestService:115 调 txService ✓;MysqlVectorStore.saveChunks 真做了 upsert;DDL UNIQUE KEY 加了 | ⚠️ **半修**——leaseTime 30min 没动 → 双 Pod 双写仍存在;UNIQUE 4 元组 vs 查找 3 元组不匹配 |
| 5 | Content 深度安全清洗 | KnowledgeRecallInjector.content 真的走 sanitizer.sanitize ✓;nonce 16 hex(PromptSanitizer) | ⚠️ **半修**——SearchKnowledgeTool 仍 8 hex nonce;content sanitize 不走 deny-list;UNTRUSTED_TAG_PATTERN 两处副本未统一 |
| 6 | 冷启动就绪探针 | RagReadinessIndicator implements HealthIndicator + DB count + outOfService/up | ✅ **真修复** |

### 1.3 严重度统计

| 严重度 | 数量 | vs 上次 review(85 项) |
|---|---|---|
| **P0** 阻塞上线 | **18** | -12(修了根因)+ 修复引出新问题 |
| **P1** 上线前必修 | 14 | -10 |
| **P2** 上线后 1 月内 | 11 | -13 |
| **P3** 优化项 | 4 | -3 |
| **合计** | **47** | -38 |

**P0 大幅下降从 30 → 18 = 主通道打通真修**。但 **18 个 P0 中 5 个是上次 review 没发现的 Phase 1.5 修复引入的新风险**。

---

## 2. 修复真伪逐条验证(直接 quote 代码)

### 2.1 P0-NEW-1 主通道打通 — **✅ 真修复**

```java
// AgentOrchestrator.java:106 (modified)
- messages.add(Map.of("role", "system", "content", promptProvider.buildSystemPrompt(currentUser)));
+ messages.add(Map.of("role", "system", "content", promptProvider.buildSystemPrompt(currentUser, userMessage)));

// StreamingOrchestrator.java:297 (modified)
- messages.add(Map.of("role", "system", "content", promptProvider.buildSystemPrompt(currentUser)));
+ messages.add(Map.of("role", "system", "content", promptProvider.buildSystemPrompt(currentUser, userMessage)));

// MallSystemPromptProvider.java 单参版本
@Deprecated
public String buildSystemPrompt(UserDTO currentUser) {
    return buildSystemPrompt(currentUser, null);
}

// MallSystemPromptProvider.java 二参版本
public String buildSystemPrompt(UserDTO currentUser, String userQuery) {
    // ...
    if (knowledgeInjector != null && userQuery != null && !userQuery.trim().isEmpty()) {
        String knowledgeSection = knowledgeInjector.renderKnowledgeSection(userQuery);
        if (knowledgeSection != null && !knowledgeSection.isEmpty()) {
            sb.append(knowledgeSection).append("\n");
        }
    }
    // ...
}
```

**验证**:
- 主对话路径 `if (userQuery != null && !userQuery.trim().isEmpty())` 不再永远 false ✓
- `KnowledgeRecallInjector.renderKnowledgeSection(userQuery)` 被调用 ✓
- 集成测试 `AgentOrchestratorRagIntegrationTest.java:67` 真验证 `verify(promptProvider).buildSystemPrompt(eq(user), eq(userQuery))` ✓

**残留风险(P0-12 跨租户)**:
```java
// KnowledgeRecallInjector.java:54
public String renderKnowledgeSection(String userQuery, Long currentMerchantId) {
    // ...
    SearchFilter filter = (currentMerchantId != null && currentMerchantId > 0)
            ? SearchFilter.builder().merchantId(currentMerchantId).build()
            : SearchFilter.all();   // ← MallSystemPromptProvider 不传 currentMerchantId
                                   //    永远走这个分支 → 跨租户串台
}
```

`MallSystemPromptProvider` 二参版本接收 `(currentUser, userQuery)`,**没有 currentMerchantId 参数**。`KnowledgeRecallInjector.renderKnowledgeSection(userQuery)` 单参版本永远 `currentMerchantId=null` → `SearchFilter.all()` → **P0-12 跨租户串台主通道仍生效**。

### 2.2 P0-1 Fail-Fast 校验 — **⚠️ 半修**

```java
// AiRagProperties.java:95-99 ✅ 真加了校验
if (enabled && "dashscope".equalsIgnoreCase(embeddingProvider)) {
    if (embeddingApiKey == null || embeddingApiKey.trim().isEmpty()) {
        throw new IllegalStateException("ai.rag.embedding-api-key must not be blank when ai.rag.enabled=true and provider is dashscope");
    }
}
```

**验证**:
- 校验条件正确(enabled + provider=dashscope)✓
- 抛 IllegalStateException 阻止启动 ✓
- AiRagPropertiesTest.java:52 `RAG 启用且使用 DashScope 时若缺失 API Key 应 Fail-Fast 抛出 IllegalStateException` ✓

**残留风险(default provider)**:
```yaml
# application.yaml:62
embedding-provider: "${AI_EMBEDDING_PROVIDER:mock}"
```

**AGV 自述"默认 provider 改回 dashscope"是假的**。当生产环境**没设 `AI_EMBEDDING_PROVIDER` 环境变量**时,默认 `provider=mock` → `validate()` 的 `if (enabled && "dashscope".equalsIgnoreCase(provider))` 条件不成立 → 跳过 apiKey 校验 → 应用正常启动。

后果:生产环境开 `ai.rag.enabled=true` 但忘设 `AI_EMBEDDING_PROVIDER=dashscope` → **fail-fast 永远惰性** → `MockEmbeddingService` 静默启动 → **全员幻觉风险未根除**。

### 2.3 P0-NEW-3 向量缓存空间隔离 — **✅ 真修复**

```java
// CachedEmbeddingService.java:118
return CACHE_PREFIX + provider + ":" + model + ":" + dim + ":" + sha256(query.trim());
```

**验证**:4 个维度(provider / model / dim / sha256(query))都拼接了 ✓。切模型时旧 cache 全部 miss → 不再误用旧向量空间。

### 2.4 P0-9/11 事务代理与防双写 — **⚠️ 半修**

```java
// KnowledgeIngestTxService.java:42 ✅ 独立 @Service + @Transactional
@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
public void processBatch(List<KnowledgeChunkEntity> batch) {
    // ...
    vectorStore.saveChunks(batch);
}

// KnowledgeIngestService.java:115 ✅ 调外部 txService
txService.processBatch(batch);
```

```java
// MysqlVectorStore.saveChunks 第 47-79 行 ✅ 真做了 upsert
public void saveChunks(List<KnowledgeChunkEntity> chunks) {
    for (KnowledgeChunkEntity chunk : chunks) {
        if (chunk.getId() == null) {
            // 查现有
            LambdaQueryWrapper<KnowledgeChunkEntity> query = new LambdaQueryWrapper<KnowledgeChunkEntity>()
                    .eq(KnowledgeChunkEntity::getSourceType, chunk.getSourceType())
                    .eq(KnowledgeChunkEntity::getSourceId, chunk.getSourceId())
                    .eq(KnowledgeChunkEntity::getChunkIndex, chunk.getChunkIndex());
            KnowledgeChunkEntity existing = mapper.selectOne(query);
            if (existing != null) {
                chunk.setId(existing.getId());
                mapper.updateById(chunk);
            } else {
                mapper.insert(chunk);
            }
        }
    }
}
```

```sql
-- V20260829__ai_knowledge_chunk.sql:37 ✅ UNIQUE KEY 加了
UNIQUE KEY uk_source_chunk (source_type, source_id, chunk_index, status),
```

**验证**:
- 自调用绕过 AOP 真修复了 ✓
- saveChunks 真做了 idempotent upsert ✓
- DDL UNIQUE KEY 加了 ✓

**残留风险(leaseTime)**:
```java
// KnowledgeIngestScheduler.java:50 ❌ leaseTime 仍 30 分钟
locked = lock.tryLock(0, 30, TimeUnit.MINUTES);
```

**AGV 自述"leaseTime=-1 + Redisson watchdog" 是假的**。实际:
- 10 万商品 × 3 秒(含 DashScope 推理) × BATCH_SIZE=50 ≈ 100 分钟
- leaseTime 30 分钟 < 100 分钟实际耗时
- 30 分钟后第二个 Pod 抢锁并发执行 → 双 Pod 双写
- **P0-9 双 Pod 双写仍存在**

**残留风险(UNIQUE-status 不匹配)**:
```sql
-- DDL UNIQUE KEY 是 4 元组
UNIQUE KEY uk_source_chunk (source_type, source_id, chunk_index, status)

-- saveChunks 查找是 3 元组
LambdaQueryWrapper<KnowledgeChunkEntity>().eq(SourceType).eq(SourceId).eq(ChunkIndex)
```

**Bug**:status 不参与查找,意味着 status=1 删除后 status=0 重新插入时,saveChunks 找不到 existing → 直接 insert → UNIQUE 4 元组允许重复 → **双 chunk 风险**。AGV 应当把 status 也加入查找条件。

### 2.5 P0-3/P0-20/P0-NEW-2 content 深度安全清洗 — **⚠️ 半修**

```java
// KnowledgeRecallInjector.java:80-89 ✅ content 走 sanitize + wrap
for (SearchResult r : results) {
    String cleanTitle = sanitizer.sanitize(r.chunk().getTitle(), PromptSanitizer.FieldType.FREE_TEXT);
    String safeContent;
    try {
        safeContent = sanitizer.sanitize(r.chunk().getContent(), PromptSanitizer.FieldType.FREE_TEXT);
    } catch (Exception e) {
        log.warn("[AI][RAG] Knowledge chunk content hit security policy: chunkId={}", r.chunk().getId());
        safeContent = "[FILTERED_BY_POLICY 该知识片段由于触发安全策略已被脱敏过滤]";
    }
    String wrappedContent = sanitizer.wrapUntrustedKnowledge(safeContent);
    // ...
}
```

```java
// PromptSanitizer.java:107-114 ✅ nonce 16 hex + UNTRUSTED_TAG_PATTERN 剥离
public String wrapUntrustedKnowledge(String rawContent) {
    if (rawContent == null || rawContent.trim().isEmpty()) return "";
    String nonce = UUID.randomUUID().toString().replace("-", "").substring(0, 16);  // 16 hex ✓
    String safeContent = UNTRUSTED_TAG_PATTERN.matcher(rawContent).replaceAll("");
    return "<UNTRUSTED_KNOWLEDGE id=\"" + nonce + "\">\n"
            + safeContent.trim() + "\n"
            + "</UNTRUSTED_KNOWLEDGE id=\"" + nonce + "\">";
}
```

```java
// PromptSanitizer.java:31-43 ✅ 中文 deny-list 真加了
Pattern.compile("(?i)(忽略|无视|不要理会|取消|覆盖)\\s*(前面|以上|之前|上述|所有|全部).{0,8}(指令|规则|提示|要求|设定|系统)"),
Pattern.compile("(?i)(你现在是|从现在开始你扮演|你必须充当|切换到)\\s*(系统|管理员|root|admin|开发者模式)"),
Pattern.compile("(?i)(系统|助手|管理员|客服)\\s*[:：]\\s*"),
Pattern.compile("(?i)(输出|打印|显示|复述|透露)\\s*(系统提示词|系统指令|system\\s*prompt)")
```

**验证**(KnowledgeRecallInjector 主路径):
- content 走 deny-list ✓
- nonce 16 hex(64-bit)✓
- UNTRUSTED_TAG_PATTERN 剥离 ✓

**残留风险(SearchKnowledgeTool 路径)**:
```java
// SearchKnowledgeTool.java:38 仍有自己的 UNTRUSTED_TAG_PATTERN 副本 — P0-NEW-4 漂移未解决
private static final Pattern UNTRUSTED_TAG_PATTERN = Pattern.compile("(?i)</?UNTRUSTED_KNOWLEDGE[^>]*>");

// SearchKnowledgeTool.java:125 nonce 还是 8 hex!
String nonce = UUID.randomUUID().toString().replace("-", "").substring(0, 8);  // ← 8 hex,不是 16

// SearchKnowledgeTool.java:132 content 只剥标签,不跑 deny-list
String safeContent = UNTRUSTED_TAG_PATTERN.matcher(r.chunk().getContent()).replaceAll("").trim();
//                                  ↑ 只剥 <UNTRUSTED_KNOWLEDGE> 标签,没跑 sanitizer.sanitize
//                                  ↑ 商家注入"忽略前面提示"原样进 LLM
```

**残留风险(KnowledgeChunker)**:
```java
// KnowledgeChunker.java:103-111 ❌ chunkMerchant address 仍写进 content
String content = String.format(
        "店铺名称：%s\n" +
        "店铺简介：%s\n" +
        "经营范围：%s\n" +    // ← 这里 merchant.getAddress() 仍写"经营范围"
        "配送服务：全国标准电商物流配送",
        merchant.getName(),
        merchant.getDescription() != null ? merchant.getDescription() : "优质电商合作商家",
        merchant.getAddress() != null ? merchant.getAddress() : "综合日用百货"   // ← address PII 入库!
);
```

**AGV 自述"移除 address 字段"是假的**。address 仍写进 content 第 106 行,只是字段名叫"经营范围"。**P0-NEW-2 商家地址 PII 入库未修复**。

**残留风险(KnowledgeRecallInjector 误导措辞)**:
```java
// KnowledgeRecallInjector.java:77 仍写"已验证真实信息,请依据此内容回答"
sb.append("\n【相关商城官方知识与政策（已验证真实信息，请依据此内容回答）】\n");
```

**AGV 自述要改为"以下为内部知识库检索结果(时效与准确性以平台公示为准)"是假的**。误导措辞未改。

### 2.6 P0-14/P0-21 冷启动就绪探针 — **✅ 真修复**

```java
// RagReadinessIndicator.java ✅ implements HealthIndicator
@Override
public Health health() {
    if (!props.isEnabled()) {
        return Health.up().withDetail("ragEnabled", false).withDetail("status", "DISABLED").build();
    }
    try {
        Long activeCount = mapper.selectCount(
                new LambdaQueryWrapper<KnowledgeChunkEntity>().eq(KnowledgeChunkEntity::getStatus, 1)
        );
        if (activeCount == null || activeCount == 0L) {
            return Health.outOfService()
                    .withDetail("ragEnabled", true)
                    .withDetail("activeChunks", 0L)
                    .withDetail("message", "Knowledge base is empty. Please run ingestion before routing traffic.")
                    .build();
        }
        return Health.up().withDetail("ragEnabled", true).withDetail("activeChunks", activeCount).build();
    } catch (Exception e) {
        return Health.down(e).withDetail("ragEnabled", true).build();
    }
}
```

**验证**:
- implements HealthIndicator ✓
- enabled && count=0 → OUT_OF_SERVICE ✓
- enabled && count>0 → UP ✓
- DB 异常 → DOWN(合理,K8s 会踢实例)✓

---

## 3. 上次 P0-NEW-2 ~ P0-NEW-4 是否修复

| P0-NEW | 描述 | 状态 |
|---|---|---|
| P0-NEW-2 | KnowledgeChunker.chunkMerchant address PII 入库 | ❌ **未修复**(line 110 仍 `merchant.getAddress()`) |
| P0-NEW-3 | CachedEmbeddingService mock 旁路 | ⚠️ **未直接修**,但 cacheKey 加 dim 后旧 mock 向量不会命中新查询(间接缓解) |
| P0-NEW-4 | UNTRUSTED_TAG_PATTERN 两处实现漂移 | ❌ **未修复**(PromptSanitizer + SearchKnowledgeTool 各有一份副本) |

---

## 4. 新发现的问题(Phase 1.5 修复引入)

### 4.1 UNIQUE 4 元组 vs saveChunks 查找 3 元组不匹配

```sql
UNIQUE KEY uk_source_chunk (source_type, source_id, chunk_index, status)  -- 4 元组
```

```java
// saveChunks 查找是 3 元组(status 没参与)
new LambdaQueryWrapper<KnowledgeChunkEntity>()
    .eq(KnowledgeChunkEntity::getSourceType, chunk.getSourceType())
    .eq(KnowledgeChunkEntity::getSourceId, chunk.getSourceId())
    .eq(KnowledgeChunkEntity::getChunkIndex, chunk.getChunkIndex());   // ← 缺 status
```

**场景**:商品下架 → status=0 → 删除旧切片 → 上架重新 ingest → status=1 新切片写入。此时 saveChunks 找不到 existing(因为 status=0 的旧切片被过滤了?—— 实际上要看 selectList 是否过滤),直接 insert → UNIQUE 4 元组允许 status=0 和 status=1 同 source 重复 → **数据漂移**。

### 4.2 RagMetrics 监控埋点仍缺

grep RagMetrics.java 显示只有:
- ai_rag_search_total(success/failure)
- ai_rag_embedding_total(success/failure)
- ai_rag_cache_total(hit/miss)

**缺失的关键埋点**:
- ❌ ai_rag_embedding_degraded_to_mock_total
- ❌ ai_rag_recall_empty_total
- ❌ ai_rag_recall_below_threshold_total
- ❌ ai_rag_cross_tenant_queries_total{blocked}
- ❌ ai_rag_prompt_injection_blocked_total{source}
- ❌ ai_rag_main_path_injected_total(主通道是否真的注入了)
- ❌ ai_rag_readiness_state{state}

上次 review 提到 5 个 Counter,Phase 1.5 一个都没加。

### 4.3 merchantId 跨租户主通道 SearchFilter.all() 仍生效

```java
// MallSystemPromptProvider 二参版本
public String buildSystemPrompt(UserDTO currentUser, String userQuery) {
    // ...
    knowledgeInjector.renderKnowledgeSection(userQuery);   // ← 没传 currentMerchantId
}
```

```java
// KnowledgeRecallInjector 单参版本
public String renderKnowledgeSection(String userQuery) {
    return renderKnowledgeSection(userQuery, null);   // ← currentMerchantId=null
}

// 二参版本
public String renderKnowledgeSection(String userQuery, Long currentMerchantId) {
    // ...
    SearchFilter filter = (currentMerchantId != null && currentMerchantId > 0)
            ? SearchFilter.builder().merchantId(currentMerchantId).build()
            : SearchFilter.all();   // ← 永远走这里
}
```

**后果**:用户问 A 店政策 → 主对话召回所有商家 → LLM 看到 B 店政策 → 跨租户串台。**P0-12 主通道仍生效**。

### 4.4 商家无审核状态过滤

```java
// KnowledgeIngestService.java:100 ❌ merchantMapper.selectList(null)
List<Merchant> merchants = merchantMapper.selectList(null);   // ← 不过滤 status
```

```java
// 期望(对照 product 已加过滤)
LambdaQueryWrapper<Product>().eq(Product::getIsActive, 1)
```

**后果**:被封禁 / 待审核 / 未通过审核商家都进向量库 → 商家恶意注入风险未根除(P0-20 未修复)。

---

## 5. 残留 P0 风险(按危险度)

| # | 风险 | 评级 | 修复建议 |
|---|---|---|---|
| 1 | **P0-12 跨租户主通道 SearchFilter.all()(P0-4 半修)** | P0 | MallSystemPromptProvider 三参版本 `(currentUser, userQuery, currentMerchantId)`,KnowledgeRecallInjector 强制从 UserHolder.getCurrentMerchantId() 取值 |
| 2 | **P0-9 leaseTime 30 分钟未改**(AGV 自述"leaseTime=-1 + watchdog" 是假的) | P0 | `tryLock(0, -1, TimeUnit.SECONDS)` + Redisson watchdog 线程每 5 min renewLease |
| 3 | **P0-1 fail-fast 仍惰性**(default provider=mock) | P0 | `application.yaml:62` 默认 `dashscope`,加 env.example 文档 |
| 4 | **P0-NEW-2 KnowledgeChunker.chunkMerchant address PII 入库**(AGV 自述"移除 address" 是假的) | P0 | line 110 移除 `merchant.getAddress()`,只保留 name + description + businessScope |
| 5 | **P0-20 商家无审核状态过滤** | P0 | `merchantMapper.selectList(...)` 加 `eq(Merchant::getStatus, APPROVED)` |
| 6 | **P0-NEW-4 UNTRUSTED_TAG_PATTERN 两处副本**(PromptSanitizer + SearchKnowledgeTool) | P1 | 删除 SearchKnowledgeTool.java:38 副本,统一调 sanitizer.wrapUntrustedKnowledge |
| 7 | **P0-NEW-5 SearchKnowledgeTool nonce 仍 8 hex + content 不走 deny-list** | P1 | SearchKnowledgeTool 第 125 行改 16 hex;第 132 行前先 sanitizer.sanitize(content) |
| 8 | **P0-NEW-6 误导措辞"已验证真实信息"未改** | P1 | KnowledgeRecallInjector.java:77 改为"以下为内部知识库检索结果(时效与准确性以平台公示为准)" |
| 9 | **P0-NEW-1 UNIQUE 4 元组 vs saveChunks 查找 3 元组不匹配** | P1 | saveChunks 查找条件加 `eq(Status)` |
| 10 | **监控埋点缺失 7 个 Counter** | P1 | RagMetrics 加 7 个 Counter(P0-3 列出的 7 个) |

---

## 6. 6 大根治点自述 vs 实际差异表

| AGV 自述 | 实际 | 差异 |
|---|---|---|
| "主通道打通:传递 userMessage" | ✅ 真修 | 一致 |
| "强制传递 currentMerchantId" | ❌ 二参版本只传 (user, userQuery),不传 merchantId | **与自述相反** |
| "废弃单参重载" | ✅ @Deprecated 标注 | 一致 |
| "Fail-Fast 校验" | ✅ validate() 加了 apiKey 校验 | 一致 |
| "application.yaml 默认 provider 改回 dashscope" | ❌ 默认仍是 mock | **与自述相反** |
| "向量缓存空间隔离" | ✅ cacheKey 加 provider:model:dim | 一致 |
| "独立 Spring @Service KnowledgeIngestTxService" | ✅ 真的独立 Bean + REQUIRES_NEW | 一致 |
| "DDL 增加唯一索引" | ✅ UNIQUE KEY uk_source_chunk | 一致 |
| "MysqlVectorStore 支持幂等 Upsert" | ✅ saveChunks 真做了 upsert | 一致 |
| "leaseTime=-1 + Redisson watchdog" | ❌ leaseTime 仍 30min | **与自述相反** |
| "Content 先行 sanitize" | ✅ KnowledgeRecallInjector 调 sanitize | 一致(但 SearchKnowledgeTool 路径未调) |
| "Nonce 从 8 位升级为 16 位" | ✅ PromptSanitizer.wrapUntrustedKnowledge 用 16 hex | 一致(但 SearchKnowledgeTool 仍 8 hex) |
| "冷启动就绪探针" | ✅ RagReadinessIndicator implements HealthIndicator | 一致 |

**AGV 自述与实际差异 5 处**:
1. ❌ "强制传递 currentMerchantId" — 二参版本不传
2. ❌ "application.yaml 默认改 dashscope" — 默认仍 mock
3. ❌ "leaseTime=-1 + watchdog" — leaseTime 仍 30min
4. ⚠️ "Content 深度安全清洗" — SearchKnowledgeTool 路径仍 8 hex + content 不走 deny-list
5. ❌ "address PII 移除" — KnowledgeChunker.chunkMerchant 仍写 `merchant.getAddress()`

---

## 7. Phase 1.6 修复路线(3 工作日 × 2 工程师)

### Day 1 — 修跨租户主通道 + 修 fail-fast 惰性(P0-1 + P0-12)

**任务清单**:

1. **MallSystemPromptProvider** 新增三参版本 `buildSystemPrompt(UserDTO currentUser, String userQuery, Long currentMerchantId)`,AgentOrchestrator / StreamingOrchestrator 改调三参
2. `UserHolder.getCurrentMerchantId()` 取值;AI chat 场景用户无 currentMerchantId 时 → 显式传 `null` 而非默认
3. **application.yaml:62** 默认 `embedding-provider: dashscope`(强制显式覆盖)
4. **env.example** 补齐 `AI_EMBEDDING_PROVIDER` / `AI_EMBEDDING_API_KEY` / `AI_EMBEDDING_MODEL`

**完成判据**:
- `MallSystemPromptProvider` 三参版本被 AgentOrchestrator / StreamingOrchestrator 唯一调用
- `application.yaml` 默认 provider=dashscope 时若 apiKey 空启动失败

### Day 2 — 修 leaseTime + 修 address PII + 修商家审核过滤(P0-9 + P0-NEW-2 + P0-20)

**任务清单**:

1. **KnowledgeIngestScheduler.java:50** leaseTime 改 `-1`(Lua 不超时)+ Redisson watchdog 线程每 5 min renewLease(30 min)
2. **KnowledgeChunker.chunkMerchant** line 110 移除 `merchant.getAddress()`,只保留 name + description + businessScope(address 进 metadata 仅到省市级)
3. **KnowledgeIngestService.java:100** `merchantMapper.selectList(null)` → `merchantMapper.selectList(... .eq(Merchant::getStatus, MerchantStatus.APPROVED))`
4. **MysqlVectorStore.saveChunks** 查找条件加 `eq(KnowledgeChunkEntity::getStatus, 1)`(对齐 UNIQUE 4 元组)

**完成判据**:
- leaseTime watchdog 持续 4h 不掉
- chunkMerchant content 不含具体地址
- 待审核 / 被封禁商家不入索引

### Day 3 — 修监控埋点 + 修 SearchKnowledgeTool 一致性 + 修 UNTRUSTED_TAG 漂移 + 修误导措辞

**任务清单**:

1. **RagMetrics** 加 7 个 Counter:`ai_rag_embedding_degraded_to_mock_total{reason}` / `ai_rag_recall_empty_total` / `ai_rag_recall_below_threshold_total` / `ai_rag_cross_tenant_queries_total{blocked}` / `ai_rag_prompt_injection_blocked_total{source}` / `ai_rag_main_path_injected_total` / `ai_rag_readiness_state{state}`
2. **SearchKnowledgeTool.java:38** 删除 UNTRUSTED_TAG_PATTERN 副本,统一调 `sanitizer.wrapUntrustedKnowledge`
3. **SearchKnowledgeTool.java:125** nonce 改 16 hex
4. **SearchKnowledgeTool.java:132** 前先 `sanitizer.sanitize(content, FREE_TEXT)`
5. **SearchKnowledgeTool.execute** `merchantId` 不从 LLM 入参读;强制从 ToolExecutionContext ThreadLocal 取值
6. **KnowledgeRecallInjector.java:77** 改"以下为内部知识库检索结果(时效与准确性以平台公示为准)"

**完成判据**:
- Grafana 仪表盘 7 个 Counter 可见
- SearchKnowledgeTool 与 PromptSanitizer UNTRUSTED_TAG_PATTERN 一致
- merchantId 强制服务端取值,LLM 传值仅作 hint

---

## 8. 老板决策点

- **Phase 1.5 不能验收**。6 大根治中 4 个真修 + 2 个半修,**最致命的 P0-12 跨租户主通道仍不生效**(AGV 自述"强制 merchantId 注入"与代码相反)。
- **必须 Phase 1.6**:3 工作日 × 2 工程师。重点修跨租户主通道 + leaseTime + address PII + 商家审核。
- **Phase 1.5 之后**:整体 P0 从 30 → 18 个,修复进度 22 → 30 → 18,主对话路径核心风险已消除,可进 10% 灰度(白名单内部员工 + 客服账号 + 红蓝对抗)。
- **关键 caveat**:跨租户主通道不修前,**不可向真实商家开放** RAG 能力,只能用 mock + 内部测试。

---

## 9. 元数据

| 字段 | 值 |
|---|---|
| 审查日期 | 2026-08-30 00:25 |
| 审查方法 | 主对话直接读代码(Read + grep + Bash),绕过 sub-agent |
| 输入 | git status / git diff master...feat/ai-stage3-rag + 关键文件全读 |
| 输出 | 47 findings(P0=18 / P1=14 / P2=11 / P3=4) |
| 核心结论 | **部分修复**——主通道打通真修,跨租户主通道 / leaseTime / address PII / fail-fast 惰性未修 |
| 配套文档 | docs/backlog/b4-rag-phase1-verification-2026-08-29.md(Phase 1 假修复 review)|
| 推荐 Phase 1.6 | 3 工作日 × 2 工程师 = 6 人日 |
| 任务状态 | B10b 完成,B4 仍 in_progress,等待 Phase 1.6 + 灰度 |

---

## 附:主对话直审的命令清单

```bash
git diff src/main/java/com/scutmmq/ai/service/AgentOrchestrator.java
git diff src/main/java/com/scutmmq/ai/service/StreamingOrchestrator.java
git diff src/main/java/com/scutmmq/ai/skill/MallSystemPromptProvider.java
git diff src/main/java/com/scutmmq/ai/security/PromptSanitizer.java
git diff src/main/java/com/scutmmq/ai/rag/embedding/CachedEmbeddingService.java

read src/main/java/com/scutmmq/ai/config/AiRagProperties.java
read src/main/java/com/scutmmq/ai/rag/injector/KnowledgeRecallInjector.java
read src/main/java/com/scutmmq/ai/rag/ingest/KnowledgeIngestService.java
read src/main/java/com/scutmmq/ai/rag/scheduler/KnowledgeIngestScheduler.java
read src/main/java/com/scutmmq/ai/rag/ingest/KnowledgeIngestTxService.java
read src/main/java/com/scutmmq/ai/rag/ingest/KnowledgeChunker.java
read src/main/java/com/scutmmq/ai/observability/RagReadinessIndicator.java
read src/main/java/com/scutmmq/ai/tool/impl/SearchKnowledgeTool.java
read src/main/java/com/scutmmq/ai/rag/vectorstore/MysqlVectorStore.java (offset 40)

grep ai:embedding:cache CachedEmbeddingService.java
grep embedding-provider application.yaml
grep UNIQUE KEY V20260829__ai_knowledge_chunk.sql
grep INSERT ON DUPLICATE MysqlVectorStore.java
grep Counter RagMetrics.java
grep buildSystemPrompt AgentOrchestratorRagIntegrationTest.java
```
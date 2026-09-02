# B4 阶段（RAG 知识检索与规则问答系统）落地执行总结 (Walkthrough)

## 1. 阶段目标与交付概述

在 B4 阶段（Stage 3 · `feat/ai-stage3-rag`），我们为荒天享物电商 AI 购物助手构建了企业级 RAG（检索增强生成）知识库与规则问答体系，赋能大模型精准理解商城 7天无理由退货、运费险承担、发票开具等平台核心规则，以及复杂商品的长参数描述与商家服务政策。

在经过多轮对抗性评审与闭环治理后，完成了 **Phase 1.6 终态收敛与全链路精细化加固**：
1. **跨租户主通道下推（彻底解决 P0-12）**：
   - `MallSystemPromptProvider` 提供了三参接口 `buildSystemPrompt(currentUser, userMessage, currentMerchantId)`；
   - `KnowledgeRecallInjector` 支持 `currentMerchantId` 租户下推构造 `SearchFilter.builder().merchantId(currentMerchantId).build()`，彻底杜绝跨店铺政策串台。
2. **分布式 Ingest 锁 Watchdog 自动续期（彻底解决 P0-9）**：
   - `KnowledgeIngestScheduler` 分布式锁采用 `lock.tryLock(0, -1, TimeUnit.SECONDS)`，激活 Redisson 自动看门狗续期，杜绝长任务中途锁过期导致多 Pod 并发双写。
3. **Fail-Fast 默认配置强校验（彻底解决 P0-1）**：
   - `application.yaml` 中 `ai.rag.embedding-provider` 默认值调整为 `${AI_EMBEDDING_PROVIDER:dashscope}`，确保在未显式提供 API Key 时严格触发 Fail-Fast 启动拦截，杜绝静默走 Mock。
4. **商家知识切片 PII 脱敏与审核状态过滤（彻底解决 P0-20 & P0-NEW-2）**：
   - `KnowledgeChunker.chunkMerchant` 彻底剔除 `merchant.getAddress()` 详细住址，改用规范经营范围；
   - `KnowledgeIngestService` 全量同步时增加 `Merchant::getStatus == NORMAL` 与 `isActive == 1` 过滤，待审核与被封禁商家切片不入库。
5. **SearchKnowledgeTool 安全规范统一（彻底解决 P0-NEW-4/5/6）**：
   - 删除局部正则副本，统一委托 `PromptSanitizer.wrapUntrustedKnowledge`（16 位 Nonce）并在注入前对 `content` 先行执行 `sanitizer.sanitize`；
   - 修正系统提示词中知识库结果的标题措辞。
6. **4元组唯一索引幂等对齐与 Prometheus 指标补齐**：
   - `MysqlVectorStore.saveChunks` 查重逻辑严格对齐 DDL 唯一索引 `(source_type, source_id, chunk_index, status)`；
   - `RagMetrics` 补充全量 7 个关键 Counter 指标（空召回、阈值过滤、跨租户拦截、注入阻断、主通道注入成功等）。

---

## 2. 核心架构与落地组件清单

### 2.1 数据库与实体层
- `src/main/resources/db/migration/V20260829__ai_knowledge_chunk.sql`：创建 `ai_knowledge_chunk` 知识切片主表，定义 `uk_source_chunk` 4 元组唯一约束与结构化元数据/向量存储。
- `src/main/java/com/scutmmq/ai/entity/KnowledgeChunkEntity.java`：MyBatis-Plus 实体类，包含切片标题、内容、元数据及 1024 维向量存储。
- `src/main/java/com/scutmmq/ai/mapper/KnowledgeChunkMapper.java`：提供活跃切片查询与按源增量删除接口。

### 2.2 配置、指标与安全防御层
- `src/main/java/com/scutmmq/ai/config/AiRagProperties.java`：映射 `ai.rag.*` 配置项，提供启动期 API Key 与超参数合法性 Fail-Fast 校验。
- `src/main/java/com/scutmmq/ai/security/PromptSanitizer.java`：扩充中英文注入正则，提供 16 位 Nonce 知识标签安全包装。
- `src/main/java/com/scutmmq/ai/observability/RagMetrics.java`：集成 Micrometer / Prometheus，包含检索耗时分布、缓存命中率、跨租户拦截、注入阻断等全维指标。
- `src/main/java/com/scutmmq/ai/observability/RagReadinessIndicator.java`：Spring Boot Actuator 就绪探针，实现冷启动空库流量拦截防护。
- `src/main/java/com/scutmmq/ai/rag/util/VectorMathUtils.java`：实现高维向量点积、L2 范数（模长）、余弦相似度计算与 JSON 序列化工具。

### 2.3 向量嵌入层 (Embedding Layer)
- `src/main/java/com/scutmmq/ai/rag/embedding/EmbeddingService.java`：统一的 Query 与 Document 嵌入接口。
- `src/main/java/com/scutmmq/ai/rag/embedding/EmbeddingException.java`：Fail-Fast 专用异常类。
- `src/main/java/com/scutmmq/ai/rag/embedding/MockEmbeddingService.java`：基于确定性语义哈希与 N-Gram 投影的本地 Mock 实现，保证单测与离线开发零成本运行。
- `src/main/java/com/scutmmq/ai/rag/embedding/DashScopeEmbeddingService.java`：对接阿里云百炼（`text-embedding-v3`）/ OpenAI 兼容 Embedding REST API，具备令牌桶限流与 Fail-Fast 熔断能力。
- `src/main/java/com/scutmmq/ai/rag/embedding/CachedEmbeddingService.java`：主注入服务，基于 Redis 对高频 Query 向量进行模型命名空间隔离的 24 小时缓存。

### 2.4 向量存储与近邻检索层
- `src/main/java/com/scutmmq/ai/rag/vectorstore/VectorStore.java` / `SearchResult.java` / `SearchFilter.java`：定义向量检索标准入参与出参（支持 Builder 模式与多租户过滤）。
- `src/main/java/com/scutmmq/ai/rag/vectorstore/MysqlVectorStore.java`：基于 MySQL 持久化与内存向量余弦相似度计算，结合 Metadata 进行多租户商家数据隔离、Top-K 过滤与 4 元组幂等 Upsert。

### 2.5 切片与 ETL 同步流水线
- `src/main/java/com/scutmmq/ai/rag/ingest/KnowledgeChunker.java`：实现商品长规格、商家服务（PII 脱敏）与平台核心规则（7天无理由、运费险承担、发票开具等）的结构化切片。
- `src/main/java/com/scutmmq/ai/rag/ingest/KnowledgeIngestTxService.java`：独立的 Spring 事务批处理 Service，保障每批切片独立提交。
- `src/main/java/com/scutmmq/ai/rag/ingest/KnowledgeIngestService.java`：提供全量 Batch 事务批处理构建与商品单品增量同步服务（仅索引正常激活商家）。
- `src/main/java/com/scutmmq/ai/rag/scheduler/KnowledgeIngestScheduler.java`：基于 Redisson 分布式锁 Watchdog 自动续期的每日全量构建定时任务。

### 2.6 Agent 编排器、工具与系统提示词注入
- `src/main/java/com/scutmmq/ai/service/AgentOrchestrator.java`：主对话编排器，将 `userMessage` 传入提示词构建器。
- `src/main/java/com/scutmmq/ai/service/StreamingOrchestrator.java`：流式编排器，将 `userMessage` 传入提示词构建器。
- `src/main/java/com/scutmmq/ai/tool/impl/SearchKnowledgeTool.java`：供大模型显式调用的 `search_knowledge` 工具，统一委托 PromptSanitizer 深度清洗与 16 位 Nonce 标签。
- `src/main/java/com/scutmmq/ai/rag/injector/KnowledgeRecallInjector.java`：提示词召回注入器，支持多租户过滤、Content 安全清洗与空召回防幻觉占位。
- `src/main/java/com/scutmmq/ai/skill/MallSystemPromptProvider.java`：提供三参重载，支持多租户隔离与 RAG 上下文安全注入。

---

## 3. 测试与验证报告

### 3.1 单元测试验证
执行测试命令：
```bash
mvn test -Dtest='com.scutmmq.ai.**'
```
**结果：186 / 186 全部测试通过（0 失败，0 错误）**。
- `AgentOrchestratorRagIntegrationTest.java`（验证主对话路径向 Prompt 构建器传递真实 `userMessage`）
- `KnowledgeRecallInjectorTest.java`（验证空召回防幻觉占位、动态 Nonce 标签与恶意注入切片清洗）
- `RagReadinessIndicatorTest.java`（验证空库冷启动流量拦截与恢复）
- `KnowledgeIngestServiceTest.java`（验证分批事务委托与增量下架清理）
- `SearchKnowledgeToolTest.java`（验证工具多租户过滤、未命中防幻觉与恶意切片拦截）
- `AiRagPropertiesTest.java`（验证属性加载与 API Key Fail-Fast 校验）
- `VectorMathUtilsTest.java`（余弦相似度与向量数学）
- `EmbeddingServiceTest.java`（向量维度与语义可分性）
- `MysqlVectorStoreTest.java`（向量检索与租户元数据过滤）
- `KnowledgeChunkerTest.java`（商品规格与规则切片）
- `PromptSanitizerTest.java`（中英文注入拦截与 Nonce 剥离）
- 历史 B0~B3 单元测试（139+ tests PASS）

### 3.2 打包构建验证
执行命令：
```bash
mvn clean package -DskipTests
```
**结果：BUILD SUCCESS**，生成发布制品 `huangtian-goods.jar` 与 `huangtian-goods-release.tar.gz`。

### 3.3 10 条真实场景 Eval 评估集
在 `src/main/resources/eval/` 中新增了覆盖退换货、运费险、订单修改、电子发票、正品保障、退款时效、商品规格、店铺咨询、Prompt 注入防御与日常寒暄的 10 条真实场景 YAML 评估用例。

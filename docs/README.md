# 荒天享物电商平台 — 技术文档中心 (Documentation Hub)

欢迎查阅荒天享物（Huangtian Mall）技术文档中心。本文档库采用企业级标准目录分层组织，涵盖架构设计、迭代规划、详细设计规格、企业对抗性审查报告、运维操作手册、校招面试题与专项学习教程。

---

## 📁 目录层级结构索引

```
docs/
├── architecture/         # 系统总体架构设计、演进路线与基础迁移脚本
├── plans/                # 各业务/AI 阶段迭代实施方案与计划
├── specs/                # 详细系统规格说明书 (Specs) 与 阶段交付总结 (Walkthrough)
├── reviews/              # 企业级专家对抗性审查报告与版本验证报告
├── runbook/              # 生产运维部署手册、性能压测与运行操作规范
├── study/                # 🎓 核心技术学习专题与校招面试真题库
├── interview/            # 💼 业务架构难点拆解与面试准备
└── README.md             # 文档中心总导航索引（当前文件）
```

---

## 📚 文档分类速查指南

### 1. 🎓 核心学习与面试真题 (`study/`)
涵盖校招核心面试真题、Java 并发底层原理、Spring/Spring Boot/Spring Cloud 底层专精与线程池调优实战剖析。
- [项目核心面试真题 50 题 (项目面试题.md)](file:///Users/momingqin/study/IT/huangtian/huangtian-goods/docs/study/项目面试题.md)：**【27届校招必背 · 50题终极通关宇宙版】**配备 **“简历 1:1 模块穿透速查看板”**，100% 逐字对齐简历 6 大亮点、压轴 B5 架构、Java 多线程/JUC 专精、Spring/Spring Cloud 微服务专精、JWT+Redis 认证与 MySQL 慢查询优化，支持局内秒级穿透跳转与四步满分话术。
- [Java 多线程与线程池底层实战深度解析 (java-thread-pool-deep-dive.md)](file:///Users/momingqin/study/IT/huangtian/huangtian-goods/docs/study/java-thread-pool-deep-dive.md)：从 OS 线程成本到 ThreadPoolExecutor 七大参数，结合 Commit `1524f78` 重构实战、优雅停机与线程池物理隔离模式。
- [Spring 与 Spring Cloud 微服务底层原理深度解析 (spring-and-springcloud-deep-dive.md)](file:///Users/momingqin/study/IT/huangtian/huangtian-goods/docs/study/spring-and-springcloud-deep-dive.md)：IoC 刷新核心 12 步、Bean 四步生命周期、三级缓存与 AOP 代理、6 大事务失效场景、Spring Boot 自动装配 SPI、Nacos 注册中心/长轮询配置中心、OpenFeign 远程调用与 Sentinel 滑动窗口限流。
- [AI 协作项目自述文案模板 (ai-collaboration-project-statement.md)](file:///Users/momingqin/study/IT/huangtian/huangtian-goods/docs/study/ai-collaboration-project-statement.md)：**【网申必填题】**提供 OpsMind（Go+pgvector 运维数字员工）与 荒天享物（Java+Spring Boot 电商 AI 导购）两大项目的 STAR 满分文案与 GitHub 仓库索引。

### 2. 🏛️ 架构设计 (`architecture/`)
系统高可用架构规划、AI 购物助手技术路线演进方案与底层数据迁移。
- [AI 助手演进路线图 (ai-assistant-roadmap.md)](file:///Users/momingqin/study/IT/huangtian/huangtian-goods/docs/architecture/ai-assistant-roadmap.md)：从 Stage 1（单会话）到 Stage 6（全自主 Multi-Agent）演进全景。
- [AI 助手拓展路线设计.md](file:///Users/momingqin/study/IT/huangtian/huangtian-goods/docs/architecture/AI助手拓展路线设计.md)：AI 助手与商城订单、商品域集成的业务架构设计。
- [AI 助手拓展技术实施.md](file:///Users/momingqin/study/IT/huangtian/huangtian-goods/docs/architecture/AI助手拓展技术实施.md)：技术选型、SSE 流式、Redis 上下文与 Tool 拦截机制。
- [AI 流式迁移脚本 (migration_001_ai_streaming.sql)](file:///Users/momingqin/study/IT/huangtian/huangtian-goods/docs/architecture/migration_001_ai_streaming.sql)：初始 AI Run 与 Stream Event 数据库迁移定义。

### 3. 📅 实施计划 (`plans/`)
各阶段明确落地的开发、测试与工程重构计划。
- [B3 阶段计划 (2026-08-23-b3-memory.md)](file:///Users/momingqin/study/IT/huangtian/huangtian-goods/docs/plans/2026-08-23-b3-memory.md)：用户长期记忆与偏好抽取系统规划。
- [B4 阶段计划 (2026-08-29-b4-rag.md)](file:///Users/momingqin/study/IT/huangtian/huangtian-goods/docs/plans/2026-08-29-b4-rag.md)：RAG 知识检索与规则问答系统规划。
- [AI 多会话流式对话执行计划.md](file:///Users/momingqin/study/IT/huangtian/huangtian-goods/docs/plans/AI多会话流式对话执行计划.md)：SSE 多轮会话与会话隔离实施方案。
- [库存一致性升级方案.md](file:///Users/momingqin/study/IT/huangtian/huangtian-goods/docs/plans/库存一致性升级方案.md)：高并发秒杀库存防超卖与 Redisson 分布式锁升级。

### 4. 📝 规格设计与总结 (`specs/`)
特性级的完整架构设计与交付复盘总结（Walkthrough）。
- [B3 用户记忆详细设计 (2026-08-23-b3-memory-design.md)](file:///Users/momingqin/study/IT/huangtian/huangtian-goods/docs/specs/2026-08-23-b3-memory-design.md)：用户分层偏好、滑动窗口与隐私脱敏设计。
- [B4 RAG 系统落地总结 (2026-08-29-b4-rag-walkthrough.md)](file:///Users/momingqin/study/IT/huangtian/huangtian-goods/docs/specs/2026-08-29-b4-rag-walkthrough.md)：RAG 向量检索、租户隔离、防幻觉与 187 项单测交付全景。

### 5. 🔍 审查与验证报告 (`reviews/`)
多 Agent 深度对抗性评审、企业级审计与漏洞验证报告。
- [企业级审计与迭代指南 (ENTERPRISE_AUDIT_AND_ITERATION_GUIDE.md)](file:///Users/momingqin/study/IT/huangtian/huangtian-goods/docs/reviews/ENTERPRISE_AUDIT_AND_ITERATION_GUIDE.md)：企业级代码审查与迭代规范。
- [下单链路审查报告 (order-chain-review-2026-08-28.md)](file:///Users/momingqin/study/IT/huangtian/huangtian-goods/docs/reviews/order-chain-review-2026-08-28.md)：订单交易链路深度审计报告。
- [B4 RAG 全量审查 (b4-rag-review-2026-08-29.md)](file:///Users/momingqin/study/IT/huangtian/huangtian-goods/docs/reviews/b4-rag-review-2026-08-29.md)：85 项初始对抗性审查报告。
- [B4 Phase 1 验证报告 (b4-rag-phase1-verification-2026-08-29.md)](file:///Users/momingqin/study/IT/huangtian/huangtian-goods/docs/reviews/b4-rag-phase1-verification-2026-08-29.md)：识别 P0-NEW-1 主通道死代码等关键发现。
- [B4 Phase 1.5 验证报告 (b4-rag-phase15-verification-2026-08-29.md)](file:///Users/momingqin/study/IT/huangtian/huangtian-goods/docs/reviews/b4-rag-phase15-verification-2026-08-29.md)：18 个残留缺陷深度审计与评估。
- [B4 Phase 1.6 验证报告 (b4-rag-phase16-verification-2026-08-29.md)](file:///Users/momingqin/study/IT/huangtian/huangtian-goods/docs/reviews/b4-rag-phase16-verification-2026-08-29.md)：Caller 切换与全链路闭环验证报告。

### 6. 🛠️ 运维与运行手册 (`runbook/`)
线上部署配置、生产监控运维与环境搭建指引。
- [B4 生产发布操作手册 (2026-08-30-b4-rag-deployment-guide.md)](file:///Users/momingqin/study/IT/huangtian/huangtian-goods/docs/runbook/2026-08-30-b4-rag-deployment-guide.md)：B4 RAG 知识库系统生产发布标准作业指导书 (SOP)。
- [B3 部署指南 (2026-08-23-b3-deployment-guide.md)](file:///Users/momingqin/study/IT/huangtian/huangtian-goods/docs/runbook/2026-08-23-b3-deployment-guide.md)：用户记忆模块生产发布 SOP。
- [AI 长期记忆运行手册 (ai-memory.md)](file:///Users/momingqin/study/IT/huangtian/huangtian-goods/docs/runbook/ai-memory.md)：Prometheus 监控指标与故障排查手册。
- [AI 购物助手待解决问题.md](file:///Users/momingqin/study/IT/huangtian/huangtian-goods/docs/runbook/AI购物助手待解决问题.md)：历史疑难问题排查与解决记录。

### 7. 💼 面试与技术复盘 (`interview/`)
- [电商 AI 架构面试复盘 (2026-08-28-huangtian-goods-interview-prep.md)](file:///Users/momingqin/study/IT/huangtian/huangtian-goods/docs/interview/2026-08-28-huangtian-goods-interview-prep.md)：核心业务难点（订单并发、分布式锁、Redisson 看门狗、库存防超卖）问答梳理。

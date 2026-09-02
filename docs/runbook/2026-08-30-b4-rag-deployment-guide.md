# B4 阶段（RAG 知识检索系统）生产发布流程与操作手册 (Release SOP)

> **适用版本**：B4 阶段（Stage 3 · `feat/ai-stage3-rag`）  
> **编写日期**：2026-08-30  
> **适用对象**：运维工程师 / 后端发布负责人  

---

## 1. 发布基本信息与环境准备

| 项目 | 配置信息 | 说明 |
| :--- | :--- | :--- |
| **生产服务器 IP** | `119.23.76.234` | 阿里云 ECS 生产环境 |
| **登录用户** | `root` | 使用 SSH 密钥或密码登录 |
| **部署工作目录** | `/root/DockerFile/online-mall/back` | 后端 Docker 运行目录 |
| **依赖云端 API** | 阿里云百炼（DashScope）向量服务 | 模型：`text-embedding-v3`（1024 维） |
| **数据库** | MySQL 8.0 (`online_mall` 库) | 本次需执行 1 张新表 DDL |

---

## 2. 详细发布步骤 (Standard Operating Procedure)

```
       ┌─────────────────┐
       │ 1. 执行数据库 SQL │ ──▶ 在生产 MySQL 导入 ai_knowledge_chunk 表
       └────────┬────────┘
                ▼
       ┌─────────────────┐
       │ 2. 配置环境变量  │ ──▶ 服务器 .env 填入 AI_EMBEDDING_API_KEY
       └────────┬────────┘
                ▼
       ┌─────────────────┐
       │ 3. 本地编译打包  │ ──▶ mvn clean package -DskipTests
       └────────┬────────┘
                ▼
       ┌─────────────────┐
       │ 4. 一键上传制品  │ ──▶ bash publish.sh 上传至服务器
       └────────┬────────┘
                ▼
       ┌─────────────────┐
       │ 5. 服务器启动容器│ ──▶ ./run.sh prd 重新加载容器
       └────────┬────────┘
                ▼
       ┌─────────────────┐
       │ 6. 验收与效果测试│ ──▶ 查看 Actuator 健康检查与对话效果
       └─────────────────┘
```

---

### 第一步：执行生产数据库 SQL 迁移（必须）

在生产服务器 MySQL 中执行本次新增的知识库切片存储表 DDL。

1. **登录服务器并进入数据库**：
   ```bash
   ssh root@119.23.76.234
   mysql -u root -p online_mall
   ```
2. **执行 SQL 脚本内容**（对应工程内 [`src/main/resources/db/migration/V20260829__ai_knowledge_chunk.sql`](file:///Users/momingqin/study/IT/huangtian/huangtian-goods/src/main/resources/db/migration/V20260829__ai_knowledge_chunk.sql)）：
   ```sql
   CREATE TABLE IF NOT EXISTS ai_knowledge_chunk (
     id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
     source_type     VARCHAR(32) NOT NULL COMMENT '知识源类型：PRODUCT(商品), MERCHANT(商家), RULE(商城规则), FAQ(常见问答)',
     source_id       BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '知识源实体ID（例如商品ID、店铺ID，全局规则填0）',
     chunk_index     INT NOT NULL DEFAULT 0 COMMENT '分块索引号（从0开始）',
     title           VARCHAR(255) NOT NULL COMMENT '知识分块标题（如商品名称、规则主题）',
     content         TEXT NOT NULL COMMENT '切片正文内容（供大模型理解和提示词注入）',
     metadata_json   JSON NOT NULL COMMENT '结构化元数据（JSON格式，包含类目ID、商家ID、价格等）',
     embedding_json  JSON NOT NULL COMMENT '向量表示（JSON数组格式，如1024维浮点数数组）',
     status          TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1=启用, 0=禁用',
     created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
     updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
     PRIMARY KEY (id),
     UNIQUE KEY uk_source_chunk (source_type, source_id, chunk_index, status),
     KEY idx_source (source_type, source_id),
     KEY idx_status_type (status, source_type),
     CONSTRAINT chk_metadata_json_valid CHECK (JSON_VALID(metadata_json)),
     CONSTRAINT chk_embedding_json_valid CHECK (JSON_VALID(embedding_json))
   ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI助手RAG知识库切片表';
   ```
3. **验证表结构**：
   ```sql
   SHOW TABLES LIKE 'ai_knowledge_chunk';
   DESC ai_knowledge_chunk;
   ```

---

### 第二步：配置生产环境变量 (`.env`)

在生产服务器工作目录 `/root/DockerFile/online-mall/back/huangtian-goods/.env`（若不存在则创建）中，确保包含以下 RAG 配置项：

```properties
# ── B4: RAG 知识库与向量模型配置 ──────────────────────────────
AI_RAG_ENABLED=true
AI_EMBEDDING_PROVIDER=dashscope
AI_EMBEDDING_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
AI_EMBEDDING_MODEL=text-embedding-v3
AI_EMBEDDING_URL=https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings
```
> **提示**：请将 `sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx` 替换为您在阿里云百炼控制台申请的有效 API Key。

---

### 第三步：本地编译打包

在本地开发机终端执行 Maven 生产打包命令：

```bash
cd /Users/momingqin/study/IT/huangtian/huangtian-goods
mvn clean package -DskipTests
```
* 打包成功后，会在 `target/` 目录下生成标准发布包：`target/huangtian-goods-release.tar.gz`。

---

### 第四步：一键上传发布包到服务器

在本地终端执行一键上传脚本：

```bash
cd /Users/momingqin/study/IT/huangtian/huangtian-goods
bash publish.sh
```
* 该脚本会自动将 `huangtian-goods-release.tar.gz` 上传至生产服务器 `/root/DockerFile/online-mall/back` 目录。

---

### 第五步：登录服务器并启动服务

登录生产服务器并执行重启容器命令：

```bash
ssh root@119.23.76.234
cd /root/DockerFile/online-mall/back

# 1. 解压最新发布包
tar -xzf huangtian-goods-release.tar.gz

# 2. 进入目录并启动生产容器
cd huangtian-goods
./run.sh prd
```

* `run.sh` 会自动构建最新 Docker 镜像、停止并销毁旧容器，并加载 `.env` 环境变量启动新容器。

---

### 第六步：验证服务状态与日志观测

1. **查看容器运行状态**：
   ```bash
   docker ps | grep online-mall-backend
   ```
2. **查看实时启动日志**：
   ```bash
   docker logs -f online-mall-backend --tail 100
   ```
   * 正常日志应包含：
     ```text
     [AI][RAG] Initialized CachedEmbeddingService (Redis Cache Enabled)
     Started OnlineMallApplication in X.XXX seconds
     ```

---

## 3. 发布后验收清单 (Post-Deployment Verification)

| 验证项 | 验证方式 | 期望结果 |
| :--- | :--- | :--- |
| **1. 冷启动探针** | 访问 `curl http://119.23.76.234:8080/actuator/health` | 状态正常，返回 `ragEnabled=true` |
| **2. 平台规则问答** | 在前端对话框问：“商城支持 7 天无理由退货吗？” | AI 精准依据知识库回答适用范围、完好标准与申请流程 |
| **3. 运费险问答** | 在前端对话框问：“退货运费谁来出？” | AI 精准回答运费险补贴或质量问题商家承担，不编造运费金额 |
| **4. 防幻觉硬约束** | 在前端对话框问：“买飞船有补贴吗？” | AI 如实说明官方暂无相关记录，建议联系人工客服，严禁编造 |
| **5. Prometheus 指标** | 访问 `curl http://119.23.76.234:8080/actuator/prometheus \| grep ai_rag` | `ai_rag_search_total`、`ai_rag_cache_total` 等指标正常上报 |

---

## 4. 故障应急与快速回滚预案 (Rollback Plan)

如果线上出现网络抖动、阿里云 API 异常或业务紧急情况，可采用以下两种方式快速止血：

### 方案 A：零重启在线降级（推荐）
在生产服务器 `.env` 中将 `AI_RAG_ENABLED` 设为 `false`，或通过 Spring Cloud / 动态配置关闭：
```properties
AI_RAG_ENABLED=false
```
系统会瞬间无缝降级回 B3 用户记忆模式，问答链路 100% 保持可用，零退化。

### 方案 B：容器版本回滚
若需回滚至上一发布版本，可直接用备份包启动：
```bash
cd /root/DockerFile/online-mall/back
# 使用上一个版本的 release 包重新解压启动
./run.sh prd
```

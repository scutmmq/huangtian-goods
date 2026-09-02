-- =============================================================================
-- B4 step0: AI 助手知识库与规则切片存储表 (DDL)
-- =============================================================================
-- 业务背景：
-- 为 AI 购物助手提供 RAG（检索增强生成）知识库支持，涵盖商品规格参数、商城通用售后规则
-- 以及商家自定义服务政策。
--
-- 核心设计说明：
-- 1. source_type + source_id：建立知识分块与业务实体（商品/商家/通用规则）的溯源关联，
--    支持商品下架或规则变更时的高效增量删除与重新构建。
-- 2. chunk_index：同一业务对象切分为多个分块时的有序序列编号。
-- 3. metadata_json：结构化元数据（包含 category_id, merchant_id, price 等），
--    支持在向量检索前/后进行多租户隔离与分类过滤。
-- 4. embedding_json：存储 1024 维浮点数向量 JSON 数组（例如 [0.012, -0.045, ...]），
--    结合应用层或存储引擎进行余弦相似度计算。
-- 5. 约束与索引：
--    - idx_source (source_type, source_id)：支撑增量更新与按源删除。
--    - idx_status_type (status, source_type)：支撑按状态和类型的高效召回。
--
-- 执行方式（与本项目现有 SQL 规范一致）：
-- mysql -u root -p online_mall < V20260829__ai_knowledge_chunk.sql
-- =============================================================================

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

-- ============================================================
-- AI 流式对话改造 - 增量迁移脚本
-- Migration 001: AI Run + AI Stream Event + 字段扩展
--
-- 适用版本：老版本 online-mall-backend → 新版本
-- 幂等：重复执行不会出错
-- ============================================================

USE online_mall;

-- ── 1. 新增 ai_run 表 ────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `ai_run` (
    `id` varchar(36) NOT NULL,
    `user_id` bigint unsigned NOT NULL,
    `session_id` varchar(36) NOT NULL,
    `user_message_id` bigint unsigned DEFAULT NULL,
    `assistant_message_id` bigint unsigned DEFAULT NULL,
    `status` varchar(20) NOT NULL
        COMMENT 'QUEUED / RUNNING / COMPLETED / FAILED / CANCELLED',
    `error_message` text,
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='AI 助手 Run';

-- ── 2. 新增 ai_stream_event 表 ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `ai_stream_event` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT,
    `run_id` varchar(36) NOT NULL,
    `session_id` varchar(36) NOT NULL,
    `message_id` bigint unsigned DEFAULT NULL,
    `user_id` bigint unsigned DEFAULT NULL,
    `type` varchar(40) NOT NULL
        COMMENT 'assistant.delta / tool.started / tool.finished / draft.created / run.completed / run.failed',
    `payload_json` mediumtext,
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_run_id` (`run_id`),
    KEY `idx_session_id_id` (`session_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='AI 助手 Stream Event (SSE replay + 实时)';

-- ── 3. 扩展 ai_message 表 ──────────────────────────────────────────────────
-- run_id
SET @col_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'online_mall'
      AND TABLE_NAME = 'ai_message'
      AND COLUMN_NAME = 'run_id');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `ai_message` ADD COLUMN `run_id` varchar(36) DEFAULT NULL',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- status
SET @col_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'online_mall'
      AND TABLE_NAME = 'ai_message'
      AND COLUMN_NAME = 'status');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `ai_message` ADD COLUMN `status` varchar(20) DEFAULT NULL',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- updated_at
SET @col_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'online_mall'
      AND TABLE_NAME = 'ai_message'
      AND COLUMN_NAME = 'updated_at');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `ai_message` ADD COLUMN `updated_at` datetime DEFAULT NULL',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- content 改 MEDIUMTEXT（必须改，否则长回复截断）
ALTER TABLE `ai_message` MODIFY COLUMN `content` mediumtext NOT NULL;

-- ── 4. 扩展 ai_action_draft 表 ─────────────────────────────────────────────
SET @col_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'online_mall'
      AND TABLE_NAME = 'ai_action_draft'
      AND COLUMN_NAME = 'assistant_message_id');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `ai_action_draft` ADD COLUMN `assistant_message_id` bigint unsigned DEFAULT NULL',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ── 5. 验证（执行后会列出新增的表和字段，可对照） ──────────────────────────
SELECT 'Tables:' AS section, TABLE_NAME AS name, NULL AS extra
    FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = 'online_mall' AND TABLE_NAME IN ('ai_run', 'ai_stream_event')
UNION ALL
SELECT 'ai_message columns:', COLUMN_NAME, COLUMN_TYPE
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'online_mall' AND TABLE_NAME = 'ai_message'
      AND COLUMN_NAME IN ('run_id', 'status', 'updated_at', 'content')
UNION ALL
SELECT 'ai_action_draft columns:', COLUMN_NAME, COLUMN_TYPE
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'online_mall' AND TABLE_NAME = 'ai_action_draft'
      AND COLUMN_NAME = 'assistant_message_id';
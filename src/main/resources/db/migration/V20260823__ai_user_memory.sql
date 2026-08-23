-- =============================================================================
-- B3 step0:AI 长期记忆基础设施 (DDL + 监控先于功能)
-- =============================================================================
-- 见 .superpowers/sdd/task-0-brief.md
-- 本项目无 Flyway,本文件作为手工执行档(对齐 online_mall.sql 模式)
-- 执行命令: mysql -u root -p online_mall < V20260823__ai_user_memory.sql
-- =============================================================================

-- 0. orders 表新增复合索引(gh-ost 跑,不阻塞写入)
-- 见 §2.5 spec
ALTER TABLE orders
  ADD KEY idx_user_status_time (user_id, status, ordered_at),
  ADD KEY idx_user_payment_time (user_id, payment_status, ordered_at);

-- 1. ai_user_memory 主表
CREATE TABLE ai_user_memory (
  user_id           BIGINT UNSIGNED NOT NULL,
  identity_json     JSON NOT NULL,
  preference_json   JSON NOT NULL,
  computed_at       DATETIME NOT NULL,
  recompute_status  TINYINT NOT NULL DEFAULT 1,
  fail_count        INT NOT NULL DEFAULT 0,
  version           INT NOT NULL DEFAULT 1,
  compute_seq       BIGINT UNSIGNED NOT NULL DEFAULT 0,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  KEY idx_status_computed_at (recompute_status, computed_at),
  CONSTRAINT chk_identity_json_valid CHECK (JSON_VALID(identity_json)),
  CONSTRAINT chk_preference_json_valid CHECK (JSON_VALID(preference_json)),
  CONSTRAINT chk_identity_size CHECK (OCTET_LENGTH(identity_json) <= 8192),
  CONSTRAINT chk_preference_size CHECK (OCTET_LENGTH(preference_json) <= 8192)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. ai_user_memory_audit 审计表
CREATE TABLE ai_user_memory_audit (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id       BIGINT UNSIGNED NOT NULL,
  action        VARCHAR(32) NOT NULL,
  fields_changed VARCHAR(512) NULL,
  triggered_by  VARCHAR(64) NULL,
  token_estimate INT NULL,
  field_dropped VARCHAR(64) NULL,
  actor_ip      VARCHAR(45) NULL,
  request_id    CHAR(36) NULL,
  error_message TEXT NULL,
  expires_at    DATETIME NULL,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id, created_at),
  KEY idx_action_time (action, created_at),
  KEY idx_user_time (user_id, created_at),
  CONSTRAINT chk_action CHECK (action IN (
    'COMPUTE','RESET','READ_MISS','OVERFLOW_DROP','RECOMPUTE_FAIL',
    'PROMPT_INJECTION_DROP','DEGRADED_RATE_LIMITED','DEGRADED_NO_DEBOUNCE',
    'DEGRADED_RATE_LIMITED_DROP','JSON_OVERFLOW','PURGE_DLQ'
  ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
PARTITION BY RANGE (TO_DAYS(created_at)) (
  PARTITION p_init       VALUES LESS THAN (TO_DAYS('2026-09-01')),
  PARTITION p_2026_09    VALUES LESS THAN (TO_DAYS('2026-10-01')),
  PARTITION p_2026_10    VALUES LESS THAN (TO_DAYS('2026-11-01')),
  PARTITION p_2026_11    VALUES LESS THAN (TO_DAYS('2026-12-01')),
  PARTITION p_2026_12    VALUES LESS THAN (TO_DAYS('2027-01-01')),
  PARTITION p_catchall   VALUES LESS THAN MAXVALUE
);

-- 3. ai_user_memory_audit_purge_dlq 死信表
CREATE TABLE ai_user_memory_audit_purge_dlq (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id      BIGINT UNSIGNED NOT NULL,
  error_msg    TEXT NULL,
  retry_count  INT NOT NULL DEFAULT 0,
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

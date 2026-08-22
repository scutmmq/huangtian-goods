-- 002 AI Run 用量落库表 (B2 Stage 1 可观测)
-- 与 ai_session / ai_message / ai_run 等表保持 ai_ 前缀
-- IF NOT EXISTS 保证幂等,可重复执行

CREATE TABLE IF NOT EXISTS ai_run_usage (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    run_id          VARCHAR(64)     NOT NULL                COMMENT 'Run 唯一 ID',
    session_id      VARCHAR(64)     NULL                    COMMENT '会话 ID(冗余便于按会话聚合)',
    user_id         BIGINT          NULL                    COMMENT '当前用户 ID',
    user_role       VARCHAR(16)     NULL                    COMMENT '角色 USER/MERCHANT/ADMIN',

    tool_count      INT             NOT NULL DEFAULT 0      COMMENT '本次 Run 调用的工具次数',
    has_draft       TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '是否生成了草稿',
    total_ms        BIGINT          NOT NULL DEFAULT 0      COMMENT 'Run 总耗时(毫秒)',
    ttft_ms         BIGINT          NOT NULL DEFAULT 0      COMMENT '首字延迟(毫秒),未收到内容时 = total',

    prompt_tokens       INT         NULL                    COMMENT 'input token (含缓存命中部分,具体看 provider)',
    completion_tokens   INT         NULL                    COMMENT 'output token',
    reasoning_tokens    INT         NULL                    COMMENT 'DeepSeek 思维链 token',
    total_tokens        INT         NULL                    COMMENT 'prompt + completion,便于直接看成本',
    prompt_cost_cents   INT         NULL                    COMMENT '按配置折算的 prompt 成本(分)',
    completion_cost_cents INT       NULL                    COMMENT '按配置折算的 completion 成本(分)',

    created_at      DATETIME        NOT NULL                COMMENT '落库时间',
    PRIMARY KEY (id),
    KEY idx_run_id (run_id),
    KEY idx_session_id (session_id),
    KEY idx_user_id_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI Run 用量统计(Stage 1 可观测)';

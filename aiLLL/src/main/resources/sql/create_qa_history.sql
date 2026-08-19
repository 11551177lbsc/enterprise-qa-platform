-- =============================================================================
-- 知识库问答历史表
-- 数据库: ailll
-- 执行方式: 在 ailll 数据库中手动执行本文件
-- =============================================================================

CREATE TABLE IF NOT EXISTS qa_history (
    id              BIGINT          PRIMARY KEY AUTO_INCREMENT  COMMENT '主键',
    user_id         BIGINT          NULL                        COMMENT '用户ID（来自JWT解析，允许为空）',
    session_id      VARCHAR(64)     NOT NULL                    COMMENT '会话ID',
    question        TEXT            NOT NULL                    COMMENT '用户问题',
    answer          MEDIUMTEXT      NULL                        COMMENT '模型回答',
    references_json MEDIUMTEXT      NULL                        COMMENT '引用来源JSON数组',
    status          VARCHAR(32)     NOT NULL                    COMMENT '处理状态: SUCCESS / NO_CONTEXT / FAILED',
    error_message   TEXT            NULL                        COMMENT '错误信息（FAILED状态时记录）',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    update_time     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_user_id (user_id),
    INDEX idx_session_id (session_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库问答历史记录';

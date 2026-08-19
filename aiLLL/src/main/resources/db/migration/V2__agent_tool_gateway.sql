CREATE TABLE IF NOT EXISTS support_ticket (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    description TEXT NOT NULL,
    priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_ticket_user_updated (user_id, updated_at),
    KEY idx_ticket_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户支持工单';

CREATE TABLE IF NOT EXISTS notification_outbox (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    ticket_id BIGINT NOT NULL,
    channel VARCHAR(32) NOT NULL,
    payload_json JSON NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    idempotency_key VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at DATETIME NULL,
    UNIQUE KEY uk_notification_idempotency (idempotency_key),
    KEY idx_notification_status_created (status, created_at),
    CONSTRAINT fk_notification_ticket FOREIGN KEY (ticket_id) REFERENCES support_ticket(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知发件箱（默认不向外部发送）';

CREATE TABLE IF NOT EXISTS agent_tool_execution (
    invocation_id VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    tool_name VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    response_json JSON NULL,
    error_message VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_agent_execution_user_created (user_id, created_at),
    KEY idx_agent_execution_status_updated (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent工具幂等执行与审计记录';

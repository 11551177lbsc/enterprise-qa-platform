CREATE TABLE IF NOT EXISTS support_ticket_detail (
    ticket_id BIGINT PRIMARY KEY,
    category VARCHAR(32) NOT NULL DEFAULT 'OTHER',
    product_model VARCHAR(120) NULL,
    sla_due_at DATETIME NOT NULL,
    knowledge_confidence DECIMAL(5,4) NULL,
    escalation_reason VARCHAR(500) NULL,
    resolution_code VARCHAR(32) NULL,
    assigned_to BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_ticket_detail_category (category),
    KEY idx_ticket_detail_sla (sla_due_at),
    KEY idx_ticket_detail_assignee (assigned_to),
    CONSTRAINT fk_ticket_detail_ticket
        FOREIGN KEY (ticket_id) REFERENCES support_ticket(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工单分类、SLA与分派信息';

CREATE TABLE IF NOT EXISTS support_ticket_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ticket_id BIGINT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    from_status VARCHAR(16) NULL,
    to_status VARCHAR(16) NULL,
    content VARCHAR(1000) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_ticket_event_ticket_time (ticket_id, created_at),
    CONSTRAINT fk_ticket_event_ticket
        FOREIGN KEY (ticket_id) REFERENCES support_ticket(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工单处理时间线';

CREATE TABLE IF NOT EXISTS answer_feedback (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    run_id VARCHAR(64) NOT NULL,
    question VARCHAR(4000) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    comment VARCHAR(1000) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_answer_feedback_run_user (run_id, user_id),
    KEY idx_answer_feedback_outcome_time (outcome, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识答案是否解决问题的用户反馈';

CREATE TABLE IF NOT EXISTS knowledge_gap (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    fingerprint CHAR(64) NOT NULL,
    sample_question VARCHAR(4000) NOT NULL,
    occurrence_count INT NOT NULL DEFAULT 1,
    last_confidence DECIMAL(5,4) NULL,
    last_run_id VARCHAR(64) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    first_seen_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_knowledge_gap_fingerprint (fingerprint),
    KEY idx_knowledge_gap_status_count (status, occurrence_count, last_seen_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='高频未解决问题，用于驱动知识补全';

CREATE TABLE IF NOT EXISTS user_role (
    user_id BIGINT PRIMARY KEY,
    role VARCHAR(32) NOT NULL DEFAULT 'USER',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_user_role_role (role),
    CONSTRAINT fk_user_role_user
        FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支持中心角色；无记录时按USER处理';

INSERT INTO support_ticket_detail(ticket_id, category, sla_due_at)
SELECT
    t.id,
    'OTHER',
    DATE_ADD(
        t.created_at,
        INTERVAL CASE t.priority
            WHEN 'URGENT' THEN 4
            WHEN 'HIGH' THEN 24
            WHEN 'LOW' THEN 120
            ELSE 72
        END HOUR
    )
FROM support_ticket t
WHERE NOT EXISTS (
    SELECT 1 FROM support_ticket_detail d WHERE d.ticket_id = t.id
);

INSERT INTO support_ticket_event(
    ticket_id, actor_user_id, event_type, from_status, to_status, content, created_at
)
SELECT
    t.id,
    t.user_id,
    'CREATED',
    NULL,
    t.status,
    '历史工单迁移生成的初始事件',
    t.created_at
FROM support_ticket t
WHERE NOT EXISTS (
    SELECT 1
    FROM support_ticket_event e
    WHERE e.ticket_id = t.id AND e.event_type = 'CREATED'
);

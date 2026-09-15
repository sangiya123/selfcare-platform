-- V1__create_audit_events.sql
-- Immutable audit trail table. No UPDATE, no DELETE at the application level.

CREATE TABLE IF NOT EXISTS audit_events (
    id              VARCHAR(64) NOT NULL,
    tenant_id       VARCHAR(32) NOT NULL,
    user_id         VARCHAR(64),
    session_id      VARCHAR(64),
    action          VARCHAR(64) NOT NULL,
    resource_type   VARCHAR(64),
    resource_id     VARCHAR(128),
    before_state    TINYTEXT,
    after_state     TINYTEXT,
    correlation_id  VARCHAR(64),
    ip_address      VARCHAR(45),
    user_agent      VARCHAR(512),
    severity        VARCHAR(16),
    message         VARCHAR(1024),
    occurred_at     DATETIME(6) NOT NULL,
    recorded_at     DATETIME(6) NOT NULL,

    PRIMARY KEY (id),
    INDEX ix_audit_tenant (tenant_id),
    INDEX ix_audit_user (user_id),
    INDEX ix_audit_action (action),
    INDEX ix_audit_resource (resource_type, resource_id),
    INDEX ix_audit_occurred (occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- V2__create_admin_sessions.sql

CREATE TABLE IF NOT EXISTS admin_sessions (
    session_id           VARCHAR(64) NOT NULL,
    admin_user_id         VARCHAR(64) NOT NULL,
    tenant_id             VARCHAR(32),
    role                  VARCHAR(32),
    token_family_id       VARCHAR(64),
    current_token_hash    VARCHAR(128),
    previous_token_hash   VARCHAR(128),
    device_id             VARCHAR(128),
    status                VARCHAR(16) NOT NULL,
    issued_at             DATETIME(6) NOT NULL,
    last_used_at          DATETIME(6),
    expires_at            DATETIME(6) NOT NULL,
    revoked_at            DATETIME(6),
    revoked_reason        VARCHAR(64),
    ip_address            VARCHAR(45),
    user_agent            VARCHAR(512),
    created_at            DATETIME(6) NOT NULL,
    updated_at            DATETIME(6) NOT NULL,

    PRIMARY KEY (session_id),
    INDEX ix_admin_session_user (admin_user_id),
    INDEX ix_admin_session_token_family (token_family_id),
    INDEX ix_admin_session_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

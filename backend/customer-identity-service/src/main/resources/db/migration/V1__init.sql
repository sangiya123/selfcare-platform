-- V1__init.sql
-- OMOBIO Selfcare Platform — customer-identity-service
-- Tables: otp_codes, customer_sessions
--
-- Notes:
-- - otp_codes: hashed codes only (no plain OTP). The raw value is held
--   in memory at dispatch time and is never persisted.
-- - customer_sessions: durable session record (MySQL). Hot path is Redis.

-- ============================================================================
-- otp_codes
-- ============================================================================
CREATE TABLE IF NOT EXISTS otp_codes (
    otp_id          VARCHAR(64)  NOT NULL,
    tenant_id       VARCHAR(32)  NOT NULL,
    identifier      VARCHAR(128) NOT NULL,
    code_hash       VARCHAR(128) NOT NULL,
    channel         VARCHAR(16)  NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    attempt_count   INT          NOT NULL DEFAULT 0,
    expires_at      DATETIME(6)  NOT NULL,
    used_at         DATETIME(6)  NULL,
    created_at      DATETIME(6)  NOT NULL,
    correlation_id  VARCHAR(64)  NULL,

    PRIMARY KEY (otp_id),

    CONSTRAINT chk_otp_channel
        CHECK (channel IN ('SMS', 'EMAIL', 'PUSH')),
    CONSTRAINT chk_otp_status
        CHECK (status IN ('ACTIVE', 'USED', 'EXPIRED', 'LOCKED')),

    INDEX ix_otp_tenant_identifier (tenant_id, identifier),
    INDEX ix_otp_correlation (correlation_id),
    INDEX ix_otp_status_expires (status, expires_at),
    INDEX ix_otp_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- customer_sessions
-- ============================================================================
CREATE TABLE IF NOT EXISTS customer_sessions (
    session_id           VARCHAR(64)  NOT NULL,
    tenant_id            VARCHAR(32)  NOT NULL,
    user_id              VARCHAR(64)  NOT NULL,
    token_family_id      VARCHAR(64)  NULL,
    device_id            VARCHAR(128) NULL,
    device_description   VARCHAR(256) NULL,
    ip_address           VARCHAR(45)  NULL,
    status               VARCHAR(16)  NOT NULL,
    current_token_hash   VARCHAR(128) NULL,
    previous_token_hash  VARCHAR(128) NULL,
    issued_at            DATETIME(6)  NOT NULL,
    last_used_at         DATETIME(6)  NULL,
    expires_at           DATETIME(6)  NOT NULL,
    revoked_at           DATETIME(6)  NULL,
    revoked_reason       VARCHAR(64)  NULL,
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    opt_lock_version     BIGINT       NULL,

    PRIMARY KEY (session_id),

    CONSTRAINT chk_session_status
        CHECK (status IN ('ACTIVE', 'EXPIRED', 'REVOKED', 'REPLACED')),

    INDEX ix_session_tenant_user (tenant_id, user_id),
    INDEX ix_session_token_family (token_family_id),
    INDEX ix_session_device (device_id),
    INDEX ix_session_tenant_status (tenant_id, status),
    INDEX ix_session_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

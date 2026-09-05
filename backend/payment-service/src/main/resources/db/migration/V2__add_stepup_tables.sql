-- V2__add_stepup_tables.sql
-- OMOBIO Selfcare Platform — payment-service
-- Tables: step_up_request, step_up_verification
--
-- Security:
-- - step_up_request: stores hashed OTP codes (SHA-256), never plaintext.
-- - step_up_verification: append-only audit log, never mutated.

-- ============================================================================
-- step_up_request
-- Tracks in-flight and completed step-up authentication attempts.
-- The code itself is stored hashed; Redis holds the plaintext for OTP send.
-- ============================================================================
CREATE TABLE IF NOT EXISTS step_up_request (
    correlation_id     VARCHAR(64)    NOT NULL,
    tenant_id          VARCHAR(64)    NOT NULL,
    user_id            VARCHAR(64)    NOT NULL,
    action             VARCHAR(64)    NOT NULL,
    amount             DECIMAL(19, 4) NULL,
    idempotency_key    VARCHAR(100)   NULL,
    code_hash          VARCHAR(256)   NOT NULL,
    status             VARCHAR(20)    NOT NULL,
    attempt_count      INT            NULL DEFAULT 0,
    expires_at         DATETIME(6)   NOT NULL,
    verified_at        DATETIME(6)    NULL,
    created_at         DATETIME(6)   NOT NULL,

    PRIMARY KEY (correlation_id),

    CONSTRAINT chk_sur_status
        CHECK (status IN ('PENDING', 'VERIFIED', 'EXPIRED', 'LOCKED')),
    CONSTRAINT chk_sur_attempts
        CHECK (attempt_count IS NULL OR attempt_count >= 0),

    INDEX idx_stepup_correlation (correlation_id),
    INDEX idx_stepup_tenant_user (tenant_id, user_id),
    INDEX idx_stepup_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- step_up_verification
-- Append-only audit log of successful step-up completions.
-- Used for audit trail only — do NOT use for retry logic.
-- ============================================================================
CREATE TABLE IF NOT EXISTS step_up_verification (
    id             BIGINT AUTO_INCREMENT  NOT NULL,
    correlation_id VARCHAR(64)            NOT NULL,
    tenant_id      VARCHAR(64)            NOT NULL,
    user_id        VARCHAR(64)            NOT NULL,
    action         VARCHAR(64)            NOT NULL,
    verified_at    DATETIME(6)            NOT NULL,

    PRIMARY KEY (id),

    INDEX idx_stepup_verify_correlation (correlation_id),
    INDEX idx_stepup_verify_tenant (tenant_id),
    INDEX idx_stepup_verify_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

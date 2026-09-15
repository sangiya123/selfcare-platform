-- V2__allowances.sql
-- Selfcare Platform — usage-service
-- Table: allowances
--
-- Per-connection bucket of data/voice/SMS allowance with expiry.
-- Written by the daily usage-rollup job and by Kafka events for
-- recharge / package-purchase notifications.

CREATE TABLE IF NOT EXISTS allowances (
    allowance_id        VARCHAR(64)   NOT NULL,
    tenant_id           VARCHAR(32)   NOT NULL,
    connection_id       VARCHAR(64)   NOT NULL,

    -- Type: DATA, VOICE, SMS, COMBO, BUNDLE
    allowance_type      VARCHAR(16)   NOT NULL,

    -- Display label
    name                VARCHAR(128)  NULL,

    -- Origin: PLAN, RECHARGE, PROMOTIONAL, BONUS, ADMIN_GRANT
    source              VARCHAR(32)   NULL,

    total_units         BIGINT        NOT NULL,
    used_units          BIGINT        NOT NULL DEFAULT 0,
    remaining_units     BIGINT        NOT NULL,

    -- Unit: BYTES, SECONDS, COUNT
    unit                VARCHAR(16)   NULL,

    activated_at        DATETIME(6)   NULL,
    expires_at          DATETIME(6)   NULL,

    -- Status: ACTIVE, EXHAUSTED, EXPIRED, CANCELLED
    status              VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',

    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,

    PRIMARY KEY (allowance_id),

    CONSTRAINT chk_allowance_type
        CHECK (allowance_type IN ('DATA', 'VOICE', 'SMS', 'COMBO', 'BUNDLE')),
    CONSTRAINT chk_allowance_unit
        CHECK (unit IS NULL OR unit IN ('BYTES', 'SECONDS', 'COUNT')),
    CONSTRAINT chk_allowance_status
        CHECK (status IN ('ACTIVE', 'EXHAUSTED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT chk_allowance_units_nonneg
        CHECK (total_units >= 0 AND used_units >= 0 AND remaining_units >= 0),
    CONSTRAINT chk_allowance_units_sum
        CHECK (used_units + remaining_units <= total_units + remaining_units),
    -- Simple invariant: remaining = total - used (within 0..total)
    CONSTRAINT chk_allowance_used_le_total
        CHECK (used_units <= total_units),

    INDEX ix_allowance_tenant_conn (tenant_id, connection_id),
    INDEX ix_allowance_tenant_conn_active
        (tenant_id, connection_id, status),
    INDEX ix_allowance_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

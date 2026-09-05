-- V2__audit_config.sql
-- OMOBIO Selfcare Platform — audit-service
-- Audit retention configuration table (platform-wide, no per-tenant for now)
--
-- Retention policy:
--   Default: 730 days (2 years) for compliance
--   Legal hold: events marked as LEGAL_HOLD are retained indefinitely
--   PII anonymization: events older than 365 days have PII fields set to NULL
--     before the physical deletion job runs.
--
-- The audit table itself is append-only. Deletion happens ONLY via this
-- configuration and the AuditRetentionJob.

CREATE TABLE IF NOT EXISTS audit_retention_config (
    config_id      VARCHAR(64)   NOT NULL PRIMARY KEY,
    tenant_id     VARCHAR(32)   NOT NULL DEFAULT 'PLATFORM',
    retention_days INT          NOT NULL DEFAULT 730,
    legal_hold    BOOLEAN       NOT NULL DEFAULT FALSE,
    anonymize_pii  BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,

    INDEX ix_audit_retention_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Default platform config
INSERT INTO audit_retention_config (config_id, tenant_id, retention_days, legal_hold, anonymize_pii, created_at, updated_at)
VALUES ('platform-default', 'PLATFORM', 730, FALSE, TRUE, NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

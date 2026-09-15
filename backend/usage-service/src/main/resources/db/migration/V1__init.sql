-- V1__init.sql
-- Selfcare Platform — usage-service
-- Table: usage_records
--
-- usage-service currently fetches balance/usage live from per-tenant
-- BalanceProvider adapters and caches results in Redis (TTL ~5 min).
-- No JPA entity exists today. This migration introduces a durable
-- usage_records table so we can:
--   - persist the last seen balance per connection (post-usage cache),
--   - keep a sliding window of usage summaries for analytics / history,
--   - support the cache-fallback path when the upstream provider is down.
--
-- The schema mirrors the canonical fields on
-- com.selfcare.usage.adapter.BalanceProvider.{Balance, UsageSummary}.

-- ============================================================================
-- usage_records
-- ============================================================================
CREATE TABLE IF NOT EXISTS usage_records (
    record_id           BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id           VARCHAR(32)   NOT NULL,
    connection_id       VARCHAR(64)   NOT NULL,

    -- Period this record covers
    period_start        DATETIME(6)   NOT NULL,
    period_end          DATETIME(6)   NOT NULL,
    recorded_at         DATETIME(6)   NOT NULL,

    -- Current balance snapshot (may be null for pure-usage records)
    balance_amount      DECIMAL(19, 4) NULL,
    balance_currency    VARCHAR(8)    NULL,
    balance_type        VARCHAR(32)   NULL, -- PREPAID, POSTPAID_DUE, POSTPAID_AVAILABLE
    balance_expiry_at   DATETIME(6)   NULL,
    balance_is_primary  BOOLEAN       NULL,

    -- Data usage (bytes)
    data_total_bytes        BIGINT    NULL,
    data_remaining_bytes    BIGINT    NULL,
    data_allowance_bytes    BIGINT    NULL,
    data_reset_at           DATETIME(6) NULL,
    data_is_unlimited       BOOLEAN   NULL,

    -- Voice usage (seconds)
    voice_total_seconds     BIGINT    NULL,
    voice_remaining_seconds BIGINT    NULL,
    voice_allowance_seconds BIGINT    NULL,
    voice_reset_at          DATETIME(6) NULL,
    voice_is_unlimited      BOOLEAN   NULL,

    -- SMS usage (count)
    sms_total_count         BIGINT    NULL,
    sms_remaining_count     BIGINT    NULL,
    sms_allowance_count     BIGINT    NULL,
    sms_reset_at            DATETIME(6) NULL,
    sms_is_unlimited        BOOLEAN   NULL,

    -- Provenance
    source_provider         VARCHAR(64) NULL, -- adapter key (e.g. "dialog-balance")
    is_stale                BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at              DATETIME(6) NOT NULL,
    updated_at              DATETIME(6) NOT NULL,

    PRIMARY KEY (record_id),

    CONSTRAINT chk_usage_balance_type
        CHECK (balance_type IS NULL
               OR balance_type IN
                   ('PREPAID', 'POSTPAID_DUE', 'POSTPAID_AVAILABLE')),
    CONSTRAINT chk_usage_period
        CHECK (period_end >= period_start),
    CONSTRAINT chk_usage_data_nonneg
        CHECK (data_total_bytes IS NULL OR data_total_bytes >= 0),
    CONSTRAINT chk_usage_voice_nonneg
        CHECK (voice_total_seconds IS NULL OR voice_total_seconds >= 0),
    CONSTRAINT chk_usage_sms_nonneg
        CHECK (sms_total_count IS NULL OR sms_total_count >= 0),

    INDEX ix_usage_tenant_connection (tenant_id, connection_id),
    INDEX ix_usage_tenant_connection_period
        (tenant_id, connection_id, period_start, period_end),
    INDEX ix_usage_recorded_at (recorded_at),
    INDEX ix_usage_tenant_period (tenant_id, period_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

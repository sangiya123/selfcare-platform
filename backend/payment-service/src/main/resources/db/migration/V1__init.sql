-- V1__init.sql
-- OMOBIO Selfcare Platform — payment-service
-- Tables: payment_methods, payment_transactions
--
-- Security:
-- - payment_methods: stores PSP-issued tokens, never raw PAN. Only lastFour
--   is kept for display.
-- - payment_transactions: every write is idempotent on
--   (tenant_id, idempotency_key) — see V1 below for the unique index.

-- ============================================================================
-- payment_methods
-- ============================================================================
CREATE TABLE IF NOT EXISTS payment_methods (
    payment_method_id   VARCHAR(64)  NOT NULL,
    tenant_id           VARCHAR(32)  NOT NULL,
    user_id             VARCHAR(64)  NOT NULL,
    method_type         VARCHAR(32)  NOT NULL,
    brand               VARCHAR(32)  NULL,
    last_four           VARCHAR(4)   NULL,
    expiry_month        INT          NULL,
    expiry_year         INT          NULL,
    cardholder_name     VARCHAR(128) NULL,
    payment_token       VARCHAR(128) NOT NULL,
    provider            VARCHAR(32)  NULL,
    is_default          BOOLEAN      NOT NULL DEFAULT FALSE,
    status              VARCHAR(16)  NOT NULL,
    nickname            VARCHAR(64)  NULL,
    metadata            JSON         NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,

    PRIMARY KEY (payment_method_id),

    CONSTRAINT chk_pm_method_type
        CHECK (method_type IN ('CARD', 'BANK_ACCOUNT', 'WALLET', 'MOBILE_MONEY')),
    CONSTRAINT chk_pm_status
        CHECK (status IN ('ACTIVE', 'EXPIRED', 'REVOKED')),
    CONSTRAINT chk_pm_expiry_month
        CHECK (expiry_month IS NULL OR (expiry_month BETWEEN 1 AND 12)),
    CONSTRAINT chk_pm_expiry_year
        CHECK (expiry_year IS NULL OR (expiry_year BETWEEN 1970 AND 9999)),

    UNIQUE INDEX uk_pm_payment_token (payment_token),
    INDEX ix_pm_tenant_user (tenant_id, user_id),
    INDEX ix_pm_tenant_user_default (tenant_id, user_id, is_default),
    INDEX ix_pm_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- payment_transactions
-- ============================================================================
CREATE TABLE IF NOT EXISTS payment_transactions (
    transaction_id        VARCHAR(64)    NOT NULL,
    tenant_id             VARCHAR(32)    NOT NULL,
    user_id               VARCHAR(64)    NOT NULL,
    idempotency_key       VARCHAR(128)   NULL,
    transaction_type      VARCHAR(32)    NOT NULL,
    source_connection_id  VARCHAR(64)    NULL,
    target_connection_id  VARCHAR(64)    NULL,
    bill_id               VARCHAR(64)    NULL,
    amount                DECIMAL(19, 4) NOT NULL,
    currency              VARCHAR(8)     NOT NULL,
    fee                   DECIMAL(19, 4) NULL,
    payment_method        VARCHAR(32)    NULL,
    payment_token         VARCHAR(128)   NULL,
    provider              VARCHAR(32)    NULL,
    provider_reference    VARCHAR(128)   NULL,
    status                VARCHAR(16)    NOT NULL,
    failure_reason        VARCHAR(256)   NULL,
    metadata              JSON           NULL,
    receipt_url           VARCHAR(512)   NULL,
    step_up_required      BOOLEAN        NOT NULL DEFAULT FALSE,
    step_up_completed     BOOLEAN        NOT NULL DEFAULT FALSE,
    retry_count           INT            NULL,
    last_retry_at         DATETIME(6)    NULL,
    completed_at          DATETIME(6)    NULL,
    created_at            DATETIME(6)    NOT NULL,
    updated_at            DATETIME(6)    NOT NULL,
    opt_lock_version      BIGINT         NULL,

    PRIMARY KEY (transaction_id),

    CONSTRAINT chk_pt_type
        CHECK (transaction_type IN
            ('RECHARGE', 'BILL_PAYMENT', 'PACKAGE_PURCHASE', 'TRANSFER')),
    CONSTRAINT chk_pt_status
        CHECK (status IN
            ('PENDING', 'SUCCESS', 'FAILED', 'UNKNOWN', 'REVERSED')),
    CONSTRAINT chk_pt_amount_positive
        CHECK (amount >= 0),
    CONSTRAINT chk_pt_fee_nonnegative
        CHECK (fee IS NULL OR fee >= 0),
    CONSTRAINT chk_pt_retry_nonnegative
        CHECK (retry_count IS NULL OR retry_count >= 0),

    UNIQUE INDEX uk_payment_idempotency (idempotency_key),
    INDEX ix_payment_tenant_user (tenant_id, user_id),
    INDEX ix_payment_status (status),
    INDEX ix_payment_created (created_at),
    INDEX ix_payment_tenant_status_created (tenant_id, status, created_at),
    INDEX ix_payment_target_connection (tenant_id, target_connection_id),
    INDEX ix_payment_bill (bill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

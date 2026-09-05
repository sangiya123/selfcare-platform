-- V2__bill_items_and_config.sql
-- OMOBIO Selfcare Platform — billing-service
-- Tables: bill_items, billing_cycle_configs
--
-- bill_items: line-item breakdown of each bill for itemized invoice display
-- billing_cycle_configs: per-tenant late-fee, due-date, and tax rules

-- ============================================================================
-- bill_items
-- ============================================================================
CREATE TABLE IF NOT EXISTS bill_items (
    bill_item_id      VARCHAR(64)   NOT NULL,
    tenant_id         VARCHAR(32)   NOT NULL,
    bill_id          VARCHAR(64)   NOT NULL,
    connection_id     VARCHAR(64)   NULL,

    item_type         VARCHAR(16)   NOT NULL,  -- CHARGE, DISCOUNT, TAX, FEE, ADJUSTMENT
    category          VARCHAR(32)   NOT NULL,  -- LINE_RENT, VOICE, DATA, SMS, ROAMING, VAS, TAX, LATE_FEE, etc.
    description       VARCHAR(256)  NULL,
    quantity          DECIMAL(19, 4) NULL,
    unit_price        DECIMAL(19, 4) NULL,
    amount            DECIMAL(19, 4) NOT NULL,
    tax_rate          DECIMAL(8, 4) NULL,
    tax_amount        DECIMAL(19, 4) NULL,
    source_code       VARCHAR(64)   NULL,
    period_start      DATETIME(6)   NULL,
    period_end        DATETIME(6)   NULL,
    created_at        DATETIME(6)   NOT NULL,

    PRIMARY KEY (bill_item_id),

    CONSTRAINT chk_bill_item_type
        CHECK (item_type IN ('CHARGE', 'DISCOUNT', 'TAX', 'FEE', 'ADJUSTMENT')),
    CONSTRAINT chk_bill_item_amount
        CHECK (amount >= 0),

    INDEX ix_bill_item_tenant_bill (tenant_id, bill_id),
    INDEX ix_bill_item_category (category),
    INDEX ix_bill_item_tenant_conn_period (tenant_id, connection_id, period_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- billing_cycle_configs
-- ============================================================================
CREATE TABLE IF NOT EXISTS billing_cycle_configs (
    tenant_id         VARCHAR(32)   NOT NULL PRIMARY KEY,

    bill_issue_day   INT          NULL,   -- Day of month (1-28)
    payment_due_days  INT          NOT NULL DEFAULT 14,
    late_fee_pct     DECIMAL(8, 4) NULL DEFAULT 2.0000,  -- e.g. 2.0000 = 2%
    late_fee_cap     DECIMAL(19, 4) NULL,
    default_tax_pct  DECIMAL(8, 4) NULL,
    currency         VARCHAR(8)   NULL,
    grace_period_days INT         NULL DEFAULT 3,

    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,

    CONSTRAINT chk_late_fee_pct CHECK (late_fee_pct IS NULL OR late_fee_pct >= 0),
    CONSTRAINT chk_grace_period CHECK (grace_period_days IS NULL OR grace_period_days >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed default configs for known tenants
INSERT INTO billing_cycle_configs (tenant_id, payment_due_days, late_fee_pct, late_fee_cap, default_tax_pct, currency, grace_period_days, created_at, updated_at)
VALUES
    ('dialog-lk',  14, 2.0000, 500.0000, 18.0000, 'LKR', 3, NOW(), NOW()),
    ('hutch-lk',   14, 2.0000, 500.0000, 18.0000, 'LKR', 3, NOW(), NOW()),
    ('airtel-lk',  14, 2.0000, 500.0000, 18.0000, 'LKR', 3, NOW(), NOW()),
    ('aia-multi',  30, 1.5000, 1000.0000, 8.0000, 'USD', 5, NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

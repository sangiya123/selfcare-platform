-- V1__init.sql
-- OMOBIO Selfcare Platform — billing-service
-- Table: bills
--
-- Canonical Bill entity. Status lifecycle:
--   DRAFT -> ISSUED -> DUE -> PARTIALLY_PAID -> PAID
--                                  |-> OVERDUE
--                                  |-> CANCELLED

-- ============================================================================
-- bills
-- ============================================================================
CREATE TABLE IF NOT EXISTS bills (
    bill_id                VARCHAR(64)    NOT NULL,
    tenant_id              VARCHAR(32)    NOT NULL,
    account_id             VARCHAR(64)    NULL,
    connection_id          VARCHAR(64)    NULL,
    source_bill_id         VARCHAR(128)   NULL,
    bill_number            VARCHAR(64)    NULL,
    bill_type              VARCHAR(32)    NULL,
    billing_period_start   DATE           NULL,
    billing_period_end     DATE           NULL,
    issue_date             DATE           NULL,
    due_date               DATE           NULL,
    total_amount           DECIMAL(19, 4) NOT NULL,
    paid_amount            DECIMAL(19, 4) NULL,
    outstanding_amount     DECIMAL(19, 4) NULL,
    currency               VARCHAR(8)     NULL,
    status                 VARCHAR(16)    NOT NULL,
    pdf_url                VARCHAR(512)   NULL,
    line_items             JSON           NULL,
    tax_breakdown          JSON           NULL,
    created_at             DATETIME(6)    NOT NULL,
    updated_at             DATETIME(6)    NOT NULL,
    opt_lock_version       BIGINT         NULL,

    PRIMARY KEY (bill_id),

    CONSTRAINT chk_bill_type
        CHECK (bill_type IS NULL
               OR bill_type IN ('POSTPAID', 'PREPAID_HISTORY', 'RECURRING')),
    CONSTRAINT chk_bill_status
        CHECK (status IN
            ('DRAFT', 'ISSUED', 'DUE', 'PARTIALLY_PAID',
             'PAID', 'OVERDUE', 'CANCELLED')),
    CONSTRAINT chk_bill_total_nonneg
        CHECK (total_amount >= 0),
    CONSTRAINT chk_bill_paid_nonneg
        CHECK (paid_amount IS NULL OR paid_amount >= 0),
    CONSTRAINT chk_bill_period
        CHECK (billing_period_end IS NULL
               OR billing_period_start IS NULL
               OR billing_period_end >= billing_period_start),

    INDEX ix_bill_tenant_connection (tenant_id, connection_id),
    INDEX ix_bill_status (status),
    INDEX ix_bill_due_date (due_date),
    INDEX ix_bill_tenant_status_due
        (tenant_id, status, due_date),
    INDEX ix_bill_account (account_id),
    INDEX ix_bill_number (bill_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

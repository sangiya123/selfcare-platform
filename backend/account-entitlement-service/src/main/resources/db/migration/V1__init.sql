-- V1__init.sql
-- OMOBIO Selfcare Platform — account-entitlement-service
-- Tables: accounts, connections
--
-- One account per primary identity (e.g. Dialog primary MSISDN).
-- Many connections per account (mobile, BB, DTV, fibre) — see ADR-006
-- (Dialog entitlement = primary identity's linked-connection list).

-- ============================================================================
-- accounts
-- ============================================================================
CREATE TABLE IF NOT EXISTS accounts (
    account_id            VARCHAR(64)  NOT NULL,
    tenant_id             VARCHAR(32)  NOT NULL,
    primary_identity      VARCHAR(32)  NOT NULL,
    profile_version       BIGINT       NULL,
    status                VARCHAR(16)  NOT NULL,
    last_login_at         DATETIME(6)  NULL,
    display_name          VARCHAR(128) NULL,
    email                 VARCHAR(128) NULL,
    operator_attributes   JSON         NULL,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    opt_lock_version      BIGINT       NULL,

    PRIMARY KEY (account_id),

    CONSTRAINT chk_account_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),

    UNIQUE INDEX uk_account_tenant_primary (tenant_id, primary_identity),
    INDEX ix_account_tenant (tenant_id),
    INDEX ix_account_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- connections
-- ============================================================================
CREATE TABLE IF NOT EXISTS connections (
    connection_id         VARCHAR(64)  NOT NULL,
    account_id            VARCHAR(64)  NOT NULL,
    tenant_id             VARCHAR(32)  NOT NULL,
    number                VARCHAR(32)  NOT NULL,
    lob                   VARCHAR(16)  NOT NULL,
    connection_type       VARCHAR(16)  NULL,
    relationship          VARCHAR(16)  NOT NULL,
    status                VARCHAR(16)  NOT NULL,
    display_name          VARCHAR(64)  NULL,
    is_primary            BOOLEAN      NOT NULL DEFAULT FALSE,
    linked_at             DATETIME(6)  NULL,
    operator_attributes   JSON         NULL,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    opt_lock_version      BIGINT       NULL,

    PRIMARY KEY (connection_id),

    CONSTRAINT chk_connection_lob
        CHECK (lob IN ('MOBILE', 'BB', 'DTV', 'FIBRE')),
    CONSTRAINT chk_connection_relationship
        CHECK (relationship IN ('PRIMARY', 'LINKED')),
    CONSTRAINT chk_connection_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DISCONNECTED')),
    CONSTRAINT fk_connection_account
        FOREIGN KEY (account_id) REFERENCES accounts (account_id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    UNIQUE INDEX uk_connection_tenant_number (tenant_id, number),
    INDEX ix_connection_account (account_id),
    INDEX ix_connection_number (number),
    INDEX ix_connection_tenant_status (tenant_id, status),
    INDEX ix_connection_tenant_account (tenant_id, account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

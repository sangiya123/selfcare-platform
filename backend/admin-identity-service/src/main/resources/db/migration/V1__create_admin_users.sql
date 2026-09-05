-- V1__create_admin_users.sql

CREATE TABLE IF NOT EXISTS admin_users (
    id                    VARCHAR(64) NOT NULL,
    tenant_id             VARCHAR(32),
    email                 VARCHAR(256) NOT NULL,
    full_name             VARCHAR(256),
    password_hash         VARCHAR(256),
    role                  VARCHAR(32) NOT NULL,
    status                VARCHAR(32) NOT NULL,
    last_login_at         DATETIME(6),
    last_login_ip         VARCHAR(45),
    failed_login_count    INT NOT NULL DEFAULT 0,
    locked_until          DATETIME(6),
    mfa_enabled           BOOLEAN NOT NULL DEFAULT FALSE,
    mfa_secret            VARCHAR(256),
    created_at            DATETIME(6) NOT NULL,
    updated_at            DATETIME(6) NOT NULL,
    created_by            VARCHAR(64),

    PRIMARY KEY (id),
    INDEX ix_admin_tenant_email (tenant_id, email),
    INDEX ix_admin_email (email),
    INDEX ix_admin_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

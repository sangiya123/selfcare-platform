-- V4__auth_settings.sql
-- OMOBIO Selfcare Platform — admin-identity-service
-- Table: admin_auth_settings
-- Tenant-scoped auth & security configuration.
-- One row per tenant.

CREATE TABLE IF NOT EXISTS admin_auth_settings (
    tenant_id           VARCHAR(64)  NOT NULL PRIMARY KEY,
    providers_enabled   VARCHAR(256) NULL,
    otp_code_length     INT          NULL,
    otp_algorithm       VARCHAR(16)  NULL,
    otp_expiry_seconds  INT          NULL,
    otp_max_attempts    INT          NULL,
    otp_lockout_minutes INT          NULL,
    otp_backup_codes    INT          NULL,
    otp_allow_qr        BOOLEAN      NULL,
    oidc_provider_type  VARCHAR(32)  NULL,
    oidc_discovery_url  VARCHAR(512) NULL,
    oidc_client_id      VARCHAR(128) NULL,
    oidc_client_secret  VARCHAR(512) NULL,
    oidc_scopes         VARCHAR(256) NULL,
    oidc_button_label   VARCHAR(128) NULL,
    session_access_min  INT          NULL,
    session_refresh_d   INT          NULL,
    session_max         INT          NULL,
    session_dev_policy  VARCHAR(16)  NULL,
    session_same_dev    BOOLEAN      NULL,
    risk_rules_json     TEXT         NULL,
    allowed_origins     VARCHAR(1024) NULL,
    updated_at          DATETIME(6)  NOT NULL,
    updated_by          VARCHAR(64)  NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

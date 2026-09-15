-- V4__auth_settings.sql
-- Selfcare Platform - admin-identity-service
-- Table: admin_auth_settings
-- Tenant-scoped auth & security configuration.
-- One row per tenant.
-- NOTE: columns follow the @Embedded attribute naming (OtpPolicy,
-- OidcConfig, SessionPolicy are embedded WITHOUT @AttributeOverrides),
-- so Hibernate expects the unprefixed default column names below.

CREATE TABLE IF NOT EXISTS admin_auth_settings (
    tenant_id              VARCHAR(64)  NOT NULL PRIMARY KEY,
    providers_enabled      VARCHAR(256) NULL,
    code_length            INT          NULL,
    algorithm              VARCHAR(16)  NULL,
    expiry_seconds         INT          NULL,
    max_attempts           INT          NULL,
    lockout_minutes        INT          NULL,
    backup_codes_per_user  INT          NULL,
    allow_qr_scan          BOOLEAN      NULL,
    provider_type          VARCHAR(32)  NULL,
    discovery_url          VARCHAR(512) NULL,
    client_id              VARCHAR(128) NULL,
    client_secret          VARCHAR(512) NULL,
    scopes                 VARCHAR(256) NULL,
    sign_in_button_label   VARCHAR(128) NULL,
    access_token_minutes   INT          NULL,
    refresh_token_days     INT          NULL,
    max_concurrent_sessions INT         NULL,
    device_fingerprint_policy VARCHAR(16) NULL,
    same_device_reauth     BOOLEAN      NULL,
    risk_rules_json        TEXT         NULL,
    allowed_origins        VARCHAR(1024) NULL,
    updated_at             DATETIME(6)  NOT NULL,
    updated_by             VARCHAR(64)  NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
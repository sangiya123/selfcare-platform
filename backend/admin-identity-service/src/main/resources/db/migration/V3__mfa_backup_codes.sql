-- V3__mfa_backup_codes.sql
-- Adds backup codes table for TOTP MFA.

CREATE TABLE IF NOT EXISTS admin_mfa_backup_codes (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_user_id   VARCHAR(64) NOT NULL,
    hashed_code     VARCHAR(128) NOT NULL,
    used_at         DATETIME(6),
    created_at      DATETIME(6) NOT NULL,

    INDEX ix_backup_codes_user (admin_user_id),
    FOREIGN KEY (admin_user_id) REFERENCES admin_users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

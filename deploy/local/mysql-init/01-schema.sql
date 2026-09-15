-- ============================================================================
-- Selfcare Platform — MySQL Schema Seed
-- ============================================================================
-- Runs ONCE on first container start via /docker-entrypoint-initdb.d/
-- Creates ALL databases required by the 18 microservices.
--
-- Database mapping (from service application.yml):
--   selfcare_identity   → customer-identity-service, ai-gateway
--   selfcare_admin      → admin-identity-service
--   selfcare_core       → account-entitlement, billing, usage, product, insurance
--   selfcare_payment    → payment-service
--   selfcare_audit      → audit-service
--   selfcare_notification → notification-service
--   selfcare_approval   → approval-service
--   selfcare_reporting  → reporting-service
-- ============================================================================

-- ─── Create all databases ──────────────────────────────────────────────────
CREATE DATABASE IF NOT EXISTS selfcare_identity   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS selfcare_admin      CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS selfcare_core       CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS selfcare_payment    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS selfcare_audit      CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS selfcare_notification CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS selfcare_approval   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS selfcare_reporting  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ─── Grant privileges to application user ──────────────────────────────────
GRANT ALL PRIVILEGES ON selfcare_identity.*   TO 'selfcare'@'%';
GRANT ALL PRIVILEGES ON selfcare_admin.*      TO 'selfcare'@'%';
GRANT ALL PRIVILEGES ON selfcare_core.*       TO 'selfcare'@'%';
GRANT ALL PRIVILEGES ON selfcare_payment.*    TO 'selfcare'@'%';
GRANT ALL PRIVILEGES ON selfcare_audit.*      TO 'selfcare'@'%';
GRANT ALL PRIVILEGES ON selfcare_notification.* TO 'selfcare'@'%';
GRANT ALL PRIVILEGES ON selfcare_approval.*   TO 'selfcare'@'%';
GRANT ALL PRIVILEGES ON selfcare_reporting.*  TO 'selfcare'@'%';
FLUSH PRIVILEGES;

-- ============================================================================
-- selfcare_identity — Customer & Admin identity tables
-- ============================================================================
USE selfcare_identity;

-- Customer sessions — matches Session entity (@Table("customer_sessions"))
CREATE TABLE IF NOT EXISTS customer_sessions (
  session_id           VARCHAR(64)  NOT NULL,
  tenant_id            VARCHAR(32)  NOT NULL,
  user_id              VARCHAR(64)  NOT NULL,
  token_family_id      VARCHAR(64),
  device_id            VARCHAR(128),
  device_description   VARCHAR(256),
  ip_address           VARCHAR(45),
  status               VARCHAR(16)  NOT NULL,
  current_token_hash   VARCHAR(128),
  previous_token_hash  VARCHAR(128),
  issued_at            DATETIME     NOT NULL,
  last_used_at         DATETIME,
  expires_at           DATETIME     NOT NULL,
  revoked_at           DATETIME,
  revoked_reason       VARCHAR(64),
  created_at           DATETIME     NOT NULL,
  updated_at           DATETIME     NOT NULL,
  opt_lock_version     BIGINT,
  PRIMARY KEY (session_id),
  KEY ix_session_tenant_user (tenant_id, user_id),
  KEY ix_session_token_family (token_family_id),
  KEY ix_session_device (device_id)
) ENGINE=InnoDB;

-- GDPR consent records — matches ConsentRecord entity (@Table("consent_records"))
CREATE TABLE IF NOT EXISTS consent_records (
  id              CHAR(36)     NOT NULL,
  tenant_id       VARCHAR(64)  NOT NULL,
  user_id         VARCHAR(64)  NOT NULL,
  purpose         VARCHAR(64)  NOT NULL,
  version         VARCHAR(32)  NOT NULL,
  granted         TINYINT(1)   NOT NULL,
  source          VARCHAR(128),
  ip_address      VARCHAR(64),
  user_agent      VARCHAR(256),
  captured_at     DATETIME     NOT NULL,
  superseded_at   DATETIME,
  PRIMARY KEY (id),
  KEY ix_consent_user (tenant_id, user_id, purpose),
  KEY ix_consent_active (tenant_id, user_id, purpose, superseded_at)
) ENGINE=InnoDB;

-- Data erasure requests (GDPR Article 17) — matches DataErasureRequest entity
CREATE TABLE IF NOT EXISTS data_erasure_requests (
  id               CHAR(36)     NOT NULL,
  tenant_id        VARCHAR(64)  NOT NULL,
  user_id_hash     VARCHAR(128) NOT NULL,
  status           VARCHAR(32)  NOT NULL,
  reason           VARCHAR(512),
  requested_by     VARCHAR(64),
  ip_address       VARCHAR(45),
  user_agent       VARCHAR(512),
  requested_at     DATETIME     NOT NULL,
  started_at       DATETIME,
  completed_at     DATETIME,
  failure_reason   VARCHAR(1024),
  notified_services VARCHAR(1024),
  PRIMARY KEY (id),
  KEY ix_erasure_user_hash (tenant_id, user_id_hash),
  KEY ix_erasure_status (status)
) ENGINE=InnoDB;

-- OTP codes — matches OtpCode entity (@Table("otp_codes"))
CREATE TABLE IF NOT EXISTS otp_codes (
  otp_id           VARCHAR(64)  NOT NULL,
  tenant_id        VARCHAR(32)  NOT NULL,
  identifier       VARCHAR(128) NOT NULL,
  code_hash        VARCHAR(128) NOT NULL,
  channel          VARCHAR(16)  NOT NULL,
  status           VARCHAR(16)  NOT NULL,
  attempt_count    INT          NOT NULL DEFAULT 0,
  expires_at       DATETIME     NOT NULL,
  used_at          DATETIME,
  created_at       DATETIME     NOT NULL,
  correlation_id   VARCHAR(64),
  PRIMARY KEY (otp_id),
  KEY ix_otp_tenant_identifier (tenant_id, identifier),
  KEY ix_otp_correlation (correlation_id),
  KEY ix_otp_status_expires (status, expires_at)
) ENGINE=InnoDB;

-- ============================================================================
-- selfcare_admin — Admin identity, RBAC, MFA
-- ============================================================================
USE selfcare_admin;

-- Admin users
CREATE TABLE IF NOT EXISTS admin_users (
  id              VARCHAR(64)  NOT NULL,
  tenant_id       VARCHAR(64)  NOT NULL,
  email           VARCHAR(255) NOT NULL,
  password_hash   VARCHAR(256) NOT NULL,
  full_name       VARCHAR(256) NOT NULL,
  role            VARCHAR(64)  NOT NULL DEFAULT 'VIEWER',
  status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
  mfa_enabled     TINYINT(1)   DEFAULT 0,
  mfa_secret      VARCHAR(128),
  last_login_at   DATETIME,
  created_at      DATETIME     NOT NULL,
  updated_at      DATETIME     NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_email (tenant_id, email)
) ENGINE=InnoDB;

-- Admin sessions — matches AdminSession entity (@Table("admin_sessions"))
CREATE TABLE IF NOT EXISTS admin_sessions (
  session_id           VARCHAR(64)  NOT NULL,
  admin_user_id        VARCHAR(64)  NOT NULL,
  tenant_id            VARCHAR(32),
  role                 VARCHAR(32),
  token_family_id      VARCHAR(64),
  current_token_hash   VARCHAR(128),
  previous_token_hash  VARCHAR(128),
  device_id            VARCHAR(128),
  status               VARCHAR(16)  NOT NULL,
  issued_at            DATETIME     NOT NULL,
  last_used_at         DATETIME,
  expires_at           DATETIME     NOT NULL,
  revoked_at           DATETIME,
  revoked_reason       VARCHAR(64),
  ip_address           VARCHAR(45),
  user_agent           VARCHAR(512),
  created_at           DATETIME     NOT NULL,
  updated_at           DATETIME     NOT NULL,
  PRIMARY KEY (session_id),
  KEY ix_admin_session_user (admin_user_id),
  KEY ix_admin_session_token_family (token_family_id),
  KEY ix_admin_session_status (status)
) ENGINE=InnoDB;

-- MFA backup codes — matches AdminMfaBackupCode entity (@Table("admin_mfa_backup_codes"))
CREATE TABLE IF NOT EXISTS admin_mfa_backup_codes (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  admin_user_id   VARCHAR(64)  NOT NULL,
  hashed_code     VARCHAR(128) NOT NULL,
  used_at         DATETIME     DEFAULT NULL,
  created_at      DATETIME     NOT NULL,
  KEY ix_backup_codes_user (admin_user_id)
) ENGINE=InnoDB;

-- Auth & security settings (one row per tenant) — matches AuthSettings entity
-- (@Table(name = "admin_auth_settings"), tenant_id is the @Id).
CREATE TABLE IF NOT EXISTS admin_auth_settings (
  tenant_id         VARCHAR(64)  NOT NULL,
  providers_enabled VARCHAR(256),
  risk_rules_json   TEXT,
  allowed_origins   VARCHAR(1024),
  updated_at        DATETIME(6)  NOT NULL,
  updated_by        VARCHAR(64),
  PRIMARY KEY (tenant_id)
) ENGINE=InnoDB;

-- Roles
CREATE TABLE IF NOT EXISTS admin_role (
  id              VARCHAR(64)  NOT NULL,
  tenant_id       VARCHAR(64)  NOT NULL,
  name            VARCHAR(64)  NOT NULL,
  description     VARCHAR(256),
  permissions     JSON,
  created_at      DATETIME     NOT NULL,
  updated_at      DATETIME     NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_role (tenant_id, name)
) ENGINE=InnoDB;

-- ============================================================================
-- selfcare_core — Account, Entitlement, Product, Usage, Billing, Insurance
-- ============================================================================
USE selfcare_core;

-- Accounts
CREATE TABLE IF NOT EXISTS accounts (
  id              VARCHAR(64)  NOT NULL,
  tenant_id       VARCHAR(64)  NOT NULL,
  customer_id     VARCHAR(64)  NOT NULL,
  account_type    VARCHAR(32)  NOT NULL DEFAULT 'PREPAID',
  status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
  created_at      DATETIME     NOT NULL,
  updated_at      DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_tenant_customer (tenant_id, customer_id)
) ENGINE=InnoDB;

-- Connections (linked numbers)
CREATE TABLE IF NOT EXISTS connections (
  id              VARCHAR(64)  NOT NULL,
  tenant_id       VARCHAR(64)  NOT NULL,
  account_id      VARCHAR(64)  NOT NULL,
  msisdn          VARCHAR(32)  NOT NULL,
  connection_type VARCHAR(32)  NOT NULL DEFAULT 'MOBILE',
  status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
  is_primary      TINYINT(1)   DEFAULT 0,
  created_at      DATETIME     NOT NULL,
  updated_at      DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_account (tenant_id, account_id),
  KEY idx_msisdn (tenant_id, msisdn)
) ENGINE=InnoDB;

-- Products
CREATE TABLE IF NOT EXISTS products (
  id              VARCHAR(64)  NOT NULL,
  tenant_id       VARCHAR(64)  NOT NULL,
  operator_code   VARCHAR(128) NOT NULL,
  name            VARCHAR(256) NOT NULL,
  category        VARCHAR(64)  NOT NULL,
  description     TEXT,
  price           DECIMAL(18,4),
  currency        VARCHAR(8)   DEFAULT 'LKR',
  validity        VARCHAR(32),
  data_allowance  VARCHAR(64),
  voice_allowance VARCHAR(64),
  sms_allowance   VARCHAR(64),
  status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
  created_at      DATETIME     NOT NULL,
  updated_at      DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_tenant_category (tenant_id, category),
  UNIQUE KEY uk_operator_code (tenant_id, operator_code)
) ENGINE=InnoDB;

-- Usage records
CREATE TABLE IF NOT EXISTS usage_records (
  id              VARCHAR(64)  NOT NULL,
  tenant_id       VARCHAR(64)  NOT NULL,
  connection_id   VARCHAR(64)  NOT NULL,
  usage_type      VARCHAR(32)  NOT NULL,
  amount          DECIMAL(18,4) NOT NULL,
  unit            VARCHAR(16)  NOT NULL,
  recorded_at     DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_connection (tenant_id, connection_id, recorded_at)
) ENGINE=InnoDB;

-- Allowances
CREATE TABLE IF NOT EXISTS allowances (
  id              VARCHAR(64)  NOT NULL,
  tenant_id       VARCHAR(64)  NOT NULL,
  connection_id   VARCHAR(64)  NOT NULL,
  allowance_type  VARCHAR(32)  NOT NULL,
  total           DECIMAL(18,4) NOT NULL,
  used            DECIMAL(18,4) DEFAULT 0,
  remaining       DECIMAL(18,4) NOT NULL,
  unit            VARCHAR(16)  NOT NULL,
  expires_at      DATETIME,
  PRIMARY KEY (id),
  KEY idx_connection (tenant_id, connection_id)
) ENGINE=InnoDB;

-- Bills
CREATE TABLE IF NOT EXISTS bills (
  id              VARCHAR(64)  NOT NULL,
  tenant_id       VARCHAR(64)  NOT NULL,
  connection_id   VARCHAR(64)  NOT NULL,
  bill_date       DATE         NOT NULL,
  due_date        DATE         NOT NULL,
  total_amount    DECIMAL(18,4) NOT NULL,
  paid_amount     DECIMAL(18,4) DEFAULT 0,
  status          VARCHAR(32)  NOT NULL DEFAULT 'UNPAID',
  currency        VARCHAR(8)   DEFAULT 'LKR',
  created_at      DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_connection (tenant_id, connection_id, bill_date)
) ENGINE=InnoDB;

-- Bill items
CREATE TABLE IF NOT EXISTS bill_items (
  id              VARCHAR(64)  NOT NULL,
  tenant_id       VARCHAR(64)  NOT NULL,
  bill_id         VARCHAR(64)  NOT NULL,
  description     VARCHAR(256) NOT NULL,
  amount          DECIMAL(18,4) NOT NULL,
  category        VARCHAR(64),
  PRIMARY KEY (id),
  KEY idx_bill (tenant_id, bill_id)
) ENGINE=InnoDB;

-- ============================================================================
-- selfcare_payment — Payment transactions, idempotency, methods
-- ============================================================================
USE selfcare_payment;

-- Payment transactions
CREATE TABLE IF NOT EXISTS payment_transactions (
  id              VARCHAR(64)  NOT NULL,
  tenant_id       VARCHAR(64)  NOT NULL,
  connection_id   VARCHAR(64)  NOT NULL,
  amount          DECIMAL(18,4) NOT NULL,
  currency        VARCHAR(8)   NOT NULL DEFAULT 'LKR',
  channel         VARCHAR(32)  NOT NULL,
  method          VARCHAR(32)  NOT NULL,
  status          VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
  gateway_ref     VARCHAR(128),
  idempotency_key VARCHAR(64),
  created_at      DATETIME     NOT NULL,
  completed_at    DATETIME     DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_tenant_status (tenant_id, status, created_at),
  KEY idx_connection (tenant_id, connection_id, created_at),
  UNIQUE KEY uk_idempotency (tenant_id, idempotency_key)
) ENGINE=InnoDB;

-- Saved payment methods
CREATE TABLE IF NOT EXISTS payment_methods (
  id              VARCHAR(64)  NOT NULL,
  tenant_id       VARCHAR(64)  NOT NULL,
  connection_id   VARCHAR(64)  NOT NULL,
  method_type     VARCHAR(32)  NOT NULL,
  provider        VARCHAR(64)  NOT NULL,
  masked_details  VARCHAR(256) NOT NULL,
  is_default      TINYINT(1)   DEFAULT 0,
  status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
  created_at      DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_connection (tenant_id, connection_id)
) ENGINE=InnoDB;

-- Step-up auth requests
CREATE TABLE IF NOT EXISTS step_up_request (
  id              VARCHAR(64)  NOT NULL,
  tenant_id       VARCHAR(64)  NOT NULL,
  user_id_hash    VARCHAR(128) NOT NULL,
  reason          VARCHAR(64)  NOT NULL,
  otp_hash        VARCHAR(256) NOT NULL,
  expires_at      DATETIME     NOT NULL,
  attempts        INT          DEFAULT 0,
  status          VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
  created_at      DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_user (tenant_id, user_id_hash, status)
) ENGINE=InnoDB;

-- ============================================================================
-- selfcare_audit — Immutable audit trail
-- ============================================================================
USE selfcare_audit;

CREATE TABLE IF NOT EXISTS audit_events (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  event_id        VARCHAR(64)  NOT NULL,
  tenant_id       VARCHAR(64)  NOT NULL,
  actor_id_hash   VARCHAR(128),
  actor_type      VARCHAR(32),
  action          VARCHAR(64)  NOT NULL,
  target_type     VARCHAR(64),
  target_id       VARCHAR(128),
  correlation_id  VARCHAR(128),
  ip_address      VARCHAR(64),
  user_agent      VARCHAR(512),
  outcome         VARCHAR(32),
  metadata        TEXT,
  created_at      DATETIME     NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_event_id (event_id),
  KEY idx_tenant_action (tenant_id, action, created_at),
  KEY idx_target (tenant_id, target_type, target_id),
  KEY idx_correlation (correlation_id)
) ENGINE=InnoDB;

-- ============================================================================
-- selfcare_notification — Notifications, templates
-- ============================================================================
USE selfcare_notification;

CREATE TABLE IF NOT EXISTS notifications (
  id              VARCHAR(64)  NOT NULL,
  tenant_id       VARCHAR(64)  NOT NULL,
  user_id_hash    VARCHAR(128) NOT NULL,
  channel         VARCHAR(32)  NOT NULL,
  template_id     VARCHAR(64),
  subject         VARCHAR(256),
  body            TEXT         NOT NULL,
  status          VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
  sent_at         DATETIME     DEFAULT NULL,
  read_at         DATETIME     DEFAULT NULL,
  created_at      DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_user (tenant_id, user_id_hash, created_at),
  KEY idx_status (tenant_id, status)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS notification_template (
  id              VARCHAR(64)  NOT NULL,
  tenant_id       VARCHAR(64)  NOT NULL,
  name            VARCHAR(128) NOT NULL,
  channel         VARCHAR(32)  NOT NULL,
  subject_template VARCHAR(256),
  body_template   TEXT         NOT NULL,
  variables       JSON,
  status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
  created_at      DATETIME     NOT NULL,
  updated_at      DATETIME     NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_template (tenant_id, name, channel)
) ENGINE=InnoDB;

-- ============================================================================
-- selfcare_approval — Four-eyes approval workflow
-- ============================================================================
USE selfcare_approval;

CREATE TABLE IF NOT EXISTS approval_requests (
  id              VARCHAR(64)  NOT NULL,
  tenant_id       VARCHAR(64)  NOT NULL,
  action_type     VARCHAR(64)  NOT NULL,
  requested_by    VARCHAR(64)  NOT NULL,
  target_type     VARCHAR(64),
  target_id       VARCHAR(128),
  payload         JSON,
  status          VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
  decided_by      VARCHAR(64),
  decided_at      DATETIME     DEFAULT NULL,
  decision_comment TEXT,
  expires_at      DATETIME     NOT NULL,
  created_at      DATETIME     NOT NULL,
  updated_at      DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_tenant_status (tenant_id, status),
  KEY idx_expires (expires_at)
) ENGINE=InnoDB;

-- ============================================================================
-- selfcare_reporting — Report definitions and executions
-- ============================================================================
USE selfcare_reporting;

CREATE TABLE IF NOT EXISTS report_definitions (
  id              VARCHAR(64)  NOT NULL,
  tenant_id       VARCHAR(64)  NOT NULL,
  name            VARCHAR(128) NOT NULL,
  description     TEXT,
  category        VARCHAR(64),
  query_template  TEXT         NOT NULL,
  parameters      JSON,
  columns         JSON,
  schedule_cron   VARCHAR(64),
  status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
  created_at      DATETIME     NOT NULL,
  updated_at      DATETIME     NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_report (tenant_id, name)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS report_executions (
  id              VARCHAR(64)  NOT NULL,
  tenant_id       VARCHAR(64)  NOT NULL,
  report_id       VARCHAR(64)  NOT NULL,
  status          VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
  parameters      JSON,
  result_url      VARCHAR(512),
  row_count       INT          DEFAULT 0,
  started_at      DATETIME     DEFAULT NULL,
  completed_at    DATETIME     DEFAULT NULL,
  error_message   TEXT,
  created_at      DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_report (tenant_id, report_id, created_at)
) ENGINE=InnoDB;

-- ============================================================================
-- Seed default admin user for Dialog
-- ============================================================================
USE selfcare_admin;

INSERT IGNORE INTO admin_users (id, tenant_id, email, password_hash, full_name, role, status, mfa_enabled, created_at, updated_at)
VALUES (
  'admin-dialog-001',
  'dialog-lk',
  'admin@selfcare.lk',
  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
  'Dialog Admin',
  'SUPER_ADMIN',
  'ACTIVE',
  0,
  NOW(),
  NOW()
);

INSERT IGNORE INTO admin_role (id, tenant_id, name, description, permissions, created_at, updated_at)
VALUES (
  'role-super-admin',
  'dialog-lk',
  'SUPER_ADMIN',
  'Full platform access',
  '["*"]',
  NOW(),
  NOW()
);

INSERT IGNORE INTO admin_role (id, tenant_id, name, description, permissions, created_at, updated_at)
VALUES (
  'role-viewer',
  'dialog-lk',
  'VIEWER',
  'Read-only access',
  '["read:tenants", "read:themes", "read:layouts", "read:features", "read:audit"]',
  NOW(),
  NOW()
);

-- ============================================================================
-- Seed default notification templates for Dialog
-- ============================================================================
USE selfcare_notification;

INSERT IGNORE INTO notification_template (id, tenant_id, name, channel, subject_template, body_template, variables, status, created_at, updated_at)
VALUES
  ('ntpl-bill-due', 'dialog-lk', 'bill-due', 'SMS', NULL, 'Dear customer, your bill of LKR {{amount}} is due on {{dueDate}}. Pay now via MyDialog app.', '["amount", "dueDate"]', 'ACTIVE', NOW(), NOW()),
  ('ntpl-payment-success', 'dialog-lk', 'payment-success', 'SMS', NULL, 'Payment of LKR {{amount}} received. Ref: {{ref}}. Thank you.', '["amount", "ref"]', 'ACTIVE', NOW(), NOW()),
  ('ntpl-otp', 'dialog-lk', 'otp-verification', 'SMS', NULL, 'Your verification code is {{otp}}. Valid for {{minutes}} minutes. Do not share.', '["otp", "minutes"]', 'ACTIVE', NOW(), NOW()),
  ('ntpl-pack-activated', 'dialog-lk', 'pack-activated', 'SMS', NULL, '{{packName}} activated successfully. Valid until {{expiry}}.', '["packName", "expiry"]', 'ACTIVE', NOW(), NOW()),
  ('ntpl-low-balance', 'dialog-lk', 'low-balance', 'SMS', NULL, 'Your balance is LKR {{balance}}. Recharge now to stay connected.', '["balance"]', 'ACTIVE', NOW(), NOW()),
  ('ntpl-data-usage-80', 'dialog-lk', 'data-usage-80', 'PUSH', 'Data Usage Alert', 'You have used {{used}} of {{total}} data. {{remaining}} remaining.', '["used", "total", "remaining"]', 'ACTIVE', NOW(), NOW()),
  ('ntpl-welcome', 'dialog-lk', 'welcome', 'SMS', NULL, 'Welcome to MyDialog! Your account is ready. Explore plans, pay bills, and more.', '[]', 'ACTIVE', NOW(), NOW());

-- === MySQL seed complete ===
--   Databases: selfcare_identity, selfcare_admin, selfcare_core,
--   selfcare_payment, selfcare_audit, selfcare_notification,
--   selfcare_approval, selfcare_reporting
--   Default admin: admin@selfcare.lk (password: Selfcare_Adm1n_P0rt4l_Pa55w0rd!2026)
--   Notification templates: 7 seeded

-- MySQL init for OMOBIO identity / audit / payment schemas.
-- Runs ONCE on first container start via /docker-entrypoint-initdb.d/

CREATE DATABASE IF NOT EXISTS omobio_identity CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS omobio_audit CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS omobio_payment CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Grant rights to the application user
GRANT ALL PRIVILEGES ON omobio_identity.* TO 'omobio'@'%';
GRANT ALL PRIVILEGES ON omobio_audit.* TO 'omobio'@'%';
GRANT ALL PRIVILEGES ON omobio_payment.* TO 'omobio'@'%';
FLUSH PRIVILEGES;

USE omobio_identity;

-- Customer sessions
CREATE TABLE IF NOT EXISTS customer_session (
  id              VARCHAR(64)  NOT NULL,
  tenant_id       VARCHAR(64)  NOT NULL,
  user_id_hash    VARCHAR(128) NOT NULL,
  connection_id   VARCHAR(64)  NOT NULL,
  refresh_token   VARCHAR(512) NOT NULL,
  access_token    VARCHAR(4096) NOT NULL,
  created_at      DATETIME     NOT NULL,
  expires_at      DATETIME     NOT NULL,
  refresh_expires_at DATETIME NOT NULL,
  ip_address      VARCHAR(64),
  user_agent      VARCHAR(512),
  status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
  PRIMARY KEY (id),
  KEY idx_tenant_user (tenant_id, user_id_hash),
  KEY idx_refresh (refresh_token(128)),
  KEY idx_expires (expires_at)
) ENGINE=InnoDB;

-- GDPR consent records
CREATE TABLE IF NOT EXISTS consent_record (
  id              VARCHAR(64)  NOT NULL,
  tenant_id       VARCHAR(64)  NOT NULL,
  user_id_hash    VARCHAR(128) NOT NULL,
  purpose         VARCHAR(64)  NOT NULL,
  version         VARCHAR(32)  NOT NULL,
  granted         TINYINT(1)   NOT NULL,
  source          VARCHAR(64)  NOT NULL,
  ip_address      VARCHAR(64),
  user_agent      VARCHAR(512),
  captured_at     DATETIME     NOT NULL,
  superseded_at   DATETIME     DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_active (tenant_id, user_id_hash, purpose, superseded_at)
) ENGINE=InnoDB;

-- Data erasure requests
CREATE TABLE IF NOT EXISTS data_erasure_request (
  id              VARCHAR(64)  NOT NULL,
  tenant_id       VARCHAR(64)  NOT NULL,
  user_id_hash    VARCHAR(128) NOT NULL,
  status          VARCHAR(32)  NOT NULL,
  reason          VARCHAR(256),
  requested_by    VARCHAR(64)  NOT NULL,
  ip_address      VARCHAR(64),
  user_agent      VARCHAR(512),
  requested_at    DATETIME     NOT NULL,
  started_at      DATETIME     DEFAULT NULL,
  completed_at    DATETIME     DEFAULT NULL,
  failure_reason  VARCHAR(512),
  notified_services TEXT,
  PRIMARY KEY (id),
  KEY idx_user_status (tenant_id, user_id_hash, status)
) ENGINE=InnoDB;

-- OTP one-time password
CREATE TABLE IF NOT EXISTS otp_record (
  id              VARCHAR(64)  NOT NULL,
  tenant_id       VARCHAR(64)  NOT NULL,
  channel         VARCHAR(32)  NOT NULL,
  recipient       VARCHAR(64)  NOT NULL,
  otp_hash        VARCHAR(256) NOT NULL,
  created_at      DATETIME     NOT NULL,
  expires_at      DATETIME     NOT NULL,
  attempts        INT          DEFAULT 0,
  status          VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
  PRIMARY KEY (id),
  KEY idx_otp_lookup (tenant_id, channel, recipient, status)
) ENGINE=InnoDB;

USE omobio_audit;

CREATE TABLE IF NOT EXISTS audit_event (
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
  KEY idx_tenant_action (tenant_id, action, created_at),
  KEY idx_target (tenant_id, target_type, target_id),
  KEY idx_correlation (correlation_id)
) ENGINE=InnoDB;

USE omobio_payment;

-- Idempotency keys for payment operations
CREATE TABLE IF NOT EXISTS idempotency_key (
  id              VARCHAR(64)  NOT NULL,
  tenant_id       VARCHAR(64)  NOT NULL,
  endpoint        VARCHAR(128) NOT NULL,
  request_hash    VARCHAR(128) NOT NULL,
  response_status INT,
  response_body   TEXT,
  created_at      DATETIME     NOT NULL,
  expires_at      DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_lookup (tenant_id, endpoint, request_hash, expires_at)
) ENGINE=InnoDB;

-- Payment records
CREATE TABLE IF NOT EXISTS payment_record (
  id              VARCHAR(64)  NOT NULL,
  tenant_id       VARCHAR(64)  NOT NULL,
  connection_id   VARCHAR(64)  NOT NULL,
  amount          DECIMAL(18,4) NOT NULL,
  currency        VARCHAR(8)   NOT NULL,
  channel         VARCHAR(32)  NOT NULL,
  status          VARCHAR(32)  NOT NULL,
  gateway_ref     VARCHAR(128),
  idempotency_key VARCHAR(64),
  created_at      DATETIME     NOT NULL,
  completed_at    DATETIME     DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_tenant_status (tenant_id, status, created_at),
  KEY idx_connection (tenant_id, connection_id, created_at)
) ENGINE=InnoDB;

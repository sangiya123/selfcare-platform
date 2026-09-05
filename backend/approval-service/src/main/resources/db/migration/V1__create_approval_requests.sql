-- V1__create_approval_requests.sql
-- OMOBIO Selfcare Platform — approval-service
-- Table: approval_requests — durable record of four-eyes approval requests
-- for high-risk admin actions.

CREATE TABLE IF NOT EXISTS approval_requests (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id          VARCHAR(64)  NOT NULL,
    tenant_id           VARCHAR(32)  NOT NULL,
    action              VARCHAR(64)  NOT NULL,
    resource_type       VARCHAR(64)  NOT NULL,
    resource_id         VARCHAR(64)  NOT NULL,
    requester_id        VARCHAR(64)  NOT NULL,
    requester_email     VARCHAR(256) NOT NULL,
    change_snapshot     TEXT         NULL,
    approver_id         VARCHAR(64)  NULL,
    approver_email      VARCHAR(256) NULL,
    status              VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    comments            TEXT         NULL,
    expires_at          DATETIME(6)  NULL,
    decided_at          DATETIME(6)  NULL,
    created_at          DATETIME(6)  NOT NULL,
    correlation_id      VARCHAR(64)  NULL,
    ticket_reference    VARCHAR(128) NULL,

    CONSTRAINT chk_approval_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'EXPIRED')),

    UNIQUE INDEX uk_approval_request_id (request_id),
    INDEX ix_approval_status (status),
    INDEX ix_approval_action (action),
    INDEX ix_approval_tenant (tenant_id),
    INDEX ix_approval_created (created_at),
    INDEX ix_approval_tenant_status (tenant_id, status),
    INDEX ix_approval_requester (requester_id),
    INDEX ix_approval_approver (approver_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

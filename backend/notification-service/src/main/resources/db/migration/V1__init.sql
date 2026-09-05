-- V1__init.sql
-- OMOBIO Selfcare Platform — notification-service
-- Tables: notifications
--
-- NotificationTemplate (MongoDB) and Notification (MySQL).
-- Notification templates live in MongoDB for flexibility.
-- Notification audit log lives in MySQL for durability and audit.

-- ============================================================================
-- notifications  — durable send log
-- ============================================================================
CREATE TABLE IF NOT EXISTS notifications (
    notification_id      VARCHAR(64)   NOT NULL,
    tenant_id           VARCHAR(32)   NOT NULL,
    user_id             VARCHAR(64)   NULL,
    channel             VARCHAR(16)   NOT NULL,
    template_id         VARCHAR(64)   NULL,
    recipient           VARCHAR(256)  NULL,
    subject             VARCHAR(256)  NULL,
    body                TEXT          NULL,
    status              VARCHAR(16)   NOT NULL,
    provider_message_id  VARCHAR(128)  NULL,
    failure_reason      VARCHAR(256)  NULL,
    metadata            JSON          NULL,
    locale              VARCHAR(8)    NULL,
    sent_at             DATETIME(6)   NULL,
    delivered_at        DATETIME(6)   NULL,
    created_at          DATETIME(6)   NOT NULL,

    PRIMARY KEY (notification_id),

    CONSTRAINT chk_notif_channel
        CHECK (channel IN ('PUSH', 'SMS', 'EMAIL', 'IN_APP')),
    CONSTRAINT chk_notif_status
        CHECK (status IN ('QUEUED', 'SENT', 'DELIVERED', 'FAILED', 'OPTED_OUT')),

    INDEX ix_notif_tenant_user (tenant_id, user_id),
    INDEX ix_notif_status (status),
    INDEX ix_notif_created (created_at),
    INDEX ix_notif_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

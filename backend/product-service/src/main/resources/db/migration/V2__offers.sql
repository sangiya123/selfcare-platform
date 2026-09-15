-- V2__offers.sql
-- Selfcare Platform — product-service
-- Table: offers
--
-- A specific deal on a Product for a target segment or for a particular window.
-- Multiple offers can attach to the same product.

CREATE TABLE IF NOT EXISTS offers (
    offer_id          VARCHAR(64)   NOT NULL,
    tenant_id         VARCHAR(32)   NOT NULL,
    product_id        VARCHAR(64)   NOT NULL,

    name              VARCHAR(128)  NULL,
    description       VARCHAR(512)  NULL,

    -- Type: DISCOUNT, BONUS_DATA, FREE_MINUTES, BUNDLE, CASHBACK
    offer_type        VARCHAR(32)   NULL,
    value             DECIMAL(19, 4) NULL,
    currency          VARCHAR(8)    NULL,

    -- Targeting
    target_segment    VARCHAR(32)   NULL,
    eligible_connection_type VARCHAR(32) NULL,
    tags              JSON          NULL,

    -- Lifecycle
    status            VARCHAR(16)   NOT NULL DEFAULT 'DRAFT',
    priority          INT           NOT NULL DEFAULT 100,
    valid_from        DATETIME(6)   NULL,
    valid_until       DATETIME(6)   NULL,
    max_purchases_per_customer INT  NULL,
    redemption_count  BIGINT        NOT NULL DEFAULT 0,

    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,

    PRIMARY KEY (offer_id),

    CONSTRAINT chk_offer_status
        CHECK (status IN ('DRAFT', 'ACTIVE', 'PAUSED', 'EXPIRED', 'ARCHIVED')),
    CONSTRAINT chk_offer_priority CHECK (priority >= 0),
    CONSTRAINT chk_offer_redemption CHECK (redemption_count >= 0),

    INDEX ix_offer_tenant (tenant_id),
    INDEX ix_offer_product (product_id),
    INDEX ix_offer_status_window (status, valid_from, valid_until),
    INDEX ix_offer_segment (target_segment)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

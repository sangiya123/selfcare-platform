-- V1__init.sql
-- OMOBIO Selfcare Platform — product-service
-- Tables: products, product_tags
--
-- product-service runs on MySQL (JPA) and uses the products table as the
-- canonical, materialized read model of operator catalogs. Tags are kept in
-- a side table (ElementCollection mapping on com.omobio.product.domain.Product).

-- ============================================================================
-- products
-- ============================================================================
CREATE TABLE IF NOT EXISTS products (
    product_id          VARCHAR(64)    NOT NULL,
    tenant_id           VARCHAR(32)    NOT NULL,
    source_product_id   VARCHAR(128)   NULL,
    source_system       VARCHAR(32)    NULL, -- DIALOG_MIFE, HUTCH_BSS, AIRTEL_CATALOG
    name                VARCHAR(256)   NOT NULL,
    description         VARCHAR(2048)  NULL,
    category            VARCHAR(64)    NOT NULL, -- DATA_PACK, VOICE_PACK, SMS_PACK, ROAMING, VAS
    subcategory         VARCHAR(64)    NULL,
    lob                 VARCHAR(16)    NOT NULL, -- MOBILE, BB, DTV, FIBRE
    connection_type     VARCHAR(16)    NULL,    -- prepaid, postpaid, both
    price               DECIMAL(19, 4) NULL,
    currency            VARCHAR(8)     NULL,
    validity_days       INT            NULL,
    allowances          JSON           NULL,    -- { data, voice, sms, ... }
    terms               TEXT           NULL,
    image_url           VARCHAR(512)   NULL,
    badge               VARCHAR(32)    NULL,    -- POPULAR, NEW, RECOMMENDED, LIMITED_TIME
    display_order       INT            NULL,
    status              VARCHAR(16)    NOT NULL, -- ACTIVE, INACTIVE, ARCHIVED
    source_priority     INT            NULL,    -- lower = higher priority for dedup
    created_at          DATETIME(6)    NOT NULL,
    updated_at          DATETIME(6)    NOT NULL,
    opt_lock_version    BIGINT         NULL,

    PRIMARY KEY (product_id),

    CONSTRAINT chk_product_lob
        CHECK (lob IN ('MOBILE', 'BB', 'DTV', 'FIBRE')),
    CONSTRAINT chk_product_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT chk_product_price_nonneg
        CHECK (price IS NULL OR price >= 0),
    CONSTRAINT chk_product_validity_pos
        CHECK (validity_days IS NULL OR validity_days > 0),
    CONSTRAINT chk_product_source_priority_range
        CHECK (source_priority IS NULL
               OR (source_priority BETWEEN 0 AND 9999)),

    INDEX ix_product_tenant (tenant_id),
    INDEX ix_product_category (category),
    INDEX ix_product_lob (lob),
    INDEX ix_product_status (status),
    INDEX ix_product_tenant_status_category
        (tenant_id, status, category),
    INDEX ix_product_tenant_source
        (tenant_id, source_system, source_product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- product_tags
-- ============================================================================
-- ElementCollection table for Product.tags. Composite PK keeps the natural
-- uniqueness of (product_id, tag).
CREATE TABLE IF NOT EXISTS product_tags (
    product_id   VARCHAR(64) NOT NULL,
    tag          VARCHAR(128) NOT NULL,

    PRIMARY KEY (product_id, tag),

    CONSTRAINT fk_product_tags_product
        FOREIGN KEY (product_id) REFERENCES products (product_id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    INDEX ix_product_tags_tag (tag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

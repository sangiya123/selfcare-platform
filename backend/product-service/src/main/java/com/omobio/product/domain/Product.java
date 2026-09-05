package com.omobio.product.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Canonical Product entity — represents an offer in the platform's normalized form.
 *
 * Per ADR: separate catalog from customer eligibility, pricing, promotion and ranking.
 * Product = the catalog item. Pricing, eligibility, promotion are derived.
 *
 * Source of truth: materialized from operator-specific catalog systems via adapters.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "products", indexes = {
    @Index(name = "ix_product_tenant", columnList = "tenant_id"),
    @Index(name = "ix_product_category", columnList = "category"),
    @Index(name = "ix_product_lob", columnList = "lob"),
    @Index(name = "ix_product_status", columnList = "status")
})
@EntityListeners(AuditingEntityListener.class)
public class Product {

    @Id
    @Column(name = "product_id", length = 64)
    private String productId;

    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId;

    /** External source product ID (operator-specific) */
    @Column(name = "source_product_id", length = 128)
    private String sourceProductId;

    /** Source system: DIALOG_MIFE, HUTCH_BSS, AIRTEL_CATALOG, etc. */
    @Column(name = "source_system", length = 32)
    private String sourceSystem;

    @Column(name = "name", nullable = false, length = 256)
    private String name;

    @Column(name = "description", length = 2048)
    private String description;

    @Column(name = "category", nullable = false, length = 64)
    private String category; // DATA_PACK, VOICE_PACK, SMS_PACK, ROAMING, VAS, etc.

    @Column(name = "subcategory", length = 64)
    private String subcategory;

    /** LOB: MOBILE, BB, DTV, FIBRE */
    @Column(name = "lob", nullable = false, length = 16)
    private String lob;

    @Column(name = "connection_type", length = 16)
    private String connectionType; // prepaid, postpaid, both

    @Column(name = "price", precision = 19, scale = 4)
    private BigDecimal price;

    @Column(name = "currency", length = 8)
    private String currency;

    /** Validity in days */
    @Column(name = "validity_days")
    private Integer validityDays;

    /** Allowances as JSON: data, voice, sms, etc. */
    @Column(name = "allowances", columnDefinition = "JSON")
    private String allowances;

    /** Terms and conditions */
    @Column(name = "terms", columnDefinition = "TEXT")
    private String terms;

    @Column(name = "image_url", length = 512)
    private String imageUrl;

    @Column(name = "badge", length = 32)
    private String badge; // POPULAR, NEW, RECOMMENDED, LIMITED_TIME

    /** Display order within category */
    @Column(name = "display_order")
    private Integer displayOrder;

    /** Status: ACTIVE, INACTIVE, ARCHIVED */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** Tags for filtering */
    @ElementCollection
    @CollectionTable(name = "product_tags", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "tag")
    private List<String> tags;

    /** Source priority (lower = higher priority for dedup) */
    @Column(name = "source_priority")
    private Integer sourcePriority;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "opt_lock_version")
    private Long optLockVersion;
}
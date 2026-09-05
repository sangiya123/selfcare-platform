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
 * Canonical Offer entity — a specific deal on a Product for a target
 * segment or for a particular window. Multiple offers can attach to
 * the same product (e.g. "10% off this plan for new postpaid customers").
 *
 * Per ADR-009: configuration is declarative. Offers are stored as data
 * and the recommendation engine reasons about them in code, not via
 * custom expressions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "offers", indexes = {
    @Index(name = "ix_offer_tenant", columnList = "tenant_id"),
    @Index(name = "ix_offer_product", columnList = "product_id"),
    @Index(name = "ix_offer_status_window", columnList = "status, valid_from, valid_until"),
    @Index(name = "ix_offer_segment", columnList = "target_segment")
})
@EntityListeners(AuditingEntityListener.class)
public class Offer {

    @Id
    @Column(name = "offer_id", length = 64)
    private String offerId;

    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId;

    @Column(name = "product_id", nullable = false, length = 64)
    private String productId;

    @Column(name = "name", length = 128)
    private String name;

    @Column(name = "description", length = 512)
    private String description;

    /** Type: DISCOUNT, BONUS_DATA, FREE_MINUTES, BUNDLE, CASHBACK */
    @Column(name = "offer_type", length = 32)
    private String offerType;

    /** Percentage discount, bonus quantity, etc. */
    @Column(name = "value", precision = 19, scale = 4)
    private BigDecimal value;

    @Column(name = "currency", length = 8)
    private String currency;

    /** Target segment: ALL, NEW_CUSTOMER, LOYAL, POSTPAID, PREPAID, STUDENT, etc. */
    @Column(name = "target_segment", length = 32)
    private String targetSegment;

    /** Eligible connection types: POSTPAID, PREPAID, ALL */
    @Column(name = "eligible_connection_type", length = 32)
    private String eligibleConnectionType;

    /** Tags for finer-grained targeting */
    @Column(name = "tags", columnDefinition = "JSON")
    private String tags;

    /** Status: DRAFT, ACTIVE, PAUSED, EXPIRED, ARCHIVED */
    @Column(name = "status", length = 16)
    private String status;

    @Column(name = "priority", nullable = false)
    @Builder.Default
    private Integer priority = 100;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_until")
    private Instant validUntil;

    /** Per-customer purchase limit */
    @Column(name = "max_purchases_per_customer")
    private Integer maxPurchasesPerCustomer;

    /** How many times this offer has been redeemed (analytics) */
    @Column(name = "redemption_count", nullable = false)
    @Builder.Default
    private Long redemptionCount = 0L;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isCurrentlyActive() {
        if (!"ACTIVE".equals(status)) return false;
        Instant now = Instant.now();
        if (validFrom != null && now.isBefore(validFrom)) return false;
        if (validUntil != null && now.isAfter(validUntil)) return false;
        return true;
    }
}

package com.selfcare.usage.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Per-connection allowance bucket — a logical allowance such as
 * "Weekend Data 5GB" or "Night Voice Unlimited".
 *
 * Allowances are written by:
 *  - the daily usage-rollup job (which creates the next cycle's bucket),
 *  - recharge/package purchase events (Kafka),
 *  - admin overrides via Selfcare Studio.
 *
 * They are read by:
 *  - dashboard widget for remaining-allowance progress bars,
 *  - the AI recommendation engine (suggesting recharges when low).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "allowances", indexes = {
    @Index(name = "ix_allowance_tenant_conn", columnList = "tenant_id, connection_id"),
    @Index(name = "ix_allowance_tenant_conn_active", columnList = "tenant_id, connection_id, status"),
    @Index(name = "ix_allowance_expires", columnList = "expires_at")
})
@EntityListeners(AuditingEntityListener.class)
public class Allowance {

    @Id
    @Column(name = "allowance_id", length = 64)
    private String allowanceId;

    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId;

    @Column(name = "connection_id", nullable = false, length = 64)
    private String connectionId;

    /** Type: DATA, VOICE, SMS, COMBO, BUNDLE */
    @Column(name = "allowance_type", nullable = false, length = 16)
    private String allowanceType;

    /** Human label e.g. "Weekend Data 5GB" */
    @Column(name = "name", length = 128)
    private String name;

    /** Source: PLAN, RECHARGE, PROMOTIONAL, BONUS, ADMIN_GRANT */
    @Column(name = "source", length = 32)
    private String source;

    @Column(name = "total_units", nullable = false)
    private Long totalUnits;

    @Column(name = "used_units", nullable = false)
    private Long usedUnits;

    @Column(name = "remaining_units", nullable = false)
    private Long remainingUnits;

    /** Unit depends on type: bytes, seconds, count */
    @Column(name = "unit", length = 16)
    private String unit;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    /** Status: ACTIVE, EXHAUSTED, EXPIRED, CANCELLED */
    @Column(name = "status", length = 16)
    private String status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public long percentUsed() {
        if (totalUnits == null || totalUnits == 0) return 0L;
        if (usedUnits == null) return 0L;
        return Math.min(100L, (usedUnits * 100L) / totalUnits);
    }
}

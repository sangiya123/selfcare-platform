package com.omobio.billing.domain;

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

/**
 * Per-tenant billing configuration.
 *
 * Stored in the platform-config collection / table so admins can tune
 * late-fee, due-date, and tax rules per client without code changes.
 *
 * ADR-009: configuration cannot execute arbitrary code; this is
 * declarative config the billing-service reads.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "billing_cycle_configs")
@EntityListeners(AuditingEntityListener.class)
public class BillingCycleConfig {

    @Id
    @Column(name = "tenant_id", length = 32)
    private String tenantId;

    /** Day of month bills are issued (1-28) */
    @Column(name = "bill_issue_day")
    private Integer billIssueDay;

    /** Days from issue until due */
    @Column(name = "payment_due_days", nullable = false)
    @Builder.Default
    private Integer paymentDueDays = 14;

    /** Late fee percentage applied to overdue bills (e.g. 2.0 for 2%) */
    @Column(name = "late_fee_pct", precision = 8, scale = 4)
    @Builder.Default
    private BigDecimal lateFeePct = new BigDecimal("2.0");

    /** Cap on late fee as absolute amount in tenant currency (e.g. 500.00) */
    @Column(name = "late_fee_cap", precision = 19, scale = 4)
    private BigDecimal lateFeeCap;

    /** Default tax rate (e.g. 18.0 for 18%) */
    @Column(name = "default_tax_pct", precision = 8, scale = 4)
    private BigDecimal defaultTaxPct;

    /** Currency code (e.g. LKR, USD) */
    @Column(name = "currency", length = 8)
    private String currency;

    /** Grace period in days — late fee only applied after this */
    @Column(name = "grace_period_days")
    @Builder.Default
    private Integer gracePeriodDays = 3;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

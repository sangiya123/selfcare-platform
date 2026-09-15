package com.selfcare.billing.domain;

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
import java.time.LocalDate;

/**
 * Canonical Bill entity.
 *
 * Represents a bill/invoice for a connection, regardless of operator.
 * Status lifecycle: DRAFT -> ISSUED -> DUE -> PARTIALLY_PAID -> PAID / OVERDUE / CANCELLED
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bills", indexes = {
    @Index(name = "ix_bill_tenant_connection", columnList = "tenant_id, connection_id"),
    @Index(name = "ix_bill_status", columnList = "status"),
    @Index(name = "ix_bill_due_date", columnList = "due_date")
})
@EntityListeners(AuditingEntityListener.class)
public class Bill {

    @Id
    @Column(name = "bill_id", length = 64)
    private String billId;

    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId;

    @Column(name = "account_id", length = 64)
    private String accountId;

    @Column(name = "connection_id", length = 64)
    private String connectionId;

    /** Operator's source bill ID */
    @Column(name = "source_bill_id", length = 128)
    private String sourceBillId;

    /** Bill number for display */
    @Column(name = "bill_number", length = 64)
    private String billNumber;

    /** Bill type: POSTPAID, PREPAID_HISTORY, RECURRING */
    @Column(name = "bill_type", length = 32)
    private String billType;

    @Column(name = "billing_period_start")
    private LocalDate billingPeriodStart;

    @Column(name = "billing_period_end")
    private LocalDate billingPeriodEnd;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "total_amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "paid_amount", precision = 19, scale = 4)
    private BigDecimal paidAmount;

    @Column(name = "outstanding_amount", precision = 19, scale = 4)
    private BigDecimal outstandingAmount;

    @Column(name = "currency", length = 8)
    private String currency;

    /** Status: DRAFT, ISSUED, DUE, PARTIALLY_PAID, PAID, OVERDUE, CANCELLED */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "pdf_url", length = 512)
    private String pdfUrl;

    @Column(name = "line_items", columnDefinition = "JSON")
    private String lineItems;

    @Column(name = "tax_breakdown", columnDefinition = "JSON")
    private String taxBreakdown;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "opt_lock_version")
    private Long optLockVersion;

    /**
     * Calculate outstanding amount.
     */
    public void calculateOutstanding() {
        if (totalAmount != null && paidAmount != null) {
            this.outstandingAmount = totalAmount.subtract(paidAmount);
        }
    }
}
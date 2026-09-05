package com.omobio.billing.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Canonical Bill line item — a single chargeable component of a bill.
 *
 * Example rows for one bill:
 *  - LINE_RENT      1500.00
 *  - VOICE_OVERAGE   250.00
 *  - DATA_OVERAGE    120.00
 *  - DISCOUNT_PROMO  -100.00
 *  - TAX             221.00
 *  - LATE_FEE         50.00
 *
 * Storing line items as rows (rather than JSON in Bill) lets the dashboard
 * show a proper itemized invoice and lets the recommendation engine
 * reason about spending patterns.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bill_items", indexes = {
    @Index(name = "ix_bill_item_tenant_bill", columnList = "tenant_id, bill_id"),
    @Index(name = "ix_bill_item_category", columnList = "category"),
    @Index(name = "ix_bill_item_tenant_conn_period", columnList = "tenant_id, connection_id, period_start")
})
@EntityListeners(AuditingEntityListener.class)
public class BillItem {

    @Id
    @Column(name = "bill_item_id", length = 64)
    private String billItemId;

    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId;

    @Column(name = "bill_id", nullable = false, length = 64)
    private String billId;

    @Column(name = "connection_id", length = 64)
    private String connectionId;

    /** Type: CHARGE, DISCOUNT, TAX, FEE, ADJUSTMENT */
    @Column(name = "item_type", nullable = false, length = 16)
    private String itemType;

    /** Category: LINE_RENT, VOICE, DATA, SMS, ROAMING, VAS, DEVICE, PROMO, TAX, LATE_FEE, OTHER */
    @Column(name = "category", nullable = false, length = 32)
    private String category;

    @Column(name = "description", length = 256)
    private String description;

    @Column(name = "quantity", precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_price", precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "tax_rate", precision = 8, scale = 4)
    private BigDecimal taxRate;

    @Column(name = "tax_amount", precision = 19, scale = 4)
    private BigDecimal taxAmount;

    /** Sub-code from operator for analytics: "MONTHLY_PLAN_FEE" etc. */
    @Column(name = "source_code", length = 64)
    private String sourceCode;

    @Column(name = "period_start")
    private Instant periodStart;

    @Column(name = "period_end")
    private Instant periodEnd;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}

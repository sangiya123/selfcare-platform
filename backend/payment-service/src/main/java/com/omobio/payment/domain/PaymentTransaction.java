package com.omobio.payment.domain;

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
 * Payment transaction — durable record of a payment attempt.
 *
 * State machine:
 *   PENDING -> SUCCESS / FAILED / UNKNOWN
 *   UNKNOWN -> SUCCESS / FAILED (after reconciliation)
 *   SUCCESS -> REVERSED (refund)
 *
 * Idempotency: every write must include a client-supplied idempotency key.
 * Same key + same payload = same transaction (no double charge).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "payment_transactions", indexes = {
    @Index(name = "ix_payment_tenant_user", columnList = "tenant_id, user_id"),
    @Index(name = "ix_payment_idempotency", columnList = "idempotency_key", unique = true),
    @Index(name = "ix_payment_status", columnList = "status"),
    @Index(name = "ix_payment_created", columnList = "created_at")
})
@EntityListeners(AuditingEntityListener.class)
public class PaymentTransaction {

    @Id
    @Column(name = "transaction_id", length = 64)
    private String transactionId;

    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    /** Idempotency key from client. Must be unique per logical operation. */
    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    /** Type: RECHARGE, BILL_PAYMENT, PACKAGE_PURCHASE, TRANSFER */
    @Column(name = "transaction_type", nullable = false, length = 32)
    private String transactionType;

    /** Source connection (the paying connection, may be the actor or a saved payment method) */
    @Column(name = "source_connection_id", length = 64)
    private String sourceConnectionId;

    /** Target connection (the connection being paid for / recharged) */
    @Column(name = "target_connection_id", length = 64)
    private String targetConnectionId;

    /** Bill ID (for bill payments) */
    @Column(name = "bill_id", length = 64)
    private String billId;

    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency;

    @Column(name = "fee", precision = 19, scale = 4)
    private BigDecimal fee;

    /** Payment method: CARD, WALLET, BANK_TRANSFER, MOBILE_MONEY, SAVED */
    @Column(name = "payment_method", length = 32)
    private String paymentMethod;

    /** Tokenized reference (NOT raw PAN) */
    @Column(name = "payment_token", length = 128)
    private String paymentToken;

    /** Provider: DIALOG_MIFE, HUTCH_BSS, AIRTEL_GATEWAY, STRIPE, etc. */
    @Column(name = "provider", length = 32)
    private String provider;

    @Column(name = "provider_reference", length = 128)
    private String providerReference;

    /** Status: PENDING, SUCCESS, FAILED, UNKNOWN, REVERSED */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "failure_reason", length = 256)
    private String failureReason;

    @Column(name = "metadata", columnDefinition = "JSON")
    private String metadata;

    @Column(name = "receipt_url", length = 512)
    private String receiptUrl;

    @Column(name = "step_up_required", nullable = false)
    private Boolean stepUpRequired;

    @Column(name = "step_up_completed", nullable = false)
    private Boolean stepUpCompleted;

    /** Number of retry attempts */
    @Column(name = "retry_count")
    private Integer retryCount;

    /** Last retry timestamp */
    @Column(name = "last_retry_at")
    private Instant lastRetryAt;

    @Column(name = "completed_at")
    private Instant completedAt;

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
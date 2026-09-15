package com.selfcare.payment.domain;

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
 * Saved payment method — tokenized, never stores raw PAN or full account details.
 *
 * Security:
 * - PaymentMethod.id is server-generated UUID
 * - paymentToken is opaque (issued by PSP, references vault record)
 * - lastFourDigits only — for display
 * - CVV never stored
 * - Card expiry stored for validity checks
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "payment_methods", indexes = {
    @Index(name = "ix_pm_tenant_user", columnList = "tenant_id, user_id"),
    @Index(name = "ix_pm_token", columnList = "payment_token", unique = true)
})
@EntityListeners(AuditingEntityListener.class)
public class PaymentMethod {

    @Id
    @Column(name = "payment_method_id", length = 64)
    private String paymentMethodId;

    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    /** Type: CARD, BANK_ACCOUNT, WALLET, MOBILE_MONEY */
    @Column(name = "method_type", nullable = false, length = 32)
    private String methodType;

    /** Brand: VISA, MASTERCARD, AMEX, BANK_X, etc. */
    @Column(name = "brand", length = 32)
    private String brand;

    /** Last 4 digits of PAN (display only) */
    @Column(name = "last_four", length = 4)
    private String lastFour;

    /** Expiry month (1-12) */
    @Column(name = "expiry_month")
    private Integer expiryMonth;

    /** Expiry year (4-digit) */
    @Column(name = "expiry_year")
    private Integer expiryYear;

    /** Cardholder name */
    @Column(name = "cardholder_name", length = 128)
    private String cardholderName;

    /** Opaque token from PSP (NEVER raw PAN) */
    @Column(name = "payment_token", length = 128, nullable = false)
    private String paymentToken;

    /** Provider: STRIPE, ADYEN, DIALOG_MIFE, etc. */
    @Column(name = "provider", length = 32)
    private String provider;

    /** Whether this is the default for the user */
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault;

    /** Status: ACTIVE, EXPIRED, REVOKED */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** Friendly label from user ("My Visa", "Work card") */
    @Column(name = "nickname", length = 64)
    private String nickname;

    @Column(name = "metadata", columnDefinition = "JSON")
    private String metadata;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
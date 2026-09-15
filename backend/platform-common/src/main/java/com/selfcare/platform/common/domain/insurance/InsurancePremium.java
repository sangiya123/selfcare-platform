package com.selfcare.platform.common.domain.insurance;

import com.selfcare.platform.common.dto.Auditable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * Insurance premium schedule and payment record.
 *
 * Stores both the schedule (what is due) and the paid records.
 * Written by the AIAInsuranceProvider when premium events come from the insurer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class InsurancePremium extends Auditable {

    private String id;

    private String tenantId;
    private String customerId;
    private String policyId;
    private String policyNumber;

    private PremiumStatus status;

    // Scheduled amount
    private BigDecimal dueAmount;
    private String currency;
    private LocalDate dueDate;

    // What was actually paid
    private BigDecimal paidAmount;
    private LocalDate paidDate;
    private String paymentMethod;
    private String paymentReference;

    // Auto-debit setup
    private boolean autoDebit;
    private String debitAccount;    // Masked — last 4 digits only
    private String debitMethod;      // BANK, CARD, MOBILE_WALLET

    // Late fee if paid after grace period
    private BigDecimal lateFee;

    // Upstream reference
    private String insurerPremiumRef;

    // Grace period (days after due date before lapse)
    private int gracePeriodDays;

    // If policy lapsed due to non-payment
    private Instant lapsedAt;
    private String lapseReason;

    public enum PremiumStatus {
        PENDING,      // Due but not yet paid
        PAID,         // Successfully paid
        PARTIAL,      // Partially paid (e.g. late fee outstanding)
        OVERDUE,      // Past due date
        LAPSED,       // Policy lapsed due to non-payment
        REINSTATED,   // Lapsed policy was reinstated after payment
        WAIVED        // Insurer waived the premium
    }
}

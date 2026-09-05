package com.omobio.platform.common.domain.insurance;

import com.omobio.platform.common.dto.Auditable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Insurance claim document — stored in MongoDB.
 *
 * Lifecycle: DRAFT -> SUBMITTED -> UNDER_REVIEW -> APPROVED / REJECTED -> SETTLED
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class InsuranceClaim extends Auditable {

    private String id;

    private String tenantId;
    private String customerId;
    private String policyId;          // Reference to InsurancePolicy.id
    private String policyNumber;

    /** Unique claim number shown to the customer */
    private String claimNumber;

    /** Upstream insurer's claim reference */
    private String insurerClaimRef;

    private ClaimType type;
    private ClaimStatus status;

    // --- Event Details ---
    private LocalDate incidentDate;
    private LocalDate reportedDate;
    private String incidentDescription;

    // --- Financial ---
    private BigDecimal claimedAmount;
    private BigDecimal approvedAmount;
    private BigDecimal paidAmount;
    private String currency;

    // --- Settlement ---
    private String settlementMethod;  // BANK_TRANSFER, CASH, MOBILE_MONEY
    private String settlementReference;
    private Instant settledAt;

    // --- Hospitalisation-specific ---
    private String hospitalName;
    private LocalDate admissionDate;
    private LocalDate dischargeDate;
    private String diagnosis;
    private String diagnosisCode;

    // --- Motor-specific ---
    private String vehicleRegNumber;
    private String accidentLocation;
    private String policeReportNumber;

    // --- Documents ---
    private List<String> documentIds;  // References to document store

    // --- Tracking ---
    private List<ClaimActivity> activities;

    // --- Rejection ---
    private String rejectionReason;

    // ======================
    // Enums
    // ======================

    public enum ClaimType {
        DEATH,
        CRITICAL_ILLNESS,
        HOSPITALISATION,
        SURGICAL,
        OUTPATIENT,
        DISABILITY,
        MOTOR_ACCIDENT,
        THEFT_LOSS,
        FIRE_DAMAGE,
        TRAVEL_DELAY,
        BAGGAGE_LOSS,
        PERSONAL_ACCIDENT,
        OTHER
    }

    public enum ClaimStatus {
        DRAFT,          // Saved but not submitted
        SUBMITTED,      // Sent to insurer
        UNDER_REVIEW,   // Insurer reviewing
        DOCUMENTS_REQUIRED, // Insurer needs more documents
        APPROVED,       // Claim approved
        REJECTED,       // Claim rejected
        SETTLED,        // Payment made to customer
        WITHDRAWN        // Customer withdrew the claim
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaimActivity {
        private Instant timestamp;
        private String action;
        private String actor;    // SYSTEM, CUSTOMER, INSURER
        private String notes;
    }
}

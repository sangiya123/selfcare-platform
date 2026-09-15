package com.selfcare.platform.common.domain.insurance;

import com.selfcare.platform.common.dto.Auditable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Insurance policy document — stored in MongoDB.
 *
 * One document per policy held by a customer.
 * The AIAInsuranceProvider (or any operator pack) maps this to/from
 * the upstream insurer's API format.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class InsurancePolicy extends Auditable {

    private String id;

    /** Tenant/operator, e.g. "aia-lk", "aia-sg" */
    private String tenantId;

    /** Customer this policy belongs to */
    private String customerId;

    /** Upstream insurer's policy number */
    private String policyNumber;

    /** Upstream insurer's system reference */
    private String insurerPolicyRef;

    /** Product type drives UI rendering and available features */
    private PolicyType type;

    /** Status within our system */
    private PolicyStatus status;

    /** Status as reported by the insurer */
    private String insurerStatus;

    // --- Policy Holder ---
    private PolicyHolder holder;

    // --- Coverage ---
    private BigDecimal sumAssured;     // Total coverage amount
    private BigDecimal premiumAmount;   // Per-period premium
    private String premiumFrequency;    // MONTHLY, QUARTERLY, HALF_YEARLY, YEARLY
    private String currency;

    private BigDecimal coverageAmount;  // Current active coverage
    private LocalDate coverageEndDate;
    private LocalDate maturityDate;     // When the policy matures / pays out

    // --- Plan Details ---
    private String planName;
    private String planCode;
    private String productCode;

    // --- Financial ---
    private BigDecimal accumulatedValue; // For endowment / unit-linked
    private BigDecimal totalPremiumsPaid;
    private BigDecimal lastPremiumPaid;

    // --- Dates ---
    private LocalDate policyStartDate;
    private LocalDate policyEndDate;
    private LocalDate nextPremiumDueDate;
    private LocalDate renewalDate;

    // --- Linked Connections (for bundled telco+insurance offers) ---
    private List<String> linkedConnectionIds;

    // --- Beneficiary IDs (managed via InsuranceBeneficiary collection) ---
    private List<String> beneficiaryIds;

    // --- Metadata from insurer ---
    private java.util.Map<String, String> metadata;

    // ======================
    // Enums
    // ======================

    public enum PolicyType {
        LIFE,           // Term life, whole life, endowment
        HEALTH,         // Hospitalisation, critical illness
        MOTOR,          // Vehicle insurance
        TRAVEL,         // Travel insurance
        HOME,           // Home contents / building
        PERSONAL_ACCIDENT,
        EDUCATION,      // Education endowment
        INVESTMENT      // Unit-linked, investment-linked
    }

    public enum PolicyStatus {
        ACTIVE,         // Premiums up to date, coverage in force
        LAPSE,          // Premium overdue — coverage suspended
        SURRENDER,      // Policyholder surrendered
        MATURED,        // Policy reached maturity
        EXPIRED,        // Coverage period ended
        CANCELLED,      // Cancelled by insurer or policyholder
        PENDING,        // New business, awaiting underwriting
        DECLINED        // Application declined
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyHolder {
        private String customerId;
        private String fullName;
        private String nic;            // National ID
        private String dateOfBirth;
        private String gender;
        private String mobile;
        private String email;
        private String address;
    }
}

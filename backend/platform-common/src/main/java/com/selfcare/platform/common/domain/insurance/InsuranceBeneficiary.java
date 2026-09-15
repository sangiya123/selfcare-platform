package com.selfcare.platform.common.domain.insurance;

import com.selfcare.platform.common.dto.Auditable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Insurance beneficiary — receives the policy payout upon the event
 * (death, maturity, claim).
 *
 * Linked to a policy via InsurancePolicy.beneficiaryIds.
 * Each beneficiary has an allocation percentage that must sum to 100.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class InsuranceBeneficiary extends Auditable {

    private String id;

    private String tenantId;
    private String customerId;
    private String policyId;

    private String fullName;
    private String nic;
    private LocalDate dateOfBirth;
    private String gender;
    private Relationship relationship;

    /** 0-100 — must sum to 100 across all beneficiaries of a policy */
    private BigDecimal allocationPercent;

    private String mobile;
    private String email;
    private String address;

    private String status;  // ACTIVE, REVOKED

    public enum Relationship {
        SPOUSE,
        CHILD,
        PARENT,
        SIBLING,
        SELF,
        BUSINESS_PARTNER,
        TRUST,
        OTHER
    }
}

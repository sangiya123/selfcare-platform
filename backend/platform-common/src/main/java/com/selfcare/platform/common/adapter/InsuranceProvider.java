package com.selfcare.platform.common.adapter;

import com.selfcare.platform.common.domain.insurance.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Insurance provider contract — implemented by operator pack providers
 * (e.g. AIAInsuranceProvider, AllianzInsuranceProvider).
 *
 * All insurance operator integrations implement this interface.
 * The InsuranceService injects ApiAdapterRegistry&lt;InsuranceProvider&gt;
 * and calls getProvider(tenantId) to get the right implementation.
 *
 * Covers the full AIA selfcare feature set:
 * - Policy management (view, renew)
 * - Claims (file, track, upload documents)
 * - Beneficiaries (add, remove, update)
 * - Premium payments (pay, setup auto-debit)
 *
 * All methods take tenantId as first param — multi-tenant safe.
 * All upstream configs (URL, credentials) are fetched at runtime from
 * TenantConfigurationService — NOT from env files.
 */
public interface InsuranceProvider extends ApiAdapter {

    // ================================================================
    // Policy Management
    // ================================================================

    /**
     * Get all policies for a customer.
     */
    List<InsurancePolicy> getPolicies(String tenantId, String customerId);

    /**
     * Get a specific policy by our internal ID.
     */
    InsurancePolicy getPolicy(String tenantId, String customerId, String policyId);

    /**
     * Get a specific policy by the insurer's reference number.
     */
    InsurancePolicy getPolicyByRef(String tenantId, String customerId, String insurerPolicyRef);

    /**
     * Initiate a policy renewal. Returns the renewal offer from the insurer.
     */
    RenewalOffer initiateRenewal(String tenantId, String policyId);

    /**
     * Confirm a renewal after the customer reviews the offer.
     */
    RenewalResult confirmRenewal(String tenantId, String policyId, String renewalOption);

    // ================================================================
    // Claims
    // ================================================================

    /**
     * File a new claim. Returns the claim number and current status.
     */
    ClaimSubmitResult submitClaim(String tenantId, String policyId, ClaimSubmission submission);

    /**
     * Get all claims for a customer.
     */
    List<InsuranceClaim> getClaims(String tenantId, String customerId);

    /**
     * Get a specific claim by our ID.
     */
    InsuranceClaim getClaim(String tenantId, String customerId, String claimId);

    /**
     * Get all activities/milestones for a claim.
     */
    List<InsuranceClaim.ClaimActivity> getClaimActivities(String tenantId, String claimId);

    /**
     * Upload a supporting document for a claim.
     */
    DocumentUploadResult uploadClaimDocument(String tenantId, String claimId,
                                              String fileName, byte[] content, String mimeType);

    // ================================================================
    // Beneficiaries
    // ================================================================

    /**
     * Get all beneficiaries for a policy.
     */
    List<InsuranceBeneficiary> getBeneficiaries(String tenantId, String policyId);

    /**
     * Add a new beneficiary to a policy.
     * May require policy endorsement — returns pending status if so.
     */
    BeneficiaryResult addBeneficiary(String tenantId, String policyId, InsuranceBeneficiary beneficiary);

    /**
     * Update a beneficiary's details.
     */
    BeneficiaryResult updateBeneficiary(String tenantId, String beneficiaryId, InsuranceBeneficiary updates);

    /**
     * Remove a beneficiary from a policy.
     */
    BeneficiaryResult removeBeneficiary(String tenantId, String beneficiaryId);

    // ================================================================
    // Premium Payments
    // ================================================================

    /**
     * Get the premium schedule for a policy.
     */
    List<InsurancePremium> getPremiumSchedule(String tenantId, String policyId);

    /**
     * Get the next due premium for a policy.
     */
    InsurancePremium getNextDuePremium(String tenantId, String policyId);

    /**
     * Pay a premium immediately (one-time payment).
     */
    PremiumPaymentResult payPremium(String tenantId, String premiumId, PremiumPayment payment);

    /**
     * Setup or update auto-debit for premium collection.
     */
    AutoDebitResult setupAutoDebit(String tenantId, String policyId, AutoDebitSetup setup);

    /**
     * Cancel auto-debit.
     */
    void cancelAutoDebit(String tenantId, String policyId);

    // ================================================================
    // Result DTOs
    // ================================================================

    record RenewalOffer(
            String renewalOptionId,
            String policyId,
            int newTermYears,
            BigDecimal newPremiumAmount,
            BigDecimal newSumAssured,
            LocalDate effectiveDate,
            LocalDate expiryDate,
            Map<String, String> options
    ) {}

    record RenewalResult(boolean success, String newPolicyNumber, String failureReason) {}

    record ClaimSubmitResult(
            boolean success,
            String claimId,
            String claimNumber,
            InsuranceClaim.ClaimStatus initialStatus,
            String failureReason
    ) {}

    record ClaimSubmission(
            InsuranceClaim.ClaimType type,
            LocalDate incidentDate,
            String description,
            BigDecimal claimedAmount,
            String hospitalName,
            LocalDate admissionDate,
            LocalDate dischargeDate,
            String diagnosis,
            String vehicleRegNumber,
            String accidentLocation,
            String policeReportNumber,
            List<String> documentIds
    ) {}

    record DocumentUploadResult(
            boolean success,
            String documentId,
            String fileName,
            String failureReason
    ) {}

    record BeneficiaryResult(
            boolean success,
            String endorsementStatus,
            String failureReason
    ) {}

    record PremiumPaymentResult(
            boolean success,
            String paymentReference,
            InsurancePremium.PremiumStatus newStatus,
            String failureReason
    ) {}

    record AutoDebitResult(
            boolean success,
            String mandateReference,
            String failureReason
    ) {}

    record AutoDebitSetup(
            String accountType,
            String accountNumber,
            String bankCode,
            String holderName,
            LocalDate firstDebitDate
    ) {}

    record PremiumPayment(
            String method,
            String paymentToken,
            BigDecimal amount,
            String currency
    ) {}
}

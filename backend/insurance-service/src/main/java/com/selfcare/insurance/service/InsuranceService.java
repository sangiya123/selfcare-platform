package com.selfcare.insurance.service;

import com.selfcare.platform.common.adapter.ApiAdapterRegistry;
import com.selfcare.platform.common.adapter.InsuranceProvider;
import com.selfcare.platform.common.domain.insurance.*;
import com.selfcare.insurance.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Insurance BFF — fronts the per-tenant insurance provider.
 *
 * Strategy:
 * - Provider (AIA, etc.) is the source of truth for live data.
 * - We also persist a denormalized cache in MongoDB for fast reads
 *   and for offline / history features.
 * - Writes go to the provider first, then cached.
 *
 * Multi-tenant safe: every method takes tenantId explicitly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsuranceService {

    private final ApiAdapterRegistry<InsuranceProvider> providerRegistry;
    private final InsurancePolicyRepository policyRepository;
    private final InsuranceClaimRepository claimRepository;
    private final InsuranceBeneficiaryRepository beneficiaryRepository;
    private final InsurancePremiumRepository premiumRepository;

    private InsuranceProvider provider(String tenantId) {
        return providerRegistry.getProvider(tenantId);
    }

    // ================================================================
    // Policy
    // ================================================================

    public List<InsurancePolicy> getPolicies(String tenantId, String customerId) {
        List<InsurancePolicy> fromProvider = safeGet(() -> provider(tenantId).getPolicies(tenantId, customerId));
        if (fromProvider != null) {
            // Cache and return
            for (InsurancePolicy p : fromProvider) {
                policyRepository.findByTenantIdAndInsurerPolicyRef(tenantId, p.getInsurerPolicyRef())
                        .orElseGet(() -> policyRepository.save(p));
            }
        }
        // Fallback to cache if provider returned nothing
        return fromProvider != null && !fromProvider.isEmpty()
                ? fromProvider
                : policyRepository.findByTenantIdAndCustomerId(tenantId, customerId);
    }

    public InsurancePolicy getPolicy(String tenantId, String customerId, String policyId) {
        InsurancePolicy fromProvider = safeGet(() -> provider(tenantId).getPolicy(tenantId, customerId, policyId));
        if (fromProvider != null) return fromProvider;
        return policyRepository.findById(policyId).orElse(null);
    }

    public InsuranceProvider.RenewalOffer initiateRenewal(String tenantId, String policyId) {
        return safeGet(() -> provider(tenantId).initiateRenewal(tenantId, policyId));
    }

    public InsuranceProvider.RenewalResult confirmRenewal(String tenantId, String policyId, String optionId) {
        return safeGet(() -> provider(tenantId).confirmRenewal(tenantId, policyId, optionId));
    }

    // ================================================================
    // Claims
    // ================================================================

    public InsuranceProvider.ClaimSubmitResult submitClaim(String tenantId, String customerId, String policyId,
                                                           InsuranceProvider.ClaimSubmission submission) {
        InsuranceProvider.ClaimSubmitResult result =
                safeGet(() -> provider(tenantId).submitClaim(tenantId, policyId, submission));
        if (result != null && result.success()) {
            // Cache locally
            InsuranceClaim claim = InsuranceClaim.builder()
                    .tenantId(tenantId)
                    .customerId(customerId)
                    .policyId(policyId)
                    .id(result.claimId())
                    .claimNumber(result.claimNumber())
                    .status(result.initialStatus())
                    .type(submission.type())
                    .incidentDate(submission.incidentDate())
                    .claimedAmount(submission.claimedAmount())
                    .incidentDescription(submission.description())
                    .build();
            claimRepository.save(claim);
        }
        return result;
    }

    public List<InsuranceClaim> getClaims(String tenantId, String customerId) {
        List<InsuranceClaim> fromProvider = safeGet(() -> provider(tenantId).getClaims(tenantId, customerId));
        if (fromProvider != null && !fromProvider.isEmpty()) {
            for (InsuranceClaim c : fromProvider) {
                claimRepository.findByTenantIdAndClaimNumber(tenantId, c.getClaimNumber())
                        .orElseGet(() -> claimRepository.save(c));
            }
            return fromProvider;
        }
        return claimRepository.findByTenantIdAndCustomerId(tenantId, customerId);
    }

    public InsuranceClaim getClaim(String tenantId, String customerId, String claimId) {
        InsuranceClaim fromProvider = safeGet(() -> provider(tenantId).getClaim(tenantId, customerId, claimId));
        if (fromProvider != null) return fromProvider;
        return claimRepository.findById(claimId).orElse(null);
    }

    public List<InsuranceClaim.ClaimActivity> getClaimActivities(String tenantId, String claimId) {
        return safeGet(() -> provider(tenantId).getClaimActivities(tenantId, claimId));
    }

    public InsuranceProvider.DocumentUploadResult uploadClaimDocument(String tenantId, String claimId,
                                                                     String fileName, byte[] content, String mimeType) {
        return safeGet(() -> provider(tenantId).uploadClaimDocument(tenantId, claimId, fileName, content, mimeType));
    }

    // ================================================================
    // Beneficiaries
    // ================================================================

    public List<InsuranceBeneficiary> getBeneficiaries(String tenantId, String policyId) {
        List<InsuranceBeneficiary> fromProvider = safeGet(() -> provider(tenantId).getBeneficiaries(tenantId, policyId));
        if (fromProvider != null && !fromProvider.isEmpty()) {
            for (InsuranceBeneficiary b : fromProvider) {
                beneficiaryRepository.findByTenantIdAndId(tenantId, b.getId())
                        .orElseGet(() -> beneficiaryRepository.save(b));
            }
            return fromProvider;
        }
        return beneficiaryRepository.findByTenantIdAndPolicyId(tenantId, policyId);
    }

    public InsuranceProvider.BeneficiaryResult addBeneficiary(String tenantId, String policyId,
                                                            InsuranceBeneficiary beneficiary) {
        beneficiary.setTenantId(tenantId);
        beneficiary.setPolicyId(policyId);
        InsuranceProvider.BeneficiaryResult result =
                safeGet(() -> provider(tenantId).addBeneficiary(tenantId, policyId, beneficiary));
        if (result != null && result.success()) {
            beneficiaryRepository.save(beneficiary);
        }
        return result;
    }

    public InsuranceProvider.BeneficiaryResult updateBeneficiary(String tenantId, String beneficiaryId,
                                                               InsuranceBeneficiary updates) {
        return safeGet(() -> provider(tenantId).updateBeneficiary(tenantId, beneficiaryId, updates));
    }

    public InsuranceProvider.BeneficiaryResult removeBeneficiary(String tenantId, String beneficiaryId) {
        InsuranceProvider.BeneficiaryResult result =
                safeGet(() -> provider(tenantId).removeBeneficiary(tenantId, beneficiaryId));
        if (result != null && result.success()) {
            beneficiaryRepository.deleteById(beneficiaryId);
        }
        return result;
    }

    // ================================================================
    // Premiums
    // ================================================================

    public List<InsurancePremium> getPremiumSchedule(String tenantId, String policyId) {
        return safeGet(() -> provider(tenantId).getPremiumSchedule(tenantId, policyId));
    }

    public InsurancePremium getNextDuePremium(String tenantId, String policyId) {
        return safeGet(() -> provider(tenantId).getNextDuePremium(tenantId, policyId));
    }

    public InsuranceProvider.PremiumPaymentResult payPremium(String tenantId, String premiumId,
                                                            InsuranceProvider.PremiumPayment payment) {
        return safeGet(() -> provider(tenantId).payPremium(tenantId, premiumId, payment));
    }

    public InsuranceProvider.AutoDebitResult setupAutoDebit(String tenantId, String policyId,
                                                           InsuranceProvider.AutoDebitSetup setup) {
        return safeGet(() -> provider(tenantId).setupAutoDebit(tenantId, policyId, setup));
    }

    public void cancelAutoDebit(String tenantId, String policyId) {
        safeRun(() -> provider(tenantId).cancelAutoDebit(tenantId, policyId));
    }

    // ================================================================
    // Helpers
    // ================================================================

    private <T> T safeGet(java.util.function.Supplier<T> fn) {
        try {
            return fn.get();
        } catch (IllegalStateException noProvider) {
            log.warn("No insurance provider registered: {}", noProvider.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Insurance provider call failed: {}", e.getMessage(), e);
            return null;
        }
    }

    private void safeRun(Runnable fn) {
        try {
            fn.run();
        } catch (IllegalStateException noProvider) {
            log.warn("No insurance provider registered: {}", noProvider.getMessage());
        } catch (Exception e) {
            log.error("Insurance provider call failed: {}", e.getMessage(), e);
        }
    }
}

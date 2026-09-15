package com.selfcare.insurance.service;

import com.selfcare.platform.common.adapter.ApiAdapterRegistry;
import com.selfcare.platform.common.adapter.InsuranceProvider;
import com.selfcare.platform.common.domain.insurance.*;
import com.selfcare.insurance.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InsuranceService.
 *
 * Verifies:
 * - getPolicies / getPolicy — provider-first with MongoDB cache fallback
 * - initiateRenewal / confirmRenewal — renewal flow forwards to provider
 * - submitClaim — provider-first, caches locally on success, swallows provider errors
 * - getClaims / getClaim — provider-first with cache fallback
 * - getClaimActivities / uploadClaimDocument — claim history and documents
 * - getBeneficiaries / addBeneficiary / updateBeneficiary / removeBeneficiary
 * - premium methods — schedule, next due, payment, auto-debit
 *
 * Provider-first strategy: provider is source of truth; MongoDB is cache/fallback.
 */
@ExtendWith(MockitoExtension.class)
class InsuranceServiceTest {

    @Mock private ApiAdapterRegistry<InsuranceProvider> providerRegistry;
    @Mock private InsurancePolicyRepository policyRepository;
    @Mock private InsuranceClaimRepository claimRepository;
    @Mock private InsuranceBeneficiaryRepository beneficiaryRepository;
    @Mock private InsurancePremiumRepository premiumRepository;
    @Mock private InsuranceProvider aiaProvider;

    private InsuranceService service;

    @BeforeEach
    void setUp() {
        service = new InsuranceService(providerRegistry, policyRepository,
                claimRepository, beneficiaryRepository, premiumRepository);
    }

    // ======================================================================
    // getPolicies
    // ======================================================================

    @Test
    @DisplayName("getPolicies returns from provider and caches in MongoDB")
    void getPolicies_providerFirst() {
        InsurancePolicy p1 = InsurancePolicy.builder()
                .id("policy-1")
                .insurerPolicyRef("AIA-001")
                .customerId("cust-a")
                .status(InsurancePolicy.PolicyStatus.ACTIVE)
                .build();
        when(providerRegistry.getProvider("aia-sg")).thenReturn(aiaProvider);
        when(aiaProvider.getPolicies("aia-sg", "cust-a")).thenReturn(List.of(p1));
        when(policyRepository.findByTenantIdAndInsurerPolicyRef("aia-sg", "AIA-001"))
                .thenReturn(Optional.empty());
        when(policyRepository.save(any(InsurancePolicy.class))).thenAnswer(inv -> inv.getArgument(0));

        List<InsurancePolicy> result = service.getPolicies("aia-sg", "cust-a");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getInsurerPolicyRef()).isEqualTo("AIA-001");
        verify(policyRepository).save(p1);
    }

    @Test
    @DisplayName("getPolicies falls back to MongoDB cache when provider returns empty")
    void getPolicies_fallbackToCache() {
        when(providerRegistry.getProvider("aia-sg")).thenReturn(aiaProvider);
        when(aiaProvider.getPolicies("aia-sg", "cust-a")).thenReturn(List.of());

        InsurancePolicy cached = InsurancePolicy.builder()
                .id("cached-1")
                .customerId("cust-a")
                .build();
        when(policyRepository.findByTenantIdAndCustomerId("aia-sg", "cust-a"))
                .thenReturn(List.of(cached));

        List<InsurancePolicy> result = service.getPolicies("aia-sg", "cust-a");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("cached-1");
    }

    @Test
    @DisplayName("getPolicies falls back to MongoDB when provider throws")
    void getPolicies_providerError() {
        when(providerRegistry.getProvider("aia-sg")).thenReturn(aiaProvider);
        when(aiaProvider.getPolicies("aia-sg", "cust-a"))
                .thenThrow(new RuntimeException("Provider unreachable"));

        InsurancePolicy cached = InsurancePolicy.builder()
                .id("cached-1")
                .customerId("cust-a")
                .build();
        when(policyRepository.findByTenantIdAndCustomerId("aia-sg", "cust-a"))
                .thenReturn(List.of(cached));

        List<InsurancePolicy> result = service.getPolicies("aia-sg", "cust-a");

        assertThat(result).hasSize(1);
    }

    // ======================================================================
    // getPolicy
    // ======================================================================

    @Test
    @DisplayName("getPolicy returns from provider")
    void getPolicy_fromProvider() {
        InsurancePolicy policy = InsurancePolicy.builder()
                .id("policy-1")
                .insurerPolicyRef("AIA-001")
                .build();
        when(providerRegistry.getProvider("aia-sg")).thenReturn(aiaProvider);
        when(aiaProvider.getPolicy("aia-sg", "cust-a", "policy-1")).thenReturn(policy);

        InsurancePolicy result = service.getPolicy("aia-sg", "cust-a", "policy-1");

        assertThat(result.getId()).isEqualTo("policy-1");
        verifyNoInteractions(policyRepository);
    }

    @Test
    @DisplayName("getPolicy falls back to MongoDB when provider returns null")
    void getPolicy_fallbackToCache() {
        when(providerRegistry.getProvider("aia-sg")).thenReturn(aiaProvider);
        when(aiaProvider.getPolicy("aia-sg", "cust-a", "policy-1")).thenReturn(null);

        InsurancePolicy cached = InsurancePolicy.builder().id("policy-1").build();
        when(policyRepository.findById("policy-1")).thenReturn(Optional.of(cached));

        InsurancePolicy result = service.getPolicy("aia-sg", "cust-a", "policy-1");

        assertThat(result.getId()).isEqualTo("policy-1");
    }

    // ======================================================================
    // Renewal
    // ======================================================================

    @Test
    @DisplayName("initiateRenewal returns the renewal offer from the provider")
    void initiateRenewal_returnsOffer() {
        InsuranceProvider.RenewalOffer offer = new InsuranceProvider.RenewalOffer(
                "opt-1", "policy-1", 1,
                new BigDecimal("150.00"), new BigDecimal("100000.00"),
                LocalDate.now().plusYears(1), LocalDate.now().plusYears(2),
                Map.of("opt-1", "Keep current plan"));
        when(providerRegistry.getProvider("aia-sg")).thenReturn(aiaProvider);
        when(aiaProvider.initiateRenewal("aia-sg", "policy-1")).thenReturn(offer);

        InsuranceProvider.RenewalOffer result = service.initiateRenewal("aia-sg", "policy-1");

        assertThat(result.renewalOptionId()).isEqualTo("opt-1");
        assertThat(result.newPremiumAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(result.expiryDate()).isNotNull();
    }

    @Test
    @DisplayName("confirmRenewal forwards the chosen option to the provider")
    void confirmRenewal_returnsResult() {
        when(providerRegistry.getProvider("aia-sg")).thenReturn(aiaProvider);
        when(aiaProvider.confirmRenewal("aia-sg", "policy-1", "opt-1"))
                .thenReturn(new InsuranceProvider.RenewalResult(true, "POL-NEW-1", null));

        InsuranceProvider.RenewalResult result = service.confirmRenewal("aia-sg", "policy-1", "opt-1");

        assertThat(result.success()).isTrue();
        assertThat(result.newPolicyNumber()).isEqualTo("POL-NEW-1");
    }

    // ======================================================================
    // submitClaim
    // ======================================================================

    @Test
    @DisplayName("submitClaim returns provider result and caches claim on success")
    void submitClaim_cachesOnSuccess() {
        InsuranceProvider.ClaimSubmission submission = new InsuranceProvider.ClaimSubmission(
                InsuranceClaim.ClaimType.OUTPATIENT,
                LocalDate.of(2026, 8, 1),
                "Doctor visit",
                new BigDecimal("500.00"),
                null, null, null, null, null, null, null, List.of());
        InsuranceProvider.ClaimSubmitResult expected = new InsuranceProvider.ClaimSubmitResult(
                true, "claim-1", "CLM-001", InsuranceClaim.ClaimStatus.SUBMITTED, null);

        when(providerRegistry.getProvider("aia-sg")).thenReturn(aiaProvider);
        when(aiaProvider.submitClaim("aia-sg", "policy-1", submission)).thenReturn(expected);
        when(claimRepository.save(any(InsuranceClaim.class))).thenAnswer(inv -> inv.getArgument(0));

        InsuranceProvider.ClaimSubmitResult result =
                service.submitClaim("aia-sg", "cust-a", "policy-1", submission);

        assertThat(result.success()).isTrue();
        assertThat(result.claimNumber()).isEqualTo("CLM-001");

        ArgumentCaptor<InsuranceClaim> captor = ArgumentCaptor.forClass(InsuranceClaim.class);
        verify(claimRepository).save(captor.capture());
        InsuranceClaim cached = captor.getValue();
        assertThat(cached.getTenantId()).isEqualTo("aia-sg");
        assertThat(cached.getCustomerId()).isEqualTo("cust-a");
        assertThat(cached.getPolicyId()).isEqualTo("policy-1");
        assertThat(cached.getId()).isEqualTo("claim-1");
        assertThat(cached.getClaimNumber()).isEqualTo("CLM-001");
        assertThat(cached.getStatus()).isEqualTo(InsuranceClaim.ClaimStatus.SUBMITTED);
        assertThat(cached.getType()).isEqualTo(InsuranceClaim.ClaimType.OUTPATIENT);
        assertThat(cached.getClaimedAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(cached.getIncidentDescription()).isEqualTo("Doctor visit");
    }

    @Test
    @DisplayName("submitClaim does not cache and returns null when provider fails")
    void submitClaim_providerFailure() {
        InsuranceProvider.ClaimSubmission submission = new InsuranceProvider.ClaimSubmission(
                InsuranceClaim.ClaimType.HOSPITALISATION,
                LocalDate.of(2026, 8, 1),
                "Inpatient stay",
                new BigDecimal("5000.00"),
                null, null, null, null, null, null, null, List.of());
        when(providerRegistry.getProvider("aia-sg")).thenReturn(aiaProvider);
        when(aiaProvider.submitClaim("aia-sg", "policy-1", submission))
                .thenThrow(new RuntimeException("Provider validation failed"));

        InsuranceProvider.ClaimSubmitResult result =
                service.submitClaim("aia-sg", "cust-a", "policy-1", submission);

        assertThat(result).isNull();
        verifyNoInteractions(claimRepository);
    }

    // ======================================================================
    // getClaims / getClaim
    // ======================================================================

    @Test
    @DisplayName("getClaims returns from provider and caches missing claims")
    void getClaims_providerFirst() {
        InsuranceClaim c1 = InsuranceClaim.builder()
                .id("claim-1")
                .claimNumber("CLM-001")
                .build();
        when(providerRegistry.getProvider("aia-sg")).thenReturn(aiaProvider);
        when(aiaProvider.getClaims("aia-sg", "cust-a")).thenReturn(List.of(c1));
        when(claimRepository.findByTenantIdAndClaimNumber("aia-sg", "CLM-001"))
                .thenReturn(Optional.empty());
        when(claimRepository.save(any(InsuranceClaim.class))).thenAnswer(inv -> inv.getArgument(0));

        List<InsuranceClaim> result = service.getClaims("aia-sg", "cust-a");

        assertThat(result).hasSize(1);
        verify(claimRepository).save(c1);
    }

    @Test
    @DisplayName("getClaims falls back to MongoDB cache when provider returns empty")
    void getClaims_fallbackToCache() {
        when(providerRegistry.getProvider("aia-sg")).thenReturn(aiaProvider);
        when(aiaProvider.getClaims("aia-sg", "cust-a")).thenReturn(List.of());

        InsuranceClaim cached = InsuranceClaim.builder().id("cached-1").build();
        when(claimRepository.findByTenantIdAndCustomerId("aia-sg", "cust-a"))
                .thenReturn(List.of(cached));

        List<InsuranceClaim> result = service.getClaims("aia-sg", "cust-a");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("cached-1");
    }

    @Test
    @DisplayName("getClaim falls back to MongoDB cache when provider returns null")
    void getClaim_fallbackToCache() {
        InsuranceClaim cached = InsuranceClaim.builder().id("claim-1").build();
        when(providerRegistry.getProvider("aia-sg")).thenReturn(aiaProvider);
        when(aiaProvider.getClaim("aia-sg", "cust-a", "claim-1")).thenReturn(null);
        when(claimRepository.findById("claim-1")).thenReturn(Optional.of(cached));

        InsuranceClaim result = service.getClaim("aia-sg", "cust-a", "claim-1");

        assertThat(result.getId()).isEqualTo("claim-1");
    }

    // ======================================================================
    // Claims support
    // ======================================================================

    @Test
    @DisplayName("getClaimActivities returns the claim timeline from the provider")
    void getClaimActivities_returnsTimeline() {
        InsuranceClaim.ClaimActivity act = InsuranceClaim.ClaimActivity.builder()
                .action("SUBMITTED")
                .actor("CUSTOMER")
                .notes("Claim filed online")
                .build();
        when(providerRegistry.getProvider("aia-sg")).thenReturn(aiaProvider);
        when(aiaProvider.getClaimActivities("aia-sg", "claim-1")).thenReturn(List.of(act));

        List<InsuranceClaim.ClaimActivity> result = service.getClaimActivities("aia-sg", "claim-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAction()).isEqualTo("SUBMITTED");
    }

    @Test
    @DisplayName("uploadClaimDocument forwards the document to the provider")
    void uploadClaimDocument_returnsResult() {
        byte[] content = new byte[]{1, 2, 3};
        when(providerRegistry.getProvider("aia-sg")).thenReturn(aiaProvider);
        when(aiaProvider.uploadClaimDocument("aia-sg", "claim-1", "receipt.pdf", content, "application/pdf"))
                .thenReturn(new InsuranceProvider.DocumentUploadResult(true, "doc-1", "receipt.pdf", null));

        InsuranceProvider.DocumentUploadResult result =
                service.uploadClaimDocument("aia-sg", "claim-1", "receipt.pdf", content, "application/pdf");

        assertThat(result.success()).isTrue();
        assertThat(result.documentId()).isEqualTo("doc-1");
    }

    // ======================================================================
    // Beneficiaries
    // ======================================================================

    @Test
    @DisplayName("getBeneficiaries returns from provider and caches missing entries")
    void getBeneficiaries_providerFirst() {
        InsuranceBeneficiary b1 = InsuranceBeneficiary.builder()
                .id("ben-1")
                .policyId("policy-1")
                .fullName("Jane Doe")
                .relationship(InsuranceBeneficiary.Relationship.SPOUSE)
                .build();
        when(providerRegistry.getProvider("aia-sg")).thenReturn(aiaProvider);
        when(aiaProvider.getBeneficiaries("aia-sg", "policy-1")).thenReturn(List.of(b1));
        when(beneficiaryRepository.findByTenantIdAndId("aia-sg", "ben-1")).thenReturn(Optional.empty());
        when(beneficiaryRepository.save(any(InsuranceBeneficiary.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        List<InsuranceBeneficiary> result = service.getBeneficiaries("aia-sg", "policy-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFullName()).isEqualTo("Jane Doe");
        verify(beneficiaryRepository).save(b1);
    }

    @Test
    @DisplayName("getBeneficiaries falls back to MongoDB cache when provider returns empty")
    void getBeneficiaries_fallbackToCache() {
        when(providerRegistry.getProvider("aia-sg")).thenReturn(aiaProvider);
        when(aiaProvider.getBeneficiaries("aia-sg", "policy-1")).thenReturn(List.of());

        InsuranceBeneficiary cached = InsuranceBeneficiary.builder().id("ben-c").build();
        when(beneficiaryRepository.findByTenantIdAndPolicyId("aia-sg", "policy-1"))
                .thenReturn(List.of(cached));

        List<InsuranceBeneficiary> result = service.getBeneficiaries("aia-sg", "policy-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("ben-c");
    }

    @Test
    @DisplayName("addBeneficiary stamps tenant/policy and caches locally on success")
    void addBeneficiary_savesOnSuccess() {
        InsuranceBeneficiary beneficiary = InsuranceBeneficiary.builder()
                .id("ben-1")
                .fullName("Jane Doe")
                .build();
        when(providerRegistry.getProvider("aia-sg")).thenReturn(aiaProvider);
        when(aiaProvider.addBeneficiary("aia-sg", "policy-1", beneficiary))
                .thenReturn(new InsuranceProvider.BeneficiaryResult(true, "PENDING_ENDORSEMENT", null));
        when(beneficiaryRepository.save(any(InsuranceBeneficiary.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        InsuranceProvider.BeneficiaryResult result =
                service.addBeneficiary("aia-sg", "policy-1", beneficiary);

        assertThat(result.success()).isTrue();
        assertThat(result.endorsementStatus()).isEqualTo("PENDING_ENDORSEMENT");
        verify(beneficiaryRepository).save(beneficiary);
        assertThat(beneficiary.getTenantId()).isEqualTo("aia-sg");
        assertThat(beneficiary.getPolicyId()).isEqualTo("policy-1");
    }

    @Test
    @DisplayName("updateBeneficiary forwards the updates to the provider")
    void updateBeneficiary_returnsResult() {
        InsuranceBeneficiary updates = InsuranceBeneficiary.builder().id("ben-1").fullName("Jane Updated").build();
        when(providerRegistry.getProvider("aia-sg")).thenReturn(aiaProvider);
        when(aiaProvider.updateBeneficiary("aia-sg", "ben-1", updates))
                .thenReturn(new InsuranceProvider.BeneficiaryResult(true, null, null));

        InsuranceProvider.BeneficiaryResult result = service.updateBeneficiary("aia-sg", "ben-1", updates);

        assertThat(result.success()).isTrue();
        verifyNoInteractions(beneficiaryRepository);
    }

    @Test
    @DisplayName("removeBeneficiary deletes the local cache when the provider succeeds")
    void removeBeneficiary_deletesOnSuccess() {
        when(providerRegistry.getProvider("aia-sg")).thenReturn(aiaProvider);
        when(aiaProvider.removeBeneficiary("aia-sg", "ben-1"))
                .thenReturn(new InsuranceProvider.BeneficiaryResult(true, null, null));

        InsuranceProvider.BeneficiaryResult result = service.removeBeneficiary("aia-sg", "ben-1");

        assertThat(result.success()).isTrue();
        verify(beneficiaryRepository).deleteById("ben-1");
    }

    // ======================================================================
    // Premiums
    // ======================================================================

    @Test
    @DisplayName("getPremiumSchedule returns the schedule from the provider")
    void getPremiumSchedule_returnsFromProvider() {
        InsurancePremium premium = InsurancePremium.builder()
                .id("prem-1")
                .policyId("policy-1")
                .dueDate(LocalDate.now().plusDays(15))
                .dueAmount(new BigDecimal("120.00"))
                .currency("SGD")
                .status(InsurancePremium.PremiumStatus.PENDING)
                .build();
        when(providerRegistry.getProvider("aia-sg")).thenReturn(aiaProvider);
        when(aiaProvider.getPremiumSchedule("aia-sg", "policy-1")).thenReturn(List.of(premium));

        List<InsurancePremium> result = service.getPremiumSchedule("aia-sg", "policy-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDueAmount()).isEqualByComparingTo(new BigDecimal("120.00"));
        assertThat(result.get(0).getStatus()).isEqualTo(InsurancePremium.PremiumStatus.PENDING);
    }

    @Test
    @DisplayName("getNextDuePremium returns the next premium from the provider")
    void getNextDuePremium_returnsFromProvider() {
        InsurancePremium premium = InsurancePremium.builder()
                .id("prem-1")
                .policyId("policy-1")
                .dueDate(LocalDate.now().plusDays(7))
                .dueAmount(new BigDecimal("120.00"))
                .build();
        when(providerRegistry.getProvider("aia-sg")).thenReturn(aiaProvider);
        when(aiaProvider.getNextDuePremium("aia-sg", "policy-1")).thenReturn(premium);

        InsurancePremium result = service.getNextDuePremium("aia-sg", "policy-1");

        assertThat(result.getPolicyId()).isEqualTo("policy-1");
        assertThat(result.getDueAmount()).isEqualByComparingTo(new BigDecimal("120.00"));
    }

    @Test
    @DisplayName("payPremium forwards the payment to the provider")
    void payPremium_returnsResult() {
        InsuranceProvider.PremiumPayment payment =
                new InsuranceProvider.PremiumPayment("CARD", "tok-123", new BigDecimal("120.00"), "SGD");
        when(providerRegistry.getProvider("aia-sg")).thenReturn(aiaProvider);
        when(aiaProvider.payPremium("aia-sg", "prem-1", payment))
                .thenReturn(new InsuranceProvider.PremiumPaymentResult(true, "ref-1",
                        InsurancePremium.PremiumStatus.PAID, null));

        InsuranceProvider.PremiumPaymentResult result = service.payPremium("aia-sg", "prem-1", payment);

        assertThat(result.success()).isTrue();
        assertThat(result.paymentReference()).isEqualTo("ref-1");
        assertThat(result.newStatus()).isEqualTo(InsurancePremium.PremiumStatus.PAID);
    }

    @Test
    @DisplayName("setupAutoDebit returns the mandate from the provider")
    void setupAutoDebit_returnsResult() {
        InsuranceProvider.AutoDebitSetup setup =
                new InsuranceProvider.AutoDebitSetup("SAVINGS", "1234", "BANK", "Jane Doe",
                        LocalDate.now().plusMonths(1));
        when(providerRegistry.getProvider("aia-sg")).thenReturn(aiaProvider);
        when(aiaProvider.setupAutoDebit("aia-sg", "policy-1", setup))
                .thenReturn(new InsuranceProvider.AutoDebitResult(true, "mandate-1", null));

        InsuranceProvider.AutoDebitResult result = service.setupAutoDebit("aia-sg", "policy-1", setup);

        assertThat(result.success()).isTrue();
        assertThat(result.mandateReference()).isEqualTo("mandate-1");
    }

    @Test
    @DisplayName("cancelAutoDebit forwards to the provider")
    void cancelAutoDebit_callsProvider() {
        when(providerRegistry.getProvider("aia-sg")).thenReturn(aiaProvider);

        service.cancelAutoDebit("aia-sg", "policy-1");

        verify(aiaProvider).cancelAutoDebit("aia-sg", "policy-1");
    }
}
package com.omobio.insurance.service;

import com.omobio.platform.common.adapter.ApiAdapterRegistry;
import com.omobio.platform.common.adapter.InsuranceProvider;
import com.omobio.platform.common.domain.insurance.*;
import com.omobio.insurance.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InsuranceService.
 *
 * Verifies:
 * - getPolicies — provider-first with MongoDB fallback
 * - getPolicy — single policy lookup
 * - submitClaim — claim submission with provider-first strategy
 * - getClaimActivities — claim history
 * - getUpcomingPremiums — premium schedule
 * - getBeneficiaries — beneficiary list
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
    // submitClaim
    // ======================================================================

    @Test
    @DisplayName("submitClaim calls provider and returns result")
    void submitClaim_success() {
        InsuranceProvider.ClaimSubmission submission =
                InsuranceProvider.ClaimSubmission.builder()
                        .type("OUTPATIENT")
                        .description("Doctor visit")
                        .amount(500.0)
                        .build();
        InsuranceProvider.ClaimSubmitResult expected = InsuranceProvider.ClaimSubmitResult.builder()
                .success(true)
                .claimNumber("CLM-001")
                .status("SUBMITTED")
                .build();

        when(providerRegistry.getProvider("aia-sg")).thenReturn(aiaProvider);
        when(aiaProvider.submitClaim("aia-sg", "policy-1", submission))
                .thenReturn(expected);

        InsuranceProvider.ClaimSubmitResult result =
                service.submitClaim("aia-sg", "cust-a", "policy-1", submission);

        assertThat(result.success()).isTrue();
        assertThat(result.getClaimNumber()).isEqualTo("CLM-001");
    }

    @Test
    @DisplayName("submitClaim throws when provider fails")
    void submitClaim_providerFailure() {
        InsuranceProvider.ClaimSubmission submission =
                InsuranceProvider.ClaimSubmission.builder()
                        .type("INPATIENT")
                        .amount(5000.0)
                        .build();
        when(providerRegistry.getProvider("aia-sg")).thenReturn(aiaProvider);
        when(aiaProvider.submitClaim("aia-sg", "policy-1", submission))
                .thenThrow(new RuntimeException("Provider validation failed"));

        assertThatThrownBy(() -> service.submitClaim("aia-sg", "cust-a", "policy-1", submission))
                .isInstanceOf(RuntimeException.class);
    }

    // ======================================================================
    // getClaimActivities
    // ======================================================================

    @Test
    @DisplayName("getClaimActivities returns claim history from MongoDB")
    void getClaimActivities_returnsHistory() {
        InsuranceClaim claim = InsuranceClaim.builder()
                .id("claim-1")
                .claimNumber("CLM-001")
                .status(InsuranceClaim.ClaimStatus.IN_REVIEW)
                .build();
        when(claimRepository.findById("claim-1")).thenReturn(Optional.of(claim));

        InsuranceClaim result = service.getClaimActivities("aia-sg", "claim-1");

        assertThat(result.getId()).isEqualTo("claim-1");
        assertThat(result.getStatus()).isEqualTo(InsuranceClaim.ClaimStatus.IN_REVIEW);
    }

    @Test
    @DisplayName("getClaimActivities throws when claim not found")
    void getClaimActivities_notFound() {
        when(claimRepository.findById("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getClaimActivities("aia-sg", "unknown"))
                .isInstanceOf(com.omobio.platform.common.web.NotFoundException.class);
    }

    // ======================================================================
    // getUpcomingPremiums
    // ======================================================================

    @Test
    @DisplayName("getUpcomingPremiums returns from provider then caches")
    void getUpcomingPremiums_providerFirst() {
        InsurancePremium premium = InsurancePremium.builder()
                .id("prem-1")
                .policyId("policy-1")
                .dueDate(LocalDate.now().plusDays(15))
                .amount(120.0)
                .currency("SGD")
                .status("PENDING")
                .build();
        when(providerRegistry.getProvider("aia-sg")).thenReturn(aiaProvider);
        when(aiaProvider.getUpcomingPremiums("aia-sg", "policy-1"))
                .thenReturn(List.of(premium));
        when(premiumRepository.save(any(InsurancePremium.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        List<InsurancePremium> result = service.getUpcomingPremiums("aia-sg", "policy-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAmount()).isEqualTo(120.0);
        verify(premiumRepository).save(premium);
    }

    // ======================================================================
    // getBeneficiaries
    // ======================================================================

    @Test
    @DisplayName("getBeneficiaries returns beneficiaries for a policy")
    void getBeneficiaries_found() {
        InsuranceBeneficiary b1 = InsuranceBeneficiary.builder()
                .id("ben-1")
                .policyId("policy-1")
                .name("Jane Doe")
                .relationship("Spouse")
                .build();
        InsuranceBeneficiary b2 = InsuranceBeneficiary.builder()
                .id("ben-2")
                .policyId("policy-1")
                .name("Jack Doe")
                .relationship("Child")
                .build();
        when(beneficiaryRepository.findByPolicyId("policy-1"))
                .thenReturn(List.of(b1, b2));

        List<InsuranceBeneficiary> result = service.getBeneficiaries("aia-sg", "policy-1");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(InsuranceBeneficiary::getName)
                .containsExactly("Jane Doe", "Jack Doe");
    }

    // ======================================================================
    // renewal
    // ======================================================================

    @Test
    @DisplayName("initiateRenewal calls provider")
    void initiateRenewal_callsProvider() {
        InsuranceProvider.RenewalOffer offer = InsuranceProvider.RenewalOffer.builder()
                .policyId("policy-1")
                .newPremium(150.0)
                .newExpiryDate(LocalDate.now().plusYears(1))
                .options(List.of(
                        InsuranceProvider.RenewalOption.builder()
                                .optionId("opt-1")
                                .description("Keep current plan")
                                .premium(150.0)
                                .build()))
                .build();
        when(providerRegistry.getProvider("aia-sg")).thenReturn(aiaProvider);
        when(aiaProvider.initiateRenewal("aia-sg", "policy-1")).thenReturn(offer);

        InsuranceProvider.RenewalOffer result = service.initiateRenewal("aia-sg", "policy-1");

        assertThat(result.getNewPremium()).isEqualTo(150.0);
    }
}

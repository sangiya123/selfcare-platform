package com.omobio.insurance.web;

import com.omobio.platform.common.adapter.InsuranceProvider;
import com.omobio.platform.common.domain.insurance.*;
import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.ApiResponse;
import com.omobio.insurance.service.InsuranceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * REST API for insurance selfcare.
 *
 * Tenant comes from X-Tenant-Id header (set by TenantResolverFilter)
 * or from the JWT token. Customer comes from auth principal.
 *
 * All paths: /api/v1/insurance/**
 */
@RestController
@RequestMapping("/api/v1/insurance")
@RequiredArgsConstructor
public class InsuranceController {

    private final InsuranceService service;

    private String tenant() {
        return TenantContext.get().getTenantId();
    }

    // ================================================================
    // Policies
    // ================================================================

    @GetMapping("/policies")
    public ResponseEntity<ApiResponse<List<InsurancePolicy>>> listPolicies(
            @RequestParam String customerId) {
        return ResponseEntity.ok(ApiResponse.of(
                service.getPolicies(tenant(), customerId), ""));
    }

    @GetMapping("/policies/{policyId}")
    public ResponseEntity<ApiResponse<InsurancePolicy>> getPolicy(
            @RequestParam String customerId,
            @PathVariable String policyId) {
        return ResponseEntity.ok(ApiResponse.of(
                service.getPolicy(tenant(), customerId, policyId), ""));
    }

    @PostMapping("/policies/{policyId}/renewal/initiate")
    public ResponseEntity<ApiResponse<InsuranceProvider.RenewalOffer>> initiateRenewal(
            @PathVariable String policyId) {
        return ResponseEntity.ok(ApiResponse.of(
                service.initiateRenewal(tenant(), policyId), ""));
    }

    @PostMapping("/policies/{policyId}/renewal/confirm")
    public ResponseEntity<ApiResponse<InsuranceProvider.RenewalResult>> confirmRenewal(
            @PathVariable String policyId,
            @RequestBody ConfirmRenewalRequest req) {
        return ResponseEntity.ok(ApiResponse.of(
                service.confirmRenewal(tenant(), policyId, req.renewalOptionId()), ""));
    }

    // ================================================================
    // Claims
    // ================================================================

    @PostMapping("/policies/{policyId}/claims")
    public ResponseEntity<ApiResponse<InsuranceProvider.ClaimSubmitResult>> submitClaim(
            @RequestParam String customerId,
            @PathVariable String policyId,
            @RequestBody InsuranceProvider.ClaimSubmission submission) {
        return ResponseEntity.ok(ApiResponse.of(
                service.submitClaim(tenant(), customerId, policyId, submission), ""));
    }

    @GetMapping("/claims")
    public ResponseEntity<ApiResponse<List<InsuranceClaim>>> listClaims(
            @RequestParam String customerId) {
        return ResponseEntity.ok(ApiResponse.of(
                service.getClaims(tenant(), customerId), ""));
    }

    @GetMapping("/claims/{claimId}")
    public ResponseEntity<ApiResponse<InsuranceClaim>> getClaim(
            @RequestParam String customerId,
            @PathVariable String claimId) {
        return ResponseEntity.ok(ApiResponse.of(
                service.getClaim(tenant(), customerId, claimId), ""));
    }

    @GetMapping("/claims/{claimId}/activities")
    public ResponseEntity<ApiResponse<List<InsuranceClaim.ClaimActivity>>> getClaimActivities(
            @PathVariable String claimId) {
        return ResponseEntity.ok(ApiResponse.of(
                service.getClaimActivities(tenant(), claimId), ""));
    }

    @PostMapping("/claims/{claimId}/documents")
    public ResponseEntity<ApiResponse<InsuranceProvider.DocumentUploadResult>> uploadClaimDocument(
            @PathVariable String claimId,
            @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(ApiResponse.of(
                service.uploadClaimDocument(tenant(), claimId,
                        file.getOriginalFilename(), file.getBytes(),
                        file.getContentType() != null ? file.getContentType() : "application/octet-stream"),
                ""));
    }

    // ================================================================
    // Beneficiaries
    // ================================================================

    @GetMapping("/policies/{policyId}/beneficiaries")
    public ResponseEntity<ApiResponse<List<InsuranceBeneficiary>>> listBeneficiaries(
            @PathVariable String policyId) {
        return ResponseEntity.ok(ApiResponse.of(
                service.getBeneficiaries(tenant(), policyId), ""));
    }

    @PostMapping("/policies/{policyId}/beneficiaries")
    public ResponseEntity<ApiResponse<InsuranceProvider.BeneficiaryResult>> addBeneficiary(
            @PathVariable String policyId,
            @RequestBody InsuranceBeneficiary beneficiary) {
        return ResponseEntity.ok(ApiResponse.of(
                service.addBeneficiary(tenant(), policyId, beneficiary), ""));
    }

    @PutMapping("/beneficiaries/{beneficiaryId}")
    public ResponseEntity<ApiResponse<InsuranceProvider.BeneficiaryResult>> updateBeneficiary(
            @PathVariable String beneficiaryId,
            @RequestBody InsuranceBeneficiary updates) {
        return ResponseEntity.ok(ApiResponse.of(
                service.updateBeneficiary(tenant(), beneficiaryId, updates), ""));
    }

    @DeleteMapping("/beneficiaries/{beneficiaryId}")
    public ResponseEntity<ApiResponse<InsuranceProvider.BeneficiaryResult>> removeBeneficiary(
            @PathVariable String beneficiaryId) {
        return ResponseEntity.ok(ApiResponse.of(
                service.removeBeneficiary(tenant(), beneficiaryId), ""));
    }

    // ================================================================
    // Premium Payments
    // ================================================================

    @GetMapping("/policies/{policyId}/premiums")
    public ResponseEntity<ApiResponse<List<InsurancePremium>>> getPremiumSchedule(
            @PathVariable String policyId) {
        return ResponseEntity.ok(ApiResponse.of(
                service.getPremiumSchedule(tenant(), policyId), ""));
    }

    @GetMapping("/policies/{policyId}/premiums/next")
    public ResponseEntity<ApiResponse<InsurancePremium>> getNextDuePremium(
            @PathVariable String policyId) {
        return ResponseEntity.ok(ApiResponse.of(
                service.getNextDuePremium(tenant(), policyId), ""));
    }

    @PostMapping("/premiums/{premiumId}/pay")
    public ResponseEntity<ApiResponse<InsuranceProvider.PremiumPaymentResult>> payPremium(
            @PathVariable String premiumId,
            @RequestBody InsuranceProvider.PremiumPayment payment) {
        return ResponseEntity.ok(ApiResponse.of(
                service.payPremium(tenant(), premiumId, payment), ""));
    }

    @PostMapping("/policies/{policyId}/auto-debit")
    public ResponseEntity<ApiResponse<InsuranceProvider.AutoDebitResult>> setupAutoDebit(
            @PathVariable String policyId,
            @RequestBody InsuranceProvider.AutoDebitSetup setup) {
        return ResponseEntity.ok(ApiResponse.of(
                service.setupAutoDebit(tenant(), policyId, setup), ""));
    }

    @DeleteMapping("/policies/{policyId}/auto-debit")
    public ResponseEntity<ApiResponse<Void>> cancelAutoDebit(
            @PathVariable String policyId) {
        service.cancelAutoDebit(tenant(), policyId);
        return ResponseEntity.ok(ApiResponse.of(null, ""));
    }

    public record ConfirmRenewalRequest(String renewalOptionId) {}
}

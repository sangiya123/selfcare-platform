package com.selfcare.aia.provider;

import com.selfcare.platform.common.adapter.ApiAdapter;
import com.selfcare.platform.common.adapter.InsuranceProvider;
import com.selfcare.platform.common.adapter.RegisterAdapter;
import com.selfcare.platform.common.domain.insurance.*;
import com.selfcare.platform.common.tenant.TenantConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AIA Insurance Provider — implements InsuranceProvider for AIA markets.
 *
 * Covers the full AIA selfcare feature set:
 * - Policy list, details, and renewals
 * - Claims: file, track, upload documents
 * - Beneficiaries: add, update, remove
 * - Premium: schedule, pay, auto-debit
 *
 * Multi-country: handles all six AIA tenants:
 *   aia-lk  (Sri Lanka)
 *   aia-sg  (Singapore)
 *   aia-th  (Thailand)
 *   aia-my  (Malaysia)
 *   aia-hk  (Hong Kong)
 *   aia-in  (India)
 *
 * Per-tenant config (base URL, clientId, clientSecret, apiKey) is loaded
 * at runtime from {@link TenantConfigurationService} — NOT from env files.
 * Configure via Selfcare Studio admin: Integrations &gt; AIA Insurance.
 *
 * Uses OAuth2 client_credentials flow via {@link AIAHttpClient}, with
 * per-tenant token caching and a 5-minute proactive refresh window.
 *
 * Mock mode: when the upstream AIA API is unreachable (e.g. in dev or
 * integration test environments) the provider returns realistic
 * AIA-prefixed mock data tailored to the tenant's country.
 */
@Slf4j
@Component
@RegisterAdapter(tenantId = "aia-lk", providerInterface = InsuranceProvider.class)
@RequiredArgsConstructor
public class AIAInsuranceProvider implements InsuranceProvider {

    private static final String INTEGRATION_TYPE = "AIA_INSURANCE";

    private final TenantConfigurationService tenantConfig;
    private final AIAHttpClient httpClient;

    @Value("${selfcare.aia.mock-mode:true}")
    private boolean mockMode;

    @Value("${selfcare.tenant.default-id:aia-lk}")
    private String defaultTenantId;

    // Per-tenant clientId/clientSecret store (populated at request time)
    private final Map<String, Config> configCache = new ConcurrentHashMap<>();
    // Mock state per tenant (deterministic, AIA-prefixed IDs)
    private final Map<String, MockState> mockState = new ConcurrentHashMap<>();

    @Override
    public String getAdapterId() {
        return defaultTenantId;
    }

    // ================================================================
    // Configuration
    // ================================================================

    private Config loadConfig(String tenantId) {
        return configCache.computeIfAbsent(tenantId, this::resolveConfig);
    }

    private Config resolveConfig(String tenantId) {
        return tenantConfig.getIntegration(tenantId, INTEGRATION_TYPE)
                .filter(c -> "ACTIVE".equalsIgnoreCase(c.getStatus()))
                .map(c -> new Config(
                        tenantId,
                        c.getBaseUrl(),
                        c.getCredential("clientId"),
                        c.getCredential("clientSecret"),
                        c.getCredential("apiKey"),
                        c.getMetadata()))
                .orElseGet(() -> {
                    log.warn("No active AIA Insurance integration for tenant={}", tenantId);
                    return Config.empty(tenantId);
                });
    }

    // ================================================================
    // Policy Management
    // ================================================================

    @Override
    public List<InsurancePolicy> getPolicies(String tenantId, String customerId) {
        if (mockMode) {
            return MockData.getPolicies(tenantId, customerId, getOrCreateMockState(tenantId));
        }
        Config cfg = loadConfig(tenantId);
        if (!cfg.isValid()) return List.of();

        String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
        List<Map<String, Object>> data = httpClient.getList(tenantId, cfg.baseUrl(),
                "/api/v1/customer/" + customerId + "/policies", null, token);
        return data.stream().map(m -> mapToPolicy(tenantId, customerId, m)).toList();
    }

    @Override
    public InsurancePolicy getPolicy(String tenantId, String customerId, String policyId) {
        if (mockMode) {
            return MockData.getPolicy(tenantId, customerId, policyId, getOrCreateMockState(tenantId));
        }
        Config cfg = loadConfig(tenantId);
        if (!cfg.isValid()) return null;

        String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
        Map<String, Object> data = httpClient.get(tenantId, cfg.baseUrl(),
                "/api/v1/policies/" + policyId, null, token);
        return data != null ? mapToPolicy(tenantId, customerId, data) : null;
    }

    @Override
    public InsurancePolicy getPolicyByRef(String tenantId, String customerId, String insurerPolicyRef) {
        if (mockMode) {
            return MockData.getPolicyByRef(tenantId, customerId, insurerPolicyRef, getOrCreateMockState(tenantId));
        }
        Config cfg = loadConfig(tenantId);
        if (!cfg.isValid()) return null;

        String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
        Map<String, Object> data = httpClient.get(tenantId, cfg.baseUrl(),
                "/api/v1/customer/" + customerId + "/policies",
                Map.of("policyRef", insurerPolicyRef), token);
        return data != null ? mapToPolicy(tenantId, customerId, data) : null;
    }

    @Override
    public RenewalOffer initiateRenewal(String tenantId, String policyId) {
        if (mockMode) {
            return MockData.initiateRenewal(tenantId, policyId, getOrCreateMockState(tenantId));
        }
        Config cfg = loadConfig(tenantId);
        if (!cfg.isValid()) return null;

        String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
        Map<String, Object> data = httpClient.post(tenantId, cfg.baseUrl(),
                "/api/v1/policies/" + policyId + "/renewal/initiate", Map.of(), token);
        return data != null ? mapToRenewalOffer(policyId, data) : null;
    }

    @Override
    public RenewalResult confirmRenewal(String tenantId, String policyId, String renewalOption) {
        if (mockMode) {
            return MockData.confirmRenewal(tenantId, policyId, renewalOption, getOrCreateMockState(tenantId));
        }
        Config cfg = loadConfig(tenantId);
        if (!cfg.isValid()) {
            return new RenewalResult(false, null, "No AIA integration configured");
        }

        String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
        Map<String, Object> data = httpClient.post(tenantId, cfg.baseUrl(),
                "/api/v1/policies/" + policyId + "/renewal/confirm",
                Map.of("renewalOptionId", renewalOption, "confirmed", true), token);
        if (data != null && data.get("data") instanceof Map<?, ?> d) {
            return new RenewalResult(true, (String) d.get("newPolicyNumber"), null);
        }
        return new RenewalResult(false, null, "Unexpected response");
    }

    // ================================================================
    // Claims
    // ================================================================

    @Override
    public ClaimSubmitResult submitClaim(String tenantId, String policyId, ClaimSubmission submission) {
        if (mockMode) {
            return MockData.submitClaim(tenantId, policyId, submission, getOrCreateMockState(tenantId));
        }
        Config cfg = loadConfig(tenantId);
        if (!cfg.isValid()) {
            return new ClaimSubmitResult(false, null, null, null, "No AIA integration configured");
        }

        String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
        Map<String, Object> body = new HashMap<>();
        body.put("type", submission.type().name());
        body.put("incidentDate", submission.incidentDate() != null ? submission.incidentDate().toString() : null);
        body.put("description", submission.description());
        body.put("claimedAmount", submission.claimedAmount() != null ? submission.claimedAmount().toString() : null);
        if (submission.hospitalName() != null) body.put("hospitalName", submission.hospitalName());
        if (submission.admissionDate() != null) body.put("admissionDate", submission.admissionDate().toString());
        if (submission.dischargeDate() != null) body.put("dischargeDate", submission.dischargeDate().toString());
        if (submission.diagnosis() != null) body.put("diagnosis", submission.diagnosis());
        if (submission.vehicleRegNumber() != null) body.put("vehicleRegNumber", submission.vehicleRegNumber());
        if (submission.accidentLocation() != null) body.put("accidentLocation", submission.accidentLocation());
        if (submission.policeReportNumber() != null) body.put("policeReportNumber", submission.policeReportNumber());

        Map<String, Object> data = httpClient.post(tenantId, cfg.baseUrl(),
                "/api/v1/policies/" + policyId + "/claims", body, token);
        if (data != null && data.get("data") instanceof Map<?, ?> d) {
            return new ClaimSubmitResult(
                    true,
                    (String) d.get("claimId"),
                    (String) d.get("claimNumber"),
                    InsuranceClaim.ClaimStatus.SUBMITTED,
                    null);
        }
        return new ClaimSubmitResult(false, null, null, null, "Unexpected response");
    }

    @Override
    public List<InsuranceClaim> getClaims(String tenantId, String customerId) {
        if (mockMode) {
            return MockData.getClaims(tenantId, customerId, getOrCreateMockState(tenantId));
        }
        Config cfg = loadConfig(tenantId);
        if (!cfg.isValid()) return List.of();

        String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
        List<Map<String, Object>> data = httpClient.getList(tenantId, cfg.baseUrl(),
                "/api/v1/customer/" + customerId + "/claims", null, token);
        return data.stream().map(m -> mapToClaim(tenantId, customerId, m)).toList();
    }

    @Override
    public InsuranceClaim getClaim(String tenantId, String customerId, String claimId) {
        if (mockMode) {
            return MockData.getClaim(tenantId, customerId, claimId, getOrCreateMockState(tenantId));
        }
        Config cfg = loadConfig(tenantId);
        if (!cfg.isValid()) return null;

        String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
        Map<String, Object> data = httpClient.get(tenantId, cfg.baseUrl(),
                "/api/v1/claims/" + claimId, null, token);
        return data != null ? mapToClaim(tenantId, customerId, data) : null;
    }

    @Override
    public List<InsuranceClaim.ClaimActivity> getClaimActivities(String tenantId, String claimId) {
        if (mockMode) {
            return MockData.getClaimActivities(tenantId, claimId, getOrCreateMockState(tenantId));
        }
        Config cfg = loadConfig(tenantId);
        if (!cfg.isValid()) return List.of();

        String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
        List<Map<String, Object>> data = httpClient.getList(tenantId, cfg.baseUrl(),
                "/api/v1/claims/" + claimId + "/activities", null, token);
        return data.stream().map(m -> InsuranceClaim.ClaimActivity.builder()
                .timestamp(m.get("timestamp") != null ? Instant.parse((String) m.get("timestamp")) : Instant.now())
                .action((String) m.get("action"))
                .actor((String) m.get("actor"))
                .notes((String) m.get("notes"))
                .build()).toList();
    }

    @Override
    public DocumentUploadResult uploadClaimDocument(String tenantId, String claimId,
                                                    String fileName, byte[] content, String mimeType) {
        if (mockMode) {
            return MockData.uploadClaimDocument(tenantId, claimId, fileName, getOrCreateMockState(tenantId));
        }
        Config cfg = loadConfig(tenantId);
        if (!cfg.isValid()) {
            return new DocumentUploadResult(false, null, fileName, "No AIA integration configured");
        }

        String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
        // Multipart upload via raw HTTP
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(org.springframework.http.MediaType.parseMediaType(mimeType));
        headers.set("X-File-Name", Base64.getEncoder().encodeToString(fileName.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        try {
            String url = cfg.baseUrl() + "/api/v1/claims/" + claimId + "/documents";
            org.springframework.web.client.RestTemplate rt = new org.springframework.web.client.RestTemplate();
            org.springframework.http.HttpEntity<byte[]> req = new org.springframework.http.HttpEntity<>(content, headers);
            org.springframework.http.ResponseEntity<Map> resp = rt.exchange(
                    url, org.springframework.http.HttpMethod.POST, req, Map.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null
                    && resp.getBody().get("data") instanceof Map<?, ?> d) {
                return new DocumentUploadResult(true, (String) d.get("documentId"), fileName, null);
            }
        } catch (Exception e) {
            log.error("uploadClaimDocument failed for claimId={}, file={}: {}", claimId, fileName, e.getMessage());
            return new DocumentUploadResult(false, null, fileName, e.getMessage());
        }
        return new DocumentUploadResult(false, null, fileName, "Unexpected response");
    }

    // ================================================================
    // Beneficiaries
    // ================================================================

    @Override
    public List<InsuranceBeneficiary> getBeneficiaries(String tenantId, String policyId) {
        if (mockMode) {
            return MockData.getBeneficiaries(tenantId, policyId, getOrCreateMockState(tenantId));
        }
        Config cfg = loadConfig(tenantId);
        if (!cfg.isValid()) return List.of();

        String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
        List<Map<String, Object>> data = httpClient.getList(tenantId, cfg.baseUrl(),
                "/api/v1/policies/" + policyId + "/beneficiaries", null, token);
        return data.stream().map(m -> mapToBeneficiary(tenantId, m)).toList();
    }

    @Override
    public BeneficiaryResult addBeneficiary(String tenantId, String policyId, InsuranceBeneficiary beneficiary) {
        if (mockMode) {
            return MockData.addBeneficiary(tenantId, policyId, beneficiary, getOrCreateMockState(tenantId));
        }
        Config cfg = loadConfig(tenantId);
        if (!cfg.isValid()) {
            return new BeneficiaryResult(false, null, "No AIA integration configured");
        }

        String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
        Map<String, Object> data = httpClient.post(tenantId, cfg.baseUrl(),
                "/api/v1/policies/" + policyId + "/beneficiaries",
                beneficiaryToMap(beneficiary), token);
        if (data != null && data.get("data") instanceof Map<?, ?> d) {
            return new BeneficiaryResult(true, (String) d.get("endorsementStatus"), null);
        }
        return new BeneficiaryResult(false, null, "Unexpected response");
    }

    @Override
    public BeneficiaryResult updateBeneficiary(String tenantId, String beneficiaryId, InsuranceBeneficiary updates) {
        if (mockMode) {
            return MockData.updateBeneficiary(tenantId, beneficiaryId, updates, getOrCreateMockState(tenantId));
        }
        Config cfg = loadConfig(tenantId);
        if (!cfg.isValid()) {
            return new BeneficiaryResult(false, null, "No AIA integration configured");
        }

        String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
        Map<String, Object> data = httpClient.put(tenantId, cfg.baseUrl(),
                "/api/v1/beneficiaries/" + beneficiaryId, beneficiaryToMap(updates), token);
        if (data != null && data.get("data") instanceof Map<?, ?> d) {
            return new BeneficiaryResult(true, (String) d.get("endorsementStatus"), null);
        }
        return new BeneficiaryResult(false, null, "Unexpected response");
    }

    @Override
    public BeneficiaryResult removeBeneficiary(String tenantId, String beneficiaryId) {
        if (mockMode) {
            return MockData.removeBeneficiary(tenantId, beneficiaryId, getOrCreateMockState(tenantId));
        }
        Config cfg = loadConfig(tenantId);
        if (!cfg.isValid()) {
            return new BeneficiaryResult(false, null, "No AIA integration configured");
        }

        String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
        try {
            httpClient.delete(tenantId, cfg.baseUrl(),
                    "/api/v1/beneficiaries/" + beneficiaryId, token);
            return new BeneficiaryResult(true, "REMOVED", null);
        } catch (Exception e) {
            log.error("removeBeneficiary failed for beneficiaryId={}: {}", beneficiaryId, e.getMessage());
            return new BeneficiaryResult(false, null, e.getMessage());
        }
    }

    // ================================================================
    // Premium Payments
    // ================================================================

    @Override
    public List<InsurancePremium> getPremiumSchedule(String tenantId, String policyId) {
        if (mockMode) {
            return MockData.getPremiumSchedule(tenantId, policyId, getOrCreateMockState(tenantId));
        }
        Config cfg = loadConfig(tenantId);
        if (!cfg.isValid()) return List.of();

        String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
        List<Map<String, Object>> data = httpClient.getList(tenantId, cfg.baseUrl(),
                "/api/v1/policies/" + policyId + "/premiums", null, token);
        return data.stream().map(m -> mapToPremium(tenantId, m)).toList();
    }

    @Override
    public InsurancePremium getNextDuePremium(String tenantId, String policyId) {
        if (mockMode) {
            return MockData.getNextDuePremium(tenantId, policyId, getOrCreateMockState(tenantId));
        }
        Config cfg = loadConfig(tenantId);
        if (!cfg.isValid()) return null;

        String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
        Map<String, Object> data = httpClient.get(tenantId, cfg.baseUrl(),
                "/api/v1/policies/" + policyId + "/premiums/next", null, token);
        return data != null ? mapToPremium(tenantId, data) : null;
    }

    @Override
    public PremiumPaymentResult payPremium(String tenantId, String premiumId, PremiumPayment payment) {
        if (mockMode) {
            return MockData.payPremium(tenantId, premiumId, payment, getOrCreateMockState(tenantId));
        }
        Config cfg = loadConfig(tenantId);
        if (!cfg.isValid()) {
            return new PremiumPaymentResult(false, null, null, "No AIA integration configured");
        }

        String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
        Map<String, Object> body = new HashMap<>();
        body.put("method", payment.method());
        body.put("paymentToken", payment.paymentToken());
        body.put("amount", payment.amount() != null ? payment.amount().toString() : null);
        body.put("currency", payment.currency());

        Map<String, Object> data = httpClient.post(tenantId, cfg.baseUrl(),
                "/api/v1/premiums/" + premiumId + "/pay", body, token);
        if (data != null && data.get("data") instanceof Map<?, ?> d) {
            return new PremiumPaymentResult(
                    true,
                    (String) d.get("paymentReference"),
                    InsurancePremium.PremiumStatus.PAID,
                    null);
        }
        return new PremiumPaymentResult(false, null, null, "Unexpected response");
    }

    @Override
    public AutoDebitResult setupAutoDebit(String tenantId, String policyId, AutoDebitSetup setup) {
        if (mockMode) {
            return MockData.setupAutoDebit(tenantId, policyId, setup, getOrCreateMockState(tenantId));
        }
        Config cfg = loadConfig(tenantId);
        if (!cfg.isValid()) {
            return new AutoDebitResult(false, null, "No AIA integration configured");
        }

        String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
        Map<String, Object> body = new HashMap<>();
        body.put("accountType", setup.accountType());
        body.put("accountNumber", setup.accountNumber());
        body.put("bankCode", setup.bankCode());
        body.put("holderName", setup.holderName());
        body.put("firstDebitDate", setup.firstDebitDate() != null ? setup.firstDebitDate().toString() : null);

        Map<String, Object> data = httpClient.post(tenantId, cfg.baseUrl(),
                "/api/v1/policies/" + policyId + "/auto-debit", body, token);
        if (data != null && data.get("data") instanceof Map<?, ?> d) {
            return new AutoDebitResult(true, (String) d.get("mandateReference"), null);
        }
        return new AutoDebitResult(false, null, "Unexpected response");
    }

    @Override
    public void cancelAutoDebit(String tenantId, String policyId) {
        if (mockMode) {
            MockData.cancelAutoDebit(tenantId, policyId, getOrCreateMockState(tenantId));
            return;
        }
        Config cfg = loadConfig(tenantId);
        if (!cfg.isValid()) return;

        try {
            String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
            httpClient.delete(tenantId, cfg.baseUrl(),
                    "/api/v1/policies/" + policyId + "/auto-debit", token);
        } catch (Exception e) {
            log.error("cancelAutoDebit failed for policyId={}: {}", policyId, e.getMessage());
        }
    }

    // ================================================================
    // Mock State Management
    // ================================================================

    private MockState getOrCreateMockState(String tenantId) {
        return mockState.computeIfAbsent(tenantId, MockState::new);
    }

    // ================================================================
    // Mapping Helpers (live mode)
    // ================================================================

    private InsurancePolicy mapToPolicy(String tenantId, String customerId, Map<String, Object> m) {
        return InsurancePolicy.builder()
                .id((String) m.get("id"))
                .tenantId(tenantId)
                .customerId(customerId)
                .policyNumber((String) m.get("policyNumber"))
                .insurerPolicyRef((String) m.get("policyRef"))
                .type(parseEnum(InsurancePolicy.PolicyType.class, (String) m.get("type")))
                .status(parseEnum(InsurancePolicy.PolicyStatus.class, (String) m.get("status")))
                .insurerStatus((String) m.get("insurerStatus"))
                .holder(parseHolder((Map<?, ?>) m.get("holder")))
                .sumAssured(parseBd(m.get("sumAssured")))
                .premiumAmount(parseBd(m.get("premiumAmount")))
                .premiumFrequency((String) m.get("premiumFrequency"))
                .currency((String) m.getOrDefault("currency", "LKR"))
                .coverageAmount(parseBd(m.get("coverageAmount")))
                .coverageEndDate(parseLocalDate(m.get("coverageEndDate")))
                .maturityDate(parseLocalDate(m.get("maturityDate")))
                .planName((String) m.get("planName"))
                .planCode((String) m.get("planCode"))
                .productCode((String) m.get("productCode"))
                .accumulatedValue(parseBd(m.get("accumulatedValue")))
                .totalPremiumsPaid(parseBd(m.get("totalPremiumsPaid")))
                .lastPremiumPaid(parseBd(m.get("lastPremiumPaid")))
                .policyStartDate(parseLocalDate(m.get("policyStartDate")))
                .policyEndDate(parseLocalDate(m.get("policyEndDate")))
                .nextPremiumDueDate(parseLocalDate(m.get("nextPremiumDueDate")))
                .renewalDate(parseLocalDate(m.get("renewalDate")))
                .beneficiaryIds((List<String>) m.get("beneficiaryIds"))
                .metadata((Map<String, String>) m.get("metadata"))
                .build();
    }

    private InsuranceClaim mapToClaim(String tenantId, String customerId, Map<String, Object> m) {
        return InsuranceClaim.builder()
                .id((String) m.get("id"))
                .tenantId(tenantId)
                .customerId(customerId)
                .policyId((String) m.get("policyId"))
                .policyNumber((String) m.get("policyNumber"))
                .claimNumber((String) m.get("claimNumber"))
                .insurerClaimRef((String) m.get("claimRef"))
                .type(parseEnum(InsuranceClaim.ClaimType.class, (String) m.get("type")))
                .status(parseEnum(InsuranceClaim.ClaimStatus.class, (String) m.get("status")))
                .incidentDate(parseLocalDate(m.get("incidentDate")))
                .reportedDate(parseLocalDate(m.get("reportedDate")))
                .incidentDescription((String) m.get("description"))
                .claimedAmount(parseBd(m.get("claimedAmount")))
                .approvedAmount(parseBd(m.get("approvedAmount")))
                .paidAmount(parseBd(m.get("paidAmount")))
                .currency((String) m.getOrDefault("currency", "LKR"))
                .settlementMethod((String) m.get("settlementMethod"))
                .settlementReference((String) m.get("settlementReference"))
                .hospitalName((String) m.get("hospitalName"))
                .admissionDate(parseLocalDate(m.get("admissionDate")))
                .dischargeDate(parseLocalDate(m.get("dischargeDate")))
                .diagnosis((String) m.get("diagnosis"))
                .diagnosisCode((String) m.get("diagnosisCode"))
                .vehicleRegNumber((String) m.get("vehicleRegNumber"))
                .accidentLocation((String) m.get("accidentLocation"))
                .policeReportNumber((String) m.get("policeReportNumber"))
                .documentIds((List<String>) m.get("documentIds"))
                .rejectionReason((String) m.get("rejectionReason"))
                .build();
    }

    private InsuranceBeneficiary mapToBeneficiary(String tenantId, Map<String, Object> m) {
        return InsuranceBeneficiary.builder()
                .id((String) m.get("id"))
                .tenantId(tenantId)
                .customerId((String) m.get("customerId"))
                .policyId((String) m.get("policyId"))
                .fullName((String) m.get("fullName"))
                .nic((String) m.get("nic"))
                .dateOfBirth(parseLocalDate(m.get("dateOfBirth")))
                .gender((String) m.get("gender"))
                .relationship(parseEnum(InsuranceBeneficiary.Relationship.class, (String) m.get("relationship")))
                .allocationPercent(parseBd(m.get("allocationPercent")))
                .mobile((String) m.get("mobile"))
                .email((String) m.get("email"))
                .address((String) m.get("address"))
                .status((String) m.getOrDefault("status", "ACTIVE"))
                .build();
    }

    private InsurancePremium mapToPremium(String tenantId, Map<String, Object> m) {
        return InsurancePremium.builder()
                .id((String) m.get("id"))
                .tenantId(tenantId)
                .customerId((String) m.get("customerId"))
                .policyId((String) m.get("policyId"))
                .policyNumber((String) m.get("policyNumber"))
                .status(parseEnum(InsurancePremium.PremiumStatus.class, (String) m.get("status")))
                .dueAmount(parseBd(m.get("dueAmount")))
                .currency((String) m.getOrDefault("currency", "LKR"))
                .dueDate(parseLocalDate(m.get("dueDate")))
                .paidAmount(parseBd(m.get("paidAmount")))
                .paidDate(parseLocalDate(m.get("paidDate")))
                .paymentMethod((String) m.get("paymentMethod"))
                .paymentReference((String) m.get("paymentReference"))
                .autoDebit(Boolean.TRUE.equals(m.get("autoDebit")))
                .debitAccount((String) m.get("debitAccount"))
                .debitMethod((String) m.get("debitMethod"))
                .lateFee(parseBd(m.get("lateFee")))
                .insurerPremiumRef((String) m.get("premiumRef"))
                .gracePeriodDays(m.get("gracePeriodDays") != null ? ((Number) m.get("gracePeriodDays")).intValue() : 30)
                .build();
    }

    private RenewalOffer mapToRenewalOffer(String policyId, Map<String, Object> data) {
        if (data.get("data") instanceof Map<?, ?> d) {
            return new RenewalOffer(
                    (String) d.get("renewalOptionId"),
                    policyId,
                    d.get("newTermYears") instanceof Number n ? n.intValue() : 1,
                    parseBd(d.get("newPremiumAmount")),
                    parseBd(d.get("newSumAssured")),
                    parseLocalDate(d.get("effectiveDate")),
                    parseLocalDate(d.get("expiryDate")),
                    (Map<String, String>) d.get("options"));
        }
        return null;
    }

    private InsurancePolicy.PolicyHolder parseHolder(Map<?, ?> m) {
        if (m == null) return null;
        InsurancePolicy.PolicyHolder h = new InsurancePolicy.PolicyHolder();
        h.setCustomerId((String) m.get("customerId"));
        h.setFullName((String) m.get("fullName"));
        h.setNic((String) m.get("nic"));
        h.setDateOfBirth((String) m.get("dateOfBirth"));
        h.setGender((String) m.get("gender"));
        h.setMobile((String) m.get("mobile"));
        h.setEmail((String) m.get("email"));
        h.setAddress((String) m.get("address"));
        return h;
    }

    private Map<String, Object> beneficiaryToMap(InsuranceBeneficiary b) {
        Map<String, Object> m = new HashMap<>();
        if (b.getFullName() != null) m.put("fullName", b.getFullName());
        if (b.getNic() != null) m.put("nic", b.getNic());
        if (b.getDateOfBirth() != null) m.put("dateOfBirth", b.getDateOfBirth().toString());
        if (b.getGender() != null) m.put("gender", b.getGender());
        if (b.getRelationship() != null) m.put("relationship", b.getRelationship().name());
        if (b.getAllocationPercent() != null) m.put("allocationPercent", b.getAllocationPercent().toString());
        if (b.getMobile() != null) m.put("mobile", b.getMobile());
        if (b.getEmail() != null) m.put("email", b.getEmail());
        if (b.getAddress() != null) m.put("address", b.getAddress());
        return m;
    }

    private <E extends Enum<E>> E parseEnum(Class<E> clazz, String value) {
        if (value == null) return null;
        try { return Enum.valueOf(clazz, value.toUpperCase().replace("-", "_")); }
        catch (Exception e) { return null; }
    }

    private BigDecimal parseBd(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(v.toString()); } catch (Exception e) { return null; }
    }

    private LocalDate parseLocalDate(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDate ld) return ld;
        if (v instanceof String s) {
            try { return LocalDate.parse(s); } catch (Exception e) { return null; }
        }
        return null;
    }

    // ================================================================
    // Internal Types
    // ================================================================

    /**
     * Per-tenant integration config resolved at runtime.
     */
    public record Config(String tenantId, String baseUrl, String clientId,
                         String clientSecret, String apiKey, Map<String, String> metadata) {
        public boolean isValid() { return baseUrl != null && !baseUrl.isEmpty(); }
        public static Config empty(String tenantId) {
            return new Config(tenantId, null, null, null, null, null);
        }
    }

    /**
     * Mock state container — per tenant, holds in-memory policies, claims, etc.
     * Used when {@code selfcare.aia.mock-mode=true} (default in dev/test).
     */
    public static class MockState {
        final String tenantId;
        final String currency;
        final String countryName;
        final String phonePrefix;
        final AtomicInteger policySeq = new AtomicInteger(1000);
        final AtomicInteger claimSeq = new AtomicInteger(5000);
        final AtomicInteger benefSeq = new AtomicInteger(9000);
        final AtomicInteger premiumSeq = new AtomicInteger(7000);
        final AtomicInteger docSeq = new AtomicInteger(8000);
        final AtomicInteger mandateSeq = new AtomicInteger(8500);
        final Map<String, InsurancePolicy> policies = new ConcurrentHashMap<>();
        final Map<String, InsuranceClaim> claims = new ConcurrentHashMap<>();
        final Map<String, InsuranceBeneficiary> beneficiaries = new ConcurrentHashMap<>();
        final Map<String, InsurancePremium> premiums = new ConcurrentHashMap<>();
        final Map<String, Boolean> autoDebit = new ConcurrentHashMap<>();

        MockState(String tenantId) {
            this.tenantId = tenantId;
            switch (tenantId) {
                case "aia-sg" -> {
                    this.currency = "SGD";
                    this.countryName = "Singapore";
                    this.phonePrefix = "+65";
                }
                case "aia-th" -> {
                    this.currency = "THB";
                    this.countryName = "Thailand";
                    this.phonePrefix = "+66";
                }
                case "aia-my" -> {
                    this.currency = "MYR";
                    this.countryName = "Malaysia";
                    this.phonePrefix = "+60";
                }
                case "aia-hk" -> {
                    this.currency = "HKD";
                    this.countryName = "Hong Kong";
                    this.phonePrefix = "+852";
                }
                case "aia-in" -> {
                    this.currency = "INR";
                    this.countryName = "India";
                    this.phonePrefix = "+91";
                }
                default -> {
                    this.currency = "LKR";
                    this.countryName = "Sri Lanka";
                    this.phonePrefix = "+94";
                }
            }
        }
    }
}

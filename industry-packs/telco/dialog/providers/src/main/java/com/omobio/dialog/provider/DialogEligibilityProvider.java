package com.omobio.dialog.provider;

import com.omobio.platform.common.adapter.ApiAdapter;
import com.omobio.platform.common.adapter.EligibilityProvider;
import com.omobio.platform.common.adapter.RegisterAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dialog Eligibility Provider — checks product / plan eligibility from Dialog BSS.
 *
 * <p>Implements the canonical {@link EligibilityProvider} contract from platform-common.
 * Returns eligibility status for products, plan migrations, and device financing.</p>
 *
 * <p>Per-tenant config (BSS base URL, API key) is loaded from MongoDB via
 * {@link DialogHttpClient}. Configure via Selfcare Studio admin:
 * Integrations &gt; Dialog BSS.</p>
 */
@Slf4j
@Component
@RegisterAdapter(tenantId = "dialog-lk", providerInterface = EligibilityProvider.class)
@RequiredArgsConstructor
public class DialogEligibilityProvider implements ApiAdapter, EligibilityProvider {

    private static final String INTEGRATION_TYPE = "DIALOG_BSS";
    private static final String ADAPTER_ID = "dialog-lk";

    private final DialogHttpClient httpClient;

    @Override
    public String getAdapterId() {
        return ADAPTER_ID;
    }

    // ================================================================
    // EligibilityProvider implementation
    // ================================================================

    @Override
    public EligibilityResult checkEligibility(String tenantId, String connectionId,
                                           String productCode, String offerCode) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Dialog BSS not configured for tenant={}", tenantId);
            return new EligibilityResult(false, connectionId, productCode, offerCode,
                    "NOT_FOUND", "No Dialog BSS integration configured", List.of(), Map.of());
        }

        Map<String, Object> body = new HashMap<>();
        body.put("subscriberId", connectionId);
        body.put("productCode", productCode);
        if (offerCode != null) body.put("offerCode", offerCode);

        try {
            Map<String, Object> data = httpClient.post(tenantId, cfg.baseUrl(),
                    "/subscriber/eligibility/check", body, null, cfg.apiKey());
            if (data == null) {
                return new EligibilityResult(false, connectionId, productCode, offerCode,
                        "NOT_FOUND", "Empty BSS response", List.of(), Map.of());
            }
            boolean eligible = Boolean.TRUE.equals(data.get("eligible"));
            Object blockersObj = data.get("blockers");
            List<String> blockers = blockersObj instanceof List<?> l
                    ? l.stream().map(Object::toString).toList() : List.of();
            Object metaObj = data.get("metadata");
            Map<String, String> meta = metaObj instanceof Map<?, ?> m
                    ? mapStrings(m) : Map.of();
            return new EligibilityResult(
                    eligible,
                    connectionId,
                    productCode,
                    offerCode,
                    str(data.get("status"), eligible ? "ELIGIBLE" : "INELIGIBLE"),
                    str(data.get("reason"), null),
                    blockers,
                    meta
            );
        } catch (DialogApiException e) {
            log.error("Dialog checkEligibility failed for tenant={} connection={} product={}: {}",
                    tenantId, connectionId, productCode, e.getMessage());
            return new EligibilityResult(false, connectionId, productCode, offerCode,
                    "FAILED", e.getMessage(), List.of(), Map.of());
        }
    }

    @Override
    public List<EligibleProduct> getEligibleProducts(String tenantId, String connectionId) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Dialog BSS not configured for tenant={}", tenantId);
            return List.of();
        }

        String path = "/subscriber/" + connectionId + "/eligible-products";
        try {
            Map<String, Object> data = httpClient.get(tenantId, cfg.baseUrl(), path, null, null, cfg.apiKey());
            if (data == null) return List.of();
            Object productsObj = data.get("products");
            if (!(productsObj instanceof List<?> list)) return List.of();
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .<EligibleProduct>map(m -> new EligibleProduct(
                            str(m.get("productCode"), null),
                            str(m.get("productName"), null),
                            str(m.get("productCategory"), null),
                            Boolean.TRUE.equals(m.get("hasOffer")),
                            str(m.get("offerCode"), null),
                            parseInstant(m.get("offerExpiry")),
                            parseStringMap(m.get("eligibilityMetadata"))
                    ))
                    .toList();
        } catch (DialogApiException e) {
            log.error("Dialog getEligibleProducts failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public MigrationEligibility checkMigrationEligibility(String tenantId, String connectionId,
                                                        String targetPlanCode) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Dialog BSS not configured for tenant={}", tenantId);
            return new MigrationEligibility(connectionId, null, targetPlanCode, false,
                    "No Dialog BSS integration", null, null, null, List.of());
        }

        Map<String, Object> body = new HashMap<>();
        body.put("subscriberId", connectionId);
        body.put("targetPlanCode", targetPlanCode);

        try {
            Map<String, Object> data = httpClient.post(tenantId, cfg.baseUrl(),
                    "/subscriber/plan/migration/eligibility", body, null, cfg.apiKey());
            if (data == null) {
                return new MigrationEligibility(connectionId, null, targetPlanCode, false,
                        "Empty BSS response", null, null, null, List.of());
            }
            boolean eligible = Boolean.TRUE.equals(data.get("eligible"));
            Object conditionsObj = data.get("migrationConditions");
            List<String> conditions = conditionsObj instanceof List<?> l
                    ? l.stream().map(Object::toString).toList() : List.of();
            return new MigrationEligibility(
                    connectionId,
                    str(data.get("currentPlanCode"), null),
                    targetPlanCode,
                    eligible,
                    str(data.get("reason"), null),
                    toBd(data.get("migrationFee")),
                    toBd(data.get("newPlanPrice")),
                    parseInstant(data.get("effectiveDate")),
                    conditions
            );
        } catch (DialogApiException e) {
            log.error("Dialog checkMigrationEligibility failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return new MigrationEligibility(connectionId, null, targetPlanCode, false,
                    e.getMessage(), null, null, null, List.of());
        }
    }

    @Override
    public FinancingEligibility checkFinancingEligibility(String tenantId, String connectionId,
                                                        String deviceId) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Dialog BSS not configured for tenant={}", tenantId);
            return new FinancingEligibility(connectionId, deviceId, false,
                    "No Dialog BSS integration", null, 0, null, null);
        }

        var queryParams = Map.<String, Object>of("deviceId", deviceId);
        String path = "/subscriber/" + connectionId + "/financing/eligibility";
        try {
            Map<String, Object> data = httpClient.get(tenantId, cfg.baseUrl(), path, queryParams, null, cfg.apiKey());
            if (data == null) {
                return new FinancingEligibility(connectionId, deviceId, false, "Empty BSS response",
                        null, 0, null, null);
            }
            return new FinancingEligibility(
                    connectionId,
                    deviceId,
                    Boolean.TRUE.equals(data.get("eligible")),
                    str(data.get("reason"), null),
                    toBd(data.get("monthlyInstallment")),
                    toInteger(data.get("installments")),
                    toBd(data.get("downPayment")),
                    str(data.get("financingPartner"), null)
            );
        } catch (DialogApiException e) {
            log.error("Dialog checkFinancingEligibility failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return new FinancingEligibility(connectionId, deviceId, false, e.getMessage(),
                    null, 0, null, null);
        }
    }

    // ================================================================
    // Helpers
    // ================================================================

    private String str(Object v, String fallback) {
        return v != null ? v.toString() : fallback;
    }

    private BigDecimal toBd(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(v.toString()); } catch (NumberFormatException e) { return null; }
    }

    private Integer toInteger(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        return null;
    }

    private Instant parseInstant(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return Instant.ofEpochSecond(n.longValue());
        if (v instanceof String s) {
            try { return Instant.parse(s); } catch (Exception ignored) {}
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> mapStrings(Map<?, ?> m) {
        return m.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        e -> String.valueOf(e.getKey()),
                        e -> String.valueOf(e.getValue())
                ));
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseStringMap(Object v) {
        if (v instanceof Map<?, ?> m) return mapStrings(m);
        return Map.of();
    }
}

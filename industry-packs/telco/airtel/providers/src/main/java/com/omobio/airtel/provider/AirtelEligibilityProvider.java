package com.omobio.airtel.provider;

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
import java.util.stream.Collectors;

/**
 * Airtel Eligibility Provider — checks product / plan eligibility from Airtel BSS.
 *
 * <p>Implements the canonical {@link EligibilityProvider} contract from platform-common.
 * Airtel uses API-key authentication and wraps responses in a {@code { data: {...} }}
 * envelope.</p>
 *
 * <p>Per-tenant config loaded from MongoDB via {@link AirtelHttpClient}.
 * Configure via Selfcare Studio admin: Integrations &gt; Airtel Gateway.</p>
 */
@Slf4j
@Component
@RegisterAdapter(tenantId = "airtel-lk", providerInterface = EligibilityProvider.class)
@RequiredArgsConstructor
public class AirtelEligibilityProvider implements ApiAdapter, EligibilityProvider {

    private static final String INTEGRATION_TYPE = "AIRTEL_BSS";
    private static final String ADAPTER_ID = "airtel-lk";

    private final AirtelHttpClient httpClient;

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
        AirtelHttpClient.AirtelConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Airtel BSS not configured for tenant={}", tenantId);
            return new EligibilityResult(false, connectionId, productCode, offerCode,
                    "NOT_FOUND", "No Airtel BSS integration configured", List.of(), Map.of());
        }

        Map<String, Object> body = new HashMap<>();
        body.put("subscriberId", connectionId);
        body.put("productCode", productCode);
        if (offerCode != null) body.put("offerCode", offerCode);

        try {
            Map<String, Object> resp = httpClient.post(tenantId, cfg,
                    "/v1/osp/eligibility/check", body, null);
            if (resp == null) {
                return new EligibilityResult(false, connectionId, productCode, offerCode,
                        "NOT_FOUND", "Empty Airtel BSS response", List.of(), Map.of());
            }
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> d)) {
                return new EligibilityResult(false, connectionId, productCode, offerCode,
                        "NOT_FOUND", "Unexpected Airtel BSS response shape", List.of(), Map.of());
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) d;
            boolean eligible = Boolean.TRUE.equals(m.get("eligible"));
            Object blockersObj = m.get("blockers");
            List<String> blockers = blockersObj instanceof List<?> l
                    ? l.stream().map(Object::toString).toList() : List.of();
            Object metaObj = m.get("metadata");
            Map<String, String> meta = metaObj instanceof Map<?, ?> mm
                    ? mapStrings(mm) : Map.of();
            return new EligibilityResult(
                    eligible,
                    connectionId,
                    productCode,
                    offerCode,
                    str(m.get("status"), eligible ? "ELIGIBLE" : "INELIGIBLE"),
                    str(m.get("reason"), null),
                    blockers,
                    meta
            );
        } catch (AirtelApiException e) {
            log.error("Airtel checkEligibility failed for tenant={} connection={} product={}: {}",
                    tenantId, connectionId, productCode, e.getMessage());
            return new EligibilityResult(false, connectionId, productCode, offerCode,
                    "FAILED", e.getMessage(), List.of(), Map.of());
        }
    }

    @Override
    public List<EligibleProduct> getEligibleProducts(String tenantId, String connectionId) {
        AirtelHttpClient.AirtelConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Airtel BSS not configured for tenant={}", tenantId);
            return List.of();
        }

        String path = "/v1/osp/subscribers/" + connectionId + "/eligible-products";
        try {
            Map<String, Object> resp = httpClient.get(tenantId, cfg, path, null, null);
            if (resp == null) return List.of();
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> d)) return List.of();
            Object productsObj = d.get("products");
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
        } catch (AirtelApiException e) {
            log.error("Airtel getEligibleProducts failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public MigrationEligibility checkMigrationEligibility(String tenantId, String connectionId,
                                                     String targetPlanCode) {
        AirtelHttpClient.AirtelConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Airtel BSS not configured for tenant={}", tenantId);
            return new MigrationEligibility(connectionId, null, targetPlanCode, false,
                    "No Airtel BSS integration", null, null, null, List.of());
        }

        Map<String, Object> body = new HashMap<>();
        body.put("subscriberId", connectionId);
        body.put("targetPlanCode", targetPlanCode);

        try {
            Map<String, Object> resp = httpClient.post(tenantId, cfg,
                    "/v1/osp/plan/migration/eligibility", body, null);
            if (resp == null) {
                return new MigrationEligibility(connectionId, null, targetPlanCode, false,
                        "Empty Airtel BSS response", null, null, null, List.of());
            }
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> d)) {
                return new MigrationEligibility(connectionId, null, targetPlanCode, false,
                        "Unexpected response shape", null, null, null, List.of());
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) d;
            boolean eligible = Boolean.TRUE.equals(m.get("eligible"));
            Object conditionsObj = m.get("migrationConditions");
            List<String> conditions = conditionsObj instanceof List<?> l
                    ? l.stream().map(Object::toString).toList() : List.of();
            return new MigrationEligibility(
                    connectionId,
                    str(m.get("currentPlanCode"), null),
                    targetPlanCode,
                    eligible,
                    str(m.get("reason"), null),
                    toBd(m.get("migrationFee")),
                    toBd(m.get("newPlanPrice")),
                    parseInstant(m.get("effectiveDate")),
                    conditions
            );
        } catch (AirtelApiException e) {
            log.error("Airtel checkMigrationEligibility failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return new MigrationEligibility(connectionId, null, targetPlanCode, false,
                    e.getMessage(), null, null, null, List.of());
        }
    }

    @Override
    public FinancingEligibility checkFinancingEligibility(String tenantId, String connectionId,
                                                     String deviceId) {
        AirtelHttpClient.AirtelConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Airtel BSS not configured for tenant={}", tenantId);
            return new FinancingEligibility(connectionId, deviceId, false,
                    "No Airtel BSS integration", null, 0, null, null);
        }

        var queryParams = Map.<String, Object>of("deviceId", deviceId);
        String path = "/v1/osp/subscribers/" + connectionId + "/financing/eligibility";
        try {
            Map<String, Object> resp = httpClient.get(tenantId, cfg, path, queryParams, null);
            if (resp == null) {
                return new FinancingEligibility(connectionId, deviceId, false, "Empty Airtel BSS response",
                        null, 0, null, null);
            }
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> d)) {
                return new FinancingEligibility(connectionId, deviceId, false, "Unexpected response shape",
                        null, 0, null, null);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) d;
            return new FinancingEligibility(
                    connectionId,
                    deviceId,
                    Boolean.TRUE.equals(m.get("eligible")),
                    str(m.get("reason"), null),
                    toBd(m.get("monthlyInstallment")),
                    toInteger(m.get("installments")),
                    toBd(m.get("downPayment")),
                    str(m.get("financingPartner"), null)
            );
        } catch (AirtelApiException e) {
            log.error("Airtel checkFinancingEligibility failed for tenant={} connection={}: {}",
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
                .collect(Collectors.toMap(
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

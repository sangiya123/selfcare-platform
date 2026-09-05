package com.omobio.airtel.provider;

import com.omobio.platform.common.adapter.ActivationProvider;
import com.omobio.platform.common.adapter.ApiAdapter;
import com.omobio.platform.common.adapter.RegisterAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Airtel Activation Provider — activates and deactivates packages / VAS on Airtel BSS.
 *
 * <p>Implements the canonical {@link ActivationProvider} contract from platform-common.
 * Airtel uses API-key authentication for BSS endpoints and wraps responses
 * in a {@code { data: { ... } }} envelope.</p>
 *
 * <p>Per-tenant config loaded from MongoDB via {@link AirtelHttpClient}.
 * Configure via Selfcare Studio admin: Integrations &gt; Airtel Gateway.</p>
 */
@Slf4j
@Component
@RegisterAdapter(tenantId = "airtel-lk", providerInterface = ActivationProvider.class)
@RequiredArgsConstructor
public class AirtelActivationProvider implements ApiAdapter, ActivationProvider {

    private static final String INTEGRATION_TYPE = "AIRTEL_BSS";
    private static final String ADAPTER_ID = "airtel-lk";

    private final AirtelHttpClient httpClient;

    @Override
    public String getAdapterId() {
        return ADAPTER_ID;
    }

    // ================================================================
    // ActivationProvider implementation
    // ================================================================

    @Override
    public ActivationResult activate(String tenantId, String connectionId, String productCode) {
        AirtelHttpClient.AirtelConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Airtel BSS not configured for tenant={}", tenantId);
            return new ActivationResult(false, null, connectionId, productCode,
                    ActivationStatusCode.FAILED, null, null, "No Airtel BSS integration");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("subscriberId", connectionId);
        body.put("productCode", productCode);
        body.put("action", "ACTIVATE");

        try {
            Map<String, Object> resp = httpClient.post(tenantId, cfg,
                    "/v1/osp/packages/activate", body, null);
            if (resp == null) {
                return new ActivationResult(false, null, connectionId, productCode,
                        ActivationStatusCode.FAILED, null, null, "Empty response from Airtel BSS");
            }
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> d)) {
                return new ActivationResult(false, null, connectionId, productCode,
                        ActivationStatusCode.FAILED, null, null, "Unexpected Airtel BSS response shape");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) d;
            String status = str(data.get("status"), "UNKNOWN");
            ActivationStatusCode mapped;
            try { mapped = ActivationStatusCode.valueOf(status.toUpperCase()); }
            catch (Exception e) { mapped = ActivationStatusCode.PENDING; }
            boolean success = "SUCCESS".equals(status);
            return new ActivationResult(
                    success,
                    str(data.get("activationId"), null),
                    connectionId,
                    productCode,
                    mapped,
                    parseInstant(data.get("activatedAt")),
                    parseInstant(data.get("expiresAt")),
                    success ? null : "Airtel BSS error: " + status
            );
        } catch (AirtelApiException e) {
            log.error("Airtel activate failed for tenant={} connection={} product={}: {}",
                    tenantId, connectionId, productCode, e.getMessage());
            return new ActivationResult(false, null, connectionId, productCode,
                    ActivationStatusCode.FAILED, null, null, e.getMessage());
        }
    }

    @Override
    public ActivationResult deactivate(String tenantId, String connectionId, String productCode) {
        AirtelHttpClient.AirtelConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Airtel BSS not configured for tenant={}", tenantId);
            return new ActivationResult(false, null, connectionId, productCode,
                    ActivationStatusCode.FAILED, null, null, "No Airtel BSS integration");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("subscriberId", connectionId);
        body.put("productCode", productCode);
        body.put("action", "DEACTIVATE");

        try {
            Map<String, Object> resp = httpClient.post(tenantId, cfg,
                    "/v1/osp/packages/deactivate", body, null);
            if (resp == null) {
                return new ActivationResult(false, null, connectionId, productCode,
                        ActivationStatusCode.FAILED, null, null, "Empty response from Airtel BSS");
            }
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> d)) {
                return new ActivationResult(false, null, connectionId, productCode,
                        ActivationStatusCode.FAILED, null, null, "Unexpected Airtel BSS response shape");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) d;
            String status = str(data.get("status"), "UNKNOWN");
            ActivationStatusCode mapped;
            try { mapped = ActivationStatusCode.valueOf(status.toUpperCase()); }
            catch (Exception e) { mapped = ActivationStatusCode.PENDING; }
            boolean success = "SUCCESS".equals(status);
            return new ActivationResult(
                    success,
                    str(data.get("deactivationId"), null),
                    connectionId,
                    productCode,
                    mapped,
                    parseInstant(data.get("deactivatedAt")),
                    null,
                    success ? null : "Airtel BSS error: " + status
            );
        } catch (AirtelApiException e) {
            log.error("Airtel deactivate failed for tenant={} connection={} product={}: {}",
                    tenantId, connectionId, productCode, e.getMessage());
            return new ActivationResult(false, null, connectionId, productCode,
                    ActivationStatusCode.FAILED, null, null, e.getMessage());
        }
    }

    @Override
    public List<ActivePackage> getActivePackages(String tenantId, String connectionId) {
        AirtelHttpClient.AirtelConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Airtel BSS not configured for tenant={}", tenantId);
            return List.of();
        }

        String path = "/v1/osp/subscribers/" + connectionId + "/packages";
        try {
            Map<String, Object> resp = httpClient.get(tenantId, cfg, path, null, null);
            if (resp == null) return List.of();
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> d)) return List.of();
            Object packagesObj = d.get("packages");
            if (!(packagesObj instanceof List<?> list)) return List.of();
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .<ActivePackage>map(m -> new ActivePackage(
                            str(m.get("packageId"), null),
                            connectionId,
                            str(m.get("productCode"), null),
                            str(m.get("packageName"), null),
                            parseInstant(m.get("activatedAt")),
                            parseInstant(m.get("expiresAt")),
                            str(m.get("status"), "ACTIVE"),
                            Boolean.TRUE.equals(m.get("autoRenew"))
                    ))
                    .toList();
        } catch (AirtelApiException e) {
            log.error("Airtel getActivePackages failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public ActivationStatus getActivationStatus(String tenantId, String activationId) {
        AirtelHttpClient.AirtelConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Airtel BSS not configured for tenant={}", tenantId);
            return new ActivationStatus(activationId, null, ActivationStatusCode.FAILED, null, null,
                    "No Airtel BSS integration");
        }

        String path = "/v1/osp/packages/activation/" + activationId;
        try {
            Map<String, Object> resp = httpClient.get(tenantId, cfg, path, null, null);
            if (resp == null) {
                return new ActivationStatus(activationId, null, ActivationStatusCode.PENDING, null, null, null);
            }
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> d)) {
                return new ActivationStatus(activationId, null, ActivationStatusCode.PENDING, null, null, null);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) d;
            return new ActivationStatus(
                    activationId,
                    str(data.get("productCode"), null),
                    parseActivationStatus(str(data.get("status"), null)),
                    parseInstant(data.get("activatedAt")),
                    parseInstant(data.get("expiresAt")),
                    str(data.get("failureReason"), null)
            );
        } catch (AirtelApiException e) {
            log.error("Airtel getActivationStatus failed for tenant={} activation={}: {}",
                    tenantId, activationId, e.getMessage());
            return new ActivationStatus(activationId, null, ActivationStatusCode.FAILED, null, null, e.getMessage());
        }
    }

    // ================================================================
    // Helpers
    // ================================================================

    private String str(Object v, String fallback) {
        return v != null ? v.toString() : fallback;
    }

    private Instant parseInstant(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return Instant.ofEpochSecond(n.longValue());
        if (v instanceof String s) {
            try { return Instant.parse(s); } catch (Exception ignored) {}
        }
        return null;
    }

    private ActivationStatusCode parseActivationStatus(String s) {
        if (s == null) return ActivationStatusCode.PENDING;
        try { return ActivationStatusCode.valueOf(s.toUpperCase()); }
        catch (Exception e) { return ActivationStatusCode.PENDING; }
    }
}

package com.selfcare.hutch.provider;

import com.selfcare.platform.common.adapter.ActivationProvider;
import com.selfcare.platform.common.adapter.ApiAdapter;
import com.selfcare.platform.common.adapter.RegisterAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hutch Activation Provider — activates and deactivates packages / VAS on Hutch BSS.
 *
 * <p>Implements the canonical {@link ActivationProvider} contract from platform-common.
 * Hutch uses API-key authentication.</p>
 *
 * <p>Per-tenant config (BSS base URL, API key) loaded from MongoDB via
 * {@link HutchHttpClient}. Configure via Selfcare Studio admin:
 * Integrations &gt; Hutch BSS.</p>
 */
@Slf4j
@Component
@RegisterAdapter(tenantId = "hutch-lk", providerInterface = ActivationProvider.class)
@RequiredArgsConstructor
public class HutchActivationProvider implements ApiAdapter, ActivationProvider {

    private static final String INTEGRATION_TYPE = "HUTCH_BSS";
    private static final String ADAPTER_ID = "hutch-lk";

    private final HutchHttpClient httpClient;

    @Override
    public String getAdapterId() {
        return ADAPTER_ID;
    }

    // ================================================================
    // ActivationProvider implementation
    // ================================================================

    @Override
    public ActivationResult activate(String tenantId, String connectionId, String productCode) {
        HutchHttpClient.HutchConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Hutch BSS not configured for tenant={}", tenantId);
            return new ActivationResult(false, null, connectionId, productCode,
                    ActivationStatusCode.FAILED, null, null, "No Hutch BSS integration");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("msisdn", connectionId);
        body.put("productCode", productCode);
        body.put("action", "ACTIVATE");

        try {
            Map<String, Object> resp = httpClient.post(tenantId, cfg,
                    "/api/v1/packages/activate", body, null);
            if (resp == null) {
                return new ActivationResult(false, null, connectionId, productCode,
                        ActivationStatusCode.FAILED, null, null, "Empty response from Hutch BSS");
            }
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> d)) {
                return new ActivationResult(false, null, connectionId, productCode,
                        ActivationStatusCode.FAILED, null, null, "Unexpected Hutch BSS response shape");
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
                    success ? null : "Hutch BSS error: " + status
            );
        } catch (HutchApiException e) {
            log.error("Hutch activate failed for tenant={} connection={} product={}: {}",
                    tenantId, connectionId, productCode, e.getMessage());
            return new ActivationResult(false, null, connectionId, productCode,
                    ActivationStatusCode.FAILED, null, null, e.getMessage());
        }
    }

    @Override
    public ActivationResult deactivate(String tenantId, String connectionId, String productCode) {
        HutchHttpClient.HutchConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Hutch BSS not configured for tenant={}", tenantId);
            return new ActivationResult(false, null, connectionId, productCode,
                    ActivationStatusCode.FAILED, null, null, "No Hutch BSS integration");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("msisdn", connectionId);
        body.put("productCode", productCode);
        body.put("action", "DEACTIVATE");

        try {
            Map<String, Object> resp = httpClient.post(tenantId, cfg,
                    "/api/v1/packages/deactivate", body, null);
            if (resp == null) {
                return new ActivationResult(false, null, connectionId, productCode,
                        ActivationStatusCode.FAILED, null, null, "Empty response from Hutch BSS");
            }
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> d)) {
                return new ActivationResult(false, null, connectionId, productCode,
                        ActivationStatusCode.FAILED, null, null, "Unexpected Hutch BSS response shape");
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
                    success ? null : "Hutch BSS error: " + status
            );
        } catch (HutchApiException e) {
            log.error("Hutch deactivate failed for tenant={} connection={} product={}: {}",
                    tenantId, connectionId, productCode, e.getMessage());
            return new ActivationResult(false, null, connectionId, productCode,
                    ActivationStatusCode.FAILED, null, null, e.getMessage());
        }
    }

    @Override
    public List<ActivePackage> getActivePackages(String tenantId, String connectionId) {
        HutchHttpClient.HutchConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Hutch BSS not configured for tenant={}", tenantId);
            return List.of();
        }

        String path = "/api/v1/subscribers/" + connectionId + "/packages";
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
        } catch (HutchApiException e) {
            log.error("Hutch getActivePackages failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public ActivationStatus getActivationStatus(String tenantId, String activationId) {
        HutchHttpClient.HutchConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Hutch BSS not configured for tenant={}", tenantId);
            return new ActivationStatus(activationId, null, ActivationStatusCode.FAILED, null, null,
                    "No Hutch BSS integration");
        }

        String path = "/api/v1/packages/activation/" + activationId;
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
        } catch (HutchApiException e) {
            log.error("Hutch getActivationStatus failed for tenant={} activation={}: {}",
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

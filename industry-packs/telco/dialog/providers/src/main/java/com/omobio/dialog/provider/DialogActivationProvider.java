package com.omobio.dialog.provider;

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
 * Dialog Activation Provider — activates and deactivates packages / VAS on Dialog BSS.
 *
 * <p>Implements the canonical {@link ActivationProvider} contract from platform-common.
 * Used by the product-service and BFFs to subscribe / unsubscribe a connection
 * to a package, add-on, or value-added service.</p>
 *
 * <p>Per-tenant config (BSS base URL, API key) is loaded from MongoDB via
 * {@link DialogHttpClient}. Configure via Selfcare Studio admin:
 * Integrations &gt; Dialog BSS.</p>
 */
@Slf4j
@Component
@RegisterAdapter(tenantId = "dialog-lk", providerInterface = ActivationProvider.class)
@RequiredArgsConstructor
public class DialogActivationProvider implements ApiAdapter, ActivationProvider {

    private static final String INTEGRATION_TYPE = "DIALOG_BSS";
    private static final String ADAPTER_ID = "dialog-lk";

    private final DialogHttpClient httpClient;

    @Override
    public String getAdapterId() {
        return ADAPTER_ID;
    }

    // ================================================================
    // ActivationProvider implementation
    // ================================================================

    @Override
    public ActivationResult activate(String tenantId, String connectionId, String productCode) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Dialog BSS not configured for tenant={}", tenantId);
            return new ActivationResult(false, null, connectionId, productCode,
                    ActivationStatusCode.FAILED, null, null, "No Dialog BSS integration");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("subscriberId", connectionId);
        body.put("productCode", productCode);
        body.put("action", "ACTIVATE");

        try {
            Map<String, Object> data = httpClient.post(tenantId, cfg.baseUrl(),
                    "/subscriber/packages/activate", body, null, cfg.apiKey());
            if (data == null) {
                return new ActivationResult(false, null, connectionId, productCode,
                        ActivationStatusCode.FAILED, null, null, "Empty response from Dialog BSS");
            }
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
                    success ? null : "BSS error: " + status
            );
        } catch (DialogApiException e) {
            log.error("Dialog activate failed for tenant={} connection={} product={}: {}",
                    tenantId, connectionId, productCode, e.getMessage());
            return new ActivationResult(false, null, connectionId, productCode,
                    ActivationStatusCode.FAILED, null, null, e.getMessage());
        }
    }

    @Override
    public ActivationResult deactivate(String tenantId, String connectionId, String productCode) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Dialog BSS not configured for tenant={}", tenantId);
            return new ActivationResult(false, null, connectionId, productCode,
                    ActivationStatusCode.FAILED, null, null, "No Dialog BSS integration");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("subscriberId", connectionId);
        body.put("productCode", productCode);
        body.put("action", "DEACTIVATE");

        try {
            Map<String, Object> data = httpClient.post(tenantId, cfg.baseUrl(),
                    "/subscriber/packages/deactivate", body, null, cfg.apiKey());
            if (data == null) {
                return new ActivationResult(false, null, connectionId, productCode,
                        ActivationStatusCode.FAILED, null, null, "Empty response from Dialog BSS");
            }
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
                    success ? null : "BSS error: " + status
            );
        } catch (DialogApiException e) {
            log.error("Dialog deactivate failed for tenant={} connection={} product={}: {}",
                    tenantId, connectionId, productCode, e.getMessage());
            return new ActivationResult(false, null, connectionId, productCode,
                    ActivationStatusCode.FAILED, null, null, e.getMessage());
        }
    }

    @Override
    public List<ActivePackage> getActivePackages(String tenantId, String connectionId) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Dialog BSS not configured for tenant={}", tenantId);
            return List.of();
        }

        String path = "/subscriber/packages/" + connectionId;
        try {
            Map<String, Object> data = httpClient.get(tenantId, cfg.baseUrl(), path, null, null, cfg.apiKey());
            if (data == null) return List.of();
            Object packagesObj = data.get("packages");
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
        } catch (DialogApiException e) {
            log.error("Dialog getActivePackages failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public ActivationStatus getActivationStatus(String tenantId, String activationId) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Dialog BSS not configured for tenant={}", tenantId);
            return new ActivationStatus(activationId, null, ActivationStatusCode.FAILED, null, null,
                    "No Dialog BSS integration");
        }

        String path = "/subscriber/packages/activation-status/" + activationId;
        try {
            Map<String, Object> data = httpClient.get(tenantId, cfg.baseUrl(), path, null, null, cfg.apiKey());
            if (data == null) {
                return new ActivationStatus(activationId, null, ActivationStatusCode.PENDING, null, null, null);
            }
            return new ActivationStatus(
                    activationId,
                    str(data.get("productCode"), null),
                    parseActivationStatus(str(data.get("status"), null)),
                    parseInstant(data.get("activatedAt")),
                    parseInstant(data.get("expiresAt")),
                    str(data.get("failureReason"), null)
            );
        } catch (DialogApiException e) {
            log.error("Dialog getActivationStatus failed for tenant={} activation={}: {}",
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

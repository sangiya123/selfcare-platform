package com.omobio.hutch.provider;

import com.omobio.platform.common.adapter.ApiAdapter;
import com.omobio.platform.common.adapter.ConnectionProvider;
import com.omobio.platform.common.adapter.RegisterAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Hutch Connection Provider — fetches connection and entitlement data from Hutch BSS.
 *
 * <p>Implements the canonical {@link ConnectionProvider} contract from platform-common.
 * Hutch uses API-key authentication and wraps responses in a {@code data} envelope.</p>
 *
 * <p>Per-tenant config (BSS base URL, API key) is loaded from MongoDB via
 * {@link HutchHttpClient}. Configure via Selfcare Studio admin:
 * Integrations &gt; Hutch BSS.</p>
 */
@Slf4j
@Component
@RegisterAdapter(tenantId = "hutch-lk", providerInterface = ConnectionProvider.class)
@RequiredArgsConstructor
public class HutchConnectionProvider implements ApiAdapter, ConnectionProvider {

    private static final String INTEGRATION_TYPE = "HUTCH_BSS";
    private static final String ADAPTER_ID = "hutch-lk";

    private final HutchHttpClient httpClient;

    @Override
    public String getAdapterId() {
        return ADAPTER_ID;
    }

    // ================================================================
    // ConnectionProvider implementation
    // ================================================================

    @Override
    public List<Connection> getConnections(String tenantId, String primaryMsisdn) {
        HutchHttpClient.HutchConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Hutch BSS not configured for tenant={}, returning stub", tenantId);
            return List.of(
                    new Connection(primaryMsisdn, primaryMsisdn, "PRIMARY", "ACTIVE",
                            "HUTCH_PREPAID", null, "4G", null, null, null)
            );
        }

        String path = "/api/v1/subscribers/" + primaryMsisdn + "/connections";
        try {
            Map<String, Object> resp = httpClient.get(tenantId, cfg, path, null, null);
            if (resp == null) return List.of();
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> d)) return List.of();
            Object connectionsObj = d.get("connections");
            if (!(connectionsObj instanceof List<?> connList)) return List.of();
            return connList.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .<Connection>map(m -> new Connection(
                            str(m.get("connectionId"), null),
                            str(m.get("msisdn"), null),
                            str(m.get("connectionType"), "PRIMARY"),
                            str(m.get("status"), "UNKNOWN"),
                            str(m.get("tariffPlan"), null),
                            str(m.get("activationDate"), null),
                            str(m.get("networkType"), null),
                            str(m.get("imsi"), null),
                            str(m.get("iccid"), null),
                            str(m.get("primaryConnectionId"), null)
                    ))
                    .toList();
        } catch (HutchApiException e) {
            log.error("Hutch getConnections failed for tenant={} msisdn={}: {}",
                    tenantId, primaryMsisdn, e.getMessage());
            return List.of();
        }
    }

    @Override
    public Connection getConnection(String tenantId, String connectionId) {
        HutchHttpClient.HutchConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Hutch BSS not configured for tenant={}", tenantId);
            return null;
        }

        String path = "/api/v1/connections/" + connectionId;
        try {
            Map<String, Object> resp = httpClient.get(tenantId, cfg, path, null, null);
            if (resp == null) return null;
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> d)) return null;
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) d;
            return new Connection(
                    connectionId,
                    connectionId,
                    str(m.get("connectionType"), "PRIMARY"),
                    str(m.get("status"), "UNKNOWN"),
                    str(m.get("tariffPlan"), null),
                    str(m.get("activationDate"), null),
                    str(m.get("networkType"), null),
                    str(m.get("imsi"), null),
                    str(m.get("iccid"), null),
                    str(m.get("primaryConnectionId"), null)
            );
        } catch (HutchApiException e) {
            log.error("Hutch getConnection failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return null;
        }
    }

    @Override
    public ServiceStatus getServiceStatus(String tenantId, String connectionId) {
        HutchHttpClient.HutchConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Hutch BSS not configured for tenant={}", tenantId);
            return null;
        }

        String path = "/api/v1/subscribers/" + connectionId + "/status";
        try {
            Map<String, Object> resp = httpClient.get(tenantId, cfg, path, null, null);
            if (resp == null) return null;
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> d)) return null;
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) d;
            return new ServiceStatus(
                    connectionId,
                    str(m.get("status"), "UNKNOWN"),
                    str(m.get("suspensionReason"), null),
                    Boolean.TRUE.equals(m.get("isRoaming")),
                    Boolean.TRUE.equals(m.get("isDataEnabled")),
                    Boolean.TRUE.equals(m.get("isVoiceEnabled")),
                    Boolean.TRUE.equals(m.get("isSmsEnabled")),
                    toInteger(m.get("dataAllowanceMb")),
                    toInteger(m.get("dataUsedMb")),
                    str(m.get("networkSlice"), null)
            );
        } catch (HutchApiException e) {
            log.error("Hutch getServiceStatus failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return null;
        }
    }

    @Override
    public SimDetails getSimDetails(String tenantId, String connectionId) {
        HutchHttpClient.HutchConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Hutch BSS not configured for tenant={}", tenantId);
            return null;
        }

        String path = "/api/v1/subscribers/" + connectionId + "/sim";
        try {
            Map<String, Object> resp = httpClient.get(tenantId, cfg, path, null, null);
            if (resp == null) return null;
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> d)) return null;
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) d;
            return new SimDetails(
                    connectionId,
                    str(m.get("iccid"), null),
                    str(m.get("imsi"), null),
                    str(m.get("simType"), null),
                    str(m.get("simStatus"), null),
                    maskPuk(str(m.get("pukCode"), null))
            );
        } catch (HutchApiException e) {
            log.error("Hutch getSimDetails failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return null;
        }
    }

    // ================================================================
    // Helpers
    // ================================================================

    private String str(Object v, String fallback) {
        return v != null ? v.toString() : fallback;
    }

    private Integer toInteger(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return null; }
    }

    private String maskPuk(String puk) {
        if (puk == null || puk.length() < 4) return "****";
        return "****" + puk.substring(puk.length() - 4);
    }
}

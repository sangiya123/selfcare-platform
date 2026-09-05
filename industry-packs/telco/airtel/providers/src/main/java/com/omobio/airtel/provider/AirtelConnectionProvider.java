package com.omobio.airtel.provider;

import com.omobio.platform.common.adapter.ApiAdapter;
import com.omobio.platform.common.adapter.ConnectionProvider;
import com.omobio.platform.common.adapter.RegisterAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Airtel Connection Provider — fetches connection and entitlement data from Airtel BSS.
 *
 * <p>Implements the canonical {@link ConnectionProvider} contract from platform-common.
 * Airtel uses API-key authentication for BSS endpoints and wraps responses in a
 * {@code { data: { ... } }} envelope.</p>
 *
 * <p>Per-tenant config loaded from MongoDB via {@link AirtelHttpClient}.
 * Configure via Selfcare Studio admin: Integrations &gt; Airtel Gateway.</p>
 */
@Slf4j
@Component
@RegisterAdapter(tenantId = "airtel-lk", providerInterface = ConnectionProvider.class)
@RequiredArgsConstructor
public class AirtelConnectionProvider implements ApiAdapter, ConnectionProvider {

    private static final String INTEGRATION_TYPE = "AIRTEL_BSS";
    private static final String ADAPTER_ID = "airtel-lk";

    private final AirtelHttpClient httpClient;

    @Override
    public String getAdapterId() {
        return ADAPTER_ID;
    }

    // ================================================================
    // ConnectionProvider implementation
    // ================================================================

    @Override
    public List<Connection> getConnections(String tenantId, String primaryMsisdn) {
        AirtelHttpClient.AirtelConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Airtel BSS not configured for tenant={}, returning stub", tenantId);
            return List.of(
                    new Connection(primaryMsisdn, primaryMsisdn, "PRIMARY", "ACTIVE",
                            "AIRTEL_PREPAID", null, "4G", null, null, null)
            );
        }

        String path = "/v1/osp/subscribers/" + primaryMsisdn + "/connections";
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
        } catch (AirtelApiException e) {
            log.error("Airtel getConnections failed for tenant={} msisdn={}: {}",
                    tenantId, primaryMsisdn, e.getMessage());
            return List.of();
        }
    }

    @Override
    public Connection getConnection(String tenantId, String connectionId) {
        AirtelHttpClient.AirtelConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Airtel BSS not configured for tenant={}", tenantId);
            return null;
        }

        String path = "/v1/osp/subscribers/" + connectionId;
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
        } catch (AirtelApiException e) {
            log.error("Airtel getConnection failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return null;
        }
    }

    @Override
    public ServiceStatus getServiceStatus(String tenantId, String connectionId) {
        AirtelHttpClient.AirtelConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Airtel BSS not configured for tenant={}", tenantId);
            return null;
        }

        String path = "/v1/osp/subscribers/" + connectionId + "/status";
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
        } catch (AirtelApiException e) {
            log.error("Airtel getServiceStatus failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return null;
        }
    }

    @Override
    public SimDetails getSimDetails(String tenantId, String connectionId) {
        AirtelHttpClient.AirtelConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Airtel BSS not configured for tenant={}", tenantId);
            return null;
        }

        String path = "/v1/osp/subscribers/" + connectionId + "/sim";
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
        } catch (AirtelApiException e) {
            log.error("Airtel getSimDetails failed for tenant={} connection={}: {}",
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

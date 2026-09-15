package com.selfcare.dialog.provider;

import com.selfcare.platform.common.adapter.ApiAdapter;
import com.selfcare.platform.common.adapter.ConnectionProvider;
import com.selfcare.platform.common.adapter.RegisterAdapter;
import com.selfcare.platform.common.tenant.TenantContextBridge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Dialog Connection Provider — fetches connection and entitlement data from Dialog BSS.
 *
 * <p>Implements the canonical {@link ConnectionProvider} contract from platform-common.
 * Returns the list of all connections (primary SIM + linked eSIMs / data-SIMs)
 * associated with the primary MSISDN, along with their service status and SIM details.</p>
 *
 * <p>Per-tenant config (BSS base URL, API key) is loaded from MongoDB via
 * {@link DialogHttpClient}. Configure via Selfcare Studio admin:
 * Integrations &gt; Dialog BSS.</p>
 */
@Slf4j
@Component
@RegisterAdapter(tenantId = "dialog-lk", providerInterface = ConnectionProvider.class)
@RequiredArgsConstructor
public class DialogConnectionProvider implements ApiAdapter, ConnectionProvider {

    private static final String INTEGRATION_TYPE = "DIALOG_BSS";
    private static final String ADAPTER_ID = "dialog-lk";

    private final DialogHttpClient httpClient;

    @Override
    public String getAdapterId() {
        return ADAPTER_ID;
    }

    // ================================================================
    // ConnectionProvider implementation
    // ================================================================

    @Override
    public List<Connection> getConnections(String tenantId, String primaryMsisdn) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        DialogOperator op = new DialogOperator(cfg);
        if (!op.configured()) {
            log.warn("Dialog BSS not configured for tenant={}, returning empty connections", tenantId);
            return List.of();
        }

        String path = op.path("connection.list", "/subscriber/connections/{connection}",
                Map.of("connection", primaryMsisdn));
        String connectionsField = op.field("connection.list", "connections");
        try {
            Map<String, Object> data = httpClient.get(tenantId, cfg.baseUrl(), path, null, null, cfg.apiKey());
            if (data == null) {
                return List.of();
            }
            Object connectionsObj = data.get(connectionsField);
            if (!(connectionsObj instanceof List<?> connList)) {
                return List.of();
            }
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
        } catch (DialogApiException e) {
            log.error("Dialog getConnections failed for tenant={} msisdn={}: {}",
                    tenantId, primaryMsisdn, e.getMessage());
            return List.of();
        }
    }

    @Override
    public Connection getConnection(String tenantId, String connectionId) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        DialogOperator op = new DialogOperator(cfg);
        if (!op.configured()) {
            log.warn("Dialog BSS not configured for tenant={}", tenantId);
            return null;
        }

        String path = op.path("connection.detail", "/subscriber/connections/{connection}",
                Map.of("connection", connectionId));
        try {
            Map<String, Object> data = httpClient.get(tenantId, cfg.baseUrl(), path, null, null, cfg.apiKey());
            if (data == null) return null;
            return new Connection(
                    connectionId,
                    connectionId,
                    str(data.get("connectionType"), "PRIMARY"),
                    str(data.get("status"), "UNKNOWN"),
                    str(data.get("tariffPlan"), null),
                    str(data.get("activationDate"), null),
                    str(data.get("networkType"), null),
                    str(data.get("imsi"), null),
                    str(data.get("iccid"), null),
                    str(data.get("primaryConnectionId"), null)
            );
        } catch (DialogApiException e) {
            log.error("Dialog getConnection failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return null;
        }
    }

    @Override
    public ServiceStatus getServiceStatus(String tenantId, String connectionId) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        DialogOperator op = new DialogOperator(cfg);
        if (!op.configured()) {
            log.warn("Dialog BSS not configured for tenant={}", tenantId);
            return null;
        }

        String path = op.path("connection.status", "/subscriber/status/{connection}",
                Map.of("connection", connectionId));
        try {
            Map<String, Object> data = httpClient.get(tenantId, cfg.baseUrl(), path, null, null, cfg.apiKey());
            if (data == null) return null;
            return new ServiceStatus(
                    connectionId,
                    str(data.get("status"), "UNKNOWN"),
                    str(data.get("suspensionReason"), null),
                    Boolean.TRUE.equals(data.get("isRoaming")),
                    Boolean.TRUE.equals(data.get("isDataEnabled")),
                    Boolean.TRUE.equals(data.get("isVoiceEnabled")),
                    Boolean.TRUE.equals(data.get("isSmsEnabled")),
                    toInteger(data.get("dataAllowanceMb")),
                    toInteger(data.get("dataUsedMb")),
                    str(data.get("networkSlice"), null)
            );
        } catch (DialogApiException e) {
            log.error("Dialog getServiceStatus failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return null;
        }
    }

    @Override
    public SimDetails getSimDetails(String tenantId, String connectionId) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        DialogOperator op = new DialogOperator(cfg);
        if (!op.configured()) {
            log.warn("Dialog BSS not configured for tenant={}", tenantId);
            return null;
        }

        String path = op.path("connection.sim", "/subscriber/sim/{connection}",
                Map.of("connection", connectionId));
        try {
            Map<String, Object> data = httpClient.get(tenantId, cfg.baseUrl(), path, null, null, cfg.apiKey());
            if (data == null) return null;
            return new SimDetails(
                    connectionId,
                    str(data.get("iccid"), null),
                    str(data.get("imsi"), null),
                    str(data.get("simType"), null),
                    str(data.get("simStatus"), null),
                    maskPuk(str(data.get("pukCode"), null))
            );
        } catch (DialogApiException e) {
            log.error("Dialog getSimDetails failed for tenant={} connection={}: {}",
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

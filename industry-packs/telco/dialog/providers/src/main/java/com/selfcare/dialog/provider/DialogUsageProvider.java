package com.selfcare.dialog.provider;

import com.selfcare.platform.common.adapter.ApiAdapter;
import com.selfcare.platform.common.adapter.RegisterAdapter;
import com.selfcare.platform.common.adapter.UsageProvider;
import com.selfcare.platform.common.tenant.TenantContextBridge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Dialog Usage Provider — fetches detailed usage records from Dialog BSS.
 *
 * <p>Implements the canonical {@link UsageProvider} contract from platform-common.
 * Returns per-session / per-event usage records for data, voice, SMS, and roaming,
 * enabling detailed usage breakdowns for the selfcare dashboard.</p>
 *
 * <p>This provider complements {@link DialogBalanceProvider} which returns aggregate
 * balance and allowance. DialogUsageProvider returns detailed transaction-level
 * usage records.</p>
 *
 * <p>Per-tenant config (BSS base URL, API key) is loaded from MongoDB via
 * {@link DialogHttpClient}. Configure via Selfcare Studio admin:
 * Integrations &gt; Dialog BSS.</p>
 */
@Slf4j
@Component
@RegisterAdapter(tenantId = "dialog-lk", providerInterface = UsageProvider.class)
@RequiredArgsConstructor
public class DialogUsageProvider implements ApiAdapter, UsageProvider {

    private static final String INTEGRATION_TYPE = "DIALOG_BSS";
    private static final String ADAPTER_ID = "dialog-lk";

    private final DialogHttpClient httpClient;

    @Override
    public String getAdapterId() {
        return ADAPTER_ID;
    }

    // ================================================================
    // UsageProvider implementation
    // ================================================================

    @Override
    public List<DataUsageRecord> getDataUsage(String tenantId, String connectionId,
                                              Instant from, Instant to) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        DialogOperator op = new DialogOperator(cfg);
        if (!op.configured()) {
            log.warn("Dialog BSS not configured for tenant={}", tenantId);
            return List.of();
        }

        var queryParams = Map.<String, Object>of(
                "from", from.getEpochSecond(),
                "to", to.getEpochSecond(),
                "type", "data");

        String path = op.path("usage.records", "/subscriber/usage/{connection}/records",
                Map.of("connection", connectionId));
        String recordsField = op.field("usage.records", "records");
        String currency = op.currency();
        try {
            Map<String, Object> data = httpClient.get(tenantId, cfg.baseUrl(), path, queryParams, null, cfg.apiKey());
            if (data == null) return List.of();
            Object recordsObj = data.get(recordsField);
            if (!(recordsObj instanceof List<?> list)) return List.of();
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .<DataUsageRecord>map(m -> new DataUsageRecord(
                            str(m.get("recordId"), null),
                            connectionId,
                            parseInstant(m.get("timestamp")),
                            toLong(m.get("bytesUsed")),
                            toLong(m.get("sessionDurationSeconds")),
                            str(m.get("networkType"), null),
                            str(m.get("sessionType"), null),
                            str(m.get("country"), null),
                            toBd(m.get("chargeableAmount")),
                            str(m.get("currency"), currency)
                    ))
                    .toList();
        } catch (DialogApiException e) {
            log.error("Dialog getDataUsage failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<VoiceUsageRecord> getVoiceUsage(String tenantId, String connectionId,
                                                Instant from, Instant to) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        DialogOperator op = new DialogOperator(cfg);
        if (!op.configured()) {
            log.warn("Dialog BSS not configured for tenant={}", tenantId);
            return List.of();
        }

        var queryParams = Map.<String, Object>of(
                "from", from.getEpochSecond(),
                "to", to.getEpochSecond(),
                "type", "voice");

        String path = op.path("usage.records", "/subscriber/usage/{connection}/records",
                Map.of("connection", connectionId));
        String recordsField = op.field("usage.records", "records");
        String currency = op.currency();
        try {
            Map<String, Object> data = httpClient.get(tenantId, cfg.baseUrl(), path, queryParams, null, cfg.apiKey());
            if (data == null) return List.of();
            Object recordsObj = data.get(recordsField);
            if (!(recordsObj instanceof List<?> list)) return List.of();
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .<VoiceUsageRecord>map(m -> new VoiceUsageRecord(
                            str(m.get("recordId"), null),
                            connectionId,
                            parseInstant(m.get("startTime")),
                            parseInstant(m.get("endTime")),
                            toLong(m.get("durationSeconds")),
                            str(m.get("direction"), null),
                            str(m.get("calledNumber"), null),
                            str(m.get("country"), null),
                            Boolean.TRUE.equals(m.get("isRoaming")),
                            toBd(m.get("chargeableAmount")),
                            str(m.get("currency"), currency)
                    ))
                    .toList();
        } catch (DialogApiException e) {
            log.error("Dialog getVoiceUsage failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<SmsUsageRecord> getSmsUsage(String tenantId, String connectionId,
                                             Instant from, Instant to) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        DialogOperator op = new DialogOperator(cfg);
        if (!op.configured()) {
            log.warn("Dialog BSS not configured for tenant={}", tenantId);
            return List.of();
        }

        var queryParams = Map.<String, Object>of(
                "from", from.getEpochSecond(),
                "to", to.getEpochSecond(),
                "type", "sms");

        String path = op.path("usage.records", "/subscriber/usage/{connection}/records",
                Map.of("connection", connectionId));
        String recordsField = op.field("usage.records", "records");
        String currency = op.currency();
        try {
            Map<String, Object> data = httpClient.get(tenantId, cfg.baseUrl(), path, queryParams, null, cfg.apiKey());
            if (data == null) return List.of();
            Object recordsObj = data.get(recordsField);
            if (!(recordsObj instanceof List<?> list)) return List.of();
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .<SmsUsageRecord>map(m -> new SmsUsageRecord(
                            str(m.get("recordId"), null),
                            connectionId,
                            parseInstant(m.get("timestamp")),
                            str(m.get("direction"), null),
                            str(m.get("recipientNumber"), null),
                            str(m.get("country"), null),
                            Boolean.TRUE.equals(m.get("isRoaming")),
                            toBd(m.get("chargeableAmount")),
                            str(m.get("currency"), currency)
                    ))
                    .toList();
        } catch (DialogApiException e) {
            log.error("Dialog getSmsUsage failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public CurrentUsage getCurrentUsage(String tenantId, String connectionId) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        DialogOperator op = new DialogOperator(cfg);
        if (!op.configured()) {
            log.warn("Dialog BSS not configured for tenant={}", tenantId);
            return null;
        }

        String path = op.path("usage.current", "/subscriber/usage/{connection}/current",
                Map.of("connection", connectionId));
        try {
            Map<String, Object> data = httpClient.get(tenantId, cfg.baseUrl(), path, null, null, cfg.apiKey());
            if (data == null) return null;
            return new CurrentUsage(
                    connectionId,
                    Instant.now(),
                    toLong(data.get("dataUsedBytes")),
                    toLong(data.get("dataAllowanceBytes")),
                    toLong(data.get("voiceUsedSeconds")),
                    toLong(data.get("voiceAllowanceSeconds")),
                    toInteger(data.get("smsUsed")),
                    toInteger(data.get("smsAllowance"))
            );
        } catch (DialogApiException e) {
            log.error("Dialog getCurrentUsage failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return null;
        }
    }

    @Override
    public List<RoamingUsageRecord> getRoamingUsage(String tenantId, String connectionId,
                                                     Instant from, Instant to) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        DialogOperator op = new DialogOperator(cfg);
        if (!op.configured()) {
            log.warn("Dialog BSS not configured for tenant={}", tenantId);
            return List.of();
        }

        var queryParams = Map.<String, Object>of(
                "from", from.getEpochSecond(),
                "to", to.getEpochSecond());

        String path = op.path("usage.roaming", "/subscriber/usage/{connection}/roaming",
                Map.of("connection", connectionId));
        String recordsField = op.field("usage.records", "records");
        String currency = op.currency();
        try {
            Map<String, Object> data = httpClient.get(tenantId, cfg.baseUrl(), path, queryParams, null, cfg.apiKey());
            if (data == null) return List.of();
            Object recordsObj = data.get(recordsField);
            if (!(recordsObj instanceof List<?> list)) return List.of();
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .<RoamingUsageRecord>map(m -> new RoamingUsageRecord(
                            str(m.get("recordId"), null),
                            connectionId,
                            parseInstant(m.get("timestamp")),
                            str(m.get("country"), null),
                            str(m.get("networkOperator"), null),
                            str(m.get("usageType"), null),
                            toBd(m.get("amount")),
                            toBd(m.get("charge")),
                            str(m.get("currency"), currency)
                    ))
                    .toList();
        } catch (DialogApiException e) {
            log.error("Dialog getRoamingUsage failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return List.of();
        }
    }

    // ================================================================
    // Helpers
    // ================================================================

    private String str(Object v, String fallback) {
        return v != null ? v.toString() : fallback;
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        return null;
    }

    private Integer toInteger(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        return null;
    }

    private BigDecimal toBd(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(v.toString()); } catch (NumberFormatException e) { return null; }
    }

    private Instant parseInstant(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return Instant.ofEpochSecond(n.longValue());
        if (v instanceof String s) {
            try { return Instant.parse(s); } catch (Exception ignored) {}
        }
        return null;
    }
}

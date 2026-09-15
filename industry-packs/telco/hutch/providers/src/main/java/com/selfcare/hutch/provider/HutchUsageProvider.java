package com.selfcare.hutch.provider;

import com.selfcare.platform.common.adapter.ApiAdapter;
import com.selfcare.platform.common.adapter.RegisterAdapter;
import com.selfcare.platform.common.adapter.UsageProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Hutch Usage Provider — fetches detailed usage records from Hutch BSS.
 *
 * <p>Implements the canonical {@link UsageProvider} contract from platform-common.
 * Hutch uses API-key authentication and wraps responses in a {@code data} envelope.</p>
 *
 * <p>Per-tenant config (BSS base URL, API key) is loaded from MongoDB via
 * {@link HutchHttpClient}. Configure via Selfcare Studio admin:
 * Integrations &gt; Hutch BSS.</p>
 */
@Slf4j
@Component
@RegisterAdapter(tenantId = "hutch-lk", providerInterface = UsageProvider.class)
@RequiredArgsConstructor
public class HutchUsageProvider implements ApiAdapter, UsageProvider {

    private static final String INTEGRATION_TYPE = "HUTCH_BSS";
    private static final String ADAPTER_ID = "hutch-lk";

    private final HutchHttpClient httpClient;

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
        HutchHttpClient.HutchConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Hutch BSS not configured for tenant={}", tenantId);
            return List.of();
        }

        var queryParams = Map.<String, Object>of(
                "from", from.getEpochSecond(),
                "to", to.getEpochSecond(),
                "type", "data");

        String path = "/api/v1/usage/" + connectionId + "/records";
        try {
            Map<String, Object> resp = httpClient.get(tenantId, cfg, path, queryParams, null);
            if (resp == null) return List.of();
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> d)) return List.of();
            Object recordsObj = d.get("records");
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
                            str(m.get("currency"), "LKR")
                    ))
                    .toList();
        } catch (HutchApiException e) {
            log.error("Hutch getDataUsage failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<VoiceUsageRecord> getVoiceUsage(String tenantId, String connectionId,
                                                Instant from, Instant to) {
        HutchHttpClient.HutchConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Hutch BSS not configured for tenant={}", tenantId);
            return List.of();
        }

        var queryParams = Map.<String, Object>of(
                "from", from.getEpochSecond(),
                "to", to.getEpochSecond(),
                "type", "voice");

        String path = "/api/v1/usage/" + connectionId + "/records";
        try {
            Map<String, Object> resp = httpClient.get(tenantId, cfg, path, queryParams, null);
            if (resp == null) return List.of();
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> d)) return List.of();
            Object recordsObj = d.get("records");
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
                            str(m.get("currency"), "LKR")
                    ))
                    .toList();
        } catch (HutchApiException e) {
            log.error("Hutch getVoiceUsage failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<SmsUsageRecord> getSmsUsage(String tenantId, String connectionId,
                                             Instant from, Instant to) {
        HutchHttpClient.HutchConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Hutch BSS not configured for tenant={}", tenantId);
            return List.of();
        }

        var queryParams = Map.<String, Object>of(
                "from", from.getEpochSecond(),
                "to", to.getEpochSecond(),
                "type", "sms");

        String path = "/api/v1/usage/" + connectionId + "/records";
        try {
            Map<String, Object> resp = httpClient.get(tenantId, cfg, path, queryParams, null);
            if (resp == null) return List.of();
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> d)) return List.of();
            Object recordsObj = d.get("records");
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
                            str(m.get("currency"), "LKR")
                    ))
                    .toList();
        } catch (HutchApiException e) {
            log.error("Hutch getSmsUsage failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public CurrentUsage getCurrentUsage(String tenantId, String connectionId) {
        HutchHttpClient.HutchConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Hutch BSS not configured for tenant={}", tenantId);
            return null;
        }

        String path = "/api/v1/usage/" + connectionId + "/current";
        try {
            Map<String, Object> resp = httpClient.get(tenantId, cfg, path, null, null);
            if (resp == null) return null;
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> d)) return null;
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) d;
            return new CurrentUsage(
                    connectionId,
                    Instant.now(),
                    toLong(m.get("dataUsedBytes")),
                    toLong(m.get("dataAllowanceBytes")),
                    toLong(m.get("voiceUsedSeconds")),
                    toLong(m.get("voiceAllowanceSeconds")),
                    toInteger(m.get("smsUsed")),
                    toInteger(m.get("smsAllowance"))
            );
        } catch (HutchApiException e) {
            log.error("Hutch getCurrentUsage failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return null;
        }
    }

    @Override
    public List<RoamingUsageRecord> getRoamingUsage(String tenantId, String connectionId,
                                                     Instant from, Instant to) {
        HutchHttpClient.HutchConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Hutch BSS not configured for tenant={}", tenantId);
            return List.of();
        }

        var queryParams = Map.<String, Object>of(
                "from", from.getEpochSecond(),
                "to", to.getEpochSecond());

        String path = "/api/v1/usage/" + connectionId + "/roaming";
        try {
            Map<String, Object> resp = httpClient.get(tenantId, cfg, path, queryParams, null);
            if (resp == null) return List.of();
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> d)) return List.of();
            Object recordsObj = d.get("records");
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
                            str(m.get("currency"), "LKR")
                    ))
                    .toList();
        } catch (HutchApiException e) {
            log.error("Hutch getRoamingUsage failed for tenant={} connection={}: {}",
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

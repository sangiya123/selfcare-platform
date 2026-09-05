package com.omobio.hutch.provider;

import com.omobio.platform.common.adapter.ApiAdapter;
import com.omobio.platform.common.adapter.RegisterAdapter;
import com.omobio.platform.common.tenant.TenantContextBridge;
import com.omobio.usage.adapter.BalanceProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Hutch Balance Provider — fetches balance and usage from Hutch BSS.
 *
 * <p>Implements the {@link BalanceProvider} contract from usage-service.
 * Hutch uses API-key authentication and a slightly different response
 * shape (data nested under a {@code data} envelope).</p>
 *
 * <p>Per-tenant config (BSS base URL, API key) loaded from MongoDB via
 * {@link HutchHttpClient}. Configure via Selfcare Studio admin:
 * Integrations &gt; Hutch BSS.</p>
 */
@Slf4j
@Component
@RegisterAdapter(tenantId = "hutch-lk", providerInterface = BalanceProvider.class)
@RequiredArgsConstructor
public class HutchBalanceProvider implements ApiAdapter, BalanceProvider {

    private static final String INTEGRATION_TYPE = "HUTCH_BSS";
    private static final String ADAPTER_ID = "hutch-lk";

    private final HutchHttpClient httpClient;

    @Override
    public String getAdapterId() {
        return ADAPTER_ID;
    }

    @Override
    public Balance fetchBalance(String connectionId) {
        return fetchBalance(TenantContextBridge.currentTenantId(), connectionId);
    }

    public Balance fetchBalance(String tenantId, String connectionId) {
        HutchHttpClient.HutchConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Hutch BSS not configured for tenant={}", tenantId);
            return null;
        }
        String path = "/api/v1/subscriber/" + connectionId + "/balance";
        try {
            Map<String, Object> body = httpClient.get(tenantId, cfg, path, null, null);
            if (body == null) return null;
            Object dataObj = body.get("data");
            if (!(dataObj instanceof Map<?, ?>)) return null;
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) dataObj;
            return Balance.builder()
                    .connectionId(connectionId)
                    .amount(toBigDecimal(data.get("balance")))
                    .currency("LKR")
                    .balanceType(str(data.get("type"), "PREPAID"))
                    .expiryDate(parseExpiry(data.get("expiry")))
                    .timestamp(Instant.now())
                    .isPrimary(true)
                    .build();
        } catch (HutchApiException e) {
            log.error("Hutch balance fetch failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return null;
        }
    }

    @Override
    public UsageSummary fetchUsage(String connectionId, Instant periodStart, Instant periodEnd) {
        return fetchUsage(TenantContextBridge.currentTenantId(), connectionId, periodStart, periodEnd);
    }

    public UsageSummary fetchUsage(String tenantId, String connectionId, Instant periodStart, Instant periodEnd) {
        HutchHttpClient.HutchConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) return null;
        var queryParams = Map.<String, Object>of(
                "fromDate", periodStart.toString(),
                "toDate", periodEnd.toString());
        String path = "/api/v1/subscriber/" + connectionId + "/usage";
        try {
            Map<String, Object> body = httpClient.get(tenantId, cfg, path, queryParams, null);
            if (body == null) return null;
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            if (data == null) return null;
            @SuppressWarnings("unchecked")
            Map<String, Object> dataUsage = (Map<String, Object>) data.get("data");
            @SuppressWarnings("unchecked")
            Map<String, Object> voiceUsage = (Map<String, Object>) data.get("voice");
            @SuppressWarnings("unchecked")
            Map<String, Object> smsUsage = (Map<String, Object>) data.get("sms");

            return UsageSummary.builder()
                    .connectionId(connectionId)
                    .periodStart(periodStart)
                    .periodEnd(periodEnd)
                    .data(DataUsage.builder()
                            .totalBytes(toLong(dataUsage, "usedMB") * 1_048_576L)
                            .remainingBytes(toLong(dataUsage, "remainingMB") * 1_048_576L)
                            .allowanceBytes(toLong(dataUsage, "totalMB") * 1_048_576L)
                            .resetDate(parseInstant(dataUsage != null ? dataUsage.get("resetAt") : null))
                            .isUnlimited(Boolean.FALSE)
                            .build())
                    .voice(VoiceUsage.builder()
                            .totalSeconds(toLong(voiceUsage, "usedMinutes") * 60L)
                            .remainingSeconds(toLong(voiceUsage, "remainingMinutes") * 60L)
                            .allowanceSeconds(toLong(voiceUsage, "totalMinutes") * 60L)
                            .resetDate(parseInstant(voiceUsage != null ? voiceUsage.get("resetAt") : null))
                            .isUnlimited(Boolean.FALSE)
                            .build())
                    .sms(SmsUsage.builder()
                            .totalCount(toLong(smsUsage, "usedCount"))
                            .remainingCount(toLong(smsUsage, "remainingCount"))
                            .allowanceCount(toLong(smsUsage, "totalCount"))
                            .resetDate(parseInstant(smsUsage != null ? smsUsage.get("resetAt") : null))
                            .isUnlimited(Boolean.FALSE)
                            .build())
                    .build();
        } catch (HutchApiException e) {
            log.error("Hutch usage fetch failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return null;
        }
    }

    // ================================================================
    // Helpers
    // ================================================================

    private Long toLong(Map<String, Object> m, String key) {
        if (m == null) return 0L;
        Object v = m.get(key);
        if (v instanceof Number n) return n.longValue();
        return 0L;
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(v.toString()); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    private String str(Object v, String fallback) {
        return v != null ? v.toString() : fallback;
    }

    private Instant parseExpiry(Object value) {
        if (value == null) return Instant.now().plusSeconds(86400 * 30);
        if (value instanceof Number n) return Instant.ofEpochSecond(n.longValue());
        if (value instanceof String s) {
            try { return Instant.parse(s); } catch (Exception ignored) {}
        }
        return Instant.now().plusSeconds(86400 * 30);
    }

    private Instant parseInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return Instant.ofEpochSecond(n.longValue());
        if (value instanceof String s) {
            try { return Instant.parse(s); } catch (Exception ignored) {}
        }
        return null;
    }
}

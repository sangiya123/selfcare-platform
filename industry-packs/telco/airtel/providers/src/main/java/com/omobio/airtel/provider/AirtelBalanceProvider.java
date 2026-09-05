package com.omobio.airtel.provider;

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
 * Airtel Balance Provider — fetches balance and usage from Airtel BSS via
 * the Airtel Money gateway.
 *
 * <p>Implements the {@link BalanceProvider} contract from usage-service.
 * Airtel wraps responses in a {@code { data: { ... } }} envelope and uses
 * a different field name convention than Dialog.</p>
 *
 * <p>Per-tenant config loaded from MongoDB via {@link AirtelHttpClient}.
 * Configure via Selfcare Studio admin: Integrations &gt; Airtel Gateway.</p>
 */
@Slf4j
@Component
@RegisterAdapter(tenantId = "airtel-lk", providerInterface = BalanceProvider.class)
@RequiredArgsConstructor
public class AirtelBalanceProvider implements ApiAdapter, BalanceProvider {

    private static final String INTEGRATION_TYPE = "AIRTEL_GATEWAY";
    private static final String ADAPTER_ID = "airtel-lk";

    private final AirtelHttpClient httpClient;

    @Override
    public String getAdapterId() {
        return ADAPTER_ID;
    }

    @Override
    public Balance fetchBalance(String connectionId) {
        return fetchBalance(TenantContextBridge.currentTenantId(), connectionId);
    }

    public Balance fetchBalance(String tenantId, String connectionId) {
        AirtelHttpClient.AirtelConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Airtel Gateway not configured for tenant={}", tenantId);
            return null;
        }
        String path = "/v1/osp/subscribers/" + connectionId + "/balance";
        try {
            String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
            Map<String, Object> resp = httpClient.get(tenantId, cfg, path, null, token);
            if (resp == null) return null;
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> d)) return null;
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) d;
            return Balance.builder()
                    .connectionId(connectionId)
                    .amount(toBigDecimal(data.get("amount")))
                    .currency(str(data.get("currency"), "LKR"))
                    .balanceType(str(data.get("accountType"), "PREPAID"))
                    .expiryDate(parseExpiry(data.get("expiryDate")))
                    .timestamp(Instant.now())
                    .isPrimary(true)
                    .build();
        } catch (AirtelApiException e) {
            log.error("Airtel balance fetch failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return null;
        }
    }

    @Override
    public UsageSummary fetchUsage(String connectionId, Instant periodStart, Instant periodEnd) {
        return fetchUsage(TenantContextBridge.currentTenantId(), connectionId, periodStart, periodEnd);
    }

    public UsageSummary fetchUsage(String tenantId, String connectionId, Instant periodStart, Instant periodEnd) {
        AirtelHttpClient.AirtelConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) return null;
        var queryParams = Map.<String, Object>of(
                "fromDate", periodStart.toString(),
                "toDate", periodEnd.toString());
        String path = "/v1/osp/subscribers/" + connectionId + "/usage";
        try {
            String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
            Map<String, Object> resp = httpClient.get(tenantId, cfg, path, queryParams, token);
            if (resp == null) return null;
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) resp.get("data");
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
                            .totalBytes(toLong(dataUsage, "usedBytes"))
                            .remainingBytes(toLong(dataUsage, "remainingBytes"))
                            .allowanceBytes(toLong(dataUsage, "allowanceBytes"))
                            .resetDate(parseInstant(dataUsage != null ? dataUsage.get("resetAt") : null))
                            .isUnlimited(Boolean.FALSE)
                            .build())
                    .voice(VoiceUsage.builder()
                            .totalSeconds(toLong(voiceUsage, "usedSeconds"))
                            .remainingSeconds(toLong(voiceUsage, "remainingSeconds"))
                            .allowanceSeconds(toLong(voiceUsage, "allowanceSeconds"))
                            .resetDate(parseInstant(voiceUsage != null ? voiceUsage.get("resetAt") : null))
                            .isUnlimited(Boolean.FALSE)
                            .build())
                    .sms(SmsUsage.builder()
                            .totalCount(toLong(smsUsage, "usedCount"))
                            .remainingCount(toLong(smsUsage, "remainingCount"))
                            .allowanceCount(toLong(smsUsage, "allowanceCount"))
                            .resetDate(parseInstant(smsUsage != null ? smsUsage.get("resetAt") : null))
                            .isUnlimited(Boolean.FALSE)
                            .build())
                    .build();
        } catch (AirtelApiException e) {
            log.error("Airtel usage fetch failed for tenant={} connection={}: {}",
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

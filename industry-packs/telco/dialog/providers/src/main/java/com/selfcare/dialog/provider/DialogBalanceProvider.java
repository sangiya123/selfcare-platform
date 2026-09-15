package com.selfcare.dialog.provider;

import com.selfcare.platform.common.adapter.ApiAdapter;
import com.selfcare.platform.common.adapter.RegisterAdapter;
import com.selfcare.platform.common.tenant.TenantContextBridge;
import com.selfcare.platform.common.adapter.BalanceProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Dialog Balance Provider — fetches balance and usage from Dialog BSS.
 *
 * <p>Implements the canonical {@link BalanceProvider} contract from the
 * usage-service. The BalanceProvider interface is also implemented
 * directly (not just registered) so {@code @Autowired} works in dev paths.</p>
 *
 * <p>Per-tenant config (BSS base URL, API key) is loaded from MongoDB via
 * {@link DialogHttpClient} which reads
 * {@code com.selfcare.platform.common.tenant.TenantConfigurationService}.</p>
 *
 * <p>Configure via Selfcare Studio admin: Integrations &gt; Dialog BSS.</p>
 */
@Slf4j
@Component
@RegisterAdapter(tenantId = "dialog-lk", providerInterface = BalanceProvider.class)
@RequiredArgsConstructor
public class DialogBalanceProvider implements ApiAdapter, BalanceProvider {

    private static final String INTEGRATION_TYPE = "DIALOG_BSS";
    private static final String ADAPTER_ID = "dialog-lk";

    private final DialogHttpClient httpClient;

    @Override
    public String getAdapterId() {
        return ADAPTER_ID;
    }

    // ================================================================
    // BalanceProvider implementation
    // ================================================================

    @Override
    public Balance fetchBalance(String connectionId) {
        return fetchBalance(TenantContextBridge.currentTenantId(), connectionId);
    }

    /**
     * Tenant-explicit variant — used by callers that already know the tenant.
     */
    public Balance fetchBalance(String tenantId, String connectionId) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        DialogOperator op = new DialogOperator(cfg);
        if (!op.configured()) {
            log.warn("Dialog BSS not configured for tenant={}, returning null", tenantId);
            return null;
        }

        String path = op.path("balance", "/subscriber/balance/{connection}",
                Map.of("connection", connectionId));
        String fieldMain = op.field("mainBalance", "mainBalance");
        String fieldType = op.field("balanceType", "balanceType");
        String fieldExpiry = op.field("expiryDate", "expiryDate");
        String currency = op.currency();

        try {
            Map<String, Object> data = httpClient.get(tenantId, cfg.baseUrl(), path, null, null, cfg.apiKey());
            if (data == null) return null;
            return Balance.builder()
                    .connectionId(connectionId)
                    .amount(toBigDecimal(data.get(fieldMain)))
                    .currency(currency)
                    .balanceType(str(data.get(fieldType), "PREPAID"))
                    .expiryDate(parseExpiry(data.get(fieldExpiry)))
                    .timestamp(Instant.now())
                    .isPrimary(true)
                    .build();
        } catch (DialogApiException e) {
            log.error("Dialog balance fetch failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return null;
        }
    }

    @Override
    public UsageSummary fetchUsage(String connectionId, Instant periodStart, Instant periodEnd) {
        return fetchUsage(TenantContextBridge.currentTenantId(), connectionId, periodStart, periodEnd);
    }

    public UsageSummary fetchUsage(String tenantId, String connectionId, Instant periodStart, Instant periodEnd) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        DialogOperator op = new DialogOperator(cfg);
        if (!op.configured()) {
            log.warn("Dialog BSS not configured for tenant={}, returning null", tenantId);
            return null;
        }

        var queryParams = Map.<String, Object>of(
                "from", periodStart.getEpochSecond(),
                "to", periodEnd.getEpochSecond());
        String path = op.path("usage", "/subscriber/usage/{connection}",
                Map.of("connection", connectionId));
        String fieldData = op.field("usage.data", "data");
        String fieldVoice = op.field("usage.voice", "voice");
        String fieldSms = op.field("usage.sms", "sms");
        String currency = op.currency();

        try {
            Map<String, Object> data = httpClient.get(tenantId, cfg.baseUrl(), path, queryParams, null, cfg.apiKey());
            if (data == null) return null;

            @SuppressWarnings("unchecked")
            Map<String, Object> dataUsage = (Map<String, Object>) data.get(fieldData);
            @SuppressWarnings("unchecked")
            Map<String, Object> voiceUsage = (Map<String, Object>) data.get(fieldVoice);
            @SuppressWarnings("unchecked")
            Map<String, Object> smsUsage = (Map<String, Object>) data.get(fieldSms);

            return UsageSummary.builder()
                    .connectionId(connectionId)
                    .periodStart(periodStart)
                    .periodEnd(periodEnd)
                    .data(DataUsage.builder()
                            .totalBytes(toBytes(dataUsage, "used"))
                            .remainingBytes(toBytes(dataUsage, "remaining"))
                            .allowanceBytes(toBytes(dataUsage, "total"))
                            .resetDate(parseInstant(dataUsage != null ? dataUsage.get("resetDate") : null))
                            .isUnlimited(Boolean.FALSE)
                            .build())
                    .voice(VoiceUsage.builder()
                            .totalSeconds(toLong(voiceUsage, "usedSeconds"))
                            .remainingSeconds(toLong(voiceUsage, "remainingSeconds"))
                            .allowanceSeconds(toLong(voiceUsage, "totalSeconds"))
                            .resetDate(parseInstant(voiceUsage != null ? voiceUsage.get("resetDate") : null))
                            .isUnlimited(Boolean.FALSE)
                            .build())
                    .sms(SmsUsage.builder()
                            .totalCount(toLong(smsUsage, "count"))
                            .remainingCount(toLong(smsUsage, "remaining"))
                            .allowanceCount(toLong(smsUsage, "total"))
                            .resetDate(parseInstant(smsUsage != null ? smsUsage.get("resetDate") : null))
                            .isUnlimited(Boolean.FALSE)
                            .build())
                    .build();
        } catch (DialogApiException e) {
            log.error("Dialog usage fetch failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return null;
        }
    }

    // ================================================================
    // Parsing Helpers
    // ================================================================

    private Long toBytes(Map<String, Object> m, String key) {
        if (m == null) return 0L;
        Object v = m.get(key);
        if (v == null) return 0L;
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) {
            String upper = s.toUpperCase().trim();
            try {
                if (upper.endsWith("GB")) return (long) (Double.parseDouble(upper.replace("GB", "")) * 1_073_741_824);
                if (upper.endsWith("MB")) return (long) (Double.parseDouble(upper.replace("MB", "")) * 1_048_576);
                if (upper.endsWith("KB")) return (long) (Double.parseDouble(upper.replace("KB", "")) * 1024);
                return Long.parseLong(upper);
            } catch (NumberFormatException nfe) {
                return 0L;
            }
        }
        return 0L;
    }

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

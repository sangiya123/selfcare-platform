package com.selfcare.dialog.provider;

import com.selfcare.platform.common.adapter.ApiAdapter;
import com.selfcare.platform.common.adapter.CreditLimitProvider;
import com.selfcare.platform.common.adapter.RegisterAdapter;
import com.selfcare.platform.common.tenant.TenantContextBridge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Dialog Credit Limit Provider — fetches the postpaid credit limit from
 * Dialog BSS.
 *
 * <p>Backs the legacy {@code getCreditLimit} proxy action:
 * MIFE {@code /apicall/crm/dds/cl/1.0.0/connections/{number}/credit}.</p>
 *
 * <p>All operator-specific values (path, LOB, response field names, currency)
 * are resolved from the tenant's DB-backed integration config via
 * {@link DialogOperator} — edited in the Admin Portal, never hardcoded.</p>
 */
@Slf4j
@Component
@RegisterAdapter(tenantId = "dialog-lk", providerInterface = CreditLimitProvider.class)
@RequiredArgsConstructor
public class DialogCreditLimitProvider implements ApiAdapter, CreditLimitProvider {

    private static final String INTEGRATION_TYPE = "DIALOG_BSS";
    private static final String ADAPTER_ID = "dialog-lk";

    private final DialogHttpClient httpClient;

    @Override
    public String getAdapterId() {
        return ADAPTER_ID;
    }

    @Override
    public CreditLimit fetchCreditLimit(String connectionId) {
        return fetchCreditLimit(TenantContextBridge.currentTenantId(), connectionId);
    }

    /**
     * Tenant-explicit variant — used by callers that already know the tenant.
     */
    public CreditLimit fetchCreditLimit(String tenantId, String connectionId) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        DialogOperator op = new DialogOperator(cfg);
        if (!op.configured()) {
            log.warn("Dialog BSS not configured for tenant={}, returning null", tenantId);
            return null;
        }

        String path = op.path("credit", "/subscriber/credit/{connection}",
                Map.of("connection", connectionId));
        String fieldLimit = op.field("creditLimit", "creditLimit");
        String fieldUsed = op.field("usedCredit", "usedCredit");
        String fieldAvailable = op.field("availableCredit", "availableCredit");
        String currency = op.currency();

        try {
            Map<String, Object> data = httpClient.get(
                    tenantId, cfg.baseUrl(), path,
                    Map.of("lob", op.lob("credit", "GSM")), null, cfg.apiKey());
            if (data == null) return null;

            Map<String, Object> payload = data;
            Object limit = data.get(fieldLimit);
            if (limit == null && data.get("data") instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                Map<String, Object> inner = (Map<String, Object>) nested;
                payload = inner;
                limit = inner.get(fieldLimit);
            }

            BigDecimal limitAmount = toBigDecimal(limit);
            BigDecimal usedAmount = toBigDecimal(payload.get(fieldUsed));
            BigDecimal available = toBigDecimal(payload.get(fieldAvailable));
            if (available == null && limitAmount != null && usedAmount != null) {
                available = limitAmount.subtract(usedAmount);
            }
            boolean enabled = limitAmount != null && limitAmount.signum() > 0;

            return new CreditLimit(
                    connectionId,
                    limitAmount,
                    usedAmount,
                    available,
                    str(payload.get(currency), currency),
                    enabled,
                    Instant.now());
        } catch (DialogApiException e) {
            log.error("Dialog credit-limit fetch failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return null;
        }
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String str(Object v, String fallback) {
        return v != null ? v.toString() : fallback;
    }
}
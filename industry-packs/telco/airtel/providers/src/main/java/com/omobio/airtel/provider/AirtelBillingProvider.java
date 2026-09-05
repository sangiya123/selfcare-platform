package com.omobio.airtel.provider;

import com.omobio.platform.common.adapter.ApiAdapter;
import com.omobio.platform.common.adapter.BillingProvider;
import com.omobio.platform.common.adapter.RegisterAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Airtel Billing Provider — fetches billing information from Airtel BSS.
 *
 * <p>Implements the canonical {@link BillingProvider} contract from platform-common.
 * Airtel uses API-key authentication for BSS billing endpoints and wraps responses
 * in a {@code { data: { ... } }} envelope.</p>
 *
 * <p>Per-tenant config loaded from MongoDB via {@link AirtelHttpClient}.
 * Configure via Selfcare Studio admin: Integrations &gt; Airtel Gateway.</p>
 */
@Slf4j
@Component
@RegisterAdapter(tenantId = "airtel-lk", providerInterface = BillingProvider.class)
@RequiredArgsConstructor
public class AirtelBillingProvider implements ApiAdapter, BillingProvider {

    private static final String INTEGRATION_TYPE = "AIRTEL_BSS";
    private static final String ADAPTER_ID = "airtel-lk";

    private final AirtelHttpClient httpClient;

    @Override
    public String getAdapterId() {
        return ADAPTER_ID;
    }

    // ================================================================
    // BillingProvider implementation
    // ================================================================

    @Override
    public List<Bill> getBills(String tenantId, String customerId) {
        AirtelHttpClient.AirtelConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Airtel BSS not configured for tenant={}", tenantId);
            return List.of();
        }

        String path = "/v1/osp/billing/customers/" + customerId + "/bills";
        try {
            Map<String, Object> resp = httpClient.get(tenantId, cfg, path, null, null);
            if (resp == null) return List.of();
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> d)) return List.of();
            Object billsObj = d.get("bills");
            if (!(billsObj instanceof List<?> list)) return List.of();
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .<Bill>map(m -> new Bill(
                            str(m.get("billId"), null),
                            customerId,
                            str(m.get("billNumber"), null),
                            str(m.get("billingPeriod"), null),
                            parseLocalDate(m.get("issueDate")),
                            parseLocalDate(m.get("dueDate")),
                            toBd(m.get("totalAmount")),
                            toBd(m.get("amountPaid")),
                            toBd(m.get("amountDue")),
                            str(m.get("currency"), "LKR"),
                            parseStatus((String) m.get("status")),
                            str(m.get("pdfUrl"), null)
                    ))
                    .toList();
        } catch (AirtelApiException e) {
            log.error("Airtel getBills failed for tenant={} customer={}: {}",
                    tenantId, customerId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public Bill getBill(String tenantId, String billId) {
        AirtelHttpClient.AirtelConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Airtel BSS not configured for tenant={}", tenantId);
            return null;
        }

        String path = "/v1/osp/billing/bills/" + billId;
        try {
            Map<String, Object> resp = httpClient.get(tenantId, cfg, path, null, null);
            if (resp == null) return null;
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> d)) return null;
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) d;
            return new Bill(
                    billId,
                    str(m.get("customerId"), null),
                    str(m.get("billNumber"), null),
                    str(m.get("billingPeriod"), null),
                    parseLocalDate(m.get("issueDate")),
                    parseLocalDate(m.get("dueDate")),
                    toBd(m.get("totalAmount")),
                    toBd(m.get("amountPaid")),
                    toBd(m.get("amountDue")),
                    str(m.get("currency"), "LKR"),
                    parseStatus((String) m.get("status")),
                    str(m.get("pdfUrl"), null)
            );
        } catch (AirtelApiException e) {
            log.error("Airtel getBill failed for tenant={} bill={}: {}",
                    tenantId, billId, e.getMessage());
            return null;
        }
    }

    @Override
    public OutstandingAmount getOutstandingAmount(String tenantId, String customerId) {
        AirtelHttpClient.AirtelConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Airtel BSS not configured for tenant={}", tenantId);
            return null;
        }

        String path = "/v1/osp/billing/customers/" + customerId + "/outstanding";
        try {
            Map<String, Object> resp = httpClient.get(tenantId, cfg, path, null, null);
            if (resp == null) return null;
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> d)) return null;
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) d;
            return new OutstandingAmount(
                    customerId,
                    toBd(m.get("totalDue")),
                    toBd(m.get("overdueAmount")),
                    parseLocalDate(m.get("oldestDueDate")),
                    toInteger(m.get("overdueCount")),
                    str(m.get("currency"), "LKR")
            );
        } catch (AirtelApiException e) {
            log.error("Airtel getOutstandingAmount failed for tenant={} customer={}: {}",
                    tenantId, customerId, e.getMessage());
            return null;
        }
    }

    @Override
    public String getBillPdfUrl(String tenantId, String billId) {
        AirtelHttpClient.AirtelConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Airtel BSS not configured for tenant={}", tenantId);
            return null;
        }

        String path = "/v1/osp/billing/bills/" + billId + "/pdf";
        try {
            Map<String, Object> resp = httpClient.get(tenantId, cfg, path, null, null);
            if (resp == null) return null;
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> d)) return null;
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) d;
            return str(m.get("url"), null);
        } catch (AirtelApiException e) {
            log.error("Airtel getBillPdfUrl failed for tenant={} bill={}: {}",
                    tenantId, billId, e.getMessage());
            return null;
        }
    }

    @Override
    public List<BillLineItem> getBillLineItems(String tenantId, String billId) {
        AirtelHttpClient.AirtelConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Airtel BSS not configured for tenant={}", tenantId);
            return List.of();
        }

        String path = "/v1/osp/billing/bills/" + billId + "/line-items";
        try {
            Map<String, Object> resp = httpClient.get(tenantId, cfg, path, null, null);
            if (resp == null) return List.of();
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> d)) return List.of();
            Object itemsObj = d.get("lineItems");
            if (!(itemsObj instanceof List<?> list)) return List.of();
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .<BillLineItem>map(m -> new BillLineItem(
                            str(m.get("lineItemId"), null),
                            billId,
                            str(m.get("description"), null),
                            str(m.get("category"), null),
                            toInteger(m.get("quantity")),
                            toBd(m.get("unitPrice")),
                            toBd(m.get("totalPrice")),
                            toBd(m.get("taxAmount")),
                            str(m.get("taxRate"), null),
                            parseLocalDate(m.get("servicePeriodFrom")),
                            parseLocalDate(m.get("servicePeriodTo"))
                    ))
                    .toList();
        } catch (AirtelApiException e) {
            log.error("Airtel getBillLineItems failed for tenant={} bill={}: {}",
                    tenantId, billId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public PaymentPlan getPaymentPlan(String tenantId, String billId) {
        AirtelHttpClient.AirtelConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Airtel BSS not configured for tenant={}", tenantId);
            return null;
        }

        String path = "/v1/osp/billing/bills/" + billId + "/payment-plan";
        try {
            Map<String, Object> resp = httpClient.get(tenantId, cfg, path, null, null);
            if (resp == null) return null;
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> d)) return null;
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) d;
            return new PaymentPlan(
                    str(m.get("planId"), null),
                    billId,
                    toInteger(m.get("totalInstallments")) != null ? toInteger(m.get("totalInstallments")) : 0,
                    toInteger(m.get("installmentsPaid")) != null ? toInteger(m.get("installmentsPaid")) : 0,
                    toBd(m.get("installmentAmount")),
                    parseLocalDate(m.get("nextDueDate")),
                    parsePlanStatus((String) m.get("status"))
            );
        } catch (AirtelApiException e) {
            log.error("Airtel getPaymentPlan failed for tenant={} bill={}: {}",
                    tenantId, billId, e.getMessage());
            return null;
        }
    }

    // ================================================================
    // Helpers
    // ================================================================

    private String str(Object v, String fallback) {
        return v != null ? v.toString() : fallback;
    }

    private BigDecimal toBd(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(v.toString()); } catch (NumberFormatException e) { return null; }
    }

    private Integer toInteger(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        return null;
    }

    private LocalDate parseLocalDate(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDate ld) return ld;
        if (v instanceof String s) {
            try { return LocalDate.parse(s); } catch (Exception e) { return null; }
        }
        return null;
    }

    private BillStatus parseStatus(String s) {
        if (s == null) return BillStatus.UNPAID;
        try { return BillStatus.valueOf(s.toUpperCase()); } catch (Exception e) { return BillStatus.UNPAID; }
    }

    private PaymentPlanStatus parsePlanStatus(String s) {
        if (s == null) return PaymentPlanStatus.ACTIVE;
        try { return PaymentPlanStatus.valueOf(s.toUpperCase()); } catch (Exception e) { return PaymentPlanStatus.ACTIVE; }
    }
}
